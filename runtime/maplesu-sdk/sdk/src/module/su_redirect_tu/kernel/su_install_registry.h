// SPDX-License-Identifier: GPL-3.0-only
#ifndef MAPLESU_SU_INSTALL_REGISTRY_H
#define MAPLESU_SU_INSTALL_REGISTRY_H
#include <linux/types.h>
#include "request_protocol.h"

#define MSU_SU_REGISTRY_MAX_UIDS 4095U
#define MSU_SU_INSTALL_ID_BYTES 32U
#define MSU_SU_PROFILE_GROUPS 32U
#define MSU_SU_PROFILE_CONTEXT 64U
#define MSU_SU_INSTALL_MAX_MEMBERS 128U

struct msu_su_registry;
struct msu_su_update;
struct msu_uid_snapshot;
struct task_struct;
struct mm_struct;
struct cred;

/* Immutable business input. Interpretation/apply belongs to the single A cred
 * core. Unsupported fields must fail its validate_profile callback; storing
 * fields does not establish that they are applied. */
struct msu_su_profile {
    u32 uid, gid, groups_count;
    u32 groups[MSU_SU_PROFILE_GROUPS];
    /* Resolved KSU business fields: effective selects its actual capability
     * writes; the remaining compatibility fields are not five independent
     * credential writes. The unique core checks the resulting active masks. */
    u64 inheritable, permitted, effective, bounding, ambient;
    char selinux_domain[MSU_SU_PROFILE_CONTEXT];
    int namespace_mode;
    u64 flags;
    bool use_default, non_root, umount_modules;
    char template_name[256];
    /* Complete stored branch metadata; admission decision is separate. */
    char name[256];
    bool explicit_profile, allow_su, non_root_use_default, template_present;
};

struct msu_su_install_member {
    char package[256];
};
struct msu_su_verified_install {
    u32 full_uid;
    u8 installation_id[MSU_SU_INSTALL_ID_BYTES];
    const void *pin;
    const struct msu_su_install_member *members;
    u32 member_count;
};

struct msu_su_saved_grant {
    u32 full_uid, decision;
    u8 installation_id[MSU_SU_INSTALL_ID_BYTES];
    struct msu_su_profile profile;
};

/* P2's real kernel factory supplies held fullUID installation observations
 * and the existing protected durable authority. Ordinary application sources
 * may follow KSU's protected packages.list/PM-file observation model: member
 * changes close affected admission when observed, not before PMS memory commits.
 * installation_id identifies that immutable observed object and never proves a
 * zero-window PMS install epoch. Manager signed mutation identity is separate.
 * read_grant is the same protected authority, not a second grant store; -ENOENT
 * means no grant and every other error keeps admission closed. Callbacks stay
 * off the native SU reader path. The owner/code outlive every held pin.
 * Successful create consumes one already-held owner reference; failure none.
 * check_closed proves no gate/source producer entry during initial bind.
 * quiesce irreversibly closes future entries and returns -EINPROGRESS while
 * existing producer/task-work/gate callers remain. It never waits for callers
 * blocked on gateway/session locks or reenters registry destruction. put_owner
 * runs only after the registry and all pins have actually retired.
 * validate_producer checks retained factory admission, not Manager lifetime.
 */
struct msu_su_registry_source {
    const void *owner;
    int (*pin_current)(const void *owner, u32 full_uid,
        struct msu_su_verified_install *installation);
    void (*put_install)(const void *owner, const void *pin);
    int (*read_grant)(const void *owner,
        const struct msu_su_verified_install *installation,
        struct msu_su_saved_grant *grant);
    int (*validate_profile)(const struct msu_su_profile *profile);
    int (*check_closed)(const void *owner);
    int (*quiesce)(const void *owner);
    void (*put_owner)(const void *owner);
    int (*validate_producer)(const void *owner);
};

struct msu_su_revoke_receipt {
    u64 generation;
    u32 full_uid, inflight;
};
enum msu_su_package_change {
    MSU_SU_PACKAGE_INSTALL = 1,
    MSU_SU_PACKAGE_UPDATE,
    MSU_SU_PACKAGE_REMOVE,
};
enum msu_su_non_root_umount_policy {
    MSU_SU_NON_ROOT_UMOUNT_DEFAULT = 0,
    MSU_SU_NON_ROOT_UMOUNT_DISABLED,
    MSU_SU_NON_ROOT_UMOUNT_ENABLED,
};

