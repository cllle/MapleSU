package dev.maplesu.manager.installer

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maplesu.core.ui.util.FlashResult
import com.maplesu.core.ui.util.InstallStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Flash progress for one durable installer task.
 *
 * Reconnects to the exact persisted task without replaying a commit. An inactive preparation
 * may be cancelled so a new attempt can rebuild its candidate.
 */
internal class InstallerFlashViewModel : ViewModel() {
    data class State(
        val text: String = "",
        val result: FlashResult? = null,
        /** S5：修补中 / 写入中 / 待重启（结构化，页面不解析日志）。 */
        val stage: InstallStage? = null,
        val canRestore: Boolean = false,
        val canQuery: Boolean = false,
        val querying: Boolean = false,
        val canReturnToPreparation: Boolean = false,
        val recoveryMessage: String? = null,
    )
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var attachedTask: String? = null
    private var restoring = false
    private var transactional = false

    /** S5：由执行体（`installBoot`）上报阶段；线程安全（只在 VM 的 IO 协程里调用）。 */
    fun reportStage(stage: InstallStage) {
        mutable.value = mutable.value.copy(stage = stage)
    }

    private fun rethrowCancellation(context: Context, error: Throwable) {
        try { rethrowInstallerCancellation(error) } catch (cancelled: Exception) {
            mutable.value = mutable.value.copy(
                result = mutable.value.result ?: FlashResult(1,
                    context.getString(com.maplesu.core.R.string.installer_patch_interrupted), false,
                    if (transactional) com.maplesu.core.ui.util.FlashOutcome.RECOVERY_REQUIRED
                    else com.maplesu.core.ui.util.FlashOutcome.FAILED),
                canRestore = false, canQuery = transactional, querying = false,
                canReturnToPreparation = !transactional,
                recoveryMessage = context.getString(if (transactional) com.maplesu.core.R.string.installer_recovery_unknown
                    else com.maplesu.core.R.string.installer_recovery_retry_hint),
            )
            if (cancelled is InterruptedException) throw kotlinx.coroutines.CancellationException("INSTALLER_CANCELLED")
                .apply { initCause(cancelled) }
            throw cancelled
        }
    }

