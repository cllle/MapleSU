package dev.maplesu.image;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;

/** File-only module carrier production. Module loading, SDK and OEM boot trust are separate evidence. */
public final class UltraInitBootProducer {
    private static final byte[] MAGIC = {'M', 'S', 'U', 'I', 'B', 'R', '0', '1'};
    private static final int HEADER_BYTES = 64;
    private static final BootImageRebuilder.OutputPolicy POLICY = BootImageRebuilder.OutputPolicy.STRICT;

    private UltraInitBootProducer() {}
    interface Checkpoint {
        void afterPublish(Path work) throws IOException;
        default BootImageRebuilder.OutputIdentity capture(Path candidate, Path anchor) throws IOException {
            return BootImageRebuilder.OutputIdentity.capture(candidate, anchor);
        }
    }

    public static final class Artifact {
        /** Build identity of a reusable component, never facts observed from a target kernel. */
        public final KmiComponent component;
        public final boolean isKmiComponent;
        public final String componentKmi;
        /** Present only for an exact-target artifact. Reusable components carry no target Image. */
        public final File targetBoot;
        /** Schema1 target identity; schema2 kernelRelease is the component build release only. */
        public final String kernelRelease, kernelSha256, credentialSha256;
        public final String targetBootSha256, handoffSha256, bridgeSha256;
        public final String ksuInitSha256;
        public final String credentialMode;
        public final int metadataDisplacement;
        public final boolean supportsDeploymentNamespace;
        public final java.util.Map<String, String> targetInputSha256;
        final byte[] handoff, bridge;
        final int configPageSize;
        private final byte[] credential;
        private final byte[] deploymentNamespace;
        private final byte[] taskProfileMetadata;
        private final String declaredCredentialSha256;

        Artifact(File targetBoot, String targetHash, String release, String kernelHash,
                 String credentialHash, byte[] handoff, byte[] bridge) {
            this(targetBoot, targetHash, release, kernelHash, credentialHash, handoff, bridge, null);
        }

        Artifact(File targetBoot, String targetHash, String release, String kernelHash,
                 String credentialHash, byte[] handoff, byte[] bridge, String ksuInitSha256) {
            this(targetBoot, targetHash, release, kernelHash, credentialHash, handoff, bridge, ksuInitSha256,
                    java.util.Map.of(), 0);
        }

        Artifact(File targetBoot, String targetHash, String release, String kernelHash,
                 String credentialHash, byte[] handoff, byte[] bridge, String ksuInitSha256,
                 java.util.Map<String, String> targetInputs, int configPageSize) {
            this(targetBoot, targetHash, release, kernelHash, credentialHash, handoff, bridge, ksuInitSha256,
                    targetInputs, configPageSize, "embedded", 0, false, null, null, null);
        }

        Artifact(File targetBoot, String targetHash, String release, String kernelHash,
                 String credentialHash, byte[] handoff, byte[] bridge, String ksuInitSha256,
                 java.util.Map<String, String> targetInputs, int configPageSize,
                 String credentialMode, int metadataDisplacement) {
            this(targetBoot, targetHash, release, kernelHash, credentialHash, handoff, bridge, ksuInitSha256,
                    targetInputs, configPageSize, credentialMode, metadataDisplacement, false, null, null, null);
        }

        Artifact(File targetBoot, String targetHash, String release, String kernelHash,
                 String credentialHash, byte[] handoff, byte[] bridge, String ksuInitSha256,
                 java.util.Map<String, String> targetInputs, int configPageSize,
                 String credentialMode, int metadataDisplacement, boolean supportsDeploymentNamespace) {
            this(targetBoot, targetHash, release, kernelHash, credentialHash, handoff, bridge, ksuInitSha256,
                    targetInputs, configPageSize, credentialMode, metadataDisplacement, supportsDeploymentNamespace, null, null, null);
        }

