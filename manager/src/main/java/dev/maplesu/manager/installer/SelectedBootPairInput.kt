package dev.maplesu.manager.installer

import dev.maplesu.manager.workmode.Hashes
import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.file.Files
import java.security.MessageDigest

/** Keeps the explicitly selected companion alive through preview or candidate production. */
internal object SelectedBootPairInput {
    data class Identity(val sourceSha256: String, val pairedBootSha256: String)

    fun <T> use(source: File, input: InputStream, directory: File, maximumBytes: Long,
        expected: Identity? = null, consume: (File, Identity) -> T): T = input.use { stream ->
        check(source.isFile && source.length() in 1..maximumBytes) { "ULTRA_INIT_SELECTED_SOURCE_INVALID" }
        val sourceHash = Hashes.sha256(source)
        check(expected == null || expected.sourceSha256 == sourceHash) { "ULTRA_INIT_SELECTED_SOURCE_CHANGED" }
        val paired = Files.createTempFile(directory.toPath(), "selected-paired-boot-", ".img").toFile()
        var primary: Throwable? = null
        try {
            val copied = MessageDigest.getInstance("SHA-256")
            paired.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var size = 0L
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedIOException("INSTALLER_CANCELLED")
                    val count = stream.read(buffer)
                    if (count < 0) break
                    check(count > 0 && count <= maximumBytes - size) { "IMAGE_SIZE_LIMIT" }
                    output.write(buffer, 0, count)
                    copied.update(buffer, 0, count)
                    size += count
                }
                check(size > 0) { "IMAGE_EMPTY" }
                output.fd.sync()
            }
            val copiedHash = copied.digest().joinToString("") { "%02x".format(it) }
            check(Hashes.sha256(paired) == copiedHash) { "ULTRA_INIT_SELECTED_PAIR_CHANGED" }
            val identity = Identity(sourceHash, copiedHash)
            check(expected == null || expected == identity) { "ULTRA_INIT_SELECTED_PAIR_CHANGED" }
            check(Hashes.sha256(source) == sourceHash) { "ULTRA_INIT_SELECTED_SOURCE_CHANGED" }
            val result = consume(paired, identity)
            check(Hashes.sha256(source) == sourceHash) { "ULTRA_INIT_SELECTED_SOURCE_CHANGED" }
            check(Hashes.sha256(paired) == identity.pairedBootSha256) { "ULTRA_INIT_SELECTED_PAIR_CHANGED" }
            result
        } catch (error: Throwable) {
            primary = error
            throw error
        } finally {
            try { Files.deleteIfExists(paired.toPath()) }
            catch (cleanup: Throwable) { if (primary != null) primary.addSuppressed(cleanup) else throw cleanup }
        }
    }
}
