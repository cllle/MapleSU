"""Prepare a reversible SU UID isolation integration overlay; never apply it."""
from pathlib import Path
import argparse, copy, hashlib, importlib.util, io, json, re, shutil, struct, subprocess, sys
from datetime import datetime, timezone
from elftools.elf.elffile import ELFFile
REPO = Path(__file__).resolve().parents[4]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--historical-root', type=Path, required=True, help='Frozen repository with runtime/pro and runtime/upstream-pro')
parser.add_argument('--input-root', type=Path, required=True, help='Relocated historical external inputs named by the recipe')
parser.add_argument('--inputs', type=Path, default=Path(__file__).resolve().parent, help='SU isolation stage with source-frozen-1449 and host results')
parser.add_argument('--configuration', type=Path, default=Path(__file__).with_name('integration-inputs.json'))
parser.add_argument('--helper-dir', type=Path)
parser.add_argument('--ndk', type=Path)
args = parser.parse_args()
ROOT = args.historical_root.resolve()
HERE = args.inputs.resolve()
input_root = args.input_root.resolve()
def input_path(value):
    path = Path(value)
    return path.resolve() if path.is_absolute() else (input_root / path).resolve()
OUT = HERE / 'integration-final'
OVERLAY = OUT / 'overlay'
WRITTEN = set()
HELPER_DIR = args.helper_dir.resolve() if args.helper_dir else input_root / 'helper-su-isolation'
sys.path.insert(0, str(REPO / 'tools'))
from native_toolchain import ndk_home, ndk_bin, tool
TOOLS = ndk_bin(ndk_home(args.ndk))
SUFFIX = '.exe' if sys.platform == 'win32' else ''
sys.path.insert(0, str(REPO / 'scripts'))
from verify_sdk_member_diff import parse_ar, compare_members
from policy_backend_identity import describe_backend
sha = lambda b: hashlib.sha256(b).hexdigest()
digest = lambda p: sha(p.read_bytes())
configuration=json.loads(args.configuration.read_bytes())
for named,expected in configuration.get('inputSha256',{}).items():
    actual=input_path(named)
    assert actual.is_file(), 'Missing historical input: '+str(actual)
    assert digest(actual)==expected, 'Historical input SHA-256 mismatch: '+str(actual)

def put(name, data):
    p = OVERLAY / name
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_bytes(data)
    WRITTEN.add(p)
    return p
def jsonput(name, value): return put(name, (json.dumps(value, indent=2) + '\n').encode())
def run(name, *args):
    r = subprocess.run([str(tool(TOOLS,name+SUFFIX)), *map(str,args)],capture_output=True,text=True,timeout=60)
    assert r.returncode == 0, r.stdout + r.stderr
