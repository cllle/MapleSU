package dev.maplesu.manager.modules.webui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 「打开模块 WebUI」的纯逻辑（无 Android 依赖，JVM 可测）。
 *
 * 为什么要把这段抽出来：两条完全不同的 WebUI 机制曾经挤在一个 `if` 里，
 * 出错时只能看到一个 Toast。这里把「怎么选路」「URL 怎么拼」「失败怎么分类」
 * 变成纯函数，页面只负责展示 [WebUiFailure]。
 *
 * 两条机制：
 *  - **Ultra（环境）模块**：`maplesu:<id32>`。webroot 由 Ultra 环境按需拉起一个只监听
 *    回环的 HTTP 服务（SDK `start_module_web_ui_server_async` 回传端口）。**没有** ksud
 *    webadmin，也不应该去读 `/data/adb/modules/<id>`。
 *  - **KSU 脚本模块**：走模块自己的 `webroot`；A 模式没有 ksud webadmin，因此由我们的
 *    liteweb daemon 在 `/x7k9f/<token>/modweb/<id>/` 下代理（与 ksud `/modweb/<id>/` 同名）。
 */
enum class WebUiRoute {
    /** Ultra 环境：SDK 回传端口后直连 `http://127.0.0.1:<port>/`。 */
    ULTRA_SDK,

    /** Lite/A 模式兜底：liteweb daemon 的 `/modweb/<id>/` 代理。 */
    LITEWEB_MODWEB,

    /** 传统 KSU 脚本模块：`WebUIActivity` + WebViewAssetLoader 直接读模块 webroot。 */
    KSU_ASSET_WEBROOT,

    /** 没有可用的 WebUI 机制。 */
    UNSUPPORTED,
}

enum class WebUiFailureReason(val code: String) {
    ENVIRONMENT_NOT_RUNNING("ENVIRONMENT_NOT_RUNNING"),
    ROOT_KEY_ABSENT("ULTRA_ROOT_KEY_ABSENT"),
    SDK_REJECTED("SDK_REJECTED"),
    PORT_MISSING("PORT_MISSING"),
    PORT_NOT_LISTENING("PORT_NOT_LISTENING"),
    NOT_HTTP("NOT_HTTP"),
    ACTIVITY_UNRESOLVED("ACTIVITY_UNRESOLVED"),
    MODULE_ID_INVALID("MODULE_ID_INVALID"),
    MODULE_DISABLED("MODULE_DISABLED"),
    PROXY_UNAVAILABLE("PROXY_UNAVAILABLE"),
    WEBVIEW_ERROR("WEBVIEW_ERROR"),

    /** 页面自己回了 4xx/5xx —— 服务在，但内容不对（不是白屏）。 */
    HTTP_STATUS("HTTP_STATUS"),

    /**
     * 兼容门在**调用 SDK 之前**拦下（`KNOWN_INCOMPATIBLE`）。
     *
     * 语义：本次请求没有发起 `start_module_web_ui_server_async`，所以「被拦」只对这次请求生效，
     * 不代表模块已停止、也不代表内核侧影响已撤销（§6.3）。
     */
    BLOCKED_INCOMPATIBLE("BLOCKED_INCOMPATIBLE"),

    /**
     * SDK 回执表示该模块的 WebUI 服务**已经**在运行（`-19918 = SERVER_RUNNING`）。
     *
     * 与 [SDK_REJECTED] 分开，是因为处理方式不同：这里不自动重启、也不重复启动，
     * 只把「已运行」如实展示；失败、超时、已运行分别展示。
     */
    ALREADY_RUNNING("ALREADY_RUNNING"),

    /**
     * 调用方/启动器报告的「超时或被中止」。
     *
     * 注意：探活预算（9s）耗尽**不**用这个码，仍按更具体的 [PORT_NOT_LISTENING] /
     * [NOT_HTTP] 分类；超时在这里指 SDK 调用本身，或注入的启动器明确返回超时。
     */
    TIMEOUT("TIMEOUT"),
}

/**
 * 一次失败：`reason` 是给用户看的稳定代码，`detail` 是给用户/日志的补充
 * （SDK `sdkError`、HTTP 状态、WebView error code 等）。
 */
data class WebUiFailure(
    val reason: WebUiFailureReason,
    val detail: String = "",
) {
    val code: String get() = reason.code

    /** 单行描述：`CODE: detail`；空 detail 时不带冒号。 */
    fun describe(): String = if (detail.isBlank()) code else "$code: $detail"
}

/** 解析成功的目标（URL 一定已通过回环/端口校验）。 */
data class WebUiTarget(
    val url: String,
    val route: WebUiRoute,
    val moduleId32: String,
)

// ---------------------------------------------------------------- 可注入的启动器 / 探活器

/**
 * SDK `start_module_web_ui_server_async` 回执的轻量投影。
 *
 * 为什么不用 `NativeReceipt`：本文件（以及它的测试）必须能在纯 JVM 上跑。真实实现由
 * `ModuleWebUiLauncher` 负责把回执翻译成这些字段，测试则可以注入计数替身。
 *
 * @param ok 回执是否成功（`NativeReceipt.succeeded`，不是「HTTP 200」）。
 * @param port SDK 回传的端口；0 = 没给。
 * @param sdkError SDK 的稳定错误字符串（`NativeReceipt.sdkError`）。
 * @param sdkErrorCode SDK 的整数错误码（`NativeReceipt.sdkErrorCode`）；null = 未知/未提供。
 * @param errorCode 启动器层的稳定码（异常类名、`TIMEOUT` 等）。与 [sdkError] 分开，避免把
 *   「桥没跑起来」和「SDK 拒绝」混成一条字符串。
 */
