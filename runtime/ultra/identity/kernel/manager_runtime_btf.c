// SPDX-License-Identifier: GPL-3.0-only
/* Kernel-side reader for the same raw vmlinux BTF used by kernel_layout.cpp.
 * No private struct btf layout or userspace/SDK channel is needed to bootstrap.
 * Like the shared reader, resolve modifiers, embedded paths and anonymous
 * aggregates; reject ambiguous definitions, bitfields and truncated records. */
#include <linux/compiler.h>
#include <linux/errno.h>
#include <linux/slab.h>
#include <linux/smp.h>
#include <linux/string.h>
#include <linux/vmalloc.h>
#include "manager_runtime_btf.h"

#define MSU_BTF_MAX_BYTES (64U * 1024U * 1024U)
#define MSU_BTF_MAX_DEPTH 32U
struct msu_btf_image {
    const u8 *data;
    u32 *offsets;
    u32 count, types, type_bytes, strings, string_bytes;
};
static struct msu_btf_image running;
static bool running_ready;

static u32 btf_word(const u8 *p)
{ return (u32)p[0] | (u32)p[1] << 8 | (u32)p[2] << 16 | (u32)p[3] << 24; }
static u32 btf_kind(const u8 *t) { return btf_word(t + 4) >> 24 & 31; }
static u32 btf_count(const u8 *t) { return btf_word(t + 4) & 65535; }
static const u8 *btf_type(u32 id)
{
    return id && id <= running.count ? running.data + running.offsets[id] : NULL;
}
static const char *btf_name(u32 offset)
{
    const char *name;
    if (offset >= running.string_bytes) return NULL;
    name = (const char *)running.data + running.strings + offset;
    return memchr(name, 0, running.string_bytes - offset) ? name : NULL;
}
static int btf_resolve(u32 id, u32 *resolved)
{
    unsigned int depth;
    for (depth = 0; depth < MSU_BTF_MAX_DEPTH; ++depth) {
        const u8 *t;
        u32 kind;
        if (!id) { *resolved = 0; return 0; }
        t = btf_type(id);
        if (!t) return -EINVAL;
        kind = btf_kind(t);
        if (kind != 8 && kind != 9 && kind != 10 && kind != 11 && kind != 18) {
            *resolved = id;
            return 0;
        }
        id = btf_word(t + 8);
    }
    return -ELOOP;
}
static int btf_size(u32 id, unsigned int depth, u32 *bytes)
{
    const u8 *t;
    u32 resolved, element, count;
    int error;
    if (depth == MSU_BTF_MAX_DEPTH) return -ELOOP;
    error = btf_resolve(id, &resolved);
    if (error) return error;
    if (!resolved) { *bytes = 0; return 0; }
    t = btf_type(resolved);
    switch (btf_kind(t)) {
    case 2: *bytes = 8; return 0;
    case 1: case 4: case 5: case 6: case 16: case 19:
        *bytes = btf_word(t + 8); return 0;
    case 3:
        error = btf_size(btf_word(t + 12), depth + 1, &element);
        if (error) return error;
        count = btf_word(t + 20);
        if (count && element > ~0U / count) return -EOVERFLOW;
        *bytes = element * count; return 0;
    case 7: case 12: case 13: *bytes = 0; return 0;
    default: return -EOPNOTSUPP;
    }
}
static int btf_record(const u8 *t, u32 remaining, u32 *bytes)
{
    u32 extra, count;
    if (remaining < 12) return -EINVAL;
    count = btf_count(t);
    switch (btf_kind(t)) {
    case 1: case 14: case 17: extra = 4; break;
    case 3: extra = 12; break;
    case 4: case 5: case 15: case 19: extra = count * 12; break;
    case 6: case 13: extra = count * 8; break;
    case 2: case 7: case 8: case 9: case 10: case 11: case 12: case 16: case 18:
        extra = 0; break;
    default: return -EOPNOTSUPP;
    }
    if (extra > remaining - 12) return -EINVAL;
    *bytes = 12 + extra;
    return 0;
}

