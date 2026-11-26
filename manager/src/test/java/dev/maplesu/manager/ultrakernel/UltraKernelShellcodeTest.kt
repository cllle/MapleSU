package dev.maplesu.manager.ultrakernel

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The code-body contract: each body must assemble to exactly the reference k22 blob on 6.6.89, from
 * nothing but symbol offsets and `task_struct` field offsets.
 *
 * This is the regression gate that replaced "the recipe contains the reference machine code". The
 * nine hex strings below are the *oracle*, and they live in the test rather than the product - so
 * the product cannot emit them by copying, it has to compute them.
 */
class UltraKernelShellcodeTest {

    // These are this test oracle's credential offsets, never production defaults.
    private val fields = UltraKernelFields(comm = CRED + 16, cred = CRED, credSuid = 16, credEuid = 24)
    private val BASE_SITE = UltraKernelPlanFixtures.K22_SYMBOL_BASE.getValue("__drm_printfn_coredump")

    private fun body(
        id: String,
        site: Int,
        symbols: UltraKernelSymbols = symbols(),
    ): UltraKernelBody = UltraKernelBodies.generate(id, site, UltraKernelSymbolResolver(symbols), fields)

    @Test fun `a consumed credential field has no implicit reference default`() {
        for ((missing, name) in listOf(fields.copy(credSuid = null) to "cred.suid",
            fields.copy(credEuid = null) to "cred.euid", fields.copy(cred = null) to "task_struct.cred")) {
            val error = org.junit.Assert.assertThrows(UltraKernelGenerateException::class.java) {
                UltraKernelBodies.generate("execve-gate", 0x1000, UltraKernelSymbolResolver(symbols()), missing)
            }
            assertEquals(UltraKernelGenerateCodes.STRUCT_OFFSET, error.code)
            assertEquals("STRUCT_OFFSET_NOT_FOUND:$name", error.detail)
        }
    }

    @Test fun `an executor does not require unused task fields`() {
        val generated = UltraKernelBodies.generate("printfn-bracket", BASE_SITE,
            UltraKernelSymbolResolver(symbols()), UltraKernelFields(credSuid = 16))
        assertEquals(UltraKernelBodies.EXECUTOR_BYTES, generated.bytes.size)
    }

    @Test
    fun `every body is registered and its cave length is the record length`() {
        assertEquals((ORACLE.keys + setOf("module-alloc-adapter", "printfn-bracket", "metadata-rebase", "request-v2", "execve-null-guard")).sorted(), UltraKernelBodies.roles().sorted())
        ORACLE.forEach { (id, spec) ->
            if (id == "execve-gate") {
                assertEquals(UltraKernelBodies.EXECVE_GATE_BYTES, UltraKernelBodies.lengthOf(id))
                return@forEach
            }
            assertEquals("cave length for $id", spec.hex.length / 2, UltraKernelBodies.lengthOf(id))
            assertEquals("cave length for $id", spec.length, UltraKernelBodies.lengthOf(id))
        }
    }

    @Test
    fun `every body reproduces the reference k22 bytes`() {
        ORACLE.forEach { (id, spec) ->
            val generated = body(id, spec.site)
            val expected = hex(spec.hex)
            if (id == "execve-gate") {
                // Dispatcher branch displacements change; the entry ABI and
                // request decoding prefix remain byte-identical.
                assertArrayEquals(expected.copyOfRange(0, 8), generated.bytes.copyOfRange(0, 8))
                return@forEach
            }
            if (id == "execve-tail") {
                assertEquals(UltraKernelA64.encodeB((spec.site + 12).toLong(),
                    (UltraKernelPlanFixtures.K22_SYMBOL_BASE.getValue("die") + UltraKernelBodies.EXECVE_ALLOC_RESUME).toLong()),
                    UltraKernelA64.le32Bits(generated.bytes, 12))
                return@forEach
            }
            assertEquals("$id length", expected.size, generated.bytes.size)
            val mismatch = firstDifference(expected, generated.bytes)
            assertEquals("$id differs at word $mismatch", -1, mismatch)
            assertArrayEquals("$id bytes", expected, generated.bytes)
        }
    }

