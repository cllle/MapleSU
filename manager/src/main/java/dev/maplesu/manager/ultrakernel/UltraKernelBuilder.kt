package dev.maplesu.manager.ultrakernel

import dev.maplesu.image.BootImageInspector
import dev.maplesu.image.BootImageRebuilder
import dev.maplesu.image.BootImageRecovery
import dev.maplesu.image.KernelCompression
import dev.maplesu.image.UltraInitBootProducer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * On-device Ultra boot-image generation, built from the same image-core primitives the product
 * already ships (`BootImageInspector` / `KernelCompression` / `BootImageRebuilder`).
 *
 * The pipeline is deliberately short and every step asserts its own outcome:
 *
 *  1. take the already-gated spec ([UltraKernelSpec]) for the selected asset;
 *  2. read the base boot image's kernel and decode it;
 *  3. require every spec record's pristine bytes to be present - a kernel that is already
 *     patched therefore fails here instead of producing a half-patched image;
 *  4. patch, then require the patched kernel digest to equal the settled one;
 *  5. rebuild the boot image around it, then re-inspect the output and require the header,
 *     size, ramdisk and re-read kernel/boot digests to match.
 *
 * `baseKernelSha256` is optional. When the asset pins it and the input matches, the build is
 * "strict": the stock boot digest and the pinned output boot digest are checked too. When the
 * asset does not pin it, its expected output digest is still required. A template applied to a
 * different pinned base validates every original patch byte and the result's complete readback.
 *
 * A pinned expected kernel digest belongs to its pinned base, not every image reporting the same
 * release. A different base of the same kernel series cannot reproduce that digest, even when
 * every patch site fits, so on that path the digest is derived from the build itself after every
 * record's pristine bytes matched and no byte outside a record changed. [Candidate.expectedPinned]
 * and the receipt's `expectedKernelPinned` / `seriesTemplate` record which mode produced the image.

 *
 * Nothing here opens a partition; the result is a candidate file plus a receipt.
 */
