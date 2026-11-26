package com.maplesu.core.ui.webui

import android.content.pm.PackageInfo
import com.maplesu.core.data.model.AppInfo

/** Context-free package facts for app_process; never constructs Resources or a synthetic WebView. */
internal fun rootWebUiApps(userId: Int = 0): List<AppInfo> {
    fun lookup() = runCatching {
        Class.forName("android.app.AppGlobals").getDeclaredMethod("getPackageManager").invoke(null)
    }.getOrNull()
    val manager = lookup() ?: run {
        val bypass = Class.forName("org.lsposed.hiddenapibypass.HiddenApiBypass")
        bypass.getDeclaredMethod("addHiddenApiExemptions", Array<String>::class.java)
            .invoke(null, arrayOf("Landroid/app/AppGlobals;", "Landroid/content/pm/IPackageManager;"))
        requireNotNull(lookup()) { "MODULE_PM_UNAVAILABLE" }
    }
    val method = manager.javaClass.methods.first { it.name == "getInstalledPackages" && it.parameterTypes.size == 2 &&
        it.parameterTypes[1] == Int::class.javaPrimitiveType }
    val flags: Any = if (method.parameterTypes[0] == Long::class.javaPrimitiveType) 0L else 0
    val result = requireNotNull(method.invoke(manager, flags, userId))
    val packages = result.javaClass.getMethod("getList").invoke(result) as? List<*> ?: error("MODULE_PM_LIST_INVALID")
    return packages.map { value ->
        val info = value as? PackageInfo ?: error("MODULE_PM_INFO_INVALID")
        AppInfo(info.applicationInfo?.nonLocalizedLabel?.toString() ?: info.packageName, info, null)
    }
}
