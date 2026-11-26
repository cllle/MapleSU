package dev.maplesu.manager.ultrakernel

/**
 * The AArch64 encodings the Ultra recipe needs, and nothing more.
 *
 * A Ultra replacement blob is bytes, but every address-bearing field inside it must be *recomputed*
 * for the target kernel at generation time, so the generator has to be able to encode:
 *
 *  - `B` / `BL` (`imm26`, PC-relative, +-128 MiB);
 *  - `MOVZ` + `MOVK` runs that build a 64 bit displacement;
 *  - `ADR` (`imm21`, PC-relative) for the residue check;
 *  - `MOVZ #1`, `MOV WZR`, `RET`, `NOP` - the one-instruction stubs.
 *
 * This is a direct port of `tools/specgen/specgen/a64.py`, which is the implementation that
 * reproduced k22 byte for byte on the reference kernel. Two rules are load-bearing and are
 * repeated here because they were real defects there:
 *
 *  - `B` and `BL` must be distinguished with mask `0xFC000000`. Masking `0x7C000000` (which drops
 *    bit 31) silently encodes every `bl` as a `b`;
 *  - a `MOVZ`/`MOVK` immediate may have to be re-emitted into an *existing* word position, so the
 *    word count a relocation declares is part of the contract - see [encodeImm64].
 *
 * All offsets are file offsets inside the raw `Image` (which is the address space the spec works
 * in). Encoding is layout independent: the caller passes the offset the instruction lands at.
 *
 * Besides the primitives, this file carries [UltraKernelAsm]: a two-pass, label-based assembler.
 * That is what makes the nine code bodies *generated* rather than carried: the shellcode author
 * writes `b("end")` / `adr(12, "text")` / `ldr(0, 11, fields.cred)`, and every displacement,
 * absolute address and `movz`/`movk` run length is computed from the layout that results.
 */
internal object UltraKernelA64 {

    const val NOP: Int = 0xD503201F.toInt()
    const val RET: Int = 0xD65F03C0.toInt()

    /** `dsb ish` - the barrier before a freshly written code page is executed. */
    const val DSB_ISH: Int = 0xD5033B9F.toInt()
    const val DSB_ISHST: Int = 0xD5033A9F.toInt()
    const val ISB: Int = 0xD5033FDF.toInt()

    /** `paciasp` / pointer-authentication prologue used by the 6.6 GKI build. */
    const val PACIA1716: Int = 0xD503233F.toInt()

    /** `mrs xN, sp_el0` base word; OR in the destination register. */
    const val MRS_SP_EL0: Int = 0xD5384100.toInt()

    /** `mrs xN, ctr_el0` base word. */
    const val MRS_CTR_EL0: Int = 0xD53B0020.toInt()

    const val SP: Int = 31
    const val XZR: Int = 31

    const val EQ = 0
    const val NE = 1
    const val CS = 2
    const val CC = 3
    const val LT = 11
    const val GE = 10
    const val HI = 8
    const val LS = 9

    /** Sign-extend the low [bits] of [value]. */
    fun signExtend(value: Long, bits: Int): Long {
        val shift = 64 - bits
        return (value shl shift) shr shift
    }

    fun encodeB(here: Long, target: Long): Int = encodeBranch(0x14000000, here, target)

    fun encodeBl(here: Long, target: Long): Int = encodeBranch(0x94000000.toInt(), here, target)

    private fun encodeBranch(opcode: Int, here: Long, target: Long): Int {
        val delta = target - here
        require(delta % 4 == 0L) { "BRANCH_NOT_ALIGNED:$delta" }
        val imm26 = delta / 4
        require(imm26 >= -(1L shl 25) && imm26 < (1L shl 25)) { "BRANCH_OUT_OF_RANGE:$delta" }
        return opcode or (imm26.toInt() and 0x03FFFFFF)
    }

    fun isBl(word: Int): Boolean = (word and 0xFC000000.toInt()) == 0x94000000.toInt()

