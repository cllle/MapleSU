package dev.maplesu.image;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Bounded flat packaging manifest; hashes are byte bindings, never module-load qualification. */
final class UltraInitBootArtifact {
    private static final Set<String> FIELDS = Set.of("schemaVersion", "targetBoot", "targetBootSha256",
            "kernelRelease", "kernelSha256", "credentialSha256", "handoff", "handoffSha256",
            "bridge", "bridgeSha256", "targetContract", "targetContractSha256", "ksuInit", "ksuInitSha256",
            "config", "configSha256", "btf", "btfSha256", "moduleSymvers", "moduleSymversSha256",
            "credentialMode", "metadataDisplacement", "targetVermagic",
            "sessionAbi", "sessionAbiSha256", "bundleSources", "bundleSourcesSha256",
            "taskProfileAbi", "taskProfileAbiSha256", "abiEvidence", "abiEvidenceSha256",
            "kind", "kmi", "architecture", "pageSize", "cfiMode", "runtimeFactsAbi", "sourceGenerationSha256");
    private static final Set<String> REQUIRED = Set.of("schemaVersion", "targetBoot", "targetBootSha256",
            "kernelRelease", "kernelSha256", "handoff", "handoffSha256", "bridge", "bridgeSha256");
    private static final Set<String> COMPONENT_REQUIRED = Set.of("schemaVersion", "kind", "kmi", "architecture",
            "pageSize", "cfiMode", "kernelRelease", "credentialMode", "metadataDisplacement", "runtimeFactsAbi",
            "sourceGenerationSha256", "handoff", "handoffSha256", "bridge", "bridgeSha256");
    private static final Set<String> COMPONENT_OPTIONAL = Set.of("ksuInit", "ksuInitSha256", "bundleSources", "bundleSourcesSha256");

    private UltraInitBootArtifact() {}

