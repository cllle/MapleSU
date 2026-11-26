package dev.maplesu.manager.liteweb

import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * H2：Lite 模式网页管理器的纯 Kotlin HTTP 核心（JVM 可测，无 Android 依赖）。
 *
 * 安全红线：
 * - 只绑 `127.0.0.1`；Host 头只接受 127.0.0.1/localhost（可带端口）；
 * - 路径必须为 `/x7k9f/<64hex token>/...`，token 常量时间比较；
 * - token 不写日志（[LiteWebRedaction]），配置 0600 由 root 进程写；
 * - 仅 GET/POST；请求头/请求体上限；静态路径防穿越；fail-closed。
 */
object LiteWebConstants {
    const val LOOPBACK = "127.0.0.1"
    val PORT_CANDIDATES: List<Int> = listOf(18427, 18437, 18447, 18457)
    const val PATH_PREFIX = "x7k9f"
    const val TOKEN_HEX_LENGTH = 64
    /** 旧版 token 配置（迁移只读兼容）。 */
    const val CONFIG_PATH = "/data/adb/maplesu/webadmin.conf"

    /**
     * 当前配置：token + 自定义端口 + 启用标记（root 0600 原子写）。
     *
     * **必须与 daemon 读取的是同一个文件**：daemon 用
     * `RecoveryPaths.WEBUI_CONFIG`（`/data/adb/maplesu/recovery/webui.conf`）。
     * 曾经这里写成 `/data/adb/maplesu/webui.conf`，于是 App 预览/复制的链接来自
     * 另一个（预览自生成的）token，daemon 不认 → 打开链接只看到 401 JSON 字符串
     * （2026-10-31 真机复现）。
     */
    const val WEBUI_CONFIG_PATH = "/data/adb/maplesu/recovery/webui.conf"
    const val MAX_BODY_BYTES = 1 * 1024 * 1024
    const val MAX_HEADER_BYTES = 16 * 1024
    const val MAX_HEADER_COUNT = 64
    const val SOCKET_TIMEOUT_MS = 10_000
    const val SOCKET_BACKLOG = 16
    const val SERVER_VERSION = 1
    val ALLOWED_HOSTS: Set<String> = setOf("127.0.0.1", "localhost")
    val ALLOWED_METHODS: Set<String> = setOf("GET", "POST")
}

/** 随机 64-hex token（root 进程生成，App 只在内存持有）。 */
object LiteWebToken {
    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(LiteWebConstants.TOKEN_HEX_LENGTH / 2)
        random.nextBytes(bytes)
        return hex(bytes)
    }

    fun isValid(token: String?): Boolean =
        token != null && token.length == LiteWebConstants.TOKEN_HEX_LENGTH &&
            token.all { it in '0'..'9' || it in 'a'..'f' }

    /** 常量时间比较（长度/内容都不提前短路到可观测分支）。 */
    fun matches(expected: String?, provided: String?): Boolean {
        if (expected == null || provided == null) return false
        val left = expected.toByteArray(StandardCharsets.US_ASCII)
        val right = provided.toByteArray(StandardCharsets.US_ASCII)
        if (left.size != right.size) return false
        return MessageDigest.isEqual(left, right)
    }

    internal fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

/** `webadmin.conf`（root 进程 0600 原子写）：token + 绑定端口 + 启用标记。 */
data class LiteWebConfig(
    val token: String,
    val port: Int = LiteWebConstants.PORT_CANDIDATES.first(),
    val enabled: Boolean = false,
    val startedAt: Long = 0L,
) {
    init {
        require(LiteWebToken.isValid(token)) { "LITEWEB_TOKEN_INVALID" }
        require(port > 0 && port <= 65535) { "LITEWEB_PORT_INVALID" }
    }

    fun render(): String = buildString {
        append("schema=1\n")
        append("token=").append(token).append('\n')
        append("port=").append(port).append('\n')
        append("enabled=").append(if (enabled) "true" else "false").append('\n')
        append("startedAt=").append(startedAt).append('\n')
    }

    companion object {
        fun parse(text: String): LiteWebConfig? {
            val values = LinkedHashMap<String, String>()
            for (line in text.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                val eq = trimmed.indexOf('=')
                if (eq <= 0) return null
                values[trimmed.substring(0, eq)] = trimmed.substring(eq + 1)
            }
            // 兼容两种真实文件：daemon/激活写 `webuiPort=` 且可能没有 schema 行；
            // 预览/管理写 `schema=1` + `port=`。缺 schema 不视为损坏（否则会误判 token 失效）。
            val schema = values["schema"]
            if (schema != null && schema != "1") return null
            val token = values["token"] ?: return null
            if (!LiteWebToken.isValid(token)) return null
            val port = (values["port"] ?: values["webuiPort"])?.toIntOrNull()
                ?: LiteWebConstants.PORT_CANDIDATES.first()
            if (port !in 1..65535) return null
            return LiteWebConfig(
                token = token,
                port = port,
                enabled = values["enabled"] == "true",
                startedAt = values["startedAt"]?.toLongOrNull() ?: 0L,
            )
        }
    }
}

