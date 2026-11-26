package dev.maplesu.image;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;

/** Shared Android/desktop component writer. No kernel patching, signing or device I/O. */
public final class BootImageRebuilder {
    private static final int PAGE = 4096;
    private static final long MAX_IMAGE = 512L * 1024 * 1024;
    private static final long MAX_KERNEL = 128L * 1024 * 1024;
    /** AOSP boot v4 trailing GKI certificate pair (two 8 KiB certificates). */
    private static final long GKI_PAIR_BYTES = 16384;
    private static final byte[] RECOVERY_MAGIC = {'M', 'S', 'U', 'R', 'R', 'E', 'G', 0};
    private static final int RECOVERY_HEADER_BYTES = 56;
    private static final int MAX_RECOVERY_BYTES = 1024 * 1024;
    public static final long MAX_RECOVERY_FILE_BYTES = 192L * 1024 * 1024;

    private BootImageRebuilder() {}

    interface Checkpoint { void afterWrite() throws IOException; }

    public enum OutputPolicy {
        STRICT,
        /** Discard an invalid terminal footer only when its entire post-component region is zero. */
        DISCARD_INVALID_AVB_IF_ZERO_TAIL
    }

    public static final class Report {
        public final BootImageInspector.Report source, image;
        public final String replacementKernelSha256;
        public final boolean kernelChanged;
        public final String replacementRamdiskSha256;
        public final boolean ramdiskChanged;
        public final long removedTrailingBytes;
        public final int declaredSignatures, trailingGkiCertificates;
        /** A valid footer retained in the output, preserving the original report contract. */
        public final boolean avbFooterPresent;
        public final boolean sourceAvbFooterPresent, avbFooterRemoved;
        public final String avbFooterDiscardReason;
        /** The declared signature slot existed but was zero-filled (nothing to drop). */
        public final boolean declaredSignatureSlotEmpty;
        /** Every byte range this rebuild may have changed, in output coordinates. */
        public final BootImageEquivalence.Journal journal;
        /** Measured proof that the output only differs inside {@link #journal}. */
        public final BootImageEquivalence.Verification equivalence;

        private Report(BootImageInspector.Report source, BootImageInspector.Report image,
                       String replacementHash, boolean changed, Metadata metadata,
                       BootImageEquivalence.Journal journal, BootImageEquivalence.Verification equivalence,
                       boolean replacingRamdisk) {
            this.source = source; this.image = image;
            replacementKernelSha256 = replacingRamdisk ? null : replacementHash;
            kernelChanged = !replacingRamdisk && changed;
            replacementRamdiskSha256 = replacingRamdisk ? replacementHash : null;
            ramdiskChanged = replacingRamdisk && changed;
            removedTrailingBytes = metadata.removedBytes;
            declaredSignatures = metadata.declared; trailingGkiCertificates = metadata.gki;
            avbFooterPresent = metadata.footer;
            sourceAvbFooterPresent = metadata.sourceFooter;
            avbFooterDiscardReason = metadata.discardedFooterReason;
            avbFooterRemoved = avbFooterDiscardReason != null;
            declaredSignatureSlotEmpty = metadata.emptySignature;
            this.journal = journal; this.equivalence = equivalence;
        }

        public String toJson() {
            return String.format(Locale.ROOT,
                    "{\"schemaVersion\":1,\"operation\":\"REBUILD_%s_V%d\",\"source\":%s,\"image\":%s,"
                    + "\"replacementKernelSha256\":%s,\"kernelChanged\":%s,"
                    + "\"replacementRamdiskSha256\":%s,\"ramdiskChanged\":%s,\"removedTrailingBytes\":%d,"
                    + "\"declaredSignaturesRemoved\":%d,\"declaredSignatureSlotEmpty\":%s,"
                    + "\"trailingGkiCertificatesRemoved\":%d,\"avbFooterRemoved\":%s,"
                    + "\"avbFooterPreserved\":%s,\"sourceAvbFooterPresent\":%s,\"avbFooterDiscardReason\":%s,"
                    + "\"containerVerified\":true,\"outputReadbackVerified\":true,\"ramdiskPreserved\":%s,\"kernelPreserved\":%s,"
                    + "\"sourceUnchanged\":true,\"originalSignaturesReverified\":false,\"oldMetadataRetained\":%s,"
                    + "\"equivalenceVerified\":%s,\"declaredDiffOnly\":%s,\"journal\":%s,\"equivalence\":%s,"
                    + "\"flashableCandidate\":false}", replacementRamdiskSha256 == null ? "BOOT" : "RAMDISK",
                    source.headerVersion, source.toJson(), image.toJson(), jsonHash(replacementKernelSha256),
                    kernelChanged, jsonHash(replacementRamdiskSha256), ramdiskChanged,
                    removedTrailingBytes, declaredSignatures, declaredSignatureSlotEmpty,
                    trailingGkiCertificates, avbFooterRemoved, avbFooterPresent,
                    sourceAvbFooterPresent, jsonHash(avbFooterDiscardReason),
                    !ramdiskChanged, !kernelChanged, avbFooterPresent,
                    equivalence.ok(), equivalence.ok(), journal.toJson(), equivalence.toJson());
        }

        private static String jsonHash(String value) { return value == null ? "null" : "\"" + value + "\""; }
    }

    /** Replaces only the kernel component in an AOSP boot v0-v4 container. */
    public static Report rebuild(File sourceFile, File kernelFile, File outputFile) throws IOException {
        return rebuildWithPolicy(sourceFile, kernelFile, outputFile, OutputPolicy.STRICT);
    }

    /** Rebuilds through the same component writer with an explicit appended-metadata policy. */
    public static Report rebuildWithPolicy(File sourceFile, File kernelFile, File outputFile,
                                           OutputPolicy policy) throws IOException {
        Objects.requireNonNull(policy, "policy");
        if (BootImageInspector.inspect(sourceFile).headerVersion == 4)
            return rebuildV4Component(sourceFile, kernelFile, outputFile, null, false, false, null, policy, MAX_IMAGE);
        return rebuildLegacyComponent(sourceFile, kernelFile, outputFile, false, false, null, policy, MAX_IMAGE);
    }

    /** Replaces the kernel and its opaque inverse record; null explicitly removes the record. */
    public static Report rebuild(File sourceFile, File kernelFile, File outputFile,
                                 byte[] recoveryRecord) throws IOException {
        return rebuildWithPolicy(sourceFile, kernelFile, outputFile, recoveryRecord, OutputPolicy.STRICT);
    }

    /** Null explicitly removes the recovery record; this does not synthesize absent AVB metadata. */
    public static Report rebuildWithPolicy(File sourceFile, File kernelFile, File outputFile,
                                           byte[] recoveryRecord, OutputPolicy policy) throws IOException {
        Objects.requireNonNull(policy, "policy");
        byte[] snapshot = recoveryRecord == null ? null : recoveryRecord.clone();
        if (snapshot != null && (snapshot.length == 0 || snapshot.length > MAX_RECOVERY_BYTES))
            throw error("RECOVERY_RECORD_SIZE_LIMIT");
        if (BootImageInspector.inspect(sourceFile).headerVersion == 4)
            return rebuildV4Component(sourceFile, kernelFile, outputFile, null, false, true, RecoveryPayload.of(snapshot), policy, MAX_IMAGE);
        return rebuildLegacyComponent(sourceFile, kernelFile, outputFile, false, true, RecoveryPayload.of(snapshot), policy, MAX_IMAGE);
    }

