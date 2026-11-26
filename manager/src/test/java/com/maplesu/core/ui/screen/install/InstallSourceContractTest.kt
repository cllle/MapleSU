package com.maplesu.core.ui.screen.install

import com.maplesu.core.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards unified installation, automatic stock recovery and the root-free file-export path. */
class InstallSourceContractTest {
    private fun source(name: String) = File("src/main/java/com/maplesu/core/ui/screen/install/$name.kt").readText()

    @Test fun installationHasNoActionSelectorOrRestoreOnlyNavigation() {
        val screen = source("InstallScreen")
        assertTrue(screen.contains("val ultraAction = UltraInstallAction.AUTOMATIC"))
        assertFalse(screen.contains("FlashIt.FlashRestore"))
        assertFalse(screen.contains("onSelectUltraAction"))
        assertFalse(source("InstallUiState").contains("onSelectUltraAction"))
        val steps = source("InstallSteps")
        assertFalse(steps.contains("R.string.install_ultra_action"))
        assertFalse(steps.contains("R.string.settings_restore_stock_image"))
        assertTrue(steps.contains("R.string.install_ultra_current_carrier"))
        for (theme in listOf("InstallMaterial", "InstallMiuix")) {
            assertTrue(source(theme).contains("InstallUltraStatusSection(uiState"))
            assertFalse(source(theme).contains("InstallUltraActionSection("))
        }
        val settings = File("src/main/java/com/maplesu/core/ui/screen/settings/UltraEnvironmentSettings.kt").readText()
        assertTrue(settings.contains("fun removeEnvironment() = uninstall(permanent = true)"))
    }

    @Test
    fun newMigrationUsesTheSameInstallPageAndKeepsBackEnabled() {
        val migration = File("src/main/java/dev/maplesu/manager/workmode/ui/MigrationScreen.kt").readText()
            .substringAfter("if (migrationId.isBlank()) {").substringBefore("val viewModel")
        assertTrue(migration.contains("InstallScreen("))
        assertTrue(migration.contains("InstallTargetMode.fromWorkMode(initialTarget)"))
        assertTrue(migration.contains("migrationEntry = true"))
        assertTrue(migration.contains("onBackEnabledChange(true)"))
        assertTrue(migration.contains("return"))
    }

    @Test
    fun currentPartitionDefersRootAndPartitionAccessUntilSubmission() {
        val screen = source("InstallScreen")
        val options = screen.substringAfter("val installMethodOptions").substringBefore("fun showMessage")
        assertTrue(options.contains("add(InstallMethod.DirectInstall)"))
        assertFalse(options.contains("installerTarget != null"))
        assertTrue(screen.contains("if (!directInstallSuppressed) add(InstallMethod.DirectInstall)"))
        assertFalse(screen.contains("StockInstallPreflight"))
        assertFalse(screen.contains("managerRootAvailable("))
        assertTrue(screen.contains("directAccessReady = !directInstallSuppressed && pageResumed && existingRootConflictAccepted"))
    }

    @Test
    fun directInstallDoesNotResolveStockBeforeAnExplicitRestore() {
        val screen = source("InstallScreen")
        assertFalse(screen.contains("StockInstallPreflight.inspect("))
        assertFalse(screen.contains("restoreOriginalForInstall"))
        assertFalse(screen.contains("installerTarget"))
        assertFalse(screen.contains("StockImageUnavailableDialog("))
        assertFalse(screen.contains("CurrentPartitionConflictDialog("))
        assertTrue(screen.contains("restoreOriginalImage = restoreOriginalImage"))
    }

    @Test
    fun filePatchingNeedsNoRootOrStockRestorationPreflight() {
        val screen = source("InstallScreen")
        assertFalse(screen.contains("filePreflightRequested"))
        assertFalse(screen.contains("showConflictForFile"))
        val selected = screen.substringAfter("val request = when (method)")
            .substringBefore("InstallMethod.DirectInstall ->")
        assertTrue(selected.contains("boot = image"))
        assertFalse(selected.contains("restoreOriginalImage ="))
        assertFalse(selected.contains("originalManagerPackage ="))
        val next = screen.substringAfter("onNext = {").substringBefore("onRestoreOriginalImageChange")
        assertTrue(next.contains("onInstall()"))
        assertFalse(next.contains("StockInstallPreflight"))
    }

