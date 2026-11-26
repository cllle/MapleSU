#!/usr/bin/env python3
"""Actual standalone stack-size body, with old emitted nofault loop interpreted."""
import argparse,hashlib,json,subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent
IMAGE='sha256:272ada1e61c525116032246df2a790d1ccec1804b7a9e0b09203c5e650dcb78e'

def identity(p):
    b=p.read_bytes();return {'path':str(p.resolve()),'bytes':len(b),'sha256':hashlib.sha256(b).hexdigest()}
def body(text,signature):
    start=text.index(signature);at=text.index('{',start)+1;depth=1
    while depth:
        if text[at]=='{':depth+=1
        elif text[at]=='}':depth-=1
        at+=1
    return text[start:at]
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source-root',required=True,type=Path);p.add_argument('--reference-root',required=True,type=Path);p.add_argument('--out',required=True,type=Path)
    a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);source=a.source_root/'runtime/maplesu-sdk/sdk/src/kernel_offsets/task_struct.cpp';inputs=[identity(source)];text=source.read_text(encoding='utf-8-sig');checked='static KModErr resolve_current_stack_size(' in text
    constants=''
    for signature in ['constexpr uint64_t kThreadSizes[]','constexpr uint64_t kKernelAddressFloor']:
        start=text.index(signature);constants+=text[start:text.index(';',start)+1]+'\n'
    exact=constants
    if not checked:exact+=body(text,'static KModErr get_kernel_shellcode_u64_result(')+'\n'
    exact+='namespace kernel_module {\n'
    if checked:exact+=body(text,'static KModErr resolve_current_stack_size(')+'\n'
    exact+=body(text,'KModErr get_task_struct_stack_size(')+'\n}\n';(out/'actual_stack_size.inl').write_text(exact,encoding='utf-8')
    fragment={'bytes':len(exact.encode()),'sha256':hashlib.sha256(exact.encode()).hexdigest()}
    for name in ['stack_fixture.h','test_stack_size.cpp']:
        src=HERE/name;inputs.append(identity(src));(out/name).write_bytes(src.read_bytes())
    src=a.reference_root/'runtime/maplesu-sdk/include/module_err_def.h';inputs.append(identity(src));(out/'module_err_def.h').write_bytes(src.read_bytes());inputs.append(identity(Path(__file__)))
    docker=['docker','run','--rm','--pull','never','--network','none','--read-only','--tmpfs','/tmp:rw,size=32m','--mount',f'type=bind,source={out},target=/work','-w','/work',IMAGE]
    commands=[('compile',docker+['g++','-std=c++20','-O1','-g','-I/work','test_stack_size.cpp','-fsanitize=address,undefined','-fno-omit-frame-pointer','-no-pie','-o','stack-size-test'])]
    commands += [(mode,docker+['./stack-size-test',mode]) for mode in ['all-readable','first-boundary','stale-probe-cache','helper-error','size16','size32','size64','size128','bad-alias','bad-span','lookup-error']]
    rows=[]
    for name,argv in commands:
        r=subprocess.run(argv,capture_output=True,text=True,encoding='utf-8',errors='replace');output=r.stdout+r.stderr;print(output,end='');(out/(name+'.log')).write_text(output,encoding='utf-8');rows.append({'name':name,'argv':argv,'exit':r.returncode,'sanitizer':'runtime error:' in output or 'ERROR: AddressSanitizer:' in output})
        if name=='compile' and r.returncode:break
    stable=all(identity(Path(x['path']))==x for x in inputs);passed=stable and len(rows)==len(commands) and all(x['exit']==0 and not x['sanitizer'] for x in rows)
    (out/'receipt.json').write_text(json.dumps({'schema':'current-model-actual-stack-size/1','inputs':inputs,'inputsUnchanged':stable,'image':IMAGE,'checkedSource':checked,'actualProductionFragment':fragment,'commands':rows,'passed':passed,'scope':'Exact standalone getter and old exact helper; tiny IR interprets actual baseline nofault branch, not copied stack algorithm. Candidate inputs are synthetic marker/alias spans. Pinned generic linker end=start+THREAD_SIZE evidence does not prove arbitrary vendor span; envelope is not target VA-width validation. No real legacy/device acceptance or whole legacy executor zero-write claim.'},indent=2)+'\n',encoding='utf-8');raise SystemExit(0 if passed else 1)
if __name__=='__main__':main()
