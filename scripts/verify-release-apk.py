#!/usr/bin/env python3
"""Verify a delivered Release APK against its build outputs and recorded SDK/bridge inputs."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile
sys.path.insert(0, str(Path(__file__).resolve().parent))
from policy_claim_routes import verify_manifest_routes

ROOT = Path(__file__).resolve().parents[1]
BRIDGE_INPUTS = {'target-boot', 'kernel-image', 'target-config', 'symvers', 'bridge',
                 'handoff', 'handoff-verifier', 'target-identity', 'target-contract', 'btf'}
PAYLOADS = {'su', 'resetprop', 'webui_loader', 'autorun_bootstrap'}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def file_identity(info, include_ctime=True):
    identity = (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns)
    return identity + (info.st_ctime_ns,) if include_ctime else identity


def record(path):
    path = path.resolve()
    with path.open('rb') as stream:
        before = os.fstat(stream.fileno())
        checksum = hashlib.file_digest(stream, 'sha256').hexdigest()
        after = os.fstat(stream.fileno())
        # Windows fstat and pathname stat can report different ctime semantics.
        # Keep the fd change-time check; compare compatible fields across APIs.
        compare_ctime = os.name != 'nt'
        require(file_identity(before) == file_identity(after) and
                file_identity(after, compare_ctime) == file_identity(path.stat(), compare_ctime),
                'SOURCE_CHANGED_WHILE_READING: ' + str(path))
    return dict(path=path.resolve().as_posix(), size=after.st_size, sha256=checksum)


def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def load_bound_json(path, changed_message):
    path = path.resolve()
    with path.open('rb') as stream:
        before = os.fstat(stream.fileno())
        data = stream.read()
        value = json.loads(data.decode('utf-8-sig'))
        after = os.fstat(stream.fileno())
        compare_ctime = os.name != 'nt'
        require(file_identity(before) == file_identity(after) and
                file_identity(after, compare_ctime) == file_identity(path.stat(), compare_ctime),
                changed_message)
    return value, dict(path=path.as_posix(), size=len(data), sha256=digest(data))


def matches(path, expected):
    actual = record(path)
    require(actual['sha256'] == expected['sha256'] and
            actual['size'] == expected.get('size', expected.get('bytes')),
            'SOURCE_IDENTITY_MISMATCH: ' + str(path))
    return actual


def certificate(output, expected):
    require('Number of signers: 1' in output.splitlines(), 'KSU_MANAGER_SIGNER_COUNT_MISMATCH')
    values = re.findall(
        r'^(?:Signer #1|V2 Signer:) certificate SHA-256 digest:[ \t]*([0-9a-fA-F]{64})[ \t]*\r?$',
        output, re.MULTILINE)
    require(len(values) == 1, 'KSU_MANAGER_CERTIFICATE_MISSING_OR_AMBIGUOUS')
    require(values[0].lower() == expected.lower(), 'KSU_MANAGER_CERTIFICATE_MISMATCH')
    return values[0].lower()


def verify_claim_release_version(manifest, sdk):
    require(manifest.get('appVersion') == sdk.get('appVersion') and
            type(manifest.get('versionCode')) is int and
            manifest['versionCode'] == sdk.get('versionCode'), 'CLAIM_RELEASE_VERSION_MISMATCH')


def verify_claim_assets(archive, names, execution, receipt):
    prefix = 'assets/ultra-manager-claim/'
    actual_names = {name[len(prefix):] for name in names
                    if name.startswith(prefix) and not name.endswith('/')}
    expected_manifest = execution.get('claimManifest')
    if not actual_names and expected_manifest is None:
        return
    require(isinstance(expected_manifest, dict), 'CLAIM_MANIFEST_EXECUTION_BINDING_MISSING')
    manifest_path = Path(expected_manifest['path'])
    source = matches(manifest_path, expected_manifest)
    data = archive.read(prefix + 'manifest.json')
    require(len(data) == source['size'] and digest(data) == source['sha256'], 'CLAIM_MANIFEST_APK_DRIFT')
    manifest = json.loads(data.decode('utf-8-sig'))
    require(manifest.get('schemaVersion') in (1, 2), 'CLAIM_MANIFEST_SCHEMA')
    verify_claim_release_version(manifest, load(Path(execution['sdkRelease']['path'])))
    payloads = manifest.get('payloads')
    verify_manifest_routes(manifest, lambda name: archive.read('assets/ultra-init-bridge/' + name))
    require(isinstance(payloads, dict) and
            all(re.fullmatch(r'android[0-9]+-[0-9]+\.[0-9]+', family) for family in payloads),
            'CLAIM_FAMILY_CLOSURE')
    support = manifest.get('support')
    require(isinstance(support, dict) and set(support) == {'bootstrap', 'materializer', 'permissions'},
            'CLAIM_SUPPORT_CLOSURE')
    expected_names = {'manifest.json'}
    bindings = []

    def check_file(relative, row):
        require(isinstance(relative, str) and re.fullmatch(r'[A-Za-z0-9._/-]+', relative) and
                all(part not in ('', '.', '..') for part in relative.split('/')) and
                relative not in expected_names, 'CLAIM_ASSET_PATH')
        require(isinstance(row, dict) and type(row.get('bytes')) is int and row['bytes'] > 0 and
                isinstance(row.get('sha256'), str) and re.fullmatch(r'[0-9a-f]{64}', row['sha256']),
                'CLAIM_ASSET_IDENTITY: ' + relative)
        body = archive.read(prefix + relative)
        require(len(body) == row['bytes'] and digest(body) == row['sha256'], 'CLAIM_ASSET_DRIFT: ' + relative)
        expected_names.add(relative)
        bindings.append(dict(path=relative, bytes=len(body), sha256=row['sha256']))
        return body

    for family, row in payloads.items():
        check_file(family + '/claim.so', row)
        if manifest['schemaVersion'] == 2:
            require(row.get('metadataEncoding') == 'gzip-chunks-v1', 'CLAIM_METADATA_ENCODING: ' + family)
            chunks = row.get('chunks')
            require(isinstance(chunks, list) and 1 <= len(chunks) <= 8, 'CLAIM_METADATA_CHUNKS: ' + family)
            aggregate = hashlib.sha256()
            size = 0
            for chunk in chunks:
                require(isinstance(chunk, dict) and isinstance(chunk.get('path'), str) and
                        chunk['path'].startswith(family + '/') and
                        type(chunk.get('bytes')) is int and 0 < chunk['bytes'] <= 524288,
                        'CLAIM_METADATA_CHUNK: ' + family)
                body = check_file(chunk['path'], chunk)
                aggregate.update(body)
                size += len(body)
                require(size <= 4194304, 'CLAIM_METADATA_SIZE: ' + family)
            require(size == row.get('gzipBytes') and aggregate.hexdigest() == row.get('gzipSha256'),
                    'CLAIM_METADATA_AGGREGATE: ' + family)
        if 'support' in row:
            overrides = row['support']
            require(isinstance(overrides, dict) and set(overrides) == {'bootstrap'},
                    'CLAIM_SUPPORT_OVERRIDE: ' + family)
            bootstrap = overrides['bootstrap']
            allowed = {'bytes', 'sha256', 'entryOffset', 'imports', 'relocations', 'sourceInputsIdentity',
                       'retirement', 'bootEntryOffset', 'deploymentBootEntryOffset', 'deploymentAnchorVersion',
                       'deploymentBindingBytes', 'deploymentConfigBytes', 'deploymentRecordBytes', 'path',
                       'nativeCfiPolicy', 'nativeCommandIdentity'}
            require(isinstance(bootstrap, dict) and set(bootstrap) <= allowed and
                    bootstrap.get('path') == family + '/bootstrap.bin', 'CLAIM_BOOTSTRAP_PATH: ' + family)
            if 'nativeCfiPolicy' in bootstrap or 'nativeCommandIdentity' in bootstrap:
                cfi, command = bootstrap.get('nativeCfiPolicy'), bootstrap.get('nativeCommandIdentity')
                retirement = bootstrap.get('retirement')
                require(isinstance(cfi, dict) and set(cfi) ==
                        {'kcfiOptionPresent', 'kcfiEnabled', 'normalizeIntegers'} and
                        all(type(value) is bool for value in cfi.values()) and
                        cfi['kcfiOptionPresent'] is True and cfi['kcfiEnabled'] is True and
                        isinstance(retirement, dict) and
                        cfi['normalizeIntegers'] == retirement.get('normalizeIntegers'),
                        'CLAIM_BOOTSTRAP_CFI: ' + family)
                require(isinstance(command, dict) and set(command) == {'path', 'bytes', 'sha256'} and
                        isinstance(command['path'], str) and bool(command['path'].strip()) and
                        type(command['bytes']) is int and command['bytes'] > 0 and
                        isinstance(command['sha256'], str) and re.fullmatch(r'[0-9a-f]{64}', command['sha256']),
                        'CLAIM_BOOTSTRAP_COMMAND: ' + family)
            entry = bootstrap.get('entryOffset')
            require(type(entry) is int and entry == 0 and type(bootstrap.get('bytes')) is int and
                    bootstrap['bytes'] > 0 and bootstrap.get('imports') == [] and
                    type(bootstrap.get('relocations')) is int and bootstrap['relocations'] == 0,
                    'CLAIM_BOOTSTRAP_ENTRY: ' + family)
            check_file(family + '/bootstrap.bin', bootstrap)
    for name, row in support.items():
        check_file(name + '.bin', row)
    require(actual_names == expected_names, 'CLAIM_ASSET_SET_MISMATCH')
    require(record(manifest_path) == source, 'CLAIM_MANIFEST_INPUT_CHANGED')
    receipt['claimManifest'] = source
    receipt['claimAssetBindings'] = bindings


def verify_bridge_input_binding(item, declared, bundle, bundled):
    name = item['name']
    actual = matches(Path(item['path']), item)
    require(isinstance(declared, dict) and {'bytes', 'sha256', 'bundledAs'} <= set(declared) and
            set(declared) <= {'path', 'bytes', 'sha256', 'bundledBytes', 'bundledSha256', 'bundledAs'},
            'INIT_BOOT_BRIDGE_INPUT_DESCRIPTOR: ' + name)
    require(declared.get('sha256') == actual['sha256'] and declared.get('bytes') == actual['size'],
            'INIT_BOOT_BRIDGE_INPUT_DRIFT: ' + name)
    fields = {'bundledBytes', 'bundledSha256'} & set(declared)
    require(not fields or fields == {'bundledBytes', 'bundledSha256'},
            'INIT_BOOT_BRIDGE_BUNDLED_IDENTITY_INCOMPLETE: ' + name)
    expected = actual
    if fields:
        require(type(declared['bundledBytes']) is int and declared['bundledBytes'] > 0 and
                isinstance(declared['bundledSha256'], str) and
                re.fullmatch(r'[0-9a-f]{64}', declared['bundledSha256']),
                'INIT_BOOT_BRIDGE_BUNDLED_IDENTITY_INVALID: ' + name)
        expected = dict(size=declared['bundledBytes'], sha256=declared['bundledSha256'])
    changed = expected['size'] != actual['size'] or expected['sha256'] != actual['sha256']
    require(not changed or name in {'bridge', 'session-abi', 'task-profile-abi'},
            'INIT_BOOT_BRIDGE_UNKNOWN_TRANSFORMATION: ' + name)
    binding = dict(name=name, file=actual)
    relative = declared.get('bundledAs')
    if relative is None:
        require(not changed, 'INIT_BOOT_BRIDGE_UNPUBLISHED_TRANSFORMATION: ' + name)
        return binding
    require(isinstance(relative, str) and re.fullmatch(r'[A-Za-z0-9._/-]+', relative) and
            all(part not in ('', '.', '..') for part in relative.split('/')),
            'INIT_BOOT_BRIDGE_INPUT_PATH')
    target = (bundle / relative).resolve()
    require(target.is_relative_to(bundle.resolve()), 'INIT_BOOT_BRIDGE_INPUT_PATH')
    packaged = matches(target, expected)
    binding['bundledFile'] = packaged
    if changed and name == 'bridge':
        require(relative == 'bridge.ko', 'INIT_BOOT_BRIDGE_TRANSFORMATION_PATH')
        proof, proof_record = load_bound_json(bundle / 'MODULE-INPUT-CHECK.json',
                                            'INIT_BOOT_BRIDGE_TRANSFORMATION_PROOF_CHANGED')
        layout = proof.get('runtimeLayoutBinding', {})
        require(layout.get('present') is True and layout.get('bound') is True and
                layout.get('sourceModuleSha256') == actual['sha256'] and
                layout.get('derivedModuleSha256') == packaged['sha256'] and
                layout.get('targetBtfSha256') == bundled.get('btf', {}).get('sha256') and
                packaged['size'] == actual['size'], 'INIT_BOOT_BRIDGE_TRANSFORMATION_PROOF')
        binding.update(transformation='target-btf-runtime-layout', transformationProof=proof_record)
    elif changed and name == 'session-abi':
        require(relative == 'session-abi.json', 'INIT_BOOT_BRIDGE_TRANSFORMATION_PATH')
        source, source_record = load_bound_json(Path(item['path']), 'INIT_BOOT_BRIDGE_SESSION_SOURCE_CHANGED')
        session, session_record = load_bound_json(target, 'INIT_BOOT_BRIDGE_SESSION_BUNDLED_CHANGED')
        require(source_record == actual and session_record == packaged and isinstance(source, dict),
                'INIT_BOOT_BRIDGE_SESSION_IDENTITY_CHANGED')
        bridge = bundled.get('bridge', {})
        module_sha = bridge.get('bundledSha256', bridge.get('sha256'))
        require(isinstance(module_sha, str) and re.fullmatch(r'[0-9a-f]{64}', module_sha),
                'INIT_BOOT_BRIDGE_SESSION_MODULE_IDENTITY')
        expected_session = dict(source, bundledModuleSha256=module_sha)
        encoded = (json.dumps(expected_session, indent=2) + '\n').encode('utf-8')
        require(session == expected_session and len(encoded) == packaged['size'] and
                digest(encoded) == packaged['sha256'], 'INIT_BOOT_BRIDGE_UNKNOWN_SESSION_TRANSFORMATION')
        binding['transformation'] = 'session-bundled-module-sha256'
    elif name == 'task-profile-abi':
        require(relative == 'task-profile-abi.json', 'INIT_BOOT_BRIDGE_TRANSFORMATION_PATH')
        source, source_record = load_bound_json(Path(item['path']), 'INIT_BOOT_BRIDGE_TASK_PROFILE_SOURCE_CHANGED')
        profile, profile_record = load_bound_json(target, 'INIT_BOOT_BRIDGE_TASK_PROFILE_BUNDLED_CHANGED')
        require(source_record == actual and profile_record == packaged and isinstance(source, dict) and
                source.get('schema') == 'maplesu-init-bridge-task-profile-abi/1' and
                source.get('originalModuleWritten') is False and source.get('targetQualified') is False and
                source.get('deviceQualified') is False, 'INIT_BOOT_BRIDGE_TASK_PROFILE_IDENTITY_CHANGED')
        bridge = bundled.get('bridge', {})
        module_sha = bridge.get('bundledSha256', bridge.get('sha256'))
        module_bytes = bridge.get('bundledBytes', bridge.get('bytes'))
        require(isinstance(module_sha, str) and re.fullmatch(r'[0-9a-f]{64}', module_sha) and
                type(module_bytes) is int and module_bytes > 0, 'INIT_BOOT_BRIDGE_TASK_PROFILE_MODULE_IDENTITY')
        expected_profile = dict(source, bundledModuleSha256=module_sha, bundledModuleBytes=module_bytes)
        encoded = (json.dumps(expected_profile, indent=2) + '\n').encode('utf-8')
        require(profile == expected_profile and len(encoded) == packaged['size'] and
                digest(encoded) == packaged['sha256'], 'INIT_BOOT_BRIDGE_UNKNOWN_TASK_PROFILE_TRANSFORMATION')
        binding['transformation'] = 'task-profile-bundled-module-identity'
    return binding


def verify(args, receipt):
    root, base, sdk, apk = [path.resolve() for path in
                            (args.root, args.manager_build, args.sdk, args.apk)]
    execution, execution_record = load_bound_json(args.execution, 'RELEASE_EXECUTION_INPUT_CHANGED')
    require(execution.get('phase') == 'release' and execution.get('status') == 'FINISHED' and
            type(execution.get('exitCode')) is int and execution['exitCode'] == 0,
            'RELEASE_BUILD_NOT_SUCCESSFUL')
    receipt['execution'] = execution_record
    sdk_release_path = sdk / 'SDK-RELEASE.json'
    receipt['sdkRelease'] = matches(sdk_release_path, execution['sdkRelease'])
    matches(Path(execution['bridgeBindings']['path']), execution['bridgeBindings'])
    release, release_record = load_bound_json(sdk_release_path, 'SDK_RELEASE_INPUT_CHANGED')
    require(release_record == receipt['sdkRelease'], 'SDK_RELEASE_INPUT_CHANGED')
    pin_path = root / 'third_party/kernelsu/ksu-payload.json'
    pin_document, pin_record = load_bound_json(pin_path, 'KSU_IDENTITY_MANIFEST_INPUT_CHANGED')
    pin = pin_document['identity']
    receipt['ksuIdentityManifest'] = pin_record
    apk_identity = file_identity(apk.stat())
    receipt['apk'] = record(apk)

    # Sample around consumers; external tools do not hold a shared snapshot lock.
    def unchanged_apk(stage):
        require(file_identity(apk.stat()) == apk_identity, 'APK_INPUT_CHANGED: ' + stage)
        actual = record(apk)
        require(file_identity(apk.stat()) == apk_identity and
                actual['sha256'] == receipt['apk']['sha256'] and
                actual['size'] == receipt['apk']['size'], 'APK_INPUT_CHANGED: ' + stage)

    built_apks = sorted((base / 'outputs/apk/release').glob('*.apk'))
    require(len(built_apks) == 1, 'MANAGER_RELEASE_APK_SET')
    matches(apk, record(built_apks[0]))
    commands = receipt['commands'] = []
    tool_suffix = '.exe' if os.name == 'nt' else ''
    signer_suffix = '.bat' if os.name == 'nt' else ''
    for name, argv in [
        ('badging', [str(args.build_tools / ('aapt2' + tool_suffix)), 'dump', 'badging', str(apk)]),
        ('signature', [str(args.build_tools / ('apksigner' + signer_suffix)), 'verify', '--verbose', '--print-certs', str(apk)])]:
        unchanged_apk(name + '.before')
        completed = subprocess.run(argv, capture_output=True)
        output = completed.stdout + completed.stderr
        log = args.output / (name + '.log')
        log.write_bytes(output)
        commands.append(dict(name=name, argv=argv, exitCode=completed.returncode, log=record(log)))
        require(completed.returncode == 0, 'APK_TOOL_FAILED: ' + name)
        unchanged_apk(name + '.after')
        text = output.decode('utf-8', 'replace')
        if name == 'badging':
            lines = [line for line in text.splitlines() if line.startswith('package:')]
            require(len(lines) == 1, 'APK_PACKAGE_IDENTITY_MISSING')
            values = dict(re.findall(r"\b(name|versionCode|versionName)='([^']*)'", lines[0]))
            require(values == dict(name=pin['packageName'], versionName=release['appVersion'],
                                   versionCode=str(release['versionCode'])), 'APK_PACKAGE_IDENTITY_MISMATCH')
            receipt['package'] = values
        else:
            receipt['certificateSha256'] = certificate(text, pin['expectedHash'])

    receipt['sdkArchive'] = matches(root / release['archive']['path'], release['archive'])
    helper = root / release['helper']['path']
    receipt['helper'] = matches(helper, release['helper'])
    backend_input = release['environmentPolicyBoot']['backend']
    matches(root / backend_input['path'], backend_input)
    native_root = base / 'intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib'
    native = {'lib/' + path.relative_to(native_root).as_posix(): path
              for path in sorted(native_root.rglob('*.so')) if path.is_file()}
    require(bool(native), 'PACKAGED_NATIVE_INPUTS_MISSING')
    bundle = base / 'generated/ultraInitBridgeAssets/ultra-init-bridge'
    bundle_sources_path = bundle / 'BUNDLE-SOURCES.json'
    require((bundle / 'manifest.json').is_file() and bundle_sources_path.is_file(),
            'INIT_BOOT_BRIDGE_MANIFEST_MISSING')
    assets = {'assets/ultra-init-bridge/' + path.relative_to(bundle).as_posix(): path
              for path in sorted(bundle.rglob('*')) if path.is_file()}
    unchanged_apk('zip.before')
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), 'DUPLICATE_ZIP_NAMES')
        verify_claim_assets(archive, names, execution, receipt)
        require({name for name in names if name.startswith('lib/') and name.endswith('.so')} == set(native),
                'PACKAGED_NATIVE_SET_MISMATCH')
        require({name for name in names if name.startswith('assets/ultra-init-bridge/') and not name.endswith('/')} == set(assets),
                'INIT_BOOT_BRIDGE_ASSET_SET_MISMATCH')
        receipt['nativeBindings'] = []
        for entry, source in native.items():
            actual = record(source)
            data = archive.read(entry)
            require(len(data) == actual['size'] and digest(data) == actual['sha256'], 'NATIVE_ZIP_DRIFT: ' + entry)
            receipt['nativeBindings'].append(dict(entry=entry, source=actual))
        backend = archive.read('lib/arm64-v8a/libmaplesu_policy_boot.so')
        require(digest(backend) == backend_input['sha256'], 'HELPER_BACKEND_PAIR_DRIFT')
        bridge = archive.read('lib/arm64-v8a/libmaplesu_ultra_bridge.so')
        for value in (release['sdkBuildId'], release['archive']['sha256'],
                      release['helper']['normalizedSha256'], release['helper']['buildId']):
            require(value.encode() + b'\0' in bridge, 'PACKAGED_SDK_IDENTITY_MISSING: ' + value)
        payloads = release['sourceBuild']['payloads']
        require(len(payloads) == 4 and {row['name'] for row in payloads} == PAYLOADS, 'SDK_PAYLOAD_SET_MISMATCH')
        receipt['embeddedPayloads'] = []
        for payload in payloads:
            source = (root / payload['file']['path']).resolve()
            require(source.is_relative_to(sdk), 'STAGED_PAYLOAD_OUTSIDE_SDK')
            actual = matches(source, payload['file'])
            data = source.read_bytes()
            require(bool(data) and data in bridge, 'PACKAGED_PAYLOAD_MISSING: ' + payload['name'])
            if payload['name'] == 'autorun_bootstrap':
                require(data == helper.read_bytes(), 'SOURCE_PAYLOAD_HELPER_MISMATCH')
            receipt['embeddedPayloads'].append(dict(name=payload['name'], file=actual))
        provider = base / 'generated/jailbreakAssets/maplesu-jailbreak-artifacts.json'
        require(archive.read('assets/' + provider.name) == provider.read_bytes(), 'PROVIDER_MANIFEST_DRIFT')
        receipt['providerManifest'] = record(provider)
        generation = pin['managerGenAsset']
        require(generation.startswith('assets/'), 'KSU_MANAGER_GENERATION_PATH')
        generation_source = root / 'manager/src/main/assets' / generation[len('assets/'):]
        require(archive.read(generation) == generation_source.read_bytes(), 'KSU_MANAGER_GENERATION_DRIFT')
        receipt['generationAsset'] = record(generation_source)
        receipt['initBootBridgeAssets'] = []
        for entry, source in assets.items():
            data = archive.read(entry)
            require(data == source.read_bytes(), 'INIT_BOOT_BRIDGE_ASSET_DRIFT: ' + entry)
            actual = record(source)
            if source == bundle_sources_path:
                require(actual['size'] == len(data) and actual['sha256'] == digest(data),
                        'INIT_BOOT_BRIDGE_SOURCES_INPUT_CHANGED')
                bundle_sources_record = actual
            receipt['initBootBridgeAssets'].append(dict(entry=entry, source=actual))

    unchanged_apk('zip.after')

    inputs = execution['bridgeInputs']
    input_names = {row['name'] for row in inputs}
    expected_inputs = BRIDGE_INPUTS | ({'session-abi', 'task-profile-abi'} & input_names)
    require(len(inputs) == len(expected_inputs) and input_names == expected_inputs,
            'INIT_BOOT_BRIDGE_INPUT_SET_MISMATCH')
    bundled_document, bundled_record = load_bound_json(bundle_sources_path, 'INIT_BOOT_BRIDGE_SOURCES_INPUT_CHANGED')
    require(bundled_record == bundle_sources_record, 'INIT_BOOT_BRIDGE_SOURCES_INPUT_CHANGED')
    bundled = bundled_document['inputs']
    require(set(bundled) == {name.replace('-', '_') for name in expected_inputs},
            'INIT_BOOT_BRIDGE_SOURCE_SET_MISMATCH')
    if 'session-abi' in input_names:
        require(bundled['session_abi'].get('bundledAs') == 'session-abi.json',
                'INIT_BOOT_BRIDGE_SESSION_ABI_MISSING')
        require((bundle / 'session-abi.json').is_file(), 'INIT_BOOT_BRIDGE_SESSION_ABI_MISSING')
    else:
        require(not (bundle / 'session-abi.json').exists(), 'INIT_BOOT_BRIDGE_SESSION_ABI_UNBOUND')
    if 'task-profile-abi' in input_names:
        require(bundled['task_profile_abi'].get('bundledAs') == 'task-profile-abi.json',
                'INIT_BOOT_BRIDGE_TASK_PROFILE_ABI_MISSING')
        require((bundle / 'task-profile-abi.json').is_file(), 'INIT_BOOT_BRIDGE_TASK_PROFILE_ABI_MISSING')
    else:
        require(not (bundle / 'task-profile-abi.json').exists(), 'INIT_BOOT_BRIDGE_TASK_PROFILE_ABI_UNBOUND')
    receipt['bridgeInputBindings'] = []
    for item in inputs:
        declared = bundled[item['name'].replace('-', '_')]
        binding = verify_bridge_input_binding(item, declared, bundle, bundled)
        if 'transformationProof' in binding:
            require(any(row['source'] == binding['transformationProof']
                        for row in receipt['initBootBridgeAssets']),
                    'INIT_BOOT_BRIDGE_TRANSFORMATION_PROOF_CHANGED')
        receipt['bridgeInputBindings'].append(binding)
    class_file = base / 'intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes/com/maplesu/core/ui/webui/ModuleWebUIActivity.class'
    require(b'$$$$$NON_LOCAL_RETURN$$$$$' not in class_file.read_bytes(), 'NON_LOCAL_RETURN_MARKER_REMAINS')
    receipt['composeClass'] = record(class_file)
    receipt['nonLocalReturnMarkerAbsent'] = True
    require(record(args.execution) == execution_record, 'RELEASE_EXECUTION_INPUT_CHANGED')
    require(record(sdk_release_path) == release_record, 'SDK_RELEASE_INPUT_CHANGED')
    require(record(pin_path) == pin_record, 'KSU_IDENTITY_MANIFEST_INPUT_CHANGED')
    require(record(bundle_sources_path) == bundled_record, 'INIT_BOOT_BRIDGE_SOURCES_INPUT_CHANGED')
    unchanged_apk('complete')
    receipt['passed'] = True


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    for name in ('apk', 'execution', 'sdk', 'manager-build', 'build-tools', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args(argv)
    args.output.mkdir(parents=True, exist_ok=False)
    receipt = dict(schema='maplesu-release-apk-verification/1',
        startedUtc=datetime.now(timezone.utc).isoformat(), verifier=record(Path(__file__)), passed=False,
        scope='Release signature, packaged bytes and recorded SDK/bridge pairing; source behavior and device acceptance remain separate.',
        formalAcceptance=False, behaviorAcceptance=False, deviceAcceptance=False, deviceOperations=0)
    try:
        verify(args, receipt)
    except Exception as exc:
        receipt['failure'] = type(exc).__name__ + ': ' + str(exc)
    receipt['finishedUtc'] = datetime.now(timezone.utc).isoformat()
    (args.output / 'APK-VERIFICATION.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: receipt[key] for key in ('passed', 'failure', 'apk') if key in receipt}))
    return 0 if receipt['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