// ------------------------------------------------------------------ request / response

data class LiteWebHttpRequest(
    val method: String,
    val target: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
    /** 连接来自 loopback（由 socket 层判定；纯 handler 测试可直接传 true/false）。 */
    val remoteLoopback: Boolean = true,
) {
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    fun textBody(): String = body.toString(StandardCharsets.UTF_8)
}

data class LiteWebHttpResponse(
    val status: Int,
    val contentType: String,
    val body: ByteArray,
    val headers: Map<String, String> = emptyMap(),
    /** Called only after the complete response has been written and flushed. */
    val afterResponse: (() -> Unit)? = null,
) {
    fun text(): String = body.toString(StandardCharsets.UTF_8)

    companion object {
        const val APPLICATION_JSON = "application/json; charset=utf-8"
        const val TEXT_PLAIN = "text/plain; charset=utf-8"

        fun json(status: Int, text: String): LiteWebHttpResponse =
            LiteWebHttpResponse(status, APPLICATION_JSON, text.toByteArray(StandardCharsets.UTF_8))

        fun text(status: Int, text: String): LiteWebHttpResponse =
            LiteWebHttpResponse(status, TEXT_PLAIN, text.toByteArray(StandardCharsets.UTF_8))

        fun empty(status: Int): LiteWebHttpResponse =
            LiteWebHttpResponse(status, TEXT_PLAIN, ByteArray(0))

        fun error(status: Int, code: String, reason: String): LiteWebHttpResponse =
            json(status, "{\"ok\":false,\"error\":${LiteWebJson.quote(code)},\"reason\":${LiteWebJson.quote(reason)}}")

        fun ok(): LiteWebHttpResponse = json(200, "{\"ok\":true}")
    }
}

// ------------------------------------------------------------------ auth

sealed interface LiteWebAuthorization {
    /** [route] 为 token 之后的部分：""、"/" 或 "/api/..."。 */
    data class Allowed(val route: String) : LiteWebAuthorization

    data class Rejected(val response: LiteWebHttpResponse) : LiteWebAuthorization
}

object LiteWebAuth {
    /**
     * 顺序固定（先本地性/方法，再 Host，最后 token）：未知前缀 404 隐藏存在性，
     * token 错误 401；任何异常路径都拒绝。
     */
    fun authorize(
        request: LiteWebHttpRequest,
        expectedToken: String,
        prefix: String = LiteWebConstants.PATH_PREFIX,
    ): LiteWebAuthorization {
        if (!request.remoteLoopback) {
            return LiteWebAuthorization.Rejected(LiteWebHttpResponse.empty(403))
        }
        if (request.method !in LiteWebConstants.ALLOWED_METHODS) {
            return LiteWebAuthorization.Rejected(
                LiteWebHttpResponse.error(405, "METHOD_NOT_ALLOWED", "only GET/POST"),
            )
        }
        if (!hostAllowed(request.header("Host"))) {
            return LiteWebAuthorization.Rejected(
                LiteWebHttpResponse.error(403, "HOST_FORBIDDEN", "only 127.0.0.1/localhost"),
            )
        }
        val rawPath = request.target.substringBefore('?').substringBefore('#')
        val decoded = LiteWebPaths.decode(rawPath)
            ?: return LiteWebAuthorization.Rejected(LiteWebHttpResponse.empty(400))
        val base = "/$prefix/"
        if (!decoded.startsWith(base)) {
            return LiteWebAuthorization.Rejected(LiteWebHttpResponse.empty(404))
        }
        val remainder = decoded.substring(base.length)
        val slash = remainder.indexOf('/')
        val provided = if (slash < 0) remainder else remainder.substring(0, slash)
        val route = if (slash < 0) "" else remainder.substring(slash)
        // 与 ksud `authed_rel` 对齐的强校验：token 段必须**恰好**是 64 位小写十六进制，
        // 再走常量时间比较。长度/字符集不对与「值不对」返回同一个 401 ——
        // 不让攻击者从响应里区分「结构猜对了一半」还是「值猜错」。
        if (!LiteWebToken.isValid(provided) || !LiteWebToken.matches(expectedToken, provided)) {
            return LiteWebAuthorization.Rejected(
                LiteWebHttpResponse.error(401, "TOKEN_INVALID", "missing or invalid token"),
            )
        }
        return LiteWebAuthorization.Allowed(route)
    }

