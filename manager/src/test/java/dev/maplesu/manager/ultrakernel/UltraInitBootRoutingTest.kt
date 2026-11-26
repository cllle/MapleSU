package dev.maplesu.manager.ultrakernel

import dev.maplesu.image.UltraInitBootProducer
import dev.maplesu.image.BootImageEquivalence
import dev.maplesu.image.BootImageInspector
import dev.maplesu.manager.installer.InstallPartition
import dev.maplesu.manager.installer.InstallPartitionPolicy
import dev.maplesu.manager.installer.InstallPartitionResolution
import dev.maplesu.manager.installer.InstallTargetMode
import dev.maplesu.manager.workmode.WorkMode
import dev.maplesu.manager.workmode.switch.ModeSwitchPreflight
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Synthetic container inputs prove routing/failure boundaries, never bridge bootability. */
class UltraInitBootRoutingTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun defaultComponentProviderUsesOnlyTheSelectedInitBoot() {
        val (source, artifact) = componentFixture(false)
        val baseline = source.readBytes()
        val flow = UltraKernelFlow(temporary.newFolder(), UltraKernelMemoryAssets(emptyMap()), moduleArtifact = {
            assertEquals(source, it)
            artifact
        })
        val prepared = flow.prepare(source, UltraKernelCredential.of("A".repeat(48)))
        val candidate = requireNotNull(prepared.candidate)
        assertEquals("init_boot", candidate.partition)
        assertEquals("android15-6.6", candidate.componentKmi)
        assertEquals("INIT_BOOT_KMI_COMPONENT", prepared.planOrigin)
        assertNull(artifact.targetBoot)
        val restored = File(temporary.newFolder(), "restored.img")
        UltraInitBootProducer.restore(candidate.image, restored)
        assertArrayEquals(baseline, restored.readBytes())
        assertArrayEquals(baseline, source.readBytes())
    }

    @Test fun explicitBootComponentSkipsRawKallsymsAndDefaultTemplateSelection() {
        val (source, artifact) = componentFixture(true)
        val flow = UltraKernelFlow(temporary.newFolder(), UltraKernelMemoryAssets(emptyMap()),
            moduleArtifact = { error("explicit component must not open the default target template") })
        val prepared = flow.prepareTarget(source, UltraKernelCredential.of("A".repeat(48)), "generated", artifact)
        val candidate = requireNotNull(prepared.candidate)
        assertEquals("boot", candidate.partition)
        assertEquals("android15-6.6", candidate.componentKmi)
        assertEquals(UltraKernelSpec.sha256(UltraKernelPlan.readKernel(source)), candidate.kernelSha256)
        assertEquals("6.6.1-android15-1-fixture", candidate.kernelRelease)
    }

    @Test fun rawBootToComponentRecoversKernelAndKeepsInputSeparateFromUninstallBaseline() {
        val (stock, artifact) = componentFixture(true)
        val baseline = stock.readBytes()
        val raw = UltraKernelPlan.readKernel(stock)
        val namespace = UltraKernelDeploymentNamespace.create()
        val input = rawPatched(stock, namespace)
        val before = input.readBytes()
        val work = temporary.newFolder()
        val prepared = UltraKernelFlow(work, UltraKernelMemoryAssets(emptyMap()))
            .prepareTarget(input, UltraKernelCredential.of("B".repeat(48)), "generated", artifact)
        val candidate = requireNotNull(prepared.candidate)
        assertArrayEquals("old raw hook and credential are removed before installing the bridge",
            raw, UltraKernelPlan.readKernel(candidate.image))
        assertEquals(UltraKernelSpec.sha256(before), candidate.baseBootSha256)
        assertEquals(UltraKernelSpec.sha256(raw), candidate.kernelSha256)
        assertEquals(BootImageEquivalence.diff(input, candidate.image).changedBytes.toInt(), candidate.differingBytes)
        assertArrayEquals(namespace.record(), requireNotNull(UltraInitBootProducer.readDeploymentNamespace(candidate.image)))
        val receipt = Json.parseToJsonElement(File(work, candidate.image.nameWithoutExtension + ".json").readText()).jsonObject
        assertEquals(UltraKernelSpec.sha256(before), receipt.getValue("sourceSha256").jsonPrimitive.content)
        assertEquals(UltraKernelSpec.sha256(baseline), receipt.getValue("recoveryBaseSha256").jsonPrimitive.content)
        assertEquals("IMAGE_BOUND_EXACT_INPUT", receipt.getValue("normalizationSource").jsonPrimitive.content)
        assertEquals("NORMALIZED_INPUT_EXACT", receipt.getValue("recoveryCoverage").jsonPrimitive.content)
        val restored = File(temporary.newFolder(), "restored.img")
        UltraInitBootProducer.restore(candidate.image, restored)
        assertArrayEquals("uninstall restores the original container rather than the old raw patch", baseline, restored.readBytes())
        assertArrayEquals(before, input.readBytes())
        assertArrayEquals(baseline, stock.readBytes())
    }

    @Test fun componentRepatchAlsoNormalizesAnOlderModuleWithRawRecoveryBaseline() {
        val (stock, artifact) = componentFixture(true)
        val namespace = UltraKernelDeploymentNamespace.create()
        val raw = rawPatched(stock, namespace)
        val input = File(temporary.newFolder(), "old-module.img")
        UltraInitBootProducer.produce(raw, input, artifact.withCredential("A".repeat(48).toByteArray())
            .withDeploymentNamespace(namespace.record()))
        val before = input.readBytes()
        val rawBefore = raw.readBytes()
        val prepared = UltraKernelFlow(temporary.newFolder(), UltraKernelMemoryAssets(emptyMap()))
            .prepareTarget(input, UltraKernelCredential.of("B".repeat(48)), "generated", artifact)
        val candidate = requireNotNull(prepared.candidate)
        assertEquals(UltraKernelSpec.sha256(before), candidate.baseBootSha256)
        assertArrayEquals(UltraKernelPlan.readKernel(stock), UltraKernelPlan.readKernel(candidate.image))
        assertArrayEquals(namespace.record(), requireNotNull(UltraInitBootProducer.readDeploymentNamespace(candidate.image)))
        val restored = File(temporary.newFolder(), "restored.img")
        UltraInitBootProducer.restore(candidate.image, restored)
        assertArrayEquals(stock.readBytes(), restored.readBytes())
        assertArrayEquals(before, input.readBytes())
        assertArrayEquals(rawBefore, raw.readBytes())
    }

    @Test fun damagedRawInverseFailsBeforeInstallingAComponent() {
        val (stock, artifact) = componentFixture(true)
        val input = rawPatched(stock, UltraKernelDeploymentNamespace.create())
        val before = input.readBytes().apply { this[BootImageInspector.inspect(input).kernelOffset.toInt() + 1024] = 0 }
        input.writeBytes(before)
        val work = temporary.newFolder()
        val failure = runCatching {
            UltraKernelFlow(work, UltraKernelMemoryAssets(emptyMap()))
                .prepareTarget(input, UltraKernelCredential.of("B".repeat(48)), "generated", artifact)
        }.exceptionOrNull()
        assertEquals("ULTRA_KERNEL_RECOVERY_CURRENT_MISMATCH", failure?.message)
        assertArrayEquals(before, input.readBytes())
        assertTrue(work.listFiles().orEmpty().isEmpty())
    }

    @Test fun moduleAndRawDeploymentConflictKeepsTheActualRecoveryFailure() {
        val (stock, artifact) = componentFixture(true)
        val raw = rawPatched(stock, UltraKernelDeploymentNamespace.create())
        val input = File(temporary.newFolder(), "old-module.img")
        UltraInitBootProducer.produce(raw, input, artifact.withCredential("A".repeat(48).toByteArray())
            .withDeploymentNamespace(UltraKernelDeploymentNamespace.create().record()))
        val before = input.readBytes()
        val work = temporary.newFolder()
        val failure = runCatching {
            UltraKernelFlow(work, UltraKernelMemoryAssets(emptyMap()))
                .prepareTarget(input, UltraKernelCredential.of("B".repeat(48)), "generated", artifact)
        }.exceptionOrNull()
        assertEquals("ULTRA_INIT_BOOT_DEPLOYMENT_NAMESPACE_CONFLICT", failure?.message)
        assertArrayEquals(before, input.readBytes())
        assertTrue(work.listFiles().orEmpty().isEmpty())
    }

    private fun rawPatched(base: File, namespace: UltraKernelDeploymentNamespace): File {
        val raw = UltraKernelPlan.readKernel(base)
        val credential = UltraKernelCredential.of("A".repeat(48))
        val plan = UltraKernelPlan.fromRecords(UltraKernelSpec.releaseOf(raw), listOf(
            UltraKernelPlan.PatchRecord("fixture", 1024, ByteArray(48), credential.copySecret()),
            UltraKernelPlan.PatchRecord("path", 2048, ByteArray(UltraKernelCredential.INIT_PATH_FIELD),
                credential.initPathField(UltraKernelCredential.INIT_PATH_FIELD)),
            UltraKernelPlan.PatchRecord("manager_claim:deployment_namespace", 3000,
                ByteArray(UltraKernelDeploymentNamespace.RECORD_BYTES), namespace.record()),
            UltraKernelPlan.PatchRecord("hook", 3072, ByteArray(4), byteArrayOf(0, 0, 0, 0x14)),
        ), credential, baseKernelSha256 = UltraKernelSpec.sha256(raw))
            .let { it.withDerived(it.apply(raw).derived) }
        val text = plan.specText(plan.credential)
        val asset = UltraKernelGeneratedAsset.of(plan, base.length(), BootImageInspector.inspect(base).ramdiskSize, text)
        return UltraKernelBuilder(temporary.newFolder()).generate(base, asset, UltraKernelSpec.parse(text)).image
    }

    @Suppress("UNCHECKED_CAST")
    private fun componentFixture(boot: Boolean): Pair<File, UltraInitBootProducer.Artifact> {
        val helpers = Class.forName("dev.maplesu.image.UltraInitBootProducerTest")
        val files = helpers.getDeclaredMethod("kmiBundle").apply { isAccessible = true }.invoke(null) as Map<String, ByteArray>
        val directory = temporary.newFolder()
        files.forEach { (path, bytes) -> File(directory, path).writeBytes(bytes) }
        val artifact = UltraInitBootProducer.readArtifact(File(directory, "manifest.json"))
        val kernel = if (boot) (helpers.getDeclaredMethod("kernel").apply { isAccessible = true }.invoke(null) as ByteArray).also {
            java.util.Arrays.fill(it, 128, 512, 0.toByte())
            "Linux version 6.6.1-android15-1-fixture (fixture)\n".toByteArray().copyInto(it, 128)
        } else ByteArray(0)
        val ramdisk = helpers.getDeclaredMethod("stock", java.lang.Boolean.TYPE, java.lang.Boolean.TYPE)
            .apply { isAccessible = true }.invoke(null, false, false) as ByteArray
        val bytes = helpers.getDeclaredMethod("image", ByteArray::class.java, ByteArray::class.java)
            .apply { isAccessible = true }.invoke(null, kernel, ramdisk) as ByteArray
        return File(directory, if (boot) "boot.img" else "init_boot.img").also { it.writeBytes(bytes) } to artifact
    }

    @Test fun automaticUltraKeepsTheImplementedBootBackendOnBothTopologies() {
        for (partitions in listOf(setOf("boot"), setOf("boot", "init_boot"))) {
            assertEquals(InstallPartitionResolution.Ready(InstallPartition.BOOT),
                InstallPartitionPolicy.resolve(InstallTargetMode.ULTRA, null, partitions))
        }
        assertTrue(InstallPartitionPolicy.resolve(InstallTargetMode.ULTRA, "init_boot", setOf("boot"))
            is InstallPartitionResolution.Rejected)
    }

    @Test fun missingArtifactFailsBeforeAnyCandidateIsProducedAndKeepsInput() {
        val input = initBoot()
        val before = input.readBytes()
        val workspace = temporary.newFolder("candidate-work")
        val flow = UltraKernelFlow(workspace, UltraKernelMemoryAssets(emptyMap()))
        val error = runCatching {
            flow.prepareTarget(input, UltraKernelCredential.of("A".repeat(48)))
        }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().startsWith("ULTRA_KERNEL_INIT_BOOT_ARTIFACT_REQUIRED:"))
        assertArrayEquals(before, input.readBytes())
        assertTrue(workspace.listFiles().orEmpty().isEmpty())
        assertTrue(runCatching { flow.releaseOf(input) }.exceptionOrNull()
            ?.message.orEmpty().contains("ULTRA_KERNEL_BASE_NO_KERNEL"))
    }

    @Test fun sameCarrierPlansStayMinimalAndUnknownCarrierCannotInventInitBoot() {
        assertEquals(listOf("init_boot"), ModeSwitchPreflight.writeOrder(
            WorkMode.KSU, "init_boot", "init_boot", setOf("boot", "init_boot")))
        assertEquals(listOf("boot"), ModeSwitchPreflight.writeOrder(
            WorkMode.LITE, "boot", "", setOf("boot")))
        assertEquals(listOf("init_boot", "boot"), ModeSwitchPreflight.writeOrder(
            WorkMode.LITE, "init_boot", "boot", setOf("boot", "init_boot")))
        assertTrue(runCatching { ModeSwitchPreflight.writeOrder(
            WorkMode.LITE, "init_boot", "", setOf("boot")) }.isFailure)
    }

    private fun initBoot(): File {
        val bytes = ByteArray(8192)
        "ANDROID!".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(12, 4); putInt(20, 1584); putInt(40, 4)
        }
        return File(temporary.root, "init_boot.img").also { it.writeBytes(bytes) }
    }
}
