package dev.maplesu.manager.installer

import android.os.Bundle
import com.maplesu.core.ui.util.FlashOutcome
import com.maplesu.core.ui.util.restoreBoot
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OwnedRemovalPublicResultTest {
    @Before fun reset() { InstallerServiceClient.failure = null }

    @Test fun ownedWriteKeepsWrittenOutcomeAndAccurateRebootLog() {
        InstallerServiceClient.reply = status("REBOOT_REQUIRED", "OWNED_WRITES_REMOVED", true)
        val lines = mutableListOf<String>()
        val result = restoreBoot(lines::add, { fail(it) }, "host-task")
        assertEquals(0, result.code)
        assertEquals(FlashOutcome.WRITTEN, result.outcome)
        assertTrue(result.showReboot)
        assertEquals("- MapleSU environment changes removed. Reboot to apply it.", lines.last())
    }

    @Test fun ownedCompletionKeepsWrittenOutcomeAndDoesNotClaimStock() {
        InstallerServiceClient.reply = status("OWNED_WRITES_REMOVED", "OWNED_WRITES_REMOVED", false)
        val lines = mutableListOf<String>()
        val result = restoreBoot(lines::add, { fail(it) }, "host-task")
        assertEquals(0, result.code)
        assertEquals(FlashOutcome.WRITTEN, result.outcome)
        assertFalse(result.showReboot)
        assertEquals("- MapleSU environment changes removed", lines.last())
    }

    @Test fun exactAndLegacyReceiptsRetainRestoreOutcomeAndLog() {
        for (kind in listOf("EXACT_STOCK", null)) {
            InstallerServiceClient.reply = status("REBOOT_REQUIRED", kind, true)
            val lines = mutableListOf<String>()
            val result = restoreBoot(lines::add, { fail(it) }, "host-task")
            assertEquals(0, result.code)
            assertEquals(FlashOutcome.RESTORED, result.outcome)
            assertEquals("- Previous boot image restored. Reboot to apply it.", lines.last())
        }
    }

    @Test fun lostReplyRecoversOwnedKindFromDurableStatus() {
        InstallerServiceClient.reply = status("REBOOT_REQUIRED", "OWNED_WRITES_REMOVED", true)
        InstallerServiceClient.failure = IllegalStateException("transport disconnected after verified write")
        assertEquals(FlashOutcome.WRITTEN, restoreBoot({}, { fail(it) }, "host-task").outcome)
    }

    @Test fun failedWriteDoesNotBecomeRemovalSuccess() {
        InstallerServiceClient.reply = status("RECOVERY_REQUIRED", "OWNED_WRITES_REMOVED", false)
        val errors = mutableListOf<String>()
        val result = restoreBoot({}, errors::add, "host-task")
        assertEquals(1, result.code)
        assertEquals(FlashOutcome.RECOVERY_REQUIRED, result.outcome)
        assertFalse(errors.isEmpty())
    }

    @Test fun observerPreservesOwnedResultBeforeAndAfterReboot() {
        for (state in listOf("REBOOT_REQUIRED", "OWNED_WRITES_REMOVED")) {
            val result = observeCompletedResult(status(state, "OWNED_WRITES_REMOVED", state == "REBOOT_REQUIRED"))
            assertEquals(0, result.code)
            assertEquals(FlashOutcome.WRITTEN, result.outcome)
        }
    }

    @Test fun observerPreservesExactAndOrdinaryInstallResults() {
        assertEquals(FlashOutcome.RESTORED,
            observeCompletedResult(status("REBOOT_REQUIRED", "EXACT_STOCK", true)).outcome)
        assertEquals(FlashOutcome.RESTORED,
            observeCompletedResult(status("STOCK_RESTORED", null, false)).outcome)
        val install = status("REBOOT_REQUIRED", null, true).apply { putString("operation", "INSTALL") }
        assertEquals(FlashOutcome.WRITTEN, observeCompletedResult(install).outcome)
    }

    private fun status(state: String, kind: String?, reboot: Boolean) = Bundle().apply {
        putString("state", state)
        putString("operation", "RESTORE_STOCK")
        if (kind != null) putString("restorationKind", kind)
        putBoolean("rebootRequired", reboot)
    }
}
