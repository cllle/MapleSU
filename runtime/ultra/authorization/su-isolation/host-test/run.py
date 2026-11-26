import argparse
import hashlib
import json
from pathlib import Path
import subprocess

HERE = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description='Replay the isolated historical SU host transaction cases.')
parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[5])
parser.add_argument('--inputs', type=Path, default=HERE.parent)
args = parser.parse_args()
BASE = args.inputs.resolve()
HERE = BASE / 'host-test'
REPO = args.root.resolve()
command = ['docker', 'run', '--rm', '--pull=never', '--network', 'none',
           '--tmpfs', '/data:exec', '--tmpfs', '/tmp:exec',
           '-v', f'{BASE.as_posix()}:/work:ro',
           '-v', f'{(REPO / "runtime/maplesu-sdk/include").as_posix()}:/sdk:ro',
           '-v', f'{(BASE / "header/module_err_def.h").as_posix()}:/sdk/module_err_def.h:ro',
           '--entrypoint', '/bin/bash', 'ghcr.io/ylarod/ddk-min:android15-6.6-20260828',
           '-c', 'g++ -std=c++17 -Wall -Wextra -Werror -I/sdk -I/work/source '
                 '/work/host-test/test.cpp -o /tmp/test && /tmp/test']
result = subprocess.run(command, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=60)
(HERE / 'execution.log').write_text(result.stdout + result.stderr)
cases = [line.removeprefix('PASS ') for line in result.stdout.splitlines() if line.startswith('PASS ')]
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
inputs = list((BASE / 'source').glob('*')) + [BASE / 'host-test/test.cpp', Path(__file__).resolve(),
    BASE / 'header/module_err_def.h']
report = {'schema': 'maplesu-su-isolation-host-v1', 'executed': True,
          'passed': result.returncode == 0 and len(cases) == 20,
          'hostOnly': True, 'deviceAcceptance': False, 'exitCode': result.returncode,
          'candidateArchiveSha256': sha(BASE / 'candidate/libkernel_module_kit_static.a'),
          'inputSha256': {str(p): sha(p) for p in inputs},
          'cases': [{'name': name, 'passed': True, 'executed': True} for name in cases],
          'limitations': ['SELinux xattrs shimmed', 'kernel read/write use isolated host table fixture',
                         'exec payload is host /bin/true; no root elevation claimed',
                         'kernel hook and Android runtime require device acceptance'],
          'command': command, 'executionLogSha256': sha(HERE / 'execution.log')}
(HERE / 'results.json').write_text(json.dumps(report, indent=2) + '\n')
print(result.stdout + result.stderr)
print('report', HERE / 'results.json')
raise SystemExit(0 if report['passed'] else 1)
