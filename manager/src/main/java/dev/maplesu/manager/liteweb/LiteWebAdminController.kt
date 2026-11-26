package dev.maplesu.manager.liteweb

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import dev.maplesu.manager.runtime.MapleSuRuntime
import dev.maplesu.manager.runtime.RootBackend
import dev.maplesu.manager.runtime.RootExec
import dev.maplesu.manager.workmode.ModeRuntime
import dev.maplesu.manager.workmode.WorkMode
import dev.maplesu.manager.workmode.detect.ModeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * H2：Lite 网页管理器设置页 API（UI 由 r5-ui 接）。
 *
 * 生命周期：按根通道选择 RootService 或独立 daemon，HTTP 服务独立于 App 进程；
 * stop 关闭监听；enabled 标记存 App prefs（A-only 功能：仅 `ModeRuntime.active == LITE` 可启动）。
 * token/URL 只在内存缓存（`url()`），不写 prefs/日志。
 *
 * 根通道（真机 P0）：启动策略按 [LiteWebBackendPolicy] 三态——
 * LITE_KERNEL → RootService；ULTRA_ENV/HOOK → 经当前根通道控制独立 daemon（失败显式
 * `ROOT_BACKEND_ULTRA_ENV_DAEMON_UNSUPPORTED`）；NONE → `LITEWEB_ROOT_BACKEND_UNAVAILABLE`。
 */