data class SdkOpenOutcome(
    val ok: Boolean,
    val port: Int = 0,
    val sdkError: String = "",
    val sdkErrorCode: Int? = null,
    val errorCode: String = "",
    val pageOpener: WebUiPageOpener = WebUiPageOpener.SDK,
    val moduleDir: String? = null,
)

/**
 * 注入点：真正调用 SDK 打开模块 WebUI。
 *
 * 这是**唯一**会执行模块 WebUI 原生代码的调用（SDK 的 root helper 会 fork 出 `webui_loader`），
 * 所以兼容门必须在它之前（见 [WebUiCompatibilityCheck]），单飞必须包住它（见
 * [WebUiOpenCoordinator]）。
 */
fun interface SdkOpenRunner {
    suspend fun open(id32: String): SdkOpenOutcome
}

/**
 * 一次探活的结果：`ready` + 失败原因 + detail。
 *
 * 探活结果作为本次调用的返回值，不跨调用共享状态。
 * 谁调用 [HttpReadyProbe.await]，谁就拿到属于本次 port 的结果。
 */
data class HttpReadyResult(
    val ready: Boolean,
    val failure: WebUiFailure? = null,
    val detail: String = "",
) {
    companion object {
        fun readyAt(port: Int): HttpReadyResult = HttpReadyResult(true, null, "port=$port")

        fun failedAt(port: Int, failure: WebUiFailure): HttpReadyResult =
            HttpReadyResult(false, failure, "port=$port")
    }
}

/**
 * 注入点：等到端口真的回一条 HTTP 状态行。
 *
 * 真实实现保留既有节奏（9s 预算、180ms 间隔，可参数化）；测试注入立即返回的替身，
 * 不必真的等 9 秒。
 */
fun interface HttpReadyProbe {
    suspend fun await(port: Int): HttpReadyResult
}

// ---------------------------------------------------------------------- 兼容门（P0 接线）

/**
 * 兼容门对一次「打开 WebUI」的纯数据结论。
 *
 * 三种情况必须能区分：
 *  - `blocked = true`：`KNOWN_INCOMPATIBLE`，[failure] 是给用户看的稳定失败；
 *  - `blocked = false` 且 `decision = VERIFIED/UNVERIFIED`：门放行，但「未验证」要原样保留；
 *  - `decision = EVIDENCE_GAP`：安装记录/判定输入取不到 —— 证据不足，**不拦**，
 *    但原因码必须写进日志与证据，不能伪装成已核实。
 */
data class WebUiGateVerdict(
    val blocked: Boolean,
    val failure: WebUiFailure? = null,
    val decision: String = DECISION_UNKNOWN,
    val reasonCodes: List<String> = emptyList(),
    val detail: String = "",
) {
    companion object {
        /** 判定输入取不到（例如安装记录里没有 Report）：证据不足，不拦，也不冒充已核实。 */
        const val DECISION_EVIDENCE_GAP = "EVIDENCE_GAP"

        /** 调用方没给判定：按证据不足处理，不拦。 */
        const val DECISION_UNKNOWN = "UNKNOWN"

        fun permit(
            decision: String,
            reasonCodes: List<String> = emptyList(),
            detail: String = "",
        ): WebUiGateVerdict = WebUiGateVerdict(
            blocked = false,
            failure = null,
            decision = decision,
            reasonCodes = reasonCodes,
            detail = detail,
        )

        fun refusal(
            failure: WebUiFailure,
            decision: String = "KNOWN_INCOMPATIBLE",
            reasonCodes: List<String> = emptyList(),
            detail: String = "",
        ): WebUiGateVerdict = WebUiGateVerdict(
            blocked = true,
            failure = failure,
            decision = decision,
            reasonCodes = reasonCodes,
            detail = detail,
        )
    }
}

/**
 * 注入点：用安装时留下的 `Report` 身份 + 本机设备事实判「打开页面」这个动作。
 *
 * 默认实现见 `ModuleWebUiLauncher`（`ModuleCatalog` → `UltraModuleCompatibilityGate`）。
 * 判定输入取不到时必须返回 [WebUiGateVerdict.DECISION_EVIDENCE_GAP]，不能抛异常、也不能拦。
 */
fun interface WebUiCompatibilityCheck {
    fun evaluate(id32: String): WebUiGateVerdict
}

// --------------------------------------------------------------------- 生命周期（§5.2 分账）

/**
 * 打开流程的阶段码（稳定机器码，写进 `UltraModuleEvent.detail` 的 `stage=`）。
 *
 * 注意这些阶段都属于 **WebUI 打开**这条线：`PAGE_CLOSED` / `APP_PROCESS_EXIT` 不在这里，
 * 因为「页面关闭」不等于「模块停止」。见 [WebUiPageLifecycle]。
 */
enum class WebUiLifecycleStage(val code: String) {
    /** 收到一次打开请求（单飞的执行者记录一次）。 */
    REQUESTED("WEBUI_OPEN_REQUESTED"),

    /** 这次请求合并到了正在飞的同模块操作上（没有新起 SDK 调用）。 */
    MERGED("WEBUI_OPEN_MERGED"),

    /** 兼容门放行（`decision` 可能是 `VERIFIED` / `UNVERIFIED` / `EVIDENCE_GAP`）。 */
    GATE_ALLOWED("WEBUI_GATE_ALLOWED"),