    /** `b.cond`; [cond] is the raw 4 bit condition field. */
    fun encodeBCond(cond: Int, here: Long, target: Long): Int {
        val delta = target - here
        require(delta % 4 == 0L) { "BRANCH_NOT_ALIGNED:$delta" }
        val imm19 = delta / 4
        require(imm19 >= -(1L shl 18) && imm19 < (1L shl 18)) { "BRANCH_OUT_OF_RANGE:$delta" }
        return 0x54000000 or ((imm19.toInt() and 0x7FFFF) shl 5) or (cond and 0xF)
    }

    /** `cbz` (or `cbnz` when [nonZero]) on a 32 bit ([sf] false) or 64 bit register. */
    fun encodeCbz(rt: Int, here: Long, target: Long, sf: Boolean, nonZero: Boolean): Int {
        val delta = target - here
        require(delta % 4 == 0L) { "BRANCH_NOT_ALIGNED:$delta" }
        val imm19 = delta / 4
        require(imm19 >= -(1L shl 18) && imm19 < (1L shl 18)) { "BRANCH_OUT_OF_RANGE:$delta" }
        val base = if (sf) 0xB4000000.toInt() else 0x34000000
        return base or (if (nonZero) 0x01000000 else 0) or
            ((imm19.toInt() and 0x7FFFF) shl 5) or (rt and 0x1F)
    }

    /**
     * `adr rd, target`.
     *
     * The immediate is `immhi:immlo` sign extended over 21 bits and is **byte granular** (the low
     * two bits are zero because instructions are aligned). Treating it as a 4 byte scaled field -
     * the shape `b`/`bl` use - is a defect that misplaces every `adr` by its own displacement.
     */
    fun encodeAdr(here: Long, target: Long, rd: Int = 0): Int {
        val delta = target - here
        require(delta % 4 == 0L) { "ADR_NOT_ALIGNED:$delta" }
        require(delta >= -(1L shl 20) && delta < (1L shl 20)) { "ADR_OUT_OF_RANGE:$delta" }
        val immlo = (delta and 0x3).toInt()
        val immhi = ((delta shr 2) and 0x7FFFF).toInt()
        return 0x10000000 or (immlo shl 29) or (immhi shl 5) or (rd and 0x1F)
    }

    fun isAdr(word: Int): Boolean = (word and 0x9F000000.toInt()) == 0x10000000

    /**
     * `adrp rd, target`.
     *
     * The immediate is the difference of the two *4 KiB pages*, so the reach is +-4 GiB even though
     * the field is only 21 bits. It exists here for the one place a `movz`/`movk` run would not fit
     * the cave it has to live in: a displacement whose four 16 bit groups are all non-zero costs four
     * words, and `execve-gate` has no spare word.
     */
    fun encodeAdrp(here: Long, target: Long, rd: Int = 0): Int {
        val delta = (target ushr 12) - (here ushr 12)
        require(delta >= -(1L shl 20) && delta < (1L shl 20)) { "ADRP_OUT_OF_RANGE:$delta" }
        val immlo = (delta and 0x3).toInt()
        val immhi = ((delta shr 2) and 0x7FFFF).toInt()
        return 0x90000000.toInt() or (immlo shl 29) or (immhi shl 5) or (rd and 0x1F)
    }

    fun isAdrp(word: Int): Boolean = (word and 0x9F000000.toInt()) == 0x90000000.toInt()

    /** Target of an `adr` word placed at [here], or `null` when [word] is not an `adr`. */
    fun decodeAdr(word: Int, here: Long): Long? {
        if (!isAdr(word)) return null
        val imm = (((word shr 5) and 0x7FFFF).toLong() shl 2) or ((word shr 29) and 0x3).toLong()
        return here + signExtend(imm, 21)
    }

    fun movz(rd: Int, imm16: Int, shift: Int = 0, sf: Int = 1): Int =
        (if (sf != 0) 0xD2800000.toInt() else 0x52800000) or
            ((shift / 16) shl 21) or
            ((imm16 and 0xFFFF) shl 5) or
            (rd and 0x1F)

    fun movk(rd: Int, imm16: Int, shift: Int = 0, sf: Int = 1): Int =
        (if (sf != 0) 0xF2800000.toInt() else 0x72800000) or
            ((shift / 16) shl 21) or
            ((imm16 and 0xFFFF) shl 5) or
            (rd and 0x1F)

