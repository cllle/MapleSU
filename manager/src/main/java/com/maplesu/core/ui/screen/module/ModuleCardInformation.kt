package com.maplesu.core.ui.screen.module

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maplesu.core.R
import com.maplesu.core.data.model.Module
import com.maplesu.core.ui.LocalUiMode
import com.maplesu.core.ui.UiMode
import dev.maplesu.manager.modules.ModuleWebUiLauncher
import dev.maplesu.manager.workmode.ModeRuntime
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Only actionable lifecycle feedback and a compact environment hint belong on the card. */
@Composable
internal fun ModuleCardInformation(module: Module, isPending: Boolean) {
    val status = if (module.remove) ModuleCardStatus.REMOVE_PENDING
        else moduleCardStatus(module.runtimeStatus, ModuleWebUiLauncher.isUltraModuleId(module.id))
    val statusRes = when (status) {
        ModuleCardStatus.MODULE_ABNORMAL -> R.string.module_card_abnormal
        ModuleCardStatus.STORED_ULTRA, ModuleCardStatus.STORED -> R.string.module_card_restart_required
        ModuleCardStatus.REMOVE_PENDING -> R.string.module_runtime_remove_pending
        ModuleCardStatus.FAILED -> R.string.module_runtime_failed
        else -> null
    }
    val unsupported = module.unsupported.distinct()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isPending) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (LocalUiMode.current) {
                    UiMode.Miuix -> top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(size = 18.dp)
                    UiMode.Material -> androidx.compose.material3.CircularProgressIndicator(
                        Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                ModuleCardInformationText(stringResource(R.string.module_card_processing), accent = true)
            }
        }
        if (status.needsAttention && statusRes != null) {
            val message = if (status == ModuleCardStatus.FAILED && module.runtimeExitCode != null) {
                stringResource(R.string.module_runtime_failed_code, module.runtimeExitCode)
            } else stringResource(statusRes)
            ModuleCardInformationText(message, error = status.isError)
        } else if (module.needsReboot || module.update) {
            ModuleCardInformationText(stringResource(R.string.module_card_restart_required))
        }
        module.preinitWarning(ModeRuntime.active)?.let {
            ModuleCardInformationText(stringResource(it), error = true)
        }
        if (module.propsFailed.isNotEmpty()) {
            ModuleCardInformationText(
                stringResource(R.string.module_card_properties_failed, module.propsFailed.size), error = true,
            )
        }
        if (unsupported.isNotEmpty()) {
            val labels = unsupported.map { moduleCardCapabilityLabel(it) }
            val summary = stringResource(R.string.module_card_unsupported_summary,
                labels.joinToString(stringResource(R.string.module_capability_separator)))
            when (LocalUiMode.current) {
                UiMode.Miuix -> top.yukonga.miuix.kmp.basic.Text(summary,
                    fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                UiMode.Material -> androidx.compose.material3.Text(summary,
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun ModuleCardBadges(module: Module, modifier: Modifier = Modifier) {
    val typeKey = ModuleTypeUi.tagKey(ModuleTypeUi.installedType(module.id))
    val labels = listOfNotNull(
        "META".takeIf { module.metamodule },
        stringResource(R.string.module_builtin_tag).takeIf { module.builtin },
        typeKey?.let { stringResource(if (it == "MAPLESU") R.string.module_type_tag_maplesu else R.string.module_type_tag_ksu) },
    )
    val badgeBackground: Color
    val badgeText: Color
    when (LocalUiMode.current) {
        UiMode.Miuix -> {
            badgeBackground = MiuixTheme.colorScheme.secondaryContainer
            badgeText = MiuixTheme.colorScheme.onSecondaryContainer
        }
        UiMode.Material -> {
            badgeBackground = MaterialTheme.colorScheme.secondaryContainer
            badgeText = MaterialTheme.colorScheme.onSecondaryContainer
        }
    }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { label ->
            val badgeModifier = Modifier.clip(RoundedCornerShape(6.dp)).background(badgeBackground)
                .padding(horizontal = 7.dp, vertical = 3.dp)
            when (LocalUiMode.current) {
                UiMode.Miuix -> top.yukonga.miuix.kmp.basic.Text(label, modifier = badgeModifier,
                    color = badgeText, fontSize = 12.sp, maxLines = 1)
                UiMode.Material -> androidx.compose.material3.Text(label, modifier = badgeModifier,
                    color = badgeText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ModuleCardInformationText(text: String, error: Boolean = false, accent: Boolean = false) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> with(MiuixTheme.colorScheme) {
            top.yukonga.miuix.kmp.basic.Text(text, fontSize = 13.sp,
                color = if (error) this.error else if (accent) primary else onSurfaceVariantSummary)
        }
        UiMode.Material -> with(MaterialTheme.colorScheme) {
            androidx.compose.material3.Text(text, style = MaterialTheme.typography.bodySmall,
                color = if (error) this.error else if (accent) primary else onSurfaceVariant)
        }
    }
}

@Composable
private fun moduleCardCapabilityLabel(key: String): String = when (key) {
    "toggle" -> stringResource(R.string.module_capability_toggle)
    "stop" -> stringResource(R.string.module_capability_stop)
    "in-place-update" -> stringResource(R.string.module_capability_in_place_update)
    "mount" -> stringResource(R.string.module_capability_mount)
    "zygisk" -> stringResource(R.string.module_capability_zygisk)
    "sepolicy-apply" -> stringResource(R.string.module_capability_sepolicy_apply)
    else -> key
}