class LiteWebAdminController(
    private val context: Context,
    private val backendProvider: () -> RootBackend = { RootExec.preferredBackend(context) },
    private val ultraDaemon: UltraEnvLiteWebDaemon = UltraEnvLiteWebDaemon.default(),
    /**
     * 「设备上装了什么 root 实现」的证据源（默认读 [RootExec.capability] 的进程内缓存：
     * Ultra 内核 = libmaplesu_runtime + 凭据都在；Ultra 环境 = 探针 Running。**不发起 root 执行**）。
     */
    private val installedBackendProvider: () -> RootBackend = { RootExec.capability(context).backend },
    /** 最近一次模式判定（冷启动窗口为 null）：用于区分「未判定」与「明确是 B」。 */
    private val detectionStateProvider: () -> ModeState? = { ModeRuntime.latestDetection?.state },
    private val recoveryControl: RecoveryWebUiControl = RecoveryWebUiControl { RootExec.execute(context, it) },
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Volatile
    private var cached: LiteWebAdminStatus? = null

    @Volatile
    private var previewCoordinatorValue: LiteWebPreviewCoordinator? = null

    @Volatile
    private var previewFailure: String = ""

    /** 最近一次 start/status 的 URL（含 token，只在内存；不落日志）。 */
    fun previewUrlOrNull(): String? = runCatching { previewCoordinator().previewUrlOrNull() }.getOrNull()

    /** 只读；不启动 daemon。 */
    fun previewUrl(): String? = previewUrlOrNull()

    /** 最近一次 prepare 失败的结构化原因（null/空 = 成功）。 */
    fun previewError(): String = previewFailure

    /** 当前 WebUI 端口（配置 > prefs > 默认 18427）。 */
    fun getPort(): Int = runCatching { readWebuiConfig()?.port }.getOrNull()
        ?: prefs.getInt(KEY_PORT, LiteWebPortPolicy.DEFAULT_PORT)

    /**
     * 隐藏设置页"隐藏后 WebUI 链接"预览：确保 webui.conf（token+webuiPort，0600）存在，
     * token 不存在时生成一次并持久化；**不启动 daemon、不写 hidden、不备份、不卸载**。
     * 无 root 写权限/读回失败 → null + [previewError] 原因。
     */
    suspend fun preparePreviewUrl(requestedPort: Int? = null): LiteWebUrlInfo? = withContext(Dispatchers.IO) {
        if (!canRunHere()) {
            previewFailure = if (ModeRuntime.active == null) {
                LiteWebPreviewCodes.MODE_UNRESOLVED
            } else {
                LiteWebPreviewCodes.MODE_NOT_LITE
            }
            return@withContext null
        }
        when (val result = previewCoordinator().preparePreviewUrl(requestedPort, ::applyRecoveryPort)) {
            is LiteWebPreviewResult.Ok -> {
                previewFailure = ""
                result.info
            }
            is LiteWebPreviewResult.Failed -> {
                previewFailure = result.code
                null
            }
        }
    }

    /** 同步只读链接（UI 即时显示/复制；缺配置返回 null，不写盘、不启动 daemon）。 */
    fun url(): String? = previewUrlOrNull()

    private fun previewCoordinator(): LiteWebPreviewCoordinator {
        previewCoordinatorValue?.let { return it }
        synchronized(this) {
            previewCoordinatorValue?.let { return it }
            val created = LiteWebPreviewCoordinator(
                store = previewStore,
                defaultPort = configuredPort(),
            )
            previewCoordinatorValue = created
            return created
        }
    }

    private fun configuredPort(): Int = runCatching { readWebuiConfig()?.port }.getOrNull()
        ?: prefs.getInt(KEY_PORT, LiteWebPortPolicy.DEFAULT_PORT)

    /** Live owners rebind over authenticated HTTP; a stopped owner changes config under the same lease. */
    private fun applyRecoveryPort(current: LiteWebPreviewConfig, requestedPort: Int): LiteWebPreviewResult.Failed? {
        try {
            val connection = URI(current.url() + "api/config/port").toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 1_500
                connection.readTimeout = 5_000
                connection.instanceFollowRedirects = false
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write("{\"webuiPort\":$requestedPort}".toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status == 200) connection.inputStream else connection.errorStream
                val fields = stream?.bufferedReader()?.use { LiteWebJson.parseFlatObject(it.readText()) }
                if (status == 200 && fields?.get("ok") == "true" && fields["webuiPort"] == requestedPort.toString()) {
                    return null
                } else {
                    fields?.get("error")?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]+")) }?.let {
                        return LiteWebPreviewResult.Failed(it)
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            // The offline path checks the same owner lease and expected config; it never rewrites a live owner.
        }
        val apk = runCatching { app.applicationInfo.sourceDir }.getOrNull()
            ?: return LiteWebPreviewResult.Failed("LITEWEB_PORT_APPLY_UNVERIFIED")
        val quotedApk = "'" + apk.replace("'", "'\\''") + "'"
        val identity = liteWebPortIdentity(current.port, current.token)
        val script = "APK=$quotedApk; [ -f \"\$APK\" ] && [ ! -L \"\$APK\" ] || exit 1; " +
            "CLASSPATH=\"\$APK\" /system/bin/timeout -k 1 8 /system/bin/app_process /system/bin " +
            "dev.maplesu.manager.liteweb.daemon.LiteWebDaemonMain --set-port $identity $requestedPort"
        val result = runCatching { RootExec.execute(app, script) }.getOrNull()
            ?: return LiteWebPreviewResult.Failed("LITEWEB_PORT_APPLY_UNVERIFIED")
        val receipts = result.output.lineSequence().map(String::trim)
            .filter { it.startsWith("LITEWEB_PORT_UPDATE ") }.toList()
        val code = receipts.singleOrNull()?.removePrefix("LITEWEB_PORT_UPDATE ")
            ?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]+")) }
            ?: return LiteWebPreviewResult.Failed("LITEWEB_PORT_APPLY_UNVERIFIED")
        return if (result.ok && code == "OK") null else {
            LiteWebPreviewResult.Failed(code.takeUnless { it == "OK" } ?: "LITEWEB_PORT_APPLY_UNVERIFIED")
        }
    }

    /** root 只读 `webui.conf`（0600）；读不到/损坏 → null。 */
    private fun readWebuiConfig(): LiteWebPreviewConfig? {
        val result = runCatching {
            RootExec.execute(app, "cat ${LiteWebConstants.WEBUI_CONFIG_PATH} 2>/dev/null")
        }.getOrNull() ?: return null
        if (!result.ok) return null
        val parsed = LiteWebConfig.parse(result.output) ?: return null
        return runCatching { LiteWebPreviewConfig(parsed.token, parsed.port) }.getOrNull()
    }

    /** root 原子写 `webui.conf` 0600 并回读校验（无 root/失败 → false）。 */
    private fun writeWebuiConfig(config: LiteWebPreviewConfig): Boolean {
        val content = LiteWebConfig(
            token = config.token, port = config.port, enabled = false, startedAt = 0L,
        ).render() + "workMode=LITE\n"
        val quoted = "'" + content.replace("'", "'\\''") + "'"
        val script = "umask 077; mkdir -p /data/adb/maplesu/recovery || exit 1; " +
            "T=\$(mktemp ${LiteWebConstants.WEBUI_CONFIG_PATH}.XXXXXX) || exit 1; " +
            "trap 'rm -f \"\$T\"' EXIT; " +
            "printf '%s' $quoted > \"\$T\" && chmod 600 \"\$T\" && " +
            "mv -f \"\$T\" ${LiteWebConstants.WEBUI_CONFIG_PATH}"
        val result = runCatching { RootExec.execute(app, script) }.getOrNull() ?: return false
        if (!result.ok) return false
        val readback = readWebuiConfig() ?: return false
        return readback.token == config.token && readback.port == config.port
    }

    private val previewStore = object : LiteWebPreviewStore {
        override fun read(): LiteWebPreviewConfig? = readWebuiConfig()

        /** 只判断文件在不在（root `test -f`）；读失败≠不存在，不能拿它去生成新 token。 */
        override fun exists(): Boolean {
            val result = runCatching {
                RootExec.execute(app, "test -f ${LiteWebConstants.WEBUI_CONFIG_PATH} && echo YES || echo NO")
            }.getOrNull() ?: return true // 根通道都不可用时按“可能存在”保守处理，绝不覆盖
            if (!result.ok) return true
            return result.output.contains("YES")
        }

        override fun write(config: LiteWebPreviewConfig): Boolean = writeWebuiConfig(config)
    }

    /**
     * 能不能在本进程跑 Lite WebUI。
     *
     * `ModeRuntime.active == LITE` → 可以；`null`（冷启动未判定）→ 只有在有 LITE 证据时
     * 才允许（[LiteWebBackendPolicy.hasLiteEvidence]），否则保持拒绝并给出
     * `LITEWEB_MODE_UNRESOLVED`（这是 2026-10-31 真机缺陷 3：旧实现直接报
     * `LITEWEB_MODE_NOT_LITE`，把"还没判定完"说成"你不是 Lite 模式"）。
     * 明确是 KSU → 永远拒绝（模式隔离）。
     */
    fun canRunHere(): Boolean {
        val mode = ModeRuntime.active
        if (mode == WorkMode.LITE) return true
        if (mode != null) return false
        return liteEvidence()
    }

    /** LITE 证据：已装 root 实现（Ultra 内核 / Ultra 环境）+ 最近一次判定。只读缓存，不起 root。 */
    private fun liteEvidence(): Boolean = runCatching {
        LiteWebBackendPolicy.hasLiteEvidence(installedBackendProvider(), detectionState())
    }.getOrDefault(false)

    private fun detectionState(): ModeState? = runCatching { detectionStateProvider() }.getOrNull()

    /** 当前根通道（UI 卡显示「根通道：…」）。 */
    fun rootBackend(): RootBackend = backendProvider()

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    /** 设置页"启动"按钮（持久化 enabled=true）。 */
    suspend fun start(): LiteWebAdminStatus = startListener(persistEnabled = true)

    /** Prepare a live recovery entry without changing the user's automatic-start preference. */
    suspend fun startForRecovery(): LiteWebAdminStatus = startListener(persistEnabled = false)

    private data class ListenerRoute(
        val strategy: LiteWebStartStrategy,
        val warning: String,
        val unavailableCode: String,
    )

    private fun listenerRoute(): ListenerRoute {
        val mode = ModeRuntime.active
        val installed = runCatching { installedBackendProvider() }.getOrDefault(RootBackend.NONE)
        val evidence = runCatching {
            LiteWebBackendPolicy.hasLiteEvidence(installed, detectionState())
        }.getOrDefault(false)
        // 未判定时"设备上装了什么"才是可执行的事实（此时 preferredBackend 会无条件回
        // LITE_KERNEL，不能拿它选路）；已判定时仍按既有 preferredBackend 策略。
        val backend = if (mode == null) {
            installed
        } else {
            runCatching { backendProvider() }.getOrDefault(RootBackend.NONE)
        }
        val strategy = LiteWebBackendPolicy.strategy(mode, backend, evidence)
        val warning = LiteWebBackendPolicy.inferredWarning(mode, evidence)
        if (warning.isNotEmpty()) {
            android.util.Log.i(
                "LiteWebAdmin",
                "mode unresolved, inferred LITE from installed backend=$installed",
            )
        }
        return ListenerRoute(strategy, warning, LiteWebBackendPolicy.unavailableCode(mode, backend, evidence))
    }

    private suspend fun startListener(persistEnabled: Boolean): LiteWebAdminStatus = withContext(Dispatchers.IO) {
        val route = listenerRoute()
        val warning = route.warning
        if (route.strategy == LiteWebStartStrategy.UNAVAILABLE) {
            return@withContext offline(route.unavailableCode, warning)
        }
        if (route.strategy == LiteWebStartStrategy.ULTRA_ENV_DAEMON) {
            val daemon = runCatching { ultraDaemon.start(app) }.getOrElse {
                LiteWebDaemonStart.unsupported(it.javaClass.simpleName)
            }
            if (!daemon.started) {
                val code = daemon.code.ifEmpty { LiteWebBackendPolicy.ULTRA_ENV_DAEMON_UNSUPPORTED }
                return@withContext offline(code, warning)
            }
            if (persistEnabled) prefs.edit().putBoolean(KEY_ENABLED, true).apply()
            val status = LiteWebAdminStatus(
                running = true,
                port = daemon.port,
                url = "",
                enabled = persistEnabled || isEnabled(),
                error = "",
                warning = warning,
            )
            cached = status
            return@withContext status
        }
        val status = runCatching { withService { ILiteWebAdmin.Stub.asInterface(it).start() } }
            .map { LiteWebAdminStatus.fromBundle(it) }
            .getOrElse { offline(codeOf(it, "LITEWEB_START_FAILED"), warning) }
        if (status.running && persistEnabled) prefs.edit().putBoolean(KEY_ENABLED, true).apply()
        val reported = status.copy(warning = warning)
        cached = reported
        reported
    }

    /** 设置页"停止"按钮。 */
    suspend fun stop(): LiteWebAdminStatus = withContext(Dispatchers.IO) {
        val route = listenerRoute()
        if (route.strategy == LiteWebStartStrategy.UNAVAILABLE) {
            return@withContext offline(route.unavailableCode, route.warning)
        }
        if (route.strategy == LiteWebStartStrategy.ULTRA_ENV_DAEMON) {
            val stopped = recoveryControl.stop()
            if (stopped.state != RecoveryWebUiState.STOPPED) {
                return@withContext offline(stopped.error.ifEmpty { "LITEWEB_STOP_FAILED" }, route.warning)
            }
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            cached = offline("", route.warning)
            return@withContext cached!!
        }
        val status = runCatching { withService { ILiteWebAdmin.Stub.asInterface(it).stop() } }
            .map { LiteWebAdminStatus.fromBundle(it) }
            .getOrElse { offline(codeOf(it, "LITEWEB_STOP_FAILED")) }
        if (!status.running && status.error.isEmpty()) prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        cached = status.copy(url = "")
        cached!!
    }

    /** 开关语义：enabled=true → start；false → stop。 */
    suspend fun enable(enabled: Boolean): LiteWebAdminStatus =
        if (enabled) start() else stop()

    /** 重新生成 token（旧 URL 立即失效）；运行中会按原端口候选重启。 */
    suspend fun resetToken(): LiteWebAdminStatus = withContext(Dispatchers.IO) {
        val route = listenerRoute()
        if (route.strategy == LiteWebStartStrategy.UNAVAILABLE) {
            return@withContext offline(route.unavailableCode, route.warning)
        }
        previewCoordinator().invalidate()
        if (route.strategy == LiteWebStartStrategy.ULTRA_ENV_DAEMON) {
            val current = readWebuiConfig()
                ?: return@withContext offline("LITEWEB_CONFIG_UNAVAILABLE", route.warning)
            val before = recoveryControl.status()
            if (before.state == RecoveryWebUiState.UNKNOWN) {
                return@withContext offline(before.error.ifEmpty { "LITEWEB_STATUS_UNAVAILABLE" }, route.warning)
            }
            if (before.state == RecoveryWebUiState.RUNNING) {
                val stopped = recoveryControl.stop()
                if (stopped.state != RecoveryWebUiState.STOPPED) {
                    return@withContext offline(stopped.error.ifEmpty { "LITEWEB_STOP_FAILED" }, route.warning)
                }
            }
            val rotated = current.copy(token = LiteWebToken.generate())
            if (!writeWebuiConfig(rotated)) {
                return@withContext offline("LITEWEB_RESET_FAILED", route.warning)
            }
            return@withContext if (before.state == RecoveryWebUiState.RUNNING) {
                startListener(persistEnabled = false)
            } else {
                offline("", route.warning).also { cached = it }
            }
        }
        val status = runCatching { withService { ILiteWebAdmin.Stub.asInterface(it).resetToken() } }
            .map { LiteWebAdminStatus.fromBundle(it) }
            .getOrElse { offline(codeOf(it, "LITEWEB_RESET_FAILED")) }
        cached = status
        status
    }

    /** 当前状态（不启动服务；服务未运行时返回 enabled 标记 + 错误）。 */
    suspend fun status(): LiteWebAdminStatus = withContext(Dispatchers.IO) {
        val route = listenerRoute()
        if (route.strategy == LiteWebStartStrategy.UNAVAILABLE) {
            return@withContext offline(route.unavailableCode, route.warning)
        }
        if (route.strategy == LiteWebStartStrategy.ULTRA_ENV_DAEMON) {
            val control = recoveryControl.status()
            val config = if (control.state == RecoveryWebUiState.RUNNING) readWebuiConfig() else null
            val status = when (control.state) {
                RecoveryWebUiState.RUNNING -> if (config == null) {
                    offline("LITEWEB_CONFIG_UNAVAILABLE", route.warning)
                } else LiteWebAdminStatus(
                    running = true, port = config.port, url = config.url(), enabled = isEnabled(), warning = route.warning,
                )
                RecoveryWebUiState.STOPPED -> offline("", route.warning)
                RecoveryWebUiState.UNKNOWN -> offline(control.error.ifEmpty { "LITEWEB_STATUS_UNAVAILABLE" }, route.warning)
            }
            cached = status
            return@withContext status
        }
        val status = runCatching { withService { ILiteWebAdmin.Stub.asInterface(it).status() } }
            .map { LiteWebAdminStatus.fromBundle(it) }
            .getOrElse { offline(codeOf(it, "LITEWEB_STATUS_UNAVAILABLE")) }
        cached = status
        status
    }

    /** 开机/进入 Lite 后按用户设置恢复：只有 enabled 且当前为 Lite 才启动。 */
    suspend fun startIfEnabled(): LiteWebAdminStatus {
        if (!isEnabled() || !canRunHere()) return offline("LITEWEB_NOT_ENABLED")
        return start()
    }

    private fun offline(code: String, warning: String = "") = LiteWebAdminStatus(
        running = false,
        port = null,
        url = "",
        enabled = isEnabled(),
        error = code,
        warning = warning,
    )

    private fun codeOf(error: Throwable, fallback: String): String {
        val message = error.message.orEmpty()
        return Regex("[A-Z][A-Z0-9_]{3,}").find(message)?.value ?: fallback
    }

    /** RootService bind 的最小封装（模式与 InstallerServiceClient.withService 一致）。 */
    private fun <T> withService(action: (IBinder) -> T): T {
        check(Looper.myLooper() != Looper.getMainLooper()) { "LITEWEB_BACKGROUND_THREAD_REQUIRED" }
        val handler = Handler(Looper.getMainLooper())
        val connected = CompletableFuture<IBinder>()
        val task = CompletableFuture<Shell.Task?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { connected.complete(binder) }
            override fun onServiceDisconnected(name: ComponentName) {
                connected.completeExceptionally(IllegalStateException("LITEWEB_SERVICE_DISCONNECTED"))
            }
            override fun onNullBinding(name: ComponentName) {
                connected.completeExceptionally(IllegalStateException("LITEWEB_SERVICE_NULL_BINDING"))
            }
            override fun onBindingDied(name: ComponentName) {
                connected.completeExceptionally(IllegalStateException("LITEWEB_SERVICE_DISCONNECTED"))
            }
        }
        fun useService(): T {
            val binder = connected.get(20, TimeUnit.SECONDS)
            check(binder.isBinderAlive) { "LITEWEB_SERVICE_DISCONNECTED" }
            return action(binder)
        }
        try {
            handler.post {
                try {
                    val intent = Intent(app, LiteWebAdminService::class.java)
                        .addCategory(RootService.CATEGORY_DAEMON_MODE)
                    task.complete(RootService.bindOrTask(intent, app.mainExecutor, connection))
                } catch (error: Exception) {
                    task.completeExceptionally(error)
                }
            }
            val launch = task.get(5, TimeUnit.SECONDS)
            if (launch != null) {
                // 首次启动需要一次已认证的 root shell 来拉起 RootService。
                val shell = MapleSuRuntime.openShell(app)
                try {
                    shell.execTask(launch)
                } finally {
                    runCatching { shell.close() }
                }
            }
            return useService()
        } finally {
            handler.post { RootService.unbind(connection) }
        }
    }

    private companion object {
        /** A-only 功能开关（Lite 专属；B 模式 gate 在 canRunHere 拒绝）。 */
        const val PREFERENCES = "liteweb"
        const val KEY_ENABLED = "enabled"

        /** 旧版端口 prefs 兜底；权威来源仍为 webui.conf。 */
        const val KEY_PORT = "webuiPort"
    }
}