        private Artifact(File targetBoot, String targetHash, String release, String kernelHash,
                         String credentialHash, byte[] handoff, byte[] bridge, String ksuInitSha256,
                         java.util.Map<String, String> targetInputs, int configPageSize,
                         String credentialMode, int metadataDisplacement, boolean supportsDeploymentNamespace,
                         byte[] credential, byte[] deploymentNamespace, byte[] taskProfileMetadata) {
            this(targetBoot, targetHash, release, kernelHash, credentialHash, handoff, bridge, ksuInitSha256,
                    targetInputs, configPageSize, credentialMode, metadataDisplacement, supportsDeploymentNamespace,
                    credential, deploymentNamespace, taskProfileMetadata, null);
        }

        private Artifact(File targetBoot, String targetHash, String release, String kernelHash,
                         String credentialHash, byte[] handoff, byte[] bridge, String ksuInitSha256,
                         java.util.Map<String, String> targetInputs, int configPageSize,
                         String credentialMode, int metadataDisplacement, boolean supportsDeploymentNamespace,
                         byte[] credential, byte[] deploymentNamespace, byte[] taskProfileMetadata,
                         KmiComponent component) {
            this.component = component; isKmiComponent = component != null;
            componentKmi = component == null ? "" : component.kmi;
            this.targetBoot = targetBoot; targetBootSha256 = targetHash;
            kernelRelease = release; kernelSha256 = kernelHash;
            declaredCredentialSha256 = credentialHash;
            this.credential = credential == null ? null : credential.clone();
            credentialSha256 = this.credential == null ? credentialHash : KernelIdentity.sha256(this.credential);
            this.credentialMode = credentialMode; this.metadataDisplacement = metadataDisplacement;
            this.supportsDeploymentNamespace = supportsDeploymentNamespace;
            this.deploymentNamespace = deploymentNamespace == null ? null : deploymentNamespace.clone();
            this.taskProfileMetadata = taskProfileMetadata == null ? null : taskProfileMetadata.clone();
            this.handoff = handoff.clone(); this.bridge = bridge.clone();
            handoffSha256 = KernelIdentity.sha256(handoff); bridgeSha256 = KernelIdentity.sha256(bridge);
            this.ksuInitSha256 = ksuInitSha256;
            targetInputSha256 = java.util.Map.copyOf(targetInputs); this.configPageSize = configPageSize;
        }

        static Artifact component(KmiComponent component, String release, byte[] handoff, byte[] bridge,
                                  String ksuInitSha256, int displacement) {
            return new Artifact(null, "", release, "", null, handoff, bridge, ksuInitSha256,
                    java.util.Map.of(), component.pageSize, "module-parameter", displacement, true,
                    null, null, null, component);
        }

        /** A copy of the source module; a target resolver never mutates the retained template. */
        public byte[] bridgeBytes() { return bridge.clone(); }

        /** Derived bytes are supplied by the Image/BTF resolver; identity checks run again here. */
        public Artifact forTarget(File boot, String bootHash, String release, String kernelHash,
                                  byte[] module, java.util.Map<String, String> inputs,
                                  int pageBytes, int displacement, String vermagic,
                                  boolean modversions) throws IOException {
            if (isKmiComponent || !"module-parameter".equals(credentialMode) || boot == null || module == null ||
                    inputs == null || !bootHash.matches("[0-9a-f]{64}") ||
                    !kernelHash.matches("[0-9a-f]{64}")) throw error("TARGET_BINDING");
            Artifact result = new Artifact(boot, bootHash, release, kernelHash,
                    declaredCredentialSha256, handoff, module, ksuInitSha256, inputs,
                    pageBytes, credentialMode, displacement, supportsDeploymentNamespace,
                    credential, deploymentNamespace, taskProfileMetadata);
            UltraInitBootArtifact.verifyRebound(result, vermagic, modversions);
            return result;
        }

        /** Retain authenticated module facts across target rebinding and namespace/credential copies. */
        public byte[] taskProfileMetadataBytes() {
            return taskProfileMetadata == null ? null : taskProfileMetadata.clone();
        }

        public Artifact withTaskProfileMetadata(byte[] metadata) throws IOException {
            if (isKmiComponent) throw error("RUNTIME_TASK_PROFILE_REQUIRED");
            if (metadata == null || metadata.length == 0 || metadata.length > 1048576)
                throw error("TASK_PROFILE_METADATA_SIZE");
            return new Artifact(targetBoot, targetBootSha256, kernelRelease, kernelSha256,
                    declaredCredentialSha256, handoff, bridge, ksuInitSha256, targetInputSha256,
                    configPageSize, credentialMode, metadataDisplacement, supportsDeploymentNamespace,
                    credential, deploymentNamespace, metadata);
        }