internal class UltraKernelBuilder @JvmOverloads constructor(
    private val workspace: File,
    private val initBootReceiptWriter: (File, ByteArray) -> Unit = { file, bytes ->
        file.outputStream().use { stream -> stream.write(bytes); stream.fd.sync() }
    },
) {

    data class BaseInspection(
        val file: File,
        val bootSha256: String,
        val size: Long,
        val kernelRelease: String,
        val kernelSha256: String,
        val pristine: Boolean,
        val mismatchedRecords: Int,
        val knownStockBase: Boolean,
        /** True only when the decoded kernel is the pinned pristine kernel for this asset. */
        val baseKernelPinned: Boolean,
        /** True when at least one gate had to run without a pinned base-kernel digest. */
        val expectedByteMode: Boolean,
        val alreadyPatched: Boolean = false,
    )

    data class Candidate(
        val image: File,
        val bootSha256: String,
        val baseBootSha256: String,
        val kernelSha256: String,
        val kernelRelease: String,
        val patches: Int,
        val differingBytes: Int,
        val knownImage: Boolean,
        /** `verified`/`experimental` of the asset that produced this candidate. */
        val verification: String,
        val source: String,
        /** True when the input base kernel matched the asset's pinned base digest. */
        val strictBase: Boolean,
        /**
         * True when the produced kernel digest was the asset's pinned value.
         *
         * False for a locally generated plan or a same-series template applied to a different
         * base kernel. A shared release string does not make two different kernels byte-identical.
         * The receipt carries the same flag plus both releases.
         */
        val expectedPinned: Boolean = true,
        /** The container actually produced; the external target boot is read-only. */
        val partition: String = "boot",
        val componentKmi: String = "",
    )

    /** [base] is the recovery baseline; the installation input keeps its own pre-normalization digest. */
    fun generateInitBoot(base: File, artifact: UltraInitBootProducer.Artifact,
                         credential: UltraKernelCredential, installationInput: File = base,
                         installationInputSha256: String = UltraKernelSpec.sha256(installationInput),
                         normalizationSource: UltraKernelOriginalRecovery.Source? = null): Candidate = synchronized(lock) {
        val source = BootImageInspector.inspect(base)
        val input = if (installationInput == base) source else BootImageInspector.inspect(installationInput)
        check(input.sha256 == installationInputSha256) { "ULTRA_KERNEL_INIT_BOOT_INPUT_CHANGED" }
        check(source.headerVersion in 0..4 && source.ramdiskSize > 0L &&
            (source.kernelSize > 0L || source.headerVersion == 4)) {
            "ULTRA_KERNEL_INIT_BOOT_CONTAINER"
        }
        val sourceKernel = if (source.kernelSize > 0L) UltraKernelPlan.readKernel(base) else null
        if (!artifact.isKmiComponent) {
            val target = UltraKernelPlan.readKernel(artifact.targetBoot)
            check(UltraKernelSpec.sha256(target) == artifact.kernelSha256 &&
                UltraKernelSpec.releaseOf(target) == artifact.kernelRelease) {
                "ULTRA_KERNEL_INIT_BOOT_TARGET_CHANGED"
            }
        } else if (sourceKernel != null) {
            check(dev.maplesu.image.KernelIdentity.kmiOf(sourceKernel) == artifact.componentKmi) {
                "ULTRA_KERNEL_MODULE_CARRIER_COMPONENT_KMI"
            }
        }
        if (artifact.credentialMode == "module-parameter") {
            check(credential.sdkMetadataValid && credential.metadataDisplacement == artifact.metadataDisplacement) {
                "ULTRA_KERNEL_INIT_BOOT_CREDENTIAL_METADATA_MISMATCH"
            }
        }
        val secret = credential.copySecret()
        try {
            check(UltraKernelSpec.sha256(secret) == artifact.credentialSha256) {
                "ULTRA_KERNEL_INIT_BOOT_CREDENTIAL_MISMATCH"
            }
        } finally { secret.fill(0) }
        if (!artifact.isKmiComponent && sourceKernel != null) check(UltraKernelSpec.sha256(sourceKernel) == artifact.kernelSha256) {
            "ULTRA_KERNEL_MODULE_CARRIER_TARGET_CHANGED"
        }
        val carrierPartition = if (source.kernelSize > 0L) "boot" else "init_boot"
        val targetBootSha = if (artifact.isKmiComponent) null else UltraKernelSpec.sha256(artifact.targetBoot)
        val kernelSha = if (artifact.isKmiComponent) sourceKernel?.let { UltraKernelSpec.sha256(it) }.orEmpty() else artifact.kernelSha256
        val kernelRelease = if (artifact.isKmiComponent) sourceKernel?.let { UltraKernelSpec.releaseOf(it) }.orEmpty() else artifact.kernelRelease
        check(workspace.isDirectory || workspace.mkdirs()) { "ULTRA_KERNEL_WORKSPACE" }
        val output = File(workspace, "candidate-init-boot-${java.util.UUID.randomUUID()}.img")
        val receipt = File(workspace, output.nameWithoutExtension + ".json")
        var pendingReceipt: File? = null
        var outputOwned = false
        var receiptOwned = false
        try {
            val produced = UltraInitBootProducer.produce(base, output, artifact)
            outputOwned = true
            val after = BootImageInspector.inspect(output)
            check(after.kernelSize == source.kernelSize && after.headerVersion == source.headerVersion &&
                (source.kernelSize == 0L || UltraKernelSpec.sha256(UltraKernelPlan.readKernel(output)) == kernelSha) &&
                after.sha256 == produced.imageSha256 && produced.sourceSha256 == source.sha256 &&
                UltraKernelSpec.sha256(base) == source.sha256 &&
                UltraKernelSpec.sha256(installationInput) == input.sha256 &&
                (artifact.isKmiComponent || UltraKernelSpec.sha256(artifact.targetBoot) == targetBootSha)) {
                "ULTRA_KERNEL_INIT_BOOT_READBACK_MISMATCH"
            }
            if (artifact.credentialMode == "module-parameter") {
                val ramdisk = dev.maplesu.image.RamdiskInspector.inspect(output)
                val entry = ramdisk.entries.singleOrNull { it.path == ".maplesu_ultra/credential" }
                check(!ramdisk.malformed && entry != null && entry.isRegularFile &&
                    entry.permissions() == 0x180 && entry.size == UltraKernelCredential.LENGTH.toLong() &&
                    entry.sha256 == artifact.credentialSha256) {
                    "ULTRA_KERNEL_INIT_BOOT_CREDENTIAL_READBACK_MISMATCH"
                }
            }
            val receiptBytes = buildJsonObject {
                put("schemaVersion", 1)
                put("operation", "ULTRA_INIT_BOOT_GENERATE")
                put("source", "init-boot-bridge")
                put("partition", carrierPartition)
                put("sourceSha256", input.sha256)
                put("candidateSha256", after.sha256)
                if (normalizationSource != null) {
                    put("recoveryBaseSha256", source.sha256)
                    put("recoveryBaseImageBytes", source.fileSize)
                    put("normalizationSource", normalizationSource.name)
                }
                if (artifact.isKmiComponent) {
                    put("componentKmi", artifact.componentKmi)
                    put("componentBuildRelease", artifact.kernelRelease)
                    put("componentPageSize", artifact.component.pageSize)
                    put("componentCfiMode", artifact.component.cfiMode)
                    put("sourceGenerationSha256", artifact.component.sourceGenerationSha256)
                    put("runtimeFactsAbi", artifact.component.runtimeFactsAbi)
                } else put("targetBootSha256", requireNotNull(targetBootSha))
                if (kernelSha.isNotEmpty()) put("kernelSha256", kernelSha)
                if (kernelRelease.isNotEmpty()) put("kernelRelease", kernelRelease)
                put("handoffSha256", artifact.handoffSha256)
                put("bridgeSha256", artifact.bridgeSha256)
                put("targetInputSha256", buildJsonObject {
                    artifact.targetInputSha256.toSortedMap().forEach { (name, digest) -> put(name, digest) }
                })
                put("readbackVerified", true)
                put("sourceImageBytes", input.fileSize)
                put("candidateImageBytes", after.fileSize)
                put("originalRamdiskBytes", produced.originalRamdiskBytes)
                put("installedRamdiskBytes", produced.installedRamdiskBytes)
                put("recoveryRecordBytes", produced.recoveryRecordBytes)
                put("recoveryCoverage", if (normalizationSource == null) "FIRST_INPUT_EXACT" else "NORMALIZED_INPUT_EXACT")
                put("runtimeVerified", false)
            }.toString().toByteArray(Charsets.UTF_8)
            val pending = File.createTempFile("init-boot-receipt-", ".pending", workspace)
            pendingReceipt = pending
            initBootReceiptWriter(pending, receiptBytes)
            check(pending.readBytes().contentEquals(receiptBytes)) { "ULTRA_KERNEL_INIT_BOOT_RECEIPT_READBACK" }
            check(UltraKernelSpec.sha256(base) == source.sha256 &&
                UltraKernelSpec.sha256(installationInput) == input.sha256) { "ULTRA_KERNEL_INIT_BOOT_INPUT_CHANGED" }
            if (Thread.currentThread().isInterrupted) {
                throw java.io.InterruptedIOException("ULTRA_KERNEL_INIT_BOOT_CANCELLED")
            }
            check(!receipt.exists() && pending.renameTo(receipt)) { "ULTRA_KERNEL_INIT_BOOT_RECEIPT_PUBLISH" }
            receiptOwned = true
            if (Thread.currentThread().isInterrupted) {
                throw java.io.InterruptedIOException("ULTRA_KERNEL_INIT_BOOT_CANCELLED")
            }
            val changedBytes = if (installationInput == base) produced.changedBytes else
                Math.toIntExact(dev.maplesu.image.BootImageEquivalence.diff(installationInput, output).changedBytes)
            Candidate(output, after.sha256, input.sha256, kernelSha, kernelRelease,
                0, changedBytes, false, "experimental", "init-boot-bridge", false,
                expectedPinned = false, partition = carrierPartition, componentKmi = artifact.componentKmi)
        } catch (error: Throwable) {
            UltraKernelRecoveryEnvelope.cleanup(null, pendingReceipt, receipt.takeIf { receiptOwned },
                output.takeIf { outputOwned }, primary = error)
            throw error
        }
    }

    /** Reads the base image and reports whether it still carries pristine kernel bytes. */
    fun inspectBase(file: File, asset: UltraKernelPatchAsset, spec: UltraKernelSpec): BaseInspection {
        check(file.isFile && file.length() in 1..UltraKernelCatalog.MAX_IMAGE_BYTES) {
            "ULTRA_KERNEL_BASE_INVALID"
        }
        val report = BootImageInspector.inspect(file)
        val raw = decodeKernel(file, report)
        val release = UltraKernelSpec.releaseOf(raw)
        val mismatched = spec.mismatched(raw)
        val bootSha = UltraKernelSpec.sha256(file)
        val kernelSha = UltraKernelSpec.sha256(raw)
        val pinnedBaseMatches =
            asset.baseKernelSha256.isNotEmpty() && kernelSha == asset.baseKernelSha256
        return BaseInspection(
            file = file,
            bootSha256 = bootSha,
            size = file.length(),
            kernelRelease = release,
            kernelSha256 = kernelSha,
            pristine = mismatched.isEmpty(),
            mismatchedRecords = mismatched.size,
            knownStockBase = asset.knownBaseImageSha256.isNotEmpty() &&
                bootSha == asset.knownBaseImageSha256,
            baseKernelPinned = pinnedBaseMatches,
            expectedByteMode = !pinnedBaseMatches,
            alreadyPatched = spec.isApplied(raw),
        )
    }

    /**
     * Patches [base] for [asset] and returns a verified candidate boot image.
     * Throws with a stable `ULTRA_KERNEL_*` code on any mismatch.
     */
    @JvmOverloads
    fun generate(base: File, asset: UltraKernelPatchAsset, spec: UltraKernelSpec,
        retainedRecoveryRecord: ByteArray? = null, retainedRecoveryImage: File? = null): Candidate =
        synchronized(lock) {
            check(asset.ready) { "ULTRA_KERNEL_ASSET_UNAVAILABLE:${asset.id}" }
            check(base.isFile && base.length() in 1..UltraKernelCatalog.MAX_IMAGE_BYTES) {
                "ULTRA_KERNEL_BASE_INVALID"
            }
            workspace.mkdirs()
            val source = BootImageInspector.inspect(base)
            check(source.headerVersion in 0..4) { "ULTRA_KERNEL_BASE_HEADER_VERSION:${source.headerVersion}" }
            check(source.kernelSize in 1L..KernelCompression.MAX_KERNEL_BYTES.toLong()) {
                "ULTRA_KERNEL_BASE_NO_KERNEL"
            }
            val originalComponent = readKernelComponent(base, source)
            val raw = decodeKernelComponent(originalComponent, source.compression)
            val release = UltraKernelSpec.releaseOf(raw)
            // Series, not release: `6.6.50` may be patched with the `6.6.89` asset ("6.6.89 匹配 6.6").
            check(KernelSeries.same(release, asset.kernelRelease)) {
                "ULTRA_KERNEL_BASE_RELEASE_MISMATCH:" +
                    KernelSeries.detail(release, asset.kernelRelease)
            }
            val baseKernelSha = UltraKernelSpec.sha256(raw)
            // Full output pins apply to their original base; patch-site checks also cover a
            // byte-compatible kernel whose release banner happens to be identical.
            val strictBase = asset.baseKernelSha256.isNotEmpty() &&
                baseKernelSha == asset.baseKernelSha256
            // Original patch bytes do not prove that embedded call targets or struct fields
            // still match. A different pinned base must be rendered from its own symbols.
            check(asset.generated != null || asset.baseKernelSha256.isEmpty() || strictBase) {
                "ULTRA_KERNEL_TEMPLATE_BASE_MISMATCH:${asset.id}"
            }
            val expectedKernel = asset.expectedKernelSha256
            val expectedPinned = asset.generated == null && expectedKernel.isNotEmpty()
            val result = spec.apply(raw)
            val kernelSha = UltraKernelSpec.sha256(result.kernel)
            if (expectedKernel.isNotEmpty()) {
                check(kernelSha == expectedKernel) {
                    "ULTRA_KERNEL_RESULT_DIGEST_MISMATCH:$kernelSha"
                }
            }
            // A generated plan hands the builder the digests it derived on its own pass. Comparing
            // them is a cross-check between two independent appliers (`UltraKernelPlan.apply` and
            // `UltraKernelSpec.apply`), not a relaxation: any disagreement means the records changed
            // after the plan was made, or the base is not the kernel the plan was generated for.
            asset.generated?.let { derived ->
                check(spec.patches.size == derived.records) {
                    "ULTRA_KERNEL_GENERATE_RECORD_COUNT:${spec.patches.size}!=${derived.records}"
                }
                check(result.differingBytes == derived.changedBytes) {
                    "ULTRA_KERNEL_GENERATE_CHANGED_BYTES:${result.differingBytes}!=${derived.changedBytes}"
                }
            }
            // The final bound plan is the only producer of the portable inverse. ABI 0 means
            // unspecified; these diagnostic values do not identify a kernel or authorize callers.
            cleanup()
            check(workspace.isDirectory || workspace.mkdirs()) { "ULTRA_KERNEL_WORKSPACE" }
            val retainedFile = File(workspace, "retained-recovery-${java.util.UUID.randomUUID()}.bin")
            val container = File(workspace, "kernel-${asset.id}.img")
            val snapshot = File(workspace, "container-snapshot-${java.util.UUID.randomUUID()}.bin")
            var completeRecord: File? = null
            try {
                val hasRetainedFile = retainedRecoveryImage != null && BootImageRebuilder.extractRecoveryRecord(
                    retainedRecoveryImage, retainedFile, BootImageRebuilder.OutputPolicy.DISCARD_INVALID_AVB_IF_ZERO_TAIL)
                var exactContainerRecord = false
                var recoveryFallback: String? = null
                val retainedInverse = if (hasRetainedFile) UltraKernelRecoveryEnvelope.read(retainedFile, workspace).use {
                    exactContainerRecord = it.snapshot != null
                    it.inverse
                } else null
                val recoveryRecord = (asset as? UltraKernelGeneratedAsset)?.let { generated ->
                    UltraKernelRecoveryRecord.fromPlan(generated.plan, raw.size, 0, 0).encode()
                } ?: retainedRecoveryRecord?.copyOf() ?: retainedInverse
                check(retainedInverse == null || retainedInverse.contentEquals(recoveryRecord)) {
                    "ULTRA_KERNEL_RECOVERY_RETAINED_MISMATCH"
                }
                if (recoveryRecord != null) check(
                    UltraKernelRecoveryRecord.decode(recoveryRecord).restore(result.kernel).contentEquals(raw)
                ) { "ULTRA_KERNEL_RECOVERY_PREIMAGE_MISMATCH" }
                // RAW bytes are already a private, independently patched result. Encoding them
                // again would only duplicate the whole kernel while both input arrays are live.
                val containerBytes = if (source.compression == "RAW_ARM64") {
                    if (Thread.currentThread().isInterrupted) throw java.io.IOException("CANCELLED")
                    result.kernel
                } else KernelCompression.encodeKernelComponent(result.kernel, originalComponent, source.compression)
                container.writeBytes(containerBytes)
                val output = File(workspace, "candidate-${asset.id}-${kernelSha.take(12)}.img")
                output.delete()
                completeRecord = if (hasRetainedFile) retainedFile else null
                var usedCompleteRecord = false
                val rebuilt = run {
                    val policy = BootImageRebuilder.OutputPolicy.DISCARD_INVALID_AVB_IF_ZERO_TAIL
                    if (completeRecord == null && recoveryRecord != null && retainedRecoveryRecord == null) {
                        try {
                            val saved = BootImageRecovery.capture(base, BootImageRecovery.Component.KERNEL, snapshot, policy)
                            check(saved.originalImageBytes == source.fileSize && saved.originalImageSha256 == source.sha256 &&
                                saved.targetComponentBytes == originalComponent.size.toLong() &&
                                saved.targetComponentSha256 == UltraKernelSpec.sha256(originalComponent)) {
                                "ULTRA_KERNEL_RECOVERY_SOURCE_CHANGED"
                            }
                            completeRecord = UltraKernelRecoveryEnvelope.write(recoveryRecord, snapshot,
                                originalComponent.takeUnless { source.compression == "RAW_ARM64" }, workspace)
                            exactContainerRecord = true
                        } catch (error: BootImageRecovery.CapacityException) {
                            recoveryFallback = error.message
                        } catch (error: UltraKernelRecoveryEnvelope.CapacityException) {
                            recoveryFallback = error.message
                        }
                    }
                    val full = completeRecord
                    val report = if (full == null) {
                        if (recoveryRecord == null) BootImageRebuilder.rebuildWithPolicy(base, container, output, policy)
                        else BootImageRebuilder.rebuildWithPolicy(base, container, output, recoveryRecord, policy)
                    } else try {
                        BootImageRebuilder.rebuildWithRecoveryFile(base, container, output, full, policy,
                            if (hasRetainedFile) UltraKernelCatalog.MAX_IMAGE_BYTES else source.fileSize).also {
                            usedCompleteRecord = true
                        }
                    } catch (error: BootImageRebuilder.ValidationException) {
                        if (hasRetainedFile || error.message !in setOf("AVB_PARTITION_SIZE_LIMIT", "BOOT_IMAGE_SIZE_LIMIT",
                                "RECOVERY_RECORD_CAPACITY_LIMIT")) throw error
                        recoveryFallback = error.message
                        BootImageRebuilder.rebuildWithPolicy(base, container, output, recoveryRecord, policy)
                    }
                    if (usedCompleteRecord) {
                        val readback = File(workspace, "container-readback-${java.util.UUID.randomUUID()}.bin")
                        try {
                            check(BootImageRebuilder.extractRecoveryRecord(output, readback, policy) &&
                                readback.length() == full!!.length() &&
                                UltraKernelSpec.sha256(readback) == UltraKernelSpec.sha256(full)) {
                                "ULTRA_KERNEL_RECOVERY_READBACK_MISMATCH"
                            }
                        } finally { readback.delete() }
                    }
                    report
                }
                if (recoveryRecord != null && !usedCompleteRecord) check(
                    BootImageRebuilder.readRecoveryRecord(output).contentEquals(recoveryRecord)
                ) { "ULTRA_KERNEL_RECOVERY_READBACK_MISMATCH" }
                check(rebuilt.kernelChanged) { "ULTRA_KERNEL_REBUILD_UNCHANGED" }
                val after = BootImageInspector.inspect(output)
                check(after.fileSize == rebuilt.image.fileSize &&
                    after.fileSize in 1..UltraKernelCatalog.MAX_IMAGE_BYTES) {
                    "ULTRA_KERNEL_OUTPUT_SIZE:${after.fileSize}"
                }
                check(after.headerVersion == source.headerVersion && after.ramdiskSize == source.ramdiskSize) {
                    "ULTRA_KERNEL_OUTPUT_HEADER"
                }
                check(after.kernelSize == containerBytes.size.toLong()) { "ULTRA_KERNEL_OUTPUT_KERNEL_SIZE" }
                check(after.compression == source.compression) { "ULTRA_KERNEL_OUTPUT_COMPRESSION" }
                val componentSha = verifyKernelComponent(output, after, containerBytes)
                val readbackKernelSha = if (after.compression == "RAW_ARM64") componentSha
                    else UltraKernelSpec.sha256(decodeKernel(output, after))
                // The image must read back as exactly the kernel that was just built. In pinned mode
                // that is the asset's digest (checked above); in the derived modes it is the digest
                // computed here - either way the bytes on disk are proven to be the bytes that were
                // verified, which is the gate the user asked to keep.
                check(readbackKernelSha == kernelSha) {
                    "ULTRA_KERNEL_READBACK_MISMATCH"
                }
                val bootSha = UltraKernelSpec.sha256(output)
                check(after.sha256 == bootSha) { "ULTRA_KERNEL_READBACK_BOOT_MISMATCH" }
                val baseSha = UltraKernelSpec.sha256(base)
                check(base.length() == source.fileSize && baseSha == source.sha256) { "ULTRA_KERNEL_RECOVERY_SOURCE_CHANGED" }
                check(bootSha != baseSha) { "ULTRA_KERNEL_OUTPUT_UNCHANGED" }
                val knownImage = asset.knownBaseImageSha256.isNotEmpty() &&
                    baseSha == asset.knownBaseImageSha256 &&
                    asset.knownBaseImageBootSha256.isNotEmpty()
                if (knownImage) {
                    check(bootSha == asset.knownBaseImageBootSha256) {
                        "ULTRA_KERNEL_OUTPUT_NOT_PINNED:$bootSha"
                    }
                }
                // Keep the exact generated records for a later receipt-bound upgrade/reversal.
                if (asset is UltraKernelGeneratedAsset) {
                    val retained = File(workspace, "candidate-${asset.id}-${asset.specSha256}.spec")
                    val bytes = asset.plan.specText(asset.plan.credential).toByteArray(Charsets.UTF_8)
                    check(UltraKernelSpec.sha256(bytes) == asset.specSha256) { "ULTRA_KERNEL_GENERATED_SPEC_CHANGED" }
                    if (!retained.isFile) {
                        val pending = File(workspace, retained.name + ".pending")
                        pending.outputStream().use { stream -> stream.write(bytes); stream.fd.sync() }
                        check(pending.renameTo(retained)) { "ULTRA_KERNEL_GENERATED_SPEC_STORE_FAILED" }
                    }
                    check(UltraKernelSpec.sha256(retained) == asset.specSha256) { "ULTRA_KERNEL_GENERATED_SPEC_CHANGED" }
                }
                writeReceipt(
                    output, asset, source, after, baseSha, baseKernelSha, bootSha, kernelSha, release,
                    result.differingBytes, spec.patches.size, knownImage, strictBase,
                    false, expectedPinned,
                    if (usedCompleteRecord && exactContainerRecord) "FIRST_INPUT_EXACT"
                    else if (recoveryRecord != null) "KERNEL_WRITES_ONLY" else "NONE", recoveryFallback,
                )
                Candidate(
                    output, bootSha, baseSha, kernelSha, release, spec.patches.size,
                    result.differingBytes, knownImage, asset.verification, asset.source, strictBase,
                    expectedPinned,
                )
            } finally {
                container.delete()
                snapshot.delete()
                completeRecord?.delete()
                retainedFile.delete()
            }
        }

    /** Copies an externally supplied base image into the workspace, verifying size only. */
    fun importBase(input: java.io.InputStream): File {
        checkNotNull(workspace.mkdirs() || workspace.isDirectory) { "ULTRA_KERNEL_WORKSPACE" }
        val pending = File(workspace, "base-stock.pending")
        pending.delete()
        input.use { stream ->
            pending.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= UltraKernelCatalog.MAX_IMAGE_BYTES) { "ULTRA_KERNEL_BASE_TOO_LARGE" }
                    output.write(buffer, 0, count)
                }
                check(total in 1..UltraKernelCatalog.MAX_IMAGE_BYTES) { "ULTRA_KERNEL_BASE_INVALID" }
                output.fd.sync()
            }
        }
        val target = File(workspace, "base-stock.img")
        check(pending.renameTo(target)) { "ULTRA_KERNEL_BASE_IMPORT_FAILED" }
        return target
    }

    fun workspaceFile(name: String): File = File(workspace, name)

    fun cleanup() {
        workspace.listFiles().orEmpty().forEach { file ->
            if (file.name.startsWith("kernel-") || file.name.endsWith(".pending")) file.delete()
        }
    }

    private fun decodeKernel(file: File, report: BootImageInspector.Report): ByteArray =
        decodeKernelComponent(readKernelComponent(file, report), report.compression)

    private fun decodeKernelComponent(component: ByteArray, compression: String): ByteArray {
        if (Thread.currentThread().isInterrupted) throw java.io.IOException("CANCELLED")
        // Only this builder owns the component; the public codec still returns independent arrays.
        return if (compression == "RAW_ARM64") component
            else KernelCompression.decode(component, compression, KernelCompression.MAX_KERNEL_BYTES)
    }

    private fun verifyKernelComponent(
        file: File, report: BootImageInspector.Report, expected: ByteArray,
    ): String {
        check(report.kernelSize == expected.size.toLong()) { "ULTRA_KERNEL_OUTPUT_KERNEL_SIZE" }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        RandomAccessFile(file, "r").use { input ->
            input.seek(report.kernelOffset)
            var offset = 0
            while (offset < expected.size) {
                if (Thread.currentThread().isInterrupted) throw java.io.IOException("CANCELLED")
                val count = minOf(buffer.size, expected.size - offset)
                input.readFully(buffer, 0, count)
                for (index in 0 until count) check(buffer[index] == expected[offset + index]) {
                    "ULTRA_KERNEL_COMPONENT_READBACK_MISMATCH"
                }
                digest.update(buffer, 0, count)
                offset += count
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readKernelComponent(file: File, report: BootImageInspector.Report): ByteArray {
        check(report.kernelSize in 1L..KernelCompression.MAX_KERNEL_BYTES.toLong()) {
            "ULTRA_KERNEL_SIZE_LIMIT"
        }
        val container = ByteArray(report.kernelSize.toInt())
        RandomAccessFile(file, "r").use { input ->
            input.seek(report.kernelOffset)
            input.readFully(container)
        }
        return container
    }

    private fun writeReceipt(
        output: File, asset: UltraKernelPatchAsset, source: BootImageInspector.Report,
        after: BootImageInspector.Report, baseSha: String, baseKernelSha: String, bootSha: String,
        kernelSha: String, release: String, differingBytes: Int, patches: Int, knownImage: Boolean,
        strictBase: Boolean, seriesTemplate: Boolean, expectedPinned: Boolean,
        recoveryCoverage: String, recoveryFallback: String?,
    ) {
        val receipt = buildJsonObject {
            put("schemaVersion", 2)
            put("operation", "ULTRA_KERNEL_GENERATE")
            put("asset", asset.id)
            put("verification", asset.verification)
            put("source", asset.source)
            put("debugOnly", asset.debugOnly)
            put("knownBaseImage", knownImage)
            put("strictBase", strictBase)
            // Releases may be equal even when the base kernels differ; both base digests below
            // identify the exact template and input independently of their banner strings.
            put("kernelRelease", release)
            put("assetKernelRelease", asset.kernelRelease)
            put("seriesTemplate", seriesTemplate)
            // False when the expected kernel digest was derived here (generated plan, or a
            // same-series base that differs from the reference) instead of the asset's pin.
            put("expectedKernelPinned", expectedPinned)
            put("specSha256", asset.specSha256)
            put("patches", patches)
            put("differingBytes", differingBytes)
            put("baseKernelPinnedSha256", asset.baseKernelSha256)
            put("baseKernelSha256", baseKernelSha)
            put("patchedKernelSha256", kernelSha)
            put("baseBootSha256", baseSha)
            put("candidateBootSha256", bootSha)
            put("sourceImageBytes", source.fileSize)
            put("candidateImageBytes", after.fileSize)
            put("headerVersion", after.headerVersion)
            put("kernelBytes", after.kernelSize)
            put("ramdiskBytes", after.ramdiskSize)
            put("compression", after.compression)
            put("readbackVerified", true)
            put("recoveryCoverage", recoveryCoverage)
            recoveryFallback?.let { put("recoveryFallback", it) }
            // A generated plan states its own provenance: the receipt must never look like one for
            // a reviewed asset, and a reader must be able to see that no pin existed.
            asset.generated?.let { derived ->
                put("planOrigin", UltraKernelPlan.Origin.GENERATED.name)
                put("supportLevel", UltraKernelPlan.SupportLevel.EXPERIMENTAL.name)
                put("generatedRecords", derived.records)
                put("generatedChangedBytes", derived.changedBytes)
                put("generatedPatchedKernelSha256", derived.patchedKernelSha256)
            }
        }
        File(output.parentFile, "candidate-${asset.id}.json")
            .writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), receipt))
    }

    private companion object {
        val lock = Any()
    }
}
