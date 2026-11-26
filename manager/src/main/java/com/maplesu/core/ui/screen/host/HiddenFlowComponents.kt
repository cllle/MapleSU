package com.maplesu.core.ui.screen.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maplesu.core.ui.LocalUiMode
import com.maplesu.core.ui.UiMode
import com.maplesu.core.R
import com.maplesu.core.ui.component.SearchStatus
import com.maplesu.core.ui.component.miuix.SearchBar as MiuixSearchBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.maplesu.core.ui.theme.materialDialogColor
import com.maplesu.core.ui.theme.miuixDialogColor
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** Shared flow semantics, rendered with native components from the selected skin. */
internal data class HiddenFlowColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val secondary: Color,
    val primary: Color,
    val error: Color,
    val outline: Color,
)

@Composable
internal fun hiddenFlowColors(): HiddenFlowColors = when (LocalUiMode.current) {
    UiMode.Miuix -> with(MiuixTheme.colorScheme) {
        HiddenFlowColors(background.copy(alpha = 1f), surface, onSurface, onSurfaceVariantSummary,
            primary, error, outline)
    }
    UiMode.Material -> with(MaterialTheme.colorScheme) {
        HiddenFlowColors(background.copy(alpha = 1f), surfaceContainer, onSurface, onSurfaceVariant,
            primary, error, outline)
    }
}

@Composable
internal fun HiddenFlowText(
    text: String,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    color: Color = Color.Unspecified,
    title: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    val resolved = if (color != Color.Unspecified) color else {
        val colors = hiddenFlowColors()
        if (secondary) colors.secondary else colors.text
    }
    when (LocalUiMode.current) {
        UiMode.Miuix -> top.yukonga.miuix.kmp.basic.Text(
            text = text, modifier = modifier, color = resolved,
            fontSize = if (title) 20.sp else if (secondary) 13.sp else 16.sp,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        )
        UiMode.Material -> androidx.compose.material3.Text(
            text = text, modifier = modifier, color = resolved,
            style = if (title) MaterialTheme.typography.titleLarge else if (secondary)
                MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun HiddenFlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> top.yukonga.miuix.kmp.basic.TextButton(
            text = text, onClick = onClick, modifier = modifier, enabled = enabled,
            colors = if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
        )
        UiMode.Material -> if (primary) {
            androidx.compose.material3.Button(onClick = onClick, modifier = modifier, enabled = enabled) {
                androidx.compose.material3.Text(text)
            }
        } else {
            androidx.compose.material3.TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
                androidx.compose.material3.Text(text)
            }
        }
    }
}

/** The same native search inputs used by the superuser screen, kept inline here. */
@Composable
internal fun HiddenFlowSearch(query: String, onQueryChange: (String) -> Unit) {
    val label = stringResource(R.string.host_picker_search_hint)
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixSearchBar(
            searchStatus = SearchStatus(label, query, current = SearchStatus.Status.EXPANDED),
            onSearchStatusChange = { onQueryChange(it.searchText) },
            searchBarTopPadding = 0.dp,
        )
        UiMode.Material -> {
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            androidx.compose.material3.SearchBarDefaults.InputField(
                query = query, onQueryChange = onQueryChange,
                onSearch = { focus.clearFocus(); keyboard?.hide() },
                expanded = false, onExpandedChange = {},
                modifier = Modifier.fillMaxWidth(),
                placeholder = { androidx.compose.material3.Text(label) },
                leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Search, label) },
                trailingIcon = {
                    if (query.isNotEmpty()) androidx.compose.material3.IconButton(onClick = { onQueryChange("") }) {
                        androidx.compose.material3.Icon(Icons.Filled.Close, stringResource(R.string.host_picker_search_clear))
                    }
                },
            )
        }
    }
}

/** Only elapsed monotonic time and measured, same-process duration references. */
@Composable
internal fun HiddenFlowProgress(progress: HiddenOperationProgress?, fallbackStageRes: Int, modifier: Modifier = Modifier) {
    val fallbackStart = remember { android.os.SystemClock.elapsedRealtime() }
    val started = progress?.startedAtElapsedMs ?: fallbackStart
    var now by remember(started) { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(started) {
        while (isActive) {
            delay(1_000L)
            now = android.os.SystemClock.elapsedRealtime()
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (LocalUiMode.current) {
                UiMode.Miuix -> top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(size = 28.dp)
                UiMode.Material -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            }
            HiddenFlowText(stringResource(progress?.stageRes ?: fallbackStageRes))
        }
        val seconds = progress?.elapsedSeconds(now) ?: ((now - started).coerceAtLeast(0L) / 1_000L)
        HiddenFlowText(stringResource(R.string.hidden_operation_elapsed, seconds), secondary = true)
        val estimate = progress?.estimatedSeconds
        HiddenFlowText(
            if (estimate != null) stringResource(R.string.hidden_operation_estimated, estimate)
            else stringResource(R.string.hidden_operation_first_run), secondary = true,
        )
    }
}

@Composable
internal fun HiddenFlowDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val maxBodyHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
    val body: @Composable () -> Unit = {
        Column(Modifier.heightIn(max = maxBodyHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
    when (LocalUiMode.current) {
        UiMode.Miuix -> WindowDialog(
            show = true, title = title, backgroundColor = miuixDialogColor(),
            onDismissRequest = { if (dismissEnabled) onDismiss() },
        ) {
            body()
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HiddenFlowButton(dismissLabel, onDismiss, Modifier.weight(1f), enabled = dismissEnabled)
                HiddenFlowButton(confirmLabel, onConfirm, Modifier.weight(1f), enabled = confirmEnabled, primary = true)
            }
        }
        UiMode.Material -> AlertDialog(
            onDismissRequest = { if (dismissEnabled) onDismiss() },
            containerColor = materialDialogColor(),
            title = { HiddenFlowText(title, title = true) }, text = body,
            confirmButton = { HiddenFlowButton(confirmLabel, onConfirm, enabled = confirmEnabled, primary = true) },
            dismissButton = { HiddenFlowButton(dismissLabel, onDismiss, enabled = dismissEnabled) },
        )
    }
}
