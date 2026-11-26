// SPDX-License-Identifier: GPL-3.0-only
#include <linux/cred.h>
#include <linux/capability.h>
#include <linux/errno.h>
#include <linux/irqflags.h>
#include <linux/kernel.h>
#include <linux/mm.h>
#include <linux/preempt.h>
#include <linux/sched.h>
#include <linux/sched/mm.h>
#include <linux/slab.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <linux/thread_info.h>
#include <linux/user_namespace.h>
#include "su_install_registry.h"
#include "su_profile_execution.h"
#include "manager_task_state.h"
#include "manager_dynamic_layout.h"
#include "su_profile_resolve.h"
#include "manager_uid_gateway.h"
#include "manager_uid_snapshot.h"
#include "manager_management.h"

struct msu_su_install_entry {
    u32 full_uid, users;
    u64 generation;
    bool revoked, busy, cancelled, granted, source_invalidated;
    struct msu_su_verified_install installation;
    struct msu_su_profile profile, saved_profile;
};

struct msu_su_registry {
    raw_spinlock_t lock;
    maplesu_host_instance host;
    u64 controller, next_generation;
    struct msu_su_registry_source source;
    u32 count;
    bool closed, producer;
    bool gateway_binding, gateway_bound, gateway_retired;
    struct msu_su_install_entry *entries[MSU_SU_REGISTRY_MAX_UIDS];
};

struct msu_su_update {
    struct msu_su_registry *registry;
    maplesu_uid_record_v3 *records;
    u64 expected_revision;
    u32 count;
    bool shutdown;
};

static int msu_su_registry_destroy(struct msu_su_registry *registry);

static bool msu_su_sleepable(void)
{
    return in_task() && !in_atomic() && !irqs_disabled();
}

static bool msu_su_full_uid(u32 uid)
{
    return uid <= 0x7fffffffU && (uid == 1000U || uid >= 2000U);
}

/* Slow-producer projection only. The explicit name was checked against these
 * held members at refresh; legacy grants select their sorted first member.
 * This label conveys no authority and never comes from a mirror caller. */
static void msu_su_record_package(const struct msu_su_install_entry *entry,
    maplesu_uid_record_v3 *record)
{
    const char *name;
    if (!entry->installation.pin || !entry->installation.member_count) return;
    name = entry->saved_profile.explicit_profile ? entry->saved_profile.name :
        entry->installation.members[0].package;
    memcpy(record->package, name, strlen(name) + 1);
}

/* Called only under the registry lock; at most 12 binary search steps. */
static u32 msu_su_position(struct msu_su_registry *registry, u32 uid)
{
    u32 low = 0, high = registry->count;
    while (low < high) {
        u32 middle = low + (high - low) / 2;
        if (registry->entries[middle]->full_uid < uid) low = middle + 1;
        else high = middle;
    }
    return low;
}

static bool msu_su_identity_present(const u8 *identity)
{
    u32 i;
    u8 combined = 0;
    for (i = 0; i < MSU_SU_INSTALL_ID_BYTES; ++i) combined |= identity[i];
    return combined != 0;
}

static int msu_su_profile_valid(const struct msu_su_profile *profile)
{
    u32 i;
    if (profile->uid > 0x7fffffffU || profile->gid > 0x7fffffffU ||
        profile->groups_count > MSU_SU_PROFILE_GROUPS ||
        !memchr(profile->selinux_domain, 0, sizeof(profile->selinux_domain)) ||
        !memchr(profile->template_name, 0, sizeof(profile->template_name)) ||
        !memchr(profile->name, 0, sizeof(profile->name))) return -EINVAL;
    for (i = 0; i < profile->groups_count; ++i)
        if (profile->groups[i] > 0x7fffffffU) return -EINVAL;
    return 0;
}

static bool msu_su_members_valid(const struct msu_su_verified_install *installation)
{
    u32 i;
    if (!installation->members || !installation->member_count ||
        installation->member_count > MSU_SU_INSTALL_MAX_MEMBERS) return false;
    for (i = 0; i < installation->member_count; ++i) {
        const char *name = installation->members[i].package;
        if (!name[0] || !memchr(name, 0, 256) ||
            (i && strcmp(installation->members[i - 1].package, name) >= 0)) return false;
    }
    return true;
}

int msu_su_registry_create(const maplesu_host_instance *host, u64 controller,
    const struct msu_su_registry_source *source, struct msu_su_registry **output)
{
    struct msu_su_registry *registry;
    if (!host || !(host->low | host->high) || !controller || !source || !output)
        return -EINVAL;
    if (!source->owner || !source->pin_current || !source->put_install ||
        !source->read_grant || !source->validate_profile || !source->check_closed ||
        !source->quiesce || !source->put_owner || !source->validate_producer)
        return -EOPNOTSUPP;
    if (!msu_su_sleepable()) return -EWOULDBLOCK;
    registry = kzalloc(sizeof(*registry), GFP_KERNEL);
    if (!registry) return -ENOMEM;
    raw_spin_lock_init(&registry->lock);
    registry->host = *host;
    registry->controller = controller;
    registry->source = *source;
    registry->next_generation = 1;
    *output = registry;
    return 0;
}

