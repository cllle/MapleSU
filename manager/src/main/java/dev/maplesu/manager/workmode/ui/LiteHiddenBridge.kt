package dev.maplesu.manager.workmode.ui

import android.content.Context
import android.content.SharedPreferences
import com.maplesu.core.R
import dev.maplesu.manager.litehidden.HiddenActivateResult
import dev.maplesu.manager.litehidden.HiddenActivateStep
import dev.maplesu.manager.litehidden.HiddenDeactivateResult
import dev.maplesu.manager.litehidden.HiddenFailure
import dev.maplesu.manager.litehidden.HiddenHostPolicy
import dev.maplesu.manager.litehidden.LiteHiddenOutcome
import dev.maplesu.manager.litehidden.LiteHiddenPaths
import dev.maplesu.manager.litehidden.LiteHiddenState
import dev.maplesu.manager.litehidden.LiteHiddenTraceItem
import dev.maplesu.manager.litehidden.LiteHiddenTraceResult

/**
 * H3 Lite 隐藏模式：UI 层薄代理（避免 UI/VM 直接依赖 Android 控制器实现细节）。
 * 所有判定仍在 `dev.maplesu.manager.litehidden` 纯策略与 `ui/security/LiteHiddenController`。
 */
object LiteHiddenRuntimeProxy {
    private val transitionActive = java.util.concurrent.atomic.AtomicBoolean(false)

    fun readState(): LiteHiddenState = com.maplesu.core.ui.security.LiteHiddenController.readState()

    /** 进程内已知的隐藏状态（不发起 root 读取；UNKNOWN 表示读失败，绝不当"已开启"文案）。 */
    fun currentState(): LiteHiddenState = com.maplesu.core.ui.security.LiteHiddenRuntime.currentState()

    fun setEnabled(enable: Boolean): LiteHiddenOutcome =
        com.maplesu.core.ui.security.LiteHiddenController.setEnabled(enable)

    fun applyTrace(context: Context, selection: Map<LiteHiddenTraceItem, Boolean>): List<LiteHiddenTraceResult> =
        com.maplesu.core.ui.security.LiteHiddenController.applyTrace(context, selection)

    fun isMaskingLite(): Boolean = com.maplesu.core.ui.security.LiteHiddenRuntime.isMaskingLite()

    fun visibility(): LiteHiddenVisibility = com.maplesu.core.ui.security.LiteHiddenRuntime.visibility()

    /** 根通道（Settings 卡显示「根通道：Ultra 环境 / Ultra 内核 / 无」）。 */
    fun rootBackendLabel(): String =
        com.maplesu.core.ui.security.LiteHiddenController.rootBackendLabel()

    /** Lite 根可用语义：Ultra 环境严格 Running（不再以 libsu shell 结果为准）。 */
    fun rootAvailableForLite(): Boolean =
        com.maplesu.core.ui.security.LiteHiddenController.rootAvailableForLite()

    // ── 最终形态：一个按钮 ────────────────────────────────────────────────

    fun preferredPort(): Int = com.maplesu.core.ui.security.LiteHiddenController.preferredPort()

    fun setPreferredPort(port: Int): Boolean =
        com.maplesu.core.ui.security.LiteHiddenController.setPreferredPort(port)

    fun webUiRecoveryLink(): String? =
        com.maplesu.core.ui.security.LiteHiddenController.webUiRecoveryLink()

    fun failureMessageRes(failure: HiddenFailure): Int = when (failure) {
        HiddenFailure.HOST_NOT_SELECTED -> R.string.lite_hidden_reason_host
        HiddenFailure.HOST_REJECTED -> R.string.lite_hidden_reason_host_invalid
        HiddenFailure.HOST_SCAN_FAILED -> R.string.lite_hidden_reason_host_scan
        HiddenFailure.HOST_SO_PATCH_FAILED -> R.string.lite_hidden_reason_host_so
        HiddenFailure.HOST_BACKUP_FAILED -> R.string.lite_hidden_reason_host_backup
        HiddenFailure.DEACTIVATE_FAILED -> R.string.lite_hidden_reason_deactivate
        HiddenFailure.RECOVERY_NOT_READY -> R.string.lite_hidden_reason_recovery_not_ready
        HiddenFailure.MANAGER_REMOVAL_FAILED -> R.string.lite_hidden_reason_manager_removal
        else -> com.maplesu.core.ui.security.LiteHiddenController.failureMessageRes(failure)
    }

