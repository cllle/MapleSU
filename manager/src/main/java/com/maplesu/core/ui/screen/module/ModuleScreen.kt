package com.maplesu.core.ui.screen.module

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import com.maplesu.core.R
import com.maplesu.core.data.model.Module
import com.maplesu.core.ui.LocalUiMode
import com.maplesu.core.ui.UiMode
import com.maplesu.core.ui.component.dialog.rememberConfirmDialog
import com.maplesu.core.ui.component.SearchStatus
import com.maplesu.core.ui.navigation3.LocalNavigator
import com.maplesu.core.ui.navigation3.Route
import com.maplesu.core.ui.navigation3.WebUiOpenOrigin
import com.maplesu.core.ui.screen.flash.FlashIt
import com.maplesu.core.ui.screen.modulerepo.downloadFailureMessage
import com.maplesu.core.ui.util.download
import com.maplesu.core.ui.util.module.Shortcut
import com.maplesu.core.ui.viewmodel.ModuleViewModel
import com.maplesu.core.ui.webui.WebUIActivity
import dev.maplesu.manager.modules.ModuleWebUiLauncher
import dev.maplesu.manager.modules.webui.WebUiFailure
import dev.maplesu.manager.workmode.ModeRuntime
import dev.maplesu.manager.workmode.WorkMode

