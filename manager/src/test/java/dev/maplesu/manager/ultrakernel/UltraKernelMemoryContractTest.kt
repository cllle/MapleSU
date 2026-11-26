package dev.maplesu.manager.ultrakernel

import dev.maplesu.image.BootImageInspector
import dev.maplesu.image.KernelCompression
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.lang.ref.Reference
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class UltraKernelMemoryContractTest {
    @Test fun rawBuilderFitsDeviceSizedHeapWithRetainedManagerLoad() = child("builder", 256)

    @Test fun rawPlanReadAvoidsAnExtraKernelSizedArray() = child("read", 160)

    @Test fun publicRawCodecResultsRemainIndependent() {
        val original = byteArrayOf(1, 2, 3, 4)
        val results = listOf(
            KernelCompression.decode(original, "RAW_ARM64", original.size),
            KernelCompression.encode(original, "RAW_ARM64"),
            KernelCompression.encodeKernelComponent(original, original, "RAW_ARM64"),
        )
        results.forEach { result ->
            assertNotSame(original, result)
            assertArrayEquals(original, result)
            result[0] = 9
            assertEquals(1, original[0].toInt())
        }
    }

    @Test fun streamedReadbackChecksEveryByteAndReturnsItsIndependentHash() = temporary { work ->
        val base = fixture(work, 128 * 1024 + 17)
        val report = BootImageInspector.inspect(base)
        val expected = UltraKernelPlan.readKernel(base)
        val builder = UltraKernelBuilder(File(work, "out"))
        val verify = UltraKernelBuilder::class.java.getDeclaredMethod(
            "verifyKernelComponent", File::class.java, BootImageInspector.Report::class.java, ByteArray::class.java,
        ).apply { isAccessible = true }
        assertEquals(UltraKernelSpec.sha256(expected), verify.invoke(builder, base, report, expected))
        for (offset in listOf(0, 64 * 1024, expected.lastIndex)) {
            RandomAccessFile(base, "rw").use { it.seek(report.kernelOffset + offset); it.write(expected[offset].toInt() xor 1) }
            try {
                verify.invoke(builder, base, report, expected)
                fail("accepted changed component byte at " + offset)
            } catch (error: InvocationTargetException) {
                assertEquals("ULTRA_KERNEL_COMPONENT_READBACK_MISMATCH", error.cause?.message)
            } finally {
                RandomAccessFile(base, "rw").use { it.seek(report.kernelOffset + offset); it.write(expected[offset].toInt()) }
            }
        }
        RandomAccessFile(base, "rw").use { it.setLength(report.kernelOffset + expected.size - 1) }
        try {
            verify.invoke(builder, base, report, expected)
            fail("accepted truncated component")
        } catch (error: InvocationTargetException) {
            assertTrue(error.cause is java.io.EOFException)
        }
    }

    @Test fun builderStillRejectsChangedComponentFormat() = temporary { work ->
        val base = fixture(work, 128 * 1024)
        val spec = UltraKernelSpec.parse("#kernel=6.6.89-memory-fixture\n56|41524d64|00000000\n")
        try {
            UltraKernelBuilder(File(work, "out")).generate(base, asset(base), spec)
            fail("accepted changed component format")
        } catch (error: IllegalStateException) {
            assertEquals("ULTRA_KERNEL_OUTPUT_COMPRESSION", error.message)
        }
    }

    private fun child(mode: String, heapMiB: Int) = temporary { work ->
        val paths = linkedSetOf<String>()
        paths.addAll(System.getProperty("java.class.path").orEmpty().split(File.pathSeparator))
        var loader: ClassLoader? = javaClass.classLoader
        while (loader != null) {
            if (loader is URLClassLoader) loader.urLs.filter { it.protocol == "file" }
                .forEach { paths += File(it.toURI()).path }
            loader = loader.parent
        }
        val process = ProcessBuilder(
            File(System.getProperty("java.home"), "bin/java").path,
            "-Xms" + heapMiB + "m", "-Xmx" + heapMiB + "m", "-XX:+UseSerialGC",
            // Retain the complete Builder frame while codecs/hashers can JIT. This also covers
            // runtimes that retain local references longer than an optimizing desktop JIT.
            "-XX:CompileCommand=exclude,dev.maplesu.manager.ultrakernel.UltraKernelBuilder::generate",
            "-cp", paths.joinToString(File.pathSeparator), javaClass.name, mode, work.path,
        ).redirectErrorStream(true).start()
        try {
            assertTrue("memory probe timed out", process.waitFor(60, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.exitValue())
            assertTrue(output, output.contains(if (mode == "builder") "BUILDER_OK" else "READ_OK"))
        } finally { process.destroyForcibly() }
    }

    private fun temporary(block: (File) -> Unit) {
        val work = Files.createTempDirectory("maplesu-kernel-memory-").toFile()
        try { block(work) } finally { work.deleteRecursively() }
    }

    companion object {
        private const val KERNEL_BYTES = 36661760

        @JvmStatic fun main(args: Array<String>) {
            val base = fixture(File(args[1]), KERNEL_BYTES)
            // The measured device heap also holds Manager/UI state. Keep this load reachable
            // through the production operation rather than relying on an allocation counter.
            val pressure = Array(96) { ByteArray(1024 * 1024).also { it[0] = 1 } }
            try {
                when (args[0]) {
                    "builder" -> {
                        val spec = UltraKernelSpec.parse("#kernel=6.6.89-memory-fixture\n8192|00000000|01020304\n")
                        val candidate = UltraKernelBuilder(File(base.parentFile, "out")).generate(base, asset(base), spec)
                        val report = BootImageInspector.inspect(candidate.image)
                        check(candidate.differingBytes == 4 && report.kernelSize == KERNEL_BYTES.toLong())
                        check(candidate.baseBootSha256 == UltraKernelSpec.sha256(base))
                        check(File(candidate.image.parentFile, "candidate-memory.json").isFile)
                        println("BUILDER_OK kernelBytes=" + report.kernelSize + " bootSha=" + candidate.bootSha256)
                    }
                    "read" -> {
                        val raw = UltraKernelPlan.readKernel(base)
                        check(raw.size == KERNEL_BYTES && raw[56] == 'A'.code.toByte())
                        println("READ_OK kernelBytes=" + raw.size)
                    }
                    else -> error("UNKNOWN_MODE")
                }
            } finally { Reference.reachabilityFence(pressure) }
        }

        private fun fixture(work: File, kernelBytes: Int): File {
            work.mkdirs()
            return File(work, "base.img").also { file ->
                val header = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
                header.put("ANDROID!".toByteArray(Charsets.US_ASCII))
                header.putInt(8, kernelBytes).putInt(12, 71).putInt(20, 1584).putInt(40, 4)
                val rawHeader = ByteArray(4096)
                byteArrayOf(65, 82, 77, 100).copyInto(rawHeader, 56)
                "Linux version 6.6.89-memory-fixture\n".toByteArray(Charsets.US_ASCII).copyInto(rawHeader, 512)
                RandomAccessFile(file, "rw").use { output ->
                    output.setLength(0)
                    output.write(header.array())
                    output.write(rawHeader)
                    output.seek(4096L + (kernelBytes.toLong() + 4095) / 4096 * 4096)
                    output.write(ByteArray(71) { 0x72 })
                    output.setLength(if (kernelBytes == KERNEL_BYTES) 100663296 else output.filePointer.let { (it + 4095) / 4096 * 4096 })
                }
            }
        }

        private fun asset(base: File) = UltraKernelVariant(
            id = "memory", label = "memory", summary = "", status = "ready", reason = "",
            debugOnly = false, debugReason = "", default = true, specAsset = "", specSha256 = "", patches = 1,
            kernelRelease = "6.6.89-memory-fixture", baseKernelSha256 = "", expectedKernelSha256 = "",
            knownBaseImageSha256 = "", knownBaseImageBootSha256 = "", imageSizeBytes = base.length(), ramdiskSize = 71,
        )
    }
}

