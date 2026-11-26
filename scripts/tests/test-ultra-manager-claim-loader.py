"""Test production protocol-v3 materialization without boot/auth/root execution."""
from pathlib import Path
import argparse
import ctypes
import hashlib
import json
import random
import struct
import zlib
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
SOURCES = [
    "runtime/ultra/identity/kernel/manager_claim_loader.c",
    "runtime/ultra/identity/kernel/manager_claim_loader.h",
    "runtime/tests/ultra_manager_claim_loader_test.c",
    "scripts/tests/test-ultra-manager-claim-loader.py",
]


class Binding(ctypes.Structure):
    _fields_ = [(name, ctypes.c_uint32) for name in (
        'image_bytes', 'descriptor_offset', 'descriptor_bytes', 'descriptor_crc', 'loader_offset', 'loader_bytes')]


def compact():
    image = bytearray(4096)
    text = bytes.fromhex('c0035fd61f2003d5')
    image[2048:2056] = text
    descriptor = struct.pack('<13I', 0x4355534d, 3, 120, 4096, 48, 0, 2, 2, 1, 2,
                             zlib.crc32(text + bytes(16)), 4096, 1024)
    descriptor += struct.pack('<8I', 0, 8, 8, 5, 16, 16, 32, 3)
    descriptor += struct.pack('<I', 128)
    descriptor += struct.pack('<8I', 16, 1, 0, 0, 24, 2, 0, 0)
    table = struct.pack('<4I', 2048, 8, 2112, 16)
    image[512:632] = descriptor
    image[1024:1040] = table
    return image, Binding(4096, 512, 120, zlib.crc32(table, zlib.crc32(descriptor)), 64, 256)


def crc(image, binding):
    table_at = struct.unpack_from('<I', image, 560)[0]
    chunks = struct.unpack_from('<I', image, 540)[0]
    binding.descriptor_crc = zlib.crc32(image[table_at:table_at + chunks * 8],
                                      zlib.crc32(image[binding.descriptor_offset:binding.descriptor_offset + binding.descriptor_bytes]))