    @Test
    fun `every generated word is a legal AArch64 encoding`() {
        (ORACLE + ("printfn-bracket" to Spec(BASE_SITE, 188, ""))).forEach { (id, spec) ->
            val generated = body(id, spec.site)
            assertEquals(
                "$id has a word the emitter should never produce",
                emptyList<Int>(),
                UltraKernelA64Verify.invalidWords(generated.bytes, generated.dataOffsets),
            )
        }
        // The one body that carries a literal must declare it, or validation would be meaningless.
        assertTrue(
            "dev-printk must declare its literal as data",
            body("dev-printk", ORACLE.getValue("dev-printk").site).dataOffsets.isNotEmpty(),
        )
    }

    @Test
    fun `executor publishes every native page before original icache publication`() {
        for ((shift, stride) in listOf(12 to 1, 14 to 4, 16 to 16)) {
            val generated = UltraKernelBodies.generate("printfn-bracket", BASE_SITE,
                UltraKernelSymbolResolver(symbols()), fields.copy(pageShift = shift)).bytes
            assertEquals(188, generated.size)
            // Independent encodings from assembled AArch64, including zero-count CBZ.
            // The publish page is `tlbi vmalle1is` (0xd508831f): a by-VA `tlbi vaae1is`
            // alone was measured insufficient for a just-reprotected executable page on
            // the 6.6.89 device, so the full inner-shareable stage-1 invalidation is the
            // published contract. 0x6c8308d5 (vaae1is, x12) is the rejected form.
            val expected = hex("ec3740a98cdd4cd39f3a03d5ad0000b41f8308d5" +
                when (stride) { 1 -> "8c050091"; 4 -> "8c110091"; else -> "8c410091" } +
                "ad0500f1a1ffff549f3b03d5df3f03d5eb4741a9")
            assertArrayEquals(expected, generated.copyOfRange(140, 140 + expected.size))
            assertEquals(UltraKernelA64.encodeB((BASE_SITE + 44).toLong(), (BASE_SITE + 140).toLong()),
                UltraKernelA64.le32Bits(generated, 44))
            assertEquals(UltraKernelA64.blr(10), UltraKernelA64.le32Bits(generated, 64))
            assertArrayEquals(hex("ea0340f91f7108d59f3b03d5df3f03d540013fd6"), generated.copyOfRange(48, 68))
            assertEquals(UltraKernelA64.encodeB((BASE_SITE + 184).toLong(), (BASE_SITE + 48).toLong()),
                UltraKernelA64.le32Bits(generated, 184))
        }
    }

    /**
     * The bodies are parameterised, not replayed: moving a symbol they reference must change the
     * bytes, and must not change anything else. A frozen blob cannot have this property, and it is
     * the reason one recipe can serve a different kernel.
     */
    @Test
    fun `a moved symbol rewrites only the words that reference it`() {
        val spec = ORACLE.getValue("printk-flush")
        val reference = body("printk-flush", spec.site)
        val moved = body(
            "printk-flush",
            spec.site,
            symbols(mapOf("arm64_notify_die" to UltraKernelPlanFixtures.K22_SYMBOL_BASE.getValue("arm64_notify_die") + 0x40)),
        )
        assertNotEquals(reference.bytes.toList(), moved.bytes.toList())
        // `b arm64_notify_die` is the word at +0x20; nothing before it may move.
        assertArrayEquals(reference.bytes.copyOfRange(0, 0x20), moved.bytes.copyOfRange(0, 0x20))
        assertEquals(1, changedWords(reference.bytes, moved.bytes))
    }

