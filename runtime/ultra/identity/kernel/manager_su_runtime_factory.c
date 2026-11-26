// SPDX-License-Identifier: GPL-3.0-only
#include <linux/binfmts.h>
#include <linux/cred.h>
#include <linux/compat.h>
#include <linux/err.h>
#include <linux/fs.h>
#include <linux/file.h>
#include <linux/errno.h>
#include <linux/irqflags.h>
#include <linux/mm.h>
#include <linux/mutex.h>
#include <linux/preempt.h>
#include <linux/rcupdate.h>
#include <linux/printk.h>
#include <linux/workqueue.h>
#include <linux/slab.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <linux/uaccess.h>
#include <linux/user_namespace.h>
#include <asm/ptrace.h>
#include <asm/processor.h>
#include "manager_management.h"
#include "manager_installation_state.h"
#include "manager_uid_gateway.h"
#include "manager_uid_snapshot.h"
#include "manager_su_gate.h"
#include "manager_su_runtime_factory.h"
#include "su_durable_authority.h"
#include "su_observed_install_source.h"
#include "su_profile_execution.h"
#include "su_exec_session.h"
#include "su_nonroot_transition.h"
#include "su_nonroot_transition_work.h"
#include "su_nonroot_mount_source.h"
#include "protected_install_kernel.h"
#include "manager_dynamic_layout.h"
#include "manager_task_state.h"
#include "manager_ksu_compat.h"

struct msu_su_runtime_entry {
    struct msu_su_runtime_hook target;
    u64 gate, gate_handle, payload, payload_handle, publication;
    u32 original, prefix;
};
static struct {
    maplesu_host_instance host;
    struct msu_su_observed_authority authority;
    struct msu_su_observed_provider *provider;
    struct msu_su_registry *registry;
    struct msu_su_nonroot_mount_source *mount_source;
    struct msu_su_runtime_entry entries[MSU_SU_RUNTIME_MAX_HOOKS];
    u64 controller, service, installation_fence;
    u32 count, users, producers, controller_effects;
    int target_error, cleanup_error, start_error, mount_source_error;
    unsigned char generation[16];
    maplesu_response_owner_v3 last_operation;
    bool initialized, bound, closed, started, published, work_initialized;
    bool installation_bound, installation_closed;
    struct work_struct start_work;
    char system_directory[25]; /* /data/ + 17 bytes + slash + NUL */
} runtime;
static DEFINE_MUTEX(msu_su_runtime_mutex);
static DEFINE_RAW_SPINLOCK(msu_su_runtime_lock);

static void msu_su_runtime_start_work(struct work_struct *work)
{
    int error;
    if (work != &runtime.start_work) return;
    error = msu_su_runtime_post_fs_data();
    if (error) pr_warn("MapleSU native SU post-fs-data failed: %d\n", error);
}
void msu_su_runtime_post_fs_data_event(void)
{
    /* Existing helper-authenticated event; queue on the system workqueue,
     * never spawn a persistent process or perform slow I/O under its mutex. */
    if (READ_ONCE(runtime.work_initialized) && !READ_ONCE(runtime.closed))
        schedule_work(&runtime.start_work);
}

static bool sleepable(void)
{
    return in_task() && !in_atomic() && !irqs_disabled();
}
static bool host_equal(const maplesu_host_instance *host)
{
    return host->low == runtime.host.low && host->high == runtime.host.high;
}
static int validate_constructor(void *context)
{
    return context == &runtime && !READ_ONCE(runtime.closed) &&
        (runtime.host.low | runtime.host.high) ? 0 : -ESHUTDOWN;
}
static struct msu_su_registry *acquire(void)
{
    struct msu_su_registry *registry = NULL;
    unsigned long irq;
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    if (READ_ONCE(runtime.initialized) && READ_ONCE(runtime.bound) &&
        READ_ONCE(runtime.started) && READ_ONCE(runtime.published) &&
        !runtime.closed && !runtime.installation_closed &&
        READ_ONCE(runtime.registry) && runtime.users != ~0U) {
        ++runtime.users;
        registry = runtime.registry;
    }
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    return registry;
}
static void release(void)
{
    unsigned long irq;
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    if (runtime.users) --runtime.users;
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
}
static int ksu_begin(u64 expected, bool compare, struct msu_su_registry **output, u64 *fence)
{
    const struct cred *cred;
    struct msu_su_registry *registry;
    unsigned long irq;
    int error = 0;
    if (!sleepable()) return -EWOULDBLOCK;
    if (!msu_runtime_layout_ready()) return -EAGAIN;
    cred = msu_dynamic_current_cred();
    if (!cred || cred != msu_dynamic_current_real_cred() ||
        MSU_DYN_FIELD(cred, cred, user_ns) != &init_user_ns ||
        __kuid_val(MSU_DYN_FIELD(cred, cred, uid)) ||
        __kuid_val(MSU_DYN_FIELD(cred, cred, euid))) return -EPERM;
    registry = acquire();
    if (!registry) return -ESHUTDOWN;
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    if (runtime.closed || runtime.installation_closed ||
        (compare && runtime.installation_fence != expected)) error = -ESTALE;
    else *fence = runtime.installation_fence;
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    if (error) { release(); return error; }
    *output = registry;
    return 0;
}
int msu_su_runtime_ksu_fence(u64 *fence)
{
    struct msu_su_registry *registry;
    int error;
    if (!fence) return -EINVAL;
    error = ksu_begin(0, false, &registry, fence);
    if (!error) release();
    return error;
}
int msu_su_runtime_ksu_validate(u64 fence)
{
    struct msu_su_registry *registry;
    u64 current_fence;
    int error = ksu_begin(fence, true, &registry, &current_fence);
    if (!error) release();
    return error;
}
int msu_su_runtime_ksu_query(u64 fence, u32 full_uid, bool *out_granted,
    enum msu_su_non_root_umount_policy *out_policy)
{
    struct msu_su_registry *registry;
    enum msu_su_non_root_umount_policy policy;
    bool granted;
    unsigned long irq;
    u64 current_fence;
    int error;
    if (!out_granted || !out_policy) return -EINVAL;
    error = ksu_begin(fence, true, &registry, &current_fence);
    if (error) return error;
    error = msu_su_registry_query_root(registry, full_uid, &granted, &policy);
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    if (!error && (runtime.closed || runtime.installation_closed ||
        runtime.installation_fence != fence)) error = -ESTALE;
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    if (!error) { *out_granted = granted; *out_policy = policy; }
    release();
    return error;
}
/* Native tracepoint marking is an atomic hint, never a credential grant.
 * Init/root/shell seed the fixed KernelSU flow; other fullUIDs need a real
 * published allow row. The permanent inline SU gate still validates its held
 * installation/Profile lease. Trace close drains these callbacks before the
 * factory releases its controller/provider and stable host storage. */
