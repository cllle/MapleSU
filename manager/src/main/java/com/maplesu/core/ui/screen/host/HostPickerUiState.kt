package com.maplesu.core.ui.screen.host

import androidx.compose.runtime.Immutable
import dev.maplesu.manager.litehidden.HiddenStepResult
import dev.maplesu.manager.litehidden.HostCandidate

/**
 * 「选择寄生应用」页的阶段。纯数据 + 纯跃迁（JVM 可测，无 Android/Compose 运行时依赖）：
 *
 * ```
 * IDLE --open--> LOADING --loaded--> LIST --select/next--> CONFIRM --confirm--> RUNNING
 *                   |                  ^                        |                   |
 *                   +--loadFailed--> FAILED                    +---backToList-----+
 *                                     RUNNING --failed--> FAILED --dismiss--> IDLE
 * ```
 */
enum class HostPickerPhase {
    /** 未展示。 */
    IDLE,

    /** 正在读取可寄生应用。 */
    LOADING,

    /** 列表就绪（可能为空）。 */
    LIST,

    /** 已选宿主，等待二次确认（显示宿主/端口/复制恢复链接）。 */
    CONFIRM,

    /** 正在执行激活事务（按钮禁用）。 */
    RUNNING,

    /** 加载或激活失败：保留原因与分步结果，可重试/返回。 */
    FAILED,
}

/** 单步展示（wire 是后端步骤名；ok/fail 独立，不合并）。 */
@Immutable
data class HostStepUi(
    val wire: String,
    val ok: Boolean,
    val code: String = "",
    /**
     * 后端诊断细节（例如 `BACKUP_SHA:libabsl.cr.so` / `unpatched=libx.so`）。
     * 只用于本页小字展示，不做文案判定、不进日志敏感区。
     */
    val detail: String = "",
)

/** Monotonic UI timing; estimates only come from a completed matching operation. */
@Immutable
data class HiddenOperationProgress(
    val startedAtElapsedMs: Long,
    val stageRes: Int,
    val estimatedDurationMs: Long? = null,
) {
    fun elapsedSeconds(nowElapsedMs: Long): Long =
        (nowElapsedMs - startedAtElapsedMs).coerceAtLeast(0L) / 1_000L

    val estimatedSeconds: Long? get() = estimatedDurationMs?.takeIf { it > 0L }
        ?.let { (it / 1_000L + if (it % 1_000L == 0L) 0L else 1L).coerceAtLeast(1L) }
}

