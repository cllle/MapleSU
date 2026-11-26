package dev.maplesu.manager.runtime

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.maplesu.core.BuildConfig
import com.maplesu.core.R
import com.maplesu.core.ui.MainActivity
import com.maplesu.core.ui.viewmodel.HomeViewModel
import com.maplesu.core.ui.viewmodel.ModuleViewModel
import dev.maplesu.manager.workmode.ModeRuntime
import dev.maplesu.manager.workmode.detect.SuReadiness
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in activity-only measurements, not process-cold or complete SDK/SU/module readiness. */
class ManagerLatencyAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()

    @Test fun coldHomePublishesLiveModeVersionAndSelinux() {
        assumeTrue(args.getString("verifyHomeStartup") == "true")
        val expectedVersion = checkNotNull(args.getString("expectedEnvironmentVersion")) {
            "Bind expectedEnvironmentVersion to the selected environment artifact"
        }.also { require(it.isNotBlank()) }
        val installedVersion = instrumentation.targetContext.packageManager.getPackageInfo(
            instrumentation.targetContext.packageName, 0).versionName
        assertEquals("Target APK version differs from compiled test target", BuildConfig.VERSION_NAME, installedVersion)
        val started = SystemClock.elapsedRealtime()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var modeAt = 0L
            var versionAt = 0L
            var selinuxAt = 0L
            lateinit var home: HomeViewModel
            scenario.onActivity { home = models(it).filterIsInstance<HomeViewModel>().single() }
            waitUntil(started + 7_500) {
                val at = SystemClock.elapsedRealtime() - started
                if (modeAt == 0L && ModeRuntime.active != null) modeAt = at
                if (versionAt == 0L && visibleVersion(expectedVersion)) versionAt = at
                if (selinuxAt == 0L && home.uiState.value.systemInfo.selinuxStatus in
                    listOf("Enforcing", "Permissive", "Disabled")) selinuxAt = at
                val state = home.uiState.value
                modeAt > 0 && versionAt > 0 && selinuxAt > 0 && ModeRuntime.detectionComplete &&
                    ModeRuntime.detectionFailure == null && !state.environmentChecking &&
                    state.environmentFailure == null && state.showInstalledStatus &&
                    !state.hiddenConfirming && !state.litePending && !state.isSafeMode &&
                    state.isRootAvailable && state.rootReadiness == SuReadiness.USABLE
            }
            val publishedAt = SystemClock.elapsedRealtime() - started
            assertTrue("Home publication exceeded its activity-only budget: ${publishedAt}ms", publishedAt < 7_500)
            assertEquals("LITE", ModeRuntime.active?.name)
            assertFalse(home.uiState.value.environmentChecking)
            assertNull(home.uiState.value.environmentFailure)
            assertTrue(home.uiState.value.isRootAvailable)
            assertEquals(SuReadiness.USABLE, home.uiState.value.rootReadiness)
            instrumentation.sendStatus(2, Bundle().apply {
                putLong("modeMillis", modeAt)
                putLong("visibleVersionMillis", versionAt)
                putLong("selinuxPublishedMillis", selinuxAt)
                putString("selinux", home.uiState.value.systemInfo.selinuxStatus)
                putString("managerVersion", installedVersion)
                putString("environmentVersion", expectedVersion)
                putString("clockScope", "activity-launch-after-application-create")
                putBoolean("completeReadyQualified", false)
                putBoolean("processColdQualified", false)
                putLong("homePublishedMillis", publishedAt)
            })
        }
    }

    @Test fun ksuRemovalAndImmediateUndoPublishBeforeTheirBackendFinishes() {
        assumeTrue(args.getString("verifyModuleRemovalFixture") == "true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var modules: ModuleViewModel
            scenario.onActivity {
                modules = models(it).filterIsInstance<ModuleViewModel>().single()
                modules.fetchModuleList(checkUpdate = false)
            }
            val id = "zz_maplesu_removal_qa"
            waitUntil(SystemClock.elapsedRealtime() + 10_000) {
                modules.uiState.value.modules.any { it.id == id } && !modules.uiState.value.isRefreshing
            }
            var markMillis = 0L
            var undoMillis = 0L
            scenario.onActivity {
                val fixture = modules.uiState.value.modules.single { row -> row.id == id }
                assertFalse(fixture.enabled)
                assertFalse(fixture.remove)
                var at = SystemClock.elapsedRealtime()
                modules.uninstallModule(fixture)
                markMillis = SystemClock.elapsedRealtime() - at
                assertTrue(modules.uiState.value.modules.single { row -> row.id == id }.remove)
                assertTrue(id in modules.uiState.value.removalChanges)
                at = SystemClock.elapsedRealtime()
                modules.undoUninstallModule(fixture)
                undoMillis = SystemClock.elapsedRealtime() - at
                assertFalse(modules.uiState.value.modules.single { row -> row.id == id }.remove)
            }
            waitUntil(SystemClock.elapsedRealtime() + 10_000) {
                id !in modules.uiState.value.pendingModuleIds && id !in modules.uiState.value.removalChanges
            }
            assertFalse(modules.uiState.value.modules.single { it.id == id }.remove)
            assertTrue("Mark must publish synchronously", markMillis < 100)
            assertTrue("Undo must publish synchronously", undoMillis < 100)
            instrumentation.sendStatus(2, Bundle().apply {
                putLong("markPublishedMillis", markMillis)
                putLong("undoPublishedMillis", undoMillis)
            })
        }
    }

    private fun models(activity: MainActivity): List<Any> {
        val result = mutableListOf<Any>()
        val stores = ArrayDeque<ViewModelStore>().apply { add(activity.viewModelStore) }
        while (stores.isNotEmpty()) {
            val store = stores.removeFirst()
            for (key in store.keys()) {
                val model = store[key] ?: continue
                result += model
                if (model.javaClass.name.endsWith("NavEntryViewModelStores")) {
                    val field = model.javaClass.getDeclaredField("stores").apply { isAccessible = true }
                    (field.get(model) as Map<*, *>).values.filterIsInstance<ViewModelStore>().forEach(stores::add)
                }
            }
        }
        return result
    }

    private fun visibleVersion(expectedVersion: String): Boolean {
        val label = instrumentation.targetContext.getString(R.string.work_mode_lite) + " " + expectedVersion
        val expected = instrumentation.targetContext.getString(R.string.home_working_version, label)
        val nodes = ArrayDeque<AccessibilityNodeInfo>()
        instrumentation.uiAutomation.rootInActiveWindow?.let(nodes::add)
        while (nodes.isNotEmpty()) {
            val node = nodes.removeFirst()
            if (node.isVisibleToUser && node.text?.toString() == expected) return true
            repeat(node.childCount) { node.getChild(it)?.let(nodes::add) }
        }
        return false
    }

    private fun waitUntil(deadline: Long, condition: () -> Boolean) {
        while (!condition()) {
            assertTrue("Startup condition exceeded its budget", SystemClock.elapsedRealtime() < deadline)
            SystemClock.sleep(20)
        }
    }
}
