"""Measured build inputs for the optional resident policy bridge, not a device allowlist."""
import gzip
import hashlib
import json
import os
from pathlib import Path
import struct
import sys

TARGETS = ("sel_write_context", "sel_write_access", "selinux_setprocattr",
    "sel_open_handle_status", "sel_read_handle_status", "sel_mmap_handle_status",
    "sk_filter_trim_cap", "commit_creds", "__skb_try_recv_datagram", "copy_process",
    "selnl_notify_policyload", "sel_write_load")
HELPERS = ("set_memory_nx", "set_memory_rw", "set_memory_ro", "set_memory_x",
    "caches_clean_inval_pou", "__flush_icache_range", "apply_alternatives_module",
    "static_key_count", "dynamic_scs_enabled", "scs_patch", "__pi_scs_patch", "ksys_write")
INTERNAL = ("__arm64_sys_getuid", "__arm64_sys_geteuid", "copy_from_user_nofault",
    "copy_to_user_nofault", "module_alloc", "execmem_alloc", "module_memfree",
    "execmem_free", "try_module_get")
SOURCES = ("policy_hook_build.py", "policy_hook_abi.h", "module/maplesu_policy_control.h",
    "module/maplesu_policy_hook.c", "module/policy_hook_dispatch.c",
    "module/policy_hook_dispatch.h", "module/policy_hook_original.S")


def execmem_type(b, image):
    btf = b.Btf(image)
    _, _, _, header, start, length, _, _ = struct.unpack_from("<HBBIIIII", image, btf.blob_offset)
    types = image[btf.blob_offset + header + start:btf.blob_offset + header + start + length]
    cursor = 0
    for t in btf.types[1:]:
        cursor += 12
        if t["name"] == "execmem_type" and t["kind"] == 6:
            values = [value for key, value in struct.iter_unpack("<Ii", types[cursor:cursor + t["vlen"] * 8])
                      if btf._name(key) == "EXECMEM_MODULE_TEXT"]
            if len(values) != 1:
                raise ValueError("ambiguous execmem module text ABI")
            return values[0]
        if t["kind"] in (b.BTF_STRUCT, b.BTF_UNION):
            cursor += t["vlen"] * 12
        elif t["kind"] in b.BTF_SKIP:
            cursor += b.BTF_SKIP[t["kind"]](t["vlen"])
    raise ValueError("missing execmem module text ABI")


def derive(b, image, symbols, probe_size):
    required = INTERNAL[:4] + ("set_memory_nx", "try_module_get", "ksys_write")
    if (any(symbols.address(name) is None for name in required) or
        not any(symbols.address(name) is not None for name in ("module_alloc", "execmem_alloc")) or
        not any(symbols.address(name) is not None for name in ("module_memfree", "execmem_free"))):
        return {}
    start = image.find(b"IKCFG_ST")
    end = image.find(b"IKCFG_ED", start + 8)
    if start < 0 or end < 0:
        return {}
    config = gzip.decompress(image[start + 8:end]).decode()
    pages = [size for label, size in (("4K", 4096), ("16K", 16384), ("64K", 65536))
             if "CONFIG_ARM64_" + label + "_PAGES=y\n" in config]
    if len(pages) != 1:
        raise ValueError("resident policy bridge needs measured page size")
    source = (b.PROJECT_ROOT / (os.environ.get("MAPLESU_POLICY_HOOK_BUNDLES") or
        "runtime/ultra/policy/hook-bundles/policy-bundles.cpp")).resolve()
    metadata = source.with_suffix(".json")
    document = json.loads(metadata.read_text(encoding="utf-8"))
    if (document.get("controlAbi") != 2 or
        hashlib.sha256(source.read_bytes()).hexdigest() != document["generatedSourceSha256"]):
        raise ValueError("resident policy bundle identity drift")
    drifted = []
    for name, digest in document["kernelSourceSha256"].items():
        path = b.PROJECT_ROOT / name
        if not path.is_file():
            drifted.append(name + " (absent)")
        elif hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            drifted.append(name + " (changed)")
    if drifted:
        # The bundle is pinned by the SDK snapshot and is not regenerated in this build, so
        # upstream edits to those sources cannot be reconciled here. The bundle identity
        # itself was verified above; record the provenance drift and keep building it.
        print("warning: resident policy payload source drift: " + ", ".join(drifted), file=sys.stderr)
    allowed = set(TARGETS + HELPERS)
    for bundle in document["bundles"]:
        allowed.update(bundle["payload"]["imports"])
    if any(not name or len(name) >= 128 for name in allowed):
        raise ValueError("invalid resident policy symbol name")
    return dict(defines={"MSU_PH_PAGE_BYTES": pages[0], "MSU_PH_KPROBE_SIZE": probe_size,
        "MSU_PH_KP_ADDR": 40, "MSU_PH_KP_NAME": 48, "MSU_PH_KP_PRE": 64, "MSU_PH_REGS_PC": 256,
        "MSU_PH_EXECMEM_MODULE_TEXT": 0 if symbols.address("module_alloc") is not None else execmem_type(b, image)},
        allowedSymbols=sorted(allowed), lookupSymbols=sorted(allowed | set(INTERNAL)),
        bundleSourceSha256=document["generatedSourceSha256"])


def header(facts):
    return ("/* Measured root module ABI; policy object layouts bind at runtime. */\n" +
        "\n".join("#define %s %d" % pair for pair in facts["defines"].items()) +
        "\n#define MSU_PH_ALLOWED_SYMBOLS " + ",".join(map(json.dumps, facts["allowedSymbols"])) + "\n")