/** 选择寄生应用页的 UI 状态（只读快照；选中项用包名标识，避免对象引用漂移）。 */
@Immutable
data class HostPickerUiState(
    val visible: Boolean = false,
    val phase: HostPickerPhase = HostPickerPhase.IDLE,
    val candidates: List<HostCandidate> = emptyList(),
    val selected: String? = null,
    val searchQuery: String = "",
    /** 打开时快照的端口文本（确认页与实际激活都用它，避免中途改端口产生不一致）。 */
    val portText: String = "18427",
    val portValid: Boolean = true,
    val failureCode: String = "",
    val failureRes: Int? = null,
    val steps: List<HostStepUi> = emptyList(),
    val progress: HiddenOperationProgress? = null,
) {
    val selectedCandidate: HostCandidate? get() = candidates.firstOrNull { it.packageName == selected }

    val filteredCandidates: List<HostCandidate> get() {
        val query = searchQuery.trim()
        return if (query.isEmpty()) candidates else candidates.filter {
            it.displayName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }

    /** 已选中一个仍存在于列表里的宿主。 */
    val hasSelection: Boolean get() = selectedCandidate != null

    val isEmpty: Boolean get() = phase == HostPickerPhase.LIST && candidates.isEmpty()

    val isLoading: Boolean get() = phase == HostPickerPhase.LOADING

    val isConfirming: Boolean get() = phase == HostPickerPhase.CONFIRM

    val isRunning: Boolean get() = phase == HostPickerPhase.RUNNING

    /** 未选择时「下一步」必须禁用。 */
    val canGoNext: Boolean get() = phase == HostPickerPhase.LIST && hasSelection

    /** 二次确认按钮可用性：必须已选宿主 + 端口合法。 */
    val canConfirm: Boolean get() = phase == HostPickerPhase.CONFIRM && hasSelection && portValid

    /** 选中的宿主展示名（未选为空串，不伪造）。 */
    val selectedDisplayName: String get() = selectedCandidate?.displayName.orEmpty()

    val selectedPackage: String get() = selectedCandidate?.packageName.orEmpty()
}

/** 页内动作（全部由 VM 提供；Compose 只做转发）。 */
@Immutable
data class HostPickerActions(
    val onSelect: (String) -> Unit = {},
    val onSearchQueryChange: (String) -> Unit = {},
    val onNext: () -> Unit = {},
    val onBackToList: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onCopyRecoveryLink: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
)

/** 解除寄生的 UI 状态（三步结果分别展示）。 */
@Immutable
data class DeactivateUiState(
    val visible: Boolean = false,
    val running: Boolean = false,
    val hostPackage: String = "",
    val steps: List<HostStepUi> = emptyList(),
    val failureCode: String = "",
    val failureRes: Int? = null,
    /** 成功提示（null = 还没成功）。 */
    val doneRes: Int? = null,
    val progress: HiddenOperationProgress? = null,
) {
    val showSteps: Boolean get() = steps.isNotEmpty()
}

/**
 * 选择寄生应用页 / 解除寄生的纯状态跃迁。**所有**分支都在这里决定，
 * Compose 与 ViewModel 只调用这些函数（保证 UI 与测试看同一套规则）。
 */
object HostPickerPolicy {

    fun initial(): HostPickerUiState = HostPickerUiState()

    /** 打开选择页：清空上次选择与失败信息，端口按当前设置快照。 */
    fun open(prev: HostPickerUiState, portText: String, portValid: Boolean): HostPickerUiState = prev.copy(
        visible = true,
        phase = HostPickerPhase.LOADING,
        candidates = emptyList(),
        selected = null,
        searchQuery = "",
        portText = portText,
        portValid = portValid,
        failureCode = "",
        failureRes = null,
        steps = emptyList(),
        progress = null,
    )

    /** 列表就绪：保留仍存在的旧选择，丢弃已消失的宿主。 */
    fun loaded(prev: HostPickerUiState, candidates: List<HostCandidate>): HostPickerUiState {
        val kept = prev.selected?.takeIf { selected -> candidates.any { it.packageName == selected } }
        return prev.copy(
            phase = HostPickerPhase.LIST,
            candidates = candidates,
            selected = kept,
            failureCode = "",
            failureRes = null,
            progress = null,
        )
    }

    /** 扫描失败：列表为空 + 原因（不假装"没有可用应用"）。 */
    fun loadFailed(prev: HostPickerUiState, failureCode: String, failureRes: Int): HostPickerUiState = prev.copy(
        phase = HostPickerPhase.FAILED,
        candidates = emptyList(),
        selected = null,
        failureCode = failureCode,
        failureRes = failureRes,
        steps = emptyList(),
        progress = null,
    )

    /** 选中列表内的宿主；列表外的包名一律忽略（防伪造）。 */
    fun select(prev: HostPickerUiState, packageName: String): HostPickerUiState {
        if (prev.phase != HostPickerPhase.LIST) return prev
        if (prev.candidates.none { it.packageName == packageName }) return prev
        return prev.copy(selected = packageName)
    }

    fun search(prev: HostPickerUiState, query: String): HostPickerUiState =
        if (prev.phase == HostPickerPhase.LIST) prev.copy(searchQuery = query) else prev

    /** 下一步：必须已选宿主，进入二次确认。 */
    fun next(prev: HostPickerUiState): HostPickerUiState =
        if (prev.canGoNext) prev.copy(phase = HostPickerPhase.CONFIRM) else prev

    /** 确认页返回列表（保留选择）。 */
    fun backToList(prev: HostPickerUiState): HostPickerUiState =
        if (prev.phase == HostPickerPhase.CONFIRM) prev.copy(phase = HostPickerPhase.LIST) else prev

    /** 二次确认通过 → 执行中（不可再次确认）。 */
    fun startRunning(prev: HostPickerUiState, progress: HiddenOperationProgress? = null): HostPickerUiState =
        if (prev.canConfirm) prev.copy(phase = HostPickerPhase.RUNNING, progress = progress) else prev

    /** 执行失败：保留分步结果与短原因。 */
    fun fail(
        prev: HostPickerUiState,
        failureCode: String,
        failureRes: Int,
        steps: List<HiddenStepResult> = emptyList(),
    ): HostPickerUiState = prev.copy(
        phase = HostPickerPhase.FAILED,
        failureCode = failureCode,
        failureRes = failureRes,
        steps = stepsOf(steps),
        progress = null,
    )

    /** 关闭：回到初始（保留端口记忆由页面状态负责）。 */
    fun dismiss(prev: HostPickerUiState): HostPickerUiState = prev.copy(
        visible = false,
        phase = HostPickerPhase.IDLE,
        failureCode = "",
        failureRes = null,
        steps = emptyList(),
        searchQuery = "",
        progress = null,
    )

    /** 后端分步结果 → UI 分步行（保持后端顺序，不排序、不合并）。 */
    fun stepsOf(results: List<HiddenStepResult>): List<HostStepUi> =
        results.map { HostStepUi(wire = it.step, ok = it.ok, code = it.code, detail = it.detail) }

    // ── 解除寄生 ──────────────────────────────────────────────────────

    fun deactivateInitial(): DeactivateUiState = DeactivateUiState()

    /** 打开解除确认（显示将要恢复的宿主）。 */
    fun deactivateOpen(prev: DeactivateUiState, hostPackage: String): DeactivateUiState = prev.copy(
        visible = true,
        running = false,
        hostPackage = hostPackage,
        steps = emptyList(),
        failureCode = "",
        failureRes = null,
        doneRes = null,
        progress = null,
    )

    fun deactivateRunning(prev: DeactivateUiState, progress: HiddenOperationProgress? = null): DeactivateUiState =
        if (prev.visible && !prev.running && prev.doneRes == null) prev.copy(
            running = true, steps = emptyList(), failureCode = "", failureRes = null, progress = progress,
        ) else prev

    /** 解除结果：成功只认 success，失败保留分步（每步 ok/fail 独立）。 */
    fun deactivateFinished(
        prev: DeactivateUiState,
        success: Boolean,
        doneRes: Int,
        failureCode: String = "",
        failureRes: Int? = null,
        steps: List<HiddenStepResult> = emptyList(),
    ): DeactivateUiState = prev.copy(
        running = false,
        progress = null,
        steps = stepsOf(steps),
        doneRes = if (success) doneRes else null,
        failureCode = if (success) "" else failureCode,
        failureRes = if (success) null else failureRes,
    )

    fun deactivateDismiss(prev: DeactivateUiState): DeactivateUiState = prev.copy(
        visible = false,
        running = false,
        failureCode = "",
        failureRes = null,
        doneRes = null,
        steps = emptyList(),
        progress = null,
    )
}
