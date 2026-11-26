package dev.maplesu.manager.modules.webui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleWebUiPreparationTest {
    private val spec = WebUiOpenSpec("native123", WebUiRoute.ULTRA_SDK)
    private val gate = WebUiCompatibilityCheck { WebUiGateVerdict.permit("UNVERIFIED") }
    private val ready = HttpReadyProbe { HttpReadyResult.readyAt(it) }

    @Test fun recoveryRunsBeforeSdkAndKeepsRealModuleDirectory() = runBlocking {
        val order = mutableListOf<String>()
        val events = mutableListOf<WebUiLifecycleStage>()
        val result = WebUiOpenCoordinator(
            SdkOpenRunner { order += "sdk"; SdkOpenOutcome(true, 1234,
                pageOpener = WebUiPageOpener.MAPLESU, moduleDir = "/data/native/modules/native123/") },
            ready, gate, onStage = { events += it.stage },
            prepareEnvironment = { order += "prepare"; null },
            pageOpen = { order += "page"; assertEquals("/data/native/modules/native123/", it.moduleDir); null },
        ).open(spec)
        assertTrue(result.succeeded)
        assertEquals(listOf("prepare", "sdk", "page"), order)
        assertTrue(events.indexOf(WebUiLifecycleStage.ENVIRONMENT_PREPARED) < events.indexOf(WebUiLifecycleStage.SDK_OPEN))
    }

    @Test fun realPreparationFailureDoesNotSubmitModuleOrPage() = runBlocking {
        var calls = 0
        val failure = WebUiFailure(WebUiFailureReason.ENVIRONMENT_NOT_RUNNING, "SDK_GATEWAY_IDENTITY_CHANGED")
        val result = WebUiOpenCoordinator(SdkOpenRunner { calls++; SdkOpenOutcome(true, 1234) }, ready, gate,
            prepareEnvironment = { failure }, pageOpen = { calls++; null }).open(spec)
        assertFalse(result.succeeded)
        assertEquals(failure, result.failure)
        assertEquals(0, calls)
    }

    @Test fun compatibilityGateStillPrecedesEnvironmentMutation() = runBlocking {
        var preparations = 0
        val rejected = WebUiFailure(WebUiFailureReason.BLOCKED_INCOMPATIBLE, "actual mismatch")
        val result = WebUiOpenCoordinator(SdkOpenRunner { error("SDK submitted") }, ready,
            WebUiCompatibilityCheck { WebUiGateVerdict.refusal(rejected) },
            prepareEnvironment = { preparations++; null }).open(spec)
        assertEquals(rejected, result.failure)
        assertEquals(0, preparations)
    }

    @Test fun overlappingRequestsShareOnePreparationAndSubmission() = runBlocking {
        var preparations = 0
        var submissions = 0
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val coordinator = WebUiOpenCoordinator(SdkOpenRunner { submissions++; SdkOpenOutcome(true, 1234) }, ready, gate,
            prepareEnvironment = { preparations++; entered.complete(Unit); release.await(); null })
        val first = async { coordinator.open(spec) }
        entered.await()
        val second = async { coordinator.open(spec) }
        kotlinx.coroutines.yield()
        release.complete(Unit)
        assertTrue(listOf(first, second).awaitAll().all { it.succeeded })
        assertEquals(1, preparations)
        assertEquals(1, submissions)
    }

    @Test fun cancellationDuringPreparationDoesNotStartLateModule() = runBlocking {
        var submissions = 0
        val entered = CompletableDeferred<Unit>()
        val coordinator = WebUiOpenCoordinator(SdkOpenRunner { submissions++; SdkOpenOutcome(true, 1234) }, ready, gate,
            prepareEnvironment = { entered.complete(Unit); CompletableDeferred<Unit>().await(); null })
        val opening = launch { coordinator.open(spec) }
        entered.await()
        opening.cancelAndJoin()
        assertEquals(0, submissions)
    }

    @Test fun alreadyRunningPathDoesNotAddPreparation() = runBlocking {
        val events = mutableListOf<WebUiLifecycleStage>()
        val result = WebUiOpenCoordinator(SdkOpenRunner { SdkOpenOutcome(true, 1234) }, ready, gate,
            onStage = { events += it.stage }).open(spec)
        assertTrue(result.succeeded)
        assertFalse(events.contains(WebUiLifecycleStage.ENVIRONMENT_PREPARING))
    }

    @Test fun malformedDirectoryCannotSilentlySelectBridgeFreeWebView() {
        for (path in listOf(null, "", "/", "relative/module", "/data/../modules/native123", "/data/mod\nules/native123", "/data/modules\\native123")) {
            val failure = ModuleWebUiPlan.failureFromSdkOpen(SdkOpenOutcome(true, 1234,
                pageOpener = WebUiPageOpener.MAPLESU, moduleDir = path))
            assertEquals(path, WebUiFailureReason.SDK_REJECTED, failure?.reason)
        }
        assertEquals("/data/native/modules/native123/", ModuleWebUiPlan.moduleDirectoryOrNull("/data/native/modules/native123/"))
    }
}