    /** 兼容门拦下：本次没有 SDK 调用。 */
    GATE_BLOCKED("WEBUI_GATE_BLOCKED"),

    ENVIRONMENT_PREPARING("WEBUI_ENVIRONMENT_PREPARING"),
    ENVIRONMENT_PREPARED("WEBUI_ENVIRONMENT_PREPARED"),

    /** 开始调用 SDK（这一步会执行模块 WebUI 原生代码）。 */
    SDK_OPEN("WEBUI_SDK_OPEN"),

    /** SDK 调用本身抛异常（桥异常、超时等）。 */
    SDK_OPEN_FAILED("WEBUI_SDK_OPEN_FAILED"),

    /** SDK 回执是失败/已运行：服务侧退出或未启动，不是「页面打不开」这么简单。 */
    SERVICE_EXIT("WEBUI_SERVICE_EXIT"),

    /** 开始等端口回 HTTP（一次打开只探一次就绪）。 */
    PROBE("WEBUI_PROBE"),

    /** 探活失败（端口未监听 / 非 HTTP / 预算耗尽）。 */
    PROBE_FAILED("WEBUI_PROBE_FAILED"),

    /** 回执成功且就绪检查通过。 */
    READY("WEBUI_READY"),

    /** Ultra 路由下页面由 SDK 自带的 `am start` 打开（MapleSU 不重复拉 Activity，§5.1）。 */
    PAGE_OPENED_BY_SDK("WEBUI_PAGE_OPENED_BY_SDK"),

    /** liteweb 兜底路由下由 MapleSU 拉起 [com.maplesu.core.ui.webui.ModuleWebUIActivity]。 */
    ACTIVITY_LAUNCHED("WEBUI_ACTIVITY_LAUNCHED"),

    /** MapleSU 拉起页面失败（Activity 解析不到等）。 */
    ACTIVITY_LAUNCH_FAILED("WEBUI_ACTIVITY_LAUNCH_FAILED"),
}

/**
 * 与打开流程平行、但**不属于**它的生命周期事件码。
 *
 * 生命周期要求「App 进程退出、页面关闭、服务退出分别记录；页面关闭不直接等同于停止后台模块」。
 * 服务退出由打开流程的 [WebUiLifecycleStage.SERVICE_EXIT] 记录；另外两类由调用方在
 * `ModuleWebUIActivity` 关闭 / 进程退出路径上记录，本类只提供稳定码，避免各调用点各写一种字符串。
 *
 * `PAGE_CLOSED` 的码里刻意带 `NOT_MODULE_STOP`：以后谁在页面关闭回调里写「已停止」，检索这个码
 * 就能发现语义被改坏了。停止后台模块只能由停止协议/卸载事务记录（§6.2/§6.3）。
 */
object WebUiPageLifecycle {
    /** 页面关闭：界面关闭，后台模块**仍在运行**。 */
    const val PAGE_CLOSED = "WEBUI_PAGE_CLOSED_NOT_MODULE_STOP"

    /** App 进程退出：由调用方/系统提供，表示管理器进程结束，不是模块停止。 */
    const val APP_PROCESS_EXIT = "APP_PROCESS_EXIT"

    /** 模块服务退出：SDK 回执/探活失败时由打开流程记录（与页面关闭分开）。 */
    const val SERVICE_EXIT = "WEBUI_SERVICE_EXIT"
}

// ---------------------------------------------------------------------------- 编排

/** 一次打开请求的纯输入。 */
data class WebUiOpenSpec(
    val moduleId32: String,
    val route: WebUiRoute,
    /** 仅 [WebUiRoute.LITEWEB_MODWEB] 使用：已经从 liteweb base URL 解析出的端口。 */
    val litewebPort: Int = 0,
)

/** 谁负责打开页面。Ultra 路由沿用 SDK（§5.1），liteweb 兜底由 MapleSU 拉 Activity。 */
enum class WebUiPageOpener {
    SDK,
    MAPLESU,
}

/**
 * 一次打开的归属结果：失败必须带着本次请求的 id32 / route / 阶段，不能是全局可变状态。
 */
data class WebUiOpenResult(
    val operationId: String,
    val moduleId32: String,
    val route: WebUiRoute,
    val stage: WebUiLifecycleStage,
    val failure: WebUiFailure? = null,
    val port: Int = 0,
    val opener: WebUiPageOpener = WebUiPageOpener.SDK,
    val moduleDir: String? = null,
) {
    val succeeded: Boolean get() = failure == null
}

/** 编排阶段事件（纯数据；落盘由 `ModuleWebUiLauncher` 翻译成 `UltraModuleEvent`）。 */
data class WebUiOpenEvent(
    val stage: WebUiLifecycleStage,
    val operationId: String,
    val moduleId32: String,
    val route: WebUiRoute,
    val detail: String = "",
    val failure: WebUiFailure? = null,
    val sdkErrorCode: Int? = null,
)

