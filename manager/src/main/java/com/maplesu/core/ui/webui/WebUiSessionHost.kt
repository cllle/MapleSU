package com.maplesu.core.ui.webui

import com.maplesu.core.data.model.AppInfo
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/** A real browser document owns the same command, file and download implementations. */
internal interface WebUiSessionHost {
    val moduleId: String
    val moduleDir: String
    val moduleInfoJson: String
    fun isCurrent(): Boolean
    fun apps(): List<AppInfo>
    fun emit(event: JSONObject)
}

internal interface WebUiDownloadSession {
    val cacheDir: File
    fun isCurrent(): Boolean
    fun openExternal(url: String)
    fun save(fileName: String?, mimeType: String?, onFinished: () -> Unit, source: () -> InputStream): Boolean
}
