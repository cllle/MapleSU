package dev.maplesu.manager.compat

import android.content.Context

fun Context.ultraPreferences(name: String, mode: Int) = Preferences()
class Preferences {
    fun edit() = Editor()
    class Editor {
        fun putString(name: String, value: String?) = this
        fun putLong(name: String, value: Long) = this
        fun apply() {}
    }
}