/**
 * 「打开模块 WebUI」的纯 JVM 编排。
 *
 * 顺序固定：`兼容门 → [单飞] SDK 打开 → 一次有预算的探活 → 结果`。
 *
 * ### 单飞（single-flight）
 * 以 **id32** 为键：同一模块同一时刻只有一个 SDK 请求在飞；期间到达的调用等待并**共用**同一个
 * 结果（不是各自重跑，也不是串行重跑）。不同模块各用各的 deferred，错误互不覆盖。
 * 完成时先在锁内摘掉键再唤醒等待者，所以「连续两次顺序打开」会各自启动一次 —— 单飞只合并
 * 重叠请求，不缓存成功结果（§5.2「同一模块一次打开只启动一个 SDK 请求」）。
 *
 * ### 不自动重启
 * 失败/超时/已运行都不递归重试、不起后台轮询；探活也只做一次有预算的等待（预算由注入的
 * [HttpReadyProbe] 决定，默认 9s/180ms）。
 *
 * ### 为什么 liteweb 路由不进单飞
 * [WebUiRoute.LITEWEB_MODWEB] 走的是 liteweb daemon 的 `/modweb/<id>/` 代理，**不调用** SDK、
 * 不执行模块 WebUI 原生代码，也没有「一次 SDK 请求」需要合并；它仍然只探一次就绪。
 *
 * ### 兼容门只接在 Ultra 路由
 * 只有 [WebUiRoute.ULTRA_SDK] 会调用 `start_module_web_ui_server_async`（§4.1 要求覆盖的入口）；
 * liteweb 兜底不执行模块原生代码，因此没有「原生调用次数为 0」的问题。
 *
 * @param sdkOpen 真正调用 SDK 的注入点。
 * @param probe 探活注入点。
 * @param gate 兼容门注入点。
 * @param onStage 阶段事件出口（默认丢弃；生产实现写 `UltraModuleEvent` JSONL）。
 * @param flights 单飞状态。生产必须是**同一个实例**跨调用复用（`ModuleWebUiLauncher` 持有）；
 *   每次调用新建一个实例就退化成「只合并同一实例内的请求」。
 */
