#!/usr/bin/env python3
"""Compile the real root-task consumers and run their focused routing tests."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import jdk_tool

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/specgen'))
from specgen.product import cached_jar


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    production = ROOT / '.tmp/p2-boot-initboot-closeout-20261118/validation/manager/current-production-buildconfig/manager/intermediates'
    cached = production / 'built_in_kotlinc/debug/compileDebugKotlin/classes'
    java_cached = production / 'javac/debug/compileDebugJavaWithJavac/classes'
    libraries = json.loads((ROOT / '.tmp/p0-mode-a-tests/compile-inputs.json').read_text())['libraries']
    stdlib = cached_jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.20')
    annotations = cached_jar('org.jetbrains', 'annotations', '13.0')
    coroutines = cached_jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.11.0')
    junit = cached_jar('junit', 'junit', '4.13.2')
    hamcrest = cached_jar('org.hamcrest', 'hamcrest-core', '1.3')
    compiler = cached_jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.4.20')
    java = jdk_tool()
    sources = [ROOT / p for p in [
        'manager/src/main/java/dev/maplesu/manager/runtime/RootManagement.kt',
        'manager/src/main/java/dev/maplesu/manager/runtime/SdkRootShellClient.kt',
        'manager/src/main/java/dev/maplesu/manager/modules/ModuleRootShell.kt',
        'manager/src/test/java/dev/maplesu/manager/runtime/RootManagementStartTest.kt',
        'manager/src/test/java/dev/maplesu/manager/modules/ModuleRootShellTest.kt',
    ]]
    classes = out / 'classes'
    classes.mkdir(exist_ok=True)
    cp = os.pathsep.join(map(str, [classes, stdlib, annotations, coroutines, junit, hamcrest, cached, java_cached, *libraries]))
    arguments = ['-no-stdlib', '-no-reflect', '-jvm-target', '21', '-module-name', 'manager',
        '-Xfriend-paths=' + str(cached), '-classpath', cp, '-d', str(classes), *map(str, sources)]
    argfile = out / 'compile.args'
    argfile.write_text('\n'.join(json.dumps(a.replace('\\', '/')) for a in arguments) + '\n')
    commands = [[str(java), '-Xmx384m', '-cp', os.pathsep.join(map(str, [compiler, stdlib, annotations, coroutines])),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '@' + str(argfile)],
        [str(java), '-cp', cp, 'org.junit.runner.JUnitCore',
            'dev.maplesu.manager.runtime.RootManagementStartTest', 'dev.maplesu.manager.modules.ModuleRootShellTest']]
    rows = []
    for label, command in zip(['compile', 'tests'], commands):
        result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=90)
        (out / (label + '.log')).write_text(result.stdout + result.stderr)
        rows.append(dict(label=label, argv=command, exitCode=result.returncode))
        print(label + ': ' + str(result.returncode) + '\n' + result.stdout + result.stderr)
        if result.returncode:
            break
    report = dict(passed=rows[-1]['label']=='tests' and rows[-1]['exitCode']==0, commands=rows,
        sources=[dict(path=str(p),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in sources],
        scope='Actual complete RootManagement/SdkRootShellClient/ModuleRootShell compiled against Android API37, real libsu and cached project dependencies; routing tests use Process/executor doubles. No device/Binder/root execution.')
    (out / 'results.json').write_text(json.dumps(report, indent=2) + '\n')
    return rows[-1]['exitCode']


if __name__ == '__main__':
    sys.exit(main())
