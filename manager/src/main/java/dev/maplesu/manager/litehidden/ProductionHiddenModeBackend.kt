package dev.maplesu.manager.litehidden

import dev.maplesu.manager.liteweb.LiteWebToken
import dev.maplesu.manager.recovery.RecoveryBundle
import dev.maplesu.manager.recovery.RecoveryCommandResult
import dev.maplesu.manager.recovery.RecoveryPrepareResult
import dev.maplesu.manager.workmode.WorkMode
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 生产后端的 root 执行口 —— **只**包一层既有通道，绝不自己开 su/libsu：
 * - A（LITE）：`UltraRuntimeClient.runRootCommand`（Ultra `run_root_cmd`）；
 * - B（KSU）：`RootExecutionRouter`（LITE_KERNEL → ULTRA_ENV 的既有顺序）。
 *
 * 失败必须如实返回非 0（不能把「后端不可用」写成执行成功）。
 */
fun interface HiddenRootRunner {
    fun run(script: String): RecoveryCommandResult
}

/** `su_auth` 授权结果（四种语义必须分开，不能把「KSU 策略托管」写成「已授权」）。 */
sealed interface SuAuthOutcome {
    /** 本次真的新增了白名单条目。 */
    data object Granted : SuAuthOutcome

    /** 之前就在白名单里（幂等重入）。 */
    data object AlreadyGranted : SuAuthOutcome

    /** 该模式没有可写的 allowlist，授权由内核策略在请求时裁决（B/KSU）。 */
    data object PolicyManaged : SuAuthOutcome

    /** 本次真的删除了白名单条目（解除寄生）。 */
    data object Revoked : SuAuthOutcome

    data class Failed(val detail: String, val rollbackGrant: Boolean = false) : SuAuthOutcome

    val ok: Boolean get() = this !is Failed

    fun wire(): String = when (this) {
        Granted -> "granted"
        AlreadyGranted -> "already_granted"
        PolicyManaged -> "policy_managed"
        Revoked -> "revoked"
        is Failed -> "failed"
    }
}

/** 宿主包名的 su 授权端口（A=Ultra su_auth 白名单；B=KSU 策略托管）。 */
interface HiddenSuAuth {
    fun grant(packageName: String): SuAuthOutcome

    fun revoke(packageName: String): SuAuthOutcome

    /** 回读：只有真的在白名单里才 true（A）；B 无 allowlist 可读 → false，不谎称。 */
    fun isGranted(packageName: String): Boolean
}

/** 隐藏状态端口（A=写 `/data/adb/maplesu/lite_hidden`；B=KSU stealth + 兼容位）。 */
interface HiddenStatePort {
    fun write(on: Boolean): Boolean

    /** 回读：null = 读不到（**不能**当 false）。 */
    fun read(): Boolean?
}

/** WebUI daemon 真实 HTTP 探活（不是「端口能 connect」）。 */
fun interface HiddenDaemonProbe {
    fun ping(port: Int, token: String): Boolean
}

/** Keep the daemon's fixed failure code instead of flattening it into a Boolean. */
data class HiddenManagerRemovalResult(val removed: Boolean, val code: String = "")

/** The root recovery daemon performs and verifies removal independently of the manager process. */
fun interface HiddenManagerRemoval {
    fun uninstall(port: Int, token: String): HiddenManagerRemovalResult

    fun supported(port: Int, token: String): Boolean = true
}

/** 一个模式的后端装配（A/B 各一份；跨模式调用一律 BACKEND_UNAVAILABLE）。 */
class HiddenModeDeps(
    val mode: WorkMode,
    val root: HiddenRootRunner,
    val suAuth: HiddenSuAuth,
    val state: HiddenStatePort,
    val rootAvailable: () -> Boolean = { true },
    /**
     * 撤销授权所需的**身份材料**（App 作用域 androidId + 包名 + uid）。
     *
     * Ultra 缺身份材料或落盘失败会中止并回滚；Normal 保持现有 App 内隐藏路径。
     */
    val identity: () -> RecoveryIdentity? = { null },
    /** Ultra activation must deploy its independent boot entry before modifying a host. */
    val prepareBoot: () -> Boolean = { true },
)

/**
 * 隐藏模式的**生产实现**：宿主校验 → 备份 → 注入 loader → su 授权 → 恢复包 + WebUI daemon
 * → 隐藏状态 → 终检；任一步失败**逆序回滚**（绝不半隐藏）。解除寄生反向执行三步。
 *
 * 与 UI 的契约见 [HiddenModeBackend]：只接收包名/端口，所有路径与命令由后端自己生成；
 * 每步都有独立 `ok` 与结构化 `code`，失败不合并成成功。
 *
 * 模式隔离：`modeProvider()` 决定用哪一份 [HiddenModeDeps]；LITE 永不触碰 KSU 通道，
 * KSU 永不触碰 Ultra 通道。模式未判定/不支持 → BACKEND_UNAVAILABLE（fail-closed）。
 *
 * 步骤映射（成功保留原有 5 步；终检与移除失败独立报告，避免覆盖写入状态）：
 * - `precheck`：root 通道自检 + PackageManager 宿主事实 + [ParasitePolicy] 过滤 + 陈旧备份守卫；
 * - `host_so`：备份宿主原 so（sha/权限/label）→ root 注入 loader → addSuAuth + 回读；
 * - `webui_start`：RecoveryBundle（APK + webui.conf）→ 落盘启动脚本 → 拉起 daemon；
 * - `webui_ping`：真实 HTTP 取页（不是端口 connect）；
 * - `hide_state`：写隐藏状态并回读；
 * - `final_verify`：宿主 loader sha 级复核 + WebUI 仍可访问 + 状态仍为 ON；
 * - `manager_removal`：Ultra 管理器移除及 PM 回读。
 */
