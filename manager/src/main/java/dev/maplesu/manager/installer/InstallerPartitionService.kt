package dev.maplesu.manager.installer

import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import com.topjohnwu.superuser.ipc.RootService
import dev.maplesu.manager.ksu.KsuFileCommandRunner
import dev.maplesu.manager.ksu.ensureNormalBootUserland
import dev.maplesu.manager.runtime.ManagerCallerVerifier
import dev.maplesu.manager.workmode.service.DeviceSwitchError
import dev.maplesu.manager.workmode.service.ModeSwitchAidlCodec
import dev.maplesu.manager.workmode.service.ModeSwitchDeviceDelegate
import dev.maplesu.manager.workmode.service.ServiceModeSwitchWiring
import dev.maplesu.manager.workmode.service.ModeSwitchRootBridge
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Uses the existing provider for installation, independently of the Manager runtime backend. */
class InstallerPartitionService : RootService() {
    private lateinit var callerVerifier: ManagerCallerVerifier
    private val observing = AtomicBoolean()
    private val authorizationRestore = dev.maplesu.manager.runtime.ultra.UltraBootAuthorizationRestore()

    /**
     * In-process mutual exclusion for mode-switch operations. Partition writes additionally take
     * install.lock inside [InstallerDeviceLock] (02-switch-contract §5.5 lock order).
     */
    private val deviceOperation = AtomicBoolean()

    /**
     * W08 服务端增量状态机（首次模式切换调用时装配，root 进程内常驻）。
     *
     * 传 `this`（本服务本身就是 `Context`）：随包 stock 资产源只从真实 `AssetManager` 解析，
     * 装配期就失败而不是在切换事务里静默降级（见 `ServiceModeSwitchWiring.create` 的 KDoc）。
     */
    private val switchDelegate: ModeSwitchDeviceDelegate by lazy {
        ServiceModeSwitchWiring.create(this)
    }
    private val modeRootBridgeState = lazy {
        ModeSwitchRootBridge(applicationInfo.uid, applicationInfo.nativeLibraryDir)
    }

    override fun onCreate() {
        super.onCreate()
        check(Process.myUid() == 0) { "INSTALLER_ROOT_REQUIRED" }
        callerVerifier = ManagerCallerVerifier(this)
    }

    override fun onDestroy() {
        if (modeRootBridgeState.isInitialized()) modeRootBridgeState.value.close()
        super.onDestroy()
    }

    private fun receipts(userId: Int) = File("/data/user/$userId/$packageName/files/ultra-flash")
    private fun prepareNormalUserland() {
        val result = ensureNormalBootUserland(File(applicationInfo.nativeLibraryDir), KsuFileCommandRunner(),
            onStdout = { android.util.Log.i("NormalBoot", it) },
            onStderr = { android.util.Log.w("NormalBoot", it) })
        if (!result.ok) android.util.Log.w("NormalBoot", "${result.failureCode}: ${result.detail}")
        if (!result.ok) throw dev.maplesu.manager.ksu.KsuBootstrapFailure(result)
    }
    private fun retainedOriginals(userId: Int) = listOf(
        File("/data/user/$userId/$packageName/files/images/imported.img"),
        File(receipts(userId), "boot-current.img"),
    )

