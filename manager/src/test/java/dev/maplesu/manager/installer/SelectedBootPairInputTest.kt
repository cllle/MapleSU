package dev.maplesu.manager.installer

import dev.maplesu.manager.workmode.Hashes
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real copied inputs, expected identities and lifetime; no native ABI/load claim. */
class SelectedBootPairInputTest {
    @get:Rule val temporary = TemporaryFolder()
    private val sourceBytes = "selected-init-boot".toByteArray()
    private val pairedBytes = "explicit-companion-boot".toByteArray()
    private fun source() = temporary.newFile().apply { writeBytes(sourceBytes) }
    private class Tracked(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    @Test fun actualCompanionLivesThroughTheConsumerThenIsRemoved() {
        val source = source()
        val stream = Tracked(pairedBytes)
        var snapshot: File? = null
        val result = SelectedBootPairInput.use(source, stream, temporary.root, 128) { paired, identity ->
            snapshot = paired
            assertTrue(paired.isFile)
            assertArrayEquals(pairedBytes, paired.readBytes())
            assertEquals(Hashes.sha256(sourceBytes), identity.sourceSha256)
            assertEquals(Hashes.sha256(pairedBytes), identity.pairedBootSha256)
            "consumed"
        }
        assertEquals("consumed", result)
        assertTrue(stream.closed)
        assertFalse(requireNotNull(snapshot).exists())
        assertArrayEquals(sourceBytes, source.readBytes())
    }

    @Test fun previewIdentityIsRequiredAgainByTheActualConsumer() {
        val source = source()
        val preview = SelectedBootPairInput.use(source, pairedBytes.inputStream(), temporary.root, 128) { _, identity -> identity }
        var calls = 0
        val actual = SelectedBootPairInput.use(source, pairedBytes.inputStream(), temporary.root, 128, preview) { _, identity ->
            calls++
            identity
        }
        assertEquals(preview, actual)
        assertEquals(1, calls)
    }

    @Test fun changedCompanionAtTheSameSelectionStopsBeforeConsumption() {
        val source = source()
        val expected = SelectedBootPairInput.Identity(Hashes.sha256(source), Hashes.sha256(pairedBytes))
        var calls = 0
        val failure = runCatching {
            SelectedBootPairInput.use(source, "other-companion".byteInputStream(), temporary.root, 128, expected) { _, _ -> calls++ }
        }.exceptionOrNull()
        assertEquals("ULTRA_INIT_SELECTED_PAIR_CHANGED", failure?.message)
        assertEquals(0, calls)
        assertEquals(listOf(source.name), temporary.root.listFiles()!!.map { it.name })
    }

    @Test fun changedSourceStopsBeforeCreatingOrConsumingACompanion() {
        val source = source()
        val expected = SelectedBootPairInput.Identity(Hashes.sha256(source), Hashes.sha256(pairedBytes))
        source.writeText("changed-init-boot")
        val stream = Tracked(pairedBytes)
        var calls = 0
        val failure = runCatching {
            SelectedBootPairInput.use(source, stream, temporary.root, 128, expected) { _, _ -> calls++ }
        }.exceptionOrNull()
        assertEquals("ULTRA_INIT_SELECTED_SOURCE_CHANGED", failure?.message)
        assertEquals(0, calls)
        assertTrue(stream.closed)
        assertEquals(1, temporary.root.listFiles()!!.size)
    }

    @Test fun overLimitAndEmptyInputsStopBeforeConsumptionAndCleanTheirCopies() {
        for (bytes in listOf(ByteArray(0), ByteArray(33))) {
            val directory = temporary.newFolder()
            val source = File(directory, "source.img").apply { writeText("init") }
            var calls = 0
            val failure = runCatching {
                SelectedBootPairInput.use(source, bytes.inputStream(), directory, 32) { _, _ -> calls++ }
            }.exceptionOrNull()
            assertEquals(if (bytes.isEmpty()) "IMAGE_EMPTY" else "IMAGE_SIZE_LIMIT", failure?.message)
            assertEquals(0, calls)
            assertEquals(listOf(source.name), directory.listFiles()!!.map { it.name })
        }
    }

    @Test fun inputFailureRemainsThePrimaryErrorAndClosesTheStream() {
        val source = source()
        val expected = IOException("input failed")
        var closed = false
        val stream = object : InputStream() {
            override fun read(): Int = throw expected
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw expected
            override fun close() { closed = true }
        }
        val actual = runCatching { SelectedBootPairInput.use(source, stream, temporary.root, 128) { _, _ -> fail("consumer") } }.exceptionOrNull()
        assertSame(expected, actual)
        assertTrue(closed)
        assertEquals(1, temporary.root.listFiles()!!.size)
    }

    @Test fun mutationDuringConsumptionIsDetectedBeforeTheCandidateCanBePublished() {
        for (mutateSource in listOf(false, true)) {
            val source = source()
            var snapshot: File? = null
            val failure = runCatching {
                SelectedBootPairInput.use(source, pairedBytes.inputStream(), temporary.root, 128) { paired, _ ->
                    snapshot = paired
                    (if (mutateSource) source else paired).appendText("changed")
                    "candidate"
                }
            }.exceptionOrNull()
            assertEquals(if (mutateSource) "ULTRA_INIT_SELECTED_SOURCE_CHANGED" else "ULTRA_INIT_SELECTED_PAIR_CHANGED", failure?.message)
            assertFalse(requireNotNull(snapshot).exists())
        }
    }

    @Test fun sourceMutationWhileCopyingTheCompanionStopsBeforeConsumption() {
        val source = source()
        val stream = object : ByteArrayInputStream(pairedBytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                source.appendText("changed")
                return super.read(buffer, offset, length)
            }
        }
        var calls = 0
        val failure = runCatching {
            SelectedBootPairInput.use(source, stream, temporary.root, 128) { _, _ -> calls++ }
        }.exceptionOrNull()
        assertEquals("ULTRA_INIT_SELECTED_SOURCE_CHANGED", failure?.message)
        assertEquals(0, calls)
        assertEquals(1, temporary.root.listFiles()!!.size)
    }

