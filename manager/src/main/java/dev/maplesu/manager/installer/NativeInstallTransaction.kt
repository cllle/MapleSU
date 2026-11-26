package dev.maplesu.manager.installer

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import dev.maplesu.image.BootImageInspector
import dev.maplesu.installer.BlockDeviceTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** Keep commit validation consistent with the resolver's actual carrier and vbmeta reference. */
internal fun verifyOwnedRestoreImage(partition: String, image: File, vbmeta: File) {
    val images = mapOf(partition to image, "vbmeta" to vbmeta)
    when (partition) {
        "boot" -> dev.maplesu.image.LenovoAvbSigner.verifySigned(
            dev.maplesu.image.LenovoAvbSigner.Mode.CHAINED, partition, images)
        "init_boot" -> dev.maplesu.image.LenovoAvbSigner.verifyOriginals(images)
        else -> error("INSTALLER_STOCK_TARGETS_INVALID")
    }
}

/** Ordinary automatic carrier migration follows the unsigned installation contract. */
internal fun requiresSignedOwnedCarrierRestore(record: JSONObject, partition: String): Boolean {
    val ordinaryMigration = record.optString("operation", "INSTALL") == "INSTALL" &&
        record.optString("targetMode") in setOf("LITE", "KSU") && record.optString("signingMode").isEmpty() &&
        record.optString("restorationKind") == "OWNED_WRITES_REMOVED" &&
        record.optString("carrierRestoration") == partition && record.optString("partition") != partition
    return !ordinaryMigration || record.optBoolean("requireSignedOwnedRestore", true)
}

internal fun verifyOwnedCarrierRestoreImage(record: JSONObject, partition: String, image: File, vbmeta: File) {
    if (requiresSignedOwnedCarrierRestore(record, partition))
        verifyOwnedRestoreImage(partition, image, vbmeta)
}

/** Root-owned first-install/reinstall tasks. Stock originals and full PRE remain separate. */
internal object NativeInstallTransaction {
    private val root = InstallerDeviceLock.defaultRoot
    // SDK teardown may remove /data/adb/maplesu. Signed PRE and journals must survive it.
    private val signedRoot = InstallerPending.signedInstallRoot()
    private val control = File("/data/adb/maplesu-workmode")
    private val names = setOf("boot", "init_boot")
    private val visibleStates = InstallerPending.installActiveStates + setOf("REBOOT_REQUIRED", "REBOOT_REQUESTED", "BOOT_IMAGE_VERIFIED")

    fun inspect(selectedManager: String, userId: Int = 0,
                assets: dev.maplesu.manager.ultrakernel.UltraKernelAssetSource? = null,
                receipts: File? = null, retainedOriginals: List<File> = emptyList()): Bundle = locked {
        require(userId >= 0) { "INSTALLER_CALLER_INVALID" }
        InstallerPending.assertNoPendingSwitch(control, installerRoot = root)
        val directory = File(root, "inspect-" + UUID.randomUUID()).apply { check(mkdir()) }
        Os.chmod(directory.path, 0x1c0)
        try {
            val snapshot = InstallerServiceClient.decodeSnapshot(InstallerPartitionReader.observe())
            val live = capture(snapshot, File(directory, "pre"))
            val resolution = runCatching {
                resolveOriginals(snapshot, live, File(directory, "original"), selectedManager, userId,
                    assets, receipts, retainedOriginals)
            }
            val resolved = resolution.getOrNull()?.originals
            val baselineDirectory = if (resolved != null && resolution.getOrNull()?.family != "maplesu")
                NativeStockBaseline.publish(snapshot, resolved) else null
            Bundle().apply {
                putString("kind", "NATIVE")
                putBoolean("stockAvailable", resolved != null)
                putBoolean("hasConflict", resolved == null || resolved.any { (name, file) -> digest(file) != snapshot.partitions.getValue(name).sha256 })
                putString("family", resolution.getOrNull()?.family.orEmpty())
                putString("sourceSlot", resolution.getOrNull()?.sourceSlot.orEmpty())
                putString("baselineDirectory", baselineDirectory.orEmpty())
                putString("error", resolution.exceptionOrNull()?.let(::code)
                    ?: if (resolved == null) "INSTALLER_STOCK_BACKUP_UNAVAILABLE" else "")
                putStringArrayList("partitions", ArrayList(live.keys))
            }
        } finally { directory.deleteRecursively() }
    }

