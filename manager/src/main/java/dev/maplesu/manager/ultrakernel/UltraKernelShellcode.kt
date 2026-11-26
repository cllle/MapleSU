package dev.maplesu.manager.ultrakernel

/**
 * Ultra code bodies emitted for the target kernel instead of carried as machine code.
 *
 * Until this file existed the recipe held nine frozen blobs: the compiled output of the reference
 * patch tool for `6.6.89-android15-8-g7e1f3c083cc6-abogki467167594-4k`. Every address inside them
 * was relocated, but the instruction sequences themselves were that one build's, so a plan for any
 * other kernel was refused (`ULTRA_KERNEL_GENERATE_SITE_COVERAGE`, `3/18` on 6.1.25).
 *
 * Here each body is a *program*: it reads its site offset, the resolved symbol offsets and the
 * derived `task_struct` field offsets, and assembles itself with [UltraKernelAsm]. Consequences that
 * matter:
 *
 *  - branch, `adr` and absolute-address fields are computed, so they cannot be stale;
 *  - a `movz`/`movk` run grows or shrinks with the displacement it encodes, and every later
 *    instruction follows, because layout is decided after the value is known (this is what removed
 *    `ULTRA_KERNEL_GENERATE_SITE: IMM64_WORD_COUNT`);
 *  - the `task_struct` displacements are arguments, not constants, so the same body fits 6.1/6.12.
 *
 * `UltraKernelShellcodeTest` checks the current body layout, control flow and ABI contract.
 *
 * Bodies are named after the record they render, matching `UltraKernelRecipeK22`'s `code` field.
 */
internal data class UltraKernelFields(
    /** `task_struct.cred` byte offset. */
    val cred: Int? = null,
    /** task_struct.comm is independently located, not inferred from cred. */
    val comm: Int? = null,
    /** `task_struct.seccomp.mode` byte offset, when a body needs it. */
    val seccomp: Int? = null,
    /** Reply channel is the target's adjacent SUID/SGID words, not a fixed cred offset. */
    val credSuid: Int? = null,
    val credEuid: Int? = null,
    /** Stack allocation replayed after a PAC/SCS instruction retained at its native address. */
    val execvePrologue: Int? = null,
    /** Native page size decoded from this target's arm64 Image flags. */
    val pageShift: Int = 12,
    /** An installed SDK identity whose locator is relocated before execution. */
    val legacyMetadataDisplacement: Int? = null,
    val actualMetadataDisplacement: Int? = null,
    /** The same gate's independent response body is present in its allocated cave. */
    val requestV2: Boolean = false,
) {
    fun requireOffset(value: Int?, name: String): Int = value ?: throw UltraKernelGenerateException(
        UltraKernelGenerateCodes.STRUCT_OFFSET, "STRUCT_OFFSET_NOT_FOUND:$name")
}

/** A body that could not be assembled; the caller maps this to a `ULTRA_KERNEL_GENERATE_*` code. */
internal class UltraKernelBodyException(val detail: String) : IllegalStateException(detail)

/** An assembled body: its bytes, and which word offsets inside them are data rather than code. */
internal class UltraKernelBody(val bytes: ByteArray, val dataOffsets: Set<Int>)

/**
 * Two-pass, label-based AArch64 assembler over a word list.
 *
 * [base] is the file offset word 0 lands at, i.e. the address space the spec works in. Forward
 * references are recorded as fixups and patched in [finish], so a body's layout is decided by the
 * code that emits it, never by a hand-counted byte offset.
 */
internal class UltraKernelAsm(val base: Int) {

    private class Fixup(val index: Int, val label: String, val kind: String, val arg: Int)

    private val words = ArrayList<Int>()
    private val labels = LinkedHashMap<String, Int>()
    private val fixups = ArrayList<Fixup>()
    private val data = HashSet<Int>()

    /** Offsets (from [base]) of words that are data, not instructions. */
    val dataOffsets: Set<Int> get() = data.toSet()

    fun offset(): Int = words.size * 4

    fun here(): Int = base + offset()

    fun emit(word: Int) {
        words += word
    }

    fun emit(words: IntArray) {
        words.forEach { this.words += it }
    }

    /**
     * Append 4 byte aligned payload bytes (the `/app_process64` literal).
     *
     * Data words are excluded from instruction validation, and that exclusion is explicit rather
     * than inferred from "it failed to decode" - a validator that silently accepts whatever it
     * cannot classify proves nothing.
     */
    fun embed(bytes: ByteArray) {
        require(bytes.size % 4 == 0) { "EMBED_NOT_ALIGNED:${bytes.size}" }
        var index = 0
        while (index < bytes.size) {
            data += offset()
            emit(UltraKernelA64.le32Bits(bytes, index))
            index += 4
        }
    }

    fun mark(name: String) {
        require(labels.put(name, words.size) == null) { "LABEL_REDEFINED:$name" }
    }

    private fun record(label: String, kind: String, arg: Int = 0) {
        fixups += Fixup(words.size, label, kind, arg)
        words += 0
    }

    fun b(label: String) = record(label, "b")

    fun bl(label: String) = record(label, "bl")

    fun bCond(cond: Int, label: String) = record(label, "bcond", cond)

    fun cbz(rt: Int, label: String, sf: Boolean = true) = record(label, "cbz", (rt and 0x1F) or (if (sf) 0x20 else 0))

    fun cbnz(rt: Int, label: String, sf: Boolean = true) =
        record(label, "cbnz", (rt and 0x1F) or (if (sf) 0x20 else 0))

    fun adr(rd: Int, label: String) = record(label, "adr", rd)

    /** `adr rd, .` - the reference's own-PC anchor for a following `movz`/`movk` displacement. */
    fun adrSelf(rd: Int) = emit(UltraKernelA64.encodeAdr(here().toLong(), here().toLong(), rd))

    fun bAbs(target: Int) = emit(UltraKernelA64.encodeB(here().toLong(), target.toLong()))

    fun bCondAbs(cond: Int, target: Int) = emit(UltraKernelA64.encodeBCond(cond, here().toLong(), target.toLong()))

    fun blAbs(target: Int) = emit(UltraKernelA64.encodeBl(here().toLong(), target.toLong()))

    fun blr(rm: Int) = emit(UltraKernelA64.blr(rm))

    fun finish(): ByteArray {
        for (fixup in fixups) {
            val position = labels[fixup.label]
                ?: throw UltraKernelBodyException("LABEL_UNBOUND:${fixup.label}")
            val here = base.toLong() + fixup.index * 4L
            val target = base.toLong() + position * 4L
            words[fixup.index] = when (fixup.kind) {
                "b" -> UltraKernelA64.encodeB(here, target)
                "bl" -> UltraKernelA64.encodeBl(here, target)
                "bcond" -> UltraKernelA64.encodeBCond(fixup.arg, here, target)
                "cbz" -> UltraKernelA64.encodeCbz(fixup.arg and 0x1F, here, target,
                    sf = (fixup.arg and 0x20) != 0, nonZero = false)
                "cbnz" -> UltraKernelA64.encodeCbz(fixup.arg and 0x1F, here, target,
                    sf = (fixup.arg and 0x20) != 0, nonZero = true)
                "adr" -> UltraKernelA64.encodeAdr(here, target, fixup.arg)
                else -> throw UltraKernelBodyException("FIXUP_KIND_UNKNOWN:${fixup.kind}")
            }
        }
        val out = ByteArray(words.size * 4)
        words.forEachIndexed { index, word -> UltraKernelA64.writeLe32(out, index * 4, word.toLong() and 0xFFFFFFFFL) }
        return out
    }
}

/**
 * An independent classification of the encodings [UltraKernelAsm] may produce.
 *
 * Deliberately *not* derived from the emitter: it is a separate set of masked patterns, so a body
 * that contains a word the emitter should never have produced fails validation even though it
 * "assembled". Data words (`/app_process64`) are excluded by the caller through
 * [UltraKernelAsm.dataOffsets], never by "unknown means data".
 */
internal object UltraKernelA64Verify {

    fun isInstruction(word: Int): Boolean {
        // Widened to Long so the masks that touch bit 31 stay unsigned and readable.
        val w = word.toLong() and 0xFFFFFFFFL
        fun masked(mask: Long, value: Long): Boolean = (w and mask) == value
        return masked(0x7C000000, 0x14000000) ||      // b / bl
            masked(0xFF000010, 0x54000000) ||         // b.cond
            masked(0x7E000000, 0x34000000) ||         // cbz / cbnz
            masked(0x9F000000, 0x10000000) ||         // adr
            masked(0x9F000000, 0x90000000) ||         // adrp
            masked(0x7F800000, 0x12800000) ||         // movn
            masked(0x7F800000, 0x52800000) ||         // movz
            masked(0x7F800000, 0x72800000) ||         // movk
            masked(0x1F800000, 0x12000000) ||         // logical immediate
            masked(0x1F000000, 0x11000000) ||         // add/sub immediate
            masked(0x1F200000, 0x0B000000) ||         // add/sub shifted register
            masked(0x1F000000, 0x0A000000) ||         // logical shifted register
            masked(0x1FE00000, 0x1A800000) ||         // csel
            masked(0x1F800000, 0x13000000) ||         // ubfm / sbfm
            masked(0x1FE00000, 0x1AC00000) ||         // lslv / lsrv / asrv
            masked(0x3B000000, 0x39000000) ||         // ld/st unsigned offset
            masked(0x3B200C00, 0x38200800) ||         // ld/st register offset
            masked(0x3B200000, 0x38000000) ||         // ld/st imm9
            masked(0x3A000000, 0x28000000) ||         // ldp / stp
            masked(0xFFFFFC1F, 0xD61F0000) ||         // br
            masked(0xFFFFFC1F, 0xD63F0000) ||         // blr
            masked(0xFFFFFC1F, 0xD65F0000) ||         // ret
            masked(0xFFF00000, 0xD5300000) ||         // mrs
            masked(0xFFF00000, 0xD5100000) ||         // msr
            masked(0xFFF80000, 0xD5080000) ||         // sys (dc / ic)
            masked(0xFFFFF09F, 0xD503309F) ||         // dsb / dmb / isb
            masked(0xFFFFF01F, 0xD503201F)            // hints, pac
    }

    /** Every non-data word must classify; returns the offending offsets. */
    fun invalidWords(body: ByteArray, dataOffsets: Set<Int>): List<Int> {
        val bad = ArrayList<Int>()
        var position = 0
        while (position + 4 <= body.size) {
            if (position !in dataOffsets && !isInstruction(UltraKernelA64.le32Bits(body, position))) {
                bad += position
            }
            position += 4
        }
        if (body.size % 4 != 0) bad += body.size
        return bad
    }
}

/** Object extents from this target's named cache initialization, not allocation slot sizes. */
internal object UltraKernelOwnerSizes {
    private data class Constant(val value: Long, val address: Boolean = false)

    private fun bounds(symbols: UltraKernelSymbols, kernel: ByteArray, name: String): IntRange? {
        val start = symbols.offsetOf(name) ?: return null
        val end = symbols.nextOffset(start) ?: return null
        if (start < 0 || start % 4 != 0 || end % 4 != 0 || end <= start ||
            end.toLong() - start > 4096 || end > kernel.size) return null
        return start until end
    }

    private fun branch(at: Int, word: Int): Long = at.toLong() +
        UltraKernelA64.signExtend((word and 0x03ffffff).toLong(), 26) * 4

    private fun address(at: Int, word: Int): Long {
        val immediate = (((word ushr 5) and 0x7ffff).toLong() shl 2) or ((word ushr 29) and 3).toLong()
        return if (UltraKernelA64.isAdrp(word)) (at.toLong() and -4096L) +
            (UltraKernelA64.signExtend(immediate, 21) shl 12)
        else at.toLong() + UltraKernelA64.signExtend(immediate, 21)
    }

    private fun move(word: Int) = word and 0x7fe0ffe0 == 0x2a0003e0
    private fun movz(word: Int) = word and 0x7f800000 == 0x52800000
    private fun immediate(word: Int): Long? {
        val shift = ((word ushr 21) and 3) * 16
        if (word >= 0 && shift >= 32) return null
        return ((word ushr 5) and 0xffff).toLong() shl shift
    }

    private fun cacheArguments(kernel: ByteArray, start: Int, call: Int): Array<Constant?>? {
        val values = arrayOfNulls<Constant>(32)
        for (at in start until call step 4) {
            val word = UltraKernelA64.le32Bits(kernel, at)
            val rd = word and 31
            val rn = (word ushr 5) and 31
            val mask = if (word < 0) -1L else 0xffffffffL
            when {
                UltraKernelA64.isAdr(word) || UltraKernelA64.isAdrp(word) -> values[rd] = Constant(address(at, word), true)
                movz(word) -> values[rd] = Constant(immediate(word) ?: return null)
                word and 0x7f800000 == 0x72800000 -> {
                    val old = values[rd]
                    val value = immediate(word) ?: return null
                    val shift = ((word ushr 21) and 3) * 16
                    values[rd] = old?.takeIf { !it.address }?.let {
                        Constant(((it.value and (0xffffL shl shift).inv()) or value) and mask) }
                }
                move(word) -> {
                    val source = (word ushr 16) and 31
                    // A file offset models a full kernel VA; W truncation proves neither its address nor numeric value.
                    values[rd] = if (source == 31) Constant(0) else values[source]?.takeIf { word < 0 || !it.address }
                        ?.let { Constant(it.value and mask, it.address) }
                }
                word and 0x7f800000 == 0x11000000 -> {
                    val value = ((word ushr 10) and 0xfff).toLong() shl (if (word and (1 shl 22) != 0) 12 else 0)
                    values[rd] = if (rn == 31) null else values[rn]?.takeIf { word < 0 || !it.address }
                        ?.let { Constant((it.value + value) and mask, it.address) }
                }
                UltraKernelA64.isBl(word) -> { for (index in 0..18) values[index] = null; values[30] = null }
                word == UltraKernelA64.NOP || word == UltraKernelA64.PACIA1716 ||
                    word == 0xd50323bf.toInt() || word == 0xd503245f.toInt() -> values[30] = null
                word and 0x3e000000 == 0x28000000 -> {
                    if (rn != 31) return null
                    if (word and (1 shl 22) != 0) { values[rd] = null; values[(word ushr 10) and 31] = null }
                    if (((word ushr 23) and 3) in listOf(1, 3)) values[rn] = null
                }
                word and 0x3f000000 == 0x39000000 -> {
                    if (word and (3 shl 22) == 0 && rn != 31) return null
                    if (word and (3 shl 22) != 0) values[rd] = null
                }
                word and 0x3f200000 == 0x38000000 -> {
                    if (word and (3 shl 22) == 0 && rn != 31 && (rn != 18 || values[rn] != null)) return null
                    if (word and (3 shl 22) != 0) values[rd] = null
                    if (((word ushr 10) and 3) in listOf(1, 3)) values[rn] = null
                }
                else -> return null
            }
            values[31] = null // ZR discards writes; SP is never a proved ordinary-register constant.
        }
        return values
    }

    /** Unknown or contradictory evidence leaves the existing nullable owner contract intact. */
    fun size(symbols: UltraKernelSymbols, kernel: ByteArray, owner: String): Int? {
        if (owner == "group_info") return UltraKernelStructOffsets.groupOwnerSize(symbols, kernel)
        val sharedCache = when (owner) {
            "fs_struct" -> "fs_cachep"
            "files_struct" -> "files_cachep"
            "mm_struct" -> "mm_cachep"
            "signal_struct" -> "signal_cachep"
            "sighand_struct" -> "sighand_cachep"
            else -> null
        }
        if (sharedCache != null) return publishedCacheSize(symbols, kernel, owner, sharedCache)
        // LTO may inline dcache_init into its caller; bind the same named cache publication.
        if (owner == "dentry" && symbols.offsetOf("dcache_init") == null)
            return publishedCacheSize(symbols, kernel, owner, "dentry_cache", "vfs_caches_init")
        val initializer: String
        val cache: String
        val copier: String
        val publishedSymbol: String
        when (owner) {
            "task_struct" -> { initializer = "fork_init"; cache = "task_struct"; copier = "arch_dup_task_struct"; publishedSymbol = "task_struct_cachep" }
            "cred" -> { initializer = "cred_init"; cache = "cred_jar"; copier = "prepare_creds"; publishedSymbol = "cred_jar" }
            "file" -> { initializer = "files_init"; cache = "filp"; copier = ""; publishedSymbol = "filp_cachep" }
            "inode" -> { initializer = "inode_init"; cache = "inode_cache"; copier = ""; publishedSymbol = "inode_cachep" }
            "dentry" -> { initializer = "dcache_init"; cache = "dentry"; copier = ""; publishedSymbol = "dentry_cache" }
            "nsproxy" -> { initializer = "nsproxy_cache_init"; cache = "nsproxy"; copier = ""; publishedSymbol = "nsproxy_cachep" }
            "mount" -> { initializer = "mnt_init"; cache = "mnt_cache"; copier = ""; publishedSymbol = "mnt_cache" }
            else -> return null
        }
        val expectedPublication = symbols.memoryOffsetOf(publishedSymbol)
        if (owner !in setOf("task_struct", "cred") && expectedPublication == null) return null
        val range = bounds(symbols, kernel, initializer) ?: return null
        val allocators = listOf("kmem_cache_create_usercopy", "kmem_cache_create")
            .mapNotNull(symbols::offsetOf).map(Int::toLong).toSet()
        val calls = range.step(4).filter { at ->
            val word = UltraKernelA64.le32Bits(kernel, at)
            word and 0x7c000000 == 0x14000000 && branch(at, word) in allocators
        }
        val call = calls.singleOrNull() ?: return null
        if (!UltraKernelA64.isBl(UltraKernelA64.le32Bits(kernel, call))) return null
        // A later edge into the argument prefix could execute it with different state.
        for (at in call + 4..range.last step 4) {
            val word = UltraKernelA64.le32Bits(kernel, at)
            val target = when {
                word and 0x7c000000 == 0x14000000 -> branch(at, word)
                word and 0xff000010.toInt() == 0x54000000 || word and 0x7e000000 == 0x34000000 ->
                    at.toLong() + UltraKernelA64.signExtend(((word ushr 5) and 0x7ffff).toLong(), 19) * 4
                word and 0x7e000000 == 0x36000000 ->
                    at.toLong() + UltraKernelA64.signExtend(((word ushr 5) and 0x3fff).toLong(), 14) * 4
                else -> null
            }
            if (target != null && target in range.first.toLong()..call.toLong()) return null
        }
        val values = cacheArguments(kernel, range.first, call) ?: return null
        val name = values[0]?.takeIf { it.address }?.value ?: return null
        val size = values[1]?.takeIf { !it.address && it.value in 1..Int.MAX_VALUE.toLong() }?.value?.toInt() ?: return null
        val expected = cache.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        if (name < 0 || name + expected.size > kernel.size ||
            !kernel.copyOfRange(name.toInt(), name.toInt() + expected.size).contentEquals(expected)) return null
        // The observed initializers publish the returned cache immediately via ADRP/STR X0.
        if (call.toLong() + 12 > range.last.toLong() + 1) return null
        val page = UltraKernelA64.le32Bits(kernel, call + 4)
        val store = UltraKernelA64.le32Bits(kernel, call + 8)
        if (!UltraKernelA64.isAdrp(page) || page and 31 in listOf(0, 31) ||
            store and 0xffc0001f.toInt() != 0xf9000000.toInt() || (store ushr 5) and 31 != page and 31) return null
        val published = address(call + 4, page) + ((store ushr 10) and 0xfff) * 8L
        if (published < 0 || expectedPublication
                ?.let { it.toLong() != published } == true) return null
        val copied = if (copier.isEmpty()) null else copySize(symbols, kernel, copier)
        return size.takeIf { copied == null || copied == it }
    }

    /** Each result binds a real create-call size and name to the exact published cache. */
    private fun publishedCacheSize(symbols: UltraKernelSymbols, kernel: ByteArray,
        owner: String, publishedName: String, initializer: String = "proc_caches_init"): Int? {
        val published = symbols.memoryOffsetOf(publishedName) ?: return null
        val range = bounds(symbols, kernel, initializer) ?: return null
        val targets = listOf("kmem_cache_create", "kmem_cache_create_usercopy").flatMap { name ->
            val bodies = symbols.offsetsOf(name)
            bodies + symbols.offsetsOf("$name.cfi_jt").filter { at ->
                at >= 0 && at.toLong() + 4 <= kernel.size && UltraKernelA64.le32Bits(kernel, at).let { word ->
                    word and 0xfc000000.toInt() == 0x14000000 && branch(at, word).toInt() in bodies }
            }
        }.toSet()
        val calls = range.step(4).filter { at -> UltraKernelA64.le32Bits(kernel, at).let { word ->
            UltraKernelA64.isBl(word) && branch(at, word).toInt() in targets } }
        fun publication(call: Int): Long? {
            if (call.toLong() + 12 > range.last.toLong() + 1) return null
            val page = UltraKernelA64.le32Bits(kernel, call + 4)
            val store = UltraKernelA64.le32Bits(kernel, call + 8)
            if (!UltraKernelA64.isAdrp(page) || page and 31 in setOf(0, 31) ||
                store and 0xffc0001f.toInt() != 0xf9000000.toInt() ||
                (store ushr 5) and 31 != page and 31) return null
            return address(call + 4, page) + ((store ushr 10) and 0xfff) * 8L
        }
        val candidates = linkedSetOf<Int>()
        for ((index, call) in calls.withIndex()) {
            if (publication(call) != published.toLong()) continue
            val prefix = if (index == 0) range.first else {
                if (publication(calls[index - 1]) == null) return null
                calls[index - 1] + 12
            }
            // No later edge may re-enter this argument prefix with another register state.
            for (at in call + 4..range.last step 4) {
                val word = UltraKernelA64.le32Bits(kernel, at)
                val target = when {
                    word and 0x7c000000 == 0x14000000 -> branch(at, word)
                    word and 0xff000010.toInt() == 0x54000000 || word and 0x7e000000 == 0x34000000 ->
                        at.toLong() + UltraKernelA64.signExtend(((word ushr 5) and 0x7ffff).toLong(), 19) * 4
                    word and 0x7e000000 == 0x36000000 -> at.toLong() +
                        UltraKernelA64.signExtend(((word ushr 5) and 0x3fff).toLong(), 14) * 4
                    else -> null
                }
                if (target != null && target in prefix.toLong()..call.toLong()) return null
            }
            val values = cacheArguments(kernel, prefix, call) ?: return null
            val size = values[1]?.takeIf { !it.address && it.value in 1..Int.MAX_VALUE.toLong() }?.value?.toInt()
                ?: return null
            val name = values[0]?.takeIf { it.address }?.value ?: return null
            val names = setOf(owner, owner.substringBefore("_struct") + "_cache")
            if (names.none { text -> (text.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)).let { bytes ->
                name >= 0 && name + bytes.size <= kernel.size &&
                    kernel.copyOfRange(name.toInt(), name.toInt() + bytes.size).contentEquals(bytes) } }) return null
            candidates += size
        }
        return candidates.singleOrNull()
    }

    /** Independent evidence only: a unique memcpy with a directly formed length. */
    private fun copySize(symbols: UltraKernelSymbols, kernel: ByteArray, name: String): Int? {
        val range = bounds(symbols, kernel, name) ?: return null
        val targets = listOf("memcpy", "__memcpy", "__pi_memcpy").mapNotNull(symbols::offsetOf).map(Int::toLong).toSet()
        val call = range.step(4).filter { at ->
            val word = UltraKernelA64.le32Bits(kernel, at)
            UltraKernelA64.isBl(word) && branch(at, word) in targets
        }.singleOrNull() ?: return null
        for (at in call - 4 downTo maxOf(range.first, call - 12) step 4) {
            val word = UltraKernelA64.le32Bits(kernel, at)
            if (movz(word) && word and 31 == 2) return immediate(word)?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
            if (!move(word) || word and 31 !in 0..1) return null
        }
        return null
    }
}

/** Target BTF layouts, with bounded accessor data flow for kernels without those types. */
internal object UltraKernelStructOffsets {
    const val CRED = "task_struct.cred"
    const val COMM = "task_struct.comm"
    // The frozen recipe key names the member historically written at seccomp's start.
    const val SECCOMP = "task_struct.seccomp"
    const val SECCOMP_MODE = "task_struct.seccomp.mode"
    const val SUID = "cred.suid"
    const val SGID = "cred.sgid"
    const val CRED_EUID = "cred.euid"
    const val CRED_UID = "cred.uid"
    const val CRED_GROUP_INFO = "cred.group_info"
    const val GROUP_USAGE = "group_info.usage"
    const val CRED_USAGE = "cred.usage"
    const val CRED_SECUREBITS = "cred.securebits"
    private val UID = listOf("__arm64_sys_getuid", "sys_getuid")
    private val EUID = listOf("__arm64_sys_geteuid", "sys_geteuid")
    private val GID = listOf("__arm64_sys_getgid", "sys_getgid")
    private val EGID = listOf("__arm64_sys_getegid", "sys_getegid")
    private val RESUID = listOf("__do_sys_getresuid", "__se_sys_getresuid", "sys_getresuid", "__arm64_sys_getresuid")
    private val RESGID = listOf("__do_sys_getresgid", "__se_sys_getresgid", "sys_getresgid", "__arm64_sys_getresgid")
    private val SECCOMP_GETTERS = listOf("prctl_get_seccomp", "seccomp_mode")
    private val COPIERS = listOf("strscpy_pad", "strscpy", "sized_strscpy", "strncpy", "memcpy", "memmove", "strlcpy")
    private val COMM_READERS = listOf("strlen", "__pi_strlen")
    private val COMM_LOCKS = listOf("_raw_spin_lock", "_raw_spin_unlock", "_raw_spin_lock_irqsave", "_raw_spin_unlock_irqrestore")

    private sealed interface Value
    private data class Task(val offset: Int = 0) : Value
    private data class Pointer(val field: Int, val offset: Int = 0) : Value
    private data class Scalar(val field: Int?, val member: Int, val width: Int, val signedToBytes: Int = 0) : Value
    private data class UserPointer(val argument: Int, val offset: Int = 0) : Value
    private data class RegisterFrame(val offset: Int = 0) : Value
    private data class Address(val offset: Int) : Value
    private data class ReadOnlyAddress(val offset: Int, val lower: Int, val upper: Int) : Value
    private data class StackAddress(val offset: Int = 0) : Value
    private data object IdOverflow : Value

