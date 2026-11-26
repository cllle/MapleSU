"""Compile actual subject describe/BTF/walker sources and run focused host JUnit tests."""
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--btf-only", action="store_true")
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    java = jdk_tool()
    compiler = cached_jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.4.20")
    stdlib = cached_jar("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.20")
    annotations = cached_jar("org.jetbrains", "annotations", "13.0")
    coroutines = cached_jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.11.0")
    cached = Path("C:/Temp/maplesu-kotlin-btf-widths-20261115/dependencies/p2-verified.jar")
    deps = [stdlib, annotations, cached,
        cached_jar("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm", "1.11.0"),
        cached_jar("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm", "1.11.0"),
        ROOT / ".tmp/p1-delivery-r7/gradle-build/image-core/libs/image-core.jar",
        android_jar(),
        cached_jar("junit", "junit", "4.13.2"), cached_jar("org.hamcrest", "hamcrest-core", "1.3")]
    source_dir = ROOT / "manager/src/main/java/dev/maplesu/manager/ultrakernel"
    sources = [source_dir / name for name in ("UltraKernelShellcode.kt", "UltraKernelBtf.kt", "UltraKernelA64.kt",
        "UltraKernelKallsyms.kt", "UltraKernelRequestV2.kt", "UltraKernelRecipe.kt")]
    classes = ["UltraKernelSubjectBtfTest", "UltraKernelBtfRequestedFieldsTest"] if args.btf_only else [
        "UltraKernelSubjectFieldsTest", "UltraKernelSubjectBtfTest", "UltraKernelOwnerSizesTest",
        "UltraKernelStructDataflowTest", "UltraKernelBtfRequestedFieldsTest", "UltraKernelSecurebitsTest"]
    sources += [ROOT / "manager/src/test/java/dev/maplesu/manager/ultrakernel" / (name + ".kt") for name in classes]
    def identity(path, raw=None):
        raw = path.read_bytes() if raw is None else raw
        return {"path": str(path), "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}
    inputs = []
    captured = []
    for path in sources:
        raw = path.read_bytes()
        inputs.append(identity(path, raw))
        copy = out / "source" / path.relative_to(ROOT)
        copy.parent.mkdir(parents=True, exist_ok=True)
        copy.write_bytes(raw)
        captured.append(copy)
    inputs += [identity(path) for path in deps + [java, compiler, coroutines, Path(__file__)]]
    target_dir = ROOT / "native-inputs/kernels"
    target_inputs = [identity(target_dir / target / name) for target in (
        "android12-5.10-9465fbb7", "android13-5.15-2f109982", "android14-6.1-71c55acc",
        "android15-6.6-346d51ea", "android15-6.6-ddd760da", "android16-6.12-6679e3fb",
        "android17-6.18-4f47635b") for name in ("Image", "symbols.tsv")]
    product = out / "tests.jar"
    classpath = os.pathsep.join(map(str, deps))
    command = [str(java), "-Xmx768m", "-cp", os.pathsep.join(map(str, [compiler, stdlib, annotations, coroutines])),
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "21",
        "-Xfriend-paths=" + str(cached), "-classpath", classpath, "-d", str(product), *map(str, captured)]
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
    (out / "compile.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    receipt = {"inputs": inputs, "targets": target_inputs, "compileCommand": command,
        "compileExitCode": result.returncode, "productionEntry": "UltraKernelStructOffsets.describe",
        "compiledExactSourceSnapshot": True, "deviceOperations": False, "APKProduced": False}
    print("compile_rc=" + str(result.returncode), flush=True)
    if result.returncode == 0:
        command = [str(java), "-Xmx768m", "-cp", str(product) + os.pathsep + classpath,
            "org.junit.runner.JUnitCore", *["dev.maplesu.manager.ultrakernel." + c for c in classes]]
        result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
        (out / "tests.log").write_text(result.stdout + result.stderr, encoding="utf-8")
        receipt.update(testCommand=command, testsExitCode=result.returncode, testedJar=identity(product))
    receipt["originalInputsUnchanged"] = all(identity(Path(row["path"])) == row for row in inputs)
    receipt["targetsUnchanged"] = all(identity(Path(row["path"])) == row for row in target_inputs)
    (out / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(result.stdout + result.stderr)
    return result.returncode if receipt["targetsUnchanged"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
