package android.app

/** Explicit binder boundary; no Context or Resources is created by this fixture. */
object AppGlobals {
    var manager: Any = Any()
    @JvmStatic fun getPackageManager(): Any = manager
}