    // Subject identities survive aliases and calls; member addresses are never inferred from neighbours.
    private data class ObjectValue(val kind: String, val identity: Int = 0, val offset: Int = 0) : Value
    private data class NumberValue(val number: Long, val width: Int) : Value
    private data class Count(val address: ObjectValue, val width: Int, val readAt: Int, val delta: Int = 0) : Value
    private data class Difference(val left: Value?, val right: Value?, val width: Int) : Value
    private data class AtomicStatus(val loaded: Count, val desired: Count) : Value
    private data class LockAttempt(val address: ObjectValue) : Value
    private data class LockStoreStatus(val address: ObjectValue, val readAt: Int) : Value
    private data class Equality(val left: Value, val right: Value, val equal: Boolean)
    private data class Change(val loaded: Count, val desired: Count)
    private data class ConditionalChange(val equality: Equality, val change: Change)
    private data class SubjectField(val offset: Int, val width: Int)
    private data class InstructionView(val words: Map<Int, Int>,
        val feature: Int? = null, val replacement: Boolean = false)
    private data class OutputCopy(val argument: Int, val destination: Int, val value: Scalar, val width: Int)
    private data class StringLength(val source: ObjectValue, val maximum: Int? = null) : Value
    private data class IndexedComm(val address: ObjectValue, val index: StringLength) : Value
    private data class CommCopy(val address: ObjectValue, val bytes: Int, val length: StringLength? = null)
    private data class SubjectTrace(
        val equalities: Set<Equality> = emptySet(),
        val zeroes: Map<Value, Boolean> = emptyMap(),
        val changes: Set<Change> = emptySet(),
        val conditionalChanges: Set<ConditionalChange> = emptySet(),
        val releases: Set<ObjectValue> = emptySet(),
        val initialised: Set<Pair<ObjectValue, Int>> = emptySet(),
        val groupFields: Set<Pair<SubjectField, SubjectField>> = emptySet(),
        val writes: Set<Pair<ObjectValue, Int>> = emptySet(),
        val alternativeWrites: List<Triple<ObjectValue, Long, Int>> = emptyList(),
        val outputCopies: Set<OutputCopy> = emptySet(),
        val inputs: Set<ObjectValue> = emptySet(),
        val heldLocks: Set<ObjectValue> = emptySet(),
        val usedLocks: Set<ObjectValue> = emptySet(),
        val pathCopies: Set<Pair<Int, Int>> = emptySet(),
        val pathReferences: Int = 0,
        val pathPartsReferenced: Set<Int> = emptySet(),
        val pathReleases: Set<Int> = emptySet(),
        val pathPartsReleased: Set<Int> = emptySet(),
        val pathDentriesReleased: Set<Int> = emptySet(),
        val fsSequence: List<Int> = emptyList(),
        val stackPointer: Int = 0,
        val stackValues: Map<SubjectField, Value> = emptyMap(),
        val positionStores: Set<Int> = emptySet(),
        val versionClears: Set<Int> = emptySet(),
        val sizeStores: Set<Int> = emptySet(),
        val truncatedSize: Int? = null,
        val attributeInodes: Set<Int> = emptySet(),
        val lockInodes: Set<Int> = emptySet(),
        val allocationSizes: Map<ObjectValue, Int> = emptyMap(),
        val commCopies: Set<CommCopy> = emptySet(),
        val commTerminated: Set<CommCopy> = emptySet(),
    )
    private enum class Subject { GROUPS, ALLOCATE_GROUPS, ALLOCATE_EMPTY_GROUPS, GET_CRED, PUT_CRED, RCU_FORWARD, ALTERNATIVE, SECUREBITS, CAPGET, EXIT_FILES, CAPABLE, SET_FS, EXIT_FS, GET_MM, MNT_PUT, SET_POSITION, TRUNCATE_SIZE, SIMPLE_GETATTR, POSIX_FILE_LOCK, COMM_LOCATION, TRACE_LOCATION }

    fun resolve(symbols: UltraKernelSymbols, kernel: ByteArray, needed: Set<String>,
                overrides: Map<String, Int>,
                btf: UltraKernelLayout? = UltraKernelBtf.layout(kernel, symbols, needed), strict: Boolean = false): Map<String, Int> {
        return needed.sorted().associateWith { name ->
            overrides[name] ?: if (btf != null) {
                btf.offset(if (name == SECCOMP) SECCOMP_MODE else name)
                    ?: error(name, "STRUCT_OFFSET_NOT_FOUND")
            } else when (name) {
                CRED -> unique(CRED, getters(symbols, kernel, UID + EUID + GID + EGID, strict)
                    .filter { it.width == 4 }.mapNotNull { it.field }
                    .filter { it in 8..0xfff8 && it % 8 == 0 })
                COMM -> comm(symbols, kernel, strict)
                SECCOMP, SECCOMP_MODE -> unique(SECCOMP_MODE,
                    getters(symbols, kernel, SECCOMP_GETTERS, strict).filter { it.field == null }
                        .map { it.member })
                else -> error(name, "STRUCT_RULE_UNKNOWN")
            } ?: error(name, "STRUCT_OFFSET_NOT_FOUND")
        }
    }
    fun describe(symbols: UltraKernelSymbols, kernel: ByteArray, needed: Set<String>,
        overrides: Map<String, Int>, credentialMembers: Set<String>,
        btf: UltraKernelLayout? = UltraKernelBtf.layout(kernel, symbols, needed + credentialMembers), strict: Boolean = false): UltraKernelLayout {
        val fields = btf?.fields?.toMutableMap() ?: linkedMapOf()
        val ownerSizes = if (btf == null) (needed + credentialMembers).map { it.substringBefore('.') }
            .distinct().associateWith { UltraKernelOwnerSizes.size(symbols, kernel, it) } else emptyMap()
        val offsets = resolve(symbols, kernel, needed, overrides, btf, strict)
        for ((name, offset) in offsets) {
            val key = if (name == SECCOMP) SECCOMP_MODE else name
            val target = btf?.fields?.get(key)
            val width = when (name) { CRED -> 8; COMM -> 16; SECCOMP, SECCOMP_MODE -> 4
                else -> error(name, "STRUCT_RULE_UNKNOWN") }
            if (offset < 0 || target?.ownerSizeBytes?.let { offset.toLong() + width > it } == true)
                error(key, "STRUCT_MEMBER_BOUNDS")
            fields[key] = UltraKernelLayoutField(offset, width, target?.ownerSizeBytes ?: ownerSizes[key.substringBefore('.')],
                if (name in overrides) "override" else target?.source ?: "accessor")
        }
        if (credentialMembers.isNotEmpty()) {
            val taskCred = offsets[CRED] ?: error(CRED, "STRUCT_OFFSET_NOT_FOUND")
            if (btf != null) {
                if (btf.offset(CRED) != taskCred) error(CRED, "STRUCT_OFFSET_CONFLICT")
            } else {
                if (SUID in credentialMembers || SGID in credentialMembers) {
                    val credential = credentialLayout(symbols, kernel, taskCred, null, strict)
                    // Identify both words independently, then enforce the old 8-byte reply contract.
                    if (credential.suid % 4 != 0 || credential.sgid != credential.suid + 4 ||
                        credential.euid % 4 != 0 || credential.euid == credential.suid ||
                        credential.euid == credential.sgid) error("saved IDs", "CRED_LAYOUT_NOT_FOUND")
                    fields[SUID] = UltraKernelLayoutField(credential.suid, 4, ownerSizes["cred"], "accessor")
                    fields[SGID] = UltraKernelLayoutField(credential.sgid, 4, ownerSizes["cred"], "accessor")
                    fields[CRED_EUID] = UltraKernelLayoutField(credential.euid, 4, ownerSizes["cred"], "accessor")
                }
                for ((name, anchors) in listOf(CRED_UID to UID, CRED_EUID to EUID, "cred.gid" to GID, "cred.egid" to EGID)) if (name in credentialMembers) {
                    val offset = unique(name, getters(symbols, kernel, anchors, strict)
                        .filter { it.field == taskCred && it.width == 4 }.map { it.member })
                        ?: error(name, "STRUCT_OFFSET_NOT_FOUND")
                    fields[name] = UltraKernelLayoutField(offset, 4, ownerSizes["cred"], "accessor")
                }
                if (CRED_GROUP_INFO in credentialMembers || GROUP_USAGE in credentialMembers) {
                    val (groups, usage) = groupLayout(symbols, kernel)
                    fields[CRED_GROUP_INFO] = UltraKernelLayoutField(groups.offset, groups.width,
                        ownerSizes["cred"], "accessor:set_groups+groups_alloc")
                    if (GROUP_USAGE in credentialMembers) fields[GROUP_USAGE] =
                        UltraKernelLayoutField(usage.offset, usage.width, ownerSizes["group_info"],
                            "accessor:set_groups+groups_alloc:zero-count-allocation-header")
                }
                if (CRED_USAGE in credentialMembers) {
                    val usage = credUsage(symbols, kernel)
                    fields[CRED_USAGE] = UltraKernelLayoutField(usage.offset, usage.width,
                        ownerSizes["cred"], "accessor:get_task_cred+__put_cred")
                }
                if (CRED_SECUREBITS in credentialMembers) {
                    val candidates = arrayListOf<Int>()
                    subjectRuns(symbols, kernel, "cap_task_prctl", Subject.SECUREBITS, { _, value, _ ->
                        val field = (value as? Scalar)?.takeIf { it.field == taskCred && it.width == 4 }
                            ?: error(CRED_SECUREBITS, "STRUCT_SECUREBITS_RETURN")
                        candidates += field.member
                    })
                    val offset = unique(CRED_SECUREBITS, candidates)
                        ?: error(CRED_SECUREBITS, "STRUCT_OFFSET_NOT_FOUND")
                    if (offset < 0 || offset % 4 != 0) error(CRED_SECUREBITS, "STRUCT_MEMBER_BOUNDS")
                    fields[CRED_SECUREBITS] = UltraKernelLayoutField(offset, 4, ownerSizes["cred"],
                        "accessor:cap_task_prctl(27)")
                }
                for (name in credentialMembers) if (name !in fields) error(name, "STRUCT_RULE_UNKNOWN")
            }
        }
        for ((name, field) in fields) if (field.offsetBytes < 0 || field.widthBytes <= 0 || field.ownerSizeBytes?.let {
            field.offsetBytes.toLong() + field.widthBytes > it } == true) error(name, "STRUCT_MEMBER_BOUNDS")
        return UltraKernelLayout(fields)
    }
    /** Strict input-image evidence for the runtime table; no nominal C type is inferred here. */
    fun runtimeAccessorField(symbols: UltraKernelSymbols, kernel: ByteArray, name: String): UltraKernelLayoutField {
        val owner = name.substringBefore('.')
        fun field(offset: Int, width: Int, source: String): UltraKernelLayoutField {
            val size = UltraKernelOwnerSizes.size(symbols, kernel, owner)
            if (offset < 0 || width <= 0 || size?.let { offset.toLong() + width > it } == true)
                error(name, "STRUCT_MEMBER_BOUNDS")
            return UltraKernelLayoutField(offset, width, size, source, 2)
        }
        if (name == "task_struct.real_cred") {
            val offsets = arrayListOf<Int>()
            subjectRuns(symbols, kernel, "get_task_cred", Subject.GET_CRED, { _, value, _ ->
                val cred = (value as? ObjectValue)?.takeIf { it.kind == "candidate-cred" && it.offset == 0 }
                    ?: error(name, "STRUCT_CRED_RETURN")
                offsets += cred.identity
            })
            val offset = unique(name, offsets) ?: error(name, "STRUCT_OFFSET_NOT_FOUND")
            if (offset < 0 || offset % 8 != 0) error(name, "STRUCT_SUBJECT_ALIGNMENT")
            return field(offset, 8, "accessor:get_task_cred:rcu-return")
        }
        if (name == "task_struct.files") {
            val offsets = linkedSetOf<Int>()
            subjectRuns(symbols, kernel, "exit_files", Subject.EXIT_FILES, { _, _, trace ->
                val files = trace.inputs.singleOrNull() ?: error(name, "STRUCT_FILES_INPUT")
                if (trace.heldLocks.isNotEmpty()) error(name, "STRUCT_FILES_LOCK_HELD")
                when (trace.zeroes[files]) {
                    true -> if (trace.writes.isNotEmpty() || trace.releases.isNotEmpty())
                        error(name, "STRUCT_FILES_NULL_EFFECT")
                    false -> if (trace.writes != setOf(ObjectValue("task-argument", offset = files.identity) to 8) ||
                        trace.releases != setOf(files)) error(name, "STRUCT_FILES_DETACH_RELEASE")
                    null -> error(name, "STRUCT_FILES_NULL_GUARD")
                }
                offsets += files.identity
            })
            val offset = offsets.singleOrNull() ?: error(name, "STRUCT_OFFSET_AMBIGUOUS")
            if (offset < 0 || offset % 8 != 0) error(name, "STRUCT_SUBJECT_ALIGNMENT")
            return field(offset, 8, "accessor:exit_files:locked-detach+put_files_struct")
        }
        if (name == "cred.user_ns") {
            val offsets = linkedSetOf<Int>()
            subjectRuns(symbols, kernel, "cap_capable", Subject.CAPABLE, { _, _, trace ->
                if (trace.writes.isNotEmpty() || trace.changes.isNotEmpty()) error(name, "STRUCT_USER_NS_WRITE")
                val candidates = trace.equalities.mapNotNull { relation ->
                    val pointer = when {
                        relation.left == ObjectValue("namespace-argument") -> relation.right as? ObjectValue
                        relation.right == ObjectValue("namespace-argument") -> relation.left as? ObjectValue
                        else -> null
                    }
                    pointer?.takeIf { it.kind == "candidate-user-ns" && it.offset == 0 }?.identity
                }.distinct()
                offsets += candidates.singleOrNull() ?: error(name, "STRUCT_USER_NS_ARGUMENT_COMPARISON")
            })
            val offset = offsets.singleOrNull() ?: error(name, "STRUCT_OFFSET_AMBIGUOUS")
            if (offset < 0 || offset % 8 != 0) error(name, "STRUCT_SUBJECT_ALIGNMENT")
            return field(offset, 8, "accessor:cap_capable:credential-namespace-argument")
        }
        if (name == "fs_struct.root" || name == "fs_struct.pwd") {
            val anchor = if (name.endsWith(".root")) "set_fs_root" else "set_fs_pwd"
            val offsets = linkedSetOf<Int>()
            subjectRuns(symbols, kernel, anchor, Subject.SET_FS, { _, _, trace ->
                val first = trace.pathCopies.singleOrNull { it.second == 0 }?.first
                    ?: error(name, "STRUCT_FS_PATH_FIRST")
                if (first < 0 || first % 8 != 0 || trace.pathCopies != setOf(first to 0, first + 8 to 8) ||
                    trace.heldLocks.isNotEmpty() || trace.pathReferences != 1 ||
                    !(trace.pathReleases == setOf(first) || trace.pathReleases.isEmpty() &&
                        (trace.pathPartsReleased.isEmpty() &&
                            trace.zeroes[ObjectValue("old-path-part", first + 8)] == true ||
                            first + 8 in trace.pathPartsReleased &&
                            (first in trace.pathPartsReleased || trace.zeroes[ObjectValue("old-path-part", first)] == true))) ||
                    trace.fsSequence.size != 2 || trace.fsSequence.distinct().size != 1 ||
                    trace.stackPointer != 0 || trace.usedLocks.size != 1 ||
                    trace.fsSequence.any { it < first.toLong() + 16 && first < it.toLong() + 4 })
                    error(name, "STRUCT_FS_PATH_SEQUENCE")
                offsets += first
            })
            val offset = offsets.singleOrNull() ?: error(name, "STRUCT_OFFSET_AMBIGUOUS")
            return field(offset, 16, "accessor:" + anchor + ":referenced-paired-path-write+release")
        }
        if (name == "task_struct.fs") {
            val offsets = linkedSetOf<Int>()
            subjectRuns(symbols, kernel, "exit_fs", Subject.EXIT_FS, { _, _, trace ->
                val fs = trace.inputs.singleOrNull() ?: error(name, "STRUCT_FS_INPUT")
                if (trace.heldLocks.isNotEmpty()) error(name, "STRUCT_FS_LOCK_HELD")
                when (trace.zeroes[fs]) {
                    true -> if (trace.writes.isNotEmpty() || trace.changes.isNotEmpty() || trace.releases.isNotEmpty())
                        error(name, "STRUCT_FS_NULL_EFFECT")
                    false -> {
                        val count = trace.changes.singleOrNull()?.desired ?: error(name, "STRUCT_FS_USERS_CHANGE")
                        if (count.address.copy(offset = 0) != fs || count.delta != -1 || count.width != 4 ||
                            trace.writes != setOf(ObjectValue("task-argument", offset = fs.identity) to 8, count.address to 4))
                            error(name, "STRUCT_FS_DETACH_USERS")
                        when (trace.zeroes[count]) {
                            true -> if (trace.releases != setOf(fs) || trace.pathReleases.size != 2)
                                error(name, "STRUCT_FS_LAST_RELEASE")
                            false -> if (trace.releases.isNotEmpty() || trace.pathReleases.isNotEmpty())
                                error(name, "STRUCT_FS_LIVE_RELEASE")
                            null -> error(name, "STRUCT_FS_USERS_GUARD")
                        }
                    }
                    null -> error(name, "STRUCT_FS_NULL_GUARD")
                }
                offsets += fs.identity
            })
            val offset = offsets.singleOrNull() ?: error(name, "STRUCT_OFFSET_AMBIGUOUS")
            if (offset < 0 || offset % 8 != 0) error(name, "STRUCT_SUBJECT_ALIGNMENT")
            return field(offset, 8, "accessor:exit_fs:locked-detach+users+path-release")
        }
        if (name == "task_struct.mm") {
            val offsets = linkedSetOf<Int>()
            subjectRuns(symbols, kernel, "get_task_mm", Subject.GET_MM, { _, value, trace ->
                val mm = trace.inputs.singleOrNull() ?: error(name, "STRUCT_MM_INPUT")
                if (trace.heldLocks.isNotEmpty() || trace.usedLocks.size != 1 || trace.writes.isNotEmpty() ||
                    trace.releases.isNotEmpty() || trace.usedLocks.any { lock ->
                        lock.offset < mm.identity.toLong() + 8 && mm.identity < lock.offset.toLong() + 4 })
                    error(name, "STRUCT_MM_LOCKED_RETURN")
                if (value == mm) {
                    val change = trace.changes.singleOrNull() ?: error(name, "STRUCT_MM_REFERENCE")
                    if (trace.zeroes[mm] != false || change.desired.address.copy(offset = 0) != mm ||
                        change.loaded.delta != 0 || change.desired.delta != 1 || change.desired.width != 4)
                        error(name, "STRUCT_MM_REFERENCE")
                } else if (value !is NumberValue || value.number != 0L || trace.changes.isNotEmpty() ||
                    trace.zeroes[mm] == null) error(name, "STRUCT_MM_RETURN")
                offsets += mm.identity
            })
            val offset = offsets.singleOrNull() ?: error(name, "STRUCT_OFFSET_AMBIGUOUS")
            if (offset < 0 || offset % 8 != 0) error(name, "STRUCT_SUBJECT_ALIGNMENT")
            return field(offset, 8, "accessor:get_task_mm:locked-load+retained-return")
        }
        if (name == "cred.non_rcu") {
            val member = credNonRcu(symbols, kernel)
            return field(member.offset, member.width, "accessor:__put_cred:rcu-zero+direct-nonzero")
        }
        if (name == "file.f_inode" && symbols.offsetsOf("file_inode").isEmpty()) {
            val offsets = linkedSetOf<Int>()
            subjectRuns(symbols, kernel, "posix_lock_file", Subject.POSIX_FILE_LOCK, { _, value, trace ->
                if (value != ObjectValue("posix-lock-result") || trace.stackPointer != 0 || trace.writes.isNotEmpty())
                    error(name, "STRUCT_LOCK_FORWARD_RETURN")
                offsets += trace.lockInodes.singleOrNull() ?: error(name, "STRUCT_LOCK_INODE")
            }, trapped = { error(name, "STRUCT_LOCK_TRAP") })
            val offset = offsets.singleOrNull() ?: error(name, "STRUCT_OFFSET_AMBIGUOUS")
            return field(offset, 8, "accessor:posix_lock_file:inode-argument+result-forward")
        }
        if (name == "dentry.d_inode" && symbols.offsetsOf("d_inode").isEmpty()) {
            val offsets = linkedSetOf<Int>()
            subjectRuns(symbols, kernel, "simple_getattr", Subject.SIMPLE_GETATTR, { _, value, trace ->
                if (value !is NumberValue || value.number != 0L || trace.stackPointer != 0)
                    error(name, "STRUCT_ATTRIBUTE_RETURN")
                offsets += trace.attributeInodes.singleOrNull() ?: error(name, "STRUCT_ATTRIBUTE_INODE")
            }, trapped = { error(name, "STRUCT_ATTRIBUTE_TRAP") })
            val offset = offsets.singleOrNull() ?: error(name, "STRUCT_OFFSET_AMBIGUOUS")
            return field(offset, 8, "accessor:simple_getattr:path-dentry+generic_fillattr")
        }
        if (name == "inode.i_size" && symbols.offsetsOf("i_size_read").isEmpty()) {
            val offsets = linkedSetOf<Int>()
            val extended = linkedSetOf<Int>()
            val inode = ObjectValue("inode-argument")
            val newSize = ObjectValue("new-size-argument")
            val extensionCalls = symbols.offsetsOf("pagecache_isize_extended").toSet()
            subjectRuns(symbols, kernel, "truncate_setsize", Subject.TRUNCATE_SIZE, { _, _, trace ->
                val offset = trace.sizeStores.singleOrNull() ?: error(name, "STRUCT_SIZE_STORE")
                if (trace.truncatedSize != offset || trace.stackPointer != 0 ||
                    trace.writes != setOf(inode.copy(offset = offset) to 8))
                    error(name, "STRUCT_SIZE_TRUNCATE")
                offsets += offset
            }, trapped = { error(name, "STRUCT_SIZE_TRAP") }, invoked = { target, arguments, trace ->
                if (target in extensionCalls) {
                    val old = arguments[1] as? Count ?: error(name, "STRUCT_SIZE_OLD_VALUE")
                    if (arguments[0] != inode || arguments[2] != newSize || old.width != 8 || old.delta != 0 ||
                        old.address.copy(offset = 0) != inode || trace.sizeStores != setOf(old.address.offset))
                        error(name, "STRUCT_SIZE_EXTENSION_ARGUMENTS")
                    extended += old.address.offset
                }
            })
            val offset = offsets.singleOrNull() ?: error(name, "STRUCT_OFFSET_AMBIGUOUS")
            if (extended != setOf(offset)) error(name, "STRUCT_SIZE_READ_STORE_CONFLICT")
            return field(offset, 8, "accessor:truncate_setsize:old-size+new-size-write+pagecache-truncate")
        }
        if (name == "file.f_pos" && symbols.offsetsOf("file_pos_read").isEmpty()) {
            val offsets = linkedSetOf<Int>()
            val position = ObjectValue("position-argument")
            subjectRuns(symbols, kernel, "vfs_setpos", Subject.SET_POSITION, { _, value, trace ->
                if (value == NumberValue(-22L, 8)) {
                    if (trace.writes.isNotEmpty()) error(name, "STRUCT_POSITION_ERROR_WRITE")
                } else {
                    if (value != position) error(name, "STRUCT_POSITION_RETURN")
                    val comparisons = trace.equalities.mapNotNull { relation ->
                        val loaded = when (position) {
                            relation.left -> relation.right as? Count
                            relation.right -> relation.left as? Count
                            else -> null
                        }
                        loaded?.takeIf { it.address.kind == "file-argument" && it.address.identity == 0 &&
                            it.address.offset >= 0 && it.address.offset % 8 == 0 && it.width == 8 && it.delta == 0 }
                            ?.let { it.address.offset to relation.equal }
                    }.distinct().singleOrNull() ?: error(name, "STRUCT_POSITION_COMPARISON")
                    val offset = comparisons.first
                    if (comparisons.second) {
                        if (trace.writes.isNotEmpty()) error(name, "STRUCT_POSITION_UNCHANGED_WRITE")
                    } else {
                        val version = trace.versionClears.singleOrNull() ?: error(name, "STRUCT_POSITION_VERSION")
                        if (trace.positionStores != setOf(offset) || version == offset || trace.writes != setOf(
                                ObjectValue("file-argument", offset = offset) to 8,
                                ObjectValue("file-argument", offset = version) to 8))
                            error(name, "STRUCT_POSITION_UPDATE")
                    }
                    offsets += offset
                }
            }, trapped = { error(name, "STRUCT_POSITION_TRAP") })
            val offset = offsets.singleOrNull() ?: error(name, "STRUCT_OFFSET_AMBIGUOUS")
            return field(offset, 8, "accessor:vfs_setpos:compared-position+argument-write+version-clear")
        }
        if (name in setOf("cred.cap_effective", "cred.cap_inheritable", "cred.cap_permitted")) {
            val member = credentialCapabilities(symbols, kernel).getValue(name)
            return field(member.offset, member.width, "accessor:cap_capget:caller-output")
        }
        if (name == COMM) {
            val candidates = linkedSetOf<SubjectField>()
            val anchors = listOf("__set_task_comm", "set_task_comm").filter { symbols.offsetsOf(it).isNotEmpty() }
            if (anchors.isEmpty()) error(name, "STRUCT_ACCESSOR_SYMBOL_MISSING")
            for (anchor in anchors) for (start in symbols.offsetsOf(anchor)) {
                val complete = walk(symbols, kernel, anchor, null, {}, { _, _ -> null },
                    startAt = start, maximumBytes = 4096, strict = true, subject = Subject.COMM_LOCATION,
                    subjectReturned = { _, trace ->
                        val copy = trace.commCopies.singleOrNull() ?: error(name, "STRUCT_COMM_COPY_AMBIGUOUS")
                        if (copy !in trace.commTerminated) error(name, "STRUCT_COPY_EXTENT_UNPROVEN")
                        candidates += SubjectField(copy.address.offset, copy.bytes)
                    })
                if (!complete) error(anchor, "STRUCT_ACCESSOR_UNRESOLVED")
            }
            val result = candidates.singleOrNull() ?: error(name, "STRUCT_OFFSET_AMBIGUOUS")
            return field(result.offset, result.width, "accessor:set_task_comm:bounded-copy+matching-termination")
        }
        if (name == "task_struct.pid") return field(traceForkPid(symbols, kernel), 4,
            "accessor:sched_process_fork:named-event-fields+registered-parent-child-copies")
        if (name == SUID || name == SGID) {
            val taskCred = resolve(symbols, kernel, setOf(CRED), emptyMap(), null, true).getValue(CRED)
            val layout = credentialLayout(symbols, kernel, taskCred, null, true)
            return field(if (name == SUID) layout.suid else layout.sgid, 4, "accessor:getresuid+getresgid")
        }
        val supported = setOf(CRED, SECCOMP_MODE, CRED_UID, CRED_EUID, "cred.gid", "cred.egid",
            CRED_GROUP_INFO, GROUP_USAGE, CRED_USAGE, CRED_SECUREBITS)
        if (name in supported) {
            val basic = if (name == CRED || name == SECCOMP_MODE) setOf(name) else setOf(CRED)
            val members = if (name in basic) emptySet() else setOf(name)
            val result = describe(symbols, kernel, basic, emptyMap(), members, null, true).fields[name]
                ?: error(name, "STRUCT_OFFSET_NOT_FOUND")
            return result.copy(parserVersion = 2)
        }
        val getter = when (name) {
            "file.f_inode" -> Triple(listOf("file_inode"), 0, 8)
            "file.f_pos" -> Triple(listOf("file_pos_read"), 0, 8)
            "inode.i_size" -> Triple(listOf("i_size_read"), 0, 8)
            "dentry.d_inode" -> Triple(listOf("d_inode"), 0, 8)
            else -> error(name, "STRUCT_RULE_UNKNOWN")
        }
        val offsets = arrayListOf<Int>()
        val anchors = arrayListOf<String>()
        for (anchor in getter.first) for (start in symbols.offsetsOf(anchor)) {
            val candidate = arrayListOf<Int>()
            val complete = walk(symbols, kernel, anchor, getter.second, { value ->
                val offset = when (value) {
                    is Pointer -> value.field.takeIf { getter.third == 8 && value.offset == 0 }
                    is Scalar -> value.member.takeIf { value.field == null && value.width == getter.third }
                    else -> null
                } ?: error(name, "STRUCT_ACCESSOR_RETURN")
                candidate += offset
            }, { _, _ -> null }, startAt = start, strict = true, permittedCalls = emptySet())
            if (!complete || candidate.isEmpty()) error(anchor, "STRUCT_ACCESSOR_UNRESOLVED")
            offsets += candidate
            anchors += anchor
        }
        val offset = unique(name, offsets) ?: error(name, "STRUCT_ACCESSOR_SYMBOL_MISSING")
        return field(offset, getter.third, "accessor:" + anchors.distinct().joinToString("+"))
    }
    class CredentialLayout(val suid: Int, val euid: Int, val sgid: Int)
    fun credentialLayout(symbols: UltraKernelSymbols, kernel: ByteArray, taskCred: Int,
        btf: UltraKernelLayout? = UltraKernelBtf.layout(kernel, symbols,
            setOf(CRED, SUID, SGID, CRED_EUID)), strict: Boolean = false): CredentialLayout {
        btf?.let { layout ->
            if (layout.offset(CRED) != taskCred) error(CRED, "STRUCT_OFFSET_CONFLICT")
            return CredentialLayout(layout.offset(SUID) ?: error(SUID, "STRUCT_OFFSET_NOT_FOUND"),
                layout.offset(CRED_EUID) ?: error(CRED_EUID, "STRUCT_OFFSET_NOT_FOUND"),
                layout.offset(SGID) ?: error(SGID, "STRUCT_OFFSET_NOT_FOUND"))
        }
        fun saved(name: String, anchors: List<String>, overflow: String, converter: String,
            realAnchors: List<String>, effectiveAnchors: List<String>): Int {
            val outputs = ArrayList<Int>()
            val direct = anchors.filter { !it.startsWith("__arm64_sys_") && symbols.offsetsOf(it).isNotEmpty() }
            for (anchor in direct.ifEmpty { anchors }) {
                for (start in symbols.offsetsOf(anchor)) {
                    val frame = anchor.startsWith("__arm64_sys_")
                    val candidateOutputs = Array(3) { ArrayList<Int>() }
                    val complete = walk(symbols, kernel, anchor, null, {},
                        called = { target, values ->
                            if (target in symbols.offsetsOf(converter)) values[1]?.takeIf { it is Scalar } else null
                        }, userArguments = true, registerFrameArguments = frame,
                        overflow = symbols.offsetsOf(overflow).toSet(), maximumBytes = 2048,
                        stored = { destination, value, width ->
                            if (destination is UserPointer && destination.offset == 0 && (frame || destination.argument == 2)) {
                                if (value !is Scalar || width != 4 || value.width != 4 || value.field != taskCred)
                                    error(name, "STRUCT_OUTPUT_UNRESOLVED")
                                candidateOutputs[destination.argument] += value.member
                            }
                        }, startAt = start, strict = strict, permittedCalls = symbols.offsetsOf(converter).toSet())
                    if (!complete || candidateOutputs[2].isEmpty()) error(anchor, "STRUCT_ACCESSOR_UNRESOLVED")
                    if (frame) {
                        val members = candidateOutputs.map { unique(name, it) ?: error(name, "STRUCT_OUTPUT_UNRESOLVED") }
                        fun getter(aliases: List<String>) = unique(name, getters(symbols, kernel, aliases, strict)
                            .filter { it.field == taskCred && it.width == 4 }.map { it.member })
                        if (members.distinct().size != 3 || members[0] != getter(realAnchors) ||
                            members[1] != getter(effectiveAnchors)) error(name, "STRUCT_OUTPUT_UNRESOLVED")
                    }
                    outputs += candidateOutputs[2]
                }
            }
            return unique(name, outputs) ?: error(name, "STRUCT_OFFSET_NOT_FOUND")
        }
        val suid = saved(SUID, RESUID, "overflowuid", "from_kuid_munged", UID, EUID)
        val sgid = saved(SGID, RESGID, "overflowgid", "from_kgid_munged", GID, EGID)
        val euid = unique(CRED_EUID, getters(symbols, kernel, EUID, strict)
            .filter { it.field == taskCred && it.width == 4 }.map { it.member })
            ?: error(CRED_EUID, "STRUCT_OFFSET_NOT_FOUND")
        return CredentialLayout(suid, euid, sgid)
    }
    private fun getters(symbols: UltraKernelSymbols, kernel: ByteArray, anchors: List<String>, strict: Boolean = false): List<Scalar> {
        val result = ArrayList<Scalar>()
        for (anchor in anchors) {
            val domain = when (anchor) { in UID + EUID -> "uid"; in GID + EGID -> "gid"; else -> null }
            val converters = domain?.let { symbols.offsetsOf("from_k${it}_munged") }.orEmpty()
            for (start in symbols.offsetsOf(anchor)) {
                val candidateResults = ArrayList<Scalar>()
                var unresolved = false
                val complete = walk(symbols, kernel, anchor, null,
                    returned = { value -> if (value is Scalar) candidateResults += value else unresolved = true },
                    called = { target, values ->
                        if (target in converters) values[1]?.takeIf { it is Scalar }
                        else null
                    }, overflow = domain?.let { symbols.offsetsOf("overflow$it").toSet() }.orEmpty(), startAt = start, strict = strict, permittedCalls = converters.toSet())
                if (!complete || unresolved || candidateResults.isEmpty()) error(anchor, "STRUCT_ACCESSOR_UNRESOLVED")
                result += candidateResults
            }
        }
        return result
    }
    private fun comm(symbols: UltraKernelSymbols, kernel: ByteArray, strict: Boolean = false): Int? {
        val result = ArrayList<Int>()
        val copiers = COPIERS.mapNotNull(symbols::offsetOf).toSet()
        // These are real signatures, not a displacement relative to another task member.
        for ((name, taskArgument, copyArgument) in listOf(
            Triple("__get_task_comm", 2, 1), Triple("get_task_comm", 1, 1),
            Triple("__set_task_comm", 0, 0), Triple("set_task_comm", 0, 0))) {
            val complete = walk(symbols, kernel, name, taskArgument, {}, called = { target, values ->
                val address = values[copyArgument]
                if (target in copiers && address is Task && address.offset > 0)
                    result += address.offset
                null
            }, strict = strict, permittedCalls = copiers)
            if (strict && symbols.offsetsOf(name).isNotEmpty() && !complete) error(name, "STRUCT_ACCESSOR_UNRESOLVED")
        }
        return unique(COMM, result)
    }
    private fun unique(name: String, values: List<Int>): Int? {
        val distinct = values.distinct()
        if (distinct.size > 1) error(name, "STRUCT_OFFSET_AMBIGUOUS")
        return distinct.singleOrNull()
    }
    private fun error(name: String, reason: String): Nothing = throw UltraKernelGenerateException(
        UltraKernelGenerateCodes.STRUCT_OFFSET, "$reason:$name")

