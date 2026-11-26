package dev.maplesu.manager

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModuleArchiveInspectorTest {
    @get:Rule val tmp = TemporaryFolder()
    private val prop = "id=maple_demo\nname=枫栖示例\nversion=1.2\nversionCode=12\nauthor=Maple\ndescription=demo\n"

    /** 与 MapleSuModuleArchiveTest 的 ELF 夹具同构：一个可执行 PT_LOAD、一个 SHT_DYNSYM、一个已定义导出。 */
    private val mapleSuEntry = dev.maplesu.manager.compat.NativeModuleAbi.ENTRY
    private val mapleSuTags = listOf(
        Triple("de1f663bcae792d7c9ed1a21cdaeff00331fe11b074f0303e018f43998f8db3e", "枫栖测试", "04429c3f7172e4cce7ac7a5d44b75631ce91777b5f2a5eec076a3a508a76fb17"),
        Triple("7dc74a2b86805f2cd0f45c6a82d384fd568e341c6f05af17c7229c9ba6d4318b", "1.2", "8dbc019a6e51fe8d4a3db9feaf05535d70879f35626050cd4131bfe323ecefc2"),
        Triple("05c62cd446c811e772c94d97fd546738328f7c6b9967ed135190ef37ff06d866", "静态检查", "ed02c95e091c66100d6f2f4554f96392b2951b394ba042278eed8f236f43e1a3"),
        Triple("1f39b220291a2fb6e4fec95f179d0faf833ca534ef8ab2ffc4bd893768694451", "Maple", "d409c886fd6cedd694c1b126fd19eccd90e2463e04154c193effc7103ce63f1c"),
        Triple("b33dcddd88c07d079a8e0e9ec6ab976f15f2edba46cab91a61234cfbb982111c", "0123456789abcdef0123456789abcdef", "3020d9a6c07a30e2649dd29a72d7e57121f0c0ca457b6d21dca5a69acbe6c2c4"),
    )

    private fun mapleSuElf(id: String = "0123456789abcdef0123456789abcdef"): ByteArray {
        val metadata = mapleSuTags.mapIndexed { index, field ->
            if (index == 4) Triple(field.first, id, field.third) else field
        }.joinToString("\u0000") { it.first + it.second + it.third }.toByteArray()
        val strings = ("\u0000" + mapleSuEntry + "\u0000").toByteArray()
        val strOffset = 512 + metadata.size
        val symOffset = (strOffset + strings.size + 7) and -8
        val shOffset = symOffset + 48
        val b = ByteBuffer.allocate(shOffset + 192).order(ByteOrder.LITTLE_ENDIAN)
        b.put(byteArrayOf(0x7f, 69, 76, 70, 2, 1, 1))
        b.putShort(16, 3); b.putShort(18, 183); b.putInt(20, 1)
        b.putLong(32, 64); b.putLong(40, shOffset.toLong())
        b.putShort(52, 64); b.putShort(54, 56); b.putShort(56, 1)
        b.putShort(58, 64); b.putShort(60, 3)
        b.putInt(64, 1); b.putInt(68, 5); b.putLong(80, 0x400000)
        b.putLong(96, b.capacity().toLong()); b.putLong(104, b.capacity().toLong()); b.putLong(112, 4096)
        b.putInt(256, 0xd65f03c0.toInt()) // RET，不会被加载执行。
        b.position(512); b.put(metadata); b.position(strOffset); b.put(strings)
        b.putInt(symOffset + 24, 1); b.putInt(symOffset + 28, 0x12)
        b.putShort(symOffset + 30, 1)
        b.putLong(symOffset + 32, 0x400100); b.putLong(symOffset + 40, 4)
        b.putInt(shOffset + 68, 3); b.putLong(shOffset + 88, strOffset.toLong())
        b.putLong(shOffset + 96, strings.size.toLong())
        b.putInt(shOffset + 132, 11); b.putLong(shOffset + 152, symOffset.toLong())
        b.putLong(shOffset + 160, 48); b.putInt(shOffset + 168, 1); b.putLong(shOffset + 184, 24)
        return b.array()
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun zip(vararg entries: Pair<String, ByteArray>, mode: Int = 0): File {
        val file = tmp.newFile()
        ZipArchiveOutputStream(file).use { archive ->
            for ((name, bytes) in entries) {
                val entry = ZipArchiveEntry(name)
                entry.method = ZipEntry.STORED
                entry.size = bytes.size.toLong()
                entry.crc = CRC32().apply { update(bytes) }.value
                if (mode != 0) entry.unixMode = mode
                archive.putArchiveEntry(entry)
                archive.write(bytes)
                archive.closeArchiveEntry()
            }
        }
        return file
    }

    private fun archive(vararg entries: Pair<String, ByteArray>) = zip("module.prop" to prop.toByteArray(), *entries)

    private fun xzArchive(wrongCrc: Boolean = false, truncated: Boolean = false): File {
        val file = tmp.newFile()
        ZipArchiveOutputStream(file).use { archive ->
            for ((name, bytes) in listOf("module.prop" to prop.toByteArray(), "customize.sh" to "SKIPUNZIP=1\n".toByteArray())) {
                val compressed = ByteArrayOutputStream().also { output ->
                    XZOutputStream(output, LZMA2Options()).use { it.write(bytes) }
                }.toByteArray().let { if (truncated) it.dropLast(12).toByteArray() else it }
                val entry = ZipArchiveEntry(name).apply {
                    method = 95
                    size = bytes.size.toLong()
                    compressedSize = compressed.size.toLong()
                    crc = CRC32().apply { update(bytes) }.value xor if (wrongCrc) 1L else 0L
                }
                archive.addRawArchiveEntry(entry, compressed.inputStream())
            }
        }
        return file
    }
    private fun reject(file: File, code: String) {
        val failure = assertThrows(ModuleArchiveInspector.Failure::class.java) { ModuleArchiveInspector.inspect(file) }
        assertEquals(code, failure.code)
    }

    @Test fun readsUtf8MetadataWithoutExtractingOrChangingSource() {
        val file = archive("service.sh" to "touch should-not-exist".toByteArray())
        val before = file.readBytes()
        val siblings = tmp.root.list()!!.sorted()
        val report = ModuleArchiveInspector.inspect(file)
        assertEquals("maple_demo", report.id)
        assertEquals("枫栖示例", report.properties["name"])
        assertEquals(12, report.versionCode)
        assertEquals(setOf(ModuleArchiveInspector.Feature.SCRIPT), report.features)
        assertEquals(2, report.entryCount)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(before).joinToString("") { "%02x".format(it) }, report.sha256)
        assertArrayEquals(before, file.readBytes())
        assertEquals(siblings, tmp.root.list()!!.sorted())
        assertThrows(UnsupportedOperationException::class.java) { report.properties["id"] = "changed" }
    }

    @Test fun followsUpstreamIdAndOptionalPropertyRules() {
        for (id in listOf("aa", "A.module-101", "a_1")) {
            val report = ModuleArchiveInspector.inspect(zip("module.prop" to "id=$id\n".toByteArray()))
            assertEquals(id, report.id)
            assertEquals(0, report.versionCode)
        }
        for (id in listOf("a", "1_module", "a module", "../module", ".", "..", "")) {
            reject(zip("module.prop" to "id=$id\n".toByteArray()), "INVALID_MODULE_ID")
        }
    }

    @Test fun rejectsDuplicateAndMalformedMetadata() {
        for (metadata in listOf("id=maple_demo\nid=other_demo\n", "id=maple_demo\nversionCode=1\nversionCode=2\n")) {
            reject(zip("module.prop" to metadata.toByteArray()), "INVALID_MODULE_METADATA")
        }
        reject(zip("module.prop" to "id=maple_demo\nname=\\uXXXX\n".toByteArray()), "INVALID_MODULE_METADATA")
        reject(zip("module.prop" to byteArrayOf(0xc3.toByte(), 0x28)), "INVALID_MODULE_METADATA")
        reject(zip("module.prop" to "id=maple_demo\nversionCode=2147483648\n".toByteArray()), "INVALID_MODULE_METADATA")
    }

    @Test fun requiresOneRootMetadataFile() {
        reject(zip("folder/module.prop" to prop.toByteArray()), "MISSING_MODULE_METADATA")
        reject(zip("module.prop/" to byteArrayOf()), "MISSING_MODULE_METADATA")
        reject(zip("module.prop" to prop.toByteArray(), "module.prop" to prop.toByteArray()), "DUPLICATE_ARCHIVE_PATH")
    }

    @Test fun rejectsUnsafeOrAmbiguousEntryNames() {
        for (name in listOf("../outside", "/absolute", "a/../../outside", "a/./entry", "a//entry", "C:/entry")) {
            reject(archive(name to byteArrayOf(1)), "INVALID_ARCHIVE_PATH")
        }
        // Commons' writer normalizes backslashes, so edit the actual local and central name bytes.
        val file = archive("a/entry" to byteArrayOf(1))
        val bytes = file.readBytes()
        val name = "a/entry".toByteArray()
        for (offset in 0..bytes.size - name.size) {
            if (bytes.copyOfRange(offset, offset + name.size).contentEquals(name)) bytes[offset + 1] = '\\'.code.toByte()
        }
        file.writeBytes(bytes)
        reject(file, "INVALID_ARCHIVE_PATH")
    }

    @Test fun rejectsFileDirectoryConflictsInEitherOrder() {
        reject(archive("webroot" to byteArrayOf(1), "webroot/index.html" to byteArrayOf(2)), "ARCHIVE_PATH_CONFLICT")
        reject(archive("webroot/index.html" to byteArrayOf(2), "webroot" to byteArrayOf(1)), "ARCHIVE_PATH_CONFLICT")
        reject(archive("webroot/" to byteArrayOf(), "webroot" to byteArrayOf(1)), "DUPLICATE_ARCHIVE_PATH")
    }

    @Test fun rejectsLinksAndSpecialFilesWithoutExtractingThem() {
        reject(zip("module.prop" to prop.toByteArray(), mode = 0xa1ff), "UNSUPPORTED_ARCHIVE_ENTRY")
        reject(zip("module.prop" to prop.toByteArray(), mode = 0x21a4), "UNSUPPORTED_ARCHIVE_ENTRY")
    }

    @Test fun reportsIndependentModuleFeaturesAndNativeHeaders() {
        val elf = ByteArray(20)
        elf[0] = 0x7f; elf[1] = 69; elf[2] = 76; elf[3] = 70
        elf[4] = 2; elf[5] = 1; elf[6] = 1
        elf[18] = 183.toByte()
        val report = ModuleArchiveInspector.inspect(archive(
            "customize.sh" to byteArrayOf(), "action.sh" to byteArrayOf(),
            "webroot/index.html" to "<html/>".toByteArray(), "system/bin/demo" to elf,
            "skip_mount" to byteArrayOf(), "sepolicy.rule" to byteArrayOf(),
            "system.prop" to byteArrayOf(), "initrc/demo.rc" to byteArrayOf(),
            "preinit/demo" to byteArrayOf(), "lib/unknown.so" to byteArrayOf(1)))
        assertEquals(ModuleArchiveInspector.Feature.values().toSet() - ModuleArchiveInspector.Feature.METAMODULE, report.features)
        assertTrue(report.skipMount)
        assertEquals("arm64-v8a", report.nativeAbis["system/bin/demo"])
        assertEquals("unknown", report.nativeAbis["lib/unknown.so"])
    }

    @Test fun skipMountDoesNotEraseOverlayPayloadInventory() {
        val report = ModuleArchiveInspector.inspect(archive("skip_mount" to byteArrayOf(), "system/lib/demo" to byteArrayOf(1)))
        assertTrue(report.skipMount)
        assertTrue(report.features.contains(ModuleArchiveInspector.Feature.SYSTEM_OVERLAY))
    }

    @Test fun metamoduleMarkersUseUpstreamSemantics() {
        for (value in listOf("1", "TrUe", " true ")) {
            val report = ModuleArchiveInspector.inspect(zip("module.prop" to (prop + "metamodule=$value\n").toByteArray()))
            assertTrue(report.features.contains(ModuleArchiveInspector.Feature.METAMODULE))
        }
        val report = ModuleArchiveInspector.inspect(zip("module.prop" to (prop + "metamodule=0\n").toByteArray()))
        assertFalse(report.features.contains(ModuleArchiveInspector.Feature.METAMODULE))
    }

    @Test fun rejectsCorruptedStoredDataCrc() {
        val file = archive("payload.bin" to byteArrayOf(17, 18, 19, 20))
        val bytes = file.readBytes()
        val needle = byteArrayOf(17, 18, 19, 20)
        val offset = bytes.indices.first { it + needle.size <= bytes.size && bytes.copyOfRange(it, it + needle.size).contentEquals(needle) }
        bytes[offset] = 99
        file.writeBytes(bytes)
        reject(file, "ARCHIVE_CONTENT_MISMATCH")
    }

    @Test fun rejectsLocalAndCentralNameDisagreement() {
        val file = archive()
        val bytes = file.readBytes()
        // First local filename begins at byte 30; change only that header's first byte.
        bytes[30] = 'x'.code.toByte()
        file.writeBytes(bytes)
        reject(file, "ARCHIVE_HEADER_MISMATCH")
    }

    @Test fun rejectsLocalAndCentralCrcDisagreement() {
        val file = archive()
        val bytes = file.readBytes()
        // STORED entry has no data descriptor; both headers must agree on CRC and sizes.
        bytes[14] = (bytes[14].toInt() xor 1).toByte()
        file.writeBytes(bytes)
        reject(file, "ARCHIVE_HEADER_MISMATCH")
    }

    @Test fun readsDeflatedEntriesWithDataDescriptors() {
        val file = tmp.newFile()
        ZipOutputStream(file.outputStream()).use { archive ->
            for ((name, value) in listOf("module.prop" to prop, "webroot/index.html" to "hello")) {
                archive.putNextEntry(ZipEntry(name))
                archive.write(value.toByteArray())
                archive.closeEntry()
            }
        }
        assertEquals(setOf(ModuleArchiveInspector.Feature.WEB_UI), ModuleArchiveInspector.inspect(file).features)
    }

    @Test fun readsXzCompressedModuleUpdatesWithoutChangingTheArchive() {
        val file = xzArchive()
        val before = file.readBytes()
        val report = ModuleArchiveInspector.inspect(file)
        assertEquals("maple_demo", report.id)
        assertEquals(12, report.versionCode)
        assertEquals(setOf(ModuleArchiveInspector.Feature.SCRIPT), report.features)
        ModuleArchiveInspector.verifyUnchanged(file, report)
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun rejectsXzContentWithIncorrectCrc() {
        reject(xzArchive(wrongCrc = true), "ARCHIVE_CONTENT_MISMATCH")
    }

    @Test fun rejectsTruncatedXzStreams() {
        reject(xzArchive(truncated = true), "INVALID_MODULE_ARCHIVE")
    }

    @Test fun readsBoundedZip64EntriesUsingLibraryMetadata() {
        val file = tmp.newFile()
        ZipArchiveOutputStream(file).use { archive ->
            archive.setUseZip64(Zip64Mode.Always)
            val entry = ZipArchiveEntry("module.prop")
            entry.method = ZipEntry.STORED
            entry.size = prop.toByteArray().size.toLong()
            entry.crc = CRC32().apply { update(prop.toByteArray()) }.value
            archive.putArchiveEntry(entry)
            archive.write(prop.toByteArray())
            archive.closeArchiveEntry()
        }
        assertEquals("maple_demo", ModuleArchiveInspector.inspect(file).id)
    }

    @Test fun recognizesAbiHeaderClassAndMachineSeparatelyFromLoaderSupport() {
        for ((machine, elfClass, abi) in listOf(Triple(183, 2, "arm64-v8a"), Triple(40, 1, "armeabi-v7a"),
                Triple(62, 2, "x86_64"), Triple(3, 1, "x86"), Triple(183, 1, "unknown"))) {
            val header = ByteArray(20)
            header[0] = 0x7f; header[1] = 69; header[2] = 76; header[3] = 70
            header[4] = elfClass.toByte(); header[5] = 1; header[6] = 1; header[18] = machine.toByte()
            val report = ModuleArchiveInspector.inspect(archive("lib/demo.so" to header))
            assertEquals(abi, report.nativeAbis["lib/demo.so"])
        }
    }

    @Test fun cancellationLeavesInputUntouched() {
        val file = archive()
        val before = file.readBytes()
        Thread.currentThread().interrupt()
        try {
            reject(file, "CANCELLED")
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun enforcesMetadataAndEntrySizeLimits() {
        reject(zip("module.prop" to ByteArray(64 * 1024 + 1) { 65 }), "ARCHIVE_LIMIT_EXCEEDED")
        val file = archive()
        val bytes = file.readBytes()
        val central = bytes.indices.first { it + 4 <= bytes.size && bytes.copyOfRange(it, it + 4).contentEquals(byteArrayOf(0x50, 0x4b, 0x01, 0x02)) }
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(central + 24, 129 * 1024 * 1024)
        file.writeBytes(bytes)
        reject(file, "ARCHIVE_LIMIT_EXCEEDED")
    }

    @Test fun rejectsTruncatedAndNonZipInput() {
        val file = tmp.newFile().apply { writeText("not a module") }
        reject(file, "INVALID_MODULE_ARCHIVE")
        val truncated = archive().apply { writeBytes(readBytes().dropLast(12).toByteArray()) }
        reject(truncated, "INVALID_MODULE_ARCHIVE")
    }

    @Test fun reportsNativePayloadSha256ForMapleSuArchives() {
        val elf = mapleSuElf()
        val report = ModuleArchiveInspector.inspect(zip("libdemo.so" to elf))
        assertEquals(ModuleArchiveInspector.ModuleType.MAPLESU, report.type)
        assertEquals("libdemo.so", report.nativeEntry)
        assertEquals(64, report.nativePayloadSha256!!.length)
        // 载荷哈希是成员字节自己的 SHA-256，不是整包哈希。
        assertEquals(sha256(elf), report.nativePayloadSha256)
        assertNotEquals(report.sha256, report.nativePayloadSha256)
    }

    @Test fun nativeDescriptorIdsUseTheSdkComponentAndUtf8Contract() {
        for (id in listOf("a", "p0_probe", "z".repeat(32), "a".repeat(33), "a".repeat(1023),
            "中".repeat(341), "😀".repeat(255), "module with space", "module:colon", "module\\backslash", " module ")) {
            val elf = mapleSuElf(id)
            assertEquals(id, MapleSuModuleMetadata.inspect(elf).getProperty("id"))
            assertEquals(id, ModuleArchiveInspector.inspect(zip("libdemo.so" to elf)).id)
        }
        for (id in listOf("", ".", "..", "a/b", "a\u0000b", "a".repeat(1024), "中".repeat(342), "😀".repeat(256))) {
            reject(zip("libdemo.so" to mapleSuElf(id)), "INVALID_MAPLESU_MODULE")
        }
    }

    @Test fun nativeDescriptorStillRejectsMalformedUtf8() {
        val elf = mapleSuElf("malformed_marker")
        val marker = "malformed_marker".toByteArray()
        val start = elf.indices.first { offset -> offset + marker.size <= elf.size &&
            elf.copyOfRange(offset, offset + marker.size).contentEquals(marker) }
        elf[start] = 0xc3.toByte()
        elf[start + 1] = 0x28
        reject(zip("libdemo.so" to elf), "INVALID_MAPLESU_MODULE")
    }

    @Test fun kernelSuArchivesHaveNoNativePayloadHash() {
        val plain = ModuleArchiveInspector.inspect(archive("service.sh" to byteArrayOf()))
        assertEquals(ModuleArchiveInspector.ModuleType.KERNEL_SU, plain.type)
        assertNull(plain.nativePayloadSha256)
        // 顶层 .so 存在但不是可识别的 MAPLESU 入口：仍是 KERNEL_SU，载荷哈希必须为 null。
        val unknown = ModuleArchiveInspector.inspect(archive("libunknown.so" to byteArrayOf(1, 2, 3)))
        assertEquals(ModuleArchiveInspector.ModuleType.KERNEL_SU, unknown.type)
        assertNull(unknown.nativeEntry)
        assertNull(unknown.nativePayloadSha256)
    }

    @Test fun nativePayloadHashIsStableAndFollowsTheMemberBytes() {
        val elf = mapleSuElf()
        val file = zip("libdemo.so" to elf)
        val first = ModuleArchiveInspector.inspect(file)
        val second = ModuleArchiveInspector.inspect(file)
        assertEquals(first.sha256, second.sha256)
        assertEquals(first.nativePayloadSha256, second.nativePayloadSha256)
        assertEquals(sha256(elf), first.nativePayloadSha256)

        // 只改成员内容的一个字节：ELF 结构与 metadata 不变，整包哈希和载荷哈希都必须变。
        val changed = elf.copyOf().also { it[256] = (it[256].toInt() xor 1).toByte() }
        val third = ModuleArchiveInspector.inspect(zip("libdemo.so" to changed))
        assertEquals(ModuleArchiveInspector.ModuleType.MAPLESU, third.type)
        assertEquals(sha256(changed), third.nativePayloadSha256)
        assertNotEquals(first.nativePayloadSha256, third.nativePayloadSha256)
        assertNotEquals(first.sha256, third.sha256)
    }

    @Test fun nativePayloadHashBelongsToTheRecognizedEntryOnly() {
        val elf = mapleSuElf()
        val report = ModuleArchiveInspector.inspect(zip("libother.so" to byteArrayOf(9, 8, 7), "libdemo.so" to elf))
        assertEquals("libdemo.so", report.nativeEntry)
        assertEquals(sha256(elf), report.nativePayloadSha256)
    }

    @Test fun normalUsesTheFinalPropertyValueLikeKsud() {
        val file = zip("module.prop" to
            "id=maple_demo\nid=maple_demo\nname=first\nname=last\nversionCode=1\nversionCode=2\n".toByteArray())
        val report = ModuleArchiveInspector.inspectKernelSu(file)
        assertEquals("maple_demo", report.id)
        assertEquals("last", report.properties["name"])
        assertEquals(2, report.versionCode)
        reject(file, "INVALID_MODULE_METADATA")
    }

    @Test fun normalKeepsJavaPropertyWhitespaceCommentsAndEscapes() {
        val metadata = "# comment\n! comment\n  id : maple_demo  \nname=first\\\n  second\n" +
            "description=escaped\\ value\\:ok\nversionCode=  12  \n"
        val report = ModuleArchiveInspector.inspectKernelSu(zip("module.prop" to metadata.toByteArray()))
        assertEquals("maple_demo", report.id)
        assertEquals("firstsecond", report.properties["name"])
        assertEquals("escaped value:ok", report.properties["description"])
        assertEquals(12, report.versionCode)
    }

    @Test fun normalPreflightIgnoresTheUtf8BomLikeTheKsudPropertyReader() {
        val file = zip("module.prop" to ("\uFEFF" + prop).toByteArray())
        val before = file.readBytes()
        val report = ModuleArchiveInspector.inspectKernelSu(file)
        assertEquals("maple_demo", report.id)
        assertEquals("枫栖示例", report.properties["name"])
        assertArrayEquals(before, file.readBytes())
        reject(file, "INVALID_MODULE_ID")
    }

    @Test fun normalDoesNotRequireVersionCodeToFitTheManagerInteger() {
        for (raw in listOf("1.2.3", "2147483648", "-2147483649", "")) {
            val file = zip("module.prop" to "id=maple_demo\nversionCode=$raw\n".toByteArray())
            val report = ModuleArchiveInspector.inspectKernelSu(file)
            assertEquals("raw versionCode=$raw", 0, report.versionCode)
            assertEquals(raw, report.properties["versionCode"])
            reject(file, "INVALID_MODULE_METADATA")
        }
    }

    @Test fun normalTreatsNativeEntryStringsAsScriptModulePayload() {
        val file = archive("libhelper.so" to "diagnostic symbol: $mapleSuEntry".toByteArray())
        val report = ModuleArchiveInspector.inspectKernelSu(file)
        assertEquals(ModuleArchiveInspector.ModuleType.KERNEL_SU, report.type)
        assertEquals("maple_demo", report.id)
        assertNull(report.nativeEntry)
        assertNull(report.nativePayloadSha256)
        assertTrue(report.features.contains(ModuleArchiveInspector.Feature.NATIVE_PAYLOAD))
        reject(file, "INVALID_MAPLESU_MODULE")
    }

    @Test fun normalUsesModulePropEvenWhenThePackageBundlesAValidNativeModule() {
        val file = archive("libdemo.so" to mapleSuElf())
        val normal = ModuleArchiveInspector.inspectKernelSu(file)
        assertEquals(ModuleArchiveInspector.ModuleType.KERNEL_SU, normal.type)
        assertEquals("maple_demo", normal.id)
        assertNull(normal.nativeEntry)
        assertNull(normal.nativePayloadSha256)
        val ultra = ModuleArchiveInspector.inspect(file)
        assertEquals(ModuleArchiveInspector.ModuleType.MAPLESU, ultra.type)
        assertEquals("0123456789abcdef0123456789abcdef", ultra.id)
        assertEquals("libdemo.so", ultra.nativeEntry)
    }

    @Test fun normalRequiresModulePropInsteadOfNativeMetadata() {
        val file = zip("libdemo.so" to mapleSuElf())
        val failure = assertThrows(ModuleArchiveInspector.Failure::class.java) {
            ModuleArchiveInspector.inspectKernelSu(file)
        }
        assertEquals("MISSING_MODULE_METADATA", failure.code)
        assertEquals(ModuleArchiveInspector.ModuleType.MAPLESU, ModuleArchiveInspector.inspect(file).type)
    }

    @Test fun normalDoesNotApplyTheUltraMetadataLimitToOrdinaryRootLibraries() {
        val file = tmp.newFile()
        ZipOutputStream(file.outputStream()).use { archive ->
            archive.putNextEntry(ZipEntry("module.prop"))
            archive.write(prop.toByteArray())
            archive.closeEntry()
            archive.putNextEntry(ZipEntry("libhelper.so"))
            val block = ByteArray(1024 * 1024)
            repeat(33) { archive.write(block) }
            archive.closeEntry()
        }
        val report = ModuleArchiveInspector.inspectKernelSu(file)
        assertEquals(ModuleArchiveInspector.ModuleType.KERNEL_SU, report.type)
        assertEquals(33L * 1024 * 1024 + prop.toByteArray().size, report.expandedBytes)
        reject(file, "ARCHIVE_LIMIT_EXCEEDED")
    }

    @Test fun normalRetainsPathCrcAndGenericSizeChecks() {
        val invalidPath = archive("../outside" to byteArrayOf(1))
        val corrupt = archive("payload.bin" to byteArrayOf(17, 18, 19, 20))
        val bytes = corrupt.readBytes()
        val needle = byteArrayOf(17, 18, 19, 20)
        val offset = bytes.indices.first { it + needle.size <= bytes.size &&
            bytes.copyOfRange(it, it + needle.size).contentEquals(needle) }
        bytes[offset] = 99
        corrupt.writeBytes(bytes)
        val tooLarge = archive()
        val largeBytes = tooLarge.readBytes()
        val central = largeBytes.indices.first { it + 4 <= largeBytes.size &&
            largeBytes.copyOfRange(it, it + 4).contentEquals(byteArrayOf(0x50, 0x4b, 0x01, 0x02)) }
        ByteBuffer.wrap(largeBytes).order(ByteOrder.LITTLE_ENDIAN).putInt(central + 24, 129 * 1024 * 1024)
        tooLarge.writeBytes(largeBytes)
        for ((file, code) in listOf(invalidPath to "INVALID_ARCHIVE_PATH",
            corrupt to "ARCHIVE_CONTENT_MISMATCH", tooLarge to "ARCHIVE_LIMIT_EXCEEDED")) {
            val failure = assertThrows(ModuleArchiveInspector.Failure::class.java) {
                ModuleArchiveInspector.inspectKernelSu(file)
            }
            assertEquals(code, failure.code)
        }
    }
}
