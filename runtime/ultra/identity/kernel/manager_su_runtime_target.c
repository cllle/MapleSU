// SPDX-License-Identifier: GPL-3.0-only
#include <linux/binfmts.h>
#include <linux/compiler.h>
#include <linux/err.h>
#include <linux/errno.h>
#include <linux/fs.h>
#include <linux/irqflags.h>
#include <linux/mutex.h>
#include <linux/preempt.h>
#include <linux/stddef.h>
#include <linux/string.h>
#include <linux/uaccess.h>
#include <asm/ptrace.h>
#include <asm/unistd.h>
#include "manager_su_runtime_factory.h"
#include "manager_task_state.h"
#ifndef __has_feature
#define __has_feature(name) 0
#endif
#if defined(MSU_INIT_BRIDGE_RUNTIME_FACTS)
#include "manager_runtime_btf.h"
#include "manager_runtime_accessor.h"
#endif
#if defined(MSU_INIT_BRIDGE_HOST_IMPORTS)
#include "native_cfi.h"
#if defined(MSU_INIT_BRIDGE_RUNTIME_FACTS)
#include "native_lookup.h"
#endif
#if MSU_INIT_BRIDGE_CFI_LEGACY && defined(MODULE)
#include "native_legacy_cfi.h"
#endif
#endif

u32 msu_su_runtime_target_facts[9] __attribute__((section(".msu.su-runtime.target"))) = {
    1, sizeof(struct filename), offsetof(struct filename, name),
    sizeof(((struct filename *)0)->name), sizeof(struct pt_regs),
    offsetof(struct pt_regs, regs), sizeof(((struct pt_regs *)0)->regs[0]),
    0, 0,
};
const u32 msu_su_runtime_filename_alignment
    __attribute__((section(".msu.su-runtime.alignment"), used)) =
    __alignof__(((struct filename *)0)->name);
static DEFINE_MUTEX(msu_su_target_lock);
static struct msu_su_runtime_hook msu_su_targets[MSU_SU_RUNTIME_MAX_HOOKS];

#if defined(CONFIG_ARM64) && defined(CONFIG_ARCH_HAS_SYSCALL_WRAPPER)
#include "manager_su_runtime_target_profile.h"
#include "manager_dynamic_layout.h"

/* Same-object compiler evidence for the actual hook ABI. This variable's BTF
 * preserves the native function-pointer typedef, not a signature string. */
static msu_su_exec_source_fn msu_su_runtime_exec_abi __attribute__((used));
#define MSU_SU_EXEC_FLAGS_REGISTER (2 + 2 * sizeof(struct user_arg_ptr) / sizeof(unsigned long))
const u32 msu_su_runtime_exec_layout[2]
    __attribute__((section(".msu.su-runtime.exec-layout"), used)) = {
    sizeof(struct user_arg_ptr), MSU_SU_EXEC_FLAGS_REGISTER,
};

struct msu_su_runtime_profile_table msu_su_runtime_profiles
    __attribute__((section(".msu.su-runtime.profiles"), aligned(4))) = {
    .magic = MSU_SU_TARGET_PROFILE_MAGIC,
    .schema = MSU_SU_TARGET_PROFILE_SCHEMA,
    .bytes = sizeof(struct msu_su_runtime_profile_table),
    .count = MSU_SU_TARGET_PROFILE_COUNT,
    .ready = 0,
    .kcfi_mode = __has_feature(kcfi),
    .filename_offset = offsetof(struct filename, name),
    .reserved = 0,
    .values = {
        { .name = "do_execveat_common" },
        { .name = "__arm64_sys_newfstatat" },
        { .name = "__arm64_sys_faccessat" },
        { .name = "__arm64_sys_statx" },
        { .name = "__arm64_sys_faccessat2" },
    },
};
_Static_assert(sizeof(msu_su_runtime_profiles) == 372, "runtime profile table schema");

_Static_assert(ARRAY_SIZE(msu_su_target_profiles) + 3 <= MSU_SU_RUNTIME_MAX_HOOKS,
    "SU target capacity includes UID transitions and KSU descriptor discovery");
