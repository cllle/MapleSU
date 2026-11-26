package dev.maplesu.manager

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.maplesu.core.theme.ThemeRuntime
import com.maplesu.core.ui.util.AppearancePreferences
import com.maplesu.core.ui.webui.WebUIActivity
import dev.maplesu.manager.workmode.ModeRuntime
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in original-page acceptance. Requires the original installed module, never a substituted webroot. */
class OriginalModuleWebUiAndroidTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test(timeout = 240_000) fun originalPageInteractionsReturnAndRecreation() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("verifyOriginalModuleWebUI") == "true")
        val moduleId = requireNotNull(args.getString("webuiModuleId"))
        val case = requireNotNull(args.getString("webuiCase"))
        val uiMode = requireNotNull(args.getString("webuiUiMode"))
        val expectedIndex = requireNotNull(args.getString("webuiIndexSha256"))
        require(moduleId.matches(Regex("[A-Za-z0-9_.-]+")))
        require(case in setOf("M05", "M06", "Next153"))
        require(uiMode in setOf("miuix", "material"))
        require(expectedIndex.matches(Regex("[0-9a-f]{64}")))
        val context = instrumentation.targetContext
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("Manager backend detection") { ModeRuntime.active != null }
        val mode = ModeRuntime.active.toString()
        val theme = ThemeRuntime.document(context)
        val themeGeneration = ThemeRuntime.generation(context)
        val themeReadsNavigator = themeGeneration != null && theme.has("isBackgroundEnabled") &&
            theme.optBoolean("isBackgroundEnabled")
        if (args.getString("webuiRequireThemeBackground") == "true") {
            assertTrue("The original LocalNavigator crash path requires an active FPT generation with backgrounds enabled",
                themeReadsNavigator)
        }
        val prefs = AppearancePreferences.prefs(context)
        val hadUiMode = prefs.contains("ui_mode")
        val previousUiMode = prefs.getString("ui_mode", null)
        assertTrue(prefs.edit().putString("ui_mode", uiMode).commit())
        val evidence = File(context.filesDir, "webui-original-acceptance").apply { mkdirs() }
        try {
            repeat(2) { opening ->
                val intent = Intent(context, WebUIActivity::class.java)
                    .setData(Uri.Builder().scheme("ksu").authority("webui")
                        .appendQueryParameter("id", moduleId).build())
                ActivityScenario.launch<WebUIActivity>(intent).use { scenario ->
                    var web = awaitPage(scenario)
                    verifyPage(web, moduleId, expectedIndex, case)
                    interact(web, case, evidence, "$case-$uiMode-open$opening")
                    capture(evidence, "$case-$uiMode-open$opening")
                    val oldView = web
                    scenario.recreate()
                    web = awaitPage(scenario)
                    assertNotSame("Activity recreation must replace the retired WebView", oldView, web)
                    verifyPage(web, moduleId, expectedIndex, case)
                    interact(web, case, evidence, "$case-$uiMode-recreated$opening")
                    capture(evidence, "$case-$uiMode-recreated$opening")
                    scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                    await("WebUI return must finish its Activity") { scenario.state == Lifecycle.State.DESTROYED }
                }
            }
            val result = "MAPLESU_ORIGINAL_WEBUI_RESULT:" + JSONObject().apply {
                put("passed", true)
                put("moduleId", moduleId)
                put("case", case)
                put("uiMode", uiMode)
                put("backendMode", mode)
                put("themeReadsNavigator", themeReadsNavigator)
                put("themeGeneration", themeGeneration?.name)
                put("indexSha256", expectedIndex)
                put("webViewProvider", WebView.getCurrentWebViewPackage()?.toString())
                put("openings", 2)
                put("activityRecreations", 2)
                put("evidenceDirectory", evidence.absolutePath)
            }
            println(result)
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$result\n") })
        } finally {
            val editor = prefs.edit()
            if (hadUiMode) editor.putString("ui_mode", previousUiMode) else editor.remove("ui_mode")
            assertTrue("Restore the user's UI mode", editor.commit())
        }
    }

    private fun verifyPage(web: WebView, moduleId: String, expectedIndex: String, case: String) {
        assertEquals("\"0\"", evaluate(web, "ksu.exec('id -u').trim()"))
        val command = "sha256sum /data/adb/modules/$moduleId/webroot/index.html"
        val actual = evaluate(web, "ksu.exec(${JSONObject.quote(command)}).trim().split(/\\s+/)[0]")
        assertEquals("Original installed index.html identity", JSONObject.quote(expectedIndex), actual)
        when (case) {
            "M05" -> await("M05 original page initialization") {
                evaluate(web, "!!document.querySelector('.container.ready')") == "true"
            }
            "M06" -> await("M06 original games navigation") {
                evaluate(web, "!!document.querySelector('[data-top=games]')") == "true"
            }
            "Next153" -> await("Official Next original page") {
                evaluate(web, "!!document.querySelector('#app')?.textContent.includes('Zygisk Next')") == "true"
            }
        }
    }

    private fun interact(web: WebView, case: String, evidence: File, phase: String) {
        when (case) {
            "M05" -> {
                evaluate(web, "document.querySelector('#openPackageModal').click()")
                await("M05 real package list") {
                    evaluate(web, "!!document.querySelector('#packageModal.active #appList .app-item')") == "true"
                }
                capture(evidence, "$phase-picker")
                evaluate(web, "document.querySelector('#packageModal .close-btn').click()")
                await("M05 cancel returns to page") {
                    evaluate(web, "!document.querySelector('#packageModal.active')") == "true"
                }
            }
            "M06" -> {
                evaluate(web, "document.querySelector('[data-top=games]').click()")
                await("M06 add-game control") { evaluate(web, "!!document.querySelector('#addGame')") == "true" }
                evaluate(web, "document.querySelector('#addGame').click()")
                await("M06 real application picker") {
                    evaluate(web, "!!document.querySelector('#appList .picker-card')") == "true"
                }
                capture(evidence, "$phase-picker")
                evaluate(web, "document.querySelector('#addCancel').click()")
                await("M06 cancel returns to games") {
                    evaluate(web, "!document.querySelector('#modalMask.show') && !!document.querySelector('#addGame')") == "true"
                }
            }
            "Next153" -> {
                evaluate(web, "document.querySelector('.refresh-icon').click()")
                await("Next remains rendered after status refresh") {
                    evaluate(web, "!!document.querySelector('#app')?.textContent.includes('Zygisk Next')") == "true"
                }
            }
        }
    }

    private fun awaitPage(scenario: ActivityScenario<WebUIActivity>): WebView {
        var view: WebView? = null
        await("Original module WebView creation") {
            scenario.onActivity { view = findWebView(it.window.decorView) }
            view != null
        }
        val web = requireNotNull(view)
        await("Original module document load") {
            evaluate(web, "document.readyState==='complete' && location.origin==='https://mui.kernelsu.org' && document.body.innerHTML.length>0") == "true"
        }
        return web
    }

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
        else -> null
    }

    private fun evaluate(view: WebView, script: String): String {
        val done = CountDownLatch(1)
        var value = ""
        instrumentation.runOnMainSync { view.evaluateJavascript(script) { value = it; done.countDown() } }
        assertTrue("Module JavaScript evaluation timed out", done.await(30, TimeUnit.SECONDS))
        return value
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        fail(description)
    }

    private fun capture(directory: File, name: String) {
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Device screenshot" }
        File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