    fun movn(rd: Int, imm16: Int, shift: Int = 0, sf: Int = 1): Int =
        (if (sf != 0) 0x92800000.toInt() else 0x12800000) or
            ((shift / 16) shl 21) or
            ((imm16 and 0xFFFF) shl 5) or
            (rd and 0x1F)

    /**
     * `movz` + up to three `movk` building [value] in [rd].
     *
     * The emitted length depends on the value: two's complement displacements lose their zero
     * 16 bit groups. A relocation that declares `words` therefore has to *fail closed* when this
     * returns a different count - see `UltraKernelRecipe`.
     */
    fun encodeImm64(rd: Int, value: Long, sf: Int = 1): IntArray {
        val words = ArrayList<Int>(4)
        words += movz(rd, (value and 0xFFFF).toInt(), 0, sf)
        for (shift in intArrayOf(16, 32, 48)) {
            if (shift >= (if (sf != 0) 64 else 32)) break
            val part = ((value ushr shift) and 0xFFFF).toInt()
            if (part != 0) words += movk(rd, part, shift, sf)
        }
        return words.toIntArray()
    }

    /**
     * `(n, immr, imms)` for a bitmask immediate, or `null` when [value] is not encodable.
     *
     * The inverse of the ARM `DecodeBitMasks` pseudocode: find the smallest element size whose
     * replication reproduces [value], then find the rotation of a run of ones that equals that
     * element. `imms` carries ones above `len` (the `N:NOT(imms)` term), which is why the high
     * bits are set rather than zero.
     */
    fun bitmaskFields(value: Long, sf: Int): IntArray? {
        val width = if (sf != 0) 64 else 32
        val mask = if (width == 64) -1L else (1L shl 32) - 1
        val v = value and mask
        if (v == 0L || v == mask) return null
        var esize = 2
        var elem = 0L
        var found = false
        while (esize <= width) {
            val em = if (esize == 64) -1L else (1L shl esize) - 1
            val e = v and em
            var rep = 0L
            var i = 0
            while (i < width) {
                rep = rep or (e shl i)
                i += esize
            }
            if (rep and mask == v) {
                elem = e
                found = true
                break
            }
            esize *= 2
        }
        if (!found) return null
        val ones = java.lang.Long.bitCount(elem)
        if (ones == 0 || ones == esize) return null
        val target = (1L shl ones) - 1
        var rotation = -1
        var r = 0
        while (r < esize) {
            if (rotateRight(target, r, esize) == elem) {
                rotation = r
                break
            }
            r++
        }
        if (rotation < 0) return null
        val s = ones - 1
        if (esize == 64) return intArrayOf(1, rotation, s)
        val len = Integer.numberOfTrailingZeros(esize)
        val high = (0x3F) and ((1 shl len) - 1).inv()
        return intArrayOf(0, rotation, s or high)
    }

    private fun rotateRight(value: Long, distance: Int, width: Int): Long {
        val d = distance % width
        val mask = if (width == 64) -1L else (1L shl width) - 1
        val v = value and mask
        if (d == 0) return v
        return ((v ushr d) or (v shl (width - d))) and mask
    }

