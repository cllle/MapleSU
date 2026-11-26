package android.os

object SystemClock {
    var now = 0L
    fun elapsedRealtime() = now
}
class Looper private constructor() {
    companion object {
        private val main = Looper()
        fun myLooper(): Looper? = null
        fun getMainLooper(): Looper = main
    }
}
object Process { fun myUid() = 10123 }
