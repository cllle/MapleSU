package dev.maplesu.manager.ultrakernel

import dev.maplesu.image.UltraInitBootProducer
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.file.Files

/** Target templates and reusable runtime components retain separate selection contracts. */
internal object UltraInitBootCatalog {
    private const val ROOT = "ultra-init-bridge"
    private val hash = Regex("[0-9a-f]{64}")
    private data class Entry(val manifest: String, val prefix: String, val target: String,
        val kernel: String, val proof: String, val proofHash: String)
    private data class ComponentEntry(val kmi: String, val architecture: String, val page: Int,
        val cfi: String, val manifest: String, val prefix: String, val generation: String)

    @Suppress("UNUSED_PARAMETER")
    fun availableKmis(filesDir: File, open: (String) -> InputStream): List<String> =
        componentEntries(open).map { it.kmi }.distinct().sorted()

    fun resolveKmi(filesDir: File, open: (String) -> InputStream, kmi: String): UltraInitBootProducer.Artifact? {
        check(kmi.matches(Regex("android[0-9]+-[0-9]+\\.[0-9]+"))) { "ULTRA_INIT_KMI_INVALID:$kmi" }
        val matches = componentEntries(open).filter { it.kmi == kmi }
        check(matches.size <= 1) { "ULTRA_INIT_KMI_COMPONENT_AMBIGUOUS:$kmi" }
        return matches.singleOrNull()?.let { loadComponent(filesDir, open, it) }
    }

