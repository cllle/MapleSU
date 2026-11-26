package com.maplesu.core.ui.screen.install

import dev.maplesu.manager.publicDownloadPath
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SelectedImageExportContractTest {
    private fun source(relative: String) = File("src/main/java/$relative").readText()
    private fun ui(relative: String) = source("com/maplesu/core/ui/$relative")

    @Test fun exportedDownloadUsesTheCompleteProviderReportedNameAndDirectory() {
        val storage = File(System.getProperty("java.io.tmpdir"), "selected-image-storage")
        val actualName = "maplesu-patched-0123456789ab (1).img"
        assertEquals(File(storage, "Download/$actualName").absolutePath,
            publicDownloadPath(storage, "Download/", actualName))
    }

    @Test fun chosenImageLeavesBeforeAnyModeSwitchOrInstallTransaction() {
        val body = ui("util/KsuCli.kt").substringAfter("private fun installStockTarget(")
            .substringBefore("private fun copyInstallerImage(")
        val selected = body.indexOf("if (bootUri != null) {")
        val switch = body.indexOf("val active = ModeRuntime.active")
        assertTrue("Selected files must be exported before looking up a mode-switch service", selected in 0 until switch)
        val export = body.substring(selected, switch)
        assertTrue(export.contains("exportSelectedImage("))
        assertTrue(export.contains("if (selectedImageOnly)"))
        assertTrue(export.contains("exportOnly = true"))
        assertTrue(export.contains("outputPath = saved"))
        assertTrue(ui("util/KsuCli.kt").contains("installationTaskId?.takeIf { bootUri == null || downloaded }"))
        assertFalse(export.contains("InstallerServiceClient."))
        assertFalse(export.contains("StockInstallFlow.install("))
        assertFalse(export.contains("installCommit("))
        assertFalse(export.contains("written()"))
    }

    @Test fun filePatchingDoesNotStartRootRestorationPreflight() {
        val screen = ui("screen/install/InstallScreen.kt")
        assertFalse(screen.contains("filePreflightRequested"))
        assertFalse(screen.contains("showConflictForFile"))
        for (theme in listOf("InstallMaterial.kt", "InstallMiuix.kt")) {
            assertFalse(ui("screen/install/$theme").contains("InstallOriginalImageSection(uiState, actions)"))
        }
    }

    @Test fun selectedFileNeverResumesAsADurableFlashOrShowsFlashActions() {
        val screen = ui("screen/flash/FlashScreen.kt")
        assertTrue(screen.contains("flashIt is FlashIt.FlashBoot && flashIt.boot == null"))
        assertTrue(screen.contains("!directInstall || flashOutcome != FlashOutcome.WRITTEN"))
        assertTrue(screen.contains("result.outcome == FlashOutcome.SAVED_ONLY"))
        assertTrue(screen.contains("resources.getString(R.string.install_image_saved, result.outputPath)"))
        assertTrue(screen.contains("showRebootAction = !patchOnly && result.showReboot"))
        assertTrue(screen.contains("canRestore = !patchOnly && progress.canRestore"))
        for (theme in listOf("FlashMaterial.kt", "FlashMiuix.kt")) {
            val body = ui("screen/flash/$theme")
            assertTrue(body.contains("FlashStatusBanner(state)"))
            assertTrue(body.contains("state.showRebootAction"))
            assertTrue(body.contains("state.canSaveBoot"))
        }
    }

    @Test fun previewErrorsRetainTheStableCodeWithoutAskingForPairing() {
        val steps = ui("screen/install/InstallSteps.kt")
        assertFalse(steps.contains("R.string.install_kernel_pairing_required"))
        assertFalse(steps.contains("InstallPairedBootSection"))
        assertTrue(steps.contains("[\$stableCode]"))
    }

    @Test fun publishedImageReadsTheActualMediaStoreLocationAndCleansFailedExports() {
        val publisher = source("dev/maplesu/manager/BootCandidatePublisher.kt")
        assertTrue(publisher.contains("resolver.query(uri,"))
        assertTrue(publisher.contains("MediaStore.Downloads.DISPLAY_NAME"))
        assertTrue(publisher.contains("MediaStore.Downloads.RELATIVE_PATH"))
        assertTrue(publisher.contains("publishAll(context, linkedMapOf(name to Image(image, expectedSha256)))"))
        assertTrue(publisher.contains("if (!complete) rows.values.forEach { uri -> runCatching { resolver.delete(uri, null, null) } }"))
        assertTrue(publisher.contains("verify(context, uri, image.file.length(), image.sha256)"))
        assertTrue(publisher.indexOf("verify(context, uri,") < publisher.indexOf("val paths = rows.mapValues"))
    }
}