int msu_su_registry_refresh(struct msu_su_registry *registry, u32 full_uid,
    maplesu_uid_record_v3 *record)
{
    struct msu_su_verified_install installation = {0};
    struct msu_su_saved_grant *grant;
    struct msu_su_profile *effective;
    struct msu_su_install_entry *entry, *unused;
    const void *old_pin = NULL;
    unsigned long irq;
    u64 generation;
    u32 position, i;
    int error;
    if (!registry || !record || !msu_su_full_uid(full_uid)) return -EINVAL;
    if (!msu_su_sleepable()) return -EWOULDBLOCK;
    /* Install/Profile records are too large for a hook stack. Allocation and
     * all source I/O stay on this slow producer path. */
    unused = kzalloc(sizeof(*unused), GFP_KERNEL);
    grant = kzalloc(sizeof(*grant), GFP_KERNEL);
    effective = kzalloc(sizeof(*effective), GFP_KERNEL);
    if (!unused || !grant || !effective) {
        kfree(unused); kfree(grant); kfree(effective); return -ENOMEM;
    }
    raw_spin_lock_irqsave(&registry->lock, irq);
    if (registry->closed) { error = -ESHUTDOWN; goto unlock; }
    if (registry->producer || registry->gateway_binding) { error = -EINPROGRESS; goto unlock; }
    position = msu_su_position(registry, full_uid);
    if (position < registry->count && registry->entries[position]->full_uid == full_uid) {
        entry = registry->entries[position];
        entry->revoked = true;
        if (entry->users || entry->busy) { error = -EINPROGRESS; goto unlock; }
    } else {
        if (registry->count == MSU_SU_REGISTRY_MAX_UIDS) { error = -ENOSPC; goto unlock; }
        entry = unused;
        unused = NULL;
        entry->full_uid = full_uid;
        entry->revoked = true;
        for (i = registry->count; i > position; --i)
            registry->entries[i] = registry->entries[i - 1];
        registry->entries[position] = entry;
        ++registry->count;
    }
    if (!registry->next_generation) { error = -EOVERFLOW; goto unlock; }
    generation = registry->next_generation++;
    entry->generation = generation;
    entry->busy = true;
    entry->cancelled = false;
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    error = registry->source.pin_current(registry->source.owner, full_uid, &installation);
    if (!error && (installation.full_uid != full_uid || !installation.pin ||
        !msu_su_identity_present(installation.installation_id) ||
        !msu_su_members_valid(&installation))) error = -ESTALE;
    if (!error) {
        error = registry->source.read_grant(registry->source.owner, &installation, grant);
        if (error == -ENOENT) {
            memset(grant, 0, sizeof(*grant));
            grant->full_uid = full_uid;
            memcpy(grant->installation_id, installation.installation_id, MSU_SU_INSTALL_ID_BYTES);
            error = 0;
        }
    }
    if (!error && (grant->full_uid != full_uid || grant->decision > 1 ||
        memcmp(grant->installation_id, installation.installation_id, MSU_SU_INSTALL_ID_BYTES)))
        error = -ESTALE;
    if (!error && (!memchr(grant->profile.name, 0, sizeof(grant->profile.name)) ||
        !memchr(grant->profile.template_name, 0, sizeof(grant->profile.template_name)))) error = -EINVAL;
    if (!error) *effective = grant->profile;
    if (!error && grant->profile.explicit_profile) {
        bool member = false;
        for (i = 0; i < installation.member_count; ++i)
            if (!strcmp(installation.members[i].package, grant->profile.name)) member = true;
        if (!member || grant->profile.allow_su == grant->profile.non_root ||
            (grant->decision && !grant->profile.allow_su)) error = -EINVAL;
    }
    if (!error && grant->decision) {
        error = msu_su_profile_resolve_root(&grant->profile, effective);
        if (!error) error = msu_su_profile_valid(effective);
        if (!error) error = registry->source.validate_profile(effective);
    }
    raw_spin_lock_irqsave(&registry->lock, irq);
    if (!error && (registry->closed || entry->cancelled)) error = -ESTALE;
    if (!error) {
        old_pin = entry->installation.pin;
        entry->installation = installation;
        installation.pin = NULL;
        entry->saved_profile = grant->profile;
        entry->profile = *effective;
        entry->granted = grant->decision != 0;
        entry->source_invalidated = false;
    }
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    /* busy pins the registry AND source owner until all sleeping cleanup has
     * finished. Shutdown/revoke can cancel this producer during put_install. */
    if (installation.pin) registry->source.put_install(registry->source.owner, installation.pin);
    if (old_pin) registry->source.put_install(registry->source.owner, old_pin);
    kfree(grant);
    kfree(effective);
    kfree(unused);
    raw_spin_lock_irqsave(&registry->lock, irq);
    if (!error && (registry->closed || entry->cancelled)) error = -ESTALE;
    entry->busy = false;
    if (!error) {
        entry->revoked = !entry->granted;
        memset(record, 0, sizeof(*record));
        record->full_uid = full_uid;
        record->decision = entry->granted;
        record->installation_generation = generation;
        record->profile_revision = generation;
        msu_su_record_package(entry, record);
    }
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    return error;
unlock:
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    if (installation.pin) registry->source.put_install(registry->source.owner, installation.pin);
    if (old_pin) registry->source.put_install(registry->source.owner, old_pin);
    kfree(grant);
    kfree(effective);
    kfree(unused);
    return error;
}

static int msu_su_producer_begin(struct msu_su_registry *registry, u32 *count)
{
    unsigned long irq;
    u32 i;
    int error = 0;
    raw_spin_lock_irqsave(&registry->lock, irq);
    if (registry->closed) error = -ESHUTDOWN;
    else if (registry->producer || registry->gateway_binding) error = -EINPROGRESS;
    else {
        for (i = 0; i < registry->count; ++i)
            if (registry->entries[i]->busy) { error = -EINPROGRESS; break; }
        if (!error) { registry->producer = true; *count = registry->count; }
    }
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    return error;
}

static void msu_su_producer_end(struct msu_su_registry *registry)
{
    unsigned long irq;
    raw_spin_lock_irqsave(&registry->lock, irq);
    registry->producer = false;
    raw_spin_unlock_irqrestore(&registry->lock, irq);
}

int msu_su_registry_rows(struct msu_su_registry *registry,
    maplesu_uid_record_v3 *records, u32 capacity, u32 *count)
{
    maplesu_uid_record_v3 *rows;
    unsigned long irq;
    u32 total = 0, i;
    int error;
    if (!registry || !records || !count || capacity > MSU_SU_REGISTRY_MAX_UIDS) return -EINVAL;
    if (!msu_su_sleepable()) return -EWOULDBLOCK;
    rows = kvmalloc((size_t)MSU_SU_REGISTRY_MAX_UIDS * sizeof(*rows), GFP_KERNEL);
    if (!rows) return -ENOMEM;
    error = msu_su_producer_begin(registry, &total);
    if (error) { kvfree(rows); return error; }
    if (capacity < total) { error = -ENOSPC; goto out; }
    for (i = 0; i < total; ++i) {
        struct msu_su_install_entry *entry = registry->entries[i];
        maplesu_uid_record_v3 *row = &rows[i];
        memset(row, 0, sizeof(*row));
        raw_spin_lock_irqsave(&registry->lock, irq);
        row->full_uid = entry->full_uid;
        row->decision = entry->granted && !entry->revoked;
        row->installation_generation = entry->generation;
        row->profile_revision = entry->generation;
        msu_su_record_package(entry, row);
        raw_spin_unlock_irqrestore(&registry->lock, irq);
        if (!row->installation_generation) { error = -ESTALE; goto out; }
    }
    raw_spin_lock_irqsave(&registry->lock, irq);
    if (registry->closed) error = -ESHUTDOWN;
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    if (!error) {
        memcpy(records, rows, (size_t)total * sizeof(*rows));
        *count = total;
    }
out:
    kvfree(rows);
    msu_su_producer_end(registry);
    return error;
}

static int msu_su_kernel_producer_validate(void *context)
{
    struct msu_su_registry *registry = context;
    unsigned long irq;
    int error = 0;
    raw_spin_lock_irqsave(&registry->lock, irq);
    if (registry->closed || registry->gateway_retired) error = -ESHUTDOWN;
    else if (!registry->gateway_bound || registry->gateway_binding) error = -EACCES;
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    if (!error) error = registry->source.validate_producer(registry->source.owner);
    return error > 0 ? -EIO : error;
}

