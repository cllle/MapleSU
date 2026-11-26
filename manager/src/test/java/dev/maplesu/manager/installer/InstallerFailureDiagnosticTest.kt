package dev.maplesu.manager.installer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class InstallerFailureDiagnosticTest {
    @Test fun serviceCauseIsNotHiddenByAGenericRootWrapper() {
        assertEquals("INSTALLER_SERVICE_FRAMEWORK_UNAVAILABLE", installerFailureDiagnostic(
            IllegalStateException("INSTALLER_ROOT_REQUIRED",
                IllegalStateException("INSTALLER_SERVICE_FRAMEWORK_UNAVAILABLE"))))
    }

    @Test fun sourceReadFailureSurvivesTheGenericFlashResult() {
        assertEquals("INSTALLER_SOURCE_READ_FAILED", installerFailureDiagnostic(
            IllegalStateException("INSTALLER_SOURCE_READ_FAILED", IOException("read failed: EACCES"))))
    }

    @Test fun stableCauseIsFoundInsideABinderWrapper() {
        assertEquals("INSTALLER_IMAGE_CHANGED", installerFailureDiagnostic(
            IllegalStateException("Remote failure", IllegalStateException("INSTALLER_IMAGE_CHANGED"))))
    }

    @Test fun recordedTransactionCodeIsRetained() {
        assertEquals("INSTALLER_TRANSACTION_PENDING", installerFailureDiagnostic(null, "INSTALLER_TRANSACTION_PENDING"))
    }

    @Test fun modeSwitchAndKsuFailuresRetainTheirStableCodes() {
        for (code in listOf("MODE_SWITCH_ROOT_SESSION_UNAVAILABLE", "KSU_PATCH_FAILED",
                "KSU_INSTALL_PARTITION_INVALID", "PRODUCE_KSU_UNAVAILABLE",
                "HIDDEN_SWITCH_CONFIRMATION_REQUIRED", "SUPERUSER_BACKEND_UNAVAILABLE")) {
            assertEquals(code, installerFailureDiagnostic(null, "$code:private details"))
        }
    }

    @Test fun legacyKsudFailureRetainsItsExactStableCode() {
        assertEquals("PRODUCE_KSud_FAILED", installerFailureDiagnostic(null, "PRODUCE_KSud_FAILED:exit 1"))
        assertEquals("PRODUCE_KSud_FAILED", installerFailureDiagnostic(
            IllegalStateException("PRODUCE_KSud_FAILED:private command details")))
    }

    @Test fun legacyCodeDoesNotAllowOtherMixedCaseDetails() {
        assertEquals("INSTALLER_OPERATION_FAILED", installerFailureDiagnostic(null, "PRODUCE_private_DETAILS"))
        assertEquals("INSTALLER_OPERATION_FAILED", installerFailureDiagnostic(null, "PRODUCE_KSud_FAILED extra"))
    }

    @Test fun untrustedDetailsAndCredentialSuffixesAreNotExposed() {
        assertEquals("INSTALLER_OPERATION_FAILED", installerFailureDiagnostic(IOException("secret /data/private")))
        assertEquals("ULTRA_KERNEL_ALL_VARIANTS_FAILED", installerFailureDiagnostic(
            IllegalStateException("ULTRA_KERNEL_ALL_VARIANTS_FAILED:private template details")))
    }
}