    /**
     * `mov rd, #value` exactly as the reference assembler picks it: a `movz` when the value fits in
     * 16 bits, a `movn` when its complement does, a bitmask `orr` when it is one, and otherwise a
     * `movz`/`movk` run. The ordering matters for byte identity - see `UltraKernelShellcodeTest`.
     */
    fun encodeMovImm(rd: Int, value: Long, sf: Int = 1): IntArray {
        val width = if (sf != 0) 64 else 32
        val mask = if (width == 64) -1L else (1L shl 32) - 1
        val v = value and mask
        // Every "does this fit in 16 bits" test below is an unsigned test: `v` routinely has bit 63
        // set (a negative displacement), and a signed `<= 0xFFFF` would report *every* negative
        // value as fitting, silently turning a two word `movz`/`movk` run into a four word `movn`.
        if ((v and 0xFFFFL) == v) return intArrayOf(movz(rd, v.toInt(), 0, sf))
        val fields = bitmaskFields(v, sf)
        if (fields != null) {
            return intArrayOf(
                (if (sf != 0) 0xB2000000.toInt() else 0x32000000) or
                    (fields[0] shl 22) or (fields[1] shl 16) or (fields[2] shl 10) or
                    (31 shl 5) or (rd and 0x1F),
            )
        }
        val inverse = v.inv() and mask
        if ((inverse and 0xFFFFL) == inverse) {
            val words = ArrayList<Int>(4)
            words += movn(rd, inverse.toInt(), 0, sf)
            var shift = 16
            while (shift < width) {
                val part = ((inverse ushr shift) and 0xFFFF).toInt()
                if (part != 0) words += movk(rd, part, shift, sf)
                shift += 16
            }
            return words.toIntArray()
        }
        return encodeImm64(rd, v, sf)
    }

    // -----------------------------------------------------------------------------------------
    // data processing
    // -----------------------------------------------------------------------------------------

    fun addImm(rd: Int, rn: Int, imm: Int, sf: Int = 1, setFlags: Boolean = false, sub: Boolean = false): Int {
        require(imm in 0..0xFFF) { "IMM12_RANGE:$imm" }
        return (if (sf != 0) 0x91000000.toInt() else 0x11000000) or
            (if (sub) 0x40000000 else 0) or
            (if (setFlags) 0x20000000 else 0) or
            (imm shl 10) or ((rn and 0x1F) shl 5) or (rd and 0x1F)
    }

    fun cmpImm(rn: Int, imm: Int, sf: Int = 1): Int =
        addImm(31, rn, imm, sf = sf, setFlags = true, sub = true)

    fun addReg(rd: Int, rn: Int, rm: Int, sf: Int = 1, setFlags: Boolean = false, sub: Boolean = false): Int =
        (if (sf != 0) 0x8B000000.toInt() else 0x0B000000) or
            (if (sub) 0x40000000 else 0) or
            (if (setFlags) 0x20000000 else 0) or
            ((rm and 0x1F) shl 16) or ((rn and 0x1F) shl 5) or (rd and 0x1F)

    fun cmpReg(rn: Int, rm: Int, sf: Int = 1): Int =
        addReg(31, rn, rm, sf = sf, setFlags = true, sub = true)

    fun orrReg(rd: Int, rn: Int, rm: Int, sf: Int = 1): Int =
        (if (sf != 0) 0xAA000000.toInt() else 0x2A000000) or
            ((rm and 0x1F) shl 16) or ((rn and 0x1F) shl 5) or (rd and 0x1F)

    fun movReg(rd: Int, rm: Int, sf: Int = 1): Int = orrReg(rd, 31, rm, sf)

    fun csel(rd: Int, rn: Int, rm: Int, cond: Int, sf: Int = 1): Int =
        (if (sf != 0) 0x9A800000.toInt() else 0x1A800000) or
            ((rm and 0x1F) shl 16) or ((cond and 0xF) shl 12) or
            ((rn and 0x1F) shl 5) or (rd and 0x1F)

    fun ubfx(rd: Int, rn: Int, lsb: Int, width: Int, sf: Int = 1): Int {
        require(lsb + width <= (if (sf != 0) 64 else 32)) { "UBFX_RANGE:$lsb+$width" }
        val imms = (lsb + width - 1) and 0x3F
        return (if (sf != 0) 0xD3400000.toInt() else 0x53000000) or
            ((if (sf != 0) 1 else 0) shl 22) or ((lsb and 0x3F) shl 16) or
            (imms shl 10) or ((rn and 0x1F) shl 5) or (rd and 0x1F)
    }

    fun lsrv(rd: Int, rn: Int, rm: Int, sf: Int = 1): Int =
        (if (sf != 0) 0x9AC02400.toInt() else 0x1AC02400) or
            ((rm and 0x1F) shl 16) or ((rn and 0x1F) shl 5) or (rd and 0x1F)

