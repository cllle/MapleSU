package dev.maplesu.manager.litehidden

/** 后端（wm-core / wm-installer 提供）执行「开启隐藏」的契约；未就绪时 fail-closed。 */
data class HiddenEnableResult(
    val success: Boolean,
    val failure: HiddenFailure? = null,
    /** 成功后返回的 WebUI 恢复链接（含 token，只在内存/剪贴板，不落 prefs/日志）。 */
    val recoveryUrl: String = "",
)

/**
 * 单步结果（诚实分步：每步独立 ok/fail，**禁止**合并成一个"成功"）。
 *
 * [step] 用 [HiddenActivateStep] / [HiddenDeactivateStep] 的 wire 常量；[code] 是后端结构化
 * 失败码（不用于文案判别），[detail] 仅诊断。
 */
data class HiddenStepResult(
    val step: String,
    val ok: Boolean,
    val code: String = "",
    val detail: String = "",
)

/** 激活事务步骤（终检/移除仅在失败时追加；UI 按后端顺序展示）。 */
enum class HiddenActivateStep(val wire: String) {
    /** 宿主预检：用户应用 / arm64 / 有 native 库 / 非自身。 */
    PRECHECK("precheck"),

    /** 写入并改写宿主 native 库（原库就地改写，保留恢复材料）。 */
    HOST_SO("host_so"),

    /** 起 WebUI 守护进程。 */
    WEBUI_START("webui_start"),

    /** WebUI ping 校验（不只是端口能连）。 */
    WEBUI_PING("webui_ping"),

    /** 写隐藏状态并回读。 */
    HIDE_STATE("hide_state"),

    /** 状态写入后的宿主、网页服务与隐藏状态终检。 */
    FINAL_VERIFY("final_verify"),

    /** Ultra 经独立恢复服务移除管理器，并确认安装状态。 */
    MANAGER_REMOVAL("manager_removal"),
}

/** 解除寄生固定步骤：恢复宿主原始 so → 撤销 su_auth → 停 daemon。 */
enum class HiddenDeactivateStep(val wire: String) {
    RESTORE_HOST_SO("restore_host_so"),
    REVOKE_SU_AUTH("revoke_su_auth"),
    STOP_DAEMON("stop_daemon"),
    /** Appended on cleanup failure after the three lifecycle effects have completed. */
    CLEANUP_RECOVERY("cleanup_recovery"),
}

/**
 * 激活结果。成功与否只看 [success]；[steps] 逐条回显，失败不合并成成功。
 * [recoveryUrl] 含 token，只在内存/剪贴板，不落 prefs/日志。
 */
data class HiddenActivateResult(
    val success: Boolean,
    val failure: HiddenFailure? = null,
    /** 后端结构化失败码（[HiddenFailure.wire] 或后端自有码）。 */
    val code: String = "",
    val hostPackage: String = "",
    val steps: List<HiddenStepResult> = emptyList(),
    val recoveryUrl: String = "",
)

/**
 * 解除结果：三步各自独立状态位 + 分步结果。
 * 只有后端明确回读成功才置 true（不回读就不算恢复）。
 *
 * [failedStep] / [retryableStep]：失败步的 wire 名。停 daemon 或清理恢复资料失败时 [retryableStep] 非空，
 * 语义是「宿主库/授权这些已经做完的步骤**不回滚**，用户可以只重试停 daemon」，UI 不该把整个
 * 解除流程展示成"什么都没发生"。
 */
data class HiddenDeactivateResult(
    val success: Boolean,
    val failure: HiddenFailure? = null,
    val code: String = "",
    val hostPackage: String = "",
    val steps: List<HiddenStepResult> = emptyList(),
    val hostSoRestored: Boolean = false,
    val suAuthRevoked: Boolean = false,
    val daemonStopped: Boolean = false,
    val failedStep: String = "",
    val retryableStep: String = "",
)

/**
 * 隐藏模式执行后端：以用户选择的宿主 App 为寄生载体启用 WebUI，并支持解除。
 *
 * 生产实现由 wm-core/wm-installer 注入；**未注入时** [HiddenModeBackends] 显式返回
 * `BACKEND_UNAVAILABLE`（fail-closed），UI 保持"未隐藏"，不假装成功。
 *
 * 契约要点：
 * - 只接收宿主**包名**，不接受客户端传入的 so 路径/命令/UID（由后端自己解析）；
 * - [activate] 任一步失败必须回滚已写入的宿主 so，并如实返回步骤结果；
 * - [deactivate] 必须真实恢复宿主原始 so、撤销 su_auth、停 daemon，三步分别报告。
 */