    override fun onBind(intent: Intent): IBinder = object : IInstallerService.Stub() {
        override fun observeBootChain(): Bundle {
            val caller = Binder.getCallingUid()
            return callerVerifier.withCallingManager {
                check(observing.compareAndSet(false, true)) { "INSTALLER_OBSERVATION_BUSY" }
                try {
                    InstallerPartitionReader.observe().apply { putInt("callerUid", caller) }
                } finally {
                    observing.set(false)
                }
            }
        }

        override fun openBootImage(snapshot: Bundle): ParcelFileDescriptor = openPartitionImage("boot", snapshot)

        override fun openPartitionImage(partition: String, snapshot: Bundle): ParcelFileDescriptor {
            snapshot.clear()
            val caller = Binder.getCallingUid()
            return callerVerifier.withCallingManager {
                check(observing.compareAndSet(false, true)) { "INSTALLER_OBSERVATION_BUSY" }
                try {
                    val (observed, image) = InstallerPartitionReader.openPartitionImage(cacheDir, partition)
                    try {
                        observed.putInt("callerUid", caller)
                        snapshot.putAll(observed)
                        image
                    } catch (error: Throwable) {
                        snapshot.clear()
                        image.close()
                        throw error
                    }
                } finally {
                    observing.set(false)
                }
            }
        }

        override fun prepareBootInstall(candidate: ParcelFileDescriptor, existingRecovery: ParcelFileDescriptor,
                                        expectedSnapshot: Bundle, candidateSha256: String, taskId: String): Bundle = candidate.use { image ->
            existingRecovery.use { recovery -> writeOperation {
                InstallerBootTransaction.prepare(image, recovery, expectedSnapshot, candidateSha256, taskId)
            } }
        }

        override fun commitBootInstall(taskId: String): Bundle = writeOperation { InstallerBootTransaction.commit(taskId) }
        // Aborting a transaction is not a partition write and must stay available.
        override fun cancelBootInstall(taskId: String): Bundle = operation {
            if (NativeInstallTransaction.owns(taskId)) nativeOperation(taskId) { NativeInstallTransaction.cancel(taskId) }
            else InstallerBootTransaction.cancel(taskId)
        }
        override fun getBootInstall(taskId: String): Bundle = callerVerifier.withCallingManager {
            var active = observing.get()
            var reconciliationError: String? = null
            if (!active) {
                try {
                    nativeOperation(taskId) { NativeInstallTransaction.reconcileForStatus() }
                } catch (error: Exception) {
                    if (error.message == "INSTALLER_TRANSACTION_BUSY") {
                        active = true
                    } else {
                        reconciliationError = "INSTALLER_RECONCILIATION_FAILED"
                        android.util.Log.w("MapleSUInstaller", "status reconciliation failed", error)
                    }
                }
            }
            (if (NativeInstallTransaction.owns(taskId)) NativeInstallTransaction.status(taskId)
            else InstallerBootTransaction.status(taskId)).apply {
                active = active || observing.get()
                reconciliationError?.let { putString("reconciliationError", it) }
                putBoolean("operationActive", active)
                putBoolean("canCancel", !active && (getString("state") in setOf("PREPARED", "PREPARING") ||
                    getString("kind") == "NATIVE" && getString("state") == "FAILED_BEFORE_WRITE"))
                if (!active && getString("state") == "PREPARING") {
                    putString("state", "FAILED_BEFORE_WRITE")
                    putString("error", "INSTALLER_PREPARATION_INTERRUPTED")
                }
                if (!active && getString("state") in setOf("WRITING", "RESTORING")) {
                    putString("state", "RECOVERY_REQUIRED")
                    putString("error", "INSTALLER_WRITE_INTERRUPTED")
                }
            }
        }
        override fun verifyBootInstall(taskId: String): Bundle = operation {
            if (NativeInstallTransaction.owns(taskId)) nativeOperation(taskId) {
                NativeInstallTransaction.verify(taskId, File(applicationInfo.nativeLibraryDir, "libksud_ksu.so"))
            }
            else InstallerBootTransaction.verifyBoot(taskId)
        }
        override fun restoreBootInstall(taskId: String): Bundle = writeOperation {
            if (NativeInstallTransaction.owns(taskId)) nativeOperation(taskId) { NativeInstallTransaction.restore(taskId) }
            else InstallerBootTransaction.restore(taskId)
        }
        override fun rebootAfterBootInstall(taskId: String): Boolean = callerVerifier.withCallingManager {
            InstallerModeGate.refusal()?.let { throw IllegalStateException(it) }
            check(observing.compareAndSet(false, true)) { "INSTALLER_OPERATION_BUSY" }
            try {
                if (NativeInstallTransaction.owns(taskId)) nativeOperation(taskId) { NativeInstallTransaction.reboot(taskId) }
                else InstallerBootTransaction.reboot(taskId)
            } finally { observing.set(false) }
        }

        override fun inspectStockInstall(selectedManager: String): Bundle {
            val userId = Binder.getCallingUid() / 100_000
            return operation { NativeInstallTransaction.inspect(selectedManager, userId,
                dev.maplesu.manager.ultrakernel.UltraKernelContextAssets(this@InstallerPartitionService),
                receipts(userId), retainedOriginals(userId)) }
        }

        override fun prepareNativeInstall(taskId: String, targetMode: String, partition: String,
                                          restoreOriginal: Boolean, selectedManager: String,
                                          conflictAccepted: Boolean): Bundle {
            val userId = Binder.getCallingUid() / 100_000
            return writeOperation {
                NativeInstallTransaction.prepare(taskId, targetMode, partition, restoreOriginal, selectedManager,
                    conflictAccepted, userId, assets =
                        dev.maplesu.manager.ultrakernel.UltraKernelContextAssets(this@InstallerPartitionService),
                    receipts = receipts(userId), retainedOriginals = retainedOriginals(userId))
            }
        }

        override fun openNativeInstallImage(taskId: String, partition: String, meta: Bundle): ParcelFileDescriptor {
            meta.clear()
            return callerVerifier.withCallingManager {
                check(observing.compareAndSet(false, true)) { "INSTALLER_OPERATION_BUSY" }
                try { nativeOperation(taskId) { NativeInstallTransaction.openImage(taskId, partition, meta, cacheDir) } }
                finally { observing.set(false) }
            }
        }

        override fun commitNativeInstall(taskId: String, candidate: ParcelFileDescriptor,
                                         candidateSha256: String): Bundle = candidate.use { image ->
            writeOperation { NativeInstallTransaction.commit(taskId, image, candidateSha256,
                ensureNormalUserland = ::prepareNormalUserland) }
        }

        override fun cancelNativeInstall(taskId: String): Bundle = operation {
            nativeOperation(taskId) { NativeInstallTransaction.cancel(taskId) }
        }

        override fun prepareNativeSignedInstall(taskId: String, targetMode: String, partition: String,
            restoreOriginal: Boolean, selectedManager: String, conflictAccepted: Boolean, signingMode: String): Bundle {
            val userId = Binder.getCallingUid() / 100_000
            return writeOperation { nativeOperation(taskId) {
                require(signingMode in setOf("CHAINED", "NON_CHAINED", "AOSP")) { "INSTALLER_SIGNING_MODE_INVALID" }
                NativeInstallTransaction.prepare(taskId, targetMode, partition, restoreOriginal, selectedManager,
                    conflictAccepted, userId, assets =
                        dev.maplesu.manager.ultrakernel.UltraKernelContextAssets(this@InstallerPartitionService), signingMode = signingMode,
                    receipts = receipts(userId), retainedOriginals = retainedOriginals(userId))
            } }
        }

        override fun commitNativeSignedInstall(taskId: String, candidate: ParcelFileDescriptor,
            candidateSha256: String, companionImages: Bundle): Bundle = candidate.use { image ->
            val descriptors = companionImages.keySet().filter { !it.endsWith(".sha256") }.mapNotNull {
                @Suppress("DEPRECATION")
                companionImages.getParcelable<ParcelFileDescriptor>(it)
            }
            try { writeOperation { nativeOperation(taskId) {
                NativeInstallTransaction.commit(taskId, image, candidateSha256, companionImages,
                    ensureNormalUserland = ::prepareNormalUserland)
            } } }
            finally { descriptors.forEach { it.close() } }
        }

        override fun restoreStockImages(taskId: String, targetMode: String): Bundle {
            val userId = Binder.getCallingUid() / 100_000
            return writeOperation { NativeInstallTransaction.restoreStockImages(taskId, targetMode, userId,
                dev.maplesu.manager.ultrakernel.UltraKernelContextAssets(this@InstallerPartitionService),
                receipts(userId), retainedOriginals(userId)) }
        }

        // ------------------------------------------------------------------ mode switch (W08 wiring)
        //
        // 12 个方法全部委托给 W08 `ModeSwitchDeviceDelegate`（服务端增量状态机）。边界顺序不变：
        // callerVerifier → InstallerModeGate(MODE_SWITCH) → deviceOperation → delegate →
        // InstallerGateway/installer-core 事务。门禁已收窄为“只拒越狱来源”，不再读 kernel proof。
        // 任何失败都转成带契约错误码的 Bundle；
        // 只有 PFD 返回型方法（openSwitchImage/openSwitchLog）用 IllegalStateException 表达。

        override fun preflightModeSwitch(switchId: String, plan: Bundle): Bundle = modeSwitchRead {
            switchBundle(switchId) {
                requireSwitchId(switchId)
                val fields = ModeSwitchAidlCodec.decodePlanFields(plan)
                ModeSwitchAidlCodec.preflightBundle(switchDelegate.preflight(switchId, fields), switchId)
            }
        }

        override fun beginModeSwitch(switchId: String, plan: Bundle,
                                     superuserJson: ParcelFileDescriptor,
                                     settingsMirrorJson: ParcelFileDescriptor): Bundle =
            superuserJson.use { settingsMirrorJson.use { _ ->
                modeSwitchWrite(switchId) {
                    val workspace = File(cacheDir, "mode-switch").apply { mkdirs() }
                    val superuserFile = File(workspace, "$switchId-superuser.json")
                    val settingsFile = File(workspace, "$switchId-settings.json")
                    try {
                        copyPfd(superuserJson, superuserFile)
                        copyPfd(settingsMirrorJson, settingsFile)
                        val fields = ModeSwitchAidlCodec.decodePlanFields(plan)
                        ModeSwitchAidlCodec.resultBundle(
                            switchDelegate.begin(switchId, fields, superuserFile, settingsFile), switchId,
                        )
                    } finally {
                        superuserFile.delete()
                        settingsFile.delete()
                    }
                }
            } }

        override fun snapshotModeSwitch(switchId: String): Bundle = modeSwitchWrite(switchId) {
            switchBundle(switchId) { ModeSwitchAidlCodec.resultBundle(switchDelegate.snapshot(switchId), switchId) }
        }

        override fun openSwitchImage(switchId: String, kind: String, partition: String,
                                     meta: Bundle): ParcelFileDescriptor {
            meta.clear()
            return modeSwitchRead {
                try {
                    requireSwitchId(switchId)
                    val file = switchDelegate.imageFile(switchId, kind, partition)
                    val sha = dev.maplesu.manager.workmode.switch.ModeSwitchHashes.sha256(file)
                    meta.putAll(
                        ModeSwitchAidlCodec.imageMeta(
                            dev.maplesu.manager.workmode.switch.ExportedImage(file, file.length(), sha),
                        ),
                    )
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } catch (error: DeviceSwitchError) {
                    ModeSwitchAidlCodec.throwForPfd(error)
                } catch (error: Exception) {
                    throw IllegalStateException("SWITCH_SERVICE_FAILED:${error.message}")
                }
            }
        }

        override fun submitSwitchCandidate(switchId: String, partition: String,
                                           candidate: ParcelFileDescriptor,
                                           candidateSha256: String): Bundle =
            candidate.use { _ ->
                modeSwitchWrite(switchId) {
                    val workspace = File(cacheDir, "mode-switch").apply { mkdirs() }
                    val candidateFile = File(workspace, "$switchId-$partition-candidate.img")
                    try {
                        copyPfd(candidate, candidateFile)
                        switchBundle(switchId) {
                            ModeSwitchAidlCodec.resultBundle(
                                switchDelegate.submitCandidate(switchId, partition, candidateFile, candidateSha256),
                                switchId,
                            )
                        }
                    } finally {
                        candidateFile.delete()
                    }
                }
            }

        override fun commitModeSwitch(switchId: String): Bundle = modeSwitchWrite(switchId) {
            switchBundle(switchId) { ModeSwitchAidlCodec.resultBundle(switchDelegate.commit(switchId), switchId) }
        }

        override fun rollbackModeSwitch(switchId: String): Bundle = modeSwitchWrite(switchId) {
            switchBundle(switchId) { ModeSwitchAidlCodec.resultBundle(switchDelegate.rollback(switchId), switchId) }
        }

        override fun abortModeSwitch(switchId: String): Bundle = modeSwitchWrite(switchId) {
            switchBundle(switchId) { ModeSwitchAidlCodec.resultBundle(switchDelegate.abort(switchId), switchId) }
        }

        override fun getModeSwitch(switchId: String): Bundle = modeSwitchRead {
            switchBundle(switchId) {
                if (switchId.isNotEmpty()) requireSwitchId(switchId)
                val status = switchDelegate.status(switchId)
                ModeSwitchAidlCodec.statusBundle(status).apply {
                    putString("switchId", status?.switchId ?: switchId)
                    putBoolean("operationActive", deviceOperation.get())
                }
            }
        }

        override fun verifyModeSwitch(switchId: String): Bundle = modeSwitchWrite(switchId) {
            switchBundle(switchId) { ModeSwitchAidlCodec.resultBundle(switchDelegate.verify(switchId), switchId) }
        }

        /**
         * 只落 pending 标记并返回；真正重启由 UI 层决定（02 §5.4 S5 的调用者职责）。
         * 未授权调用者/未就绪状态一律 false（fail-closed），绝不由服务自行 reboot。
         */
        override fun rebootAfterModeSwitch(switchId: String): Boolean = try {
            callerVerifier.withCallingManager {
                if (InstallerModeGate.writeRefusal(InstallerModeGate.WriteScope.MODE_SWITCH) != null) {
                    return@withCallingManager false
                }
                if (!deviceOperation.compareAndSet(false, true)) return@withCallingManager false
                try {
                    requireSwitchId(switchId)
                    switchDelegate.markRebootRequested(switchId)
                } finally {
                    deviceOperation.set(false)
                }
            }
        } catch (_: Exception) {
            false
        }

        override fun openSwitchLog(switchId: String): ParcelFileDescriptor = modeSwitchRead {
            try {
                requireSwitchId(switchId)
                val file = File(File(cacheDir, "mode-switch").apply { mkdirs() }, "recovery-$switchId.txt")
                if (NativeInstallTransaction.owns(switchId)) nativeOperation(switchId) {
                    NativeInstallTransaction.recoveryLog(switchId, file)
                } else switchDelegate.recoveryLog(switchId, file)
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (error: DeviceSwitchError) {
                ModeSwitchAidlCodec.throwForPfd(error)
            } catch (error: Exception) {
                throw IllegalStateException("SWITCH_SERVICE_FAILED:${error.message}")
            }
        }

        override fun removeKsuModulesForUltra(): Bundle {
            val managerUid = Binder.getCallingUid()
            return operation {
                InstallerDeviceLock.withDeviceLock {
                    InstallerPending.assertNoPendingSwitch(File(dev.maplesu.manager.workmode.storage.MoveSet.CONTROL_ROOT))
                    InstallerPending.assertNoPendingInstall(InstallerDeviceLock.defaultRoot)
                    UltraModuleCleanup.remove(this@InstallerPartitionService, managerUid)
                }
            }
        }

        override fun readUltraIdentity(withBuild: Boolean): Bundle {
            val caller = Binder.getCallingUid()
            return callerVerifier.withCallingManager {
                dev.maplesu.manager.runtime.ultra.UltraIdentityRecovery.readForRoot(caller, withBuild).apply {
                    putInt("serviceUid", Process.myUid())
                    putInt("servicePid", Process.myPid())
                    putInt("callerUid", caller)
                }
            }
        }

        override fun refreshUltraAuthorization(rootKey: String): String = callerVerifier.withCallingManager {
            check(dev.maplesu.manager.runtime.ultra.UltraRecoveryKey.isValidKeyFormat(rootKey))
            val fingerprint = java.security.MessageDigest.getInstance("SHA-256")
                .digest(rootKey.toByteArray(Charsets.US_ASCII)).joinToString("") { "%02x".format(it.toInt() and 255) }
            authorizationRestore.ensure(dev.maplesu.manager.runtime.ultra.UltraEnvironmentAutoSetup.currentBootId(), fingerprint) {
                if (File(dev.maplesu.manager.runtime.ultra.UltraIdentityBroker.DISABLED).exists()) return@ensure "SUSPENDED"
                val receipt = dev.maplesu.manager.runtime.ultra.UltraRuntimeClient { rootKey }.refreshSuAuthList()
                if (receipt.succeeded) "OK" else receipt.sdkError.takeUnless { it == "OK" } ?: "INVALID_RECEIPT"
            }
        }

        override fun openModeSwitchRoot(tokenFile: ParcelFileDescriptor, timeoutMillis: Int,
                                        identity: Bundle): ParcelFileDescriptor {
            val callerUid = Binder.getCallingUid()
            return tokenFile.use { token -> callerVerifier.withCallingManager {
                modeRootBridgeState.value.open(callerUid, token, timeoutMillis, identity)
            } }
        }
    }

    private fun <T> nativeOperation(taskId: String, action: () -> T): T =
        if (modeRootBridgeState.isInitialized()) modeRootBridgeState.value.withInstallLock(taskId, action) else action()

    /**
     * Authenticate the current Manager and actual root service before entering the transaction.
     * Ancestor command names do not establish or revoke the caller's installation authority.
     */
    private fun writeOperation(action: () -> Bundle): Bundle = callerVerifier.withCallingManager {
        InstallerModeGate.refusal()?.let { throw IllegalStateException(it) }
        check(observing.compareAndSet(false, true)) { "INSTALLER_OPERATION_BUSY" }
        try { action() } finally { observing.set(false) }
    }

    private fun operation(action: () -> Bundle): Bundle = callerVerifier.withCallingManager {
        check(observing.compareAndSet(false, true)) { "INSTALLER_OPERATION_BUSY" }
        try { action() } finally { observing.set(false) }
    }

    /** Read-only mode-switch entry: caller auth first, then in-process exclusion. */
    private fun <T> modeSwitchRead(action: () -> T): T = callerVerifier.withCallingManager {
        check(deviceOperation.compareAndSet(false, true)) { "INSTALLER_OPERATION_BUSY" }
        try { action() } finally { deviceOperation.set(false) }
    }

    /**
     * Mode-switch write entry. Order stays fixed by 02-switch-contract §5.4: gate first (never
     * write an FD before the root check), then in-process exclusion; install.lock is taken by the
     * transaction. The same Manager/root identity applies to every available transport.
     */
    private fun modeSwitchWrite(switchId: String, action: () -> Bundle): Bundle =
        callerVerifier.withCallingManager {
            InstallerModeGate.writeRefusal(InstallerModeGate.WriteScope.MODE_SWITCH)
                ?.let { return@withCallingManager ModeSwitchAidlCodec.errorBundle(it, "", switchId) }
            if (!deviceOperation.compareAndSet(false, true)) {
                return@withCallingManager ModeSwitchAidlCodec.errorBundle(
                    "INSTALLER_OPERATION_BUSY", "", switchId,
                )
            }
            try { action() } finally { deviceOperation.set(false) }
        }

    /** Bundle 型入口统一兜底：异常 → 契约错误码 Bundle（不穿出 Binder）。 */
    private fun switchBundle(switchId: String, action: () -> Bundle): Bundle = try {
        action()
    } catch (error: DeviceSwitchError) {
        ModeSwitchAidlCodec.errorBundle(error.code, error.detail, switchId)
    } catch (error: Exception) {
        ModeSwitchAidlCodec.errorBundle("SWITCH_SERVICE_FAILED", error.message.orEmpty(), switchId)
    }

    private fun copyPfd(source: ParcelFileDescriptor, destination: File) {
        destination.parentFile?.mkdirs()
        ParcelFileDescriptor.AutoCloseInputStream(
            ParcelFileDescriptor.dup(source.fileDescriptor),
        ).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }

    private fun requireSwitchId(switchId: String) {
        check(UUID.fromString(switchId).toString() == switchId) { "SWITCH_PLAN_INVALID" }
    }
}
