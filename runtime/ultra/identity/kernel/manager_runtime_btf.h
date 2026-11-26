/* SPDX-License-Identifier: GPL-3.0-only */
#ifndef MAPLESU_MANAGER_RUNTIME_BTF_H
#define MAPLESU_MANAGER_RUNTIME_BTF_H
#include <linux/types.h>

/* IDs and names belong to this boot's vmlinux BTF, never a compiled template.
 * All output arguments remain unchanged on failure. Queries are read-only. */
struct msu_runtime_btf_field {
    u32 offset, bytes, owner_bytes, kind, type_id;
};
struct msu_runtime_btf_type {
    u32 id, kind, bytes, target_id;
    const char *name;
};
#define MSU_RUNTIME_BTF_MAX_ARGS 16U
struct msu_runtime_btf_function {
    u32 return_type, argument_count, arguments[MSU_RUNTIME_BTF_MAX_ARGS];
    bool variadic;
};

int msu_runtime_btf_init(unsigned long (*lookup)(const char *));
void msu_runtime_btf_release(void);
int msu_runtime_btf_field(const char *structure, const char *member,
    struct msu_runtime_btf_field *output);
int msu_runtime_btf_type_id(const char *structure, u32 *output);
int msu_runtime_btf_type_size(const char *structure, u32 *output);
int msu_runtime_btf_type(u32 id, struct msu_runtime_btf_type *output);
int msu_runtime_btf_prototype(u32 id, struct msu_runtime_btf_function *output);
int msu_runtime_btf_function(const char *name, struct msu_runtime_btf_function *output);
int msu_runtime_btf_function_id(const char *name, u32 *output);
int msu_runtime_btf_function_entry(u32 prototype_id, unsigned long (*lookup)(const char *),
    unsigned long *entry);
int msu_runtime_btf_enum(const char *name, s64 *output);
#endif
