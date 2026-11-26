package com.maplesu.core.ui.webui

import com.maplesu.core.data.model.AppInfo
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import dev.maplesu.manager.liteweb.LiteWebHttpRequest
import dev.maplesu.manager.liteweb.LiteWebHttpResponse
import dev.maplesu.manager.liteweb.LiteWebModuleBridgePort
import dev.maplesu.manager.liteweb.LiteWebModuleFiles
import dev.maplesu.manager.liteweb.LiteWebToken
import dev.maplesu.manager.modules.webui.ModuleWebUiSessions
import dev.maplesu.manager.workmode.ModeRuntime
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Authenticated browser documents share the native KSU implementations, never a synthetic WebView. */
internal class ExternalWebUiBridge(
    private val files: LiteWebModuleFiles,
    private val cacheRoot: File,
    private val apps: () -> List<AppInfo>,
    private val sourceCurrent: () -> Boolean,
    private val sourceIdentity: () -> String? = {
        runCatching { File(dev.maplesu.manager.workmode.control.ControlPaths.modesFile()).readText() }.getOrNull()
    },
    private val shellFactory: (String) -> Shell = ::browserRootShell,
) : LiteWebModuleBridgePort {
    private val sessions = ConcurrentHashMap<String, Session>()
    private val closed = AtomicBoolean(false)
    private val reaper = Executors.newSingleThreadScheduledExecutor { job ->
        Thread(job, "module-webui-expiry").apply { isDaemon = true }
    }.apply {
        scheduleWithFixedDelay({ sessions.values.toList().forEach { if (!it.validate()) retire(it) } }, 30, 30, TimeUnit.SECONDS)
    }

    override fun handle(request: LiteWebHttpRequest, token: String): LiteWebHttpResponse {
        return try {
            if (closed.get() || !sourceCurrent()) {
                sessions.values.toList().forEach(::retire)
                error("MODULE_BRIDGE_RETIRED")
            }
            require(request.header("Content-Type")?.substringBefore(';')?.trim() == "application/json") { "MODULE_BRIDGE_JSON_REQUIRED" }
            val input = JSONObject(request.textBody())
            val moduleId = input.getString("id")
            requireDocumentOrigin(request, moduleId)
            sessions.values.toList().forEach { if (it.token != token || !it.validate()) retire(it) }
            val action = input.getString("action")
            val output = if (action == "open") {
                val module = requireNotNull(files.module(moduleId)) { "MODULE_WEB_SESSION_UNAVAILABLE" }
                val session = Session(module, token)
                val admitted = synchronized(sessions) {
                    if (closed.get() || sessions.size >= 16) false else { sessions[session.id] = session; true }
                }
                if (!admitted) { session.close(); error("MODULE_BRIDGE_SESSION_LIMIT") }
                check(session.subscribe() && session.validate()) { retire(session); "MODULE_WEB_SESSION_CHANGED" }
                JSONObject().put("ok", true).put("session", session.id).put("schema", WebUiBridgeDispatcher.schema())
            } else {
                val session = requireNotNull(sessions[input.getString("session")]) { "MODULE_WEB_SESSION_RETIRED" }
                check(session.moduleId == moduleId && LiteWebToken.matches(session.token, token) && session.validate()) {
                    retire(session)
                    "MODULE_WEB_SESSION_CHANGED"
                }
                val response = when (action) {
                    "invoke" -> JSONObject(session.dispatcher.invoke(input.getJSONObject("call").toString()))
                        .put("events", session.drainEvents())
                    "syncExec" -> JSONObject().put("ok", true).put("stdout", session.commands.executeSynchronous(
                        input.getString("command"), input.optString("options", "{}")))
                    "poll" -> JSONObject().put("ok", true).put("events", session.drainEvents())
                    "download" -> session.readDownload(input.getString("download"))
                    "cancelDownload" -> { session.cancelDownload(input.getString("download")); JSONObject().put("ok", true) }
                    "close" -> { retire(session); JSONObject().put("ok", true) }
                    else -> error("MODULE_BRIDGE_ACTION_UNKNOWN")
                }
                if (action != "close") check(session.validate()) { retire(session); "MODULE_WEB_SESSION_CHANGED" }
                response
            }
            LiteWebHttpResponse.json(200, output.toString())
        } catch (error: Exception) {
            LiteWebHttpResponse.error(409, "MODULE_BRIDGE_FAILED", error.message ?: error.javaClass.simpleName)
        }
    }

    private fun requireDocumentOrigin(request: LiteWebHttpRequest, id: String) {
        val host = requireNotNull(request.header("Host"))
        val origin = "http://$host"
        require(request.header("Origin") == origin) { "MODULE_BRIDGE_ORIGIN_DENIED" }
        val referrer = URI(requireNotNull(request.header("Referer")))
        require(referrer.scheme == "http" && referrer.rawAuthority == host && referrer.userInfo == null &&
            referrer.path.contains("/modweb/$id/")) { "MODULE_BRIDGE_DOCUMENT_DENIED" }
        require(request.header("Sec-Fetch-Site").let { it == null || it == "same-origin" }) { "MODULE_BRIDGE_ORIGIN_DENIED" }
    }

    private fun retire(session: Session) {
        sessions.remove(session.id, session)
        session.close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        reaper.shutdownNow()
        sessions.values.toList().forEach(::retire)
    }

    private inner class Session(private val module: LiteWebModuleFiles.Module, val token: String) : WebUiSessionHost, WebUiDownloadSession, AutoCloseable {
        val id = UUID.randomUUID().toString()
        override val moduleId = module.id
        override val moduleDir = module.directory.path
        override val moduleInfoJson = JSONObject(module.properties).put("moduleDir", moduleDir).put("id", moduleId).toString()
        override val cacheDir = File(cacheRoot, id).apply { check(isDirectory || mkdirs()) { "MODULE_DOWNLOAD_CACHE_FAILED" } }
        val revision = ModuleWebUiSessions.revision(moduleId)
        private val activation = ModeRuntime.activation
        private val sourceGeneration = sourceIdentity()
        private val alive = AtomicBoolean(true)
        @Volatile private var touchedAt = System.nanoTime()
        private val subscription = AtomicReference<AutoCloseable?>()
        private val events = ArrayDeque<JSONObject>()
        private var eventBytes = 0
        private val downloads = ConcurrentHashMap<String, BrowserDownload>()
        val commands = WebViewInterface(this, { shellFactory(moduleDir) })
        val downloadBridge = WebUIDownloadInterface(this)
        val dispatcher = WebUiBridgeDispatcher(commands, downloadBridge)

        override fun isCurrent() = alive.get() && !closed.get() && activation === ModeRuntime.activation && sourceCurrent()

        fun validate(): Boolean = isCurrent() &&
            System.nanoTime() - touchedAt <= TimeUnit.MINUTES.toNanos(2) &&
            ModuleWebUiSessions.revision(moduleId) == revision && sourceIdentity() == sourceGeneration &&
            files.identity(moduleId) == module.identity

        fun subscribe(): Boolean {
            val handle = ModuleWebUiSessions.subscribe(moduleId, revision) { retire(this) } ?: return false
            if (!subscription.compareAndSet(null, handle)) { handle.close(); return false }
            // close/commit may have retired the page while subscribe was returning.
            if (!alive.get()) { subscription.getAndSet(null)?.close(); return false }
            return true
        }

        override fun apps() = apps.invoke()

        override fun emit(event: JSONObject) {
            if (!isCurrent()) return
            val accepted = synchronized(events) {
                if (!alive.get() || events.size >= 1024 || eventBytes + event.toString().length > 1024 * 1024) false
                else { events.addLast(event); eventBytes += event.toString().length; true }
            }
            if (!accepted) retire(this)
        }

        fun drainEvents(): JSONArray {
            check(isCurrent()) { "MODULE_WEB_SESSION_RETIRED" }
            touchedAt = System.nanoTime()
            return synchronized(events) {
                JSONArray().also { result ->
                    repeat(minOf(64, events.size)) {
                        val event = events.removeFirst()
                        eventBytes -= event.toString().length
                        result.put(event)
                    }
                }
            }
        }

        override fun openExternal(url: String) { emit(JSONObject().put("kind", "openExternal").put("url", url)) }

        override fun save(fileName: String?, mimeType: String?, onFinished: () -> Unit, source: () -> InputStream): Boolean {
            check(isCurrent()) { "MODULE_WEB_SESSION_RETIRED" }
            check(downloads.size < 16) { "MODULE_DOWNLOAD_LIMIT" }
            val download = BrowserDownload(source(), onFinished)
            val downloadId = UUID.randomUUID().toString()
            downloads[downloadId] = download
            if (!isCurrent()) { cancelDownload(downloadId); return false }
            emit(JSONObject().put("kind", "download").put("id", downloadId)
                .put("fileName", sanitizeWebUIDownloadFileName(fileName)).put("mimeType", mimeType ?: "application/octet-stream"))
            return isCurrent()
        }

        fun readDownload(id: String): JSONObject {
            val download = requireNotNull(downloads[id]) { "MODULE_DOWNLOAD_RETIRED" }
            return synchronized(download) {
                check(isCurrent()) { "MODULE_WEB_SESSION_RETIRED" }
                val chunk = ByteArray(64 * 1024)
                val count = download.input.read(chunk)
                val done = count < 0
                if (done) cancelDownload(id)
                touchedAt = System.nanoTime()
                JSONObject().put("ok", true).put("done", done)
                    .put("base64", if (done) "" else java.util.Base64.getEncoder().encodeToString(chunk.copyOf(count)))
            }
        }

        fun cancelDownload(id: String) { downloads.remove(id)?.close() }

        override fun close() {
            if (!alive.compareAndSet(true, false)) return
            subscription.getAndSet(null)?.close()
            commands.destroy()
            downloadBridge.destroy()
            synchronized(events) { events.clear(); eventBytes = 0 }
            cleanupWebUiResources {
                downloads.keys.toList().forEach(::cancelDownload)
                downloadBridge.blobs.cleanup()
                cacheDir.listFiles()?.forEach { it.delete() }
                cacheDir.delete()
            }
        }
    }

    private class BrowserDownload(val input: InputStream, private val onFinished: () -> Unit) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            try { input.close() } finally { onFinished() }
        }
    }
}

private fun browserRootShell(moduleDir: String): Shell {
    val shell = Shell.Builder.create().build("/system/bin/sh")
    try {
        check(shell.isRoot) { "MODULE_BRIDGE_ROOT_REQUIRED" }
        val directory = ShellUtils.escapedString(moduleDir)
        val result = shell.newJob().add("cd -- $directory && export KSU=true KSU_MODULE=$directory MODDIR=$directory").exec()
        check(result.isSuccess) { "MODULE_BRIDGE_SHELL_PREPARE_FAILED" }
        return shell
    } catch (error: Exception) { shell.close(); throw error }
}
