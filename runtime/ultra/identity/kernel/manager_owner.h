// SPDX-License-Identifier: GPL-3.0-only
#ifndef MAPLESU_MANAGER_OWNER_H
#define MAPLESU_MANAGER_OWNER_H

#include <linux/types.h>

struct msu_internal_manager_policy;
struct msu_manager_owner_binding;
struct msu_manager_owner_watch;

/* Private to one retained kernel host. Never supplied by a request or metadata.
 * Initialize zeroed storage once before publication. Once claim/allowed may run,
 * retain the boot-owned host and per-user bindings for the complete boot
 * lifetime. Retired watch marks use fsnotify references and SRCU reclamation. */
struct msu_manager_owner {
    struct msu_manager_owner_binding *bindings;
    struct msu_manager_owner_watch *watch;
    u32 epoch;
    u32 initialized;
};

int msu_manager_owner_init(struct msu_manager_owner *owner);

/* Only a private host that was never published or used by claim/allowed/monitor,
 * with no concurrent caller. Discard init resources before freeing that host.
 * Published hosts and per-user bindings remain live for the complete boot;
 * retired watch marks are reclaimed independently after callback readers drain. */
int msu_manager_owner_discard_unpublished(struct msu_manager_owner *owner);
u32 msu_manager_owner_epoch(const struct msu_manager_owner *owner);

/* Synchronous process context as the actual Manager caller. The existing
 * protected-install verifier is the sole source of signature/install proof.
 * Output is internal state, not a user-wire record or Root grant. */
int msu_manager_owner_claim(struct msu_manager_owner *owner,
    const struct msu_internal_manager_policy *policy, u32 *epoch);

/* actual_uid is captured by the gateway and is checked against current_uid().
 * This path does not allocate, read files, verify APKs or acquire a read lock. */
bool msu_manager_owner_allowed(const struct msu_manager_owner *owner,
    u32 actual_uid, u32 *epoch);
/* Read-only init-user-ns root projection of current signed bindings. No binding
 * returns the fixed KSU invalid appId (~0U); this never enrolls a Manager. */
int msu_manager_owner_appid(const struct msu_manager_owner *owner, u32 *appid);

/* Trusted kernel installation lifecycle only. Advance before revalidation;
 * zero means exhausted/closed until a new boot-owned instance is initialized. */
u32 msu_manager_owner_invalidate(struct msu_manager_owner *owner);

/* These compile-time field accesses must be bound to the actual target by the
 * same loader/layout producer used for the installation verifier. */
extern const unsigned int msu_manager_owner_compiled_layout[2];

#endif
