package com.maplesu.core.ui.webui

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import dev.maplesu.manager.workmode.ModeRuntime
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference

/** Synchronous legacy API for providers with document-start scripts but no restricted builder. */
internal class HttpWebUiBridge(
    private val state: WebUIState,
    private val view: WebView,
    commands: WebViewInterface,
    downloads: WebUIDownloadInterface,
) : AutoCloseable {
    private val dispatcher = WebUiBridgeDispatcher(commands, downloads)
    private val activation = ModeRuntime.activation
    private val token = AtomicReference(SecureRandom().generateSeed(32).joinToString("") { "%02x".format(it) })
    private var script: ScriptHandler? = null

    fun install(origin: ModuleWebOrigin) {
        view.addJavascriptInterface(this, INTERFACE)
        val installed = WebViewCompat.addDocumentStartJavaScript(view, documentScript(), setOf(origin.value))
        synchronized(this) {
            if (token.get() != null) script = installed else {
                installed.remove()
                view.removeJavascriptInterface(INTERFACE)
            }
        }
    }

    override fun close() {
        if (token.getAndSet(null) == null) return
        val installed = synchronized(this) { script.also { script = null } }
        Handler(Looper.getMainLooper()).post {
            installed?.remove()
            view.removeJavascriptInterface(INTERFACE)
        }
    }

    @JavascriptInterface
    fun invoke(proof: String?, request: String?): String {
        // The native object is visible to every frame. Only the exact-origin
        // document-start closure receives this page's unguessable capability.
        if (!acceptsCall(proof))
            return failure("WebUI page closed or origin denied")
        val response = dispatcher.invoke(request)
        return if (acceptsCall(proof)) response else failure("WebUI page closed or origin denied")
    }

    private fun acceptsCall(proof: String?): Boolean =
        proof != null && proof == token.get() && state.acceptsPage(view) && activation === ModeRuntime.activation

    internal fun documentScript(): String {
        val schema = WebUiBridgeDispatcher.schema()
        return """(function(){'use strict';
            var proof=${JSONObject.quote(requireNotNull(token.get()))},native=window.$INTERFACE;
            var schema=$schema;
            function encode(value){
                if(value===null)return ['null'];
                var type=typeof value;
                if(type==='number')return [type,String(value)];
                if(type==='string'||type==='boolean')return [type,value];
                return [type==='undefined'?'undefined':'object'];
            }
            function object(root,path,type){
                var result={},singletons={};
                Object.keys(schema[type]).forEach(function(name){
                    result[name]=function(){
                        var args=Array.prototype.map.call(arguments,encode);
                        var reply=JSON.parse(native.invoke(proof,JSON.stringify({root:root,path:path,method:name,args:args})));
                        if(reply.kind==='error')throw new Error(reply.message);
                        if(reply.kind==='void')return;
                        if(reply.kind==='number')return Number(reply.value);
                        if(reply.kind==='object'){
                            var next=path.concat([[name,args]]);
                            if(reply.type===$FILE_TYPE)return object(root,next,reply.type);
                            return singletons[name]||(singletons[name]=object(root,next,reply.type));
                        }
                        return reply.value;
                    };
                });
                return result;
            }
            window.ksu=object('ksu',[],$COMMAND_TYPE);
            window.ksu_download=object('ksu_download',[],$DOWNLOAD_TYPE);
        })();""".trimIndent()
    }

    private fun failure(message: String) = JSONObject().put("kind", "error").put("message", message).toString()

    companion object {
        internal const val INTERFACE = "__maplesu_http_bridge"
        private const val COMMAND_TYPE = 0
        private const val DOWNLOAD_TYPE = 1
        private const val FILE_TYPE = 3
    }
}
