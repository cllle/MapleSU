package dev.maplesu.image;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Bounded, platform-independent codecs. No native libraries or external programs. */
public final class KernelCompression {
    public static final int MAX_KERNEL_BYTES = 128 * 1024 * 1024;
    private static final int BLOCK_SIZE = 8 * 1024 * 1024;
    private static final byte[] LZ4_MAGIC = {2, 33, 76, 24};

    private KernelCompression() {}

    public static byte[] decode(byte[] compressed, String format, int limit) throws IOException {
        cancelled();
        if (limit <= 0 || limit > MAX_KERNEL_BYTES) throw new IOException("INVALID_DECODE_LIMIT");
        switch (format) {
            case "RAW_ARM64":
                if (compressed.length > limit) throw new IOException("DECOMPRESSION_LIMIT");
                return compressed.clone();
            case "GZIP":
                try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                    return readBounded(in, limit);
                }
            case "LZ4_LEGACY":
                return decodeLegacy(compressed, limit);
            case "LZ4_FRAME":
                return Lz4FrameCodec.decode(compressed, limit);
            case "ZSTD":
                return ZstdCodec.decode(compressed, limit);
            case "XZ":
                return XzCodec.decode(compressed, limit);
            default:
                throw new IOException("UNSUPPORTED_COMPRESSION:" + format);
        }
    }

    /**
     * LZ4 containers share a bounded block compressor. Zstd writes raw blocks and xz writes
     * uncompressed LZMA2 chunks; none of the codecs needs a native library or external program.
     */
    public static byte[] encode(byte[] raw, String format) throws IOException {
        cancelled();
        if (raw.length == 0 || raw.length > MAX_KERNEL_BYTES) throw new IOException("INVALID_KERNEL_SIZE");
        if ("RAW_ARM64".equals(format)) return raw.clone();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if ("GZIP".equals(format)) {
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                for (int offset = 0; offset < raw.length; offset += 64 * 1024) {
                    cancelled();
                    gzip.write(raw, offset, Math.min(64 * 1024, raw.length - offset));
                }
            }
        } else if ("LZ4_LEGACY".equals(format)) {
            out.write(LZ4_MAGIC);
            for (int offset = 0; offset < raw.length; offset += BLOCK_SIZE) {
                cancelled();
                int length = Math.min(BLOCK_SIZE, raw.length - offset);
                byte[] block = Lz4BlockEncoder.encode(raw, offset, length);
                writeU32(out, block.length);
                out.write(block);
            }
        } else if ("LZ4_FRAME".equals(format)) {
            return Lz4FrameCodec.encode(raw);
        } else if ("ZSTD".equals(format)) {
            return ZstdCodec.encode(raw);
        } else if ("XZ".equals(format)) {
            return XzCodec.encode(raw);
        } else {
            throw new IOException("UNSUPPORTED_COMPRESSION:" + format);
        }
        return out.toByteArray();
    }

    /** Re-encodes a kernel while retaining bytes after its compressed stream (for example DTBs). */
    public static byte[] encodeKernelComponent(byte[] raw, byte[] original, String format) throws IOException {
        cancelled();
        int[] consumed = {original.length};
        switch (format) {
            case "GZIP": consumed[0] = gzipMembersEnd(original); break;
            case "LZ4_FRAME": Lz4FrameCodec.decode(original, MAX_KERNEL_BYTES, consumed); break;
            case "XZ": XzCodec.decode(original, MAX_KERNEL_BYTES, consumed); break;
            case "ZSTD": ZstdCodec.decode(original, MAX_KERNEL_BYTES, consumed); break;
            // Legacy LZ4 has no end marker; EOF must be a validated block boundary.
            case "LZ4_LEGACY": decodeLegacy(original, MAX_KERNEL_BYTES); break;
        }
        int end = consumed[0];
        byte[] encoded = encode(raw, format);
        if (end == original.length) return encoded;
        byte[] component = Arrays.copyOf(encoded, encoded.length + original.length - end);
        System.arraycopy(original, end, component, encoded.length, original.length - end);
        return component;
    }

    /** Finds the boundary from deflate consumption, never from a guessed footer or DTB signature. */
    private static int gzipMembersEnd(byte[] input) throws IOException {
        int position = 0, total = 0;
        byte[] buffer = new byte[64 * 1024];
        do {
            cancelled();
            int header = position;
            requireBytes(input, position, 10, "TRUNCATED_GZIP_HEADER");
            int flags = input[position + 3] & 255;
            if ((input[position] & 255) != 31 || (input[position + 1] & 255) != 139
                    || input[position + 2] != 8 || (flags & 224) != 0) {
                throw new IOException("INVALID_GZIP_HEADER");
            }
            position += 10;
            if ((flags & 4) != 0) {
                requireBytes(input, position, 2, "TRUNCATED_GZIP_HEADER");
                int length = (input[position] & 255) | ((input[position + 1] & 255) << 8);
                position += 2;
                requireBytes(input, position, length, "TRUNCATED_GZIP_HEADER");
                position += length;
            }
            for (int flag : new int[]{8, 16}) {
                if ((flags & flag) == 0) continue;
                while (true) {
                    requireBytes(input, position, 1, "TRUNCATED_GZIP_HEADER");
                    if ((position & 65535) == 0) cancelled();
                    if (input[position++] == 0) break;
                }
            }
            CRC32 crc = new CRC32();
            if ((flags & 2) != 0) {
                requireBytes(input, position, 2, "TRUNCATED_GZIP_HEADER");
                crc.update(input, header, position - header);
                int expected = (input[position] & 255) | ((input[position + 1] & 255) << 8);
                if ((crc.getValue() & 65535) != expected) throw new IOException("INVALID_GZIP_HEADER_CRC");
                position += 2;
                crc.reset();
            }
            Inflater inflater = new Inflater(true);
            int size = 0;
            try {
                inflater.setInput(input, position, input.length - position);
                while (!inflater.finished()) {
                    cancelled();
                    int count = inflater.inflate(buffer);
                    if (count > MAX_KERNEL_BYTES - total) throw new IOException("DECOMPRESSION_LIMIT");
                    crc.update(buffer, 0, count);
                    total += count;
                    size += count;
                    if (count == 0 && !inflater.finished()) {
                        if (inflater.needsInput()) throw new IOException("TRUNCATED_GZIP_DATA");
                        throw new IOException("INVALID_GZIP_DATA");
                    }
                }
                position = input.length - inflater.getRemaining();
            } catch (DataFormatException error) {
                throw new IOException("INVALID_GZIP_DATA", error);
            } finally {
                inflater.end();
            }
            requireBytes(input, position, 8, "TRUNCATED_GZIP_TRAILER");
            if (readU32(input, position) != crc.getValue() || readU32(input, position + 4) != size) {
                throw new IOException("INVALID_GZIP_TRAILER");
            }
            position += 8;
        } while (input.length - position >= 2 && (input[position] & 255) == 31
                && (input[position + 1] & 255) == 139);
        if (total == 0) throw new IOException("EMPTY_KERNEL");
        return position;
    }

    private static void requireBytes(byte[] input, int position, int size, String error) throws IOException {
        if (size > input.length - position) throw new IOException(error);
    }

    private static long readU32(byte[] input, int position) {
        return (input[position] & 255L) | ((input[position + 1] & 255L) << 8)
                | ((input[position + 2] & 255L) << 16) | ((input[position + 3] & 255L) << 24);
    }

    private static byte[] decodeLegacy(byte[] input, int limit) throws IOException {
        if (input.length < 4 || !Arrays.equals(LZ4_MAGIC, Arrays.copyOf(input, 4))) {
            throw new IOException("INVALID_LZ4_MAGIC");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int position = 4;
        while (position < input.length) {
            cancelled();
            if (input.length - position < 4) throw new IOException("TRUNCATED_LZ4_BLOCK_HEADER");
            long size = (input[position] & 255L) | ((input[position + 1] & 255L) << 8)
                    | ((input[position + 2] & 255L) << 16) | ((input[position + 3] & 255L) << 24);
            position += 4;
            if (size == 0 || size > BLOCK_SIZE + BLOCK_SIZE / 255 + 16
                    || size > input.length - position) throw new IOException("INVALID_LZ4_BLOCK_SIZE");
            byte[] block = decodeBlock(input, position, (int) size, Math.min(BLOCK_SIZE, limit - out.size()));
            out.write(block);
            position += (int) size;
        }
        if (out.size() == 0) throw new IOException("EMPTY_KERNEL");
        return out.toByteArray();
    }

    private static byte[] decodeBlock(byte[] input, int start, int size, int limit) throws IOException {
        byte[] output = new byte[Math.max(0, limit)];
        int in = start, end = start + size, count = 0;
        while (in < end) {
            int token = input[in++] & 255;
            int literals = token >>> 4;
            if (literals == 15) {
                int next;
                do {
                    if (in == end) throw new IOException("TRUNCATED_LZ4_LENGTH");
                    next = input[in++] & 255;
                    if (literals > limit - next) throw new IOException("DECOMPRESSION_LIMIT");
                    literals += next;
                } while (next == 255);
            }
            if (literals > end - in) throw new IOException("TRUNCATED_LZ4_LITERALS");
            if (literals > limit - count) throw new IOException("DECOMPRESSION_LIMIT");
            System.arraycopy(input, in, output, count, literals);
            in += literals;
            count += literals;
            if (in == end) break;
            if (end - in < 2) throw new IOException("TRUNCATED_LZ4_OFFSET");
            int distance = (input[in] & 255) | ((input[in + 1] & 255) << 8);
            in += 2;
            if (distance == 0 || distance > count) throw new IOException("INVALID_LZ4_OFFSET");
            int match = (token & 15) + 4;
            if ((token & 15) == 15) {
                int next;
                do {
                    if (in == end) throw new IOException("TRUNCATED_LZ4_LENGTH");
                    next = input[in++] & 255;
                    if (match > limit - next) throw new IOException("DECOMPRESSION_LIMIT");
                    match += next;
                } while (next == 255);
            }
            if (match > limit - count) throw new IOException("DECOMPRESSION_LIMIT");
            for (int i = 0; i < match; i++) { output[count] = output[count - distance]; count++; }
        }
        if (count == 0) throw new IOException("EMPTY_LZ4_BLOCK");
        return Arrays.copyOf(output, count);
    }

    private static byte[] readBounded(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = in.read(buffer)) != -1) {
            cancelled();
            if (count > limit - out.size()) throw new IOException("DECOMPRESSION_LIMIT");
            out.write(buffer, 0, count);
        }
        if (out.size() == 0) throw new IOException("EMPTY_KERNEL");
        return out.toByteArray();
    }

    private static void writeU32(ByteArrayOutputStream out, int value) {
        for (int i = 0; i < 4; i++) out.write((value >>> (i * 8)) & 255);
    }

    private static void cancelled() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("CANCELLED");
    }
}
