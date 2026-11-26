"""Exercise release byte/identity checks with ordinary ZIP files and mocked Android tools."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import warnings
import zipfile

SOURCE = Path(__file__).resolve().parents[1] / 'verify-release-apk.py'
SPEC = importlib.util.spec_from_file_location('release_apk_verifier', SOURCE)
M = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(M)
PIN = 'a' * 64


class ReleaseApkTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='maplesu-release-apk-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.base = self.root / 'manager-build'
        self.sdk = self.root / 'sdk'
        self.apk = self.base / 'outputs/apk/release/manager-release.apk'
        self.native = self.base / 'intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/arm64-v8a'
        self.bundle = self.base / 'generated/ultraInitBridgeAssets/ultra-init-bridge'
        self.write('third_party/kernelsu/ksu-payload.json', json.dumps(dict(identity=dict(
            packageName='dev.maplesu.manager', managerGenAsset='assets/ksu_manager_gen2', expectedHash=PIN))).encode())
        self.write('manager/src/main/assets/ksu_manager_gen2', b'2')
        archive = self.write('sdk/lib/libkernel_module_kit_static.a', b'ordinary archive fixture')
        helper = self.write('sdk/helper/autorun_bootstrap', b'ordinary source helper fixture')
        backend = self.write('sdk/backend/libmaplesu_policy_boot.so', b'ordinary backend fixture')
        payloads = []
        self.payload_bytes = {}
        for name in sorted(M.PAYLOADS):
            body = helper.read_bytes() if name == 'autorun_bootstrap' else ('ordinary payload: ' + name).encode()
            path = self.write('sdk/assets/' + name + '.bin', body)
            self.payload_bytes[name] = body
            payloads.append(dict(name=name, file=self.row(path)))
        self.release = dict(appVersion='5.2.1', versionCode=50201, sdkBuildId='ordinary-sdk-generation',
            archive=self.row(archive), helper=dict(self.row(helper), normalizedSha256='b' * 64, buildId='ordinary-helper-generation'),
            environmentPolicyBoot=dict(backend=self.row(backend)), sourceBuild=dict(payloads=payloads))
        bridge_bytes = b'\0'.join(value.encode() for value in (self.release['sdkBuildId'],
            self.release['archive']['sha256'], self.release['helper']['normalizedSha256'],
            self.release['helper']['buildId'])) + b'\0'
        bridge_bytes += b'\n'.join(self.payload_bytes.values())
        self.save(self.native / 'libmaplesu_ultra_bridge.so', bridge_bytes)
        self.save(self.native / 'libmaplesu_policy_boot.so', backend.read_bytes())
        self.save(self.base / 'generated/jailbreakAssets/maplesu-jailbreak-artifacts.json', b'{"ordinaryFixture":true}')
        self.save(self.bundle / 'manifest.json', b'{"schemaVersion":1}')
        inputs = []
        bundled = {}
        for name in sorted(M.BRIDGE_INPUTS):
            path = self.write('bridge-inputs/' + name, ('ordinary bridge input: ' + name).encode())
            row = self.row(path)
            inputs.append(dict(name=name, path=path.as_posix(), bytes=row['size'], sha256=row['sha256']))
            target = None if name in ('kernel-image', 'handoff-verifier') else name + '.fixture'
            bundled[name.replace('-', '_')] = dict(path=path.as_posix(), bytes=row['size'], sha256=row['sha256'], bundledAs=target)
            if target is not None:
                self.save(self.bundle / target, path.read_bytes())
        self.save(self.bundle / 'BUNDLE-SOURCES.json', json.dumps(dict(inputs=bundled)).encode())
        bindings = self.write('bridge-bindings.json', b'{"ordinaryFixture":true}')
        self.execution = dict(phase='release', status='FINISHED', exitCode=0,
            bridgeBindings=M.record(bindings), bridgeInputs=inputs)
        self.execution_path = self.root / 'execution.json'
        self.refresh_release()
        self.save(self.base / 'intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes/com/maplesu/core/ui/webui/ModuleWebUIActivity.class',
                  b'ordinary class fixture')
        self.entries = {
            'AndroidManifest.xml': b'ordinary manifest fixture',
            'assets/ksu_manager_gen2': b'2',
            'assets/maplesu-jailbreak-artifacts.json': b'{"ordinaryFixture":true}',
        }
        for path in sorted(self.native.glob('*.so')):
            self.entries['lib/arm64-v8a/' + path.name] = path.read_bytes()
        for path in sorted(self.bundle.rglob('*')):
            if path.is_file():
                self.entries['assets/ultra-init-bridge/' + path.relative_to(self.bundle).as_posix()] = path.read_bytes()
        self.repack()
        self.index = 0
        self.tools = patch.object(M.subprocess, 'run', side_effect=self.android_tool).start()
        self.addCleanup(patch.stopall)

    @staticmethod
    def save(path, data):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return path

    def write(self, name, data):
        return self.save(self.root / name, data)

    def row(self, path):
        return dict(M.record(path), path=path.relative_to(self.root).as_posix())

    def refresh_release(self):
        path = self.save(self.sdk / 'SDK-RELEASE.json', json.dumps(self.release).encode())
        self.execution['sdkRelease'] = M.record(path)
        self.save(self.execution_path, json.dumps(self.execution).encode())

    def repack(self):
        self.apk.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(self.apk, 'w') as package:
            for name, data in self.entries.items():
                package.writestr(name, data)

    @staticmethod
    def android_tool(argv, **kwargs):
        if 'badging' in argv:
            output = b"package: name='dev.maplesu.manager' versionCode='50201' versionName='5.2.1'\n"
        else:
            output = ('Verifies\nNumber of signers: 1\nV2 Signer: certificate SHA-256 digest: ' + PIN + '\n').encode()
        return subprocess.CompletedProcess(argv, 0, output, b'')

    def execute(self, apk=None):
        self.index += 1
        output = self.root / ('verification-' + str(self.index))
        argv = ['--root', str(self.root), '--apk', str(apk or self.apk), '--execution', str(self.execution_path),
                '--sdk', str(self.sdk), '--manager-build', str(self.base), '--build-tools', str(self.root / 'mock-tools'),
                '--output', str(output)]
        with contextlib.redirect_stdout(io.StringIO()):
            code = M.main(argv)
        receipt = M.load(output / 'APK-VERIFICATION.json')
        self.assertFalse(receipt['formalAcceptance'])
        self.assertFalse(receipt['behaviorAcceptance'])
        self.assertFalse(receipt['deviceAcceptance'])
        self.assertEqual(receipt['deviceOperations'], 0)
        return code, receipt

    def rejected(self, message):
        code, receipt = self.execute()
        self.assertEqual(code, 1)
        self.assertFalse(receipt['passed'])
        self.assertIn(message, receipt['failure'])
        return receipt

    def add_session_abi(self):
        path = self.write('bridge-inputs/session-abi', b'{"ordinarySessionAbiFixture":true}')
        row = M.record(path)
        self.execution['bridgeInputs'].append(dict(name='session-abi', path=path.as_posix(),
            bytes=row['size'], sha256=row['sha256']))
        self.save(self.execution_path, json.dumps(self.execution).encode())
        sources_path = self.bundle / 'BUNDLE-SOURCES.json'
        sources = M.load(sources_path)
        sources['inputs']['session_abi'] = dict(path=path.as_posix(), bytes=row['size'],
            sha256=row['sha256'], bundledAs='session-abi.json')
        self.save(sources_path, json.dumps(sources).encode())
        self.save(self.bundle / 'session-abi.json', path.read_bytes())
        self.entries['assets/ultra-init-bridge/BUNDLE-SOURCES.json'] = sources_path.read_bytes()
        self.entries['assets/ultra-init-bridge/session-abi.json'] = path.read_bytes()
        self.repack()
        return path

    def test_claim_release_version_matches_the_actual_sdk(self):
        M.verify_claim_release_version(dict(appVersion='5.2.1', versionCode=50201), self.release)
        for manifest in ({}, dict(appVersion='5.2.0', versionCode=50200),
                         dict(appVersion='5.2.1', versionCode=50200),
                         dict(appVersion='5.2.1', versionCode='50201')):
            with self.subTest(manifest=manifest), self.assertRaisesRegex(ValueError, 'CLAIM_RELEASE_VERSION_MISMATCH'):
                M.verify_claim_release_version(manifest, self.release)

    def test_optional_session_abi_and_all_eleven_inputs_pass(self):
        path = self.add_session_abi()
        code, receipt = self.execute()
        self.assertEqual(code, 0)
        self.assertTrue(receipt['passed'])
        self.assertEqual(len(receipt['bridgeInputBindings']), 11)
        bound = next(row for row in receipt['bridgeInputBindings'] if row['name'] == 'session-abi')
        self.assertEqual(bound['file'], M.record(path))

    def test_session_abi_missing_source_record_is_rejected(self):
        self.add_session_abi()
        path = self.bundle / 'BUNDLE-SOURCES.json'
        sources = M.load(path)
        del sources['inputs']['session_abi']
        self.save(path, json.dumps(sources).encode())
        self.entries['assets/ultra-init-bridge/BUNDLE-SOURCES.json'] = path.read_bytes()
        self.repack()
        self.rejected('INIT_BOOT_BRIDGE_SOURCE_SET_MISMATCH')

    def test_session_abi_missing_bundled_file_is_rejected(self):
        self.add_session_abi()
        (self.bundle / 'session-abi.json').unlink()
        del self.entries['assets/ultra-init-bridge/session-abi.json']
        self.repack()
        self.rejected('INIT_BOOT_BRIDGE_SESSION_ABI_MISSING')

    def test_session_abi_omitted_bundle_binding_is_rejected(self):
        self.add_session_abi()
        path = self.bundle / 'BUNDLE-SOURCES.json'
        sources = M.load(path)
        sources['inputs']['session_abi']['bundledAs'] = None
        self.save(path, json.dumps(sources).encode())
        self.entries['assets/ultra-init-bridge/BUNDLE-SOURCES.json'] = path.read_bytes()
        self.repack()
        self.rejected('INIT_BOOT_BRIDGE_SESSION_ABI_MISSING')

    def test_session_abi_source_drift_is_rejected(self):
        path = self.add_session_abi()
        self.save(path, b'changed session ABI source')
        self.rejected('SOURCE_IDENTITY_MISMATCH')

    def test_session_abi_bundled_drift_is_rejected(self):
        self.add_session_abi()
        path = self.bundle / 'session-abi.json'
        self.save(path, b'changed session ABI bundle')
        self.entries['assets/ultra-init-bridge/session-abi.json'] = path.read_bytes()
        self.repack()
        self.rejected('SOURCE_IDENTITY_MISMATCH')

    def test_session_abi_apk_drift_is_rejected(self):
        self.add_session_abi()
        self.entries['assets/ultra-init-bridge/session-abi.json'] = b'changed session ABI APK'
        self.repack()
        self.rejected('INIT_BOOT_BRIDGE_ASSET_DRIFT')

    def test_session_abi_orphan_source_record_is_rejected(self):
        self.add_session_abi()
        self.execution['bridgeInputs'].pop()
        self.save(self.execution_path, json.dumps(self.execution).encode())
        self.rejected('INIT_BOOT_BRIDGE_SOURCE_SET_MISMATCH')

    def test_unknown_extra_input_with_session_abi_is_rejected(self):
        self.add_session_abi()
        item = dict(self.execution['bridgeInputs'][-1], name='unknown-extra')
        self.execution['bridgeInputs'].append(item)
        self.save(self.execution_path, json.dumps(self.execution).encode())
        self.rejected('INIT_BOOT_BRIDGE_INPUT_SET_MISMATCH')

    def test_complete_current_bytes_and_all_ten_inputs_pass(self):
        code, receipt = self.execute()
        self.assertEqual(code, 0)
        self.assertTrue(receipt['passed'])
        self.assertEqual(len(receipt['nativeBindings']), 2)
        self.assertEqual(len(receipt['bridgeInputBindings']), 10)
        self.assertEqual(len(receipt['embeddedPayloads']), 4)
        self.assertEqual(self.tools.call_count, 2)

    def test_both_real_apksigner_prefixes_and_windows_newlines(self):
        for prefix in ('Signer #1', 'V2 Signer:'):
            with self.subTest(prefix=prefix):
                text = 'Number of signers: 1\r\n' + prefix + ' certificate SHA-256 digest: ' + PIN.upper() + '\r\n'
                self.assertEqual(M.certificate(text, PIN), PIN)

    def test_wrong_missing_and_ambiguous_certificates_are_rejected(self):
        line = 'V2 Signer: certificate SHA-256 digest: ' + PIN + '\n'
        for output, expected in (('Number of signers: 2\n' + line, 'COUNT'),
                                 ('Number of signers: 1\n', 'AMBIGUOUS'),
                                 ('Number of signers: 1\n' + line * 2, 'AMBIGUOUS'),
                                 ('Number of signers: 1\n' + line.replace(PIN, 'c' * 64), 'CERTIFICATE_MISMATCH')):
            with self.subTest(expected=expected), self.assertRaisesRegex(ValueError, expected):
                M.certificate(output, PIN)

    def test_failed_build_stops_before_tools(self):
        self.execution['exitCode'] = 1
        self.save(self.execution_path, json.dumps(self.execution).encode())
        self.rejected('RELEASE_BUILD_NOT_SUCCESSFUL')
        self.tools.assert_not_called()

    def test_sdk_manifest_drift_stops_before_tools(self):
        self.save(self.sdk / 'SDK-RELEASE.json', b'{}')
        self.rejected('SOURCE_IDENTITY_MISMATCH')
        self.tools.assert_not_called()

    def test_delivered_apk_must_match_release_output(self):
        other = self.write('delivered.apk', b'other generation')
        code, receipt = self.execute(other)
        self.assertEqual(code, 1)
        self.assertIn('SOURCE_IDENTITY_MISMATCH', receipt['failure'])
        self.tools.assert_not_called()

    def test_unrecorded_native_library_is_rejected(self):
        self.entries['lib/arm64-v8a/unrecorded.so'] = b'ordinary extra library'
        self.repack()
        self.rejected('PACKAGED_NATIVE_SET_MISMATCH')

    def test_packaged_native_byte_drift_is_rejected(self):
        self.entries['lib/arm64-v8a/libmaplesu_policy_boot.so'] += b'changed'
        self.repack()
        self.rejected('NATIVE_ZIP_DRIFT')

    def test_unrecorded_bridge_asset_is_rejected(self):
        self.entries['assets/ultra-init-bridge/unrecorded'] = b'ordinary extra bridge asset'
        self.repack()
        self.rejected('INIT_BOOT_BRIDGE_ASSET_SET_MISMATCH')

    def test_duplicate_zip_name_is_rejected(self):
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(self.apk, 'a') as package:
                package.writestr('AndroidManifest.xml', b'duplicate')
        self.rejected('DUPLICATE_ZIP_NAMES')

    def test_packaged_payload_absence_is_rejected(self):
        path = self.native / 'libmaplesu_ultra_bridge.so'
        data = path.read_bytes().replace(self.payload_bytes['su'], b'missing su fixture')
        self.save(path, data)
        self.entries['lib/arm64-v8a/' + path.name] = data
        self.repack()
        self.rejected('PACKAGED_PAYLOAD_MISSING: su')

    def test_self_consistent_sdk_helper_from_another_generation_is_rejected(self):
        helper = self.save(self.sdk / 'helper/autorun_bootstrap', b'ordinary previous helper generation')
        self.release['helper'].update(self.row(helper))
        self.refresh_release()
        self.rejected('SOURCE_PAYLOAD_HELPER_MISMATCH')

    def test_successful_tool_with_wrong_version_is_rejected(self):
        def old_version(argv, **kwargs):
            completed = self.android_tool(argv, **kwargs)
            if 'badging' in argv:
                completed.stdout = completed.stdout.replace(b"versionName='5.2.1'", b"versionName='5.2.0'")
            return completed
        self.tools.side_effect = old_version
        self.rejected('APK_PACKAGE_IDENTITY_MISMATCH')
        self.assertEqual(self.tools.call_count, 1)

    def test_duplicate_input_cannot_cover_a_missing_binding(self):
        self.execution['bridgeInputs'][-1] = self.execution['bridgeInputs'][0]
        self.save(self.execution_path, json.dumps(self.execution).encode())
        self.rejected('INIT_BOOT_BRIDGE_INPUT_SET_MISMATCH')

    def test_changed_bridge_input_digest_is_rejected(self):
        path = self.bundle / 'BUNDLE-SOURCES.json'
        sources = M.load(path)
        sources['inputs']['bridge']['sha256'] = 'f' * 64
        self.save(path, json.dumps(sources).encode())
        self.entries['assets/ultra-init-bridge/BUNDLE-SOURCES.json'] = path.read_bytes()
        self.repack()
        self.rejected('INIT_BOOT_BRIDGE_INPUT_DRIFT: bridge')

    def test_bundled_input_bytes_must_match_recorded_source(self):
        path = self.bundle / 'bridge.fixture'
        self.save(path, b'ordinary previous bridge generation')
        self.entries['assets/ultra-init-bridge/bridge.fixture'] = path.read_bytes()
        self.repack()
        self.rejected('SOURCE_IDENTITY_MISMATCH')

    def test_tool_failure_keeps_logs_and_failed_receipt(self):
        self.tools.side_effect = lambda argv, **kwargs: subprocess.CompletedProcess(argv, 4, b'', b'ordinary tool failure')
        receipt = self.rejected('APK_TOOL_FAILED: badging')
        log = Path(receipt['commands'][0]['log']['path'])
        self.assertEqual(log.read_bytes(), b'ordinary tool failure')


    def test_record_accepts_stable_file_with_distinct_fd_and_path_ctime(self):
        body = b'ordinary stable file with distinct ctime semantics'
        target = self.write('record-input.bin', body)
        linked = target.stat()
        opened = SimpleNamespace(**{name: getattr(linked, name) for name in dir(linked)
                                    if name.startswith('st_')})
        opened.st_ctime_ns += 1_000_000_000
        with patch.object(M.os, 'name', 'nt'), \
                patch.object(M.os, 'fstat', return_value=opened) as fstat:
            result = M.record(target)
        self.assertEqual(fstat.call_count, 2)
        self.assertEqual(result['path'], target.as_posix())
        self.assertEqual(result['size'], len(body))
        self.assertEqual(result['sha256'], M.digest(body))
        self.tools.assert_not_called()

    def test_record_rejects_fd_ctime_change_with_stable_size_and_mtime(self):
        target = self.write('record-input.bin', b'ordinary stable file bytes')
        before = target.stat()
        after = SimpleNamespace(**{name: getattr(before, name) for name in dir(before)
                                   if name.startswith('st_')})
        after.st_ctime_ns += 1
        with patch.object(M.os, 'fstat', side_effect=[before, after]) as fstat:
            with self.assertRaisesRegex(ValueError, 'SOURCE_CHANGED_WHILE_READING'):
                M.record(target)
        self.assertEqual(fstat.call_count, 2)
        self.tools.assert_not_called()

    def test_record_rejects_metadata_change_during_digest(self):
        target = self.write('record-input.bin', b'ordinary input')
        original_digest = M.hashlib.file_digest

        def digest_then_append(stream, algorithm):
            result = original_digest(stream, algorithm)
            with target.open('ab') as changed:
                changed.write(b'x')
            return result

        with patch.object(M.hashlib, 'file_digest', side_effect=digest_then_append):
            with self.assertRaisesRegex(ValueError, 'SOURCE_CHANGED_WHILE_READING'):
                M.record(target)
        self.tools.assert_not_called()

    def test_record_rejects_path_identity_swap_after_digest(self):
        target = self.write('record-input.bin', b'ordinary original identity')
        replacement = self.write('record-replacement.bin', b'ordinary replacement identity')
        replacement_info = replacement.stat()
        original_digest, original_stat = M.hashlib.file_digest, Path.stat
        swapped = False

        def digest_then_swap_link(stream, algorithm):
            nonlocal swapped
            result = original_digest(stream, algorithm)
            swapped = True
            return result

        def linked_stat(path, *args, **kwargs):
            if swapped and path == target:
                return replacement_info
            return original_stat(path, *args, **kwargs)

        # Model the changed pathname identity without Windows open-file rename rules.
        with patch.object(M.hashlib, 'file_digest', side_effect=digest_then_swap_link), \
                patch.object(Path, 'stat', new=linked_stat):
            with self.assertRaisesRegex(ValueError, 'SOURCE_CHANGED_WHILE_READING'):
                M.record(target)
        self.assertTrue(swapped)
        self.tools.assert_not_called()

    def test_apk_drift_after_badging_or_signature_is_rejected(self):
        for phase in ('badging', 'signature'):
            with self.subTest(phase=phase):
                self.repack()
                self.tools.reset_mock()

                def drift_after_tool(argv, **kwargs):
                    result = self.android_tool(argv, **kwargs)
                    current = 'badging' if 'badging' in argv else 'signature'
                    if current == phase:
                        with self.apk.open('ab') as changed:
                            changed.write(b'x')
                    return result

                self.tools.side_effect = drift_after_tool
                receipt = self.rejected('APK_INPUT_CHANGED: ' + phase + '.after')
                self.assertEqual(self.tools.call_count, 1 if phase == 'badging' else 2)
                self.assertEqual(receipt['commands'][-1]['name'], phase)
                self.assertEqual(receipt['commands'][-1]['exitCode'], 0)

    def test_apk_drift_during_zip_consumption_is_rejected(self):
        original_read = zipfile.ZipFile.read
        changed = False

        def read_then_append(archive, name, pwd=None):
            nonlocal changed
            data = original_read(archive, name, pwd)
            if not changed:
                changed = True
                with self.apk.open('ab') as output:
                    output.write(b'x')
            return data

        with patch.object(M.zipfile.ZipFile, 'read', new=read_then_append):
            self.rejected('APK_INPUT_CHANGED: zip.after')
        self.assertTrue(changed)
        self.assertEqual(self.tools.call_count, 2)

    def test_apk_drift_before_final_success_is_rejected(self):
        original_record = M.record
        class_file = self.base / 'intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes/com/maplesu/core/ui/webui/ModuleWebUIActivity.class'
        changed = False

        def record_then_append(path):
            nonlocal changed
            result = original_record(path)
            if Path(path) == class_file:
                changed = True
                with self.apk.open('ab') as output:
                    output.write(b'x')
            return result

        with patch.object(M, 'record', side_effect=record_then_append):
            receipt = self.rejected('APK_INPUT_CHANGED: complete')
        self.assertTrue(changed)
        self.assertTrue(receipt['nonLocalReturnMarkerAbsent'])
        self.assertEqual(self.tools.call_count, 2)

    def test_apk_drift_during_final_manifest_checks_is_rejected(self):
        manifest = self.bundle / 'BUNDLE-SOURCES.json'
        original_record = M.record
        checked_manifest = 0

        def record_then_change(path):
            nonlocal checked_manifest
            result = original_record(path)
            if Path(path) == manifest:
                checked_manifest += 1
                if checked_manifest == 2:
                    with self.apk.open('ab') as output:
                        output.write(b'x')
            return result

        with patch.object(M, 'record', side_effect=record_then_change):
            self.rejected('APK_INPUT_CHANGED: complete')
        self.assertEqual(checked_manifest, 2)
        self.assertEqual(self.tools.call_count, 2)

    def test_failed_tool_remains_first_error_when_apk_also_drifts(self):
        for phase in ('badging', 'signature'):
            with self.subTest(phase=phase):
                self.repack()
                self.tools.reset_mock()

                def fail_and_drift(argv, **kwargs):
                    current = 'badging' if 'badging' in argv else 'signature'
                    if current != phase:
                        return self.android_tool(argv, **kwargs)
                    with self.apk.open('ab') as changed:
                        changed.write(b'x')
                    return subprocess.CompletedProcess(argv, 4, b'', b'ordinary tool failure with input drift')

                self.tools.side_effect = fail_and_drift
                receipt = self.rejected('APK_TOOL_FAILED: ' + phase)
                self.assertNotIn('APK_INPUT_CHANGED', receipt['failure'])
                command = receipt['commands'][-1]
                self.assertEqual(command['name'], phase)
                self.assertEqual(command['exitCode'], 4)
                self.assertEqual(Path(command['log']['path']).read_bytes(), b'ordinary tool failure with input drift')
                self.assertEqual(self.tools.call_count, 1 if phase == 'badging' else 2)


    def test_execution_json_change_after_parse_is_rejected(self):
        self.execution['fixtureGeneration'] = 'execution-success-generation'
        self.save(self.execution_path, json.dumps(self.execution).encode())
        original_loads = M.json.loads
        changed = False

        def parse_then_change(data, *args, **kwargs):
            nonlocal changed
            value = original_loads(data, *args, **kwargs)
            if isinstance(value, dict) and value.get('fixtureGeneration') == 'execution-success-generation':
                changed = True
                failed = dict(value, exitCode=1, fixtureGeneration='execution-failed-generation')
                self.save(self.execution_path, json.dumps(failed).encode())
            return value

        with patch.object(M.json, 'loads', side_effect=parse_then_change):
            code, receipt = self.execute()
        self.assertTrue(changed)
        self.assertEqual(code, 1)
        self.assertFalse(receipt['passed'])
        self.assertIn('RELEASE_EXECUTION_INPUT_CHANGED', receipt['failure'])
        self.tools.assert_not_called()

    def test_execution_changed_before_final_promotion_is_rejected(self):
        original_record = M.record
        class_file = self.base / 'intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes/com/maplesu/core/ui/webui/ModuleWebUIActivity.class'
        changed = False

        def record_then_change(path):
            nonlocal changed
            value = original_record(path)
            if Path(path) == class_file:
                changed = True
                self.save(self.execution_path, json.dumps(dict(self.execution, exitCode=1)).encode())
            return value

        with patch.object(M, 'record', side_effect=record_then_change):
            code, receipt = self.execute()
        self.assertTrue(changed)
        self.assertEqual(code, 1)
        self.assertFalse(receipt['passed'])
        self.assertIn('RELEASE_EXECUTION_INPUT_CHANGED', receipt['failure'])
        self.assertEqual(self.tools.call_count, 2)

    def test_stable_execution_binds_the_parsed_bytes(self):
        body = self.execution_path.read_bytes()
        code, receipt = self.execute()
        self.assertEqual(code, 0)
        self.assertTrue(receipt['passed'])
        self.assertEqual(receipt['execution']['sha256'], M.digest(body))
        self.assertEqual(receipt['execution']['size'], len(body))
        self.assertEqual(self.tools.call_count, 2)


    def test_sdk_manifest_changed_after_identity_check_is_rejected(self):
        target = self.sdk / 'SDK-RELEASE.json'
        bindings = Path(self.execution['bridgeBindings']['path'])
        original_record = M.record
        changed = False

        def record_then_change(path):
            nonlocal changed
            value = original_record(path)
            if Path(path) == bindings:
                changed = True
                self.save(target, json.dumps(dict(self.release, appVersion='5.2.0')).encode())
            return value

        def changed_version(argv, **kwargs):
            value = self.android_tool(argv, **kwargs)
            if 'badging' in argv:
                value.stdout = value.stdout.replace(b"versionName='5.2.1'", b"versionName='5.2.0'")
            return value

        self.tools.side_effect = changed_version
        with patch.object(M, 'record', side_effect=record_then_change):
            self.rejected('SDK_RELEASE_INPUT_CHANGED')
        self.assertTrue(changed)
        self.tools.assert_not_called()

    def test_manifest_change_after_parse_is_rejected(self):
        targets = ((self.sdk / 'SDK-RELEASE.json', 'SDK_RELEASE_INPUT_CHANGED', 0),
                   (self.root / 'third_party/kernelsu/ksu-payload.json', 'KSU_IDENTITY_MANIFEST_INPUT_CHANGED', 0),
                   (self.bundle / 'BUNDLE-SOURCES.json', 'INIT_BOOT_BRIDGE_SOURCES_INPUT_CHANGED', 2))
        original_loads = M.json.loads
        for target, expected, tool_calls in targets:
            with self.subTest(manifest=target.name):
                body = target.read_bytes()
                self.tools.reset_mock()
                changed = False

                def parse_then_change(data, *args, **kwargs):
                    nonlocal changed
                    value = original_loads(data, *args, **kwargs)
                    if data == body.decode('utf-8-sig'):
                        changed = True
                        self.save(target, body + b'\n')
                    return value

                try:
                    with patch.object(M.json, 'loads', side_effect=parse_then_change):
                        self.rejected(expected)
                    self.assertTrue(changed)
                    self.assertEqual(self.tools.call_count, tool_calls)
                finally:
                    self.save(target, body)

    def test_bundle_sources_changed_after_zip_check_is_rejected(self):
        target = self.bundle / 'BUNDLE-SOURCES.json'
        last_asset = sorted(path for path in self.bundle.rglob('*') if path.is_file())[-1]
        original_record = M.record
        changed = False

        def record_then_change(path):
            nonlocal changed
            value = original_record(path)
            if Path(path) == last_asset:
                changed = True
                sources = M.load(target)
                sources['fixtureGeneration'] = 'changed-after-zip-check'
                self.save(target, json.dumps(sources).encode())
            return value

        with patch.object(M, 'record', side_effect=record_then_change):
            self.rejected('INIT_BOOT_BRIDGE_SOURCES_INPUT_CHANGED')
        self.assertTrue(changed)
        self.assertEqual(self.tools.call_count, 2)

    def test_bundle_sources_changed_between_zip_bytes_and_record_is_rejected(self):
        target = self.bundle / 'BUNDLE-SOURCES.json'
        original_record = M.record
        changed = False

        def record_changed_source(path):
            nonlocal changed
            if Path(path) == target and not changed:
                changed = True
                sources = M.load(target)
                sources['fixtureGeneration'] = 'changed-before-source-record'
                self.save(target, json.dumps(sources).encode())
            return original_record(path)

        with patch.object(M, 'record', side_effect=record_changed_source):
            self.rejected('INIT_BOOT_BRIDGE_SOURCES_INPUT_CHANGED')
        self.assertTrue(changed)
        self.assertEqual(self.tools.call_count, 2)

    def test_manifests_changed_before_final_promotion_are_rejected(self):
        targets = ((self.sdk / 'SDK-RELEASE.json', 'SDK_RELEASE_INPUT_CHANGED'),
                   (self.root / 'third_party/kernelsu/ksu-payload.json', 'KSU_IDENTITY_MANIFEST_INPUT_CHANGED'),
                   (self.bundle / 'BUNDLE-SOURCES.json', 'INIT_BOOT_BRIDGE_SOURCES_INPUT_CHANGED'))
        class_file = self.base / 'intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes/com/maplesu/core/ui/webui/ModuleWebUIActivity.class'
        original_record = M.record
        for target, expected in targets:
            with self.subTest(manifest=target.name):
                body = target.read_bytes()
                self.tools.reset_mock()
                changed = False

                def record_then_change(path):
                    nonlocal changed
                    value = original_record(path)
                    if Path(path) == class_file:
                        changed = True
                        self.save(target, body + b'\n')
                    return value

                try:
                    with patch.object(M, 'record', side_effect=record_then_change):
                        self.rejected(expected)
                    self.assertTrue(changed)
                    self.assertEqual(self.tools.call_count, 2)
                finally:
                    self.save(target, body)

    def test_stable_manifests_bind_consumed_bytes(self):
        sdk = M.record(self.sdk / 'SDK-RELEASE.json')
        pin = M.record(self.root / 'third_party/kernelsu/ksu-payload.json')
        bundled = M.record(self.bundle / 'BUNDLE-SOURCES.json')
        code, receipt = self.execute()
        self.assertEqual(code, 0)
        self.assertTrue(receipt['passed'])
        self.assertEqual(receipt['sdkRelease'], sdk)
        self.assertEqual(receipt['ksuIdentityManifest'], pin)
        source = next(row['source'] for row in receipt['initBootBridgeAssets']
                      if row['entry'] == 'assets/ultra-init-bridge/BUNDLE-SOURCES.json')
        self.assertEqual(source, bundled)
        self.assertEqual(self.tools.call_count, 2)

    def test_sdk_identity_and_bridge_failure_remain_before_json_parse(self):
        target = self.sdk / 'SDK-RELEASE.json'
        bindings = Path(self.execution['bridgeBindings']['path'])
        self.save(target, b'{')
        self.save(bindings, b'changed bindings')
        self.rejected('SOURCE_IDENTITY_MISMATCH: ' + str(target))
        self.tools.assert_not_called()
        self.execution['sdkRelease'] = M.record(target)
        self.save(self.execution_path, json.dumps(self.execution).encode())
        self.rejected('SOURCE_IDENTITY_MISMATCH: ' + str(bindings))
        self.tools.assert_not_called()



if __name__ == '__main__':
    unittest.main()