    /** A `task_struct` field offset is an argument: a different kernel shifts the loads that use it. */
    @Test
    fun `a different cred offset rewrites the loads that read it`() {
        val spec = ORACLE.getValue("execve-gate")
        val reference = body("execve-gate", spec.site)
        val shifted = UltraKernelBodies.generate(
            "execve-gate",
            spec.site,
            UltraKernelSymbolResolver(symbols()),
            fields.copy(cred = CRED + 0x40),
        )
        assertEquals(1, changedWords(reference.bytes, shifted.bytes))
        assertNotEquals(reference.bytes.toList(), shifted.bytes.toList())
    }

    /**
     * A negative `panic` displacement uses the compact ADRP form. The separately generated
     * die+0xD4 body still returns to die+156, so that slot must remain the x17 restore rather
     * than sliding into `case1` when the address materialisation becomes shorter.
     */
    @Test
    fun `a far panic keeps the execve tail resume at its fixed offset`() {
        val base = UltraKernelPlanFixtures.K22_SYMBOL_BASE
        val die = base.getValue("die")
        val farPanic = 0x1000
        val relocated = FakeSymbols(base + ("panic" to farPanic), 0x1_1000_000)
        val gate = UltraKernelBodies.generate(
            "execve-gate", die, UltraKernelSymbolResolver(relocated), fields,
        )
        val tail = UltraKernelBodies.generate(
            "execve-tail", die + UltraKernelBodies.EXECVE_GATE_BYTES, UltraKernelSymbolResolver(relocated), fields,
        )

        assertEquals(
            "die+156 must be ldp x17, xzr, [sp], #16",
            UltraKernelA64.pair("ldp", 17, 31, 31, 2, true, 1),
            UltraKernelA64.le32Bits(gate.bytes, UltraKernelBodies.EXECVE_ALLOC_RESUME),
        )
        val branch = UltraKernelA64.le32Bits(tail.bytes, 12)
        val displacement = UltraKernelA64.signExtend((branch and 0x03FFFFFF).toLong(), 26) * 4
        assertEquals("execve-tail must return to the x17 restore", die + UltraKernelBodies.EXECVE_ALLOC_RESUME,
            die + UltraKernelBodies.EXECVE_GATE_BYTES + 12 + displacement.toInt())
    }

    @Test
    fun `a body refuses to assemble when a symbol it calls is gone`() {
        val error = try {
            UltraKernelBodies.generate(
                "puts-guide",
                ORACLE.getValue("puts-guide").site,
                UltraKernelSymbolResolver(Without("path_put")),
                fields,
            )
            null
        } catch (expected: UltraKernelGenerateException) {
            expected
        }
        assertEquals(UltraKernelGenerateCodes.TARGET_SYMBOL_MISSING, error?.code)
        assertEquals("path_put", error?.detail)
    }

    @Test
    fun `an execmem allocation resolves to the shared ABI adapter`() {
        val base = UltraKernelPlanFixtures.K22_SYMBOL_BASE
        val aliased = FakeSymbols(base.filterKeys { it != "module_alloc" } +
            ("execmem_alloc" to base.getValue("module_alloc")), 0x1_1000_000)
        assertEquals(base.getValue("die") + UltraKernelBodies.EXECVE_GATE_BYTES + 16, UltraKernelSymbolResolver(aliased).require("module_alloc"))
    }

    @Test
    fun `an unknown body is refused by name`() {
        val error = try {
            body("no-such-body", 0x1000)
            null
        } catch (expected: UltraKernelGenerateException) {
            expected
        }
        assertEquals(UltraKernelGenerateCodes.BODY, error?.code)
        assertTrue(error!!.detail.startsWith("BODY_UNKNOWN:"))
    }

    private fun symbols(overrides: Map<String, Int> = emptyMap()): UltraKernelSymbols =
        FakeSymbols(UltraKernelPlanFixtures.K22_SYMBOL_BASE + overrides, 0x1_1000_000)

    private fun hex(text: String): ByteArray = ByteArray(text.length / 2) {
        ((Character.digit(text[it * 2], 16) shl 4) or Character.digit(text[it * 2 + 1], 16)).toByte()
    }

