"""Run the real recovery writer and authorization gate together on the host JVM."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import android_jar, jdk_tool, sdk_dir

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/specgen'))
from specgen.product import cached_jar


def declaration(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    for end in range(opening + 1, len(source)):
        if source[end] == '{': depth += 1
        if source[end] == '}': depth -= 1
        if depth == 0: return source[start:end + 1]
    raise ValueError(signature)


p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--out', required=True, type=Path)
a = p.parse_args()
a.out.mkdir(parents=True, exist_ok=True)
runtime = ROOT / 'manager/src/main/java/dev/maplesu/manager/runtime'
jailbreak = runtime.parent / 'jailbreak'
provider = runtime / 'NativeSuDirectoryProvider.kt'
control = runtime / 'authorization/SuperuserControl.kt'
sources = [runtime / 'SuGate.kt', runtime / 'CarrierProver.kt', jailbreak / 'JailbreakStatus.kt',
    ROOT / 'manager/src/test/java/dev/maplesu/manager/runtime/HookRecoveryAuthorityTest.kt',
    jailbreak / 'JailbreakSelinuxBaseline.kt',
    ROOT / 'manager/src/test/java/dev/maplesu/manager/jailbreak/JailbreakSelinuxBaselineTest.kt']
fixtures = {
    'claims.kt': 'package dev.maplesu.manager.runtime\nimport dev.maplesu.manager.jailbreak.*\n' +
        '\n'.join(declaration(provider.read_text(encoding='utf-8'), sig) for sig in
            ['internal data class ClaimResult(', 'internal interface ClaimSink', 'internal fun confirmHookCarrier(']),
    'control.kt': 'package dev.maplesu.manager.runtime.authorization\nimport dev.maplesu.manager.runtime.SuGate\nimport dev.maplesu.manager.jailbreak.*\nobject SuperuserControl {\n' +
        declaration(control.read_text(encoding='utf-8'), 'internal fun hookGate(') + '\n}',
    'jailbreak.kt': '''package dev.maplesu.manager.jailbreak
import android.content.Context
import java.io.File
object DirtyfragAttempt { fun readBootId():String?=null;fun hookEvidence(context:Context)=File(".") }
object JailbreakDiagnostics { fun record(a:File,b:File,c:JailbreakStatus,d:String){} }
object HookEvidence {
 const val VERDICT_OK="OK"
 class Evidence(val verdict:String="OK")
 fun read(file:File):Evidence?=null
 fun refusalToken(value:Evidence):String?=null
 fun bindingRefusal(value:Evidence,boot:String,attempt:String):String?=null
}
object JailbreakRequest { const val BACKEND_HOOK="hook";const val BACKEND_KSU="ksu" }
fun jailbreakErrorCode(value:String)=value
fun carrierBindsAttempt(status:JailbreakStatus):Boolean = status.carrier?.let {
 it.attemptId==status.attemptId && it.bootId==status.bootId } ?: true
''',
    'native.kt': '''package dev.maplesu.manager.runtime
import java.io.File
object NativeSu {
 const val FALLBACK_NAME="su"
 data class Result(val code:Int,val out:String)
 fun executeScript(gate:SuGate.Gate,script:String,file:File)=Result(126,"")
}
''',
}
generated = []
for name, text in fixtures.items():
    file = a.out / name
    file.write_text(text, encoding='utf-8')
    generated.append(file)
deps = [cached_jar(*x) for x in [('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.20'),
    ('org.jetbrains', 'annotations', '13.0'), ('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.11.0'),
    ('org.jetbrains.kotlinx', 'kotlinx-serialization-json-jvm', '1.11.0'),
    ('org.jetbrains.kotlinx', 'kotlinx-serialization-core-jvm', '1.11.0'),
    ('org.json', 'json', '20240303'), ('junit', 'junit', '4.13.2'), ('org.hamcrest', 'hamcrest-core', '1.3')]]
android = android_jar()
if not android.is_file():
    sdk = sdk_dir()
    if sdk is not None: android = next((sdk / 'platforms').glob('android-*/android.jar'))
java = jdk_tool()
compiler = cached_jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.4.20')
classes = a.out / 'classes'
cp = os.pathsep.join(map(str, [classes, *deps, android]))
commands = [[java, '-Xmx512m', '-cp', os.pathsep.join(map(str, [compiler, *deps])),
    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-nowarn',
    '-jvm-target', '21', '-classpath', cp, '-d', classes, *sources, *generated],
    [java, '-cp', cp, 'org.junit.runner.JUnitCore', 'dev.maplesu.manager.runtime.HookRecoveryAuthorityTest',
     'dev.maplesu.manager.jailbreak.JailbreakSelinuxBaselineTest']]
logs = []
for command in commands:
    r = subprocess.run(list(map(str, command)), cwd=ROOT, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=55)
    logs.append(r.stdout + r.stderr)
    if r.returncode: break
report = dict(passed=r.returncode == 0, exitCode=r.returncode, output='\n'.join(logs),
    scope='Production status, gate, prover and extracted recovery/authorization functions; OS boundaries are fixtures',
    sources={str(f.relative_to(ROOT)): hashlib.sha256(f.read_bytes()).hexdigest() for f in [*sources, provider, control]})
(a.out / 'result.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
print(report['output'])
raise SystemExit(r.returncode)
