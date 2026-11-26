"""Actual helper layout/control and native capability regression; no device operations."""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / 'jailbreak-providers/root-loader'
SOURCES = ['module-policy/src/boot_sdk_layout.cpp', 'module-policy/src/boot_sdk_layout.h',
    'module-policy/src/boot_sdk_control.cpp', 'jailbreak-providers/root-loader/boot_sdk_abi.h',
    'jailbreak-providers/root-loader/module/maplesu_boot_sdk.c',
    'jailbreak-providers/root-loader/tests/boot_sdk_configuration_test.c',
    'jailbreak-providers/root-loader/tests/test_boot_sdk_configuration.py',
    'runtime/tests/boot_sdk_allocator_test.cpp', 'runtime/tests/boot_sdk_facts_test.cpp',
    'runtime/ultra/policy/kernel_layout.h', 'scripts/tests/test-boot-sdk-allocator.py']
CASES = ['facts', 'new-only13', 'new-only13-zero', 'old-only13', 'new-observer',
         'old-observer', 'new-outer9', 'new-inner9-zero', 'old-inner9-zero', 'baseline']


def identities():
    return {n: dict(bytes=(ROOT/n).stat().st_size, sha256=hashlib.sha256((ROOT/n).read_bytes()).hexdigest())
            for n in SOURCES}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    before = identities()
    for name in SOURCES:
        path = output/'source'/name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes((ROOT/name).read_bytes())
    sys.path.insert(0, str(ROOT/'jailbreak-providers/dirtyfrag/tests'))
    from test_l3_arm import definition
    source = (NATIVE/'module/maplesu_boot_sdk.c').read_text()
    (output/'configuration.inc').write_text('\n'.join(definition(source,n) for n in
        ['sdk_observation_entry','msu_boot_sdk_allocator_pre','sample_allocator','configure','msu_boot_sdk_control']))
    (output/'boot_sdk_control.inc').write_text('int boot_sdk_control(' +
        (ROOT/'module-policy/src/boot_sdk_control.cpp').read_text().split('int boot_sdk_control(',1)[1])
    native_fixture = (NATIVE/'tests/boot_sdk_configuration_test.c').read_text().split('struct test_case',1)[0]
    native_fixture = native_fixture.replace('task+2048&&', 'task+probe_layout.task_cred&&')
    native_fixture = native_fixture.replace('static uptr lookup_name(const char *name){',
        'static int missing_outer;\nstatic uptr lookup_name(const char *name){\n'
        ' if(missing_outer && (!strcmp(name,"__vmalloc_node_range")||!strcmp(name,"__vmalloc_node_range_noprof")))return 0;')
    native_fixture += '''
void fixture_native_setup(int no_outer) {
 task=kernel_base+0x4000;credential=kernel_base+0x8000;native_return=kernel_base+0xa000;
 uptr args[]={MSU_BS_PAGE_BYTES,1,kernel_base+0x10000000,kernel_base+0x70000000,0xcc0,0x0068000000000703,0,0xffffffffu};
 memcpy(native_args,args,sizeof args);release=fixture_release;missing_outer=no_outer;
 msu_sdk_store(header+16,0x10000000,8);msu_sdk_store(header+56,0x644d5241,4);
}
int fixture_native_transact(void *wire,int fail_capture) {
 struct msu_boot_sdk_configuration *request=wire;
 if(request->reply.command==MSU_BOOT_SDK_CONFIGURE){probe_layout=request->layout;capture_mode=fail_capture?1:0;}
 int error=(int)msu_boot_sdk_control((uptr)wire);
 if(fixture_fault||sample_live||probe_active||active_probe||observer||control_busy||sdk_allocator_owner||sdk_allocator_hits||sdk_allocator_segmented)return -1;
 return error;
}
'''
    (output/'coupled-native.c').write_text(native_fixture)
    docker = ['docker','run','--rm','--pull','never','--network','none','--cap-drop','ALL',
        '--security-opt','no-new-privileges','--mount',f'type=bind,source={ROOT.as_posix()},target=/source,readonly',
        '--mount',f'type=bind,source={output.as_posix()},target=/output',
        'sha256:51159bc935c75a282fc65bc87e02f16bd098d1cdf55dc558c4fdd0fcf7a132e2']
    steps = []

    def run(name, command):
        done = subprocess.run(docker+command, capture_output=True, text=True, timeout=90)
        (output/(name+'.log')).write_text(done.stdout+done.stderr, encoding='utf-8')
        steps.append(dict(name=name, command=docker+command, exitCode=done.returncode))
        if done.returncode:
            raise RuntimeError(name+': '+done.stdout+done.stderr)
        return done.stdout

    def cases(binary, names):
        return ['python3','-c', 'import subprocess,json\nrows=[]\nfor case in '+repr(names)+':\n'
            ' r=subprocess.run(['+repr(binary)+',case],capture_output=True,text=True)\n'
            ' rows.append(dict(case=case,exitCode=r.returncode,stdout=r.stdout,stderr=r.stderr))\n'
            'print(json.dumps(rows))\nraise SystemExit(any(r["exitCode"] for r in rows))']

    error = None
    try:
        run('helper-compile', ['c++','-std=c++17','-Wall','-Wextra','-Werror','-fsanitize=address,undefined',
            '-fno-omit-frame-pointer','-I/output','/source/module-policy/src/boot_sdk_layout.cpp',
            '/source/runtime/tests/boot_sdk_allocator_test.cpp','-o','/output/helper-test'])
        run('helper-run', cases('/output/helper-test', CASES))
        native_cases = re.findall(r'^\s*\{"([^"\n]+)",',
            (NATIVE/'tests/boot_sdk_configuration_test.c').read_text(), re.M)
        for page in (4096,16384,65536):
            run(f'native-{page}-compile', ['cc','-std=c11','-Wall','-Wextra','-Werror','-Wno-unused-function',
                '-fsanitize=address,undefined','-fno-omit-frame-pointer',f'-DMSU_BS_PAGE_BYTES={page}',
                '-I/output','-I/source/jailbreak-providers/root-loader/module',
                '/source/jailbreak-providers/root-loader/tests/boot_sdk_configuration_test.c','-o','/output/native-test'])
            run(f'native-{page}-run', cases('/output/native-test', native_cases))
            run(f'coupled-{page}-native-compile', ['cc','-std=c11','-Wall','-Wextra','-Werror','-Wno-unused-function',
                '-fsanitize=address,undefined','-fno-omit-frame-pointer',f'-DMSU_BS_PAGE_BYTES={page}',
                '-I/output','-I/source/jailbreak-providers/root-loader/module','-c','/output/coupled-native.c','-o','/output/coupled-native.o'])
            run(f'coupled-{page}-compile', ['c++','-std=c++17','-Wall','-Wextra','-Werror','-fsanitize=address,undefined',
                '-fno-omit-frame-pointer',f'-DMAPLESU_COUPLED_PAGE={page}u','-I/output',
                '/source/module-policy/src/boot_sdk_layout.cpp','/source/runtime/tests/boot_sdk_allocator_test.cpp',
                '/output/coupled-native.o','-o','/output/coupled-test'])
            run(f'coupled-{page}-run', cases('/output/coupled-test', CASES[1:-1]))
        spec = importlib.util.spec_from_file_location('sdk_allocator_fallback', NATIVE/'tests/test_boot_sdk_configuration.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        stream = io.StringIO()
        result = unittest.TextTestRunner(stream=stream).run(
            unittest.defaultTestLoader.loadTestsFromTestCase(module.FallbackSourceTest))
        (output/'fallback.log').write_text(stream.getvalue())
        if not result.wasSuccessful():
            raise RuntimeError('fallback predicate regression failed')
    except Exception as failure:
        error = str(failure)
    report = dict(at=datetime.now(timezone.utc).isoformat(), passed=error is None and before==identities(),
        error=error, sources=before, sourcesUnchanged=before==identities(), steps=steps,
        helperCaseGroups=len(CASES), pageBytes=[4096,16384,65536],
        formalArtifactBuilt=False, deviceTest=False,
        scope='Typed BTF and syscall ports; unchanged actual helper and native control functions execute under ASAN/UBSAN')
    (output/'RESULTS.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(passed=report['passed'],error=error,steps=len(steps))))
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
