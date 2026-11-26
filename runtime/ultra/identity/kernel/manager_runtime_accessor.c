// SPDX-License-Identifier: GPL-3.0-only
#include <linux/compiler.h>
#include <linux/errno.h>
#include <linux/slab.h>
#include <linux/smp.h>
#include <linux/string.h>
#include <linux/uaccess.h>
#include "manager_runtime_accessor.h"
#include "native_lookup.h"

#define MSU_ACCESSOR_WORDS 128U
struct msu_accessor_value { u32 kind, field, offset; };
struct msu_accessor_fact {
    const char *owner, *member;
    struct msu_runtime_btf_field field;
};
static struct msu_accessor_fact accessor_facts[4];
static u32 accessor_count;
static bool accessor_ready;

static noinline unsigned int accessor_cache_size_prototype(struct kmem_cache *cache)
{
    (void)cache;
    return 0;
}
_Static_assert(__builtin_types_compatible_p(typeof(&accessor_cache_size_prototype),
    typeof(&kmem_cache_size)), "native slab object size prototype");

static int accessor_owner(unsigned long (*lookup)(const char *),
    unsigned int (*size_of)(struct kmem_cache *), const char *symbol, u32 *bytes)
{
    unsigned long slot = lookup(symbol);
    struct kmem_cache *cache;
    unsigned int size;
    if (!slot || (slot & (sizeof(cache) - 1))) return -ENOENT;
    if (copy_from_kernel_nofault(&cache, (const void *)slot, sizeof(cache))) return -EFAULT;
    if (!cache || ((unsigned long)cache & (sizeof(void *) - 1))) return -EINVAL;
    /* The native accessor reads its own private slab layout. Object size is
     * allocation evidence; neither symbol spacing nor a compiled sizeof is used. */
    size = size_of(cache);
    if (!size || size > (1U << 20)) return -EOPNOTSUPP;
    *bytes = size;
    return 0;
}

int msu_runtime_facts_cache_extent(const char *cache_symbol, u32 *output)
{
    unsigned int (*size_of)(struct kmem_cache *);
    unsigned long function;
    int error;
    if (!cache_symbol || !*cache_symbol || !output) return -EINVAL;
    error = msu_init_bridge_resolve_cfi("kmem_cache_size", (void *)accessor_cache_size_prototype, &function);
    if (error) return error;
    size_of = (typeof(size_of))function;
    return accessor_owner(msu_init_bridge_lookup, size_of, cache_symbol, output);
}

static bool accessor_hint(u32 word)
{
    return word == 0xd503201fU || word == 0xd503233fU || word == 0xd50323bfU ||
        word == 0xd503211fU || word == 0xd503219fU ||
        (word & 0xffffff3fU) == 0xd503241fU;
}

/* The SDK's target-text accessor rule, restricted to a fully witnessed
 * straight-line leaf. Unknown instructions/calls/branches stop resolution.
 * No instruction scanner, adjacent-field inference or candidate offset table. */
static int accessor_getter(const char *name, bool task_scalar,
    struct msu_accessor_value *output)
{
    struct msu_accessor_value values[32] = {0};
    unsigned long address, bytes, at;
    int error = msu_init_bridge_lookup_size(name, &address, &bytes);
    if (error) return error;
    if (bytes < 4 || (bytes & 3)) return -EOPNOTSUPP;
    if (bytes > MSU_ACCESSOR_WORDS * 4) bytes = MSU_ACCESSOR_WORDS * 4;
    for (at = 0; at < bytes; at += 4) {
        u32 word, rd, rn, rm;
        if (copy_from_kernel_nofault(&word, (const void *)(address + at), sizeof(word))) return -EFAULT;
        rd = word & 31; rn = word >> 5 & 31; rm = word >> 16 & 31;
        if (accessor_hint(word)) continue;
        if (word == 0xd65f03c0U) {
            if ((!task_scalar && values[0].kind == 3 && !(values[0].field & 7) &&
                 !(values[0].offset & 3)) ||
                (task_scalar && values[0].kind == 4 && !(values[0].offset & 3))) {
                *output = values[0];
                return 0;
            }
            return -EOPNOTSUPP;
        }
        if ((word & 0xffffffe0U) == 0xd5384100U) {
            values[rd] = (struct msu_accessor_value){ .kind = 1 }; /* SP_EL0 current */
        } else if ((word & 0x7fe0ffe0U) == 0x2a0003e0U) {
            values[rd] = (word & 0x80000000U) || values[rm].kind == 3 || values[rm].kind == 4 ?
                values[rm] : (struct msu_accessor_value){0};
        } else if ((word & 0x3b000000U) == 0x39000000U && !(word & (1U << 26))) {
            u32 width = 1U << (word >> 30), operation = word >> 22 & 3;
            u32 offset = (word >> 10 & 4095) * width;
            struct msu_accessor_value base = values[rn], next = {0};
            if (operation != 1) return -EOPNOTSUPP;
            if (base.kind == 1 && width == 8 && !task_scalar)
                next = (struct msu_accessor_value){ .kind = 2, .field = offset };
            else if (base.kind == 2 && width == 4 && !task_scalar)
                next = (struct msu_accessor_value){ .kind = 3, .field = base.field, .offset = offset };
            else if (base.kind == 1 && width == 4 && task_scalar)
                next = (struct msu_accessor_value){ .kind = 4, .offset = offset };
            values[rd] = next;
        } else return -EOPNOTSUPP;
        values[31] = (struct msu_accessor_value){0};
    }
    return -EOPNOTSUPP;
}