int msu_su_registry_prepare_kernel(struct msu_su_registry *registry,
    maplesu_response_owner_v3 *response)
{
    struct msu_uid_snapshot *snapshot;
    struct msu_uid_snapshot_metadata metadata;
    struct msu_uid_snapshot_record *records = NULL;
    maplesu_uid_record_v3 *rows = NULL;
    u32 count, i;
    int error;
    if (!response) return -EINVAL;
    memset(response, 0, sizeof(*response));
    if (!registry) { error = -EINVAL; goto out; }
    if (!msu_su_sleepable()) { error = -EWOULDBLOCK; goto out; }
    error = msu_su_kernel_producer_validate(registry);
    if (error) goto out;
    snapshot = msu_uid_gateway_acquire(&registry->host, registry->controller);
    error = snapshot ? msu_uid_snapshot_metadata(snapshot, &metadata) : -ESTALE;
    msu_uid_snapshot_put(snapshot);
    if (error) goto out;
    rows = kvmalloc((size_t)MSU_SU_REGISTRY_MAX_UIDS * sizeof(*rows), GFP_KERNEL);
    if (!rows) { error = -ENOMEM; goto out; }
    error = msu_su_registry_rows(registry, rows, MSU_SU_REGISTRY_MAX_UIDS, &count);
    if (error) goto out;
    if (count) {
        records = kvmalloc((size_t)count * sizeof(*records), GFP_KERNEL);
        if (!records) { error = -ENOMEM; goto out; }
    }
    for (i = 0; i < count; ++i) {
        records[i].full_uid = rows[i].full_uid;
        records[i].decision = rows[i].decision;
        records[i].installation_generation = rows[i].installation_generation;
        records[i].profile_revision = rows[i].profile_revision;
        memcpy(records[i].package, rows[i].package, sizeof(records[i].package));
    }
    error = msu_uid_gateway_prepare_kernel(&registry->host, registry->controller,
        registry, records, count, metadata.content_revision, response,
        msu_su_kernel_producer_validate, registry);
out:
    kvfree(records);
    kvfree(rows);
    response->status = error;
    return error;
}

int msu_su_registry_commit_kernel(struct msu_su_registry *registry, u64 candidate,
    maplesu_response_owner_v3 *response)
{
    if (!response) return -EINVAL;
    if (!registry) {
        memset(response, 0, sizeof(*response));
        response->status = -EINVAL;
        return -EINVAL;
    }
    return msu_uid_gateway_commit_kernel(&registry->host, registry->controller,
        registry, candidate, response, msu_su_kernel_producer_validate, registry);
}

int msu_su_registry_cancel_kernel(struct msu_su_registry *registry, u64 candidate,
    maplesu_response_owner_v3 *response)
{
    if (!response) return -EINVAL;
    if (!registry) {
        memset(response, 0, sizeof(*response));
        response->status = -EINVAL;
        return -EINVAL;
    }
    return msu_uid_gateway_cancel_kernel(&registry->host, registry->controller,
        registry, candidate, response);
}

static int msu_su_row_matches(struct msu_su_registry *registry,
    const maplesu_uid_record_v3 *row, u32 *position)
{
    struct msu_su_install_entry *entry;
    u32 p = msu_su_position(registry, row->full_uid);
    *position = p;
    if (p == registry->count || registry->entries[p]->full_uid != row->full_uid)
        return row->decision ? -ESTALE : 0;
    entry = registry->entries[p];
    if (row->decision && (!entry->granted || entry->revoked || entry->cancelled ||
        entry->generation != row->installation_generation || entry->generation != row->profile_revision))
        return -ESTALE;
    return 0;
}

int msu_su_registry_update_begin(struct msu_su_registry *registry,
    const maplesu_uid_record_v3 *records, u32 count, u64 expected_revision,
    struct msu_su_update **output, struct msu_su_revoke_receipt *receipt)
{
    struct msu_su_update *update;
    struct msu_uid_snapshot *snapshot;
    struct msu_uid_snapshot_metadata metadata;
    unsigned long irq;
    u32 total = 0, i, next = 0, position;
    int error = 0;
    if (!registry || !output || *output || !receipt || !expected_revision ||
        expected_revision == ~0ULL || count > MSU_SU_REGISTRY_MAX_UIDS ||
        (count && !records)) return -EINVAL;
    if (!msu_su_sleepable()) return -EWOULDBLOCK;
    for (i = 0; i < count; ++i)
        if (!msu_su_full_uid(records[i].full_uid) || records[i].decision > 1 ||
            !records[i].installation_generation || !records[i].profile_revision ||
            !memchr(records[i].package, 0, sizeof(records[i].package)) ||
            (i && records[i - 1].full_uid >= records[i].full_uid)) return -EINVAL;
    update = kzalloc(sizeof(*update), GFP_KERNEL);
    if (!update) return -ENOMEM;
    if (count) {
        update->records = kvmalloc((size_t)count * sizeof(*records), GFP_KERNEL);
        if (!update->records) { kfree(update); return -ENOMEM; }
        memcpy(update->records, records, (size_t)count * sizeof(*records));
    }
    error = msu_su_producer_begin(registry, &total);
    if (error) goto free_update;
    snapshot = msu_uid_gateway_acquire(&registry->host, registry->controller);
    if (!snapshot) error = -ESTALE;
    else {
        error = msu_uid_snapshot_metadata(snapshot, &metadata);
        if (!error && metadata.content_revision != expected_revision) error = -EAGAIN;
    }
    msu_uid_snapshot_put(snapshot);
    if (error) goto release_producer;
    for (i = 0; i < count; ++i) {
        raw_spin_lock_irqsave(&registry->lock, irq);
        error = msu_su_row_matches(registry, &update->records[i], &position);
        raw_spin_unlock_irqrestore(&registry->lock, irq);
        if (error) goto release_producer;
    }
    memset(receipt, 0, sizeof(*receipt));
    for (i = 0; i < total; ++i) {
        struct msu_su_install_entry *entry = registry->entries[i];
        while (next < count && update->records[next].full_uid < entry->full_uid) ++next;
        if (next < count && update->records[next].full_uid == entry->full_uid &&
            update->records[next].decision) continue;
        if (next < count && update->records[next].full_uid == entry->full_uid &&
            !update->records[next].decision) {
            const maplesu_uid_record_v3 *denial = &update->records[next];
            bool unchanged_pinned_denial;
            raw_spin_lock_irqsave(&registry->lock, irq);
            unchanged_pinned_denial = !entry->granted && !entry->cancelled &&
                !entry->source_invalidated && !entry->busy &&
                entry->installation.pin && entry->installation.full_uid == entry->full_uid &&
                entry->generation == denial->installation_generation &&
                entry->generation == denial->profile_revision;
            raw_spin_unlock_irqrestore(&registry->lock, irq);
            if (unchanged_pinned_denial) continue;
        }
        raw_spin_lock_irqsave(&registry->lock, irq);
        entry->revoked = true;
        entry->cancelled = true;
        entry->source_invalidated = true;
        if (entry->users && !error) {
            receipt->full_uid = entry->full_uid;
            receipt->generation = entry->generation;
            receipt->inflight = entry->users;
            error = -EINPROGRESS;
        }
        raw_spin_unlock_irqrestore(&registry->lock, irq);
    }
    raw_spin_lock_irqsave(&registry->lock, irq);
    if (!error && registry->closed) error = -ESHUTDOWN;
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    if (error) goto release_producer;
    update->registry = registry;
    update->expected_revision = expected_revision;
    update->count = count;
    *output = update;
    return 0;
release_producer:
    kvfree(update->records);
    kfree(update);
    msu_su_producer_end(registry);
    return error;
free_update:
    kvfree(update->records);
    kfree(update);
    return error;
}