    /** Replaces the opaque record from a bounded ordinary file without allocating its full contents. */
    public static Report rebuildWithRecoveryFile(File sourceFile, File kernelFile, File outputFile,
                                                 File recordFile, OutputPolicy policy) throws IOException {
        return rebuildWithRecoveryFile(sourceFile, kernelFile, outputFile, recordFile, policy, MAX_IMAGE);
    }

    /** Uses the actual writer layout to keep an optional complete record within the caller's capacity. */
    public static Report rebuildWithRecoveryFile(File sourceFile, File kernelFile, File outputFile,
                                                 File recordFile, OutputPolicy policy, long maxOutputBytes) throws IOException {
        Objects.requireNonNull(policy, "policy");
        if (maxOutputBytes <= 0 || maxOutputBytes > MAX_IMAGE) throw error("RECOVERY_RECORD_CAPACITY_LIMIT");
        RecoveryPayload record = RecoveryPayload.file(Objects.requireNonNull(recordFile, "recordFile").toPath());
        if (BootImageInspector.inspect(sourceFile).headerVersion == 4)
            return rebuildV4Component(sourceFile, kernelFile, outputFile, null, false, true, record, policy, maxOutputBytes);
        return rebuildLegacyComponent(sourceFile, kernelFile, outputFile, false, true, record, policy, maxOutputBytes);
    }

    /** Reads a bounded opaque record outside all AOSP components; never patches or writes input. */
    public static byte[] readRecoveryRecord(File sourceFile) throws IOException {
        return readRecoveryRecord(sourceFile, OutputPolicy.STRICT);
    }

