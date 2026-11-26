"""Compile current Normal production code and run its host JVM regressions.

Uses an existing Manager compilation only for Android and unrelated dependencies;
the KSU command, module, settings, boot receipt and bootstrap classes are rebuilt.
No APK, device access, root commands or partition writes are involved.
"""
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
    parser.add_argument("--compile-inputs", type=Path,
                        default=ROOT / ".tmp/p0-mode-a-tests/compile-inputs.json")
    parser.add_argument("--manager-build", type=Path,
                        default=ROOT / ".tmp/p1-delivery-r7/gradle-build/manager")
    parser.add_argument("--consumer-classes", type=Path, action="append",
                        help="explicit dependency class roots (for example current Release Kotlin/Java/image/installer)")
    parser.add_argument("--resource-jar", type=Path,
                        help="current generated Manager R.jar, ahead of cached dependencies")
    parser.add_argument("--tests", nargs="+",
                        help="run only these suites (short or fully qualified class names)")
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    inputs = json.loads(args.compile_inputs.read_text(encoding="utf-8"))
    consumer_classes = args.consumer_classes or [
        args.manager_build / "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
        args.manager_build / "intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ROOT / ".tmp/p0-mode-a-tests/b2-wiring.jar",
        ROOT / ".tmp/p0-mode-a-tests/c-installer-classes",
    ]
    consumer_classes = [path.resolve() for path in consumer_classes]
    if any(not path.exists() for path in consumer_classes):
        parser.error("Dependency class root missing: " + ", ".join(str(path) for path in consumer_classes if not path.exists()))
    cached = consumer_classes[0]
    java = shutil.which("java")
    if not java:
        parser.error("Java 21 is required")
    deps = [*consumer_classes, *map(Path, inputs["libraries"])]
    if args.resource_jar:
        deps.insert(0, args.resource_jar.resolve())
    deps.insert(0, cached_jar("org.json", "json", "20240303"))
    compiler_deps = [cached_jar(*item) for item in [
        ("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.4.20"),
        ("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.20"),
        ("org.jetbrains", "annotations", "13.0"),
        ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.11.0")]]
    deps += compiler_deps[1:]
    deps += [cached_jar(*item) for item in [
        ("junit", "junit", "4.13.2"), ("org.hamcrest", "hamcrest-core", "1.3")]]
    names = ["KsuSupport", "KsuCommandBackend", "KsuModuleOps", "KsuModuleActionResult",
             "KsuSettingsFeature", "KsuRootShell", "KsuBootInfo", "KsuVersionInfo",
             "KsuBootReceipt", "KsuDeployReceipt", "KsuNatives", "KsuNativesBackend",
             "KsuManagerFd", "KsuBackends", "KsuSuperuserAdapter"]
    sources = [ROOT / "manager/src/ksu/java/dev/maplesu/manager/ksu" / (name + ".kt")
               for name in names]
    sources += [ROOT / "manager/src/main/java/dev/maplesu/manager/ksu" / (name + ".kt")
                for name in ["KsuBootstrap", "KsuInstallProducer", "KsuInstallPolicy",
                             "KsuRootChannel", "NativesFacadeDispatch"]]
    sources += [ROOT / "manager/src/main/java/dev/maplesu/manager/workmode/backend/KsuBackendRegistry.kt"]
    sources += [ROOT / "manager/src/main/java" / name for name in [
        "com/maplesu/core/data/model/Module.kt",
        "com/maplesu/core/data/repository/ModuleRepositoryImpl.kt",
        "com/maplesu/core/ui/screen/module/ModulePreinit.kt",
        "dev/maplesu/manager/modules/ModuleListParsers.kt",
        "dev/maplesu/manager/workmode/ModeRuntime.kt"]]
    tests = ["KsuModuleInstallTest", "KsuParsersAndSettingsTest", "KsuModuleActionTest",
             "KsuModuleIdContractTest", "KsuCommandConstructionTest", "KsuSettingsFeatureOutputTest",
             "KsuVersionIdentityTest", "KsuUapiFreezeTest", "KsuBootReceiptTest",
             "KsuBootstrapTest", "KsuRuntimeStatusTest", "KsuRootChannelTest",
             "KsuManagerFdTest", "KsuNativesDegradationTest", "KsuRootWiringTest",
             "KsuPayloadConsistencyTest"]
    test_base = ROOT / "manager/src/test/java/dev/maplesu/manager/ksu"
    sources += [test_base / (name + ".kt") for name in
                ["KsuTestPaths", "KsuImageFixtures", "KsuBootReceiptFixture"]]
    tests = ["dev.maplesu.manager.ksu." + name for name in tests]
    tests += ["com.maplesu.core.ui.screen.module.ModulePreinitTest",
              "dev.maplesu.manager.workmode.backend.KsuBackendRegistryTest",
              "dev.maplesu.manager.modules.ModuleRepositoryRefreshRaceTest",
              "dev.maplesu.manager.modules.ModuleRepositoryRoutingTest",
              "dev.maplesu.manager.wiring.KsuNormalWiringContractTest"]
    if args.tests:
        by_name = {key: name for name in tests for key in (name, name.rsplit(".", 1)[-1])}
        unknown = set(args.tests) - by_name.keys()
        if unknown:
            parser.error("unknown test suites: " + ", ".join(sorted(unknown)))
        tests = list(dict.fromkeys(by_name[name] for name in args.tests))
    sources += [ROOT / "manager/src/test/java" / (name.replace(".", "/") + ".kt")
                for name in tests]
    digests = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
               for p in sources}
    jar = out / "tests.jar"
    classpath = os.pathsep.join(map(str, [jar] + deps))
    commands = [[java, "-Xmx768m", "-cp", os.pathsep.join(map(str, compiler_deps)),
                 "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect",
                 "-jvm-target", "21", "-Xfriend-paths=" + ",".join(map(str, consumer_classes)),
                 "-classpath", os.pathsep.join(map(str, deps)), "-d", str(jar),
                 *map(str, sources)],
                [java, "-cp", classpath, "org.junit.runner.JUnitCore",
                 *tests]]
    for index, command in enumerate(commands):
        result = subprocess.run(command, cwd=ROOT / "manager", capture_output=True,
                                text=True, encoding="utf-8", errors="replace", timeout=120)
        log = result.stdout + result.stderr
        (out / ("compile.log" if index == 0 else "tests.log")).write_text(log, encoding="utf-8")
        print(log)
        if result.returncode:
            break
    unchanged = digests == {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                            for p in sources}
    (out / "verification.json").write_text(json.dumps(dict(
        passed=result.returncode == 0 and unchanged, exitCode=result.returncode,
        sourcesUnchanged=unchanged, sourceSha256=digests, tests=tests,
        apkProduced=False, deviceTestsPerformed=False, partitionWrites=False,
        dependencyClasses=str(cached), consumerClassRoots=list(map(str, consumer_classes)),
        compileInputs=str(args.compile_inputs),
    ), indent=2) + "\n", encoding="utf-8")
    raise SystemExit(result.returncode if unchanged else 1)


if __name__ == "__main__":
    main()