bool msu_su_runtime_trace_mark_task(struct task_struct *task)
{
    struct msu_uid_snapshot *snapshot = NULL;
    struct msu_uid_snapshot_metadata metadata;
    struct msu_uid_snapshot_record row;
    const struct cred *cred;
    maplesu_host_instance host;
    unsigned long irq;
    u64 controller = 0;
    u32 uid, low = 0, high, middle;
    bool mark = false;
    if (!task || !msu_runtime_layout_ready()) return false;
    rcu_read_lock();
    cred = rcu_dereference(MSU_DYN_FIELD(task, task_struct, real_cred));
    if (!cred || READ_ONCE(MSU_DYN_FIELD(cred, cred, user_ns)) != &init_user_ns ||
        (READ_ONCE(MSU_DYN_FIELD(task, task_struct, pid)) != 1 &&
         !READ_ONCE(MSU_DYN_FIELD(task, task_struct, mm)))) goto out;
    uid = __kuid_val(READ_ONCE(MSU_DYN_FIELD(cred, cred, uid)));
    if (READ_ONCE(MSU_DYN_FIELD(task, task_struct, pid)) == 1 || uid == 0 || uid == 2000 ||
        test_bit(MSU_TASK_PROFILE_DISABLE_ROOT_BIT,
            &MSU_DYN_FIELD(msu_dynamic_task_thread_info(task), thread_info, flags))) {
        mark = true;
        goto out;
    }
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    if (runtime.initialized && runtime.bound && !runtime.closed &&
        !runtime.installation_closed && runtime.registry) {
        host = runtime.host;
        controller = runtime.controller;
    }
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    if (!controller) goto out;
    snapshot = msu_uid_gateway_acquire(&host, controller);
    if (!snapshot || msu_uid_snapshot_metadata(snapshot, &metadata) ||
        metadata.schema != MSU_UID_SNAPSHOT_SCHEMA || metadata.reserved ||
        metadata.record_bytes != sizeof(row) || metadata.count > MSU_UID_SNAPSHOT_MAX_RECORDS ||
        !metadata.content_revision || metadata.host.low != host.low || metadata.host.high != host.high) goto out;
    high = metadata.count;
    while (low < high) {
        middle = low + (high - low) / 2;
        if (msu_uid_snapshot_record_at(snapshot, middle, &row)) goto out;
        if (row.full_uid < uid) low = middle + 1;
        else high = middle;
    }
    if (low < metadata.count && !msu_uid_snapshot_record_at(snapshot, low, &row) &&
        row.full_uid == uid && row.decision == 1) mark = true;
out:
    msu_uid_snapshot_put(snapshot);
    rcu_read_unlock();
    return mark;
}

struct msu_su_runtime_readback {
    struct msu_uid_snapshot *snapshot;
    struct task_struct *task;
    struct mm_struct *mm;
    const struct cred *cred;
    unsigned long exec_id;
    u64 fence;
    maplesu_installation_state_v3 installation;
    int (*validate)(void *);
    void *context;
};
static int view_validate(void *context)
{
    struct msu_su_runtime_readback *view = context;
    maplesu_installation_state_v3 installation = {0};
    unsigned long irq;
    int error;
    if (current != view->task || MSU_DYN_FIELD(current, task_struct, mm) != view->mm ||
        MSU_DYN_FIELD(current, task_struct, self_exec_id) != view->exec_id || msu_dynamic_current_cred() != view->cred ||
        msu_dynamic_current_real_cred() != view->cred) return -ESTALE;
    error = view->validate(view->context);
    if (error) return error > 0 ? -EIO : error;
    error = msu_installation_read(&installation);
    if (error) return error;
    if (!installation.record.active || installation.flags ||
        !host_equal(&installation.host) ||
        installation.device != view->installation.device ||
        installation.inode != view->installation.inode ||
        memcmp(installation.record.generation, view->installation.record.generation, 16))
        return installation.flags & MAPLESU_INSTALLATION_PENDING ? -EINPROGRESS : -ESTALE;
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    error = runtime.closed || runtime.installation_closed ||
        runtime.installation_fence != view->fence ? -ESTALE : 0;
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    return error;
}
static void view_drop(struct msu_su_runtime_readback *view)
{
    msu_uid_snapshot_put(view->snapshot);
    if (view->cred) msu_dynamic_put_cred(view->cred);
    if (view->mm) msu_dynamic_mmdrop(view->mm);
    if (view->task) { msu_dynamic_put_task_struct(view->task); release(); }
}
static int view_begin(struct msu_su_runtime_readback *view,
    int (*validate)(void *), void *context)
{
    unsigned long irq;
    int error;
    if (!validate) return -EINVAL;
    if (!sleepable()) return -EWOULDBLOCK;
    error = validate(context);
    if (error) return error > 0 ? -EIO : error;
    memset(view, 0, sizeof(*view));
    /* Only reference acquisition takes runtime mutex. In particular standalone
     * authorize must never run under it: CORE dispatch holds session.operation
     * before entering here. Every observer scan acquisition is trylock. */
    mutex_lock(&msu_su_runtime_mutex);
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    if (!runtime.initialized || !runtime.bound || !runtime.started) error = -EAGAIN;
    else if (runtime.closed) error = -ESHUTDOWN;
    else if (runtime.installation_closed) error = -ESTALE;
    else if (runtime.users == ~0U) error = -EOVERFLOW;
    else { ++runtime.users; view->fence = runtime.installation_fence; error = 0; }
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    mutex_unlock(&msu_su_runtime_mutex);
    if (error) return error;
    view->task = current; msu_dynamic_get_task_struct(view->task);
    view->mm = MSU_DYN_FIELD(current, task_struct, mm);
    if (view->mm) msu_dynamic_mmgrab(view->mm);
    view->cred = msu_dynamic_get_current_cred(); view->exec_id = MSU_DYN_FIELD(current, task_struct, self_exec_id);
    view->validate = validate; view->context = context;
    error = msu_installation_read(&view->installation);
    if (!error) error = view_validate(view);
    if (error) { view_drop(view); memset(view, 0, sizeof(*view)); }
    return error;
}

