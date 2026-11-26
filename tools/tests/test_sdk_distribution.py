import copy
import hashlib
import json
import os
import shutil
from pathlib import Path
import sys
import tempfile
import unittest

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from check_sdk_source_contract import (
    check_member_sources, check_retained_sdk, check_snapshot_policy,
    check_source_checkpoint, required_file,
)

ROOT = Path(os.environ.get('MAPLESU_SOURCE_CONTRACT_ROOT', TOOLS.parent)).resolve()
sys.path.insert(0, str(ROOT / 'scripts'))
from source_sdk_delivery import verify_source_delivery


class SdkSourceCheckpointTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads((ROOT / 'runtime/maplesu-sdk/SDK-RELEASE.json').read_text(encoding='utf-8-sig'))

    def test_snapshot_checks_retained_inputs_without_claiming_artifact_acceptance(self):
        result = check_source_checkpoint(ROOT)
        self.assertEqual('work-in-progress-source-checkpoint', result['scope'])
        self.assertFalse(result['artifactProvenanceVerified'])
        self.assertFalse(result['runtimeAcceptance'])
        self.assertGreater(result['sourceMembers'], 0)
        self.assertEqual(self.manifest['sdkBuildId'], result['retainedSdkBuildId'])
        self.assertNotIn('sourceBuildVerified', result)

    def test_changed_archive_is_rejected(self):
        manifest = copy.deepcopy(self.manifest)
        manifest['archive']['sha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'retained input changed'):
            check_retained_sdk(ROOT, manifest)

    def test_rehashed_archive_with_unpaired_embedded_helper_is_rejected(self):
        manifest = copy.deepcopy(self.manifest)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for name in (manifest['helper']['path'],
                         manifest['environmentPolicyBoot']['backend']['path'],
                         'runtime/maplesu-sdk/SDK-IDENTITY.cmake', 'scripts/policy_backend_identity.py',
                         'scripts/ar_archive.py'):
                target = root / name
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(ROOT / name, target)
            archive = root / manifest['archive']['path']
            archive.parent.mkdir(parents=True, exist_ok=True)
            data = bytearray((ROOT / manifest['archive']['path']).read_bytes())
            helper = (ROOT / manifest['helper']['path']).read_bytes()
            self.assertEqual(1, data.count(helper))
            offset = data.index(helper)
            data[offset + len(helper) - 1] ^= 1
            archive.write_bytes(data)
            old_sha = manifest['archive']['sha256']
            manifest['archive']['sha256'] = hashlib.sha256(data).hexdigest()
            identity = root / 'runtime/maplesu-sdk/SDK-IDENTITY.cmake'
            identity.write_text(identity.read_text(encoding='utf-8').replace(old_sha, manifest['archive']['sha256']), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'archive embedded helper differs'):
                check_retained_sdk(root, manifest)

    def test_changed_helper_is_rejected(self):
        manifest = copy.deepcopy(self.manifest)
        manifest['helper']['sha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'retained input changed'):
            check_retained_sdk(ROOT, manifest)

    def test_wrong_retained_helper_identity_is_rejected(self):
        manifest = copy.deepcopy(self.manifest)
        manifest['helper']['buildId'] = '0' * 40
        with self.assertRaisesRegex(ValueError, 'helper build identity'):
            check_retained_sdk(ROOT, manifest)

    def test_wrong_retained_helper_slot_is_rejected(self):
        manifest = copy.deepcopy(self.manifest)
        if 'keySlotOffset' in manifest['helper']:
            manifest['helper']['keySlotOffset'] += 1
        else:
            manifest['helper']['keySlotOffset'] = 1
        with self.assertRaisesRegex(ValueError, 'helper (normalized identity|credential shape)'):
            check_retained_sdk(ROOT, manifest)

    def test_wrong_backend_identity_is_rejected(self):
        manifest = copy.deepcopy(self.manifest)
        manifest['environmentPolicyBoot']['backend']['noteHex'] = '00'
        with self.assertRaisesRegex(ValueError, 'backend identity'):
            check_retained_sdk(ROOT, manifest)

    def test_changed_backend_is_rejected(self):
        manifest = copy.deepcopy(self.manifest)
        manifest['environmentPolicyBoot']['backend']['sha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'retained input changed'):
            check_retained_sdk(ROOT, manifest)

    def test_outside_source_path_is_rejected(self):
        for name in ('../SDK-RELEASE.json', '/SDK-RELEASE.json', 'C:/SDK-RELEASE.json', 'sdk\\file'):
            with self.subTest(path=name), self.assertRaisesRegex(ValueError, 'invalid SDK source path'):
                required_file(ROOT, name)

    def test_non_snapshot_policy_is_rejected(self):
        policy = json.loads((ROOT / 'release/source-policy.json').read_text(encoding='utf-8'))
        for scope in (None, 'release-candidate', 'release'):
            candidate = copy.deepcopy(policy)
            candidate['public_scope'] = scope
            with self.subTest(scope=scope), self.assertRaisesRegex(ValueError, 'requires explicit WIP'):
                check_snapshot_policy(candidate)

    def test_resolved_release_blocker_is_rejected(self):
        policy = json.loads((ROOT / 'release/source-policy.json').read_text(encoding='utf-8'))
        for blocker in ('complete-source-build', 'maplesu-5.2.1-acceptance'):
            candidate = copy.deepcopy(policy)
            next(item for item in candidate['release_blockers'] if item['id'] == blocker)['resolved'] = True
            with self.subTest(blocker=blocker), self.assertRaisesRegex(ValueError, 'unresolved release blockers'):
                check_snapshot_policy(candidate)

    def source_fixture(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        source = root / 'runtime/maplesu-sdk/sdk/src/one.cpp'
        source.parent.mkdir(parents=True)
        source.write_text('int value;\n', encoding='utf-8')
        manifest_dir = root / 'runtime/maplesu-sdk/sdk/manifest'
        manifest_dir.mkdir()
        records = []
        # Repeated archive member names are valid; ordinals disambiguate them.
        for idx in range(2):
            record = dict(idx=idx, name='same.o', kind='SINGLE', layout='elf',
                          sources=['src/one.cpp'], flagsProfile='test')
            definition = f'runtime/maplesu-sdk/sdk/manifest/{idx:04d}_same.o.json'
            (root / definition).write_text(json.dumps(record), encoding='utf-8')
            records.append(dict(record, sourceFile=definition))
        index = root / 'runtime/maplesu-sdk/sdk/manifest.jsonl'
        index.write_text('\n'.join(json.dumps(row) for row in records) + '\n', encoding='utf-8')
        return root, records

    def test_member_count_follows_source_and_duplicate_names_are_valid(self):
        root, _ = self.source_fixture()
        result = check_member_sources(root)
        self.assertEqual(2, result['sourceMembers'])
        self.assertEqual(1, result['sourceFiles'])

    def test_missing_member_source_is_rejected(self):
        root, _ = self.source_fixture()
        (root / 'runtime/maplesu-sdk/sdk/src/one.cpp').unlink()
        with self.assertRaisesRegex(ValueError, 'missing SDK source file'):
            check_member_sources(root)

    def test_unindexed_member_definition_is_rejected(self):
        root, _ = self.source_fixture()
        path = root / 'runtime/maplesu-sdk/sdk/manifest/0002_extra.o.json'
        path.write_text('{}', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'member definition coverage'):
            check_member_sources(root)

    def test_changed_source_mapping_is_rejected(self):
        root, records = self.source_fixture()
        path = root / records[0]['sourceFile']
        definition = json.loads(path.read_text(encoding='utf-8'))
        definition['sources'] = ['src/different.cpp']
        path.write_text(json.dumps(definition), encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'member index differs'):
            check_member_sources(root)

    def test_missing_declared_header_is_rejected(self):
        root, records = self.source_fixture()
        path = root / records[0]['sourceFile']
        definition = json.loads(path.read_text(encoding='utf-8'))
        definition['privateHeaders'] = ['include/missing.h']
        path.write_text(json.dumps(definition), encoding='utf-8')
        records[0]['privateHeaders'] = definition['privateHeaders']
        index = root / 'runtime/maplesu-sdk/sdk/manifest.jsonl'
        index.write_text('\n'.join(json.dumps(row) for row in records) + '\n', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'missing SDK source file'):
            check_member_sources(root)

    def test_stale_cmake_identity_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            identity = root / 'runtime/maplesu-sdk/SDK-IDENTITY.cmake'
            identity.parent.mkdir(parents=True)
            original = ROOT / 'runtime/maplesu-sdk/SDK-IDENTITY.cmake'
            identity.write_text(original.read_text(encoding='utf-8').replace(self.manifest['sdkBuildId'], 'stale-build'), encoding='utf-8')
            # Identity is checked separately after real retained binary verification.
            from check_sdk_source_contract import check_cmake_identity
            with self.assertRaisesRegex(ValueError, 'SDK-IDENTITY.cmake is stale'):
                check_cmake_identity(root, self.manifest)

    def test_formal_artifact_verifier_still_rejects_missing_build_receipt(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / 'fixture.a'
            archive.write_bytes(b'fixture archive bytes, never a production receipt')
            manifest = {
                'rootKeyRequired': False, 'helper': {'keyless': True},
                'archive': {'path': archive.name, 'size': archive.stat().st_size,
                            'sha256': hashlib.sha256(archive.read_bytes()).hexdigest()},
                'sourceBuild': {'schemaVersion': 1, 'deviceAcceptance': False,
                                'buildReport': {'path': 'absent-build-report.json', 'size': 1, 'sha256': '0' * 64}},
            }
            with self.assertRaisesRegex(AssertionError, 'SOURCE_SDK_FILE: absent-build-report.json'):
                verify_source_delivery(manifest, root)


if __name__ == '__main__':
    unittest.main()
