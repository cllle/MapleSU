package android.widget

class Toast {
    fun show() { shown++ }
    companion object {
        const val LENGTH_SHORT = 0
        var shown = 0
        fun makeText(context: Any, text: String, duration: Int) = Toast()
    }
}
