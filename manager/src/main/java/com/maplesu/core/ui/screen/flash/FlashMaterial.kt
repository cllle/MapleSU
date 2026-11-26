package com.maplesu.core.ui.screen.flash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.maplesu.core.R
import com.maplesu.core.ui.component.KeyEventBlocker
import com.maplesu.core.ui.component.material.ExpressiveScaffold
import com.maplesu.core.ui.component.material.SnackBarHost
import com.maplesu.core.ui.component.material.TopBarBackButton
import com.maplesu.core.ui.component.material.expressiveTopAppBarColors

@Composable
fun FlashScreenMaterial(
    state: FlashUiState,
    actions: FlashScreenActions,
    snackBarHost: SnackbarHostState,
) {
    val scrollState = rememberScrollState()
    if (state.showJailbreakWarning) {
        JailbreakFlashWarningDialog(
            onConfirm = actions.onConfirmJailbreakWarning,
            onDismiss = actions.onDismissJailbreakWarning,
        )
    }

    ExpressiveScaffold(
        snackbarHost = {
            SnackBarHost(
                hostState = snackBarHost,
                modifier = Modifier.let { if (state.showRebootAction) it else it.safeDrawingPadding() })
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(state.titleRes))
                },
                colors = expressiveTopAppBarColors(),
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                actions = {
                    if (state.showLog) {
                        IconButton(onClick = actions.onSaveLog) {
                            Icon(Icons.Filled.Save, stringResource(R.string.save_log))
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val navBars = WindowInsets.navigationBars.asPaddingValues()
        val captionBar = WindowInsets.captionBar.asPaddingValues()
        KeyEventBlocker {
            it.key == Key.VolumeDown || it.key == Key.VolumeUp
        }

        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        end = innerPadding.calculateEndPadding(layoutDirection),
                    )
                    .verticalScroll(scrollState)
            ) {
                LaunchedEffect(state.text) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
                Spacer(Modifier.height(innerPadding.calculateTopPadding()))
                FlashStatusBanner(state)
                if (state.showLog) {
                    Text(
                        modifier = Modifier.padding(8.dp),
                        text = state.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                FlashResultActions(state, actions)
                Spacer(
                    Modifier.height(
                        16.dp + 54.dp + navBars.calculateBottomPadding() + captionBar.calculateBottomPadding()
                    )
                )
            }
            if (state.directInstall && state.flashingStatus == FlashingStatus.SUCCESS) {
                DirectInstallActions(state, actions, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun DirectInstallActions(
    state: FlashUiState,
    actions: FlashScreenActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(
            start = 16.dp, end = 16.dp,
            bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
        ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (state.canSaveBoot) {
                OutlinedButton(onClick = actions.onSaveBoot) {
                    Text(stringResource(R.string.install_boot_action_save))
                }
            }
        }
        if (state.showRebootAction || state.showCloseAction) {
            FloatingActionButton(onClick = {
                if (!state.actionInProgress) {
                    if (state.showRebootAction) actions.onReboot() else actions.onClose()
                }
            }) {
                if (state.actionInProgress) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(
                        if (state.showRebootAction) Icons.Filled.Refresh else Icons.Filled.Close,
                        stringResource(if (state.showRebootAction) state.rebootLabelRes else R.string.close),
                    )
                }
            }
        }
    }
}

/** 非直装成功态保留原有重启入口；失败时保留事务恢复入口。 */
@Composable
private fun FlashResultActions(state: FlashUiState, actions: FlashScreenActions) {
    if (state.flashingStatus == FlashingStatus.FLASHING) return
    if (state.directInstall && state.flashingStatus == FlashingStatus.SUCCESS) return
    state.recoveryMessage?.let {
        Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
    if (state.canQuery) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            enabled = !state.querying,
            onClick = actions.onQueryOriginalTask,
        ) { Text(stringResource(if (state.querying) R.string.installer_recovery_querying else R.string.installer_recovery_query)) }
    }
    if (state.canReturnToPreparation && state.flashingStatus == FlashingStatus.FAILED) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            onClick = actions.onReturnToPreparation,
        ) { Text(stringResource(R.string.installer_recovery_return)) }
    }
    if (state.canRestore) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            onClick = actions.onRestore,
        ) { Text(stringResource(R.string.flash_restore_pre_install)) }
    }
    if (!state.canSaveBoot && !state.showRebootAction && !state.showCloseAction) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.canSaveBoot) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = actions.onSaveBoot,
            ) { Text(stringResource(R.string.install_boot_action_save)) }
        }
        if (state.showRebootAction || state.showCloseAction) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = !state.actionInProgress,
                onClick = { if (state.showRebootAction) actions.onReboot() else actions.onClose() },
            ) {
                if (state.actionInProgress) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        stringResource(
                            if (state.showCloseAction) R.string.close
                            else if (state.showLog) state.rebootLabelRes else R.string.install_boot_action_flash
                        )
                    )
                }
            }
        }
    }
}