spec=importlib.util.spec_from_file_location('upgrade',ROOT/'scripts/repair-pro-environment-upgrade.py')
upgrade=importlib.util.module_from_spec(spec); spec.loader.exec_module(upgrade)
OUT.mkdir(exist_ok=True)
prod=ROOT/'runtime/upstream-pro'
manifest=json.loads((prod/'SDK-RELEASE.json').read_bytes())
basepath=prod/'lib/arm64-v8a/libkernel_module_kit_static.a'
base=basepath.read_bytes()
assert sha(base)=='f015f15ceb91e81f09c0a3b70c4a1c09042c2926ad91380499692a3733c2b4c5'
core=input_path(configuration.get('coreArchive',HERE/'candidate/libkernel_module_kit_static.a'))
assert digest(core)==configuration.get('coreArchiveSha256','5d36cbad542f588999e52d292e96c028d977ad8a65c6e25ecdb1052e7e61b825')
helper_candidate=input_path(configuration.get('helperCandidate',HELPER_DIR/'candidate-v2'))
entry=json.loads((helper_candidate/'build.json').read_bytes())
helper=(helper_candidate/'candidate-helper.elf').read_bytes()
assert sha(helper)==entry['helperSha256']
backendpath=input_path(configuration.get('backend',entry['backend']['path'])); backend=backendpath.read_bytes()
assert digest(backendpath)==entry['backend']['sha256']
before=parse_ar(str(basepath)); row=before['members'][52]
oldobj=base[row['body_offset']:row['body_offset']+row['size']]
oldhelper=ELFFile(io.BytesIO(oldobj)).get_section_by_name('.data').data()
assert sha(oldhelper)==entry['baselineHelperSha256']
slot=manifest['helper']['keySlotOffset']; assert helper[slot:slot+4096]==oldhelper[slot:slot+4096]
run('clang','--target=aarch64-linux-android21','-S','-emit-llvm','-x','ir',ROOT/'runtime/pro/upgrade/sdk-m052.bc','-o',OUT/'m052.input.ll')
ir=upgrade.patch_stream((OUT/'m052.input.ll').read_text(),'_ZN10skroot_env32unsafe_install_autorun_bootstrapEv',131,148,151,10,1215088,78,145,144,(147,146),'u:object_r:kernel:s0')
ir=upgrade.keep_bootstrap_until_publish(ir)
payload=re.compile(r'^(@_ZN10skroot_envL27autorun_bootstrap_file_dataE = internal global )[^\n]+, align 8$',re.M)
assert len(payload.findall(ir))==1
def installer(blob):
    encoded=''.join('\\%02X'%byte for byte in blob)
    text=payload.sub(lambda m:m[1]+f'[{len(blob)} x i8] c"'+encoded+'", align 8',ir)
    return re.sub(r'\b1215088\b',str(len(blob)),text)
for label,blob in [('baseline',oldhelper),('patched',helper)]:
    src=OUT/f'm052.{label}.ll'; obj=OUT/f'm052.{label}.o'; src.write_text(installer(blob))
    run('clang','--target=aarch64-linux-android21','-Oz','-fPIC','-c',src,'-o',obj)
assert upgrade.object_contract((OUT/'m052.baseline.o').read_bytes())==upgrade.object_contract(oldobj)
member52=OUT/row['name']; shutil.copyfile(OUT/'m052.patched.o',member52)
assert ELFFile(io.BytesIO(member52.read_bytes())).get_section_by_name('.data').data()==helper
image=ELFFile(io.BytesIO(helper)); idx,_=max(((i,s) for i,s in enumerate(image.iter_segments()) if s['p_type']=='PT_LOAD'),key=lambda x:x[1]['p_vaddr'])
ph=image['e_phoff']+idx*image['e_phentsize']; gate=manifest['moduleControl']; at=gate['gateFileOffset']; size=gate['gateBytes']; branch=0xA6D68
header=f'#pragma once\n#define MAPLESU_GATE_FILE_OFFSET {at}\n#define MAPLESU_GATE_BRANCH_OFFSET {branch}\n#define MAPLESU_GATE_PHDR_OFFSET {ph}\n'
for name,data in [('bytes',helper[at:at+size]),('branch',helper[branch:branch+4]),('phdr',helper[ph:ph+56])]:
    header+='static constexpr unsigned char maplesu_gate_'+name+'[] = {'+','.join(map(str,data))+'};\n'
(OUT/'module_gate_identity.h').write_text(header)
member255=OUT/before['members'][255]['name']
run('clang++','--target=aarch64-linux-android21','-std=c++17','-Oz','-fPIC','-fexceptions','-ffunction-sections','-fdata-sections','-I',prod/'include','-I',OUT,'-c',ROOT/'runtime/pro/modules/module_control.cpp','-o',member255)
archive=put('runtime/upstream-pro/lib/arm64-v8a/libkernel_module_kit_static.a',core.read_bytes())
run('llvm-ar','rD',archive,member52,member255);run('llvm-ar','sD',archive)
for ordinal,path in configuration.get('extraMembers',{}).items():
    source=input_path(path); target=OUT/before['members'][int(ordinal)]['name']
    shutil.copyfile(source,target);run('llvm-ar','rD',archive,target)
