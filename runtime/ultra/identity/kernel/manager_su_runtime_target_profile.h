// SPDX-License-Identifier: GPL-3.0-only
#ifndef MSU_SU_RUNTIME_TARGET_PROFILE_H
#define MSU_SU_RUNTIME_TARGET_PROFILE_H
#include <asm/compat.h>
struct user_arg_ptr {
#ifdef CONFIG_COMPAT
    bool is_compat;
#endif
    union {
        const char __user *const __user *native;
#ifdef CONFIG_COMPAT
        const compat_uptr_t __user *compat;
#endif
    } ptr;
};
typedef int (*msu_su_exec_source_fn)(int, struct filename *, struct user_arg_ptr, struct user_arg_ptr, int);
_Static_assert(sizeof(((struct filename *)0)->name) == sizeof(void *), "native filename pointer");
_Static_assert(sizeof(((struct pt_regs *)0)->regs[0]) == sizeof(unsigned long), "native register word");

/* Exact input-image facts or facts produced from this boot's live kernel.
 * Kernel identity is not a shipping profile: selected entries always supply
 * their own instruction/KCFI bytes before ready is release-published. */
#define MSU_SU_TARGET_PROFILE_MAGIC 0x4d535550U
#define MSU_SU_TARGET_PROFILE_SCHEMA 1U
#define MSU_SU_TARGET_PROFILE_COUNT 5U
struct msu_su_target_profile { char name[40]; u32 kcfi; u32 entry[6]; };
struct msu_su_runtime_profile_table {
    u32 magic, schema, bytes, count, ready, kcfi_mode, filename_offset, reserved;
    struct msu_su_target_profile values[MSU_SU_TARGET_PROFILE_COUNT];
};
extern struct msu_su_runtime_profile_table msu_su_runtime_profiles;
#define msu_su_target_profiles (msu_su_runtime_profiles.values)
#endif