    @Test fun cleanupFailureIsSuppressedBehindTheRealConsumerFailure() {
        val source = source()
        val expected = IOException("consumer failed")
        val actual = runCatching {
            SelectedBootPairInput.use(source, pairedBytes.inputStream(), temporary.root, 128) { paired, _ ->
                assertTrue(paired.delete())
                assertTrue(paired.mkdir())
                File(paired, "child").writeText("owned fixture")
                throw expected
            }
        }.exceptionOrNull()
        assertSame(expected, actual)
        assertEquals(1, expected.suppressed.size)
    }

    @Test fun inputCloseFailureAfterSuccessPropagatesAfterDeletingTheCopy() {
        val source = source()
        val expected = IOException("input close failed")
        val stream = object : ByteArrayInputStream(pairedBytes) {
            override fun close() { throw expected }
        }
        var calls = 0
        val actual = runCatching {
            SelectedBootPairInput.use(source, stream, temporary.root, 128) { _, _ -> calls++ }
        }.exceptionOrNull()
        assertEquals(1, calls)
        assertSame(expected, actual)
        assertEquals(listOf(source), temporary.root.listFiles()!!.toList())
    }

    @Test fun inputCloseFailureIsSuppressedBehindTheConsumerFailure() {
        val source = source()
        val expected = IOException("consumer failed")
        val closing = IOException("input close failed")
        val stream = object : ByteArrayInputStream(pairedBytes) {
            override fun close() { throw closing }
        }
        val actual = runCatching {
            SelectedBootPairInput.use(source, stream, temporary.root, 128) { _, _ -> throw expected }
        }.exceptionOrNull()
        assertSame(expected, actual)
        assertArrayEquals(arrayOf(closing), expected.suppressed)
        assertEquals(listOf(source), temporary.root.listFiles()!!.toList())
    }

    @Test fun cleanupFailureAfterConsumerSuccessIsPropagatedOnWindows() {
        org.junit.Assume.assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val source = source()
        var snapshot: File? = null
        var completed = false
        try {
            val actual = runCatching {
                SelectedBootPairInput.use(source, pairedBytes.inputStream(), temporary.root, 128) { paired, _ ->
                    snapshot = paired
                    requireNotNull(java.nio.file.Files.getFileAttributeView(paired.toPath(),
                        java.nio.file.attribute.DosFileAttributeView::class.java)).setReadOnly(true)
                    completed = true
                    "consumer completed"
                }
            }.exceptionOrNull()
            assertTrue(completed)
            assertTrue(actual is java.nio.file.AccessDeniedException)
            assertTrue(requireNotNull(snapshot).isFile)
        } finally {
            snapshot?.let { paired ->
                requireNotNull(java.nio.file.Files.getFileAttributeView(paired.toPath(),
                    java.nio.file.attribute.DosFileAttributeView::class.java)).setReadOnly(false)
                java.nio.file.Files.deleteIfExists(paired.toPath())
            }
        }
        assertEquals(listOf(source), temporary.root.listFiles()!!.toList())
    }
}
