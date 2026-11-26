"""Exercise current mode-switch source recovery against verified current image consumers."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import jdk_tool

ROOT = Path(__file__).resolve().parents[2]


def identity(path):
    path = Path(path)
    data = path.read_bytes()
    return {'path': str(path), 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--consumer-receipt', type=Path,
                        default=Path('C:/Temp/MapleSU-P2-embedded-recovery-r1/final/receipt.json'))
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    consumer = json.loads(args.consumer_receipt.read_bytes())
    runtime = consumer['runtime']
    verified = [consumer['testJar'], *consumer['imageClasses'], *consumer['sources'],
                runtime['java'], runtime['jar'], *runtime['dependencies'], *runtime['buildDependencies'],
                *consumer['testDependencies']]
    for record in verified:
        assert identity(record['path']) == record, 'verified input changed: ' + record['path']
    base = ROOT / '.tmp/p1-delivery-r7/gradle-build/manager'
    cached = base / 'intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
    inputs = json.loads((ROOT / '.tmp/p0-mode-a-tests/compile-inputs.json').read_bytes())
    names = ['workmode/service/ModeSwitchSourceRecovery.kt',
             'workmode/service/ModeSwitchCandidateImage.kt', 'workmode/WorkMode.kt',
             'workmode/switch/ModeSwitchTypes.kt', 'workmode/switch/ModeSwitchPreflight.kt',
             'installer/InstallTargetPartition.kt', 'installer/KsuKernelPreservation.kt', 'ksu/KsuInstallProducer.kt']
    sources = [ROOT / 'manager/src/main/java/dev/maplesu/manager' / name for name in names]
    sources += [ROOT / 'manager/src/ksu/java/dev/maplesu/manager/ksu' / name
                for name in ['KsuCommandBackend.kt', 'KsuSupport.kt']]
    sources += [ROOT / 'manager/src/test/java/dev/maplesu/manager' / name for name in
                ['workmode/service/ModeSwitchSourceRecoveryTest.kt', 'ksu/KsuImageFixtures.kt']]
    source_records, frozen = [], []
    for source in sources:
        target = args.out / 'source' / source.relative_to(ROOT)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(source.read_bytes())
        source_records.append(identity(source))
        frozen.append(target)
    runner = args.out / 'runner.py'
    runner.write_bytes(Path(__file__).read_bytes())
    java = jdk_tool()
    runtime_java = identity(java)
    compiler = next(rec['path'] for rec in runtime['buildDependencies'] if 'kotlin-compiler-embeddable' in rec['path'])
    coroutines = next(rec['path'] for rec in runtime['buildDependencies'] if 'kotlinx-coroutines-core-jvm' in rec['path'])
    compiler_cp = os.pathsep.join([compiler, *runtime['classpath'].split(os.pathsep)[:2], coroutines])
    classes = args.out / 'classes'
    classes.mkdir()
    classpath = os.pathsep.join(map(str, [classes, consumer['testJar']['path'], consumer['imageClassesDirectory'],
        runtime['jar']['path'], cached, base / 'intermediates/javac/debug/compileDebugJavaWithJavac/classes',
        ROOT / '.tmp/p0-mode-a-tests/b2-wiring.jar', ROOT / '.tmp/p0-mode-a-tests/c-installer-classes',
        *inputs['libraries'], *[rec['path'] for rec in consumer['testDependencies']]]))
    friends = ','.join([str(cached), consumer['testJar']['path'], runtime['jar']['path']])
    receipt = {'schema': 'maplesu-mode-switch-source-recovery/1', 'consumerReceipt': identity(args.consumer_receipt),
               'sources': source_records, 'runner': identity(__file__), 'commands': [],
               'java': runtime_java,
               'deviceOperations': False, 'SDKOrAPKBuild': False,
               'classpathOrder': 'Current switch classes, verified current recovery/image classes, frozen host, cached dependencies'}

    def run(label, argv):
        done = subprocess.run(list(map(str, argv)), cwd=ROOT / 'manager', stdin=subprocess.DEVNULL,
                              capture_output=True)
        for name in ('stdout', 'stderr'):
            (args.out / (label + '.' + name + '.log')).write_bytes(getattr(done, name))
        receipt['commands'].append({'label': label, 'argv': list(map(str, argv)), 'exitCode': done.returncode})
        print(done.stdout.decode('utf-8', 'replace'), end='')
        print(done.stderr.decode('utf-8', 'replace'), end='')
        return done.returncode

    exit_code = run('compile', [java, '-Xmx768m', '-cp', compiler_cp,
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17',
        '-Xfriend-paths=' + friends, '-classpath', classpath, '-d', classes, *frozen])
    if not exit_code:
        exit_code = run('tests', [java, '-Xmx768m', '-cp', classpath, 'org.junit.runner.JUnitCore',
            'dev.maplesu.manager.workmode.service.ModeSwitchSourceRecoveryTest'])
    receipt['exitCode'] = exit_code
    receipt['sourcesUnchanged'] = source_records == [identity(rec['path']) for rec in source_records]
    receipt['dependenciesUnchanged'] = verified == [identity(rec['path']) for rec in verified]
    receipt['classes'] = [identity(path) for path in sorted(classes.rglob('*.class'))]
    (args.out / 'receipt.json').write_bytes((json.dumps(receipt, ensure_ascii=False, indent=2) + '\n').encode())
    return exit_code if receipt['sourcesUnchanged'] and receipt['dependenciesUnchanged'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
