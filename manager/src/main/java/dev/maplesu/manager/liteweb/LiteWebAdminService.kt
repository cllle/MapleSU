package dev.maplesu.manager.liteweb

import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.maplesu.core.ui.security.LiteHiddenController
import com.topjohnwu.superuser.ipc.RootService
import dev.maplesu.manager.litehidden.LiteHiddenOutcome
import dev.maplesu.manager.litehidden.LiteHiddenState
import dev.maplesu.manager.modules.LiteScriptModuleBackend
import dev.maplesu.manager.runtime.ManagerCallerVerifier
import dev.maplesu.manager.liteweb.daemon.DaemonUltraSdkAccess
import dev.maplesu.manager.liteweb.daemon.DaemonRuntimeShell
import dev.maplesu.manager.liteweb.daemon.DaemonSuperuserPort
import java.io.File
import java.io.FileInputStream
import java.security.SecureRandom

/**
 * H2：Lite 网页管理器的 RootService 宿主（root 进程内托管 HTTP 服务）。
 *
 * 复用 libsu 已有的 `RootService` 长驻 root 进程机制（同 `NativeSuDaemonService`），不依赖
 * ksud/Ultra；App 通过 [ILiteWebAdmin] 控制 start/stop/resetToken/status。
 *
 * 安全：只绑 127.0.0.1；配置 0600 由本进程原子写；token 只出现在内存/回执，日志一律脱敏。
 */
class LiteWebAdminService : RootService() {
    private lateinit var verifier: ManagerCallerVerifier
    private lateinit var store: LiteWebConfigStore
    private val clock: () -> Long = { System.currentTimeMillis() }

    @Volatile
    private var config: LiteWebConfig? = null

    @Volatile
    private var server: LiteWebServer? = null

    @Volatile
    private var backend: AndroidLiteWebBackend? = null

    @Volatile
    private var lastError: String = ""

    private var ownerLease: dev.maplesu.manager.liteweb.daemon.DaemonProcessLease? = null

    override fun onCreate() {
        super.onCreate()
        check(Process.myUid() == 0) { "LITEWEB_ROOT_REQUIRED" }
        verifier = ManagerCallerVerifier(this)
        store = LiteWebConfigStore(File(LiteWebConstants.WEBUI_CONFIG_PATH), clock)
    }

    override fun onBind(intent: Intent): IBinder = object : ILiteWebAdmin.Stub() {
        override fun start(): Bundle = verifier.withCallingManager { startServer().toBundle() }

        override fun stop(): Bundle = verifier.withCallingManager {
            stopServer(persistEnabled = false)
            statusBundle()
        }

        override fun status(): Bundle = verifier.withCallingManager { statusBundle() }

        override fun resetToken(): Bundle = verifier.withCallingManager { rotateToken() }
    }

