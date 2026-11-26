package android.content

import java.io.File

open class Context {
    val applicationContext: Context get() = this
    val filesDir = File("fixture-files-not-read")
    companion object { const val MODE_PRIVATE = 0 }
}
