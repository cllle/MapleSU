package dev.maplesu.manager.runtime.ultra

import android.content.Context
import com.maplesu.core.KernelSUApplication
import dev.maplesu.manager.workmode.*
import dev.maplesu.manager.workmode.boot.ModeBootstrapFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Nineteen methods retain the original entry-gate cases; loops add current scope/capability fences. */
class StartupFaultGateTest {
    private val context = Context()
    private fun fault() = UltraEnvironmentProbe.Snapshot(UltraEnvironmentProbe.State.Fault, observedAtElapsedMs = 0)
    private fun running(root: Boolean = true) = UltraEnvironmentProbe.Snapshot(
        UltraEnvironmentProbe.State.Running, observedAtElapsedMs = 0, kernelRootVerified = root)
    @Before fun reset() = Fixture.reset()
    private fun startup() = runBlocking { KernelSUApplication().runRecovery() }
    private fun recovered(key: String = Fixture.key!!, boot: String? = Fixture.boot) =
        UltraEnvironmentProbe.refreshRecoveredIdentity(context, key, boot)
    private fun noCapabilities() {
        for (call in listOf("modules", "version", "authorization", "maintenance", "persistent-log")) {
            assertFalse("$call: ${Fixture.calls}", call in Fixture.calls)
        }
    }

    @Test fun sameKeyAndBootFaultStopsBeforeAnyRestoreOrSdkCall() {
        Fixture.observe(fault())
        assertTrue(UltraIdentityRecovery.blocked(context))
        assertEquals("RECOVERY_SUSPENDED", UltraIdentityRecovery.recover(context))
        assertEquals(emptyList<String>(), Fixture.calls)
    }

    @Test fun faultAlsoStopsTheEarlyTemporarySessionBranch() {
        Fixture.session = Fixture.SESSION
        Fixture.observe(fault())
        assertEquals("RECOVERY_SUSPENDED", UltraIdentityRecovery.recover(context))
        assertEquals(emptyList<String>(), Fixture.calls)
    }

    @Test fun changedKeyDoesNotInheritAHistoricalFault() {
        Fixture.observe(fault()); Fixture.key = Fixture.KEY_B
        assertFalse(UltraIdentityRecovery.blocked(context))
        assertEquals("RECOVERY_ROOT_NOT_VERIFIED", UltraIdentityRecovery.recover(context))
        assertEquals(1, Fixture.count("restore"))
        assertEquals(1, Fixture.count("root"))
        assertEquals(listOf(Fixture.KEY_B), Fixture.rootKeys)
        assertEquals(0, Fixture.count("environment"))
    }

    @Test fun changedBootDoesNotInheritAHistoricalFault() {
        Fixture.observe(fault()); Fixture.boot = "fixture-boot-b"
        assertFalse(UltraIdentityRecovery.blocked(context))
        assertEquals("RECOVERY_ROOT_NOT_VERIFIED", UltraIdentityRecovery.recover(context))
        assertEquals(1, Fixture.count("restore"))
        assertEquals(1, Fixture.count("root"))
        assertEquals(0, Fixture.count("environment"))
    }

    @Test fun absentKeyReachesRecoveryAndLiveOwnerProofShortCircuitsIt() {
        Fixture.observe(fault()); Fixture.key = null
        assertFalse(UltraIdentityRecovery.blocked(context))
        assertEquals("RECOVERY_ROOT_NOT_VERIFIED", UltraIdentityRecovery.recover(context))
        assertEquals(1, Fixture.count("restore"))
        assertEquals(0, Fixture.count("root"))
        Fixture.reset(); Fixture.key = null
        Fixture.owner = UltraRuntimeClient.ManagerOwnerIdentity(10123, 1, "host-a"); Fixture.ownerRoot = true
        assertEquals(UltraEnvironmentProbe.State.Running, UltraEnvironmentProbe.refresh(context, false))
        assertTrue(UltraEnvironmentProbe.hasCurrentRootProof(context))
        Fixture.calls.clear()
        assertEquals("RECOVERY_OWNER_VERIFIED", UltraIdentityRecovery.recover(context))
        assertEquals(emptyList<String>(), Fixture.calls)
        // A readable signed owner without a matching live root proof grants no Root.
        Fixture.ownerRoot = false
        UltraEnvironmentProbe.refresh(context, true)
        assertFalse(UltraEnvironmentProbe.hasCurrentRootProof(context))
    }

