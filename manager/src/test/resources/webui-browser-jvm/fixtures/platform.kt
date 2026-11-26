package android.content

import android.net.Uri
import java.io.File

open class Context {
    val cacheDir: File = java.nio.file.Files.createTempDirectory("webui-retirement-").toFile()
    val applicationContext: Context get() = this
    val contentResolver: android.content.ContentResolver get() = error("MediaStore is outside this JVM test")
    val externalIntents = mutableListOf<Intent>()
    fun startActivity(intent: Intent) { externalIntents += intent }
    fun getString(id: Int, vararg values: Any): String = "$id:${values.joinToString()}"
    fun <T> getSystemService(type: Class<T>): T = error("System service is outside this JVM test")
}

class Intent(val action: String = "", val uri: Uri? = null) {
    var type: String = ""
    fun putExtra(name: String, value: Boolean): Intent = this
    fun setDataAndType(uri: Uri, type: String): Intent = this
    fun addFlags(flags: Int): Intent = this
    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT"
        const val EXTRA_ALLOW_MULTIPLE = "allow_multiple"
        const val FLAG_GRANT_READ_URI_PERMISSION = 1
        const val FLAG_ACTIVITY_NEW_TASK = 2
        fun createChooser(intent: Intent, title: String): Intent = intent
    }
}
