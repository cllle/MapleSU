package android.content

import android.net.Uri

open class Context {
    val activities = mutableListOf<Intent>()
    open fun startActivity(intent: Intent) { activities += intent }
    fun getString(id: Int, vararg args: Any): String = "$id:${args.joinToString()}"
}

class Intent() {
    constructor(context: Context, target: Class<*>) : this() { this.target = target }
    var target: Class<*>? = null
    var action: String? = null
    var data: Uri? = null
    var type: String? = null
    var `package`: String? = null
    private val extras = mutableMapOf<String, String>()
    fun getStringExtra(name: String): String? = extras[name]
    fun putExtra(name: String, value: String): Intent = apply { extras[name] = value }
    fun setData(value: Uri): Intent = apply { data = value }
    companion object {
        const val URI_INTENT_SCHEME = 1
        const val ACTION_SEND = "send"
        const val ACTION_SEND_MULTIPLE = "send_multiple"
        const val EXTRA_STREAM = "stream"
        // Browser/Android handoff boundary only; production dispatcher is compiled unchanged.
        fun parseUri(value: String, flags: Int): Intent {
            if (!value.startsWith("intent://")) return Intent().setData(Uri(value))
            require(value.endsWith(";end"))
            val fields = value.substringAfter("#Intent;").split(';').associate {
                it.substringBefore('=') to it.substringAfter('=', "")
            }
            return Intent().apply {
                `package` = fields["package"]
                data = Uri(fields.getValue("scheme") + ":" + value.substringAfter("intent:").substringBefore('#'))
            }
        }
    }
}
