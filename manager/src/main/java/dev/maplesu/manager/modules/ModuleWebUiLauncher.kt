package dev.maplesu.manager.modules

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.maplesu.core.R
import com.maplesu.core.data.model.Module
import com.maplesu.core.ksuApp
import com.maplesu.core.ui.navigation3.WebUiOpenOrigin
import com.maplesu.core.ui.navigation3.WebUiOpenPolicy
import com.maplesu.core.ui.navigation3.WebUiOpenRequest
import com.maplesu.core.ui.webui.ModuleWebUIActivity
import dev.maplesu.manager.ModuleArchiveInspector.ModuleType
import dev.maplesu.manager.liteweb.LiteWebProxyBase
import dev.maplesu.manager.modules.compat.UltraModuleAction
import dev.maplesu.manager.modules.compat.UltraModuleCompatibilityGate
import dev.maplesu.manager.modules.compat.UltraModuleDeviceFacts
import dev.maplesu.manager.modules.compat.UltraModuleEvent
import dev.maplesu.manager.modules.compat.UltraModuleEvidenceRecordResult
import dev.maplesu.manager.modules.compat.UltraModuleGateEvidenceLog
import dev.maplesu.manager.modules.compat.UltraModuleOutcome
import dev.maplesu.manager.modules.compat.UltraModulePhase
import dev.maplesu.manager.modules.compat.UltraModuleProcessRole
import dev.maplesu.manager.modules.webui.HttpReadyProbe
import dev.maplesu.manager.modules.webui.HttpReadyResult
import dev.maplesu.manager.modules.webui.ModuleWebUiPlan
import dev.maplesu.manager.modules.webui.ModuleWebUiSessions
import dev.maplesu.manager.modules.webui.SdkOpenOutcome
import dev.maplesu.manager.modules.webui.SdkOpenRunner
import dev.maplesu.manager.modules.webui.SingleFlight
import dev.maplesu.manager.modules.webui.WebUiCompatibilityCheck
import dev.maplesu.manager.modules.webui.WebUiFailure
import dev.maplesu.manager.modules.webui.WebUiFailureReason
import dev.maplesu.manager.modules.webui.WebUiGateVerdict
import dev.maplesu.manager.modules.webui.WebUiLifecycleStage
import dev.maplesu.manager.modules.webui.WebUiOpenCoordinator
import dev.maplesu.manager.modules.webui.WebUiOpenEvent
import dev.maplesu.manager.modules.webui.WebUiPageOpener
import dev.maplesu.manager.modules.webui.WebUiOpenResult
import dev.maplesu.manager.modules.webui.WebUiOpenSpec
import dev.maplesu.manager.modules.webui.WebUiRoute
import dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe
import dev.maplesu.manager.runtime.ultra.UltraEnvironmentAutoSetup
import dev.maplesu.manager.runtime.ultra.UltraSdkCredentials
import dev.maplesu.manager.soter.SoterLane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import dev.maplesu.manager.workmode.ModeRuntime
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Instant

object ModuleWebUiLauncher {

    const val TAG = "MapleSUWebUi"

    /** 环境模块在模块列表里的 id 前缀；真正的 id32 是它后面那段。 */
    const val ULTRA_ID_PREFIX = ModuleWebUiPlan.ULTRA_ID_PREFIX

    /** 探活总预算：loader 冷启动在低端机上并不快。 */
    private const val READY_DEADLINE_MS = 9_000L
    private const val PROBE_TIMEOUT_MS = 400
    private const val PROBE_INTERVAL_MS = 180L
    private const val MODULE_INFO_SEED_MAX_CHARS = 8 * 1024

    /** 与 `MapleSuModuleBackend` 相同的 catalog 根目录（安装事务写的安装记录在这里）。 */
    private const val CATALOG_DIR = "module-catalog"

    /** 与 `MapleSuModuleBackend` 相同的证据文件：逐行 JSONL，只 append。 */
    private const val EVIDENCE_FILE = "ultra-module-evidence.jsonl"