        /** Bind Manager's actual metadata-bound bytes; the package and caller's array stay unchanged. */
        public Artifact withCredential(byte[] actualBound) throws IOException {
            if (!"module-parameter".equals(credentialMode)) throw error("CREDENTIAL_MODE");
            if (actualBound == null || actualBound.length != 48) throw error("CREDENTIAL_INVALID");
            byte[] copy = actualBound.clone();
            try {
                credentialShape(copy);
                if (declaredCredentialSha256 != null && !declaredCredentialSha256.equals(KernelIdentity.sha256(copy)))
                    throw error("CREDENTIAL_HASH");
                return new Artifact(targetBoot, targetBootSha256, kernelRelease, kernelSha256,
                        declaredCredentialSha256, handoff, bridge, ksuInitSha256, targetInputSha256,
                        configPageSize, credentialMode, metadataDisplacement, supportsDeploymentNamespace, copy, deploymentNamespace, taskProfileMetadata, component);
            } finally { Arrays.fill(copy, (byte) 0); }
        }

        /** Public path namespace only; neither a credential nor an installation-generation marker. */
        public Artifact withDeploymentNamespace(byte[] record) throws IOException {
            if (!supportsDeploymentNamespace) throw error("DEPLOYMENT_NAMESPACE_UNSUPPORTED");
            if (record == null || record.length != 24) throw error("DEPLOYMENT_NAMESPACE_INVALID");
            byte[] copy = record.clone();
            deploymentNamespaceShape(copy);
            return new Artifact(targetBoot, targetBootSha256, kernelRelease, kernelSha256,
                    declaredCredentialSha256, handoff, bridge, ksuInitSha256, targetInputSha256,
                    configPageSize, credentialMode, metadataDisplacement, supportsDeploymentNamespace, credential, copy, taskProfileMetadata, component);
        }
    }

    public static final class KmiComponent {
        public final String kmi, architecture, cfiMode, runtimeFactsAbi, sourceGenerationSha256;
        public final int pageSize;

        KmiComponent(String kmi, String architecture, int pageSize, String cfiMode,
                     String runtimeFactsAbi, String sourceGenerationSha256) {
            this.kmi = kmi; this.architecture = architecture; this.pageSize = pageSize;
            this.cfiMode = cfiMode; this.runtimeFactsAbi = runtimeFactsAbi;
            this.sourceGenerationSha256 = sourceGenerationSha256;
        }
    }

    public static final class Report {
        public final String imageSha256, sourceSha256;
        public final int changedBytes;
        public final long recoveryRecordBytes, originalRamdiskBytes, installedRamdiskBytes;
        public final BootImageRebuilder.Report container;
        public final boolean flashableCandidate = false, kernelServiceVerified = false, structuralOnly = true;

        private Report(String sourceHash, BootImageRebuilder.Report container, int changedBytes,
                       long recoveryRecordBytes, long originalRamdiskBytes, long installedRamdiskBytes) {
            sourceSha256 = sourceHash; imageSha256 = container.image.sha256;
            this.container = container; this.changedBytes = changedBytes;
            this.recoveryRecordBytes = recoveryRecordBytes; this.originalRamdiskBytes = originalRamdiskBytes;
            this.installedRamdiskBytes = installedRamdiskBytes;
        }
    }

    public static Artifact readArtifact(File manifest) throws IOException {
        return UltraInitBootArtifact.read(manifest);
    }

    /** KMI components have no target image; exact-target artifacts retain their read-only input. */
    public static Report produce(File source, File output, Artifact artifact) throws IOException {
        return produce(source, output, artifact, null);
    }

