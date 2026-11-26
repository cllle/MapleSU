package dev.maplesu.manager.installer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.ParcelFileDescriptor
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import dev.maplesu.manager.ImageRepository
import dev.maplesu.installer.BlockDeviceTransaction
import dev.maplesu.manager.workmode.service.ModeSwitchRuntime
import dev.maplesu.manager.workmode.service.ClientStatus
import dev.maplesu.manager.workmode.service.SwitchOutcome
import dev.maplesu.manager.workmode.ModeRuntime
import dev.maplesu.manager.workmode.WorkMode
import dev.maplesu.manager.runtime.RootExec
import dev.maplesu.manager.runtime.HookRootExecutor
import dev.maplesu.manager.runtime.PageReadDeadline
import dev.maplesu.manager.runtime.RootServiceBootstrap
import dev.maplesu.manager.runtime.SuRootCandidates
import dev.maplesu.manager.runtime.ultra.ProcessSuExec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Launch libsu through the explicit installation su session; never through the Manager shell. */
internal object InstallerServiceClient {
    class AuthorizationFailure(cause: Exception) : IllegalStateException("INSTALLER_ROOT_REQUIRED", cause)
    class ServiceFailure(val phase: String, cause: Exception) : IllegalStateException("INSTALLER_SERVICE_${phase}_FAILED", cause)

    private const val KEY_LAST_TASK_ID = "lastTaskId"
    private const val KEY_LEGACY_TASK_ID_RETIRED = "legacyTaskIdRetired"

    /** 「这次安装的目标工作模式」事务记录（只保留当前任务，避免 prefs 无界增长）。 */
    private const val KEY_INSTALL_TARGET_MODE = "installTargetMode"
    private const val KEY_INSTALL_TARGET_TASK = "installTargetTaskId"

    fun observeBootChain(context: Context): InstallerSnapshot =
        withService(context) { decode(observe(it)) }

    fun analyzeCurrentBoot(context: Context): InstallerSnapshot = analyzeCurrentPartition(context, "boot")