int msu_su_runtime_sync_held(u32 full_uid, maplesu_response_owner_v3 *response,
    int (*validate)(void *), void *context, struct msu_su_observed_sync_receipt *detail)
{
    struct msu_su_observed_sync_receipt local;
    struct msu_su_runtime_readback view;
    u64 unchanged_revision = 0;
    int error, post_error;
    if (!response) return -EINVAL;
    memset(response, 0, sizeof(*response));
    if (!detail) detail = &local;
    memset(detail, 0, sizeof(*detail)); detail->full_uid = full_uid;
    error = view_begin(&view, validate, context);
    if (error) goto out;
    error = msu_su_observed_sync(runtime.provider, full_uid, view_validate, &view, detail);
    response->metadata_length = detail->effects;
    response->result = detail->committed_revision;
    if (!error && !detail->committed_revision) {
        struct msu_uid_snapshot_metadata metadata;
        struct msu_uid_snapshot *snapshot =
            msu_uid_gateway_acquire(&runtime.host, runtime.controller);
        error = snapshot ? msu_uid_snapshot_metadata(snapshot, &metadata) : -ESTALE;
        if (!error && (!host_equal(&metadata.host) || !metadata.content_revision ||
            metadata.record_bytes != sizeof(maplesu_uid_record_v3) ||
            metadata.count > MSU_UID_SNAPSHOT_MAX_RECORDS)) error = -EUCLEAN;
        if (!error) unchanged_revision = metadata.content_revision;
        msu_uid_snapshot_put(snapshot);
    }
    post_error = view_validate(&view);
    if (!error) error = post_error;
    if (!error && !detail->committed_revision) response->result = unchanged_revision;
    view_drop(&view);
out:
    detail->status = response->status = error;
    if (error == -EINPROGRESS) response->metadata_length |= MAPLESU_EFFECT_PENDING;
    detail->effects = response->metadata_length;
    return error;
}
struct standalone_manager { u32 full_uid, epoch; };
static int standalone_validate(void *context)
{
    const struct standalone_manager *expected = context;
    u32 uid, epoch;
    int error = msu_management_authorize(&uid, &epoch);
    if (error) return error;
    if (msu_management_current_helper()) return -EPERM;
    return uid == expected->full_uid && epoch == expected->epoch ? 0 : -ESTALE;
}
int msu_su_runtime_sync(u32 full_uid, maplesu_response_owner_v3 *response)
{
    struct standalone_manager manager;
    int error;
    if (!response) return -EINVAL;
    memset(response, 0, sizeof(*response));
    error = msu_management_authorize(&manager.full_uid, &manager.epoch);
    if (!error && msu_management_current_helper()) error = -EPERM;
    if (error) return response->status = error;
    return msu_su_runtime_sync_held(full_uid, response, standalone_validate, &manager, NULL);
}
int msu_su_runtime_readback_acquire(int (*validate)(void *), void *context,
    struct msu_su_runtime_readback **output, maplesu_uid_metadata_v3 *metadata,
    maplesu_response_owner_v3 *response)
{
    struct msu_su_runtime_readback *view;
    struct msu_uid_snapshot_metadata native;
    maplesu_uid_metadata_v3 projected = {0};
    int error;
    if (!response) return -EINVAL;
    memset(response, 0, sizeof(*response));
    if (!output || !metadata) return response->status = -EINVAL;
    *output = NULL;
    if (!sleepable()) return response->status = -EWOULDBLOCK;
    view = kzalloc(sizeof(*view), GFP_KERNEL);
    if (!view) return response->status = -ENOMEM;
    error = view_begin(view, validate, context);
    if (error) goto free;
    view->snapshot = msu_uid_gateway_acquire(&runtime.host, runtime.controller);
    error = view->snapshot ? msu_uid_snapshot_metadata(view->snapshot, &native) : -ESTALE;
    if (error) goto drop;
    if (!host_equal(&native.host) || native.record_bytes != sizeof(maplesu_uid_record_v3) ||
        native.count > MSU_UID_SNAPSHOT_MAX_RECORDS) { error = -EUCLEAN; goto drop; }
    projected.schema = native.schema; projected.count = native.count;
    projected.record_bytes = native.record_bytes; projected.reserved = native.reserved;
    projected.content_revision = native.content_revision; projected.host = native.host;
    error = view_validate(view);
    if (error) goto drop;
    *metadata = projected; *output = view;
    response->result = native.content_revision;
    return 0;
drop:
    view_drop(view);
free:
    kfree(view);
    response->status = error;
    if (error == -EINPROGRESS) response->metadata_length |= MAPLESU_EFFECT_PENDING;
    return error;
}
int msu_su_runtime_readback_record(struct msu_su_runtime_readback *view,
    u32 index, maplesu_uid_record_v3 *record)
{
    struct msu_uid_snapshot_record native;
    maplesu_uid_record_v3 projected = {0};
    int error;
    if (!view || !record || !view->snapshot) return -EINVAL;
    if (!sleepable()) return -EWOULDBLOCK;
    error = view_validate(view);
    if (!error) error = msu_uid_snapshot_record_at(view->snapshot, index, &native);
    if (error) return error;
    projected.full_uid = native.full_uid; projected.decision = native.decision;
    projected.installation_generation = native.installation_generation;
    projected.profile_revision = native.profile_revision;
    memcpy(projected.package, native.package, sizeof(projected.package));
    error = view_validate(view);
    if (!error) *record = projected;
    return error;
}
void msu_su_runtime_readback_put(struct msu_su_runtime_readback *view)
{
    if (view) { view_drop(view); kfree(view); }
}
int msu_su_runtime_status_get(const maplesu_host_instance *host,
    struct msu_su_runtime_status *output)
{
    maplesu_installation_state_v3 installation = {0};
    unsigned long irq;
    u64 fence;
    u32 i;
    bool generation = false, changed, service;
    int error;
    _Static_assert(sizeof(*output) == 104, "SU runtime facts layout");
    if (!output) return -EINVAL;
    memset(output, 0, sizeof(*output));
    output->schema = MSU_SU_RUNTIME_STATUS_SCHEMA;
    output->bytes = sizeof(*output);
    if (!host || !(host->low | host->high)) return output->status = -EINVAL;
    if (!sleepable()) return output->status = -EWOULDBLOCK;
    mutex_lock(&msu_su_runtime_mutex);
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    if (!(runtime.host.low | runtime.host.high)) error = -ENODEV;
    else if (!host_equal(host)) error = -ESTALE;
    else if (runtime.users == ~0U) error = -EOVERFLOW;
    else { ++runtime.users; error = 0; }
    fence = runtime.installation_fence;
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    if (error) goto out;
    /* Same lock order as the real producer: runtime mutex -> installation.
     * Revoke takes only runtime's raw lock and never waits on this mutex. */
    error = msu_installation_read(&installation);
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    output->host = runtime.host;
    memcpy(output->runtime_generation, runtime.generation, 16);
    output->installation_fence = runtime.installation_fence;
    output->flags = (runtime.initialized ? MSU_SU_RUNTIME_INITIALIZED : 0) |
        (runtime.bound ? MSU_SU_RUNTIME_BOUND : 0) |
        (runtime.started ? MSU_SU_RUNTIME_STARTED : 0) |
        (runtime.published ? MSU_SU_RUNTIME_PUBLISHED : 0) |
        (runtime.closed ? MSU_SU_RUNTIME_CLOSED : 0) |
        (runtime.installation_closed ? MSU_SU_RUNTIME_INSTALLATION_CLOSED : 0);
    output->users = runtime.users - 1; /* exclude this real diagnostic ref */
    output->producers = runtime.producers;
    output->hook_count = runtime.count;
    output->target_error = runtime.target_error;
    output->cleanup_error = runtime.cleanup_error;
    output->start_error = READ_ONCE(runtime.start_error);
    output->target_qualified = msu_su_runtime_target_facts[8];
    changed = fence != runtime.installation_fence;
    service = runtime.service != 0;
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    if (!error) {
        memcpy(output->installation_generation, installation.record.generation, 16);
        output->installation_flags = installation.flags;
    }
    for (i = 0; i < 16; ++i) generation |= output->runtime_generation[i] != 0;
    if (!(output->flags & MSU_SU_RUNTIME_INITIALIZED) ||
        !(output->flags & MSU_SU_RUNTIME_BOUND)) error = -ENODEV;
    else if (output->flags & MSU_SU_RUNTIME_CLOSED) error = -ESHUTDOWN;
    else if (!error && (installation.host.low != host->low || installation.host.high != host->high))
        error = -ESTALE;
    else if (!error && (installation.flags & MAPLESU_INSTALLATION_PENDING)) error = -EINPROGRESS;
    else if (!error && (!installation.record.active || installation.flags || changed)) error = -ESTALE;
    else if (!error && (output->flags & MSU_SU_RUNTIME_INSTALLATION_CLOSED))
        error = service || output->users || output->producers ? -EINPROGRESS : -ESTALE;
    else if (!error && generation && memcmp(output->runtime_generation,
        output->installation_generation, 16)) error = -ESTALE;
    else if (!error && output->target_error) error = output->target_error;
    else if (!error && output->start_error) error = output->start_error;
    else if (!error && !(output->flags & MSU_SU_RUNTIME_PUBLISHED) && output->cleanup_error)
        error = output->cleanup_error;
    else if (!error && (!(output->flags & MSU_SU_RUNTIME_STARTED) ||
        !(output->flags & MSU_SU_RUNTIME_PUBLISHED))) error = -EAGAIN;
    output->status = error;
    release();
out:
    output->status = error;
    mutex_unlock(&msu_su_runtime_mutex);
    return error;
}
static int producer_begin(void)
{
    unsigned long irq;
    int error = 0;
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    if (!runtime.initialized || !runtime.bound || runtime.closed) error = -ESHUTDOWN;
    else if (runtime.producers == ~0U) error = -EOVERFLOW;
    else ++runtime.producers;
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    return error;
}
static void producer_end(void)
{
    unsigned long irq;
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    if (runtime.producers) --runtime.producers;
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
}
static void uid_path(char output[40], u32 uid)
{
    static const char digits[] = "0123456789abcdef";
    u32 i;
    memcpy(output, runtime.system_directory, 24);
    memcpy(output + 24, "u-00000000/su", 14);
    for (i = 0; i < 8; ++i) output[26 + i] = digits[(uid >> (28 - 4 * i)) & 15U];
}
static bool user_su(const char __user *path)
{
    static const char expected[] = "/system/bin/su";
    char value[sizeof(expected)] = {0};
    return path && !copy_from_user_nofault(value, path, sizeof(value)) &&
        !memcmp(value, expected, sizeof(expected));
}
/* A scheduling hint only. The callback reads the original syscall result and
 * reacquires the current registry; no borrowed pointer survives the syscall. */
