package com.maplesu.core.ui.util

import android.content.Context
import android.net.Uri

fun Uri.getFileName(context: Context): String? = null
object DownloadService {
    const val ACTION_INSTALL_MODULE = "install"
    const val EXTRA_TOKEN = "token"
    const val EXTRA_MODULE_URI = "uri"
}
