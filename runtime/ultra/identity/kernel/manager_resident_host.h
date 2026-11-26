// SPDX-License-Identifier: GPL-3.0-only
#ifndef MAPLESU_MANAGER_RESIDENT_HOST_H
#define MAPLESU_MANAGER_RESIDENT_HOST_H
#include "manager_resident_abi.h"
#include "request_protocol.h"

/* Loader-only entries. All require sleepable task context and reject atomic
 * or IRQ-disabled callers with -EWOULDBLOCK before init, cleanup or dispatch.
 * Init and discard run before release publication; once dispatch is reachable,
 * code/data/owner/watch all live until the next boot. */
int msu_manager_resident_init(const struct msu_resident_config *config);
int msu_manager_resident_discard(void);
/* Kernel-only immutable path fact; no install/Ready authority or user transport. */
int msu_manager_resident_deployment(struct msu_deployment_namespace_record *output);
/* Root-only readback of actually signed current Manager bindings. A missing
 * binding is the KSU invalid appId; it does not enroll or start the Manager. */
int msu_manager_resident_ksu_appid(u32 *appid);
/* Private helper-session metadata copy. Its epoch/principal come only from the
 * authenticated current task session, never a caller-supplied vendor snapshot. */
int msu_manager_resident_helper_metadata(u32 principal_uid, u32 epoch,
    maplesu_metadata_owner_v3 *output, u64 *descriptor);
int msu_manager_resident_dispatch(const char *name);
#endif
