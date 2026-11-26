package com.maplesu.core

import android.content.Context

val ksuApp = Context()
object Natives { var isManager = true; var isSafeMode = false }
object R {
    object string {
        const val theme_single_file = 1
        const val safe_mode_module_disabled = 2
        const val module = 3
        const val module_install_prompt_with_name = 4
        const val module_webui_open_failed = 5
    }
}
