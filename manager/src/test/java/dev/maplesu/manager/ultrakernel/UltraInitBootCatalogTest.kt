package dev.maplesu.manager.ultrakernel

import dev.maplesu.image.UltraInitBootProducer
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Actual catalog/importer and exact-target parser. Synthetic ELF bytes never prove cross-ABI load. */
class UltraInitBootCatalogTest {
    @get:Rule val temporary = TemporaryFolder()
    private val generation = "1".repeat(64)

    @Test fun singleFileKmiSelectionHasNoTargetImageOrProof() {
        val assets = kmiCatalog(listOf(kmiBundle()))
        val files = temporary.newFolder()
        assertEquals(listOf("android15-6.6"), UltraInitBootCatalog.availableKmis(files, opener(assets)))
        val component = UltraInitBootCatalog.resolveKmi(files, opener(assets), "android15-6.6")!!
        assertTrue(component.isKmiComponent)
        assertNull(component.targetBoot)
        assertEquals("", component.targetBootSha256)
        assertEquals("", component.kernelSha256)
        assertTrue(component.targetInputSha256.isEmpty())
        assertNull(UltraInitBootCatalog.resolveKmi(files, opener(assets), "android14-6.1"))
    }

    @Test fun separateKmiIndexPreservesTheExactTargetCatalog() {
        val exactBundle = bundle()
        val assets = catalog(listOf(exactBundle)).apply { putAll(kmiCatalog(listOf(kmiBundle()))) }
        val files = temporary.newFolder()
        assertTrue(UltraInitBootCatalog.resolveKmi(files, opener(assets), "android15-6.6")!!.isKmiComponent)
        val exact = UltraInitBootCatalog.resolve(files, opener(assets), target(exactBundle))!!
        assertFalse(exact.isKmiComponent)
        assertEquals(UltraKernelSpec.sha256(exactBundle.getValue("target-boot.img")), exact.targetBootSha256)
    }

    @Test fun missingPackagedKmiManifestDoesNotReusePrivateCache() {
        val assets = kmiCatalog(listOf(kmiBundle()))
        val files = temporary.newFolder()
        UltraInitBootCatalog.resolveKmi(files, opener(assets), "android15-6.6")
        val path = assets.keys.single { it.endsWith("/manifest.json") }
        assets.remove(path)
        val failure = runCatching {
            UltraInitBootCatalog.resolveKmi(files, opener(assets), "android15-6.6")
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().startsWith("ULTRA_INIT_KMI_COMPONENT_ASSET_MISSING:"))
    }

    @Test fun listingKmisReadsOnlyTheIndexAndCreatesNoFiles() {
        val assets = kmiCatalog(listOf(kmiBundle()))
        val files = temporary.newFolder()
        val reads = mutableListOf<String>()
        val open = opener(assets)
        assertEquals(listOf("android15-6.6"), UltraInitBootCatalog.availableKmis(files) { path ->
            reads += path
            open(path)
        })
        assertEquals(listOf("ultra-init-bridge/catalog-kmi.json"), reads)
        assertTrue(files.listFiles().orEmpty().isEmpty())
    }

    @Test fun selectingKmiDoesNotImportOtherKernels() {
        val assets = kmiCatalog(listOf(kmiBundle()))
        val indexPath = "ultra-init-bridge/catalog-kmi.json"
        val index = Json.parseToJsonElement(assets.getValue(indexPath).decodeToString()).jsonObject
        val row = index.getValue("components").jsonArray.single().jsonObject
        val otherHash = "b".repeat(64)
        val other = JsonObject(row + mapOf("kmi" to JsonPrimitive("android14-6.1"),
            "manifestSha256" to JsonPrimitive(otherHash),
            "prefix" to JsonPrimitive("components/$otherHash/")))
        assets[indexPath] = JsonObject(index + ("components" to JsonArray(listOf(row, other))))
            .toString().toByteArray()
        val reads = mutableListOf<String>()
        val open = opener(assets)
        val component = UltraInitBootCatalog.resolveKmi(temporary.newFolder(), { path ->
            reads += path
            open(path)
        }, "android15-6.6")!!
        assertEquals("android15-6.6", component.componentKmi)
        assertFalse(reads.any { it.startsWith("ultra-init-bridge/components/$otherHash/") })
    }

