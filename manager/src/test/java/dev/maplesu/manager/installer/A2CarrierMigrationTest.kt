package dev.maplesu.manager.installer

import dev.maplesu.image.AvbFooter
import dev.maplesu.installer.BlockDeviceTransaction
import dev.maplesu.manager.ultrakernel.*
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class A2CarrierMigrationTest {
    @get:Rule val temporary = TemporaryFolder()
    private val root = File(System.getProperty("maplesu.repo", ".."))
    private fun assets() = UltraKernelFileAssets(File(root, "manager/src/main/assets"))
    private fun inputs(): Map<String, File> {
        val boot = File(root, ".tmp/panic-agent-20261123/flash/boot-a2-fullflush.img")
        val main = File(root, ".tmp/supervision-20261123-build-acceptance/source-repairs-current/device-direct-install-identity-r1/vbmeta_a.img")
        assumeTrue("actual A2/vbmeta corpus absent", boot.isFile && main.isFile)
        assertEquals("afcabb30ff1573ff114fe2ee7d9734e5c67df048b0b8d2e4db63e540b79183a8", hash(boot))
        assertEquals("e87be89cf4242547dc79aeafa5241c0623b46ec1773d37ba27830d1c129b9079", hash(main))
        return mapOf("boot" to boot, "vbmeta" to main)
    }

    @Test fun ordinaryAutomaticCarrierMigrationUsesTheActualA2InputAndFullWriter() {
        val live = inputs()
        val snapshot = snapshot(live)
        val result = StockOriginalResolver(assets(), null).resolveForRestore(snapshot, live,
            temporary.newFolder(), setOf("boot"), requireSignedOwnedRestore = false)
        assertEquals(ImageRestorationKind.OWNED_WRITES_REMOVED, result.kind)
        assertEquals("ARCHIVED_KERNEL_REVERSE", result.ownershipSource)
        val restored = result.originals.getValue("boot")
        assertEquals("c07fd486b563c370d2294f580be02fb5b24feaf4966932d3111d6a61b219f243", hash(restored))
        assertTrue(AvbFooter.parse(restored).present)
        val record = migrationRecord()
        verifyOwnedCarrierRestoreImage(record, "boot", restored, live.getValue("vbmeta"))
        val init = initBoot()
        val candidate = temporary.newFile().apply { writeBytes(init.readBytes().apply { this[60] = 1 }) }
        val images = live + ("init_boot" to init)
        val devices = images.mapValues { CopyOnWriteDevice(it.value) }
        try {
            val writes = listOf(NativeInstallSequence.Image("boot", restored, hash(restored)),
                NativeInstallSequence.Image("init_boot", candidate, hash(candidate)))
            val events = mutableListOf<String>()
            val outcome = NativeInstallSequence.commit(devices, pre(images), writes,
                NativeInstallSequence.Journal { state, _, _ -> events += state })
            assertEquals("REBOOT_REQUIRED", outcome.state)
            assertEquals(hash(restored), BlockDeviceTransaction.digest(devices.getValue("boot")))
            assertEquals(hash(candidate), BlockDeviceTransaction.digest(devices.getValue("init_boot")))
            assertEquals(snapshot.partitions.getValue("vbmeta").sha256, BlockDeviceTransaction.digest(devices.getValue("vbmeta")))
            assertEquals(0, devices.getValue("vbmeta").writes)
            assertEquals("REBOOT_REQUIRED", events.last())
            assertEquals(snapshot.partitions.getValue("boot").sha256, hash(live.getValue("boot")))
            println("ACTUAL_A2 ordinaryCarrier=OWNED_WRITES_REMOVED commit=${outcome.state} preUnchanged=true vbmetaWrites=0")
        } finally { devices.values.forEach { it.close() } }
    }

    @Test fun theActualCommitGateAcceptsOnlyTheJournaledOrdinaryMigrationContract() {
        val live = inputs()
        val image = recovered(live.getValue("boot"))
        verifyOwnedCarrierRestoreImage(migrationRecord(), "boot", image, live.getValue("vbmeta"))
        val journal = JSONObject(migrationRecord().toString())
        assertEquals(false, journal.getBoolean("requireSignedOwnedRestore"))
        verifyOwnedCarrierRestoreImage(journal, "boot", image, live.getValue("vbmeta"))
    }

    @Test fun normalInstallAlsoAcceptsTheActualOwnedCarrierRestoration() {
        val live = inputs()
        val image = recovered(live.getValue("boot"))
        val record = migrationRecord().put("targetMode", "KSU")
        assertFalse(requiresSignedOwnedCarrierRestore(record, "boot"))
        verifyOwnedCarrierRestoreImage(record, "boot", image, live.getValue("vbmeta"))
        assertEquals("c07fd486b563c370d2294f580be02fb5b24feaf4966932d3111d6a61b219f243", hash(image))
    }

    @Test fun oldJournalExplicitSigningStockRestoreAndNonMigrationRetainFullVerification() {
        val live = inputs()
        val image = recovered(live.getValue("boot"))
        for (record in listOf(
            migrationRecord().apply { remove("requireSignedOwnedRestore") },
            migrationRecord().put("requireSignedOwnedRestore", true),
            migrationRecord().put("signingMode", "CHAINED"),
            migrationRecord().put("operation", "RESTORE_STOCK"),
            migrationRecord().put("carrierRestoration", ""),
            migrationRecord().put("partition", "boot"),
            migrationRecord().put("targetMode", "UNKNOWN"),
        )) {
            val error = assertThrows(IOException::class.java) {
                verifyOwnedCarrierRestoreImage(record, "boot", image, live.getValue("vbmeta"))
            }
            assertEquals("LENOVO_AVB_HASH_MISMATCH", error.message)
        }
        for (required in listOf(true)) {
            val error = assertThrows(IOException::class.java) {
                StockOriginalResolver(assets(), null).resolveForRestore(snapshot(live), live,
                    temporary.newFolder(), setOf("boot"), requireSignedOwnedRestore = required)
            }
            assertEquals("LENOVO_AVB_HASH_MISMATCH", error.message)
        }
        val defaultError = assertThrows(IOException::class.java) {
            StockOriginalResolver(assets(), null).resolveForRestore(snapshot(live), live,
                temporary.newFolder(), setOf("boot"))
        }
        assertEquals("LENOVO_AVB_HASH_MISMATCH", defaultError.message)
    }

    @Test fun ordinaryMigrationKeepsUnknownInverseAndPreIdentityFailures() {
        val live = inputs()
        val original = snapshot(live)
        val changed = temporary.newFile()
        live.getValue("boot").copyTo(changed, overwrite = true)
        RandomAccessFile(changed, "rw").use { file ->
            file.seek(4096L + 4096); val before = file.read()
            file.seek(4096L + 4096); file.write(before xor 1)
        }
        val unknown = live + ("boot" to changed)
        val stalePre = assertThrows(IllegalStateException::class.java) {
            StockOriginalResolver(assets(), null).resolveForRestore(original, unknown,
                temporary.newFolder(), setOf("boot"), requireSignedOwnedRestore = false)
        }
        assertEquals("INSTALLER_IMAGE_CHANGED", stalePre.message)
        val output = temporary.newFolder()
        val inverse = assertThrows(IllegalArgumentException::class.java) {
            StockOriginalResolver(assets(), null).resolveForRestore(snapshot(unknown), unknown,
                output, setOf("boot"), requireSignedOwnedRestore = false)
        }
        assertEquals("ULTRA_KERNEL_RECOVERY_CURRENT_MISMATCH", inverse.message)
        assertFalse(File(output, "boot.img").exists())
        assertEquals(original.partitions.getValue("boot").sha256, hash(live.getValue("boot")))
    }

    @Test fun ordinaryMigrationStillRejectsChangedCandidatesBeforeTheFirstWrite() {
        val live = inputs()
        val restored = recovered(live.getValue("boot"))
        verifyOwnedCarrierRestoreImage(migrationRecord(), "boot", restored, live.getValue("vbmeta"))
        val expected = hash(restored)
        RandomAccessFile(restored, "rw").use { file ->
            file.seek(4096L + 4096); val before = file.read()
            file.seek(4096L + 4096); file.write(before xor 1)
        }
        val devices = live.mapValues { CopyOnWriteDevice(it.value) }
        try {
            val result = NativeInstallSequence.commit(devices, pre(live),
                listOf(NativeInstallSequence.Image("boot", restored, expected)), NativeInstallSequence.Journal { _, _, _ -> })
            assertEquals("FAILED_BEFORE_WRITE", result.state)
            assertEquals("INSTALLER_IMAGE_CHANGED", result.error)
            assertTrue(devices.values.all { it.writes == 0 })
            assertEquals(hash(live.getValue("boot")), BlockDeviceTransaction.digest(devices.getValue("boot")))
        } finally { devices.values.forEach { it.close() } }
    }

    @Test fun aRealWriterFailureRestoresTheEntireActualA2PreImage() {
        val live = inputs()
        val restored = recovered(live.getValue("boot"))
        verifyOwnedCarrierRestoreImage(migrationRecord(), "boot", restored, live.getValue("vbmeta"))
        val devices = live.mapValues { CopyOnWriteDevice(it.value) }
        devices.getValue("boot").failFirstWrite = true
        try {
            val result = NativeInstallSequence.commit(devices, pre(live),
                listOf(NativeInstallSequence.Image("boot", restored, hash(restored))), NativeInstallSequence.Journal { _, _, _ -> })
            assertEquals("RESTORED", result.state)
            assertEquals(hash(live.getValue("boot")), BlockDeviceTransaction.digest(devices.getValue("boot")))
            assertEquals(0, devices.getValue("vbmeta").writes)
            assertEquals("afcabb30ff1573ff114fe2ee7d9734e5c67df048b0b8d2e4db63e540b79183a8", hash(live.getValue("boot")))
        } finally { devices.values.forEach { it.close() } }
    }

    private fun migrationRecord() = JSONObject().put("operation", "INSTALL").put("targetMode", "LITE")
        .put("partition", "init_boot").put("carrierRestoration", "boot").put("signingMode", "")
        .put("restorationKind", "OWNED_WRITES_REMOVED").put("requireSignedOwnedRestore", false)
    private fun recovered(boot: File) = requireNotNull(UltraKernelOriginalRecovery(assets()).recover(boot,
        temporary.newFolder(), emptyList(), temporary.newFolder(), generatedOnly = true)).image
    private fun hash(file: File) = UltraKernelSpec.sha256(file)
    private fun snapshot(live: Map<String, File>) = InstallerSnapshot(UltraKernelSpec.releaseOf(UltraKernelPlan.readKernel(live.getValue("boot"))),
        "actual A2 carrier migration fixture", "_a", "Enforcing", live.mapValues { (name, file) ->
            BootPartitionSnapshot(name, "/dev/block/by-name/${name}_a", "/fixture/$name", file.length(), hash(file)) })
    private fun pre(live: Map<String, File>) = live.mapValues { (name, file) -> NativeInstallSequence.Image(name, file, hash(file)) }
    private fun initBoot() = temporary.newFile().apply { writeBytes(ByteArray(4096).apply {
        ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("ANDROID!".toByteArray()); putInt(20, 1584); putInt(40, 4)
        }
    }) }

    /** A local copy appears only at the first write; PRE/source files are always opened read-only. */
    private inner class CopyOnWriteDevice(private val source: File) : BlockDeviceTransaction.Device {
        private var input = RandomAccessFile(source, "r")
        private var owned: File? = null
        var writes = 0
        var failFirstWrite = false
        override fun size() = source.length()
        override fun revalidate() = Unit
        override fun read(offset: Long, buffer: ByteArray, length: Int): Int {
            input.seek(offset); return input.read(buffer, 0, length)
        }
        override fun write(offset: Long, buffer: ByteArray, start: Int, length: Int): Int {
            if (owned == null) {
                val copy = temporary.newFile()
                source.copyTo(copy, overwrite = true)
                input.close(); input = RandomAccessFile(copy, "rw"); owned = copy
            }
            writes++
            input.seek(offset)
            if (failFirstWrite && writes == 1) {
                input.write(buffer, start, 1)
                throw IOException("INSTALLER_DEVICE_WRITE_FAILED")
            }
            input.write(buffer, start, length); return length
        }
        override fun sync() { input.fd.sync() }
        override fun close() { input.close(); owned?.delete() }
    }
}
