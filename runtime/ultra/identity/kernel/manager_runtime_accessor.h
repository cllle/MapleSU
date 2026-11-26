/* SPDX-License-Identifier: GPL-3.0-only */
#ifndef MAPLESU_MANAGER_RUNTIME_ACCESSOR_H
#define MAPLESU_MANAGER_RUNTIME_ACCESSOR_H
#include "manager_runtime_btf.h"
/* Bootstrap-only, read-only subset. Every returned offset comes from live
 * accessor instructions and every owner extent from the native slab API.
 * Unsupported fields keep their real error; this is not a compiled fallback. */
int msu_runtime_accessor_init(unsigned long (*lookup)(const char *),
    int (*resolve_cfi)(const char *, const void *, unsigned long *));
int msu_runtime_accessor_field(const char *owner, const char *member,
    struct msu_runtime_btf_field *output);
/* Consumer entry points: preserve authoritative BTF errors, otherwise accept
 * independently witnessed native facts. No type IDs are fabricated. */
int msu_runtime_facts_field(const char *owner, const char *member,
    struct msu_runtime_btf_field *output);
/* Exact type sizes still require type metadata. A slab's object extent is
 * separately named because it may include trailing architecture allocation. */
int msu_runtime_facts_type_size(const char *owner, u32 *output);
int msu_runtime_facts_cache_extent(const char *cache_symbol, u32 *output);
#endif