    @Test fun kmiNeverGuessesBetweenCfiVariants() {
        val first = kmiBundle()
        val second = first.toMutableMap().apply {
            this["manifest.json"] = getValue("manifest.json").decodeToString().replace("\"kcfi\"", "\"none\"").toByteArray()
        }
        val assets = kmiCatalog(listOf(first, second))
        val files = temporary.newFolder()
        assertEquals(listOf("android15-6.6"), UltraInitBootCatalog.availableKmis(files, opener(assets)))
        val failure = runCatching { UltraInitBootCatalog.resolveKmi(files, opener(assets), "android15-6.6") }.exceptionOrNull()
        assertEquals("ULTRA_INIT_KMI_COMPONENT_AMBIGUOUS:android15-6.6", failure?.message)
    }

    @Suppress("UNCHECKED_CAST")
    private fun kmiBundle(): Map<String, ByteArray> = Class.forName("dev.maplesu.image.UltraInitBootProducerTest")
        .getDeclaredMethod("kmiBundle").apply { isAccessible = true }.invoke(null) as Map<String, ByteArray>

    private fun kmiCatalog(bundles: List<Map<String, ByteArray>>): MutableMap<String, ByteArray> {
        val assets = linkedMapOf<String, ByteArray>()
        val entries = bundles.map { bundle ->
            val encoded = bundle.getValue("manifest.json")
            val manifest = Json.parseToJsonElement(encoded.decodeToString()).jsonObject
            val digest = UltraKernelSpec.sha256(encoded)
            val prefix = "components/$digest/"
            bundle.forEach { (path, bytes) -> assets["ultra-init-bridge/$prefix$path"] = bytes }
            buildJsonObject {
                for (field in listOf("kmi", "architecture", "pageSize", "cfiMode")) put(field, manifest.getValue(field))
                put("manifestSha256", digest); put("prefix", prefix)
            }
        }
        assets["ultra-init-bridge/catalog-kmi.json"] = buildJsonObject {
            put("schema", "maplesu-init-bridge-catalog/2"); put("sourceGenerationSha256", "a".repeat(64))
            put("components", JsonArray(entries))
        }.toString().toByteArray()
        return assets
    }

    @Test fun absentCatalogRetainsActualDefaultImporter() {
        val bundle = bundle()
        val files = temporary.newFolder()
        val target = target(bundle)
        val actual = UltraInitBootCatalog.resolve(files, opener(bundle.mapKeys { "ultra-init-bridge/" + it.key }), target)!!
        assertEquals(UltraKernelSpec.sha256(target), actual.targetBootSha256)
        assertEquals(UltraKernelSpec.sha256(bundle.getValue("bridge.ko")), actual.bridgeSha256)
    }

    @Test fun exactTargetUsesRealResolverAndImporter() {
        val bundle = bundle()
        val packaged = catalog(listOf(bundle))
        val actual = UltraInitBootCatalog.resolve(temporary.newFolder(), opener(packaged), target(bundle))!!
        assertEquals(UltraKernelSpec.sha256(bundle.getValue("bridge.ko")), actual.bridgeSha256)
    }

    @Test fun targetAbiRejectionTriesNextButDoesNotQualifyByMetadata() {
        val first = bundle()
        val second = bundle(1)
        val firstHash = UltraKernelSpec.sha256(first.getValue("bridge.ko"))
        var calls = 0
        val actual = UltraInitBootCatalog.resolve(temporary.newFolder(), opener(catalog(listOf(first, second))), target(first),
            resolver = { artifact, image, claims ->
                calls++
                if (artifact.bridgeSha256 == firstHash) error("ULTRA_INIT_TARGET_CONFIG_ABI:CONFIG_CFI")
                UltraInitBootTargetResolver.resolve(artifact, image, claims)
            })!!
        assertEquals(2, calls)
        assertEquals(UltraKernelSpec.sha256(second.getValue("bridge.ko")), actual.bridgeSha256)
    }

