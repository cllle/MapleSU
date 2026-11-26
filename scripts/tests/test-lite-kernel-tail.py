"""Run the real LiteBootPatcher private encoding seam without Android/native process substitution."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools/specgen"))
from specgen.product import cached_jar

ROOT = Path(__file__).resolve().parents[2]


def identity(path):
    data = Path(path).read_bytes()
    return {'path': str(path), 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--runtime-receipt', type=Path,
                        default=Path('C:/Temp/MapleSU-P2-securebits-host-r3/receipt.json'))
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    runtime = json.loads(args.runtime_receipt.read_text(encoding='utf-8-sig'))['runtime']
    dependencies = [runtime['java'], runtime['jar'], *runtime['dependencies'], *runtime['buildDependencies']]
    for record in dependencies:
        assert identity(record['path']) == record, 'runtime dependency changed: ' + record['path']
    java = Path(runtime['java']['path'])
    javac = java.with_name('javac' + ('.exe' if os.name == 'nt' else ''))
    compiler = next(Path(rec['path']) for rec in runtime['buildDependencies'] if 'kotlin-compiler-embeddable' in rec['path'])
    coroutines = next(Path(rec['path']) for rec in runtime['buildDependencies'] if 'kotlinx-coroutines-core-jvm' in rec['path'])
    junit = list(cached_jar('junit', 'junit', '4.13.2').parent.parent.glob("*/*.jar"))
    hamcrest = list(cached_jar('org.hamcrest', 'hamcrest-core', '1.3').parent.parent.glob("*/*.jar"))
    assert len(junit) == len(hamcrest) == 1
    dependencies.extend([identity(junit[0]), identity(hamcrest[0])])
    production = ROOT / 'manager/src/main/java/dev/maplesu/manager/patching/LiteBootPatcher.kt'
    tests = ROOT / 'manager/src/test/java/dev/maplesu/manager/patching/LiteKernelComponentTest.kt'
    java_sources = sorted((ROOT / 'image-core/src/main/java').rglob('*.java'))
    source_records = [identity(path) for path in [*java_sources, production, tests, Path(__file__).resolve()]]
    frozen = {}
    for record in source_records:
        path = Path(record['path'])
        target = args.out / 'source' / path.relative_to(ROOT)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(path.read_bytes())
        frozen[path] = target
    # Only compile-time resource constants are substituted. No Android or patcher method is stubbed.
    resources = args.out / 'stubs/com/maplesu/core/R.java'
    resources.parent.mkdir(parents=True)
    resources.write_text('package com.maplesu.core; public final class R { public static final class string { '
                         'public static final int install_patch_reading=1, install_patch_applying=2, '
                         'install_patch_rebuilding=3, install_patch_verifying=4; } }\n')
    receipt = {'schema': 'maplesu-lite-kernel-tail/1', 'sources': source_records,
               'runtimeReceipt': identity(args.runtime_receipt), 'runtime': runtime,
               'testDependencies': dependencies[-2:], 'resourceCompileStub': identity(resources), 'commands': [],
               'deviceOperations': False, 'SDKOrAPKBuild': False, 'nativePatchExecuted': False,
               'privateEncodingSeamOnly': True}

    def run(label, argv):
        done = subprocess.run(list(map(str, argv)), stdin=subprocess.DEVNULL, capture_output=True)
        for name in ('stdout', 'stderr'):
            (args.out / (label + '.' + name + '.log')).write_bytes(getattr(done, name))
        receipt['commands'].append({'label': label, 'argv': list(map(str, argv)), 'exitCode': done.returncode})
        return done.returncode

    classes = args.out / 'classes'
    classes.mkdir()
    test_jar = args.out / 'tests.jar'
    kotlin_boot = os.pathsep.join([str(compiler), *runtime['classpath'].split(os.pathsep)[:2], str(coroutines)])
    classpath = os.pathsep.join([str(classes), runtime['classpath'], str(junit[0]), str(hamcrest[0])])
    exit_code = run('javac', [javac, '--release', '17', '-encoding', 'UTF-8', '-d', classes,
                            *[frozen[path] for path in java_sources], resources])
    if not exit_code:
        exit_code = run('kotlin', [java, '-Xmx768m', '-cp', kotlin_boot, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                                 '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-classpath', classpath,
                                 '-d', test_jar, frozen[production], frozen[tests]])
    if not exit_code:
        exit_code = run('tests', [java, '-Xmx768m', '-cp', str(test_jar) + os.pathsep + classpath,
                                'org.junit.runner.JUnitCore', 'dev.maplesu.manager.patching.LiteKernelComponentTest'])
    receipt['exitCode'] = exit_code
    receipt['sourcesUnchanged'] = source_records == [identity(Path(rec['path'])) for rec in source_records]
    receipt['runtimeUnchanged'] = dependencies == [identity(Path(rec['path'])) for rec in dependencies]
    if test_jar.is_file():
        receipt['testJar'] = identity(test_jar)
    (args.out / 'receipt.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'out': str(args.out), 'exitCode': exit_code, 'sourcesUnchanged': receipt['sourcesUnchanged']}))
    return exit_code


if __name__ == '__main__':
    raise SystemExit(main())