    /** Host 只允许 `127.0.0.1[:port]` / `localhost[:port]`（大小写不敏感、禁止 userinfo）。 */
    fun hostAllowed(hostHeader: String?): Boolean {
        if (hostHeader.isNullOrBlank()) return false
        if (hostHeader.contains('@')) return false
        val host = hostHeader.substringBefore(':').trim().lowercase()
        if (host !in LiteWebConstants.ALLOWED_HOSTS) return false
        val portPart = hostHeader.substringAfter(':', "")
        return portPart.isEmpty() || portPart.toIntOrNull() in 1..65535
    }
}

// ------------------------------------------------------------------ paths

object LiteWebPaths {
    /** percent-decode + 拒绝 NUL/控制字符；解码失败 → null。 */
    fun decode(raw: String): String? {
        if (raw.isEmpty()) return null
        if (raw.any { it == '\u0000' || it.code < 0x20 }) return null
        val decoded = try {
            URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (decoded.any { it == '\u0000' || it.code < 0x20 }) return null
        return decoded
    }

    /**
     * 静态相对路径安全化：拒绝 `..`/`.` 段、反斜杠、绝对路径、空段重复；返回规范化相对路径
     * （无前导 `/`）。根路径/空路径 → `index.html`。
     */
    fun normalizeStatic(route: String): String? {
        if (route.contains('\\') || route.contains('\u0000')) return null
        val trimmed = route.trimStart('/')
        if (trimmed.startsWith("/")) return null
        val segments = trimmed.split('/').filter { it.isNotEmpty() }
        if (segments.any { it == ".." || it == "." }) return null
        if (segments.isEmpty()) return "index.html"
        val joined = segments.joinToString("/")
        return if (joined.endsWith("/")) "$joined/index.html" else joined
    }
}

// ------------------------------------------------------------------ redaction

object LiteWebRedaction {
    /** 日志行/URL 中 token 一律替换；同时丢弃 query。 */
    fun redact(message: String, token: String?): String {
        var result = message
        if (token != null && token.isNotEmpty()) result = result.replace(token, "<token>")
        val query = result.indexOf('?')
        if (query >= 0) result = result.substring(0, query) + "?<redacted>"
        return result
    }

    fun redactTarget(target: String, token: String?, prefix: String = LiteWebConstants.PATH_PREFIX): String {
        val hasQuery = target.contains('?')
        val path = target.substringBefore('?')
        val redacted = redact(path, token)
        return if (hasQuery) "$redacted?<redacted>" else redacted
    }
}

// ------------------------------------------------------------------ JSON (subset, no org.json)

object LiteWebJson {
    fun quote(value: String): String {
        val out = StringBuilder(value.length + 2)
        out.append('"')
        for (character in value) {
            when (character) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (character.code < 0x20) {
                    out.append("\\u").append("%04x".format(character.code))
                } else {
                    out.append(character)
                }
            }
        }
        return out.append('"').toString()
    }

