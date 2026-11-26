package com.maplesu.core.ui.screen.install

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maplesu.core.R
import com.maplesu.core.ui.LocalUiMode
import com.maplesu.core.ui.UiMode
import com.maplesu.core.ui.component.material.SegmentedColumn
import com.maplesu.core.ui.component.material.SegmentedDropdownItem
import com.maplesu.core.ui.component.material.SegmentedItemContainer
import dev.maplesu.manager.installer.InstallPartition
import dev.maplesu.manager.installer.InstallTargetDecision
import dev.maplesu.manager.installer.InstallTargetMode
import dev.maplesu.manager.installer.installTargetBlockerMessage
import dev.maplesu.manager.workmode.ui.WorkModeTargetRow
import dev.maplesu.manager.workmode.ui.WorkModeTargetSheet
import dev.maplesu.manager.workmode.ui.workModeNameRes
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Only changes this installation target; it never switches the running work mode. */
@Composable
internal fun InstallTargetSelectionSection(
    state: InstallUiState,
    onSelectMode: (InstallTargetMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTargetSheet by rememberSaveable { mutableStateOf(false) }
    WorkModeTargetRow(
        value = stringResource(workModeNameRes(state.targetMode.workMode)),
        enabled = state.targetSelectable,
        onClick = { showTargetSheet = true },
        modifier = modifier,
    )
    WorkModeTargetSheet(
        show = showTargetSheet && state.targetSelectable,
        selected = state.targetMode.workMode,
        current = null,
        onSelect = { mode ->
            if (state.targetSelectable) onSelectMode(InstallTargetMode.fromWorkMode(mode))
        },
        onDismiss = { showTargetSheet = false },
    )
}

@Composable
internal fun InstallUltraStatusSection(state: InstallUiState,
    modifier: Modifier = Modifier) {
    if (state.targetMode != InstallTargetMode.ULTRA) return
    Column(modifier) {
        if (state.installMethod == InstallMethod.DirectInstall) InstallTargetInfo(
            title = stringResource(R.string.install_ultra_current_carrier),
            summary = state.observedCarrier?.let { it + state.observedSlotSuffix.orEmpty() }
                ?: stringResource(R.string.install_ultra_carrier_unknown),
        )
        if (state.hasInstallSource) state.ultraRoute.blocker?.let { reason ->
            InstallTargetInfo(title = stringResource(R.string.install_step_kernel_unavailable), summary = reason)
        }
    }
}

/** The displayed target is also the partition passed to the existing installer. */
@Composable
internal fun InstallPartitionSection(
    state: InstallUiState,
    onSelectPartition: (InstallPartition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.install_step_partition)
    if (state.partitionFixed) {
        InstallTargetInfo(
            title = title,
            summary = stringResource(R.string.install_partition_fixed, state.partition.wire),
            modifier = modifier,
        )
        return
    }
    val items = state.partitionOptions.map { it.wire }
    val onSelected: (Int) -> Unit = { index ->
        if (state.targetSelectable) state.partitionOptions.getOrNull(index)?.let(onSelectPartition)
    }
    when (LocalUiMode.current) {
        UiMode.Material -> SegmentedColumn(
            modifier = modifier,
            content = listOf {
                SegmentedDropdownItem(
                    title = title,
                    items = items,
                    selectedIndex = state.partitionOptions.indexOf(state.partition),
                    enabled = state.targetSelectable,
                    onItemSelected = onSelected,
                )
            },
        )
        UiMode.Miuix -> Column(modifier) {
            OverlayDropdownPreference(
                title = title,
                items = items,
                selectedIndex = state.partitionOptions.indexOf(state.partition),
                enabled = state.targetSelectable,
                onSelectedIndexChange = onSelected,
            )
        }
    }
}

/** Boot uses its own kernel; init_boot selects an actually packaged KMI component. */
@Composable
internal fun InstallTargetStepSection(state: InstallUiState, modifier: Modifier = Modifier,
    onSelectInitBootKmi: (String?) -> Unit = {}) {
    if (state.partition == InstallPartition.INIT_BOOT &&
        (state.targetMode == InstallTargetMode.ULTRA || state.installMethod is InstallMethod.SelectFile)) {
        val kmis = listOf<String?>(null) + state.initBootKmis
        val items = listOf(stringResource(R.string.current_device_kmi)) + state.initBootKmis
        val selected = kmis.indexOf(state.initBootKmi).coerceAtLeast(0)
        val onSelected: (Int) -> Unit = { index ->
            if (state.targetSelectable && index in kmis.indices) onSelectInitBootKmi(kmis[index])
        }
        when (LocalUiMode.current) {
            UiMode.Material -> SegmentedColumn(modifier = modifier, content = listOf {
                SegmentedDropdownItem(title = stringResource(R.string.select_kmi), items = items,
                    selectedIndex = selected, enabled = state.targetSelectable, onItemSelected = onSelected)
            })
            UiMode.Miuix -> Column(modifier) {
                OverlayDropdownPreference(title = stringResource(R.string.select_kmi), items = items,
                    selectedIndex = selected, enabled = state.targetSelectable,
                    onSelectedIndexChange = onSelected)
            }
        }
        return
    }
    when (state.targetMode) {
        InstallTargetMode.ULTRA -> InstallKernelVersionSection(profile = state.bootFlow, modifier = modifier)
        InstallTargetMode.NORMAL -> InstallNormalTargetSection(
            decision = state.normalDecision,
            partition = state.partition,
            modifier = modifier,
        )
    }
}

@Composable
internal fun InstallSourceStatusSection(state: InstallUiState) {
    val fromFile = state.installMethod is InstallMethod.SelectFile
    InstallTargetInfo(
        title = stringResource(when {
            fromFile && state.sourceLoading -> R.string.install_source_checking_backup
            state.sourceLoading && state.sourceUsesManagedRoot -> R.string.install_source_connecting_service
            state.sourceLoading -> R.string.install_source_connecting
            state.sourceAccessFailure == InstallSourceAccessFailure.CHANNEL_UNAVAILABLE ->
                R.string.install_source_service_unavailable
            state.sourceAccessFailure == InstallSourceAccessFailure.ROOT_REQUIRED -> R.string.install_source_root_required
            else -> R.string.install_source_check_failed
        }),
        summary = stringResource(when {
            fromFile && state.sourceLoading -> R.string.install_source_reading_image_hint
            state.sourceLoading && state.sourceUsesManagedRoot -> R.string.install_source_connecting_service_hint
            state.sourceLoading -> R.string.install_source_connecting_hint
            state.sourceAccessFailure == InstallSourceAccessFailure.CHANNEL_UNAVAILABLE -> R.string.install_source_service_retry
            state.sourceAccessFailure == InstallSourceAccessFailure.ROOT_REQUIRED -> R.string.install_source_retry_hint
            fromFile -> R.string.install_source_check_retry
            else -> R.string.install_source_partition_retry
        }),
    )
}

@Composable
private fun InstallNormalTargetSection(
    decision: InstallTargetDecision?,
    partition: InstallPartition,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    InstallTargetInfo(
        title = stringResource(R.string.install_target_information),
        summary = when (decision) {
            null -> stringResource(R.string.install_target_kernel_loading)
            is InstallTargetDecision.Ready -> stringResource(
                R.string.install_target_normal_ready,
                decision.partition ?: partition.wire,
                decision.kmi.orEmpty(),
            )
            is InstallTargetDecision.Blocked -> installTargetBlockerMessage(resources, decision)
        },
        modifier = modifier,
    )
}

@Composable
internal fun InstallKernelVersionSection(profile: BootFlowProfile?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        when {
            profile == null || profile.reading -> InstallTargetInfo(
                title = stringResource(R.string.install_step_kernel),
                summary = stringResource(R.string.install_target_kernel_loading),
            )
            profile.release.isNotBlank() -> {
                InstallTargetInfo(
                    title = if (profile.seriesLabel.isNotEmpty()) {
                        stringResource(R.string.install_kernel_series, profile.seriesLabel)
                    } else {
                        stringResource(R.string.install_step_kernel)
                    },
                    summary = profile.release,
                )
                if (profile.releaseMismatch) {
                    KernelWarning(
                        stringResource(
                            R.string.install_boot_release_mismatch,
                            profile.deviceRelease,
                            profile.release,
                        ),
                    )
                }
            }
            else -> InstallTargetInfo(
                title = stringResource(R.string.install_step_kernel_unavailable),
                summary = profile.reasonText(),
            )
        }
    }
}

