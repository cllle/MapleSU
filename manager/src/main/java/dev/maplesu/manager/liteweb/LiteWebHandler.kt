package dev.maplesu.manager.liteweb

import dev.maplesu.manager.modules.compat.validUltraModuleControlId

/**
 * H2：Lite WebUI 路由（纯 Kotlin，JVM 可测）。
 *
 * 静态：`GET {prefix}/{token}/` → `assets/liteweb/index.html`，其余静态资源同前缀；
 * `/modweb/<id>/...` 提供模块资源；`POST /api/module/bridge` 分派文档会话。
 *
 * API：
 * - `GET  /api/ping` / `status` / `modules` / `superusers` / `sulog`
 * - `GET  /api/hidden` → `{hidden,webuiPort,recovery:{apkBackupPresent,apkShaValid,daemonRunning,bootPersistence},steps}`
 * - `POST /api/hidden {enable:"0"|false}` → **完整解除寄生**（宿主库还原 → loader 删除 →
 *   撤销宿主授权 → 停 daemon，逐条 `steps` 回显）；`{enable:true}` → 409（隐藏必须走 activate）
 * - `POST /api/activate {port?/webuiPort?, confirm:true}` → 一键隐藏事务（备份→端口→daemon→ping→写 ON→复核）
 * - `POST /api/restore {action:"full"|"unhide"}` → 一键恢复（sha→重装→回读→OFF→组件→验证）
 * - `GET/POST /api/config/port` → `webuiPort`（旧别名 `recoveryPort` 兼容）
 * - `POST /api/config/token/reset` → token 轮换（旧链接失效）
 * - `GET/POST /api/settings`（白名单；根/安全项 403）
 * - `GET /api/diagnostics`（含备份/daemon/bootPersistence）、`GET /api/workmode`（只读）
 * - `POST /api/module/toggle`（唯一模块写：启停）、`POST /api/module/action`（动作脚本，后端不可用 501）
 *
 * 安全：所有写接口 POST + JSON + token + Host 校验；token 不写日志；无"演练/自检"用户可见字段。
 */
