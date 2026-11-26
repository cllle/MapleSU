package dev.maplesu.manager.workmode.ui

import android.content.Context
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import dev.maplesu.manager.liteweb.LiteWebAdminController
import dev.maplesu.manager.liteweb.RecoveryWebUiControl
import dev.maplesu.manager.liteweb.RecoveryWebUiState
import dev.maplesu.manager.liteweb.UltraEnvLiteWebDaemon
import dev.maplesu.manager.litehidden.HttpHiddenDaemonProbe
import dev.maplesu.manager.runtime.RootBackend
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maplesu.core.R
import com.maplesu.core.ksuApp
import com.maplesu.core.ui.screen.host.HostPickerPolicy
import com.maplesu.core.ui.screen.host.HiddenOperationProgress
import dev.maplesu.manager.litehidden.HiddenCardPhase
import dev.maplesu.manager.litehidden.HiddenFailure
import dev.maplesu.manager.litehidden.HiddenModePolicy
import dev.maplesu.manager.litehidden.LiteHiddenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * 独立隐藏设置页的 IO/命令驱动（仅 LITE；一个主按钮 + 宿主选择）。
 *
 * 流程：点「开启隐藏模式」→ 打开「选择寄生应用」→ 选宿主 → 二次确认（宿主/端口/复制链接）
 * → 激活事务 → 分步回显。失败一律 fail-closed（保持未隐藏 + 显示具体原因），不假装成功。
 * 解除寄生：恢复宿主 so / 撤销 su_auth / 停 daemon 三步结果分别展示。
 */
class LiteHiddenViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LiteHiddenPageUi())
    val uiState: StateFlow<LiteHiddenPageUi> = _uiState.asStateFlow()

    /** WebUI token 只在内存（用于预览链接；不写日志/prefs）。 */
    private var webUiToken: String? = null

    /** 宿主图标缓存（内存；取不到就只显示名称/包名）。 */
    private val hostIcons = mutableMapOf<String, ImageBitmap>()

    private var hostLoadJob: Job? = null
    private var hostRequest = 0L
    private var refreshJob: Job? = null
    private var webUiStatusJob: Job? = null
    private var webUiStatusRevision = 0L
    private val completedOperationDurations = mutableMapOf<String, Long>()
    private val webUiControl = RecoveryWebUiControl {
        dev.maplesu.manager.runtime.RootExec.execute(ksuApp, it)
    }

    init {
        refresh()
    }

    private fun beginOperation(key: String, stageRes: Int) = HiddenOperationProgress(
        SystemClock.elapsedRealtime(), stageRes, completedOperationDurations[key],
    )

    private fun rememberCompletedOperation(key: String, progress: HiddenOperationProgress?) {
        if (progress == null) return
        val elapsed = SystemClock.elapsedRealtime() - progress.startedAtElapsedMs
        if (elapsed > 0L) completedOperationDurations[key] = elapsed
    }

    fun refresh() {
        val card = _uiState.value
        if (refreshJob?.isActive == true || card.hostPicker.visible || card.deactivate.visible ||
            card.preparingLink || card.stoppingWebUi || card.confirmStopWebUi) return
        _uiState.update { it.copy(checkingState = true,
            statusRes = R.string.ui_polish_hidden_reading) }
        refreshJob = viewModelScope.launch {
            val revision = webUiStatusRevision
            try {
                webUiStatusJob?.join()
                val mode = dev.maplesu.manager.workmode.ModeRuntime.active
                    ?: dev.maplesu.manager.workmode.ModeRuntime.awaitActivated(5_000L)
                val snapshot = withContext(Dispatchers.IO) {
                    runCatching {
                        val state = when (mode) {
                            dev.maplesu.manager.workmode.WorkMode.LITE ->
                                LiteHiddenRuntimeProxy.readState()

                            // B 也一样：读不到就说"未知"，不要用 isEnabled()（读失败 → false）冒充"未开启"。
                            dev.maplesu.manager.workmode.WorkMode.KSU -> when (
                                runCatching { com.maplesu.core.ui.security.Stealth.state() }.getOrNull()
                            ) {
                                com.maplesu.core.ui.security.Stealth.State.ON -> LiteHiddenState.ON
                                com.maplesu.core.ui.security.Stealth.State.OFF -> LiteHiddenState.OFF
                                else -> LiteHiddenState.UNKNOWN
                            }

                            null -> LiteHiddenState.UNKNOWN
                        }
                        HiddenPageSnapshot(
                            state = state,
                            port = if (mode == dev.maplesu.manager.workmode.WorkMode.LITE) LiteHiddenRuntimeProxy.preferredPort() else 18427,
                            cachedUrl = if (mode == dev.maplesu.manager.workmode.WorkMode.LITE && state != LiteHiddenState.UNKNOWN)
                                LiteHiddenRuntimeProxy.webUiRecoveryLink() else null,
                            activeHost = if (mode == dev.maplesu.manager.workmode.WorkMode.LITE) LiteHiddenRuntimeProxy.activeHostPackage() else "",
                        )
                    }.getOrElse { error ->
                        if (error is CancellationException) throw error
                        HiddenPageSnapshot(LiteHiddenState.UNKNOWN, 18427, null, "")
                    }
                }
                if (revision != webUiStatusRevision) return@launch
                val ui = _uiState.value
                if (ui.hostPicker.visible || ui.deactivate.visible || ui.preparingLink ||
                    ui.stoppingWebUi) return@launch
                if (!ui.webUiLinkAvailable) webUiToken = tokenOf(snapshot.cachedUrl)
                val status = LiteHiddenStatusPolicy.status(snapshot.state)
                _uiState.update {
                    it.copy(
                        // 事实陈述：只有明确 ON 才显示"已开启"；UNKNOWN 显示"状态未知（读取失败）"。
                        status = status,
                        statusRes = LiteHiddenStatusPolicy.statusRes(snapshot.state),
                        // 只有明确 ON 才让整页消失；UNKNOWN 留页并写清"读取失败"（否则用户永远看不到原因）。
                        hidden = status == LiteHiddenStatus.ON,
                        phase = HiddenCardPhase.UNHIDDEN,
                        portText = if (it.webUiLinkAvailable) it.portText else snapshot.port.toString(),
                        portValid = if (it.webUiLinkAvailable) it.portValid else HiddenModePolicy.validatePort(snapshot.port.toString()).valid,
                        webUiLink = if (it.webUiLinkAvailable) it.webUiLink else previewLinkFor(snapshot.port.toString()),
                        failureRes = null,
                        activeHost = snapshot.activeHost,
                    )
                }
            } finally {
                _uiState.update { it.copy(checkingState = false) }
                // The listener uses the same serialized root channel. Read it after state.
                if (isActive && revision == webUiStatusRevision) refreshWebUiStatus()
            }
        }
    }

    /** Read the listener itself; the legacy RootService is a different process. */
    fun refreshWebUiStatus() {
        val ui = _uiState.value
        if (webUiStatusJob?.isActive == true || ui.checkingState || ui.stoppingWebUi || ui.preparingLink ||
            ui.hostPicker.isRunning || ui.deactivate.running) return
        val revision = webUiStatusRevision
        _uiState.update { it.copy(checkingWebUi = true) }
        webUiStatusJob = viewModelScope.launch {
            try {
                val status = withContext(Dispatchers.IO) { webUiControl.status() }
                if (revision != webUiStatusRevision) return@launch
                _uiState.update {
                    val running = status.state == RecoveryWebUiState.RUNNING
                    it.copy(webUiState = status.state, checkingWebUi = false,
                        webUiLinkAvailable = it.webUiLinkAvailable && running,
                        recoveryLinkSaved = it.recoveryLinkSaved && running,
                        confirmStopWebUi = it.confirmStopWebUi && running)
                }
            } finally {
                if (revision == webUiStatusRevision) _uiState.update { it.copy(checkingWebUi = false) }
            }
        }
    }

    private fun invalidateWebUiStatusRead() {
        webUiStatusRevision++
        webUiStatusJob?.cancel()
        webUiStatusJob = null
    }

    fun requestStopWebUi() {
        val ui = _uiState.value
        if (ui.webUiState != RecoveryWebUiState.RUNNING || ui.checkingWebUi || ui.stoppingWebUi ||
            ui.preparingLink || ui.hostPicker.isRunning || ui.deactivate.running) return
        _uiState.update { it.copy(confirmStopWebUi = true, webUiStopFailed = false) }
    }

    fun dismissStopWebUi() {
        if (_uiState.value.stoppingWebUi) return
        _uiState.update { it.copy(confirmStopWebUi = false) }
    }

    fun confirmStopWebUi() {
        val ui = _uiState.value
        if (!ui.confirmStopWebUi || ui.webUiState != RecoveryWebUiState.RUNNING ||
            ui.checkingWebUi || ui.stoppingWebUi || ui.preparingLink ||
            ui.hostPicker.isRunning || ui.deactivate.running) return
        invalidateWebUiStatusRead()
        _uiState.update { it.copy(stoppingWebUi = true, checkingWebUi = false, webUiStopFailed = false,
            webUiStopProgress = beginOperation("stop-webui", R.string.webui_control_stopping),
            webUiState = RecoveryWebUiState.UNKNOWN,
            webUiLinkAvailable = false, recoveryLinkSaved = false) }
        viewModelScope.launch {
            try {
                val status = withContext(Dispatchers.IO) { webUiControl.stop() }
                val stopped = status.state == RecoveryWebUiState.STOPPED
                if (stopped) rememberCompletedOperation("stop-webui", _uiState.value.webUiStopProgress)
                _uiState.update {
                    it.copy(stoppingWebUi = false, confirmStopWebUi = false, webUiState = status.state,
                        webUiStopFailed = !stopped, webUiLinkAvailable = false, recoveryLinkSaved = false)
                }
            } finally {
                _uiState.update { it.copy(stoppingWebUi = false, confirmStopWebUi = false, webUiStopProgress = null) }
            }
        }
    }

    /** 隐藏页刷新结果（状态三态 + 端口 + 缓存的恢复链接）。 */
    private data class HiddenPageSnapshot(
        val state: LiteHiddenState,
        val port: Int,
        val cachedUrl: String?,
        val activeHost: String,
    )

    // ── 开启：选择寄生应用 ────────────────────────────────────────────────

    /** 主按钮：先做与策略一致的裁决，通过后打开宿主选择页（不直接激活）。 */
    fun onClickEnable() {
        val card = _uiState.value
        val input = HiddenModePolicy.validatePort(card.portText)
        if (!card.enableButtonEnabled || card.stoppingWebUi || card.preparingLink ||
            card.confirmStopWebUi || card.deactivate.visible || !input.valid || card.hostPicker.visible) return
        _uiState.update {
            it.copy(phase = HiddenCardPhase.UNHIDDEN, failureRes = null,
                hostPicker = HostPickerPolicy.open(it.hostPicker, card.portText, input.valid)
                    .copy(progress = beginOperation("load-hosts", R.string.host_picker_loading)))
        }
        loadHosts()
    }

    fun onSelectHost(packageName: String) {
        _uiState.update { it.copy(hostPicker = HostPickerPolicy.select(it.hostPicker, packageName)) }
    }

    fun onHostSearchQueryChange(query: String) {
        _uiState.update { it.copy(hostPicker = HostPickerPolicy.search(it.hostPicker, query)) }
    }

    /** 「下一步」：未选宿主时无效果（按钮本身也禁用）。 */
    fun onHostNext() {
        _uiState.update { it.copy(hostPicker = HostPickerPolicy.next(it.hostPicker)) }
        if (_uiState.value.hostPicker.isConfirming && !_uiState.value.webUiLinkAvailable) prepareRecoveryLink()
    }

    fun onHostBackToList() {
        _uiState.update { it.copy(hostPicker = HostPickerPolicy.backToList(it.hostPicker)) }
    }

    fun onDismissHostPicker() {
        if (_uiState.value.hostPicker.isRunning) return
        hostRequest++
        hostLoadJob?.cancel()
        _uiState.update {
            it.copy(hostPicker = HostPickerPolicy.dismiss(it.hostPicker), phase = HiddenCardPhase.UNHIDDEN)
        }
    }

    fun onRetryHostLoad() {
        if (!_uiState.value.hostPicker.visible) return
        _uiState.update {
            it.copy(hostPicker = HostPickerPolicy.open(it.hostPicker, it.portText, it.portValid)
                .copy(progress = beginOperation("load-hosts", R.string.host_picker_loading)))
        }
        loadHosts()
    }

    /** 宿主图标（未加载到 → null，UI 回退为名称首字）。 */
    fun hostIcon(packageName: String): ImageBitmap? = hostIcons[packageName]

    private fun loadHosts() {
        val request = ++hostRequest
        hostLoadJob?.cancel()
        hostLoadJob = viewModelScope.launch {
            val card = _uiState.value
            val (result, blocker) = withContext(Dispatchers.IO) {
                val blocked = runCatching { LiteHiddenRuntimeProxy.canEnable(HiddenModePolicy.validatePort(card.portText)) }
                    .getOrDefault(HiddenFailure.UNKNOWN)
                val hosts = if (blocked != null) null else runCatching { LiteHiddenRuntimeProxy.hostCandidates(context()) }.getOrNull()
                hosts to blocked
            }
            if (request != hostRequest || !_uiState.value.hostPicker.visible) return@launch
            if (result == null || !result.ok) {
                val failure = blocker ?: result?.failure ?: HiddenFailure.HOST_SCAN_FAILED
                _uiState.update { it.copy(hostPicker = HostPickerPolicy.loadFailed(it.hostPicker,
                    failure.wire, LiteHiddenRuntimeProxy.failureMessageRes(failure))) }
                return@launch
            }
            hostIcons.clear()
            hostIcons.putAll(result.icons)
            rememberCompletedOperation("load-hosts", _uiState.value.hostPicker.progress)
            _uiState.update { it.copy(hostPicker = HostPickerPolicy.loaded(it.hostPicker, result.candidates)) }
        }
    }

    // ── 二次确认 → 激活事务 ───────────────────────────────────────────────

    /** 二次确认：未选宿主/端口非法 → 无效果（按钮同样禁用）。 */
    fun onConfirmActivate() {
        val card = _uiState.value
        val picker = card.hostPicker
        if (!card.canActivate || card.stoppingWebUi || card.confirmStopWebUi || card.deactivate.visible) return
        val host = picker.selectedPackage
        val port = HiddenModePolicy.validatePort(picker.portText).value
        if (port == null) {
            val res = LiteHiddenRuntimeProxy.failureMessageRes(HiddenFailure.INVALID_PORT)
            _uiState.update {
                it.copy(
                    phase = HiddenCardPhase.FAILED,
                    portValid = false,
                    failureRes = res,
                    hostPicker = HostPickerPolicy.fail(it.hostPicker, HiddenFailure.INVALID_PORT.wire, res),
                )
            }
            return
        }
        invalidateWebUiStatusRead()
        _uiState.update {
            it.copy(
                phase = HiddenCardPhase.ENABLING,
                failureRes = null,
                checkingWebUi = false,
                hostPicker = HostPickerPolicy.startRunning(it.hostPicker,
                    beginOperation("activate:$host", R.string.lite_hidden_step_webui_ping)),
            )
        }
        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) {
                val token = tokenOf(card.webUiLink)
                runCatching { token != null && HttpHiddenDaemonProbe().ping(port, token) }
                    .onFailure { if (it is CancellationException) throw it }.getOrDefault(false)
            }
            val result = if (ready) {
                _uiState.update { it.copy(hostPicker = it.hostPicker.copy(
                    progress = it.hostPicker.progress?.copy(stageRes = R.string.host_picker_running))) }
                withContext(Dispatchers.IO) {
                    runCatching { LiteHiddenRuntimeProxy.activate(host, port) }
                        .onFailure { if (it is CancellationException) throw it }.getOrNull()
                }
            } else null
            if (result != null && result.success) {
                rememberCompletedOperation("activate:$host", _uiState.value.hostPicker.progress)
                tokenOf(result.recoveryUrl)?.let { webUiToken = it }
                Toast.makeText(ksuApp, R.string.lite_hidden_enabled, Toast.LENGTH_LONG).show()
                _uiState.update {
                    it.copy(
                        status = LiteHiddenStatus.ON,
                        hidden = true,
                        statusRes = R.string.lite_hidden_status_on,
                        phase = HiddenCardPhase.UNHIDDEN,
                        activeHost = host,
                        webUiLink = result.recoveryUrl.takeIf { url -> url.isNotBlank() }
                            ?: previewLinkFor(it.portText),
                        hostPicker = HostPickerPolicy.dismiss(it.hostPicker),
                    )
                }
                return@launch
            }
            val failure = result?.failure ?: HiddenFailure.UNKNOWN
            val res = LiteHiddenRuntimeProxy.failureMessageRes(failure)
            Toast.makeText(ksuApp, res, Toast.LENGTH_LONG).show()
            // 失败 ≠ 已知关闭：按运行时真实状态写文案（UNKNOWN 就说"状态未知"，不冒充"未开启"）。
            val failedState = LiteHiddenRuntimeProxy.currentState()
            val failedStatus = LiteHiddenStatusPolicy.status(failedState)
            val failedHost = withContext(Dispatchers.IO) { LiteHiddenRuntimeProxy.activeHostPackage() }
            _uiState.update {
                it.copy(
                    phase = HiddenCardPhase.FAILED,
                    status = failedStatus,
                    hidden = failedStatus == LiteHiddenStatus.ON,
                    statusRes = LiteHiddenStatusPolicy.statusRes(failedState),
                    failureRes = res,
                    activeHost = failedHost,
                    webUiLink = result?.recoveryUrl?.takeIf { url -> url.isNotBlank() } ?: it.webUiLink,
                    webUiLinkAvailable = result?.recoveryUrl?.isNotBlank() == true,
                    recoveryLinkSaved = result?.recoveryUrl?.isNotBlank() == true && it.recoveryLinkSaved,
                    linkFailure = true,
                    hostPicker = HostPickerPolicy.fail(it.hostPicker, failure.wire, res, result?.steps.orEmpty()),
                )
            }
            refreshWebUiStatus()
        }
    }

    // ── 解除寄生 ──────────────────────────────────────────────────────────

    fun onDeactivateClick() {
        val card = _uiState.value
        val host = card.activeHost
        if (host.isBlank() || card.preparingLink || card.stoppingWebUi || card.confirmStopWebUi ||
            card.hostPicker.isRunning || card.deactivate.running) return
        _uiState.update {
            it.copy(deactivate = HostPickerPolicy.deactivateOpen(it.deactivate, host))
        }
    }

    fun onDismissDeactivate() {
        if (_uiState.value.deactivate.running) return
        _uiState.update { it.copy(deactivate = HostPickerPolicy.deactivateDismiss(it.deactivate)) }
    }

    fun onConfirmDeactivate() {
        val card = _uiState.value
        val deactivate = card.deactivate
        if (!deactivate.visible || deactivate.running || deactivate.doneRes != null ||
            card.preparingLink || card.stoppingWebUi || card.confirmStopWebUi || card.hostPicker.isRunning) return
        val host = card.activeHost
        if (host.isBlank()) return
        invalidateWebUiStatusRead()
        _uiState.update {
            it.copy(deactivate = HostPickerPolicy.deactivateRunning(it.deactivate,
                beginOperation("deactivate:$host", R.string.lite_hidden_deactivate_running)),
                checkingWebUi = false, confirmStopWebUi = false,
                webUiState = RecoveryWebUiState.UNKNOWN,
                webUiLinkAvailable = false, recoveryLinkSaved = false)
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { LiteHiddenRuntimeProxy.deactivate(host) }
                    .onFailure { if (it is CancellationException) throw it }.getOrNull()
            }
            val ok = result?.success == true
            val failure = result?.failure ?: HiddenFailure.DEACTIVATE_FAILED
            if (ok) {
                rememberCompletedOperation("deactivate:$host", _uiState.value.deactivate.progress)
                Toast.makeText(ksuApp, R.string.lite_hidden_deactivate_ok, Toast.LENGTH_LONG).show()
                _uiState.update {
                    it.copy(
                        status = LiteHiddenStatus.OFF,
                        hidden = false,
                        statusRes = R.string.lite_hidden_status_off,
                        activeHost = "",
                        deactivate = HostPickerPolicy.deactivateFinished(
                            it.deactivate,
                            success = true,
                            doneRes = R.string.lite_hidden_deactivate_ok,
                            steps = result?.steps.orEmpty(),
                        ),
                    )
                }
                refreshWebUiStatus()
                return@launch
            }
            val res = LiteHiddenRuntimeProxy.failureMessageRes(failure)
            Toast.makeText(ksuApp, res, Toast.LENGTH_LONG).show()
            _uiState.update {
                it.copy(
                    deactivate = HostPickerPolicy.deactivateFinished(
                        it.deactivate,
                        success = false,
                        doneRes = R.string.lite_hidden_deactivate_ok,
                        failureCode = failure.wire,
                        failureRes = res,
                        steps = result?.steps.orEmpty(),
                    ),
                )
            }
            refreshWebUiStatus()
        }
    }

    // ── 端口 / 链接 ───────────────────────────────────────────────────────

    fun onPortChange(text: String) {
        if (_uiState.value.preparingLink || _uiState.value.stoppingWebUi || _uiState.value.hostPicker.visible) return
        val digits = text.filter { it.isDigit() }.take(5)
        val input = HiddenModePolicy.validatePort(digits)
        _uiState.update {
            it.copy(
                portText = digits,
                portValid = input.valid,
                webUiLink = previewLinkFor(digits),
                webUiLinkAvailable = false,
                recoveryLinkSaved = false,
                linkPortAdjusted = false,
                linkFailure = false,
            )
        }
        if (input.valid && input.value != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { LiteHiddenRuntimeProxy.setPreferredPort(input.value) }
            }
        }
    }

    /** Preparing a recovery entry starts the existing daemon, never the hidden transaction. */
    fun prepareRecoveryLink() {
        val card = _uiState.value
        val requestedPort = HiddenModePolicy.validatePort(card.portText).value ?: return
        if (card.checkingState || card.preparingLink || card.stoppingWebUi || !card.portValid || card.hostPicker.isRunning ||
            card.deactivate.visible || card.confirmStopWebUi ||
            dev.maplesu.manager.workmode.ModeRuntime.active != dev.maplesu.manager.workmode.WorkMode.LITE) return
        invalidateWebUiStatusRead()
        _uiState.update { it.copy(preparingLink = true, webUiLinkAvailable = false,
            recoveryProgress = beginOperation("prepare-link", R.string.ui_polish_link_preparing),
            recoveryLinkSaved = false, linkFailure = false, checkingWebUi = false,
            webUiState = RecoveryWebUiState.UNKNOWN) }
        viewModelScope.launch {
            try {
                var stage = "config"
                var failure = "LITEWEB_PREPARE_FAILED"
                val prepared = withContext(Dispatchers.IO) {
                    runCatching {
                        val controller = LiteWebAdminController(ksuApp)
                        val info = controller.preparePreviewUrl(requestedPort) ?: run {
                            failure = controller.previewError().ifEmpty { "LITEWEB_CONFIG_UNAVAILABLE" }
                            return@runCatching null
                        }
                        stage = "existing_probe"
                        _uiState.update { it.copy(recoveryProgress = it.recoveryProgress?.copy(stageRes = R.string.lite_hidden_step_webui_ping)) }
                        val existing = controller.url().orEmpty()
                        val existingToken = tokenOf(existing)
                        val existingPort = runCatching { Uri.parse(existing).port }.getOrDefault(-1)
                        if (existingPort == requestedPort && existingToken != null &&
                            HttpHiddenDaemonProbe().ping(existingPort, existingToken)) {
                            return@runCatching existing to existingPort
                        }
                        // The hidden transaction reuses only the recovery daemon. Kernel RootService
                        // serves a different endpoint, so use the existing daemon API for that branch.
                        stage = "daemon_start"
                        _uiState.update { it.copy(recoveryProgress = it.recoveryProgress?.copy(stageRes = R.string.lite_hidden_step_webui_start)) }
                        val listener = if (LiteHiddenRecoveryLinkPolicy.usesDirectDaemon(controller.rootBackend() == RootBackend.LITE_KERNEL)) {
                            UltraEnvLiteWebDaemon.default().start(ksuApp).let {
                                failure = it.code.ifEmpty { "LITEWEB_DAEMON_START_FAILED" }
                                it.started to it.port
                            }
                        } else {
                            controller.startForRecovery().let {
                                failure = it.error.ifEmpty { "LITEWEB_DAEMON_START_FAILED" }
                                it.running to it.port
                            }
                        }
                        val (started, actualPort) = listener
                        if (!started || actualPort == null) return@runCatching null
                        if (actualPort != requestedPort) {
                            failure = "LITEWEB_PORT_READBACK_MISMATCH"
                            return@runCatching null
                        }
                        stage = "authenticated_probe"
                        _uiState.update { it.copy(recoveryProgress = it.recoveryProgress?.copy(stageRes = R.string.lite_hidden_step_webui_ping)) }
                        failure = "LITEWEB_DAEMON_PING_FAILED"
                        repeat(6) {
                            val link = controller.url().orEmpty()
                            val token = tokenOf(link)
                            val port = runCatching { Uri.parse(link).port }.getOrDefault(info.port)
                            if (port == requestedPort && token != null && LiteHiddenRecoveryLinkPolicy.isReady(started, actualPort, port,
                                    HttpHiddenDaemonProbe().ping(port, token))) return@runCatching link to port
                            delay(150)
                        }
                        null
                    }.onFailure {
                        if (it is kotlinx.coroutines.CancellationException) throw it
                        failure = "LITEWEB_PREPARE_EXCEPTION_${it.javaClass.simpleName}"
                    }.getOrNull()
                }
                if (prepared == null) {
                    // Record only fixed stage/code fields, never URLs, tokens or root output.
                    android.util.Log.w("MapleSUHidden", "recovery-link stage=$stage code=$failure")
                    _uiState.update { it.copy(preparingLink = false, linkFailure = true,
                        webUiState = RecoveryWebUiState.UNKNOWN,
                        webUiLinkAvailable = false, recoveryLinkSaved = false) }
                    refreshWebUiStatus()
                    return@launch
                }
                val (link, port) = prepared
                webUiToken = tokenOf(link)
                withContext(Dispatchers.IO) { LiteHiddenRuntimeProxy.setPreferredPort(port) }
                rememberCompletedOperation("prepare-link", _uiState.value.recoveryProgress)
                _uiState.update {
                    it.copy(preparingLink = false, webUiLink = link, webUiLinkAvailable = true,
                        webUiState = RecoveryWebUiState.RUNNING, checkingWebUi = false, webUiStopFailed = false,
                        portText = port.toString(), portValid = true, linkPortAdjusted = card.portText != port.toString(),
                        hostPicker = it.hostPicker.copy(portText = port.toString(), portValid = true))
                }
            } finally {
                _uiState.update { it.copy(preparingLink = false, recoveryProgress = null) }
            }
        }
    }

    fun onCopyRecoveryLink() = copyPreviewLink()

    /** Copy only a verified recovery entry; credentials remain in memory/clipboard. */
    fun copyPreviewLink() {
        val card = _uiState.value
        val link = card.webUiLink
        if (!card.webUiLinkAvailable || link.isBlank() || link == "—") {
            Toast.makeText(ksuApp, R.string.lite_hidden_no_link, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val cm = ksuApp.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("MapleSU", link))
        }.onSuccess {
            _uiState.update { it.copy(recoveryLinkSaved = true) }
            Toast.makeText(ksuApp, R.string.lite_hidden_link_copied, Toast.LENGTH_SHORT).show()
        }
    }

    fun openRecoveryLink() {
        val card = _uiState.value
        if (!card.webUiLinkAvailable) return
        runCatching {
            ksuApp.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(card.webUiLink)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Toast.makeText(ksuApp, R.string.ui_polish_link_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** 链接只读预览：token 只在内存；不可用显示 "—"（不启动 daemon）。 */
    private fun previewLinkFor(port: String): String =
        HiddenModePolicy.webUiLinkPreview(port, webUiToken)

    private fun tokenOf(url: String?): String? =
        Regex("/x7k9f/([0-9a-fA-F]+)").find(url.orEmpty())?.groupValues?.getOrNull(1)

    private fun context(): Context = ksuApp
}