@Composable
private fun InstallTargetInfo(title: String, summary: String, modifier: Modifier = Modifier) {
    when (LocalUiMode.current) {
        UiMode.Material -> SegmentedColumn(
            modifier = modifier,
            content = listOf {
                SegmentedItemContainer {
                    ListItem(
                        headlineContent = { androidx.compose.material3.Text(title) },
                        supportingContent = { androidx.compose.material3.Text(summary) },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceBright,
                            supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            },
        )
        UiMode.Miuix -> Column(modifier) {
            BasicComponent(title = title, summary = summary)
        }
    }
}

@Composable
private fun KernelWarning(text: String) {
    val modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    when (LocalUiMode.current) {
        UiMode.Material -> androidx.compose.material3.Text(
            text = text,
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        UiMode.Miuix -> top.yukonga.miuix.kmp.basic.Text(
            text = text,
            modifier = modifier,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.error,
        )
    }
}

/** Keep the next step and stable failure code together. */
@Composable
private fun BootFlowProfile.reasonText(): String {
    val reason = stringResource(
        when (stableCode) {
            "BOOT_SOURCE_READ_FAILED", "ULTRA_KERNEL_BASE_UNAVAILABLE", "ULTRA_KERNEL_BASE_RELEASE_UNREADABLE" ->
                R.string.install_kernel_image_unreadable
            else -> R.string.install_kernel_unavailable_hint
        },
    )
    val imageHint = if (stableCode.startsWith("ULTRA_INIT_") || stableCode.startsWith("ULTRA_KERNEL_INIT_BOOT_")) "" else
        "\n" + stringResource(R.string.install_kernel_boot_image_hint)
    return reason + imageHint + stableCode.takeIf(String::isNotBlank)?.let { " [$stableCode]" }.orEmpty()
}
