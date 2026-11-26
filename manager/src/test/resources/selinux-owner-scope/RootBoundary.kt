package dev.maplesu.manager.runtime

import dev.maplesu.manager.runtime.ultra.SELinuxBoundary

class HookRootExecutor {
    companion object { fun production(context: Any) = HookRootExecutor() }
    fun available() = SELinuxBoundary.hook
    fun run(command: String): Result {
        SELinuxBoundary.hookCalls++
        return Result(true, "Enforcing")
    }
    data class Result(val ok: Boolean, val output: String)
}

object RootManagement {
    fun openShell(context: Any): Shell {
        SELinuxBoundary.legacyCalls++
        return Shell()
    }
    class Shell : AutoCloseable {
        fun newJob() = Job()
        override fun close() = Unit
    }
    class Job {
        fun add(command: String) = this
        fun to(output: ArrayList<String>, errors: ArrayList<String>) = this
        fun exec() = Result(true, listOf("Enforcing"))
    }
    data class Result(val isSuccess: Boolean, val out: List<String>)
}