    fun lslv(rd: Int, rn: Int, rm: Int, sf: Int = 1): Int =
        (if (sf != 0) 0x9AC02000.toInt() else 0x1AC02000) or
            ((rm and 0x1F) shl 16) or ((rn and 0x1F) shl 5) or (rd and 0x1F)

    // -----------------------------------------------------------------------------------------
    // load / store
    // -----------------------------------------------------------------------------------------

    private fun scaleOf(size: Int): Int = when (size) {
        1 -> 0
        2 -> 1
        4 -> 2
        8 -> 3
        else -> throw IllegalArgumentException("LD_SIZE_UNSUPPORTED:$size")
    }

    /** `opc` field for a mnemonic/size pair. */
    private fun opcOf(mnemonic: String, size: Int): Int = when (mnemonic) {
        "ldr", "ldrb", "ldrh" -> 1
        "ldrsw" -> 2
        "ldrsb", "ldrsh" -> 3
        "str", "strb", "strh" -> 0
        else -> throw IllegalArgumentException("LD_OP_UNSUPPORTED:$mnemonic")
    }

    /** Unsigned-offset form; [offset] must be a multiple of the access size and fit 12 scaled bits. */
    fun ldImm(mnemonic: String, rt: Int, rn: Int, offset: Int, size: Int): Int {
        val scale = scaleOf(size)
        require(offset >= 0 && offset % size == 0 && offset / size < 0x1000) { "LD_OFFSET:$offset" }
        return 0x39000000 or (scale shl 30) or (opcOf(mnemonic, size) shl 22) or
            ((offset / size) shl 10) or ((rn and 0x1F) shl 5) or (rt and 0x1F)
    }

    /** Unscaled ([mode] 0), post-index (1) or pre-index (3) form. */
    fun ldImm9(mnemonic: String, rt: Int, rn: Int, imm9: Int, size: Int, mode: Int): Int {
        require(imm9 in -256..255) { "LD_IMM9_RANGE:$imm9" }
        return 0x38000000 or (scaleOf(size) shl 30) or (opcOf(mnemonic, size) shl 22) or
            ((imm9 and 0x1FF) shl 12) or ((mode and 3) shl 10) or
            ((rn and 0x1F) shl 5) or (rt and 0x1F)
    }

    /**
     * The form the reference assembler picks for `mnemonic rt, [rn, #offset]`: the unsigned-offset
     * encoding when the displacement is legal for it, the unscaled encoding otherwise. Both are
     * valid AArch64; picking the same one the reference picked is what keeps k22 byte identical.
     */
    fun ldAuto(mnemonic: String, rt: Int, rn: Int, offset: Int, size: Int): Int =
        if (offset >= 0 && offset % size == 0 && offset / size < 0x1000) {
            ldImm(mnemonic, rt, rn, offset, size)
        } else {
            ldImm9(mnemonic, rt, rn, offset, size, 0)
        }

    /** Register-offset form (`[xn, xm]`). */
    fun ldReg(mnemonic: String, rt: Int, rn: Int, rm: Int, size: Int, option: Int = 3): Int =
        0x38200800 or (scaleOf(size) shl 30) or (opcOf(mnemonic, size) shl 22) or
            ((rm and 0x1F) shl 16) or ((option and 7) shl 13) or
            ((rn and 0x1F) shl 5) or (rt and 0x1F)

    /** `ldp`/`stp`; [mode] is the raw 2 bit field (1 post-index, 2 signed offset, 3 pre-index). */
    fun pair(mnemonic: String, rt: Int, rt2: Int, rn: Int, imm7: Int, wide: Boolean, mode: Int): Int {
        require(imm7 in -64..63) { "PAIR_IMM7_RANGE:$imm7" }
        val base = if (wide) 0xA8000000.toInt() else 0x28000000
        val load = if (mnemonic == "ldp") 1 else 0
        return base or ((mode and 3) shl 23) or (load shl 22) or ((imm7 and 0x7F) shl 15) or
            ((rt2 and 0x1F) shl 10) or ((rn and 0x1F) shl 5) or (rt and 0x1F)
    }