    private data class TraceField(val name: String, val type: String, val offset: Int, val width: Int,
        val alignment: Int, val signed: Int)
    private data class TraceCopy(val destination: Int, val task: String, val source: Int, val width: Int)

    /** Bind named sched fields to the registered callback's actual original-task copies. */
    private fun traceForkPid(symbols: UltraKernelSymbols, kernel: ByteArray): Int {
        val anchor = "trace_event_raw_event_sched_process_fork"
        fun one(name: String): Int = symbols.offsetsOf(name).singleOrNull()
            ?: error(name, "STRUCT_TRACE_SYMBOL")
        val event = one("event_sched_process_fork")
        val eventClass = one("event_class_sched_process_fork")
        val callback = one(anchor)
        val pointers = UltraKernelImagePointers(kernel, symbols)
        fun isCallback(at: Int, target: Int): Boolean {
            if (at == target) return true
            if (at < 0 || at.toLong() + 4 > kernel.size || symbols.nextOffset(at) != at + 4) return false
            val word = UltraKernelA64.le32Bits(kernel, at)
            return word and 0xfc000000.toInt() == 0x14000000 &&
                at + UltraKernelA64.signExtend((word and 0x03ffffff).toLong(), 26) * 4 == target.toLong()
        }
        if (eventClass !in pointers.references(event)) error(anchor, "STRUCT_TRACE_EVENT_CLASS")
        val classReferences = pointers.references(eventClass)
        if (classReferences.none { isCallback(it, callback) }) error(anchor, "STRUCT_TRACE_CALLBACK_BINDING")
        val fields = arrayListOf<TraceField>()
        val descriptors = symbols.offsetsOf("trace_event_fields_sched_process_fork").singleOrNull()
        val definition = symbols.offsetsOf("trace_event_define_fields_sched_process_fork").singleOrNull()
        val relative = descriptors != null && descriptors in classReferences
        if (relative) {
            // trace_event_fields stores size/alignment, not task-member offsets or record-header size.
            val start = descriptors!!
            val end = symbols.nextOffset(start) ?: error(anchor, "STRUCT_TRACE_FIELDS_END")
            var at = start
            var cursor = 0
            var terminated = false
            while (at.toLong() + 32 <= end) {
                val type = pointers.pointer(at)
                val name = pointers.pointer(at + 8)
                if (type == null && name == null) { terminated = true; break }
                if (type == null || name == null) error(anchor, "STRUCT_TRACE_DESCRIPTOR_POINTER")
                val width = UltraKernelA64.le32Bits(kernel, at + 16)
                val alignment = UltraKernelA64.le32Bits(kernel, at + 20)
                val signed = UltraKernelA64.le32Bits(kernel, at + 24)
                if (width <= 0 || alignment !in setOf(1, 2, 4, 8, 16) || signed !in 0..1)
                    error(anchor, "STRUCT_TRACE_DESCRIPTOR_RANGE")
                cursor = UltraKernelA64.alignUp(cursor, alignment)
                fields += TraceField(pointers.text(name), pointers.text(type), cursor, width, alignment, signed)
                cursor += width
                at += 32
            }
            if (!terminated) error(anchor, "STRUCT_TRACE_FIELDS_TERMINATOR")
        } else {
            if (definition == null || classReferences.none { isCallback(it, definition) })
                error(anchor, "STRUCT_TRACE_FIELDS_BINDING")
            val defineCalls = symbols.offsetsOf("trace_define_field").toSet()
            if (defineCalls.isEmpty()) error(anchor, "STRUCT_TRACE_DEFINE_MISSING")
            val complete = walk(symbols, kernel, "trace_event_define_fields_sched_process_fork", null, {}, { target, values ->
                if (target in defineCalls) {
                    if (values[0] != ObjectValue("trace-event-argument")) error(anchor, "STRUCT_TRACE_DEFINE_EVENT")
                    fun text(index: Int) = pointers.text((values[index] as? Address)?.offset
                        ?: error(anchor, "STRUCT_TRACE_DEFINE_STRING"))
                    fun integer(index: Int) = (values[index] as? NumberValue)?.number
                        ?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
                        ?: error(anchor, "STRUCT_TRACE_DEFINE_INTEGER")
                    val width = integer(4)
                    if (width <= 0 || integer(5) !in 0..1) error(anchor, "STRUCT_TRACE_DEFINE_RANGE")
                    fields += TraceField(text(2), text(1), integer(3), width, 1, integer(5))
                }
                null
            }, startAt = definition, maximumBytes = 4096, strict = true, subject = Subject.TRACE_LOCATION,
                locationArguments = mapOf(0 to ObjectValue("trace-event-argument")))
            if (!complete) error(anchor, "STRUCT_TRACE_DEFINE_UNRESOLVED")
        }
        val named = fields.distinct().associateBy { it.name }
        if (fields.distinct().size != named.size || named.keys != setOf("parent_comm", "parent_pid", "child_comm", "child_pid"))
            error(anchor, "STRUCT_TRACE_FIELD_NAMES")
        for (name in listOf("parent_pid", "child_pid")) if (named.getValue(name).let {
                it.type != "pid_t" || it.width != 4 || it.signed != 1 }) error(anchor, "STRUCT_TRACE_PID_TYPE")
        val comm = runtimeAccessorField(symbols, kernel, COMM)
        for (name in listOf("parent_comm", "child_comm")) if (named.getValue(name).let {
                it.type != "char[${comm.widthBytes}]" || it.width != comm.widthBytes })
            error(anchor, "STRUCT_TRACE_COMM_EXTENT")
        val copies = linkedSetOf<TraceCopy>()
        val reserves = symbols.offsetsOf("trace_event_buffer_reserve").toSet()
        val lengths = linkedSetOf<Int>()
        if (reserves.isEmpty()) error(anchor, "STRUCT_TRACE_RESERVE_MISSING")
        fun observed(destination: Value?, value: Value?, width: Int) {
            val record = (destination as? ObjectValue)?.takeIf { it.kind == "fork-record" && it.identity == 0 }
                ?: return
            val source = value as? Count ?: return
            if (source.address.kind !in setOf("fork-parent", "fork-child") || source.address.identity != 0 ||
                source.width != width || source.delta != 0) return
            copies += TraceCopy(record.offset, source.address.kind, source.address.offset, width)
        }
        val copiers = listOf("memcpy", "memmove").flatMap(symbols::offsetsOf).toSet()
        val complete = walk(symbols, kernel, anchor, null, {}, { target, values ->
            when (target) {
                in reserves -> {
                    if (values[1] != ObjectValue("fork-file")) error(anchor, "STRUCT_TRACE_RESERVE_EVENT")
                    val length = (values[2] as? NumberValue)?.number?.takeIf { it in 1..Int.MAX_VALUE.toLong() }
                        ?.toInt() ?: error(anchor, "STRUCT_TRACE_RESERVE_LENGTH")
                    lengths += length
                    ObjectValue("fork-record")
                }
                in copiers -> {
                    val destination = values[0] as? ObjectValue
                    val source = values[1] as? ObjectValue
                    val length = (values[2] as? NumberValue)?.number?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
                    if (destination?.kind == "fork-record" && source?.kind in setOf("fork-parent", "fork-child") &&
                        destination.identity == 0 && source?.identity == 0 && length != null)
                        copies += TraceCopy(destination.offset, source!!.kind, source.offset, length)
                    values[0]
                }
                else -> null
            }
        }, startAt = callback, maximumBytes = 4096, strict = true, subject = Subject.TRACE_LOCATION,
            stored = ::observed, locationArguments = mapOf(0 to ObjectValue("fork-file"),
                1 to ObjectValue("fork-parent"), 2 to ObjectValue("fork-child")))
        if (!complete || lengths.isEmpty()) error(anchor, "STRUCT_TRACE_CALLBACK_UNRESOLVED")
        val origins = linkedSetOf<Int>()
        for ((member, task) in listOf("parent_comm" to "fork-parent", "child_comm" to "fork-child")) {
            val memberOffset = named.getValue(member).offset
            val fragments = copies.filter { it.task == task && it.source >= comm.offsetBytes &&
                it.source.toLong() + it.width <= comm.offsetBytes.toLong() + comm.widthBytes }
            val starts = fragments.map { it.destination - (it.source - comm.offsetBytes) - memberOffset }.distinct()
            val origin = starts.singleOrNull() ?: error(anchor, "STRUCT_TRACE_COMM_COPY")
            val covered = fragments.flatMap { (0 until it.width).map { index -> it.source - comm.offsetBytes + index } }.toSet()
            if (covered != (0 until comm.widthBytes).toSet()) error(anchor, "STRUCT_TRACE_COMM_COPY_WIDTH")
            origins += origin
        }
        val origin = origins.singleOrNull() ?: error(anchor, "STRUCT_TRACE_RECORD_ORIGIN")
        if ((!relative && origin != 0) || origin < 0 || origin % fields.maxOf { it.alignment } != 0)
            error(anchor, "STRUCT_TRACE_RECORD_ALIGNMENT")
        if (named.values.any { it.offset.toLong() + origin + it.width > lengths.min() })
            error(anchor, "STRUCT_TRACE_RECORD_BOUNDS")
        val result = linkedSetOf<Int>()
        for ((member, task) in listOf("parent_pid" to "fork-parent", "child_pid" to "fork-child")) {
            val slot = named.getValue(member)
            val copied = copies.filter { it.task == task && it.destination == slot.offset + origin && it.width == slot.width }
                .map { it.source }.distinct().singleOrNull() ?: error(anchor, "STRUCT_TRACE_PID_COPY")
            result += copied
        }
        return result.singleOrNull()?.takeIf { it >= 0 && it % 4 == 0 }
            ?: error(anchor, "STRUCT_TRACE_PID_CONFLICT")
    }

    private fun subjectRuns(symbols: UltraKernelSymbols, kernel: ByteArray, name: String,
        subject: Subject, result: (Int, Value?, SubjectTrace) -> Unit,
        trapped: (SubjectTrace) -> Unit = {}, permittedCalls: Set<Int>? = null,
        called: (Int, Array<Value?>) -> Value? = { _, _ -> null },
        invoked: (Int, Array<Value?>, SubjectTrace) -> Unit = { _, _, _ -> }): Int {
        val starts = symbols.offsetsOf(name)
        if (starts.isEmpty()) error(name, "STRUCT_SUBJECT_SYMBOL_MISSING")
        var returns = 0
        for (start in starts) {
            var count = 0
            val complete = walk(symbols, kernel, name, null, {}, called,
                maximumBytes = 4096, startAt = start, subject = subject,
                subjectReturned = { value, trace -> count++; result(start, value, trace) }, subjectTrapped = trapped,
                permittedCalls = permittedCalls, invoked = invoked, strict = subject in setOf(Subject.EXIT_FILES, Subject.CAPABLE, Subject.SET_FS, Subject.EXIT_FS, Subject.GET_MM, Subject.MNT_PUT, Subject.SET_POSITION, Subject.TRUNCATE_SIZE, Subject.SIMPLE_GETATTR, Subject.POSIX_FILE_LOCK, Subject.ALLOCATE_EMPTY_GROUPS, Subject.COMM_LOCATION, Subject.TRACE_LOCATION))
            if (!complete || count == 0) error(name, "STRUCT_SUBJECT_UNRESOLVED")
            returns += count
        }
        return returns
    }
    /** A flexible group array's fixed header is the target allocation requested for zero groups. */
    fun groupOwnerSize(symbols: UltraKernelSymbols, kernel: ByteArray): Int {
        val extents = linkedSetOf<Int>()
        val candidates = linkedSetOf<Int>()
        subjectRuns(symbols, kernel, "groups_alloc", Subject.ALLOCATE_EMPTY_GROUPS, { start, value, trace ->
            if (trace.stackPointer != 0) error(GROUP_USAGE, "STRUCT_ALLOCATION_STACK")
            if (value != NumberValue(0, 8) && !(value is ObjectValue && trace.zeroes[value] == true)) {
                val allocated = (value as? ObjectValue)?.takeIf { it.kind == "allocated-group" && it.offset == 0 }
                    ?: error(GROUP_USAGE, "STRUCT_ALLOCATION_RETURN")
                val extent = trace.allocationSizes[allocated] ?: error(GROUP_USAGE, "STRUCT_ALLOCATION_EXTENT")
                if (trace.zeroes[allocated] != false) error(GROUP_USAGE, "STRUCT_ALLOCATION_NULL_GUARD")
                val initialized = trace.initialised.filter { it.first.copy(offset = 0) == allocated }
                if (initialized.size != 1 || trace.writes.none { it.first.copy(offset = 0) == allocated } ||
                    trace.writes.any { (address, bytes) -> address.copy(offset = 0) != allocated ||
                        address.offset < 0 || address.offset.toLong() + bytes > extent })
                    error(GROUP_USAGE, "STRUCT_ALLOCATION_HEADER")
                candidates += start
                extents += extent
            }
        }, trapped = { error(GROUP_USAGE, "STRUCT_ALLOCATION_TRAP") })
        if (candidates != symbols.offsetsOf("groups_alloc").toSet()) error(GROUP_USAGE, "STRUCT_ALLOCATION_ANCHOR")
        return extents.singleOrNull() ?: error(GROUP_USAGE, "STRUCT_ALLOCATION_EXTENT_AMBIGUOUS")
    }

    private fun groupLayout(symbols: UltraKernelSymbols, kernel: ByteArray): Pair<SubjectField, SubjectField> {
        val setters = linkedSetOf<Pair<SubjectField, SubjectField>>()
        subjectRuns(symbols, kernel, "set_groups", Subject.GROUPS, { _, _, trace ->
            setters += trace.groupFields.singleOrNull() ?: error(CRED_GROUP_INFO, "STRUCT_GROUP_SEQUENCE")
        })
        val allocationFields = linkedSetOf<SubjectField>()
        val allocationCandidates = linkedSetOf<Int>()
        subjectRuns(symbols, kernel, "groups_alloc", Subject.ALLOCATE_GROUPS, { start, value, trace ->
            if (value == NumberValue(0, 8) || value is ObjectValue && trace.zeroes[value] == true) Unit
            else {
                val allocated = (value as? ObjectValue)?.takeIf { it.kind == "allocated-group" && it.offset == 0 }
                    ?: error(GROUP_USAGE, "STRUCT_ALLOCATION_RETURN")
                if (trace.zeroes[allocated] != false) error(GROUP_USAGE, "STRUCT_ALLOCATION_NULL_GUARD")
                val fields = trace.initialised.filter { it.first.copy(offset = 0) == allocated }
                    .map { SubjectField(it.first.offset, it.second) }
                allocationFields += fields.singleOrNull() ?: error(GROUP_USAGE, "STRUCT_ALLOCATION_INITIALIZER")
                allocationCandidates += start
            }
        })
        if (allocationCandidates != symbols.offsetsOf("groups_alloc").toSet()) error(GROUP_USAGE, "STRUCT_ALLOCATION_ANCHOR")
        val fields = setters.singleOrNull() ?: error(CRED_GROUP_INFO, "STRUCT_OFFSET_AMBIGUOUS")
        if (fields.first.offset < 0 || fields.first.offset % 8 != 0 || fields.second.offset < 0 ||
            fields.second.width !in setOf(4, 8) || fields.second.offset % fields.second.width != 0)
            error(GROUP_USAGE, "STRUCT_SUBJECT_ALIGNMENT")
        if (allocationFields.singleOrNull() != fields.second) error(GROUP_USAGE, "STRUCT_SUBJECT_ANCHOR_CONFLICT")
        return fields
    }
    private data class CredRelease(val direct: Int, val deferred: Int, val head: ObjectValue, val callback: Address)

    /** A private release function is identified by call_rcu's actual callback, not a compiler suffix. */
    private fun privateCredRelease(symbols: UltraKernelSymbols, kernel: ByteArray): CredRelease? {
        fun calls(name: String): Set<Int> = symbols.offsetsOf(name).flatMap { start ->
            val end = minOf(symbols.nextOffset(start) ?: kernel.size, start + 4096, kernel.size)
            if (start < 0 || start % 4 != 0 || end <= start) emptyList() else (start until end - 3 step 4).mapNotNull { at ->
                val word = UltraKernelA64.le32Bits(kernel, at)
                val target = at + (UltraKernelA64.signExtend((word and 0x03ffffff).toLong(), 26) * 4).toInt()
                if (word and 0x7c000000 == 0x14000000 &&
                    (UltraKernelA64.isBl(word) || target !in start until end)) target else null
            }
        }.toSet()
        val delegates = calls("call_rcu")
        val delegate = delegates.singleOrNull() ?: return null
        val forwarded = ObjectValue("forwarded-rcu")
        var forwardedCalls = 0
        val releases = linkedSetOf<CredRelease>()
        try {
            subjectRuns(symbols, kernel, "call_rcu", Subject.RCU_FORWARD, { _, value, trace ->
                if (value != forwarded || trace.writes.isNotEmpty()) error("call_rcu", "STRUCT_RCU_FORWARD")
            }, permittedCalls = delegates, called = { _, values ->
                if (values[0] != ObjectValue("rcu-head") || values[1] != ObjectValue("rcu-callback") ||
                    (values[2] as? NumberValue)?.number != 0L) error("call_rcu", "STRUCT_RCU_FORWARD")
                forwardedCalls++; forwarded
            })
            if (forwardedCalls != 1 || !symbols.isSymbolStart(delegate)) return null
            subjectRuns(symbols, kernel, "__put_cred", Subject.PUT_CRED, { _, _, _ -> },
                permittedCalls = calls("__put_cred"), called = { target, values ->
                    if (target == delegate) {
                        val head = (values[0] as? ObjectValue)?.takeIf { it.kind == "cred-argument" && it.offset > 0 }
                            ?: error("__put_cred", "STRUCT_CRED_RELEASE_CALL")
                        val callback = values[1] as? Address ?: error("__put_cred", "STRUCT_CRED_RELEASE_CALL")
                        if ((values[2] as? NumberValue)?.number != 0L || callback.offset < 0 ||
                            callback.offset.toLong() + 4 > kernel.size || !symbols.isSymbolStart(callback.offset))
                            error("__put_cred", "STRUCT_CRED_RELEASE_CALL")
                        val word = UltraKernelA64.le32Bits(kernel, callback.offset)
                        val direct = if (symbols.nextOffset(callback.offset) == callback.offset + 4 &&
                            word and 0xfc000000.toInt() == 0x14000000) callback.offset +
                            (UltraKernelA64.signExtend((word and 0x03ffffff).toLong(), 26) * 4).toInt() else callback.offset
                        if (!symbols.isSymbolStart(direct)) error("__put_cred", "STRUCT_CRED_RELEASE_CALL")
                        releases += CredRelease(direct, delegate, head, callback)
                    }
                    null
                })
        } catch (_: UltraKernelGenerateException) { return null }
        return releases.singleOrNull()
    }