if configuration.get('extraMembers'): run('llvm-ar','sD',archive)
after=parse_ar(str(archive)); delta=compare_members(before,after)
assert [x['ord'] for x in delta['changed']]==configuration.get('expectedChangedOrdinals',[48,52,81,83,255])
assert not delta['added'] and not delta['removed'] and not delta['orderErrors']
put('runtime/upstream-pro/helper/autorun_bootstrap',helper)
backendname='runtime/upstream-pro/backend/'+entry['backend']['fileName']
put(backendname,backend)
sources={}
for p in (HERE/'source-frozen-1449').iterdir():
    if p.name=='build_candidate.py': dest='runtime/pro/authorization/su-isolation/build_candidate.py'
    elif p.name=='patch_do_execve.cpp': dest='runtime/upstream-pro/sdk/src/patch/patch_do_execve.cpp'
    else: dest='runtime/pro/authorization/'+p.name
    put(dest,p.read_bytes()); sources[dest]=digest(p)
    if p.suffix in ('.cpp','.h') and p.name!='patch_do_execve.cpp':
        dup='runtime/upstream-pro/sdk/src/module/su_redirect_tu/'+p.name
        put(dup,p.read_bytes());sources[dup]=digest(p)
for name in ('helper_su_backend.cpp','helper_su_loader.c','helper_su_entry.S','build_helper.py','CMakeLists.txt'):
    dest='runtime/pro/boot/su-isolation/'+name
    put(dest,(HELPER_DIR/name).read_bytes()); sources[dest]=digest(HELPER_DIR/name)
for name in ('test.cpp','run.py'):
    dest='runtime/pro/authorization/su-isolation/host-test/'+name
    put(dest,(HERE/'host-test'/name).read_bytes());sources[dest]=digest(HERE/'host-test'/name)
for dest,path in configuration.get('extraSources',{}).items():
    p=input_path(path);put(dest,p.read_bytes());sources[dest]=digest(p)
for name in ('runtime/pro/modules/module_control.cpp','runtime/pro/upgrade/sdk-m052.bc',
             'scripts/repair-pro-environment-upgrade.py','scripts/sync-pro-sdk-identity.py'):
    sources[name]=digest(ROOT/name)
put('runtime/pro/modules/module_gate_identity.h',header.encode());sources['runtime/pro/modules/module_gate_identity.h']=sha(header.encode())
err_header=input_path(configuration.get('errorHeader',prod/'include/module_err_def.h'))
sources['runtime/upstream-pro/include/module_err_def.h']=digest(err_header)
put('runtime/upstream-pro/include/module_err_def.h',err_header.read_bytes())
dest='runtime/pro/authorization/su-isolation/integrate_sdk.py';put(dest,Path(__file__).read_bytes());sources[dest]=digest(Path(__file__))
if args.configuration.is_file():
    dest='runtime/pro/authorization/su-isolation/integration-inputs.json'
    put(dest,args.configuration.read_bytes());sources[dest]=digest(args.configuration)