    /** 仅支持扁平对象、字符串/布尔/数字值；未知/损坏 → null（toggle 用）。 */
    fun parseFlatObject(text: String): Map<String, String>? {
        var index = 0
        fun skip() {
            while (index < text.length && text[index].isWhitespace()) index++
        }
        fun string(): String? {
            skip()
            if (index >= text.length || text[index] != '"') return null
            index++
            val out = StringBuilder()
            while (index < text.length) {
                val character = text[index++]
                if (character == '"') return out.toString()
                if (character != '\\') {
                    out.append(character)
                    continue
                }
                if (index >= text.length) return null
                when (val escape = text[index++]) {
                    '"' -> out.append('"')
                    '\\' -> out.append('\\')
                    '/' -> out.append('/')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'u' -> {
                        if (index + 4 > text.length) return null
                        out.append(text.substring(index, index + 4).toIntOrNull(16)?.toChar() ?: return null)
                        index += 4
                    }
                    else -> return null
                }
            }
            return null
        }
        val values = LinkedHashMap<String, String>()
        skip()
        if (index >= text.length || text[index] != '{') return null
        index++
        while (true) {
            skip()
            if (index < text.length && text[index] == '}') {
                index++
                break
            }
            val key = string() ?: return null
            skip()
            if (index >= text.length || text[index] != ':') return null
            index++
            skip()
            if (index >= text.length) return null
            val value = when {
                text[index] == '"' -> string() ?: return null
                else -> {
                    val start = index
                    while (index < text.length && text[index] !in ",}") index++
                    val raw = text.substring(start, index).trim()
                    if (raw.isEmpty()) return null
                    if (raw != "true" && raw != "false" && raw.toLongOrNull() == null) return null
                    raw
                }
            }
            values[key] = value
            skip()
            if (index < text.length && text[index] == ',') {
                index++
                continue
            }
            if (index < text.length && text[index] == '}') {
                index++
                break
            }
            return null
        }
        skip()
        if (index != text.length) return null
        return values
    }
}

// ------------------------------------------------------------------ backend ports

data class LiteWebStatusView(
    val mode: String = "LITE",
    val rootAvailable: Boolean,
    val moduleCount: Int,
    val hidden: Boolean = false,
    val uapi: String = "lite",
    val serverVersion: Int = LiteWebConstants.SERVER_VERSION,
    /** 非空 = 模块 JSON 解析失败（诚实标记，moduleCount 归 0）。 */
    val moduleCountError: String = "",
    /**
     * 向后兼容新增：隐藏态是否**已知**。
     *
     * 默认值 = [hidden]（老后端零改动、行为与旧版一致）；后端**明确**传 `null` 表示
     * 「读不到、不冒充」，此时 `/api/status` 输出 `"hidden":null`（前端显示「—」）。
     */
    val hiddenKnown: Boolean? = hidden,
)

/** Lite 隐藏开关端口（r5-ui 的 LiteHiddenController 适配；未就绪时 [UnavailableLiteHiddenPort]）。 */
sealed interface LiteHiddenResult {
    data class Ok(val hidden: Boolean, val message: String = "") : LiteHiddenResult

    data class Failed(val code: String, val reason: String) : LiteHiddenResult

    /** 控制器未接入/当前进程不可用 → 501，不伪装成功。 */
    data class Unavailable(val reason: String) : LiteHiddenResult
}

interface LiteHiddenPort {
    /** null = 当前读不到（status 保持只读展示）。 */
    fun currentHidden(): Boolean?

    fun setHidden(hidden: Boolean, level: String? = null): LiteHiddenResult
}

object UnavailableLiteHiddenPort : LiteHiddenPort {
    override fun currentHidden(): Boolean? = null

    override fun setHidden(hidden: Boolean, level: String?): LiteHiddenResult =
        LiteHiddenResult.Unavailable("Ultra hidden controller is not available in this build/process")
}

/** `/api/modules` 顶层是 JSON 数组（LiteScriptModuleBackend.listJson）；兼容旧对象 `{modules:[...]}`。 */
data class LiteWebModuleCount(val count: Int, val error: String = "")

object LiteWebModuleList {
    fun count(json: String): LiteWebModuleCount {
        val text = json.trim()
        if (text.isEmpty()) return LiteWebModuleCount(0, "EMPTY_RESPONSE")
        val arrayText = when {
            text.startsWith("[") -> text
            text.startsWith("{") -> objectArrayField(text, "modules")
                ?: return LiteWebModuleCount(0, "MODULES_FIELD_MISSING")
            else -> return LiteWebModuleCount(0, "INVALID_JSON")
        }
        val length = arrayLength(arrayText) ?: return LiteWebModuleCount(0, "INVALID_JSON")
        return LiteWebModuleCount(length)
    }

