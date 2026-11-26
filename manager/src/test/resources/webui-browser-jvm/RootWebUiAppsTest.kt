package com.maplesu.core.ui.webui

import android.app.AppGlobals
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.junit.Assert.*
import org.junit.Test

class RootWebUiAppsTest {
    class Slice {
        fun getList() = listOf(PackageInfo().also {
            it.packageName = "fixture.package"
            it.applicationInfo = ApplicationInfo().also { app -> app.uid = 1010042; app.nonLocalizedLabel = "Fixture label" }
        })
    }
    class IntFlags {
        var seen: Pair<Int, Int>? = null
        fun getInstalledPackages(flags: Int, user: Int): Slice { seen = flags to user; return Slice() }
    }
    class LongFlags {
        var seen: Pair<Long, Int>? = null
        fun getInstalledPackages(flags: Long, user: Int): Slice { seen = flags to user; return Slice() }
    }
    @Test fun oldIntFlagsRetainTheExplicitAndroidUser() {
        val manager = IntFlags(); AppGlobals.manager = manager
        val apps = rootWebUiApps(10)
        assertEquals(0 to 10, manager.seen)
        assertEquals("Fixture label", apps.single().label)
        assertEquals(1010042, apps.single().uid)
    }
    @Test fun widenedLongFlagsRetainTheExplicitAndroidUser() {
        val manager = LongFlags(); AppGlobals.manager = manager
        assertEquals("fixture.package", rootWebUiApps(10).single().packageName)
        assertEquals(0L to 10, manager.seen)
    }
}