earliest=parse_ar(str(prod/'maintenance/baseline/libkernel_module_kit_static.a')); count=earliest['member_count']
full=compare_members(earliest,dict(after,members=after['members'][:count],member_count=count))
full['added']=[dict(ord=m['ord'],name=m['name'],candidateSha256=m['sha256']) for m in after['members'][count:]]
diffpath='runtime/upstream-pro/SU-AUTH-MEMBER-DIFF.json'
jsonput(diffpath,dict(schemaVersion=1,baselineArchiveSha256=earliest['file_sha256'],archiveSha256=digest(archive),totalMembers=after['member_count'],**full))
physical='work/panic-implementation-20261114/final-build/retention-physical-baseline-r25.json'
host_path='work/panic-implementation-20261114/final-build/su-isolation-host-r25.json'
host_written=jsonput(host_path,json.loads((HERE/'host-test/results.json').read_bytes()))
repair=dict(schemaVersion=1,deviceAcceptance=False,baselineArchiveSha256=sha(base),baselineHelperSha256=sha(oldhelper),deliveredArchiveSha256=digest(archive),deliveredHelperSha256=sha(helper),policyBackendSha256=sha(backend),sources=sources,memberDiff=delta,m52EmbeddedHelperSha256=sha(helper),baselinePhysicalReceipt=dict(path=physical,sha256=digest(ROOT/physical)),dacContract=json.loads((HERE/'candidate/build.json').read_bytes())['contract'],hostExecution=dict(path=host_path,sha256=digest(host_written)))
release=copy.deepcopy(manifest)
release.update(appVersion='5.2.0',versionCode=50200,sdkVersion='5.2.0',sdkBuildId='maplesu-pro-5.2.0-su-uid-isolation-20261114',generatedUtc=datetime.now(timezone.utc).isoformat(),suUidIsolationRepair=repair)
release['archive'].update(sha256=digest(archive),size=archive.stat().st_size)
for name in ('helper','bootstrap'): release[name].update(sha256=sha(helper),buildId=entry['helperBuildId'],size=len(helper))
release['helper']['normalizedSha256']=entry['helperNormalizedSha256']
release['moduleControl'].update(helperSha256=sha(helper),helperNormalizedSha256=entry['helperNormalizedSha256'],helperBuildId=entry['helperBuildId'],helperBytes=len(helper),archiveSha256=digest(archive),memberSha256=after['members'][255]['sha256'])
for r in release['repairs']:
    if r['memberOrdinal'] in {x['ord'] for x in delta['changed']}: r['sha256']=after['members'][r['memberOrdinal']]['sha256']
if configuration.get('hotloadRepair'):
    host_result=input_path(configuration['hotloadRepair']['hostResultPath'])
    assert digest(host_result)==configuration['hotloadRepair']['hostResultSha256']
    release['bootstrapReadinessRepair']=dict(configuration['hotloadRepair'],hostResultPath=str(host_result))
    release['bootstrapReadinessRepair']['memberSha256']=after['members'][53]['sha256']
    release['bootstrapReadinessRepair']['errorName']='ERR_BOOTSTRAP_NOT_READY'
    release['bootstrapReadinessRepair']['errorCode']=-21002
release['memberDiff'].update(sha256=digest(OVERLAY/diffpath),changed=len(full['changed']),added=len(full['added']),removed=len(full['removed']))
release['environmentPolicyBoot'].update(backend=dict(describe_backend(backendpath),path=backendname),entryHelperSha256=sha(helper),identityMatched=True,deviceAcceptance=False,complete=False)
jsonput('runtime/upstream-pro/SDK-RELEASE.json',release)
provenance=json.loads((prod/'PROVENANCE.json').read_bytes())
provenance.update(sdkVersion='5.2.0',sdkBuildId=release['sdkBuildId'],archive=release['archive'],helper=release['helper'],pairedPolicyBackend=release['environmentPolicyBoot']['backend'],memberDiff=release['memberDiff'],suUidIsolationRepair=repair)
if configuration.get('hotloadRepair'): provenance['bootstrapReadinessRepair']=release['bootstrapReadinessRepair']
jsonput('runtime/upstream-pro/PROVENANCE.json',provenance)
identity=(prod/'SDK-IDENTITY.cmake').read_text()
values=dict(MAPLESU_PRO_SDK_BUILD_ID=release['sdkBuildId'],MAPLESU_PRO_SDK_VERSION='5.2.0',MAPLESU_PRO_SDK_SHA256=digest(archive),MAPLESU_PRO_HELPER_SHA256=entry['helperNormalizedSha256'],MAPLESU_PRO_HELPER_BUILD_ID=entry['helperBuildId'],MAPLESU_APP_VERSION='5.2.0',MAPLESU_PRO_HELPER_SIZE=len(helper))
for name,value in values.items():
    replacement=str(value) if isinstance(value,int) else '"'+value+'"'
    identity,n=re.subn(r'set\('+name+r' [^\n]+\)',f'set({name} {replacement})',identity);assert n==1,name
