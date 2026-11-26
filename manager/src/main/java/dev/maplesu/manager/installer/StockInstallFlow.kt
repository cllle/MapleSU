package dev.maplesu.manager.installer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import dev.maplesu.manager.workmode.Hashes
import java.io.File
import java.io.InputStream
import java.io.IOException

/** App glue only: the root service owns all backups, restoration and partition writes. */
internal object StockInstallFlow {
    fun install(
        context: Context,
        taskId: String,
        target: InstallTargetMode,
        partition: String,
        selectedImage: Uri?,
        restoreOriginal: Boolean,
        selectedManager: String?,
        conflictAccepted: Boolean,
        onPrepared: (Bundle) -> Unit,
        produce: (File, dev.maplesu.manager.ultrakernel.UltraKernelDeploymentNamespace?) -> InstallCandidateImage,
        onWriting: () -> Unit,
        signingOptions: LenovoSigningOptions = LenovoSigningOptions(),
        signing: LenovoSigningSession? = null,
        log: (String) -> Unit = {},
        initBootArtifact: dev.maplesu.image.UltraInitBootProducer.Artifact? = null,
        resolveInitBootArtifact: ((File) -> dev.maplesu.image.UltraInitBootProducer.Artifact?)? = null,
        onPrepareSubmitted: () -> Unit = {},
    ): Bundle {
        InstallerServiceClient.beginInstallation(context, taskId, target)
        val restoreManager = if (restoreOriginal) selectedManager.orEmpty() else ""
        val source = File(context.cacheDir, "native-source-$taskId.img")
        var prepareAttempted = false
        var recoveredDeployment: dev.maplesu.manager.ultrakernel.UltraKernelDeploymentNamespace? = null
        var pairedSource: File? = null
        return try { StockInstallAttempt.run(source, partition, prepare = {
            val prepared = InstallerServiceClient.withService(context, persistent = true, rootTarget = target) {
                // A root connection failure before this callback has not sent a prepare request.
                prepareAttempted = true
                onPrepareSubmitted()
                if (signingOptions.enabled) it.prepareNativeSignedInstall(taskId, target.workMode.name, partition,
                    restoreOriginal, restoreManager, conflictAccepted, signingOptions.mode(partition).name)
                else it.prepareNativeInstall(taskId, target.workMode.name, partition, restoreOriginal,
                    restoreManager, conflictAccepted)
            }
            check(prepared.getString("taskId") == taskId && prepared.getString("kind") == "NATIVE" &&
                prepared.getString("state") == "PREPARED") { "INSTALLER_SERVICE_RESPONSE_INVALID" }
            onPrepared(prepared)
            if (target == InstallTargetMode.ULTRA && partition == "init_boot") {
                if (initBootArtifact?.isKmiComponent != true) {
                    @Suppress("DEPRECATION")
                    val boot = prepared.getParcelableArrayList<Bundle>("partitionRows")
                        ?.singleOrNull { it.getString("name") == "boot" }
                    check(boot != null) { "ULTRA_INIT_PAIRED_BOOT_REQUIRED" }
                    // openNativeInstallImage exports this task's final boot source (restored original
                    // on a carrier change), rather than re-reading the still-patched live partition.
                    val paired = File.createTempFile("native-paired-$taskId-", ".img", context.cacheDir)
                    pairedSource = paired
                    run {
                        val meta = Bundle()
                        val descriptor = InstallerServiceClient.withService(context, persistent = true, rootTarget = target) {
                            it.openNativeInstallImage(taskId, "boot", meta)
                        }
                        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                            check(meta.getString("partition") == "boot" && meta.getLong("size") == boot.getLong("size") &&
                                meta.getString("sha256") == boot.getString("sourceSha256")) { "INSTALLER_IMAGE_CHANGED" }
                            copySource(input, paired, boot.getLong("size"))
                        }
                        check(paired.length() == boot.getLong("size") &&
                            Hashes.sha256(paired) == boot.getString("sourceSha256")) { "INSTALLER_IMAGE_CHANGED" }
                        val artifact = requireNotNull(resolveInitBootArtifact?.invoke(paired) ?: initBootArtifact) {
                            "ULTRA_KERNEL_INIT_BOOT_ARTIFACT_REQUIRED:bridge.ko,handoff,target-kernel-loading-contract"
                        }
                        UltraInitBootInstallTarget.requireCompatible(artifact, prepared.getString("kernelRelease").orEmpty(),
                            boot.getString("sourceSha256"), boot.getLong("size"))
                    }
                }
            }
            if (target == InstallTargetMode.ULTRA && prepared.getBoolean("restoreOriginal") && selectedImage == null) {
                val lineage = dev.maplesu.manager.workmode.service.ModeSwitchCandidateInput(
                    dev.maplesu.manager.ultrakernel.UltraKernelContextAssets(context),
                    dev.maplesu.manager.compat.UltraStateStorage.flash(context.filesDir))
                @Suppress("DEPRECATION")
                val rows = prepared.getParcelableArrayList<Bundle>("partitionRows").orEmpty().map { row ->
                    dev.maplesu.manager.workmode.service.ClientPartition(requireNotNull(row.getString("name")), "",
                        row.getLong("size"), row.getString("preSha256").orEmpty(), "", "", "")
                }
                recoveredDeployment = kotlinx.coroutines.runBlocking {
                    lineage.readCaptured(rows, context.cacheDir) { name, destination ->
                        val meta = Bundle()
                        val descriptor = InstallerServiceClient.withService(context, persistent = true, rootTarget = target) {
                            it.openPartitionImage(name, meta)
                        }
                        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                            val observed = InstallerServiceClient.decodeSnapshot(meta)
                            val row = rows.single { it.name == name }
                            check(observed.slotSuffix == prepared.getString("slotSuffix") &&
                                observed.partitions[name]?.size == row.size &&
                                observed.partitions[name]?.sha256 == row.preSha256) { "INSTALLER_IMAGE_CHANGED" }
                            copySource(input, destination, row.size)
                        }
                        true
                    }
                }
            }
        }, acquireSource = {
            if (selectedImage == null) {
                val meta = Bundle()
                val descriptor = InstallerServiceClient.withService(context, persistent = true, rootTarget = target) {
                    it.openNativeInstallImage(taskId, partition, meta)
                }
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    check(meta.getString("partition") == partition &&
                        meta.getLong("size") in 1..dev.maplesu.manager.ImageRepository.MAX_IMAGE_BYTES) {
                        "INSTALLER_SERVICE_RESPONSE_INVALID"
                    }
                    try { copySource(input, source, meta.getLong("size")) }
                    catch (error: IOException) {
                        throw IllegalStateException("INSTALLER_SOURCE_READ_FAILED", error)
                    }
                }
                check(source.length() == meta.getLong("size") &&
                    Hashes.sha256(source) == meta.getString("sha256")) { "INSTALLER_IMAGE_CHANGED" }
            } else {
                val input = checkNotNull(context.contentResolver.openInputStream(selectedImage)) { "DOCUMENT_READ_FAILED" }
                input.use { copySource(it, source, dev.maplesu.manager.ImageRepository.MAX_IMAGE_BYTES) }
            }
        }, produce = { input ->
            val candidate = produce(input, recoveredDeployment)
            val compatible = if (!VivoCompatibility.deviceSupported()) candidate else {
                VivoCompatibility.prepare(context, target.workMode, candidate, mapOf(partition to input),
                    log, operationId = taskId)
            }
            signing?.finish(compatible, input) ?: compatible
        }, beforeCommit = onWriting, commit = { candidate ->
            InstallerServiceClient.withService(context, persistent = true, rootTarget = target) { service ->
                if (signing != null) signing.commit(service, candidate)
                else if (candidate.compatibilityImages.isNotEmpty()) LenovoSigningSession(context, signingOptions,
                    target, taskId, false, {}).use { it.commit(service, candidate) }
                else ParcelFileDescriptor.open(candidate.image, ParcelFileDescriptor.MODE_READ_ONLY).use {
                    service.commitNativeInstall(taskId, it, candidate.sha256)
                }
            }.also { result ->
                check(result.getString("taskId") == taskId && result.getString("kind") == "NATIVE" &&
                    result.getString("state") in setOf("FAILED_BEFORE_WRITE", "RESTORED", "RECOVERY_REQUIRED",
                        "REBOOT_REQUIRED", "REBOOT_REQUESTED", "BOOT_IMAGE_VERIFIED")) {
                    "INSTALLER_SERVICE_RESPONSE_INVALID"
                }
                result.getString("ioFailures")?.takeIf { it.length <= 16_384 }?.let { failures ->
                    runCatching {
                        File(context.filesDir, "installer-io-failures.log").writeText(
                            "taskId=$taskId state=${result.getString("state")}\n$failures\n")
                    }.onFailure { android.util.Log.w("StockInstall", "I/O diagnostics persistence failed", it) }
                }
                dev.maplesu.manager.runtime.ultra.UltraPendingInstallCredentialStore(context).observe(result)
            }
        }, cancel = {
            InstallerServiceClient.withService(context, persistent = true, rootTarget = target) { it.cancelNativeInstall(taskId) }.also {
                check(it.getString("taskId") == taskId && it.getString("state") == "CANCELLED") {
                    "INSTALLER_SERVICE_RESPONSE_INVALID"
                }
                dev.maplesu.manager.runtime.ultra.UltraPendingInstallCredentialStore(context).observe(it)
            }
        }, shouldCancel = { prepareAttempted }, failureAt = { phase, error ->
            val code = installerFailureDiagnostic(error).let {
                if (it == "INSTALLER_OPERATION_FAILED") "INSTALLER_${phase}_FAILED" else it
            }
            val types = generateSequence(error) { it.cause }.take(8).joinToString(" → ") { it.javaClass.simpleName }
            runCatching {
                val journal = File(context.filesDir, "installer-io-failures.log")
                val previous = journal.takeIf { it.isFile && it.length() <= 16_384 }?.readText().orEmpty().takeLast(8192)
                journal.writeText(previous + "\ntaskId=$taskId phase=$phase code=$code causes=$types\n")
            }
            log("$code · $phase")
            if (error is InstallerServiceClient.AuthorizationFailure || error is InterruptedException ||
                error is java.util.concurrent.CancellationException || error is Error ||
                installerFailureDiagnostic(error) != "INSTALLER_OPERATION_FAILED") error
            else IllegalStateException(code, error)
        }) } finally { pairedSource?.delete() }
    }

    private fun copySource(input: InputStream, destination: File, maximum: Long) {
        destination.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            var size = 0L
            while (true) {
                check(!Thread.currentThread().isInterrupted) { "INSTALLER_CANCELLED" }
                val count = input.read(buffer)
                if (count < 0) break
                check(count > 0) { "DOCUMENT_READ_FAILED" }
                check(count <= maximum - size) { "IMAGE_SIZE_LIMIT" }
                output.write(buffer, 0, count)
                size += count
            }
            check(size > 0) { "DOCUMENT_READ_FAILED" }
            output.fd.sync()
        }
    }
}
