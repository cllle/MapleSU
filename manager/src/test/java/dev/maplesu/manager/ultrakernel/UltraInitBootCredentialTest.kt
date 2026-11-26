package dev.maplesu.manager.ultrakernel

import dev.maplesu.image.RamdiskInspector
import dev.maplesu.image.UltraInitBootProducer
import dev.maplesu.manager.runtime.ultra.UltraInstallCredentialPolicy
import dev.maplesu.manager.runtime.ultra.UltraInstallCredentialTransition
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real file producer and shared retain policy; synthetic ELF bytes are never executed. */
class UltraInitBootCredentialTest {
    @get:Rule val temporary = TemporaryFolder()
    private val original = UltraKernelCredential.of("Aa1".repeat(16)).withMetadataDisplacement(-8192)
    private val bootId = "11111111-1111-4111-8111-111111111111"
    private val nextBootId = "22222222-2222-4222-8222-222222222222"

    @Test fun boundIdentityReachesPreparationProducerAndExistingRetainPolicy() {
        val fixture = fixture()
        val sourceBefore = fixture.source.readBytes()
        val targetBefore = fixture.artifact.targetBoot.readBytes()
        val manifestBefore = fixture.manifest.readBytes()
        val prepared = prepare(fixture)
        val bound = requireNotNull(prepared.credential)
        val candidate = requireNotNull(prepared.candidate)
        assertEquals(original.withMetadataDisplacement(-4096), bound)
        assertEquals(-4096, bound.metadataDisplacement)
        val entries = RamdiskInspector.inspect(candidate.image).entries
        val parameter = entries.single { it.path == ".maplesu_ultra/credential" }
        val secret = bound.copySecret()
        try { assertEquals(UltraKernelSpec.sha256(secret), parameter.sha256) } finally { secret.fill(0) }
        assertEquals(48L, parameter.size)
        assertEquals(0x180, parameter.permissions())
        assertEquals(fixture.artifact.bridgeSha256, entries.single { it.path == ".maplesu_ultra/bridge.ko" }.sha256)
        assertNull(fixture.artifact.credentialSha256)
        assertArrayEquals(sourceBefore, fixture.source.readBytes())
        assertArrayEquals(targetBefore, fixture.artifact.targetBoot.readBytes())
        assertArrayEquals(manifestBefore, fixture.manifest.readBytes())

        val currentBytes = original.copySecret()
        val current = try { currentBytes.toString(Charsets.US_ASCII) } finally { currentBytes.fill(0) }
        val policy = UltraInstallCredentialPolicy({ current }, { null }, { null },
            { error("fixture identity must not be generated") }, { false })
        var pending: UltraInstallCredentialTransition? = null
        // retainCandidate uses this production policy and PendingStore uses this transition.
        policy.retain(bound, stageForReboot = { value ->
            val bytes = value.toByteArray(Charsets.US_ASCII)
            try { assertTrue(bound.matches(bytes)) } finally { bytes.fill(0) }
            pending = UltraInstallCredentialTransition.stage(current, bound, "install", candidate.bootSha256, bootId)
            true
        }, storeIfAbsent = { error("running identity must stay unchanged") })
        val retained = requireNotNull(pending)
        assertEquals(bound, retained.credential)
        assertEquals(bound, retained.confirm("install", candidate.bootSha256, bootId, current)
            .afterBoot(nextBootId, current, false))
        policy.retainForExport(bound, archive = { value ->
            val bytes = value.toByteArray(Charsets.US_ASCII)
            try { assertTrue(bound.matches(bytes)) } finally { bytes.fill(0) }
        }, storeIfAbsent = { error("export must preserve the existing pairing") })
    }

    @Test fun upgradeComparesActualParameterHashRatherThanAbsentGenericHash() {
        val fixture = fixture()
        val prepared = prepare(fixture)
        val image = requireNotNull(prepared.candidate).image
        assertFalse(UltraKernelFlow.initBootUpdateRequired(image, fixture.artifact, original))
        assertFalse(UltraKernelFlow.initBootUpdateRequired(image, fixture.artifact, requireNotNull(prepared.credential)))
        assertTrue(UltraKernelFlow.initBootUpdateRequired(image, fixture(displacement = -12288).artifact, original))
        assertTrue(UltraKernelFlow.initBootUpdateRequired(image, fixture(variant = 1).artifact, original))
        val other = UltraKernelCredential.of("Bb2".repeat(16)).withMetadataDisplacement(-4096)
        assertTrue(UltraKernelFlow.initBootUpdateRequired(image, fixture.artifact, other))
    }

    @Test fun legacyEmbeddedKeepsExactCredentialAndAddsNoParameterEntry() {
        val fixture = fixture(embedded = true)
        val prepared = prepare(fixture)
        assertSame(original, prepared.credential)
        val image = requireNotNull(prepared.candidate).image
        assertTrue(RamdiskInspector.inspect(image).entries.none { it.path == ".maplesu_ultra/credential" })
        assertFalse(UltraKernelFlow.initBootUpdateRequired(image, fixture.artifact, original))
        assertTrue(UltraKernelFlow.initBootUpdateRequired(image, fixture().artifact, original))
        assertTrue(UltraKernelFlow.initBootUpdateRequired(image, fixture.artifact, UltraKernelCredential.of("C".repeat(48))))
    }