    /** 安装记录里取不到 Report 时的稳定原因码（证据不足，不拦）。 */
    private const val CATALOG_REPORT_MISSING = "CATALOG_REPORT_MISSING"

    /**
     * 单飞状态必须是对象级（跨调用复用）：每次 `openDetailed` 新建一个就退化成
     * 「只合并同一实例内的请求」，连续点击仍会各自启动 SDK。
     */
    private val openFlights = SingleFlight<String, WebUiOpenResult>()

    fun isUltraModuleId(id: String): Boolean = ModuleWebUiPlan.isUltraModuleId(id)

    /** `maplesu:<id32>` → `<id32>`；不是环境模块就原样返回，绝不猜。 */
    fun id32Of(id: String): String = ModuleWebUiPlan.moduleId32(id)

    /**
     * 已验证的 Ultra 模块保留打开入口；临时探测失败由下一次点击实时重试。
     * 这里只读缓存和配对身份，不在列表布局时调用 SDK。
     */
    fun isAvailable(module: Module, context: Context = ksuApp): Boolean =
        isUltraModuleId(module.id) && module.hasWebUi &&
            (UltraEnvironmentProbe.currentSnapshot(context).state == UltraEnvironmentProbe.State.Running ||
                UltraEnvironmentProbe.usesUltraBackend(context))

    /**
     * 打开模块 WebUI。
     *
     * [origin] 不是装饰：只有 [WebUiOpenOrigin.USER_CLICK]（模块列表里用户点的那个按钮）
     * 能走到这里。后台/进程启动/安装回执/无法判定的调用一律 fail-safe 返回 `null`，
     * 不拉起页面、也不启动模块服务。
     *
     * @return `null` 表示请求已按当前基线完成（Ultra 路由由 SDK 打开页面；liteweb 路由由
     *         MapleSU 拉起页面），或请求不是显式用户动作、什么都没做；否则是可直接展示给
     *         用户的一句话（含失败 code）。
     */
    suspend fun open(
        context: Context,
        moduleId: String,
        origin: WebUiOpenOrigin,
    ): String? {
        val failure = openDetailed(context, moduleId, origin) ?: return null
        return context.applicationContext.getString(
            R.string.module_webui_open_failed,
            failure.describe(),
        )
    }

    /**
     * 同 [open]，但返回结构化失败 —— 页面用对话框展示 code + 明细 + 重试，而不是一闪而过的 Toast。
     *
     * 新增参数全部带默认值（仓库既有先例：`litewebBaseProvider`），所以 `ModuleScreen` 的
     * 既有调用点不需要改签名：
     *  - [sdkOpenRunner] / [httpReadyProbe] / [compatibilityCheck]：§5.2 要求的测试注入点。
     *    默认实现才调用 `UltraRuntimeClient` / 真实 socket / 兼容门；测试注入计数替身后，
     *    本函数不碰 Android 运行时之外的系统状态，也不改 PATH。
     *  - [evidenceSink]：`UltraModuleEvent` 的落点（默认写 `filesDir/ultra-module-evidence.jsonl`）。
     *  - [probeBudgetMillis] / [probeIntervalMillis]：探活节奏（默认 9s / 180ms），只为了让
     *    测试不必真等 9 秒；生产不传。
     */
    suspend fun openDetailed(
        context: Context,
        moduleId: String,
        origin: WebUiOpenOrigin,
        moduleName: String = "",
        litewebBaseProvider: (Context) -> String? = { LiteWebProxyBase.current(it) },
        sdkOpenRunner: SdkOpenRunner? = null,
        httpReadyProbe: HttpReadyProbe? = null,
        compatibilityCheck: WebUiCompatibilityCheck? = null,
        evidenceSink: ((UltraModuleEvent) -> Unit)? = null,
        probeBudgetMillis: Long = READY_DEADLINE_MS,
        probeIntervalMillis: Long = PROBE_INTERVAL_MS,
        environmentProbe: (Context) -> Boolean = {
            // Reuse the observed state without adding a foreground root query.
            // Backend identity alone does not prove the SDK environment started.
            if (UltraEnvironmentProbe.usesUltraBackend(it))
                UltraEnvironmentProbe.currentSnapshot(it).state == UltraEnvironmentProbe.State.Running
            else UltraEnvironmentProbe.refresh(it, force = false) == UltraEnvironmentProbe.State.Running
        },
        moduleInfo: Module? = null,
    ): WebUiFailure? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val openContext = currentCoroutineContext()
        val activation = ModeRuntime.activation
        if (!WebUiOpenPolicy.mayNavigate(WebUiOpenRequest(moduleId, origin, tokenValid = true))) {
            return@withContext null
        }