    /** Uses the writer's explicit policy while keeping the complete input read-only. */
    public static byte[] readRecoveryRecord(File sourceFile, OutputPolicy policy) throws IOException {
        Objects.requireNonNull(policy, "policy");
        Path source = sourceFile.toPath();
        BasicFileAttributes identity = input(source, 1632, MAX_IMAGE, "BOOT_IMAGE_SIZE_LIMIT");
        try (FileChannel original = FileChannel.open(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            BootImageInspector.Report layout = BootImageInspector.inspect(sourceFile);
            if (layout.fileSize != identity.size()) throw error("SOURCE_CHANGED");
            Metadata metadata = recoveryMetadata(original, layout, policy);
            if (metadata.recovery != null && metadata.recovery.length > MAX_RECOVERY_BYTES)
                throw error("RECOVERY_RECORD_SIZE_LIMIT");
            byte[] record = metadata.recovery == null ? null :
                    read(original, metadata.recovery.offset, (int) metadata.recovery.length);
            unchanged(source, identity, original, layout.sha256);
            return record;
        }
    }

    /** Validates the same tail inventory used by the writer, without normalizing any byte. */
    static void validateRecoveryInput(File sourceFile, OutputPolicy policy) throws IOException {
        Objects.requireNonNull(policy, "policy");
        Path source = sourceFile.toPath();
        BasicFileAttributes identity = input(source, 1632, MAX_IMAGE, "BOOT_IMAGE_SIZE_LIMIT");
        try (FileChannel original = FileChannel.open(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            BootImageInspector.Report layout = BootImageInspector.inspect(sourceFile);
            if (layout.fileSize != identity.size()) throw error("SOURCE_CHANGED");
            validateSourceLayout(original, layout);
            recoveryMetadata(original, layout, policy);
            unchanged(source, identity, original, layout.sha256);
        }
    }

    private static void validateSourceLayout(FileChannel original, BootImageInspector.Report layout) throws IOException {
        int version = layout.headerVersion;
        if (layout.headerSize != new int[]{1632, 1648, 1660, 1580, 1584}[version])
            throw error("EXPECTED_AOSP_BOOT_HEADER");
        if (version >= 3) canonicalHeader(read(original, 0, PAGE), version == 4 ? 1584 : 1580);
        long next = layout.pageSize;
        long[][] components = {{layout.kernelOffset, layout.kernelSize}, {layout.ramdiskOffset, layout.ramdiskSize},
                {layout.secondOffset, layout.secondSize}, {layout.recoveryDtboOffset, layout.recoveryDtboSize},
                {layout.dtbOffset, layout.dtbSize}};
        for (long[] component : components) if (component[1] > 0) {
            if (component[0] != next) throw error("NONCANONICAL_COMPONENT_LAYOUT");
            next = align(component[0] + component[1], layout.pageSize);
            if (next > layout.fileSize) throw error("COMPONENT_OUT_OF_BOUNDS");
        }
        if (version == 4) {
            long signatureEnd = layout.signatureOffset + layout.signatureSize;
            if (align(signatureEnd, layout.pageSize) > layout.fileSize) throw error("COMPONENT_OUT_OF_BOUNDS");
            requireZero(original, signatureEnd, align(signatureEnd, layout.pageSize), "NONCANONICAL_BOOT_PADDING");
        }
    }

    private static Metadata recoveryMetadata(FileChannel original, BootImageInspector.Report layout,
                                              OutputPolicy policy) throws IOException {
        long bodyEnd = layout.pageSize;
        long[][] components = {{layout.kernelOffset, layout.kernelSize}, {layout.ramdiskOffset, layout.ramdiskSize},
                {layout.secondOffset, layout.secondSize}, {layout.recoveryDtboOffset, layout.recoveryDtboSize},
                {layout.dtbOffset, layout.dtbSize}, {layout.signatureOffset, layout.signatureSize}};
        for (long[] component : components) if (component[1] > 0)
            bodyEnd = Math.max(bodyEnd, align(component[0] + component[1], layout.pageSize));
        return metadata(original, layout, bodyEnd, policy);
    }

    /** Extracts one validated opaque record; an absent record leaves destination untouched. */
    public static boolean extractRecoveryRecord(File sourceFile, File destinationFile,
                                                 OutputPolicy policy) throws IOException {
        Objects.requireNonNull(policy, "policy");
        Path source = sourceFile.toPath(), output = destinationFile.toPath();
        Path anchor = output.toAbsolutePath().getParent().resolve(".maplesu-rebuild-" + UUID.randomUUID() + ".pending");
        BasicFileAttributes sourceIdentity = input(source, 1632, MAX_IMAGE, "BOOT_IMAGE_SIZE_LIMIT");
        OutputIdentity outputIdentity = null;
        boolean complete = false;
        Throwable failure = null;
        try {
            try (FileChannel original = FileChannel.open(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                BootImageInspector.Report layout = BootImageInspector.inspect(sourceFile);
                if (layout.fileSize != sourceIdentity.size()) throw error("SOURCE_CHANGED");
                Metadata metadata = recoveryMetadata(original, layout, policy);
                if (metadata.recovery == null) {
                    unchanged(source, sourceIdentity, original, layout.sha256);
                    return false;
                }
                try (FileChannel destination = FileChannel.open(output, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    outputIdentity = OutputIdentity.capture(output, anchor);
                    metadata.recovery.copyTo(destination);
                    destination.force(true);
                    if (destination.size() != metadata.recovery.length ||
                            !metadata.recovery.sha256.equals(hash(destination, 0, destination.size())))
                        throw error("REBUILD_READBACK_MISMATCH");
                    if (!outputIdentity.matches(output)) throw error("OUTPUT_IDENTITY_CHANGED");
                    unchanged(source, sourceIdentity, original, layout.sha256);
                    cancelled();
                }
            }
            complete = true;
            return true;
        } catch (IOException | RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            OutputIdentity identity = outputIdentity;
            boolean removeOutput = !complete;
            cleanup(() -> {
                if (removeOutput && identity != null && identity.matches(output)) Files.deleteIfExists(output);
            }, () -> {
                if (identity != null && identity.anchor != null) Files.deleteIfExists(identity.anchor);
            }, failure);
        }
    }

    /** Replaces only the encoded ramdisk component in an AOSP boot v0-v4 container. */
    public static Report rebuildRamdisk(File sourceFile, File ramdiskFile, File outputFile) throws IOException {
        if (BootImageInspector.inspect(sourceFile).headerVersion == 4) {
            return rebuildRamdiskV4(sourceFile, ramdiskFile, outputFile);
        }
        return rebuildLegacyComponent(sourceFile, ramdiskFile, outputFile, true, false, null, OutputPolicy.STRICT, MAX_IMAGE);
    }

    /** Replaces the ramdisk and its opaque file record; null explicitly removes the record. */
    public static Report rebuildRamdiskWithRecoveryFile(File sourceFile, File ramdiskFile, File outputFile,
                                                        File recordFile, OutputPolicy policy) throws IOException {
        return rebuildRamdiskWithRecoveryFile(sourceFile, ramdiskFile, outputFile, recordFile, policy, MAX_IMAGE);
    }

    /** Uses the shared writer's actual layout and capacity checks, including kernel-free init_boot. */
    public static Report rebuildRamdiskWithRecoveryFile(File sourceFile, File ramdiskFile, File outputFile,
                                                        File recordFile, OutputPolicy policy, long maxOutputBytes) throws IOException {
        Objects.requireNonNull(policy, "policy");
        if (maxOutputBytes <= 0 || maxOutputBytes > MAX_IMAGE) throw error("RECOVERY_RECORD_CAPACITY_LIMIT");
        RecoveryPayload record = recordFile == null ? null : RecoveryPayload.file(recordFile.toPath());
        if (BootImageInspector.inspect(sourceFile).headerVersion == 4)
            return rebuildV4Component(sourceFile, ramdiskFile, outputFile, null, true, true, record, policy, maxOutputBytes);
        return rebuildLegacyComponent(sourceFile, ramdiskFile, outputFile, true, true, record, policy, maxOutputBytes);
    }

    private static Report rebuildLegacyComponent(File sourceFile, File componentFile, File outputFile,
                                                 boolean replacingRamdisk, boolean replaceRecovery,
                                                 RecoveryPayload recoveryRecord, OutputPolicy policy, long maxOutputBytes) throws IOException {
        cancelled();
        Path source = sourceFile.toPath(), component = componentFile.toPath(), output = outputFile.toPath();
        Path work = output.toAbsolutePath().getParent().resolve(".maplesu-rebuild-" + UUID.randomUUID() + ".pending");
        BasicFileAttributes sourceIdentity = input(source, 1632, MAX_IMAGE, "BOOT_IMAGE_SIZE_LIMIT");
        BasicFileAttributes componentIdentity = input(component, replacingRamdisk ? 0 : 1, MAX_KERNEL,
                replacingRamdisk ? "REPLACEMENT_RAMDISK_SIZE_LIMIT" : "REPLACEMENT_KERNEL_SIZE_LIMIT");
        boolean created = false, complete = false;
        OutputIdentity outputIdentity = null;
        Throwable failure = null;
        try {
            Report report;
            try (FileChannel original = FileChannel.open(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
                 FileChannel replacement = FileChannel.open(component, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                String sourceHash = hash(original, 0, sourceIdentity.size());
                String componentHash = hash(replacement, 0, componentIdentity.size());
                BootImageInspector.Report before = BootImageInspector.inspect(sourceFile);
                int version = before.headerVersion, page = (int) before.pageSize;
                if (before.fileSize != sourceIdentity.size() || !sourceHash.equals(before.sha256)) throw error("SOURCE_CHANGED");
                if (version > 3 || (!replacingRamdisk && before.kernelSize == 0) ||
                        before.headerSize != new int[]{1632, 1648, 1660, 1580}[version]) throw error("EXPECTED_AOSP_BOOT_HEADER");
                byte[] header = read(original, 0, page);
                if (version == 3) canonicalHeader(header, 1580);
                long[] offsets = {before.kernelOffset, before.ramdiskOffset, before.secondOffset,
                        before.recoveryDtboOffset, before.dtbOffset};
                long[] sizes = {before.kernelSize, before.ramdiskSize, before.secondSize,
                        before.recoveryDtboSize, before.dtbSize};
                int components = version == 3 ? 2 : version + 3;
                int replacementIndex = replacingRamdisk ? 1 : 0;
                int sizeField = replacingRamdisk ? (version < 3 ? 16 : 12) : 8;
                long bodyEnd = page;
                for (int index = 0; index < components; index++) {
                    if (sizes[index] == 0) continue;
                    if (offsets[index] != bodyEnd) throw error("NONCANONICAL_COMPONENT_LAYOUT");
                    long end = offsets[index] + sizes[index];
                    bodyEnd = align(end, page);
                    if (bodyEnd > before.fileSize) throw error("COMPONENT_OUT_OF_BOUNDS");
                }
                Metadata metadata = metadata(original, before, bodyEnd, policy);
                metadata.outputRecovery = replaceRecovery ? recoveryRecord : metadata.recovery;
                ByteBuffer fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                fields.putInt(sizeField, (int) componentIdentity.size());
                long[] outputOffsets = new long[components];
                long outputSize = page;
                for (int index = 0; index < components; index++) {
                    long size = index == replacementIndex ? componentIdentity.size() : sizes[index];
                    outputOffsets[index] = outputSize;
                    outputSize = align(outputSize + size, page);
                }
                long componentEnd = outputSize;
                long payloadSize = metadata.outputRecovery == null ? componentEnd :
                        align(componentEnd + metadata.outputRecovery.regionLength(), before.pageSize);
                if (payloadSize > MAX_IMAGE) throw error("BOOT_IMAGE_SIZE_LIMIT");
                outputSize = rebuiltSize(metadata, payloadSize, sourceIdentity.size());
                if (outputSize > maxOutputBytes) throw error("RECOVERY_RECORD_CAPACITY_LIMIT");
                BootImageEquivalence.Journal journal = new BootImageEquivalence.Journal();
                journal.shape(version, (int) before.headerSize, page);
                journal.outputSize(outputSize);
                journal.add(BootImageEquivalence.HEADER_FIELD, sizeField, 4);
                if (version == 1 || version == 2) journal.add(BootImageEquivalence.HEADER_FIELD, 1636, 8);
                if (version < 3) journal.add(BootImageEquivalence.HEADER_FIELD, 576, 32);
                journal.add(replacingRamdisk ? BootImageEquivalence.COMPONENT : BootImageEquivalence.KERNEL,
                        offsets[replacementIndex], Math.min(sizes[replacementIndex], componentIdentity.size()));
                declareTail(journal, metadata, before, bodyEnd, componentEnd, payloadSize, outputSize,
                        sourceIdentity.size(), sizes[replacementIndex] != componentIdentity.size(), offsets[replacementIndex]);
                if (version == 1 || version == 2) fields.putLong(1636, sizes[3] == 0 ? 0 : outputOffsets[3]);
                if (version < 3) {
                    final MessageDigest id;
                    try { id = MessageDigest.getInstance("SHA-1"); }
                    catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
                    for (int index = 0; index < components; index++) {
                        boolean replaced = index == replacementIndex;
                        updateId(id, replaced ? replacement : original, replaced ? 0 : offsets[index],
                                replaced ? componentIdentity.size() : sizes[index]);
                    }
                    Arrays.fill(header, 576, 608, (byte) 0);
                    System.arraycopy(id.digest(), 0, header, 576, 20);
                }
                boolean changed = sizes[replacementIndex] != componentIdentity.size() ||
                        !componentHash.equals(hash(original, offsets[replacementIndex], sizes[replacementIndex]));
                try (FileChannel destination = FileChannel.open(output, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    created = true;
                    outputIdentity = OutputIdentity.capture(output, work);
                    write(destination, ByteBuffer.wrap(header));
                    for (int index = 0; index < components; index++) {
                        boolean replaced = index == replacementIndex;
                        long size = replaced ? componentIdentity.size() : sizes[index];
                        copy(replaced ? replacement : original, replaced ? 0 : offsets[index], size, destination);
                        componentPadding(original, offsets[index], sizes[index], destination, size, page);
                    }
                    appendAvb(original, destination, metadata, componentEnd, payloadSize, outputSize, journal);
                    destination.force(true);
                    if (destination.size() != outputSize || !Arrays.equals(header, read(destination, 0, page)))
                        throw error("REBUILD_READBACK_MISMATCH");
                    for (int index = 0; index < components; index++) {
                        long size = index == replacementIndex ? componentIdentity.size() : sizes[index];
                        String expectedHash = index == replacementIndex ? componentHash : hash(original, offsets[index], size);
                        if (!expectedHash.equals(hash(destination, outputOffsets[index], size))) throw error("REBUILD_READBACK_MISMATCH");
                        verifyComponentPadding(original, offsets[index], sizes[index], destination,
                                outputOffsets[index], size, page);
                    }
                    verifyAvb(original, destination, metadata, componentEnd, payloadSize, outputSize);
                    BootImageInspector.Report after = BootImageInspector.inspect(outputFile);
                    if (!outputIdentity.matches(output)) throw error("OUTPUT_IDENTITY_CHANGED");
                    if (after.fileSize != outputSize || after.headerVersion != version ||
                            after.kernelSize != (replacingRamdisk ? before.kernelSize : componentIdentity.size()) ||
                            after.ramdiskSize != (replacingRamdisk ? componentIdentity.size() : before.ramdiskSize) ||
                            after.secondSize != before.secondSize || after.recoveryDtboSize != before.recoveryDtboSize ||
                            after.dtbSize != before.dtbSize || !after.sha256.equals(hash(destination, 0, outputSize)))
                        throw error("REBUILD_READBACK_MISMATCH");
                    unchanged(source, sourceIdentity, original, sourceHash);
                    unchanged(component, componentIdentity, replacement, componentHash);
                    if (metadata.outputRecovery != null) metadata.outputRecovery.verify();
                    cancelled();
                    BootImageEquivalence.Verification equivalence =
                            BootImageEquivalence.verify(sourceFile, outputFile, journal);
                    if (!equivalence.ok()) throw error(equivalence.code);
                    report = new Report(before, after, componentHash, changed, metadata, journal, equivalence, replacingRamdisk);
                }
            }
            complete = true;
            return report;
        } catch (IOException | RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            OutputIdentity identity = outputIdentity;
            boolean removeOutput = created && !complete;
            cleanup(() -> {
                if (removeOutput && identity != null && identity.matches(output)) Files.deleteIfExists(output);
            }, () -> {
                if (identity != null && identity.anchor != null) Files.deleteIfExists(identity.anchor);
            }, failure);
        }
    }

    private static void updateId(MessageDigest digest, FileChannel input, long offset, long size) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
        long remaining = size;
        while (remaining > 0) {
            cancelled();
            buffer.clear().limit((int) Math.min(remaining, buffer.capacity()));
            int count = input.read(buffer, offset + size - remaining);
            if (count < 0) throw error("SOURCE_CHANGED");
            digest.update(buffer.array(), 0, count);
            remaining -= count;
        }
        digest.update(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) size).array());
    }

    public static Report rebuildV4(File sourceFile, File kernelFile, File outputFile) throws IOException {
        return rebuildV4(sourceFile, kernelFile, outputFile, null);
    }

    // The caller must own the output directory; identity checks also detect accidental path replacement.
    static Report rebuildV4(File sourceFile, File kernelFile, File outputFile, Checkpoint checkpoint) throws IOException {
        return rebuildV4Component(sourceFile, kernelFile, outputFile, checkpoint, false, false, null, OutputPolicy.STRICT, MAX_IMAGE);
    }

    /** Replaces the encoded ramdisk in boot/init_boot v4, preserving the complete kernel component. */
    public static Report rebuildRamdiskV4(File sourceFile, File ramdiskFile, File outputFile) throws IOException {
        return rebuildRamdiskV4(sourceFile, ramdiskFile, outputFile, null);
    }

    static Report rebuildRamdiskV4(File sourceFile, File ramdiskFile, File outputFile, Checkpoint checkpoint) throws IOException {
        return rebuildV4Component(sourceFile, ramdiskFile, outputFile, checkpoint, true, false, null, OutputPolicy.STRICT, MAX_IMAGE);
    }

    private static Report rebuildV4Component(File sourceFile, File componentFile, File outputFile,
                                            Checkpoint checkpoint, boolean replacingRamdisk,
                                            boolean replaceRecovery, RecoveryPayload recoveryRecord, OutputPolicy policy,
                                            long maxOutputBytes) throws IOException {
        cancelled();
        Path source = sourceFile.toPath(), component = componentFile.toPath(), output = outputFile.toPath();
        Path work = output.toAbsolutePath().getParent().resolve(".maplesu-rebuild-" + UUID.randomUUID() + ".pending");
        BasicFileAttributes sourceIdentity = input(source, PAGE, MAX_IMAGE, "BOOT_IMAGE_SIZE_LIMIT");
        BasicFileAttributes componentIdentity = input(component, replacingRamdisk ? 0 : 1, MAX_KERNEL,
                replacingRamdisk ? "REPLACEMENT_RAMDISK_SIZE_LIMIT" : "REPLACEMENT_KERNEL_SIZE_LIMIT");
        boolean created = false, complete = false;
        OutputIdentity outputIdentity = null;
        Throwable failure = null;
        try {
            Report report;
            try (FileChannel original = FileChannel.open(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
                 FileChannel replacement = FileChannel.open(component, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                String sourceHash = hash(original, 0, sourceIdentity.size());
                String componentHash = hash(replacement, 0, componentIdentity.size());
                BootImageInspector.Report before = BootImageInspector.inspect(sourceFile);
                if (before.fileSize != sourceIdentity.size() || !sourceHash.equals(before.sha256)) throw error("SOURCE_CHANGED");
                if (before.headerVersion != 4 || before.headerSize != 1584 || (!replacingRamdisk && before.kernelSize == 0))
                    throw error("EXPECTED_BOOT_V4");
                byte[] header = read(original, 0, PAGE);
                canonicalHeader(header);
                long bodyEnd = align(before.signatureOffset + before.signatureSize);
                if (bodyEnd > before.fileSize) throw error("COMPONENT_OUT_OF_BOUNDS");
                requireZero(original, before.signatureOffset + before.signatureSize, bodyEnd, "NONCANONICAL_BOOT_PADDING");
                Metadata metadata = metadata(original, before, bodyEnd, policy);
                metadata.outputRecovery = replaceRecovery ? recoveryRecord : metadata.recovery;
                long kernelSize = replacingRamdisk ? before.kernelSize : componentIdentity.size();
                long ramdiskSize = replacingRamdisk ? componentIdentity.size() : before.ramdiskSize;
                String kernelHash = replacingRamdisk ? hash(original, PAGE, before.kernelSize) : componentHash;
                String ramdiskHash = replacingRamdisk ? componentHash : hash(original, before.ramdiskOffset, before.ramdiskSize);
                long oldOffset = replacingRamdisk ? before.ramdiskOffset : PAGE;
                long oldSize = replacingRamdisk ? before.ramdiskSize : before.kernelSize;
                boolean changed = oldSize != componentIdentity.size()
                        || !componentHash.equals(hash(original, oldOffset, oldSize));
                ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(replacingRamdisk ? 12 : 8, (int) componentIdentity.size()).putInt(1580, 0);
                long ramdiskOffset = align(PAGE + kernelSize);
                long outputSize = ramdiskOffset + align(ramdiskSize);
                long componentEnd = outputSize;
                long payloadSize = metadata.outputRecovery == null ? componentEnd :
                        align(componentEnd + metadata.outputRecovery.regionLength(), before.pageSize);
                if (payloadSize > MAX_IMAGE) throw error("BOOT_IMAGE_SIZE_LIMIT");
                outputSize = rebuiltSize(metadata, payloadSize, sourceIdentity.size());
                if (outputSize > maxOutputBytes) throw error("RECOVERY_RECORD_CAPACITY_LIMIT");
                BootImageEquivalence.Journal journal = new BootImageEquivalence.Journal();
                journal.shape(4, (int) before.headerSize, PAGE);
                journal.outputSize(outputSize);
                journal.add(BootImageEquivalence.HEADER_FIELD, replacingRamdisk ? 12 : 8, 4);
                journal.add(BootImageEquivalence.HEADER_FIELD, 1580, 4);
                journal.add(replacingRamdisk ? BootImageEquivalence.COMPONENT : BootImageEquivalence.KERNEL,
                        oldOffset, Math.min(oldSize, componentIdentity.size()));
                declareTail(journal, metadata, before, bodyEnd, componentEnd, payloadSize, outputSize,
                        sourceIdentity.size(), oldSize != componentIdentity.size(), oldOffset);
                try (FileChannel destination = FileChannel.open(output, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    created = true;
                    outputIdentity = OutputIdentity.capture(output, work);
                    write(destination, ByteBuffer.wrap(header));
                    copy(replacingRamdisk ? original : replacement, replacingRamdisk ? PAGE : 0, kernelSize, destination);
                    componentPadding(original, PAGE, before.kernelSize, destination, kernelSize, PAGE);
                    copy(replacingRamdisk ? replacement : original, replacingRamdisk ? 0 : before.ramdiskOffset,
                            ramdiskSize, destination);
                    componentPadding(original, before.ramdiskOffset, before.ramdiskSize,
                            destination, ramdiskSize, PAGE);
                    appendAvb(original, destination, metadata, componentEnd, payloadSize, outputSize, journal);
                    destination.force(true);
                    if (checkpoint != null) checkpoint.afterWrite();
                    if (destination.size() != outputSize
                            || !Arrays.equals(header, read(destination, 0, PAGE))
                            || !kernelHash.equals(hash(destination, PAGE, kernelSize))
                            || !ramdiskHash.equals(hash(destination, ramdiskOffset, ramdiskSize))) {
                        throw error("REBUILD_READBACK_MISMATCH");
                    }
                    verifyComponentPadding(original, PAGE, before.kernelSize, destination, PAGE, kernelSize, PAGE);
                    verifyComponentPadding(original, before.ramdiskOffset, before.ramdiskSize,
                            destination, ramdiskOffset, ramdiskSize, PAGE);
                    verifyAvb(original, destination, metadata, componentEnd, payloadSize, outputSize);
                    BootImageInspector.Report after = BootImageInspector.inspect(outputFile);
                    if (!outputIdentity.matches(output)) throw error("OUTPUT_IDENTITY_CHANGED");
                    if (after.fileSize != outputSize || after.kernelSize != kernelSize
                            || after.ramdiskSize != ramdiskSize || after.signatureSize != 0
                            || !after.sha256.equals(hash(destination, 0, outputSize))) throw error("REBUILD_READBACK_MISMATCH");
                    unchanged(source, sourceIdentity, original, sourceHash);
                    unchanged(component, componentIdentity, replacement, componentHash);
                    if (metadata.outputRecovery != null) metadata.outputRecovery.verify();
                    cancelled();
                    BootImageEquivalence.Verification equivalence =
                            BootImageEquivalence.verify(sourceFile, outputFile, journal);
                    if (!equivalence.ok()) throw error(equivalence.code);
                    report = new Report(before, after, componentHash, changed, metadata, journal, equivalence, replacingRamdisk);
                }
            }
            complete = true;
            return report;
        } catch (IOException | RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            OutputIdentity identity = outputIdentity;
            boolean removeOutput = created && !complete;
            cleanup(() -> {
                if (removeOutput && identity != null && identity.matches(output)) Files.deleteIfExists(output);
            }, () -> {
                if (identity != null && identity.anchor != null) Files.deleteIfExists(identity.anchor);
            }, failure);
        }
    }

    static void cleanup(Checkpoint outputCleanup, Checkpoint anchorCleanup, Throwable primaryFailure) throws IOException {
        Throwable failure = primaryFailure;
        for (Checkpoint action : new Checkpoint[]{outputCleanup, anchorCleanup}) {
            try {
                action.afterWrite();
            } catch (IOException | RuntimeException | Error exception) {
                if (failure == null) failure = exception;
                else if (failure != exception) failure.addSuppressed(exception);
            }
        }
        if (primaryFailure == null) {
            if (failure instanceof IOException) throw (IOException) failure;
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            if (failure instanceof Error) throw (Error) failure;
        }
    }

    static final class OutputIdentity {
        final Object fileKey;
        final Map<String, Object> unix;
        final Path anchor;
        private OutputIdentity(Object fileKey, Map<String, Object> unix, Path anchor) {
            this.fileKey = fileKey; this.unix = unix; this.anchor = anchor;
        }
        static OutputIdentity capture(Path output, Path anchor) throws IOException {
            BasicFileAttributes attributes = Files.readAttributes(output, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) throw error("OUTPUT_IDENTITY_CHANGED");
            if (attributes.fileKey() != null) return new OutputIdentity(attributes.fileKey(), null, null);
            try {
                return new OutputIdentity(null, Files.readAttributes(output, "unix:dev,ino", LinkOption.NOFOLLOW_LINKS), null);
            } catch (UnsupportedOperationException unsupported) {
                Files.createLink(anchor, output);
                return new OutputIdentity(null, null, anchor);
            }
        }
        boolean matches(Path output) throws IOException {
            if (fileKey != null) {
                BasicFileAttributes attributes = Files.readAttributes(output, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                return attributes.isRegularFile() && fileKey.equals(attributes.fileKey());
            }
            if (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)) return false;
            return unix != null ? unix.equals(Files.readAttributes(output, "unix:dev,ino", LinkOption.NOFOLLOW_LINKS))
                    : Files.isRegularFile(anchor, LinkOption.NOFOLLOW_LINKS) && Files.isSameFile(output, anchor);
        }
    }

    private static void canonicalHeader(byte[] header) throws IOException {
        canonicalHeader(header, 1584);
    }

    private static void canonicalHeader(byte[] header, int headerSize) throws IOException {
        for (int i = 24; i < 40; i++) if (header[i] != 0) throw error("NONCANONICAL_BOOT_PADDING");
        for (int i = headerSize; i < PAGE; i++) if (header[i] != 0) throw error("NONCANONICAL_BOOT_PADDING");
        int end = 44;
        while (end < 1580 && header[end] != 0) end++;
        if (end == 1580) throw error("NONCANONICAL_CMDLINE");
        for (int i = end; i < 1580; i++) if (header[i] != 0) throw error("NONCANONICAL_CMDLINE");
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(header, 44, end - 44));
        } catch (CharacterCodingException exception) { throw error("NONCANONICAL_CMDLINE"); }
    }

    private static final class Metadata {
        int declared, gki;
        boolean footer, sourceFooter;
        String discardedFooterReason;
        /** A declared signature slot that is entirely zero, i.e. carries no signature blob. */
        boolean emptySignature;
        long removedBytes, vbmetaOffset, vbmetaSize;
        byte[] footerBytes;
        RecoveryPayload recovery, outputRecovery;
        long recoveryOffset;
        int recoveryLength;
    }

    private static long rebuiltSize(Metadata metadata, long payloadSize, long sourceSize) throws IOException {
        if (!metadata.footer) return payloadSize;
        if (align(payloadSize) + metadata.vbmetaSize > sourceSize - 64) throw error("AVB_PARTITION_SIZE_LIMIT");
        return sourceSize;
    }

    private static byte[] relocatedFooter(Metadata metadata, long payloadSize) {
        byte[] footer = metadata.footerBytes.clone();
        ByteBuffer.wrap(footer).order(ByteOrder.BIG_ENDIAN)
                .putLong(AvbFooter.OFFSET_ORIGINAL_IMAGE_SIZE, payloadSize)
                .putLong(AvbFooter.OFFSET_VBMETA_OFFSET, align(payloadSize));
        return footer;
    }

    /**
     * Declares the tail ranges a rebuild intentionally rewrites: the dropped declared signature, the
     * dropped 16 KiB GKI certificate pair, the relocated vbmeta, the zero fill after it, the two
     * footer fields, and the length delta when the payload no longer matches the source length.
     */
    private static void declareTail(BootImageEquivalence.Journal journal, Metadata metadata,
                                    BootImageInspector.Report before, long bodyEnd, long componentEnd, long payloadSize,
                                    long outputSize, long sourceSize, boolean relocated, long relocationStart) {
        if (metadata.declared > 0) {
            journal.add(BootImageEquivalence.DROPPED_SIGNATURE, before.signatureOffset, before.signatureSize);
        }
        if (metadata.gki > 0) journal.add(BootImageEquivalence.DROPPED_GKI, bodyEnd, GKI_PAIR_BYTES);
        if (metadata.recoveryLength > 0)
            journal.add(BootImageEquivalence.RECOVERY_RECORD, metadata.recoveryOffset, metadata.recoveryLength);
        if (metadata.outputRecovery != null) {
            long length = metadata.outputRecovery.regionLength();
            journal.add(BootImageEquivalence.RECOVERY_RECORD, componentEnd, length);
            journal.add(BootImageEquivalence.TRAILING_ZERO, componentEnd + length,
                    payloadSize - componentEnd - length);
        }
        if (metadata.footer) {
            journal.add(BootImageEquivalence.AVB_VBMETA, align(payloadSize), metadata.vbmetaSize);
            long trailer = align(payloadSize) + metadata.vbmetaSize;
            journal.add(BootImageEquivalence.TRAILING_ZERO, trailer, outputSize - 64 - trailer);
            journal.add(BootImageEquivalence.AVB_FOOTER, outputSize - 64 + 12, 8);
            journal.add(BootImageEquivalence.AVB_FOOTER, outputSize - 64 + 20, 8);
        }
        if (relocated) journal.add(BootImageEquivalence.COMPONENT_RELOCATED, relocationStart, outputSize - relocationStart);
        if (outputSize > sourceSize) {
            journal.add(BootImageEquivalence.COMPONENT_RELOCATED, sourceSize, outputSize - sourceSize);
        }
        if (outputSize < sourceSize) {
            journal.add(BootImageEquivalence.TRAILING_TRUNCATED, outputSize, sourceSize - outputSize);
        }
    }

    /** Preserve chain metadata like magiskboot; its old hash does not authenticate the new payload. */
    private static void appendAvb(FileChannel original, FileChannel output, Metadata metadata,
                                  long componentEnd, long payloadSize, long outputSize, BootImageEquivalence.Journal journal)
            throws IOException {
        if (output.position() != componentEnd) throw error("REBUILD_READBACK_MISMATCH");
        if (metadata.outputRecovery != null) {
            write(output, ByteBuffer.wrap(metadata.outputRecovery.header()));
            metadata.outputRecovery.copyTo(output);
        }
        zeros(output, payloadSize - output.position());
        if (!metadata.footer) return;
        zeros(output, align(payloadSize) - output.position());
        copy(original, metadata.vbmetaOffset, metadata.vbmetaSize, output);
        zeros(output, outputSize - 64 - output.position());
        write(output, ByteBuffer.wrap(relocatedFooter(metadata, payloadSize)));
    }

    private static void verifyAvb(FileChannel original, FileChannel output, Metadata metadata,
                                  long componentEnd, long payloadSize, long outputSize) throws IOException {
        long recoveryLength = metadata.outputRecovery == null ? 0 : metadata.outputRecovery.regionLength();
        if (recoveryLength > 0 &&
                (!Arrays.equals(metadata.outputRecovery.header(), read(output, componentEnd, RECOVERY_HEADER_BYTES)) ||
                 !metadata.outputRecovery.sha256.equals(hash(output, componentEnd + RECOVERY_HEADER_BYTES,
                         metadata.outputRecovery.length)))) throw error("REBUILD_READBACK_MISMATCH");
        requireZero(output, componentEnd + recoveryLength, payloadSize, "REBUILD_READBACK_MISMATCH");
        if (!metadata.footer) return;
        long offset = align(payloadSize);
        if (!hash(original, metadata.vbmetaOffset, metadata.vbmetaSize).equals(hash(output, offset, metadata.vbmetaSize)) ||
                !Arrays.equals(relocatedFooter(metadata, payloadSize), read(output, outputSize - 64, 64)))
            throw error("REBUILD_READBACK_MISMATCH");
        requireZero(output, payloadSize, offset, "REBUILD_READBACK_MISMATCH");
        requireZero(output, offset + metadata.vbmetaSize, outputSize - 64, "REBUILD_READBACK_MISMATCH");
    }

    /** Structural inventory only. Old certificates never authenticate replacement contents. */
    private static Metadata metadata(FileChannel input, BootImageInspector.Report layout, long bodyEnd,
                                     OutputPolicy policy) throws IOException {
        Metadata result = new Metadata();
        result.removedBytes = layout.fileSize - (layout.headerVersion >= 3 ? layout.signatureOffset : bodyEnd);
        long originalEnd = layout.fileSize;
        byte[] footer = read(input, layout.fileSize - 64, 64);
        AvbFooter parsed = AvbFooter.parse(footer);
        result.sourceFooter = parsed.present;
        if (parsed.present) {
            try {
                // Negative here means the u64 high bit is set, i.e. the field cannot be a file offset.
                if (parsed.originalImageSize < 0 || parsed.vbmetaOffset < 0 || parsed.vbmetaSize < 0) {
                    throw error("INVALID_AVB_FOOTER");
                }
                originalEnd = parsed.originalImageSize;
                long offset = parsed.vbmetaOffset, size = parsed.vbmetaSize;
                if (originalEnd < bodyEnd || offset < originalEnd || offset > layout.fileSize - 64
                        || size < AvbFooter.MIN_VBMETA || size > layout.fileSize - 64 - offset) {
                    throw error("INVALID_AVB_FOOTER");
                }
                requireZero(input, originalEnd, offset, "UNRECOGNIZED_BOOT_TRAILER");
                requireZero(input, offset + size, layout.fileSize - 64, "UNRECOGNIZED_BOOT_TRAILER");
                vbmetaRegion(input, offset, size, 1);
                result.footer = true;
                result.footerBytes = footer;
                result.vbmetaOffset = offset;
                result.vbmetaSize = size;
            } catch (ValidationException invalid) {
                String reason = invalid.getMessage();
                if (policy != OutputPolicy.DISCARD_INVALID_AVB_IF_ZERO_TAIL ||
                        !("INVALID_AVB_FOOTER".equals(reason) || "INVALID_AVB_METADATA".equals(reason))) throw invalid;
                long footerOffset = layout.fileSize - 64;
                if (footerOffset < bodyEnd) throw invalid;
                // Independently prove that no metadata or unknown payload would be discarded.
                requireZero(input, bodyEnd, footerOffset, "UNRECOGNIZED_BOOT_TRAILER");
                result.discardedFooterReason = reason;
                originalEnd = footerOffset;
            }
        }
        if (layout.signatureSize != 0) {
            // OnePlus 10 Pro (5.10) declares a 4096-byte signature slot that is entirely zero and
            // keeps the real vbmeta after it. A zero slot carries no signature, so there is nothing
            // to validate; it is dropped like any other declared slot. A slot with content must
            // still be a well-formed AVB0 image, otherwise the image is rejected.
            if (isZero(input, layout.signatureOffset, layout.signatureOffset + layout.signatureSize)) {
                result.emptySignature = true;
            } else {
                vbmetaRegion(input, layout.signatureOffset, layout.signatureSize, 1);
            }
            result.declared = 1;
        }
        long cursor = bodyEnd;
        if (originalEnd - cursor >= GKI_PAIR_BYTES && layout.headerVersion >= 3 &&
                magic(read(input, cursor, 4), "AVB0")) {
            vbmetaRegion(input, cursor, GKI_PAIR_BYTES, 2);
            result.gki = 2;
            cursor += GKI_PAIR_BYTES;
        }
        cursor = readRecoveryRegion(input, cursor, originalEnd, result);
        requireZero(input, cursor, originalEnd, "UNRECOGNIZED_BOOT_TRAILER");
        if (result.footer) result.removedBytes = originalEnd -
                (layout.headerVersion >= 3 ? layout.signatureOffset : bodyEnd);
        return result;
    }

    /** One bounded payload: small legacy bytes, a held source region, or a bound ordinary file. */
    private static final class RecoveryPayload {
        final byte[] bytes;
        final FileChannel channel;
        final long offset, length;
        final String sha256;
        final Path path;
        final BasicFileAttributes identity;

        private RecoveryPayload(byte[] bytes, FileChannel channel, long offset, long length,
                                String sha256, Path path, BasicFileAttributes identity) {
            this.bytes = bytes; this.channel = channel; this.offset = offset; this.length = length;
            this.sha256 = sha256; this.path = path; this.identity = identity;
        }

        static RecoveryPayload of(byte[] bytes) {
            return bytes == null ? null : new RecoveryPayload(bytes, null, 0, bytes.length,
                    hex(digest(bytes)), null, null);
        }

        static RecoveryPayload file(Path path) throws IOException {
            BasicFileAttributes identity = input(path, 1, MAX_RECOVERY_FILE_BYTES, "RECOVERY_RECORD_SIZE_LIMIT");
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                String sha = hash(channel, 0, identity.size());
                unchanged(path, identity, channel, sha);
                return new RecoveryPayload(null, null, 0, identity.size(), sha, path, identity);
            }
        }

        long regionLength() { return RECOVERY_HEADER_BYTES + length; }

        byte[] header() {
            ByteBuffer result = ByteBuffer.allocate(RECOVERY_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            result.put(RECOVERY_MAGIC).putInt(1).putInt(RECOVERY_HEADER_BYTES).putInt((int) length).putInt(0);
            for (int index = 0; index < sha256.length(); index += 2)
                result.put((byte) Integer.parseInt(sha256.substring(index, index + 2), 16));
            return result.array();
        }

        void copyTo(FileChannel destination) throws IOException {
            if (bytes != null) write(destination, ByteBuffer.wrap(bytes));
            else if (channel != null) copy(channel, offset, length, destination);
            else try (FileChannel input = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                unchanged(path, identity, input, sha256);
                copy(input, 0, length, destination);
                unchanged(path, identity, input, sha256);
            }
        }

        void verify() throws IOException {
            if (path != null) try (FileChannel input = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                unchanged(path, identity, input, sha256);
            }
        }
    }

    /** Keeps zero-ending records intact when a signer trims full-partition padding. */
    static long recoveryRecordEnd(FileChannel input, long componentEnd) throws IOException {
        long size = input.size();
        if (componentEnd < 0 || componentEnd > size) throw error("RECOVERY_REGION_TRUNCATED");
        return readRecoveryRegion(input, componentEnd, size, new Metadata());
    }

    private static long readRecoveryRegion(FileChannel input, long offset, long end, Metadata metadata) throws IOException {
        if (end - offset < RECOVERY_MAGIC.length || !Arrays.equals(RECOVERY_MAGIC, read(input, offset, RECOVERY_MAGIC.length)))
            return offset;
        if (end - offset < RECOVERY_HEADER_BYTES) throw error("RECOVERY_REGION_TRUNCATED");
        ByteBuffer header = ByteBuffer.wrap(read(input, offset, RECOVERY_HEADER_BYTES)).order(ByteOrder.LITTLE_ENDIAN);
        int length = header.getInt(16);
        if (header.getInt(8) != 1 || header.getInt(12) != RECOVERY_HEADER_BYTES || header.getInt(20) != 0)
            throw error("RECOVERY_REGION_HEADER");
        if (length < 1 || length > MAX_RECOVERY_FILE_BYTES) throw error("RECOVERY_RECORD_SIZE_LIMIT");
        if (length > end - offset - RECOVERY_HEADER_BYTES) throw error("RECOVERY_REGION_TRUNCATED");
        String expected = hex(Arrays.copyOfRange(header.array(), 24, 56));
        if (!expected.equals(hash(input, offset + RECOVERY_HEADER_BYTES, length)))
            throw error("RECOVERY_REGION_CHECKSUM");
        metadata.recovery = new RecoveryPayload(null, input, offset + RECOVERY_HEADER_BYTES,
                length, expected, null, null);
        metadata.recoveryOffset = offset;
        metadata.recoveryLength = RECOVERY_HEADER_BYTES + length;
        return offset + metadata.recoveryLength;
    }

    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }

    private static void vbmetaRegion(FileChannel input, long offset, long length, int count) throws IOException {
        long position = 0;
        // Format offsets follow AOSP avbtool.AvbVBMetaHeader at the pinned reference revision.
        for (int record = 0; record < count; record++) {
            if (length - position < 256) throw error("INVALID_AVB_METADATA");
            byte[] bytes = read(input, offset + position, 256);
            if (!magic(bytes, "AVB0")) throw error("INVALID_AVB_METADATA");
            ByteBuffer h = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            long auth = unsigned64(h, 12), auxiliary = unsigned64(h, 20);
            if (auth > length - position - 256 || auxiliary > length - position - 256 - auth) throw error("INVALID_AVB_METADATA");
            for (int field : new int[]{32, 48}) region(unsigned64(h, field), unsigned64(h, field + 8), auth);
            for (int field : new int[]{64, 80, 96}) region(unsigned64(h, field), unsigned64(h, field + 8), auxiliary);
            long descriptor = offset + position + 256 + auth + unsigned64(h, 96);
            long remaining = unsigned64(h, 104);
            while (remaining > 0) {
                if (remaining < 16) throw error("INVALID_AVB_METADATA");
                ByteBuffer d = ByteBuffer.wrap(read(input, descriptor, 16)).order(ByteOrder.BIG_ENDIAN);
                long size = unsigned64(d, 8);
                if (size > remaining - 16 || (size & 7) != 0) throw error("INVALID_AVB_METADATA");
                descriptor += size + 16; remaining -= size + 16;
            }
            position += 256 + auth + auxiliary;
        }
        requireZero(input, offset + position, offset + length, "INVALID_AVB_METADATA");
    }

    private static void region(long offset, long size, long length) throws IOException {
        if (offset > length || size > length - offset) throw error("INVALID_AVB_METADATA");
    }

    private static long unsigned64(ByteBuffer bytes, int offset) throws IOException {
        long value = bytes.getLong(offset);
        if (value < 0) throw error("INVALID_AVB_METADATA");
        return value;
    }

    private static boolean magic(byte[] bytes, String value) {
        for (int i = 0; i < value.length(); i++) if (bytes[i] != value.charAt(i)) return false;
        return true;
    }

    static BasicFileAttributes input(Path path, long min, long max, String code) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isRegularFile() || attrs.size() < min || attrs.size() > max) throw error(code);
        return attrs;
    }

    static void unchanged(Path path, BasicFileAttributes before, FileChannel channel, String hash) throws IOException {
        BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!after.isRegularFile() || before.size() != after.size() || before.size() != channel.size()
                || !Objects.equals(before.fileKey(), after.fileKey()) || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !hash.equals(hash(channel, 0, before.size()))) throw error("SOURCE_CHANGED");
    }

    static byte[] read(FileChannel channel, long offset, int size) throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate(size);
        while (bytes.hasRemaining()) {
            cancelled();
            int count = channel.read(bytes, offset + bytes.position());
            if (count < 0) throw error("SOURCE_CHANGED");
        }
        return bytes.array();
    }

