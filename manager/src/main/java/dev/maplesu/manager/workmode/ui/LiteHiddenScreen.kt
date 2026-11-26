package dev.maplesu.manager.workmode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maplesu.core.R
import com.maplesu.core.ui.LocalUiMode
import com.maplesu.core.ui.UiMode
import com.maplesu.core.ui.component.material.ExpressiveScaffold
import com.maplesu.core.ui.component.material.SegmentedColumn
import com.maplesu.core.ui.component.material.TopBarBackButton
import com.maplesu.core.ui.component.material.expressiveTopAppBarColors
import com.maplesu.core.ui.screen.host.DeactivateUiState
import com.maplesu.core.ui.screen.host.HostPickerActions
import com.maplesu.core.ui.screen.host.HostPickerPage
import com.maplesu.core.ui.screen.host.HostPickerUiState
import com.maplesu.core.ui.screen.host.HostStepList
import com.maplesu.core.ui.screen.host.HiddenFlowText
import com.maplesu.core.ui.screen.host.HiddenFlowButton
import com.maplesu.core.ui.screen.host.HiddenFlowDialog
import com.maplesu.core.ui.screen.host.HiddenFlowProgress
import com.maplesu.core.ui.screen.host.HiddenOperationProgress
import com.maplesu.core.ui.screen.host.hiddenFlowColors
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.maplesu.core.ui.theme.LocalEnableBlur
import com.maplesu.core.ui.util.BlurredBar
import com.maplesu.core.ui.util.rememberBlurBackdrop
import dev.maplesu.manager.liteweb.RecoveryWebUiState
import dev.maplesu.manager.litehidden.HiddenCardPhase
import dev.maplesu.manager.litehidden.HiddenHostPolicy
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 独立隐藏设置页的 UI 状态。
 *
 * 终态：状态行 / `开启隐藏模式` / 小字 / `WebUI 端口` / `WebUI 链接`，外加
 * [hostPicker]（选择寄生应用）与 [deactivate]（解除寄生）。
 */
@Immutable
data class LiteHiddenPageUi(
    /**
     * 只有**明确读到 ON** 才为 true：整页消失/入口关闭只认这个。
     * UNKNOWN（读失败）不得冒充"已开启"，也不能把页面直接关掉让用户看不到原因。
     */
    val hidden: Boolean = false,
    /** 三态事实（状态行文案来源）：UNKNOWN 单独一句"状态未知（读取失败）"，不复用"已开启"。 */
    val status: LiteHiddenStatus = LiteHiddenStatus.UNKNOWN,
    val checkingState: Boolean = false,
    val statusRes: Int = R.string.ui_polish_hidden_reading,
    val phase: HiddenCardPhase = HiddenCardPhase.UNHIDDEN,
    val portText: String = "18427",
    val portValid: Boolean = true,
    /** 隐藏后将使用的 WebUI 链接（只读预览；不可用显示 "—"）。 */
    val webUiLink: String = "—",
    val failureRes: Int? = null,
    val webUiLinkAvailable: Boolean = false,
    val preparingLink: Boolean = false,
    val recoveryLinkSaved: Boolean = false,
    val linkFailure: Boolean = false,
    val linkPortAdjusted: Boolean = false,
    val webUiState: RecoveryWebUiState = RecoveryWebUiState.UNKNOWN,
    val checkingWebUi: Boolean = true,
    val stoppingWebUi: Boolean = false,
    val confirmStopWebUi: Boolean = false,
    val webUiStopFailed: Boolean = false,
    /** 已记录/已改写过的宿主包名（仅 UI 记忆，不是宿主真实状态）。 */
    val activeHost: String = "",
    /** 选择寄生应用页状态。 */
    val hostPicker: HostPickerUiState = HostPickerUiState(),
    /** 解除寄生状态。 */
    val deactivate: DeactivateUiState = DeactivateUiState(),
    val recoveryProgress: HiddenOperationProgress? = null,
    val webUiStopProgress: HiddenOperationProgress? = null,
) {
    val canActivate: Boolean
        get() = LiteHiddenRecoveryLinkPolicy.canActivate(hostPicker.canConfirm, webUiLinkAvailable, recoveryLinkSaved, preparingLink)

    /** 「解除寄生」入口：有宿主且当前未处于隐藏生效态（ON/UNKNOWN 都不给入口）。 */
    val showDeactivate: Boolean
        get() = HiddenHostPolicy.deactivateVisible(activeHost, status != LiteHiddenStatus.OFF)

    /** 「开启隐藏模式」按钮：只有**明确 OFF** 才可点（UNKNOWN 先解决读取失败，避免重复开启）。 */
    val enableButtonEnabled: Boolean
        get() = !checkingState && phase != HiddenCardPhase.ENABLING && status == LiteHiddenStatus.OFF
}

