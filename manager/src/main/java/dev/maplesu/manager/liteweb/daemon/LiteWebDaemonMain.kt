package dev.maplesu.manager.liteweb.daemon

import dev.maplesu.manager.liteweb.LiteWebAuth
import dev.maplesu.manager.liteweb.LiteWebConstants
import dev.maplesu.manager.liteweb.LiteWebHandler
import dev.maplesu.manager.liteweb.LiteWebHttpRequest
import dev.maplesu.manager.liteweb.LiteWebHttpResponse
import dev.maplesu.manager.liteweb.LiteWebRequestHandler
import dev.maplesu.manager.liteweb.LiteWebServer
import dev.maplesu.manager.liteweb.LiteWebStartResult
import dev.maplesu.manager.liteweb.LiteWebToken
import dev.maplesu.manager.recovery.LocalRecoveryFs
import dev.maplesu.manager.recovery.RecoveryBundle
import dev.maplesu.manager.recovery.RecoveryCommandResult
import dev.maplesu.manager.recovery.RecoveryCommandRunner
import dev.maplesu.manager.recovery.RecoveryPaths
import dev.maplesu.manager.recovery.RecoveryRestoreReport
import dev.maplesu.manager.recovery.RecoveryRestoreService
import dev.maplesu.manager.recovery.RecoveryCleanupResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch

/**
 * 自包含 WebUI daemon（**无 Android Context 依赖**）：卸载管理器后由 root 进程（app_process）
 * 从 `/data/adb/maplesu/recovery/` 读 config/APK，监听 127.0.0.1:<port>，token 鉴权。
 *
 * 分层（全部纯 JDK）：
 * - [LiteWebDaemonConfig]：`webui.conf` 解析（token + 端口）；
 * - [DaemonLiteWebBackend]：只读/受限 root 后端（APK 静态资源、模块、隐藏态、su 日志）；
 * - [LiteWebDaemonCore]：**daemon 自己的契约层**（路径 token + 三条既有契约 + 转发）；
 * - `LiteWebHandler`/[LiteWebServer]：复用 App 侧纯 JVM HTTP 层（有界请求、
 *   loopback 绑定、脱敏日志、连接超时），不再自己写 socket 解析；
 * - [DaemonRequestHandler]：wire 层 Host 强制 + 把 daemon 响应转成 HTTP 响应；
 * - [LiteWebDaemonMain]：`app_process` 入口。
 *
 * 鉴权形状：`/<prefix>/<64hex token>/<route>`（恢复链接形状，路径 token 优先），
 * 旧式 `?token=` / `x-maplesu-token` 仍接受（兼容既有测试与手工调用），但**不再生成**。
 * token 常量时间比较、绝不打印。
 */
data class LiteWebDaemonConfig(val port: Int, val token: String, val workMode: String? = null) {
    companion object {
        const val PATH_PREFIX = LiteWebConstants.PATH_PREFIX
        fun parse(text: String): LiteWebDaemonConfig? {
            val values = text.lineSequence()
                .mapNotNull { line ->
                    val index = line.indexOf('=')
                    if (index <= 0) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
                }
                .toMap()
            val port = (values["webuiPort"] ?: values["port"])?.toIntOrNull() ?: return null
            val token = values["token"].orEmpty()
            if (port !in 1..65535) return null
            if (token.length < 16 || !token.all { it.isLetterOrDigit() }) return null
            val mode = values["workMode"]
            if (mode != null && mode !in setOf("LITE", "KSU")) return null
            return LiteWebDaemonConfig(port, token, mode)
        }

        fun load(path: String = RecoveryPaths.WEBUI_CONFIG): LiteWebDaemonConfig? =
            runCatching { File(path).readText(Charsets.UTF_8) }.getOrNull()?.let(::parse)

        /** Only a stopped listener may change config offline; live owners use authenticated HTTP rebind. */
        internal fun updateStoppedPort(directory: File, expectedIdentity: String, port: Int, pid: String): String? {
            dev.maplesu.manager.liteweb.LiteWebPortPolicy.validate(port)?.let { return it }
            if (!expectedIdentity.matches(Regex("[0-9a-f]{64}"))) return "RECOVERY_CONFIG_CHANGED"
            return try {
                val lease = DaemonProcessLease.acquire(directory, pid) ?: return "LITEWEB_DAEMON_OWNER_ACTIVE"
                lease.use {
                    val path = File(directory, "webui.conf").absolutePath
                    val current = load(path) ?: return "LITEWEB_DAEMON_CONFIG_MISSING"
                    if (current.workMode != "LITE") return "LITEWEB_MODE_NOT_LITE"
                    if (dev.maplesu.manager.liteweb.liteWebPortIdentity(current.port, current.token) != expectedIdentity) {
                        return "RECOVERY_CONFIG_CHANGED"
                    }
                    // Reserve the candidate until save+manifest readback completes; never bind the old port.
                    java.net.ServerSocket().use { candidate ->
                        candidate.bind(java.net.InetSocketAddress(InetAddress.getByName(LiteWebConstants.LOOPBACK), port))
                        updatePort(path, current, port)
                    }
                }
                null
            } catch (_: java.net.BindException) {
                dev.maplesu.manager.liteweb.LiteWebPortPolicy.CODE_CONFLICT
            } catch (error: Exception) {
                error.message?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]+")) } ?: "LITEWEB_CONFIG_WRITE_FAILED"
            }
        }