static int accessor_id(const char *first, const char *second, struct msu_accessor_value *output)
{
    struct msu_accessor_value selected = {0}, candidate;
    const char *names[2] = {first, second};
    unsigned int i, matches = 0;
    for (i = 0; i < 2; ++i) {
        int error = accessor_getter(names[i], false, &candidate);
        if (error == -ENOENT) continue;
        if (error) return error;
        if (matches && memcmp(&selected, &candidate, sizeof(candidate))) return -ENOTUNIQ;
        selected = candidate; matches++;
    }
    if (!matches) return -ENOENT;
    *output = selected;
    return 0;
}

noinline __used __visible int msu_runtime_accessor_init(unsigned long (*lookup)(const char *),
    int (*resolve_cfi)(const char *, const void *, unsigned long *))
{
    struct msu_accessor_fact next[4] = {0};
    struct msu_accessor_value uid, euid, seccomp;
    unsigned int (*size_of)(struct kmem_cache *);
    unsigned long function;
    u32 task_bytes, cred_bytes, count = 0;
    int error;
    WRITE_ONCE(accessor_ready, false);
    if (!lookup || !resolve_cfi) return -EINVAL;
    error = resolve_cfi("kmem_cache_size", (void *)accessor_cache_size_prototype, &function);
    if (error) return error;
    size_of = (typeof(size_of))function;
    error = accessor_owner(lookup, size_of, "task_struct_cachep", &task_bytes);
    if (error) return error;
    error = accessor_owner(lookup, size_of, "cred_jar", &cred_bytes);
    if (error) return error;
    error = accessor_id("__arm64_sys_getuid", "sys_getuid", &uid);
    if (error) return error;
    error = accessor_id("__arm64_sys_geteuid", "sys_geteuid", &euid);
    if (error) return error;
    if (uid.field != euid.field || uid.field > task_bytes || 8 > task_bytes - uid.field ||
        uid.offset > cred_bytes || 4 > cred_bytes - uid.offset ||
        euid.offset > cred_bytes || 4 > cred_bytes - euid.offset || uid.offset == euid.offset)
        return -EOPNOTSUPP;
#define FACT(owner_name, member_name, at, width, extent, btf_kind) do { \
    next[count++] = (struct msu_accessor_fact){ .owner = owner_name, .member = member_name, \
        .field = { .offset = at, .bytes = width, .owner_bytes = extent, .kind = btf_kind } }; \
} while (0)
    /* A scalar load proves storage width, not whether the C declaration is
     * an integer or a wrapper such as kuid_t. Leave that BTF kind unknown. */
    FACT("task_struct", "cred", uid.field, 8, task_bytes, 2);
    FACT("cred", "uid", uid.offset, 4, cred_bytes, 0);
    FACT("cred", "euid", euid.offset, 4, cred_bytes, 0);
    error = accessor_getter("prctl_get_seccomp", true, &seccomp);
    if (error == -ENOENT) error = accessor_getter("seccomp_mode", true, &seccomp);
    if (!error) {
        if (seccomp.offset > task_bytes || 4 > task_bytes - seccomp.offset) return -EOPNOTSUPP;
        FACT("task_struct", "seccomp.mode", seccomp.offset, 4, task_bytes, 0);
    } else if (error != -ENOENT && error != -EOPNOTSUPP) return error;
#undef FACT
    memcpy(accessor_facts, next, sizeof(next));
    accessor_count = count;
    smp_store_release(&accessor_ready, true);
    return 0;
}

int msu_runtime_accessor_field(const char *owner, const char *member,
    struct msu_runtime_btf_field *output)
{
    u32 i;
    if (!owner || !*owner || !member || !*member || !output) return -EINVAL;
    if (!smp_load_acquire(&accessor_ready)) return -EAGAIN;
    for (i = 0; i < accessor_count; ++i) {
        if (strcmp(owner, accessor_facts[i].owner) || strcmp(member, accessor_facts[i].member)) continue;
        *output = accessor_facts[i].field;
        return 0;
    }
    return -ENOENT;
}

int msu_runtime_facts_field(const char *owner, const char *member,
    struct msu_runtime_btf_field *output)
{
    int error = msu_runtime_btf_field(owner, member, output), fallback;
    if (error != -ENOENT) return error;
    if (!strcmp(owner, "filename") && !strcmp(member, "name")) {
        extern int msu_su_runtime_filename_field(struct msu_runtime_btf_field *);
        return msu_su_runtime_filename_field(output);
    }
    fallback = msu_runtime_accessor_field(owner, member, output);
    return fallback == -EAGAIN ? error : fallback;
}

int msu_runtime_facts_type_size(const char *owner, u32 *output)
{
    /* Cache allocation extent is not evidence for an exact by-value ABI size. */
    return msu_runtime_btf_type_size(owner, output);
}