    fun prepare(taskId: String, targetMode: String, partition: String, restoreOriginal: Boolean,
                selectedManager: String, conflictAccepted: Boolean, userId: Int = 0,
                operation: String = "INSTALL",
                assets: dev.maplesu.manager.ultrakernel.UltraKernelAssetSource? = null,
                signingMode: String = "", receipts: File? = null,
                retainedOriginals: List<File> = emptyList()): Bundle = locked {
        validId(taskId)
        require(userId >= 0) { "INSTALLER_CALLER_INVALID" }
        require(operation in setOf("INSTALL", "RESTORE_STOCK")) { "INSTALLER_TASK_INVALID" }
        require(signingMode.isEmpty() || when (partition) {
            "boot" -> signingMode in setOf("CHAINED", "AOSP")
            "init_boot" -> signingMode == "NON_CHAINED"
            else -> false
        }) { "INSTALLER_SIGNING_MODE_INVALID" }
        require(targetMode in setOf("LITE", "KSU") &&
            dev.maplesu.installer.SwitchPartitionLayout.allowsPartition(
                dev.maplesu.installer.WorkMode.valueOf(targetMode), partition)) { "INSTALLER_PARTITION_INVALID" }
        val existing = taskDirectory(taskId)
        if (existing.exists()) {
            val old = read(existing)
            check(old.optString("targetMode") == targetMode && old.optString("partition") == partition &&
                old.optBoolean("requestedRestoreOriginal", old.optBoolean("restoreOriginal")) == restoreOriginal && old.optString("selectedManager") == selectedManager &&
                old.optBoolean("conflictAccepted") == conflictAccepted && old.optInt("userId") == userId &&
                old.optString("operation", "INSTALL") == operation && old.optString("signingMode") == signingMode) { "INSTALLER_TASK_CONFLICT" }
            if (operation == "RESTORE_STOCK") stockRestoreTargets(old)
            if (old.getString("state") == "PREPARED") rebind(old, requirePre = true)
            return@locked response(old)
        }
        InstallerPending.assertNoPendingInstall(root, taskId)
        InstallerPending.assertNoPendingSwitch(control, taskId, root)
        val signedNames = signingNames(signingMode)
        val rawSnapshot = InstallerServiceClient.decodeSnapshot(InstallerPartitionReader.observeSigning(signedNames))
        val observed = withSigning(rawSnapshot, signedNames)
        require(partition in observed.partitions) { "INSTALLER_PARTITION_INVALID" }
        val stockSelection = if (operation == "RESTORE_STOCK") StockRestoreTargets.select(observed, targetMode,
            recordDirectories().filter { File(it, "record.json").isFile }.mapNotNull { runCatching { read(it) }.getOrNull() }, bootId()) else null
        val parent = run {
            for (folder in listOf(signedRoot.parentFile!!, signedRoot)) {
                if (!folder.exists()) {
                    check(folder.mkdir()) { "INSTALLER_STATE_DIRECTORY_FAILED" }
                    Os.chmod(folder.path, 0x1c0)
                    InstallerDeviceLock.syncStateDirectory(folder.parentFile!!)
                }
                privateDirectory(folder)
            }
            signedRoot
        }
        val directory = File(parent, taskId).apply { check(mkdir()) { "INSTALLER_TASK_CREATE_FAILED" } }
        Os.chmod(directory.path, 0x1c0)
        InstallerDeviceLock.syncStateDirectory(parent)
        val record = JSONObject().apply {
            put("kind", "NATIVE"); put("schema", 1); put("taskId", taskId); put("targetMode", targetMode)
            put("operation", operation)
            put("vivoCompatibility", operation == "INSTALL" && VivoCompatibility.deviceSupported())
            put("signingMode", signingMode)
            put("partition", partition); put("requestedRestoreOriginal", restoreOriginal)
            put("restoreOriginal", restoreOriginal); put("selectedManager", selectedManager)
            put("userId", userId)
            put("conflictAccepted", conflictAccepted); put("state", "PREPARING"); put("error", "")
            put("bootId", bootId()); put("snapshot", encode(observed)); put("attempted", JSONArray())
            put("backupDirectory", File(directory, "pre").path); put("baselineDirectory", "")
            stockSelection?.let {
                put("stockRestoreTargets", JSONArray(it.targets.sorted()))
                put("stockRestoreSources", JSONObject(it.sourceProofs))
                put("stockRestoreCoverage", it.coverage)
            }
        }
        save(directory, record)
        try {
            val captured = capture(withSigning(observed,
                observed.signingPartitions + listOf("vbmeta").filter { it in observed.partitions }), File(directory, "pre"))
            val pre = captured.filterKeys { it in names }
            // Resolve a carrier change once, from the immutable PRE of this installation.
            // Unknown or modified recovery bytes retain their original failure semantics.
            if (operation == "INSTALL" && !restoreOriginal) {
                val other = if (partition == "boot") "init_boot" else "boot"
                val image = pre[other]
                val ownsOther = image != null && (dev.maplesu.image.UltraInitBootProducer.recognizes(image) ||
                    dev.maplesu.image.RamdiskInspector.inspect(image).let { ramdisk ->
                        ramdisk.hasKernelsuKo || ramdisk.hasKsuConfig || ramdisk.hasKsud || ramdisk.hasSevenkKo ||
                            ramdisk.entries.any { it.path.removePrefix("./") == "stock_image.sha1" }
                    } ||
                    other == "boot" && assets != null &&
                    dev.maplesu.manager.ultrakernel.UltraKernelOriginalRecovery(assets).let { recovery ->
                        val lineage = receipts ?: File(directory, "no-receipts")
                        recovery.generatedKernelWithDeployment(image, lineage) != null ||
                            recovery.installedSpec(image, lineage) != null
                    })
                if (ownsOther) record.put("restoreOriginal", true).put("carrierRestoration", other)
            }
            if (record.getBoolean("restoreOriginal")) {
                val destination = File(directory, "original")
                val carrierRestore = record.optString("carrierRestoration").takeIf(String::isNotBlank)
                val requireSignedOwnedRestore = carrierRestore == null || signingMode.isNotEmpty()
                record.put("requireSignedOwnedRestore", requireSignedOwnedRestore)
                val restoreTargets = carrierRestore?.let(::setOf)
                    ?: stockSelection?.takeIf { it.sourceProofs.isNotEmpty() }?.targets
                val sources = if (operation == "RESTORE_STOCK" || carrierRestore != null)
                    pre + captured.filterKeys { it == "vbmeta" } else pre
                val resolution = resolveOriginals(observed, sources, destination, selectedManager, userId,
                    assets, receipts, retainedOriginals, restoreTargets,
                    forRemoval = operation == "RESTORE_STOCK" || carrierRestore != null,
                    requireSignedOwnedRestore = requireSignedOwnedRestore)
                val originals = resolution.originals
                record.put("originalSource", resolution.family).put("sourceSlot", resolution.sourceSlot)
                record.put("restorationKind", resolution.kind.name)
                if (resolution.kind == ImageRestorationKind.OWNED_WRITES_REMOVED) {
                    val owned = originals.keys.singleOrNull()
                    check(owned in names && (operation == "RESTORE_STOCK" || operation == "INSTALL" &&
                        owned == carrierRestore && owned != partition)) { "INSTALLER_STOCK_TARGETS_INVALID" }
                    if (operation == "RESTORE_STOCK") record.put("stockRestoreTargets", JSONArray(listOf(owned)))
                        .put("stockRestoreSources", JSONObject()).put("stockRestoreCoverage", "IMAGE_OWNED_RECORD")
                    record.put("ownedRemovalSource", resolution.ownershipSource)
                        .put("ownedRemovalProof", JSONObject().put(owned, observed.partitions.getValue(requireNotNull(owned)).sha256))
                } else check(originals.keys == (restoreTargets ?: pre.keys)) { "INSTALLER_STOCK_BACKUP_UNAVAILABLE" }
                record.put("originals", JSONObject().apply {
                    originals.forEach { (name, image) ->
                        check(image.canonicalFile == File(destination, "$name.img").canonicalFile &&
                            image.length() == observed.partitions.getValue(name).size) { "INSTALLER_STOCK_BACKUP_MISMATCH" }
                        BootImageInspector.inspect(image)
                        put(name, digest(image))
                    }
                })
                if (restoreTargets == null && resolution.kind == ImageRestorationKind.EXACT_STOCK)
                    record.put("baselineDirectory", NativeStockBaseline.publish(observed, originals)
                    ?: error("INSTALLER_STOCK_BACKUP_UNAVAILABLE"))
            }
            if (operation == "RESTORE_STOCK" && record.optString("restorationKind") != "OWNED_WRITES_REMOVED" &&
                stockSelection?.sourceProofs?.isEmpty() == true) retainedVendorOriginal(observed)?.let { original ->
                val copy = File(directory, "original/vendor_boot.img")
                Files.copy(original.toPath(), copy.toPath())
                Os.chmod(copy.path, 0x180)
                java.io.RandomAccessFile(copy, "rw").use { it.fd.sync() }
                record.getJSONObject("originals").put("vendor_boot", digest(copy))
                record.put("stockRestoreTargets", JSONArray((stockSelection.targets + "vendor_boot").sorted()))
            }
            rebind(record, requirePre = true)
            record.put("state", "PREPARED")
            save(directory, record)
            response(record)
        } catch (error: Exception) {
            record.put("state", "FAILED_BEFORE_WRITE").put("error", code(error))
            save(directory, record)
            throw error
        }
    }

    fun openImage(taskId: String, partition: String, meta: Bundle, cacheDirectory: File): ParcelFileDescriptor = locked {
        meta.clear()
        val directory = directory(taskId)
        val record = read(directory)
        check(record.optString("operation", "INSTALL") == "INSTALL") { "INSTALLER_TASK_CONFLICT" }
        check(record.getString("state") == "PREPARED" &&
            (partition == record.getString("partition") ||
                partition == "boot" && record.getString("targetMode") == "LITE" && record.getString("partition") == "init_boot" ||
                partition == "vendor_boot" && partition in decode(record).partitions || partition in signingNames(record.optString("signingMode")) ||
                partition == "init_boot" && partition in decode(record).partitions && record.optBoolean("vivoCompatibility") &&
                record.getString("targetMode") == "LITE" && record.getString("partition") == "boot" ||
                record.getBoolean("restoreOriginal") &&
                partition in names && partition in decode(record).partitions)) {
            "INSTALLER_TRANSACTION_NOT_PREPARED"
        }
        val companion = partition !in names
        val source = if (companion) File(directory, "pre/$partition.img") else image(directory, record, partition)
        val sha = if (!companion && record.optJSONObject("originals")?.has(partition) == true) record.getJSONObject("originals").getString(partition)
            else decode(record).partitions.getValue(partition).sha256
        check(source.length() == decode(record).partitions.getValue(partition).size && digest(source) == sha) {
            "INSTALLER_IMAGE_CHANGED"
        }
        meta.putString("partition", partition); meta.putLong("size", source.length()); meta.putString("sha256", sha)
        // The transaction directory carries the root environment's SELinux label. Passing its
        // open FD still makes app reads fail with EACCES on enforcing ROMs. Export a private,
        // unlinked cache snapshot, exactly as openBootImage does, and keep originals immutable.
        openReadOnlySnapshot(source, cacheDirectory, meta.getLong("size"), sha)
    }