    fun stpPre(rt: Int, rt2: Int, rn: Int, imm7: Int, wide: Boolean = true): Int =
        pair("stp", rt, rt2, rn, imm7, wide, 3)

    fun ldpPost(rt: Int, rt2: Int, rn: Int, imm7: Int, wide: Boolean = true): Int =
        pair("ldp", rt, rt2, rn, imm7, wide, 1)

    // -----------------------------------------------------------------------------------------
    // system
    // -----------------------------------------------------------------------------------------

    fun mrs(rt: Int, base: Int): Int = base or (rt and 0x1F)

    /** `sys #op1, c<n>, c<m>, #op2, <rt>` - the space `dc`/`ic` maintenance lives in. */
    fun sysInstr(op1: Int, crn: Int, crm: Int, op2: Int, rt: Int = 31): Int =
        0xD5080000.toInt() or (op1 shl 16) or (crn shl 12) or (crm shl 8) or (op2 shl 5) or (rt and 0x1F)

    /** `dc cvac, x<rt>` - clean a data cache line to the point of coherency. */
    fun dcCvac(rt: Int): Int = sysInstr(3, 7, 10, 1, rt)

    /** `ic iallu` - invalidate the whole instruction cache. */
    fun icIallu(): Int = sysInstr(0, 7, 5, 0, 31)

    /** `ic ialluis` - invalidate instruction caches throughout the inner-shareable domain. */
    fun icIalluis(): Int = sysInstr(0, 7, 1, 0, 31)

    /** Invalidate this kernel VA at all translation levels on inner-shareable PEs. */
    fun tlbiVaae1is(rt: Int): Int = sysInstr(0, 8, 3, 3, rt)

    /**
     * `tlbi vmalle1is` - invalidate every EL1&0 stage-1 translation (all ASIDs, all levels) on
     * inner-shareable PEs. Rt is the "no register" encoding; the whole domain is invalidated.
     *
     * The executor publishes a page it has just moved NX -> RO/X. On the 6.6.89 target a by-VA
     * `tlbi vaae1is` alone left the first fetch still hitting the pre-publication non-executable
     * translation (ESR 0x8600000f after BLR), so the publication step uses this full-domain form.
     */
    fun tlbiVmalle1is(): Int = sysInstr(0, 8, 3, 0, 31)

    fun blr(rm: Int): Int = 0xD63F0000.toInt() or ((rm and 0x1F) shl 5)

    // -----------------------------------------------------------------------------------------
    // little-endian access
    // -----------------------------------------------------------------------------------------

    /** Little-endian unsigned 32 bit word at [at]. */
    fun le32(data: ByteArray, at: Int): Long {
        require(at >= 0 && at + 4 <= data.size) { "LE32_RANGE:$at" }
        return (data[at].toLong() and 0xFF) or
            ((data[at + 1].toLong() and 0xFF) shl 8) or
            ((data[at + 2].toLong() and 0xFF) shl 16) or
            ((data[at + 3].toLong() and 0xFF) shl 24)
    }

    /** Little-endian unsigned 32 bit word at [at] as an `Int` (bit pattern preserved). */
    fun le32Bits(data: ByteArray, at: Int): Int = le32(data, at).toInt()

    fun writeLe32(target: ByteArray, at: Int, value: Long) {
        require(at >= 0 && at + 4 <= target.size) { "LE32_WRITE_RANGE:$at" }
        target[at] = value.toByte()
        target[at + 1] = (value ushr 8).toByte()
        target[at + 2] = (value ushr 16).toByte()
        target[at + 3] = (value ushr 24).toByte()
    }

    fun writeLe16(target: ByteArray, at: Int, value: Int) {
        require(at >= 0 && at + 2 <= target.size) { "LE16_WRITE_RANGE:$at" }
        target[at] = value.toByte()
        target[at + 1] = (value ushr 8).toByte()
    }

    /** Align [value] up to the next multiple of [alignment] (Python's `-value % n`). */
    fun alignUp(value: Int, alignment: Int): Int {
        val remainder = value % alignment
        return value + if (remainder == 0) 0 else alignment - remainder
    }
}
