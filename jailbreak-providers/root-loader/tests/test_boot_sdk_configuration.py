"""Replay the actual native sampler and CONFIGURE/control functions on an LP64 host."""
from pathlib import Path
import ast
import re
import subprocess
import sys
import tempfile
import unittest
ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT/'jailbreak-providers/dirtyfrag/tests'))
from test_l3_arm import definition, host_compiler

class FallbackSourceTest(unittest.TestCase):
    """Source-only gate checks; these do not compile or run the native fixture."""
    def test_only_clean_zero_hit_inner_capture_can_retry_once(self):
        source = (ROOT/'module-policy/src/boot_sdk_control.cpp').read_text()
        match = re.search(r'if \((request\.status == -61.*?)\) \{(.*?)\n\s*\}', source, re.DOTALL)
        self.assertIsNotNone(match)
        predicate, body = match.groups()
        for original, name in (
            ('request.status', 'status'), ('request.padding[3]', 'stage'),
            ('request.state', 'state'), ('request.padding[2]', 'configured'),
            ('configuration.layout.allocator_noprof', 'kind')):
            predicate = predicate.replace(original, name)
        predicate = ' '.join(predicate.replace('&&', ' and ').replace('!', 'not ').split())
        tree = ast.parse(predicate, mode='eval')
        allowed = (ast.Expression, ast.BoolOp, ast.And, ast.Compare, ast.Eq,
            ast.Name, ast.Load, ast.Constant, ast.UnaryOp, ast.USub, ast.Not,
            ast.BinOp, ast.BitAnd, ast.BitOr)
        for node in ast.walk(tree):
            self.assertIsInstance(node, allowed)
        names = {'status', 'stage', 'state', 'configured', 'kind',
            'MSU_BS_CONFIG_CAPTURE', 'MSU_BOOT_SDK_EMPTY', 'MSU_BOOT_SDK_ALLOCATOR_INNER',
            'MSU_BOOT_SDK_ALLOCATOR_SEGMENTED'}
        self.assertLessEqual({node.id for node in ast.walk(tree) if isinstance(node, ast.Name)}, names)
        expression = compile(tree, '<actual CONFIGURE fallback predicate>', 'eval')
        checked = 0
        for status in (-61, -95, -12, -22, 0):
            for stage in ('CAPTURE', 'ARGUMENTS', 'ALLOCATE', 'PROBE'):
                for state in ('EMPTY', 'READY', 'INITIALIZING', 'FAILED'):
                    for configured in (0, 1):
                        for kind in range(16):
                            values = dict(status=status, stage=stage, state=state,
                                configured=configured, kind=kind, MSU_BS_CONFIG_CAPTURE='CAPTURE',
                                MSU_BOOT_SDK_EMPTY='EMPTY', MSU_BOOT_SDK_ALLOCATOR_INNER=2,
                                MSU_BOOT_SDK_ALLOCATOR_SEGMENTED=8)
                            expected = (status == -61 and stage == 'CAPTURE' and state == 'EMPTY'
                                and configured == 0 and bool(kind & 2) and not bool(kind & 8))
                            with self.subTest(**values):
                                self.assertEqual(bool(eval(expression, {'__builtins__': {}}, values)), expected)
                            checked += 1
        self.assertEqual(checked, 2560)
        self.assertEqual(' '.join(body.split()),
            'configuration.layout.allocator_noprof &= ~MSU_BOOT_SDK_ALLOCATOR_INNER; '
            'transact(MSU_BOOT_SDK_CONFIGURE);')
        self.assertEqual(source.count('transact(MSU_BOOT_SDK_CONFIGURE);'), 2)
        self.assertEqual(source.count('transact(MSU_BOOT_SDK_BOOTSTRAP);'), 1)
        self.assertLess(match.end(), source.index('if (request.status) {', match.end()))
        self.assertLess(source.index('if (request.status) {', match.end()),
            source.index('transact(MSU_BOOT_SDK_BOOTSTRAP);'))

    def test_native_zero_and_duplicate_hits_have_distinct_results(self):
        source = (HERE.parent/'module/maplesu_boot_sdk.c').read_text()
        sampler = definition(source, 'sample_allocator')
        self.assertIsNotNone(sampler)
        zero = sampler.index('if (!sdk_allocator_hits) { error = -61; goto clear; }')
        duplicate = sampler.index('if (sdk_allocator_hits != 1) goto clear;')
        self.assertLess(sampler.index('error = -95;'), zero)
        self.assertLess(sampler.index('unregister_kprobe(observer);'), zero)
        self.assertLess(sampler.index('release((const void *)allocation);'), zero)
        self.assertLess(zero, duplicate)
        self.assertLess(duplicate, sampler.index('sdk_config_stage = MSU_BS_CONFIG_ARGUMENTS;'))

class ConfigurationTest(unittest.TestCase):
    def test_native_sampling_qualifies_and_cleans_up_before_bootstrap(self):
        source = (HERE.parent/'module/maplesu_boot_sdk.c').read_text()
        fixture = HERE/'boot_sdk_configuration_test.c'
        cases = re.findall(r'^\s*\{"([^"\n]+)",', fixture.read_text(), re.MULTILINE)
        self.assertEqual(len(cases), len(set(cases)))
        self.assertGreater(len(cases), 40)
        names = ('sdk_observation_entry', 'msu_boot_sdk_allocator_pre', 'sample_allocator', 'configure', 'msu_boot_sdk_control')
        functions = [definition(source, name) for name in names]
        for name, function in zip(names, functions):
            self.assertIsNotNone(function, name)
        compiler = host_compiler()
        if compiler is None:
            self.skipTest('No host C compiler configured')
        with tempfile.TemporaryDirectory(prefix='sdk-configuration-') as directory:
            path = Path(directory); exe = path/'control'
            (path/'configuration.inc').write_text('\n'.join(functions))
            for page in (4096, 16384, 65536):
                subprocess.run([compiler, '-std=c11','-Wall','-Wextra','-Werror','-Wno-unused-function',
                    '-fsanitize=address,undefined','-fno-omit-frame-pointer', '-DMSU_BS_PAGE_BYTES=' + str(page),
                    '-I'+str(path),'-I'+str(HERE.parent/'module'), fixture,'-o',exe],check=True)
                for case in cases:
                    with self.subTest(case=case, page=page):
                        result = subprocess.run([exe,case],check=True,capture_output=True,text=True)
                        self.assertIn('PASS '+case+';', result.stdout)

if __name__ == '__main__': unittest.main()