    private fun credUsage(symbols: UltraKernelSymbols, kernel: ByteArray): SubjectField {
        val getters = linkedSetOf<SubjectField>()
        subjectRuns(symbols, kernel, "get_task_cred", Subject.GET_CRED, { _, value, trace ->
            val cred = (value as? ObjectValue)?.takeIf { it.kind == "candidate-cred" && it.offset == 0 }
                ?: error(CRED_USAGE, "STRUCT_CRED_RETURN")
            val changes = trace.changes.filter { it.desired.address.copy(offset = 0) == cred &&
                it.desired.delta == 1 && trace.zeroes[it.loaded] == false }
                .map { SubjectField(it.desired.address.offset, it.desired.width) }.distinct()
            val field = changes.singleOrNull() ?: error(CRED_USAGE, "STRUCT_CRED_INCREMENT")
            if (trace.writes.any { (destination, width) -> destination.copy(offset = 0) == cred &&
                destination.offset < field.offset.toLong() + field.width && field.offset < destination.offset.toLong() + width })
                error(CRED_USAGE, "STRUCT_SUBJECT_OVERWRITE")
            getters += field
        })
        val putCandidates = arrayListOf<Set<SubjectField>>()
        val traps = linkedSetOf<SubjectField>()
        val privateRelease = privateCredRelease(symbols, kernel)
        val releaseCalls = listOf("put_cred_rcu", "call_rcu", "__call_rcu").flatMap(symbols::offsetsOf).toSet() +
            listOfNotNull(privateRelease?.direct, privateRelease?.deferred)
        subjectRuns(symbols, kernel, "__put_cred", Subject.PUT_CRED, { _, _, trace ->
            val zero = trace.zeroes.filter { (value, isZero) -> isZero && value is Count &&
                value.address.kind == "cred-argument" && value.delta == 0 &&
                trace.writes.none { (destination, width) -> destination.copy(offset = 0) == value.address.copy(offset = 0) &&
                    destination.offset < value.address.offset.toLong() + value.width && value.address.offset < destination.offset.toLong() + width } }
                .keys.filterIsInstance<Count>().map { SubjectField(it.address.offset, it.width) }.distinct()
            if (zero.isEmpty()) error(CRED_USAGE, "STRUCT_CRED_ZERO_ASSERTION")
            putCandidates += zero.toSet()
        }, trapped = { trace ->
            traps += trace.zeroes.filter { (value, isZero) -> !isZero && value is Count &&
                value.address.kind == "cred-argument" && value.delta == 0 }
                .keys.filterIsInstance<Count>().map { SubjectField(it.address.offset, it.width) }
        }, permittedCalls = releaseCalls, called = { target, values ->
            if (privateRelease != null && target in setOf(privateRelease.direct, privateRelease.deferred)) {
                if (values[0] != privateRelease.head || target == privateRelease.deferred &&
                    (values[1] != privateRelease.callback || (values[2] as? NumberValue)?.number != 0L))
                    error(CRED_USAGE, "STRUCT_CRED_RELEASE_CALL")
            }
            null
        })
        val field = getters.singleOrNull() ?: error(CRED_USAGE, "STRUCT_OFFSET_AMBIGUOUS")
        if (field.offset < 0 || field.width !in setOf(4, 8) || field.offset % field.width != 0)
            error(CRED_USAGE, "STRUCT_SUBJECT_ALIGNMENT")
        val puts = putCandidates.reduce { a, b -> a.intersect(b) }
        if (puts.singleOrNull() != field || traps != puts) error(CRED_USAGE, "STRUCT_SUBJECT_ANCHOR_CONFLICT")
        return field
    }

    /** Caller outputs prove each complete capability block without assigning a nominal C type. */
    private fun credentialCapabilities(symbols: UltraKernelSymbols, kernel: ByteArray): Map<String, SubjectField> {
        val realCred = runtimeAccessorField(symbols, kernel, "task_struct.real_cred").offsetBytes
        val candidates = Array(3) { linkedSetOf<SubjectField>() }
        val starts = symbols.offsetsOf("cap_capget")
        if (starts.isEmpty()) error("cap_capget", "STRUCT_ACCESSOR_SYMBOL_MISSING")
        for (start in starts) {
            var returns = 0
            val complete = walk(symbols, kernel, "cap_capget", 0, {}, { _, _ -> null },
                maximumBytes = 4096, startAt = start, subject = Subject.CAPGET, strict = true,
                subjectReturned = { value, trace ->
                    if (value != NumberValue(0, 4)) error("cap_capget", "STRUCT_CAPGET_RETURN")
                    for (argument in 1..3) {
                        val copies = trace.outputCopies.filter { it.argument == argument }.sortedBy { it.destination }
                        if (copies.isEmpty()) error("cap_capget", "STRUCT_CAPGET_OUTPUT")
                        val first = copies.first().value.member
                        var bytes = 0
                        for (copy in copies) {
                            if (copy.destination != bytes || copy.value.field != realCred || copy.width !in setOf(4, 8) ||
                                copy.value.width != copy.width || copy.value.member != first + bytes || copy.value.signedToBytes != 0)
                                error("cap_capget", "STRUCT_CAPGET_OUTPUT")
                            bytes += copy.width
                        }
                        if (bytes !in setOf(4, 8) || first < 0 || first % 4 != 0)
                            error("cap_capget", "STRUCT_CAPGET_EXTENT")
                        candidates[argument - 1] += SubjectField(first, bytes)
                    }
                    returns++
                })
            if (!complete || returns == 0) error("cap_capget", "STRUCT_ACCESSOR_UNRESOLVED")
        }
        val fields = candidates.map { it.singleOrNull() ?: error("cap_capget", "STRUCT_OFFSET_AMBIGUOUS") }
        if (fields.map { it.width }.distinct().size != 1 || fields.indices.any { a -> fields.indices.any { b ->
                a != b && fields[a].offset < fields[b].offset + fields[b].width &&
                    fields[b].offset < fields[a].offset + fields[a].width } })
            error("cap_capget", "STRUCT_CAPGET_OVERLAP")
        return listOf("cred.cap_effective", "cred.cap_inheritable", "cred.cap_permitted").zip(fields).toMap()
    }

    /** The same tested scalar selects deferred RCU on zero and direct release on nonzero. */
    private fun credNonRcu(symbols: UltraKernelSymbols, kernel: ByteArray): SubjectField {
        val usage = credUsage(symbols, kernel)
        val privateRelease = privateCredRelease(symbols, kernel)
        val direct = symbols.offsetsOf("put_cred_rcu").toSet() + listOfNotNull(privateRelease?.direct)
        val deferred = listOf("call_rcu", "__call_rcu").flatMap(symbols::offsetsOf).toSet() +
            listOfNotNull(privateRelease?.deferred)
        if (direct.isEmpty() || deferred.isEmpty() || direct.intersect(deferred).isNotEmpty())
            error("cred.non_rcu", "STRUCT_CRED_RELEASE_ANCHOR")
        val zeroBranches = arrayListOf<Set<SubjectField>>()
        val nonzeroBranches = arrayListOf<Set<SubjectField>>()
        val heads = linkedSetOf<Int>()
        subjectRuns(symbols, kernel, "__put_cred", Subject.PUT_CRED, { _, _, _ -> },
            permittedCalls = direct + deferred, invoked = { target, values, trace ->
                if (target in direct || target in deferred) {
                    val head = (values[0] as? ObjectValue)?.takeIf { it.kind == "cred-argument" && it.offset > 0 }
                        ?: error("cred.non_rcu", "STRUCT_CRED_RELEASE_HEAD")
                    heads += head.offset
                    val zero = target in deferred
                    val fields = trace.zeroes.filter { (value, isZero) -> isZero == zero && value is Count &&
                        value.address.kind == "cred-argument" && value.delta == 0 }
                        .keys.filterIsInstance<Count>().map { SubjectField(it.address.offset, it.width) }
                        .filter { it != usage }.toSet()
                    if (fields.isEmpty()) error("cred.non_rcu", "STRUCT_CRED_RELEASE_GUARD")
                    if (zero) zeroBranches += fields else nonzeroBranches += fields
                }
            })
        if (zeroBranches.isEmpty() || nonzeroBranches.isEmpty() || heads.size != 1)
            error("cred.non_rcu", "STRUCT_CRED_RELEASE_BRANCHES")
        val fields = zeroBranches.reduce { a, b -> a.intersect(b) }
            .intersect(nonzeroBranches.reduce { a, b -> a.intersect(b) })
        val result = fields.singleOrNull() ?: error("cred.non_rcu", "STRUCT_OFFSET_AMBIGUOUS")
        if (result.offset < 0 || result.width != 4 || result.offset % 4 != 0)
            error("cred.non_rcu", "STRUCT_SUBJECT_ALIGNMENT")
        return result
    }

    /** Keep each alternative block correlated, including entry into its middle and retry loops. */
    private fun instructionVariants(symbols: UltraKernelSymbols, kernel: ByteArray, start: Int, end: Int): Map<Int, Set<InstructionView>> {
        val variants = linkedMapOf<Int, Set<InstructionView>>()
        fun word(at: Int): Int {
            if (at < 0 || at % 4 != 0 || at.toLong() + 4 > kernel.size) error("alternatives", "STRUCT_METADATA_BOUNDS")
            return UltraKernelA64.le32Bits(kernel, at)
        }
        fun add(choices: Set<InstructionView>) {
            val addresses = choices.first().words.keys
            if (choices.any { it.words.keys != addresses } || addresses.any { it in variants })
                error("alternatives", "STRUCT_METADATA_OVERLAP")
            for (at in addresses) variants[at] = choices
        }
        val altStart = symbols.offsetOf("__alt_instructions")
        val altEnd = symbols.offsetOf("__alt_instructions_end")
        if (altStart != null || altEnd != null) {
            if (altStart == null || altEnd == null || altStart < 0 || altEnd > kernel.size ||
                altEnd < altStart || (altEnd - altStart) % 12 != 0) error("alternatives", "STRUCT_METADATA_BOUNDS")
            for (record in altStart until altEnd step 12) {
                val original = record.toLong() + UltraKernelA64.le32Bits(kernel, record)
                val length = kernel[record + 10].toInt() and 255
                if (original >= end || original + length <= start) continue
                val replacement = record.toLong() + 4 + UltraKernelA64.le32Bits(kernel, record + 4)
                val replacementLength = kernel[record + 11].toInt() and 255
                val feature = (kernel[record + 8].toInt() and 255) or ((kernel[record + 9].toInt() and 255) shl 8)
                if (original < start || original + length > end || length == 0 || length % 4 != 0 || original % 4 != 0L ||
                    replacement < 0 || replacement > Int.MAX_VALUE) error("alternatives", "STRUCT_ALTERNATIVE_UNSUPPORTED:" +
                        original.toString(16) + ":" + length + ":" + replacementLength)
                val originalAt = original.toInt()
                val originalWords = (0 until length step 4).associate { originalAt + it to word(originalAt + it) }
                val replacementWords = if (feature and 0x8000 != 0) {
                    if (replacementLength != 0 || replacement.toInt() !in symbols.offsetsOf("alt_cb_patch_nops"))
                        error("alternatives", "STRUCT_ALTERNATIVE_CALLBACK")
                    var proved = 0
                    val expected = (0 until length step 4).map { Triple(ObjectValue("alternative-output", offset = it),
                        UltraKernelA64.NOP.toLong() and 0xffffffffL, 4) }
                    val complete = walk(symbols, kernel, "alt_cb_patch_nops", null, {}, { _, _ -> null },
                        maximumBytes = 512, startAt = replacement.toInt(), subject = Subject.ALTERNATIVE,
                        alternativeInstructions = length / 4, subjectReturned = { _, trace ->
                            if (trace.alternativeWrites != expected) error("alternatives", "STRUCT_ALTERNATIVE_CALLBACK_WRITE")
                            proved++
                        })
                    if (!complete || proved != 1) error("alternatives", "STRUCT_ALTERNATIVE_CALLBACK")
                    originalWords.mapValues { UltraKernelA64.NOP }
                } else {
                    if (replacementLength != length || replacement + length > kernel.size)
                        error("alternatives", "STRUCT_ALTERNATIVE_LENGTH")
                    (0 until length step 4).associate { delta ->
                        val value = word(replacement.toInt() + delta)
                        // Relocatable replacements stay rejected until their exact kernel relocation is modelled.
                        if (UltraKernelA64.isAdr(value) || UltraKernelA64.isAdrp(value) ||
                            value and 0x7c000000 == 0x14000000 || value and 0xff000010.toInt() == 0x54000000 ||
                            value and 0x7e000000 in setOf(0x34000000, 0x36000000))
                            error("alternatives", "STRUCT_ALTERNATIVE_RELOCATION:" + (originalAt + delta).toString(16))
                        originalAt + delta to value
                    }
                }
                // A CPU capability has one value throughout this kernel invocation.
                // Callback alternatives retain their independently proved choices.
                val capability = feature.takeIf { it and 0x8000 == 0 }
                add(linkedSetOf(InstructionView(originalWords, capability),
                    InstructionView(replacementWords, capability, true)))
            }
        }
        val jumpStart = symbols.offsetOf("__start___jump_table")
        val jumpEnd = symbols.offsetOf("__stop___jump_table")
        if (jumpStart != null || jumpEnd != null) {
            if (jumpStart == null || jumpEnd == null || jumpStart < 0 || jumpEnd > kernel.size ||
                jumpEnd < jumpStart || (jumpEnd - jumpStart) % 16 != 0) error("jump labels", "STRUCT_METADATA_BOUNDS")
            for (record in jumpStart until jumpEnd step 16) {
                val at = record.toLong() + UltraKernelA64.le32Bits(kernel, record)
                if (at !in start.toLong() until end.toLong()) continue
                val target = record.toLong() + 4 + UltraKernelA64.le32Bits(kernel, record + 4)
                if (target !in start.toLong() until end.toLong() || target % 4 != 0L)
                    error("jump labels", "STRUCT_JUMP_LABEL_TARGET")
                val branch = UltraKernelA64.encodeB(at, target)
                if (word(at.toInt()) !in setOf(branch, UltraKernelA64.NOP)) error("jump labels", "STRUCT_JUMP_LABEL_INSTRUCTION")
                add(linkedSetOf(InstructionView(mapOf(at.toInt() to branch)),
                    InstructionView(mapOf(at.toInt() to UltraKernelA64.NOP))))
            }
        }
        return variants
    }

    private data class AttributeInline(val inodeRegister: Int, val statRegister: Int)

    /** Match the complete target's named leaf body, not a reference-kernel byte signature. */
    private fun inlineAttributes(symbols: UltraKernelSymbols, kernel: ByteArray, start: Int, end: Int):
        Map<Int, AttributeInline> {
        fun operands(word: Int): IntArray? = when {
            word and 0x3b000000 == 0x39000000 && word and (1 shl 26) == 0 &&
                (word ushr 22) and 3 in setOf(0, 1) -> intArrayOf(0, 5)
            word and 0x3a000000 == 0x28000000 && word and (1 shl 26) == 0 &&
                (word ushr 23) and 3 == 2 && (word ushr 30) and 3 in setOf(0, 2) -> intArrayOf(0, 5, 10)
            word and 0x7f800000 == 0x52800000 -> intArrayOf(0)
            word and 0x7fe0fc00 == 0x1ac02000 -> intArrayOf(0, 5, 16)
            else -> null
        }
        val variants = instructionVariants(symbols, kernel, start, end).values.flatten()
            .flatMap { it.words.keys }.toSet()
        val matches = linkedMapOf<Int, AttributeInline>()
        for (entry in symbols.offsetsOf("generic_fillattr")) {
            val limit = symbols.nextOffset(entry) ?: continue
            if (entry < 0 || entry % 4 != 0 || limit % 4 != 0 || limit !in entry + 8..minOf(kernel.size, entry + 1024) ||
                UltraKernelA64.le32Bits(kernel, limit - 4) != UltraKernelA64.RET ||
                instructionVariants(symbols, kernel, entry, limit).isNotEmpty()) continue
            val template = (entry until limit - 4 step 4).map { UltraKernelA64.le32Bits(kernel, it) }
            if (template.any { operands(it) == null }) continue
            val length = template.size * 4
            for (at in start..end - length step 4) {
                if ((at until at + length step 4).any { it in variants }) continue
                val registers = IntArray(32) { -1 }
                val reverse = IntArray(32) { -1 }
                registers[31] = 31; reverse[31] = 31
                val same = template.withIndex().all { (index, expected) ->
                    val actual = UltraKernelA64.le32Bits(kernel, at + index * 4)
                    val fields = operands(expected)!!
                    val mask = fields.fold(0) { old, shift -> old or (31 shl shift) }
                    expected and mask.inv() == actual and mask.inv() && fields.all { shift ->
                        val from = (expected ushr shift) and 31
                        val to = (actual ushr shift) and 31
                        if (registers[from] !in setOf(-1, to) || reverse[to] !in setOf(-1, from)) false
                        else { registers[from] = to; reverse[to] = from; true }
                    }
                }
                if (same && registers[0] in 0..30 && registers[1] in 0..30) {
                    val matched = AttributeInline(registers[0], registers[1])
                    if (matches.put(at, matched)?.let { it != matched } == true)
                        error("simple_getattr", "STRUCT_ATTRIBUTE_TEMPLATE_AMBIGUOUS")
                }
            }
        }
        return matches
    }

    /** Only dead machine state is discarded; unknown instructions conservatively use everything. */
    private fun accessorLiveness(kernel: ByteArray, start: Int, end: Int,
        variants: Map<Int, Set<InstructionView>>, callArguments: Long? = null): Map<Int, Long> {
        val flags = 1L shl 32
        val all = (1L shl 33) - 1
        fun reg(index: Int) = if (index == 31) 0L else 1L shl index
        data class Flow(val uses: Long, val defines: Long, val next: List<Int>)
        val flows = (start until end - 3 step 4).associateWith { at ->
            val words = setOf(UltraKernelA64.le32Bits(kernel, at)) +
                variants[at].orEmpty().mapNotNull { it.words[at] }
            words.map { word ->
                val rd = word and 31; val rn = (word ushr 5) and 31; val rm = (word ushr 16) and 31
                val ordinary = listOf(at + 4)
                when {
                    word == UltraKernelA64.RET -> Flow(reg(0), 0, emptyList())
                    word and 0xffe0001f.toInt() == 0xd4200000.toInt() -> Flow(0, 0, emptyList())
                    word and 0x7c000000 == 0x14000000 -> {
                        val target = at + (UltraKernelA64.signExtend((word and 0x03ffffff).toLong(), 26) * 4).toInt()
                        if (UltraKernelA64.isBl(word)) Flow(callArguments ?: all, ((1L shl 19) - 1) or reg(30) or flags, ordinary)
                        else if (target in start until end) Flow(0, 0, listOf(target))
                        else Flow(callArguments ?: all, 0, emptyList())
                    }
                    word and 0xff000010.toInt() == 0x54000000 || word and 0x7e000000 in setOf(0x34000000, 0x36000000) -> {
                        val conditional = word and 0xff000010.toInt() == 0x54000000
                        val bits = if (word and 0x7e000000 == 0x36000000) 14 else 19
                        val target = at + (UltraKernelA64.signExtend(((word ushr 5) and ((1 shl bits) - 1)).toLong(), bits) * 4).toInt()
                        Flow(if (conditional) flags else reg(rd), 0, ordinary + target)
                    }
                    callArguments != null && word and 0xfffffc1f.toInt() == 0xd63f0000.toInt() ->
                        Flow(callArguments or reg(rn), ((1L shl 19) - 1) or reg(30) or flags, ordinary)
                    word and 0xffffffe0.toInt() == 0xd53b4200.toInt() -> Flow(flags, reg(rd), ordinary)
                    UltraKernelA64.isAdr(word) || UltraKernelA64.isAdrp(word) ||
                        word and 0xfff00000.toInt() == 0xd5300000.toInt() -> Flow(0, reg(rd), ordinary)
                    word and 0xfff00000.toInt() == 0xd5100000.toInt() -> Flow(reg(rd), 0, ordinary)
                    word and 0xfffff01f.toInt() == 0xd503201f.toInt() ||
                        word and 0xfffff09f.toInt() == 0xd503309f.toInt() ||
                        word in setOf(0xd500409f.toInt(), 0xd500419f.toInt(), 0xd50342df.toInt(), 0xd50342ff.toInt()) -> Flow(0, 0, ordinary)
                    word and 0x7f800000 in setOf(0x12800000, 0x52800000, 0x72800000) ->
                        Flow(if (word and 0x7f800000 == 0x72800000) reg(rd) else 0, reg(rd), ordinary)
                    word and 0x1f800000 == 0x11000000 -> Flow(reg(rn), reg(rd) or
                        (if (word and (1 shl 29) != 0) flags else 0), ordinary)
                    word and 0x1f000000 in setOf(0x0a000000, 0x0b000000) -> Flow(reg(rn) or reg(rm),
                        reg(rd) or (if (word and 0x1f000000 == 0x0a000000) {
                            if ((word ushr 29) and 3 == 3) flags else 0
                        } else if (word and (1 shl 29) != 0) flags else 0), ordinary)
                    // CSINV/CSINC/CSNEG aliases read the same operands and NZCV as CSEL.
                    word and 0x3fe00800 == 0x1a800000 -> Flow(reg(rn) or reg(rm) or flags, reg(rd), ordinary)
                    // ADC/SBC consume carry even when they also replace NZCV (ADCS/SBCS).
                    word and 0x1fe0fc00 == 0x1a000000 -> Flow(reg(rn) or reg(rm) or flags,
                        reg(rd) or (if (word and (1 shl 29) != 0) flags else 0), ordinary)
                    word and 0x1f800000 == 0x12000000 -> Flow(reg(rn), reg(rd) or
                        (if ((word ushr 29) and 3 == 3) flags else 0), ordinary)
                    word and 0x1f800000 == 0x13000000 -> Flow(reg(rn) or
                        (if ((word ushr 29) and 3 == 1) reg(rd) else 0), reg(rd), ordinary)
                    word and 0xffc00000.toInt() == 0xf9800000.toInt() -> Flow(reg(rn), 0, ordinary)
                    word and 0x3b200c00 == 0x38200800 && word and (1 shl 26) == 0 &&
                        (word ushr 30) and 3 == 3 && (word ushr 22) and 3 == 2 ->
                        Flow(reg(rn) or reg(rm), 0, ordinary)
                    word and 0x3b200000 == 0x38000000 && word and (1 shl 26) == 0 &&
                        (word ushr 30) and 3 == 3 && (word ushr 22) and 3 == 2 -> Flow(reg(rn), 0, ordinary)
                    word and 0x3b000000 == 0x39000000 && word and (1 shl 26) == 0 ->
                        if ((word ushr 22) and 3 == 0) Flow(reg(rn) or reg(rd), 0, ordinary)
                        else Flow(reg(rn), reg(rd), ordinary)
                    word and 0x3a000000 == 0x28000000 && word and (1 shl 26) == 0 -> {
                        val second = (word ushr 10) and 31
                        val writeback = if ((word ushr 23) and 3 in setOf(1, 3)) reg(rn) else 0
                        if (word and (1 shl 22) == 0) Flow(reg(rn) or reg(rd) or reg(second), writeback, ordinary)
                        else Flow(reg(rn), reg(rd) or reg(second) or writeback, ordinary)
                    }
                    word and 0x3b200c00 == 0x38200800 && word and (1 shl 26) == 0 ->
                        if ((word ushr 22) and 3 == 0) Flow(reg(rn) or reg(rm) or reg(rd), 0, ordinary)
                        else Flow(reg(rn) or reg(rm), reg(rd), ordinary)
                    word and 0x3b200000 == 0x38000000 && word and (1 shl 26) == 0 -> {
                        val writeback = if ((word ushr 10) and 3 in setOf(1, 3)) reg(rn) else 0
                        if ((word ushr 22) and 3 == 0) Flow(reg(rn) or reg(rd), writeback, ordinary)
                        else Flow(reg(rn), reg(rd) or writeback, ordinary)
                    }
                    else -> Flow(all, 0, ordinary)
                }
            }
        }
        val live = flows.keys.associateWith { 0L }.toMutableMap()
        do {
            var changed = false
            for ((at, choices) in flows.entries.reversed()) {
                val needed = choices.fold(0L) { result, choice -> result or choice.uses or
                    (choice.next.fold(0L) { mask, next -> mask or (live[next] ?: 0L) } and choice.defines.inv()) }
                if (live.getValue(at) != needed) { live[at] = needed; changed = true }
            }
        } while (changed)
        return live
    }

