"""Compile the actual symbol decoder and accessor tests against cached host dependencies."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import android_jar, jdk_tool


ROOT = Path(__file__).resolve().parents[2]
CLASSES = ["UltraKernelKallsymsLayoutTest", "UltraKernelStructDataflowTest"]


def identity(path: Path) -> dict:
    raw = path.read_bytes()
    return {"path": str(path), "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    work = args.out.resolve()
    work.mkdir(parents=True, exist_ok=False)
    spec = importlib.util.spec_from_file_location("product", ROOT / "tools/specgen/specgen/product.py")
    product = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(product)
    jar = product.cached_jar
    java = jdk_tool()
    compiler = jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.4.20")
    stdlib = jar("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.20")
    annotations = jar("org.jetbrains", "annotations", "13.0")
    coroutines = jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.11.0")
    deps = [stdlib, annotations,
        jar("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm", "1.11.0"),
        jar("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm", "1.11.0"),
        ROOT / ".tmp/p1-delivery-r7/gradle-build/image-core/libs/image-core.jar",
        android_jar(),
        jar("junit", "junit", "4.13.2"), jar("org.hamcrest", "hamcrest-core", "1.3")]
    sources = sorted((ROOT / "manager/src/main/java/dev/maplesu/manager/ultrakernel").glob("*.kt"))
    sources += [ROOT / "manager/src/test/java/dev/maplesu/manager/ultrakernel" / (c + ".kt") for c in CLASSES]
    snapshots = []
    for source in sources:
        snapshot = work / "inputs" / source.relative_to(ROOT)
        snapshot.parent.mkdir(parents=True, exist_ok=True)
        snapshot.write_bytes(source.read_bytes())
        snapshots.append((source, snapshot, identity(snapshot)))
    inputs = [identity(p) for p in deps + [java, compiler, coroutines, Path(__file__).resolve(),
        ROOT / "native-inputs/kernels/android15-6.6-346d51ea/Image",
        ROOT / "native-inputs/kernels/android15-6.6-346d51ea/symbols.tsv"]]
    output = work / "tests.jar"
    classpath = os.pathsep.join(map(str, deps))
    boot = os.pathsep.join(map(str, [compiler, stdlib, annotations, coroutines]))
    compile_command = [str(java), "-Xmx512m", "-cp", boot,
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "21",
        "-classpath", classpath, "-d", str(output), *[str(p) for _, p, _ in snapshots]]
    test_command = [str(java), "-Xmx512m", "-Dmaplesu.repo=" + str(ROOT), "-cp",
        str(output) + os.pathsep + classpath, "org.junit.runner.JUnitCore",
        *["dev.maplesu.manager.ultrakernel." + c for c in CLASSES]]
    compiled = subprocess.run(compile_command, capture_output=True, text=True, cwd=ROOT)
    (work / "compile.log").write_text(compiled.stdout + compiled.stderr, encoding="utf-8")
    print("compile_rc=" + str(compiled.returncode), flush=True)
    tested = None
    if compiled.returncode == 0:
        tested = subprocess.run(test_command, capture_output=True, text=True, cwd=ROOT / "manager")
        (work / "tests.log").write_text(tested.stdout + tested.stderr, encoding="utf-8")
        print(tested.stdout + tested.stderr, flush=True)
    else:
        print(compiled.stdout + compiled.stderr, flush=True)
    receipt = {
        "scope": "host JVM; actual production decoder and accessor sources; no release or device execution",
        "compileCommand": compile_command, "compileExitCode": compiled.returncode,
        "testCommand": test_command, "testsExitCode": tested.returncode if tested else None,
        "inputs": inputs,
        "dependenciesUnchanged": all(identity(Path(p["path"])) == p for p in inputs),
        "sources": [{"origin": str(source), "compiled": item,
            "snapshotUnchanged": identity(snapshot) == item,
            "originUnchanged": source.read_bytes() == snapshot.read_bytes()}
            for source, snapshot, item in snapshots],
        "testedJar": identity(output) if output.exists() else None,
    }
    (work / "receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return compiled.returncode or tested.returncode


if __name__ == "__main__":
    sys.exit(main())
