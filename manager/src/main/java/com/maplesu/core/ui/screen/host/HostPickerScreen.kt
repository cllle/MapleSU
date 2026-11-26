package com.maplesu.core.ui.screen.host

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.maplesu.core.R
import dev.maplesu.manager.litehidden.HiddenActivateStep
import dev.maplesu.manager.litehidden.HiddenDeactivateStep
import dev.maplesu.manager.litehidden.HostCandidate

/** Full-page host selection: one state machine, native controls for each visual skin. */
@Composable
fun HostPickerPage(
    ui: HostPickerUiState,
    actions: HostPickerActions,
    iconFor: (String) -> ImageBitmap? = { null },
) {
    if (!ui.visible) return
    val colors = hiddenFlowColors()
    val listState = rememberLazyListState()
    LaunchedEffect(ui.searchQuery) { listState.scrollToItem(0) }
    Dialog(
        onDismissRequest = { if (!ui.isRunning) actions.onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val lightBars = colors.background.luminance() > 0.5f
        DisposableEffect(view, lightBars) {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
            onDispose { }
        }
        Column(
            Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HiddenFlowText(stringResource(R.string.host_picker_title), Modifier.weight(1f), title = true)
                HiddenFlowButton(stringResource(R.string.host_picker_cancel), actions.onDismiss, enabled = !ui.isRunning)
            }
            HiddenFlowText(stringResource(R.string.host_picker_subtitle), secondary = true)
            HiddenFlowText(stringResource(R.string.host_picker_risk), secondary = true)
            if (ui.phase == HostPickerPhase.LIST) HiddenFlowSearch(ui.searchQuery, actions.onSearchQueryChange)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    ui.isLoading || ui.isRunning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        HiddenFlowProgress(ui.progress,
                            if (ui.isRunning) R.string.host_picker_running else R.string.host_picker_loading)
                    }
                    ui.isEmpty -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        HiddenFlowText(stringResource(R.string.host_picker_empty), secondary = true)
                        HiddenFlowButton(stringResource(R.string.host_picker_retry), actions.onRetryLoad)
                    }
                    ui.phase == HostPickerPhase.LIST && ui.filteredCandidates.isEmpty() ->
                        HiddenFlowText(stringResource(R.string.host_picker_search_empty), Modifier.align(Alignment.Center), secondary = true)
                    else -> LazyColumn(Modifier.selectableGroup(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ui.filteredCandidates, key = { it.packageName }) { candidate ->
                            HostRow(candidate, candidate.packageName == ui.selected, iconFor(candidate.packageName),
                                enabled = ui.phase == HostPickerPhase.LIST,
                                onClick = { actions.onSelect(candidate.packageName) })
                        }
                    }
                }
            }
            if (ui.phase == HostPickerPhase.LIST && ui.hasSelection) {
                HiddenFlowText(stringResource(R.string.host_picker_selected, ui.selectedDisplayName), secondary = true, maxLines = 1)
            }
            if (ui.phase == HostPickerPhase.FAILED) {
                ui.failureRes?.let { HiddenFlowText(stringResource(it), color = colors.error, secondary = true) }
                if (ui.steps.isNotEmpty()) HostStepList(ui.steps)
                HiddenFlowButton(stringResource(R.string.host_picker_retry), actions.onRetryLoad)
            }
            HiddenFlowButton(stringResource(R.string.host_picker_next), actions.onNext,
                Modifier.fillMaxWidth(), enabled = ui.canGoNext, primary = true)
        }
    }
}

@Composable
private fun HostRow(candidate: HostCandidate, selected: Boolean, icon: ImageBitmap?, enabled: Boolean, onClick: () -> Unit) {
    val colors = hiddenFlowColors()
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().background(colors.surface, shape)
            .border(if (selected) 1.5.dp else 0.dp, if (selected) colors.primary else Color.Transparent, shape)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (icon != null) Image(icon, null, Modifier.size(44.dp))
            else HiddenFlowText(candidate.displayName.take(1), title = true)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HiddenFlowText(candidate.displayName, maxLines = 1)
            HiddenFlowText(candidate.packageName, secondary = true, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(22.dp).border(1.5.dp, if (selected) colors.primary else colors.outline, CircleShape)
            .padding(5.dp).background(if (selected) colors.primary else Color.Transparent, CircleShape))
    }
}

/** User-facing outcomes only; low-level wire/code/detail stay in diagnostic artifacts. */
@Composable
fun HostStepList(steps: List<HostStepUi>, modifier: Modifier = Modifier) {
    val colors = hiddenFlowColors()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEach { step ->
            HiddenFlowText(
                text = (if (step.ok) "✓  " else "×  ") + stringResource(hostStepLabelRes(step.wire)),
                secondary = true, color = if (step.ok) colors.primary else colors.error,
            )
        }
    }
}

/** 步骤 wire → 短文案（未知 wire 用通用"步骤"，不编造语义）。 */
fun hostStepLabelRes(wire: String): Int = when (wire) {
    HiddenActivateStep.PRECHECK.wire -> R.string.lite_hidden_step_precheck
    HiddenActivateStep.HOST_SO.wire -> R.string.lite_hidden_step_host_so
    HiddenActivateStep.WEBUI_START.wire -> R.string.lite_hidden_step_webui_start
    HiddenActivateStep.WEBUI_PING.wire -> R.string.lite_hidden_step_webui_ping
    HiddenActivateStep.HIDE_STATE.wire -> R.string.lite_hidden_step_hide_state
    HiddenActivateStep.FINAL_VERIFY.wire -> R.string.lite_hidden_step_final_verify
    HiddenActivateStep.MANAGER_REMOVAL.wire -> R.string.lite_hidden_step_manager_removal
    HiddenDeactivateStep.RESTORE_HOST_SO.wire -> R.string.lite_hidden_deactivate_step_so
    HiddenDeactivateStep.REVOKE_SU_AUTH.wire -> R.string.lite_hidden_deactivate_step_su
    HiddenDeactivateStep.STOP_DAEMON.wire -> R.string.lite_hidden_deactivate_step_daemon
    HiddenDeactivateStep.CLEANUP_RECOVERY.wire -> R.string.lite_hidden_deactivate_step_cleanup
    else -> R.string.lite_hidden_reason_unknown
}
