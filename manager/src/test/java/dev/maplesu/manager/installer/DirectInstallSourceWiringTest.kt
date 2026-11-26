package dev.maplesu.manager.installer

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Complements the executed flow tests: guards the production call sites that select its input. */
class DirectInstallSourceWiringTest {
    private fun source(path: String) = File("src/main/java/$path").readText()

    @Test fun failedComponentResolutionBeforePrepareDoesNotQueryRootOrOfferRecovery() {
        val cli = source("com/maplesu/core/ui/util/KsuCli.kt")
        val failure = cli.substringAfter("Log.w(TAG, \"Stock installation failed\", error)")
            .substringBefore("private fun copyInstallerImage(")
        assertTrue(failure.contains("val status = if (nativePrepareSubmitted == false) null else runCatching"))
        assertTrue(failure.contains("if (nativePrepareSubmitted == false) FlashOutcome.FAILED else FlashOutcome.RECOVERY_REQUIRED"))
    }

    @Test fun bothInstallModesResolveAnOwnedOtherCarrierFromTheActualPreImage() {
        val prepare = source("dev/maplesu/manager/installer/NativeInstallTransaction.kt")
            .substringAfter("fun prepare(").substringBefore("fun openImage(")
        val ownership = prepare.substringAfter("// Resolve a carrier change once,")
            .substringBefore("if (record.getBoolean(\"restoreOriginal\"))")
        assertTrue(ownership.contains("if (operation == \"INSTALL\" && !restoreOriginal)"))
        assertFalse(ownership.contains("targetMode == \"LITE\""))
        assertTrue(ownership.contains("val image = pre[other]"))
        assertTrue(ownership.contains("recovery.generatedKernelWithDeployment(image, lineage)"))
        assertTrue(ownership.contains("recovery.installedSpec(image, lineage)"))
        assertTrue(prepare.contains("val captured = capture(withSigning(observed,"))
    }

    @Test fun selectingDirectInstallDoesNotInferRestorationFromManagerIdentity() {
        val screen = source("com/maplesu/core/ui/screen/install/InstallScreen.kt")
        val select = screen.substringAfter("onSelectMethod = { method ->").substringBefore("onSelectBootImage =")
        assertFalse(select.contains("managerInstalled != true || migrationEntry"))
        assertTrue(select.contains("restoreOriginalImage = false"))
        assertFalse(screen.contains("StockInstallPreflight.inspect("))
        assertFalse(screen.contains("restoreOriginalForInstall"))
        assertTrue(screen.contains("restoreOriginalImage = restoreOriginalImage"))
    }

    @Test fun allBootEntryDefaultsUseTheChosenImageOrCurrentPre() {
        val cli = source("com/maplesu/core/ui/util/KsuCli.kt")
        val requests = source("com/maplesu/core/ui/screen/flash/FlashUtils.kt")
        assertEquals(2, Regex("restoreOriginalImage: Boolean = false").findAll(cli).count())
        assertEquals(2, Regex("restoreOriginalImage: Boolean = false").findAll(requests).count())
        val upgrade = cli.substringAfter("internal fun installUltraKernelUpdate(").substringBefore("private class InstallBuildStopped")
        assertTrue(upgrade.contains("restoreOriginal = false"))
    }

    @Test fun normalRepatchUsesTheProducerWithoutAManagerOrMarkerGate() {
        val cli = source("com/maplesu/core/ui/util/KsuCli.kt")
        val producer = source("dev/maplesu/manager/ksu/KsuInstallProducer.kt")
        assertTrue(cli.contains("producer.produceCandidate(source"))
        assertFalse(cli.contains("allowKsuRepatch"))
        assertFalse(producer.contains("PRODUCE_SOURCE_ALREADY_PATCHED"))
        assertFalse(producer.contains("confirmedKsuRepatch"))
    }

    @Test fun explicitStockRestorationKeepsTheRootOwnedRestoreAction() {
        val cli = source("com/maplesu/core/ui/util/KsuCli.kt")
        val restore = cli.substringAfter("fun restoreBoot(").substringBefore("fun installBoot(")
        assertTrue(restore.contains("InstallerServiceClient.restoreStockImages(ksuApp, taskId)"))
        val preflight = source("dev/maplesu/manager/installer/StockInstallPreflight.kt")
        assertTrue(preflight.contains("fun discoverTarget("))
        assertFalse(preflight.contains("fun inspect("))
        assertFalse(preflight.contains("productionBaselineProvisioner"))
    }