class WebUiOpenCoordinator(
    private val sdkOpen: SdkOpenRunner,
    private val probe: HttpReadyProbe,
    private val gate: WebUiCompatibilityCheck,
    private val onStage: (WebUiOpenEvent) -> Unit = {},
    private val flights: SingleFlight<String, WebUiOpenResult> = SingleFlight(),
    private val pageOpen: suspend (WebUiOpenResult) -> WebUiFailure? = { null },
    private val prepareEnvironment: (suspend () -> WebUiFailure?)? = null,
) {

    suspend fun open(spec: WebUiOpenSpec): WebUiOpenResult = when (spec.route) {
        WebUiRoute.ULTRA_SDK -> openUltraSdk(spec)
        WebUiRoute.LITEWEB_MODWEB -> openLiteweb(spec)
        WebUiRoute.KSU_ASSET_WEBROOT, WebUiRoute.UNSUPPORTED -> failed(
            spec = spec,
            operationId = UUID.randomUUID().toString(),
            failure = WebUiFailure(WebUiFailureReason.PROXY_UNAVAILABLE, "route=${spec.route.name}"),
            stage = WebUiLifecycleStage.REQUESTED,
        )
    }

    // ------------------------------------------------------------------ Ultra（SDK）

    private suspend fun openUltraSdk(spec: WebUiOpenSpec): WebUiOpenResult {
        var joined = false
        val result = flights.run(
            key = spec.moduleId32,
            block = {
                val ready = openUltraSdkOnce(spec)
                if (!ready.succeeded || ready.opener != WebUiPageOpener.MAPLESU) ready else {
                    currentCoroutineContext().ensureActive()
                    val failure = try { pageOpen(ready) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        WebUiFailure(WebUiFailureReason.ACTIVITY_UNRESOLVED, error.javaClass.simpleName)
                    }
                    val stage = if (failure == null) WebUiLifecycleStage.ACTIVITY_LAUNCHED
                        else WebUiLifecycleStage.ACTIVITY_LAUNCH_FAILED
                    emit(WebUiOpenEvent(stage, ready.operationId, spec.moduleId32, spec.route,
                        detail = "port=${ready.port} opener=maplesu", failure = failure))
                    ready.copy(stage = stage, failure = failure)
                }
            },
            onJoin = { joined = true },
        )
        if (joined) {
            // 合并发生在执行者结束之后才可观测：这里记的 operationId 就是被合并的那次操作。
            emit(
                WebUiOpenEvent(
                    stage = WebUiLifecycleStage.MERGED,
                    operationId = result.operationId,
                    moduleId32 = spec.moduleId32,
                    route = spec.route,
                    detail = "shared an in-flight operation",
                    failure = result.failure,
                ),
            )
        }
        return result
    }

    private suspend fun openUltraSdkOnce(spec: WebUiOpenSpec): WebUiOpenResult {
        val operationId = UUID.randomUUID().toString()
        val id32 = spec.moduleId32
        emit(WebUiOpenEvent(WebUiLifecycleStage.REQUESTED, operationId, id32, spec.route))

        val verdict = evaluateGate(id32)
        if (verdict.blocked) {
            val failure = verdict.failure ?: WebUiFailure(
                WebUiFailureReason.BLOCKED_INCOMPATIBLE,
                "gate=blocked decision=${verdict.decision}",
            )
            emit(
                WebUiOpenEvent(
                    stage = WebUiLifecycleStage.GATE_BLOCKED,
                    operationId = operationId,
                    moduleId32 = id32,
                    route = spec.route,
                    detail = verdict.detail,
                    failure = failure,
                ),
            )
            return failed(spec, operationId, failure, WebUiLifecycleStage.GATE_BLOCKED)
        }
        emit(
            WebUiOpenEvent(
                stage = WebUiLifecycleStage.GATE_ALLOWED,
                operationId = operationId,
                moduleId32 = id32,
                route = spec.route,
                detail = verdict.detail,
            ),
        )

        prepareEnvironment?.let { prepare ->
            emit(WebUiOpenEvent(WebUiLifecycleStage.ENVIRONMENT_PREPARING, operationId, id32, spec.route))
            currentCoroutineContext().ensureActive()
            val failure = try { prepare() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                WebUiFailure(WebUiFailureReason.SDK_REJECTED, "environment preparation: ${error.javaClass.simpleName}")
            }
            currentCoroutineContext().ensureActive()
            if (failure != null) {
                emit(WebUiOpenEvent(WebUiLifecycleStage.SDK_OPEN_FAILED, operationId, id32, spec.route,
                    detail = "environment preparation failed before module submission", failure = failure))
                return failed(spec, operationId, failure, WebUiLifecycleStage.SDK_OPEN_FAILED)
            }
            emit(WebUiOpenEvent(WebUiLifecycleStage.ENVIRONMENT_PREPARED, operationId, id32, spec.route))
        }
        emit(WebUiOpenEvent(WebUiLifecycleStage.SDK_OPEN, operationId, id32, spec.route))
        currentCoroutineContext().ensureActive()
        val outcome = try {
            sdkOpen.open(id32)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val failure = if (error is java.util.concurrent.TimeoutException) {
                WebUiFailure(WebUiFailureReason.TIMEOUT, error.javaClass.simpleName)
            } else {
                WebUiFailure(WebUiFailureReason.SDK_REJECTED, error.javaClass.simpleName)
            }
            emit(
                WebUiOpenEvent(
                    stage = WebUiLifecycleStage.SDK_OPEN_FAILED,
                    operationId = operationId,
                    moduleId32 = id32,
                    route = spec.route,
                    failure = failure,
                ),
            )
            return failed(spec, operationId, failure, WebUiLifecycleStage.SDK_OPEN_FAILED)
        }

        val receiptFailure = ModuleWebUiPlan.failureFromSdkOpen(outcome)
        if (receiptFailure != null) {
            emit(
                WebUiOpenEvent(
                    stage = WebUiLifecycleStage.SERVICE_EXIT,
                    operationId = operationId,
                    moduleId32 = id32,
                    route = spec.route,
                    detail = "opener=sdk sdkError=${outcome.sdkError}",
                    failure = receiptFailure,
                    sdkErrorCode = outcome.sdkErrorCode,
                ),
            )
            return failed(
                spec = spec,
                operationId = operationId,
                failure = receiptFailure,
                stage = WebUiLifecycleStage.SERVICE_EXIT,
                port = outcome.port,
            )
        }

        val port = outcome.port
        val probeFailure = probeOnce(spec, operationId, port, opener = outcome.pageOpener)
        if (probeFailure != null) {
            return failed(spec, operationId, probeFailure, WebUiLifecycleStage.PROBE_FAILED, port)
        }
        emit(
            WebUiOpenEvent(
                stage = WebUiLifecycleStage.READY,
                operationId = operationId,
                moduleId32 = id32,
                route = spec.route,
                detail = "port=$port opener=${outcome.pageOpener.name.lowercase()}",
            ),
        )
        // The receipt explicitly selects one page owner; legacy receipts retain SDK opening.
        return WebUiOpenResult(
            operationId = operationId,
            moduleId32 = id32,
            route = spec.route,
            stage = WebUiLifecycleStage.READY,
            failure = null,
            port = port,
            opener = outcome.pageOpener,
            moduleDir = outcome.moduleDir,
        )
    }

    // -------------------------------------------------------------- liteweb（兜底）

    private suspend fun openLiteweb(spec: WebUiOpenSpec): WebUiOpenResult {
        val operationId = UUID.randomUUID().toString()
        emit(WebUiOpenEvent(WebUiLifecycleStage.REQUESTED, operationId, spec.moduleId32, spec.route))
        val port = spec.litewebPort
        val failure = probeOnce(spec, operationId, port, opener = WebUiPageOpener.MAPLESU)
        if (failure != null) {
            return failed(spec, operationId, failure, WebUiLifecycleStage.PROBE_FAILED, port)
        }
        emit(
            WebUiOpenEvent(
                stage = WebUiLifecycleStage.READY,
                operationId = operationId,
                moduleId32 = spec.moduleId32,
                route = spec.route,
                detail = "port=$port opener=maplesu",
            ),
        )
        return WebUiOpenResult(
            operationId = operationId,
            moduleId32 = spec.moduleId32,
            route = spec.route,
            stage = WebUiLifecycleStage.READY,
            failure = null,
            port = port,
            opener = WebUiPageOpener.MAPLESU,
        )
    }

    // ------------------------------------------------------------------ internals

    private fun evaluateGate(id32: String): WebUiGateVerdict = try {
        gate.evaluate(id32)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        // 判定器自己坏了：按证据不足处理（不拦），但原因码必须留下，不能伪装成已核实。
        WebUiGateVerdict(
            blocked = false,
            decision = WebUiGateVerdict.DECISION_EVIDENCE_GAP,
            reasonCodes = listOf("GATE_EVALUATION_FAILED"),
            detail = "gate=error class=${error.javaClass.simpleName}",
        )
    }

    /**
     * 一次有预算的探活。返回 null = 就绪；否则是**带着本次 port** 的失败。
     * 不循环重试、不在失败后重新调用 SDK。
     */
    private suspend fun probeOnce(
        spec: WebUiOpenSpec,
        operationId: String,
        port: Int,
        opener: WebUiPageOpener = WebUiPageOpener.SDK,
    ): WebUiFailure? {
        if (port !in 1..65535) {
            val failure = WebUiFailure(WebUiFailureReason.PORT_MISSING, "port=$port")
            emit(
                WebUiOpenEvent(
                    stage = WebUiLifecycleStage.PROBE_FAILED,
                    operationId = operationId,
                    moduleId32 = spec.moduleId32,
                    route = spec.route,
                    detail = "opener=${opener.name.lowercase()} port=$port",
                    failure = failure,
                ),
            )
            return failure
        }
        emit(
            WebUiOpenEvent(
                stage = WebUiLifecycleStage.PROBE,
                operationId = operationId,
                moduleId32 = spec.moduleId32,
                route = spec.route,
                detail = "port=$port",
            ),
        )
        val readiness = try {
            probe.await(port)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            HttpReadyResult.failedAt(
                port,
                WebUiFailure(WebUiFailureReason.PORT_NOT_LISTENING, "probe-error=${error.javaClass.simpleName}"),
            )
        }
        if (readiness.ready) return null
        val failure = withPort(readiness.failure ?: WebUiFailure(WebUiFailureReason.PORT_NOT_LISTENING), port)
        emit(
            WebUiOpenEvent(
                stage = WebUiLifecycleStage.PROBE_FAILED,
                operationId = operationId,
                moduleId32 = spec.moduleId32,
                route = spec.route,
                detail = "opener=${opener.name.lowercase()} port=$port",
                failure = failure,
            ),
        )
        return failure
    }

    private fun failed(
        spec: WebUiOpenSpec,
        operationId: String,
        failure: WebUiFailure,
        stage: WebUiLifecycleStage,
        port: Int = 0,
    ): WebUiOpenResult = WebUiOpenResult(
        operationId = operationId,
        moduleId32 = spec.moduleId32,
        route = spec.route,
        stage = stage,
        failure = failure,
        port = port,
        opener = if (spec.route == WebUiRoute.LITEWEB_MODWEB) WebUiPageOpener.MAPLESU else WebUiPageOpener.SDK,
    )

    private fun emit(event: WebUiOpenEvent) = runCatching { onStage(event) }

    /** 失败 detail 必须指向**本次** port；不覆盖调用方已经写进去的 port。 */
    private fun withPort(failure: WebUiFailure, port: Int): WebUiFailure {
        if (failure.detail.split(' ').any { it == "port=$port" }) return failure
        val combined = if (failure.detail.isBlank()) "port=$port" else "${failure.detail} port=$port"
        return failure.copy(detail = combined)
    }
}

