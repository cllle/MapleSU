package com.maplesu.core.ui.webui

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Window
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.Shell
import com.maplesu.core.ui.util.createRootShell
import com.maplesu.core.ui.util.listModules
import com.maplesu.core.ui.webui.file.KsuIO
import com.maplesu.core.ui.webui.pm.KsuPm
import dev.maplesu.manager.workmode.ModeRuntime
import dev.maplesu.manager.workmode.WorkMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.Future
import java.util.concurrent.ExecutionException
import java.util.concurrent.CompletionException

class WebViewInterface private constructor(
    private val state: WebUIState?,
    private val session: WebUiSessionHost?,
    private val shellFactory: () -> Shell,
) {
    constructor(state: WebUIState) : this(state, null, { createRootShell(true) })
    internal constructor(state: WebUIState, shellFactory: () -> Shell) : this(state, null, shellFactory)
    internal constructor(session: WebUiSessionHost, shellFactory: () -> Shell) : this(null, session, shellFactory)
    private val webView get() = state?.webView
    private val modDir get() = session?.moduleDir ?: requireNotNull(state).modDir
    private val callbacks = WebUiCallbackScripts(ModeRuntime.active == WorkMode.KSU)
    private val fileIo = KsuIO(shellFactory, ::pageOpen)
    private val packageManager = KsuPm(::pageOpen, ::apps)
    private val lifecycleLock = Any()
    private var destroyed = false
    private var commandView = state?.webView
    private val moduleInfoId = session?.moduleId ?: state?.moduleId
    private val moduleInfoActivation = ModeRuntime.activation
    private var moduleInfoSeed = state?.moduleInfoJson.also { state?.moduleInfoJson = null }
    private val commands = mutableSetOf<Command>()
    private val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private class Command(val shell: Shell) {
        var future: Future<*>? = null
        var closed = false
    }

    private fun pageOpen(): Boolean = synchronized(lifecycleLock) {
        if (destroyed || moduleInfoActivation !== ModeRuntime.activation) return@synchronized false
        if (session != null) return@synchronized session.isCurrent()
        if (state?.isModuleSessionCurrent != true) return@synchronized false
        if (commandView == null) commandView = webView
        webView === commandView
    }

    private fun openCommand(): Command? {
        if (!pageOpen()) return null
        val command = Command(shellFactory())
        val accepted = synchronized(lifecycleLock) {
            if (pageOpen()) commands.add(command) else false
        }
        if (!accepted) runCatching { command.shell.close() }
        return command.takeIf { accepted }
    }

    private fun retainFuture(command: Command, future: Future<*>): Boolean {
        val accepted = synchronized(lifecycleLock) {
            if (!pageOpen() || command.closed) false else {
                command.future = future
                true
            }
        }
        if (!accepted) runCatching { future.cancel(true) }
        return accepted
    }

    private fun finishCommand(command: Command, cancel: Boolean = false) {
        val close = synchronized(lifecycleLock) {
            if (command.closed) false else {
                command.closed = true
                commands.remove(command)
                true
            }
        }
        if (close) {
            if (cancel) runCatching { command.future?.cancel(true) }
            runCatching { command.shell.close() }
        }
    }

    private fun failureMessage(error: Throwable): String {
        var cause = error
        while ((cause is ExecutionException || cause is CompletionException) && cause.cause != null)
            cause = cause.cause!!
        return cause.message ?: cause.javaClass.simpleName
    }

    private fun postJavascript(code: String) {
        val target = synchronized(lifecycleLock) {
            if (!pageOpen()) null else commandView
        } ?: return
        target.post {
            synchronized(lifecycleLock) {
                if (pageOpen() && webView === target) target.loadUrl(code)
            }
        }
    }

    @JavascriptInterface
    fun io(): KsuIO {
        if (!pageOpen()) fileIo.destroy()
        return fileIo
    }

    @JavascriptInterface
    fun pm() = packageManager

    fun destroy() {
        val pending = synchronized(lifecycleLock) {
            if (destroyed) return
            destroyed = true
            moduleInfoSeed = null
            commands.toList().also { commands.clear() }
        }
        // Closing/cancelling can complete a callback synchronously. Keep it
        // outside the page lock so completion and teardown never wait on each other.
        commandScope.cancel()
        fileIo.destroy()
        cleanupWebUiResources {
            pending.forEach { command ->
                finishCommand(command, cancel = true)
            }
        }
    }

    @JavascriptInterface
    fun exec(cmd: String): String = executeSynchronous(cmd, null)

    internal fun executeSynchronous(cmd: String, options: String?): String {
        return try {
            require('\u0000' !in cmd) { "Invalid command" }
            val finalCommand = StringBuilder().also { processOptions(it, options) }.append(cmd).toString()
            val command = openCommand() ?: return ""
            try { ShellUtils.fastCmd(command.shell, finalCommand).takeIf { pageOpen() } ?: "" }
            finally { finishCommand(command) }
        } catch (error: Exception) {
            // The legacy overload exposes stdout only. Keep that contract and
            // retain the failure in diagnostics; callback exec carries code/stderr.
            Log.w("WebViewInterface", "Synchronous exec failed", error)
            ""
        }
    }

    @JavascriptInterface
    fun exec(cmd: String, callbackFunc: String) {
        exec(cmd, null, callbackFunc)
    }

    private fun processOptions(sb: StringBuilder, options: String?) {
        // JS 传进来的 options 可能是畸形 JSON：解析失败就用空对象（等于没有 options），
        // 不让异常穿过 @JavascriptInterface 边界把进程带崩。
        val opts = if (options == null) JSONObject() else {
            runCatching { JSONObject(options) }.getOrElse { JSONObject() }
        }

        val cwd = opts.optString("cwd")
        if (!TextUtils.isEmpty(cwd)) {
            require('\u0000' !in cwd) { "Invalid working directory" }
            val directory = if (cwd.startsWith('-')) "./$cwd" else cwd
            sb.append("cd -- ${ShellUtils.escapedString(directory)} || exit $?; ")
        }

        opts.optJSONObject("env")?.let { env ->
            env.keys().forEach { key ->
                require(key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Invalid environment name" }
                // ⚠️ 2026-09-19 修（G2）：`env.getString(key)` 在值是 JSON null
                // （JS 侧写 `{env: {FOO: null}}`）时会抛 JSONException，异常会穿过
                // @JavascriptInterface 边界。改成先判 null：拿不到就当空字符串。
                val value = if (env.isNull(key)) "" else env.optString(key)
                require('\u0000' !in value) { "Invalid environment value" }
                sb.append("export ${key}=${ShellUtils.escapedString(value)} || exit $?; ")
            }
        }
    }

    @JavascriptInterface
    fun exec(
        cmd: String,
        options: String?,
        callbackFunc: String
    ) {
        if (!pageOpen()) return
        val finalCommand = try {
            require('\u0000' !in cmd) { "Invalid command" }
            StringBuilder().also { processOptions(it, options) }.append(cmd).toString()
        } catch (exception: IllegalArgumentException) {
            postExecResult(callbackFunc, 64, "", exception.message ?: "Invalid WebUI options")
            return
        }

        try {
            commandScope.launch {
                runInterruptible {
                    val owned = try { openCommand() } catch (error: Exception) {
                        postExecResult(callbackFunc, -1, "", failureMessage(error))
                        return@runInterruptible
                    } ?: return@runInterruptible
                    try {
                        val future = owned.shell.newJob().add(finalCommand).to(ArrayList(), ArrayList()).enqueue()
                        if (!retainFuture(owned, future)) return@runInterruptible
                        val result = future.get()
                        postExecResult(callbackFunc, result.code, result.out.joinToString("\n"), result.err.joinToString("\n"))
                    } catch (error: Exception) {
                        finishCommand(owned, cancel = true)
                        postExecResult(callbackFunc, -1, "", failureMessage(error))
                    } finally { finishCommand(owned) }
                }
            }
        } catch (error: Exception) {
            postExecResult(callbackFunc, -1, "", failureMessage(error))
        }
    }

    private fun postExecResult(callbackFunc: String, code: Int, stdout: String, stderr: String) {
        if (session != null) {
            if (pageOpen()) session.emit(JSONObject().put("kind", "exec").put("callback", callbackFunc)
                .put("code", code).put("stdout", stdout).put("stderr", stderr))
            return
        }
        callbacks.exec(callbackFunc, code, stdout, stderr)?.let(::postJavascript)
    }

    @JavascriptInterface
    fun spawn(command: String, args: String, options: String?, callbackFunc: String) {
        if (!pageOpen()) return
        val finalCommand = try {
            buildSpawnCommand(command, args, options)
        } catch (exception: IllegalArgumentException) {
            postSpawnResult(callbackFunc, 64, exception.message ?: "Invalid WebUI options")
            return
        } catch (exception: org.json.JSONException) {
            postSpawnResult(callbackFunc, 64, "Invalid spawn arguments")
            return
        }

        val emitData = fun(name: String, data: String) {
            if (session != null) {
                if (pageOpen()) session.emit(JSONObject().put("kind", "data").put("callback", callbackFunc)
                    .put("stream", name).put("value", data))
            } else callbacks.data(callbackFunc, name, data)?.let(::postJavascript)
        }

        val stdout = object : CallbackList<String>({ it.run() }) {
            override fun onAddElement(s: String) {
                emitData("stdout", s)
            }
        }

        val stderr = object : CallbackList<String>({ it.run() }) {
            override fun onAddElement(s: String) {
                emitData("stderr", s)
            }
        }

        try {
            commandScope.launch {
                runInterruptible {
                    val owned = try { openCommand() } catch (error: Exception) {
                        postSpawnResult(callbackFunc, -1, failureMessage(error))
                        return@runInterruptible
                    } ?: return@runInterruptible
                    try {
                        val future = owned.shell.newJob().add(finalCommand).to(stdout, stderr).enqueue()
                        if (!retainFuture(owned, future)) return@runInterruptible
                        val result = future.get()
                        postSpawnResult(callbackFunc, result.code, result.err.joinToString("\n"))
                    } catch (error: Exception) {
                        finishCommand(owned, cancel = true)
                        postSpawnResult(callbackFunc, -1, failureMessage(error))
                    } finally { finishCommand(owned) }
                }
            }
        } catch (error: Exception) {
            postSpawnResult(callbackFunc, -1, failureMessage(error))
        }
    }

    private fun buildSpawnCommand(command: String, args: String, options: String?): String {
        require('\u0000' !in command) { "Invalid command" }
        val result = StringBuilder()
        processOptions(result, options)
        result.append(command)
        val arguments = if (args.isEmpty()) JSONArray() else JSONArray(args)
        for (index in 0 until arguments.length()) {
            val value = arguments.get(index)
            require(value is String && '\u0000' !in value) { "Invalid spawn argument" }
            result.append(' ').append(ShellUtils.escapedString(value))
        }
        return result.toString()
    }

    private fun postSpawnResult(callbackFunc: String, code: Int, error: String) {
        if (session != null) {
            if (pageOpen()) session.emit(JSONObject().put("kind", "exit").put("callback", callbackFunc)
                .put("code", code).put("stderr", error))
            return
        }
        callbacks.result(callbackFunc, code, error)?.let(::postJavascript)
    }

    @JavascriptInterface
    fun toast(msg: String) {
        if (session != null) {
            if (pageOpen()) session.emit(JSONObject().put("kind", "toast").put("message", msg))
            return
        }
        val target = webView?.takeIf { pageOpen() } ?: return
        target.post {
            if (pageOpen() && webView === target) Toast.makeText(target.context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun fullScreen(enable: Boolean) {
        if (!pageOpen()) return
        if (session != null) {
            session.emit(JSONObject().put("kind", "fullScreen").put("enable", enable))
            return
        }
        val context = webView?.context
        if (context is Activity) {
            Handler(Looper.getMainLooper()).post {
                if (!pageOpen()) return@post
                if (enable) {
                    hideSystemUI(context.window)
                } else {
                    showSystemUI(context.window)
                }
            }
        }
        enableEdgeToEdge(enable)
    }

    @JavascriptInterface
    fun enableEdgeToEdge(enable: Boolean = true) {
        if (!pageOpen()) return
        if (session != null) session.emit(JSONObject().put("kind", "edgeToEdge").put("enable", enable))
        else state?.isInsetsEnabled = enable
    }

    @JavascriptInterface
    fun moduleInfo(): String {
        if (!pageOpen()) return "{}"
        if (session != null) return session.moduleInfoJson.takeIf { pageOpen() } ?: "{}"
        val moduleId = state?.moduleId ?: File(modDir).name
        val seed = synchronized(lifecycleLock) {
            val pending = moduleInfoSeed.also { moduleInfoSeed = null }
            if (pageOpen() && state?.moduleId == moduleInfoId && ModeRuntime.activation === moduleInfoActivation) pending else null
        }
        val metadata = seed?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.takeIf { it.optString("id") == moduleId }
            ?: runCatching {
                if (!pageOpen()) return@runCatching null
                val modules = JSONArray(listModules())
                var found: JSONObject? = null
                for (index in 0 until modules.length()) {
                    val info = modules.optJSONObject(index) ?: continue
                    if (info.optString("id") == moduleId) {
                        found = info
                        break
                    }
                }
                found
            }.getOrNull()
        if (!pageOpen()) return "{}"
        return JSONObject().apply {
            metadata?.let { info -> info.keys().forEach { key -> put(key, info.get(key)) } }
            // The actual host supplies the directory; list metadata never changes it.
            put("moduleDir", modDir)
        }.toString()
    }

    private fun apps() = session?.apps() ?: webUIApps()

    @JavascriptInterface
    fun listPackages(type: String): String {
        if (!pageOpen()) return "[]"
        val packageNames = apps()
            .filter { appInfo ->
                if (appInfo.special) return@filter false
                val flags = appInfo.packageInfo.applicationInfo?.flags ?: 0
                when (type.lowercase()) {
                    "system" -> (flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    "user" -> (flags and ApplicationInfo.FLAG_SYSTEM) == 0
                    else -> true
                }
            }
            .map { it.packageName }
            .sorted()

        val jsonArray = JSONArray()
        for (pkgName in packageNames) {
            jsonArray.put(pkgName)
        }
        val result = jsonArray.toString()
        return if (pageOpen()) result else "[]"
    }

    @JavascriptInterface
    fun getPackagesInfo(packageNamesJson: String): String {
        if (!pageOpen()) return "[]"
        // 入参是 JS 拼的字符串，畸形 JSON 不能让异常冲出 JS 桥；失败返回空数组 "[]"
        val packageNames = runCatching { JSONArray(packageNamesJson) }.getOrElse { JSONArray() }
        val jsonArray = JSONArray()
        val appMap = apps().filterNot { it.special }.associateBy { it.packageName }
        for (i in 0 until packageNames.length()) {
            val pkgName = packageNames.getString(i)
            val appInfo = appMap[pkgName]
            if (appInfo != null) {
                val pkg = appInfo.packageInfo
                val app = pkg.applicationInfo
                val obj = JSONObject()
                obj.put("packageName", pkg.packageName)
                obj.put("versionName", pkg.versionName ?: "")
                obj.put("versionCode", PackageInfoCompat.getLongVersionCode(pkg))
                obj.put("appLabel", appInfo.label)
                obj.put("isSystem", if (app != null) ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) else JSONObject.NULL)
                obj.put("uid", app?.uid ?: JSONObject.NULL)
                jsonArray.put(obj)
            } else {
                val obj = JSONObject()
                obj.put("packageName", pkgName)
                obj.put("error", "Package not found or inaccessible")
                jsonArray.put(obj)
            }
        }
        val result = jsonArray.toString()
        return if (pageOpen()) result else "[]"
    }

    @JavascriptInterface
    fun exit() {
        if (session != null) {
            if (pageOpen()) session.emit(JSONObject().put("kind", "exitPage"))
            return
        }
        val target = webView?.takeIf { pageOpen() } ?: return
        target.post {
            if (pageOpen() && webView === target) state?.requestExit()
        }
    }
}

fun hideSystemUI(window: Window) =
    WindowInsetsControllerCompat(window, window.decorView).let { controller ->
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

fun showSystemUI(window: Window) =
    WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
