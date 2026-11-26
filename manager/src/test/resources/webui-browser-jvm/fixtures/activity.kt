package android.app

class Activity : android.content.Context() {
    val window = android.view.Window()
    val queued = ArrayList<Runnable>()
    fun runOnUiThread(action: Runnable) { queued += action }
    fun drain() { val pending = queued.toList(); queued.clear(); pending.forEach { it.run() } }
}
