#!/usr/bin/env python3
"""Run exact production credential transactions with explicit host ownership seams."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil
import subprocess


def extract(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo-root', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--management-source', type=Path)
    args = parser.parse_args()
    root = args.repo_root.resolve() if args.repo_root else Path(__file__).resolve().parents[4]
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    source_path = args.management_source.resolve() if args.management_source else root / 'runtime/ultra/identity/kernel/manager_management.c'
    fixture_path = Path(__file__).with_name('p4_credential_lifecycle_fixture.cpp')
    runner_path = Path(__file__).resolve()
    production_paths = [source_path,
                        root / 'runtime/ultra/identity/kernel/manager_credential_profile.h',
                        root / 'runtime/ultra/identity/kernel/manager_dynamic_layout.h']
    production_inputs = {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in production_paths}
    source = source_path.read_text(encoding='utf-8')
    signatures = [
        'static kernel_cap_t msu_management_cap(',
        'int msu_credential_profile_validate(',
        'static int msu_credential_replace_profile(',
        'int msu_credential_replace_profile_current(',
        'int msu_credential_replace_current(',
    ]
    exact = '\n\n'.join(extract(source, signature) for signature in signatures)
    fixture = fixture_path.read_text(encoding='utf-8')
    if fixture.count('// EXACT_FUNCTIONS') != 1:
        raise SystemExit('fixture extraction marker must occur exactly once')
    generated = out / 'p4_credential_lifecycle.cpp'
    generated.write_text(fixture.replace('// EXACT_FUNCTIONS', exact), encoding='utf-8')
    compilers = sorted((root / '.research/toolchains').glob('llvm-mingw-*/bin/x86_64-w64-mingw32-clang++.exe'))
    compiler = str(compilers[-1]) if compilers else shutil.which('clang++')
    if not compiler:
        raise SystemExit('Clang C++ compiler required for the exact kernel C11 generic expression')
    modes = []
    for group_refcount in (0, 1):
        for ucounts in (0, 1):
            for cap_last in (40, 63):
                name = f'group-refcount-{group_refcount}-ucounts-{ucounts}-cap-{cap_last}'
                mode_out = out / name
                mode_out.mkdir(exist_ok=True)
                exe = mode_out / ('p4-credential-lifecycle.exe' if Path(compiler).suffix == '.exe' else 'p4-credential-lifecycle')
                command = [compiler, '-std=c++20', '-O1', '-g', '-Wall', '-Wextra',
                           f'-DMSU_GROUP_INFO_REFCOUNT={group_refcount}', f'-DMSU_MANAGEMENT_CRED_UCOUNTS={ucounts}', f'-DCAP_LAST_CAP={cap_last}',
                           str(generated), '-o', str(exe)]
                if 'mingw' in compiler.lower():
                    command.insert(-2, '-static')
                build = subprocess.run(command, capture_output=True, text=True, encoding='utf-8', errors='replace')
                (mode_out / 'compile.log').write_text(build.stdout + build.stderr, encoding='utf-8')
                mode = {'name': name, 'groupRefcount': group_refcount, 'ucounts': ucounts, 'capLast': cap_last,
                        'command': command, 'compileExitCode': build.returncode, 'passed': False}
                if build.returncode == 0:
                    run = subprocess.run([str(exe)], capture_output=True, text=True, encoding='utf-8', errors='replace')
                    output = run.stdout + run.stderr
                    (mode_out / 'run.log').write_text(output, encoding='utf-8')
                    match = re.search(r'cases=(\d+) checks=(\d+) passed=1', output)
                    mode.update({'runExitCode': run.returncode, 'runOutput': output,
                                 'passed': run.returncode == 0 and bool(match)})
                    if match:
                        mode.update({'cases': int(match[1]), 'checks': int(match[2])})
                modes.append(mode)
    report = {
        'passed': all(mode['passed'] for mode in modes),
        'source': str(source_path), 'sourceSha256': hashlib.sha256(source_path.read_bytes()).hexdigest(),
        'fixture': str(fixture_path), 'fixtureSha256': hashlib.sha256(fixture_path.read_bytes()).hexdigest(),
        'runner': str(runner_path), 'runnerSha256': hashlib.sha256(runner_path.read_bytes()).hexdigest(),
        'productionInputs': production_inputs,
        'productionInputsUnchanged': all(hashlib.sha256(path.read_bytes()).hexdigest() == production_inputs[str(path)]
                                         for path in production_paths),
        'exactExtractSha256': hashlib.sha256(exact.encode()).hexdigest(),
        'extractedFunctions': signatures, 'compiler': compiler, 'modes': modes,
        'scope': 'five exact candidate profile/Root/CAPS functions; host dynamic-field access binds fixture structs, dynamic cred get clears non_rcu and owns a reference, final put invokes the dependency destructor, and mm get/put balance; atomic/refcount group local handoff and all original lifecycle assertions retained; no runtime offset discovery, real kernel concurrency, LSM resolver, fork/exec, target ABI or device evidence',
    }
    report['passed'] = report['passed'] and report['productionInputsUnchanged']
    (out / 'results.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report))
    raise SystemExit(0 if report['passed'] else 1)


if __name__ == '__main__':
    main()