    @Test fun rootCandidateProofDoesNotWriteAPersistentLogPolicy() {
        for (uid in listOf(null, 1, 0)) {
            Fixture.reset(); Fixture.uid = uid
            val result = UltraIdentityRecovery.recover(context)
            assertEquals(if (uid == 0) "RECOVERY_LOCAL_KEY_VERIFIED" else "RECOVERY_ROOT_NOT_VERIFIED", result)
            assertEquals(1, Fixture.count("root"))
            assertEquals(listOf(Fixture.KEY_A), Fixture.rootKeys)
            assertFalse("persistent-log" in Fixture.calls)
            assertEquals(uid == 0, UltraEnvironmentProbe.currentSnapshot(context).rootAvailable)
            if (uid == 0) {
                assertEquals(1, Fixture.count("environment"))
                assertEquals(0, Fixture.count("restore"))
            }
        }
    }

    @Test fun historicalPairingDoesNotGrantRootOrCapabilitiesWithUnknownOrFaultEvidence() {
        for (isFault in listOf(false, true)) {
            Fixture.reset(); Fixture.pairedKey = Fixture.KEY_A
            if (isFault) Fixture.observe(fault()) else {
                Fixture.sdkSucceeded = false; Fixture.sdkError = "ERR_MODULE_NO_BOOT"
            }
            startup()
            assertFalse(UltraEnvironmentProbe.currentSnapshot(context).rootAvailable)
            assertTrue(Fixture.terminal(), Fixture.terminal().contains("ready=false"))
            noCapabilities()
        }
    }

    @Test fun successfulPairingRequiresScopedRootObservationAndAllCurrentCapabilities() {
        Fixture.uid = 0; Fixture.pair = true
        startup()
        assertEquals(listOf("environment", "root", "pair", "modules", "version", "authorization", "maintenance"), Fixture.calls)
        assertTrue(Fixture.terminal(), Fixture.terminal().contains("ready=true"))
        assertEquals("allow", Fixture.gate)
        val rejected = linkedMapOf<String, () -> Unit>(
            "module-error" to { Fixture.moduleSucceeded = false },
            "module-array-missing" to { Fixture.moduleArray = false },
            "version-error" to { Fixture.versionSucceeded = false },
            "version-empty" to { Fixture.version = " " },
            "authorization-error" to { Fixture.authorizationOk = false },
            "boot-changed" to { Fixture.onAuthorization = { Fixture.boot = "changed" } },
            "key-changed" to { Fixture.onAuthorization = { Fixture.key = Fixture.KEY_B } },
            "activation-changed" to { Fixture.onAuthorization = { ModeRuntime.activation = ModeActivation() } },
            "root-owner-retired" to { Fixture.rootMarkerCurrent = false },
            "session-owner-retired" to { Fixture.sessionMarkerCurrent = false },
        )
        for ((name, arrange) in rejected) {
            Fixture.reset(); Fixture.uid = 0; Fixture.pair = true; arrange()
            startup()
            assertTrue("$name: ${Fixture.terminal()}", Fixture.terminal().contains("ready=false"))
            assertTrue(name, Fixture.terminal().contains("reason=CAPABILITIES_UNVERIFIED"))
            assertEquals(name, 1, Fixture.count("modules"))
            assertEquals(name, 1, Fixture.count("version"))
            assertEquals(name, 1, Fixture.count("authorization"))
        }
        Fixture.reset(); Fixture.key = null; Fixture.ownerRoot = true; Fixture.pair = true
        Fixture.owner = UltraRuntimeClient.ManagerOwnerIdentity(10123, 1, "host-a")
        Fixture.onAuthorization = { Fixture.owner = Fixture.owner!!.copy(epoch = 2) }
        startup()
        assertTrue(Fixture.terminal(), Fixture.terminal().contains("reason=CAPABILITIES_UNVERIFIED"))
        assertEquals(0, Fixture.count("root"))
        assertEquals(0, Fixture.count("maintenance"))
    }