int msu_su_registry_update_finish(struct msu_su_update **handle, int commit_error)
{
    struct msu_su_update *update;
    struct msu_su_registry *registry;
    struct msu_uid_snapshot *snapshot = NULL;
    struct msu_uid_snapshot_record row;
    struct msu_uid_snapshot_metadata metadata;
    unsigned long irq;
    u32 i, position;
    int error = commit_error;
    if (!handle || !*handle) return -EINVAL;
    if (!msu_su_sleepable()) return -EWOULDBLOCK;
    update = *handle;
    registry = update->registry;
    if (!error && update->shutdown) {
        raw_spin_lock_irqsave(&registry->lock, irq);
        registry->gateway_retired = true;
        raw_spin_unlock_irqrestore(&registry->lock, irq);
    }
    if (!error && !update->shutdown) {
        snapshot = msu_uid_gateway_acquire(&registry->host, registry->controller);
        error = snapshot ? msu_uid_snapshot_metadata(snapshot, &metadata) : -ESTALE;
        if (!error && (metadata.content_revision != update->expected_revision + 1 ||
            metadata.count != update->count)) error = -ESTALE;
    }
    for (i = 0; !error && !update->shutdown && i < update->count; ++i) {
        const maplesu_uid_record_v3 *wanted = &update->records[i];
        error = msu_uid_snapshot_lookup(snapshot, wanted->full_uid,
            wanted->installation_generation, &row);
        if (!error && (row.decision != wanted->decision ||
            row.profile_revision != wanted->profile_revision ||
            strcmp(row.package, wanted->package))) error = -ESTALE;
        if (error) break;
        raw_spin_lock_irqsave(&registry->lock, irq);
        error = registry->closed ? -ESHUTDOWN : msu_su_row_matches(registry, wanted, &position);
        raw_spin_unlock_irqrestore(&registry->lock, irq);
    }
    msu_uid_snapshot_put(snapshot);
    *handle = NULL;
    kvfree(update->records);
    kfree(update);
    msu_su_producer_end(registry);
    return error;
}

/* One factory-owned binding to A's actual gateway; no public registration.
 * The registry's retained host/controller are the only binding selectors. */
static int msu_su_fence_check_closed(const void *owner)
{
    struct msu_su_registry *registry = (struct msu_su_registry *)owner;
    unsigned long irq;
    int error = 0;
    raw_spin_lock_irqsave(&registry->lock, irq);
    if (registry->closed) error = -ESHUTDOWN;
    else if (!registry->gateway_binding || registry->count || registry->producer)
        error = -EBUSY;
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    if (!error) error = registry->source.check_closed(registry->source.owner);
    return error > 0 ? -EIO : error;
}

static int msu_su_fence_begin(const void *owner,
    const struct msu_uid_snapshot *candidate, u64 revision, bool shutdown,
    void **transaction)
{
    struct msu_su_registry *registry = (struct msu_su_registry *)owner;
    struct msu_su_update *update = NULL;
    struct msu_uid_snapshot_metadata metadata;
    struct msu_uid_snapshot_record actual;
    struct msu_su_revoke_receipt receipt;
    maplesu_uid_record_v3 *rows = NULL;
    unsigned long irq;
    u32 i;
    int error;
    if (!transaction || *transaction || shutdown != !candidate) return -EINVAL;
    if (shutdown) {
        update = kzalloc(sizeof(*update), GFP_KERNEL);
        if (!update) return -ENOMEM;
        error = msu_su_registry_shutdown(registry);
        if (error) { kfree(update); return error; }
        raw_spin_lock_irqsave(&registry->lock, irq);
        if (registry->producer) error = -EINPROGRESS;
        else registry->producer = true;
        raw_spin_unlock_irqrestore(&registry->lock, irq);
        if (error) { kfree(update); return error; }
        update->registry = registry;
        update->shutdown = true;
        *transaction = update;
        return 0;
    }
    error = msu_uid_snapshot_metadata(candidate, &metadata);
    if (error) return error;
    if (metadata.count > MSU_SU_REGISTRY_MAX_UIDS) return -E2BIG;
    if (metadata.count) {
        rows = kvmalloc((size_t)metadata.count * sizeof(*rows), GFP_KERNEL);
        if (!rows) return -ENOMEM;
    }
    for (i = 0; i < metadata.count; ++i) {
        error = msu_uid_snapshot_record_at(candidate, i, &actual);
        if (error) goto out;
        memset(&rows[i], 0, sizeof(rows[i]));
        rows[i].full_uid = actual.full_uid;
        rows[i].decision = actual.decision;
        rows[i].installation_generation = actual.installation_generation;
        rows[i].profile_revision = actual.profile_revision;
        memcpy(rows[i].package, actual.package, sizeof(rows[i].package));
    }
    error = msu_su_registry_update_begin(registry, rows, metadata.count,
        revision, &update, &receipt);
    if (!error) *transaction = update;
out:
    kvfree(rows);
    return error;
}

static int msu_su_fence_revoke(const void *owner, u32 full_uid)
{
    struct msu_su_registry *registry = (struct msu_su_registry *)owner;
    struct msu_uid_snapshot *previous;
    struct msu_uid_snapshot_record row;
    struct msu_su_revoke_receipt receipt;
    struct msu_su_install_entry *entry;
    unsigned long irq;
    u32 position;
    bool already_drained = false;
    int error = 0;
    previous = msu_uid_gateway_acquire(&registry->host, registry->controller);
    raw_spin_lock_irqsave(&registry->lock, irq);
    if (!registry->producer) error = -ESTALE;
    else if (!registry->closed && previous) {
        position = msu_su_position(registry, full_uid);
        if (position < registry->count &&
            registry->entries[position]->full_uid == full_uid) {
            entry = registry->entries[position];
            error = msu_uid_snapshot_lookup(previous, full_uid, entry->generation, &row);
            /* refresh changes generation only after old leases drain. begin
             * reserved this producer and validated the actual new candidate;
             * the gateway callback selects the OLD published generation. */
            if (error == -ESTALE && !entry->users && !entry->busy) {
                already_drained = true;
                error = 0;
            }
        }
    }
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    msu_uid_snapshot_put(previous);
    if (error || already_drained) return error;
    return msu_su_registry_revoke(registry, full_uid, &receipt);
}

