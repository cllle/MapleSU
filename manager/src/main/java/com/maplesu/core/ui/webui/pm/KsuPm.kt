package com.maplesu.core.ui.webui.pm

import android.content.pm.ApplicationInfo
import android.webkit.JavascriptInterface
import androidx.core.content.pm.PackageInfoCompat
import com.maplesu.core.data.model.AppInfo
import com.maplesu.core.ui.webui.webUIApps
import org.json.JSONArray
import org.json.JSONObject

class KsuPm internal constructor(
    private val pageCurrent: () -> Boolean,
    private val apps: () -> List<AppInfo> = ::webUIApps,
) {
    private companion object {
        const val KSU_PM_VERSION = 1
    }

    private fun requireCurrentPage() {
        check(pageCurrent()) { "WebUI page closed" }
    }

    @JavascriptInterface
    fun version(): Int {
        requireCurrentPage()
        return KSU_PM_VERSION
    }

    @JavascriptInterface
    fun listPackages(type: String): String {
        requireCurrentPage()
        val packageNames = apps()
            .filter { appInfo ->
                if (appInfo.special) return@filter false
                val flags = appInfo.packageInfo.applicationInfo?.flags ?: 0
                when (type.lowercase()) {
                    "system" -> (flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    "user" -> (flags and ApplicationInfo.FLAG_SYSTEM) == 0
                    else -> true
                }
            }
            .map { it.packageName }
            .distinct()
            .sorted()

        val jsonArray = JSONArray()
        for (pkgName in packageNames) {
            jsonArray.put(pkgName)
        }
        val result = jsonArray.toString()
        requireCurrentPage()
        return result
    }

    @JavascriptInterface
    fun getPackagesInfo(packageNamesJson: String): String {
        requireCurrentPage()
        val packageNames = JSONArray(packageNamesJson)
        val appsByPackage = apps()
            .filterNot { it.special }
            .groupBy { it.packageName }
        val jsonArray = JSONArray()
        for (i in 0 until packageNames.length()) {
            val pkgName = packageNames.getString(i)
            val matches = appsByPackage[pkgName]
            if (matches.isNullOrEmpty()) {
                val error = JSONObject()
                error.put("packageName", pkgName)
                error.put("error", "Package not found or inaccessible")
                jsonArray.put(error)
                continue
            }
            for (appInfo in matches.sortedBy { it.uid }) {
                jsonArray.put(buildInfo(appInfo))
            }
        }
        val result = jsonArray.toString()
        requireCurrentPage()
        return result
    }

    private fun buildInfo(appInfo: AppInfo): JSONObject {
        val pkg = appInfo.packageInfo
        val app = pkg.applicationInfo
        return JSONObject().apply {
            put("packageName", pkg.packageName)
            put("versionName", pkg.versionName ?: "")
            put("versionCode", PackageInfoCompat.getLongVersionCode(pkg))
            put("appLabel", appInfo.label)
            put("isSystem", app?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: JSONObject.NULL)
            put("uid", app?.uid ?: JSONObject.NULL)
            put("userId", app?.let { it.uid / 100000 } ?: JSONObject.NULL)
        }
    }
}
