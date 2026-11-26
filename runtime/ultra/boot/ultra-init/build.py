#!/usr/bin/env python3
"""Build the static PID1 entry; this does not build or qualify a kernel bridge."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import subprocess


def identity(path):
    data = path.read_bytes()
    return {"path": str(path.resolve()), "bytes": len(data),
            "sha256": hashlib.sha256(data).hexdigest()}


def verify_static_executable(data):
    if len(data) < 64 or data[:7] != b"\x7fELF\x02\x01\x01":
        raise ValueError("ULTRA_INIT_ELF_HEADER")
    kind, machine, version, entry, phoff, _, _, ehsize, stride, count = struct.unpack_from(
        "<HHIQQQIHHH", data, 16)
    if kind not in (2, 3) or machine != 183 or version != 1 or ehsize != 64:
        raise ValueError("ULTRA_INIT_ELF_TARGET")
    if stride != 56 or not 1 <= count <= 128 or phoff < 64 or phoff + count * stride > len(data):
        raise ValueError("ULTRA_INIT_ELF_PROGRAM_HEADERS")
    loads, stacks = [], []
    for index in range(count):
        tag, flags, offset, address, _, filesz, memsz, alignment = struct.unpack_from(
            "<IIQQQQQQ", data, phoff + index * stride)
        if offset > len(data) or filesz > len(data) - offset or address + memsz > 2**64 - 1:
            raise ValueError("ULTRA_INIT_ELF_SEGMENT_RANGE")
        if tag == 3:
            raise ValueError("ULTRA_INIT_ELF_INTERPRETER")
        if tag == 2:
            if filesz % 16:
                raise ValueError("ULTRA_INIT_ELF_DYNAMIC")
            terminated = False
            for position in range(offset, offset + filesz, 16):
                dynamic_tag, _ = struct.unpack_from("<qQ", data, position)
                if dynamic_tag == 0:
                    terminated = True
                    break
                if dynamic_tag == 1:
                    raise ValueError("ULTRA_INIT_ELF_NEEDED")
            if not terminated:
                raise ValueError("ULTRA_INIT_ELF_DYNAMIC")
        if tag == 1:
            if filesz > memsz or flags & 3 == 3:
                raise ValueError("ULTRA_INIT_ELF_LOAD_PERMISSIONS")
            if alignment < 65536 or alignment & (alignment - 1) or (address - offset) % alignment:
                raise ValueError("ULTRA_INIT_ELF_LOAD_ALIGNMENT")
            loads.append((offset, address, filesz, memsz, flags))
        if tag == 0x6474E551:
            stacks.append(flags)
    if stacks != [6] or not any(flags & 1 and address <= entry < address + filesz
                                for _, address, filesz, _, flags in loads):
        raise ValueError("ULTRA_INIT_ELF_ENTRY_OR_STACK")
    for page_size in (4096, 16384, 65536):
        mappings = sorted((address // page_size, (address + memsz + page_size - 1) // page_size)
                          for _, address, _, memsz, _ in loads if memsz)
        if any(left[1] > right[0] for left, right in zip(mappings, mappings[1:])):
            raise ValueError("ULTRA_INIT_ELF_LOAD_OVERLAP")
    return {"architecture": "AArch64", "pageSizes": [4096, 16384, 65536],
            "static": True, "writableExecutableLoads": False, "executed": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--cmake", type=Path, required=True)
    parser.add_argument("--ninja", type=Path, required=True)
    parser.add_argument("--build-dir", type=Path, required=True)
    args = parser.parse_args()
    source = Path(__file__).resolve().parent
    build = args.build_dir.resolve()
    if build == source or source.is_relative_to(build):
        parser.error("build-dir must not contain the source directory")
    if build.exists():
        parser.error("build-dir must be new so a failed build retains no stale success receipt")
    inputs = [source / name for name in ("CMakeLists.txt", "main.c", "handoff.c", "handoff.h", "build.py",
        "legacy_direct_bind.c", "legacy_direct_bind.h", "legacy_direct_loader.c", "legacy_direct_loader.h")]
    inputs += [source.parents[1] / "identity/kernel" / name for name in
               ("manager_deployment_namespace.h", "manager_claim_loader.h")]
    before = [identity(path) for path in inputs]
    toolchain = args.ndk.resolve() / "build/cmake/android.toolchain.cmake"
    tools = [identity(path.resolve()) for path in (args.cmake, args.ninja, toolchain)]
    build.mkdir(parents=True, exist_ok=False)
    commands = [[str(args.cmake.resolve()), "-S", str(source), "-B", str(build), "-G", "Ninja",
                 "-DCMAKE_MAKE_PROGRAM=" + str(args.ninja.resolve()),
                 "-DCMAKE_TOOLCHAIN_FILE=" + str(toolchain), "-DANDROID_ABI=arm64-v8a",
                 "-DANDROID_PLATFORM=android-31", "-DCMAKE_BUILD_TYPE=Release"],
                [str(args.cmake.resolve()), "--build", str(build), "--target", "maplesu-ultra-init", "--", "-j2"]]
    for command in commands:
        subprocess.run(command, check=True)
    artifact = build / "maplesu-ultra-init"
    elf = verify_static_executable(artifact.read_bytes())
    if before != [identity(path) for path in inputs]:
        raise RuntimeError("ULTRA_INIT_SOURCE_CHANGED_DURING_BUILD")
    if tools != [identity(Path(row["path"])) for row in tools]:
        raise RuntimeError("ULTRA_INIT_TOOLS_CHANGED_DURING_BUILD")
    receipt = {"schema": "maplesu-ultra-init-build/1", "sources": before, "tools": tools,
               "commands": commands, "artifact": identity(artifact), "elf": elf,
               "bridgeBuilt": False, "deviceOperations": False, "sdkOrSuQualified": False}
    (build / "ULTRA-INIT-BUILD.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