static int msu_su_fence_finish(const void *owner, void **transaction, int commit_error)
{
    struct msu_su_update *update;
    int error;
    if (!transaction || !*transaction) return -EINVAL;
    update = *transaction;
    if (update->registry != owner) return -ESTALE;
    error = msu_su_registry_update_finish(&update, commit_error);
    *transaction = update;
    return error;
}

static int msu_su_fence_retire(const void *owner)
{
    struct msu_su_registry *registry = (struct msu_su_registry *)owner;
    unsigned long irq;
    int error = 0;
    raw_spin_lock_irqsave(&registry->lock, irq);
    if (!registry->gateway_bound || !registry->gateway_retired ||
        registry->gateway_binding) error = -EINPROGRESS;
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    /* CORE has withdrawn state and drained its outer RCU readers. Registry
     * shutdown still checks real leases/workers on every explicit attempt. */
    return error ? error : msu_su_registry_destroy(registry);
}

int msu_su_registry_bind_gateway(struct msu_su_registry *registry)
{
    struct msu_uid_reader_fence fence = {
        .owner = registry,
        .check_closed = msu_su_fence_check_closed,
        .begin = msu_su_fence_begin,
        .revoke = msu_su_fence_revoke,
        .finish = msu_su_fence_finish,
        .retire = msu_su_fence_retire,
    };
    unsigned long irq;
    int error = 0;
    if (!registry) return -EINVAL;
    if (!msu_su_sleepable()) return -EWOULDBLOCK;
    if (!registry->source.check_closed || !registry->source.quiesce ||
        !registry->source.put_owner || !registry->source.validate_producer)
        return -EOPNOTSUPP;
    raw_spin_lock_irqsave(&registry->lock, irq);
    if (registry->closed) error = -ESHUTDOWN;
    else if (registry->gateway_bound) error = -EALREADY;
    else if (registry->gateway_binding || registry->producer || registry->count)
        error = -EBUSY;
    else registry->gateway_binding = true;
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    if (error) return error;
    error = msu_uid_gateway_bind_reader(&registry->host, registry->controller, &fence);
    raw_spin_lock_irqsave(&registry->lock, irq);
    registry->gateway_binding = false;
    if (!error) registry->gateway_bound = true;
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    return error;
}

int msu_su_registry_revoke(struct msu_su_registry *registry, u32 full_uid,
    struct msu_su_revoke_receipt *receipt)
{
    unsigned long irq;
    u32 position;
    int error = 0;
    if (!registry || !receipt || !msu_su_full_uid(full_uid)) return -EINVAL;
    if (in_nmi()) return -EWOULDBLOCK;
    raw_spin_lock_irqsave(&registry->lock, irq);
    memset(receipt, 0, sizeof(*receipt));
    receipt->full_uid = full_uid;
    position = msu_su_position(registry, full_uid);
    if (position < registry->count && registry->entries[position]->full_uid == full_uid) {
        struct msu_su_install_entry *entry = registry->entries[position];
        entry->revoked = true;
        entry->cancelled = true;
        entry->source_invalidated = true;
        receipt->generation = entry->generation;
        receipt->inflight = entry->users;
        if (entry->users) error = -EINPROGRESS;
    }
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    return error;
}

int msu_su_registry_revoke_package(struct msu_su_registry *registry, u32 full_uid,
    const char *package, enum msu_su_package_change change,
    struct msu_su_revoke_receipt *receipt)
{
    struct msu_su_install_entry *entry;
    unsigned long irq;
    u32 position, low = 0, high;
    int error = 0;
    if (!registry || !receipt || !package || !package[0] ||
        !memchr(package, 0, 256) || !msu_su_full_uid(full_uid) ||
        change < MSU_SU_PACKAGE_INSTALL || change > MSU_SU_PACKAGE_REMOVE) return -EINVAL;
    if (in_nmi()) return -EWOULDBLOCK;
    raw_spin_lock_irqsave(&registry->lock, irq);
    position = msu_su_position(registry, full_uid);
    if (position == registry->count || registry->entries[position]->full_uid != full_uid) {
        error = -ENOENT;
        goto out;
    }
    entry = registry->entries[position];
    /* A first pin being established may not expose its member names yet.
     * Cancel that affected fullUID conservatively instead of losing the event. */
    if (!entry->busy && change != MSU_SU_PACKAGE_INSTALL) {
        high = entry->installation.member_count;
        while (low < high) {
            u32 middle = low + (high - low) / 2;
            if (strcmp(entry->installation.members[middle].package, package) < 0) low = middle + 1;
            else high = middle;
        }
        if (low == entry->installation.member_count ||
            strcmp(entry->installation.members[low].package, package)) {
            error = -ENOENT;
            goto out;
        }
    }
    entry->revoked = true;
    entry->cancelled = true;
    entry->source_invalidated = true;
    memset(receipt, 0, sizeof(*receipt));
    receipt->full_uid = full_uid;
    receipt->generation = entry->generation;
    receipt->inflight = entry->users;
    if (entry->users) error = -EINPROGRESS;
out:
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    return error;
}

int msu_su_registry_shutdown(struct msu_su_registry *registry)
{
    unsigned long irq;
    u32 i;
    int error = 0;
    if (!registry) return -EINVAL;
    if (in_nmi()) return -EWOULDBLOCK;
    raw_spin_lock_irqsave(&registry->lock, irq);
    registry->closed = true;
    if (registry->producer) error = -EINPROGRESS;
    for (i = 0; i < registry->count; ++i) {
        registry->entries[i]->revoked = true;
        registry->entries[i]->cancelled = true;
        if (registry->entries[i]->users || registry->entries[i]->busy) error = -EINPROGRESS;
    }
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    return error;
}

static int msu_su_registry_destroy(struct msu_su_registry *registry)
{
    struct msu_su_registry_source source = registry->source;
    u32 i;
    int error;
    if (!msu_su_sleepable()) return -EWOULDBLOCK;
    error = msu_su_registry_shutdown(registry);
    if (error) return error;
    error = source.quiesce(source.owner);
    if (error) return error > 0 ? -EIO : error;
    /* Zero quiesce proves no future raw API/branch-before-enter, not merely
     * users=0. Source stays held through all sleeping pin-release callbacks. */
    for (i = 0; i < registry->count; ++i) {
        const void *pin = registry->entries[i]->installation.pin;
        if (pin) source.put_install(source.owner, pin);
        kfree(registry->entries[i]);
    }
    kfree(registry);
    source.put_owner(source.owner);
    return 0;
}