    @Test fun directBuilderRejectsWrongMetadataBeforeProducingAnything() {
        val fixture = fixture()
        val bytes = original.copySecret()
        val wrong = try { fixture.artifact.withCredential(bytes) } finally { bytes.fill(0) }
        val workspace = temporary.newFolder()
        val failure = runCatching { UltraKernelBuilder(workspace).generateInitBoot(fixture.source, wrong, original) }
            .exceptionOrNull()
        assertEquals("ULTRA_KERNEL_INIT_BOOT_CREDENTIAL_METADATA_MISMATCH", failure?.message)
        assertTrue(workspace.listFiles().orEmpty().isEmpty())
    }

    @Test fun realPinnedCredentialMismatchFailsBeforeAnyCandidate() {
        val fixture = fixture(pinnedHash = "0".repeat(64))
        val workspace = temporary.newFolder()
        val sourceBefore = fixture.source.readBytes()
        val failure = runCatching { UltraKernelFlow(workspace, UltraKernelMemoryAssets(emptyMap()))
            .prepareTarget(fixture.source, original, null, fixture.artifact) }.exceptionOrNull()
        assertEquals("ULTRA_INIT_BOOT_CREDENTIAL_HASH", failure?.message)
        assertTrue(workspace.listFiles().orEmpty().isEmpty())
        assertArrayEquals(sourceBefore, fixture.source.readBytes())
    }

    private data class Fixture(val source: File, val artifact: UltraInitBootProducer.Artifact, val manifest: File)

    private fun prepare(fixture: Fixture): UltraKernelFlow.Preparation =
        UltraKernelFlow(temporary.newFolder(), UltraKernelMemoryAssets(emptyMap()))
            .prepareTarget(fixture.source, original, null, fixture.artifact)

    private fun fixture(embedded: Boolean = false, displacement: Int = -4096,
                        variant: Int = 0, pinnedHash: String? = null): Fixture {
        val directory = temporary.newFolder()
        val helpers = Class.forName("dev.maplesu.image.UltraInitBootProducerTest")
        fun bytes(name: String, vararg args: Any): ByteArray {
            val types = args.map { when (it) {
                is Boolean -> java.lang.Boolean.TYPE
                is Int -> java.lang.Integer.TYPE
                else -> it.javaClass
            } }.toTypedArray()
            val method = helpers.getDeclaredMethod(name, *types).apply { isAccessible = true }
            return method.invoke(null, *args) as ByteArray
        }
        val kernel = if (embedded) bytes("kernel") else {
            val layout = UltraKernelKallsymsLayoutTest::class.java.getDeclaredMethod("fixture",
                java.lang.Boolean.TYPE, java.lang.Integer.TYPE, java.lang.Integer.TYPE,
                java.lang.Integer.TYPE, Map::class.java).apply { isAccessible = true }
            (layout.invoke(UltraKernelKallsymsLayoutTest(), false, 1025, 0x5000, 0,
                emptyMap<Int, Pair<String, Int>>()) as ByteArray).apply {
                ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putLong(24, 2)
                "Linux version 6.6.1-fixture (byte fixture)\n".toByteArray(Charsets.US_ASCII).copyInto(this, 0x9000)
            }
        }
        val module = if (embedded) bytes("module").also {
            val elf = ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN)
            val symbols = elf.getLong(40).toInt() + 3 * elf.getShort(58).toInt()
            elf.putInt(symbols + 40, 1).putLong(symbols + 56, 24)
        } else bytes("parameterModule", 4096 - displacement).also {
            val elf = ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN)
            val section = elf.getLong(40).toInt() + 5 * elf.getShort(58).toInt()
            val at = elf.getLong(section + 24).toInt()
            val count = elf.getLong(section + 32).toInt()
            val parameter = "parmtype=deployment_namespace:string\u0000".toByteArray(Charsets.US_ASCII)
            parameter.copyInto(it, at + count)
            elf.putLong(section + 32, (count + parameter.size).toLong())
        }
        val files = mapOf("target-boot.img" to bytes("image", kernel, ByteArray(0)),
            "ultra-init" to bytes("handoff"), "bridge.ko" to (module + variant.toByte()))
        files.forEach { (path, content) -> File(directory, path).writeBytes(content) }
        val manifest = File(directory, "manifest.json")
        manifest.writeText(buildJsonObject {
            put("schemaVersion", 1)
            put("kernelRelease", "6.6.1-fixture")
            put("kernelSha256", UltraKernelSpec.sha256(kernel))
            if (embedded) {
                val secret = original.copySecret()
                try { put("credentialSha256", UltraKernelSpec.sha256(secret)) } finally { secret.fill(0) }
            } else {
                put("credentialMode", "module-parameter")
                put("metadataDisplacement", displacement)
                pinnedHash?.let { put("credentialSha256", it) }
            }
            for ((field, path) in mapOf("targetBoot" to "target-boot.img", "handoff" to "ultra-init", "bridge" to "bridge.ko")) {
                put(field, path)
                put(field + "Sha256", UltraKernelSpec.sha256(files.getValue(path)))
            }
        }.toString())
        val source = File(directory, "init_boot.img").also {
            it.writeBytes(bytes("image", ByteArray(0), bytes("stock", false, false)))
        }
        return Fixture(source, UltraInitBootProducer.readArtifact(manifest), manifest)
    }
}
