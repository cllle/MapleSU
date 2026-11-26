#!/usr/bin/env python3
"""Source-first actual production classifier and actual kernel-shellcode helper."""
import argparse,hashlib,json,subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent
IMAGE='sha256:272ada1e61c525116032246df2a790d1ccec1804b7a9e0b09203c5e650dcb78e'

def identity(p):
    b=p.read_bytes();return {'path':str(p.resolve()),'bytes':len(b),'sha256':hashlib.sha256(b).hexdigest()}

def body(text,signature):
    start=text.index(signature);first=text.index('{',start);depth=1;at=first+1
    while depth:
        if text[at]=='{':depth+=1
        elif text[at]=='}':depth-=1
        at+=1
    return text[start:at]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source-root',required=True,type=Path);p.add_argument('--reference-root',required=True,type=Path);p.add_argument('--out',required=True,type=Path)
    a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);inputs=[]
    source=a.source_root/'runtime/maplesu-sdk/sdk/src/kernel_offsets/task_struct.cpp';inputs.append(identity(source));text=source.read_text(encoding='utf-8-sig')
    checked='KModErr resolve_current_task_model(' in text
    constants=''
    for signature in ['constexpr uint32_t kSpEl0SysReg','constexpr uint64_t kThreadSizes[]','constexpr uint64_t kKernelAddressFloor']:
        start=text.index(signature);constants+=text[start:text.index(';',start)+1]+'\n'
    exact=constants+body(text,'static KModErr get_kernel_shellcode_u64_result(')+'\nnamespace kernel_module {\n'
    if checked:exact+=body(text,'static KModErr resolve_current_stack_size(')+'\n'+body(text,'KModErr get_task_struct_stack_size(')+'\n'+body(text,'KModErr resolve_current_task_model(')+'\n'
    exact+=body(text,'bool is_CONFIG_THREAD_INFO_IN_TASK(')+'\n'+body(text,'bool is_CURRENT_FROM_SP_EL0_THREAD_INFO(')+'\n}\n'
    if checked:
        wrappers='''namespace resolver_test {
inline KModErr resolve(kernel_module::CurrentTaskModel& m){return kernel_module::resolve_current_task_model(m);}
inline void embedded_success(uint64_t task,uint64_t sp){reply(KModErr::OK,task,1);reply(KModErr::OK,sp,2);}
inline void thread_success(uint64_t task,uint64_t sp){embedded_success(task,sp);}
}
'''
    else:
        wrappers='''namespace resolver_test {
inline KModErr resolve(kernel_module::CurrentTaskModel& m){
    if(kernel_module::is_CONFIG_THREAD_INFO_IN_TASK()){m={kernel_module::CurrentTaskSource::sp_el0_task,0};return KModErr::OK;}
    if(kernel_module::is_CURRENT_FROM_SP_EL0_THREAD_INFO()){m={kernel_module::CurrentTaskSource::sp_el0_thread_info,0};return KModErr::OK;}
    m={kernel_module::CurrentTaskSource::stack_thread_info,0x4000};return KModErr::OK;
}
inline void embedded_success(uint64_t task,uint64_t sp){reply(KModErr::OK,task,1);reply(KModErr::OK,task,1);reply(KModErr::OK,sp,2);}
inline void thread_success(uint64_t task,uint64_t sp){embedded_success(task,sp);}
}
'''
    (out/'actual_resolver.inl').write_text(exact+wrappers,encoding='utf-8')
    fragment={'bytes':len(exact.encode()),'sha256':hashlib.sha256(exact.encode()).hexdigest(),'baselineAdapter':not checked}
    for name in ['resolver_fixture.h','test_resolver.cpp']:
        src=HERE/name;inputs.append(identity(src));(out/name).write_bytes(src.read_bytes())
    src=a.reference_root/'runtime/maplesu-sdk/include/module_err_def.h';inputs.append(identity(src));(out/'module_err_def.h').write_bytes(src.read_bytes());inputs.append(identity(Path(__file__)))
    docker=['docker','run','--rm','--pull','never','--network','none','--read-only','--tmpfs','/tmp:rw,size=32m','--mount',f'type=bind,source={out},target=/work','-w','/work',IMAGE]
    commands=[('compile',docker+['g++','-std=c++20','-O1','-g','-I/work','test_resolver.cpp','-fsanitize=address,undefined','-fno-omit-frame-pointer','-no-pie','-o','resolver-test'])]
    for mode in ['embedded-retry','thread-info-retry','exact-first-error','exact-second-error','unknown-stack','embedded','thread-info','invalid-sp','stack16','stack32','stack64','stack128','stack-missing-end','stack-bad-alias','stack-bad-span','stack-lookup-error','stack-marker-error','stack-invalid-address','stack-new-markers']:commands.append((mode,docker+['./resolver-test',mode]))
    rows=[]
    for name,argv in commands:
        r=subprocess.run(argv,capture_output=True,text=True,encoding='utf-8',errors='replace');output=r.stdout+r.stderr;print(output,end='');(out/(name+'.log')).write_text(output,encoding='utf-8')
        rows.append({'name':name,'argv':argv,'exit':r.returncode,'sanitizer':'runtime error:' in output or 'ERROR: AddressSanitizer:' in output})
        if name=='compile' and r.returncode:break
    stable=all(identity(Path(x['path']))==x for x in inputs);passed=stable and len(rows)==len(commands) and all(x['exit']==0 and not x['sanitizer'] for x in rows)
    (out/'receipt.json').write_text(json.dumps({'schema':'current-model-actual-classifier/1','inputs':inputs,'inputsUnchanged':stable,'image':IMAGE,'checkedSource':checked,'actualProductionFragment':fragment,'commands':rows,'passed':passed,'scope':'Exact production classifier bodies + exact helper with inert assembler/executor and synthetic register/symbol inputs. Baseline adapter exposes old predicate model with fixed16K fallback, not a replacement algorithm. Upper-envelope checks are not target VA-width validation; low1234 is not VA-hole coverage. Raw SP_EL0 heuristic is not proven vendor structure or combined Huawei behavior. No real legacy/device qualification.'},indent=2)+'\n',encoding='utf-8');raise SystemExit(0 if passed else 1)
if __name__=='__main__':main()