    static Report produce(File source, File output, Artifact artifact, Checkpoint checkpoint) throws IOException {
        if (artifact == null) throw error("ARTIFACT_REQUIRED");
        if ("module-parameter".equals(artifact.credentialMode) && artifact.credential == null) throw error("CREDENTIAL_REQUIRED");
        if (artifact.supportsDeploymentNamespace && artifact.deploymentNamespace == null) throw error("DEPLOYMENT_NAMESPACE_REQUIRED");
        BootImageInspector.Report before = requireInitBoot(source);
        UltraInitBootArtifact.verifyArtifact(artifact);
        Path work = Files.createTempDirectory(output.toPath().toAbsolutePath().getParent(), ".maplesu-init-");
        Publication publication = new Publication(output, checkpoint);
        Throwable failure = null;
        try {
            File base = source;
            byte[] deployment = artifact.deploymentNamespace;
            if (readOwned(source, work)) {
                byte[] retained = deploymentNamespace(source);
                if (retained != null) {
                    if (deployment != null && !Arrays.equals(deployment, retained)) throw error("DEPLOYMENT_NAMESPACE_CONFLICT");
                    deployment = retained;
                }
                base = work.resolve("base.img").toFile();
                restoreOwned(source, base, work);
            }
            BootImageInspector.Report layout = requireInitBoot(base);
            byte[] original = component(base, layout);
            RamdiskInspector.Report ramdisk = ramdisk(original);
            byte[] cpio = decode(original, ramdisk.compression);
            byte[] edited = UltraInitRamdisk.install(cpio, artifact.handoff, artifact.bridge, artifact.ksuInitSha256, artifact.credential, deployment);
            byte[] encoded = "RAW".equals(ramdisk.compression) ? edited :
                    KernelCompression.encode(edited, ramdisk.compression);
            RamdiskInspector.Report generated = ramdisk(encoded);
            if (!generated.decompressedSha256.equals(KernelIdentity.sha256(edited))) throw error("CODEC_READBACK");
            File snapshot = work.resolve("capture.bin").toFile();
            BootImageRecovery.capture(base, BootImageRecovery.Component.RAMDISK, snapshot, POLICY);
            File record = work.resolve("new-record.bin").toFile();
            writeRecord(record, snapshot, original, encoded);
            File replacement = work.resolve("ramdisk.bin").toFile();
            Files.write(replacement.toPath(), encoded, StandardOpenOption.CREATE_NEW);
            File candidate = work.resolve("candidate.img").toFile();
            BootImageRebuilder.Report rebuilt = BootImageRebuilder.rebuildRamdiskWithRecoveryFile(
                    base, replacement, candidate, record, POLICY);
            if (rebuilt.image.kernelSize != layout.kernelSize || !rebuilt.equivalence.ok() ||
                    !Arrays.equals(encoded, component(candidate, rebuilt.image))) throw error("READBACK");
            // Exercise the exact recovery bytes before exposing a candidate to the caller.
            Path check = Files.createDirectory(work.resolve("check"));
            if (!readOwned(candidate, check)) throw error("RECOVERY_MISSING");
            File recovered = check.resolve("restored.img").toFile();
            restoreOwned(candidate, recovered, check);
            if (!BootImageInspector.inspect(recovered).sha256.equals(layout.sha256)) throw error("RECOVERY_READBACK");
            unchanged(source, before.sha256);
            UltraInitBootArtifact.verifyArtifact(artifact);
            long changed = BootImageEquivalence.diff(source, candidate).changedBytes;
            publication.publish(candidate, rebuilt.image.sha256);
            if (checkpoint != null) checkpoint.afterPublish(work);
            unchanged(source, before.sha256);
            if (!artifact.isKmiComponent) unchanged(artifact.targetBoot, artifact.targetBootSha256);
            return new Report(before.sha256, rebuilt, Math.toIntExact(changed), record.length(), original.length, encoded.length);
        } catch (IOException | RuntimeException | Error exception) {
            failure = exception; throw exception;
        } finally { finish(work, publication, failure); }
    }

    /** A corrupt owned record is an error, not a negative ownership answer. */
    public static boolean recognizes(File image) throws IOException {
        BootImageInspector.Report layout = BootImageInspector.inspect(image);
        if (layout.headerVersion < 0 || layout.headerVersion > 4 || layout.ramdiskSize == 0) return false;
        Path work = Files.createTempDirectory("maplesu-init-read-");
        Throwable failure = null;
        try { return readOwned(image, work); }
        catch (IOException | RuntimeException | Error exception) { failure = exception; throw exception; }
        finally { cleanup(work, failure); }
    }