    static UltraInitBootProducer.Artifact read(File manifest) throws IOException {
        byte[] bytes = file(manifest.toPath(), 16 * 1024);
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        Map<String, String> fields = new Json(text).read();
        if ("2".equals(fields.get("schemaVersion"))) return readComponent(manifest, bytes, fields);
        if (!fields.keySet().containsAll(REQUIRED) || !"1".equals(fields.get("schemaVersion")) ||
                fields.keySet().stream().anyMatch(name -> COMPONENT_REQUIRED.contains(name) && !REQUIRED.contains(name) &&
                        !name.equals("credentialMode") && !name.equals("metadataDisplacement")) ||
                fields.containsKey("targetContract") != fields.containsKey("targetContractSha256")) throw error("FIELDS");
        if (fields.containsKey("ksuInit") != fields.containsKey("ksuInitSha256")) throw error("FIELDS");
        String credentialMode = fields.getOrDefault("credentialMode", "embedded");
        if (!credentialMode.equals("embedded") && !credentialMode.equals("module-parameter")) throw error("CREDENTIAL_MODE");
        if (credentialMode.equals("embedded") && !fields.containsKey("credentialSha256")) throw error("FIELDS");
        // The bridge packager derives this from its ELF/page layout; Manager owns credential encoding.
        int metadataDisplacement = fields.containsKey("metadataDisplacement") ?
                Integer.parseInt(fields.get("metadataDisplacement")) : 0;
        if (credentialMode.equals("module-parameter") && metadataDisplacement == 0) throw error("METADATA_DISPLACEMENT");
        Path directory = manifest.toPath().toAbsolutePath().getParent();
        for (String name : new String[]{"targetBoot", "kernel", "handoff", "bridge"})
            if (!fields.get(name + "Sha256").matches("[0-9a-f]{64}")) throw error("HASH:" + name);
        if (fields.containsKey("credentialSha256") && !fields.get("credentialSha256").matches("[0-9a-f]{64}")) throw error("HASH:credential");
        File boot = directory.resolve(fields.get("targetBoot")).toFile();
        byte[] handoff = bound(directory, fields, "handoff", 16 * 1024 * 1024);
        byte[] bridge = bound(directory, fields, "bridge", 64 * 1024 * 1024);
        Map<String, String> targetInputs = new LinkedHashMap<>();
        byte[] config = null;
        for (String name : new String[]{"config", "btf", "moduleSymvers"}) {
            if (fields.containsKey(name) != fields.containsKey(name + "Sha256")) throw error("FIELDS");
            if (!fields.containsKey(name)) continue;
            if (!fields.get(name + "Sha256").matches("[0-9a-f]{64}")) throw error("HASH:" + name);
            byte[] input = bound(directory, fields, name, name.equals("config") ? 1024 * 1024 : 64 * 1024 * 1024);
            if (name.equals("config")) config = input;
            targetInputs.put(name, fields.get(name + "Sha256"));
        }
        // Source/config/BTF/symvers/CFI/factory receipt belongs to the bridge build owner.
        // Binding its bytes does not assert that a module is loadable or its service works.
        if (fields.containsKey("targetContract")) {
            if (!fields.get("targetContractSha256").matches("[0-9a-f]{64}")) throw error("HASH:targetContract");
            bound(directory, fields, "targetContract", 1024 * 1024);
            targetInputs.put("targetContract", fields.get("targetContractSha256"));
        }
        for (String name : new String[]{"sessionAbi", "bundleSources", "taskProfileAbi", "abiEvidence"}) {
            if (fields.containsKey(name) != fields.containsKey(name + "Sha256")) throw error("FIELDS");
            if (!fields.containsKey(name)) continue;
            if (!fields.get(name + "Sha256").matches("[0-9a-f]{64}")) throw error("HASH:" + name);
            bound(directory, fields, name, 1024 * 1024);
            targetInputs.put(name, fields.get(name + "Sha256"));
        }
        String configuration = config == null ? "" : new String(config, StandardCharsets.US_ASCII);
        int configPage = 0;
        if (config != null) {
            if (!configured(configuration, "CONFIG_MODULES") || !configured(configuration, "CONFIG_ARM64")) throw error("TARGET_MODULE_CONFIG");
            for (int page : new int[]{4, 16, 64}) if (configured(configuration, "CONFIG_ARM64_" + page + "K_PAGES")) {
                if (configPage != 0) throw error("TARGET_PAGE_CONFIG"); configPage = page * 1024;
            }
            if (configured(configuration, "CONFIG_MODVERSIONS") && !targetInputs.containsKey("moduleSymvers"))
                throw error("TARGET_MODULE_SYMVERS_REQUIRED");
            if (configured(configuration, "CONFIG_MODULE_SIG_FORCE") &&
                    !new String(bridge, Math.max(0, bridge.length - 28), Math.min(28, bridge.length),
                            StandardCharsets.US_ASCII).endsWith("~Module signature appended~\n")) throw error("TARGET_MODULE_SIGNATURE_REQUIRED");
        }
        elfHeader(handoff, 2, "HANDOFF");
        elfHeader(bridge, 1, "BRIDGE");
        String ksuInitSha256 = null;
        if (fields.containsKey("ksuInit")) {
            if (!fields.get("ksuInitSha256").matches("[0-9a-f]{64}")) throw error("HASH:ksuInit");
            byte[] ksuInit = bound(directory, fields, "ksuInit", 16 * 1024 * 1024);
            elfHeader(ksuInit, 0, "KSU_INIT");
            ksuInitSha256 = fields.get("ksuInitSha256");
        }
        UltraInitBootProducer.Artifact artifact = new UltraInitBootProducer.Artifact(boot,
                fields.get("targetBootSha256"), fields.get("kernelRelease"), fields.get("kernelSha256"),
                fields.get("credentialSha256"), handoff, bridge, ksuInitSha256, targetInputs, configPage,
                credentialMode, metadataDisplacement);
        String targetVermagic = fields.get("targetVermagic");
        int page = verifyTarget(artifact, targetVermagic);
        boolean deployment = module(bridge, artifact.kernelRelease, configured(configuration, "CONFIG_MODVERSIONS"),
                credentialMode.equals("module-parameter") ? page : 0, metadataDisplacement, targetVermagic);
        if (deployment) artifact = new UltraInitBootProducer.Artifact(boot,
                fields.get("targetBootSha256"), fields.get("kernelRelease"), fields.get("kernelSha256"),
                fields.get("credentialSha256"), handoff, bridge, ksuInitSha256, targetInputs, configPage,
                credentialMode, metadataDisplacement, true);
        if (!java.util.Arrays.equals(bytes, file(manifest.toPath(), 16 * 1024))) throw error("MANIFEST_CHANGED");
        return artifact;
    }

