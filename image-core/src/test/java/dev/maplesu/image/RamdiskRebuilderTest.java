package dev.maplesu.image;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** File-only component tests; no loader, kernel bridge or device boot claim. */
public final class RamdiskRebuilderTest {
    private static int passed;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        passed++;
    }

    private static void rejected(Path source, Path ramdisk, Path output, String code) throws Exception {
        byte[] original = Files.readAllBytes(source);
        try {
            BootImageRebuilder.rebuildRamdiskV4(source.toFile(), ramdisk.toFile(), output.toFile());
            throw new AssertionError("Expected " + code);
        } catch (IOException error) {
            check(code.equals(error.getMessage()), "Unexpected failure: " + error);
        }
        check(!Files.exists(output), "Failed candidate removed");
        check(Arrays.equals(original, Files.readAllBytes(source)), "Failed source preserved");
    }

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("maplesu-ramdisk-rebuild-");
        Path source = work.resolve("source.img"), ramdisk = work.resolve("ramdisk"), output = work.resolve("output.img");
        try {
            for (int kernelSize : new int[]{0, 71}) {
                for (int oldSize : new int[]{0, 53, 5000}) {
                    byte[] original = BootImageRebuilderTest.fixture(kernelSize, oldSize);
                    Files.write(source, original);
                    for (int size : new int[]{0, 1, 53, 8193}) {
                        byte[] replacement = new byte[size];
                        Arrays.fill(replacement, (byte) 0x5a);
                        Files.write(ramdisk, replacement);
                        BootImageRebuilder.Report report = BootImageRebuilder.rebuildRamdiskV4(
                                source.toFile(), ramdisk.toFile(), output.toFile());
                        byte[] result = Files.readAllBytes(output);
                        int start = (4096 + kernelSize + 4095) / 4096 * 4096;
                        check(report.image.kernelSize == kernelSize, "Kernel size preserved");
                        check(report.image.ramdiskSize == size, "Ramdisk size replaced");
                        check(!report.kernelChanged && report.ramdiskChanged == !Arrays.equals(
                                Arrays.copyOfRange(original, start, start + oldSize), replacement), "Component change flags");
                        if (oldSize == 0 && size == 0)
                            check(Arrays.equals(original, result), "Empty ramdisk no-op is byte identical");
                        check(Arrays.equals(Arrays.copyOfRange(original, 4096, start),
                                Arrays.copyOfRange(result, 4096, start)), "Kernel and its padding preserved");
                        check(Arrays.equals(replacement, Arrays.copyOfRange(result, start, start + size)), "Ramdisk readback");
                        byte[] expectedHeader = Arrays.copyOf(original, 4096);
                        ByteBuffer.wrap(expectedHeader).order(ByteOrder.LITTLE_ENDIAN).putInt(12, size);
                        check(Arrays.equals(expectedHeader, Arrays.copyOf(result, 4096)), "Other header bytes preserved");
                        check(report.equivalence.ok(), "Independent declared diff");
                        check(report.toJson().contains("\"kernelPreserved\":true"), "Kernel preservation reported");
                        check(report.toJson().contains("\"flashableCandidate\":false"), "Container is not boot acceptance");
                        check(Arrays.equals(original, Files.readAllBytes(source)), "Source preserved");
                        Files.delete(output);
                    }
                }
            }
            byte[] original = BootImageRebuilderTest.fixture(71, 53);
            byte[] same = Arrays.copyOfRange(original, 8192, 8245);
            Files.write(source, original);
            Files.write(ramdisk, same);
            BootImageRebuilder.Report report = BootImageRebuilder.rebuildRamdiskV4(
                    source.toFile(), ramdisk.toFile(), output.toFile());
            check(Arrays.equals(original, Files.readAllBytes(output)), "Unsigned no-op is byte identical");
            check(!report.ramdiskChanged && !report.kernelChanged, "No-op change flags");
            try {
                BootImageRebuilder.rebuildRamdiskV4(source.toFile(), ramdisk.toFile(), output.toFile());
                throw new AssertionError("Existing output overwritten");
            } catch (java.nio.file.FileAlreadyExistsException expected) {
                check(Arrays.equals(original, Files.readAllBytes(output)), "Existing output preserved");
            }
            Files.delete(output);
            byte[] avb = Arrays.copyOf(original, 32768);
            avb[12288] = 'A'; avb[12289] = 'V'; avb[12290] = 'B'; avb[12291] = '0';
            ByteBuffer footer = ByteBuffer.wrap(avb).order(ByteOrder.BIG_ENDIAN);
            int at = avb.length - 64;
            footer.position(at); footer.put(new byte[]{'A', 'V', 'B', 'f'});
            footer.putInt(at + 4, 1).putLong(at + 12, 12288).putLong(at + 20, 12288).putLong(at + 28, 256);
            Files.write(source, avb);
            Files.write(ramdisk, new byte[8193]);
            report = BootImageRebuilder.rebuildRamdiskV4(source.toFile(), ramdisk.toFile(), output.toFile());
            byte[] result = Files.readAllBytes(output);
            check(result.length == avb.length && report.avbFooterPresent, "AVB partition capacity retained");
            check(Arrays.equals(Arrays.copyOfRange(avb, 4096, 8192), Arrays.copyOfRange(result, 4096, 8192)),
                    "AVB relocation preserves kernel");
            check(Arrays.equals(Arrays.copyOfRange(avb, 12288, 12544), Arrays.copyOfRange(result, 20480, 20736)),
                    "Existing vbmeta bytes relocated");
            check(ByteBuffer.wrap(result).order(ByteOrder.BIG_ENDIAN).getLong(at + 20) == 20480, "AVB footer updated");
            check(report.equivalence.ok(), "AVB declared diff verified");
            Files.delete(output);
            Files.write(ramdisk, new byte[32768]);
            rejected(source, ramdisk, output, "AVB_PARTITION_SIZE_LIMIT");
            Files.write(source, original);
            Files.write(ramdisk, same);
            try {
                BootImageRebuilder.rebuildRamdiskV4(source.toFile(), ramdisk.toFile(), output.toFile(),
                        () -> Files.write(ramdisk, new byte[]{1}));
                throw new AssertionError("Changed ramdisk input accepted");
            } catch (IOException error) {
                check("SOURCE_CHANGED".equals(error.getMessage()), "Changed input identified");
                check(!Files.exists(output), "Changed-input output removed");
            }
            Files.write(ramdisk, same);
            try {
                BootImageRebuilder.rebuildRamdiskV4(source.toFile(), ramdisk.toFile(), output.toFile(), () -> {
                    try (var file = new java.io.RandomAccessFile(output.toFile(), "rw")) {
                        file.seek(4096); file.write(0x19);
                    }
                });
                throw new AssertionError("Corrupted kernel accepted");
            } catch (IOException error) {
                check("REBUILD_READBACK_MISMATCH".equals(error.getMessage()), "Kernel corruption identified");
                check(!Files.exists(output), "Corrupt output removed");
            }
            check(Arrays.equals(original, Files.readAllBytes(source)), "All tests preserved original");
            System.out.println("PASS " + passed + " ramdisk rebuild checks");
        } finally {
            try (var files = Files.list(work)) { for (Path file : files.toList()) Files.deleteIfExists(file); }
            Files.deleteIfExists(work);
        }
    }
}