        val id32 = ModuleWebUiPlan.moduleId32OrNull(moduleId)
            ?: return@withContext fail(WebUiFailureReason.MODULE_ID_INVALID, moduleId)

        val moduleInfoJson = moduleInfo?.takeIf { it.id == moduleId }?.webUiInfoJson
        val isPro = ModuleWebUiPlan.isUltraModuleId(moduleId)
        val running = if (isPro) runCatching { environmentProbe(app) }.getOrDefault(false)
        else UltraEnvironmentProbe.currentSnapshot(app).state == UltraEnvironmentProbe.State.Running
        val liteBase = if (!running) runCatching { litewebBaseProvider(app) }.getOrNull() else null
        val route = ModuleWebUiPlan.routeFor(
            hasWebUi = true,
            isUltraModule = isPro,
            ultraEnvironmentRunning = running,
            litewebBaseUrl = liteBase,
            moduleEnabled = true,
        )
        Log.i(TAG, "open module=$id32 route=$route envRunning=$running liteBase=${liteBase != null}")

        val sessionId = if (route == WebUiRoute.ULTRA_SDK) "maplesu:$id32" else id32
        val sessionRevision = ModuleWebUiSessions.revision(sessionId)
        val ownerScope = UltraEnvironmentProbe.webUiOwnerScope(app)
        val sink = evidenceSink ?: defaultEvidenceSink(app)
        val coordinator = WebUiOpenCoordinator(
            sdkOpen = SdkOpenRunner { target ->
                openContext.ensureActive()
                if (ModeRuntime.activation !== activation) SdkOpenOutcome(false, errorCode = "SDK_CREDENTIAL_SCOPE_CHANGED")
                else (sdkOpenRunner ?: defaultSdkOpenRunner(app)).open(target)
            },
            probe = httpReadyProbe ?: defaultHttpReadyProbe(probeBudgetMillis, probeIntervalMillis),
            gate = compatibilityCheck ?: defaultCompatibilityCheck(app),
            onStage = { event -> recordStage(sink, event) },
            flights = openFlights,
            pageOpen = { ready ->
                val url = ModuleWebUiPlan.loopbackUrl(ready.port)
                val currentOwner = UltraEnvironmentProbe.webUiOwnerScope(app)
                if (ModeRuntime.activation !== activation || (ownerScope != null && currentOwner != ownerScope)) {
                    fail(WebUiFailureReason.SDK_REJECTED, "SDK_CREDENTIAL_SCOPE_CHANGED")
                } else if (url == null) fail(WebUiFailureReason.PORT_MISSING, "port=${ready.port}")
                else launch(app, url, id32, moduleName, route, ready.moduleDir, moduleInfoJson, sessionRevision, currentOwner)
            },
            prepareEnvironment = if (!isPro || running) null else suspend {
                if (UltraEnvironmentProbe.isSafeMode(app)) {
                    fail(WebUiFailureReason.ENVIRONMENT_NOT_RUNNING, "ULTRA_SAFE_MODE_ACTIVE")
                } else {
                    val prepared = UltraEnvironmentAutoSetup.ensure(app) {
                        openContext.isActive && ModeRuntime.activation === activation
                    }
                    openContext.ensureActive()
                    if (prepared.installed) null
                    else fail(WebUiFailureReason.ENVIRONMENT_NOT_RUNNING, prepared.reason)
                }
            },
        )

