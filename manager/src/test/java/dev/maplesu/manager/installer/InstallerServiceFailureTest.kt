package dev.maplesu.manager.installer

import com.maplesu.core.ui.screen.install.InstallSourceAccessFailure
import com.maplesu.core.ui.screen.install.installSourceAccessFailure
import dev.maplesu.manager.runtime.RootServiceBootstrap
import org.junit.Assert.*
import org.junit.Test

class InstallerServiceFailureTest {
    private fun bootstrapFailure(code: Int): RootServiceBootstrap.Failure =
        assertThrows(RootServiceBootstrap.Failure::class.java) {
            RootServiceBootstrap.launch("start-service", listOf(RootServiceBootstrap.Candidate("root") {
                RootServiceBootstrap.Result(code, "original stdout", "original detail", "original stderr")
            }))
        }

    @Test fun onlyTheExplicitUidFailureUsesTheAuthorizationException() {
        val original = bootstrapFailure(13)
        val failure = installerServiceFailure(original)
        assertTrue(failure is InstallerServiceClient.AuthorizationFailure)
        assertSame(original, failure.cause)
        assertEquals(13, installerFailureExitCode(failure))
        assertEquals(InstallSourceAccessFailure.ROOT_REQUIRED, installSourceAccessFailure(failure))
    }

    @Test fun frameworkFailureReachesTheExecutionClassifierWithoutAnAuthorizationWrapper() {
        val original = bootstrapFailure(14)
        val failure = installerServiceFailure(original)
        assertSame(original, failure)
        assertEquals("original stdout", original.result.output)
        assertEquals("original stderr", original.result.stderr)
        assertEquals("original detail", original.result.detail)
        assertEquals(14, installerFailureExitCode(failure))
        assertEquals("INSTALLER_SERVICE_FRAMEWORK_UNAVAILABLE", installerFailureDiagnostic(failure))
        assertEquals(InstallSourceAccessFailure.CHANNEL_UNAVAILABLE, installSourceAccessFailure(failure))
    }

    @Test fun exhaustedOrTimedOutSourcesDoNotAssertThatRootWasDenied() {
        val failure = installerServiceFailure(bootstrapFailure(-1))
        assertFalse(failure is InstallerServiceClient.AuthorizationFailure)
        assertEquals(InstallSourceAccessFailure.CHANNEL_UNAVAILABLE, installSourceAccessFailure(failure))
    }

    @Test fun binderFailureRetainsItsPhaseAndOriginalCause() {
        val original = java.util.concurrent.TimeoutException("original connection timeout")
        val failure = InstallerServiceClient.ServiceFailure("CONNECT", original)
        assertSame(original, failure.cause)
        assertEquals("CONNECT", failure.phase)
        assertEquals(InstallSourceAccessFailure.CHANNEL_UNAVAILABLE, installSourceAccessFailure(failure))
    }

    @Test fun wrappedInterruptRetainsIdentityAndInterruptFlag() {
        try {
            val original = InterruptedException("interrupted")
            val failure = InstallerServiceClient.ServiceFailure("BIND", java.util.concurrent.ExecutionException(original))
            assertSame(original, assertThrows(InterruptedException::class.java) { installSourceAccessFailure(failure) })
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }
}
