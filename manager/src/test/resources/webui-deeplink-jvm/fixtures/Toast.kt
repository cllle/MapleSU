package android.widget

import android.content.Context

class Toast(private val message: String) {
    fun show() { messages += message }
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        val messages = mutableListOf<String>()
        fun makeText(context: Context, message: String, duration: Int): Toast = Toast(message)
        fun makeText(context: Context, message: Int, duration: Int): Toast = Toast(message.toString())
    }
}
