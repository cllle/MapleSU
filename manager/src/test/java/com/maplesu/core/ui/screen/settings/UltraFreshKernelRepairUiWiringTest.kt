package com.maplesu.core.ui.screen.settings

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UltraFreshKernelRepairUiWiringTest {
    private fun source(path: String): String = listOf(File("src/main/java/$path"), File("manager/src/main/java/$path"))
        .first { it.isFile }.readText()

    private fun settings() = source("com/maplesu/core/ui/screen/settings/UltraEnvironmentSettings.kt")

    @Test fun freshRepairVisibilityUsesTheObservedKernelProof() {
        val text = settings()
        val panel = text.substringAfter("class UltraEnvironmentPanel").substringBefore("fun rememberUltraEnvironmentPanel")
        assertTrue(panel.contains("seed?.kernelRootVerified ?: initialSnapshot.kernelRootVerified"))
        assertTrue(panel.contains("val observation = UltraEnvironmentProbe.currentSnapshot(context)"))
        assertTrue(panel.contains("kernelRootVerified = observation.state == nextState && observation.kernelRootVerified"))
        assertTrue(panel.contains("kernelRootVerified = merged.kernelRootVerified"))
        val visibility = text.substringAfter("private val UltraEnvironmentPanel.canShowRepair:")
            .substringBefore("@Composable")
        assertTrue(visibility.contains("!hookEnvironment"))
        assertTrue(visibility.contains("state == UltraEnvironmentProbe.State.NotInstalled && kernelRootVerified"))
    }

    @Test fun bothThemesConfirmTheSameProductionRepairAction() {
        val text = settings()
        for (name in listOf("UltraEnvironmentSectionMiuix", "UltraEnvironmentRowMaterial")) {
            val row = text.substringAfter("fun $name(").substringBefore("\n@Composable")
            assertTrue("$name must use shared visibility", row.contains("if (panel.canShowRepair)"))
            assertTrue("$name must use shared confirmation", row.contains("rememberFaultRepairAction(panel)"))
        }
        assertTrue(text.contains("rememberConfirmDialog(onConfirm = { panel.repairEnvironment() })"))
        assertTrue(text.contains("withContext(Dispatchers.IO) { UltraEnvironmentUpgrade.repairEnvironment(context) }"))
    }

    @Test fun unknownCarrierAndExplicitRepatchStillReachTheImagePath() {
        val caller = source("com/maplesu/core/ui/util/KsuCli.kt")
        val condition = caller.substringAfter("val changesUltraCarrier =").substringBefore("var directRequest =")
        assertTrue(condition.contains("installedCarrier != resolvedPartition"))
        assertFalse(condition.contains("installedCarrier != null"))
        assertTrue(caller.contains("forceRepatch = forceRepatch || changesUltraCarrier"))
    }
}