static void nonroot_before(struct msu_su_registry *registry,
    const struct msu_su_runtime_entry *entry, const struct msu_su_gate_frame *frame)
{
    const struct pt_regs *regs = (const void *)(unsigned long)frame->x[0];
    u32 uid;
    if (__kuid_val(msu_dynamic_current_uid()) || !entry->target.syscall_frame ||
        regs != msu_dynamic_task_pt_regs(current)) return;
    uid = (u32)READ_ONCE(regs->regs[0]);
    if (entry->target.kind == MSU_SU_SETRESUID16) uid &= 0xffffU;
    if (!uid || uid == (entry->target.kind == MSU_SU_SETRESUID16 ? 0xffffU : ~0U)) return;
    if (msu_su_registry_non_root_wants_transition(registry, uid))
        msu_su_nonroot_work_prepare(0, regs);
}

int msu_su_runtime_nonroot_complete(u32 old_uid, long syscall_result,
    struct msu_su_nonroot_receipt *receipt)
{
    struct msu_su_registry *registry;
    struct msu_su_nonroot_mounts *mounts = NULL;
    enum msu_su_non_root_umount_policy policy;
    u32 uid;
    int error;
    if (!receipt) return -EINVAL;
    memset(receipt, 0, sizeof(*receipt));
    if (!msu_runtime_layout_ready()) return receipt->error = receipt->validation_error = -EAGAIN;
    uid = __kuid_val(msu_dynamic_current_uid());
    receipt->full_uid = uid;
    if (syscall_result < 0 || uid == old_uid) return 0;
    if (syscall_result) return receipt->error = receipt->validation_error = -EINVAL;
    registry = acquire();
    if (!registry) return receipt->error = receipt->validation_error = -ESHUTDOWN;
    error = msu_su_registry_non_root_policy(registry, uid, &policy);
    if (!error) {
        receipt->policy = policy;
        if (policy == MSU_SU_NON_ROOT_UMOUNT_ENABLED) {
            error = runtime.mount_source ?
                msu_su_nonroot_mount_source_take(runtime.mount_source, registry, &mounts) :
                (runtime.mount_source_error ? runtime.mount_source_error : -ENODATA);
            if (!error && mounts)
                error = msu_su_nonroot_after_setresuid(registry, old_uid,
                    syscall_result, mounts, receipt);
            msu_su_nonroot_mount_source_put(runtime.mount_source, mounts);
        }
    }
    if (error && !receipt->error)
        receipt->error = receipt->validation_error = error;
    release();
    return error;
}
/* Native payload, called only after the permanent publication gate entered.
 * Exactly one skips the original function; all other paths preserve arguments.
 * Ordinary denied calls perform no disk/credential/mount/property operations. */