int msu_su_registry_destroy_quiesced(struct msu_su_registry *registry)
{
    unsigned long irq;
    bool bound;
    if (!registry) return -EINVAL;
    if (!msu_su_sleepable()) return -EWOULDBLOCK;
    raw_spin_lock_irqsave(&registry->lock, irq);
    bound = registry->gateway_binding || registry->gateway_bound;
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    if (bound) return -EINPROGRESS;
    return msu_su_registry_destroy(registry);
}

bool msu_su_registry_non_root_wants_transition(struct msu_su_registry *registry,
    u32 full_uid)
{
    const struct cred *cred;
    struct msu_su_install_entry *entry;
    unsigned long irq;
    u32 position;
    bool wants = false;
    if (!registry || !msu_su_full_uid(full_uid) || !in_task() || in_nmi()) return false;
    cred = msu_dynamic_current_real_cred();
    if (!cred || cred != msu_dynamic_current_cred() || MSU_DYN_FIELD(cred, cred, user_ns) != &init_user_ns ||
        __kuid_val(MSU_DYN_FIELD(cred, cred, uid))) return false;
    raw_spin_lock_irqsave(&registry->lock, irq);
    position = msu_su_position(registry, full_uid);
    if (!registry->closed && !registry->gateway_retired && !registry->gateway_binding &&
        registry->gateway_bound && position != registry->count) {
        entry = registry->entries[position];
        wants = !entry->busy && !entry->cancelled && !entry->source_invalidated &&
            !entry->granted && entry->installation.pin &&
            entry->installation.full_uid == full_uid && entry->saved_profile.explicit_profile &&
            !entry->saved_profile.allow_su && entry->saved_profile.non_root &&
            !entry->saved_profile.non_root_use_default && entry->saved_profile.umount_modules;
    }
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    return wants;
}

int msu_su_registry_non_root_policy(struct msu_su_registry *registry,
    u32 full_uid, enum msu_su_non_root_umount_policy *policy)
{
    const struct cred *cred;
    struct msu_su_install_entry *entry = NULL;
    struct msu_uid_snapshot *snapshot;
    struct msu_uid_snapshot_record row;
    unsigned long irq;
    u64 generation = 0;
    u32 position = 0;
    bool granted = false, explicit_profile = false, allow_su = false;
    bool non_root = false, use_default = false, umount_modules = false;
    int error = 0;

    if (!registry || !policy || !msu_su_full_uid(full_uid)) return -EINVAL;
    *policy = MSU_SU_NON_ROOT_UMOUNT_DEFAULT;
    if (!in_task() || in_nmi()) return -EWOULDBLOCK;
    cred = msu_dynamic_current_real_cred();
    if (!cred || MSU_DYN_FIELD(cred, cred, user_ns) != &init_user_ns || __kuid_val(MSU_DYN_FIELD(cred, cred, uid)) != full_uid)
        return -EPERM;

    raw_spin_lock_irqsave(&registry->lock, irq);
    if (registry->closed || registry->gateway_retired) error = -ESHUTDOWN;
    else if (registry->gateway_binding || !registry->gateway_bound) error = -EINPROGRESS;
    position = msu_su_position(registry, full_uid);
    if (!error && position == registry->count) {
        raw_spin_unlock_irqrestore(&registry->lock, irq);
        return 0;
    }
    if (!error) {
        entry = registry->entries[position];
        if (entry->busy || entry->cancelled || entry->source_invalidated ||
            (entry->revoked && entry->granted)) error = -EINPROGRESS;
        else if (entry->installation.full_uid != full_uid || !entry->installation.pin)
            error = -ESTALE;
        generation = entry->generation;
        granted = entry->granted;
        explicit_profile = entry->saved_profile.explicit_profile;
        allow_su = entry->saved_profile.allow_su;
        non_root = entry->saved_profile.non_root;
        use_default = entry->saved_profile.non_root_use_default;
        umount_modules = entry->saved_profile.umount_modules;
    }
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    if (error) return error;

    snapshot = msu_uid_gateway_acquire(&registry->host, registry->controller);
    if (!snapshot) return -EAGAIN;
    error = msu_uid_snapshot_lookup(snapshot, full_uid, generation, &row);
    if (!error && (row.profile_revision != generation || !!row.decision != granted))
        error = -ESTALE;
    msu_uid_snapshot_put(snapshot);
    if (error) return error;

    raw_spin_lock_irqsave(&registry->lock, irq);
    position = msu_su_position(registry, full_uid);
    if (registry->closed || registry->gateway_binding ||
        position == registry->count || registry->entries[position] != entry ||
        entry->generation != generation || entry->busy || entry->cancelled ||
        entry->source_invalidated ||
        entry->granted != granted || (entry->revoked && entry->granted))
        error = -ESTALE;
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    if (error) return error;

    if (granted) {
        *policy = MSU_SU_NON_ROOT_UMOUNT_DISABLED;
    } else if (explicit_profile) {
        if (allow_su || !non_root) return -ESTALE;
        if (!use_default)
            *policy = umount_modules ? MSU_SU_NON_ROOT_UMOUNT_ENABLED :
                MSU_SU_NON_ROOT_UMOUNT_DISABLED;
    }
    return 0;
}

int msu_su_registry_query_root(struct msu_su_registry *registry, u32 full_uid,
    bool *out_granted, enum msu_su_non_root_umount_policy *out_policy)
{
    struct msu_su_install_entry *entry;
    struct msu_uid_snapshot *snapshot;
    struct msu_uid_snapshot_record row;
    const struct cred *cred;
    enum msu_su_non_root_umount_policy policy = MSU_SU_NON_ROOT_UMOUNT_DEFAULT;
    unsigned long irq;
    u64 generation;
    u32 position;
    bool granted, explicit_profile, allow_su, non_root, use_default, umount_modules;
    int error = 0;
    if (!registry || !out_granted || !out_policy || full_uid > 0x7fffffffU) return -EINVAL;
    if (!msu_su_sleepable()) return -EWOULDBLOCK;
    cred = msu_dynamic_current_cred();
    if (!cred || cred != msu_dynamic_current_real_cred() ||
        MSU_DYN_FIELD(cred, cred, user_ns) != &init_user_ns ||
        __kuid_val(MSU_DYN_FIELD(cred, cred, uid)) ||
        __kuid_val(MSU_DYN_FIELD(cred, cred, euid))) return -EPERM;
    raw_spin_lock_irqsave(&registry->lock, irq);
    if (registry->closed || registry->gateway_retired) error = -ESHUTDOWN;
    else if (registry->gateway_binding || !registry->gateway_bound) error = -EINPROGRESS;
    position = msu_su_position(registry, full_uid);
    if (error) goto unlock;
    if (position == registry->count || registry->entries[position]->full_uid != full_uid) {
        *out_granted = false;
        *out_policy = policy;
        goto unlock;
    }
    entry = registry->entries[position];
    if (entry->busy || entry->cancelled || entry->source_invalidated ||
        (entry->revoked && entry->granted)) { error = -EINPROGRESS; goto unlock; }
    if (entry->installation.full_uid != full_uid || !entry->installation.pin) {
        error = -ESTALE; goto unlock;
    }
    generation = entry->generation;
    granted = entry->granted;
    explicit_profile = entry->saved_profile.explicit_profile;
    allow_su = entry->saved_profile.allow_su;
    non_root = entry->saved_profile.non_root;
    use_default = entry->saved_profile.non_root_use_default;
    umount_modules = entry->saved_profile.umount_modules;
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    snapshot = msu_uid_gateway_acquire(&registry->host, registry->controller);
    if (!snapshot) return -EAGAIN;
    error = msu_uid_snapshot_lookup(snapshot, full_uid, generation, &row);
    if (!error && (row.profile_revision != generation || !!row.decision != granted)) error = -ESTALE;
    msu_uid_snapshot_put(snapshot);
    if (error) return error;
    raw_spin_lock_irqsave(&registry->lock, irq);
    position = msu_su_position(registry, full_uid);
    if (registry->closed || registry->gateway_retired || !registry->gateway_bound ||
        registry->gateway_binding || position == registry->count ||
        registry->entries[position] != entry || entry->generation != generation ||
        entry->busy || entry->cancelled || entry->source_invalidated ||
        entry->granted != granted || (entry->revoked && entry->granted)) error = -ESTALE;
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    if (error) return error;
    if (granted) policy = MSU_SU_NON_ROOT_UMOUNT_DISABLED;
    else if (explicit_profile) {
        if (allow_su || !non_root) return -ESTALE;
        if (!use_default) policy = umount_modules ? MSU_SU_NON_ROOT_UMOUNT_ENABLED :
            MSU_SU_NON_ROOT_UMOUNT_DISABLED;
    }
    *out_granted = granted;
    *out_policy = policy;
    return 0;
unlock:
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    return error;
}

