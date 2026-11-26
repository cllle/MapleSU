package dev.maplesu.manager.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallerModeGatePolicyTest {
    @Test fun anAuthenticatedRootServiceMayWriteInBothScopes() {
        for (scope in InstallerModeGate.WriteScope.entries) {
            assertNull(scope.name, InstallerModeGate.refusal(0, scope))
        }
    }

    @Test fun anUnprivilegedProcessNeverBecomesRootThroughAModeLabel() {
        for (scope in InstallerModeGate.WriteScope.entries) {
            for (uid in listOf(-1, 1000, 2000, 10000, 110000)) {
                assertEquals("$scope:$uid", "INSTALLER_ROOT_REQUIRED", InstallerModeGate.refusal(uid, scope))
            }
        }
    }
}