    private static UltraInitBootProducer.Artifact readComponent(File manifest, byte[] bytes,
                                                               Map<String, String> fields) throws IOException {
        if (!fields.keySet().containsAll(COMPONENT_REQUIRED) || fields.keySet().stream().anyMatch(name ->
                !COMPONENT_REQUIRED.contains(name) && !COMPONENT_OPTIONAL.contains(name))) throw error("COMPONENT_FIELDS");
        if (!"kmi-component".equals(fields.get("kind"))) throw error("COMPONENT_KIND");
        if (!"arm64".equals(fields.get("architecture"))) throw error("ARCHITECTURE");
        if (!"module-parameter".equals(fields.get("credentialMode"))) throw error("CREDENTIAL_MODE");
        if (!"maplesu-runtime-facts/1".equals(fields.get("runtimeFactsAbi"))) throw error("RUNTIME_FACTS_ABI");
        String kmi = fields.get("kmi"), release = fields.get("kernelRelease");
        if (!kmi.matches("android[0-9]+-[0-9]+\\.[0-9]+") ||
                release.length() > 256 || !release.matches("[!-~]+")) throw error("KMI");
        if (!Set.of("none", "kcfi", "legacy-lto-cfi").contains(fields.get("cfiMode"))) throw error("CFI_MODE");
        String pageText = fields.get("pageSize");
        if (!Set.of("4096", "16384", "65536").contains(pageText)) throw error("PAGE_SIZE");
        int page = Integer.parseInt(pageText), displacement = Integer.parseInt(fields.get("metadataDisplacement"));
        if (!fields.get("sourceGenerationSha256").matches("[0-9a-f]{64}")) throw error("HASH:sourceGeneration");
        Path directory = manifest.toPath().toAbsolutePath().getParent();
        byte[] handoff = bound(directory, fields, "handoff", 16 * 1024 * 1024);
        byte[] bridge = bound(directory, fields, "bridge", 64 * 1024 * 1024);
        String ksuInitSha256 = null;
        for (String name : new String[]{"ksuInit", "bundleSources"}) {
            if (fields.containsKey(name) != fields.containsKey(name + "Sha256")) throw error("COMPONENT_FIELDS");
            if (!fields.containsKey(name)) continue;
            byte[] input = bound(directory, fields, name, name.equals("ksuInit") ? 16 * 1024 * 1024 : 1024 * 1024);
            if (name.equals("ksuInit")) { elfHeader(input, 0, "KSU_INIT"); ksuInitSha256 = fields.get("ksuInitSha256"); }
        }
        var component = new UltraInitBootProducer.KmiComponent(kmi, fields.get("architecture"), page,
                fields.get("cfiMode"), fields.get("runtimeFactsAbi"), fields.get("sourceGenerationSha256"));
        var artifact = UltraInitBootProducer.Artifact.component(component, release, handoff, bridge, ksuInitSha256, displacement);
        verifyArtifact(artifact);
        if (!java.util.Arrays.equals(bytes, file(manifest.toPath(), 16 * 1024))) throw error("MANIFEST_CHANGED");
        return artifact;
    }

    static void verifyArtifact(UltraInitBootProducer.Artifact artifact) throws IOException {
        if (!artifact.isKmiComponent) { verifyTarget(artifact); return; }
        if (artifact.targetBoot != null || !artifact.targetBootSha256.isEmpty() || !artifact.kernelSha256.isEmpty() ||
                !artifact.targetInputSha256.isEmpty() || artifact.taskProfileMetadataBytes() != null) throw error("COMPONENT_TARGET_FACTS");
        elfHeader(artifact.handoff, 2, "HANDOFF");
        elfHeader(artifact.bridge, 1, "BRIDGE");
        staticHandoff(artifact.handoff, artifact.component.pageSize);
        if (!module(artifact.bridge, artifact.kernelRelease, false, artifact.component.pageSize,
                artifact.metadataDisplacement, null, true)) throw error("BRIDGE_DEPLOYMENT_NAMESPACE_PARAMETER_TYPE");
    }