/**
 * 以 key 合并「正在飞」的请求（single-flight）。
 *
 * 语义边界（§5.2「同一模块一次打开只启动一个 SDK 请求」）：
 *  - 同一个 key 同时只有一个 [block] 在执行；后到的调用 `await` 同一个结果，**不重跑**、也不串行重跑；
 *  - **不缓存已完成的结果**：完成前先在锁内把 key 摘掉，再唤醒等待者，所以「连续两次顺序打开」
 *    会各自启动一次；
 *  - 不同 key 的请求各用各的 deferred，互不阻塞、错误互不覆盖；
 *  - 执行者被取消时把同一个取消异常交给等待者（否则等待者会永远挂住）；等待者自己被取消只影响它自己。
 */
class SingleFlight<K : Any, V> {

    private val mutex = Mutex()
    private val inFlight = HashMap<K, CompletableDeferred<V>>()

    /**
     * @param onJoin 本次调用合并到了已有请求时同步调用（执行者路径不会调用它）。
     */
    suspend fun run(key: K, block: suspend () -> V, onJoin: () -> Unit = {}): V {
        var owned: CompletableDeferred<V>? = null
        val flight = mutex.withLock {
            val existing = inFlight[key]
            if (existing != null) {
                existing
            } else {
                CompletableDeferred<V>().also {
                    inFlight[key] = it
                    owned = it
                }
            }
        }
        val mine = owned
        if (mine == null) {
            onJoin()
            return flight.await()
        }
        return try {
            val value = block()
            // 先摘键再 complete：完成之后到达的调用必须启动新请求，而不是拿旧结果。
            // NonCancellable：执行者在这两步之间被取消时，等待者仍必须拿到结果/异常，
            // 否则它们会永远挂在 deferred 上，键也会留在表里。
            withContext(NonCancellable) {
                mutex.withLock { if (inFlight[key] === mine) inFlight.remove(key) }
                mine.complete(value)
            }
            value
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                mutex.withLock { if (inFlight[key] === mine) inFlight.remove(key) }
                mine.completeExceptionally(error)
            }
            throw error
        }
    }
}

object ModuleWebUiPlan {

    const val ULTRA_ID_PREFIX = "maplesu:"

    private const val MAX_ID_LENGTH = 64
    private val MODULE_ID32 = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*$")
    private val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost")

    fun isUltraModuleId(id: String): Boolean = id.startsWith(ULTRA_ID_PREFIX)

    /** `maplesu:<id32>` → `<id32>`；不做任何猜测（非法就原样返回，交给 [moduleId32OrNull]）。 */
    fun moduleId32(id: String): String = id.removePrefix(ULTRA_ID_PREFIX)

