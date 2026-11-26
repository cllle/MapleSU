package dev.maplesu.manager.runtime

import org.junit.Assert.*
import org.junit.Test

class RootServiceBootstrapTest {
    private val ready = RootServiceBootstrap.Result(0, "MAPLESU_ROOT_SERVICE_READY\n")

    @Test fun failedStaleAndNonrootSourcesDoNotSuppressTheLaterWorkingSu() {
        val events = mutableListOf<String>()
        val candidates = listOf(
            RootServiceBootstrap.Candidate("stale-sdk") { events += "sdk"; throw IllegalStateException() },
            RootServiceBootstrap.Candidate("nonroot-su") { events += "nonroot"; RootServiceBootstrap.Result(13, "") },
            RootServiceBootstrap.Candidate("working-su") { command ->
                if (command.contains("MAPLESU_ROOT_SERVICE_READY")) {
                    events += "probe"; ready
                } else {
                    events += "launch"; RootServiceBootstrap.Result(0, "")
                }
            },
        )
        assertTrue(RootServiceBootstrap.launch("start-service", candidates).ok)
        assertEquals(listOf("sdk", "nonroot", "probe", "launch"), events)
    }

    @Test fun zeroExitWithoutAReadinessReceiptDoesNotDispatch() {
        var calls = 0
        val candidate = RootServiceBootstrap.Candidate("empty") {
            calls++; RootServiceBootstrap.Result(0, "")
        }
        val failure = assertThrows(RootServiceBootstrap.Failure::class.java) {
            RootServiceBootstrap.launch("start-service", listOf(candidate))
        }
        assertEquals(0, failure.result.code)
        assertEquals(1, calls)
    }

    @Test fun aSuBannerBeforeTheReceiptDoesNotDiscardWorkingRoot() {
        var calls = 0
        val candidate = RootServiceBootstrap.Candidate("su") {
            calls++
            if (calls == 1) ready.copy(output = "su banner\n${ready.output}") else RootServiceBootstrap.Result(0, "")
        }
        assertTrue(RootServiceBootstrap.launch("start-service", listOf(candidate)).ok)
        assertEquals(2, calls)
    }

    @Test fun aSubmittedLaunchIsNeverReplayedThroughAnotherSource() {
        var calls = 0
        val first = RootServiceBootstrap.Candidate("selected") {
            calls++
            if (calls == 1) ready else RootServiceBootstrap.Result(19, "", "lost reply")
        }
        val other = RootServiceBootstrap.Candidate("other") { error("must not replay a submitted launch") }
        val failure = assertThrows(RootServiceBootstrap.Failure::class.java) {
            RootServiceBootstrap.launch("start-service", listOf(first, other))
        }
        assertEquals(19, failure.result.code)
        assertEquals("launch", failure.phase)
        assertEquals(2, calls)
    }

    @Test fun allSourcesAreAttemptedBeforeReportingExhaustion() {
        val tried = mutableListOf<Int>()
        val sources = (1..12).map { index -> RootServiceBootstrap.Candidate("source-$index") {
            tried += index; RootServiceBootstrap.Result(-1, "")
        } }
        val failure = assertThrows(RootServiceBootstrap.Failure::class.java) {
            RootServiceBootstrap.launch("start-service", sources)
        }
        assertEquals("INSTALLER_ROOT_SOURCES_EXHAUSTED", failure.message)
        assertEquals((1..12).toList(), tried)
    }

    @Test fun verifiedUidWithBlockedFrameworkIsDistinctFromMissingRoot() {
        val sources = listOf(
            RootServiceBootstrap.Candidate("root") { RootServiceBootstrap.Result(14, "") },
            RootServiceBootstrap.Candidate("missing") { RootServiceBootstrap.Result(13, "") },
        )
        val failure = assertThrows(RootServiceBootstrap.Failure::class.java) {
            RootServiceBootstrap.launch("start-service", sources)
        }
        assertEquals("INSTALLER_SERVICE_FRAMEWORK_UNAVAILABLE", failure.message)
        assertEquals(14, failure.result.code)
        assertEquals("root", failure.source)
        assertEquals("probe", failure.phase)
    }

    @Test fun cancellationStopsBeforeDispatchWithoutBecomingAnotherProbe() {
        try {
            val candidate = RootServiceBootstrap.Candidate("interrupted") {
                Thread.currentThread().interrupt(); ready
            }
            val other = RootServiceBootstrap.Candidate("other") { error("cancelled") }
            assertEquals("ROOT_EXECUTION_INTERRUPTED", assertThrows(InterruptedException::class.java) {
                RootServiceBootstrap.launch("start-service", listOf(candidate, other))
            }.message)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }

    @Test fun cancellationExceptionIsNeverConvertedIntoAnotherSourceAttempt() {
        val cancelled = java.util.concurrent.CancellationException("cancelled")
        val source = RootServiceBootstrap.Candidate("cancelled") { throw cancelled }
        assertSame(cancelled, assertThrows(java.util.concurrent.CancellationException::class.java) {
            RootServiceBootstrap.launch("start-service", listOf(source,
                RootServiceBootstrap.Candidate("other") { error("must not continue") }))
        })
    }

    @Test fun wrappedCancellationIsUnwrappedBeforeTryingAnotherSource() {
        val cancelled = java.util.concurrent.CancellationException("cancelled")
        assertSame(cancelled, assertThrows(java.util.concurrent.CancellationException::class.java) {
            RootServiceBootstrap.launch("start-service", listOf(
                RootServiceBootstrap.Candidate("wrapped") { throw java.util.concurrent.ExecutionException(cancelled) },
                RootServiceBootstrap.Candidate("other") { error("must not continue") }))
        })
    }

    @Test fun launchFailureRetainsTheOriginalStreamsDetailAndCause() {
        val cause = java.io.IOException("launch failed")
        val original = RootServiceBootstrap.Result(37, "stdout", "original detail", "stderr", cause)
        var calls = 0
        val failure = assertThrows(RootServiceBootstrap.Failure::class.java) {
            RootServiceBootstrap.launch("start-service", listOf(RootServiceBootstrap.Candidate("root") {
                if (++calls == 1) ready else original
            }))
        }
        assertSame(original, failure.result)
        assertSame(cause, failure.cause)
        assertEquals("INSTALLER_SERVICE_LAUNCH_FAILED", failure.message)
        assertEquals("launch", failure.phase)
        assertEquals("root", failure.source)
    }
}
