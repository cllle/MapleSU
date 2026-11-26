package com.maplesu.core.ui.screen.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.TimeoutException

class InstallSourceAccessFailureTest {
    @Test fun wrappedCancellationIsPropagatedInsteadOfClassified() {
        val cancelled = java.util.concurrent.CancellationException("cancelled")
        assertEquals(cancelled, assertThrows(java.util.concurrent.CancellationException::class.java) {
            installSourceAccessFailure(IllegalStateException("INSTALLER_SERVICE_CONNECT_FAILED", cancelled))
        })
    }

    @Test fun unavailableModeSwitchSessionIsAChannelFailure() {
        assertEquals(InstallSourceAccessFailure.CHANNEL_UNAVAILABLE,
            installSourceAccessFailure(IllegalStateException("MODE_SWITCH_ROOT_SESSION_UNAVAILABLE")))
    }

    @Test fun binderFailureInsideAnAuthorizationWrapperRetainsItsMeaning() {
        val error = IllegalStateException("INSTALLER_ROOT_REQUIRED",
            IllegalStateException("INSTALLER_SERVICE_DISCONNECTED"))
        assertEquals(InstallSourceAccessFailure.CHANNEL_UNAVAILABLE, installSourceAccessFailure(error))
    }

    @Test fun timeoutDoesNotAssertThatRootIsMissing() {
        assertEquals(InstallSourceAccessFailure.CHANNEL_UNAVAILABLE,
            installSourceAccessFailure(TimeoutException()))
    }

    @Test fun explicitlyMissingRootKeepsTheAuthorizationHint() {
        assertEquals(InstallSourceAccessFailure.ROOT_REQUIRED,
            installSourceAccessFailure(IllegalStateException("INSTALLER_ROOT_REQUIRED")))
    }

    @Test fun runningRootWithAFailedLaunchShowsTheConnectionFailure() {
        assertEquals(InstallSourceAccessFailure.CHANNEL_UNAVAILABLE,
            installSourceAccessFailure(IllegalStateException("INSTALLER_ROOT_UNAVAILABLE"), rootWasRunning = true))
    }

    @Test fun partitionErrorsAreNotRootAuthorizationErrors() {
        assertEquals(InstallSourceAccessFailure.CHECK_FAILED,
            installSourceAccessFailure(IllegalStateException("INSTALLER_BLOCK_DEVICE_REQUIRED")))
    }
}
