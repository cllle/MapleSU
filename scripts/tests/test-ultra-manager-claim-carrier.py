"""Compile and exercise the production Kotlin carrier without Android or Gradle."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import struct
import subprocess
import sys
import zlib

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'work/ultra-native-manager-claim-20261113'
SERIES = {'5.10': 'android12-5.10', '5.15': 'android13-5.15', '6.1': 'android14-6.1',
          '6.6': 'android15-6.6', '6.12': 'android16-6.12', '6.18': 'android17-6.18'}
SHA = lambda data: hashlib.sha256(data).hexdigest()


def aligned_padding(raw, text_start, text_end, exclusions):
    """Scan aligned words, preserving the independent symbol/runtime/spec exclusions."""
    exclusions = sorted(exclusions)
    caves, blocked, run = [], 0, None
    at, end = (text_start + 3) & ~3, text_end & ~3
    while at <= end - 4:
        while blocked < len(exclusions) and exclusions[blocked][1] <= at:
            blocked += 1
        if blocked < len(exclusions) and exclusions[blocked][0] < at + 4:
            if run is not None:
                caves.append([run, at])
                run = None
            at = (exclusions[blocked][1] + 3) & ~3
            continue
        if struct.unpack_from('<I', raw, at)[0] not in (0, 0xd503201f):
            if run is not None:
                caves.append([run, at])
                run = None
        elif run is None:
            run = at
        at += 4
    if run is not None:
        caves.append([run, at])
    return caves


def padding_boundary_regression():
    # The string's terminal NUL starts an unaligned regex run and used to hide
    # the last complete zero word before the next symbol's BTI instruction.
    raw = b'abc\0' + bytes(52) + struct.pack('<I', 0xd503245f)
    assert aligned_padding(raw, 0, len(raw), [[56, 60]]) == [[4, 56]]
    assert aligned_padding(raw, 0, len(raw), [[13, 14], [56, 60]]) == [[4, 12], [16, 56]]
    assert aligned_padding(raw[:56] + bytes(4), 0, len(raw), [[56, 60]]) == [[4, 56]]


def replay_connected(image, plan, folder, bootstrap_bytes):
    """Run real relay/bootstrap/materializer; kernel calls and final identity entry are fixtures."""
    from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE
    from unicorn import arm64_const as reg
    base, stack, stage = 0x10000000, 0x70000000, 0x50000000
    gate, key_at, frame_at = map(int, (folder / 'connected-entries.tsv').read_text().split())
    expected_key = (folder / 'connected-credential.bin').read_bytes()
    replay_image = bytearray(image)
    replay_image[key_at:key_at + 48] = expected_key
    frame = (folder / 'connected-frame.bin').read_bytes()
    assert len(frame) == 52
    replay_image[frame_at:frame_at + len(frame)] = frame
    anchor = struct.unpack_from('<24I', replay_image, plan['anchorOffset'])
    assert anchor[:4] == (0x4143534d, 1, plan['anchorOffset'], 12)
    assert anchor[4] == plan['materializerOffset'] and anchor[5] == key_at
    outcomes = []
    for scenario in ('ordinary', 'claim', 'error-pointer', 'null-filename'):
        machine = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
        machine.mem_map(base, (len(image) + 4095) & ~4095)
        machine.mem_write(base, bytes(replay_image))
        machine.mem_map(stack, 65536)
        machine.mem_map(stage, 1048576)
        sp, name, filename = stack + 16384, stack + 32768, stack + 36864
        address = 0x12345678
        request = b'/.msu-ultra-identity-v1/' + f'{address:016x}'.encode() if scenario == 'claim' else b'/system/bin/ordinary'
        machine.mem_write(name, request + b'\0')
        machine.mem_write(filename, struct.pack('<Q', name))
        initial = {i: 0x1000 + i for i in range(31)}
        initial[1] = ((1 << 64) - 2 if scenario == 'error-pointer' else
                      0 if scenario == 'null-filename' else filename)
        for i, value in initial.items():
            machine.reg_write(getattr(reg, f'UC_ARM64_REG_X{i}'), value)
        machine.reg_write(reg.UC_ARM64_REG_SP, sp)
        calls, reached, maintenance, tlbi_operands = [], [], [], []

        def hook(uc, pc, size, _):
            def value(index):
                return uc.reg_read(getattr(reg, f'UC_ARM64_REG_X{index}'))
            def finish(result=0):
                lr = value(30)
                for index in range(18):
                    uc.reg_write(getattr(reg, f'UC_ARM64_REG_X{index}'), 0xdead0000 + index)
                uc.reg_write(reg.UC_ARM64_REG_X0, result)
                uc.reg_write(reg.UC_ARM64_REG_PC, lr)
            word = int.from_bytes(uc.mem_read(pc, 4), 'little')
            if word & 0xffffffe0 == 0xd5088360:
                tlbi_operands.append(value(word & 31))
                maintenance.append('tlbi-vaae1is')
                uc.reg_write(reg.UC_ARM64_REG_PC, pc + 4)
                return
            barrier = {0xd5033a9f: 'dsb-ishst', 0xd5033b9f: 'dsb-ish', 0xd5033fdf: 'isb'}.get(word)
            if barrier:
                maintenance.append(barrier)
                uc.reg_write(reg.UC_ARM64_REG_PC, pc + 4)
                return
            if pc == base + gate:
                for index in (*range(8), 18, 29, 30):
                    assert value(index) == initial[index], (scenario, index, hex(value(index)))
                assert uc.reg_read(reg.UC_ARM64_REG_SP) == sp - 80
                reached.append(True)
                uc.emu_stop()
            elif pc == base + anchor[6]:
                calls.append('allocate')
                assert (value(1) if anchor[13] else value(0)) >= plan['memoryBytes']
                finish(stage)
            elif pc == base + anchor[7]:
                calls.append('release')
                assert value(0) == stage
                finish()
            elif pc in {base + offset for offset in anchor[8:13]}:
                if pc == base + anchor[12]:
                    pages = (value(1) - value(0)) >> anchor[3]
                    assert maintenance == ['dsb-ishst'] + ['tlbi-vaae1is'] * pages + ['dsb-ish', 'isb']
                    assert tlbi_operands == [((stage >> 12) & ((1 << 44) - 1)) +
                                             (page << (anchor[3] - 12)) for page in range(pages)]
                calls.append('permission-or-cache')
                finish()
            elif pc == stage + plan['entryOffset']:
                policy = struct.unpack('<IIQ', bytes(uc.mem_read(value(0), 16)))
                assert policy == (1, 0, base + key_at) and value(1) == name
                assert bytes(uc.mem_read(policy[2], 48)) == expected_key
                calls.append('identity-entry-fixture')
                finish(1)
        machine.hook_add(UC_HOOK_CODE, hook)
        # Execute the production five STP instructions; no test-authored stack layout.
        machine.emu_start(base + frame_at + 28, 0, count=8000000)
        assert reached, (scenario, calls)
        if scenario == 'claim':
            assert calls.count('allocate') == calls.count('release') == calls.count('identity-entry-fixture') == 1, calls
        else:
            assert not calls, (scenario, calls)
        outcomes.append(dict(scenario=scenario, returnedToOriginalGate=True, callerFramePreserved=True,
                             calls=calls, tlbiOperands=list(map(hex, tlbi_operands)),
                             maintenance=maintenance))
    return dict(passed=True, realRelayBootstrapMaterializerExecuted=True,
                kernelApisAndIdentityEntryAreFixtures=True, androidKernelExecuted=False, scenarios=outcomes,
                tlbMaintenanceRecordedAndSkipped=True)


def matrix(out, cp, args):
    from elftools.elf.elffile import ELFFile
    from elftools.elf.relocation import RelocationSection
    fixture = ROOT / 'scripts/fixtures/UltraManagerClaimCarrierMatrix.java'
    compile_run = subprocess.run([shutil.which('javac'), '--release', '21', '-cp', cp, '-d',
                                 str(out / 'classes'), str(fixture)], capture_output=True, text=True)
    (out / 'matrix-compile.log').write_text(compile_run.stdout + compile_run.stderr)
    if compile_run.returncode:
        print(compile_run.stdout + compile_run.stderr)
        return compile_run.returncode
    bootstrap = args.bootstrap.resolve() if args.bootstrap else out / 'bootstrap-1600-byte-fixture.bin'
    if not args.bootstrap:
        bootstrap.write_bytes(bytes(1600))
    materializer = (args.materializer or WORK / 'full-resolver-carrier/production-v2/loader.text.bin').resolve()
    payload_root = (args.payload_root or WORK / 'resolver-prototype').resolve()
    capacities = json.loads((WORK / 'carrier-prototype/capacity-results.json').read_text())['results']
    results = []
    for capacity in capacities:
        index = capacity['index']
        folder = out / f'image-{index}'
        source = WORK / f'carrier-prototype/image-{index}/Image'
        series = '.'.join(capacity['release'].split('-')[0].split('.')[:2])
        elf_path = payload_root / SERIES[series] / args.payload_name
        folder.mkdir(exist_ok=True)
        run = subprocess.run([shutil.which('java'), '-Xmx768m', '-cp', cp,
                              'UltraManagerClaimCarrierMatrix', str(ROOT), str(folder), str(index),
                              str(elf_path), str(bootstrap), str(materializer), str(args.policy_bytes),
                              'connected' if args.connected else 'storage'],
                             cwd=ROOT, capture_output=True, text=True, timeout=180)
        (folder / 'production-plan.log').write_text(run.stdout + run.stderr)
        print(run.stdout + run.stderr, end='', flush=True)
        if run.returncode:
            results.append(dict(index=index, release=capacity['release'], placementPassed=False,
                                failure=run.stdout + run.stderr))
            continue
        raw = source.read_bytes()
        assert SHA(raw) == capacity['rawSha256']
        # The old byte-regex capacity report can truncate an aligned zero run.
        # Rebuild only its padding boundaries; keep every recorded exclusion.
        caves = aligned_padding(raw, capacity['textStart'], capacity['textEnd'],
                                json.loads(source.with_name('exclusions.json').read_text()))
        plan = {k: int(v) for k, v in (line.split('=') for line in
                (folder / 'production-plan.txt').read_text().splitlines())}
        assert sum(end - start for start, end in caves) == plan['qualifiedPaddingBytes']
        patches = (folder / 'resolver-carrier-patches.bin').read_bytes()
        writes, image, cursor = [], bytearray(raw), 0
        for line in (folder / 'resolver-carrier-writes.tsv').read_text().splitlines():
            at, length, role = line.split('\t')
            at, length = int(at), int(length)
            assert any(start <= at and at + length <= end for start, end in caves)
            image[at:at + length] = patches[cursor:cursor + length]
            cursor += length
            writes.append(dict(offset=at, bytes=length, role=role))
        assert cursor == len(patches) and len(image) == len(raw)
        previous = 0
        for write in sorted(writes, key=lambda w: w['offset']):
            at, length = write['offset'], write['bytes']
            assert previous <= at and raw[previous:at] == image[previous:at]
            previous = at + length
        assert raw[previous:] == image[previous:]
        with elf_path.open('rb') as stream:
            elf = ELFFile(stream)
            allocated = [s for s in elf.iter_sections() if s['sh_flags'] & 2 and s['sh_size']]
            runtime = [s for s in allocated if s['sh_type'] in ('SHT_PROGBITS', 'SHT_NOBITS')
                       and s.name not in ('.eh_frame', '.eh_frame_hdr')]
            excluded_metadata = [s.name for s in allocated if s not in runtime]
            assert not any(s.name in ('.dynsym', '.dynstr', '.dynamic', '.rela.dyn', '.rela.plt',
                                     '.gnu.hash', '.hash', '.eh_frame', '.eh_frame_hdr') for s in runtime)
            assert {s.name for s in allocated if s['sh_type'] in ('SHT_RELA', 'SHT_DYNSYM', 'SHT_DYNAMIC')} <= set(excluded_metadata)
            sections = [dict(name=s.name, offset=s['sh_addr'], file=b'' if s['sh_type'] == 'SHT_NOBITS'
                            else s.data(), memory=s['sh_size'], flags=1 | (2 if s['sh_flags'] & 1 else 0) |
                            (4 if s['sh_flags'] & 4 else 0)) for s in runtime]
            sections.sort(key=lambda s: s['offset'])
            imports, import_names, relocations = [], [], []
            names = {name: int(offset) for offset, name in (line.split('\t', 1) for line in
                     source.with_name('symbols.tsv').read_text(encoding='utf-8').splitlines())}
            for table in elf.iter_sections():
                if not isinstance(table, RelocationSection):
                    continue
                symbols = elf.get_section(table['sh_link'])
                for rel in table.iter_relocations():
                    kind, addend = rel['r_info_type'], rel['r_addend']
                    if kind == 1027:
                        relocations.append([rel['r_offset'], 1, addend, 0])
                    else:
                        assert kind in (257, 1025, 1026) and addend == 0
                        name = symbols.get_symbol(rel['r_info_sym']).name
                        if name not in import_names:
                            import_names.append(name)
                            imports.append(names[name])
                        relocations.append([rel['r_offset'], 2, import_names.index(name), 0])
            entry = elf['e_entry']
        descriptor_at = plan['descriptorOffset']
        descriptor = image[descriptor_at:descriptor_at + plan['descriptorBytes']]
        table_at, count = plan['chunkTableOffset'], plan['chunkCount']
        table = image[table_at:table_at + count * 8]
        binding = list(struct.unpack_from('<6I', image, plan['bindingOffset']))
        assert binding == [len(raw), descriptor_at, len(descriptor), plan['descriptorCrc'],
                           plan['materializerOffset'], materializer.stat().st_size]
        memory = (max(s['offset'] + s['memory'] for s in sections) + 15) & ~15
        expected_header = [0x4355534d, 3, len(descriptor), len(raw), memory, entry, len(sections), count,
                           len(imports), len(relocations), zlib.crc32(b''.join(s['file'] for s in sections)), len(raw), table_at]
        assert list(struct.unpack_from('<13I', descriptor)) == expected_header
        assert zlib.crc32(table, zlib.crc32(descriptor)) == plan['descriptorCrc']
        expected_descriptor = struct.pack('<13I', *expected_header)
        expected_descriptor += b''.join(struct.pack('<4I', s['offset'], len(s['file']), s['memory'], s['flags'])
                                        for s in sections)
        expected_descriptor += struct.pack('<' + 'I' * len(imports), *imports)
        expected_descriptor += b''.join(struct.pack('<4I', *rel) for rel in relocations)
        assert descriptor == expected_descriptor
        reconstructed, expected, section, section_cursor, chunks = bytearray(memory), bytearray(memory), 0, 0, []
        for s in sections:
            expected[s['offset']:s['offset'] + len(s['file'])] = s['file']
        for at, length in struct.iter_unpack('<2I', table):
            while section_cursor == len(sections[section]['file']):
                section += 1
                section_cursor = 0
            target = sections[section]['offset'] + section_cursor
            assert length and section_cursor + length <= len(sections[section]['file'])
            reconstructed[target:target + length] = image[at:at + length]
            section_cursor += length
            chunks.append([at, target, length])
        assert reconstructed == expected
        connected = replay_connected(image, plan, folder, bootstrap.stat().st_size) if args.connected else None
        (folder / 'resolver-expected.bin').write_bytes(expected)
        (folder / 'resolver-relocations.tsv').write_text(''.join(f'{a}\t{b}\t{c}\n' for a, b, c, _ in relocations))
        result = dict(index=index, release=capacity['release'], protocolVersion=3, placementPassed=True,
                      patchDataFile='resolver-carrier-patches.bin', patchDataSha256=SHA(patches),
                      sourceSha256=SHA(raw), derivedSha256=SHA(image), binding=binding,
                      loaderEntryOffset=plan['materializerOffset'], loaderBytes=len(materializer.read_bytes()),
                      sectionCount=len(sections), importCount=len(imports), relocationCount=len(relocations),
                      imports=imports, importNames=import_names, relocations=relocations,
                      sections=[{k: v for k, v in s.items() if k != 'file'} | dict(fileBytes=len(s['file'])) for s in sections],
                      writes=writes, chunks=chunks, **plan,
                      payloadPath=elf_path.relative_to(ROOT).as_posix(), payloadSha256=SHA(elf_path.read_bytes()),
                      sourceUnchanged=SHA(source.read_bytes()) == SHA(raw), allOtherBytesUnchanged=True,
                      exactSectionReadback=True, onlyQualifiedPaddingWritten=True, sizeUnchanged=True,
                      bootstrapFixture=args.bootstrap is None or args.bootstrap_fixture, kernelExecuted=False, hookInstalled=False,
                      sourcePlusPatches=True, derivedImageFileWritten=False)
        result['excludedMetadataSections'] = excluded_metadata
        result['onlyRuntimeSectionsRetained'] = True
        if connected:
            result['connectedExecution'] = connected
        (folder / 'resolver-carrier.json').write_text(json.dumps(result, indent=2) + '\n')
        results.append(result)
    passed = sum(r['placementPassed'] for r in results)
    (out / 'placement-results.json').write_text(json.dumps(dict(
        protocolVersion=3, placementPassedCount=passed, inputCount=len(results), results=results,
        productionKotlinCarrierExecuted=True, independentElfReadback=True,
        bootstrapFixture=args.bootstrap is None or args.bootstrap_fixture,
        bootstrapPath=str(bootstrap), bootstrapSha256=SHA(bootstrap.read_bytes()),
        bootstrapBytes=bootstrap.stat().st_size, policyBytes=args.policy_bytes, kernelExecuted=False,
        bootEntrypointInstalled=False), indent=2) + '\n')
    print(f'production Kotlin carrier: {passed}/{len(results)} placements')
    return 0 if passed == len(results) == 17 else 1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--build', type=Path, required=True)
    parser.add_argument('--matrix', action='store_true')
    parser.add_argument('--bootstrap', type=Path)
    parser.add_argument('--bootstrap-fixture', action='store_true')
    parser.add_argument('--materializer', type=Path)
    parser.add_argument('--payload-root', type=Path)
    parser.add_argument('--payload-name', default='resolver.so')
    parser.add_argument('--policy-bytes', type=int, default=96)
    parser.add_argument('--connected', action='store_true', help='Replay real relay/bootstrap/materializer with explicit kernel API fixtures')
    args = parser.parse_args()
    padding_boundary_regression()
    out = args.build.resolve()
    out.mkdir(parents=True, exist_ok=True)
    classes = out / 'classes'
    classes.mkdir(exist_ok=True)
    sys.path.insert(0, str(ROOT / 'tools/specgen'))
    from specgen.product import cached_jar
    deps = [cached_jar(*entry) for entry in (
        ('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.20'), ('org.jetbrains', 'annotations', '13.0'),
        ('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.11.0'),
        ('org.jetbrains.kotlinx', 'kotlinx-serialization-core-jvm', '1.11.0'),
        ('org.jetbrains.kotlinx', 'kotlinx-serialization-json-jvm', '1.11.0'),
        ('junit', 'junit', '4.13.2'), ('org.hamcrest', 'hamcrest-core', '1.3'))]
    compiler = cached_jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.4.20')
    product = ROOT / 'manager/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
    java_product = ROOT / 'manager/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
    base = ROOT / 'manager/src/main/java/dev/maplesu/manager/ultrakernel'
    sources = [base / name for name in (
        'UltraKernelKallsyms.kt', 'UltraKernelA64.kt', 'UltraKernelCaveAllocator.kt',
        'UltraKernelGenerateException.kt', 'UltraKernelManagerClaimPayload.kt',
        'UltraKernelManagerClaimCarrier.kt', 'UltraKernelSpecGenerator.kt')]
    sources += [ROOT / 'manager/src/test/java/dev/maplesu/manager/ultrakernel/UltraKernelManagerClaimCarrierTest.kt']
    digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    before = {path.relative_to(ROOT).as_posix(): digest(path) for path in sources}
    cp = os.pathsep.join(map(str, [classes, product, java_product, *deps]))
    commands = [
        [shutil.which('java'), '-Xmx768m', '-cp', os.pathsep.join(map(str, [compiler, *deps])),
         'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '21',
         '-Xfriend-paths=' + str(product), '-classpath', cp, '-d', str(classes), *map(str, sources)],
        [shutil.which('java'), '-cp', cp, 'org.junit.runner.JUnitCore',
         'dev.maplesu.manager.ultrakernel.UltraKernelManagerClaimCarrierTest'],
    ]
    code = 1
    for index, command in enumerate(commands):
        run = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, encoding='utf-8',
                             errors='replace', timeout=180)
        (out / f'step-{index}.log').write_text(run.stdout + run.stderr, encoding='utf-8')
        print(run.stdout + run.stderr, end='')
        code = run.returncode
        if code:
            break
    if code == 0 and args.matrix:
        code = matrix(out, cp, args)
    unchanged = before == {path.relative_to(ROOT).as_posix(): digest(path) for path in sources}
    (out / 'tests.json').write_text(json.dumps(dict(
        passed=code == 0 and unchanged, exitCode=code, sourcesUnchanged=unchanged, sourceSha256=before,
        productionCarrierCompiled=True, existingProductTypesUsed=True,
        alignedPaddingBoundaryRegressionPassed=True,
        gradleExecuted=False, androidRuntimeExecuted=False, deviceAccess=False), indent=2) + '\n')
    return code if unchanged else 1


if __name__ == '__main__':
    raise SystemExit(main())
