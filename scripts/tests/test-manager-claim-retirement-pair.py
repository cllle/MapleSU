"""Exercise the actual retirement Image/native source-pair comparison loop."""
import argparse
import ast
import copy
from pathlib import Path
import tempfile
import unittest


parser = argparse.ArgumentParser()
parser.add_argument("--builder", type=Path,
    default=Path(__file__).resolve().parents[1] / "build-ultra-manager-claim.py")
args, unittest_args = parser.parse_known_args()
tree = ast.parse(args.builder.read_text(encoding="utf-8-sig"))
collector = next(node for node in tree.body
    if isinstance(node, ast.FunctionDef) and node.name == "collect_host_runtime_abi")
loop = next(node for node in ast.walk(collector) if isinstance(node, ast.For)
    and isinstance(node.iter, ast.Subscript) and isinstance(node.iter.value, ast.Name)
    and node.iter.value.id == "HOST_RETIREMENT_SELECTION")
code = compile(ast.Module(body=[loop], type_ignores=[]), str(args.builder), "exec")
LOGICAL = "runtime/ultra/identity/kernel/manager_resident_abi.h"
ERROR = "Retirement Image callback/native source pair differs"


class RetirementSourcePairTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name).resolve()
        self.path = self.root / "manager_resident_abi.h"
        self.path.write_bytes(b"paired source")
        self.expected = dict(path=str(self.path), bytes=13, sha256="frozen-digest")
        self.actual = dict(self.expected)

    def compare(self, actual=None, expected=None, missing=False):
        expected = self.expected if expected is None else expected
        actual = self.actual if actual is None else actual
        env = dict(Path=Path, HOST_RETIREMENT_SELECTION={"files": [dict(
            logicalPath=LOGICAL, after=dict(expected, sha256="older-source"))]},
            RUNTIME_LAYOUT_SELECTION={"files": [dict(logicalPath=LOGICAL, after=expected)]},
            actual_sources={} if missing else {LOGICAL: actual})
        exec(code, env)

    def test_native_and_posix_path_spellings_match(self):
        actual = dict(self.actual, path=self.path.as_posix())
        self.compare(actual=actual)

    def test_equivalent_resolved_path_matches(self):
        (self.root / "intermediate").mkdir()
        self.compare(actual=dict(self.actual, path=str(self.root / "intermediate" / ".." / self.path.name)))

    def test_same_content_at_different_actual_path_is_rejected(self):
        other = self.root / "other.h"
        other.write_bytes(self.path.read_bytes())
        with self.assertRaisesRegex(ValueError, ERROR):
            self.compare(actual=dict(self.actual, path=str(other)))

    def test_changed_size_is_rejected(self):
        with self.assertRaisesRegex(ValueError, ERROR):
            self.compare(actual=dict(self.actual, bytes=14))

    def test_changed_hash_is_rejected(self):
        with self.assertRaisesRegex(ValueError, ERROR):
            self.compare(actual=dict(self.actual, sha256="different-digest"))

    def test_missing_source_is_rejected(self):
        with self.assertRaisesRegex(ValueError, ERROR):
            self.compare(missing=True)

    def test_extra_identity_field_is_rejected(self):
        with self.assertRaisesRegex(ValueError, ERROR):
            self.compare(actual=dict(self.actual, unexpected="extra"))

    def test_missing_identity_path_is_rejected(self):
        actual = copy.copy(self.actual)
        del actual["path"]
        with self.assertRaises((ValueError, KeyError)):
            self.compare(actual=actual)

    def test_retirement_source_fallback_remains_exact(self):
        logical = "runtime/ultra/identity/kernel/manager_claim_bootstrap.c"
        env = dict(Path=Path, HOST_RETIREMENT_SELECTION={"files": [dict(
            logicalPath=logical, after=self.expected)]}, RUNTIME_LAYOUT_SELECTION=None,
            actual_sources={logical: self.actual})
        exec(code, env)
        env["actual_sources"][logical] = dict(self.actual, sha256="different-digest")
        with self.assertRaisesRegex(ValueError, ERROR):
            exec(code, env)


if __name__ == "__main__":
    unittest.main(argv=[__file__, *unittest_args])
