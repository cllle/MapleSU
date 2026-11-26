/* SPDX-License-Identifier: GPL-3.0-or-later */
#ifndef MAPLESU_LEGACY_DIRECT_BIND_H
#define MAPLESU_LEGACY_DIRECT_BIND_H
#include <stddef.h>
#include <stdint.h>
#define MSU_DIRECT_LIMIT 78U

typedef int (*msu_direct_resolver)(void *, const char *const *, uint64_t *, size_t);
/* 0: ordinary module, 1: marked direct calls bound, negative errno: reject. */
int msu_bind_legacy_direct(void *module, size_t bytes, msu_direct_resolver resolver, void *context);
/* Only a declared runtime-facts component is eligible for the one-shot retry. */
int msu_component_vermagic(const void *module, size_t bytes, char *magic, size_t capacity);
int msu_required_vermagic(const char *record, size_t bytes, const char *current,
    char *required, size_t capacity);
int msu_replace_component_vermagic(unsigned char **module, size_t *bytes,
    const char *current, const char *required);
#endif
