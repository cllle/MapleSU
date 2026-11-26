package com.maplesu.core.ui.navigation3

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.maplesu.core.Natives
import com.maplesu.core.ui.webui.WebUIActivity
import dev.maplesu.manager.modules.ModuleEnvironmentWatch
import dev.maplesu.manager.modules.ModuleWebUiLauncher
import dev.maplesu.manager.liteweb.LiteWebProxyBase
import dev.maplesu.manager.modules.webui.WebUiFailure
import dev.maplesu.manager.modules.webui.WebUiFailureReason
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Properties

/** Runs the complete production dispatcher; Android, Compose and the SDK boundary are fixtures. */
class WebUiDeepLinkRouteJvmTest {
    private lateinit var context: Context
    private val nativeId = "maplesu:0123456789abcdef0123456789abcdef"

    @Before fun reset() {
        context = Context()
        LocalContext.current = context
        LocalNavigator.current = Navigator()
        Natives.isManager = true
        Natives.isSafeMode = false
        ModuleEnvironmentWatch.waitReason = null
        ModuleEnvironmentWatch.ready = true
        ModuleWebUiLauncher.calls.clear()
        ModuleWebUiLauncher.failure = null
        Toast.messages.clear()
        LiteWebProxyBase.url = null
        LiteWebProxyBase.calls = 0
    }

    private fun dispatch(id: String, token: String? = "fixture-token", scheme: String = "ksu") {
        val uri = Uri.Builder().scheme(scheme).authority("webui").appendQueryParameter("id", id)
        token?.let { uri.appendQueryParameter("token", it) }
        dispatchUri(uri.build())
    }

    private fun dispatchUri(uri: Uri) {
        val intents = Channel<Intent>(1)
        check(intents.trySend(Intent().setData(uri)).isSuccess)
        intents.close()
        IntentDispatcher(intents)
    }

    private fun browserLink(name: String): String = Properties().apply {
        File(requireNotNull(System.getProperty("webui.links"))).inputStream().use(::load)
    }.getProperty(name)

    private fun dispatchBrowserLink(name: String) {
        val intent = Intent.parseUri(browserLink(name), Intent.URI_INTENT_SCHEME)
        dispatchUri(requireNotNull(intent.data))
    }

    @Test fun realLiteWebLinksTargetOnlyTheManagerPackage() {
        for (name in listOf("script", "native")) {
            assertEquals("dev.maplesu.manager", Intent.parseUri(browserLink(name), Intent.URI_INTENT_SCHEME).`package`)
        }
    }

    @Test fun realLiteWebScriptLinkOpensTheAssetHost() {
        LiteWebProxyBase.url = "http://127.0.0.1:18427/x7k9f/${"a".repeat(64)}/"
        dispatchBrowserLink("script")
        assertEquals("real browser link must reach the script host", 1, context.activities.size)
        assertEquals("YH_YC", context.activities.single().data?.getQueryParameter("id"))
        assertTrue(ModuleWebUiLauncher.calls.isEmpty())
    }

    @Test fun realLiteWebNativeLinkPreservesSdkRouteAndExplicitOrigin() {
        LiteWebProxyBase.url = "http://127.0.0.1:18427/x7k9f/${"a".repeat(64)}/"
        dispatchBrowserLink("native")
        assertEquals(1, ModuleWebUiLauncher.calls.size)
        assertEquals(nativeId, ModuleWebUiLauncher.calls.single().moduleId)
        assertEquals(WebUiOpenOrigin.DEEP_LINK, ModuleWebUiLauncher.calls.single().origin)
        assertTrue(context.activities.isEmpty())
    }

    @Test fun rotatedWebTokenRejectsOldBrowserLink() {
        LiteWebProxyBase.url = "http://127.0.0.1:18427/x7k9f/${"b".repeat(64)}/"
        dispatchBrowserLink("script")
        assertTrue(context.activities.isEmpty())
        assertTrue(ModuleWebUiLauncher.calls.isEmpty())
    }

    @Test fun unreadableWebConfigurationDoesNotAuthorizeABrowserLink() {
        dispatchBrowserLink("script")
        assertEquals(1, LiteWebProxyBase.calls)
        assertTrue(context.activities.isEmpty())
        assertTrue(ModuleWebUiLauncher.calls.isEmpty())
    }

