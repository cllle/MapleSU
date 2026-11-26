"""Compile the current mode-switch source batch and run narrow host regressions."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import android_jar, jdk_tool

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/specgen"))
from specgen.product import cached_jar

TESTS = [
    "dev.maplesu.manager.workmode.service.ModeSwitchDeviceDelegateTest",
    "dev.maplesu.manager.workmode.switch.ModeSwitchControllerTest",
    "dev.maplesu.manager.workmode.switch.ModeSwitchTargetPartitionTest",
]


def identity(path):
    path = Path(path).resolve()
    return {"path": str(path), "bytes": path.stat().st_size,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}


def dependency_files(paths):
    files = set()
    for path in paths:
        if path.is_dir():
            files.update(path.rglob("*.class"))
            files.update(path.rglob("*.kotlin_module"))
        else:
            files.add(path)
    return sorted(files)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    main_root = ROOT / "manager/src/main/java/dev/maplesu/manager"
    sources = [main_root / name for name in [
        "workmode/WorkMode.kt", "workmode/switch/ModeSwitchTypes.kt",
        "workmode/switch/ModeSwitchAuthority.kt", "workmode/switch/ModeSwitchPlan.kt",
        "workmode/switch/ModeSwitchPreflight.kt", "workmode/switch/ModeSwitchController.kt",
        "workmode/service/ModeSwitchDeviceDelegate.kt", "workmode/service/ServiceModeSwitchWiring.kt",
        "workmode/service/ModeSwitchSourceRecovery.kt", "workmode/service/ModeSwitchCandidateImage.kt",
        "workmode/service/LocalInstallerGateway.kt", "workmode/service/ModeSwitchAidlCodec.kt",
        "workmode/service/ServiceClientModeSwitchClient.kt", "workmode/service/ModeSwitchClient.kt",
        "installer/InstallTargetMode.kt", "installer/InstallTargetPartition.kt",
        "installer/InstallerPending.kt", "installer/InstallerDeviceLock.kt",
        "installer/KsuKernelPreservation.kt", "ksu/KsuInstallProducer.kt",
        "ksu/KsuFileCommandRunner.kt",
    ]]
    sources += [ROOT / "manager/src/ksu/java/dev/maplesu/manager/ksu" / name
                for name in ["KsuCommandBackend.kt", "KsuSupport.kt"]]
    sources += [ROOT / "manager/src/test/java" / (name.replace(".", "/") + ".kt") for name in TESTS]
    sources += [ROOT / "manager/src/test/java/dev/maplesu/manager" / name for name in
                ["workmode/switch/ModeSwitchFakes.kt", "workmode/TestFixtures.kt"]]
    sources.append(ROOT / "tools/tests/ModeSwitchPlanCodecMain.kt")
    source_records = [identity(Path(__file__)), *map(identity, sources)]
    snapshots = []
    for source in sources:
        target = out / "source" / source.relative_to(ROOT)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(source.read_bytes())
        snapshots.append(target)

    inputs_path = ROOT / ".tmp/p0-mode-a-tests/compile-inputs.json"
    inputs = json.loads(inputs_path.read_bytes())
    base = ROOT / ".tmp/p1-delivery-r7/gradle-build/manager/intermediates"
    cached = base / "built_in_kotlinc/debug/compileDebugKotlin/classes"
    recovery_path = ROOT / ".tmp/p2-recovery-invalid-avb-r1/final-captured/receipt.json"
    recovery = json.loads(recovery_path.read_bytes())
    product = ROOT / "docs/dual-mode/impl/_work/2026-11-15-p2/evidence/embedded-real-lifecycle-r2/tested-product-overlay.jar"
    product_record = next(row for row in recovery["frozenInputs"] if Path(row["path"]) == product)
    verified = [recovery["overlay"], *recovery["imageClasses"], product_record]
    for record in verified:
        if identity(record["path"]) != record:
            raise ValueError("Verified dependency changed: " + record["path"])
    image_classes = Path(recovery["imageClasses"][0]["path"]).parents[3]
    deps = [Path(recovery["overlay"]["path"]), image_classes, product,
            ROOT / ".tmp/p0-mode-a-tests/c-installer-classes",
            ROOT / ".tmp/p0-mode-a-tests/b2-wiring.jar", cached,
            base / "javac/debug/compileDebugJavaWithJavac/classes",
            base / "../../image-core/libs/image-core.jar",
            base / "../../installer-core/libs/installer-core.jar",
            android_jar(),
            *map(Path, inputs["libraries"]),
            cached_jar("junit", "junit", "4.13.2"),
            cached_jar("org.hamcrest", "hamcrest-core", "1.3")]
    compiler = [cached_jar(*item) for item in [
        ("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.4.20"),
        ("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.20"),
        ("org.jetbrains", "annotations", "13.0"),
        ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.11.0")]]
    plugin = cached_jar("org.jetbrains.kotlin", "kotlin-serialization-compiler-plugin-embeddable", "2.4.20")
    java, javac = jdk_tool(), jdk_tool("javac")
    classes, seam_classes, temp = out / "classes", out / "bundle-seam", out / "temp"
    for path in [classes, seam_classes, temp]:
        path.mkdir()
    seam = out / "Bundle.java"
    seam.write_text("""package android.os;