    /** Read only a verified owned deployment; an unowned or older image has no namespace. */
    public static byte[] readDeploymentNamespace(File image) throws IOException {
        BootImageInspector.Report layout = BootImageInspector.inspect(image);
        if (layout.headerVersion < 0 || layout.headerVersion > 4 || layout.ramdiskSize == 0) return null;
        Path work = Files.createTempDirectory("maplesu-init-namespace-");
        Throwable failure = null;
        try {
            if (!readOwned(image, work)) return null;
            byte[] record = deploymentNamespace(image);
            unchanged(image, layout.sha256);
            return record;
        } catch (IOException | RuntimeException | Error exception) { failure = exception; throw exception; }
        finally { cleanup(work, failure); }
    }

    private static byte[] deploymentNamespace(File image) throws IOException {
        byte[] encoded = component(image, requireInitBoot(image));
        return UltraInitRamdisk.readDeploymentNamespace(decode(encoded, ramdisk(encoded).compression));
    }

    /** Restores the exact recorded input; that input may itself be a third-party modified image. */
    public static File restore(File image, File output) throws IOException {
        return restore(image, output, null);
    }

    static File restore(File image, File output, Checkpoint checkpoint) throws IOException {
        BootImageInspector.Report before = requireInitBoot(image);
        Path work = Files.createTempDirectory(output.toPath().toAbsolutePath().getParent(), ".maplesu-init-restore-");
        Publication publication = new Publication(output, checkpoint);
        Throwable failure = null;
        try {
            if (!readOwned(image, work)) throw error("RECOVERY_MISSING");
            File restored = work.resolve("restored.img").toFile();
            restoreOwned(image, restored, work);
            String expected = BootImageRecovery.read(work.resolve("snapshot.bin").toFile()).originalImageSha256;
            unchanged(image, before.sha256);
            publication.publish(restored, expected);
            if (checkpoint != null) checkpoint.afterPublish(work);
            unchanged(image, before.sha256);
            return output;
        } catch (IOException | RuntimeException | Error exception) { failure = exception; throw exception; }
        finally { finish(work, publication, failure); }
    }

    private static void restoreOwned(File image, File output, Path work) throws IOException {
        BootImageRecovery.restore(image, work.resolve("original.bin").toFile(),
                work.resolve("snapshot.bin").toFile(), output);
    }

    private static boolean readOwned(File image, Path work) throws IOException {
        BootImageInspector.Report layout = requireInitBoot(image);
        File record = work.resolve("record.bin").toFile();
        if (!BootImageRebuilder.extractRecoveryRecord(image, record, POLICY)) return unowned(image, layout);
        try (FileChannel input = FileChannel.open(record.toPath(), StandardOpenOption.READ)) {
            if (input.size() < MAGIC.length || !Arrays.equals(MAGIC, BootImageRebuilder.read(input, 0, MAGIC.length)))
                return unowned(image, layout);
            if (input.size() < HEADER_BYTES + 32) throw error("RECOVERY_FORMAT");
            ByteBuffer header = ByteBuffer.wrap(BootImageRebuilder.read(input, 0, HEADER_BYTES)).order(ByteOrder.LITTLE_ENDIAN);
            header.position(8);
            if (header.getInt() != 1 || header.getInt() != HEADER_BYTES) throw error("RECOVERY_VERSION");
            long snapshotBytes = header.getLong(), originalBytes = header.getLong();
            if (snapshotBytes <= 0 || snapshotBytes > BootImageRecovery.MAX_SNAPSHOT_BYTES || originalBytes <= 0 ||
                    originalBytes > RamdiskInspector.MAX_COMPONENT_BYTES ||
                    snapshotBytes + originalBytes != input.size() - HEADER_BYTES - 32) throw error("RECOVERY_SIZE");
            byte[] installedHash = new byte[32]; header.get(installedHash);
            if (!hex(installedHash).equals(KernelIdentity.sha256(component(image, layout))) ||
                    !hex(BootImageRebuilder.read(input, input.size() - 32, 32)).equals(
                            BootImageRebuilder.hash(input, 0, input.size() - 32))) throw error("RECOVERY_CHECKSUM");
            copyRange(input, HEADER_BYTES, snapshotBytes, work.resolve("snapshot.bin"));
            copyRange(input, HEADER_BYTES + snapshotBytes, originalBytes, work.resolve("original.bin"));
            BootImageRecovery.Snapshot snapshot = BootImageRecovery.read(work.resolve("snapshot.bin").toFile());
            if (snapshot.target != BootImageRecovery.Component.RAMDISK || snapshot.targetComponentBytes != originalBytes ||
                    !snapshot.targetComponentSha256.equals(hash(work.resolve("original.bin")))) throw error("RECOVERY_TARGET");
            unchanged(image, layout.sha256);
            return true;
        }
    }