    @Test fun webTokenNeverAuthorizesModuleActions() {
        LiteWebProxyBase.url = "http://127.0.0.1:18427/x7k9f/${"a".repeat(64)}/"
        dispatchUri(Uri("ksu://action?id=YH_YC&webToken=${"a".repeat(64)}"))
        assertTrue(LocalNavigator.current.backStack.isEmpty())
        assertEquals(0, LiteWebProxyBase.calls)
    }

    @Test fun invalidWebTokenShapeDoesNotReadProtectedConfiguration() {
        dispatchUri(Uri("ksu://webui?id=YH_YC&webToken=wrong-token"))
        assertTrue(context.activities.isEmpty())
        assertEquals(0, LiteWebProxyBase.calls)
    }

    @Test fun browserLinkChecksManagerBeforeReadingProtectedConfiguration() {
        Natives.isManager = false
        dispatchBrowserLink("script")
        assertTrue(context.activities.isEmpty())
        assertEquals(0, LiteWebProxyBase.calls)
    }

    @Test fun browserLinkChecksEnvironmentBeforeReadingProtectedConfiguration() {
        ModuleEnvironmentWatch.waitReason = "fixture-starting"
        ModuleEnvironmentWatch.ready = false
        dispatchBrowserLink("script")
        assertTrue(context.activities.isEmpty())
        assertEquals(0, LiteWebProxyBase.calls)
    }

    @Test fun nativeShortcutUsesSdkLauncherWithExactIdAndExplicitOrigin() {
        dispatch(nativeId)
        assertEquals("native shortcut must reach the existing SDK launcher", 1, ModuleWebUiLauncher.calls.size)
        val call = ModuleWebUiLauncher.calls.single()
        assertSame(context, call.context)
        assertEquals(nativeId, call.moduleId)
        assertEquals(WebUiOpenOrigin.DEEP_LINK, call.origin)
        assertTrue("native IDs have no KSU asset webroot", context.activities.isEmpty())
    }

    @Test fun scriptShortcutPreservesAssetHostAndDecodedModuleId() {
        val id = "fixture-module_1"
        dispatch(id)
        assertTrue(ModuleWebUiLauncher.calls.isEmpty())
        val intent = context.activities.single()
        assertEquals(WebUIActivity::class.java, intent.target)
        assertEquals("ksu", intent.data?.scheme)
        assertEquals("webui", intent.data?.host)
        assertEquals(id, intent.data?.getQueryParameter("id"))
        assertNull(intent.data?.getQueryParameter("token"))
    }

    @Test fun invalidTokenStartsNeitherHostNorSdk() {
        dispatch(nativeId, "wrong-token")
        assertTrue(context.activities.isEmpty())
        assertTrue(ModuleWebUiLauncher.calls.isEmpty())
    }

    @Test fun missingTokenStartsNeitherHostNorSdk() {
        dispatch(nativeId, null)
        assertTrue(context.activities.isEmpty())
        assertTrue(ModuleWebUiLauncher.calls.isEmpty())
    }

    @Test fun anotherSchemeStartsNeitherHostNorSdk() {
        dispatch(nativeId, scheme = "https")
        assertTrue(context.activities.isEmpty())
        assertTrue(ModuleWebUiLauncher.calls.isEmpty())
    }

    @Test fun managerIdentityRemainsRequired() {
        Natives.isManager = false
        dispatch(nativeId)
        assertTrue(context.activities.isEmpty())
        assertTrue(ModuleWebUiLauncher.calls.isEmpty())
    }

    @Test fun environmentReadinessRemainsRequired() {
        ModuleEnvironmentWatch.waitReason = "fixture-starting"
        ModuleEnvironmentWatch.ready = false
        dispatch(nativeId)
        assertTrue(context.activities.isEmpty())
        assertTrue(ModuleWebUiLauncher.calls.isEmpty())
    }

    @Test fun sdkFailureIsShownAndDoesNotFallBackToWrongAssetDirectory() {
        ModuleWebUiLauncher.failure = WebUiFailure(WebUiFailureReason.SDK_REJECTED, "fixture-sdk-error")
        dispatch(nativeId)
        assertEquals(1, ModuleWebUiLauncher.calls.size)
        assertTrue(context.activities.isEmpty())
        assertTrue(Toast.messages.single().contains("SDK_REJECTED: fixture-sdk-error"))
    }
}