/**
 * 隐藏设置页（Lite 专属；设置项「隐藏」点入）。视觉与「界面与外观→主题设置」一致：
 * Material = ExpressiveScaffold + LargeFlexibleTopAppBar + LazyColumn + SegmentedColumn；
 * Miuix = Scaffold + BlurredBar/TopAppBar + LazyColumn + Card。
 * 激活成功后整页与设置项一起消失。
 */
@Composable
fun LiteHiddenScreen(onBack: () -> Unit) {
    val viewModel = viewModel<LiteHiddenViewModel>()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(ui.hidden) { if (ui.hidden) onBack() }
    when (LocalUiMode.current) {
        UiMode.Miuix -> LiteHiddenScreenMiuix(ui, viewModel, onBack)
        UiMode.Material -> LiteHiddenScreenMaterial(ui, viewModel, onBack)
    }
    // 选择寄生应用（整页）；二次确认弹窗在列表之上。
    HostPickerPage(
        ui = ui.hostPicker,
        actions = HostPickerActions(
            onSelect = viewModel::onSelectHost,
            onSearchQueryChange = viewModel::onHostSearchQueryChange,
            onNext = viewModel::onHostNext,
            onBackToList = viewModel::onHostBackToList,
            onDismiss = viewModel::onDismissHostPicker,
            onConfirm = viewModel::onConfirmActivate,
            onCopyRecoveryLink = viewModel::onCopyRecoveryLink,
            onRetryLoad = viewModel::onRetryHostLoad,
        ),
        iconFor = viewModel::hostIcon,
    )
    HiddenConfirmDialog(ui, viewModel)
    HiddenDeactivateDialog(ui, viewModel)
    WebUiStopDialog(ui, viewModel::dismissStopWebUi, viewModel::confirmStopWebUi)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiteHiddenScreenMaterial(
    ui: LiteHiddenPageUi,
    viewModel: LiteHiddenViewModel,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                title = { Text(stringResource(R.string.lite_hidden_title)) },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            item {
                SegmentedColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    content = listOf { Column(Modifier.padding(16.dp)) { HiddenPageBody(ui, viewModel) } },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiteHiddenScreenMiuix(
    ui: LiteHiddenPageUi,
    viewModel: LiteHiddenViewModel,
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface
    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                MiuixTopAppBar(
                    color = barColor,
                    title = stringResource(R.string.lite_hidden_title),
                    navigationIcon = {
                        MiuixIconButton(onClick = onBack) {
                            MiuixIcon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.ui_d_back),
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            HiddenPageBody(ui, viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenPageBody(ui: LiteHiddenPageUi, viewModel: LiteHiddenViewModel) {
    val colors = hiddenFlowColors()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HiddenFlowText(stringResource(ui.statusRes), secondary = true)
        if (ui.status == LiteHiddenStatus.UNKNOWN && !ui.checkingState) {
            HiddenFlowButton(stringResource(R.string.lite_hidden_retry_state), viewModel::refresh,
                Modifier.fillMaxWidth(), enabled = !ui.preparingLink && !ui.stoppingWebUi &&
                    !ui.hostPicker.visible && !ui.deactivate.visible && !ui.confirmStopWebUi)
        }
        HiddenFlowButton(stringResource(R.string.lite_hidden_enable_button), viewModel::onClickEnable,
            Modifier.fillMaxWidth(), enabled = ui.enableButtonEnabled && !ui.stoppingWebUi && !ui.preparingLink &&
                !ui.deactivate.visible && !ui.confirmStopWebUi, primary = true)
        HiddenFlowText(stringResource(R.string.lite_hidden_button_hint), secondary = true)
        ValueRow(ui, viewModel::onPortChange)
        WebUiLinkRow(ui, viewModel::copyPreviewLink)
        RecoveryLinkActions(ui, viewModel)
        WebUiServiceActions(ui, viewModel::requestStopWebUi, viewModel::refreshWebUiStatus)
        if (ui.showDeactivate) {
            HiddenFlowButton(stringResource(R.string.lite_hidden_deactivate), viewModel::onDeactivateClick,
                Modifier.fillMaxWidth(), enabled = !ui.preparingLink && !ui.stoppingWebUi &&
                    !ui.hostPicker.visible && !ui.confirmStopWebUi)
            HiddenFlowText(ui.activeHost, secondary = true, maxLines = 2)
        }
        ui.failureRes?.let { HiddenFlowText(stringResource(it), color = colors.error, secondary = true) }
    }
}

@Composable
private fun ValueRow(ui: LiteHiddenPageUi, onValueChange: (String) -> Unit) {
    val colors = hiddenFlowColors()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HiddenFlowText(stringResource(R.string.lite_hidden_webui_port), Modifier.weight(1f))
        BasicTextField(
            value = ui.portText, onValueChange = onValueChange, singleLine = true,
            enabled = !ui.preparingLink && !ui.stoppingWebUi && !ui.hostPicker.visible,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = colors.text, fontSize = 16.sp, textAlign = TextAlign.End),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier.widthIn(min = 90.dp, max = 120.dp)
                .background(colors.surface, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
    if (!ui.portValid) HiddenFlowText(stringResource(R.string.lite_hidden_port_invalid), color = colors.error, secondary = true)
}

@Composable
private fun WebUiLinkRow(ui: LiteHiddenPageUi, onCopy: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(enabled = ui.webUiLinkAvailable, onClick = onCopy).padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HiddenFlowText(stringResource(R.string.lite_hidden_webui_link))
        HiddenFlowText(if (ui.webUiLinkAvailable) ui.webUiLink else stringResource(R.string.ui_polish_link_not_prepared),
            secondary = true, maxLines = 2)
    }
}

@Composable
private fun RecoveryLinkActions(ui: LiteHiddenPageUi, viewModel: LiteHiddenViewModel) {
    val colors = hiddenFlowColors()
    if (ui.preparingLink) {
        HiddenFlowProgress(ui.recoveryProgress, R.string.ui_polish_link_preparing)
    } else if (!ui.webUiLinkAvailable) {
        HiddenFlowButton(stringResource(R.string.ui_polish_link_prepare), viewModel::prepareRecoveryLink, Modifier.fillMaxWidth(), enabled = !ui.stoppingWebUi)
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HiddenFlowButton(stringResource(R.string.lite_hidden_copy_link), viewModel::onCopyRecoveryLink, Modifier.weight(1f))
            HiddenFlowButton(stringResource(R.string.ui_polish_link_open), viewModel::openRecoveryLink, Modifier.weight(1f))
        }
        HiddenFlowText(stringResource(if (ui.recoveryLinkSaved) R.string.ui_polish_link_saved else R.string.ui_polish_link_save_hint),
            secondary = true, color = if (ui.recoveryLinkSaved) colors.primary else colors.secondary)
        if (ui.linkPortAdjusted) HiddenFlowText(stringResource(R.string.ui_polish_link_actual_port, ui.portText), secondary = true)
    }
    if (ui.linkFailure) HiddenFlowText(stringResource(R.string.ui_polish_link_failed), color = colors.error, secondary = true)
}

@Composable
private fun HiddenConfirmDialog(ui: LiteHiddenPageUi, viewModel: LiteHiddenViewModel) {
    if (!ui.hostPicker.isConfirming) return
    HiddenFlowDialog(
        title = stringResource(R.string.lite_hidden_confirm_title),
        onDismiss = viewModel::onHostBackToList,
        confirmLabel = stringResource(R.string.lite_hidden_confirm_enable),
        onConfirm = viewModel::onConfirmActivate,
        dismissLabel = stringResource(R.string.lite_hidden_confirm_cancel),
        confirmEnabled = ui.canActivate,
    ) {
        HiddenFlowText(stringResource(R.string.lite_hidden_confirm_body), secondary = true)
        ConfirmRow(stringResource(R.string.host_picker_confirm_host), ui.hostPicker.selectedDisplayName.ifBlank { "—" })
        ConfirmRow(stringResource(R.string.host_picker_confirm_port), ui.hostPicker.portText)
        RecoveryLinkActions(ui, viewModel)
    }
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HiddenFlowText(label, secondary = true)
        HiddenFlowText(value, maxLines = 2)
    }
}

@Composable
private fun HiddenDeactivateDialog(ui: LiteHiddenPageUi, viewModel: LiteHiddenViewModel) {
    val state = ui.deactivate
    if (!state.visible) return
    val colors = hiddenFlowColors()
    HiddenFlowDialog(
        title = stringResource(R.string.lite_hidden_deactivate_title),
        onDismiss = viewModel::onDismissDeactivate,
        confirmLabel = stringResource(R.string.lite_hidden_deactivate_confirm),
        onConfirm = viewModel::onConfirmDeactivate,
        dismissLabel = stringResource(R.string.lite_hidden_confirm_cancel),
        confirmEnabled = !state.running && state.doneRes == null,
        dismissEnabled = !state.running,
    ) {
        HiddenFlowText(stringResource(R.string.lite_hidden_deactivate_body), secondary = true)
        if (state.hostPackage.isNotBlank()) HiddenFlowText(state.hostPackage, secondary = true)
        if (state.running) HiddenFlowProgress(state.progress, R.string.lite_hidden_deactivate_running)
        if (state.showSteps) HostStepList(state.steps)
        state.failureRes?.let { HiddenFlowText(stringResource(it), color = colors.error, secondary = true) }
        state.doneRes?.let { HiddenFlowText(stringResource(it), color = colors.primary, secondary = true) }
    }
}

/** Listener controls share the current skin, without changing hidden mode or recovery data. */
@Composable
internal fun WebUiServiceActions(ui: LiteHiddenPageUi, onStop: () -> Unit, onRefresh: () -> Unit) {
    val colors = hiddenFlowColors()
    val busy = ui.stoppingWebUi || ui.preparingLink || ui.hostPicker.isRunning || ui.deactivate.running
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HiddenFlowText(stringResource(R.string.webui_control_title), title = true)
        val status = when {
            ui.stoppingWebUi -> R.string.webui_control_stopping
            ui.checkingWebUi -> R.string.webui_control_checking
            ui.webUiState == RecoveryWebUiState.RUNNING -> R.string.webui_control_running
            ui.webUiState == RecoveryWebUiState.STOPPED -> R.string.webui_control_stopped
            else -> R.string.webui_control_unknown
        }
        HiddenFlowText(stringResource(status), secondary = true)
        if (ui.webUiState == RecoveryWebUiState.RUNNING || ui.stoppingWebUi) {
            HiddenFlowButton(stringResource(if (ui.stoppingWebUi) R.string.webui_control_stopping else R.string.webui_control_stop),
                onStop, Modifier.fillMaxWidth(), enabled = !busy && !ui.checkingWebUi)
        } else if (ui.webUiState == RecoveryWebUiState.UNKNOWN && !ui.checkingWebUi) {
            HiddenFlowButton(stringResource(R.string.webui_control_retry), onRefresh,
                Modifier.fillMaxWidth(), enabled = !busy)
        }
        if (ui.webUiState == RecoveryWebUiState.STOPPED && !ui.checkingWebUi) {
            HiddenFlowText(stringResource(R.string.webui_control_restart_hint), secondary = true)
        }
        if (ui.webUiStopFailed) HiddenFlowText(stringResource(R.string.webui_control_stop_failed),
            color = colors.error, secondary = true)
    }
}

@Composable
internal fun WebUiStopDialog(ui: LiteHiddenPageUi, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    if (!ui.confirmStopWebUi) return
    HiddenFlowDialog(
        title = stringResource(R.string.webui_control_confirm_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(if (ui.stoppingWebUi) R.string.webui_control_stopping else R.string.webui_control_stop),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.lite_hidden_confirm_cancel),
        confirmEnabled = !ui.stoppingWebUi,
        dismissEnabled = !ui.stoppingWebUi,
    ) {
        HiddenFlowText(stringResource(R.string.webui_control_confirm_body), secondary = true)
        if (ui.stoppingWebUi) HiddenFlowProgress(ui.webUiStopProgress, R.string.webui_control_stopping)
    }
}