    fun analyzeCurrentPartition(context: Context, partition: String): InstallerSnapshot = withService(context) { service ->
        require(partition in InstallPartition.ALLOWED_WIRE) { "INSTALLER_PARTITION_INVALID" }
        val response = Bundle()
        val descriptor = if (partition == "boot") service.openBootImage(response)
            else service.openPartitionImage(partition, response)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            val snapshot = decode(response)
            val boot = snapshot.partitions[partition] ?: error("INSTALLER_PARTITION_INVALID")
            val verified = ImageRepository(context).importAndVerifyInstalledPartition(
                input, boot.size, boot.sha256, partition, snapshot.kernelRelease)
            check(verified.report.sha256 == boot.sha256) {
                "INSTALLER_IMAGE_CHANGED"
            }
            snapshot
        }
    }

    /** Keep the paired kernel outside the mutable imported-image workspace for the whole transaction. */
    @Synchronized
    fun capturePairedBoot(context: Context, expected: InstallerSnapshot, destination: File): File =
        capturePairedPartition(context, expected, "boot", destination)

    /** A reselected original must be the verified inverse of this same live init_boot. */
    fun verifyInitBootSource(context: Context, expected: InstallerSnapshot, source: File):
        dev.maplesu.manager.ultrakernel.UltraKernelDeploymentNamespace? {
        val current = expected.partitions["init_boot"] ?: error("ULTRA_INIT_PAIRED_BOOT_REQUIRED")
        return dev.maplesu.manager.ultrakernel.UltraInitBootSourceBinding.requireSource(
            source, current.size, current.sha256, context.cacheDir) {
            capturePairedPartition(context, expected, "init_boot",
                File(context.filesDir, "ultra-init-current-${current.sha256}.img"))
        }
    }

    @Synchronized
    private fun capturePairedPartition(context: Context, expected: InstallerSnapshot,
                                       partition: String, destination: File): File =
        withService(context) { service ->
            require(partition == "boot" || partition == "init_boot") { "INSTALLER_PARTITION_INVALID" }
            val boot = expected.partitions[partition] ?: error("ULTRA_INIT_PAIRED_BOOT_REQUIRED")
            val parent = requireNotNull(destination.parentFile).canonicalFile
            check(parent.toPath().startsWith(context.filesDir.canonicalFile.toPath()) &&
                destination.name.matches(Regex("[A-Za-z0-9._-]+"))) { "ULTRA_INIT_PAIRED_BOOT_PATH" }
            Files.createDirectories(parent.toPath())
            fun same(actual: InstallerSnapshot) {
                check(actual.slotSuffix == expected.slotSuffix && actual.kernelRelease == expected.kernelRelease &&
                    actual.firmware == expected.firmware && actual.partitions == expected.partitions) {
                    "INSTALLER_DEVICE_CHANGED"
                }
            }
            fun verify(file: File) {
                check(!Files.isSymbolicLink(file.toPath()) && file.isFile && file.length() == boot.size &&
                    dev.maplesu.manager.ultrakernel.UltraKernelSpec.sha256(file) == boot.sha256) {
                    "ULTRA_INIT_PAIRED_BOOT_CHANGED"
                }
            }
            val response = Bundle()
            val descriptor = if (partition == "boot") service.openBootImage(response)
                else service.openPartitionImage(partition, response)
            var pending: File? = null
            try {
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    same(decode(response))
                    if (Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) verify(destination)
                    else {
                        val temporary = Files.createTempFile(parent.toPath(), ".paired-boot-", ".pending").toFile()
                        pending = temporary
                        FileOutputStream(temporary).use { output ->
                            val digest = MessageDigest.getInstance("SHA-256")
                            val buffer = ByteArray(64 * 1024)
                            var length = 0L
                            while (true) {
                                check(!Thread.currentThread().isInterrupted) { "INSTALLER_CANCELLED" }
                                val count = input.read(buffer)
                                if (count < 0) break
                                check(count > 0 && count <= boot.size - length) { "INSTALLER_IMAGE_CHANGED" }
                                output.write(buffer, 0, count); digest.update(buffer, 0, count); length += count
                            }
                            check(length == boot.size && digest.digest().joinToString("") { "%02x".format(it) } == boot.sha256) {
                                "INSTALLER_IMAGE_CHANGED"
                            }
                            output.fd.sync()
                            android.system.Os.fchmod(output.fd, 0x100)
                        }
                        verify(temporary)
                        same(decode(observe(service)))
                        try { Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE) }
                        catch (error: java.nio.file.FileAlreadyExistsException) { verify(destination) }
                    }
                }
                same(decode(observe(service)))
                verify(destination)
                destination
            } finally { pending?.let { Files.deleteIfExists(it.toPath()) } }
        }

    /** Return only a carrier proven by a current receipt or verified owned bytes in the live image. */
    fun installedCarrierPartition(context: Context, observed: InstallerSnapshot): String? {
        val receipt = installationStatus(context)
        val partition = receipt.getString("partition")
        if (receipt.getString("targetMode") == "LITE" && receipt.getString("operation") == "INSTALL" &&
            receipt.getString("state") in setOf("REBOOT_REQUIRED", "REBOOT_REQUESTED", "BOOT_IMAGE_VERIFIED", "VERIFIED", "BOOT_VERIFIED") &&
            !receipt.getBoolean("operationActive") && receipt.getString("slotSuffix") == observed.slotSuffix &&
            partition in InstallPartition.ALLOWED_WIRE && partition in observed.partitions &&
            receipt.getString("partitionSha256") == observed.partitions[partition]?.sha256) return requireNotNull(partition)
        if ("init_boot" in observed.partitions) {
            val current = analyzeCurrentPartition(context, "init_boot")
            check(current.slotSuffix == observed.slotSuffix &&
                current.partitions.getValue("init_boot").sha256 == observed.partitions.getValue("init_boot").sha256) {
                "INSTALLER_DEVICE_CHANGED"
            }
            if (dev.maplesu.image.UltraInitBootProducer.recognizes(File(context.filesDir, "images/imported.img")))
                return "init_boot"
        }
        if ("boot" in observed.partitions) {
            val current = analyzeCurrentPartition(context, "boot")
            check(current.slotSuffix == observed.slotSuffix &&
                current.partitions.getValue("boot").sha256 == observed.partitions.getValue("boot").sha256) {
                "INSTALLER_DEVICE_CHANGED"
            }
            val image = File(context.filesDir, "images/imported.img")
            if (dev.maplesu.image.UltraInitBootProducer.recognizes(image)) return "boot"
            val recovery = dev.maplesu.manager.ultrakernel.UltraKernelOriginalRecovery(
                dev.maplesu.manager.ultrakernel.UltraKernelContextAssets(context))
            val receipts = dev.maplesu.manager.compat.UltraStateStorage.flash(context.filesDir)
            if (recovery.installedSpec(image, receipts) != null ||
                recovery.generatedKernelWithDeployment(image, receipts) != null) return "boot"
        }
        return null
    }

    /** Legacy carrier selection keeps boot as its operational fallback when no carrier is proven. */
    fun installedPartition(context: Context, observed: InstallerSnapshot): String =
        installedCarrierPartition(context, observed) ?: "boot"

    fun analyzeCurrentCarrier(context: Context): Pair<String, InstallerSnapshot> {
        val observed = observeBootChain(context)
        val partition = installedPartition(context, observed)
        val snapshot = analyzeCurrentPartition(context, partition)
        check(snapshot.slotSuffix == observed.slotSuffix && snapshot.partitions[partition]?.sha256 == observed.partitions[partition]?.sha256) {
            "INSTALLER_DEVICE_CHANGED"
        }
        return partition to snapshot
    }

    /** Copies only an existing recovery image; the service never creates a fresh partition backup. */
    fun installBoot(context: Context, candidate: File, snapshot: InstallerSnapshot, recovery: File, taskId: String): Bundle {
        rememberTask(context, taskId)
        val prepared = prepareBootInstall(context, candidate, snapshot, recovery, taskId)
        return if (prepared.getString("state") == "PREPARED") {
            withService(context, persistent = true) { it.commitBootInstall(taskId) }
        } else {
            prepared
        }.also { dev.maplesu.manager.runtime.ultra.UltraPendingInstallCredentialStore(context).observe(it) }
    }

    /**
     * Stages the candidate and the rollback image inside the root-owned task and stops at
     * `PREPARED`. Since 2026-11-01 the caller does not ask the user again: [InstallerFlashViewModel]
     * issues [continueInstallation] automatically for the same task id (one tap = one flash), and
     * only a failure of that commit becomes visible. `recovery` must be a byte-exact image of the
     * live partition, which the service re-verifies against its own digest before the task becomes
     * PREPARED.
     */
    fun prepareBootInstall(context: Context, candidate: File, snapshot: InstallerSnapshot,
                           recovery: File, taskId: String): Bundle {
        rememberTask(context, taskId)
        check(recovery.isFile) { "INSTALLER_RECOVERY_REQUIRED" }
        val candidateSha256 = BlockDeviceTransaction.paddedDigest(candidate.toPath(), candidate.length())
        return withService(context, persistent = true) { service ->
            ParcelFileDescriptor.open(candidate, ParcelFileDescriptor.MODE_READ_ONLY).use { image ->
                ParcelFileDescriptor.open(recovery, ParcelFileDescriptor.MODE_READ_ONLY).use { original ->
                    val prepared = service.prepareBootInstall(image, original, encode(snapshot), candidateSha256, taskId)
                    check(prepared.getString("taskId") == taskId) { "INSTALLER_SERVICE_RESPONSE_INVALID" }
                    prepared
                }
            }
        }
    }

    fun lastTaskId(context: Context): String? = routing(context).getString(KEY_LAST_TASK_ID, null)
        ?: prefs(context).getString(KEY_LAST_TASK_ID, null)

    private fun routing(context: Context) = context.getSharedPreferences("installer-routing", Context.MODE_PRIVATE)

    private fun stockRestoreAfterBoot(context: Context, taskId: String?): Bundle? {
        if (taskId == null) return null
        val saved = routing(context)
        if (saved.getString("stockRestoreTaskId", null) != taskId) return null
        val previousBoot = saved.getString("stockRestoreBootId", null).orEmpty()
        val currentBoot = dev.maplesu.manager.workmode.detect.ProcBootIdSource.read()
        val restoration = imageRestorationAfterBoot(previousBoot, currentBoot, saved.getString("stockRestoreKind", null))
            ?: return null
        // The service verified the image write before reboot. The new boot may have no
        // root service; observing its new boot does not require MapleSU to remain installed.
        return Bundle().apply {
            putString("taskId", taskId)
            putString("kind", "NATIVE")
            putString("operation", "RESTORE_STOCK")
            putString("restorationKind", restoration.name)
            putString("state", restoration.completedState)
            putString("currentBootId", currentBoot)
            putBoolean("rebootObserved", true)
        }
    }

    private fun clearStockRestoreReceipt(context: Context) {
        routing(context).edit().remove("stockRestoreTaskId").remove("stockRestoreBootId").remove("stockRestoreKind").commit()
    }

    private fun rememberStockRestoreReceipt(context: Context, status: Bundle) {
        if (status.getString("operation") == "RESTORE_STOCK" &&
            status.getString("error").isNullOrEmpty() &&
            !status.getBoolean("rebootObserved") &&
            status.getString("state") in setOf("REBOOT_REQUIRED", "REBOOT_REQUESTED", "BOOT_IMAGE_VERIFIED")) {
            routing(context).edit().putString("stockRestoreTaskId", status.getString("taskId"))
                .putString("stockRestoreBootId", status.getString("currentBootId"))
                .putString("stockRestoreKind", ImageRestorationKind.fromWire(status.getString("restorationKind")).name).commit()
        }
    }

    fun rememberModeSwitchTask(context: Context, taskId: String) {
        rememberTask(context, taskId)
        check(routing(context).edit().putString("modeSwitchTaskId", taskId).commit()) {
            "INSTALLER_TASK_SAVE_FAILED"
        }
    }

    private fun modeTask(context: Context, taskId: String?) = !taskId.isNullOrBlank() &&
        routing(context).getString("modeSwitchTaskId", null) == taskId

    private fun modeFacade(context: Context) = ModeSwitchRuntime.facadeOrNull()
        ?: ModeSwitchRuntime.initialize(context.applicationContext)

    private fun modeStatus(status: ClientStatus?, taskId: String, operationActive: Boolean = false): Bundle = Bundle().apply {
        val candidate = status?.carrierCandidate()
        putString("kind", "MODE_SWITCH")
        putString("taskId", taskId)
        putString("state", status?.state ?: "NONE")
        putString("error", status?.error.orEmpty())
        putString("candidateSha256", candidate?.targetSha256)
        putString("partition", candidate?.name)
        putString("partitionSha256", candidate?.targetSha256)
        putString("targetMode", status?.toMode)
        putString("slotSuffix", status?.slotSuffix)
        putString("signingMode", status?.signingMode)
        putString("operation", "INSTALL")
        putBoolean("rebootRequired", status?.rebootRequired == true)
        putBoolean("canRestore", status?.canRollback == true)
        putBoolean("canCancel", !operationActive && status?.state in setOf("BEGIN", "SNAPSHOT", "PRODUCE"))
        putBoolean("rebootObserved", status?.bootObserved == true)
        putBoolean("operationActive", operationActive)
    }

    /**
     * One-shot retirement of the persisted task id (2026-10-29).
     *
     * The settings page no longer surfaces the boot-install transaction, so an id written by an
     * earlier build can only produce a "task status read failed" card on a Ultra kernel. Drop it
     * exactly once - guarded by its own flag so a future flashing flow reusing [lastTaskId] is
     * not wiped on every start.
     */
    fun retireLegacyTaskId(context: Context) {
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_LEGACY_TASK_ID_RETIRED, false)) return
        prefs.edit().remove(KEY_LAST_TASK_ID).putBoolean(KEY_LEGACY_TASK_ID_RETIRED, true).commit()
    }

    fun beginInstallation(context: Context, taskId: String, rootTarget: InstallTargetMode? = null) {
        val previous = lastTaskId(context)
        if (previous != taskId) {
            // A new explicit attempt owns a new snapshot. Old task receipts remain queryable;
            // neither a stale receipt nor an unavailable old root channel is a prerequisite.
            clearStockRestoreReceipt(context)
        }
        rememberTask(context, taskId)
        rootTarget?.let { rememberInstallTarget(context, taskId, it) }
    }

    fun rememberTask(context: Context, taskId: String) {
        require(java.util.UUID.fromString(taskId).toString() == taskId) { "INSTALLER_TASK_INVALID" }
        check(prefs(context).edit().putString(KEY_LAST_TASK_ID, taskId).commit()) {
            "INSTALLER_TASK_SAVE_FAILED"
        }
        check(routing(context).edit().putString(KEY_LAST_TASK_ID, taskId).commit()) {
            "INSTALLER_TASK_SAVE_FAILED"
        }
    }

    /**
     * 把「本次安装的目标工作模式」写进安装事务记录（按 taskId 校验，进程重启后仍可回查）。
     *
     * 这是**快照**：安装开始时写一次，之后用户在安装页改选择不会改写它（换目标 = 换 task）。
     * 只保留当前任务的两条键（`installTargetMode` / `installTargetTaskId`），不随任务数增长。
     * 写失败不阻断安装（record 只用于可观测性，不是写分区的前置条件）。
     */
    fun rememberInstallTarget(context: Context, taskId: String, mode: InstallTargetMode) {
        runCatching {
            routing(context).edit()
                .putString(KEY_INSTALL_TARGET_TASK, taskId)
                .putString(KEY_INSTALL_TARGET_MODE, mode.wire)
                .commit()
            prefs(context).edit()
                .putString(KEY_INSTALL_TARGET_TASK, taskId)
                .putString(KEY_INSTALL_TARGET_MODE, mode.wire)
                .commit()
        }
    }

    /** 回查**该 task** 的目标工作模式；没有记录 / 记录属于别的任务 / 坏值 → null（不猜成 Ultra）。 */
    fun installTargetOf(context: Context, taskId: String): InstallTargetMode? {
        val stores = InstallerPrefsPolicy.targetRecordNames(InstallerPrefsRouter(context).prefsName()).map {
            context.getSharedPreferences(it, Context.MODE_PRIVATE)
        }
        return installTargetFromRecords(taskId, stores.map {
            it.getString(KEY_INSTALL_TARGET_TASK, null) to it.getString(KEY_INSTALL_TARGET_MODE, null)
        })
    }

    /**
     * P1：installer 状态按模式隔离（A→`installer` 原名逐字节等价；B→`installer_B`；
     * 未判定→中性+同一份 lastKnown/证据裁决）。路由策略见 [InstallerPrefsPolicy]。
     */
    private fun prefs(context: Context) = InstallerPrefsRouter(context).prefs()

    fun installationStatus(context: Context, taskId: String? = lastTaskId(context),
                           rootTarget: InstallTargetMode? = null): Bundle {
        stockRestoreAfterBoot(context, taskId)?.let { return it }
        val status = if (modeTask(context, taskId)) runBlocking(Dispatchers.IO) {
            val facade = modeFacade(context)
            modeStatus(facade.statusFor(requireNotNull(taskId)), taskId, facade.operationActive)
        } else withService(context, persistent = true,
            rootTarget = rootTarget ?: taskId?.let { installTargetOf(context, it) }) { it.getBootInstall(taskId ?: "") }
        rememberStockRestoreReceipt(context, status)
        dev.maplesu.manager.runtime.ultra.UltraPendingInstallCredentialStore(context).observe(status)
        return status
    }

    /** Used only by an explicit continue action for this exact persisted PREPARED task. */
    fun continueInstallation(context: Context, taskId: String): Bundle = withService(context, persistent = true) {
        val status = it.getBootInstall(taskId)
        check(status.getString("kind") != "NATIVE") { "INSTALLER_PATCH_INTERRUPTED" }
        check(status.getString("state") == "PREPARED") { "INSTALLER_TRANSACTION_NOT_PREPARED" }
        it.commitBootInstall(taskId).also { status ->
            dev.maplesu.manager.runtime.ultra.UltraPendingInstallCredentialStore(context).observe(status)
        }
    }

    fun cancelInstallation(context: Context, taskId: String): Bundle {
        if (modeTask(context, taskId)) return runBlocking(Dispatchers.IO) {
            val result = modeFacade(context).abort(taskId)
            check(result?.ok == true) { result?.code ?: "INSTALLER_CANCELLATION_NOT_ALLOWED" }
            modeStatus(result.status, taskId).also {
                dev.maplesu.manager.runtime.ultra.UltraPendingInstallCredentialStore(context).observe(taskId, "CANCELLED", null)
            }
        }
        return withService(context, persistent = true) {
            it.cancelBootInstall(taskId).also { result ->
                check(result.getString("taskId") == taskId && result.getString("state") == "CANCELLED") {
                    "INSTALLER_SERVICE_RESPONSE_INVALID"
                }
                dev.maplesu.manager.runtime.ultra.UltraPendingInstallCredentialStore(context).observe(result)
            }
        }
    }

    fun verifyInstallation(context: Context, taskId: String): Bundle {
        stockRestoreAfterBoot(context, taskId)?.let { return it }
        if (modeTask(context, taskId)) return runBlocking(Dispatchers.IO) {
            val facade = modeFacade(context)
            val verified = facade.retryVerify(taskId)
            modeStatus(facade.statusFor(taskId), taskId).apply {
                putBoolean("runtimeVerified", verified is SwitchOutcome.Verified)
                if (verified is SwitchOutcome.Verified) putString("state", "BOOT_VERIFIED")
            }
        }
        val recordedTarget = installTargetOf(context, taskId)
        val image = withService(context, persistent = true, rootTarget = recordedTarget) { it.verifyBootInstall(taskId) }
        check(image.getString("taskId") == taskId && image.getBoolean("rebootObserved") &&
            image.getString("state") == "BOOT_IMAGE_VERIFIED") { "INSTALLER_SERVICE_RESPONSE_INVALID" }
        if (image.getString("operation") == "RESTORE_STOCK") return image.apply {
            putString("state", ImageRestorationKind.fromWire(getString("restorationKind")).completedState)
        }
        val imageTarget = WorkMode.fromWire(image.getString("targetMode"))?.let(InstallTargetMode::fromWorkMode)
        check(recordedTarget == null || imageTarget == null || recordedTarget == imageTarget) {
            "INSTALLER_TARGET_MODE_MISMATCH"
        }
        val target = imageTarget ?: recordedTarget
        val verified = runCatching {
            val result = RootExec.execute(context,
                "/system/bin/id -u\n/system/bin/cat /proc/sys/kernel/random/boot_id")
            installerRuntimeIdentityVerified(target, image.getString("currentBootId").orEmpty(), result)
        }.getOrDefault(false)
        return image.apply {
            putBoolean("runtimeVerified", verified)
            if (verified) putString("state", "BOOT_VERIFIED")
            else putString("error", if (target == null) "INSTALLER_TARGET_MODE_UNKNOWN"
                else "INSTALLER_MAPLESU_RUNTIME_NOT_VERIFIED")
        }
    }

    fun restoreInstallation(context: Context, taskId: String): Bundle {
        clearStockRestoreReceipt(context)
        val result = if (modeTask(context, taskId)) runBlocking(Dispatchers.IO) {
            val facade = modeFacade(context)
            check(facade.statusFor(taskId)?.switchId == taskId) { "INSTALLER_TASK_INVALID" }
            val restored = facade.rollback(taskId)
            check(restored?.ok == true) { restored?.code ?: "INSTALLER_RECOVERY_FAILED" }
            modeStatus(restored?.status, taskId).apply {
                if (restored?.ok == true) putString("state", "RESTORED")
            }
        } else withService(context, persistent = true) { service ->
            dev.maplesu.manager.runtime.ultra.UltraPendingInstallCredentialStore(context).observe(taskId, "RESTORING", null)
            service.restoreBootInstall(taskId)
        }
        dev.maplesu.manager.runtime.ultra.UltraPendingInstallCredentialStore(context).observe(result)
        return result
    }

    fun restoreStockImages(context: Context, taskId: String): Bundle {
        beginInstallation(context, taskId)
        val mode = ModeRuntime.active ?: error("INSTALLER_MODE_UNAVAILABLE")
        return withService(context, persistent = true) { it.restoreStockImages(taskId, mode.name) }
            .also { rememberStockRestoreReceipt(context, it) }
    }

    fun rebootAfterInstallation(context: Context, taskId: String? = lastTaskId(context)): Boolean {
        if (taskId == null) return false
        if (modeTask(context, taskId)) return runBlocking(Dispatchers.IO) { modeFacade(context).reboot(taskId) }
        return withService(context, persistent = true) { service ->
            val status = service.getBootInstall(taskId)
            dev.maplesu.manager.runtime.ultra.UltraPendingInstallCredentialStore(context).observe(status)
            if (!status.getBoolean("rebootRequired")) false else service.rebootAfterBootInstall(taskId)
        }
    }

    internal fun encode(snapshot: InstallerSnapshot) = Bundle().apply {
        putString("slotSuffix", snapshot.slotSuffix)
        putString("kernelRelease", snapshot.kernelRelease)
        putString("firmware", snapshot.firmware)
        putString("selinux", snapshot.selinux)
        putStringArrayList("partitions", ArrayList(snapshot.partitions.keys))
        snapshot.partitions.forEach { (name, partition) ->
            val identity = requireNotNull(partition.identity) { "INSTALLER_DEVICE_IDENTITY_REQUIRED" }
            putBundle(name, Bundle().apply {
                putString("resolvedPath", partition.resolvedPath)
                putLong("size", partition.size)
                putString("sha256", partition.sha256)
                putLong("deviceNumber", identity.deviceNumber)
                putLong("inode", identity.inode)
                putLong("filesystem", identity.filesystem)
            })
        }
    }

    internal fun observe(service: IInstallerService): Bundle {
        check(Looper.myLooper() != Looper.getMainLooper()) { "INSTALLER_BACKGROUND_THREAD_REQUIRED" }
        return service.observeBootChain()
    }

    fun <T> withService(context: Context, persistent: Boolean = false,
                        rootTarget: InstallTargetMode? = null, action: (IInstallerService) -> T): T {
        check(Looper.myLooper() != Looper.getMainLooper()) { "INSTALLER_BACKGROUND_THREAD_REQUIRED" }
        val handler = Handler(Looper.getMainLooper())
        val connected = CompletableFuture<IBinder>()
        val task = CompletableFuture<Shell.Task?>()
        var phase = "BIND"
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { connected.complete(binder) }
            override fun onServiceDisconnected(name: ComponentName) {
                connected.completeExceptionally(IllegalStateException("INSTALLER_SERVICE_DISCONNECTED"))
            }
            override fun onNullBinding(name: ComponentName) {
                connected.completeExceptionally(IllegalStateException("INSTALLER_SERVICE_NULL_BINDING"))
            }
            override fun onBindingDied(name: ComponentName) {
                connected.completeExceptionally(IllegalStateException("INSTALLER_SERVICE_DISCONNECTED"))
            }
        }
        fun useConnectedService(): T {
            phase = "CONNECT"
            val binder = connected.get(20, TimeUnit.SECONDS)
            check(binder.isBinderAlive) { "INSTALLER_SERVICE_DISCONNECTED" }
            phase = "IPC"
            return action(IInstallerService.Stub.asInterface(binder))
        }
        try {
            handler.post {
                try {
                    val intent = Intent(context, InstallerPartitionService::class.java).apply {
                        if (persistent) addCategory(RootService.CATEGORY_DAEMON_MODE)
                    }
                    task.complete(RootService.bindOrTask(intent, context.mainExecutor, connection))
                } catch (error: Exception) { task.completeExceptionally(error) }
            }
            // Reconnect to an authenticated surviving daemon without needing another su launch.
            // A fresh process is still launched only through the explicit existing-provider session.
            val launch = task.get(5, TimeUnit.SECONDS)
            if (launch != null) {
                val route = installerServiceLaunchRoute(ModeRuntime.active, rootTarget,
                    rootTarget == InstallTargetMode.ULTRA && HookRootExecutor.production(context).available(),
                    dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe.usesUltraBackend(context))
                val script = ByteArrayOutputStream()
                launch.run(script, ByteArrayInputStream(ByteArray(0)), ByteArrayInputStream(ByteArray(0)))
                val command = script.toString(Charsets.UTF_8.name())
                val candidates = buildList {
                    if (route == InstallerServiceLaunchRoute.HOOK) {
                        add(RootServiceBootstrap.Candidate("hook") { probe ->
                            runBlocking(PageReadDeadline.context(5_000L)) {
                                RootServiceBootstrap.Result(RootExec.executeHook(context, probe))
                            }
                        })
                    }
                    // Use the same inventory as SU detection, even with a missing/stale SDK key.
                    // Each explicit installation probe has its own budget; one timeout cannot
                    // consume the later sources' chance to establish root and framework access.
                    for (candidate in SuRootCandidates.all(context)) {
                        add(RootServiceBootstrap.Candidate(candidate.source) { probe ->
                            val result = ProcessSuExec.exec(candidate.argv, 5_000L, "$probe\n")
                            RootServiceBootstrap.Result(
                                if (result.ok) 0 else result.exitCode.takeUnless { it == 0 } ?: -1,
                                result.output, result.failure ?: if (result.timedOut) "SU_TIMEOUT" else "")
                        })
                    }
                    if (route == InstallerServiceLaunchRoute.ROOT_EXEC) {
                        add(RootServiceBootstrap.Candidate("runtime") { probe ->
                            runBlocking(PageReadDeadline.context(5_000L)) {
                                RootServiceBootstrap.Result(RootExec.execute(context, probe))
                            }
                        })
                    } else if (route == InstallerServiceLaunchRoute.SHELL) {
                        add(RootServiceBootstrap.Candidate("installer-shell") { probe ->
                            InstallerRootSession.open(context).use { session ->
                                // Keep libsu's interactive shell alive for its own job receipt.
                                val result = session.execute("(\n$probe\n)")
                                RootServiceBootstrap.Result(result.code, result.out.joinToString("\n"),
                                    stderr = result.err.joinToString("\n"))
                            }
                        })
                    }
                }
                try {
                    RootServiceBootstrap.launch(command, candidates) { source, stage, attempt ->
                        // Fixed source/phase/status only: no commands, keys or helper paths.
                        Log.i("MapleSUInstaller", "service source=$source phase=$stage code=${attempt.code}")
                    }
                } catch (error: RootServiceBootstrap.Failure) {
                    Log.w("MapleSUInstaller", "service phase=${error.phase} code=${error.result.code} reason=${error.message}")
                    try {
                        File(context.filesDir, "installer-service-failure.json").writeText(org.json.JSONObject()
                            .put("source", error.source).put("phase", error.phase)
                            .put("reason", error.message).put("exitCode", error.result.code)
                            .put("output", error.result.output).put("stderr", error.result.stderr)
                            .put("detail", error.result.detail).toString())
                    } catch (diagnostic: Exception) {
                        rethrowInstallerCancellation(diagnostic)
                        error.addSuppressed(diagnostic)
                    }
                    throw installerServiceFailure(error)
                }
            }
            return useConnectedService()
        } catch (error: Exception) {
            rethrowInstallerCancellation(error)
            if (error is AuthorizationFailure || error is RootServiceBootstrap.Failure || error is ServiceFailure) throw error
            if (error is java.util.concurrent.ExecutionException || error is java.util.concurrent.TimeoutException ||
                error is android.os.RemoteException || error.message.orEmpty().startsWith("INSTALLER_SERVICE_")) {
                throw ServiceFailure(phase, error)
            }
            throw error
        } finally {
            handler.post { RootService.unbind(connection) }
        }
    }

    internal fun decode(result: Bundle): InstallerSnapshot {
        check(result.getInt("serviceUid", -1) == 0 && result.getInt("servicePid", -1) > 0 &&
            result.getInt("servicePid") != Process.myPid() && result.getInt("callerUid", -1) == Process.myUid()) {
            "INSTALLER_SERVICE_IDENTITY_INVALID"
        }
        return decodeSnapshot(result)
    }

    internal fun decodeSnapshot(result: Bundle): InstallerSnapshot {
        fun text(key: String) = requireNotNull(result.getString(key)) { "INSTALLER_SERVICE_RESPONSE_INVALID" }
        val slot = InstallerSnapshotParser.slot(text("slotSuffix"))
        val names = requireNotNull(result.getStringArrayList("partitions")) { "INSTALLER_SERVICE_RESPONSE_INVALID" }
        check(names.size == names.toSet().size && names.all { it in InstallerSnapshotParser.partitionNames } &&
            names.containsAll(listOf("boot", "vbmeta"))) { "INSTALLER_SERVICE_RESPONSE_INVALID" }
        val partitions = names.associateWith { name ->
            val row = requireNotNull(result.getBundle(name)) { "INSTALLER_SERVICE_RESPONSE_INVALID" }
            val identity = BootPartitionIdentity(row.getLong("deviceNumber", -1), row.getLong("inode", -1),
                row.getLong("filesystem", -1))
            InstallerSnapshotParser.partition(name, slot, requireNotNull(row.getString("resolvedPath")),
                row.getLong("size").toString(),
                "${requireNotNull(row.getString("sha256"))}  ${InstallerSnapshotParser.partitionPath(name, slot)}", identity)
        }
        // SELinux is decoded as evidence only (2026-11-01): a Permissive/Unknown value never
        // rejects the service response and never blocks a write.
        check(text("kernelRelease").isNotBlank() && text("firmware").isNotBlank()) {
            "INSTALLER_DEVICE_CHANGED"
        }
        return InstallerSnapshot(text("kernelRelease"), text("firmware"), slot, text("selinux"), partitions)
    }

    // ------------------------------------------------------------------ mode switch (02 §5.2 / W07b)
    //
    // Thin client calls over the appended AIDL methods. The state machine lives in the service
    // (W08); until it lands every mutating call fails with SWITCH_SERVICE_NOT_READY and never
    // touches a partition. switchId parsing/validation is done here as well as in the service.

    fun requireSwitchId(switchId: String, allowEmpty: Boolean = false) {
        check(allowEmpty && switchId.isEmpty() ||
            java.util.UUID.fromString(switchId).toString() == switchId) { "SWITCH_PLAN_INVALID" }
    }

    fun preflightModeSwitch(context: Context, switchId: String, plan: Bundle): Bundle {
        requireSwitchId(switchId)
        return withService(context, persistent = true) { it.preflightModeSwitch(switchId, plan) }
    }

    /** S1: copies are passed as read-only descriptors; the service re-verifies them. */
    fun beginModeSwitch(context: Context, switchId: String, plan: Bundle,
                        superuserJson: File, settingsMirrorJson: File): Bundle {
        requireSwitchId(switchId)
        check(superuserJson.isFile && settingsMirrorJson.isFile) { "SWITCH_PRE_IMAGE_INVALID" }
        return withService(context, persistent = true) { service ->
            ParcelFileDescriptor.open(superuserJson, ParcelFileDescriptor.MODE_READ_ONLY).use { superuser ->
                ParcelFileDescriptor.open(settingsMirrorJson, ParcelFileDescriptor.MODE_READ_ONLY).use { settings ->
                    service.beginModeSwitch(switchId, plan, superuser, settings)
                }
            }
        }
    }

    fun snapshotModeSwitch(context: Context, switchId: String): Bundle {
        requireSwitchId(switchId)
        return withService(context, persistent = true) { it.snapshotModeSwitch(switchId) }
    }

    /** Read-only base/pre/candidate copy; [meta] receives the snapshot bundle. */
    fun openSwitchImage(context: Context, switchId: String, kind: String,
                        partition: String): Pair<Bundle, ParcelFileDescriptor> {
        requireSwitchId(switchId)
        val meta = Bundle()
        val image = withService(context, persistent = true) {
            it.openSwitchImage(switchId, kind, partition, meta)
        }
        return meta to image
    }

    fun submitSwitchCandidate(context: Context, switchId: String, partition: String,
                              candidate: File, candidateSha256: String): Bundle {
        requireSwitchId(switchId)
        check(candidate.isFile) { "PRODUCE_OUTPUT_SIZE_INVALID" }
        check(candidateSha256.matches(Regex("[0-9a-f]{64}"))) { "PRODUCE_CANDIDATE_CHANGED" }
        return withService(context, persistent = true) { service ->
            ParcelFileDescriptor.open(candidate, ParcelFileDescriptor.MODE_READ_ONLY).use { image ->
                service.submitSwitchCandidate(switchId, partition, image, candidateSha256)
            }
        }
    }

    fun commitModeSwitch(context: Context, switchId: String): Bundle {
        requireSwitchId(switchId)
        return withService(context, persistent = true) { it.commitModeSwitch(switchId) }
    }

    fun rollbackModeSwitch(context: Context, switchId: String): Bundle {
        requireSwitchId(switchId)
        return withService(context, persistent = true) { it.rollbackModeSwitch(switchId) }
    }

    fun abortModeSwitch(context: Context, switchId: String): Bundle {
        requireSwitchId(switchId)
        return withService(context, persistent = true) { it.abortModeSwitch(switchId) }
    }

    fun getModeSwitch(context: Context, switchId: String = ""): Bundle {
        requireSwitchId(switchId, allowEmpty = true)
        return withService(context, persistent = true) { it.getModeSwitch(switchId) }
    }

    fun verifyModeSwitch(context: Context, switchId: String): Bundle {
        requireSwitchId(switchId)
        return withService(context, persistent = true) { it.verifyModeSwitch(switchId) }
    }

    fun rebootAfterModeSwitch(context: Context, switchId: String): Boolean {
        requireSwitchId(switchId)
        return withService(context, persistent = true) { it.rebootAfterModeSwitch(switchId) }
    }

    /** Read-only export of journal.jsonl + recovery-<id>.txt; caller writes it to a user path. */
    fun openSwitchLog(context: Context, switchId: String): ParcelFileDescriptor {
        requireSwitchId(switchId)
        return withService(context, persistent = true) { it.openSwitchLog(switchId) }
    }

    fun exportSwitchLog(context: Context, switchId: String, destination: File): File {
        destination.parentFile?.mkdirs()
        ParcelFileDescriptor.AutoCloseInputStream(openSwitchLog(context, switchId)).use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return destination
    }
}
