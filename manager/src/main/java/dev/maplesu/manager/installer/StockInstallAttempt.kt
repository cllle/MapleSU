package dev.maplesu.manager.installer

import dev.maplesu.manager.workmode.Hashes
import dev.maplesu.installer.BlockDeviceTransaction
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** Keep pre-commit cancellation separate from an accepted commit whose reply may be lost. */
internal object StockInstallAttempt {
    fun <T> run(
        source: File,
        partition: String,
        prepare: () -> Unit,
        acquireSource: () -> Unit,
        produce: (File) -> InstallCandidateImage,
        beforeCommit: () -> Unit,
        commit: (InstallCandidateImage) -> T,
        cancel: () -> Unit,
        shouldCancel: () -> Boolean = { true },
        failureAt: (String, Throwable) -> Throwable = { _, error -> error },
    ): T {
        var commitStarted = false
        var failure: Throwable? = null
        var phase = "PREPARE"
        try {
            prepare()
            phase = "SOURCE"
            acquireSource()
            phase = "PRODUCE"
            val candidate = produce(source)
            phase = "VALIDATE"
            check(candidate.partition == partition && candidate.size in 1..BlockDeviceTransaction.MAX_BYTES &&
                Files.isRegularFile(candidate.image.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                candidate.image.length() == candidate.size && Hashes.sha256(candidate.image) == candidate.sha256) {
                "INSTALLER_CANDIDATE_CHANGED"
            }
            phase = "BEFORE_COMMIT"
            beforeCommit()
            commitStarted = true
            phase = "COMMIT"
            return commit(candidate)
        } catch (error: Throwable) {
            failure = try { failureAt(phase, error) } catch (diagnostic: Throwable) {
                if (diagnostic !== error) error.addSuppressed(diagnostic)
                error
            }
            throw failure
        } finally {
            source.delete()
            // A disconnected commit may still be writing. Its durable record owns recovery.
            if (!commitStarted && shouldCancel()) try { cancel() } catch (error: Exception) {
                val cleanup = IllegalStateException("INSTALLER_CANCELLATION_FAILED", error)
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }
}