    @Test fun verifiedKernelRootRemainsIndependentOfHelperAndCapabilityReadiness() {
        for (state in listOf("Unknown", "NotInstalled")) {
            Fixture.reset(); Fixture.sdkState = state; Fixture.uid = 0; Fixture.pair = true
            Fixture.moduleSucceeded = false
            startup()
            assertTrue(UltraEnvironmentProbe.currentSnapshot(context).rootAvailable)
            assertEquals(0, Fixture.count("restore"))
            assertEquals(1, Fixture.count("root"))
            assertTrue(Fixture.terminal(), Fixture.terminal().contains("reason=CAPABILITIES_UNVERIFIED"))
            assertFalse("persistent-log" in Fixture.calls)
        }
    }

    @Test fun aTemporarySessionNeverUsesTheHistoricalPermanentKey() {
        Fixture.session = Fixture.SESSION; Fixture.uid = 0; Fixture.setupInstalled = true
        startup()
        assertEquals(listOf(Fixture.SESSION), Fixture.rootKeys)
        assertEquals(0, Fixture.count("restore"))
        assertEquals(0, Fixture.count("pair"))
        assertEquals(0, Fixture.count("maintenance"))
        assertEquals(0, Fixture.count("store"))
        assertEquals(1, Fixture.count("resume-boot-sdk"))
        assertTrue(Fixture.terminal(), Fixture.terminal().contains("ready=true"))
        assertFalse("persistent-log" in Fixture.calls)
    }

    @Test fun unsuccessfulWarmupDoesNotGrantCapabilitiesFromHistoricalRunningEvidence() {
        Fixture.observe(running(root = false)); Fixture.pairedKey = Fixture.KEY_A
        startup()
        assertFalse(UltraEnvironmentProbe.currentSnapshot(context).rootAvailable)
        assertEquals(1, Fixture.count("restore"))
        assertEquals(1, Fixture.count("pair"))
        assertTrue(Fixture.terminal(), Fixture.terminal().contains("ready=false"))
        noCapabilities()
    }

    @Test fun ksuOwnershipDoesNotStartSdkOrUseHistoricalUltraEvidence() {
        Fixture.observe(running()); ModeRuntime.active = WorkMode.KSU
        startup()
        assertEquals(emptyList<String>(), Fixture.calls)
        assertEquals("skip", Fixture.gate)
        assertTrue(Fixture.terminal(), Fixture.terminal().contains("reason=MODE_KSU"))
    }

    @Test fun unresolvedDetectionObservesAndRecoversBeforeItsConsumerWithoutAwaitingIt() {
        ModeRuntime.active = null; ModeRuntime.detectionComplete = false
        startup()
        assertEquals("environment", Fixture.calls.first())
        assertTrue(Fixture.calls.indexOf("root") < Fixture.calls.indexOf("restore"))
        assertEquals(1, Fixture.count("restore"))
        assertFalse("await-detection" in Fixture.calls)
        assertEquals(0, Fixture.count("setup"))
        assertTrue(Fixture.terminal(), Fixture.terminal().contains("reason=DETECTION_PENDING"))
        noCapabilities()
        Fixture.reset(); ModeRuntime.active = null; ModeRuntime.detectionComplete = false
        Fixture.uid = 0; Fixture.pair = true
        startup()
        assertEquals("environment", Fixture.calls.first())
        assertEquals(0, Fixture.count("restore"))
        assertEquals(1, Fixture.count("retry-detection"))
        assertFalse("await-detection" in Fixture.calls)
        assertTrue(Fixture.terminal(), Fixture.terminal().contains("ready=true"))
    }

    @Test fun acceptedIdentityReusesTheValidSameKeyBootAndActivationObservation() {
        Fixture.observe(running())
        repeat(3) { assertEquals(UltraEnvironmentProbe.State.Running, recovered()) }
        assertEquals(emptyList<String>(), Fixture.calls)
        assertTrue(UltraEnvironmentProbe.hasCurrentRootProof(context))
    }

