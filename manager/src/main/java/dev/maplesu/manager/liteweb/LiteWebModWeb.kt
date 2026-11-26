package dev.maplesu.manager.liteweb

/**
 * 模块自带 WebUI 的代理（`{prefix}/{token}/modweb/<模块id>/<相对路径>`）。
 *
 * 为什么要它：KSU 的网页管理器由 ksud 提供 `/modweb/<id>/`（读模块 `webroot`），
 * 而 A 模式**没有 ksud webadmin**，Ultra 环境也只服务它自己的模块。于是 Lite 模式下
 * KSU 脚本模块的 WebUI 在手机上根本没有入口。这里用同一个路径形状补上这一层，
 * 让网页端与 ksud 的 `/modweb/<id>/` 兼容（含 HTML 桥注入）。
 *
 * 与 ksud `webadmin.rs` 对齐的四条：
 *  1. 只允许 `GET`（`POST` → 405，不是静默 404）；
 *  2. `id` 必须 `[A-Za-z0-9._-]` 且不含 `..`；`rel` 逐段校验，防目录穿越；
 *  3. 文件不存在 → 404，后端不可用 → **501** + 原因（诚实降级，不伪装成 404）；
 *  4. HTML 注入 `window.__7K_BASE__`（含 token 的 base）+ `modbridge.js`，否则模块页里的
 *     `ksu.*` 桥一个请求都发不出去。
 *
 * 与 ksud 的一个差异（记录在对比表里）：ksud 把资源编译进 ksud 二进制，路径穿越由
 * `ALLOWED_ASSETS` 白名单兜底；我们的文件来自设备上的模块目录，因此**必须**用
 * canonical path 复核（见 `AndroidLiteWebBackend.moduleWebFile`），只靠字符串校验不够。
 */
object LiteWebModWeb {

    /** 单个文件上限：模块 WebUI 是页面，不是镜像；超过就明确拒绝。 */
    const val MAX_FILE_BYTES = 4 * 1024 * 1024

    /** 与 ksud `CSP_MODWEB` 保持一致（同源 iframe/脚本，禁外链 base/form）。 */
    const val CSP_MODWEB =
        "default-src 'none'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: https:; connect-src 'self'; base-uri 'none'; form-action 'none'"

    /** 模块 id：与 handler 的 `validModuleId` 同一规则，且不允许 `..`。 */
    private val MODULE_ID = Regex("^[A-Za-z0-9._-]+$")

    /** 解析结果：要么是 (id, rel)，要么是一条明确的拒绝响应。 */
    sealed interface Parsed {
        data class File(val moduleId: String, val rel: String) : Parsed
        data class Rejected(val response: LiteWebHttpResponse) : Parsed
    }

    /**
     * `/modweb`、`/modweb/<id>`、`/modweb/<id>/<rel...>`。
     * 不合法 → 400 `MODWEB_PATH_INVALID`；未知子形状 → 404（不暴露内部规则）。
     */
    fun parse(route: String): Parsed {
        if (route != "/modweb" && !route.startsWith("/modweb/")) {
            return Parsed.Rejected(LiteWebHttpResponse.empty(404))
        }
        val rest = route.removePrefix("/modweb").trimStart('/')
        if (rest.isEmpty()) {
            return Parsed.Rejected(
                LiteWebHttpResponse.error(400, "MODWEB_ID_MISSING", "use /modweb/<module-id>/"),
            )
        }
        val moduleId = rest.substringBefore('/')
        if (!validModuleId(moduleId)) {
            return Parsed.Rejected(
                LiteWebHttpResponse.error(400, "MODWEB_ID_INVALID", "module id must be [A-Za-z0-9._-]"),
            )
        }
        val rawRel = rest.substringAfter('/', "")
        val rel = normalizeRel(rawRel)
            ?: return Parsed.Rejected(
                LiteWebHttpResponse.error(400, "MODWEB_PATH_INVALID", "relative path rejected"),
            )
        return Parsed.File(moduleId, rel)
    }

    fun validModuleId(id: String): Boolean =
        id.isNotEmpty() && id.length <= 128 && MODULE_ID.matches(id) && !id.contains("..")

    /**
     * 相对路径归一：空 / `/` → `index.html`；每段不许 `.`、`..`、前导 `.`（点文件）、
     * 反斜杠、NUL 与控制字符。返回**不带**前导斜杠的路径。
     */
    fun normalizeRel(raw: String): String? {
        if (raw.isEmpty()) return "index.html"
        if (raw.any { it == '\\' || it == '\u0000' || it.code < 0x20 }) return null
        val trimmed = raw.trim('/')
        if (trimmed.isEmpty()) return "index.html"
        val segments = trimmed.split('/')
        if (segments.any { it.isEmpty() }) return null
        if (segments.any { it == "." || it == ".." || it.startsWith(".") }) return null
        if (segments.any { segment -> segment.any { it.code !in 0x21..0x7E } }) return null
        return segments.joinToString("/")
    }

    /** 与静态资源同一套 MIME（浏览器靠它决定是否渲染）。 */
    fun contentType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html; charset=utf-8"
        "js", "mjs" -> "application/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "woff2" -> "font/woff2"
        "ico" -> "image/x-icon"
        "txt" -> "text/plain; charset=utf-8"
        else -> "application/octet-stream"
    }

    fun isHtml(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in setOf("html", "htm")

    /**
     * 桥注入片段。`base` 是**带 token** 的 `/{prefix}/{token}`（ksud 同名变量），
     * 少了 token，`modbridge.js` 自己都取不到。
     */
    fun bridgeTag(base: String, moduleId: String): String =
        "<script>window.__7K_BASE__=\"$base\"</script>" +
            "<script>window.__7K_MODULE__={\"id\":\"$moduleId\",\"lite\":true}</script>" +
            "<script src=\"$base/a/modbridge.js\"></script>"

    /**
     * 在模块自己的首段脚本前安装桥：优先 `<head...>` 后，其次 `<body...>` 后，再次最前面。
     * 只做一次（模块页面自己已经注入过就不重复）。
     */
    fun injectBridge(html: String, tag: String): String {
        if (html.contains("__7K_BASE__")) return html
        val headStart = Regex("<head(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE).find(html)
        if (headStart != null) {
            val at = headStart.range.last + 1
            return html.substring(0, at) + tag + html.substring(at)
        }
        val bodyStart = Regex("<body[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (bodyStart != null) {
            val at = bodyStart.range.last + 1
            return html.substring(0, at) + tag + html.substring(at)
        }
        return tag + html
    }
}

/** 模块 webroot 单文件读取结果（后端实现返回，handler 决定状态码）。 */
sealed interface LiteWebModuleWeb {
    data class Found(val mime: String, val bytes: ByteArray) : LiteWebModuleWeb

    data object NotFound : LiteWebModuleWeb

    /** 后端不可用（未接线 / Ultra 模块 webroot 不在本机 / 文件过大）：→ 501 + reason。 */
    data class Unavailable(val reason: String) : LiteWebModuleWeb
}