    /**
     * 严格 id32：1..64 字节、`[A-Za-z0-9._-]`、首字符字母数字、不含 `..`。
     * 这个值会进 URL 与文件路径，宁可不打开也不猜。
     */
    fun moduleId32OrNull(id: String): String? {
        val value = moduleId32(id)
        if (value.isEmpty() || value.length > MAX_ID_LENGTH) return null
        if (!MODULE_ID32.matches(value)) return null
        if (value.contains("..")) return null
        return value
    }

    /**
     * 回环 URL。端口必须 1..65535；`path` 必须是单段或以 `/` 开头的相对路径，
     * 不接受 scheme/host/`..`（避免把 WebView 变成任意 URL 容器）。
     */
    fun loopbackUrl(port: Int, path: String = "/"): String? {
        if (port !in 1..65535) return null
        val normalized = normalizePath(path) ?: return null
        return "http://127.0.0.1:$port$normalized"
    }

    /**
     * liteweb 的 `/modweb/<id32>/<rel>` 代理 URL。
     *
     * `baseUrl` 必须是 `http://127.0.0.1:<port>/<prefix>/<token>/`（由 daemon 生成，
     * 只含回环地址）；`rel` 走和静态资源一样的防穿越规则。
     */
    fun modWebUrl(baseUrl: String, moduleId32: String, rel: String = ""): String? {
        val base = normalizeBase(baseUrl) ?: return null
        if (moduleId32OrNull(moduleId32) == null) return null
        val inner = normalizeModWebRel(rel) ?: return null
        val tail = if (inner.isEmpty()) "" else inner
        return "${base}modweb/$moduleId32/$tail"
    }

    /**
     * 选路。顺序即优先级：
     *  1. 没有 web_ui 声明 → UNSUPPORTED；
     *  2. Ultra 模块 → ULTRA_SDK；未就绪时由显式打开准备真实环境。
     *  3. KSU 脚本模块 → KSU_ASSET_WEBROOT（由 WebUIActivity 读模块 webroot）。
     * liteweb 只服务脚本目录，原生模块不以它替代 SDK 的目录与 loader。
     */
    fun routeFor(
        hasWebUi: Boolean,
        isUltraModule: Boolean,
        ultraEnvironmentRunning: Boolean,
        litewebBaseUrl: String?,
        moduleEnabled: Boolean,
        moduleRemoved: Boolean = false,
    ): WebUiRoute {
        if (!hasWebUi || moduleRemoved) return WebUiRoute.UNSUPPORTED
        if (isUltraModule) {
            if (!moduleEnabled) return WebUiRoute.UNSUPPORTED
            return WebUiRoute.ULTRA_SDK
        }
        if (!moduleEnabled) return WebUiRoute.UNSUPPORTED
        return WebUiRoute.KSU_ASSET_WEBROOT
    }

    /**
     * SDK 回执 → 失败（null = 可以继续等端口）。
     *
     * `MAPLESU_ULTRA_ROOT_KEY_ABSENT` 必须和「SDK 拒绝」分开：前者是配置问题，
     * 提示用户去启用环境；后者是模块/环境真的拒绝了。
     */
    fun failureFromReceipt(ok: Boolean, sdkError: String, port: Int): WebUiFailure? {
        if (!ok) {
            val code = sdkError.trim()
            return when {
                code.isEmpty() || code == "MAPLESU_BRIDGE_UNPARSEABLE" ->
                    WebUiFailure(WebUiFailureReason.SDK_REJECTED, "bridge answer was not a receipt")
                code.contains("ROOT_KEY", ignoreCase = true) ->
                    WebUiFailure(WebUiFailureReason.ROOT_KEY_ABSENT, code)
                else -> WebUiFailure(WebUiFailureReason.SDK_REJECTED, code)
            }
        }
        if (port !in 1..65535) return WebUiFailure(WebUiFailureReason.PORT_MISSING, "port=$port")
        return null
    }

    /** SDK 明确报告「服务已在运行」时的错误码（`SERVER_RUNNING`，见旧启动器注释）。 */
    const val SERVER_RUNNING_SDK_ERROR = -19918

    fun moduleDirectoryOrNull(path: String?): String? = path?.takeIf {
        it.startsWith('/') && it.any { c -> c != '/' } && it.none { c -> c.code < 0x20 || c == '\\' } &&
            it.split('/').none { part -> part == "." || part == ".." }
    }

    /**
     * [SdkOpenOutcome] → 失败（null = 可以继续探活）。
     *
     * 在 [failureFromReceipt] 之上只加三条分类，不改变它的既有语义：
     *  - 已运行（`-19918` / `SERVER_RUNNING`）→ [WebUiFailureReason.ALREADY_RUNNING]，**不**自动重启；
     *  - 启动器层明确报告超时 → [WebUiFailureReason.TIMEOUT]；
     *  - 启动器层异常（`errorCode` 非空、`sdkError` 为空）→ [WebUiFailureReason.SDK_REJECTED]，
     *    并且 detail 里保留稳定码，而不是退化成「bridge answer was not a receipt」。
     */
    fun failureFromSdkOpen(outcome: SdkOpenOutcome): WebUiFailure? {
        if (outcome.ok) {
            if (outcome.pageOpener == WebUiPageOpener.MAPLESU && moduleDirectoryOrNull(outcome.moduleDir) == null) {
                return WebUiFailure(WebUiFailureReason.SDK_REJECTED, "manager receipt missing or invalid moduleDir")
            }
            return failureFromReceipt(true, outcome.sdkError, outcome.port)
        }
        val sdkError = outcome.sdkError.trim()
        val extra = outcome.errorCode.trim()
        if (outcome.sdkErrorCode == SERVER_RUNNING_SDK_ERROR ||
            sdkError.contains("SERVER_RUNNING", ignoreCase = true) ||
            extra.contains("ALREADY_RUNNING", ignoreCase = true)
        ) {
            return WebUiFailure(
                WebUiFailureReason.ALREADY_RUNNING,
                sdkError.ifBlank { extra.ifBlank { "sdkErrorCode=${outcome.sdkErrorCode}" } },
            )
        }
        if (sdkError.contains("TIMEOUT", ignoreCase = true) || extra.contains("TIMEOUT", ignoreCase = true)) {
            return WebUiFailure(WebUiFailureReason.TIMEOUT, sdkError.ifBlank { extra })
        }
        if (sdkError.isEmpty()) {
            return WebUiFailure(
                WebUiFailureReason.SDK_REJECTED,
                extra.ifBlank { "sdk rejected without an error code" },
            )
        }
        return failureFromReceipt(false, sdkError, outcome.port)
    }

