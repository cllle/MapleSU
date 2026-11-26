package dev.maplesu.manager.ultrakernel

import dev.maplesu.image.BootImageInspector
import dev.maplesu.image.UltraInitBootProducer
import dev.maplesu.manager.ksu.KsuRootCommandRunner
import dev.maplesu.manager.workmode.WorkMode
import dev.maplesu.manager.workmode.service.ModeSwitchCandidateInput
import dev.maplesu.manager.workmode.service.ModeSwitchSourceRecovery
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Actual owned ramdisk inverses, never module execution or device qualification. */
class UltraBootModuleRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val assets = UltraKernelMemoryAssets(emptyMap())

    @Test fun bootAndInitBootModuleCarriersRestoreExactContainerAndNamespace() {
        for (withKernel in listOf(false, true)) {
            val input = fixture(withKernel)
            assertEquals(if (withKernel) 4096L else 0L, BootImageInspector.inspect(input.installed).kernelSize)
            assertTrue(UltraInitBootProducer.recognizes(input.installed))
            val before = input.installed.readBytes()
            val found = UltraKernelOriginalRecovery(assets).recover(input.installed, temporary.newFolder(),
                emptyList(), temporary.newFolder())
            assertNotNull(found)
            assertEquals(UltraKernelOriginalRecovery.Source.IMAGE_BOUND_EXACT_INPUT, found!!.source)
            assertArrayEquals(input.original.readBytes(), found.image.readBytes())
            assertArrayEquals(input.namespace.record(), requireNotNull(found.deploymentNamespace).record())
            assertArrayEquals(input.namespace.record(), requireNotNull(ModeSwitchCandidateInput(assets,
                temporary.newFolder()).readSource(input.installed)).record())
            assertArrayEquals(before, input.installed.readBytes())
        }
    }

    @Test fun bootSourceRecoveryUsesOwnedRamdiskInsteadOfKernelInverse() {
        val input = fixture(true)
        val recovery = ModeSwitchSourceRecovery(assets, temporary.newFolder(), emptyList(), "unused-ksud",
            KsuRootCommandRunner { _, _ -> error("UNEXPECTED_ROOT_COMMAND") })
        val result = recovery.recover(WorkMode.LITE, "boot", input.installed, temporary.newFolder(), true)
        assertTrue(result.code + ":" + result.detail, result.ok)
        assertEquals("ultra:boot:owned-writes-restored", result.structureEvidence)
        val bytes = requireNotNull(result.image).readBytes()
        val original = input.original.readBytes()
        assertArrayEquals(original, bytes.copyOfRange(0, original.size))
        assertTrue(bytes.drop(original.size).all { it == 0.toByte() })
        assertEquals(UltraKernelSpec.sha256(UltraKernelPlan.readKernel(input.original)),
            UltraKernelSpec.sha256(UltraKernelPlan.readKernel(requireNotNull(result.image))))
    }

    @Test fun damagedBootOwnedRecoveryFailsWithoutCandidateOrRootCommands() {
        val input = fixture(true)
        val report = BootImageInspector.inspect(input.installed)
        input.installed.writeBytes(input.installed.readBytes().apply {
            val at = report.ramdiskOffset.toInt() + 128
            this[at] = (this[at].toInt() xor 1).toByte()
        })
        val before = input.installed.readBytes()
        val result = ModeSwitchSourceRecovery(assets, temporary.newFolder(), emptyList(), "unused-ksud",
            KsuRootCommandRunner { _, _ -> error("UNEXPECTED_ROOT_COMMAND") })
            .recover(WorkMode.LITE, "boot", input.installed, temporary.newFolder(), true)
        assertFalse(result.ok)
        assertNull(result.image)
        assertEquals("ULTRA_INIT_BOOT_RECOVERY_CHECKSUM", result.code)
        assertArrayEquals(before, input.installed.readBytes())
    }

    private data class Fixture(val original: File, val installed: File,
                               val namespace: UltraKernelDeploymentNamespace)

    private fun fixture(withKernel: Boolean): Fixture {
        val directory = temporary.newFolder()
        val helper = Class.forName("dev.maplesu.image.UltraInitBootProducerTest")
        fun bytes(name: String, vararg args: Any): ByteArray {
            val types = args.map { when (it) {
                is Boolean -> java.lang.Boolean.TYPE
                is Int -> java.lang.Integer.TYPE
                else -> it.javaClass
            } }.toTypedArray()
            return helper.getDeclaredMethod(name, *types).apply { isAccessible = true }
                .invoke(null, *args) as ByteArray
        }
        val kernel = bytes("kernel")
        val module = bytes("parameterModule", 32)
        val header = ByteBuffer.wrap(module).order(ByteOrder.LITTLE_ENDIAN)
        val section = header.getLong(40).toInt() + 5 * header.getShort(58).toInt()
        val at = header.getLong(section + 24).toInt()
        val count = header.getLong(section + 32).toInt()
        val parameter = "parmtype=deployment_namespace:string\u0000".toByteArray(Charsets.US_ASCII)
        parameter.copyInto(module, at + count)
        header.putLong(section + 32, (count + parameter.size).toLong())
        val files = mapOf("target-boot.img" to bytes("image", kernel, ByteArray(0)),
            "ultra-init" to bytes("handoff"), "bridge.ko" to module)
        files.forEach { (name, content) -> File(directory, name).writeBytes(content) }
        val manifest = File(directory, "manifest.json")
        manifest.writeText(buildJsonObject {
            put("schemaVersion", 1)
            put("kernelRelease", "6.6.1-fixture")
            put("kernelSha256", UltraKernelSpec.sha256(kernel))
            put("credentialMode", "module-parameter")
            put("metadataDisplacement", 4096 - 32)
            for ((field, path) in mapOf("targetBoot" to "target-boot.img", "handoff" to "ultra-init", "bridge" to "bridge.ko")) {
                put(field, path); put(field + "Sha256", UltraKernelSpec.sha256(files.getValue(path)))
            }
        }.toString())
        val namespace = UltraKernelDeploymentNamespace.create()
        val artifact = UltraInitBootProducer.readArtifact(manifest).withDeploymentNamespace(namespace.record())
            .withCredential("Aa1".repeat(16).toByteArray(Charsets.US_ASCII))
        val source = File(directory, "original.img").also {
            it.writeBytes(bytes("image", if (withKernel) kernel else ByteArray(0), bytes("stock", false, false)))
        }
        val installed = File(directory, "installed.img")
        UltraInitBootProducer.produce(source, installed, artifact)
        // Mode-switch inputs are full partition captures, while the producer exports a compact file.
        assertTrue(installed.length() <= source.length())
        RandomAccessFile(installed, "rw").use { it.setLength(source.length()) }
        return Fixture(source, installed, namespace)
    }
}
