"""Narrow-compile the current entry classifier and replay its emitted static-SCS hook."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import struct
import subprocess

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools/specgen"))
from specgen.product import cached_jar

ROOT = Path(__file__).resolve().parents[2]


def identity(path):
    path = Path(path)
    raw = path.read_bytes()
    return {'path': str(path), 'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()}


def replay(path, null_filename=False):
    from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE, UC_HOOK_MEM_WRITE
    from unicorn import arm64_const as r
    data = json.loads(path.read_text('utf-8'))
    results = []
    for patched in (False, True):
        uc = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
        entry, frame, gate = (data[key] for key in ('entry', 'frame', 'gate'))
        code_end = max(entry + 64, frame + len(bytes.fromhex(data['frameBytes'])),
                       gate + len(bytes.fromhex(data['gateBytes'])))
        code_limit = max(0x1200000, (code_end + 0xffff) & ~0xffff)
        uc.mem_map(0, code_limit)
        uc.mem_write(entry, bytes.fromhex(data['patchedEntry' if patched else 'original']))
        uc.mem_write(frame, bytes.fromhex(data['frameBytes']))
        uc.mem_write(gate, bytes.fromhex(data['gateBytes']))
        # Exercise the real gate's ordinary unprivileged filename path, not a stub gate.
        task = max(0x1400000, code_limit)
        cred, filename, name = task + 0x1000, task + 0x2000, task + 0x3000
        stack, shadow = task + 0x100000, task + 0x200000
        uc.mem_map(task, 0x10000)
        uc.mem_map(stack, 0x10000)
        uc.mem_map(shadow, 0x1000)
        uc.mem_write(task + data.get('taskCred', 0x820), struct.pack('<Q', cred))
        uc.mem_write(cred + data.get('credEuid', 24), struct.pack('<I', 10000))
        uc.mem_write(filename, struct.pack('<Q', name))
        uc.mem_write(name, b'ordinary-app\0')
        registers = list(range(31))
        initial = {index: 0x10000 + index for index in registers}
        initial[1], initial[18] = 0 if null_filename else filename, shadow + 0x100
        for index, value in initial.items():
            uc.reg_write(getattr(r, 'UC_ARM64_REG_X' + str(index)), value)
        uc.reg_write(r.UC_ARM64_REG_SP, stack + 0x8000)
        uc.reg_write(r.UC_ARM64_REG_SP_EL0, task)
        visits, writes = [], []

        def code(machine, address, size, context):
            visits.append(address)
            if address == entry + 8:
                machine.emu_stop()

        def write(machine, access, address, size, value, context):
            if shadow <= address < shadow + 0x1000:
                writes.append({'address': address, 'size': size, 'value': value})

        uc.hook_add(UC_HOOK_CODE, code)
        uc.hook_add(UC_HOOK_MEM_WRITE, write)
        uc.emu_start(entry, entry + 64, count=500)
        assert uc.reg_read(r.UC_ARM64_REG_PC) == entry + 8
        assert uc.reg_read(r.UC_ARM64_REG_SP) == stack + 0x8000 - data.get('stackBytes', 112)
        assert uc.reg_read(r.UC_ARM64_REG_X18) == initial[18] + 8
        assert writes == [{'address': initial[18], 'size': 8, 'value': initial[30]}]
        for index in list(range(8)) + [18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30]:
            assert uc.reg_read(getattr(r, 'UC_ARM64_REG_X' + str(index))) == initial[index] + (8 if index == 18 else 0)
        assert visits.count(entry) == 1
        replay_at = data['nullReplay'] if patched and null_filename else frame + 20 if patched else entry + 4
        assert visits.count(replay_at) == 1
        results.append({'patched': patched, 'sp': uc.reg_read(r.UC_ARM64_REG_SP),
                        'lr': uc.reg_read(r.UC_ARM64_REG_X30), 'x18': uc.reg_read(r.UC_ARM64_REG_X18),
                        'resume': uc.reg_read(r.UC_ARM64_REG_PC), 'shadowWrites': writes,
                        'entryExecutedOnce': True, 'shadowPushExecutedOnce': True,
                        'realGateExecuted': gate in visits})
    assert results[1]['realGateExecuted'] is (not null_filename)
    return {'engine': 'Unicorn', 'scope': 'actual emitted frame and ordinary unprivileged gate path',
            'statesEqual': all(results[0][key] == results[1][key] for key in ('sp', 'lr', 'x18', 'resume', 'shadowWrites')),
            'cases': results}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--runtime-receipt', type=Path, default=ROOT /
                        'docs/dual-mode/impl/_work/2026-11-15-p2/evidence/embedded-real-lifecycle-r2/effective-runtime.json')
    args = parser.parse_args()
    args.out = args.out.resolve()
    args.out.mkdir(parents=True, exist_ok=False)
    runtime = json.loads(args.runtime_receipt.read_text('utf-8-sig'))['runtime']
    dependencies = [runtime['java'], runtime['jar'], *runtime['dependencies'], *runtime['buildDependencies']]
    assert all(identity(item['path']) == item for item in dependencies)
    compiler = next(item['path'] for item in runtime['buildDependencies'] if 'kotlin-compiler-embeddable' in item['path'])
    coroutines = next(item['path'] for item in runtime['buildDependencies'] if 'kotlinx-coroutines-core-jvm' in item['path'])
    junit = cached_jar('junit', 'junit', '4.13.2')
    hamcrest = cached_jar('org.hamcrest', 'hamcrest-core', '1.3')
    dependencies.extend([identity(junit), identity(hamcrest)])
    real_scs_fixture = ROOT / 'Log_Reference/11.6/Original.img'
    test_names = ['UltraKernelStaticScsEntryTest']
    paths = [ROOT / 'manager/src/main/java/dev/maplesu/manager/ultrakernel/UltraKernelRecipe.kt',
             ROOT / 'manager/src/test/java/dev/maplesu/manager/ultrakernel/UltraKernelPlanFixtures.kt',
             ROOT / 'manager/src/test/java/dev/maplesu/manager/ultrakernel/UltraKernelStaticScsEntryTest.kt']
    if real_scs_fixture.is_file():
        test_names.append('UltraKernelDynamicScsBootTest')
        paths.append(ROOT / 'manager/src/test/java/dev/maplesu/manager/ultrakernel/UltraKernelDynamicScsBootTest.kt')
    sources, frozen = [identity(path) for path in paths], []
    for path in paths:
        target = args.out / 'source' / path.relative_to(ROOT)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(path.read_bytes())
        frozen.append(target)
    receipt = {'schema': 'maplesu-ultra-static-scs-entry/1', 'sources': sources,
               'runner': identity(__file__), 'runtimeReceipt': identity(args.runtime_receipt),
               'runtime': runtime, 'testDependencies': dependencies[-2:], 'commands': [],
               'deviceTest': False, 'partitionWrites': False, 'SDKOrAPKBuild': False,
               'testClasses': test_names,
               'realDynamicScsFixture': {'path': str(real_scs_fixture), 'present': real_scs_fixture.is_file()},
               'realDynamicScsRegressionPerformed': real_scs_fixture.is_file()}
    jar, dump = args.out / 'tests.jar', args.out / 'emitted.json'
    classpath = os.pathsep.join([runtime['jar']['path'], runtime['classpath'], str(junit), str(hamcrest)])

    def run(label, argv):
        done = subprocess.run(list(map(str, argv)), cwd=ROOT / 'manager', stdin=subprocess.DEVNULL, capture_output=True)
        for stream in ('stdout', 'stderr'):
            (args.out / (label + '.' + stream + '.log')).write_bytes(getattr(done, stream))
        receipt['commands'].append({'label': label, 'argv': list(map(str, argv)), 'exitCode': done.returncode})
        return done.returncode

    code = run('compile', [runtime['java']['path'], '-Xmx768m', '-cp', os.pathsep.join([
        compiler, *runtime['classpath'].split(os.pathsep)[:2], coroutines]),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17',
        '-Xfriend-paths=' + runtime['jar']['path'], '-classpath', classpath, '-d', jar, *frozen])
    if code == 0:
        code = run('tests', [runtime['java']['path'], '-Xmx768m', '-Dmaplesu.scs.dump=' + str(dump),
            '-cp', str(jar) + os.pathsep + classpath, 'org.junit.runner.JUnitCore',
            *['dev.maplesu.manager.ultrakernel.' + name for name in test_names]])
    if code == 0:
        receipt['instructionReplay'] = replay(dump)
    receipt.update(exitCode=code, sourcesUnchanged=sources == [identity(path) for path in paths],
                   runtimeUnchanged=all(identity(item['path']) == item for item in dependencies))
    if jar.is_file():
        receipt['testJar'] = identity(jar)
    (args.out / 'receipt.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'out': str(args.out), 'exitCode': code, 'sourcesUnchanged': receipt['sourcesUnchanged']}))
    return code


if __name__ == '__main__':
    raise SystemExit(main())
