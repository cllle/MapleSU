package com.maplesu.core.ui.screen.flash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maplesu.core.R
import com.maplesu.core.ui.component.KeyEventBlocker
import com.maplesu.core.ui.theme.LocalEnableBlur
import com.maplesu.core.ui.util.BlurredBar
import com.maplesu.core.ui.util.miuixBarColor
import com.maplesu.core.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * @author weishu
 * @date 2023/1/1.
 */
@Composable
fun FlashScreenMiuix(
    state: FlashUiState,
    actions: FlashScreenActions,
) {
    val enableBlur = LocalEnableBlur.current
    val scrollState = rememberScrollState()
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = miuixBarColor(blurActive)

    if (state.showJailbreakWarning) {
        JailbreakFlashWarningDialog(
            onConfirm = actions.onConfirmJailbreakWarning,
            onDismiss = actions.onDismissJailbreakWarning,
        )
    }

    Scaffold(
        topBar = {
            TopBar(
                titleRes = state.titleRes,
                onBack = actions.onBack,
                onSave = actions.onSaveLog,
                showSave = state.showLog,
                backdrop = backdrop,
                barColor = barColor,
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        KeyEventBlocker {
            it.key == Key.VolumeDown || it.key == Key.VolumeUp
        }

        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            Column(
                modifier = Modifier
                    .fillMaxSize(1f)
                    .scrollEndHaptic()
                    .padding(
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        end = innerPadding.calculateEndPadding(layoutDirection),
                    )
                    .verticalScroll(scrollState),
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
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                FlashResultActions(state, actions)
                Spacer(
                    Modifier.height(
                        (if (state.directInstall) 80.dp else 12.dp) + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
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
            start = 20.dp, end = 20.dp,
            bottom = 20.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
        ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (state.canSaveBoot) {
                TextButton(
                    text = stringResource(R.string.install_boot_action_save),
                    colors = ButtonDefaults.textButtonColors(),
                    onClick = actions.onSaveBoot,
                )
            }
        }
        if (state.showRebootAction || state.showCloseAction) {
            FloatingActionButton(onClick = {
                if (!state.actionInProgress) {
                    if (state.showRebootAction) actions.onReboot() else actions.onClose()
                }
            }, shadowElevation = 0.dp) {
                if (state.actionInProgress) {
                    InfiniteProgressIndicator(color = colorScheme.onPrimary)
                } else {
                    Icon(
                        if (state.showRebootAction) Icons.Filled.Refresh else Icons.Filled.Close,
                        stringResource(if (state.showRebootAction) state.rebootLabelRes else R.string.close),
                        tint = colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/** 非直装成功态与失败恢复动作，与 Material 使用同一判据。 */
@Composable
private fun FlashResultActions(state: FlashUiState, actions: FlashScreenActions) {
    if (state.flashingStatus == FlashingStatus.FLASHING) return
    if (state.directInstall && state.flashingStatus == FlashingStatus.SUCCESS) return
    state.recoveryMessage?.let {
        Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
    if (state.canQuery) {
        TextButton(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            text = stringResource(if (state.querying) R.string.installer_recovery_querying else R.string.installer_recovery_query),
            enabled = !state.querying,
            colors = ButtonDefaults.textButtonColors(),
            onClick = actions.onQueryOriginalTask,
        )
    }
    if (state.canReturnToPreparation && state.flashingStatus == FlashingStatus.FAILED) {
        TextButton(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            text = stringResource(R.string.installer_recovery_return),
            colors = ButtonDefaults.textButtonColors(),
            onClick = actions.onReturnToPreparation,
        )
    }
    if (state.canRestore) {
        TextButton(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            text = stringResource(R.string.flash_restore_pre_install),
            colors = ButtonDefaults.textButtonColors(),
            onClick = actions.onRestore,
        )
    }
    if (!state.canSaveBoot && !state.showRebootAction && !state.showCloseAction) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (state.canSaveBoot) {
            TextButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.install_boot_action_save),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = actions.onSaveBoot,
            )
        }
        if (state.showRebootAction || state.showCloseAction) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (state.actionInProgress) {
                    InfiniteProgressIndicator()
                } else {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(
                            if (state.showCloseAction) R.string.close
                            else if (state.showLog) state.rebootLabelRes else R.string.install_boot_action_flash
                        ),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = { if (state.showRebootAction) actions.onReboot() else actions.onClose() },
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    @androidx.annotation.StringRes titleRes: Int,
    onBack: () -> Unit = {},
    onSave: () -> Unit = {},
    showSave: Boolean,
    backdrop: LayerBackdrop?,
    barColor: Color,
) {
    BlurredBar(backdrop) {
        SmallTopAppBar(
            title = stringResource(titleRes),
            color = barColor,
            navigationIcon = {
                IconButton(
                    onClick = onBack
                ) {
                    val layoutDirection = LocalLayoutDirection.current
                    Icon(
                        modifier = Modifier.graphicsLayer {
                            if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                        },
                        imageVector = MiuixIcons.Back,
                        contentDescription = null,
                        tint = colorScheme.onBackground
                    )
                }
            },
            actions = {
                if (showSave) {
                    IconButton(
                        onClick = onSave
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Share,
                            contentDescription = stringResource(id = R.string.save_log),
                            tint = colorScheme.onBackground
                        )
                    }
                }
            },
        )
    }
}
