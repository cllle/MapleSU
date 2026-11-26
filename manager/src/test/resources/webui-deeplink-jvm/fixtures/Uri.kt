package android.net

import java.net.URLDecoder
import java.net.URLEncoder

class Uri(private val value: String) {
    private val parsed = java.net.URI(value)
    val scheme: String? get() = parsed.scheme
    val host: String? get() = parsed.host
    val pathSegments: List<String> get() = parsed.path.orEmpty().split('/').filter { it.isNotEmpty() }
    val lastPathSegment: String? get() = parsed.path?.substringAfterLast('/')
    fun getQueryParameter(name: String): String? = parsed.rawQuery?.split('&')?.map {
        it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
    }?.firstOrNull { it.first == name }?.second
    override fun toString(): String = value
    class Builder {
        private var scheme = ""
        private var host = ""
        private val args = mutableListOf<Pair<String, String>>()
        fun scheme(value: String): Builder = apply { scheme = value }
        fun authority(value: String): Builder = apply { host = value }
        fun appendQueryParameter(key: String, value: String): Builder = apply { args += key to value }
        fun build(): Uri = Uri("$scheme://$host?" + args.joinToString("&") {
            it.first + "=" + URLEncoder.encode(it.second, "UTF-8")
        })
    }
}