static int btf_open(const void *blob, u32 length)
{
    struct msu_btf_image next = { .data = blob };
    const u8 *data = blob;
    u32 header, types, strings, at, record, count = 0;
    int error;
    if (!blob || length < 24 || length > MSU_BTF_MAX_BYTES ||
        data[0] != 0x9f || data[1] != 0xeb || data[2] != 1 || data[3]) return -EINVAL;
    header = btf_word(data + 4);
    types = btf_word(data + 8); next.type_bytes = btf_word(data + 12);
    strings = btf_word(data + 16); next.string_bytes = btf_word(data + 20);
    if (header < 24 || header > length || types > length - header ||
        next.type_bytes > length - header - types || strings > length - header ||
        !next.string_bytes || next.string_bytes > length - header - strings) return -EINVAL;
    next.types = header + types; next.strings = header + strings;
    if (!(next.types + next.type_bytes <= next.strings ||
        next.strings + next.string_bytes <= next.types) || data[next.strings]) return -EINVAL;
    for (at = 0; at < next.type_bytes; at += record) {
        if (next.type_bytes - at < 12) return -EINVAL;
        error = btf_record(data + next.types + at, next.type_bytes - at, &record);
        if (error) return error;
        count++;
    }
    if (!count) return -EINVAL;
    next.offsets = kvcalloc((size_t)count + 1, sizeof(u32), GFP_KERNEL);
    if (!next.offsets) return -ENOMEM;
    next.count = count;
    for (at = 0, count = 1; at < next.type_bytes; at += record, count++) {
        next.offsets[count] = next.types + at;
        error = btf_record(data + next.types + at, next.type_bytes - at, &record);
        if (error) { kvfree(next.offsets); return error; }
    }
    running = next;
    smp_store_release(&running_ready, true);
    return 0;
}

noinline __used __visible int msu_runtime_btf_init(unsigned long (*lookup)(const char *))
{
    unsigned long start, end;
    if (!lookup) return -EINVAL;
    if (running.offsets) return -EALREADY;
    start = lookup("__start_BTF"); end = lookup("__stop_BTF");
    if (!start && !end) return -ENOENT;
    if (!start || end <= start || end - start > MSU_BTF_MAX_BYTES || (start & 3)) return -EINVAL;
    return btf_open((const void *)start, (u32)(end - start));
}
void msu_runtime_btf_release(void)
{
    /* Only bootstrap failure releases facts; no published callback is a reader. */
    WRITE_ONCE(running_ready, false);
    kvfree(running.offsets);
    memset(&running, 0, sizeof(running));
}

static int btf_field(u32 id, const char *path, u32 prefix, unsigned int depth,
    struct msu_runtime_btf_field *output)
{
    const u8 *t;
    const char *dot = strchr(path, '.');
    struct msu_runtime_btf_field selected = {0};
    u32 resolved, i, matches = 0;
    size_t wanted = dot ? (size_t)(dot - path) : strlen(path);
    int error;
    if (depth == MSU_BTF_MAX_DEPTH) return -ELOOP;
    if (!wanted || (dot && !dot[1])) return -EINVAL;
    error = btf_resolve(id, &resolved);
    if (error) return error;
    t = btf_type(resolved);
    if (!t || (btf_kind(t) != 4 && btf_kind(t) != 5)) return -EOPNOTSUPP;
    for (i = 0; i < btf_count(t); ++i) {
        const u8 *m = t + 12 + i * 12;
        const char *name = btf_name(btf_word(m));
        struct msu_runtime_btf_field field = {0};
        u32 member = btf_word(m + 4), offset = btf_word(m + 8), bits = 0, size, value;
        const u8 *mt;
        if (!name) return -EINVAL;
        if (btf_word(t + 4) >> 31) { bits = offset >> 24; offset &= 0xffffff; }
        if (*name && (strlen(name) != wanted || strncmp(name, path, wanted))) continue;
        if (bits || (offset & 7)) {
            if (*name) return -EOPNOTSUPP;
            continue;
        }
        error = btf_resolve(member, &value);
        if (error) return error;
        mt = btf_type(value);
        if (!mt) return -EINVAL;
        error = btf_size(member, 0, &size);
        if (error) return error;
        offset /= 8;
        if (offset > btf_word(t + 8) || size > btf_word(t + 8) - offset ||
            prefix > ~0U - offset) return -EINVAL;
        if (!*name) {
            if (btf_kind(mt) != 4 && btf_kind(mt) != 5) continue;
            error = btf_field(member, path, prefix + offset, depth + 1, &field);
            if (error == -ENOENT) continue;
            if (error) return error;
        } else if (dot) {
            error = btf_field(member, dot + 1, prefix + offset, depth + 1, &field);
            if (error) return error;
        } else {
            if (btf_kind(mt) == 1) {
                u32 encoding = btf_word(mt + 12);
                if ((encoding >> 16 & 255) || (u64)(encoding & 255) != (u64)size * 8) return -EOPNOTSUPP;
            }
            field.offset = prefix + offset; field.bytes = size;
            field.kind = btf_kind(mt); field.type_id = value;
        }
        if (++matches != 1) return -ENOTUNIQ;
        selected = field;
    }
    if (!matches) return -ENOENT;
    *output = selected;
    return 0;
}

