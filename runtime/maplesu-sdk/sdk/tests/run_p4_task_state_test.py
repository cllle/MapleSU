#!/usr/bin/env python3
"""Run the current production P4 task-state body and macro branches on host seams."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import subprocess

HERE = Path(__file__).resolve().parent


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def extract_function(source: str, signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=HERE.parents[3])
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    root = args.repo_root.resolve()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    source_path = root / "runtime/ultra/identity/kernel/manager_task_state.c"
    protocol_path = root / "runtime/ultra/authorization/request_protocol.h"
    fixture_path = HERE / "p4_task_state_fixture.cpp"
    source_bytes = source_path.read_bytes()
    protocol_bytes = protocol_path.read_bytes()
    fixture_bytes = fixture_path.read_bytes()
    source = source_bytes.decode("utf-8").replace("\r\n", "\n")
    protocol = protocol_bytes.decode("utf-8").replace("\r\n", "\n")
    fixture = fixture_bytes.decode("utf-8").replace("\r\n", "\n")
    exact = extract_function(source, "int msu_task_state_release_current(")
    macro_end = source.index("\n#define MSU_LAYOUT_TYPE")
    macro_start = source.rfind("#ifdef CONFIG_SECCOMP\n", 0, macro_end)
    macros = source[macro_start:macro_end].strip()
    if not macros or "#define MSU_TASK_SECCOMP_WORK" not in macros:
        raise SystemExit("missing production seccomp work macro block")
    effect_lines = []
    for name in ("MAPLESU_EFFECT_TASK_STATE_CHANGED", "MAPLESU_EFFECT_TASK_STATE_COMPLETED"):
        matches = re.findall(rf"^#define {name} .+$", protocol, re.MULTILINE)
        if len(matches) != 1:
            raise SystemExit(f"expected exactly one protocol definition for {name}")
        effect_lines.extend(matches)
    effect_definitions = "\n".join(effect_lines)
    for marker, replacement in (
        ("// EXACT_PROTOCOL_EFFECTS", effect_definitions),
        ("// EXACT_TASK_STATE_MACROS", macros),
        ("// EXACT_TASK_STATE_FUNCTION", exact),
    ):
        if fixture.count(marker) != 1:
            raise SystemExit(f"expected exactly one fixture marker: {marker}")
        fixture = fixture.replace(marker, replacement)

    compilers = sorted((root / ".research/toolchains").glob(
        "llvm-mingw-*/bin/x86_64-w64-mingw32-clang++.exe"
    ))
    if not compilers:
        raise SystemExit("missing llvm-mingw host compiler under repo root")
    compiler = compilers[-1]
    configurations = (
        ("syscall_work_filter", 1, 1, 1, 0),
        ("thread_flag_filter", 1, 1, 0, 0),
        ("syscall_work_filter_locking_release", 1, 1, 1, 1),
        ("thread_flag_filter_locking_release", 1, 1, 0, 1),
        ("syscall_work_strict", 1, 0, 1, 0),
        ("thread_flag_strict", 1, 0, 0, 0),
        ("no_seccomp", 0, 0, 0, 0),
    )
    runs = []
    for name, seccomp, filters, generic, locks_task in configurations:
        generated = out / f"p4_task_state_{name}.cpp"
        exe = out / f"p4-task-state-{name}.exe"
        generated.write_text(fixture, encoding="utf-8", newline="\n")
        command = [str(compiler), "-std=c++20", "-O1", "-g",
                   f"-DFIXTURE_SECCOMP={seccomp}", f"-DFIXTURE_FILTER={filters}",
                   f"-DFIXTURE_GENERIC_ENTRY={generic}", f"-DMSU_TASK_SECCOMP_RELEASE_LOCKS_TASK={locks_task}",
                   str(generated), "-static", "-o", str(exe)]
        build = subprocess.run(command, capture_output=True, text=True,
                               encoding="utf-8", errors="replace", timeout=60)
        (out / f"compile-{name}.log").write_text(build.stdout + build.stderr, encoding="utf-8")
        entry = {"mode": name, "config": {"seccomp": seccomp, "filter": filters, "genericEntry": generic,
                                         "releaseLocksTask": locks_task},
                 "compiled": build.returncode == 0, "buildExitCode": build.returncode,
                 "command": command, "generatedSha256": sha(generated.read_bytes())}
        if not build.returncode:
            run = subprocess.run([str(exe)], capture_output=True, text=True,
                                 encoding="utf-8", errors="replace", timeout=15)
            output = run.stdout + run.stderr
            (out / f"run-{name}.log").write_text(output, encoding="utf-8")
            totals = re.search(r"checks=(\d+) scenarios=(\d+) passed=1", run.stdout)
            entry.update(passed=run.returncode == 0 and totals is not None,
                         exitCode=run.returncode, output=output,
                         checks=int(totals[1]) if totals else 0,
                         scenarios=int(totals[2]) if totals else 0,
                         executableSha256=sha(exe.read_bytes()))
        runs.append(entry)
    unchanged = source_path.read_bytes() == source_bytes and protocol_path.read_bytes() == protocol_bytes
    report = {
        "passed": unchanged and all(item.get("passed", False) for item in runs),
        "repoRoot": str(root), "source": str(source_path), "sourceSha256": sha(source_bytes),
        "protocol": str(protocol_path), "protocolSha256": sha(protocol_bytes),
        "exactFunctionSha256": sha(exact.encode()), "exactMacrosSha256": sha(macros.encode()),
        "effectDefinitions": effect_lines, "effectDefinitionsSha256": sha(effect_definitions.encode()),
        "fixture": str(fixture_path), "fixtureSha256": sha(fixture_bytes),
        "runnerSha256": sha(Path(__file__).read_bytes()), "productionInputsUnchanged": unchanged,
        "compiler": str(compiler), "runs": runs,
        "totalChecks": sum(item.get("checks", 0) for item in runs),
        "totalScenarios": sum(item.get("scenarios", 0) for item in runs),
        "scope": "exact production function/macros/protocol effects; kernel seams for task/mm/cred/locks/filter references; deterministic race hooks; no live kernel/device or scheduler-race proof",
    }
    (out / "results.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