put('runtime/upstream-pro/SDK-IDENTITY.cmake',identity.encode())
native=json.loads((ROOT/'release/native-inputs.json').read_bytes())
rows={r['path']:r for r in native['files']}
for p in sorted(WRITTEN):
    if p.is_file():
        name=p.relative_to(OVERLAY).as_posix()
        if name.startswith('work/'): continue
        rows[name]=dict(path=name,bytes=p.stat().st_size,sha256=digest(p))
native.update(sdk_build_id=release['sdkBuildId'],sdk_archive_sha256=digest(archive),helper_sha256=sha(helper),policy_backend_sha256=sha(backend),files=sorted(rows.values(),key=lambda r:r['path']))
jsonput('release/native-inputs.json',native)
writes=[]
for p in sorted(WRITTEN):
    if not p.is_file(): continue
    name=p.relative_to(OVERLAY).as_posix(); target=ROOT/name
    writes.append(dict(path=name,source=str(p),beforeSha256=digest(target) if target.is_file() else None,afterSha256=digest(p),bytes=p.stat().st_size))
plan=dict(schemaVersion=1,candidateOnly=True,applied=False,archiveSha256=digest(archive),helperSha256=sha(helper),backendSha256=sha(backend),writes=writes,rollback=dict(preserveBeforeApply=True,restoreOnlyMatchingAfterSha256=True),memberDiff=delta)
(OUT/'import-plan.json').write_text(json.dumps(plan,indent=2)+'\n')
(OUT/'scope.json').write_text(json.dumps(dict(inputSha256=sources,isolationSourcePaths=sorted(sources),artifactIdentity=dict(archiveSha256=digest(archive),helperSha256=sha(helper),backendSha256=sha(backend))),indent=2)+'\n')
host_report=HERE/'host-test/results.json';host_data=json.loads(host_report.read_bytes())
assert host_data['passed'] and host_data['executed'] and not host_data['deviceAcceptance']
compiled_names={'su_uid_copies.cpp','su_uid_copies.h','su_exec_context.cpp','su_auth_transaction.cpp','module_err_def.h'}
host_paths=[n for n in sources if Path(n).name in compiled_names]
assert {sources[n] for n in host_paths} <= set(host_data['inputSha256'].values())
cases=[];required=[]
for i,c in enumerate(host_data['cases']):
    role=('unauthorized-filesystem-denied' if c['name']=='ordinary UID DAC isolation' else
          'authorized-filesystem-execute-permitted' if c['name']=='authorized UID DAC access' else 'host-transaction-check')
    case=dict(id=f'host-{i:02}',name=c['name'],role=role,passed=c['passed'],executed=c['executed'])
    cases.append(case)
    if role!='host-transaction-check': required.append(case['id'])
execution=dict(schema='maplesu-su-isolation-execution-v1',passed=True,executed=True,
    executionEnvironment='host',deviceAcceptance=False,deviceAcceptancePending=True,
    scope='Actual host C++ filesystem and authorization transaction branches; /bin/true payload, shimmed SELinux, kernel table fixture. Android credential elevation and kernel hooks pending device acceptance.',
    artifactIdentity=dict(archiveSha256=digest(archive),helperSha256=sha(helper),backendSha256=sha(backend)),
    inputSha256=sources,hostExecutedSourcePaths=sorted(host_paths),cases=cases,requiredCases=required,
    executedReports=[dict(path=str(host_report),sha256=digest(host_report))])
(OUT/'execution-wrapper.json').write_text(json.dumps(execution,indent=2)+'\n')
assert digest(basepath)==sha(base),'Canonical archive changed during preparation'
print(json.dumps(dict(passed=True,candidateOnly=True,archiveSha256=digest(archive),helperSha256=sha(helper),backendSha256=sha(backend),changedOrdinals=[r['ord'] for r in delta['changed']],writes=len(writes),plan=str(OUT/'import-plan.json')),indent=2))