@Composable
fun ModulePager(
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val uiMode = LocalUiMode.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val resource = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel = viewModel<ModuleViewModel>()
    val scope = rememberCoroutineScope()
    val rawUiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Activation happens once per process; suspend without blocking UI until its exact mode is known.
    val activeMode by produceState(ModeRuntime.active) {
        value = ModeRuntime.active ?: ModeRuntime.awaitActivated(Long.MAX_VALUE)
    }
    // Keep list position when the skin changes; the two native layouts reuse these states.
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()

    val webUILauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshAfterReturn() }

    // Request notification permission for download progress notifications
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Download works regardless of result */ }
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)
    val initialResumeHandled = rememberSaveable { mutableStateOf(false) }

    var hasActivated by rememberSaveable { mutableStateOf(false) }

    // WebUI 解析失败（SDK 拒绝 / 端口没起来 / 非 HTTP / 代理不可用）：用对话框说明原因，
    // 不用 Toast —— Toast 一闪而过就等同于「点了没反应」。
    // Keep the list interactive while a module starts, and merge clicks per module.
    val openingWebUi = remember { mutableStateMapOf<String, Job>() }
    var webUiFailure by remember { mutableStateOf<WebUiFailure?>(null) }
    var webUiRetryModuleId by remember { mutableStateOf<String?>(null) }
    fun openEnvironmentWebUi(moduleId: String, moduleName: String = "", moduleInfo: Module? = null) {
        if (!isCurrentPage || moduleId in openingWebUi) return
        val openJob = scope.launch(start = CoroutineStart.LAZY) {
            val failure = ModuleWebUiLauncher.openDetailed(context, moduleId, WebUiOpenOrigin.USER_CLICK, moduleName,
                moduleInfo = moduleInfo)
            if (failure != null) {
                webUiRetryModuleId = moduleId
                webUiFailure = failure
            }
        }
        openingWebUi[moduleId] = openJob
        openJob.invokeOnCompletion { openingWebUi.remove(moduleId, openJob) }
        openJob.start()
    }
    DisposableEffect(isCurrentPage) {
        onDispose { openingWebUi.values.toList().forEach { it.cancel() } }
    }
    val webUiErrorDialog = rememberConfirmDialog(
        onConfirm = {
            val retryId = webUiRetryModuleId
            webUiFailure = null
            if (retryId != null) {
                val currentModule = viewModel.uiState.value.modules.firstOrNull { it.id == retryId }
                openEnvironmentWebUi(retryId, currentModule?.name.orEmpty(), currentModule)
            }
        },
        onDismiss = { webUiFailure = null },
    )
    LaunchedEffect(webUiFailure) {
        webUiFailure?.let { failure ->
            webUiErrorDialog.showConfirm(
                title = resource.getString(R.string.module_webui_error_title),
                content = buildString {
                    append(resource.getString(R.string.module_webui_error_reason,
                        failure.code))
                    if (failure.detail.isNotBlank()) append("\n")
                        .append(failure.detail)
                },
                confirm = resource.getString(R.string.module_webui_retry),
                dismiss = resource.getString(R.string.module_webui_close),
            )
        }
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.setCurrentPage(false) }
    }
    LaunchedEffect(isCurrentPage, activeMode) {
        viewModel.setCurrentPage(isCurrentPage)
        if (isCurrentPage) {
            if (!hasActivated) {
                hasActivated = true
                viewModel.refreshEnvironmentState()
                viewModel.initializePreferences()
                val state = viewModel.uiState.value
                if (!state.hasLoaded && !state.isRefreshing) {
                    viewModel.fetchModuleList()
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else if (activeMode != null) {
                val state = viewModel.uiState.value
                // Supersede a request that started unresolved, even if it is still returning.
                if (!state.hasLoaded ||
                    state.loadError is com.maplesu.core.data.repository.ModuleModeUnresolvedException) {
                    viewModel.fetchModuleList()
                }
            }
        } else if (!rawUiState.searchStatus.isCollapsed()) {
            viewModel.updateSearchStatus(rawUiState.searchStatus.copy(searchText = "", current = SearchStatus.Status.COLLAPSED))
        }
    }

    LifecycleResumeEffect(Unit) {
        if (initialResumeHandled.value && latestIsCurrentPage) {
            viewModel.refreshAfterReturn()
        }
        initialResumeHandled.value = true
        onPauseOrDispose {}
    }

    val actions = ModuleActions(
        onRefresh = {
            viewModel.fetchModuleList(checkUpdate = true, resort = false, coalesce = true)
        },
        onSearchStatusChange = {
            viewModel.updateSearchStatus(it)
        },
        onSearchTextChange = { text ->
            viewModel.updateSearchText(text)
        },
        onClearSearch = {
            viewModel.updateSearchText("")
        },
        onRequestUpdateConfirmation = { module, updateInfo ->
            viewModel.requestUpdateConfirmation(module, updateInfo)
        },
        onRequestUninstallConfirmation = { module ->
            viewModel.requestUninstallConfirmation(module)
        },
        onDismissConfirmRequest = {
            viewModel.dismissConfirmRequest()
        },
        onConfirmUpdate = { request ->
            scope.launch {
                download(
                    url = request.downloadUrl,
                    fileName = request.fileName,
                    // Registry releases are size + SHA-256 checked, kept app-private and
                    // re-verified by the flash step; other updateJson sources are unchanged.
                    expected = request.expected,
                    onFailed = { failed ->
                        viewModel.emitEffect(
                            ModuleEffect.Toast(downloadFailureMessage(context, failed, request.fileName))
                        )
                    },
                    onDownloaded = { uri ->
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            navigator.push(Route.Flash(FlashIt.FlashModules(listOf(uri), dev.maplesu.manager.modules.compat.UltraModuleAction.UPDATE)))
                            viewModel.markNeedRefresh()
                        }
                    },
                    onDownloading = {
                        viewModel.emitEffect(
                            ModuleEffect.Toast(
                                resource.getString(R.string.module_downloading).format(request.module.name)
                            )
                        )
                    },
                )
            }
            viewModel.dismissConfirmRequest()
        },
        onOpenRepo = {
            when (activeMode) {
                WorkMode.LITE -> navigator.push(Route.MapleMarket)
                WorkMode.KSU -> navigator.push(Route.ModuleRepo)
                null -> viewModel.emitEffect(ModuleEffect.Toast(resource.getString(R.string.maple_market_confirming_environment)))
            }
        },
        onToggleSortActionFirst = {
            viewModel.toggleSortActionFirst()
        },
        onToggleSortEnabledFirst = {
            viewModel.toggleSortEnabledFirst()
        },
        onOpenWebUi = { module ->
            viewModel.markNeedRefresh()
            if (ModuleWebUiLauncher.isUltraModuleId(module.id)) {
                openEnvironmentWebUi(module.id, module.name, module)
            } else {
                webUILauncher.launch(
                    Intent(context, WebUIActivity::class.java)
                        .setData(
                            Shortcut.buildShortcutUri(module.id, ShortcutType.WebUI)
                        )
                )
            }
        },
        onToggleModule = { module ->
            viewModel.toggleModule(module)
        },
        onUninstallModule = { module ->
            viewModel.uninstallModule(module)
        },
        onUndoUninstallModule = { module ->
            viewModel.undoUninstallModule(module)
        },
        onOpenFlash = { uris ->
            if (uris.isNotEmpty()) {
                navigator.push(Route.Flash(FlashIt.FlashModules(uris)))
                viewModel.markNeedRefresh()
            }
        },
        onExecuteModuleAction = { module ->
            navigator.push(Route.ExecuteModuleAction(module.id))
            viewModel.markNeedRefresh()
        },
    )

    when (uiMode) {
        UiMode.Miuix -> ModulePagerMiuix(
            uiState = rawUiState.copy(isUltra = activeMode == WorkMode.LITE, modeResolved = activeMode != null,
                isCurrentPage = isCurrentPage, pendingModuleIds = rawUiState.pendingModuleIds + openingWebUi.keys),
            confirmDialogState = rawUiState.confirmDialogState,
            moduleEvent = viewModel.moduleEvent,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
            listState = listState,
            searchListState = searchListState,
        )

        UiMode.Material -> ModulePagerMaterial(
            uiState = rawUiState.copy(isUltra = activeMode == WorkMode.LITE, modeResolved = activeMode != null,
                isCurrentPage = isCurrentPage, pendingModuleIds = rawUiState.pendingModuleIds + openingWebUi.keys),
            confirmDialogState = rawUiState.confirmDialogState,
            moduleEvent = viewModel.moduleEvent,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
            listState = listState,
            searchListState = searchListState,
        )
    }
}
