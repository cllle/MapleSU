package com.maplesu.core.ui.screen.flash

import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maplesu.manager.installer.InstallerFlashViewModel
import dev.maplesu.manager.installer.InstallerServiceClient
import dev.maplesu.manager.installer.InstallCandidateImage
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.maplesu.core.Natives
import com.maplesu.core.R
import com.maplesu.core.data.repository.isSoftRebootPreferred
import com.maplesu.core.ui.LocalUiMode
import com.maplesu.core.ui.UiMode
import com.maplesu.core.ui.component.dialog.ConfirmResult
import com.maplesu.core.ui.component.dialog.rememberConfirmDialog
import com.maplesu.core.ui.navigation3.LocalNavigator
import com.maplesu.core.ui.util.FlashOutcome
import com.maplesu.core.ui.util.InstallStage
import com.maplesu.core.ui.util.UltraKernelBootExport
import com.maplesu.core.ui.util.reboot

@Composable
fun FlashScreen(flashIt: FlashIt, onBackEnabledChange: (Boolean) -> Unit) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    val logContent = remember { StringBuilder(text) }
    val restoringInstallation = flashIt == FlashIt.FlashRestore || flashIt == FlashIt.FlashUninstall
    val patchOnly = flashIt is FlashIt.FlashBoot && flashIt.boot != null
    val directInstall = flashIt is FlashIt.FlashBoot && flashIt.boot == null
    val transactional = (flashIt is FlashIt.FlashBoot && flashIt.boot == null) ||
        flashIt is FlashIt.DownloadBoot || restoringInstallation
    val managedFlash = transactional || flashIt is FlashIt.FlashBoot || flashIt is FlashIt.DownloadBoot
    // 直接安装显示实时日志；选文件只显示修补结果和公共 Download 路径。
    val showLog = !patchOnly
    val installationTaskId = rememberSaveable { java.util.UUID.randomUUID().toString() }
    var installationStarted by rememberSaveable { mutableStateOf(false) }
    var restoringPreInstall by rememberSaveable { mutableStateOf(false) }
    var showRebootAction by rememberSaveable { mutableStateOf(false) }
    var rebooting by remember { mutableStateOf(false) }
    var flashingStatus by rememberSaveable { mutableStateOf(FlashingStatus.FLASHING) }
    // S5/S6：阶段与结果态（结构化）。结果态决定"真的写进分区"还是"只出候选"。
    var flashStage by remember { mutableStateOf<InstallStage?>(null) }
    var flashOutcome by remember { mutableStateOf<FlashOutcome?>(null) }
    var flashMessage by remember { mutableStateOf<String?>(null) }
    var flashErrorCode by remember { mutableStateOf<String?>(null) }
    var canSaveBoot by remember { mutableStateOf(false) }
    var bootCandidate by remember { mutableStateOf<InstallCandidateImage?>(null) }
    var canRestore by remember { mutableStateOf(false) }
    var canQuery by remember { mutableStateOf(false) }
    var querying by remember { mutableStateOf(false) }
    var canReturnToPreparation by remember { mutableStateOf(false) }
    var recoveryMessage by remember { mutableStateOf<String?>(null) }
    var restoreAction: (() -> Unit)? = null
    var queryAction: (() -> Unit)? = null
    val needJailbreakWarning = flashIt is FlashIt.FlashBoot && !patchOnly && Natives.isLateLoadMode
    // Soft reboot keeps the jailbreak and still applies modules
    val softReboot = flashIt is FlashIt.FlashModules && isSoftRebootPreferred()
    var flashingEnabled by rememberSaveable { mutableStateOf(!needJailbreakWarning) }
    val uiMode = LocalUiMode.current
    val snackbarHost = remember { SnackbarHostState() }
    val moduleTypeDialog = rememberConfirmDialog()
    val restoreDialog = rememberConfirmDialog()
    var moduleTypeChoice by remember { mutableStateOf<ModuleTypeChoiceRequest?>(null) }
    var modulePolicyNotice by remember { mutableStateOf<ModulePolicyNoticeRequest?>(null) }
    var moduleConfirmationPending by rememberSaveable { mutableStateOf(false) }
    val backEnabled = flashingStatus != FlashingStatus.FLASHING &&
        (!transactional || canReturnToPreparation) && !querying

    moduleTypeChoice?.let { ModuleTypeChoiceDialog(it) }
    modulePolicyNotice?.let { ModulePolicyNoticeDialog(it) }

    SideEffect { onBackEnabledChange(backEnabled) }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !backEnabled,
        onBackCompleted = {},
    )

    fun showMessage(message: String) {
        scope.launch {
            if (uiMode == UiMode.Material) {
                snackbarHost.showSnackbar(message)
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (managedFlash) {
        val installation = viewModel<InstallerFlashViewModel>(key = installationTaskId)
        val progress by installation.state.collectAsState()
        LaunchedEffect(flashingEnabled) {
            if (flashingEnabled) {
                val resume = installationStarted
                installationStarted = true
                installation.attach(context.applicationContext, installationTaskId, resume,
                    restoringPreInstall, transactional) { append ->
                    flashIt(
                        flashIt, append, append, { null }, installationTaskId,
                        onStage = installation::reportStage,
                    )
                }
            }
        }
        progress.result?.imagesRequired?.let { request ->
            dev.maplesu.manager.workmode.ui.WorkModeImageDialog(request, busy = false, readError = null,
                onConfirm = { boot, initBoot -> installation.continueModeSwitch(context.applicationContext, boot, initBoot) },
                onDismiss = { navigator.pop() })
        }
        restoreAction = { installation.restore(context.applicationContext) }
        queryAction = { installation.queryOriginalTask(context.applicationContext) }
        LaunchedEffect(progress) {
            // 安装执行体逐行上报日志，界面持续跟随本次任务。
            text = progress.text + (progress.result?.err?.takeIf { it.isNotBlank() }?.let { "\n$it\n" } ?: "")
            logContent.clear()
            logContent.append(text)
            progress.stage?.let { flashStage = it }
            canRestore = !patchOnly && progress.canRestore
            canQuery = progress.canQuery
            querying = progress.querying
            canReturnToPreparation = progress.canReturnToPreparation
            recoveryMessage = progress.recoveryMessage
            if (progress.result == null) flashingStatus = FlashingStatus.FLASHING
            progress.result?.let { result ->
                bootCandidate = result.bootCandidate
                flashingStatus = if (result.imagesRequired != null) FlashingStatus.WAITING_INPUT
                    else if (result.code == 0) FlashingStatus.SUCCESS else FlashingStatus.FAILED
                showRebootAction = !patchOnly && result.showReboot
                flashOutcome = result.outcome ?: if (result.code == 0) null else FlashOutcome.FAILED
                if (result.outcome == FlashOutcome.WRITTEN) flashStage = InstallStage.AWAITING_REBOOT
                flashMessage = when {
                    result.outcome == FlashOutcome.SAVED_ONLY && result.outputPath != null ->
                        resources.getString(R.string.install_image_saved, result.outputPath)
                    result.code != 0 -> result.err.takeIf { it.isNotBlank() }
                    else -> null
                }
                flashErrorCode = if (result.code == 0) {
                    null
                } else {
                    stableCodeOf(result.err) ?: result.code.toString()
                }
            }
        }
        // 只提供本次成功安装的候选，不借用历史 Ultra 镜像或模块安装产物。
        LaunchedEffect(flashingStatus, flashOutcome, bootCandidate) {
            canSaveBoot = if (!directInstall || flashOutcome != FlashOutcome.WRITTEN ||
                flashingStatus != FlashingStatus.SUCCESS) {
                false
            } else {
                withContext(Dispatchers.IO) {
                    runCatching { UltraKernelBootExport.hasVerifiedCandidate(progress.result?.bootCandidate) }
                        .getOrDefault(false)
                }
            }
        }
        // Reconnection observes this task. An inactive preparation is discarded before retry.
    } else FlashEffect(
        flashIt = flashIt,
        text = text,
        logContent = logContent,
        onTextUpdate = { text = it },
        onShowRebootChange = { showRebootAction = it },
        onFlashingStatusChange = { flashingStatus = it },
        enabled = flashingEnabled,
        resumeModuleConfirmation = moduleConfirmationPending,
        confirmModuleType = { confirmation ->
            withContext(Dispatchers.Main) {
                moduleConfirmationPending = true
                val report = confirmation.report
                val evidence = confirmation.evidence
                val model = com.maplesu.core.ui.screen.module.ModuleTypeUi.dialogModel(
                    evidence, dev.maplesu.manager.workmode.ModeRuntime.active,
                )
                val typeLabel = when (model.type) {
                    dev.maplesu.manager.modules.ModuleType.MAPLESU ->
                        resources.getString(R.string.module_type_maplesu)
                    dev.maplesu.manager.modules.ModuleType.KERNELSU ->
                        resources.getString(R.string.module_type_ksu)
                    dev.maplesu.manager.modules.ModuleType.AMBIGUOUS ->
                        resources.getString(R.string.module_type_ambiguous)
                    dev.maplesu.manager.modules.ModuleType.UNKNOWN ->
                        resources.getString(R.string.module_type_unknown)
                }
                val name = report.properties["name"].orEmpty().filterNot(Char::isISOControl)
                    .take(120).ifBlank { report.id }
                val chosen: dev.maplesu.manager.modules.ModuleType? = when {
                    // UNKNOWN：显示真实 evidence 原因并禁用安装，不弹确认。
                    model.type == dev.maplesu.manager.modules.ModuleType.UNKNOWN -> {
                        showMessage(
                            resources.getString(
                                R.string.module_type_unknown_reason,
                                com.maplesu.core.ui.screen.module.ModuleTypeUi.unknownReason(evidence),
                            ),
                        )
                        null
                    }
                    // AMBIGUOUS：让用户显式选择安装类型（不默认猜测）。
                    model.requiresChoice -> {
                        val allowedTypes = model.choices.filter { choice ->
                            com.maplesu.core.ui.screen.module.ModuleTypeUi.installEnabled(
                                evidence, dev.maplesu.manager.workmode.ModeRuntime.active, choice,
                            )
                        }.toSet()
                        if (allowedTypes.isEmpty()) {
                            showMessage(resources.getString(R.string.module_type_unsupported_in_ksu))
                            null
                        } else {
                            val request = ModuleTypeChoiceRequest(name, allowedTypes)
                            moduleTypeChoice = request
                            try {
                                request.result.await()
                            } finally {
                                if (moduleTypeChoice === request) moduleTypeChoice = null
                            }
                        }
                    }
                    // B 模式 Ultra / 未判定：明确拒绝，不允许假成功。
                    !model.installEnabled -> {
                        showMessage(resources.getString(R.string.module_type_unsupported_in_ksu))
                        null
                    }
                    // Ultra/KSU：类型行 + 正常确认。
                    else -> {
                        val confirmed = moduleTypeDialog.awaitConfirm(
                            title = resources.getString(R.string.module_install),
                            content = resources.getString(R.string.module_type_confirmation, name, typeLabel),
                            confirm = resources.getString(R.string.module_install),
                            dismiss = resources.getString(android.R.string.cancel),
                        ) == ConfirmResult.Confirmed
                        if (confirmed) model.type else null
                    }
                }
                if (chosen != null && dev.maplesu.manager.workmode.ModeRuntime.active ==
                    dev.maplesu.manager.workmode.WorkMode.LITE &&
                    dev.maplesu.manager.ModuleArchiveInspector.Feature.SELINUX_POLICY in report.features) {
                    val warn = withContext(Dispatchers.IO) {
                        val repository = dev.maplesu.manager.modules.UltraModulePolicyRepository
                        val state = runCatching { repository.read(context.applicationContext) }
                            .getOrDefault(repository.peek())
                        state.shouldWarn(true, repository.warningSuppressed(context.applicationContext))
                    }
                    if (warn) {
                        val request = ModulePolicyNoticeRequest(name)
                        modulePolicyNotice = request
                        try {
                            if (request.result.await() == true) withContext(Dispatchers.IO) {
                                runCatching { dev.maplesu.manager.modules.UltraModulePolicyRepository
                                    .suppressWarning(context.applicationContext) }.onFailure {
                                    showMessage(resources.getString(R.string.ui_polish_setting_failed))
                                }
                            }
                        } finally {
                            if (modulePolicyNotice === request) modulePolicyNotice = null
                        }
                    }
                }
                moduleConfirmationPending = false
                if (chosen == null) navigator.pop()
                chosen
            }
        },
    )

    val state = FlashUiState(
        text = text,
        showLog = showLog,
        showRebootAction = showRebootAction,
        flashingStatus = flashingStatus,
        showJailbreakWarning = needJailbreakWarning && !flashingEnabled,
        rebootLabelRes = if (softReboot) R.string.reboot_soft else R.string.reboot,
        showCloseAction = dev.maplesu.manager.workmode.ModeRuntime.active ==
            dev.maplesu.manager.workmode.WorkMode.KSU &&
            flashingStatus == FlashingStatus.SUCCESS && !showRebootAction,
        actionInProgress = rebooting,
        stage = flashStage,
        outcome = flashOutcome,
        message = flashMessage,
        errorCode = flashErrorCode,
        canSaveBoot = canSaveBoot,
        canRestore = canRestore,
        directInstall = directInstall,
        generatedOnDevice = (flashIt as? FlashIt.FlashBoot)?.generatedOnDevice == true,
        canQuery = canQuery,
        querying = querying,
        canReturnToPreparation = canReturnToPreparation,
        recoveryMessage = recoveryMessage,
    )
    val actions = FlashScreenActions(
        onBack = dropUnlessResumed { if (backEnabled) navigator.pop() },
        // 🟠（v2.19）失败文案在组合期取好（saveLog 不是 @Composable，只能传字符串进去）
        onSaveLog = saveLog(logContent, scope, stringResource(R.string.log_save_failed)) { showMessage(it) },
        onSaveBoot = {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { UltraKernelBootExport.save(context.applicationContext, bootCandidate) }
                }
                result.onSuccess { path ->
                    showMessage(resources.getString(R.string.install_boot_save_done, path))
                }.onFailure { error ->
                    val code = error.message.orEmpty().ifBlank { "ULTRA_KERNEL_EXPORT_FAILED" }
                    showMessage(resources.getString(R.string.install_boot_save_failed, code))
                }
            }
        },
        onReboot = {
            if (!rebooting) {
                rebooting = true
                scope.launch {
                    try {
                        // A written image keeps its transaction's reboot marker and validation.
                        val viaTransaction = flashOutcome != FlashOutcome.ENVIRONMENT_UPDATED &&
                            (transactional || flashOutcome == FlashOutcome.WRITTEN)
                        val accepted = withContext(Dispatchers.IO) {
                            runCatching {
                                if (viaTransaction) InstallerServiceClient.rebootAfterInstallation(context, installationTaskId)
                                else reboot(if (softReboot) "soft_reboot" else "")
                            }.getOrDefault(false)
                        }
                        if (!accepted) {
                            showMessage(resources.getString(if (viaTransaction) R.string.installer_task_reboot_failed
                                else R.string.reboot_request_failed))
                        }
                    } finally { rebooting = false }
                }
            }
        },
        onClose = dropUnlessResumed { navigator.pop() },
        onQueryOriginalTask = { if (canQuery && !querying) queryAction?.invoke() },
        onReturnToPreparation = dropUnlessResumed {
            if (backEnabled && canReturnToPreparation) navigator.pop()
        },
        onConfirmJailbreakWarning = { flashingEnabled = true },
        onDismissJailbreakWarning = dropUnlessResumed { navigator.pop() },
        onRestore = {
            scope.launch {
                val confirmed = restoreDialog.awaitConfirm(
                    title = resources.getString(R.string.flash_restore_pre_install),
                    content = resources.getString(R.string.flash_restore_pre_install_message),
                    confirm = resources.getString(R.string.flash_restore_pre_install),
                    dismiss = resources.getString(android.R.string.cancel),
                ) == ConfirmResult.Confirmed
                if (confirmed && canRestore) {
                    restoringPreInstall = true
                    restoreAction?.invoke()
                }
            }
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> FlashScreenMiuix(state, actions)
        UiMode.Material -> FlashScreenMaterial(state, actions, snackbarHost)
    }
}

/**
 * 从一句可读文案里取出稳定码（`ULTRA_KERNEL_*` / `INSTALLER_*` / `SITE_*`）。
 *
 * 失败页必须能给出"明确错误码"，但**不**把整段日志倒给用户：这里只挑第一个像稳定码的
 * 大写标识符；挑不到就由调用方退回落库的退出码。
 */
private fun stableCodeOf(message: String): String? =
    Regex("[A-Z][A-Z0-9_]{7,}").find(message)?.value
