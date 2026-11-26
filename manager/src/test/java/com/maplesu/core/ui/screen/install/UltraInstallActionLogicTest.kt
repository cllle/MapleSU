package com.maplesu.core.ui.screen.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UltraInstallActionLogicTest {
    private val both = setOf("boot", "init_boot")

    @Test fun ordinaryInstallAutomaticallyKeepsTheInstalledCarrierWithoutForcingARepatch() {
        for (carrier in both) {
            assertEquals(UltraInstallRoute(carrier), ultraInstallRoute(UltraInstallAction.AUTOMATIC,
                carrier, carrier, both, true, null))
            assertFalse(resolvedUltraInstallAction(UltraInstallAction.AUTOMATIC,
                carrier, carrier, true).forceRepatch)
        }
    }

    @Test fun aDifferentExplicitTargetSelectsTheExistingCarrierRestorationPath() {
        val action = resolvedUltraInstallAction(UltraInstallAction.AUTOMATIC, "init_boot", "boot", true)
        assertEquals(UltraInstallAction.SWITCH_CARRIER, action)
        assertTrue(action.forceRepatch)
        assertTrue(action.restoresOtherCarrier)
        assertEquals(UltraInstallRoute("init_boot"), ultraInstallRoute(UltraInstallAction.AUTOMATIC,
            "init_boot", "boot", both, true, null))
    }

    @Test fun anImageFileAlwaysUsesItsOwnTargetWithoutRestoringLivePartitions() {
        val action = resolvedUltraInstallAction(UltraInstallAction.AUTOMATIC, "init_boot", "boot", false)
        assertFalse(action.restoresOtherCarrier)
        assertEquals(UltraInstallRoute("init_boot"), ultraInstallRoute(UltraInstallAction.AUTOMATIC,
            "init_boot", "boot", both, false, null))
    }

    @Test fun firstPatchKeepsTheExplicitObservedTarget() {
        assertEquals(UltraInstallRoute("boot"), ultraInstallRoute(UltraInstallAction.FIRST_PATCH,
            "boot", null, both, true, "BRIDGE_MISSING"))
        assertFalse(UltraInstallAction.FIRST_PATCH.forceRepatch)
    }

    @Test fun updatingInitBootStaysOnTheInstalledCarrierAndForcesPatching() {
        assertEquals(UltraInstallRoute("init_boot"), ultraInstallRoute(UltraInstallAction.REPATCH_SDK,
            "boot", "init_boot", both, true, null))
        assertTrue(UltraInstallAction.REPATCH_SDK.forceRepatch)
    }

    @Test fun updatingBootStaysOnBootDespiteAnInitBootSelection() {
        assertEquals(UltraInstallRoute("boot"), ultraInstallRoute(UltraInstallAction.REPATCH_SDK,
            "init_boot", "boot", both, true, "BRIDGE_MISSING"))
    }

    @Test fun unknownInstalledCarrierNeverBecomesAnUpdateFallback() {
        assertEquals("INSTALLER_CARRIER_UNCONFIRMED", ultraInstallRoute(UltraInstallAction.REPATCH_SDK,
            "boot", null, both, true, null).blocker)
    }

    @Test fun restoringInitBootDoesNotRequireANewBridge() {
        assertEquals(UltraInstallRoute("init_boot"), ultraInstallRoute(UltraInstallAction.RESTORE,
            "boot", "init_boot", both, true, "BRIDGE_MISSING"))
        assertFalse(UltraInstallAction.RESTORE.forceRepatch)
    }

    @Test fun aFileSourceDoesNotRouteToLiveRestoration() {
        assertEquals("INSTALLER_RESTORE_REQUIRES_CURRENT_PARTITION", ultraInstallRoute(UltraInstallAction.RESTORE,
            "init_boot", null, null, false, null).blocker)
    }

    @Test fun carrierSwitchRequiresTheOtherCarrierRestorationTransaction() {
        assertEquals(UltraInstallRoute("init_boot"), ultraInstallRoute(UltraInstallAction.SWITCH_CARRIER,
            "init_boot", "boot", both, true, null))
        assertTrue(UltraInstallAction.SWITCH_CARRIER.forceRepatch)
        assertTrue(UltraInstallAction.SWITCH_CARRIER.restoresOtherCarrier)
        assertFalse(UltraInstallAction.FIRST_PATCH.restoresOtherCarrier)
    }

    @Test fun firstPatchDoesNotSilentlyLeaveAnotherInstalledCarrier() {
        assertEquals("INSTALLER_CARRIER_SWITCH_REQUIRED", ultraInstallRoute(UltraInstallAction.FIRST_PATCH,
            "init_boot", "boot", both, true, null).blocker)
    }

    @Test fun selectingTheCurrentCarrierIsNotASwitch() {
        assertEquals("INSTALLER_CARRIER_SWITCH_TARGET_UNCHANGED", ultraInstallRoute(UltraInstallAction.SWITCH_CARRIER,
            "boot", "boot", both, true, null).blocker)
    }

    @Test fun anAbsentPartitionNeverRoutesToAnotherCarrier() {
        assertEquals("INSTALL_PARTITION_UNSUPPORTED", ultraInstallRoute(UltraInstallAction.FIRST_PATCH,
            "init_boot", null, setOf("boot"), true, null).blocker)
    }

    @Test fun initBootPreservesTheActualBridgeFailure() {
        assertEquals("ULTRA_INIT_ASSET_MANIFEST_CHANGED", ultraInstallRoute(UltraInstallAction.REPATCH_SDK,
            "init_boot", "init_boot", both, true, "ULTRA_INIT_ASSET_MANIFEST_CHANGED").blocker)
    }

    @Test fun filePatchingUsesItsOwnCarrierWithoutInventingDeviceTopology() {
        assertEquals(UltraInstallRoute("init_boot"), ultraInstallRoute(UltraInstallAction.REPATCH_SDK,
            "init_boot", null, setOf("boot"), false, null))
    }

    @Test fun filePatchingResolvesTheKmiComponentDuringExecution() {
        assertEquals(UltraInstallRoute("init_boot"), ultraInstallRoute(UltraInstallAction.FIRST_PATCH,
            "init_boot", null, null, false, "ULTRA_INIT_PAIRED_BOOT_REQUIRED"))
    }
}