interface HiddenModeBackend {
    /** 旧契约：仅端口（无宿主）。保留给既有接线；新流程一律走 [activate]。 */
    suspend fun enableHidden(port: Int): HiddenEnableResult

    suspend fun restore(): HiddenEnableResult

    /** 以 [hostPackage] 为寄生宿主执行激活事务（宿主不存在/不可用 → 结构化失败）。 */
    suspend fun activate(hostPackage: String, port: Int): HiddenActivateResult

    /** 解除寄生：恢复宿主原始 so + 撤销 su_auth + 停 daemon，逐项报告。 */
    suspend fun deactivate(hostPackage: String): HiddenDeactivateResult
}

/**
 * 后端门面：可注入（[current]），未注入/参数非法时**显式失败**并给出原因码，
 * 绝不返回 success=true（UI 与测试据此判断"不可用"）。
 */
object HiddenModeBackends {
    @Volatile
    var current: HiddenModeBackend? = null

    /**
     * **懒装配**（2026-10-31 真机缺陷修复）：启动时 `ModeBootstrap` 的模式判定可能超过
     * `HiddenModeWiring` 的 10s 有界等待（Ultra 探测在后台线程、还要等 root 授权），
     * 旧实现一旦错过就**永久** `current = null` → 隐藏页一直「后端未就绪」，只有杀进程重启
     * 才可能恢复。这里留一个"模式已判定就现装"的提供者，后端缺失时按需装配一次。
     */
    @Volatile
    var ensureProvider: (() -> HiddenModeBackend?)? = null

    /** 当前可用后端；`current` 为空时按需装配（失败仍返回 null，绝不伪造可用）。 */
    fun backend(): HiddenModeBackend? {
        current?.let { return it }
        val created = runCatching { ensureProvider?.invoke() }.getOrNull() ?: return null
        current = created
        return created
    }

    suspend fun enableHidden(port: Int): HiddenEnableResult =
        backend()?.enableHidden(port)
            ?: HiddenEnableResult(success = false, failure = HiddenFailure.BACKEND_UNAVAILABLE)

    suspend fun restore(): HiddenEnableResult =
        backend()?.restore()
            ?: HiddenEnableResult(success = false, failure = HiddenFailure.BACKEND_UNAVAILABLE)

    /** 激活：先做与 UI 相同的纯校验（未选宿主/端口非法），再交后端；未接线 → 不可用。 */
    suspend fun activate(hostPackage: String, port: Int): HiddenActivateResult {
        val host = hostPackage.trim()
        if (host.isEmpty()) {
            return HiddenActivateResult(
                success = false,
                failure = HiddenFailure.HOST_NOT_SELECTED,
                code = HiddenFailure.HOST_NOT_SELECTED.wire,
            )
        }
        if (!HiddenModePolicy.validatePort(port.toString()).valid) {
            return HiddenActivateResult(
                success = false,
                failure = HiddenFailure.INVALID_PORT,
                code = HiddenFailure.INVALID_PORT.wire,
                hostPackage = host,
            )
        }
        val backend = backend()
            ?: return HiddenActivateResult(
                success = false,
                failure = HiddenFailure.BACKEND_UNAVAILABLE,
                code = HiddenFailure.BACKEND_UNAVAILABLE.wire,
                hostPackage = host,
            )
        return backend.activate(host, port)
    }

    /** 解除：未选宿主 → HOST_NOT_SELECTED；未接线 → BACKEND_UNAVAILABLE（均不假装成功）。 */
    suspend fun deactivate(hostPackage: String): HiddenDeactivateResult {
        val host = hostPackage.trim()
        if (host.isEmpty()) {
            return HiddenDeactivateResult(
                success = false,
                failure = HiddenFailure.HOST_NOT_SELECTED,
                code = HiddenFailure.HOST_NOT_SELECTED.wire,
            )
        }
        val backend = backend()
            ?: return HiddenDeactivateResult(
                success = false,
                failure = HiddenFailure.BACKEND_UNAVAILABLE,
                code = HiddenFailure.BACKEND_UNAVAILABLE.wire,
                hostPackage = host,
            )
        return backend.deactivate(host)
    }
}
