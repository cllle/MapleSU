package dev.maplesu.manager.ultrakernel

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import org.junit.Assert.*
import org.junit.Test

class UltraKernelManagerClaimCarrierTest {
    private class Symbols(private val values: Map<String, Int>, override val imageSize: Int = 16384) : UltraKernelSymbols {
        private val ordered = values.values.distinct().sorted()
        override fun offsetOf(name: String) = values[name]
        override fun isSymbolStart(value: Int) = value in ordered
        override fun nextOffset(offset: Int) = ordered.firstOrNull { it > offset }
        override fun nameAt(offset: Int) = values.entries.firstOrNull { it.value == offset }?.key
        override fun nameBefore(offset: Int) = ordered.lastOrNull { it < offset }?.let(::nameAt)
        override fun hasSymbolIn(start: Int, end: Int) = ordered.any { it in start until end }
        override val summary = "fixture"
    }

    private fun symbols(import: Int = 256, extra: Map<String, Int> = emptyMap()) = Symbols(mapOf("_stext" to 1024, "_etext" to 15360,
        "kernel_import" to import, "old_cave" to 4096, "after_old" to 6144,
        "__init_begin" to 7000, "__init_end" to 7500, "__init_helper" to 9000,
        "after_init" to 10000, "__start___jump_table" to 16000, "__stop___jump_table" to 16016,
        "__alt_instructions" to 16016, "__alt_instructions_end" to 16028) + extra)

    private fun kernel() = ByteArray(16384).apply {
        putInt(this, 16000, 12000 - 16000)
        putInt(this, 16016, 13000 - 16016)
        putInt(this, 16020, 14000 - 16020)
        this[16026] = 64
        this[16027] = 64
    }

    private fun elf() = ByteArray(1536).apply {
        val b = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(0, 0x464c457f); this[4] = 2; this[5] = 1; this[6] = 1
        b.putShort(16, 3); b.putShort(18, 183); b.putInt(20, 1)
        b.putLong(24, 0); b.putLong(40, 1024); b.putShort(52, 64)
        b.putShort(58, 64); b.putShort(60, 8); b.putShort(62, 7)
        val names = "\u0000.text\u0000.data\u0000.bss\u0000.rela.dyn\u0000.dynsym\u0000.dynstr\u0000.shstrtab\u0000"
        names.toByteArray().copyInto(this, 700)
        fun section(index: Int, name: String, kind: Int, flags: Long, address: Long,
                    file: Long, size: Long, link: Int = 0, stride: Long = 0) {
            val at = 1024 + index * 64
            b.putInt(at, names.indexOf(name)); b.putInt(at + 4, kind); b.putLong(at + 8, flags)
            b.putLong(at + 16, address); b.putLong(at + 24, file); b.putLong(at + 32, size)
            b.putInt(at + 40, link); b.putLong(at + 56, stride)
        }
        section(1, ".text", 1, 6, 0, 512, 8)
        section(2, ".data", 1, 3, 16, 528, 16)
        section(3, ".bss", 8, 3, 32, 0, 16)
        section(4, ".rela.dyn", 4, 2, 48, 552, 48, 5, 24)
        section(5, ".dynsym", 11, 2, 96, 600, 48, 6, 24)
        section(6, ".dynstr", 3, 2, 144, 648, 16)
        section(7, ".shstrtab", 3, 0, 0, 700, names.length.toLong())
        b.putInt(512, 0xd65f03c0.toInt()); b.putInt(516, UltraKernelA64.NOP)
        b.putLong(552, 16); b.putLong(560, 1027); b.putLong(568, 0)
        b.putLong(576, 24); b.putLong(584, (1L shl 32) or 1026L); b.putLong(592, 0)
        b.putInt(624, 1)
        "\u0000kernel_import\u0000".toByteArray().copyInto(this, 648)
    }

    private fun plan(image: ByteArray = kernel(), binary: ByteArray = elf(),
                     symbolTable: UltraKernelSymbols = symbols(), records: List<UltraKernelPlan.PatchRecord> = emptyList()) =
        UltraKernelManagerClaimCarrier.plan(image, symbolTable, records, binary, ByteArray(64), ByteArray(1964))

