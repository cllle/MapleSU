package dev.maplesu.manager.ultrakernel

import dev.maplesu.image.BootImageInspector
import dev.maplesu.image.KernelCompression
import java.io.File
import java.io.RandomAccessFile

/**
 * One Ultra patch plan: the records, the credential slots inside them, and the digests derived from
 * applying them.
 *
 * Why this type exists. Today two different things are called "a Ultra kernel" and they disagree:
 *
 *  - [UltraKernelSpec] is a *shipped* offset table with a device secret baked in. It can apply, but it
 *    cannot be re-bound: `#key=` is parsed and never used, and the 48 byte literal inside the
 *    `panic` record is copied verbatim. A device whose root key is not that literal gets no root.
 *  - The host generator (`tools/specgen`) can now produce the same records for *any* kernel and
 *    *any* credential, but its output arrives as plain `offset|orig|repl` text, which carries
 *    neither "which record holds the secret" nor "what should the result hash to".
 *
 * [UltraKernelPlan] is the missing contract between them: it names the slots, keeps the record order
 * (the applier writes in file order, and two records overlap), and computes the expected digests
 * instead of trusting a pinned constant. A plan is either
 * [Origin.PINNED_ASSET] (a shipped, device-proven asset, digests known) or [Origin.GENERATED]
 * (produced locally, digests derived, and therefore `experimental` by construction).
 *
 * Nothing here opens a partition, starts a process, or reads the credential store.
 */