    private static boolean unowned(File image, BootImageInspector.Report layout) throws IOException {
        byte[] encoded = component(image, layout);
        RamdiskInspector.Report report = ramdisk(encoded);
        if (UltraInitRamdisk.hasOwnedPath(decode(encoded, report.compression))) throw error("RECOVERY_MISSING");
        unchanged(image, layout.sha256);
        return false;
    }

    private static void writeRecord(File file, File snapshot, byte[] original, byte[] installed) throws IOException {
        long snapshotSize = Files.size(snapshot.toPath());
        if (HEADER_BYTES + snapshotSize + original.length + 32 > BootImageRebuilder.MAX_RECOVERY_FILE_BYTES)
            throw error("RECOVERY_SIZE");
        try (FileChannel out = FileChannel.open(file.toPath(), StandardOpenOption.CREATE_NEW,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileChannel saved = FileChannel.open(snapshot.toPath(), StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            header.put(MAGIC).putInt(1).putInt(HEADER_BYTES).putLong(snapshotSize).putLong(original.length)
                    .put(unhex(KernelIdentity.sha256(installed)));
            header.flip(); BootImageRebuilder.write(out, header);
            BootImageRebuilder.copy(saved, 0, snapshotSize, out);
            BootImageRebuilder.write(out, ByteBuffer.wrap(original));
            byte[] digest = unhex(BootImageRebuilder.hash(out, 0, out.position()));
            BootImageRebuilder.write(out, ByteBuffer.wrap(digest)); out.force(true);
        }
    }

    static byte[] component(File image, BootImageInspector.Report report) throws IOException {
        if (report.ramdiskSize <= 0 || report.ramdiskSize > RamdiskInspector.MAX_COMPONENT_BYTES) throw error("RAMDISK_SIZE");
        return range(image, report.ramdiskOffset, (int) report.ramdiskSize);
    }

    static byte[] range(File file, long offset, int size) throws IOException {
        try (FileChannel input = FileChannel.open(file.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            if (offset < 0 || size < 0 || size > input.size() - offset) throw error("COMPONENT_RANGE");
            return BootImageRebuilder.read(input, offset, size);
        }
    }

    private static byte[] decode(byte[] encoded, String format) throws IOException {
        return "RAW".equals(format) ? encoded.clone() :
                KernelCompression.decode(encoded, format, (int) RamdiskInspector.DEFAULT_MAX_BYTES);
    }

    private static RamdiskInspector.Report ramdisk(byte[] encoded) throws IOException {
        RamdiskInspector.Report result = RamdiskInspector.inspectRamdisk(encoded);
        if (result.malformed) throw error("RAMDISK:" + result.errorCode);
        return result;
    }

    private static BootImageInspector.Report requireInitBoot(File image) throws IOException {
        BootImageRebuilder.input(image.toPath(), 4096, RamdiskInspector.MAX_IMAGE_BYTES, "ULTRA_INIT_IMAGE_SIZE");
        BootImageInspector.Report report = BootImageInspector.inspect(image);
        if (report.headerVersion < 0 || report.headerVersion > 4 || report.ramdiskSize <= 0 ||
                (report.kernelSize == 0 && report.headerVersion != 4)) throw error("CONTAINER");
        return report;
    }

    private static void copyRange(FileChannel input, long start, long length, Path output) throws IOException {
        try (FileChannel out = FileChannel.open(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            BootImageRebuilder.copy(input, start, length, out); out.force(true);
        }
    }

    private static final class Publication {
        final Path path;
        final Checkpoint checkpoint;
        BootImageRebuilder.OutputIdentity identity = null;
        String expectedHash;
        Publication(File output, Checkpoint checkpoint) { path = output.toPath(); this.checkpoint = checkpoint; }

        void publish(File candidate, String expected) throws IOException {
            expectedHash = expected;
            Path staged = candidate.toPath();
            Path anchor = path.resolveSibling(".maplesu-init-" + UUID.randomUUID() + ".anchor");
            try (FileChannel input = FileChannel.open(staged, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                // Capture before exposing a path: a capture/close failure leaves no unowned output.
                identity = checkpoint == null ? BootImageRebuilder.OutputIdentity.capture(staged, anchor) :
                        checkpoint.capture(staged, anchor);
                if (!identity.matches(staged) || !expected.equals(BootImageRebuilder.hash(input, 0, input.size()))) throw error("READBACK");
                BootImageRebuilder.cancelled();
            }
            // Staging is in the output parent filesystem. No REPLACE_EXISTING or ATOMIC_MOVE:
            // the latter may replace an existing destination despite the absent replace option.
            Files.move(staged, path);
            verify();
        }

        void releaseAnchor() throws IOException {
            if (identity != null && identity.anchor != null) Files.deleteIfExists(identity.anchor);
        }

        void verify() throws IOException {
            if (identity == null || !identity.matches(path) || !expectedHash.equals(hash(path))) throw error("READBACK");
        }

        void abort(Throwable primary) throws IOException {
            BootImageRebuilder.cleanup(() -> {
                if (identity != null && Files.exists(path, LinkOption.NOFOLLOW_LINKS) && identity.matches(path)) Files.delete(path);
            }, this::releaseAnchor, primary);
        }
    }

    private static void finish(Path work, Publication publication, Throwable primary) throws IOException {
        Throwable problem = primary;
        try { cleanup(work, problem); }
        catch (IOException | RuntimeException | Error exception) { problem = exception; }
        if (problem == null) {
            try { BootImageRebuilder.cancelled(); publication.verify(); publication.releaseAnchor(); }
            catch (IOException | RuntimeException | Error exception) { problem = exception; }
        }
        if (problem != null) {
            publication.abort(problem);
            if (primary == null) {
                if (problem instanceof IOException) throw (IOException) problem;
                if (problem instanceof RuntimeException) throw (RuntimeException) problem;
                throw (Error) problem;
            }
        }
    }

    private static void cleanup(Path work, Throwable primary) throws IOException {
        BootImageRebuilder.cleanup(() -> {
            try (var files = Files.walk(work)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }, () -> {}, primary);
    }

    static String hash(Path file) throws IOException {
        try (FileChannel input = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            return BootImageRebuilder.hash(input, 0, input.size());
        }
    }

    private static void unchanged(File file, String expected) throws IOException {
        if (!expected.equals(hash(file.toPath()))) throw error("SOURCE_CHANGED");
    }

    static void credentialShape(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length != 48) throw error("CREDENTIAL_INVALID");
        for (byte value : bytes) if (!(value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z' ||
                value >= '0' && value <= '9')) throw error("CREDENTIAL_INVALID");
    }

    static void deploymentNamespaceShape(byte[] bytes) throws IOException {
        // Canonical manager_deployment_namespace.h: LE magic, schema1, sizeof(record), name16.
        if (bytes == null || bytes.length != 24) throw error("DEPLOYMENT_NAMESPACE_INVALID");
        ByteBuffer record = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (record.getInt(0) != 0x4e44534d || record.getShort(4) != 1 || record.getShort(6) != 24)
            throw error("DEPLOYMENT_NAMESPACE_INVALID");
        for (int index = 8; index < 24; index++) {
            byte value = bytes[index];
            if (!(value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z' || value >= '0' && value <= '9'))
                throw error("DEPLOYMENT_NAMESPACE_INVALID");
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
        return out.toString();
    }
    private static byte[] unhex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int index = 0; index < out.length; index++) out[index] = (byte) Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
        return out;
    }
    private static IOException error(String code) { return new IOException("ULTRA_INIT_BOOT_" + code); }
}