    /** Follow values reaching a return, known call argument or observed user output. */
    private fun walk(symbols: UltraKernelSymbols, kernel: ByteArray, anchor: String,
        taskArgument: Int?, returned: (Value?) -> Unit,
        called: (Int, Array<Value?>) -> Value?, userArguments: Boolean = false, registerFrameArguments: Boolean = false,
        overflow: Set<Int> = emptySet(), maximumBytes: Int = 512,
        stored: (Value?, Value?, Int) -> Unit = { _, _, _ -> }, startAt: Int? = null,
        subject: Subject? = null, subjectReturned: (Value?, SubjectTrace) -> Unit = { _, _ -> },
        subjectTrapped: (SubjectTrace) -> Unit = {}, permittedCalls: Set<Int>? = null, strict: Boolean = false,
        invoked: (Int, Array<Value?>, SubjectTrace) -> Unit = { _, _, _ -> },
        alternativeInstructions: Int = 1, locationArguments: Map<Int, ObjectValue> = emptyMap()): Boolean {
        val start = startAt ?: symbols.offsetOf(anchor) ?: return false
        if (start < 0 || start % 4 != 0 || start.toLong() + 4 > kernel.size) return false
        val end = minOf(symbols.nextOffset(start) ?: kernel.size, start + maximumBytes, kernel.size)
        data class Flags(val left: Value?, val right: Value?, val width: Int, val subtract: Boolean)
        data class State(val at: Int, val values: List<Value?>, val compared: Scalar? = null,
            val flags: Flags? = null, val exclusive: Count? = null,
            val trace: SubjectTrace = SubjectTrace(), val view: Map<Int, Int> = emptyMap(),
            val features: Map<Int, Boolean> = emptyMap())
        val initial = arrayOfNulls<Value>(32)
        taskArgument?.let { initial[it] = Task() }
        if (registerFrameArguments) initial[0] = RegisterFrame()
        else if (userArguments) for (index in 0..2) initial[index] = UserPointer(index)
        when (subject) {
            Subject.GROUPS -> { initial[0] = ObjectValue("cred-argument"); initial[1] = ObjectValue("new-group") }
            Subject.ALLOCATE_EMPTY_GROUPS -> initial[0] = NumberValue(0, 4)
            Subject.COMM_LOCATION -> {
                initial[0] = ObjectValue("comm-task")
                initial[1] = ObjectValue("comm-source")
            }
            Subject.GET_CRED -> initial[0] = ObjectValue("task-argument")
            Subject.PUT_CRED -> initial[0] = ObjectValue("cred-argument")
            Subject.RCU_FORWARD -> { initial[0] = ObjectValue("rcu-head"); initial[1] = ObjectValue("rcu-callback") }
            Subject.ALTERNATIVE -> { initial[2] = ObjectValue("alternative-output"); initial[3] = NumberValue(alternativeInstructions.toLong(), 4) }
            Subject.SECUREBITS -> initial[0] = NumberValue(27, 4) // PR_GET_SECUREBITS, not a layout offset.
            Subject.CAPGET -> for (argument in 1..3) initial[argument] = UserPointer(argument)
            Subject.EXIT_FILES -> initial[0] = ObjectValue("task-argument")
            Subject.CAPABLE -> {
                initial[0] = ObjectValue("cred-argument")
                initial[1] = ObjectValue("namespace-argument")
            }
            Subject.SET_FS -> {
                initial[0] = ObjectValue("fs-argument")
                initial[1] = ObjectValue("path-argument")
            }
            Subject.EXIT_FS -> initial[0] = ObjectValue("task-argument")
            Subject.GET_MM -> initial[0] = ObjectValue("task-argument")
            Subject.MNT_PUT -> initial[0] = ObjectValue("mount-argument")
            Subject.SET_POSITION -> {
                initial[0] = ObjectValue("file-argument")
                initial[1] = ObjectValue("position-argument")
                initial[2] = ObjectValue("maximum-position-argument")
            }
            Subject.TRUNCATE_SIZE -> {
                initial[0] = ObjectValue("inode-argument")
                initial[1] = ObjectValue("new-size-argument")
            }
            Subject.SIMPLE_GETATTR -> {
                initial[0] = ObjectValue("attribute-path")
                initial[1] = ObjectValue("attribute-stat")
            }
            Subject.POSIX_FILE_LOCK -> {
                initial[0] = ObjectValue("lock-file-argument")
                initial[1] = ObjectValue("lock-request-argument")
                initial[2] = ObjectValue("lock-conflict-argument")
            }
            else -> Unit
        }
        locationArguments.forEach { (index, value) -> initial[index] = value }
        // Location extraction follows actual register identities, not callee memory-effect summaries.
        val memberLocation = subject in setOf(Subject.COMM_LOCATION, Subject.TRACE_LOCATION)
        val instructionViews = if (strict || subject != null && subject != Subject.ALTERNATIVE)
            instructionVariants(symbols, kernel, start, end) else emptyMap()
        val liveState = if (memberLocation) accessorLiveness(kernel, start, end, instructionViews, (1L shl 9) - 1)
            else if (strict && subject == null && userArguments) accessorLiveness(kernel, start, end, instructionViews)
            else emptyMap()
        val allocators = listOf("__kmalloc", "__vmalloc", "__vmalloc_node", "__vmalloc_node_range", "kvmalloc_node").flatMap(symbols::offsetsOf).toSet()
        val releasers = listOf("kfree", "vfree", "kvfree").flatMap(symbols::offsetsOf).toSet()
        val fileLocks = symbols.offsetsOf("_raw_spin_lock").toSet()
        val fileUnlocks = symbols.offsetsOf("_raw_spin_unlock").toSet()
        val fileReleases = symbols.offsetsOf("put_files_struct").toSet()
        val fileSlowLocks = listOf("queued_spin_lock_slowpath", "_raw_spin_lock_slowpath")
            .flatMap(symbols::offsetsOf).toSet()
        val fileSchedules = listOf("preempt_schedule", "preempt_schedule_notrace", "__schedule")
            .flatMap(symbols::offsetsOf).toSet()
        val pathGets = symbols.offsetsOf("path_get").toSet()
        val pathPuts = symbols.offsetsOf("path_put").toSet()
        val mountGets = symbols.offsetsOf("mntget").toSet()
        val dentryGets = symbols.offsetsOf("lockref_get").toSet()
        val dentryPuts = symbols.offsetsOf("dput").toSet()
        val directMountPuts = symbols.offsetsOf("mntput").toSet()
        val containerMountPuts = symbols.offsetsOf("mntput_no_expire").toSet()
        val mountPuts = directMountPuts + containerMountPuts
        val mountPutEvidence: Pair<Int, Set<Pair<Int, Int>>>? by lazy { if (subject in setOf(Subject.SET_FS, Subject.EXIT_FS)) {
            val adjustments = linkedSetOf<Int>()
            val zeroWrites = linkedSetOf<Pair<Int, Int>>()
            subjectRuns(symbols, kernel, "mntput", Subject.MNT_PUT, { _, _, trace ->
                val mount = ObjectValue("mount-argument")
                if (trace.zeroes[mount] == true) {
                    if (trace.writes.isNotEmpty() || trace.releases.isNotEmpty()) error(anchor, "STRUCT_MNTPUT_NULL_EFFECT")
                } else if (trace.zeroes[mount] != false || trace.releases != setOf(mount))
                    error(anchor, "STRUCT_MNTPUT_RELEASE")
                if (trace.writes != trace.alternativeWrites.map { it.first to it.third }.toSet())
                    error(anchor, "STRUCT_MNTPUT_WRITE")
                zeroWrites += trace.alternativeWrites.map { it.first.offset to it.third }
            }, invoked = { target, arguments, _ ->
                if (target in containerMountPuts) {
                    val mount = arguments[0] as? ObjectValue ?: error(anchor, "STRUCT_MNTPUT_ARGUMENT")
                    if (mount.kind != "mount-argument" || mount.offset >= 0 || mount.offset % 8 != 0)
                        error(anchor, "STRUCT_MNTPUT_CONTAINER")
                    adjustments += mount.offset
                }
            })
            (adjustments.singleOrNull() ?: error(anchor, "STRUCT_MNTPUT_CONTAINER")) to zeroWrites
        } else null }
        val fsFrees = symbols.offsetsOf("kmem_cache_free").toSet()
        val pageTruncates = symbols.offsetsOf("truncate_inode_pages_range").toSet()
        val panics = symbols.offsetsOf("panic").toSet()
        val trappedCalls = symbols.offsetsOf("__stack_chk_fail").filter { entry ->
            val limit = symbols.nextOffset(entry)
            limit != null && entry >= 0 && entry % 4 == 0 && limit % 4 == 0 &&
                limit in entry + 4..minOf(kernel.size, entry + 96) &&
                UltraKernelA64.le32Bits(kernel, limit - 4).let { last -> UltraKernelA64.isBl(last) &&
                    limit - 4 + (UltraKernelA64.signExtend((last and 0x03ffffff).toLong(), 26) * 4).toInt() in panics } &&
                (entry until limit - 4 step 4).none { at -> UltraKernelA64.le32Bits(kernel, at).let { word ->
                    word and 0x7c000000 == 0x14000000 || word and 0xff000010.toInt() == 0x54000000 ||
                        word and 0x7e000000 in setOf(0x34000000, 0x36000000) ||
                        word and 0xfe000000.toInt() == 0xd6000000.toInt() } }
        }.toSet()
        val fsCache = if (subject == Subject.EXIT_FS) symbols.memoryOffsetOf("fs_cachep") else null
        val lockOwner = if (subject == Subject.SET_FS) "fs-argument" else "task-argument"
        val lockSubject = subject in setOf(Subject.EXIT_FILES, Subject.SET_FS, Subject.GET_MM, Subject.EXIT_FS)
        val stackSubject = subject in setOf(Subject.SET_FS, Subject.TRUNCATE_SIZE, Subject.SIMPLE_GETATTR,
            Subject.POSIX_FILE_LOCK, Subject.ALLOCATE_EMPTY_GROUPS, Subject.COMM_LOCATION, Subject.TRACE_LOCATION)
        val knownCalls = permittedCalls ?: when (subject) {
            Subject.GROUPS -> releasers
            Subject.ALLOCATE_GROUPS, Subject.ALLOCATE_EMPTY_GROUPS -> allocators
            Subject.GET_CRED, Subject.CAPGET -> listOf("__rcu_read_lock", "__rcu_read_unlock", "rcu_read_unlock_special").flatMap(symbols::offsetsOf).toSet()
            Subject.PUT_CRED -> listOf("put_cred_rcu", "call_rcu", "__call_rcu").flatMap(symbols::offsetsOf).toSet()
            Subject.EXIT_FILES -> fileLocks + fileUnlocks + fileReleases + fileSlowLocks + fileSchedules
            Subject.SET_FS -> fileLocks + fileUnlocks + fileSlowLocks + fileSchedules + pathGets + pathPuts + mountGets + dentryGets + dentryPuts + mountPuts + trappedCalls
            Subject.EXIT_FS -> fileLocks + fileUnlocks + fileSlowLocks + fileSchedules + pathPuts + dentryPuts + mountPuts + fsFrees + trappedCalls
            Subject.GET_MM -> fileLocks + fileUnlocks + fileSlowLocks + fileSchedules + trappedCalls
            Subject.MNT_PUT -> containerMountPuts
            Subject.TRUNCATE_SIZE -> (listOf("pagecache_isize_extended", "unmap_mapping_pages", "unmap_mapping_range")
                .flatMap(symbols::offsetsOf).toSet() + pageTruncates)
            Subject.SIMPLE_GETATTR -> symbols.offsetsOf("generic_fillattr").toSet()
            Subject.POSIX_FILE_LOCK -> (start until end step 4).mapNotNull { at ->
                val word = UltraKernelA64.le32Bits(kernel, at)
                if (word and 0x7c000000 != 0x14000000) null else {
                    val target = at + (UltraKernelA64.signExtend((word and 0x03ffffff).toLong(), 26) * 4).toInt()
                    target.takeIf { symbols.isSymbolStart(it) && symbols.nameAt(it)?.matches(
                        Regex("""posix_lock_inode(?:\.llvm\.[0-9]+)?""")) == true }
                }
            }.toSet()
            else -> emptySet()
        }
        val attributes = if (subject == Subject.SIMPLE_GETATTR) inlineAttributes(symbols, kernel, start, end)
            else emptyMap()
        fun attribute(inode: Value?, stat: Value?, trace: SubjectTrace): SubjectTrace {
            val candidate = (inode as? ObjectValue)?.takeIf { it.kind == "attribute-inode" &&
                it.offset == 0 && it.identity >= 0 && it.identity % 8 == 0 }
                ?: error(anchor, "STRUCT_ATTRIBUTE_INODE_ARGUMENT")
            if (stat != ObjectValue("attribute-stat") || trace.attributeInodes.isNotEmpty())
                error(anchor, "STRUCT_ATTRIBUTE_ARGUMENTS")
            return trace.copy(attributeInodes = setOf(candidate.identity))
        }
        val pending = ArrayDeque<State>()
        val visited = HashSet<State>()
        var complete = true
        pending += State(start, initial.toList())
        fun address(value: Value?, offset: Int): Value? = when (value) {
            is Task -> Task(value.offset + offset)
            is Pointer -> Pointer(value.field, value.offset + offset)
            is Address -> Address(value.offset + offset)
            is ReadOnlyAddress -> (value.offset.toLong() + offset).let { target ->
                if (target < value.lower || target > value.upper) error(anchor, "STRUCT_READONLY_BOUNDS")
                value.copy(offset = target.toInt())
            }
            is ObjectValue -> value.copy(offset = value.offset + offset)
            is UserPointer -> value.copy(offset = value.offset + offset)
            is RegisterFrame -> RegisterFrame(value.offset + offset)
            is StackAddress -> StackAddress(value.offset + offset)
            else -> null
        }
        fun number(value: Long, width: Int) = NumberValue(value and (if (width == 4) 0xffffffffL else -1L), width)
        val rodataStart = symbols.offsetsOf("__start_rodata").singleOrNull()
        val rodataEnd = symbols.offsetsOf("__end_rodata").singleOrNull()
        fun inRodata(offset: Int) = rodataStart != null && rodataEnd != null &&
            rodataStart >= 0 && rodataEnd <= kernel.size && offset >= rodataStart && offset < rodataEnd
        fun readOnlyComponent(base: Int): ReadOnlyAddress {
            if (!inRodata(base)) error(anchor, "STRUCT_READONLY_RODATA")
            val componentEnd = symbols.nextOffset(base) ?: error(anchor, "STRUCT_READONLY_COMPONENT")
            if (componentEnd <= base || componentEnd > kernel.size) error(anchor, "STRUCT_READONLY_COMPONENT")
            return ReadOnlyAddress(base, base, minOf(componentEnd, rodataEnd!!))
        }
        fun constantCondition(flags: Flags, condition: Int): Boolean {
            val left = (flags.left as NumberValue).number
            val right = (flags.right as NumberValue).number
            val result = number(if (flags.subtract) left - right else left + right, flags.width).number
            val sign = if (flags.width == 4) 1L shl 31 else Long.MIN_VALUE
            val negative = result and sign != 0L
            val zero = result == 0L
            val carry = if (flags.subtract) java.lang.Long.compareUnsigned(left, right) >= 0
                else java.lang.Long.compareUnsigned(result, left) < 0
            val overflow = (if (flags.subtract) left xor right else (left xor right).inv()) and
                (left xor result) and sign != 0L
            return when (condition) {
                0 -> zero; 1 -> !zero; 2 -> carry; 3 -> !carry
                4 -> negative; 5 -> !negative; 6 -> overflow; 7 -> !overflow
                8 -> carry && !zero; 9 -> !carry || zero
                10 -> negative == overflow; 11 -> negative != overflow
                12 -> !zero && negative == overflow; 13 -> zero || negative != overflow
                else -> true
            }
        }
        fun equivalent(left: Value?, right: Value?, trace: SubjectTrace): Boolean? {
            if (left == null || right == null) return null
            if (left == right) return true
            if (left is NumberValue && right is NumberValue) return left.number == right.number
            return trace.equalities.firstOrNull { (it.left == left && it.right == right) ||
                (it.left == right && it.right == left) }?.equal
        }
        fun zero(value: Value?, trace: SubjectTrace): Boolean? = when (value) {
            is NumberValue -> value.number == 0L
            is Difference -> equivalent(value.left, value.right, trace)
            null -> null
            else -> trace.zeroes[value]
        }
        fun arithmetic(left: Value?, right: Value?, width: Int, subtract: Boolean): Value? = when {
            left is StackAddress && right is NumberValue && width == 8 && right.number in 0..Int.MAX_VALUE ->
                StackAddress(left.offset + right.number.toInt() * (if (subtract) -1 else 1))
            subtract && left is ObjectValue && left.kind in setOf("mount-argument", "old-path-part", "released-path-part") &&
                right is NumberValue && width == 8 && right.number in 0..Int.MAX_VALUE ->
                address(left, -right.number.toInt())
            left is NumberValue && right is NumberValue -> number(if (subtract) left.number - right.number else left.number + right.number, width)
            left is Count && right is NumberValue && left.width == width &&
                (if (width == 4) right.number.toInt().toLong() else right.number) in -1..1 ->
                left.copy(delta = left.delta + right.number.toInt() * (if (subtract) -1 else 1))
            !subtract && left is NumberValue && right is Count && right.width == width &&
                (if (width == 4) left.number.toInt().toLong() else left.number) in -1..1 ->
                right.copy(delta = right.delta + left.number.toInt())
            !subtract && width == 8 && subject == Subject.SECUREBITS && left is Address && right is NumberValue ->
                (left.offset.toLong() + right.number).takeIf { it in 0 until kernel.size.toLong() }?.let { Address(it.toInt()) }
            !subtract && width == 8 && right is NumberValue && right.number in 0..Int.MAX_VALUE -> address(left, right.number.toInt())
            else -> null
        }
        fun canAcquire(lock: ObjectValue, trace: SubjectTrace): Boolean {
            if (lock.offset <= 0) return false
            if (subject == Subject.EXIT_FS) {
                val fs = trace.inputs.singleOrNull() ?: return false
                return trace.zeroes[fs] == false &&
                    (lock.kind == "task-argument" && trace.heldLocks.isEmpty() ||
                        lock.copy(offset = 0) == fs && trace.heldLocks.size == 1 &&
                            trace.heldLocks.single().kind == "task-argument")
            }
            return lock.kind == lockOwner && trace.heldLocks.isEmpty() &&
                (subject != Subject.SET_FS || trace.pathReferences == 1)
        }
        fun acquire(lock: ObjectValue, trace: SubjectTrace): SubjectTrace {
            if (!canAcquire(lock, trace)) error(anchor, "STRUCT_FILES_LOCK")
            return trace.copy(heldLocks = trace.heldLocks + lock, usedLocks = trace.usedLocks + lock)
        }
        fun release(lock: ObjectValue, trace: SubjectTrace): SubjectTrace {
            if (lock !in trace.heldLocks) error(anchor, "STRUCT_FILES_UNLOCK")
            when (subject) {
                Subject.EXIT_FS -> {
                    val fs = trace.inputs.singleOrNull() ?: error(anchor, "STRUCT_FS_INPUT")
                    if (trace.changes.size != 1 ||
                        (ObjectValue("task-argument", offset = fs.identity) to 8) !in trace.writes ||
                        !(lock.copy(offset = 0) == fs && trace.heldLocks.size == 2 ||
                            lock.kind == "task-argument" && trace.heldLocks.size == 1)) error(anchor, "STRUCT_FS_UNLOCK_ORDER")
                }
                Subject.EXIT_FILES -> if (trace.writes.size != 1) error(anchor, "STRUCT_FILES_UNLOCK_BEFORE_DETACH")
                Subject.SET_FS -> if (trace.pathCopies.size != 2 || trace.fsSequence.size != 2)
                    error(anchor, "STRUCT_FS_UNLOCK_BEFORE_WRITE")
                else -> Unit
            }
            return trace.copy(heldLocks = trace.heldLocks - lock)
        }
        fun guard(trace: SubjectTrace, value: Value?, isZero: Boolean): SubjectTrace {
            if (value == null) return trace
            if (lockSubject && value is LockAttempt) {
                if (!canAcquire(value.address, trace)) error(anchor, "STRUCT_FILES_LOCK")
                val guarded = trace.copy(zeroes = trace.zeroes + (value to isZero))
                return if (isZero) acquire(value.address, guarded) else guarded
            }
            if (subject == Subject.SET_FS && value == ObjectValue("path-part", 8) && isZero &&
                trace.pathReferences == 0 && trace.pathPartsReferenced == setOf(0) &&
                trace.heldLocks.isEmpty() && trace.pathCopies.isEmpty())
                return trace.copy(zeroes = trace.zeroes + (value to true),
                    pathReferences = 1, pathPartsReferenced = setOf(0, 8))
            return if (value is Difference && value.left != null && value.right != null)
                trace.copy(equalities = trace.equalities + Equality(value.left, value.right, isZero))
            else trace.copy(zeroes = trace.zeroes + (value to isZero))
        }
        while (pending.isNotEmpty()) {
            // Consumed alternative words are not future machine state. Keep CPU-feature
            // choices and lifecycle histories, but merge identical continuations after them.
            // A backward edge may explore a consumed jump choice again; that widens paths.
            val queued = pending.removeLast()
            val needed = liveState[queued.at]
            val state = queued.copy(trace = if (subject == null) SubjectTrace() else queued.trace,
                view = queued.view.filterKeys { it >= queued.at },
                values = if (needed == null) queued.values else queued.values.mapIndexed { index, value ->
                    value.takeIf { needed and (1L shl index) != 0L } },
                flags = queued.flags.takeIf { needed == null || needed and (1L shl 32) != 0L },
                compared = queued.compared.takeIf { needed == null || needed and (1L shl 32) != 0L })
            // A branch fact about a dead load is not current machine state. Keep facts reachable
            // from live registers/flags, exclusive state or spilled snapshots; a future reload is
            // unconstrained, so forgetting a dead predicate widens paths rather than dropping one.
            val canonical = if (memberLocation) {
                val roots = linkedSetOf<Value>()
                fun retain(value: Value?) {
                    if (value == null || !roots.add(value)) return
                    when (value) {
                        is Difference -> { retain(value.left); retain(value.right) }
                        is AtomicStatus -> { retain(value.loaded); retain(value.desired) }
                        else -> Unit
                    }
                }
                state.values.forEach(::retain)
                retain(state.flags?.left); retain(state.flags?.right); retain(state.exclusive)
                state.trace.stackValues.values.forEach(::retain)
                state.copy(trace = state.trace.copy(zeroes = state.trace.zeroes.filterKeys { it in roots },
                    equalities = state.trace.equalities.filter { it.left in roots && it.right in roots }.toSet()))
            } else state
            val at = canonical.at
            if (at < start || at.toLong() + 4 > end || at % 4 != 0) { complete = false; continue }
            if (!visited.add(canonical)) continue
            if (visited.size > if (subject == null || memberLocation) 4096 else 32768) error(anchor, "STRUCT_ACCESSOR_COMPLEX")
            if (at !in canonical.view && at in instructionViews) {
                for (view in instructionViews.getValue(at)) {
                    val selected = view.feature?.let(canonical.features::get)
                    if (selected != null && selected != view.replacement) continue
                    val features = view.feature?.let { canonical.features + (it to view.replacement) } ?: canonical.features
                    pending += canonical.copy(view = canonical.view + view.words, features = features)
                }
                continue
            }
            val values = canonical.values.toTypedArray()
            var compared: Scalar? = null
            var flags: Flags? = canonical.flags
            var exclusive = canonical.exclusive
            var trace = canonical.trace
            attributes[at]?.let { match ->
                trace = attribute(values[match.inodeRegister], values[match.statRegister], trace)
            }
            var next = at + 4
            val word = canonical.view[at] ?: UltraKernelA64.le32Bits(kernel, at)
            if (subject != null && canonical.values.any { it is AtomicStatus }) {
                val status = word and 31
                val retry = canonical.values[status] as? AtomicStatus
                val target = at.toLong() + UltraKernelA64.signExtend(((word ushr 5) and 0x7ffff).toLong(), 19) * 4
                if (word and 0xff000000.toInt() != 0x35000000 || retry == null || target != retry.loaded.readAt.toLong())
                    error(anchor, "STRUCT_EXCLUSIVE_RETRY")
            }
            if (lockSubject && canonical.values.any { it is LockStoreStatus }) {
                val retry = canonical.values[word and 31] as? LockStoreStatus
                val target = at.toLong() + UltraKernelA64.signExtend(((word ushr 5) and 0x7ffff).toLong(), 19) * 4
                if (word and 0xff000000.toInt() != 0x35000000 || retry == null || target != retry.readAt.toLong())
                    error(anchor, "STRUCT_FILES_EXCLUSIVE_RETRY")
            }
            val rd = word and 31
            val rn = (word ushr 5) and 31
            val rm = (word ushr 16) and 31
            val width = if (word < 0) 8 else 4
            fun base(index: Int): Value? = if (index == 31 && stackSubject)
                StackAddress(trace.stackPointer) else values[index]
            fun register(index: Int): Value? {
                val value = if (index == 31) number(0, width) else values[index]
                return when {
                    // A W write zero-extends the architectural X value; provenance width is not its numeric value.
                    memberLocation && width == 8 && value is NumberValue -> number(value.number, 8)
                    width == 8 -> value
                    value is NumberValue -> number(value.number, width)
                    value is Scalar && value.width <= width -> when {
                        value.width == width -> value.copy(signedToBytes = 0)
                        value.signedToBytes > width -> value.copy(signedToBytes = width)
                        else -> value
                    }
                    value is Count && value.width <= width -> value
                    value is Difference && value.width <= width -> value
                    value is LockAttempt && width == 4 -> value
                    value is LockStoreStatus && width == 4 -> value
                    else -> null
                }
            }
            fun load(destination: Value?, size: Int, signedToBytes: Int = 0): Value? = when (destination) {
                is StackAddress -> trace.stackValues[SubjectField(destination.offset, size)]
                is RegisterFrame -> if (size == 8 && signedToBytes == 0 && destination.offset in setOf(0, 8, 16))
                    UserPointer(destination.offset / 8) else null
                is Task -> if (size == 8) Pointer(destination.offset) else Scalar(null, destination.offset, size, signedToBytes)
                is Pointer -> if (size == 4 || subject == Subject.CAPGET && size == 8)
                    Scalar(destination.field, destination.offset, size, signedToBytes) else null
                is Address -> if (size == 4 && destination.offset in overflow) IdOverflow else
                    if (subject == Subject.EXIT_FS && size == 8 && destination.offset == fsCache) ObjectValue("fs-cache") else null
                is ReadOnlyAddress -> {
                    if (size != 4 || signedToBytes != 0 || destination.offset % 4 != 0 ||
                        destination.offset < destination.lower || destination.offset.toLong() + size > destination.upper)
                        error(anchor, "STRUCT_READONLY_BOUNDS")
                    number(UltraKernelA64.le32Bits(kernel, destination.offset).toLong(), 4)
                }
                is ObjectValue -> when {
                    subject == Subject.POSIX_FILE_LOCK && destination.kind == "lock-file-argument" && size == 8 &&
                        destination.identity == 0 && destination.offset >= 0 && destination.offset % 8 == 0 ->
                        ObjectValue("lock-inode", destination.offset)
                    subject == Subject.SIMPLE_GETATTR && destination.kind == "attribute-path" && size == 8 &&
                        destination.offset >= 0 && destination.offset % 8 == 0 ->
                        ObjectValue("attribute-dentry", destination.offset)
                    subject == Subject.SIMPLE_GETATTR && destination.kind == "attribute-dentry" && size == 8 &&
                        destination.offset >= 0 && destination.offset % 8 == 0 ->
                        ObjectValue("attribute-inode", destination.offset)
                    subject == Subject.GET_MM && destination.kind == "task-argument" && size == 8 -> {
                        if (trace.heldLocks.size != 1 || trace.heldLocks.single().kind != "task-argument")
                            error(anchor, "STRUCT_MM_UNPROTECTED_READ")
                        val mm = ObjectValue("candidate-mm", destination.offset)
                        trace = trace.copy(inputs = trace.inputs + mm)
                        mm
                    }
                    subject == Subject.EXIT_FS && destination.kind == "task-argument" && size == 8 -> {
                        if (trace.writes.any { it.first == destination }) error(anchor, "STRUCT_FS_INPUT_RELOAD")
                        val fs = ObjectValue("candidate-fs", destination.offset)
                        trace = trace.copy(inputs = trace.inputs + fs)
                        fs
                    }
                    subject == Subject.EXIT_FS && destination.kind == "candidate-fs" && size == 8 -> {
                        val count = trace.changes.singleOrNull()?.desired ?: error(anchor, "STRUCT_FS_USERS_CHANGE")
                        if (trace.heldLocks.isNotEmpty() || trace.zeroes[count] != true ||
                            trace.releases.isNotEmpty()) error(anchor, "STRUCT_FS_RELEASED_PATH_READ")
                        ObjectValue("released-path-part", destination.offset)
                    }
                    subject == Subject.SET_FS && destination.kind == "path-argument" && size == 8 &&
                        destination.offset in setOf(0, 8) -> ObjectValue("path-part", destination.offset)
                    subject == Subject.SET_FS && destination.kind == "fs-argument" && size == 8 -> {
                        if (trace.heldLocks.size != 1 || trace.fsSequence.size != 1 ||
                            trace.pathCopies.any { it.first < destination.offset.toLong() + size &&
                                destination.offset < it.first.toLong() + 8 }) error(anchor, "STRUCT_FS_OLD_PATH_READ")
                        ObjectValue("old-path-part", destination.offset)
                    }
                    subject == Subject.CAPABLE && destination.kind == "cred-argument" && size == 8 ->
                        ObjectValue("candidate-user-ns", destination.offset)
                    subject == Subject.EXIT_FILES && destination.kind == "task-argument" && size == 8 -> {
                        if (trace.writes.any { (old, bytes) -> old.kind == "task-argument" &&
                            old.offset < destination.offset.toLong() + size &&
                            destination.offset < old.offset.toLong() + bytes }) null else {
                            val input = ObjectValue("candidate-files", destination.offset)
                            trace = trace.copy(inputs = trace.inputs + input)
                            input
                        }
                    }
                    subject == Subject.GROUPS && destination.kind == "cred-argument" && size == 8 ->
                        ObjectValue("old-group", destination.offset)
                    subject == Subject.GET_CRED && destination.kind == "task-argument" && size == 8 ->
                        ObjectValue("candidate-cred", destination.offset)
                    size in setOf(4, 8) -> Count(destination, size, at)
                    else -> null
                }
                else -> null
            }
            fun mmReference(loaded: Count, desired: Count) {
                if (subject != Subject.GET_MM) return
                val mm = trace.inputs.singleOrNull() ?: error(anchor, "STRUCT_MM_INPUT")
                if (trace.heldLocks.size != 1 || trace.zeroes[mm] != false ||
                    loaded.address.copy(offset = 0) != mm || loaded.address != desired.address ||
                    loaded.width != 4 || desired.width != 4 || loaded.delta != 0 || desired.delta != 1 ||
                    trace.changes.isNotEmpty()) error(anchor, "STRUCT_MM_REFERENCE")
            }
            fun store(destination: Value?, value: Value?, size: Int) {
                if (stackSubject && destination is StackAddress) {
                    val slots = trace.stackValues.filterKeys { old -> old.offset.toLong() + old.width <= destination.offset ||
                        destination.offset.toLong() + size <= old.offset }
                    trace = trace.copy(stackValues = if (value == null) slots else
                        slots + (SubjectField(destination.offset, size) to value))
                    return
                }
                if (subject == Subject.ALLOCATE_EMPTY_GROUPS) {
                    val address = destination as? ObjectValue ?: error(anchor, "STRUCT_ALLOCATION_WRITE")
                    val allocation = address.copy(offset = 0)
                    val extent = trace.allocationSizes[allocation] ?: error(anchor, "STRUCT_ALLOCATION_WRITE")
                    if (address.offset < 0 ||
                        address.offset.toLong() + size > extent || trace.zeroes[allocation] != false)
                        error(anchor, "STRUCT_ALLOCATION_HEADER")
                }
                if (subject == Subject.COMM_LOCATION && destination is IndexedComm) {
                    val copy = trace.commCopies.singleOrNull { it.address == destination.address && it.length == destination.index }
                        ?: error(anchor, "STRUCT_COMM_TERMINATOR_INDEX")
                    if (size != 1 || value !is NumberValue || value.number != 0L)
                        error(anchor, "STRUCT_COMM_TERMINATOR_WRITE")
                    trace = trace.copy(commTerminated = trace.commTerminated + copy)
                    return
                }
                if (subject == Subject.POSIX_FILE_LOCK) error(anchor, "STRUCT_LOCK_FORWARD_WRITE")
                if (subject == Subject.SIMPLE_GETATTR && (destination !is ObjectValue ||
                    destination.kind != "attribute-stat" || destination.identity != 0 || destination.offset < 0))
                    error(anchor, "STRUCT_ATTRIBUTE_WRITE")
                if (subject == Subject.TRUNCATE_SIZE) {
                    if (destination !is ObjectValue || destination.kind != "inode-argument" || destination.identity != 0 ||
                        destination.offset < 0 || destination.offset % 8 != 0 || size != 8 ||
                        value != ObjectValue("new-size-argument") || trace.writes.isNotEmpty())
                        error(anchor, "STRUCT_SIZE_WRITE")
                    trace = trace.copy(sizeStores = setOf(destination.offset))
                }
                if (subject == Subject.SET_POSITION) {
                    if (destination !is ObjectValue || destination.kind != "file-argument" || destination.identity != 0 ||
                        destination.offset < 0 || destination.offset % 8 != 0 || size != 8)
                        error(anchor, "STRUCT_POSITION_WRITE")
                    trace = when (value) {
                        ObjectValue("position-argument") -> trace.copy(positionStores = trace.positionStores + destination.offset)
                        NumberValue(0L, 8) -> trace.copy(versionClears = trace.versionClears + destination.offset)
                        else -> error(anchor, "STRUCT_POSITION_VALUE")
                    }
                }
                if (subject == Subject.MNT_PUT && destination is ObjectValue) {
                    if (destination.kind != "mount-argument" || size != 4 || value !is NumberValue ||
                        value.number != 0L || value.width < size) error(anchor, "STRUCT_MNTPUT_WRITE")
                    trace = trace.copy(alternativeWrites = trace.alternativeWrites + Triple(destination, 0L, size))
                }
                if (strict && (destination is Task || destination is Pointer)) error(anchor,
                    "STRUCT_ACCESSOR_INPUT_WRITE:" + at.toString(16) + ":" + destination + ":source=" +
                        (taskArgument?.let { "argument$it" } ?: "current") + ":bytes=" + size)
                if (lockSubject && destination is ObjectValue && size == 1 &&
                    value is NumberValue && value.number == 0L && value.width >= size && destination in trace.heldLocks) {
                    trace = release(destination, trace)
                    return
                }
                if (subject == Subject.EXIT_FS && destination is ObjectValue) {
                    if (destination.kind == "released-path-part") {
                        if (trace.heldLocks.isNotEmpty() || destination.identity + 8 !in trace.pathDentriesReleased ||
                            value !is NumberValue || value.number != 0L || value.width < size ||
                            (destination.offset to size) !in mountPutEvidence!!.second)
                            error(anchor, "STRUCT_FS_MNTPUT_WRITE")
                        return
                    }
                    val fs = trace.inputs.singleOrNull() ?: error(anchor, "STRUCT_FS_INPUT")
                    if (trace.heldLocks.size != 2 || trace.writes.any { it.first == destination } || trace.zeroes[fs] != false)
                        error(anchor, "STRUCT_FS_UNPROTECTED_WRITE")
                    if (trace.usedLocks.any { lock -> lock.copy(offset = 0) == destination.copy(offset = 0) &&
                        lock.offset < destination.offset.toLong() + size && destination.offset < lock.offset.toLong() + 4 })
                        error(anchor, "STRUCT_FS_LOCK_OVERWRITE")
                    when {
                        destination == ObjectValue("task-argument", offset = fs.identity) && size == 8 && value == NumberValue(0, 8) -> Unit
                        destination.copy(offset = 0) == fs && size == 4 && value is Count && value.address == destination &&
                            value.width == 4 && value.delta == -1 && trace.changes.isEmpty() ->
                            trace = trace.copy(changes = setOf(Change(value.copy(delta = 0), value)))
                        else -> error(anchor, "STRUCT_FS_UNPROVEN_WRITE")
                    }
                }
                if (subject == Subject.SET_FS && destination is ObjectValue) {
                    if (destination.kind == "old-path-part") {
                        val first = trace.pathCopies.singleOrNull { it.second == 0 }?.first
                            ?: error(anchor, "STRUCT_FS_PATH_FIRST")
                        if (destination.identity != first || trace.heldLocks.isNotEmpty() || trace.fsSequence.size != 2 ||
                            first + 8 !in trace.pathPartsReleased || value !is NumberValue || value.number != 0L ||
                            value.width < size || (destination.offset to size) !in mountPutEvidence!!.second)
                            error(anchor, "STRUCT_FS_MNTPUT_WRITE")
                        return
                    }
                    if (destination.kind != "fs-argument" || trace.heldLocks.size != 1 || trace.pathReferences != 1)
                        error(anchor, "STRUCT_FS_UNPROVEN_WRITE")
                    if (trace.usedLocks.any { lock -> lock.kind == destination.kind &&
                        lock.offset < destination.offset.toLong() + size && destination.offset < lock.offset.toLong() + 4 })
                        error(anchor, "STRUCT_FS_LOCK_OVERWRITE")
                    when {
                        size == 4 && value is Count && value.width == 4 && value.address == destination && value.delta == 1 -> {
                            if (trace.fsSequence.size > 1 || trace.fsSequence.firstOrNull()?.let { it != destination.offset } == true ||
                                trace.fsSequence.isEmpty() && trace.pathCopies.isNotEmpty() ||
                                trace.fsSequence.size == 1 && trace.pathCopies.size != 2)
                                error(anchor, "STRUCT_FS_SEQUENCE_WRITE")
                            trace = trace.copy(fsSequence = trace.fsSequence + destination.offset)
                        }
                        size == 8 && value is ObjectValue && value.kind == "path-part" && value.offset == 0 &&
                            value.identity in setOf(0, 8) && trace.fsSequence.size == 1 -> {
                            if (trace.pathCopies.any { it.first == destination.offset || it.second == value.identity })
                                error(anchor, "STRUCT_FS_PATH_OVERWRITE")
                            trace = trace.copy(pathCopies = trace.pathCopies + (destination.offset to value.identity))
                        }
                        else -> error(anchor, "STRUCT_FS_UNPROVEN_WRITE")
                    }
                }
                if (subject == Subject.GET_MM && destination is ObjectValue)
                    error(anchor, "STRUCT_MM_UNPROVEN_WRITE")
                if (subject == Subject.EXIT_FILES && destination is ObjectValue &&
                    (destination.kind != "task-argument" || size != 8 ||
                        value != NumberValue(0, 8) || trace.heldLocks.size != 1 ||
                        trace.writes.isNotEmpty() || trace.heldLocks.single().offset < destination.offset.toLong() + size &&
                            destination.offset < trace.heldLocks.single().offset.toLong() + 4 ||
                        trace.inputs.none { it.identity == destination.offset && trace.zeroes[it] == false }))
                    error(anchor, "STRUCT_FILES_UNPROVEN_WRITE:" + at.toString(16) + ":destination=" +
                        destination + ":value=" + value + ":bytes=" + size + ":locks=" + trace.heldLocks)
                stored(destination, value, size)
                if (subject == Subject.CAPGET && destination is UserPointer) {
                    val scalar = value as? Scalar ?: error(anchor, "STRUCT_CAPGET_OUTPUT")
                    trace = trace.copy(outputCopies = trace.outputCopies +
                        OutputCopy(destination.argument, destination.offset, scalar, size))
                }
                if (subject in setOf(Subject.ALLOCATE_GROUPS, Subject.ALLOCATE_EMPTY_GROUPS, Subject.SECUREBITS) &&
                    (destination is ReadOnlyAddress || destination is Address && inRodata(destination.offset)))
                    error(anchor, "STRUCT_READONLY_WRITE")
                if (destination is ObjectValue) {
                    val survivors = trace.initialised.filterNot { (old, bytes) ->
                        old.copy(offset = 0) == destination.copy(offset = 0) &&
                            old.offset < destination.offset.toLong() + size && destination.offset < old.offset.toLong() + bytes }.toSet()
                    trace = trace.copy(writes = trace.writes + (destination to size), initialised = survivors)
                }
                if (subject == Subject.ALTERNATIVE) {
                    val target = destination as? ObjectValue ?: error(anchor, "STRUCT_ALTERNATIVE_CALLBACK_ADDRESS")
                    val constant = value as? NumberValue ?: error(anchor, "STRUCT_ALTERNATIVE_CALLBACK_DATA")
                    trace = trace.copy(alternativeWrites = trace.alternativeWrites + Triple(target, constant.number, size))
                }
                if (subject in setOf(Subject.ALLOCATE_GROUPS, Subject.ALLOCATE_EMPTY_GROUPS) && destination is ObjectValue &&
                    destination.kind == "allocated-group" && value is NumberValue && value.number == 1L &&
                    size in setOf(4, 8)) trace = trace.copy(initialised = trace.initialised + (destination to size))
                if (subject == Subject.GROUPS && destination is ObjectValue && destination.kind == "cred-argument" &&
                    value == ObjectValue("new-group") && size == 8) {
                    val old = ObjectValue("old-group", destination.offset)
                    val decrements = trace.changes.filter { it.desired.address.copy(offset = 0) == old && it.desired.delta == -1 }
                    val matches = decrements.mapNotNull { decrement ->
                        val increment = trace.changes.any { change -> change.desired.address ==
                            ObjectValue("new-group", offset = decrement.desired.address.offset) &&
                            change.desired.width == decrement.desired.width && change.desired.delta == 1 }
                        val oldZero = zero(decrement.desired, trace)
                        if (increment && (oldZero == false || oldZero == true && old in trace.releases))
                            SubjectField(destination.offset, 8) to SubjectField(decrement.desired.address.offset, decrement.desired.width)
                        else null
                    }.distinct()
                    val field = matches.singleOrNull() ?: error(CRED_GROUP_INFO, "STRUCT_GROUP_SEQUENCE")
                    if (trace.writes.any { (destination, width) -> destination.kind in setOf("old-group", "new-group") &&
                        destination.offset < field.second.offset.toLong() + field.second.width &&
                        field.second.offset < destination.offset.toLong() + width }) error(anchor, "STRUCT_SUBJECT_OVERWRITE")
                    trace = trace.copy(groupFields = trace.groupFields + field)
                }
            }
            fun finish(value: Value?) { returned(value); subjectReturned(value, trace) }
            when {
                // A fixed SCS LR spill is not an input write; an aliased X18 is still rejected.
                subject in setOf(Subject.TRUNCATE_SIZE, Subject.SIMPLE_GETATTR, Subject.POSIX_FILE_LOCK, Subject.ALLOCATE_EMPTY_GROUPS, Subject.COMM_LOCATION, Subject.TRACE_LOCATION) &&
                    word == 0xf800865e.toInt() && values[18] == null -> Unit
                word and 0xffffffe0.toInt() == UltraKernelA64.MRS_SP_EL0 ->
                    // An exit_files task argument is distinct from this CPU's current task.
                    values[rd] = if (memberLocation) ObjectValue("location-current-task")
                    else if (taskArgument == null && !lockSubject && subject != Subject.EXIT_FS) Task() else null
                // MRS has one GPR destination. Unknown system-register data stays opaque on every branch.
                strict && word and 0xfff00000.toInt() == 0xd5300000.toInt() -> values[rd] = null
                // PAN toggles privileged user access; neither encoding changes a GPR or NZCV.
                strict && word in setOf(0xd500409f.toInt(), 0xd500419f.toInt()) -> Unit
                // The target DAIFSet/DAIFClr #2 window changes interrupt masks, not GPRs or NZCV.
                strict && word in setOf(0xd50342df.toInt(), 0xd50342ff.toInt()) -> Unit
                // Target IRQ alternatives switch DAIF or ICC_PMR_EL1; both read a GPR, not NZCV.
                word and 0xffffffe0.toInt() in setOf(0xd51b4220.toInt(), 0xd5184600.toInt()) -> Unit
                // The target user-access windows write S3_0_C2_C0_{0,1}; no GPR or NZCV is written.
                word and 0xffffffe0.toInt() in setOf(0xd5182000.toInt(), 0xd5182020.toInt()) -> Unit
                subject in setOf(Subject.ALLOCATE_GROUPS, Subject.ALLOCATE_EMPTY_GROUPS) && word and 0xffffffe0.toInt() == 0xd5380000.toInt() ->
                    values[rd] = null // MIDR_EL1 is a runtime value; every possible CPU branch remains reachable.
                word == UltraKernelA64.RET -> { finish(values[0]); continue }
                word and 0xffe0001f.toInt() == 0xd4200000.toInt() -> { if (strict && subject == null) complete = false; subjectTrapped(trace); continue }
                word and 0xffc00000.toInt() == 0xf9800000.toInt() -> Unit
                word and 0xfffff01f.toInt() == 0xd503201f.toInt() ||
                    word and 0xfffff09f.toInt() == 0xd503309f.toInt() -> Unit
                UltraKernelA64.isAdr(word) || UltraKernelA64.isAdrp(word) -> {
                    val immediate = (((word ushr 5) and 0x7ffff).toLong() shl 2) or ((word ushr 29) and 3).toLong()
                    val target = if (UltraKernelA64.isAdrp(word)) (at.toLong() and -4096L) +
                        (UltraKernelA64.signExtend(immediate, 21) shl 12) else at.toLong() + UltraKernelA64.signExtend(immediate, 21)
                    values[rd] = if (target in 0 until kernel.size.toLong() || subject == Subject.EXIT_FS &&
                        fsCache != null && target == (fsCache.toLong() and -4096L)) Address(target.toInt()) else null
                }
                word and 0x7f800000 == 0x52800000 || word and 0x7f800000 == 0x12800000 ||
                    word and 0x7f800000 == 0x72800000 -> {
                    val shift = ((word ushr 21) and 3) * 16
                    if (width == 4 && shift >= 32) values[rd] = null
                    else {
                        val immediate = ((word ushr 5) and 0xffff).toLong() shl shift
                        values[rd] = when (word and 0x7f800000) {
                            0x52800000 -> number(immediate, width)
                            0x12800000 -> number(immediate.inv(), width)
                            else -> (values[rd] as? NumberValue)?.let {
                                number((it.number and (0xffffL shl shift).inv()) or immediate, width) }
                        }
                    }
                }
                subject == Subject.ALLOCATE_EMPTY_GROUPS && word and 0x7f800000 == 0x53000000 -> {
                    val bits = width * 8
                    val rotate = (word ushr 16) and 63
                    val last = (word ushr 10) and 63
                    if (((word ushr 22) and 1) != (if (width == 8) 1 else 0) || rotate >= bits || last >= bits)
                        error(anchor, "STRUCT_ALLOCATION_BITFIELD")
                    fun ones(count: Int) = if (count == 64) -1L else (1L shl count) - 1
                    values[rd] = (register(rn) as? NumberValue)?.let { input -> number(
                        if (last >= rotate) (input.number ushr rotate) and ones(last - rotate + 1)
                        else (input.number and ones(last + 1)) shl (bits - rotate), width) }
                }
                word and 0x7fe0ffe0 == 0x2a0003e0 -> {
                    val value = register(rm)
                    values[rd] = value.takeIf { width == 8 || it is Scalar || it is NumberValue ||
                        it is Count && it.width == 4 || it is LockAttempt || it is LockStoreStatus }
                }
                word and 0x7fe00c00 == 0x1a800000 -> {
                    val left = if (values[rn] == IdOverflow) IdOverflow else register(rn)
                    val right = if (values[rm] == IdOverflow) IdOverflow else register(rm)
                    values[rd] = when {
                        left == right -> left
                        left is Scalar && right == IdOverflow && canonical.compared == left && (word ushr 12) and 15 == 1 -> left
                        right is Scalar && left == IdOverflow && canonical.compared == right && (word ushr 12) and 15 == 0 -> right
                        subject == Subject.COMM_LOCATION && width == 8 && flags?.subtract == true && flags.width == 8 &&
                            flags.left is StringLength && (flags.left as StringLength).maximum == null &&
                            flags.right is NumberValue && (flags.right as NumberValue).number in 0 until Int.MAX_VALUE.toLong() &&
                            ((left == flags.left && right == flags.right && (word ushr 12) and 15 in setOf(3, 9)) ||
                                (right == flags.left && left == flags.right && (word ushr 12) and 15 in setOf(2, 8))) ->
                            (flags.left as StringLength).copy(maximum = (flags.right as NumberValue).number.toInt())
                        userArguments && width == 8 && left is UserPointer && rm == 31 -> left
                        userArguments && width == 8 && right is UserPointer && rn == 31 -> right
                        else -> null
                    }
                }
                word and 0x1f800000 == 0x11000000 -> {
                    val immediate = ((word ushr 10) and 0xfff).toLong() shl (if (word and (1 shl 22) != 0) 12 else 0)
                    val right = number(immediate, width); val left = if (rn == 31) base(rn) else register(rn)
                    val subtract = word and (1 shl 30) != 0
                    // The munged-ID CMN idiom also replaces NZCV, just like every ADDS.
                    if (word and 0xfffffc1f.toInt() == 0x3100041f) compared = left as? Scalar
                    values[rd] = arithmetic(left, right, width, subtract)
                    if (stackSubject && rd == 31 && word and (1 shl 29) == 0) {
                        val stack = values[rd] as? StackAddress ?: error(anchor, "STRUCT_FS_STACK_POINTER")
                        trace = trace.copy(stackPointer = stack.offset)
                    }
                    if (word and (1 shl 29) != 0) flags = Flags(left, right, width, subtract)
                }
                word and 0x1f200000 == 0x0b000000 && (word ushr 22) and 3 == 0 -> {
                    val left = register(rn)
                    val shift = (word ushr 10) and 0x3f
                    val rawRight = register(rm)
                    val right = if (shift == 0) rawRight else (rawRight as? NumberValue)?.let { number(it.number shl shift, width) }
                    val subtract = word and (1 shl 30) != 0
                    values[rd] = arithmetic(left, right, width, subtract)
                    if (word and (1 shl 29) != 0) flags = Flags(left, right, width, subtract)
                }
                word and 0x7fe0fc00 == 0x4a000000 -> {
                    val left = register(rn); val right = register(rm)
                    values[rd] = if (left is NumberValue && right is NumberValue) number(left.number xor right.number, width)
                        else Difference(left, right, width)
                }
                word and 0x3ffffc00 == 0x08dffc00 || word and 0x3ffffc00 == 0x38bfc000 ->
                    values[rd] = load(address(base(rn), 0), 1 shl ((word ushr 30) and 3))
                word and 0x3ffffc00 == 0x089ffc00 -> {
                    val size = 1 shl ((word ushr 30) and 3)
                    store(address(base(rn), 0), if (rd == 31) number(0, size) else values[rd], size)
                }
                word and 0x3fff7c00 == 0x085f7c00 -> {
                    val size = 1 shl ((word ushr 30) and 3)
                    val loaded = load(address(base(rn), 0), size) as? Count
                    values[rd] = loaded; exclusive = loaded
                }
                word and 0x3fe07c00 == 0x08007c00 -> {
                    val size = 1 shl ((word ushr 30) and 3)
                    val desired = values[rd] as? Count
                    val loaded = exclusive
                    if (lockSubject && !(subject == Subject.GET_MM && loaded?.address?.kind == "candidate-mm")) {
                        if (size != 4 || loaded == null || loaded.width != 4 || loaded.delta != 0 ||
                            !canAcquire(loaded.address, trace) ||
                            loaded.address != address(base(rn), 0) || values[rd] != NumberValue(1, 4) ||
                            trace.zeroes[loaded] != true)
                            error(anchor, "STRUCT_FILES_EXCLUSIVE_ACQUIRE")
                        values[rm] = LockStoreStatus(loaded.address, loaded.readAt)
                    } else {
                        if (subject != null && !memberLocation && (loaded == null || desired == null || loaded.width != size || desired.width != size ||
                            loaded.address != address(base(rn), 0) || desired.address != loaded.address ||
                            desired.delta !in setOf(-1, 1) || equivalent(loaded, desired.copy(delta = 0), trace) != true))
                            error(anchor, "STRUCT_EXCLUSIVE_STORE")
                        if (subject == Subject.GET_CRED && desired != null &&
                            trace.zeroes[desired.copy(delta = 0)] != false) error(anchor, "STRUCT_CRED_NONZERO_GUARD")
                        if (loaded != null && desired != null) mmReference(loaded, desired)
                        values[rm] = if (loaded != null && desired != null) AtomicStatus(loaded, desired) else null
                    }
                }
                word and 0x3f20fc00 == 0x38200000 -> {
                    val size = 1 shl ((word ushr 30) and 3)
                    val source = values[rm] as? NumberValue
                    val loaded = load(address(base(rn), 0), size) as? Count
                    val signed = source?.number?.let { if (size == 4) it.toInt().toLong() else it }
                    if (subject != null && !memberLocation && (loaded == null || signed !in setOf(-1L, 1L))) error(anchor, "STRUCT_ATOMIC_ADD")
                    values[rd] = loaded
                    if (loaded != null && signed != null) {
                        val desired = loaded.copy(delta = signed.toInt())
                        mmReference(loaded, desired)
                        trace = trace.copy(changes = trace.changes + Change(loaded, desired))
                    }
                }
                word and 0x3fa07c00 == 0x08a07c00 -> {
                    val size = 1 shl ((word ushr 30) and 3)
                    val expected = values[rm] as? Count
                    val desired = values[rd] as? Count
                    val loaded = load(address(base(rn), 0), size) as? Count
                    if (lockSubject && !(subject == Subject.GET_MM && loaded?.address?.kind == "candidate-mm")) {
                        if (size != 4 || loaded == null || !canAcquire(loaded.address, trace) ||
                            values[rm] != NumberValue(0, 4) || values[rd] != NumberValue(1, 4) ||
                            loaded.delta != 0) error(anchor, "STRUCT_FILES_COMPARE_ACQUIRE:" + at.toString(16) +
                                ":loaded=" + loaded + ":expected=" + values[rm] + ":desired=" + values[rd] +
                                ":locks=" + trace.heldLocks + ":refs=" + trace.pathReferences + ":features=" + canonical.features)
                        values[rm] = LockAttempt(loaded.address)
                    } else {
                        if (subject != null && !memberLocation && (expected == null || desired == null || loaded == null ||
                            desired.copy(delta = 0) != expected || desired.delta != 1 || loaded.address != expected.address ||
                            loaded.width != expected.width || desired.width != size)) error(anchor, "STRUCT_COMPARE_EXCHANGE")
                        if (subject == Subject.GET_CRED && expected != null && trace.zeroes[expected] != false)
                            error(anchor, "STRUCT_CRED_NONZERO_GUARD")
                        if (loaded != null && desired != null) mmReference(loaded, desired)
                        values[rm] = loaded
                        if (expected != null && desired != null && loaded != null) trace = trace.copy(conditionalChanges =
                            trace.conditionalChanges + ConditionalChange(Equality(loaded, expected, true), Change(expected, desired)))
                    }
                }
                word and 0x3b000000 == 0x39000000 && word and (1 shl 26) == 0 -> {
                    val size = 1 shl ((word ushr 30) and 3); val opcode = (word ushr 22) and 3
                    if (opcode == 3 && size >= 4) error(anchor, "STRUCT_LOAD_ENCODING")
                    val offset = ((word ushr 10) and 0xfff) * size
                    if (opcode != 0) {
                        if (subject in setOf(Subject.ALLOCATE_GROUPS, Subject.ALLOCATE_EMPTY_GROUPS) && opcode == 1 && size == 4) {
                            val base = values[rn] as? Address
                            if (base != null) {
                                if (!symbols.isSymbolStart(base.offset)) error(anchor, "STRUCT_READONLY_COMPONENT")
                                values[rn] = readOnlyComponent(base.offset)
                            } else if (values[rn] !is ReadOnlyAddress && values[rn] !is ObjectValue)
                                error(anchor, "STRUCT_READONLY_ADDRESS")
                        }
                        if (subject != null && opcode != 1 && values[rn] is ObjectValue) error(anchor, "STRUCT_LOAD_EXTENSION")
                        values[rd] = load(address(base(rn), offset), size, if (opcode >= 2) (if (opcode == 2) 8 else 4) else 0)
                    }
                    else store(address(base(rn), offset), if (rd == 31) number(0, size) else values[rd], size)
                }
                // Only this constant-option accessor consumes immutable switch entries.
                subject == Subject.SECUREBITS && word and 0xffe0fc00.toInt() in
                    setOf(0xb8a07800.toInt(), 0x38606800) -> {
                    val base = values[rn] as? Address ?: error(anchor, "STRUCT_SWITCH_ADDRESS")
                    val index = values[rm] as? NumberValue ?: error(anchor, "STRUCT_SWITCH_INDEX")
                    if (index.number !in 0..Int.MAX_VALUE.toLong()) error(anchor, "STRUCT_SWITCH_INDEX")
                    val size = if (word and 0xffe0fc00.toInt() == 0x38606800) 1 else 4
                    val table = readOnlyComponent(base.offset)
                    val entry = base.offset.toLong() + index.number * size
                    if (entry % size != 0L || entry < table.lower || entry + size > table.upper)
                        error(anchor, "STRUCT_SWITCH_BOUNDS")
                    values[rd] = if (size == 1) number((kernel[entry.toInt()].toInt() and 255).toLong(), 4)
                        else NumberValue(UltraKernelA64.le32Bits(kernel, entry.toInt()).toLong(), 8)
                }
                word and 0x3b200c00 == 0x38200800 && word and (1 shl 26) == 0 -> {
                    val size = 1 shl ((word ushr 30) and 3)
                    val opcode = (word ushr 22) and 3
                    val option = (word ushr 13) and 7
                    if (option !in setOf(2, 3, 6, 7) || opcode == 3 && size >= 4)
                        error(anchor, "STRUCT_INDEXED_ENCODING")
                    if (!(size == 8 && opcode == 2)) { // PRFM has no architectural destination.
                        val index = if (rm == 31) number(0, 8) else values[rm] as? NumberValue
                        val extended = index?.number?.let { when (option) {
                            2 -> it and 0xffffffffL; 6 -> it.toInt().toLong(); else -> it } }
                        val offset = extended?.let { it shl (if (word and (1 shl 12) == 0) 0 else (word ushr 30) and 3) }
                        if (offset != null && offset !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
                            error(anchor, "STRUCT_INDEXED_BOUNDS")
                        val commIndex = (values[rm] as? StringLength)?.takeIf { subject == Subject.COMM_LOCATION &&
                            it.maximum != null && option == 3 && word and (1 shl 12) == 0 && size == 1 && opcode == 0 }
                        if (commIndex != null) {
                            val destination = values[rn] as? ObjectValue ?: error(anchor, "STRUCT_COMM_TERMINATOR_ADDRESS")
                            store(IndexedComm(destination, commIndex), if (rd == 31) number(0, 1) else values[rd], 1)
                        } else if (opcode == 0 && offset == null && values[rn] != null)
                            error(anchor, "STRUCT_INDEXED_ADDRESS_UNPROVEN:" + at.toString(16) +
                                ":base=" + values[rn] + ":index=" + values[rm] + ":option=" + option + ":bytes=" + size)
                        val target = offset?.let { address(base(rn), it.toInt()) }
                        if (commIndex != null) Unit
                        else if (opcode == 0) store(target, if (rd == 31) number(0, size) else values[rd], size)
                        else {
                            if (subject != null && opcode != 1 && values[rn] is ObjectValue)
                                error(anchor, "STRUCT_LOAD_EXTENSION")
                            values[rd] = load(target, size, if (opcode >= 2) (if (opcode == 2) 8 else 4) else 0)
                        }
                    }
                }
                word and 0x7c000000 == 0x14000000 -> {
                    val target = at + (UltraKernelA64.signExtend((word and 0x03ffffff).toLong(), 26) * 4).toInt()
                    if (!UltraKernelA64.isBl(word) && target in start until end) next = target
                    else {
                        if ((subject != null || strict) && !memberLocation && target !in knownCalls) error(anchor, "STRUCT_SUBJECT_CALL:" + at.toString(16) + ":" + target.toString(16) + ":" + (symbols.nameAt(target) ?: "unknown"))
                        if (target in trappedCalls) { subjectTrapped(trace); continue }
                        invoked(target, values, trace)
                        if (memberLocation && target !in knownCalls && values.take(9).any { it is StackAddress })
                            trace = trace.copy(stackValues = emptyMap())
                        var output = called(target, values)
                        if (subject == Subject.COMM_LOCATION) {
                            val readers = COMM_READERS.flatMap(symbols::offsetsOf).toSet()
                            val copiers = COPIERS.flatMap(symbols::offsetsOf).toSet()
                            if (target in readers) {
                                val source = values[0] as? ObjectValue
                                if (source == ObjectValue("comm-source")) output = StringLength(source)
                            } else if (target in copiers) {
                                val destination = (values[0] as? ObjectValue)?.takeIf { it.kind == "comm-task" &&
                                    it.identity == 0 && it.offset >= 0 }
                                if (destination != null) {
                                    if (values[1] != ObjectValue("comm-source")) error(anchor, "STRUCT_COMM_COPY_ARGUMENTS")
                                    val length = values[2]
                                    val stringCopy = target in listOf("strlcpy", "strscpy", "strscpy_pad", "sized_strscpy")
                                        .flatMap(symbols::offsetsOf)
                                    val bytes = when {
                                        stringCopy && length is NumberValue && length.number in 1..Int.MAX_VALUE.toLong() -> length.number.toInt()
                                        !stringCopy && target in listOf("memcpy", "memmove").flatMap(symbols::offsetsOf) &&
                                            length is StringLength && length.source == values[1] && length.maximum != null -> length.maximum + 1
                                        else -> error(anchor, "STRUCT_COPY_EXTENT_UNPROVEN")
                                    }
                                    val copy = CommCopy(destination, bytes, length as? StringLength)
                                    trace = trace.copy(commCopies = trace.commCopies + copy,
                                        commTerminated = if (stringCopy) trace.commTerminated + copy else trace.commTerminated)
                                    output = if (!stringCopy) destination else null
                                }
                            }
                        }
                        if (subject == Subject.POSIX_FILE_LOCK) {
                            val inode = (values[0] as? ObjectValue)?.takeIf { it.kind == "lock-inode" && it.offset == 0 }
                                ?: error(anchor, "STRUCT_LOCK_INODE_ARGUMENT")
                            if (values[1] != ObjectValue("lock-request-argument") ||
                                values[2] != ObjectValue("lock-conflict-argument") || trace.lockInodes.isNotEmpty())
                                error(anchor, "STRUCT_LOCK_FORWARD_ARGUMENTS")
                            trace = trace.copy(lockInodes = setOf(inode.identity))
                            output = ObjectValue("posix-lock-result")
                        }
                        if (subject == Subject.SIMPLE_GETATTR)
                            trace = attribute(values[0], values[1], trace)
                        if (subject == Subject.TRUNCATE_SIZE && target in pageTruncates) {
                            val mapping = values[0] as? Count ?: error(anchor, "STRUCT_SIZE_MAPPING")
                            val offset = trace.sizeStores.singleOrNull() ?: error(anchor, "STRUCT_SIZE_STORE")
                            if (mapping.address.kind != "inode-argument" || mapping.address.identity != 0 ||
                                mapping.address.offset < 0 || mapping.address.offset % 8 != 0 ||
                                mapping.address.offset == offset || mapping.width != 8 || mapping.delta != 0 ||
                                values[1] != ObjectValue("new-size-argument") || values[2] != NumberValue(-1L, 8) ||
                                trace.truncatedSize != null) error(anchor, "STRUCT_SIZE_TRUNCATE_ARGUMENTS")
                            trace = trace.copy(truncatedSize = offset)
                        }
                        if (subject == Subject.MNT_PUT) {
                            val mount = ObjectValue("mount-argument")
                            val argument = values[0] as? ObjectValue ?: error(anchor, "STRUCT_MNTPUT_ARGUMENT")
                            if (trace.zeroes[mount] != false || argument.kind != mount.kind ||
                                trace.releases.isNotEmpty()) error(anchor, "STRUCT_MNTPUT_RELEASE")
                            trace = trace.copy(releases = setOf(mount))
                        } else if (subject == Subject.EXIT_FS) {
                            val fs = trace.inputs.singleOrNull() ?: error(anchor, "STRUCT_FS_INPUT")
                            when (target) {
                                in fileLocks -> {
                                    val lock = values[0] as? ObjectValue ?: error(anchor, "STRUCT_FS_LOCK_ARGUMENT")
                                    trace = acquire(lock, trace)
                                }
                                in fileUnlocks -> {
                                    val lock = values[0] as? ObjectValue ?: error(anchor, "STRUCT_FS_LOCK_ARGUMENT")
                                    trace = release(lock, trace)
                                }
                                in fileSlowLocks -> {
                                    val lock = values[0] as? ObjectValue ?: error(anchor, "STRUCT_FS_LOCK_ARGUMENT")
                                    val expected = values[1]
                                    val failed = trace.zeroes.any { (value, isZero) -> !isZero &&
                                        (value is LockAttempt && value.address == lock ||
                                            value is Count && value.address == lock && value.delta == 0) }
                                    if (!failed || expected != null &&
                                        !(expected is LockAttempt && expected.address == lock ||
                                            expected is Count && expected.address == lock && expected.width == 4))
                                        error(anchor, "STRUCT_FILES_SLOW_ACQUIRE")
                                    trace = acquire(lock, trace)
                                }
                                in fileSchedules -> {
                                    val count = trace.changes.singleOrNull()?.desired
                                        ?: error(anchor, "STRUCT_FS_USERS_CHANGE")
                                    val taskPreempt = trace.heldLocks.singleOrNull()?.kind == "task-argument" &&
                                        target in symbols.offsetsOf("__schedule") && values[0] == NumberValue(1, 4)
                                    if (trace.writes != setOf(ObjectValue("task-argument", offset = fs.identity) to 8,
                                            count.address to 4) || trace.usedLocks.size != 2 ||
                                        trace.heldLocks.isNotEmpty() && !taskPreempt)
                                        error(anchor, "STRUCT_FILES_SCHEDULE_LOCKED")
                                }
                                in pathPuts -> {
                                    val path = values[0] as? ObjectValue ?: error(anchor, "STRUCT_FS_PATH_ARGUMENT")
                                    val count = trace.changes.singleOrNull()?.desired ?: error(anchor, "STRUCT_FS_USERS_CHANGE")
                                    if (path.copy(offset = 0) != fs || path.offset < 0 || path.offset % 8 != 0 ||
                                        trace.heldLocks.isNotEmpty() || trace.zeroes[count] != true ||
                                        trace.pathReleases.any { it < path.offset.toLong() + 16 && path.offset < it.toLong() + 16 } ||
                                        trace.usedLocks.any { it.copy(offset = 0) == fs && it.offset < path.offset.toLong() + 16 &&
                                            path.offset < it.offset.toLong() + 4 } ||
                                        count.address.offset < path.offset.toLong() + 16 && path.offset < count.address.offset.toLong() + 4)
                                        error(anchor, "STRUCT_FS_PATH_RELEASE")
                                    trace = trace.copy(pathReleases = trace.pathReleases + path.offset)
                                }
                                in dentryPuts -> {
                                    val part = values[0] as? ObjectValue ?: error(anchor, "STRUCT_FS_PATH_PART_ARGUMENT")
                                    val count = trace.changes.singleOrNull()?.desired ?: error(anchor, "STRUCT_FS_USERS_CHANGE")
                                    if (part.kind != "released-path-part" || part.offset != 0 || part.identity < 8 ||
                                        part.identity % 8 != 0 || trace.heldLocks.isNotEmpty() || trace.zeroes[count] != true ||
                                        part.identity in trace.pathDentriesReleased || trace.releases.isNotEmpty())
                                        error(anchor, "STRUCT_FS_DENTRY_RELEASE")
                                    trace = trace.copy(pathDentriesReleased = trace.pathDentriesReleased + part.identity)
                                }
                                in mountPuts -> {
                                    val part = values[0] as? ObjectValue ?: error(anchor, "STRUCT_FS_PATH_PART_ARGUMENT")
                                    val adjustment = if (target in containerMountPuts) mountPutEvidence!!.first else 0
                                    if (part.kind != "released-path-part" || part.offset != adjustment ||
                                        part.identity + 8 !in trace.pathDentriesReleased || trace.heldLocks.isNotEmpty() ||
                                        part.identity in trace.pathReleases || trace.releases.isNotEmpty())
                                        error(anchor, "STRUCT_FS_MOUNT_RELEASE")
                                    trace = trace.copy(pathReleases = trace.pathReleases + part.identity)
                                }
                                in fsFrees -> {
                                    val count = trace.changes.singleOrNull()?.desired ?: error(anchor, "STRUCT_FS_USERS_CHANGE")
                                    val paths = trace.pathReleases + trace.pathDentriesReleased.mapNotNull { dentry ->
                                        (dentry - 8).takeIf { trace.zeroes[ObjectValue("released-path-part", it)] == true } }
                                    if (paths.any { path -> path < 0 || path % 8 != 0 ||
                                        paths.any { other -> other != path && other < path.toLong() + 16 && path < other.toLong() + 16 } ||
                                        trace.usedLocks.any { it.copy(offset = 0) == fs && it.offset < path.toLong() + 16 &&
                                            path < it.offset.toLong() + 4 } || count.address.offset < path.toLong() + 16 &&
                                        path < count.address.offset.toLong() + 4 }) error(anchor, "STRUCT_FS_PATH_RELEASE")
                                    if (values[0] != ObjectValue("fs-cache") || values[1] != fs || trace.zeroes[count] != true ||
                                        paths.size != 2 || trace.heldLocks.isNotEmpty() || trace.releases.isNotEmpty())
                                        error(anchor, "STRUCT_FS_CACHE_RELEASE:cache=" + values[0] + ":expected=" + fsCache +
                                            ":object=" + values[1] + ":paths=" + paths + ":zero=" + trace.zeroes[count])
                                    trace = trace.copy(releases = setOf(fs), pathReleases = paths)
                                }
                            }
                        } else if (subject == Subject.SET_FS && target in pathGets) {
                            if (values[0] != ObjectValue("path-argument") || trace.pathPartsReferenced.isNotEmpty())
                                error(anchor, "STRUCT_FS_PATH_REFERENCE")
                            trace = trace.copy(pathReferences = 1, pathPartsReferenced = setOf(0, 8))
                        } else if (subject == Subject.SET_FS && target in mountGets) {
                            if (values[0] != ObjectValue("path-part", 0) || trace.pathPartsReferenced.isNotEmpty())
                                error(anchor, "STRUCT_FS_MOUNT_REFERENCE")
                            trace = trace.copy(pathPartsReferenced = setOf(0))
                            output = values[0]
                        } else if (subject == Subject.SET_FS && target in dentryGets) {
                            val dentry = values[0] as? ObjectValue
                            if (dentry?.kind != "path-part" || dentry.identity != 8 || dentry.offset <= 0 ||
                                trace.pathPartsReferenced != setOf(0)) error(anchor, "STRUCT_FS_DENTRY_REFERENCE")
                            trace = trace.copy(pathReferences = 1, pathPartsReferenced = setOf(0, 8))
                        } else if (subject == Subject.SET_FS && target in pathPuts) {
                            val old = values[0] as? StackAddress ?: error(anchor, "STRUCT_FS_OLD_PATH_STACK")
                            val mount = trace.stackValues[SubjectField(old.offset, 8)] as? ObjectValue
                            val dentry = trace.stackValues[SubjectField(old.offset + 8, 8)] as? ObjectValue
                            if (mount?.kind != "old-path-part" || mount.offset != 0 ||
                                dentry != ObjectValue("old-path-part", mount.identity + 8) ||
                                trace.pathCopies != setOf(mount.identity to 0, mount.identity + 8 to 8) ||
                                trace.heldLocks.isNotEmpty() || trace.fsSequence.size != 2 || trace.pathReleases.isNotEmpty())
                                error(anchor, "STRUCT_FS_OLD_PATH_RELEASE")
                            trace = trace.copy(pathReleases = setOf(mount.identity))
                        } else if (subject == Subject.SET_FS && (target in dentryPuts || target in mountPuts)) {
                            val part = values[0] as? ObjectValue ?: error(anchor, "STRUCT_FS_OLD_PATH_PART")
                            val first = trace.pathCopies.singleOrNull { it.second == 0 }?.first
                                ?: error(anchor, "STRUCT_FS_PATH_FIRST")
                            val expected = if (target in dentryPuts) first + 8 else first
                            val adjustment = if (target in containerMountPuts) mountPutEvidence!!.first else 0
                            if (part != ObjectValue("old-path-part", expected, adjustment) || trace.heldLocks.isNotEmpty() ||
                                trace.fsSequence.size != 2 || trace.pathReleases.isNotEmpty() || expected in trace.pathPartsReleased ||
                                target in mountPuts && first + 8 !in trace.pathPartsReleased)
                                error(anchor, "STRUCT_FS_OLD_PATH_PART_RELEASE")
                            trace = trace.copy(pathPartsReleased = trace.pathPartsReleased + expected)
                        } else if (lockSubject && target in fileSchedules) {
                            if (trace.heldLocks.isNotEmpty() ||
                                subject == Subject.EXIT_FILES && trace.writes.size != 1 ||
                                subject == Subject.SET_FS && (trace.pathCopies.size != 2 || trace.fsSequence.size != 2))
                                error(anchor, "STRUCT_FILES_SCHEDULE_LOCKED")
                        } else if (lockSubject) {
                            val argument = values[0] as? ObjectValue ?: error(anchor, "STRUCT_FILES_CALL_ARGUMENT")
                            when (target) {
                                in fileLocks -> {
                                    trace = acquire(argument, trace)
                                }
                                in fileUnlocks -> {
                                    trace = release(argument, trace)
                                }
                                in fileReleases -> {
                                    if (argument.kind != "candidate-files" || argument.offset != 0 ||
                                        trace.zeroes[argument] != false || trace.heldLocks.isNotEmpty() ||
                                        trace.writes != setOf(ObjectValue("task-argument", offset = argument.identity) to 8) ||
                                        argument in trace.releases) error(anchor, "STRUCT_FILES_RELEASE")
                                    trace = trace.copy(releases = trace.releases + argument)
                                }
                                in fileSlowLocks -> {
                                    val expected = values[1]
                                    val failed = trace.zeroes.any { (value, isZero) -> !isZero &&
                                        (value is LockAttempt && value.address == argument ||
                                            value is Count && value.address == argument && value.delta == 0) }
                                    if (!canAcquire(argument, trace) || !failed || expected != null &&
                                            !(expected is LockAttempt && expected.address == argument ||
                                                expected is Count && expected.address == argument && expected.width == 4))
                                        error(anchor, "STRUCT_FILES_SLOW_ACQUIRE")
                                    trace = acquire(argument, trace)
                                }
                            }
                        }
                        if (subject in setOf(Subject.ALLOCATE_GROUPS, Subject.ALLOCATE_EMPTY_GROUPS) && target in allocators) {
                            val allocated = ObjectValue("allocated-group", at)
                            output = allocated
                            if (subject == Subject.ALLOCATE_EMPTY_GROUPS) {
                                val extent = (values[0] as? NumberValue)?.number?.takeIf { it in 1..Int.MAX_VALUE.toLong() }
                                    ?.toInt() ?: error(anchor, "STRUCT_ALLOCATION_EXTENT")
                                trace = trace.copy(allocationSizes = trace.allocationSizes + (allocated to extent))
                            }
                        }
                        if (subject == Subject.GROUPS && target in releasers) {
                            val released = values[0] as? ObjectValue
                            if (released?.kind != "old-group" || released.offset != 0 ||
                                trace.changes.none { it.desired.address.copy(offset = 0) == released &&
                                    it.desired.delta == -1 && zero(it.desired, trace) == true })
                                error(anchor, "STRUCT_GROUP_RELEASE")
                            trace = trace.copy(releases = trace.releases + released)
                        }
                        if (!UltraKernelA64.isBl(word)) { finish(output); continue }
                        for (index in 0..18) values[index] = null
                        values[0] = output; values[30] = null; flags = null; exclusive = null
                    }
                }
                word and 0xff000010.toInt() == 0x54000000 || word and 0x7e000000 == 0x34000000 ||
                    word and 0x7e000000 == 0x36000000 -> {
                    val testBit = word and 0x7e000000 == 0x36000000
                    val bits = if (testBit) 14 else 19
                    val immediate = (word ushr 5) and ((1 shl bits) - 1)
                    val target = at + (UltraKernelA64.signExtend(immediate.toLong(), bits) * 4).toInt()
                    val conditional = word and 0xff000010.toInt() == 0x54000000
                    val rawValue = if (!conditional && !testBit) values[rd] else null
                    val value = if (!conditional && !testBit)
                        rawValue.takeIf { it is AtomicStatus } ?: register(rd) else null
                    val nonZero = word and (1 shl 24) != 0
                    if (subject != null && rawValue != null && !conditional && !testBit &&
                        ((rawValue is Count && rawValue.width > width) || (rawValue is Difference && rawValue.width > width) ||
                            (rawValue is ObjectValue && width != 8))) error(anchor, "STRUCT_BRANCH_WIDTH")
                    if (value is AtomicStatus) {
                        if (!nonZero || word < 0 || target != value.loaded.readAt) error(anchor, "STRUCT_EXCLUSIVE_RETRY")
                        trace = trace.copy(changes = trace.changes + Change(value.desired.copy(delta = 0), value.desired))
                        values[rd] = null
                        exclusive = null
                    } else if (value is LockStoreStatus) {
                        if (!nonZero || word < 0 || target != value.readAt || !canAcquire(value.address, trace))
                            error(anchor, "STRUCT_FILES_EXCLUSIVE_RETRY")
                        // Success is the only edge that reaches the protected body. A failed
                        // exclusive store retries without changing any proved task member.
                        trace = acquire(value.address, trace)
                        values[rd] = null
                        exclusive = null
                    } else if (conditional && flags?.left is NumberValue && flags.right is NumberValue) {
                        if (constantCondition(flags, word and 15)) next = target
                    } else if (conditional && flags != null && flags.subtract && (word and 15) in setOf(0, 1)) {
                        val equality = flags.left?.let { left -> flags.right?.let { right -> Equality(left, right, true) } }
                        val isEqual = equivalent(flags.left, flags.right, trace)
                        val branchEqual = word and 15 == 0
                        fun constrained(equal: Boolean): SubjectTrace {
                            if (equality == null) return trace
                            val relation = equality.copy(equal = equal)
                            val changes = trace.conditionalChanges.filter { update -> equal &&
                                ((update.equality.left == relation.left && update.equality.right == relation.right) ||
                                    (update.equality.left == relation.right && update.equality.right == relation.left)) }.map { it.change }
                            return trace.copy(equalities = trace.equalities + relation, changes = trace.changes + changes)
                        }
                        if (isEqual == null || isEqual == branchEqual) pending += State(target, values.toList(),
                            flags = flags, exclusive = exclusive, trace = constrained(branchEqual), view = canonical.view, features = canonical.features)
                        if (isEqual != null && isEqual == branchEqual) continue
                        trace = constrained(!branchEqual)
                    } else if (!conditional && !testBit && value != null) {
                        val isZero = zero(value, trace)
                        val branchZero = !nonZero
                        if (isZero == null || isZero == branchZero) pending += State(target, values.toList(),
                            flags = flags, exclusive = exclusive, trace = guard(trace, value, branchZero), view = canonical.view, features = canonical.features)
                        if (isZero != null && isZero == branchZero) continue
                        trace = guard(trace, value, !branchZero)
                    } else pending += State(target, values.toList(), flags = flags, exclusive = exclusive, trace = trace, view = canonical.view, features = canonical.features)
                }
                word and 0xfffffc1f.toInt() == 0xd61f0000.toInt() -> {
                    if (subject == Subject.SECUREBITS) {
                        val target = (values[rn] as? Address)?.offset ?: error(anchor, "STRUCT_SWITCH_TARGET")
                        if (target % 4 != 0 || target < start || target.toLong() + 4 > end)
                            error(anchor, "STRUCT_SWITCH_TARGET")
                        next = target
                    } else { complete = false; continue }
                }
                word and 0xfffffc1f.toInt() == 0xd63f0000.toInt() -> {
                    if ((subject != null || strict) && !memberLocation) error(anchor, "STRUCT_INDIRECT_CALL")
                    if (memberLocation && values.take(9).any { it is StackAddress })
                        trace = trace.copy(stackValues = emptyMap())
                    for (index in 0..18) values[index] = null
                    values[30] = null; flags = null; exclusive = null
                }
                word and 0x3a000000 == 0x28000000 -> {
                    val second = (word ushr 10) and 31
                    val mode = (word ushr 23) and 3
                    val opcode = (word ushr 30) and 3
                    val size = if (opcode == 2) 8 else 4
                    val offset = UltraKernelA64.signExtend(((word ushr 15) and 127).toLong(), 7).toInt() * size
                    val target = address(base(rn), if (mode == 1) 0 else offset)
                    val scalar = word and (1 shl 26) == 0 && opcode in setOf(0, 2)
                    if (word and (1 shl 22) == 0) {
                        if (scalar) {
                            store(target, if (rd == 31) number(0, size) else values[rd], size)
                            store(address(target, size), if (second == 31) number(0, size) else values[second], size)
                        } else if (target != null) error(anchor, "STRUCT_PAIR_ENCODING")
                    } else {
                        val tracked = scalar && (registerFrameArguments && target is RegisterFrame ||
                            subject in setOf(Subject.CAPGET, Subject.SET_FS, Subject.TRUNCATE_SIZE, Subject.SIMPLE_GETATTR,
                                Subject.POSIX_FILE_LOCK, Subject.ALLOCATE_EMPTY_GROUPS, Subject.COMM_LOCATION, Subject.TRACE_LOCATION)) &&
                            !(mode in setOf(1, 3) && rn in setOf(rd, second)) && rd != second
                        values[rd] = if (tracked) load(target, size) else null
                        values[second] = if (tracked) load(address(target, size), size) else null
                    }
                    if (mode in setOf(1, 3)) {
                        if (stackSubject && rn == 31) trace = trace.copy(stackPointer = trace.stackPointer + offset)
                        else values[rn] = address(values[rn], offset)
                    }
                }
                word and 0x3b200000 == 0x38000000 && word and (1 shl 26) == 0 -> {
                    val mode = (word ushr 10) and 3
                    val offset = UltraKernelA64.signExtend(((word ushr 12) and 0x1ff).toLong(), 9).toInt()
                    val target = address(base(rn), if (mode == 1) 0 else offset)
                    val size = 1 shl ((word ushr 30) and 3)
                    val opcode = (word ushr 22) and 3
                    if (opcode == 3 && size >= 4 || opcode == 2 && size == 8 && mode != 0)
                        error(anchor, "STRUCT_LOAD_ENCODING")
                    if (size != 8 || opcode != 2) { // PRFUM is a hint; it does not load or write back.
                        if (mode in listOf(1, 3) && rn == rd && rn != 31) error(anchor, "STRUCT_LOAD_WRITEBACK")
                        if (opcode == 0) store(target, if (rd == 31) number(0, size) else values[rd], size)
                        else {
                            if (subject != null && opcode != 1 && values[rn] is ObjectValue)
                                error(anchor, "STRUCT_LOAD_EXTENSION")
                            values[rd] = load(target, size, if (opcode >= 2) (if (opcode == 2) 8 else 4) else 0)
                        }
                        if (mode in listOf(1, 3)) {
                            if (stackSubject && rn == 31) trace = trace.copy(stackPointer = trace.stackPointer + offset)
                            else values[rn] = address(values[rn], offset)
                        }
                    }
                }
                word and 0x1f000000 in setOf(0x0a000000, 0x0b000000, 0x11000000, 0x12000000, 0x13000000,
                    0x1a000000, 0x1b000000) -> { values[rd] = null; flags = null }
                else -> {
                    if (subject != null || strict) error(anchor, "STRUCT_INSTRUCTION_UNKNOWN:" + at.toString(16) + ":" + word.toUInt().toString(16))
                    values[rd] = null
                }
            }
            values[31] = null
            pending += State(next, values.toList(), compared, flags, exclusive, trace, canonical.view, canonical.features)
        }
        return complete
    }
}

/**
 * Only ABI-compatible names are aliases. execmem_alloc(type, size) needs an adapter;
 * module_free(module, ptr) is not a module_memfree(ptr) replacement.
 */
internal object UltraKernelSymbolAliases {