    @Test fun ultraDirectInstallRechecksTheCarrierAndPreservesTheExplicitTarget() {
        val screen = source("com/maplesu/core/ui/screen/install/InstallScreen.kt")
        assertTrue(screen.contains("val requestedPartition = selectedPartition.wire"))
        assertTrue(screen.contains("resolvedUltraInstallAction(ultraAction, requestedPartition, null"))

        val cli = source("com/maplesu/core/ui/util/KsuCli.kt")
        val selection = cli.substringAfter("val discoveredTarget =").substringBefore("val partitionResolution =")
        assertTrue(selection.contains("StockInstallPreflight.discoverTarget"))
        assertTrue(selection.contains("partition.isNullOrBlank()"))
        assertTrue(selection.contains("requestedTarget == InstallTargetMode.NORMAL"))
        assertFalse(selection.contains("installedCarrierPartition("))
        assertFalse(selection.contains("capturePairedBoot"))
        assertFalse(selection.contains("initBootArtifact("))
        assertTrue(cli.contains("partition = resolvedPartition"))
        assertTrue(cli.contains("requestedTarget, requireNotNull(resolvedPartition)"))
        assertTrue(cli.contains("initBootArtifact = ultraInitBootArtifact"))
        assertTrue(cli.contains("resolveInitBootArtifact = if (bootUri != null) null else"))
        val prepare = source("dev/maplesu/manager/installer/NativeInstallTransaction.kt")
            .substringAfter("fun prepare(").substringBefore("fun openImage(")
        assertTrue(prepare.contains("record.put(\"restoreOriginal\", true).put(\"carrierRestoration\", other)"))

        val client = source("dev/maplesu/manager/installer/InstallerServiceClient.kt")
        assertTrue(client.contains("fun installedCarrierPartition(context: Context, observed: InstallerSnapshot): String?"))
        assertTrue(client.contains("installedCarrierPartition(context, observed) ?: \"boot\""))
        assertTrue(client.contains("recovery.installedSpec(image, receipts) != null"))
        assertTrue(client.contains("recovery.generatedKernelWithDeployment(image, receipts) != null"))
    }

    @Test fun standardFileDispatchContainsOnlyTheImageAndKmiChoice() {
        val screen = source("com/maplesu/core/ui/screen/install/InstallScreen.kt")
        val requests = source("com/maplesu/core/ui/screen/flash/FlashUtils.kt")
        val cli = source("com/maplesu/core/ui/util/KsuCli.kt")
        for (field in listOf("pairedBootSnapshot", "pairedBootUri", "pairedSourceSha256", "pairedBootSha256")) {
            assertFalse(screen.contains(field))
            assertTrue(requests.contains(field + " = flashIt." + field))
            assertTrue(cli.contains(field + " = " + field))
        }
        val selected = screen.substringAfter("val request = when (method)").substringBefore("InstallMethod.DirectInstall ->")
        assertTrue(selected.contains("boot = image"))
        assertTrue(selected.contains("lkm = selectedLkm"))
        assertTrue(screen.contains("initBootKmi?.let { LkmSelection.KmiString(it) } ?: LkmSelection.KmiNone"))
        assertTrue(cli.contains("initBootKmi = (lkm as? LkmSelection.KmiString)?.value"))
        assertFalse(screen.contains("InstallerServiceClient."))
        // Explicit historical callers retain a local compatibility path, outside the standard UI.
        val explicit = cli.substringAfter("if (pairedBootUri != null && explicitArtifact == null)")
            .substringBefore("onStage(InstallStage.PATCHING)")
        assertTrue(explicit.contains("SelectedBootPairInput.Identity("))
        assertTrue(explicit.contains("sourceSha, requireNotNull(pairedBootSha256)"))
        assertTrue(explicit.contains("SelectedBootPairInput.use(source, input"))
        assertTrue(explicit.contains("UltraKernelFlow.initBootArtifact(ksuApp, paired)"))
        assertTrue(explicit.contains("buildPatched(source, recoveredDeployment, artifact)"))
        assertFalse(explicit.contains("InstallerServiceClient."))
        assertTrue(cli.contains("bootUri != null && selectedImageOnly && pairedBootSnapshot == null"))
    }

    @Test fun neitherThemeOrPreviewOffersACompanionOrRootPrecheck() {
        val screen = source("com/maplesu/core/ui/screen/install/InstallScreen.kt")
        assertTrue(screen.contains("var sourceRequest by rememberSaveable"))
        assertTrue(screen.contains("LaunchedEffect(bootSourceKey, sourceRequest)"))
        for (file in listOf("InstallScreen.kt", "InstallMaterial.kt", "InstallMiuix.kt", "InstallSteps.kt", "InstallUiState.kt",
            "InstallBootFlow.kt")) {
            val text = source("com/maplesu/core/ui/screen/install/" + file)
            for (retired in listOf("pairedBoot", "PairedBoot", "SelectedBootPair", "install_paired_boot_")) {
                assertFalse("$file: $retired", text.contains(retired))
            }
        }
        assertFalse(screen.contains("RootExec."))
        assertFalse(screen.contains("managerRootAvailable("))
        assertFalse(screen.contains("StockInstallPreflight"))
        val probes = source("com/maplesu/core/ui/screen/install/InstallBootFlow.kt").substringAfter("internal fun probeChosenBoot(")
        assertFalse(probes.contains("generation.plan("))
        assertFalse(probes.contains("InstallBootFlow.fromFacts("))
        assertFalse(probes.contains("initBootArtifact("))
    }

