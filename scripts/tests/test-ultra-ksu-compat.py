#!/usr/bin/env python3
"""Execute exact compat TU and focused production query slices at kernel boundaries."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
HOST = Path("runtime/ultra/identity/kernel")
REGISTRY = Path("runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/kernel/su_install_registry.c")
FIXTURE = Path("scripts/fixtures/ksu-compat")
IMAGE = "sha256:272ada1e61c525116032246df2a790d1ccec1804b7a9e0b09203c5e650dcb78e"


def identity(path):
    data = path.read_bytes()
    return dict(path=str(path), size=len(data), sha256=hashlib.sha256(data).hexdigest())


def function(source, name):
    import re
    found = list(re.finditer(r"(?m)^(?:static )?(?:u32|int|void|bool|struct msu_su_registry \*)\s*" + name + r"\(", source))
    if len(found) != 1:
        raise ValueError("missing or ambiguous production function: " + name)
    begin = found[0].start()
    at = source.index("{", found[0].end())
    depth = 1
    end = at + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[begin:end] + "\n"


def inside(root, output):
    fixture = root / FIXTURE
    paths = [root / HOST / name for name in ("manager_ksu_compat.c", "manager_ksu_compat.h",
        "manager_su_runtime_factory.c", "manager_su_runtime_factory.h", "manager_owner.c", "manager_owner.h")]
    paths += [root / REGISTRY, root / FIXTURE / "boundary.h", root / FIXTURE / "fixture.c", Path(__file__),
        root / FIXTURE / "module_environment.cpp",
        root / "runtime/maplesu-sdk/sdk/include/maplesu_module_environment_private.h",
        root / "runtime/src/module_task_environment.h"]
    before = [identity(path) for path in paths]
    work = Path("/tmp/msu-ksu-compat")
    work.mkdir()
    shutil.copyfile(root / HOST / "manager_ksu_compat.c", work / "manager_ksu_compat.c")
    shutil.copyfile(root / HOST / "manager_ksu_compat.h", work / "manager_ksu_compat.h")
    headers = ("linux/anon_inodes.h", "linux/compat.h", "linux/cred.h", "linux/err.h", "linux/file.h",
        "linux/fs.h", "linux/irqflags.h", "linux/mm.h", "linux/preempt.h", "linux/security.h",
        "linux/slab.h", "linux/spinlock.h", "linux/task_work.h", "linux/uaccess.h", "linux/user_namespace.h",
        "linux/module.h", "linux/types.h", "asm/ptrace.h", "manager_dynamic_layout.h",
        "manager_su_runtime_factory.h", "manager_resident_host.h", "su_install_registry.h")
    for name in headers:
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text('#include "boundary.h"\n')
    shutil.copyfile(fixture / "boundary.h", work / "boundary.h")
    sources = {name: (root / HOST / name).read_text() for name in ("manager_owner.c", "manager_su_runtime_factory.c")}
    slices = function((root / REGISTRY).read_text(), "msu_su_registry_query_root")
    for name in ("msu_owner_uid_valid", "msu_manager_owner_epoch", "msu_manager_owner_appid"):
        slices += function(sources["manager_owner.c"], name)
    for name in ("sleepable", "acquire", "release", "ksu_begin", "msu_su_runtime_ksu_fence",
                 "msu_su_runtime_ksu_validate", "msu_su_runtime_ksu_query"):
        slices += function(sources["manager_su_runtime_factory.c"], name)
    (work / "production-query-slices.h").write_text(slices)
    shutil.copyfile(fixture / "fixture.c", work / "fixture.c")
    commands = []
    result = dict(schema="maplesu-ksu-compat-narrow/1", inputs=before,
        wholeCompatProductionTu=True, registryFactoryAndOwnerFunctions="exact production source slices",
        kernelObjectsAndServices="host boundary fixtures", device=False, fullBuilds=0)
    passed = True
    for compat in (0, 1):
        binary = work / ("test-compat-" + str(compat))
        command = ["/opt/ddk/clang/clang-r416183b/bin/clang", "-std=gnu11", "-O1", "-g0",
            "-Wall", "-Wextra", "-Werror", "-Wno-unused-parameter", "-pthread", "-I" + str(work),
            *( ["-DCONFIG_COMPAT=1"] if compat else []), str(work / "fixture.c"), "-o", str(binary)]
        commands.append(command)
        run = subprocess.run(command, capture_output=True)
        (output / ("compile-" + str(compat) + ".log")).write_bytes(run.stdout + run.stderr)
        row = dict(compat=bool(compat), compileExit=run.returncode)
        if not run.returncode:
            run = subprocess.run([str(binary)], capture_output=True)
            (output / ("behavior-" + str(compat) + ".log")).write_bytes(run.stdout + run.stderr)
            row.update(behaviorExit=run.returncode, passed=run.returncode == 0 and b"KSU_COMPAT_SOURCE_PASS" in run.stdout)
        else:
            row["passed"] = False
            print(run.stderr.decode())
        passed &= row["passed"]
        result.setdefault("variants", []).append(row)
    command = ["g++", "-std=c++20", "-O1", "-Wall", "-Wextra", "-Werror",
        "-I/repo/runtime/maplesu-sdk/sdk/src/helper", "-I/repo/runtime/maplesu-sdk/sdk/include",
        "-I/repo/runtime/maplesu-sdk/include", "-I/repo/runtime/src",
        str(root / FIXTURE / "module_environment.cpp"), "-o", str(work / "module-environment")]
    commands.append(command)
    run = subprocess.run(command, capture_output=True)
    (output / "compile-environment.log").write_bytes(run.stdout + run.stderr)
    row = dict(compileExit=run.returncode)
    if not run.returncode:
        run = subprocess.run([str(work / "module-environment")], capture_output=True)
        (output / "behavior-environment.log").write_bytes(run.stdout + run.stderr)
        row.update(behaviorExit=run.returncode, passed=run.returncode == 0 and b"KSU_MODULE_ENV_PASS" in run.stdout)
    else:
        row["passed"] = False
        print(run.stderr.decode())
    result["environmentRuntimeProbe"] = row
    passed &= row["passed"]
    result.update(commands=commands, inputsUnchanged=before == [identity(path) for path in paths])
    result["passed"] = passed and result["inputsUnchanged"]
    (output / "RESULT.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(dict(passed=result["passed"], variants=result["variants"])))
    return 0 if result["passed"] else 1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--inside", action="store_true")
    args = parser.parse_args()
    if args.inside:
        return inside(Path("/repo"), args.output)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    argv = ["docker", "run", "--rm", "--pull", "never", "--network", "none", "--memory", "256m",
        "--memory-swap", "256m", "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
        "--mount", "type=bind,source=" + str(ROOT) + ",target=/repo,readonly",
        "--mount", "type=bind,source=" + str(output) + ",target=/out", "--entrypoint", "python3", IMAGE,
        "-B", "/repo/scripts/tests/test-ultra-ksu-compat.py", "--inside", "--output", "/out"]
    (output / "COMMAND.json").write_text(json.dumps(argv, indent=2) + "\n")
    run = subprocess.run(argv, capture_output=True)
    (output / "docker.log").write_bytes(run.stdout + run.stderr)
    print(run.stdout.decode("utf-8", "replace"))
    print(run.stderr.decode("utf-8", "replace"))
    return run.returncode


if __name__ == "__main__":
    sys.exit(main())
