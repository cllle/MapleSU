package dev.maplesu.manager.workmode
enum class WorkMode {KSU,LITE};object ModeRuntime {@Volatile var active=WorkMode.KSU;@Volatile var activation=Any()}