    private fun componentEntries(open: (String) -> InputStream): List<ComponentEntry> {
        var legacyFallback = false
        val encoded = try { open("$ROOT/catalog-kmi.json").use { bounded(it, 262144) } }
            catch (_: FileNotFoundException) {
                legacyFallback = true
                try { open("$ROOT/catalog.json").use { bounded(it, 262144) } }
                catch (_: FileNotFoundException) { return emptyList() }
            }
        val catalog = json(encoded)
        if (legacyFallback && string(catalog, "schema") == "maplesu-init-bridge-catalog/1") return emptyList()
        check(catalog.keys == setOf("schema", "sourceGenerationSha256", "components") &&
            string(catalog, "schema") == "maplesu-init-bridge-catalog/2") { "ULTRA_INIT_KMI_CATALOG_SCHEMA" }
        val generation = digest(catalog, "sourceGenerationSha256")
        val rows = catalog["components"] as? JsonArray ?: error("ULTRA_INIT_KMI_CATALOG_COMPONENTS")
        check(rows.size <= 64) { "ULTRA_INIT_KMI_CATALOG_COMPONENTS" }
        val identities = mutableSetOf<String>()
        val manifests = mutableSetOf<String>()
        return rows.map { value ->
            val row = value as? JsonObject ?: error("ULTRA_INIT_KMI_CATALOG_ENTRY")
            check(row.keys == setOf("kmi", "architecture", "pageSize", "cfiMode", "manifestSha256", "prefix")) {
                "ULTRA_INIT_KMI_CATALOG_FIELDS"
            }
            val kmi = string(row, "kmi")
            val architecture = string(row, "architecture")
            val cfi = string(row, "cfiMode")
            val page = (row["pageSize"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
                ?: error("ULTRA_INIT_KMI_CATALOG_PAGE")
            check(kmi.matches(Regex("android[0-9]+-[0-9]+\\.[0-9]+")) && architecture == "arm64" &&
                page in setOf(4096, 16384, 65536) && cfi in setOf("none", "kcfi", "legacy-lto-cfi")) {
                "ULTRA_INIT_KMI_CATALOG_IDENTITY"
            }
            val manifest = digest(row, "manifestSha256")
            val prefix = string(row, "prefix")
            check(prefix == "components/$manifest/") { "ULTRA_INIT_KMI_CATALOG_PREFIX" }
            check(identities.add("$kmi:$architecture:$page:$cfi") && manifests.add(manifest)) {
                "ULTRA_INIT_KMI_CATALOG_DUPLICATE"
            }
            ComponentEntry(kmi, architecture, page, cfi, manifest, prefix, generation)
        }
    }

    private fun loadComponent(filesDir: File, open: (String) -> InputStream,
        entry: ComponentEntry): UltraInitBootProducer.Artifact {
        val directory = File(filesDir, "ultra-init-component-${entry.manifest}")
        check(!Files.isSymbolicLink(directory.toPath())) { "ULTRA_INIT_CATALOG_PRIVATE_SYMLINK" }
        Files.createDirectories(directory.toPath())
        val mapped: (String) -> InputStream = { path ->
            check(path.startsWith("$ROOT/")) { "ULTRA_INIT_CATALOG_ASSET_PATH" }
            val relative = path.removePrefix("$ROOT/")
            val source = try { open("$ROOT/${entry.prefix}$relative") }
                catch (error: FileNotFoundException) {
                    throw IOException("ULTRA_INIT_KMI_COMPONENT_ASSET_MISSING:${entry.prefix}$relative", error)
                }
            if (relative == "manifest.json") {
                val bytes = source.use { bounded(it, 16384) }
                check(UltraKernelSpec.sha256(bytes) == entry.manifest) { "ULTRA_INIT_CATALOG_MANIFEST_HASH" }
                ByteArrayInputStream(bytes)
            } else source
        }
        val artifact = requireNotNull(UltraInitBootAssets.load(directory, mapped)) { "ULTRA_INIT_KMI_COMPONENT_MISSING" }
        val component = artifact.component
        check(artifact.isKmiComponent && component != null && component.kmi == entry.kmi &&
            component.architecture == entry.architecture && component.pageSize == entry.page && component.cfiMode == entry.cfi &&
            component.sourceGenerationSha256 == entry.generation) { "ULTRA_INIT_KMI_COMPONENT_IDENTITY" }
        return artifact
    }

    fun resolve(filesDir: File, open: (String) -> InputStream, pairedBoot: File,
        claimAssets: UltraKernelAssetSource? = null,
        resolver: (UltraInitBootProducer.Artifact, File, UltraKernelAssetSource?) -> UltraInitBootProducer.Artifact =
            UltraInitBootTargetResolver::resolve): UltraInitBootProducer.Artifact? {
        val encoded = try { open("$ROOT/catalog.json").use { bounded(it, 262144) } }
            catch (_: FileNotFoundException) { null }
        if (encoded == null) return UltraInitBootAssets.load(filesDir, open)?.let {
            resolver(it, pairedBoot, claimAssets)
        }
        val catalog = json(encoded)
        check(catalog.keys == setOf("schema", "sourceGenerationSha256", "defaultManifestSha256", "variants")) {
            "ULTRA_INIT_CATALOG_FIELDS"
        }
        check(string(catalog, "schema") == "maplesu-init-bridge-catalog/1") { "ULTRA_INIT_CATALOG_SCHEMA" }
        val generation = digest(catalog, "sourceGenerationSha256")
        val default = digest(catalog, "defaultManifestSha256")
        val rows = catalog["variants"] as? JsonArray ?: error("ULTRA_INIT_CATALOG_VARIANTS")
        check(rows.size in 1..64) { "ULTRA_INIT_CATALOG_VARIANTS" }
        val entries = rows.map { value ->
            val row = value as? JsonObject ?: error("ULTRA_INIT_CATALOG_ENTRY")
            check(row.keys == setOf("manifestSha256", "prefix", "targetBootSha256", "kernelSha256",
                "sourceGenerationSha256", "proof", "proofSha256")) { "ULTRA_INIT_CATALOG_ENTRY_FIELDS" }
            check(digest(row, "sourceGenerationSha256") == generation) { "ULTRA_INIT_CATALOG_GENERATION" }
            val manifest = digest(row, "manifestSha256")
            val prefix = string(row, "prefix")
            check(prefix == if (manifest == default) "" else "variants/$manifest/") { "ULTRA_INIT_CATALOG_PREFIX" }
            val proof = string(row, "proof")
            check(proof == "catalog-proofs/$manifest.json") { "ULTRA_INIT_CATALOG_PROOF_PATH" }
            Entry(manifest, prefix, digest(row, "targetBootSha256"), digest(row, "kernelSha256"),
                proof, digest(row, "proofSha256"))
        }
        check(entries.map { it.manifest }.distinct().size == entries.size && entries.count { it.manifest == default } == 1) {
            "ULTRA_INIT_CATALOG_DUPLICATE"
        }
        // Validate declared provenance even when an exact earlier match would otherwise hide a bad row.
        for (entry in entries) {
            val proofBytes = open("$ROOT/${entry.proof}").use { bounded(it, 1048576) }
            check(UltraKernelSpec.sha256(proofBytes) == entry.proofHash) { "ULTRA_INIT_CATALOG_PROOF_HASH" }
            val proof = json(proofBytes)
            check(string(proof, "schema") == "maplesu-init-bridge-variant/1" &&
                digest(proof, "manifestSha256") == entry.manifest &&
                digest(proof, "sourceGenerationSha256") == generation &&
                digest(proof, "targetBootSha256") == entry.target && digest(proof, "kernelSha256") == entry.kernel) {
                "ULTRA_INIT_CATALOG_PROOF_IDENTITY"
            }
        }
        val targetHash = UltraKernelSpec.sha256(pairedBoot)
        val targetKernelHash = UltraKernelSpec.sha256(UltraKernelPlan.readKernel(pairedBoot))
        val ordered = entries.sortedWith(compareBy<Entry> { if (it.target == targetHash) 0 else 1 }
            .thenBy { if (it.kernel == targetKernelHash) 0 else 1 }
            .thenBy { if (it.manifest == default) 0 else 1 }.thenBy { it.manifest })
        val rejected = mutableListOf<String>()
        for (entry in ordered) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("ULTRA_INIT_CATALOG_CANCELLED")
            val privateDir = if (entry.prefix.isEmpty()) filesDir else File(filesDir, "ultra-init-catalog-${entry.manifest}")
            check(!Files.isSymbolicLink(privateDir.toPath())) { "ULTRA_INIT_CATALOG_PRIVATE_SYMLINK" }
            Files.createDirectories(privateDir.toPath())
            val mapped: (String) -> InputStream = { path ->
                check(path.startsWith("$ROOT/")) { "ULTRA_INIT_CATALOG_ASSET_PATH" }
                val relative = path.removePrefix("$ROOT/")
                val source = try { open("$ROOT/${entry.prefix}$relative") }
                    catch (error: FileNotFoundException) {
                        throw IOException("ULTRA_INIT_CATALOG_ASSET_MISSING:${entry.prefix}$relative", error)
                    }
                if (relative == "manifest.json") {
                    val bytes = source.use { bounded(it, 16384) }
                    check(UltraKernelSpec.sha256(bytes) == entry.manifest) { "ULTRA_INIT_CATALOG_MANIFEST_HASH" }
                    ByteArrayInputStream(bytes)
                } else source
            }
            val template = requireNotNull(UltraInitBootAssets.load(privateDir, mapped)) { "ULTRA_INIT_CATALOG_ARTIFACT_MISSING" }
            check(template.targetBootSha256 == entry.target && template.kernelSha256 == entry.kernel) {
                "ULTRA_INIT_CATALOG_TARGET_IDENTITY"
            }
            try {
                val resolved = resolver(template, pairedBoot, claimAssets)
                check(resolved.targetBootSha256 == targetHash && UltraKernelSpec.sha256(pairedBoot) == targetHash) {
                    "ULTRA_INIT_INPUT_CHANGED"
                }
                check(resolved.kernelSha256 == targetKernelHash) { "ULTRA_INIT_CATALOG_RESOLVED_KERNEL" }
                return resolved
            } catch (error: IllegalStateException) {
                val reason = error.message.orEmpty()
                if (!targetMismatch(reason)) throw error
                rejected += "${entry.manifest}:$reason"
            }
        }
        check(UltraKernelSpec.sha256(pairedBoot) == targetHash) { "ULTRA_INIT_INPUT_CHANGED" }
        error("ULTRA_INIT_TARGET_ARTIFACT_UNAVAILABLE:" + rejected.joinToString(";"))
    }

    private fun targetMismatch(reason: String): Boolean = reason.startsWith("ULTRA_INIT_TARGET_") ||
        reason == "ULTRA_INIT_DYNAMIC_CREDENTIAL_TEMPLATE" || reason.startsWith("ULTRA_INIT_TASK_PROFILE_TARGET_PROOFS:")

    private fun json(bytes: ByteArray): JsonObject = try {
        Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)).jsonObject
    } catch (error: Exception) { throw IOException("ULTRA_INIT_CATALOG_JSON", error) }

    private fun string(row: JsonObject, name: String): String =
        (row[name] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("ULTRA_INIT_CATALOG_FIELD:$name")

    private fun digest(row: JsonObject, name: String): String = string(row, name).also {
        check(it.matches(hash)) { "ULTRA_INIT_CATALOG_HASH:$name" }
    }

    private fun bounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16384)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("ULTRA_INIT_CATALOG_CANCELLED")
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            if (count == 0 || count > limit - output.size()) throw IOException("ULTRA_INIT_CATALOG_SIZE")
            output.write(buffer, 0, count)
        }
    }
}
