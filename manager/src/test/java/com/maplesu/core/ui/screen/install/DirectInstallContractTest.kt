package com.maplesu.core.ui.screen.install

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DirectInstallContractTest {
    private fun source(path: String) = File("src/main/java/$path").readText()
    private fun ui(path: String) = source("com/maplesu/core/ui/$path")

    @Test fun directInstallDefersRootAccessUntilExecution() {
        val screen = ui("screen/install/InstallScreen.kt")
        for (probe in listOf("rootGranted", "managerRootAvailable(", "RootExec.", "suPresent()",
            "rootAvailability.collectAsStateWithLifecycle", "!ModeRuntime.detectionComplete")) {
            assertFalse(probe, screen.contains(probe))
        }
        assertTrue(screen.contains("if (!directInstallSuppressed) add(InstallMethod.DirectInstall)"))
        assertTrue(screen.contains("directAccessReady = !directInstallSuppressed && pageResumed && existingRootConflictAccepted"))
    }

    @Test fun backPreviewKeepsTheConfirmedDisplayButNeverGrantsInstallationAccess() {
        val screen = ui("screen/install/InstallScreen.kt")
        val pause = screen.substringAfter("LifecycleResumeEffect(context, detectionRevision, hiddenRevision) {")
            .substringAfter("onPauseOrDispose {")
            .substringBefore("LaunchedEffect(sourceRequest")
        assertTrue(pause.contains("job.cancel()"))
        assertFalse("A lifecycle transition is not evidence of lost SU", pause.contains("rootGranted = false"))
        assertFalse(pause.contains("hasRoot = false"))
        assertTrue(screen.contains("if (!pageResumed || installInFlight || WorkModeSwitchSession.executing) return@onInstall"))
        assertTrue(screen.contains("directAccessReady = !directInstallSuppressed && pageResumed &&"))
    }

    @Test fun confirmedHiddenFactsStillSuppressTheOption() {
        val screen = ui("screen/install/InstallScreen.kt")
        assertTrue(screen.contains("HiddenFactsSignal.revision.collectAsStateWithLifecycle()"))
        assertTrue(screen.contains("remember(context, detectionRevision, hiddenRevision)"))
        assertTrue(screen.contains("HiddenStartupConfirm.masking(context)"))
        assertTrue(screen.contains("HiddenStartupConfirm.isPending(context)"))
        assertTrue(screen.contains("mapleModeKnown && HiddenStartupConfirm.current(context)"))
        assertFalse(screen.contains("!ModeRuntime.detectionComplete"))
        assertTrue(screen.contains("LifecycleResumeEffect(context, detectionRevision, hiddenRevision)"))
    }

    @Test fun directInstallationShowsItsLogAndUsesItsOwnVerifiedCandidate() {
        val screen = ui("screen/flash/FlashScreen.kt")
        assertTrue(screen.contains("val showLog = !patchOnly"))
        assertTrue(screen.contains("directInstall = directInstall"))
        assertTrue(screen.contains("UltraKernelBootExport.hasVerifiedCandidate(progress.result?.bootCandidate)"))
        assertTrue(screen.contains("UltraKernelBootExport.save(context.applicationContext, bootCandidate)"))
        val export = ui("util/UltraKernelInstallExport.kt")
        assertFalse("Saving must not search for an unrelated old image", export.contains("sortedByDescending"))
        assertTrue(export.contains("candidate.sha256"))
        assertTrue(export.contains("candidate.size"))
    }

    @Test fun returningToThePageRefreshesOnlyTheDisplayedKernelFacts() {
        val screen = ui("screen/install/InstallScreen.kt")
        assertTrue(screen.contains("ModeRuntime.detectionRevision.collectAsStateWithLifecycle()"))
        assertTrue(screen.contains("targetMode, detectionRevision, pageResumed)"))
        assertTrue(screen.contains("if (!pageResumed) return@LaunchedEffect"))
    }

    @Test fun runtimeChangesDoNotDiscardTheSelectedSource() {
        val screen = ui("screen/install/InstallScreen.kt")
        val read = screen.substringAfter("LaunchedEffect(bootSourceKey, sourceRequest, bootProbeKey")
            .substringBefore("val installMethodOptions")
        assertTrue(read.contains("if (!pageResumed) return@LaunchedEffect"))
        assertFalse(read.contains("installMethod = null"))
        assertFalse(read.contains("existingRootConflictAccepted = false"))
    }

    @Test fun aCrossModeDirectInstallAlsoStreamsItsProgressIntoTheLog() {
        val cli = ui("util/KsuCli.kt")
        val progress = cli.substringAfter("val progress: (String) -> Unit = { stage ->")
            .substringBefore("if (bootUri == null)")
        assertTrue(progress.contains("modeSwitchStepOf(stage, stage, stage)"))
        assertTrue(progress.contains("onStdout(ksuApp.getString(WorkModeStrings.stepRes(step)))"))
        assertTrue(progress.contains("ModeSwitchStep.REBOOT -> InstallStage.AWAITING_REBOOT"))
    }

    @Test fun successfulDirectInstallHasOnlyFixedBottomSaveAndRebootActions() {
        for (theme in listOf("FlashMaterial.kt", "FlashMiuix.kt")) {
            val screen = ui("screen/flash/$theme")
            assertTrue(theme, screen.contains("DirectInstallActions(state, actions, Modifier.align(Alignment.BottomCenter))"))
            val actions = screen.substringAfter("private fun DirectInstallActions(")
                .substringBefore("private fun FlashResultActions(")
            assertTrue(theme, actions.contains("Arrangement.SpaceBetween"))
            assertTrue(theme, actions.contains("state.canSaveBoot"))
            assertTrue(theme, actions.contains("state.showRebootAction"))
            assertTrue(theme, actions.contains("FloatingActionButton("))
            assertTrue(theme, actions.contains("actions.onReboot"))
            assertFalse(theme, actions.contains("install_boot_action_flash"))
            assertFalse(theme, actions.contains("actions.onRestore"))
            assertTrue(theme, screen.contains("if (state.directInstall && state.flashingStatus == FlashingStatus.SUCCESS) return"))
        }
    }
}
