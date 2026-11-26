"""Compile only the production kallsyms decoder and its NGKI regression seam."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import jdk_tool

ROOT = Path(__file__).resolve().parents[2]
sys.dont_write_bytecode = True


def identity(path):
    data = Path(path).read_bytes()
    return dict(path=str(Path(path).resolve()), bytes=len(data), sha256=hashlib.sha256(data).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--fixture', required=True, type=Path)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    fixture = args.fixture.resolve()
    spec = importlib.util.spec_from_file_location('product_ngki_test', ROOT / 'tools/specgen/specgen/product.py')
    product = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(product)
    jar = product.cached_jar
    java = jdk_tool()
    compiler = jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.4.20')
    stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.20')
    annotations = jar('org.jetbrains', 'annotations', '13.0')
    coroutines = jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.11.0')
    deps = [stdlib, annotations, jar('junit', 'junit', '4.13.2'), jar('org.hamcrest', 'hamcrest-core', '1.3')]
    compiler_classpath = os.pathsep.join(str(p) for p in (compiler, stdlib, annotations, coroutines))
    sources = [ROOT / 'manager/src/main/java/dev/maplesu/manager/ultrakernel' / name for name in (
        'UltraKernelKallsyms.kt', 'UltraKernelA64.kt', 'UltraKernelGenerateException.kt')]
    sources += [ROOT / 'manager/src/test/java/dev/maplesu/manager/ultrakernel/UltraKernelKallsymsNgkiTest.kt']
    snapshots = []
    for path in sources:
        target = out / 'source' / path.relative_to(ROOT)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(path.read_bytes())
        snapshots.append(dict(origin=identity(path), snapshot=identity(target)))
    inputs = [identity(path) for path in [java, compiler, coroutines, *deps, Path(__file__),
        fixture / 'target-5.4-kallsyms.zlib', fixture / 'target-5.4-header.bin', fixture / 'target-5.4.json',
        fixture / 'target-5.4-oracle.tsv.zlib', fixture / 'ORACLE.json']]
    output = out / 'tests.jar'
    classpath = os.pathsep.join(str(path) for path in deps)
    compile_command = [str(java), '-Xmx512m', '-cp', compiler_classpath,
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17',
        '-classpath', classpath, '-d', str(output), *[row['snapshot']['path'] for row in snapshots]]
    test_command = [str(java), '-Xmx512m', '-Dmaplesu.ngki.fixture=' + str(fixture), '-cp',
        str(output) + os.pathsep + classpath, 'org.junit.runner.JUnitCore',
        'dev.maplesu.manager.ultrakernel.UltraKernelKallsymsNgkiTest']
    commands = []
    final = None
    for label, command in [('compile', compile_command), ('tests', test_command)]:
        result = subprocess.run(command, cwd=ROOT, capture_output=True)
        (out / (label + '.stdout.log')).write_bytes(result.stdout)
        (out / (label + '.stderr.log')).write_bytes(result.stderr)
        commands.append(dict(label=label, argv=command, exitCode=result.returncode))
        print(label + '_exit=' + str(result.returncode), flush=True)
        print((result.stdout + result.stderr).decode('utf-8', errors='replace'), flush=True)
        final = result.returncode
        if final:
            break
    receipt = dict(schema='maplesu-ngki-kallsyms-narrow-test/1', commands=commands,
        sources=snapshots, inputs=inputs, inputsUnchanged=all(identity(row['path']) == row for row in inputs),
        sourceSnapshotsUnchanged=all(identity(row['snapshot']['path']) == row['snapshot'] for row in snapshots),
        originsUnchanged=all(identity(row['origin']['path']) == row['origin'] for row in snapshots),
        testJar=identity(output) if output.exists() else None, exitCode=final,
        wholeImageBuild=False, SDKOrAPKBuild=False, deviceOperations=False)
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    return final


if __name__ == '__main__':
    raise SystemExit(main())
