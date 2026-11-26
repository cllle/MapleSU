from pathlib import Path
import argparse,subprocess,sys,hashlib,json
REPO=Path(__file__).resolve().parents[5]
parser=argparse.ArgumentParser(description='Replay the pinned historical stat members; no installation.')
parser.add_argument('--root',type=Path,default=REPO)
parser.add_argument('--inputs',type=Path,default=Path(__file__).resolve().parent)
parser.add_argument('--base',type=Path,required=True,help='Pinned SU isolation candidate archive')
parser.add_argument('--ndk',type=Path)
args=parser.parse_args()
ROOT=args.root.resolve();P=args.inputs.resolve()
sys.path.insert(0,str(REPO/'tools'))
from native_toolchain import ndk_home,ndk_bin,tool
TOOLS=ndk_bin(ndk_home(args.ndk))
SUFFIX='.exe' if sys.platform=='win32' else ''
sys.path.insert(0,str(REPO/'scripts'))
from verify_sdk_member_diff import parse_ar
def run(name,*args):subprocess.run([str(tool(TOOLS,name+SUFFIX)),*map(str,args)],check=True)
original=ROOT/'runtime/maplesu-sdk/lib/arm64-v8a/libkernel_module_kit_static.a'
rows=parse_ar(str(original))['members']; data=original.read_bytes()
old='@_ZN15default_modules18module_su_redirect9PatchBase16get_fake_su_pathEv'
copy='@_ZN13kernel_module13export_symbol12copy_to_userEPN6asmjit5v1_163a649AssemblerER7KModErrNS3_3GpXES8_m'
changes=[]
for n in [84,85]:
 bc=P/f'm{n:03}.original.bc'
 run('clang','--target=aarch64-linux-android21','-S','-emit-llvm',bc,'-o',P/f'm{n:03}.original.ll')
 text=(P/f'm{n:03}.original.ll').read_text()
 start=text.index('define hidden noundef i64 @_ZN15default_modules18module_su_redirect')
 end=text.index('\n}\n',start)+3
 body=text[start:end]
 assert body.count(old)==1 and body.count(copy)==1
 body=body.replace(old,f'@maplesu_stat_uid_template_{n}')
 body=body.replace(copy,f'@maplesu_emit_uid_stat_copy_{n}')
 body=body.replace('i64 noundef %307) #27','i64 noundef %307, ptr noundef %0) #27')
 if n==85:
  assert 'store i32 32, ptr %327' in body and '@_ZN6asmjit5v1_163a644regsL2x4E)' in body
  body=body.replace('store i32 32, ptr %327','store i32 8, ptr %327')
  line=next(x for x in body.splitlines() if '%335 = invoke' in x)
  assert '@_ZN6asmjit5v1_163a644regsL2x4E,' in line
  body=body.replace(line,line.replace('@_ZN6asmjit5v1_163a644regsL2x4E,','@_ZN6asmjit5v1_163a644regsL2x1E,'))
  assert 'store i32 8, ptr %327' in body
  final_line=next(x for x in body.splitlines() if '%335 = invoke' in x)
  assert '@_ZN6asmjit5v1_163a644regsL2x1E,' in final_line and '@_ZN6asmjit5v1_163a644regsL2x4E,' not in final_line
 text=text[:start]+body+text[end:]
 text+=f'\ndeclare void @maplesu_stat_uid_template_{n}(ptr sret(%"class.std::__ndk1::basic_string") align 8, ptr)\ndeclare void @maplesu_emit_uid_stat_copy_{n}(ptr, ptr, ptr, ptr, i64, ptr)\n'
 ll=P/f'm{n:03}.patched.ll';ll.write_text(text)
 run('clang','--target=aarch64-linux-android21','-Oz','-fPIC','-c',ll,'-o',P/f'm{n:03}.ir.o')
flags=['--target=aarch64-linux-android21','-std=c++17','-Oz','-fPIC','-fvisibility=hidden','-fexceptions','-fno-rtti','-Wno-return-type-c-linkage','-I',ROOT/'runtime/maplesu-sdk/include']
for n in [84,85]:
 run('clang++',*flags,f'-Dmaplesu_stat_uid_template=maplesu_stat_uid_template_{n}',f'-Dmaplesu_emit_uid_stat_copy=maplesu_emit_uid_stat_copy_{n}','-c',P/'stat_uid_path.cpp','-o',P/f'stat_uid_path_{n}.o')
 run('ld.lld','-r',P/f'm{n:03}.ir.o',P/f'stat_uid_path_{n}.o','-o',P/rows[n]['name'])
base=args.base.resolve()
assert hashlib.sha256(base.read_bytes()).hexdigest()=='5d36cbad542f588999e52d292e96c028d977ad8a65c6e25ecdb1052e7e61b825'
out=P/'libkernel_module_kit_static.a';out.write_bytes(base.read_bytes())
run('llvm-ar','rD',out,P/rows[84]['name'],P/rows[85]['name']);run('llvm-ar','sD',out)
after=parse_ar(str(out))['members'];before=parse_ar(str(base))['members']
changed=[b['ord'] for a,b in zip(before,after) if a['sha256']!=b['sha256']]
assert changed==[84,85],changed
report={'archiveSha256':hashlib.sha256(out.read_bytes()).hexdigest(),'baseSha256':hashlib.sha256(base.read_bytes()).hexdigest(),'changedMembers':changed,'unchangedAccessMembers':[86,87],'deviceAcceptance':False}
(P/'build.json').write_text(json.dumps(report,indent=2)+'\n');print(report)
