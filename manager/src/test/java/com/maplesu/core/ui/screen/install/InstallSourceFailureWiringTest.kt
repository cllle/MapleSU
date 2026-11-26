package com.maplesu.core.ui.screen.install

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Access errors must keep their meaning rather than becoming a missing-root assertion. */
class InstallSourceFailureWiringTest {
    @Test fun directSourceDoesNotMapEveryFailureToMissingRoot() {
        val source = File("src/main/java/com/maplesu/core/ui/screen/install/InstallSteps.kt").readText()
        val status = source.substringAfter("internal fun InstallSourceStatusSection")
            .substringBefore("internal fun InstallOriginalImageSection")
        assertFalse(status.contains("else -> R.string.install_source_root_required"))
        assertTrue(status.contains("InstallSourceAccessFailure.CHANNEL_UNAVAILABLE"))
    }

    @Test fun directFailuresAreClassifiedByTheSubmittedTransaction() {
        val source = File("src/main/java/com/maplesu/core/ui/screen/install/InstallScreen.kt").readText()
        val page = source.substringBefore("private fun probeInstallBoot(")
        assertFalse(page.contains("StockInstallPreflight"))
        assertFalse(page.contains("sourceAccessFailure ="))
        val flow = File("src/main/java/dev/maplesu/manager/installer/StockInstallFlow.kt").readText()
        assertTrue(flow.contains("failureAt = { phase, error ->"))
        assertTrue(flow.contains("installerFailureDiagnostic(error)"))
        assertTrue(flow.contains("shouldCancel = { prepareAttempted }"))
        val selected = source.substringAfter("val selectImageLauncher =").substringBefore("val normalDecision =")
        assertTrue(selected.contains("installMethod = InstallMethod.SelectFile(uri, summary = selectFileTip)"))
        assertFalse(selected.contains("StockInstallPreflight"))
        assertFalse(selected.contains("UltraEnvironmentProbe"))
        assertFalse(source.contains("filePreflightRequested"))
    }
}