    fun canEnable(port: dev.maplesu.manager.litehidden.PortInput): HiddenFailure? {
        val mode = dev.maplesu.manager.workmode.ModeRuntime.active
        val state = readState()
        // 入口可见性（A/B 一致，隐藏生效后不可再开）与端口/根/恢复通道全部走纯策略。
        if (!HiddenHostPolicy.canOpenHostPicker(mode, state)) return HiddenFailure.VERIFY_FAILED
        val channels = dev.maplesu.manager.litehidden.RecoveryChannels(
            dialCodeAvailable = true,
            webUiRecoveryLink = webUiRecoveryLink(),
            rootAvailable = state != LiteHiddenState.UNKNOWN,
        )
        return dev.maplesu.manager.litehidden.HiddenModePolicy.canEnable(mode, state, port, channels)
    }

    /**
     * 以用户选择的宿主执行激活事务：前置校验 → 后端校验状态并移除管理器。
     *
     * 失败语义（不假装成功）：
     * - 非 LITE / 已隐藏 / 端口非法 / 无根 → 结构化失败码，**不调后端**；
     * - 宿主不可寄生 / 未选择 → HOST_REJECTED / HOST_NOT_SELECTED；
     * - 后端未接线 → BACKEND_UNAVAILABLE；
     * - 后端完成写入与回读后才移除管理器；成功回执同步进程内状态，不再经已移除的 App 回读。
     */
    suspend fun activate(hostPackage: String, port: Int): HiddenActivateResult {
        val mode = dev.maplesu.manager.workmode.ModeRuntime.active
        val host = hostPackage.trim()
        if (mode != dev.maplesu.manager.workmode.WorkMode.LITE) {
            return HiddenActivateResult(false, HiddenFailure.BACKEND_UNAVAILABLE, HiddenFailure.BACKEND_UNAVAILABLE.wire, host)
        }
        if (host.isEmpty()) {
            return HiddenActivateResult(false, HiddenFailure.HOST_NOT_SELECTED, HiddenFailure.HOST_NOT_SELECTED.wire)
        }
        if (!HiddenHostPolicy.isValidPackage(host) || host in HiddenHostPolicy.SELF_PACKAGES) {
            return HiddenActivateResult(false, HiddenFailure.HOST_REJECTED, HiddenFailure.HOST_REJECTED.wire, host)
        }
        val state = readState()
        if (state == LiteHiddenState.UNKNOWN) {
            return HiddenActivateResult(false, HiddenFailure.ROOT_UNAVAILABLE, HiddenFailure.ROOT_UNAVAILABLE.wire, host)
        }
        if (state == LiteHiddenState.ON) {
            return HiddenActivateResult(false, HiddenFailure.VERIFY_FAILED, HiddenFailure.VERIFY_FAILED.wire, host)
        }
        if (!dev.maplesu.manager.litehidden.HiddenModePolicy.validatePort(port.toString()).valid) {
            return HiddenActivateResult(false, HiddenFailure.INVALID_PORT, HiddenFailure.INVALID_PORT.wire, host)
        }
        if (!transitionActive.compareAndSet(false, true)) {
            return HiddenActivateResult(false, HiddenFailure.UNKNOWN, dev.maplesu.manager.litehidden.ParasiteCodes.BUSY, host)
        }
        return try {
            setPreferredPort(port)
            // Persist before entering root work: process removal can prevent every post-call update.
            setActiveHostPackage(host)
            // Only this owner may clear pending; BUSY calls exit before any preference or pending write.
            com.maplesu.core.ui.security.HiddenStartupConfirm.setPending(com.maplesu.core.ksuApp, true)
            val result = dev.maplesu.manager.litehidden.HiddenModeBackends.activate(host, port).also {
                if (it.success) com.maplesu.core.ui.security.LiteHiddenRuntime.update(LiteHiddenState.ON)
            }
            // 宿主 so 已被改写（即使后续步骤失败）→ 记录，保证「解除寄生」入口可达。
            if (result.steps.any { it.step == HiddenActivateStep.HOST_SO.wire && it.ok }) {
                setActiveHostPackage(host)
            }
            if (result.success) setActiveHostPackage(host)
            result
        } finally {
            runCatching { com.maplesu.core.ui.security.HiddenStartupConfirm.setPending(com.maplesu.core.ksuApp, false) }
            transitionActive.set(false)
        }
    }

