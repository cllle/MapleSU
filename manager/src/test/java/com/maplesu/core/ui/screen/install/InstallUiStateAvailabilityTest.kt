package com.maplesu.core.ui.screen.install

import dev.maplesu.manager.installer.InstallPartition
import dev.maplesu.manager.installer.InstallTargetBlocker
import dev.maplesu.manager.installer.InstallTargetCodes
import dev.maplesu.manager.installer.InstallTargetDecision
import dev.maplesu.manager.installer.InstallTargetMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallUiStateAvailabilityTest {
    @Test fun automaticInstallKeepsTheObservedTargetWithoutRequiringAPreviewPlan() {
        for (carrier in listOf(InstallPartition.BOOT, InstallPartition.INIT_BOOT)) {
            val current = state(partition = carrier).copy(
                partitionOptions = listOf(InstallPartition.BOOT, InstallPartition.INIT_BOOT),
                observedCarrier = carrier.wire,
                availableCarriers = setOf("boot", "init_boot"),
            )
            assertEquals(UltraInstallRoute(carrier.wire), current.ultraRoute)
            assertTrue(current.canInstall)
            assertTrue(current.copy(bootFlow = null).canInstall)
            assertFalse(current.copy(targetSelectable = false).canInstall)
            val other = if (carrier == InstallPartition.BOOT) InstallPartition.INIT_BOOT else InstallPartition.BOOT
            assertEquals(UltraInstallRoute(other.wire), current.copy(partition = other).ultraRoute)
            assertFalse(current.copy(partition = other, availableCarriers = setOf(carrier.wire)).canInstall)
        }
    }

    private fun profile(target: KernelTarget? = KernelTarget("k22")) = BootFlowProfile(
        source = "boot.img",
        release = "6.6.89-android15-8-g7e1f3c083cc6-abogki467167594-4k",
        target = target,
    )

    private fun normalReady(partition: String = "init_boot") = InstallTargetDecision.Ready(
        target = InstallTargetMode.NORMAL,
        path = dev.maplesu.manager.installer.InstallPatchPath.NORMAL_KSU_LKM,
        workMode = dev.maplesu.manager.workmode.WorkMode.KSU,
        partition = partition,
        kmi = "android15-6.6",
        kmiArtifact = "android15-6.6_kernelsu.ko",
        kmiCatalogKnown = true,
    )

    private fun normalBlocked() = InstallTargetDecision.Blocked(
        target = InstallTargetMode.NORMAL,
        code = InstallTargetCodes.KMI_NOT_IN_MATRIX,
        blocker = InstallTargetBlocker.KMI_NOT_IN_MATRIX,
        detail = "kernel not in matrix",
        kmi = "android15-6.6",
    )

    private fun state(
        method: InstallMethod? = InstallMethod.DirectInstall,
        target: KernelTarget? = KernelTarget("k22"),
        options: List<InstallMethod> = listOf(InstallMethod.DirectInstall),
        mode: InstallTargetMode = InstallTargetMode.ULTRA,
        partition: InstallPartition = InstallPartition.INIT_BOOT,
        normalDecision: InstallTargetDecision? = null,
        targetSelectable: Boolean = true,
        directAccessReady: Boolean = true,
        sourceLoading: Boolean = false,
    ) = InstallUiState(
        installMethod = method,
        installMethodOptions = options,
        bootFlow = profile(target),
        targetMode = mode,
        partition = partition,
        partitionOptions = dev.maplesu.manager.installer.InstallPartitionPolicy.optionsFor(mode),
        normalDecision = normalDecision,
        targetSelectable = targetSelectable,
        directAccessReady = directAccessReady,
        sourceLoading = sourceLoading,
    )

    // ── Ultra：boot 读取真实内核，init_boot 在执行时选择 KMI 组件 ──────────────

    @Test
    fun nextIsAvailableOnceBootAndKernelVersionAreChosen() {
        assertTrue(state().canInstall)
    }

    @Test fun vivoSingleImageKeepsInstallationAvailableInBothModes() {
        assertTrue(state().copy(vivoDevice = true).canInstall)
        assertTrue(state(mode = InstallTargetMode.NORMAL, normalDecision = normalReady())
            .copy(vivoDevice = true).canInstall)
        assertTrue(state(mode = InstallTargetMode.NORMAL, partition = InstallPartition.BOOT,
            normalDecision = normalReady("boot")).copy(vivoDevice = true).canInstall)
        assertFalse(state(targetSelectable = false).copy(vivoDevice = true).canInstall)
    }

    @Test
    fun nextIsDisabledForBothModesWhileSelectionsAreLocked() {
        assertFalse(state(targetSelectable = false).canInstall)
        assertFalse(
            state(
                mode = InstallTargetMode.NORMAL,
                normalDecision = normalReady(),
                targetSelectable = false,
            ).canInstall,
        )
    }

    @Test
    fun targetInformationWaitsForAnAvailableInstallationSource() {
        assertTrue(state().hasInstallSource)
        assertFalse(state(method = null).hasInstallSource)
        val emptyFile = InstallMethod.SelectFile(summary = "")
        assertFalse(state(method = emptyFile, options = listOf(emptyFile)).hasInstallSource)
        assertFalse(state(options = emptyList()).hasInstallSource)
    }

    @Test
    fun currentPartitionRespectsThePageLifecycleAndSourceSelection() {
        assertFalse(state(directAccessReady = false).hasInstallSource)
        assertFalse(state(directAccessReady = false).canInstall)
        assertFalse(state(sourceLoading = true).hasInstallSource)
        assertFalse(state(sourceLoading = true).canInstall)
    }

    @Test
    fun nextDoesNotTreatAReadingOrFailedPreviewAsAnExecutionVerdict() {
        for (partition in listOf(InstallPartition.BOOT, InstallPartition.INIT_BOOT)) {
            val selected = state(target = null, partition = partition)
            assertTrue(selected.canInstall)
            assertTrue(selected.copy(bootFlow = InstallBootFlow.reading("chosen.img", "")).canInstall)
            assertTrue(selected.copy(bootFlow = InstallBootFlow.failed("chosen.img", "",
                "ULTRA_KERNEL_GENERATE_SITE_COVERAGE")).canInstall)
        }
    }

    @Test
    fun nextIsDisabledWhenTheChosenBootHasNoUri() {
        val method = InstallMethod.SelectFile(summary = "")
        assertFalse(
            state(method = method, options = listOf(method)).canInstall,
        )
    }

    @Test
    fun nextIsDisabledWhenTheMethodIsNotOffered() {
        assertFalse(
            state(method = InstallMethod.DownloadFile(summary = "")).canInstall,
        )
    }

    // ── Normal：由"设备 KMI × 目标分区"的纯函数结论决定 ──────────────────────

    @Test
    fun normalDirectInstallIsAvailableWhenTheDecisionIsReady() {
        // 直装 + Normal：不需要文件，也不需要 Ultra 的内核版本目标。
        assertTrue(
            state(
                method = InstallMethod.DirectInstall,
                target = null,
                mode = InstallTargetMode.NORMAL,
                normalDecision = normalReady(),
            ).canInstall,
        )
    }

    @Test
    fun normalDefersTheKernelVerdictToTheExecutionSource() {
        assertTrue(
            state(
                method = InstallMethod.DirectInstall,
                target = null,
                mode = InstallTargetMode.NORMAL,
                normalDecision = normalBlocked(),
            ).canInstall,
        )
    }

    @Test
    fun pageAvailabilityDoesNotTurnAnExecutionFailureIntoSuccess() {
        val decision = dev.maplesu.manager.installer.InstallTargetDispatch.decide(
            dev.maplesu.manager.installer.InstallTargetRequest(
                target = InstallTargetMode.NORMAL,
                source = dev.maplesu.manager.installer.InstallSourceKind.CURRENT_PARTITION,
                partition = "init_boot",
                kernelRelease = "unreadable",
            ),
        )
        assertTrue(decision is InstallTargetDecision.Blocked)
        assertEquals(InstallTargetCodes.KMI_UNPARSEABLE, (decision as InstallTargetDecision.Blocked).code)
        assertTrue(state(mode = InstallTargetMode.NORMAL, normalDecision = decision).canInstall)
    }

    @Test fun normalFileDoesNotBindTheChosenKmiToTheCurrentDevice() {
        for (deviceRelease in listOf("", "6.1.99-android14-11", "6.6.89-android15-8")) {
            val decision = dev.maplesu.manager.installer.InstallTargetDispatch.decide(
                dev.maplesu.manager.installer.InstallTargetRequest(
                    target = InstallTargetMode.NORMAL,
                    source = dev.maplesu.manager.installer.InstallSourceKind.IMAGE,
                    partition = "init_boot",
                    kernelRelease = deviceRelease,
                    availableKmis = listOf("android15-6.6"),
                ),
            )
            assertTrue(decision is InstallTargetDecision.Ready)
            assertEquals(null, (decision as InstallTargetDecision.Ready).kmi)
            assertEquals("init_boot", decision.partition)
        }
    }

    @Test
    fun normalStillRequiresAnUriForTheChosenFile() {
        val method = InstallMethod.SelectFile(summary = "")
        assertFalse(
            state(
                method = method,
                options = listOf(method),
                target = null,
                mode = InstallTargetMode.NORMAL,
                normalDecision = normalReady(),
            ).canInstall,
        )
    }

    @Test
    fun ultraIgnoresTheNormalDecision() {
        assertTrue(
            state(target = null, mode = InstallTargetMode.ULTRA, partition = InstallPartition.BOOT,
                normalDecision = normalReady()).canInstall,
        )
    }

    // ── 分区选择的可见性/固定语义 ───────────────────────────────────────────

    @Test
    fun ultraShowsASingleFixedPartitionAndNormalShowsBoth() {
        assertTrue(state(mode = InstallTargetMode.ULTRA).partitionFixed)
        assertFalse(state(mode = InstallTargetMode.NORMAL).partitionFixed)
    }

    @Test
    fun lenovoSigningKeepsTheSelectedNormalPartitionAndDoesNotGateOnFingerprint() {
        for (partition in listOf(InstallPartition.INIT_BOOT, InstallPartition.BOOT)) {
            val selected = state(mode = InstallTargetMode.NORMAL, partition = partition,
                normalDecision = normalReady(partition.wire)).copy(
                lenovoSigning = dev.maplesu.manager.installer.LenovoSigningOptions(enabled = true))
            assertTrue(selected.canInstall)
            assertFalse(selected.partitionFixed)
        }
    }
}
