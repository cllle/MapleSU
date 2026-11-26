"""Exercise the real authenticated SDK gateway and L3 credential handover."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "jailbreak-providers/dirtyfrag/tests"))
from test_l3_arm import definition, host_compiler


class BootSdkRootHandoverTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        sdk = (HERE.parent / "module/maplesu_boot_sdk.c").read_text(encoding="utf-8")
        l3 = (HERE.parent / "module/maplesu_l3.c").read_text(encoding="utf-8")
        functions = [definition(l3, name) for name in
                     ("maplesu_root_subject", "maplesu_root_seccomp_disable")]
        # Before the fix, the authenticated gateway has no L3 handover at all.
        functions += [definition(l3, "maplesu_sdk_root_handover") or
                      "static int maplesu_sdk_root_handover(void) { return 0; }"]
        functions += [definition(sdk, name) for name in
                      ("zero", "private_cred", "reply", "sdk_execve")]
        if not all(functions):
            raise AssertionError("The harness requires production gateway definitions")
        compiler = host_compiler()
        if compiler is None:
            raise AssertionError("host compiler missing")
        cls.temporary = tempfile.TemporaryDirectory(prefix="maplesu-sdk-root-")
        cls.addClassCleanup(cls.temporary.cleanup)
        temporary = Path(cls.temporary.name)
        (temporary / "boot_sdk_root_handover.inc").write_text(
            "\n".join(functions), encoding="utf-8")
        cls.binary = temporary / "handover.exe"
        subprocess.run([compiler, "-std=c11", "-Wall", "-Wextra", "-Werror", "-Wno-unused-function",
            "-I" + str(temporary), "-I" + str(HERE.parent / "module"),
            "-I" + str(ROOT / "runtime/ultra/authorization"),
            HERE / "boot_sdk_root_handover_test.c", "-o", cls.binary], check=True)

    def run_case(self, case):
        result = subprocess.run([self.binary, case], capture_output=True, text=True, timeout=10)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_authenticated_sdk_root_receives_private_l3_credential(self):
        for case in ("root", "jit-commit"):
            with self.subTest(case=case):
                self.run_case(case)

    def test_queries_and_untrusted_or_partial_root_calls_do_not_gain_marker(self):
        for case in ("query", "uid-only", "euid-only", "bad-key", "not-ready"):
            with self.subTest(case=case):
                self.run_case(case)

    def test_existing_and_inherited_markers_avoid_another_handover_allocation(self):
        for case in ("marked", "inherit", "setgroups"):
            with self.subTest(case=case):
                self.run_case(case)

    def test_credential_failures_report_failure_and_preserve_filter(self):
        for case in ("private-fail", "handover-fail"):
            with self.subTest(case=case):
                self.run_case(case)


if __name__ == "__main__":
    unittest.main()
