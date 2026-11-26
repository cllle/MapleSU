package dev.maplesu.manager.ultrakernel

import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class UltraInitBootLocalBtfTest {
    private class Symbols(override val imageSize: Int) : UltraKernelSymbols {
        override val summary = "rcu-local-btf"
        override fun offsetOf(name: String) = if (name == "__start_BTF") 0 else null
        override fun isSymbolStart(value: Int) = false
        override fun nextOffset(offset: Int): Int? = null
    }

    @Test fun `resolver requests the compiled RCU struct name`() {
        val field = UltraInitBootTargetResolver::class.java.getDeclaredField("localTypes")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST") val types = field.get(null) as Set<String>
        assertTrue(types.toString(), "callback_head" in types)
        assertFalse(types.toString(), "rcu_head" in types)
        val raw = fixture()
        val signatures = UltraKernelBtf.typeSignatures(raw, Symbols(raw.size), types.intersect(setOf("callback_head", "rcu_head")))
        assertEquals(setOf("callback_head"), signatures!!.keys)
        assertEquals("scallback_head:16{0=*scallback_head;64=*fv(*scallback_head;);}", signatures.getValue("callback_head"))
    }

    @Test fun `RCU full shape preserves size offset and callback prototype differences`() {
        fun signature(raw: ByteArray) = UltraKernelBtf.typeSignatures(raw, Symbols(raw.size), setOf("callback_head"))
        val original = signature(fixture())
        assertEquals(original, signature(fixture()))
        for (raw in listOf(fixture(size = 24), fixture(funcOffset = 0), fixture(callbackReturn = 5))) {
            assertNotEquals(original, signature(raw))
        }
    }

    @Test fun `missing compiled RCU struct is still rejected`() {
        val raw = fixture(name = "unrelated_head")
        val error = assertThrows(UltraKernelGenerateException::class.java) {
            UltraKernelBtf.typeSignatures(raw, Symbols(raw.size), setOf("callback_head"))
        }
        assertTrue(error.detail, error.detail.contains("TYPE_MISSING:callback_head"))
    }

    private fun fixture(size: Int = 16, funcOffset: Int = 64,
        callbackReturn: Int = 0, name: String = "callback_head"): ByteArray {
        val strings = ByteArrayOutputStream().also { it.write(0) }
        val offsets = HashMap<String, Int>()
        fun text(value: String) = offsets.getOrPut(value) {
            if (value.isEmpty()) 0 else strings.size().also {
                strings.write(value.toByteArray(Charsets.US_ASCII)); strings.write(0)
            }
        }
        val types = ByteArrayOutputStream()
        fun word(value: Int) { repeat(4) { types.write(value ushr (it * 8) and 255) } }
        fun type(value: String, kind: Int, sizeOrType: Int, count: Int = 0) {
            word(text(value)); word(kind shl 24 or count); word(sizeOrType)
        }
        type(name, 4, size, 2) // 1: struct callback_head
        word(text("next")); word(2); word(0)
        word(text("func")); word(4); word(funcOffset)
        type("", 2, 1) // 2: callback_head *
        type("", 13, callbackReturn, 1) // 3: void (*)(callback_head *)
        word(text("head")); word(2)
        type("", 2, 3) // 4: callback function pointer
        type("unsigned int", 1, 4); word(32) // 5: distinct callback return ABI
        val body = types.toByteArray()
        val names = strings.toByteArray()
        return ByteArray(24 + body.size + names.size).also { raw ->
            listOf(0x0001EB9F, 24, 0, body.size, body.size, names.size).forEachIndexed { index, value ->
                UltraKernelA64.writeLe32(raw, index * 4, value.toLong())
            }
            body.copyInto(raw, 24); names.copyInto(raw, 24 + body.size)
        }
    }
}
