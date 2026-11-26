"""Exercise the production static-ELF boundary without running target code."""
import importlib.util
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location("ultra_init_build", Path(__file__).resolve().parents[1] / "build.py")
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


def executable():
    data = bytearray(512)
    data[:7] = b"\x7fELF\x02\x01\x01"
    struct.pack_into("<HHIQQQIHHHHHH", data, 16, 2, 183, 1, 0x10000, 64, 0, 0, 64, 56, 2, 0, 0, 0)
    struct.pack_into("<IIQQQQQQ", data, 64, 1, 5, 0, 0x10000, 0, len(data), len(data), 65536)
    struct.pack_into("<IIQQQQQQ", data, 120, 0x6474E551, 6, 0, 0, 0, 0, 0, 16)
    return data


class StaticElfTest(unittest.TestCase):
    def reject(self, data, code):
        with self.assertRaisesRegex(ValueError, code):
            build.verify_static_executable(data)

    def test_static_page_contract(self):
        self.assertEqual(build.verify_static_executable(executable())["pageSizes"], [4096, 16384, 65536])

    def test_malformed_inputs(self):
        for size in (0, 6, 63, 119):
            self.reject(executable()[:size], "ULTRA_INIT_ELF_")
        data = executable()
        struct.pack_into("<H", data, 18, 62)
        self.reject(data, "ELF_TARGET")
        data = executable()
        struct.pack_into("<Q", data, 32, 2**64 - 1)
        self.reject(data, "PROGRAM_HEADERS")

    def test_dynamic_dependencies(self):
        data = executable()
        struct.pack_into("<I", data, 120, 3)
        self.reject(data, "ELF_INTERPRETER")
        data = executable()
        struct.pack_into("<IIQQQQQQ", data, 120, 2, 6, 256, 256, 0, 16, 16, 8)
        struct.pack_into("<qQ", data, 256, 1, 0)
        self.reject(data, "ELF_NEEDED")

    def test_permissions_and_entry(self):
        data = executable()
        struct.pack_into("<I", data, 68, 7)
        self.reject(data, "LOAD_PERMISSIONS")
        data = executable()
        struct.pack_into("<I", data, 124, 7)
        self.reject(data, "ENTRY_OR_STACK")
        data = executable()
        struct.pack_into("<Q", data, 24, 0x20000)
        self.reject(data, "ENTRY_OR_STACK")

    def test_alignment_and_ranges(self):
        data = executable()
        struct.pack_into("<Q", data, 112, 4096)
        self.reject(data, "LOAD_ALIGNMENT")
        data = executable()
        struct.pack_into("<Q", data, 96, 513)
        self.reject(data, "SEGMENT_RANGE")
        data = executable()
        struct.pack_into("<Q", data, 104, 511)
        self.reject(data, "LOAD_PERMISSIONS")

    def test_rejects_existing_build_directory_before_running_tools(self):
        with tempfile.TemporaryDirectory() as directory:
            receipt = Path(directory) / "ULTRA-INIT-BUILD.json"
            receipt.write_text("previous success", encoding="utf-8")
            result = subprocess.run([sys.executable, "-B", str(spec.origin),
                                     "--ndk", "missing", "--cmake", "missing", "--ninja", "missing",
                                     "--build-dir", directory], capture_output=True, text=True)
            self.assertEqual(result.returncode, 2)
            self.assertIn("build-dir must be new", result.stderr)
            self.assertEqual(receipt.read_text(encoding="utf-8"), "previous success")



class BuildReceiptTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / "runtime/ultra/boot/ultra-init"
        self.source.mkdir(parents=True)
        for name in ("CMakeLists.txt", "main.c", "handoff.c", "handoff.h", "legacy_direct_bind.c",
                     "legacy_direct_bind.h", "legacy_direct_loader.c", "legacy_direct_loader.h"):
            (self.source / name).write_bytes(("fixture:" + name).encode())
        (self.source / "build.py").write_bytes(Path(spec.origin).read_bytes())
        headers = self.root / "runtime/ultra/identity/kernel"
        headers.mkdir(parents=True)
        for name in ("manager_deployment_namespace.h", "manager_claim_loader.h"):
            (headers / name).write_bytes(("fixture:" + name).encode())
        toolchain = self.root / "ndk/build/cmake/android.toolchain.cmake"
        toolchain.parent.mkdir(parents=True)
        toolchain.write_bytes(b"fixture toolchain")
        self.cmake = self.root / "cmake"
        self.ninja = self.root / "ninja"
        self.cmake.write_bytes(b"fixture cmake")
        self.ninja.write_bytes(b"fixture ninja")
        selected = importlib.util.spec_from_file_location("receipt_fixture_build", self.source / "build.py")
        self.api = importlib.util.module_from_spec(selected)
        selected.loader.exec_module(self.api)
        self.output = self.root / "output"
        self.arguments = [str(self.source / "build.py"), "--ndk", str(self.root / "ndk"),
                          "--cmake", str(self.cmake), "--ninja", str(self.ninja),
                          "--build-dir", str(self.output)]

    def execute(self, mutate=None):
        commands = []

        def invoke(command, *, check):
            self.assertTrue(check)
            commands.append(command)
            if command[1] == "--build":
                (self.output / "maplesu-ultra-init").write_bytes(executable())
                if mutate is not None:
                    (self.source / mutate).write_bytes(b"changed while compiling")

        with mock.patch.object(sys, "argv", self.arguments), mock.patch.object(self.api.subprocess, "run", invoke):
            self.api.main()
        return commands

    def test_build_receipt_binds_linked_loader_and_binder_sources(self):
        commands = self.execute()
        self.assertEqual(len(commands), 2)
        receipt = json.loads((self.output / "ULTRA-INIT-BUILD.json").read_bytes())
        bound = {Path(row["path"]).name: row for row in receipt["sources"]}
        for name in ("legacy_direct_bind.c", "legacy_direct_bind.h", "legacy_direct_loader.c", "legacy_direct_loader.h"):
            self.assertEqual(bound[name], self.api.identity(self.source / name))
        self.assertEqual(receipt["artifact"], self.api.identity(self.output / "maplesu-ultra-init"))
        self.assertFalse(receipt["elf"]["executed"])

    def test_loader_change_during_build_leaves_no_success_receipt(self):
        with self.assertRaisesRegex(RuntimeError, "ULTRA_INIT_SOURCE_CHANGED_DURING_BUILD"):
            self.execute("legacy_direct_loader.c")
        self.assertFalse((self.output / "ULTRA-INIT-BUILD.json").exists())


if __name__ == "__main__":
    unittest.main()