        internal fun updatePort(path: String, expected: LiteWebDaemonConfig, port: Int): LiteWebDaemonConfig {
            require(dev.maplesu.manager.liteweb.LiteWebPortPolicy.validate(port) == null)
            val file = File(path)
            check(!java.nio.file.Files.isSymbolicLink(file.toPath())) { "LITEWEB_CONFIG_INVALID" }
            check(file.isFile && file.length() <= 4096) { "LITEWEB_CONFIG_INVALID" }
            val original = file.readText(Charsets.UTF_8)
            check(parse(original) == expected) { "RECOVERY_CONFIG_CHANGED" }
            val updated = original.lineSequence().filterNot {
                it.substringBefore('=').trim() in setOf("port", "webuiPort")
            }.joinToString("\n").trimEnd() + "\nwebuiPort=$port\n"
            save(path, updated, original)
            return expected.copy(port = port)
        }

        /** Runtime config and its existing local integrity entry change together; other backup entries stay fixed. */
        @Synchronized
        internal fun save(path: String, text: String, expectedText: String? = null) {
            check(parse(text) != null) { "LITEWEB_CONFIG_INVALID" }
            val file = File(path)
            val parent = file.parentFile ?: error("LITEWEB_CONFIG_INVALID")
            check(!java.nio.file.Files.isSymbolicLink(file.toPath())) { "LITEWEB_CONFIG_INVALID" }
            if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) error("LITEWEB_CONFIG_WRITE_FAILED")
            check(!file.exists() || file.isFile && file.length() <= 4096) { "LITEWEB_CONFIG_INVALID" }
            val original = file.takeIf { it.exists() }?.readBytes()
            if (expectedText != null) check(original?.toString(Charsets.UTF_8) == expectedText) { "RECOVERY_CONFIG_CHANGED" }
            val updated = text.toByteArray(Charsets.UTF_8)
            check(updated.size <= 4096) { "LITEWEB_CONFIG_TOO_LARGE" }
            val manifestFile = File(parent, "manifest.json")
            val manifestBytes = if (file.name == "webui.conf" && manifestFile.exists()) {
                check(!java.nio.file.Files.isSymbolicLink(manifestFile.toPath()) && manifestFile.isFile) { "RECOVERY_MANIFEST_CORRUPT" }
                manifestFile.readBytes()
            } else null
            val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
            val serializer = dev.maplesu.manager.recovery.RecoveryManifest.serializer()
            val bundle = RecoveryBundle(LocalRecoveryFs(), directory = parent.absolutePath, locationPolicy = { true })
            val updatedManifest = manifestBytes?.let { bytes ->
                val manifest = json.decodeFromString(serializer, bytes.toString(Charsets.UTF_8))
                check(manifest.schema <= dev.maplesu.manager.recovery.RecoveryManifest.SCHEMA &&
                    manifest.integritySha256 == bundle.integrity(manifest)) { "RECOVERY_MANIFEST_CORRUPT" }
                val entries = manifest.items.filter { it.role == "WEBUI_CONFIG" || it.relativePath == "webui.conf" }
                if (entries.isEmpty()) null else {
                    check(entries.size == 1 && entries.single().role == "WEBUI_CONFIG" &&
                        entries.single().relativePath == "webui.conf") { "RECOVERY_MANIFEST_CORRUPT" }
                    val entry = entries.single()
                    check(original != null && entry.size == original.size.toLong() &&
                        entry.sha256 == sha256(original)) { "RECOVERY_CONFIG_CHANGED" }
                    val next = manifest.copy(items = manifest.items.map {
                        if (it == entry) it.copy(size = updated.size.toLong(), sha256 = sha256(updated)) else it
                    })
                    json.encodeToString(serializer, next.copy(integritySha256 = bundle.integrity(next))).toByteArray(Charsets.UTF_8)
                }
            }
            fun replace(target: File, bytes: ByteArray?) {
                if (bytes == null) {
                    java.nio.file.Files.deleteIfExists(target.toPath())
                    return
                }
                val temporary = java.nio.file.Files.createTempFile(parent.toPath(), ".${target.name}-", ".tmp")
                var firstError: Exception? = null
                try {
                    try {
                        java.nio.file.Files.setPosixFilePermissions(temporary, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
                    } catch (_: UnsupportedOperationException) {
                        // Windows source fixtures have no POSIX permissions; Android does.
                    }
                    java.io.FileOutputStream(temporary.toFile()).use { output ->
                        output.write(bytes)
                        output.fd.sync()
                    }
                    java.nio.file.Files.move(temporary, target.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    if (parent.toPath().fileSystem.supportedFileAttributeViews().contains("posix")) {
                        java.nio.channels.FileChannel.open(parent.toPath(), java.nio.file.StandardOpenOption.READ).use { it.force(true) }
                    }
                } catch (error: Exception) {
                    firstError = error
                    throw error
                } finally {
                    try {
                        java.nio.file.Files.deleteIfExists(temporary)
                    } catch (cleanup: Exception) {
                        if (firstError != null) firstError.addSuppressed(cleanup) else throw cleanup
                    }
                }
            }
            try {
                replace(file, updated)
                if (updatedManifest != null) replace(manifestFile, updatedManifest)
                check(file.readBytes().contentEquals(updated) && load(path) == parse(text)) { "LITEWEB_PORT_READBACK_MISMATCH" }
                if (updatedManifest != null) check(manifestFile.readBytes().contentEquals(updatedManifest)) { "RECOVERY_MANIFEST_READBACK_FAILED" }
            } catch (error: Exception) {
                for ((target, bytes) in listOf(file to original, manifestFile to manifestBytes).take(if (updatedManifest == null) 1 else 2)) {
                    try {
                        replace(target, bytes)
                        check(if (bytes == null) !target.exists() else target.readBytes().contentEquals(bytes)) { "LITEWEB_PORT_ROLLBACK_FAILED" }
                    } catch (rollback: Exception) {
                        error.addSuppressed(rollback)
                    }
                }
                throw error
            }
        }

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

/**
 * daemon 响应。
 *
 * [body] 是文本投影（既有测试/契约读它）；[binary] 是**真实字节**（静态资源，UTF-8 往返会
 * 破坏 png/woff2）。socket 层优先写 [binary]，为 null 时回退 [body]。
 */
data class DaemonResponse(
    val status: Int,
    val body: String,
    val contentType: String = "application/json; charset=utf-8",
    val binary: ByteArray? = null,
    val afterResponse: (() -> Unit)? = null,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * daemon HTTP 核心：鉴权 + 三条既有契约 + 转发给 [LiteWebHandler]（即 App 侧同一个
 * [LiteWebRequestHandler]）。host 测试直接调用 [handle]。
 *
 * 路由（前缀 `/<PATH_PREFIX>/<token>`，路径 token 优先，其次 `?token=`，最后 `x-maplesu-token`）：
 * - `GET /ping` → `{"ok":true,"service":"maplesu-liteweb-daemon"}`（App 探活依赖，字节稳定）
 * - `GET /status` → [LiteWebDaemonConfig] 注入的 status JSON（生产 = [DaemonLiteWebStatus]）
 * - `POST /api/restore` body `{"action":"full"}` → 进程内 root 恢复报告（`pm install -r`）
 * - 其余（`/`、`/a/...`、`/api/...`、`/modweb/...`）→ [handler]，与 App 侧 WebUI 完全同一套路由
 *
 * `handler == null`（纯契约测试/降级）时，未知路由一律 404（不暴露内部规则）。
 */
class LiteWebDaemonCore(
    private val config: LiteWebDaemonConfig,
    private val restore: (String) -> String = { action -> LiteWebDaemonRestore.run(action) },
    private val status: () -> String = { LEGACY_STATUS_JSON },
    private val handler: LiteWebHandler? = null,
    private val uninstallManager: (() -> String)? = null,
    private val restoreResponse: ((String) -> DaemonResponse)? = null,
    private val managerInstalled: (() -> Boolean?)? = null,
    private val normalHidden: DaemonNormalHidden? = null,
    private val independentCodeReady: () -> Boolean = { true },
    private val restoreStatus: (() -> DaemonResponse)? = null,
    private val confirmRestore: ((String) -> DaemonResponse)? = null,
    private val currentPort: () -> Int = { config.port },
) {
    /** 转发给 [handler] 时使用的 64 位小写 hex（配置 token 不满足该形状时用 sha256 派生）。 */
    private val bridgeToken: String by lazy { DaemonBridgeToken.derive(config.token) }

    fun handle(
        method: String,
        rawPath: String,
        headers: Map<String, String>,
        body: String,
        remoteLoopback: Boolean = true,
    ): DaemonResponse {
        if (!remoteLoopback) return json(403, "LOOPBACK_ONLY")
        // Host 头存在就必须合法；缺失由 wire 层（DaemonRequestHandler）强制拒绝。
        val hostHeader = headers.entries.firstOrNull { it.key.equals("Host", ignoreCase = true) }?.value
        if (hostHeader != null && !LiteWebAuth.hostAllowed(hostHeader)) return json(403, "HOST_FORBIDDEN")
        if (!rawPath.startsWith("/") || rawPath.contains("..")) return json(400, "BAD_PATH")
        val path = rawPath.substringBefore('?')
        if (!path.startsWith("/")) return json(400, "BAD_PATH")
        val query = rawPath.substringAfter('?', "")
        val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty() || segments[0] != LiteWebDaemonConfig.PATH_PREFIX) {
            return json(404, "NOT_FOUND")
        }
        val pathToken = segments.getOrNull(1)?.takeIf(::looksLikeToken)
        val routeSegments = if (pathToken != null) segments.drop(2) else segments.drop(1)
        if (!authorized(pathToken, query, headers)) return json(401, "LITEWEB_UNAUTHORIZED")
        val relative = if (routeSegments.isEmpty()) "/" else "/" + routeSegments.joinToString("/")
        return when {
            method == "GET" && relative == "/ping" -> DaemonResponse(200, PING_JSON)
            method == "GET" && relative == "/status" -> DaemonResponse(200, status())
            method == "GET" && relative == "/api/restore/status" ->
                restoreStatus?.invoke() ?: json(501, "RECOVERY_HANDOVER_UNAVAILABLE")
            method == "POST" && relative == "/api/manager/confirm" -> {
                val fields = dev.maplesu.manager.liteweb.LiteWebJson.parseFlatObject(body)
                val nonce = fields?.get("nonce").orEmpty()
                if (fields?.keys != setOf("nonce") || !nonce.matches(Regex("[a-f0-9-]{36}"))) {
                    json(400, "RECOVERY_CONFIRM_BODY_INVALID")
                } else confirmRestore?.invoke(nonce) ?: json(501, "RECOVERY_HANDOVER_UNAVAILABLE")
            }
            method == "GET" && relative == "/api/manager/capabilities" ->
                if (runCatching(independentCodeReady).getOrDefault(false)) DaemonResponse(200, MANAGER_CAPABILITIES_JSON)
                else json(409, "RECOVERY_CLASSPATH_NOT_INDEPENDENT")
            method == "GET" && relative == "/api/normal/ready" ->
                if (normalHidden?.ready() == true) DaemonResponse(200, """{"ok":true,"mode":"KSU"}""")
                else json(409, "NORMAL_INDEPENDENT_RECOVERY_UNVERIFIED")
            method == "POST" && relative == "/api/normal/activate" -> {
                if (body.trim() != "{}") json(400, "NORMAL_ACTIVATE_BODY_INVALID")
                else normalHidden?.let { DaemonResponse(200, it.activate()) }
                    ?: json(501, "NORMAL_HIDDEN_UNAVAILABLE")
            }
            relative == "/api/restore" && method == "POST" -> {
                val action = extractAction(body)
                if (action == null) json(400, "RESTORE_ACTION_MISSING")
                else restoreResponse?.invoke(action) ?: DaemonResponse(200, restore(action))
            }
            relative == "/api/manager/uninstall" && method == "POST" -> {
                if (body.trim() != "{}") json(400, "MANAGER_UNINSTALL_BODY_INVALID")
                else uninstallManager?.let { DaemonResponse(200, it()) }
                    ?: json(501, "MANAGER_UNINSTALL_UNAVAILABLE")
            }
            relative == "/api/manager/uninstall" -> json(404, "NOT_FOUND")
            relative == "/api/hidden" && method == "POST" && restoreResponse != null -> {
                val fields = dev.maplesu.manager.liteweb.LiteWebJson.parseFlatObject(body)
                val value = (fields?.get("enable") ?: fields?.get("hidden"))?.trim()?.lowercase()
                if (value !in setOf("0", "false", "off", "disable")) delegate(method, relative, headers, body)
                else if (config.workMode in setOf("KSU", "LITE")) restoreResponse.invoke("full")
                else when (runCatching { managerInstalled?.invoke() }.getOrNull()) {
                    false -> restoreResponse.invoke("full")
                    true -> delegate(method, relative, headers, body)
                    null -> json(409, "MANAGER_PRESENCE_UNVERIFIED")
                }
            }
            // 既有契约：非 POST 的 /api/restore 是 404（不是 405），保持不回归。
            relative == "/api/restore" -> json(404, "NOT_FOUND")
            else -> delegate(method, relative, headers, body)
        }
    }

    /** 转发给 App 侧同一套路由；token 由本层已经校验过，这里换成 [bridgeToken] 形状。 */
    private fun delegate(method: String, relative: String, originalHeaders: Map<String, String>, body: String): DaemonResponse {
        val delegate = handler ?: return json(404, "NOT_FOUND")
        val target = "/${LiteWebConstants.PATH_PREFIX}/$bridgeToken$relative"
        val headers = buildMap {
            put("Host", originalHeaders.entries.firstOrNull { it.key.equals("Host", true) }?.value
                ?: "${LiteWebConstants.LOOPBACK}:${currentPort()}")
            // Preserve the body type for JSON write validation, not external auth/Host headers.
            for (name in listOf("Content-Type", "Origin", "Referer", "Sec-Fetch-Site")) {
                originalHeaders.entries.firstOrNull { it.key.equals(name, true) }?.value?.let { put(name, it) }
            }
        }
        val request = LiteWebHttpRequest(
            method = method,
            target = target,
            headers = headers,
            body = body.toByteArray(Charsets.UTF_8),
            remoteLoopback = true,
        )
        val response = try {
            delegate.handle(request)
        } catch (error: Throwable) {
            if (error is Error) throw error
            LiteWebHttpResponse.error(500, "INTERNAL_ERROR", "handler failure")
        }
        return DaemonResponse(
            status = response.status,
            body = response.body.toString(Charsets.UTF_8),
            contentType = response.contentType,
            binary = response.body,
            headers = response.headers,
            afterResponse = response.afterResponse,
        )
    }

    /** 路径 token 的形状（长度 ≥16、字母数字）；真正比较仍在 [authorized] 里常量时间做。 */
    private fun looksLikeToken(value: String): Boolean =
        value.length >= 16 && value.all { it.isLetterOrDigit() }

    private fun authorized(pathToken: String?, query: String, headers: Map<String, String>): Boolean {
        val queryToken = query.split('&')
            .mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) null else part.substring(0, index) to part.substring(index + 1)
            }
            .firstOrNull { it.first == "token" }?.second
        val headerToken = headers.entries.firstOrNull { it.key.equals("x-maplesu-token", true) }?.value
        val provided = pathToken ?: queryToken ?: headerToken ?: return false
        return constantTimeEquals(provided, config.token)
    }

