"""Exercise Ultra's real shell shim with a recording CLI, without device access."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHIM = "manager/src/main/assets/maplesu/modules/sepolicy.sh"
CALLER = "manager/src/main/java/dev/maplesu/manager/modules/LiteScriptModuleBackend.kt"
DEPLOYMENT = "manager/src/main/java/dev/maplesu/manager/modules/ModuleToolDeployment.kt"
BOOT = "manager/src/main/assets/maplesu/modules/boot.sh"
script = r'''
import os
from pathlib import Path
import subprocess

root = Path('/output')
source = Path('/source/manager/src/main/assets/maplesu/modules/sepolicy.sh').read_text()
assert source.count('loader=/data/adb/maplesu/bin/maplesu-policy') == 1
source = source.replace('#!/system/bin/sh', '#!/bin/sh', 1)
source = source.replace('loader=/data/adb/maplesu/bin/maplesu-policy', 'loader=/output/loader')
source = source.replace('/data/adb/maplesu/.module-rules-XXXXXX', '/output/.module-rules-XXXXXX')
(root/'shim').write_text(source)
(root/'shim').chmod(0o755)
(root/'sepolicy').symlink_to('shim')
(root/'magiskpolicy').symlink_to('shim')
deployment = Path('/source/manager/src/main/java/dev/maplesu/manager/modules/ModuleToolDeployment.kt').read_text()
marker = "<<'MAPLESU_KSUD_SHIM'\n"
assert deployment.count(marker) == 1
ksud = deployment.split(marker)[1].split('\nMAPLESU_KSUD_SHIM')[0]
ksud = ksud.replace("${'$'}", '$').replace('#!/system/bin/sh', '#!/bin/sh', 1)
assert ksud.count('exec /data/adb/maplesu/bin/sepolicy') == 1
(root/'ksud').write_text(ksud.replace('exec /data/adb/maplesu/bin/sepolicy', 'exec /output/sepolicy'))
(root/'ksud').chmod(0o755)
(root/'loader').write_text('#!/bin/sh\nprintf "%s\\n" "$@" > /output/arguments\nfor rule; do :; done\ncat "$rule" > /output/rules\nexit "$FIXTURE_CODE"\n')
(root/'loader').chmod(0o755)
rule_text = 'allow zygote zygote process execmem'
(root/'module rules').write_text(rule_text + '\n')

tests = 0
routes = [(['sepolicy'], command) for command in ('apply', 'patch', '--live', 'check')]
routes += [(['magiskpolicy'], '--live')]
routes += [(['ksud', 'sepolicy'], command) for command in ('apply', 'patch', 'check')]
forms = [[rule_text], ['/output/module rules']]
forms += [[option, '/output/module rules'] for option in ('--rules', '--file', '--apply', '-f')]
for route, command in routes:
    for form in forms:
        for status in (0, 75, 77):
            result = subprocess.run(['/output/' + route[0]] + route[1:] + [command] + form,
                env=dict(os.environ, FIXTURE_CODE=str(status)), capture_output=True, text=True)
            assert result.returncode == status, (route, command, form, result.stdout, result.stderr)
            arguments = (root/'arguments').read_text().splitlines()
            expected = ['module-check', '--rules'] if command == 'check' else ['module-apply', '--require-boot-policy', '--rules']
            assert arguments[:-1] == expected, arguments
            assert (root/'rules').read_text().strip() == rule_text, (route, form)
            assert not Path(arguments[-1]).exists(), 'temporary rules leaked'
            tests += 1
for route, command in routes:
    for form in ([], ['--rules'], ['--file', '/output/missing'], ['--apply'], ['-f', '/output/missing']):
        (root/'arguments').unlink(missing_ok=True)
        result = subprocess.run(['/output/' + route[0]] + route[1:] + [command] + form,
            env=dict(os.environ, FIXTURE_CODE='0'), capture_output=True, text=True)
        assert result.returncode == 2, (route, command, form, result.stderr)
        assert not (root/'arguments').exists(), 'invalid request reached loader'
        assert not list(root.glob('.module-rules-*')), 'invalid request leaked temporary rules'
        tests += 1
caller = Path('/source/manager/src/main/java/dev/maplesu/manager/modules/LiteScriptModuleBackend.kt').read_text()
assert 'ModuleToolDeployment.policyPath())} module-apply --require-boot-policy $arguments' in caller
boot = Path('/source/manager/src/main/assets/maplesu/modules/boot.sh').read_text()
assert boot.count('policies() {\n') == 1
body = boot.split('policies() {\n')[1].split('\n}\n')[0]
(root/'maplesu-policy').symlink_to('loader')
modules = root/'modules'
for name in ('active', 'disabled', 'linked'):
    (modules/name).mkdir(parents=True)
(modules/'active/sepolicy.rule').write_text(rule_text + '\n')
(modules/'disabled/sepolicy.rule').write_text(rule_text + '\n')
(modules/'disabled/disable').touch()
(modules/'linked/sepolicy.rule').symlink_to(modules/'active/sepolicy.rule')
(root/'boot-policy-route').write_text('#!/bin/sh\nmodules=/output/modules\nbin=/output\n'
    'active() { [ ! -e "$1/disable" ]; }\nrecord() { printf "%s %s\\n" "$@" > /output/record; }\n'
    'policies() {\n' + body + '\n}\npolicies\n')
for status in (0, 75, 77):
    result = subprocess.run(['sh', '/output/boot-policy-route'],
        env=dict(os.environ, FIXTURE_CODE=str(status)), capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    assert (root/'arguments').read_text().splitlines() == [
        'module-apply', '--require-boot-policy', '--rules', '/output/modules/active/sepolicy.rule']
    assert (root/'record').read_text().strip() == f'{status} policy'
    tests += 1
print(f'PASS {tests} real sepolicy/magiskpolicy/ksud and boot policy routes, rule content, errors and cleanup; Kotlin call-site inspected')
'''

with tempfile.TemporaryDirectory(prefix="maplesu-ultra-policy-callers-") as output:
    Path(output, "test.py").write_text(script, encoding="utf-8")
    command = ["docker", "run", "--rm", "--pull", "never", "--network", "none",
               "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
               "--mount", f"type=bind,source={ROOT},target=/source,readonly",
               "--mount", f"type=bind,source={output},target=/output",
               "ghcr.io/ylarod/ddk-min:android15-6.6-20260828", "python3", "/output/test.py"]
    result = subprocess.run(command, capture_output=True, text=True, timeout=30)
report = dict(recordedAt=datetime.now(timezone.utc).isoformat(), exitCode=result.returncode,
    stdout=result.stdout, stderr=result.stderr,
    sourceSha256={n: hashlib.sha256((ROOT/n).read_bytes()).hexdigest() for n in (SHIM, CALLER, DEPLOYMENT, BOOT)},
    scope=__doc__ + " Real boot policies() runs with fixture active/record boundaries. Kotlin routing is inspected, not instrumented on Android.", deviceAccess=False)
(ROOT/"work/ultra-module-policy-20261107/ultra-caller-routing.json").write_text(json.dumps(report, indent=2)+"\n")
print(result.stdout or result.stderr)
raise SystemExit(result.returncode)