    private fun objectArrayField(text: String, field: String): String? {
        var depth = 0
        var index = 0
        var inString = false
        var escaped = false
        while (index < text.length) {
            val character = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
            } else {
                when (character) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> depth--
                    ':' -> if (depth == 1) {
                        val rest = text.substring(index + 1).trimStart()
                        if (rest.startsWith("[")) return rest
                    }
                }
            }
            index++
        }
        return null
    }

    /** 最小 JSON 数组元素计数（字符串/嵌套安全）；不合法 → null。 */
    private fun arrayLength(text: String): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        var elements = 0
        var elementOpen = false
        for (index in text.indices) {
            val character = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
                continue
            }
            when (character) {
                '"' -> {
                    inString = true
                    elementOpen = true
                }
                '[' -> {
                    depth++
                    if (depth > 1) elementOpen = true
                }
                '{' -> {
                    depth++
                    elementOpen = true
                }
                ']' -> {
                    depth--
                    if (depth == 0) {
                        if (elementOpen) elements++
                        return elements
                    }
                    if (depth < 0) return null
                }
                '}' -> {
                    depth--
                    if (depth < 0) return null
                }
                ',' -> if (depth == 1 && elementOpen) {
                    elements++
                    elementOpen = false
                }
                in " \t\r\n" -> Unit
                else -> if (depth == 1) elementOpen = true
            }
        }
        return null
    }
}

data class LiteWebSulogView(
    val available: Boolean,
    val lines: List<String> = emptyList(),
    val reason: String = "",
)

sealed interface LiteWebToggleView {
    data class Ok(val moduleId: String, val enabled: Boolean, val needsReboot: Boolean = false) : LiteWebToggleView

    data class Failed(val code: String, val reason: String) : LiteWebToggleView

    /** 写后端不可用（Lite 受限环境）→ 501 + 原因，不伪造成功。 */
    data class Unavailable(val reason: String) : LiteWebToggleView
}

interface LiteWebAssets {
    /** [relativePath] 已被 [LiteWebPaths.normalizeStatic] 规范化；不存在返回 null。 */
    fun read(relativePath: String): ByteArray?
}

/** App/root 侧只读事实端口（生产实现见 LiteWebAdminService；测试用 fake）。 */
interface LiteWebBackend {
    fun status(): LiteWebStatusView

    /** 只读模块列表 JSON（生产 = LiteScriptModuleBackend.listJson 的只读投影）。 */
    fun modulesJson(): String

    /** 只读授权列表 JSON（生产 = DirectPolicy/ManagerAuthorizations 快照）。 */
    fun superusersJson(): String

    /** Ultra SDK package-global grant management; unsupported backends must fail explicitly. */
    fun superuserPort(): LiteWebSuperuserPort = UnavailableSuperuserPort

    fun sulog(): LiteWebSulogView

    fun toggleModule(moduleId: String, enabled: Boolean): LiteWebToggleView

    fun assets(): LiteWebAssets

    /**
     * 模块自带 WebUI 的单文件读取（`GET .../modweb/<id>/<rel>`）。
     *
     * 默认 `Unavailable`：没有实现就明确回 501 + 原因，绝不假装「文件不存在」。
     * 生产实现见 `AndroidLiteWebBackend.moduleWebFile`（root 进程内读模块 webroot）。
     */
    fun moduleBridgePort(): LiteWebModuleBridgePort = UnavailableModuleBridgePort

    fun moduleWebFile(moduleId: String, relativePath: String): LiteWebModuleWeb =
        LiteWebModuleWeb.Unavailable("MODULE_WEB_BACKEND_UNAVAILABLE")

    /** Lite 隐藏开关端口；未接入返回 [UnavailableLiteHiddenPort]（写接口 → 501，绝不假成功）。 */
    fun hiddenPort(): LiteHiddenPort = UnavailableLiteHiddenPort

    /** 一键隐藏事务端口（wm-core FullHideTransaction/daemon/APK 备份适配）；未接线显式失败。 */
    fun activatePort(): LiteWebActivatePort = UnavailableActivatePort

    /** 一键恢复事务端口（wm-core RestoreTransaction：sha→pm install→回读→OFF→组件）；未接线显式失败。 */
    fun restorePort(): LiteWebRestorePort = UnavailableRestorePort

    /**
     * 解除寄生端口（`POST /api/hidden {enable:false}`）：宿主库还原 → loader 删除 →
     * 撤销宿主授权 → 停 daemon。daemon 生产实现见 `DaemonLiteWebBackend`；
     * App 侧未接线时保持 501（[UnavailableUnhidePort]），绝不假装已解除。
     */
    fun unhidePort(): LiteWebUnhidePort = UnavailableUnhidePort

