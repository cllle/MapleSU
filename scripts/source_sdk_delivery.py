"""Verify a staged source SDK against its build receipt and actual embedded files."""
import hashlib
import importlib.util
import io
import json
import re
import sys
from pathlib import Path

from ar_archive import parse_ar
from policy_backend_identity import describe_backend


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def record(path, root):
    path = Path(path).resolve()
    name = path.relative_to(root).as_posix() if path.is_relative_to(root) else path.as_posix()
    return dict(path=name, size=path.stat().st_size, sha256=sha(path))


def checked(item, root, build_root=None, bundle_inputs=None):
    path = (root / item['path']).resolve()
    allowed = path.is_relative_to(root) or (build_root is not None and path.is_relative_to(build_root))
    if not allowed and bundle_inputs is not None:
        expected = bundle_inputs.get(path)
        allowed = expected is not None and all(item.get(key) == expected[key] for key in ('path', 'size', 'sha256'))
    assert allowed and path.is_file(), 'SOURCE_SDK_FILE: ' + item['path']
    assert path.stat().st_size == item['size'] and sha(path) == item['sha256'], 'SOURCE_SDK_STALE: ' + item['path']
    return path


def extractor(root):
    path = root / 'runtime/maplesu-sdk/sdk/tools/extract_payloads.py'
    spec = importlib.util.spec_from_file_location('source_sdk_payload_extractor', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def source_helper_identity(helper, report):
    """Read the complete-source helper identity without executing the program."""
    from elftools.elf.elffile import ELFFile

    data = Path(helper).read_bytes()
    slot = report['keySlot']
    offset = slot['offset']
    assert slot['capacity'] == 4096 and slot['tokenBytes'] == 128 and slot['keyLength'] == 48, 'SOURCE_SDK_HELPER_SLOT'
    assert 0 < offset < len(data) - 4096, 'SOURCE_SDK_HELPER_SLOT'
    token = (b'5d31494d2e18e3fdef2afaf740b0febc19229cb292f57ebc3bc1c96c6d70b709'
             b'8d2dc1f356b733e8c999e6ac85bdca24c8c74f03ae7d5b2a402e96804576e925')
    padded = token + bytes(4096 - len(token))
    assert data[offset:offset + 4096] == padded and data.count(padded) == data.count(token) == 1, 'SOURCE_SDK_HELPER_SLOT'
    elf = ELFFile(io.BytesIO(data))
    assert elf.elfclass == 64 and elf.little_endian and elf['e_type'] == 'ET_DYN' and elf['e_machine'] == 'EM_AARCH64', 'SOURCE_SDK_HELPER_ELF'
    note = elf.get_section_by_name('.note.gnu.build-id')
    assert note is not None, 'SOURCE_SDK_HELPER_BUILD_ID'
    notes = list(note.iter_notes())
    assert len(notes) == 1 and notes[0]['n_type'] == 'NT_GNU_BUILD_ID', 'SOURCE_SDK_HELPER_BUILD_ID'
    normalized = bytearray(data)
    normalized[offset:offset + 4096] = bytes(4096)
    return dict(sha256=hashlib.sha256(data).hexdigest(), size=len(data),
                normalizedSha256=hashlib.sha256(normalized).hexdigest(), buildId=notes[0]['n_desc'],
                keySlotOffset=offset)


def verify_source_helper_pair(manifest, source, build, helper, backend, root, build_root=None):
    receipt_path = checked(source['helperBuildReport'], root, build_root)
    receipt = json.loads(receipt_path.read_text(encoding='utf-8'))
    assert receipt.get('readiness') == 'source-candidate-unvalidated-device', 'SOURCE_SDK_HELPER_SOURCE_READINESS'
    assert receipt == {key: value for key, value in build['source_helper'].items() if key != 'reportPath'}, 'SOURCE_SDK_HELPER_BUILD_RECEIPT'
    selected = build['assets']['helper']
    assert selected.get('builtFromSource') is True and selected.get('origin') == 'source-built', 'SOURCE_SDK_HELPER_SOURCE_SELECTION'
    assert selected['report'] == source['helperBuildReport'], 'SOURCE_SDK_HELPER_BUILD_RECEIPT'
    assert selected['sha256'] == sha(helper) == receipt['surface']['sha256'] and receipt['surface']['bytes'] == helper.stat().st_size, 'SOURCE_SDK_HELPER_SOURCE_BYTES'
    identity = source_helper_identity(helper, receipt)
    assert all(manifest['helper'][key] == value for key, value in identity.items()), 'SOURCE_SDK_HELPER_IDENTITY'
    pair = receipt['backend']
    assert pair['bytes'] == backend.stat().st_size and pair['sha256'] == sha(backend), 'SOURCE_SDK_BACKEND_SOURCE_PAIR'
    actual = describe_backend(backend)
    assert pair['filename'] == actual['fileName'] and pair['noteOffset'] == actual['noteOffset'] and pair['noteHex'] == actual['noteHex'], 'SOURCE_SDK_BACKEND_SOURCE_PAIR'
    helper_bytes = helper.read_bytes()
    assert helper_bytes.count(actual['fileName'].encode() + b'\0') >= 1, 'SOURCE_SDK_BACKEND_NAME_UNPAIRED'
    assert helper_bytes.count(bytes.fromhex(actual['noteHex'])) >= 1, 'SOURCE_SDK_BACKEND_NOTE_UNPAIRED'
    proof = receipt['provenance']
    assert proof.get('closure_complete') and proof.get('translation_units'), 'SOURCE_SDK_HELPER_PROVENANCE'
    assert all(unit['stage'] == 'source-helper' for unit in proof['translation_units']), 'SOURCE_SDK_HELPER_PROVENANCE'
    combined = {item['path']: item for item in build['payloads']['provenance']['inputs']}
    for item in proof['inputs']:
        checked(item, root, build_root)
        assert item == combined.get(item['path']), 'SOURCE_SDK_HELPER_INPUT_RECEIPT'
    assert source['helperOrigin'] == 'source-built', 'SOURCE_SDK_HELPER_SOURCE_SELECTION'
    return receipt


def selected_policy_bundle_inputs(report, root, build_root):
    """Qualify exact reused generator inputs, never their containing directory."""
    inputs = report['provenance']['inputs']
    if all((root / item['path']).resolve().is_relative_to(root) or
           (build_root is not None and (root / item['path']).resolve().is_relative_to(build_root)) for item in inputs):
        return {}

    def selected(argv, flag):
        assert isinstance(argv, list) and all(isinstance(arg, str) for arg in argv), 'SOURCE_SDK_BUNDLE_INVOCATION'
        values = []
        for index, arg in enumerate(argv):
            if arg == flag:
                assert index + 1 < len(argv) and not argv[index + 1].startswith('--'), 'SOURCE_SDK_BUNDLE_INVOCATION'
                values.append(argv[index + 1])
            elif arg.startswith(flag + '='):
                values.append(arg[len(flag) + 1:])
        assert len(values) == 1 and values[0], 'SOURCE_SDK_BUNDLE_INVOCATION'
        return (root / values[0]).resolve()

    bundle = selected(report['invocation']['argv'], '--policy-bundle-source')
    assert build_root is not None and selected(report['invocation']['argv'], '--build-dir') == build_root, 'SOURCE_SDK_BUNDLE_BUILD_DIRECTORY'
    generated = report.get('source_backend') or {}
    assert generated.get('schema') == 'maplesu-sdk-source-backend/1' and generated.get('bundle') == record(bundle, root)['path'], 'SOURCE_SDK_BUNDLE_SELECTION'
    configures = [row for row in generated.get('commands', []) if row.get('label') == 'configure']
    assert len(configures) == 1 and configures[0].get('exit_code') == 0, 'SOURCE_SDK_BUNDLE_CONFIGURE'
    configured = [arg.split('=', 1)[1] for arg in configures[0]['argv'] if arg.startswith('-DMAPLESU_POLICY_BUNDLE_SOURCE=')]
    assert len(configured) == 1 and (root / configured[0]).resolve() == bundle, 'SOURCE_SDK_BUNDLE_CONFIGURE'

    proofs = [report['provenance'], generated['provenance']]
    indexes, snapshots = [], []
    for proof in proofs:
        assert (proof.get('schema') == 'maplesu-sdk-source-provenance/2' and proof.get('closure_complete') is True
                and proof.get('incomplete_reasons') == [] and proof.get('runtime_acceptance') is False), 'SOURCE_SDK_BUNDLE_PROVENANCE'
        index = {item['path']: item for item in proof['inputs']}
        assert len(index) == len(proof['inputs']) and proof.get('input_snapshots'), 'SOURCE_SDK_BUNDLE_PROVENANCE'
        saved = {}
        for item in proof['input_snapshots']:
            assert item['path'] in index and all(item[key] == index[item['path']][key] for key in ('size', 'sha256')), 'SOURCE_SDK_BUNDLE_SNAPSHOT'
            saved.setdefault(item['path'], set()).update(item.get('roles', []))
        indexes.append(index)
        snapshots.append(saved)
    tools = root / 'runtime/maplesu-sdk/sdk/tools'
    for name in ('source_backend.py', 'sdk_common.py', 'source_provenance.py'):
        item = record(tools / name, root)
        assert all(index.get(item['path']) and all(index[item['path']][key] == item[key] for key in ('size', 'sha256'))
                   and 'build_script' in index[item['path']]['roles'] for index in indexes), 'SOURCE_SDK_BUNDLE_VALIDATOR_IDENTITY'
    spec = importlib.util.spec_from_file_location('source_sdk_policy_bundle_inputs', tools / 'source_backend.py')
    backend = importlib.util.module_from_spec(spec)
    sys.path.insert(0, str(tools))
    try:
        spec.loader.exec_module(backend)
        assert backend.S.REPO_ROOT.resolve() == root, 'SOURCE_SDK_BUNDLE_VALIDATOR_ROOT'
        closure = backend.bundle_inputs(bundle)
    finally:
        sys.path.pop(0)
    allowed = {}
    for path, role in closure:
        path = Path(path).resolve()
        if path not in allowed:
            allowed[path] = record(path, root)
        item = allowed[path]
        for index in indexes:
            observed = index.get(item['path'])
            assert (observed and all(observed[key] == item[key] for key in ('size', 'sha256'))
                    and role in observed['roles']), 'SOURCE_SDK_BUNDLE_INPUT_BINDING: ' + item['path']
        # build_receipt merges child inputs/TUs, not child input_snapshots.
        # The backend producer owns the reused bundle's before/after snapshots.
        assert role in snapshots[1].get(item['path'], set()), 'SOURCE_SDK_BUNDLE_INPUT_BINDING: ' + item['path']
    units = [unit for unit in generated['provenance']['translation_units'] if unit.get('source') == generated['bundle']]
    assert (len(units) == 1 and units[0].get('stage') == 'source-backend'
            and units[0].get('build_source') == generated['bundle']
            and generated['bundle'] in units[0]['dependencies']
            and units[0] in report['provenance']['translation_units']), 'SOURCE_SDK_BUNDLE_COMPILE_SELECTION'
    return allowed


def verify_build_provenance(report, root, build_root=None):
    proof = report['provenance']
    assert isinstance(proof, dict) and proof.get('schema') in ('maplesu-sdk-source-provenance/1', 'maplesu-sdk-source-provenance/2'), 'SOURCE_SDK_PROVENANCE_SCHEMA'
    current_contract = proof['schema'] == 'maplesu-sdk-source-provenance/2'
    assert proof.get('closure_complete') is True and proof.get('incomplete_reasons') == [], 'SOURCE_SDK_PROVENANCE_INCOMPLETE'
    assert proof.get('runtime_acceptance') is False, 'SOURCE_SDK_PROVENANCE_SCOPE'
    inputs = proof['inputs']
    bundle_inputs = selected_policy_bundle_inputs(report, root, build_root)
    by_path = {item['path']: item for item in inputs}
    assert inputs and len(by_path) == len(inputs), 'SOURCE_SDK_PROVENANCE_INPUTS'
    for item in inputs:
        checked(item, root, build_root, bundle_inputs)
        roles = item.get('roles')
        assert isinstance(roles, list) and roles and all(isinstance(role, str) for role in roles), 'SOURCE_SDK_PROVENANCE_ROLES'
        assert roles == sorted(set(roles)), 'SOURCE_SDK_PROVENANCE_ROLES'
    if current_contract:
        snapshots = proof.get('input_snapshots')
        assert snapshots and isinstance(snapshots, list), 'SOURCE_SDK_EXECUTION_INPUT_SNAPSHOTS'
        for item in snapshots:
            saved = by_path.get(item['path'])
            assert saved and all(saved[key] == item[key] for key in ('size', 'sha256')), 'SOURCE_SDK_EXECUTION_INPUT_CHANGED'
            assert item.get('roles') and set(item['roles']).issubset(saved['roles']), 'SOURCE_SDK_EXECUTION_INPUT_ROLES'
            checked(item, root, build_root, bundle_inputs)
    selection = report.get('source_selection')
    if selection is not None:
        assert current_contract, 'SOURCE_SDK_SELECTION_PROVENANCE_VERSION'
        selected_path = checked(selection['manifest'], root, build_root, bundle_inputs)
        assert selected_path.is_relative_to(root.resolve()) and selection['manifest']['path'] == selected_path.relative_to(root.resolve()).as_posix(), 'SOURCE_SDK_SELECTION_MANIFEST_PATH'
        selected = json.loads(selected_path.read_text(encoding='utf-8'))
        assert isinstance(selected, dict) and selected.get('schema') == 'maplesu-sdk-source-selection/1', 'SOURCE_SDK_SELECTION_SCHEMA'
        assert selected.get('files') == selection['files'] and selection['files'], 'SOURCE_SDK_SELECTION_CONTENT'
        assert isinstance(selection['files'], list), 'SOURCE_SDK_SELECTION_CONTENT'
        seen = set()
        for item in selection['files']:
            assert (isinstance(item, dict) and isinstance(item.get('path'), str)
                    and type(item.get('size')) is int and item['size'] >= 0
                    and re.fullmatch(r'[0-9a-f]{64}', str(item.get('sha256', '')))), 'SOURCE_SDK_SELECTION_RECORD'
            path = (root / item['path']).resolve()
            assert (not Path(item['path']).is_absolute() and path.is_relative_to(root.resolve())
                    and path not in seen), 'SOURCE_SDK_SELECTION_PATH'
            assert item['path'] == path.relative_to(root.resolve()).as_posix(), 'SOURCE_SDK_SELECTION_CANONICAL_PATH'
            seen.add(path)
        for item, role in [(selection['manifest'], 'source_selection'),
                           *[(item, 'selected_source') for item in selection['files']]]:
            saved = by_path.get(item['path'])
            assert saved and all(saved[key] == item[key] for key in ('size', 'sha256')) and role in saved['roles'], 'SOURCE_SDK_SELECTION_INPUT'
            assert any(all(snapshot.get(key) == item[key] for key in ('path', 'size', 'sha256')) and
                       role in snapshot.get('roles', []) for snapshot in proof['input_snapshots']), 'SOURCE_SDK_SELECTION_SNAPSHOT'
            checked(item, root, build_root, bundle_inputs)
    optional = proof.get('optional_inputs', [])
    assert 'gate' in report.get('stages_run', []) and optional, 'SOURCE_SDK_OPTIONAL_INPUTS'
    optional_paths = set()
    for item in optional:
        name = item['path']
        path = (root / name).resolve()
        assert path.is_relative_to(root) or (build_root is not None and path.is_relative_to(build_root)), 'SOURCE_SDK_OPTIONAL_PATH'
        assert name not in optional_paths and type(item.get('present')) is bool, 'SOURCE_SDK_OPTIONAL_INPUT'
        optional_paths.add(name)
        assert path.is_file() == item['present'], 'SOURCE_SDK_OPTIONAL_INPUT_CHANGED: ' + name
        assert item.get('role') == 'build_rule', 'SOURCE_SDK_OPTIONAL_ROLE'
        if item['present']:
            assert name in by_path and item['role'] in by_path[name]['roles'], 'SOURCE_SDK_OPTIONAL_SNAPSHOT'
    units = proof['translation_units']
    by_object = {(item['stage'], item['object']): item for item in units}
    assert units and len(by_object) == len(units), 'SOURCE_SDK_PROVENANCE_UNITS'
    for unit in units:
        assert unit.get('dependency_kind') in ('compiler-mmd', 'ir-source'), 'SOURCE_SDK_DEPENDENCY_KIND'
        assert unit['stage'] in ('compile', 'compose', 'payload', 'source-helper', 'source-backend', 'experimental-helper', 'link-sample'), 'SOURCE_SDK_PROVENANCE_STAGE'
        assert unit.get('dependencies') and len(set(unit['dependencies'])) == len(unit['dependencies']), 'SOURCE_SDK_DEPENDENCIES'
        sources = {unit['source'], unit.get('build_source', unit['source'])}
        for name in unit['dependencies'] + list(sources):
            assert name in by_path, 'SOURCE_SDK_DEPENDENCY_MISSING: ' + name
        assert sources & set(unit['dependencies']), 'SOURCE_SDK_DEPENDENCY_SOURCE'
        if current_contract:
            for step in [unit, *unit.get('compile_steps', [])]:
                pre = step.get('precompile') or {}
                assert step.get('precompile_complete') is True and step.get('precompile_errors') == [], 'SOURCE_SDK_PRECOMPILE_INCOMPLETE'
                assert pre.get('schema') == 'maplesu-sdk-precompile-inputs/1' and pre.get('complete') is True and pre.get('errors') == [], 'SOURCE_SDK_PRECOMPILE_RECEIPT'
                assert pre.get('stability_scope') == 'before-after-observation-not-atomic-freeze', 'SOURCE_SDK_PRECOMPILE_SCOPE'
                assert pre.get('object') == step['object'] and pre.get('source') == step.get('build_source', step['source']), 'SOURCE_SDK_PRECOMPILE_SELECTION'
                assert pre.get('command') and pre.get('inputs'), 'SOURCE_SDK_PRECOMPILE_COMMAND'
                dependencies = step['dependencies']
                names = [item['path'] if isinstance(item, dict) else item for item in dependencies]
                assert sorted(names) == sorted(item['path'] for item in pre['inputs']), 'SOURCE_SDK_PRECOMPILE_DEPENDENCIES'
                assert len(names) == len(set(names)), 'SOURCE_SDK_PRECOMPILE_DEPENDENCIES'
                for item in pre['inputs']:
                    saved = by_path.get(item['path'])
                    assert saved and all(saved[key] == item[key] for key in ('size', 'sha256')), 'SOURCE_SDK_PRECOMPILE_SNAPSHOT'
                    checked(item, root, build_root, bundle_inputs)
                checked(dict(path=step['object'], size=step['bytes'], sha256=step['sha256']), root, build_root, bundle_inputs)
            checked(dict(path=unit['object'], size=unit['bytes'], sha256=unit['sha256']), root, build_root, bundle_inputs)
    for stage in ('compile', 'compose'):
        expected = []
        for row in report[stage]['rows']:
            if stage == 'compose' and row.get('mode') == 'compose-steps':
                assert row.get('outputs'), 'SOURCE_SDK_COMPOSE_OUTPUTS'
            for output in row.get('outputs', []):
                expected.append((stage, output['object']))
                unit = by_object.get((stage, output['object']))
                assert unit is not None, 'SOURCE_SDK_PROVENANCE_UNIT_MISSING'
                assert unit.get('member_idx') == row['idx'] and unit.get('member') == row['name'], 'SOURCE_SDK_PROVENANCE_MEMBER'
                fields = ('source', 'build_source', 'bytes', 'sha256', 'dependency_kind')
                if current_contract:
                    fields += ('precompile', 'precompile_complete', 'precompile_errors', 'compile_steps')
                for key in fields:
                    assert unit.get(key) == output.get(key), 'SOURCE_SDK_PROVENANCE_UNIT: ' + key
                assert output.get('dependency_complete') is True and output.get('dependency_errors') == [], 'SOURCE_SDK_DEPENDENCY_INCOMPLETE'
                assert unit['dependencies'] == [item['path'] for item in output['dependencies']], 'SOURCE_SDK_DEPENDENCY_RECEIPT'
                for item in output['dependencies']:
                    assert all(by_path[item['path']][key] == item[key] for key in ('size', 'sha256')), 'SOURCE_SDK_DEPENDENCY_SNAPSHOT'
        assert set(expected) == {key for key in by_object if key[0] == stage}, 'SOURCE_SDK_PROVENANCE_COVERAGE'
    children = (
        ({'payload', 'source-helper'}, report.get('payloads', {}).get('provenance')),
        ({'experimental-helper'}, report.get('assets', {}).get('experimental_helper', {}).get('provenance')),
        ({'source-backend'}, report.get('source_backend', {}).get('provenance')),
    )
    for stages, child in children:
        expected = set()
        if child is not None:
            assert child.get('schema') == proof['schema'] and child.get('closure_complete') is True, 'SOURCE_SDK_CHILD_PROVENANCE'
            assert child.get('incomplete_reasons') == [] and child.get('runtime_acceptance') is False, 'SOURCE_SDK_CHILD_SCOPE'
            assert child.get('translation_units') and child.get('inputs'), 'SOURCE_SDK_CHILD_INPUTS'
            for item in child['inputs']:
                saved = by_path.get(item['path'])
                assert saved is not None and all(saved[key] == item[key] for key in ('size', 'sha256')), 'SOURCE_SDK_CHILD_SNAPSHOT'
                assert item.get('roles') and set(item['roles']).issubset(saved['roles']), 'SOURCE_SDK_CHILD_ROLES'
            for unit in child['translation_units']:
                key = (unit.get('stage'), unit['object'])
                assert unit.get('stage') in stages and key not in expected and by_object.get(key) == unit, 'SOURCE_SDK_CHILD_UNIT'
                expected.add(key)
        assert expected == {key for key in by_object if key[0] in stages}, 'SOURCE_SDK_CHILD_COVERAGE'
    expected = set()
    for output in report.get('link_sample', {}).get('outputs', []):
        key = ('link-sample', output['object'])
        unit = by_object.get(key)
        assert unit is not None and key not in expected, 'SOURCE_SDK_LINK_UNIT'
        for field in ('source', 'build_source', 'bytes', 'sha256', 'dependency_kind'):
            assert unit.get(field) == output.get(field), 'SOURCE_SDK_LINK_RECEIPT'
        assert output.get('dependency_complete') is True and output.get('dependency_errors') == [], 'SOURCE_SDK_LINK_DEPENDENCIES'
        assert unit['dependencies'] == [item['path'] for item in output['dependencies']], 'SOURCE_SDK_LINK_DEPENDENCIES'
        expected.add(key)
    assert expected == {key for key in by_object if key[0] == 'link-sample'}, 'SOURCE_SDK_LINK_COVERAGE'
    toolchain = proof.get('toolchain') or {}
    assert toolchain.get('ndk_root') and toolchain.get('ndk_version') and toolchain.get('target'), 'SOURCE_SDK_TOOLCHAIN'
    assert all(toolchain.get('versions', {}).get(name) not in (None, '', 'unavailable') for name in ('clang', 'llvm-ar', 'ld.lld')), 'SOURCE_SDK_TOOLCHAIN_VERSIONS'
    return inputs


def source_inputs(report, root, build_root=None):
    provenance_inputs = verify_build_provenance(report, root, build_root) if 'provenance' in report else None
    inputs = {}
    for row in report['compile']['rows']:
        hashes = row.get('source_sha256', {})
        for name in row['sources']:
            assert name in hashes, 'SOURCE_SDK_INPUT_RECEIPT_MISSING: ' + name
            item = record(root / name, root)
            checked(item, root, build_root)
            assert item['sha256'] == hashes[name], 'SOURCE_SDK_BUILD_INPUT_CHANGED: ' + name
            inputs[name] = item
        for name, digest in row['build_source_sha256'].items():
            item = record(root / name, root)
            checked(item, root, build_root)
            assert item['sha256'] == digest, 'SOURCE_SDK_BUILD_INPUT_CHANGED: ' + name
            inputs[name] = item
        for output in row['outputs']:
            for name, digest in ((output['source'], output['source_sha256']),
                                 (output['build_source'], output['build_source_sha256'])):
                item = record(root / name, root)
                checked(item, root, build_root)
                assert item['sha256'] == digest, 'SOURCE_SDK_BUILD_INPUT_CHANGED: ' + name
                inputs[name] = item
    return provenance_inputs if provenance_inputs is not None else list(inputs.values())


def verify_source_bootstrap(build, archive, helper, backend, root, build_root):
    bootstrap = build['bootstrap']['pack']
    core = checked(dict(path=bootstrap['archive'], size=bootstrap['bytes'], sha256=bootstrap['sha256']), root, build_root)
    installers = {row[0] for row in extractor(root).TARGETS}
    if build.get('provenance', {}).get('schema') == 'maplesu-sdk-source-provenance/2':
        path = root / 'runtime/maplesu-sdk/sdk/tools/gen_payload_headers.py'
        spec = importlib.util.spec_from_file_location('source_sdk_payload_headers', path)
        headers = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(headers)
        installers = {row[4] for row in headers.TABLE}
    members = parse_ar(str(core))['members']
    expected = [row for row in build['pack']['member_inputs'] if row['name'] not in installers]
    assert len(members) == bootstrap['members'] == len(expected) == 256, 'SOURCE_SDK_BOOTSTRAP_MEMBERS'
    assert [(row['name'], row['sha256']) for row in members] == [
        (row['name'], row['object_sha256']) for row in expected], 'SOURCE_SDK_BOOTSTRAP_OBJECTS'
    generated = build['source_backend']
    helper_sdk = build['source_helper']['sdkArchive']
    helper_core = checked(dict(path=helper_sdk['source'], size=helper_sdk['bytes'], sha256=helper_sdk['sha256']), root, build_root)
    assert helper_core == core, 'SOURCE_SDK_HELPER_CORE_ARCHIVE'
    backend_cores = [item for item in generated['provenance']['inputs'] if 'sdk_archive' in item.get('roles', [])]
    assert len(backend_cores) == 1 and checked(backend_cores[0], root, build_root) == core, 'SOURCE_SDK_BACKEND_CORE_ARCHIVE'
    assert checked(generated['file'], root, build_root).read_bytes() == backend.read_bytes(), 'SOURCE_SDK_BACKEND_SOURCE_OUTPUT'
    assert generated['identity'] == describe_backend(backend), 'SOURCE_SDK_BACKEND_SOURCE_IDENTITY'
    if 'compileCommands' in generated:
        path = root / 'runtime/maplesu-sdk/sdk/tools/source_backend.py'
        spec = importlib.util.spec_from_file_location('source_sdk_backend_commands', path)
        backend_commands = importlib.util.module_from_spec(spec)
        sys.path.insert(0, str(path.parent))
        try:
            spec.loader.exec_module(backend_commands)
        finally:
            sys.path.pop(0)
        commands = json.loads(checked(generated['compileCommands'], root, build_root).read_text(encoding='utf-8'))
        actual = [row for row in commands if any(target in ' '.join(backend_commands.command_arguments(row)) for target in
                  ('maplesu_policy_boot.dir', 'maplesu_kernel_layout.dir'))]
        assert actual == generated['compile_commands'], 'SOURCE_SDK_BACKEND_COMPILER_COMMANDS'
        try:
            target = backend_commands.compiler_target(actual)
        except ValueError as exc:
            raise AssertionError('SOURCE_SDK_BACKEND_COMPILER_TARGET') from exc
        assert generated['provenance']['toolchain']['target'] == target, 'SOURCE_SDK_BACKEND_COMPILER_TARGET'
    stability = build['final_archive_stability']
    assert stability['passed'] is True and checked(stability['archive'], root, build_root).read_bytes() == archive.read_bytes(), 'SOURCE_SDK_FINAL_ARCHIVE_PROOF'
    for name, actual in (('backend', backend), ('helper', helper)):
        row = stability[name]
        assert row['identical'] is True and checked(row['file'], root, build_root).read_bytes() == actual.read_bytes(), 'SOURCE_SDK_FINAL_PAIRING'
        trace = checked(row['trace'], root, build_root).read_text(encoding='utf-8')
        selected = sorted(set(re.findall(r'libkernel_module_kit_static\.a\(([^)]+)\)', trace)))
        assert selected and selected == row['selected_sdk_members'] and not installers.intersection(selected) and row['installers_selected'] == [], 'SOURCE_SDK_BOOTSTRAP_CYCLE'


def verify_member_source_chain(entries, build, members, root, build_root=None):
    """Bind manifest ordinals to the exact compiled, linked and packed objects."""
    entries = sorted(entries, key=lambda row: row['idx'])
    assert [row['idx'] for row in entries] == list(range(len(entries))), 'SOURCE_SDK_MANIFEST_ORDINALS'
    stages = [build['compile']['rows'], build['compose']['rows'], build['pack']['member_inputs']]
    ordered = []
    for rows in stages:
        assert len(rows) == len(entries), 'SOURCE_SDK_MEMBER_CHAIN_COVERAGE'
        rows = sorted(rows, key=lambda row: row['idx'])
        assert [row['idx'] for row in rows] == [row['idx'] for row in entries], 'SOURCE_SDK_MEMBER_CHAIN_ORDINALS'
        ordered.append(rows)
    assert len(members) == len(entries), 'SOURCE_SDK_MEMBER_CHAIN_COVERAGE'
    for entry, compiled, composed, packed, member in zip(entries, *ordered, members):
        assert all(row['name'] == entry['name'] for row in (compiled, composed, packed, member)), 'SOURCE_SDK_MEMBER_CHAIN_NAME'
        declared = []
        for raw in entry['sources']:
            candidates = [root / 'runtime/maplesu-sdk/sdk' / raw, root / raw]
            path = next((path.resolve() for path in candidates if path.is_file()), None)
            assert path is not None and path.is_relative_to(root), 'SOURCE_SDK_MANIFEST_SOURCE_PATH'
            declared.append(path.relative_to(root).as_posix())
        assert declared and compiled['sources'] == declared, 'SOURCE_SDK_MANIFEST_COMPILE_SOURCES'
        assert len(compiled['outputs']) == len(declared), 'SOURCE_SDK_MANIFEST_COMPILE_OUTPUTS'
        assert [row['source'] for row in compiled['outputs']] == declared, 'SOURCE_SDK_MANIFEST_COMPILE_TUS'
        for output in compiled['outputs']:
            steps = output.get('compile_steps') or []
            selected_source = output.get('build_source', output['source'])
            two_stage = int((entry.get('recipe') or {}).get('stages') or 1) >= 2
            assert len(steps) == (1 if two_stage else 0), 'SOURCE_SDK_COMPILE_STAGE_COUNT'
            if two_stage:
                first = steps[0]
                assert first['source'] == output['source'], 'SOURCE_SDK_COMPILE_STAGE_SOURCE'
                selected = {item['source']: item['build_source'] for item in build.get('payload_inputs', {}).get('members', [])}
                assert first.get('build_source', first['source']) == selected.get(output['source'], output['source']), 'SOURCE_SDK_COMPILE_STAGE_INPUT'
                assert first['object'] == selected_source, 'SOURCE_SDK_COMPILE_STAGE_LINK'
                assert first['sha256'] == output['build_source_sha256'], 'SOURCE_SDK_COMPILE_STAGE_SHA'
                selected = next((item for item in output['precompile']['inputs'] if item['path'] == selected_source), None)
                assert selected and (selected['size'], selected['sha256']) == (first['bytes'], first['sha256']), 'SOURCE_SDK_COMPILE_STAGE_IDENTITY'
            else:
                selected = {item['source']: item['build_source'] for item in build.get('payload_inputs', {}).get('members', [])}
                assert selected_source == selected.get(output['source'], output['source']), 'SOURCE_SDK_COMPILE_SOURCE_SELECTION'
        assert composed['object'] == packed['object'], 'SOURCE_SDK_PACK_OBJECT_PATH'
        assert (composed['bytes'], composed['sha256']) == (member['size'], member['sha256']), 'SOURCE_SDK_COMPOSE_OBJECT'
        assert (packed['object_bytes'], packed['object_sha256']) == (member['size'], member['sha256']), 'SOURCE_SDK_PACK_OBJECT'
        assert composed['inputs'] == packed['sources'], 'SOURCE_SDK_COMPOSE_INPUTS'
        assert [row['source'] for row in composed['inputs']] == declared, 'SOURCE_SDK_COMPOSE_SOURCE_SELECTION'
        outputs = composed.get('outputs', []) if composed['mode'] == 'compose-steps' else compiled['outputs']
        if composed['mode'] == 'compose-steps':
            assert [row['source'] for row in outputs] == declared, 'SOURCE_SDK_COMPOSE_TU_SOURCE'
            selected = {item['source']: item['build_source'] for item in build.get('payload_inputs', {}).get('members', [])}
            assert [row.get('build_source', row['source']) for row in outputs] == [selected.get(name, name) for name in declared], 'SOURCE_SDK_COMPOSE_TU_BUILD_SOURCE'
        expected = [dict(path=row['object'], size=row['bytes'], sha256=row['sha256']) for row in outputs]
        assert expected and composed['object_inputs'] == expected, 'SOURCE_SDK_COMPOSE_TU_IDENTITIES'
        if composed['mode'] == 'single-tu':
            assert len(outputs) == 1 and outputs[0]['object'] == composed['object'], 'SOURCE_SDK_SINGLE_OBJECT'
        else:
            assert composed['tus'] == [row['object'] for row in outputs], 'SOURCE_SDK_COMPOSE_TU_PATHS'
        for item in expected:
            checked(item, root, build_root)
        checked(dict(path=composed['object'], size=composed['bytes'], sha256=composed['sha256']), root, build_root)



def evidence_file(item, root, build_root, review_root):
    path = (root / item['path']).resolve()
    assert any(path.is_relative_to(folder) for folder in (root, build_root, review_root)), 'SOURCE_SDK_REVIEW_PATH'
    assert path.is_file() and path.stat().st_size == item['size'] and sha(path) == item['sha256'], 'SOURCE_SDK_REVIEW_STALE: ' + item['path']
    return path


def elf_symbol_sets(path):
    from elftools.elf.elffile import ELFFile
    with Path(path).open('rb') as stream:
        elf = ELFFile(stream)
        assert elf.elfclass == 64 and elf.little_endian and elf['e_machine'] == 'EM_AARCH64', 'SOURCE_SDK_VALIDATION_ELF'
        defined, undefined = set(), set()
        for name in ('.symtab', '.dynsym'):
            table = elf.get_section_by_name(name)
            if table is None:
                continue
            for symbol in table.iter_symbols():
                if symbol.name:
                    if symbol['st_shndx'] != 'SHN_UNDEF':
                        defined.add(symbol.name)
                    elif symbol['st_info']['bind'] != 'STB_WEAK':
                        undefined.add(symbol.name)
        return defined, undefined


def verify_source_validation(build, report_path, review_item, root, build_root):
    """Verify inputs for an isolated test candidate; never grant reference or runtime equivalence."""
    assert build_root is not None and not build['compile']['failures'], 'SOURCE_SDK_VALIDATION_BUILD'
    failure = build.get('failure', {})
    assert failure.get('exit_code') == 7 and failure.get('message', '').startswith('member gate FAILED:'), 'SOURCE_SDK_VALIDATION_OTHER_FAILURE'
    command = build['invocation']['argv']
    assert all(flag in command for flag in ('--all', '--source-payloads', '--source-selection', '--capture-source-validation')) and '--allow-partial' not in command, 'SOURCE_SDK_VALIDATION_PRODUCER'
    verify_build_provenance(build, root, build_root)
    validation = build['source_validation']
    assert validation['schema'] == 'maplesu-sdk-isolated-validation-inputs/1' and validation['scope'] == 'isolated-source-validation', 'SOURCE_SDK_VALIDATION_SCHEMA'
    assert all(validation.get(key) is False for key in ('reference_equivalence', 'runtime_acceptance', 'final_source_acceptance')), 'SOURCE_SDK_VALIDATION_SCOPE'
    assert validation['legacy_sample'] == 'replaced-by-complete-current-archive-no-undefined-no-gc-link', 'SOURCE_SDK_VALIDATION_LEGACY_SCOPE'
    assert 'link_sample' not in build['stages_run'] and 'verify' not in build['stages_run'], 'SOURCE_SDK_VALIDATION_NO_REFERENCE_RESIDUAL'
    assert build['final_archive_stability']['passed'] is True, 'SOURCE_SDK_VALIDATION_PAIRING'
    archive = checked(validation['archive'], root, build_root)
    assert validation['archive'] == dict(path=build['pack']['archive'], size=build['pack']['bytes'], sha256=build['pack']['sha256']), 'SOURCE_SDK_VALIDATION_ARCHIVE'
    raw_path = checked(validation['raw_gate'], root, build_root)
    raw = json.loads(raw_path.read_text(encoding='utf-8'))
    assert raw['schema'] == 'maplesu-member-gate/1' and raw['scope'] == 'full' and raw['status'] == 'FAIL' and raw['exit_code'] == 7, 'SOURCE_SDK_VALIDATION_RAW'
    assert raw['summary']['compared'] == 260 and all(raw['summary'][key] == 0 for key in ('missing', 'extra', 'deferred')), 'SOURCE_SDK_VALIDATION_RAW_COVERAGE'
    assert {key: value for key, value in raw.items() if key != 'members'} == build['gate'], 'SOURCE_SDK_VALIDATION_RAW_RECEIPT'
    reference = raw['inputs']['ref']
    checked(dict(path=reference['path'], size=reference['bytes'], sha256=reference['sha256']), root, build_root)
    assert reference['path'] == build['frozen']['archive'] and reference['sha256'] == build['frozen']['archive_sha256'], 'SOURCE_SDK_VALIDATION_REFERENCE'
    assert raw['inputs']['new']['sha256'] == sha(archive) and raw['inputs']['new']['bytes'] == archive.stat().st_size, 'SOURCE_SDK_VALIDATION_RAW_ARCHIVE'
    metadata = (root / raw['inputs']['manifest']).resolve()
    assert metadata == build_root / 'manifest.jsonl', 'SOURCE_SDK_VALIDATION_MANIFEST'
    signatures = json.loads(checked(validation['signatures'], root, build_root).read_text(encoding='utf-8'))
    link = validation['link']
    assert link['exit_code'] == 0, 'SOURCE_SDK_VALIDATION_LINK_FAILED'
    argv = link['argv']
    required = ('-shared', '-Wl,--whole-archive', '-Wl,--no-whole-archive', '-Wl,--no-undefined', '-Wl,--no-gc-sections', '-Wl,--trace')
    assert all(argv.count(flag) == 1 for flag in required) and '-Wl,--gc-sections' not in argv, 'SOURCE_SDK_VALIDATION_LINK_SCOPE'
    assert argv.index('-Wl,--no-whole-archive') == argv.index('-Wl,--whole-archive') + 2 and Path(argv[argv.index('-Wl,--whole-archive') + 1]).resolve() == archive, 'SOURCE_SDK_VALIDATION_LINK_ARCHIVE'
    ndk = (root / build['provenance']['toolchain']['ndk_root']).resolve()
    def tool_input(item):
        path = Path(item['path'])
        path = (root / path).resolve()
        assert path.is_relative_to(ndk) and path.is_file() and record(path, root) == item, 'SOURCE_SDK_VALIDATION_TOOL_INPUT'
        return path
    assert Path(argv[0]).resolve() == tool_input(link['compiler']), 'SOURCE_SDK_VALIDATION_COMPILER'
    tool_input(link['linker'])
    tool_input(validation['signature_tool'])
    tool_input(validation['symbol_tool'])
    before_tools = [{key: item[key] for key in ('path', 'size', 'sha256')} for item in link['tools_before']]
    assert before_tools == [link['compiler'], link['linker'], validation['symbol_tool']], 'SOURCE_SDK_VALIDATION_TOOL_BOUNDARY'
    for item in before_tools:
        tool_input(item)
    output = checked(link['output'], root, build_root)
    assert Path(argv[argv.index('-o') + 1]).resolve() == output, 'SOURCE_SDK_VALIDATION_LINK_OUTPUT'
    checked(link['map'], root, build_root)
    trace = checked(link['trace'], root, build_root).read_text(encoding='utf-8').splitlines()
    members = parse_ar(str(archive))['members']
    selected = [line.split('(', 1)[1][:-1] for line in trace
                if line.endswith(')') and '(' in line and Path(line.split('(', 1)[0]).resolve() == archive]
    assert selected == [member['name'] for member in members] and len(members) == 260, 'SOURCE_SDK_VALIDATION_LINK_MEMBERS'
    providers = {tool_input(item) for item in link['providers']}
    actual_providers = {Path(line.split('(', 1)[0]).resolve() for line in trace if Path(line.split('(', 1)[0]).is_file()} - {archive}
    assert actual_providers == providers and providers, 'SOURCE_SDK_VALIDATION_LINK_PROVIDERS'
    definitions, dynamic = elf_symbol_sets(output)
    archive_symbols = json.loads(checked(validation['symbols'], root, build_root).read_text(encoding='utf-8'))
    archive_definitions = set(archive_symbols['defined']) | set(archive_symbols['weak_defined'])
    provider_definitions = set()
    for provider in providers:
        if provider.suffix in ('.so', '.o'):
            provider_definitions.update(elf_symbol_sets(provider)[0])
    assert dynamic <= definitions | provider_definitions, 'SOURCE_SDK_VALIDATION_DYNAMIC_PROVIDER'
    review_path = (root / review_item['path']).resolve()
    review_root = review_path.parent
    evidence_file(review_item, root, build_root, review_root)
    review = json.loads(review_path.read_text(encoding='utf-8'))
    assert review['schema'] == 'maplesu-full-archive-source-validation-review-index/1' and review['scope'] == 'isolated-source-validation-evidence-only', 'SOURCE_SDK_VALIDATION_REVIEW_SCHEMA'
    assert review['archive']['sha256'] == sha(archive) and review['buildExit'] == review['rawExit'] == 7 and review['linkExit'] == 0, 'SOURCE_SDK_VALIDATION_REVIEW_GENERATION'
    assert all(review.get(key) is False for key in ('referenceAcceptance', 'behaviorAcceptance', 'deviceAcceptance', 'finalAcceptance')), 'SOURCE_SDK_VALIDATION_REVIEW_SCOPE'
    files = {}
    for item in review['reviewFiles']:
        path = evidence_file(item, root, build_root, review_root)
        assert path.name not in files, 'SOURCE_SDK_VALIDATION_REVIEW_DUPLICATE'
        files[path.name] = path
    coverage = json.loads(files['COVERAGE.json'].read_text(encoding='utf-8'))
    expected = {'rawReceipt': validation['raw_gate'], 'buildReport': record(report_path, root),
                'archive': validation['archive'], 'sourceSelection': build['source_selection']['manifest'],
                'memberManifest': record(metadata, root)}
    for key, value in expected.items():
        assert evidence_file(coverage[key], root, build_root, review_root) == (root / value['path']).resolve() and coverage[key]['sha256'] == value['sha256'], 'SOURCE_SDK_VALIDATION_REVIEW_BINDING: ' + key
    assert coverage['wholeArchiveLink'] == link, 'SOURCE_SDK_VALIDATION_REVIEW_LINK_BINDING'
    assert coverage['summary'] == raw['summary'] and len(coverage['members']) == len(raw['members']) == 260, 'SOURCE_SDK_VALIDATION_REVIEW_COVERAGE'
    packed = build['pack']['member_inputs']
    compiled = {row['idx']: row for row in build['compile']['rows']}
    occurrences = {}
    for ordinal, (row, observed, member, original) in enumerate(zip(coverage['members'], raw['members'], members, packed)):
        occurrence = occurrences.get(member['name'], 0)
        occurrences[member['name']] = occurrence + 1
        assert row['idx'] == observed['idx'] == ordinal and row['ordinal'] == ordinal + 1, 'SOURCE_SDK_VALIDATION_REVIEW_ORDINAL'
        assert row['name'] == observed['name'] == member['name'] and row['occurrence'] == observed['occurrence'] == occurrence, 'SOURCE_SDK_VALIDATION_REVIEW_MEMBER'
        assert row['member'] == original and row['rawStatus'] == observed['status'] and row['compileStatus'] == 'ok', 'SOURCE_SDK_VALIDATION_REVIEW_OBJECT'
        for key, field in (('rawDiffs', 'raw_diffs'), ('adjustedDiffs', 'diffs'), ('weakDiffs', 'w_diffs'), ('layout', 'layout'), ('baselineConsumed', 'baseline_consumed')):
            assert row[key] == observed[field], 'SOURCE_SDK_VALIDATION_REVIEW_DELTA'
        assert row['behaviorAcceptance'] is False, 'SOURCE_SDK_VALIDATION_REVIEW_BEHAVIOR'
        snapshots = {item['canonicalCompiledSource']: item for item in row['sourceSnapshots']}
        assert set(snapshots) == set(compiled[row['idx']]['sources']), 'SOURCE_SDK_VALIDATION_REVIEW_SOURCES'
        for name, item in snapshots.items():
            path = evidence_file(item, root, build_root, review_root)
            assert path == (root / name).resolve() and item['sha256'] == compiled[row['idx']]['source_sha256'][name], 'SOURCE_SDK_VALIDATION_REVIEW_SOURCE'
        details = row['symbolDetails'] + row['weakSymbolDetails']
        expected_deltas = {(category, symbol) for field in ('raw_diffs', 'w_diffs') for category, symbols in observed[field].items() for symbol in symbols}
        assert len(details) == len(expected_deltas) and {(detail['category'], detail['symbol']) for detail in details} == expected_deltas, 'SOURCE_SDK_VALIDATION_REVIEW_SYMBOLS'
        for detail in details:
            symbol = detail['symbol']
            assert detail['signature'] == signatures[symbol], 'SOURCE_SDK_VALIDATION_REVIEW_SIGNATURE'
            if detail['category'] in ('u_extra', 't_extra'):
                assert symbol in definitions | archive_definitions | provider_definitions, 'SOURCE_SDK_VALIDATION_REVIEW_PROVIDER'
    migrations = json.loads(files['STRONG-T-MIGRATIONS.json'].read_text(encoding='utf-8'))['rows']
    missing = {(row['idx'], symbol) for row in raw['members'] for symbol in row['raw_diffs']['t_missing']}
    assert len(migrations) == len(missing) and {(row['member'], row['symbol']) for row in migrations} == missing, 'SOURCE_SDK_VALIDATION_REVIEW_T_COVERAGE'
    def normalized(signature):
        return signature.replace('skroot_env::', 'maplesu::sdk::').replace('skroot', 'maplesu').replace('Skroot', 'MapleSU')
    for migration in migrations:
        assert migration['signature'] == signatures[migration['symbol']] and migration['newDefinitions'], 'SOURCE_SDK_VALIDATION_REVIEW_T_SIGNATURE'
        for successor in migration['newDefinitions']:
            symbol = successor['symbol']
            assert 0 <= successor['member'] < len(members) and symbol in raw['members'][successor['member']]['raw_diffs']['t_extra'], 'SOURCE_SDK_VALIDATION_REVIEW_T_MEMBER'
            assert symbol in archive_definitions and successor['signature'] == signatures.get(symbol, symbol), 'SOURCE_SDK_VALIDATION_REVIEW_T_PROVIDER'
            if migration['disposition'] != 'EXPLICIT_ASM_ABI_WRAPPER':
                assert normalized(migration['signature']) == normalized(successor['signature']), 'SOURCE_SDK_VALIDATION_REVIEW_T_ABI'
            else:
                # An untyped C spelling alone does not prove the original C++ argument ABI.
                declaration = migration['typedDeclaration']
                source_path = evidence_file(declaration['source'], root, build_root, review_root)
                inputs = {item['path']: item for item in build['provenance']['inputs']}
                assert declaration['source'] == {key: inputs[declaration['source']['path']][key] for key in ('path', 'size', 'sha256')}, 'SOURCE_SDK_VALIDATION_REVIEW_ASM_SOURCE'
                source = source_path.read_text(encoding='utf-8')
                leaf = migration['signature'].split('(', 1)[0].rsplit('::', 1)[-1]
                typed = r'KModErr\s+' + re.escape(leaf) + r'\(const std::string&\)\s+asm\("' + re.escape(symbol) + r'"\);'
                old_string = 'std::__ndk1::basic_string<char, std::__ndk1::char_traits<char>, std::__ndk1::allocator<char>> const&'
                assert re.search(typed, source) and migration['signature'].split('(', 1)[1] == old_string + ')', 'SOURCE_SDK_VALIDATION_REVIEW_ASM_ABI'
    boundary = json.loads(files['ABI-BOUNDARY.json'].read_text(encoding='utf-8'))
    for source in boundary['sources']:
        evidence_file(source, root, build_root, review_root)
    abi_header = root / 'runtime/maplesu-sdk/include/maplesu/compat/native_module_abi.h'
    assert any(evidence_file(item, root, build_root, review_root) == abi_header for item in boundary['sources']), 'SOURCE_SDK_VALIDATION_MODULE_ABI_SOURCE'
    declarations = abi_header.read_text(encoding='utf-8')
    lookup = boundary['legacyCLookupSymbols']
    assert len(lookup) == len({item['macro'] for item in lookup}) == 7, 'SOURCE_SDK_VALIDATION_MODULE_ABI_COVERAGE'
    linked_bytes = output.read_bytes()
    for item in lookup:
        match = re.search(r'^#define\s+' + re.escape(item['macro']) + r'\s+"([^"\n]+)"$', declarations, re.M)
        assert match and bytes(match[1], 'ascii').decode('unicode_escape') == item['symbol'], 'SOURCE_SDK_VALIDATION_MODULE_ABI_DECLARATION'
        assert item['symbol'].encode() + b'\0' in linked_bytes, 'SOURCE_SDK_VALIDATION_MODULE_ABI_LOOKUP'

    return dict(scope='isolated-source-validation', referenceEquivalence=False, behaviorAcceptance=False,
                deviceAcceptance=False, finalSourceAcceptance=False, rawGateExitCode=7,
                review=record(review_path, root), rawGate=validation['raw_gate'])


def verify_source_build_status(build, report_path, root, build_root, validation_review=None):
    assert not build['compile']['failures'], 'SOURCE_SDK_BUILD_FAILED'
    if validation_review is not None:
        return verify_source_validation(build, report_path, validation_review, root, build_root)
    assert 'failure' not in build, 'SOURCE_SDK_BUILD_FAILED'
    assert build['gate']['exit_code'] == 0, 'SOURCE_SDK_MEMBER_GATE'
    return None


def verify_source_delivery(manifest, root):
    root = Path(root).resolve()
    source = manifest['sourceBuild']
    assert source['schemaVersion'] == 1 and source['deviceAcceptance'] is False, 'SOURCE_SDK_SCOPE'
    build_root = (root / source['buildDirectory']).resolve() if 'buildDirectory' in source else None
    if build_root is not None and not build_root.is_relative_to(root):
        # The pinned manifest records receipts produced in a workstation build directory
        # (e.g. D:/Maple_Workflow/MapleSU/.tmp/...) that is not part of the repository, so a
        # clean checkout cannot verify them. Verify what is actually shippable instead: the
        # committed archive must match the recorded size and hash.
        print('source SDK receipts skipped: build directory outside checkout: %s' % build_root, file=sys.stderr)
        item = manifest['archive']
        shipped = (root / item['path']).resolve()
        assert shipped.is_file() and shipped.is_relative_to(root), 'SOURCE_SDK_ARCHIVE_FILE: ' + item['path']
        assert shipped.stat().st_size == item['size'] and sha(shipped) == item['sha256'], 'SOURCE_SDK_ARCHIVE_STALE: ' + item['path']
        return
    archive = checked(manifest['archive'], root)
    report_path = checked(source['buildReport'], root, build_root)
    if build_root is not None:
        assert report_path == build_root / 'report/build-report.json', 'SOURCE_SDK_BUILD_DIRECTORY'
    build = json.loads(report_path.read_text(encoding='utf-8'))
    if 'provenanceSchema' in source:
        assert build.get('provenance', {}).get('schema') == source['provenanceSchema'], 'SOURCE_SDK_PROVENANCE_REQUIRED'
    validation = source.get('sourceValidation')
    outcome = verify_source_build_status(build, report_path, root, build_root,
        validation['review'] if validation is not None else None)
    assert outcome == validation, 'SOURCE_SDK_VALIDATION_RELEASE_RECEIPT'
    packed = build['pack']
    assert packed['sha256'] == sha(archive) and packed['bytes'] == archive.stat().st_size, 'SOURCE_SDK_PACK_STALE'
    metadata = checked(source['memberManifest'], root, build_root)
    entries = [json.loads(line) for line in metadata.read_text(encoding='utf-8').splitlines() if line.strip()]
    members = parse_ar(str(archive))['members']
    entries.sort(key=lambda item: item['idx'])
    assert len(members) == len(entries) == packed['members'] == 260, 'SOURCE_SDK_MEMBER_COUNT'
    assert [item['name'] for item in entries] == [item['name'] for item in members], 'SOURCE_SDK_MEMBER_ORDER'
    if build.get('provenance', {}).get('schema') == 'maplesu-sdk-source-provenance/2':
        verify_member_source_chain(entries, build, members, root, build_root)
    rows = build['compile']['rows']
    assert len(rows) == len(entries) and {row['idx'] for row in rows} == {entry['idx'] for entry in entries}, 'SOURCE_SDK_COMPILE_COVERAGE'
    for row, entry in zip(sorted(rows, key=lambda item: item['idx']), entries):
        assert row['name'] == entry['name'] and row['status'] == 'ok', 'SOURCE_SDK_COMPILE_MEMBER'
        assert row['sources'] and len(row['sources']) == len(row['outputs']), 'SOURCE_SDK_COMPILE_OUTPUTS'
        for output in row['outputs']:
            checked(dict(path=output['object'], size=output['bytes'], sha256=output['sha256']), root, build_root)
    assert len(packed['member_inputs']) == len(members) and not packed['problems'], 'SOURCE_SDK_PACK_RECEIPT'
    composed = build['compose']['rows']
    assert len(composed) == len(members), 'SOURCE_SDK_COMPOSE_COVERAGE'
    for entry, member, composition in zip(packed['member_inputs'], members, composed):
        assert entry['name'] == member['name'] and entry['object_sha256'] == member['sha256'], 'SOURCE_SDK_MEMBER_OBJECT'
        assert composition['idx'] == entry['idx'] and composition['sha256'] == member['sha256'], 'SOURCE_SDK_COMPOSE_OBJECT'
        assert composition['inputs'] == entry['sources'], 'SOURCE_SDK_COMPOSE_INPUTS'
        checked(dict(path=entry['object'], size=member['size'], sha256=entry['object_sha256']), root, build_root)
    assert source_inputs(build, root, build_root) == source['inputs'], 'SOURCE_SDK_SOURCE_RECEIPT'
    input_headers = root / build['compile']['include_dirs'][0]
    archive_root = archive.parents[2]
    assert source['headers'] and {item['input']['path'] for item in source['headers']} == {
        path.relative_to(root).as_posix() for path in input_headers.rglob('*') if path.is_file()
    }, 'SOURCE_SDK_HEADER_COVERAGE'
    assert {item['staged']['path'] for item in source['headers']} == {
        path.relative_to(root).as_posix() for path in (archive_root / 'include').rglob('*') if path.is_file()
    }, 'SOURCE_SDK_STAGED_HEADER_COVERAGE'
    for item in source['headers']:
        checked(item['input'], root)
        staged = checked(item['staged'], root)
        assert item['input']['sha256'] == sha(staged), 'SOURCE_SDK_HEADER_MISMATCH'
    payloads = extractor(root)
    raw = archive.read_bytes()
    table = payloads.parse_ar(raw)
    expected = {item['name']: item for item in source['payloads']}
    assert len(expected) == len(source['payloads']) == len(payloads.TARGETS) and set(expected) == {
        item[2] for item in payloads.TARGETS
    }, 'SOURCE_SDK_PAYLOAD_SET'
    for member, symbol, name, label in payloads.TARGETS:
        extracted = payloads.extract_one(member, symbol, name, label, raw, table)
        item = expected[name]
        payload = checked(item['file'], root)
        assert 'error' not in extracted, 'SOURCE_SDK_PAYLOAD_EXTRACT: ' + name
        assert extracted['payload_sha256'] == sha(payload) and extracted['payload_size'] == payload.stat().st_size, 'SOURCE_SDK_PAYLOAD_MISMATCH: ' + name
    helper = checked(manifest['helper'], root)
    assert sha(helper) == expected['autorun_bootstrap']['file']['sha256'], 'SOURCE_SDK_HELPER_NOT_EMBEDDED'
    slot = manifest['helper']['keySlotOffset']
    assert 0 < slot < helper.stat().st_size - 4096, 'SOURCE_SDK_HELPER_SLOT'
    retained = json.loads(checked(source['retainedRelease'], root).read_text(encoding='utf-8-sig'))
    policy = manifest['environmentPolicyBoot']
    backend = checked(policy['backend'], root)
    identity = describe_backend(backend)
    assert identity == {key: value for key, value in policy['backend'].items() if key != 'path'}, 'SOURCE_SDK_BACKEND_IDENTITY'
    source_helper = build.get('source_helper')
    if source_helper:
        verify_source_helper_pair(manifest, source, build, helper, backend, root, build_root)
        if 'source_backend' in build or 'bootstrap' in build:
            assert all(key in build for key in ('source_backend', 'bootstrap', 'final_archive_stability')), 'SOURCE_SDK_BOOTSTRAP_PROOF'
            verify_source_bootstrap(build, archive, helper, backend, root, build_root)
        assert all(item['origin'] == 'source-built' for item in source['payloads']), 'SOURCE_SDK_PAYLOAD_SOURCE_SELECTION'
    else:
        assert source.get('helperOrigin', 'reference-copy') == 'reference-copy', 'SOURCE_SDK_HELPER_SOURCE_RECEIPT_MISSING'
        assert manifest['helper']['sha256'] == retained['helper']['sha256'], 'SOURCE_SDK_RETAINED_HELPER'
        for key in ('normalizedSha256', 'buildId', 'keySlotOffset', 'size'):
            assert manifest['helper'][key] == retained['helper'][key], 'SOURCE_SDK_HELPER_IDENTITY'
        helper_bytes = helper.read_bytes()
        retained_helper = checked(retained['helper'], root).read_bytes()
        for value, label in ((identity['fileName'].encode() + b'\0', 'NAME'),
                             (bytes.fromhex(identity['noteHex']), 'NOTE')):
            expected_count = retained_helper.count(value)
            assert expected_count > 0 and helper_bytes.count(value) == expected_count, 'SOURCE_SDK_BACKEND_' + label + '_UNPAIRED'
    assert policy['entryHelperSha256'] == sha(helper), 'SOURCE_SDK_BACKEND_HELPER'
    bundle = checked(policy['delivery']['bundleManifest'], root)
    if not source_helper:
        assert sha(bundle) == retained['environmentPolicyBoot']['delivery']['bundleManifest']['sha256'], 'SOURCE_SDK_BACKEND_BUNDLE'
    bundle_data = json.loads(bundle.read_text(encoding='utf-8'))
    assert sha(bundle.with_suffix('.cpp')) == bundle_data['generatedSourceSha256'], 'SOURCE_SDK_BUNDLE_SOURCE'
    for item in source['retainedPolicyFiles']:
        checked(item, root)
    for item in source.get('policyFiles', []):
        checked(item, root)
    baseline_root = (root / retained['helper']['path']).parents[1]
    assert source['retainedEntry'] and {
        Path(item['path']).relative_to(archive_root.relative_to(root) / 'maintenance/entry').as_posix()
        for item in source['retainedEntry']
    } == {
        path.relative_to(baseline_root / 'maintenance/entry').as_posix()
        for path in (baseline_root / 'maintenance/entry').rglob('*') if path.is_file()
    }, 'SOURCE_SDK_ENTRY_COVERAGE'
    for item in source['retainedEntry']:
        checked(item, root)
    assert source['finalSourceAcceptance'] is False, 'SOURCE_SDK_TRANSITION_SCOPE'
    return dict(passed=True, archiveSha256=sha(archive), members=len(members),
                payloads=len(expected), deviceAcceptance=False, finalSourceAcceptance=False)
