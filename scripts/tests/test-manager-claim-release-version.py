"""AST-isolated publisher functions and version-input capture; no native build."""

import argparse
import ast
import gzip
import hashlib
import json
from pathlib import Path
import re
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
PARSER = argparse.ArgumentParser()
PARSER.add_argument("--builder", type=Path, default=ROOT / "scripts/build-ultra-manager-claim.py")
PARSER.add_argument("--gradle-build", type=Path, default=ROOT / "manager/build.gradle.kts")
ARGS, UNITTEST_ARGS = PARSER.parse_known_args()
SOURCE = ARGS.builder.read_text(encoding="utf-8-sig")
TREE = ast.parse(SOURCE)
FUNCTIONS = {"claim_release_version", "write_runtime_manifest", "identity", "sha"}
CODE = compile(ast.Module(body=[node for node in TREE.body
    if isinstance(node, ast.FunctionDef) and node.name in FUNCTIONS], type_ignores=[]),
    str(ARGS.builder), "exec")
MAIN = next(node for node in TREE.body if isinstance(node, ast.FunctionDef) and node.name == "main")
BEFORE = next(node.value for node in MAIN.body if isinstance(node, ast.Assign) and
    any(isinstance(target, ast.Name) and target.id == "before" for target in node.targets))
UNCHANGED = next(node.value for node in MAIN.body if isinstance(node, ast.Assign) and
    any(isinstance(target, ast.Name) and target.id == "unchanged" for target in node.targets))
ROUTES = ast.parse((ROOT / "scripts/policy_claim_routes.py").read_text(encoding="utf-8-sig"))
RAW_FAMILIES = ast.literal_eval(next(node.value for node in ROUTES.body if isinstance(node, ast.Assign)
    and any(isinstance(target, ast.Name) and target.id == "RAW_FAMILIES" for target in node.targets)))


class ClaimReleaseVersionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.assets, self.out = self.root / "assets", self.root / "out"
        self.assets.mkdir()
        self.out.mkdir()
        self.env = dict(ROOT=self.root, Path=Path, re=re, gzip=gzip, hashlib=hashlib,
                        json=json, RAW_FAMILIES=RAW_FAMILIES)
        exec(CODE, self.env)
        self.write_gradle('versionName = "5.2.1"\nversionCode = 50201\n')

    def write_gradle(self, text):
        path = self.root / "manager/build.gradle.kts"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def snapshot_environment(self):
        # Evaluate the real main() capture and unchanged expressions. Unrelated
        # native source collectors have empty fixture selections, not build receipts.
        env = dict(self.env)
        fixture = self.root / "fixture"
        fixture.write_bytes(b"unrelated fixture input")
        (self.root / "BASELINE.json").write_bytes(b"fixture baseline")
        for element in BEFORE.generators[0].iter.elts:
            if isinstance(element, ast.Starred) and isinstance(element.value, ast.Name):
                env[element.value.id] = []
            elif isinstance(element, ast.Name):
                env[element.id] = fixture
            elif isinstance(element, ast.Call) and isinstance(element.func, ast.Attribute):
                env[element.func.value.id] = fixture
            for node in ast.walk(element):
                if isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
                    env[node.func.id] = lambda: []
        return env

    def snapshot(self, env):
        return eval(compile(ast.Expression(body=BEFORE), str(ARGS.builder), "eval"), env)

    def results(self, families=("android15-6.6",), retirement=False):
        rows = []
        for index, family in enumerate(families):
            (self.assets / family).mkdir(exist_ok=True)
            (self.out / family).mkdir(exist_ok=True)
            row = dict(kernel=family, payload=dict(bytes=index + 1,
                sha256=hashlib.sha256(family.encode()).hexdigest(), familyFact=family,
                nativeAbi={"offset": 8, "signature": "fixture ABI", "unicode": "枫"}))
            if retirement:
                row["retirementSupport"] = dict(bootstrap=dict(bytes=3, sha256="bootstrap-sha"))
            rows.append(row)
        return rows

    def publish(self, rows=None, routes=None):
        if rows is None:
            rows = self.results()
        inputs = {str(Path("manager/build.gradle.kts")):
                  self.env["sha"](self.root / "manager/build.gradle.kts")}
        support = dict(bootstrap=dict(bytes=3, sha256="support-sha"))
        self.env["write_runtime_manifest"](self.assets, self.out, inputs, support, rows, routes)
        return json.loads((self.assets / "manifest.json").read_text(encoding="utf-8")), inputs, support

    def test_version_comes_from_gradle(self):
        self.assertEqual(self.env["claim_release_version"](),
                         dict(appVersion="5.2.1", versionCode=50201))

    def test_version_changes_follow_gradle(self):
        self.write_gradle('versionName = "5.3.4"\nversionCode = 50304\n')
        self.assertEqual(self.env["claim_release_version"](),
                         dict(appVersion="5.3.4", versionCode=50304))

    def test_version_accepts_utf8_bom(self):
        self.write_gradle('\ufeffversionName = "5.2.1"\nversionCode = 50201\n')
        self.assertEqual(self.env["claim_release_version"]()["versionCode"], 50201)

    def test_invalid_or_ambiguous_version_is_rejected(self):
        for text in ('', 'versionName = "5.2.1"\n', 'versionCode = 50201\n',
                'versionName = "5.2.1-beta"\nversionCode = 50201\n',
                'versionName = "5.2.1"\nversionCode = 0\n',
                'versionName = "5.2.1"\nversionCode = -1\n',
                'versionName = "5.2.1"\nversionCode = 50201.5\n',
                'versionName = "5.2.1"\nversionCode = "50201"\n',
                'versionName = "5.2.1"\nversionName = "5.2.2"\nversionCode = 50201\n',
                'versionName = "5.2.1"\nversionCode = 50201\nversionCode = 50202\n'):
            with self.subTest(text=text):
                self.write_gradle(text)
                with self.assertRaisesRegex(ValueError, "one exact Manager release version"):
                    self.env["claim_release_version"]()

    def test_before_snapshot_hashes_actual_gradle_bytes(self):
        before = self.snapshot(self.snapshot_environment())
        key = str(Path("manager/build.gradle.kts"))
        self.assertIn(key, before)
        self.assertEqual(before[key], self.env["sha"](self.root / key))

    def test_existing_unchanged_guard_detects_version_source_mutation(self):
        env = self.snapshot_environment()
        env["before"] = self.snapshot(env)
        self.assertTrue(eval(compile(ast.Expression(body=UNCHANGED), str(ARGS.builder), "eval"), env))
        self.write_gradle('versionName = "5.2.2"\nversionCode = 50202\n')
        self.assertFalse(eval(compile(ast.Expression(body=UNCHANGED), str(ARGS.builder), "eval"), env))

    def test_manifest_binds_version_inputs_and_support(self):
        manifest, inputs, support = self.publish()
        self.assertEqual(manifest["schemaVersion"], 2)
        self.assertEqual((manifest["appVersion"], manifest["versionCode"]), ("5.2.1", 50201))
        self.assertEqual(manifest["inputs"], inputs)
        self.assertEqual(manifest["support"], support)

    def test_manifest_version_is_not_hardcoded(self):
        self.write_gradle('versionName = "8.9.10"\nversionCode = 80910\n')
        manifest, _, _ = self.publish()
        self.assertEqual((manifest["appVersion"], manifest["versionCode"]), ("8.9.10", 80910))

    def test_manifest_chunks_retain_complete_metadata_and_native_identity(self):
        rows = self.results()
        manifest, _, _ = self.publish(rows)
        for row in rows:
            family = row["kernel"]
            payload = manifest["payloads"][family]
            chunks = []
            for chunk in payload["chunks"]:
                data = (self.assets / chunk["path"]).read_bytes()
                self.assertEqual(chunk["bytes"], len(data))
                self.assertEqual(chunk["sha256"], hashlib.sha256(data).hexdigest())
                chunks.append(data)
            compressed = b"".join(chunks)
            decoded = gzip.decompress(compressed)
            self.assertEqual(decoded, (self.out / family / "runtime-payload-metadata.full.json").read_bytes())
            self.assertEqual(json.loads(decoded), row["payload"])
            self.assertEqual(payload["bytes"], row["payload"]["bytes"])
            self.assertEqual(payload["sha256"], row["payload"]["sha256"])
            self.assertEqual(payload["decodedBytes"], len(decoded))
            self.assertEqual(payload["decodedSha256"], hashlib.sha256(decoded).hexdigest())
            self.assertEqual(payload["gzipBytes"], len(compressed))
            self.assertEqual(payload["gzipSha256"], hashlib.sha256(compressed).hexdigest())

    def test_packaging_receipt_identifies_actual_manifest(self):
        manifest, _, _ = self.publish()
        receipt = json.loads((self.out / "runtime-manifest-packaging.json").read_text())
        self.assertEqual(receipt["manifest"], self.env["identity"](self.assets / "manifest.json"))
        self.assertEqual(receipt["payloads"], manifest["payloads"])
        self.assertIs(receipt["completeMetadataRetained"], True)

    def test_four_raw_families_preserve_module_routes(self):
        routes = {family: dict(module="fixture") for family in RAW_FAMILIES}
        manifest, _, _ = self.publish(self.results(sorted(RAW_FAMILIES)), routes)
        self.assertEqual(manifest["moduleRoutes"], routes)
        self.assertEqual(set(manifest["payloads"]), RAW_FAMILIES)

    def test_partial_raw_family_publication_rejects_module_routes(self):
        with self.assertRaisesRegex(ValueError, "all four actual raw families"):
            self.publish(routes={"fixture": True})
        self.assertFalse((self.assets / "manifest.json").exists())

    def test_retirement_bootstrap_variant_is_preserved(self):
        rows = self.results(retirement=True)
        manifest, _, _ = self.publish(rows)
        payload = manifest["payloads"][rows[0]["kernel"]]
        self.assertEqual(payload["support"]["bootstrap"], dict(bytes=3,
            sha256="bootstrap-sha", path="android15-6.6/bootstrap.bin"))
        compressed = b"".join((self.assets / c["path"]).read_bytes() for c in payload["chunks"])
        self.assertEqual(json.loads(gzip.decompress(compressed))["support"], payload["support"])

    def test_invalid_version_does_not_publish_manifest(self):
        self.write_gradle('versionName = "5.2.1"\nversionCode = 0\n')
        with self.assertRaisesRegex(ValueError, "one exact Manager release version"):
            self.publish()
        self.assertFalse((self.assets / "manifest.json").exists())
        self.assertFalse((self.out / "runtime-manifest-packaging.json").exists())

    def test_gradle_version_code_check_uses_exact_int_without_coercion(self):
        gradle = ARGS.gradle_build.read_text(encoding="utf-8-sig")
        check = re.search(r'check\(manifest\["appVersion"\].*?\{\s*'
            r'"ULTRA_CLAIM_RELEASE_VERSION_MISMATCH"', gradle, re.S)
        self.assertIsNotNone(check)
        self.assertIn('(manifest["versionCode"] as? Int) == releaseVersionCode.get()', check.group())
        self.assertNotIn('toInt()', check.group())


if __name__ == "__main__":
    unittest.main(argv=[__file__, *UNITTEST_ARGS])
