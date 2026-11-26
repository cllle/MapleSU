package dev.maplesu.image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/** Synthetic byte/file fixtures only. No module, ELF, device or SDK is executed. */
public final class UltraInitBootProducerTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private interface Operation { void run() throws Exception; }
    private static IOException fails(String code, Operation action) throws Exception {
        try { action.run(); throw new AssertionError("expected " + code); }
        catch (IOException error) {
            if (error.getMessage() == null || !error.getMessage().startsWith(code))
                throw new AssertionError("expected " + code + ", got " + error, error);
            checks++; return error;
        }
    }

    private static byte[] entry(String name, int mode, byte[] data, boolean crc) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write((crc ? "070702" : "070701").getBytes(StandardCharsets.US_ASCII));
        byte[] path = name.getBytes(StandardCharsets.US_ASCII);
        long sum = 0; if (crc) for (byte value : data) sum += value & 255;
        for (long value : new long[]{19, mode, 0, 0, 1, 1699999999, data.length, 0, 0, 0, 0, path.length + 1, sum})
            out.write(String.format(Locale.ROOT, "%08x", value).getBytes(StandardCharsets.US_ASCII));
        out.write(path); out.write(0);
        while ((out.size() & 3) != 0) out.write(0x71);
        out.write(data);
        while ((out.size() & 3) != 0) out.write(0x53);
        return out.toByteArray();
    }
    private static byte[] join(byte[]... arrays) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] bytes : arrays) out.write(bytes);
        return out.toByteArray();
    }
    private static byte[] stock(boolean crc, boolean symlink) throws Exception {
        return join(entry("dev", 0040755, new byte[0], crc),
                entry("init", symlink ? 0120777 : 0100750, ascii(symlink ? "system/bin/init" : "original-init"), crc),
                entry("vendor-file", 0100641, new byte[]{1, 0, -1, 7}, crc),
                entry("unrelated-marker", 0100644, ascii("inert-third-party-marker"), crc),
                entry("dev", 0040700, new byte[0], crc),
                entry("TRAILER!!!", 0, new byte[0], crc), new byte[17]);
    }
    private static byte[] ascii(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
    private static byte[] image(byte[] kernel, byte[] ramdisk) {
        int ramdiskAt = align(4096 + kernel.length), end = align(ramdiskAt + ramdisk.length);
        byte[] image = new byte[Math.max(end + 8192, 131072)];
        ByteBuffer h = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
        h.put(ascii("ANDROID!")); h.putInt(8, kernel.length).putInt(12, ramdisk.length).putInt(20, 1584).putInt(40, 4);
        System.arraycopy(kernel, 0, image, 4096, kernel.length);
        System.arraycopy(ramdisk, 0, image, ramdiskAt, ramdisk.length);
        return image;
    }
    private static int align(int value) { return (value + 4095) & ~4095; }
    private static byte[] kernel() {
        byte[] raw = new byte[4096];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).putLong(24, 2);
        System.arraycopy(ascii("ARMd"), 0, raw, 56, 4);
        System.arraycopy(ascii("Linux version 6.6.1-fixture (byte fixture)\n"), 0, raw, 128, 40);
        return raw;
    }
    private static byte[] handoff() {
        byte[] bytes = new byte[256];
        ByteBuffer h = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        h.put(new byte[]{127, 'E', 'L', 'F', 2, 1, 1}); h.putShort(16, (short) 2).putShort(18, (short) 183)
                .putInt(20, 1).putLong(24, 0x10080).putLong(32, 64).putShort(52, (short) 64)
                .putShort(54, (short) 56).putShort(56, (short) 1);
        h.putInt(64, 1).putInt(68, 5).putLong(72, 0).putLong(80, 0x10000)
                .putLong(96, bytes.length).putLong(104, bytes.length).putLong(112, 65536);
        return bytes;
    }

    private static byte[] module() throws Exception {
        byte[] names = ascii("\0.shstrtab\0.text\0.symtab\0.gnu.linkonce.this_module\0.modinfo\0");
        byte[] info = ascii("vermagic=6.6.1-fixture SMP\0");
        byte[] bytes = new byte[1024];
        ByteBuffer h = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        h.put(new byte[]{127, 'E', 'L', 'F', 2, 1, 1}); h.putShort(16, (short) 1).putShort(18, (short) 183)
                .putInt(20, 1).putLong(40, 64).putShort(52, (short) 64)
                .putShort(58, (short) 64).putShort(60, (short) 6).putShort(62, (short) 1);
        int[] nameAt = {0, 1, 11, 17, 25, 51};
        for (int index = 1; index < 6; index++) {
            int at = 64 + index * 64;
            h.putInt(at, nameAt[index]).putInt(at + 4, index == 1 ? 3 : index == 3 ? 2 : 1)
                    .putLong(at + 8, index == 2 ? 4 : 0).putLong(at + 24, index == 1 ? 448 : index == 5 ? 768 : 640)
                    .putLong(at + 32, index == 1 ? names.length : index == 5 ? info.length : 24);
        }
        System.arraycopy(names, 0, bytes, 448, names.length); System.arraycopy(info, 0, bytes, 768, info.length);
        return bytes;
    }

    private static byte[] parameterModule(int call) {
        String names = "\0.shstrtab\0.text\0.symtab\0.gnu.linkonce.this_module\0.modinfo\0.strtab\0";
        String symbols = "\0msu_sdk_call_prefix\0msu_sdk_call_blr\0msu_sdk_call_template\0";
        byte[] info = ascii("vermagic=6.6.1-fixture SMP\0parmtype=credential:string\0");
        byte[] bytes = new byte[1024 + call + 4];
        ByteBuffer h = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        h.put(new byte[]{127, 'E', 'L', 'F', 2, 1, 1}); h.putShort(16, (short) 1).putShort(18, (short) 183)
                .putInt(20, 1).putLong(40, 64).putShort(52, (short) 64)
                .putShort(58, (short) 64).putShort(60, (short) 7).putShort(62, (short) 1);
        String[] sections = {"", ".shstrtab", ".text", ".symtab", ".gnu.linkonce.this_module", ".modinfo", ".strtab"};
        int[] offsets = {0, 512, 1024, 704, 800, 832, 640};
        int[] sizes = {0, names.length(), call + 4, 96, 24, info.length, symbols.length()};
        for (int index = 1; index < sections.length; index++) {
            int at = 64 + index * 64;
            h.putInt(at, names.indexOf(sections[index])).putInt(at + 4, index == 1 || index == 6 ? 3 : index == 3 ? 2 : 1)
                    .putLong(at + 8, index == 2 ? 6 : 0).putLong(at + 24, offsets[index]).putLong(at + 32, sizes[index]);
        }
        h.putInt(64 + 3 * 64 + 40, 6).putLong(64 + 3 * 64 + 56, 24);
        for (int index = 1; index <= 3; index++) {
            int at = 704 + index * 24;
            h.putInt(at, symbols.indexOf(index == 1 ? "msu_sdk_call_prefix" : index == 2 ? "msu_sdk_call_blr" : "msu_sdk_call_template"))
                    .putShort(at + 6, (short) 2).putLong(at + 8, index == 1 ? 0 : index == 2 ? call : 4);
        }
        System.arraycopy(ascii(names), 0, bytes, 512, names.length());
        System.arraycopy(ascii(symbols), 0, bytes, 640, symbols.length());
        System.arraycopy(info, 0, bytes, 832, info.length);
        h.putInt(1024 + call, 0xd63f0140); // synthetic BLR x10, never executed
        return bytes;
    }

    private static void manifest(Path work, UltraInitBootProducer.Artifact artifact) throws Exception {
        Files.write(work.resolve("handoff.bin"), artifact.handoff); Files.write(work.resolve("bridge.ko"), module());
        String json = "{\"schemaVersion\":1,\"targetBoot\":\"target.img\",\"targetBootSha256\":\"" + artifact.targetBootSha256 +
                "\",\"kernelRelease\":\"" + artifact.kernelRelease + "\",\"kernelSha256\":\"" + artifact.kernelSha256 +
                "\",\"credentialSha256\":\"" + artifact.credentialSha256 + "\",\"handoff\":\"handoff.bin\",\"handoffSha256\":\"" +
                artifact.handoffSha256 + "\",\"bridge\":\"bridge.ko\",\"bridgeSha256\":\"" + KernelIdentity.sha256(module()) + "\"}";
        Path manifest = work.resolve("artifact.json"); Files.writeString(manifest, json);
        var parsed = UltraInitBootProducer.readArtifact(manifest.toFile());
        check(parsed.kernelSha256.equals(artifact.kernelSha256), "public manifest parser binds real input bytes");
        Files.writeString(manifest, json.replace(artifact.handoffSha256, "0".repeat(64)));
        fails("ULTRA_INIT_ARTIFACT_HASH:handoff", () -> UltraInitBootProducer.readArtifact(manifest.toFile()));
        Files.writeString(manifest, json.replace("\"kernelRelease\":\"6.6.1-fixture\"", "\"kernelRelease\":\"6.6.2-other\""));
        fails("ULTRA_INIT_ARTIFACT_TARGET_KERNEL", () -> UltraInitBootProducer.readArtifact(manifest.toFile()));
        Files.writeString(manifest, json.replace("\"bridge.ko\"", "\"absent.ko\""));
        try { UltraInitBootProducer.readArtifact(manifest.toFile()); throw new AssertionError("missing bridge accepted"); }
        catch (java.nio.file.NoSuchFileException expected) { checks++; }
    }

    private static void rejectedManifest(Path path, String json, String code) throws Exception {
        Files.writeString(path, json);
        fails("ULTRA_INIT_ARTIFACT_" + code, () -> UltraInitBootProducer.readArtifact(path.toFile()));
    }

    private static void runtimeCredential(Path work) throws Exception {
        Path target = work.resolve("target.img"), source = work.resolve("input.img"), output = work.resolve("candidate.img"),
                repeated = work.resolve("repeat.img"), restored = work.resolve("restored.img"), manifest = work.resolve("artifact.json");
        byte[] raw = kernel(), targetBytes = image(raw, new byte[0]); Files.write(target, targetBytes);
        // A synthetic signature-shaped tail proves byte preservation, not cryptographic validity or loading.
        byte[] bridge = join(parameterModule(32), ascii("~Module signature appended~\n")), handoff = handoff();
        byte[] config = ascii("CONFIG_MODULES=y\nCONFIG_ARM64=y\nCONFIG_ARM64_4K_PAGES=y\n");
        Files.write(work.resolve("bridge.ko"), bridge); Files.write(work.resolve("handoff.bin"), handoff);
        Files.write(work.resolve("target.config"), config);
        String json = "{\"schemaVersion\":1,\"targetBoot\":\"target.img\",\"targetBootSha256\":\"" + KernelIdentity.sha256(targetBytes) +
                "\",\"kernelRelease\":\"6.6.1-fixture\",\"kernelSha256\":\"" + KernelIdentity.sha256(raw) +
                "\",\"handoff\":\"handoff.bin\",\"handoffSha256\":\"" + KernelIdentity.sha256(handoff) +
                "\",\"bridge\":\"bridge.ko\",\"bridgeSha256\":\"" + KernelIdentity.sha256(bridge) +
                "\",\"config\":\"target.config\",\"configSha256\":\"" + KernelIdentity.sha256(config) +
                "\",\"credentialMode\":\"module-parameter\",\"metadataDisplacement\":4064}";
        Files.writeString(manifest, json);
        var generic = UltraInitBootProducer.readArtifact(manifest.toFile());
        check(generic.credentialSha256 == null && generic.credentialMode.equals("module-parameter") &&
                generic.metadataDisplacement == 4064, "generic module package has no fabricated credential hash");
        byte[] cpio = stock(true, true), encoded = KernelCompression.encode(cpio, "GZIP");
        encoded[4] = 0x29; encoded = Arrays.copyOf(encoded, encoded.length + 9);
        byte[] baseline = image(new byte[0], encoded); Files.write(source, baseline);
        fails("ULTRA_INIT_BOOT_CREDENTIAL_REQUIRED", () -> UltraInitBootProducer.produce(source.toFile(), output.toFile(), generic));
        check(!Files.exists(output), "unbound package exposes no candidate");
        byte[] actual = ascii("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKL"), expected = actual.clone();
        var bound = generic.withCredential(actual); Arrays.fill(actual, (byte) 0);
        check(bound != generic && generic.credentialSha256 == null && bound.credentialSha256.equals(KernelIdentity.sha256(expected)),
                "binding clones actual bytes without mutating generic artifact");
        check(bound.targetBootSha256.equals(generic.targetBootSha256) && bound.kernelSha256.equals(generic.kernelSha256) &&
                bound.handoffSha256.equals(generic.handoffSha256) && bound.bridgeSha256.equals(generic.bridgeSha256) &&
                bound.targetInputSha256.equals(generic.targetInputSha256), "binding preserves all package and target contracts");
        check(Files.readString(manifest).equals(json), "binding never persists raw secret or rewrites manifest");
        for (byte[] invalid : new byte[][]{null, new byte[47], new byte[49], ascii("A".repeat(47) + "\n"),
                ascii("A".repeat(47) + "="), new byte[48]})
            fails("ULTRA_INIT_BOOT_CREDENTIAL_INVALID", () -> generic.withCredential(invalid));
        UltraInitBootProducer.produce(source.toFile(), output.toFile(), bound);
        check(UltraInitBootProducer.recognizes(output.toFile()), "runtime credential keeps original recovery-envelope ownership");
        var layout = BootImageInspector.inspect(output.toFile());
        byte[] produced = KernelCompression.decode(UltraInitBootProducer.component(output.toFile(), layout), "GZIP", 1024 * 1024);
        var entries = RamdiskInspector.inspectRamdisk(produced);
        var secret = entries.entry(UltraInitRamdisk.CREDENTIAL);
        check(secret != null && secret.mode == 0100600 && secret.uid == 0 && secret.gid == 0 && secret.links == 1 && secret.size == 48,
                "credential is exactly one root-owned regular 0600 entry of length 48");
        check(Arrays.equals(expected, Arrays.copyOfRange(produced, (int) secret.offset, (int) (secret.offset + secret.size))),
                "CPIO has exact bound ASCII without newline or NUL");
        var ko = entries.entry(UltraInitRamdisk.BRIDGE);
        check(Arrays.equals(bridge, Arrays.copyOfRange(produced, (int) ko.offset, (int) (ko.offset + ko.size))),
                "entire module including signature-shaped tail remains byte-identical");
        check(entries.entry(UltraInitRamdisk.ORIGINAL).mode == 0120777 &&
                entries.entry(UltraInitRamdisk.ORIGINAL).sha256.equals(RamdiskInspector.inspectRamdisk(cpio).entry("init").sha256),
                "runtime credential preserves original init type and handoff");
        UltraInitBootProducer.produce(output.toFile(), repeated.toFile(), bound);
        check(Arrays.equals(Files.readAllBytes(output), Files.readAllBytes(repeated)), "bound repatch retains existing envelope idempotence");
        Files.delete(repeated);
        byte[] next = expected.clone(); next[0] = 'Z';
        UltraInitBootProducer.produce(output.toFile(), repeated.toFile(), generic.withCredential(next));
        byte[] changed = KernelCompression.decode(UltraInitBootProducer.component(repeated.toFile(),
                BootImageInspector.inspect(repeated.toFile())), "GZIP", 1024 * 1024);
        var changedSecret = RamdiskInspector.inspectRamdisk(changed).entry(UltraInitRamdisk.CREDENTIAL);
        check(Arrays.equals(next, Arrays.copyOfRange(changed, (int) changedSecret.offset, (int) (changedSecret.offset + changedSecret.size))),
                "credential rotation replaces owned deployment, not the original recovery input");
        UltraInitBootProducer.restore(repeated.toFile(), restored.toFile());
        check(Arrays.equals(baseline, Files.readAllBytes(restored)), "rotation restores exact first encoded stream, header and padding");
        check(Arrays.equals(targetBytes, Files.readAllBytes(target)) && Arrays.equals(baseline, Files.readAllBytes(source)) &&
                Arrays.equals(bridge, Files.readAllBytes(work.resolve("bridge.ko"))), "paired boot, input image and real package module stay read-only");
        fails("ULTRA_INIT_CPIO_PATH_CONFLICT", () -> UltraInitRamdisk.install(
                join(entry("./.maplesu_ultra/credential", 0100600, expected, true), cpio), handoff, bridge, null, expected));
        Files.delete(output);
        IOException first = fails("RUNTIME_CREDENTIAL_FIRST", () -> UltraInitBootProducer.produce(source.toFile(), output.toFile(), bound,
                directory -> { throw new IOException("RUNTIME_CREDENTIAL_FIRST"); }));
        check(first.getClass() == IOException.class && !Files.exists(output), "runtime credential failure compensates output without error normalization");
        rejectedManifest(manifest, json.replace("\"module-parameter\"", "\"Module-Parameter\""), "CREDENTIAL_MODE");
        rejectedManifest(manifest, json.replace(",\"credentialMode\":\"module-parameter\"", ""), "FIELDS");
        rejectedManifest(manifest, json.replace(",\"metadataDisplacement\":4064", ""), "METADATA_DISPLACEMENT");
        for (String value : new String[]{"0", "-0", "2", "0132", "134217732", "-134217732", "2147483648", "999999999999999999999", "\"4096\"", "{}", "[]", "null"})
            rejectedManifest(manifest, json.replace("\"metadataDisplacement\":4064", "\"metadataDisplacement\":" + value), "METADATA_DISPLACEMENT");
        for (String value : new String[]{"4096.0", "4e3"})
            rejectedManifest(manifest, json.replace("\"metadataDisplacement\":4064", "\"metadataDisplacement\":" + value), "JSON");
        rejectedManifest(manifest, json.replace("\"metadataDisplacement\":4064", "\"metadataDisplacement\":4064,\"metadataDisplacement\":4064"), "FIELDS");
        rejectedManifest(manifest, json.replace("\"credentialMode\":\"module-parameter\"", "\"credentialMode\":\"module-parameter\",\"credentialMode\":\"module-parameter\""), "FIELDS");
        rejectedManifest(manifest, json.replace("\"credentialMode\":\"module-parameter\"", "\"credentialMode\":{}"), "JSON");
        rejectedManifest(manifest, json.replace("\"metadataDisplacement\":4064", "\"metadataDisplacement\":-134217728"), "BRIDGE_METADATA_DISPLACEMENT");
        String pinned = json.replace("\"credentialMode\"", "\"credentialSha256\":\"" + KernelIdentity.sha256(expected) + "\",\"credentialMode\"");
        Files.writeString(manifest, pinned);
        var pinnedArtifact = UltraInitBootProducer.readArtifact(manifest.toFile());
        check(pinnedArtifact.withCredential(expected).credentialSha256.equals(KernelIdentity.sha256(expected)), "optional supplied credential hash matches actual binding");
        fails("ULTRA_INIT_BOOT_CREDENTIAL_HASH", () -> pinnedArtifact.withCredential(next));
        Files.writeString(manifest, pinned.replace(",\"credentialMode\":\"module-parameter\",\"metadataDisplacement\":4064", ""));
        var embedded = UltraInitBootProducer.readArtifact(manifest.toFile());
        check(embedded.credentialMode.equals("embedded") && embedded.metadataDisplacement == 0 &&
                embedded.credentialSha256.equals(KernelIdentity.sha256(expected)), "legacy embedded manifest keeps exact original hash contract");
        fails("ULTRA_INIT_BOOT_CREDENTIAL_MODE", () -> embedded.withCredential(expected));
        rejectedManifest(manifest, pinned.replace(KernelIdentity.sha256(expected), "not-a-hash"), "HASH:credential");
        check(RamdiskInspector.inspectRamdisk(UltraInitRamdisk.install(cpio, embedded.handoff, embedded.bridge)).entry(UltraInitRamdisk.CREDENTIAL) == null,
                "embedded ramdisk has no runtime credential entry");
        Arrays.fill(expected, (byte) 0); Arrays.fill(next, (byte) 0);
    }

    private static String metadataManifest(Path work, byte[] raw, byte[] bridge, int displacement, String config) throws Exception {
        byte[] boot = image(raw, new byte[0]), shim = handoff();
        Files.write(work.resolve("target.img"), boot); Files.write(work.resolve("bridge.ko"), bridge);
        Files.write(work.resolve("handoff.bin"), shim);
        String extra = "";
        if (config != null) {
            Files.write(work.resolve("target.config"), ascii(config));
            extra = ",\"config\":\"target.config\",\"configSha256\":\"" + KernelIdentity.sha256(ascii(config)) + "\"";
        }
        String json = "{\"schemaVersion\":1,\"targetBoot\":\"target.img\",\"targetBootSha256\":\"" + KernelIdentity.sha256(boot) +
                "\",\"kernelRelease\":\"6.6.1-fixture\",\"kernelSha256\":\"" + KernelIdentity.sha256(raw) +
                "\",\"handoff\":\"handoff.bin\",\"handoffSha256\":\"" + KernelIdentity.sha256(shim) +
                "\",\"bridge\":\"bridge.ko\",\"bridgeSha256\":\"" + KernelIdentity.sha256(bridge) +
                "\",\"credentialMode\":\"module-parameter\",\"metadataDisplacement\":" + displacement + extra + "}";
        Files.writeString(work.resolve("artifact.json"), json);
        return json;
    }

    private static void artifactSidecars(Path work) throws Exception {
        Path manifest = work.resolve("artifact.json"), sidecar = work.resolve("task-profile-abi.json");
        String base = metadataManifest(work, kernel(), parameterModule(32), 4064, null);
        check(UltraInitBootProducer.readArtifact(manifest.toFile()).targetInputSha256.isEmpty(),
                "absent optional sidecars keep the existing artifact contract");
        byte[] proof = ascii("{\"schemaVersion\":1,\"fixture\":\"byte binding only\"}\n");
        Files.write(sidecar, proof);
        String pair = "\"taskProfileAbi\":\"task-profile-abi.json\",\"taskProfileAbiSha256\":\"" + KernelIdentity.sha256(proof) + "\"";
        String json = base.substring(0, base.length() - 1) + "," + pair + "}";
        Files.writeString(manifest, json);
        var artifact = UltraInitBootProducer.readArtifact(manifest.toFile());
        check(artifact.targetInputSha256.size() == 1 &&
                KernelIdentity.sha256(proof).equals(artifact.targetInputSha256.get("taskProfileAbi")),
                "known taskProfileAbi pair binds exact bytes without asserting semantic ABI qualification");
        check(artifact.withCredential(ascii("A".repeat(48))).targetInputSha256.equals(artifact.targetInputSha256),
                "runtime credential binding preserves task-profile generation identity");
        check(Arrays.equals(proof, Files.readAllBytes(sidecar)) && Files.readString(manifest).equals(json),
                "parser and credential binding never rewrite proof or manifest");
        rejectedManifest(manifest, json.replace(",\"taskProfileAbiSha256\":\"" + KernelIdentity.sha256(proof) + "\"", ""), "FIELDS");
        rejectedManifest(manifest, json.replace("\"taskProfileAbi\":\"task-profile-abi.json\",", ""), "FIELDS");
        rejectedManifest(manifest, json.replace(KernelIdentity.sha256(proof), "0".repeat(64)), "HASH:taskProfileAbi");
        rejectedManifest(manifest, json.replace(KernelIdentity.sha256(proof), "not-a-hash"), "HASH:taskProfileAbi");
        rejectedManifest(manifest, json.replace("\"task-profile-abi.json\"", "\"\""), "PATH:taskProfileAbi");
        rejectedManifest(manifest, json.replace("\"taskProfileAbi\":\"task-profile-abi.json\"", "\"taskProfileAbi\":123"), "JSON");
        rejectedManifest(manifest, json.replace(pair, pair + "," + pair), "FIELDS");
        rejectedManifest(manifest, json.replace("taskProfileAbi", "unknownProof"), "FIELDS");
        Files.writeString(manifest, json.replace("task-profile-abi.json", "missing-task-profile.json"));
        try { UltraInitBootProducer.readArtifact(manifest.toFile()); throw new AssertionError("missing task profile accepted"); }
        catch (java.nio.file.NoSuchFileException expected) { checks++; }
        Files.writeString(manifest, json.replace("task-profile-abi.json", "proof-directory"));
        Files.createDirectory(work.resolve("proof-directory"));
        fails("ULTRA_INIT_ARTIFACT_SIZE", () -> UltraInitBootProducer.readArtifact(manifest.toFile()));
        Files.writeString(manifest, json);
        Files.write(sidecar, new byte[0]);
        fails("ULTRA_INIT_ARTIFACT_SIZE", () -> UltraInitBootProducer.readArtifact(manifest.toFile()));
        Files.write(sidecar, new byte[1024 * 1024 + 1]);
        fails("ULTRA_INIT_ARTIFACT_SIZE", () -> UltraInitBootProducer.readArtifact(manifest.toFile()));
        byte[] maximum = new byte[1024 * 1024];
        Files.write(sidecar, maximum);
        Files.writeString(manifest, json.replace(KernelIdentity.sha256(proof), KernelIdentity.sha256(maximum)));
        check(KernelIdentity.sha256(maximum).equals(UltraInitBootProducer.readArtifact(manifest.toFile())
                .targetInputSha256.get("taskProfileAbi")), "exact 1 MiB sidecar remains admitted");
        Files.write(sidecar, proof);
        for (String name : new String[]{"sessionAbi", "bundleSources", "abiEvidence"}) {
            String selected = json.replace("taskProfileAbi", name);
            Files.writeString(manifest, selected);
            var retained = UltraInitBootProducer.readArtifact(manifest.toFile());
            check(KernelIdentity.sha256(proof).equals(retained.targetInputSha256.get(name)),
                    name + " binds the selected bytes");
            check(retained.withCredential(ascii("A".repeat(48))).targetInputSha256.equals(retained.targetInputSha256),
                    name + " survives credential binding");
            if (name.equals("abiEvidence")) {
                rejectedManifest(manifest, selected.replace(",\"abiEvidenceSha256\":\"" + KernelIdentity.sha256(proof) + "\"", ""), "FIELDS");
                rejectedManifest(manifest, selected.replace("\"abiEvidence\":\"task-profile-abi.json\",", ""), "FIELDS");
                rejectedManifest(manifest, selected.replace(KernelIdentity.sha256(proof), "0".repeat(64)), "HASH:abiEvidence");
                rejectedManifest(manifest, selected.replace(KernelIdentity.sha256(proof), "not-a-hash"), "HASH:abiEvidence");
                rejectedManifest(manifest, selected.replace("\"abiEvidence\":\"task-profile-abi.json\"", "\"abiEvidence\":{}"), "JSON");
                rejectedManifest(manifest, selected.replace("\"abiEvidence\":\"task-profile-abi.json\"",
                        "\"abiEvidence\":\"task-profile-abi.json\",\"abiEvidence\":\"task-profile-abi.json\""), "FIELDS");
                check(Files.readAllBytes(sidecar).length == proof.length && Arrays.equals(proof, Files.readAllBytes(sidecar)),
                        "rejected ABI evidence leaves the original sidecar intact");
            }
        }
        Path link = work.resolve("task-profile-link.json");
        Files.createSymbolicLink(link, sidecar.getFileName());
        Files.writeString(manifest, json.replace("task-profile-abi.json", "task-profile-link.json"));
        fails("ULTRA_INIT_ARTIFACT_SIZE", () -> UltraInitBootProducer.readArtifact(manifest.toFile()));
        check(Files.isSymbolicLink(link) && Arrays.equals(proof, Files.readAllBytes(sidecar)),
                "rejected symlink does not mutate proof or link");
    }

    private static void rejectedMetadata(Path work, byte[] bridge, String code) throws Exception {
        metadataManifest(work, kernel(), bridge, 4064, null);
        fails("ULTRA_INIT_ARTIFACT_" + code, () -> UltraInitBootProducer.readArtifact(work.resolve("artifact.json").toFile()));
    }

    private static byte[] kmiModule(String magic, boolean versions) {
        byte[] bytes = Arrays.copyOf(parameterModule(32), 2048);
        String names = "\0.shstrtab\0.text\0.symtab\0.gnu.linkonce.this_module\0.modinfo\0.strtab\0__versions\0";
        byte[] info = ascii("vermagic=" + magic + "\0parmtype=credential:string\0");
        ByteBuffer elf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        elf.putLong(64 + 64 + 24, 1120).putLong(64 + 64 + 32, names.length());
        elf.putLong(64 + 5 * 64 + 32, info.length);
        System.arraycopy(ascii(names), 0, bytes, 1120, names.length());
        System.arraycopy(info, 0, bytes, 832, info.length);
        if (versions) {
            elf.putShort(60, (short) 8);
            int at = 64 + 7 * 64;
            elf.putInt(at, names.indexOf("__versions")).putInt(at + 4, 1)
                    .putLong(at + 24, 1408).putLong(at + 32, 64);
            elf.putLong(1408, 1);
            System.arraycopy(ascii("module_layout\0"), 0, bytes, 1416, 14);
        }
        return bytes;
    }

    private static String kmiManifest(Path work, String moduleMagic, boolean versions, boolean configuredVersions) throws Exception {
        String targetMagic = "6.6.1-fixture SMP preempt mod_unload modversions aarch64";
        byte[] raw = kernel();
        System.arraycopy(ascii(targetMagic + "\0"), 0, raw, 512, targetMagic.length() + 1);
        String config = "CONFIG_ARM64=y\nCONFIG_MODULES=y\nCONFIG_ARM64_4K_PAGES=y\n" +
                (configuredVersions ? "CONFIG_MODVERSIONS=y\n" : "");
        String json = metadataManifest(work, raw, kmiModule(moduleMagic, versions), 4064, config);
        byte[] symvers = ascii("0x00000001\tmodule_layout\tvmlinux\tEXPORT_SYMBOL\n");
        Files.write(work.resolve("Module.symvers"), symvers);
        json = json.substring(0, json.length() - 1) + ",\"targetVermagic\":\"" + targetMagic +
                "\",\"moduleSymvers\":\"Module.symvers\",\"moduleSymversSha256\":\"" + KernelIdentity.sha256(symvers) + "\"}";
        Files.writeString(work.resolve("artifact.json"), json);
        return json;
    }

    private static void kmiVermagic(Path work) throws Exception {
        Path manifest = work.resolve("artifact.json");
        String suffix = " SMP preempt mod_unload modversions aarch64";
        for (boolean configuredVersions : new boolean[]{false, true}) {
            kmiManifest(work, "6.6.127-real-ddk" + suffix, true, configuredVersions);
            check(UltraInitBootProducer.readArtifact(manifest.toFile()).kernelRelease.equals("6.6.1-fixture"),
                    "real CRC section preserves DDK release and binds the complete target suffix");
        }
        for (String changed : new String[]{" SMP preempt mod_unload modversions", "  SMP preempt mod_unload modversions aarch64",
                " SMP preempt mod_unload modversions aarch64 ", " SMP mod_unload preempt modversions aarch64"}) {
            kmiManifest(work, "6.6.127-real-ddk" + changed, true, true);
            fails("ULTRA_INIT_ARTIFACT_BRIDGE_VERMAGIC", () -> UltraInitBootProducer.readArtifact(manifest.toFile()));
        }
        for (boolean configuredVersions : new boolean[]{false, true}) {
            kmiManifest(work, "6.6.127-real-ddk" + suffix, false, configuredVersions);
            fails("ULTRA_INIT_ARTIFACT_BRIDGE_VERMAGIC", () -> UltraInitBootProducer.readArtifact(manifest.toFile()));
        }
        kmiManifest(work, "6.6.1-fixture" + suffix, false, false);
        check(UltraInitBootProducer.readArtifact(manifest.toFile()).kernelRelease.equals("6.6.1-fixture"),
                "without CRC sections the complete target magic must match");
        kmiManifest(work, "6.6.1-fixture" + suffix, false, true);
        fails("ULTRA_INIT_ARTIFACT_BRIDGE_MODVERSIONS", () -> UltraInitBootProducer.readArtifact(manifest.toFile()));
        String json = kmiManifest(work, "6.6.127-real-ddk" + suffix, true, true);
        rejectedManifest(manifest, json.replace("\"targetVermagic\":\"6.6.1-fixture SMP", "\"targetVermagic\":\"6.6.1-fixture FAKE"), "TARGET_VERMAGIC");
        rejectedManifest(manifest, json.replace("\"targetVermagic\":\"6.6.1-fixture", "\"targetVermagic\":\"6.6.2-other"), "TARGET_VERMAGIC");
        byte[] truncated = kmiModule("6.6.127-real-ddk" + suffix, true);
        ByteBuffer.wrap(truncated).order(ByteOrder.LITTLE_ENDIAN).putLong(64 + 7 * 64 + 32, 63);
        Files.write(work.resolve("bridge.ko"), truncated);
        Files.writeString(manifest, json.replace(KernelIdentity.sha256(kmiModule("6.6.127-real-ddk" + suffix, true)), KernelIdentity.sha256(truncated)));
        fails("ULTRA_INIT_ARTIFACT_BRIDGE_MODVERSIONS", () -> UltraInitBootProducer.readArtifact(manifest.toFile()));
    }

    private static void artifactMetadata(Path work) throws Exception {
        Path manifest = work.resolve("artifact.json");
        byte[] bridge = join(parameterModule(32), ascii("~Module signature appended~\n"));
        for (int code = 1; code <= 3; code++) for (int call : new int[]{28, 32}) {
            int page = code == 1 ? 4096 : code == 2 ? 16384 : 65536;
            byte[] raw = kernel(); ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).putLong(24, code * 2L);
            byte[] selected = join(parameterModule(call), ascii("~Module signature appended~\n"));
            if (call == 28) ByteBuffer.wrap(selected).order(ByteOrder.LITTLE_ENDIAN).putLong(776 + 8, 0);
            String json = metadataManifest(work, raw, selected, page - call, null);
            var artifact = UltraInitBootProducer.readArtifact(manifest.toFile());
            check(artifact.metadataDisplacement == page - call, "actual Image page and symbol distance allow prefix/template alias without fixed prefix bytes");
            check(Arrays.equals(selected, Files.readAllBytes(work.resolve("bridge.ko"))) && Arrays.equals(selected, artifact.bridge),
                    "ELF metadata validation never rewrites module or signature tail");
            rejectedManifest(manifest, json.replace("\"metadataDisplacement\":" + (page - call),
                    "\"metadataDisplacement\":" + (page - call + 4)), "BRIDGE_METADATA_DISPLACEMENT");
        }
        metadataManifest(work, kernel(), parameterModule(8192), -4096, null);
        check(UltraInitBootProducer.readArtifact(manifest.toFile()).metadataDisplacement == -4096, "real same-section negative delta accepted");
        byte[] shifted = parameterModule(64);
        ByteBuffer.wrap(shifted).order(ByteOrder.LITTLE_ENDIAN).putLong(728 + 8, 16).putLong(752 + 8, 48).putInt(1024 + 48, 0xd63f0140);
        metadataManifest(work, kernel(), shifted, 4064, null);
        check(UltraInitBootProducer.readArtifact(manifest.toFile()).metadataDisplacement == 4064,
                "delta uses symbol difference rather than absolute BLR offset");
        byte[] raw = kernel(); ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).putLong(24, 0);
        metadataManifest(work, raw, bridge, 16384 - 32, "CONFIG_ARM64=y\nCONFIG_MODULES=y\nCONFIG_ARM64_16K_PAGES=y\n");
        check(UltraInitBootProducer.readArtifact(manifest.toFile()).metadataDisplacement == 16384 - 32,
                "unspecified Image page uses supplied real config source");
        metadataManifest(work, raw, bridge, 4064, null);
        fails("ULTRA_INIT_ARTIFACT_TARGET_PAGE_SIZE_SOURCE_REQUIRED", () -> UltraInitBootProducer.readArtifact(manifest.toFile()));
        metadataManifest(work, kernel(), bridge, 4064, "CONFIG_ARM64=y\nCONFIG_MODULES=y\nCONFIG_ARM64_16K_PAGES=y\n");
        fails("ULTRA_INIT_ARTIFACT_TARGET_PAGE_SIZE", () -> UltraInitBootProducer.readArtifact(manifest.toFile()));
        for (String tags : new String[]{"", "parmtype=credential:charp\0", "parmtype=credential:string\0parmtype=credential:string\0"}) {
            byte[] changed = parameterModule(32), info = ascii("vermagic=6.6.1-fixture SMP\0" + tags);
            Arrays.fill(changed, 832, 1024, (byte) 0); System.arraycopy(info, 0, changed, 832, info.length);
            ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putLong(64 + 5 * 64 + 32, info.length);
            rejectedMetadata(work, changed, "BRIDGE_CREDENTIAL_PARAMETER_TYPE");
        }
        byte[] changed = parameterModule(32);
        ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putInt(752, 1);
        rejectedMetadata(work, changed, "BRIDGE_METADATA_SYMBOL:msu_sdk_call_prefix");
        for (int section : new int[]{0, 7, 0xfff1, 0xffff}) {
            changed = parameterModule(32); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putShort(728 + 6, (short) section);
            rejectedMetadata(work, changed, "BRIDGE_METADATA_SYMBOL:msu_sdk_call_prefix");
        }
        changed = parameterModule(32); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putInt(752, 0);
        rejectedMetadata(work, changed, "BRIDGE_METADATA_SYMBOL_REQUIRED");
        changed = parameterModule(32); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putShort(752 + 6, (short) 4).putLong(752 + 8, 4);
        rejectedMetadata(work, changed, "BRIDGE_METADATA_SYMBOL_SECTION");
        for (long call : new long[]{-4, 0, 2, 36, Long.MAX_VALUE}) {
            changed = parameterModule(32); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putLong(752 + 8, call);
            rejectedMetadata(work, changed, "BRIDGE_METADATA_SYMBOL_RANGE");
        }
        changed = parameterModule(32); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putLong(752 + 16, 8);
        rejectedMetadata(work, changed, "BRIDGE_METADATA_SYMBOL_RANGE");
        changed = parameterModule(4096);
        rejectedMetadata(work, changed, "BRIDGE_METADATA_DISPLACEMENT");
        changed = parameterModule(32); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putInt(64 + 2 * 64 + 4, 8);
        rejectedMetadata(work, changed, "BRIDGE_METADATA_SYMBOL_RANGE");
        changed = parameterModule(32); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putLong(64 + 2 * 64 + 8, 2);
        rejectedMetadata(work, changed, "BRIDGE_MODULE");
        for (int link : new int[]{0, 2, 7, -1}) {
            changed = parameterModule(32); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putInt(64 + 3 * 64 + 40, link);
            rejectedMetadata(work, changed, "BRIDGE_METADATA_SYMBOL_TABLE");
        }
        changed = parameterModule(32); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putLong(64 + 3 * 64 + 56, 16);
        rejectedMetadata(work, changed, "BRIDGE_METADATA_SYMBOL_TABLE");
        changed = parameterModule(32); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putLong(64 + 3 * 64 + 32, 71);
        rejectedMetadata(work, changed, "BRIDGE_METADATA_SYMBOL_TABLE");
        changed = parameterModule(32); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putInt(728, -1);
        rejectedMetadata(work, changed, "BRIDGE_METADATA_SYMBOL_TABLE");
        changed = parameterModule(32); Arrays.fill(changed, 640 + ascii("\0msu_sdk_call_prefix\0").length, 704, (byte) 'x');
        rejectedMetadata(work, changed, "BRIDGE_SECTIONS");
        changed = parameterModule(32); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putLong(64 + 6 * 64 + 24, changed.length - 1);
        rejectedMetadata(work, changed, "BRIDGE_SECTIONS");
        changed = parameterModule(32); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putInt(64 + 1 * 64 + 4, 8);
        rejectedMetadata(work, changed, "BRIDGE_SECTIONS");
        changed = parameterModule(32); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(64 + 4 * 64, 17).putInt(64 + 4 * 64 + 4, 2);
        rejectedMetadata(work, changed, "BRIDGE_METADATA_SYMBOL_TABLE");
        changed = parameterModule(32); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putInt(64 + 4 * 64 + 4, 2);
        rejectedMetadata(work, changed, "BRIDGE_METADATA_SYMBOL_TABLE");
        changed = parameterModule(32); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putInt(64 + 4 * 64, 51);
        rejectedMetadata(work, changed, "BRIDGE_SECTIONS");
        String legacy = metadataManifest(work, kernel(), module(), 4064, null)
                .replace("\"credentialMode\":\"module-parameter\",\"metadataDisplacement\":4064", "\"credentialSha256\":\"" + "a".repeat(64) + "\"");
        Files.writeString(manifest, legacy);
        check(UltraInitBootProducer.readArtifact(manifest.toFile()).credentialMode.equals("embedded"),
                "embedded compatibility does not require runtime symbols or credential parmtype");
    }

    private static byte[] namespaceRecord(String name) {
        if (name.length() != 16) throw new IllegalArgumentException("fixture namespace must have16ASCII characters");
        byte[] record = new byte[24];
        ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN).putInt(0x4e44534d).putShort((short) 1).putShort((short) 24);
        System.arraycopy(ascii(name), 0, record, 8, 16);
        return record;
    }

    private static byte[] namespaceModule(String tags) {
        byte[] bridge = parameterModule(32), info = ascii("vermagic=6.6.1-fixture SMP\0parmtype=credential:string\0" + tags);
        Arrays.fill(bridge, 832, 1024, (byte) 0); System.arraycopy(info, 0, bridge, 832, info.length);
        ByteBuffer.wrap(bridge).order(ByteOrder.LITTLE_ENDIAN).putLong(64 + 5 * 64 + 32, info.length);
        return bridge;
    }

    private static Path ownedNamespaceFixture(Path work, Path source, Path valid, byte[] cpio) throws Exception {
        Path directory = Files.createTempDirectory(work, "owned-namespace-"), record = directory.resolve("record.bin"),
                ramdisk = directory.resolve("ramdisk.bin"), output = directory.resolve("candidate.img");
        check(BootImageRebuilder.extractRecoveryRecord(valid.toFile(), record.toFile(), BootImageRebuilder.OutputPolicy.STRICT),
                "malformed-entry fixture starts from actual producer recovery record");
        byte[] envelope = Files.readAllBytes(record);
        System.arraycopy(java.util.HexFormat.of().parseHex(KernelIdentity.sha256(cpio)), 0, envelope, 32, 32);
        System.arraycopy(java.util.HexFormat.of().parseHex(KernelIdentity.sha256(Arrays.copyOf(envelope, envelope.length - 32))),
                0, envelope, envelope.length - 32, 32);
        Files.write(record, envelope); Files.write(ramdisk, cpio);
        BootImageRebuilder.rebuildRamdiskWithRecoveryFile(source.toFile(), ramdisk.toFile(), output.toFile(), record.toFile(),
                BootImageRebuilder.OutputPolicy.STRICT);
        return output;
    }

    private static void namespace(Path work) throws Exception {
        Path manifest = work.resolve("artifact.json"), source = work.resolve("input.img"), output = work.resolve("candidate.img"),
                repeated = work.resolve("repeat.img"), upgraded = work.resolve("upgrade.img"), restored = work.resolve("restored.img");
        byte[] credential = ascii("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKL"), record = namespaceRecord("DeploySpace123ab"),
                expected = record.clone(), raw = kernel();
        byte[] bridge = join(namespaceModule("parmtype=deployment_namespace:string\0"), ascii("~Module signature appended~\n"));
        String json = metadataManifest(work, raw, bridge, 4064, null);
        var generic = UltraInitBootProducer.readArtifact(manifest.toFile());
        check(generic.supportsDeploymentNamespace, "namespace support derives from actual unique module parmtype");
        var credentialOnly = generic.withCredential(credential);
        byte[] cpio = join(entry("public-record-not-owned", 0100644, expected, true), stock(true, true));
        byte[] encoded = KernelCompression.encode(cpio, "GZIP"); encoded[4] = 0x67; encoded = Arrays.copyOf(encoded, encoded.length + 7);
        byte[] baseline = image(new byte[0], encoded); Files.write(source, baseline);
        check(UltraInitBootProducer.readDeploymentNamespace(source.toFile()) == null,
                "unowned image never scans canonical bytes or derives namespace from credential");
        check(UltraInitBootProducer.readDeploymentNamespace(work.resolve("target.img").toFile()) == null,
                "paired kernel boot has no init_boot namespace");
        fails("ULTRA_INIT_BOOT_DEPLOYMENT_NAMESPACE_REQUIRED", () -> UltraInitBootProducer.produce(source.toFile(), output.toFile(), credentialOnly));
        check(!Files.exists(output), "missing required namespace exposes no candidate");
        for (byte[] invalid : new byte[][]{null, new byte[23], new byte[25], new byte[24]})
            fails("ULTRA_INIT_BOOT_DEPLOYMENT_NAMESPACE_INVALID", () -> generic.withDeploymentNamespace(invalid));
        for (int offset : new int[]{0, 4, 5, 6, 7, 8, 23}) {
            byte[] invalid = expected.clone(); invalid[offset] = (byte) (offset >= 8 ? '_' : invalid[offset] ^ 0x40);
            fails("ULTRA_INIT_BOOT_DEPLOYMENT_NAMESPACE_INVALID", () -> generic.withDeploymentNamespace(invalid));
        }
        var bound = generic.withDeploymentNamespace(record).withCredential(credential); Arrays.fill(record, (byte) 0);
        fails("ULTRA_INIT_BOOT_DEPLOYMENT_NAMESPACE_REQUIRED", () -> UltraInitBootProducer.produce(source.toFile(), output.toFile(), credentialOnly));
        UltraInitBootProducer.produce(source.toFile(), output.toFile(), bound);
        byte[] actual = UltraInitBootProducer.readDeploymentNamespace(output.toFile());
        check(Arrays.equals(expected, actual), "record is defensively bound and read from verified owned CPIO");
        actual[8] = 'Z';
        check(Arrays.equals(expected, UltraInitBootProducer.readDeploymentNamespace(output.toFile())), "namespace getter returns independent bytes");
        var layout = BootImageInspector.inspect(output.toFile());
        byte[] produced = KernelCompression.decode(UltraInitBootProducer.component(output.toFile(), layout), "GZIP", 1024 * 1024);
        var entries = RamdiskInspector.inspectRamdisk(produced); var deployment = entries.entry(UltraInitRamdisk.DEPLOYMENT);
        check(deployment.mode == 0100600 && deployment.uid == 0 && deployment.gid == 0 && deployment.links == 1 && deployment.size == 24,
                "deployment is one root-owned 0600 regular exact24B canonical record");
        var beforeVendor = RamdiskInspector.inspectRamdisk(cpio).entry("vendor-file"); var afterVendor = entries.entry("vendor-file");
        check(Arrays.equals(cpio, beforeVendor.headerOffset, beforeVendor.endOffset, produced, afterVendor.headerOffset, afterVendor.endOffset),
                "namespace addition preserves non-target CPIO header, data, CRC and padding bytes");
        check(Arrays.equals(bridge, Arrays.copyOfRange(produced, (int) entries.entry(UltraInitRamdisk.BRIDGE).offset,
                (int) (entries.entry(UltraInitRamdisk.BRIDGE).offset + entries.entry(UltraInitRamdisk.BRIDGE).size))),
                "namespace production does not change module or signature bytes");
        UltraInitBootProducer.produce(output.toFile(), repeated.toFile(), generic.withCredential(credential).withDeploymentNamespace(expected));
        check(Arrays.equals(Files.readAllBytes(output), Files.readAllBytes(repeated)), "both binding orders preserve deterministic same-version namespace");
        Files.delete(repeated);
        byte[] different = namespaceRecord("OtherSpace0123ab");
        fails("ULTRA_INIT_BOOT_DEPLOYMENT_NAMESPACE_CONFLICT", () -> UltraInitBootProducer.produce(output.toFile(), repeated.toFile(),
                generic.withCredential(credential).withDeploymentNamespace(different)));
        check(!Files.exists(repeated), "upgrade cannot silently rotate retained public namespace");
        byte[] nextCredential = credential.clone(); nextCredential[0] = 'Z';
        UltraInitBootProducer.produce(output.toFile(), upgraded.toFile(), generic.withCredential(nextCredential)
                .withDeploymentNamespace(UltraInitBootProducer.readDeploymentNamespace(output.toFile())));
        check(Arrays.equals(expected, UltraInitBootProducer.readDeploymentNamespace(upgraded.toFile())), "upgrade preserves namespace independently of credential");
        UltraInitBootProducer.restore(upgraded.toFile(), restored.toFile());
        check(Arrays.equals(baseline, Files.readAllBytes(restored)), "namespace upgrade restores exact first encoded input"); Files.delete(restored);
        metadataManifest(work, raw, parameterModule(32), 4064, null);
        var legacy = UltraInitBootProducer.readArtifact(manifest.toFile());
        check(!legacy.supportsDeploymentNamespace, "old module without parmtype stays supported on old production path");
        fails("ULTRA_INIT_BOOT_DEPLOYMENT_NAMESPACE_UNSUPPORTED", () -> legacy.withDeploymentNamespace(expected));
        UltraInitBootProducer.produce(source.toFile(), repeated.toFile(), legacy.withCredential(credential));
        check(UltraInitBootProducer.readDeploymentNamespace(repeated.toFile()) == null, "old owned image without entry returns null");
        UltraInitBootProducer.produce(repeated.toFile(), upgraded.resolveSibling("first-namespace.img").toFile(), bound);
        check(Arrays.equals(expected, UltraInitBootProducer.readDeploymentNamespace(upgraded.resolveSibling("first-namespace.img").toFile())),
                "upgrade from old owned image binds explicit canonical namespace without changing original recovery");
        Files.delete(repeated);
        UltraInitBootProducer.produce(output.toFile(), repeated.toFile(), legacy.withCredential(credential));
        check(Arrays.equals(expected, UltraInitBootProducer.readDeploymentNamespace(repeated.toFile())),
                "old-package downgrade preserves actual namespace as inert public data, not parameter support");
        UltraInitBootProducer.restore(repeated.toFile(), restored.toFile());
        check(Arrays.equals(baseline, Files.readAllBytes(restored)), "old-package path retains original encoded restoration"); Files.delete(restored); Files.delete(repeated);
        Path corrupt = work.resolve("corrupt.img"); byte[] damaged = Files.readAllBytes(output);
        damaged[(int) layout.ramdiskOffset + 4] ^= 1; Files.write(corrupt, damaged);
        fails("ULTRA_INIT_BOOT_RECOVERY_CHECKSUM", () -> UltraInitBootProducer.readDeploymentNamespace(corrupt.toFile()));
        byte[] noNamespace = stock(false, false);
        Path ownedMissing = ownedNamespaceFixture(work, source, output, noNamespace);
        check(UltraInitBootProducer.readDeploymentNamespace(ownedMissing.toFile()) == null, "valid owned envelope without actual namespace returns null");
        byte[] validEntry = entry(UltraInitRamdisk.DEPLOYMENT, 0100600, expected, false);
        Path ownedDuplicate = ownedNamespaceFixture(work, source, output, join(validEntry, validEntry, noNamespace));
        fails("ULTRA_INIT_CPIO_DUPLICATE_ENTRY", () -> UltraInitBootProducer.readDeploymentNamespace(ownedDuplicate.toFile()));
        for (int headerField : new int[]{22, 30, 38}) {
            byte[] bad = validEntry.clone(); bad[headerField + 7] = headerField == 38 ? (byte) '2' : (byte) '1';
            Path invalid = ownedNamespaceFixture(work, source, output, join(bad, noNamespace));
            fails("ULTRA_INIT_CPIO_DEPLOYMENT_NAMESPACE_METADATA", () -> UltraInitBootProducer.readDeploymentNamespace(invalid.toFile()));
        }
        for (int mode : new int[]{0100644, 0120600}) {
            Path invalid = ownedNamespaceFixture(work, source, output, join(entry(UltraInitRamdisk.DEPLOYMENT, mode, expected, false), noNamespace));
            fails("ULTRA_INIT_CPIO_DEPLOYMENT_NAMESPACE_METADATA", () -> UltraInitBootProducer.readDeploymentNamespace(invalid.toFile()));
        }
        for (byte[] bad : new byte[][]{new byte[24], Arrays.copyOf(expected, 23), Arrays.copyOf(expected, 25)}) {
            Path invalid = ownedNamespaceFixture(work, source, output, join(entry(UltraInitRamdisk.DEPLOYMENT, 0100600, bad, false), noNamespace));
            fails(bad.length == 24 ? "ULTRA_INIT_BOOT_DEPLOYMENT_NAMESPACE_INVALID" : "ULTRA_INIT_CPIO_DEPLOYMENT_NAMESPACE_METADATA",
                    () -> UltraInitBootProducer.readDeploymentNamespace(invalid.toFile()));
        }
        Path noncanonical = ownedNamespaceFixture(work, source, output,
                join(entry("./" + UltraInitRamdisk.DEPLOYMENT, 0100600, expected, false), noNamespace));
        fails("ULTRA_INIT_CPIO_DEPLOYMENT_NAMESPACE_METADATA", () -> UltraInitBootProducer.readDeploymentNamespace(noncanonical.toFile()));
        Files.write(corrupt, image(new byte[0], join(validEntry, noNamespace)));
        fails("ULTRA_INIT_BOOT_RECOVERY_MISSING", () -> UltraInitBootProducer.readDeploymentNamespace(corrupt.toFile()));
        fails("ULTRA_INIT_CPIO_PATH_CONFLICT", () -> UltraInitRamdisk.install(join(validEntry, noNamespace), handoff(), bridge, null, credential, expected));
        IOException first = fails("NAMESPACE_FIRST", () -> UltraInitBootProducer.produce(source.toFile(), repeated.toFile(), bound,
                directory -> { throw new IOException("NAMESPACE_FIRST"); }));
        check(first.getClass() == IOException.class && !Files.exists(repeated), "namespace production keeps first error and compensates actual output");
        try {
            UltraInitBootProducer.produce(source.toFile(), repeated.toFile(), bound, directory -> Thread.currentThread().interrupt());
            throw new AssertionError("interrupted namespace publication accepted");
        } catch (IOException error) {
            check(error instanceof ClosedByInterruptException || "CANCELLED".equals(error.getMessage()), "namespace cancellation keeps actual first error");
            check(Thread.currentThread().isInterrupted(), "namespace cancellation marker preserved");
        } finally { Thread.interrupted(); }
        check(!Files.exists(repeated), "namespace cancellation compensates owned output");
        for (String tags : new String[]{"parmtype=deployment_namespace:charp\0", "parmtype=deployment_namespace\0",
                "parmtype=deployment_namespace:string\0parmtype=deployment_namespace:string\0"}) {
            metadataManifest(work, raw, namespaceModule(tags), 4064, null);
            fails("ULTRA_INIT_ARTIFACT_BRIDGE_DEPLOYMENT_NAMESPACE_PARAMETER_TYPE", () -> UltraInitBootProducer.readArtifact(manifest.toFile()));
        }
        metadataManifest(work, raw, join(parameterModule(32), ascii("parmtype=deployment_namespace:string\0")), 4064, null);
        check(!UltraInitBootProducer.readArtifact(manifest.toFile()).supportsDeploymentNamespace,
                "parmtype-shaped bytes outside actual modinfo do not advertise namespace support");
        rejectedManifest(manifest, json.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"supportsDeploymentNamespace\":\"true\""), "FIELDS");
        check(Arrays.equals(baseline, Files.readAllBytes(source)), "namespace scenarios leave source input unchanged");
        Arrays.fill(credential, (byte) 0); Arrays.fill(nextCredential, (byte) 0);
    }

    private static UltraInitBootProducer.Artifact repatchArtifact(Path work, int generation,
                                                                  byte[] credential, byte[] namespace) throws Exception {
        byte[] bridge = namespaceModule("parmtype=deployment_namespace:string\0");
        // Distinct AArch64 SDK-call and handoff bytes, parsed and copied but never executed.
        ByteBuffer.wrap(bridge).order(ByteOrder.LITTLE_ENDIAN).putInt(1028, 0xd2800000 | generation << 5);
        String json = metadataManifest(work, kernel(), bridge, 4064, null);
        byte[] nextHandoff = handoff();
        ByteBuffer.wrap(nextHandoff).order(ByteOrder.LITTLE_ENDIAN).putInt(128, 0xd2800000 | generation << 5);
        Files.write(work.resolve("handoff.bin"), nextHandoff);
        Files.writeString(work.resolve("artifact.json"), json.replace(KernelIdentity.sha256(handoff()), KernelIdentity.sha256(nextHandoff)));
        return UltraInitBootProducer.readArtifact(work.resolve("artifact.json").toFile())
                .withCredential(credential).withDeploymentNamespace(namespace);
    }

    private static void repatchPayload(Path image, UltraInitBootProducer.Artifact artifact,
                                       String originalInitHash, byte[] credential, byte[] namespace) throws Exception {
        var layout = BootImageInspector.inspect(image.toFile());
        byte[] cpio = KernelCompression.decode(UltraInitBootProducer.component(image.toFile(), layout), "GZIP", 1024 * 1024);
        var entries = RamdiskInspector.inspectRamdisk(cpio);
        check(entries.entry("init").sha256.equals(artifact.handoffSha256), "repatch installs selected handoff bytes");
        check(entries.entry(UltraInitRamdisk.BRIDGE).sha256.equals(artifact.bridgeSha256), "repatch installs selected bridge including changed SDK-call bytes");
        check(entries.entry(UltraInitRamdisk.ORIGINAL).sha256.equals(originalInitHash), "repatch saves original Android or third-party init, not the previous handoff");
        check(entries.entry(UltraInitRamdisk.CREDENTIAL).sha256.equals(KernelIdentity.sha256(credential)), "cross-artifact update preserves supplied credential identity");
        check(Arrays.equals(namespace, UltraInitBootProducer.readDeploymentNamespace(image.toFile())), "cross-artifact update preserves deployment namespace");
    }

    private static void crossArtifactRepatch(Path work) throws Exception {
        byte[] credential = ascii("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKL"), namespace = namespaceRecord("RepatchSpace12ab");
        var artifacts = new UltraInitBootProducer.Artifact[3];
        for (int index = 0; index < artifacts.length; index++) {
            artifacts[index] = repatchArtifact(Files.createDirectory(work.resolve("artifact-" + index)), index + 1, credential, namespace);
            if (index > 0) {
                check(!artifacts[index - 1].handoffSha256.equals(artifacts[index].handoffSha256) &&
                        !artifacts[index - 1].bridgeSha256.equals(artifacts[index].bridgeSha256), "new artifact has different handoff and bridge content identities");
                check(!Arrays.equals(artifacts[index - 1].bridge, 1024, 1056, artifacts[index].bridge, 1024, 1056),
                        "successive artifacts contain explicitly different SDK-call code");
            }
        }
        for (String kind : new String[]{"stock", "third-party", "ksu"}) {
            Path directory = Files.createDirectory(work.resolve(kind)), source = directory.resolve("input.img"),
                    restored = directory.resolve("restored.img"), failed = directory.resolve("failed.img");
            byte[] realInit = ascii("retained Android init"), cpio = kind.equals("stock") ? stock(true, true) : join(
                    entry("init", 0100755, ascii(kind.equals("ksu") ? "previous KSU shim" : "third-party init"), true),
                    kind.equals("ksu") ? join(entry("init.real", 0100750, realInit, true),
                            entry("kernelsu.ko", 0100600, ascii("preserved previous KSU module"), true)) : new byte[0],
                    entry("third-party-settings", 0100640, ascii("retain this complete CPIO entry"), true),
                    entry("TRAILER!!!", 0, new byte[0], true), new byte[13]);
            var originalEntries = RamdiskInspector.inspectRamdisk(cpio);
            String originalInitHash = originalEntries.entry(kind.equals("ksu") ? "init.real" : "init").sha256;
            byte[] encoded = KernelCompression.encode(cpio, "GZIP"); encoded[4] = 0x69; encoded = Arrays.copyOf(encoded, encoded.length + 9);
            byte[] baseline = image(new byte[0], encoded); Files.write(source, baseline);
            Path[] outputs = new Path[3]; byte[][] installed = new byte[3][];
            for (int index = 0; index < artifacts.length; index++) {
                Path input = index == 0 ? source : outputs[index - 1]; outputs[index] = directory.resolve("generation-" + index + ".img");
                var report = UltraInitBootProducer.produce(input.toFile(), outputs[index].toFile(), artifacts[index]);
                check(report.sourceSha256.equals(KernelIdentity.sha256(index == 0 ? baseline : installed[index - 1])), "report identifies the exact current input of each update");
                check(UltraInitBootProducer.recognizes(outputs[index].toFile()), "every generation has a verified owned recovery record");
                repatchPayload(outputs[index], artifacts[index], originalInitHash, credential, namespace);
                installed[index] = Files.readAllBytes(outputs[index]);
                check(index == 0 || !Arrays.equals(installed[index - 1], installed[index]), "changed artifact produces changed image bytes");
                UltraInitBootProducer.restore(outputs[index].toFile(), restored.toFile());
                check(Arrays.equals(baseline, Files.readAllBytes(restored)), "every successive artifact restores the first exact compressed input, header and padding");
                if (index < artifacts.length - 1) Files.delete(restored);
            }
            Path reinstalled = directory.resolve("reinstalled.img"), repeated = directory.resolve("repeated.img");
            UltraInitBootProducer.produce(restored.toFile(), reinstalled.toFile(), artifacts[2]);
            check(Arrays.equals(installed[2], Files.readAllBytes(reinstalled)), "restore then patch selects the same latest artifact without nested old records");
            UltraInitBootProducer.produce(outputs[2].toFile(), repeated.toFile(), artifacts[2]);
            check(Arrays.equals(installed[2], Files.readAllBytes(repeated)), "latest cross-artifact update remains byte-idempotent");
            IOException first = new IOException("CROSS_ARTIFACT_PUBLICATION_FIRST");
            IOException actual = fails(first.getMessage(), () -> UltraInitBootProducer.produce(outputs[1].toFile(), failed.toFile(), artifacts[2],
                    directoryAfterPublish -> { throw first; }));
            check(actual == first && !Files.exists(failed), "failed cross-artifact update preserves the actual first error and removes only its candidate");
            Path corrupt = directory.resolve("corrupt-owned.img"); byte[] corruptBytes = installed[1].clone();
            corruptBytes[(int) BootImageInspector.inspect(outputs[1].toFile()).ramdiskOffset + 4] ^= 1; Files.write(corrupt, corruptBytes);
            fails("ULTRA_INIT_BOOT_RECOVERY_CHECKSUM", () -> UltraInitBootProducer.produce(corrupt.toFile(), failed.toFile(), artifacts[2]));
            check(Arrays.equals(corruptBytes, Files.readAllBytes(corrupt)) && !Files.exists(failed), "corrupt owned input keeps its bytes and real recovery failure");
            for (int index = 0; index < outputs.length; index++)
                check(Arrays.equals(installed[index], Files.readAllBytes(outputs[index])), "successful and failed later updates preserve every prior input image");
            check(Arrays.equals(baseline, Files.readAllBytes(source)), "cross-artifact sequence leaves first input unchanged");
            if (!kind.equals("stock")) {
                byte[] latest = KernelCompression.decode(UltraInitBootProducer.component(outputs[2].toFile(), BootImageInspector.inspect(outputs[2].toFile())), "GZIP", 1024 * 1024);
                var retained = RamdiskInspector.inspectRamdisk(latest);
                check(retained.entry("third-party-settings").sha256.equals(originalEntries.entry("third-party-settings").sha256), "updates retain unrelated third-party content");
                if (kind.equals("ksu")) check(retained.entry("init.real").sha256.equals(KernelIdentity.sha256(realInit)) &&
                        retained.entry("kernelsu.ko").sha256.equals(originalEntries.entry("kernelsu.ko").sha256), "recognized KSU keeps init.real and the inert old module");
            }
        }
        Path conflict = work.resolve("unowned-conflict.img"), conflictOutput = work.resolve("conflict-output.img");
        byte[] conflictBytes = image(new byte[0], join(entry(UltraInitRamdisk.ORIGINAL, 0100750, ascii("unproven old original"), false), stock(false, false)));
        Files.write(conflict, conflictBytes);
        fails("ULTRA_INIT_BOOT_RECOVERY_MISSING", () -> UltraInitBootProducer.produce(conflict.toFile(), conflictOutput.toFile(), artifacts[2]));
        check(Arrays.equals(conflictBytes, Files.readAllBytes(conflict)) && !Files.exists(conflictOutput), "unproven third-party owned paths preserve original input and actual conflict");
        for (var artifact : artifacts) {
            Path directory = artifact.targetBoot.toPath().getParent();
            check(BootImageInspector.inspect(artifact.targetBoot).sha256.equals(artifact.targetBootSha256) &&
                    KernelIdentity.sha256(Files.readAllBytes(directory.resolve("handoff.bin"))).equals(artifact.handoffSha256) &&
                    KernelIdentity.sha256(Files.readAllBytes(directory.resolve("bridge.ko"))).equals(artifact.bridgeSha256), "all artifact generations and paired boot inputs remain byte-identical");
        }
        Arrays.fill(credential, (byte) 0);
    }

    private static java.util.Map<String, byte[]> kmiBundle() throws Exception {
        return kmiBundle("6.6.1-android15-1-fixture");
    }

    private static java.util.Map<String, byte[]> kmiBundle(String release) throws Exception {
        byte[] bridge = parameterModule(32);
        byte[] info = ascii("vermagic=" + release + " SMP\0parmtype=credential:string\0" +
                "parmtype=deployment_namespace:string\0maplesu_runtime_facts=1\0");
        int infoAt = bridge.length;
        bridge = Arrays.copyOf(bridge, infoAt + info.length);
        System.arraycopy(info, 0, bridge, infoAt, info.length);
        ByteBuffer.wrap(bridge).order(ByteOrder.LITTLE_ENDIAN).putLong(64 + 5 * 64 + 24, infoAt)
                .putLong(64 + 5 * 64 + 32, info.length);
        String json = "{\"schemaVersion\":2,\"kind\":\"kmi-component\",\"kmi\":\"android15-6.6\"," +
                "\"architecture\":\"arm64\",\"pageSize\":4096,\"cfiMode\":\"kcfi\"," +
                "\"kernelRelease\":\"" + release + "\",\"credentialMode\":\"module-parameter\"," +
                "\"metadataDisplacement\":4064,\"runtimeFactsAbi\":\"maplesu-runtime-facts/1\"," +
                "\"sourceGenerationSha256\":\"" + "a".repeat(64) + "\",\"handoff\":\"handoff.bin\"," +
                "\"handoffSha256\":\"" + KernelIdentity.sha256(handoff()) + "\",\"bridge\":\"bridge.ko\"," +
                "\"bridgeSha256\":\"" + KernelIdentity.sha256(bridge) + "\"}";
        return java.util.Map.of("bridge.ko", bridge, "handoff.bin", handoff(), "manifest.json", ascii(json));
    }

    private static void kmiComponent(Path work) throws Exception {
        var files = kmiBundle();
        for (var file : files.entrySet()) Files.write(work.resolve(file.getKey()), file.getValue());
        byte[] bridge = files.get("bridge.ko");
        int marker = new String(bridge, StandardCharsets.ISO_8859_1).indexOf("maplesu_runtime_facts=1");
        String json = new String(files.get("manifest.json"), StandardCharsets.US_ASCII);
        Path manifest = work.resolve("manifest.json");
        var component = UltraInitBootProducer.readArtifact(manifest.toFile());
        check(component.isKmiComponent && component.componentKmi.equals("android15-6.6") &&
                component.component.pageSize == 4096 && component.component.cfiMode.equals("kcfi"), "component exposes its build ABI separately");
        check(component.targetBoot == null && component.targetBootSha256.isEmpty() && component.kernelSha256.isEmpty() &&
                component.targetInputSha256.isEmpty(), "KMI component carries no invented target input or proof");
        byte[] baseline = image(new byte[0], KernelCompression.encode(stock(true, false), "GZIP"));
        Path source = work.resolve("init_boot.img"), output = work.resolve("patched.img"),
                repeated = work.resolve("repeated.img"), restored = work.resolve("restored.img");
        Files.write(source, baseline);
        var bound = component.withCredential(ascii("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKL"))
                .withDeploymentNamespace(namespaceRecord("DeploySpace123ab"));
        var produced = UltraInitBootProducer.produce(source.toFile(), output.toFile(), bound);
        check(UltraInitBootProducer.recognizes(output.toFile()), "single-file component uses the real owned recovery envelope");
        Path record = work.resolve("record.bin");
        check(BootImageRebuilder.extractRecoveryRecord(output.toFile(), record.toFile(), BootImageRebuilder.OutputPolicy.STRICT) &&
                produced.recoveryRecordBytes == Files.size(record) && produced.originalRamdiskBytes == BootImageInspector.inspect(source.toFile()).ramdiskSize &&
                produced.installedRamdiskBytes == BootImageInspector.inspect(output.toFile()).ramdiskSize, "reported capacity uses exact published bytes");
        UltraInitBootProducer.produce(output.toFile(), repeated.toFile(), bound);
        check(Arrays.equals(Files.readAllBytes(output), Files.readAllBytes(repeated)), "single-file KMI repatch is byte idempotent");
        UltraInitBootProducer.restore(output.toFile(), restored.toFile());
        check(Arrays.equals(baseline, Files.readAllBytes(restored)), "single-file KMI restores the exact input");
        rejectedManifest(manifest, json.replace("android15-6.6", "not-a-kmi"), "KMI");
        rejectedManifest(manifest, json.replace("\"kcfi\"", "\"guess\""), "CFI_MODE");
        rejectedManifest(manifest, json.replace("\"pageSize\":4096", "\"pageSize\":8192"), "PAGE_SIZE");
        rejectedManifest(manifest, json.replace("\"kind\"", "\"targetBoot\":\"absent.img\",\"kind\""), "COMPONENT_FIELDS");
        byte[] noRuntime = bridge.clone(); noRuntime[marker + "maplesu_runtime_facts=".length()] = '0';
        Files.write(work.resolve("bridge.ko"), noRuntime);
        rejectedManifest(manifest, json.replace(KernelIdentity.sha256(bridge), KernelIdentity.sha256(noRuntime)), "BRIDGE_RUNTIME_FACTS");
        check(Arrays.equals(baseline, Files.readAllBytes(source)), "all component success and failure paths preserve the source");
        Path untagged = Files.createDirectory(work.resolve("untagged-release"));
        for (var file : kmiBundle("6.6.127-4k-g46a034eca005-dirty").entrySet())
            Files.write(untagged.resolve(file.getKey()), file.getValue());
        var ddkComponent = UltraInitBootProducer.readArtifact(untagged.resolve("manifest.json").toFile());
        check(ddkComponent.isKmiComponent && ddkComponent.componentKmi.equals("android15-6.6") &&
                ddkComponent.kernelRelease.equals("6.6.127-4k-g46a034eca005-dirty"),
                "actual DDK release need not carry an Android tag");
        kmiCapacity(Files.createDirectory(work.resolve("capacity")), files);
    }

    private static void kmiCapacity(Path work, java.util.Map<String, byte[]> files) throws Exception {
        byte[] bridge = Arrays.copyOf(files.get("bridge.ko"), 4 * 1024 * 1024);
        byte[] noise = new byte[2 * 1024 * 1024];
        new java.util.Random(1729).nextBytes(noise);
        System.arraycopy(noise, 0, bridge, files.get("bridge.ko").length, noise.length);
        System.arraycopy(noise, 0, bridge, bridge.length - noise.length, noise.length);
        String json = new String(files.get("manifest.json"), StandardCharsets.US_ASCII)
                .replace(KernelIdentity.sha256(files.get("bridge.ko")), KernelIdentity.sha256(bridge));
        Files.write(work.resolve("bridge.ko"), bridge); Files.write(work.resolve("handoff.bin"), files.get("handoff.bin"));
        Files.writeString(work.resolve("manifest.json"), json);
        byte[] original = KernelCompression.encode(join(entry("bulk", 0100644, noise, false), stock(false, false)), "GZIP");
        byte[] body = image(new byte[0], original), baseline = Arrays.copyOf(body, 8 * 1024 * 1024);
        ByteBuffer.wrap(baseline).put(body.length, ascii("AVB0"));
        int footerAt = baseline.length - 64;
        ByteBuffer footer = ByteBuffer.wrap(baseline).order(ByteOrder.BIG_ENDIAN);
        footer.put(footerAt, ascii("AVBf")).putInt(footerAt + 4, 1).putLong(footerAt + 12, body.length)
                .putLong(footerAt + 20, body.length).putLong(footerAt + 28, 256);
        Path source = work.resolve("init_boot.img"), output = work.resolve("patched.img"), snapshot = work.resolve("snapshot.bin");
        Files.write(source, baseline);
        var artifact = UltraInitBootProducer.readArtifact(work.resolve("manifest.json").toFile())
                .withCredential(ascii("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKL"))
                .withDeploymentNamespace(namespaceRecord("DeploySpace123ab"));
        byte[] cpio = KernelCompression.decode(original, "GZIP", 8 * 1024 * 1024);
        byte[] installed = KernelCompression.encode(UltraInitRamdisk.install(cpio, handoff(), bridge, null,
                ascii("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKL"), namespaceRecord("DeploySpace123ab")), "GZIP");
        BootImageRecovery.capture(source.toFile(), BootImageRecovery.Component.RAMDISK, snapshot.toFile(), BootImageRebuilder.OutputPolicy.STRICT);
        long recordBytes = 64 + Files.size(snapshot) + original.length + 32;
        check(4096L + align(installed.length) + 4096 < baseline.length, "component alone fits the eight MiB container");
        check(4096L + align(installed.length) + align(Math.toIntExact(56 + recordBytes)) + 4096 > baseline.length,
                "complete first-input recovery makes the actual eight MiB layout overflow");
        fails("AVB_PARTITION_SIZE_LIMIT", () -> UltraInitBootProducer.produce(source.toFile(), output.toFile(), artifact));
        check(!Files.exists(output) && Arrays.equals(baseline, Files.readAllBytes(source)), "capacity failure keeps exact recovery protection and preserves input");
        System.out.println("8MiB capacity fixture: originalRamdisk=" + original.length + ", installedRamdisk=" + installed.length +
                ", recoveryRecord=" + recordBytes + ", container=8388608; rejected with complete recovery retained");
    }

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("maplesu-init-producer-fixture-");
        try {
            if (args.length == 1 && args[0].equals("kmi-component")) {
                kmiComponent(work);
                System.out.println("PASS " + checks + " single-file KMI component checks; no module/device execution");
                return;
            }
            if (args.length == 1 && args[0].equals("artifact-sidecars")) {
                artifactSidecars(work);
                System.out.println("PASS " + checks + " bounded artifact sidecar checks; no ABI/module/device execution");
                return;
            }
            if (args.length == 1 && args[0].equals("kmi-vermagic")) {
                kmiVermagic(work);
                System.out.println("PASS " + checks + " KMI target vermagic checks; byte fixtures only, no module execution");
                return;
            }
            if (args.length == 1 && args[0].equals("cross-artifact-repatch")) {
                crossArtifactRepatch(work);
                System.out.println("PASS " + checks + " cross-artifact init_boot repatch checks; real producer and recovery, no boot/bridge/SDK execution");
                return;
            }
            if (args.length == 1 && args[0].equals("namespace")) {
                namespace(work);
                System.out.println("PASS " + checks + " deployment namespace checks; no boot/bridge/SDK execution");
                return;
            }
            if (args.length == 1 && args[0].equals("artifact-metadata")) {
                artifactMetadata(work);
                System.out.println("PASS " + checks + " artifact metadata checks; no boot/bridge/SDK execution");
                return;
            }
            if (args.length == 1 && args[0].equals("runtime-credential")) {
                runtimeCredential(work);
                System.out.println("PASS " + checks + " runtime credential container checks; no boot/bridge/SDK execution");
                return;
            }
            kmiComponent(Files.createDirectory(work.resolve("kmi-component")));
            artifactSidecars(Files.createDirectory(work.resolve("artifact-sidecars")));
            runtimeCredential(Files.createDirectory(work.resolve("runtime-credential")));
            artifactMetadata(Files.createDirectory(work.resolve("artifact-metadata")));
            namespace(Files.createDirectory(work.resolve("namespace")));
            crossArtifactRepatch(Files.createDirectory(work.resolve("cross-artifact-repatch")));
            Path target = work.resolve("target.img"), source = work.resolve("input.img"),
                    output = work.resolve("candidate.img"), repeated = work.resolve("repeat.img"), restored = work.resolve("restored.img");
            byte[] raw = kernel(), targetBytes = image(raw, new byte[0]); Files.write(target, targetBytes);
            // Package-private constructor deliberately supplies synthetic opaque module bytes;
            // readArtifact has independent module checks and production callers use that method.
            UltraInitBootProducer.Artifact artifact = new UltraInitBootProducer.Artifact(target.toFile(),
                    KernelIdentity.sha256(targetBytes), KernelIdentity.releaseOf(raw), KernelIdentity.sha256(raw),
                    KernelIdentity.sha256(ascii("fixture-credential")), handoff(), ascii("opaque module fixture"));
            for (byte[] emptyKernel : new byte[][]{new byte[0], raw}) {
                byte[] emptyRamdiskImage = image(emptyKernel, new byte[0]); Files.write(source, emptyRamdiskImage);
                fails("ULTRA_INIT_BOOT_CONTAINER", () -> UltraInitBootProducer.produce(source.toFile(), output.toFile(), artifact));
                fails("ULTRA_INIT_BOOT_RAMDISK_SIZE", () -> UltraInitBootProducer.component(source.toFile(),
                        BootImageInspector.inspect(source.toFile())));
                check(!Files.exists(output) && Arrays.equals(emptyRamdiskImage, Files.readAllBytes(source)),
                        "Init injection keeps the nonempty ramdisk guard for boot and init_boot");
            }
            manifest(work, artifact);
            for (boolean crc : new boolean[]{false, true}) for (boolean symlink : new boolean[]{false, true}) {
                byte[] cpio = stock(crc, symlink), edited = UltraInitRamdisk.install(cpio, handoff(), artifact.bridge);
                var original = RamdiskInspector.inspectRamdisk(cpio);
                var actual = RamdiskInspector.inspectRamdisk(edited);
                var oldInit = original.entry("init"); var savedInit = actual.entry(UltraInitRamdisk.ORIGINAL);
                check(savedInit.mode == oldInit.mode && savedInit.sha256.equals(oldInit.sha256), "init type/data/mode retained");
                check(actual.entry("unrelated-marker").sha256.equals(original.entry("unrelated-marker").sha256), "inert marker preserved");
                if (crc || symlink) continue;
                for (String format : new String[]{"RAW", "GZIP", "LZ4_LEGACY", "LZ4_FRAME", "XZ", "ZSTD"}) {
                    byte[] encoded = format.equals("RAW") ? cpio : KernelCompression.encode(cpio, format);
                    if (format.equals("GZIP")) {
                        encoded[4] = 0x37; encoded[5] = 0x12; // irreproducible original mtime retained by recovery
                        encoded = Arrays.copyOf(encoded, encoded.length + 11);
                    }
                    byte[] baseline = image(new byte[0], encoded); Files.write(source, baseline);
                    check(!UltraInitBootProducer.recognizes(source.toFile()), "stock is unowned");
                    var report = UltraInitBootProducer.produce(source.toFile(), output.toFile(), artifact);
                    check(!report.flashableCandidate && !report.kernelServiceVerified, "container qualification only");
                    check(UltraInitBootProducer.recognizes(output.toFile()), "produced record recognized");
                    UltraInitBootProducer.produce(output.toFile(), repeated.toFile(), artifact);
                    check(Arrays.equals(Files.readAllBytes(output), Files.readAllBytes(repeated)), "idempotent without local receipt");
                    UltraInitBootProducer.restore(output.toFile(), restored.toFile());
                    check(Arrays.equals(baseline, Files.readAllBytes(restored)), "exact encoded stream/header/pad recovery");
                    check(Arrays.equals(baseline, Files.readAllBytes(source)), "input unchanged");
                    check(Arrays.equals(targetBytes, Files.readAllBytes(target)), "external boot unchanged");
                    Files.delete(repeated); Files.delete(restored);
                    if (format.equals("RAW")) {
                        IOException first = fails("FIRST_ERROR", () -> UltraInitBootProducer.restore(output.toFile(), restored.toFile(), directory -> {
                            Files.move(directory, directory.resolveSibling(directory.getFileName() + "-moved"));
                            throw new IOException("FIRST_ERROR");
                        }));
                        check(first.getSuppressed().length > 0, "cleanup error suppressed on first error");
                        check(!Files.exists(restored), "primary plus cleanup failure compensates owned restore");
                    }
                    Files.delete(output);
                }
            }
            Files.write(source, image(new byte[0], stock(false, false)));
            byte[] oldShim = ascii("trusted synthetic KSU shim"), androidInit = ascii("true synthetic Android init");
            byte[] compatible = join(entry("init", 0100755, oldShim, false),
                    entry("init.real", 0100750, androidInit, false),
                    entry("kernelsu.ko", 0100600, ascii("old module remains inert"), false),
                    entry("TRAILER!!!", 0, new byte[0], false));
            byte[] directMigration = UltraInitRamdisk.install(compatible, handoff(), artifact.bridge);
            check(RamdiskInspector.inspectRamdisk(directMigration).entry(UltraInitRamdisk.ORIGINAL).sha256.equals(
                    KernelIdentity.sha256(androidInit)), "KSU structural KeepInitReal needs no external shim or backup ticket");
            fails("ULTRA_INIT_CPIO_KSU_INIT_PROVENANCE_MISMATCH", () -> UltraInitRamdisk.install(
                    compatible, handoff(), artifact.bridge, KernelIdentity.sha256(ascii("another shim"))));
            fails("ULTRA_INIT_CPIO_KSU_LAYOUT_CONFLICT", () -> UltraInitRamdisk.install(join(
                    entry("init.real", 0100755, androidInit, false), stock(false, false)), handoff(), artifact.bridge));
            byte[] migrated = UltraInitRamdisk.install(compatible, handoff(), artifact.bridge, KernelIdentity.sha256(oldShim));
            var migration = RamdiskInspector.inspectRamdisk(migrated);
            check(migration.entry("init").sha256.equals(KernelIdentity.sha256(handoff())), "first hop is Ultra handoff");
            check(migration.entry(UltraInitRamdisk.ORIGINAL).sha256.equals(KernelIdentity.sha256(androidInit)) &&
                    migration.entry(UltraInitRamdisk.ORIGINAL).mode == 0100750, "handoff target is preserved Android init, not old shim");
            check(migration.entries.stream().noneMatch(item -> item.sha256.equals(KernelIdentity.sha256(oldShim))), "old shim has no active CPIO entry");
            check(migration.has("kernelsu.ko") && migration.has("init.real"), "old module/original stay inert");
            byte[] looped = join(entry("init", 0100755, oldShim, false),
                    entry("init.real", 0120777, ascii("system/../init"), false),
                    entry("kernelsu.ko", 0100600, ascii("module"), false),
                    entry("TRAILER!!!", 0, new byte[0], false));
            fails("ULTRA_INIT_CPIO_INIT_SYMLINK_LOOP", () -> UltraInitRamdisk.install(looped, handoff(), artifact.bridge));
            var ksuArtifact = new UltraInitBootProducer.Artifact(artifact.targetBoot, artifact.targetBootSha256,
                    artifact.kernelRelease, artifact.kernelSha256, artifact.credentialSha256,
                    artifact.handoff, artifact.bridge, KernelIdentity.sha256(oldShim));
            byte[] ksuInput = image(new byte[0], KernelCompression.encode(compatible, "GZIP")); Files.write(source, ksuInput);
            UltraInitBootProducer.produce(source.toFile(), output.toFile(), ksuArtifact);
            UltraInitBootProducer.restore(output.toFile(), restored.toFile());
            check(Arrays.equals(ksuInput, Files.readAllBytes(restored)), "migration restore returns exact KSU input including old shim");
            Files.delete(output); Files.delete(restored);
            Files.write(source, image(new byte[0], stock(false, false)));
            IOException captureFailure = new IOException("IDENTITY_CAPTURE_FAILED");
            IOException captured = fails("IDENTITY_CAPTURE_FAILED", () -> UltraInitBootProducer.produce(
                    source.toFile(), output.toFile(), artifact, new UltraInitBootProducer.Checkpoint() {
                        @Override public BootImageRebuilder.OutputIdentity capture(Path staged, Path anchor) throws IOException {
                            check(Files.isRegularFile(staged) && !Files.exists(output), "capture only sees private staged candidate");
                            throw captureFailure;
                        }
                        @Override public void afterPublish(Path directory) { throw new AssertionError("capture failure published"); }
                    }));
            check(captured == captureFailure && !Files.exists(output), "capture first error retained and no empty output exposed");
            byte[] captureOther = ascii("other writer during failed capture");
            captured = fails("IDENTITY_CAPTURE_FAILED", () -> UltraInitBootProducer.produce(
                    source.toFile(), output.toFile(), artifact, new UltraInitBootProducer.Checkpoint() {
                        @Override public BootImageRebuilder.OutputIdentity capture(Path staged, Path anchor) throws IOException {
                            Files.write(output, captureOther, java.nio.file.StandardOpenOption.CREATE_NEW);
                            throw captureFailure;
                        }
                        @Override public void afterPublish(Path directory) { throw new AssertionError("capture failure published"); }
                    }));
            check(captured == captureFailure && Arrays.equals(captureOther, Files.readAllBytes(output)),
                    "capture failure preserves another writer's output");
            Files.delete(output);
            Files.write(output, captureOther);
            try {
                UltraInitBootProducer.produce(source.toFile(), output.toFile(), artifact);
                throw new AssertionError("publication replaced an existing output");
            } catch (java.nio.file.FileAlreadyExistsException expected) {
                check(Arrays.equals(captureOther, Files.readAllBytes(output)), "non-replacing move and identity compensation preserve existing output");
            }
            Files.delete(output);
            UltraInitBootProducer.produce(source.toFile(), output.toFile(), artifact);
            captured = fails("IDENTITY_CAPTURE_FAILED", () -> UltraInitBootProducer.restore(output.toFile(), restored.toFile(),
                    new UltraInitBootProducer.Checkpoint() {
                        @Override public BootImageRebuilder.OutputIdentity capture(Path staged, Path anchor) throws IOException {
                            check(Files.isRegularFile(staged) && !Files.exists(restored), "restore captures identity before output creation");
                            throw captureFailure;
                        }
                        @Override public void afterPublish(Path directory) { throw new AssertionError("capture failure published"); }
                    }));
            check(captured == captureFailure && !Files.exists(restored), "restore retains capture first error and exposes no output");
            Files.delete(output);
            fails("AFTER_PUBLICATION", () -> UltraInitBootProducer.produce(source.toFile(), output.toFile(), artifact,
                    directory -> { throw new IOException("AFTER_PUBLICATION"); }));
            check(!Files.exists(output), "post-publication failure removes owned candidate");
            try {
                UltraInitBootProducer.produce(source.toFile(), output.toFile(), artifact,
                        directory -> Files.move(directory, directory.resolveSibling(directory.getFileName() + "-moved")));
                throw new AssertionError("cleanup failure accepted");
            } catch (java.nio.file.NoSuchFileException expected) { check(!Files.exists(output), "cleanup-only failure compensates candidate"); }
            byte[] other = ascii("other writer's replacement");
            fails("OTHER_WRITER", () -> UltraInitBootProducer.produce(source.toFile(), output.toFile(), artifact, directory -> {
                Files.move(output, work.resolve("displaced.img")); Files.write(output, other);
                throw new IOException("OTHER_WRITER");
            }));
            check(Arrays.equals(other, Files.readAllBytes(output)), "foreign output identity retained"); Files.delete(output);
            try {
                UltraInitBootProducer.produce(source.toFile(), output.toFile(), artifact,
                        directory -> Thread.currentThread().interrupt());
                throw new AssertionError("interrupted publication accepted");
            } catch (IOException error) {
                check(error instanceof ClosedByInterruptException || "CANCELLED".equals(error.getMessage()),
                        "first cancellation error retained: " + error);
                check(Thread.currentThread().isInterrupted(), "cancellation marker preserved");
            } finally { Thread.interrupted(); }
            check(!Files.exists(output), "canceled publication compensated");
            fails("ULTRA_INIT_CPIO_PATH_CONFLICT", () -> UltraInitRamdisk.install(
                    join(entry("./.maplesu_ultra/bridge.ko", 0100600, other, false), stock(false, false)), handoff(), other));
            fails("ULTRA_INIT_BOOT_ARTIFACT_REQUIRED", () -> UltraInitBootProducer.produce(source.toFile(), output.toFile(), null));
            Path manifest = work.resolve("manifest.json"); Files.writeString(manifest, "{\"schemaVersion\":1,\"schemaVersion\":1}");
            fails("ULTRA_INIT_ARTIFACT_FIELDS", () -> UltraInitBootProducer.readArtifact(manifest.toFile()));
            check(!Files.exists(output), "rejections expose no output");
            System.out.println("PASS " + checks + " synthetic init_boot container checks; no boot/bridge/SDK execution");
        } finally {
            Thread.interrupted();
            try (var files = Files.walk(work)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }
}