    private fun constantTimeEquals(provided: String, expected: String): Boolean =
        // LiteWebToken.matches(expected, provided)：长度不同先短路，同长走 MessageDigest.isEqual。
        LiteWebToken.matches(expected, provided)

    private fun extractAction(body: String): String? {
        val match = Regex("\"action\"\\s*:\\s*\"([A-Za-z0-9_]+)\"").find(body) ?: return null
        return match.groupValues[1]
    }

    private fun json(status: Int, code: String?, extra: String = ""): DaemonResponse {
        val parts = buildList {
            add("\"ok\":${status == 200}")
            if (code != null) add("\"code\":\"$code\"")
            if (extra.isNotEmpty()) add(extra)
        }
        return DaemonResponse(status, "{${parts.joinToString(",")}}")
    }

    companion object {
        /** App 侧探活（`HttpHiddenDaemonProbe`）读服务名；字节形状属于契约，别改。 */
        const val PING_JSON = "{\"ok\":true,\"service\":\"maplesu-liteweb-daemon\"}"

        const val LEGACY_STATUS_JSON = "{\"ok\":true,\"service\":\"maplesu-liteweb-daemon\"}"
        const val MANAGER_CAPABILITIES_JSON = """{"ok":true,"managerUninstall":1,"fullRestore":6}"""
    }
}