_Static_assert(sizeof(struct user_arg_ptr) == sizeof(unsigned long) ||
    sizeof(struct user_arg_ptr) == 2 * sizeof(unsigned long), "exec argument registers");
/* The real resolver consumes this compiler's native syscall prototype/hash. */
static noinline long msu_su_syscall_reference(const struct pt_regs *regs)
{
    return regs ? -EOPNOTSUPP : -EINVAL;
}

#if defined(MSU_INIT_BRIDGE_RUNTIME_FACTS)
static DEFINE_MUTEX(msu_su_filename_lock);
static struct msu_runtime_btf_field msu_su_filename_field;
static bool msu_su_filename_published;

int msu_su_runtime_filename_field(struct msu_runtime_btf_field *output)
{
    static const char first[] = "MapleSU.filename.native.fact.1.20261124";
    static const char second[] = "MapleSU.filename.native.fact.2.20261124";
    const char *const sentinels[] = { first, second };
    struct filename *names[2] = {NULL, NULL};
    struct msu_runtime_btf_field field = {0};
    char sample[sizeof(first)];
    unsigned long pointer;
    u32 extent, offset, i, matches = 0;
    int error = 0;
    _Static_assert(sizeof(first) == sizeof(second), "independent filename probe widths");
    if (!output) return -EINVAL;
    if (!in_task() || in_atomic() || irqs_disabled()) return -EWOULDBLOCK;
    mutex_lock(&msu_su_filename_lock);
    if (smp_load_acquire(&msu_su_filename_published)) goto published;
    error = msu_runtime_facts_cache_extent("names_cachep", &extent);
    if (error) goto out;
    if (extent < sizeof(void *) || extent > 65536) { error = -EOPNOTSUPP; goto out; }
    for (i = 0; i < ARRAY_SIZE(names); ++i) {
        names[i] = getname_kernel(sentinels[i]);
        if (IS_ERR(names[i])) { error = PTR_ERR(names[i]); names[i] = NULL; goto release; }
        if (!names[i]) { error = -ENOMEM; goto release; }
    }
    /* Observe two independent native allocations. An accepted member must
     * point to each object's different, exact string at the same unique slot.
     * The bound is the slab's allocation extent, not a claimed struct sizeof. */
    for (offset = 0; offset <= extent - sizeof(void *); offset += sizeof(void *)) {
        for (i = 0; i < ARRAY_SIZE(names); ++i) {
            if (copy_from_kernel_nofault(&pointer, (const unsigned char *)names[i] + offset,
                    sizeof(pointer)) || !pointer ||
                copy_from_kernel_nofault(sample, (const void *)pointer, sizeof(sample)) ||
                memcmp(sample, sentinels[i], sizeof(sample))) break;
        }
        if (i != ARRAY_SIZE(names)) continue;
        field.offset = offset;
        if (++matches > 1) { error = -ENOTUNIQ; goto release; }
    }
    if (matches != 1) { error = -ENOENT; goto release; }
    field.bytes = sizeof(void *);
    field.owner_bytes = extent;
    field.kind = 2; /* Witnessed native pointer; no BTF type ID is invented. */
release:
    for (i = ARRAY_SIZE(names); i; --i) if (names[i - 1]) putname(names[i - 1]);
    if (error) goto out;
    msu_su_filename_field = field;
    smp_store_release(&msu_su_filename_published, true);
published:
    *output = msu_su_filename_field;
out:
    mutex_unlock(&msu_su_filename_lock);
    return error;
}
#endif

#if defined(MSU_INIT_BRIDGE_HOST_IMPORTS) && defined(MSU_INIT_BRIDGE_RUNTIME_FACTS)
/* Native fact providers supply the live target ABI; the resolver owns CFI. The
 * producer never substitutes the reference kernel's entry instructions. */
static int msu_su_btf_type(u32 id, struct msu_runtime_btf_type *type)
{
    unsigned int depth;
    int error;
    for (depth = 0; depth < 16; ++depth) {
        error = msu_runtime_btf_type(id, type);
        if (error) return error;
        if (type->kind != 8 && type->kind != 9 && type->kind != 10 &&
            type->kind != 11 && type->kind != 18) return 0;
        id = type->target_id;
    }
    return -ELOOP;
}