    private fun applied(image: ByteArray, plan: UltraKernelManagerClaimCarrier.Plan) = image.copyOf().apply {
        plan.records.forEach { it.replacement.copyInto(this, it.offset.toInt()) }
    }

    private fun word(bytes: ByteArray, at: Int) = ByteBuffer.wrap(bytes, at, 4).order(ByteOrder.LITTLE_ENDIAN).int
    private fun putInt(bytes: ByteArray, at: Int, value: Int) { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(at, value) }

    @Test fun runtimeSectionsReconstructAndDynamicMetadataIsConsumedWithoutBeingRetained() {
        val image = kernel()
        val binary = elf()
        val original = image.copyOf()
        val originalElf = binary.copyOf()
        val result = plan(image, binary)
        assertArrayEquals(original, image)
        assertArrayEquals(originalElf, binary)
        val patched = applied(image, result)
        val descriptor = result.descriptorOffset
        assertEquals(3, word(patched, descriptor + 4))
        assertEquals(image.size, word(patched, descriptor + 44))
        assertEquals(3, word(patched, descriptor + 24))
        assertEquals(1, word(patched, descriptor + 32))
        assertEquals(2, word(patched, descriptor + 36))
        assertEquals(result.chunkTableOffset, word(patched, descriptor + 48))
        assertEquals(result.anchorOffset + 56, result.bindingOffset)
        assertTrue(descriptor + result.descriptorBytes <= result.anchorOffset ||
            result.anchorOffset + 96 <= descriptor)
        assertEquals(48, result.payloadMemoryBytes)
        assertEquals(52 + 3 * 16 + 4 + 2 * 16, result.descriptorBytes)
        assertEquals(256, word(patched, descriptor + 52 + 3 * 16))
        assertEquals(16, word(patched, descriptor + 52 + 3 * 16 + 4))
        assertEquals(24, word(patched, descriptor + 52 + 3 * 16 + 4 + 16))
        val crc = CRC32().apply {
            update(patched, descriptor, result.descriptorBytes)
            update(patched, result.chunkTableOffset, result.chunkCount * 8)
        }.value
        assertEquals(crc, result.descriptorCrc)
        assertEquals(crc.toInt(), word(patched, result.bindingOffset + 12))
        val reconstructed = ByteArray(result.payloadMemoryBytes)
        var section = 0
        var cursor = 0
        for (index in 0 until result.chunkCount) {
            while (cursor == word(patched, descriptor + 52 + section * 16 + 4)) {
                section++; cursor = 0
            }
            val at = word(patched, result.chunkTableOffset + index * 8)
            val length = word(patched, result.chunkTableOffset + index * 8 + 4)
            val target = word(patched, descriptor + 52 + section * 16) + cursor
            patched.copyInto(reconstructed, target, at, at + length)
            cursor += length
        }
        assertArrayEquals(binary.copyOfRange(512, 520), reconstructed.copyOfRange(0, 8))
        assertArrayEquals(ByteArray(40), reconstructed.copyOfRange(8, 48))
    }

    @Test fun originalCavesRuntimePatchesInitTextAndExistingRecordsArePreserved() {
        val image = kernel()
        val record = UltraKernelPlan.PatchRecord("existing", 8080,
            image.copyOfRange(8080, 8096), ByteArray(16), "old_cave", 8000, 80, 4096)
        val result = plan(image, records = listOf(record))
        val blocked = listOf(4096 to 6144, 7000 to 7500, 8000 to 8096, 9000 to 10000,
            12000 to 12004, 13000 to 13064, 14000 to 14064)
        for (write in result.records) {
            assertTrue(write.offset >= 1024 && write.offset + write.length <= 15360)
            assertFalse(symbols().hasSymbolIn(write.offset.toInt(), (write.offset + write.length).toInt()))
            for ((start, end) in blocked) assertFalse(write.role,
                write.offset < end && start < write.offset + write.length)
        }
    }