    /**
     * 解除寄生：恢复宿主原始 so + 撤销 su_auth + 停 daemon（后端逐项报告）。
     * 成功后回读隐藏状态必须为 OFF，否则按失败返回（不假装解除完成）。
     */
    suspend fun deactivate(hostPackage: String): HiddenDeactivateResult {
        val host = hostPackage.trim()
        if (dev.maplesu.manager.workmode.ModeRuntime.active != dev.maplesu.manager.workmode.WorkMode.LITE) {
            return HiddenDeactivateResult(false, HiddenFailure.BACKEND_UNAVAILABLE, HiddenFailure.BACKEND_UNAVAILABLE.wire, host)
        }
        if (host.isEmpty()) {
            return HiddenDeactivateResult(false, HiddenFailure.HOST_NOT_SELECTED, HiddenFailure.HOST_NOT_SELECTED.wire)
        }
        if (!transitionActive.compareAndSet(false, true)) {
            return HiddenDeactivateResult(false, HiddenFailure.UNKNOWN, dev.maplesu.manager.litehidden.ParasiteCodes.BUSY, host)
        }
        return try {
            com.maplesu.core.ui.security.HiddenStartupConfirm.setPending(com.maplesu.core.ksuApp, true)
            val result = dev.maplesu.manager.litehidden.HiddenModeBackends.deactivate(host)
            if (!result.success) result else if (readState().isEffectivelyOn) {
                result.copy(
                    success = false,
                    failure = HiddenFailure.VERIFY_FAILED,
                    code = HiddenFailure.VERIFY_FAILED.wire,
                )
            } else {
                setActiveHostPackage("")
                result
            }
        } finally {
            runCatching { com.maplesu.core.ui.security.HiddenStartupConfirm.setPending(com.maplesu.core.ksuApp, false) }
            transitionActive.set(false)
        }
    }

    // ── 宿主记忆（仅 UI 记忆，不是宿主真实状态）──────────────────────────

    /** 上一次成功/尝试改写的宿主包名（用于「解除寄生」入口）。 */
    fun activeHostPackage(): String = runCatching {
        prefs().getString(LiteHiddenPaths.PREF_HOST, "").orEmpty()
    }.getOrDefault("")

    fun setActiveHostPackage(packageName: String) {
        runCatching {
            prefs().edit().putString(LiteHiddenPaths.PREF_HOST, packageName.trim()).commit()
        }
    }

    /** 宿主候选：可注入 fake（生产用 PackageManager 扫描）。 */
    suspend fun hostCandidates(context: Context): HostCandidateResult =
        (LiteHiddenHostSources.current ?: LiteHiddenHostSources.forContext(context)).load()

    private fun prefs(): SharedPreferences =
        dev.maplesu.manager.workmode.storage.SettingsPrefsRouter(com.maplesu.core.ksuApp).prefs()
}

typealias LiteHiddenVisibility = dev.maplesu.manager.litehidden.LiteHiddenVisibility
