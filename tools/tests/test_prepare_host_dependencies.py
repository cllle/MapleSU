"""Narrow tests for the pinned host dependency preparer."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

SPEC = importlib.util.spec_from_file_location("prepare_host_dependencies", Path(__file__).resolve().parents[1] / "prepare_host_dependencies.py")
PREPARE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PREPARE)


class HostDependenciesTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="dependency-test-")
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.data = b"pinned-test-jar"
        self.record = dict(group="test.group", artifact="fixture", version="1.0",
                           bytes=len(self.data), sha256=hashlib.sha256(self.data).hexdigest())

    def target(self):
        return self.directory / "caches/modules-2/files-2.1/test.group/fixture/1.0" / self.record["sha256"] / "fixture-1.0.jar"

    def lock(self, value):
        path = self.directory / "lock.json"
        path.write_text(json.dumps(value), encoding="utf-8")
        return path

    def download(self, data):
        return mock.patch.object(PREPARE.urllib.request, "urlopen", return_value=io.BytesIO(data))

    def test_missing_dependency_is_not_accepted(self):
        with mock.patch.object(PREPARE.urllib.request, "urlopen") as network:
            with self.assertRaisesRegex(ValueError, "HOST_JVM_DEPENDENCY_MISSING:fixture"):
                PREPARE.prepare(self.record, self.directory, check_only=True)
            network.assert_not_called()

    def test_official_coordinate_and_verified_publication(self):
        with self.download(self.data) as network:
            self.assertTrue(PREPARE.prepare(self.record, self.directory))
            network.assert_called_once_with("https://repo.maven.apache.org/maven2/test/group/fixture/1.0/fixture-1.0.jar", timeout=60)
        self.assertEqual(self.target().read_bytes(), self.data)
        self.assertEqual(list(self.directory.rglob("*.part")), [])

    def test_reuse_and_check_do_not_need_network(self):
        with self.download(self.data):
            PREPARE.prepare(self.record, self.directory)
        with mock.patch.object(PREPARE.urllib.request, "urlopen") as network:
            self.assertFalse(PREPARE.prepare(self.record, self.directory))
            self.assertFalse(PREPARE.prepare(self.record, self.directory, check_only=True))
            network.assert_not_called()

    def test_tampered_cache_is_rejected(self):
        self.target().parent.mkdir(parents=True)
        self.target().write_bytes(b"X" * len(self.data))
        with self.assertRaisesRegex(ValueError, "HOST_JVM_DEPENDENCY_SHA256_MISMATCH:fixture"):
            PREPARE.prepare(self.record, self.directory)

    def test_unexpected_jar_selection_is_rejected(self):
        other = self.target().parent.parent / "00000000/fixture-1.0.jar"
        other.parent.mkdir(parents=True)
        other.write_bytes(self.data)
        with self.assertRaisesRegex(ValueError, "HOST_JVM_DEPENDENCY_UNEXPECTED_JAR:fixture"):
            PREPARE.prepare(self.record, self.directory)

    def test_wrong_size_is_not_published(self):
        for data in (self.data[:-1], self.data + b"X"):
            with self.download(data):
                with self.assertRaisesRegex(ValueError, "HOST_JVM_DEPENDENCY_SIZE_MISMATCH:fixture"):
                    PREPARE.prepare(self.record, self.directory)
            self.assertFalse(self.target().exists())
            self.assertEqual(list(self.directory.rglob("*.part")), [])

    def test_wrong_hash_is_not_published(self):
        with self.download(b"X" * len(self.data)):
            with self.assertRaisesRegex(ValueError, "HOST_JVM_DEPENDENCY_SHA256_MISMATCH:fixture"):
                PREPARE.prepare(self.record, self.directory)
        self.assertFalse(self.target().exists())
        self.assertEqual(list(self.directory.rglob("*.part")), [])

    def test_duplicate_coordinates_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "HOST_JVM_LOCK_DUPLICATE"):
            PREPARE.load_lock(self.lock([self.record, self.record]))

    def test_path_coordinates_are_rejected(self):
        for field in ("group", "artifact", "version"):
            with self.assertRaisesRegex(ValueError, "HOST_JVM_LOCK_COORDINATE:" + field):
                PREPARE.load_lock(self.lock([dict(self.record, **{field: "../fixture"})]))

    def test_date_version_and_lock_digests(self):
        record = dict(self.record, version="20240303")
        self.assertEqual(PREPARE.load_lock(self.lock([record])), [record])
        for field, value, code in (("sha256", "abc", "SHA256"), ("bytes", 0, "SIZE")):
            with self.assertRaisesRegex(ValueError, "HOST_JVM_LOCK_" + code):
                PREPARE.load_lock(self.lock([dict(self.record, **{field: value})]))

    def test_cli_reports_missing_dependency(self):
        with mock.patch("sys.stderr", new_callable=io.StringIO) as error:
            code = PREPARE.main(["--lock", str(self.lock([self.record])), "--gradle-user-home", str(self.directory), "--check"])
        self.assertEqual(code, 1)
        self.assertIn("HOST_JVM_DEPENDENCY_MISSING:fixture", error.getvalue())

    def test_cli_requires_explicit_cache(self):
        with mock.patch.dict(PREPARE.os.environ, {}, clear=True):
            with mock.patch("sys.stderr", new_callable=io.StringIO):
                with self.assertRaises(SystemExit) as error:
                    PREPARE.main([])
        self.assertEqual(error.exception.code, 2)


if __name__ == "__main__":
    unittest.main(verbosity=2)
