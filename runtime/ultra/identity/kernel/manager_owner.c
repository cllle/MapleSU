// SPDX-License-Identifier: GPL-3.0-only
#include <linux/cred.h>
#include <linux/errno.h>
#include <linux/sched.h>
#include <linux/slab.h>
#include <linux/user_namespace.h>
#include "manager_owner.h"
#include "manager_management.h"
#include "manager_owner_watch.h"
#include "protected_install_kernel.h"
#include "manager_dynamic_layout.h"

struct msu_manager_owner_binding {
    struct msu_manager_owner_binding *next;
    u64 identity;
    u32 user;
};

const unsigned int msu_manager_owner_compiled_layout[2] __attribute__((section(".msu.layout"))) = {
    offsetof(struct task_struct, cred), offsetof(struct cred, uid)
};

static bool msu_owner_uid_valid(u32 uid)
{
    const u32 app = uid % 100000U;
    return uid <= 2147483647U && app >= 10000U && app <= 19999U;
}

int msu_manager_owner_init(struct msu_manager_owner *owner)
{
    u32 expected = 0;
    int error;
    if (!owner) return -EINVAL;
    if (!msu_runtime_layout_ready()) return -EPROTO;
    if (!__atomic_compare_exchange_n(&owner->initialized, &expected, 1, false,
            __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE)) return -EALREADY;
    if (__atomic_load_n(&owner->bindings, __ATOMIC_RELAXED) ||
        __atomic_load_n(&owner->epoch, __ATOMIC_RELAXED)) return -EINVAL;
    error = msu_manager_owner_watch_init(owner);
    if (error) return error;
    __atomic_store_n(&owner->epoch, 1, __ATOMIC_RELEASE);
    __atomic_store_n(&owner->initialized, 2, __ATOMIC_RELEASE);
    return 0;
}

u32 msu_manager_owner_epoch(const struct msu_manager_owner *owner)
{
    if (!owner || __atomic_load_n(&owner->initialized, __ATOMIC_ACQUIRE) != 2)
        return 0;
    return __atomic_load_n(&owner->epoch, __ATOMIC_ACQUIRE);
}

static struct msu_manager_owner_binding *msu_owner_find(
    struct msu_manager_owner_binding *entry, u32 user)
{
    for (; entry; entry = entry->next)
        if (entry->user == user) return entry;
    return NULL;
}

static int msu_owner_bind(struct msu_manager_owner *owner, u32 uid, u32 epoch)
{
    struct msu_manager_owner_binding *fresh = NULL, *entry, *head;
    const u64 identity = ((u64)epoch << 32) | uid;
    u64 previous;
    for (;;) {
        if (msu_manager_owner_epoch(owner) != epoch) {
            kfree(fresh);
            return -EAGAIN;
        }
        head = __atomic_load_n(&owner->bindings, __ATOMIC_ACQUIRE);
        entry = msu_owner_find(head, uid / 100000U);
        if (entry) {
            kfree(fresh);
            /* An old in-flight claim must not overwrite a newer binding. */
            previous = __atomic_load_n(&entry->identity, __ATOMIC_ACQUIRE);
            for (;;) {
                if ((u32)(previous >> 32) > epoch ||
                    msu_manager_owner_epoch(owner) != epoch) return -EAGAIN;
                if (__atomic_compare_exchange_n(&entry->identity, &previous,
                        identity, false, __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE))
                    return msu_manager_owner_epoch(owner) == epoch ? 0 : -EAGAIN;
            }
        }
        if (!fresh) {
            fresh = kzalloc(sizeof(*fresh), GFP_KERNEL);
            if (!fresh) return -ENOMEM;
            fresh->user = uid / 100000U;
            fresh->identity = identity;
        }
        fresh->next = head;
        if (__atomic_compare_exchange_n(&owner->bindings, &head, fresh, false,
                __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE)) {
            /* Published records are never freed while any gateway may read. */
            return msu_manager_owner_epoch(owner) == epoch ? 0 : -EAGAIN;
        }
    }
}

