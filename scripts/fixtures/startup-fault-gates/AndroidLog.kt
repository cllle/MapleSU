package android.util

import dev.maplesu.manager.runtime.ultra.Fixture

object Log {
    fun i(tag: String, message: String): Int { Fixture.logs += message; return 0 }
    fun w(tag: String, message: String): Int { Fixture.logs += message; return 0 }
    fun w(tag: String, message: String, error: Throwable): Int { Fixture.logs += message; return 0 }
}
