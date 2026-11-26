package dev.maplesu.manager.installer

import dev.maplesu.manager.ultrakernel.UltraKernelAssetSource
import dev.maplesu.manager.ultrakernel.UltraKernelFlow
import dev.maplesu.manager.ultrakernel.UltraKernelOriginalRecovery
import dev.maplesu.manager.ultrakernel.UltraKernelPlan
import dev.maplesu.manager.ultrakernel.UltraKernelSpec
import dev.maplesu.image.BootImageInspector
import dev.maplesu.image.AvbFooter
import dev.maplesu.image.LenovoAvbSigner
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

internal data class AlternateSlotImages(val slot: String, val images: Map<String, File>)

/** Resolves stock sources for both inspection and preparation; never writes a partition. */
internal class StockOriginalResolver(
    private val assets: UltraKernelAssetSource?,
    private val receipts: File?,
    private val originals: List<File> = emptyList(),
    private val alternate: (File) -> AlternateSlotImages? = { null },
) {
    data class Resolution(val originals: Map<String, File>, val family: String, val sourceSlot: String,
                          val kind: ImageRestorationKind = ImageRestorationKind.EXACT_STOCK,
                          val ownershipSource: String = "")

    fun resolveForRestore(snapshot: InstallerSnapshot, live: Map<String, File>, output: File,
                          targets: Set<String>?, selectedManager: String = "", userId: Int = 0,
                          requireSignedOwnedRestore: Boolean = true): Resolution {
        try {
            return resolve(snapshot, live, output, selectedManager, userId, restoreTargets = targets)
        } catch (error: IllegalStateException) {
            if (error.message != "INSTALLER_STOCK_BACKUP_UNAVAILABLE") throw error
            live["init_boot"]?.takeIf { targets == setOf("init_boot") }?.let { current ->
                if (dev.maplesu.image.UltraInitBootProducer.recognizes(current)) {
                    val row = snapshot.partitions.getValue("init_boot")
                    check(current.length() == row.size && UltraKernelSpec.sha256(current) == row.sha256) {
                        "INSTALLER_IMAGE_CHANGED"
                    }
                    val directory = File(output, "owned-init-recovery").apply {
                        check(isDirectory || mkdirs()) { "INSTALLER_STOCK_BACKUP_MISMATCH" }
                    }
                    val restored = dev.maplesu.image.UltraInitBootProducer.restore(current, File(directory, "restored.img"))
                    if (requireSignedOwnedRestore && AvbFooter.parse(current).present) {
                        val main = live["vbmeta"] ?: error("LENOVO_AVB_IMAGE_REQUIRED_vbmeta")
                        check(UltraKernelSpec.sha256(main) == snapshot.partitions["vbmeta"]?.sha256) {
                            "INSTALLER_IMAGE_CHANGED"
                        }
                        // Do not call a pre-install snapshot stock or silently rewrite vbmeta.
                        LenovoAvbSigner.verifyOriginals(mapOf("init_boot" to restored, "vbmeta" to main))
                    }
                    check(restored.length() <= row.size) { "INSTALLER_STOCK_BACKUP_MISMATCH" }
                    if (restored.length() < row.size) {
                        check(!AvbFooter.parse(restored).present) { "INSTALLER_STOCK_BACKUP_MISMATCH" }
                        RandomAccessFile(restored, "rw").use { it.setLength(row.size); it.fd.sync() }
                    }
                    check(UltraKernelSpec.sha256(current) == row.sha256) { "INSTALLER_IMAGE_CHANGED" }
                    return publish(mapOf("init_boot" to restored), mapOf("init_boot" to row.size), output,
                        "maplesu-owned", snapshot.slotSuffix).copy(kind = ImageRestorationKind.OWNED_WRITES_REMOVED,
                        ownershipSource = "IMAGE_BOUND_INIT_BOOT_INPUT")
                }
            }
            val recovery = assets?.let(::UltraKernelOriginalRecovery) ?: throw error
            val boot = live["boot"] ?: throw error
            if (targets != null && "boot" !in targets) throw error
            val row = snapshot.partitions["boot"] ?: throw error
            check(boot.length() == row.size && UltraKernelSpec.sha256(boot) == row.sha256) { "INSTALLER_IMAGE_CHANGED" }
            val recovered = recovery.recover(boot, receipts ?: File(output, "no-receipts"), emptyList(),
                File(output, "owned-recovery"), generatedOnly = true) ?: throw error
            // Image-owned and packaged inverses prove the kernel without local install state.
            if (recovered.source !in setOf(UltraKernelOriginalRecovery.Source.IMAGE_BOUND_KERNEL_REVERSE,
                UltraKernelOriginalRecovery.Source.ARCHIVED_KERNEL_REVERSE,
                UltraKernelOriginalRecovery.Source.IMAGE_BOUND_EXACT_INPUT)) throw error
            val signed = requireSignedOwnedRestore && AvbFooter.parse(boot).present
            val signingDirectory = File(output, "owned-recovery-signed")
            try {
                var image = recovered.image
                if (signed) {
                    val main = live["vbmeta"] ?: error("LENOVO_AVB_IMAGE_REQUIRED_vbmeta")
                    val mainRow = snapshot.partitions["vbmeta"] ?: error("LENOVO_AVB_IMAGE_REQUIRED_vbmeta")
                    check(main.length() == mainRow.size && UltraKernelSpec.sha256(main) == mainRow.sha256) { "INSTALLER_IMAGE_CHANGED" }
                    // Boot-only removal preserves the current chain key and never rewrites main vbmeta.
                    LenovoAvbSigner.verifySigned(LenovoAvbSigner.Mode.CHAINED, "boot", mapOf("boot" to boot, "vbmeta" to main))
                    val outputs = LenovoAvbSigner.sign(LenovoAvbSigner.Mode.CHAINED, "boot", image, boot,
                        emptyMap(), signingDirectory, { bits ->
                            LenovoAvbSigner.decodeKey(requireNotNull(assets).read("maplesu/avb/testkey_rsa$bits.pem"))
                        }, {})
                    check(outputs.keys == setOf("boot")) { "INSTALLER_STOCK_TARGETS_INVALID" }
                    image = outputs.getValue("boot")
                    LenovoAvbSigner.verifySigned(LenovoAvbSigner.Mode.CHAINED, "boot", outputs + ("vbmeta" to main))
                    check(UltraKernelSpec.sha256(main) == mainRow.sha256) { "INSTALLER_IMAGE_CHANGED" }
                }
                check(image.length() <= row.size) { "INSTALLER_STOCK_BACKUP_MISMATCH" }
                if (image.length() < row.size) {
                    check(!AvbFooter.parse(image).present) { "INSTALLER_STOCK_BACKUP_MISMATCH" }
                    RandomAccessFile(image, "rw").use { it.setLength(row.size); it.fd.sync() }
                }
                check(UltraKernelSpec.sha256(boot) == row.sha256) { "INSTALLER_IMAGE_CHANGED" }
                val result = publish(mapOf("boot" to image), mapOf("boot" to row.size), output,
                    "maplesu-owned", snapshot.slotSuffix)
                return result.copy(kind = ImageRestorationKind.OWNED_WRITES_REMOVED,
                    ownershipSource = recovered.source.name)
            } catch (failure: Exception) {
                if (signed) File(output, "boot.img").delete()
                throw failure
            } finally {
                if (signed) {
                    recovered.image.delete()
                    signingDirectory.deleteRecursively()
                }
            }
        }
    }

    fun resolve(snapshot: InstallerSnapshot, live: Map<String, File>, output: File,
                selectedManager: String = "", userId: Int = 0, restoreTargets: Set<String>? = null): Resolution {
        if (restoreTargets != null) check(restoreTargets.isNotEmpty() && restoreTargets.all {
            it in setOf("boot", "init_boot") && it in snapshot.partitions
        }) { "INSTALLER_STOCK_TARGETS_INVALID" }
        val sizes = snapshot.partitions.filterKeys { it in (restoreTargets ?: setOf("boot", "init_boot")) }.mapValues { it.value.size }
        val selectedLive = if (restoreTargets == null) live else live.filterKeys { it in restoreTargets }
        if (restoreTargets != null) check(selectedLive.keys == restoreTargets) { "INSTALLER_STOCK_BACKUP_UNAVAILABLE" }
        val backup = StockBackupResolver(managerPackage = selectedManager.takeIf { it.isNotBlank() }, userId = userId)
        val boot = selectedLive["boot"]
        if (restoreTargets == null) check(boot != null) { "INSTALLER_STOCK_BACKUP_UNAVAILABLE" }
        val recovery = assets?.let(::UltraKernelOriginalRecovery)
        val restored = boot?.let { recovery?.recover(it, receipts ?: File(output, "no-receipts"), originals,
            File(output, "exact-recovery")) }
        val local = runCatching {
            val recoveredBoot = restored?.image?.let { original ->
                val size = sizes.getValue("boot")
                if (original.length() == size) original else {
                    // The receipt proves the complete compact source first. Match the writer's
                    // zero padding, but never relocate or bury a stock AVB footer.
                    check(original.length() < size && !AvbFooter.parse(original).present) { "INSTALLER_STOCK_BACKUP_MISMATCH" }
                    File(output, "recovered-partition.img").also { padded ->
                        original.copyTo(padded, overwrite = true)
                        RandomAccessFile(padded, "rw").use { file ->
                            file.seek(file.length())
                            val zeros = ByteArray(64 * 1024)
                            while (file.length() < size) {
                                check(!Thread.currentThread().isInterrupted) { "INSTALLER_CANCELLED" }
                                file.write(zeros, 0, minOf(zeros.size.toLong(), size - file.length()).toInt())
                            }
                            file.fd.sync()
                        }
                    }
                }
            }
            backup.resolve(if (recoveredBoot == null) selectedLive else selectedLive + ("boot" to recoveredBoot), sizes,
                File(output, "local-originals"))
        }
        local.getOrNull()?.let { result ->
            if (restored != null || result.family == "ksu" && result.restoredPartitions.isNotEmpty()) {
                if (restoreTargets != null) check((result.restoredPartitions +
                    if (restored != null) setOf("boot") else emptySet()) == restoreTargets) { "INSTALLER_STOCK_BACKUP_UNAVAILABLE" }
                return publish(result.originals, sizes, output,
                    if (restored != null) "maplesu-receipt" else result.family, snapshot.slotSuffix)
            }
        }
        // A subset requires per-target proof; alternate-slot authentication remains a full-image path.
        if (restoreTargets != null) throw local.exceptionOrNull() ?: IllegalStateException("INSTALLER_STOCK_BACKUP_UNAVAILABLE")
        val other = if (snapshot.slotSuffix in setOf("_a", "_b")) alternate(File(output, "alternate")) else null
        if (other == null) throw local.exceptionOrNull() ?: IllegalStateException("INSTALLER_STOCK_BACKUP_UNAVAILABLE")
        check(other.slot == if (snapshot.slotSuffix == "_a") "_b" else "_a") { "INSTALLER_STOCK_SLOT_MISMATCH" }
        check(sizes.keys.all { name ->
            other.images[name]?.let { it.isFile && !Files.isSymbolicLink(it.toPath()) && it.length() == sizes[name] } == true
        }) { "INSTALLER_STOCK_SLOT_MISMATCH" }
        val otherBoot = other.images.getValue("boot")
        val raw = UltraKernelPlan.readKernel(otherBoot)
        check(UltraKernelSpec.releaseOf(raw) == snapshot.kernelRelease) { "INSTALLER_STOCK_SLOT_MISMATCH" }
        sizes.keys.forEach { name ->
            val current = live[name] ?: error("INSTALLER_STOCK_SLOT_MISMATCH")
            val original = other.images.getValue(name)
            try {
                backup.verifyUnchangedComponents(current, BootImageInspector.inspect(current), original,
                    BootImageInspector.inspect(original), compareKernel = false)
            } catch (error: Exception) { throw IllegalStateException("INSTALLER_STOCK_SLOT_MISMATCH", error) }
        }
        val source = other.images.filterKeys { it in sizes }
        val clean = RamdiskBaselineCleanliness().verify(source, sizes,
            BaselinePatchEvidence(true, false, null, "alternate-slot"))
        check(clean.clean && clean.patchMarkers.allAbsent) { "INSTALLER_STOCK_SLOT_UNVERIFIED" }
        // A known pristine kernel is sufficient only when every companion is unchanged.
        val pinned = assets?.let { sourceAssets ->
            val flow = UltraKernelFlow(File(output, "pinned-proof"), sourceAssets)
            flow.selectForRelease(snapshot.kernelRelease)?.usable?.any { asset ->
                runCatching { flow.inspect(otherBoot, asset).let { it.pristine && it.baseKernelPinned } }.getOrDefault(false)
            }
        } == true && source.filterKeys { it != "boot" }.all { (name, file) ->
            UltraKernelSpec.sha256(file) == UltraKernelSpec.sha256(live.getValue(name))
        }
        // Otherwise require coverage by the *current* signed vbmeta, not a self-signed footer.
        val authenticated = !pinned && runCatching {
            val vbmeta = other.images["vbmeta"] ?: return@runCatching false
            check(UltraKernelSpec.sha256(vbmeta) == snapshot.partitions["vbmeta"]?.sha256)
            LenovoAvbSigner.verifyOriginals(source + ("vbmeta" to vbmeta))
            true
        }.getOrDefault(false)
        check(pinned || authenticated) { "INSTALLER_STOCK_SLOT_UNVERIFIED" }
        return publish(source, sizes, output, "alternate-slot", other.slot)
    }

    private fun publish(images: Map<String, File>, sizes: Map<String, Long>, output: File,
                        family: String, slot: String): Resolution {
        check(images.keys == sizes.keys && (output.isDirectory || output.mkdirs())) { "INSTALLER_STOCK_BACKUP_MISMATCH" }
        val published = images.mapValues { (name, image) ->
            val target = File(output, "$name.img")
            check(!Files.isSymbolicLink(target.toPath()) && target.canonicalFile.parentFile == output.canonicalFile &&
                images.values.none { it.canonicalFile == target.canonicalFile }) { "INSTALLER_STOCK_BACKUP_MISMATCH" }
            val hash = UltraKernelSpec.sha256(image)
            image.copyTo(target, overwrite = true)
            check(target.length() == sizes[name] && UltraKernelSpec.sha256(target) == hash) { "INSTALLER_STOCK_BACKUP_MISMATCH" }
            target
        }
        return Resolution(published, family, slot)
    }
}
