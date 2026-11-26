package dev.maplesu.manager.ultrakernel

/** Absolute data pointers in the input Image, including its actual AArch64 RELATIVE records. */
internal class UltraKernelImagePointers(private val kernel: ByteArray, private val symbols: UltraKernelSymbols) {
    private fun fail(reason: String): Nothing = throw UltraKernelGenerateException(
        UltraKernelGenerateCodes.STRUCT_OFFSET, "STRUCT_IMAGE_POINTER:$reason")
    private fun u64(at: Int): Long {
        if (at < 0 || at.toLong() + 8 > kernel.size) fail("BOUNDS")
        return (0 until 8).fold(0L) { value, i -> value or ((kernel[at + i].toLong() and 255) shl (i * 8)) }
    }
    private data class Rela(val slot: Long, val addend: Long)
    private fun record(at: Int): Rela? {
        if (at < 0 || at.toLong() + 24 > kernel.size || u64(at + 8) != 1027L) return null
        val slot = u64(at)
        if (slot >= 0 || slot < -(1L shl 48) || slot and 7L != 0L) return null
        return Rela(slot, u64(at + 16))
    }
    private val relocations = linkedMapOf<Int, Long>()
    private val base: Long
    init {
        val known = (symbols as? UltraKernelKallsymsTable)?.textBase?.takeIf { it != 0L }
        val imageHeader = kernel.size >= 64 && u64(56).toInt() == 0x644d5241
        val textOffset = if (imageHeader) u64(8) else 0L
        val kimage = symbols.offsetsOf("kimage_vaddr").singleOrNull()
        val roots = if (kimage == null || !imageHeader) emptyList() else
            (0..kernel.size - 24 step 8).mapNotNull { at ->
                record(at)?.takeIf { it.slot - it.addend == kimage.toLong() + textOffset }
                    ?.let { at to (it.slot - kimage) }
            }
        val rooted = roots.map { it.second }.distinct().singleOrNull()
        if (roots.isNotEmpty() && rooted == null || known != null && rooted != null && known != rooted)
            fail("BASE_CONFLICT")
        base = known ?: rooted ?: fail("BASE_UNPROVEN")
        if (base >= 0 || base < -(1L shl 48)) fail("BASE_RANGE")
        val memoryEnd = maxOf(kernel.size, symbols.memoryOffsetOf("_end") ?: kernel.size).toLong()
        for ((root, _) in roots) {
            var first = root
            while (first >= 24 && record(first - 24) != null) first -= 24
            var at = first
            while (true) {
                val item = record(at) ?: break
                val slot = item.slot - base
                if (slot < 0 || slot + 8 > memoryEnd) fail("RELA_SLOT")
                if (slot + 8 <= kernel.size) {
                    val old = relocations.put(slot.toInt(), item.addend)
                    if (old != null && old != item.addend) fail("RELA_CONFLICT")
                }
                at += 24
            }
        }
    }
    private fun value(at: Int): Long {
        val raw = u64(at)
        val relocated = relocations[at]
        if (raw != 0L && relocated != null && raw != relocated) fail("RAW_RELA_CONFLICT")
        return relocated ?: raw
    }
    fun isNull(at: Int): Boolean = value(at) == 0L
    fun pointer(at: Int): Int? {
        val value = value(at)
        if (value == 0L) return null
        val offset = value - base
        return offset.takeIf { it >= 0 && it < kernel.size }?.toInt()
    }
    fun references(at: Int): Set<Int> {
        val end = symbols.nextOffset(at) ?: fail("SYMBOL_END")
        if (at < 0 || at and 7 != 0 || end <= at || end > kernel.size) fail("SYMBOL_BOUNDS")
        return (at until end - 7 step 8).mapNotNull(::pointer).toSet()
    }
    fun text(at: Int): String {
        if (at !in kernel.indices) fail("STRING_ADDRESS")
        val end = (at until minOf(kernel.size, at + 256)).firstOrNull { kernel[it] == 0.toByte() }
            ?: fail("STRING_TERMINATOR")
        if ((at until end).any { (kernel[it].toInt() and 255) !in 32..126 }) fail("STRING_ENCODING")
        return String(kernel, at, end - at, Charsets.US_ASCII)
    }
}