    private fun firstDifference(expected: ByteArray, actual: ByteArray): Int {
        var position = 0
        while (position + 4 <= minOf(expected.size, actual.size)) {
            if (UltraKernelA64.le32Bits(expected, position) != UltraKernelA64.le32Bits(actual, position)) {
                return position / 4
            }
            position += 4
        }
        return if (expected.size == actual.size) -1 else position / 4
    }

    private fun changedWords(before: ByteArray, after: ByteArray): Int {
        var count = 0
        var position = 0
        while (position + 4 <= minOf(before.size, after.size)) {
            if (UltraKernelA64.le32Bits(before, position) != UltraKernelA64.le32Bits(after, position)) count++
            position += 4
        }
        return count
    }

    /** The delegate with [removed] hidden, to prove a missing symbol fails instead of guessing. */
    private inner class Without(vararg removed: String) : UltraKernelSymbols {
        private val delegate = symbols()
        private val gone = removed.toSet()
        override val imageSize: Int get() = delegate.imageSize
        override val summary: String get() = delegate.summary
        override fun offsetOf(name: String): Int? = if (name in gone) null else delegate.offsetOf(name)
        override fun isSymbolStart(value: Int): Boolean = delegate.isSymbolStart(value)
        override fun nextOffset(offset: Int): Int? = delegate.nextOffset(offset)
    }

    /** One reference blob, pinned as the oracle. */
    private class Spec(val site: Int, val length: Int, val hex: String)

    private companion object {
        /** `task_struct.cred` on 6.6.89, read out of `__arm64_sys_getuid`. */
        const val CRED = 0x820

        private val BASE = UltraKernelPlanFixtures.K22_SYMBOL_BASE

        /** body id -> the reference blob it must reproduce, and the site it was written at. */
        val ORACLE: Map<String, Spec> = linkedMapOf(
            "execve-gate" to Spec(
                BASE.getValue("die"), 212,
                "cbff81923f000beb42060054104138d5111244f92b0040f90c0000100d089cd24d20a0f28c010d8b0d0080d26e696d388f696d38df010f6b61040054ad050091bfc100f14bffff546c010d8b8e0140398f494039ef010e0b8f4900398c0500918e4940394effff356c094139604144f89fc50071800100549fc90071a00100549fcd0071a00100549fd1007180010054f17fbfa910000014c2390094f17fc1a8070000146e0d41390e000039046f23140000403902000014056e2314200a00f9040000142d1a40b94d0000350b3f241408010014",
            ),
            "execve-tail" to Spec(
                BASE.getValue("die") + 212, 16,
                "604d40b940000034b1390094efffff17",
            ),
            "execve-frame" to Spec(
                BASE.getValue("arm64_notify_die"), 52,
                "fd7bc1a8e61fc1a8e417c1a8e20fc1a8e007c1a810f80e143f2303d5e007bfa9e20fbfa9e417bfa9e61fbfa9fd7bbfa9b8feff17",
            ),
            "puts-guide" to Spec(
                BASE.getValue("__drm_puts_coredump"), 88,
                "c0035fd6ecb9ff908cb13091eb33bea9e0030caae243009121008052cc9beb97e0000035e04300912699eb97eb3340a98d1540386d150038cdffff35eb33c2a83193dc171f2003d51f2003d51f2003d51f2003d51f2003d5",
            ),
            "printk-flush" to Spec(
                BASE.getValue("drm_dev_printk"), 108,
                "c0035fd62e003bd5ce4d50d3ce0900910024ce9a0020ce9a207a0bd59f3b03d50692dc170b4138d56c1144f98d1940b9cd010035eecf04b26c1944f99f010eeb200100542ecd8d522e8dae729f010e6bc10000546dd160398d0000353f28007142000054e0031f2ac0035fd6",
            ),
            "dev-printk" to Spec(
                BASE.getValue("__dev_printk"), 84,
                "c0035fd60cc260399fa5017121020054050000142f6170705f70726f63657373363400008affff10ec030baaef030aaaee154038ce0000348d154038ad000034bf010e6b4f118f9afaffff17ac2eff17ebc1db17",
            ),
        )
    }
}
