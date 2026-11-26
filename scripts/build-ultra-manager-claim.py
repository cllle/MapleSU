"""Build the boot-owned manager claim payload and its freestanding support code."""
from argparse import ArgumentParser
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import gzip
import hashlib
import json
import re
import shutil
import subprocess
import time
import uuid

from elftools.elf.elffile import ELFFile
from elftools.elf.relocation import RelocationSection
from policy_btf_abi import Btf
from policy_reference_abi import ReferenceAbi
from policy_claim_routes import RAW_FAMILIES, inspect_selected_catalog

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "runtime/ultra/identity/kernel"
KERNELS = ["android12-5.10", "android13-5.15", "android14-6.1",
           "android15-6.6", "android16-6.12", "android17-6.18"]
MODULE_ROUTE_BINDING = None
MODULE_ROUTE_INPUTS = None
KERNEL_618_SOURCE = None
KERNEL_618_OBJTREE = None
KERNEL_618_TOOLCHAIN = None
SOURCES = [SOURCE / name for name in (
    "install_records.c", "install_records.h", "protected_install_kernel.c",
    "protected_install_kernel.h", "manager_apk_identity.c", "manager_apk_identity.h",
    "manager_claim_entry.c", "manager_claim_entry.h", "manager_claim_abi.h",
    "manager_owner.c", "manager_owner.h", "manager_owner_claim.h",
    "manager_owner_watch.c", "manager_owner_watch.h",
    "manager_resident_host.c", "manager_resident_host.h", "manager_resident_abi.h",
    "manager_management.c", "manager_management.h",
    "manager_uid_gateway.c", "manager_uid_gateway.h", "manager_uid_snapshot.c", "manager_uid_snapshot.h",
    "manager_bytes.c",
    "manager_claim_loader.h")]
PROTOCOL = ROOT / "runtime/ultra/authorization/request_protocol.h"
FRAME = ROOT / "runtime/ultra/authorization/execution_frame.h"
PUBLICATION_PRIVATE_HEADERS = [ROOT / path for path in (
    "runtime/ultra/authorization/publication_return_private.h",
    "runtime/ultra/authorization/legacy_hook_private.h",
    "runtime/ultra/identity/kernel/manager_legacy_hook.inc",
    "runtime/ultra/identity/kernel/manager_legacy_provider.inc",
    "runtime/ultra/identity/kernel/native-text/kprobe_text_owner.h",
)]
PUBLICATION_GATE_INPUTS = [ROOT / path for path in (
    "runtime/maplesu-sdk/sdk/src/func_hook/module_base_kernel_func_hook.cpp",
    "runtime/maplesu-sdk/sdk/src/func_hook/owner_hook_codegen.h",
    "runtime/maplesu-sdk/sdk/src/func_hook/owner_hook_publication.h",
    "runtime/maplesu-sdk/sdk/src/func_hook/owner_publication_gate.h",
    "runtime/maplesu-sdk/sdk/src/func_hook/legacy_hook_publication.h",
    "runtime/maplesu-sdk/sdk/include/maplesu_hook_private.h",
    "runtime/ultra/authorization/publication_return_private.h",
    "runtime/ultra/authorization/legacy_hook_private.h",
    "runtime/ultra/identity/kernel/native-text/kprobe_text_owner.h",
    "runtime/ultra/identity/kernel/native-text/kprobe_text_owner.inc",
    "runtime/ultra/identity/kernel/manager_legacy_hook.inc",
    "runtime/ultra/identity/kernel/manager_legacy_provider.inc",
    "scripts/apply-ultra-kprobe-text-owner.py",
    "jailbreak-providers/root-loader/module/maplesu_boot_sdk.c",
    "jailbreak-providers/root-loader/boot_sdk_build.py",
)]
SUPPORT_SOURCES = [SOURCE / name for name in (
    "manager_claim_bootstrap.c", "manager_claim_bootstrap.h",
    "manager_claim_loader.c", "manager_claim_loader.h", "manager_resident_abi.h",
    "manager_claim_permissions.c")]
FLAGS = ("-Werror -Wno-declaration-after-statement -fno-lto "
         "-fno-sanitize=cfi,shadow-call-stack -fno-stack-protector "
         "-ffixed-x18 -fPIC -mbranch-protection=standard -mno-outline-atomics "
         "-ffunction-sections -fdata-sections -Oz")
EXPORTS = (
    "msu_manager_claim_entry", "msu_resolver_compiled_layout",
    "msu_manager_owner_claim_entry", "msu_manager_owner_init",
    "msu_manager_owner_discard_unpublished", "msu_manager_owner_epoch",
    "msu_manager_owner_claim", "msu_manager_owner_allowed",
    "msu_manager_owner_invalidate", "msu_manager_owner_compiled_layout",
    "msu_manager_owner_watch_compiled_layout",
    "msu_resolver_target_layout", "msu_manager_resident_init",
    "msu_manager_resident_discard", "msu_manager_resident_dispatch",
    "msu_management_compiled_layout", "msu_procattr_compiled_layout", "msu_management_owner_invalidated",
    "msu_credential_replace_current", "msu_publication_enter", "msu_publication_leave",
)
BOOTSTRAP_FUNCTIONS = ("module_alloc", "execmem_alloc", "module_memfree", "execmem_free",
                       "set_memory_nx", "set_memory_rw", "set_memory_ro", "set_memory_x",
                       "sync_icache_aliases", "caches_clean_inval_pou", "__flush_icache_range")
CALLBACKS = {
    "fsnotify_ops.handle_inode_event": "msu_owner_inode_event",
    "fsnotify_ops.freeing_mark": "msu_owner_freeing_mark",
    "fsnotify_ops.free_mark": "msu_owner_free_mark",
}
MANAGEMENT_CALLBACKS = {
    "sched_process_exit": "msu_management_process_exit",
    "sched_process_exec": "msu_management_process_exec",
    "work_struct.func": "msu_management_cleanup",
    "msu_publication_cleanup": "msu_publication_cleanup",
    "msu_credential_replace_current.validate": "msu_management_validate_credentials",
}
UID_CALLBACKS = {
    "msu_uid_snapshot_reclaim_rcu": "msu_uid_snapshot_reclaim_rcu",
    "msu_uid_snapshot_reclaim_work": "msu_uid_snapshot_reclaim_work",
    "msu_uid_gateway_dispatch.validate": "msu_management_validate_credentials",
}
BOOT_EVENTS_CALLBACKS = {
    "file_operations.read": "boot_read", "file_operations.read_iter": "boot_read_iter",
    "file_operations.release": "boot_release", "kprobe.pre_handler": "boot_read_pre",
}
BOOT_EVENTS_LAYOUTS = {
    "file_operations": (4, {"owner": 6, "read": 8, "read_iter": 10, "release": 12}),
    "kprobe": (14, {"symbol_name": 16, "pre_handler": 18}),
    "kiocb": (20, {"ki_filp": 22, "ki_pos": 24, "ki_complete": 26, "ki_flags": 28}),
    "iov_iter": (30, {"count": 32}), "pt_regs": (34, {"regs": 36}),
}
R09_FREEZE = ROOT / ".tmp/p3-p4-owner-20261117/r09/SOURCE-FREEZE.json"
R09_FREEZE_SHA256 = "6866113e8135cd566d3d95a7a803841c17cdf61267763b75a2f5eef24c5fcf45"
R09_CHANGESET = R09_FREEZE.with_name("SOURCE-CHANGESET.json")
R09_CHANGESET_SHA256 = "c5af45fc4f1cebad8181f3bc143fa8058841143fde6061a31561f2df262f1398"
R09_BASELINE = R09_FREEZE.with_name("BASELINE.json")
R09_BASELINE_SHA256 = "30f3a60b491c1e92d838c3b0c9301cfac0cc43cd9415b53350b5ac98581d6927"
R10_FREEZE = ROOT / ".tmp/p3-p4-owner-20261117/r10/SOURCE-FREEZE.json"
R10_FREEZE_SHA256 = "60765de2896756ce75d7b6db8f5ba219b547fa692346f32c91ad7690daadd945"
R10_CHANGESET = R10_FREEZE.with_name("SOURCE-CHANGESET.json")
R10_CHANGESET_SHA256 = "7541371671593a645af6335a37013b8f12256f162247c86eac12f08ad939b114"
R10_BASELINE = R10_FREEZE.with_name("BASELINE.json")
R10_BASELINE_SHA256 = "5a48f8eef0530b2724ef103e0e00a15b3046fc218eadd38d59065a7b70184a8e"
R10_ADOPTION = ROOT / "docs/parallel/2026-11-17/evidence/mainline-integration/r10-b-tools-intake-20261118/INTEGRATION-RECEIPT.json"
R10_ADOPTION_SHA256 = "4a3985e95076d59beb03eb1c6a3f336bed2ad10160cbf8ff1c5b68dcb3c89f50"
P2_ROUTE_DELTA = ROOT / "docs/parallel/2026-11-17/evidence/mainline-integration/p2-r09-source-integration-20261118/P2-HOST-ROUTE-DELTA.json"
P2_ROUTE_DELTA_SHA256 = "887e2deb4661286aad759620f496300e4d13c7b922bc159f03dd4825440a72a5"
SOURCE_MIGRATION = ROOT / ".tmp/mainline-source-integration/current-intake-preparation-20261118/naming-migration/FINAL-SOURCE-MAP.json"
SOURCE_MIGRATION_SHA256 = "d766da5fc7c5e6ad3687735fcaf7604fadfc51562c7dc0d9de6b6afc44add496"
BUILD_TOOLS = [Path(__file__), ROOT / "scripts/policy_claim_routes.py", ROOT / "scripts/policy_object_debug.py", ROOT / "scripts/apply-ultra-kprobe-text-owner.py", ROOT / "scripts/policy_btf_abi.py",
               ROOT / "scripts/policy-kernel-clang.py", ROOT / "scripts/policy_reference_abi.py",
               ROOT / "scripts/policy_payload_publish.py",
               ROOT / "native-inputs/policy/android17-6.18/source/seccomp.c",
               ROOT / "native-inputs/policy/android17-6.18/source/source-receipt.json",
               ROOT / "scripts/policy_reference_image.py", ROOT / "scripts/policy_payload_image_target.py",
               ROOT / "scripts/policy_payload_target.py", ROOT / "tools/specgen/specgen/kallsyms.py"]
RAW_REFERENCE_INPUTS = [ROOT / name for name in ['jailbreak-providers/root-loader/contracts/family-6.18.json', 'native-inputs/kernels/android17-6.18-4f47635b/Image', 'native-inputs/kernels/android17-6.18-4f47635b/symbols.tsv', 'native-inputs/policy/android17-6.18/vmlinux.btf', 'native-inputs/policy/android17-6.18/System.map', 'native-inputs/policy/android17-6.18/target.config', '.tmp/main-sdk-device-current-20261120/kernel-inputs-r2/objtree-618/preparation-inputs.json', '.tmp/p1-kernel-six-source-r1/restore-receipt.json', '.tmp/p1-kernel-six-source-r1/config-replay.json']]
SOURCE_FREEZE = ROOT / ".tmp/p3-p4-owner-20261117/r08/revision3/SOURCE-FREEZE.json"
SOURCE_FREEZE_SHA256 = "23e344c461ba298bbdd11a86b84addf692c0fa8bcead40b33ad76bd1a93bd767"
PARENT_FREEZE = SOURCE_FREEZE.parents[1] / "revision2/SOURCE-FREEZE.json"
PARENT_FREEZE_SHA256 = "0186ab6dbe1bd9ec84c53f5db6d8ce3ffae2624a2188531e1ab4810b764148be"
BASE_FREEZE = SOURCE_FREEZE.parents[1] / "SOURCE-FREEZE.json"
BASE_FREEZE_SHA256 = "8dae87f2a90c7c9746d11421db0b3a7f5e7adc2a7d3044f43eb0f0c641cd071e"
PUBLICATION_CONTACT = ROOT / "docs/parallel/2026-11-17/a-p3-p4/r08/P3-PUBLICATION-CONTACT.md"
PUBLICATION_CONTACT_SHA256 = "c7773d9f68acc2d0b3bbb1a80bd8bc39b469554c70270fbe77088f25127b4037"
P2_HOST_DELTA = ROOT / "docs/parallel/2026-11-17/evidence/mainline-integration/p2-host-context-20261118/SOURCE-DELTA.json"
P2_HOST_DELTA_SHA256 = "3d3ac063116dc62d07e0da54cb72db7306c1571519a7891a8e09412c4d5da658"
SOURCE_CHANGESET = PUBLICATION_CONTACT.parent / "revision3/SOURCE-CHANGESET.json"
SOURCE_CHANGESET_SHA256 = "10ded7e5de69ff03ba6533bff73a19d508542dbb94521676e0cadeb16e510813"
PARENT_CHANGESET = PUBLICATION_CONTACT.parent / "revision2/SOURCE-CHANGESET.json"
PARENT_CHANGESET_SHA256 = "3525bbaa298febaf5d6b9ce44e5830a8b2bf23aa37a5e2077e0c5b37f311160d"
BASE_CHANGESET = PUBLICATION_CONTACT.parent / "SOURCE-CHANGESET.json"
BASE_CHANGESET_SHA256 = "baad4faad873aa4aed4aefd215ed45a3ea3d91031194ef112b39d2db14021bf4"
OWNED_FROZEN_INPUTS = (
    "runtime/pro/authorization/request_protocol.h",
    "runtime/pro/identity/kernel/manager_management.c",
    "runtime/pro/identity/kernel/manager_management.h",
    "runtime/pro/identity/kernel/manager_resident_host.c",
)
MANAGEMENT_FACT_NAMES = (
    "schema", "PAGE_SHIFT", "CAP_LAST_CAP", "EXECMEM_DEFAULT", "MSU_MANAGEMENT_EXECMEM",
    "MSU_MANAGEMENT_EXECMEM_ALLOC_RW", "MSU_MANAGEMENT_PUBLICATION_GATE_VERIFIED",
    "exitProbeArguments", "task_struct", "cred", "group_info", "mm_struct", "mutex",
    "raw_spinlock_t", "work_struct", "kernel_cap_t", "controlBytes", "controlAlign",
    "prepareBytes", "memberBytes",
)
WIRE_FACTS = {
    "requestBytes": ("sizeof(maplesu_request_owner_v3)", 48),
    "responseBytes": ("sizeof(maplesu_response_owner_v3)", 48),
    "managementBytes": ("sizeof(maplesu_management_v3)", 64),
    "metadataBytes": ("sizeof(maplesu_metadata_owner_v3)", 80),
    "publicationControlBytes": ("sizeof(maplesu_publication_control_v3)", 64),
    "publicationControlAlign": ("__alignof__(maplesu_publication_control_v3)", 8),
    "publicationPrepareBytes": ("sizeof(maplesu_publication_prepare_v3)", 64),
    "publicationPrepareAlign": ("__alignof__(maplesu_publication_prepare_v3)", 8),
    "publicationMemberBytes": ("sizeof(maplesu_publication_member_v3)", 24),
    "publicationMemberAlign": ("__alignof__(maplesu_publication_member_v3)", 8),
    "sharedBoolCreateCommand": ("MAPLESU_REQUEST_SHARED_BOOL_CREATE", 16),
    "sharedBoolStoreCommand": ("MAPLESU_REQUEST_SHARED_BOOL_STORE", 17),
    "sharedBoolReleaseCommand": ("MAPLESU_REQUEST_SHARED_BOOL_RELEASE", 18),
    "sharedBoolCapability": ("MAPLESU_CAP_SHARED_BOOL", 16384),
    "sharedBoolRole": ("MAPLESU_PUBLICATION_SHARED_BOOL", 5),
    "baseCapabilities": ("MAPLESU_OWNER_BASE_CAPABILITIES", 2023),
    "optionalCapabilities": ("MAPLESU_OWNER_OPTIONAL_CAPABILITIES", 30720),
    "ownerCapabilities": ("MAPLESU_OWNER_CAPABILITIES", 131047),
    "credProcattrCommand": ("MAPLESU_REQUEST_CRED_PROCATTR", 25),
    "procattrCapability": ("MAPLESU_CAP_PROCATTR", 65536),
    "procattrQueryArg0": ("MAPLESU_OWNER_PROCATTR_QUERY", 129024),
    "procattrCurrent": ("MAPLESU_PROCATTR_CURRENT", 1),
    "procattrExec": ("MAPLESU_PROCATTR_EXEC", 2),
    "procattrMaxBytes": ("MAPLESU_PROCATTR_MAX_BYTES", 4096),
    "procWriteAttemptedEffect": ("MAPLESU_EFFECT_PROC_WRITE_ATTEMPTED", 32768),
    "credChangedEffect": ("MAPLESU_EFFECT_CRED_CHANGED", 65536),
    "uidCreateCommand": ("MAPLESU_REQUEST_UID_CREATE", 19),
    "uidPrepareCommand": ("MAPLESU_REQUEST_UID_PREPARE", 20),
    "uidCommitCommand": ("MAPLESU_REQUEST_UID_COMMIT", 21),
    "uidCancelCommand": ("MAPLESU_REQUEST_UID_CANCEL", 22),
    "uidReadbackCommand": ("MAPLESU_REQUEST_UID_READBACK", 23),
    "uidShutdownCommand": ("MAPLESU_REQUEST_UID_SHUTDOWN", 24),
    "uidSnapshotCapability": ("MAPLESU_CAP_UID_SNAPSHOT", 32768),
    "uidQueryArg0": ("MAPLESU_OWNER_UID_QUERY", 63488),
    "uidRecordBytes": ("sizeof(maplesu_uid_record_v3)", 280),
    "uidMetadataBytes": ("sizeof(maplesu_uid_metadata_v3)", 40),
    "uidPrepareBytes": ("sizeof(maplesu_uid_prepare_v3)", 32),
    "uidReadbackBytes": ("sizeof(maplesu_uid_readback_v3)", 24),
    "uidSchema": ("MAPLESU_UID_SNAPSHOT_SCHEMA", 1),
    "uidMaxRecords": ("MAPLESU_UID_SNAPSHOT_MAX_RECORDS", 4095),
    "queryViews": ("4", 4), "queryBaseArg0": ("0", 0),
    "queryExtendedArg0": ("MAPLESU_OWNER_OPTIONAL_CAPABILITIES", 30720),
    "sharedBoolValueBytes": ("sizeof(u8)", 1),
    "publicationPrepareCommand": ("MAPLESU_REQUEST_PUBLICATION_PREPARE", 12),
    "publicationPublishCommand": ("MAPLESU_REQUEST_PUBLICATION_PUBLISH", 13),
    "publicationDisableCommand": ("MAPLESU_REQUEST_PUBLICATION_DISABLE", 14),
    "publicationRetireCommand": ("MAPLESU_REQUEST_PUBLICATION_RETIRE", 15),
    "referenceDroppedEffect": ("MAPLESU_EFFECT_REFERENCE_DROPPED", 8192),
    "sharedRetainedEffect": ("MAPLESU_EFFECT_SHARED_RETAINED", 16384),
    "publicationControlSchema": ("1", 1),
}
WIRE_STRUCTURES = {
    "uidRecord": ("maplesu_uid_record_v3", {"full_uid": 0, "decision": 4,
        "installation_generation": 8, "profile_revision": 16, "package": 24}),
    "uidMetadata": ("maplesu_uid_metadata_v3", {"schema": 0, "count": 4, "record_bytes": 8,
        "reserved": 12, "content_revision": 16, "host": 24}),
    "uidPrepare": ("maplesu_uid_prepare_v3", {"records_user": 0, "expected_revision": 8,
        "count": 16, "record_bytes": 20, "schema": 24, "reserved": 28}),
    "uidReadback": ("maplesu_uid_readback_v3", {"destination_user": 0, "capacity": 8,
        "reserved": 12, "expected_revision": 16}),
    "request": ("maplesu_request_owner_v3", {"magic": 0, "version": 4, "length": 6,
        "command": 8, "expected_epoch": 12, "request_id": 16, "response_user": 24, "arg0": 32, "arg1": 40}),
    "response": ("maplesu_response_owner_v3", {"completion_magic": 0, "version": 4, "length": 6,
        "command": 8, "status": 12, "request_id": 16, "result": 24, "metadata_address": 32,
        "metadata_length": 40, "metadata_version": 44}),
    "management": ("maplesu_management_v3", {"host": 0, "session": 16, "handle": 24,
        "address": 32, "data_user": 40, "length": 48, "flags": 52, "reserved": 56}),
    "metadata": ("maplesu_metadata_owner_v3", {"common": 0, "owner_schema": 48,
        "owner_state": 52, "owner_epoch": 56, "owner_full_uid": 60, "host": 64}),
    "metadataCommon": ("maplesu_metadata_v2", {"magic": 0, "version": 4, "length": 6,
        "capabilities": 8, "kernel_base_displacement": 16, "legacy_metadata_displacement": 24,
        "layout_displacement": 32, "layout_length": 40, "layout_version": 44}),
    "publicationMember": ("maplesu_publication_member_v3", {"handle": 0, "role": 8, "reserved": 12, "address": 16}),
    "publicationPrepare": ("maplesu_publication_prepare_v3", {"members_user": 0, "member_count": 8,
        "gate_index": 12, "entry_address": 16, "gate_entry_offset": 24, "expected_instruction": 32,
        "flags": 36, "payload_entry": 40, "reserved": 48}),
    "publicationControl": ("maplesu_publication_control_v3", {"publication_id": 0, "enter_function": 8,
        "leave_function": 16, "payload_entry": 24, "entry_address": 32, "resume_address": 40,
        "schema": 48, "length": 52, "epoch": 56, "reserved": 60}),
}
for prefix, (wire_type, fields) in WIRE_STRUCTURES.items():
    for field, expected in fields.items():
        key = prefix + "".join(word.capitalize() for word in field.split("_")) + "Offset"
        WIRE_FACTS[key] = (f"offsetof({wire_type}, {field})", expected)
WIRE_FACTS["publicationPrepareMembersOffset"] = WIRE_FACTS.pop("publicationPrepareMembersUserOffset")
UID_LAYOUT_TYPES = {
    "state": ("struct msu_uid_snapshot_state", "msu_uid_snapshot_state", "manager_uid_snapshot", ("mutation", "published_snapshot", "host", "revision", "closed")),
    "snapshot": ("struct msu_uid_snapshot", "msu_uid_snapshot", "manager_uid_snapshot", ("publication", "references", "reclaim_rcu", "reclaim_work", "metadata", "published", "records")),
    "record": ("struct msu_uid_snapshot_record", "msu_uid_snapshot_record", "manager_uid_snapshot", ("full_uid", "decision", "installation_generation", "profile_revision", "package")),
    "metadata": ("struct msu_uid_snapshot_metadata", "msu_uid_snapshot_metadata", "manager_uid_snapshot", ("schema", "count", "record_bytes", "reserved", "content_revision", "host")),
    "controller": ("struct msu_uid_controller_slot", "msu_uid_controller_slot", "manager_uid_gateway", ("state", "host", "id", "closed")),
    "candidate": ("struct msu_uid_candidate_slot", "msu_uid_candidate_slot", "manager_uid_gateway", ("snapshot", "controller", "id", "session", "expected_revision")),
    "refcount": ("refcount_t", "refcount_struct", "manager_uid_snapshot", ("refs",)),
    "rcuHead": ("struct rcu_head", "callback_head", "manager_uid_snapshot", ("next", "func")),
    "work": ("struct work_struct", "work_struct", "manager_uid_snapshot", ("data", "entry", "func")),
}
UID_FACTS = {"schema": "1", "recordStride": "sizeof(struct msu_uid_snapshot_record)"}
for prefix, (c_type, _, _, members) in UID_LAYOUT_TYPES.items():
    UID_FACTS[prefix + "Bytes"] = f"sizeof({c_type})"
    UID_FACTS[prefix + "Align"] = f"__alignof__({c_type})"
    for member in members:
        key = prefix + "".join(word.capitalize() for word in member.split("_"))
        UID_FACTS[key + "Offset"] = f"offsetof({c_type}, {member})"
        UID_FACTS[key + "Bytes"] = f"sizeof((({c_type} *)0)->{member})" if member != "records" else "0"
UID_CONFIG_NAMES = ("SMP", "PREEMPT_RT", "PREEMPT_COUNT", "THREAD_INFO_IN_TASK", "PREEMPT_RCU",
    "TREE_RCU", "TINY_RCU", "RCU_BOOST", "RCU_NOCB_CPU", "RCU_LAZY", "TASKS_RCU", "TASKS_TRACE_RCU",
    "PROVE_RCU", "DEBUG_OBJECTS_RCU_HEAD", "REFCOUNT_FULL", "DEBUG_LOCK_ALLOC", "DEBUG_OBJECTS_WORK",
    "LOCKDEP", "DEBUG_ATOMIC_SLEEP", "CFI", "CFI_CLANG", "CFI_ICALL_NORMALIZE_INTEGERS")


PROCATTR_LAYOUT_TYPES = {
    "file": ("file", 3, 4, {"f_op": (5, 6), "f_path": (7, 8), "f_inode": (9, 10)}),
    "path": ("path", 11, 12, {"dentry": (13, 14)}),
    "fileOperations": ("file_operations", 15, 16, {"write": (17, 18)}),
    "inode": ("inode", 19, 20, {"i_sb": (21, 22)}),
    "superBlock": ("super_block", 23, 24, {"s_magic": (25, 26)}),
    "dentry": ("dentry", 27, 28, {"d_parent": (29, 30), "d_name": (31, 32)}),
    "qstr": ("qstr", 33, 34, {"len": (35, 36), "name": (37, 38)}),
}
PROCATTR_FUNCTIONS = ("filp_open", "vfs_write", "fput", "proc_pid_attr_write")
PROCATTR_CONFIG_NAMES = ("CONFIG_PROC_FS", "CONFIG_SECURITY", "CONFIG_CFI",
    "CONFIG_CFI_CLANG", "CONFIG_CFI_ICALL_NORMALIZE_INTEGERS")
PROCATTR_FACT_NAMES = ("schema", "kernelVersion", "configured", "fileAlign", "pathAlign",
    "fileOperationsAlign", "inodeAlign", "superBlockAlign", "dentryAlign", "qstrAlign", "procMagic")

# Reuse the unlinked management facts probe for native procattr C declarations.
PROCATTR_FACT_SOURCE = r'''
#include <linux/dcache.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/magic.h>
#include <linux/version.h>
#if defined(CONFIG_PROC_FS) && defined(CONFIG_SECURITY)
extern ssize_t proc_pid_attr_write(struct file *, const char __user *, size_t, loff_t *);
static typeof(&filp_open) msu_build_procattr_open __used = filp_open;
static typeof(&vfs_write) msu_build_procattr_write __used = vfs_write;
static typeof(&fput) msu_build_procattr_put __used = fput;
static typeof(&proc_pid_attr_write) msu_build_procattr_marker __used = proc_pid_attr_write;
_Static_assert(__builtin_types_compatible_p(typeof(&proc_pid_attr_write),
    typeof(((struct file_operations *)0)->write)), "native procattr writer declaration");
#define MSU_BUILD_PROCATTR_CONFIGURED 1
#else
#define MSU_BUILD_PROCATTR_CONFIGURED 0
#endif
const unsigned int msu_procattr_build_facts[] = {
    1, LINUX_VERSION_CODE, MSU_BUILD_PROCATTR_CONFIGURED,
    __alignof__(struct file), __alignof__(struct path),
    __alignof__(struct file_operations), __alignof__(struct inode),
    __alignof__(struct super_block), __alignof__(struct dentry),
    __alignof__(struct qstr), PROC_SUPER_MAGIC,
};
'''

# The actual KCFI-enabled declaration probe retains the marker's compiler typeid.
PROCATTR_CALLBACK_TYPE_SOURCE = r'''
#include <linux/fs.h>
#if defined(CONFIG_PROC_FS) && defined(CONFIG_SECURITY)
extern ssize_t proc_pid_attr_write(struct file *, const char __user *, size_t, loff_t *);
_Static_assert(__builtin_types_compatible_p(typeof(&proc_pid_attr_write),
    typeof(((struct file_operations *)0)->write)), "native procattr marker declaration");
__ADDRESSABLE(proc_pid_attr_write);
#endif
'''


MANAGEMENT_FACT_SOURCE = r'''#include <linux/binfmts.h>
#include <linux/capability.h>
#include <linux/compiler.h>
#include <linux/cpu.h>
#include <linux/cred.h>
#include <linux/memory.h>
#include <linux/mm.h>
#include <linux/mutex.h>
#include <linux/sched.h>
#include <linux/spinlock.h>
#include <linux/workqueue.h>
#if __has_include(<linux/execmem.h>)
#include <linux/execmem.h>
#define MSU_BUILD_EXECMEM 1
#define MSU_BUILD_EXECMEM_DEFAULT EXECMEM_DEFAULT
#else
#include <linux/moduleloader.h>
#define MSU_BUILD_EXECMEM 0
#define MSU_BUILD_EXECMEM_DEFAULT 0
#endif
#if __has_include(<asm/text-patching.h>)
#include <asm/text-patching.h>
#elif __has_include(<asm/patching.h>)
#include <asm/patching.h>
#else
#include <asm/insn.h>
#endif
#include <trace/events/sched.h>
#include "manager_management.h"
_Static_assert(MSU_MANAGEMENT_PUBLICATION_GATE_VERIFIED == 0 ||
    MSU_MANAGEMENT_PUBLICATION_GATE_VERIFIED == 1, "P5 gate binding selection");
_Static_assert(__builtin_types_compatible_p(typeof(text_mutex), struct mutex), "text_mutex type");
_Static_assert(__builtin_types_compatible_p(typeof(&cpus_read_lock), void (*)(void)), "CPU lock type");
_Static_assert(__builtin_types_compatible_p(typeof(&cpus_read_unlock), void (*)(void)), "CPU unlock type");
_Static_assert(__builtin_types_compatible_p(typeof(&aarch64_insn_patch_text),
    int (*)(void **, u32 *, int)), "native text patch type");
#ifdef CONFIG_TRACEPOINTS
typedef void (*msu_build_exit_group_probe)(void *, struct task_struct *, bool);
typedef int (*msu_build_exit_group_register)(msu_build_exit_group_probe, void *);
static typeof(&register_trace_sched_process_exit) msu_build_exit_register __used = register_trace_sched_process_exit;
static typeof(&unregister_trace_sched_process_exit) msu_build_exit_unregister __used = unregister_trace_sched_process_exit;
static typeof(&register_trace_sched_process_exec) msu_build_exec_register __used = register_trace_sched_process_exec;
static typeof(&unregister_trace_sched_process_exec) msu_build_exec_unregister __used = unregister_trace_sched_process_exec;
#define MSU_BUILD_EXIT_ARGS (__builtin_types_compatible_p(typeof(&register_trace_sched_process_exit), msu_build_exit_group_register) ? 3 : 2)
#else
#define MSU_BUILD_EXIT_ARGS 0
#endif
static work_func_t msu_build_work_type __used;
static typeof(&cpus_read_lock) msu_build_cpu_lock __used = cpus_read_lock;
static typeof(&cpus_read_unlock) msu_build_cpu_unlock __used = cpus_read_unlock;
static typeof(&aarch64_insn_patch_text) msu_build_text_patch __used = aarch64_insn_patch_text;
const unsigned int msu_management_native_text_owner[] = { MSU_MANAGEMENT_NATIVE_TEXT_OWNER };
#if MSU_MANAGEMENT_NATIVE_TEXT_OWNER
#include "native-text/kprobe_text_owner.h"
static typeof(&kprobe_text_owner_claim) msu_build_native_claim __used = kprobe_text_owner_claim;
static typeof(&kprobe_text_owner_disable) msu_build_native_disable __used = kprobe_text_owner_disable;
static typeof(&kprobe_text_owner_release) msu_build_native_release __used = kprobe_text_owner_release;
static typeof(&kprobe_text_owner_read) msu_build_native_read __used = kprobe_text_owner_read;
static typeof(&maplesu_legacy_hook_provider_register) msu_build_legacy_register __used = maplesu_legacy_hook_provider_register;
static typeof(&maplesu_legacy_hook_provider_unregister) msu_build_legacy_unregister __used = maplesu_legacy_hook_provider_unregister;
static typeof(&maplesu_legacy_hook_provider_task_exit) msu_build_legacy_task_exit __used = maplesu_legacy_hook_provider_task_exit;
#endif
const unsigned int msu_management_build_facts[] = {
    1, PAGE_SHIFT, CAP_LAST_CAP, MSU_BUILD_EXECMEM_DEFAULT, MSU_BUILD_EXECMEM,
    MSU_MANAGEMENT_EXECMEM_ALLOC_RW, MSU_MANAGEMENT_PUBLICATION_GATE_VERIFIED,
    MSU_BUILD_EXIT_ARGS, __alignof__(struct task_struct), __alignof__(struct cred),
    __alignof__(struct group_info), __alignof__(struct mm_struct), __alignof__(struct mutex),
    __alignof__(raw_spinlock_t), __alignof__(struct work_struct), __alignof__(kernel_cap_t),
    sizeof(maplesu_publication_control_v3), __alignof__(maplesu_publication_control_v3),
    sizeof(maplesu_publication_prepare_v3), sizeof(maplesu_publication_member_v3),
};
'''

MANAGEMENT_FACT_SOURCE += PROCATTR_FACT_SOURCE

DEPENDENCY_EXTRACTOR = r'''from pathlib import Path
import glob
import hashlib
import json
import re
import shutil
import sys

target, kernel = Path(sys.argv[1]), Path(sys.argv[2])
destination = target / "build-dependencies"
destination.mkdir(exist_ok=True)
working_receipt = target / "kbuild-working-directory.txt"
working = Path(working_receipt.read_text().strip())
if not working.is_absolute() or working.resolve() not in (target.resolve(), kernel.resolve()):
    raise ValueError("Actual Kbuild working directory is not the target or kernel objtree")
# Inputs are read only and Kbuild/pahole have completed before this process.
# These caches live only for this capture; every unique input is rehashed below.
identity_cache, wildcard_cache, archived = {}, {}, set()
archive_directory = destination / "files"
archive_directory.mkdir(exist_ok=True)
def stat_key(path):
    value = path.stat()
    return (value.st_dev, value.st_ino, value.st_size, value.st_mtime_ns, value.st_ctime_ns)
def identity(path):
    if path not in identity_cache:
        previous = stat_key(path)
        data = path.read_bytes()
        if previous != stat_key(path) or len(data) != previous[2]:
            raise ValueError("Dependency changed while captured: " + str(path))
        identity_cache[path] = (previous, {"path": str(path), "bytes": len(data),
            "sha256": hashlib.sha256(data).hexdigest()})
    return dict(identity_cache[path][1])
object_names = ("manager_owner_watch", "manager_management", "manager_management_facts", "manager_callback_types", "manager_resident_host", "manager_uid_gateway", "manager_uid_snapshot", "manager_uid_facts")
if json.loads((target / "build-inputs.json").read_text())["suSourceSelection"] is not None:
    su_selection = json.loads((target / "build-inputs.json").read_text())["suSourceSelection"]
    if su_selection["schema"] == "maplesu-p2-su-component-source-selection/1":
        object_names += ("su_install_registry", "su_profile_apply", "su_profile_namespace", "su_profile_execution", "su_profile_resolve", "manager_su_component_facts", "manager_su_profile_facts", "manager_su_namespace_facts", "manager_su_component_callback_types")
    else:
        object_names += ("su_install_registry", "manager_su_facts", "manager_su_callback_types")
if json.loads((target / "build-inputs.json").read_text()).get("coreSourceSelection") is not None:
    object_names += ("manager_task_state", "manager_credential_domain", "manager_core_facts", "manager_core_uid_facts", "manager_core_callback_types")
if json.loads((target / "build-inputs.json").read_text()).get("taskProfileEnabled"):
    object_names += ("manager_task_profile_types",)
if json.loads((target / "build-inputs.json").read_text()).get("runtimeLayoutSourceSelection") is not None:
    object_names += ("manager_dynamic_layout", *json.loads((target / "build-inputs.json").read_text())["runtimeLayoutSourceSelection"]["additionalObjects"])
host_selection = json.loads((target / "build-inputs.json").read_text()).get("hostDeploymentSourceSelection")
if host_selection is not None and host_selection.get("runtimeChannel"):
    object_names += (*host_selection["runtimeObjects"], "manager_host_runtime_types")
for object_name in dict.fromkeys(object_names):
    obj, command = target / (object_name + ".o"), target / ("." + object_name + ".o.cmd")
    if not obj.exists() and object_name == "manager_callback_types":
        continue
    raw = command.read_text()
    joined = raw.replace("\\\n", " ")
    dependencies = re.search(r"^deps_[^\n]*? := (.*)$", joined, re.M)
    source = re.search(r"^source_[^\n]*? := (.*)$", joined, re.M)
    if not dependencies or not source:
        raise ValueError("Missing actual Kbuild dependency/source list: " + object_name)
    expressions = []
    def wildcard(match):
        pattern = match.group(1)
        if pattern not in wildcard_cache:
            wildcard_cache[pattern] = sorted(glob.glob(str(kernel / pattern)))
        matches = wildcard_cache[pattern]
        expressions.append({"expression": match.group(0), "matches": matches})
        return " ".join(matches)
    expanded = re.sub(r"\$\(wildcard\s+([^)]*)\)", wildcard, dependencies.group(1))
    if "$" in expanded:
        raise ValueError("Unresolved Kbuild dependency expression: " + object_name)
    source_path = Path(source.group(1).strip())
    if not source_path.is_absolute():
        source_path = working / source_path
    paths = {Path(token) if Path(token).is_absolute() else working / token
             for token in expanded.split()}
    paths.add(source_path)
    paths.add(kernel / ".config")
    rows = []
    for path in sorted(paths):
        row = identity(path)
        archive = archive_directory / (row["sha256"] + ".bin")
        if row["sha256"] not in archived:
            if not archive.exists():
                shutil.copyfile(path, archive)
            if hashlib.sha256(archive.read_bytes()).hexdigest() != row["sha256"]:
                raise ValueError("Dependency archive identity differs: " + str(path))
            archived.add(row["sha256"])
        row["archivePath"] = str(archive.relative_to(target))
        rows.append(row)
    (destination / (object_name + ".json")).write_text(json.dumps({
        "schemaVersion": 1, "complete": True, "objectIdentity": identity(obj),
        "commandIdentity": identity(command), "sourceIdentity": identity(source_path),
        "dependencies": rows, "configurationWildcards": expressions,
        "workingDirectory": str(working), "workingDirectoryIdentity": identity(working_receipt),
    }, indent=2) + "\n")
for path, (previous, row) in identity_cache.items():
    if previous != stat_key(path) or hashlib.sha256(path.read_bytes()).hexdigest() != row["sha256"]:
        raise ValueError("Dependency changed during capture: " + str(path))
for pattern, matches in wildcard_cache.items():
    if sorted(glob.glob(str(kernel / pattern))) != matches:
        raise ValueError("Configuration wildcard changed during capture: " + pattern)
(target / "dependency-capture-cache.json").write_text(json.dumps({
    "schemaVersion": 1, "scope": "this read-only post-Kbuild capture process only",
    "uniqueInputs": len(identity_cache), "uniqueWildcards": len(wildcard_cache),
    "archivedContents": len(archived), "allUniqueInputsRehashed": True,
    "allWildcardMatchesRechecked": True, "crossInvocationReuse": False,
}, indent=2) + "\n")

'''


def identity(path):
    return {"path": str(path), "bytes": path.stat().st_size, "sha256": sha(path)}


def source_migration_selection(expected_inputs):
    if sha(SOURCE_MIGRATION) != SOURCE_MIGRATION_SHA256:
        raise ValueError("Unified source migration identity changed")
    migration = json.loads(SOURCE_MIGRATION.read_text(encoding="utf-8"))
    if migration["schema"] != "maplesu-unified-current-source-map/1" or not migration["sourceWritersStopped"]:
        raise ValueError("Unified source migration is not a frozen writer checkpoint")
    selected = []
    for relative, expected in expected_inputs.items():
        matches = [row for row in migration["files"] if row["oldPath"] == relative]
        if len(matches) != 1:
            raise ValueError("Missing/ambiguous historical source migration: " + relative)
        row = matches[0]
        if row["beforeSha256"] != expected["sha256"] or row["beforeBytes"] != expected["bytes"]:
            raise ValueError("Migration does not bind its selected historical source: " + relative)
        destination = core_baseline_input(row["newPath"], ROOT / row["newPath"])
        if not destination.resolve().is_relative_to(ROOT.resolve()):
            raise ValueError("Migrated source escaped the workspace: " + relative)
        actual = identity(destination)
        if actual["sha256"] != row["afterSha256"] or actual["bytes"] != row["afterBytes"]:
            raise ValueError("Current migrated source differs from its exact after image: " + relative)
        selected.append({key: row[key] for key in
                         ("oldPath", "newPath", "beforeSha256", "afterSha256", "beforeBytes", "afterBytes")})
    if len({row["newPath"] for row in selected}) != len(selected):
        raise ValueError("Selected historical sources alias one migrated destination")
    return {"identity": identity(SOURCE_MIGRATION), "selectedInputs": selected}


def r10_source_evolution(r09_rows, route_selected):
    for path, expected in ((R10_FREEZE, R10_FREEZE_SHA256), (R10_CHANGESET, R10_CHANGESET_SHA256),
                           (R10_BASELINE, R10_BASELINE_SHA256), (R10_ADOPTION, R10_ADOPTION_SHA256)):
        if sha(path) != expected:
            raise ValueError("R10 source/adoption identity changed: " + str(path))
    frozen = json.loads(R10_FREEZE.read_text(encoding="utf-8"))
    delta = json.loads(R10_CHANGESET.read_text(encoding="utf-8"))
    baseline = json.loads(R10_BASELINE.read_text(encoding="utf-8"))
    adoption = json.loads(R10_ADOPTION.read_text(encoding="utf-8"))
    before = {row["path"]: row for row in baseline["files"]}
    after = {row["path"]: row for row in frozen["files"]}
    changes = {row["path"]: row for row in delta["files"]}
    adopted = {row["path"]: row for row in adoption["files"]
               if row["ownerSelection"] == "A-R10-and-P2-route"}
    host = route_selected["path"]
    actual_changes = {name for name in after if name not in before or any(
        after[name][key] != before[name][key] for key in ("bytes", "sha256"))}
    if frozen["schema"] != "maplesu-p3p4-r10-source-freeze/1" or len(after) != 82 or \
            before.keys() != after.keys() or len(changes) != 8 or delta["deletedFiles"] or \
            frozen["baseline"]["sha256"] != R10_BASELINE_SHA256 or \
            delta["baseline"]["sha256"] != R10_BASELINE_SHA256 or actual_changes != changes.keys() or \
            adopted.keys() != changes.keys() | {host} or not adoption["sourceIntegrated"]:
        raise ValueError("R10 exact freeze/baseline/adoption path set mismatch")
    for relative, row in changes.items():
        if row["newFile"] or row["after"] != after[relative] or any(
                row["before"][key] != before[relative][key] for key in ("bytes", "sha256")):
            raise ValueError("R10 frozen before/after mismatch: " + relative)
        if relative in r09_rows and any(before[relative][key] != r09_rows[relative][key]
                                        for key in ("bytes", "sha256")):
            raise ValueError("R10 baseline does not bind adopted R09 source: " + relative)
    if any(before[host][key] != route_selected[key] for key in ("bytes", "sha256")):
        raise ValueError("R10 route does not bind the P2 R09 context/route after image")
    selected = {}
    for relative, row in adopted.items():
        previous = route_selected if relative == host else before[relative]
        expected = {"path": relative, "bytes": row["afterBytes"], "sha256": row["afterSha256"]}
        if not row["readbackExact"] or any(row["before" + key.capitalize()] != previous[key]
                                          for key in ("bytes", "sha256")) or \
                (relative != host and expected != after[relative]):
            raise ValueError("R10 production adoption does not bind its source evolution: " + relative)
        source = ROOT / row["selectedSource"]
        actual = identity(source)
        if any(actual[key] != expected[key] for key in ("bytes", "sha256")):
            raise ValueError("R10 archived selected source after mismatch: " + str(source))
        selected[relative] = expected
    return {"r10SourceFreezeIdentity": identity(R10_FREEZE),
            "r10ChangeSetIdentity": identity(R10_CHANGESET), "r10BaselineIdentity": identity(R10_BASELINE),
            "r10AdoptionIdentity": identity(R10_ADOPTION),
            "r10SelectedInputs": [selected[name] for name in changes],
            "p2R10RouteEvolution": {"identity": identity(R10_ADOPTION), "selectedInputs": [selected[host]]}}, selected


def upstream_source_freeze():
    for path, expected in ((SOURCE_FREEZE, SOURCE_FREEZE_SHA256),
                           (PARENT_FREEZE, PARENT_FREEZE_SHA256), (BASE_FREEZE, BASE_FREEZE_SHA256),
                           (PUBLICATION_CONTACT, PUBLICATION_CONTACT_SHA256),
                           (SOURCE_CHANGESET, SOURCE_CHANGESET_SHA256),
                           (PARENT_CHANGESET, PARENT_CHANGESET_SHA256), (BASE_CHANGESET, BASE_CHANGESET_SHA256)):
        if sha(path) != expected:
            raise ValueError("Approved A-side R08 input changed: " + str(path))
    frozen = json.loads(SOURCE_FREEZE.read_text(encoding="utf-8"))
    base = json.loads(BASE_FREEZE.read_text(encoding="utf-8"))
    changes = json.loads(BASE_CHANGESET.read_text(encoding="utf-8"))
    baseline = BASE_FREEZE.with_name("BASELINE.json")
    if frozen["generation"] != "R08" or len(frozen["files"]) != 68 or len(changes["changed"]) != 13 or \
            changes["sourceFreezeSha256"] != BASE_FREEZE_SHA256 or \
            sha(baseline) != frozen["baselineSha256"] or frozen["baselineSha256"] != base["baselineSha256"]:
        raise ValueError("A-side R08 freeze/changeset/baseline identity mismatch")
    previous = {row["path"]: row for row in base["files"]}
    revision_inputs = []
    for freeze_path, delta_path, revision, count, parent_digest in (
            (PARENT_FREEZE, PARENT_CHANGESET, "R08-revision2", 2, BASE_FREEZE_SHA256),
            (SOURCE_FREEZE, SOURCE_CHANGESET, "R08-revision3", 3, PARENT_FREEZE_SHA256)):
        current = json.loads(freeze_path.read_text(encoding="utf-8"))
        delta = json.loads(delta_path.read_text(encoding="utf-8"))
        rows = {row["path"]: row for row in current["files"]}
        actual_changes = {name for name in rows if name not in previous or rows[name] != previous[name]}
        expected_changes = {row["path"]: row for row in delta["changedFromParent"]}
        if current["sourceRevision"] != revision or current["parentFreezeSha256"] != parent_digest or \
                delta["sourceRevision"] != revision or delta["parentFreezeSha256"] != parent_digest or \
                delta["sourceFreezeSha256"] != sha(freeze_path) or len(rows) != 68 or \
                rows.keys() != previous.keys() or len(expected_changes) != count or actual_changes != expected_changes.keys():
            raise ValueError("A-side exact revision/parent delta mismatch: " + revision)
        for relative, row in expected_changes.items():
            if rows[relative] != row:
                raise ValueError("Revision delta differs from frozen after image: " + relative)
            if revision == "R08-revision3":
                revision_inputs.append({"path": relative, "sha256": row["sha256"]})
        previous = rows
    frozen_rows = {row["path"]: row for row in frozen["files"]}
    if sha(P2_HOST_DELTA) != P2_HOST_DELTA_SHA256:
        raise ValueError("P2 resident host source delta changed")
    delta = json.loads(P2_HOST_DELTA.read_text(encoding="utf-8"))
    host_paths = {"runtime/pro/identity/kernel/manager_resident_host.c",
                  "runtime/pro/identity/kernel/manager_resident_host.h"}
    rows = delta["files"]
    if delta["schema"] != "maplesu-p2-resident-host-context-source-delta/1" or \
            len(rows) != len(host_paths) or {row["path"] for row in rows} != host_paths:
        raise ValueError("P2 resident host source delta paths/schema mismatch")
    evolved = {}
    for row in rows:
        relative = row["path"]
        if row["after"]["path"] != relative or any(
                row["before"][key] != frozen_rows[relative][key] for key in ("bytes", "sha256")):
            raise ValueError("P2 resident host delta does not bind A-side before: " + relative)
        evolved[relative] = row["after"]
    for path, expected in ((R09_FREEZE, R09_FREEZE_SHA256), (R09_CHANGESET, R09_CHANGESET_SHA256),
                           (R09_BASELINE, R09_BASELINE_SHA256), (P2_ROUTE_DELTA, P2_ROUTE_DELTA_SHA256)):
        if sha(path) != expected:
            raise ValueError("R09 source/evolution identity changed: " + str(path))
    r09 = json.loads(R09_FREEZE.read_text(encoding="utf-8"))
    r09_changes = json.loads(R09_CHANGESET.read_text(encoding="utf-8"))
    r09_base = json.loads(R09_BASELINE.read_text(encoding="utf-8"))
    baseline_rows = {row["path"]: row for row in r09_base["files"]}
    r09_rows = {row["path"]: row for row in r09["files"]}
    changes = r09_changes["files"]
    if r09["schema"] != "maplesu-p3p4-r09-source-freeze/1" or len(r09_rows) != 82 or \
            len(changes) != 12 or r09_changes["deletedFiles"] or \
            r09["baseline"]["sha256"] != R09_BASELINE_SHA256 or \
            r09_changes["baseline"]["sha256"] != R09_BASELINE_SHA256:
        raise ValueError("R09 freeze/changeset/baseline schema mismatch")
    actual_changes = {name for name, row in r09_rows.items() if name not in baseline_rows or any(
        row[key] != baseline_rows[name][key] for key in ("bytes", "sha256"))}
    if actual_changes != {row["path"] for row in changes}:
        raise ValueError("R09 exact changed-path set mismatch")
    r09_selected = []
    for row in changes:
        relative = row["path"]
        if r09_rows[relative] != row["after"] or (row["newFile"] and row["before"] is not None) or \
                (not row["newFile"] and any(row["before"][key] != baseline_rows[relative][key]
                                           for key in ("bytes", "sha256"))):
            raise ValueError("R09 delta before/after mismatch: " + relative)
        r09_selected.append(row["after"])
    for relative in (*OWNED_FROZEN_INPUTS, *(row["path"] for row in revision_inputs)):
        if any(baseline_rows[relative][key] != frozen_rows[relative][key] for key in ("bytes", "sha256")):
            raise ValueError("R09 baseline does not bind adopted R08 source: " + relative)
    route = json.loads(P2_ROUTE_DELTA.read_text(encoding="utf-8"))
    route_rows = route["files"]
    host_c = "runtime/pro/identity/kernel/manager_resident_host.c"
    if route["schema"] != "maplesu-p2-r09-resident-route-source-delta/1" or len(route_rows) != 1 or \
            route_rows[0]["path"] != host_c or any(
                route_rows[0]["before"][key] != evolved[host_c][key] for key in ("bytes", "sha256")):
        raise ValueError("R09 P2 host route does not bind the context-guard after image")
    route_selected = route_rows[0]["after"]
    if route_selected["path"] != host_c:
        raise ValueError("R09 P2 host route path mismatch")
    r10_record, r10_selected = r10_source_evolution(r09_rows, route_selected)
    expected_inputs = {row["path"]: r10_selected.get(row["path"], row) for row in r09_selected}
    expected_inputs.update({relative: r10_selected.get(relative, expected)
                           for relative, expected in {**evolved, host_c: route_selected}.items()})
    expected_inputs.update(r10_selected)
    selected = []
    for relative in OWNED_FROZEN_INPUTS:
        expected = r10_selected.get(relative,
            route_selected if relative == host_c else evolved.get(relative, r09_rows[relative]))
        expected_inputs[relative] = expected
        selected.append({"path": relative, "sha256": expected["sha256"]})
    migration = source_migration_selection(expected_inputs)
    return {"schemaVersion": 1, "generation": "R10", "sourceRevision": "R10",
            "sourceFreezeIdentity": identity(SOURCE_FREEZE), "parentFreezeIdentity": identity(PARENT_FREEZE),
            "baseFreezeIdentity": identity(BASE_FREEZE),
            "baselineIdentity": identity(baseline), "contactIdentity": identity(PUBLICATION_CONTACT),
            "changeSetIdentity": identity(SOURCE_CHANGESET), "parentChangeSetIdentity": identity(PARENT_CHANGESET),
            "baseChangeSetIdentity": identity(BASE_CHANGESET), "selectedOwnedInputs": selected,
            "p2SourceEvolution": {"identity": identity(P2_HOST_DELTA),
                                  "selectedInputs": list(evolved.values())},
            "p2RouteEvolution": {"identity": identity(P2_ROUTE_DELTA), "selectedInputs": [route_selected]},
            "r09SourceFreezeIdentity": identity(R09_FREEZE), "r09ChangeSetIdentity": identity(R09_CHANGESET),
            "r09BaselineIdentity": identity(R09_BASELINE), "r09SelectedInputs": r09_selected,
            **r10_record, "sourceMigration": migration,
            **({"coreLifecycleEvolution": core_source_evolution()} if CORE_SELECTION is not None else {}),
            **({"hostDeploymentEvolution": host_deployment_public_selection()} if HOST_DEPLOYMENT_SELECTION is not None else {}),
            **({"coreRuntimeEvolution": core_runtime_public_selection()} if CORE_RUNTIME_SELECTION is not None else {}),
            **({"suProfileMetadataEvolution": SU_COMPONENT_METADATA_SELECTION} if SU_COMPONENT_METADATA_SELECTION is not None else {}),
            **({"hostDurableEvolution": HOST_DURABLE_SELECTION} if HOST_DURABLE_SELECTION is not None else {}),
            **({"sdkCurrentTaskEvolution": CORE_CURRENT_TASK_SELECTION} if CORE_CURRENT_TASK_SELECTION is not None else {}),
            **({"hostPublicationEvolution": HOST_PUBLICATION_SELECTION} if HOST_PUBLICATION_SELECTION is not None else {}),
            **({"hostSyncEvolution": HOST_SYNC_SELECTION} if HOST_SYNC_SELECTION is not None else {}),
            **({"hostRetirementEvolution": HOST_RETIREMENT_SELECTION} if HOST_RETIREMENT_SELECTION is not None else {}),
            **({"runtimeLayoutEvolution": RUNTIME_LAYOUT_SELECTION} if RUNTIME_LAYOUT_SELECTION is not None else {}),
            "revisionInputs": revision_inputs, "kernelRolePermissions": frozen["kernelRolePermissions"],
            "scope": "R10 exact 8-path adoption and P2 route with complete R08/R09 and P2 evolution chain; not a complete composition freeze"}


def object_btf(path):
    with path.open("rb") as stream:
        section = ELFFile(stream).get_section_by_name(".BTF")
        if section is None:
            raise ValueError("Missing same-build object BTF: " + str(path))
        return Btf(section.data())


def object_provenance(target, name):
    receipt = target / "build-dependencies" / (name + ".json")
    record = json.loads(receipt.read_text(encoding="utf-8"))
    for key, path in (("objectIdentity", target / (name + ".o")),
                      ("commandIdentity", target / ("." + name + ".o.cmd")),
                      ("workingDirectoryIdentity", target / "kbuild-working-directory.txt")):
        if not record.get("complete") or record[key]["sha256"] != sha(path):
            raise ValueError("Stale/incomplete actual object/command provenance: " + name)
    for item in record["dependencies"]:
        path = target / item["archivePath"]
        if path.stat().st_size != item["bytes"] or sha(path) != item["sha256"]:
            raise ValueError("Missing/changed actual dependency content: " + item["path"])
    selection = json.loads((target / "build-inputs.json").read_text(encoding="utf-8"))
    if selection.get("sourceCorrections") != SOURCE_CORRECTIONS:
        raise ValueError("Object provenance does not bind the actual source-correction generation")
    if SOURCE_CORRECTIONS is not None:
        record["sourceCorrections"] = SOURCE_CORRECTIONS
    if selection.get("taskProfileSourceSelection") != TASK_PROFILE_SELECTION:
        raise ValueError("Object provenance does not bind its TaskProfile source generation")
    if selection.get("runtimeLayoutSourceSelection") != RUNTIME_LAYOUT_SELECTION:
        raise ValueError("Object provenance does not bind its runtime layout source generation")
    if RUNTIME_LAYOUT_SELECTION is not None:
        record["runtimeLayoutSourceSelection"] = RUNTIME_LAYOUT_SELECTION
    transform_path = target / "debug-transformations.json"
    if transform_path.is_file():
        transformation = json.loads(transform_path.read_text())
        rows = [row for row in transformation["objects"] if row["object"] == name]
        if len(rows) != 1:
            raise ValueError("Missing actual compiler/debug transformation: " + name)
        row = rows[0]
        archived = Path(row["archivedCompilerOutput"]["path"])
        archived = target / "compiler-objects" / archived.name
        if row.get("allocatedLoadSymbolRelocationCfiUnchanged") is not True or \
                row["semanticsBeforeSha256"] != row["semanticsAfterSha256"] or \
                row["output"]["sha256"] != sha(target / (name + ".o")) or \
                row["compilerCommand"]["sha256"] != sha(target / ("." + name + ".o.cmd")) or \
                row["compilerOutput"]["sha256"] != sha(archived):
            raise ValueError("Changed compiler/debug transformation provenance: " + name)
        record["debugTransformation"] = dict(row, receipt=identity(transform_path),
            tool=transformation["tool"], transformer=transformation["transformer"])
    return record


def object_words(path, name):
    with path.open("rb") as stream:
        elf = ELFFile(stream)
        table = elf.get_section_by_name(".symtab")
        matches = table.get_symbol_by_name(name) if table else None
        if not matches or len(matches) != 1:
            raise ValueError("Missing/ambiguous compiled facts: " + name)
        symbol = matches[0]
        section = elf.get_section(symbol["st_shndx"])
        at = symbol["st_value"] - section["sh_addr"]
        size = symbol["st_size"]
        if at < 0 or size <= 0 or size % 4 or at + size > section["sh_size"]:
            raise ValueError("Invalid compiled facts bounds: " + name)
        data = section.data()[at:at + size]
        return [int.from_bytes(data[pos:pos + 4], "little") for pos in range(0, size, 4)]


def resident_host_type(target, obj, btf):
    if btf.variables.get("host"):
        return btf.variable_members("host"), {
            "method": "same-object-BTF-VAR", "variable": "host",
            "objectIdentity": identity(obj)}
    debug_path = target / "manager_resident_host.debug.o"
    with obj.open("rb") as original_stream, debug_path.open("rb") as debug_stream:
        original, debug = ELFFile(original_stream), ELFFile(debug_stream)

        def retained(elf):
            return [(section.name, section["sh_type"], section["sh_flags"], section["sh_addr"],
                     section["sh_size"], section.data() if section["sh_type"] != "SHT_NOBITS" else b"")
                    for section in elf.iter_sections() if section["sh_flags"] & 2 or section.name == ".BTF"]

        if retained(original) != retained(debug):
            raise ValueError("Decompressed resident debug copy changed allocated/BTF content")

        def host_symbol(elf):
            table = elf.get_section_by_name(".symtab")
            symbols = table.get_symbol_by_name("host") if table else None
            if not symbols or len(symbols) != 1 or symbols[0]["st_info"]["type"] != "STT_OBJECT":
                raise ValueError("Missing/ambiguous resident object host symbol")
            symbol = symbols[0]
            return symbol, elf.get_section(symbol["st_shndx"])

        symbol, section = host_symbol(debug)
        original_symbol, original_section = host_symbol(original)
        if (symbol["st_size"], symbol["st_value"], section.name) != \
                (original_symbol["st_size"], original_symbol["st_value"], original_section.name):
            raise ValueError("Resident host symbol changed in decompressed debug copy")
        dwarf = debug.get_dwarf_info()
        variables = [(cu, die) for cu in dwarf.iter_CUs() for die in cu.iter_DIEs()
                     if die.tag == "DW_TAG_variable" and die.attributes.get("DW_AT_name") and
                     die.attributes["DW_AT_name"].value == b"host" and "DW_AT_location" in die.attributes]
        if len(variables) != 1:
            raise ValueError("Missing/ambiguous actual resident host DWARF variable")
        cu, variable = variables[0]
        unit = cu.get_top_DIE().attributes
        if Path(unit["DW_AT_name"].value.decode()).name != "manager_resident_host.c":
            raise ValueError("Resident host DWARF is not from its selected compilation unit")
        modifiers = ("DW_TAG_typedef", "DW_TAG_const_type", "DW_TAG_volatile_type", "DW_TAG_restrict_type")

        def resolve(die):
            for _ in range(64):
                if die.tag not in modifiers:
                    return die
                die = die.get_DIE_from_attribute("DW_AT_type")
            raise ValueError("Resident host DWARF modifier cycle")

        host_type = resolve(variable.get_DIE_from_attribute("DW_AT_type"))
        if host_type.tag != "DW_TAG_structure_type" or \
                host_type.attributes["DW_AT_byte_size"].value != symbol["st_size"]:
            raise ValueError("Resident DWARF host type differs from the actual ELF object")
        fields = {}
        kinds = {"DW_TAG_structure_type": 4, "DW_TAG_union_type": 5,
                 "DW_TAG_base_type": 1, "DW_TAG_pointer_type": 2}
        for member in host_type.iter_children():
            if member.tag != "DW_TAG_member":
                continue
            attrs = member.attributes
            if any(key in attrs for key in ("DW_AT_bit_size", "DW_AT_bit_offset", "DW_AT_data_bit_offset")):
                raise ValueError("Resident host has unsupported DWARF bitfield")
            name = attrs["DW_AT_name"].value.decode()
            at = attrs["DW_AT_data_member_location"].value
            field = resolve(member.get_DIE_from_attribute("DW_AT_type"))
            size = field.attributes.get("DW_AT_byte_size")
            size = size.value if size else cu["address_size"] if field.tag == "DW_TAG_pointer_type" else None
            field_name = field.attributes.get("DW_AT_name")
            if name in fields or not isinstance(at, int) or not size or at < 0 or at + size > symbol["st_size"]:
                raise ValueError("Resident host DWARF member bounds/uniqueness mismatch")
            fields[name] = (at, size, kinds[field.tag], field_name.value.decode() if field_name else "")
        candidates = []
        for index, entry in enumerate(btf.types):
            if not entry or entry[1] != 4 or entry[3] != symbol["st_size"]:
                continue
            layout = btf.structure_members(index)
            if layout["members"].keys() != fields.keys():
                continue
            members = {btf.name(btf.u32(entry[5] + pos * 12)): btf.u32(entry[5] + pos * 12 + 4)
                       for pos in range(entry[2])}
            shapes = {name: (item["offset"], item["bytes"], btf.types[btf.resolve(members[name])][1],
                             btf.name(btf.types[btf.resolve(members[name])][0]))
                      for name, item in layout["members"].items()}
            if shapes == fields:
                candidates.append((index, layout))
        if len(candidates) != 1:
            raise ValueError("Resident DWARF variable does not bind one exact same-object BTF structure")
        location = variable.attributes["DW_AT_location"]
        from elftools.dwarf.dwarf_expr import DWARFExprParser
        expression = DWARFExprParser(dwarf.structs).parse_expr(location.value)
        if len(expression) != 1:
            raise ValueError("Resident host DWARF location is not one object address")
        if expression[0].op_name == "DW_OP_addrx":
            location_section = ".debug_addr"
            location_offset = unit["DW_AT_addr_base"].value + expression[0].args[0] * cu["address_size"]
        elif expression[0].op_name == "DW_OP_addr" and location.form in ("DW_FORM_exprloc", "DW_FORM_block"):
            location_section = ".debug_info"
            location_offset = location.offset + (len(location.value).bit_length() + 6) // 7 + 1
        else:
            raise ValueError("Unsupported resident host DWARF address form")
        relocations = [(relocations, relocation) for relocations in debug.iter_sections()
                       if isinstance(relocations, RelocationSection) and
                       debug.get_section(relocations["sh_info"]).name == location_section
                       for relocation in relocations.iter_relocations() if relocation["r_offset"] == location_offset]
        if len(relocations) != 1:
            raise ValueError("Resident host DWARF address does not have one actual ELF relocation")
        relocations, relocation = relocations[0]
        address_symbol = debug.get_section(relocations["sh_link"]).get_symbol(relocation["r_info_sym"])
        if relocation["r_info_type"] != 257 or address_symbol["st_shndx"] != symbol["st_shndx"] or \
                address_symbol["st_value"] + relocation["r_addend"] != symbol["st_value"]:
            raise ValueError("Resident host DWARF address does not bind the actual ELF host symbol")
        index, layout = candidates[0]
        if btf.variables.get("host") and btf.variable_members("host") != layout:
            raise ValueError("Resident BTF VAR and DWARF variable layouts disagree")
        return layout, {"method": "same-object-DWARF-variable-to-BTF-type", "btfTypeId": index,
            "debugObjectIdentity": identity(debug_path), "compileUnit": unit["DW_AT_name"].value.decode(),
            "compilationDirectory": unit["DW_AT_comp_dir"].value.decode(),
            "variableDieOffset": variable.offset, "typeDieOffset": host_type.offset,
            "locationSection": location_section, "locationOffset": location_offset,
            "objectSymbolSection": section.name, "objectSymbolValue": symbol["st_value"],
            "objectSymbolBytes": symbol["st_size"], "allHostMemberTypesAndOffsetsMatched": True}


def descriptor_abi(target, claim):
    provenance = object_provenance(target, "manager_resident_host")
    source, = [path for path in SOURCES if path.name == "manager_resident_host.c"]
    source_identity = identity(source)
    copied_identity = identity(target / source.name)
    if any(source_identity[key] != provenance["sourceIdentity"][key] or
           source_identity[key] != copied_identity[key] for key in ("bytes", "sha256")):
        raise ValueError("Resident descriptor source differs from selected/compiled host")
    obj = target / "manager_resident_host.o"
    btf = object_btf(obj)
    host, variable_binding = resident_host_type(target, obj, btf)
    descriptor = btf.typedef_members("maplesu_metadata_owner_v3")
    names = ("metadata", "extended_metadata", "uid_metadata", "procattr_metadata")
    if CORE_SELECTION is not None:
        names += ("task_metadata",)
    views = [host["members"][name] for name in names]
    if descriptor["bytes"] != 80 or any(item["bytes"] != 80 or item["signature"] != descriptor["signature"] for item in views) or \
            any(left["offset"] + 80 > right["offset"] for left, right in zip(views, views[1:])) or \
            views[-1]["offset"] + 80 > host["bytes"]:
        raise ValueError("Actual resident descriptor BTF layout mismatch")
    base, extended, uid, procattr = views[:4]
    with obj.open("rb") as stream:
        object_btf_sha = hashlib.sha256(ELFFile(stream).get_section_by_name(".BTF").data()).hexdigest()
    with claim.open("rb") as stream:
        elf = ELFFile(stream)
        table = elf.get_section_by_name(".symtab")
        symbols = table.get_symbol_by_name("host") if table else None
        if not symbols or len(symbols) != 1:
            raise ValueError("Missing/ambiguous final resident host symbol")
        symbol = symbols[0]
        section = elf.get_section(symbol["st_shndx"])
        if symbol["st_info"]["type"] != "STT_OBJECT" or symbol["st_size"] != host["bytes"] or \
                section["sh_flags"] & 7 != 3 or section["sh_type"] != "SHT_NOBITS" or \
                symbol["st_value"] < section["sh_addr"] or \
                symbol["st_value"] + symbol["st_size"] > section["sh_addr"] + section["sh_size"]:
            raise ValueError("Final resident host is not the actual whole writable/NX BSS object")
        record = {"schemaVersion": 1, "sourceIdentity": source_identity,
                **({"sourceCorrections": SOURCE_CORRECTIONS} if SOURCE_CORRECTIONS is not None else {}),
                "objectIdentity": identity(obj), "commandIdentity": identity(target / ".manager_resident_host.o.cmd"),
                "dependencyReceiptIdentity": identity(target / "build-dependencies/manager_resident_host.json"),
                "dependencies": provenance["dependencies"], "objectBtfSha256": object_btf_sha,
                "variableTypeBinding": variable_binding,
                "hostSymbol": "host", "hostSymbolOffset": symbol["st_value"], "hostBytes": host["bytes"],
                "baseOffset": base["offset"], "extendedOffset": extended["offset"], "uidOffset": uid["offset"],
                "procattrOffset": procattr["offset"], "descriptorBytes": 80,
                "sectionName": section.name, "sectionOffset": section["sh_addr"],
                "sectionBytes": section["sh_size"], "sectionFlags": section["sh_flags"],
                "descriptorTypeSignature": descriptor["signature"], "baseField": dict(base, name="metadata"),
                "extendedField": dict(extended, name="extended_metadata"), "uidField": dict(uid, name="uid_metadata"),
                "procattrField": dict(procattr, name="procattr_metadata"),
                "descriptorFields": descriptor["members"],
                "commonFields": btf.typedef_members("maplesu_metadata_v2")["members"]}
        if CORE_SELECTION is not None:
            record.update(taskOffset=views[4]["offset"], taskField=dict(views[4], name="task_metadata"))
        deployment = host["members"].get("deployment")
        if deployment is not None:
            facts = object_words(obj, "msu_manager_deployment_host_facts")
            if facts != [2, 48, 24] or deployment["bytes"] != facts[2] or                     deployment["offset"] < 0 or deployment["offset"] + deployment["bytes"] > host["bytes"]:
                raise ValueError("Actual native deployment host facts/BTF bounds mismatch")
            record["deploymentNamespaceAbi"] = dict(configVersion=facts[0], configBytes=facts[1],
                recordBytes=facts[2], storageOffset=deployment["offset"], storageBytes=deployment["bytes"],
                storageSignature=deployment["signature"], compiledFactsSymbol="msu_manager_deployment_host_facts")
        return record


def selected_exit_probe(path):
    """Both registration and unregistration must take the same wrapper address."""
    wrappers = {"msu_management_process_exit", "msu_management_process_exit_group_dead"}
    address_types = {257, 275, 276, 277, 311, 312}  # ABS64, ADRP/ADD and GOT address relocations.
    selected, addresses, branches = {}, [], []
    with path.open("rb") as stream:
        elf = ELFFile(stream)
        table = elf.get_section_by_name(".symtab")
        functions = [symbol for symbol in table.iter_symbols()
                     if symbol["st_info"]["type"] == "STT_FUNC" and symbol["st_size"]]
        for relocations in elf.iter_sections():
            if not isinstance(relocations, RelocationSection):
                continue
            symbols = elf.get_section(relocations["sh_link"])
            for relocation in relocations.iter_relocations():
                name = symbols.get_symbol(relocation["r_info_sym"]).name
                if name not in wrappers:
                    continue
                for function in functions:
                    if function["st_shndx"] == relocations["sh_info"] and \
                            function["st_value"] <= relocation["r_offset"] < \
                            function["st_value"] + function["st_size"]:
                        item = {"function": function.name, "target": name,
                                "type": relocation["r_info_type"], "offset": relocation["r_offset"]}
                        if relocation["r_info_type"] in address_types:
                            selected.setdefault(function.name, set()).add(name)
                            addresses.append(item)
                        elif relocation["r_info_type"] in (282, 283):
                            # A wrapper may call the shared implementation; this is not registration.
                            branches.append(item)
    actual = set().union(*selected.values()) if selected else set()
    source = path.with_suffix(".c").read_text(encoding="utf-8")
    callsites = [name for name in ("register_trace_sched_process_exit", "unregister_trace_sched_process_exit")
                 if re.search(r"\b" + name + r"\s*\(\s*MSU_MANAGEMENT_EXIT_PROBE\s*,", source)]
    if len(actual) != 1 or len(callsites) != 2 or \
            selected.get("msu_management_init") != actual or \
            selected.get("msu_management_discard_unpublished") != actual:
        raise ValueError("No unique matching exit register/unregister wrapper address relocations: " + repr(selected))
    return next(iter(actual)), {"functions": {name: sorted(values) for name, values in selected.items()},
                                "addressRelocations": addresses, "directBranches": branches,
                                "callsites": callsites, "sourceSha256": sha(path.with_suffix(".c"))}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()



def verify_runtime_claim(full_path, runtime_path):
    """Bind a debug-preserving link to its load-equivalent runtime carrier."""
    full_data, runtime_data = full_path.read_bytes(), runtime_path.read_bytes()
    with full_path.open("rb") as full_stream, runtime_path.open("rb") as runtime_stream:
        full, runtime = ELFFile(full_stream), ELFFile(runtime_stream)
        btf = full.get_section_by_name(".BTF")
        if btf is None or btf["sh_flags"] & 2 or runtime.get_section_by_name(".BTF") is not None:
            raise ValueError("Runtime packaging requires removal of only non-ALLOC BTF")
        section_coordinates = {"e_shoff", "e_shnum", "e_shstrndx"}
        if {k: v for k, v in full.header.items() if k not in section_coordinates} != \
                {k: v for k, v in runtime.header.items() if k not in section_coordinates}:
            raise ValueError("Runtime packaging changed executable ELF header")
        full_sections = {section.name: section for section in full.iter_sections() if section.name != ".BTF"}
        runtime_sections = {section.name: section for section in runtime.iter_sections()}
        if full_sections.keys() != runtime_sections.keys():
            raise ValueError("Runtime packaging removed a section other than BTF")
        allocated = []
        for name, original in full_sections.items():
            current = runtime_sections[name]
            if original["sh_flags"] & 2:
                keys = ("sh_type", "sh_flags", "sh_addr", "sh_offset", "sh_size", "sh_addralign", "sh_entsize")
                if any(original[key] != current[key] for key in keys) or original.data() != current.data():
                    raise ValueError("Runtime packaging changed allocated section: " + name)
                allocated.append(dict(name=name, address=original["sh_addr"], bytes=original["sh_size"],
                    sha256=hashlib.sha256(original.data()).hexdigest()))
        original_segments, current_segments = list(full.iter_segments()), list(runtime.iter_segments())
        if [dict(segment.header) for segment in original_segments] != \
                [dict(segment.header) for segment in current_segments]:
            raise ValueError("Runtime packaging changed program headers")
        loads = []
        for original, current in zip(original_segments, current_segments):
            if original["p_type"] != "PT_LOAD":
                continue
            # These three section-table coordinates are outside the loaded ABI.
            # They occur in the ELF header covered by the first load segment.
            normalized = []
            for segment in (original, current):
                data = bytearray(segment.data())
                for start, end in ((40, 48), (60, 64)):
                    for position in range(max(start, segment["p_offset"]),
                                          min(end, segment["p_offset"] + len(data))):
                        data[position - segment["p_offset"]] = 0
                normalized.append(bytes(data))
            if normalized[0] != normalized[1]:
                raise ValueError("Runtime packaging changed PT_LOAD contents")
            loads.append(dict(offset=original["p_offset"], address=original["p_vaddr"],
                fileBytes=original["p_filesz"], memoryBytes=original["p_memsz"],
                normalizedSha256=hashlib.sha256(normalized[0]).hexdigest()))
        def symbols(elf, name):
            section = elf.get_section_by_name(name)
            if section is None:
                raise ValueError("Runtime packaging lost symbol table: " + name)
            return sorted((item.name, item["st_value"], item["st_size"], item["st_info"]["bind"],
                item["st_info"]["type"], item["st_other"]["visibility"],
                elf.get_section(item["st_shndx"]).name if isinstance(item["st_shndx"], int)
                else item["st_shndx"]) for item in section.iter_symbols())
        for name in (".dynsym", ".symtab"):
            if symbols(full, name) != symbols(runtime, name):
                raise ValueError("Runtime packaging changed symbols: " + name)
        if not 0 < len(runtime_data) <= 1048576 or len(runtime_data) >= len(full_data):
            raise ValueError("Runtime claim is outside the unchanged product asset size bound")
        receipt = dict(schema="maplesu-runtime-claim-packaging/1", full=identity(full_path),
            runtime=identity(runtime_path), command=["llvm-objcopy", "--remove-section=.BTF",
                str(full_path), str(runtime_path)],
            removedSection=dict(name=".BTF", flags=btf["sh_flags"], bytes=btf["sh_size"],
                sha256=hashlib.sha256(btf.data()).hexdigest()),
            allocatedSections=allocated, loadSegments=loads, symbolTablesUnchanged=True,
            elfEntry=runtime["e_entry"], headerCoordinateExceptions=sorted(section_coordinates))
    (runtime_path.parent / "runtime-packaging.json").write_text(json.dumps(receipt, indent=2) + "\n")
    return receipt


def claim_release_version():
    source = ROOT / "manager/build.gradle.kts"
    version = source.read_text(encoding="utf-8-sig")
    names = re.findall(r'^\s*versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"\s*$', version, re.M)
    codes = re.findall(r'^\s*versionCode\s*=\s*([0-9]+)\s*$', version, re.M)
    if len(names) != 1 or len(codes) != 1 or int(codes[0]) <= 0:
        raise ValueError("Claim publication requires one exact Manager release version")
    return dict(appVersion=names[0], versionCode=int(codes[0]))


def write_runtime_manifest(assets, out, inputs, support, results, module_routes=None):
    """Package every original ABI fact using bounded selected-family gzip chunks."""
    payloads = {}
    for row in results:
        kernel, payload = row["kernel"], row["payload"]
        if "retirementSupport" in row:
            payload = dict(payload, support={"bootstrap": dict(row["retirementSupport"]["bootstrap"],
                path=kernel + "/bootstrap.bin")})
        decoded = (json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
        compressed = gzip.compress(decoded, compresslevel=9, mtime=0)
        if not 0 < len(decoded) <= 32 * 1048576 or not 0 < len(compressed) <= 4 * 1048576:
            raise ValueError("Runtime payload metadata exceeds its bounded encoding: " + kernel)
        # This complete JSON is build evidence; it is byte-identical to inflation.
        (out / kernel / "runtime-payload-metadata.full.json").write_bytes(decoded)
        chunks = []
        for index, start in enumerate(range(0, len(compressed), 524288)):
            path = f"{kernel}/metadata-{index:03d}.json.gz.part"
            if not path.startswith(kernel + "/") or "\\" in path or \
                    Path(path).is_absolute() or any(part in ("", ".", "..") for part in path.split("/")):
                raise ValueError("Runtime metadata chunk path is invalid")
            data = compressed[start:start + 524288]
            (assets / path).write_bytes(data)
            chunks.append(dict(path=path, bytes=len(data), sha256=hashlib.sha256(data).hexdigest()))
        if not 0 < len(chunks) <= 8 or len({row["path"] for row in chunks}) != len(chunks):
            raise ValueError("Runtime metadata chunk count is invalid")
        payloads[kernel] = dict(bytes=payload["bytes"], sha256=payload["sha256"],
            metadataEncoding="gzip-chunks-v1", decodedBytes=len(decoded),
            decodedSha256=hashlib.sha256(decoded).hexdigest(), gzipBytes=len(compressed),
            gzipSha256=hashlib.sha256(compressed).hexdigest(), chunks=chunks)
    variants = {row["kernel"]: dict(row["retirementSupport"]["bootstrap"],
        path=row["kernel"] + "/bootstrap.bin") for row in results if "retirementSupport" in row}
    if variants:
        if set(variants) != set(payloads):
            raise ValueError("Retirement support variant family closure differs")
        for family, bootstrap in variants.items():
            payloads[family]["support"] = {"bootstrap": bootstrap}
    manifest = dict(schemaVersion=2, **claim_release_version(), inputs=inputs, support=support, payloads=payloads)
    if module_routes is not None:
        if set(payloads) != RAW_FAMILIES:
            raise ValueError("Module-route publication requires all four actual raw families")
        manifest["moduleRoutes"] = module_routes
    data = (json.dumps(manifest, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
    if not 0 < len(data) <= 1048576:
        raise ValueError("Runtime manifest is outside the unchanged product asset size bound")
    (assets / "manifest.json").write_bytes(data)
    (out / "runtime-manifest-packaging.json").write_text(json.dumps(dict(
        schema="maplesu-runtime-manifest-packaging/1", manifest=identity(assets / "manifest.json"),
        payloads=payloads, completeMetadataRetained=True), indent=2) + "\n")


def inspect(path):
    with path.open("rb") as source:
        elf = ELFFile(source)
        sections = list(elf.iter_sections())
        symbols = list(elf.get_section_by_name(".dynsym").iter_symbols())
        defined = {s.name: s for s in symbols if s.name and s["st_shndx"] != "SHN_UNDEF"}
        missing = sorted(set(EXPORTS) - defined.keys())
        if missing:
            raise RuntimeError(f"missing claim/owner exports: {missing}")
        imports = sorted({s.name for s in symbols
                          if s.name and s["st_shndx"] == "SHN_UNDEF"})
        entry = next(s["st_value"] for s in symbols
                     if s.name == "msu_manager_claim_entry")
        forbidden = [s for s in imports if "stack_chk" in s or "cfi" in s or
                     s.startswith(("__aarch64_", "__atomic_"))]
        if forbidden:
            raise RuntimeError(f"unexpected instrumentation imports: {forbidden}")
        layouts = {}
        for name, words in (("msu_resolver_compiled_layout", 8),
                            ("msu_manager_owner_compiled_layout", 2),
                            ("msu_management_compiled_layout", None),
                            ("msu_procattr_compiled_layout", 40),
                            ("msu_manager_owner_watch_compiled_layout", None),
                            ("msu_resolver_target_layout", None),
                            *(CORE_LAYOUT_EXPORTS.items() if CORE_SELECTION else ())):
            symbol = defined[name]
            section = elf.get_section(symbol["st_shndx"])
            if name in CORE_LAYOUT_EXPORTS and CORE_SELECTION is not None:
                expected_section = ".msu.task_state.layout" if name == "msu_task_state_compiled_layout" else ".msu.credential_domain.layout"
                if section.name != expected_section or section["sh_flags"] & 7 != 2:
                    raise RuntimeError("CORE final compiled layout is not immutable/NX: " + name)
            offset = symbol["st_value"] - section["sh_addr"]
            size = symbol["st_size"]
            if not size or size % 4 or size > 4096 or (words and size != words * 4) or \
                    offset < 0 or offset + size > section["sh_size"]:
                raise RuntimeError(f"invalid compiled layout export: {name}")
            data = section.data()[offset:offset + size]
            layouts[name] = {
                "offset": symbol["st_value"], "bytes": size,
                "values": [int.from_bytes(data[at:at + 4], "little")
                           for at in range(0, size, 4)],
                "source": "compiled-kernel-headers-not-target-layout-proof",
            }
        if task_profile_sources() is not None:
            layouts["msu_task_profile_compiled_layout"] = task_profile_layout(elf, elf.get_section_by_name(".dynsym"))
        record = {"bytes": path.stat().st_size, "sha256": sha(path),
                "entryOffset": entry,
                "executableBytes": sum(s["sh_size"] for s in sections
                                       if s["sh_flags"] & 4),
                "imports": imports,
                "exports": {name: defined[name]["st_value"] for name in EXPORTS},
                "compiledLayouts": layouts,
                "callbackCfi": inspect_callback_cfi(path),
                "relocationCount": sum(s.num_relocations() for s in sections
                                       if isinstance(s, RelocationSection))}
        if RUNTIME_LAYOUT_SELECTION is not None:
            record["runtimeLayout"] = runtime_layout_record(path, elf, defined)
            record["sessionAbi"] = session_abi_record(path, elf)
            record["bootEventsAbi"] = boot_events_abi_record(path, elf)
            record["ksuCompatAbi"] = ksu_compat_abi_record(path, elf)
            if runtime_profiles_enabled():
                record["runtimeProfiles"] = runtime_profiles_record(path, elf, defined)
        return record


def inspect_callback_cfi(path):
    record = {"schemaVersion": 1, "claimSha256": sha(path), "mode": "unknown",
              "supported": False, "normalizeIntegers": False, "callbacks": {}, "missing": []}
    config_path = path.parent / "msu_resolver_build_config.json"
    if config_path.is_file():
        record["kernelConfig"] = json.loads(config_path.read_text(encoding="utf-8")).get("cfiConfig", {})
    groups = {"manager_owner_watch": dict(CALLBACKS),
              "manager_management": dict(MANAGEMENT_CALLBACKS, **{"msu_uid_gateway_dispatch.validate": UID_CALLBACKS["msu_uid_gateway_dispatch.validate"]}),
              "manager_uid_snapshot": {key: value for key, value in UID_CALLBACKS.items() if key != "msu_uid_gateway_dispatch.validate"}}
    try:
        selected, evidence = selected_exit_probe(path.parent / "manager_management.o")
        groups["manager_management"]["sched_process_exit"] = selected
        record["exitSelection"] = {"function": selected, "relocations": evidence}
    except (OSError, ValueError) as error:
        record["missing"].append(str(error))
    modes, normalization = set(), set()
    with path.open("rb") as source:
        elf = ELFFile(source)
        table = elf.get_section_by_name(".symtab")
        symbols = {symbol.name: symbol for symbol in table.iter_symbols()} if table else {}
        for object_name, callbacks in groups.items():
            object_path = path.parent / (object_name + ".o")
            command_path = path.parent / ("." + object_name + ".o.cmd")
            dependencies = path.parent / "build-dependencies" / (object_name + ".json")
            if not all(item.is_file() for item in (object_path, command_path, dependencies)):
                record["missing"].append("Missing same-build object/command/dependencies: " + object_name)
                continue
            try:
                object_provenance(path.parent, object_name)
            except (OSError, KeyError, ValueError) as error:
                record["missing"].append(str(error))
            command = command_path.read_text(encoding="utf-8").splitlines()[0]
            tokens = command.split()
            normalized = any(token in ("-fsanitize-cfi-icall-normalize-integers",
                "-fsanitize-cfi-icall-experimental-normalize-integers") for token in tokens)
            normalization.add(normalized)
            enabled, present, legacy = False, False, False
            for token in tokens:
                if token.startswith(("-fsanitize=", "-fno-sanitize=")):
                    values = token.split("=", 1)[1].split(",")
                    if "kcfi" in values:
                        present |= token.startswith("-fsanitize=")
                        enabled = token.startswith("-fsanitize=")
                    legacy |= token.startswith("-fsanitize=") and "cfi" in values
            mode = "kcfi" if present else "legacy-lto-cfi" if legacy else "none"
            modes.add(mode)
            if present and not enabled:
                record["missing"].append("Actual compilation disabled kernel KCFI: " + object_name)
            if mode == "legacy-lto-cfi":
                record["missing"].append("Legacy LTO CFI requires formal module/shadow registration: " + object_name)
            for callback, name in callbacks.items():
                function = symbols.get(name)
                if function is None or function["st_info"]["type"] != "STT_FUNC":
                    record["missing"].append("Missing actual callback symbol: " + name)
                    continue
                item = {"function": name, "entryOffset": function["st_value"], "mode": mode,
                        "originKey": callback, "normalizeIntegers": normalized,
                        "objectIdentity": identity(object_path), "commandIdentity": identity(command_path),
                        "buildCommandSha256": sha(command_path), "dependencyReceiptSha256": sha(dependencies)}
                if mode == "kcfi":
                    probe = path.parent / "build-dependencies/manager_callback_types.json"
                    if not probe.is_file():
                        record["missing"].append("Missing actual KCFI declaration probe dependencies")
                    else:
                        try:
                            object_provenance(path.parent, "manager_callback_types")
                        except (OSError, KeyError, ValueError) as error:
                            record["missing"].append(str(error))
                        item["typeProbeDependencyIdentity"] = identity(probe)
                record["callbacks"][callback] = item
                if object_name == "manager_owner_watch":
                    record["buildCommandSha256"] = sha(command_path)
                if mode != "kcfi":
                    continue
                type_name = "__kcfi_typeid_" + name
                type_symbol = symbols.get(type_name)
                if type_symbol is None or type_symbol["st_shndx"] != "SHN_ABS":
                    record["missing"].append("Missing absolute KCFI type symbol: " + type_name)
                    continue
                section = elf.get_section(function["st_shndx"])
                offset = function["st_value"] - section["sh_addr"]
                if offset < 4 or offset > section["sh_size"]:
                    record["missing"].append("Missing callback KCFI prefix: " + name)
                    continue
                prefix = int.from_bytes(section.data()[offset - 4:offset], "little")
                typeid = type_symbol["st_value"] & 0xffffffff
                item.update(typeidSymbol=type_name, typeid=typeid, prefixWord=prefix,
                            prefixOffset=function["st_value"] - 4, verified=prefix == typeid)
                if prefix != typeid:
                    record["missing"].append("Callback KCFI prefix/typeid mismatch: " + name)
    if len(modes) == 1 and len(normalization) == 1:
        record["mode"] = modes.pop()
        record["normalizeIntegers"] = normalization.pop()
    else:
        record["missing"].append("Callback objects use inconsistent CFI policies")
    record["supported"] = not record["missing"]
    return record


def kernel_environment(kernel, out):
    mounts = ["--mount", f"type=bind,source={ROOT},target=/source,readonly",
              "--mount", f"type=bind,source={out},target=/output"]
    if kernel == "android17-6.18":
        baseline = ROOT / ".deps" / "maplesu-kernel-618"
        source = KERNEL_618_SOURCE or baseline / "source"
        prepared = KERNEL_618_OBJTREE or ROOT / ("work/ultra-native-manager-claim-20261113/"
                                                "prototype/android17-6.18/kdir")
        mounts += ["--mount", f"type=bind,source={source},target=/kernel,readonly",
                   "--mount", f"type=bind,source={prepared},target=/kernel-output"]
        compiler = "clang"
        if KERNEL_618_TOOLCHAIN is not None:
            mounts += ["--mount", f"type=bind,source={KERNEL_618_TOOLCHAIN},target=/kernel-toolchain,readonly"]
            compiler = "/kernel-toolchain/lib/llvm-22/bin/clang"
        make = (f"make -C /kernel O=/kernel-output M=/output/{kernel} ARCH=arm64 "
                f'LLVM=1 CC="python3 /source/scripts/policy-kernel-clang.py {compiler}" '
                "LOCALVERSION=-maplesu-policy-ci")
        return (make, mounts, "android16-6.12", "/kernel/include/linux/fs_struct.h",
                "/kernel-output/vmlinux")
    return (f"make -C /opt/ddk/kdir/{kernel} M=/output/{kernel} ARCH=arm64 LLVM=1",
            mounts, kernel, f"/opt/ddk/kdir/{kernel}/source/include/linux/fs_struct.h",
            f"/opt/ddk/kdir/{kernel}/vmlinux")


REFERENCE_EXTRACTOR = r'''from pathlib import Path
import hashlib
import json
import struct
import sys

source, selection, output = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
wanted = set(json.loads(selection.read_text()))
core_names = wanted & {"seccomp_filter_release", "security_secctx_to_secid", "__arm64_sys_setns", "__arm64_sys_unshare", "__arm64_sys_close", "path_mount", "get_task_exe_file", "vfs_getxattr", "vfs_setxattr", "vfs_fsync"}
su_data_names = wanted & {"init_cred", "init_pid_ns", "mntns_operations"}
with source.open("rb") as stream:
    file_bytes = source.stat().st_size
    def read_at(offset, size):
        if offset < 0 or size < 0 or offset > file_bytes or size > file_bytes - offset:
            raise ValueError("ELF section outside reference vmlinux")
        stream.seek(offset)
        value = stream.read(size)
        if len(value) != size:
            raise ValueError("Short read of reference vmlinux")
        return value
    header = read_at(0, 64)
    if header[:6] != b"\x7fELF\x02\x01" or struct.unpack_from("<H", header, 18)[0] != 183:
        raise ValueError("Reference requires little-endian AArch64 ELF64")
    section_at = struct.unpack_from("<Q", header, 40)[0]
    stride, count, names_index = struct.unpack_from("<HHH", header, 58)
    if stride != 64 or not count or names_index >= count:
        raise ValueError("Unsupported reference ELF section table")
    section_bytes = read_at(section_at, stride * count)
    sections = [struct.unpack_from("<IIQQQQIIQQ", section_bytes, at * stride) for at in range(count)]
    def bytes_of(section):
        return read_at(section[4], section[5])
    def string(table, at):
        return table[at:table.index(0, at)].decode("utf-8")
    names = bytes_of(sections[names_index])
    named = {string(names, section[0]): section for section in sections}
    if ".BTF" not in named:
        raise ValueError("Actual reference vmlinux has no .BTF section")
    btf = bytes_of(named[".BTF"])
    symtab = named.get(".symtab")
    if symtab is None or symtab[1] != 2 or symtab[9] != 24 or symtab[6] >= count:
        raise ValueError("Actual reference vmlinux has no valid ELF64 .symtab")
    symbols, strings = bytes_of(symtab), bytes_of(sections[symtab[6]])
    selected, marker_symbols = [], []
    symbol_extents = {}
    core_marker_symbols = {name: [] for name in core_names}
    su_data_symbols = {name: [] for name in su_data_names}
    for at in range(0, len(symbols), 24):
        name, info, other, index, value, size = struct.unpack_from("<IBBHQQ", symbols, at)
        name = string(strings, name)
        if name not in wanted:
            continue
        kind = {1: "OBJECT", 2: "FUNC"}.get(info & 15, "TYPE_" + str(info & 15))
        if name == "proc_pid_attr_write" and index != 0:
            marker_symbols.append((at // 24, info & 15, index, value, size))
        if name in su_data_names and index != 0:
            su_data_symbols[name].append((at // 24, info & 15, index, value, size))
        if name in core_names and index != 0:
            core_marker_symbols[name].append((at // 24, info & 15, index, value, size))
        if index != 0 and index < len(sections) and kind in ("FUNC", "OBJECT"):
            owner = sections[index]
            symbol_extent = {"kind": kind, "address": value,
                "bytes": size, "symbolIndex": at // 24, "sectionIndex": index,
                "section": string(names, owner[0]), "sectionAddress": owner[3],
                "sectionBytes": owner[5], "sectionFlags": owner[2], "sectionType": owner[1]}
            symbol_offset = value - owner[3]
            if kind == "FUNC" and owner[2] & 4 and 4 <= symbol_offset < owner[5]:
                prefix = read_at(owner[4] + symbol_offset - 4, 4)
                symbol_extent.update(prefixWord=int.from_bytes(prefix, "little"),
                    prefixBytesSha256=hashlib.sha256(prefix).hexdigest())
            symbol_extents.setdefault(name, []).append(symbol_extent)
        selected.append("%d: %016x %d %s %d %d %s %s" %
                        (at // 24, value, size, kind, info >> 4, other & 3,
                         "UND" if index == 0 else str(index), name))
    (output / "reference.btf").write_bytes(btf)
    (output / "reference.symbols").write_text("\n".join(selected) + "\n")
    source_identity = {
        "vmlinuxPath": str(source), "sourceFileBytes": file_bytes,
        "btfSha256": hashlib.sha256(btf).hexdigest(), "btfBytes": len(btf),
        "symtabSha256": hashlib.sha256(symbols).hexdigest(),
        "symstrSha256": hashlib.sha256(strings).hexdigest(),
        "sectionHeadersSha256": hashlib.sha256(section_bytes).hexdigest(),
        "selectedSymbolRows": len(selected),
    }
    config = source.parent / ".config"
    if config.is_file():
        source_identity.update(configPath=str(config), configBytes=config.stat().st_size,
            configSha256=hashlib.sha256(config.read_bytes()).hexdigest())
    (output / "reference-symbol-extents.json").write_text(json.dumps(symbol_extents, indent=2) + "\n")
    (output / "reference-source.json").write_text(json.dumps(source_identity, indent=2) + "\n")
    markers = {}
    if "proc_pid_attr_write" in wanted:
        if len(marker_symbols) != 1:
            raise ValueError("Missing/ambiguous actual reference procattr function marker")
        ordinal, kind, index, value, size = marker_symbols[0]
        if kind != 2 or index >= len(sections):
            raise ValueError("Reference procattr marker is not a defined native function")
        section = sections[index]
        offset = value - section[3]
        if not section[2] & 4 or offset < 0 or size <= 0 or offset + size > section[5] or value % 4:
            raise ValueError("Reference procattr marker is outside its executable section")
        marker = {"function": "proc_pid_attr_write", "address": value, "section": string(names, section[0]),
                  "symbolIndex": ordinal, "functionBytes": size}
        if offset >= 4:
            prefix = read_at(section[4] + offset - 4, 4)
            marker.update(prefixAddress=value - 4, prefixWord=int.from_bytes(prefix, "little"),
                          prefixBytesSha256=hashlib.sha256(prefix).hexdigest())
        else:
            marker["prefixUnavailable"] = "Function entry has no preceding word in its executable section"
        markers["proc_pid_attr_write"] = marker
    core_markers = {}
    for function in sorted(core_names):
        try:
            matches = core_marker_symbols[function]
            if len(matches) != 1:
                raise ValueError("Missing/ambiguous native reference function")
            ordinal, kind, index, value, size = matches[0]
            if kind != 2 or index >= len(sections):
                raise ValueError("Native reference marker is not a defined function")
            section = sections[index]
            offset = value - section[3]
            if not section[2] & 4 or offset < 0 or size <= 0 or offset + size > section[5] or value % 4:
                raise ValueError("Native reference marker is outside its executable section")
            marker = {"available": True, "function": function, "address": value,
                "symbolIndex": ordinal, "functionBytes": size, "section": string(names, section[0]),
                "sectionAddress": section[3], "sectionBytes": section[5], "sectionFlags": section[2],
                "fileOffset": section[4] + offset}
            if offset >= 4:
                prefix = read_at(section[4] + offset - 4, 4)
                marker.update(prefixAddress=value - 4, prefixFileOffset=section[4] + offset - 4,
                    prefixWord=int.from_bytes(prefix, "little"), prefixBytesSha256=hashlib.sha256(prefix).hexdigest())
            else:
                marker["prefixUnavailable"] = "Function entry has no preceding word in its executable section"
            core_markers[function] = marker
        except ValueError as error:
            core_markers[function] = {"available": False, "function": function, "reason": str(error)}
    su_data_markers = {}
    for name in sorted(su_data_names):
        try:
            matches = su_data_symbols[name]
            if len(matches) != 1:
                raise ValueError("Missing/ambiguous native data object")
            ordinal, kind, index, value, size = matches[0]
            if kind != 1 or index >= len(sections):
                raise ValueError("Native data marker is not one defined STT_OBJECT")
            section = sections[index]
            offset = value - section[3]
            if not section[2] & 2 or section[2] & 4 or size <= 0 or offset < 0 or offset + size > section[5]:
                raise ValueError("Native data object bounds/allocated/NX section mismatch")
            su_data_markers[name] = {"available": True, "name": name, "address": value,
                "bytes": size, "symbolIndex": ordinal, "section": string(names, section[0]),
                "sectionAddress": section[3], "sectionBytes": section[5], "sectionFlags": section[2],
                "sectionType": section[1], "fileOffset": None if section[1] == 8 else section[4] + offset}
        except ValueError as error:
            su_data_markers[name] = {"available": False, "name": name, "reason": str(error)}
    (output / "reference-function-markers.json").write_text(json.dumps({
        "schemaVersion": 1, "sourceBinding": {name: source_identity[name] for name in
            ("sourceFileBytes", "btfSha256", "symtabSha256", "sectionHeadersSha256")},
        "markers": markers, "coreMarkers": core_markers, "suDataMarkers": su_data_markers,
    }, indent=2) + "\n")
'''


def uid_callback_references(path):
    expected = {"msu_uid_snapshot_reclaim_rcu", "msu_uid_snapshot_reclaim_work"}
    references = {name: set() for name in expected}
    with path.open("rb") as stream:
        elf = ELFFile(stream)
        table = elf.get_section_by_name(".symtab")
        functions = [symbol for symbol in table.iter_symbols()
                     if symbol["st_info"]["type"] == "STT_FUNC" and symbol["st_size"]]
        for section in elf.iter_sections():
            if not isinstance(section, RelocationSection):
                continue
            symbols = elf.get_section(section["sh_link"])
            for relocation in section.iter_relocations():
                name = symbols.get_symbol(relocation["r_info_sym"]).name
                if name not in expected:
                    continue
                for function in functions:
                    if function["st_shndx"] == section["sh_info"] and function["st_value"] <= relocation["r_offset"] < \
                            function["st_value"] + function["st_size"]:
                        references[name].add(function.name)
    if not all(references.values()):
        raise ValueError("Missing actual snapshot callback address relocations: " + repr(references))
    return {name: sorted(callers) for name, callers in references.items()}


def qualification_selected_source(name):
    paths = list(dict.fromkeys(path for path in (*SOURCES, *su_source_paths()) if path.name == name))
    if len(paths) != 1:
        raise ValueError("Missing/ambiguous current factory/provider source selection: " + name)
    return paths[0]

def qualification_callback_parameter(btf, variable, parameter):
    indexes = btf.variables.get(variable, ())
    if len(indexes) != 1:
        raise ValueError("Missing/ambiguous retained callback declaration: " + variable)
    pointer = btf.types[btf.resolve(btf.types[indexes[0]][3])]
    if pointer[1] != 2:
        raise ValueError("Retained declaration is not a function pointer: " + variable)
    prototype = btf.types[btf.resolve(pointer[3])]
    if prototype[1] != 13 or not 0 <= parameter < prototype[2]:
        raise ValueError("Retained callback declaration parameter is absent: " + variable)
    signature = btf.signature(btf.u32(prototype[5] + parameter * 8 + 4))
    if not signature.startswith("*f"):
        raise ValueError("Retained parameter is not a callback: " + variable)
    return signature


def qualification_source_body(source, function, required):
    # Mask comments/literals only for brace matching; retain the exact source slice.
    masked = re.sub(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'',
        lambda match: " " * len(match.group(0)), source, flags=re.S)
    matches = [match for match in re.finditer(r"\b" + re.escape(function) + r"\s*\([^;{}]*\)\s*\{", masked)
               if masked.count("{", 0, match.start()) == masked.count("}", 0, match.start())]
    if len(matches) != 1:
        raise ValueError("Missing/ambiguous selected function body: " + function)
    start, at, depth = matches[0].start(), matches[0].end(), 1
    while at < len(masked) and depth:
        depth += (masked[at] == "{") - (masked[at] == "}")
        at += 1
    body = source[start:at]
    if depth or any(text not in body for text in required):
        raise ValueError("Selected source qualification contract differs: " + function)
    return {"function": function, "sourceBytes": len(body.encode("utf-8")),
        "sourceSha256": hashlib.sha256(body.encode("utf-8")).hexdigest(),
        "requiredExpressions": list(required)}


def qualification_object_references(path, required):
    references = {name: [] for name in required}
    with path.open("rb") as stream:
        elf = ELFFile(stream)
        table = elf.get_section_by_name(".symtab")
        functions = [symbol for symbol in table.iter_symbols()
            if symbol["st_info"]["type"] == "STT_FUNC" and symbol["st_size"]]
        for section in elf.iter_sections():
            if not isinstance(section, RelocationSection):
                continue
            destination = elf.get_section(section["sh_info"])
            if not destination["sh_flags"] & 2:
                continue
            symbols = elf.get_section(section["sh_link"])
            for relocation in section.iter_relocations():
                symbol = symbols.get_symbol(relocation["r_info_sym"])
                names = {symbol.name}
                if symbol["st_info"]["type"] == "STT_SECTION":
                    at = symbol["st_value"] + relocation.entry.get("r_addend", 0)
                    names.update(item.name for item in functions
                        if item["st_shndx"] == symbol["st_shndx"] and item["st_value"] == at)
                callers = [item.name for item in functions
                    if item["st_shndx"] == section["sh_info"] and item["st_value"] <= relocation["r_offset"] <
                        item["st_value"] + item["st_size"]]
                for name in names & references.keys():
                    references[name].append({"section": destination.name,
                        "offset": relocation["r_offset"], "type": relocation["r_info_type"],
                        "symbol": symbol.name, "addend": relocation.entry.get("r_addend", 0),
                        "callers": callers})
    if any(not rows for rows in references.values()):
        raise ValueError("Actual selected factory/provider relocation missing: " +
            ", ".join(name for name, rows in references.items() if not rows))
    return references


def collect_runtime_source_qualification(target, payload):
    record = {"schema": "maplesu-runtime-source-qualification/1", "metadataComplete": False,
        "factoryBound": False, "providerBound": False, "lifecycleBound": False,
        "targetQualified": False, "deviceQualified": False, "objects": {},
        "sourceContracts": {}, "callbackAbi": {}, "relocations": {}, "missing": []}
    try:
        inputs = json.loads((target / "build-inputs.json").read_text(encoding="utf-8"))
        if HOST_DEPLOYMENT_SELECTION is None or not HOST_DEPLOYMENT_SELECTION.get("runtimeChannel") or \
                inputs.get("hostDeploymentSourceSelection") != host_deployment_public_selection() or \
                inputs.get("coreRuntimeSourceSelection") != core_runtime_public_selection():
            raise ValueError("No exact current kernel factory/provider source selection")
        names = ("manager_resident_host", "manager_management", "manager_uid_gateway",
            "manager_uid_snapshot", "manager_su_runtime_factory", "su_observed_install_source",
            "su_durable_authority", "su_durable_modern", "su_install_registry", "su_profile_execution")
        sources, types, policies = {}, {}, {}
        for name in names:
            source = target / (name + ".c")
            proof = object_provenance(target, name)
            if proof["sourceIdentity"]["sha256"] != sha(source) or sha(source) != sha(qualification_selected_source(source.name)):
                raise ValueError("Factory/provider source/object generation differs: " + name)
            sources[name], types[name] = source.read_text(encoding="utf-8"), object_btf(target / (name + ".o"))
            policies[name] = core_cfi_policy(target / ("." + name + ".o.cmd"))
            record["objects"][name] = {"source": identity(source), "object": identity(target / (name + ".o")),
                "command": identity(target / ("." + name + ".o.cmd")),
                "dependencyReceipt": identity(target / "build-dependencies" / (name + ".json")),
                "dependencyCount": len(proof["dependencies"]), "dependencies": proof["dependencies"],
                "debugTransformation": proof.get("debugTransformation")}
        if len({tuple(row.values()) for row in policies.values()}) != 1:
            raise ValueError("Actual factory/provider/caller CFI policies differ")
        config = json.loads((target / "msu_resolver_build_config.json").read_text(encoding="utf-8"))["managementConfig"]
        policy = policies["manager_management"]
        enabled = any(config.get(name) == "y" for name in ("CONFIG_CFI", "CONFIG_CFI_CLANG"))
        if policy["kcfiEnabled"] != enabled or enabled and policy["normalizeIntegers"] != \
                (config.get("CONFIG_CFI_ICALL_NORMALIZE_INTEGERS") == "y"):
            raise ValueError("Factory/provider compiled CFI/config differ")
        record["cfiPolicy"], record["configIdentity"] = policy, identity(target / "msu_resolver_build_config.json")
        contracts = {
            "manager_resident_host": {
                "msu_manager_resident_init": ("!msu_resident_sleepable()", "msu_management_init(", "msu_su_runtime_init("),
                "msu_manager_resident_discard": ("!msu_resident_sleepable()", "msu_su_runtime_close()", "msu_management_discard_unpublished()", "msu_su_runtime_discarded()", "if (error) goto out;"),
                "msu_manager_resident_dispatch": ("!msu_resident_sleepable()", "msu_owner_request("),
            },
            "manager_su_runtime_factory": {
                "msu_su_runtime_init": ("!sleepable()", "msu_su_durable_authority_create(", "msu_uid_gateway_create_kernel(", "msu_su_observed_create(", "msu_su_registry_bind_gateway(", "msu_installation_bind_lifecycle("),
                "create_entries": ("descriptor.kind = MSU_RUNTIME_SERVICE_UID_SU", "descriptor.lifecycle_owner = &runtime", "msu_runtime_service_register("),
                "msu_su_runtime_close": ("runtime.closed = true", "msu_su_observed_close(", "msu_runtime_service_revoke(", "users || producers", "-EINPROGRESS"),
            },
            "manager_uid_gateway": {
                "msu_uid_gateway_discard_host_unpublished": ("WRITE_ONCE(msu_uid_gateway_retiring, true)", "synchronize_rcu()", "msu_uid_snapshot_state_destroy_quiesced(", "controller->fence.retire(", "MAPLESU_EFFECT_PENDING"),
            },
            "manager_uid_snapshot": {
                "msu_uid_snapshot_runtime_discard": ("msu_uid_snapshot_runtime_closed = true", "rcu_barrier()", "flush_workqueue(", "atomic_read(&msu_uid_snapshots_live)", "destroy_workqueue("),
            },
            "su_install_registry": {
                "msu_su_lease_begin": ("full_uid = __kuid_val(", "registry->entries[position]->full_uid != full_uid", "++entry->users", "msu_uid_gateway_acquire(", "msu_uid_snapshot_lookup("),
                "msu_su_registry_destroy": ("msu_su_registry_shutdown(", "source.quiesce(", "source.put_install(", "source.put_owner("),
            },
            "manager_management": {
                "msu_credential_replace_profile": ("!msu_management_sleepable()", "error = validate(context)", "error = commit_creds(prepared)", "MAPLESU_EFFECT_COMMITTED", "adopt(context, original, committed)"),
            },
        }
        for obj, functions in contracts.items():
            for name, required in functions.items():
                record["sourceContracts"][name] = qualification_source_body(sources[obj], name, required)
        edges = {
            "manager_resident_host": ("msu_management_init", "msu_su_runtime_init", "msu_su_runtime_close", "msu_management_discard_unpublished", "msu_su_runtime_discarded", "msu_management_delivery_failed"),
            "manager_su_runtime_factory": ("msu_runtime_service_register", "msu_runtime_service_revoke", "msu_uid_gateway_acquire", "msu_su_registry_bind_gateway", "msu_su_observed_create", "msu_su_durable_authority_create", "msu_su_observed_start", "msu_su_durable_authority_load", "msu_su_read_current"),
            "su_observed_install_source": ("msu_su_registry_create", "msu_su_profile_check_execution"),
            "su_install_registry": ("msu_uid_gateway_bind_reader", "msu_uid_gateway_acquire"),
            "su_durable_authority": ("msu_su_modern_authority_list", "msu_su_modern_authority_grant"),
            "manager_management": ("msu_uid_gateway_discard_host_unpublished", "commit_creds"),
        }
        for obj, required in edges.items():
            record["relocations"][obj] = qualification_object_references(target / (obj + ".o"), required)
        source_callbacks = {"pin_current": "msu_observed_pin_current", "put_install": "msu_observed_put_install",
            "read_grant": "msu_observed_read_grant", "check_closed": "msu_observed_check_closed",
            "quiesce": "msu_observed_quiesce", "put_owner": "msu_observed_put_owner",
            "validate_producer": "msu_observed_validate_producer"}
        record["sourceContracts"]["msu_su_observed_create"] = qualification_source_body(
            sources["su_observed_install_source"], "msu_su_observed_create",
            tuple("source." + member + " = " + name for member, name in source_callbacks.items()) +
            ("source.validate_profile = msu_su_profile_check_execution", "msu_su_registry_create("))
        record["sourceContracts"]["msu_su_durable_authority_create"] = qualification_source_body(
            sources["su_durable_authority"], "msu_su_durable_authority_create",
            ("selected.read_grant = msu_durable_grant", "selected.put_owner = msu_durable_put", "selected.list_uids = msu_durable_list"))
        record["relocations"]["observedSourceCallbacks"] = qualification_object_references(
            target / "su_observed_install_source.o", tuple(source_callbacks.values()))
        record["relocations"]["durableAuthorityCallbacks"] = qualification_object_references(
            target / "su_durable_authority.o", ("msu_durable_grant", "msu_durable_put", "msu_durable_list"))
        for member, name in source_callbacks.items():
            signature = types["su_install_registry"].member_function("msu_su_registry_source", member)
            if signature != "*" + types["su_observed_install_source"].function(name):
                raise ValueError("Actual trusted source callback type differs: " + member)
            record["callbackAbi"]["source." + member] = {"definition": name, "signature": signature}
        for member, name in {"read_grant": "msu_durable_grant", "put_owner": "msu_durable_put", "list_uids": "msu_durable_list"}.items():
            signature = types["su_observed_install_source"].member_function("msu_su_observed_authority", member)
            if signature != "*" + types["su_durable_authority"].function(name):
                raise ValueError("Actual durable authority callback type differs: " + member)
            record["callbackAbi"]["authority." + member] = {"definition": name, "signature": signature}
        retained = {"manager_resident_host": ("msu_manager_resident_init", "msu_manager_resident_discard", "msu_manager_resident_dispatch"),
            "manager_su_runtime_factory": ("msu_su_runtime_init", "msu_su_runtime_post_fs_data", "msu_su_runtime_close", "msu_su_runtime_discarded"),
            "su_observed_install_source": ("msu_su_observed_create", "msu_su_observed_start", "msu_su_observed_close", *source_callbacks.values()),
            "su_durable_authority": ("msu_su_durable_authority_create", "msu_su_durable_authority_load", "msu_durable_grant", "msu_durable_put", "msu_durable_list"),
            "su_install_registry": ("msu_su_registry_bind_gateway", "msu_su_lease_begin", "msu_su_read_current"),
            "manager_uid_snapshot": ("msu_uid_snapshot_runtime_discard",),
            "manager_uid_gateway": ("msu_uid_gateway_discard_host_unpublished", "msu_uid_gateway_acquire"),
            "manager_management": ("msu_runtime_service_register", "msu_runtime_service_revoke", "msu_credential_replace_profile_current")}
        record["retainedFunctions"] = {}
        with (target / "claim.so").open("rb") as stream:
            final = ELFFile(stream)
            final_table = final.get_section_by_name(".symtab")
            for obj, functions in retained.items():
                with (target / (obj + ".o")).open("rb") as object_stream:
                    own = ELFFile(object_stream)
                    table = own.get_section_by_name(".symtab")
                    for name in functions:
                        symbols, final_symbols = table.get_symbol_by_name(name) or [], final_table.get_symbol_by_name(name) or []
                        if len(symbols) != 1 or len(final_symbols) != 1:
                            raise ValueError("Missing/ambiguous retained factory/provider function: " + name)
                        item = {"object": obj, "signature": types[obj].function(name)}
                        for label, elf, symbol in (("object", own, symbols[0]), ("final", final, final_symbols[0])):
                            if symbol["st_info"]["type"] != "STT_FUNC" or not isinstance(symbol["st_shndx"], int):
                                raise ValueError("Retained factory/provider symbol is not a function: " + name)
                            section = elf.get_section(symbol["st_shndx"])
                            at, size = symbol["st_value"] - section["sh_addr"], symbol["st_size"]
                            if not section["sh_flags"] & 4 or size <= 0 or at < 0 or at + size > section["sh_size"]:
                                raise ValueError("Retained factory/provider function bounds differ: " + name)
                            item[label + "Entry"] = {"section": section.name, "offset": symbol["st_value"], "bytes": size}
                            if enabled:
                                if at < 4:
                                    raise ValueError("Retained factory/provider CFI prefix absent: " + name)
                                item[label + "Typeid"] = int.from_bytes(section.data()[at - 4:at], "little")
                        if enabled and item["objectTypeid"] != item["finalTypeid"]:
                            raise ValueError("Retained factory/provider object/final CFI differs: " + name)
                        record["retainedFunctions"][name] = item
        record["sourceSelection"] = {"host": inputs["hostDeploymentSourceSelection"],
            "runtime": inputs["coreRuntimeSourceSelection"], "buildInputs": identity(target / "build-inputs.json"),
            "claimSha256": payload["sha256"]}
        record.update(metadataComplete=True, factoryBound=True, providerBound=True, lifecycleBound=True)
    except (OSError, ValueError, KeyError, IndexError) as error:
        record["missing"].append(type(error).__name__ + ": " + str(error))
    return record


def collect_sleepable_source_qualification(target):
    record = {"schema": "maplesu-sleepable-source-qualification/1", "sourceQualified": False,
        "qualified": False, "targetQualified": False, "deviceQualified": False,
        "objects": {}, "sourceContracts": {}, "missing": []}
    try:
        contracts = {
            "manager_resident_host": {
                "msu_resident_sleepable": ("in_task()", "!in_atomic()", "!irqs_disabled()"),
                "msu_manager_resident_init": ("!msu_resident_sleepable()", "msu_management_init("),
                "msu_manager_resident_discard": ("!msu_resident_sleepable()", "msu_management_discard_unpublished()"),
                "msu_manager_resident_dispatch": ("!msu_resident_sleepable()", "msu_owner_request("),
                "msu_owner_request": ("msu_management_dispatch(", "msu_management_delivery_failed("),
            },
            "manager_management": {
                "msu_management_sleepable": ("in_task()", "!in_atomic()", "!irqs_disabled()"),
                "msu_management_dispatch": ("!msu_management_sleepable()", "-EWOULDBLOCK"),
                "msu_management_discard_unpublished": ("!msu_management_sleepable()", "msu_uid_gateway_discard_host_unpublished("),
                "msu_management_delivery_failed": ("msu_management_retire(", "msu_management_release_all("),
                "msu_management_procattr": ("/proc/thread-self/attr/current", "/proc/thread-self/attr/exec", "O_WRONLY | O_NOFOLLOW | O_NONBLOCK | O_NOCTTY", "filp_open(", "msu_management_procattr_file(", "msu_management_check(session)", "msu_dynamic_get_current_cred()", "vfs_write(", "written == operation->length ? 0 : -EIO", "fput(file)"),
                "msu_management_procattr_file": ("PROC_SUPER_MAGIC", "file_operations, write", "proc_pid_attr_write"),
            },
        }
        policies = []
        for obj, functions in contracts.items():
            source = target / (obj + ".c")
            proof = object_provenance(target, obj)
            if proof["sourceIdentity"]["sha256"] != sha(source) or sha(source) != sha(qualification_selected_source(source.name)):
                raise ValueError("Sleepable source/object generation differs: " + obj)
            record["objects"][obj] = {"source": identity(source), "object": identity(target / (obj + ".o")),
                "command": identity(target / ("." + obj + ".o.cmd")),
                "dependencyReceipt": identity(target / "build-dependencies" / (obj + ".json"))}
            policies.append(core_cfi_policy(target / ("." + obj + ".o.cmd")))
            for name, required in functions.items():
                record["sourceContracts"][name] = qualification_source_body(source.read_text(encoding="utf-8"), name, required)
        if policies[0] != policies[1]:
            raise ValueError("Resident/management sleepable closure CFI differs")
        record["cfiPolicy"] = policies[0]
        config = json.loads((target / "msu_resolver_build_config.json").read_text(encoding="utf-8"))["managementConfig"]
        record["procattrConfigured"] = all(config.get(name) == "y" for name in ("CONFIG_PROC_FS", "CONFIG_SECURITY"))
        record["procattrRelocations"] = qualification_object_references(target / "manager_management.o",
            ("filp_open", "vfs_write", "fput", "proc_pid_attr_write")) if record["procattrConfigured"] else {}
        record["sourceQualified"] = True
        record["qualificationMissing"] = ["Actual target execution and specified-device effects remain unmeasured"]
    except (OSError, ValueError, KeyError, IndexError) as error:
        record["missing"].append(type(error).__name__ + ": " + str(error))
    return record


def collect_uid_snapshot_abi(target, payload, reference):
    record = {"schemaVersion": 1, "metadataComplete": False, "targetQualified": False,
              "sourceIdentities": [], "objectIdentities": [], "commandIdentities": [],
              "dependencyReceiptIdentities": [], "compiledFacts": {}, "compiledLayouts": {},
              "callbackOrigins": {}, "config": {}, "inlineDependencies": [], "missing": []}
    try:
        objects = {}
        for name in ("manager_uid_snapshot", "manager_uid_gateway"):
            obj = target / (name + ".o")
            source_identity = identity(selected_native_source(name + ".c"))
            proof = object_provenance(target, name)
            if proof["sourceIdentity"]["sha256"] != source_identity["sha256"] or \
                    sha(target / (name + ".c")) != source_identity["sha256"]:
                raise ValueError("UID actual object/source generation mismatch: " + name)
            objects[name] = object_btf(obj)
            record["sourceIdentities"].append(source_identity)
            record["objectIdentities"].append(identity(obj))
            record["commandIdentities"].append(identity(target / ("." + name + ".o.cmd")))
            record["dependencyReceiptIdentities"].append(identity(target / "build-dependencies" / (name + ".json")))
            record["inlineDependencies"].extend(proof["dependencies"])
            with obj.open("rb") as stream:
                table = ELFFile(stream).get_section_by_name(".symtab")
                record.setdefault("objectUndefinedSymbols", {})[name] = sorted({symbol.name
                    for symbol in table.iter_symbols() if symbol.name and symbol["st_shndx"] == "SHN_UNDEF"})
        record["callbackRelocations"] = uid_callback_references(target / "manager_uid_snapshot.o")
        policies = {}
        for name in ("manager_uid_snapshot", "manager_uid_gateway", "manager_management"):
            command = (target / ("." + name + ".o.cmd")).read_text(encoding="utf-8").splitlines()[0]
            enabled, present = False, False
            for token in command.split():
                if token.startswith(("-fsanitize=", "-fno-sanitize=")) and "kcfi" in token.split("=", 1)[1].split(","):
                    present = True
                    enabled = token.startswith("-fsanitize=")
            if present and not enabled:
                raise ValueError("Actual UID caller/callback compilation disabled KCFI: " + name)
            policies[name] = {"kcfiOptionPresent": present, "kcfiEnabled": enabled,
                              "commandSha256": sha(target / ("." + name + ".o.cmd"))}
        if len({(item["kcfiOptionPresent"], item["kcfiEnabled"]) for item in policies.values()}) != 1:
            raise ValueError("UID caller/callback objects use inconsistent CFI policies")
        record["objectCfiPolicies"] = policies
        facts_proof = object_provenance(target, "manager_uid_facts")
        record["factsObjectIdentity"] = identity(target / "manager_uid_facts.o")
        record["factsCommandIdentity"] = identity(target / ".manager_uid_facts.o.cmd")
        record["factsDependencyReceiptIdentity"] = identity(target / "build-dependencies/manager_uid_facts.json")
        record["inlineDependencies"].extend(facts_proof["dependencies"])
        values = object_words(target / "manager_uid_facts.o", "msu_uid_build_facts")
        if len(values) != len(UID_FACTS) or values[0] != 1:
            raise ValueError("Actual UID layout facts schema/count mismatch")
        facts = record["compiledFacts"] = dict(zip(UID_FACTS, values))
        if facts["recordBytes"] != 280 or facts["recordStride"] != 280 or facts["recordAlign"] != 8:
            raise ValueError("UID record compiled stride/size/alignment mismatch")
        for prefix, (_, btf_name, object_name, members) in UID_LAYOUT_TYPES.items():
            layout = objects[object_name].named_structure_members(btf_name)
            if layout["bytes"] != facts[prefix + "Bytes"] or facts[prefix + "Align"] <= 0:
                raise ValueError("UID actual object size/C alignment mismatch: " + prefix)
            for member in members:
                key = prefix + "".join(word.capitalize() for word in member.split("_"))
                item = layout["members"][member]
                if item["offset"] != facts[key + "Offset"] or item["bytes"] != facts[key + "Bytes"]:
                    raise ValueError("UID actual BTF/C field mismatch: " + prefix + "." + member)
            record["compiledLayouts"][prefix] = layout
        declarations = object_btf(target / "manager_uid_facts.o")
        snapshot, gateway = objects["manager_uid_snapshot"], objects["manager_uid_gateway"]
        declared_rcu, record["rcuDeclarationEvidence"] = retained_probe_declaration(
            target, declarations, "manager_uid_facts", "msu_build_uid_call_rcu", "call_rcu")
        if not declared_rcu.startswith("*f"):
            raise ValueError("UID actual call_rcu declaration is not a function pointer")
        rcu_signature = qualification_callback_parameter(declarations, "msu_build_uid_call_rcu", 1)
        if rcu_signature != reference.member_function("callback_head", "func") or \
                rcu_signature != declarations.typedef_function("rcu_callback_t") or \
                rcu_signature != "*" + snapshot.function("msu_uid_snapshot_reclaim_rcu"):
            raise ValueError("UID RCU callsite/field/typedef/definition ABI mismatch")
        work_signature = reference.member_function("work_struct", "func")
        if work_signature != reference.typedef_function("work_func_t") or \
                work_signature != declarations.typedef_function("work_func_t") or \
                work_signature != "*" + snapshot.function("msu_uid_snapshot_reclaim_work"):
            raise ValueError("UID work field/typedef/definition ABI mismatch")
        validator_signature = gateway.function_parameter("msu_uid_gateway_dispatch", 4)
        management = object_btf(target / "manager_management.o")
        if validator_signature != "*" + management.function("msu_management_validate_credentials"):
            raise ValueError("UID gateway validator callsite/definition ABI mismatch")
        source = (target / "manager_uid_snapshot.c").read_text(encoding="utf-8")
        if not re.search(r"call_rcu\s*\(\s*&snapshot->reclaim_rcu\s*,\s*msu_uid_snapshot_reclaim_rcu\s*\)", source) or \
                not re.search(r"INIT_WORK\s*\(\s*&snapshot->reclaim_work\s*,\s*msu_uid_snapshot_reclaim_work\s*\)", source):
            raise ValueError("UID snapshot actual callback registration callsites missing")
        record["callbackOrigins"] = {
            "msu_uid_snapshot_reclaim_rcu": {"kind": "rcu", "source": "call_rcu",
                "secondarySource": "rcu_callback_t", "parameterIndex": 1, "signature": rcu_signature},
            "msu_uid_snapshot_reclaim_work": {"kind": "work", "source": "work_struct.func",
                "secondarySource": "work_func_t", "signature": work_signature},
            "msu_uid_gateway_dispatch.validate": {"kind": "internal", "source": "msu_uid_gateway_dispatch",
                "parameterIndex": 4, "definition": "msu_management_validate_credentials", "signature": validator_signature},
        }
        record["callbackCallsites"] = {"sourceIdentity": identity(selected_native_source("manager_uid_snapshot.c")),
            "rcuRegistration": "call_rcu(&snapshot->reclaim_rcu, msu_uid_snapshot_reclaim_rcu)",
            "workRegistration": "INIT_WORK(&snapshot->reclaim_work, msu_uid_snapshot_reclaim_work)",
            "validatorCallerObjectIdentity": identity(target / "manager_uid_gateway.o"),
            "validatorDefinitionObjectIdentity": identity(target / "manager_management.o")}
        build_config = json.loads((target / "msu_resolver_build_config.json").read_text(encoding="utf-8"))
        record["config"] = {"CONFIG_" + name: build_config["managementConfig"].get("CONFIG_" + name, "n")
                            for name in UID_CONFIG_NAMES}
        record["finalImports"] = {name: reference.variable(name) if name in reference.variables else reference.function(name)
                                  for name in payload["imports"]}
        record["metadataComplete"] = True
    except (OSError, ValueError, KeyError, IndexError) as error:
        record["missing"].append(type(error).__name__ + ": " + str(error))
    source = record["sourceQualification"] = collect_runtime_source_qualification(target, payload)
    record["missing"].extend(source["missing"])
    record["metadataComplete"] = record["metadataComplete"] and source["metadataComplete"]
    record["executionContext"] = collect_sleepable_source_qualification(target)
    record["missing"].extend(record["executionContext"]["missing"])
    record["metadataComplete"] = record["metadataComplete"] and record["executionContext"]["sourceQualified"]
    record["qualificationMissing"] = ["Actual target RCU/refcount/work execution remains pending specified-device acceptance"]
    record["runtimeApiRoutes"] = {
        "factory": ["msu_runtime_service_register", "msu_runtime_service_publish", "msu_runtime_service_revoke"],
        "reader": "msu_uid_gateway_acquire", "managerSessionBindingIsFactoryRoute": False}
    return record


def procattr_marker_references(path):
    references = []
    with path.open("rb") as stream:
        elf = ELFFile(stream)
        for relocations in elf.iter_sections():
            if not isinstance(relocations, RelocationSection):
                continue
            symbols = elf.get_section(relocations["sh_link"])
            destination = elf.get_section(relocations["sh_info"])
            if not destination["sh_flags"] & 4:
                continue
            functions = [item for item in symbols.iter_symbols()
                         if item["st_info"]["type"] == "STT_FUNC" and
                         item["st_shndx"] == relocations["sh_info"] and item["st_size"]]
            for relocation in relocations.iter_relocations():
                symbol = symbols.get_symbol(relocation["r_info_sym"])
                if symbol.name != "proc_pid_attr_write":
                    continue
                if relocation["r_info_type"] in (282, 283):
                    raise ValueError("Native procattr identity marker is used as a direct branch")
                at = relocation["r_offset"]
                callers = [item.name for item in functions
                           if item["st_value"] <= at < item["st_value"] + item["st_size"]]
                if len(callers) != 1:
                    raise ValueError("Native procattr marker lacks a unique actual caller")
                references.append({"section": relocations.name, "relocatedSection": destination.name,
                    "offset": at, "type": relocation["r_info_type"],
                    "symbol": symbol.name, "caller": callers[0]})
    if not references:
        raise ValueError("No executable native procattr identity-marker relocation")
    return references


def procattr_cfi_policy(command):
    enabled, seen, legacy = False, False, False
    tokens = command.read_text(encoding="utf-8").splitlines()[0].split()
    for token in tokens:
        if token.startswith(("-fsanitize=", "-fno-sanitize=")):
            values = token.split("=", 1)[1].split(",")
            positive = token.startswith("-fsanitize=")
            if "kcfi" in values:
                seen |= positive
                enabled = positive
            if "cfi" in values:
                legacy = positive
    if seen and not enabled or legacy:
        raise ValueError("Unsupported/disabled native procattr KCFI compilation")
    return {"mode": "kcfi" if enabled else "none", "normalizeIntegers": any(token in (
        "-fsanitize-cfi-icall-normalize-integers",
        "-fsanitize-cfi-icall-experimental-normalize-integers") for token in tokens)}


def collect_procattr_abi(target, payload, reference):
    record = {"schemaVersion": 1, "metadataComplete": False, "targetQualified": False,
              "compiledLayouts": {}, "typeAlignments": {}, "functionAbi": {},
              "config": {}, "missing": []}
    try:
        layout = payload["compiledLayouts"]["msu_procattr_compiled_layout"]["values"]
        record.update(layoutSchema=layout[0], layoutWords=len(layout),
            compiledLayoutSha256=hashlib.sha256(b"".join(value.to_bytes(4, "little")
                                                      for value in layout)).hexdigest())
        header = (target / "manager_management.h").read_text(encoding="utf-8")
        enumeration = re.search(r"enum\s+msu_procattr_layout_word\s*\{([^}]*)\}", header, re.S)
        words = re.findall(r"\bMSU_PROCATTR_[A-Z_]+\b", enumeration.group(1)) if enumeration else []
        if len(layout) != 40 or layout[0] != 1 or not words or len(words) != 41 or \
                words[-1] != "MSU_PROCATTR_LAYOUT_WORDS":
            raise ValueError("Actual independent procattr layout schema/enum mismatch")
        obj = target / "manager_management.o"
        with obj.open("rb") as stream:
            elf = ELFFile(stream)
            table = elf.get_section_by_name(".symtab")
            symbols = table.get_symbol_by_name("msu_procattr_compiled_layout") if table else None
            if not symbols or len(symbols) != 1 or symbols[0]["st_info"]["type"] != "STT_OBJECT":
                raise ValueError("Missing actual independent procattr layout object")
            section = elf.get_section(symbols[0]["st_shndx"])
            if section.name != ".msu.procattr.layout" or symbols[0]["st_size"] != 160:
                raise ValueError("Native procattr layout does not occupy its independent section")
            record["layoutSection"] = {"name": section.name, "flags": section["sh_flags"],
                                       "symbolBytes": symbols[0]["st_size"]}
        proof = object_provenance(target, "manager_management")
        record.update(sourceIdentity=identity(selected_native_source("manager_management.c")),
            objectIdentity=identity(obj), commandIdentity=identity(target / ".manager_management.o.cmd"),
            dependencyReceiptIdentity=identity(target / "build-dependencies/manager_management.json"),
            dependencies=proof["dependencies"])
        if proof["sourceIdentity"]["sha256"] != record["sourceIdentity"]["sha256"] or \
                sha(target / "manager_management.c") != record["sourceIdentity"]["sha256"] or \
                object_words(obj, "msu_procattr_compiled_layout") != layout:
            raise ValueError("Native procattr source/object/final-layout generation mismatch")
        facts_proof = object_provenance(target, "manager_management_facts")
        facts_obj = target / "manager_management_facts.o"
        record.update(factsObjectIdentity=identity(facts_obj),
            factsCommandIdentity=identity(target / ".manager_management_facts.o.cmd"),
            factsDependencyReceiptIdentity=identity(target / "build-dependencies/manager_management_facts.json"),
            factsDependencies=facts_proof["dependencies"])
        values = object_words(facts_obj, "msu_procattr_build_facts")
        if len(values) != len(PROCATTR_FACT_NAMES):
            raise ValueError("Native procattr actual C facts count mismatch")
        facts = record["compiledFacts"] = dict(zip(PROCATTR_FACT_NAMES, values))
        if values[:3] != layout[:3] or facts["procMagic"] != layout[39]:
            raise ValueError("Native procattr C probe/management constants mismatch")
        config = json.loads((target / "msu_resolver_build_config.json").read_text(encoding="utf-8"))
        record["config"] = {name: config["managementConfig"].get(name, "n")
                            for name in PROCATTR_CONFIG_NAMES}
        if layout[2] != int(all(record["config"][name] == "y"
                                for name in ("CONFIG_PROC_FS", "CONFIG_SECURITY"))):
            raise ValueError("Native procattr actual configuration/implementation mismatch")
        own = object_btf(obj)
        for prefix, (name, size_at, align_at, fields) in PROCATTR_LAYOUT_TYPES.items():
            item = own.named_structure_selected(name, fields)
            if item["bytes"] != layout[size_at] or layout[align_at] != facts[prefix + "Align"] or \
                    layout[align_at] <= 0 or layout[align_at] & (layout[align_at] - 1):
                raise ValueError("Native procattr actual size/C-alignment mismatch: " + prefix)
            for member, (offset_at, bytes_at) in fields.items():
                if (item["members"][member]["offset"], item["members"][member]["bytes"]) != \
                        (layout[offset_at], layout[bytes_at]):
                    raise ValueError("Native procattr object BTF/C field mismatch: " + prefix + "." + member)
            record["compiledLayouts"][prefix] = item
            record["typeAlignments"][prefix] = layout[align_at]
        for prefix, (name, _, _, fields) in PROCATTR_LAYOUT_TYPES.items():
            observed = reference.named_structure_selected(name, fields)
            item = record["compiledLayouts"][prefix]
            if observed["bytes"] != item["bytes"] or observed["members"] != item["members"]:
                raise ValueError("Native procattr compiled/reference BTF mismatch: " + prefix)
        if layout[2]:
            declarations = object_btf(facts_obj)
            probe_variables = {"filp_open": "msu_build_procattr_open", "vfs_write": "msu_build_procattr_write",
                "fput": "msu_build_procattr_put", "proc_pid_attr_write": "msu_build_procattr_marker"}
            record["declarationEvidence"] = {}
            for name in PROCATTR_FUNCTIONS:
                pointer, evidence = retained_probe_declaration(
                    target, declarations, "manager_management_facts", probe_variables[name], name)
                signature = pointer[1:]
                record["declarationEvidence"][name] = evidence
                record["functionAbi"][name] = signature
                if signature != reference.function(name) or name not in payload["imports"]:
                    raise ValueError("Native procattr declaration/reference/final import mismatch: " + name)
            write = reference.member_function("file_operations", "write")
            record["writeSignature"] = write
            if write != "*" + record["functionAbi"]["vfs_write"] or \
                    write != "*" + record["functionAbi"]["proc_pid_attr_write"]:
                raise ValueError("Native procattr marker/VFS/write-field ABI mismatch")
            marker = record["marker"] = {"function": "proc_pid_attr_write",
                "signature": record["functionAbi"]["proc_pid_attr_write"],
                "objectRelocations": procattr_marker_references(obj)}
            policy = marker["cfi"] = procattr_cfi_policy(target / ".manager_management.o.cmd")
            enabled = any(record["config"][name] == "y" for name in ("CONFIG_CFI", "CONFIG_CFI_CLANG"))
            if (policy["mode"] == "kcfi") != enabled or enabled and policy["normalizeIntegers"] != \
                    (record["config"]["CONFIG_CFI_ICALL_NORMALIZE_INTEGERS"] == "y"):
                raise ValueError("Native procattr compilation/configuration CFI mismatch")
            if enabled:
                probe = target / "manager_callback_types.o"
                object_provenance(target, "manager_callback_types")
                probe_command = target / ".manager_callback_types.o.cmd"
                if procattr_cfi_policy(probe_command) != policy:
                    raise ValueError("Native procattr marker declaration uses another CFI policy")
                policy.update(typeProbeObjectIdentity=identity(probe),
                    typeProbeCommandIdentity=identity(probe_command),
                    typeProbeDependencyReceiptIdentity=identity(target / "build-dependencies/manager_callback_types.json"))
                with probe.open("rb") as stream:
                    table = ELFFile(stream).get_section_by_name(".symtab")
                    symbols = table.get_symbol_by_name("__kcfi_typeid_proc_pid_attr_write") if table else None
                    if not symbols or len(symbols) != 1 or symbols[0]["st_shndx"] != "SHN_ABS":
                        raise ValueError("Missing actual native procattr marker declaration KCFI typeid")
                    policy["declarationTypeid"] = symbols[0]["st_value"] & 0xffffffff
                evidence_path = target / "reference-function-markers.json"
                evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
                reference_source = json.loads((target / "reference-source.json").read_text(encoding="utf-8"))
                if evidence.get("schemaVersion") != 1 or evidence["sourceBinding"] != {name: reference_source[name] for name in
                        ("sourceFileBytes", "btfSha256", "symtabSha256", "sectionHeadersSha256")}:
                    raise ValueError("Native procattr marker/reference provenance mismatch")
                observed = evidence["markers"]["proc_pid_attr_write"]
                if observed["function"] != "proc_pid_attr_write" or observed["address"] <= 4 or \
                        observed.get("prefixAddress") != observed["address"] - 4 or \
                        not isinstance(observed.get("prefixWord"), int) or not 0 <= observed["prefixWord"] <= 0xffffffff:
                    raise ValueError("Invalid actual reference procattr function marker bounds")
                policy.update(referenceEvidenceIdentity=identity(evidence_path),
                    referenceTypeid=observed["prefixWord"], referenceMarker=observed)
                if policy["referenceTypeid"] != policy["declarationTypeid"]:
                    raise ValueError("Native procattr marker actual/declaration KCFI typeid mismatch")
            policy["verified"] = True
        record["metadataComplete"] = True
    except (OSError, ValueError, KeyError, IndexError) as error:
        record["missing"].append(type(error).__name__ + ": " + str(error))
    record["executionContext"] = collect_sleepable_source_qualification(target)
    record["missing"].extend(record["executionContext"]["missing"])
    record["metadataComplete"] = record["metadataComplete"] and record["executionContext"]["sourceQualified"]
    record["nativeVfsSelector"] = {
        "route": "filp_open /proc/thread-self/attr/{current,exec}; verify native f_op.write; vfs_write",
        "sourceUsesNativeSelector": bool(record.get("compiledFacts", {}).get("configured") and record["metadataComplete"]),
        "targetBodyQualified": False, "deviceQualified": False}
    record["qualificationMissing"] = ["Actual procattr execution effects remain pending specified-device acceptance"]
    return record


def management_build_tool_provenance(target, selection):
    collector_tools = selection.get("metadataCollectorBuildTools")
    if collector_tools is None:
        for tool in selection["buildTools"]:
            if identity(Path(tool["path"])) != tool:
                raise ValueError("Build tool changed since native selection: " + tool["path"])
        return {"mode": "native-current-tools", "nativeBuildTools": selection["buildTools"]}
    native_receipt = selection["compiler17InputReceipt"]
    native_path = Path(native_receipt["path"])
    if identity(native_path) != native_receipt:
        raise ValueError("Recovered native input receipt changed")
    native = json.loads(native_path.read_text(encoding="utf-8"))
    if any(selection.get(key) != value for key, value in native.items()):
        raise ValueError("Metadata recovery changed an original native selection field")
    if selection["compilerBuildTools"] != native["buildTools"]:
        raise ValueError("Metadata recovery does not bind the original native build tools")
    archives = selection["nativeBuildToolArchives"]
    if [row["original"] for row in archives] != native["buildTools"]:
        raise ValueError("Recovered native build tool archives differ from the compiler receipt")
    for row in archives:
        archive = row["archive"]
        if identity(Path(archive["path"])) != archive or any(
                archive[key] != row["original"][key] for key in ("bytes", "sha256")):
            raise ValueError("Recovered native build tool archive changed: " + row["original"]["path"])
    if collector_tools != [identity(path) for path in BUILD_TOOLS]:
        raise ValueError("Metadata collector tools changed since recovery selection")
    artifacts = selection["nativeRuntimeArtifacts"]
    if set(artifacts) != {"claim.so", "claim.full.so"}:
        raise ValueError("Recovered native runtime artifact set differs")
    for name, expected in artifacts.items():
        if identity(Path(expected["path"])) != expected or any(
                identity(target / name)[key] != expected[key] for key in ("bytes", "sha256")):
            raise ValueError("Recovered native runtime bytes changed: " + name)
    return {"mode": "archived-native-and-current-metadata-tools", "nativeInputReceipt": native_receipt,
            "nativeBuildTools": archives, "metadataCollectorBuildTools": collector_tools,
            "nativeRuntimeArtifacts": artifacts, "runtimeCompilationExecuted": False}


def collect_management_abi(target, payload, reference):
    layout = payload["compiledLayouts"]["msu_management_compiled_layout"]["values"]
    record = {"schemaVersion": 1, "claimSha256": payload["sha256"], "complete": False,
              "metadataComplete": False, "targetQualified": False,
              "layoutSchema": layout[0], "layoutWords": len(layout),
              "compiledLayoutSha256": hashlib.sha256(b"".join(
                  value.to_bytes(4, "little") for value in layout)).hexdigest(),
              "sourceFrozen": False, "config": {}, "constants": {}, "typeAlignments": {}, "wireLayout": {},
              "callbackOrigins": {}, "enabledFeatures": [], "gateVerified": False, "missing": []}
    try:
        selection = json.loads((target / "build-inputs.json").read_text(encoding="utf-8"))
        upstream = upstream_source_freeze()
        if selection["upstreamSourceFreeze"] != upstream:
            raise ValueError("Native inputs do not bind the selected A-side runtime freeze")
        record["upstreamSourceFreeze"] = upstream
        record["wireIdentity"] = identity(PROTOCOL)
        for row in selection["inputs"]:
            source, copied = Path(row["sourceIdentity"]["path"]), target / row["copy"]
            if sha(source) != row["sourceIdentity"]["sha256"] or sha(copied) != row["sourceIdentity"]["sha256"]:
                raise ValueError("Source changed since native build selection: " + str(source))
        record["buildToolProvenance"] = management_build_tool_provenance(target, selection)
        record["buildInputIdentity"] = identity(target / "build-inputs.json")
        dependencies = target / "build-dependencies/manager_management.json"
        provenance = object_provenance(target, "manager_management")
        obj, command = target / "manager_management.o", target / ".manager_management.o.cmd"
        record.update(sourceIdentity=identity(selected_native_source("manager_management.c")),
                      objectIdentity=identity(obj), commandIdentity=identity(command),
                      dependencyReceiptIdentity=identity(dependencies), dependencies=provenance["dependencies"])
        if provenance["sourceIdentity"]["sha256"] != record["sourceIdentity"]["sha256"]:
            raise ValueError("Management object dependencies refer to another source generation")
        tokens = command.read_text(encoding="utf-8").splitlines()[0].split()
        gate_definitions = [token for token in tokens if token.startswith("-DMSU_MANAGEMENT_PUBLICATION_GATE_VERIFIED")]
        binding = publication_gate_binding()
        gate_verified = int(binding is not None)
        if selection.get("publicationGateBinding") != binding or gate_definitions != [
                "-DMSU_MANAGEMENT_PUBLICATION_GATE_VERIFIED=" + str(gate_verified)]:
            raise ValueError("Publication gate does not match its actual source generation")
        if gate_verified and "-mgeneral-regs-only" not in tokens:
            raise ValueError("Publication native calls require the no-FP kernel ABI")
        object_provenance(target, "manager_management_facts")
        values = object_words(target / "manager_management_facts.o", "msu_management_build_facts")
        if len(values) != len(MANAGEMENT_FACT_NAMES) or values[0] != 1:
            raise ValueError("Unknown actual management facts schema")
        facts = dict(zip(MANAGEMENT_FACT_NAMES, values))
        if facts["MSU_MANAGEMENT_PUBLICATION_GATE_VERIFIED"] != gate_verified:
            raise ValueError("Publication compiled facts disagree with the selected gate generator")
        record["gateVerified"] = bool(gate_verified)
        record["publicationGateBinding"] = binding
        if (facts["controlBytes"], facts["controlAlign"], facts["prepareBytes"], facts["memberBytes"]) != (64, 8, 64, 24):
            raise ValueError("Publication control/prepare/member ABI does not match the unique wire")
        wire_values = object_words(target / "manager_management_facts.o", "msu_management_wire_facts")
        if len(wire_values) != len(WIRE_FACTS):
            raise ValueError("Actual wire facts count does not match the frozen contract")
        record["wireLayout"] = dict(zip(WIRE_FACTS, wire_values))
        for name, (_, expected) in WIRE_FACTS.items():
            if record["wireLayout"][name] != expected:
                raise ValueError("Actual frozen wire fact mismatch: " + name)
        record["factSemantics"] = {
            "queryViews": f"Contract diagnostic count; actual {5 if CORE_SELECTION else 4}-descriptor layout is independently bound to resident object BTF and final ELF",
            "publicationControlSchema": "Contract diagnostic schema; not a runtime publication proof",
            "wireLayout": "Actual sizeof/alignof/offsetof/macros from the selected protocol header; independent of target kernel BTF",
        }
        header = (target / "manager_management.h").read_text(encoding="utf-8")
        enumeration = re.search(r"enum\s+msu_management_layout_word\s*\{([^}]*)\}", header, re.S)
        words = re.findall(r"\bMSU_MANAGEMENT_[A-Z_]+\b", enumeration.group(1)) if enumeration else []
        if layout[0] != 1 or len(layout) != 98 or not words or \
                words[-1] != "MSU_MANAGEMENT_LAYOUT_WORDS" or len(words) - 1 != len(layout):
            raise ValueError("Compiled management layout does not match its actual enum")
        compiled = dict(zip(words, layout))
        if compiled["MSU_MANAGEMENT_PAGE_SHIFT"] != facts["PAGE_SHIFT"] or \
                compiled["MSU_MANAGEMENT_CAP_LAST"] != facts["CAP_LAST_CAP"]:
            raise ValueError("Management facts/layout constant mismatch")
        build_config = json.loads((target / "msu_resolver_build_config.json").read_text(encoding="utf-8"))
        record["config"] = build_config["managementConfig"]
        native_enabled = build_config["macros"].get("MSU_MANAGEMENT_NATIVE_TEXT_OWNER", 0)
        native_facts = object_words(target / "manager_management_facts.o", "msu_management_native_text_owner")
        if native_enabled not in (0, 1) or native_facts != [native_enabled] or \
                bool(native_enabled) != bool(build_config.get("nativeTextOwner", {}).get("complete")):
            raise ValueError("Native text-owner compiled facts disagree with the selected producer")
        record["nativeTextOwner"] = build_config["nativeTextOwner"]
        ucounts = build_config["macros"]["MSU_MANAGEMENT_CRED_UCOUNTS"]
        if ucounts not in (0, 1) or (layout[54] != 0xffffffff) != bool(ucounts) or \
                (not ucounts and layout[55] != 0):
            raise ValueError("Actual cred field selection differs from compiled layout")
        record["targetHeaderFeatures"] = {"credUcounts": ucounts,
            "credHeader": build_config["credHeader"], "credHeaderSha256": build_config["credHeaderSha256"]}
        record["constants"] = {name: facts[name] for name in MANAGEMENT_FACT_NAMES[1:7]}
        record["typeAlignments"] = {name: facts[name] for name in MANAGEMENT_FACT_NAMES[8:16]}
        record["controlAbi"] = {name: facts[name] for name in MANAGEMENT_FACT_NAMES[16:]}
        record["factsObjectIdentity"] = identity(target / "manager_management_facts.o")
        record["factsCommandIdentity"] = identity(target / ".manager_management_facts.o.cmd")
        record["factsDependencyIdentity"] = identity(target / "build-dependencies/manager_management_facts.json")
        record["descriptorAbi"] = descriptor_abi(target, target / "claim.so")
        record["procattrAbi"] = collect_procattr_abi(target, payload, reference)
        if not record["procattrAbi"]["metadataComplete"]:
            record["missing"].extend(record["procattrAbi"]["missing"])
        elif record["procattrAbi"]["compiledFacts"]["configured"]:
            record["enabledFeatures"].append("procattr")
        record["uidSnapshotAbi"] = collect_uid_snapshot_abi(target, payload, reference)
        if not record["uidSnapshotAbi"]["metadataComplete"]:
            record["missing"].extend(record["uidSnapshotAbi"]["missing"])
        for prefix, field_key in (("metadata", "descriptorFields"), ("metadataCommon", "commonFields")):
            for name, item in record["descriptorAbi"][field_key].items():
                key = prefix + "".join(word.capitalize() for word in name.split("_")) + "Offset"
                if record["wireLayout"][key] != item["offset"]:
                    raise ValueError("Descriptor object BTF/wire offsetof mismatch: " + key)
        own, declarations = object_btf(obj), object_btf(target / "manager_management_facts.o")
        def declaration_function(name, variable):
            pointer, evidence = retained_probe_declaration(
                target, declarations, "manager_management_facts", variable, name)
            record.setdefault("declarationEvidence", {})[name] = evidence
            return pointer[1:]
        if native_enabled:
            native_functions = {
                "kprobe_text_owner_claim": "msu_build_native_claim",
                "kprobe_text_owner_disable": "msu_build_native_disable",
                "kprobe_text_owner_release": "msu_build_native_release",
                "kprobe_text_owner_read": "msu_build_native_read",
                "maplesu_legacy_hook_provider_register": "msu_build_legacy_register",
                "maplesu_legacy_hook_provider_unregister": "msu_build_legacy_unregister",
                "maplesu_legacy_hook_provider_task_exit": "msu_build_legacy_task_exit"}
            record["nativeTextOwnerAbi"] = {}
            for name, variable in native_functions.items():
                signature = declaration_function(name, variable)
                if name not in payload["imports"] or signature != reference.function(name):
                    raise ValueError("Native text-owner import/reference ABI mismatch: " + name)
                record["nativeTextOwnerAbi"][name] = signature
        selected, evidence = selected_exit_probe(obj)
        expected = "msu_management_process_exit_group_dead" if facts["exitProbeArguments"] == 3 else "msu_management_process_exit"
        if facts["exitProbeArguments"] not in (2, 3) or selected != expected:
            raise ValueError("Header-selected exit wrapper disagrees with actual object relocations")
        record["exitSelection"] = {"function": selected, "relocations": evidence,
                                   "headerProbeArguments": facts["exitProbeArguments"]}
        callbacks = dict(MANAGEMENT_CALLBACKS, sched_process_exit=selected)
        for key, function in callbacks.items():
            if key.startswith("sched_process_"):
                register, unregister = "register_trace_" + key, "unregister_trace_" + key
                signature = declarations.function_parameter(register, 0)
                if signature != declarations.function_parameter(unregister, 0):
                    raise ValueError("Actual trace registration/unregistration ABI mismatch: " + key)
                if register in reference.functions or unregister in reference.functions:
                    target_signature = reference.function_parameter(register, 0)
                    if target_signature != reference.function_parameter(unregister, 0):
                        raise ValueError("Reference trace registration/unregistration ABI mismatch: " + key)
                else:
                    # This is the reference's real BTF probe typedef, never an iterator return type.
                    target_signature = reference.typedef_function("btf_trace_" + key)
                if signature != target_signature:
                    raise ValueError("Header/reference trace callback mismatch: " + key)
                origin = {"kind": "tracepoint", "source": register, "secondarySource": unregister,
                          "parameterIndex": 0, "signature": signature}
            elif key in ("work_struct.func", "msu_publication_cleanup"):
                signature = reference.member_function("work_struct", "func")
                if signature != reference.typedef_function("work_func_t") or \
                        signature != declarations.typedef_function("work_func_t"):
                    raise ValueError("Actual work field/typedef/header ABI mismatch")
                origin = {"kind": "work", "source": "work_struct.func", "secondarySource": "work_func_t",
                          "signature": signature}
            else:
                signature = own.function_parameter("msu_credential_replace_current", 1)
                origin = {"kind": "internal", "source": "msu_credential_replace_current",
                          "parameterIndex": 1, "signature": signature}
                record["internalCallbackProof"] = {"caller": "msu_credential_replace_current",
                    "parameterIndex": 1, "parameterSignature": signature, "definition": function,
                    "definitionSignature": own.function(function), "objectSha256": sha(obj)}
            if signature != "*" + own.function(function):
                raise ValueError("Callback declaration/actual definition mismatch: " + function)
            record["callbackOrigins"][key] = origin
        record["declarationAbi"] = {name: declaration_function(name, variable) for name, variable in {
            "cpus_read_lock": "msu_build_cpu_lock", "cpus_read_unlock": "msu_build_cpu_unlock",
            "aarch64_insn_patch_text": "msu_build_text_patch"}.items()}
        if not record["declarationAbi"]["cpus_read_lock"] == \
                record["declarationAbi"]["cpus_read_unlock"] == "fv()":
            raise ValueError("Actual CPU read lock/unlock ABI mismatch")
        mutex_types = {reference.signature(index) for index, entry in enumerate(reference.types)
                       if entry and entry[1] == 4 and reference.name(entry[0]) == "mutex"}
        if "text_mutex" not in payload["imports"] or mutex_types != {reference.variable("text_mutex")}:
            raise ValueError("Management text_mutex is not the actual reference mutex object")
        record["enabledFeatures"].extend(["text", "shared_bool"])
        shared_source = (target / "manager_management.c").read_text(encoding="utf-8")
        create_start = shared_source.index("static int msu_shared_bool_create(")
        create_end = shared_source.index("static int msu_shared_bool_update(", create_start)
        create = shared_source[create_start:create_end]
        create_source = qualification_source_body(shared_source, "msu_shared_bool_create", (
            "copy_from_user_nofault(", "return msu_shared_bool_create_domain(session, NULL, value, response);"))
        allocation_source = qualification_source_body(shared_source, "msu_shared_bool_create_domain", (
            "vmalloc(PAGE_SIZE)", "set_memory_nx((unsigned long)address, 1)",
            "set_memory_rw((unsigned long)address, 1)", "msu_management_construction_check(session, service)",
            "memset(address, 0, PAGE_SIZE)", "WRITE_ONCE(*address, value)"))
        update_source = qualification_source_body(shared_source, "msu_shared_bool_update", (
            "msu_management_check(session)", "WRITE_ONCE(*shared->address, value)"))
        permissions = {name: reference.function(name) for name in ("set_memory_nx", "set_memory_rw")}
        if not set(permissions) <= set(payload["imports"]):
            raise ValueError("Shared-bool permission functions are not actual payload imports")
        with obj.open("rb") as stream:
            symbols = ELFFile(stream).get_section_by_name(".symtab")
            imported = {symbol.name for symbol in symbols.iter_symbols() if symbol["st_shndx"] == "SHN_UNDEF"}
        allocation_imports = [name for name in payload["imports"] if name in imported and
                              (name == "vfree" or re.fullmatch(r"(?:__)?vmalloc(?:_[A-Za-z0-9]+)?", name))]
        if "vfree" not in allocation_imports or len(allocation_imports) < 2:
            raise ValueError("No actual shared-bool allocator/release import chain")
        record["sharedBoolProof"] = {
            "sourceIdentity": record["sourceIdentity"], "objectIdentity": record["objectIdentity"],
            "commandIdentity": record["commandIdentity"], "dependencyReceiptIdentity": record["dependencyReceiptIdentity"],
            "createFunction": "msu_shared_bool_create", "storeFunction": "msu_shared_bool_update",
            "createSourceSha256": hashlib.sha256(create.encode("utf-8")).hexdigest(),
            "allocationFunction": "msu_shared_bool_create_domain",
            "sourceChain": {"create": create_source, "allocation": allocation_source, "update": update_source},
            "allocationPages": 1, "allocationBytes": 1 << facts["PAGE_SHIFT"], "publicValueBytes": 1,
            "allocatorImports": {name: reference.function(name) for name in allocation_imports},
            "permissionImports": permissions, "runtimePermissionsVerified": False,
            "qualification": "Frozen same-generation source and actual import ABI only; whole-page runtime RW/NX and execution context remain unqualified",
        }
        config = record["config"]
        code = config.get("CONFIG_EXECMEM") == "y" if facts["MSU_MANAGEMENT_EXECMEM"] else config.get("CONFIG_MODULES") == "y"
        if facts["MSU_MANAGEMENT_EXECMEM"] and config.get("CONFIG_ARCH_HAS_EXECMEM_ROX") == "y" and \
                not facts["MSU_MANAGEMENT_EXECMEM_ALLOC_RW"]:
            code = False
        if code:
            record["enabledFeatures"].append("code")
        if code and record["gateVerified"] and native_enabled and config.get("CONFIG_TASKS_RCU") == "y":
            record["enabledFeatures"].append("publication")
        record["metadataComplete"] = not record["missing"]
    except (OSError, ValueError, KeyError, IndexError) as error:
        record["missing"].append(type(error).__name__ + ": " + str(error))
    context = record["executionContext"] = collect_sleepable_source_qualification(target)
    if not record["gateVerified"]:
        context["sourceQualified"] = False
        context["missing"].append("The native object has no same-generation P5 gate source binding")
    record["missing"].extend(context["missing"])
    record["metadataComplete"] = record["metadataComplete"] and context["sourceQualified"]
    context["qualificationMissing"] = ["Target CPU-hotplug/text-lock/allocator execution remains pending specified-device acceptance"]
    return record


# This fragment is inserted into the self-contained builder candidate.
CORE_SELECTION = None
CORE_BASE_SOURCES = tuple(SOURCES)
CORE_BASE_WIRE_FACTS = dict(WIRE_FACTS)
CORE_BASE_EXPORTS = EXPORTS
CORE_LAYOUT_EXPORTS = {"msu_task_state_compiled_layout": 53, "msu_credential_domain_compiled_layout": 26}
CORE_NATIVE_FUNCTIONS = ("seccomp_filter_release", "security_secctx_to_secid")
CORE_OBJECTS = ("manager_management", "manager_uid_gateway", "manager_task_state", "manager_credential_domain")
CORE_PROBES = ("manager_core_facts", "manager_core_uid_facts", "manager_core_callback_types")
CORE_LAYOUT_TYPES = {
    "profile": ("msu_credential_profile", "manager_core_facts", ("uid", "gid", "groups_count", "groups", "caps", "cap_write_mask", "replace_ids", "replace_groups", "clear_securebits", "selinux_domain")),
    "serviceDescriptor": ("msu_runtime_service_descriptor", "manager_core_facts", ("host", "kind", "lifecycle_owner", "entries", "entry_count")),
    "service": ("msu_runtime_service", "manager_core_facts", ("operation", "host", "lifecycle_owner", "id", "entries", "entry_count", "operations", "kind", "reserved", "active", "aborting", "discarded", "factory_owned", "sealed")),
    "readerFence": ("msu_uid_reader_fence", "manager_core_uid_facts", ("owner", "check_closed", "begin", "revoke", "finish")),
    "controller": ("msu_uid_controller_slot", "manager_core_uid_facts", ("state", "host", "fence", "quarantined_transaction", "id", "closed", "shutdown_pending", "fence_broken")),
}
CORE_FACTS = {"schema": "1", "runtimeMaxEntries": "MSU_RUNTIME_SERVICE_MAX_ENTRIES", "profileMaxGroups": "MSU_CRED_PROFILE_MAX_GROUPS",
              "profileDomainBytes": "MSU_CRED_PROFILE_DOMAIN_BYTES", "capAll": "MSU_CRED_CAP_ALL",
              "taskStateVerified": "MSU_MANAGEMENT_TASK_STATE_VERIFIED", "profileDomainVerified": "MSU_MANAGEMENT_PROFILE_DOMAIN_VERIFIED"}
CORE_UID_FACTS = {"schema": "1"}
for prefix, (structure, probe, members) in CORE_LAYOUT_TYPES.items():
    facts = CORE_FACTS if probe == "manager_core_facts" else CORE_UID_FACTS
    c_type = "struct " + structure
    facts[prefix + "Bytes"] = f"sizeof({c_type})"
    facts[prefix + "Align"] = f"__alignof__({c_type})"
    for member in members:
        key = prefix + "".join(word.capitalize() for word in member.split("_"))
        facts[key + "Offset"] = f"offsetof({c_type}, {member})"
        facts[key + "Bytes"] = f"sizeof((({c_type} *)0)->{member})"
CORE_FUNCTIONS = {
    "manager_management": ("msu_runtime_service_register", "msu_runtime_service_bind", "msu_runtime_service_bind_held", "msu_runtime_service_revoke",
        "msu_runtime_service_alloc", "msu_runtime_service_write", "msu_runtime_service_shared_bool_create", "msu_runtime_service_prepare",
        "msu_runtime_service_publish", "msu_runtime_service_abort_unexposed", "msu_credential_profile_validate", "msu_credential_replace_profile_current"),
    "manager_uid_gateway": ("msu_uid_gateway_create_kernel", "msu_uid_gateway_bind_reader"),
    "manager_task_state": ("msu_task_state_release_current",),
    "manager_credential_domain": ("msu_credential_domain_supported", "msu_credential_domain_prepare"),
}
CORE_CALLBACK_PARAMETERS = {"profile.validate": ("manager_management", "msu_credential_replace_profile_current", 1),
    "profile.adopt": ("manager_management", "msu_credential_replace_profile_current", 2),
    "uid.create.validate": ("manager_uid_gateway", "msu_uid_gateway_create_kernel", 1),
    "task.validate": ("manager_task_state", "msu_task_state_release_current", 0)}
CORE_TASK_LAYOUTS = {
    "task": ("task_struct", 7, 8, {"seccomp": (9, 10), "sighand": (11, 12), "thread_info": (13, 14), "stack": (15, 16)}),
    "seccomp": ("seccomp", 17, 18, {"mode": (19, 20), "filter_count": (21, 22), "filter": (23, 24)}),
    "sighand": ("sighand_struct", 25, 26, {"siglock": (27, 28)}),
    "threadInfo": ("thread_info", 29, 30, {}),
    "spinlock": ("spinlock_t", 34, 35, {"rlock": (36, 37)}),
    "rawSpinlock": ("raw_spinlock_t", 38, 39, {"raw_lock": (40, 41), "magic": (42, 43), "owner_cpu": (44, 45), "owner": (46, 47), "dep_map": (48, 49)}),
}


CORE_BASE_OBJECTS = CORE_OBJECTS
CORE_BASE_LAYOUT_TYPES = dict(CORE_LAYOUT_TYPES)
CORE_BASE_FUNCTIONS = dict(CORE_FUNCTIONS)
CORE_BASE_CALLBACK_PARAMETERS = dict(CORE_CALLBACK_PARAMETERS)
CORE_BASE_SCALAR_FACTS = dict(list(CORE_FACTS.items())[:7])


def core_revision_metadata(revision):
    global CORE_OBJECTS, CORE_LAYOUT_TYPES, CORE_FACTS, CORE_UID_FACTS, CORE_FUNCTIONS, CORE_CALLBACK_PARAMETERS
    CORE_OBJECTS = CORE_BASE_OBJECTS
    CORE_LAYOUT_TYPES = dict(CORE_BASE_LAYOUT_TYPES)
    CORE_FUNCTIONS = dict(CORE_BASE_FUNCTIONS)
    CORE_CALLBACK_PARAMETERS = dict(CORE_BASE_CALLBACK_PARAMETERS)
    if revision == 2:
        CORE_OBJECTS += ("manager_uid_snapshot",)
        CORE_LAYOUT_TYPES["readerFence"] = ("msu_uid_reader_fence", "manager_core_uid_facts",
            (*CORE_BASE_LAYOUT_TYPES["readerFence"][2], "retire"))
        CORE_LAYOUT_TYPES["controller"] = ("msu_uid_controller_slot", "manager_core_uid_facts",
            ("state", "host", "fence", "quarantined_transaction", "id", "revision", "closed", "shutdown_pending", "shutdown_complete", "fence_broken"))
        CORE_LAYOUT_TYPES["candidate"] = ("msu_uid_candidate_slot", "manager_core_uid_facts",
            ("snapshot", "controller", "kernel_owner", "id", "session", "expected_revision"))
        CORE_FUNCTIONS["manager_management"] += ("msu_management_discard_unpublished",)
        CORE_FUNCTIONS["manager_uid_gateway"] += ("msu_uid_gateway_prepare_kernel", "msu_uid_gateway_commit_kernel",
            "msu_uid_gateway_cancel_kernel", "msu_uid_gateway_discard_host_unpublished")
        CORE_FUNCTIONS["manager_uid_snapshot"] = ("msu_uid_snapshot_state_destroy_quiesced", "msu_uid_snapshot_runtime_discard")
        CORE_CALLBACK_PARAMETERS.update({"uid.prepare.validate": ("manager_uid_gateway", "msu_uid_gateway_prepare_kernel", 7),
            "uid.commit.validate": ("manager_uid_gateway", "msu_uid_gateway_commit_kernel", 5)})
    CORE_FACTS, CORE_UID_FACTS = dict(CORE_BASE_SCALAR_FACTS), {"schema": "1"}
    for prefix, (structure, probe, members) in CORE_LAYOUT_TYPES.items():
        facts = CORE_FACTS if probe == "manager_core_facts" else CORE_UID_FACTS
        c_type = "struct " + structure
        facts[prefix + "Bytes"] = f"sizeof({c_type})"
        facts[prefix + "Align"] = f"__alignof__({c_type})"
        for member in members:
            key = prefix + "".join(word.capitalize() for word in member.split("_"))
            facts[key + "Offset"] = f"offsetof({c_type}, {member})"
            facts[key + "Bytes"] = f"sizeof((({c_type} *)0)->{member})"


def core_receipt_path(item):
    path = (ROOT / item["path"]).resolve()
    if not path.is_relative_to(ROOT.resolve()):
        raise ValueError("CORE selected input escaped the workspace")
    historical_loaders = {
        "manager_claim_loader.c": "438b5ac988dcea3fd865200ce7d243a7f1c284dee1189993e361d69d6649cb38",
        "manager_claim_loader.h": "2f3812ade0d519b32a46570a421242b6785f6040a621b02807ae1db7aeb90c33",
    }
    if path.parent == (ROOT / "runtime/ultra/identity/kernel").resolve() and \
            item["sha256"] == historical_loaders.get(path.name):
        path = ROOT / ".tmp/image-adaptation-freeze/capacity-before" / path.name
    actual = identity(path)
    if any(actual[key] != item[key] for key in ("bytes", "sha256")):
        raise ValueError("CORE selected input changed: " + str(path))
    return path


CORE_BASE_SUPPORT_SOURCES = tuple(SUPPORT_SOURCES)
CORE_DEPLOYMENT_SUPPORT = tuple("runtime/ultra/identity/kernel/" + name for name in (
    "manager_claim_bootstrap.c", "manager_claim_bootstrap.h", "manager_claim_loader.h",
    "manager_deployment_namespace.h", "manager_resident_abi.h", "manager_resident_host.h"))
CORE_DEPLOYMENT_EDITS = (
    "runtime/maplesu-sdk/sdk/include/maplesu_request_private.h",
    "runtime/maplesu-sdk/sdk/manifest/0023_module_base_kernel_request.o.json",
    "runtime/maplesu-sdk/sdk/src/kernel_request/module_base_kernel_request.cpp",
    "runtime/ultra/authorization/request_protocol.h",
    "runtime/ultra/identity/kernel/manager_management.c",
    "runtime/ultra/identity/kernel/manager_resident_host.c")


def select_deployment_core_sources(path, selected):
    global CORE_SELECTION, SOURCE, SOURCES, PROTOCOL, FRAME, SUPPORT_SOURCES
    if selected.get("schema") != "maplesu-core-lifecycle-source-selection/1" or selected.get("lifecycleRevision") != 2 or selected.get("productionWritten") or not selected.get("sourceOwnerFrozen"):
        raise ValueError("Deployment CORE source/status contract mismatch")
    prior = selected["previousCoreSelection"]
    select_core_sources(core_receipt_path(prior), prior["sha256"])
    previous = CORE_SELECTION
    if len(previous["files"]) != 48 or selected["predecessorEvolution"] != core_source_evolution() or selected["predecessorBaselineInputs"] != previous["baselineInputs"]:
        raise ValueError("Deployment CORE must preserve the exact 48-input predecessor and original migration baseline")
    previous_inputs = [*core_selection_inputs(), *(core_receipt_path(row["snapshotIdentity"]) for row in previous["nativeInputs"])]
    for row in selected["historyInputs"]:
        core_receipt_path(row)
    history = {str(core_receipt_path(row)) for row in selected["historyInputs"]}
    if not {str(p.resolve()) for p in previous_inputs}.issubset(history):
        raise ValueError("Deployment CORE prior input closure is incomplete")
    receipts = selected["receipts"]
    for row in receipts.values():
        core_receipt_path(row)
    frozen = json.loads(core_receipt_path(receipts["freeze"]).read_text(encoding="utf-8"))
    baseline = json.loads(core_receipt_path(receipts["baseline"]).read_text(encoding="utf-8"))
    delta = json.loads(core_receipt_path(receipts["changeset"]).read_text(encoding="utf-8"))
    owner_selection = json.loads(core_receipt_path(receipts["ownerSelection"]).read_text(encoding="utf-8"))
    delivery = json.loads(core_receipt_path(receipts["ownerDelivery"]).read_text(encoding="utf-8"))
    for key in ("freeze", "previousCoreFreeze", "parentFreeze"):
        expected = previous["receipts"]["freeze"] if key != "freeze" else receipts["freeze"]
        if any((frozen["parentFreeze"] if key != "freeze" else owner_selection["sourceFreeze"])[k] != expected[k] for k in ("bytes", "sha256")):
            raise ValueError("Deployment CORE frozen parent/source identity mismatch")
    for field, key in (("baseline", "baseline"), ("changeset", "changeset"), ("diff", "diff"), ("coreOnlyDiff", "coreOnlyDiff"), ("hostAdoptionDiff", "hostAdoptionDiff"), ("hostSupportingSelection", "hostSupportingSelection")):
        if any(frozen[field][k] != receipts[key][k] for k in ("bytes", "sha256")):
            raise ValueError("Deployment CORE does not bind complete source receipt: " + field)
    if frozen.get("schema") != "maplesu-complete-core-lifecycle-source-freeze/1" or frozen.get("sourceFileCount") != 54 or frozen.get("parentSourceFileCount") != 48 or frozen.get("additionalHostSupportingInputs") != 6 or frozen.get("lifecycleAbiRevision") != 2 or frozen.get("newOwnerCommand") != 27 or frozen.get("existingQueryViews") != 5 or frozen.get("existingQueryCapabilitiesChanged") is not False or delta["deleted"]:
        raise ValueError("Deployment CORE54 exact source/wire evolution mismatch")
    if any(delivery["sourceFreeze"][k] != receipts["freeze"][k] or delivery["sourceSelection"][k] != receipts["ownerSelection"][k] for k in ("bytes", "sha256")) or delivery.get("sourceInputCount") != 54 or delivery.get("command") != 27:
        raise ValueError("Deployment CORE final delivery does not bind its source group")
    frozen_rows = {r["path"]: r for r in frozen["files"]}
    old_rows = {r["logicalPath"]: r["snapshotIdentity"] for r in previous["files"]}
    rows = {r["logicalPath"]: r for r in selected["files"]}
    owners = {r["logicalPath"]: r for r in owner_selection["files"]}
    if len(rows) != len(frozen_rows) or len(rows) != len(selected["files"]) or len(rows) != 54 or rows.keys() != frozen_rows.keys() or owners.keys() != rows.keys() or rows.keys() != old_rows.keys() | set(CORE_DEPLOYMENT_SUPPORT):
        raise ValueError("Deployment CORE54 exact path set mismatch")
    changed = {name for name in old_rows if any(old_rows[name][k] != frozen_rows[name][k] for k in ("bytes", "sha256"))}
    adopted_name = "runtime/ultra/bridge/installed_helper_readback.cpp"
    adopted = selected["adoptedBetweenPredecessorAndChannel"]
    if len(adopted) != 1 or adopted[0]["logicalPath"] != adopted_name or changed != set(CORE_DEPLOYMENT_EDITS) | {adopted_name}:
        raise ValueError("Deployment CORE differs from its six channel paths plus exact adopted readback predecessor")
    adopted_base = next(row for row in baseline["files"] if row["path"] == adopted_name)
    if any(adopted[0]["before"][k] != old_rows[adopted_name][k] or
           adopted[0]["after"][k] != adopted_base["production"][k] or
           adopted[0]["after"][k] != frozen_rows[adopted_name][k] for k in ("bytes", "sha256")):
        raise ValueError("Deployment CORE adopted readback does not bind prior and real current baseline")
    core_receipt_path(adopted[0]["before"])
    core_receipt_path(adopted[0]["after"])
    for name, row in rows.items():
        core_receipt_path(row["snapshotIdentity"])
        core_receipt_path(row["ownerSelected"])
        if row["ownerSelected"] != owners[name]["selected"] or any(row["snapshotIdentity"][k] != frozen_rows[name][k] or row["ownerSelected"][k] != frozen_rows[name][k] for k in ("bytes", "sha256")):
            raise ValueError("Deployment CORE selected bytes differ from owner freeze: " + name)
    original_bases = {r["path"]: r for r in baseline["files"]}
    bases = {r["logicalPath"]: r for r in selected["currentBaselineInputs"]}
    if bases.keys() != rows.keys() or original_bases.keys() != rows.keys():
        raise ValueError("Deployment CORE current production baseline closure mismatch")
    for name, row in bases.items():
        for key in ("production", "candidateBaseline"):
            item, expected = row[key], original_bases[name][key]
            if item is None or expected is None:
                if item != expected: raise ValueError("Deployment CORE baseline absence mismatch")
            elif any(item[k] != expected[k] for k in ("bytes", "sha256")):
                raise ValueError("Deployment CORE current baseline bytes differ: " + name)
            else: core_receipt_path(item)
    changes = {r["path"]: r for r in delta["files"]}
    actual = {name for name in rows if bases[name]["production"] is None or any(bases[name]["production"][k] != frozen_rows[name][k] for k in ("bytes", "sha256"))}
    if changes.keys() != actual or len(changes) != 12:
        raise ValueError("Deployment CORE exact production changeset mismatch")
    for name, row in changes.items():
        if any(row["after"][k] != frozen_rows[name][k] for k in ("bytes", "sha256")) or (row["before"] is None) != (bases[name]["production"] is None) or row["before"] is not None and any(row["before"][k] != bases[name]["production"][k] for k in ("bytes", "sha256")):
            raise ValueError("Deployment CORE production delta does not bind before/after: " + name)
    native = {r["logicalPath"]: r for r in selected["nativeInputs"]}
    expected_native = {r["logicalPath"] for r in previous["nativeInputs"]} | {"runtime/ultra/identity/kernel/manager_deployment_namespace.h"}
    source_root = (ROOT / selected["nativeSourceRoot"]).resolve()
    if native.keys() != expected_native or len(native) != len(selected["nativeInputs"]):
        raise ValueError("Deployment CORE native directory path set mismatch")
    for name, row in native.items():
        p = core_receipt_path(row["snapshotIdentity"])
        expected = rows[name]["snapshotIdentity"] if name in rows else next(r["snapshotIdentity"] for r in previous["nativeInputs"] if r["logicalPath"] == name)
        if p.parent != source_root or p.name != Path(name).name or any(row["snapshotIdentity"][k] != expected[k] or row["selectedFrom"][k] != expected[k] for k in ("bytes", "sha256")):
            raise ValueError("Deployment CORE native copy differs from frozen selected source")
    supports = {r["logicalPath"]: r for r in selected["supportInputs"]}
    expected_support = {"runtime/ultra/identity/kernel/" + p.name for p in SUPPORT_SOURCES} | {"runtime/ultra/identity/kernel/manager_deployment_namespace.h"}
    if supports.keys() != expected_support or len(supports) != 7:
        raise ValueError("Deployment CORE support input set mismatch")
    for name, row in supports.items():
        core_receipt_path(row["snapshotIdentity"])
        core_receipt_path(row["selectedFrom"])
        expected = rows[name]["snapshotIdentity"] if name in rows else row["selectedFrom"]
        if any(row["snapshotIdentity"][k] != expected[k] or row["selectedFrom"][k] != expected[k] for k in ("bytes", "sha256")):
            raise ValueError("Deployment CORE actual support source differs")
    CORE_SELECTION = dict(selected, identity=identity(path), componentCompatibilityCore=previous)
    SOURCE = source_root
    SOURCES = [core_receipt_path(r["snapshotIdentity"]) for r in selected["nativeInputs"]]
    SUPPORT_SOURCES = [core_receipt_path(r["snapshotIdentity"]) for r in selected["supportInputs"]]
    PROTOCOL = core_receipt_path(rows["runtime/ultra/authorization/request_protocol.h"]["snapshotIdentity"])
    FRAME = core_receipt_path(rows["runtime/ultra/authorization/execution_frame.h"]["snapshotIdentity"])
    return CORE_SELECTION


def deployment_core_evolution():
    selected = CORE_SELECTION
    previous = {r["path"]: r for r in selected["predecessorEvolution"]["selectedInputs"]}
    bases = {r["logicalPath"]: r for r in selected["currentBaselineInputs"]}
    native = {r["logicalPath"]: r["snapshotIdentity"] for r in selected["nativeInputs"]}
    rows = []
    for item in selected["files"]:
        name = item["logicalPath"]
        before = previous[name]["before"] if name in previous else bases[name]["production"]
        row = {"path": name, "before": None if before is None else {k: before[k] for k in ("bytes", "sha256")}, "after": item["snapshotIdentity"]}
        if name in native: row["nativeCopyIdentity"] = native[name]
        rows.append(row)
    receipts = selected["receipts"]
    return {"schema": "maplesu-core-lifecycle-source-evolution/1", "selectionIdentity": selected["identity"],
        "freezeIdentity": receipts["freeze"], "baselineIdentity": receipts["baseline"],
        "changesetIdentity": receipts["changeset"], "parentFreezeIdentity": receipts["parentFreeze"],
        "selectedInputs": rows, "productionWritten": False, "lifecycleRevision": 2,
        "previousCoreFreezeIdentity": receipts["previousCoreFreeze"], "previousCoreSelectionIdentity": selected["previousCoreSelection"],
        "predecessorEvolution": selected["predecessorEvolution"],
        "adoptedBetweenPredecessorAndChannel": selected["adoptedBetweenPredecessorAndChannel"],
        "authenticatedDeployment": {"ownerDelivery": receipts["ownerDelivery"], "ownerSelection": receipts["ownerSelection"],
            "coreOnlyDiff": receipts["coreOnlyDiff"], "hostAdoptionDiff": receipts["hostAdoptionDiff"],
            "command": 27, "namespaceWireBytes": 24, "queryViews": 5, "sourceFileCount": 54,
            "original48MigrationPredecessorsPreserved": True, "sameTargetQualified": False}}


def select_core_sources(path, expected_sha256):
    global CORE_SELECTION, SOURCE, SOURCES, PROTOCOL, FRAME, WIRE_FACTS, EXPORTS, SUPPORT_SOURCES
    path = path.resolve()
    if not path.is_relative_to(ROOT.resolve()) or sha(path) != expected_sha256:
        raise ValueError("CORE source selection identity mismatch")
    selected = json.loads(path.read_text(encoding="utf-8"))
    if selected.get("sourceChannel") == "authenticated-deployment-20261119":
        return select_deployment_core_sources(path, selected)
    SUPPORT_SOURCES = list(CORE_BASE_SUPPORT_SOURCES)
    if selected["schema"] != "maplesu-core-lifecycle-source-selection/1" or selected["productionWritten"] or not selected["sourceOwnerFrozen"]:
        raise ValueError("CORE source selection is not an independent owner-frozen input")
    revision = selected.get("lifecycleRevision", 1)
    if revision not in (1, 2):
        raise ValueError("Unsupported CORE lifecycle source revision")
    receipts = selected["receipts"]
    source_correction = "issuedDelta" in receipts
    if source_correction and revision != 2:
        raise ValueError("CORE source-only correction requires unchanged lifecycle ABI revision 2")
    if revision == 2:
        previous_path = core_receipt_path(selected["previousCoreSelection"])
        previous = select_core_sources(previous_path, selected["previousCoreSelection"]["sha256"])
        if previous.get("lifecycleRevision", 1) != (2 if source_correction else 1):
            raise ValueError("CORE lifecycle source selection requires its complete actual parent")
        if any(receipts["previousCoreFreeze"][key] != previous["receipts"]["freeze"][key] for key in ("bytes", "sha256")):
            raise ValueError("CORE lifecycle revision prior freeze mismatch")
        expected_history = {item.resolve(): {key: identity(item)[key] for key in ("bytes", "sha256")} for item in
            (*core_selection_inputs(), *(ROOT / row["snapshotIdentity"]["path"] for row in previous["nativeInputs"]))}
        actual_history = {core_receipt_path(row): {key: row[key] for key in ("bytes", "sha256")} for row in selected["historyInputs"]}
        if len(actual_history) != len(selected["historyInputs"]) or actual_history != expected_history:
            raise ValueError("CORE lifecycle prior input closure mismatch")
    for item in receipts.values():
        core_receipt_path(item)
    frozen = json.loads(core_receipt_path(receipts["freeze"]).read_text(encoding="utf-8"))
    baseline = json.loads(core_receipt_path(receipts["baseline"]).read_text(encoding="utf-8"))
    delta = json.loads(core_receipt_path(receipts["changeset"]).read_text(encoding="utf-8"))
    supplement = json.loads(core_receipt_path(receipts["supplement"]).read_text(encoding="utf-8"))
    parent = json.loads(core_receipt_path(receipts["parentFreeze"]).read_text(encoding="utf-8"))
    if frozen["schema"] != "maplesu-complete-core-lifecycle-source-freeze/1" or len(frozen["files"]) != (48 if revision == 2 else 47) or delta["deleted"]:
        raise ValueError("CORE freeze schema/path count mismatch")
    if revision == 2:
        reason = json.loads(core_receipt_path(receipts["revisionReason"]).read_text(encoding="utf-8"))
        if any(frozen["revisionReason"][key] != receipts["revisionReason"][key] for key in ("bytes", "sha256")) or \
                (reason["priorFreeze"]["sha256"] if source_correction else reason["priorFreezeSha256"]) != receipts["previousCoreFreeze"]["sha256"] or not reason["oldFreezePreserved"]:
            raise ValueError("CORE lifecycle revision reason does not bind the previous freeze")
    for key, name in (("baseline", "baseline"), ("changeset", "changeset"), ("unchangedAbiConsumerSupplement", "supplement"), ("diff", "diff"), ("lifecycleOnlyDiff", "lifecycleOnlyDiff")):
        if any(frozen[key][field] != receipts[name][field] for field in ("bytes", "sha256")):
            raise ValueError("CORE freeze does not bind receipt " + key)
    if any(baseline["priorFreeze"][field] != receipts["parentFreeze"][field] for field in ("bytes", "sha256")):
        raise ValueError("CORE baseline does not bind its task-state parent")
    for key in ("productionBaseline", "initialBaseline", "supplement", "changeset", "diff", "taskOnlyDiff"):
        item = receipts["parent" + key[0].upper() + key[1:]]
        if any(parent[key][field] != item[field] for field in ("bytes", "sha256")):
            raise ValueError("CORE task parent receipt mismatch: " + key)
    after = {row["path"]: row for row in frozen["files"]}
    if source_correction:
        issued = json.loads(core_receipt_path(receipts["issuedDelta"]).read_text(encoding="utf-8"))
        if issued["schema"] != "maplesu-core-lifecycle-source-freeze/3" or issued["sourceInputs"] != 48 or \
                issued["apiAbiWireChanged"] is not False or issued["runtimeBranchesChanged"] is not False:
            raise ValueError("CORE source-only revision issued delta contract mismatch")
        for key, name in (("parentFreeze", "previousCoreFreeze"), ("issuedDeltaReceipt", "issuedDelta"),
                          ("revision2DeltaPatch", "revision2DeltaPatch")):
            if any(frozen[key][field] != receipts[name][field] for field in ("bytes", "sha256")):
                raise ValueError("CORE full freeze does not bind its issued delta: " + key)
        for key, name in (("parentFreeze", "previousCoreFreeze"), ("revisionReason", "revisionReason"),
                          ("deltaPatch", "revision2DeltaPatch"), ("originalBaseline", "baseline")):
            if any(issued[key][field] != receipts[name][field] for field in ("bytes", "sha256")):
                raise ValueError("CORE issued delta receipt mismatch: " + key)
        old = {row["logicalPath"]: row["snapshotIdentity"] for row in previous["files"]}
        changed = {name for name in after if name not in old or any(after[name][field] != old[name][field] for field in ("bytes", "sha256"))}
        correction = "runtime/ultra/identity/kernel/manager_management.c"
        if old.keys() != after.keys() or changed != {correction} or len(issued["delta"]) != 1:
            raise ValueError("CORE source-only revision changed more than its actual single-file correction")
        delta_row = issued["delta"][0]
        if delta_row["path"] != correction or any(delta_row["before"][field] != old[correction][field] or
                delta_row["after"][field] != after[correction][field] for field in ("bytes", "sha256")):
            raise ValueError("CORE source-only revision after tuple differs from its issued correction")
        issued_rows = {row["path"]: row for row in issued["files"]}
        if issued_rows.keys() != after.keys() or any(issued_rows[name][field] != row[field]
                for name, row in after.items() for field in ("bytes", "sha256")):
            raise ValueError("CORE issued and full source freezes differ")
    before = {row["path"]: row for row in baseline["files"]}
    rows = {row["logicalPath"]: row for row in selected["files"]}
    bases = {row["logicalPath"]: row for row in selected["baselineInputs"]}
    supplements = {row["path"]: row for row in supplement["files"]}
    additions = {row["path"] for row in delta["files"] if row["before"] is None}
    if len(rows) != (48 if revision == 2 else 47) or len(before) != 38 or len(supplements) != (7 if revision == 2 else 6) or rows.keys() != after.keys() or \
            bases.keys() != before.keys() or before.keys() | additions | supplements.keys() != after.keys():
        raise ValueError("CORE exact selected path set mismatch")
    for relative, row in rows.items():
        core_receipt_path(row["snapshotIdentity"])
        if any(row["snapshotIdentity"][field] != after[relative][field] for field in ("bytes", "sha256")):
            raise ValueError("CORE selected after mismatch: " + relative)
        if relative in supplements and any(supplements[relative][field] != after[relative][field] for field in ("bytes", "sha256")):
            raise ValueError("CORE unchanged supplement differs from frozen input: " + relative)
        for key in ("production", "candidateBaseline") if relative in bases else ():
            item, original = bases[relative][key], before[relative][key]
            if item is None or original is None:
                if item != original:
                    raise ValueError("CORE baseline absence mismatch: " + relative)
            else:
                core_receipt_path(item)
                if any(item[field] != original[field] for field in ("bytes", "sha256")):
                    raise ValueError("CORE baseline bytes differ: " + relative)
    parent_rows = {row["path"]: row for row in parent["files"]}
    for relative, row in parent_rows.items():
        current = bases[relative]["candidateBaseline"]
        if current is None or any(current[field] != row[field] for field in ("bytes", "sha256")):
            raise ValueError("CORE candidate baseline differs from task parent: " + relative)
    changes = {row["path"]: row for row in delta["files"]}
    actual_changes = {name for name in after if name not in supplements and (name not in bases or bases[name]["production"] is None or any(after[name][field] != bases[name]["production"][field] for field in ("bytes", "sha256")))}
    if changes.keys() != actual_changes:
        raise ValueError("CORE exact production delta path set mismatch")
    for relative, row in changes.items():
        if any(row["after"][field] != after[relative][field] for field in ("bytes", "sha256")):
            raise ValueError("CORE delta after mismatch: " + relative)
        item = bases.get(relative, {}).get("production")
        if (item is None) != (row["before"] is None) or item is not None and any(row["before"][field] != item[field] for field in ("bytes", "sha256")):
            raise ValueError("CORE delta before mismatch: " + relative)
    expected_native = {"runtime/ultra/identity/kernel/" + path.name for path in CORE_BASE_SOURCES} | {
        "runtime/ultra/identity/kernel/" + name for name in ("manager_task_state.c", "manager_task_state.h", "manager_credential_domain.c", "manager_credential_domain.h", "manager_credential_profile.h")}
    native = {row["logicalPath"]: row for row in selected["nativeInputs"]}
    if len(native) != len(selected["nativeInputs"]) or native.keys() != expected_native:
        raise ValueError("CORE native source path set mismatch")
    source_root = (ROOT / selected["nativeSourceRoot"]).resolve()
    for relative, row in native.items():
        source = core_receipt_path(row["snapshotIdentity"])
        if source.parent != source_root or source.name != Path(relative).name:
            raise ValueError("CORE native source directory mismatch")
        expected = rows.get(relative, {}).get("snapshotIdentity", row["selectedFrom"])
        if any(row["snapshotIdentity"][field] != expected[field] for field in ("bytes", "sha256")):
            raise ValueError("CORE native view differs from selected source: " + relative)
    CORE_SELECTION = dict(selected, identity=identity(path))
    core_revision_metadata(revision)
    SOURCE = source_root
    SOURCES = [ROOT / row["snapshotIdentity"]["path"] for row in selected["nativeInputs"]]
    PROTOCOL = ROOT / rows["runtime/ultra/authorization/request_protocol.h"]["snapshotIdentity"]["path"]
    FRAME = ROOT / rows["runtime/ultra/authorization/execution_frame.h"]["snapshotIdentity"]["path"]
    WIRE_FACTS = dict(CORE_BASE_WIRE_FACTS, ownerCapabilities=("MAPLESU_OWNER_CAPABILITIES", 262119), queryViews=("5", 5),
        taskStateCommand=("MAPLESU_REQUEST_TASK_STATE", 26), taskStateCapability=("MAPLESU_CAP_TASK_STATE", 131072),
        taskQueryArg0=("MAPLESU_OWNER_TASK_QUERY", 260096), taskStateChangedEffect=("MAPLESU_EFFECT_TASK_STATE_CHANGED", 131072),
        taskStateCompletedEffect=("MAPLESU_EFFECT_TASK_STATE_COMPLETED", 262144))
    EXPORTS = CORE_BASE_EXPORTS + tuple(CORE_LAYOUT_EXPORTS) + task_profile_exports()
    return CORE_SELECTION


def core_baseline_input(relative, current):
    if CORE_SELECTION is not None:
        rows = {row["logicalPath"]: row for row in CORE_SELECTION["baselineInputs"]}
        for row in CORE_SELECTION.get("currentBaselineInputs", ()):
            rows.setdefault(row["logicalPath"], row)
        if relative in rows and rows[relative]["production"] is not None:
            return core_receipt_path(rows[relative]["production"])
    return current


def core_selection_inputs():
    if CORE_SELECTION is None:
        return []
    inputs = [ROOT / item["snapshotIdentity"]["path"] for item in CORE_SELECTION["files"]]
    inputs.extend(ROOT / item["path"] for item in CORE_SELECTION["receipts"].values())
    inputs.extend(ROOT / item[key]["path"] for item in CORE_SELECTION["baselineInputs"]
        for key in ("production", "candidateBaseline") if item[key] is not None)
    inputs.extend(core_receipt_path(item) for item in CORE_SELECTION.get("historyInputs", ()))
    return list(dict.fromkeys([*inputs, Path(CORE_SELECTION["identity"]["path"])]))


def core_pending_abi():
    return {"schema": "maplesu-core-lifecycle-abi/1", "schemaVersion": 1,
        "sourceSelection": core_public_selection(), "metadataComplete": False, "kernelMetadataComplete": False,
        "targetQualified": False, "taskStateQualified": False, "profileDomainQualified": False,
        "factoryBound": False, "lifecycleQualified": False, "sourceIdentities": [], "objectIdentities": [],
        "commandIdentities": [], "dependencyReceiptIdentities": [], "inlineDependencies": [],
        "compiledFacts": {}, "uidCompiledFacts": {}, "compiledLayouts": {}, "localFunctionAbi": {},
        "callbackParameters": {}, "readerFenceAbi": {}, "entryCfi": {}, "entrypoints": {},
        "taskState": {}, "credentialDomain": {}, "nativeFunctionAbi": {}, "config": {}, "missing": [
            "Actual boot service factory, reader/provider lifetime and target execution qualification remain required"]}


def core_probe_source(group):
    if group == "manager_core_facts":
        include, facts = '#include "manager_management.c"\n', CORE_FACTS
    else:
        include, facts = '#include "manager_uid_gateway.c"\n', CORE_UID_FACTS
    pointers = "".join("static struct " + structure + " *msu_build_core_" + prefix + " __used;\n"
        for prefix, (structure, probe, _) in CORE_LAYOUT_TYPES.items() if probe == group)
    return include + pointers + "const unsigned int msu_core_build_facts[] = {\n" + \
        "".join("    " + expression + ",\n" for expression in facts.values()) + "};\n"


def core_callback_type_source():
    include = '#include "manager_uid_snapshot.h"\n#include <linux/compiler.h>\n#include <linux/security.h>\n#include <linux/seccomp.h>\n#include "manager_management.h"\n#include "manager_credential_profile.h"\n#include "manager_task_state.h"\n#include "manager_credential_domain.h"\n#include "manager_uid_gateway.h"\n'
    text = re.sub(r"/\*.*?\*/|//[^\n]*", "", selected_native_source("manager_task_state.c").read_text(encoding="utf-8"), flags=re.S)
    native = re.findall(r"\bextern\b[^;{}]*\bseccomp_filter_release\s*\([^;{}]*\)\s*;", text)
    if len(native) != 1:
        raise ValueError("CORE native helper declaration missing/ambiguous in selected source")
    domain = selected_native_source("manager_credential_domain.c").read_text(encoding="utf-8")
    required = re.findall(r'__builtin_types_compatible_p\(typeof\(&security_secctx_to_secid\),\s*(int\s*\(\s*\*\s*\)\s*\([^;]+?\))\)', domain)
    if len(required) != 1:
        raise ValueError("CORE domain resolver source-required type missing/ambiguous")
    named = re.sub(r'\(\s*\*\s*\)', '(*msu_core_domain_source_decl)', required[0], count=1)
    declarations = '#ifdef CONFIG_SECCOMP_FILTER\n' + native[0] + '\nstatic typeof(&seccomp_filter_release) msu_core_filter_release_decl __used = seccomp_filter_release;\n__ADDRESSABLE(seccomp_filter_release);\n#endif\n#ifdef CONFIG_SECURITY\nstatic typeof(&security_secctx_to_secid) msu_core_domain_resolver_decl __used = security_secctx_to_secid;\nstatic ' + named + ' __used;\n__ADDRESSABLE(security_secctx_to_secid);\n#endif\n'
    return include + declarations + "".join("__ADDRESSABLE(" + name + ");\n" for names in CORE_FUNCTIONS.values() for name in names)



def core_public_selection():
    return {"identity": CORE_SELECTION["identity"], "freezeIdentity": CORE_SELECTION["receipts"]["freeze"],
            **({"sourceCorrections": SOURCE_CORRECTIONS} if SOURCE_CORRECTIONS is not None else {}),
            "parentFreezeIdentity": CORE_SELECTION["receipts"]["parentFreeze"], "sourceOwnerFrozen": True,
            "productionWritten": False, "lifecycleRevision": CORE_SELECTION.get("lifecycleRevision", 1),
            "sourceFileCount": len(CORE_SELECTION["files"]),
            **({"previousCoreFreezeIdentity": CORE_SELECTION["receipts"]["previousCoreFreeze"],
                "previousCoreSelectionIdentity": CORE_SELECTION["previousCoreSelection"]}
               if CORE_SELECTION.get("lifecycleRevision", 1) == 2 else {})}


def core_source_evolution():
    if CORE_SELECTION.get("sourceChannel") == "authenticated-deployment-20261119":
        return deployment_core_evolution()
    baseline = {row["logicalPath"]: row for row in CORE_SELECTION["baselineInputs"]}
    native = {row["logicalPath"]: row["snapshotIdentity"] for row in CORE_SELECTION["nativeInputs"]}
    supplement = json.loads(core_receipt_path(CORE_SELECTION["receipts"]["supplement"]).read_text(encoding="utf-8"))
    unchanged = {row["path"]: row for row in supplement["files"]}
    rows = []
    for item in CORE_SELECTION["files"]:
        relative = item["logicalPath"]
        before = baseline.get(relative, {}).get("production", unchanged.get(relative))
        row = {"path": relative, "before": None if before is None else
               {key: before[key] for key in ("bytes", "sha256")}, "after": item["snapshotIdentity"]}
        if relative in native:
            row["nativeCopyIdentity"] = native[relative]
        rows.append(row)
    receipts = CORE_SELECTION["receipts"]
    return {"schema": "maplesu-core-lifecycle-source-evolution/1", "selectionIdentity": CORE_SELECTION["identity"],
            "freezeIdentity": receipts["freeze"], "baselineIdentity": receipts["baseline"],
            "changesetIdentity": receipts["changeset"], "parentFreezeIdentity": receipts["parentFreeze"],
            "selectedInputs": rows, "productionWritten": False,
            "lifecycleRevision": CORE_SELECTION.get("lifecycleRevision", 1),
            **({"previousCoreFreezeIdentity": receipts["previousCoreFreeze"],
                "previousCoreSelectionIdentity": CORE_SELECTION["previousCoreSelection"]}
               if CORE_SELECTION.get("lifecycleRevision", 1) == 2 else {})}


def core_cfi_policy(command):
    tokens = command.read_text(encoding="utf-8").splitlines()[0].split()
    enabled, present = False, False
    for token in tokens:
        if token.startswith(("-fsanitize=", "-fno-sanitize=")) and "kcfi" in token.split("=", 1)[1].split(","):
            present = True
            enabled = token.startswith("-fsanitize=")
    normalized = any(token in ("-fsanitize-cfi-icall-normalize-integers", "-fsanitize-cfi-icall-experimental-normalize-integers") for token in tokens)
    if present and not enabled:
        raise ValueError("CORE object disabled kernel KCFI: " + command.name)
    return {"kcfiOptionPresent": present, "kcfiEnabled": enabled, "normalizeIntegers": normalized}


def core_layout_record(target, payload, name, header, enum_name, expected_words):
    obj = target / (name + ".o")
    proof = object_provenance(target, name)
    symbol_name = "msu_task_state_compiled_layout" if name == "manager_task_state" else "msu_credential_domain_compiled_layout"
    exported = payload["compiledLayouts"][symbol_name]
    words = object_words(obj, symbol_name)
    enumeration = re.search(r"enum\s+" + enum_name + r"\s*\{([^}]*)\}", core_selected_source(header).read_text(encoding="utf-8"), re.S)
    names = re.findall(r"\bMSU_[A-Z_]+\b", enumeration.group(1)) if enumeration else []
    if len(words) != expected_words or len(names) != expected_words + 1 or names[-1].endswith("_LAYOUT_WORDS") is False or words != exported["values"] or words[0] != 1:
        raise ValueError("CORE exact compiled layout/schema/export mismatch: " + name)
    with obj.open("rb") as stream:
        elf = ELFFile(stream)
        symbols = elf.get_section_by_name(".symtab").get_symbol_by_name(symbol_name) or []
        if len(symbols) != 1 or symbols[0]["st_info"]["type"] != "STT_OBJECT":
            raise ValueError("CORE compiled layout object missing/ambiguous: " + name)
        section = elf.get_section(symbols[0]["st_shndx"])
        expected_section = ".msu.task_state.layout" if name == "manager_task_state" else ".msu.credential_domain.layout"
        if section.name != expected_section or section["sh_flags"] & 7 != 2 or symbols[0]["st_size"] != expected_words * 4:
            raise ValueError("CORE compiled layout is not the independent immutable object: " + name)
    return {"sourceIdentity": identity(selected_native_source(name + ".c")), "headerIdentity": identity(core_selected_source(header)),
        "objectIdentity": identity(obj), "commandIdentity": identity(target / ("." + name + ".o.cmd")),
        "dependencyReceiptIdentity": identity(target / "build-dependencies" / (name + ".json")),
        "dependencies": proof["dependencies"], "layoutSchema": 1, "layoutWords": expected_words,
        "layoutNames": names[:-1], "layoutValues": words, "compiledLayoutSha256": hashlib.sha256(
            b"".join(value.to_bytes(4, "little") for value in words)).hexdigest(),
        "symbol": symbol_name, "exportOffset": exported["offset"], "exportBytes": exported["bytes"]}


def core_function_entries(target, payload, policies, definitions, functions=None,
                          probe_name="manager_core_callback_types"):
    functions = CORE_FUNCTIONS if functions is None else functions
    entries, cfi = {}, {"mode": "kcfi" if policies["manager_management"]["kcfiEnabled"] else "none",
        "normalizeIntegers": policies["manager_management"]["normalizeIntegers"], "objectPolicies": policies, "functions": {}}
    enabled = cfi["mode"] == "kcfi"
    with (target / (probe_name + ".o")).open("rb") as probe_stream, (target / "claim.so").open("rb") as claim_stream:
        declarations = ELFFile(probe_stream).get_section_by_name(".symtab")
        final_elf = ELFFile(claim_stream)
        final_table = final_elf.get_section_by_name(".symtab")
        for object_name, names in functions.items():
            with (target / (object_name + ".o")).open("rb") as stream:
                elf = ELFFile(stream)
                table = elf.get_section_by_name(".symtab")
                for name in names:
                    symbols = table.get_symbol_by_name(name) or []
                    if len(symbols) != 1 or symbols[0]["st_info"]["type"] != "STT_FUNC":
                        raise ValueError("CORE actual function missing/ambiguous: " + name)
                    symbol = symbols[0]
                    section = elf.get_section(symbol["st_shndx"])
                    offset = symbol["st_value"] - section["sh_addr"]
                    if not section["sh_flags"] & 4 or symbol["st_size"] <= 0 or offset < 0 or offset + symbol["st_size"] > section["sh_size"]:
                        raise ValueError("CORE actual function bounds invalid: " + name)
                    item = {"function": name, "signature": definitions[object_name].function(name),
                        "objectIdentity": identity(target / (object_name + ".o")), "verified": not enabled}
                    if enabled:
                        types = declarations.get_symbol_by_name("__kcfi_typeid_" + name) or []
                        if len(types) != 1 or types[0]["st_shndx"] != "SHN_ABS" or offset < 4:
                            raise ValueError("CORE declaration KCFI typeid/prefix missing: " + name)
                        typeid = types[0]["st_value"] & 0xffffffff
                        prefix = int.from_bytes(section.data()[offset - 4:offset], "little")
                        if prefix != typeid:
                            raise ValueError("CORE declaration/definition KCFI mismatch: " + name)
                        item.update(typeidSymbol="__kcfi_typeid_" + name, typeid=typeid,
                            functionOffset=symbol["st_value"], prefixOffset=symbol["st_value"] - 4,
                            prefixWord=prefix, verified=True)
                    cfi["functions"][name] = item
                    final = final_table.get_symbol_by_name(name) or []
                    if not final:
                        entries[name] = {"available": False, "reason": "No retained actual runtime consumer/factory root"}
                        continue
                    if len(final) != 1 or final[0]["st_info"]["type"] != "STT_FUNC":
                        raise ValueError("CORE final function missing/ambiguous: " + name)
                    function = final[0]
                    output = final_elf.get_section(function["st_shndx"])
                    at = function["st_value"] - output["sh_addr"]
                    size = function["st_size"]
                    if not output["sh_flags"] & 4 or not size or function["st_value"] % 4 or at < 0 or at + size > output["sh_size"]:
                        raise ValueError("CORE final executable entry bounds invalid: " + name)
                    entry = entries[name] = {"available": True, "entryOffset": function["st_value"],
                        "fileOffset": output["sh_offset"] + at, "bytes": size, "sectionName": output.name,
                        "sectionOffset": output["sh_addr"], "sectionBytes": output["sh_size"], "binding": function["st_info"]["bind"]}
                    if enabled:
                        if at < 4 or int.from_bytes(output.data()[at - 4:at], "little") != item["typeid"]:
                            raise ValueError("CORE retained final entry KCFI prefix mismatch: " + name)
                        entry.update(prefixOffset=function["st_value"] - 4, prefixFileOffset=output["sh_offset"] + at - 4,
                            prefixWord=item["typeid"], typeid=item["typeid"], prefixVerified=True)
    return entries, cfi


def core_named_layout(btf, name, fields):
    aliases = [btf.resolve(index) for index, entry in enumerate(btf.types)
        if entry and entry[1] == 8 and btf.name(entry[0]) == name]
    if aliases:
        if len(set(aliases)) != 1 or btf.types[aliases[0]][1] != 4:
            raise ValueError("CORE required typedef is not one actual structure: " + name)
        name = btf.name(btf.types[aliases[0]][0])
    return btf.named_structure_selected(name, fields)


def retained_probe_declaration(target, declarations, probe_name, name, callee=None):
    """Bind one emitted VAR to its same-object ELF type and optional callee relocation."""
    indexes = declarations.variables.get(name, ())
    if len(indexes) != 1:
        raise ValueError("Missing/ambiguous retained declaration VAR: " + name)
    signature = declarations.variable(name)
    if not signature.startswith("*f") or declarations.type_size(declarations.types[indexes[0]][3]) != 8:
        raise ValueError("Retained declaration is not an actual AArch64 function pointer: " + name)
    obj = target / (probe_name + ".o")
    provenance = object_provenance(target, probe_name)
    with obj.open("rb") as stream:
        elf = ELFFile(stream)
        if elf["e_machine"] != "EM_AARCH64" or elf["e_type"] != "ET_REL":
            raise ValueError("Declaration probe is not an actual AArch64 relocatable object")
        table = elf.get_section_by_name(".symtab")
        matches = table.get_symbol_by_name(name) if table else None
        if not matches or len(matches) != 1:
            raise ValueError("Retained declaration ELF symbol missing/ambiguous: " + name)
        symbol = matches[0]
        if symbol["st_info"]["type"] != "STT_OBJECT" or not isinstance(symbol["st_shndx"], int):
            raise ValueError("Retained declaration is not one defined ELF object: " + name)
        section = elf.get_section(symbol["st_shndx"])
        offset = symbol["st_value"] - section["sh_addr"]
        if symbol["st_size"] != 8 or offset < 0 or offset + 8 > section["sh_size"]:
            raise ValueError("Retained declaration pointer ELF bounds differ: " + name)
        relocations = []
        for item in elf.iter_sections():
            if not isinstance(item, RelocationSection) or item["sh_info"] != symbol["st_shndx"]:
                continue
            symbols = elf.get_section(item["sh_link"])
            for relocation in item.iter_relocations():
                if relocation["r_offset"] != symbol["st_value"]:
                    continue
                relocations.append(dict(section=item.name, offset=relocation["r_offset"],
                    type=relocation["r_info_type"],
                    symbol=symbols.get_symbol(relocation["r_info_sym"]).name,
                    addend=relocation["r_addend"] if relocation.is_RELA() else None))
        if callee is not None and (len(relocations) != 1 or
                relocations[0]["symbol"] != callee or relocations[0]["type"] != 257 or
                relocations[0]["addend"] != 0):
            raise ValueError("Retained declaration lacks the exact callee ABS64 relocation: " + name)
    return signature, dict(method="same-object-BTF-VAR-ELF-relocation",
        variable=name, signature=signature, btfTypeId=indexes[0],
        objectIdentity=identity(obj), sourceIdentity=provenance["sourceIdentity"],
        commandIdentity=identity(target / ("." + probe_name + ".o.cmd")),
        dependencyReceiptIdentity=identity(target / "build-dependencies" / (probe_name + ".json")),
        section=section.name, offset=offset, bytes=8, relocations=relocations)


def reference_import_signature(target, reference, name):
    """Use the actual reference ELF symbol class, including objects lacking BTF VARs."""
    symbols = target / "reference.symbols"
    matches = [line.split() for line in symbols.read_text(encoding="utf-8").splitlines()
        if len(line.split()) == 8 and line.split()[7] == name and line.split()[6] != "UND"]
    if len(matches) != 1 or matches[0][3] not in ("OBJECT", "FUNC"):
        raise ValueError("Missing/ambiguous actual reference import symbol kind: " + name)
    return reference.variable(name) if matches[0][3] == "OBJECT" else reference.function(name)


def core_native_function_names(target):
    config = json.loads((target / "msu_resolver_build_config.json").read_text(encoding="utf-8"))
    return tuple(name for name in CORE_NATIVE_FUNCTIONS
        if name != "seccomp_filter_release" or config["seccompReleaseApi"]["enabled"])


def core_native_functions(target, reference, declarations, policy, record,
                          names=None, variables=None,
                          probe_name="manager_core_callback_types"):
    evidence_path = target / "reference-function-markers.json"
    evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
    source = json.loads((target / "reference-source.json").read_text(encoding="utf-8"))
    if evidence.get("schemaVersion") != 1 or evidence["sourceBinding"] != {name: source[name] for name in
            ("sourceFileBytes", "btfSha256", "symtabSha256", "sectionHeadersSha256")}:
        raise ValueError("CORE native marker/reference generation mismatch")
    if names is None:
        names = core_native_function_names(target)
    if variables is None:
        variables = {"seccomp_filter_release": "msu_core_filter_release_decl",
            "security_secctx_to_secid": "msu_core_domain_resolver_decl"}
        variables = {name: variables[name] for name in names}
    if set(variables) != set(names):
        raise ValueError("Native function/probe declaration set differs")
    for name in names:
        item = record["nativeFunctionAbi"][name] = {"verified": False}
        try:
            declared, item["declarationEvidence"] = retained_probe_declaration(
                target, declarations, probe_name, variables[name], name)
            item["declarationSignature"] = declared[1:]
            required, item["sourceDeclarationEvidence"] = retained_probe_declaration(
                target, declarations, probe_name, "msu_core_domain_source_decl") \
                if name == "security_secctx_to_secid" else (declared, item["declarationEvidence"])
            item["sourceRequiredSignature"] = required[1:]
            item["signature"] = reference.function(name)
            if name == "seccomp_filter_release":
                config = json.loads((target / "msu_resolver_build_config.json").read_text(encoding="utf-8"))
                item["bodyTraits"] = config["seccompReleaseApi"]
                if not item["bodyTraits"]["enabled"] or config["macros"]["MSU_TASK_SECCOMP_RELEASE_LOCKS_TASK"] != int(item["bodyTraits"]["locksTask"]):
                    raise ValueError("Native seccomp release body/config selection differs")
            if item["sourceRequiredSignature"] != item["declarationSignature"] or item["declarationSignature"] != item["signature"]:
                raise ValueError("Source-required/declaration/reference function ABI differs")
            marker = item["marker"] = evidence.get("coreMarkers", {}).get(name, {})
            if not marker.get("available") or marker.get("function") != name:
                raise ValueError(marker.get("reason", "No unique native function marker"))
            cfi = item["cfi"] = {"mode": "kcfi" if policy["kcfiEnabled"] else "none",
                "normalizeIntegers": policy["normalizeIntegers"],
                "typeProbeObjectIdentity": identity(target / (probe_name + ".o")),
                "typeProbeCommandIdentity": identity(target / ("." + probe_name + ".o.cmd")),
                "typeProbeDependencyReceiptIdentity": identity(target / "build-dependencies" / (probe_name + ".json")),
                "referenceEvidenceIdentity": identity(evidence_path)}
            if policy["kcfiEnabled"]:
                with (target / (probe_name + ".o")).open("rb") as stream:
                    symbols = ELFFile(stream).get_section_by_name(".symtab").get_symbol_by_name("__kcfi_typeid_" + name) or []
                    if len(symbols) != 1 or symbols[0]["st_shndx"] != "SHN_ABS":
                        raise ValueError("Actual native declaration KCFI typeid missing/ambiguous")
                    typeid = symbols[0]["st_value"] & 0xffffffff
                if marker["address"] <= 4 or marker.get("prefixAddress") != marker["address"] - 4 or \
                        not isinstance(marker.get("prefixWord"), int) or marker["prefixWord"] != typeid:
                    raise ValueError("Actual native declaration/reference KCFI prefix differs")
                cfi.update(typeidSymbol="__kcfi_typeid_" + name, declarationTypeid=typeid,
                    referencePrefixWord=marker["prefixWord"], referencePrefixVerified=True)
            item["verified"] = True
        except (OSError, ValueError, KeyError, IndexError) as error:
            item["reason"] = str(error)
            record["missing"].append("Native function " + name + ": " + str(error))


def collect_core_lifecycle_abi(target, payload, reference):
    record = core_pending_abi()
    try:
        build_inputs = json.loads((target / "build-inputs.json").read_text(encoding="utf-8"))
        if build_inputs.get("coreSourceSelection") != core_public_selection() or \
                build_inputs.get("coreRuntimeSourceSelection") != core_runtime_public_selection():
            raise ValueError("CORE target generation does not bind the selected frozen source")
        for name in (*CORE_OBJECTS, *CORE_PROBES):
            source = target / (name + ".c")
            proof = object_provenance(target, name)
            if proof["sourceIdentity"]["sha256"] != sha(source):
                raise ValueError("CORE object source/dependency generation mismatch: " + name)
            if name in CORE_OBJECTS:
                if sha(source) != sha(selected_native_source(source.name)):
                    raise ValueError("CORE actual object source differs from effective native selection: " + name)
            else:
                expected = core_callback_type_source() if name == "manager_core_callback_types" else core_probe_source(name)
                if source.read_bytes() != expected.encode("utf-8"):
                    raise ValueError("CORE generated probe differs from selected actual definitions: " + name)
            record["sourceIdentities"].append(identity(source))
            record["objectIdentities"].append(identity(target / (name + ".o")))
            record["commandIdentities"].append(identity(target / ("." + name + ".o.cmd")))
            record["dependencyReceiptIdentities"].append(identity(target / "build-dependencies" / (name + ".json")))
            record["inlineDependencies"].extend(proof["dependencies"])
        for name, facts_key, expected in (("manager_core_facts", "compiledFacts", CORE_FACTS), ("manager_core_uid_facts", "uidCompiledFacts", CORE_UID_FACTS)):
            values = object_words(target / (name + ".o"), "msu_core_build_facts")
            if len(values) != len(expected) or values[0] != 1:
                raise ValueError("CORE actual C facts count/schema mismatch: " + name)
            record[facts_key] = dict(zip(expected, values))
        if any(record["compiledFacts"][key] for key in ("taskStateVerified", "profileDomainVerified")):
            raise ValueError("CORE task/domain qualification enabled without selected target proof")
        objects = {name: object_btf(target / (name + ".o")) for name in (*CORE_OBJECTS, *CORE_PROBES)}
        for prefix, (structure, probe, members) in CORE_LAYOUT_TYPES.items():
            layout = objects[probe].named_structure_selected(structure, members)
            facts = record["compiledFacts"] if probe == "manager_core_facts" else record["uidCompiledFacts"]
            if layout["bytes"] != facts[prefix + "Bytes"] or facts[prefix + "Align"] <= 0:
                raise ValueError("CORE BTF/C type size mismatch: " + prefix)
            for member in members:
                key = prefix + "".join(word.capitalize() for word in member.split("_"))
                item = layout["members"][member]
                if item["offset"] != facts[key + "Offset"] or item["bytes"] != facts[key + "Bytes"]:
                    raise ValueError("CORE BTF/C field mismatch: " + prefix + "." + member)
            record["compiledLayouts"][prefix] = layout
        record["localFunctionAbi"] = {name: objects[obj].function(name) for obj, names in CORE_FUNCTIONS.items() for name in names}
        record["callbackParameters"] = {key: {"object": obj, "source": function, "parameterIndex": index,
            "signature": objects[obj].function_parameter(function, index)} for key, (obj, function, index) in CORE_CALLBACK_PARAMETERS.items()}
        record["readerFenceAbi"] = {member: objects["manager_core_uid_facts"].member_function("msu_uid_reader_fence", member)
            for member in CORE_LAYOUT_TYPES["readerFence"][2] if member != "owner"}
        for key in record["callbackParameters"]:
            if not record["callbackParameters"][key]["signature"].startswith("*f"):
                raise ValueError("CORE callback parameter is not a real function pointer: " + key)
        policies = {name: core_cfi_policy(target / ("." + name + ".o.cmd")) for name in (*CORE_OBJECTS, *CORE_PROBES)}
        if len({tuple(row.values()) for row in policies.values()}) != 1:
            raise ValueError("CORE objects/probes use inconsistent actual CFI policies")
        config = json.loads((target / "msu_resolver_build_config.json").read_text(encoding="utf-8"))
        record["config"] = {key: config["managementConfig"].get(key, "n") for key in (
            "CONFIG_SECCOMP", "CONFIG_SECCOMP_FILTER", "CONFIG_GENERIC_ENTRY", "CONFIG_THREAD_INFO_IN_TASK", "CONFIG_PREEMPT_RT", "CONFIG_SECURITY_SELINUX", "CONFIG_SECURITY", "CONFIG_CFI", "CONFIG_CFI_CLANG", "CONFIG_CFI_ICALL_NORMALIZE_INTEGERS")}
        policy = policies["manager_management"]
        enabled = any(record["config"][name] == "y" for name in ("CONFIG_CFI", "CONFIG_CFI_CLANG"))
        if policy["kcfiEnabled"] != enabled or enabled and policy["normalizeIntegers"] != \
                (record["config"]["CONFIG_CFI_ICALL_NORMALIZE_INTEGERS"] == "y"):
            raise ValueError("CORE actual object/reference configuration CFI mismatch")
        record["entrypoints"], record["entryCfi"] = core_function_entries(target, payload, policies, objects)
        task = record["taskState"] = core_layout_record(target, payload, "manager_task_state", "manager_task_state.h", "msu_task_state_layout_word", 53)
        domain = record["credentialDomain"] = core_layout_record(target, payload, "manager_credential_domain", "manager_credential_domain.h", "msu_credential_domain_layout_word", 26)
        if domain["layoutValues"][2] != 0:
            raise ValueError("CORE native domain branch enabled without target qualification")
        task_layout = task["layoutValues"]
        task["compiledLayouts"], task["targetLayouts"] = {}, {}
        if reference is None:
            raise ValueError("CORE linked vmlinux reference ABI is missing")
        for key, (type_name, size_at, align_at, members) in CORE_TASK_LAYOUTS.items():
            fields = dict(members)
            if key == "threadInfo":
                fields["syscall_work" if task_layout[4] else "flags"] = (31, 32)
            fields = {member: indices for member, indices in fields.items() if task_layout[indices[0]] != 0xffffffff and task_layout[indices[1]] > 0}
            if task_layout[size_at] == 0xffffffff:
                continue
            actual = core_named_layout(objects["manager_task_state"], type_name, fields)
            native = core_named_layout(reference, type_name, fields)
            if actual != native or actual["bytes"] != task_layout[size_at] or task_layout[align_at] <= 0:
                raise ValueError("CORE task compiled/reference type mismatch: " + key)
            for member, indices in fields.items():
                if actual["members"][member]["offset"] != task_layout[indices[0]] or actual["members"][member]["bytes"] != task_layout[indices[1]]:
                    raise ValueError("CORE task compiled/header field mismatch: " + key + "." + member)
            task["compiledLayouts"][key], task["targetLayouts"][key] = actual, native
        core_native_functions(target, reference, objects["manager_core_callback_types"], policy, record)
        record["kernelMetadataComplete"] = len(record["missing"]) == 1
    except (OSError, ValueError, KeyError, IndexError) as error:
        record["missing"].append(type(error).__name__ + ": " + str(error))
    source = record["sourceQualification"] = collect_runtime_source_qualification(target, payload)
    record["missing"].extend(source["missing"])
    record["kernelMetadataComplete"] = record["kernelMetadataComplete"] and source["metadataComplete"]
    if source["metadataComplete"]:
        record["missing"] = ["Actual target execution qualification remains required" if reason ==
            "Actual boot service factory, reader/provider lifetime and target execution qualification remain required"
            else reason for reason in record["missing"]]
    return record


SU_SELECTION = None
SU_NATIVE_LOGICAL_ROOT = "runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/"
SU_SOURCE_NAMES = ("su_install_registry.c", "su_install_registry.h", "su_native_reader_abi.h")
SU_LAYOUT_TYPES = {
    "profile": ("msu_su_profile", ("uid", "gid", "groups_count", "groups", "inheritable", "permitted", "effective", "bounding", "ambient", "selinux_domain", "namespace_mode", "flags", "use_default", "non_root", "umount_modules", "template_name")),
    "installMember": ("msu_su_install_member", ("package",)),
    "verifiedInstall": ("msu_su_verified_install", ("full_uid", "installation_id", "pin", "members", "member_count")),
    "savedGrant": ("msu_su_saved_grant", ("full_uid", "decision", "installation_id", "profile")),
    "registrySource": ("msu_su_registry_source", ("owner", "pin_current", "put_install", "read_grant", "validate_profile")),
    "revokeReceipt": ("msu_su_revoke_receipt", ("generation", "full_uid", "inflight")),
    "lease": ("msu_su_lease", ("registry", "entry", "snapshot", "task", "cred", "generation", "invalidated")),
    "registry": ("msu_su_registry", ("lock", "host", "controller", "next_generation", "source", "count", "closed", "producer", "entries")),
    "update": ("msu_su_update", ("registry", "records", "expected_revision", "count")),
    "installEntry": ("msu_su_install_entry", ("full_uid", "users", "generation", "revoked", "busy", "cancelled", "granted", "installation", "profile")),
}
SU_FACTS = {"schema": "1", "registryMaxUids": "MSU_SU_REGISTRY_MAX_UIDS",
            "installationIdBytes": "MSU_SU_INSTALL_ID_BYTES", "profileGroups": "MSU_SU_PROFILE_GROUPS",
            "profileContextBytes": "MSU_SU_PROFILE_CONTEXT", "installMaxMembers": "MSU_SU_INSTALL_MAX_MEMBERS",
            "capLastCap": "CAP_LAST_CAP", "configMultiuser": "IS_ENABLED(CONFIG_MULTIUSER)"}
for prefix, (structure, members) in SU_LAYOUT_TYPES.items():
    c_type = "struct " + structure
    SU_FACTS[prefix + "Bytes"] = f"sizeof({c_type})"
    SU_FACTS[prefix + "Align"] = f"__alignof__({c_type})"
    for member in members:
        key = prefix + "".join(word.capitalize() for word in member.split("_"))
        SU_FACTS[key + "Offset"] = f"offsetof({c_type}, {member})"
        SU_FACTS[key + "Bytes"] = f"sizeof((({c_type} *)0)->{member})"
SU_FUNCTIONS = ("msu_su_registry_create", "msu_su_registry_refresh", "msu_su_registry_rows",
                "msu_su_registry_update_begin", "msu_su_registry_update_finish", "msu_su_registry_revoke",
                "msu_su_registry_revoke_package", "msu_su_registry_shutdown", "msu_su_registry_destroy_quiesced",
                "msu_su_read_current", "msu_su_lease_begin", "msu_su_lease_validate",
                "msu_su_lease_profile", "msu_su_lease_end", "msu_su_apply_current")
SU_CALLBACKS = {"su.reader": "msu_su_read_current", "su.apply": "msu_su_apply_current",
                "su.credential.validate": "msu_su_lease_validate"}
SU_CALLBACK_TYPE_SOURCE = '''#include <linux/compiler.h>
#include "su_install_registry.h"
__ADDRESSABLE(msu_su_read_current);
__ADDRESSABLE(msu_su_apply_current);
__ADDRESSABLE(msu_su_lease_validate);
'''


def select_su_sources(path, expected_sha256):
    """Accept a separate exact snapshot without altering the historical freeze chain."""
    global SU_SELECTION
    path = path.resolve()
    if not path.is_relative_to(ROOT.resolve()) or sha(path) != expected_sha256:
        raise ValueError("SU source selection path/identity mismatch")
    selected = json.loads(path.read_text(encoding="utf-8"))
    if selected["schema"] != "maplesu-p2-su-candidate-source-selection/1" or \
            selected.get("sourceOwnerFrozen") is not False or selected.get("adoptedToProduction") is not False:
        raise ValueError("SU candidate snapshot must not claim owner freeze or production adoption")
    expected_paths = {SU_NATIVE_LOGICAL_ROOT + ("" if name == "su_native_reader_abi.h" else "kernel/") + name
                      for name in SU_SOURCE_NAMES}
    rows = selected["files"]
    if len(rows) != 3 or {row["logicalPath"] for row in rows} != expected_paths:
        raise ValueError("SU source selection requires the exact three native inputs")
    provenance = selected["ownerProvenance"]
    for key in ("deliveryIdentity", "sourceSelectionIdentity"):
        item = provenance[key]
        actual = identity(ROOT / item["path"])
        if any(actual[field] != item[field] for field in ("bytes", "sha256")):
            raise ValueError("SU owner receipt changed: " + key)
    delivery = json.loads((ROOT / provenance["deliveryIdentity"]["path"]).read_text(encoding="utf-8"))
    owner = json.loads((ROOT / provenance["sourceSelectionIdentity"]["path"]).read_text(encoding="utf-8"))
    if delivery["changedFiles"]["sha256"] != provenance["sourceSelectionIdentity"]["sha256"]:
        raise ValueError("SU delivery does not bind its actual source selection")
    for row in rows:
        item = row["snapshotIdentity"]
        source = (ROOT / item["path"]).resolve()
        if not source.is_relative_to(ROOT.resolve()):
            raise ValueError("SU selected source escaped the workspace")
        actual = identity(source)
        if any(actual[field] != item[field] for field in ("bytes", "sha256")):
            raise ValueError("SU selected snapshot changed: " + row["logicalPath"])
        matching = [entry for entry in owner["files"] if entry["path"] == row["logicalPath"]]
        if len(matching) != 1 or any(matching[0]["after"][field] != item[field] for field in ("bytes", "sha256")):
            raise ValueError("SU snapshot does not match its owner-selected after image")
    if len({Path(row["snapshotIdentity"]["path"]).name for row in rows}) != len(rows):
        raise ValueError("SU inputs alias one staged filename")
    SU_SELECTION = {"identity": identity(path), "ownerProvenance": provenance, "files": rows,
                    "sourceOwnerFrozen": False, "adoptedToProduction": False}
    return SU_SELECTION


def su_source_paths():
    return [] if SU_SELECTION is None else [effective_source(row) for row in SU_SELECTION["files"]]


def su_pending_abi():
    binding = next(row for row in SU_SELECTION["files"] if row["logicalPath"].endswith("/su_native_reader_abi.h"))
    return {"schema": "maplesu-su-provider-abi/2", "schemaVersion": 2,
            "sourceSelection": SU_SELECTION, "metadataComplete": False, "kernelMetadataComplete": False,
            "providerComplete": False, "factoryBound": False, "lifecycleQualified": False,
            "targetQualified": False, "suReady": False, "entrypointsComplete": False,
            "sourceIdentities": [], "objectIdentities": [], "commandIdentities": [],
            "dependencyReceiptIdentities": [], "inlineDependencies": [], "compiledFacts": {},
            "compiledLayouts": {}, "localFunctionAbi": {}, "callbackOrigins": {},
            "sourceCallbackAbi": {}, "callbackCfi": {}, "entrypoints": {}, "config": {},
            "readerBinding": {"available": False, "metadataComplete": False,
                "sourceIdentity": binding["snapshotIdentity"],
                "reason": "Owner header is SDK-only stdint.h; no actual same-target C++ facts or owner dual-use ABI supplied"},
            "missing": ["Reader binding needs actual dual-use C or same-target SDK C++ facts"]}


def su_callback_relocations(path):
    references = {"msu_credential_replace_current": set(), "msu_su_lease_validate": set()}
    with path.open("rb") as stream:
        elf = ELFFile(stream)
        table = elf.get_section_by_name(".symtab")
        functions = [symbol for symbol in table.iter_symbols()
                     if symbol["st_info"]["type"] == "STT_FUNC" and symbol["st_size"]]
        for section in elf.iter_sections():
            if not isinstance(section, RelocationSection):
                continue
            symbols = elf.get_section(section["sh_link"])
            for relocation in section.iter_relocations():
                name = symbols.get_symbol(relocation["r_info_sym"]).name
                if name not in references:
                    continue
                for function in functions:
                    if function["st_shndx"] == section["sh_info"] and function["st_value"] <= relocation["r_offset"] < function["st_value"] + function["st_size"]:
                        references[name].add(function.name)
    if any("msu_su_apply_current" not in callers for callers in references.values()):
        raise ValueError("SU actual apply/core/validator address relocations missing")
    return {name: sorted(callers) for name, callers in references.items()}


def collect_su_provider_abi(target, payload, reference):
    """Read exact unlinked C facts and actual objects; do not infer provider readiness."""
    record = su_pending_abi()
    try:
        build_inputs = json.loads((target / "build-inputs.json").read_text(encoding="utf-8"))
        if build_inputs.get("suSourceSelection") != SU_SELECTION:
            raise ValueError("SU target build does not bind the current explicit source selection")
        sources = {path.name: path for path in su_source_paths()}
        for path in sources.values():
            if sha(target / path.name) != sha(path):
                raise ValueError("SU staged source differs from selected snapshot: " + path.name)
            record["sourceIdentities"].append(identity(path))
        for name in ("su_install_registry", "manager_su_facts", "manager_su_callback_types"):
            proof = object_provenance(target, name)
            record["objectIdentities"].append(identity(target / (name + ".o")))
            record["commandIdentities"].append(identity(target / ("." + name + ".o.cmd")))
            record["dependencyReceiptIdentities"].append(identity(target / "build-dependencies" / (name + ".json")))
            record["inlineDependencies"].extend(proof["dependencies"])
            if proof["sourceIdentity"]["sha256"] != sha(target / (name + ".c")):
                raise ValueError("SU object source/dependency generation mismatch: " + name)
        definitions = object_btf(target / "su_install_registry.o")
        facts_btf = object_btf(target / "manager_su_facts.o")
        values = object_words(target / "manager_su_facts.o", "msu_su_build_facts")
        if len(values) != len(SU_FACTS) or values[0] != 1:
            raise ValueError("SU actual compiled facts schema/count mismatch")
        facts = record["compiledFacts"] = dict(zip(SU_FACTS, values))
        for prefix, (structure, members) in SU_LAYOUT_TYPES.items():
            layout = facts_btf.named_structure_selected(structure, members)
            if layout["bytes"] != facts[prefix + "Bytes"] or facts[prefix + "Align"] <= 0:
                raise ValueError("SU actual BTF/C structure mismatch: " + prefix)
            for member in members:
                key = prefix + "".join(word.capitalize() for word in member.split("_"))
                field = layout["members"][member]
                if field["offset"] != facts[key + "Offset"] or field["bytes"] != facts[key + "Bytes"]:
                    raise ValueError("SU actual BTF/C member mismatch: " + prefix + "." + member)
            record["compiledLayouts"][prefix] = layout
        record["localFunctionAbi"] = {name: definitions.function(name) for name in SU_FUNCTIONS}
        core = object_btf(target / "manager_management.o")
        validator = core.function_parameter("msu_credential_replace_current", 1)
        if validator != "*" + definitions.function("msu_su_lease_validate") or \
                validator != facts_btf.function_parameter("msu_credential_replace_current", 1):
            raise ValueError("SU lease validator differs from actual single credential-core parameter")
        record["callbackOrigins"] = {"su.credential.validate": {"kind": "internal",
            "source": "msu_credential_replace_current", "parameterIndex": 1,
            "definition": "msu_su_lease_validate", "signature": validator}}
        record["sourceCallbackAbi"] = {name: facts_btf.member_function("msu_su_registry_source", name)
            for name in ("pin_current", "put_install", "read_grant", "validate_profile")}
        record["callbackRelocations"] = su_callback_relocations(target / "su_install_registry.o")
        policies = {}
        for name in ("su_install_registry", "manager_su_facts", "manager_su_callback_types", "manager_management"):
            tokens = (target / ("." + name + ".o.cmd")).read_text(encoding="utf-8").splitlines()[0].split()
            enabled, present = False, False
            for token in tokens:
                if token.startswith(("-fsanitize=", "-fno-sanitize=")) and "kcfi" in token.split("=", 1)[1].split(","):
                    present = True
                    enabled = token.startswith("-fsanitize=")
            normalized = any(token in ("-fsanitize-cfi-icall-normalize-integers", "-fsanitize-cfi-icall-experimental-normalize-integers") for token in tokens)
            if present and not enabled:
                raise ValueError("SU actual object disabled kernel KCFI: " + name)
            policies[name] = {"kcfiOptionPresent": present, "kcfiEnabled": enabled, "normalizeIntegers": normalized}
        if len({tuple(row.values()) for row in policies.values()}) != 1:
            raise ValueError("SU/core/declaration objects use inconsistent CFI policies")
        cfi = record["callbackCfi"] = {"mode": "kcfi" if enabled else "none", "supported": True,
            "normalizeIntegers": normalized, "objectPolicies": policies, "callbacks": {}}
        with (target / "manager_su_callback_types.o").open("rb") as probe, (target / "su_install_registry.o").open("rb") as implementation:
            declarations = ELFFile(probe).get_section_by_name(".symtab")
            elf = ELFFile(implementation)
            table = elf.get_section_by_name(".symtab")
            for key, name in SU_CALLBACKS.items():
                functions = table.get_symbol_by_name(name) or []
                if len(functions) != 1 or functions[0]["st_info"]["type"] != "STT_FUNC":
                    raise ValueError("SU actual callback function missing/ambiguous: " + name)
                function = functions[0]
                entry = {"function": name, "signature": definitions.function(name), "verified": not enabled,
                    "objectIdentity": identity(target / "su_install_registry.o"),
                    "typeProbeObjectIdentity": identity(target / "manager_su_callback_types.o")}
                if enabled:
                    types = declarations.get_symbol_by_name("__kcfi_typeid_" + name) or []
                    if len(types) != 1 or types[0]["st_shndx"] != "SHN_ABS":
                        raise ValueError("SU actual declaration KCFI typeid missing: " + name)
                    section = elf.get_section(function["st_shndx"])
                    offset = function["st_value"] - section["sh_addr"]
                    if offset < 4 or offset > section["sh_size"]:
                        raise ValueError("SU actual callback prefix outside executable section: " + name)
                    prefix = int.from_bytes(section.data()[offset - 4:offset], "little")
                    typeid = types[0]["st_value"] & 0xffffffff
                    entry.update(typeidSymbol="__kcfi_typeid_" + name, typeid=typeid,
                        functionOffset=function["st_value"], prefixOffset=function["st_value"] - 4,
                        prefixWord=prefix, verified=prefix == typeid)
                    if prefix != typeid:
                        raise ValueError("SU actual callback prefix/declaration typeid mismatch: " + name)
                cfi["callbacks"][key] = entry
        with (target / "su_install_registry.o").open("rb") as stream:
            table = ELFFile(stream).get_section_by_name(".symtab")
            record["objectUndefinedSymbols"] = sorted({symbol.name for symbol in table.iter_symbols()
                if symbol.name and symbol["st_shndx"] == "SHN_UNDEF"})
        internal = {"msu_credential_replace_current", "msu_uid_gateway_acquire", "msu_uid_snapshot_lookup", "msu_uid_snapshot_put"}
        if internal & set(payload["imports"]):
            raise ValueError("SU claim-internal definitions were exposed as reference imports")
        record["finalImports"] = {name: reference.variable(name) if name in reference.variables else reference.function(name)
            for name in record["objectUndefinedSymbols"] if name in payload["imports"]}
        config = json.loads((target / "msu_resolver_build_config.json").read_text(encoding="utf-8"))
        record["config"] = {name: config["managementConfig"].get(name, "n") for name in
            ("CONFIG_MULTIUSER", "CONFIG_PREEMPT_RCU", "CONFIG_TREE_RCU", "CONFIG_REFCOUNT_FULL", "CONFIG_DEBUG_SPINLOCK")}
        with (target / "claim.so").open("rb") as stream:
            elf = ELFFile(stream)
            table = elf.get_section_by_name(".symtab")
            for name in SU_FUNCTIONS:
                symbols = table.get_symbol_by_name(name) or []
                if not symbols:
                    record["entrypoints"][name] = {"available": False, "reason": "No retained final function; no actual factory/consumer root supplied"}
                    continue
                if len(symbols) != 1 or symbols[0]["st_info"]["type"] != "STT_FUNC":
                    raise ValueError("SU final entry is not a unique function: " + name)
                function = symbols[0]
                section = elf.get_section(function["st_shndx"])
                offset, size = function["st_value"] - section["sh_addr"], function["st_size"]
                if not section["sh_flags"] & 4 or not size or function["st_value"] % 4 or offset < 0 or offset + size > section["sh_size"]:
                    raise ValueError("SU final entry bounds/alignment invalid: " + name)
                entry = record["entrypoints"][name] = {"available": True,
                    "entryOffset": function["st_value"], "fileOffset": section["sh_offset"] + offset, "bytes": size,
                    "sectionName": section.name, "sectionOffset": section["sh_addr"],
                    "sectionBytes": section["sh_size"], "binding": function["st_info"]["bind"]}
                callback = next((key for key, function_name in SU_CALLBACKS.items() if function_name == name), None)
                if enabled and callback is not None:
                    if offset < 4:
                        raise ValueError("SU retained final callback has no KCFI prefix: " + name)
                    prefix = int.from_bytes(section.data()[offset - 4:offset], "little")
                    typeid = cfi["callbacks"][callback]["typeid"]
                    if prefix != typeid:
                        raise ValueError("SU retained final callback prefix/typeid mismatch: " + name)
                    entry.update(prefixOffset=function["st_value"] - 4,
                        prefixFileOffset=section["sh_offset"] + offset - 4,
                        prefixWord=prefix, typeid=typeid, prefixVerified=True)
        record["entrypointsComplete"] = all(row["available"] for row in record["entrypoints"].values())
        if not record["entrypointsComplete"]:
            record["missing"].append("SU functions have no complete retained factory/consumer roots in final claim")
        record["kernelMetadataComplete"] = True
    except (OSError, ValueError, KeyError, IndexError) as error:
        record["missing"].append(type(error).__name__ + ": " + str(error))
    return record


# Exact five-TU component is separate from historical three-input SU1/2.
SU_COMPONENT_SELECTION = None
SU_COMPONENT_METADATA_SELECTION = None
SU_COMPONENT_ROOT = SU_NATIVE_LOGICAL_ROOT + "kernel/"
SU_COMPONENT_OBJECTS = ("su_install_registry", "su_profile_apply", "su_profile_namespace", "su_profile_execution", "su_profile_resolve")
SU_COMPONENT_PROBES = ("manager_su_component_facts", "manager_su_profile_facts", "manager_su_namespace_facts", "manager_su_component_callback_types")
SU_COMPONENT_LAYOUTS = {
    **{key: (kind, "manager_su_component_facts", fields) for key, (kind, fields) in SU_LAYOUT_TYPES.items()},
    "namespaceReceipt": ("msu_su_namespace_receipt", "manager_su_component_facts", ("mode", "effects", "error", "operation_error", "close_error", "cwd_error", "validation_error")),
    "executionReceipt": ("msu_su_execution_receipt", "manager_su_component_facts", ("phase", "credential_effects", "task_effects", "previous_seccomp_mode", "mount_namespace", "error", "completed")),
    "profileExecution": ("msu_su_profile_execution", "manager_su_profile_facts", ("lease", "before", "effects", "adopted")),
}
SU_COMPONENT_LAYOUTS.update({
    "registrySource": ("msu_su_registry_source", "manager_su_component_facts", ("owner", "pin_current", "put_install", "read_grant", "validate_profile", "check_closed", "quiesce", "put_owner", "validate_producer")),
    "lease": ("msu_su_lease", "manager_su_component_facts", ("registry", "entry", "snapshot", "task", "mm", "cred", "generation", "exec_id", "invalidated")),
    "registry": ("msu_su_registry", "manager_su_component_facts", ("lock", "host", "controller", "next_generation", "source", "count", "closed", "producer", "gateway_binding", "gateway_bound", "gateway_retired", "entries")),
    "update": ("msu_su_update", "manager_su_component_facts", ("registry", "records", "expected_revision", "count", "shutdown")),
})
SU_COMPONENT_FACTS = {"schema": "1", **dict(list(SU_FACTS.items())[1:8]),
    "namespaceApplied": "MSU_SU_NS_APPLIED", "namespaceChanged": "MSU_SU_NS_CHANGED",
    "namespaceCwdRestored": "MSU_SU_NS_CWD_RESTORED", "namespaceRootPrivate": "MSU_SU_NS_ROOT_PRIVATE",
    "namespaceCompleted": "MSU_SU_NS_COMPLETED", "phaseAdmission": "MSU_SU_EXECUTION_ADMISSION",
    "phaseSupport": "MSU_SU_EXECUTION_SUPPORT", "phaseCredentials": "MSU_SU_EXECUTION_CREDENTIALS",
    "phaseTask": "MSU_SU_EXECUTION_TASK", "phaseNamespace": "MSU_SU_EXECUTION_NAMESPACE",
    "phaseFinalValidate": "MSU_SU_EXECUTION_FINAL_VALIDATE"}
SU_COMPONENT_PROFILE_FACTS = {"schema": "1"}
for prefix, (kind, probe, members) in SU_COMPONENT_LAYOUTS.items():
    values = SU_COMPONENT_PROFILE_FACTS if probe == "manager_su_profile_facts" else SU_COMPONENT_FACTS
    kind = "struct " + kind
    values[prefix + "Bytes"] = "sizeof(" + kind + ")"
    values[prefix + "Align"] = "__alignof__(" + kind + ")"
    for member in members:
        key = prefix + "".join(word.capitalize() for word in member.split("_"))
        values[key + "Offset"] = "offsetof(" + kind + ", " + member + ")"
        values[key + "Bytes"] = "sizeof(((" + kind + " *)0)->" + member + ")"
SU_COMPONENT_FUNCTIONS = {
    "su_install_registry": (*SU_FUNCTIONS, "msu_su_registry_bind_gateway", "msu_su_registry_prepare_kernel", "msu_su_registry_commit_kernel", "msu_su_registry_cancel_kernel"),
    "su_profile_apply": ("msu_su_profile_check", "msu_su_profile_apply_held"),
    "su_profile_namespace": ("msu_su_profile_namespace_supported", "msu_su_profile_namespace_apply_held"),
    "su_profile_execution": ("msu_su_profile_check_execution", "msu_su_apply_current_detailed"),
    "su_profile_resolve": ("msu_su_profile_resolve_root",),
}
SU_COMPONENT_CALLBACKS = {
    "su.reader": ("su_install_registry", "msu_su_read_current"),
    "su.apply": ("su_install_registry", "msu_su_apply_current"),
    "su.lease.validate": ("su_install_registry", "msu_su_lease_validate"),
    "su.uid.prepare.validate": ("su_install_registry", "msu_su_kernel_producer_validate"),
    **{"su.fence." + name: ("su_install_registry", "msu_su_fence_" + name)
       for name in ("check_closed", "begin", "revoke", "finish", "retire")},
    "su.profile.validate": ("su_profile_apply", "msu_su_profile_validate_held"),
    "su.profile.adopt": ("su_profile_apply", "msu_su_profile_adopt"),
}
SU_COMPONENT_PARAMETERS = {
    "su.uid.prepare.validate": ("manager_uid_gateway", "msu_uid_gateway_prepare_kernel", 7, "su_install_registry", "msu_su_kernel_producer_validate"),
    "su.uid.commit.validate": ("manager_uid_gateway", "msu_uid_gateway_commit_kernel", 5, "su_install_registry", "msu_su_kernel_producer_validate"),
    "su.profile.validate": ("manager_management", "msu_credential_replace_profile_current", 1, "su_profile_apply", "msu_su_profile_validate_held"),
    "su.profile.adopt": ("manager_management", "msu_credential_replace_profile_current", 2, "su_profile_apply", "msu_su_profile_adopt"),
    "su.task.validate": ("manager_task_state", "msu_task_state_release_current", 0, "su_install_registry", "msu_su_lease_validate"),
}
SU_COMPONENT_RELOCATIONS = {
    "su_install_registry": {
        "msu_su_registry_bind_gateway": ("msu_uid_gateway_bind_reader", "msu_su_fence_check_closed", "msu_su_fence_begin", "msu_su_fence_revoke", "msu_su_fence_finish", "msu_su_fence_retire"),
        "msu_su_registry_prepare_kernel": ("msu_uid_gateway_prepare_kernel", "msu_su_kernel_producer_validate"),
        "msu_su_registry_commit_kernel": ("msu_uid_gateway_commit_kernel", "msu_su_kernel_producer_validate"),
        "msu_su_registry_cancel_kernel": ("msu_uid_gateway_cancel_kernel",),
        "msu_su_registry_refresh": ("msu_su_profile_resolve_root",),
        "msu_su_apply_current": ("msu_su_apply_current_detailed",),
    },
    "su_profile_apply": {"msu_su_profile_apply_held": ("msu_credential_replace_profile_current", "msu_su_profile_validate_held", "msu_su_profile_adopt")},
    "su_profile_execution": {"msu_su_apply_current_detailed": ("msu_su_profile_apply_held", "msu_task_state_release_current", "msu_su_lease_validate", "msu_su_profile_namespace_apply_held")},
}
SU_NAMESPACE_FUNCTIONS = ("__arm64_sys_setns", "__arm64_sys_unshare", "__arm64_sys_close", "path_mount")
SU_NAMESPACE_DATA = ("init_cred", "init_pid_ns", "mntns_operations")
SU_NAMESPACE_TYPES = {
    "ptRegs": ("pt_regs", {"regs": 10, "sp": 12, "pstate": 13}, 9),
    "task": ("task_struct", {"fs": 14, "files": 15, "nsproxy": 16}, None),
    "fs": ("fs_struct", {"root": 17, "pwd": 18}, 28),
    "path": ("path", {"mnt": 20, "dentry": 21}, 19),
    "nsproxy": ("nsproxy", {"mnt_ns": 22}, 27),
    "files": ("files_struct", {"count": 23}, 29),
    "procNsOperations": ("proc_ns_operations", {}, 26),
}


def su_component_receipt(item):
    path = (ROOT / item["path"]).resolve()
    if not path.is_relative_to(ROOT.resolve()) or any(identity(path)[key] != item[key] for key in ("bytes", "sha256")):
        raise ValueError("SU component archived input identity mismatch: " + str(path))
    return path


def select_su_component_sources(path, expected_sha256):
    global SU_COMPONENT_SELECTION, SU_SELECTION
    if SU_SELECTION is not None:
        raise ValueError("Select one historical SU or full native component source generation")
    if CORE_SELECTION is None or CORE_SELECTION.get("lifecycleRevision") != 2:
        raise ValueError("SU component needs the complete lifecycle ABI revision 2 CORE selection")
    path = path.resolve()
    if not path.is_relative_to(ROOT.resolve()) or sha(path) != expected_sha256:
        raise ValueError("SU component selection path/identity mismatch")
    selected = json.loads(path.read_text(encoding="utf-8"))
    if selected["schema"] != "maplesu-p2-su-component-source-selection/1" or selected["sourceOwnerFrozen"] is not False or selected["adoptedToProduction"] is not False:
        raise ValueError("SU component must preserve its independent nonproduction status")
    provenance = selected["ownerProvenance"]
    original = json.loads(su_component_receipt(provenance["sourceSelectionIdentity"]).read_text(encoding="utf-8"))
    if original["schema"] != "maplesu-su-native-component-selection/1" or original["productionWritten"] or not original["nativeComponentSourcesStable"] or original["wholeSuComplete"] or original["targetQualified"] or original["officialFactoryPublished"]:
        raise ValueError("SU owner component source/status contract mismatch")
    expected = {SU_COMPONENT_ROOT + name + extension for name in SU_COMPONENT_OBJECTS for extension in (".c", ".h")}
    native = [SU_COMPONENT_ROOT + name + ".c" for name in SU_COMPONENT_OBJECTS]
    rows = {row["logicalPath"]: row for row in selected["files"]}
    owners = {row["logicalPath"]: row for row in original["files"]}
    if len(rows) != 10 or len(rows) != len(selected["files"]) or len(owners) != len(original["files"]) or rows.keys() != expected or owners.keys() != expected or original["nativeTUs"] != native or selected["nativeTUs"] != native:
        raise ValueError("SU component requires the exact five TUs and ten inputs")
    for relative, row in rows.items():
        su_component_receipt(row["snapshotIdentity"])
        if any(row["snapshotIdentity"][key] != owners[relative]["source"][key] for key in ("bytes", "sha256")) or row["ownerSourceIdentity"] != owners[relative]["source"]:
            raise ValueError("SU snapshot differs from owner's selected after image: " + relative)
    if len({Path(row["snapshotIdentity"]["path"]).name for row in rows.values()}) != 10:
        raise ValueError("SU component staged source filename aliases")
    evidence = provenance["evidence"]
    if len(evidence) != len(original["historicalAndCurrentEvidence"]) or any(row["ownerIdentity"] != owner for row, owner in zip(evidence, original["historicalAndCurrentEvidence"])):
        raise ValueError("SU component historical evidence source list mismatch")
    for row in evidence:
        su_component_receipt(row["snapshotIdentity"])
        if any(row["ownerIdentity"][key] != row["snapshotIdentity"][key] for key in ("bytes", "sha256")):
            raise ValueError("SU component evidence snapshot mismatch")
    frozen = json.loads(su_component_receipt(provenance["componentCoreFreezeIdentity"]).read_text(encoding="utf-8"))
    if any(provenance["componentCoreFreezeIdentity"][key] != original["coreSourceFreeze"][key] for key in ("bytes", "sha256")):
        raise ValueError("SU component historical CORE freeze mismatch")
    old = {row["path"]: row for row in frozen["files"]}
    compatibility = CORE_SELECTION.get("componentCompatibilityCore", CORE_SELECTION)
    current = {row["logicalPath"]: row["snapshotIdentity"] for row in compatibility["files"]}
    changed = {name for name in old if name not in current or any(old[name][key] != current[name][key] for key in ("bytes", "sha256"))}
    if old.keys() != current.keys():
        raise ValueError("SU component/current CORE path set differs")
    if changed:
        issued = json.loads(core_receipt_path(compatibility["receipts"]["issuedDelta"]).read_text(encoding="utf-8"))
        correction = "runtime/ultra/identity/kernel/manager_management.c"
        if changed != {correction} or issued["apiAbiWireChanged"] is not False or issued["runtimeBranchesChanged"] is not False or len(issued["delta"]) != 1 or any(issued["delta"][0]["before"][key] != old[correction][key] or issued["delta"][0]["after"][key] != current[correction][key] for key in ("bytes", "sha256")):
            raise ValueError("SU component/current CORE differs beyond verified source-only correction")
    reader = selected["readerBindingHistory"]
    su_component_receipt(reader["sourceIdentity"])
    history = json.loads(su_component_receipt(reader["selectionIdentity"]).read_text(encoding="utf-8"))
    old_reader = [row for row in history["files"] if row["logicalPath"] == SU_NATIVE_LOGICAL_ROOT + "su_native_reader_abi.h"]
    if len(old_reader) != 1 or any(old_reader[0]["snapshotIdentity"][key] != reader["sourceIdentity"][key] for key in ("bytes", "sha256")) or reader["qualified"] is not False:
        raise ValueError("SU reader header historical identity/qualification mismatch")
    SU_COMPONENT_SELECTION = dict(selected, identity=identity(path), currentCoreSourceSelection=core_public_selection())
    SU_SELECTION = SU_COMPONENT_SELECTION
    return SU_COMPONENT_SELECTION


def su_selected_receipts():
    if SU_COMPONENT_SELECTION is not None:
        item = SU_COMPONENT_SELECTION
        provenance = item["ownerProvenance"]
        return [Path(item["identity"]["path"]), su_component_receipt(provenance["sourceSelectionIdentity"]), su_component_receipt(provenance["componentCoreFreezeIdentity"]),
            *(su_component_receipt(row["snapshotIdentity"]) for row in provenance["evidence"]),
            su_component_receipt(item["readerBindingHistory"]["sourceIdentity"]), su_component_receipt(item["readerBindingHistory"]["selectionIdentity"])]
    return [] if SU_SELECTION is None else [Path(SU_SELECTION["identity"]["path"]),
        *(ROOT / SU_SELECTION["ownerProvenance"][key]["path"] for key in ("deliveryIdentity", "sourceSelectionIdentity"))]


def su_component_probe(name):
    includes = {"manager_su_component_facts": "su_install_registry.c", "manager_su_profile_facts": "su_profile_apply.c", "manager_su_namespace_facts": "su_profile_namespace.c"}
    if name == "manager_su_component_callback_types":
        result = '#include <linux/compiler.h>\n#include "su_install_registry.h"\n#include "su_profile_apply.h"\n#include "su_profile_execution.h"\n#include "su_profile_resolve.h"\n'
        result += "struct cred;\n"
        paths = {Path(row["logicalPath"]).stem: effective_source(row)
            for row in SU_COMPONENT_SELECTION["files"] if row["logicalPath"].endswith(".c")}
        public = {name for names in SU_COMPONENT_FUNCTIONS.values() for name in names}
        for obj, function in SU_COMPONENT_CALLBACKS.values():
            if function in public:
                continue
            text = re.sub(r"/\*.*?\*/|//[^\n]*", "", paths[obj].read_text(encoding="utf-8"), flags=re.S)
            matches = re.findall(r"\bstatic\s+([^;{}]+?\b" + re.escape(function) + r"\s*\([^;{}]*\))\s*\{", text, re.S)
            if len(matches) != 1:
                raise ValueError("SU actual private callback source declaration missing/ambiguous: " + function)
            result += "extern " + " ".join(matches[0].split()) + ";\n"
        names = [name for values in SU_COMPONENT_FUNCTIONS.values() for name in values] + [name for _, name in SU_COMPONENT_CALLBACKS.values()]
        return result + "".join("typeof(&" + name + ") msu_su_component_decl_" + name +
            " __used = " + name + ";\n__ADDRESSABLE(" + name + ");\n" for name in dict.fromkeys(names))
    result = '#include <linux/compiler.h>\n#include <linux/stddef.h>\n#include "' + includes[name] + '"\n'
    if name == "manager_su_namespace_facts":
        result += "const unsigned int msu_su_namespace_build_facts[] = {1, MSU_SU_NAMESPACE_NATIVE_SOURCE, MSU_SU_NAMESPACE_NATIVE_VERIFIED};\n"
        result += "#if MSU_SU_NAMESPACE_NATIVE_SOURCE\n"
        for index, function in enumerate(SU_NAMESPACE_FUNCTIONS):
            result += "typeof(&" + function + ") msu_su_namespace_decl_" + str(index) + " __used;\n__ADDRESSABLE(" + function + ");\n"
        for index, variable in enumerate(SU_NAMESPACE_DATA):
            result += "typeof(" + variable + ") *msu_su_namespace_data_" + str(index) + " __used = &" + variable + ";\n"
        for prefix, (kind, _, _) in SU_NAMESPACE_TYPES.items():
            result += "static struct " + kind + " *msu_su_namespace_type_" + prefix + " __used;\n"
        return result + "#endif\n"
    values = SU_COMPONENT_PROFILE_FACTS if name == "manager_su_profile_facts" else SU_COMPONENT_FACTS
    for prefix, (kind, probe, _) in SU_COMPONENT_LAYOUTS.items():
        if probe == name:
            result += "static struct " + kind + " *msu_component_type_" + prefix + " __used;\n"
    return result + "const unsigned int msu_su_component_build_facts[] = {\n" + "".join("    " + value + ",\n" for value in values.values()) + "};\n"


def su_component_pending_abi():
    return {"schema": "maplesu-su-provider-abi/3", "schemaVersion": 3, "sourceSelection": SU_COMPONENT_SELECTION,
        **({"sourceCorrections": SOURCE_CORRECTIONS} if SOURCE_CORRECTIONS is not None else {}),
        "metadataComplete": False, "kernelMetadataComplete": False, "providerComplete": False, "factoryBound": False,
        "lifecycleQualified": False, "targetQualified": False, "suReady": False, "entrypointsComplete": False,
        "sourceIdentities": [], "objectIdentities": [], "commandIdentities": [], "dependencyReceiptIdentities": [], "inlineDependencies": [],
        "compiledFacts": {}, "profileCompiledFacts": {}, "compiledLayouts": {}, "localFunctionAbi": {}, "callbackOrigins": {},
        "sourceCallbackAbi": {}, "readerFenceAbi": {}, "callbackRelocations": {}, "callbackCfi": {}, "entrypoints": {},
        "nativeNamespace": {"metadataComplete": False, "targetQualified": False, "functionAbi": {}, "dataAbi": {}, "compiledLayouts": {}, "targetLayouts": {}, "missing": []},
        "readerBinding": {"available": False, "metadataComplete": False, "sourceIdentity": SU_COMPONENT_SELECTION["readerBindingHistory"]["sourceIdentity"],
            "reason": "Inherited SDK-only header; component supplies no kernel dual-use or same-target C++ binding facts"},
        "config": {}, "missing": ["No trusted PMS/durable grant source or actual retained native factory/binding is selected", "Reader binding needs real dual-use or same-target C++ facts"]}


def su_component_relocations(path, callers):
    result = {caller: {name: [] for name in names} for caller, names in callers.items()}
    with path.open("rb") as stream:
        elf = ELFFile(stream)
        table = elf.get_section_by_name(".symtab")
        functions = [item for item in table.iter_symbols() if item["st_info"]["type"] == "STT_FUNC" and item["st_size"]]
        for section in elf.iter_sections():
            if not isinstance(section, RelocationSection):
                continue
            symbols = elf.get_section(section["sh_link"])
            for relocation in section.iter_relocations():
                symbol = symbols.get_symbol(relocation["r_info_sym"])
                destinations = {symbol.name} if symbol.name else set()
                # Local callback addresses use section+addend, not a named relocation.
                if symbol["st_info"]["type"] == "STT_SECTION":
                    destination = symbol["st_value"] + relocation.entry.get("r_addend", 0)
                    destinations.update(item.name for item in functions
                        if item["st_shndx"] == symbol["st_shndx"] and item["st_value"] == destination)
                for function in functions:
                    if function.name in result and function["st_shndx"] == section["sh_info"] and function["st_value"] <= relocation["r_offset"] < function["st_value"] + function["st_size"]:
                        for name in destinations & result[function.name].keys():
                            result[function.name][name].append({"offset": relocation["r_offset"], "type": relocation["r_info_type"],
                                "section": section.name, "symbol": symbol.name, "addend": relocation.entry.get("r_addend", 0),
                                "resolvedTarget": name})
    if any(not values for names in result.values() for values in names.values()):
        raise ValueError("SU component actual caller/callee or callback relocation missing: " + str(path))
    return result


def su_component_entry(target, object_name, function_name, btf, declarations, policy):
    with (target / (object_name + ".o")).open("rb") as stream:
        elf = ELFFile(stream)
        matches = elf.get_section_by_name(".symtab").get_symbol_by_name(function_name) or []
        if len(matches) != 1 or matches[0]["st_info"]["type"] != "STT_FUNC":
            raise ValueError("SU component actual definition missing/ambiguous: " + function_name)
        symbol = matches[0]
        section = elf.get_section(symbol["st_shndx"])
        offset, size = symbol["st_value"] - section["sh_addr"], symbol["st_size"]
        if not section["sh_flags"] & 4 or offset < 0 or size <= 0 or offset + size > section["sh_size"]:
            raise ValueError("SU component actual function bounds: " + function_name)
        signature = btf.function(function_name)
        declared = declarations.variable("msu_su_component_decl_" + function_name)
        if "*" + signature != declared:
            raise ValueError("SU component declaration/definition BTF signature mismatch: " + function_name)
        result = {"function": function_name, "signature": signature, "objectIdentity": identity(target / (object_name + ".o")),
            "typeProbeObjectIdentity": identity(target / "manager_su_component_callback_types.o"), "verified": not policy["kcfiEnabled"]}
        if policy["kcfiEnabled"]:
            with (target / "manager_su_component_callback_types.o").open("rb") as probe:
                matches = ELFFile(probe).get_section_by_name(".symtab").get_symbol_by_name("__kcfi_typeid_" + function_name) or []
                if len(matches) != 1 or matches[0]["st_shndx"] != "SHN_ABS" or offset < 4:
                    raise ValueError("SU component actual KCFI declaration missing: " + function_name)
                typeid = matches[0]["st_value"] & 0xffffffff
            prefix = int.from_bytes(section.data()[offset - 4:offset], "little")
            if prefix != typeid:
                raise ValueError("SU component actual declaration/definition KCFI mismatch: " + function_name)
            result.update(typeidSymbol="__kcfi_typeid_" + function_name, typeid=typeid, prefixOffset=symbol["st_value"] - 4,
                functionOffset=symbol["st_value"], prefixWord=prefix, verified=True)
        return result


def su_namespace_words(path):
    with path.open("rb") as stream:
        elf = ELFFile(stream)
        matches = elf.get_section_by_name(".symtab").get_symbol_by_name("msu_su_namespace_native_layout") or []
        if len(matches) != 1 or matches[0]["st_info"]["type"] != "STT_OBJECT":
            raise ValueError("SU namespace layout is not one actual object")
        symbol = matches[0]
        section = elf.get_section(symbol["st_shndx"])
        offset = symbol["st_value"] - section["sh_addr"]
        if section.name != ".msu.su_namespace.layout" or section["sh_flags"] & 7 != 2 or symbol["st_size"] != 31 * 8 or offset < 0 or offset + 31 * 8 > section["sh_size"]:
            raise ValueError("SU namespace independent 31x64-bit layout bounds/RO/NX mismatch")
        raw = section.data()[offset:offset + 31 * 8]
        return {"layoutSchema": 1, "layoutWords": 31, "layoutWordBytes": 8,
            "layoutValues": [int.from_bytes(raw[pos:pos + 8], "little") for pos in range(0, len(raw), 8)],
            "compiledLayoutSha256": hashlib.sha256(raw).hexdigest(), "symbol": symbol.name, "sectionName": section.name,
            "sectionFlags": section["sh_flags"], "objectIdentity": identity(path)}


def su_namespace_object_declaration(declarations, variable):
    """Expand the actual data probe pointee; named pointer ABI intentionally omits its body."""
    indexes = declarations.variables.get(variable, ())
    if len(indexes) != 1:
        raise ValueError("SU namespace data declaration missing/ambiguous: " + variable)
    pointer = declarations.resolve(declarations.types[indexes[0]][3])
    if declarations.types[pointer][1] != 2 or declarations.type_size(pointer) != 8:
        raise ValueError("SU namespace data declaration is not one native pointer: " + variable)
    pointee = declarations.resolve(declarations.types[pointer][3])
    if not pointee or declarations.types[pointee][1] not in (4, 5):
        raise ValueError("SU namespace data declaration has no complete aggregate: " + variable)
    signature = declarations.signature(pointee)
    if declarations.type_size(pointee) <= 0 or ":" not in signature:
        raise ValueError("SU namespace data declaration aggregate is incomplete: " + variable)
    return "*" + signature


def collect_su_namespace_abi(target, reference, policy, objects, record):
    native = record["nativeNamespace"]
    try:
        values = object_words(target / "manager_su_namespace_facts.o", "msu_su_namespace_build_facts")
        if len(values) != 3 or values[0] != 1 or values[1] not in (0, 1) or values[2] != 0:
            raise ValueError("SU namespace source/verification gate facts mismatch")
        native["compiledFacts"] = dict(zip(("schema", "nativeSource", "nativeVerified"), values))
        if not values[1]:
            native["missing"].append("Native namespace source branch not compiled for this target")
            return
        native.update(su_namespace_words(target / "su_profile_namespace.o"))
        if native["layoutValues"][0] != 1 or native["layoutValues"] != su_namespace_words(target / "manager_su_namespace_facts.o")["layoutValues"]:
            raise ValueError("SU namespace actual production/probe layout differs")
        layouts = native["layoutValues"]
        if layouts[1] != record["compiledFacts"]["namespaceReceiptBytes"]:
            raise ValueError("SU namespace native/receipt C facts differ")
        for member, at in zip(SU_COMPONENT_LAYOUTS["namespaceReceipt"][2], range(2, 9)):
            if layouts[at] != record["compiledFacts"]["namespaceReceipt" + "".join(word.capitalize() for word in member.split("_")) + "Offset"]:
                raise ValueError("SU namespace native receipt offset mismatch: " + member)
        declarations = objects["manager_su_namespace_facts"]
        for key, (kind, fields, size_at) in SU_NAMESPACE_TYPES.items():
            selected = declarations.named_structure_selected(kind, fields)
            native["compiledLayouts"][key] = selected
            if (size_at is not None and selected["bytes"] != layouts[size_at]) or any(selected["members"][member]["offset"] != layouts[at] for member, at in fields.items()):
                raise ValueError("SU namespace actual C/BTF layout mismatch: " + key)
            observed = reference.named_structure_selected(kind, fields)
            native["targetLayouts"][key] = observed
            if observed != selected:
                raise ValueError("SU namespace compiled/reference BTF mismatch: " + key)
        if native["compiledLayouts"]["ptRegs"]["members"]["regs"]["bytes"] // 31 != layouts[11]:
            raise ValueError("SU namespace actual ARM64 register argument width mismatch")
        evidence_path = target / "reference-function-markers.json"
        evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
        source = json.loads((target / "reference-source.json").read_text(encoding="utf-8"))
        if evidence["sourceBinding"] != {key: source[key] for key in ("sourceFileBytes", "btfSha256", "symtabSha256", "sectionHeadersSha256")}:
            raise ValueError("SU namespace native marker/reference source mismatch")
        with (target / "manager_su_namespace_facts.o").open("rb") as stream:
            table = ELFFile(stream).get_section_by_name(".symtab")
            for index, name in enumerate(SU_NAMESPACE_FUNCTIONS):
                declared = declarations.variable("msu_su_namespace_decl_" + str(index))
                signature = reference.function(name)
                if declared != "*" + signature:
                    raise ValueError("SU namespace native declaration/reference ABI mismatch: " + name)
                marker = evidence.get("coreMarkers", {}).get(name, {})
                if not marker.get("available") or marker.get("function") != name:
                    raise ValueError("SU namespace native callee marker missing: " + name)
                item = native["functionAbi"][name] = {"signature": signature, "declarationSignature": declared, "marker": marker, "verified": False}
                if policy["kcfiEnabled"]:
                    matches = table.get_symbol_by_name("__kcfi_typeid_" + name) or []
                    if len(matches) != 1 or matches[0]["st_shndx"] != "SHN_ABS" or marker.get("prefixAddress") != marker["address"] - 4 or marker.get("prefixWord") != (matches[0]["st_value"] & 0xffffffff):
                        raise ValueError("SU namespace native declaration/reference KCFI mismatch: " + name)
                    item["cfi"] = {"mode": "kcfi", "typeidSymbol": "__kcfi_typeid_" + name, "declarationTypeid": matches[0]["st_value"] & 0xffffffff, "referencePrefixWord": marker["prefixWord"], "referencePrefixVerified": True}
                item["verified"] = True
        for index, name in enumerate(SU_NAMESPACE_DATA):
            declared = su_namespace_object_declaration(declarations, "msu_su_namespace_data_" + str(index))
            signature = reference.variable(name)
            marker = evidence.get("suDataMarkers", {}).get(name, {})
            item = native["dataAbi"][name] = {"signature": signature, "declarationSignature": declared, "marker": marker, "verified": False}
            if declared != "*" + signature or not marker.get("available") or marker.get("name") != name:
                raise ValueError("SU namespace native data object/type marker mismatch: " + name)
            expected_size = layouts[24 if name == "init_cred" else 25 if name == "init_pid_ns" else 26]
            if marker["bytes"] != expected_size:
                raise ValueError("SU namespace native data object compiled/reference size mismatch: " + name)
            item["verified"] = True
        native["metadataComplete"] = True
    except (OSError, ValueError, KeyError, IndexError) as error:
        native["missing"].append(type(error).__name__ + ": " + str(error))


def collect_su_component_abi(target, payload, reference):
    record = su_component_pending_abi()
    try:
        inputs = json.loads((target / "build-inputs.json").read_text(encoding="utf-8"))
        if inputs.get("suSourceSelection") != SU_COMPONENT_SELECTION or inputs.get("coreSourceSelection") != core_public_selection():
            raise ValueError("SU component actual build generation/CORE selection mismatch")
        if inputs.get("suProfileMetadataSelection") != SU_COMPONENT_METADATA_SELECTION:
            raise ValueError("SU profile metadata differs from the actual build generation")
        for source in su_source_paths():
            if sha(target / source.name) != sha(source):
                raise ValueError("SU component actual staged source differs: " + source.name)
        for name in (*SU_COMPONENT_OBJECTS, *SU_COMPONENT_PROBES):
            proof = object_provenance(target, name)
            path = target / (name + ".c")
            if proof["sourceIdentity"]["sha256"] != sha(path) or (name in SU_COMPONENT_PROBES and path.read_bytes() != su_component_probe(name).encode("utf-8")):
                raise ValueError("SU component actual object/source/probe generation mismatch: " + name)
            record["sourceIdentities"].append(identity(path))
            record["objectIdentities"].append(identity(target / (name + ".o")))
            record["commandIdentities"].append(identity(target / ("." + name + ".o.cmd")))
            record["dependencyReceiptIdentities"].append(identity(target / "build-dependencies" / (name + ".json")))
            record["inlineDependencies"].extend(proof["dependencies"])
        for name, key, expected in (("manager_su_component_facts", "compiledFacts", SU_COMPONENT_FACTS), ("manager_su_profile_facts", "profileCompiledFacts", SU_COMPONENT_PROFILE_FACTS)):
            values = object_words(target / (name + ".o"), "msu_su_component_build_facts")
            if len(values) != len(expected) or values[0] != 1:
                raise ValueError("SU component actual C facts schema/count mismatch: " + name)
            record[key] = dict(zip(expected, values))
        objects = {name: object_btf(target / (name + ".o")) for name in (*SU_COMPONENT_OBJECTS, *SU_COMPONENT_PROBES, "manager_management", "manager_uid_gateway", "manager_task_state", "manager_core_uid_facts")}
        for prefix, (kind, probe, members) in SU_COMPONENT_LAYOUTS.items():
            facts = record["profileCompiledFacts"] if probe == "manager_su_profile_facts" else record["compiledFacts"]
            layout = objects[probe].named_structure_selected(kind, members)
            if layout["bytes"] != facts[prefix + "Bytes"] or facts[prefix + "Align"] <= 0 or facts[prefix + "Align"] & (facts[prefix + "Align"] - 1):
                raise ValueError("SU component C/BTF size/alignment mismatch: " + prefix)
            for member in members:
                key = prefix + "".join(word.capitalize() for word in member.split("_"))
                if layout["members"][member]["offset"] != facts[key + "Offset"] or layout["members"][member]["bytes"] != facts[key + "Bytes"]:
                    raise ValueError("SU component C/BTF member mismatch: " + prefix + "." + member)
            record["compiledLayouts"][prefix] = layout
        for obj, names in SU_COMPONENT_FUNCTIONS.items():
            for name in names:
                record["localFunctionAbi"][name] = objects[obj].function(name)
        for key, (caller, function, index, obj, name) in SU_COMPONENT_PARAMETERS.items():
            source = objects[caller].function_parameter(function, index)
            signature = objects[obj].function(name)
            if source != "*" + signature:
                raise ValueError("SU component actual CORE callback parameter mismatch: " + key)
            record["callbackOrigins"][key] = {"kind": "internal", "object": caller, "source": function, "parameterIndex": index, "definition": name, "signature": source}
        fields = SU_COMPONENT_LAYOUTS["registrySource"][2][1:]
        record["sourceCallbackAbi"] = {member: objects["manager_su_component_facts"].member_function("msu_su_registry_source", member) for member in fields}
        if record["sourceCallbackAbi"]["validate_profile"] != "*" + objects["su_profile_execution"].function("msu_su_profile_check_execution"):
            raise ValueError("SU component validate_profile implementation/header signature mismatch")
        for member in ("check_closed", "begin", "revoke", "finish", "retire"):
            signature = objects["manager_core_uid_facts"].member_function("msu_uid_reader_fence", member)
            name = SU_COMPONENT_CALLBACKS["su.fence." + member][1]
            if signature != "*" + objects["su_install_registry"].function(name):
                raise ValueError("SU component actual CORE reader fence callback mismatch: " + member)
            record["readerFenceAbi"][member] = {"definition": name, "signature": signature}
        for obj, callers in SU_COMPONENT_RELOCATIONS.items():
            record["callbackRelocations"][obj] = su_component_relocations(target / (obj + ".o"), callers)
        core_names = ("manager_management", "manager_uid_gateway", "manager_task_state", "manager_core_uid_facts")
        policies = {name: core_cfi_policy(target / ("." + name + ".o.cmd")) for name in (*SU_COMPONENT_OBJECTS, *SU_COMPONENT_PROBES, *core_names)}
        if len({tuple(item.values()) for item in policies.values()}) != 1:
            raise ValueError("SU component actual source/probe/CORE KCFI policies differ")
        policy = policies["su_install_registry"]
        record["callbackCfi"] = {"mode": "kcfi" if policy["kcfiEnabled"] else "none", "normalizeIntegers": policy["normalizeIntegers"], "objectPolicies": policies, "functions": {}, "callbacks": {}}
        for obj, names in SU_COMPONENT_FUNCTIONS.items():
            for name in names:
                record["callbackCfi"]["functions"][name] = su_component_entry(target, obj, name, objects[obj], objects["manager_su_component_callback_types"], policy)
        for key, (obj, name) in SU_COMPONENT_CALLBACKS.items():
            record["callbackCfi"]["callbacks"][key] = su_component_entry(target, obj, name, objects[obj], objects["manager_su_component_callback_types"], policy)
        config = json.loads((target / "msu_resolver_build_config.json").read_text(encoding="utf-8"))
        record["config"] = {key: config["managementConfig"].get(key, "n") for key in ("CONFIG_MULTIUSER", "CONFIG_PREEMPT_RCU", "CONFIG_TREE_RCU", "CONFIG_REFCOUNT_FULL", "CONFIG_DEBUG_SPINLOCK", "CONFIG_NAMESPACES", "CONFIG_CFI", "CONFIG_CFI_CLANG", "CONFIG_CFI_ICALL_NORMALIZE_INTEGERS")}
        enabled = any(record["config"][key] == "y" for key in ("CONFIG_CFI", "CONFIG_CFI_CLANG"))
        if policy["kcfiEnabled"] != enabled or enabled and policy["normalizeIntegers"] != (record["config"]["CONFIG_CFI_ICALL_NORMALIZE_INTEGERS"] == "y"):
            raise ValueError("SU component actual CFI policy/reference config mismatch")
        record["objectUndefinedSymbols"] = {}
        for obj in SU_COMPONENT_OBJECTS:
            with (target / (obj + ".o")).open("rb") as stream:
                record["objectUndefinedSymbols"][obj] = sorted({symbol.name for symbol in ELFFile(stream).get_section_by_name(".symtab").iter_symbols() if symbol.name and symbol["st_shndx"] == "SHN_UNDEF"})
        internal = {name for names in SU_COMPONENT_FUNCTIONS.values() for name in names} | {name for names in CORE_FUNCTIONS.values() for name in names} | {"msu_uid_gateway_acquire", "msu_uid_snapshot_lookup", "msu_uid_snapshot_put"} | set(task_profile_exports())
        if internal & set(payload["imports"]):
            raise ValueError("SU component internal API became a reference import")
        undefined = {name for names in record["objectUndefinedSymbols"].values() for name in names}
        record["referenceSymbolSelectionIdentity"] = identity(target / "reference.symbols")
        record["finalImports"] = {name: reference_import_signature(target, reference, name)
            for name in sorted(undefined & set(payload["imports"]))}
        collect_su_namespace_abi(target, reference, policy, objects, record)
        with (target / "claim.so").open("rb") as stream:
            elf = ELFFile(stream)
            table = elf.get_section_by_name(".symtab")
            for name, item in record["callbackCfi"]["functions"].items():
                symbols = table.get_symbol_by_name(name) or []
                if not symbols:
                    record["entrypoints"][name] = {"available": False, "reason": "No actual retained native factory/consumer root"}
                    continue
                if len(symbols) != 1 or symbols[0]["st_info"]["type"] != "STT_FUNC":
                    raise ValueError("SU component final entry not unique: " + name)
                symbol = symbols[0]
                section = elf.get_section(symbol["st_shndx"])
                at, size = symbol["st_value"] - section["sh_addr"], symbol["st_size"]
                if not section["sh_flags"] & 4 or symbol["st_value"] % 4 or at < 0 or size <= 0 or at + size > section["sh_size"]:
                    raise ValueError("SU component final entry bounds invalid: " + name)
                entry = record["entrypoints"][name] = {"available": True, "entryOffset": symbol["st_value"], "bytes": size, "fileOffset": section["sh_offset"] + at, "sectionName": section.name, "sectionOffset": section["sh_addr"], "sectionBytes": section["sh_size"]}
                if enabled:
                    if at < 4 or int.from_bytes(section.data()[at - 4:at], "little") != item["typeid"]:
                        raise ValueError("SU component final entry KCFI prefix mismatch: " + name)
                    entry.update(prefixOffset=symbol["st_value"] - 4, prefixWord=item["typeid"], typeid=item["typeid"], prefixVerified=True)
        record["entrypointsComplete"] = all(item["available"] for item in record["entrypoints"].values())
        record["kernelMetadataComplete"] = record["nativeNamespace"]["metadataComplete"]
        if not record["entrypointsComplete"]:
            record["missing"].append("Actual native factory/consumer roots are incomplete")
        record["missing"].extend(record["nativeNamespace"]["missing"])
    except (OSError, ValueError, KeyError, IndexError) as error:
        record["missing"].append(type(error).__name__ + ": " + str(error))
    source = record["sourceQualification"] = collect_runtime_source_qualification(target, payload)
    record["missing"].extend(source["missing"])
    record["kernelMetadataComplete"] = record["kernelMetadataComplete"] and source["metadataComplete"]
    if source["metadataComplete"]:
        pending = {
            "No trusted PMS/durable grant source or actual retained native factory/binding is selected",
            "Selected observed-install/durable authority and retained factory need same-generation linked and target-qualified execution"}
        record["missing"] = ["Actual target SU execution qualification remains required" if reason in pending else reason
            for reason in record["missing"] if reason != "Reader binding needs real dual-use or same-target C++ facts"]
        record["nativeReaderBinding"] = {"mode": "same-kernel-C-factory", "sourceQualified": True,
            "entry": source["retainedFunctions"]["msu_su_read_current"],
            "context": "factory-owned registry; lease and provider fence retirement bound in sourceQualification",
            "targetQualified": False, "deviceQualified": False}
        record["readerBinding"]["reason"] = "Historical SDK C++ binding remains unavailable; selected kernel factory uses nativeReaderBinding"
    return record


def collect_reference_abi(kernel, out):
    """Extract ABI facts for an existing claim.so without rebuilding native code."""
    target = out / kernel
    payload = inspect(target / "claim.so")
    _, mounts, image, _, vmlinux = kernel_environment(kernel, out)
    mounts = [value + ",readonly" if value.endswith("target=/kernel-output") else value
              for value in mounts]
    btf_path, symbols_path = target / "reference.btf", target / "reference.symbols"
    for generated in (btf_path, symbols_path, target / "reference-source.json",
                      target / "reference-function-markers.json", target / "reference-symbol-extents.json"):
        generated.unlink(missing_ok=True)
    (target / "reference-extract.py").write_text(REFERENCE_EXTRACTOR, newline="\n")
    (target / "reference-imports.json").write_text(
        json.dumps(sorted(set(payload["imports"]) | set(BOOTSTRAP_FUNCTIONS) |
                          (set(core_native_function_names(target)) if CORE_SELECTION else set()) |
                          (set(HOST_RUNTIME_NATIVE_FUNCTIONS) if HOST_DEPLOYMENT_SELECTION is not None and HOST_DEPLOYMENT_SELECTION.get("runtimeChannel") else set()) |
                          (set(SU_NAMESPACE_FUNCTIONS) | set(SU_NAMESPACE_DATA) if SU_COMPONENT_SELECTION else set()))) + "\n")
    name = "maplesu-claim-abi-" + kernel + "-" + uuid.uuid4().hex[:6]
    command = ["docker", "run", "--rm", "--pull", "never", "--network", "none",
               "--name", name, "--cap-drop", "ALL", "--security-opt", "no-new-privileges", *mounts,
               f"ghcr.io/ylarod/ddk-min:{image}-20260828", "python3",
               f"/output/{kernel}/reference-extract.py", vmlinux,
               f"/output/{kernel}/reference-imports.json", f"/output/{kernel}"]
    raw_contract = None
    if kernel == "android17-6.18":
        raw_contract = ROOT / "jailbreak-providers/root-loader/contracts/family-6.18.json"
        command = ["docker", "run", "--rm", "--pull", "never", "--network", "none",
            "--name", name, "--cap-drop", "ALL", "--security-opt", "no-new-privileges", *mounts,
            f"ghcr.io/ylarod/ddk-min:{image}-20260828", "python3",
            "/source/scripts/policy_reference_image.py", "--contract",
            "/source/jailbreak-providers/root-loader/contracts/family-6.18.json",
            "--selection", f"/output/{kernel}/reference-imports.json",
            "--output", f"/output/{kernel}", "--kernel-source", "/kernel"]
    record = {
        "schemaVersion": 1, "claimSha256": payload["sha256"], "complete": False,
        "source": {"kernelFamily": kernel, "vmlinuxPath": vmlinux,
                   "dockerImage": f"ghcr.io/ylarod/ddk-min:{image}-20260828"},
        "generatorSha256": {p.name: sha(p) for p in
                            (Path(__file__), ROOT / "scripts/policy_btf_abi.py", ROOT / "scripts/policy_reference_abi.py", ROOT / "scripts/policy_reference_image.py", ROOT / "scripts/policy_payload_image_target.py")},
        "functionAbi": {}, "callbackAbi": {}, "dataImports": [], "dataAbi": {}, "missing": [],
        "bootstrapFunctionAbi": {}, "bootstrapUnavailable": {}, "bootstrapSelection": {},
        "command": command,
    }
    if raw_contract is not None:
        record["source"].update(rawReferenceContract=identity(raw_contract), referenceFormat="raw-arm64-image")
    if CORE_SELECTION is not None:
        record["coreLifecycleAbi"] = core_pending_abi()
    if SU_SELECTION is not None:
        record["suProviderAbi"] = su_component_pending_abi() if SU_COMPONENT_SELECTION else su_pending_abi()
    if HOST_DEPLOYMENT_SELECTION is not None and HOST_DEPLOYMENT_SELECTION.get("runtimeChannel"):
        record["hostRuntimeAbi"] = host_runtime_pending_abi()
    def output_text(value):
        return value.decode("utf-8", "replace") if isinstance(value, bytes) else (value or "")
    try:
        result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8",
                                errors="replace", timeout=120)
    except subprocess.TimeoutExpired as error:
        result = subprocess.CompletedProcess(command, 124, output_text(error.stdout),
            output_text(error.stderr) + "\nReference extraction exceeded 120 seconds")
    except OSError as error:
        result = subprocess.CompletedProcess(command, 127, "", str(error))
    finally:
        try:
            cleanup = subprocess.run(["docker", "rm", "-f", name], capture_output=True,
                                     text=True, timeout=20)
            record["containerCleanupExitCode"] = cleanup.returncode
        except (OSError, subprocess.TimeoutExpired) as error:
            record["containerCleanupError"] = str(error)
    record["exitCode"] = result.returncode
    (target / "reference-abi.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        record["missing"].append({"kind": "reference-source", "name": vmlinux,
                                  "reason": (result.stderr or result.stdout).strip()})
    else:
        try:
            data = btf_path.read_bytes()
            record["source"].update(json.loads((target / "reference-source.json").read_text()))
            record["source"]["symbolSelectionSha256"] = sha(symbols_path)
            record["source"]["symbolExtentSha256"] = sha(target / "reference-symbol-extents.json")
            btf = Btf(data)
            symbols = {}
            for line in symbols_path.read_text().splitlines():
                fields = line.split()
                if len(fields) >= 8 and fields[0].endswith(":") and fields[0][:-1].isdigit() and \
                        fields[6] != "UND":
                    symbols.setdefault(fields[7], set()).add(fields[3])
            btf = ReferenceAbi(btf, target, record["source"], symbols,
                               object_provenance, object_btf, identity)
            record["declarationAbi"] = btf.evidence
            for imported in payload["imports"]:
                kinds = symbols.get(imported, set())
                if kinds == {"OBJECT"}:
                    record["dataImports"].append(imported)
                    try:
                        record["dataAbi"][imported] = btf.variable(imported)
                    except ValueError as error:
                        record["missing"].append({"kind": "data", "name": imported, "reason": str(error)})
                elif kinds == {"FUNC"}:
                    try:
                        record["functionAbi"][imported] = btf.function(imported)
                    except ValueError as error:
                        record["missing"].append({"kind": "function", "name": imported, "reason": str(error)})
                else:
                    record["missing"].append({"kind": "symbol", "name": imported,
                        "reason": "Expected unique STT_FUNC/STT_OBJECT: " + repr(sorted(kinds))})
            for member in ("handle_inode_event", "freeing_mark", "free_mark"):
                callback = "fsnotify_ops." + member
                try:
                    record["callbackAbi"][callback] = btf.member_function("fsnotify_ops", member)
                except ValueError as error:
                    record["missing"].append({"kind": "callback", "name": callback, "reason": str(error)})
            for function in BOOTSTRAP_FUNCTIONS:
                if symbols.get(function) != {"FUNC"}:
                    record["bootstrapUnavailable"][function] = "No unique STT_FUNC in actual reference"
                    continue
                try:
                    record["bootstrapFunctionAbi"][function] = btf.function(function)
                except ValueError as error:
                    record["bootstrapUnavailable"][function] = str(error)
            allocate = "module_alloc" if symbols.get("module_alloc") == {"FUNC"} else "execmem_alloc"
            release = "module_memfree" if allocate == "module_alloc" else "execmem_free"
            cache = "sync_icache_aliases"  # Resident carrier requires this actual C/BTF wrapper.
            selected = {"allocate": allocate, "release": release, "cache": cache}
            selected.update({function: function for function in
                             ("set_memory_nx", "set_memory_rw", "set_memory_ro", "set_memory_x")})
            record["bootstrapSelection"] = selected
            for function in selected.values():
                if function not in record["bootstrapFunctionAbi"]:
                    record["missing"].append({"kind": "bootstrap-function", "name": function,
                        "reason": record["bootstrapUnavailable"].get(function, "Missing actual reference ABI")})
            record["managementAbi"] = collect_management_abi(target, payload, btf)
            for callback, origin in record["managementAbi"]["callbackOrigins"].items():
                record["callbackAbi"][callback] = origin["signature"]
            uid = record["managementAbi"].get("uidSnapshotAbi", {})
            for callback, origin in uid.get("callbackOrigins", {}).items():
                record["callbackAbi"][callback] = origin["signature"]
            for reason in uid.get("missing", []):
                record["missing"].append({"kind": "uid-snapshot", "name": "uidSnapshotAbi", "reason": reason})
            for reason in record["managementAbi"].get("procattrAbi", {}).get("missing", []):
                record["missing"].append({"kind": "procattr", "name": "procattrAbi", "reason": reason})
            if SU_SELECTION is not None:
                record["suProviderAbi"] = collect_su_component_abi(target, payload, btf) if SU_COMPONENT_SELECTION else collect_su_provider_abi(target, payload, btf)
            if CORE_SELECTION is not None:
                record["coreLifecycleAbi"] = collect_core_lifecycle_abi(target, payload, btf)
            if task_profile_sources() is not None:
                record["taskProfileAbi"] = collect_task_profile_abi(target, payload, record)
            if "hostRuntimeAbi" in record:
                record["hostRuntimeAbi"] = collect_host_runtime_abi(target, payload, btf)
                for reason in record["hostRuntimeAbi"]["missing"]:
                    record["missing"].append({"kind": "host-runtime", "name": "hostRuntimeAbi", "reason": reason})
            for reason in record["managementAbi"]["missing"]:
                record["missing"].append({"kind": "management", "name": "managementAbi", "reason": reason})
        except Exception as error:
            record["missing"].append({"kind": "reference-parser", "name": vmlinux,
                                      "reason": type(error).__name__ + ": " + str(error)})
    if sha(target / "claim.so") != payload["sha256"]:
        record["missing"].append({"kind": "payload-identity", "name": "claim.so",
                                  "reason": "claim.so changed during ABI reference extraction"})
    record["complete"] = not record["missing"]
    (target / "reference-abi.json").write_text(json.dumps(record, indent=2) + "\n", newline="\n")
    return record



def build_support(out, retirement_policy=None, bootstrap_only=False):
    target = out / "support"
    target.mkdir(parents=True, exist_ok=True)
    staged_inputs = []
    source_root = target / "source/runtime/ultra"
    support_sources = list(SUPPORT_SOURCES)
    for name in ("manager_claim_entry.h", "manager_claim_abi.h", "manager_apk_identity.h"):
        header, = [path for path in SOURCES if path.name == name]
        support_sources.append(header)
    for source, relative in [(p, "identity/kernel/" + p.name) for p in support_sources] + [(PROTOCOL, "authorization/request_protocol.h")]:
        before = identity(source)
        data = source.read_bytes()
        if len(data) != before["bytes"] or hashlib.sha256(data).hexdigest() != before["sha256"]:
            raise ValueError("Support source changed while captured: " + str(source))
        destination = source_root / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        copied = identity(destination)
        if any(copied[key] != before[key] for key in ("bytes", "sha256")) or identity(source) != before:
            raise ValueError("Actual selected support copy differs: " + str(source))
        staged_inputs.append({"logicalPath": "runtime/ultra/" + relative,
            "selectedSource": before, "compiledCopy": copied})
    source_receipt = target / "source-inputs.json"
    source_receipt.write_text(json.dumps({"schema": "maplesu-support-selected-source-inputs/1",
        "hostDeployment": host_deployment_public_selection() if HOST_DEPLOYMENT_SELECTION is not None else None,
        "files": staged_inputs}, indent=2) + "\n", encoding="utf-8", newline="\n")
    commands = ["set -eu"]
    support = (("bootstrap", "msu_manager_claim_bootstrap", "manager_claim_bootstrap.c"),
               ("materializer", "msu_manager_claim_materialize", "manager_claim_loader.c"),
               ("permissions", "msu_manager_claim_permissions", "manager_claim_permissions.c"))
    if bootstrap_only:
        support = support[:1]
    normalize_integers = retirement_policy["normalizeIntegers"] if retirement_policy is not None else True
    for name, entry, source in support:
        retirement_flags = ("-DMSU_RESIDENT_RETIREMENT_KCFI=1 -fsanitize=kcfi " +
            ("-fsanitize-cfi-icall-experimental-normalize-integers " if normalize_integers else "")
            if name == "bootstrap" and HOST_RETIREMENT_SELECTION is not None else "")
        (target / f"{name}.ld").write_text(
            "SECTIONS { . = 0; .text : { KEEP(*(.text." + entry + ")) "
            "*(.text*) *(.rodata*) . = ALIGN(4); } /DISCARD/ : { *(.comment) *(.note*) *(.eh_frame*) } }\n",
            newline="\n")
        commands += [
            "clang --target=aarch64-none-elf -Os -ffreestanding -fno-builtin "
            "-fno-stack-protector -fno-vectorize -fno-slp-vectorize -fno-jump-tables "
            "-fPIC -mcmodel=tiny -ffixed-x18 -mgeneral-regs-only -mbranch-protection=standard -mno-outline-atomics "
            "-ffunction-sections -fdata-sections -fno-asynchronous-unwind-tables "
            f"{retirement_flags}-Wall -Wextra -Werror -c /output/support/source/runtime/ultra/identity/kernel/{source} "
            f"-o /output/support/{name}.o",
            f"ld.lld --entry={entry} -T /output/support/{name}.ld "
            f"-o /output/support/{name}.elf /output/support/{name}.o",
            f"llvm-objcopy -O binary --only-section=.text /output/support/{name}.elf "
            f"/output/support/{name}.bin",
            f"llvm-objdump -d /output/support/{name}.elf > /output/support/{name}.disasm",
        ]
    (target / "build.sh").write_text("\n".join(commands) + "\n", newline="\n")
    command = ["docker", "run", "--rm", "--pull", "never", "--network", "none",
               "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
               "--mount", f"type=bind,source={ROOT},target=/source,readonly",
               "--mount", f"type=bind,source={out},target=/output",
               "ghcr.io/ylarod/ddk-min:android16-6.12-20260828", "sh", "/output/support/build.sh"]
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8",
                            errors="replace", timeout=120)
    (target / "build.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    report = {}
    for name, _, _ in support:
        with (target / f"{name}.elf").open("rb") as stream:
            elf = ELFFile(stream)
            text = elf.get_section_by_name(".text")
            imports = [s.name for s in elf.get_section_by_name(".symtab").iter_symbols()
                       if s.name and s["st_shndx"] == "SHN_UNDEF"]
            assert not imports and elf["e_entry"] == text["sh_addr"] == 0
            assert not any(isinstance(s, RelocationSection) and s.num_relocations()
                           for s in elf.iter_sections())
            assert not any(s["sh_flags"] & 2 and s.name != ".text" and s["sh_size"]
                           for s in elf.iter_sections())
            binary = target / f"{name}.bin"
            assert binary.read_bytes() == text.data() and binary.stat().st_size % 4 == 0
            # Carrier placement is only four-byte aligned. Page-relative ADRP
            # instructions would still encode the linked blob's page alignment.
            assert not any(int.from_bytes(text.data()[at:at + 4], "little") & 0x9f000000 == 0x90000000
                           for at in range(0, len(text.data()), 4))
            report[name] = {"bytes": binary.stat().st_size, "sha256": sha(binary),
                            "entryOffset": 0, "imports": [], "relocations": 0,
                            "sourceInputsIdentity": identity(source_receipt)}
            if name == "materializer":
                versions = re.findall(r"^#define MSU_LOADER_VERSION (\d+)U$",
                    (source_root / "identity/kernel/manager_claim_loader.h").read_text(), re.M)
                if versions != ["3"]:
                    raise ValueError("Selected materializer source is not protocol v3")
                report[name]["protocolVersion"] = int(versions[0])
            if name == "bootstrap":
                if HOST_RETIREMENT_SELECTION is not None:
                    callback, = elf.get_section_by_name(".symtab").get_symbol_by_name("msu_manager_resident_retire_work")
                    at = callback["st_value"] - text["sh_addr"]
                    if at < 4 or at + callback["st_size"] > len(text.data()):
                        raise ValueError("Image retirement callback is outside paired support bytes")
                    report[name]["retirement"] = {"sourceSelection": HOST_RETIREMENT_SELECTION,
                        "callbackOffset": at, "kcfiPrefix": int.from_bytes(text.data()[at - 4:at], "little"),
                        "normalizeIntegers": normalize_integers, "deviceQualified": False}
                symbols = elf.get_section_by_name(".symtab").get_symbol_by_name("msu_manager_resident_boot")
                if symbols:
                    if len(symbols) != 1 or symbols[0]["st_info"]["type"] != "STT_FUNC" or \
                            not isinstance(symbols[0]["st_shndx"], int):
                        raise ValueError("Ambiguous/non-function resident boot entry in actual support ELF")
                    symbol = symbols[0]
                    section = elf.get_section(symbol["st_shndx"])
                    offset = symbol["st_value"] - text["sh_addr"]
                    if not section["sh_flags"] & 4 or section.name != text.name or offset < 0 or offset % 4 or \
                            symbol["st_size"] <= 0 or offset + symbol["st_size"] > binary.stat().st_size:
                        raise ValueError("Actual resident boot entry is outside bootstrap executable bytes")
                    report[name]["bootEntryOffset"] = offset
                deployment = elf.get_section_by_name(".symtab").get_symbol_by_name("msu_manager_resident_boot_deployment")
                if deployment:
                    if len(deployment) != 1 or deployment[0]["st_info"]["type"] != "STT_FUNC" or                             not isinstance(deployment[0]["st_shndx"], int):
                        raise ValueError("Ambiguous/non-function deployment boot entry in actual support ELF")
                    symbol = deployment[0]
                    offset = symbol["st_value"] - text["sh_addr"]
                    if symbol["st_shndx"] != elf.get_section_index(".text") or offset < 0 or offset % 4 or                             symbol["st_size"] <= 0 or offset + symbol["st_size"] > binary.stat().st_size:
                        raise ValueError("Actual deployment boot entry is outside bootstrap executable bytes")
                    facts = object_words(target / "bootstrap.elf", "msu_manager_deployment_support_facts")
                    if facts != [4, 56, 48, 24] or "bootEntryOffset" not in report[name]:
                        raise ValueError("Actual deployment support facts disagree with the selected ABI")
                    report[name].update(deploymentBootEntryOffset=offset, deploymentAnchorVersion=facts[0],
                        deploymentBindingBytes=facts[1], deploymentConfigBytes=facts[2], deploymentRecordBytes=facts[3])
    return report



def reference_data_probe_source(imports):
    # Names come from the actual linked import table; declarations come from headers.
    known = {"__tracepoint_sched_process_exec", "__tracepoint_sched_process_exit", "cpu_hwcaps",
        "system_cpucaps", "init_cred", "init_nsproxy", "init_pid_ns", "init_user_ns", "kmalloc_caches",
        "memmove", "memchr", "strchr", "__arch_copy_to_user", "namespace_sem", "system_wq", "system_percpu_wq", "text_mutex", "tracepoint_srcu"}
    names = sorted(set(imports) & known)
    headers = ("compiler", "cred", "init_task", "nsproxy", "pid_namespace", "user_namespace", "slab", "workqueue",
               "tracepoint", "memory", "string", "rwsem", "uaccess")
    return ("\n/* Actual same-target typed-address declaration probes. */\n" +
        "".join("#include <linux/" + name + ".h>\n" for name in headers) +
        "#include <asm/cpufeature.h>\nextern struct rw_semaphore namespace_sem;\n" +
        "".join("static typeof(&" + name + ") msu_reference_decl_" + name +
                " __used = &" + name + ";\n" for name in names))


def rebuild_reference_data_probes(kernel, out, payload, command):
    target = out / kernel
    source = target / "manager_management_facts.c"
    original = source.read_text()
    source.write_text(original + reference_data_probe_source(payload["imports"]), newline="\n")
    make, _, _, _, _ = kernel_environment(kernel, out)
    script = (target / "build.sh").read_text()
    pahole = [line for line in script.splitlines() if line.startswith("pahole ") and
              line.endswith("/manager_management_facts.o")]
    capture = [line for line in script.splitlines() if "capture-dependencies.py /output/" in line]
    if len(pahole) != 1 or len(capture) != 1:
        raise ValueError("Typed-address probe rebuild suffix is not unique")
    staging = "/output/" + kernel + "/reference-probe-transform"
    transform = "python3 - <<'PYREFERENCE'\n" + \
        "from pathlib import Path\nimport json,shutil\nt=Path('/output/" + kernel + "')\n" + \
        "s=t/'reference-probe-transform'\nr=json.loads((s/'debug-transformations.json').read_text())\n" + \
        "d=json.loads((t/'debug-transformations.json').read_text())\n" + \
        "d['objects']=[v for v in d['objects'] if v['object']!='manager_management_facts']+r['objects']\n" + \
        "shutil.copyfile(s/'manager_management_facts.o',t/'manager_management_facts.o')\n" + \
        "shutil.copyfile(s/'compiler-objects/manager_management_facts.o',t/'compiler-objects/manager_management_facts.o')\n" + \
        "(t/'debug-transformations.json').write_text(json.dumps(d,indent=2)+'\\n')\nPYREFERENCE"
    lines = ["set -eu", *[line for line in script.splitlines() if line.startswith("export LD_LIBRARY_PATH=")],
        make + " -j2 manager_management_facts.o", pahole[0], "mkdir " + staging,
        "cp /output/" + kernel + "/manager_management_facts.o " + staging + "/",
        "cp /output/" + kernel + "/.manager_management_facts.o.cmd " + staging + "/",
        "python3 /source/scripts/policy_object_debug.py " + staging, transform, capture[0]]
    suffix = target / "rebuild-reference-probes.sh"
    suffix.write_text("\n".join(lines) + "\n", newline="\n")
    argv = list(command)
    if "--name" in argv:
        argv[argv.index("--name") + 1] += "-typed"
    argv[-1] = "/output/" + kernel + "/rebuild-reference-probes.sh"
    completed = subprocess.run(argv,capture_output=True,text=True,encoding="utf-8",errors="replace",timeout=180)
    (target / "reference-probes.log").write_text(completed.stdout + completed.stderr)
    if completed.returncode:
        raise RuntimeError("Actual typed-address probe compilation failed: " + completed.stderr[-4000:])
    return {"sourceIdentity": identity(source), "objectIdentity": identity(target / "manager_management_facts.o"),
            "commandIdentity": identity(target / ".manager_management_facts.o.cmd"), "command": argv,
            "linkedIntoRuntime": False}


def build_retirement_support(target):
    policy = core_cfi_policy(target / ".manager_host_runtime_types.o.cmd")
    if not policy["kcfiEnabled"]:
        raise ValueError("Retirement support requires the actual target KCFI callback contract")
    report = build_support(target, retirement_policy=policy, bootstrap_only=True)
    report["bootstrap"]["nativeCfiPolicy"] = policy
    report["bootstrap"]["nativeCommandIdentity"] = identity(target / ".manager_host_runtime_types.o.cmd")
    (target / "retirement-support.json").write_text(json.dumps(report, indent=2) + "\n", newline="\n")
    return report


def build(kernel, out):
    if HOST_INITIALIZATION_SELECTION is not None and kernel != HOST_INITIALIZATION_SELECTION["kernelProfile"] and \
            not runtime_profiles_enabled():
        raise ValueError("HOST target profile is selected only for its actual kernel source generation")
    target = out / kernel
    target.mkdir(parents=True, exist_ok=True)
    # No object or Kbuild command from an older source selection can qualify this build.
    for generated in [*target.glob("*.o"), *target.glob(".*.o.cmd"), target / "claim.so"]:
        generated.unlink(missing_ok=True)
    (target / "kbuild-working-directory.txt").unlink(missing_ok=True)
    config = target / "msu_resolver_build_config.json"
    config.unlink(missing_ok=True)
    inputs = []
    gate_binding = publication_gate_binding()
    for source in [*SOURCES, *PUBLICATION_PRIVATE_HEADERS, PROTOCOL, FRAME, *su_source_paths()]:
        copied = (Path("native-text") / source.name if source.parent.name == "native-text"
                  else Path(source.name))
        (target / copied).parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target / copied)
        selected = identity(source)
        if sha(target / copied) != selected["sha256"]:
            raise ValueError("Source changed while copying build input: " + str(source))
        inputs.append({"sourceIdentity": selected, "copy": copied.as_posix()})
    if RUNTIME_LAYOUT_SELECTION is not None:
        # Preserve the SDK TU's actual ../../../payload quoted include without
        # modifying source bytes or writing outside this fresh build directory.
        include_root = Path("project/runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/kernel")
        (target / include_root).mkdir(parents=True, exist_ok=True)
        receipt_copy = Path("project/runtime/maplesu-sdk/sdk/src/payload/su/su_session_receipt.h")
        (target / receipt_copy).parent.mkdir(parents=True, exist_ok=True)
        receipt_source = selected_native_source("su_session_receipt.h")
        selected = identity(receipt_source)
        shutil.copyfile(receipt_source, target / receipt_copy)
        if sha(target / receipt_copy) != selected["sha256"]:
            raise ValueError("SDK relative include changed while copying its selected input")
        inputs.append({"sourceIdentity": selected, "copy": receipt_copy.as_posix()})
    (target / "build-inputs.json").write_text(json.dumps({
        "schemaVersion": 1, "generation": uuid.uuid4().hex, "sourceFrozen": False,
        "inputs": inputs, "buildTools": [identity(path) for path in BUILD_TOOLS],
        "publicationGateBinding": gate_binding,
        "upstreamSourceFreeze": upstream_source_freeze(), "suSourceSelection": SU_SELECTION,
        "sourceCorrections": SOURCE_CORRECTIONS,
        "suProfileMetadataSelection": SU_COMPONENT_METADATA_SELECTION,
        "coreRuntimeSourceSelection": core_runtime_public_selection(),
        "hostDurableSourceSelection": HOST_DURABLE_SELECTION,
        "hostInitializationSourceSelection": HOST_INITIALIZATION_SELECTION,
        "sdkCurrentTaskSourceSelection": CORE_CURRENT_TASK_SELECTION,
        "hostPublicationSourceSelection": HOST_PUBLICATION_SELECTION,
        "hostSyncSourceSelection": HOST_SYNC_SELECTION,
        "hostRetirementSourceSelection": HOST_RETIREMENT_SELECTION,
        "coreSourceSelection": core_public_selection() if CORE_SELECTION else None,
        "taskProfileEnabled": task_profile_sources() is not None,
        "taskProfileSourceSelection": TASK_PROFILE_SELECTION,
        "runtimeLayoutSourceSelection": RUNTIME_LAYOUT_SELECTION,
        "compiledKernelFamily": kernel,
        "runtimeTargetProfileGenerated": runtime_profiles_enabled(),
        "runtimeAdditionalObjects": list(RUNTIME_ADDITIONAL_OBJECTS) if RUNTIME_LAYOUT_SELECTION is not None else [],
        "hostDeploymentSourceSelection": host_deployment_public_selection() if HOST_DEPLOYMENT_SELECTION is not None else None,
    }, indent=2) + "\n", encoding="utf-8", newline="\n")
    flags = FLAGS
    (target / "Makefile").write_text(
        f"$(file >/output/{kernel}/kbuild-working-directory.txt,$(CURDIR))\n"
        "obj-m += msu_manager_claim.o\n"
        "targets += manager_management_facts.o manager_uid_facts.o\n"
        "msu_manager_claim-y := install_records.o protected_install_kernel.o "
        "manager_apk_identity.o manager_claim_entry.o manager_owner.o manager_owner_watch.o "
        f"manager_resident_host.o manager_management.o manager_uid_gateway.o manager_uid_snapshot.o manager_bytes.o{' su_install_registry.o' if SU_SELECTION else ''}\n"
        f"ccflags-y += {flags}\n"
        f"ccflags-y += -include /output/{kernel}/msu_resolver_build_config.h "
        f"-DMSU_MANAGEMENT_PUBLICATION_GATE_VERIFIED={int(gate_binding is not None)}\n"
        "CFLAGS_manager_bytes.o += -ffreestanding -fno-builtin\n"
        "CFLAGS_manager_management.o += -g -mgeneral-regs-only\n"
        "CFLAGS_manager_management_facts.o += -g\n"
        "CFLAGS_manager_resident_host.o += -g\n"
        "CFLAGS_manager_uid_gateway.o += -g\n"
        "CFLAGS_manager_uid_snapshot.o += -g\n"
        "CFLAGS_manager_uid_facts.o += -g\n"
        "ifneq ($(filter -fsanitize=kcfi,$(KBUILD_CFLAGS)),)\n"
        "ccflags-y += -fno-sanitize=kcfi\n"
        "CFLAGS_manager_owner_watch.o += -fsanitize=kcfi\n"
        "CFLAGS_manager_management.o += -fsanitize=kcfi\n"
        "CFLAGS_manager_resident_host.o += -fsanitize=kcfi\n"
        "CFLAGS_manager_uid_gateway.o += -fsanitize=kcfi\n"
        "CFLAGS_manager_uid_snapshot.o += -fsanitize=kcfi\n"
        "CFLAGS_manager_callback_types.o += -fsanitize=kcfi\n"
        "msu_manager_claim-y += manager_callback_types.o\n"
        "endif\n", newline="\n")
    if RUNTIME_LAYOUT_SELECTION is not None:
        with (target / "Makefile").open("a", encoding="utf-8", newline="\n") as makefile:
            makefile.write("msu_manager_claim-y += manager_dynamic_layout.o\n"
                           "CFLAGS_manager_dynamic_layout.o += -g\n"
                           "CFLAGS_manager_resident_host.o += -DMSU_RAW_RESIDENT_LOOKUP=1\n"
                            "CFLAGS_boot_events.o += -DMSU_BOOT_EVENTS_RAW_RESIDENT=1\n"
                           "CFLAGS_su_nonroot_transition_work.o += -DMSU_SU_NONROOT_WORK_RAW_RESIDENT=1\n"
                           "CFLAGS_su_exec_session.o += -DMSU_SU_EXEC_SESSION_RAW_RESIDENT=1\n"
                           "CFLAGS_manager_ksu_compat.o += -DMSU_KSU_COMPAT_RAW_RESIDENT=1\n"
                           f"CFLAGS_su_exec_session.o += -I/output/{kernel}/project/runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/kernel\n")
            makefile.write("msu_manager_claim-y += " + " ".join(name + ".o" for name in RUNTIME_ADDITIONAL_OBJECTS) + "\n")
            for name in RUNTIME_ADDITIONAL_OBJECTS:
                makefile.write("CFLAGS_" + name + ".o += -g\n")
            makefile.write("ifneq ($(filter -fsanitize=kcfi,$(KBUILD_CFLAGS)),)\n")
            for name in RUNTIME_ADDITIONAL_OBJECTS:
                makefile.write("CFLAGS_" + name + ".o += -fsanitize=kcfi\n")
            makefile.write("endif\n")
    if HOST_DEPLOYMENT_SELECTION is not None and HOST_DEPLOYMENT_SELECTION.get("runtimeChannel"):
        with (target / "Makefile").open("a", encoding="utf-8", newline="\n") as makefile:
            makefile.write("targets += manager_host_runtime_types.o\n")
            makefile.write("msu_manager_claim-y += " + " ".join(v + ".o" for v in HOST_RUNTIME_OBJECTS) + "\n")
            for name in HOST_RUNTIME_OBJECTS:
                makefile.write("CFLAGS_" + name + ".o += -g\n")
            makefile.write("CFLAGS_manager_host_runtime_types.o += -g\n")
            makefile.write("ifneq ($(filter -fsanitize=kcfi,$(KBUILD_CFLAGS)),)\n")
            for name in HOST_RUNTIME_OBJECTS:
                makefile.write("CFLAGS_" + name + ".o += -fsanitize=kcfi\n")
            makefile.write("CFLAGS_manager_host_runtime_types.o += -fsanitize=kcfi\n")
            makefile.write("endif\n")
        (target / "manager_host_runtime_types.c").write_text(host_runtime_probe_source(), encoding="utf-8", newline="\n")
    if CORE_SELECTION is not None:
        with (target / "Makefile").open("a", encoding="utf-8", newline="\n") as makefile:
            makefile.write("targets += " + " ".join(name + ".o" for name in CORE_PROBES) + "\n")
            makefile.write("msu_manager_claim-y += manager_task_state.o manager_credential_domain.o\n"
                "ccflags-y += -DMSU_MANAGEMENT_TASK_STATE_VERIFIED=0 -DMSU_MANAGEMENT_PROFILE_DOMAIN_VERIFIED=0\n")
            for name in ("manager_task_state", "manager_credential_domain", *CORE_PROBES):
                makefile.write("CFLAGS_" + name + ".o += -g\n")
            makefile.write("ifneq ($(filter -fsanitize=kcfi,$(KBUILD_CFLAGS)),)\n")
            for name in ("manager_task_state", "manager_credential_domain", *CORE_PROBES):
                makefile.write("CFLAGS_" + name + ".o += -fsanitize=kcfi\n")
            makefile.write("endif\n")
        # Private C/BTF/declaration probes never enter the resident payload.
        for name in CORE_PROBES[:2]:
            (target / (name + ".c")).write_text(core_probe_source(name), encoding="utf-8", newline="\n")
        (target / "manager_core_callback_types.c").write_text(core_callback_type_source(),
            encoding="utf-8", newline="\n")
    if task_profile_sources() is not None:
        with (target / "Makefile").open("a", encoding="utf-8", newline="\n") as makefile:
            makefile.write("targets += " + TASK_PROFILE_PROBE + ".o\n")
            makefile.write("CFLAGS_manager_task_state.o += -DMSU_TASK_PROFILE_FLAGS_VERIFIED=0\n")
            makefile.write("CFLAGS_manager_task_profile_types.o += -g\n")
            makefile.write("ifneq ($(filter -fsanitize=kcfi,$(KBUILD_CFLAGS)),)\n")
            makefile.write("CFLAGS_manager_task_profile_types.o += -fsanitize=kcfi\nendif\n")
        (target / (TASK_PROFILE_PROBE + ".c")).write_text(task_profile_type_source(), encoding="utf-8", newline="\n")
    if SU_COMPONENT_SELECTION is not None:
        with (target / "Makefile").open("a", encoding="utf-8", newline="\n") as makefile:
            makefile.write("targets += " + " ".join(name + ".o" for name in SU_COMPONENT_PROBES) + "\n")
            makefile.write("msu_manager_claim-y += " + " ".join(name + ".o" for name in SU_COMPONENT_OBJECTS[1:]) + "\n")
            makefile.write("ccflags-y += -DMSU_SU_NAMESPACE_NATIVE_VERIFIED=0\n")
            for name in (*SU_COMPONENT_OBJECTS, *SU_COMPONENT_PROBES):
                makefile.write("CFLAGS_" + name + ".o += -g\n")
            makefile.write("ifneq ($(filter -fsanitize=kcfi,$(KBUILD_CFLAGS)),)\n")
            for name in (*SU_COMPONENT_OBJECTS, *SU_COMPONENT_PROBES):
                makefile.write("CFLAGS_" + name + ".o += -fsanitize=kcfi\n")
            makefile.write("endif\n")
        for name in SU_COMPONENT_PROBES:
            (target / (name + ".c")).write_text(su_component_probe(name), encoding="utf-8", newline="\n")
    elif SU_SELECTION is not None:
        with (target / "Makefile").open("a", encoding="utf-8", newline="\n") as makefile:
            makefile.write("targets += manager_su_facts.o manager_su_callback_types.o\n")
            makefile.write("CFLAGS_su_install_registry.o += -g\n"
                "CFLAGS_manager_su_facts.o += -g\n"
                "CFLAGS_manager_su_callback_types.o += -g\n"
                "ifneq ($(filter -fsanitize=kcfi,$(KBUILD_CFLAGS)),)\n"
                "CFLAGS_su_install_registry.o += -fsanitize=kcfi\n"
                "CFLAGS_manager_su_facts.o += -fsanitize=kcfi\n"
                "CFLAGS_manager_su_callback_types.o += -fsanitize=kcfi\nendif\n")
        # Both probes remain unlinked; only the real registry object enters the claim.
        (target / "manager_su_facts.c").write_text(
            '#include "su_install_registry.c"\n' +
            "".join("static struct " + structure + " *msu_build_su_" + prefix + " __used;\n"
                    for prefix, (structure, _) in SU_LAYOUT_TYPES.items()) +
            "const unsigned int msu_su_build_facts[] = {\n" +
            "".join("    " + expression + ",\n" for expression in SU_FACTS.values()) + "};\n",
            encoding="utf-8", newline="\n")
        (target / "manager_su_callback_types.c").write_text(SU_CALLBACK_TYPE_SOURCE,
            encoding="utf-8", newline="\n")
    (target / "manager_callback_types.c").write_text(
        "#include <linux/compiler.h>\n#include <linux/fsnotify_backend.h>\n"
        "#include <linux/binfmts.h>\n#include <linux/sched.h>\n#include <linux/workqueue.h>\n#include <linux/rcupdate.h>\n"
        "extern typeof(*((struct fsnotify_ops *)0)->handle_inode_event) msu_owner_inode_event;\n"
        "extern typeof(*((struct fsnotify_ops *)0)->freeing_mark) msu_owner_freeing_mark;\n"
        "extern typeof(*((struct fsnotify_ops *)0)->free_mark) msu_owner_free_mark;\n"
        "extern void msu_management_process_exit(void *, struct task_struct *);\n"
        "extern void msu_management_process_exit_group_dead(void *, struct task_struct *, bool);\n"
        "extern void msu_management_process_exec(void *, struct task_struct *, pid_t, struct linux_binprm *);\n"
        "extern void msu_management_cleanup(struct work_struct *);\n"
        "extern void msu_publication_cleanup(struct work_struct *);\n"
        "extern int msu_management_validate_credentials(void *);\n"
        "extern void msu_uid_snapshot_reclaim_rcu(struct rcu_head *);\n"
        "extern void msu_uid_snapshot_reclaim_work(struct work_struct *);\n"
        "_Static_assert(__builtin_types_compatible_p(typeof(&msu_uid_snapshot_reclaim_rcu), rcu_callback_t), \"snapshot RCU callback\");\n"
        "_Static_assert(__builtin_types_compatible_p(typeof(&msu_uid_snapshot_reclaim_work), work_func_t), \"snapshot work callback\");\n"
        "__ADDRESSABLE(msu_owner_inode_event);\n"
        "__ADDRESSABLE(msu_owner_freeing_mark);\n"
        "__ADDRESSABLE(msu_owner_free_mark);\n"
        "__ADDRESSABLE(msu_management_process_exit);\n"
        "__ADDRESSABLE(msu_management_process_exit_group_dead);\n"
        "__ADDRESSABLE(msu_management_process_exec);\n"
        "__ADDRESSABLE(msu_management_cleanup);\n"
        "__ADDRESSABLE(msu_publication_cleanup);\n"
        "__ADDRESSABLE(msu_management_validate_credentials);\n"
        "__ADDRESSABLE(msu_uid_snapshot_reclaim_rcu);\n"
        "__ADDRESSABLE(msu_uid_snapshot_reclaim_work);\n" + PROCATTR_CALLBACK_TYPE_SOURCE, newline="\n")
    (target / "manager_management_facts.c").write_text(MANAGEMENT_FACT_SOURCE +
        "\nconst unsigned int msu_management_wire_facts[] = {\n" +
        "".join("    " + expression + ",\n" for expression, _ in WIRE_FACTS.values()) + "};\n", newline="\n")
    # The unlinked probe compiles exact private definitions, never copied layouts.
    (target / "manager_uid_facts.c").write_text(
        '#include "manager_uid_snapshot.c"\n#include "manager_uid_gateway.c"\n'
        'static typeof(&call_rcu) msu_build_uid_call_rcu __used = call_rcu;\n'
        'static rcu_callback_t msu_build_uid_rcu_type __used;\n'
        'static work_func_t msu_build_uid_work_type __used;\n'
        "const unsigned int msu_uid_build_facts[] = {\n" +
        "".join("    " + expression + ",\n" for expression in UID_FACTS.values()) + "};\n", newline="\n")
    (target / "capture-dependencies.py").write_text(DEPENDENCY_EXTRACTOR, newline="\n")
    (target / "exports.map").write_text(
        "{ global: " + "; ".join(EXPORTS) + "; local: *; };\n", newline="\n")
    make, mounts, image, fs_header, vmlinux = kernel_environment(kernel, out)
    if RUNTIME_LAYOUT_SELECTION is not None:
        private_root = fs_header.rsplit("/include/linux/", 1)[0]
        with (target / "Makefile").open("a", encoding="utf-8", newline="\n") as makefile:
            for name in ("manager_dynamic_layout", "su_nonroot_mount_source"):
                makefile.write("CFLAGS_" + name + ".o += -I" + private_root + "\n")
    kernel_config = vmlinux.rsplit("/", 1)[0] + "/.config"
    (target / "configure-layout.py").write_text(r'''from pathlib import Path
import hashlib
import json
import re
import runpy
import sys

header = Path(sys.argv[1]).read_bytes()
text = re.sub(r"/\*.*?\*/|//[^\n]*", "", header.decode("utf-8"), flags=re.S)
declaration = re.search(r"\bstruct\s+fs_struct\s*\{([^}]*)\}", text, flags=re.S)
if not declaration:
    raise RuntimeError("fs_struct declaration missing from actual kernel headers")
body = declaration.group(1)
has_lock = bool(re.search(r"\bspinlock_t\s+lock\s*;", body))
if bool(re.search(r"\block\b", body)) != has_lock:
    raise RuntimeError("unsupported fs_struct.lock declaration")
seq = re.findall(r"\b([A-Za-z_]\w*)\s+seq\s*;", body)
if len(seq) != 1 or seq[0] not in ("seqlock_t", "seqcount_t", "seqcount_spinlock_t"):
    raise RuntimeError("unsupported fs_struct.seq declaration")
macros = {"MSU_RESOLVER_FS_HAS_LOCK": int(has_lock),
          "MSU_RESOLVER_FS_SEQLOCK": int(seq[0] == "seqlock_t")}
execmem_header = Path(sys.argv[1]).parent / "execmem.h"
execmem = execmem_header.read_text() if execmem_header.is_file() else ""
execmem = re.sub(r"/\*.*?\*/|//[^\n]*", "", execmem, flags=re.S)
macros["MSU_MANAGEMENT_EXECMEM_ALLOC_RW"] = int(bool(re.search(r"\bexecmem_alloc_rw\s*\(", execmem)))
cred_header = Path(sys.argv[1]).parent / "cred.h"
cred_bytes = cred_header.read_bytes()
cred = re.sub(r"/\*.*?\*/|//[^\n]*", "", cred_bytes.decode("utf-8"), flags=re.S)
cred_start = re.search(r"\bstruct\s+cred\s*\{", cred)
if not cred_start:
    raise RuntimeError("cred declaration missing from actual kernel headers")
level, end = 1, cred_start.end()
while level and end < len(cred):
    level += (cred[end] == "{") - (cred[end] == "}")
    end += 1
if level:
    raise RuntimeError("unterminated actual cred declaration")
cred_body = cred[cred_start.end():end - 1]
ucounts = re.findall(r"\bstruct\s+ucounts\s*\*\s*ucounts\s*;", cred_body)
helper = re.findall(r"\bextern\s+int\s+set_cred_ucounts\s*\(\s*struct\s+cred\s*\*\s*(?:[A-Za-z_]\w*)?\s*\)\s*;", cred)
if len(ucounts) > 1 or len(helper) > 1 or bool(re.search(r"\bucounts\b", cred_body)) != bool(ucounts) or \
        bool(re.search(r"\bset_cred_ucounts\b", cred)) != bool(helper) or bool(ucounts) != bool(helper):
    raise RuntimeError("unsupported actual cred ucounts field/helper contract")
macros["MSU_MANAGEMENT_CRED_UCOUNTS"] = int(bool(ucounts))
kernel_root = Path(sys.argv[1]).parents[2]
native_header = kernel_root / "include/linux/kprobe_text_owner.h"
native_text = {"complete": False, "reason": "Selected kernel has no native text-owner producer"}
if native_header.exists():
    verifier = runpy.run_path("/source/scripts/apply-ultra-kprobe-text-owner.py")
    native_text = verifier["verify_installed"](kernel_root)
    if not native_text.get("complete"):
        raise RuntimeError("Selected native text-owner producer is incomplete")
macros["MSU_MANAGEMENT_NATIVE_TEXT_OWNER"] = int(native_text["complete"])
# Qualify the actual public task-release body, including vendor backports.
seccomp_config = Path(sys.argv[3]).read_bytes()
seccomp_enabled = b"\nCONFIG_SECCOMP_FILTER=y\n" in b"\n" + seccomp_config + b"\n"
seccomp_header = kernel_root / "include/linux/seccomp.h"
seccomp_header_bytes = seccomp_header.read_bytes()
seccomp_api = {"schema": "maplesu-seccomp-release-body/1",
    "symbol": "seccomp_filter_release", "enabled": seccomp_enabled,
    "locksTask": False, "requiresPfExiting": False,
    "header": {"path": str(seccomp_header), "bytes": len(seccomp_header_bytes),
        "sha256": hashlib.sha256(seccomp_header_bytes).hexdigest()},
    "config": {"path": sys.argv[3], "bytes": len(seccomp_config),
        "sha256": hashlib.sha256(seccomp_config).hexdigest()}}
if seccomp_enabled:
    seccomp_source = kernel_root / "kernel/seccomp.c"
    if not seccomp_source.is_file():
        # Sparse module source trees bind a separately preserved blob from their
        # exact source commit. This never selects an operation by kernel version.
        seccomp_source = Path(sys.argv[4])
        saved = json.loads(Path(sys.argv[5]).read_text(encoding="utf-8"))
        source_identity = kernel_root / "source-identity.json"
        declared = json.loads(source_identity.read_text(encoding="utf-8"))
        if saved["commit"] != declared["commit"] or hashlib.sha256(seccomp_source.read_bytes()).hexdigest() != saved["sha256"]:
            raise RuntimeError("Seccomp fallback source does not bind the actual sparse source commit")
        seccomp_api["sourceCommit"] = saved["commit"]
        seccomp_api["sourceBlob"] = saved["blob"]
    source_bytes = seccomp_source.read_bytes()
    clean = re.sub(r"/\*.*?\*/|//[^\n]*", "", source_bytes.decode("utf-8"), flags=re.S)
    declarations = list(re.finditer(r"\bvoid\s+seccomp_filter_release\s*\(\s*struct\s+task_struct\s*\*\s*tsk\s*\)\s*\{", clean))
    if len(declarations) != 1:
        raise RuntimeError("Actual seccomp task-release implementation is missing/ambiguous")
    begin, at, depth = declarations[0].end(), declarations[0].end(), 1
    while depth and at < len(clean):
        depth += (clean[at] == "{") - (clean[at] == "}")
        at += 1
    if depth:
        raise RuntimeError("Actual seccomp task-release implementation has incomplete bounds")
    body = clean[begin:at-1]
    compact = re.sub(r"\s+", "", body)
    locking = "spin_lock_irq(&tsk->sighand->siglock);" in compact
    unlocking = "spin_unlock_irq(&tsk->sighand->siglock);" in compact
    exiting = "WARN_ON((tsk->flags&PF_EXITING)==0)" in compact
    fields = set(re.findall(r"\btsk\s*->\s*(\w+)", body))
    seccomp_fields = set(re.findall(r"\btsk\s*->\s*seccomp\s*\.\s*(\w+)", body))
    if locking != unlocking or (locking and not exiting) or fields - {"flags","sighand","seccomp"} or seccomp_fields != {"filter"}:
        raise RuntimeError("Actual seccomp release task fields/lock/exit contract is unsupported")
    if "sighand" in fields and not locking and "WARN_ON(tsk->sighand!=NULL);" not in compact:
        raise RuntimeError("Actual seccomp release sighand contract is unsupported")
    if "orig=tsk->seccomp.filter;" not in compact or "tsk->seccomp.filter=NULL;" not in compact or "__seccomp_filter_release(orig);" not in compact:
        raise RuntimeError("Actual seccomp release detach/native-reference contract differs")
    seccomp_api.update(locksTask=locking, requiresPfExiting=exiting,
        taskFields=sorted(fields), seccompFields=sorted(seccomp_fields),
        bodySha256=hashlib.sha256(body.encode("utf-8")).hexdigest(),
        source={"path": str(seccomp_source), "bytes": len(source_bytes),
            "sha256": hashlib.sha256(source_bytes).hexdigest()},
        compiledSourceArchive="seccomp-release-source.c",
        compiledHeaderArchive="seccomp-release-header.h")
    Path(sys.argv[2]).with_name("seccomp-release-source.c").write_bytes(source_bytes)
    Path(sys.argv[2]).with_name("seccomp-release-header.h").write_bytes(seccomp_header_bytes)
macros["MSU_TASK_SECCOMP_RELEASE_LOCKS_TASK"] = int(seccomp_api["locksTask"])

destination = Path(sys.argv[2])
destination.write_text("".join("#define %s %d\n" % row for row in macros.items()), newline="\n")
config = Path(sys.argv[3]).read_bytes()
cfi_names = ("CONFIG_CFI_CLANG", "CONFIG_CFI", "CONFIG_CFI_PERMISSIVE",
             "CONFIG_CFI_ICALL_NORMALIZE_INTEGERS", "CONFIG_LTO_CLANG", "CONFIG_LTO_NONE")
cfi_config = {name: "n" for name in cfi_names}
management_names = (
    "SMP", "PREEMPT_RT", "PREEMPT_COUNT", "THREAD_INFO_IN_TASK", "DEBUG_SPINLOCK",
    "DEBUG_LOCK_ALLOC", "LOCKDEP", "RWSEM_SPIN_ON_OWNER", "DEBUG_RWSEMS", "TRACEPOINTS",
    "PREEMPTION", "PREEMPT", "PREEMPT_DYNAMIC", "PROVE_LOCKING", "PROVE_RCU", "DEBUG_PREEMPT",
    "LOCK_STAT", "CFI", "CFI_CLANG", "CFI_ICALL_NORMALIZE_INTEGERS", "MODULES", "EXECMEM",
    "ARCH_HAS_EXECMEM_ROX", "MULTIUSER", "HOTPLUG_CPU", "DEBUG_OBJECTS_WORK", "DEBUG_OBJECTS",
    "DEBUG_CREDENTIALS", "REFCOUNT_FULL", "GENERIC_LOCKBREAK", "PREEMPT_RCU", "TREE_RCU",
    "TINY_RCU", "DEBUG_ATOMIC_SLEEP")
management_config = {"CONFIG_" + name: "n" for name in management_names}
for line in config.decode("utf-8").splitlines():
    if line.partition("=")[0] in cfi_config:
        name, value = line.split("=", 1)
        cfi_config[name] = value
    if line.startswith("CONFIG_") and "=" in line:
        name, value = line.split("=", 1)
        management_config[name] = value
    elif line.startswith("# CONFIG_") and line.endswith(" is not set"):
        management_config[line[2:-11]] = "n"
destination.with_suffix(".json").write_text(json.dumps({
    "source": sys.argv[1], "sourceSha256": hashlib.sha256(header).hexdigest(),
    "macros": macros, "sha256": hashlib.sha256(destination.read_bytes()).hexdigest(),
    "kernelConfigPath": sys.argv[3], "kernelConfigSha256": hashlib.sha256(config).hexdigest(),
    "cfiConfig": cfi_config,
    "managementConfig": management_config,
    "nativeTextOwner": native_text,
    "seccompReleaseApi": seccomp_api,
    "execmemHeader": str(execmem_header),
    "execmemHeaderSha256": hashlib.sha256(execmem_header.read_bytes()).hexdigest() if execmem_header.is_file() else None,
    "credHeader": str(cred_header), "credHeaderSha256": hashlib.sha256(cred_bytes).hexdigest(),
}, indent=2) + "\n", newline="\n")
''', newline="\n")
    core_build_steps = ""
    if CORE_SELECTION is not None:
        core_build_steps = f"{make} -j2 " + " ".join(name + ".o" for name in CORE_PROBES) + "\n" + \
            "".join("pahole -J " + ("--btf_features_strict=var,global_var "
                    if name in CORE_PROBES or name == "manager_task_state" and task_profile_trace_sources() is not None else "") +
                    f"/output/{kernel}/{name}.o\n" for name in
                    ("manager_task_state", "manager_credential_domain", *CORE_PROBES,
                     *(HOST_RUNTIME_OBJECTS if HOST_DEPLOYMENT_SELECTION is not None and HOST_DEPLOYMENT_SELECTION.get("runtimeChannel") else ())))
    if task_profile_sources() is not None:
        core_build_steps += f"{make} -j2 {TASK_PROFILE_PROBE}.o\n" + f"pahole -J --btf_features=default,global_var /output/{kernel}/{TASK_PROFILE_PROBE}.o\n"
    if RUNTIME_LAYOUT_SELECTION is not None:
        core_build_steps += f"pahole -J /output/{kernel}/manager_dynamic_layout.o\n"
        core_build_steps += "".join(f"pahole -J /output/{kernel}/{name}.o\n" for name in RUNTIME_ADDITIONAL_OBJECTS)
        if runtime_profiles_enabled():
            core_build_steps += f"pahole -J --btf_features_strict=var,global_var /output/{kernel}/manager_su_runtime_target.o\n"
    if HOST_DEPLOYMENT_SELECTION is not None and HOST_DEPLOYMENT_SELECTION.get("runtimeChannel"):
        core_build_steps += f"{make} -j2 manager_host_runtime_types.o\n" + \
            f"pahole -J --btf_features_strict=var,global_var /output/{kernel}/manager_host_runtime_types.o\n"
    su_build_steps = ""
    if SU_COMPONENT_SELECTION is not None:
        su_build_steps = f"{make} -j2 " + " ".join(name + ".o" for name in SU_COMPONENT_PROBES) + "\n" + \
            "".join("pahole -J " + ("--btf_features_strict=var,global_var "
                if name in SU_COMPONENT_PROBES else "") +
                f"/output/{kernel}/{name}.o\n" for name in (*SU_COMPONENT_OBJECTS, *SU_COMPONENT_PROBES))
    elif SU_SELECTION is not None:
        su_build_steps = f"{make} -j2 manager_su_facts.o manager_su_callback_types.o\n" + \
            f"pahole -J /output/{kernel}/su_install_registry.o\n" + \
            f"pahole -J /output/{kernel}/manager_su_facts.o\n" + \
            f"pahole -J /output/{kernel}/manager_su_callback_types.o\n"
    compiler_environment = ("export LD_LIBRARY_PATH=/kernel-toolchain/lib/x86_64-linux-gnu:/kernel-toolchain/lib/llvm-22/lib\n"
        if kernel == "android17-6.18" and KERNEL_618_TOOLCHAIN is not None else "")
    script = f"""set -eu
{compiler_environment}python3 /output/{kernel}/configure-layout.py {fs_header} /output/{kernel}/msu_resolver_build_config.h {kernel_config} /source/native-inputs/policy/android17-6.18/source/seccomp.c /source/native-inputs/policy/android17-6.18/source/source-receipt.json
{make} -j2 msu_manager_claim.o
{make} -j2 manager_management_facts.o manager_uid_facts.o
{core_build_steps}{su_build_steps}pahole -J /output/{kernel}/manager_management.o
pahole -J --btf_features_strict=var,global_var /output/{kernel}/manager_management_facts.o
pahole -J --btf_features_strict=var,global_var /output/{kernel}/manager_resident_host.o
llvm-objcopy --decompress-debug-sections /output/{kernel}/manager_resident_host.o /output/{kernel}/manager_resident_host.debug.o
pahole -J /output/{kernel}/manager_uid_snapshot.o
pahole -J /output/{kernel}/manager_uid_gateway.o
pahole -J --btf_features_strict=var,global_var /output/{kernel}/manager_uid_facts.o
{make} -j2 msu_manager_claim.o
python3 /source/scripts/policy_object_debug.py /output/{kernel}
python3 /output/{kernel}/capture-dependencies.py /output/{kernel} {vmlinux.rsplit('/', 1)[0]}
ld.lld -shared -m aarch64elf --gc-sections --entry=msu_manager_claim_entry --strip-debug -Bsymbolic -z max-page-size=65536 -z force-bti --version-script=/output/{kernel}/exports.map -o /output/{kernel}/claim.full.so --whole-archive /output/{kernel}/msu_manager_claim.o --no-whole-archive
llvm-objcopy --remove-section=.BTF /output/{kernel}/claim.full.so /output/{kernel}/claim.so
llvm-objdump -d /output/{kernel}/claim.so > /output/{kernel}/claim.disasm
llvm-size -A /output/{kernel}/claim.so
llvm-nm -u /output/{kernel}/claim.so
"""
    (target / "build.sh").write_text(script, newline="\n")
    name = "maplesu-claim-" + kernel + "-" + uuid.uuid4().hex[:6]
    command = ["docker", "run", "--rm", "--pull", "never", "--network", "none",
               "--name", name, "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
               *mounts, f"ghcr.io/ylarod/ddk-min:{image}-20260828",
               "sh", f"/output/{kernel}/build.sh"]
    start = time.monotonic()
    timed_out, cleanup_error = False, None
    try:
        result = subprocess.run(command, capture_output=True, text=True,
                                encoding="utf-8", errors="replace", timeout=600)
    except subprocess.TimeoutExpired as error:
        timed_out = True
        stdout, stderr = error.stdout or "", error.stderr or ""
        result = subprocess.CompletedProcess(command, 124,
            stdout.decode("utf-8", errors="replace") if isinstance(stdout, bytes) else stdout,
            stderr.decode("utf-8", errors="replace") if isinstance(stderr, bytes) else stderr)
        try:
            cleanup = subprocess.run(["docker", "rm", "-f", name], capture_output=True, timeout=20)
            if cleanup.returncode:
                cleanup_error = cleanup.stderr.decode("utf-8", errors="replace")
        except (OSError, subprocess.TimeoutExpired) as failure:
            cleanup_error = type(failure).__name__ + ": " + str(failure)
    (target / "stdout.log").write_text(result.stdout, newline="\n")
    (target / "stderr.log").write_text(result.stderr, newline="\n")
    row = {"kernel": kernel, "exitCode": result.returncode,
           "elapsedSeconds": round(time.monotonic() - start, 3), "command": command,
           "compilerBudgetSeconds": 600, "timedOut": timed_out,
           **({"cleanupError": cleanup_error} if cleanup_error else {}),
           "flags": flags, "callbackTypeProbeSha256": sha(target / "manager_callback_types.c")}
    if config.is_file():
        row["resolverBuildConfig"] = json.loads(config.read_text())
    receipt = target / "build-result.json"
    receipt.write_text(json.dumps(row, indent=2) + "\n", newline="\n")
    if result.returncode == 0:
        try:
            row["runtimePackaging"] = verify_runtime_claim(target / "claim.full.so", target / "claim.so")
            row["payload"] = inspect(target / "claim.so")
            row["referenceDataProbes"] = rebuild_reference_data_probes(kernel, out, row["payload"], command)
            if HOST_RETIREMENT_SELECTION is not None:
                row["retirementSupport"] = build_retirement_support(target)
            row["payload"]["referenceAbi"] = collect_reference_abi(kernel, out)
        except Exception as error:
            row["metadataError"] = type(error).__name__ + ": " + str(error)
    receipt.write_text(json.dumps(row, indent=2) + "\n", newline="\n")
    print(json.dumps({"kernel": kernel, "exitCode": row["exitCode"],
                      "executableBytes": row.get("payload", {}).get("executableBytes")}),
          flush=True)
    return row


SOURCE_CORRECTIONS = None
SOURCE_CORRECTION_PATHS = (
    "runtime/ultra/identity/kernel/manager_management.c",
    "runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/kernel/su_install_registry.c",
    "runtime/ultra/identity/kernel/manager_task_state.c")
# Exact owned runtime source successor over the same CORE/host selection.
HOST_RUNTIME_NATIVE_BASE = ("manager_installation_state.c", "manager_installation_state.h",
    "manager_management.c", "protected_install_kernel.c", "protected_install_kernel.h",
    "install_records.c", "install_records.h", "manager_su_runtime_factory.c", "manager_su_runtime_factory.h",
    "manager_su_gate.c", "manager_su_gate.h", "manager_su_runtime_target.c",
    "su_observed_install_source.c", "su_observed_install_source.h",
    "su_durable_authority.c", "su_durable_authority.h", "su_durable_modern.c", "su_durable_modern.h")
HOST_RUNTIME_OBJECTS = ("manager_installation_state", "manager_su_runtime_factory", "manager_su_gate",
    "manager_su_runtime_target", "su_observed_install_source", "su_durable_authority", "su_durable_modern")
HOST_RUNTIME_EXTRA_FUNCTIONS = {"manager_installation_state": (
    "msu_installation_init", "msu_installation_bind_lifecycle", "msu_installation_unbind_lifecycle",
    "msu_installation_discard_unpublished", "msu_installation_read",
    "msu_installation_management", "msu_installation_vendor", "msu_installation_helper_acquire",
    "msu_installation_helper_validate", "msu_installation_helper_put", "msu_installation_helper_close"),
    "manager_su_runtime_factory": ("msu_su_runtime_init", "msu_su_runtime_post_fs_data",
        "msu_su_runtime_post_fs_data_event", "msu_su_runtime_refresh", "msu_su_runtime_revoke_install",
        "msu_su_runtime_close", "msu_su_runtime_discarded", "msu_su_runtime_status_get",
        "msu_su_runtime_sync", "msu_su_runtime_sync_held", "msu_su_runtime_readback_acquire",
        "msu_su_runtime_readback_record", "msu_su_runtime_readback_put"),
    "manager_su_gate": ("msu_su_gate_build", "msu_su_payload_build", "msu_su_gate_instruction"),
    "manager_su_runtime_target": ("msu_su_runtime_target_hooks",),
    "su_observed_install_source": ("msu_su_observed_create", "msu_su_observed_start",
        "msu_su_observed_refresh", "msu_su_observed_close", "msu_su_observed_match_saved_member",
        "msu_su_observed_sync"),
    "su_durable_authority": ("msu_su_durable_authority_create", "msu_su_durable_authority_load"),
    "su_durable_modern": ("msu_su_modern_authority_list", "msu_su_modern_authority_grant")}
HOST_RUNTIME_NATIVE_FUNCTIONS = ("get_task_exe_file", "vfs_getxattr", "vfs_setxattr", "vfs_fsync")
HOST_RUNTIME_NATIVE_CALLBACKS = {
    "su_observed_install_source": {
        "msu_observed_inode_event": ("fsnotify_ops", "handle_inode_event"),
        "msu_observed_freeing_mark": ("fsnotify_ops", "freeing_mark"),
        "msu_observed_free_mark": ("fsnotify_ops", "free_mark"),
        "msu_observed_scan_work": ("work_struct", "func"),
        "msu_observed_authority_directory": ("dir_context", "actor"),
    },
    "manager_su_runtime_factory": {"msu_su_runtime_start_work": ("work_struct", "func")},
    "su_durable_modern": {"msu_su_authority_directory_actor": ("dir_context", "actor")},
}
HOST_RUNTIME_LAYOUT_TYPES = {
    "authority": ("msu_su_observed_authority", ("owner", "read_grant", "put_owner", "list_uids", "directory", "filename")),
    "hook": ("msu_su_runtime_hook", ("entry", "kind", "argument_register", "result_register", "filename_member", "fd_register", "flags_register", "syscall_frame", "optional")),
    "frame": ("msu_su_gate_frame", ("x", "nzcv")),
    "status": ("msu_su_runtime_status", ("schema", "bytes", "host", "installation_generation",
        "runtime_generation", "installation_fence", "flags", "users", "producers", "hook_count",
        "status", "target_error", "cleanup_error", "start_error", "target_qualified", "installation_flags")),
    "fsnotify": ("fsnotify_ops", ("handle_inode_event", "freeing_mark", "free_mark")),
    "work": ("work_struct", ("func",)),
    "directory": ("dir_context", ("actor",)),
}
HOST_RUNTIME_WIRE_FACTS = {
    "installationStateCommand": ("MAPLESU_REQUEST_INSTALLATION_STATE", 28),
    "vendorStateCommand": ("MAPLESU_REQUEST_VENDOR_STATE", 29),
    "installationReadAction": ("MAPLESU_INSTALLATION_READ", 1),
    "installationCommitAction": ("MAPLESU_INSTALLATION_COMMIT", 2),
    "installationRevokeAction": ("MAPLESU_INSTALLATION_REVOKE", 3),
    "vendorBeginAction": ("MAPLESU_VENDOR_BEGIN", 4),
    "vendorBeforeAction": ("MAPLESU_VENDOR_BEFORE", 5),
    "vendorCompleteAction": ("MAPLESU_VENDOR_COMPLETE", 6),
    "installationRecordBytes": ("sizeof(maplesu_installation_record_v3)", 160),
    "installationRecordAlign": ("__alignof__(maplesu_installation_record_v3)", 8),
    "installationStateBytes": ("sizeof(maplesu_installation_state_v3)", 232),
    "installationStateAlign": ("__alignof__(maplesu_installation_state_v3)", 8),
}
HOST_RUNTIME_WIRE_STRUCTURES = {
    "installationRecord": ("maplesu_installation_record_v3", {"magic": 0, "schema": 4, "bytes": 6,
        "active": 8, "reserved": 12, "generation": 16, "attempt": 32, "boot": 48, "stages": 88,
        "publication_source": 136, "publication_phase": 140, "publication_code": 144,
        "version_published": 152, "publication_reserved": 156}),
    "installationState": ("maplesu_installation_state_v3", {"magic": 0, "schema": 4, "bytes": 6,
        "flags": 8, "reserved": 12, "host": 16, "name": 32, "revision": 48,
        "device": 56, "inode": 64, "record": 72}),
}
for _prefix, (_wire, _fields) in HOST_RUNTIME_WIRE_STRUCTURES.items():
    for _field, _at in _fields.items():
        _key = _prefix + "".join(w.capitalize() for w in _field.split("_")) + "Offset"
        HOST_RUNTIME_WIRE_FACTS[_key] = (f"offsetof({_wire}, {_field})", _at)


def select_host_runtime_metadata(selected):
    global PROTOCOL, EXPORTS
    channel = selected.get("runtimeChannel")
    if channel is None:
        return
    if channel != "authenticated-installation-r02-20261119":
        raise ValueError("Unknown owned host runtime source channel")
    protocol = selected["protocolInput"]
    if protocol["logicalPath"] != "runtime/ultra/authorization/request_protocol.h":
        raise ValueError("Owned runtime protocol path mismatch")
    if identity(PROTOCOL) != protocol["before"]:
        raise ValueError("Owned runtime protocol predecessor mismatch")
    after = host_deployment_receipt(protocol["after"])
    WIRE_FACTS.update(HOST_RUNTIME_WIRE_FACTS)
    PROTOCOL = after
    EXPORTS = tuple(dict.fromkeys((*EXPORTS, "msu_su_runtime_post_fs_data_event",
        "msu_su_runtime_refresh", "msu_su_runtime_sync", "msu_su_observed_refresh")))


def host_runtime_probe_source():
    return ("#include <linux/compiler.h>\n#include <linux/fs.h>\n#include <linux/mm.h>\n"
        "#include <linux/user_namespace.h>\n#include <linux/xattr.h>\n"
        "#include <linux/fsnotify_backend.h>\n#include <linux/workqueue.h>\n"
        "#include <linux/stddef.h>\n#include \"manager_installation_state.h\"\n"
        "#include \"manager_su_runtime_factory.h\"\n#include \"manager_su_gate.h\"\n"
        "#include \"su_durable_authority.h\"\n#include \"su_durable_modern.h\"\n"
        + "".join("__ADDRESSABLE(" + name + ");\n"
            "static typeof(&" + name + ") msu_runtime_decl_" + name + " __used = " + name + ";\n"
            for names in HOST_RUNTIME_EXTRA_FUNCTIONS.values() for name in names)
        + "".join("static struct " + name + " *msu_runtime_" + prefix + " __used;\n"
            for prefix, (name, _) in HOST_RUNTIME_LAYOUT_TYPES.items())
        + ("extern void msu_manager_resident_retire_work(struct work_struct *);\n"
           "__ADDRESSABLE(msu_manager_resident_retire_work);\n"
           if HOST_RETIREMENT_SELECTION is not None else "")
        + "static msu_installation_revoke_fn msu_runtime_revoke __used;\n"
        + "".join("extern typeof(*((struct " + structure + " *)0)->" + member + ") " + name + ";\n"
            "__ADDRESSABLE(" + name + ");\n"
            "static typeof(&" + name + ") msu_runtime_decl_" + name + " __used = " + name + ";\n"
            for callbacks in HOST_RUNTIME_NATIVE_CALLBACKS.values()
            for name, (structure, member) in callbacks.items())
        + "static typeof(&get_task_exe_file) msu_runtime_get_task_exe_file __used = get_task_exe_file;\n"
        + "static typeof(&vfs_getxattr) msu_runtime_vfs_getxattr __used = vfs_getxattr;\n"
        + "static typeof(&vfs_setxattr) msu_runtime_vfs_setxattr __used = vfs_setxattr;\n"
        + "static typeof(&vfs_fsync) msu_runtime_vfs_fsync __used = vfs_fsync;\n"
        + "".join("__ADDRESSABLE(" + name + ");\n" for name in HOST_RUNTIME_NATIVE_FUNCTIONS))


def host_runtime_pending_abi():
    return {"schema": "maplesu-host-runtime-abi/1", "schemaVersion": 1,
        "sourceSelection": host_deployment_public_selection(), "metadataComplete": False,
        "coreRuntimeSourceEvolution": core_runtime_public_selection(),
        "durableSourceEvolution": HOST_DURABLE_SELECTION,
        "suProfileMetadataEvolution": SU_COMPONENT_METADATA_SELECTION,
        "hostInitializationSourceEvolution": HOST_INITIALIZATION_SELECTION,
        "sdkCurrentTaskEvolution": CORE_CURRENT_TASK_SELECTION,
        "hostPublicationEvolution": HOST_PUBLICATION_SELECTION,
        "hostSyncEvolution": HOST_SYNC_SELECTION,
        "hostRetirementEvolution": HOST_RETIREMENT_SELECTION,
        "runtimeLayoutSourceSelection": RUNTIME_LAYOUT_SELECTION,
        "targetQualified": False, "sourceIdentities": [], "objectIdentities": [],
        "commandIdentities": [], "dependencyReceiptIdentities": [], "inlineDependencies": [],
        "localFunctionAbi": {}, "compiledLayouts": {}, "authorityCallbacks": {},
        "nativeFunctionAbi": {}, "entrypoints": {}, "entryCfi": {}, "targetFacts": [],
        "missing": ["Actual selected host runtime objects/reference evidence have not been collected"]}


def selected_native_source(name):
    paths = [path for path in SOURCES if path.name == name]
    if len(paths) != 1:
        raise ValueError("Missing/ambiguous selected native source: " + name)
    return paths[0]


def publication_gate_binding():
    """Bind current native code to the actual SDK emitter, separately from hardware qualification."""
    current = ROOT / "runtime/ultra/identity/kernel/manager_management.c"
    if sha(selected_native_source("manager_management.c")) != sha(current):
        return None
    return {"schema": "maplesu-publication-gate-source-binding/1",
            "nativeSource": identity(current),
            "inputs": [identity(path) for path in PUBLICATION_GATE_INPUTS],
            "publicControlBytes": 64, "privateControlBytes": 72,
            "targetQualified": False, "deviceValidated": False}


def effective_source(row):
    if TASK_PROFILE_SELECTION is not None:
        rows = {item["logicalPath"]: item for item in TASK_PROFILE_SELECTION["files"]}
        if row["logicalPath"] in rows:
            return core_receipt_path(rows[row["logicalPath"]]["after"])
    if RUNTIME_LAYOUT_SELECTION is not None:
        rows = {item["logicalPath"]: item for item in RUNTIME_LAYOUT_SELECTION["files"]}
        if row["logicalPath"] in rows:
            return core_receipt_path(rows[row["logicalPath"]]["after"])
    if SOURCE_CORRECTIONS is not None:
        corrections = {item["logicalPath"]: item for item in SOURCE_CORRECTIONS["files"]}
        if row["logicalPath"] in corrections:
            return core_receipt_path(corrections[row["logicalPath"]]["after"])
    return core_receipt_path(row["snapshotIdentity"])


def select_source_corrections(path, expected_sha256):
    """Preserve validated owner snapshots, then bind the three explicit MAIN successors."""
    global SOURCE_CORRECTIONS, SOURCES
    path = path.resolve()
    if SOURCE_CORRECTIONS is not None or not path.is_relative_to(ROOT.resolve()) or sha(path) != expected_sha256:
        raise ValueError("Source-correction path/identity or single-selection mismatch")
    if CORE_SELECTION is None or SU_COMPONENT_SELECTION is None:
        raise ValueError("Source corrections require the original validated CORE and SU component selections")
    selected = json.loads(path.read_text(encoding="utf-8"))
    successor = selected.get("schema") == "maplesu-main-native-source-corrections/2"
    if not successor and (CORE_RUNTIME_SELECTION is not None or SU_COMPONENT_METADATA_SELECTION is not None):
        raise ValueError("Original MAIN corrections require their unchanged owner sources; select the exact successor after runtime/metadata evolution")
    if selected.get("schema") not in ("maplesu-main-native-source-corrections/1",
            "maplesu-main-native-source-corrections/2") or \
            any(selected.get(key) is not False for key in ("sourceOwnerFrozen", "targetQualified", "factoryBound", "suReady")):
        raise ValueError("Source corrections must preserve independent owner and target qualification status")
    for key, current in (("corePredecessor", CORE_SELECTION["identity"]),
                         ("suComponentPredecessor", SU_COMPONENT_SELECTION["identity"])):
        if core_receipt_path(selected[key]) != Path(current["path"]).resolve() or \
                any(selected[key][field] != current[field] for field in ("bytes", "sha256")):
            raise ValueError("Source corrections do not bind the validated owner selection: " + key)
    rows = {row["logicalPath"]: row for row in selected["files"]}
    if len(rows) != len(selected["files"]) or rows.keys() != set(SOURCE_CORRECTION_PATHS):
        raise ValueError("Source corrections require exactly the three reviewed compatibility paths")
    originals = {row["logicalPath"]: row["snapshotIdentity"]
                 for row in (*CORE_SELECTION["nativeInputs"], *SU_COMPONENT_SELECTION["files"])}
    if successor:
        parent_path = core_receipt_path(selected["parentCorrectionSelection"])
        parent = json.loads(parent_path.read_text(encoding="utf-8"))
        if parent.get("schema") != "maplesu-main-native-source-corrections/1" or \
                any(parent[key] != selected[key] for key in ("corePredecessor", "suComponentPredecessor")) or \
                any(parent.get(key) is not False for key in ("sourceOwnerFrozen", "targetQualified", "factoryBound", "suReady")):
            raise ValueError("Source-correction successor differs from its immutable MAIN parent")
        parents = {"coreRuntimeSelection": CORE_RUNTIME_SELECTION,
            "suProfileMetadataSelection": SU_COMPONENT_METADATA_SELECTION,
            "hostDurableSelection": HOST_DURABLE_SELECTION,
            "hostInitializationSelection": HOST_INITIALIZATION_SELECTION}
        if selected["selectedParents"].keys() != parents.keys():
            raise ValueError("Source-correction successor parent selection set mismatch")
        for key, current in parents.items():
            prior = selected["selectedParents"][key]
            if current is None or core_receipt_path(prior) != Path(current["identity"]["path"]).resolve() or \
                    any(prior[field] != current["identity"][field] for field in ("bytes", "sha256")):
                raise ValueError("Source-correction successor actual selected parent differs: " + key)
        parent_rows = {row["logicalPath"]: row for row in parent["files"]}
        if len(parent_rows) != len(parent["files"]) or parent_rows.keys() != rows.keys():
            raise ValueError("Source-correction successor must retain the exact three MAIN paths")
    replacement = {}
    for logical, row in rows.items():
        before, after = core_receipt_path(row["before"]), core_receipt_path(row["after"])
        original = parent_rows[logical] if successor else row
        if core_receipt_path(original["before"]) != core_receipt_path(originals[logical]) or \
                any(original["before"][key] != originals[logical][key] for key in ("bytes", "sha256")) or \
                before.name != after.name or row["before"]["sha256"] == row["after"]["sha256"]:
            raise ValueError("Source correction does not retain its exact original native source: " + logical)
        if successor:
            if row["parentCorrection"] != original:
                raise ValueError("Source-correction successor changed the original MAIN repair: " + logical)
            for key in ("after", "correctionReceipt", "diff"):
                core_receipt_path(original[key])
            actual = [source for source in (su_source_paths() if logical == SOURCE_CORRECTION_PATHS[1]
                                           else SOURCES) if source.name == before.name]
            if len(actual) != 1 or actual[0].resolve() != before:
                raise ValueError("Source-correction successor actual native predecessor differs: " + logical)
            merge = json.loads(core_receipt_path(row["mergeReceipt"]).read_text(encoding="utf-8"))
            if merge.get("schema") != "maplesu-main-compatibility-source-successor-merge/1" or \
                    merge.get("logicalPath") != logical or merge.get("selectedBefore") != row["before"] or \
                    merge.get("originalCorrection") != original or merge.get("after") != row["after"] or \
                    merge.get("diff") != row["diff"] or merge.get("mergeExitCode") != 0 or \
                    merge.get("conflictMarkers") is not False or merge.get("productionWritten") is not False:
                raise ValueError("Source-correction successor merge receipt differs: " + logical)
        for key in ("correctionReceipt", "diff"):
            core_receipt_path(row[key])
        if logical != SOURCE_CORRECTION_PATHS[1]:
            if selected_native_source(before.name).resolve() != before:
                raise ValueError("Native source was changed before its explicit correction")
            replacement[before] = after
    SOURCE_CORRECTIONS = dict(selected, identity=identity(path))
    SOURCES = [replacement.get(source.resolve(), source) for source in SOURCES]
    return SOURCE_CORRECTIONS


def source_correction_inputs():
    if SOURCE_CORRECTIONS is None:
        return []
    result = [Path(SOURCE_CORRECTIONS["identity"]["path"]),
        *(core_receipt_path(row[key]) for row in SOURCE_CORRECTIONS["files"]
          for key in ("before", "after", "correctionReceipt", "diff"))]
    if SOURCE_CORRECTIONS["schema"] == "maplesu-main-native-source-corrections/2":
        parent = core_receipt_path(SOURCE_CORRECTIONS["parentCorrectionSelection"])
        result.extend((parent, *(core_receipt_path(row) for row in SOURCE_CORRECTIONS["selectedParents"].values())))
        for row in SOURCE_CORRECTIONS["files"]:
            result.append(core_receipt_path(row["mergeReceipt"]))
            result.extend(core_receipt_path(row["parentCorrection"][key])
                for key in ("before", "after", "correctionReceipt", "diff"))
        previous = json.loads(parent.read_text(encoding="utf-8")).get("previousCorrectionSelection")
        if previous is not None:
            result.append(core_receipt_path(previous))
    return list(dict.fromkeys(result))
def collect_host_runtime_abi(target, payload, reference):
    record = host_runtime_pending_abi()
    record["missing"] = []
    probe = "manager_host_runtime_types"
    try:
        inputs = json.loads((target / "build-inputs.json").read_text(encoding="utf-8"))
        if inputs.get("hostDeploymentSourceSelection") != host_deployment_public_selection() or \
                inputs.get("hostDurableSourceSelection") != HOST_DURABLE_SELECTION or \
                inputs.get("coreRuntimeSourceSelection") != core_runtime_public_selection() or \
                inputs.get("hostInitializationSourceSelection") != HOST_INITIALIZATION_SELECTION or \
                inputs.get("hostPublicationSourceSelection") != HOST_PUBLICATION_SELECTION or \
                inputs.get("hostSyncSourceSelection") != HOST_SYNC_SELECTION or \
                inputs.get("hostRetirementSourceSelection") != HOST_RETIREMENT_SELECTION:
            raise ValueError("Host runtime target does not bind its exact source selection")
        objects, policies = {}, {}
        for name in (*HOST_RUNTIME_OBJECTS, probe):
            source = target / (name + ".c")
            provenance = object_provenance(target, name)
            selected = next((p for p in SOURCES if p.name == source.name), None)
            if provenance["sourceIdentity"]["sha256"] != sha(source) or (
                    name == probe and source.read_bytes() != host_runtime_probe_source().encode("utf-8")) or (
                    name != probe and (selected is None or sha(selected) != sha(source))):
                raise ValueError("Host runtime object/source/probe generation differs: " + name)
            record["sourceIdentities"].append(identity(source))
            record["objectIdentities"].append(identity(target / (name + ".o")))
            record["commandIdentities"].append(identity(target / ("." + name + ".o.cmd")))
            record["dependencyReceiptIdentities"].append(identity(target / "build-dependencies" / (name + ".json")))
            record["inlineDependencies"].extend(provenance["dependencies"])
            policies[name] = core_cfi_policy(target / ("." + name + ".o.cmd"))
            objects[name] = object_btf(target / (name + ".o"))
        if len({tuple(row.values()) for row in policies.values()}) != 1:
            raise ValueError("Host runtime objects/probe use different CFI policies")
        policy = policies[probe]
        policies["manager_management"] = core_cfi_policy(target / ".manager_management.o.cmd")
        if policies["manager_management"] != policy:
            raise ValueError("Host runtime and actual management CFI policies differ")
        declarations = objects[probe]
        for prefix, (name, members) in HOST_RUNTIME_LAYOUT_TYPES.items():
            record["compiledLayouts"][prefix] = declarations.named_structure_selected(name, members)
            if prefix in ("fsnotify", "work", "directory"):
                if reference is None:
                    raise ValueError("Host runtime linked vmlinux reference ABI is missing")
                actual = reference.named_structure_selected(name, members)
                if record["compiledLayouts"][prefix] != actual:
                    raise ValueError("Host runtime callback container target layout differs: " + name)
        record["authorityCallbacks"] = {member: declarations.member_function("msu_su_observed_authority", member)
            for member in ("read_grant", "put_owner", "list_uids")}
        record["installationRevokeCallback"], record["installationRevokeDeclaration"] = retained_probe_declaration(
            target, declarations, probe, "msu_runtime_revoke")
        record["localFunctionAbi"] = {name: objects[obj].function(name)
            for obj, names in HOST_RUNTIME_EXTRA_FUNCTIONS.items() for name in names}
        functions = {obj: (*names, *HOST_RUNTIME_NATIVE_CALLBACKS.get(obj, {}))
            for obj, names in HOST_RUNTIME_EXTRA_FUNCTIONS.items()}
        record["declarationFunctionAbi"] = {}
        record["declarationFunctionEvidence"] = {}
        for obj, names in functions.items():
            for name in names:
                actual = objects[obj].function(name)
                declared_pointer, record["declarationFunctionEvidence"][name] = retained_probe_declaration(
                    target, declarations, probe, "msu_runtime_decl_" + name, name)
                declared = declared_pointer[1:]
                if actual != declared:
                    raise ValueError("Host runtime actual definition/declaration ABI differs: " + name)
                record["declarationFunctionAbi"][name] = declared
        record["callbackAbi"] = {}
        for obj, callbacks in HOST_RUNTIME_NATIVE_CALLBACKS.items():
            for name, (structure, member) in callbacks.items():
                signature = "*" + objects[obj].function(name)
                declared = declarations.member_function(structure, member)
                native = reference.member_function(structure, member)
                if signature != declared or declared != native:
                    raise ValueError("Host runtime callback definition/declaration/target differs: " + name)
                record["callbackAbi"][name] = {"structure": structure, "member": member,
                    "signature": signature, "targetSignature": native}
        record["entrypoints"], record["entryCfi"] = core_function_entries(
            target, payload, policies, objects, functions, probe)
        missing = [name for name, entry in record["entrypoints"].items() if not entry.get("available")]
        if missing:
            raise ValueError("Host runtime API lacks retained actual callers: " + ", ".join(missing))
        core_native_functions(target, reference, declarations, policy, record,
            HOST_RUNTIME_NATIVE_FUNCTIONS, {name: "msu_runtime_" + name for name in HOST_RUNTIME_NATIVE_FUNCTIONS}, probe)
        facts = record["targetFacts"] = object_words(target / "manager_su_runtime_target.o", "msu_su_runtime_target_facts")
        if len(facts) != 9 or facts[0] != 1 or facts[8] not in (0, 1):
            raise ValueError("Host runtime exact native target fact schema differs")
        if HOST_RETIREMENT_SELECTION is not None:
            support = target / "support"
            with (target / (probe + ".o")).open("rb") as stream:
                types = ELFFile(stream).get_section_by_name(".symtab").get_symbol_by_name(
                    "__kcfi_typeid_msu_manager_resident_retire_work") or []
                if len(types) != 1:
                    raise ValueError("Retirement work lacks the actual target workqueue KCFI type")
                expected_type = types[0]["st_value"] & 0xffffffff
            source_receipt = support / "source-inputs.json"
            support_inputs = json.loads(source_receipt.read_bytes())
            actual_sources = {row["logicalPath"]: row["selectedSource"] for row in support_inputs["files"]}
            for row in HOST_RETIREMENT_SELECTION["files"]:
                expected = next((item["after"] for item in RUNTIME_LAYOUT_SELECTION["files"]
                    if item["logicalPath"] == row["logicalPath"]), row["after"]) if RUNTIME_LAYOUT_SELECTION else row["after"]
                if Path(row["logicalPath"]).name in ("manager_claim_bootstrap.c", "manager_resident_abi.h"):
                    actual = actual_sources.get(row["logicalPath"])
                    if actual is None or dict(actual, path=Path(actual["path"]).resolve()) != \
                            dict(expected, path=Path(expected["path"]).resolve()):
                        raise ValueError("Retirement Image callback/native source pair differs")
            with (support / "bootstrap.elf").open("rb") as stream:
                image_elf = ELFFile(stream)
                callback, = image_elf.get_section_by_name(".symtab").get_symbol_by_name("msu_manager_resident_retire_work")
                section = image_elf.get_section(callback["st_shndx"])
                at = callback["st_value"] - section["sh_addr"]
                prefix = int.from_bytes(section.data()[at - 4:at], "little") if at >= 4 else None
                if prefix != expected_type or (support / "bootstrap.bin").read_bytes() != section.data():
                    raise ValueError("Retirement Image callback/native workqueue KCFI mismatch")
            record["retirement"] = {"sourceSelection": HOST_RETIREMENT_SELECTION,
                "callbackOffset": callback["st_value"], "expectedKcfiType": expected_type,
                "actualKcfiPrefix": prefix, "sameWorkqueueType": True,
                "imageElf": identity(support / "bootstrap.elf"),
                "imageBinary": identity(support / "bootstrap.bin"), "sourceReceipt": identity(source_receipt),
                "deviceQualified": False}
        record["nativeTargetEnabled"] = bool(facts[8])
        if facts[8]:
            record["missing"].append("Native target enabled without a selected target qualification receipt")
        record["metadataComplete"] = not record["missing"]
    except (OSError, ValueError, KeyError, IndexError) as error:
        record["missing"].append(type(error).__name__ + ": " + str(error))
    return record


HOST_DEPLOYMENT_SELECTION = None
HOST_DEPLOYMENT_NATIVE = ("manager_resident_host.c", "manager_resident_host.h", "manager_resident_abi.h", "manager_claim_loader.h", "manager_deployment_namespace.h")
HOST_DEPLOYMENT_SUPPORT = ("manager_claim_bootstrap.c", "manager_claim_bootstrap.h", "manager_resident_abi.h", "manager_claim_loader.h", "manager_deployment_namespace.h")


def host_deployment_receipt(row):
    path = Path(row["path"]).resolve()
    if not path.is_relative_to(ROOT.resolve()) or not path.is_file():
        raise ValueError("Deployment source receipt outside workspace or absent")
    actual = identity(path)
    if any(actual[key] != row[key] for key in ("bytes", "sha256")):
        raise ValueError("Deployment source receipt changed: " + str(path))
    return path


def select_host_deployment_sources(path, expected_sha256):
    global SOURCES, SUPPORT_SOURCES, HOST_DEPLOYMENT_SELECTION
    path = path.resolve()
    if not path.is_relative_to(ROOT.resolve()) or sha(path) != expected_sha256:
        raise ValueError("Deployment source selection identity mismatch")
    selected = json.loads(path.read_text(encoding="utf-8"))
    if selected.get("schema") != "maplesu-host-deployment-source-selection/1" or CORE_SELECTION is None or HOST_DEPLOYMENT_SELECTION is not None:
        raise ValueError("Deployment selection requires one explicit CORE predecessor")
    if selected["corePredecessor"] != CORE_SELECTION["identity"]:
        raise ValueError("Deployment CORE source predecessor mismatch")
    host_deployment_receipt(selected["ownerCandidate"])
    host_deployment_receipt(selected["ownerSelection"])
    for correction in selected.get("sourceCorrections", ()):
        host_deployment_receipt(correction)
    rows = {Path(row["logicalPath"]).name: row for row in selected["files"]}
    runtime_names = HOST_RUNTIME_NATIVE_BASE if selected.get("runtimeChannel") else ()
    expected = set(HOST_DEPLOYMENT_NATIVE) | set(HOST_DEPLOYMENT_SUPPORT) | set(runtime_names)
    if len(rows) != len(selected["files"]) or rows.keys() != expected:
        raise ValueError("Deployment source path set mismatch")
    replacement = {}
    for name, row in rows.items():
        if row["logicalPath"] != "runtime/ultra/identity/kernel/" + name:
            raise ValueError("Deployment logical path mismatch")
        replacement[name] = host_deployment_receipt(row["after"])
        if row["before"] is not None:
            host_deployment_receipt(row["before"])
    roles = (("native", SOURCES, (*HOST_DEPLOYMENT_NATIVE, *runtime_names)), ("support", SUPPORT_SOURCES, HOST_DEPLOYMENT_SUPPORT))
    for role, paths, names in roles:
        current = {p.name: p for p in paths}
        if len(current) != len(paths):
            raise ValueError("Duplicate deployment predecessor input name")
        for name in expected:
            prior = selected["previousInputs"][role].get(name)
            actual = identity(current[name]) if name in current else None
            if actual != prior or (name in current and name not in names):
                raise ValueError("Deployment actual predecessor input changed: " + role + ":" + name)
    # Commit source sets only after all predecessor/protocol after-image checks.
    select_host_runtime_metadata(selected)
    SOURCES = [replacement.get(p.name, p) for p in SOURCES]
    SUPPORT_SOURCES = [replacement.get(p.name, p) for p in SUPPORT_SOURCES]
    for name in (*HOST_DEPLOYMENT_NATIVE, *runtime_names):
        if all(p.name != name for p in SOURCES):
            SOURCES.append(replacement[name])
    for name in HOST_DEPLOYMENT_SUPPORT:
        if all(p.name != name for p in SUPPORT_SOURCES):
            SUPPORT_SOURCES.append(replacement[name])
    HOST_DEPLOYMENT_SELECTION = dict(selected, identity=identity(path))
    return HOST_DEPLOYMENT_SELECTION


def host_deployment_selection_inputs():
    if HOST_DEPLOYMENT_SELECTION is None:
        return []
    item = HOST_DEPLOYMENT_SELECTION
    rows = [item["ownerCandidate"], item["ownerSelection"], item["identity"], item["corePredecessor"], *item.get("sourceCorrections", ())]
    rows += [row[key] for row in item["files"] for key in ("before", "after") if row[key] is not None]
    rows += [row for group in item["previousInputs"].values() for row in group.values() if row is not None]
    return list(dict.fromkeys(host_deployment_receipt(row) for row in rows))


def host_deployment_public_selection():
    item = HOST_DEPLOYMENT_SELECTION
    return {"schema": "maplesu-host-deployment-source-evolution/1",
        "identity": item["identity"], "ownerCandidate": item["ownerCandidate"],
        "ownerSelection": item["ownerSelection"], "corePredecessor": item["corePredecessor"],
        "previousInputs": item["previousInputs"],
        "coreNativeInputs": [{"logicalPath": row["logicalPath"], "snapshotIdentity": row["snapshotIdentity"]}
            for row in CORE_SELECTION["nativeInputs"] if Path(row["logicalPath"]).name in (
                "manager_resident_host.c", "manager_resident_host.h", "manager_resident_abi.h", "manager_claim_loader.h")],
        "selectedInputs": [{"logicalPath": row["logicalPath"], "before": row["before"],
            "after": row["after"]} for row in item["files"]],
        "sourceOwner": "HOST", "sourceFileCount": len(item["files"]),
        **({"runtimeChannel": item["runtimeChannel"], "protocolInput": item["protocolInput"],
            "runtimeObjects": list(HOST_RUNTIME_OBJECTS), "runtimeNativeImports": list(HOST_RUNTIME_NATIVE_FUNCTIONS)} if item.get("runtimeChannel") else {}),
        "sourceCorrections": item.get("sourceCorrections", []),
        "predecessorIsNotFinalHostSource": True, "productionWritten": False, "targetQualified": False}


def su_metadata_receipt(item, root=None):
    root = root or Path(SU_COMPONENT_METADATA_SELECTION["identity"]["path"]).parent
    path = Path(item["path"])
    return su_component_receipt(dict(item, path=str(root / path) if not path.is_absolute()
        and not item["path"].startswith(".tmp/") else str(path)))


def su_metadata_selection_inputs():
    if SU_COMPONENT_METADATA_SELECTION is None:
        return []
    item = SU_COMPONENT_METADATA_SELECTION
    archive_path = su_metadata_receipt(item["archiveIndex"])
    archive = json.loads(archive_path.read_text(encoding="utf-8"))
    rows = [item["identity"], item["archiveIndex"],
        *(item[key]["identity"] for key in ("parentSelection", "metadataOwnerSelection", "hostEvidence", "nativeEvidence")),
        item["headerEvolution"]["providerSelection"],
        *(row[key] for row in item["files"] for key in ("before", "after")),
        *(row["identity"] for row in item["retainedInputs"]),
        *(row["archive"] for row in item["headerEvolution"]["stages"]),
        *(row["archive"] for row in archive["ownerReceipts"]),
        *archive["hostFiles"], *archive["nativeCommandsAndDepfiles"],
        *(row["object"] for row in item["nativeObjects"]),
        *(row["archive"] for row in item["nativeDependencies"])]
    return list(dict.fromkeys(su_metadata_receipt(row) for row in rows))


_parent_su_selected_receipts = su_selected_receipts


def su_selected_receipts():
    return [*_parent_su_selected_receipts(), *su_metadata_selection_inputs()]


_parent_su_source_paths = su_source_paths


def su_source_paths():
    paths = _parent_su_source_paths()
    if SU_COMPONENT_METADATA_SELECTION is None:
        return paths
    replacement = {Path(row["logicalPath"]).name: su_metadata_receipt(row["after"])
                   for row in SU_COMPONENT_METADATA_SELECTION["files"]}
    if SOURCE_CORRECTIONS is not None:
        replacement.update({Path(row["logicalPath"]).name: core_receipt_path(row["after"])
            for row in SOURCE_CORRECTIONS["files"] if row["logicalPath"].startswith(SU_COMPONENT_ROOT)})
    return [replacement.get(path.name, path) for path in paths]

# Insert beside SU_COMPONENT_SELECTION in the ROOT builder successor.
SU_COMPONENT_METADATA_PATHS = frozenset((
    SU_COMPONENT_ROOT + "su_install_registry.c",
    SU_COMPONENT_ROOT + "su_install_registry.h",
))


def select_su_component_metadata_sources(path, expected_sha256):
    """Select the exact two-source saved-profile metadata successor after SU10."""
    global SU_COMPONENT_METADATA_SELECTION
    if SU_COMPONENT_SELECTION is None or SU_COMPONENT_METADATA_SELECTION is not None:
        raise ValueError("Select the exact five-TU SU parent once before metadata successor")
    path = path.resolve()
    receipt = lambda item: su_metadata_receipt(item, path.parent)
    if not path.is_relative_to(ROOT.resolve()) or sha(path) != expected_sha256:
        raise ValueError("SU metadata successor selection identity mismatch")
    selected = json.loads(path.read_text(encoding="utf-8"))
    if selected.get("schema") != "maplesu-p2-su-profile-metadata-source-successor/1" or \
            selected.get("productionWritten") or selected.get("sourceOwnerFrozen") or \
            selected.get("fullSuComplete") or selected.get("targetQualified") or selected.get("suReady"):
        raise ValueError("SU metadata successor status/schema mismatch")

    parent = selected["parentSelection"]
    if any(parent["sourceIdentity"][key] != SU_COMPONENT_SELECTION["identity"][key]
           for key in ("sha256",)):
        raise ValueError("SU metadata successor has a different SU10 predecessor")
    parent_path = receipt(parent["identity"])
    owner_path = receipt(selected["metadataOwnerSelection"]["identity"])
    host_path = receipt(selected["hostEvidence"]["identity"])
    native_path = receipt(selected["nativeEvidence"]["identity"])
    provider_path = receipt(selected["headerEvolution"]["providerSelection"])
    parent_doc = json.loads(parent_path.read_text(encoding="utf-8"))
    owner_doc = json.loads(owner_path.read_text(encoding="utf-8"))
    provider_doc = json.loads(provider_path.read_text(encoding="utf-8"))
    if parent_doc.get("schema") != "maplesu-p2-su-component-source-selection/1" or \
            len(parent_doc.get("files", ())) != 10 or parent_doc.get("sourceOwnerFrozen") is not False:
        raise ValueError("SU metadata successor parent is not the original exact SU10 selection")
    if owner_doc.get("schema") != "maplesu-complete-saved-profile-metadata/1" or \
            owner_doc.get("productionWritten") or owner_doc.get("wholeSuComplete") or \
            owner_doc.get("targetQualified") or len(owner_doc.get("files", ())) != 2:
        raise ValueError("SU metadata owner selection is not its exact two-source frozen delivery")
    old = {row["logicalPath"]: row for row in parent_doc["files"]}
    owner_rows = {row["logicalPath"]: row for row in owner_doc["files"]}
    rows = {row["logicalPath"]: row for row in selected["files"]}
    if rows.keys() != set(SU_COMPONENT_METADATA_PATHS) or owner_rows.keys() != rows.keys() or \
            len(rows) != 2 or selected.get("sourcePathCount") != 2 or \
            selected.get("totalSelectedInputs") != 10 or selected.get("translationUnits") != {"su_install_registry.c": True, "unchangedTuCount": 4, "newTuCount": 0}:
        raise ValueError("SU metadata successor changed more than the exact registry C/header pair")
    for logical, row in rows.items():
        if logical not in old:
            raise ValueError("SU metadata successor path is absent from parent: " + logical)
        receipt(row["before"])
        after = receipt(row["after"])
        expected_before = old[logical]["snapshotIdentity"]
        owner = owner_rows[logical]
        if any(row["before"][key] != expected_before[key] for key in ("bytes", "sha256")) or \
                any(row["ownerBefore"][key] != expected_before[key] or
                    row["successorChain"][-1]["before"][key] != owner["before"][key]
                    for key in ("bytes", "sha256")) or \
                any(row["ownerFrozen"][key] != owner["frozen"][key] for key in ("bytes", "sha256")) or \
                any(row["after"][key] != owner["frozen"][key] for key in ("bytes", "sha256")):
            raise ValueError("SU metadata owner/parent after tuple mismatch: " + logical)

    header = "runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/kernel/su_install_registry.h"
    provider_rows = {row["logicalPath"]: row for row in provider_doc["files"]}
    ph, mh = provider_rows.get(header), owner_rows.get(header)
    if not ph or not mh or \
            old[header]["snapshotIdentity"]["sha256"] != ph["before"]["sha256"] or \
            ph["after"]["sha256"] != mh["before"]["sha256"] or \
            mh["frozen"]["sha256"] != rows[header]["after"]["sha256"]:
        raise ValueError("SU header evolution does not bind SU10 → provider → metadata successor")
    stages = {row["stage"]: row for row in selected["headerEvolution"]["stages"]}
    for stage, digest in {
        "su10-before": old[header]["snapshotIdentity"]["sha256"],
        "provider-intermediate": ph["after"]["sha256"],
        "metadata2-before": mh["before"]["sha256"],
        "metadata2-final": mh["frozen"]["sha256"],
    }.items():
        row = stages.get(stage)
        if not row or row["archive"]["sha256"] != digest:
            raise ValueError("Missing actual SU header evolution evidence: " + stage)
        receipt(row["archive"])
    for row in selected.get("retainedInputs", ()):
        if row["logicalPath"] in rows or row["logicalPath"] not in old or \
                any(row["identity"][key] != old[row["logicalPath"]]["snapshotIdentity"][key]
                    for key in ("bytes", "sha256")):
            raise ValueError("SU metadata successor changed a retained SU10 input")
    if len(selected.get("retainedInputs", ())) != 8:
        raise ValueError("SU metadata successor must retain the other eight SU10 source inputs")

    host = json.loads(host_path.read_text(encoding="utf-8"))
    native = json.loads(native_path.read_text(encoding="utf-8"))
    if not host.get("passed") or not host.get("inputsStable") or \
            not native.get("passed") or not native.get("inputsStable") or not native.get("kcfiEnabled"):
        raise ValueError("SU metadata owner host/native evidence is incomplete")
    host_inputs = {Path(row["path"]).name: row for row in host.get("inputs", ())}
    for logical, row in rows.items():
        name = Path(logical).name
        if name not in host_inputs or host_inputs[name]["sha256"] != row["after"]["sha256"]:
            raise ValueError("SU host evidence does not bind selected metadata source: " + name)
    native_rows = {row["source"]["sha256"]: row for row in native["objects"]}
    expected_tus = {row["after"]["sha256"] for row in rows.values() if row["logicalPath"].endswith(".c")} | {
        old[logical]["snapshotIdentity"]["sha256"] for logical in old if logical.endswith(".c") and logical not in rows}
    if len(native_rows) != 5 or set(native_rows) != expected_tus or \
            selected["nativeEvidence"].get("target66Qualified") is not False or \
            selected["nativeEvidence"].get("wholeSdkBuild") is not False or \
            selected["nativeEvidence"].get("linked") is not False:
        raise ValueError("SU metadata native evidence does not bind the exact five current TUs/KCFI scope")
    for obj in selected.get("nativeObjects", ()):
        source_sha = obj["source"]["sha256"]
        if source_sha not in native_rows or any(obj["object"][key] != native_rows[source_sha]["object"][key]
                                                   for key in ("bytes", "sha256")):
            raise ValueError("SU metadata archived native object identity differs from owner result")
        receipt(obj["object"])
    if len(selected.get("nativeObjects", ())) != 5:
        raise ValueError("SU metadata successor must preserve all five current native object receipts")
    for dep in selected.get("nativeDependencies", ()):
        receipt(dep["archive"])
    if len(selected.get("nativeDependencies", ())) != selected.get("nativeDependencyCount"):
        raise ValueError("SU metadata native dependency archive is incomplete")

    SU_COMPONENT_METADATA_SELECTION = dict(selected, identity=identity(path))
    su_metadata_selection_inputs()
    apply_su_component_metadata_abi()
    return SU_COMPONENT_METADATA_SELECTION

# Include with the selector fragment before source/probe emission.
def _su_selected_struct_members(source, structure):
    text = re.sub(r"/\*.*?\*/|//[^\n]*", "", source, flags=re.S)
    match = re.search(r"\bstruct\s+" + re.escape(structure) + r"\s*\{([^}]*)\}", text, re.S)
    if not match:
        raise ValueError("Selected SU metadata structure absent: " + structure)
    members = []
    for declaration in match.group(1).split(";"):
        declaration = re.sub(r"\[[^\]]*\]", "", declaration).strip()
        if not declaration:
            continue
        members.extend(re.findall(r"\b[A-Za-z_]\w*\b", part)[-1]
            for part in declaration.split(",") if re.findall(r"\b[A-Za-z_]\w*\b", part))
    if len(members) != len(set(members)):
        raise ValueError("Duplicate selected SU structure member: " + structure)
    return tuple(members)


def apply_su_component_metadata_abi():
    """Populate layout/fact expressions from the actual frozen after-images."""
    if SU_COMPONENT_METADATA_SELECTION is None:
        return
    rows = {row["logicalPath"]: row for row in SU_COMPONENT_METADATA_SELECTION["files"]}
    header_path = su_metadata_receipt(rows[SU_COMPONENT_ROOT + "su_install_registry.h"]["after"])
    registry_path = su_metadata_receipt(rows[SU_COMPONENT_ROOT + "su_install_registry.c"]["after"])
    header = header_path.read_text(encoding="utf-8")
    registry = registry_path.read_text(encoding="utf-8")
    profile_members = _su_selected_struct_members(header, "msu_su_profile")
    entry_members = _su_selected_struct_members(registry, "msu_su_install_entry")
    metadata_fields = ("name", "explicit_profile", "allow_su", "non_root_use_default", "template_present")
    if profile_members[-5:] != metadata_fields or \
            not {"profile", "saved_profile"}.issubset(entry_members) or \
            "msu_su_lease_saved_profile" not in registry:
        raise ValueError("Frozen SU profile metadata/accessor differs from requested ABI")

    # The old component probe facts reported profile with the old source fields.
    old_profile = SU_COMPONENT_LAYOUTS["profile"][2]
    for member in old_profile:
        key = "profile" + "".join(word.capitalize() for word in member.split("_"))
        SU_COMPONENT_FACTS.pop(key + "Offset", None)
        SU_COMPONENT_FACTS.pop(key + "Bytes", None)
    SU_COMPONENT_FACTS.pop("profileBytes", None)
    SU_COMPONENT_FACTS.pop("profileAlign", None)
    # Keep the private facts header word at 1; the enclosing report ABI explicitly advances to /4.
    # The actual field facts come from the dedicated same-generation profile probe.
    SU_COMPONENT_LAYOUTS["profile"] = ("msu_su_profile", "manager_su_profile_facts", profile_members)
    SU_COMPONENT_LAYOUTS["installEntry"] = (
        "msu_su_install_entry", "manager_su_component_facts", entry_members)
    for prefix, kind, members, target in (
        ("profile", "struct msu_su_profile", profile_members, SU_COMPONENT_PROFILE_FACTS),
        ("installEntry", "struct msu_su_install_entry", entry_members, SU_COMPONENT_FACTS),
    ):
        target[prefix + "Bytes"] = "sizeof(" + kind + ")"
        target[prefix + "Align"] = "__alignof__(" + kind + ")"
        for member in members:
            key = prefix + "".join(word.capitalize() for word in member.split("_"))
            target[key + "Offset"] = "offsetof(" + kind + ", " + member + ")"
            target[key + "Bytes"] = "sizeof(((" + kind + " *)0)->" + member + ")"
    SU_COMPONENT_FUNCTIONS["su_install_registry"] = (
        *SU_COMPONENT_FUNCTIONS["su_install_registry"], "msu_su_lease_saved_profile")


def apply_su_component_runtime_layout_abi(replacements):
    """Bind the two current-runtime layout additions to their selected sources."""
    if SU_COMPONENT_METADATA_SELECTION is None:
        return
    for prefix, filename, kind, added in (
        ("installEntry", "su_install_registry.c", "msu_su_install_entry", "source_invalidated"),
        ("executionReceipt", "su_profile_execution.h", "msu_su_execution_receipt", "task_profile"),
    ):
        if filename not in replacements:
            raise ValueError("Runtime SU layout source absent: " + filename)
        members = _su_selected_struct_members(replacements[filename].read_text(encoding="utf-8"), kind)
        previous = SU_COMPONENT_LAYOUTS[prefix][2]
        if members.count(added) != 1 or tuple(member for member in members if member != added) != previous:
            raise ValueError("Runtime SU layout is not its exact one-member successor: " + prefix)
        SU_COMPONENT_LAYOUTS[prefix] = (kind, "manager_su_component_facts", members)
        for member in members:
            key = prefix + "".join(word.capitalize() for word in member.split("_"))
            SU_COMPONENT_FACTS[key + "Offset"] = "offsetof(struct " + kind + ", " + member + ")"
            SU_COMPONENT_FACTS[key + "Bytes"] = "sizeof(((struct " + kind + " *)0)->" + member + ")"


# Call apply_su_component_metadata_abi() immediately after the metadata successor
# selector and before build()/su_component_probe() emits any source.
_original_su_component_pending_abi = su_component_pending_abi

def su_component_pending_abi():
    record = _original_su_component_pending_abi()
    if SU_COMPONENT_METADATA_SELECTION is not None:
        record["schema"] = "maplesu-su-provider-abi/4"
        record["schemaVersion"] = 4
        record["previousReportSchema"] = "maplesu-su-provider-abi/3"
        record["missing"][0] = "Selected observed-install/durable authority and retained factory need same-generation linked and target-qualified execution"
        record["profileMetadata"] = {
            "schema": "maplesu-su-profile-metadata-abi/1",
            "sourceSelection": SU_COMPONENT_METADATA_SELECTION["identity"],
            "storedFields": ["name", "explicit_profile", "allow_su",
                             "non_root_use_default", "template_present"],
            "savedAndEffectiveDistinct": True,
            "savedAccessor": "msu_su_lease_saved_profile",
            "savedAccessorLifetime": "held lease only",
            "consumerApplied": False,
            "targetQualified": False,
        }
    return record

# Explicit child source generation; CORE54 remains the immutable predecessor.
CORE_RUNTIME_SELECTION = None
HOST_DURABLE_SELECTION = None


def core_runtime_selection_inputs():
    if CORE_RUNTIME_SELECTION is None:
        return []
    selected = CORE_RUNTIME_SELECTION
    result = [host_deployment_receipt(selected["identity"])]
    for key in ("parentCoreSelection", "parentCoreAbi", "parentCoreFreeze", "rootHostDeploymentSelection",
                "core73Selection", "core73Freeze", "core73Changeset", "coreOnlyDiff", "completeArchive",
                "overlapResolution", "mergedPathIndex", "integratedDiff"):
        result.append(host_deployment_receipt(selected[key]))
    result.extend(host_deployment_receipt(row) for row in selected["hostOverlayReceipts"])
    archive = json.loads(host_deployment_receipt(selected["completeArchive"]).read_text(encoding="utf-8"))
    for row in archive["coreFiles"]:
        result.append(host_deployment_receipt(row["coreAfterArchive"]))
        if row.get("selectedBeforeCoreArchive"):
            result.append(host_deployment_receipt(row["selectedBeforeCoreArchive"]))
    for row in selected["files"]:
        result.extend(host_deployment_receipt(row[key]) for key in
                      ("rootCurrent", "coreSelectedBefore", "coreFrozenAfter", "mergedAfter") if row.get(key))
    return list(dict.fromkeys(result))


def core_runtime_public_selection():
    if CORE_RUNTIME_SELECTION is None:
        return None
    item = CORE_RUNTIME_SELECTION
    return {"schema": "maplesu-core-runtime-source-evolution/1", "identity": item["identity"],
        "parentCoreSelection": item["parentCoreSelection"], "parentCoreAbi": item["parentCoreAbi"],
        "parentHostSelection": item["rootHostDeploymentSelection"],
        "core73Selection": item["core73Selection"], "core73Freeze": item["core73Freeze"],
        "selectedInputs": item["files"], "sourceFileCount": len(item["files"]),
        "productionWritten": False, "targetQualified": False}


def select_core_runtime_sources(path, expected_sha256):
    global CORE_RUNTIME_SELECTION, SOURCES, SUPPORT_SOURCES, PROTOCOL
    path = path.resolve()
    if CORE_SELECTION is None or HOST_DEPLOYMENT_SELECTION is None or CORE_RUNTIME_SELECTION is not None:
        raise ValueError("CORE runtime child requires its exact CORE54 and HOST parent once")
    if not path.is_relative_to(ROOT.resolve()) or sha(path) != expected_sha256:
        raise ValueError("CORE runtime child selection identity mismatch")
    selected = json.loads(path.read_text(encoding="utf-8"))
    if selected.get("schema") != "maplesu-core-runtime-successor-selection-fragment/1" or \
            selected.get("productionWritten") or selected.get("previousInputsRelaxed") or \
            selected.get("oldCore54FreezeModified") or selected.get("oldCore54Abi24SchemaModified"):
        raise ValueError("CORE runtime child status/schema mismatch")
    for key, parent in (("parentCoreSelection", CORE_SELECTION["identity"]),
                        ("rootHostDeploymentSelection", HOST_DEPLOYMENT_SELECTION["identity"])):
        if host_deployment_receipt(selected[key]) != Path(parent["path"]).resolve() or \
                any(selected[key][k] != parent[k] for k in ("bytes", "sha256")):
            raise ValueError("CORE runtime child predecessor mismatch: " + key)
    archive = json.loads(host_deployment_receipt(selected["completeArchive"]).read_text(encoding="utf-8"))
    freeze = json.loads(host_deployment_receipt(selected["core73Freeze"]).read_text(encoding="utf-8"))
    frozen = {row["path"]: row for row in freeze["files"]}
    archived = {row["logicalPath"]: row for row in archive["coreFiles"]}
    if len(frozen) != 73 or len(archived) != 73 or frozen.keys() != archived.keys():
        raise ValueError("CORE runtime child complete73 archive path set mismatch")
    for logical, row in archived.items():
        after = row["coreAfterArchive"]
        host_deployment_receipt(after)
        if any(after[k] != frozen[logical][k] for k in ("bytes", "sha256")):
            raise ValueError("CORE runtime child archived source differs from freeze: " + logical)
    rows = {row["logicalPath"]: row for row in selected["files"]}
    merged = json.loads(host_deployment_receipt(selected["mergedPathIndex"]).read_text(encoding="utf-8"))
    indexed = {row["logicalPath"]: row["mergedAfter"] for row in merged["files"]}
    if len(rows) != 17 or len(rows) != len(selected["files"]) or rows.keys() != indexed.keys():
        raise ValueError("CORE runtime child exact17 source paths mismatch")
    current = {"runtime/ultra/identity/kernel/" + p.name: p for p in SOURCES}
    current.update({row["logicalPath"]: core_receipt_path(row["snapshotIdentity"])
                    for row in CORE_SELECTION["files"] if row["logicalPath"] not in current})
    current["runtime/ultra/authorization/request_protocol.h"] = PROTOCOL
    replacement = {}
    for logical, row in rows.items():
        before, actual = row["rootCurrent"], current.get(logical)
        if actual is None and before is not None and logical.startswith("runtime/maplesu-sdk/") and \
                row["rootCurrentRole"] == "ROOT has no child selection; selected-before-core base is current":
            archived_before = archived[logical]["selectedBeforeCoreArchive"]
            if any(before[k] != row["coreSelectedBefore"][k] or before[k] != archived_before[k]
                   for k in ("bytes", "sha256")):
                raise ValueError("CORE unselected SDK predecessor differs from archived canonical base: " + logical)
            actual = host_deployment_receipt(archived_before)
        if (before is None) != (actual is None) or before is not None and \
                (any(identity(host_deployment_receipt(before))[k] != identity(actual)[k]
                     for k in ("bytes", "sha256"))):
            raise ValueError("CORE runtime child actual predecessor changed: " + logical)
        after = host_deployment_receipt(row["mergedAfter"])
        if row["mergedAfter"] != indexed[logical] or any(row["coreFrozenAfter"][k] != frozen[logical][k]
                                                       for k in ("bytes", "sha256")):
            raise ValueError("CORE runtime child merged/frozen tuple mismatch: " + logical)
        if re.search(rb"^(?:<<<<<<<|=======|>>>>>>>)", after.read_bytes(), re.M):
            raise ValueError("CORE runtime child contains unresolved markers: " + logical)
        if not logical.startswith("runtime/ultra/identity/kernel/") and \
                any(row["mergedAfter"][k] != row["coreFrozenAfter"][k] for k in ("bytes", "sha256")):
            raise ValueError("CORE SDK/protocol requires exact canonical after-image: " + logical)
        replacement[Path(logical).name] = after
    for required in ("manager_early_helper.h", "manager_management.c", "manager_management.h",
                     "manager_resident_host.c", "manager_resident_host.h", "request_protocol.h"):
        if required not in replacement:
            raise ValueError("CORE runtime child missing required source: " + required)
    # Validate the entire child receipt closure before selecting any new bytes.
    CORE_RUNTIME_SELECTION = dict(selected, identity=identity(path))
    try:
        core_runtime_selection_inputs()
    except Exception:
        CORE_RUNTIME_SELECTION = None
        raise
    SOURCES = [replacement.get(p.name, p) for p in SOURCES]
    SOURCES.append(replacement["manager_early_helper.h"])
    SUPPORT_SOURCES = [replacement.get(p.name, p) for p in SUPPORT_SOURCES]
    PROTOCOL = replacement["request_protocol.h"]
    source = replacement["manager_management.c"].read_text(encoding="utf-8")
    for prefix, structure in (("service", "msu_runtime_service"), ("session", "msu_management_session")):
        members = _su_selected_struct_members(source, structure)
        CORE_LAYOUT_TYPES[prefix] = (structure, "manager_core_facts", members)
        CORE_FACTS[prefix + "Bytes"] = "sizeof(struct " + structure + ")"
        CORE_FACTS[prefix + "Align"] = "__alignof__(struct " + structure + ")"
        for member in members:
            key = prefix + "".join(word.capitalize() for word in member.split("_"))
            CORE_FACTS[key + "Offset"] = "offsetof(struct " + structure + ", " + member + ")"
            CORE_FACTS[key + "Bytes"] = "sizeof(((struct " + structure + " *)0)->" + member + ")"
    status_fields = {"schema": 0, "bytes": 4, "host": 8, "installation_generation": 24,
        "runtime_generation": 40, "installation_fence": 56, "flags": 64, "users": 68,
        "producers": 72, "hook_count": 76, "status": 80, "target_error": 84,
        "cleanup_error": 88, "start_error": 92, "target_qualified": 96, "installation_flags": 100}
    WIRE_FACTS.update({"suRuntimeReadCommand": ("MAPLESU_REQUEST_SU_RUNTIME_READ", 30),
        "suRuntimeStatusBytes": ("sizeof(maplesu_su_runtime_status_v3)", 104),
        "suRuntimeStatusAlign": ("__alignof__(maplesu_su_runtime_status_v3)", 8)})
    for member, offset in status_fields.items():
        key = "suRuntimeStatus" + "".join(word.capitalize() for word in member.split("_")) + "Offset"
        WIRE_FACTS[key] = ("offsetof(maplesu_su_runtime_status_v3, " + member + ")", offset)
    return CORE_RUNTIME_SELECTION


def select_host_durable_sources(path, expected_sha256):
    global HOST_DURABLE_SELECTION, SOURCES
    path = path.resolve()
    if HOST_DEPLOYMENT_SELECTION is None or SU_COMPONENT_METADATA_SELECTION is None or HOST_DURABLE_SELECTION is not None:
        raise ValueError("Durable v3 child requires selected HOST and SU metadata parents once")
    if not path.is_relative_to(ROOT.resolve()) or sha(path) != expected_sha256:
        raise ValueError("Durable v3 child selection identity mismatch")
    selected = json.loads(path.read_text(encoding="utf-8"))
    if selected.get("schema") != "maplesu-durable-authority-source-selection/2" or selected.get("productionWritten"):
        raise ValueError("Durable v3 child status/schema mismatch")
    current = {p.name: p for p in SOURCES}
    rows = {Path(row["logicalPath"]).name: row for row in selected["files"]}
    expected = {"su_durable_" + stem + extension for stem in ("authority", "modern") for extension in (".c", ".h")}
    if len(rows) != 4 or rows.keys() != expected:
        raise ValueError("Durable v3 child exact four-source path set mismatch")
    for name, row in rows.items():
        if row["logicalPath"] != "runtime/ultra/identity/kernel/" + name or \
                host_deployment_receipt(row["before"]) != current[name].resolve():
            raise ValueError("Durable v3 child actual predecessor mismatch: " + name)
        host_deployment_receipt(row["after"])
    inputs = json.loads(host_deployment_receipt(selected["inputs"]).read_text(encoding="utf-8"))
    metadata = SU_COMPONENT_METADATA_SELECTION
    if inputs["nativeSelection"]["sha256"] != metadata["metadataOwnerSelection"]["identity"]["sha256"]:
        raise ValueError("Durable v3 decoder differs from selected frozen SU metadata generation")
    native_sources = {Path(row["path"]).name: row for row in inputs["nativeFrozenSources"]}
    for row in metadata["files"]:
        native = native_sources[Path(row["logicalPath"]).name]
        if any(native[k] != row["after"][k] for k in ("bytes", "sha256")):
            raise ValueError("Durable v3 decoder and SU metadata after-image differ")
    HOST_DURABLE_SELECTION = dict(selected, identity=identity(path))
    SOURCES = [host_deployment_receipt(rows[p.name]["after"]) if p.name in rows else p for p in SOURCES]
    return HOST_DURABLE_SELECTION


def host_durable_selection_inputs():
    if HOST_DURABLE_SELECTION is None:
        return []
    item = HOST_DURABLE_SELECTION
    return [host_deployment_receipt(item["identity"]), host_deployment_receipt(item["priorSelection"]),
            host_deployment_receipt(item["inputs"]),
            *(host_deployment_receipt(row[key]) for row in item["files"] for key in ("before", "after"))]


_core_parent_public_selection = core_public_selection


def core_public_selection():
    result = _core_parent_public_selection()
    if CORE_RUNTIME_SELECTION is not None:
        result["runtimeSuccessor"] = core_runtime_public_selection()
    return result


HOST_INITIALIZATION_SELECTION = None


def host_initialization_inputs():
    if HOST_INITIALIZATION_SELECTION is None:
        return []
    item = HOST_INITIALIZATION_SELECTION
    rows = [item["identity"], item["parentCandidate"], item["parentSourceSelection"],
            item["helperSourceSelection"], item["targetAcquisition"], item["targetSymbols"],
            item["targetConfig"], item["nativeAffected"], item["targetLayoutHeaders"],
            item["typedSuConsumerSelection"], *item["targetSourceFiles"],
            *item["imageCaller"].values(), item["helperFile"]["before"], item["helperFile"]["after"]]
    rows = [row for row in rows if isinstance(row, dict)]
    rows += [row[key] for row in item["kernelFiles"] for key in ("before", "after") if row[key]]
    return list(dict.fromkeys(host_deployment_receipt(row) for row in rows))


def select_host_initialization_sources(path, expected_sha256):
    global HOST_INITIALIZATION_SELECTION, SOURCES
    if CORE_RUNTIME_SELECTION is None or HOST_DURABLE_SELECTION is None or HOST_INITIALIZATION_SELECTION is not None:
        raise ValueError("HOST initialization child requires its full CORE/SU/durable parents once")
    path = path.resolve()
    if not path.is_relative_to(ROOT.resolve()) or sha(path) != expected_sha256:
        raise ValueError("HOST initialization child selection identity mismatch")
    selected = json.loads(path.read_text(encoding="utf-8"))
    if selected.get("schema") != "maplesu-host-initialization-source-successor/1" or \
            selected.get("productionWritten") or selected.get("genericHelperAuthorityExpanded") or \
            selected.get("kernelProfile") != "android17-6.18":
        raise ValueError("HOST initialization child status/profile mismatch")
    parent = json.loads(host_deployment_receipt(selected["parentCandidate"]).read_text(encoding="utf-8"))
    if any(parent["effectiveSelection"][k] != selected["parentSourceSelection"][k] for k in ("bytes", "sha256")):
        raise ValueError("HOST initialization child predecessor receipt mismatch")
    previous = json.loads(host_deployment_receipt(selected["parentSourceSelection"]).read_text(encoding="utf-8"))
    actual = [identity(p) for p in SOURCES]
    if actual != previous["native"]:
        raise ValueError("HOST initialization child actual parent native set changed")
    expected = {"manager_su_runtime_target.c", "manager_su_runtime_factory.h",
                "manager_su_runtime_target_profile.h", "manager_installation_state.c"}
    current = {p.name: p for p in SOURCES}
    rows = {Path(row["logicalPath"]).name: row for row in selected["kernelFiles"]}
    if len(rows) != 4 or rows.keys() != expected:
        raise ValueError("HOST initialization child requires the exact four kernel paths")
    replacement = {}
    for name, row in rows.items():
        if row["logicalPath"] != "runtime/ultra/identity/kernel/" + name or \
                (row["before"] is None) != (name not in current) or \
                row["before"] is not None and row["before"] != identity(current[name]):
            raise ValueError("HOST initialization child actual source predecessor mismatch: " + name)
        replacement[name] = host_deployment_receipt(row["after"])
    # All exact receipt/source gates run before replacing the selected source set.
    HOST_INITIALIZATION_SELECTION = dict(selected, identity=identity(path))
    try:
        host_initialization_inputs()
    except Exception:
        HOST_INITIALIZATION_SELECTION = None
        raise
    SOURCES = [replacement.get(p.name, p) for p in SOURCES]
    SOURCES.append(replacement["manager_su_runtime_target_profile.h"])
    HOST_RUNTIME_EXTRA_FUNCTIONS["manager_su_runtime_target"] = (
        *HOST_RUNTIME_EXTRA_FUNCTIONS["manager_su_runtime_target"], "msu_su_runtime_target_bind")
    return HOST_INITIALIZATION_SELECTION


# Independent TaskProfile facts. Neither these APIs nor their layout extend the
# historical CORE lifecycle function set or the 53-word task-state ABI.
TASK_PROFILE_LAYOUT_EXPORTS = {"msu_task_profile_compiled_layout": 26}
TASK_PROFILE_APIS = ("msu_task_profile_flags_supported", "msu_task_profile_check_current", "msu_task_profile_apply_current")
TASK_PROFILE_CALLERS = {
    "msu_task_profile_flags_supported": ("su_profile_apply",),
    "msu_task_profile_check_current": ("su_profile_apply", "su_install_registry"),
    "msu_task_profile_apply_current": ("su_profile_execution",),
}
TASK_PROFILE_PROBE = "manager_task_profile_types"
TASK_PROFILE_MISSING = ("slot63", "rcu", "bitops", "cfi", "syscallTracepoint", "cloneExec")
TASK_PROFILE_SELECTION = None
TASK_PROFILE_TRACE_APIS = ("msu_task_profile_trace_bind", "msu_task_profile_trace_open",
    "msu_task_profile_trace_close", "msu_task_profile_trace_ready")
TASK_PROFILE_TRACE_CALLBACK = "msu_task_profile_sys_enter"
TASK_PROFILE_MARKER_CALLBACKS = ("msu_task_profile_marker_entry", "msu_task_profile_marker_return")
TASK_PROFILE_TRACE_SOURCES = {"manager_task_state.c", "manager_task_state.h",
    "manager_su_runtime_target.c", "manager_su_runtime_factory.c"}


def task_profile_trace_sources():
    pair = task_profile_sources()
    if pair is None:
        return None
    header = pair[1].read_text(encoding="utf-8")
    functions = (*TASK_PROFILE_TRACE_APIS, TASK_PROFILE_TRACE_CALLBACK, *TASK_PROFILE_MARKER_CALLBACKS)
    present = [bool(re.search(r"\b" + name + r"\s*\(", header)) for name in functions]
    if not any(present):
        return None
    if not all(present):
        raise ValueError("TaskProfile trace selected header has a partial private API")
    return {name: core_selected_source(name) for name in sorted(TASK_PROFILE_TRACE_SOURCES)}


def task_profile_selection_inputs():
    if TASK_PROFILE_SELECTION is None:
        return []
    return [core_receipt_path(item) for item in (
        TASK_PROFILE_SELECTION["identity"], TASK_PROFILE_SELECTION["sourceFreeze"],
        *(row[key] for row in TASK_PROFILE_SELECTION["files"] for key in ("before", "after")))]


def core_selected_source(name):
    paths = [path for path in SOURCES if path.name == name]
    if len(paths) != 1:
        raise ValueError("CORE actual selected source is missing or ambiguous: " + name)
    return paths[0]


def select_task_profile_sources(path, expected_sha256):
    global TASK_PROFILE_SELECTION, SOURCES, EXPORTS
    if CORE_SELECTION is None or TASK_PROFILE_SELECTION is not None:
        raise ValueError("TaskProfile successor requires one actual selected CORE parent")
    path = path.resolve()
    if not path.is_relative_to(ROOT.resolve()) or sha(path) != expected_sha256:
        raise ValueError("TaskProfile successor receipt identity mismatch")
    selected = json.loads(path.read_text(encoding="utf-8"))
    if selected.get("schema") != "maplesu-task-profile-source-successor/1" or \
            selected.get("productionWritten") is not False or \
            selected["parentCoreSelection"] != core_public_selection()["identity"]:
        raise ValueError("TaskProfile successor schema/status/CORE parent mismatch")
    frozen = json.loads(core_receipt_path(selected["sourceFreeze"]).read_text(encoding="utf-8"))
    frozen_rows = {row["path"]: row for row in frozen["files"]}
    rows = {row["logicalPath"]: row for row in selected["files"]}
    expected = {"runtime/ultra/identity/kernel/manager_task_state." + suffix for suffix in ("c", "h")}
    trace_paths = {"runtime/ultra/identity/kernel/" + name for name in TASK_PROFILE_TRACE_SOURCES}
    trace_successor = rows.keys() == trace_paths and len(selected["files"]) == len(trace_paths)
    if not trace_successor and (len(selected["files"]) != 2 or rows.keys() != expected):
        raise ValueError("TaskProfile successor requires the exact task pair or four trace lifecycle sources")
    if trace_successor and (RUNTIME_LAYOUT_SELECTION is None or
            selected.get("parentRuntimeLayoutSelection") != RUNTIME_LAYOUT_SELECTION["identity"]):
        raise ValueError("TaskProfile trace successor requires its actual runtime layout predecessor")
    if not trace_successor and selected.get("parentRuntimeLayoutSelection") is not None:
        raise ValueError("TaskProfile legacy pair cannot carry a runtime trace predecessor")
    if len(frozen_rows) != len(frozen["files"]) or not rows.keys() <= frozen_rows.keys():
        raise ValueError("TaskProfile successor source freeze omits selected paths")
    replacements = {}
    for logical, row in rows.items():
        before, after = core_receipt_path(row["before"]), core_receipt_path(row["after"])
        current = core_selected_source(Path(logical).name)
        if before.name != current.name or after.name != current.name or \
                any(identity(before)[key] != identity(current)[key] or
                    identity(after)[key] != frozen_rows[logical][key] for key in ("bytes", "sha256")):
            raise ValueError("TaskProfile successor actual predecessor/freeze mismatch: " + logical)
        replacements[current.name] = after
    previous_sources = SOURCES
    SOURCES = [replacements.get(item.name, item) for item in SOURCES]
    try:
        if task_profile_sources() is None or trace_successor and task_profile_trace_sources() is None:
            raise ValueError("TaskProfile successor does not contain the complete private API/layout/lifecycle")
    except Exception:
        SOURCES = previous_sources
        raise
    TASK_PROFILE_SELECTION = dict(selected, identity=identity(path))
    EXPORTS = tuple(dict.fromkeys((*EXPORTS, *task_profile_exports())))
    return TASK_PROFILE_SELECTION


def task_profile_sources():
    paths = {name: [p for p in SOURCES if p.name == name] for name in ("manager_task_state.c", "manager_task_state.h")}
    if not CORE_SELECTION:
        return None
    if any(len(items) != 1 for items in paths.values()):
        raise ValueError("TaskProfile selected task source/header is missing or ambiguous")
    source, header = (paths[name][0] for name in paths)
    present = ("msu_task_profile_compiled_layout" in source.read_text(encoding="utf-8"),
               "enum msu_task_profile_layout_word" in header.read_text(encoding="utf-8"))
    if not any(present):
        return None
    if not all(present) or any(name not in header.read_text(encoding="utf-8") for name in TASK_PROFILE_APIS):
        raise ValueError("TaskProfile selected two-source API/layout set is incomplete")
    return source, header


def task_profile_exports():
    if task_profile_sources() is None:
        return ()
    trace = task_profile_trace_sources()
    return (*TASK_PROFILE_LAYOUT_EXPORTS, *TASK_PROFILE_APIS,
        *((*TASK_PROFILE_TRACE_APIS, TASK_PROFILE_TRACE_CALLBACK,
           *TASK_PROFILE_MARKER_CALLBACKS, "msu_task_profile_trace_compiled_layout", "msu_task_profile_target",
           "msu_task_profile_marker_target") if trace else ()))


def task_profile_core_evolution():
    result = _task_profile_parent_core_evolution()
    if TASK_PROFILE_SELECTION is None or TASK_PROFILE_SELECTION.get("parentRuntimeLayoutSelection") is not None:
        return result
    replacements = {row["logicalPath"]: row for row in TASK_PROFILE_SELECTION["files"]}
    result["selectedInputs"] = [dict(row, previousTaskProfileInput=row.get("nativeCopyIdentity", row["after"]),
        after=replacements[row["path"]]["after"], nativeCopyIdentity=replacements[row["path"]]["after"])
        if row["path"] in replacements else row for row in result["selectedInputs"]]
    result["taskProfileSuccessor"] = TASK_PROFILE_SELECTION["identity"]
    return result


def task_profile_type_source():
    names = (*TASK_PROFILE_APIS,
        *((*TASK_PROFILE_TRACE_APIS, TASK_PROFILE_TRACE_CALLBACK, *TASK_PROFILE_MARKER_CALLBACKS) if task_profile_trace_sources() else ()))
    return '#include <linux/compiler.h>\n#include "manager_task_state.h"\n' + "".join(
        "typeof(&" + name + ") msu_task_profile_decl_" + str(index) + " __used = " + name + ";\n__ADDRESSABLE(" + name + ");\n"
        for index, name in enumerate(names)) + (task_profile_native_type_source() if task_profile_trace_sources() else "")


def task_profile_layout(elf, table):
    name = "msu_task_profile_compiled_layout"
    symbols = table.get_symbol_by_name(name) if table else None
    if not symbols or len(symbols) != 1 or symbols[0]["st_info"]["type"] != "STT_OBJECT":
        raise ValueError("TaskProfile layout requires one actual STT_OBJECT")
    symbol = symbols[0]
    index = symbol["st_shndx"]
    if not isinstance(index, int) or not 0 < index < elf.num_sections():
        raise ValueError("TaskProfile layout has no actual section index")
    section = elf.get_section(index)
    if section.name != ".msu.task_profile.layout" or section["sh_type"] != "SHT_PROGBITS" or \
            section["sh_flags"] & 7 != 2 or symbol["st_size"] != 104 or \
            sum(s.name == section.name for s in elf.iter_sections()) != 1:
        raise ValueError("TaskProfile layout requires independent 104-byte ALLOC/RO/NX section")
    offset = symbol["st_value"] - section["sh_addr"]
    if offset < 0 or offset % 4 or offset + 104 > section["sh_size"]:
        raise ValueError("TaskProfile layout object is outside its actual section")
    raw = section.data()[offset:offset + 104]
    return {"offset": symbol["st_value"], "bytes": 104,
        "values": [int.from_bytes(raw[at:at + 4], "little") for at in range(0, 104, 4)],
        "section": section.name, "sectionIndex": index,
        "source": "compiled-kernel-headers-not-target-layout-proof"}


def task_profile_input(target, name, selected, header):
    proof = object_provenance(target, name)
    actual = identity(selected)
    if any(actual[key] != proof["sourceIdentity"][key] or actual[key] != identity(target / (name + ".c"))[key]
           for key in ("bytes", "sha256")):
        raise ValueError("TaskProfile actual selected/staged/source provenance mismatch: " + name)
    expected_header = identity(header)
    dependencies = proof["dependencies"]
    for item in (actual, expected_header):
        if not any(all(dep[key] == item[key] for key in ("bytes", "sha256")) for dep in dependencies):
            raise ValueError("TaskProfile actual source/header dependency missing: " + name)
    configs = [dep for dep in dependencies if Path(dep["path"]).name == ".config"]
    if len(configs) != 1:
        raise ValueError("TaskProfile actual .config dependency missing/ambiguous: " + name)
    return proof, configs[0]


def task_profile_layout_record(target, payload):
    source, header = task_profile_sources()
    proof, config = task_profile_input(target, "manager_task_state", source, header)
    obj = target / "manager_task_state.o"
    with obj.open("rb") as stream:
        elf = ELFFile(stream)
        layout = task_profile_layout(elf, elf.get_section_by_name(".symtab"))
    values = object_words(obj, "msu_task_profile_compiled_layout")
    text = re.sub(r"/\*.*?\*/|//[^\n]*", "", header.read_text(encoding="utf-8"), flags=re.S)
    enum = re.search(r"enum\s+msu_task_profile_layout_word\s*\{([^}]*)\}", text, re.S)
    names = re.findall(r"\bMSU_TASK_PROFILE_[A-Z_]+\b", enum.group(1)) if enum else []
    exported = payload["compiledLayouts"]["msu_task_profile_compiled_layout"]
    if len(names) != 27 or names[-1] != "MSU_TASK_PROFILE_LAYOUT_WORDS" or len(set(names)) != 27 or \
            values != layout["values"] or values != exported["values"] or exported["bytes"] != 104 or values[0] != 1:
        raise ValueError("TaskProfile actual enum/object/final 26-word layout mismatch")
    # Family claims carry implementation facts. Per-input Image qualification
    # is derived during actual target binding and is never baked into a family.
    if values[2] != 0 or values[3] not in (0, 1):
        raise ValueError("TaskProfile family claim contains baked target qualification")
    raw = (target / config["archivePath"]).read_text(encoding="utf-8")
    config_values = {key: "y" if re.search(r"^" + key + r"=y$", raw, re.M) else "n" for key in (
        "CONFIG_ARM64", "CONFIG_64BIT", "CONFIG_THREAD_INFO_IN_TASK", "CONFIG_HAVE_SYSCALL_TRACEPOINTS",
        "CONFIG_KRETPROBES", "CONFIG_PREEMPT_RT", "CONFIG_SMP", "CONFIG_QUEUED_RWLOCKS", "CONFIG_PARAVIRT_SPINLOCKS",
        "CONFIG_DEBUG_SPINLOCK", "CONFIG_DEBUG_LOCK_ALLOC", "CONFIG_GENERIC_ENTRY", "CONFIG_CFI", "CONFIG_CFI_CLANG", "CONFIG_CFI_ICALL_NORMALIZE_INTEGERS")}
    if values[4] != (config_values["CONFIG_GENERIC_ENTRY"] == "y") or values[5] != (config_values["CONFIG_THREAD_INFO_IN_TASK"] == "y"):
        raise ValueError("TaskProfile compiled layout/actual .config mismatch")
    implementation = task_profile_trace_sources() is not None
    if values[3] != (implementation and config_values["CONFIG_ARM64"] == "y" and
                     config_values["CONFIG_HAVE_SYSCALL_TRACEPOINTS"] == "y" and config_values["CONFIG_64BIT"] == "y" and
                     config_values["CONFIG_THREAD_INFO_IN_TASK"] == "y"):
        raise ValueError("TaskProfile native implementation/config/selected source mismatch")
    return {"implementationPresent": bool(values[3]),
        "qualificationStage": "per-input-image-binding", "sourceIdentity": identity(source), "headerIdentity": identity(header),
        "sourceSelectionIdentity": core_public_selection()["identity"],
        "objectIdentity": identity(obj), "commandIdentity": identity(target / ".manager_task_state.o.cmd"),
        "dependencyReceiptIdentity": identity(target / "build-dependencies/manager_task_state.json"),
        "dependencies": proof["dependencies"], "configIdentity": config, "config": config_values,
        "layoutWords": 26, "layoutNames": names[:-1], "layoutValues": values,
        "symbol": "msu_task_profile_compiled_layout", "section": ".msu.task_profile.layout",
        "sectionIndex": layout["sectionIndex"], "compiledLayoutSha256": hashlib.sha256(
            b"".join(word.to_bytes(4, "little") for word in values)).hexdigest(), "exportOffset": exported["offset"]}


def collect_task_profile_abi(target, payload, reference):
    record = task_profile_layout_record(target, payload)
    inputs = json.loads((target / "build-inputs.json").read_text(encoding="utf-8"))
    if inputs.get("coreSourceSelection") != core_public_selection() or inputs.get("suSourceSelection") != SU_COMPONENT_SELECTION:
        raise ValueError("TaskProfile actual CORE/SU source generation mismatch")
    if reference["coreLifecycleAbi"]["sourceSelection"] != core_public_selection() or sha(target / "claim.so") != payload["sha256"]:
        raise ValueError("TaskProfile actual reference/source/claim identity mismatch")
    selected = {row["path"]: row.get("nativeCopyIdentity", row["after"]) for row in core_source_evolution()["selectedInputs"]}
    for key, suffix in (("sourceIdentity", "c"), ("headerIdentity", "h")):
        chosen = selected["runtime/ultra/identity/kernel/manager_task_state." + suffix]
        if any(chosen[field] != record[key][field] for field in ("bytes", "sha256")):
            raise ValueError("TaskProfile source is outside selected CORE evolution")
    source, header = task_profile_sources()
    probe = target / (TASK_PROFILE_PROBE + ".c")
    if probe.read_bytes() != task_profile_type_source().encode("utf-8"):
        raise ValueError("TaskProfile declaration probe differs from selected header generation")
    proof, config = task_profile_input(target, TASK_PROFILE_PROBE, probe, header)
    policies = {name: core_cfi_policy(target / ("." + name + ".o.cmd")) for name in ("manager_task_state", TASK_PROFILE_PROBE)}
    policy = policies["manager_task_state"]
    enabled = record["config"]["CONFIG_CFI"] == "y" or record["config"]["CONFIG_CFI_CLANG"] == "y"
    if policy != policies[TASK_PROFILE_PROBE] or policy["kcfiEnabled"] != enabled or enabled and \
            policy["normalizeIntegers"] != (record["config"]["CONFIG_CFI_ICALL_NORMALIZE_INTEGERS"] == "y"):
        raise ValueError("TaskProfile provider/declaration/config CFI policy mismatch")
    if any(config[key] != record["configIdentity"][key] for key in ("bytes", "sha256")):
        raise ValueError("TaskProfile declaration .config generation mismatch")
    mode = "kcfi" if enabled else "none"
    declarations = object_btf(target / (TASK_PROFILE_PROBE + ".o"))
    definitions = object_btf(target / "manager_task_state.o")
    entries, cfi = core_function_entries(target, payload,
        {"manager_management": policy, **policies}, {"manager_task_state": definitions},
        functions={"manager_task_state": TASK_PROFILE_APIS}, probe_name=TASK_PROFILE_PROBE)
    record.update(schema="maplesu-task-profile-abi/1", claimSha256=payload["sha256"],
        apis={}, nativeImports={}, qualification={}, missing=list(TASK_PROFILE_MISSING))
    record["declarationProbe"] = {"sourceIdentity": identity(probe),
        "objectIdentity": identity(target / (TASK_PROFILE_PROBE + ".o")),
        "commandIdentity": identity(target / ("." + TASK_PROFILE_PROBE + ".o.cmd")),
        "dependencyReceiptIdentity": identity(target / "build-dependencies" / (TASK_PROFILE_PROBE + ".json")),
        "dependencies": proof["dependencies"]}
    sources = {path.stem: path for path in su_source_paths() if path.suffix == ".c"}
    for index, name in enumerate(TASK_PROFILE_APIS):
        signature = definitions.function(name)
        if declarations.variable("msu_task_profile_decl_" + str(index)) != "*" + signature or not entries[name]["available"]:
            raise ValueError("TaskProfile actual provider/declaration/final function mismatch: " + name)
        api = record["apis"][name] = {"signature": signature, "entryOffset": entries[name]["entryOffset"], "callers": []}
        if enabled:
            api["typeid"] = cfi["functions"][name]["typeid"]
        for caller in TASK_PROFILE_CALLERS[name]:
            proof, config = task_profile_input(target, caller, sources[caller], header)
            caller_policy = core_cfi_policy(target / ("." + caller + ".o.cmd"))
            if caller_policy != policy or any(config[key] != record["configIdentity"][key] for key in ("bytes", "sha256")):
                raise ValueError("TaskProfile actual caller/header/config/CFI mismatch: " + caller)
            with (target / (caller + ".o")).open("rb") as stream:
                elf = ELFFile(stream)
                undefined = sorted({item.name for item in elf.get_section_by_name(".symtab").iter_symbols()
                    if item.name and item["st_shndx"] == "SHN_UNDEF"})
                relocations = [dict(section=section.name, offset=rel["r_offset"], type=rel["r_info_type"])
                    for section in elf.iter_sections() if isinstance(section, RelocationSection)
                    for rel in section.iter_relocations() if elf.get_section(section["sh_link"]).get_symbol(rel["r_info_sym"]).name == name]
            if name not in undefined or not relocations:
                raise ValueError("TaskProfile actual caller undefined import/relocation missing: " + caller + ":" + name)
            call = {"logicalPath": SU_COMPONENT_ROOT + caller + ".c", "sourceIdentity": identity(sources[caller]),
                "headerIdentity": identity(header), "objectIdentity": identity(target / (caller + ".o")),
                "commandIdentity": identity(target / ("." + caller + ".o.cmd")),
                "dependencyReceiptIdentity": identity(target / "build-dependencies" / (caller + ".json")),
                "dependencies": proof["dependencies"], "signature": signature, "undefinedSymbols": undefined,
                "cfiMode": mode, "normalizeIntegers": policy["normalizeIntegers"], "relocations": relocations}
            if enabled:
                call["typeid"] = api["typeid"]
            api["callers"].append(call)
    if set(TASK_PROFILE_APIS) & set(payload["imports"]):
        raise ValueError("TaskProfile private API became a target kernel import")
    with (target / "manager_task_state.o").open("rb") as stream:
        table = ELFFile(stream).get_section_by_name(".symtab")
        undefined = {item.name for item in table.iter_symbols() if item.name and item["st_shndx"] == "SHN_UNDEF"}
    native = undefined & set(payload["imports"])
    record["nativeImports"] = {name: reference["functionAbi"][name] for name in sorted(native - set(reference.get("dataImports", ())))}
    if task_profile_trace_sources() is not None:
        record["implementation"] = collect_task_profile_implementation(target, payload, record, policy)
    return record


def task_profile_native_type_source():
    return ('\n#include <linux/list.h>\n#include <linux/spinlock.h>\n'
        'const unsigned int msu_task_profile_native_type_facts[4] __used = {\n'
        '    sizeof(struct list_head), __alignof__(struct list_head), sizeof(rwlock_t), __alignof__(rwlock_t),\n};\n'
        'struct list_head *msu_task_profile_native_list_type __used;\n'
        'rwlock_t *msu_task_profile_native_rwlock_type __used;\n')


def task_profile_native_abi(target, record, definitions, declarations):
    """Provider BTF structure facts plus actual same-config compiler alignment."""
    probe = target / (TASK_PROFILE_PROBE + ".o")
    facts = object_words(probe, "msu_task_profile_native_type_facts")
    with probe.open("rb") as stream:
        elf = ELFFile(stream)
        found = elf.get_section_by_name(".symtab").get_symbol_by_name("msu_task_profile_native_type_facts")
        if not found or len(found) != 1 or found[0]["st_info"]["type"] != "STT_OBJECT" or \
                found[0]["st_size"] != 16 or not isinstance(found[0]["st_shndx"], int):
            raise ValueError("TaskProfile actual compiler native type facts are missing/ambiguous")
        symbol = found[0]
        section = elf.get_section(symbol["st_shndx"])
        at = symbol["st_value"] - section["sh_addr"]
        if section["sh_type"] != "SHT_PROGBITS" or section["sh_flags"] & 7 != 2 or \
                at < 0 or at % 4 or at + 16 > section["sh_size"]:
            raise ValueError("TaskProfile compiler native type facts have invalid actual RO/NX bounds")
    if len(facts) != 4 or any(not value or value > 65536 for value in facts) or \
            any(alignment & (alignment - 1) or size % alignment for size, alignment in (facts[:2], facts[2:])):
        raise ValueError("TaskProfile compiler native sizeof/alignment facts are invalid")

    def root(btf, name):
        indexes = [index for index, entry in enumerate(btf.types)
            if entry and entry[1] in (4, 5, 8) and btf.name(entry[0]) == name]
        if not indexes or len({btf.signature(index) for index in indexes}) != 1:
            raise ValueError("TaskProfile native named object type missing/ambiguous: " + name)
        return indexes[0]

    roots = {name: root(definitions, name) for name in ("list_head", "rwlock_t")}
    probe_roots = {name: root(declarations, name) for name in roots}
    for name in roots:
        if definitions.signature(roots[name]) != declarations.signature(probe_roots[name]):
            raise ValueError("TaskProfile provider/declaration native BTF type differs: " + name)
    if [definitions.type_size(roots[name]) for name in roots] != [facts[0], facts[2]]:
        raise ValueError("TaskProfile provider BTF/compiler native sizeof differs")
    types, visited = {}, set()

    def visit(index, active=frozenset()):
        if not index or index in visited:
            return
        if index in active or len(active) >= 32:
            raise ValueError("TaskProfile native by-value BTF type cycle")
        entry = definitions.types[index]
        label, kind, count, value, bitfields, start = entry
        next_active = active | {index}
        if kind in (9, 10, 11, 18):
            visit(value, next_active)
        elif kind == 3:
            visit(definitions.u32(start), next_active)
        elif kind in (4, 5, 8):
            resolved = definitions.resolve(index)
            actual = definitions.types[resolved]
            if actual[1] not in (4, 5):
                visited.add(index)
                return
            name = definitions.name(label)
            members = []
            for ordinal in range(actual[2]):
                pos = actual[5] + ordinal * 12
                member_type = definitions.u32(pos + 4)
                raw = definitions.u32(pos + 8)
                members.append(dict(name=definitions.name(definitions.u32(pos)),
                    offsetBits=raw & 0xffffff if actual[4] else raw,
                    bitfieldBits=raw >> 24 if actual[4] else 0,
                    bytes=definitions.type_size(member_type), signature=definitions.signature(member_type)))
                visit(member_type, next_active)
            if name:
                item = dict(kind={4: "struct", 5: "union", 8: "typedef"}[kind],
                    bytes=definitions.type_size(index), signature=definitions.signature(index), members=members)
                if name in roots:
                    item["alignment"] = facts[1 if name == "list_head" else 3]
                    item["alignmentSource"] = "actual-same-config-declaration-object"
                if name in types and types[name] != item:
                    raise ValueError("TaskProfile native BTF aggregate name is ambiguous: " + name)
                types[name] = item
            if kind == 8:
                visit(value, next_active)
        visited.add(index)

    for index in roots.values():
        visit(index)
    for name, item in types.items():
        if definitions.signature(root(definitions, name)) != item["signature"]:
            raise ValueError("TaskProfile native BTF aggregate root differs: " + name)
    locks = ("CONFIG_SMP", "CONFIG_QUEUED_RWLOCKS", "CONFIG_PARAVIRT_SPINLOCKS",
        "CONFIG_DEBUG_SPINLOCK", "CONFIG_DEBUG_LOCK_ALLOC", "CONFIG_PREEMPT_RT")
    return dict(schema="maplesu-task-profile-native-abi/1", types=types,
        listHeadType="list_head", listHeadBytes=facts[0], listHeadAlignment=facts[1],
        rwlockType="rwlock_t", rwlockBytes=facts[2], rwlockAlignment=facts[3],
        lockConfig={name: record["config"][name] for name in locks},
        providerObjectIdentity=record["objectIdentity"],
        providerCommandIdentity=record["commandIdentity"],
        providerDependencyReceiptIdentity=record["dependencyReceiptIdentity"],
        alignmentFacts=dict(record["declarationProbe"], symbol="msu_task_profile_native_type_facts", values=facts,
            typeSignatures={name: declarations.signature(probe_roots[name]) for name in roots},
            source="actual-provider-BTF-and-same-config-compiler-facts-not-target-layout"),
        internalAlignmentPolicy="not-inferred-from-BTF; complete-by-value-layout-signatures-and-root-compiler-alignment",
        targetQualified=False, deviceQualified=False)


def task_profile_implementation_data(target, name, section_name, size, writable=False):
    """Read exact same-generation object/final bytes; this is not target proof."""
    images = {}
    for key, path in (("object", target / "manager_task_state.o"), ("final", target / "claim.so")):
        with path.open("rb") as stream:
            elf = ELFFile(stream)
            table = elf.get_section_by_name(".symtab")
            found = table.get_symbol_by_name(name) if table else None
            if not found or len(found) != 1 or found[0]["st_info"]["type"] != "STT_OBJECT" or \
                    not isinstance(found[0]["st_shndx"], int):
                raise ValueError("TaskProfile implementation table missing/ambiguous: " + name)
            symbol = found[0]
            section = elf.get_section(symbol["st_shndx"])
            at = symbol["st_value"] - section["sh_addr"]
            if symbol["st_size"] != size or section.name != section_name or \
                    section["sh_type"] != "SHT_PROGBITS" or section["sh_flags"] & 7 != (3 if writable else 2) or \
                    section["sh_addralign"] < 4 or at < 0 or at % 4 or at + size > section["sh_size"] or \
                    sum(item.name == section_name for item in elf.iter_sections()) != 1:
                raise ValueError("TaskProfile implementation table bounds/permissions differ: " + name)
            images[key] = dict(raw=section.data()[at:at + size], offset=symbol["st_value"],
                fileOffset=section["sh_offset"] + at, sectionIndex=symbol["st_shndx"])
    if images["object"]["raw"] != images["final"]["raw"]:
        raise ValueError("TaskProfile same-object/final table bytes differ: " + name)
    final = images["final"]
    return dict(symbol=name, section=section_name, bytes=size, offset=final["offset"],
        fileOffset=final["fileOffset"], sectionIndex=final["sectionIndex"], writable=writable,
        values=[int.from_bytes(final["raw"][at:at + 4], "little") for at in range(0, size, 4)],
        sha256=hashlib.sha256(final["raw"]).hexdigest(),
        source="same-generation-compiled-implementation-not-target-qualification")


def collect_task_profile_implementation(target, payload, record, policy):
    selected = task_profile_trace_sources()
    header = selected["manager_task_state.h"]
    definitions = object_btf(target / "manager_task_state.o")
    declarations = object_btf(target / (TASK_PROFILE_PROBE + ".o"))
    enabled = policy["kcfiEnabled"]
    result = dict(schema="maplesu-task-profile-implementation/1", present=record["implementationPresent"],
        targetQualified=False, deviceQualified=False, qualificationStage="per-input-image-binding",
        sourceSelectionIdentity=record["sourceSelectionIdentity"],
        sourceSuccessor=TASK_PROFILE_SELECTION,
        targetProofSchema="maplesu-task-profile-target-proof/2",
        slot63Contract="fixed-kernelsu-bit63-layout-compatibility",
        sourceSuccessorIdentity=TASK_PROFILE_SELECTION["identity"] if TASK_PROFILE_SELECTION else None,
        objects={}, functions={}, sourceContracts={}, relocations={})
    for source in (path for path in selected.values() if path.suffix == ".c"):
        name = source.stem
        proof, config = task_profile_input(target, name, source, header)
        actual_policy = core_cfi_policy(target / ("." + name + ".o.cmd"))
        if actual_policy != policy or any(config[key] != record["configIdentity"][key] for key in ("bytes", "sha256")):
            raise ValueError("TaskProfile trace lifecycle config/CFI generation differs: " + name)
        result["objects"][name] = dict(logicalPath="runtime/ultra/identity/kernel/" + source.name,
            sourceIdentity=identity(source), objectIdentity=identity(target / (name + ".o")),
            commandIdentity=identity(target / ("." + name + ".o.cmd")),
            dependencyReceiptIdentity=identity(target / "build-dependencies" / (name + ".json")),
            dependencies=proof["dependencies"], configIdentity=config, cfiPolicy=actual_policy)
    functions = (*TASK_PROFILE_TRACE_APIS, TASK_PROFILE_TRACE_CALLBACK, *TASK_PROFILE_MARKER_CALLBACKS)
    entries, cfi = core_function_entries(target, payload,
        {"manager_management": policy, "manager_task_state": policy},
        {"manager_task_state": definitions}, functions={"manager_task_state": functions}, probe_name=TASK_PROFILE_PROBE)
    for index, name in enumerate(functions, len(TASK_PROFILE_APIS)):
        signature = definitions.function(name)
        declared, declaration = retained_probe_declaration(target, declarations, TASK_PROFILE_PROBE,
            "msu_task_profile_decl_" + str(index), name)
        if declared != "*" + signature or not entries[name]["available"]:
            raise ValueError("TaskProfile trace implementation/declaration/final entry differs: " + name)
        item = dict(result["objects"]["manager_task_state"], function=name,
            signature="*" + signature, declarationSignature=declared, declaration=declaration,
            entryOffset=entries[name]["entryOffset"])
        if enabled:
            item.update(typeid=cfi["functions"][name]["typeid"],
                declarationTypeid=cfi["functions"][name]["typeid"])
        result["functions"][name] = item
    callback_signature, callback_reference = retained_probe_declaration(target, definitions,
        "manager_task_state", "msu_task_profile_sys_enter_type", TASK_PROFILE_TRACE_CALLBACK)
    if callback_signature != result["functions"][TASK_PROFILE_TRACE_CALLBACK]["signature"]:
        raise ValueError("TaskProfile native callback retained pointer differs from the actual definition")
    result["callbackRegistrationReference"] = callback_reference
    result["callbackRegistrationReferences"] = {TASK_PROFILE_TRACE_CALLBACK: callback_reference}
    marker_type = definitions.typedef_function("kretprobe_handler_t")
    for name in TASK_PROFILE_MARKER_CALLBACKS:
        signature, retained = retained_probe_declaration(target, definitions, "manager_task_state", name + "_type", name)
        if signature != marker_type or signature != result["functions"][name]["signature"]:
            raise ValueError("TaskProfile native marker callback typedef/definition differs: " + name)
        result["callbackRegistrationReferences"][name] = retained
    if set(functions) & set(payload["imports"]):
        raise ValueError("TaskProfile private trace API became a target kernel import")
    result["relocations"]["targetBind"] = qualification_object_references(
        target / "manager_su_runtime_target.o", ("msu_task_profile_trace_bind",))
    lifecycle = qualification_object_references(target / "manager_su_runtime_factory.o",
        ("msu_task_profile_trace_open", "msu_task_profile_trace_close"))
    expected = {"msu_task_profile_trace_open": {"msu_su_runtime_post_fs_data"},
        "msu_task_profile_trace_close": {"msu_su_runtime_post_fs_data", "msu_su_runtime_close",
            "msu_su_runtime_revoke_install", "msu_su_runtime_discarded"}}
    for name, callers in expected.items():
        observed = {caller for row in lifecycle[name] for caller in row["callers"]}
        if not callers <= observed:
            raise ValueError("TaskProfile trace lifecycle actual caller relocation missing: " + name + ":" + repr(sorted(callers - observed)))
    result["relocations"]["lifecycle"] = lifecycle
    result["relocations"]["markerUidHint"] = qualification_object_references(
        target / "manager_task_state.o", ("msu_su_runtime_trace_mark_task",))
    if "msu_su_runtime_trace_mark_task" in payload["imports"]:
        raise ValueError("TaskProfile private marker UID hint became a target kernel import")
    task_source = selected["manager_task_state.c"].read_text(encoding="utf-8")
    contracts = {
        "msu_task_profile_trace_bind": ('lookup("__tracepoint_sys_enter")', "msu_task_profile_trace_bind_error", "mutex_lock", "mutex_unlock"),
        "msu_task_profile_trace_open": ("msu_task_profile_target_ready()", "tracepoint_probe_register_prio", "msu_task_profile_sys_enter", "INT_MIN", "smp_store_release"),
        "msu_task_profile_trace_close": ("smp_store_release", "tracepoint_probe_unregister", "tracepoint_synchronize_unregister", "msu_task_profile_trace_registered", "msu_task_profile_marker_close()"),
        "msu_task_profile_marker_open": ('IS_ENABLED(CONFIG_KRETPROBES)', 'msu_task_profile_marker_ready()', 'register_kretprobe', 'msu_task_profile_marker_entry', 'msu_task_profile_marker_return', 'raw_spin_lock_irqsave', 'WRITE_ONCE(msu_task_profile_marker.opened, true)', 'msu_task_profile_marker_close()'),
        "msu_task_profile_marker_close": ("unregister_kretprobe", "msu_task_profile_marker_lock", "kfree"),
        "msu_task_profile_marker_update": ('raw_spin_lock_irqsave', 'raw_spin_unlock_irqrestore', 'rcu_read_lock()', 'rcu_read_unlock()', 'rcu_dereference_raw(process_head->next)', 'rcu_dereference_raw(process->next)', 'signal_struct, thread_head', 'rcu_dereference_raw(thread_head->next)', 'rcu_dereference_raw(thread_node->next)', 'MSU_DYN_CONTAINER(thread_node, task_struct, thread_node)', 'msu_task_profile_marker_mode()', 'msu_su_runtime_trace_mark_task(thread)', 'msu_task_profile_work(thread)'),
        "msu_task_profile_marker_return": ("msu_task_profile_marker_update()",),
        "msu_task_profile_marker_entry": ("regs_get_kernel_argument", "msu_task_profile_tracepoint", "msu_task_profile_marker.exit_tracepoint"),
        "msu_task_profile_sys_enter": ("msu_task_profile_trace_ready()", "test_bit(MSU_TASK_PROFILE_DISABLE_ROOT_BIT", "__NR_execve", "__NR_execveat", "copy_from_user_nofault", "WRITE_ONCE(regs->syscallno, -1)"),
        "msu_task_profile_apply_current": ("test_and_set_bit", "rcu_read_lock()", "rcu_read_unlock()", "thread_head", "thread_node", "msu_task_profile_work(thread)")}
    for name, expressions in contracts.items():
        result["sourceContracts"][name] = qualification_source_body(task_source, name, expressions)
    trace = result["traceLayout"] = task_profile_implementation_data(target,
        "msu_task_profile_trace_compiled_layout", ".msu.task_profile.trace-layout", 48)
    words = trace["values"]
    if words[:3] != [1, record["layoutValues"][1], int(record["implementationPresent"])] or words[11] != 0:
        raise ValueError("TaskProfile trace layout schema/native/compat facts differ")
    if record["implementationPresent"]:
        regs = definitions.named_structure_selected("pt_regs", ("regs", "syscallno"))
        args, syscall = regs["members"]["regs"], regs["members"]["syscallno"]
        array = re.fullmatch(r"a([1-9][0-9]*):(.+)", args["signature"])
        if not array or args["bytes"] % int(array.group(1)) or \
                words[3] != regs["bytes"] or words[5] != args["offset"] or \
                words[6] != args["bytes"] // int(array.group(1)) or \
                words[7:9] != [syscall["offset"], syscall["bytes"]] or \
                not words[4] or words[4] & (words[4] - 1) or words[3] % words[4] or \
                words[6] != 8 or not 0 < words[9] < words[10] < 0xffffffff:
            raise ValueError("TaskProfile trace pt_regs facts differ from same-object BTF")
    binding = result["targetBinding"] = task_profile_implementation_data(target,
        "msu_task_profile_target", ".msu.task_profile.target", 136, writable=True)
    if binding["values"][:3] != [0x4d535450, 1, 136] or any(binding["values"][3:]):
        raise ValueError("TaskProfile family reference target binding is not initially unbound")
    members = ("magic", "schema", "bytes", "bound", "proof_mask", "reserved", "root_bit",
        "work_member", "work_bit", "compat", "target_sha256", "config_sha256", "proof_sha256")
    structure = definitions.named_structure_selected("msu_task_profile_target_binding", members)
    offsets = [*range(0, 40, 4), 40, 72, 104]
    widths = [*[4] * 10, 32, 32, 32]
    if structure["bytes"] != 136 or any(structure["members"][name]["offset"] != offset or
            structure["members"][name]["bytes"] != width for name, offset, width in zip(members, offsets, widths)):
        raise ValueError("TaskProfile target binding ABI differs from same-object BTF")
    binding["members"] = {name: dict(offset=offset, bytes=width) for name, offset, width in zip(members, offsets, widths)}
    binding["targetProofSchema"] = result["targetProofSchema"]
    binding["slot63Contract"] = result["slot63Contract"]
    binding["proofBits"] = {name: 1 << index for index, name in enumerate(TASK_PROFILE_MISSING)}
    binding["requiredProofMask"] = 0x3f
    binding["initiallyUnbound"] = True
    marker = result["markerTargetBinding"] = task_profile_implementation_data(target,
        "msu_task_profile_marker_target", ".msu.task_profile.marker-target", 112, writable=True)
    if marker["values"][:3] != [0x4d53544d, 1, 112] or any(marker["values"][3:]):
        raise ValueError("TaskProfile family marker binding is not initially unbound")
    marker_members = ("magic", "schema", "bytes", "bound", "task_bytes", "tasks_offset", "tasks_bytes",
        "tracepoint_bytes", "funcs_offset", "funcs_bytes", "func_bytes", "callback_offset", "callback_bytes",
        "data_offset", "data_bytes", "kretprobe_bytes", "handler_offset", "handler_bytes", "kp_addr_offset",
        "kp_addr_bytes", "maxactive_offset", "maxactive_bytes", "nmissed_offset", "nmissed_bytes",
        "rwlock_bytes", "rwlock_align", "entry_handler_offset", "entry_handler_bytes")
    marker_structure = definitions.named_structure_selected("msu_task_profile_marker_binding", marker_members)
    if marker_structure["bytes"] != 112 or any(marker_structure["members"][name]["offset"] != index * 4 or
            marker_structure["members"][name]["bytes"] != 4 for index, name in enumerate(marker_members)):
        raise ValueError("TaskProfile marker target binding differs from same-object BTF")
    marker["members"] = {name: dict(offset=index * 4, bytes=4) for index, name in enumerate(marker_members)}
    marker["initiallyUnbound"] = True
    result["nativeAbi"] = task_profile_native_abi(target, record, definitions, declarations)
    result["metadataComplete"] = True
    result["targetProofMissing"] = list(TASK_PROFILE_MISSING)
    return result



_task_profile_parent_core_evolution = core_source_evolution
core_source_evolution = task_profile_core_evolution
_task_profile_parent_public_selection = core_public_selection


def core_public_selection():
    result = _task_profile_parent_public_selection()
    if TASK_PROFILE_SELECTION is not None:
        result["taskProfileSuccessor"] = TASK_PROFILE_SELECTION["identity"]
    return result


CORE_CURRENT_TASK_SELECTION = None


def core_current_task_inputs():
    if CORE_CURRENT_TASK_SELECTION is None:
        return []
    item = CORE_CURRENT_TASK_SELECTION
    rows = [item["identity"], item["parentCandidate"], item["parentEffectiveSelection"], item["ownerSelection"],
            *item["receipts"].values(),
            *(row[key] for row in item["files"] for key in ("before", "after")),
            *(row["object"] for row in item["reusedAndroidObjects"])]
    return list(dict.fromkeys(host_deployment_receipt(row) for row in rows))


def select_core_current_task_sources(path, expected_sha256):
    global CORE_CURRENT_TASK_SELECTION
    path = path.resolve()
    if HOST_INITIALIZATION_SELECTION is None or CORE_CURRENT_TASK_SELECTION is not None:
        raise ValueError("Current-task SDK child requires the complete actual HOST initialization parent once")
    if not path.is_relative_to(ROOT.resolve()) or sha(path) != expected_sha256:
        raise ValueError("Current-task SDK child selection identity mismatch")
    selected = json.loads(path.read_text(encoding="utf-8"))
    if selected.get("schema") != "maplesu-host-current-task-source-successor/1" or \
            selected.get("productionWritten") or selected.get("wireChanged") or \
            selected.get("kernelSourcesChanged") or selected.get("helperArbitraryMemoryEnabled"):
        raise ValueError("Current-task SDK child status/source contract mismatch")
    prior = json.loads(host_deployment_receipt(selected["parentEffectiveSelection"]).read_text(encoding="utf-8"))
    parent = json.loads(host_deployment_receipt(selected["parentCandidate"]).read_text(encoding="utf-8"))
    if parent["builder"] != prior["builder"] or [identity(p) for p in SOURCES] != prior["native"] or \
            parent["initializationSelection"] != HOST_INITIALIZATION_SELECTION["identity"]:
        raise ValueError("Current-task SDK child actual HOST parent generation mismatch")
    receipts = selected["receipts"]
    frozen = json.loads(host_deployment_receipt(receipts["SOURCE-FREEZE.json"]).read_text(encoding="utf-8"))
    owner = json.loads(host_deployment_receipt(receipts["SOURCE-SELECTION.json"]).read_text(encoding="utf-8"))
    baseline = json.loads(host_deployment_receipt(receipts["BASELINE.json"]).read_text(encoding="utf-8"))
    if owner["sourceFreeze"]["sha256"] != receipts["SOURCE-FREEZE.json"]["sha256"] or \
            frozen.get("inputCount") != 9 or frozen.get("modified") != 4 or frozen.get("wireChanged"):
        raise ValueError("Current-task SDK child owner freeze mismatch")
    rows = {row["logicalPath"]: row for row in selected["files"]}
    before = {row["path"]: row for row in baseline["files"]}
    after = {row["path"]: row for row in frozen["files"]}
    owner_rows = {row["logicalPath"]: row for row in owner["files"]}
    if len(rows) != 9 or rows.keys() != before.keys() or rows.keys() != after.keys() or rows.keys() != owner_rows.keys():
        raise ValueError("Current-task SDK child exact nine-path set mismatch")
    current = {row["logicalPath"]: row["snapshotIdentity"] for row in CORE_SELECTION["files"]}
    current.update({row["logicalPath"]: row["mergedAfter"] for row in CORE_RUNTIME_SELECTION["files"]})
    for logical, row in rows.items():
        for key, expected in (("before", before[logical]), ("after", after[logical])):
            host_deployment_receipt(row[key])
            if any(row[key][field] != expected[field] for field in ("bytes", "sha256")):
                raise ValueError("Current-task SDK child exact source tuple mismatch: " + logical)
        if any(row["after"][field] != owner_rows[logical]["selected"][field] for field in ("bytes", "sha256")) or \
                row["parentSelected"] != current.get(logical):
            raise ValueError("Current-task SDK child actual CORE source predecessor mismatch: " + logical)
        if logical in current and any(row["before"][field] != current[logical][field]
                                       for field in ("bytes", "sha256")):
            raise ValueError("Current-task SDK child source overlap requires an explicit composition: " + logical)
        if logical == "runtime/ultra/authorization/request_protocol.h" and \
                any(row["after"][field] != identity(PROTOCOL)[field] for field in ("bytes", "sha256")):
            raise ValueError("Current-task SDK child changed the selected R02 protocol")
    proof = json.loads(host_deployment_receipt(receipts["validation"]).read_text(encoding="utf-8"))
    if not proof.get("passedWithinRecordedScope") or proof["sourceFreeze"]["sha256"] != receipts["SOURCE-FREEZE.json"]["sha256"]:
        raise ValueError("Current-task SDK child existing focused evidence mismatch")
    CORE_CURRENT_TASK_SELECTION = dict(selected, identity=identity(path))
    try:
        core_current_task_inputs()
    except Exception:
        CORE_CURRENT_TASK_SELECTION = None
        raise
    return CORE_CURRENT_TASK_SELECTION


HOST_PUBLICATION_SELECTION = None


def host_publication_inputs():
    if HOST_PUBLICATION_SELECTION is None:
        return []
    item = HOST_PUBLICATION_SELECTION
    receipts = [item["identity"], item["parentCandidate"], item["parentEffectiveSelection"],
                item["initializationSelection"], item["currentTaskSelection"],
                item["sourceDiff"], item["validation"], item["nativeObject"],
                *(row[key] for row in item["files"] for key in ("before", "archivedBefore", "after"))]
    return list(dict.fromkeys(host_deployment_receipt(row) for row in receipts))


def select_host_publication_sources(path, expected_sha256):
    global HOST_PUBLICATION_SELECTION, SOURCES
    if CORE_CURRENT_TASK_SELECTION is None or HOST_PUBLICATION_SELECTION is not None:
        raise ValueError("HOST publication child requires the complete current-task parent once")
    path = path.resolve()
    if not path.is_relative_to(ROOT.resolve()) or sha(path) != expected_sha256:
        raise ValueError("HOST publication child selection identity mismatch")
    selected = json.loads(path.read_text(encoding="utf-8"))
    if selected.get("schema") != "maplesu-host-publication-source-successor/1" or \
            selected.get("productionWritten") or selected.get("wireChanged") or \
            selected.get("genericHelperAuthorityExpanded") or selected.get("kernelProfile") != "android17-6.18":
        raise ValueError("HOST publication child contract/profile mismatch")
    parent = json.loads(host_deployment_receipt(selected["parentCandidate"]).read_text(encoding="utf-8"))
    prior = json.loads(host_deployment_receipt(selected["parentEffectiveSelection"]).read_text(encoding="utf-8"))
    if parent["builder"] != prior["builder"] or \
            selected["initializationSelection"] != HOST_INITIALIZATION_SELECTION["identity"] or \
            selected["currentTaskSelection"] != CORE_CURRENT_TASK_SELECTION["identity"] or \
            parent["sdkCurrentTaskSelection"] != CORE_CURRENT_TASK_SELECTION["identity"] or \
            [identity(p) for p in SOURCES] != prior["native"] or \
            [identity(p) for p in SUPPORT_SOURCES] != prior["support"] or \
            [identity(p) for p in su_source_paths()] != prior["su"] or identity(PROTOCOL) != prior["protocol"]:
        raise ValueError("HOST publication child actual parent generation mismatch")
    host_deployment_receipt(parent["sourceFreeze"])
    if len(selected["files"]) != 1 or selected["files"][0]["logicalPath"] != \
            "runtime/ultra/identity/kernel/manager_su_runtime_factory.c":
        raise ValueError("HOST publication child requires exactly the factory C source")
    row = selected["files"][0]
    current = next(p for p in SOURCES if p.name == "manager_su_runtime_factory.c")
    if row["before"] != identity(current) or any(row["before"][key] != row["archivedBefore"][key]
                                               for key in ("bytes", "sha256")):
        raise ValueError("HOST publication child factory predecessor mismatch")
    after = host_deployment_receipt(row["after"])
    proof = json.loads(host_deployment_receipt(selected["validation"]).read_text(encoding="utf-8"))
    if proof["variants"]["after"]["firstFailedLine"] or \
            not proof["variants"]["before"]["firstFailedLine"] or \
            proof["variants"]["before"]["prematurePublicationAdmissions"] != 5 or \
            proof["variants"]["before"]["prematureFinalCheckAdmissions"] != 1 or \
            any(row["after"][key] != proof["variants"]["after"]["actualSource"][key] or
                row["after"][key] != proof["arm64Source"][key] or
                row["before"][key] != proof["variants"]["before"]["actualSource"][key] or
                selected["nativeObject"][key] != proof["arm64Object"][key]
                for key in ("bytes", "sha256")):
        raise ValueError("HOST publication child focused evidence differs from its source")
    HOST_PUBLICATION_SELECTION = dict(selected, identity=identity(path))
    try:
        host_publication_inputs()
    except Exception:
        HOST_PUBLICATION_SELECTION = None
        raise
    SOURCES = [after if p.name == current.name else p for p in SOURCES]
    return HOST_PUBLICATION_SELECTION


HOST_SYNC_SELECTION = None


def host_sync_inputs():
    if HOST_SYNC_SELECTION is None:
        return []
    item = HOST_SYNC_SELECTION
    receipts = [item["identity"], item["parentCandidate"], item["parentEffectiveSelection"],
                item["parentSdkInventory"], item["factorySelection"], item["coreSuccessorSelection"],
                item["sourceDiff"], *(row[key] for row in item["files"] for key in
                ("before", "archivedBefore", "after", "upstreamBefore", "upstreamAfter"))]
    return list(dict.fromkeys(host_deployment_receipt(row) for row in receipts))


def select_host_sync_sources(path, expected_sha256):
    global HOST_SYNC_SELECTION, SOURCES, PROTOCOL
    if HOST_PUBLICATION_SELECTION is None or HOST_SYNC_SELECTION is not None:
        raise ValueError("HOST sync child requires its complete publication parent once")
    path = path.resolve()
    if not path.is_relative_to(ROOT.resolve()) or sha(path) != expected_sha256:
        raise ValueError("HOST sync child selection identity mismatch")
    selected = json.loads(path.read_text(encoding="utf-8"))
    if selected.get("schema") != "maplesu-host-runtime-sync-source-successor/1" or \
            selected.get("kernelProfile") != "android17-6.18" or \
            any(selected.get(key) for key in ("productionWritten", "publicWireLayoutChanged",
                "genericHelperAuthorityExpanded", "ordinaryAppPolicyOrMountAdded")):
        raise ValueError("HOST sync child contract/profile mismatch")
    prior = json.loads(host_deployment_receipt(selected["parentEffectiveSelection"]).read_bytes())
    parent = json.loads(host_deployment_receipt(selected["parentCandidate"]).read_bytes())
    sdk = json.loads(host_deployment_receipt(selected["parentSdkInventory"]).read_bytes())
    if parent["effectiveSelection"] != selected["parentEffectiveSelection"] or \
            parent["builder"] != prior["builder"] or \
            prior["hostPublicationSourceEvolution"] != HOST_PUBLICATION_SELECTION or \
            selected["coreSuccessorSelection"] != CORE_RUNTIME_SELECTION["identity"] or \
            [identity(p) for p in SOURCES] != prior["native"] or \
            [identity(p) for p in SUPPORT_SOURCES] != prior["support"] or \
            [identity(p) for p in su_source_paths()] != prior["su"] or identity(PROTOCOL) != prior["protocol"]:
        raise ValueError("HOST sync child actual parent generation mismatch")
    expected = {"runtime/ultra/identity/kernel/" + name for name in
                ("manager_su_runtime_factory.c", "manager_su_runtime_factory.h",
                 "su_observed_install_source.c", "su_observed_install_source.h",
                 "manager_management.c", "manager_management.h")}
    expected.update(("runtime/ultra/authorization/request_protocol.h",
        "runtime/maplesu-sdk/sdk/include/maplesu_request_private.h",
        "runtime/maplesu-sdk/sdk/src/kernel_request/module_base_kernel_request.cpp"))
    rows = {row["logicalPath"]: row for row in selected["files"]}
    if len(rows) != 9 or len(selected["files"]) != 9 or rows.keys() != expected:
        raise ValueError("HOST sync child exact nine-path set mismatch")
    current = {"runtime/ultra/identity/kernel/" + p.name: identity(p) for p in SOURCES}
    current.update({row["logicalPath"]: row["selected"] for row in sdk["files"]})
    current["runtime/ultra/authorization/request_protocol.h"] = identity(PROTOCOL)
    factory = json.loads(host_deployment_receipt(selected["factorySelection"]).read_bytes())
    factory_rows = {row["logicalPath"]: row for row in factory["files"]}
    replacement = {}
    for logical, row in rows.items():
        if row["before"] != identity(host_deployment_receipt(current[logical])) or any(row["before"][key] != row["archivedBefore"][key]
                                                      for key in ("bytes", "sha256")):
            raise ValueError("HOST sync child predecessor differs: " + logical)
        after = host_deployment_receipt(row["after"])
        if any(row["after"][key] != row["upstreamAfter"][key] or
               row["before"][key] != row["upstreamBefore"][key] for key in ("bytes", "sha256")):
            raise ValueError("HOST sync child source provenance differs: " + logical)
        if logical in factory_rows and (host_deployment_receipt(row["upstreamAfter"]) != host_deployment_receipt(factory_rows[logical]["after"]) or
                host_deployment_receipt(row["upstreamBefore"]) != host_deployment_receipt(factory_rows[logical]["before"])):
            raise ValueError("HOST sync child factory lineage differs: " + logical)
        if re.search(rb"^(?:<<<<<<<|=======|>>>>>>>)", after.read_bytes(), re.M):
            raise ValueError("HOST sync child unresolved markers: " + logical)
        replacement[Path(logical).name] = after
    # Do not publish partial selection if any retained receipt/source fails.
    HOST_SYNC_SELECTION = dict(selected, identity=identity(path))
    try:
        host_sync_inputs()
    except Exception:
        HOST_SYNC_SELECTION = None
        raise
    SOURCES = [replacement.get(p.name, p) for p in SOURCES]
    PROTOCOL = replacement["request_protocol.h"]
    HOST_RUNTIME_EXTRA_FUNCTIONS["manager_su_runtime_factory"] = tuple(dict.fromkeys((
        *HOST_RUNTIME_EXTRA_FUNCTIONS["manager_su_runtime_factory"],
        "msu_su_runtime_sync", "msu_su_runtime_sync_held", "msu_su_runtime_readback_acquire",
        "msu_su_runtime_readback_record", "msu_su_runtime_readback_put")))
    HOST_RUNTIME_EXTRA_FUNCTIONS["su_observed_install_source"] = tuple(dict.fromkeys((
        *HOST_RUNTIME_EXTRA_FUNCTIONS["su_observed_install_source"], "msu_su_observed_sync")))
    return HOST_SYNC_SELECTION


HOST_RETIREMENT_SELECTION = None


def host_retirement_inputs():
    if HOST_RETIREMENT_SELECTION is None:
        return []
    item = HOST_RETIREMENT_SELECTION
    return list(dict.fromkeys(host_deployment_receipt(row) for row in
        (item["identity"], item["parentCandidate"], item["parentEffectiveSelection"], item["sourceDiff"],
         *(row[key] for row in item["files"] for key in ("before", "archivedBefore", "after")))))


def select_host_retirement_sources(path, expected_sha256):
    global HOST_RETIREMENT_SELECTION, SOURCES, SUPPORT_SOURCES
    if HOST_SYNC_SELECTION is None or HOST_RETIREMENT_SELECTION is not None:
        raise ValueError("HOST retirement requires its complete sync parent once")
    path = path.resolve()
    if not path.is_relative_to(ROOT.resolve()) or sha(path) != expected_sha256:
        raise ValueError("HOST retirement source selection identity mismatch")
    selected = json.loads(path.read_bytes())
    if selected.get("schema") != "maplesu-host-retirement-source-successor/1" or \
            selected.get("kernelProfile") != "android17-6.18" or any(selected.get(key) for key in
                ("productionWritten", "publicWireChanged", "readyRetirementEnabled")):
        raise ValueError("HOST retirement source contract mismatch")
    parent = json.loads(host_deployment_receipt(selected["parentCandidate"]).read_bytes())
    prior = json.loads(host_deployment_receipt(selected["parentEffectiveSelection"]).read_bytes())
    if parent["effectiveSelection"] != selected["parentEffectiveSelection"] or \
            parent["builder"] != prior["builder"] or prior["hostSyncSourceEvolution"] != HOST_SYNC_SELECTION or \
            [identity(p) for p in SOURCES] != prior["native"] or \
            [identity(p) for p in SUPPORT_SOURCES] != prior["support"] or \
            [identity(p) for p in su_source_paths()] != prior["su"] or identity(PROTOCOL) != prior["protocol"]:
        raise ValueError("HOST retirement actual parent generation differs")
    rows = {Path(row["logicalPath"]).name: row for row in selected["files"]}
    if len(rows) != 4 or len(selected["files"]) != 4 or rows.keys() != {
            "manager_claim_bootstrap.c", "manager_resident_abi.h", "manager_resident_host.c", "manager_resident_host.h"}:
        raise ValueError("HOST retirement requires exact paired Image/native four-path set")
    current = {p.name: identity(p) for p in [*SOURCES, *SUPPORT_SOURCES]}
    replacement = {}
    for name, row in rows.items():
        if row["logicalPath"] != "runtime/ultra/identity/kernel/" + name or row["before"] != current[name] or \
                any(row["before"][key] != row["archivedBefore"][key] for key in ("bytes", "sha256")):
            raise ValueError("HOST retirement actual source predecessor differs: " + name)
        replacement[name] = host_deployment_receipt(row["after"])
    HOST_RETIREMENT_SELECTION = dict(selected, identity=identity(path))
    try:
        host_retirement_inputs()
    except Exception:
        HOST_RETIREMENT_SELECTION = None
        raise
    SOURCES = [replacement.get(p.name, p) for p in SOURCES]
    SUPPORT_SOURCES = [replacement.get(p.name, p) for p in SUPPORT_SOURCES]
    return HOST_RETIREMENT_SELECTION



RUNTIME_LAYOUT_SELECTION = None
RUNTIME_SU_SOURCE_PATHS = None
RUNTIME_SU_CURRENT_INPUTS = {name: ROOT / SU_COMPONENT_ROOT / name for name in
    ("su_install_registry.c", "su_install_registry.h", "su_profile_apply.c", "su_profile_apply.h",
     "su_profile_execution.c", "su_profile_execution.h", "su_profile_namespace.c", "su_profile_namespace.h")}
RUNTIME_SU_CURRENT_SHA256 = {
    "su_install_registry.c": "6d84c1b94869c1381c0d35d75732a835ee2a5c5e138b34e4243caadeec44d280",
    "su_install_registry.h": "cd3157c98f0aad35a625259d219281be38ca8f4962e35aa3aed3d35618d9f14b",
    "su_profile_apply.c": "8f21982f62a402339227c95a5d9d91d3e69dc36638297a28f91ed227d907bc46",
    "su_profile_apply.h": "cc21e93424dc29c97df3dd79e91b64ba100a38414a5345c029e7cfd44add92fc",
    "su_profile_execution.c": "2452c98074edad9d00e6328c7c61233dbb1f4f6fcdb3a545baf5abce36234426",
    "su_profile_execution.h": "de1b2da01000bac3435abd9dfee9151d30057d3f98cbb35df9623a2ce927ed3a",
    "su_profile_namespace.h": "a6aad26cd4fa0f92f98ca7dfc22b25d7ed58edc854eff6606d283a5a1f9d3a8f",
    "su_profile_namespace.c": "c03abe93caf54d3e13d1eddde3e3e62424234b53402b4f1029ba420b2990d3b1",
}
RUNTIME_ADDITIONAL_OBJECTS = ("manager_early_fixed", "manager_early_oem", "manager_early_root",
    "manager_early_selinux", "manager_early_runtime_factory", "manager_early_root_backend",
    "manager_early_selinux_backend", "su_nonroot_transition", "su_nonroot_transition_work",
    "su_nonroot_mount_source", "native_lookup", "su_exec_session", "boot_events", "manager_ksu_compat")
RUNTIME_ADDITIONAL_INPUTS = {name: SOURCE / name for name in
    ("manager_dynamic_layout.c", "manager_dynamic_layout.h", *(unit + suffix
     for unit in RUNTIME_ADDITIONAL_OBJECTS if unit not in ("native_lookup", "su_exec_session", "boot_events")
     for suffix in (".c", ".h")))}
RUNTIME_ADDITIONAL_INPUTS.update({name: ROOT / "runtime/ultra/boot/init-bridge" / name for name in
    ("native_lookup.c", "native_lookup.h", "native_cfi.h", "boot_events.c", "boot_events.h", "preinit.h")})
RUNTIME_ADDITIONAL_INPUTS.update({name: ROOT / "runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/kernel" / name
    for name in ("su_exec_session.c", "su_exec_session.h")})
RUNTIME_ADDITIONAL_INPUTS["su_session_receipt.h"] = ROOT / "runtime/maplesu-sdk/sdk/src/payload/su/su_session_receipt.h"
RUNTIME_HEADER_ROOTS = tuple(ROOT / path for path in (
    "runtime/ultra/identity/kernel", "runtime/ultra/authorization",
    "runtime/maplesu-sdk/sdk/src/module/su_redirect_tu/kernel",
    "runtime/maplesu-sdk/sdk/src/payload/su", "runtime/ultra/boot/init-bridge"))


def runtime_local_include_closure(roots, available):
    """Resolve real quoted project includes; generated/kernel headers stay native."""
    known = {path.name: path for path in available}
    queue, visited, dependencies = list(roots), set(), []
    while queue:
        source = queue.pop(0)
        if source in visited:
            continue
        visited.add(source)
        text = re.sub(r"/\*.*?\*/|//[^\n]*", "", source.read_text(encoding="utf-8-sig"), flags=re.S)
        for include in re.findall(r'^\s*#\s*include\s*"([^"]+)"', text, re.M):
            name = Path(include).name
            if name in ("msu_resolver_build_config.h", "objsec.h"):
                continue
            header = known.get(name)
            if header is None:
                candidates = [directory / name for directory in RUNTIME_HEADER_ROOTS
                              if (directory / name).is_file()]
                if len(candidates) != 1:
                    raise ValueError("Runtime quoted include missing/ambiguous: " + str(source) + ":" + include)
                header = known[name] = candidates[0]
            dependencies.append(dict(source=identity(source), include=include, header=identity(header)))
            queue.append(header)
    return known, dependencies


def runtime_layout_selection_inputs():
    if RUNTIME_LAYOUT_SELECTION is None:
        return []
    selected = RUNTIME_LAYOUT_SELECTION
    return list(dict.fromkeys(core_receipt_path(row) for row in
        [selected["identity"], *selected["predecessorInputs"]["native"],
         *selected["predecessorInputs"]["support"], *selected["predecessorInputs"].get("private", []),
         *selected["predecessorInputs"].get("su", []),
         selected["predecessorInputs"]["protocol"], selected["predecessorInputs"]["frame"],
         *(item[key] for item in selected["files"] for key in ("before", "after")
           if item[key] is not None)]))


def select_runtime_layout_sources(path, expected_sha256):
    """Apply an exact native/support/protocol successor with a closed local include set."""
    global RUNTIME_LAYOUT_SELECTION, RUNTIME_SU_SOURCE_PATHS, SOURCES, SUPPORT_SOURCES, EXPORTS, PUBLICATION_PRIVATE_HEADERS, PROTOCOL, FRAME
    path = path.resolve()
    if RUNTIME_LAYOUT_SELECTION is not None or not path.is_relative_to(ROOT.resolve()) or sha(path) != expected_sha256:
        raise ValueError("Runtime layout source selection identity mismatch")
    selected = json.loads(path.read_text(encoding="utf-8-sig"))
    if selected.get("schema") != "maplesu-claim-runtime-layout-source-selection/1" or \
            selected.get("sourceOwnerFrozen") is not True or selected.get("targetQualified") is not False:
        raise ValueError("Runtime layout source selection is not a frozen, unqualified native successor")
    previous = selected["predecessorInputs"]
    actual = dict(native=[identity(item) for item in SOURCES], support=[identity(item) for item in SUPPORT_SOURCES],
        private=[identity(item) for item in PUBLICATION_PRIVATE_HEADERS], protocol=identity(PROTOCOL), frame=identity(FRAME))
    prior_su = su_source_paths()
    if "su" in previous:
        actual["su"] = [identity(item) for item in prior_su]
    if previous != actual or selected.get("additionalObjects") != list(RUNTIME_ADDITIONAL_OBJECTS):
        raise ValueError("Runtime successor differs from its actual native/support/protocol predecessor")
    rows = {row["logicalPath"]: row for row in selected["files"]}
    if not rows or len(rows) != len(selected["files"]):
        raise ValueError("Runtime layout successor has duplicate/empty source paths")
    original = {}
    for source in [*SOURCES, *SUPPORT_SOURCES, *PUBLICATION_PRIVATE_HEADERS, PROTOCOL, FRAME, *prior_su]:
        if source.name in original and identity(original[source.name]) != identity(source):
            raise ValueError("Runtime predecessor has ambiguous native/support input: " + source.name)
        original[source.name] = source
    required = set(RUNTIME_ADDITIONAL_INPUTS)
    replacements, added_headers = {}, set()
    for logical, row in rows.items():
        name = Path(logical).name
        valid_logical = {directory.relative_to(ROOT).as_posix() + "/" + name for directory in RUNTIME_HEADER_ROOTS}
        if name == "kprobe_text_owner.h":
            valid_logical.add("runtime/ultra/identity/kernel/native-text/" + name)
        if logical not in valid_logical or Path(name).suffix not in (".c", ".h", ".inc") or name in replacements:
            raise ValueError("Runtime successor is outside its exact project input set: " + logical)
        after = core_receipt_path(row["after"])
        if after.name != name:
            raise ValueError("Runtime successor source name differs: " + logical)
        if name in original:
            if row["before"] != identity(original[name]):
                raise ValueError("Runtime successor native/support predecessor changed: " + logical)
            if original[name] in prior_su and ("su" not in previous or
                    name not in RUNTIME_SU_CURRENT_INPUTS or logical != SU_COMPONENT_ROOT + name or
                    row["after"]["sha256"] != RUNTIME_SU_CURRENT_SHA256[name]):
                raise ValueError("Runtime SU source successor is not an exact reviewed current API pair")
        elif row["before"] is not None or name not in required and Path(name).suffix not in (".h", ".inc"):
            raise ValueError("Runtime successor addition is not a required TU or local header: " + logical)
        elif name not in required:
            added_headers.add(name)
        replacements[name] = after
    if not required.issubset(replacements):
        raise ValueError("Runtime source, header and additional object set is incomplete")
    native = [replacements.get(item.name, item) for item in SOURCES]
    native += [after for name, after in replacements.items() if name not in original]
    support = [replacements.get(item.name, item) for item in SUPPORT_SOURCES]
    private = [replacements.get(item.name, item) for item in PUBLICATION_PRIVATE_HEADERS]
    selected_su = [replacements.get(item.name, item) for item in prior_su]
    protocol, frame = replacements.get(PROTOCOL.name, PROTOCOL), replacements.get(FRAME.name, FRAME)
    available = [*native, *support, *private, protocol, frame, *selected_su]
    known, closure = runtime_local_include_closure(available, available)
    copied = {item.name for item in available}
    if set(known) - copied or added_headers - {Path(item["header"]["path"]).name for item in closure}:
        raise ValueError("Runtime successor quoted include closure is not completely selected")
    SOURCES, SUPPORT_SOURCES, PUBLICATION_PRIVATE_HEADERS = native, support, private
    PROTOCOL, FRAME = protocol, frame
    RUNTIME_SU_SOURCE_PATHS = selected_su
    RUNTIME_LAYOUT_SELECTION = dict(selected, identity=identity(path), includeClosure=closure)
    if "su_profile_execution.c" in replacements:
        # The reviewed current implementation shares the real held stages;
        # prove both public paths into that same compiled body and its calls.
        SU_COMPONENT_RELOCATIONS["su_profile_execution"] = {
            "msu_su_apply_current_detailed": ("msu_su_lease_begin", "msu_su_apply_held_stages", "msu_su_lease_end"),
            "msu_su_apply_held_detailed": ("msu_su_lease_validate", "msu_su_apply_held_stages"),
            "msu_su_apply_held_stages": ("msu_su_profile_apply_held", "msu_task_state_release_current",
                "msu_task_profile_apply_current", "msu_su_lease_validate", "msu_su_profile_namespace_apply_held"),
        }
        apply_su_component_runtime_layout_abi(replacements)
    EXPORTS = tuple(dict.fromkeys((*EXPORTS, "msu_runtime_layout", "msu_su_session_abi_words",
                                 "msu_boot_events_abi_words", "msu_ksu_compat_layout", *task_profile_exports())))
    if runtime_profiles_enabled():
        EXPORTS = tuple(dict.fromkeys((*EXPORTS, "msu_su_runtime_profiles")))
    return RUNTIME_LAYOUT_SELECTION


_runtime_parent_su_source_paths = su_source_paths


def su_source_paths():
    return _runtime_parent_su_source_paths() if RUNTIME_SU_SOURCE_PATHS is None else list(RUNTIME_SU_SOURCE_PATHS)


def runtime_layout_slots():
    header = selected_native_source("manager_dynamic_layout.h").read_text(encoding="utf-8-sig")
    header = re.sub(r"/\*.*?\*/|//[^\n]*", "", header, flags=re.S)
    macro = re.search(r"^#define MSU_RUNTIME_LAYOUT_FIELDS\(X\)\\\s*\n((?:[^\n]*\\\s*\n)*[^\n]*)", header, re.M)
    if macro is None:
        # Whitespace before the continuation is conventional and is preserved.
        macro = re.search(r"^#define MSU_RUNTIME_LAYOUT_FIELDS\(X\)\s+\\\s*\n((?:[^\n]*\\\s*\n)*[^\n]*)", header, re.M)
    slots = re.findall(r"\bX\(([A-Za-z_]\w*),\s*([A-Za-z_]\w*)\)", macro.group(1)) if macro else []
    if not slots or len(slots) != len(set(slots)):
        raise ValueError("Runtime layout header has missing/duplicate field slots")
    return slots


def runtime_layout_aliases(slots):
    header = selected_native_source("manager_dynamic_layout.h").read_text(encoding="utf-8-sig")
    header = re.sub(r"/\*.*?\*/|//[^\n]*", "", header, flags=re.S)
    macro = re.search(r"^#define MSU_RUNTIME_LAYOUT_ALIASES\(X\)\s+\\\s*\n((?:[^\n]*\\\s*\n)*[^\n]*)", header, re.M)
    rows = re.findall(r"\bX\(([A-Za-z_]\w*),\s*([A-Za-z_]\w*),\s*([A-Za-z_]\w*)\)",
                      macro.group(1)) if macro else []
    aliases = {(owner, key): member for owner, key, member in rows}
    if len(rows) != len(aliases) or aliases != {("vfsmount", "mnt_user_ns"): "mnt_userns"} or \
            not set(aliases).issubset(slots):
        raise ValueError("Runtime layout member alias differs from its qualified source contract")
    return aliases


def runtime_layout_record(path, elf, defined):
    name = "msu_runtime_layout"
    symbol = defined.get(name)
    if symbol is None or symbol["st_info"]["type"] != "STT_OBJECT" or not isinstance(symbol["st_shndx"], int):
        raise ValueError("Runtime layout requires one actual exported object")
    section = elf.get_section(symbol["st_shndx"])
    offset, size = symbol["st_value"] - section["sh_addr"], symbol["st_size"]
    if section.name != ".msu.runtime.layout" or section["sh_type"] != "SHT_PROGBITS" or \
            section["sh_flags"] & 7 != 3 or section["sh_addralign"] < 4 or offset < 0 or offset % 4 or \
            offset + size > section["sh_size"] or size % 4 or \
            sum(item.name == section.name for item in elf.iter_sections()) != 1:
        raise ValueError("Runtime layout must be a bounded independent writable/NX table")
    raw = section.data()[offset:offset + size]
    words = [int.from_bytes(raw[at:at + 4], "little") for at in range(0, size, 4)]
    slots = runtime_layout_slots()
    if size != 24 + 12 * len(slots) or words[:6] != [0x4d53554c, 1, size, len(slots), 0, 0]:
        raise ValueError("Runtime layout header/slot schema mismatch")
    target, obj = path.parent, path.parent / "manager_dynamic_layout.o"
    if object_words(obj, name) != words:
        raise ValueError("Runtime layout final bytes differ from their same-build object")
    alignments = object_words(obj, "msu_runtime_layout_alignments")
    if len(alignments) != len(slots):
        raise ValueError("Runtime layout independent alignment report has a different slot count")
    provenance = object_provenance(target, "manager_dynamic_layout")
    aliases = runtime_layout_aliases(slots)
    expected_aliases = b"".join((owner + "." + key + "=" + owner + "." + member).encode("ascii") + b"\0"
                                for (owner, key), member in aliases.items()) + b"\0"
    with obj.open("rb") as stream:
        native = ELFFile(stream)
        matches = native.get_section_by_name(".symtab").get_symbol_by_name("msu_runtime_layout_aliases")
        if not matches or len(matches) != 1 or matches[0]["st_info"]["type"] != "STT_OBJECT" or \
                not isinstance(matches[0]["st_shndx"], int):
            raise ValueError("Runtime member aliases lack one actual object symbol")
        alias_symbol = matches[0]
        alias_section = native.get_section(alias_symbol["st_shndx"])
        alias_at, alias_size = alias_symbol["st_value"] - alias_section["sh_addr"], alias_symbol["st_size"]
        if alias_section.name != ".msu.runtime.aliases" or alias_section["sh_type"] != "SHT_PROGBITS" or \
                alias_at < 0 or alias_size != len(expected_aliases) or \
                alias_at + alias_size > alias_section["sh_size"] or \
                alias_section.data()[alias_at:alias_at + alias_size] != expected_aliases:
            raise ValueError("Runtime member aliases differ from their same-build source declaration")
    btf, fields = object_btf(obj), []
    for slot, (owner, member) in enumerate(slots):
        at = 6 + slot * 3
        start, width, owner_bytes = words[at:at + 3]
        alignment, signature = alignments[slot], None
        actual_member = member
        if (start, width, owner_bytes) == (0xffffffff, 0, 0):
            if alignment:
                raise ValueError("Absent runtime layout field has nonzero alignment")
        else:
            if not width or not owner_bytes or start > owner_bytes or width > owner_bytes - start or \
                    not alignment or alignment > 4096 or alignment & (alignment - 1) or start % alignment:
                raise ValueError("Runtime layout field has invalid compiled bounds/alignment")
            actual_member = aliases.get((owner, member), member)
            layout = btf.named_structure_selected(owner, (actual_member,))
            actual = layout["members"][actual_member]
            if actual["offset"] != start or actual["bytes"] != width or layout["bytes"] != owner_bytes:
                raise ValueError("Runtime layout field differs from its actual object BTF: " + owner + "." + member)
            signature = actual["signature"]
        fields.append(dict(slot=slot, key=owner + "." + member, type=owner, member=actual_member,
            offsetIndex=at, widthIndex=at + 1, ownerSizeIndex=at + 2, compiledOffset=start,
            compiledBytes=width, compiledOwnerBytes=owner_bytes, signature=signature, alignment=alignment))
    sources = {"runtime/ultra/identity/kernel/" + source.name: identity(source)
               for source in SOURCES if source.name in ("manager_dynamic_layout.c", "manager_dynamic_layout.h")}
    for source in sources.values():
        if not any(all(dependency[key] == source[key] for key in ("bytes", "sha256"))
                   for dependency in provenance["dependencies"]):
            raise ValueError("Runtime layout object omitted its selected source/header dependency")
    configs = [item for item in provenance["dependencies"] if Path(item["path"]).name == ".config"]
    if len(configs) != 1:
        raise ValueError("Runtime layout actual configuration dependency is missing/ambiguous")
    return dict(schemaVersion=1, symbol=name, offset=symbol["st_value"], bytes=size,
        section=section.name, sectionFlags=section["sh_flags"], values=words, fields=fields,
        compiledSha256=hashlib.sha256(raw).hexdigest(), claimSha256=sha(path),
        sourceSelectionIdentity=RUNTIME_LAYOUT_SELECTION["identity"], sourceIdentities=sources,
        objectIdentity=identity(obj), commandIdentity=identity(target / ".manager_dynamic_layout.o.cmd"),
        dependencyReceiptIdentity=identity(target / "build-dependencies/manager_dynamic_layout.json"),
        dependencies=provenance["dependencies"], configIdentity=configs[0])


def session_cfi_policy(command):
    tokens = command.read_text(encoding="utf-8").splitlines()[0].split()
    enabled = set()
    for token in tokens:
        if token.startswith(("-fsanitize=", "-fno-sanitize=")):
            values = set(token.split("=", 1)[1].split(","))
            enabled = enabled | values if token.startswith("-fsanitize=") else enabled - values
    return dict(mode="kcfi" if "kcfi" in enabled else "legacy-lto-cfi" if "cfi" in enabled else "none",
        normalizeIntegers=any(token in ("-fsanitize-cfi-icall-normalize-integers",
            "-fsanitize-cfi-icall-experimental-normalize-integers") for token in tokens))


def session_abi_facts(obj, elf, cfi_policy):
    """Same-TU facts; callers bind their own raw claim or LKM provenance."""
    name = "msu_su_session_abi_words"
    if cfi_policy.get("mode") not in ("kcfi", "none", "legacy-lto-cfi") or \
            not isinstance(cfi_policy.get("normalizeIntegers"), bool):
        raise ValueError("Session object has an invalid actual CFI policy")

    def actual_symbol(image, label, kind):
        table = image.get_section_by_name(".symtab") or image.get_section_by_name(".dynsym")
        matches = table.get_symbol_by_name(label) if table else None
        if not matches or len(matches) != 1 or matches[0]["st_info"]["type"] != kind or \
                not isinstance(matches[0]["st_shndx"], int):
            raise ValueError("Session actual symbol missing/ambiguous: " + label)
        symbol = matches[0]
        section = image.get_section(symbol["st_shndx"])
        at, size = symbol["st_value"] - section["sh_addr"], symbol["st_size"]
        if at < 0 or size <= 0 or at + size > section["sh_size"]:
            raise ValueError("Session actual symbol has invalid bounds: " + label)
        return symbol, section, at, size

    symbol, section, at, size = actual_symbol(elf, name, "STT_OBJECT")
    if section.name != ".msu.session.abi" or section["sh_type"] != "SHT_PROGBITS" or \
            section["sh_flags"] & 7 != 2 or section["sh_addralign"] < 4 or at % 4 or size != 96 or \
            sum(item.name == section.name for item in elf.iter_sections()) != 1:
        raise ValueError("Session facts must occupy an independent immutable/NX 96-byte section")
    raw = section.data()[at:at + size]
    words = [int.from_bytes(raw[pos:pos + 4], "little") for pos in range(0, size, 4)]
    if words[:3] != [0x4d535553, 1, 96] or object_words(obj, name) != words:
        raise ValueError("Session fact schema/final/same-object bytes differ")
    btf = object_btf(obj)
    group = btf.named_structure_members("group_info")
    if group["bytes"] != words[3] or set(("usage", "ngroups", "gid")) - group["members"].keys():
        raise ValueError("Session group_info facts differ from actual same-TU BTF")
    for index, member in ((5, "usage"), (7, "ngroups")):
        field = group["members"][member]
        if (field["offset"], field["bytes"]) != tuple(words[index:index + 2]) or not field["bytes"]:
            raise ValueError("Session group_info member differs from actual BTF: " + member)
    gid = group["members"]["gid"]
    if gid["offset"] != words[9] or gid["bytes"] != 0 or not gid["signature"].startswith("a0:"):
        raise ValueError("Session group_info gid must be its actual flexible array")
    roots = [index for index, entry in enumerate(btf.types) if entry and entry[1] == 4 and
             btf.name(entry[0]) == "group_info"]
    element_facts = set()
    for root in roots:
        entry = btf.types[root]
        for index in range(entry[2]):
            pos = entry[5] + index * 12
            if btf.name(btf.u32(pos)) == "gid":
                array = btf.types[btf.resolve(btf.u32(pos + 4))]
                if array[1] != 3 or btf.u32(array[5] + 8):
                    raise ValueError("Session group_info gid is not a zero-length BTF array")
                element_facts.add((btf.type_size(btf.u32(array[5])), btf.signature(btf.u32(array[5]))))
    if len(element_facts) != 1 or next(iter(element_facts))[0] != words[10]:
        raise ValueError("Session group_info gid element differs from actual BTF")
    for alignment, width in ((words[4], words[3]), (words[11], words[10]), (words[13], words[12])):
        if not alignment or alignment > 4096 or alignment & (alignment - 1) or width % alignment:
            raise ValueError("Session compiled type alignment is invalid")
    if words[9] > words[3] or words[9] % words[11]:
        raise ValueError("Session flexible gid offset/alignment exceeds its actual owner")
    fops_indexes = {"owner": 14, "unlocked_ioctl": 16, "flush": 20, "release": 22}
    compat = words[18:20] != [0xffffffff, 0]
    if compat:
        fops_indexes["compat_ioctl"] = 18
    fops = btf.named_structure_selected("file_operations", fops_indexes)
    if fops["bytes"] != words[12]:
        raise ValueError("Session file_operations owner size differs from actual BTF")
    for member, index in fops_indexes.items():
        field = fops["members"][member]
        if (field["offset"], field["bytes"]) != tuple(words[index:index + 2]):
            raise ValueError("Session file_operations member differs from actual BTF: " + member)
    callbacks = {
        "msu_su_session_ioctl": "file_operations.unlocked_ioctl",
        "msu_su_session_flush": "file_operations.flush",
        "msu_su_session_release": "file_operations.release",
        "msu_su_session_free_rcu": "callback_head.func",
        "msu_su_session_resume": "callback_head.func",
        "msu_su_session_exit_work": "work_struct.func",
    }
    if compat:
        callbacks["msu_su_session_compat_ioctl"] = "file_operations.compat_ioctl"
    result = {}
    with obj.open("rb") as stream:
        native = ELFFile(stream)
        _, native_section, native_at, native_size = actual_symbol(native, name, "STT_OBJECT")
        if native_section.name != section.name or native_section["sh_flags"] & 7 != 2 or native_size != size:
            raise ValueError("Session facts lack immutable same-TU section provenance")
        for callback, member in callbacks.items():
            owner, field = member.split(".")
            signature = btf.function(callback)
            if btf.member_function(owner, field) != "*" + signature:
                raise ValueError("Session callback signature differs from actual callback member: " + callback)
            source, source_section, source_at, _ = actual_symbol(native, callback, "STT_FUNC")
            final, final_section, final_at, _ = actual_symbol(elf, callback, "STT_FUNC")
            if not source_section["sh_flags"] & 4 or not final_section["sh_flags"] & 4 or \
                    source["st_value"] % 4 or final["st_value"] % 4:
                raise ValueError("Session callback entry is outside actual executable code: " + callback)
            prefix, object_type = None, None
            if cfi_policy["mode"] == "kcfi":
                if source_at < 4 or final_at < 4:
                    raise ValueError("Session actual callback lacks its KCFI entry prefix: " + callback)
                object_type = int.from_bytes(source_section.data()[source_at - 4:source_at], "little")
                prefix = int.from_bytes(final_section.data()[final_at - 4:final_at], "little")
                if prefix != object_type:
                    raise ValueError("Session actual object/final KCFI prefix differs: " + callback)
            result[callback] = dict(symbol=callback, member=field if owner == "file_operations" else member,
                signature=signature, offset=final["st_value"], kcfiPrefix=prefix, objectKcfiType=object_type,
                objectKcfiTypeSource="actual-object-entry-prefix")
    return dict(schemaVersion=1, symbol=name, offset=symbol["st_value"], bytes=size,
        section=section.name, sectionFlags=section["sh_flags"], values=words,
        compiledSha256=hashlib.sha256(raw).hexdigest(),
        groupInfo=dict(bytes=words[3], alignment=words[4], signature=group["signature"],
            members={member: group["members"][member] for member in ("usage", "ngroups")},
            gid=dict(offset=words[9], elementBytes=words[10], alignment=words[11], signature=gid["signature"])),
        fileOperations=dict(bytes=words[12], alignment=words[13], signature=fops["signature"], members=fops["members"]),
        callbacks=result, cfiPolicy=dict(cfi_policy))


def session_abi_record(path, elf):
    target, obj = path.parent, path.parent / "su_exec_session.o"
    provenance = object_provenance(target, "su_exec_session")
    command = target / ".su_exec_session.o.cmd"
    result = session_abi_facts(obj, elf, session_cfi_policy(command))
    sources = {SU_COMPONENT_ROOT + name: identity(selected_native_source(name))
               for name in ("su_exec_session.c", "su_exec_session.h")}
    for source in sources.values():
        if not any(all(dependency[key] == source[key] for key in ("bytes", "sha256"))
                   for dependency in provenance["dependencies"]):
            raise ValueError("Session ABI omitted its selected source/header dependency")
    configs = [item for item in provenance["dependencies"] if Path(item["path"]).name == ".config"]
    if len(configs) != 1:
        raise ValueError("Session ABI actual configuration dependency is missing/ambiguous")
    result.update(claimSha256=sha(path), sourceSelectionIdentity=RUNTIME_LAYOUT_SELECTION["identity"],
        sourceIdentities=sources, objectIdentity=identity(obj), commandIdentity=identity(command),
        dependencyReceiptIdentity=identity(target / "build-dependencies/su_exec_session.json"),
        dependencies=provenance["dependencies"], configIdentity=configs[0])
    return result


def boot_events_abi_record(path, elf):
    """Bind the raw producer's native layouts/callbacks to its actual object."""
    target, obj = path.parent, path.parent / "boot_events.o"
    provenance = object_provenance(target, "boot_events")
    command = target / ".boot_events.o.cmd"
    policy = session_cfi_policy(command)
    btf = object_btf(obj)

    def symbol(image, name, kind):
        table = image.get_section_by_name(".symtab")
        matches = table.get_symbol_by_name(name) if table else None
        if not matches or len(matches) != 1 or matches[0]["st_info"]["type"] != kind or \
                not isinstance(matches[0]["st_shndx"], int):
            raise ValueError("Boot event native symbol missing/ambiguous: " + name)
        item = matches[0]
        section = image.get_section(item["st_shndx"])
        at, size = item["st_value"] - section["sh_addr"], item["st_size"]
        if at < 0 or size <= 0 or at + size > section["sh_size"]:
            raise ValueError("Boot event native symbol bounds: " + name)
        return item, section, at, size

    name = "msu_boot_events_abi_words"
    item, section, at, size = symbol(elf, name, "STT_OBJECT")
    if section.name != ".msu.boot_events.abi" or section["sh_type"] != "SHT_PROGBITS" or \
            section["sh_flags"] & 7 != 2 or at or size != 152 or section["sh_size"] != size or \
            sum(entry.name == section.name for entry in elf.iter_sections()) != 1:
        raise ValueError("Boot event ABI must have one immutable/NX 152-byte section")
    raw = section.data()
    words = [int.from_bytes(raw[pos:pos + 4], "little") for pos in range(0, size, 4)]
    if words[:4] != [0x4d534245, 1, size, 1] or object_words(obj, name) != words:
        raise ValueError("Boot event ABI schema/final/same-object bytes differ")
    layouts = {}
    for owner, (base, members) in BOOT_EVENTS_LAYOUTS.items():
        layout = btf.named_structure_selected(owner, members)
        alignment = words[base + 1]
        if layout["bytes"] != words[base] or not alignment or alignment > 4096 or \
                alignment & (alignment - 1) or words[base] % alignment:
            raise ValueError("Boot event native object size/alignment: " + owner)
        for member, index in members.items():
            field = layout["members"][member]
            if (field["offset"], field["bytes"]) != tuple(words[index:index + 2]) or not field["bytes"]:
                raise ValueError("Boot event native member differs from same-TU BTF: " + owner + "." + member)
        layouts[owner] = dict(layout, alignment=alignment)
    callbacks = {}
    with obj.open("rb") as stream:
        native = ELFFile(stream)
        _, native_section, _, native_size = symbol(native, name, "STT_OBJECT")
        if native_section.name != section.name or native_section["sh_flags"] & 7 != 2 or native_size != size:
            raise ValueError("Boot event ABI omitted its immutable same-TU provenance")
        for member, callback in BOOT_EVENTS_CALLBACKS.items():
            owner, field = member.split(".")
            signature = btf.function(callback)
            if btf.member_function(owner, field) != "*" + signature:
                raise ValueError("Boot event callback differs from native member: " + callback)
            source, source_section, source_at, _ = symbol(native, callback, "STT_FUNC")
            final, final_section, final_at, _ = symbol(elf, callback, "STT_FUNC")
            if not source_section["sh_flags"] & 4 or not final_section["sh_flags"] & 4 or \
                    source["st_value"] % 4 or final["st_value"] % 4:
                raise ValueError("Boot event callback is outside actual code: " + callback)
            prefix, object_type = None, None
            if policy["mode"] == "kcfi":
                if source_at < 4 or final_at < 4:
                    raise ValueError("Boot event callback lacks its KCFI entry prefix: " + callback)
                object_type = int.from_bytes(source_section.data()[source_at - 4:source_at], "little")
                prefix = int.from_bytes(final_section.data()[final_at - 4:final_at], "little")
                if prefix != object_type:
                    raise ValueError("Boot event object/final callback KCFI mismatch: " + callback)
            callbacks[callback] = dict(symbol=callback, member=member, signature=signature,
                offset=final["st_value"], kcfiPrefix=prefix, objectKcfiType=object_type,
                objectKcfiTypeSource="actual-object-entry-prefix")
    sources = {"runtime/ultra/boot/init-bridge/" + name: identity(selected_native_source(name))
               for name in ("boot_events.c", "boot_events.h")}
    for source in sources.values():
        if not any(all(dependency[key] == source[key] for key in ("bytes", "sha256"))
                   for dependency in provenance["dependencies"]):
            raise ValueError("Boot event ABI omitted a selected source/header dependency")
    configs = [entry for entry in provenance["dependencies"] if Path(entry["path"]).name == ".config"]
    if len(configs) != 1:
        raise ValueError("Boot event ABI configuration dependency is missing/ambiguous")
    return dict(schemaVersion=1, symbol=name, offset=item["st_value"], bytes=size,
        section=section.name, sectionFlags=section["sh_flags"], values=words,
        compiledSha256=hashlib.sha256(raw).hexdigest(), layouts=layouts, callbacks=callbacks,
        cfiPolicy=policy, claimSha256=sha(path), sourceSelectionIdentity=RUNTIME_LAYOUT_SELECTION["identity"],
        sourceIdentities=sources, objectIdentity=identity(obj), commandIdentity=identity(command),
        dependencyReceiptIdentity=identity(target / "build-dependencies/boot_events.json"),
        dependencies=provenance["dependencies"], configIdentity=configs[0])


def ksu_compat_abi_record(path, elf):
    """Retain this descriptor's real native fops/task-work and callback ABI."""
    target, obj = path.parent, path.parent / "manager_ksu_compat.o"
    provenance = object_provenance(target, "manager_ksu_compat")
    command = target / ".manager_ksu_compat.o.cmd"
    policy = session_cfi_policy(command)
    if not re.search(r"(?:^|\s)-DMSU_KSU_COMPAT_RAW_RESIDENT=1(?:\s|$)", command.read_text()):
        raise ValueError("KSU compatibility descriptor lacks its real raw-resident owner selection")
    btf = object_btf(obj)

    def symbol(image, name, kind):
        table = image.get_section_by_name(".symtab")
        matches = table.get_symbol_by_name(name) if table else None
        if not matches or len(matches) != 1 or matches[0]["st_info"]["type"] != kind or \
                not isinstance(matches[0]["st_shndx"], int):
            raise ValueError("KSU compatibility native symbol missing/ambiguous: " + name)
        item = matches[0]
        section = image.get_section(item["st_shndx"])
        at, size = item["st_value"] - section["sh_addr"], item["st_size"]
        if at < 0 or size <= 0 or at + size > section["sh_size"]:
            raise ValueError("KSU compatibility symbol bounds: " + name)
        return item, section, at, size

    name = "msu_ksu_compat_layout"
    item, section, at, size = symbol(elf, name, "STT_OBJECT")
    if section.name != ".msu.ksu-compat.layout" or section["sh_type"] != "SHT_PROGBITS" or \
            section["sh_flags"] & 7 != 2 or at or size != 56 or section["sh_size"] != size:
        raise ValueError("KSU compatibility ABI must have one immutable/NX 56-byte section")
    raw = section.data()
    words = [int.from_bytes(raw[pos:pos + 4], "little") for pos in range(0, size, 4)]
    if words[0] != 1 or object_words(obj, name) != words:
        raise ValueError("KSU compatibility ABI schema/final/same-object bytes differ")
    fields = {"owner": 3, "unlocked_ioctl": 5, "release": 9}
    compat = words[7:9] != [0xffffffff, 0]
    if compat:
        fields["compat_ioctl"] = 7
    fops = btf.named_structure_selected("file_operations", fields)
    if fops["bytes"] != words[1] or not words[2] or words[2] & (words[2] - 1) or words[1] % words[2]:
        raise ValueError("KSU compatibility file_operations size/alignment differs")
    for member, index in fields.items():
        field = fops["members"][member]
        if (field["offset"], field["bytes"]) != tuple(words[index:index + 2]):
            raise ValueError("KSU compatibility file_operations member differs: " + member)
    callback = btf.named_structure_selected("callback_head", ("func",))
    field = callback["members"]["func"]
    if callback["bytes"] != words[11] or (field["offset"], field["bytes"]) != tuple(words[12:14]):
        raise ValueError("KSU compatibility task_work layout differs")
    members = {"msu_ksu_ioctl": "file_operations.unlocked_ioctl", "msu_ksu_release": "file_operations.release",
        "msu_ksu_install_resume": "callback_head.func"}
    if compat:
        members["msu_ksu_compat_ioctl"] = "file_operations.compat_ioctl"
    callbacks = {}
    with obj.open("rb") as stream:
        native = ELFFile(stream)
        _, native_section, _, native_size = symbol(native, name, "STT_OBJECT")
        if native_section.name != section.name or native_section["sh_flags"] & 7 != 2 or native_size != size:
            raise ValueError("KSU compatibility omitted immutable same-TU provenance")
        for function, member in members.items():
            owner, field = member.split(".")
            signature = btf.function(function)
            if btf.member_function(owner, field) != "*" + signature:
                raise ValueError("KSU compatibility callback differs from native member: " + function)
            source, source_section, source_at, _ = symbol(native, function, "STT_FUNC")
            final, final_section, final_at, _ = symbol(elf, function, "STT_FUNC")
            if not source_section["sh_flags"] & 4 or not final_section["sh_flags"] & 4 or \
                    source["st_value"] % 4 or final["st_value"] % 4:
                raise ValueError("KSU compatibility callback is outside actual code: " + function)
            prefix, object_type = None, None
            if policy["mode"] == "kcfi":
                if source_at < 4 or final_at < 4:
                    raise ValueError("KSU compatibility callback lacks KCFI prefix: " + function)
                object_type = int.from_bytes(source_section.data()[source_at - 4:source_at], "little")
                prefix = int.from_bytes(final_section.data()[final_at - 4:final_at], "little")
                if prefix != object_type:
                    raise ValueError("KSU compatibility object/final KCFI mismatch: " + function)
            callbacks[function] = dict(symbol=function, member=member, signature=signature,
                offset=final["st_value"], kcfiPrefix=prefix, objectKcfiType=object_type,
                objectKcfiTypeSource="actual-object-entry-prefix")
    sources = {"runtime/ultra/identity/kernel/" + name: identity(selected_native_source(name))
               for name in ("manager_ksu_compat.c", "manager_ksu_compat.h")}
    for source in sources.values():
        if not any(all(dependency[key] == source[key] for key in ("bytes", "sha256"))
                   for dependency in provenance["dependencies"]):
            raise ValueError("KSU compatibility ABI omitted selected source/header dependency")
    configs = [entry for entry in provenance["dependencies"] if Path(entry["path"]).name == ".config"]
    if len(configs) != 1:
        raise ValueError("KSU compatibility ABI configuration dependency missing/ambiguous")
    return dict(schemaVersion=1, symbol=name, offset=item["st_value"], bytes=size,
        section=section.name, sectionFlags=section["sh_flags"], values=words,
        compiledSha256=hashlib.sha256(raw).hexdigest(),
        layouts=dict(file_operations=dict(fops, alignment=words[2]), callback_head=callback),
        callbacks=callbacks, cfiPolicy=policy, claimSha256=sha(path),
        sourceSelectionIdentity=RUNTIME_LAYOUT_SELECTION["identity"], sourceIdentities=sources,
        objectIdentity=identity(obj), commandIdentity=identity(command),
        dependencyReceiptIdentity=identity(target / "build-dependencies/manager_ksu_compat.json"),
        dependencies=provenance["dependencies"], configIdentity=configs[0])


def runtime_profiles_enabled():
    if RUNTIME_LAYOUT_SELECTION is None:
        return False
    sources = [item for item in SOURCES if item.name == "manager_su_runtime_target.c"]
    return len(sources) == 1 and bool(re.search(r"\bmsu_su_runtime_profiles\b",
                                               sources[0].read_text(encoding="utf-8-sig")))


def runtime_profiles_record(path, elf, defined):
    name = "msu_su_runtime_profiles"
    symbol = defined.get(name)
    if symbol is None or symbol["st_info"]["type"] != "STT_OBJECT" or not isinstance(symbol["st_shndx"], int):
        raise ValueError("Runtime SU profiles require one actual exported object")
    section = elf.get_section(symbol["st_shndx"])
    offset, size = symbol["st_value"] - section["sh_addr"], symbol["st_size"]
    if section.name != ".msu.su-runtime.profiles" or section["sh_type"] != "SHT_PROGBITS" or \
            section["sh_flags"] & 7 != 3 or section["sh_addralign"] < 4 or offset < 0 or offset % 4 or \
            offset + size > section["sh_size"] or size != 372 or \
            sum(item.name == section.name for item in elf.iter_sections()) != 1:
        raise ValueError("Runtime SU profile table must be independent writable/NX bytes")
    raw = section.data()[offset:offset + size]
    words = [int.from_bytes(raw[at:at + 4], "little") for at in range(0, size, 4)]
    if words[:5] != [0x4d535550, 1, size, 5, 0] or words[5] not in (0, 1) or words[7]:
        raise ValueError("Runtime SU profile header has an invalid initial state")
    names = []
    for index in range(5):
        record = raw[32 + index * 68:32 + (index + 1) * 68]
        end = record[:40].find(b"\0")
        if end <= 0 or any(record[end:40]) or any(record[40:]):
            raise ValueError("Runtime SU profile must contain an unbound zero instruction template")
        names.append(record[:end].decode("ascii"))
    if names != ["do_execveat_common", "__arm64_sys_newfstatat", "__arm64_sys_faccessat",
                 "__arm64_sys_statx", "__arm64_sys_faccessat2"]:
        raise ValueError("Runtime SU profile function order differs from the native consumer")
    target, obj = path.parent, path.parent / "manager_su_runtime_target.o"
    if object_words(obj, name) != words:
        raise ValueError("Runtime SU profile final bytes differ from their actual native object")
    facts = object_words(obj, "msu_su_runtime_target_facts")
    alignment, = object_words(obj, "msu_su_runtime_filename_alignment")
    btf = object_btf(obj)
    layout = btf.named_structure_selected("filename", ("name",))
    member = layout["members"]["name"]
    if len(facts) != 9 or facts[:4] != [1, layout["bytes"], member["offset"], member["bytes"]] or \
            facts[8] or member["bytes"] != 8 or not member["signature"].startswith("*") or \
            not alignment or alignment > 4096 or alignment & (alignment - 1) or member["offset"] % alignment or \
            words[6] != member["offset"]:
        raise ValueError("Runtime SU profile filename facts/BTF/alignment mismatch")
    registers = btf.named_structure_selected("pt_regs", ("regs",))
    register_member = registers["members"]["regs"]
    exec_abi = btf.variable("msu_su_runtime_exec_abi")
    exec_layout = object_words(obj, "msu_su_runtime_exec_layout")
    if facts[4] != registers["bytes"] or facts[5] != register_member["offset"] or \
            facts[6] != 8 or register_member["bytes"] % facts[6] or facts[7] or \
            exec_abi != btf.typedef_function("msu_su_exec_source_fn") or \
            len(exec_layout) != 2 or exec_layout[0] not in (8, 16) or \
            exec_layout[1] != 2 + 2 * exec_layout[0] // 8:
        raise ValueError("Runtime SU profile native register/exec ABI evidence mismatch")
    provenance = object_provenance(target, "manager_su_runtime_target")
    sources = {"runtime/ultra/identity/kernel/" + source.name: identity(source)
               for source in SOURCES if source.name in ("manager_su_runtime_target.c", "manager_su_runtime_target_profile.h")}
    for source in sources.values():
        if not any(all(dependency[key] == source[key] for key in ("bytes", "sha256"))
                   for dependency in provenance["dependencies"]):
            raise ValueError("Runtime SU profile omitted its actual source/header dependency")
    configs = [item for item in provenance["dependencies"] if Path(item["path"]).name == ".config"]
    if len(configs) != 1:
        raise ValueError("Runtime SU profile actual configuration dependency is missing/ambiguous")
    return dict(schemaVersion=1, symbol=name, offset=symbol["st_value"], bytes=size,
        section=section.name, sectionFlags=section["sh_flags"], values=words, names=names,
        compiledSha256=hashlib.sha256(raw).hexdigest(), claimSha256=sha(path),
        targetFacts=facts, registersSignature=register_member["signature"], execAbi=exec_abi,
        execLayout=dict(userArgPtrBytes=exec_layout[0], flagsRegister=exec_layout[1]),
        filename=dict(offset=member["offset"], bytes=member["bytes"], ownerBytes=layout["bytes"],
                      signature=member["signature"], alignment=alignment),
        sourceSelectionIdentity=RUNTIME_LAYOUT_SELECTION["identity"], sourceIdentities=sources,
        objectIdentity=identity(obj), commandIdentity=identity(target / ".manager_su_runtime_target.o.cmd"),
        dependencyReceiptIdentity=identity(target / "build-dependencies/manager_su_runtime_target.json"),
        dependencies=provenance["dependencies"], configIdentity=configs[0], compiledKernelFamily=target.name)


_runtime_parent_core_public_selection = core_public_selection
_runtime_parent_core_source_evolution = core_source_evolution


def core_public_selection():
    result = _runtime_parent_core_public_selection()
    if RUNTIME_LAYOUT_SELECTION is not None:
        result["runtimeLayoutSourceSelection"] = RUNTIME_LAYOUT_SELECTION["identity"]
    return result


def core_source_evolution():
    result = _runtime_parent_core_source_evolution()
    if RUNTIME_LAYOUT_SELECTION is not None:
        replacements = {row["logicalPath"]: row for row in RUNTIME_LAYOUT_SELECTION["files"]}
        result["selectedInputs"] = [dict(row, previousRuntimeLayoutInput=row.get("nativeCopyIdentity", row["after"]),
            after=replacements[row["path"]]["after"], nativeCopyIdentity=replacements[row["path"]]["after"])
            if row["path"] in replacements else row for row in result["selectedInputs"]]
        result["runtimeLayoutSourceSelection"] = RUNTIME_LAYOUT_SELECTION["identity"]
    if TASK_PROFILE_SELECTION is not None and TASK_PROFILE_SELECTION.get("parentRuntimeLayoutSelection") is not None:
        replacements = {row["logicalPath"]: row for row in TASK_PROFILE_SELECTION["files"]}
        result["selectedInputs"] = [dict(row, previousTaskProfileInput=row.get("nativeCopyIdentity", row["after"]),
            after=replacements[row["path"]]["after"], nativeCopyIdentity=replacements[row["path"]]["after"])
            if row["path"] in replacements else row for row in result["selectedInputs"]]
        result["taskProfileSuccessor"] = TASK_PROFILE_SELECTION["identity"]
    return result


def main():
    global KERNEL_618_SOURCE, KERNEL_618_OBJTREE, KERNEL_618_TOOLCHAIN
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=ROOT / (
        "work/ultra-native-manager-claim-20261113/claim-entry/build"))
    parser.add_argument("--kernels", nargs="+", choices=KERNELS, default=KERNELS)
    parser.add_argument("--assets", type=Path, help="Publish all six payloads and support code to this assets directory")
    parser.add_argument("--module-route-catalog", type=Path)
    parser.add_argument("--module-route-plan", type=Path)
    parser.add_argument("--publish-existing", action="store_true",
                        help="Verify and package the exact existing six-family generation without compilation")
    parser.add_argument("--reference-abi-only", action="store_true",
                        help="Extract reference ABI from existing claim.so files; no native compilation")
    parser.add_argument("--core-source-selection", type=Path,
                        help="Explicit owner-frozen complete CORE lifecycle source selection")
    parser.add_argument("--core-source-selection-sha256",
                        help="Exact identity of the separate CORE source-selection receipt")
    parser.add_argument("--su-component-source-selection", type=Path,
                        help="Exact independent five-TU SU native component source selection")
    parser.add_argument("--su-component-source-selection-sha256",
                        help="Exact identity of that full component selection")
    parser.add_argument("--su-source-selection", type=Path,
                        help="Explicit independent SU candidate source-selection receipt")
    parser.add_argument("--su-source-selection-sha256",
                        help="Exact identity of the separate SU source-selection receipt")
    parser.add_argument("--host-deployment-source-selection", type=Path,
                        help="Explicit HOST native/support deployment source overlay")
    parser.add_argument("--host-deployment-source-selection-sha256",
                        help="Exact identity of that deployment source overlay")
    parser.add_argument("--source-corrections", type=Path,
                        help="Exact three-source MAIN compatibility corrections after original CORE/SU validation")
    parser.add_argument("--source-corrections-sha256", help="Exact identity of the MAIN correction manifest")
    parser.add_argument("--core-runtime-source-selection", type=Path)
    parser.add_argument("--core-runtime-source-selection-sha256")
    parser.add_argument("--su-profile-metadata-source-selection", type=Path)
    parser.add_argument("--su-profile-metadata-source-selection-sha256")
    parser.add_argument("--host-durable-source-selection", type=Path)
    parser.add_argument("--host-durable-source-selection-sha256")
    parser.add_argument("--host-initialization-source-selection", type=Path)
    parser.add_argument("--host-initialization-source-selection-sha256")
    parser.add_argument("--task-profile-source-selection", type=Path)
    parser.add_argument("--task-profile-source-selection-sha256")
    parser.add_argument("--core-current-task-source-selection", type=Path)
    parser.add_argument("--core-current-task-source-selection-sha256")
    parser.add_argument("--host-publication-source-selection", type=Path)
    parser.add_argument("--host-publication-source-selection-sha256")
    parser.add_argument("--host-sync-source-selection", type=Path)
    parser.add_argument("--host-sync-source-selection-sha256")
    parser.add_argument("--host-retirement-source-selection", type=Path)
    parser.add_argument("--host-retirement-source-selection-sha256")
    parser.add_argument("--runtime-layout-source-selection", type=Path)
    parser.add_argument("--runtime-layout-source-selection-sha256")
    parser.add_argument("--kernel-source-618", type=Path)
    parser.add_argument("--kernel-objtree-618", type=Path)
    parser.add_argument("--kernel-toolchain-618", type=Path)
    args = parser.parse_args()
    custom_618 = (args.kernel_source_618, args.kernel_objtree_618, args.kernel_toolchain_618)
    if any(custom_618):
        if not all(item is not None and item.is_dir() for item in custom_618):
            parser.error("6.18 source, objtree and toolchain must be existing directories supplied together")
        KERNEL_618_SOURCE, KERNEL_618_OBJTREE, KERNEL_618_TOOLCHAIN = (item.resolve() for item in custom_618)
    if bool(args.core_source_selection) != bool(args.core_source_selection_sha256):
        parser.error("CORE selection path and exact SHA256 must be supplied together")
    if args.core_source_selection:
        select_core_sources(args.core_source_selection, args.core_source_selection_sha256)
    if bool(args.su_component_source_selection) != bool(args.su_component_source_selection_sha256):
        parser.error("SU component selection path and SHA256 must be supplied together")
    if args.su_component_source_selection and args.su_source_selection:
        parser.error("Select one historical SU or native component source generation")
    if args.su_component_source_selection:
        select_su_component_sources(args.su_component_source_selection, args.su_component_source_selection_sha256)
    if bool(args.su_source_selection) != bool(args.su_source_selection_sha256):
        parser.error("SU selection path and exact SHA256 must be supplied together")
    if args.su_source_selection:
        select_su_sources(args.su_source_selection, args.su_source_selection_sha256)
    if bool(args.host_deployment_source_selection) != bool(args.host_deployment_source_selection_sha256):
        parser.error("Deployment selection path and exact SHA256 must be supplied together")
    if args.host_deployment_source_selection:
        select_host_deployment_sources(args.host_deployment_source_selection, args.host_deployment_source_selection_sha256)
    if bool(args.core_runtime_source_selection) != bool(args.core_runtime_source_selection_sha256):
        parser.error("core-runtime selection path and SHA256 must be supplied together")
    if args.core_runtime_source_selection:
        select_core_runtime_sources(args.core_runtime_source_selection, args.core_runtime_source_selection_sha256)
    if bool(args.su_profile_metadata_source_selection) != bool(args.su_profile_metadata_source_selection_sha256):
        parser.error("su-profile-metadata selection path and SHA256 must be supplied together")
    if args.su_profile_metadata_source_selection:
        select_su_component_metadata_sources(args.su_profile_metadata_source_selection, args.su_profile_metadata_source_selection_sha256)
    if bool(args.host_durable_source_selection) != bool(args.host_durable_source_selection_sha256):
        parser.error("host-durable selection path and SHA256 must be supplied together")
    if args.host_durable_source_selection:
        select_host_durable_sources(args.host_durable_source_selection, args.host_durable_source_selection_sha256)
    if bool(args.host_initialization_source_selection) != bool(args.host_initialization_source_selection_sha256):
        parser.error("HOST initialization selection path and SHA256 must be supplied together")
    if args.host_initialization_source_selection:
        select_host_initialization_sources(args.host_initialization_source_selection, args.host_initialization_source_selection_sha256)
    for option, selector in (
            ("core_current_task_source_selection", select_core_current_task_sources),
            ("host_publication_source_selection", select_host_publication_sources),
            ("host_sync_source_selection", select_host_sync_sources),
            ("host_retirement_source_selection", select_host_retirement_sources)):
        selection, expected = getattr(args, option), getattr(args, option + "_sha256")
        if bool(selection) != bool(expected):
            parser.error(option.replace("_", "-") + " requires its exact SHA256")
        if selection:
            selector(selection, expected)
    if bool(args.source_corrections) != bool(args.source_corrections_sha256):
        parser.error("Source-correction path and exact SHA256 must be supplied together")
    if args.source_corrections:
        select_source_corrections(args.source_corrections, args.source_corrections_sha256)
    if bool(args.task_profile_source_selection) != bool(args.task_profile_source_selection_sha256):
        parser.error("TaskProfile successor path and exact SHA256 must be supplied together")
    deferred_task_profile = False
    if args.task_profile_source_selection:
        task_manifest = json.loads(args.task_profile_source_selection.read_text(encoding="utf-8"))
        deferred_task_profile = task_manifest.get("parentRuntimeLayoutSelection") is not None
        if not deferred_task_profile:
            select_task_profile_sources(args.task_profile_source_selection, args.task_profile_source_selection_sha256)
    if bool(args.runtime_layout_source_selection) != bool(args.runtime_layout_source_selection_sha256):
        parser.error("runtime-layout source selection requires its exact SHA256")
    if args.runtime_layout_source_selection:
        select_runtime_layout_sources(args.runtime_layout_source_selection, args.runtime_layout_source_selection_sha256)
    if deferred_task_profile:
        select_task_profile_sources(args.task_profile_source_selection, args.task_profile_source_selection_sha256)
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    global MODULE_ROUTE_BINDING, MODULE_ROUTE_INPUTS
    if bool(args.module_route_catalog) != bool(args.module_route_plan):
        parser.error("module-route catalog and strict plan must be supplied together")
    if args.module_route_catalog:
        if not args.assets or set(args.kernels) != RAW_FAMILIES or RUNTIME_LAYOUT_SELECTION is None:
            parser.error("module routes require assets, all four raw families and current runtime selection")
        MODULE_ROUTE_BINDING = inspect_selected_catalog(args.module_route_catalog,
            args.module_route_plan, RUNTIME_LAYOUT_SELECTION)
        MODULE_ROUTE_INPUTS = (args.module_route_catalog, args.module_route_plan)
    if args.assets and set(args.kernels) != set(KERNELS) and MODULE_ROUTE_BINDING is None:
        parser.error("--assets requires six raw families or four raw plus actual strict legacy module routes")
    if args.publish_existing and (not args.assets or args.reference_abi_only):
        parser.error("--publish-existing requires --assets and excludes --reference-abi-only")
    if args.reference_abi_only and args.assets:
        parser.error("--reference-abi-only writes reference receipts; use the existing build receipt to publish")
    if args.reference_abi_only:
        references = {kernel: collect_reference_abi(kernel, out) for kernel in args.kernels}
        (out / "reference-abi-results.json").write_text(
            json.dumps(references, indent=2) + "\n", newline="\n")
        return 0 if all(r["complete"] for r in references.values()) else 1
    selection = upstream_source_freeze()
    selected_inputs = [core_baseline_input(row["newPath"], ROOT / row["newPath"])
                       for row in selection["sourceMigration"]["selectedInputs"]]
    su_receipts = su_selected_receipts()
    before = {str(s.relative_to(ROOT)): sha(s) for s in [ROOT / "manager/build.gradle.kts", *SOURCES, *PUBLICATION_GATE_INPUTS, *SUPPORT_SOURCES, *selected_inputs,
              *su_source_paths(), *su_receipts, *core_selection_inputs(), *host_deployment_selection_inputs(),
              *source_correction_inputs(), PROTOCOL, FRAME, *BUILD_TOOLS,
              *su_source_paths(), *su_receipts, *core_selection_inputs(), *host_deployment_selection_inputs(),
              *core_runtime_selection_inputs(), *host_durable_selection_inputs(), *host_initialization_inputs(), *task_profile_selection_inputs(),
              *core_current_task_inputs(), *host_publication_inputs(), *host_sync_inputs(), *host_retirement_inputs(),
              *runtime_layout_selection_inputs(), PROTOCOL, FRAME, *BUILD_TOOLS, *RAW_REFERENCE_INPUTS,
              SOURCE_FREEZE, PARENT_FREEZE, BASE_FREEZE, SOURCE_CHANGESET, PARENT_CHANGESET, BASE_CHANGESET,
              PUBLICATION_CONTACT, P2_HOST_DELTA, P2_ROUTE_DELTA, R09_FREEZE, R09_CHANGESET,
              R09_BASELINE, R10_FREEZE, R10_CHANGESET, R10_BASELINE, R10_ADOPTION, SOURCE_MIGRATION,
              BASE_FREEZE.with_name("BASELINE.json")]}
    if args.publish_existing:
        from policy_payload_publish import publish_existing
        return publish_existing(globals(), out, args.assets.resolve(), before)
    support = build_support(out)
    with ThreadPoolExecutor(max_workers=2) as pool:
        pending = [(kernel, pool.submit(build, kernel, out)) for kernel in args.kernels]
        results = []
        for kernel, future in pending:
            try:
                results.append(future.result())
            except Exception as error:
                receipt = out / kernel / "build-result.json"
                row = json.loads(receipt.read_text()) if receipt.is_file() else dict(kernel=kernel, exitCode=1)
                row["buildError"] = type(error).__name__ + ": " + str(error)
                receipt.write_text(json.dumps(row, indent=2) + "\n", newline="\n")
                results.append(row)
    unchanged = all(sha(ROOT / s) == digest for s, digest in before.items())
    report = {"scope": "Kbuild/PIC and freestanding compile; no device execution",
              "inputs": before, "inputsUnchanged": unchanged, "flags": FLAGS,
              "support": support, "results": results, "gradleExecuted": False, "deviceExecuted": False}
    (out / "results.json").write_text(json.dumps(report, indent=2) + "\n", newline="\n")
    passed = unchanged and all(r["exitCode"] == 0 and not r.get("buildError") and
                              not r.get("metadataError") and
                              r.get("payload", {}).get("referenceAbi", {}).get("complete", False) and
                              r.get("payload", {}).get("callbackCfi", {}).get("supported", False) for r in results)
    if passed and args.assets:
        assets = args.assets.resolve()
        assets.mkdir(parents=True, exist_ok=True)
        for name in support:
            shutil.copyfile(out / "support" / f"{name}.bin", assets / f"{name}.bin")
        for row in results:
            family = assets / row["kernel"]
            family.mkdir(exist_ok=True)
            shutil.copyfile(out / row["kernel"] / "claim.so", family / "claim.so")
            if "retirementSupport" in row:
                shutil.copyfile(out / row["kernel"] / "support/bootstrap.bin", family / "bootstrap.bin")
        if MODULE_ROUTE_BINDING is not None and inspect_selected_catalog(args.module_route_catalog,
                args.module_route_plan, RUNTIME_LAYOUT_SELECTION) != MODULE_ROUTE_BINDING:
            raise ValueError("Selected module routes changed during raw compilation")
        write_runtime_manifest(assets, out, before, support, results, MODULE_ROUTE_BINDING)
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