/**
 * 转发用的 token 形状适配。
 *
 * `LiteWebAuth` 要求路径 token 恰好 64 位小写 hex（与 ksud 对齐）。`webui.conf` 的 token
 * 由 `LiteWebConfig` 生成时就是 64 hex，因此正常情况下原样透传。若设备上的配置是手工写的
 * 非 64-hex token（历史/调试），这里派生一个稳定的 64-hex 内部值，避免「鉴权通过但页面全 401」；
 * 派生值只在进程内使用（外部鉴权早已用真实 token 常量时间完成），不会出现在日志/URL 里。
 */
internal object DaemonBridgeToken {
    fun derive(configToken: String): String =
        if (LiteWebToken.isValid(configToken)) configToken else sha256Hex(configToken)

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

/**
 * wire 层处理器：真实连接必经此处，因此这里**强制** Host（浏览器/探活客户端都会带）。
 *
 * 职责只有两件事：Host 校验（DNS rebinding 防线）、把 [DaemonResponse] 转成
 * [LiteWebHttpResponse]（保留二进制字节与 content-type）。
 */
class DaemonRequestHandler(private val core: LiteWebDaemonCore) : LiteWebHandler {
    override fun handle(request: LiteWebHttpRequest): LiteWebHttpResponse {
        if (!LiteWebAuth.hostAllowed(request.header("Host"))) {
            return LiteWebHttpResponse.error(403, "HOST_FORBIDDEN", "only 127.0.0.1/localhost")
        }
        val response = core.handle(
            method = request.method,
            rawPath = request.target,
            headers = request.headers,
            body = request.textBody(),
            remoteLoopback = request.remoteLoopback,
        )
        return LiteWebHttpResponse(
            status = response.status,
            contentType = response.contentType,
            body = response.binary ?: response.body.toByteArray(Charsets.UTF_8),
            afterResponse = response.afterResponse,
            headers = response.headers,
        )
    }
}

/** 回装管理器、恢复宿主与授权；确认后保留有界窗口供网页读取结果。 */
object LiteWebDaemonRestore {
    private val json = Json { encodeDefaults = true }
    private val handover = dev.maplesu.manager.recovery.ManagerRestoreHandover()

