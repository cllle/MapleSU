"""Keep the live allocator decoder compatible with the fixed 5.10 C dialect."""
from pathlib import Path
import subprocess
import sys
import unittest

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(ROOT / 'jailbreak-providers/dirtyfrag/tests'))
from test_l3_arm import host_compiler


class BootSdkGnu89Test(unittest.TestCase):
    def test_live_allocator_compiles_with_the_kernel_c_dialect(self):
        compiler = host_compiler()
        target = ['--target=aarch64-linux-gnu'] if 'clang' in compiler.name else []
        result = subprocess.run([compiler, *target, '-std=gnu89', '-Wall', '-Wextra',
            '-Werror', '-Wno-unused-function', '-fsyntax-only',
            '-I' + str(HERE.parent / 'module'), '-x', 'c', '-'],
            input='#include "boot_sdk_protocol.h"\n#include "boot_sdk_allocator.h"\n',
            capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == '__main__':
    unittest.main()
