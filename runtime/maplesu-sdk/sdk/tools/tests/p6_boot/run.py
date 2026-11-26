#!/usr/bin/env python3
"""Exercise actual boot/session sources with real Linux fork/exec/flock and NDK compile."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[5]
SDK = ROOT / 'runtime/maplesu-sdk/sdk'
IMAGE = 'sha256:272ada1e61c525116032246df2a790d1ccec1804b7a9e0b09203c5e650dcb78e'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--boot-source', type=Path, help='Compare a focused helper_boot candidate inside the repository')
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    sources = [(args.boot_source or SDK / 'src/helper/helper_boot.cpp').resolve(),
               SDK / 'src/helper/helper_session.cpp']
    inputs = [*HERE.glob('*.*'), *sources, SDK / 'src/helper/helper_text.cpp',
              SDK / 'include/maplesu_storage_worker_private.h',
              SDK / 'include/maplesu_module_environment_private.h',
              SDK / 'include/maplesu_storage_private.h', SDK / 'include/maplesu_request_private.h',
              ROOT / 'runtime/ultra/boot/bootstrap_finish.c']
    before = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs}
    commands = []

    def run(name, argv):
        done = subprocess.run([str(a) for a in argv], capture_output=True, text=True,
                              encoding='utf-8', errors='replace', timeout=45)
        (out / (name + '.log')).write_text(done.stdout + done.stderr, encoding='utf-8')
        commands.append({'name': name, 'argv': [str(a) for a in argv], 'exit': done.returncode})
        if done.returncode:
            raise RuntimeError(name + ': ' + (done.stdout + done.stderr)[-6000:])
        return done.stdout + done.stderr

    docker = ['docker', 'run', '--rm', '--pull', 'never', '--network', 'none', '--read-only',
              '--tmpfs', '/tmp:rw,exec,size=32m', '--tmpfs', '/data:rw,exec,size=16m',
              '--tmpfs', '/system:rw,exec,size=4m',
              '--mount', f'type=bind,source={ROOT},target=/repo,readonly',
              '--mount', f'type=bind,source={out},target=/work', '-w', '/work',
              '--entrypoint', '/bin/sh', IMAGE]
    prefix = '/repo/runtime/maplesu-sdk/sdk/'
    tests = prefix + 'tools/tests/p6_boot/'
    base = ['g++', '-std=c++20', '-O1', '-Wall', '-Wextra', '-Werror',
            '-I' + prefix + 'src/helper', '-I' + prefix + 'include',
            '-I/repo/runtime/maplesu-sdk/include', '-I' + tests]
    results = []
    completed = False
    try:
        results.append(run('session-read', [*docker, '-ec',
            '"$@" && exec ./session-read', 'sh', *base, tests + 'test_session_read.cpp',
            prefix + 'src/helper/helper_session.cpp', prefix + 'src/helper/helper_text.cpp',
            '-o', 'session-read']))
        run('compile-finish', [*docker, '-c', 'exec "$@"', 'sh', 'gcc', '-O1', '-Wall', '-Wextra',
                              '-Werror', '-c', '/repo/runtime/ultra/boot/bootstrap_finish.c', '-o', 'finish.o'])
        run('compile-boot', [*docker, '-c', 'exec "$@"', 'sh', *base, tests + 'test_boot.cpp',
                            tests + 'worker_boundary.cpp', *['/repo/' + p.relative_to(ROOT).as_posix() for p in sources],
                            prefix + 'src/helper/helper_text.cpp', 'finish.o', '-o', 'boot-test'])
        results.append(run('boot', [*docker, '-c', 'exec ./boot-test']))
        sys.path.insert(0, str(SDK / 'tools'))
        import sdk_common as S
        manifest = json.loads((SDK / 'src/helper/manifest.json').read_text(encoding='utf-8-sig'))
        flags = S.profile_flags(manifest['flagsProfile'], 'cxx', manifest['extraFlags'])
        flags += ['-I' + str(SDK / p) for p in manifest['includeDirs']]
        compiler = S.DEFAULT_NDK / S.TOOLCHAIN_SUBDIR / 'clang++.exe'
        for source in sources:
            run(source.stem + '-android', [compiler, *flags, '-Werror', '-c', source,
                                         '-o', out / (source.stem + '.android.o')])
        completed = True
    finally:
        stable = all(before[str(p)] == hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs)
        receipt = {'passed': completed and stable and all(not c['exit'] for c in commands),
                   'deviceTested': False, 'sourceInputsStable': stable, 'commands': commands,
                   'results': results, 'inputs': before}
        (out / 'RESULT.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print('\n'.join(results))
    if not receipt['passed']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
