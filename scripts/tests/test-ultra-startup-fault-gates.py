"""Run production startup/identity gates against explicit host-only SDK and Android fixtures."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / "scripts/fixtures/startup-fault-gates"
sys.path.insert(0, str(ROOT / "tools/specgen"))
from specgen.product import cached_jar


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def declaration(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    for end in range(opening + 1, len(source)):
        if source[end] == "{":
            depth += 1
        elif source[end] == "}":
            depth -= 1
        if depth == 0:
            return source[start:end + 1]
    raise ValueError(signature)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--runtime-json", type=Path,
                        help="Optional recorded compiler/dependency closure; default uses java from PATH")
    args = parser.parse_args()
    root = ROOT
    args.out.mkdir(parents=True, exist_ok=True)
    ultra = root / "manager/src/main/java/dev/maplesu/manager/runtime/ultra"
    app = root / "manager/src/main/java/com/maplesu/core/KernelSUApplication.kt"
    boot = root / "manager/src/main/java/dev/maplesu/manager/workmode/boot/ModeBootstrap.kt"
    fixtures = sorted(FIXTURES.glob("*.kt"))
    inputs = [app, boot, boot.parent / "PairedStartupPolicy.kt",
              root / "manager/src/main/java/dev/maplesu/manager/ultrakernel/UltraKernelCredential.kt",
              *[ultra / name for name in ("UltraEnvironmentProbe.kt", "UltraIdentityRecovery.kt",
                  "UltraIdentityResolution.kt", "UltraReinstallPolicy.kt", "UltraStartupWarmup.kt")],
              Path(__file__).resolve(), *fixtures, FIXTURES / "ORIGINAL19-MAP.json"]
    before = {str(path.relative_to(root)): sha(path) for path in inputs}
    (args.out / "source-inputs-before.json").write_text(json.dumps(before, indent=2), encoding="utf-8")
    try:
        recovery = (ultra / "UltraIdentityRecovery.kt").read_text(encoding="utf-8")
        blocked = recovery[recovery.index("internal fun blocked("):].split("\n\n", 1)[0]
        fragments = {
            "Application.kt": '''package com.maplesu.core
import android.content.Context
import android.util.Log
import dev.maplesu.manager.workmode.*
import dev.maplesu.manager.workmode.gating.*
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ensureActive
private const val TAG = "fixture"
class KernelSUApplication : Context() {
 private val ultraRecoveryRequestedAt = AtomicLong(0L)
 private val ultraStartupTriggeredAt = AtomicLong(0L)
 suspend fun runRecovery() = recoverUltraEnvironment()
''' + declaration(app.read_text(encoding="utf-8"), "private suspend fun recoverUltraEnvironment()") + "\n}\n",
            "Recovery.kt": '''package dev.maplesu.manager.runtime.ultra
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import dev.maplesu.manager.compat.ultraPreferences
import dev.maplesu.manager.modules.UltraRootKeyStore
import dev.maplesu.manager.workmode.*
import dev.maplesu.manager.workmode.superuser.LiteImplantQuiesceGate
import dev.maplesu.manager.workmode.ui.WorkModeSwitchSession
internal object UltraIdentityRecovery {
 private const val TAG = "fixture"
''' + blocked + "\n@Synchronized\n" + declaration(recovery, "fun recover(context: Context): String {") + "\n" +
                declaration(recovery, "internal fun uid(client: UltraRuntimeClient)") + '''
 // Broker transport is an explicit external boundary; its returned key still enters real resolution.
 private data class Candidate(val key: String)
 private fun candidate(context: Context, userId: Int, withBuild: Boolean): Candidate? {
  check(!withBuild); Fixture.calls += "broker"; return Fixture.brokerKey?.let(::Candidate)
 }
}
''',
            "ReinstallPairing.kt": "package dev.maplesu.manager.runtime.ultra\n" +
                "import dev.maplesu.manager.ultrakernel.UltraKernelCredential\n" +
                declaration((ultra / "UltraReinstallPolicy.kt").read_text(encoding="utf-8"), "internal object UltraReinstallPairing {") + "\n",
            "Bootstrap.kt": '''package dev.maplesu.manager.workmode.boot
import android.content.Context
import dev.maplesu.manager.workmode.*
import dev.maplesu.manager.ultrakernel.UltraKernelCredential
import java.util.concurrent.atomic.AtomicBoolean
class ModeBootstrapFixture {
 private val started = AtomicBoolean(true)
 private val firstFrameHandled = AtomicBoolean(false)
 // Host scheduling boundary executes synchronously; eligibility/readRunning stay production text.
 private fun startDetectionThread(name: String, block: () -> Unit) = block()
 private fun executePhaseTwo(beforeDetection: () -> Unit) = beforeDetection()
 private fun startupTrace(message: String) {}
''' + declaration(boot.read_text(encoding="utf-8"), "fun onManagerFirstFrame(context: Context)") + "\n}\n",
        }
        sources = [ultra / name for name in ("UltraEnvironmentProbe.kt", "UltraStartupWarmup.kt", "UltraIdentityResolution.kt")]
        sources.extend([
            root / "manager/src/main/java/dev/maplesu/manager/ultrakernel/UltraKernelCredential.kt",
            boot.parent / "PairedStartupPolicy.kt",
        ])
        for name, text in fragments.items():
            path = args.out / name
            path.write_text(text, encoding="utf-8")
            sources.append(path)
        sources.extend(fixtures)
        if args.runtime_json:
            runtime = json.loads(args.runtime_json.read_text(encoding="utf-8"))["runtime"]
            for dependency in runtime["dependencies"]:
                if sha(Path(dependency["path"])) != dependency["sha256"]:
                    raise ValueError(f"DEPENDENCY_CHANGED:{dependency['path']}")
            dependencies = [Path(item["path"]) for item in runtime["dependencies"]]
            compiler_cp = runtime["compilerClasspath"]
            java = Path(runtime["jdk"]) / ("java.exe" if os.name == "nt" else "java")
        else:
            dependencies = [cached_jar(*item) for item in [
                ("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.20"),
                ("org.jetbrains", "annotations", "13.0"),
                ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.11.0"),
            ]]
            compiler = cached_jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.4.20")
            compiler_cp = os.pathsep.join(map(str, [compiler, *dependencies]))
            java = shutil.which("java")
            if java is None:
                raise SystemExit("java not found on PATH; JDK 21 or newer is required")
        junit = cached_jar("junit", "junit", "4.13.2")
        hamcrest = cached_jar("org.hamcrest", "hamcrest-core", "1.3")
        classes = args.out / "classes"
        classes.mkdir(exist_ok=True)
        cp = os.pathsep.join(map(str, [classes, *dependencies, junit, hamcrest]))
        tool_paths = sorted({Path(path) for path in compiler_cp.split(os.pathsep)} |
                            set(dependencies) | {Path(junit), Path(hamcrest), Path(java)})
        tools_before = {str(path): sha(path) for path in tool_paths}
        commands = [
            [java, "-Xmx512m", "-cp", compiler_cp,
             "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-nowarn",
             "-jvm-target", "21", "-classpath", cp, "-d", classes, *sources],
            [java, "-cp", cp, "org.junit.runner.JUnitCore", "dev.maplesu.manager.runtime.ultra.StartupFaultGateTest"],
        ]
        executions = []
        for command in commands:
            argv = list(map(str, command))
            result = subprocess.run(argv, cwd=root, capture_output=True, text=True,
                                    encoding="utf-8", errors="replace", timeout=55)
            executions.append(dict(argv=argv, exitCode=result.returncode, stdout=result.stdout, stderr=result.stderr))
            if result.returncode:
                break
    finally:
        after = {str(path.relative_to(root)): sha(path) if path.exists() else None for path in inputs}
        inputs_unchanged = before == after
        (args.out / "source-inputs.json").write_text(json.dumps(
            dict(before=before, after=after, unchanged=inputs_unchanged), indent=2), encoding="utf-8")
    tools_after = {str(path): sha(path) for path in tool_paths}
    tools_unchanged = tools_before == tools_after
    exit_code = executions[-1]["exitCode"] or (0 if inputs_unchanged and tools_unchanged else 1)
    output = "\n".join(item["stdout"] + item["stderr"] for item in executions)
    if not inputs_unchanged:
        output += "\nSTARTUP_FIXTURE_SOURCE_INPUTS_CHANGED"
    if not tools_unchanged:
        output += "\nSTARTUP_FIXTURE_TOOL_INPUTS_CHANGED"
    report = dict(
        passed=len(executions) == 2 and exit_code == 0,
        exitCode=exit_code, output=output, executions=executions,
        expectedJUnitMethods=19, deviceVerification=False,
        scope="Whole production Probe/Warmup/IdentityResolution/Credential/PairedStartupPolicy; complete unchanged Recovery entry/blocked/uid, ReinstallPairing object, Application recovery method and first-frame method; Android, SDK, I/O and scheduler boundaries are fixtures",
        sourceHashes=before, sourceHashesAfter=after, inputsUnchanged=inputs_unchanged,
        generatedHashes={name: sha(args.out / name) for name in fragments},
        runtimeJson=(dict(path=str(args.runtime_json), sha256=sha(args.runtime_json)) if args.runtime_json else None),
        dependencies=[dict(path=str(p), sha256=sha(p)) for p in [*dependencies, junit, hamcrest]],
        toolHashesBefore=tools_before, toolHashesAfter=tools_after, toolsUnchanged=tools_unchanged,
    )
    (args.out / "result.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(output)
    return exit_code



if __name__ == "__main__":
    raise SystemExit(main())