    /** 隐藏详情（GET /api/hidden）；默认由 activatePort 推导，生产可覆盖。 */
    fun hiddenStatus(): LiteWebHiddenView {
        val port = runCatching { activatePort() }.getOrNull()
        val recovery = runCatching { port?.recovery() }.getOrNull() ?: LiteWebRecoveryStatus()
        return LiteWebHiddenView(
            hidden = runCatching { hiddenPort().currentHidden() }.getOrNull(),
            port = runCatching { port?.currentPort() }.getOrNull() ?: LiteWebPortPolicy.DEFAULT_PORT,
            recovery = recovery,
            steps = runCatching { port?.recordedSteps() }.getOrNull().orEmpty(),
        )
    }

    fun configuredPort(): Int = runCatching { activatePort().currentPort() }
        .getOrDefault(LiteWebPortPolicy.DEFAULT_PORT)

    /** Port repair works independently of a new hide transaction or an installed Manager. */
    fun applyConfiguredPort(port: Int): LiteWebRestoreStep = activatePort().applyPort(port)

    fun settingsPort(): LiteWebSettingsPort = UnavailableSettingsPort

    fun diagnosticsPort(): LiteWebDiagnosticsPort = UnavailableDiagnosticsPort

    fun workModePort(): LiteWebWorkModePort = UnavailableWorkModePort

    fun moduleActionPort(): LiteWebModuleActionPort = UnavailableModuleActionPort

    /** 当前 token 是否存在（URL 可用性；不返回 token 本身）。 */
    fun tokenPresent(): Boolean = true

    /** 轮换 token（root 进程写 webui.conf + 重启监听）；未接线显式失败。 */
    fun resetToken(): LiteWebRestoreStep =
        LiteWebRestoreStep("resetToken", false, "TOKEN_RESET_BACKEND_UNAVAILABLE")

    /** 模式无关隐藏/恢复后端（A=Ultra 模块 / B=KSU 模块 boot 钩子）；未接线显式失败。 */
    fun modeBackend(): LiteWebHiddenModeBackend = UnavailableHiddenModeBackend
}

// ------------------------------------------------------------------ HTTP handler / server

/**
 * [LiteWebServer] 的请求处理器端口（向后兼容新增）。
 *
 * 生产实现仍是 [LiteWebRequestHandler]（App root 服务）；恢复 daemon 需要在同一 HTTP
 * 层上先做自己的 `/x7k9f/<token>/ping|status|api/restore` 契约、再转发给
 * [LiteWebRequestHandler]，所以 socket 层只依赖这个最小接口。
 */
fun interface LiteWebHandler {
    fun handle(request: LiteWebHttpRequest): LiteWebHttpResponse
}

// ------------------------------------------------------------------ HTTP/1.1 parser

sealed interface LiteWebParseResult {
    data class Parsed(val request: LiteWebHttpRequest) : LiteWebParseResult

    /** 已定响应（400/413/431），不进入鉴权。 */
    data class Rejected(val response: LiteWebHttpResponse) : LiteWebParseResult
}