static int msu_su_btf_value(u32 id, u32 kind, u32 bytes, const char *name)
{
    struct msu_runtime_btf_type type;
    int error = msu_su_btf_type(id, &type);
    if (error) return error;
    return type.kind == kind && type.bytes == bytes &&
        (!name || (type.name && !strcmp(type.name, name))) ? 0 : -EPROTO;
}

#if !MSU_INIT_BRIDGE_CFI_KCFI
static int msu_su_btf_pointer(u32 id, const char *name)
{
    struct msu_runtime_btf_type type;
    int error = msu_su_btf_type(id, &type);
    if (error) return error;
    if (type.kind != 2 || type.bytes != sizeof(void *)) return -EPROTO;
    error = msu_su_btf_type(type.target_id, &type);
    if (error) return error;
    return type.kind == 4 && type.name && !strcmp(type.name, name) ? 0 : -EPROTO;
}
#endif

#if MSU_INIT_BRIDGE_CFI_KCFI
static noinline int msu_su_exec_reference(int fd, struct filename *filename,
    struct user_arg_ptr argv, struct user_arg_ptr envp, int flags)
{
    (void)filename; (void)argv; (void)envp; (void)flags;
    return fd ? -EOPNOTSUPP : -EINVAL;
}
#endif

static int msu_su_runtime_prototype(const char *name, bool exec)
{
#if MSU_INIT_BRIDGE_CFI_KCFI
    unsigned long address;
    /* The real compiler type word can bind function types without BTF.
     * Aggregate member/size checks below remain independent ABI requirements. */
    return msu_init_bridge_resolve_cfi(name, exec ? (const void *)msu_su_exec_reference :
        (const void *)msu_su_syscall_reference, &address);
#else
    struct msu_runtime_btf_function function;
    int error = msu_runtime_btf_function(name, &function);
    if (error) return error;
    if (function.variadic || function.argument_count != (exec ? 5 : 1)) return -EPROTO;
    error = msu_su_btf_value(function.return_type, 1, exec ? sizeof(int) : sizeof(long), NULL);
    if (error) return error;
    if (!exec) return msu_su_btf_pointer(function.arguments[0], "pt_regs");
    error = msu_su_btf_value(function.arguments[0], 1, sizeof(int), NULL);
    if (!error) error = msu_su_btf_pointer(function.arguments[1], "filename");
    if (!error) error = msu_su_btf_value(function.arguments[2], 4,
        sizeof(struct user_arg_ptr), "user_arg_ptr");
    if (!error) error = msu_su_btf_value(function.arguments[3], 4,
        sizeof(struct user_arg_ptr), "user_arg_ptr");
    if (!error) error = msu_su_btf_value(function.arguments[4], 1, sizeof(int), NULL);
    return error;
#endif
}

