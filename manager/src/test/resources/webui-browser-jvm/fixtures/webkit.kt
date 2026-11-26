package android.webkit

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.InputStream

annotation class JavascriptInterface
class WebView(val context: Context = Context()) {
    var parent: Any? = null
    fun destroy() {}
    fun addJavascriptInterface(value: Any, name: String) {}
    val queued = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
    val urls = java.util.concurrent.CopyOnWriteArrayList<String>()
    fun post(run: Runnable): Boolean { queued.add(run); return true }
    fun loadUrl(url: String) { urls.add(url) }
    fun drain() { while (true) (queued.poll() ?: break).run() }
}
open class JsResult {
    var confirmed = 0
    var cancelled = 0
    open fun confirm() { confirmed++ }
    open fun cancel() { cancelled++ }
}
class JsPromptResult : JsResult() { fun confirm(value: String) { confirm() } }
fun interface ValueCallback<T> { fun onReceiveValue(value: T?) }
open class WebChromeClient {
    open fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean = false
    open fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean = false
    open fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean = false
    open fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean = false
    open class FileChooserParams {
        open val mode = 0
        open fun createIntent() = Intent()
        companion object { const val MODE_OPEN_MULTIPLE = 1 }
    }
}
interface WebResourceRequest { val url: Uri }
class WebResourceResponse(val mimeType: String?, val encoding: String?, var data: InputStream?) {
    var statusCode = 200
    var responseHeaders: Map<String, String> = emptyMap()
    constructor(mimeType: String?, encoding: String?, statusCode: Int, reason: String, headers: Map<String, String>, data: InputStream?) : this(mimeType, encoding, data) {
        this.statusCode = statusCode; responseHeaders = headers
    }
}
