"""Run identity recovery and private-image fallback production JVM logic without Gradle."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys


sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import android_jar, jdk_tool

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build", type=Path, required=True)
    parser.add_argument("--product-classes", type=Path, required=True)
    parser.add_argument("--image", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    sys.path.insert(0, str(root / "tools/specgen"))
    from specgen.product import cached_jar
    build = args.build.resolve()
    classes = build / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    product = args.product_classes.resolve()
    java_sources = [root / "manager/src/main/java/dev/maplesu/manager/runtime/authorization/ApplicationIdentity.java"]
    ultra = root / "manager/src/main/java/dev/maplesu/manager/runtime/ultra"
    source_names = ["UltraIdentityResolution", "UltraReinstallPolicy", "UltraRecoveryKey", "UltraImageIdentityRecovery",
                    "UltraInstallCredentialPolicy", "UltraInstallCredentialTransition"]
    sources = [ultra / f"{name}.kt" for name in source_names]
    tests = ["UltraIdentityRecoveryFallbackTest", "UltraIdentityResolutionTest", "UltraReinstallPolicyTest",
             "UltraImageIdentityRecoveryTest", "UltraInstallCredentialPolicyTest", "UltraInstallCredentialTransitionTest",
             "UltraIdentityDeploymentTest", "UltraManagerSignatureAuthorizationTest"]
    sources += [root / f"manager/src/test/java/dev/maplesu/manager/runtime/ultra/{name}.kt" for name in tests]
    stub = build / "UltraSdkBuild.kt"
    stub.write_text('package dev.maplesu.manager.runtime.ultra\ninternal object UltraSdkBuild { const val BUILD_ID = "host-test" }\n',
                    encoding="utf-8")
    sources.append(stub)
    dependencies = [cached_jar(*entry) for entry in [
        ("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.20"), ("org.jetbrains", "annotations", "13.0"),
        ("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm", "1.11.0"),
        ("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm", "1.11.0"),
        ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.11.0"),
        ("junit", "junit", "4.13.2"), ("org.hamcrest", "hamcrest-core", "1.3")]]
    compiler = cached_jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.4.20")
    android = android_jar()
    cp = os.pathsep.join(map(str, [classes, product, android, *dependencies]))
    commands = [
        [str(jdk_tool("javac")), "--release", "17", "-d", str(classes), *map(str, java_sources)],
        [str(jdk_tool()), "-Xmx768m", "-cp", os.pathsep.join(map(str, [compiler, *dependencies])),
         "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "21",
         f"-Xfriend-paths={product}", "-classpath", cp, "-d", str(classes), *map(str, sources)],
        [str(jdk_tool()), "-Xmx768m", f"-Dmaplesu.test.recovery.image={args.image.resolve()}", "-cp", cp,
         "org.junit.runner.JUnitCore", *[f"dev.maplesu.manager.runtime.ultra.{name}" for name in tests]]]
    product_hash = hashlib.sha256()
    for file in sorted(product.rglob("*.class")):
        product_hash.update(file.relative_to(product).as_posix().encode())
        product_hash.update(hashlib.sha256(file.read_bytes()).digest())
    code = 1
    for index, command in enumerate(commands):
        result = subprocess.run(command, cwd=root, capture_output=True, text=True, encoding="utf-8",
                                errors="replace", timeout=90)
        (build / f"step-{index}.log").write_text(result.stdout + result.stderr, encoding="utf-8")
        print(result.stdout + result.stderr, end="")
        code = result.returncode
        if code:
            break
    (build / "identity-recovery.json").write_text(json.dumps({
        "passed": code == 0, "exitCode": code, "tests": tests,
        "sourceHashes": {str(source.relative_to(root)): hashlib.sha256(source.read_bytes()).hexdigest()
                         for source in java_sources + sources if source != stub},
        "sdkRootUid": "explicit fixture; only production decode and identity state machine execute",
        "deviceVerification": False, "productClasses": str(product),
        "productClassTreeSha256": product_hash.hexdigest(),
        "imageSha256": hashlib.sha256(args.image.read_bytes()).hexdigest(),
    }, indent=2) + "\n", encoding="utf-8")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
