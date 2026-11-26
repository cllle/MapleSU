package dev.maplesu.manager.installer

import dev.maplesu.manager.workmode.Hashes
import dev.maplesu.installer.BlockDeviceTransaction
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class StockInstallAttemptTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun failedCleanupKeepsTheOriginalCancellationPrimary() {
        val source = temporary.newFile("cancel-cleanup-source.img")
        val cancelled = java.util.concurrent.CancellationException("cancelled")
        val failure = assertThrows(java.util.concurrent.CancellationException::class.java) {
            StockInstallAttempt.run<Unit>(source, "boot", prepare = { throw cancelled },
                acquireSource = {}, produce = { error("unexpected producer") }, beforeCommit = {},
                commit = { error("unexpected commit") }, cancel = { throw IOException("cleanup failure") })
        }
        assertEquals(cancelled, failure)
        assertEquals("INSTALLER_CANCELLATION_FAILED", failure.suppressed.single().message)
        assertEquals("cleanup failure", failure.suppressed.single().cause?.message)
        assertFalse(source.exists())
    }

    private fun runAttempt(failAt: String = "", mutate: ((InstallCandidateImage) -> InstallCandidateImage)? = null): Pair<List<String>, String> {
        val events = mutableListOf<String>()
        val source = File(temporary.root, "source.img")
        val candidate = temporary.newFile("candidate-${System.nanoTime()}.img").apply { writeText("patched candidate") }
        fun step(name: String) {
            events += name
            if (failAt == name) throw IOException(name)
        }
        try {
            val result = StockInstallAttempt.run(source, "boot", prepare = { step("prepare"); step("preparedCallback") },
                acquireSource = { source.writeText("source image"); step("source") },
                produce = {
                    assertEquals("source image", it.readText())
                    step("produce")
                    InstallCandidateImage(candidate, Hashes.sha256(candidate), candidate.length(), "boot").let { image ->
                        mutate?.invoke(image) ?: image
                    }
                }, beforeCommit = { step("writingCallback") }, commit = {
                    assertEquals(candidate, it.image)
                    step("commit")
                    "REBOOT_REQUIRED"
                }, cancel = { step("cancel") })
            return events to result
        } catch (error: Exception) {
            return events to error.message.orEmpty()
        } finally { assertFalse("temporary source survived", source.exists()) }
    }

    @Test fun completesPrepareSourceProduceCommitInOrder() {
        val (events, result) = runAttempt()
        assertEquals("REBOOT_REQUIRED", result)
        assertEquals(listOf("prepare", "preparedCallback", "source", "produce", "writingCallback", "commit"), events)
    }

    @Test fun failedPrepareCancelsPossiblePreparedTask() {
        val (events, result) = runAttempt("prepare")
        assertEquals("prepare", result)
        assertEquals(listOf("prepare", "cancel"), events)
    }

    @Test fun failedPreparedCallbackDoesNotStrandPreparedTask() {
        val (events, result) = runAttempt("preparedCallback")
        assertEquals("preparedCallback", result)
        assertEquals(listOf("prepare", "preparedCallback", "cancel"), events)
    }

    @Test fun failedSourceReadCancelsAndDeletesPartialSource() {
        val (events, result) = runAttempt("source")
        assertEquals("source", result)
        assertEquals("cancel", events.last())
        assertFalse(events.contains("produce"))
    }

    @Test fun failedProducerCancelsPreparedTask() {
        val (events, result) = runAttempt("produce")
        assertEquals("produce", result)
        assertEquals("cancel", events.last())
        assertFalse(events.contains("commit"))
    }

    @Test fun failedWritingCallbackCancelsBeforeCommit() {
        val (events, result) = runAttempt("writingCallback")
        assertEquals("writingCallback", result)
        assertEquals("cancel", events.last())
        assertFalse(events.contains("commit"))
    }

    @Test fun lostCommitReplyPreservesTaskForRecovery() {
        val (events, result) = runAttempt("commit")
        assertEquals("commit", result)
        assertEquals("commit", events.last())
        assertFalse(events.contains("cancel"))
    }

    @Test fun wrongPartitionNeverReachesCommit() {
        val (events, result) = runAttempt(mutate = { it.copy(partition = "init_boot") })
        assertEquals("INSTALLER_CANDIDATE_CHANGED", result)
        assertEquals("cancel", events.last())
        assertFalse(events.contains("writingCallback"))
    }

    @Test fun wrongDigestNeverReachesCommit() {
        val (events, result) = runAttempt(mutate = { it.copy(sha256 = "f".repeat(64)) })
        assertEquals("INSTALLER_CANDIDATE_CHANGED", result)
        assertEquals("cancel", events.last())
        assertFalse(events.contains("commit"))
    }

    @Test fun wrongSizeNeverReachesCommit() {
        val (events, result) = runAttempt(mutate = { it.copy(size = it.size + 1) })
        assertEquals("INSTALLER_CANDIDATE_CHANGED", result)
        assertEquals("cancel", events.last())
        assertFalse(events.contains("commit"))
    }

    @Test fun failedCancelIsVisibleAndRetainsOriginalError() {
        val source = temporary.newFile("source.img")
        val failure = assertThrows(IOException::class.java) {
            StockInstallAttempt.run<Unit>(source, "boot", prepare = { throw IOException("prepare failure") },
                acquireSource = {}, produce = { error("unexpected producer") }, beforeCommit = {},
                commit = { error("unexpected commit") }, cancel = { throw IOException("cancel failure") })
        }
        assertEquals("prepare failure", failure.message)
        assertEquals("INSTALLER_CANCELLATION_FAILED", failure.suppressed.single().message)
        assertEquals("cancel failure", failure.suppressed.single().cause?.message)
        assertFalse(source.exists())
    }

    @Test fun rootConnectionFailureBeforePreparePreservesOriginalExceptionWithoutCancel() {
        val source = temporary.newFile("connection-source.img")
        var cancelled = false
        val authorization = IOException("root connection failed")
        val failure = assertThrows(IOException::class.java) {
            StockInstallAttempt.run<Unit>(source, "boot", prepare = { throw authorization },
                acquireSource = {}, produce = { error("unexpected producer") }, beforeCommit = {},
                commit = { error("unexpected commit") }, cancel = { cancelled = true }, shouldCancel = { false })
        }
        assertEquals(authorization, failure)
        assertFalse(cancelled)
        assertFalse(source.exists())
    }

    @Test fun diagnosticReceivesTheExactFailureStageAndCommitIsNeverReplayed() {
        for (phase in listOf("PREPARE", "SOURCE", "PRODUCE", "VALIDATE", "BEFORE_COMMIT", "COMMIT")) {
            val source = File(temporary.root, "phase-source.img")
            val image = temporary.newFile("phase-$phase.img").apply { writeText("candidate") }
            val original = IOException("fixture failure")
            var observed: String? = null
            var cancelled = false
            val error = assertThrows(Exception::class.java) {
                fun step(at: String) { if (phase == at) throw original }
                StockInstallAttempt.run<Unit>(source, "boot", prepare = { step("PREPARE") },
                    acquireSource = { source.writeText("source"); step("SOURCE") },
                    produce = {
                        step("PRODUCE")
                        InstallCandidateImage(image, Hashes.sha256(image), image.length(),
                            if (phase == "VALIDATE") "init_boot" else "boot")
                    }, beforeCommit = { step("BEFORE_COMMIT") }, commit = { step("COMMIT") },
                    cancel = { cancelled = true }, failureAt = { at, failure -> observed = at; failure })
            }
            assertEquals(phase, observed)
            if (phase != "VALIDATE") assertEquals(original, error)
            assertEquals(phase != "COMMIT", cancelled)
            assertFalse(source.exists())
        }
    }

    @Test fun failedDiagnosticPreservesCancellationAndStillCleansPreparedTask() {
        val source = temporary.newFile("cancelled-source.img")
        val cancelled = java.util.concurrent.CancellationException("cancelled")
        var cleanup = false
        val failure = assertThrows(java.util.concurrent.CancellationException::class.java) {
            StockInstallAttempt.run<Unit>(source, "boot", prepare = { throw cancelled },
                acquireSource = {}, produce = { error("unexpected producer") }, beforeCommit = {},
                commit = { error("unexpected commit") }, cancel = { cleanup = true },
                failureAt = { _, _ -> error("diagnostic failure") })
        }
        assertEquals(cancelled, failure)
        assertEquals("diagnostic failure", failure.suppressed.single().message)
        assertEquals(true, cleanup)
        assertFalse(source.exists())
    }

    @Test fun appAttemptReachesRealWriteReadbackAndCleansItsSource() {
        exercisePartitionTransaction(failWrite = false)
    }

    @Test fun appAttemptKeepsRestoredCommitResultAndDoesNotCancelIt() {
        exercisePartitionTransaction(failWrite = true)
    }

    private fun exercisePartitionTransaction(failWrite: Boolean) {
        val bytes = ByteArray(4096) { 1 }
        val original = bytes.clone()
        var writes = 0
        var cancelled = false
        val device = object : BlockDeviceTransaction.Device {
            override fun size() = bytes.size.toLong()
            override fun revalidate() = Unit
            override fun read(offset: Long, buffer: ByteArray, length: Int): Int {
                bytes.copyInto(buffer, 0, offset.toInt(), offset.toInt() + length)
                return length
            }
            override fun write(offset: Long, buffer: ByteArray, start: Int, length: Int): Int {
                if (++writes == 1 && failWrite) throw IOException("INSTALLER_DEVICE_WRITE_FAILED")
                buffer.copyInto(bytes, offset.toInt(), start, start + length)
                return length
            }
            override fun sync() = Unit
            override fun close() = Unit
        }
        val pre = temporary.newFile("pre.img").apply { writeBytes(original) }
        val source = File(temporary.root, "service-source.img")
        val outcome = StockInstallAttempt.run(source, "boot", prepare = {},
            acquireSource = { pre.copyTo(source) }, produce = {
                val candidate = temporary.newFile("transaction-candidate.img").apply {
                    writeBytes(it.readBytes().also { image -> image.fill(3) })
                }
                InstallCandidateImage(candidate, Hashes.sha256(candidate), candidate.length(), "boot")
            }, beforeCommit = {}, commit = { candidate ->
                NativeInstallSequence.commit(mapOf("boot" to device),
                    mapOf("boot" to NativeInstallSequence.Image("boot", pre, Hashes.sha256(pre))),
                    listOf(NativeInstallSequence.Image("boot", candidate.image, candidate.sha256)),
                    NativeInstallSequence.Journal { _, _, _ -> })
            }, cancel = { cancelled = true })
        assertEquals(if (failWrite) "RESTORED" else "REBOOT_REQUIRED", outcome.state)
        assertArrayEquals(if (failWrite) original else ByteArray(4096) { 3 }, bytes)
        assertFalse(source.exists())
        assertFalse(cancelled)
    }
}