    static String hash(FileChannel input, long start, long length) throws IOException {
        final MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        long position = start;
        while (position < start + length) {
            byte[] bytes = read(input, position, (int) Math.min(64 * 1024, start + length - position));
            digest.update(bytes); position += bytes.length;
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value & 255));
        return hex.toString();
    }

    static void copy(FileChannel input, long start, long length, FileChannel output) throws IOException {
        for (long position = 0; position < length;) {
            byte[] bytes = read(input, start + position, (int) Math.min(64 * 1024, length - position));
            write(output, ByteBuffer.wrap(bytes)); position += bytes.length;
        }
    }

    private static void componentPadding(FileChannel original, long oldOffset, long oldSize,
                                         FileChannel output, long size, long page) throws IOException {
        long padding = align(output.position(), page) - output.position();
        if (size == oldSize) copy(original, oldOffset + oldSize, padding, output);
        else zeros(output, padding);
    }

    private static void verifyComponentPadding(FileChannel original, long oldOffset, long oldSize,
                                               FileChannel output, long offset, long size, long page) throws IOException {
        long end = offset + size;
        long padding = align(end, page) - end;
        if (size == oldSize) {
            if (!hash(original, oldOffset + oldSize, padding).equals(hash(output, end, padding)))
                throw error("REBUILD_READBACK_MISMATCH");
        } else requireZero(output, end, end + padding, "REBUILD_READBACK_MISMATCH");
    }

    static void write(FileChannel output, ByteBuffer bytes) throws IOException {
        while (bytes.hasRemaining()) { cancelled(); output.write(bytes); }
    }

    static void zeros(FileChannel output, long length) throws IOException {
        while (length > 0) {
            int size = (int) Math.min(64 * 1024, length);
            write(output, ByteBuffer.allocate(size)); length -= size;
        }
    }

    private static boolean isZero(FileChannel input, long start, long end) throws IOException {
        if (end < start) throw error("COMPONENT_OUT_OF_BOUNDS");
        for (long position = start; position < end;) {
            byte[] bytes = read(input, position, (int) Math.min(64 * 1024, end - position));
            for (byte value : bytes) if (value != 0) return false;
            position += bytes.length;
        }
        return true;
    }

    private static void requireZero(FileChannel input, long start, long end, String code) throws IOException {
        if (!isZero(input, start, end)) throw error(code);
    }

    private static long align(long value) { return align(value, PAGE); }
    private static long align(long value, long page) { return (value + page - 1) / page * page; }
    /** A rejected image format; filesystem I/O failures retain their original exception type. */
    public static final class ValidationException extends IOException {
        private ValidationException(String code) { super(code); }
    }

    private static IOException error(String code) {
        switch (code) {
            case "EXPECTED_AOSP_BOOT_HEADER":
            case "NONCANONICAL_COMPONENT_LAYOUT":
            case "COMPONENT_OUT_OF_BOUNDS":
            case "BOOT_IMAGE_SIZE_LIMIT":
            case "EXPECTED_BOOT_V4":
            case "NONCANONICAL_BOOT_PADDING":
            case "NONCANONICAL_CMDLINE":
            case "AVB_PARTITION_SIZE_LIMIT":
            case "INVALID_AVB_FOOTER":
            case "UNRECOGNIZED_BOOT_TRAILER":
            case "INVALID_AVB_METADATA":
            case "RECOVERY_RECORD_SIZE_LIMIT":
            case "RECOVERY_RECORD_CAPACITY_LIMIT":
            case "RECOVERY_REGION_TRUNCATED":
            case "RECOVERY_REGION_HEADER":
            case "RECOVERY_REGION_CHECKSUM":
                return new ValidationException(code);
            default:
                return new IOException(code);
        }
    }
    static void cancelled() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw error("CANCELLED");
    }
}
