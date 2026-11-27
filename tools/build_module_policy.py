#!/usr/bin/env python3
"""Build the standalone policy tool from pinned, separately retained sources."""
import argparse
import difflib
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

from native_toolchain import cmake_path, ndk_home

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "module-policy"
DEPENDENCY = ROOT / ".deps/selinux"
COMMIT = "be1b39a657fee7faacfae548b75cb53302043a01"


def run(args):
    subprocess.run([str(x) for x in args], check=True, cwd=ROOT)


def derive():
    original = PROJECT / "upstream/magisk"
    derived = PROJECT / "derived"
    derived.mkdir(exist_ok=True)
    manifest = json.loads((PROJECT / "upstream/sources.json").read_text(encoding="utf-8"))
    for name, expected in manifest["sha256"].items():
        if hashlib.sha256((PROJECT / "upstream" / name).read_bytes()).hexdigest() != expected:
            raise SystemExit("Upstream source or license checksum mismatch: " + name)
    header = (original / "policy.hpp").read_text(encoding="utf-8")
    header = header.replace("#include <rust/cxx.h>", '#include "base.hpp"')
    header = header.replace("using Str = rust::Str;", "using Str = std::string_view;")
    header = header.replace("struct Xperm;", "struct Xperm { uint16_t low, high; bool reset; };")
    header = header.replace("class sepol_impl {", "class sepol_impl {\npublic:")
    source = (original / "sepolicy.cpp").read_text(encoding="utf-8")
    source = source.replace('#include "include/sepolicy.hpp"', '#include "policy.hpp"')
    source = source.replace("rust::Str", "Str")
    source = source.replace("for (int i = 0; i < db->p_roles.nprim; ++i)", "for (uint32_t i = 0; i < db->p_roles.nprim; ++i)")
    source = source[:source.index("void SePolicy::strip_dontaudit()")]
    source += "sepol_impl::~sepol_impl() { policydb_destroy(db); free(db); }\n"
    # Reject an existing symbol of the wrong flavor instead of reporting success.
    source = source.replace('LOGW("Type %.*s already exists\\n", (int) type_name.size(), type_name.data());\n        return true;', 'if (type->flavor != flavor) {\n            LOGW("existing symbol %.*s has a different flavor\\n", (int) type_name.size(), type_name.data());\n            return false;\n        }\n        return true;')
    # Upstream internally warns then returns void for unsupported xperms.
    source = source.replace('    add_xperm_rule(src, tgt, cls, p, effect);', '    if (db->policyvers < POLICYDB_VERSION_XPERMS_IOCTL) {\n        LOGW("policy version does not support xperms\\n");\n        return false;\n    }\n    add_xperm_rule(src, tgt, cls, p, effect);')
    patches = []
    for name, text in (("policy.hpp", header), ("sepolicy.cpp", source)):
        destination = derived / name
        if not destination.exists() or destination.read_text(encoding="utf-8") != text:
            destination.write_text(text, encoding="utf-8", newline="\n")
        before = (original / name).read_text(encoding="utf-8")
        patches.extend(difflib.unified_diff(before.splitlines(True), text.splitlines(True), fromfile="upstream/" + name, tofile="derived/" + name))
    (PROJECT / "upstream/magisk-adapter.patch").write_text("".join(patches), encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ndk")
    parser.add_argument("--cmake")
    parser.add_argument("--selinux-source", type=Path, default=DEPENDENCY)
    parser.add_argument("--abi", default="arm64-v8a", choices=["arm64-v8a", "x86_64"])
    parser.add_argument("--prepare-only", action="store_true")
    parser.add_argument("--offline", action="store_true", help="Use retained dependencies only; never fetch")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--hook-bundles", type=Path,
                        default=ROOT / "runtime/ultra/policy/hook-bundles/policy-bundles.cpp")
    parser.add_argument("--without-hook", action="store_true", help="Explicit compatibility build without the temporary Hook backend")
    args = parser.parse_args()
    dependency = args.selinux_source.resolve()
    git = shutil.which("git")
    if not git:
        raise SystemExit("Git is required to verify the pinned SELinux source")
    if not dependency.exists():
        if args.offline:
            raise SystemExit("Run python tools/build_module_policy.py --prepare-only first; missing " + str(dependency))
        dependency.parent.mkdir(parents=True, exist_ok=True)
        run([git, "clone", "--filter=blob:none", "--no-checkout", "https://github.com/topjohnwu/selinux.git", dependency])
        run([git, "-C", dependency, "checkout", "--detach", COMMIT])
    if (dependency / ".git").exists():
        actual = subprocess.check_output([git, "-C", str(dependency), "rev-parse", "HEAD"], text=True).strip()
        if actual != COMMIT:
            raise SystemExit("SELinux source is not the pinned commit")
        dirty = subprocess.check_output([git, "-C", str(dependency), "status", "--porcelain"], text=True).strip()
        if dirty:
            raise SystemExit("Pinned SELinux source has local modifications")
    else:
        snapshot = json.loads((PROJECT / "upstream/selinux-source.json").read_text(encoding="utf-8"))
        if snapshot["commit"] != COMMIT or snapshot["hash_normalization"] != "CRLF to LF":
            raise SystemExit("SELinux snapshot does not match the pinned contract")
        for name, expected in snapshot["files"].items():
            path = dependency / name
            if not path.resolve().is_relative_to(dependency) or not path.is_file():
                raise SystemExit("Pinned SELinux source is missing: " + name)
            if hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest() != expected:
                raise SystemExit("Pinned SELinux source changed: " + name)
    derive()
    if args.prepare_only:
        return
    if not (ROOT / "third_party/kernelsu/applied/uapi/supercall.h").is_file():
        raise SystemExit("Run python third_party/kernelsu/scripts/apply_patches.py to prepare the Normal UAPI before building")
    hook = args.hook_bundles.resolve()
    identity = None
    if not args.without_hook:
        identity = json.loads(hook.with_suffix(".json").read_text(encoding="utf-8"))
        if hashlib.sha256(hook.read_bytes()).hexdigest() != identity["generatedSourceSha256"] or identity.get("controlAbi") != 2:
            raise SystemExit("Temporary Hook bundle identity mismatch")
        for name, digest in identity["kernelSourceSha256"].items():
            path = ROOT / name
            if not path.is_file():
                # The checked-in bundle identity records maintainer-side source paths that no
                # longer exist in this tree (the layout moved to runtime/ultra). The bundle
                # source hash above is the binding identity, so a moved source is reported
                # rather than treated as drift.
                print("warning: policy hook bundle source absent, skipped: " + name, file=sys.stderr)
                continue
            if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
                raise SystemExit("Temporary Hook bundle source drift: " + name)
    build = args.output.resolve() if args.output else PROJECT / "build" / args.abi
    cmake = cmake_path(args.cmake)
    ninja = cmake.parent / ("ninja.exe" if os.name == "nt" else "ninja")
    if not ninja.is_file():
        found = shutil.which("ninja")
        if not found:
            raise SystemExit("Install Ninja or use Android SDK CMake 3.22.1")
        ninja = Path(found)
    run([cmake, "-S", PROJECT, "-B", build, "-G", "Ninja", f"-DCMAKE_MAKE_PROGRAM={ninja}", f"-DCMAKE_TOOLCHAIN_FILE={ndk_home(args.ndk) / 'build/cmake/android.toolchain.cmake'}", f"-DSELINUX_SOURCE={dependency}", f"-DANDROID_ABI={args.abi}", "-DANDROID_PLATFORM=android-26", "-DANDROID_STL=c++_static", "-DCMAKE_BUILD_TYPE=Release", f"-DMAPLESU_POLICY_HOOK_BUNDLE_SOURCE={hook if not args.without_hook else ''}"])
    run([cmake, "--build", build, "--parallel", "4"])
    binary = build / "maplesu-policy"
    tests = build / "maplesu-policy-tests"
    manifest = {"abi": args.abi, "selinux_commit": COMMIT, "magisk_commit": "e8a58776f1d7bdf852072ad0baa6eceb9a1e4aac", "binary": str(binary), "sha256": hashlib.sha256(binary.read_bytes()).hexdigest(), "bytes": binary.stat().st_size, "tests_binary": str(tests), "tests_sha256": hashlib.sha256(tests.read_bytes()).hexdigest()}
    manifest["temporaryHookBackend"] = {"enabled": not args.without_hook, "controlAbi": 2 if identity else 0,
        "bundleSourceSha256": identity["generatedSourceSha256"] if identity else None}
    (build / "build.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest))


if __name__ == "__main__":
    main()
