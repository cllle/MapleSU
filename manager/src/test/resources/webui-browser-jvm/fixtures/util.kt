package com.maplesu.core.ui.util
import com.topjohnwu.superuser.Shell
var fixtureShellFactory:()->Shell={throw IllegalStateException("shell-open-failed")}
fun createRootShell(globalMnt:Boolean=false)=fixtureShellFactory()
inline fun <T> withNewRootShell(globalMnt:Boolean=false,block:Shell.()->T)=createRootShell(globalMnt).use(block)
var fixtureModuleList:()->String={"[]"}
fun listModules()=fixtureModuleList()