    private fun openReadOnlySnapshot(source: File, cacheDirectory: File, size: Long,
                                     sha256: String): ParcelFileDescriptor {
        val snapshot = File.createTempFile("maplesu-install-source-", ".tmp", cacheDirectory)
        try {
            val output = Os.open(snapshot.path, OsConstants.O_WRONLY or OsConstants.O_CLOEXEC or
                OsConstants.O_NOFOLLOW, 0)
            try {
                val created = Os.fstat(output)
                check(OsConstants.S_ISREG(created.st_mode) && created.st_uid == 0 &&
                    created.st_nlink == 1L && created.st_size == 0L) { "INSTALLER_SNAPSHOT_INVALID" }
                Os.fchmod(output, 0x180)
                val hash = MessageDigest.getInstance("SHA-256")
                source.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        check(!Thread.currentThread().isInterrupted) { "INSTALLER_CANCELLED" }
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(count > 0 && count <= size - copied) { "INSTALLER_IMAGE_CHANGED" }
                        hash.update(buffer, 0, count)
                        var written = 0
                        while (written < count) {
                            val bytes = Os.write(output, buffer, written, count - written)
                            check(bytes > 0) { "INSTALLER_SNAPSHOT_WRITE_FAILED" }
                            written += bytes
                        }
                        copied += count
                    }
                    check(copied == size) { "INSTALLER_IMAGE_CHANGED" }
                }
                Os.fsync(output)
                check(hash.digest().joinToString("") { "%02x".format(it) } == sha256) { "INSTALLER_IMAGE_CHANGED" }
                val descriptor = ParcelFileDescriptor.open(snapshot, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    val actual = Os.fstat(descriptor.fileDescriptor)
                    check(OsConstants.S_ISREG(actual.st_mode) && actual.st_ino == created.st_ino && actual.st_dev == created.st_dev &&
                        actual.st_size == size) { "INSTALLER_SNAPSHOT_INVALID" }
                    check(snapshot.delete()) { "INSTALLER_SNAPSHOT_CLEANUP_FAILED" }
                    return descriptor
                } catch (error: Throwable) {
                    descriptor.close()
                    throw error
                }
            } finally { Os.close(output) }
        } finally {
            snapshot.delete()
        }
    }

    fun commit(taskId: String, candidate: ParcelFileDescriptor, candidateSha256: String,
               companionImages: Bundle = Bundle(),
               ensureNormalUserland: () -> Unit = { error("INSTALLER_NORMAL_USERLAND_UNWIRED") }): Bundle = locked {
        val directory = directory(taskId)
        val record = read(directory)
        check(record.optString("operation", "INSTALL") == "INSTALL") { "INSTALLER_TASK_CONFLICT" }
        var normalUserlandFailure: dev.maplesu.manager.ksu.KsuBootstrapFailure? = null
        fun deployNormalUserland(beforeReboot: Boolean = false) {
            if (record.getString("targetMode") != "KSU") return
            try {
                ensureNormalUserland()
                if (record.has("normalUserlandFailure")) {
                    record.remove("normalUserlandFailure")
                    record.remove("errorDetail")
                    record.put("error", "")
                    save(directory, record)
                }
            } catch (error: dev.maplesu.manager.ksu.KsuBootstrapFailure) {
                normalUserlandFailure = error
                // record.json is bounded; full command output remains in the bootstrap log callbacks.
                val rawDetail = error.result.detail
                val detail = if (rawDetail.length <= 2_048) rawDetail else
                    rawDetail.take(1_024) + "\n...[NormalBoot log contains full output]...\n" + rawDetail.takeLast(1_024)
                record.put("normalUserlandFailure", JSONObject()
                    .put("code", error.message).put("detail", detail).put("detailTruncated", detail != rawDetail))
                    .put("error", error.message).put("errorDetail", detail)
                try { save(directory, record) } catch (journalError: Exception) { error.addSuppressed(journalError) }
                if (beforeReboot) throw java.io.IOException("INSTALLER_NORMAL_USERLAND_FAILED", error)
                throw error
            }
        }
        if (record.getString("state") in setOf("REBOOT_REQUIRED", "REBOOT_REQUESTED", "BOOT_IMAGE_VERIFIED")) {
            check(record.optString("candidateSha256") == candidateSha256) { "INSTALLER_TASK_CONFLICT" }
            verifyImages(record, rebind(record, requirePre = false), target = true)
            deployNormalUserland()
            return@locked response(record)
        }
        check(record.getString("state") == "PREPARED") { "INSTALLER_TRANSACTION_NOT_PREPARED" }
        require(candidateSha256.matches(Regex("[0-9a-f]{64}"))) { "INSTALLER_HASH_INVALID" }
        InstallerPending.assertNoPendingSwitch(control, taskId, root)
        try {
            val snapshot = rebind(record, requirePre = true)
            val target = record.getString("partition")
            val file = File(directory, "candidate.img")
            InstallerImageInput.copy(candidate, file, snapshot.partitions.getValue(target).size)
            val report = BootImageInspector.inspect(file)
            check(report.sha256 == candidateSha256 &&
                if (target == "boot") report.kernelSize > 0 else report.kernelSize == 0L) {
                "INSTALLER_CANDIDATE_CHANGED"
            }
            if (record.getString("targetMode") == "LITE" && target == "init_boot") {
                check(dev.maplesu.image.UltraInitBootProducer.recognizes(file)) {
                    "ULTRA_KERNEL_INIT_BOOT_CANDIDATE_UNRECOGNIZED"
                }
            }
            record.put("candidateSha256", candidateSha256)
            record.put("vrKernelFilter", record.optBoolean("vivoCompatibility") &&
                record.getString("targetMode") == "LITE" && target == "boot" &&
                dev.maplesu.manager.ultrakernel.UltraKernelVivoFilter.present(
                    dev.maplesu.manager.ultrakernel.UltraKernelPlan.readKernel(file)))
            val signedNames = signingNames(record.optString("signingMode"))
            val compatibilityNames = companionImages.keySet().filter { it in setOf("vendor_boot", "init_boot") }.toSet()
            require(compatibilityNames.all { it in snapshot.partitions && it != target &&
                (it != "init_boot" || record.getString("targetMode") == "LITE" && target == "boot") }) {
                "INSTALLER_COMPANION_INVALID"
            }
            val keys = signedNames.flatMap { listOf(it, "$it.sha256") } +
                compatibilityNames.flatMap { listOf(it, "$it.sha256", "$it.sourceSha256") }
            require(companionImages.keySet() == keys.toSet()) { "INSTALLER_SIGNED_IMAGES_REQUIRED" }
            val compatibility = compatibilityNames.map { name ->
                @Suppress("DEPRECATION")
                val descriptor = requireNotNull(companionImages.getParcelable<ParcelFileDescriptor>(name))
                val companion = File(directory, "compatibility-$name.img")
                InstallerImageInput.copy(descriptor, companion, snapshot.partitions.getValue(name).size)
                val expected = requireNotNull(companionImages.getString("$name.sha256"))
                val source = if (name == "vendor_boot" || !record.getBoolean("restoreOriginal"))
                    snapshot.partitions.getValue(name).sha256 else record.getJSONObject("originals").getString(name)
                check(companionImages.getString("$name.sourceSha256") == source &&
                    expected.matches(Regex("[0-9a-f]{64}")) && digest(companion) == expected) { "INSTALLER_IMAGE_CHANGED" }
                if (name == "vendor_boot") dev.maplesu.image.VendorBootInspector.inspect(companion)
                else check(BootImageInspector.inspect(companion).kernelSize == 0L) { "INSTALLER_COMPANION_INVALID" }
                check(companion.length() == snapshot.partitions.getValue(name).size) { "INSTALLER_IMAGE_SIZE_INVALID" }
                NativeInstallSequence.Image(name, companion, expected)
            }.filter { digest(it.file) != snapshot.partitions.getValue(it.partition).sha256 }
            val signed = signedNames.map { name ->
                @Suppress("DEPRECATION")
                val descriptor = requireNotNull(companionImages.getParcelable<ParcelFileDescriptor>(name))
                val companion = File(directory, "signed-$name.img")
                InstallerImageInput.copy(descriptor, companion, snapshot.partitions.getValue(name).size)
                val expected = requireNotNull(companionImages.getString("$name.sha256"))
                check(expected.matches(Regex("[0-9a-f]{64}")) && digest(companion) == expected) { "INSTALLER_IMAGE_CHANGED" }
                NativeInstallSequence.Image(name, companion, expected)
            }
            record.put("compatibilityPartitions", JSONArray(compatibility.map { it.partition }))
            val writes = (names.filter { it in snapshot.partitions && it != target && it !in compatibilityNames &&
                record.optJSONObject("originals")?.has(it) == true }
                .map { name -> NativeInstallSequence.Image(name, image(directory, record, name), record.getJSONObject("originals").getString(name)) } +
                NativeInstallSequence.Image(target, file, candidateSha256))
                 .let { it + compatibility }.sortedBy { when {
                     it.partition == "vendor_boot" -> 0
                     it.partition == target -> 3
                     it.partition == "init_boot" -> 1
                     else -> 2
                 } } + signed
            if (record.optString("restorationKind") == "OWNED_WRITES_REMOVED" &&
                record.optString("carrierRestoration").isNotBlank()) {
                val restored = writes.single { it.partition == record.getString("carrierRestoration") }
                if (dev.maplesu.image.AvbFooter.parse(restored.file).present) {
                    val main = File(directory, "pre/vbmeta.img")
                    val expectedMain = snapshot.partitions["vbmeta"]
                    check(expectedMain != null || !requiresSignedOwnedCarrierRestore(record, restored.partition)) {
                        "INSTALLER_IMAGE_CHANGED"
                    }
                    if (expectedMain != null)
                        check(digest(main) == expectedMain.sha256) { "INSTALLER_IMAGE_CHANGED" }
                    verifyOwnedCarrierRestoreImage(record, restored.partition, restored.file, main)
                }
            }
            if (record.optString("signingMode").isNotEmpty()) {
                // Verify the complete final write set, including any restored init_boot hash.
                dev.maplesu.image.LenovoAvbSigner.verifySigned(
                    dev.maplesu.image.LenovoAvbSigner.Mode.valueOf(record.getString("signingMode")),
                    target, writes.associate { it.partition to it.file })
            }
            record.put("expected", JSONObject().apply {
                snapshot.partitions.filterKeys { it in tracked(snapshot) }.forEach { (name, row) ->
                    put(name, writes.firstOrNull { it.partition == name }?.let {
                        BlockDeviceTransaction.paddedDigest(it.file.toPath(), row.size)
                    } ?: row.sha256)
                }
            })
            save(directory, record)
            val outcome = withDevices(snapshot, writable = true, onIoFailure = ioJournal(directory, record)) { devices ->
                NativeInstallSequence.commit(devices, pre(directory, snapshot), writes, journal(directory, record),
                    beforeReboot = {
                        deployNormalUserland(beforeReboot = true)
                    })
            }
            val outcomeError = normalUserlandFailure?.message
                ?.takeIf { outcome.error == "INSTALLER_NORMAL_USERLAND_FAILED" } ?: outcome.error
            if (record.getString("state") != outcome.state || record.optString("error") != outcomeError) {
                record.put("state", outcome.state).put("error", outcomeError)
                save(directory, record)
            }
        } catch (error: Exception) {
            val state = if (record.optJSONArray("attempted")?.length() == 0 && record.getString("state") !in
                setOf("WRITING", "RESTORING", "RECOVERY_REQUIRED")) "FAILED_BEFORE_WRITE" else "RECOVERY_REQUIRED"
            record.put("state", state).put("error", code(error))
            save(directory, record)
        }
        response(record)
    }

    /** User-requested stock restoration is distinct from rolling this task back to its PRE. */
    fun restoreStockImages(taskId: String, targetMode: String, userId: Int = 0,
                           assets: dev.maplesu.manager.ultrakernel.UltraKernelAssetSource? = null,
                           receipts: File? = null, retainedOriginals: List<File> = emptyList()): Bundle {
        prepare(taskId, targetMode, "boot", restoreOriginal = true, selectedManager = "",
            conflictAccepted = false, userId = userId, operation = "RESTORE_STOCK", assets = assets,
            receipts = receipts, retainedOriginals = retainedOriginals)
        return locked {
            val directory = directory(taskId)
            val record = read(directory)
            check(record.optString("operation") == "RESTORE_STOCK") { "INSTALLER_TASK_CONFLICT" }
            val targets = stockRestoreTargets(record)
            if (record.getString("state") in setOf("REBOOT_REQUIRED", "REBOOT_REQUESTED", "BOOT_IMAGE_VERIFIED")) {
                verifyImages(record, rebind(record, requirePre = false), target = true)
                return@locked response(record)
            }
            check(record.getString("state") == "PREPARED") { "INSTALLER_TRANSACTION_NOT_PREPARED" }
            InstallerPending.assertNoPendingSwitch(control, taskId, root)
            try {
                val snapshot = rebind(record, requirePre = true)
                check(record.getJSONObject("originals").keys().asSequence().toSet() == targets) { "INSTALLER_STOCK_BACKUP_UNAVAILABLE" }
                val writes = targets.sorted().map { name ->
                    NativeInstallSequence.Image(name, image(directory, record, name), record.getJSONObject("originals").getString(name))
                }
                if (StockRestoreTargets.restorationKind(record) == ImageRestorationKind.OWNED_WRITES_REMOVED &&
                    dev.maplesu.image.AvbFooter.parse(writes.single().file).present) {
                    val main = File(directory, "pre/vbmeta.img")
                    check(digest(main) == snapshot.partitions.getValue("vbmeta").sha256) { "INSTALLER_IMAGE_CHANGED" }
                    verifyOwnedRestoreImage(writes.single().partition, writes.single().file, main)
                }
                record.put("expected", JSONObject().apply { snapshot.partitions.filterKeys { it in tracked(snapshot) }.forEach { (name, row) ->
                    put(name, writes.firstOrNull { it.partition == name }?.sha256 ?: row.sha256)
                } })
                save(directory, record)
                val outcome = withDevices(snapshot, writable = true, onIoFailure = ioJournal(directory, record)) { devices ->
                    NativeInstallSequence.commit(devices, pre(directory, snapshot), writes, journal(directory, record))
                }
                if (record.getString("state") != outcome.state || record.optString("error") != outcome.error) {
                    record.put("state", outcome.state).put("error", outcome.error)
                    save(directory, record)
                }
            } catch (error: Exception) {
                val state = if (record.optJSONArray("attempted")?.length() == 0 && record.getString("state") !in
                    setOf("WRITING", "RESTORING", "RECOVERY_REQUIRED")) "FAILED_BEFORE_WRITE" else "RECOVERY_REQUIRED"
                record.put("state", state).put("error", code(error))
                save(directory, record)
            }
            response(record)
        }
    }

    fun cancel(taskId: String): Bundle = locked {
        validId(taskId)
        // Prepare can fail before creating its record; cancellation is then already complete.
        if (!taskDirectory(taskId).exists()) return@locked Bundle().apply {
            putString("kind", "NATIVE"); putString("taskId", taskId); putString("state", "CANCELLED")
        }
        val directory = directory(taskId)
        val record = read(directory)
        check(record.getString("state") in setOf("PREPARING", "PREPARED", "CANCELLED", "FAILED_BEFORE_WRITE")) {
            "INSTALLER_CANCELLATION_NOT_ALLOWED"
        }
        record.put("state", "CANCELLED").put("error", "")
        save(directory, record)
        response(record)
    }

    fun restore(taskId: String): Bundle = locked {
        val directory = directory(taskId)
        val record = read(directory)
        if (record.getString("state") == "REBOOT_REQUESTED" && record.optString("rebootSourceState") == "RESTORED") {
            return@locked response(record)
        }
        if (record.getString("state") == "RESTORED") return@locked response(record)
        check(record.getString("state") in setOf("WRITING", "RESTORING", "RECOVERY_REQUIRED", "REBOOT_REQUIRED", "REBOOT_REQUESTED", "BOOT_IMAGE_VERIFIED")) {
            "INSTALLER_RECOVERY_NOT_REQUIRED"
        }
        InstallerPending.assertNoPendingSwitch(control, taskId, root)
        val snapshot = rebind(record, requirePre = false)
        record.put("restoreBootId", bootId()).put("restoreRebootRequired", record.getString("bootId") != bootId())
        val outcome = try {
            withDevices(snapshot, writable = true, onIoFailure = ioJournal(directory, record)) { devices ->
                val order = (record.getJSONArray("attempted").let { a -> (0 until a.length()).map(a::getString) } + devices.keys).distinct()
                NativeInstallSequence.restore(devices, pre(directory, decode(record)), order, journal(directory, record))
            }
        } catch (error: Exception) {
            record.put("state", "RECOVERY_REQUIRED").put("error", code(error))
            save(directory, record)
            return@locked response(record)
        }
        if (record.getString("state") != outcome.state) {
            record.put("state", outcome.state).put("error", outcome.error)
            save(directory, record)
        }
        response(record)
    }

    fun verify(taskId: String, daemon: File): Bundle = locked {
        val directory = directory(taskId)
        val record = read(directory)
        check(record.getString("state") in setOf("REBOOT_REQUIRED", "REBOOT_REQUESTED", "BOOT_IMAGE_VERIFIED")) {
            "INSTALLER_VERIFICATION_NOT_READY"
        }
        check(record.optString("rebootSourceState") != "RESTORED") { "INSTALLER_VERIFICATION_NOT_READY" }
        check(record.getString("bootId") != bootId()) { "INSTALLER_REBOOT_NOT_OBSERVED" }
        verifyImages(record, rebind(record, requirePre = false), target = true)
        if (record.optBoolean("vivoCompatibility")) {
            val kernelFilter = record.optBoolean("vrKernelFilter") &&
                digest(File(directory, "candidate.img")) == record.getString("candidateSha256") &&
                dev.maplesu.manager.ultrakernel.UltraKernelVivoFilter.present(
                    dev.maplesu.manager.ultrakernel.UltraKernelPlan.readKernel(File(directory, "candidate.img")))
            VivoCompatibility.verifyRuntime(record.getString("targetMode"), daemon, kernelFilter)
            record.put("vrVerifiedBootId", bootId())
        }
        record.put("state", "BOOT_IMAGE_VERIFIED").put("verifiedBootId", bootId()).put("error", "")
        save(directory, record)
        response(record)
    }

    fun reboot(taskId: String): Boolean = locked {
        val directory = directory(taskId)
        val record = read(directory)
        reconcileReboot(directory, record)
        val restored = record.getString("state") == "RESTORED"
        check(record.getString("state") == "REBOOT_REQUIRED" || restored && record.optBoolean("restoreRebootRequired")) {
            "INSTALLER_REBOOT_NOT_READY"
        }
        check(record.getString(if (restored) "restoreBootId" else "bootId") == bootId()) { "INSTALLER_REBOOT_ALREADY_OBSERVED" }
        verifyImages(record, rebind(record, requirePre = false), target = !restored)
        val previous = record.getString("state")
        record.put("state", "REBOOT_REQUESTED").put("rebootSourceState", previous)
            .put("rebootRequestUptimeMs", android.os.SystemClock.elapsedRealtime())
        save(directory, record)
        try {
            val process = ProcessBuilder("/system/bin/svc", "power", "reboot").start()
            if (process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() != 0) {
                record.put("state", previous).put("error", "INSTALLER_REBOOT_FAILED"); save(directory, record); false
            } else true
        } catch (_: Exception) {
            record.put("state", previous).put("error", "INSTALLER_REBOOT_FAILED"); save(directory, record); false
        }
    }

    fun owns(taskId: String): Boolean = if (taskId.isEmpty()) latest()?.optString("kind") == "NATIVE" else {
        validId(taskId)
        val directory = taskDirectory(taskId)
        File(directory, "record.json").exists() && readAny(directory).optString("kind") == "NATIVE"
    }

    fun status(taskId: String): Bundle {
        val record = if (taskId.isEmpty()) latest()?.takeIf { it.optString("kind") == "NATIVE" } else read(directory(taskId))
        return record?.let(::response) ?: Bundle().apply { putString("taskId", taskId); putString("state", "NONE") }
    }

    /** Export only recorded diagnostics; this does not reconcile or re-read partitions. */
    fun recoveryLog(taskId: String, destination: File): File = locked {
        val record = read(directory(taskId))
        fun select(source: JSONObject, vararg fields: String) = JSONObject().also { output ->
            fields.forEach { if (source.has(it)) output.put(it, source.opt(it)) }
        }
        val log = select(record, "kind", "operation", "taskId", "state", "error", "errorDetail",
            "targetMode", "partition", "candidateSha256", "signingMode", "restorationKind",
            "carrierRestoration", "requireSignedOwnedRestore")
        record.optJSONObject("normalUserlandFailure")?.let {
            log.put("normalUserlandFailure", select(it, "code", "detail", "detailTruncated"))
        }
        record.optJSONArray("ioFailures")?.let { failures ->
            log.put("ioFailures", JSONArray().apply {
                for (index in 0 until failures.length()) failures.optJSONObject(index)?.let {
                    put(select(it, "partition", "path", "operation", "offset", "length", "errno", "exception"))
                }
            })
        }
        record.optJSONObject("snapshot")?.let { snapshot ->
            log.put("slotSuffix", snapshot.optString("slotSuffix"))
            val partitions = snapshot.optJSONObject("partitions") ?: JSONObject()
            log.put("partitions", JSONArray().apply {
                partitions.keys().forEach { name -> partitions.optJSONObject(name)?.let { row ->
                    put(JSONObject().put("name", name).put("size", row.optLong("size"))
                        .put("capturedSha256", row.optString("sha256"))
                        .put("expectedSha256", record.optJSONObject("expected")?.optString(name).orEmpty()))
                } }
            })
        }
        destination.parentFile?.mkdirs()
        destination.writeText(log.toString(2))
        destination
    }

    /** A status read only updates journal metadata after complete live partition verification. */
    fun reconcileForStatus() = locked {
        reconcileInstallRecords(cancelPrepared = false)
    }

    private fun reconcileInstallRecords(cancelPrepared: Boolean) {
        val records = recordDirectories()
            .filter { it.isDirectory && File(it, "record.json").isFile }
            .map { it to readAny(it) }
            .filter { InstallerPending.isPendingInstallState(it.second.optString("state")) }
        if (records.isEmpty()) return
        val currentBootId = bootId()
        for ((directory, record) in records) {
            val unwritten = record.optString("state") in setOf("PREPARING", "PREPARED")
            val observed = if (unwritten) null else InstallerServiceClient.decodeSnapshot(
                InstallerPartitionReader.observeSigning(signingNames(record.optString("signingMode"))))
            val state = InstallerPending.reconciledInstallState(record, observed, currentBootId,
                cancelPrepared = cancelPrepared)
                ?: continue
            record.put("state", state).put("error", "")
            if (state == "RESTORED") record.put("restoreBootId", currentBootId).put("restoreRebootRequired", false)
            if (state == "BOOT_IMAGE_VERIFIED") record.put("verifiedBootId", currentBootId)
            save(directory, record)
        }
    }

    private fun latest(): JSONObject? {
        return recordDirectories().filter { it.isDirectory && File(it, "record.json").isFile }
            .sortedByDescending { File(it, "record.json").lastModified() }.asSequence().map(::readAny)
            .firstOrNull { it.optString("state") in visibleStates ||
                it.optString("state") == "RESTORED" && it.optBoolean("restoreRebootRequired") }
    }

    private fun journal(directory: File, record: JSONObject) = NativeInstallSequence.Journal { state, error, attempted ->
        record.put("state", state).put("error", error).put("attempted", JSONArray(attempted)); save(directory, record)
    }

    private fun resolveOriginals(snapshot: InstallerSnapshot, live: Map<String, File>, output: File,
        selectedManager: String, userId: Int, assets: dev.maplesu.manager.ultrakernel.UltraKernelAssetSource?,
        receipts: File?, retainedOriginals: List<File>, restoreTargets: Set<String>? = null,
        forRemoval: Boolean = false, requireSignedOwnedRestore: Boolean = true): StockOriginalResolver.Resolution {
        val baseline = if (restoreTargets == null) NativeStockBaseline.originals(snapshot, output, assets, receipts, retainedOriginals)
            else NativeStockBaseline.originalsForRestore(snapshot, output, restoreTargets,
                assets = assets, receipts = receipts, retainedOriginals = retainedOriginals)
        baseline?.let {
            return StockOriginalResolver.Resolution(it, "maplesu", snapshot.slotSuffix)
        }
        val resolver = StockOriginalResolver(assets, receipts, retainedOriginals,
            alternate = { InstallerPartitionReader.captureAlternate(snapshot, it) })
        return if (forRemoval) resolver.resolveForRestore(snapshot, live, output, restoreTargets, selectedManager, userId,
                requireSignedOwnedRestore = requireSignedOwnedRestore)
            else if (restoreTargets == null) resolver.resolve(snapshot, live, output, selectedManager, userId)
            else resolver.resolveForRestore(snapshot, live, output, restoreTargets, selectedManager, userId,
                requireSignedOwnedRestore = requireSignedOwnedRestore)
    }

    /** Recompute from the saved PRE, so retries after a stock write validate the same source proof. */
    private fun stockRestoreTargets(record: JSONObject): Set<String> {
        val records = record.optJSONObject("stockRestoreSources")?.keys()?.asSequence()?.sorted()?.map { id ->
            validId(id); read(taskDirectory(id))
        }?.toList().orEmpty()
        return StockRestoreTargets.saved(record, decode(record), records)
    }
    /** Only restore vendor bytes whose same-firmware, same-slot cleanup is proven by a completed batch. */
    private fun retainedVendorOriginal(snapshot: InstallerSnapshot): File? {
        val current = snapshot.partitions["vendor_boot"] ?: return null
        return recordDirectories().asSequence().filter { File(it, "record.json").isFile }.mapNotNull { directory ->
            runCatching {
                val previous = read(directory)
                val old = decode(previous)
                val source = old.partitions["vendor_boot"] ?: return@runCatching null
                if (old.firmware != snapshot.firmware || old.slotSuffix != snapshot.slotSuffix || source.size != current.size ||
                    previous.optString("state") !in setOf("REBOOT_REQUIRED", "REBOOT_REQUESTED", "BOOT_IMAGE_VERIFIED") ||
                    previous.optJSONObject("expected")?.optString("vendor_boot") != current.sha256 || source.sha256 == current.sha256)
                    return@runCatching null
                File(directory, "pre/vendor_boot.img").takeIf { it.length() == source.size && digest(it) == source.sha256 }
            }.getOrNull()
        }.firstOrNull()
    }
    private fun tracked(snapshot: InstallerSnapshot) = names + snapshot.signingPartitions + snapshot.partitions.keys.filter { it == "vendor_boot" }
    private fun signingNames(mode: String): Set<String> = when (mode) {
        "AOSP" -> linkedSetOf("recovery", "vbmeta_system", "vbmeta")
        "NON_CHAINED", "CHAINED" -> setOf("vbmeta")
        else -> emptySet()
    }
    private fun withSigning(snapshot: InstallerSnapshot, signing: Set<String>) = InstallerSnapshot(
        snapshot.kernelRelease, snapshot.firmware, snapshot.slotSuffix, snapshot.selinux, snapshot.partitions, signing)
    private fun digest(file: File) = BlockDeviceTransaction.paddedDigest(file.toPath(), file.length())
    private fun bootId() = File("/proc/sys/kernel/random/boot_id").readText().trim().also {
        check(it.matches(Regex("[0-9a-fA-F-]{36}"))) { "INSTALLER_BOOT_ID_INVALID" }
    }
    private fun validId(id: String) { require(UUID.fromString(id).toString() == id) { "INSTALLER_TASK_INVALID" } }
    private fun code(error: Throwable) = generateSequence(error) { it.cause }
        .filterIsInstance<dev.maplesu.manager.ksu.KsuBootstrapFailure>().firstOrNull()?.message
        ?: error.message?.substringBefore(':')?.takeIf {
            it.matches(Regex("(?:INSTALLER|LENOVO_AVB|ULTRA_KERNEL|ULTRA_INIT)_[A-Z0-9_]+")) }
        ?: "INSTALLER_OPERATION_FAILED"
    private fun image(directory: File, record: JSONObject, name: String) = File(directory,
        (if (record.optJSONObject("originals")?.has(name) == true) "original" else "pre") + "/$name.img")
    private fun pre(directory: File, snapshot: InstallerSnapshot) = snapshot.partitions.filterKeys { it in tracked(snapshot) }.mapValues { (name, row) ->
        NativeInstallSequence.Image(name, File(directory, "pre/$name.img"), row.sha256)
    }
    private fun <T> locked(action: () -> T): T = InstallerDeviceLock.withDeviceLock(root, action)
    private fun taskDirectory(id: String): File {
        val signed = File(signedRoot, id)
        val legacy = File(root, id)
        check(!signed.exists() || !legacy.exists()) { "INSTALLER_TASK_CONFLICT" }
        return if (signed.exists()) signed else legacy
    }
    private fun recordDirectories(): List<File> = listOf(root, signedRoot).filter { it.exists() }.flatMap {
        privateDirectory(it)
        it.listFiles().orEmpty().toList()
    }
    private fun directory(id: String): File { validId(id); return taskDirectory(id).also(::privateDirectory) }
    private fun privateDirectory(file: File) {
        var parent: File? = file.absoluteFile
        while (parent != null) {
            check(!Files.isSymbolicLink(parent.toPath())) { "INSTALLER_STATE_DIRECTORY_INVALID" }
            parent = parent.parentFile
        }
        val stat = Os.lstat(file.path)
        check(OsConstants.S_ISDIR(stat.st_mode) && stat.st_uid == 0 && stat.st_mode and 0x3f == 0) { "INSTALLER_STATE_DIRECTORY_INVALID" }
    }
    private fun readAny(directory: File): JSONObject {
        privateDirectory(directory)
        val file = File(directory, "record.json")
        val stat = Os.lstat(file.path)
        check(OsConstants.S_ISREG(stat.st_mode) && stat.st_uid == 0 && stat.st_mode and 0x3f == 0 &&
            stat.st_size in 1..64 * 1024 && !Files.isSymbolicLink(file.toPath())) { "INSTALLER_RECORD_INVALID" }
        return JSONObject(file.readText()).also {
            check(it.getString("taskId") == directory.name && it.getInt("schema") == 1) { "INSTALLER_RECORD_INVALID" }
        }
    }
    private fun read(directory: File) = readAny(directory).also {
        check(it.optString("kind") == "NATIVE") { "INSTALLER_RECORD_INVALID" }
    }
    private fun save(directory: File, record: JSONObject) {
        FileOutputStream(File(directory, "record.pending")).use { output ->
            Os.fchmod(output.fd, 0x180); output.write(record.toString().toByteArray()); output.fd.sync()
        }
        Files.move(File(directory, "record.pending").toPath(), File(directory, "record.json").toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        InstallerDeviceLock.syncStateDirectory(directory)
    }
    private fun encode(snapshot: InstallerSnapshot) = JSONObject().apply {
        put("kernelRelease", snapshot.kernelRelease); put("firmware", snapshot.firmware); put("slotSuffix", snapshot.slotSuffix); put("selinux", snapshot.selinux)
        put("partitions", JSONObject().apply { snapshot.partitions.forEach { (name, row) ->
            val identity = requireNotNull(row.identity)
            put(name, JSONObject().put("resolvedPath", row.resolvedPath).put("size", row.size).put("sha256", row.sha256)
                .put("deviceNumber", identity.deviceNumber).put("inode", identity.inode).put("filesystem", identity.filesystem))
        } })
    }
    private fun decode(record: JSONObject): InstallerSnapshot {
        val data = record.getJSONObject("snapshot")
        val slot = InstallerSnapshotParser.slot(data.getString("slotSuffix"))
        val rows = data.getJSONObject("partitions")
        return InstallerSnapshot(data.getString("kernelRelease"), data.getString("firmware"), slot, data.getString("selinux"),
            rows.keys().asSequence().associateWith { name ->
                val row = rows.getJSONObject(name)
                InstallerSnapshotParser.partition(name, slot, row.getString("resolvedPath"), row.getLong("size").toString(),
                    row.getString("sha256") + "  " + InstallerSnapshotParser.partitionPath(name, slot),
                    BootPartitionIdentity(row.getLong("deviceNumber"), row.getLong("inode"), row.getLong("filesystem")))
            }, signingNames(record.optString("signingMode")))
    }
    private fun rebind(record: JSONObject, requirePre: Boolean): InstallerSnapshot {
        val old = decode(record)
        val observed = InstallerServiceClient.decodeSnapshot(InstallerPartitionReader.observeSigning(old.signingPartitions))
        val fresh = withSigning(InstallerSnapshot(observed.kernelRelease, observed.firmware, observed.slotSuffix,
            observed.selinux, observed.partitions.filterKeys { it in old.partitions }), old.signingPartitions)
        check(old.slotSuffix == fresh.slotSuffix && old.partitions.keys == fresh.partitions.keys) { "INSTALLER_DEVICE_CHANGED" }
        if (requirePre) check(record.getString("bootId") == bootId()) { "INSTALLER_REBOOT_ALREADY_OBSERVED" }
        old.partitions.forEach { (name, row) ->
            val now = fresh.partitions.getValue(name)
            check(row.resolvedPath == now.resolvedPath && row.size == now.size && row.identity?.deviceNumber == now.identity?.deviceNumber &&
                (record.getString("bootId") != bootId() || row.identity == now.identity) &&
                (!requirePre || row.sha256 == now.sha256)) { "INSTALLER_PARTITION_CHANGED" }
        }
        return fresh
    }
    private fun verifyImages(record: JSONObject, snapshot: InstallerSnapshot, target: Boolean) {
        snapshot.partitions.filterKeys { it in tracked(snapshot) }.forEach { (name, row) ->
            val expected = if (target) record.getJSONObject("expected").getString(name) else decode(record).partitions.getValue(name).sha256
            check(row.sha256 == expected) { "INSTALLER_PARTITION_CHANGED" }
        }
    }
    private fun capture(snapshot: InstallerSnapshot, directory: File): Map<String, File> {
        check(directory.mkdirs()) { "INSTALLER_SNAPSHOT_WRITE_FAILED" }
        Os.chmod(directory.path, 0x1c0)
        val result = linkedMapOf<String, File>()
        withDevices(snapshot, writable = false) { devices -> devices.forEach { (name, device) ->
            val image = File(directory, "$name.img")
            FileOutputStream(image).use { output ->
                Os.fchmod(output.fd, 0x180)
                val buffer = ByteArray(64 * 1024)
                var offset = 0L
                while (offset < device.size()) {
                    val count = device.read(offset, buffer, minOf(buffer.size.toLong(), device.size() - offset).toInt())
                    check(count > 0) { "INSTALLER_DEVICE_READ_FAILED" }
                    output.write(buffer, 0, count); offset += count
                }
                output.fd.sync()
            }
            check(digest(image) == snapshot.partitions.getValue(name).sha256 && BlockDeviceTransaction.digest(device) == digest(image)) { "INSTALLER_PARTITION_CHANGED" }
            result[name] = image
        } }
        InstallerDeviceLock.syncStateDirectory(directory)
        return result
    }
    private fun ioJournal(directory: File, record: JSONObject): (JSONObject) -> Unit = { detail ->
        val failures = record.optJSONArray("ioFailures") ?: JSONArray().also { record.put("ioFailures", it) }
        if (failures.length() < 8) {
            failures.put(detail)
            save(directory, record)
        }
    }
    private fun <T> withDevices(snapshot: InstallerSnapshot, writable: Boolean,
        onIoFailure: (JSONObject) -> Unit = {}, action: (Map<String, BlockDeviceTransaction.Device>) -> T): T {
        val devices = linkedMapOf<String, BlockDeviceTransaction.Device>()
        var failure: Throwable? = null
        try {
            snapshot.partitions.filterKeys { it in tracked(snapshot) }.forEach { (name, _) -> devices[name] = Partition(snapshot, name, writable, onIoFailure) }
            return action(devices)
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            var closeFailure: Throwable? = failure
            for (device in devices.values.toList().asReversed()) {
                try { device.close() } catch (error: Throwable) {
                    if (closeFailure == null) closeFailure = error else closeFailure.addSuppressed(error)
                }
            }
            if (failure == null) closeFailure?.let { throw it }
        }
    }
    private class Partition(private val snapshot: InstallerSnapshot, private val name: String, private val writable: Boolean,
                            private val onIoFailure: (JSONObject) -> Unit) : BlockDeviceTransaction.Device {
        private val row = snapshot.partitions.getValue(name)
        private val identity = requireNotNull(row.identity)
        private val access = BlockPartitionAccess(row.resolvedPath, writable, ::verifyDescriptor)
        private val fd get() = access.descriptor
        override fun size() = row.size
        override fun revalidate() = access.revalidate()
        private fun verifyDescriptor(fd: FileDescriptor) {
            val stat = Os.fstat(fd); val link = Os.stat(row.path)
            check(OsConstants.S_ISBLK(stat.st_mode) && OsConstants.S_ISBLK(link.st_mode) &&
                stat.st_rdev == identity.deviceNumber && stat.st_ino == identity.inode && stat.st_dev == identity.filesystem &&
                link.st_rdev == stat.st_rdev && link.st_ino == stat.st_ino && link.st_dev == stat.st_dev &&
                File(row.path).canonicalPath == row.resolvedPath && Os.lseek(fd, 0, OsConstants.SEEK_END) == row.size) { "INSTALLER_PARTITION_CHANGED" }
            InstallerPartitionReader.verifyRuntime(snapshot, true)
        }
        override fun read(offset: Long, buffer: ByteArray, length: Int) = io("pread", offset, length) { Os.pread(fd, buffer, 0, length, offset) }
        override fun write(offset: Long, buffer: ByteArray, start: Int, length: Int): Int {
            check(writable) { "INSTALLER_READ_ONLY" }
            return io("prepare-or-pwrite", offset, length) {
                Os.pwrite(access.writableDescriptor(), buffer, start, length, offset)
            }
        }
        override fun sync() = io("fsync") { access.sync() }
        override fun close() = io("close-or-restore-readonly") { access.close() }
        private fun <T> io(operation: String, offset: Long = 0, length: Int = 0, action: () -> T): T = try {
            action()
        } catch (error: Exception) {
            val errno = generateSequence(error as Throwable) { it.cause }.take(8)
                .filterIsInstance<android.system.ErrnoException>().firstOrNull()?.errno
            val detail = JSONObject().put("partition", name).put("path", row.resolvedPath)
                .put("operation", operation).put("offset", offset).put("length", length)
                .put("errno", errno ?: JSONObject.NULL).put("exception", error.javaClass.simpleName)
            android.util.Log.e("NativeInstall", detail.toString(), error)
            try { onIoFailure(detail) } catch (journalError: Exception) { error.addSuppressed(journalError) }
            if (error is android.system.ErrnoException) throw java.io.IOException("INSTALLER_DEVICE_IO_FAILED", error)
            throw error
        }
    }
    private fun response(record: JSONObject) = Bundle().apply {
        val currentBoot = bootId()
        val sourceState = record.optString("rebootSourceState")
        val requestBoot = record.optString(if (sourceState == "RESTORED") "restoreBootId" else "bootId")
        val requested = record.optLong("rebootRequestUptimeMs", -1)
        val timedOut = record.getString("state") == "REBOOT_REQUESTED" && requested >= 0 &&
            requestBoot == currentBoot && android.os.SystemClock.elapsedRealtime() - requested >= 30_000
        val restoredAfterBoot = record.getString("state") == "REBOOT_REQUESTED" &&
            sourceState == "RESTORED" && requestBoot != currentBoot
        val observedState = if (timedOut || restoredAfterBoot) sourceState else record.getString("state")
        val state = if (observedState == "BOOT_IMAGE_VERIFIED" && record.optBoolean("vivoCompatibility") &&
            record.optString("vrVerifiedBootId") != currentBoot) "REBOOT_REQUIRED" else observedState
        listOf("kind", "operation", "taskId", "state", "error", "errorDetail", "targetMode", "partition", "backupDirectory", "baselineDirectory", "candidateSha256", "restorationKind")
            .forEach { putString(it, record.optString(it)) }
        record.optJSONObject("normalUserlandFailure")?.let { putString("normalUserlandFailure", it.toString()) }
        if (record.optString("operation") == "RESTORE_STOCK")
            putString("restorationKind", StockRestoreTargets.restorationKind(record).name)
        record.optJSONArray("ioFailures")?.let { putString("ioFailures", it.toString()) }
        putString("state", state)
        if (timedOut) putString("error", "INSTALLER_REBOOT_NOT_OBSERVED")
        putString("rebootSourceState", sourceState)
        putString("currentBootId", currentBoot)
        putString("slotSuffix", decode(record).slotSuffix)
        putString("kernelRelease", decode(record).kernelRelease)
        putString("partitionSha256", record.optJSONObject("expected")?.optString(record.getString("partition")).orEmpty())
        putString("signingMode", record.optString("signingMode"))
        putString("originalSource", record.optString("originalSource"))
        putString("sourceSlot", record.optString("sourceSlot"))
        putBoolean("restoreOriginal", record.optBoolean("restoreOriginal"))
        putBoolean("requestedRestoreOriginal", record.optBoolean("requestedRestoreOriginal", record.optBoolean("restoreOriginal")))
        putString("carrierRestoration", record.optString("carrierRestoration"))
        putStringArrayList("restorePartitions", ArrayList(if (record.optBoolean("restoreOriginal"))
            names.filter { it != record.getString("partition") && record.optJSONObject("originals")?.has(it) == true }
            else emptyList()))
        putParcelableArrayList("partitionRows", ArrayList(decode(record).partitions.filterKeys {
            it in tracked(decode(record))
        }.map { (name, row) -> Bundle().apply {
            putString("name", name); putLong("size", row.size); putString("preSha256", row.sha256)
            putString("sourceSha256", if (record.optJSONObject("originals")?.has(name) == true)
                record.optJSONObject("originals")?.optString(name).orEmpty() else row.sha256)
            putString("targetSha256", record.optJSONObject("expected")?.optString(name).orEmpty().ifBlank { row.sha256 })
        } }))
        putInt("attemptedPartitionCount", record.optJSONArray("attempted")?.length() ?: 0)
        putBoolean("rebootObserved", record.getString("bootId") != currentBoot)
        putString("bootObservation", if (record.getString("bootId") != currentBoot) "REBOOT_OBSERVED" else "SAME_BOOT")
        putBoolean("rebootRequired", state == "REBOOT_REQUIRED" && record.getString("bootId") == currentBoot ||
            state == "RESTORED" && record.optBoolean("restoreRebootRequired") && record.optString("restoreBootId") == currentBoot)
        putBoolean("canCancel", state in setOf("PREPARING", "PREPARED", "FAILED_BEFORE_WRITE"))
        putBoolean("canRestore", state in setOf("WRITING", "RESTORING", "RECOVERY_REQUIRED", "REBOOT_REQUIRED", "REBOOT_REQUESTED", "BOOT_IMAGE_VERIFIED"))
    }

    private fun reconcileReboot(directory: File, record: JSONObject) {
        if (record.optString("state") != "REBOOT_REQUESTED") return
        val previous = record.optString("rebootSourceState")
        val requested = record.optLong("rebootRequestUptimeMs", -1)
        val requestBoot = record.optString(if (previous == "RESTORED") "restoreBootId" else "bootId")
        if (requested >= 0 && requestBoot == bootId() && android.os.SystemClock.elapsedRealtime() - requested >= 30_000) {
            record.put("state", previous).put("error", "INSTALLER_REBOOT_NOT_OBSERVED")
            save(directory, record)
        }
    }
}
