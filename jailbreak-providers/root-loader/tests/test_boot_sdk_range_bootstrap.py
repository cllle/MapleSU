"""Execute the real arena bootstrap with distinct nine/thirteen-argument seams."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(ROOT / "jailbreak-providers/dirtyfrag/tests"))
from test_l3_arm import definition, host_compiler


class BootSdkRangeBootstrapTest(unittest.TestCase):
    def test_native_and_legacy_bootstrap_keep_the_selected_allocator_abi(self):
        source = Path(os.environ.get("MAPLESU_BOOT_SDK_SOURCE",
            HERE.parent / "module/maplesu_boot_sdk.c")).read_text(encoding="utf-8")
        functions = [definition(source, name) for name in ("zero", "request_v2_init", "bootstrap")]
        self.assertTrue(all(functions))
        compiler = host_compiler()
        self.assertIsNotNone(compiler)
        with tempfile.TemporaryDirectory(prefix="sdk-range-bootstrap-") as directory:
            folder = Path(directory)
            (folder / "boot_sdk_v2_bootstrap.inc").write_text("\n".join(functions), encoding="utf-8")
            for native in (0, 1):
                for environment in (0, 1):
                    for page in (4096, 16384, 65536):
                        binary = folder / "bootstrap"
                        flags = ["-DMSU_BOOT_SDK_NATIVE=1"] if native else []
                        subprocess.run([compiler, "-std=c11",
                            "-Wall", "-Wextra", "-Werror", "-Wno-unused-function", *flags,
                            "-DMSU_BS_STATIC_BOOTSTRAP=0", "-DMSU_INIT_BRIDGE_CFI_LEGACY=0",
                            "-DMSU_BS_ENVIRONMENT=" + str(environment), "-DMSU_BS_PAGE_BYTES=" + str(page),
                            "-I" + str(folder), "-I" + str(HERE.parent / "module"),
                            "-I" + str(ROOT / "runtime/ultra/authorization"),
                            HERE / "boot_sdk_v2_bootstrap_test.c", "-o", binary], check=True)
                        for kind in range(16):
                            with self.subTest(native=native, environment=environment, page=page, kind=kind):
                                result = subprocess.run([binary, str(kind)], check=True,
                                    capture_output=True, text=True, timeout=10)
                                self.assertIn('"failures":0', result.stdout)


if __name__ == "__main__":
    unittest.main()