    private val ALIASES = mapOf(
        "module_alloc" to listOf("module_alloc"),
        "module_memfree" to listOf("module_memfree", "execmem_free"),
        "kallsyms_lookup_name" to listOf("kallsyms_lookup_name", "kallsyms_lookup_name_v"),
    )

    /** Every name to try for [name], primary first. */
    fun candidates(name: String): List<String> = ALIASES[name] ?: listOf(name)
}

/**
 * Resolves a symbol offset for a body, honouring [UltraKernelSymbolAliases].
 *
 * @throws UltraKernelGenerateException `ULTRA_KERNEL_GENERATE_TARGET_SYMBOL_MISSING` naming the primary
 *         symbol; a body is never emitted with a placeholder address.
 */
internal class UltraKernelSymbolResolver(private val symbols: UltraKernelSymbols) {
    fun exact(name: String): Int? = symbols.offsetOf(name)

    val needsExecmemAdapter: Boolean
        get() = symbols.offsetOf("module_alloc") == null && symbols.offsetOf("execmem_alloc") != null

    fun require(name: String): Int {
        if (name == "module_alloc" && needsExecmemAdapter)
            return require("die") + UltraKernelBodies.EXECVE_GATE_BYTES + 16
        for (candidate in UltraKernelSymbolAliases.candidates(name)) {
            symbols.offsetOf(candidate)?.let { return it }
        }
        throw UltraKernelGenerateException(UltraKernelGenerateCodes.TARGET_SYMBOL_MISSING, name)
    }
}

/**
 * The generated bodies used by the recipe's code records.
 *
 * Each names the reference implementation it reproduces so a reviewer can diff intent, not just
 * bytes:
 *
 * | body id | record | reference |
 * | --- | --- | --- |
 * | `execve-gate` | `die+0x0` | `patch_do_execve.cpp` - root_key loop, cred wipe, dispatch |
 * | `execve-tail` | `die+0xD4` | `patch_do_execve.cpp` tail - `module_alloc` and jump back |
 * | `execve-frame` | `arm64_notify_die+0x0` | register save frame around the execve entry |
 * | `puts-guide` | `__drm_puts_coredump+0x0` | `patch_filldir64.cpp` guide - materialise + copy |
 * | `printfn-bracket` | `__drm_printfn_coredump+0x0` | permission transitions, execution and cleanup |
 * | `printk-flush` | `drm_dev_printk+0x0` | `patch_avc_denied.cpp` - safe_bl, cbz, mov, ret |
 * | `dev-printk` | `__dev_printk+0x0` | `patch_audit_log_start.cpp` - call, test, return, jump |
 */
internal object UltraKernelBodies {

