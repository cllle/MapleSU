package dev.maplesu.manager.installer

import dev.maplesu.image.BootImageRebuilder
import dev.maplesu.image.LenovoAvbSigner
import dev.maplesu.manager.ultrakernel.*
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PanicA2ArchivedRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val root = File(System.getProperty("maplesu.repo", ".."))
    private val originalSha = "346d51ea1f63413ffa5d99c5c426806291af3573f0a1d66a7621c388e835880e"
    private val a2RawSha = "7a78d504d95c5768744fd264de862fc0166e2f2235d99d5d76ae2a13534e85e7"
    private fun assets() = UltraKernelFileAssets(File(root, "manager/src/main/assets"))
    private fun a2(): File = File(root, ".tmp/panic-agent-20261123/flash/boot-a2-fullflush.img").also {
        assumeTrue("Panic A2 image corpus absent", it.isFile)
        assertEquals("afcabb30ff1573ff114fe2ee7d9734e5c67df048b0b8d2e4db63e540b79183a8", UltraKernelSpec.sha256(it))
    }

    @Test fun originalInverseMatchesAllOldRangesAndOnlyA2MakesItStale() {
        val old = File(root, "evidence/device-panic-20261123T1041Z/boot-readback-20261123T113323Z/boot_a.img")
        assumeTrue("pre-A2 image corpus absent", old.isFile)
        val a2 = a2()
        val inverse = embedded(old)
        val decoded = UltraKernelRecoveryRecord.decode(inverse)
        val oldRaw = UltraKernelPlan.readKernel(old)
        val a2Raw = UltraKernelPlan.readKernel(a2)
        assertEquals(147, decoded.rangeCount)
        assertEquals(originalSha, UltraKernelSpec.sha256(decoded.restore(oldRaw)))
        assertEquals(a2RawSha, UltraKernelSpec.sha256(a2Raw))
        assertEquals(listOf(0x3ff4c), oldRaw.indices.filter { oldRaw[it] != a2Raw[it] })
        val error = assertThrows(IllegalArgumentException::class.java) { decoded.restore(a2Raw) }
        assertEquals("ULTRA_KERNEL_RECOVERY_CURRENT_MISMATCH", error.message)
        val oldResult = UltraKernelOriginalRecovery(UltraKernelMemoryAssets(emptyMap()))
            .generatedKernelWithDeployment(old, temporary.newFolder())
        assertEquals(originalSha, UltraKernelSpec.sha256(requireNotNull(oldResult).kernel))
        assertThrows(IllegalArgumentException::class.java) {
            UltraKernelOriginalRecovery(UltraKernelMemoryAssets(emptyMap()))
                .generatedKernelWithDeployment(a2, temporary.newFolder())
        }
    }

    @Test fun actualA2RecoversThePinnedKernelThroughBothProductionEntrypoints() {
        val current = a2()
        val before = UltraKernelSpec.sha256(current)
        val recovery = UltraKernelOriginalRecovery(assets())
        val generated = requireNotNull(recovery.generatedKernelWithDeployment(current, temporary.newFolder()))
        assertEquals(originalSha, UltraKernelSpec.sha256(generated.kernel))
        assertNull(generated.deploymentNamespace)
        assertTrue(recovery.archivedKernelUpdateRequired(current))
        assertNull(recovery.recover(current, temporary.newFolder(), emptyList(), temporary.newFolder()))
        val recovered = requireNotNull(recovery.recover(current, temporary.newFolder(), emptyList(),
            temporary.newFolder(), generatedOnly = true))
        assertEquals(UltraKernelOriginalRecovery.Source.ARCHIVED_KERNEL_REVERSE, recovered.source)
        assertEquals(originalSha, UltraKernelSpec.sha256(UltraKernelPlan.readKernel(recovered.image)))
        assertNull(BootImageRebuilder.readRecoveryRecord(recovered.image))
        assertEquals(before, UltraKernelSpec.sha256(current))
    }

    @Test fun sameA2RawKernelAndStaleInversePassTheRealOwnedRemovalFallback() {
        val current = compactA2()
        val before = UltraKernelSpec.sha256(current)
        val recovery = UltraKernelOriginalRecovery(assets())
        assertNull(recovery.recover(current, temporary.newFolder(), emptyList(), temporary.newFolder()))
        val result = StockOriginalResolver(assets(), null).resolveForRestore(snapshot(current),
            mapOf("boot" to current), temporary.newFolder(), setOf("boot"))
        assertEquals(ImageRestorationKind.OWNED_WRITES_REMOVED, result.kind)
        assertEquals("ARCHIVED_KERNEL_REVERSE", result.ownershipSource)
        assertEquals(setOf("boot"), result.originals.keys)
        assertEquals(originalSha, UltraKernelSpec.sha256(UltraKernelPlan.readKernel(result.originals.getValue("boot"))))
        assertEquals(before, UltraKernelSpec.sha256(current))
    }

    @Test fun oneUnknownByteInsideOrOutsideTheOwnedRangesKeepsTheOriginalFailure() {
        for (at in listOf(4096, 261720)) {
            val current = compactA2()
            RandomAccessFile(current, "rw").use { file ->
                file.seek(4096L + at); val original = file.read()
                file.seek(4096L + at); file.write(original xor 1)
            }
            val before = UltraKernelSpec.sha256(current)
            val recovery = UltraKernelOriginalRecovery(assets())
            assertFalse(recovery.archivedKernelUpdateRequired(current))
            val generatedError = assertThrows(IllegalArgumentException::class.java) {
                recovery.generatedKernelWithDeployment(current, temporary.newFolder())
            }
            assertEquals("ULTRA_KERNEL_RECOVERY_CURRENT_MISMATCH", generatedError.message)
            val output = temporary.newFolder()
            val restoreError = assertThrows(IllegalArgumentException::class.java) {
                StockOriginalResolver(assets(), null).resolveForRestore(snapshot(current),
                    mapOf("boot" to current), output, setOf("boot"))
            }
            assertEquals("ULTRA_KERNEL_RECOVERY_CURRENT_MISMATCH", restoreError.message)
            assertFalse(File(output, "boot.img").exists())
            assertEquals(before, UltraKernelSpec.sha256(current))
        }
    }

    @Test fun actualSignedA2StillRequiresItsCurrentAvbHashToVerify() {
        val current = a2()
        val vbmeta = File(root, ".tmp/supervision-20261123-build-acceptance/source-repairs-current/device-direct-install-identity-r1/vbmeta_a.img")
        assumeTrue("current device vbmeta corpus absent", vbmeta.isFile)
        val before = UltraKernelSpec.sha256(current)
        val mainBefore = UltraKernelSpec.sha256(vbmeta)
        assertEquals("e87be89cf4242547dc79aeafa5241c0623b46ec1773d37ba27830d1c129b9079", mainBefore)
        val live = mapOf("boot" to current, "vbmeta" to vbmeta)
        val verifyError = assertThrows(IOException::class.java) {
            LenovoAvbSigner.verifySigned(LenovoAvbSigner.Mode.CHAINED, "boot", live)
        }
        assertEquals("LENOVO_AVB_HASH_MISMATCH", verifyError.message)
        val bootSnapshot = snapshot(current)
        val snapshot = InstallerSnapshot(bootSnapshot.kernelRelease, "actual A2 signed container", "_a", "Enforcing",
            bootSnapshot.partitions + ("vbmeta" to BootPartitionSnapshot("vbmeta", "/dev/block/by-name/vbmeta_a",
                "/fixture/vbmeta_a", vbmeta.length(), mainBefore)))
        val output = temporary.newFolder()
        val restoreError = assertThrows(IOException::class.java) {
            StockOriginalResolver(assets(), null).resolveForRestore(snapshot, live, output, setOf("boot"))
        }
        assertEquals("LENOVO_AVB_HASH_MISMATCH", restoreError.message)
        assertFalse(File(output, "boot.img").exists())
        assertEquals(before, UltraKernelSpec.sha256(current))
        assertEquals(mainBefore, UltraKernelSpec.sha256(vbmeta))
        println("ACTUAL_SIGNED_A2 verifySigned=${verifyError.message} ownedRestore=${restoreError.message} finalCandidate=false")
    }

    private fun snapshot(boot: File) = InstallerSnapshot(UltraKernelSpec.releaseOf(UltraKernelPlan.readKernel(boot)),
        "Panic A2 raw-kernel fixture; unsigned compact container", "_a", "Enforcing", mapOf("boot" to
            BootPartitionSnapshot("boot", "/dev/block/by-name/boot_a", "/fixture/boot_a", boot.length(), UltraKernelSpec.sha256(boot))))

    private fun embedded(boot: File): ByteArray {
        val directory = temporary.newFolder()
        val record = File(directory, "record.bin")
        check(BootImageRebuilder.extractRecoveryRecord(boot, record,
            BootImageRebuilder.OutputPolicy.DISCARD_INVALID_AVB_IF_ZERO_TAIL))
        return UltraKernelRecoveryEnvelope.read(record, directory).use { it.inverse.clone() }
    }

    /** Copies only the exact raw kernel plus its small record, never the 96 MiB source image. */
    private fun compactA2(): File {
        val source = a2()
        val raw = UltraKernelPlan.readKernel(source)
        val inverse = embedded(source)
        val header = ByteArray(4096)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("ANDROID!".toByteArray()); putInt(8, raw.size); putInt(20, 1584); putInt(40, 4)
        }
        val recordHeader = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("MSURREG\u0000".toByteArray()); putInt(1); putInt(56); putInt(inverse.size); putInt(0)
            put(MessageDigest.getInstance("SHA-256").digest(inverse))
        }.array()
        return temporary.newFile().apply {
            outputStream().use { out ->
                out.write(header); out.write(raw)
                out.write(ByteArray((4096 - raw.size % 4096) % 4096))
                out.write(recordHeader); out.write(inverse)
                out.write(ByteArray((4096 - (56 + inverse.size) % 4096) % 4096))
            }
        }
    }
}