    @Test fun relativeAndEachSupportedImportRelocationUseTheBoundKernelSymbols() {
        for (kind in listOf(257, 1025, 1026)) {
            val binary = elf()
            ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN).putLong(584, (1L shl 32) or kind.toLong())
            val result = plan(binary = binary)
            val bytes = applied(kernel(), result)
            val imports = result.descriptorOffset + 52 + 3 * 16
            assertEquals(256, word(bytes, imports))
            assertEquals(1, word(bytes, imports + 4 + 4))
            assertEquals(2, word(bytes, imports + 4 + 16 + 4))
        }
    }

    private fun dataImage() = kernel().apply {
        val b = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        b.putLong(16, 20480); b.putInt(24, 2); b.putInt(56, 0x644d5241)
        val names = "\u0000word\u0000kernel_import\u0000".toByteArray()
        val body = intArrayOf(1, 1 shl 24, 8, 64, 0, 3 shl 24, 0, 1, 1, 2,
            6, 14 shl 24, 2, 1)
        intArrayOf(0x0001eb9f, 24, 0, body.size * 4, body.size * 4, names.size)
            .forEachIndexed { i, value -> b.putInt(64 + i * 4, value) }
        body.forEachIndexed { i, value -> b.putInt(88 + i * 4, value) }
        names.copyInto(this, 88 + body.size * 4)
    }

    private fun memorySymbols(offsets: List<Int> = listOf(16416),
        boundaries: Map<String, Int> = emptyMap()): UltraKernelSymbols {
        val base = symbols()
        val memory = mapOf("_text" to 0, "_end" to 20480,
            "__bss_start" to 16384, "__bss_stop" to 16432) + boundaries
        return object : UltraKernelSymbols by base {
            override fun offsetOf(name: String) = when (name) {
                "kernel_import" -> offsets.singleOrNull()?.takeIf { it in 0 until imageSize }
                "__start_BTF" -> 64
                else -> base.offsetOf(name)
            }
            override fun offsetsOf(name: String) = listOfNotNull(offsetOf(name))
            override fun memoryOffsetsOf(name: String) = when (name) {
                "kernel_import" -> offsets
                else -> listOfNotNull(memory[name] ?: offsetOf(name))
            }
        }
    }

    private fun dataPlan(image: ByteArray = dataImage(), binary: ByteArray = elf(),
        table: UltraKernelSymbols = memorySymbols(), signature: String = "a2:i8:64") =
        UltraKernelManagerClaimCarrier.plan(image, table, emptyList(), binary, ByteArray(64), ByteArray(1964),
            dataSignatures = mapOf("kernel_import" to signature))

    @Test fun targetBtfObjectUsesTheCompleteBssRangeAndTheSeparateMemoryExtent() {
        for (type in listOf(0, 1)) {
            val image = dataImage()
            val before = image.copyOf()
            val binary = elf().apply { this[628] = type.toByte() }
            val result = dataPlan(image, binary)
            val bytes = applied(image, result)
            assertEquals(20480, word(bytes, result.descriptorOffset + 44))
            assertEquals(16416, word(bytes, result.descriptorOffset + 52 + 3 * 16))
            assertEquals(image.size, word(bytes, result.bindingOffset))
            assertArrayEquals(before, image)
        }
    }

    @Test fun omittedVariableRecordsUseTheExactActualObjectTypeNotANameOrEstimatedSize() {
        val image = dataImage().apply {
            // Remove only the VAR record; preserve the actual 2 x u64 array type.
            putInt(this, 64 + 12, 40)
            putInt(this, 64 + 16, 56)
        }
        val result = dataPlan(image)
        val bytes = applied(image, result)
        assertEquals(16416, word(bytes, result.descriptorOffset + 52 + 3 * 16))
        assertThrows(UltraKernelGenerateException::class.java) { dataPlan(image, signature = "a1:i8:64") }
        val oversized = image.copyOf().apply { putInt(this, 88 + 9 * 4, 3) }
        assertThrows(UltraKernelGenerateException::class.java) { dataPlan(oversized) }
    }

    @Test fun aConflictingVariableRecordIsNotHiddenByAMatchingTypeElsewhere() {
        val image = dataImage().apply { putInt(this, 88 + 12 * 4, 1) }
        assertThrows(UltraKernelGenerateException::class.java) { dataPlan(image) }
    }

    @Test fun aBssAddressAloneDoesNotMakeAFunctionOrUnknownElfTypeADataImport() {
        assertThrows(IllegalStateException::class.java) { plan(dataImage(), elf(), memorySymbols()) }
        for (type in listOf(2, 3, 4, 10, 15)) assertThrows(IllegalArgumentException::class.java) {
            dataPlan(binary = elf().apply { this[628] = type.toByte() })
        }
        assertThrows(IllegalArgumentException::class.java) {
            plan(binary = elf().apply { this[628] = 1 })
        }
    }

    @Test fun bssTailCrossingNonBssAndAmbiguousDataAreRejectedBeforeWrites() {
        for (offsets in listOf(listOf(16420), listOf(16380), listOf(18000),
            listOf(20480), listOf(16416, 16400), listOf(-4))) {
            val image = dataImage()
            val before = image.copyOf()
            assertThrows(RuntimeException::class.java) { dataPlan(image, table = memorySymbols(offsets)) }
            assertArrayEquals(before, image)
        }
    }

    @Test fun onlyTargetHeaderAndUniqueLinkerBoundariesEstablishTheMemoryExtent() {
        for ((at, value) in listOf(16 to 16384, 20 to 1, 24 to 3, 56 to 0)) {
            val image = dataImage().apply { putInt(this, at, value) }
            assertThrows(IllegalArgumentException::class.java) { dataPlan(image) }
        }
        for (facts in listOf(mapOf("_text" to 4), mapOf("_end" to 20484),
            mapOf("__bss_stop" to 20484), mapOf("__bss_start" to 16432))) {
            assertThrows(IllegalArgumentException::class.java) { dataPlan(table = memorySymbols(boundaries = facts)) }
        }
        assertThrows(UltraKernelGenerateException::class.java) { dataPlan(signature = "a1:i8:64") }
    }

    @Test fun invalidHeadersSectionsSymbolsRelocationsAndMissingImportsStopBeforeWrites() {
        val mutations = listOf<Pair<Int, Long>>(
            0 to 0, 4 to 0, 16 to 0, 18 to 0, 40 to Long.MAX_VALUE,
            1024 + 64 + 8 to 7, 1024 + 64 + 16 to Long.MAX_VALUE,
            1024 + 64 + 32 to Long.MAX_VALUE,
            1024 + 2 * 64 + 16 to 0, 1024 + 4 * 64 + 56 to 16,
            552 to 17, 560 to 999, 568 to -1, 584 to ((99L shl 32) or 1026L), 592 to 1)
        for ((at, value) in mutations) {
            val image = kernel()
            val before = image.copyOf()
            val binary = elf()
            ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN).putLong(at, value)
            assertThrows("mutation=$at", IllegalArgumentException::class.java) { plan(image, binary) }
            assertArrayEquals(before, image)
        }
        assertThrows(IllegalStateException::class.java) {
            plan(symbolTable = Symbols(mapOf("_stext" to 1024, "_etext" to 15360)))
        }
        assertThrows(IllegalArgumentException::class.java) { plan(symbolTable = symbols(257)) }
    }

    @Test fun missingContiguousSpaceAndMalformedCodeNeverModifyTheKernel() {
        val image = kernel()
        val before = image.copyOf()
        val tooSmall = Symbols(mapOf("kernel_import" to 256, "_stext" to 1024, "_etext" to 1400))
        assertThrows(UltraKernelGenerateException::class.java) { plan(image, symbolTable = tooSmall) }
        assertThrows(IllegalArgumentException::class.java) {
            UltraKernelManagerClaimCarrier.plan(image, symbols(), emptyList(), elf(), ByteArray(3), ByteArray(64))
        }
        assertArrayEquals(before, image)
    }

    @Test fun tableCorruptionChangesTheBoundChecksumAndNoWriteOverlapsAnother() {
        val image = kernel()
        val result = plan(image)
        val patched = applied(image, result)
        patched[result.chunkTableOffset] = (patched[result.chunkTableOffset].toInt() xor 1).toByte()
        assertNotEquals(result.descriptorCrc, CRC32().apply {
            update(patched, result.descriptorOffset, result.descriptorBytes)
            update(patched, result.chunkTableOffset, result.chunkCount * 8)
        }.value)
        val writes = result.records.sortedBy { it.offset }
        writes.zipWithNext().forEach { (left, right) -> assertTrue(left.offset + left.length <= right.offset) }
    }

    @Test fun entryMustComeFromExecutableFileBytesAndElfVersionMustMatch() {
        val image = kernel()
        val original = image.copyOf()
        for (kind in listOf(8, 0)) {
            val binary = elf()
            if (kind == 8) putInt(binary, 1024 + 64 + 4, kind)
            else binary[6] = 0
            assertThrows(IllegalArgumentException::class.java) { plan(image, binary) }
            assertArrayEquals(original, image)
        }
        val binary = elf()
        ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN).putShort(52, 32)
        assertThrows(IllegalArgumentException::class.java) { plan(image, binary) }
        assertArrayEquals(original, image)
    }

    @Test fun fixedAnchorEmbedsBindingAndPreservesOtherPolicyBytes() {
        val policy = ByteArray(96) { it.toByte() }
        val before = policy.copyOf()
        val result = UltraKernelManagerClaimCarrier.plan(kernel(), symbols(), emptyList(), elf(),
            ByteArray(128), ByteArray(1964), policy)
        val anchor = result.records.single { it.role == "manager_claim:anchor" }
        assertEquals(96, anchor.length)
        assertFalse(result.records.any { it.role == "manager_claim:binding" })
        assertArrayEquals(before, policy)
        assertArrayEquals(before.copyOfRange(0, 56), anchor.replacement.copyOfRange(0, 56))
        assertArrayEquals(before.copyOfRange(80, 96), anchor.replacement.copyOfRange(80, 96))
        assertEquals(kernel().size, word(anchor.replacement, 56))
        assertEquals(result.descriptorOffset, word(anchor.replacement, 60))
        for (size in listOf(0, 72, 100)) assertThrows(IllegalArgumentException::class.java) {
            UltraKernelManagerClaimCarrier.plan(kernel(), symbols(), emptyList(), elf(),
                ByteArray(128), ByteArray(1964), ByteArray(size))
        }
    }

    @Test fun connectedRecoveryBindsEveryFunctionAndPreservesTheSavedExecFrame() {
        val image = kernel().apply { putInt(this, 24, 0xa) }
        val original = image.copyOf()
        val frame = ByteArray(52).apply {
            putInt(this, 48, UltraKernelA64.encodeB(848, 900))
        }
        val records = listOf(
            UltraKernelPlan.PatchRecord("arm64_notify_die+0x0", 800, ByteArray(52), frame),
            UltraKernelPlan.PatchRecord("die+0x0", 900, ByteArray(64), ByteArray(64)),
            UltraKernelPlan.PatchRecord("panic+0x0", 128, ByteArray(48), "Aa1".repeat(16).toByteArray()),
        )
        val imports = listOf("module_alloc", "module_memfree", "set_memory_nx", "set_memory_rw",
            "set_memory_ro", "set_memory_x", "caches_clean_inval_pou")
            .mapIndexed { index, name -> name to 300 + index * 4 }.toMap()
        val payload = UltraKernelManagerClaimPayload(ByteArray(128), ByteArray(1964), elf())
        val result = UltraKernelManagerClaimCarrier.connect(image, symbols(extra = imports), records, payload)
        assertArrayEquals(original, image)
        val newFrame = result.single { it.role == records[0].role }
        assertArrayEquals(frame.copyOfRange(0, 48), newFrame.replacement.copyOfRange(0, 48))
        assertArrayEquals(frame, records[0].replacement)
        val anchor = result.single { it.role == "manager_claim:anchor" }
        assertEquals(0x4143534d, word(anchor.replacement, 0))
        assertEquals(anchor.offset.toInt(), word(anchor.replacement, 8))
        assertEquals(12, word(anchor.replacement, 12))
        assertEquals(128, word(anchor.replacement, 20))
        assertEquals(300, word(anchor.replacement, 24))
        assertEquals(304, word(anchor.replacement, 28))
        assertEquals(324, word(anchor.replacement, 48))
        assertEquals(0, word(anchor.replacement, 52))
        val bootstrap = result.single { it.role == "manager_claim:bootstrap" }
        assertEquals(payload.bootstrap.size, bootstrap.length)
        assertArrayEquals(payload.bootstrap, bootstrap.replacement)
        val continuation = result.single { it.role == "manager_claim:continuation" }
        assertEquals(56, continuation.length)
        assertEquals(UltraKernelA64.encodeB(848, continuation.offset), word(newFrame.replacement, 48))
        assertEquals(UltraKernelA64.encodeBl(continuation.offset + 28, bootstrap.offset),
            word(continuation.replacement, 28))
        assertEquals(UltraKernelA64.encodeB(continuation.offset + 52, 900), word(continuation.replacement, 52))
        for ((offset, pair) in listOf(0 to (29 to 30), 2 to (6 to 7), 4 to (4 to 5),
            6 to (2 to 3), 8 to (0 to 1)).withIndex()) {
            assertEquals(UltraKernelA64.pair("ldp", pair.second.first, pair.second.second,
                31, pair.first, true, 2), word(continuation.replacement, 32 + offset * 4))
        }
        assertThrows(IllegalStateException::class.java) {
            UltraKernelManagerClaimCarrier.connect(image, symbols(), records, payload)
        }
        assertArrayEquals(original, image)
    }

    @Test fun assetSourceSelectsTheExactFamilyAndMissingPayloadDoesNotDisableRecoverySilently() {
        val reads = ArrayList<String>()
        val source = object : UltraKernelAssetSource {
            override fun list(directory: String) = emptyList<String>()
            override fun read(path: String): ByteArray {
                reads += path
                return byteArrayOf(1, 2, 3, 4)
            }
        }
        for ((series, family) in listOf("5.10" to "android12-5.10", "5.15" to "android13-5.15",
            "6.1" to "android14-6.1", "6.6" to "android15-6.6", "6.12" to "android16-6.12",
            "6.18" to "android17-6.18")) {
            reads.clear()
            val payload = UltraKernelManagerClaimPayload.load(source, "$series.123-device")
            assertEquals(listOf("ultra-manager-claim/$family/claim.so",
                "ultra-manager-claim/bootstrap.bin", "ultra-manager-claim/materializer.bin"), reads)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), payload.elf)
        }
        assertThrows(java.io.FileNotFoundException::class.java) {
            UltraKernelManagerClaimPayload.load(UltraKernelMemoryAssets(emptyMap()), "6.6.89-device")
        }
    }

    @Test fun cacheAdapterSelectsExactTargetAbiAndConvertsEndToLength() {
        val owner = UltraKernelManagerClaimCarrier
        val choose = owner.javaClass.getDeclaredMethod("cacheUsesAddressLength", String::class.java).apply {
            isAccessible = true
        }
        assertEquals(true, choose.invoke(owner, "fv(*v;i8:64;)"))
        assertEquals(false, choose.invoke(owner, "fv(i8:64;i8:64;)"))
        try {
            choose.invoke(owner, "fv(i4:32;i4:32;)")
            fail("Unknown cache ABI was accepted")
        } catch (error: java.lang.reflect.InvocationTargetException) {
            assertEquals("ULTRA_KERNEL_RESIDENT_CACHE_ABI", error.cause?.message)
        }
        val adapter = owner.javaClass.getDeclaredMethod("cacheAdapter", Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType).apply { isAccessible = true }.invoke(owner, 4096, 512) as ByteArray
        assertEquals(12, adapter.size)
        assertEquals(0xd503245f.toInt(), UltraKernelA64.le32Bits(adapter, 0))
        assertEquals(0xcb000021.toInt(), UltraKernelA64.le32Bits(adapter, 4)) // sub x1, x1, x0
        assertEquals(0x17fffc7e, UltraKernelA64.le32Bits(adapter, 8)) // b 512 from 4104
    }
}