class ProductionHiddenModeBackend(
    private val modeProvider: () -> WorkMode?,
    private val deps: Map<WorkMode, HiddenModeDeps>,
    private val hostFacts: HostFactsSource,
    private val bundle: RecoveryBundle,
    private val probe: HiddenDaemonProbe,
    private val managerApkPath: String,
    private val managerVersionCode: Long,
    private val loaderSourcePath: String,
    private val managerPackage: String = "dev.maplesu.manager",
    private val selfPackages: Set<String> = HiddenHostPolicy.SELF_PACKAGES + managerPackage,
    private val tokenFactory: () -> String = { LiteWebToken.generate() },
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
    private val logger: (String) -> Unit = {},
    private val managerRemoval: HiddenManagerRemoval = HiddenManagerRemoval { _, _ -> HiddenManagerRemovalResult(false) },
) : HiddenModeBackend {

    @Volatile
    private var lastHostPackage: String = ""

    // ── 旧契约（无宿主）：fail-closed，不猜宿主 ────────────────────────────

    override suspend fun enableHidden(port: Int): HiddenEnableResult {
        val host = lastHostPackage
        if (host.isBlank()) {
            return HiddenEnableResult(success = false, failure = HiddenFailure.HOST_NOT_SELECTED)
        }
        val result = activate(host, port)
        return HiddenEnableResult(
            success = result.success,
            failure = result.failure,
            recoveryUrl = result.recoveryUrl,
        )
    }

    override suspend fun restore(): HiddenEnableResult {
        val host = lastHostPackage
        if (host.isBlank()) {
            return HiddenEnableResult(success = false, failure = HiddenFailure.HOST_NOT_SELECTED)
        }
        val result = deactivate(host)
        return HiddenEnableResult(success = result.success, failure = result.failure)
    }

    // ── 激活 ───────────────────────────────────────────────────────────────

    override suspend fun activate(hostPackage: String, port: Int): HiddenActivateResult {
        if (!transactionActive.compareAndSet(false, true)) {
            return HiddenActivateResult(false, HiddenFailure.UNKNOWN, ParasiteCodes.BUSY, hostPackage.trim())
        }
        return try { activateOwned(hostPackage, port) } finally { transactionActive.set(false) }
    }

    private fun activateOwned(hostPackage: String, port: Int): HiddenActivateResult {
        val host = hostPackage.trim()
        val plan = resolveDeps()
            ?: return HiddenActivateResult(
                success = false,
                failure = HiddenFailure.BACKEND_UNAVAILABLE,
                code = HiddenFailure.BACKEND_UNAVAILABLE.wire,
                hostPackage = host,
            )
        val steps = mutableListOf<HiddenStepResult>()
        val rollback = RollbackBook(plan, host)

        // 1. PRECHECK
        val precheck = precheck(host, plan)
        steps += precheck.step
        if (!precheck.ok || precheck.prepared == null) {
            return failed(steps, precheck.code, precheck.detail, host, rollback)
        }
        var prepared = precheck.prepared

        // 2. HOST_SO：备份 → 注入 → su 授权
        val hostSo = injectHost(prepared, plan, rollback)
        steps += hostSo.step
        if (!hostSo.ok || hostSo.prepared == null) {
            return failed(steps, hostSo.step.code, hostSo.step.detail, host, rollback)
        }
        prepared = hostSo.prepared

        // 3. WEBUI_START：恢复包 → 启动脚本 → daemon
        // 若已经有一个**健康**的 daemon 在监听（例如用户先在「WebUI 自检」里启动过），
        // 必须复用它并沿用它的 token：否则新 daemon 会因端口被占立即退出
        // （真机实测 DAEMON_EXITED），而且换 token 会让已复制的链接立刻失效。
        val existingToken = existingDaemonToken(plan, port)
        val token = existingToken ?: runCatching { tokenFactory() }.getOrNull().orEmpty()
        if (token.length < 16 || !token.all { it.isLetterOrDigit() }) {
            steps += HiddenStepResult(
                HiddenActivateStep.WEBUI_START.wire, false, ParasiteCodes.RECOVERY_PREPARE_FAILED, "token",
            )
            return failed(steps, ParasiteCodes.RECOVERY_PREPARE_FAILED, "token", host, rollback)
        }
        val webui = startWebUi(plan, port, token, rollback, reuseExistingDaemon = existingToken != null)
        steps += webui.step
        if (!webui.ok) return failed(steps, webui.step.code, webui.step.detail, host, rollback)

        // 4. WEBUI_PING
        val pinged = pingDaemon(port, token)
        steps += HiddenStepResult(
            HiddenActivateStep.WEBUI_PING.wire,
            ok = pinged,
            code = if (pinged) "" else ParasiteCodes.DAEMON_PING_FAILED,
            detail = "port=$port",
        )
        if (!pinged) return failed(steps, ParasiteCodes.DAEMON_PING_FAILED, "port=$port", host, rollback)

        // 5. HIDE_STATE
        val hide = writeHiddenState(plan, rollback)
        steps += hide.step
        if (!hide.ok) return failed(steps, hide.step.code, hide.step.detail, host, rollback)

        // Ultra terminates the manager-started daemon during the host proof. Any new
        // process belongs to this activation and must be stopped on a verified rollback.
        if (plan.mode == WorkMode.LITE) rollback.daemonStarted = true
        // 终检：loader sha + 本次宿主启动证明 + WebUI + 隐藏状态。
        val finalFailure = finalVerify(prepared, plan, port, token)
        if (finalFailure != null) {
            steps += finalFailure.step
            return failed(steps, finalFailure.step.code, finalFailure.step.detail, host, rollback)
        }
        // Ultra recovery owns its root authorization independently of the installed app.
        // Normal retains its existing in-app stealth path until kernel-state restoration is wired.
        if (plan.mode == WorkMode.LITE) {
            // PM can still be processing a request after the HTTP client times out.
            // From this boundary onward an app-side rollback must retain the daemon.
            rollback.keepDaemon = true
            val removal = runCatching { managerRemoval.uninstall(port, token) }
                .getOrElse { HiddenManagerRemovalResult(false) }
            if (!removal.removed) {
                val removalCode = removal.code.ifEmpty { HiddenFailure.MANAGER_REMOVAL_FAILED.wire }
                steps += step(HiddenActivateStep.MANAGER_REMOVAL.wire, false, removalCode, "manager_removal")
                // A lost HTTP response says nothing about PM's committed side effects.
                // Stop recovery only after a fresh query proves the manager still exists.
                val presence = runCatching { plan.root.run(
                    "PACKAGES=\$(pm list packages --user 0 '$managerPackage' 2>/dev/null) || exit 1; " +
                        "if printf '%s\\n' \"\$PACKAGES\" | grep -qx 'package:$managerPackage'; then " +
                        "echo MAPLESU_MANAGER_PRESENT; else echo MAPLESU_MANAGER_ABSENT; fi",
                ) }.getOrNull()
                val marker = presence?.takeIf { it.code == 0 }?.output?.trim()
                if (marker == "MAPLESU_MANAGER_PRESENT") {
                    return failed(steps, removalCode, "manager_removal", host, rollback)
                }
                if (marker != "MAPLESU_MANAGER_ABSENT") {
                    lastHostPackage = host
                    steps[steps.lastIndex] = steps.last().copy(detail = "manager_presence=unknown;recovery=retained")
                    logger("hidden.activate failed host=$host code=$removalCode " +
                        "step=${HiddenActivateStep.MANAGER_REMOVAL.wire} detail=manager_presence_unknown;recovery=retained")
                    return HiddenActivateResult(
                        success = false, failure = failureOf(removalCode),
                        code = removalCode, hostPackage = host,
                        steps = steps,
                        recoveryUrl = ParasitePaths.webUiUrl(port, token),
                    )
                }
                steps.removeAt(steps.lastIndex)
            }
            steps[steps.lastIndex] = steps.last().copy(detail = "state=on;manager=absent;data=retained")
        }
        lastHostPackage = host
        return HiddenActivateResult(
            success = true,
            hostPackage = host,
            steps = steps,
            recoveryUrl = ParasitePaths.webUiUrl(port, token),
        )
    }

    // ── 解除寄生 ───────────────────────────────────────────────────────────

    override suspend fun deactivate(hostPackage: String): HiddenDeactivateResult {
        if (!transactionActive.compareAndSet(false, true)) {
            return HiddenDeactivateResult(false, HiddenFailure.UNKNOWN, ParasiteCodes.BUSY, hostPackage.trim())
        }
        return try { deactivateOwned(hostPackage) } finally { transactionActive.set(false) }
    }

    private fun deactivateOwned(hostPackage: String): HiddenDeactivateResult {
        val host = hostPackage.trim()
        val plan = resolveDeps()
            ?: return HiddenDeactivateResult(
                success = false,
                failure = HiddenFailure.BACKEND_UNAVAILABLE,
                code = HiddenFailure.BACKEND_UNAVAILABLE.wire,
                hostPackage = host,
            )
        val steps = mutableListOf<HiddenStepResult>()

        val rootProbe = runCatching { plan.root.run(ParasiteScripts.probeRoot()) }.getOrNull()
        if (rootProbe?.code != 0 || rootProbe.output.trim() != "0") {
            return HiddenDeactivateResult(
                success = false, failure = HiddenFailure.ROOT_UNAVAILABLE,
                code = HiddenFailure.ROOT_UNAVAILABLE.wire, hostPackage = host,
            )
        }
        if (!HiddenHostPolicy.isValidPackage(host)) {
            return HiddenDeactivateResult(false, HiddenFailure.HOST_REJECTED, ParasiteCodes.HOST_UNSUPPORTED, host)
        }
        val checkpoint = runCatching { plan.root.run(ParasiteScripts.readCleanupCheckpoint()) }.getOrNull()
        val checkpointText = checkpoint?.output?.trim()
        if (checkpoint?.code != 0 || checkpointText != ParasiteMarkers.CLEANUP_NONE) {
            val previouslyRestored = listOf(false, true).firstOrNull {
                checkpoint?.code == 0 && checkpointText == ParasiteScripts.cleanupReceipt(host, plan.mode.name, it)
            }
            if (previouslyRestored == null) {
                return cleanupFailure(host, steps, false, ParasiteCodes.CLEANUP_CHECKPOINT_FAILED,
                    "checkpoint_untrusted_or_other_host_mode")
            }
            // This receipt is outside the recursively removed backup. Even a partial rm
            // and a rebuilt backend resume cleanup without reusing damaged .orig files.
            listOf(HiddenDeactivateStep.RESTORE_HOST_SO, HiddenDeactivateStep.REVOKE_SU_AUTH,
                HiddenDeactivateStep.STOP_DAEMON).forEach {
                steps += step(it.wire, true, detail = "previously_verified")
            }
            return cleanupRecovery(host, plan, steps, previouslyRestored, checkpointExists = true)
        }
        val restored = restoreHost(host, plan)
        steps += restored.step
        val revoked = if (restored.step.ok) revokeSuAuth(host, plan).also { steps += it.step } else null
        val stopped = if (revoked?.step?.ok == true) stopDaemonAndClearState(plan).also { steps += it.step } else null

        // 「没有备份」= 本机从未成功注入：没有可恢复材料，如实记为已复原过（hostSoRestored=false）。
        val hostSoRestored = restored.verified
        val restoredOk = restored.step.ok
        val success = restoredOk && revoked?.step?.ok == true && stopped?.step?.ok == true
        if (success) return cleanupRecovery(host, plan, steps, hostSoRestored, checkpointExists = false)
        val code = steps.firstOrNull { !it.ok }?.code.orEmpty().ifEmpty { ParasiteCodes.HOST_SO_RESTORE_FAILED }
        val failedStep = steps.firstOrNull { !it.ok }
        // 停 daemon 失败 = **总体失败**（不能因为宿主库已还原就报成功）；但已完成的还原/撤销
        // 保留，不回滚 —— 用户只需要重试这一件事，见 retryableStep。
        val retryableStep = if (failedStep?.step == HiddenDeactivateStep.STOP_DAEMON.wire) {
            HiddenDeactivateStep.STOP_DAEMON.wire
        } else {
            ""
        }
        logger(
            "hidden.deactivate failed host=$host code=$code " +
                "step=${failedStep?.step ?: "-"} detail=${failedStep?.detail ?: "-"} " +
                "host_so=${restored.step.ok} su_auth=${revoked?.step?.ok == true} retry=$retryableStep",
        )
        return HiddenDeactivateResult(
            success = false,
            failure = failureOf(code),
            code = code,
            hostPackage = host,
            steps = steps,
            hostSoRestored = hostSoRestored,
            suAuthRevoked = revoked?.step?.ok == true,
            daemonStopped = stopped?.step?.ok == true,
            failedStep = failedStep?.step.orEmpty(),
            retryableStep = retryableStep,
        )
    }

    private fun cleanupRecovery(
        host: String,
        plan: HiddenModeDeps,
        steps: List<HiddenStepResult>,
        restored: Boolean,
        checkpointExists: Boolean,
    ): HiddenDeactivateResult {
        if (!checkpointExists) {
            val started = runCatching {
                plan.root.run(ParasiteScripts.beginCleanup(host, plan.mode.name, restored))
            }.getOrNull()
            if (started?.code != 0 || !started.output.lineSequence().any { it == ParasiteMarkers.CLEANUP_READY }) {
                return cleanupFailure(host, steps, restored, ParasiteCodes.CLEANUP_CHECKPOINT_FAILED, "checkpoint_write_unverified")
            }
        }
        val removed = runCatching { plan.root.run(ParasiteScripts.removeBackup(host)) }.getOrNull()
        if (removed?.code != 0 || !removed.output.lineSequence().any { it == ParasiteMarkers.BACKUP_GONE }) {
            return cleanupFailure(host, steps, restored, ParasiteCodes.HOST_BACKUP_CLEANUP_FAILED,
                removed?.let(::failureDetail) ?: "backup_cleanup_exception")
        }
        val identity = runCatching { plan.root.run(ParasiteScripts.removeCleanupIdentity()) }.getOrNull()
        if (identity?.code != 0 || !identity.output.lineSequence().any { it == ParasiteMarkers.IDENTITY_GONE }) {
            return cleanupFailure(host, steps, restored, ParasiteCodes.IDENTITY_CLEANUP_FAILED,
                identity?.let(::failureDetail) ?: "identity_cleanup_exception")
        }
        val finished = runCatching {
            plan.root.run(ParasiteScripts.finishCleanup(host, plan.mode.name, restored))
        }.getOrNull()
        if (finished?.code != 0 || !finished.output.lineSequence().any { it == ParasiteMarkers.CLEANUP_GONE }) {
            return cleanupFailure(host, steps, restored, ParasiteCodes.CLEANUP_CHECKPOINT_FAILED, "checkpoint_delete_unverified")
        }
        lastHostPackage = ""
        return HiddenDeactivateResult(true, hostPackage = host, steps = steps,
            hostSoRestored = restored, suAuthRevoked = true, daemonStopped = true)
    }

    private fun cleanupFailure(
        host: String,
        steps: List<HiddenStepResult>,
        restored: Boolean,
        code: String,
        detail: String,
    ): HiddenDeactivateResult {
        val cleanup = HiddenDeactivateStep.CLEANUP_RECOVERY.wire
        logger("hidden.deactivate failed host=$host code=$code step=$cleanup detail=$detail")
        return HiddenDeactivateResult(false, failureOf(code), code, host,
            steps = steps + step(cleanup, false, code, detail), hostSoRestored = restored,
            suAuthRevoked = steps.any { it.step == HiddenDeactivateStep.REVOKE_SU_AUTH.wire && it.ok },
            daemonStopped = steps.any { it.step == HiddenDeactivateStep.STOP_DAEMON.wire && it.ok },
            failedStep = cleanup, retryableStep = cleanup)
    }

    // ── 步骤实现 ───────────────────────────────────────────────────────────

    private class PreparedHost(
        val facts: HostFacts,
        val targets: List<String>,
        val label: String,
        val manifest: HostManifest,
    )

    private class PrecheckOutcome(
        val step: HiddenStepResult,
        val prepared: PreparedHost? = null,
    ) {
        val ok: Boolean get() = step.ok
        val code: String get() = step.code
        val detail: String get() = step.detail
    }

    private class HostSoOutcome(
        val step: HiddenStepResult,
        val prepared: PreparedHost? = null,
    ) {
        val ok: Boolean get() = step.ok
    }

    private class SimpleOutcome(val step: HiddenStepResult) {
        val ok: Boolean get() = step.ok
    }

    private fun resolveDeps(): HiddenModeDeps? {
        val mode = runCatching { modeProvider() }.getOrNull() ?: return null
        return deps[mode]?.takeIf { it.mode == mode }
    }

    private fun step(wire: String, ok: Boolean, code: String = "", detail: String = ""): HiddenStepResult =
        HiddenStepResult(wire, ok = ok, code = code, detail = detail)

    private fun precheck(host: String, plan: HiddenModeDeps): PrecheckOutcome {
        fun reject(code: String, detail: String): PrecheckOutcome =
            PrecheckOutcome(step(HiddenActivateStep.PRECHECK.wire, ok = false, code = code, detail = detail))

        val available = runCatching { plan.rootAvailable() }.getOrDefault(false)
        if (!available) return reject(HiddenFailure.ROOT_UNAVAILABLE.wire, "root_backend_unavailable")
        val rootProbe = runCatching { plan.root.run(ParasiteScripts.probeRoot()) }.getOrNull()
            ?: return reject(HiddenFailure.ROOT_UNAVAILABLE.wire, "root_probe_exception")
        if (rootProbe.code != 0 || rootProbe.output.trim() != "0") {
            return reject(HiddenFailure.ROOT_UNAVAILABLE.wire, "root_probe_failed")
        }
        val lookup = runCatching { hostFacts.load(host) }.getOrElse { error ->
            HostFactsLookup.Failed(error.javaClass.simpleName)
        }
        val facts = when (lookup) {
            is HostFactsLookup.Failed -> return reject(ParasiteCodes.HOST_SCAN_FAILED, lookup.detail)
            is HostFactsLookup.Missing -> return reject(ParasiteCodes.HOST_UNSUPPORTED, "not_installed")
            is HostFactsLookup.Ok -> lookup.facts
        }
        val validated = when (val validation = ParasitePolicy.validate(facts, selfPackages)) {
            is HostValidation.Rejected -> return reject(validation.code, validation.detail)
            is HostValidation.Ok -> validation
        }
        val guard = runCatching { plan.root.run(ParasiteScripts.backupGuard(host)) }.getOrNull()
            ?: return reject(HiddenFailure.ROOT_UNAVAILABLE.wire, "backup_guard_exception")
        if (guard.code != 0) return reject(HiddenFailure.ROOT_UNAVAILABLE.wire, "backup_guard")
        if (guard.output.contains(STALE_BACKUP_MARKER)) {
            return reject(ParasiteCodes.HOST_BACKUP_STALE, "previous_backup_present")
        }
        if (plan.mode == WorkMode.LITE) {
            if (!runCatching { plan.prepareBoot() }.getOrDefault(false)) {
                return reject(ParasiteCodes.DAEMON_START_FAILED, "boot_recovery_unavailable")
            }
            val handover = runCatching { plan.root.run(
                "if [ -f '${dev.maplesu.manager.recovery.ManagerRestoreHandover.PATH}' ]; then " +
                    "cat '${dev.maplesu.manager.recovery.ManagerRestoreHandover.PATH}'; fi",
            ) }.getOrNull()
            if (handover == null || handover.code != 0 || handover.output.trim().startsWith("pending:")) {
                return reject(HiddenFailure.VERIFY_FAILED.wire, "manager_restore_confirmation_required")
            }
        }
        return PrecheckOutcome(
            step = step(
                HiddenActivateStep.PRECHECK.wire,
                ok = true,
                detail = "libs=${validated.targets.size};abi=arm64;extractNativeLibs=true",
            ),
            prepared = PreparedHost(
                facts = validated.facts,
                targets = validated.targets,
                label = "",
                manifest = HostManifest(
                    packageName = validated.facts.packageName,
                    libraryDir = validated.facts.nativeLibraryDir,
                    label = "",
                    loaderName = ParasitePaths.LOADER_NAME,
                    loaderSha256 = "",
                    files = emptyList(),
                ),
            ),
        )
    }

    private fun injectHost(
        prepared: PreparedHost,
        plan: HiddenModeDeps,
        rollback: RollbackBook,
    ): HostSoOutcome {
        val facts = prepared.facts
        rollback.libraryDir = facts.nativeLibraryDir
        // 2a. 备份（label 取目录自身；双向 sha 校验）。真实宿主可能有 48 个库：
        // 按 BACKUP_CHUNK 分批跑，最后单独落「完成标记」（半截备份永远不可用于恢复）。
        val shas = mutableMapOf<String, String>()
        var label = ""
        prepared.targets.chunked(ParasitePolicy.BACKUP_CHUNK).forEachIndexed { index, chunk ->
            val backup = runCatching { plan.root.run(ParasiteScripts.backup(facts, chunk, complete = false)) }
                .getOrNull()
                ?: return HostSoOutcome(
                    step(
                        HiddenActivateStep.HOST_SO.wire, false, ParasiteCodes.HOST_BACKUP_FAILED,
                        "backup_exception:chunk=$index",
                    ),
                )
            if (backup.code != 0 || !backup.output.contains(ParasiteMarkers.BACKUP_OK)) {
                return HostSoOutcome(
                    step(
                        HiddenActivateStep.HOST_SO.wire, false, ParasiteCodes.HOST_BACKUP_FAILED,
                        "chunk=$index;${failureDetail(backup)}",
                    ),
                )
            }
            if (label.isBlank()) {
                label = backup.output.lineSequence()
                    .firstOrNull { it.startsWith(ParasiteMarkers.LABEL) }
                    ?.removePrefix(ParasiteMarkers.LABEL)?.trim().orEmpty()
            }
            shas.putAll(parseBackupRecords(backup.output))
        }
        val marker = runCatching { plan.root.run(ParasiteScripts.markBackupComplete(facts.packageName)) }
            .getOrNull() ?: return HostSoOutcome(
            step(HiddenActivateStep.HOST_SO.wire, false, ParasiteCodes.HOST_BACKUP_FAILED, "backup_marker_exception"),
        )
        if (marker.code != 0 || !marker.output.contains(ParasiteMarkers.BACKUP_OK)) {
            return HostSoOutcome(
                step(
                    HiddenActivateStep.HOST_SO.wire, false, ParasiteCodes.HOST_BACKUP_FAILED,
                    failureDetail(marker),
                ),
            )
        }
        rollback.label = label
        val missing = prepared.targets.filter { shas[it].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            return HostSoOutcome(
                step(
                    HiddenActivateStep.HOST_SO.wire, false, ParasiteCodes.HOST_BACKUP_FAILED,
                    "missing_sha=${missing.joinToString(",")}",
                ),
            )
        }

        // 2b. 注入（root 起 app_process 跑 HostSoPatchMain）。先置位：任何尝试后都允许回滚。
        // 真实 App 可能有几十个 `.so`（如 48 个），按 INJECT_CHUNK 分批跑，避免单条 root 命令
        // 过长/超时；清单由执行端增量合并（见 HostSoPatchEngine），这里逐批校验。
        rollback.originalHashes = shas.toMap()
        rollback.hostInjected = true
        val patched = mutableMapOf<String, String>()
        val skipped = mutableSetOf<String>()
        prepared.targets.chunked(ParasitePolicy.INJECT_CHUNK).forEachIndexed { index, chunk ->
            val inject = runCatching {
                plan.root.run(
                    ParasiteScripts.inject(facts, chunk, managerApkPath, loaderSourcePath, label),
                )
            }.getOrNull() ?: return HostSoOutcome(
                step(
                    HiddenActivateStep.HOST_SO.wire, false, ParasiteCodes.HOST_SO_PATCH_FAILED,
                    "inject_exception:chunk=$index",
                ),
            )
            if (inject.code != 0 || !inject.output.contains(ParasiteMarkers.OK)) {
                return HostSoOutcome(
                    step(
                        HiddenActivateStep.HOST_SO.wire, false, ParasiteCodes.HOST_SO_PATCH_FAILED,
                        "chunk=$index;${failureDetail(inject)}",
                    ),
                )
            }
            val chunkPatched = parsePatchedRecords(inject.output)
            val chunkSkipped = parseSkippedRecords(inject.output)
            skipped += chunkSkipped
            val missing = chunk.filter { chunkPatched[it].isNullOrBlank() && it !in chunkSkipped }
            if (missing.isNotEmpty()) {
                return HostSoOutcome(
                    step(
                        HiddenActivateStep.HOST_SO.wire, false, ParasiteCodes.HOST_SO_PATCH_FAILED,
                        "unpatched=${missing.joinToString(",")}",
                    ),
                )
            }
            patched.putAll(chunkPatched)
        }
        if (patched.isEmpty()) {
            // 全部目标都结构不支持 → 注入没发生（loader 永远加载不到），明确失败。
            return HostSoOutcome(
                step(
                    HiddenActivateStep.HOST_SO.wire, false, ParasiteCodes.HOST_SO_PATCH_FAILED,
                    "no_eligible_libs=${skipped.size}",
                ),
            )
        }
        val notPatched = prepared.targets.filter { patched[it].isNullOrBlank() && it !in skipped }
        if (notPatched.isNotEmpty()) {
            return HostSoOutcome(
                step(
                    HiddenActivateStep.HOST_SO.wire, false, ParasiteCodes.HOST_SO_PATCH_FAILED,
                    "unpatched=${notPatched.joinToString(",")}",
                ),
            )
        }
        val manifestText = runCatching { plan.root.run(ParasiteScripts.readManifest(facts.packageName)) }
            .getOrNull()?.output
        val manifest = HostManifestCodec.decode(manifestText)
            ?: return HostSoOutcome(
                step(HiddenActivateStep.HOST_SO.wire, false, ParasiteCodes.HOST_SO_PATCH_FAILED, "manifest_unreadable"),
            )
        if (manifest.libraryDir != facts.nativeLibraryDir || manifest.files.isEmpty()) {
            return HostSoOutcome(
                step(HiddenActivateStep.HOST_SO.wire, false, ParasiteCodes.HOST_SO_PATCH_FAILED, "manifest_mismatch"),
            )
        }
        // 结构上不能注入、被显式跳过的库不进清单，也不该算"清单缺项"。
        val uncovered = prepared.targets.filter { name ->
            name !in skipped && manifest.files.none { it.name == name }
        }
        if (uncovered.isNotEmpty()) {
            // 分批注入下清单必须覆盖全部目标；缺一个都说明执行端没把清单合并好。
            return HostSoOutcome(
                step(
                    HiddenActivateStep.HOST_SO.wire, false, ParasiteCodes.HOST_SO_PATCH_FAILED,
                    "manifest_uncovered=${uncovered.joinToString(",")}",
                ),
            )
        }

        // 2c. su 授权（幂等；失败即回滚，不留「so 已改但拿不到 root」的半隐藏）。
        val suAuth = runCatching { plan.suAuth.grant(facts.packageName) }
            .getOrElse { SuAuthOutcome.Failed(it.javaClass.simpleName) }
        rollback.suGranted = suAuth is SuAuthOutcome.Granted ||
            (suAuth is SuAuthOutcome.Failed && suAuth.rollbackGrant)
        if (!suAuth.ok) {
            return HostSoOutcome(
                step(
                    HiddenActivateStep.HOST_SO.wire, false, ParasiteCodes.SU_AUTH_FAILED,
                    (suAuth as? SuAuthOutcome.Failed)?.detail.orEmpty(),
                ),
            )
        }
        val allowlist = suAuth is SuAuthOutcome.Granted || suAuth is SuAuthOutcome.AlreadyGranted
        if (allowlist && !runCatching { plan.suAuth.isGranted(facts.packageName) }.getOrDefault(false)) {
            return HostSoOutcome(
                step(HiddenActivateStep.HOST_SO.wire, false, ParasiteCodes.SU_AUTH_FAILED, "allowlist_readback"),
            )
        }
        val identityResult = persistIdentity(plan)
        if (plan.mode == WorkMode.LITE && !identityResult.startsWith("identity=ok;")) {
            return HostSoOutcome(step(
                HiddenActivateStep.HOST_SO.wire, false, "MANAGER_RECOVERY_IDENTITY_UNVERIFIED", identityResult,
            ))
        }
        return HostSoOutcome(
            step = step(
                HiddenActivateStep.HOST_SO.wire,
                ok = true,
                detail = "backup=${prepared.targets.size};patched=${manifest.files.size};" +
                    "su_auth=${suAuth.wire()};$identityResult",
            ),
            prepared = PreparedHost(facts, prepared.targets, label, manifest),
        )
    }

    /**
     * 把「撤销授权所需的身份材料」写到 root-only 文件（`recovery/identity.json`，0600）。
     *
     * <p>为什么必须是激活时写：WebUI 一键解除寄生唯一拿不到的东西就是**App 作用域**的
     * androidId（daemon 进程只能读到系统值）⇒ 不落盘就永远卡在 `revoke_su_auth`（真机 D1）。
     * 这一步放在 **su 授权成功之后**：此时「宿主已可被本 App 提权」已成立，撤销步才有意义。
     *
     * <p>调用方在 Ultra 真卸载前要求 identity=ok；缺失或写失败必须回滚宿主改动。
     */
    private fun persistIdentity(plan: HiddenModeDeps): String {
        val material = runCatching { plan.identity() }.getOrNull()
            ?: return "identity=skipped:no_material"
        val run = runCatching { plan.root.run(RecoveryIdentityScripts.write(material)) }.getOrNull()
            ?: return "identity=failed:exception"
        if (run.code != 0 || !run.output.contains(ParasiteMarkers.IDENTITY_OK)) {
            logger("hidden.activate identity write failed code=${run.code}")
            return "identity=failed:write"
        }
        return "identity=ok;uid=${material.uid}"
    }

    private fun startWebUi(
        plan: HiddenModeDeps,
        port: Int,
        token: String,
        rollback: RollbackBook,
        reuseExistingDaemon: Boolean = false,
    ): SimpleOutcome {
        rollback.recoveryUrl = ParasitePaths.webUiUrl(port, token)
        // Replacing a live daemon's classpath APK can break later class/resource loads.
        if (reuseExistingDaemon && managerRemoval.supported(port, token)) {
            val existing = runCatching { bundle.verify() }.getOrNull()
            val sourceHash = runCatching { dev.maplesu.manager.workmode.Hashes.sha256(java.io.File(managerApkPath)) }.getOrNull()
            if (existing?.ok == true && existing.manifest?.packageName == managerPackage &&
                existing.manifest.versionCode == managerVersionCode && existing.manifest.apkSha256 == sourceHash &&
                pingDaemon(port, token)) {
                rollback.bundlePrepared = true
                // Recreate an entry removed by a preceding recovery attempt even if HTTP is alive.
                val wrote = runCatching { plan.root.run(ParasiteScripts.writeStartScript()) }.getOrNull()
                if (wrote?.code != 0 || !wrote.output.contains(ParasiteMarkers.LITE_OK)) {
                    return SimpleOutcome(step(HiddenActivateStep.WEBUI_START.wire, false,
                        ParasiteCodes.DAEMON_START_FAILED, "start_script_write"))
                }
                return SimpleOutcome(step(HiddenActivateStep.WEBUI_START.wire, true,
                    detail = "port=$port;daemon=reused;bundle=verified"))
            }
        }
        // Stop an incompatible daemon before modifying its executable archive.
        val previousStopped = runCatching { plan.root.run(ParasiteScripts.daemonStop()) }.getOrNull()
        if (previousStopped == null || previousStopped.code != 0 ||
            !previousStopped.output.contains(ParasiteMarkers.STOPPED)) {
            return SimpleOutcome(step(HiddenActivateStep.WEBUI_START.wire, false,
                ParasiteCodes.DAEMON_START_FAILED, "previous_daemon_stop_unverified"))
        }
        val config = "webuiPort=$port\ntoken=$token\nworkMode=${plan.mode.name}\n".toByteArray(Charsets.UTF_8)
        val preparedBundle = runCatching {
            bundle.prepare(
                packageName = managerPackage,
                versionCode = managerVersionCode,
                apkSourcePath = managerApkPath,
                webuiConfig = config,
            )
        }.getOrNull()
        if (preparedBundle !is RecoveryPrepareResult.Ok) {
            val code = (preparedBundle as? RecoveryPrepareResult.Failed)?.code.orEmpty()
            return SimpleOutcome(
                step(HiddenActivateStep.WEBUI_START.wire, false, ParasiteCodes.RECOVERY_PREPARE_FAILED, code),
            )
        }
        val verification = runCatching { bundle.verify() }.getOrNull()
        if (verification == null || !verification.ok) {
            return SimpleOutcome(
                step(
                    HiddenActivateStep.WEBUI_START.wire, false, ParasiteCodes.RECOVERY_VERIFY_FAILED,
                    verification?.problems?.joinToString(",").orEmpty(),
                ),
            )
        }
        rollback.bundlePrepared = true
        // 端口可能被**另一个 token** 的旧 daemon 占着（例如先启动过 WebUI 自检）：
        // 先停（pid 文件 + argv 扫描），再起本事务的 daemon；否则第二个 daemon 立即退出 → DAEMON_EXITED。
        val resumed = runCatching {
            plan.root.run(dev.maplesu.manager.liteweb.RecoveryWebUiScripts.resume() + "\necho MAPLESU_WEBUI_RESUME_OK")
        }.getOrNull()
        if (resumed == null || resumed.code != 0 || !resumed.output.contains("MAPLESU_WEBUI_RESUME_OK")) {
            return SimpleOutcome(
                step(HiddenActivateStep.WEBUI_START.wire, false, ParasiteCodes.DAEMON_START_FAILED, "resume_failed"),
            )
        }
        val wrote = runCatching { plan.root.run(ParasiteScripts.writeStartScript()) }.getOrNull()
            ?: return SimpleOutcome(
                step(HiddenActivateStep.WEBUI_START.wire, false, ParasiteCodes.DAEMON_START_FAILED, "start_script_exception"),
            )
        if (wrote.code != 0 || !wrote.output.contains(ParasiteMarkers.LITE_OK)) {
            return SimpleOutcome(
                step(HiddenActivateStep.WEBUI_START.wire, false, ParasiteCodes.DAEMON_START_FAILED, "start_script_write"),
            )
        }
        // 从这里开始 daemon 可能已经起来了 → 失败必须回滚停机。
        rollback.daemonStarted = true
        val started = runCatching { plan.root.run(ParasiteScripts.runStartScript()) }.getOrNull()
            ?: return SimpleOutcome(
                step(HiddenActivateStep.WEBUI_START.wire, false, ParasiteCodes.DAEMON_START_FAILED, "daemon_exception"),
            )
        val alive = started.output.contains(ParasiteMarkers.STARTED) ||
            started.output.contains(ParasiteMarkers.ALIVE)
        if (started.code != 0 || !alive) {
            return SimpleOutcome(
                step(
                    HiddenActivateStep.WEBUI_START.wire, false, ParasiteCodes.DAEMON_START_FAILED,
                    failureDetail(started),
                ),
            )
        }
        if (!managerRemoval.supported(port, token)) {
            return SimpleOutcome(
                step(HiddenActivateStep.WEBUI_START.wire, false, ParasiteCodes.DAEMON_START_FAILED, "daemon_upgrade_required"),
            )
        }
        val daemonState = if (started.output.contains(ParasiteMarkers.ALIVE)) "alive" else "started"
        return SimpleOutcome(
            step(
                HiddenActivateStep.WEBUI_START.wire, true,
                detail = "port=$port;daemon=$daemonState;bundle=verified",
            ),
        )
    }

    private fun pingDaemon(port: Int, token: String): Boolean {
        repeat(PING_ATTEMPTS) { attempt ->
            val ok = runCatching { probe.ping(port, token) }.getOrDefault(false)
            if (ok) return true
            if (attempt < PING_ATTEMPTS - 1) runCatching { pause(PING_BACKOFF_MS) }
        }
        return false
    }

    /**
     * 现有 daemon 可复用吗：读 `webui.conf` 的 token，且**该 token 能 ping 通**才返回它。
     *
     * 用于「用户先启动过 WebUI 自检 daemon，再点开启隐藏」的场景：复用同一个 daemon/token，
     * 既避免端口冲突（第二个 daemon 会立即退出 → DAEMON_EXITED），也保证已复制的链接不失效。
     */
    private fun existingDaemonToken(plan: HiddenModeDeps, port: Int): String? {
        val result = runCatching {
            plan.root.run("cat ${ParasitePaths.WEBUI_CONFIG} 2>/dev/null")
        }.getOrNull() ?: return null
        if (result.code != 0) return null
        val token = result.output.lineSequence()
            .firstOrNull { it.trimStart().startsWith("token=") }
            ?.substringAfter('=')?.trim().orEmpty()
        if (token.length < 16 || !token.all { it.isLetterOrDigit() }) return null
        return token.takeIf { pingDaemon(port, it) }
    }

    private fun writeHiddenState(plan: HiddenModeDeps, rollback: RollbackBook): SimpleOutcome {
        // A write can commit and then lose its acknowledgement.
        rollback.stateWritten = true
        val written = runCatching { plan.state.write(true) }.getOrDefault(false)
        if (!written) {
            return SimpleOutcome(
                step(HiddenActivateStep.HIDE_STATE.wire, false, ParasiteCodes.HIDDEN_STATE_WRITE_FAILED, "write"),
            )
        }
        val readback = runCatching { plan.state.read() }.getOrNull()
        if (readback != true) {
            return SimpleOutcome(
                step(
                    HiddenActivateStep.HIDE_STATE.wire, false, ParasiteCodes.HIDDEN_STATE_READBACK_FAILED,
                    "readback=${readback?.toString() ?: "unknown"}",
                ),
            )
        }
        return SimpleOutcome(step(HiddenActivateStep.HIDE_STATE.wire, true, detail = "state=on"))
    }

    private fun finalVerify(
        prepared: PreparedHost,
        plan: HiddenModeDeps,
        port: Int,
        token: String,
    ): SimpleOutcome? {
        val manifest = prepared.manifest
        if (manifest.files.isNotEmpty() && manifest.loaderSha256.length == 64) {
            val verify = runCatching { plan.root.run(ParasiteScripts.verifyEmbedded(manifest)) }.getOrNull()
            if (verify == null || verify.code != 0 || !verify.output.contains(ParasiteMarkers.VERIFY_OK)) {
                return SimpleOutcome(
                    step(
                        HiddenActivateStep.FINAL_VERIFY.wire, false, ParasiteCodes.VERIFY_FAILED,
                        verify?.let { failureDetail(it) } ?: "verify_exception",
                    ),
                )
            }
        }
        if (plan.mode == WorkMode.LITE) {
            val nonce = java.util.UUID.randomUUID().toString().replace("-", "")
            val started = runCatching {
                plan.root.run(ParasiteScripts.hostStartupCheck(prepared.facts, nonce))
            }.getOrNull()
            if (started == null || started.code != 0 || !started.output.lineSequence().any { it == ParasiteMarkers.HOST_STARTED }) {
                return SimpleOutcome(step(
                    HiddenActivateStep.FINAL_VERIFY.wire, false, ParasiteCodes.HOST_START_UNVERIFIED,
                    "host_start=unverified;manager=retained",
                ))
            }
        }
        if (!pingDaemon(port, token)) {
            return SimpleOutcome(
                step(HiddenActivateStep.FINAL_VERIFY.wire, false, ParasiteCodes.DAEMON_PING_FAILED, "final_ping"),
            )
        }
        if (runCatching { plan.state.read() }.getOrNull() != true) {
            return SimpleOutcome(
                step(HiddenActivateStep.FINAL_VERIFY.wire, false, ParasiteCodes.HIDDEN_STATE_READBACK_FAILED, "final_readback"),
            )
        }
        return null
    }

    private class RestoreOutcome(val step: HiddenStepResult, val verified: Boolean)

    private fun restoreHost(host: String, plan: HiddenModeDeps): RestoreOutcome {
        val exists = runCatching { plan.root.run(ParasiteScripts.backupDirExists(host)) }.getOrNull()
            ?: return RestoreOutcome(
                step(HiddenDeactivateStep.RESTORE_HOST_SO.wire, false, ParasiteCodes.HOST_SO_RESTORE_FAILED, "backup_probe"),
                verified = false,
            )
        if (exists.code != 0) {
            return RestoreOutcome(
                step(HiddenDeactivateStep.RESTORE_HOST_SO.wire, false, ParasiteCodes.HOST_SO_RESTORE_FAILED, "backup_probe"),
                verified = false,
            )
        }
        if (exists.output.contains("PARASITE_NO")) {
            return RestoreOutcome(
                step(HiddenDeactivateStep.RESTORE_HOST_SO.wire, true, detail = "no_backup"),
                verified = false,
            )
        }
        val libDir = runCatching { plan.root.run(ParasiteScripts.readLibDir(host)) }
            .getOrNull()?.output?.trim().orEmpty()
        if (!ParasitePolicy.isAbsolutePath(libDir)) {
            return RestoreOutcome(
                step(HiddenDeactivateStep.RESTORE_HOST_SO.wire, false, ParasiteCodes.HOST_SO_RESTORE_FAILED, "libdir_unreadable"),
                verified = false,
            )
        }
        val label = runCatching { plan.root.run(ParasiteScripts.readLabel(host)) }
            .getOrNull()?.output?.trim().orEmpty()
        val restore = runCatching { plan.root.run(ParasiteScripts.restore(host, libDir, label)) }.getOrNull()
            ?: return RestoreOutcome(
                step(HiddenDeactivateStep.RESTORE_HOST_SO.wire, false, ParasiteCodes.HOST_SO_RESTORE_FAILED, "restore_exception"),
                verified = false,
            )
        if (restore.code != 0 || !restore.output.contains(ParasiteMarkers.RESTORE_OK)) {
            return RestoreOutcome(
                step(
                    HiddenDeactivateStep.RESTORE_HOST_SO.wire, false, ParasiteCodes.HOST_SO_RESTORE_FAILED,
                    failureDetail(restore),
                ),
                verified = false,
            )
        }
        val restoredCount = restore.output.lineSequence().count {
            it.startsWith(ParasiteMarkers.RESTORED) || it.startsWith(ParasiteMarkers.RESTORE_SKIP)
        }
        if (restoredCount == 0) {
            return RestoreOutcome(
                step(HiddenDeactivateStep.RESTORE_HOST_SO.wire, false, ParasiteCodes.HOST_SO_RESTORE_FAILED, "no_file_restored"),
                verified = false,
            )
        }
        return RestoreOutcome(
            step(HiddenDeactivateStep.RESTORE_HOST_SO.wire, true, detail = "restored=$restoredCount"),
            verified = true,
        )
    }

    private fun revokeSuAuth(host: String, plan: HiddenModeDeps): SimpleOutcome {
        if (plan.mode == WorkMode.LITE && runCatching { plan.suAuth.isGranted(host) }.getOrNull() == false) {
            return SimpleOutcome(step(HiddenDeactivateStep.REVOKE_SU_AUTH.wire, true, detail = "su_auth=already_absent"))
        }
        val outcome = runCatching { plan.suAuth.revoke(host) }
            .getOrElse { SuAuthOutcome.Failed(it.javaClass.simpleName) }
        if (!outcome.ok) {
            return SimpleOutcome(
                step(
                    HiddenDeactivateStep.REVOKE_SU_AUTH.wire, false, ParasiteCodes.SU_AUTH_REVOKE_FAILED,
                    (outcome as? SuAuthOutcome.Failed)?.detail.orEmpty(),
                ),
            )
        }
        if (outcome is SuAuthOutcome.Granted || outcome is SuAuthOutcome.AlreadyGranted ||
            outcome is SuAuthOutcome.Revoked
        ) {
            if (runCatching { plan.suAuth.isGranted(host) }.getOrDefault(true)) {
                return SimpleOutcome(
                    step(HiddenDeactivateStep.REVOKE_SU_AUTH.wire, false, ParasiteCodes.SU_AUTH_REVOKE_FAILED, "still_listed"),
                )
            }
        }
        return SimpleOutcome(
            step(HiddenDeactivateStep.REVOKE_SU_AUTH.wire, true, detail = "su_auth=${outcome.wire()}"),
        )
    }

    private fun stopDaemonAndClearState(plan: HiddenModeDeps): SimpleOutcome {
        val previous = runCatching { plan.state.read() }.getOrNull()
        val cleared = runCatching { plan.state.write(false) }.getOrDefault(false)
        val off = runCatching { plan.state.read() }.getOrNull()
        if (!cleared || off != false) {
            if (previous == true) runCatching { plan.state.write(true) }
            return SimpleOutcome(step(HiddenDeactivateStep.STOP_DAEMON.wire, false,
                ParasiteCodes.HIDDEN_STATE_WRITE_FAILED, "state_off_unverified;recovery=retained"))
        }
        val stop = runCatching { plan.root.run(ParasiteScripts.daemonStop()) }.getOrNull()
        // 两个条件都要：脚本 exit 0（自证失败会 exit 1）**且** 输出成功标记。
        // 脚本内部已经验过"进程消失 + 端口不再 LISTEN"，失败时输出 `PARASITE_FAIL code=…`。
        // ← 2026-10-31 真机缺陷 1：旧脚本无条件 echo STOPPED，所以只看标记会得到假成功。
        val stoppedMarker = stop?.code == 0 && stop.output.contains(ParasiteMarkers.STOPPED)
        if (!stoppedMarker) {
            if (previous == true) runCatching { plan.state.write(true) }
            return SimpleOutcome(
                step(
                    HiddenDeactivateStep.STOP_DAEMON.wire,
                    false,
                    ParasiteCodes.DAEMON_STOP_FAILED,
                    "stop=${stop?.let(::failureDetail) ?: "stop_exception"};recovery=retained",
                ),
            )
        }
        return SimpleOutcome(
            step(HiddenDeactivateStep.STOP_DAEMON.wire, true, detail = "daemon=stopped;state=off"),
        )
    }

    // ── 失败/回滚 ──────────────────────────────────────────────────────────

    private class RollbackBook(val plan: HiddenModeDeps, val host: String) {
        var hostInjected: Boolean = false
        var suGranted: Boolean = false
        var bundlePrepared: Boolean = false
        var daemonStarted: Boolean = false
        var stateWritten: Boolean = false
        var libraryDir: String = ""
        var label: String = ""
        var recoveryUrl: String = ""
        var keepDaemon: Boolean = false
        var originalHashes: Map<String, String> = emptyMap()

        /** Restore side effects first; stop recovery only after every rollback is verified. */
        fun undo(): String {
            // 一个副作用都还没写（典型是 precheck 直接拒绝）→ 没有回滚动作，也就没有回滚结论。
            // 此时绝不能把 `rollback=ok` 混进失败原因，否则等于谎报做过一次复原验证。
            if (!stateWritten && !daemonStarted && !suGranted && !hostInjected) return ""
            val problems = mutableListOf<String>()
            if (stateWritten) {
                runCatching { plan.state.write(false) }
                if (runCatching { plan.state.read() }.getOrNull() != false) problems += "state_off"
            }
            if (suGranted) {
                val revoked = runCatching { plan.suAuth.revoke(host) }
                    .getOrElse { SuAuthOutcome.Failed("exception") }
                if (!revoked.ok || runCatching { plan.suAuth.isGranted(host) }.getOrDefault(true)) {
                    problems += "su_revoke"
                }
            }
            if (hostInjected) {
                val restored = runCatching {
                    plan.root.run(ParasiteScripts.restore(host, libraryDir, label, originalHashes))
                }.getOrNull()
                if (restored == null || restored.code != 0 ||
                    !restoresExpectedOriginals(restored.output, originalHashes)
                ) {
                    problems += "host_so_restore"
                }
            }
            if (problems.isEmpty()) {
                if (daemonStarted && !keepDaemon) {
                    val stopped = runCatching { plan.root.run(ParasiteScripts.daemonStop()) }.getOrNull()
                    if (stopped == null || stopped.code != 0 || !stopped.output.contains(ParasiteMarkers.STOPPED)) {
                        problems += "daemon_stop"
                    }
                }
            }
            if (problems.isEmpty()) {
                if (hostInjected) {
                    // Retire atomically instead of recursively deleting the only originals.
                    // keepDaemon / a pre-existing grant are not full deactivation facts.
                    val nonce = java.util.UUID.randomUUID().toString().replace("-", "")
                    fun verified(result: RecoveryCommandResult?): Boolean = result?.code == 0 &&
                        result.output.lineSequence().any { it == ParasiteMarkers.BACKUP_RETIRED }
                    val retired = runCatching {
                        plan.root.run(ParasiteScripts.retireRestoredBackup(host, originalHashes, nonce))
                    }.getOrNull()
                    if (!verified(retired)) {
                        val observed = runCatching {
                            plan.root.run(ParasiteScripts.verifyRetiredBackup(host, originalHashes, nonce))
                        }.getOrNull()
                        if (!verified(observed)) problems += "backup_retire"
                    }
                }
                if (problems.isEmpty()) return "rollback=ok"
            }
            return "rollback=incomplete(${problems.joinToString(",")})"
        }
    }

    private fun failed(
        steps: List<HiddenStepResult>,
        code: String,
        detail: String,
        host: String,
        rollback: RollbackBook,
    ): HiddenActivateResult {
        val rollbackNote = rollback.undo()
        val rollbackIncomplete = rollbackNote.contains("incomplete")
        val stepsOut = steps.toMutableList()
        if (stepsOut.isNotEmpty()) {
            val last = stepsOut[stepsOut.lastIndex]
            stepsOut[stepsOut.lastIndex] = last.copy(
                ok = false,
                code = last.code.ifEmpty { code },
                detail = merge(last.detail, detail, rollbackNote),
            )
        } else {
            stepsOut += HiddenStepResult(
                HiddenActivateStep.PRECHECK.wire, ok = false, code = code, detail = merge(detail, rollbackNote),
            )
        }
        val finalCode = if (rollbackIncomplete) ParasiteCodes.ROLLBACK_INCOMPLETE else code
        if (rollbackIncomplete) logger("hidden.activate rollback_incomplete code=$code")
        // 失败必须留痕：UI 只显示短文案，真正的步骤/码/细节进日志（不含 token/凭据）。
        val failedStep = stepsOut.lastOrNull()
        logger(
            "hidden.activate failed host=$host code=$code " +
                "step=${failedStep?.step ?: "-"} step_code=${failedStep?.code ?: "-"} " +
                "detail=${failedStep?.detail ?: "-"}",
        )
        return HiddenActivateResult(
            success = false,
            failure = failureOf(if (rollbackIncomplete) ParasiteCodes.HOST_SO_PATCH_FAILED else code),
            code = finalCode,
            hostPackage = host,
            steps = stepsOut,
            recoveryUrl = if ((rollbackIncomplete || rollback.keepDaemon) && rollback.bundlePrepared) rollback.recoveryUrl else "",
        )
    }

    // 失败原因按段拼接：同一原因（例如 precheck 的 detail 与失败 code 复述同一个 marker）
    // 只报一次，避免 `not_installed;not_installed` 这类重复把真正的信息冲淡。
    private fun merge(vararg parts: String): String =
        parts.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(";")

    private fun failureDetail(result: RecoveryCommandResult): String {
        val marker = result.output.lineSequence().firstOrNull { it.contains(ParasiteMarkers.FAIL) }
        val code = marker?.let { Regex("code=([A-Za-z0-9_]+)").find(it)?.groupValues?.get(1) }.orEmpty()
        val detail = marker?.let { Regex("detail=([^\\s]+)").find(it)?.groupValues?.get(1) }.orEmpty()
        return listOf(code.ifEmpty { "exit=${result.code}" }, detail)
            .filter { it.isNotEmpty() }
            .joinToString(":")
    }

    private fun parseBackupRecords(output: String): Map<String, String> = buildMap {
        output.lineSequence().filter { it.startsWith(ParasiteMarkers.BACKUP) }.forEach { line ->
            val name = Regex("name=([^\\s]+)").find(line)?.groupValues?.get(1) ?: return@forEach
            val sha = Regex("sha=([0-9a-f]{64})").find(line)?.groupValues?.get(1) ?: return@forEach
            put(name, sha)
        }
    }

    private fun parsePatchedRecords(output: String): Map<String, String> = buildMap {
        output.lineSequence().filter { it.startsWith(ParasiteMarkers.PATCHED) }.forEach { line ->
            val name = Regex("name=([^\\s]+)").find(line)?.groupValues?.get(1) ?: return@forEach
            val sha = Regex("patched=([0-9a-f]{64})").find(line)?.groupValues?.get(1) ?: return@forEach
            put(name, sha)
        }
    }

    /** 执行端明确跳过的库（结构上无法注入）；这些不进清单，也不需要复核。 */
    private fun parseSkippedRecords(output: String): Set<String> = buildSet {
        output.lineSequence().filter { it.startsWith(ParasiteMarkers.SKIP) }.forEach { line ->
            val name = Regex("name=([^\\s]+)").find(line)?.groupValues?.get(1) ?: return@forEach
            add(name)
        }
    }

    private companion object {
        // All instances share the recovery bundle, hidden state and daemon lifecycle.
        val transactionActive = AtomicBoolean(false)
        const val PING_ATTEMPTS = 3
        const val PING_BACKOFF_MS = 250L
        const val STALE_BACKUP_MARKER = "PARASITE_STALE_BACKUP"

        fun restoresExpectedOriginals(output: String, expected: Map<String, String>): Boolean {
            if (expected.isEmpty()) return false
            val lines = output.lineSequence().toList()
            val records = lines.filter { it.startsWith(ParasiteMarkers.RESTORED) || it.startsWith(ParasiteMarkers.RESTORE_SKIP) }
            val pattern = Regex("PARASITE_(?:RESTORED|RESTORE_SKIP) name=([A-Za-z0-9._-]+) sha=([0-9a-f]{64})")
            val parsed = records.map { pattern.matchEntire(it) ?: return false }
            if (parsed.size != expected.size || parsed.map { it.groupValues[1] }.distinct().size != parsed.size) return false
            if (parsed.any { expected[it.groupValues[1]] != it.groupValues[2] }) return false
            return lines.count { it == "${ParasiteMarkers.RESTORE_OK} count=${expected.size}" } == 1
        }

        fun failureOf(code: String): HiddenFailure = when (code) {
            ParasiteCodes.HOST_UNSUPPORTED,
            ParasiteCodes.HOST_BACKUP_STALE,
            -> HiddenFailure.HOST_REJECTED

            ParasiteCodes.HOST_SCAN_FAILED -> HiddenFailure.HOST_SCAN_FAILED
            HiddenFailure.ROOT_UNAVAILABLE.wire -> HiddenFailure.ROOT_UNAVAILABLE
            ParasiteCodes.SU_AUTH_FAILED -> HiddenFailure.ROOT_UNAVAILABLE
            ParasiteCodes.SU_AUTH_REVOKE_FAILED,
            ParasiteCodes.HOST_BACKUP_CLEANUP_FAILED,
            ParasiteCodes.IDENTITY_CLEANUP_FAILED,
            ParasiteCodes.CLEANUP_CHECKPOINT_FAILED,
            -> HiddenFailure.DEACTIVATE_FAILED

            ParasiteCodes.HOST_BACKUP_FAILED -> HiddenFailure.HOST_BACKUP_FAILED

            ParasiteCodes.HOST_SO_PATCH_FAILED,
            ParasiteCodes.HOST_SO_RESTORE_FAILED,
            -> HiddenFailure.HOST_SO_PATCH_FAILED

            ParasiteCodes.DAEMON_START_FAILED,
            ParasiteCodes.DAEMON_PING_FAILED,
            -> HiddenFailure.WEBUI_NOT_STARTED

            ParasiteCodes.RECOVERY_PREPARE_FAILED,
            ParasiteCodes.RECOVERY_VERIFY_FAILED,
            ParasiteCodes.HIDDEN_STATE_WRITE_FAILED,
            ParasiteCodes.HIDDEN_STATE_READBACK_FAILED,
            ParasiteCodes.VERIFY_FAILED,
            ParasiteCodes.HOST_START_UNVERIFIED,
            "MANAGER_HIDE_NOT_VERIFIED",
            ParasiteCodes.ROLLBACK_INCOMPLETE,
            -> HiddenFailure.VERIFY_FAILED

            "MANAGER_FULL_RESTORE_UNAVAILABLE",
            "MANAGER_RECOVERY_NOT_VERIFIED",
            -> HiddenFailure.RECOVERY_NOT_READY
            "MANAGER_UNINSTALL_UNVERIFIED",
            "MANAGER_UNINSTALL_FAILED",
            "MANAGER_UNINSTALL_UNAVAILABLE",
            -> HiddenFailure.MANAGER_REMOVAL_FAILED

            HiddenFailure.INVALID_PORT.wire -> HiddenFailure.INVALID_PORT
            else -> HiddenFailure.UNKNOWN
        }
    }
}
