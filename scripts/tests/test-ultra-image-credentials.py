"""Run production Ultra image credential extraction against corrupt and real image inputs."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys


sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import jdk_tool, ndk_bin, ndk_home

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build", type=Path, required=True)
    parser.add_argument("--image", type=Path, action="append", default=[])
    parser.add_argument("--empty-image", type=Path, action="append", default=[])
    parser.add_argument("--ndk", type=Path, default=None)
    args = parser.parse_args()
    args.ndk = ndk_home(args.ndk)
    root = Path(__file__).resolve().parents[2]
    build = args.build.resolve()
    build.mkdir(parents=True, exist_ok=True)
    classes = build / "decode-classes"
    classes.mkdir(exist_ok=True)
    headers = build / "host-include"
    (headers / "sys").mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ndk_bin(args.ndk).parent / "sysroot/usr/include/jni.h",
                    headers / "jni.h")
    (headers / "sys/system_properties.h").write_text(
        '#pragma once\n#define PROP_VALUE_MAX 92\nextern "C" int __system_property_get(const char*, char*);\n',
        encoding="utf-8")
    decode_sources = sorted((root / "image-core/src/main/java").rglob("*.java"))
    decode_sources.append(root / "scripts/fixtures/ImageCredentialDecode.java")
    subprocess.run([str(jdk_tool("javac")), "--release", "17", "-encoding", "UTF-8", "-d", str(classes),
                    *map(str, decode_sources)], check=True, timeout=60)
    images = []
    supplied = [(path, 1) for path in args.image] + [(path, 0) for path in args.empty_image]
    for index, (image, expected) in enumerate(supplied):
        image = image.resolve()
        destination = build / f"kernel-{index}.raw"
        if image.suffix == ".raw":
            shutil.copyfile(image, destination)
        else:
            subprocess.run([str(jdk_tool()), "-Xmx512m", "-cp", str(classes), "ImageCredentialDecode",
                            str(image), str(destination)], check=True, timeout=60)
        raw = destination.read_bytes()
        images.append({"image": str(image), "kernel": destination.name,
                       "kernelSha256": hashlib.sha256(raw).hexdigest(), "kernelBytes": len(raw),
                       "expectedCandidates": expected})
    kernel_args = " ".join(f"{item['expectedCandidates']} /case/{item['kernel']}" for item in images)
    script = """set -eu
ulimit -c 0
cd /case
C=/opt/ddk/clang/clang-r510928
$C/bin/clang++ -std=c++20 -O1 -g -stdlib=libc++ -Wall -Wextra -Werror \\
 -fsanitize=address,undefined -fno-sanitize=vptr -fno-omit-frame-pointer \\
 -Wl,-rpath,$C/lib/x86_64-unknown-linux-gnu \\
 /repo/runtime/tests/pro_image_credentials_test.cpp -o image-credentials.test
timeout 90s ./image-credentials.test """ + kernel_args + """
$C/bin/clang++ -std=c++20 -O1 -g -stdlib=libc++ -pthread -fexceptions \\
 -ffunction-sections -fdata-sections -Wall -Wextra -Werror \\
 -fsanitize=address,undefined -fno-sanitize=vptr -fno-omit-frame-pointer \\
 -I/case/host-include -I/repo/runtime/ultra/bridge \\
 -isystem /repo/runtime/maplesu-sdk/include -isystem /repo/runtime/ultra/third_party/include \\
 -isystem /repo/runtime/ultra/third_party/cJSON \\
 -Wl,--gc-sections -Wl,--wrap=close -Wl,-rpath,$C/lib/x86_64-unknown-linux-gnu \\
 /repo/runtime/tests/pro_image_credentials_jni_test.cpp \\
 /repo/runtime/ultra/bridge/sdk_call_scope.cpp /repo/runtime/ultra/third_party/cJSON/cJSON.cpp \\
 -o image-credentials-jni.test
timeout 90s ./image-credentials-jni.test """ + kernel_args
    result = subprocess.run([
        "docker", "run", "--rm", "--pull=never", "--network", "none",
        "--mount", f"type=bind,source={root},target=/repo,readonly",
        "--mount", f"type=bind,source={build},target=/case", "--entrypoint", "sh",
        "ghcr.io/ylarod/ddk-min:android15-6.6-20260828", "-c", script,
    ], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=240)
    output = result.stdout + result.stderr
    (build / "image-credentials.log").write_text(output, encoding="utf-8")
    sources = ["runtime/ultra/bridge/image_credentials.h", "runtime/tests/pro_image_credentials_test.cpp",
               "runtime/tests/pro_image_credentials_jni_test.cpp", "runtime/ultra/bridge/native_lib_ultra.cpp",
               "runtime/ultra/bridge/sdk_call_scope.cpp", "runtime/ultra/bridge/sdk_call_scope.h",
               "runtime/ultra/third_party/cJSON/cJSON.cpp", "scripts/tests/test-ultra-image-credentials.py"]
    (build / "image-credentials.json").write_text(json.dumps({
        "passed": result.returncode == 0, "exitCode": result.returncode,
        "sourceHashes": {source: hashlib.sha256((root / source).read_bytes()).hexdigest()
                         for source in sources + [path.relative_to(root).as_posix() for path in decode_sources]},
        "images": images, "sanitizers": ["address", "undefined (DDK vptr runtime excluded)"],
        "network": "none", "kernelExecuted": False, "deviceVerification": False,
    }, indent=2) + "\n", encoding="utf-8")
    print(output, end="")
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