internal class UltraKernelPlan private constructor(
    val kernelRelease: String,
    val origin: Origin,
    val records: List<PatchRecord>,
    val slots: Slots?,
    val derived: Derived?,
    /** What the renderer observed at the patch sites when this plan was generated. */
    val evidence: SiteEvidence? = null,
    /** sha256 of the raw kernel this plan was applied to; `""` when never applied. */
    val baseKernelSha256: String = "",
    private val sdkMetadataDisplacement: Int? = null,
    /** Layout consumed by the source renderer; legacy spec inputs carry no inferred fields. */
    val layout: UltraKernelLayout = UltraKernelLayout(emptyMap()),
) {

    enum class Origin {
        /** Shipped asset with pinned digests; the only origin allowed to claim `verified`. */
        PINNED_ASSET,

        /** Produced locally from a recipe; every digest in [derived] was computed, not pinned. */
        GENERATED,
    }

    /**
     * What a [Origin.GENERATED] plan is allowed to claim.
     *
     * Deliberately *not* a boolean: a `PINNED_ASSET` plan's support level belongs to the asset that
     * produced it (some shipped assets are themselves `experimental`), so this type refuses to
     * answer for them instead of guessing. A `GENERATED` plan is `EXPERIMENTAL` by construction and
     * no site observation can promote it - only a device boot can.
     */
    enum class SupportLevel {
        /** Generated locally, never booted. The UI must say so and must not call it verified. */
        EXPERIMENTAL,

        /** Belongs to the shipped asset; read `UltraKernelPatchAsset.verification` instead. */
        ASSET_DECIDES,
    }

    /**
     * What a generated plan can say about its own sites.
     *
     * [matching]/[total] count the records whose replacement this generator *produced for this
     * kernel* - assembled from its symbol offsets, never carried as machine code. That is the gate
     * (`requireFullSiteCoverage`): a record still holding a frozen blob is counted as missing,
     * because such a blob was compiled for one build and is exactly what must not reach another.
     *
     * [referenceOrigMatch] is the older, narrower probe: how many sites still carry the *reference*
     * build's pristine bytes. On 6.6.89 it is `18/18`; on 6.1.25 it is `3/18` and that is expected,
     * not a defect - the code bodies are generated here, so a differing site no longer means a
     * foreign blob. It is recorded as evidence only and never gates.
     *
     * [referenceMatch] therefore means "every site was generated for this kernel and no address was
     * left stale". It never promotes [supportLevel] - it is evidence, not proof.
     */
    class SiteEvidence(
        val matching: Int,
        val total: Int,
        val differingRoles: List<String>,
        val residue: List<String>,
        /** `"18/18"` on the reference build; diagnostic only. */
        val referenceOrigMatch: String = "",
        /**
         * `optional` records this kernel does not host, e.g. the CFI stub on 5.10.
         *
         * Recorded so a receipt can say *why* a plan has fewer records than the recipe; an absent
         * optional record is not a defect, but it must never be silent either.
         */
        val skippedRoles: List<String> = emptyList(),
    ) {
        val referenceMatch: Boolean
            get() = total > 0 && matching == total && residue.isEmpty()

        /** `"18/18"` style summary for receipts and the UI. */
        val summary: String get() = "$matching/$total"
    }

    /** One `offset|orig|repl` record. [role] names the site (`die+0x0`, `free_initmem+0x0`, ...). */
    class PatchRecord(
        val role: String,
        val offset: Long,
        val original: ByteArray,
        val replacement: ByteArray,
        val cave: String? = null,
        val caveBase: Long? = null,
        val caveOffset: Int? = null,
        val shiftedFrom: Long? = null,
    ) {
        init {
            require(role.isNotEmpty()) { "ULTRA_KERNEL_PLAN_ROLE_EMPTY" }
            require(offset >= 0) { "ULTRA_KERNEL_PLAN_OFFSET_INVALID" }
            require(original.isNotEmpty() && replacement.isNotEmpty()) {
                "ULTRA_KERNEL_PLAN_RECORD_EMPTY:$role"
            }
        }

        /** The highest byte this record touches; kept equal for every record in a valid plan. */
        val length: Int get() = maxOf(original.size, replacement.size)

        fun copy(): PatchRecord = PatchRecord(
            role, offset, original.copyOf(), replacement.copyOf(),
            cave, caveBase, caveOffset, shiftedFrom,
        )
    }

    /** Where the device secret lives inside [records]. Declared, never guessed at write time. */
    class Slots(val credential: Slot, val initPaths: List<Slot>) {
        init {
            require(initPaths.isNotEmpty()) { "ULTRA_KERNEL_PLAN_PATH_SLOT_MISSING" }
        }

        class Slot(val recordIndex: Int, val offsetInRecord: Int, val length: Int) {
            init {
                require(recordIndex >= 0) { "ULTRA_KERNEL_PLAN_SLOT_RECORD_INVALID" }
                require(offsetInRecord >= 0 && length > 0) { "ULTRA_KERNEL_PLAN_SLOT_RANGE_INVALID" }
            }
        }
    }

    /** Every gate the applier enforces, quantified, plus the digest of the result. */
    class Derived(
        val records: Int,
        val changedBytes: Int,
        val outsideSites: Int,
        val patchedKernelSha256: String,
    )

    class Applied(val kernel: ByteArray, val derived: Derived)

    /**
     * The values a device-proven build must reproduce. Belongs to a *release*, so a comparison is
     * only meaningful for the release the values were recorded on.
     */
    class Oracle(
        val kernelRelease: String,
        val records: Int,
        val changedBytes: Int,
        val kernelSha256: String,
        val bootSha256: String,
    )

    /**
     * Apply the records to [kernel] with the applier's exact rules and derive the outcome.
     *
     * The version gate is the kernel **series** (`major.minor`), so a plan recorded on `6.6.89`
     * applies to a `6.6.50` kernel; a different series (`6.12`, `5.15`) is still refused, and an
     * unparseable banner is never guessed.
     *
     * @throws IllegalStateException `ULTRA_KERNEL_PLAN_RELEASE_MISMATCH:…` for a different series,
     *         `ULTRA_KERNEL_PLAN_PATCH_ORIGINAL_MISMATCH:i@off` when a record's pristine bytes are
     *         absent - a kernel that is already patched therefore fails here instead of producing a
     *         half-patched image;
     *         `ULTRA_KERNEL_PLAN_PATCH_OUTSIDE_SITES` when any byte outside a record changed.
     */
    fun apply(kernel: ByteArray): Applied {
        require(kernel.isNotEmpty()) { "ULTRA_KERNEL_PLAN_EMPTY" }
        val release = UltraKernelSpec.releaseOf(kernel)
        check(KernelSeries.same(release, kernelRelease)) {
            "ULTRA_KERNEL_PLAN_RELEASE_MISMATCH:${KernelSeries.detail(release, kernelRelease)}"
        }

        records.forEachIndexed { index, record ->
            check(record.offset + record.length <= kernel.size.toLong()) {
                "ULTRA_KERNEL_PLAN_PATCH_RANGE:$index"
            }
            check(matches(kernel, record.offset, record.original)) {
                "ULTRA_KERNEL_PLAN_PATCH_ORIGINAL_MISMATCH:$index@${record.offset}"
            }
        }
        val patched = kernel.copyOf()
        records.forEach { record ->
            record.replacement.copyInto(patched, record.offset.toInt())
        }
        var changed = 0
        var outside = 0
        val covered = BooleanArray(patched.size)
        records.forEach { record ->
            val start = record.offset.toInt()
            for (index in start until minOf(start + record.replacement.size, covered.size)) {
                covered[index] = true
            }
        }
        for (index in patched.indices) {
            if (patched[index] == kernel[index]) continue
            changed++
            if (!covered[index]) outside++
        }
        check(outside == 0) { "ULTRA_KERNEL_PLAN_PATCH_OUTSIDE_SITES:$outside" }
        return Applied(
            patched,
            Derived(records.size, changed, outside, UltraKernelSpec.sha256(patched)),
        )
    }

    /** True when this plan can still emit [UltraKernelCredential] because it declares its slots. */
    val rebindable: Boolean get() = slots != null

    /** Read the identity actually embedded by the renderer, including its SDK locator encoding. */
    val credential: UltraKernelCredential?
        get() = slots?.credential?.let { slot ->
            val secret = records[slot.recordIndex].replacement.copyOfRange(
                slot.offsetInRecord, slot.offsetInRecord + slot.length)
            try { UltraKernelCredential.of(secret) } finally { secret.fill(0) }
        }

    /**
     * `EXPERIMENTAL` for every locally generated plan; `ASSET_DECIDES` otherwise.
     *
     * The product rule this encodes: a generated plan is *never* "the same as k22". k22 is
     * `verified` because it booted a device; a plan this class derived from bytes cannot be, no
     * matter how many of its sites matched the reference build.
     */
    val supportLevel: SupportLevel
        get() = if (origin == Origin.GENERATED) SupportLevel.EXPERIMENTAL else SupportLevel.ASSET_DECIDES

    /** True for a locally generated plan; the UI must surface a warning for these. */
    val experimental: Boolean get() = supportLevel == SupportLevel.EXPERIMENTAL

    /** Same plan with the outcome of [apply] attached. */
    fun withDerived(derived: Derived): UltraKernelPlan =
        UltraKernelPlan(kernelRelease, origin, records, slots, derived, evidence, baseKernelSha256,
            sdkMetadataDisplacement, layout)

    /** Same plan, re-labelled with the credential slots detected for [credential]. */
    fun withSlots(slots: Slots?): UltraKernelPlan =
        UltraKernelPlan(kernelRelease, origin, records, slots, derived, evidence, baseKernelSha256,
            sdkMetadataDisplacement, layout)

    /**
     * Re-bind the plan to [credential]: the 48 byte literal and every `/data/<key16>/init` field are
     * rewritten, and nothing else is touched.
     *
     * This is the fix for "the kernel accepts one device's key". The envelope's path field has a
     * fixed width ([UltraKernelCredential.INIT_PATH_FIELD]) and the key is a fixed 48 bytes, so a
     * re-bind can never change a record's length - the applier's same-length invariant holds by
     * construction.
     *
     * @throws IllegalStateException `ULTRA_KERNEL_PLAN_SLOT_UNDECLARED` when the plan carries a
     *         credential-shaped literal but no declared slot (a spec that baked a secret in).
     */
    fun rebind(credential: UltraKernelCredential): UltraKernelPlan {
        val boundCredential = sdkMetadataDisplacement?.let(credential::withMetadataDisplacement) ?: credential
        val declared = slots ?: throw IllegalStateException(
            if (containsCredentialRun()) "ULTRA_KERNEL_PLAN_SLOT_UNDECLARED"
            else "ULTRA_KERNEL_PLAN_SLOT_MISSING",
        )
        val rebound = records.map { it.copy() }.toMutableList()
        val secret = boundCredential.copySecret()
        try {
            val target = rebound[declared.credential.recordIndex]
            check(declared.credential.length == secret.size) { "ULTRA_KERNEL_PLAN_SLOT_LENGTH" }
            secret.copyInto(
                target.replacement,
                declared.credential.offsetInRecord,
                0,
                secret.size,
            )
            declared.initPaths.forEach { slot ->
                val path = boundCredential.initPathField(slot.length)
                val record = rebound[slot.recordIndex]
                check(slot.offsetInRecord + path.size <= record.replacement.size) {
                    "ULTRA_KERNEL_PLAN_SLOT_OUT_OF_RECORD"
                }
                path.copyInto(record.replacement, slot.offsetInRecord)
            }
        } finally {
            secret.fill(0)
        }
        return UltraKernelPlan(kernelRelease, origin, rebound, declared, null, evidence, baseKernelSha256,
            sdkMetadataDisplacement, layout)
    }

    /** The plan as the applier's spec grammar, with its slots and origin stated in the header. */
    fun specText(credential: UltraKernelCredential? = null): String {
        val text = StringBuilder()
        text.append("#plan.origin=").append(origin.name).append('\n')
        // A locally generated spec says so in the same slot the host generator uses, so a file that
        // escapes its private directory can still never be mistaken for a reviewed asset.
        text.append("#origin=").append(origin.name).append('\n')
        text.append("#supportLevel=")
            .append(if (experimental) "experimental" else "asset")
            .append('\n')
        if (evidence != null) {
            text.append("#sites=").append(evidence.summary)
                .append(" referenceMatch=").append(evidence.referenceMatch)
                .append('\n')
        }
        text.append("#plan.slots=").append(if (slots == null) "none" else "credential+path").append('\n')
        text.append("#kernel=").append(kernelRelease).append('\n')
        credential?.let {
            check(it == this.credential) { "ULTRA_KERNEL_PLAN_CREDENTIAL_MISMATCH" }
            val secret = it.copySecret()
            try {
                text.append("#key=").append(String(secret, Charsets.US_ASCII)).append('\n')
            } finally {
                secret.fill(0)
            }
        }
        records.filter { it.cave != null }.forEach { record ->
            text.append("#cave=").append(record.role)
                .append(";cave=").append(record.cave)
                .append(";base=").append(record.caveBase ?: record.offset)
                .append(";offset=").append(record.caveOffset ?: 0)
                .append(";shiftedFrom=").append(record.shiftedFrom ?: "-")
                .append(";writeStart=").append(record.offset)
                .append(";writeEnd=").append(record.offset + record.replacement.size)
                .append('\n')
        }
        records.forEach { record ->
            text.append(record.offset).append('|')
                .append(hex(record.original)).append('|')
                .append(hex(record.replacement)).append('\n')
        }
        return text.toString()
    }

    /** True when any record carries a 48 character alphanumeric run (a baked-in secret). */
    fun containsCredentialRun(): Boolean = records.any { hasCredentialRun(it.replacement) }

    /**
     * Differences from a pinned [Oracle]; empty means "this plan matches the device-proven build".
     * Call this only for the release the oracle was recorded on - the caller decides that, this
     * class never assumes a release is comparable.
     *
     * The release comparison here stays **exact on purpose**. This is not a gate: it answers "is
     * this byte-for-byte the build that booted a device?", a question a same-series-but-different
     * release can only answer "no" to. Gates use [KernelSeries]; this diagnostic does not.
     */
    fun verifyAgainst(oracle: Oracle, bootSha256: String): List<String> {
        val report = derived ?: return listOf("ULTRA_KERNEL_PLAN_NOT_APPLIED")
        val mismatches = ArrayList<String>()
        if (oracle.kernelRelease != kernelRelease) {
            mismatches += "release:${kernelRelease}"
        }

        if (report.records != oracle.records) mismatches += "records:${report.records}"
        if (report.changedBytes != oracle.changedBytes) mismatches += "changedBytes:${report.changedBytes}"
        if (report.patchedKernelSha256 != oracle.kernelSha256) {
            mismatches += "kernelSha256:${report.patchedKernelSha256}"
        }
        if (oracle.bootSha256.isNotEmpty() && bootSha256 != oracle.bootSha256) {
            mismatches += "bootSha256:$bootSha256"
        }
        return mismatches
    }

    companion object {
        /**
         * The one Ultra build that has ever booted a device (`47aa147a...`, kernel `287fbdd0...`).
         * Recorded here so a generated plan can be compared against reality - never used as an
         * expected value for a plan that was not generated on this release.
         */
        val K22_ORACLE = Oracle(
            kernelRelease = "6.6.89-android15-8-g7e1f3c083cc6-abogki467167594-4k",
            records = 18,
            changedBytes = 842,
            kernelSha256 = "287fbdd08c14bf62488e624d60fd5c5fd4de783712fd17673dd1e7c8c5c00f80",
            bootSha256 = "47aa147acbe3edf8e58ce60d334ad4c4b26cad26b5cb93aa6acb59a67fdd5f59",
        )

        /**
         * Wrap a shipped [spec] as a plan. When [credential] is given, the slots are detected from
         * *that* credential (the shipped spec only works for the device it was generated for), and
         * a spec that carries a credential-shaped literal without a detectable slot is refused.
         */
        fun fromSpec(
            spec: UltraKernelSpec,
            origin: Origin = Origin.PINNED_ASSET,
            credential: UltraKernelCredential? = null,
        ): UltraKernelPlan {
            val records = spec.patches.map { patch ->
                PatchRecord("site-${patch.index}", patch.offset, patch.original, patch.replacement)
            }
            val slots = credential?.let { detectSlots(records, it) }
            if (slots == null && records.any { hasCredentialRun(it.replacement) }) {
                throw IllegalStateException("ULTRA_KERNEL_PLAN_SLOT_UNDECLARED")
            }
            return UltraKernelPlan(spec.kernelRelease, origin, records, slots, null)
        }

        /**
         * Build a plan from records that were produced somewhere else (the on-device recipe
         * renderer), with the slots detected from the [credential] those records were built for.
         */
        fun fromRecords(
            kernelRelease: String,
            records: List<PatchRecord>,
            credential: UltraKernelCredential,
            origin: Origin = Origin.GENERATED,
            evidence: SiteEvidence? = null,
            baseKernelSha256: String = "",
            sdkMetadataDisplacement: Int? = null,
            layout: UltraKernelLayout = UltraKernelLayout(emptyMap()),
        ): UltraKernelPlan {
            require(records.isNotEmpty()) { "ULTRA_KERNEL_PLAN_EMPTY" }
            require(kernelRelease.isNotEmpty()) { "ULTRA_KERNEL_PLAN_RELEASE_MISSING" }
            return UltraKernelPlan(
                kernelRelease, origin, records, detectSlots(records, credential), null,
                evidence, baseKernelSha256, sdkMetadataDisplacement, layout,
            )
        }

        /**
         * The on-device entry point: take the shipped plan for this release, re-bind it to the
         * user's [credential], and prove the result against [kernel] before anything is rebuilt.
         *
         * The base kernel is decoded here exactly the way [UltraKernelBuilder] decodes it, so this
         * method and the builder can never disagree about which bytes the records are checked on.
         */
        fun planFor(boot: File, credential: UltraKernelCredential, template: UltraKernelPlan): UltraKernelPlan =
            planFor(readKernel(boot), credential, template)

        /** [planFor] over an already-decoded raw kernel. */
        fun planFor(
            kernel: ByteArray,
            credential: UltraKernelCredential,
            template: UltraKernelPlan,
        ): UltraKernelPlan {
            val release = UltraKernelSpec.releaseOf(kernel)
            // Series, not release: a `6.6.50` boot may be re-bound with the `6.6.89` template.
            check(KernelSeries.same(release, template.kernelRelease)) {
                "ULTRA_KERNEL_PLAN_RELEASE_MISMATCH:${KernelSeries.detail(release, template.kernelRelease)}"
            }

            val rebound = template.rebind(credential)
            val applied = rebound.apply(kernel)
            return UltraKernelPlan(
                release, Origin.GENERATED, rebound.records, rebound.slots, applied.derived,
                rebound.evidence, UltraKernelSpec.sha256(kernel), rebound.sdkMetadataDisplacement, rebound.layout,
            )
        }

        /** Read + decode the kernel of [boot] without touching any partition. */
        fun readKernel(boot: File): ByteArray {
            check(boot.isFile && boot.length() > 0) { "ULTRA_KERNEL_PLAN_BASE_INVALID" }
            val report = BootImageInspector.inspect(boot)
            check(report.kernelSize in 1L..KernelCompression.MAX_KERNEL_BYTES.toLong()) {
                "ULTRA_KERNEL_PLAN_BASE_NO_KERNEL"
            }
            val container = ByteArray(report.kernelSize.toInt())
            RandomAccessFile(boot, "r").use { input ->
                input.seek(report.kernelOffset)
                input.readFully(container)
            }
            if (Thread.currentThread().isInterrupted) throw java.io.IOException("CANCELLED")
            // This freshly read component has no other owner; RAW needs no decoding copy.
            return if (report.compression == "RAW_ARM64") container else KernelCompression.decode(
                container, report.compression, KernelCompression.MAX_KERNEL_BYTES,
            )
        }

        /**
         * Locate the secret slot and every init path slot from the credential the plan was built
         * for. Detection is by content and by the fixed field widths, and it fails closed: a spec
         * that does not contain the credential it claims is not silently "already correct".
         */
        private fun detectSlots(records: List<PatchRecord>, credential: UltraKernelCredential): Slots {
            val secret = credential.copySecret()
            try {
                var credentialSlot: Slots.Slot? = null
                val pathSlots = ArrayList<Slots.Slot>()
                records.forEachIndexed { index, record ->
                    val at = indexOf(record.replacement, secret)
                    if (at >= 0) {
                        check(credentialSlot == null) { "ULTRA_KERNEL_PLAN_SLOT_AMBIGUOUS" }
                        credentialSlot = Slots.Slot(index, at, secret.size)
                    }
                    val path = credential.initPath.toByteArray(Charsets.US_ASCII)
                    val pathAt = indexOf(record.replacement, path)
                    if (pathAt >= 0) {
                        pathSlots += Slots.Slot(
                            index, pathAt, UltraKernelCredential.INIT_PATH_FIELD,
                        )
                    }
                }
                val slot = credentialSlot
                    ?: throw IllegalStateException("ULTRA_KERNEL_PLAN_CREDENTIAL_SLOT_NOT_FOUND")
                return Slots(slot, pathSlots)
            } finally {
                secret.fill(0)
            }
        }

        private fun hasCredentialRun(replacement: ByteArray): Boolean {
            val needle = UltraKernelCredential.LENGTH
            if (replacement.size < needle) return false
            var start = 0
            while (start + needle <= replacement.size) {
                if (!isAlphanumeric(replacement[start])) {
                    start++
                    continue
                }
                var end = start
                while (end < replacement.size && isAlphanumeric(replacement[end])) end++
                if (end - start == needle) return true
                start = end
            }
            return false
        }

        private fun isAlphanumeric(byte: Byte): Boolean =
            byte in 'A'.code.toByte()..'Z'.code.toByte() ||
                byte in 'a'.code.toByte()..'z'.code.toByte() ||
                byte in '0'.code.toByte()..'9'.code.toByte()

        private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
            if (needle.isEmpty() || haystack.size < needle.size) return -1
            outer@ for (start in 0..(haystack.size - needle.size)) {
                for (index in needle.indices) {
                    if (haystack[start + index] != needle[index]) continue@outer
                }
                return start
            }
            return -1
        }

        private fun matches(kernel: ByteArray, offset: Long, expected: ByteArray): Boolean {
            if (offset < 0 || offset + expected.size > kernel.size.toLong()) return false
            for (index in expected.indices) {
                if (kernel[offset.toInt() + index] != expected[index]) return false
            }
            return true
        }

        private fun hex(bytes: ByteArray): String =
            bytes.joinToString("") { "%02x".format(it) }
    }
}
