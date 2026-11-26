package dev.maplesu.manager.modules

import android.content.Context
import com.maplesu.core.ui.navigation3.WebUiOpenOrigin
import dev.maplesu.manager.modules.webui.ModuleWebUiPlan
import dev.maplesu.manager.modules.webui.WebUiFailure

object ModuleEnvironmentWatch {
    data class Facts(val waitReason: String?)
    var waitReason: String? = null
    var ready = true
    fun facts(): Facts = Facts(waitReason)
    suspend fun awaitReady(reason: String, timeout: Long): Boolean = ready
}
object ModuleWebUiLauncher {
    data class Call(val context: Context, val moduleId: String, val origin: WebUiOpenOrigin)
    val calls = mutableListOf<Call>()
    var failure: WebUiFailure? = null
    fun isUltraModuleId(moduleId: String): Boolean = ModuleWebUiPlan.isUltraModuleId(moduleId)
    suspend fun openDetailed(context: Context, moduleId: String, origin: WebUiOpenOrigin): WebUiFailure? {
        calls += Call(context, moduleId, origin)
        return failure
    }
}
