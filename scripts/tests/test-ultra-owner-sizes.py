"""Compile actual owner-size/describe sources and focused JUnit tests, without an APK."""
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
    sources = [source_dir / name for name in ("UltraKernelShellcode.kt", "UltraKernelBtf.kt", "UltraKernelA64.kt", "UltraKernelKallsyms.kt", "UltraKernelRequestV2.kt", "UltraKernelRecipe.kt")]
    classes = ["UltraKernelOwnerSizesTest", "UltraKernelStructDataflowTest", "UltraKernelBtfRequestedFieldsTest"]
    sources += [ROOT / "manager/src/test/java/dev/maplesu/manager/ultrakernel" / (name + ".kt") for name in classes]
    def identity(path):
        raw = path.read_bytes()
        return {"path": str(path), "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}
    inputs = [identity(path) for path in sources + deps + [java, compiler, coroutines, Path(__file__)]]
    product = out / "tests.jar"
    classpath = os.pathsep.join(map(str, deps))
    command = [str(java), "-Xmx768m", "-cp", os.pathsep.join(map(str, [compiler, stdlib, annotations, coroutines])),
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "21",
        "-Xfriend-paths=" + str(cached), "-classpath", classpath, "-d", str(product), *map(str, sources)]
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
    (out / "compile.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    receipt = {"inputs": inputs, "compileCommand": command, "compileExitCode": result.returncode,
               "productionEntry": "UltraKernelStructOffsets.describe", "deviceOperations": False, "APKProduced": False}
    print("compile_rc=" + str(result.returncode), flush=True)
    if result.returncode == 0:
        command = [str(java), "-Xmx768m", "-cp", str(product) + os.pathsep + classpath,
            "org.junit.runner.JUnitCore", *["dev.maplesu.manager.ultrakernel." + c for c in classes]]
        result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
        (out / "tests.log").write_text(result.stdout + result.stderr, encoding="utf-8")
        receipt.update(testCommand=command, testsExitCode=result.returncode, testedJar=identity(product))
    receipt["inputsUnchanged"] = all(identity(Path(row["path"])) == row for row in inputs)
    (out / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(result.stdout + result.stderr)
    return result.returncode if receipt["inputsUnchanged"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