static int msu_su_runtime_profiles_produce(unsigned long (*lookup)(const char *),
    int (*resolve_cfi)(const char *, const void *, unsigned long *))
{
    struct msu_su_runtime_profile_table table = msu_su_runtime_profiles;
    struct msu_runtime_btf_field filename, regs, ptr;
    struct msu_runtime_btf_type array;
    unsigned long address;
    u32 regs_bytes, i;
    int error;
    if (smp_load_acquire(&msu_su_runtime_profiles.ready) == 1) return 0;
    if (table.ready || table.magic != MSU_SU_TARGET_PROFILE_MAGIC ||
        table.schema != MSU_SU_TARGET_PROFILE_SCHEMA || table.bytes != sizeof(table) ||
        table.count != MSU_SU_TARGET_PROFILE_COUNT || table.reserved) return -EPROTO;
    error = msu_runtime_facts_field("filename", "name", &filename);
    if (error) return error;
    if (filename.kind != 2 || filename.bytes != sizeof(void *) ||
        filename.offset % __alignof__(void *)) return -EPROTO;
    /* ARM64 syscall regs are a native DDK API object, as in fixed KernelSU.
     * Available live metadata must agree; absent metadata adds no API gate. */
    error = msu_runtime_facts_type_size("pt_regs", &regs_bytes);
    if (error && error != -ENOENT) return error;
    if (!error && regs_bytes != sizeof(struct pt_regs)) return -EPROTO;
    regs_bytes = sizeof(struct pt_regs);
    error = msu_runtime_facts_field("pt_regs", "regs", &regs);
    if (error && error != -ENOENT) return error;
    if (error == -ENOENT) regs = (struct msu_runtime_btf_field){
        .offset = offsetof(struct pt_regs, regs),
        .bytes = sizeof(((struct pt_regs *)0)->regs),
        .owner_bytes = sizeof(struct pt_regs),
    };
    if (regs.offset != offsetof(struct pt_regs, regs) ||
        regs.bytes != sizeof(((struct pt_regs *)0)->regs)) return -EPROTO;
    if (regs.type_id) {
        error = msu_su_btf_type(regs.type_id, &array);
        if (error) return error;
        if (array.kind != 3) return -EPROTO;
        error = msu_su_btf_value(array.target_id, 1, sizeof(unsigned long), NULL);
        if (error) return error;
    }
    /* user_arg_ptr is private and passed by value, so the native register ABI
     * above supplies no evidence for its independent aggregate layout. */
    error = msu_runtime_facts_field("user_arg_ptr", "ptr.native", &ptr);
    if (error) return error;
    if (ptr.bytes != sizeof(void *) || ptr.kind != 2 ||
        ptr.offset != offsetof(struct user_arg_ptr, ptr.native) ||
        ptr.owner_bytes != sizeof(struct user_arg_ptr)) return -EPROTO;
#ifdef CONFIG_COMPAT
    error = msu_runtime_facts_field("user_arg_ptr", "is_compat", &ptr);
    if (error) return error;
    if (ptr.offset != offsetof(struct user_arg_ptr, is_compat) ||
        ptr.bytes != sizeof(bool) || ptr.kind != 1) return -EPROTO;
#endif
    for (i = 0; i < ARRAY_SIZE(table.values); ++i) {
        struct msu_su_target_profile *profile = &table.values[i];
        if (!memchr(profile->name, 0, sizeof(profile->name))) return -EPROTO;
        memset(profile->entry, 0, sizeof(profile->entry));
        profile->kcfi = 0;
        address = lookup(profile->name);
        if (!address && i >= 3) continue;
        if (!address) return -ENOENT;
        error = msu_su_runtime_prototype(profile->name, i == 0);
        if (error) return error;
        if (i) {
            error = resolve_cfi(profile->name, msu_su_syscall_reference, &address);
            if (error) return error;
#if MSU_INIT_BRIDGE_CFI_LEGACY && defined(MODULE)
            {
                unsigned long body = lookup(profile->name);
                if (!msu_legacy_probe_matches(address, body)) return -ESTALE;
                address = body;
            }
#endif
        }
        if (!address || (address & 3) || copy_from_kernel_nofault(profile->entry,
                (const void *)address, sizeof(profile->entry))) return -EFAULT;
        if (i && __has_feature(kcfi) && (address < 4 ||
            copy_from_kernel_nofault(&profile->kcfi, (const void *)(address - 4),
                sizeof(profile->kcfi)))) return -EFAULT;
    }
    table.filename_offset = filename.offset;
    table.kcfi_mode = __has_feature(kcfi);
    memcpy(msu_su_runtime_profiles.values, table.values, sizeof(table.values));
    msu_su_runtime_profiles.filename_offset = table.filename_offset;
    msu_su_runtime_profiles.kcfi_mode = table.kcfi_mode;
    /* An accessor's allocation bound is sufficient for the pointer read, but
     * is not a true filename sizeof. Preserve that distinction in diagnostics. */
    msu_su_runtime_target_facts[1] = filename.type_id ? filename.owner_bytes : 0;
    msu_su_runtime_target_facts[2] = filename.offset;
    msu_su_runtime_target_facts[3] = filename.bytes;
    msu_su_runtime_target_facts[4] = regs_bytes;
    msu_su_runtime_target_facts[5] = regs.offset;
    msu_su_runtime_target_facts[6] = sizeof(unsigned long);
    smp_store_release(&msu_su_runtime_profiles.ready, 1);
    return 0;
}
#endif

