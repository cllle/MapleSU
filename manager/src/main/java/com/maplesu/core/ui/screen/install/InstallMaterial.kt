package com.maplesu.core.ui.screen.install

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.maplesu.core.R
import com.maplesu.core.ui.component.material.ExpressiveScaffold
import com.maplesu.core.ui.component.material.SegmentedColumn
import com.maplesu.core.ui.component.material.SegmentedRadioItem
import com.maplesu.core.ui.component.material.SnackBarHost
import com.maplesu.core.ui.component.material.TopBarBackButton
import com.maplesu.core.ui.component.material.expressiveTopAppBarColors

/**
 * Material 皮肤：原生选择列表、按需显示的安装信息与一个「下一步」。
 *
 * @author weishu
 * @date 2024/3/12.
 */
@Composable
internal fun InstallScreenMaterial(
    uiState: InstallUiState,
    actions: InstallScreenActions,
    snackBarHost: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            TopBar(
                onBack = actions.onBack,
                title = stringResource(R.string.install),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackBarHost, modifier = Modifier.safeDrawingPadding()) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxHeight()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SelectInstallMethod(
                state = uiState,
                onSelected = actions.onSelectMethod,
                onSelectBootImage = actions.onSelectBootImage,
            )
            InstallTargetSelectionSection(
                state = uiState,
                onSelectMode = actions.onSelectTargetMode,
            )
            InstallPartitionSection(
                state = uiState,
                onSelectPartition = actions.onSelectPartition,
            )
            InstallUltraStatusSection(uiState)
            if (uiState.sourceLoading || uiState.sourceAccessFailed) {
                InstallSourceStatusSection(uiState)
            }

            if (uiState.hasInstallSource) {
                InstallTargetStepSection(state = uiState, onSelectInitBootKmi = actions.onSelectInitBootKmi)
            }

            InstallAdvancedOptions(uiState, actions)
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.canInstall,
                onClick = actions.onNext
            ) { Text(stringResource(R.string.install_next)) }
            Spacer(Modifier.height(WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()))
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

    key(state.installMethodOptions.size) {
        SegmentedColumn(
            content = state.installMethodOptions.map { option ->
                {
                    SegmentedRadioItem(
                        title = if (option == InstallMethod.DirectInstall) {
                            stringResource(state.directInstallLabel)
                        } else stringResource(option.label),
                        summary = if (option == InstallMethod.DirectInstall) stringResource(R.string.install_read_current_partition_summary) else option.summary,
                        selected = option.javaClass == state.installMethod?.javaClass,
                        enabled = state.targetSelectable,
                        onClick = { if (state.targetSelectable) onClick(option) }
                    )
                }
            }
        )
    }
}

@Composable
private fun TopBar(
    onBack: () -> Unit = {},
    title: String,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    LargeFlexibleTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            TopBarBackButton(onClick = onBack)
        },
        colors = expressiveTopAppBarColors(),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}