    private val progress = dev.maplesu.manager.recovery.RecoveryRestoreProgress(handover) {
        runCatching { File(RecoveryPaths.HIDDEN_STATE).readText().trim() == "0" }.getOrDefault(false)
    }
    private const val COMPLETION_GRACE_MILLIS = 120_000L

    fun status(): DaemonResponse {
        val report = progress.status()
        return DaemonResponse(200, json.encodeToString(RecoveryRestoreReport.serializer(), report),
            afterResponse = if (report.completed) ({ DaemonSelfStop.schedule() }) else null)
    }

    @Synchronized
    fun confirm(nonce: String): DaemonResponse {
        if (LiteWebDaemonConfig.load()?.workMode != "LITE") {
            return DaemonResponse(409, """{"ok":false,"code":"RECOVERY_MODE_UNVERIFIED"}""")
        }
        val result = handover.confirm(nonce,
            managerReady = {
                val probe = RuntimeRecoveryCommandRunner.run(
                    "pm path 'dev.maplesu.manager' && " +
                        "[ \"\$(cat '${RecoveryPaths.HIDDEN_STATE}' 2>/dev/null)\" = 0 ]",
                )
                probe.code == 0 && probe.output.lineSequence().any { it.startsWith("package:/data/app/") }
            },
            finalize = {
                val unhide = DaemonParasiteUnhide(DaemonRuntimeShell,
                    DaemonLiteHiddenPort(RecoveryPaths.HIDDEN_STATE), UltraSdkSuAuthRevoke(DaemonRuntimeShell),
                    selfStop = { true }, clearIdentityOnSuccess = false)
                val restored = unhide.deactivate()
                val failed = restored.firstOrNull { !it.ok && it.name != "cleanup_stale_backups" }
                val steps = restored.map { step ->
                    "${step.name}:${if (step.ok) "ok" else "failed(${step.error})"}"
                }
                if (failed != null) RecoveryCleanupResult(false, failed.error, steps)
                else {
                    val archived = unhide.archiveRestoredBackups()
                    RecoveryCleanupResult(archived.ok, archived.error,
                        steps + "${archived.name}:${if (archived.ok) "ok" else "failed(${archived.error})"}")
                }
            },
        )
        // The foreground user has already confirmed. Scheduling here also covers a lost
        // HTTP reply: afterResponse is intentionally skipped on socket write failures.
        if (result.ok) DaemonSelfStop.schedule(COMPLETION_GRACE_MILLIS)
        val report = RecoveryRestoreReport(result.ok, result.code, result.steps,
            awaitingConfirmation = handover.pending(), completed = result.ok)
        progress.confirmed(report)
        System.err.println(report.diagnosticLine(confirmation = true))
        return DaemonResponse(200, json.encodeToString(RecoveryRestoreReport.serializer(),
            report),
            afterResponse = if (result.ok) ({
                DaemonRuntimeShell.run(dev.maplesu.manager.litehidden.RecoveryIdentityScripts.remove())
            }) else null,
        )
    }