int msu_su_lease_begin(const void *context, struct msu_su_lease *lease)
{
    struct msu_su_registry *registry = (struct msu_su_registry *)context;
    struct msu_su_lease candidate = {0};
    struct msu_su_install_entry *entry;
    struct msu_uid_snapshot_record row;
    unsigned long irq;
    u32 full_uid, position;
    int error;
    if (!registry || !lease || lease->registry) return -EINVAL;
    if (!in_task() || in_nmi()) return -EWOULDBLOCK;
    /* This task keeps its current cred/mm alive through the non-sleeping
     * lookup. Denied calls need no additional lifetime references. */
    candidate.cred = msu_dynamic_current_cred();
    if (candidate.cred != msu_dynamic_current_real_cred() || MSU_DYN_FIELD(candidate.cred, cred, user_ns) != &init_user_ns) {
        return -EPERM;
    }
    candidate.mm = READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm));
    if (!candidate.mm) return -EPERM;
    candidate.exec_id = READ_ONCE(MSU_DYN_FIELD(current, task_struct, self_exec_id));
    full_uid = __kuid_val(MSU_DYN_FIELD(candidate.cred, cred, uid));
    raw_spin_lock_irqsave(&registry->lock, irq);
    position = msu_su_position(registry, full_uid);
    if (registry->closed || !registry->gateway_bound || registry->gateway_binding ||
        registry->gateway_retired || position == registry->count ||
        registry->entries[position]->full_uid != full_uid) { error = -ENOENT; goto fail_locked; }
    entry = registry->entries[position];
    if (entry->revoked || entry->busy || !entry->granted || entry->users == ~0U) {
        error = -EACCES;
        goto fail_locked;
    }
    candidate.cred = msu_dynamic_get_cred(candidate.cred);
    msu_dynamic_mmgrab(candidate.mm);
    ++entry->users;
    candidate.registry = registry;
    candidate.entry = entry;
    candidate.task = current;
    candidate.generation = entry->generation;
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    candidate.snapshot = msu_uid_gateway_acquire(&registry->host, registry->controller);
    error = candidate.snapshot ? msu_uid_snapshot_lookup(candidate.snapshot, full_uid,
        entry->generation, &row) : -EAGAIN;
    if (!error && (!row.decision || row.profile_revision != entry->generation)) error = -EACCES;
    if (!error) error = msu_task_profile_check_current(msu_su_lease_validate, &candidate);
    if (error) { msu_su_lease_end(&candidate); return error; }
    *lease = candidate;
    return 0;
fail_locked:
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    return error;
}

static int msu_su_lease_validate_published_row(struct msu_su_lease *lease)
{
    struct msu_su_registry *registry = lease->registry;
    struct msu_su_install_entry *entry = lease->entry;
    struct msu_uid_snapshot *latest;
    struct msu_uid_snapshot_record row;
    unsigned long irq;
    int error = 0;
    raw_spin_lock_irqsave(&registry->lock, irq);
    if (registry->closed || entry->revoked || !entry->granted || !entry->users ||
        entry->generation != lease->generation) error = -ESTALE;
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    if (error) { lease->invalidated = true; return error; }
    latest = msu_uid_gateway_acquire(&registry->host, registry->controller);
    error = latest ? msu_uid_snapshot_lookup(latest, entry->full_uid,
        lease->generation, &row) : -ESTALE;
    if (!error && (!row.decision || row.profile_revision != lease->generation)) error = -ESTALE;
    msu_uid_snapshot_put(latest);
    if (error) lease->invalidated = true;
    return error;
}

int msu_su_lease_validate(void *context)
{
    struct msu_su_lease *lease = context;
    if (!lease || !lease->registry || !lease->entry || !lease->snapshot) return -EINVAL;
    if (!in_task() || in_nmi()) return -EWOULDBLOCK;
    if (lease->invalidated) return -ESTALE;
    if (current != lease->task || READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm)) != lease->mm ||
        READ_ONCE(MSU_DYN_FIELD(current, task_struct, self_exec_id)) != lease->exec_id ||
        msu_dynamic_current_cred() != lease->cred || msu_dynamic_current_real_cred() != lease->cred) {
        lease->invalidated = true;
        return -ESTALE;
    }
    return msu_su_lease_validate_published_row(lease);
}

int msu_su_lease_validate_after_exec(struct msu_su_lease *lease)
{
    if (!lease || !lease->registry || !lease->entry || !lease->snapshot) return -EINVAL;
    if (!in_task() || in_nmi()) return -EWOULDBLOCK;
    if (lease->invalidated) return -ESTALE;
    if (current != lease->task) {
        lease->invalidated = true;
        return -ESTALE;
    }
    return msu_su_lease_validate_published_row(lease);
}

const struct msu_su_profile *msu_su_lease_profile(const struct msu_su_lease *lease)
{
    if (!lease || !lease->registry || !lease->entry) return NULL;
    return &((struct msu_su_install_entry *)lease->entry)->profile;
}

const struct msu_su_profile *msu_su_lease_saved_profile(const struct msu_su_lease *lease)
{
    const struct msu_su_install_entry *entry;
    if (!lease || !lease->registry || !lease->entry || lease->invalidated) return NULL;
    entry = lease->entry;
    return &entry->saved_profile;
}