    override fun onDestroy() {
        stopServer(persistEnabled = false)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ lifecycle

    @Synchronized
    private fun startServer(): LiteWebAdminStatus {
        server?.let { existing ->
            if (existing.isRunning()) return statusValue(rotatedToken = null)
        }
        if (!acquireOwnerLease()) return statusValue(null)
        val current = try {
            loadConfigOrCreate()
        } catch (error: Exception) {
            lastError = codeOf(error, "LITEWEB_CONFIG_CORRUPT")
            releaseOwnerLease()
            return statusValue(null)
        }
        val webBackend = AndroidLiteWebBackend(this, { config?.port ?: current.port }, ::applyPort)
        val requestHandler = LiteWebRequestHandler(
            tokenProvider = { config?.token },
            backend = webBackend,
        )
        val candidate = LiteWebServer(
            handler = requestHandler,
            portCandidates = LiteWebPortPolicy.preferred(current.port),
            logger = { line -> Log.i(TAG, LiteWebRedaction.redact(line, config?.token)) },
            secretProvider = { config?.token },
        )
        backend = webBackend
        config = current
        when (val result = candidate.start()) {
            is LiteWebStartResult.Started -> {
                server = candidate
                lastError = ""
                val updated = current.copy(
                    port = result.port,
                    enabled = true,
                    startedAt = clock(),
                )
                try {
                    saveConfig(updated)
                    config = updated
                } catch (error: Exception) {
                    candidate.stop()
                    webBackend.close()
                    backend = null
                    server = null
                    releaseOwnerLease()
                    lastError = codeOf(error, "LITEWEB_CONFIG_WRITE_FAILED")
                    Log.w(TAG, lastError, error)
                }
                return statusValue(null)
            }
            is LiteWebStartResult.Failed -> {
                runCatching { candidate.stop() }
                webBackend.close()
                backend = null
                server = null
                releaseOwnerLease()
                lastError = result.code
                Log.w(TAG, "${result.code}: ${result.detail} attempts=${result.attempts}")
                return statusValue(null)
            }
        }
    }

    @Synchronized
    private fun stopServer(persistEnabled: Boolean, releaseLease: Boolean = true) {
        server?.let { runCatching { it.stop() } }
        server = null
        backend?.close()
        backend = null
        config?.let { current ->
            val updated = current.copy(enabled = false)
            config = updated
            if (persistEnabled) runCatching { saveConfig(updated) }
        }
        if (releaseLease) releaseOwnerLease()
    }

    @Synchronized
    private fun rotateToken(): Bundle {
        if (!acquireOwnerLease()) return statusBundle()
        val wasRunning = server?.isRunning() == true
        stopServer(persistEnabled = false, releaseLease = false)
        return try {
            val rotated = loadConfigOrCreate().copy(
                token = LiteWebToken.generate(SecureRandom()), enabled = wasRunning, startedAt = 0L,
            )
            saveConfig(rotated)
            config = rotated
            if (wasRunning) startServer().toBundle() else statusBundle()
        } finally {
            if (server == null) releaseOwnerLease()
        }
    }

    private fun acquireOwnerLease(): Boolean {
        if (ownerLease != null) return true
        return try {
            val directory = File(dev.maplesu.manager.recovery.RecoveryPaths.DIR)
            check(directory.isDirectory || directory.mkdirs()) { "LITEWEB_CONFIG_WRITE_FAILED" }
            ownerLease = dev.maplesu.manager.liteweb.daemon.DaemonProcessLease.acquire(directory, Process.myPid().toString())
            if (ownerLease == null) lastError = "LITEWEB_DAEMON_OWNER_ACTIVE"
            ownerLease != null
        } catch (error: Exception) {
            lastError = codeOf(error, "LITEWEB_DAEMON_LEASE_FAILED")
            false
        }
    }

    private fun releaseOwnerLease() {
        try { ownerLease?.close() } finally { ownerLease = null }
    }

    private fun statusBundle(): Bundle = statusValue(null).toBundle()

    private fun statusValue(rotatedToken: String?): LiteWebAdminStatus {
        val current = config
        val running = server?.isRunning() == true
        val port = server?.boundPort?.takeIf { it > 0 }
        return LiteWebAdminStatus(
            running = running,
            port = port,
            url = if (running && port != null && current != null) {
                LiteWebUrls.url(port, current.token)
            } else "",
            enabled = current?.enabled == true,
            error = lastError,
        )
    }

    private fun loadConfigOrCreate(): LiteWebConfig {
        store.load()?.let { return it }
        LiteWebConfigStore(File(LiteWebConstants.CONFIG_PATH), clock).load()?.let {
            saveConfig(it)
            return it
        }
        val created = LiteWebConfig(
            token = LiteWebToken.generate(SecureRandom()),
            port = LiteWebConstants.PORT_CANDIDATES.first(),
            enabled = false,
            startedAt = 0L,
        )
        saveConfig(created)
        return created
    }

    private fun saveConfig(value: LiteWebConfig) {
        store.save(value)
    }

    @Synchronized
    private fun applyPort(port: Int): LiteWebRestoreStep {
        val listener = server ?: return LiteWebRestoreStep("applyPort", false, "LITEWEB_NOT_RUNNING")
        val previous = config ?: return LiteWebRestoreStep("applyPort", false, "LITEWEB_CONFIG_UNAVAILABLE")
        val updated = previous.copy(port = port)
        val result = listener.rebind(port) {
            check(store.load() == previous) { "RECOVERY_CONFIG_CHANGED" }
            saveConfig(updated)
            config = updated
        }
        return when (result) {
            is LiteWebStartResult.Started -> LiteWebRestoreStep("applyPort", true)
            is LiteWebStartResult.Failed -> LiteWebRestoreStep("applyPort", false, result.code, result.detail)
        }
    }

    private fun codeOf(error: Exception, fallback: String): String =
        error.message?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]+")) } ?: fallback

    private companion object {
        const val TAG = "LiteWebAdmin"
    }
}

