package com.maplesu.core.ui.screen.install

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.maplesu.core.R
import com.maplesu.core.ui.theme.LocalEnableBlur
import com.maplesu.core.ui.util.BlurredBar
import com.maplesu.core.ui.util.miuixBarColor
import com.maplesu.core.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Miuix 皮肤：原生选择列表、按需显示的安装信息与一个「下一步」。
 *
 * @author weishu
 * @date 2024/3/12.
 */
@Composable
internal fun InstallScreenMiuix(
    uiState: InstallUiState,
    actions: InstallScreenActions,
    snackbarHost: SnackbarHostState,
) {
    val enableBlur = LocalEnableBlur.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = miuixBarColor(blurActive)

    Scaffold(
        topBar = {
            TopBar(
                onBack = actions.onBack,
                title = stringResource(R.string.install),
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                barColor = barColor,
            )
        },
        popupHost = { },
        snackbarHost = {
            SnackbarHost(
                state = snackbarHost,
                modifier = Modifier.padding(bottom = 20.dp),
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(top = 12.dp)
                    .padding(horizontal = 16.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        SelectInstallMethod(
                            state = uiState,
                            onSelected = actions.onSelectMethod,
                            onSelectBootImage = actions.onSelectBootImage,
                        )
                    }
                    InstallTargetSelectionSection(
                        state = uiState,
                        onSelectMode = actions.onSelectTargetMode,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        InstallPartitionSection(
                            state = uiState,
                            onSelectPartition = actions.onSelectPartition,
                        )
                    }
                    if (uiState.sourceLoading || uiState.sourceAccessFailed) {
                        Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            InstallSourceStatusSection(uiState)
                        }
                    }
                    InstallUltraStatusSection(uiState, Modifier.padding(top = 12.dp))
                    if (uiState.hasInstallSource) {
                        Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            InstallTargetStepSection(state = uiState, onSelectInitBootKmi = actions.onSelectInitBootKmi)
                        }
                    }

                    InstallAdvancedOptions(uiState, actions, Modifier.padding(top = 12.dp))
                    TextButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        text = stringResource(id = R.string.install_next),
                        enabled = uiState.canInstall,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = actions.onNext
                    )

                    Spacer(
                        Modifier.height(
                            16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                    WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectInstallMethod(
    state: InstallUiState,
    onSelected: (InstallMethod) -> Unit,
    onSelectBootImage: () -> Unit,
) {
    val onClick = { option: InstallMethod ->
        when (option) {
            is InstallMethod.SelectFile -> onSelectBootImage()
            else -> onSelected(option)
        }
    }

    Column {
        state.installMethodOptions.forEach { option ->
            CheckboxPreference(
                title = if (option == InstallMethod.DirectInstall) {
                    stringResource(state.directInstallLabel)
                } else stringResource(option.label),
                summary = if (option == InstallMethod.DirectInstall) stringResource(R.string.install_read_current_partition_summary) else option.summary,
                checked = option.javaClass == state.installMethod?.javaClass,
                enabled = state.targetSelectable,
                onCheckedChange = { if (state.targetSelectable) onClick(option) },
            )
        }
    }
}

@Composable
private fun TopBar(
    onBack: () -> Unit = {},
    title: String,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    barColor: Color,
) {
    BlurredBar(backdrop) {
        TopAppBar(
            color = barColor,
            title = title,
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
                        tint = colorScheme.onSurface,
                        contentDescription = null,
                    )
                }
            },
            scrollBehavior = scrollBehavior
        )
    }
}