    fun run(action: String): String {
        return respond(action).body
    }

    fun respond(action: String): DaemonResponse {
        if (action != "full") return DaemonResponse(200, """{"ok":false,"code":"RESTORE_ACTION_UNSUPPORTED"}""")
        if (!DaemonRecoveryCode.ready()) return DaemonResponse(200, """{"ok":false,"code":"RECOVERY_CLASSPATH_NOT_INDEPENDENT"}""")
        val mode = LiteWebDaemonConfig.load()?.workMode
        if (mode !in setOf("LITE", "KSU")) {
            return DaemonResponse(200, """{"ok":false,"code":"RECOVERY_MODE_UNVERIFIED"}""")
        }
        val report = progress.restore { restore(mode) }
        System.err.println(report.diagnosticLine())
        return DaemonResponse(
            200, json.encodeToString(RecoveryRestoreReport.serializer(), report),
            afterResponse = if (report.ok && !report.awaitingConfirmation && !report.restoring) ({
                DaemonSelfStop.schedule(COMPLETION_GRACE_MILLIS)
            }) else null,
        )
    }

    @Synchronized
    private fun restore(mode: String?): RecoveryRestoreReport {
        val bundle = RecoveryBundle(LocalRecoveryFs(), locationPolicy = { true })
        val unhide = if (mode == "LITE") DaemonParasiteUnhide(
            shell = DaemonRuntimeShell,
            hiddenState = DaemonLiteHiddenPort(RecoveryPaths.HIDDEN_STATE),
            revokeSuAuth = UltraSdkSuAuthRevoke(DaemonRuntimeShell),
            selfStop = { true },
            clearIdentityOnSuccess = false,
        ) else null
        val normal = if (mode == "KSU") DaemonNormalHidden(
            bundle, { mode }, DaemonNormalKernel(), DaemonLiteHiddenPort(RecoveryPaths.HIDDEN_STATE),
            uninstall = { """{"ok":false}""" },
        ) else null
        val service = RecoveryRestoreService(
            bundle, RuntimeRecoveryCommandRunner,
            cleanup = {
                if (normal != null) normal.restoreKernel()
                else {
                    val restored = requireNotNull(unhide).deactivate()
                    val failed = restored.firstOrNull {
                        !it.ok && it.name != dev.maplesu.manager.liteweb.LiteWebUnhideSteps.CLEANUP_STALE_BACKUPS
                    }
                    RecoveryCleanupResult(
                        ok = failed == null && restored.any { it.name == "stop_daemon" && it.ok },
                        code = failed?.error.orEmpty(),
                        steps = restored.map { "${it.name}:${if (it.ok) "ok" else "failed"}" },
                    )
                }
            },
            retainedUid = { DaemonManagerUninstall.retainedUid() },
            finalizeRestoration = {
                if (unhide == null) requireNotNull(normal).removeBootEntry()
                else {
                    val archived = unhide.archiveRestoredBackups()
                    RecoveryCleanupResult(archived.ok, archived.error, listOf("${archived.name}:${if (archived.ok) "ok" else "failed"}"))
                }
            },
            prepareHandover = if (mode == "LITE") handover::prepare else null,
        )
        return service.restoreFull().let { it.copy(completed = it.ok && !it.awaitingConfirmation) }
    }
}

/** daemon 进程内 root 执行：daemon 本身即 root，直接跑 sh。 */
object RuntimeRecoveryCommandRunner : RecoveryCommandRunner {
    override fun run(script: String): RecoveryCommandResult = runCatching {
        val process = ProcessBuilder("/system/bin/timeout", "-k", "5", "60", "/system/bin/sh", "-c", script)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        RecoveryCommandResult(code, output)
    }.getOrElse { RecoveryCommandResult(-1, it.javaClass.simpleName) }
}

/**
 * 阻塞服务：复用 App 侧的 [LiteWebServer]（有界请求头/体、soTimeout、loopback 绑定、
 * token 脱敏日志），端口**严格**用配置端口（恢复链接里写死了它，不能回退到别的端口）。
 *
 * `serveBlocking()` 只在绑定失败时抛异常；成功后阻塞到进程被杀。
 */
class LiteWebDaemonServer(
    private val config: LiteWebDaemonConfig,
    private val core: LiteWebDaemonCore,
    private val host: String = LiteWebConstants.LOOPBACK,
    private val logger: (String) -> Unit = {},
    private val onReady: () -> Unit = {},
) {
    @Volatile private var listener: LiteWebServer? = null

    fun changePort(port: Int, persist: () -> Unit): dev.maplesu.manager.liteweb.LiteWebRestoreStep {
        val result = listener?.rebind(port, persist)
            ?: return dev.maplesu.manager.liteweb.LiteWebRestoreStep("applyPort", false, "LITEWEB_NOT_RUNNING")
        return when (result) {
            is LiteWebStartResult.Started -> dev.maplesu.manager.liteweb.LiteWebRestoreStep("applyPort", true)
            is LiteWebStartResult.Failed -> dev.maplesu.manager.liteweb.LiteWebRestoreStep("applyPort", false, result.code, result.detail)
        }
    }

    fun serveBlocking() {
        val address = InetAddress.getByName(host)
        // 红线：只允许 loopback 绑定。
        if (!address.isLoopbackAddress) {
            throw IllegalStateException("LITEWEB_DAEMON_NON_LOOPBACK_BIND")
        }
        val server = LiteWebServer(
            handler = DaemonRequestHandler(core),
            address = address,
            portCandidates = listOf(config.port),
            logger = logger,
            secretProvider = { config.token },
        )
        when (val started = server.start()) {
            is LiteWebStartResult.Started -> server.use {
                listener = server
                try {
                    onReady()
                    CountDownLatch(1).await()
                } finally {
                    listener = null
                }
            }
            is LiteWebStartResult.Failed -> throw IllegalStateException(
                "${started.code}:${started.detail}",
            )
        }
    }
}

/** `app_process` 入口：无 Android Context；缺配置/绑定失败以非 0 退出码失败（不假装成功）。 */
object LiteWebDaemonMain {
    @JvmStatic
    fun main(args: Array<String>) {
        if (android.os.Process.myUid() != 0) {
            System.err.println("LITEWEB_ROOT_REQUIRED")
            kotlin.system.exitProcess(4)
        }
        if (args.firstOrNull() == "--set-port") {
            val port = args.getOrNull(2)?.toIntOrNull()
            val error = if (args.size != 3 || port == null) "LITEWEB_PORT_RANGE" else {
                LiteWebDaemonConfig.updateStoppedPort(File(RecoveryPaths.DIR), args[1], port, android.os.Process.myPid().toString())
            }
            println("LITEWEB_PORT_UPDATE ${error ?: "OK"}")
            if (error != null) kotlin.system.exitProcess(6)
            return
        }
        if (File(dev.maplesu.manager.liteweb.RECOVERY_WEBUI_PAUSE_PATH).exists()) {
            if (args.contentEquals(arrayOf("--await-ready"))) kotlin.system.exitProcess(5)
            return
        }
        // The start script uses an authenticated HTTP response, never a PID or an open port alone.
        if (args.contentEquals(arrayOf("--await-ready"))) {
            val config = LiteWebDaemonConfig.load() ?: kotlin.system.exitProcess(2)
            val probe = dev.maplesu.manager.litehidden.HttpHiddenDaemonProbe(connectTimeoutMs = 200, readTimeoutMs = 200)
            repeat(20) {
                if (probe.ping(config.port, config.token)) return
                Thread.sleep(200)
            }
            kotlin.system.exitProcess(5)
        }
        val lease = DaemonProcessLease.acquire(File(RecoveryPaths.DIR), android.os.Process.myPid().toString()) ?: return
        lease.use {
            // Load after owning the lease: an offline port change may have committed before this launch.
            val config = LiteWebDaemonConfig.load()
            if (config == null) {
                System.err.println("LITEWEB_DAEMON_CONFIG_MISSING")
                kotlin.system.exitProcess(2)
            }
            serve(config, it)
        }
    }

