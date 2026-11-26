package dev.maplesu.manager.ultrakernel

import dev.maplesu.image.BootImageInspector
import dev.maplesu.image.BootImageRebuilder
import dev.maplesu.image.BootImageRecovery
import dev.maplesu.image.AvbFooter
import dev.maplesu.image.KernelCompression
import dev.maplesu.image.KernelIdentity
import dev.maplesu.image.LenovoAvbSigner
import dev.maplesu.manager.workmode.control.AtomicControlFile
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One recovery entry for image-owned kernel inverses and legacy exact-container proofs. */
internal class UltraKernelOriginalRecovery(private val assets: UltraKernelAssetSource) {
    enum class Source {
        LOCAL_PREIMAGE, EXACT_SPEC_REVERSE, PINNED_SPEC_REVERSE, RECEIPT_BOUND_KERNEL_REVERSE,
        IMAGE_BOUND_KERNEL_REVERSE, ARCHIVED_KERNEL_REVERSE, IMAGE_BOUND_EXACT_INPUT,
    }
    class Recovered(val image: File, val source: Source, val kernelRelease: String,
        val deploymentNamespace: UltraKernelDeploymentNamespace? = null)
    class RecoveredKernel(val kernel: ByteArray, val deploymentNamespace: UltraKernelDeploymentNamespace? = null)

    // Previous packaged specs are visible only here, never to the install catalogue.
    private fun recoverySource(root: String) = object : UltraKernelAssetSource {
        private fun archived(path: String) = path.replaceFirst("ultra-kernels/", root)
        override fun list(directory: String) = assets.list(archived(directory))
        override fun read(path: String) = assets.read(archived(path))
    }
    private val recoveryAssets = recoverySource("ultra-kernels/recovery/")
    private val previousRecoveryAssets = recoverySource("ultra-kernels/recovery/previous/")
    private val legacyPacRecoveryAssets = recoverySource("ultra-kernels/recovery/pac-entry-legacy/")
    private val localCacheRecoveryAssets = recoverySource("ultra-kernels/recovery/ic-iallu/")
    private val transportBoundaryRecoveryAssets = recoverySource("ultra-kernels/recovery/transport-boundary-legacy/")
    private val preRangeTlbiRecoveryAssets = recoverySource("ultra-kernels/recovery/pre-range-tlbi/")
    private val preTailStubRecoveryAssets = recoverySource("ultra-kernels/recovery/pre-tail-stub-180/")

    /** App data may be gone. The complete boot and both kernel digests replace the local receipt. */
    fun recoverPinned(currentBoot: File, output: File): Recovered? {
        val receipt = runCatching { pinnedReceipt(currentBoot, output) }.getOrNull() ?: return null
        return reverse(currentBoot, receipt, output, Source.PINNED_SPEC_REVERSE)
    }

    /** Component migration prefers a complete image-owned baseline over a kernel-only archive. */
    fun recoverImageBoundExactInput(currentBoot: File, output: File): Recovered? = exactEmbedded(currentBoot, output)

    /** Inspect only a complete installed image with a verified reversible spec. */
    fun installedSpec(currentBoot: File, receiptsDirectory: File): UltraKernelSpec? = runCatching {
        val receipt = matchingReceipt(currentBoot, receiptsDirectory, generatedOnly = false)
            ?: pinnedReceipt(currentBoot, receiptsDirectory) ?: return null
        val kernel = decode(currentBoot, BootImageInspector.inspect(currentBoot))
        if (originalKernel(kernel, receipt) == null) return null
        receipt.spec
    }.getOrNull()

