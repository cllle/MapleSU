#!/usr/bin/env python3
"""Actual full m072 TU + exact current inline bodies; source-only author delivery."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

HERE=Path(__file__).resolve().parent
IMAGE='sha256:272ada1e61c525116032246df2a790d1ccec1804b7a9e0b09203c5e650dcb78e'
SDK='runtime/maplesu-sdk/sdk/'
INCLUDE='runtime/maplesu-sdk/include/'

def identity(path):
    raw=path.read_bytes()
    return {'path':str(path.resolve()),'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source-root',type=Path,required=True)
    p.add_argument('--reference-root',type=Path,required=True)
    p.add_argument('--out',type=Path,required=True)
    a=p.parse_args(); out=a.out.resolve(); out.mkdir(parents=True,exist_ok=False)
    roots=[a.source_root.resolve(),a.reference_root.resolve()]
    inputs=[]
    def capture(relative,destination,reference=False):
        source=next((r/relative for r in (roots[1:] if reference else roots) if (r/relative).is_file()),None)
        if source is None: raise FileNotFoundError(relative)
        target=out/destination; target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(source.read_bytes()); inputs.append(identity(source)); return target
    for relative,destination in [
        (SDK+'src/patch/patch_current.cpp','patch_current.cpp'),
        (INCLUDE+'module_base_kernel_export_symbol.inl','source_current.inl'),
        (SDK+'include/default_modules_module_root_patch_base.h','include/default_modules_module_root_patch_base.h'),
        (SDK+'include/maplesu_caps_private.h','include/maplesu_caps_private.h'),
        (INCLUDE+'kernel_offsets/cred.h','include/kernel_offsets/cred.h'),
        (INCLUDE+'module_err_def.h','include/module_err_def.h'),
        ('runtime/ultra/authorization/request_protocol.h','include/request_protocol.h')]:
        capture(relative,destination)
    original=capture(SDK+'tools/tests/caps_precheck/test_patch_current.cpp','original_test_patch_current.cpp',True)
    raw=(out/'source_current.inl').read_text(encoding='utf-8-sig')
    checked='inline KModErr get_current_checked(' in raw
    begin=raw.index('inline KModErr get_current_checked(' if checked else 'inline void get_current(')
    end=raw.index('static const std::vector<std::string> copy_from_user_symbol_names',begin)
    exact=raw[begin:end]
    fragment={'sourceLine':raw.count('\n',0,begin)+1,'sourceEndLine':raw.count('\n',0,end)+1,'bytes':len(exact.encode()),'sha256':hashlib.sha256(exact.encode()).hexdigest()}
    wrappers='''\nnamespace current_test {\ninline KModErr emit_task(asmjit::a64::Assembler* a,asmjit::a64::GpX r) { %s }\ninline KModErr emit_info(asmjit::a64::Assembler* a,asmjit::a64::GpX r) { %s }\n}\n''' % (
        'return kernel_module::export_symbol::get_current_checked(a,r);' if checked else 'kernel_module::export_symbol::get_current(a,r); return KModErr::OK;',
        'return kernel_module::export_symbol::get_current_thread_info_checked(a,r);' if checked else 'kernel_module::export_symbol::get_current_thread_info(a,r); return KModErr::OK;')
    (out/'include/actual_current.inl').write_text('namespace kernel_module::export_symbol {\nusing namespace asmjit; using namespace asmjit::a64;\n'+exact+'\n}\n'+wrappers,encoding='utf-8')
    for name in ['kernel_module_kit_umbrella.h','append_checks.inc']:
        source=HERE/name; inputs.append(identity(source)); (out/('include/'+name if name.endswith('.h') else name)).write_bytes(source.read_bytes())
    inputs.append(identity(Path(__file__)))
    test=original.read_text(encoding='utf-8-sig')
    old='void reset() { fixture = {}; current_test::builds = 0;'
    assert test.count(old)==1
    test=test.replace(old,'void reset() { fixture = {}; current_test::reset_emitter(); current_test::builds = 0;')
    marker='    std::printf("patch-current-caps:'
    assert test.count(marker)==1
    test=test.replace(marker,(HERE/'append_checks.inc').read_text(encoding='utf-8-sig')+'\n'+marker)
    (out/'test_patch_current.cpp').write_text(test,encoding='utf-8')
    (out/'include/maplesu_request_private.h').write_text('''#pragma once
#include "module_err_def.h"
#include "request_protocol.h"
namespace kernel_module {
bool maplesu_request_owner_mode();
KModErr maplesu_request_credential_channel(bool&,uint64_t&);
KModErr maplesu_request_owner_root(); KModErr maplesu_request_owner_task_state();
KModErr maplesu_request_owner_caps(const maplesu_caps_v3&);
KModErr maplesu_execute_legacy_credential_frame(const uint8_t*,uint32_t,uint64_t&);
}
''',encoding='utf-8')
    docker=['docker','run','--rm','--pull','never','--network','none','--read-only','--tmpfs','/tmp:rw,size=32m','--mount',f'type=bind,source={out},target=/work','-w','/work',IMAGE]
    commands=[('compile',docker+['g++','-std=c++20','-O1','-g','-I/work/include','test_patch_current.cpp','patch_current.cpp','-Wl,--wrap=sched_getaffinity','-fsanitize=address,undefined','-fno-omit-frame-pointer','-no-pie','-o','current-model-test']),('test',docker+['./current-model-test'])]
    rows=[]
    for name,argv in commands:
        r=subprocess.run(argv,capture_output=True,text=True,encoding='utf-8',errors='replace')
        output=r.stdout+r.stderr; (out/(name+'.log')).write_text(output,encoding='utf-8'); print(output,end='')
        rows.append({'name':name,'argv':argv,'exit':r.returncode,'sanitizer':'runtime error:' in output or 'ERROR: AddressSanitizer:' in output})
        if r.returncode: break
    stable=all(identity(Path(i['path']))==i for i in inputs)
    passed=stable and len(rows)==len(commands) and all(r['exit']==0 and not r['sanitizer'] for r in rows)
    receipt={'schema':'current-model-actual-emitter/1','inputs':inputs,'inputsUnchanged':stable,'commands':rows,'image':IMAGE,'checkedSource':checked,'actualInlineFragment':fragment,'passed':passed,'scope':'Actual full m072 + exact inline current bodies; original caps assertions preserved. Injected synthetic models/sizes/addresses, including Huawei-adjusted emission only; no real resolver-plus-Huawei, legacy target span, VA-width or device acceptance. Zero-write assertions cover emitted target credential stores, not whole legacy executor receipt writes.'}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
    raise SystemExit(0 if passed else 1)

if __name__=='__main__': main()