    private fun serve(initialConfig: LiteWebDaemonConfig, lease: DaemonProcessLease) {
        val currentConfig = java.util.concurrent.atomic.AtomicReference(initialConfig)
        val config = initialConfig
        lateinit var daemonServer: LiteWebDaemonServer
        // Keep serving recovery even if migration fails, but block manager removal.
        val processIndependent = {
            config.workMode != "LITE" || DaemonProcessIsolation.ensureIndependent()
        }
        processIndependent()
        // 与 `DaemonLiteWebBackend` 的默认根一致（真机 `/data/adb`，测试可整体换目录）。
        val adbRoot = "/data/adb"
        val hiddenState = DaemonLiteHiddenPort("$adbRoot/maplesu/lite_hidden")
        val sdkAccess = DaemonUltraSdkAccess(shell = DaemonRuntimeShell)
        val recoveryReadiness = DaemonManagerRecoveryReadiness(sdkAccess = sdkAccess::open)
        val normalKernel = DaemonNormalKernel()
        val recoveryBundle = RecoveryBundle(LocalRecoveryFs(), locationPolicy = { true })
        lateinit var normalHidden: DaemonNormalHidden
        val managerRemoval = DaemonManagerUninstall(
            bundle = recoveryBundle,
            runner = RuntimeRecoveryCommandRunner,
            logger = { System.err.println(it) },
            hidden = {
                if (config.workMode == "KSU") hiddenState.currentHidden()?.let { it && normalKernel.state() == true }
                else hiddenState.currentHidden()
            },
            recoveryReady = {
                DaemonRecoveryCode.ready() && LiteWebDaemonConfig.load() == currentConfig.get() && when (config.workMode) {
                    "LITE" -> processIndependent() && recoveryReadiness.ready()
                    "KSU" -> normalHidden.ready()
                    else -> false
                }
            },
        )
        val managerInstalled: () -> Boolean? = {
            val packages = RuntimeRecoveryCommandRunner.run("pm list packages --user 0 'dev.maplesu.manager'")
            if (packages.code != 0) null
            else packages.output.lineSequence().any { it.trim() == "package:dev.maplesu.manager" }
        }
        normalHidden = DaemonNormalHidden(
            recoveryBundle, { config.workMode }, normalKernel, hiddenState, managerRemoval::uninstall,
            managerInstalled = managerInstalled,
        )
        val backend = DaemonLiteWebBackend(
            configuredPortValue = config.port,
            portValue = { currentConfig.get().port },
            changePort = { requested ->
                daemonServer.changePort(requested) {
                    currentConfig.set(LiteWebDaemonConfig.updatePort(RecoveryPaths.WEBUI_CONFIG, currentConfig.get(), requested))
                }
            },
            hiddenPortValue = hiddenState,
            runtimeSource = if (config.workMode == "KSU") DaemonNormalRuntimeFacts(normalKernel) else DaemonUltraRuntimeFacts(sdkAccess),
            superuserPortValue = daemonModeSuperuserPort(config.workMode,
                ultraPort = { DaemonSuperuserPort(sdkAccess, DaemonRuntimeShell) }),
            managerInstalled = managerInstalled,
            // 完整解除寄生：宿主库还原 → loader 删除 → 撤销宿主 su 授权 → 停 daemon（杀自己）。
            // 停 daemon 只**排程**延迟退出（先回响应再退），见 DaemonSelfStop。
            unhidePortValue = if (config.workMode == "LITE") DaemonParasiteUnhide(
                shell = DaemonRuntimeShell,
                hiddenState = hiddenState,
                revokeSuAuth = UltraSdkSuAuthRevoke(shell = DaemonRuntimeShell),
                selfStop = { DaemonSelfStop.schedule() },
                logger = { line -> System.err.println(line) },
            ) else dev.maplesu.manager.liteweb.UnavailableUnhidePort,
        )
        backend.attachModuleBridge(com.maplesu.core.ui.webui.ExternalWebUiBridge(
            files = dev.maplesu.manager.liteweb.LiteWebModuleFiles(),
            cacheRoot = File(RecoveryPaths.DIR, "module-webui-cache"),
            apps = { com.maplesu.core.ui.webui.rootWebUiApps() },
            sourceCurrent = {
                LiteWebDaemonConfig.load() == currentConfig.get() &&
                    !File(dev.maplesu.manager.liteweb.RECOVERY_WEBUI_PAUSE_PATH).exists()
            },
        ))
        // 转发目标里的 token 形状必须与 core 一致（见 DaemonBridgeToken）。
        val handlerToken = DaemonBridgeToken.derive(config.token)
        val requestHandler = LiteWebRequestHandler(
            tokenProvider = { handlerToken },
            backend = backend,
            documentTokenProvider = { currentConfig.get().token },
        )
        val core = LiteWebDaemonCore(
            config = config,
            restore = { action -> LiteWebDaemonRestore.run(action) },
            status = { DaemonLiteWebStatus.render(backend) },
            handler = requestHandler,
            uninstallManager = managerRemoval::uninstall,
            restoreResponse = { action ->
                if (LiteWebDaemonConfig.load() == currentConfig.get()) LiteWebDaemonRestore.respond(action)
                else DaemonResponse(409, """{"ok":false,"code":"RECOVERY_CONFIG_CHANGED"}""")
            },
            managerInstalled = managerInstalled,
            normalHidden = if (config.workMode == "KSU") normalHidden else null,
            independentCodeReady = {
                DaemonRecoveryCode.ready() && config.workMode in setOf("LITE", "KSU") &&
                    LiteWebDaemonConfig.load() == currentConfig.get() && processIndependent()
            },
            restoreStatus = LiteWebDaemonRestore::status,
            confirmRestore = { nonce ->
                if (LiteWebDaemonConfig.load() == currentConfig.get()) LiteWebDaemonRestore.confirm(nonce)
                else DaemonResponse(409, """{"ok":false,"code":"RECOVERY_CONFIG_CHANGED"}""")
            },
            currentPort = { currentConfig.get().port },
        )
        try {
            // 日志进 liteweb.log（root 脚本已重定向 stderr）；token 由 LiteWebServer 脱敏。
            daemonServer = LiteWebDaemonServer(config, core, logger = { line -> System.err.println(line) }, onReady = lease::publish)
            daemonServer.serveBlocking()
        } catch (error: Throwable) {
            System.err.println("LITEWEB_DAEMON_FAILED:${error.javaClass.simpleName}")
            kotlin.system.exitProcess(3)
        } finally {
            backend.close()
        }
    }
}