    @Test fun acceptedIdentityWithAChangedKeyPerformsOneNewEnvironmentQuery() {
        Fixture.observe(running()); Fixture.key = Fixture.KEY_B
        repeat(2) { assertEquals(UltraEnvironmentProbe.State.Running, recovered()) }
        assertEquals(listOf("environment"), Fixture.calls)
        assertTrue(UltraEnvironmentProbe.currentSnapshot(context).rootAvailable)
        assertEquals(0, Fixture.count("maintenance"))
    }

    @Test fun acceptedIdentityRejectsTheOldBootBeforeOneNewBootEnvironmentQuery() {
        Fixture.observe(running()); val oldBoot = Fixture.boot; Fixture.boot = "fixture-boot-b"
        assertEquals(UltraEnvironmentProbe.State.Unknown, recovered(boot = oldBoot))
        assertEquals(emptyList<String>(), Fixture.calls)
        assertFalse(UltraEnvironmentProbe.currentSnapshot(context).rootAvailable)
        repeat(2) { assertEquals(UltraEnvironmentProbe.State.Running, recovered()) }
        assertEquals(listOf("environment"), Fixture.calls)
        assertTrue(UltraEnvironmentProbe.currentSnapshot(context).rootAvailable)
    }

    @Test fun expiredOrReactivatedEvidenceIsQueriedAndRecoveryProofIsSingleUse() {
        Fixture.observe(running()); android.os.SystemClock.now = 3_000
        repeat(2) { assertEquals(UltraEnvironmentProbe.State.Running, recovered()) }
        assertEquals(listOf("environment"), Fixture.calls)
        android.os.SystemClock.now = 6_000; Fixture.uid = 0
        UltraEnvironmentProbe.refresh(context, false)
        assertEquals(2, Fixture.count("environment")); assertEquals(1, Fixture.count("root"))
        ModeRuntime.activation = ModeActivation()
        assertFalse(UltraEnvironmentProbe.currentSnapshot(context).rootAvailable)
        UltraEnvironmentProbe.refresh(context, false)
        assertEquals(3, Fixture.count("environment")); assertEquals(2, Fixture.count("root"))
        // An in-flight identity change discards the receipt and the recovered root proof.
        Fixture.reset(); Fixture.onEnvironment = { Fixture.key = Fixture.KEY_B }
        assertEquals(UltraEnvironmentProbe.State.Unknown, recovered())
        assertFalse(UltraEnvironmentProbe.currentSnapshot(context).rootAvailable)
        assertEquals(0, Fixture.count("root"))
    }

    @Test fun firstFrameReusesFreshValidIdentityAfterExplicitNegativeKsuEvidence() {
        ModeRuntime.active = null; Fixture.observe(running())
        assertNull(Fixture.pairedKey)
        ModeBootstrapFixture().onManagerFirstFrame(context)
        assertEquals(emptyList<String>(), Fixture.calls)
        for (ksu in listOf<Boolean?>(null, true)) {
            Fixture.reset(); ModeRuntime.active = null
            ModeRuntime.latestDetection = Detection(trace = listOf(Trace("S1", ksu)))
            ModeBootstrapFixture().onManagerFirstFrame(context)
            assertEquals(emptyList<String>(), Fixture.calls)
        }
    }

    @Test fun firstFrameDoesNotInheritAnotherKeyBootOrActivationObservation() {
        for (change in listOf<() -> Unit>(
            { Fixture.key = Fixture.KEY_B },
            { Fixture.boot = "fixture-boot-b" },
            { ModeRuntime.activation = ModeActivation() },
        )) {
            Fixture.reset(); ModeRuntime.active = null; Fixture.observe(running()); Fixture.uid = 0
            change()
            ModeBootstrapFixture().onManagerFirstFrame(context)
            assertEquals(listOf("environment", "root"), Fixture.calls)
            assertTrue(UltraEnvironmentProbe.currentSnapshot(context).rootAvailable)
        }
    }
}