        when (route) {
            WebUiRoute.ULTRA_SDK -> {
                val result = coordinator.open(WebUiOpenSpec(id32, route))
                val failure = result.failure
                if (failure != null) return@withContext failure
                if (result.opener == WebUiPageOpener.SDK) recordStage(
                    sink,
                    WebUiOpenEvent(
                        stage = WebUiLifecycleStage.PAGE_OPENED_BY_SDK,
                        operationId = result.operationId,
                        moduleId32 = id32,
                        route = route,
                        detail = "port=${result.port} opener=sdk",
                    ),
                )
                // 用户显式打开了本模块的 WebUI：把已就绪的端口交给 SOTER lane 只读使用。
                // 这样 lane 不需要（也绝不允许）在进程启动时自己 start_module_web_ui_server_async。
                runCatching { SoterLane.onExplicitWebUiOpened(id32, result.port) }
                null
            }

            WebUiRoute.LITEWEB_MODWEB -> {
                val base = liteBase
                    ?: return@withContext fail(WebUiFailureReason.PROXY_UNAVAILABLE, "no liteweb base")
                val url = ModuleWebUiPlan.modWebUrl(base, id32)
                    ?: return@withContext fail(WebUiFailureReason.PROXY_UNAVAILABLE, "base rejected")
                val port = portOf(url)
                val result = coordinator.open(WebUiOpenSpec(id32, route, port))
                val failure = result.failure
                if (failure != null) return@withContext failure
                val launched = launch(app, url, id32, moduleName, route, moduleInfoJson = moduleInfoJson,
                    sessionRevision = sessionRevision, ownerScope = ownerScope)
                recordStage(
                    sink,
                    WebUiOpenEvent(
                        stage = if (launched == null) {
                            WebUiLifecycleStage.ACTIVITY_LAUNCHED
                        } else {
                            WebUiLifecycleStage.ACTIVITY_LAUNCH_FAILED
                        },
                        operationId = result.operationId,
                        moduleId32 = id32,
                        route = route,
                        detail = "port=$port opener=maplesu",
                        failure = launched,
                    ),
                )
                launched
            }

            WebUiRoute.KSU_ASSET_WEBROOT ->
                fail(WebUiFailureReason.PROXY_UNAVAILABLE, "script module WebUI uses WebUIActivity")

            WebUiRoute.UNSUPPORTED ->
                fail(
                    if (isPro) WebUiFailureReason.ENVIRONMENT_NOT_RUNNING else WebUiFailureReason.PROXY_UNAVAILABLE,
                    "state=${UltraEnvironmentProbe.currentSnapshot(app).state}",
                )
        }
    }

    // ------------------------------------------------------------------ routes

    private suspend fun launch(
        app: Context,
        url: String,
        id32: String,
        moduleName: String,
        route: WebUiRoute,
        moduleDir: String? = null,
        moduleInfoJson: String? = null,
        sessionRevision: Long,
        ownerScope: ModuleWebUiSessions.OwnerScope?,
    ): WebUiFailure? {
        currentCoroutineContext().ensureActive()
        val intent = Intent(app, ModuleWebUIActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ModuleWebUIActivity.EXTRA_URL, url)
            .putExtra(ModuleWebUIActivity.EXTRA_MODULE_ID, if (route == WebUiRoute.ULTRA_SDK) "maplesu:$id32" else id32)
            .putExtra(ModuleWebUIActivity.EXTRA_TITLE, moduleName)
            .putExtra(ModuleWebUIActivity.EXTRA_MODULE_DIR, moduleDir)
            .putExtra(ModuleWebUIActivity.EXTRA_SESSION_REVISION, sessionRevision)
        ownerScope?.let {
            intent.putExtra(ModuleWebUIActivity.EXTRA_OWNER_UID, it.fullUid)
                .putExtra(ModuleWebUIActivity.EXTRA_OWNER_EPOCH, it.epoch)
                .putExtra(ModuleWebUIActivity.EXTRA_OWNER_HOST, it.hostInstance)
        }
        moduleInfoJson?.takeIf { it.length <= MODULE_INFO_SEED_MAX_CHARS }?.let {
            intent.putExtra(ModuleWebUIActivity.EXTRA_MODULE_INFO, it)
        }
        val started = runCatching { app.startActivity(intent) }.isSuccess
        if (!started) return fail(WebUiFailureReason.ACTIVITY_UNRESOLVED, "ModuleWebUIActivity")
        Log.i(TAG, "opened module=$id32 route=$route url=${safeUrl(url)}")
        return null
    }

    // ------------------------------------------------------- P0 兼容门（OPEN_WEBUI 动作）

    /**
     * 默认兼容门：用安装记录里的 `Report` 身份 + 本机设备事实判 `OPEN_WEBUI`。
     *
     * 输入来源：
     *  - `Report`：`ModuleCatalog(filesDir/module-catalog).get(MAPLESU, id32)` —— 安装时留下的
     *    记录（`readEntry` 会重新 inspect 归档，所以 `nativePayloadSha256` 也在）；
     *  - 设备事实：`UltraModuleDeviceFacts.read(client)`（内核 release 读 procfs，SDK 版本走只读
     *    `get_sdk_version` 探针，**不是**模块执行入口）。
     *
     * 取不到时：**证据不足**（`EVIDENCE_GAP`），不拦，但原因码写进日志/证据；不把「没有记录」
     * 当成「已确认不兼容」，也不当成「已兼容」。
     *
     * 判定器自己抛异常同样按证据不足处理：门坏了不能让打开动作崩溃，也不能被读成放行已核实。
     */
    private fun defaultCompatibilityCheck(app: Context): WebUiCompatibilityCheck =
        WebUiCompatibilityCheck { id32 ->
            runCatching { compatibilityVerdict(app, id32) }.getOrElse { error ->
                Log.w(TAG, "webui gate failed module=$id32 class=${error.javaClass.simpleName}")
                WebUiGateVerdict(
                    blocked = false,
                    decision = WebUiGateVerdict.DECISION_EVIDENCE_GAP,
                    reasonCodes = listOf("GATE_EVALUATION_FAILED"),
                    detail = "gate=error class=${error.javaClass.simpleName}",
                )
            }
        }

    private fun compatibilityVerdict(app: Context, id32: String): WebUiGateVerdict {
        val entry = runCatching {
            ModuleCatalog(File(app.filesDir, CATALOG_DIR)).get(ModuleType.MAPLESU, id32)
        }.getOrNull()
        val report = entry?.report
        if (report == null) {
            Log.i(TAG, "webui gate module=$id32 evidence-gap code=$CATALOG_REPORT_MISSING")
            return WebUiGateVerdict(
                blocked = false,
                decision = WebUiGateVerdict.DECISION_EVIDENCE_GAP,
                reasonCodes = listOf(CATALOG_REPORT_MISSING),
                detail = "gate=${WebUiGateVerdict.DECISION_EVIDENCE_GAP} record=missing code=$CATALOG_REPORT_MISSING",
            )
        }

        val facts = UltraModuleDeviceFacts.read(UltraSdkCredentials.client(app))
        val verdict = UltraModuleCompatibilityGate.evaluate(
            UltraModuleCompatibilityGate.identityFrom(report),
            facts,
            UltraModuleAction.OPEN_WEBUI,
        )
        val codes = verdict.reasonCodes
        val machine = "gate=${verdict.decision.name} record=${verdict.matchedRecordId ?: "none"} " +
            "codes=${codes.joinToString(",")}"
        Log.i(TAG, "webui gate module=$id32 decision=${verdict.decision.name} codes=$codes")

        if (!verdict.blocksNativeExecution) {
            return WebUiGateVerdict(
                blocked = false,
                decision = verdict.decision.name,
                reasonCodes = codes,
                detail = machine,
            )
        }
        return WebUiGateVerdict(
            blocked = true,
            failure = WebUiFailure(
                WebUiFailureReason.BLOCKED_INCOMPATIBLE,
                UltraModuleCompatibilityGate.blockingText(verdict) +
                    if (codes.isEmpty()) "" else " codes=${codes.joinToString(",")}",
            ),
            decision = verdict.decision.name,
            reasonCodes = codes,
            detail = machine,
        )
    }

    // -------------------------------------------------------- 生命周期证据（§4.3 / §5.2）

    /**
     * 默认证据落点：`filesDir/ultra-module-evidence.jsonl`，一行一个 `UltraModuleEvent`。
     *
     * 与安装路径共用同一个文件（W1-C 的 JSONL schema）；写失败只丢这一条，不影响打开动作。
     */
    private fun defaultEvidenceSink(app: Context): (UltraModuleEvent) -> Unit {
        val file = File(app.filesDir, EVIDENCE_FILE)
        val log = UltraModuleGateEvidenceLog(appendLine = { line -> file.appendText(line + "\n") })
        return { event ->
            when (val result = log.record(event)) {
                is UltraModuleEvidenceRecordResult.Dropped ->
                    Log.w(TAG, "webui evidence dropped: ${result.reason}")

                is UltraModuleEvidenceRecordResult.Appended -> Unit
            }
        }
    }

    /**
     * 把编排阶段事件翻译成 W1-C 的 `UltraModuleEvent`。
     *
     * 分账口径（§5.2）：
     *  - 门阶段 → `PRE_EXEC_CHECK`；其余 → `WEBUI_START`；
     *  - `SERVICE_EXIT` 是**服务侧**退出/未启动（SDK 回执失败），不是页面关闭，也不是模块停止；
     *  - 页面关闭/App 进程退出由调用方用 [dev.maplesu.manager.modules.webui.WebUiPageLifecycle]
     *    的稳定码记录（`ModuleWebUIActivity` 不在本次改动范围，见 w3-p1/EVIDENCE.md）。
     */
    private fun recordStage(sink: (UltraModuleEvent) -> Unit, event: WebUiOpenEvent) {
        val phase = when (event.stage) {
            WebUiLifecycleStage.GATE_ALLOWED, WebUiLifecycleStage.GATE_BLOCKED -> UltraModulePhase.PRE_EXEC_CHECK
            else -> UltraModulePhase.WEBUI_START
        }
        val outcome = when {
            event.failure != null -> when (event.failure.reason) {
                WebUiFailureReason.BLOCKED_INCOMPATIBLE -> UltraModuleOutcome.BLOCKED
                else -> UltraModuleOutcome.FAILURE
            }

            event.stage == WebUiLifecycleStage.READY ||
                event.stage == WebUiLifecycleStage.ACTIVITY_LAUNCHED -> UltraModuleOutcome.SUCCESS

            // PAGE_OPENED_BY_SDK 是「页面交给 SDK 打开」的归属说明，不是可观测的成功：
            // SDK 的 root helper 是否真的拉起浏览器，管理器看不到，所以留 UNKNOWN。
            else -> UltraModuleOutcome.UNKNOWN
        }
        val detail = buildString {
            append("stage=").append(event.stage.code)
            append(" route=").append(event.route.name)
            event.failure?.let { append(" code=").append(it.code) }
            if (event.detail.isNotBlank()) append(' ').append(event.detail)
        }
        val record = UltraModuleEvent(
            operationId = event.operationId,
            action = UltraModuleAction.OPEN_WEBUI,
            bootId = bootId(),
            hostUtcIso = hostUtcIso(),
            processRole = UltraModuleProcessRole.MANAGER,
            processPid = runCatching { android.os.Process.myPid() }.getOrNull(),
            phase = phase,
            returnCode = event.sdkErrorCode,
            outcome = outcome,
            detail = detail,
        )
        Log.i(TAG, "webui evidence ${event.stage.code} module=${event.moduleId32} ${event.detail}")
        runCatching { sink(record) }
            .onFailure { Log.w(TAG, "webui evidence sink failed: ${it.javaClass.simpleName}") }
    }

    private fun bootId(): String = runCatching {
        File("/proc/sys/kernel/random/boot_id").readText().trim()
    }.getOrDefault("")

    private fun hostUtcIso(): String = runCatching { Instant.now().toString() }.getOrDefault("")

    // ------------------------------------------------------------------ 默认真实实现

    /**
     * 默认真实启动器：调用 SDK `start_module_web_ui_server_async`。
     *
     * Receipt pageOpener selects exactly one UI owner; legacy SDK receipts keep their own browser opening.
     */
    private fun defaultSdkOpenRunner(app: Context): SdkOpenRunner = SdkOpenRunner { id32 ->
        val receipt = UltraSdkCredentials.client(app).openModuleWebUi(id32)
        SdkOpenOutcome(
            ok = receipt.succeeded,
            port = receipt.int("port") ?: 0,
            sdkError = receipt.sdkError,
            sdkErrorCode = receipt.sdkErrorCode.takeIf { it != Int.MIN_VALUE },
            errorCode = receipt.sdkError,
            pageOpener = if (receipt.string("pageOpener") == "manager") WebUiPageOpener.MAPLESU else WebUiPageOpener.SDK,
            moduleDir = receipt.string("moduleDir"),
        )
    }

    /**
     * 默认真实探活：等到端口真的回一条 HTTP 状态行（最多 [budgetMillis]）。
     * 失败时把「连上过但没回 HTTP」和「根本没连上」区分开：两者的排查方向完全不同。
     *
     * 每次调用返回**本次**结果，没有共享 `lastProbe` 之类的字段（§5.2）。
     */
    private fun defaultHttpReadyProbe(budgetMillis: Long, intervalMillis: Long): HttpReadyProbe =
        object : HttpReadyProbe {
            override suspend fun await(port: Int): HttpReadyResult {
                if (port !in 1..65535) {
                    return HttpReadyResult.failedAt(
                        port,
                        WebUiFailure(WebUiFailureReason.PORT_MISSING, "port=$port"),
                    )
                }
                val start = SystemClock.elapsedRealtime()
                var connected = false
                while (SystemClock.elapsedRealtime() - start < budgetMillis) {
                    if (probeHttp(port) != null) return HttpReadyResult.readyAt(port)
                    if (!connected) connected = tcpConnect(port)
                    delay(intervalMillis)
                }
                val failure = ModuleWebUiPlan.failureFromProbe(connected, null)
                    ?.copy(detail = "port=$port")
                    ?: WebUiFailure(WebUiFailureReason.PORT_NOT_LISTENING, "port=$port")
                return HttpReadyResult.failedAt(port, failure)
            }
        }

    // ------------------------------------------------------------------ probe

    private fun tcpConnect(port: Int): Boolean = runCatching {
        Socket().use { socket -> socket.connect(InetSocketAddress("127.0.0.1", port), PROBE_TIMEOUT_MS) }
    }.isSuccess

    /** 只取状态行，不读 body。返回 null = 连不上 / 不是 HTTP。 */
    private fun probeHttp(port: Int): Int? = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), PROBE_TIMEOUT_MS)
            socket.soTimeout = PROBE_TIMEOUT_MS
            socket.getOutputStream().write("GET / HTTP/1.0\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
            socket.getOutputStream().flush()
            val line = socket.getInputStream().bufferedReader().readLine() ?: return null
            parseStatusLine(line)
        }
    }.getOrNull()

    /** `HTTP/1.1 200 OK` → 200；不是状态行 → null。 */
    internal fun parseStatusLine(line: String): Int? {
        if (!line.startsWith("HTTP/")) return null
        val parts = line.split(' ')
        if (parts.size < 2) return null
        return parts[1].toIntOrNull()?.takeIf { it in 100..599 }
    }

    private fun portOf(url: String): Int =
        url.substringAfter("://", "").substringBefore('/').substringAfter(':', "").toIntOrNull() ?: 0

    /** 日志里不出现 token / root key。 */
    internal fun safeUrl(url: String): String = url.replace(Regex("[0-9a-fA-F]{32,}"), "<token>")

    private fun fail(reason: WebUiFailureReason, detail: String): WebUiFailure {
        Log.w(TAG, "web ui failure ${reason.code} detail=$detail")
        return WebUiFailure(reason, detail)
    }
}
