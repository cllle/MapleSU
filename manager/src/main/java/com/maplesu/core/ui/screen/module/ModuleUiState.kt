package com.maplesu.core.ui.screen.module

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.maplesu.core.R
import me.weishu.kernelsu.data.registry.ExpectedFile
import com.maplesu.core.data.model.Module
import com.maplesu.core.data.model.ModuleUpdateInfo
import com.maplesu.core.ui.component.SearchStatus
import dev.maplesu.manager.modules.ModuleType
import dev.maplesu.manager.modules.ModuleTypeEvidence
import dev.maplesu.manager.modules.ModuleWaitReason
import dev.maplesu.manager.workmode.WorkMode
import java.util.concurrent.CancellationException

sealed interface ModuleConfirmRequest {
    data class Update(
        val module: Module,
        val downloadUrl: String,
        val fileName: String,
        /** Set when the update is a registry release: size + SHA-256 are enforced. */
        val expected: ExpectedFile? = null,
    ) : ModuleConfirmRequest

    data class Uninstall(
        val module: Module,
    ) : ModuleConfirmRequest
}

@Immutable
data class ModuleConfirmDialogState(
    val request: ModuleConfirmRequest,
    val title: String,
    val content: String? = null,
    val markdown: Boolean = false,
    val html: Boolean = false,
    /** Registry-hosted content: render with JavaScript off and network locked to the registry. */
    val restricted: Boolean = false,
    val confirm: String? = null,
    val dismiss: String? = null,
    /** 安装确认：检测到的模块类型（null = 非安装请求）。 */
    val moduleType: ModuleType? = null,
    val moduleTypeEvidence: List<String> = emptyList(),
    /** UNKNOWN / B 模式 Ultra 类型 → false（确认按钮禁用）。 */
    val moduleInstallAllowed: Boolean = true,
    /** AMBIGUOUS 时让用户选择的候选类型。 */
    val moduleTypeChoices: List<ModuleType> = emptyList(),
)

sealed interface ModuleEffect {
    data class Toast(
        val message: String,
    ) : ModuleEffect

    data class SnackBar(
        val message: String,
    ) : ModuleEffect
}

@Immutable
data class ModuleUiState(
    val isRefreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    val loadError: Throwable? = null,
    /**
     * T10：正在等待运行环境接线（"启动中"）。**不是失败**：
     * 此时不得显示 [R.string.module_load_failed]，且 [hasLoaded] 只由**终态**置 true。
     */
    val pending: Boolean = false,
    /** T10：具名等待原因（[dev.maplesu.manager.modules.ModuleWaitReason.code]，如 `MODE_UNRESOLVED`）。 */
    val pendingReason: String? = null,
    val modules: List<Module> = emptyList(),
    val moduleList: List<Module> = emptyList(),
    /** In-flight device operations; KSU removal intent is shown immediately in all lists. */
    val pendingModuleIds: Set<String> = emptySet(),
    internal val removalChanges: Map<String, ModuleRemovalChange> = emptyMap(),
    internal val toggleChanges: Map<String, ModuleToggleChange> = emptyMap(),
    val updateInfo: Map<String, ModuleUpdateInfo> = emptyMap(),
    val searchStatus: SearchStatus = SearchStatus(""),
    val searchResults: List<Module> = emptyList(),
    val sortEnabledFirst: Boolean = false,
    val sortActionFirst: Boolean = false,
    val checkModuleUpdate: Boolean = true,
    val isSafeMode: Boolean = false,
    val magiskInstalled: Boolean = false,
    /** Display only; opening WebUI still checks the current environment on IO. */
    val environmentWebUiAvailable: Boolean = false,
    val isUltra: Boolean = false,
    val modeResolved: Boolean = true,
    val isCurrentPage: Boolean = true,
    val confirmDialogState: ModuleConfirmDialogState? = null,
    /**
     * 安装类型检测结果（wm-core ModuleTypeDetector）；未接线时 UNKNOWN + 明确原因，绝不猜测。
     * UI 安装流程在拿到真实 evidence 后覆盖此字段。
     */
    val pendingModuleTypeEvidence: ModuleTypeEvidence = ModuleTypeEvidence(
        ModuleType.UNKNOWN, listOf("no-module-selected"),
    ),
) {
    val recoveryOnly: Boolean get() = isUltra && isSafeMode

    val installButtonVisible: Boolean
        get() = modeResolved && !(isSafeMode || magiskInstalled)

    val emptyStateMessage: Int?
        get() = when {
            modules.isNotEmpty() || moduleList.isNotEmpty() || isRefreshing -> null
            // T10：等待接线 → "正在确认运行环境"，绝不是"加载失败"。
            pending -> R.string.module_environment_pending
            loadError != null -> R.string.module_load_failed
            hasLoaded -> R.string.module_empty
            else -> null
        }
}

