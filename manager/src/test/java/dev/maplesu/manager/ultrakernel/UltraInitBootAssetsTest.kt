package dev.maplesu.manager.ultrakernel

import dev.maplesu.image.UltraInitBootProducer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Actual importer/parser with synthetic container-owner bytes; never executes a shim or module. */
class UltraInitBootAssetsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun absentAssetsCreateNothing() {
        val files = temporary.newFolder()
        assertNull(UltraInitBootAssets.load(files, { throw FileNotFoundException(it) }))
        assertTrue(files.listFiles().orEmpty().isEmpty())
    }

    @Test fun legacyPrivateBundleRemainsUsableWithoutBundledAssets() {
        val files = temporary.newFolder()
        val legacy = File(files, "ultra-init-bridge").apply { check(mkdir()) }
        for ((path, bytes) in bundle()) File(legacy, path).writeBytes(bytes)
        val artifact = UltraInitBootAssets.load(files, { throw FileNotFoundException(it) })!!
        assertEquals(File(legacy, "target-boot.img").canonicalFile, artifact.targetBoot.canonicalFile)
        assertFalse(File(files, "ultra-init-bridge.current").exists())
    }

    @Test fun sameManifestReusesTheCompletePrivateGeneration() {
        val files = temporary.newFolder()
        val bundled = bundle()
        val first = UltraInitBootAssets.load(files, opener(bundled))!!
        val second = UltraInitBootAssets.load(files, { path ->
            check(path == "ultra-init-bridge/manifest.json")
            ByteArrayInputStream(bundled.getValue("manifest.json"))
        })!!
        assertEquals(first.targetBoot, second.targetBoot)
        assertNoTemporaryFiles(files)
    }

    @Test fun newManifestSwitchesGenerationWithoutInvalidatingHeldArtifact() {
        val files = temporary.newFolder()
        val first = UltraInitBootAssets.load(files, opener(bundle()))!!
        val before = first.targetBoot.readBytes()
        val second = UltraInitBootAssets.load(files, opener(bundle(1)))!!
        assertNotEquals(first.bridgeSha256, second.bridgeSha256)
        assertNotEquals(first.targetBoot.parentFile, second.targetBoot.parentFile)
        assertArrayEquals(before, first.targetBoot.readBytes())
        assertEquals(second.targetBoot, UltraInitBootAssets.load(files, { throw FileNotFoundException(it) })!!.targetBoot)
        assertNoTemporaryFiles(files)
    }

    @Test fun selectionFailureKeepsPublishedGenerationAndOldSelection() {
        val files = temporary.newFolder()
        val first = UltraInitBootAssets.load(files, opener(bundle()))!!
        val selected = File(files, "ultra-init-bridge.current")
        val before = selected.readBytes()
        val next = bundle(2)
        val primary = IOException("selection-atomic-move-failed")
        var published: File? = null
        val failure = runCatching {
            UltraInitBootAssets.load(files, opener(next)) { pending, selection ->
                assertEquals(selected, selection)
                assertTrue(pending.isFile)
                published = File(files, "ultra-init-bridge-${UltraKernelSpec.sha256(next.getValue("manifest.json"))}")
                // Another consumer can already open this generation before selector publication.
                assertTrue(UltraInitBootProducer.readArtifact(File(published, "manifest.json")).targetBoot.isFile)
                throw primary
            }
        }.exceptionOrNull()
        assertSame(primary, failure)
        assertArrayEquals(before, selected.readBytes())
        assertTrue(first.targetBoot.isFile)
        assertTrue(File(published, "target-boot.img").isFile)
        assertEquals(first.targetBoot, UltraInitBootAssets.load(files, { throw FileNotFoundException(it) })!!.targetBoot)
        assertNoTemporaryFiles(files)
    }

    @Test fun invalidUpgradeReturnsItsErrorAndRetainsOldChoice() {
        val files = temporary.newFolder()
        val first = UltraInitBootAssets.load(files, opener(bundle()))!!
        val before = File(files, "ultra-init-bridge.current").readBytes()
        val next = bundle(3).toMutableMap().apply { put("bridge.ko", byteArrayOf(1)) }
        val failure = runCatching { UltraInitBootAssets.load(files, opener(next)) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().startsWith("ULTRA_INIT_ARTIFACT_HASH:bridge"))
        assertArrayEquals(before, File(files, "ultra-init-bridge.current").readBytes())
        assertTrue(first.targetBoot.isFile)
        assertNoTemporaryFiles(files)
    }

    @Test fun interruptedCopyPreservesCancellationAndRemovesOnlyPendingFiles() {
        val files = temporary.newFolder()
        val next = bundle()
        val failure = try {
            runCatching {
                UltraInitBootAssets.load(files, { path ->
                    if (path.endsWith("target-boot.img")) Thread.currentThread().interrupt()
                    opener(next)(path)
                })
            }.exceptionOrNull().also { assertTrue(Thread.currentThread().isInterrupted) }
        } finally { Thread.interrupted() }
        assertTrue(failure is InterruptedIOException)
        assertEquals("ULTRA_INIT_ASSET_CANCELLED", failure?.message)
        assertTrue(files.listFiles().orEmpty().isEmpty())
    }

    @Test fun abiEvidenceIsCopiedBoundAndReusedWithTheGeneration() {
        val files = temporary.newFolder()
        val evidence = "{\"method\":\"same-kmi-module-loader\"}\n".toByteArray()
        val bundled = bundle(evidence = evidence)
        val first = UltraInitBootAssets.load(files, opener(bundled))!!
        assertEquals(UltraKernelSpec.sha256(evidence), first.targetInputSha256["abiEvidence"])
        assertArrayEquals(evidence, File(first.targetBoot.parentFile, "abi-evidence.json").readBytes())
        val second = UltraInitBootAssets.load(files, { path ->
            assertEquals("ultra-init-bridge/manifest.json", path)
            ByteArrayInputStream(bundled.getValue("manifest.json"))
        })!!
        assertEquals(first.targetBoot, second.targetBoot)
        assertEquals(first.targetInputSha256, second.targetInputSha256)
        assertNoTemporaryFiles(files)
    }

    @Test fun missingAbiEvidenceKeepsHeldArtifactAndSelection() {
        val files = temporary.newFolder()
        val first = UltraInitBootAssets.load(files, opener(bundle()))!!
        val held = first.targetBoot.readBytes()
        val selection = File(files, "ultra-init-bridge.current").readBytes()
        val next = bundle(1, "{}\n".toByteArray()).toMutableMap().apply { remove("abi-evidence.json") }
        val failure = runCatching { UltraInitBootAssets.load(files, opener(next)) }.exceptionOrNull()
        assertTrue(failure is FileNotFoundException)
        assertEquals("ultra-init-bridge/abi-evidence.json", failure?.message)
        assertArrayEquals(selection, File(files, "ultra-init-bridge.current").readBytes())
        assertArrayEquals(held, first.targetBoot.readBytes())
        assertEquals(first.targetBoot, UltraInitBootAssets.load(files, { throw FileNotFoundException(it) })!!.targetBoot)
        assertNoTemporaryFiles(files)
    }

    @Test fun changedAbiEvidenceKeepsHeldArtifactAndSelection() {
        val files = temporary.newFolder()
        val first = UltraInitBootAssets.load(files, opener(bundle()))!!
        val held = first.targetBoot.readBytes()
        val selection = File(files, "ultra-init-bridge.current").readBytes()
        val next = bundle(2, "{}\n".toByteArray()).toMutableMap().apply {
            put("abi-evidence.json", "{\"changed\":true}\n".toByteArray())
        }
        val failure = runCatching { UltraInitBootAssets.load(files, opener(next)) }.exceptionOrNull()
        assertEquals("ULTRA_INIT_ARTIFACT_HASH:abiEvidence", failure?.message)
        assertArrayEquals(selection, File(files, "ultra-init-bridge.current").readBytes())
        assertArrayEquals(held, first.targetBoot.readBytes())
        assertEquals(first.targetBoot, UltraInitBootAssets.load(files, { throw FileNotFoundException(it) })!!.targetBoot)
        assertNoTemporaryFiles(files)
    }

    @Test fun oversizedAbiEvidenceKeepsHeldArtifactAndSelection() {
        val files = temporary.newFolder()
        val first = UltraInitBootAssets.load(files, opener(bundle()))!!
        val held = first.targetBoot.readBytes()
        val selection = File(files, "ultra-init-bridge.current").readBytes()
        val next = bundle(3, ByteArray(1024 * 1024 + 1) { 32 })
        val failure = runCatching { UltraInitBootAssets.load(files, opener(next)) }.exceptionOrNull()
        assertEquals("ULTRA_INIT_ASSET_SIZE_LIMIT", failure?.message)
        assertArrayEquals(selection, File(files, "ultra-init-bridge.current").readBytes())
        assertArrayEquals(held, first.targetBoot.readBytes())
        assertEquals(first.targetBoot, UltraInitBootAssets.load(files, { throw FileNotFoundException(it) })!!.targetBoot)
        assertNoTemporaryFiles(files)
    }

    private fun assertNoTemporaryFiles(files: File) {
        assertTrue(files.listFiles().orEmpty().none { it.name.startsWith(".ultra-init-") })
    }

    private fun opener(bundle: Map<String, ByteArray>): (String) -> InputStream = { path ->
        ByteArrayInputStream(bundle[path.removePrefix("ultra-init-bridge/")] ?: throw FileNotFoundException(path))
    }

    private fun bundle(variant: Int = 0, evidence: ByteArray? = null): Map<String, ByteArray> {
        val fixture = Class.forName("dev.maplesu.image.UltraInitBootProducerTest")
        fun bytes(name: String, vararg args: Any): ByteArray {
            val method = fixture.getDeclaredMethod(name, *args.map { it.javaClass }.toTypedArray())
                .apply { isAccessible = true }
            return method.invoke(null, *args) as ByteArray
        }
        val kernel = bytes("kernel")
        val bridge = bytes("module").also { it[it.lastIndex] = variant.toByte() }
        val files = linkedMapOf(
            "target-boot.img" to bytes("image", kernel, ByteArray(0)),
            "ultra-init" to bytes("handoff"), "bridge.ko" to bridge,
            "target.config" to "CONFIG_ARM64=y\nCONFIG_MODULES=y\nCONFIG_ARM64_4K_PAGES=y\n".toByteArray(),
            "Module.symvers" to "fixture symvers\n".toByteArray(),
            "target-contract.json" to "{}\n".toByteArray(),
        )
        if (evidence != null) files["abi-evidence.json"] = evidence
        val manifest = buildJsonObject {
            if (evidence != null) {
                put("abiEvidence", "abi-evidence.json")
                put("abiEvidenceSha256", UltraKernelSpec.sha256(evidence))
            }
            put("schemaVersion", 1)
            put("kernelRelease", "6.6.1-fixture")
            put("kernelSha256", UltraKernelSpec.sha256(kernel))
            put("credentialSha256", UltraKernelSpec.sha256("A".repeat(48).toByteArray()))
            for ((field, path) in mapOf("targetBoot" to "target-boot.img", "handoff" to "ultra-init",
                "bridge" to "bridge.ko", "config" to "target.config", "moduleSymvers" to "Module.symvers",
                "targetContract" to "target-contract.json")) {
                put(field, path)
                put(field + "Sha256", UltraKernelSpec.sha256(files.getValue(path)))
            }
        }
        files["manifest.json"] = manifest.toString().toByteArray()
        return files
    }
}