/* Factory-only construction; no user request registers a source or binding.
 * Entries are nonreused UID tombstones, at most 4095 per registry; repeat
 * installations of the same UID reuse its slot only after leases have drained.
 * This local generation identifies that pinned object; it is not proof of an
 * Android installation. No Manager epoch is part of this runtime lifetime. */
int msu_su_registry_create(const maplesu_host_instance *host, u64 controller,
    const struct msu_su_registry_source *source, struct msu_su_registry **output);
/* P2 calls immediately after create, before refresh or exposure. Binds only
 * the retained host/controller to CORE's initial empty revision 1. The gateway
 * now invokes the real registry reservation/revoke/drain/finish before every
 * affected commit and shutdown. A bound registry remains held until the actual
 * gateway retire consumes it after actual host discard/state withdrawal and
 * factory quiescence. Ordinary UID_SHUTDOWN does not release the owner. */
int msu_su_registry_bind_gateway(struct msu_su_registry *registry);

int msu_su_registry_refresh(struct msu_su_registry *registry, u32 full_uid,
    maplesu_uid_record_v3 *record);

/* Slow, kernel-only producer for the existing controller. Outputs the complete
 * ordered table from held installation/Profile objects, never accepting user
 * generation as authority. Revoked entries export denials. Capacity is bounded
 * by 4095; no partial output/count is published on failure. */
int msu_su_registry_rows(struct msu_su_registry *registry,
    maplesu_uid_record_v3 *records, u32 capacity, u32 *count);

/* Slow factory-only producers on the SAME bound controller/staging table.
 * Prepare exports only real held registry rows at a captured revision; it does
 * not refresh an installation or invent its grant. Its response.result is the
 * existing CORE candidate handle whenever ALLOCATED is set, even on a later
 * validation error. The factory retains that handle, source/API owner and exact
 * response. Commit PENDING without COMMITTED/REFERENCE_DROPPED keeps the handle
 * for explicit continuation; consumed/unknown completion must never be replayed.
 * Cancel drops this registry owner's candidate only, including during close.
 * Host discard cancels any remaining candidates before successful retire.
 * All effects and native errors are preserved; no session/Key or new protocol. */
int msu_su_registry_prepare_kernel(struct msu_su_registry *registry,
    maplesu_response_owner_v3 *response);
int msu_su_registry_commit_kernel(struct msu_su_registry *registry, u64 candidate,
    maplesu_response_owner_v3 *response);
int msu_su_registry_cancel_kernel(struct msu_su_registry *registry, u64 candidate,
    maplesu_response_owner_v3 *response);

/* A's authenticated gateway holds its existing session/gateway mutation locks
 * and passes the exact, immutable PREPARE candidate's KERNEL readback here,
 * before UID_COMMIT. Only rows matching this registry's real pin/Profile are
 * accepted. Changed/removed grants close admission BEFORE commit; an old cred
 * lease returns -EINPROGRESS and no update token, so commit must wait. The
 * supplied receipt identifies the first affected fullUID still executing.
 * Successful begin pins this producer until finish and excludes refresh. PMS
 * invalidation remains non-sleeping and may cancel admission at any time.
 * These APIs do not create another controller, wire or publication path. */
int msu_su_registry_update_begin(struct msu_su_registry *registry,
    const maplesu_uid_record_v3 *records, u32 count, u64 expected_revision,
    struct msu_su_update **update, struct msu_su_revoke_receipt *receipt);
/* Call exactly once after the existing msu_uid_snapshot_commit attempt, even
 * when its final management validation/reply delivery fails. Pass that commit
 * primitive's result, not the later request status. Actual gateway readback
 * must match the planned rows/revision before any new admission opens. Failure
 * retains closed affected grants; no unknown commit is retried or rolled back.
 * Return status never erases A's independently recorded COMMITTED effects. */
int msu_su_registry_update_finish(struct msu_su_update **update, int commit_error);

/* Bounded non-sleeping invalidation. Existing leases retain their pin/Profile;
 * a post-validation/pre-commit race remains PENDING until lease_end. Caller
 * retains old execution until its receipt drains. The observer does not block
 * Android PMS or promise pre-memory-commit revocation. The same local fence is
 * required BEFORE an authenticated gateway deny/Profile replacement
 * becomes visible; a new snapshot by itself does not drain old cred commits.
 * Repeating revoke is idempotent; refresh is the explicit post-commit action. */