static u64 msu_su_runtime_before(struct msu_su_gate_frame *frame, const void *context)
{
    const struct msu_su_runtime_entry *entry = context;
    struct msu_su_registry *registry;
    struct msu_su_execution_receipt receipt;
    struct msu_su_exec_session *session;
    struct file *executable;
    struct pt_regs regs;
    char replacement[40], name[256];
    const char *kernel_path;
    u64 filename, user_destination;
    size_t bytes, original_bytes;
    u32 uid;
    struct filename *owned_filename;
    int error;
    u64 skip = 0;
    if (!msu_runtime_layout_ready() || !frame || entry < runtime.entries || entry >= runtime.entries + runtime.count) return 0;
    uid = __kuid_val(msu_dynamic_current_uid());
    registry = acquire();
    if (!registry) return 0;
    if (entry->target.kind == MSU_SU_KSU_REBOOT) {
        long result;
        if (entry->target.syscall_frame &&
            msu_ksu_compat_reboot((const void *)(unsigned long)frame->x[0], &result)) {
            frame->x[0] = (u64)(s64)result;
            skip = 1;
        }
        goto out;
    }
    if (entry->target.kind == MSU_SU_SETRESUID || entry->target.kind == MSU_SU_SETRESUID16) {
        nonroot_before(registry, entry, frame);
        goto out;
    }
    if (msu_su_read_current(registry) != 1) goto out;
    if (entry->target.kind == MSU_SU_EXEC) {
        filename = frame->x[entry->target.argument_register];
        if (IS_ERR_OR_NULL((void *)(unsigned long)filename) ||
            copy_from_kernel_nofault(&kernel_path,
                (void *)(unsigned long)(filename + entry->target.filename_member), sizeof(kernel_path)) ||
            !kernel_path || copy_from_kernel_nofault(name, kernel_path, sizeof(name))) goto out;
        original_bytes = strnlen(name, sizeof(name));
        if (original_bytes == sizeof(name) || original_bytes < 3 ||
            memcmp(name + original_bytes - 3, "/su", 4)) goto out;
        uid_path(replacement, 0);
        /* Resolve the protected deployment from PID1's root before applying
         * the Profile. The actual exec uses this held file through AT_EMPTY_PATH
         * so a changed path cannot inherit the receipt of another executable. */
        executable = msu_kernel_open_installation_path(replacement, false);
        if (IS_ERR(executable)) { error = PTR_ERR(executable); goto exec_error; }
        owned_filename = getname_kernel("");
        if (IS_ERR(owned_filename)) {
            error = PTR_ERR(owned_filename);
            fput(executable);
            goto exec_error;
        }
        error = msu_su_exec_session_prepare(registry, executable, &session);
        fput(executable);
        if (error) { putname(owned_filename); goto exec_error; }
        memset(&receipt, 0, sizeof(receipt));
        error = msu_su_exec_session_apply(session, &receipt);
        if (error) {
            msu_su_exec_session_abort(session);
            putname(owned_filename);
            goto exec_error;
        }
        /* Publish only after the full single-core transaction. TWA_RESUME owns
         * failure/exit cleanup and verifies the exact exec before user code. */
        error = msu_su_exec_session_publish(session);
        if (error) {
            msu_su_exec_session_abort(session);
            putname(owned_filename);
            goto exec_error;
        }
        frame->x[entry->target.fd_register] = msu_su_exec_session_exec_fd(session);
        frame->x[entry->target.flags_register] = AT_EMPTY_PATH;
        putname((struct filename *)(unsigned long)filename);
        frame->x[entry->target.argument_register] = (unsigned long)owned_filename;
        goto out;
exec_error:
        putname((struct filename *)(unsigned long)filename);
        frame->x[0] = (u64)(s64)error;
        skip = 1; /* retain the original error and any already committed effects */
        goto out;
    }
    if (!entry->target.syscall_frame ||
        copy_from_kernel_nofault(&regs, (void *)(unsigned long)frame->x[0], sizeof(regs))) goto out;
    filename = regs.regs[entry->target.argument_register];
    if (is_compat_task()) filename = (u32)filename;
    if (!user_su((const char __user *)(unsigned long)filename)) goto out;
    if (entry->target.kind == MSU_SU_ACCESS) {
        frame->x[0] = 0;
        skip = 1; /* existing m086/m087 successful SU discovery semantics */
        goto out;
    }
    if (entry->target.kind != MSU_SU_STAT) goto out;
    uid_path(replacement, uid);
    bytes = strlen(replacement) + 1;
    /* Match the existing stat payload: temporary filename in caller statbuf.
     * No user argument is changed unless its complete copy succeeded. */
    user_destination = regs.regs[entry->target.result_register];
    if (is_compat_task()) user_destination = (u32)user_destination;
    if (!user_destination || copy_to_user_nofault((void __user *)(unsigned long)user_destination,
        replacement, bytes)) goto out;
    ((struct pt_regs *)(unsigned long)frame->x[0])->regs[entry->target.argument_register] = user_destination;
out:
    release();
    return skip;
}