void msu_su_lease_end(struct msu_su_lease *lease)
{
    struct msu_su_install_entry *entry;
    unsigned long irq;
    if (!lease || !lease->registry) return;
    entry = lease->entry;
    raw_spin_lock_irqsave(&lease->registry->lock, irq);
    if (entry->users) --entry->users;
    raw_spin_unlock_irqrestore(&lease->registry->lock, irq);
    msu_uid_snapshot_put(lease->snapshot);
    msu_dynamic_put_cred(lease->cred);
    msu_dynamic_mmdrop(lease->mm);
    memset(lease, 0, sizeof(*lease));
}

/* Discovery does not acquire a credential lease. The factory retains this
 * registry while both bounded reads check its live admission/generation. */
static bool msu_su_discovery_row(struct msu_su_registry *registry, u32 full_uid,
    u64 *generation)
{
    struct msu_su_install_entry *entry;
    unsigned long irq;
    u32 position;
    bool allowed = false;
    raw_spin_lock_irqsave(&registry->lock, irq);
    position = msu_su_position(registry, full_uid);
    if (!registry->closed && registry->gateway_bound && !registry->gateway_binding &&
        !registry->gateway_retired && position < registry->count &&
        registry->entries[position]->full_uid == full_uid) {
        entry = registry->entries[position];
        if (!entry->revoked && !entry->busy && !entry->cancelled &&
            !entry->source_invalidated && entry->granted && entry->users != ~0U &&
            entry->generation && (!*generation || *generation == entry->generation)) {
            *generation = entry->generation;
            allowed = true;
        }
    }
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    return allowed;
}

u64 msu_su_read_current(const void *context)
{
    struct msu_su_registry *registry = (struct msu_su_registry *)context;
    const struct cred *cred;
    struct mm_struct *mm;
    struct msu_uid_snapshot *snapshot;
    struct msu_uid_snapshot_record row;
    u64 generation = 0, exec_id;
    u32 full_uid;
    int error;
    if (!registry || !in_task() || in_nmi()) return 0;
    /* Current keeps these borrowed objects live; no pointer escapes this call.
     * A real SU execution still enters the original full lease in prepare. */
    cred = msu_dynamic_current_cred();
    if (!cred || cred != msu_dynamic_current_real_cred() ||
        MSU_DYN_FIELD(cred, cred, user_ns) != &init_user_ns) return 0;
    mm = READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm));
    if (!mm) return 0;
    exec_id = READ_ONCE(MSU_DYN_FIELD(current, task_struct, self_exec_id));
    full_uid = __kuid_val(MSU_DYN_FIELD(cred, cred, uid));
    if (!msu_su_discovery_row(registry, full_uid, &generation)) return 0;
    /* Qualification includes the input Image's dynamic bit63/native marker;
     * no copied constant is trusted without the existing target proof. */
    if (!msu_su_sleepable() || !msu_task_profile_trace_ready() ||
        test_bit(MSU_TASK_PROFILE_DISABLE_ROOT_BIT,
            &MSU_DYN_FIELD(msu_dynamic_task_thread_info(current), thread_info, flags))) return 0;
    snapshot = msu_uid_gateway_acquire(&registry->host, registry->controller);
    if (!snapshot) return 0;
    error = msu_uid_snapshot_lookup(snapshot, full_uid, generation, &row);
    msu_uid_snapshot_put(snapshot);
    if (error || !row.decision || row.profile_revision != generation) return 0;
    if (msu_dynamic_current_cred() != cred || msu_dynamic_current_real_cred() != cred ||
        READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm)) != mm ||
        READ_ONCE(MSU_DYN_FIELD(current, task_struct, self_exec_id)) != exec_id ||
        (READ_ONCE(MSU_DYN_FIELD(current, task_struct, flags)) & PF_EXITING) ||
        !msu_task_profile_trace_ready() ||
        test_bit(MSU_TASK_PROFILE_DISABLE_ROOT_BIT,
            &MSU_DYN_FIELD(msu_dynamic_task_thread_info(current), thread_info, flags))) return 0;
    /* Revoke/refresh may have raced the immutable snapshot. Re-read the actual
     * provider; generation is monotonic and no positive decision is cached. */
    return msu_su_discovery_row(registry, full_uid, &generation) ? 1 : 0;
}

int msu_su_apply_current(const void *context, u32 *effects)
{
    struct msu_su_execution_receipt receipt;
    int error;
    if (!effects) return -EINVAL;
    *effects = 0;
    error = msu_su_apply_current_detailed(context, &receipt);
    *effects = receipt.credential_effects | receipt.task_effects;
    return error;
}

/* Conservative no-action projection from held protected installation members.
 * This name match is deliberately NOT Manager signature/Root authorization. */
int msu_su_registry_non_root_skip_manager(struct msu_su_registry *registry, bool *skip)
{
    const struct cred *cred;
    struct msu_su_install_entry *entry;
    unsigned long irq;
    u32 position, i;
    int error = 0;
    if (!registry || !skip) return -EINVAL;
    *skip = false;
    if (!in_task() || in_nmi()) return -EWOULDBLOCK;
    cred = msu_dynamic_current_real_cred();
    if (!cred || msu_dynamic_current_cred() != cred || MSU_DYN_FIELD(cred, cred, user_ns) != &init_user_ns ||
        __kuid_val(MSU_DYN_FIELD(cred, cred, uid)) != __kuid_val(MSU_DYN_FIELD(cred, cred, euid))) return -EPERM;
    raw_spin_lock_irqsave(&registry->lock, irq);
    if (registry->closed || registry->gateway_retired) error = -ESHUTDOWN;
    else if (registry->gateway_binding || !registry->gateway_bound) error = -EINPROGRESS;
    position = msu_su_position(registry, __kuid_val(MSU_DYN_FIELD(cred, cred, uid)));
    if (!error && position < registry->count &&
        registry->entries[position]->full_uid == __kuid_val(MSU_DYN_FIELD(cred, cred, uid))) {
        entry = registry->entries[position];
        if (entry->busy || entry->cancelled || entry->source_invalidated ||
            (entry->revoked && entry->granted)) error = -EINPROGRESS;
        else if (entry->installation.full_uid != __kuid_val(MSU_DYN_FIELD(cred, cred, uid)) ||
            !entry->installation.pin || !entry->installation.members ||
            !entry->installation.member_count ||
            entry->installation.member_count > MSU_SU_INSTALL_MAX_MEMBERS) error = -ESTALE;
        else for (i = 0; i < entry->installation.member_count; ++i)
            if (!strcmp(entry->installation.members[i].package, "dev.maplesu.manager")) {
                *skip = true;
                break;
            }
    }
    raw_spin_unlock_irqrestore(&registry->lock, irq);
    return error;
}

