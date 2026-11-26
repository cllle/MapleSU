#!/usr/bin/env python3
"""Narrow CLI/plan contract check only; never execute Kotlin or Java."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys


def invoke(runner, arguments, expected):
    completed = subprocess.run([sys.executable, '-B', '-X', 'utf8', str(runner), *arguments],
        capture_output=True, text=True, encoding='utf-8', timeout=90)
    if completed.returncode != expected:
        raise AssertionError((arguments, completed.returncode, completed.stdout, completed.stderr))
    return completed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runner', required=True, type=Path)
    parser.add_argument('--invocation', required=True, type=Path)
    parser.add_argument('--invocation-sha256', required=True)
    args = parser.parse_args()
    if hashlib.sha256(args.invocation.read_bytes()).hexdigest() != args.invocation_sha256:
        raise AssertionError('NARROW_INVOCATION_HASH')
    invocation = json.loads(args.invocation.read_text(encoding='utf-8-sig'))
    argv = invocation['arguments']
    if '--plan-only' not in argv:
        raise AssertionError('PLAN_ONLY_REQUIRED')
    if Path(argv[argv.index('--output') + 1]).exists():
        raise AssertionError('PLAN_OUTPUT_MUST_START_ABSENT')
    checks = []
    help_result = invoke(args.runner, ['--help'], 0)
    for flag in ['--assets', '--assets-tree-sha256', '--metadata', '--metadata-sha256',
                 '--dependencies', '--dependencies-sha256', '--mode', '--output', '--plan-only']:
        if flag not in help_result.stdout:
            raise AssertionError(flag)
    checks.append('help-declares-nine-explicit-input-options')
    invoke(args.runner, [], 2)
    checks.append('missing-required-inputs-exit2')
    wrong = list(argv)
    wrong[wrong.index('--metadata-sha256') + 1] = '0' * 64
    invoke(args.runner, wrong, 2)
    checks.append('different-generation-metadata-exit2')
    wrong = list(argv)
    wrong[wrong.index('--dependencies-sha256') + 1] = '0' * 64
    invoke(args.runner, wrong, 2)
    checks.append('dependency-manifest-hash-mismatch-exit2')
    final = list(argv)
    final[final.index('--mode') + 1] = 'final'
    result = invoke(args.runner, final, 2)
    if 'EXPLICIT_PRODUCT_SELECTION_REQUIRED' not in result.stderr:
        raise AssertionError('FINAL_MUST_NOT_FALL_BACK_TO_HISTORICAL_SUPPORT')
    checks.append('final-mode-requires-explicit-product-no-support-fallback')
    plan = json.loads(invoke(args.runner, argv, 0).stdout)
    if [row['name'] for row in plan['commands']] != [
            'compile-product', 'compile-fixture', 'actual-load', 'actual-negatives', 'original-package-negatives']:
        raise AssertionError('SOURCE_FIRST_AND_LOAD_FIRST_ORDER')
    checks.append('source-product-compile-before-fixture-load-before-negatives')
    fixture = plan['commands'][1]['argv']
    runtime = plan['commands'][2]['argv']
    product = str(Path(argv[argv.index('--output') + 1]).resolve() / 'product-classes')
    import os
    if fixture[fixture.index('-classpath') + 1].split(os.pathsep)[0] != product:
        raise AssertionError('FIXTURE_SOURCE_FIRST')
    if runtime[runtime.index('-cp') + 1].split(os.pathsep)[1] != product:
        raise AssertionError('RUNTIME_SOURCE_FIRST')
    checks.append('fresh-product-classes-precede-supporting-dependencies')
    if not plan['planOnly'] or plan['finalGenerationQualified'] or plan['deviceOperations'] != 0:
        raise AssertionError('PLAN_SCOPE')
    if Path(argv[argv.index('--output') + 1]).exists():
        raise AssertionError('PLAN_WROTE_OUTPUT')
    checks.append('plan-only-creates-no-output-and-claims-no-final-or-device-pass')
    print(json.dumps({'checks': checks, 'checksPassed': len(checks),
        'compilerInvocations': 0, 'javaInvocations': 0, 'fixtureExecutions': 0,
        'scope': 'CLI argument errors and real-input source-first plan only'}, indent=2))


if __name__ == '__main__':
    main()