static int create_entries(void)
{
    struct msu_su_runtime_hook targets[MSU_SU_RUNTIME_MAX_HOOKS];
    struct msu_runtime_service_descriptor descriptor = {0};
    u64 entries[MSU_SU_RUNTIME_MAX_HOOKS];
    u32 i, instruction;
    int error = msu_su_runtime_target_hooks(targets, &runtime.count);
    if (error) return error;
    if (!runtime.count || runtime.count > MSU_SU_RUNTIME_MAX_HOOKS) return -EINVAL;
    for (i = 0; i < runtime.count; ++i) {
        struct msu_su_runtime_entry *entry = &runtime.entries[i];
        entry->target = targets[i];
        if (!entry->target.entry || (entry->target.entry & 3U) ||
            entry->target.argument_register > 7 || entry->target.result_register > 7 ||
            (entry->target.kind == MSU_SU_EXEC &&
             (entry->target.fd_register > 7 || entry->target.flags_register > 7 ||
              entry->target.fd_register == entry->target.argument_register ||
              entry->target.flags_register == entry->target.argument_register ||
              entry->target.flags_register == entry->target.fd_register)) ||
            copy_from_kernel_nofault(&instruction,
                (void *)(unsigned long)entry->target.entry, sizeof(instruction))) return -EFAULT;
        if ((instruction & 0xffffff3fU) == 0xd503241fU ||
            instruction == 0xd503233fU || instruction == 0xd503237fU ||
            instruction == 0xd503231fU || instruction == 0xd503235fU) {
            entry->prefix = instruction;
            entry->target.entry += 4;
            if (copy_from_kernel_nofault(&instruction,
                (void *)(unsigned long)entry->target.entry, sizeof(instruction))) return -EFAULT;
        }
        if (!msu_su_gate_instruction(instruction)) return -EOPNOTSUPP;
        entry->original = instruction;
        entries[i] = entry->target.entry;
    }
    descriptor.host = runtime.host;
    descriptor.kind = MSU_RUNTIME_SERVICE_UID_SU;
    descriptor.lifecycle_owner = &runtime;
    descriptor.entries = entries;
    descriptor.entry_count = runtime.count;
    return msu_runtime_service_register(&descriptor, &runtime.service);
}
static int upload_entries(void)
{
    maplesu_response_owner_v3 response;
    maplesu_publication_member_v3 members[2];
    maplesu_publication_prepare_v3 descriptor;
    maplesu_publication_control_v3 control;
    u32 gate[MSU_SU_GATE_BYTES / 4], payload[MSU_SU_PAYLOAD_BYTES / 4], written, i;
    int error;
    for (i = 0; i < runtime.count; ++i) {
        struct msu_su_runtime_entry *entry = &runtime.entries[i];
        memset(&response, 0, sizeof(response));
        error = msu_runtime_service_alloc(&runtime.host, runtime.service, &runtime,
            MSU_SU_GATE_BYTES, true, entry->target.entry, &response);
        entry->gate_handle = response.metadata_address;
        entry->gate = response.result;
        runtime.last_operation = response;
        if (error) return error;
        memset(&response, 0, sizeof(response));
        error = msu_runtime_service_alloc(&runtime.host, runtime.service, &runtime,
            MSU_SU_PAYLOAD_BYTES, true, 0, &response);
        entry->payload_handle = response.metadata_address;
        entry->payload = response.result;
        runtime.last_operation = response;
        if (error) return error;
        memset(members, 0, sizeof(members));
        members[0].handle = entry->gate_handle;
        members[0].role = MAPLESU_PUBLICATION_GATE_CODE;
        members[1].handle = entry->payload_handle;
        members[1].role = MAPLESU_PUBLICATION_PAYLOAD_CODE;
        memset(&descriptor, 0, sizeof(descriptor));
        descriptor.member_count = 2;
        descriptor.entry_address = entry->target.entry;
        descriptor.expected_instruction = entry->original;
        descriptor.payload_entry = entry->payload;
        memset(&response, 0, sizeof(response));
        error = msu_runtime_service_prepare(&runtime.host, runtime.service, &runtime,
            &descriptor, members, &response);
        entry->publication = response.result;
        runtime.last_operation = response;
        if (error) return error;
        if (!response.metadata_address ||
            copy_from_kernel_nofault(&control, (void *)(unsigned long)response.metadata_address,
                sizeof(control))) return -EFAULT;
        if (control.entry_address != entry->target.entry || control.payload_entry != entry->payload)
            return -ESTALE;
        error = msu_su_gate_build(gate, sizeof(gate), entry->gate, &control,
            entry->original, entry->prefix, &written);
        runtime.last_operation = response;
        if (error) return error;
        error = msu_su_payload_build(payload, sizeof(payload), (unsigned long)msu_su_runtime_before,
            (unsigned long)entry);
        runtime.last_operation = response;
        if (error) return error;
        error = msu_runtime_service_write(&runtime.host, runtime.service, &runtime,
            entry->payload_handle, 0, payload, sizeof(payload), &response);
        runtime.last_operation = response;
        if (error) return error;
        error = msu_runtime_service_write(&runtime.host, runtime.service, &runtime,
            entry->gate_handle, 0, gate, sizeof(gate), &response);
        runtime.last_operation = response;
        if (error) return error;
    }
    for (i = 0; i < runtime.count; ++i) {
        error = msu_runtime_service_publish(&runtime.host, runtime.service, &runtime,
            runtime.entries[i].publication, &response);
        runtime.last_operation = response;
        if (error) return error;
    }
    return 0;
}