/** 控制面回执（Bundle wire 见 [toBundle]）；token 只在 start/reset 的 url 中出现。 */
data class LiteWebAdminStatus(
    val running: Boolean,
    val port: Int?,
    val url: String,
    val enabled: Boolean,
    val error: String = "",
    /** 非致命提示（例如 `LITEWEB_MODE_INFERRED_LITE`）：UI 可忽略，排障用。 */
    val warning: String = "",
) {
    fun toBundle(): Bundle = Bundle().apply {
        putBoolean("running", running)
        putInt("port", port ?: -1)
        putString("url", url)
        putBoolean("enabled", enabled)
        putString("error", error)
        putString("warning", warning)
        putInt("serverVersion", LiteWebConstants.SERVER_VERSION)
    }

    companion object {
        fun fromBundle(bundle: Bundle): LiteWebAdminStatus = LiteWebAdminStatus(
            running = bundle.getBoolean("running", false),
            port = bundle.getInt("port", -1).takeIf { it > 0 },
            url = bundle.getString("url").orEmpty(),
            enabled = bundle.getBoolean("enabled", false),
            error = bundle.getString("error").orEmpty(),
            warning = bundle.getString("warning").orEmpty(),
        )
    }
}

/** `webadmin.conf` 读写（root 进程 0600，tmp+fsync+ATOMIC_MOVE）。 */
class LiteWebConfigStore(
    private val file: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun load(): LiteWebConfig? {
        if (!file.isFile) return null
        if (file.length() > 4096) throw IllegalStateException("LITEWEB_CONFIG_TOO_LARGE")
        val text = FileInputStream(file).use { it.readBytes().toString(Charsets.UTF_8) }
        return LiteWebConfig.parse(text) ?: throw IllegalStateException("LITEWEB_CONFIG_CORRUPT")
    }

    fun save(config: LiteWebConfig) {
        dev.maplesu.manager.liteweb.daemon.LiteWebDaemonConfig.save(
            file.absolutePath, config.render() + "workMode=LITE\n",
        )
    }
}

/** 只读事实端口的生产实现（root 进程内；模块/授权均为现有 Lite 后端的只读投影）。 */
class AndroidLiteWebBackend(
    private val service: LiteWebAdminService,
    private val portValue: () -> Int = { LiteWebPortPolicy.DEFAULT_PORT },
    private val changePort: (Int) -> LiteWebRestoreStep = { UnavailableActivatePort.applyPort(it) },
) : LiteWebBackend, AutoCloseable {
    override fun configuredPort(): Int = portValue()

    override fun applyConfiguredPort(port: Int): LiteWebRestoreStep = changePort(port)

    private val moduleFiles = LiteWebModuleFiles()
    private val moduleBridge = com.maplesu.core.ui.webui.ExternalWebUiBridge(
        files = moduleFiles,
        cacheRoot = File(service.cacheDir, "module-webui"),
        apps = { com.maplesu.core.ui.webui.rootWebUiApps(service.applicationInfo.uid / 100000) },
        sourceCurrent = {
            !File(dev.maplesu.manager.liteweb.RECOVERY_WEBUI_PAUSE_PATH).exists()
        },
    )
    override fun moduleBridgePort(): LiteWebModuleBridgePort = moduleBridge
    override fun close() = moduleBridge.close()

    private val liteModules = LiteScriptModuleBackend.get(service)
    private val nativeClient = dev.maplesu.manager.runtime.ultra.UltraRuntimeClient {
        dev.maplesu.manager.modules.UltraRootKeyStore.read(service)
    }
    private val nativeModules = dev.maplesu.manager.liteweb.daemon.DaemonUltraRuntimeFacts(
        DaemonUltraSdkAccess(DaemonRuntimeShell),
        // An installed root service uses the app's key; recovery backups belong to the standalone daemon.
        openSdk = { dev.maplesu.manager.liteweb.daemon.DaemonUltraSdkResult.Ready(nativeClient, "installed_manager") },
    )
    private val assets = AndroidLiteWebAssets(service.assets)
    private val hidden = AndroidLiteHiddenPort()
    private val superusers = DaemonSuperuserPort(DaemonUltraSdkAccess(DaemonRuntimeShell), DaemonRuntimeShell)

    override fun superuserPort(): LiteWebSuperuserPort = superusers

    override fun status(): LiteWebStatusView {
        val modulesJson = modulesJson()
        val count = LiteWebModuleList.count(modulesJson)
        return LiteWebStatusView(
            mode = "LITE",
            rootAvailable = Process.myUid() == 0,
            moduleCount = count.count,
            hidden = hidden.currentHidden() ?: false,
            uapi = "lite",
            moduleCountError = count.error,
        )
    }

    override fun modulesJson(): String = runCatching {
        val native = nativeModules.ultraModules()
        if (native.error.isNotEmpty()) return native.responseJson()
        val rows = org.json.JSONArray(liteModules.listJson())
        val ultra = org.json.JSONArray(native.json)
        for (index in 0 until ultra.length()) rows.put(ultra.getJSONObject(index))
        rows.toString()
    }.getOrElse { "{\"ok\":false,\"error\":\"MODULE_LIST_UNAVAILABLE\"}" }

    override fun superusersJson(): String {
        return when (val snapshot = superusers.snapshot()) {
            is LiteWebSuResult.Ok -> snapshot.value.entriesJson()
            is LiteWebSuResult.Failed -> throw IllegalStateException(snapshot.code)
        }
    }

    /**
     * Design §5.2: Ultra now records su audit lines, so `sulog` is available whenever the
     * file exists - no more `available=false` ("Ultra does not record su logs"). Normal's
     * `DaemonLiteWebBackend` keeps reading `/data/adb/maplesu/logs/su.log`; the line
     * grammar is the same JSONL, so the UI parser (`SulogHelper`) is unchanged.
     */
    override fun sulog(): LiteWebSulogView {
        if (!dev.maplesu.manager.runtime.SuAuditLog.exists(service)) {
            return LiteWebSulogView(
                available = false,
                reason = "no su audit recorded yet (no su request has been served)",
            )
        }
        return LiteWebSulogView(
            available = true,
            lines = dev.maplesu.manager.runtime.SuAuditLog.tail(service),
        )
    }

    override fun toggleModule(moduleId: String, enabled: Boolean): LiteWebToggleView =
        try {
            if (moduleId.startsWith("maplesu:")) {
                nativeModules.toggleUltraModule(moduleId, enabled)
            } else if (liteModules.setEnabled(moduleId, enabled)) {
                LiteWebToggleView.Ok(moduleId, enabled)
            } else {
                LiteWebToggleView.Failed("MODULE_TOGGLE_REJECTED", "backend returned false")
            }
        } catch (error: Exception) {
            LiteWebToggleView.Failed("MODULE_TOGGLE_FAILED", error.message.orEmpty())
        }

    override fun assets(): LiteWebAssets = assets

    /**
     * 模块自带 WebUI 的静态文件（root 进程内直读，A 模式没有 ksud `/modweb` 的替代）。
     *
     * 安全性：`LiteWebModWeb` 已经在字符串层拒绝 `..`/点文件/控制字符，这里再做一次
     * **canonical path** 复核 —— 模块目录里可能有指向外部的符号链接，只靠字符串校验挡不住。
     */
    override fun moduleWebFile(moduleId: String, relativePath: String): LiteWebModuleWeb =
        moduleFiles.read(moduleId, relativePath)

    override fun hiddenPort(): LiteHiddenPort = hidden
}

