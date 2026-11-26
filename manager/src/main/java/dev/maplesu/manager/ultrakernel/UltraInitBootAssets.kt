package dev.maplesu.manager.ultrakernel

import dev.maplesu.image.UltraInitBootProducer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Immutable private bundle generations keep in-flight Artifact target paths valid across upgrades. */
internal object UltraInitBootAssets {
    private const val ROOT = "ultra-init-bridge"
    private const val MANIFEST = "manifest.json"
    private val limits = mapOf(
        "targetBoot" to UltraKernelCatalog.MAX_IMAGE_BYTES,
        "handoff" to 16L * 1024 * 1024, "bridge" to 64L * 1024 * 1024,
        "targetContract" to 1024L * 1024, "config" to 1024L * 1024,
        "sessionAbi" to 1024L * 1024, "bundleSources" to 1024L * 1024,
        "taskProfileAbi" to 1024L * 1024, "abiEvidence" to 1024L * 1024,
        "moduleSymvers" to 64L * 1024 * 1024, "btf" to 64L * 1024 * 1024,
        "ksuInit" to 16L * 1024 * 1024,
    )

    @Synchronized
    fun load(filesDir: File, open: (String) -> InputStream,
             publishSelection: (File, File) -> Unit = { pending, selection ->
                 Files.move(pending.toPath(), selection.toPath(),
                     StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
             }): UltraInitBootProducer.Artifact? {
        val current = current(filesDir)
        val input = try { open("$ROOT/$MANIFEST") } catch (_: FileNotFoundException) { null }
        val manifest = input?.use { readManifest(it) }
        if (manifest == null) return current?.let(::read)
        val digest = sha256(manifest)
        if (current != null && sha256(File(current, MANIFEST).inputStream().use(::readManifest)) == digest)
            return read(current)
        val destination = File(filesDir, "$ROOT-$digest")
        val fields = try { Json.parseToJsonElement(manifest.decodeToString(throwOnInvalidSequence = true)).jsonObject }
            catch (error: Exception) { throw IOException("ULTRA_INIT_ASSET_MANIFEST", error) }
        val references = limits.mapNotNull { (field, limit) ->
            val value = fields[field] ?: return@mapNotNull null
            val path = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw IOException("ULTRA_INIT_ASSET_PATH:$field")
            if (!path.matches(Regex("[A-Za-z0-9._/-]{1,240}")) ||
                path.split('/').any { it.isEmpty() || it == "." || it == ".." } || path == MANIFEST)
                throw IOException("ULTRA_INIT_ASSET_PATH:$field")
            path to limit
        }
        if (references.map { it.first }.distinct().size != references.size)
            throw IOException("ULTRA_INIT_ASSET_PATH_CONFLICT")
        var owned: File? = null
        var pendingSelection: File? = null
        try {
            if (!Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                val pending = Files.createTempDirectory(filesDir.toPath(), ".ultra-init-bridge-").toFile()
                owned = pending
                for ((path, limit) in references) {
                    val output = File(pending, path)
                    Files.createDirectories(output.parentFile.toPath())
                    open("$ROOT/$path").use { source ->
                        output.outputStream().use { stream -> copy(source, stream, limit); stream.fd.sync() }
                    }
                }
                File(pending, MANIFEST).outputStream().use { stream -> stream.write(manifest); stream.fd.sync() }
                UltraInitBootProducer.readArtifact(File(pending, MANIFEST))
                // No replacement: the entire generation must exist before it can be selected.
                Files.move(pending.toPath(), destination.toPath())
                owned = null // Published generations may already be held by another App process.
            }
            val artifact = read(destination)
            if (sha256(File(destination, MANIFEST).inputStream().use(::readManifest)) != digest)
                throw IOException("ULTRA_INIT_ASSET_MANIFEST_CHANGED")
            val selection = Files.createTempFile(filesDir.toPath(), ".ultra-init-selection-", ".pending").toFile()
            pendingSelection = selection
            val selected = "$digest\n".toByteArray(Charsets.US_ASCII)
            selection.outputStream().use { stream -> stream.write(selected); stream.fd.sync() }
            if (!selection.readBytes().contentEquals(selected)) throw IOException("ULTRA_INIT_ASSET_SELECTION_READBACK")
            publishSelection(selection, File(filesDir, "$ROOT.current"))
            // Artifact has no close/lease: published generations follow App-private storage lifetime,
            // not this call's lifetime. Never unlink an old targetBoot held by another transaction/process.
            return artifact
        } catch (error: Throwable) {
            pendingSelection?.let { UltraKernelRecoveryEnvelope.cleanup(null, it, primary = error) }
            owned?.let { cleanup(it, error) }
            throw error
        }
    }

    private fun current(filesDir: File): File? {
        val selection = File(filesDir, "$ROOT.current")
        if (Files.exists(selection.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(selection.toPath()) || selection.length() != 65L)
                throw IOException("ULTRA_INIT_ASSET_SELECTION_INVALID")
            val digest = selection.readText(Charsets.US_ASCII)
            if (!digest.matches(Regex("[0-9a-f]{64}\n"))) throw IOException("ULTRA_INIT_ASSET_SELECTION_INVALID")
            return File(filesDir, "$ROOT-${digest.trimEnd()}")
        }
        val legacy = File(filesDir, ROOT)
        if (Files.isSymbolicLink(legacy.toPath())) throw IOException("ULTRA_INIT_ASSET_PRIVATE_SYMLINK")
        return legacy.takeIf { File(it, MANIFEST).isFile }
    }

    private fun read(directory: File): UltraInitBootProducer.Artifact {
        if (Files.isSymbolicLink(directory.toPath())) throw IOException("ULTRA_INIT_ASSET_PRIVATE_SYMLINK")
        return UltraInitBootProducer.readArtifact(File(directory, MANIFEST))
    }

    private fun readManifest(input: InputStream): ByteArray =
        ByteArrayOutputStream().also { copy(input, it, 16L * 1024) }.toByteArray()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun cleanup(directory: File, primary: Throwable) {
        try {
            Files.walk(directory.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { path ->
                    try { Files.deleteIfExists(path) } catch (error: Throwable) { primary.addSuppressed(error) }
                }
            }
        } catch (error: Throwable) { primary.addSuppressed(error) }
    }

    private fun copy(input: InputStream, output: OutputStream, limit: Long) {
        val buffer = ByteArray(64 * 1024)
        var size = 0L
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("ULTRA_INIT_ASSET_CANCELLED")
            val count = input.read(buffer)
            if (count < 0) return
            if (count == 0 || count > limit - size) throw IOException("ULTRA_INIT_ASSET_SIZE_LIMIT")
            output.write(buffer, 0, count)
            size += count
        }
    }
}
