package android.os

object Process {
    @JvmStatic fun myUid() = 10123
}

object SystemClock {
    @Volatile var now = 1_000L
    @JvmStatic fun elapsedRealtime() = now
}
