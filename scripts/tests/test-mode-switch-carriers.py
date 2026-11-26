"""Exercise the actual carrier snapshot/restore and switch orchestration on the host JVM."""
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
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
    lifecycle = next((cache / "androidx.lifecycle/lifecycle-viewmodel-android/2.11.0").glob("*/*.aar"))
    actual = out / "lifecycle-viewmodel.jar"
    with zipfile.ZipFile(lifecycle) as archive:
        actual.write_bytes(archive.read("classes.jar"))
    deps.insert(0, actual)
    names = [
        "workmode/WorkMode.kt",
        "installer/InstallTargetPartition.kt",
        "installer/InstallCommit.kt",
        "installer/KsuKernelPreservation.kt",
        "ultrakernel/UltraKernelOriginalRecovery.kt",
        "ultrakernel/UltraKernelFlow.kt",
        "ksu/KsuInstallProducer.kt",
        "workmode/service/ModeSwitchClient.kt",
        "workmode/service/ModeSwitchFacade.kt",
        "workmode/service/ModeSwitchProgress.kt",
        "workmode/service/UltraRootModeSwitchClient.kt",
        "workmode/service/ModeSwitchRuntime.kt",
        "workmode/service/ModeSwitchSourceRecovery.kt",
        "workmode/service/ModeSwitchCandidateImage.kt",
        "workmode/switch/ModeSwitchPreflight.kt",
        "workmode/switch/ModeSwitchTypes.kt",
        "workmode/ui/ModeSwitchViewModel.kt",
        "workmode/ui/WorkModeViewModel.kt",
        "workmode/ui/WorkModeUiState.kt",
        "workmode/ui/ModeSwitchUiState.kt",
        "workmode/ui/ModeSwitchStatusProjection.kt",
        "workmode/ui/WorkModeSwitchSession.kt",
    ]
    sources = [ROOT / "manager/src/main/java/dev/maplesu/manager" / name for name in names]
    sources += [ROOT / "manager/src/ksu/java/dev/maplesu/manager/ksu" / name
                for name in ["KsuCommandBackend.kt", "KsuSupport.kt"]]
    tests = ["workmode.service.ModeSwitchFacadeTest", "workmode.service.UltraRootModeSwitchClientTest",
             "workmode.service.ModeSwitchSourceRecoveryTest", "workmode.ui.ModeSwitchViewModelTest",
             "installer.InstallCommitCoordinatorTest"]
    sources += [ROOT / "manager/src/test/java/dev/maplesu/manager/workmode/TestFixtures.kt"]
    sources += [ROOT / "manager/src/test/java/dev/maplesu/manager/ksu/KsuImageFixtures.kt"]
    sources += [ROOT / "manager/src/test/java/dev/maplesu/manager" /
                (name.replace(".", "/") + ".kt") for name in tests]
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
                [str(java), "-cp", cp, "org.junit.runner.JUnitCore",
                 *["dev.maplesu.manager." + name for name in tests]]]
    digests = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    runs = []
    for i, command in enumerate(commands):
        result = subprocess.run(command, cwd=ROOT / "manager", capture_output=True,
                                text=True, encoding="utf-8", errors="replace", timeout=120)
        log = result.stdout + result.stderr
        (out / ("compile.log" if i == 0 else "tests.log")).write_text(log, encoding="utf-8")
        print(log)
        runs.append(dict(command=command, exitCode=result.returncode))
        if result.returncode:
            break
    unchanged = digests == {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    (out / "verification.json").write_text(json.dumps(dict(
        passed=result.returncode == 0 and unchanged, runs=runs, sourcesUnchanged=unchanged,
        sourceSha256=digests, tests=tests, APKProduced=False, deviceTestsPerformed=False,
        partitionWrites=False), indent=2) + "\n", encoding="utf-8")
    raise SystemExit(result.returncode if unchanged else 1)


if __name__ == "__main__":
    main()