int msu_su_runtime_init(const maplesu_host_instance *host,
    const struct msu_deployment_namespace_record *deployment)
{
    const struct cred *boot_cred;
    u64 revision = 0;
    int error;
    if (!host || !(host->low | host->high) || !msu_deployment_namespace_valid(deployment)) return -EINVAL;
    if (!msu_runtime_layout_ready()) return -EAGAIN;
    if (!sleepable()) return -EWOULDBLOCK;
    mutex_lock(&msu_su_runtime_mutex);
    if (runtime.initialized || runtime.controller || runtime.provider) { error = -EALREADY; goto out; }
    runtime.host = *host;
    INIT_WORK(&runtime.start_work, msu_su_runtime_start_work);
    runtime.work_initialized = true;
    memcpy(runtime.system_directory, "/data/", 6);
    memcpy(runtime.system_directory + 6, deployment->name, 16);
    runtime.system_directory[22] = deployment->name[0];
    runtime.system_directory[23] = '/';
    error = msu_su_durable_authority_create(deployment->name, &runtime.authority);
    if (error) goto out;
    /* Capture the real bridge constructor's credential. The source owns its
     * own reference, without manufacturing init_cred or an application grant. */
    boot_cred = msu_dynamic_get_current_cred();
    error = msu_su_nonroot_mount_source_create(host, boot_cred, &runtime.mount_source);
    msu_dynamic_put_cred(boot_cred);
    runtime.mount_source_error = error;
    /* This optional per-app action never becomes a new SU boot prerequisite.
     * An Enabled consumer preserves the actual source-construction error. */
    error = msu_uid_gateway_create_kernel(&runtime.host, validate_constructor, &runtime,
        &runtime.controller, &revision, &runtime.controller_effects);
    if (error) goto put_authority;
    if (revision != 1) { error = -ESTALE; goto put_authority; }
    error = msu_su_observed_create(&runtime.host, runtime.controller, &runtime.authority,
        &runtime.provider, &runtime.registry);
    if (error) goto put_authority;
    runtime.initialized = true;
    error = msu_su_registry_bind_gateway(runtime.registry);
    if (error) goto close_source;
    runtime.bound = true;
    error = msu_installation_bind_lifecycle(&runtime.host, &runtime,
        msu_su_runtime_revoke_install, &runtime);
    if (error) goto out; /* bound registry is retired only by real host teardown */
    runtime.installation_bound = true;
    error = msu_su_nonroot_work_open();
    if (error) goto out;
    error = msu_ksu_compat_open();
    if (error) goto out;
    /* The actual target failure is retained; empty registry/source still have
     * a real owner and lifetime. It is not SU or publication success. */
    runtime.target_error = create_entries();
    goto out;
close_source:
    runtime.closed = true;
    msu_su_observed_close(runtime.provider);
    if (!msu_su_registry_destroy_quiesced(runtime.registry)) {
        runtime.registry = NULL;
        runtime.provider = NULL;
    }
    goto out;
put_authority:
    msu_su_nonroot_mount_source_destroy(&runtime.mount_source);
    runtime.authority.put_owner(runtime.authority.owner);
    memset(&runtime.authority, 0, sizeof(runtime.authority));
out:
    mutex_unlock(&msu_su_runtime_mutex);
    return error;
}

