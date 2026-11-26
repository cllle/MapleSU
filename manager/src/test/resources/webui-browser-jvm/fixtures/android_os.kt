package android.os

class Looper { companion object { fun getMainLooper() = Looper() } }
class Handler(val looper: Looper) {
    fun post(run: Runnable): Boolean { queued += run; return true }
    companion object {
        val queued = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
        fun drain() { while (true) (queued.poll() ?: break).run() }
    }
}
