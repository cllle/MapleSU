package dev.maplesu.manager.installer

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Ordering guards for source selection; executable transport/IO fixtures are recorded separately. */
class DirectInstallPreparationContractTest {
    private fun source(path: String) = File("src/main/java/$path").readText()

    @Test fun selectingDirectInstallDoesNotStartAnotherPartitionOrRecoverySession() {
        val screen = source("com/maplesu/core/ui/screen/install/InstallScreen.kt")
        val page = screen.substringBefore("private fun probeInstallBoot(")
        assertFalse(page.contains("StockInstallPreflight"))
        assertFalse(page.contains("InstallerServiceClient.observeBootChain"))
        assertFalse(page.contains("installedCarrierPartition"))
        assertFalse(page.contains("UltraKernelFlow.initBootArtifact"))
        assertFalse(page.contains("installerTarget != null"))
        assertTrue(page.contains("else InstallPartition.ALLOWED"))
        assertTrue(page.contains("directAccessReady = hasRoot && pageResumed && existingRootConflictAccepted"))
    }

    @Test fun explicitTargetDoesNotRunTopologyDiscoveryOrDecodeRecoveryBeforePre() {
        val cli = source("com/maplesu/core/ui/util/KsuCli.kt")
        val planning = cli.substringAfter("val discoveredTarget =").substringBefore("var directRequest =")
        assertTrue(planning.contains("partition.isNullOrBlank()"))
        assertTrue(planning.contains("requestedTarget == InstallTargetMode.NORMAL"))
        assertTrue(planning.contains("service.getBootInstall(\"\")"))
        assertTrue(planning.indexOf("service.getBootInstall") < planning.indexOf("service.observeBootChain"))
        assertTrue(planning.contains("rethrowInstallerCancellation(error)"))
        assertTrue(planning.contains("Optional environment carrier verification failed; preparing installation"))
        assertFalse(planning.contains("installedCarrierPartition("))
        assertFalse(planning.contains("capturePairedBoot"))
        assertFalse(planning.contains("initBootArtifact("))

    }

    @Test fun carrierRestorationIsSelectedAfterImmutablePreCapture() {
        val native = source("dev/maplesu/manager/installer/NativeInstallTransaction.kt")
        val prepare = native.substringAfter("fun prepare(").substringBefore("fun openImage(")
        assertTrue(prepare.indexOf("val pre = captured.filterKeys") < prepare.indexOf("val ownsOther ="))
        assertTrue(prepare.contains("val other = if (partition == \"boot\") \"init_boot\" else \"boot\""))
        assertTrue(prepare.contains("record.put(\"restoreOriginal\", true).put(\"carrierRestoration\", other)"))
        assertTrue(prepare.contains("old.optBoolean(\"requestedRestoreOriginal\", old.optBoolean(\"restoreOriginal\")) == restoreOriginal"))
        val ownership = prepare.substringAfter("val ownsOther =").substringBefore("if (record.getBoolean")
        assertFalse(ownership.contains("catch"))
    }

    @Test fun initBootArtifactUsesThePreparedFinalBootSource() {
        val flow = source("dev/maplesu/manager/installer/StockInstallFlow.kt")
        assertTrue(flow.indexOf("onPrepared(prepared)") < flow.indexOf("resolveInitBootArtifact?.invoke(paired)"))
        assertTrue(flow.contains("it.openNativeInstallImage(taskId, \"boot\", meta)"))
        assertTrue(flow.contains("boot.getString(\"sourceSha256\")"))
        assertFalse(flow.contains("boot.getString(\"preSha256\")"))
        assertTrue(flow.contains("finally { pairedSource?.delete() }"))
        assertTrue(flow.contains("prepared.getBoolean(\"restoreOriginal\") && selectedImage == null"))
    }

    @Test fun sourceAndPreHashesRemainSeparateInTheNativeResponse() {
        val native = source("dev/maplesu/manager/installer/NativeInstallTransaction.kt")
        val response = native.substringAfter("private fun response(record:")
        assertTrue(response.contains("putString(\"preSha256\", row.sha256)"))
        assertTrue(response.contains("putString(\"sourceSha256\", if (record.optJSONObject(\"originals\")?.has(name) == true)"))
        assertTrue(response.contains("record.optJSONObject(\"originals\")?.optString(name).orEmpty() else row.sha256"))
    }
    @Test fun automaticCarrierMigrationUsesOnlyTheOwnedNonTargetSubset() {
        val native = source("dev/maplesu/manager/installer/NativeInstallTransaction.kt")
        val prepare = native.substringAfter("val carrierRestore =").substringBefore("rebind(record, requirePre = true)")
        assertTrue(prepare.contains("carrierRestore?.let"))
        assertTrue(prepare.contains("forRemoval = operation == \"RESTORE_STOCK\" || carrierRestore != null"))
        assertTrue(prepare.contains("owned == carrierRestore && owned != partition"))
        val images = native.substringAfter("private fun image(directory:").substringBefore("private fun")
        assertTrue(images.contains("record.optJSONObject(\"originals\")?.has(name) == true"))
        val commit = native.substringAfter("val writes =").substringBefore("record.put(\"expected\"")
        assertTrue(commit.contains("record.optJSONObject(\"originals\")?.has(it) == true"))
        assertTrue(commit.contains("it.partition == target -> 3"))
        assertTrue(commit.contains("verifyOwnedCarrierRestoreImage(record, restored.partition, restored.file, main)"))
        assertTrue(prepare.contains("val requireSignedOwnedRestore = carrierRestore == null || signingMode.isNotEmpty()"))
        assertTrue(prepare.contains("record.put(\"requireSignedOwnedRestore\", requireSignedOwnedRestore)"))
        assertTrue(prepare.contains("requireSignedOwnedRestore = requireSignedOwnedRestore"))
        assertFalse(commit.contains("Image(\"vbmeta\""))
    }

}