    const val EXECVE_GATE_BYTES = 272
    const val EXECVE_ALLOC_RESUME = 196
    const val EXECUTOR_CALL_OFFSET = 64
    const val EXECUTOR_BYTES = 188
    const val METADATA_REBASE_BYTES = 448

    private const val CRED_LEN = 48
    private const val TRANSPORT_LEN = 18
    private const val NAME_BYTES = 66
    private const val NAME_PTR = 68
    private const val MAX_ERRNO = 4095
    private const val APP_PROCESS = "/app_process64"

    private const val SP = 31
    private const val XZR = 31

    /**
     * body id -> (generator, declared cave length). The length is the budget, never a guess.
     *
     * The id is what `UltraKernelRecipeK22`'s `code` field names, so a record says *where* it goes
     * (`role`) and *what emits it* (`code`) separately.
     */
    private val REGISTRY: Map<String, Pair<(UltraKernelAsm, UltraKernelSymbolResolver, UltraKernelFields) -> Unit, Int>> =
        linkedMapOf(
            "execve-gate" to (::die0 to EXECVE_GATE_BYTES),
            "execve-tail" to (::dieD4 to 16),
            "execve-frame" to (::notifyDie0 to 52),
            "execve-null-guard" to (::execveNullGuard to 16),
            "puts-guide" to (::drmPuts0 to 88),
            "printfn-bracket" to (::printfn0 to EXECUTOR_BYTES),
            "metadata-rebase" to (UltraKernelMetadataRebase::generate to METADATA_REBASE_BYTES),
            "request-v2" to (UltraKernelRequestV2::generate to UltraKernelRequestV2.BYTES),
            "printk-flush" to (::drmPrintk0 to 108),
            "dev-printk" to (::devPrintk0 to 84),
            "module-alloc-adapter" to (::moduleAllocAdapter to 12),
        )