int msu_su_runtime_post_fs_data(void)
{
    u32 *uids, count = 0, effects = 0;
    maplesu_installation_state_v3 installation, final_installation;
    unsigned long irq;
    u64 fence;
    int error, cleanup;
    bool trace_opened = false;
    if (!sleepable()) return -EWOULDBLOCK;
    uids = kvmalloc_array(MSU_SU_REGISTRY_MAX_UIDS, sizeof(*uids), GFP_KERNEL);
    if (!uids) { WRITE_ONCE(runtime.start_error, -ENOMEM); return -ENOMEM; }
    mutex_lock(&msu_su_runtime_mutex);
    if (!runtime.initialized || !runtime.bound || runtime.closed) { error = -ESHUTDOWN; goto out; }
    trace_opened = true;
    error = msu_task_profile_trace_open();
    if (error) goto out;
    if (runtime.started && !runtime.installation_closed) {
        /* Explicit post-fs-data event retries source-only failures, no polling.
         * This mutex keeps source ownership while close returns PENDING. */
        if (runtime.mount_source)
            runtime.mount_source_error = msu_su_nonroot_mount_source_refresh(runtime.mount_source);
        error = runtime.target_error;
        goto out;
    }
    error = producer_begin();
    if (error) goto out;
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    fence = runtime.installation_fence;
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    error = msu_installation_read(&installation);
    if (error) goto finish;
    if (!installation.record.active || installation.flags || !host_equal(&installation.host)) {
        error = -ESTALE;
        goto finish;
    }
    error = msu_su_durable_authority_load(&runtime.authority, uids, MSU_SU_REGISTRY_MAX_UIDS, &count);
    if (error == -ENOENT) { error = 0; count = 0; }
    if (error) goto finish;
    error = msu_su_observed_start(runtime.provider, uids, count);
    if (error) goto finish;
    if (runtime.mount_source)
        runtime.mount_source_error = msu_su_nonroot_mount_source_refresh(runtime.mount_source);
    if (runtime.mount_source_error)
        pr_warn("MapleSU non-root mount source unavailable: %d\n", runtime.mount_source_error);
    /* Source errors invalidate only explicit Enabled actions. A missing or
     * invalid mount ledger is not a new prerequisite for ordinary SU startup. */
    /* Observer start owns the SAME registry's real prepare/commit receipt. */
    WRITE_ONCE(runtime.started, true);
    if (runtime.installation_closed) {
        /* The old generation's service actually drained before the installed
         * helper could enroll/BEGIN the new generation. Keep app grants/source. */
        if (runtime.service) { error = -EINPROGRESS; goto finish; }
        memset(runtime.entries, 0, sizeof(runtime.entries));
        runtime.target_error = create_entries();
    }
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    if (runtime.installation_fence != fence || runtime.closed) error = -ESTALE;
    else { runtime.installation_closed = false; error = 0; }
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    if (error) goto finish;
    if (runtime.target_error) { error = runtime.target_error; goto finish; }
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    memcpy(runtime.generation, installation.record.generation, 16);
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    error = upload_entries();
    if (!error) {
        error = msu_installation_read(&final_installation);
        if (!error && (!final_installation.record.active || final_installation.flags ||
            final_installation.device != installation.device ||
            final_installation.inode != installation.inode ||
            memcmp(final_installation.record.generation, installation.record.generation, 16)))
            error = -ESTALE;
        raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
        if (!error && (runtime.installation_fence != fence || runtime.closed)) error = -ESTALE;
        if (!error) runtime.published = true;
        raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    }
    if (error) {
        WRITE_ONCE(runtime.installation_closed, true);
        cleanup = msu_runtime_service_abort_unexposed(&runtime.host,
            runtime.service, &runtime, &effects);
        runtime.cleanup_error = cleanup;
        if (!cleanup) runtime.service = 0;
    }
finish:
    producer_end();
out:
    if (error && trace_opened) {
        cleanup = msu_task_profile_trace_close();
        if (cleanup) runtime.cleanup_error = cleanup;
    }
    WRITE_ONCE(runtime.start_error, error);
    mutex_unlock(&msu_su_runtime_mutex);
    kvfree(uids);
    return error;
}
int msu_su_runtime_refresh(u32 full_uid, maplesu_response_owner_v3 *response)
{
    int error;
    if (!response) return -EINVAL;
    memset(response, 0, sizeof(*response));
    if (!sleepable()) return -EWOULDBLOCK;
    mutex_lock(&msu_su_runtime_mutex);
    if (!runtime.initialized || !runtime.started || runtime.closed) error = -ESHUTDOWN;
    else {
        error = producer_begin();
        if (!error) {
            error = msu_su_observed_refresh(runtime.provider, full_uid);
            producer_end();
        }
    }
    response->status = error;
    mutex_unlock(&msu_su_runtime_mutex);
    return error;
}
int msu_su_runtime_close(void)
{
    unsigned long irq;
    u32 effects = 0, helper_effects = 0, users, producers;
    int error = 0;
    int helper_error;
    /* Host-only outside installation/gateway/session mutexes. Do not wait for
     * a queued producer: report PENDING until it has returned on its own. */
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    runtime.closed = true;
    users = runtime.users;
    producers = runtime.producers;
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    error = msu_task_profile_trace_close();
    helper_error = msu_su_nonroot_work_close();
    if (!error) error = helper_error;
    helper_error = msu_ksu_compat_close();
    if (!error) error = helper_error;
    msu_su_nonroot_mount_source_close(runtime.mount_source);
    {
        int source_error = msu_su_nonroot_mount_source_quiesce(runtime.mount_source);
        if (!error) error = source_error;
    }
    helper_error = runtime.host.low | runtime.host.high ?
        msu_installation_helper_close(&runtime.host, &helper_effects) : 0;
    if (helper_error && !error) error = helper_error;
    if (runtime.work_initialized) {
        cancel_work(&runtime.start_work);
        if (work_busy(&runtime.start_work) && !error) error = -EINPROGRESS;
    }
    if (runtime.provider) msu_su_observed_close(runtime.provider);
    if (runtime.service) {
        int service_error;
        if (!error && !users && !producers)
            service_error = msu_runtime_service_abort_unexposed(&runtime.host,
                runtime.service, &runtime, &effects);
        else service_error = msu_runtime_service_revoke(&runtime.host, runtime.service, &effects);
        if (!service_error && (effects & MAPLESU_EFFECT_RELEASED)) runtime.service = 0;
        if (!error) error = service_error;
    }
    if ((users || producers) && !error) error = -EINPROGRESS;
    if (!error && !runtime.bound && runtime.registry) {
        error = msu_su_registry_destroy_quiesced(runtime.registry);
        if (!error) { runtime.registry = NULL; runtime.provider = NULL; }
    }
    return error;
}
int msu_su_runtime_revoke_install(void *context, const maplesu_host_instance *host,
    const unsigned char generation[16], u32 *effects)
{
    unsigned long irq;
    u32 service_effects = 0;
    u64 service;
    bool active;
    int error = 0;
    if (context != &runtime || !host || !effects || !generation || !host_equal(host)) return -ESTALE;
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    runtime.installation_closed = true;
    if (runtime.installation_fence == ~0ULL) {
        raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
        return -EOVERFLOW;
    }
    ++runtime.installation_fence;
    active = runtime.users || runtime.producers;
    service = runtime.service;
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    error = msu_task_profile_trace_close();
    if (service) {
        int service_error = msu_runtime_service_revoke(&runtime.host, service, &service_effects);
        if (!error) error = service_error;
    }
    *effects |= service_effects;
    if (active && !error) error = -EINPROGRESS;
    if (!error) {
        raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
        runtime.service = 0;
        runtime.published = false;
        raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    } else *effects |= MAPLESU_EFFECT_PENDING;
    /* Environment closure depends on installed helper; unrelated UID grants,
     * observer, controller and source stay alive across install replacement. */
    return error;
}
int msu_su_runtime_discarded(void)
{
    unsigned long irq;
    int error = 0;
    /* Only resident discard calls after the gateway's mandatory real provider
     * retire and snapshot RCU/work drain returned zero. */
    mutex_lock(&msu_su_runtime_mutex);
    error = msu_task_profile_trace_close();
    if (error) {
        mutex_unlock(&msu_su_runtime_mutex);
        return error;
    }
    error = msu_su_nonroot_work_close();
    if (error) {
        mutex_unlock(&msu_su_runtime_mutex);
        return error;
    }
    error = msu_ksu_compat_close();
    if (error) {
        mutex_unlock(&msu_su_runtime_mutex);
        return error;
    }
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    if (runtime.users || runtime.producers) {
        raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
        mutex_unlock(&msu_su_runtime_mutex);
        return -EINPROGRESS;
    }
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    error = msu_su_nonroot_mount_source_destroy(&runtime.mount_source);
    if (error) {
        mutex_unlock(&msu_su_runtime_mutex);
        return error;
    }
    raw_spin_lock_irqsave(&msu_su_runtime_lock, irq);
    runtime.registry = NULL;
    runtime.provider = NULL;
    raw_spin_unlock_irqrestore(&msu_su_runtime_lock, irq);
    if (runtime.installation_bound)
        error = msu_installation_unbind_lifecycle(&runtime.host, &runtime);
    if (!error) memset(&runtime, 0, sizeof(runtime));
    mutex_unlock(&msu_su_runtime_mutex);
    return error;
}
