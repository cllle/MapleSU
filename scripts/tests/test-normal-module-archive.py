"""Compile the real ZIP inspector and run strict and Normal preflight regressions."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/specgen"))
from specgen.product import cached_jar


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    java, javac = shutil.which("java"), shutil.which("javac")
    if not java or not javac:
        parser.error("Java 21 JDK is required")
    dependencies = [cached_jar(*item) for item in [
        ("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.20"),
        ("org.jetbrains", "annotations", "13.0"),
        ("junit", "junit", "4.13.2"), ("org.hamcrest", "hamcrest-core", "1.3"),
        ("org.apache.commons", "commons-compress", "1.28.0"),
        ("commons-io", "commons-io", "2.20.0"),
        ("org.apache.commons", "commons-lang3", "3.18.0"),
        ("org.tukaani", "xz", "1.12")]]
    compiler = cached_jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.4.20")
    coroutines = cached_jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.11.0")
    sources = [ROOT / "manager/src/main/java/dev/maplesu/manager" / (name + ".java")
               for name in ["ModuleArchiveInspector", "MapleSuModuleMetadata", "compat/NativeModuleAbi"]]
    test = ROOT / "manager/src/test/java/dev/maplesu/manager/ModuleArchiveInspectorTest.kt"
    sources.append(test)
    digests = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
               for p in sources}
    classes = out / "classes"
    classes.mkdir(exist_ok=True)
    jar = out / "tests.jar"
    classpath = os.pathsep.join(map(str, [classes] + dependencies))
    commands = [
        [javac, "-encoding", "UTF-8", "-cp", classpath, "-d", str(classes),
         *map(str, sources[:-1])],
        [java, "-Xmx768m", "-cp", os.pathsep.join(map(str, [compiler, coroutines] + dependencies)),
         "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "21",
         "-classpath", classpath, "-d", str(jar), str(test)],
        [java, "-Xmx768m", "-cp", str(jar) + os.pathsep + classpath,
         "org.junit.runner.JUnitCore", "dev.maplesu.manager.ModuleArchiveInspectorTest"],
    ]
    for name, command in zip(["java-compile.log", "kotlin-compile.log", "tests.log"], commands):
        result = subprocess.run(command, cwd=ROOT / "manager", capture_output=True,
                                text=True, encoding="utf-8", errors="replace", timeout=120)
        log = result.stdout + result.stderr
        (out / name).write_text(log, encoding="utf-8")
        print(log)
        if result.returncode:
            break
    unchanged = digests == {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                            for p in sources}
    (out / "verification.json").write_text(json.dumps(dict(
        passed=result.returncode == 0 and unchanged, exitCode=result.returncode,
        sourcesUnchanged=unchanged, sourceSha256=digests,
        tests=["dev.maplesu.manager.ModuleArchiveInspectorTest"],
        deviceTestsPerformed=False, partitionWrites=False,
    ), indent=2) + "\n", encoding="utf-8")
    raise SystemExit(result.returncode if unchanged else 1)


if __name__ == "__main__":
    main()