    @Test fun defaultInitBootBuildResolvesTheExplicitKmiBeforeAnyLegacyPairPath() {
        val cli = source("com/maplesu/core/ui/util/KsuCli.kt")
        val component = cli.substringAfter("val componentArtifact by lazy {").substringBefore("val bridgeArtifact by lazy {")
        assertTrue(component.contains("val kmi = initBootKmi ?: initBootArtifact?.componentKmi"))
        assertTrue(component.contains("UltraKernelFlow.initBootArtifact(ksuApp, kmi)"))
        assertTrue(component.contains("ULTRA_INIT_KMI_COMPONENT_MISSING:"))
        assertTrue(component.contains("it.isKmiComponent && it.componentKmi == kmi"))
        assertFalse(component.contains("runCatching"))
        assertFalse(component.contains("InstallerServiceClient."))
        assertFalse(component.contains("pairedBootSnapshot == null"))
        val bridge = cli.substringAfter("val bridgeArtifact by lazy {").substringBefore("fun written()")
        assertTrue(bridge.trimStart().startsWith("if (componentArtifact != null) componentArtifact"))
        val build = cli.substringAfter("val selectedArtifact = explicitArtifact ?: bridgeArtifact")
            .substringBefore("val repository =")
        assertTrue(build.contains("selectedArtifact?.isKmiComponent != true"))
    }

    @Test fun normalFileKmiUsesTheBundledDirectoryWithoutOpeningRoot() {
        val screen = source("com/maplesu/core/ui/screen/install/InstallScreen.kt")
        val list = screen.substringAfter("LaunchedEffect(targetMode) {").substringBefore("LifecycleResumeEffect(Unit)")
        assertTrue(list.contains("InstallTargetMode.NORMAL -> packagedKmiCatalog(context).orEmpty()"))
        assertTrue(list.contains("UltraKernelFlow.availableInitBootKmis(context)"))
        assertTrue(screen.contains("rememberSaveable(targetMode)"))
        val selection = screen.substringAfter("val selectedLkm =").substringBefore("val request =")
        assertTrue(selection.contains("selectedPartition == InstallPartition.INIT_BOOT"))
        assertTrue(selection.contains("mode == InstallTargetMode.ULTRA || method is InstallMethod.SelectFile"))
        val steps = source("com/maplesu/core/ui/screen/install/InstallSteps.kt")
        assertTrue(steps.contains("state.targetMode == InstallTargetMode.ULTRA || state.installMethod is InstallMethod.SelectFile"))
        val cli = source("com/maplesu/core/ui/util/KsuCli.kt")
        val catalog = cli.substringAfter("internal fun packagedKmiCatalog(").substringBefore("private fun deviceKernelRelease()")
        assertTrue(catalog.contains("context.applicationInfo.nativeLibraryDir"))
        assertTrue(catalog.contains("ProcessSuExec.exec("))
        assertTrue(catalog.contains("listOf(daemon.path, \"boot-info\", \"supported-kmis\")"))
        assertFalse(catalog.contains("getRootShell("))
        assertFalse(catalog.contains("InstallerServiceClient"))
        val policy = cli.substringAfter("val policy = dev.maplesu.manager.ksu.KsuInstallPolicy.evaluate(")
            .substringBefore("if (policy !is")
        assertTrue(policy.contains("targetDecision.kmi ?: (lkm as? LkmSelection.KmiString)?.value"))
        assertTrue(policy.contains("directInstall = bootUri == null"))
    }

    /** Current-source display contract only; this does not execute Compose or ActivityResult. */
    @Test fun kernelFreePreviewDefersItsComponentRequirementToTheBuilder() {
        val flow = source("com/maplesu/core/ui/screen/install/InstallBootFlow.kt")
        val initBoot = flow.substringAfter("if (carrier == dev.maplesu.manager.installer.InstallPartition.INIT_BOOT) {")
            .substringBefore("val release = try {")
        assertTrue(initBoot.contains("return BootFlowProfile(source = source"))
        assertFalse(initBoot.contains("initBootArtifact("))
        assertFalse(initBoot.contains("ULTRA_INIT_PAIRED_BOOT_REQUIRED"))
        val steps = source("com/maplesu/core/ui/screen/install/InstallSteps.kt")
        val hint = steps.substringAfter("val imageHint =").substringBefore("return reason + imageHint")
        assertTrue(hint.contains("stableCode.startsWith(\"ULTRA_INIT_\")"))
        assertTrue(hint.contains("stableCode.startsWith(\"ULTRA_KERNEL_INIT_BOOT_\")"))
        assertFalse(hint.contains("stableCode.startsWith(\"ULTRA_KERNEL_\")"))
        assertTrue(hint.contains("\"\" else"))
        assertTrue(hint.contains("stringResource(R.string.install_kernel_boot_image_hint)"))
    }
}
