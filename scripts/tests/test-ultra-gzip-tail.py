"""Run the gzip-tail regression against frozen real Java/core and Kotlin/builder sources."""
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
    parser.add_argument('--source-root', type=Path, default=ROOT)
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
    host = Path(runtime['jar']['path'])
    jars = list(cached_jar('junit', 'junit', '4.13.2').parent.parent.glob("*/*.jar"))
    hamcrest = list(cached_jar('org.hamcrest', 'hamcrest-core', '1.3').parent.parent.glob("*/*.jar"))
    assert len(jars) == len(hamcrest) == 1
    dependencies.extend([identity(jars[0]), identity(hamcrest[0])])
    compiler = next(Path(rec['path']) for rec in runtime['buildDependencies'] if 'kotlin-compiler-embeddable' in rec['path'])
    coroutines = next(Path(rec['path']) for rec in runtime['buildDependencies'] if 'kotlinx-coroutines-core-jvm' in rec['path'])
    java_sources = sorted((args.source_root / 'image-core/src/main/java').rglob('*.java'))
    java_sources.append(ROOT / 'image-core/src/test/java/dev/maplesu/image/KernelComponentGzipTest.java')
    kotlin_sources = [args.source_root / 'manager/src/main/java/dev/maplesu/manager/ultrakernel/UltraKernelBuilder.kt',
                      args.source_root / 'manager/src/main/java/dev/maplesu/manager/ultrakernel/UltraKernelOriginalRecovery.kt',
                      ROOT / 'manager/src/test/java/dev/maplesu/manager/ultrakernel/UltraKernelGzipTailTest.kt',
                      ROOT / 'manager/src/test/java/dev/maplesu/manager/ultrakernel/UltraKernelGeneratedRecoveryTest.kt']
    resources = sorted(path for path in (ROOT / 'image-core/src/test/resources').rglob('*') if path.is_file())
    source_records, frozen_java, frozen_kotlin = [], [], []
    for paths, destination in ((java_sources, frozen_java), (kotlin_sources, frozen_kotlin), (resources, [])):
        for path in paths:
            logical = path.relative_to(args.source_root) if path.is_relative_to(args.source_root) else path.relative_to(ROOT)
            target = args.out / 'source' / logical
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(path.read_bytes())
            source_records.append(identity(path))
            destination.append(target)
    receipt = {'schema': 'maplesu-gzip-kernel-tail/1', 'sources': source_records, 'runner': identity(__file__),
               'runtimeReceipt': identity(args.runtime_receipt), 'runtime': runtime, 'commands': [],
               'deviceOperations': False, 'SDKOrAPKBuild': False, 'isolatedSourceRoot': str(args.source_root)}

    def run(label, argv):
        done = subprocess.run(list(map(str, argv)), stdin=subprocess.DEVNULL, capture_output=True)
        for name in ('stdout', 'stderr'):
            (args.out / (label + '.' + name + '.log')).write_bytes(getattr(done, name))
        receipt['commands'].append({'label': label, 'argv': list(map(str, argv)), 'exitCode': done.returncode})
        return done.returncode

    classes = args.out / 'image-classes'
    classes.mkdir()
    test_jar = args.out / 'tests.jar'
    kotlin_boot = os.pathsep.join([str(compiler), *runtime['classpath'].split(os.pathsep)[:2], str(coroutines)])
    classpath = os.pathsep.join([str(classes), str(args.out / 'source/image-core/src/test/resources'),
                                str(host), runtime['classpath'], str(jars[0]), str(hamcrest[0])])
    exit_code = run('javac', [javac, '--release', '17', '-encoding', 'UTF-8', '-d', classes, *frozen_java])
    if not exit_code:
        exit_code = run('codec', [java, '-Xmx768m', '-cp', classes, 'dev.maplesu.image.KernelComponentGzipTest'])
    if not exit_code:
        exit_code = run('kotlin', [java, '-Xmx768m', '-cp', kotlin_boot, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                                 '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-Xfriend-paths=' + str(host),
                                 '-classpath', classpath, '-d', test_jar, *frozen_kotlin])
    if not exit_code:
        exit_code = run('tests', [java, '-Xmx768m', '-cp', str(test_jar) + os.pathsep + classpath,
                                'org.junit.runner.JUnitCore', 'dev.maplesu.manager.ultrakernel.UltraKernelGzipTailTest',
                                'dev.maplesu.manager.ultrakernel.UltraKernelGeneratedRecoveryTest'])
    receipt['exitCode'] = exit_code
    receipt['sourcesUnchanged'] = source_records == [identity(Path(rec['path'])) for rec in source_records]
    receipt['runtimeUnchanged'] = dependencies == [identity(Path(rec['path'])) for rec in dependencies]
    receipt['testDependencies'] = dependencies[-2:]
    if test_jar.is_file():
        receipt['testJar'] = identity(test_jar)
    (args.out / 'receipt.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'out': str(args.out), 'exitCode': exit_code, 'sourcesUnchanged': receipt['sourcesUnchanged']}))
    return exit_code


if __name__ == '__main__':
    raise SystemExit(main())