    /** Keep the signed container binding separate from the unsigned reconstruction proof. */
    fun retainSignedCandidate(unsigned: File, signed: File, receiptsDirectory: File): Boolean = runCatching {
        if (dev.maplesu.image.UltraInitBootProducer.recognizes(unsigned)) {
            return sameSignedPayload(unsigned, signed) && dev.maplesu.image.UltraInitBootProducer.recognizes(signed)
        }
        if (BootImageInspector.inspect(unsigned).kernelSize == 0L) return false
        val receipt = matchingReceipt(unsigned, receiptsDirectory, generatedOnly = false)
            ?: pinnedReceipt(unsigned, receiptsDirectory) ?: return false
        if (originalKernel(decode(unsigned, BootImageInspector.inspect(unsigned)), receipt) == null) return false
        val source = receipt.unsignedContainer ?: unsigned
        if (!sameSignedPayload(source, signed)) return false
        val sourceSha = UltraKernelSpec.sha256(source)
        val sourceBytes = source.length()
        val signedSha = UltraKernelSpec.sha256(signed)
        val directory = File(receiptsDirectory, "signed-candidates/$signedSha")
        val binding = File(directory, "binding.json")
        if (binding.exists() && readSignedReceipt(binding, signed, signedSha) != null) return true
        if (!directory.isDirectory && !directory.mkdirs()) return false
        val retained = File(directory, "unsigned.img")
        copySignedProof(source, retained)
        if (source.length() != sourceBytes || UltraKernelSpec.sha256(source) != sourceSha ||
            retained.length() != sourceBytes || UltraKernelSpec.sha256(retained) != sourceSha) return false
        val record = File(directory, "candidate-${receipt.asset.id}.json")
        if (receipt.file != null) copySignedProof(receipt.file, record)
        else record.writeText(pinnedRecord(receipt))
        if (receipt.asset.id.startsWith("generated-")) {
            val name = "candidate-${receipt.asset.id}-${receipt.asset.specSha256}.spec"
            copySignedProof(File(requireNotNull(receipt.file).parentFile, name), File(directory, name))
        }
        val pending = File(directory, "binding.json.pending")
        pending.outputStream().use { stream ->
            stream.write(buildJsonObject {
                put("schemaVersion", 1)
                put("operation", "ULTRA_KERNEL_SIGNED_GENERATE")
                put("signedBootSha256", signedSha)
                put("signedImageBytes", signed.length())
                put("unsignedBootSha256", sourceSha)
                put("unsignedImageBytes", sourceBytes)
                put("receipt", record.name)
                put("receiptSha256", UltraKernelSpec.sha256(record))
            }.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        try {
            Files.move(pending.toPath(), binding.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(pending.toPath(), binding.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        AtomicControlFile.syncDirectory(directory)
        readSignedReceipt(binding, signed, signedSha) != null && UltraKernelSpec.sha256(signed) == signedSha
    }.getOrDefault(false)

    /** Used only after a root-owned install record attests this exact signed partition digest. */
    fun retainPreviouslySignedCandidate(signed: File, receiptsDirectory: File): Boolean = runCatching {
        if (!signed.isFile || signed.length() !in 1..UltraKernelCatalog.MAX_IMAGE_BYTES) return false
        val signedSha = UltraKernelSpec.sha256(signed)
        val existing = File(receiptsDirectory, "signed-candidates/$signedSha/binding.json")
        if (existing.exists() && readSignedReceipt(existing, signed, signedSha) != null) return true
        val candidates = receiptsDirectory.listFiles { file ->
            file.isFile && file.name.startsWith("candidate-") && file.name.endsWith(".json")
        }.orEmpty().mapNotNull { record ->
            runCatching {
                if (record.length() !in 1..65_536) return@runCatching null
                val json = Json.parseToJsonElement(record.readText()) as? JsonObject ?: return@runCatching null
                val kernelSha = (json["patchedKernelSha256"] as? JsonPrimitive)?.contentOrNull
                    ?.takeIf(SHA256::matches) ?: return@runCatching null
                val image = File(receiptsDirectory, "${record.name.removeSuffix(".json")}-${kernelSha.take(12)}.img")
                val receipt = readReceipt(record, image, UltraKernelSpec.sha256(image)) ?: return@runCatching null
                if (originalKernel(decode(image, BootImageInspector.inspect(image)), receipt) == null ||
                    !sameSignedPayload(image, signed)) return@runCatching null
                image
            }.getOrNull()
        }.distinctBy { UltraKernelSpec.sha256(it) }
        if (candidates.size != 1) return false
        retainSignedCandidate(candidates.single(), signed, receiptsDirectory)
    }.getOrDefault(false)

    private fun copySignedProof(source: File, target: File) {
        if (source.canonicalFile == target.canonicalFile ||
            target.exists() && Files.isSameFile(source.toPath(), target.toPath())) return
        source.copyTo(target, overwrite = true)
    }

    private fun pinnedRecord(receipt: Receipt): String = buildJsonObject {
        put("schemaVersion", 2)
        put("operation", "ULTRA_KERNEL_GENERATE")
        put("readbackVerified", true)
        put("asset", receipt.asset.id)
        put("assetKernelRelease", receipt.asset.kernelRelease)
        put("kernelRelease", receipt.kernelRelease)
        put("specSha256", receipt.asset.specSha256)
        put("baseBootSha256", receipt.baseBootSha)
        put("baseKernelSha256", receipt.baseKernelSha)
        put("patchedKernelSha256", receipt.patchedKernelSha)
        put("sourceImageBytes", receipt.sourceBytes)
        put("candidateImageBytes", receipt.candidateBytes)
        put("candidateBootSha256", receipt.candidateBootSha)
    }.toString()

    private fun readSignedReceipt(binding: File, current: File, currentSha: String): Receipt? {
        if (!binding.isFile || binding.length() !in 1..65_536) return null
        val record = try {
            Json.parseToJsonElement(binding.readText()) as? JsonObject ?: return null
        } catch (_: SerializationException) { return null }
        fun text(name: String) = (record[name] as? JsonPrimitive)?.contentOrNull
        fun size(name: String) = (record[name] as? JsonPrimitive)?.longOrNull
        if ((record["schemaVersion"] as? JsonPrimitive)?.intOrNull != 1 ||
            text("operation") != "ULTRA_KERNEL_SIGNED_GENERATE" ||
            text("signedBootSha256") != currentSha || size("signedImageBytes") != current.length() ||
            binding.parentFile?.name != currentSha || !currentSha.matches(SHA256)) return null
        val unsigned = File(binding.parentFile, "unsigned.img")
        val unsignedSha = text("unsignedBootSha256")?.takeIf(SHA256::matches) ?: return null
        if (!unsigned.isFile || unsigned.length() !in 1..UltraKernelCatalog.MAX_IMAGE_BYTES ||
            size("unsignedImageBytes") != unsigned.length() || UltraKernelSpec.sha256(unsigned) != unsignedSha) return null
        val name = text("receipt")?.takeIf { it.matches(Regex("candidate-[A-Za-z0-9._-]+\\.json")) } ?: return null
        val file = File(binding.parentFile, name)
        if (!file.isFile || UltraKernelSpec.sha256(file) != text("receiptSha256")) return null
        val receipt = try {
            readReceipt(file, unsigned, unsignedSha)
        } catch (_: SerializationException) { return null } ?: return null
        if (!sameSignedPayload(unsigned, current) ||
            originalKernel(decode(current, BootImageInspector.inspect(current)), receipt) == null) return null
        return receipt.copy(unsignedContainer = unsigned)
    }

    private fun sameSignedPayload(unsigned: File, signed: File): Boolean {
        if (!unsigned.isFile || !signed.isFile || unsigned.length() !in 1..UltraKernelCatalog.MAX_IMAGE_BYTES ||
            signed.length() !in 1..UltraKernelCatalog.MAX_IMAGE_BYTES) return false
        val footer = AvbFooter.parse(signed)
        val before = AvbFooter.parse(unsigned)
        if (!footer.present || !footer.valid(signed.length()) || footer.originalImageSize !in 1..unsigned.length() ||
            before.present && (!before.valid(unsigned.length()) || before.originalImageSize != footer.originalImageSize)) return false
        val report = BootImageInspector.inspect(unsigned)
        val sections = listOf(report.kernelOffset to report.kernelSize, report.ramdiskOffset to report.ramdiskSize,
            report.secondOffset to report.secondSize, report.recoveryDtboOffset to report.recoveryDtboSize,
            report.dtbOffset to report.dtbSize, report.signatureOffset to report.signatureSize)
        if (sections.any { (offset, size) -> size > 0 && offset + size > footer.originalImageSize }) return false
        RandomAccessFile(unsigned, "r").use { left ->
            RandomAccessFile(signed, "r").use { right ->
                val a = ByteArray(64 * 1024)
                val b = ByteArray(a.size)
                var remaining = footer.originalImageSize
                while (remaining > 0) {
                    val count = minOf(a.size.toLong(), remaining).toInt()
                    left.readFully(a, 0, count); right.readFully(b, 0, count)
                    if ((0 until count).any { a[it] != b[it] }) return false
                    remaining -= count
                }
                if (!before.present) while (left.filePointer < left.length()) {
                    val count = left.read(a)
                    if ((0 until count).any { a[it] != 0.toByte() }) return false
                }
            }
        }
        if (report.kernelSize == 0L) LenovoAvbSigner.verify(mapOf("init_boot" to signed))
        else LenovoAvbSigner.verifySigned(LenovoAvbSigner.Mode.CHAINED, "boot", mapOf("boot" to signed))
        return true
    }

    private fun pinnedReceipt(currentBoot: File, workspace: File): Receipt? {
        if (!currentBoot.isFile || currentBoot.length() !in 1..UltraKernelCatalog.MAX_IMAGE_BYTES) return null
        val report = BootImageInspector.inspect(currentBoot)
        val patched = decode(currentBoot, report)
        val release = UltraKernelSpec.releaseOf(patched)
        val patchedSha = UltraKernelSpec.sha256(patched)
        val bootSha = UltraKernelSpec.sha256(currentBoot)
        val matches = sequenceOf(assets, recoveryAssets, previousRecoveryAssets, legacyPacRecoveryAssets, localCacheRecoveryAssets, transportBoundaryRecoveryAssets, preRangeTlbiRecoveryAssets, preTailStubRecoveryAssets).flatMap { source ->
            val selection = UltraKernelFlow(workspace, source).selectForRelease(release)
            if (selection == null) emptySequence()
            else selection.usable.asSequence().map { source to it }
        }.filter { (_, asset) ->
            asset.expectedKernelSha256 == patchedSha && asset.knownBaseImageBootSha256 == bootSha &&
                asset.baseKernelSha256.matches(SHA256) && asset.knownBaseImageSha256.matches(SHA256) &&
                asset.imageSizeBytes == currentBoot.length()
        }.distinctBy { (_, asset) -> asset.specSha256 }.toList()
        if (matches.size != 1) return null
        val (source, asset) = matches.single()
        val spec = when (asset) {
            is UltraKernelReleaseAsset -> loadReleaseSpec(source, asset)
            is UltraKernelVariant -> UltraKernelCatalog.spec(source, asset)
            else -> return null
        }
        return Receipt(asset, spec, release, asset.knownBaseImageSha256,
            asset.baseKernelSha256, patchedSha, currentBoot.length(), currentBoot.length(), bootSha)
    }

    fun recover(currentBoot: File, receiptsDirectory: File, originals: List<File>, output: File,
        generatedOnly: Boolean = false): Recovered? {
        if (dev.maplesu.image.UltraInitBootProducer.recognizes(currentBoot)) {
            check(output.isDirectory || output.mkdirs()) { "ULTRA_KERNEL_RECOVERY_OUTPUT_CREATE_FAILED" }
            val namespace = dev.maplesu.image.UltraInitBootProducer.readDeploymentNamespace(currentBoot)
                ?.let(UltraKernelDeploymentNamespace::decode)
            val restored = dev.maplesu.image.UltraInitBootProducer.restore(currentBoot,
                File(output, "module-carrier-original.img"))
            val report = BootImageInspector.inspect(restored)
            val release = if (report.kernelSize > 0L) UltraKernelSpec.releaseOf(UltraKernelPlan.readKernel(restored)) else ""
            return Recovered(restored, Source.IMAGE_BOUND_EXACT_INPUT, release, namespace)
        }
        // An exact archived kernel proof also covers historical diagnostic images whose
        // embedded inverse predates their final writes. Unknown images still fail closed.
        if (generatedOnly) archivedKernel(currentBoot)?.let { pristine ->
            return reverseEmbedded(currentBoot, pristine.kernel, output, Source.ARCHIVED_KERNEL_REVERSE, pristine.deploymentNamespace)
        }
        if (generatedOnly) exactEmbedded(currentBoot, output)?.let { return it }
        if (generatedOnly) embeddedKernel(currentBoot, output)?.let { pristine ->
            return reverseEmbedded(currentBoot, pristine.kernel, output, deploymentNamespace = pristine.deploymentNamespace)
        }
        val pinned = if (generatedOnly) null else runCatching { pinnedReceipt(currentBoot, output) }.getOrNull()
        val receipt = matchingReceipt(currentBoot, receiptsDirectory, generatedOnly)
        val originalReceipts = listOfNotNull(pinned, receipt)
        if (originalReceipts.isEmpty()) return null
        if (!output.isDirectory && !output.mkdirs()) throw IOException("ULTRA_KERNEL_RECOVERY_OUTPUT_CREATE_FAILED:$output")
        originals.forEachIndexed { index, original ->
            val originalReceipt = originalReceipts.firstOrNull { candidate ->
                runCatching { proveOriginal(original, candidate, File(output, "local-proof-$index")) }
                    .getOrElse { error ->
                        if (error is IOException && error !is BootImageRebuilder.ValidationException) throw error
                        false
                    }
            } ?: return@forEachIndexed
            val retained = File(output, "original.img")
            if (original.canonicalFile != retained.canonicalFile) original.copyTo(retained, overwrite = true)
            if (UltraKernelSpec.sha256(retained) == originalReceipt.baseBootSha) {
                return Recovered(retained, Source.LOCAL_PREIMAGE, originalReceipt.kernelRelease)
            }
            return null
        }
        if (pinned != null) reverse(currentBoot, pinned, output, Source.PINNED_SPEC_REVERSE)?.let { return it }
        if (receipt == null) return null
        // A generated upgrade needs the proven kernel preimage in the installed container.
        // Old signatures and AVB layout may have been deliberately removed by the first build;
        // exact stock recovery keeps its stronger whole-boot digest requirement.
        return reverse(currentBoot, receipt, output,
            if (generatedOnly) Source.RECEIPT_BOUND_KERNEL_REVERSE else Source.EXACT_SPEC_REVERSE,
            kernelPreimageOnly = generatedOnly)
    }

    /** Read-only feasibility path: the pristine bytes and namespace share one verified input. */
    fun generatedKernel(currentBoot: File, receiptsDirectory: File): ByteArray? =
        generatedKernelWithDeployment(currentBoot, receiptsDirectory)?.kernel

    fun generatedKernelWithDeployment(currentBoot: File, receiptsDirectory: File): RecoveredKernel? {
        archivedKernel(currentBoot)?.let { return it }
        embeddedKernel(currentBoot, receiptsDirectory)?.let { return it }
        return runCatching {
            val receipt = matchingReceipt(currentBoot, receiptsDirectory, generatedOnly = true)
                ?: pinnedReceipt(currentBoot, receiptsDirectory) ?: return null
            originalKernel(decode(currentBoot, BootImageInspector.inspect(currentBoot)), receipt)?.let(::RecoveredKernel)
        }.getOrNull()
    }

    /** A complete archived identity proves a legacy image, not an unknown current executor. */
    fun archivedKernelUpdateRequired(currentBoot: File): Boolean = archivedKernel(currentBoot) != null

    private fun embeddedKernel(currentBoot: File, workspace: File): RecoveredKernel? {
        if (!currentBoot.isFile || currentBoot.length() !in 1..UltraKernelCatalog.MAX_IMAGE_BYTES) return null
        check(workspace.isDirectory || workspace.mkdirs()) { "ULTRA_KERNEL_RECOVERY_OUTPUT_CREATE_FAILED" }
        val directory = Files.createTempDirectory(workspace.toPath(), "recovery-read-").toFile()
        val record = File(directory, "record.bin")
        try {
            if (!BootImageRebuilder.extractRecoveryRecord(currentBoot, record,
                    BootImageRebuilder.OutputPolicy.DISCARD_INVALID_AVB_IF_ZERO_TAIL)) return null
            return UltraKernelRecoveryEnvelope.read(record, directory).use { payload ->
                val inverse = UltraKernelRecoveryRecord.decode(payload.inverse)
                val current = decode(currentBoot, BootImageInspector.inspect(currentBoot))
                val deployment = inverse.deploymentNamespace(current)
                RecoveredKernel(inverse.restore(current), deployment)
            }
        } finally {
            record.delete()
            directory.delete()
        }
    }

    private fun exactEmbedded(currentBoot: File, output: File): Recovered? {
        if (!currentBoot.isFile || currentBoot.length() !in 1..UltraKernelCatalog.MAX_IMAGE_BYTES) return null
        if (!output.isDirectory && !output.mkdirs()) throw IOException("ULTRA_KERNEL_RECOVERY_OUTPUT_CREATE_FAILED:$output")
        val directory = Files.createTempDirectory(output.toPath(), "exact-recovery-").toFile()
        val record = File(directory, "record.bin")
        val rawTarget = File(directory, "original-component.bin")
        val original = File(directory, "original.img")
        var complete = false
        var failure: Throwable? = null
        try {
            if (!BootImageRebuilder.extractRecoveryRecord(currentBoot, record,
                    BootImageRebuilder.OutputPolicy.DISCARD_INVALID_AVB_IF_ZERO_TAIL)) return null
            val recovered = UltraKernelRecoveryEnvelope.read(record, directory).use { payload ->
                val snapshot = payload.snapshot ?: return@use null
                val inverse = UltraKernelRecoveryRecord.decode(payload.inverse)
                val current = decode(currentBoot, BootImageInspector.inspect(currentBoot))
                val deployment = inverse.deploymentNamespace(current)
                val raw = inverse.restore(current)
                val release = UltraKernelSpec.releaseOf(raw)
                val saved = BootImageRecovery.read(snapshot)
                check(saved.target == BootImageRecovery.Component.KERNEL &&
                    saved.targetComponentBytes in 1..KernelCompression.MAX_KERNEL_BYTES.toLong()) {
                    "ULTRA_KERNEL_RECOVERY_COMPONENT_MISMATCH"
                }
                val target = payload.originalComponent ?: rawTarget.also {
                    if (raw.size.toLong() != saved.targetComponentBytes || UltraKernelSpec.sha256(raw) !=
                        saved.targetComponentSha256) return@use null
                    it.outputStream().use { stream -> stream.write(raw); stream.fd.sync() }
                }
                // The original stream may use a different codec from a later repacked container.
                val component = target.readBytes()
                if (!KernelCompression.decode(component, KernelIdentity.formatOf(component),
                        KernelCompression.MAX_KERNEL_BYTES).contentEquals(raw)) return@use null
                try {
                    BootImageRecovery.restore(currentBoot, target, snapshot, original)
                } catch (error: IOException) {
                    // A later component edit keeps its own bytes through the existing raw inverse.
                    if (error.message == "BOOT_RECOVERY_COMPONENT_CHANGED") return@use null
                    throw error
                }
                Recovered(original, Source.IMAGE_BOUND_EXACT_INPUT, release, deployment)
            }
            // Publish the owned image only after Payload.close and temporary cleanup both succeed.
            UltraKernelRecoveryEnvelope.cleanup(null, rawTarget, record)
            complete = recovered != null
            return recovered
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            if (!complete) UltraKernelRecoveryEnvelope.cleanup(directory, original, rawTarget, record, primary = failure)
        }
    }

    /** Legacy images predate embedded inverses. Match the whole raw kernel, never its release alone. */
    private fun archivedKernel(currentBoot: File): RecoveredKernel? {
        if (!currentBoot.isFile || currentBoot.length() !in 1..UltraKernelCatalog.MAX_IMAGE_BYTES) return null
        val report = BootImageInspector.inspect(currentBoot)
        if (report.kernelSize !in 1..KernelCompression.MAX_KERNEL_BYTES.toLong()) return null
        val current = decode(currentBoot, report)
        val digest = UltraKernelSpec.sha256(current)
        val root = "ultra-kernels/recovery/kernel-inverses"
        val name = "$digest.json"
        if (name !in assets.list(root)) return null
        val metadata = assets.read("$root/$name")
        check(metadata.size in 1..65_536) { "ULTRA_KERNEL_ARCHIVED_METADATA_SIZE" }
        val record = Json.parseToJsonElement(metadata.toString(Charsets.UTF_8)) as? JsonObject
            ?: error("ULTRA_KERNEL_ARCHIVED_METADATA_INVALID")
        fun text(key: String) = (record[key] as? JsonPrimitive)?.contentOrNull
        check((record["schemaVersion"] as? JsonPrimitive)?.intOrNull == 1 &&
            (record["kernelBytes"] as? JsonPrimitive)?.intOrNull == current.size &&
            text("patchedKernelSha256") == digest &&
            text("kernelRelease") == UltraKernelSpec.releaseOf(current)) { "ULTRA_KERNEL_ARCHIVED_BINDING_MISMATCH" }
        val inverse = assets.read("$root/$digest.bin")
        check(inverse.size <= UltraKernelRecoveryRecord.MAX_BYTES &&
            UltraKernelSpec.sha256(inverse) == text("recordSha256")) { "ULTRA_KERNEL_ARCHIVED_RECORD_MISMATCH" }
        val decoded = UltraKernelRecoveryRecord.decode(inverse)
        val deployment = decoded.deploymentNamespace(current)
        val restored = decoded.restore(current)
        check(UltraKernelSpec.sha256(restored) == text("originalKernelSha256") &&
            UltraKernelSpec.releaseOf(restored) == text("kernelRelease")) { "ULTRA_KERNEL_ARCHIVED_PREIMAGE_MISMATCH" }
        return RecoveredKernel(restored, deployment)
    }

    /** Removes only our verified writes; this is not a proof of the original container bytes. */
    private fun reverseEmbedded(currentBoot: File, pristine: ByteArray, output: File,
        source: Source = Source.IMAGE_BOUND_KERNEL_REVERSE,
        deploymentNamespace: UltraKernelDeploymentNamespace? = null): Recovered {
        if (!output.isDirectory && !output.mkdirs()) throw IOException("ULTRA_KERNEL_RECOVERY_OUTPUT_CREATE_FAILED:$output")
        val original = File(output, "original.img")
        check(original.canonicalFile != currentBoot.canonicalFile) { "ULTRA_KERNEL_RECOVERY_OUTPUT_IS_SOURCE" }
        val report = BootImageInspector.inspect(currentBoot)
        val component = KernelCompression.encodeKernelComponent(pristine,
            readKernelComponent(currentBoot, report), report.compression)
        val kernel = File(output, "original-kernel.img")
        kernel.writeBytes(component)
        try {
            if (original.exists() && !original.delete()) throw IOException("ULTRA_KERNEL_RECOVERY_OUTPUT_DELETE_FAILED:$original")
            BootImageRebuilder.rebuildWithPolicy(currentBoot, kernel, original, null,
                BootImageRebuilder.OutputPolicy.DISCARD_INVALID_AVB_IF_ZERO_TAIL)
            check(readKernelComponent(original, BootImageInspector.inspect(original)).contentEquals(component) &&
                BootImageRebuilder.readRecoveryRecord(original) == null) { "ULTRA_KERNEL_RECOVERY_READBACK_MISMATCH" }
            return Recovered(original, source, UltraKernelSpec.releaseOf(pristine), deploymentNamespace)
        } catch (error: Exception) {
            original.delete()
            throw error
        } finally {
            kernel.delete()
        }
    }

    private fun matchingReceipt(currentBoot: File, receiptsDirectory: File, generatedOnly: Boolean): Receipt? {
        if (!currentBoot.isFile || currentBoot.length() !in 1..UltraKernelCatalog.MAX_IMAGE_BYTES) return null
        val receipts = receiptsDirectory.listFiles { file ->
            file.isFile && file.name.startsWith(if (generatedOnly) "candidate-generated-" else "candidate-") &&
                file.name.endsWith(".json")
        }.orEmpty()
        val currentSha = UltraKernelSpec.sha256(currentBoot)
        val matching = receipts.mapNotNull { file ->
            runCatching { readReceipt(file, currentBoot, currentSha) }.getOrNull()
        } + listOfNotNull(runCatching {
            readSignedReceipt(File(receiptsDirectory, "signed-candidates/$currentSha/binding.json"), currentBoot, currentSha)
                ?.takeIf { !generatedOnly || it.asset.id.startsWith("generated-") }
        }.getOrNull())
        if (matching.size != 1) return null
        return matching.single()
    }

    private fun reverse(currentBoot: File, receipt: Receipt, output: File, source: Source,
        kernelPreimageOnly: Boolean = false): Recovered? {
        val container = receipt.unsignedContainer ?: currentBoot
        val report = runCatching { BootImageInspector.inspect(container) }.getOrNull() ?: return null
        if (report.headerVersion !in 0..4) return null
        val pristine = runCatching { originalKernel(decode(currentBoot, report), receipt) }
            .getOrNull() ?: return null
        if (!kernelPreimageOnly) exactEmbedded(container, output)?.let { exact ->
            var proven = false
            var failure: Throwable? = null
            try {
                proven = proveOriginal(exact.image, receipt, File(output, "exact-replay-proof"))
                if (proven) return Recovered(exact.image, source, receipt.kernelRelease, exact.deploymentNamespace)
            } catch (error: Throwable) {
                failure = error
                throw error
            } finally {
                if (!proven) UltraKernelRecoveryEnvelope.cleanup(exact.image.parentFile, exact.image, primary = failure)
            }
        }
        return runCatching {
            if (!output.isDirectory && !output.mkdirs()) throw IOException("ULTRA_KERNEL_RECOVERY_OUTPUT_CREATE_FAILED:$output")
            val kernel = File(output, "original-kernel.img")
            val component = KernelCompression.encodeKernelComponent(pristine, readKernelComponent(container, report), report.compression)
            kernel.writeBytes(component)
            val original = File(output, "original.img")
            if (original.canonicalFile == currentBoot.canonicalFile) return null
            if (original.exists() && !original.delete()) throw IOException("ULTRA_KERNEL_RECOVERY_OUTPUT_DELETE_FAILED:$original")
            try {
                BootImageRebuilder.rebuild(container, kernel, original, null)
            } finally {
                kernel.delete()
            }
            if (!readKernelComponent(original, BootImageInspector.inspect(original)).contentEquals(component)) return null
            // The builder may compact a footerless full-partition source. Only its proven zero
            // padding can be restored here; the complete original digest still has to match.
            if (original.length() < receipt.sourceBytes) {
                RandomAccessFile(original, "rw").use { it.setLength(receipt.sourceBytes) }
            }
            if (!proveOriginal(original, receipt, File(output, "reverse-proof"),
                    requireOriginalContainer = !kernelPreimageOnly)) return null
            Recovered(original, source, receipt.kernelRelease)
        }.getOrElse { error ->
            if (error is IOException && error !is BootImageRebuilder.ValidationException) throw error
            null
        }
    }

    private fun originalKernel(patched: ByteArray, receipt: Receipt): ByteArray? {
        if (UltraKernelSpec.sha256(patched) != receipt.patchedKernelSha || !receipt.spec.isApplied(patched)) return null
        val originalsByOffset = receipt.spec.patches.sortedBy { it.offset }
        val allReplacedBytesKnown = receipt.spec.patches.all { patch ->
            var covered = patch.offset
            val end = patch.offset + patch.replacement.size
            for (original in originalsByOffset) {
                if (original.offset > covered) break
                covered = maxOf(covered, original.offset + original.original.size)
                if (covered >= end) break
            }
            covered >= end
        }
        if (!allReplacedBytesKnown) return null
        val pristine = patched.copyOf()
        for (patch in receipt.spec.patches) {
            if (patch.offset < 0 || patch.offset + patch.original.size > pristine.size) return null
            patch.original.copyInto(pristine, patch.offset.toInt())
        }
        // Conflicting originals in overlapping records cannot both match this one preimage.
        if (receipt.spec.mismatched(pristine).isNotEmpty() ||
            UltraKernelSpec.sha256(pristine) != receipt.baseKernelSha ||
            !receipt.spec.apply(pristine).kernel.contentEquals(patched)) return null
        return pristine
    }

    private fun proveOriginal(original: File, receipt: Receipt, workspace: File,
        requireOriginalContainer: Boolean = true): Boolean {
        if (!original.isFile || original.length() !in 1..UltraKernelCatalog.MAX_IMAGE_BYTES) return false
        if (requireOriginalContainer && (original.length() != receipt.sourceBytes ||
                UltraKernelSpec.sha256(original) != receipt.baseBootSha)) return false
        // Kernel-only recovery still proves every original byte, both kernel digests, and
        // reconstruction of the complete installed candidate (not just its release string).
        try {
            val builder = UltraKernelBuilder(workspace)
            val inspected = runCatching { builder.inspectBase(original, receipt.asset, receipt.spec) }
                .getOrNull() ?: return false
            if (!inspected.pristine || inspected.kernelSha256 != receipt.baseKernelSha ||
                inspected.kernelRelease != receipt.kernelRelease) return false
            val rebuilt = builder.generate(original, receipt.asset, receipt.spec, receipt.recoveryRecord,
                receipt.recoveryImage)
            return rebuilt.bootSha256 == receipt.candidateBootSha && rebuilt.image.length() == receipt.candidateBytes
        } finally {
            workspace.deleteRecursively()
        }
    }

    private fun readReceipt(file: File, current: File, currentSha: String): Receipt? {
        if (file.length() !in 1..65_536) return null
        val record = Json.parseToJsonElement(file.readText()) as? JsonObject ?: return null
        fun primitive(name: String) = record[name] as? JsonPrimitive
        fun text(name: String) = primitive(name)?.contentOrNull
        fun digest(name: String) = text(name)?.takeIf(SHA256::matches)
        if (primitive("schemaVersion")?.intOrNull != 2 || text("operation") != "ULTRA_KERNEL_GENERATE" ||
            primitive("readbackVerified")?.booleanOrNull != true) return null
        val generated = text("planOrigin") == UltraKernelPlan.Origin.GENERATED.name
        if (!generated && !text("planOrigin").isNullOrBlank()) return null
        val candidateSha = digest("candidateBootSha256") ?: return null
        val candidateBytes = primitive("candidateImageBytes")?.longOrNull
            ?.takeIf { it in 1..UltraKernelCatalog.MAX_IMAGE_BYTES } ?: return null
        if (!matchesInstalledCandidate(current, candidateSha, candidateBytes, currentSha)) return null
        val id = text("asset") ?: return null
        if (file.name != "candidate-$id.json") return null
        val assetRelease = text("assetKernelRelease") ?: return null
        val specSha = digest("specSha256") ?: return null
        val selected: Pair<UltraKernelPatchAsset, UltraKernelSpec> = if (generated) {
            if (!id.matches(Regex("generated-[A-Za-z0-9._-]{1,48}")) ||
                text("source") != UltraKernelGeneratedAsset.SOURCE ||
                text("verification") != UltraKernelPatchAsset.VERIFICATION_EXPERIMENTAL ||
                text("supportLevel") != UltraKernelPlan.SupportLevel.EXPERIMENTAL.name ||
                primitive("expectedKernelPinned")?.booleanOrNull != false ||
                text("generatedPatchedKernelSha256") != digest("patchedKernelSha256")) return null
            val retained = File(file.parentFile, "candidate-$id-$specSha.spec")
            if (retained.canonicalFile.parentFile != file.canonicalFile.parentFile ||
                !retained.isFile || retained.length() !in 1..UltraKernelSpec.MAX_SPEC_BYTES ||
                UltraKernelSpec.sha256(retained) != specSha) return null
            val spec = UltraKernelSpec.read(retained)
            if (!UltraKernelCredential.isValid(spec.key) || spec.kernelRelease != assetRelease ||
                spec.kernelRelease != text("kernelRelease") ||
                spec.patches.size != primitive("patches")?.intOrNull) return null
            // This private asset is used solely to prove the original; it never enters the catalogue.
            UltraKernelReleaseAsset(id, id, "", retained.name, specSha, spec.patches.size, assetRelease,
                digest("baseKernelSha256") ?: return null, digest("patchedKernelSha256") ?: return null,
                digest("baseBootSha256") ?: return null, candidateSha,
                primitive("sourceImageBytes")?.longOrNull ?: return null,
                primitive("ramdiskBytes")?.longOrNull ?: return null,
                UltraKernelGeneratedAsset.SOURCE, UltraKernelPatchAsset.VERIFICATION_EXPERIMENTAL) to spec
        } else {
            val (source, asset) = sequenceOf(assets, recoveryAssets, previousRecoveryAssets, legacyPacRecoveryAssets, localCacheRecoveryAssets, transportBoundaryRecoveryAssets, preRangeTlbiRecoveryAssets, preTailStubRecoveryAssets).mapNotNull { source ->
                UltraKernelFlow(file.absoluteFile.parentFile!!, source).selectForRelease(assetRelease)?.usable
                    ?.singleOrNull { it.id == id && it.kernelRelease == assetRelease && it.specSha256 == specSha }
                    ?.let { source to it }
            }.firstOrNull() ?: return null
            val spec = when (asset) {
                is UltraKernelReleaseAsset -> loadReleaseSpec(source, asset)
                is UltraKernelVariant -> UltraKernelCatalog.spec(source, asset)
                else -> return null
            }
            asset to spec
        }
        val (asset, spec) = selected
        return Receipt(asset, spec, text("kernelRelease") ?: return null,
            digest("baseBootSha256") ?: return null, digest("baseKernelSha256") ?: return null,
            digest("patchedKernelSha256") ?: return null,
            primitive("sourceImageBytes")?.longOrNull?.takeIf { it in 1..UltraKernelCatalog.MAX_IMAGE_BYTES } ?: return null,
            candidateBytes, candidateSha, file = file,
            recoveryImage = current)
    }

    private fun matchesInstalledCandidate(current: File, expectedSha: String, size: Long, fullSha: String): Boolean {
        if (current.length() == size) return fullSha == expectedSha
        if (current.length() < size) return false
        val digest = MessageDigest.getInstance("SHA-256")
        current.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var remaining = size
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (count <= 0) return false
                digest.update(buffer, 0, count)
                remaining -= count
            }
            if (digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) } != expectedSha) return false
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if ((0 until count).any { buffer[it] != 0.toByte() }) return false
            }
        }
        return true
    }

    private fun decode(image: File, report: BootImageInspector.Report): ByteArray =
        KernelCompression.decode(readKernelComponent(image, report), report.compression, KernelCompression.MAX_KERNEL_BYTES)

    private fun readKernelComponent(image: File, report: BootImageInspector.Report): ByteArray {
        check(report.kernelSize in 1..KernelCompression.MAX_KERNEL_BYTES.toLong())
        val compressed = ByteArray(report.kernelSize.toInt())
        RandomAccessFile(image, "r").use { input -> input.seek(report.kernelOffset); input.readFully(compressed) }
        return compressed
    }

    private data class Receipt(val asset: UltraKernelPatchAsset, val spec: UltraKernelSpec,
        val kernelRelease: String, val baseBootSha: String, val baseKernelSha: String,
        val patchedKernelSha: String, val sourceBytes: Long, val candidateBytes: Long,
        val candidateBootSha: String, val file: File? = null, val unsignedContainer: File? = null,
        val recoveryRecord: ByteArray? = null, val recoveryImage: File? = null)

    private companion object { val SHA256 = Regex("[0-9a-f]{64}") }
}