/* Match credential fields to the actual task pointer, rather than an
 * unrelated same-named aggregate retained in vmlinux debug information. */
static int btf_credential_id(u32 *output)
{
    struct msu_runtime_btf_field anchor;
    const u8 *t;
    const char *name;
    u32 resolved;
    int error = msu_runtime_btf_field("task_struct", "cred", &anchor);
    if (error) return error;
    t = btf_type(anchor.type_id);
    if (!t || btf_kind(t) != 2 || anchor.bytes != 8) return -EPROTO;
    error = btf_resolve(btf_word(t + 8), &resolved);
    if (error) return error;
    t = btf_type(resolved);
    if (!t || btf_kind(t) != 4 || !btf_word(t + 8)) return -EPROTO;
    name = btf_name(btf_word(t));
    if (!name || strcmp(name, "cred")) return -EPROTO;
    *output = resolved;
    return 0;
}

int msu_runtime_btf_field(const char *structure, const char *member,
    struct msu_runtime_btf_field *output)
{
    struct msu_runtime_btf_field selected = {0};
    u32 id, matches = 0;
    if (!structure || !*structure || !member || !*member || !output) return -EINVAL;
    if (!smp_load_acquire(&running_ready)) return -ENOENT;
    if (!strcmp(structure, "cred")) {
        int error = btf_credential_id(&id);
        if (error) return error;
        error = btf_field(id, member, 0, 0, &selected);
        if (error) return error;
        selected.owner_bytes = btf_word(btf_type(id) + 8);
        if (!selected.bytes || selected.offset > selected.owner_bytes ||
            selected.bytes > selected.owner_bytes - selected.offset) return -EINVAL;
        *output = selected;
        return 0;
    }
    for (id = 1; id <= running.count; ++id) {
        const u8 *t = btf_type(id);
        const char *name;
        struct msu_runtime_btf_field candidate;
        u32 resolved;
        int error;
        if (btf_kind(t) != 4 && btf_kind(t) != 5 && btf_kind(t) != 8) continue;
        name = btf_name(btf_word(t));
        if (!name) return -EINVAL;
        if (strcmp(name, structure)) continue;
        error = btf_resolve(id, &resolved);
        if (error) return error;
        t = btf_type(resolved);
        if (!t || (btf_kind(t) != 4 && btf_kind(t) != 5) || !btf_word(t + 8)) continue;
        error = btf_field(id, member, 0, 0, &candidate);
        if (error) return error;
        candidate.owner_bytes = btf_word(t + 8);
        if (!candidate.bytes || candidate.offset > candidate.owner_bytes ||
            candidate.bytes > candidate.owner_bytes - candidate.offset) return -EINVAL;
        if (matches && (selected.offset != candidate.offset || selected.bytes != candidate.bytes ||
            selected.owner_bytes != candidate.owner_bytes || selected.kind != candidate.kind)) return -ENOTUNIQ;
        selected = candidate; matches++;
    }
    if (!matches) return -ENOENT;
    *output = selected;
    return 0;
}
int msu_runtime_btf_type_id(const char *structure, u32 *output)
{
    u32 id, selected = 0;
    if (!structure || !*structure || !output) return -EINVAL;
    if (!smp_load_acquire(&running_ready)) return -ENOENT;
    if (!strcmp(structure, "cred")) return btf_credential_id(output);
    for (id = 1; id <= running.count; ++id) {
        const u8 *t = btf_type(id);
        const char *name;
        u32 resolved;
        int error;
        if (btf_kind(t) != 4 && btf_kind(t) != 5 && btf_kind(t) != 8) continue;
        name = btf_name(btf_word(t));
        if (!name) return -EINVAL;
        if (strcmp(name, structure)) continue;
        error = btf_resolve(id, &resolved);
        if (error) return error;
        t = btf_type(resolved);
        if (!t || ((btf_kind(t) == 4 || btf_kind(t) == 5) && !btf_word(t + 8))) continue;
        if (selected && selected != resolved) return -ENOTUNIQ;
        selected = resolved;
    }
    if (!selected) return -ENOENT;
    *output = selected;
    return 0;
}
int msu_runtime_btf_type_size(const char *structure, u32 *output)
{
    u32 id, selected = 0;
    if (!structure || !*structure || !output) return -EINVAL;
    if (!smp_load_acquire(&running_ready)) return -ENOENT;
    if (!strcmp(structure, "cred")) {
        u32 bytes;
        int error = btf_credential_id(&id);
        if (!error) error = btf_size(id, 0, &bytes);
        if (!error) *output = bytes;
        return error;
    }
    for (id = 1; id <= running.count; ++id) {
        const u8 *t = btf_type(id);
        const char *name;
        u32 bytes;
        int error;
        if (btf_kind(t) != 4 && btf_kind(t) != 5 && btf_kind(t) != 8) continue;
        name = btf_name(btf_word(t));
        if (!name) return -EINVAL;
        if (strcmp(name, structure)) continue;
        error = btf_size(id, 0, &bytes);
        if (error) return error;
        if (!bytes) continue;
        /* Identical concrete definitions may retain distinct BTF IDs. A size
         * query needs size agreement, not identity of an unrelated type ID. */
        if (selected && selected != bytes) return -ENOTUNIQ;
        selected = bytes;
    }
    if (!selected) return -ENOENT;
    *output = selected;
    return 0;
}
int msu_runtime_btf_type(u32 id, struct msu_runtime_btf_type *output)
{
    struct msu_runtime_btf_type next = {0};
    const u8 *t;
    int error;
    if (!output) return -EINVAL;
    if (!smp_load_acquire(&running_ready)) return -ENOENT;
    error = btf_resolve(id, &next.id);
    if (error) return error;
    if (!next.id) { next.name = ""; *output = next; return 0; }
    t = btf_type(next.id); next.kind = btf_kind(t);
    next.name = btf_name(btf_word(t));
    if (!next.name) return -EINVAL;
    error = btf_size(next.id, 0, &next.bytes);
    if (error) return error;
    if (next.kind == 2 || next.kind == 12 || next.kind == 13) next.target_id = btf_word(t + 8);
    else if (next.kind == 3) next.target_id = btf_word(t + 12);
    *output = next;
    return 0;
}
static int btf_prototype_id(u32 id, u32 *output)
{
    unsigned int depth;
    for (depth = 0; depth < MSU_BTF_MAX_DEPTH; ++depth) {
        const u8 *t;
        u32 resolved;
        int error = btf_resolve(id, &resolved);
        if (error) return error;
        t = btf_type(resolved);
        if (!t) return -EINVAL;
        if (btf_kind(t) == 13) { *output = resolved; return 0; }
        if (btf_kind(t) != 2 && btf_kind(t) != 12) return -EOPNOTSUPP;
        id = btf_word(t + 8);
    }
    return -ELOOP;
}
int msu_runtime_btf_prototype(u32 id, struct msu_runtime_btf_function *output)
{
    struct msu_runtime_btf_function next = {0};
    const u8 *t;
    u32 resolved, i;
    int error;
    if (!output) return -EINVAL;
    if (!smp_load_acquire(&running_ready)) return -ENOENT;
    error = btf_prototype_id(id, &resolved);
    if (error) return error;
    t = btf_type(resolved);
    if (btf_count(t) > MSU_RUNTIME_BTF_MAX_ARGS) return -EOPNOTSUPP;
    next.return_type = btf_word(t + 8); next.argument_count = btf_count(t);
    for (i = 0; i < next.argument_count; ++i) {
        next.arguments[i] = btf_word(t + 12 + i * 8 + 4);
        if (!next.arguments[i]) {
            if (i + 1 != next.argument_count) return -EINVAL;
            next.variadic = true; next.argument_count--; break;
        }
    }
    *output = next;
    return 0;
}
int msu_runtime_btf_function_id(const char *name, u32 *output)
{
    u32 id, selected = 0;
    if (!name || !*name || !output) return -EINVAL;
    if (!smp_load_acquire(&running_ready)) return -ENOENT;
    for (id = 1; id <= running.count; ++id) {
        const u8 *t = btf_type(id);
        const char *symbol;
        if (btf_kind(t) != 12) continue;
        symbol = btf_name(btf_word(t));
        if (!symbol) return -EINVAL;
        if (strcmp(symbol, name)) continue;
        if (selected && btf_word(btf_type(selected) + 8) != btf_word(t + 8)) {
            struct msu_runtime_btf_function previous, candidate;
            int error = msu_runtime_btf_prototype(selected, &previous);
            if (error) return error;
            error = msu_runtime_btf_prototype(id, &candidate);
            if (error) return error;
            if (memcmp(&previous, &candidate, sizeof(candidate))) return -ENOTUNIQ;
        }
        selected = id;
    }
    if (!selected) return -ENOENT;
    *output = selected;
    return 0;
}
int msu_runtime_btf_function(const char *name, struct msu_runtime_btf_function *output)
{
    struct msu_runtime_btf_function selected = {0};
    u32 id, matches = 0;
    if (!name || !*name || !output) return -EINVAL;
    if (!smp_load_acquire(&running_ready)) return -ENOENT;
    for (id = 1; id <= running.count; ++id) {
        const u8 *t = btf_type(id);
        const char *symbol;
        struct msu_runtime_btf_function next;
        int error;
        if (btf_kind(t) != 12) continue;
        symbol = btf_name(btf_word(t));
        if (!symbol) return -EINVAL;
        if (strcmp(symbol, name)) continue;
        error = msu_runtime_btf_prototype(id, &next);
        if (error) return error;
        if (matches && memcmp(&selected, &next, sizeof(next))) return -ENOTUNIQ;
        selected = next; matches++;
    }
    if (!matches) return -ENOENT;
    *output = selected;
    return 0;
}
int msu_runtime_btf_function_entry(u32 prototype_id, unsigned long (*lookup)(const char *),
    unsigned long *entry)
{
    struct msu_runtime_btf_function prototype;
    u32 id, expected;
    int error;
    if (!lookup || !entry) return -EINVAL;
    if (!smp_load_acquire(&running_ready)) return -ENOENT;
    error = btf_prototype_id(prototype_id, &expected);
    if (error) return error;
    error = msu_runtime_btf_prototype(expected, &prototype);
    if (error) return error;
    for (id = 1; id <= running.count; ++id) {
        const u8 *t = btf_type(id);
        const char *name;
        unsigned long address;
        if (btf_kind(t) != 12) continue;
        if (btf_word(t + 8) != expected) {
            struct msu_runtime_btf_function candidate;
            /* Parameter names can give identical callback types different BTF
             * IDs. Compare callable types before the caller checks live KCFI. */
            if (msu_runtime_btf_prototype(id, &candidate) ||
                memcmp(&prototype, &candidate, sizeof(candidate))) continue;
        }
        name = btf_name(btf_word(t));
        if (!name) return -EINVAL;
        if (!*name) continue;
        address = lookup(name);
        if (!address || (address & 3)) continue;
        *entry = address;
        return 0;
    }
    return -ENOENT;
}
int msu_runtime_btf_enum(const char *name, s64 *output)
{
    u32 id, matches = 0;
    s64 selected = 0;
    if (!name || !*name || !output) return -EINVAL;
    if (!smp_load_acquire(&running_ready)) return -ENOENT;
    for (id = 1; id <= running.count; ++id) {
        const u8 *t = btf_type(id);
        u32 kind = btf_kind(t), i;
        if (kind != 6 && kind != 19) continue;
        for (i = 0; i < btf_count(t); ++i) {
            const u8 *entry = t + 12 + i * (kind == 6 ? 8 : 12);
            const char *value_name = btf_name(btf_word(entry));
            u64 bits;
            s64 value;
            if (!value_name) return -EINVAL;
            if (strcmp(name, value_name)) continue;
            bits = btf_word(entry + 4);
            if (kind == 19) bits |= (u64)btf_word(entry + 8) << 32;
            value = kind == 6 && btf_word(t + 4) >> 31 ? (s64)(s32)bits : (s64)bits;
            if (matches && value != selected) return -ENOTUNIQ;
            selected = value; matches++;
        }
    }
    if (!matches) return -ENOENT;
    *output = selected;
    return 0;
}