    @Test fun noMatchingTemplateRetainsEachActualReason() {
        val first = bundle()
        val error = runCatching {
            UltraInitBootCatalog.resolve(temporary.newFolder(), opener(catalog(listOf(first, bundle(1)))), target(first),
                resolver = { _, _, _ -> error("ULTRA_INIT_TARGET_LOCAL_ABI:module") })
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().startsWith("ULTRA_INIT_TARGET_ARTIFACT_UNAVAILABLE:"))
        assertEquals(2, error?.message.orEmpty().split("ULTRA_INIT_TARGET_LOCAL_ABI:module").size - 1)
    }

    @Test fun templateCorruptionIsNotTreatedAsTargetMismatch() {
        val first = bundle()
        var calls = 0
        val error = runCatching {
            UltraInitBootCatalog.resolve(temporary.newFolder(), opener(catalog(listOf(first, bundle(1)))), target(first),
                resolver = { _, _, _ -> calls++; error("ULTRA_INIT_TEMPLATE_CRC:module_layout") })
        }.exceptionOrNull()
        assertEquals("ULTRA_INIT_TEMPLATE_CRC:module_layout", error?.message)
        assertEquals(1, calls)
    }

    @Test fun mixedGenerationFailsBeforeImport() {
        val first = bundle()
        val assets = catalog(listOf(first))
        val index = Json.parseToJsonElement(assets.getValue("ultra-init-bridge/catalog.json").decodeToString()).jsonObject
        val row = index.getValue("variants").jsonArray.single().jsonObject
        assets["ultra-init-bridge/catalog.json"] = JsonObject(index + ("variants" to JsonArray(listOf(
            JsonObject(row + ("sourceGenerationSha256" to JsonPrimitive("2".repeat(64)))))))).toString().toByteArray()
        val files = temporary.newFolder()
        val error = runCatching { UltraInitBootCatalog.resolve(files, opener(assets), target(first)) }.exceptionOrNull()
        assertEquals("ULTRA_INIT_CATALOG_GENERATION", error?.message)
        assertTrue(files.listFiles().orEmpty().isEmpty())
    }

    @Test fun changedManifestFailsBeforeImporterSelection() {
        val first = bundle()
        val assets = catalog(listOf(first))
        assets["ultra-init-bridge/manifest.json"] = assets.getValue("ultra-init-bridge/manifest.json") + byteArrayOf(10)
        val error = runCatching { UltraInitBootCatalog.resolve(temporary.newFolder(), opener(assets), target(first)) }.exceptionOrNull()
        assertEquals("ULTRA_INIT_CATALOG_MANIFEST_HASH", error?.message)
    }

    @Test fun changedProofIsNotSkippedByExactTarget() {
        val first = bundle()
        val assets = catalog(listOf(first, bundle(1)))
        val proof = assets.keys.last { it.startsWith("ultra-init-bridge/catalog-proofs/") }
        assets[proof] = assets.getValue(proof) + byteArrayOf(10)
        val files = temporary.newFolder()
        val error = runCatching { UltraInitBootCatalog.resolve(files, opener(assets), target(first)) }.exceptionOrNull()
        assertEquals("ULTRA_INIT_CATALOG_PROOF_HASH", error?.message)
        assertTrue(files.listFiles().orEmpty().isEmpty())
    }

    @Test fun changedBridgeRetainsComponentHashFailure() {
        val first = bundle()
        val assets = catalog(listOf(first))
        assets["ultra-init-bridge/bridge.ko"] = byteArrayOf(0)
        val error = runCatching { UltraInitBootCatalog.resolve(temporary.newFolder(), opener(assets), target(first)) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().startsWith("ULTRA_INIT_ARTIFACT_HASH:bridge"))
    }

    @Test fun declaredMissingManifestDoesNotReuseAnOlderPrivateSelection() {
        val first = bundle()
        val files = temporary.newFolder()
        val packaged = catalog(listOf(first))
        UltraInitBootCatalog.resolve(files, opener(packaged), target(first))
        packaged.remove("ultra-init-bridge/manifest.json")
        val error = runCatching { UltraInitBootCatalog.resolve(files, opener(packaged), target(first)) }.exceptionOrNull()
        assertEquals("ULTRA_INIT_CATALOG_ASSET_MISSING:manifest.json", error?.message)
    }

