// SPDX-License-Identifier: GPL-3.0-only
#ifndef MAPLESU_MANAGER_KSU_COMPAT_H
#define MAPLESU_MANAGER_KSU_COMPAT_H
#include <linux/types.h>

/* The four read-only queries used by official Zygisk Next 1.5.3. This is not
 * a claim that the complete KernelSU UAPI 6 or its mutation commands exist. */
#define MSU_KSU_COMPAT_VERSION 10940U
#define MSU_KSU_COMPAT_GET_INFO 0x80104b02U
#define MSU_KSU_COMPAT_GET_INFO_LEGACY 0x80004b02U
#define MSU_KSU_COMPAT_UID_GRANTED_ROOT 0xc0004b08U
#define MSU_KSU_COMPAT_UID_SHOULD_UMOUNT 0xc0004b09U
#define MSU_KSU_COMPAT_GET_MANAGER_APPID 0x80004b0aU
#define MSU_KSU_COMPAT_INVALID_APPID (~0U)
struct msu_ksu_compat_info { u32 version, flags, features, uapi_version; };
struct msu_ksu_compat_uid { u32 uid; u8 value, reserved[3]; };
struct pt_regs;
struct msu_resident_slot;
int msu_ksu_compat_bind_boot_resident(const struct msu_resident_slot *slot,
    const void *memory, u64 allocation_bytes);
int msu_ksu_compat_open(void);
/* Close admission before host teardown. Idle descriptors own no registry or
 * grant reference; resident retirement waits for their real final release. */
int msu_ksu_compat_close(void);
/* False leaves the original syscall and all arguments unchanged. */
bool msu_ksu_compat_reboot(const struct pt_regs *regs, long *result);
#define MSU_KSU_COMPAT_LAYOUT_WORDS 14U
extern const u32 msu_ksu_compat_layout[MSU_KSU_COMPAT_LAYOUT_WORDS];
#endif
