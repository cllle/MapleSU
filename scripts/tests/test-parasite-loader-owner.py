"""Execute the current native loader with real root shell entries in an ephemeral container."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--out', type=Path, required=True)
out = parser.parse_args().out.resolve()
out.mkdir(parents=True, exist_ok=False)
path = ROOT / 'runtime/src/parasite/parasite_loader.cpp'
original = path.read_bytes()
source = original.decode('utf-8').replace('#include <android/log.h>', r'''#include <stdarg.h>
#include <stdio.h>
#define ANDROID_LOG_WARN 5
int __android_log_print(int, const char*, const char* fmt, ...) {
 va_list a; va_start(a,fmt); int r=vfprintf(stderr,fmt,a); va_end(a); fputc('\n',stderr); return r;
}''')
source = source.replace('__attribute__((constructor))', '')
source = source.replace('/system/bin/id', '/usr/bin/id').replace('/system/bin/sh', '/bin/sh')
source = source.replace('/data/adb/maplesu/recovery/parasite-start.sh', '/tmp/maplesu-owner/start.sh')
source = source.replace('/data/adb/maplesu/lite_hidden', '/tmp/maplesu-owner/hidden')
start = source.index('constexpr const char* kSuCandidates[] = {')
end = source.index('};', start) + 2
source = source[:start] + 'constexpr const char* kSuCandidates[] = {"/tmp/maplesu-owner/su0", "/tmp/maplesu-owner/su1", "/tmp/maplesu-owner/su2"};' + source[end:]
source = source.replace('constexpr int kCommandTimeoutMs = 30000;', 'constexpr int kCommandTimeoutMs = 250;')
source += '\nint main() { start_thread(nullptr); return 0; }\n'
(out / 'loader.cpp').write_text(source, encoding='utf-8')
harness = r'''
from pathlib import Path
import json, subprocess, time
r=Path('/tmp/maplesu-owner'); r.mkdir(); (r/'hidden').write_text('1\n')
c='/opt/ddk/clang/clang-r510928'
p=subprocess.run([c+'/bin/clang++','-stdlib=libc++','-std=c++17','-pthread','-Wall','-Wextra','-Werror',
 '-Wl,-rpath,'+c+'/lib/x86_64-unknown-linux-gnu','/fixture/loader.cpp','-o',str(r/'test')],capture_output=True,text=True)
Path('/fixture/compile.log').write_text(p.stdout+p.stderr)
if p.returncode: print(p.stdout+p.stderr); raise SystemExit(p.returncode)
cases=[]
for name,body,first,want,starts in [
 ('root_script_failure','exit 11\n','root',1,1),
 ('root_script_timeout','sleep 5\n','root',1,1),
 ('nonroot_entry_continues','exit 0\n','denied',2,1),
 ('missing_entry_continues','exit 0\n','missing',1,1),
 ('root_script_success','exit 0\n','root',1,1)]:
 for f in ['attempts','starts']: (r/f).unlink(missing_ok=True)
 (r/'start.sh').write_text('#!/bin/sh\necho start >> '+str(r/'starts')+'\n'+body)
 for i in range(3):
  su=r/('su'+str(i)); su.unlink(missing_ok=True)
  if i==0 and first=='missing': continue
  code='exit 13\n' if i==0 and first=='denied' else 'exec /bin/sh "$@"\n'
  su.write_text('#!/bin/sh\necho su'+str(i)+' >> '+str(r/'attempts')+'\n'+code); su.chmod(0o755)
 begin=time.monotonic(); p=subprocess.run([str(r/'test')],capture_output=True,text=True,timeout=3)
 elapsed=time.monotonic()-begin
 attempts=(r/'attempts').read_text().splitlines() if (r/'attempts').exists() else []
 actual_starts=(r/'starts').read_text().splitlines() if (r/'starts').exists() else []
 original_error=('status=11 ' in p.stderr if name=='root_script_failure' else 'status=124 ' in p.stderr if name=='root_script_timeout' else True)
 ok=p.returncode==0 and len(attempts)==want and len(actual_starts)==starts and original_error
 cases.append(dict(name=name,ok=ok,attempts=attempts,starts=len(actual_starts),expectedAttempts=want,seconds=elapsed,stderr=p.stderr))
print(json.dumps(cases,indent=2)); Path('/fixture/CASES.json').write_text(json.dumps(cases,indent=2)+'\n')
raise SystemExit(any(not c['ok'] for c in cases))
'''
(out / 'run.py').write_text(harness, encoding='utf-8')
argv = ['docker','run','--rm','--network','none','--mount',f'type=bind,source={out},target=/fixture',
        '--entrypoint','python3','ghcr.io/ylarod/ddk-min:android15-6.6-20260828','/fixture/run.py']
p = subprocess.run(argv, cwd=ROOT, capture_output=True, timeout=60)
(out / 'execution.log').write_bytes(p.stdout + p.stderr)
receipt = {'source': {'path': str(path), 'sha256': hashlib.sha256(original).hexdigest(), 'bytes': len(original)},
           'sourceUnchanged': path.read_bytes() == original, 'argv': argv, 'exitCode': p.returncode,
           'fixtureChanges': ['android logging shim', 'explicit test root paths and three real shell entry wrappers', 'constructor invoked explicitly', 'deadline scaled from 30000ms to 250ms'],
           'deviceOperations': 0, 'largeBuild': False}
(out / 'RESULT.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
print((p.stdout+p.stderr).decode('utf-8',errors='replace'))
raise SystemExit(p.returncode)