def inside(build, carriers, images):
    OUT = build
    library = ctypes.CDLL(str(OUT / 'loader-host.so'))
    load = library.msu_manager_claim_materialize
    load.argtypes = [ctypes.POINTER(Binding), ctypes.c_void_p, ctypes.c_void_p, ctypes.c_uint32]
    load.restype = ctypes.c_int
    corpus = OUT / 'corpus'
    corpus.mkdir(exist_ok=True)
    cases, named = [], []

    def run(image, binding, capacity=48, expected=None, mode=0, save=True, import_offset=128):
        source = (ctypes.c_ubyte * len(image)).from_buffer(image)
        destination = bytearray([0xa5]) * (capacity + 128)
        dest = (ctypes.c_ubyte * len(destination)).from_buffer(destination)
        source_at, target_at = ctypes.addressof(source), ctypes.addressof(dest) + 64
        before_dest, before_image = bytes(destination), bytes(image)
        if mode == 2:
            target_at = source_at + 64
        result = load(ctypes.byref(binding), source_at, target_at, capacity)
        assert destination[:64] == bytes([0xa5]) * 64 and destination[-64:] == bytes([0xa5]) * 64
        assert image == before_image
        if result < 0:
            assert destination == before_dest, 'failure wrote destination'
        if expected is not None:
            assert result == expected, (result, expected)
        if mode == 1:
            assert result == 1 and destination[64:72] == bytes.fromhex('c0035fd61f2003d5')
            assert destination[72:80] == bytes(8) and destination[96:-64] == bytes(16)
            assert struct.unpack_from('<QQ', destination, 80) == (target_at, source_at + import_offset)
        if save:
            path = corpus / f'case-{len(cases):05}.Image'
            path.write_bytes(image)
            values = [getattr(binding, name) for name, _ in Binding._fields_]
            cases.append(' '.join(map(str, [path, *values, capacity, result, mode, import_offset])) + '\n')
        return result, destination, source_at, target_at

    image, binding = compact()
    run(image, binding, expected=1, mode=1)
    named.append('valid-relative-import-implicit-targets')
    fields = {
        'magic': (0, 0, -2), 'version-v1-rejected': (4, 1, -2), 'version-v2-rejected': (4, 2, -2),
        'total-size': (8, 124, -2),
        'image-binding': (12, 4092, -2), 'zero-memory': (16, 0, -2), 'memory-cap': (16, 1048577, -2),
        'entry-unaligned': (20, 1, -5), 'entry-gap': (20, 8, -5), 'entry-data': (20, 16, -5),
        'zero-sections': (24, 0, -2), 'section-cap': (24, 33, -2),
        'zero-chunks': (28, 0, -2), 'chunk-cap': (28, 257, -2), 'missing-last-chunk': (28, 1, -7),
        'extra-chunk': (28, 3, -6), 'import-cap': (32, 65, -2), 'reloc-cap': (36, 129, -2),
        'record-size-mismatch': (36, 1, -3), 'payload-checksum': (40, 1, -7),
        'kernel-memory-below-file': (44, 4095, -2),
        'table-misaligned': (48, 1025, -10), 'table-out-of-image': (48, 4092, -10),
        'table-u32-overflow': (48, 0xffffffff, -10), 'table-descriptor-alias': (48, 512, -10),
        'table-descriptor-tail-overlap': (48, 628, -10), 'table-loader-alias': (48, 64, -10),
        'table-loader-tail-overlap': (48, 316, -10),
        'section-out-of-memory': (52, 47, -4), 'file-over-memory': (56, 9, -4),
        'section-overflow': (60, 0xffffffff, -4), 'section-rwx': (64, 7, -4),
        'section-no-read': (64, 4, -4), 'section-unknown-permission': (64, 13, -4),
        'section-overlap': (68, 4, -4), 'import-sentinel': (84, 0xffffffff, -8),
        'import-unaligned': (84, 129, -8), 'import-out-of-image': (84, 4096, -8),
        'reloc-readonly-target': (88, 0, -9), 'reloc-misaligned-target': (88, 17, -9),
        'reloc-outside': (88, 48, -9), 'reloc-kind': (92, 3, -9),
        'relative-to-gap': (96, 8, -9), 'reloc-reserved': (100, 1, -9),
        'reloc-duplicate': (104, 16, -9), 'import-index': (112, 1, -9),
    }
    for name, (offset, value, expected) in fields.items():
        image, binding = compact()
        struct.pack_into('<I', image, 512 + offset, value)
        crc(image, binding)
        run(image, binding, expected=expected)
        named.append(name)
    for name, offset, expected in [
        ('bss-import-first-byte', 4096, 1), ('bss-import-last-word', 8188, 1),
        ('bss-import-out-of-memory', 8192, -8), ('bss-import-unaligned', 4097, -8)]:
        image, binding = compact()
        struct.pack_into('<I', image, 556, 8192)
        struct.pack_into('<I', image, 596, offset)
        crc(image, binding)
        run(image, binding, expected=expected, mode=1 if expected == 1 else 0, import_offset=offset)
        named.append(name)
    for name, header_offset, value, expected in [
        ('memory-extent-does-not-expand-chunk-table', 48, 4096, -10)]:
        image, binding = compact()
        struct.pack_into('<I', image, 556, 8192)
        struct.pack_into('<I', image, 512 + header_offset, value)
        run(image, binding, expected=expected)
        named.append(name)
    image, binding = compact()
    struct.pack_into('<I', image, 556, 8192)
    struct.pack_into('<I', image, 1024, 4096)
    crc(image, binding)
    run(image, binding, expected=-6)
    named.append('memory-extent-does-not-expand-chunk-source')
    table_cases = {
        'source-out-of-image': (0, 4093, -6), 'source-descriptor-alias': (0, 512, -6),
        'source-loader-alias': (0, 64, -6), 'source-table-alias': (0, 1024, -6),
        'source-table-tail-overlap': (0, 1036, -6), 'source-zero-length': (4, 0, -6),
        'source-length-overflow': (4, 0xffffffff, -6), 'source-over-section': (4, 12, -6),
        'implicit-target-skips-no-bytes': (4, 4, -6), 'source-pair-overlap': (8, 2048, -6),
        'missing-section-tail': (12, 8, -7),
    }
    for name, (offset, value, expected) in table_cases.items():
        image, binding = compact()
        struct.pack_into('<I', image, 1024 + offset, value)
        crc(image, binding)
        run(image, binding, expected=expected)
        named.append(name)
    for name, at in [('table-adjacent-after-descriptor', 632), ('table-adjacent-after-loader', 320)]:
        image, binding = compact()
        image[at:at + 16] = image[1024:1040]
        struct.pack_into('<I', image, 560, at)
        crc(image, binding)
        run(image, binding, expected=1, mode=1)
        named.append(name)
    for name, field, value in [
        ('descriptor-misaligned', 'descriptor_offset', 513), ('descriptor-short', 'descriptor_bytes', 48),
        ('descriptor-outside', 'descriptor_offset', 4092), ('loader-misaligned', 'loader_offset', 65),
        ('loader-empty', 'loader_bytes', 0), ('loader-outside', 'loader_offset', 4092),
        ('loader-descriptor-overlap', 'loader_offset', 512)]:
        image, binding = compact()
        setattr(binding, field, value)
        run(image, binding, expected=-1)
        named.append(name)
    for name, at in [('descriptor-crc-detects-corruption', 596), ('chunk-table-crc-detects-corruption', 1036)]:
        image, binding = compact()
        image[at] ^= 1
        run(image, binding, expected=-2)
        named.append(name)
    image, binding = compact()
    image[2048] ^= 1
    run(image, binding, expected=-7)
    named.append('payload-corruption')
    image, binding = compact()
    run(image, binding, capacity=47, expected=-2)
    named.append('destination-short')
    image, binding = compact()
    run(image, binding, expected=-1, mode=2)
    named.append('source-destination-alias')
    rng = random.Random(0x3255534d)
    accepted = rejected = 0
    positions = [512 + i * 4 for i in range(30)] + [1024 + i * 4 for i in range(4)]
    for _ in range(4000):
        image, binding = compact()
        position = rng.choice(positions)
        struct.pack_into('<I', image, position,
                         rng.choice([0, 1, 3, 7, 32, 256, 4095, 4096, 0xffffffff, rng.getrandbits(32)]))
        crc(image, binding)
        result, _, _, _ = run(image, binding)
        accepted += result > 0
        rejected += result < 0
    assert len(named) == 78
    (OUT / 'compact-replay.tsv').write_text(''.join(cases))
    full_results, full_replay = [], []
    if carriers:
        placement = json.loads((carriers / 'placement-results.json').read_text())
        assert placement['protocolVersion'] == 3 and placement['placementPassedCount'] == 17
        rows = placement['results']
        assert len(rows) == 17
        production_text = (OUT / 'loader.text.bin').read_bytes()
        for row in rows:
            assert row['placementPassed']
            index = row['index']
            folder = carriers / f'image-{index}'
            image = bytearray((images / f'image-{index}' / 'Image').read_bytes())
            assert hashlib.sha256(image).hexdigest() == row['sourceSha256']
            patches = (folder / row['patchDataFile']).read_bytes()
            cursor = 0
            for write in row['writes']:
                at, length = write['offset'], write['bytes']
                image[at:at + length] = patches[cursor:cursor + length]
                cursor += length
            assert cursor == len(patches) and hashlib.sha256(image).hexdigest() == row['derivedSha256']
            loader_at = row['binding'][4]
            assert row['binding'][5] == len(production_text)
            assert image[loader_at:loader_at + len(production_text)] == production_text
            result, destination, source_at, target_at = run(image, Binding(*row['binding']), row['memoryBytes'],
                                                           row['entryOffset'] + 1, save=False)
            expected = bytearray((folder / 'resolver-expected.bin').read_bytes())
            for target, kind, value, reserved in row['relocations']:
                assert reserved == 0 and kind in (1, 2)
                pointer = target_at + value if kind == 1 else source_at + row['imports'][value]
                struct.pack_into('<Q', expected, target, pointer)
            assert destination[64:-64] == expected
            full_results.append(dict(index=index, loaderReturn=result, allSectionsAndRelocationsEqual=True,
                                     zeroFilledMemoryVerified=True, sourceUnchanged=True,
                                     destinationCanariesPreserved=True, embeddedLoaderMatchesProductionText=True))
            full_replay.append(' '.join(map(str, [index, *row['binding'], row['memoryBytes'], result])) + '\n')
        assert len(full_results) == 17
    (OUT / 'materializer-replay.tsv').write_text(''.join(full_replay))
    (OUT / 'python-results.json').write_text(json.dumps(dict(
        passed=True, protocolVersion=3, compiledProductionCExecuted=True, namedChecks=named, namedCheckCount=len(named),
        mutationAudit=dict(iterations=4000, acceptedStructurallyValid=accepted, rejected=rejected,
                           allFailuresZeroWrite=True, allCanariesPreserved=True),
        compactReplayCases=len(cases), fullResolverMaterializations=full_results,
        kernelOrArm64PayloadExecuted=False, rootGranted=False), indent=2) + '\n')
    print(f'production v3 materializer: {len(named)} named + 4000 mutations + {len(full_results)} byte-exact full resolvers')



