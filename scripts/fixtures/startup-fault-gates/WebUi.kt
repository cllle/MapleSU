package dev.maplesu.manager.modules.webui
object ModuleWebUiSessions {
    data class OwnerScope(val fullUid: Int, val epoch: Long, val hostInstance: String)
    fun observedOwner(scope: OwnerScope) {}
}