object LiteWebHttpParser {
    fun parse(
        input: InputStream,
        remoteLoopback: Boolean = true,
        maxBodyBytes: Int = LiteWebConstants.MAX_BODY_BYTES,
        maxHeaderBytes: Int = LiteWebConstants.MAX_HEADER_BYTES,
        maxHeaderCount: Int = LiteWebConstants.MAX_HEADER_COUNT,
    ): LiteWebParseResult {
        val head = ArrayList<String>()
        var headerBytes = 0
        try {
            while (true) {
                val line = try {
                    readLine(input, limit = maxHeaderBytes - headerBytes)
                } catch (_: HeaderTooLarge) {
                    return LiteWebParseResult.Rejected(
                        LiteWebHttpResponse.error(431, "HEADER_TOO_LARGE", "header line limit"),
                    )
                } ?: return LiteWebParseResult.Rejected(
                    LiteWebHttpResponse.error(400, "REQUEST_INVALID", "truncated request head"),
                )
                headerBytes += line.length + 2
                if (headerBytes > maxHeaderBytes) {
                    return LiteWebParseResult.Rejected(
                        LiteWebHttpResponse.error(431, "HEADER_TOO_LARGE", "header limit"),
                    )
                }
                if (line.isEmpty()) break
                head.add(line)
                if (head.size > maxHeaderCount + 1) {
                    return LiteWebParseResult.Rejected(
                        LiteWebHttpResponse.error(431, "HEADER_TOO_LARGE", "header count"),
                    )
                }
            }
        } catch (_: IOException) {
            return LiteWebParseResult.Rejected(
                LiteWebHttpResponse.error(400, "REQUEST_INVALID", "unreadable request"),
            )
        }
        if (head.isEmpty()) {
            return LiteWebParseResult.Rejected(
                LiteWebHttpResponse.error(400, "REQUEST_INVALID", "empty request"),
            )
        }
        val requestLine = head.first().split(' ')
        if (requestLine.size != 3 || !requestLine[2].startsWith("HTTP/1.")) {
            return LiteWebParseResult.Rejected(
                LiteWebHttpResponse.error(400, "REQUEST_INVALID", "request line"),
            )
        }
        val method = requestLine[0]
        val target = requestLine[1]
        if (target.length > 4096 || !target.startsWith("/")) {
            return LiteWebParseResult.Rejected(
                LiteWebHttpResponse.error(400, "REQUEST_INVALID", "target"),
            )
        }
        val headers = LinkedHashMap<String, String>()
        for (line in head.drop(1)) {
            val colon = line.indexOf(':')
            if (colon <= 0) {
                return LiteWebParseResult.Rejected(
                    LiteWebHttpResponse.error(400, "REQUEST_INVALID", "header line"),
                )
            }
            val name = line.substring(0, colon).trim()
            val value = line.substring(colon + 1).trim()
            headers[name] = value
        }
        if (headers.entries.any { it.key.equals("Transfer-Encoding", ignoreCase = true) }) {
            return LiteWebParseResult.Rejected(
                LiteWebHttpResponse.error(400, "TRANSFER_ENCODING_UNSUPPORTED", "Content-Length only"),
            )
        }
        val lengthText = headers.entries.firstOrNull { it.key.equals("Content-Length", ignoreCase = true) }?.value
        val length = lengthText?.toLongOrNull() ?: 0L
        if (length < 0) {
            return LiteWebParseResult.Rejected(
                LiteWebHttpResponse.error(400, "REQUEST_INVALID", "content length"),
            )
        }
        if (length > maxBodyBytes) {
            return LiteWebParseResult.Rejected(
                LiteWebHttpResponse.error(413, "BODY_TOO_LARGE", "limit=$maxBodyBytes"),
            )
        }
        val body = ByteArray(length.toInt())
        var read = 0
        try {
            while (read < body.size) {
                val count = input.read(body, read, body.size - read)
                if (count < 0) {
                    return LiteWebParseResult.Rejected(
                        LiteWebHttpResponse.error(400, "REQUEST_INVALID", "truncated body"),
                    )
                }
                read += count
            }
        } catch (_: IOException) {
            return LiteWebParseResult.Rejected(
                LiteWebHttpResponse.error(400, "REQUEST_INVALID", "unreadable body"),
            )
        }
        return LiteWebParseResult.Parsed(
            LiteWebHttpRequest(method, target, headers, body, remoteLoopback),
        )
    }

    private class HeaderTooLarge : IOException("HEADER_TOO_LARGE")

    private fun readLine(input: InputStream, limit: Int): String? {
        val out = StringBuilder()
        while (true) {
            val single = input.read()
            if (single < 0) return if (out.isEmpty()) null else out.toString()
            if (single == '\n'.code) return out.toString().removeSuffix("\r")
            out.append(single.toChar())
            if (out.length > limit) throw HeaderTooLarge()
        }
    }
}

/** 响应序列化（服务器写 socket 用；测试直接断言 status/body）。 */
object LiteWebResponses {
    fun render(response: LiteWebHttpResponse, includeBody: Boolean = true): ByteArray {
        val reason = when (response.status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            413 -> "Payload Too Large"
            431 -> "Request Header Fields Too Large"
            501 -> "Not Implemented"
            503 -> "Service Unavailable"
            else -> "Error"
        }
        val headers = buildString {
            append("HTTP/1.1 ").append(response.status).append(' ').append(reason).append("\r\n")
            append("Content-Type: ").append(response.contentType).append("\r\n")
            append("Content-Length: ").append(response.body.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Connection: close\r\n")
            for ((name, value) in response.headers) append(name).append(": ").append(value).append("\r\n")
            append("\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)
        if (!includeBody) return headers
        return headers + response.body
    }
}
