package com.maplesu.core.ui.navigation3

import com.maplesu.core.ui.screen.flash.FlashIt

sealed interface Route {
    data object Wallpaper : Route
    data class Flash(val operation: FlashIt) : Route
    data class ExecuteModuleAction(val id: String, val fromShortcut: Boolean) : Route
}
class Navigator {
    val backStack = mutableListOf<Route>()
    fun push(route: Route) { backStack += route }
    fun popUntil(predicate: (Route) -> Boolean) { while (backStack.isNotEmpty() && !predicate(backStack.last())) backStack.removeLast() }
}
object LocalNavigator { var current = Navigator() }
