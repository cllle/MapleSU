package dev.maplesu.image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;

/** Independent container fixtures only; no loader, bridge, signing or device claim. */
public final class RamdiskComponentWriterTest {
    private static int passed;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        passed++;
    }

    private static int align(int value, int page) { return (value + page - 1) / page * page; }

    private static byte[] content(int size, int seed) {
        byte[] value = new byte[size];
        for (int index = 0; index < size; index++) value[index] = (byte) (seed + index * 37);
        return value;
    }

    private static byte[][] components(int version, int kernel, int ramdisk, boolean extras) {
        return new byte[][]{content(kernel, 31), content(ramdisk, 71),
                content(version < 3 && extras ? 39 : 0, 91),
                content((version == 1 || version == 2) && extras ? 17 : 0, 111),
                content(version == 2 && extras ? 7 : 0, 131)};
    }

    // Kept separate from the production inspector/writer and its SHA-1 implementation.
    private static byte[] fixture(int version, int page, byte[][] parts) throws Exception {
        int count = version < 3 ? version + 3 : 2;
        int[] offsets = new int[count];
        int length = page;
        for (int index = 0; index < count; index++) {
            offsets[index] = length;
            length += align(parts[index].length, page);
        }
        byte[] bytes = new byte[length];
        ByteBuffer fields = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        fields.put("ANDROID!".getBytes(StandardCharsets.US_ASCII));
        fields.putInt(8, parts[0].length).putInt(40, version);
        if (version >= 3) {
            fields.putInt(12, parts[1].length).putInt(16, 0x12345678)
                    .putInt(20, version == 3 ? 1580 : 1584);
            byte[] command = "console=ttyS0 keep_original=1".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(command, 0, bytes, 44, command.length);
        } else {
            fields.putInt(12, 0x80208000).putInt(16, parts[1].length).putInt(20, 0x82000000)
                    .putInt(24, parts[2].length).putInt(28, 0x83000000).putInt(32, 0x80000100)
                    .putInt(36, page).putInt(44, 0x12345678);
            bytes[50] = 'x'; bytes[70] = 'y'; bytes[1000] = 'z'; bytes[page - 1] = 0x65;
            if (version > 0) fields.putInt(1632, parts[3].length)
                    .putLong(1636, parts[3].length == 0 ? 0 : offsets[3])
                    .putInt(1644, version == 1 ? 1648 : 1660);
            if (version == 2) fields.putInt(1648, parts[4].length).putLong(1652, 0x84000000L);
            MessageDigest id = MessageDigest.getInstance("SHA-1");
            for (int index = 0; index < count; index++) {
                id.update(parts[index]);
                int size = parts[index].length;
                id.update(new byte[]{(byte) size, (byte) (size >>> 8), (byte) (size >>> 16), (byte) (size >>> 24)});
            }
            System.arraycopy(id.digest(), 0, bytes, 576, 20);
        }
        for (int index = 0; index < count; index++)
            System.arraycopy(parts[index], 0, bytes, offsets[index], parts[index].length);
        return bytes;
    }

    private static BootImageRebuilder.Report rebuild(Path source, Path ramdisk, Path output) throws Exception {
        try {
            return (BootImageRebuilder.Report) BootImageRebuilder.class
                    .getMethod("rebuildRamdisk", java.io.File.class, java.io.File.class, java.io.File.class)
                    .invoke(null, source.toFile(), ramdisk.toFile(), output.toFile());
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            throw error;
        }
    }

    private static int cli(String command, Path source, Path ramdisk, Path output) throws Exception {
        PrintStream stdout = System.out, stderr = System.err;
        ByteArrayOutputStream text = new ByteArrayOutputStream(), errors = new ByteArrayOutputStream();
        int code;
        try (PrintStream sink = new PrintStream(text, true, StandardCharsets.UTF_8);
             PrintStream errorSink = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            System.setOut(sink); System.setErr(errorSink);
            try { code = ImageTool.run(new String[]{command, source.toString(), ramdisk.toString(), output.toString()}); }
            finally { System.setOut(stdout); System.setErr(stderr); }
        }
        if (code == 0) {
            check(errors.size() == 0, "CLI success has no error output");
            check(text.toString(StandardCharsets.UTF_8).contains("\"operation\":\"REBUILD_RAMDISK_V"),
                    "CLI reports ramdisk operation");
        }
        return code;
    }

    private static void one(Path source, Path ramdisk, Path output, int version, int page,
                            byte[][] parts, byte[] replacement) throws Exception {
        byte[] original = fixture(version, page, parts);
        Files.write(source, original); Files.write(ramdisk, replacement);
        BootImageRebuilder.Report report = rebuild(source, ramdisk, output);
        byte[][] expectedParts = parts.clone(); expectedParts[1] = replacement;
        byte[] expected = fixture(version, page, expectedParts), actual = Files.readAllBytes(output);
        check(Arrays.equals(expected, actual), "Independent v" + version + " page " + page + " output");
        check(report.image.headerVersion == version && report.image.pageSize == page, "Container shape retained");
        check(report.image.kernelSize == parts[0].length && !report.kernelChanged, "Kernel identity retained");
        check(report.image.ramdiskSize == replacement.length, "Ramdisk size replaced");
        check(report.ramdiskChanged == !Arrays.equals(parts[1], replacement), "Ramdisk changed flag");
        check(report.replacementKernelSha256 == null && report.replacementRamdiskSha256 != null,
                "Replacement report identifies ramdisk");
        check(report.equivalence.ok(), "Declared difference independently verified");
        check(Arrays.equals(original, Files.readAllBytes(source)), "Source unchanged");
        check(Arrays.equals(replacement, Files.readAllBytes(ramdisk)), "Replacement unchanged");
        if (parts[0].length != 0) {
            byte[] sabotage = actual.clone(); sabotage[page] ^= 0x5a; Files.write(output, sabotage);
            check(!BootImageEquivalence.verify(source.toFile(), output.toFile(), report.journal).ok(),
                    "Relocation declaration excludes original kernel");
            Files.write(output, actual);
        }
        if (version < 3) {
            byte[] sabotage = actual.clone(); sabotage[12] ^= 1; Files.write(output, sabotage);
            report.journal.add(BootImageEquivalence.HEADER_FIELD, 12, 4);
            check(!BootImageEquivalence.verify(source.toFile(), output.toFile(), report.journal).ok(),
                    "Legacy kernel address remains forbidden even when declared");
        }
        Files.delete(output);
        check(cli("rebuild-ramdisk", source, ramdisk, output) == 0, "Real generic CLI accepts v" + version);
        check(Arrays.equals(expected, Files.readAllBytes(output)), "CLI and API share exact writer");
        Files.delete(output);
    }

    private static byte[] avb(byte[] payload, int capacity) {
        byte[] bytes = Arrays.copyOf(payload, capacity);
        int at = align(payload.length, 4096);
        bytes[at] = 'A'; bytes[at + 1] = 'V'; bytes[at + 2] = 'B'; bytes[at + 3] = '0';
        ByteBuffer footer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int end = capacity - 64;
        footer.position(end); footer.put(new byte[]{'A', 'V', 'B', 'f'});
        footer.putInt(end + 4, 1).putInt(end + 8, 0).putLong(end + 12, payload.length)
                .putLong(end + 20, at).putLong(end + 28, 256);
        bytes[end + 40] = 0x7a;
        return bytes;
    }

    private static void avbCycles(Path source, Path ramdisk, Path output) throws Exception {
        for (int version = 0; version <= 4; version++) {
          for (int page : new int[]{2048, 4096, 8192, 16384}) {
            if (version >= 3 && page != 4096) continue;
            byte[][] parts = components(version, 71, 53, true);
            byte[] original = avb(fixture(version, page, parts), 131072);
            Files.write(source, original);
            for (byte[] value : new byte[][]{content(16385, 9), content(16385, 9), new byte[0], new byte[0], content(1, 7), parts[1]}) {
                byte[] before = Files.readAllBytes(source);
                Files.write(ramdisk, value);
                BootImageRebuilder.Report report = rebuild(source, ramdisk, output);
                byte[][] expectedParts = parts.clone(); expectedParts[1] = value;
                byte[] expected = avb(fixture(version, page, expectedParts), original.length);
                check(Arrays.equals(expected, Files.readAllBytes(output)), "AVB grow/repeat/empty/no-op/shrink/restore v" + version);
                check(report.avbFooterPresent && report.image.fileSize == original.length, "Partition capacity preserved");
                check(report.equivalence.ok() && !report.kernelChanged, "AVB kernel and diff verified");
                check(report.toJson().contains("\"originalSignaturesReverified\":false"), "No old authentication claim");
                check(Arrays.equals(before, Files.readAllBytes(source)), "AVB input preserved");
                Files.write(source, Files.readAllBytes(output)); Files.delete(output);
            }
            check(Arrays.equals(original, Files.readAllBytes(source)), "AVB restore exact original v" + version);
            Files.write(ramdisk, new byte[131072]);
            rejected(source, ramdisk, output, "AVB_PARTITION_SIZE_LIMIT");
          }
        }
    }

    private static void rejected(Path source, Path ramdisk, Path output, String code) throws Exception {
        byte[] before = Files.readAllBytes(source);
        try { rebuild(source, ramdisk, output); throw new AssertionError("Expected " + code); }
        catch (IOException error) { check(code.equals(error.getMessage()), "Precise failure: " + error); }
        check(!Files.exists(output), "Failed output absent");
        check(Arrays.equals(before, Files.readAllBytes(source)), "Failed input preserved");
    }

    private static void contracts(Path source, Path ramdisk, Path output) throws Exception {
        for (int version = 0; version <= 4; version++) {
            byte[] original = fixture(version, 4096, components(version, 71, 53, true));
            Files.write(source, original); Files.write(ramdisk, content(53, 71));
            check(cli("rebuild-ramdisk-v4", source, ramdisk, output) == (version == 4 ? 0 : 3),
                    "Old CLI retains V4 restriction");
            if (version == 4) { check(Arrays.equals(original, Files.readAllBytes(output)), "Old V4 no-op"); Files.delete(output); }
            else check(!Files.exists(output), "Legacy rejected by old CLI without output");
            Files.write(ramdisk, new byte[0]);
            try {
                BootImageRebuilder.rebuild(source.toFile(), ramdisk.toFile(), output.toFile());
                throw new AssertionError("Kernel API accepted an empty replacement");
            } catch (IOException error) {
                check("REPLACEMENT_KERNEL_SIZE_LIMIT".equals(error.getMessage()), "Kernel replacement remains nonempty");
                check(!Files.exists(output) && Arrays.equals(original, Files.readAllBytes(source)),
                        "Empty kernel replacement preserves source and exposes no output");
            }
            Files.write(ramdisk, content(53, 71));
            Files.write(output, new byte[]{4, 5});
            try { rebuild(source, ramdisk, output); throw new AssertionError("Existing output overwritten"); }
            catch (java.nio.file.FileAlreadyExistsException expected) {
                check(Arrays.equals(new byte[]{4, 5}, Files.readAllBytes(output)), "Existing output preserved");
            }
            Files.delete(output);
            try { rebuild(source, ramdisk, source); throw new AssertionError("Source overwritten"); }
            catch (java.nio.file.FileAlreadyExistsException expected) {
                check(Arrays.equals(original, Files.readAllBytes(source)), "Source/output collision preserved");
            }
            byte[] input = Files.readAllBytes(ramdisk);
            try { rebuild(source, ramdisk, ramdisk); throw new AssertionError("Replacement overwritten"); }
            catch (java.nio.file.FileAlreadyExistsException expected) {
                check(Arrays.equals(input, Files.readAllBytes(ramdisk)), "Replacement/output collision preserved");
            }
            Thread.currentThread().interrupt();
            try { rejected(source, ramdisk, output, "CANCELLED"); }
            finally { Thread.interrupted(); }
            Files.write(source, fixture(version, 4096, components(version, 0, 53, true)));
            try {
                BootImageRebuilder.rebuild(source.toFile(), ramdisk.toFile(), output.toFile());
                throw new AssertionError("Kernel API accepted absent original kernel");
            } catch (IOException error) {
                check((version == 4 ? "EXPECTED_BOOT_V4" : "EXPECTED_AOSP_BOOT_HEADER").equals(error.getMessage()),
                        "Kernel API retains original nonzero-kernel contract");
                check(!Files.exists(output), "Kernel API rejected without output");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("maplesu-ramdisk-component-");
        Path source = work.resolve("source.img"), ramdisk = work.resolve("ramdisk"), output = work.resolve("output.img");
        try {
            Files.write(source, fixture(0, 4096, components(0, 71, 53, true)));
            Files.write(ramdisk, content(8193, 7));
            check(cli("rebuild-ramdisk", source, ramdisk, output) == 0,
                    "Real generic CLI must accept legacy ramdisk replacement");
            Files.delete(output);
            for (int version = 0; version <= 4; version++) {
                for (int page : new int[]{2048, 4096, 8192, 16384}) {
                    if (version >= 3 && page != 4096) continue;
                    for (boolean extras : new boolean[]{false, true}) {
                        for (int kernel : new int[]{0, 71}) {
                            byte[][] parts = components(version, kernel, extras ? 5000 : 0, extras);
                            for (byte[] value : new byte[][]{new byte[0], content(1, 7), content(53, 71), content(page * 2 + 1, 9)})
                                one(source, ramdisk, output, version, page, parts, value);
                            if (parts[1].length > 0) one(source, ramdisk, output, version, page, parts, parts[1]);
                        }
                    }
                }
            }
            for (int mask = 0; mask < 8; mask++) {
                byte[][] parts = components(2, 71, 53, true);
                for (int index = 2; index < 5; index++)
                    if ((mask & (1 << (index - 2))) == 0) parts[index] = new byte[0];
                one(source, ramdisk, output, 2, 2048, parts, content(8193, 9));
            }
            avbCycles(source, ramdisk, output);
            contracts(source, ramdisk, output);
            System.out.println("PASS " + passed + " unified ramdisk component writer checks");
        } finally {
            try (var paths = Files.list(work)) { for (Path path : paths.toList()) Files.deleteIfExists(path); }
            Files.deleteIfExists(work);
        }
    }
}
