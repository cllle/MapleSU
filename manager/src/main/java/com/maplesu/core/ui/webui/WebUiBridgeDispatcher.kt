package com.maplesu.core.ui.webui

import android.webkit.JavascriptInterface
import com.maplesu.core.ui.webui.file.FileInputStreamInterface
import com.maplesu.core.ui.webui.file.FileInterface
import com.maplesu.core.ui.webui.file.FileOutputStreamInterface
import com.maplesu.core.ui.webui.file.KsuIO
import com.maplesu.core.ui.webui.file.RandomAccessFileInterface
import com.maplesu.core.ui.webui.pm.KsuPm
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** The annotated KSU object surface shared by document-start and browser transports. */
internal class WebUiBridgeDispatcher(commands: WebViewInterface, downloads: WebUIDownloadInterface) {
    private val roots = mapOf("ksu" to commands, "ksu_download" to downloads)

    fun invoke(request: String?): String {
        return try {
            val call = JSONObject(requireNotNull(request))
            var receiver: Any = roots[call.getString("root")] ?: error("Unknown WebUI bridge")
            val path = call.getJSONArray("path")
            for (index in 0 until path.length()) {
                val step = path.getJSONArray(index)
                val args = step.getJSONArray(1)
                val factory = method(receiver, step.getString(0), args.length())
                // These getters/factories construct wrappers only. Replaying them
                // avoids retaining an unbounded map of Java object handles.
                check(factory.returnType in objectTypes) { "Invalid WebUI object path" }
                receiver = requireNotNull(invokeMethod(receiver, factory, args))
            }
            val args = call.getJSONArray("args")
            val target = method(receiver, call.getString("method"), args.length())
            val value = invokeMethod(receiver, target, args)
            JSONObject().apply {
                when {
                    target.returnType == Void.TYPE -> put("kind", "void")
                    target.returnType in objectTypes -> {
                        put("kind", "object")
                        put("type", objectTypes.indexOf(target.returnType))
                    }
                    value is Number -> { put("kind", "number"); put("value", value.toString()) }
                    else -> { put("kind", "value"); put("value", value ?: JSONObject.NULL) }
                }
            }.toString()
        } catch (error: Exception) {
            val cause = (error as? InvocationTargetException)?.targetException ?: error
            failure(cause.message ?: cause.javaClass.simpleName)
        }
    }

    private fun method(receiver: Any, name: String, count: Int): Method =
        requireNotNull(methods[receiver.javaClass]?.get(name to count)) { "Unknown WebUI method" }

    private fun invokeMethod(receiver: Any, method: Method, args: JSONArray): Any? =
        method.invoke(receiver, *method.parameterTypes.mapIndexed { index, type ->
            val value = args.getJSONArray(index)
            when (type) {
                String::class.java -> when (value.getString(0)) {
                    "null", "object" -> null
                    "undefined" -> "undefined"
                    else -> value.get(1).toString()
                }
                Boolean::class.javaPrimitiveType -> value.optBoolean(1, false).takeIf { value.getString(0) == "boolean" } ?: false
                else -> {
                    val number = if (value.getString(0) == "number") value.getString(1).toDouble() else 0.0
                    when (type) {
                        Int::class.javaPrimitiveType -> number.toInt()
                        Long::class.javaPrimitiveType -> number.toLong()
                        Float::class.javaPrimitiveType -> number.toFloat()
                        Double::class.javaPrimitiveType -> number
                        else -> error("Unsupported WebUI argument type")
                    }
                }
            }
        }.toTypedArray<Any?>())

    companion object {
        private val objectTypes = listOf(WebViewInterface::class.java, WebUIDownloadInterface::class.java,
            KsuIO::class.java, FileInterface::class.java, FileInputStreamInterface::class.java,
            FileOutputStreamInterface::class.java, RandomAccessFileInterface::class.java, KsuPm::class.java)
        private val methods = objectTypes.associateWith { type ->
            type.methods.filter { it.isAnnotationPresent(JavascriptInterface::class.java) }
                .associateBy { it.name to it.parameterTypes.size }
        }

        fun schema(): JSONArray {
            return JSONArray(objectTypes.map { type ->
                JSONObject().apply {
                    methods.getValue(type).keys.forEach { (name, count) ->
                        put(name, (optJSONArray(name) ?: JSONArray().also { put(name, it) }).put(count))
                    }
                }
            })
        }

        fun failure(message: String): String =
            JSONObject().put("kind", "error").put("message", message).toString()
    }
}