    @Test
    fun restorationChoicesUseInstalledManagerNamesWithoutLaunchingOrInferringTheActiveOne() {
        val managers = source("OriginalRootManager")
        assertTrue(managers.contains("MigrationPaths.ALL_MANAGER_PACKAGES"))
        assertTrue(managers.contains("getApplicationInfo(packageName, 0)"))
        assertFalse(managers.contains("startActivity"))
        val screen = source("InstallScreen")
        assertFalse(screen.contains("restoreOriginalImage = managerInstalled != true || migrationEntry"))
        assertTrue(screen.contains("restoreOriginalImage = false"))
        assertTrue(screen.contains("restoreOriginalImage = restoreOriginalImage"))
        assertTrue(screen.contains("originalManagerPackage = originalManagerPackage"))
        assertFalse(screen.contains("openOriginalRootManager"))
    }

    @Test
    fun bothThemesUseAdvancedRestoreOptionsAndRemoveTheResumeTaskCard() {
        listOf("InstallMaterial", "InstallMiuix").forEach { name ->
            val theme = source(name)
            assertFalse(theme.contains("InstallOriginalImageSection(uiState, actions)"))
            assertTrue(theme.contains("InstallSourceStatusSection(uiState)"))
            assertTrue(theme.contains("stringResource(state.directInstallLabel)"))
            assertFalse(theme.contains("InstallerTransactionEntry"))
        }
        val advanced = source("InstallAdvancedOptions")
        assertTrue(advanced.contains("if (state.installMethod == InstallMethod.DirectInstall)"))
        assertTrue(advanced.contains("OriginalImageOptions("))
        val dialogs = source("InstallSourceDialogs")
        assertTrue(dialogs.contains("SegmentedSwitchItem("))
        assertTrue(dialogs.contains("SwitchPreference("))
        assertTrue(dialogs.contains("SegmentedDropdownItem("))
        assertTrue(dialogs.contains("OverlayDropdownPreference("))
    }

    @Test
    fun directInstallKeepsOneNameForFirstInstallsUpdatesAndLegacyMigrationRoutes() {
        for (installed in listOf(null, false, true)) for (migration in listOf(false, true)) {
            val state = InstallUiState(InstallMethod.DirectInstall, listOf(InstallMethod.DirectInstall),
                managerInstalled = installed, migrationEntry = migration)
            assertEquals(R.string.direct_install, state.directInstallLabel)
        }
        val screen = source("InstallScreen")
        assertTrue(screen.contains("when (readInstallSignal(ModeRuntime.active))"))
        assertTrue(screen.contains("managerInstalled = managerInstalled"))
    }

    @Test fun homeNoLongerOffersASeparateMigrationCard() {
        for (name in listOf("HomeScreen", "HomeMiuix", "HomeMaterial")) {
            val home = File("src/main/java/com/maplesu/core/ui/screen/home/$name.kt").readText()
            assertFalse(home.contains("MigrationEntryCard("))
            assertFalse(home.contains("onMigrationOpenPage"))
        }
    }

    @Test
    fun installModeSelectionUsesTheSharedSheetWithoutCurrentModeBlocking() {
        val selection = source("InstallSteps").substringAfter("internal fun InstallTargetSelectionSection(")
            .substringBefore("internal fun InstallPartitionSection(")
        assertTrue(selection.contains("WorkModeTargetRow("))
        assertTrue(selection.contains("WorkModeTargetSheet("))
        assertTrue(selection.contains("selected = state.targetMode.workMode"))
        assertTrue(selection.contains("current = null"))
        assertTrue(selection.contains("InstallTargetMode.fromWorkMode(mode)"))
        assertFalse(selection.contains("Dropdown"))
        assertFalse(selection.contains("WorkModeSwitchSheet("))
    }
}
