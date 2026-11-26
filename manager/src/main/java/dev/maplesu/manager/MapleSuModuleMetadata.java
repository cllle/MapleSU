package dev.maplesu.manager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Static recognition of the pinned Ultra ABI. Never dlopen an untrusted module to read its metadata. */
final class MapleSuModuleMetadata {
    private static final String ENTRY = dev.maplesu.manager.compat.NativeModuleAbi.ENTRY;
    private static final String[][] FIELDS = {
        {"name", dev.maplesu.manager.compat.NativeModuleAbi.NAME_BEGIN, dev.maplesu.manager.compat.NativeModuleAbi.NAME_END},
        {"version", dev.maplesu.manager.compat.NativeModuleAbi.VERSION_BEGIN, dev.maplesu.manager.compat.NativeModuleAbi.VERSION_END},
        {"description", dev.maplesu.manager.compat.NativeModuleAbi.DESCRIPTION_BEGIN, dev.maplesu.manager.compat.NativeModuleAbi.DESCRIPTION_END},
        {"author", dev.maplesu.manager.compat.NativeModuleAbi.AUTHOR_BEGIN, dev.maplesu.manager.compat.NativeModuleAbi.AUTHOR_END},
        {"id", dev.maplesu.manager.compat.NativeModuleAbi.ID_BEGIN, dev.maplesu.manager.compat.NativeModuleAbi.ID_END},
    };

    static Properties inspect(byte[] data) throws ModuleArchiveInspector.Failure {
        if (index(data, ENTRY.getBytes(StandardCharsets.US_ASCII), 0) < 0) return null;
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < 64 || b.getInt(0) != 0x464c457f || b.get(4) != 2 || b.get(5) != 1
                || b.get(6) != 1 || b.getShort(16) != 3 || b.getShort(18) != 183
                || b.getInt(20) != 1 || b.getShort(52) != 64) throw invalid();
        int phnum = Short.toUnsignedInt(b.getShort(56)), shnum = Short.toUnsignedInt(b.getShort(60));
        if (b.getShort(54) != 56 || b.getShort(58) != 64 || phnum == 0 || phnum > 256
                || shnum == 0 || shnum > 4096) throw invalid();
        int phoff = range(b.getLong(32), phnum * 56L, data.length);
        int shoff = range(b.getLong(40), shnum * 64L, data.length);
        for (int i = 0; i < phnum; i++) {
            int p = phoff + i * 56;
            range(b.getLong(p + 8), b.getLong(p + 32), data.length);
            if (b.getLong(p + 40) < b.getLong(p + 32)) throw invalid();
        }
        for (int i = 0; i < shnum; i++) {
            int s = shoff + i * 64;
            if (b.getInt(s + 4) != 8) range(b.getLong(s + 24), b.getLong(s + 32), data.length);
        }
        int exports = 0;
        byte[] entry = (ENTRY + "\0").getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < shnum; i++) {
            int s = shoff + i * 64;
            if (b.getInt(s + 4) != 11) continue; // SHT_DYNSYM, not an arbitrary string/symtab.
            long size = b.getLong(s + 32);
            int link = b.getInt(s + 40);
            if (b.getLong(s + 56) != 24 || size % 24 != 0 || link < 0 || link >= shnum) throw invalid();
            int strings = shoff + link * 64;
            if (b.getInt(strings + 4) != 3) throw invalid();
            int stringOffset = range(b.getLong(strings + 24), b.getLong(strings + 32), data.length);
            long stringSize = b.getLong(strings + 32);
            int symoff = range(b.getLong(s + 24), size, data.length);
            for (int pos = symoff; pos < symoff + size; pos += 24) {
                long name = Integer.toUnsignedLong(b.getInt(pos));
                if (name >= stringSize) throw invalid();
                if (entry.length > stringSize - name || !matches(data, stringOffset + (int) name, entry)) continue;
                int info = Byte.toUnsignedInt(b.get(pos + 4)), visibility = b.get(pos + 5) & 3;
                int section = Short.toUnsignedInt(b.getShort(pos + 6));
                if (section == 0) continue; // An import is not a module entry.
                if (section >= shnum || (info & 15) != 2 || (info >> 4 != 1 && info >> 4 != 2)
                        || (visibility != 0 && visibility != 3)
                        || !executable(b, phoff, phnum, b.getLong(pos + 8), b.getLong(pos + 16))) throw invalid();
                exports++;
            }
        }
        if (exports == 0) return null;
        if (exports != 1) throw invalid();
        Properties result = new Properties();
        for (String[] field : FIELDS) {
            byte[] begin = field[1].getBytes(StandardCharsets.US_ASCII), end = field[2].getBytes(StandardCharsets.US_ASCII);
            int start = index(data, begin, 0), stop = start < 0 ? -1 : index(data, end, start + begin.length);
            if (start < 0 || stop < 0 || stop - start - begin.length > 4096
                    || index(data, begin, start + 1) >= 0 || index(data, end, stop + 1) >= 0
                    || !loaded(b, phoff, phnum, start, stop + end.length)) throw invalid();
            String value;
            try {
                value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(data, start + begin.length, stop - start - begin.length)).toString();
            } catch (CharacterCodingException exception) { throw invalid(); }
            if (value.indexOf('\0') >= 0) throw invalid();
            result.setProperty(field[0], value);
        }
        String id = result.getProperty("id");
        if (id.isEmpty() || id.equals(".") || id.equals("..") || id.indexOf('/') >= 0
                || id.getBytes(StandardCharsets.UTF_8).length >= 1024) throw invalid();
        return result;
    }

    private static boolean executable(ByteBuffer b, int phoff, int count, long address, long size) {
        if (address < 0 || size < 0) return false;
        for (int i = 0; i < count; i++) {
            int p = phoff + i * 56;
            long base = b.getLong(p + 16), length = b.getLong(p + 32);
            if (b.getInt(p) == 1 && (b.getInt(p + 4) & 1) != 0 && base >= 0 && address >= base
                    && address - base < length && size <= length - (address - base)) return true;
        }
        return false;
    }

    private static boolean loaded(ByteBuffer b, int phoff, int count, int start, int end) {
        for (int i = 0; i < count; i++) {
            int p = phoff + i * 56;
            long offset = b.getLong(p + 8), size = b.getLong(p + 32);
            if (b.getInt(p) == 1 && offset <= start && end - offset <= size) return true;
        }
        return false;
    }

    private static int range(long offset, long size, int length) throws ModuleArchiveInspector.Failure {
        if (offset < 0 || size < 0 || offset > length || size > length - offset) throw invalid();
        return (int) offset;
    }

    private static int index(byte[] data, byte[] needle, int start) throws ModuleArchiveInspector.Failure {
        for (int i = start; i <= data.length - needle.length; i++) {
            if ((i & 65535) == 0 && Thread.currentThread().isInterrupted()) {
                throw new ModuleArchiveInspector.Failure("CANCELLED");
            }
            if (matches(data, i, needle)) return i;
        }
        return -1;
    }

    private static boolean matches(byte[] data, int start, byte[] needle) {
        for (int i = 0; i < needle.length; i++) if (data[start + i] != needle[i]) return false;
        return true;
    }

    private static ModuleArchiveInspector.Failure invalid() {
        return new ModuleArchiveInspector.Failure("INVALID_MAPLESU_MODULE");
    }
}
