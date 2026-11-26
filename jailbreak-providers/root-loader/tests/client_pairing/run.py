"""Pair five real SDK client TUs with compiled real A64 root-loader B1/bootstrap."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys

from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE, UC_HOOK_MEM_WRITE
from unicorn import arm64_const as R

sys.path.insert(0, str(Path(__file__).resolve().parents[4] / "tools"))
from native_toolchain import ndk_bin, ndk_home

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
BASE = 0xffff000040000000
ARENA = BASE + 0x400000
ORDINARY = BASE + 0x500000
STOP, STACK = 0x30000000, 0x30040000
MASK = (1 << 64) - 1
SEAMS = ("copy_in", "copy_out", "read_kernel", "msu_sdk_call_original", "private_cred",
         "root_handover", "take", "reply", "dispatch", "sdk_image_qualified", "allocate_range",
         "memory_nx", "memory_rw", "memory_ro", "memory_x", "random_bytes", "flush_code", "release",
         "register_kprobe", "unregister_kprobe", "msu_boot_sdk_pre", "sdk_lookup")
FORBIDDEN = {"msu_sdk_call_original", "private_cred", "root_handover", "take", "reply", "dispatch"}
TUS = ("runtime/ultra/authorization/kernel_read.cpp",
       "runtime/maplesu-sdk/sdk/src/kernel_mem/module_base_kernel_mem.cpp",
       "runtime/maplesu-sdk/sdk/src/module/module_append_package_parser.cpp",
       "runtime/maplesu-sdk/sdk/src/kernel_request/module_base_kernel_request.cpp",
       "runtime/maplesu-sdk/sdk/src/module/module_current_identity.cpp")


def identity(path):
    data = path.read_bytes()
    return {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class Target:
    def __init__(self, elf, audit, scenario):
        self.u = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
        self.pages, self.calls, self.writes = set(), [], []
        self.symbols = {row["name"]: row["value"] for row in elf.defined_symbols()}
        for section in elf.sections:
            if section["flags"] & 2 and section["size"]:
                self.map(section["addr"], section["size"])
                if section["type"] != 8:
                    self.u.mem_write(section["addr"], elf.section_bytes(section))
        self.map(BASE, 4096)
        self.map(ARENA, 66 * 4096)
        self.map(ORDINARY, 4096)
        self.map(STOP, STACK - STOP)
        self.u.hook_add(UC_HOOK_CODE, self.execute)
        self.u.hook_add(UC_HOOK_MEM_WRITE, self.write)
        self.phase, self.user_ranges = "bootstrap", []
        assert self.call("fixture_bootstrap", audit) == 0
        self.bootstrap_calls = self.calls[:]
        self.descriptor = self.symbols["sdk_request_metadata"]
        self.metadata = bytes(self.u.mem_read(self.descriptor, 48))
        fields = struct.unpack("<IHHQqqqII", self.metadata)
        self.capabilities = 23 if audit else 7
        assert fields[:4] == (0x324d534d, 2, 48, self.capabilities)
        assert self.descriptor + fields[4] == BASE and self.descriptor + fields[5] == ARENA + 4096
        assert fields[6:] == (0, 0, 0)
        self.append = ARENA + 4096
        self.published = 58 if audit else 54
        self.append_bytes = bytes(self.u.mem_read(self.append, self.published))
        assert struct.unpack_from("<I", self.append_bytes)[0] == self.append - BASE
        if audit:
            assert struct.unpack_from("<I", self.append_bytes, 54)[0] == 0x900
        self.key = bytes(self.u.mem_read(self.symbols["root_key"], 48))
        assert not self.u.mem_read(self.symbols["root_key"] + 48, 1)[0]
        self.ordinary = bytes(i % 251 for i in range(600))
        self.u.mem_write(ORDINARY, self.ordinary)
        if scenario == "metadata_version":
            self.u.mem_write(self.descriptor + 4, struct.pack("<H", 3))
        elif scenario == "metadata_length":
            self.u.mem_write(self.descriptor + 6, struct.pack("<H", 47))
        self.scenario = scenario

    def map(self, address, length):
        for page in range(address & ~4095, ((address + length - 1) & ~4095) + 4096, 4096):
            if page not in self.pages:
                self.u.mem_map(page, 4096)
                self.pages.add(page)

    def put(self, pointer, data, role):
        if not data:
            return
        self.map(pointer, len(data))
        self.u.mem_write(pointer, data)
        self.user_ranges.append((pointer, len(data), role))

    def valid_user(self, pointer, size, roles):
        return any(role in roles and pointer >= base and pointer - base <= length and
                   size <= length - (pointer - base) for base, length, role in self.user_ranges)

    def x(self, index):
        return self.u.reg_read(getattr(R, f"UC_ARM64_REG_X{index}"))

    def write(self, uc, access, address, size, value, unused):
        stage = self.symbols["stage_busy"]
        if self.phase != "bootstrap" and address < stage + 4 and stage < address + size:
            self.writes.append([address, size])

    def execute(self, uc, pc, size, unused):
        if pc == STOP:
            uc.emu_stop()
            return
        name = next((name for name in SEAMS if self.symbols[name] == pc), None)
        if name is None:
            return
        assert name not in FORBIDDEN, f"B1 touched {name}"
        a, b, count = self.x(0), self.x(1), self.x(2)
        row = {"function": name, "arg0": a, "arg1": b, "arg2": count}
        self.calls.append(row)
        result = 0
        if name == "sdk_image_qualified":
            result = 1
        elif name == "allocate_range":
            assert a == 66 * 4096
            result = ARENA
        elif name == "random_bytes":
            uc.mem_write(a, bytes(b))
        elif name == "register_kprobe":
            uc.mem_write(a + 16, struct.pack("<Q", BASE + 0x100))
        elif name == "copy_in":
            assert self.valid_user(b, count, {"packet", "request", "read"})
            uc.mem_write(a, bytes(uc.mem_read(b, count)))
        elif name == "read_kernel":
            allowed = ((self.descriptor, 48), (self.append, self.published), (ORDINARY, 600))
            valid = any(b >= start and b - start <= length and count <= length - (b - start)
                        for start, length in allowed)
            if not valid or self.phase == "kernel_fault":
                result = -14
            else:
                uc.mem_write(a, bytes(uc.mem_read(b, count)))
        elif name == "copy_out":
            assert self.valid_user(a, count, {"response", "destination"})
            data = bytes(uc.mem_read(b, count))
            if a == self.response_pointer:
                if count == 48:
                    assert not data[:4].strip(b"\0") and not self.response_writes
                    if self.phase == "response_version":
                        data = data[:4] + struct.pack("<H", 3) + data[6:]
                    elif self.phase == "response_length":
                        data = data[:6] + struct.pack("<H", 47) + data[8:]
                else:
                    assert count == 4 and self.response_writes == [48]
                    assert not any(uc.mem_read(a, 4))
                self.response_writes.append(count)
                uc.mem_write(a, data)
            elif self.phase == "partial_data":
                assert count >= 17
                uc.mem_write(a, data[:17])
                result = -14
            else:
                uc.mem_write(a, data)
        uc.reg_write(R.UC_ARM64_REG_X0, result & MASK)
        uc.reg_write(R.UC_ARM64_REG_PC, self.x(30))

    def call(self, name, argument):
        for index in range(31):
            self.u.reg_write(getattr(R, f"UC_ARM64_REG_X{index}"), 0)
        self.u.reg_write(R.UC_ARM64_REG_X0, argument)
        self.u.reg_write(R.UC_ARM64_REG_SP, STACK)
        self.u.reg_write(R.UC_ARM64_REG_X30, STOP)
        self.u.emu_start(self.symbols[name], STOP + 4, count=3000000)
        assert self.u.reg_read(R.UC_ARM64_REG_PC) == STOP
        result = self.x(0)
        return result if result < 1 << 63 else result - (1 << 64)

    def replay(self, row):
        self.phase, self.calls, self.writes, self.user_ranges = row["phase"], [], [], []
        packet, request, response = (bytes.fromhex(row[key]) for key in ("packet", "request", "response"))
        fields = struct.unpack("<IHHIIQQQQ", request)
        self.response_pointer = fields[6]
        self.response_writes = []
        self.put(row["packetPointer"], packet, "packet")
        self.put(row["requestPointer"], request, "request")
        self.put(self.response_pointer, response, "response")
        source, descriptor = fields[7:]
        destination = length = 0
        if fields[3] == 3:
            read = bytes.fromhex(row["readArgs"])
            destination, length, reserved = struct.unpack("<QII", read)
            assert reserved == 0 and 1 <= length <= 256
            self.put(descriptor, read, "read")
            self.put(destination, bytes.fromhex(row["destination"]), "destination")
        if self.phase == "request_version":
            self.u.mem_write(row["requestPointer"] + 4, struct.pack("<H", 3))
        elif self.phase.startswith("request_length_"):
            size = 47 if self.phase.endswith("short") else 49
            self.u.mem_write(row["requestPointer"] + 6, struct.pack("<H", size))
        result = self.call("fixture_call", row["packetPointer"])
        assert result == -2 and self.response_writes == [48, 4]
        assert not self.writes and not any(self.u.mem_read(self.symbols["stage_busy"], 4))
        actual = bytes(self.u.mem_read(self.response_pointer, 48))
        data = bytes(self.u.mem_read(destination, length)) if length else b""
        response_fields = struct.unpack("<IHHIiQQQII", actual)
        assert response_fields[5] == fields[5]
        counts = Counter(call["function"] for call in self.calls)
        assert not any(counts[name] for name in FORBIDDEN)
        return {"phase": self.phase, "command": fields[3], "requestId": fields[5],
                "source": source, "response": actual.hex(), "data": data.hex(), "calls": self.calls,
                "completionWrites": self.response_writes[:], "credentialStageCalls": 0,
                "stageWrites": self.writes[:]}, actual, data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--ndk-compiler", type=Path, default=None)
    args = parser.parse_args()
    if args.ndk_compiler is None:
        args.ndk_compiler = ndk_bin(ndk_home()) / ("clang.exe" if os.name == "nt" else "clang")
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    source_root, build = out / "source", out / "build"
    source_root.mkdir(exist_ok=True)
    build.mkdir(exist_ok=True)
    loader = "jailbreak-providers/root-loader/"
    sdk = "runtime/maplesu-sdk/sdk/"
    seams = ROOT / sdk / "tools/tests/request_metadata"
    paths = [*TUS, loader + "module/maplesu_boot_sdk.c", loader + "module/boot_sdk_protocol.h",
             loader + "module/boot_sdk_allocator.h", loader + "boot_sdk_abi.h", loader + "build_root_module.py",
             "jailbreak-providers/dirtyfrag/tests/test_l3_arm.py", "runtime/ultra/authorization/request_protocol.h",
             "runtime/ultra/authorization/kernel_read_metadata.h", sdk + "include/module_base_kernel_request.h",
             sdk + "include/maplesu_request_private.h", sdk + "include/private_module_base_kernel_func_executor.h",
             "runtime/maplesu-sdk/include/module_base_kernel_mem.h", "runtime/maplesu-sdk/include/module_err_def.h",
             *[str(path.relative_to(ROOT)).replace("\\", "/") for path in seams.glob("*.h")
               if path.name not in ("module_base_kernel_request.h", "unistd.h")],
             *[str(path.relative_to(ROOT)).replace("\\", "/") for path in HERE.glob("*") if path.is_file()]]
    paths = list(dict.fromkeys(paths))
    before = {name: identity(ROOT / name) for name in paths}
    for name in paths:
        destination = source_root / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / name, destination)
        assert identity(destination) == before[name]
    helper = load("pairing_extract", source_root / "jailbreak-providers/dirtyfrag/tests/test_l3_arm.py")
    builder_text = (source_root / loader / "build_root_module.py").read_text(encoding="utf-8")
    # Use the existing ELF class without importing its unrelated build toolchain.
    elf_text = builder_text[builder_text.index("class Elf:"):builder_text.index("BTF_MAGIC =")]
    elf_namespace = {"struct": struct, "Path": Path, "SHT_SYMTAB": 2,
                     "BuildError": RuntimeError, "rel": str}
    exec(compile(elf_text, "frozen-production-Elf", "exec"), elf_namespace)
    text = (source_root / loader / "module/maplesu_boot_sdk.c").read_text(encoding="utf-8")
    functions = {name: helper.definition(text, name) for name in
                 ("zero", "request_v2_init", "request_v2", "sdk_execve", "bootstrap")}
    assert all(functions.values())
    (build / "functions.inc").write_text("\n".join(functions.values()), encoding="utf-8")
    (build / "seams.S").write_text(".text\n" + "\n".join(
        f".global {name}\n.type {name}, %function\n{name}:\n ret\n.size {name}, .-{name}" for name in SEAMS), encoding="utf-8")
    (build / "link.ld").write_text("ENTRY(fixture_bootstrap)\nSECTIONS { . = 0xffff000040100000; "
        ".text : { *(.text*) } .rodata : { *(.rodata*) } .data : { *(.data*) } "
        ".bss (NOLOAD) : { *(.bss*) *(COMMON) } /DISCARD/ : { *(.comment*) *(.eh_frame*) *(.note*) } }\n")
    commands = []

    def run(argv):
        done = subprocess.run(list(map(str, argv)), capture_output=True, text=True,
                              encoding="utf-8", errors="replace", timeout=90)
        commands.append({"argv": list(map(str, argv)), "exitCode": done.returncode,
                         "stdout": done.stdout, "stderr": done.stderr})
        if done.returncode:
            (out / "failed-commands.json").write_text(json.dumps(commands, indent=2))
            raise AssertionError(done.stderr)
        return done

    run([args.ndk_compiler, "--version"])
    run([args.ndk_compiler, "--target=aarch64-linux-gnu", "-fuse-ld=lld", "-nostdlib", "-ffreestanding",
         "-fno-builtin", "-fno-stack-protector", "-fno-pic", "-no-pie", "-O1", "-fno-inline",
         "-mgeneral-regs-only", "-Wl,-z,max-page-size=4096", "-Wl,-T," + str(build / "link.ld"),
         "-I" + str(build), "-I" + str(source_root / loader / "module"),
         "-I" + str(source_root / "runtime/ultra/authorization"), source_root / HERE.relative_to(ROOT) / "loader_fixture.c",
         build / "seams.S", "-o", build / "loader.elf"])
    image = "sha256:272ada1e61c525116032246df2a790d1ccec1804b7a9e0b09203c5e650dcb78e"
    docker = ["docker", "run", "--rm", "--pull", "never", "--network", "none", "--read-only", "--tmpfs",
              "/tmp:rw,size=32m", "--mount", f"type=bind,source={out},target=/work", "-w", "/work/build", image]
    run(docker + ["g++", "--version"])
    for header in seams.glob("*.h"):
        if header.name not in ("module_base_kernel_request.h", "unistd.h"):
            shutil.copyfile(source_root / header.relative_to(ROOT), build / header.name)
    prefix = "/work/source/"
    run(docker + ["g++", "-std=c++20", "-O1", "-ffunction-sections", "-fdata-sections",
                  "-DMAPLESU_KERNEL_READ_CANONICAL", "-I/work/build", "-I" + prefix + sdk + "include",
                  "-I" + prefix + "runtime/maplesu-sdk/include", "-I" + prefix + "runtime/ultra/authorization",
                  prefix + str(HERE.relative_to(ROOT)).replace("\\", "/") + "/client_fixture.cpp",
                  *[prefix + path for path in TUS], "-Wl,--gc-sections,--wrap=syscall,--wrap=getresuid,--wrap=getresgid",
                  "-o", "client"])
    elf = elf_namespace["Elf"](build / "loader.elf")
    assert not elf.imports()
    sessions = []
    for audit in (0, 1):
        for scenario in ("normal", "metadata_version", "metadata_length"):
            target = Target(elf, audit, scenario)
            client = docker[:2] + ["-i"] + docker[2:] + ["./client"] + ([] if scenario == "normal" else [scenario])
            process = subprocess.Popen(client, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                       text=True, encoding="utf-8")
            initial = [target.key.hex(), f"{BASE} {target.append} {ORDINARY} {target.capabilities} {target.published}",
                       target.append_bytes.hex(), target.ordinary.hex()]
            process.stdin.write("\n".join(initial) + "\n")
            process.stdin.flush()
            rows, simulations = [], []
            try:
                for line in process.stdout:
                    row = json.loads(line)
                    rows.append(row)
                    if row["type"] == "transport":
                        simulation, response, data = target.replay(row)
                        simulations.append(simulation)
                        process.stdin.write(response.hex() + " " + data.hex() + "\n")
                        process.stdin.flush()
                process.stdin.close()
                exit_code = process.wait(timeout=15)
                stderr = process.stderr.read()
            except BaseException:
                process.kill()
                process.wait(timeout=15)
                raise
            name = f"cap{target.capabilities}-{scenario}"
            for suffix, content in (("client.jsonl", rows), ("simulation.jsonl", simulations)):
                (out / f"{name}-{suffix}").write_text("".join(json.dumps(row) + "\n" for row in content))
            summary = next((row for row in rows if row["type"] == "summary"), None)
            sessions.append({"capabilities": target.capabilities, "appendBytes": target.published,
                "scenario": scenario, "exitCode": exit_code, "stderr": stderr, "summary": summary,
                "transports": len(simulations), "metadataAddress": target.descriptor,
                "metadata": target.metadata.hex(), "append": target.append_bytes.hex(),
                "bootstrapCalls": target.bootstrap_calls})
            assert exit_code == 0 and summary and not summary["failures"], sessions[-1]
            assert len(simulations) == summary["transports"]
    frozen_stable = all(identity(source_root / name) == value for name, value in before.items())
    live_after = {name: identity(ROOT / name) for name in paths}
    total_bytes = sum(path.stat().st_size for path in out.rglob("*") if path.is_file())
    assert frozen_stable and total_bytes < 10 * 1024 * 1024
    receipt = {"schema": "root-loader-client-pairing/1", "recordedAtUtc": datetime.now(timezone.utc).isoformat(),
               "passed": True, "sources": before, "liveSourceAfter": live_after, "frozenInputsStable": frozen_stable,
               "liveSourceChanges": [name for name in paths if before[name] != live_after[name]],
               "extractedFunctionsSha256": {name: hashlib.sha256(value.encode()).hexdigest() for name, value in functions.items()},
               "commands": commands, "dockerImage": image, "sessions": sessions,
               "artifacts": {name: identity(build / name) for name in ("loader.elf", "client", "functions.inc")},
               "outputBytes": total_bytes, "completeDeliveryBuilt": False, "deviceAccess": False,
               "execAllocEnabled": False, "partitionWrites": False}
    (out / "receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"passed": True, "sessions": [{k: row[k] for k in ("capabilities", "scenario", "summary")}
                      for row in sessions], "outputBytes": total_bytes, "receipt": str(out / "receipt.json")}))


if __name__ == "__main__":
    main()
