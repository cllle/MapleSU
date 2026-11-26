package dev.maplesu.manager.runtime
import android.content.Context
import dev.maplesu.manager.runtime.ultra.Fixture
object HookRootExecutor {
    fun production(context: Context) = this
    fun available() = Fixture.hook
}
object RuntimeDeployment {
    fun resumeBootSdk(context: Context) { Fixture.calls += "resume-boot-sdk" }
}