int msu_manager_owner_claim(struct msu_manager_owner *owner,
    const struct msu_internal_manager_policy *policy, u32 *out_epoch)
{
    const struct cred *caller;
    u32 uid;
    const u32 epoch = msu_manager_owner_epoch(owner);
    struct msu_install_monitor monitor;
    u32 bound_epoch = 0, apk_generation = 0;
    int error;
    if (out_epoch) *out_epoch = 0;
    if (!msu_runtime_layout_ready()) return -EPROTO;
    caller = msu_dynamic_current_cred();
    uid = __kuid_val(msu_dynamic_current_uid());
    if (!out_epoch || !policy || !epoch || !msu_owner_uid_valid(uid)) return -EPERM;
    if (msu_manager_owner_allowed(owner, uid, &bound_epoch)) {
        if (bound_epoch != epoch || msu_dynamic_current_cred() != caller ||
            __kuid_val(msu_dynamic_current_uid()) != uid ||
            msu_manager_owner_epoch(owner) != epoch) return -EAGAIN;
        *out_epoch = epoch;
        return 0;
    }
    error = msu_manager_owner_watch_monitor(owner, &monitor);
    if (error) return error;
    error = msu_kernel_verify_manager_installation_watched(policy, &apk_generation, &monitor);
    if (error) return error;
    if (apk_generation < policy->pin.minimum_generation) return -EPERM;
    if (msu_dynamic_current_cred() != caller || __kuid_val(msu_dynamic_current_uid()) != uid ||
        msu_manager_owner_epoch(owner) != epoch) return -EAGAIN;
    error = msu_owner_bind(owner, uid, epoch);
    if (error) return error;
    if (msu_dynamic_current_cred() != caller || __kuid_val(msu_dynamic_current_uid()) != uid ||
        msu_manager_owner_epoch(owner) != epoch) return -EAGAIN;
    *out_epoch = epoch;
    return 0;
}

bool msu_manager_owner_allowed(const struct msu_manager_owner *owner,
    u32 actual_uid, u32 *out_epoch)
{
    struct msu_manager_owner_binding *entry;
    u64 identity;
    u32 epoch;
    if (out_epoch) *out_epoch = 0;
    if (!msu_runtime_layout_ready() || !out_epoch || !msu_owner_uid_valid(actual_uid) ||
        __kuid_val(msu_dynamic_current_uid()) != actual_uid) return false;
    epoch = msu_manager_owner_epoch(owner);
    if (!epoch) return false;
    entry = msu_owner_find(__atomic_load_n(&owner->bindings, __ATOMIC_ACQUIRE),
        actual_uid / 100000U);
    if (!entry) return false;
    identity = __atomic_load_n(&entry->identity, __ATOMIC_ACQUIRE);
    if (identity != (((u64)epoch << 32) | actual_uid) ||
        msu_manager_owner_epoch(owner) != epoch ||
        __kuid_val(msu_dynamic_current_uid()) != actual_uid) return false;
    *out_epoch = epoch;
    return true;
}

int msu_manager_owner_appid(const struct msu_manager_owner *owner, u32 *appid)
{
    const struct cred *cred;
    struct msu_manager_owner_binding *entry;
    u64 identity;
    u32 epoch, selected = ~0U, uid;
    if (!owner || !appid) return -EINVAL;
    if (!msu_runtime_layout_ready()) return -EAGAIN;
    cred = msu_dynamic_current_cred();
    if (!cred || cred != msu_dynamic_current_real_cred() ||
        MSU_DYN_FIELD(cred, cred, user_ns) != &init_user_ns ||
        __kuid_val(MSU_DYN_FIELD(cred, cred, uid)) ||
        __kuid_val(MSU_DYN_FIELD(cred, cred, euid))) return -EPERM;
    epoch = msu_manager_owner_epoch(owner);
    if (!epoch) return -ESTALE;
    for (entry = __atomic_load_n(&owner->bindings, __ATOMIC_ACQUIRE); entry; entry = entry->next) {
        identity = __atomic_load_n(&entry->identity, __ATOMIC_ACQUIRE);
        uid = (u32)identity;
        if ((u32)(identity >> 32) != epoch) continue;
        if (!msu_owner_uid_valid(uid) || uid / 100000U != entry->user) return -EUCLEAN;
        if (selected != ~0U && selected != uid % 100000U) return -EUCLEAN;
        selected = uid % 100000U;
    }
    if (msu_manager_owner_epoch(owner) != epoch || msu_dynamic_current_cred() != cred)
        return -EAGAIN;
    *appid = selected;
    return 0;
}

u32 msu_manager_owner_invalidate(struct msu_manager_owner *owner)
{
    u32 epoch = msu_manager_owner_epoch(owner);
    while (epoch) {
        const u32 next = epoch == ~(u32)0 ? 0 : epoch + 1;
        if (__atomic_compare_exchange_n(&owner->epoch, &epoch, next, false,
                __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE)) {
            msu_management_owner_invalidated(owner);
            return next;
        }
    }
    return 0;
}