/**
 * r5-ui `LiteHiddenController` 的只读/写适配。root 服务进程内若模式运行时/凭据尚未就绪，
 * 控制器返回 NOT_SUPPORTED/异常 —— 统一映射成 [LiteHiddenResult.Unavailable]（501 诚实降级）。
 */
class AndroidLiteHiddenPort : LiteHiddenPort {
    override fun currentHidden(): Boolean? = runCatching {
        when (LiteHiddenController.readState()) {
            LiteHiddenState.ON -> true
            LiteHiddenState.OFF -> false
            LiteHiddenState.UNKNOWN -> null
        }
    }.getOrNull()

    override fun setHidden(hidden: Boolean, level: String?): LiteHiddenResult = try {
        when (val outcome = LiteHiddenController.setEnabled(hidden)) {
            LiteHiddenOutcome.SUCCESS -> LiteHiddenResult.Ok(hidden, if (hidden) "hidden enabled" else "hidden disabled")
            LiteHiddenOutcome.NOT_SUPPORTED ->
                LiteHiddenResult.Unavailable("Ultra hidden controller: mode runtime not active in this process")
            LiteHiddenOutcome.NOT_AUTHORIZED ->
                LiteHiddenResult.Failed("HIDDEN_NOT_AUTHORIZED", "controller rejected the caller")
            LiteHiddenOutcome.FAILED ->
                LiteHiddenResult.Failed("HIDDEN_WRITE_FAILED", "controller write/readback failed")
        }
    } catch (error: Exception) {
        LiteHiddenResult.Unavailable("Ultra hidden controller unavailable: ${error.javaClass.simpleName}")
    }
}

/** `assets/liteweb/` 只读（不存在 → null，由 handler 返回 404）。 */
class AndroidLiteWebAssets(private val assets: android.content.res.AssetManager) : LiteWebAssets {
    override fun read(relativePath: String): ByteArray? = try {
        assets.open("liteweb/$relativePath").use { it.readBytes() }
    } catch (_: java.io.IOException) {
        null
    }
}
