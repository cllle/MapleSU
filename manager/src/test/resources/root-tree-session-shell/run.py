"""Compile actual RootModuleTree/session sources and execute generated scripts in Docker."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument("--java", required=True)
parser.add_argument("--compiler-classpath", required=True)
parser.add_argument("--stdlib", required=True)
parser.add_argument("--output", required=True, type=Path)
args = parser.parse_args()
fixture = Path(__file__).resolve().parent
repo = Path(os.environ.get("MAPLESU_REMOVAL_SOURCE", fixture.parents[4])).resolve()
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=False)
sources = [repo / "manager/src/main/java/dev/maplesu/manager/modules/RootModuleTree.kt",
           repo / "manager/src/main/java/dev/maplesu/manager/modules/webui/ModuleWebUiSessions.kt",
           fixture / "Boundaries.kt", fixture / "RootTreeSessionShellCases.kt"]
digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
inputs = [{"path": str(p), "sha256": digest(p)} for p in sources]
(out / "INPUTS.json").write_text(json.dumps(inputs, indent=2), encoding="utf-8")
commands = []

def run(name, argv):
    argfile = out / (name + ".args")
    argfile.write_text("\n".join('"' + str(a).replace("\\", "/").replace('"', '\\"') + '"'
                                 for a in argv), encoding="utf-8")
    proc = subprocess.run([args.java, "@" + str(argfile)], cwd=repo, capture_output=True,
                          text=True, encoding="utf-8", errors="replace", timeout=180)
    (out / (name + ".log")).write_text(proc.stdout + proc.stderr, encoding="utf-8")
    commands.append({"name": name, "args": str(argfile), "exitCode": proc.returncode})
    print(name, proc.returncode, proc.stdout, proc.stderr[-2500:], flush=True)
    if proc.returncode:
        raise SystemExit(proc.returncode)

try:
    run("compile", ["-Xmx512m", "-cp", args.compiler_classpath,
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-nowarn",
        "-jvm-target", "21", "-classpath", args.stdlib, "-d", out / "classes", *sources])
    run("cases", ["-cp", str(out / "classes") + ";" + args.stdlib,
        "dev.maplesu.manager.modules.RootTreeSessionShellCasesKt", out])
finally:
    stable = all(digest(Path(i["path"])) == i["sha256"] for i in inputs)
    result = {"passed": len(commands) == 2 and all(c["exitCode"] == 0 for c in commands) and stable,
              "sourceInputsStable": stable, "commands": commands,
              "scope": "Two complete production Kotlin TUs, actual generated shell and Linux inode rename/removal; explicit compile-only ModuleScriptRunner/ModuleTree boundaries; module-sync uses host sync -f plus one injected failure; BusyBox flock entry delegates to real host flock; module-initrc is an explicit successful platform substitute recording calls, not initrc execution. No Android process, real Root transport, APK, device, power-loss or cross-process revocation."}
    (out / "RESULT.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
