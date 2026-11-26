#!/usr/bin/env python3
"""Replay the fixed R18/90e7 consumer regression with explicit bound dependencies."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

FIXTURE_SHA256 = '94867abcad7b847c4aa5147e57f1fe8ef9e485ec421dabf2bb4d9ad20f4500bd'
METADATA_SHA256 = '90e7ca2424782b908cd9d82a7804222e6c6194cc0d72109ff82d54c4b1d04c0f'
MANIFEST_SHA256 = '2bc643e4fa67cb26ff3dc6d6256aa004033ae75ab7dda90374f703f79061909a'
SOURCE_NAMES = {
    'UltraKernelHostDeploymentMetadata.kt', 'UltraKernelManagerClaimPayload.kt',
    'UltraKernelResidentAbi.kt', 'UltraKernelSuComponentMetadata.kt',
    'UltraKernelTaskProfileTarget.kt',
}
MAIN_CLASS = 'dev.maplesu.manager.ultrakernel.ReferenceConsumerLoadKt'


def file_identity(path):
    path = Path(path).resolve(strict=True)
    with path.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    return {'path': str(path), 'bytes': path.stat().st_size, 'sha256': digest}


def tree_identity(path):
    root = Path(path).resolve(strict=True)
    rows = []
    for path in sorted(root.rglob('*'), key=lambda item: item.relative_to(root).as_posix()):
        if path.is_file():
            row = file_identity(path)
            row['path'] = path.relative_to(root).as_posix()
            rows.append(row)
    encoded = json.dumps(rows, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    return {'path': str(root), 'fileCount': len(rows),
            'treeSha256': hashlib.sha256(encoded).hexdigest()}


def bound_path(row):
    path = Path(row['path']).resolve(strict=True)
    if not Path(row['path']).is_absolute():
        raise ValueError('DEPENDENCY_PATH_MUST_BE_ABSOLUTE:' + str(path))
    actual = tree_identity(path) if path.is_dir() else file_identity(path)
    fields = ('fileCount', 'treeSha256') if path.is_dir() else ('bytes', 'sha256')
    if any(actual[key] != row[key] for key in fields):
        raise ValueError('INPUT_IDENTITY_CHANGED:' + str(path))
    return actual


def parser():
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument('--assets', required=True, type=Path,
                        help='Exact external current20 claim directory containing manifest.json')
    result.add_argument('--assets-tree-sha256', required=True)
    result.add_argument('--metadata', required=True, type=Path)
    result.add_argument('--metadata-sha256', required=True, choices=[METADATA_SHA256])
    result.add_argument('--dependencies', required=True, type=Path)
    result.add_argument('--dependencies-sha256', required=True)
    result.add_argument('--mode', required=True, choices=['source', 'final'])
    result.add_argument('--output', required=True, type=Path, help='New output directory')
    result.add_argument('--plan-only', action='store_true',
                        help='Validate bound inputs and print argv; launch no Java or compiler')
    return result


def prepare(args):
    dependency_identity = file_identity(args.dependencies)
    if dependency_identity['sha256'] != args.dependencies_sha256:
        raise ValueError('DEPENDENCY_MANIFEST_HASH')
    deps = json.loads(args.dependencies.read_text(encoding='utf-8-sig'))
    if deps['schema'] != 'maplesu-reference-consumer-dependencies/1':
        raise ValueError('DEPENDENCY_MANIFEST_SCHEMA')
    if not deps['selectionDescription'].strip():
        raise ValueError('DEPENDENCY_SELECTION_DESCRIPTION')
    selected = deps['sources'] if args.mode == 'source' else deps['productClasspath']
    if not selected:
        raise ValueError('EXPLICIT_PRODUCT_SELECTION_REQUIRED')
    if args.mode == 'source' and (len(selected) != 5 or
            {Path(row['path']).name for row in selected} != SOURCE_NAMES):
        raise ValueError('EXACT_FIVE_COMPLETE_CONSUMER_SOURCES_REQUIRED')
    fixture = Path(__file__).resolve().parent / 'ReferenceConsumerLoad.kt'
    identities = [dependency_identity, file_identity(__file__), file_identity(fixture)]
    if identities[-1]['sha256'] != FIXTURE_SHA256:
        raise ValueError('FIXED_R18_FIXTURE_HASH')
    metadata = file_identity(args.metadata)
    if metadata['sha256'] != args.metadata_sha256:
        raise ValueError('FIXED_R18_METADATA_HASH')
    assets = tree_identity(args.assets)
    if assets['treeSha256'] != args.assets_tree_sha256:
        raise ValueError('EXPLICIT_ASSET_TREE_HASH')
    asset_manifest = file_identity(args.assets / 'manifest.json')
    if asset_manifest['sha256'] != MANIFEST_SHA256:
        raise ValueError('FIXED_CURRENT20_MANIFEST_HASH')
    identities.extend([metadata, assets, asset_manifest])
    rows = [deps['java'], *deps['compilerClasspath'], *deps['classpath'], *selected]
    identities.extend(bound_path(row) for row in rows)
    if not deps['compilerClasspath'] or not deps['classpath']:
        raise ValueError('EXPLICIT_COMPILER_AND_SUPPORT_CLASSPATH_REQUIRED')
    java = deps['java']['path']
    supporting = [row['path'] for row in deps['classpath']]
    output = args.output.resolve()
    product = [str(output / 'product-classes')] if args.mode == 'source' else [row['path'] for row in selected]
    compiler = [java, '-Xmx768m', '-cp', os.pathsep.join(row['path'] for row in deps['compilerClasspath']),
                'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler']
    def compile_argv(inputs, target, classpath):
        return [*compiler, '-no-stdlib', '-no-reflect', '-jvm-target', '17',
                '-Xfriend-paths=' + ','.join(classpath), '-classpath', os.pathsep.join(classpath),
                '-d', str(target), *inputs]
    commands = []
    if args.mode == 'source':
        commands.append({'name': 'compile-product', 'argv': compile_argv(
            [row['path'] for row in selected], output / 'product-classes', supporting)})
    fixture_jar = output / 'reference-r18-fixture.jar'
    commands.append({'name': 'compile-fixture', 'argv': compile_argv(
        [str(fixture)], fixture_jar, [*product, *supporting])})
    runtime = [java, '-ea', '-Xmx768m', '-Dfile.encoding=UTF-8', '-cp',
               os.pathsep.join([str(fixture_jar), *product, *supporting]), MAIN_CLASS, str(args.assets.resolve())]
    for name, phase in [('actual-load', 'abi4-r5-load'), ('actual-negatives', 'abi4-r5'),
                        ('original-package-negatives', 'abi4-known-red')]:
        argv = [*runtime, phase]
        if phase != 'abi4-known-red':
            argv.extend([str(args.metadata.resolve()), args.metadata_sha256])
        commands.append({'name': name, 'argv': argv})
    return {'schema': 'maplesu-fixed-r18-replay/1', 'scope': 'historical-R18-90e7-consumer-only',
            'mode': args.mode, 'selectionDescription': deps['selectionDescription'],
            'planOnly': args.plan_only, 'inputs': identities, 'commands': commands,
            'finalGenerationQualified': False, 'deviceOperations': 0}


def check_result(name, lines):
    if name == 'actual-load':
        if lines.count('PASS reference-real-r5-selected-load') != 1:
            raise ValueError('ACTUAL_LOAD_MARKER')
    elif name == 'actual-negatives':
        if (sum(line.startswith('PASS ABI4_BOUNDARY ') for line in lines) != 379 or
                lines.count('PASS ABI4_CONFIG_UNDEFINED_DISABLED_EXPLICIT_N') != 1 or
                'ABI4_BOUNDARY_NEGATIVES=379; ACTUAL_LOAD=PASS; targetPlan=false; deviceOperations=0; assetWrites=0' not in lines):
            raise ValueError('FULL_379_NEGATIVES_AND_DISABLED_N_REQUIRED')
    elif name == 'original-package-negatives':
        if (sum(line.startswith('PASS ABI4_BOUNDARY ') for line in lines) != 24 or
                'ABI4_BOUNDARY_NEGATIVES=23; ACTUAL_LOAD=RED_FACT_FIELDS; targetPlan=false; deviceOperations=0; assetWrites=0' not in lines):
            raise ValueError('ORIGINAL_23_NEGATIVES_AND_KNOWN_RED_REQUIRED')


def main():
    args = parser().parse_args()
    plan = prepare(args)
    if args.plan_only:
        print(json.dumps(plan, ensure_ascii=False, indent=2))
        return 0
    output = args.output.resolve()
    # Fresh output only; no recursive removal or mutations of external inputs.
    if any(output == Path(row['path']) or output in Path(row['path']).parents or
           Path(row['path']) in output.parents for row in plan['inputs']):
        raise ValueError('OUTPUT_OVERLAPS_INPUT')
    output.mkdir(parents=True, exist_ok=False)
    (output / 'product-classes').mkdir()
    report = {**plan, 'status': 'RUNNING', 'steps': []}
    def save():
        (output / 'EXECUTION.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    save()
    try:
        for command in plan['commands']:
            step = {**command, 'status': 'RUNNING'}
            report['steps'].append(step)
            save()
            log = output / (command['name'] + '.log')
            with log.open('wb') as stream:
                step['exitCode'] = subprocess.run(command['argv'], stdout=stream,
                    stderr=subprocess.STDOUT, timeout=300).returncode
            step['log'] = file_identity(log)
            if step['exitCode']:
                raise ValueError('COMMAND_FAILED:' + command['name'])
            check_result(command['name'], log.read_text(encoding='utf-8', errors='replace').splitlines())
            step['status'] = 'PASS'
            save()
        report['status'] = 'PASS_FIXED_R18_REPLAY_ONLY'
    except BaseException as error:
        report.update(status='FAIL', error=repr(error))
        if report['steps']:
            report['steps'][-1]['status'] = 'FAIL'
    finally:
        report['notRun'] = [command['name'] for command in plan['commands'][len(report['steps']):]]
        try:
            report['inputsUnchanged'] = prepare(args)['inputs'] == plan['inputs']
            if not report['inputsUnchanged']:
                report.update(status='FAIL', integrityError='INPUT_SET_CHANGED')
        except BaseException as error:
            report.update(status='FAIL', inputsUnchanged=False, integrityError=repr(error))
        save()
    print(report['status'])
    return 0 if report['status'] == 'PASS_FIXED_R18_REPLAY_ONLY' else 1


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, KeyError, OSError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(2)