    fun has(role: String): Boolean = REGISTRY.containsKey(role)

    fun roles(): Set<String> = REGISTRY.keys

    /**
     * Assemble [role] for the kernel this [resolver]/[fields] describe, at file offset [site].
     *
     * A body that assembles to fewer words than its declared cave is padded with `nop`: the trailing
     * bytes sit after an unconditional transfer, so they are unreachable, and keeping the record
     * length fixed is what lets the following record keep its offset. A body that does not fit is
     * refused - writing past the cave would overwrite the next record.
     *
     * @throws UltraKernelGenerateException `ULTRA_KERNEL_GENERATE_BODY` when a body outgrows its cave or
     *         fails to assemble, `..._TARGET_SYMBOL_MISSING` when a symbol it calls is gone.
     */
    fun generate(role: String, site: Int, resolver: UltraKernelSymbolResolver, fields: UltraKernelFields): UltraKernelBody {
        val asm = assemble(role, site, resolver, fields)
        val length = REGISTRY.getValue(role).second
        if (asm.offset() > length) {
            throw UltraKernelGenerateException(
                UltraKernelGenerateCodes.BODY,
                "BODY_TOO_LONG:$role:${asm.offset()}>$length",
            )
        }
        while (asm.offset() < length) asm.emit(UltraKernelA64.NOP)
        return UltraKernelBody(asm.finish(), asm.dataOffsets)
    }

    /** The same assembly, without padding or a length check; used by validation and tests. */
    fun assemble(role: String, site: Int, resolver: UltraKernelSymbolResolver, fields: UltraKernelFields): UltraKernelAsm {
        val entry = REGISTRY[role] ?: throw UltraKernelGenerateException(
            UltraKernelGenerateCodes.BODY,
            "BODY_UNKNOWN:$role",
        )
        val asm = UltraKernelAsm(site)
        try {
            entry.first(asm, resolver, fields)
        } catch (error: UltraKernelBodyException) {
            throw UltraKernelGenerateException(UltraKernelGenerateCodes.BODY, "$role:${error.detail}")
        } catch (error: IllegalArgumentException) {
            throw UltraKernelGenerateException(
                UltraKernelGenerateCodes.BODY,
                "$role:${error.message ?: error.javaClass.simpleName}",
            )
        }
        return asm
    }

    /** The declared cave length for [role]. */
    fun lengthOf(role: String): Int = REGISTRY.getValue(role).second

    // -----------------------------------------------------------------------------------------
    // die+0x0
    //
    // patch_do_execve.cpp: reject a non-filename pointer, compare `filename->name` against the
    // credential stored at `panic`, then dispatch on `filename->name[66]` to the four copied-code
    // entry points. Every address in it is either a label or a symbol offset.
    // -----------------------------------------------------------------------------------------
    private fun die0(a: UltraKernelAsm, r: UltraKernelSymbolResolver, f: UltraKernelFields) {
        val minusMaxErrno = -MAX_ERRNO.toLong()
        a.emit(UltraKernelA64.movn(11, minusMaxErrno.inv().toInt() and 0xFFFF))
        a.emit(UltraKernelA64.cmpReg(1, 11))
        a.bCond(UltraKernelA64.CS, "end")
        a.emit(UltraKernelA64.mrs(16, UltraKernelA64.MRS_SP_EL0))
        a.emit(UltraKernelA64.ldAuto("ldr", 17, 16, f.requireOffset(f.cred, UltraKernelStructOffsets.CRED), 8))
        a.emit(UltraKernelA64.ldAuto("ldr", 11, 1, 0, 8))
        val anchor = a.here()
        if (materialiseAddress(a, 12, 13, anchor, r.require("panic"))) {
            a.emit(UltraKernelA64.addReg(12, 12, 13))
        }
        a.emit(UltraKernelA64.encodeMovImm(13, 0))
        a.mark("compare")
        a.emit(UltraKernelA64.ldReg("ldrb", 14, 11, 13, 1))
        a.emit(UltraKernelA64.ldReg("ldrb", 15, 12, 13, 1))
        a.emit(UltraKernelA64.cmpReg(14, 15, sf = 0))
        a.bCond(UltraKernelA64.NE, "drain")
        a.emit(UltraKernelA64.addImm(13, 13, 1))
        a.emit(UltraKernelA64.cmpImm(13, CRED_LEN))
        a.bCond(UltraKernelA64.LT, "compare")
        // getname() guarantees a terminated filename, not a complete SDK request.
        // Validate the entire encoded transport before the in-place decode touches it.
        a.mark("transport")
        a.emit(UltraKernelA64.ldReg("ldrb", 14, 11, 13, 1))
        a.cbz(14, "end", sf = false)
        a.emit(UltraKernelA64.addImm(13, 13, 1))
        a.emit(UltraKernelA64.cmpImm(13, CRED_LEN + TRANSPORT_LEN * 2))
        a.bCond(UltraKernelA64.LT, "transport")
        a.emit(UltraKernelA64.ldAuto("ldrb", 14, 11, CRED_LEN + TRANSPORT_LEN * 2, 1))
        a.cbnz(14, "end", sf = false)
        a.emit(UltraKernelA64.addImm(13, 13, TRANSPORT_LEN * 2, sub = true))
        a.emit(UltraKernelA64.addReg(12, 11, 13))
        a.mark("mix")
        a.emit(UltraKernelA64.ldAuto("ldrb", 14, 12, 0, 1))
        a.emit(UltraKernelA64.ldAuto("ldrb", 15, 12, 18, 1))
        a.emit(UltraKernelA64.addReg(15, 15, 14, sf = 0))
        a.emit(UltraKernelA64.ldAuto("strb", 15, 12, 18, 1))
        a.emit(UltraKernelA64.addImm(12, 12, 1))
        a.emit(UltraKernelA64.ldAuto("ldrb", 14, 12, 18, 1))
        a.cbnz(14, "mix", sf = false)
        a.emit(UltraKernelA64.ldAuto("ldrb", 12, 11, NAME_BYTES, 1))
        a.emit(UltraKernelA64.ldAuto("ldr", 0, 11, NAME_PTR, 8))
        a.emit(UltraKernelA64.cmpImm(12, '1'.code, sf = 0))
        a.bCond(UltraKernelA64.EQ, "case1")
        a.emit(UltraKernelA64.cmpImm(12, '2'.code, sf = 0))
        a.bCond(UltraKernelA64.EQ, "case2")
        a.emit(UltraKernelA64.cmpImm(12, '3'.code, sf = 0))
        a.bCond(UltraKernelA64.EQ, "case3")
        a.emit(UltraKernelA64.cmpImm(12, '4'.code, sf = 0))
        if (f.requestV2) a.bCondAbs(UltraKernelA64.EQ, r.require("die") + UltraKernelRequestV2.CAVE_OFFSET)
        else a.bCond(UltraKernelA64.EQ, "join")
        a.emit(UltraKernelA64.cmpImm(12, '5'.code, sf = 0))
        a.bCond(UltraKernelA64.EQ, "release")
        a.emit(UltraKernelA64.stpPre(17, XZR, SP, -2))
        a.bAbs(r.require("die") + EXECVE_GATE_BYTES)
        a.blAbs(r.require("module_alloc"))
        a.emit(UltraKernelA64.ldpPost(17, XZR, SP, 2))
        a.b("join")
        a.mark("case1")
        a.emit(UltraKernelA64.ldAuto("ldrb", 14, 11, NAME_BYTES + 1, 1))
        a.emit(UltraKernelA64.ldAuto("strb", 14, 0, 0, 1))
        a.bAbs(r.require("drm_dev_printk") + 4)
        a.mark("case2")
        a.emit(UltraKernelA64.ldAuto("ldrb", 0, 0, 0, 1))
        a.b("join")
        a.mark("case3")
        a.bAbs(r.require("__drm_printfn_coredump") + 4)
        // Command 5 releases an owned, never-executed staging allocation after
        // a failed userspace copy. It requires no further staging allocation.
        a.mark("release")
        a.emit(UltraKernelA64.stpPre(17, XZR, SP, -2))
        a.blAbs(r.require("module_memfree"))
        a.emit(UltraKernelA64.ldpPost(17, XZR, SP, 2))
        a.emit(UltraKernelA64.encodeMovImm(0, 0))
        a.mark("join")
        a.emit(UltraKernelA64.ldAuto("str", 0, 17, f.requireOffset(f.credSuid, UltraKernelStructOffsets.SUID), 8))
        a.b("end")
        a.mark("drain")
        a.emit(UltraKernelA64.ldAuto("ldr", 13, 17, f.requireOffset(f.credEuid, UltraKernelStructOffsets.CRED_EUID), 4))
        a.cbnz(13, "end", sf = false)
        a.bAbs(r.require("__dev_printk") + 4)
        a.mark("end")
        a.bAbs(r.require("arm64_notify_die"))
    }

    /** die+0xD4: the `module_alloc` half of the dispatch, entered from the `stp`/`ldp` pair. */
    private fun dieD4(a: UltraKernelAsm, r: UltraKernelSymbolResolver, _f: UltraKernelFields) {
        a.emit(UltraKernelA64.ldAuto("ldr", 0, 11, 76, 4))
        a.cbz(0, "tail", sf = false)
        a.blAbs(r.require("module_alloc"))
        a.mark("tail")
        a.bAbs(r.require("die") + EXECVE_ALLOC_RESUME)
    }

    /** Preserve the one-argument call contract and LR while supplying EXECMEM_DEFAULT (0). */
    private fun moduleAllocAdapter(a: UltraKernelAsm, r: UltraKernelSymbolResolver, _f: UltraKernelFields) {
        a.emit(UltraKernelA64.movReg(1, 0))
        a.emit(UltraKernelA64.encodeMovImm(0, 0, sf = 0))
        a.bAbs(r.require("execmem_alloc"))
    }

    /**
     * Restore/save frame. Static PAC enters at +24; dynamic SCS retains the native PAC,
     * enters at +28, and replays the displaced stack allocation only after all registers restore.
     */
    private fun notifyDie0(a: UltraKernelAsm, r: UltraKernelSymbolResolver, f: UltraKernelFields) {
        for (pair in listOf(29 to 30, 6 to 7, 4 to 5, 2 to 3, 0 to 1)) {
            a.emit(UltraKernelA64.pair("ldp", pair.first, pair.second, SP, 2, true, 1))
        }
        if (f.execvePrologue != null) {
            a.emit(f.execvePrologue)
            a.bAbs(r.require("do_execveat_common") + 8)
        } else {
            a.bAbs(r.require("do_execveat_common") + 4)
            a.emit(UltraKernelA64.PACIA1716)
        }
        for (pair in listOf(0 to 1, 2 to 3, 4 to 5, 6 to 7, 29 to 30)) {
            a.emit(UltraKernelA64.stpPre(pair.first, pair.second, SP, -2))
        }
        a.bAbs(r.require("die"))
    }

    /** File-backed execs have no filename; preserve X7 and bypass all filename dereferences. */
    private fun execveNullGuard(a: UltraKernelAsm, r: UltraKernelSymbolResolver, f: UltraKernelFields) {
        a.cbz(1, "native")
        a.bAbs(r.require("arm64_notify_die") + 28)
        a.mark("native")
        a.emit(requireNotNull(f.execvePrologue))
        a.bAbs(r.require("do_execveat_common") + 8)
    }

    /** Lookup the replacement init using a stack path, releasing the acquired VFS reference. */
    private fun drmPuts0(a: UltraKernelAsm, r: UltraKernelSymbolResolver, _f: UltraKernelFields) {
        a.emit(UltraKernelA64.RET)
        val path = r.require("free_initmem") + 12
        a.emit(UltraKernelA64.encodeAdrp(a.here().toLong(), path.toLong(), 12))
        a.emit(UltraKernelA64.addImm(12, 12, path and 0xFFF))
        // Two saved registers and a 16-byte struct path. No heap allocation can fail or leak.
        a.emit(UltraKernelA64.stpPre(11, 12, SP, -4))
        a.emit(UltraKernelA64.movReg(0, 12))
        a.emit(UltraKernelA64.addImm(2, SP, 16))
        a.emit(UltraKernelA64.encodeMovImm(1, 1, sf = 0))
        a.blAbs(r.require("kern_path"))
        a.cbnz(0, "out", sf = false)
        a.emit(UltraKernelA64.addImm(0, SP, 16))
        a.blAbs(r.require("path_put"))
        a.emit(UltraKernelA64.pair("ldp", 11, 12, SP, 0, true, 2))
        a.mark("copy")
        a.emit(UltraKernelA64.ldImm9("ldrb", 13, 12, 1, 1, 1))
        a.emit(UltraKernelA64.ldImm9("strb", 13, 11, 1, 1, 1))
        a.cbnz(13, "copy", sf = false)
        a.mark("out")
        a.emit(UltraKernelA64.ldpPost(11, 12, SP, 4))
        a.bAbs(r.require("arm64_notify_die"))
    }

    /**
     * __drm_printfn_coredump+0x0: `set_memory_ro` / `set_memory_x` bracket around the copied code,
     * cache maintenance, `blr` into it, then NX/RW and release. A failed permission transition
     * never enters the buffer. A failed cleanup retains the allocation rather than freeing pages
     * whose permissions are uncertain. The failure reply is ERR_MODULE_TEST_CHANNEL (-19999).
     */
    private fun printfn0(a: UltraKernelAsm, r: UltraKernelSymbolResolver, f: UltraKernelFields) {
        a.emit(UltraKernelA64.RET)
        if (f.legacyMetadataDisplacement == null) {
            a.emit(UltraKernelA64.addImm(17, 17, f.requireOffset(f.credSuid, UltraKernelStructOffsets.SUID)))
        } else {
            a.blAbs(r.require("__drm_printfn_coredump") + EXECUTOR_BYTES)
        }
        a.emit(UltraKernelA64.stpPre(11, 17, SP, -2))
        a.emit(UltraKernelA64.ldAuto("ldr", 0, 11, NAME_PTR, 8))
        a.emit(UltraKernelA64.ldAuto("ldr", 1, 11, 80, 4))
        a.emit(UltraKernelA64.stpPre(0, 1, SP, -2))
        a.blAbs(r.require("set_memory_ro"))
        a.cbnz(0, "failed", sf = false)
        a.emit(UltraKernelA64.pair("ldp", 0, 1, SP, 0, true, 2))
        a.blAbs(r.require("set_memory_x"))
        a.cbnz(0, "failed", sf = false)
        // Keep BLR/LR at their original offsets: the SDK key locates metadata from LR.
        a.b("publish")
        a.mark("execute")
        a.emit(UltraKernelA64.ldAuto("ldr", 10, SP, 0, 8))
        // The task may migrate before BLR. A local IALLU leaves other CPUs' cached code intact.
        a.emit(UltraKernelA64.icIalluis())
        a.emit(UltraKernelA64.DSB_ISH)
        a.emit(UltraKernelA64.ISB)
        a.emit(UltraKernelA64.blr(10))
        a.b("cleanup")
        a.mark("failed")
        a.emit(UltraKernelA64.pair("ldp", 11, 17, SP, 2, true, 2))
        a.emit(UltraKernelA64.movn(0, 19998))
        a.emit(UltraKernelA64.ldAuto("str", 0, 17, 0, 8))
        a.mark("cleanup")
        a.emit(UltraKernelA64.pair("ldp", 0, 1, SP, 0, true, 2))
        a.blAbs(r.require("set_memory_nx"))
        a.cbnz(0, "cleanup_failed", sf = false)
        a.emit(UltraKernelA64.pair("ldp", 0, 1, SP, 0, true, 2))
        a.blAbs(r.require("set_memory_rw"))
        a.cbnz(0, "cleanup_failed", sf = false)
        a.emit(UltraKernelA64.ldAuto("ldr", 0, SP, 0, 8))
        a.blAbs(r.require("module_memfree"))
        a.b("out")
        a.mark("cleanup_failed")
        a.emit(UltraKernelA64.pair("ldp", 11, 17, SP, 2, true, 2))
        a.emit(UltraKernelA64.movn(0, 19998))
        a.emit(UltraKernelA64.ldAuto("str", 0, 17, 0, 8))
        a.mark("out")
        a.emit(UltraKernelA64.addImm(SP, SP, 32))
        a.bAbs(r.require("arm64_notify_die"))
        a.mark("publish")
        require(f.pageShift == 12 || f.pageShift == 14 || f.pageShift == 16) { "IMAGE_PAGE_SIZE" }
        a.emit(UltraKernelA64.pair("ldp", 12, 13, SP, 0, true, 2))
        a.emit(UltraKernelA64.ubfx(12, 12, 12, 44))
        a.emit(UltraKernelA64.DSB_ISHST)
        a.cbz(13, "published")
        a.mark("publish_page")
        // A by-VA `tlbi vaae1is, x12` is not enough for a just-reprotected page on the 6.6.89
        // target: the first fetch after BLR still resolved through the old non-executable
        // translation (ESR 0x8600000f, PC at the payload page start, handler PTE executable).
        // Publish with the full inner-shareable stage-1 invalidation instead.
        a.emit(UltraKernelA64.tlbiVmalle1is())
        a.emit(UltraKernelA64.addImm(12, 12, 1 shl (f.pageShift - 12)))
        a.emit(UltraKernelA64.addImm(13, 13, 1, setFlags = true, sub = true))
        a.bCond(UltraKernelA64.NE, "publish_page")
        a.mark("published")
        a.emit(UltraKernelA64.DSB_ISH)
        a.emit(UltraKernelA64.ISB)
        a.emit(UltraKernelA64.pair("ldp", 11, 17, SP, 2, true, 2))
        a.b("execute")
    }

    /**
     * drm_dev_printk+0x0: round the copied-code pointer down to a cache line, clean it to the point
     * of coherency, then gate on `cred` identity before returning.
     */
    private fun drmPrintk0(a: UltraKernelAsm, r: UltraKernelSymbolResolver, f: UltraKernelFields) {
        a.emit(UltraKernelA64.RET)
        a.emit(UltraKernelA64.mrs(14, UltraKernelA64.MRS_CTR_EL0))
        a.emit(UltraKernelA64.ubfx(14, 14, 16, 4))
        a.emit(UltraKernelA64.addImm(14, 14, 2))
        a.emit(UltraKernelA64.lsrv(0, 0, 14))
        a.emit(UltraKernelA64.lslv(0, 0, 14))
        a.emit(UltraKernelA64.dcCvac(0))
        a.emit(UltraKernelA64.DSB_ISH)
        a.bAbs(r.require("arm64_notify_die"))
        a.emit(UltraKernelA64.mrs(11, UltraKernelA64.MRS_SP_EL0))
        a.emit(UltraKernelA64.ldAuto("ldr", 12, 11, f.requireOffset(f.cred, UltraKernelStructOffsets.CRED), 8))
        a.emit(UltraKernelA64.ldAuto("ldr", 13, 12, f.requireOffset(f.credEuid, UltraKernelStructOffsets.CRED_EUID), 4))
        a.cbnz(13, "out", sf = false)
        a.emit(UltraKernelA64.encodeMovImm(14, 0xF000F000F000F000uL.toLong()))
        a.emit(UltraKernelA64.ldAuto("ldr", 12, 11, f.requireOffset(f.comm, UltraKernelStructOffsets.COMM), 8))
        a.emit(UltraKernelA64.cmpReg(12, 14))
        a.bCond(UltraKernelA64.EQ, "allow")
        a.emit(UltraKernelA64.encodeMovImm(14, 0x74696E69L, sf = 0))
        a.emit(UltraKernelA64.cmpReg(12, 14, sf = 0))
        a.bCond(UltraKernelA64.NE, "out")
        a.emit(UltraKernelA64.ldAuto("ldrb", 13, 11, f.requireOffset(f.comm, UltraKernelStructOffsets.COMM) + 4, 1))
        a.cbnz(13, "out", sf = false)
        a.emit(UltraKernelA64.cmpImm(1, 10, sf = 0))
        a.bCond(UltraKernelA64.CS, "out")
        a.mark("allow")
        a.emit(UltraKernelA64.movReg(0, XZR, sf = 0))
        a.mark("out")
        a.emit(UltraKernelA64.RET)
    }

    /**
     * __dev_printk+0x0: compare the `comm` field against `/app_process64`, with the literal carried
     * as *data* inside the body and its address produced by `adr` - so the only address field is a
     * label displacement.
     */
    private fun devPrintk0(a: UltraKernelAsm, r: UltraKernelSymbolResolver, f: UltraKernelFields) {
        a.emit(UltraKernelA64.RET)
        a.emit(UltraKernelA64.ldAuto("ldrb", 12, 16, f.requireOffset(f.comm, UltraKernelStructOffsets.COMM), 1))
        a.emit(UltraKernelA64.cmpImm(12, 'i'.code, sf = 0))
        a.bCond(UltraKernelA64.NE, "out")
        a.b("name")
        a.mark("text")
        a.embed(literal(APP_PROCESS))
        a.mark("name")
        a.adr(10, "text")
        a.emit(UltraKernelA64.movReg(12, 11))
        a.emit(UltraKernelA64.movReg(15, 10))
        a.mark("loop")
        a.emit(UltraKernelA64.ldImm9("ldrb", 14, 15, 1, 1, 1))
        a.cbz(14, "puts", sf = false)
        a.emit(UltraKernelA64.ldImm9("ldrb", 13, 12, 1, 1, 1))
        a.cbz(13, "out", sf = false)
        a.emit(UltraKernelA64.cmpReg(13, 14, sf = 0))
        a.emit(UltraKernelA64.csel(15, 10, 15, UltraKernelA64.NE))
        a.b("loop")
        a.mark("puts")
        a.bAbs(r.require("__drm_puts_coredump") + 4)
        a.mark("out")
        a.bAbs(r.require("arm64_notify_die"))
    }

    /** The literal padded to a whole number of words, as the reference embeds it. */
    private fun literal(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        val padded = ByteArray(UltraKernelA64.alignUp(bytes.size, 4))
        bytes.copyInto(padded)
        return padded
    }

    /**
     * Put [target] into [rd], returning whether the caller must still `add rd, rd, scratch`.
     *
     * The reference materialises an absolute address as `adr rd, .` followed by a `movz`/`movk` run
     * holding the displacement, added with [scratch]. That costs one word per non-zero 16 bit group
     * of the displacement - two on 6.6.89, but **four** when the displacement is a large negative
     * number, whose two's complement has all four groups populated.
     *
     * A large negative displacement needs four move-immediate words. ADRP + ADD
     * keeps that path within the gate's fixed materialisation slots.
     */
    private fun materialiseAddress(a: UltraKernelAsm, rd: Int, scratch: Int, anchor: Int, target: Int): Boolean {
        // The first record (execve-gate) and the separately rendered die+0xD4 record form one
        // control-flow pair: the latter returns to the invariant allocation restore instruction.
        // Keep the address-materialisation block at four words on every target. The reference
        // encoding already occupies four words; the ADRP form is two, so pad those two slots.
        // Without this, a large negative displacement shortens the body by eight bytes and
        // The tail would then miss the restore instruction and leave x17 on the stack.
        val start = a.offset()
        val run = UltraKernelA64.encodeMovImm(scratch, target.toLong() - anchor)
        if (run.size <= 2) {
            a.adrSelf(rd)
            a.emit(run)
            while (a.offset() - start < 12) a.emit(UltraKernelA64.NOP)
            return true
        }
        a.emit(UltraKernelA64.encodeAdrp(anchor.toLong(), target.toLong(), rd))
        a.emit(UltraKernelA64.addImm(rd, rd, target and 0xFFF))
        while (a.offset() - start < 16) a.emit(UltraKernelA64.NOP)
        return false
    }
}

