"""Replay the production AArch64 bootstrap/materializer in a host emulator."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import struct
import time
import zlib

from elftools.elf.elffile import ELFFile
from elftools.elf.relocation import RelocationSection
from unicorn import Uc, UcError, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE
from unicorn import UC_PROT_READ, UC_PROT_WRITE, UC_PROT_EXEC
from unicorn import arm64_const as registers
from unicorn.arm64_const import UC_ARM64_REG_X0, UC_ARM64_REG_X1
from unicorn.arm64_const import UC_ARM64_REG_X2, UC_ARM64_REG_X3
from unicorn.arm64_const import UC_ARM64_REG_X30, UC_ARM64_REG_SP, UC_ARM64_REG_PC
from unicorn.arm64_const import UC_ARM64_REG_X19, UC_ARM64_REG_X20, UC_ARM64_REG_X21
from unicorn.arm64_const import UC_ARM64_REG_X22, UC_ARM64_REG_X23, UC_ARM64_REG_X24
from unicorn.arm64_const import UC_ARM64_REG_X25, UC_ARM64_REG_X26, UC_ARM64_REG_X27
from unicorn.arm64_const import UC_ARM64_REG_X28, UC_ARM64_REG_X29

ROOT = Path(__file__).resolve().parents[2]
IMAGE, DEST, INPUT, STACK, STOP = (0x100000000, 0xffffffe57c770000, 0x300000000,
                                  0x400000000, 0x500000000)
IMAGE_BYTES, ANCHOR, DESCRIPTOR, CREDENTIAL, TABLE = 0x10000, 0x400, 0x800, 0x900, 0xA00
BOOTSTRAP, MATERIALIZER = 0x1000, 0x2000
MOCKS = {name: 0x3000 + index * 0x100 for index, name in enumerate(
    ("allocate", "release", "nx", "rw", "ro", "x", "cache"))}
VALID_NAME = b"/.msu-ultra-identity-v1/0000000012345678"
KEY = b"Aa1" * 16
RET = bytes.fromhex("c0035fd6")
ANCHOR_FIELDS = ("magic", "version", "self", "page_shift", "materializer", "credential",
                 "allocate", "release", "nx", "rw", "ro", "x", "cache", "allocator",
                 "image_bytes", "descriptor", "descriptor_bytes", "descriptor_crc",
                 "loader", "loader_bytes", "reserved0", "reserved1", "reserved2", "reserved3")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def support_identity(support):
    result = {}
    for name in ("bootstrap", "materializer"):
        blob = (support / f"{name}.bin").read_bytes()
        with (support / f"{name}.elf").open("rb") as source:
            elf = ELFFile(source)
            text = elf.get_section_by_name(".text")
            assert elf["e_machine"] == "EM_AARCH64" and elf["e_entry"] == 0
            assert text["sh_addr"] == 0 and text.data() == blob and len(blob) % 4 == 0
            imports = [s.name for s in elf.get_section_by_name(".symtab").iter_symbols()
                       if s.name and s["st_shndx"] == "SHN_UNDEF"]
            assert not imports
            assert not any(isinstance(s, RelocationSection) and s.num_relocations()
                           for s in elf.iter_sections())
            assert not any(s["sh_flags"] & 2 and s.name != ".text" and s["sh_size"]
                           for s in elf.iter_sections())
        result[name] = {"bytes": len(blob), "sha256": sha(blob), "entry": 0,
                        "imports": [], "relocations": 0}
    return result


def fixture(bootstrap, materializer, page_shift=12, allocator=0, data_offset=0x10000,
            text_pages=1, extra_exec=False):
    image = bytearray(IMAGE_BYTES)
    text, data = RET + bytes.fromhex("1f2003d5") * 3, bytes(range(16))
    image[BOOTSTRAP:BOOTSTRAP + len(bootstrap)] = bootstrap
    image[MATERIALIZER:MATERIALIZER + len(materializer)] = materializer
    image[CREDENTIAL:CREDENTIAL + len(KEY)] = KEY
    image[0x4000:0x4000 + len(text)] = text
    image[0x5000:0x5000 + len(data)] = data
    for offset in MOCKS.values():
        image[offset:offset + 4] = RET
    section_rows = [(0, len(text), len(text) if text_pages == 1 else text_pages << page_shift, 5)]
    if extra_exec:
        section_rows.append((3 << page_shift, 0, 2 << page_shift, 5))
    section_rows.append((data_offset, len(data), 32, 3))
    sections = b"".join(struct.pack("<4I", *row) for row in section_rows)
    descriptor_bytes = 52 + len(sections)
    descriptor = struct.pack("<13I", 0x4355534D, 3, descriptor_bytes, IMAGE_BYTES,
                             data_offset + 32, 0, len(section_rows), 2, 0, 0,
                             zlib.crc32(text + data), IMAGE_BYTES, TABLE) + sections
    table = struct.pack("<4I", 0x4000, len(text), 0x5000, len(data))
    image[DESCRIPTOR:DESCRIPTOR + len(descriptor)] = descriptor
    image[TABLE:TABLE + len(table)] = table
    anchor = dict(zip(ANCHOR_FIELDS, (0x4143534D, 1, ANCHOR, page_shift,
        MATERIALIZER, CREDENTIAL, MOCKS["allocate"], MOCKS["release"], MOCKS["nx"],
        MOCKS["rw"], MOCKS["ro"], MOCKS["x"], MOCKS["cache"], allocator,
        IMAGE_BYTES, DESCRIPTOR, descriptor_bytes,
        zlib.crc32(table, zlib.crc32(descriptor)), MATERIALIZER, len(materializer), 0, 0, 0, 0)))
    return image, anchor


class Replay:
    def __init__(self, image, anchor, *, name=VALID_NAME, null_anchor=False,
                 failures=None, allocation_fails=False, payload_result=1):
        self.image, self.anchor = image, anchor
        self.name, self.null_anchor = name, null_anchor
        self.failures = set(failures or ())
        self.allocation_fails, self.payload_result = allocation_fails, payload_result
        self.events, self.counts, self.materializer_result = [], Counter(), None
        self.tlbi_operands = []
        self.payload, self.allocated, self.freed = None, False, False
        self.rights = {page: UC_PROT_READ | UC_PROT_WRITE for page in range(0, 0x100000, 4096)}
        self.uc = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
        for base, size in ((IMAGE, IMAGE_BYTES), (DEST, 0x100000), (INPUT, 0x1000),
                           (STACK, 0x10000), (STOP, 0x1000)):
            self.uc.mem_map(base, size, UC_PROT_READ | UC_PROT_WRITE | UC_PROT_EXEC)
        struct.pack_into("<24I", self.image, ANCHOR, *(anchor[k] for k in ANCHOR_FIELDS))
        self.uc.mem_write(IMAGE, bytes(self.image))
        self.uc.mem_protect(IMAGE, IMAGE_BYTES, UC_PROT_READ | UC_PROT_EXEC)
        self.uc.mem_write(DEST, b"\xa5" * 0x100000)
        self.uc.mem_protect(DEST, 0x100000, UC_PROT_READ | UC_PROT_WRITE)
        if name is not None:
            self.uc.mem_write(INPUT, name + b"\0")
        self.uc.mem_write(STOP, RET)
        self.uc.reg_write(UC_ARM64_REG_SP, STACK + 0xF000)
        self.uc.reg_write(UC_ARM64_REG_X30, STOP)
        self.uc.reg_write(UC_ARM64_REG_X0, 0 if null_anchor else IMAGE + ANCHOR)
        self.uc.reg_write(UC_ARM64_REG_X1, 0 if name is None else INPUT)
        self.saved_registers = {reg: 0xDEAD0000 + index for index, reg in enumerate((
            UC_ARM64_REG_X19, UC_ARM64_REG_X20, UC_ARM64_REG_X21, UC_ARM64_REG_X22,
            UC_ARM64_REG_X23, UC_ARM64_REG_X24, UC_ARM64_REG_X25, UC_ARM64_REG_X26,
            UC_ARM64_REG_X27, UC_ARM64_REG_X28, UC_ARM64_REG_X29))}
        for reg, value in self.saved_registers.items():
            self.uc.reg_write(reg, value)
        for kind, offset in MOCKS.items():
            self.uc.hook_add(UC_HOOK_CODE, self.mock, kind, IMAGE + offset, IMAGE + offset)
        self.uc.hook_add(UC_HOOK_CODE, self.materialize, None,
                         IMAGE + MATERIALIZER, IMAGE + MATERIALIZER)
        self.uc.hook_add(UC_HOOK_CODE, self.entry, None, DEST, DEST)
        self.uc.hook_add(UC_HOOK_CODE, self.maintenance, None,
                         IMAGE + BOOTSTRAP, IMAGE + MATERIALIZER - 4)

    def maintenance(self, uc, pc, size, ignored):
        word = int.from_bytes(uc.mem_read(pc, 4), "little")
        kind = {0xd5033a9f: "dsb-ishst", 0xd5033b9f: "dsb-ish", 0xd5033fdf: "isb"}.get(word)
        if word & 0xffffffe0 == 0xd5088360:
            operand = uc.reg_read(getattr(registers, f"UC_ARM64_REG_X{word & 31}"))
            self.tlbi_operands.append(operand)
            self.events.append(["tlbi-vaae1is", operand])
        elif kind:
            self.events.append([kind])
        else:
            return
        # Unicorn does not model an ARM MMU or broadcast TLB maintenance.
        uc.reg_write(UC_ARM64_REG_PC, pc + 4)

    def publication_kinds(self):
        return (["dsb-ishst"] + ["tlbi-vaae1is"] * (self.capacity >> self.anchor["page_shift"]) +
                ["dsb-ish", "isb"])

    def arg(self, index):
        return self.uc.reg_read((UC_ARM64_REG_X0, UC_ARM64_REG_X1,
                                 UC_ARM64_REG_X2, UC_ARM64_REG_X3)[index])

    def returned(self, value):
        self.uc.reg_write(UC_ARM64_REG_X0, value & 0xFFFFFFFFFFFFFFFF)
        self.uc.reg_write(UC_ARM64_REG_PC, self.uc.reg_read(UC_ARM64_REG_X30))

    def mock(self, uc, address, size, kind):
        self.counts[kind] += 1
        self.events.append([kind, self.arg(0), self.arg(1)])
        failed = (kind, self.counts[kind]) in self.failures
        if kind == "allocate":
            count = self.arg(1) if self.anchor["allocator"] else self.arg(0)
            if self.anchor["allocator"]:
                assert self.arg(0) == 0, "execmem allocator type changed"
            assert 0 < count <= 0x100000 and count % (1 << self.anchor["page_shift"]) == 0
            self.capacity = count
            self.allocated = not self.allocation_fails
            self.returned(DEST if self.allocated else 0)
        elif kind == "release":
            assert self.arg(0) == DEST and self.allocated and not self.freed
            self.freed = True
            self.returned(0)
        elif kind == "cache":
            assert self.arg(0) == DEST and self.arg(1) == DEST + self.capacity
            publication = self.publication_kinds()
            assert [e[0] for e in self.events[-len(publication) - 1:-1]] == publication, \
                "cache/entry reached before complete TLB publication"
            expected = [((DEST >> 12) & ((1 << 44) - 1)) + (page << (self.anchor["page_shift"] - 12))
                        for page in range(self.capacity >> self.anchor["page_shift"])]
            assert self.tlbi_operands == expected, "wrong TLB range or native-page stride"
            self.returned(0)
        else:
            start, count = self.arg(0), self.arg(1) << self.anchor["page_shift"]
            assert start >= DEST and start + count <= DEST + self.capacity and count > 0
            if not failed:
                for page in range(start - DEST, start - DEST + count, 4096):
                    current = self.rights[page]
                    rights = {"nx": current & ~UC_PROT_EXEC, "rw": current | UC_PROT_WRITE,
                              "ro": current & ~UC_PROT_WRITE, "x": current | UC_PROT_EXEC}[kind]
                    assert rights & (UC_PROT_WRITE | UC_PROT_EXEC) != (UC_PROT_WRITE | UC_PROT_EXEC)
                    self.rights[page] = rights
                    uc.mem_protect(DEST + page, 4096, rights)
            self.returned(-5 if failed else 0)

    def materialize(self, uc, address, size, data):
        self.events.append(["materializer", self.arg(0), self.arg(3)])
        assert self.arg(0) == IMAGE + ANCHOR + 56 and self.arg(1) == IMAGE
        assert self.arg(2) == DEST and self.arg(3) == self.capacity
        at = uc.reg_read(UC_ARM64_REG_X30)
        handle = None

        def finished(machine, pc, count, ignored):
            value = self.arg(0) & 0xFFFFFFFF
            self.materializer_result = value - 0x100000000 if value & 0x80000000 else value
            self.events.append(["materializer-return", self.materializer_result])
            machine.hook_del(handle)

        handle = uc.hook_add(UC_HOOK_CODE, finished, None, at, at)

    def entry(self, uc, address, size, data):
        self.events.append(["entry", self.arg(0), self.arg(1)])
        policy = bytes(uc.mem_read(self.arg(0), 16))
        version, reserved, credential = struct.unpack("<IIQ", policy)
        assert (version, reserved, credential) == (1, 0, IMAGE + CREDENTIAL)
        assert bytes(uc.mem_read(credential, 48)) == KEY
        assert self.arg(1) == INPUT and bytes(uc.mem_read(INPUT, len(VALID_NAME) + 1)) == VALID_NAME + b"\0"
        assert any(e[0] == "cache" for e in self.events[:-1]), "payload preceded instruction cache sync"
        assert bytes(uc.mem_read(DEST, 16)) == RET + bytes.fromhex("1f2003d5") * 3
        section_count = struct.unpack_from("<I", self.image, DESCRIPTOR + 24)[0]
        data_at = next(struct.unpack_from("<I", self.image, DESCRIPTOR + 52 + index * 16)[0]
                       for index in range(section_count)
                       if struct.unpack_from("<I", self.image, DESCRIPTOR + 64 + index * 16)[0] & 2)
        assert bytes(uc.mem_read(DEST + data_at, 16)) == bytes(range(16))
        assert bytes(uc.mem_read(DEST + data_at + 16, 16)) == bytes(16)
        self.payload = {"version": version, "reserved": reserved,
                        "credentialOffset": credential - IMAGE, "namePassedUnchanged": True}
        self.returned(self.payload_result)

    def run(self):
        self.uc.emu_start(IMAGE + BOOTSTRAP, STOP, timeout=5_000_000, count=2_000_000)
        assert self.uc.reg_read(UC_ARM64_REG_PC) == STOP, "bootstrap did not return to its caller"
        assert bytes(self.uc.mem_read(IMAGE, IMAGE_BYTES)) == bytes(self.image), "immutable image changed"
        assert self.uc.reg_read(UC_ARM64_REG_SP) == STACK + 0xF000, "bootstrap unbalanced the stack"
        assert all(self.uc.reg_read(r) == v for r, v in self.saved_registers.items()), "callee registers changed"
        if self.name is not None:
            assert bytes(self.uc.mem_read(INPUT, len(self.name) + 1)) == self.name + b"\0", "original exec name changed"
        if self.materializer_result is not None and self.materializer_result < 0:
            assert bytes(self.uc.mem_read(DEST, self.capacity)) == b"\xa5" * self.capacity
        return {"events": self.events, "materializerResult": self.materializer_result,
                "payload": self.payload, "allocated": self.allocated, "freed": self.freed,
                "tlbiOperands": list(map(hex, self.tlbi_operands)),
                "immutableImageUnchanged": True, "execNameUnchanged": True,
                "calleeRegistersAndStackPreserved": True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build", type=Path, required=True)
    parser.add_argument("--support", type=Path, required=True)
    args = parser.parse_args()
    build, support = args.build.resolve(), args.support.resolve()
    build.mkdir(parents=True, exist_ok=True)
    identities = support_identity(support)
    bootstrap, materializer = ((support / f"{n}.bin").read_bytes()
                               for n in ("bootstrap", "materializer"))
    source_paths = [ROOT / "runtime/ultra/identity/kernel" / n for n in (
        "manager_claim_bootstrap.c", "manager_claim_bootstrap.h", "manager_claim_loader.c",
        "manager_claim_loader.h", "manager_claim_entry.h", "manager_claim_abi.h")]
    before = {str(p.relative_to(ROOT)): sha(p.read_bytes()) for p in source_paths}
    rows, failures = [], []
    started = time.monotonic()

    def check(case_name, *, page_shift=12, allocator=0, data_offset=0x10000,
              text_pages=1, extra_exec=False, alter=None, expected="success", **options):
        image, anchor = fixture(bootstrap, materializer, page_shift, allocator, data_offset,
                                text_pages, extra_exec)
        if alter:
            alter(image, anchor)
        row = {"name": case_name, "pageShift": page_shift, "allocatorKind": allocator}
        try:
            replay = Replay(image, anchor, **options)
            row.update(replay.run())
            kinds = [event[0] for event in row["events"]]
            if expected == "quiet":
                assert not kinds, "invalid request/anchor made an external call"
            elif expected == "success":
                assert row["payload"] and row["freed"] and row["materializerResult"] == 1
                assert kinds == (["allocate", "nx", "rw", "materializer", "materializer-return",
                                  "ro", "x"] + (["x"] if extra_exec else []) + ["rw"] +
                                 replay.publication_kinds() + ["cache", "entry", "nx", "rw", "release"])
            elif expected == "failure":
                assert row["payload"] is None and "cache" not in kinds and not replay.tlbi_operands
                assert row["freed"], "recoverable pre-entry failure did not free staging"
            elif expected == "retained":
                assert row["allocated"] and not row["freed"], "uncertain page permissions reached free"
                if set(options.get("failures", ())) & {("nx", 1), ("rw", 1)}:
                    assert row["payload"] is None and "cache" not in kinds and not replay.tlbi_operands
                else:
                    assert row["payload"], "cleanup-after-entry failure was not exercised"
            elif expected == "allocation-failure":
                assert kinds == ["allocate"] and not row["allocated"] and not row["payload"]
            else:
                raise AssertionError(expected)
            row["passed"] = True
        except (AssertionError, UcError) as error:
            row["passed"], row["error"] = False, str(error) or type(error).__name__
            if "replay" in locals():
                row["events"] = replay.events
            failures.append(case_name)
        rows.append(row)
        print(f"{case_name}: {'PASS' if row['passed'] else 'FAIL ' + row['error']}", flush=True)

    for shift in (12, 14, 16):
        for allocator in (0, 1):
            check(f"valid-page-{shift}-allocator-{allocator}", page_shift=shift, allocator=allocator)
            check(f"multi-exec-page-{shift}-allocator-{allocator}", page_shift=shift, allocator=allocator,
                  data_offset=5 << shift, text_pages=2, extra_exec=True)
        check(f"multi-exec-second-x-failure-{shift}", page_shift=shift, data_offset=5 << shift,
              text_pages=2, extra_exec=True, failures={("x", 2)}, expected="failure")
    for name, value in (("null-name", None), ("empty-name", b""), ("normal-exec", b"/system/bin/id"),
                        ("bad-prefix", VALID_NAME.replace(b"ultra", b"Ultra")),
                        ("uppercase-address", VALID_NAME[:-1] + b"A"),
                        ("nonhex-address", VALID_NAME[:-1] + b"g"),
                        ("zero-address", VALID_NAME[:-16] + b"0" * 16),
                        ("short-name", VALID_NAME[:-1]), ("long-name", VALID_NAME + b"0"),
                        ("very-long-name", VALID_NAME + b"x" * 100)):
        check(name, name=value, expected="quiet")
    check("null-anchor", null_anchor=True, expected="quiet")
    fields = {"magic": 0, "version": 2, "page_shift": 13, "allocator": 2,
              "image_bytes": 95, "descriptor_bytes": 51, "descriptor": IMAGE_BYTES + 4,
              "self": IMAGE_BYTES - 92, "credential": IMAGE_BYTES - 47}
    for field, value in fields.items():
        check(f"anchor-invalid-{field}", alter=lambda i, a, k=field, v=value: a.update({k: v}),
              expected="quiet")
    check("anchor-descriptor-span", alter=lambda i, a: a.update(descriptor=IMAGE_BYTES - 52), expected="quiet")
    for field in ("self", "descriptor", "materializer", "allocate", "release", "nx", "rw", "ro", "x", "cache"):
        check(f"anchor-misaligned-{field}", alter=lambda i, a, k=field: a.update({k: a[k] + 1}),
              expected="quiet")
    for field in ("materializer", "allocate", "release", "nx", "rw", "ro", "x", "cache"):
        check(f"anchor-outside-{field}", alter=lambda i, a, k=field: a.update({k: IMAGE_BYTES}),
              expected="quiet")
    for index in range(4):
        check(f"anchor-reserved-{index}", alter=lambda i, a, n=index: a.update({f"reserved{n}": 1}),
              expected="quiet")
    for bytes_ in (0, 1_048_577):
        check(f"header-memory-{bytes_}", alter=lambda i, a, n=bytes_: struct.pack_into("<I", i, DESCRIPTOR + 16, n),
              expected="quiet")
    check("allocator-null", allocation_fails=True, expected="allocation-failure")
    check("materializer-bad-header", alter=lambda i, a: struct.pack_into("<I", i, DESCRIPTOR, 0),
          expected="failure")
    check("materializer-bad-payload-crc", alter=lambda i, a: i.__setitem__(0x4000, i[0x4000] ^ 1),
          expected="failure")
    for shift, offset in ((12, 32), (14, 4096), (16, 16384)):
        check(f"wx-shared-page-{shift}", page_shift=shift, data_offset=offset, expected="failure")
    for shift in (12, 14, 16):
        for kind, count in (("nx", 1), ("rw", 1), ("ro", 1), ("x", 1), ("rw", 2)):
            check(f"permission-failure-page-{shift}-{kind}-{count}", page_shift=shift,
                  failures={(kind, count)}, expected="failure")
    for failures_ in ({("nx", 2)}, {("rw", 3)}, {("nx", 1), ("nx", 2)}, {("rw", 1), ("rw", 2)}):
        label = "+".join(f"{k}-{v}" for k, v in sorted(failures_))
        check(f"cleanup-retains-{label}", failures=failures_, expected="retained")
    check("payload-negative-result-still-cleans-up", payload_result=-22)
    unchanged = all(sha(p.read_bytes()) == before[str(p.relative_to(ROOT))] for p in source_paths)
    binaries_unchanged = support_identity(support) == identities
    report = {"passed": not failures and unchanged and binaries_unchanged, "cases": len(rows),
              "passedCases": sum(r["passed"] for r in rows), "failedCases": failures,
              "runnerSha256": sha(Path(__file__).read_bytes()),
              "sourceHashes": before, "sourcesUnchanged": unchanged,
              "support": identities, "supportUnchanged": binaries_unchanged,
              "elapsedSeconds": round(time.monotonic() - started, 3), "results": rows,
              "scope": "host Unicorn execution of production AArch64 bootstrap and materializer; kernel APIs and payload entry are explicit mocks",
              "deviceExecuted": False, "kernelExecuted": False, "relayExecuted": False,
              "tlbMaintenanceRecordedAndSkipped": True, "hardwarePanicAcceptance": False}
    (build / "bootstrap.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    (build / "bootstrap.log").write_text("\n".join(
        f"{r['name']}: {'PASS' if r['passed'] else 'FAIL ' + r['error']}" for r in rows) +
        f"\npassed={report['passed']} cases={len(rows)} failures={len(failures)}\n", encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("passed", "cases", "passedCases", "failedCases",
                                            "sourcesUnchanged", "supportUnchanged", "elapsedSeconds")}))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
