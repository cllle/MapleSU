#!/usr/bin/env python3
"""Check an explicitly declared WIP source snapshot and its retained SDK inputs.

This does not validate a source-built SDK delivery or grant Release acceptance.
Formal builds must keep sync-ultra-sdk-identity.py --check and delivery gates.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path, PurePosixPath
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SDK = 'runtime/maplesu-sdk'
MEMBERS = SDK + '/sdk'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def required_file(root, name):
    root = Path(root).resolve()
    require(isinstance(name, str) and name and '\\' not in name and ':' not in name,
            'invalid SDK source path: ' + str(name))
    relative = PurePosixPath(name)
    require(not relative.is_absolute() and '..' not in relative.parts,
            'invalid SDK source path: ' + name)
    path = root / name
    require(not path.is_symlink() and path.resolve().is_relative_to(root),
            'invalid SDK source path: ' + name)
    require(path.is_file(), 'missing SDK source file: ' + name)
    return path


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def checked_retained(root, item):
    path = required_file(root, item['path'])
    require(path.stat().st_size == item['size']
            and hashlib.sha256(path.read_bytes()).hexdigest() == item['sha256'],
            'retained input changed: ' + item['path'])
    return path


def check_snapshot_policy(policy):
    require(policy.get('public_scope') == 'work-in-progress-source-checkpoint',
            'SDK source gate requires explicit WIP source-checkpoint policy; use formal artifact gates')
    blockers = {item['id']: item.get('resolved') for item in policy.get('release_blockers', [])}
    require(all(blockers.get(name) is False for name in
                ('complete-source-build', 'maplesu-5.2.1-acceptance')),
            'SDK source gate requires unresolved release blockers; use formal artifact gates')


def cmake_values(text):
    values = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        match = re.fullmatch(r'set\(([A-Z0-9_]+) (?:"([A-Za-z0-9./_-]+)"|([0-9]+))\)', line)
        require(match is not None, 'invalid SDK identity statement')
        key, quoted, number = match.groups()
        require(key not in values, 'duplicate SDK identity statement: ' + key)
        values[key] = quoted if quoted is not None else int(number)
    return values


def check_cmake_identity(root, manifest):
    helper = manifest['helper']
    keyless = helper.get('keyless') is True
    expected = {
        'MAPLESU_ULTRA_IDENTITY_SOURCE': SDK + '/SDK-RELEASE.json',
        'MAPLESU_ULTRA_SDK_BUILD_ID': manifest['sdkBuildId'],
        'MAPLESU_ULTRA_SDK_VERSION': manifest['sdkVersion'],
        'MAPLESU_ULTRA_SDK_SHA256': manifest['archive']['sha256'],
        'MAPLESU_ULTRA_HELPER_SHA256': helper['sha256'] if keyless else helper['normalizedSha256'],
        'MAPLESU_ULTRA_HELPER_BUILD_ID': helper['buildId'],
        'MAPLESU_APP_VERSION': manifest['appVersion'],
        'MAPLESU_ULTRA_HELPER_SIZE': helper['size'],
    }
    if not keyless:
        expected['MAPLESU_ULTRA_KEY_SLOT_OFFSET'] = helper['keySlotOffset']
    identity = required_file(root, SDK + '/SDK-IDENTITY.cmake')
    require(cmake_values(identity.read_text(encoding='utf-8-sig')) == expected,
            'SDK-IDENTITY.cmake is stale for the retained SDK receipt')


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def check_archive_helper_pair(root, archive, helper_bytes):
    from elftools.elf.elffile import ELFFile

    reader = load_module('sdk_snapshot_archive_reader', required_file(root, 'scripts/ar_archive.py'))
    members = reader.parse_ar(str(archive))['members']
    installers = [member for member in members if member['name'].endswith('_autorun_bootstrap_install.o')]
    require(len(installers) == 1, 'retained archive bootstrap member coverage')
    member = installers[0]
    data = archive.read_bytes()
    elf = ELFFile(io.BytesIO(data[member['body_offset']:member['body_offset'] + member['size']]))
    table = elf.get_section_by_name('.symtab')
    symbols = [symbol for symbol in table.iter_symbols()
               if symbol.name.endswith('autorun_bootstrap_file_dataE')] if table is not None else []
    require(len(symbols) == 1, 'retained archive helper symbol coverage')
    symbol = symbols[0]
    require(isinstance(symbol['st_shndx'], int), 'retained archive helper symbol section')
    section = elf.get_section(symbol['st_shndx'])
    start = symbol['st_value'] - section['sh_addr']
    require(start >= 0 and symbol['st_size'] == len(helper_bytes)
            and section.data()[start:start + symbol['st_size']] == helper_bytes,
            'retained archive embedded helper differs from retained helper input')


def check_retained_sdk(root, manifest):
    from elftools.elf.elffile import ELFFile

    archive = checked_retained(root, manifest['archive'])
    helper = checked_retained(root, manifest['helper'])
    data = helper.read_bytes()
    check_archive_helper_pair(root, archive, data)
    elf = ELFFile(io.BytesIO(data))
    require(elf.elfclass == 64 and elf.little_endian and elf['e_type'] == 'ET_DYN'
            and elf['e_machine'] == 'EM_AARCH64', 'retained helper ELF shape')
    note = elf.get_section_by_name('.note.gnu.build-id')
    notes = list(note.iter_notes()) if note is not None else []
    require(len(notes) == 1 and notes[0]['n_type'] == 'NT_GNU_BUILD_ID'
            and notes[0]['n_desc'] == manifest['helper']['buildId'], 'retained helper build identity')
    helper_identity = manifest['helper']
    normalized = bytearray(data)
    if helper_identity.get('keyless') is True:
        require(manifest.get('rootKeyRequired') is False and 'keySlotOffset' not in helper_identity,
                'retained helper credential shape')
    else:
        offset = helper_identity.get('keySlotOffset')
        require(type(offset) is int and 0 < offset < len(data) - 4096,
                'retained helper credential shape')
        normalized[offset:offset + 4096] = bytes(4096)
    require(hashlib.sha256(normalized).hexdigest() == helper_identity['normalizedSha256'],
            'retained helper normalized identity')
    policy = manifest['environmentPolicyBoot']
    backend = checked_retained(root, policy['backend'])
    describer = load_module('sdk_snapshot_backend_identity',
                            required_file(root, 'scripts/policy_backend_identity.py'))
    actual = describer.describe_backend(backend)
    require(actual == {key: value for key, value in policy['backend'].items() if key != 'path'},
            'retained backend identity')
    require(actual['fileName'].encode() + b'\0' in data and bytes.fromhex(actual['noteHex']) in data
            and policy['entryHelperSha256'] == helper_identity['sha256'], 'retained helper/backend pairing')
    check_cmake_identity(root, manifest)


def check_member_sources(root):
    root = Path(root).resolve()
    index = required_file(root, MEMBERS + '/manifest.jsonl')
    rows = [json.loads(line) for line in index.read_text(encoding='utf-8').splitlines() if line.strip()]
    require(rows and [row['idx'] for row in rows] == list(range(len(rows))), 'SDK member ordinal coverage')
    definitions, sources, headers = set(), set(), set()
    fields = ('idx', 'name', 'kind', 'layout', 'sources', 'flagsProfile', 'extraFlags',
              'headers', 'privateHeaders', 'compose', 'recipe', 'referenceInput')
    lists = {'sources', 'extraFlags', 'headers', 'privateHeaders', 'compose'}
    for row in rows:
        definition = required_file(root, row['sourceFile'])
        definitions.add(definition.resolve())
        raw = read_json(definition)
        for key in fields:
            default = [] if key in lists else None
            require(row.get(key, default) == raw.get(key, default),
                    f'SDK member index differs: {row["idx"]} {key}')
        require(row.get('sources'), f'SDK member has no source: {row["idx"]}')
        for key in ('sources', 'headers', 'privateHeaders'):
            for name in row.get(key, []):
                # Same relative-path resolution order as the SDK metadata generator.
                require(isinstance(name, str) and name and '\\' not in name
                        and ':' not in name and not PurePosixPath(name).is_absolute(),
                        'invalid SDK source path: ' + str(name))
                candidate = (root / MEMBERS / name).resolve()
                require(candidate.is_relative_to(root), 'invalid SDK source path: ' + name)
                if not candidate.is_file():
                    alternative = (root / name).resolve()
                    if alternative.is_relative_to(root) and alternative.is_file():
                        candidate = alternative
                path = required_file(root, candidate.relative_to(root).as_posix())
                (sources if key == 'sources' else headers).add(path.resolve())
    expected = {path.resolve() for path in (root / MEMBERS / 'manifest').glob('[0-9][0-9][0-9][0-9]_*.json')}
    require(len(definitions) == len(rows) and definitions == expected, 'SDK member definition coverage')
    return dict(sourceMembers=len(rows), sourceFiles=len(sources), declaredHeaders=len(headers))


def check_generated_index(root):
    tools = root / MEMBERS / 'tools'
    sys.path.insert(0, str(tools))
    generator = load_module('sdk_snapshot_manifest_generator', required_file(root, MEMBERS + '/tools/gen_manifest.py'))
    generated = generator.generate(write=False, no_legacy=True, require_normalized=True, quiet=True)
    require(generated.ok and not generated.missing_idx,
            'SDK metadata generation failed: ' + '; '.join(issue.render() for issue in generated.errors))
    rows = [json.loads(line) for line in (root / MEMBERS / 'manifest.jsonl').read_text(encoding='utf-8').splitlines() if line.strip()]
    require(rows == [record.to_jsonl() for record in generated.records],
            'SDK generated member index is stale; regenerate with gen_manifest.py')


def check_source_checkpoint(root):
    root = Path(root).resolve()
    check_snapshot_policy(read_json(required_file(root, 'release/source-policy.json')))
    manifest = read_json(required_file(root, SDK + '/SDK-RELEASE.json'))
    check_retained_sdk(root, manifest)
    result = check_member_sources(root)
    check_generated_index(root)
    for name in ('build.py', 'tools/sdk_common.py', 'tools/member_gate.py', 'tools/extract_payloads.py'):
        required_file(root, MEMBERS + '/' + name)
    for name in ('sync-ultra-sdk-identity.py', 'stage-source-sdk.py', 'source_sdk_delivery.py', 'verify-release-apk.py'):
        required_file(root, 'scripts/' + name)
    for name in ('kernel_module_kit_umbrella.h', 'module_descriptor.h', 'maplesu/compat/native_module_abi.h'):
        required_file(root, SDK + '/include/' + name)
    return dict(scope='work-in-progress-source-checkpoint', **result,
                retainedSdkBuildId=manifest['sdkBuildId'], retainedSdkVersion=manifest['sdkVersion'],
                artifactProvenanceVerified=False, runtimeAcceptance=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    args = parser.parse_args()
    try:
        print(json.dumps(check_source_checkpoint(args.root), indent=2))
    except (AssertionError, KeyError, OSError, ValueError) as error:
        print('SDK source checkpoint check failed: ' + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