int msu_su_registry_revoke(struct msu_su_registry *registry, u32 full_uid,
    struct msu_su_revoke_receipt *receipt);
/* The protected observer supplies the actual affected fullUID/member on change.
 * Any member change revokes that shared UID's one Linux credential authority.
 * The name only selects within the pinned trusted member list for update or
 * remove. INSTALL also invalidates when a new member joins that shared UID. */
int msu_su_registry_revoke_package(struct msu_su_registry *registry, u32 full_uid,
    const char *package, enum msu_su_package_change change,
    struct msu_su_revoke_receipt *receipt);
/* Read the current task's published non-root Profile projection. DEFAULT leaves
 * the global policy to MODULE; this function never performs an unmount. */
int msu_su_registry_non_root_policy(struct msu_su_registry *registry,
    u32 full_uid, enum msu_su_non_root_umount_policy *policy);
/* Independent read-only daemon query. Requires actual init-user-ns root and
 * reads the TARGET fullUID's same installation/Profile and published generation.
 * It does not acquire an SU execution lease or evaluate the daemon's own UID. */
int msu_su_registry_query_root(struct msu_su_registry *registry, u32 full_uid,
    bool *granted, enum msu_su_non_root_umount_policy *policy);
/* Scheduling hint for the root zygote's upcoming setresuid, never an action or
 * authorization receipt. Bounded memory only; the successful syscall's current
 * task must still call non_root_policy before doing any namespace operation. */
bool msu_su_registry_non_root_wants_transition(struct msu_su_registry *registry,
    u32 full_uid);
int msu_su_registry_non_root_skip_manager(struct msu_su_registry *registry, bool *skip);
int msu_su_registry_shutdown(struct msu_su_registry *registry);
/* Only a NEVER-BOUND factory may destroy directly, after shutdown/drain and
 * successful source.quiesce. A bound registry always returns -EINPROGRESS here,
 * even after successful UID_SHUTDOWN. Only CORE's mandatory retire callback,
 * after state withdrawal/outer RCU, may consume that binding's registry. Zero
 * consumes the registry and its source ref; no caller may use either afterward.
 * Every negative result retains them. Factory serializes unbound destruction. */
int msu_su_registry_destroy_quiesced(struct msu_su_registry *registry);

struct msu_su_lease {
    struct msu_su_registry *registry;
    void *entry;
    struct msu_uid_snapshot *snapshot;
    struct task_struct *task;
    struct mm_struct *mm;
    const struct cred *cred;
    u64 generation, exec_id;
    bool invalidated;
};

/* Discovery-only native probe; the caller retains the actual factory registry.
 * Checks current fullUID, published/live installation generation and qualified
 * disable-root state without credential/mm refs or a full execution lease.
 * No disk, Manager, source callback, allocation or sleeping lock; two binary
 * lookups <=12 iterations each plus the existing bounded gateway lookup.
 * This result grants no credentials: actual SU execution must still acquire
 * and validate the full lease/Profile in session_prepare before any mutation. */
u64 msu_su_read_current(const void *context);
int msu_su_lease_begin(const void *context, struct msu_su_lease *lease);
/* Checks only this fullUID's current row, preserving leases across unrelated
 * publications. Any observed loss is sticky for that lease; an ABA table or
 * cred restoration never revives it. Caller keeps the old snapshot held. */
int msu_su_lease_validate(void *lease);
/* After the caller's single exec, validate the same task and pinned grant
 * generation without treating the new mm/exec/credentials as reusable state. */
int msu_su_lease_validate_after_exec(struct msu_su_lease *lease);
const struct msu_su_profile *msu_su_lease_profile(const struct msu_su_lease *lease);
/* Original saved fields are not the resolved execution projection. */
const struct msu_su_profile *msu_su_lease_saved_profile(const struct msu_su_lease *lease);
void msu_su_lease_end(struct msu_su_lease *lease);
/* Existing private consumer, now backed by the detailed credential/task/
 * namespace sequence in su_profile_execution.h. effects retains credential
 * and task wire facts only; namespace has its own private detailed receipt.
 * No syscall/Manager entry or second credential transaction is created. */
int msu_su_apply_current(const void *context, u32 *effects);
#endif