class LiteWebRequestHandler(
    private val tokenProvider: () -> String?,
    private val backend: LiteWebBackend,
    private val prefix: String = LiteWebConstants.PATH_PREFIX,
    private val documentTokenProvider: () -> String? = tokenProvider,
) : LiteWebHandler {

    override fun handle(request: LiteWebHttpRequest): LiteWebHttpResponse {
        if (request.body.size > LiteWebConstants.MAX_BODY_BYTES) {
            return LiteWebHttpResponse.error(413, "BODY_TOO_LARGE", "limit=${LiteWebConstants.MAX_BODY_BYTES}")
        }
        val token = tokenProvider() ?: return LiteWebHttpResponse.error(503, "TOKEN_UNAVAILABLE", "server not started")
        val authorization = LiteWebAuth.authorize(request, token, prefix)
        if (authorization is LiteWebAuthorization.Rejected) return authorization.response
        val route = (authorization as LiteWebAuthorization.Allowed).route
        if (route.isEmpty() || route == "/") return serveStatic("index.html")
        if (route == "/modweb" || route.startsWith("/modweb/")) {
            return serveModWeb(request, route, token)
        }
        if (route == "/api/module/bridge") {
            if (request.method != "POST") return LiteWebHttpResponse.error(405, "METHOD_NOT_ALLOWED", "use POST")
            return backend.moduleBridgePort().handle(request, token)
        }
        if (route.startsWith("/api/")) return handleApi(request, route.removePrefix("/api"))
        if (route.startsWith("/api")) return LiteWebHttpResponse.empty(404)
        return serveStatic(route)
    }

    // ------------------------------------------------------------------ dispatch

    private fun handleApi(request: LiteWebHttpRequest, apiPath: String): LiteWebHttpResponse {
        val normalized = apiPath.trimEnd('/').ifEmpty { "/" }
        val get = request.method == "GET"
        val post = request.method == "POST"
        return when {
            get && normalized == "/ping" -> LiteWebHttpResponse.ok()
            get && normalized == "/status" -> status()
            get && normalized == "/modules" -> modules()
            get && normalized == "/superusers" -> superusers(legacy = true)
            get && normalized == "/superuser" -> superusers(legacy = false)
            post && normalized == "/superuser" -> setSuperuser(request)
            get && normalized == "/superuser/log" -> superuserLog()
            post && normalized == "/superuser/log" -> clearSuperuserLog(request)
            post && normalized == "/superuser/logging" -> setSuperuserLogging(request)
            get && normalized == "/sulog" -> sulog()
            get && normalized == "/hidden" -> hiddenStatus()
            post && normalized == "/hidden" -> setHidden(request)
            post && normalized == "/activate" -> activate(request)
            get && normalized == "/restore" -> LiteWebHttpResponse.error(405, "METHOD_NOT_ALLOWED", "use POST")
            post && normalized == "/restore" -> restore(request)
            get && normalized == "/config/port" -> configPort()
            post && normalized == "/config/port" -> setConfigPort(request)
            post && normalized == "/config/token/reset" -> resetToken()
            get && normalized == "/settings" -> settings()
            post && normalized == "/settings" -> updateSettings(request)
            get && normalized == "/diagnostics" -> diagnostics()
            get && normalized == "/selfcheck" -> selfCheck()
            get && normalized == "/restore/dry-run" -> restoreDryRun()
            post && normalized == "/daemon/start" -> daemon(start = true)
            post && normalized == "/daemon/stop" -> daemon(start = false)
            get && normalized == "/workmode" -> workMode()
            post && normalized == "/module/toggle" -> toggle(request)
            post && normalized == "/module/action" -> moduleAction(request)
            post -> LiteWebHttpResponse.json(
                501,
                "{\"ok\":false,\"error\":\"NOT_IMPLEMENTED\",\"reason\":${LiteWebJson.quote(writeUnavailable(normalized))}}",
            )
            else -> LiteWebHttpResponse.error(404, "NOT_FOUND", "unknown api endpoint")
        }
    }

    private fun modules(): LiteWebHttpResponse = runCatching {
        val json = backend.modulesJson()
        val failed = LiteWebJson.parseFlatObject(json)?.get("ok") == "false"
        if (json.isBlank()) LiteWebHttpResponse.error(503, "MODULES_BACKEND_UNAVAILABLE", "")
        else LiteWebHttpResponse.json(if (failed) 503 else 200, json)
    }.getOrElse { LiteWebHttpResponse.error(503, "MODULES_BACKEND_UNAVAILABLE", "") }

    private fun superusers(legacy: Boolean): LiteWebHttpResponse = superuserCall {
        val port = backend.superuserPort()
        if (legacy && port === UnavailableSuperuserPort) {
            val json = backend.superusersJson()
            if (json.isBlank()) return@superuserCall LiteWebHttpResponse.error(503, "SU_AUTH_BACKEND_UNAVAILABLE", "")
            return@superuserCall LiteWebHttpResponse.json(200, json)
        }
        when (val result = port.snapshot()) {
            is LiteWebSuResult.Ok -> LiteWebHttpResponse.json(200,
                if (legacy) result.value.entriesJson() else result.value.json())
            is LiteWebSuResult.Failed -> superuserFailure(result)
        }
    }

    private fun setSuperuser(request: LiteWebHttpRequest): LiteWebHttpResponse {
        val fields = superuserFields(request) ?: return LiteWebHttpResponse.error(400, "BODY_INVALID", "")
        if (fields.keys.any { it !in setOf("packageName", "allow", "confirm", "uid") }) {
            return LiteWebHttpResponse.error(400, "SU_AUTH_FIELDS_UNSUPPORTED", "")
        }
        if (fields.containsKey("uid") && !backend.superuserPort().uidScoped) {
            return LiteWebHttpResponse.error(400, "SU_AUTH_FIELDS_UNSUPPORTED", "")
        }
        if (fields["confirm"] != "true") return LiteWebHttpResponse.error(400, "CONFIRM_REQUIRED", "")
        val name = fields["packageName"].orEmpty()
        if (!LiteWebSuperuserManager.validPackage(name)) return LiteWebHttpResponse.error(400, "SU_AUTH_PACKAGE_INVALID", "")
        val allowed = fields["allow"]?.toBooleanStrictOrNull()
            ?: return LiteWebHttpResponse.error(400, "SU_AUTH_ALLOW_INVALID", "")
        val uid = fields["uid"]?.toIntOrNull()?.takeIf { it >= 0 }
        if (fields.containsKey("uid") && uid == null) return LiteWebHttpResponse.error(400, "SU_AUTH_UID_INVALID", "")
        return superuserCall {
            when (val result = backend.superuserPort().setAllowed(name, allowed, uid)) {
                is LiteWebSuResult.Failed -> superuserFailure(result)
                is LiteWebSuResult.Ok -> LiteWebHttpResponse.json(200,
                    "{\"ok\":true,\"packageName\":${LiteWebJson.quote(result.value.packageName)}," +
                        "\"allowed\":${result.value.allowed},\"changed\":${result.value.changed}}")
            }
        }
    }

    private fun superuserLog(): LiteWebHttpResponse = superuserCall {
        when (val result = backend.superuserPort().log()) {
            is LiteWebSuResult.Failed -> superuserFailure(result)
            is LiteWebSuResult.Ok -> LiteWebHttpResponse.json(200,
                "{\"ok\":true,\"source\":${LiteWebJson.quote(result.value.source)},\"text\":${LiteWebJson.quote(result.value.text)}," +
                    "\"truncated\":${result.value.truncated}}")
        }
    }

    private fun clearSuperuserLog(request: LiteWebHttpRequest): LiteWebHttpResponse {
        val fields = superuserFields(request) ?: return LiteWebHttpResponse.error(400, "BODY_INVALID", "")
        if (fields.keys.any { it !in setOf("action", "confirm") } || fields["action"] != "clear") {
            return LiteWebHttpResponse.error(400, "SU_LOG_ACTION_INVALID", "")
        }
        if (fields["confirm"] != "true") return LiteWebHttpResponse.error(400, "CONFIRM_REQUIRED", "")
        return superuserCall {
            when (val result = backend.superuserPort().clearLog()) {
                is LiteWebSuResult.Failed -> superuserFailure(result)
                is LiteWebSuResult.Ok -> LiteWebHttpResponse.json(200, "{\"ok\":true,\"cleared\":true}")
            }
        }
    }

    private fun setSuperuserLogging(request: LiteWebHttpRequest): LiteWebHttpResponse {
        val fields = superuserFields(request) ?: return LiteWebHttpResponse.error(400, "BODY_INVALID", "")
        if (fields.keys.any { it !in setOf("enabled", "confirm") }) {
            return LiteWebHttpResponse.error(400, "SU_LOGGING_FIELDS_UNSUPPORTED", "")
        }
        if (fields["confirm"] != "true") return LiteWebHttpResponse.error(400, "CONFIRM_REQUIRED", "")
        val enabled = fields["enabled"]?.toBooleanStrictOrNull()
            ?: return LiteWebHttpResponse.error(400, "SU_LOGGING_VALUE_INVALID", "")
        return superuserCall {
            when (val result = backend.superuserPort().setLogging(enabled)) {
                is LiteWebSuResult.Failed -> superuserFailure(result)
                is LiteWebSuResult.Ok -> LiteWebHttpResponse.json(200, "{\"ok\":true,\"enabled\":${result.value}}")
            }
        }
    }

    private fun superuserFields(request: LiteWebHttpRequest): Map<String, String>? {
        val contentType = request.header("Content-Type")?.substringBefore(';')?.trim()
        if (!contentType.equals("application/json", ignoreCase = true)) return null
        return LiteWebJson.parseFlatObject(request.textBody())
    }

    private fun superuserCall(block: () -> LiteWebHttpResponse): LiteWebHttpResponse =
        runCatching(block).getOrElse { LiteWebHttpResponse.error(503, "SU_AUTH_BACKEND_UNAVAILABLE", "") }

    private fun superuserFailure(failure: LiteWebSuResult.Failed): LiteWebHttpResponse {
        val status = when {
            failure.code == "SU_AUTH_PROTECTED" -> 403
            failure.rollback != null -> 409
            failure.unavailable -> 503
            else -> 409
        }
        val rollback = failure.rollback?.let { ",\"rollback\":${LiteWebJson.quote(it)}" }.orEmpty()
        return LiteWebHttpResponse.json(status, "{\"ok\":false,\"error\":${LiteWebJson.quote(failure.code)}$rollback}")
    }

    // ------------------------------------------------------------------ status / hidden

    private fun status(): LiteWebHttpResponse {
        val status = backend.status()
        val liveHidden = runCatching { backend.hiddenPort().currentHidden() }.getOrNull()
        // 顺序：实时读 > 后端断言。两者都 null 就输出 null（不冒充 false）。
        val hidden: Boolean? = liveHidden ?: status.hiddenKnown
        val body = buildString {
            append("{\"ok\":true")
            append(",\"mode\":").append(LiteWebJson.quote(status.mode))
            append(",\"root\":").append(status.rootAvailable)
            append(",\"moduleCount\":").append(status.moduleCount)
            if (status.moduleCountError.isNotEmpty()) {
                append(",\"moduleCountError\":").append(LiteWebJson.quote(status.moduleCountError))
            }
            append(",\"hidden\":").append(hidden?.toString() ?: "null")
            append(",\"uapi\":").append(LiteWebJson.quote(status.uapi))
            append(",\"serverVersion\":").append(status.serverVersion)
            append(",\"webuiPort\":").append(backend.configuredPort())
            append("}")
        }
        return LiteWebHttpResponse.json(200, body)
    }

    private fun hiddenStatus(): LiteWebHttpResponse {
        val view = runCatching { backend.hiddenStatus() }.getOrElse {
            LiteWebHiddenView(
                hidden = null,
                port = backend.configuredPort(),
                recovery = LiteWebRecoveryStatus(),
                steps = emptyList(),
            )
        }
        val body = buildString {
            append("{\"ok\":true,\"hidden\":").append(view.hidden ?: "null")
            append(",\"managerUninstalled\":").append(view.managerUninstalled ?: "null")
            append(",\"webuiPort\":").append(view.port)
            append(",\"recovery\":").append(recoveryJson(view.recovery))
            append(",\"steps\":").append(stepsJson(view.steps))
            append("}")
        }
        return LiteWebHttpResponse.json(200, body)
    }

    private fun recoveryJson(recovery: LiteWebRecoveryStatus): String = buildString {
        append("{\"apkBackupPresent\":").append(recovery.apkBackupPresent ?: "null")
        append(",\"apkShaValid\":").append(recovery.apkShaValid ?: "null")
        append(",\"daemonRunning\":").append(recovery.daemonRunning ?: "null")
        append(",\"bootPersistence\":").append(LiteWebJson.quote(recovery.bootPersistence))
        append("}")
    }

    /**
     * 取消隐藏 = **完整解除寄生**：`{enable:"0"/false}` → daemon 侧四步
     * （宿主库还原 → loader 删除 → 撤销授权 → 停 daemon），每步如实回显。
     * `{enable:true}` 必须走 `/api/activate`（409，不伪造成功）。
     *
     * 只有后端完全未接线（每一步都是 `RESTORE_BACKEND_UNAVAILABLE`）才回 501；
     * 接线后某一步失败是 **409 + ok:false + failedStep/code**，不是 501、更不是 200。
     */
    private fun setHidden(request: LiteWebHttpRequest): LiteWebHttpResponse {
        val fields = LiteWebJson.parseFlatObject(request.textBody())
            ?: return LiteWebHttpResponse.error(400, "BODY_INVALID", "expected flat JSON object")
        val raw = fields["enable"] ?: fields["hidden"]
            ?: return LiteWebHttpResponse.error(400, "HIDDEN_VALUE_MISSING", "enable")
        val enabled = when (raw.trim().lowercase()) {
            "1", "true", "on", "enable" -> true
            "0", "false", "off", "disable" -> false
            else -> return LiteWebHttpResponse.error(400, "HIDDEN_VALUE_INVALID", "expected 1/0")
        }
        if (enabled) {
            return LiteWebHttpResponse.json(
                409,
                "{\"ok\":false,\"error\":\"USE_ACTIVATE_API\",\"reason\":" +
                    LiteWebJson.quote("hide must go through POST /api/activate") + "}",
            )
        }
        val result = LiteWebUnhideTransaction(backend.unhidePort()).run()
        if (!result.ok && result.backendUnavailable) {
            return LiteWebHttpResponse.json(
                501,
                "{\"ok\":false,\"error\":\"NOT_IMPLEMENTED\",\"reason\":\"RESTORE_BACKEND_UNAVAILABLE\"," +
                    "\"steps\":${stepsJson(result.steps)}}",
            )
        }
        val body = buildString {
            append("{\"ok\":").append(result.ok)
            // 兼容旧字段：ok=false 时 hidden 仍为 true（真的没解除），不谎报。
            append(",\"hidden\":").append(!result.ok)
            append(",\"steps\":").append(stepsJson(result.steps))
            append(",\"failedStep\":").append(LiteWebJson.quote(result.failedStep))
            append(",\"code\":").append(LiteWebJson.quote(result.code))
            append(",\"retryable\":").append(result.retryable)
            append("}")
        }
        return LiteWebHttpResponse.json(if (result.ok) 200 else 409, body)
    }

    // ------------------------------------------------------------------ activate / restore

    private fun activate(request: LiteWebHttpRequest): LiteWebHttpResponse {
        val fields = LiteWebJson.parseFlatObject(request.textBody())
            ?: return LiteWebHttpResponse.error(400, "BODY_INVALID", "expected flat JSON object")
        val confirm = fields["confirm"]?.trim()?.lowercase()
        if (confirm !in setOf("true", "1", "yes")) {
            return LiteWebHttpResponse.error(400, "CONFIRM_REQUIRED", "confirm:true")
        }
        val portText = fields["port"] ?: fields["webuiPort"]
        val requestedPort = portText?.toIntOrNull()
        if (portText != null && requestedPort == null) {
            return LiteWebHttpResponse.error(400, LiteWebPortPolicy.CODE_RANGE, "webuiPort")
        }
        val transaction = LiteWebActivateTransaction(backend.activatePort(), tokenPresent = backend::tokenPresent)
        val result = transaction.run(requestedPort)
        if (!result.ok && result.steps.all { it.error == LiteWebRecoveryCodes.BACKEND_UNAVAILABLE }) {
            return LiteWebHttpResponse.json(
                501,
                "{\"ok\":false,\"error\":\"NOT_IMPLEMENTED\",\"reason\":\"ACTIVATE_BACKEND_UNAVAILABLE\"," +
                    "\"steps\":${stepsJson(result.steps)}}",
            )
        }
        return LiteWebHttpResponse.json(
            if (result.ok) 200 else 409,
            "{\"ok\":${result.ok},\"webuiPort\":${result.port}," +
                "\"recoveryUrlTokenPresent\":${result.recoveryUrlTokenPresent}," +
                "\"error\":${LiteWebJson.quote(result.code)}," +
                "\"steps\":${stepsJson(result.steps)}," +
                "\"recovery\":${recoveryJson(result.recovery)}}",
        )
    }

    private fun restore(request: LiteWebHttpRequest): LiteWebHttpResponse {
        val fields = LiteWebJson.parseFlatObject(request.textBody())
            ?: return LiteWebHttpResponse.error(400, "BODY_INVALID", "expected flat JSON object")
        val action = fields["action"]?.trim()
        LiteWebRestoreActions.validate(action)?.let {
            return LiteWebHttpResponse.error(400, "RESTORE_ACTION_INVALID", "action")
        }
        val result = LiteWebRestoreTransaction(backend.restorePort()).run(action!!)
        if (!result.ok && result.steps.all { it.error == LiteWebRecoveryCodes.RESTORE_BACKEND_UNAVAILABLE }) {
            return LiteWebHttpResponse.json(
                501,
                "{\"ok\":false,\"error\":\"NOT_IMPLEMENTED\",\"reason\":\"RESTORE_BACKEND_UNAVAILABLE\"," +
                    "\"steps\":${stepsJson(result.steps)}}",
            )
        }
        return LiteWebHttpResponse.json(
            if (result.ok) 200 else 409,
            "{\"ok\":${result.ok},\"action\":${LiteWebJson.quote(result.action)}," +
                "\"steps\":${stepsJson(result.steps)},\"needReboot\":${result.needReboot}}",
        )
    }

    // ------------------------------------------------------------------ config

    private fun configPort(): LiteWebHttpResponse = LiteWebHttpResponse.json(
        200,
        "{\"ok\":true,\"webuiPort\":${backend.configuredPort()}," +
            "\"recoveryPort\":${backend.configuredPort()}}",
    )

    private fun setConfigPort(request: LiteWebHttpRequest): LiteWebHttpResponse {
        val fields = LiteWebJson.parseFlatObject(request.textBody())
            ?: return LiteWebHttpResponse.error(400, "BODY_INVALID", "expected flat JSON object")
        val raw = fields["webuiPort"] ?: fields["recoveryPort"]
            ?: return LiteWebHttpResponse.error(400, "PORT_MISSING", "webuiPort")
        val requested = raw.toIntOrNull()
            ?: return LiteWebHttpResponse.error(400, LiteWebPortPolicy.CODE_RANGE, "webuiPort")
        LiteWebPortPolicy.validate(requested)?.let {
            return LiteWebHttpResponse.error(400, it, "webuiPort=$requested")
        }
        val step = backend.applyConfiguredPort(requested)
        if (step.ok) {
            val actual = runCatching { backend.configuredPort() }.getOrNull()
                ?: return LiteWebHttpResponse.error(409, "LITEWEB_PORT_READBACK_FAILED", "webuiPort")
            if (actual != requested) {
                return LiteWebHttpResponse.json(
                    409,
                    "{\"ok\":false,\"error\":\"LITEWEB_PORT_READBACK_MISMATCH\"," +
                        "\"requested\":$requested,\"webuiPort\":$actual}",
                )
            }
            return LiteWebHttpResponse.json(
                200,
                "{\"ok\":true,\"webuiPort\":$actual,\"recoveryPort\":$actual}",
            )
        }
        val unavailable = step.error == LiteWebRecoveryCodes.BACKEND_UNAVAILABLE ||
            step.error == LiteWebRecoveryCodes.PORT_APPLY_FAILED
        return LiteWebHttpResponse.json(
            if (unavailable) 501 else 409,
            "{\"ok\":false,\"error\":${LiteWebJson.quote(step.error)}," +
                "\"webuiPort\":${backend.configuredPort()}}",
        )
    }

    private fun resetToken(): LiteWebHttpResponse {
        val step = backend.resetToken()
        return when {
            step.ok -> LiteWebHttpResponse.json(200, "{\"ok\":true}")
            step.error == "TOKEN_RESET_BACKEND_UNAVAILABLE" -> LiteWebHttpResponse.json(
                501,
                "{\"ok\":false,\"error\":\"NOT_IMPLEMENTED\",\"reason\":\"TOKEN_RESET_BACKEND_UNAVAILABLE\"}",
            )
            else -> LiteWebHttpResponse.json(
                409,
                "{\"ok\":false,\"error\":${LiteWebJson.quote(step.error)}}",
            )
        }
    }

    // ------------------------------------------------------------------ settings / diagnostics

    private fun settings(): LiteWebHttpResponse {
        val values = runCatching { backend.settingsPort().read() }.getOrDefault(emptyMap())
        return LiteWebHttpResponse.json(200, "{\"ok\":true,\"items\":${itemsJson(values)}}")
    }

    private fun updateSettings(request: LiteWebHttpRequest): LiteWebHttpResponse {
        val fields = LiteWebJson.parseFlatObject(request.textBody())
            ?: return LiteWebHttpResponse.error(400, "BODY_INVALID", "expected flat JSON object")
        if (fields.isEmpty()) return LiteWebHttpResponse.error(400, "SETTINGS_EMPTY", "no keys")
        val ordered = fields.entries.sortedBy { it.key }
        for ((key, value) in ordered) {
            LiteWebSettingsPolicy.checkKey(key)?.let {
                return LiteWebHttpResponse.json(
                    403,
                    "{\"ok\":false,\"error\":${LiteWebJson.quote(it)},\"key\":${LiteWebJson.quote(key)}}",
                )
            }
            LiteWebSettingsPolicy.checkValue(key, value)?.let {
                return LiteWebHttpResponse.error(400, it, key)
            }
        }
        for ((key, value) in ordered) {
            when (val outcome = backend.settingsPort().write(key, value)) {
                is LiteWebSettingsWriteResult.Ok -> Unit
                is LiteWebSettingsWriteResult.Forbidden -> return LiteWebHttpResponse.json(
                    403,
                    "{\"ok\":false,\"error\":\"SETTINGS_KEY_FORBIDDEN\",\"key\":${LiteWebJson.quote(outcome.key)}}",
                )
                is LiteWebSettingsWriteResult.Invalid -> return LiteWebHttpResponse.error(400, outcome.reason, outcome.key)
                is LiteWebSettingsWriteResult.Unavailable -> return LiteWebHttpResponse.json(
                    501,
                    "{\"ok\":false,\"error\":\"NOT_IMPLEMENTED\",\"reason\":${LiteWebJson.quote(outcome.reason)}}",
                )
                is LiteWebSettingsWriteResult.Failed -> return LiteWebHttpResponse.json(
                    409,
                    "{\"ok\":false,\"error\":${LiteWebJson.quote(outcome.reason)},\"key\":${LiteWebJson.quote(outcome.key)}}",
                )
            }
        }
        return settings()
    }

    private fun diagnostics(): LiteWebHttpResponse {
        val view = runCatching { backend.diagnosticsPort().read() }.getOrDefault(LiteWebDiagnosticsView())
        val mode = runCatching { backend.modeBackend().mode() }.getOrDefault(
            LiteWebModeInfo(mode = view.mode, rootBackend = view.rootBackend),
        )
        val backup = runCatching { backend.modeBackend().backup() }.getOrDefault(LiteWebBackupInfo())
        val boot = runCatching { backend.modeBackend().bootEvidence() }
            .getOrDefault(LiteWebBootEvidence())
        val bootPersistence = if (boot.bootPersistence != LiteWebBootPersistence.UNKNOWN) {
            boot.bootPersistence
        } else {
            view.bootPersistence
        }
        return LiteWebHttpResponse.json(
            200,
            "{\"ok\":true,\"probe\":${LiteWebJson.quote(view.probe)}," +
                "\"rootBackend\":${LiteWebJson.quote(mode.rootBackend)}," +
                "\"mode\":${LiteWebJson.quote(mode.mode)}," +
                "\"bootHook\":${LiteWebJson.quote(mode.bootHook)}," +
                "\"port\":${view.port},\"webuiPort\":${view.port}," +
                "\"version\":${LiteWebJson.quote(view.version)}," +
                "\"serverVersion\":${view.serverVersion}," +
                "\"apkBackupPresent\":${backup.present ?: view.apkBackupPresent ?: "null"}," +
                "\"apkShaValid\":${backup.shaValid ?: view.apkShaValid ?: "null"}," +
                "\"daemonUptimeSeconds\":${view.daemonUptimeSeconds ?: "null"}," +
                "\"bootPersistence\":${LiteWebJson.quote(bootPersistence)}}",
        )
    }

    /** 自检页：启动/链接/状态/备份/重启证据 + restore dry-run（只读，无副作用）。 */
    private fun selfCheck(): LiteWebHttpResponse {
        val modeBackend = runCatching { backend.modeBackend() }.getOrNull()
            ?: UnavailableHiddenModeBackend
        val mode = runCatching { modeBackend.mode() }.getOrDefault(LiteWebModeInfo())
        val backup = runCatching { modeBackend.backup() }.getOrDefault(LiteWebBackupInfo())
        val boot = runCatching { modeBackend.bootEvidence() }.getOrDefault(LiteWebBootEvidence())
        val plan = runCatching { modeBackend.restorePlan() }
            .getOrDefault(UnavailableHiddenModeBackend.restorePlan())
        val tokenPresent = runCatching { backend.tokenPresent() }.getOrDefault(false)
        val body = buildString {
            append("{\"ok\":true")
            append(",\"mode\":{\"mode\":").append(LiteWebJson.quote(mode.mode))
            append(",\"rootBackend\":").append(LiteWebJson.quote(mode.rootBackend))
            append(",\"bootHook\":").append(LiteWebJson.quote(mode.bootHook))
            append(",\"daemonRunning\":").append(mode.daemonRunning ?: "null")
            append(",\"daemonPort\":").append(mode.daemonPort ?: backend.configuredPort())
            append("}")
            append(",\"link\":{\"tokenPresent\":").append(tokenPresent)
            append(",\"webuiPort\":").append(backend.configuredPort()).append("}")
            append(",\"backup\":{\"present\":").append(backup.present ?: "null")
            append(",\"shaValid\":").append(backup.shaValid ?: "null").append("}")
            append(",\"bootEvidence\":{\"bootIdObserved\":").append(boot.bootIdObserved ?: "null")
            append(",\"persistent\":").append(boot.persistent ?: "null")
            append(",\"bootPersistence\":").append(LiteWebJson.quote(boot.bootPersistence)).append("}")
            append(",\"restoreDryRun\":{\"steps\":").append(stepsJson(plan)).append("}")
            append("}")
        }
        return LiteWebHttpResponse.json(200, body)
    }

    /** restore dry-run：返回两模式一致的计划步骤，不执行。 */
    private fun restoreDryRun(): LiteWebHttpResponse {
        val modeBackend = runCatching { backend.modeBackend() }.getOrNull()
            ?: UnavailableHiddenModeBackend
        val steps = runCatching { modeBackend.restorePlan() }
            .getOrDefault(UnavailableHiddenModeBackend.restorePlan())
        if (steps.isNotEmpty() && steps.all { it.error == "MODE_BACKEND_UNAVAILABLE" }) {
            return LiteWebHttpResponse.json(
                501,
                "{\"ok\":false,\"error\":\"NOT_IMPLEMENTED\",\"reason\":\"MODE_BACKEND_UNAVAILABLE\"," +
                    "\"steps\":${stepsJson(steps)}}",
            )
        }
        return LiteWebHttpResponse.json(200, "{\"ok\":true,\"steps\":${stepsJson(steps)}}")
    }

    /** 自检页"启动/停止 WebUI daemon"（模式无关；不写 hidden、不备份）。 */
    private fun daemon(start: Boolean): LiteWebHttpResponse {
        val modeBackend = runCatching { backend.modeBackend() }.getOrNull()
            ?: UnavailableHiddenModeBackend
        val step = runCatching { if (start) modeBackend.startDaemon() else modeBackend.stopDaemon() }
            .getOrElse { LiteWebRestoreStep("daemon", false, "MODE_BACKEND_EXCEPTION") }
        if (step.error == "MODE_BACKEND_UNAVAILABLE") {
            return LiteWebHttpResponse.json(
                501,
                "{\"ok\":false,\"error\":\"NOT_IMPLEMENTED\",\"reason\":\"MODE_BACKEND_UNAVAILABLE\"}",
            )
        }
        if (!step.ok) {
            return LiteWebHttpResponse.json(
                409,
                "{\"ok\":false,\"error\":${LiteWebJson.quote(step.error)}," +
                    "\"steps\":${stepsJson(listOf(step))}}",
            )
        }
        val info = runCatching { modeBackend.mode() }.getOrDefault(LiteWebModeInfo())
        return LiteWebHttpResponse.json(
            200,
            "{\"ok\":true,\"running\":${info.daemonRunning ?: !start}," +
                "\"webuiPort\":${info.daemonPort ?: backend.configuredPort()}," +
                "\"urlTokenPresent\":${runCatching { backend.tokenPresent() }.getOrDefault(false)}}",
        )
    }

    private fun workMode(): LiteWebHttpResponse {
        val view = runCatching { backend.workModePort().read() }.getOrDefault(LiteWebWorkModeView())
        return LiteWebHttpResponse.json(
            200,
            "{\"ok\":true,\"mode\":${LiteWebJson.quote(view.mode)}," +
                "\"canSwitch\":${view.canSwitch}," +
                "\"blocker\":${view.blocker?.let(LiteWebJson::quote) ?: "null"}," +
                "\"target\":${LiteWebJson.quote(view.target)}}",
        )
    }

    // ------------------------------------------------------------------ modules / sulog / static

    private fun sulog(): LiteWebHttpResponse {
        val sulog = backend.sulog()
        if (!sulog.available) {
            return LiteWebHttpResponse.json(
                200,
                "{\"ok\":true,\"available\":false,\"lines\":[],\"reason\":${LiteWebJson.quote(sulog.reason)}}",
            )
        }
        val lines = sulog.lines.joinToString(",") { LiteWebJson.quote(it) }
        return LiteWebHttpResponse.json(200, "{\"ok\":true,\"available\":true,\"lines\":[$lines]}")
    }

    private fun toggle(request: LiteWebHttpRequest): LiteWebHttpResponse {
        val fields = LiteWebJson.parseFlatObject(request.textBody())
            ?: return LiteWebHttpResponse.error(400, "BODY_INVALID", "expected flat JSON object")
        val moduleId = fields["id"] ?: fields["module"]
            ?: return LiteWebHttpResponse.error(400, "MODULE_ID_MISSING", "id")
        if (!validModuleId(moduleId) && !(moduleId.startsWith("maplesu:") &&
                validUltraModuleControlId(moduleId.removePrefix("maplesu:"))))
            return LiteWebHttpResponse.error(400, "MODULE_ID_INVALID", "id")
        val enabled = when (fields["enabled"]?.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> return LiteWebHttpResponse.error(400, "ENABLED_INVALID", "expected boolean")
        }
        return when (val result = backend.toggleModule(moduleId, enabled)) {
            is LiteWebToggleView.Ok -> LiteWebHttpResponse.json(
                200,
                "{\"ok\":true,\"id\":${LiteWebJson.quote(result.moduleId)},\"enabled\":${result.enabled},\"needsReboot\":${result.needsReboot}}",
            )
            is LiteWebToggleView.Failed -> LiteWebHttpResponse.json(
                409,
                "{\"ok\":false,\"error\":${LiteWebJson.quote(result.code)},\"reason\":${LiteWebJson.quote(result.reason)}}",
            )
            is LiteWebToggleView.Unavailable -> LiteWebHttpResponse.json(
                503,
                "{\"ok\":false,\"error\":\"BACKEND_UNAVAILABLE\",\"reason\":${LiteWebJson.quote(result.reason)}}",
            )
        }
    }

    private fun moduleAction(request: LiteWebHttpRequest): LiteWebHttpResponse {
        val fields = LiteWebJson.parseFlatObject(request.textBody())
            ?: return LiteWebHttpResponse.error(400, "BODY_INVALID", "expected flat JSON object")
        val id = fields["id"] ?: return LiteWebHttpResponse.error(400, "MODULE_ID_MISSING", "id")
        if (!validModuleId(id)) return LiteWebHttpResponse.error(400, "MODULE_ID_INVALID", "id")
        val action = fields["action"] ?: return LiteWebHttpResponse.error(400, "ACTION_MISSING", "action")
        if (!action.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))) {
            return LiteWebHttpResponse.error(400, "ACTION_INVALID", "action")
        }
        return when (val result = backend.moduleActionPort().run(id, action)) {
            is LiteWebModuleActionResult.Ok -> LiteWebHttpResponse.json(
                200,
                "{\"ok\":true,\"id\":${LiteWebJson.quote(result.moduleId)}," +
                    "\"action\":${LiteWebJson.quote(result.action)}," +
                    "\"output\":${LiteWebJson.quote(result.output)}}",
            )
            is LiteWebModuleActionResult.Failed -> LiteWebHttpResponse.json(
                409,
                "{\"ok\":false,\"error\":${LiteWebJson.quote(result.code)},\"reason\":${LiteWebJson.quote(result.reason)}}",
            )
            is LiteWebModuleActionResult.Unavailable -> LiteWebHttpResponse.json(
                501,
                "{\"ok\":false,\"error\":\"NOT_IMPLEMENTED\",\"reason\":${LiteWebJson.quote(result.reason)}}",
            )
        }
    }

    private fun serveStatic(route: String): LiteWebHttpResponse {
        val relative = LiteWebPaths.normalizeStatic(route)
            ?: return LiteWebHttpResponse.error(400, "PATH_INVALID", "static path")
        val bytes = backend.assets().read(relative)
            ?: return LiteWebHttpResponse.error(404, "NOT_FOUND", relative)
        return LiteWebHttpResponse(200, contentType(relative), bytes)
    }

    /**
     * `{prefix}/{token}/modweb/<模块id>/<相对路径>`：模块自带 WebUI 的只读代理。
     *
     * 与 ksud 的 `/modweb/<id>/` 同名同形；但这里**不吞错**：
     *  - 非 GET → 405（写操作不该静默变成 404）；
     *  - 后端未接线/Ultra 模块 webroot 不在本机 → 501 + 原因（不是 404「像不存在」）；
     *  - HTML 注入带 token 的 `__7K_BASE__` + `modbridge.js`，否则页面里的 `ksu.*` 全 401。
     */
    private fun serveModWeb(
        request: LiteWebHttpRequest,
        route: String,
        token: String,
    ): LiteWebHttpResponse {
        if (request.method != "GET") {
            return LiteWebHttpResponse.error(
                405, "METHOD_NOT_ALLOWED", "module WebUI proxy is read-only (GET)",
            )
        }
        val parsed = LiteWebModWeb.parse(route)
        if (parsed is LiteWebModWeb.Parsed.Rejected) return parsed.response
        val file = parsed as LiteWebModWeb.Parsed.File
        return when (val result = backend.moduleWebFile(file.moduleId, file.rel)) {
            is LiteWebModuleWeb.NotFound -> LiteWebHttpResponse.error(
                404, "NOT_FOUND", "${file.moduleId}/${file.rel}",
            )
            is LiteWebModuleWeb.Unavailable -> LiteWebHttpResponse.error(
                501, "MODWEB_PROXY_UNAVAILABLE", result.reason,
            )
            is LiteWebModuleWeb.Found -> {
                val bytes = if (LiteWebModWeb.isHtml(file.rel)) {
                    val base = "/$prefix/${documentTokenProvider() ?: token}"
                    val tag = LiteWebModWeb.bridgeTag(base, file.moduleId)
                    LiteWebModWeb.injectBridge(
                        result.bytes.toString(Charsets.UTF_8),
                        tag,
                    ).toByteArray(Charsets.UTF_8)
                } else {
                    result.bytes
                }
                LiteWebHttpResponse(
                    status = 200,
                    contentType = result.mime.ifBlank { LiteWebModWeb.contentType(file.rel) },
                    body = bytes,
                    headers = mapOf(
                        "Content-Security-Policy" to LiteWebModWeb.CSP_MODWEB,
                        "Referrer-Policy" to "same-origin",
                    ),
                )
            }
        }
    }

    private fun contentType(path: String): String = when (path.substringAfterLast('.', "")) {
        "html", "htm" -> "text/html; charset=utf-8"
        "js", "mjs" -> "application/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "woff2" -> "font/woff2"
        "ico" -> "image/x-icon"
        else -> "application/octet-stream"
    }

    private fun stepsJson(steps: List<LiteWebRestoreStep>): String =
        steps.joinToString(",", "[", "]") { step ->
            "{\"name\":${LiteWebJson.quote(step.name)},\"ok\":${step.ok}" +
                (if (step.error.isNotEmpty()) ",\"error\":${LiteWebJson.quote(step.error)}" else "") +
                (if (step.detail.isNotEmpty()) ",\"detail\":${LiteWebJson.quote(step.detail)}" else "") + "}"
        }

    private fun itemsJson(values: Map<String, String>): String =
        values.entries.sortedBy { it.key }.joinToString(",", "{", "}") { (key, value) ->
            "${LiteWebJson.quote(key)}:${LiteWebJson.quote(value)}"
        }

    private fun validModuleId(id: String): Boolean =
        id.isNotBlank() && id.length <= 128 && id.matches(Regex("^[A-Za-z0-9._-]+$"))

    private fun writeUnavailable(apiPath: String): String =
        "Ultra web admin v1 only supports module toggle/action; '$apiPath' write endpoint is not implemented"
}