#ifdef CONFIG_COMPAT
/* ARM EABI numbers and wrappers are paired by the target's actual compat table.
 * stat/lstat and stat64/lstat64 take pathname in r0 and statbuf in r1;
 * fstatat64, statx and access wrappers use their native argument positions. */
struct msu_su_compat_target {
    u32 number, kind, path, result;
    const char *name;
};
static const struct msu_su_compat_target msu_su_compat_targets[] = {
    {106, MSU_SU_STAT, 0, 1, "__arm64_compat_sys_newstat"},
    {107, MSU_SU_STAT, 0, 1, "__arm64_compat_sys_newlstat"},
    {195, MSU_SU_STAT, 0, 1, "__arm64_sys_stat64"},
    {196, MSU_SU_STAT, 0, 1, "__arm64_sys_lstat64"},
    {327, MSU_SU_STAT, 1, 2, "__arm64_sys_fstatat64"},
    {334, MSU_SU_ACCESS, 1, 0, "__arm64_sys_faccessat"},
    {397, MSU_SU_STAT, 1, 4, "__arm64_sys_statx"},
    {439, MSU_SU_ACCESS, 1, 0, "__arm64_sys_faccessat2"},
};
static int msu_su_bind_compat(unsigned long (*lookup)(const char *),
    int (*resolve_cfi)(const char *, const void *, unsigned long *),
    struct msu_su_runtime_hook *targets, u32 *count)
{
    unsigned long table = lookup("compat_sys_call_table");
    unsigned long absent = lookup("__arm64_sys_ni_syscall");
    unsigned long entry, address;
    u32 i, j;
    int error;
    if (!table || (table & (sizeof(entry) - 1)) ||
        table > ~0UL - 439 * sizeof(entry)) return -EFAULT;
    for (i = 0; i < ARRAY_SIZE(msu_su_compat_targets); ++i) {
        const struct msu_su_compat_target *source = &msu_su_compat_targets[i];
        error = copy_from_kernel_nofault(&entry,
            (const void *)(table + source->number * sizeof(entry)), sizeof(entry));
        if (error) return -EFAULT;
        if (!entry || (entry & 3)) return -EFAULT;
        if (absent && entry == absent) continue; /* actual unimplemented ABI */
        address = 0;
#if defined(MSU_INIT_BRIDGE_HOST_IMPORTS) && defined(MSU_INIT_BRIDGE_RUNTIME_FACTS)
        error = msu_su_runtime_prototype(source->name, false);
        if (error) return error;
#endif
        error = resolve_cfi(source->name, msu_su_syscall_reference, &address);
        if (error) return error;
        if (address != entry) return -ESTALE;
        for (j = 0; j < *count; ++j) if (targets[j].entry == address) break;
        if (j < *count) {
            if (targets[j].kind != source->kind ||
                targets[j].argument_register != source->path ||
                targets[j].result_register != source->result ||
                !targets[j].syscall_frame) return -EINVAL;
            continue;
        }
        if (*count == MSU_SU_RUNTIME_MAX_HOOKS) return -EOVERFLOW;
        targets[*count].entry = address;
        targets[*count].kind = source->kind;
        targets[*count].argument_register = source->path;
        targets[*count].result_register = source->result;
        targets[*count].syscall_frame = true;
        targets[*count].optional = true;
        ++*count;
    }
    return 0;
}
#endif
#endif