    static void verifyRebound(UltraInitBootProducer.Artifact artifact, String vermagic,
                              boolean modversions) throws IOException {
        int page = verifyTarget(artifact, vermagic);
        elfHeader(artifact.bridge, 1, "BRIDGE");
        boolean deployment = module(artifact.bridge, artifact.kernelRelease, modversions,
                page, artifact.metadataDisplacement, vermagic);
        if (deployment != artifact.supportsDeploymentNamespace) throw error("DEPLOYMENT_PARAMETER_CHANGED");
    }

    static int verifyTarget(UltraInitBootProducer.Artifact artifact) throws IOException {
        return verifyTarget(artifact, null);
    }

    private static int verifyTarget(UltraInitBootProducer.Artifact artifact, String targetVermagic) throws IOException {
        Path path = artifact.targetBoot.toPath();
        BootImageRebuilder.input(path, 1632, RamdiskInspector.MAX_IMAGE_BYTES, "ULTRA_INIT_TARGET_SIZE");
        BootImageInspector.Report report = BootImageInspector.inspect(artifact.targetBoot);
        if (!report.sha256.equals(artifact.targetBootSha256) || report.kernelSize <= 0 ||
                report.kernelSize > KernelCompression.MAX_KERNEL_BYTES) throw error("TARGET_BOOT");
        byte[] encoded = UltraInitBootProducer.range(artifact.targetBoot, report.kernelOffset, (int) report.kernelSize);
        byte[] raw = KernelCompression.decode(encoded, report.compression, KernelCompression.MAX_KERNEL_BYTES);
        if (!KernelIdentity.ARCH_ARM64.equals(KernelIdentity.archOf(raw)) ||
                !KernelIdentity.sha256(raw).equals(artifact.kernelSha256) ||
                !KernelIdentity.releaseOf(raw).equals(artifact.kernelRelease) ||
                artifact.kernelRelease.isEmpty() || KernelIdentity.AMBIGUOUS.equals(artifact.kernelRelease)) throw error("TARGET_KERNEL");
        if (targetVermagic != null) {
            if (!targetVermagic.startsWith(artifact.kernelRelease + " ") || targetVermagic.length() > 512 ||
                    !targetVermagic.matches("[ -~]+")) throw error("TARGET_VERMAGIC");
            byte[] expected = (targetVermagic + "\0").getBytes(StandardCharsets.US_ASCII);
            boolean found = false;
            for (int at = 0; at <= raw.length - expected.length; at++) {
                if (raw[at] == expected[0] && java.util.Arrays.equals(raw, at, at + expected.length, expected, 0, expected.length)) {
                    found = true; break;
                }
            }
            if (!found) throw error("TARGET_VERMAGIC");
        }
        // These bits belong to this Image, independent of the boot header's 4096-byte pages.
        int pageCode = (int) ((ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).getLong(24) >>> 1) & 3);
        int page = pageCode == 0 ? artifact.configPageSize : pageCode == 1 ? 4096 : pageCode == 2 ? 16384 : 65536;
        if (page == 0) throw error("TARGET_PAGE_SIZE_SOURCE_REQUIRED");
        if (artifact.configPageSize != 0 && page != artifact.configPageSize) throw error("TARGET_PAGE_SIZE");
        staticHandoff(artifact.handoff, page);
        if (!UltraInitBootProducer.hash(path).equals(artifact.targetBootSha256)) throw error("TARGET_CHANGED");
        return page;
    }

    private static void elfHeader(byte[] bytes, int type, String name) throws IOException {
        if (bytes.length < 64 || bytes[0] != 127 || bytes[1] != 'E' || bytes[2] != 'L' || bytes[3] != 'F' ||
                bytes[4] != 2 || bytes[5] != 1 || bytes[6] != 1) throw error(name + "_ELF");
        ByteBuffer elf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int actualType = elf.getShort(16) & 65535;
        if ((type == 0 ? actualType != 2 && actualType != 3 : actualType != type) ||
                (elf.getShort(18) & 65535) != 183 || elf.getInt(20) != 1 ||
                (elf.getShort(52) & 65535) != 64) throw error(name + "_ELF");
    }

    private static void staticHandoff(byte[] bytes, int page) throws IOException {
        ByteBuffer elf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        long table = elf.getLong(32), entry = elf.getLong(24);
        int size = elf.getShort(54) & 65535, count = elf.getShort(56) & 65535;
        if (table < 64 || size != 56 || count == 0 || table > bytes.length ||
                (long) count * size > bytes.length - table) throw error("HANDOFF_SEGMENTS");
        boolean executableEntry = false;
        for (int index = 0; index < count; index++) {
            int at = (int) table + index * size, type = elf.getInt(at), flags = elf.getInt(at + 4);
            if (type == 2 || type == 3) throw error("HANDOFF_NOT_STATIC");
            if (type != 1) continue;
            long offset = elf.getLong(at + 8), address = elf.getLong(at + 16), fileSize = elf.getLong(at + 32),
                    memorySize = elf.getLong(at + 40), alignment = elf.getLong(at + 48);
            if (offset < 0 || address < 0 || fileSize < 0 || memorySize < fileSize || memorySize > Long.MAX_VALUE - address || offset > bytes.length ||
                    fileSize > bytes.length - offset || alignment < page || (alignment & (alignment - 1)) != 0 ||
                    (offset & (page - 1)) != (address & (page - 1)) || (flags & 3) == 3) throw error("HANDOFF_SEGMENTS");
            if ((flags & 1) != 0 && entry >= address && entry - address < fileSize) executableEntry = true;
        }
        if (!executableEntry) throw error("HANDOFF_ENTRY");
    }

    private static boolean module(byte[] bytes, String release, boolean modversions, int page, int displacement,
                                  String targetVermagic) throws IOException {
        return module(bytes, release, modversions, page, displacement, targetVermagic, false);
    }

    private static boolean module(byte[] bytes, String release, boolean modversions, int page, int displacement,
                                  String targetVermagic, boolean runtimeFacts) throws IOException {
        ByteBuffer elf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        long table = elf.getLong(40);
        int size = elf.getShort(58) & 65535, count = elf.getShort(60) & 65535, namesIndex = elf.getShort(62) & 65535;
        if (table < 64 || size != 64 || count == 0 || namesIndex >= count || table > bytes.length ||
                (long) size * count > bytes.length - table) throw error("BRIDGE_SECTIONS");
        int names = (int) table + namesIndex * size;
        if (page != 0 && elf.getInt(names + 4) != 3) throw error("BRIDGE_SECTIONS");
        long strings = elf.getLong(names + 24), stringsSize = elf.getLong(names + 32);
        region(bytes, strings, stringsSize);
        boolean info = false, infoSection = false, symbols = false, module = false, code = false, versions = false;
        boolean credentialParameter = false;
        boolean deploymentParameter = false;
        boolean runtimeFactsMarker = false;
        String vermagic = null;
        long extendedCrcs = -1, extendedNames = -1;
        Set<String> versionLabels = new java.util.HashSet<>();
        int symbolTable = -1;
        for (int index = 0; index < count; index++) {
            BootImageRebuilder.cancelled();
            int at = (int) table + index * size;
            String name = cstring(bytes, strings + Integer.toUnsignedLong(elf.getInt(at)), strings + stringsSize);
            long offset = elf.getLong(at + 24), length = elf.getLong(at + 32);
            if (elf.getInt(at + 4) != 8) region(bytes, offset, length);
            if (length > 0 && (elf.getLong(at + 8) & 4) != 0) code = true;
            if (page != 0 && elf.getInt(at + 4) == 2 && !name.equals(".symtab")) throw error("BRIDGE_METADATA_SYMBOL_TABLE");
            if (name.equals(".symtab") && length >= 24 && elf.getInt(at + 4) == 2) symbols = true;
            if (page != 0 && name.equals(".symtab")) {
                if (symbolTable != -1 || elf.getInt(at + 4) != 2) throw error("BRIDGE_METADATA_SYMBOL_TABLE");
                symbolTable = at;
            }
            if (name.equals(".gnu.linkonce.this_module") && length > 0) module = true;
            if (name.equals("__versions") && length > 0) {
                if (elf.getInt(at + 4) != 1 || length % 64 != 0) throw error("BRIDGE_MODVERSIONS");
                for (long cursor = offset; cursor < offset + length; cursor += 64) {
                    String label = cstring(bytes, cursor + 8, cursor + 64);
                    if (label.isEmpty() || !versionLabels.add(label)) throw error("BRIDGE_MODVERSIONS");
                }
                versions = true;
            }
            if (name.equals("__version_ext_crcs")) {
                if (extendedCrcs != -1 || elf.getInt(at + 4) != 1 || length % 4 != 0) throw error("BRIDGE_MODVERSIONS");
                extendedCrcs = length / 4;
            }
            if (name.equals("__version_ext_names")) {
                if (extendedNames != -1 || elf.getInt(at + 4) != 1) throw error("BRIDGE_MODVERSIONS");
                extendedNames = 0;
                for (long cursor = offset; cursor < offset + length;) {
                    String label = cstring(bytes, cursor, offset + length);
                    if (label.isEmpty() || !versionLabels.add(label)) throw error("BRIDGE_MODVERSIONS");
                    extendedNames++; cursor += label.length() + 1;
                }
            }
            if (name.equals(".modinfo")) {
                if (infoSection) throw error("BRIDGE_SECTIONS");
                if (page != 0 && elf.getInt(at + 4) != 1) throw error("BRIDGE_SECTIONS");
                infoSection = true;
                region(bytes, offset, length);
                for (long cursor = offset; cursor < offset + length;) {
                    String field = cstring(bytes, cursor, offset + length);
                    cursor += field.length() + 1;
                    if (field.startsWith("vermagic=")) {
                        if (info) throw error("BRIDGE_VERMAGIC");
                        vermagic = field.substring(9);
                        info = true;
                    }
                    if (page != 0 && field.startsWith("parmtype=credential:")) {
                        if (credentialParameter || !field.equals("parmtype=credential:string"))
                            throw error("BRIDGE_CREDENTIAL_PARAMETER_TYPE");
                        credentialParameter = true;
                    }
                    if (field.equals("parmtype=deployment_namespace") || field.startsWith("parmtype=deployment_namespace:")) {
                        if (deploymentParameter || elf.getInt(at + 4) != 1 ||
                                !field.equals("parmtype=deployment_namespace:string")) throw error("BRIDGE_DEPLOYMENT_NAMESPACE_PARAMETER_TYPE");
                        deploymentParameter = true;
                    }
                    if (field.startsWith("maplesu_runtime_facts=")) {
                        if (runtimeFactsMarker || !field.equals("maplesu_runtime_facts=1")) throw error("BRIDGE_RUNTIME_FACTS");
                        runtimeFactsMarker = true;
                    }
                }
            }
        }
        if (extendedCrcs != -1 || extendedNames != -1) {
            if (extendedCrcs != extendedNames) throw error("BRIDGE_MODVERSIONS");
            versions |= extendedCrcs > 0;
        }
        if (!info || !symbols || !module || !code) throw error("BRIDGE_MODULE");
        if (targetVermagic == null) {
            if (!(vermagic.equals(release) || vermagic.startsWith(release + " "))) throw error("BRIDGE_VERMAGIC");
        } else {
            // Follow the target same_magic(): actual CRC sections select the
            // release-word exception; the complete remaining bytes still match.
            String actual = vermagic, required = targetVermagic;
            if (versions) {
                actual = actual.indexOf(' ') < 0 ? "" : actual.substring(actual.indexOf(' '));
                required = required.substring(required.indexOf(' '));
            }
            if (!actual.equals(required)) throw error("BRIDGE_VERMAGIC");
        }
        if (modversions && !versions) throw error("BRIDGE_MODVERSIONS");
        if (runtimeFacts && !runtimeFactsMarker) throw error("BRIDGE_RUNTIME_FACTS");
        if (page != 0) {
            if (!credentialParameter) throw error("BRIDGE_CREDENTIAL_PARAMETER_TYPE");
            moduleMetadata(bytes, elf, (int) table, count, symbolTable, page, displacement);
        }
        return deploymentParameter;
    }

    private static void moduleMetadata(byte[] bytes, ByteBuffer elf, int table, int count,
                                      int symbols, int page, int displacement) throws IOException {
        if (symbols < 0) throw error("BRIDGE_METADATA_SYMBOL_TABLE");
        long offset = elf.getLong(symbols + 24), length = elf.getLong(symbols + 32);
        int stringsIndex = elf.getInt(symbols + 40);
        if (elf.getLong(symbols + 56) != 24 || length < 24 || length % 24 != 0 ||
                stringsIndex <= 0 || stringsIndex >= count) throw error("BRIDGE_METADATA_SYMBOL_TABLE");
        int strings = table + stringsIndex * 64;
        if (elf.getInt(strings + 4) != 3) throw error("BRIDGE_METADATA_SYMBOL_TABLE");
        long stringsOffset = elf.getLong(strings + 24), stringsSize = elf.getLong(strings + 32);
        region(bytes, stringsOffset, stringsSize);
        boolean[] found = new boolean[2];
        int[] sections = new int[2];
        long[] values = new long[2];
        for (long cursor = offset; cursor < offset + length; cursor += 24) {
            BootImageRebuilder.cancelled();
            int at = (int) cursor;
            long nameOffset = Integer.toUnsignedLong(elf.getInt(at));
            if (nameOffset >= stringsSize) throw error("BRIDGE_METADATA_SYMBOL_TABLE");
            String name = cstring(bytes, stringsOffset + nameOffset, stringsOffset + stringsSize);
            int which = name.equals("msu_sdk_call_prefix") ? 0 : name.equals("msu_sdk_call_blr") ? 1 : -1;
            if (which < 0) continue;
            int section = elf.getShort(at + 6) & 65535;
            if (found[which] || section == 0 || section >= count || section >= 0xff00)
                throw error("BRIDGE_METADATA_SYMBOL:" + name);
            found[which] = true; sections[which] = section; values[which] = elf.getLong(at + 8);
            long size = elf.getLong(at + 16), sectionSize = elf.getLong(table + section * 64 + 32);
            if (values[which] < 0 || size < 0 || values[which] > sectionSize || size > sectionSize - values[which])
                throw error("BRIDGE_METADATA_SYMBOL_RANGE");
        }
        if (!found[0] || !found[1]) throw error("BRIDGE_METADATA_SYMBOL_REQUIRED");
        if (sections[0] != sections[1]) throw error("BRIDGE_METADATA_SYMBOL_SECTION");
        int section = table + sections[0] * 64;
        long start = values[0], call = values[1], sectionSize = elf.getLong(section + 32);
        if (elf.getInt(section + 4) != 1 || (elf.getLong(section + 8) & 4) == 0 || call <= start ||
                sectionSize < 4 || call > sectionSize - 4 || (start & 3) != 0 || (call & 3) != 0)
            throw error("BRIDGE_METADATA_SYMBOL_RANGE");
        // ET_REL values share one executable section: the loader's relocation base cancels out.
        long actual = page - (call - start);
        if (actual == 0 || (actual & 3) != 0 || actual < -134217728 || actual > 134217728 || actual != displacement)
            throw error("BRIDGE_METADATA_DISPLACEMENT");
    }

    private static boolean configured(String config, String name) {
        for (String line : config.split("\n")) if (line.strip().equals(name + "=y")) return true;
        return false;
    }

    private static void region(byte[] bytes, long offset, long length) throws IOException {
        if (offset < 0 || length < 0 || offset > bytes.length || length > bytes.length - offset) throw error("BRIDGE_SECTIONS");
    }
    private static String cstring(byte[] bytes, long offset, long end) throws IOException {
        if (offset < 0 || offset >= end || end > bytes.length) throw error("BRIDGE_SECTIONS");
        int at = (int) offset;
        while (at < end && bytes[at] != 0) {
            if (at - offset >= 4096) throw error("BRIDGE_SECTIONS");
            at++;
        }
        if (at == end) throw error("BRIDGE_SECTIONS");
        return new String(bytes, (int) offset, at - (int) offset, StandardCharsets.ISO_8859_1);
    }

    private static byte[] bound(Path directory, Map<String, String> fields, String name, int limit) throws IOException {
        String path = fields.get(name);
        if (path.isEmpty()) throw error("PATH:" + name);
        byte[] bytes = file(directory.resolve(path), limit);
        if (!KernelIdentity.sha256(bytes).equals(fields.get(name + "Sha256"))) throw error("HASH:" + name);
        return bytes;
    }

    private static byte[] file(Path path, int limit) throws IOException {
        var identity = BootImageRebuilder.input(path, 1, limit, "ULTRA_INIT_ARTIFACT_SIZE");
        byte[] bytes = UltraInitBootProducer.range(path.toFile(), 0, (int) identity.size());
        try (var input = java.nio.channels.FileChannel.open(path, java.nio.file.StandardOpenOption.READ,
                java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            BootImageRebuilder.unchanged(path, identity, input, KernelIdentity.sha256(bytes));
        }
        return bytes;
    }

    /** Only the declared flat fields and strictly bounded JSON integers are accepted. */
    private static final class Json {
        final String text;
        int at;
        Json(String text) { this.text = text; }

        Map<String, String> read() throws IOException {
            Map<String, String> fields = new LinkedHashMap<>();
            take('{');
            do {
                String name = string(); take(':');
                String value;
                if (name.equals("schemaVersion")) {
                    whitespace();
                    if (at >= text.length() || text.charAt(at) != '1' && text.charAt(at) != '2') throw error("JSON");
                    value = String.valueOf(text.charAt(at++));
                }
                else if (name.equals("metadataDisplacement")) value = displacement();
                else if (name.equals("pageSize")) {
                    whitespace(); int start = at;
                    while (at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9') at++;
                    if (at == start || at - start > 5 || at - start > 1 && text.charAt(start) == '0') throw error("PAGE_SIZE");
                    value = text.substring(start, at);
                }
                else value = string();
                if (!FIELDS.contains(name) || fields.putIfAbsent(name, value) != null) throw error("FIELDS");
                whitespace();
                if (at < text.length() && text.charAt(at) == ',') { at++; continue; }
                break;
            } while (true);
            take('}'); whitespace();
            if (at != text.length()) throw error("JSON");
            return fields;
        }

        String displacement() throws IOException {
            whitespace();
            int start = at;
            boolean negative = at < text.length() && text.charAt(at) == '-';
            if (negative) at++;
            int digits = at;
            long value = 0;
            while (at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9') {
                value = value * 10 + text.charAt(at++) - '0';
                if (value > 134217728) throw error("METADATA_DISPLACEMENT");
            }
            if (at == digits || at - digits > 1 && text.charAt(digits) == '0' || value == 0 || (value & 3) != 0)
                throw error("METADATA_DISPLACEMENT");
            if (at < text.length() && ",} \t\r\n".indexOf(text.charAt(at)) < 0) throw error("JSON");
            return text.substring(start, at);
        }

        String string() throws IOException {
            take('"'); StringBuilder out = new StringBuilder();
            while (at < text.length()) {
                char value = text.charAt(at++);
                if (value == '"') return out.toString();
                if (value < 32) throw error("JSON");
                if (value == '\\') {
                    if (at >= text.length()) throw error("JSON");
                    char escape = text.charAt(at++);
                    switch (escape) {
                        case '"': case '\\': case '/': value = escape; break;
                        case 'b': value = '\b'; break;
                        case 'f': value = '\f'; break;
                        case 'n': value = '\n'; break;
                        case 'r': value = '\r'; break;
                        case 't': value = '\t'; break;
                        case 'u':
                            if (text.length() - at < 4) throw error("JSON");
                            int code = 0;
                            for (int index = 0; index < 4; index++) {
                                int digit = Character.digit(text.charAt(at++), 16);
                                if (digit < 0) throw error("JSON"); code = (code << 4) | digit;
                            }
                            value = (char) code; break;
                        default: throw error("JSON");
                    }
                }
                out.append(value);
            }
            throw error("JSON");
        }

        void take(char expected) throws IOException {
            whitespace();
            if (at >= text.length() || text.charAt(at++) != expected) throw error("JSON");
        }
        void whitespace() { while (at < text.length() && " \t\r\n".indexOf(text.charAt(at)) >= 0) at++; }
    }

    private static IOException error(String code) { return new IOException("ULTRA_INIT_ARTIFACT_" + code); }
}