    private fun target(bundle: Map<String, ByteArray>): File = temporary.newFile().apply {
        writeBytes(bundle.getValue("target-boot.img"))
    }

    private fun opener(assets: Map<String, ByteArray>): (String) -> InputStream = { path ->
        ByteArrayInputStream(assets[path] ?: throw FileNotFoundException(path))
    }

    private fun catalog(bundles: List<Map<String, ByteArray>>): MutableMap<String, ByteArray> {
        val assets = linkedMapOf<String, ByteArray>()
        val entries = bundles.mapIndexed { index, bundle ->
            val encoded = bundle.getValue("manifest.json")
            val manifest = Json.parseToJsonElement(encoded.decodeToString()).jsonObject
            val digest = UltraKernelSpec.sha256(encoded)
            val prefix = if (index == 0) "" else "variants/$digest/"
            for ((path, bytes) in bundle) assets["ultra-init-bridge/$prefix$path"] = bytes
            val proof = buildJsonObject {
                put("schema", "maplesu-init-bridge-variant/1")
                put("manifestSha256", digest); put("sourceGenerationSha256", generation)
                put("targetBootSha256", manifest.getValue("targetBootSha256"))
                put("kernelSha256", manifest.getValue("kernelSha256"))
            }.toString().toByteArray()
            val proofPath = "catalog-proofs/$digest.json"
            assets["ultra-init-bridge/$proofPath"] = proof
            buildJsonObject {
                put("manifestSha256", digest); put("prefix", prefix); put("sourceGenerationSha256", generation)
                put("targetBootSha256", manifest.getValue("targetBootSha256"))
                put("kernelSha256", manifest.getValue("kernelSha256"))
                put("proof", proofPath); put("proofSha256", UltraKernelSpec.sha256(proof))
            }
        }
        assets["ultra-init-bridge/catalog.json"] = buildJsonObject {
            put("schema", "maplesu-init-bridge-catalog/1"); put("sourceGenerationSha256", generation)
            put("defaultManifestSha256", entries.first().getValue("manifestSha256"))
            put("variants", JsonArray(entries))
        }.toString().toByteArray()
        return assets
    }

    private fun bundle(variant: Int = 0): Map<String, ByteArray> {
        val fixture = Class.forName("dev.maplesu.image.UltraInitBootProducerTest")
        fun bytes(name: String, vararg args: Any): ByteArray {
            val method = fixture.getDeclaredMethod(name, *args.map { it.javaClass }.toTypedArray()).apply { isAccessible = true }
            return method.invoke(null, *args) as ByteArray
        }
        val kernel = bytes("kernel")
        val bridge = bytes("module").also {
            // The exact-target resolver reads a real ELF symbol table, including its null entry.
            val elf = ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN)
            val symbols = elf.getLong(40).toInt() + 3 * elf.getShort(58).toInt()
            elf.putInt(symbols + 40, 1).putLong(symbols + 56, 24)
            it[it.lastIndex] = variant.toByte()
        }
        val files = linkedMapOf("target-boot.img" to bytes("image", kernel, ByteArray(0)),
            "ultra-init" to bytes("handoff"), "bridge.ko" to bridge,
            "target.config" to "CONFIG_ARM64=y\nCONFIG_MODULES=y\nCONFIG_ARM64_4K_PAGES=y\n".toByteArray(),
            "Module.symvers" to "fixture symvers\n".toByteArray(), "target-contract.json" to "{}\n".toByteArray())
        files["manifest.json"] = buildJsonObject {
            put("schemaVersion", 1); put("kernelRelease", "6.6.1-fixture")
            put("kernelSha256", UltraKernelSpec.sha256(kernel))
            put("credentialSha256", UltraKernelSpec.sha256("A".repeat(48).toByteArray()))
            for ((field, path) in mapOf("targetBoot" to "target-boot.img", "handoff" to "ultra-init",
                "bridge" to "bridge.ko", "config" to "target.config", "moduleSymvers" to "Module.symvers",
                "targetContract" to "target-contract.json")) {
                put(field, path); put(field + "Sha256", UltraKernelSpec.sha256(files.getValue(path)))
            }
        }.toString().toByteArray()
        return files
    }
}