int msu_su_runtime_target_bind(unsigned long (*lookup)(const char *),
    int (*resolve_cfi)(const char *, const void *, unsigned long *))
{
#if defined(CONFIG_ARM64) && defined(CONFIG_ARCH_HAS_SYSCALL_WRAPPER)
    struct msu_su_runtime_hook targets[MSU_SU_RUNTIME_MAX_HOOKS] = {0};
    unsigned long address;
    u32 words[6], prefix, i, j, count;
    int error = 0;
    if (!lookup || !resolve_cfi) return -EINVAL;
    if (!msu_runtime_layout_ready()) return -EPROTO;
    if (!in_task() || in_atomic() || irqs_disabled()) return -EWOULDBLOCK;
    mutex_lock(&msu_su_target_lock);
    if (smp_load_acquire(&msu_su_runtime_target_facts[8])) { error = -EALREADY; goto out; }
#if defined(MSU_INIT_BRIDGE_HOST_IMPORTS) && defined(MSU_INIT_BRIDGE_RUNTIME_FACTS)
    error = msu_su_runtime_profiles_produce(lookup, resolve_cfi);
    if (error) goto out;
#endif
    if (smp_load_acquire(&msu_su_runtime_profiles.ready) != 1 ||
        msu_su_runtime_profiles.magic != MSU_SU_TARGET_PROFILE_MAGIC ||
        msu_su_runtime_profiles.schema != MSU_SU_TARGET_PROFILE_SCHEMA ||
        msu_su_runtime_profiles.bytes != sizeof(msu_su_runtime_profiles) ||
        msu_su_runtime_profiles.count != MSU_SU_TARGET_PROFILE_COUNT ||
        msu_su_runtime_profiles.reserved ||
        msu_su_runtime_profiles.kcfi_mode !=
            (__has_feature(kcfi))) { error = -EPROTO; goto out; }
    for (i = 0; i < ARRAY_SIZE(msu_su_target_profiles); ++i) {
        const struct msu_su_target_profile *profile = &msu_su_target_profiles[i];
        address = 0;
        if (i >= 3 && !lookup(profile->name)) {
            const u32 absent[6] = {0};
            if (profile->kcfi || memcmp(profile->entry, absent, sizeof(absent))) {
                error = -ESTALE;
                goto out;
            }
            continue;
        }
        if (!i) {
            /* This native entry is a patch site, never called through this
             * pointer. Its selected producer binds the actual target ABI/bytes. */
            address = lookup(profile->name);
        } else {
            error = resolve_cfi(profile->name, msu_su_syscall_reference, &address);
            if (error) goto out;
#if defined(MSU_INIT_BRIDGE_HOST_IMPORTS) && MSU_INIT_BRIDGE_CFI_LEGACY && defined(MODULE)
            /* A hook edits the body. The typed resolver retains the canonical
             * identity; compare both before using this target's body bytes. */
            {
                unsigned long body = lookup(profile->name);
                if (!msu_legacy_probe_matches(address, body)) { error = -ESTALE; goto out; }
                address = body;
            }
#endif
        }
        if (!address || (address & 3) || copy_from_kernel_nofault(words,
                (const void *)address, sizeof(words))) { error = -EFAULT; goto out; }
        if (memcmp(words, profile->entry, sizeof(words))) { error = -ESTALE; goto out; }
        if (i && msu_su_runtime_profiles.kcfi_mode && (address < 4 || copy_from_kernel_nofault(&prefix,
                (const void *)(address - 4), sizeof(prefix)) || prefix != profile->kcfi)) {
            error = -EOPNOTSUPP;
            goto out;
        }
        targets[i].entry = address;
        targets[i].kind = !i ? MSU_SU_EXEC : (i == 1 || i == 3 ? MSU_SU_STAT : MSU_SU_ACCESS);
        targets[i].argument_register = 1;
        targets[i].result_register = i == 1 ? 2 : i == 3 ? 4 : 0;
        targets[i].filename_member = !i ? msu_su_runtime_profiles.filename_offset : 0;
        targets[i].fd_register = 0;
        targets[i].flags_register = !i ?
            MSU_SU_EXEC_FLAGS_REGISTER : 0;
        targets[i].syscall_frame = i != 0;
        targets[i].optional = i >= 3;
    }
    count = 0;
    for (i = 0; i < ARRAY_SIZE(msu_su_target_profiles); ++i) {
        if (!targets[i].entry) continue;
        if (count != i) targets[count] = targets[i];
        ++count;
    }
    for (i = 0; i < count; ++i) for (j = 0; j < i; ++j) {
        if (targets[i].entry != targets[j].entry) continue;
        if (targets[i].kind != targets[j].kind ||
            targets[i].argument_register != targets[j].argument_register ||
            targets[i].result_register != targets[j].result_register ||
            targets[i].syscall_frame != targets[j].syscall_frame) {
            error = -EINVAL;
            goto out;
        }
        memmove(&targets[i], &targets[i + 1], (count - i - 1) * sizeof(targets[0]));
        memset(&targets[--count], 0, sizeof(targets[0]));
        --i;
        break;
    }
#ifdef CONFIG_COMPAT
    error = msu_su_bind_compat(lookup, resolve_cfi, targets, &count);
    if (error) goto out;
#endif
    /* Both native setresuid and compat setresuid32 use the same ARM64 wrapper.
     * The optional old 16-bit ABI has its own wrapper, not compat_sys_setresuid.
     * Resolve the actual native prototype; never copy another entry's bytes.
     * create_entries still validates every selected instruction for the gate. */
    for (i = 0; i < 2; ++i) {
        const char *name = i ? "__arm64_sys_setresuid16" : "__arm64_sys_setresuid";
        if (count >= ARRAY_SIZE(targets)) { error = -EOVERFLOW; goto out; }
        if (!lookup(name)) {
            if (i) continue;
            error = -ENOENT;
            goto out;
        }
        address = 0;
#if defined(MSU_INIT_BRIDGE_HOST_IMPORTS) && defined(MSU_INIT_BRIDGE_RUNTIME_FACTS)
        error = msu_su_runtime_prototype(name, false);
        if (error) goto out;
#endif
        error = resolve_cfi(name, msu_su_syscall_reference, &address);
        if (error) goto out;
        if (!address || (address & 3)) { error = -EFAULT; goto out; }
        targets[count].entry = address;
        targets[count].kind = i ? MSU_SU_SETRESUID16 : MSU_SU_SETRESUID;
        targets[count].syscall_frame = true;
        targets[count].optional = i != 0;
        ++count;
    }
    if (count >= ARRAY_SIZE(targets)) { error = -EOVERFLOW; goto out; }
    address = 0;
#if defined(MSU_INIT_BRIDGE_HOST_IMPORTS) && defined(MSU_INIT_BRIDGE_RUNTIME_FACTS)
    error = msu_su_runtime_prototype("__arm64_sys_reboot", false);
    if (error) goto out;
#endif
    error = resolve_cfi("__arm64_sys_reboot", msu_su_syscall_reference, &address);
    if (error) goto out;
    if (!address || (address & 3)) { error = -EFAULT; goto out; }
    targets[count].entry = address;
    targets[count].kind = MSU_SU_KSU_REBOOT;
    targets[count].syscall_frame = true;
    ++count;
    memcpy(msu_su_targets, targets, sizeof(targets));
    WRITE_ONCE(msu_su_runtime_target_facts[7], count);
    smp_store_release(&msu_su_runtime_target_facts[8], 1);
    /* Hook-site binding is separate from Profile target qualification. Retain
     * a missing native sys_enter object in the Profile binder; trace_open
     * reports its actual error before any SU source/publication starts. */
    (void)msu_task_profile_trace_bind(lookup);
out:
    mutex_unlock(&msu_su_target_lock);
    return error;
#else
    return -EOPNOTSUPP;
#endif
}

int msu_su_runtime_target_hooks(struct msu_su_runtime_hook *hooks, u32 *count)
{
    if (!hooks || !count) return -EINVAL;
    memset(hooks, 0, sizeof(*hooks) * MSU_SU_RUNTIME_MAX_HOOKS);
    *count = 0;
    if (!smp_load_acquire(&msu_su_runtime_target_facts[8])) return -EOPNOTSUPP;
    memcpy(hooks, msu_su_targets, sizeof(msu_su_targets));
    *count = READ_ONCE(msu_su_runtime_target_facts[7]);
    return 0;
}
