package androidx.compose.ui.platform

import android.content.Context

class Resources {
    fun getString(id: Int, vararg args: Any): String = "$id:${args.joinToString()}"
}
object LocalContext { var current = Context() }
object LocalResources { val current = Resources() }
