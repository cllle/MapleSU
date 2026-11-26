"""Compile product sources and simulate patch/build/partition writes on supplied boot images."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import android_jar, jdk_tool

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/specgen"))
from specgen.product import cached_jar


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--images", type=Path, required=True, help="UTF-8 file of workspace-relative boot image paths")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--android-jar", type=Path)
    args = parser.parse_args()
    images = args.images.resolve(strict=True)
    output = args.out.resolve()
    output.mkdir(parents=True, exist_ok=False)
    java = shutil.which(str(jdk_tool()))
    javac = shutil.which(str(jdk_tool("javac")))
    if not java or not javac:
        raise RuntimeError("JDK_21_REQUIRED")
    android = args.android_jar or android_jar()
    dependencies = [cached_jar(*entry) for entry in [
        ("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.20"),
        ("org.jetbrains", "annotations", "13.0"),
        ("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm", "1.11.0"),
        ("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm", "1.11.0"),
        ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.11.0")]]
    compiler = cached_jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.4.20")
    classes = output / "classes"
    classes.mkdir()
    java_sources = sorted(path for component in ("image-core", "installer-core")
                          for path in (ROOT / component / "src/main/java").rglob("*.java"))
    kotlin_sources = sorted((ROOT / "manager/src/main/java/dev/maplesu/manager/ultrakernel").glob("*.kt"))
    kotlin_sources.append(ROOT / "scripts/fixtures/UltraInstallMatrix.kt")
    packaged_claim = sorted((ROOT / "manager/src/main/assets/ultra-manager-claim").rglob("*"))
    packaged_claim = [path for path in packaged_claim if path.is_file()]
    sources = java_sources + kotlin_sources + packaged_claim + [Path(__file__).resolve()]
    digests = {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources}
    cp = os.pathsep.join(map(str, [classes, android, *dependencies]))
    commands = [
        [javac, "--release", "17", "-encoding", "UTF-8", "-d", str(classes), *map(str, java_sources)],
        [java, "-Xmx768m", "-cp", os.pathsep.join(map(str, [compiler, *dependencies])),
         "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "21",
         "-classpath", cp, "-d", str(classes), *map(str, kotlin_sources)],
        [java, "-Xmx1536m", "-Dfile.encoding=UTF-8", "-cp", cp,
         "dev.maplesu.manager.ultrakernel.UltraInstallMatrixKt", str(ROOT), str(output), str(images)]]
    code = 1
    for index, command in enumerate(commands):
        with (output / f"step-{index}.log").open("w", encoding="utf-8") as log:
            result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=1800)
        code = result.returncode
        print(f"step={index} exit={code} log={output / f'step-{index}.log'}", flush=True)
        if code:
            break
    unchanged = digests == {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources}
    (output / "verification.json").write_text(json.dumps(dict(
        passed=code == 0 and unchanged, exitCode=code, sourcesUnchanged=unchanged,
        sourceSha256=digests, deviceAccess=False,
        imagesFileSha256=hashlib.sha256(images.read_bytes()).hexdigest()), indent=2) + "\n", encoding="utf-8")
    return code if unchanged else 1


if __name__ == "__main__":
    raise SystemExit(main())