    fun restore(context: Context) {
        val taskId = attachedTask ?: return
        if (!mutable.value.canRestore || mutable.value.result == null || mutable.value.querying) return
        restoring = true
        mutable.value = mutable.value.copy(result = null, canRestore = false, canQuery = false,
            canReturnToPreparation = false, stage = InstallStage.WRITING)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = InstallerServiceClient.restoreInstallation(context.applicationContext, taskId)
                publishResult(context, taskId, installerTaskResult(status.getString("state").orEmpty(),
                    status.getBoolean("rebootRequired"), status.getString("error").orEmpty(), restoring = true))
            } catch (error: Exception) {
                rethrowCancellation(context, error)
                publishResult(context, taskId, FlashResult(1, error.message.orEmpty(), false,
                    com.maplesu.core.ui.util.FlashOutcome.RECOVERY_REQUIRED))
            }
        }
    }

    fun queryOriginalTask(context: Context) {
        val taskId = attachedTask ?: return
        if (!transactional || !mutable.value.canQuery || mutable.value.querying) return
        mutable.value = mutable.value.copy(querying = true, canRestore = false, canReturnToPreparation = false)
        viewModelScope.launch(Dispatchers.IO) { observe(context.applicationContext, taskId) }
    }

    private suspend fun publishResult(context: Context, taskId: String, result: FlashResult) {
        if (transactional && result.code != 0 && result.installationSubmitted != false &&
            result.imagesRequired == null) {
            observe(context.applicationContext, taskId, result)
            return
        }
        val canRestore = if (transactional && result.installationSubmitted != false &&
            result.outcome in setOf(com.maplesu.core.ui.util.FlashOutcome.WRITTEN,
                com.maplesu.core.ui.util.FlashOutcome.RECOVERY_REQUIRED)) {
            runCatching { InstallerServiceClient.installationStatus(context.applicationContext, taskId)
                .let { it.getString("taskId") == taskId && !it.getBoolean("operationActive") &&
                    it.getString("reconciliationError").isNullOrEmpty() && it.getBoolean("canRestore") } }
                .getOrDefault(false)
        } else false
        val resolved = if (result.code != 0 && result.installationSubmitted == false)
            result.copy(outcome = com.maplesu.core.ui.util.FlashOutcome.FAILED, showReboot = false) else result
        mutable.value = mutable.value.copy(result = resolved, canRestore = canRestore, canQuery = false,
            querying = false, canReturnToPreparation = true,
            recoveryMessage = if (result.code != 0) context.getString(com.maplesu.core.R.string.installer_recovery_retry_hint) else null)
    }

    fun attach(context: Context, taskId: String, resume: Boolean, restore: Boolean, transactional: Boolean,
               execute: suspend ((String) -> Unit) -> FlashResult) {
        if (attachedTask == taskId) return
        check(attachedTask == null) { "INSTALLER_TASK_CONFLICT" }
        attachedTask = taskId
        restoring = restore
        this.transactional = transactional
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val environmentResult = if (resume) UltraDirectEnvironmentUpgrade.resume(context, taskId) else null
                if (environmentResult != null) {
                    reportStage(InstallStage.UPGRADING_ENVIRONMENT)
                    publishResult(context, taskId, environmentResult)
                } else if (resume && transactional) observe(context.applicationContext, taskId)
                else if (resume) {
                    publishResult(context, taskId, FlashResult(1,
                        context.getString(com.maplesu.core.R.string.installer_patch_interrupted), false))
                } else {
                    // The selected native or mode-switch flow owns its pending check and root transport.
                    val result = execute { line -> mutable.value = mutable.value.copy(text = mutable.value.text + line + "\n") }
                    publishResult(context, taskId, result)
                }
            } catch (error: Exception) {
                rethrowCancellation(context, error)
                publishResult(context, taskId, FlashResult(1, error.message.orEmpty(), false))
            }
        }
    }

    fun continueModeSwitch(context: Context, boot: android.net.Uri?, initBoot: android.net.Uri?) {
        val request = mutable.value.result?.imagesRequired ?: return
        val taskId = attachedTask ?: return
        if (request.requiresBoot && boot == null ||
            request.restoreInitBoot && initBoot == null) return
        mutable.value = mutable.value.copy(result = null, stage = InstallStage.PATCHING)
        viewModelScope.launch(Dispatchers.IO) {
            val directory = java.io.File(context.cacheDir, "switch-images-${java.util.UUID.randomUUID()}")
            try {
                check(directory.mkdirs())
                fun copy(uri: android.net.Uri?, name: String): java.io.File? = uri?.let {
                    java.io.File(directory, name).also { file ->
                        requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                            file.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var size = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    size += count
                                    check(size <= dev.maplesu.manager.ultrakernel.UltraKernelCatalog.MAX_IMAGE_BYTES)
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                    }
                }
                val images = dev.maplesu.manager.workmode.service.ModeSwitchImages(
                    copy(boot, "boot.img"), copy(initBoot, "init_boot.img"))
                val facade = dev.maplesu.manager.workmode.service.ModeSwitchRuntime.facadeOrNull()
                    ?: dev.maplesu.manager.workmode.service.ModeSwitchRuntime.initialize(context)
                val result = installerModeSwitchResult(facade.startSwitch(request.target,
                    confirmHidden = true, requestedSwitchId = taskId, images = images,
                    onProgress = { line -> mutable.value = mutable.value.copy(text = mutable.value.text + line + "\n") }))
                if (result.outcome == com.maplesu.core.ui.util.FlashOutcome.WRITTEN) {
                    InstallerServiceClient.rememberInstallTarget(context, taskId,
                        if (request.target == dev.maplesu.manager.workmode.WorkMode.KSU) InstallTargetMode.NORMAL else InstallTargetMode.ULTRA)
                    if (request.target == dev.maplesu.manager.workmode.WorkMode.LITE) {
                        runCatching { dev.maplesu.manager.runtime.ultra.UltraInstallCredentialRecovery.recover(context) }
                            .onFailure { android.util.Log.w("InstallerFlash", "Credential handoff deferred to startup", it) }
                    }
                }
                publishResult(context, taskId, result)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                rethrowCancellation(context, cancelled)
            } catch (error: Exception) {
                rethrowCancellation(context, error)
                publishResult(context, taskId, FlashResult(1, error.message.orEmpty(), false))
            } finally {
                directory.deleteRecursively()
            }
        }
    }

    private suspend fun observe(context: Context, taskId: String, failure: FlashResult? = mutable.value.result) {
        val previous = failure ?: FlashResult(1,
            context.getString(com.maplesu.core.R.string.installer_patch_interrupted), false,
            com.maplesu.core.ui.util.FlashOutcome.RECOVERY_REQUIRED)
        mutable.value = mutable.value.copy(result = previous, canRestore = false, canQuery = true,
            querying = true, canReturnToPreparation = false,
            recoveryMessage = context.getString(com.maplesu.core.R.string.installer_recovery_querying))
        try {
            repeat(6) { attempt ->
                var status = InstallerServiceClient.installationStatus(context, taskId)
                fun validate(receipt: Bundle) {
                    check(receipt.getString("taskId") == taskId) { "INSTALLER_SERVICE_RESPONSE_INVALID" }
                    check(!receipt.getString("state").isNullOrBlank()) { "INSTALLER_SERVICE_RESPONSE_INVALID" }
                    check(receipt.getString("reconciliationError").isNullOrEmpty()) {
                        "INSTALLER_RECONCILIATION_FAILED"
                    }
                }
                validate(status)
                if (!status.getBoolean("operationActive") && status.getBoolean("canCancel") &&
                    status.getString("state") in setOf("BEGIN", "SNAPSHOT", "PRODUCE", "PREPARING", "PREPARED", "FAILED_BEFORE_WRITE")) {
                    // A status query never replays commit. Only the exact inactive preparation
                    // is discarded; a lost cancellation response remains attached for re-query.
                    status = InstallerServiceClient.cancelInstallation(context, taskId)
                    validate(status)
                    check(status.getString("state") == "CANCELLED" && !status.getBoolean("operationActive")) {
                        "INSTALLER_CANCELLATION_FAILED"
                    }
                }
                val observed = status.getString("state", "NONE")
                val current = if (observed == "REBOOT_REQUESTED" && status.getString("rebootSourceState") == "RESTORED")
                    "RESTORED" else observed
                // S5：恢复观察时也要有阶段（进程被杀后重进刷写页）。
                val stage = when (current) {
                    "BEGIN", "SNAPSHOT", "PRODUCE", "PREPARING", "PREPARED" -> InstallStage.PATCHING
                    "COMMIT", "ROLLING_BACK", "WRITING", "RESTORING" -> InstallStage.WRITING
                    "REBOOT_REQUIRED", "REBOOT_REQUESTED", "BOOT_IMAGE_VERIFIED", "BOOT_VERIFIED", "VERIFIED" ->
                        InstallStage.AWAITING_REBOOT
                    else -> mutable.value.stage
                }
                mutable.value = mutable.value.copy(
                    stage = stage,
                )
                if (status.getBoolean("operationActive")) {
                    mutable.value = mutable.value.copy(
                        recoveryMessage = context.getString(com.maplesu.core.R.string.installer_recovery_active))
                    if (attempt < 5) delay(500)
                    return@repeat
                }
                val stockRestore = status.getString("operation") == "RESTORE_STOCK"
                val result = installerTaskResult(current, status.getBoolean("rebootRequired"),
                    status.getString("error").orEmpty(), restoring)
                val resolved = if (stockRestore && result.code == 0 &&
                    ImageRestorationKind.fromWire(status.getString("restorationKind")) == ImageRestorationKind.EXACT_STOCK)
                    result.copy(outcome = com.maplesu.core.ui.util.FlashOutcome.RESTORED) else result
                val retry = current in setOf("NONE", "FAILED_BEFORE_WRITE", "FAILED_PRE_WRITE", "CANCELLED",
                    "RESTORED", "ROLLED_BACK", "STOCK_RESTORED", "OWNED_WRITES_REMOVED")
                val canRestore = status.getBoolean("canRestore")
                mutable.value = mutable.value.copy(
                    result = if (current in setOf("NONE", "CANCELLED", "FAILED_BEFORE_WRITE", "FAILED_PRE_WRITE"))
                        previous.copy(outcome = com.maplesu.core.ui.util.FlashOutcome.FAILED, showReboot = false) else resolved,
                    canRestore = canRestore, canQuery = !retry && resolved.code != 0, querying = false,
                    canReturnToPreparation = retry || resolved.code == 0,
                    recoveryMessage = when {
                        retry -> context.getString(com.maplesu.core.R.string.installer_recovery_retry_hint)
                        canRestore -> context.getString(com.maplesu.core.R.string.installer_recovery_restore_hint)
                        resolved.code != 0 -> context.getString(com.maplesu.core.R.string.installer_recovery_unknown)
                        else -> null
                    },
                )
                return
            }
            mutable.value = mutable.value.copy(querying = false)
        } catch (error: Exception) {
            rethrowCancellation(context, error)
            mutable.value = mutable.value.copy(result = previous, canRestore = false, canQuery = true,
                querying = false, canReturnToPreparation = false,
                recoveryMessage = context.getString(com.maplesu.core.R.string.installer_recovery_unknown))
        }
    }
}