import java.util.LinkedHashMap;
import java.util.Set;
public class Bundle {
    private final LinkedHashMap<String, String> values = new LinkedHashMap<>();
    public Bundle() {}
    public void putString(String key, String value) { values.put(key, value); }
    public String getString(String key) { return values.get(key); }
    public Set<String> keySet() { return values.keySet(); }
}
""", encoding="utf-8")
    deps = [path.resolve() for path in deps]
    classpath = os.pathsep.join(map(str, [classes, *deps]))
    compiler_cp = os.pathsep.join(map(str, compiler))
    commands = [
        ("compile", [java, "-Djava.io.tmpdir=" + str(temp), "-Xmx768m", "-cp", compiler_cp,
                     "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect",
                     "-jvm-target", "21", "-Xfriend-paths=" + ",".join(map(str, [cached, product,
                         Path(recovery["overlay"]["path"])])), "-Xplugin=" + str(plugin),
                     "-classpath", classpath, "-d", classes, *snapshots]),
        ("junit", [java, "-Djava.io.tmpdir=" + str(temp), "-Xmx768m", "-cp", classpath,
                    "org.junit.runner.JUnitCore", *TESTS]),
        ("bundle-seam", [javac, "-J-Djava.io.tmpdir=" + str(temp), "-d", seam_classes, seam]),
        ("codec", [java, "-Djava.io.tmpdir=" + str(temp), "-Xmx512m", "-cp",
                    os.pathsep.join(map(str, [seam_classes, classes, *deps])),
                    "dev.maplesu.manager.workmode.service.ModeSwitchPlanCodecMainKt"]),
    ]
    receipt = {"schema": "maplesu-mode-switch-source-batch/1", "sources": source_records,
               "dependencies": [identity(path) for path in dependency_files([*deps, *compiler, plugin,
                   java, javac])],
               "commands": [], "tests": TESTS, "compileInputs": identity(inputs_path),
               "classpath": list(map(str, deps)), "verifiedRecoveryInputs": verified,
               "recoveryReceipt": identity(recovery_path),
               "deviceOperations": False, "partitionWrites": False, "SDKOrAPKBuild": False,
               "scope": "Current Controller/Planner/Delegate/Wiring/SourceRecovery/client/codec compiled together; "
                        "three JUnit suites use fake/file ports and real installer-core. Wiring is compile-only. "
                        "Codec uses a map-backed host Bundle; no Binder or Parcel. Other dependencies are cached "
                        "identified classes; this is not a whole-current-tree build. Delegate recreation preserves "
                        "BASE and allows new prepare; it does not restore the gateway's old in-memory plan.",
               "bundleSeam": identity(seam)}
    env = os.environ.copy()
    env.update(TEMP=str(temp), TMP=str(temp), TMPDIR=str(temp))
    for name, argv in commands:
        argv = list(map(str, argv))
        done = subprocess.run(argv, cwd=ROOT / "manager", env=env, stdin=subprocess.DEVNULL,
                              capture_output=True, text=True, encoding="utf-8", errors="replace")
        (out / (name + ".log")).write_text(done.stdout + done.stderr, encoding="utf-8")
        receipt["commands"].append({"name": name, "argv": argv, "exit": done.returncode})
        print(name + ": exit=" + str(done.returncode), flush=True)
        print((done.stdout + done.stderr).encode("ascii", errors="backslashreplace").decode("ascii"),
              end="", flush=True)
        if done.returncode:
            break
    receipt["sourcesUnchanged"] = source_records == [identity(row["path"]) for row in source_records]
    receipt["dependenciesUnchanged"] = receipt["dependencies"] == [identity(row["path"]) for row in receipt["dependencies"]]
    receipt["classes"] = [identity(path) for path in sorted(classes.rglob("*.class"))]
    receipt["passed"] = (receipt["sourcesUnchanged"] and receipt["dependenciesUnchanged"] and
                          len(receipt["commands"]) == len(commands) and
                          all(row["exit"] == 0 for row in receipt["commands"]))
    (out / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    return 0 if receipt["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
