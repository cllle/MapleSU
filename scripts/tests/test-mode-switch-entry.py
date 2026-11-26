"""Compile and exercise the real mode-switch UI orchestration without an APK build."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import jdk_tool

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/specgen"))
from specgen.product import cached_jar


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--contract-only", action="store_true")
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    base = ROOT / ".tmp/p1-delivery-r7/gradle-build/manager"
    cached = base / "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
    inputs = json.loads((ROOT / ".tmp/p0-mode-a-tests/compile-inputs.json").read_text())
    deps = [cached, base / "intermediates/javac/debug/compileDebugJavaWithJavac/classes",
            ROOT / ".tmp/p0-mode-a-tests/b2-wiring.jar",
            ROOT / ".tmp/p0-mode-a-tests/c-installer-classes", *map(Path, inputs["libraries"])]
    deps += [cached_jar(*item) for item in [
        ("junit", "junit", "4.13.2"), ("org.hamcrest", "hamcrest-core", "1.3"),
        ("org.jetbrains.kotlinx", "kotlinx-coroutines-test-jvm", "1.11.0")]]
    # Gradle's API jars compile the UI; ViewModel execution needs its actual implementation.
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
    lifecycle = next((cache / "androidx.lifecycle/lifecycle-viewmodel-android/2.11.0").glob("*/*.aar"))
    actual = out / "lifecycle-viewmodel.jar"
    with zipfile.ZipFile(lifecycle) as archive:
        actual.write_bytes(archive.read("classes.jar"))
    deps.insert(0, actual)
    source_names = [
        "dev/maplesu/manager/workmode/ui/WorkModeUiState.kt",
        "dev/maplesu/manager/workmode/ui/WorkModeSheetModel.kt",
    ]
    tests = ["dev.maplesu.manager.workmode.ui.WorkModeSheetModelTest",
             "dev.maplesu.manager.workmode.ui.SwitchFlowReducerTest",
             "dev.maplesu.manager.workmode.ui.WorkModeStateMachineTest"]
    if not args.contract_only:
        source_names += [
            "dev/maplesu/manager/workmode/ui/WorkModeViewModel.kt",
            "dev/maplesu/manager/workmode/ui/ModeSwitchViewModel.kt",
            "dev/maplesu/manager/workmode/ui/ModeSwitchUiState.kt",
            "dev/maplesu/manager/workmode/ui/ModeSwitchStatusProjection.kt",
            "dev/maplesu/manager/workmode/ui/WorkModeSwitchSession.kt",
            "dev/maplesu/manager/workmode/ui/ModeSwitchScreen.kt",
            "dev/maplesu/manager/workmode/ui/WorkModeSwitchSheet.kt",
            "dev/maplesu/manager/workmode/service/ModeSwitchFacade.kt",
            "dev/maplesu/manager/workmode/service/ModeSwitchClient.kt",
            "dev/maplesu/manager/workmode/service/ModeSwitchProgress.kt",
            "dev/maplesu/manager/workmode/switch/ModeSwitchPreflight.kt",
            "dev/maplesu/manager/workmode/switch/ModeSwitchTypes.kt",
            "dev/maplesu/manager/installer/InstallTargetPartition.kt",
            "com/maplesu/core/ui/navigation3/Routes.kt",
            "com/maplesu/core/ui/navigation3/WorkModeNavigation.kt",
            "com/maplesu/core/ui/MainActivity.kt",
        ]
        tests += ["dev.maplesu.manager.workmode.ui.ModeSwitchStatusProjectionTest",
                  "dev.maplesu.manager.workmode.ui.ModeSwitchViewModelTest",
                  "com.maplesu.core.ui.navigation3.WorkModeRoutesTest"]
    sources = [ROOT / "manager/src/main/java" / name for name in source_names]
    sources += [ROOT / "manager/src/test/java" / (name.replace(".", "/") + ".kt") for name in tests]
    identities = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    compiler = cached_jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.4.20")
    compiler_deps = [compiler] + [cached_jar(*item) for item in [
        ("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.20"),
        ("org.jetbrains", "annotations", "13.0"),
        ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.11.0")]]
    java = jdk_tool()
    classes = out / "classes"
    classes.mkdir(exist_ok=True)
    cp = os.pathsep.join(map(str, [classes] + deps))
    commands = [[str(java), "-Xmx768m", "-cp", os.pathsep.join(map(str, compiler_deps)),
                 "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect",
                 "-jvm-target", "21", "-Xfriend-paths=" + str(cached), "-classpath", cp,
                 "-d", str(classes),
                 *("-Xplugin=" + p for p in inputs["pluginClasspath"][:3]), *map(str, sources)],
                [str(java), "-cp", cp, "org.junit.runner.JUnitCore", *tests]]
    for i, command in enumerate(commands):
        result = subprocess.run(command, cwd=ROOT / "manager", capture_output=True,
                                text=True, encoding="utf-8", errors="replace", timeout=120)
        log = result.stdout + result.stderr
        (out / ("compile.log" if i == 0 else "tests.log")).write_text(log, encoding="utf-8")
        print(log)
        if result.returncode:
            break
    unchanged = identities == {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    (out / "verification.json").write_text(json.dumps(dict(
        passed=result.returncode == 0 and unchanged, exitCode=result.returncode,
        sourcesUnchanged=unchanged, sourceSha256=identities, tests=tests,
        APKProduced=False, deviceTestsPerformed=False, partitionWrites=False,
    ), indent=2) + "\n", encoding="utf-8")
    raise SystemExit(result.returncode if unchanged else 1)


if __name__ == "__main__":
    main()