/** Ultra transitions never replace the independent KSU safety observation. */
internal fun ModuleUiState.withUltraSafety(safeMode: Boolean, mode: WorkMode?): ModuleUiState =
    if (mode == WorkMode.KSU) this else copy(isSafeMode = safeMode)

internal fun ModuleUiState.withModuleLoadResult(result: Result<List<Module>>): ModuleUiState =
    result.fold(
        onSuccess = {
            val previous = modules.associateBy { module -> module.id }
            val received = it.map { module ->
                val toggled = toggleChanges[module.id]?.present(module) ?: module
                val presented = removalChanges[module.id]?.present(toggled) ?: toggled
                previous[module.id]?.takeIf { old -> old == presented } ?: presented
            }
            copy(
                modules = if (received == modules) modules else received,
                loadError = null,
                hasLoaded = true,
                pending = false,
                pendingReason = null,
            )
        },
        onFailure = {
            if (it is CancellationException) throw it
            // T10：走到这里就是**终态**失败，具名 code 在异常消息里
            // （`MODE_UNRESOLVED` / `BACKEND_UNAVAILABLE: …`）。等待期不得进入本分支。
            copy(
                loadError = it,
                hasLoaded = true,
                pending = false,
                pendingReason = null,
            )
        },
    )

/** Rows and update hints belong to one repository read identity, never to its successor. */
internal fun ModuleUiState.withModuleReadContextReset(): ModuleUiState = copy(
    modules = emptyList(),
    moduleList = emptyList(),
    searchResults = emptyList(),
    updateInfo = emptyMap(),
    removalChanges = emptyMap(),
    toggleChanges = emptyMap(),
    hasLoaded = false,
    loadError = null,
    pending = false,
    pendingReason = null,
    isRefreshing = true,
)

/**
 * T10：置"启动中"（等待运行环境接线）。
 *
 * **不置 loadError、不置 hasLoaded**，也不用空列表伪装成功——`hasLoaded` 只在终态为 true。
 */
internal fun ModuleUiState.withModulePending(reason: ModuleWaitReason): ModuleUiState = copy(
    pending = true,
    pendingReason = reason.code,
    loadError = null,
    hasLoaded = false,
    isRefreshing = false,
)

internal fun ModuleUiState.moduleListInputs(): ModuleListInputs<Module> = ModuleListInputs(
    modules, moduleList, searchStatus.searchText, sortEnabledFirst, sortActionFirst,
)

@Immutable
data class ModuleActions(
    val onRefresh: () -> Unit,
    val onSearchStatusChange: (SearchStatus) -> Unit,
    val onSearchTextChange: (String) -> Unit,
    val onClearSearch: () -> Unit,
    val onRequestUpdateConfirmation: (Module, ModuleUpdateInfo) -> Unit,
    val onRequestUninstallConfirmation: (Module) -> Unit,
    val onDismissConfirmRequest: () -> Unit,
    val onConfirmUpdate: (ModuleConfirmRequest.Update) -> Unit,
    val onOpenRepo: () -> Unit,
    val onToggleSortActionFirst: () -> Unit,
    val onToggleSortEnabledFirst: () -> Unit,
    val onOpenWebUi: (Module) -> Unit,
    val onToggleModule: (Module) -> Unit,
    val onUninstallModule: (Module) -> Unit,
    val onUndoUninstallModule: (Module) -> Unit,
    val onOpenFlash: (List<Uri>) -> Unit,
    val onExecuteModuleAction: (Module) -> Unit,
)
