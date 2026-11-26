"""Compile the actual manager switch facade and home state regressions on the host JVM."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import android_jar, jdk_tool

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/specgen"))
from specgen.product import cached_jar


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    classes = ROOT / "manager/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
    java = jdk_tool()
    dependencies = [cached_jar(*item) for item in [
        ("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.20"), ("org.jetbrains", "annotations", "13.0"),
        ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.11.0"),
        ("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm", "1.11.0"),
        ("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm", "1.11.0"),
        ("junit", "junit", "4.13.2"), ("org.hamcrest", "hamcrest-core", "1.3")]]
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
    for group, artifact in [
        ("androidx.compose.runtime", "runtime-annotation-android"),
        ("androidx.compose.runtime", "runtime-android"),
        ("androidx.compose.ui", "ui-android"),
        ("androidx.lifecycle", "lifecycle-viewmodel-android"),
    ]:
        archive = next((cache / group / artifact).glob("*/*/*.aar"))
        target = out / (artifact + ".jar")
        with zipfile.ZipFile(archive) as aar:
            target.write_bytes(aar.read("classes.jar"))
        dependencies.append(target)
    dependencies += [android_jar(),
                     ROOT / "manager/build/intermediates/compile_r_class_jar/debug/generateDebugRFile/R.jar",
                     ROOT / "image-core/build/libs/image-core.jar", ROOT / "installer-core/build/libs/installer-core.jar"]
    main = ROOT / "manager/src/main/java"
    tests = ROOT / "manager/src/test/java"
    sources = [main / name for name in [
        "dev/maplesu/manager/workmode/service/ModeSwitchFacade.kt",
        "dev/maplesu/manager/workmode/switch/ModeSwitchPreflight.kt",
        "dev/maplesu/manager/workmode/switch/HiddenSwitchPolicy.kt",
        "com/maplesu/core/ui/screen/home/HomeWorkModeSwitchRuntime.kt",
    ]]
    test_classes = ["dev.maplesu.manager.workmode.service.ModeSwitchFacadeTest",
                    "com.maplesu.core.ui.screen.home.HomeWorkModeSwitchStateTest"]
    sources += [tests / "dev/maplesu/manager/workmode/TestFixtures.kt"]
    sources += [tests / (name.replace(".", "/") + ".kt") for name in test_classes]
    digests = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    compiler = cached_jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.4.20")
    cp = os.pathsep.join(map(str, [out, classes] + dependencies))
    commands = [[str(java), "-Xmx768m", "-cp", os.pathsep.join(map(str, [compiler] + dependencies)),
                 "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "21",
                 "-Xfriend-paths=" + str(classes), "-classpath", cp, "-d", str(out), *map(str, sources)],
                [str(java), "-cp", cp, "org.junit.runner.JUnitCore", *test_classes]]
    for index, command in enumerate(commands):
        result = subprocess.run(command, cwd=ROOT / "manager", capture_output=True,
                                text=True, encoding="utf-8", errors="replace", timeout=90)
        log = result.stdout + result.stderr
        (out / ("compile.log" if index == 0 else "regression.log")).write_text(log, encoding="utf-8")
        print(log)
        if result.returncode:
            break
    unchanged = digests == {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    (out / "verification.json").write_text(json.dumps(dict(
        passed=result.returncode == 0 and unchanged, exitCode=result.returncode,
        deviceAccess=False, partitionWrites=False, sourcesUnchanged=unchanged,
        sourceSha256=digests, tests=test_classes,
    ), indent=2) + "\n", encoding="utf-8")
    raise SystemExit(result.returncode if unchanged else 1)


if __name__ == "__main__":
    main()