def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build", type=Path, required=True)
    parser.add_argument("--carriers", type=Path)
    parser.add_argument("--images", type=Path, help="Original unpacked Image directory for v3 full replay")
    parser.add_argument("--inside", action="store_true")
    args = parser.parse_args()
    if bool(args.carriers) != bool(args.images):
        parser.error("--carriers and --images must be supplied together")
    if args.inside:
        inside(args.build, args.carriers, args.images)
        return 0
    build = args.build.resolve()
    build.mkdir(parents=True, exist_ok=True)
    source_hashes = {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in SOURCES}
    script = r'''set -eu
P=/repo/runtime/ultra/identity/kernel/manager_claim_loader.c
clang --version
clang --target=aarch64-linux-gnu -Os -ffreestanding -fno-builtin -fno-stack-protector -fno-vectorize -fno-slp-vectorize -fPIC -mbranch-protection=standard -fno-asynchronous-unwind-tables -Wall -Wextra -Werror -c "$P" -o /case/loader.o
ld.lld -shared --entry=msu_manager_claim_materialize -Bsymbolic --strip-debug -z force-bti -o /case/loader.so /case/loader.o
llvm-objcopy -O binary --only-section=.text /case/loader.so /case/loader.text.bin
llvm-objdump -d /case/loader.so > /case/loader.disasm.txt
clang -Os -ffreestanding -fno-builtin -fno-stack-protector -fPIC -shared -Wall -Wextra -Werror "$P" -o /case/loader-host.so
python3 /repo/scripts/tests/test-ultra-manager-claim-loader.py --inside --build /case CARRIERS
clang -std=c11 -O1 -g -Wall -Wextra -Werror -fno-omit-frame-pointer -fsanitize=address,undefined -fno-sanitize-recover=all "$P" /repo/runtime/tests/ultra_manager_claim_loader_test.c -o /case/loader-sanitized.test
/case/loader-sanitized.test /case/compact-replay.tsv
FULL_REPLAY
make -C /opt/ddk/kdir/android16-6.12 M=/case/kernel-probe ARCH=arm64 LLVM=1 manager_claim_loader_probe.o
'''.replace("CARRIERS", "--carriers /carriers --images /images" if args.carriers else "").replace(
    "FULL_REPLAY", "/case/loader-sanitized.test --full /case/materializer-replay.tsv /images /carriers" if args.carriers else "")
    probe = build / "kernel-probe"
    probe.mkdir(exist_ok=True)
    (probe / "manager_claim_loader_probe.c").write_text(
        '#include <linux/types.h>\n'
        '#include "/repo/runtime/ultra/identity/kernel/manager_apk_identity.h"\n'
        '#include "/repo/runtime/ultra/identity/kernel/manager_claim_loader.c"\n', encoding="utf-8")
    (probe / "Makefile").write_text(
        "obj-m += manager_claim_loader_probe.o\n"
        "ccflags-y += -Werror -fno-lto -fno-sanitize=cfi -fPIC -mbranch-protection=standard\n",
        encoding="utf-8")
    (build / "run.sh").write_text(script, encoding="utf-8", newline="\n")
    command = ["docker", "run", "--rm", "--pull=never", "--network", "none",
               "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
               "--mount", f"type=bind,source={ROOT},target=/repo,readonly",
               "--mount", f"type=bind,source={build},target=/case"]
    if args.carriers:
        carriers = args.carriers.resolve(strict=True)
        command += ["--mount", f"type=bind,source={carriers},target=/carriers,readonly"]
        images = args.images.resolve(strict=True)
        command += ["--mount", f"type=bind,source={images},target=/images,readonly"]
    command += ["ghcr.io/ylarod/ddk-min:android16-6.12-20260828", "sh", "/case/run.sh"]
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8",
                            errors="replace", timeout=240)
    (build / "run.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    unchanged = all(hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == digest
                    for name, digest in source_hashes.items())
    metrics = None
    if result.returncode == 0:
        from elftools.elf.elffile import ELFFile
        from elftools.elf.relocation import RelocationSection
        with (build / "loader.so").open("rb") as stream:
            elf = ELFFile(stream)
            text = elf.get_section_by_name(".text")
            imports = [s.name for s in elf.get_section_by_name(".dynsym").iter_symbols()
                       if s.name and s["st_shndx"] == "SHN_UNDEF"]
            relocations = sum(s.num_relocations() for s in elf.iter_sections()
                              if isinstance(s, RelocationSection))
            metrics = {"textBytes": text["sh_size"], "entryOffsetInText": elf["e_entry"] - text["sh_addr"],
                       "imports": imports, "dynamicRelocationCount": relocations,
                       "textSha256": hashlib.sha256((build / "loader.text.bin").read_bytes()).hexdigest()}
            assert not imports and relocations == 0 and metrics["entryOffsetInText"] == 0
            assert not any(s.name in (".rodata", ".data", ".bss", ".got", ".got.plt") and s["sh_size"]
                           for s in elf.iter_sections())
    report = {"passed": result.returncode == 0 and unchanged, "protocolVersion": 3, "exitCode": result.returncode,
              "sourceHashes": source_hashes, "sourcesUnchanged": unchanged, "aarch64": metrics,
              "sanitizers": ["address", "undefined"], "linuxHeaderCoexistenceCompile": result.returncode == 0,
              "carrierRegressionRequested": bool(args.carriers), "gradleExecuted": False,
              "bootEntrypointConnected": False, "kernelExecuted": False,
              "rootGranted": False, "deviceExecuted": False}
    (build / "results.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(result.stdout + result.stderr, end="")
    if metrics:
        print(json.dumps(metrics))
    return result.returncode if unchanged else 1


if __name__ == "__main__":
    sys.exit(main())