    /**
     * 端口探活结果 → 失败（null = 端口已经是 HTTP 服务）。
     *
     * 只有「连上了且回了任何 HTTP 状态行」才算服务就绪：Ultra loader 的 fork+exec
     * 是异步的，端口先回、监听后到，光连 TCP 不够，光看端口被占用也不够。
     */
    fun failureFromProbe(connected: Boolean, httpStatus: Int?): WebUiFailure? = when {
        !connected -> WebUiFailure(WebUiFailureReason.PORT_NOT_LISTENING, "no TCP accept")
        httpStatus == null -> WebUiFailure(WebUiFailureReason.NOT_HTTP, "port answered but not HTTP")
        else -> null
    }

    /** 页面主文档 4xx/5xx（服务在、内容不对）→ 失败；3xx/2xx 放行。 */
    fun failureFromHttpStatus(status: Int, url: String = ""): WebUiFailure? {
        if (status < 400) return null
        val detail = if (url.isBlank()) "status=$status" else "status=$status url=$url"
        return WebUiFailure(WebUiFailureReason.HTTP_STATUS, detail)
    }

    // ------------------------------------------------------------------ helpers

    /**
     * 允许 `""`（根）、`/`、`/a`、`a`、`a/b`。
     * 拒绝：反斜杠、NUL/控制字符、`..`、`.` 段、`//`、以 `/` 开头的多段（`//evil`）。
     */
    fun normalizePath(path: String): String? {
        if (path.isEmpty()) return "/"
        if (path.any { it == '\\' || it == '\u0000' || it.code < 0x20 }) return null
        val withSlash = if (path.startsWith('/')) path else "/$path"
        val segments = withSlash.split('/').drop(1)
        if (segments.any { it == "." || it == ".." }) return null
        if (segments.any { it.isEmpty() } && segments.size > 1) return null
        val joined = segments.filter { it.isNotEmpty() }.joinToString("/")
        return if (joined.isEmpty()) "/" else "/$joined"
    }

    /**
     * `/modweb/<id>/<rel>` 里的 `rel`：空 = 模块首页；每段不许 `.`/`..`/前导 `.`，
     * 不许反斜杠与 NUL。返回的串**不带**前导 `/`，可直接拼到 webroot 后面。
     */
    fun normalizeModWebRel(raw: String): String? {
        if (raw.isEmpty()) return "index.html"
        if (raw.any { it == '\\' || it == '\u0000' || it.code < 0x20 }) return null
        val trimmed = raw.trim('/')
        if (trimmed.isEmpty()) return "index.html"
        val segments = trimmed.split('/')
        if (segments.any { it.isEmpty() }) return null
        if (segments.any { it == "." || it == ".." || it.startsWith(".") }) return null
        if (segments.any { !segmentAllowed(it) }) return null
        return segments.joinToString("/")
    }

    /** liteweb base：`http://127.0.0.1:<port>/<prefix>/<token>/`（尾部斜杠归一）。 */
    fun normalizeBase(baseUrl: String?): String? {
        if (baseUrl.isNullOrBlank()) return null
        val text = baseUrl.trim()
        if (!text.startsWith("http://")) return null
        val rest = text.removePrefix("http://")
        val slash = rest.indexOf('/')
        if (slash <= 0) return null
        val authority = rest.substring(0, slash)
        val host = authority.substringBefore(':')
        if (host.lowercase() !in LOOPBACK_HOSTS) return null
        val portPart = authority.substringAfter(':', "")
        if (portPart.isEmpty()) return null
        val port = portPart.toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        val path = rest.substring(slash)
        if (path.any { it == '\\' || it == '\u0000' || it.code < 0x20 }) return null
        if (path.contains("..")) return null
        val withSlash = if (path.endsWith('/')) path else "$path/"
        // base 必须是 `/x7k9f/<token>/` 形状：至少两段，最后一段是 token 段。
        val segments = withSlash.trim('/').split('/')
        if (segments.size < 2) return null
        if (segments.any { it.isEmpty() }) return null
        return "http://${host.lowercase()}:$port/" + segments.joinToString("/") + "/"
    }

    private fun segmentAllowed(segment: String): Boolean {
        // 允许 URL 里常见的可见 ASCII；空格与 % 交给上层解码后判断（这里不做解码）。
        return segment.all { it.code in 0x21..0x7E && it != '\\' }
    }
}
