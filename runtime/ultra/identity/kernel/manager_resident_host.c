// SPDX-License-Identifier: GPL-3.0-only
#include <linux/cred.h>
#include <linux/errno.h>
#include <linux/irqflags.h>
#include <linux/mutex.h>
#include <linux/slab.h>
#include <linux/workqueue.h>
#include <linux/preempt.h>
#include <linux/random.h>
#include <linux/string.h>
#include <linux/uaccess.h>
#include <linux/user_namespace.h>
#include "manager_claim_entry.h"
#include "manager_management.h"
#include "manager_early_fixed.h"
#include "manager_owner_claim.h"
#include "manager_resident_host.h"
#include "manager_su_runtime_factory.h"
#include "manager_ksu_compat.h"
#include "manager_installation_state.h"
#include "request_protocol.h"
#include "manager_dynamic_layout.h"
#ifdef MSU_RAW_RESIDENT_LOOKUP
#include "manager_early_runtime_factory.h"
#include "native_lookup.h"
#include "su_nonroot_transition_work.h"
#include "su_exec_session.h"
#include "boot_events.h"
static bool msu_resident_raw_targets_bound;
static bool msu_resident_boot_events_stopped;
#endif

const u32 msu_manager_deployment_host_facts[] = {
    2, sizeof(struct msu_resident_config_v2), sizeof(struct msu_deployment_namespace_record),
};

static struct {
    struct msu_manager_owner owner;
    struct msu_manager_claim_boot_policy legacy;
    maplesu_metadata_owner_v3 metadata;
    maplesu_metadata_owner_v3 extended_metadata;
    maplesu_metadata_owner_v3 uid_metadata;
    maplesu_metadata_owner_v3 procattr_metadata;
    maplesu_metadata_owner_v3 task_metadata;
    maplesu_metadata_owner_v3 helper_metadata;
    struct msu_deployment_namespace_record deployment;
    int deployment_available;
    int initialized;
} host;

static bool msu_resident_sleepable(void)
{
    return in_task() && !in_atomic() && !irqs_disabled();
}

/* Retirement work lives outside the resident mapping. The Image callback
 * alone frees that mapping after the complete staged discard returns zero. */
struct msu_resident_retirement_work {
    struct msu_resident_retirement job;
    struct delayed_work work;
};
_Static_assert(offsetof(struct msu_resident_retirement_work, work) ==
    sizeof(struct msu_resident_retirement), "native retirement work offset");
_Static_assert(offsetof(struct delayed_work, work) == 0, "delayed work callback address");
static DEFINE_MUTEX(msu_resident_discard_mutex);
static unsigned int msu_resident_discard_stage;

static int msu_resident_retirement_prepare(const struct msu_resident_config_v3 *config)
{
    struct msu_resident_retirement_work *retire;
    if (!config->anchor || !config->slot || !config->memory || !config->allocation_bytes ||
        !config->retire_work || config->slot->reserved[0]) return -EINVAL;
    retire = kzalloc(sizeof(*retire), GFP_KERNEL);
    if (!retire) return -ENOMEM;
    retire->job.anchor = config->anchor;
    retire->job.slot = config->slot;
    retire->job.memory = config->memory;
    retire->job.allocation_bytes = config->allocation_bytes;
    retire->job.delay = msecs_to_jiffies(100);
    retire->job.max_delay = msecs_to_jiffies(30000);
    retire->job.cpu = WORK_CPU_UNBOUND;
    retire->job.wq = system_wq;
    retire->job.queue = queue_delayed_work_on;
    retire->job.free = kfree;
    retire->job.discard = msu_manager_resident_discard;
    retire->job.caller_active = 1;
    INIT_DELAYED_WORK(&retire->work, config->retire_work);
    config->slot->reserved[0] = (msu_loader_uptr)retire;
    return 0;
}

int msu_manager_resident_init(const struct msu_resident_config *config)
{
    maplesu_metadata_v2 *common = &host.metadata.common;
    unsigned long descriptor = (unsigned long)&host.metadata;
    const struct msu_deployment_namespace_record *deployment = NULL;
    const void *image, *append;
    const char *credential;
    u32 append_bytes;
    int error;
    if (!msu_runtime_layout_ready()) return -EPROTO;
    if (!msu_resident_sleepable()) return -EWOULDBLOCK;
    if (!config || host.initialized) return -EINVAL;
    if (config->version == 1) {
        image = config->image;
        append = config->append;
        append_bytes = config->append_bytes;
        credential = config->credential;
    } else if (config->version == 2 || config->version == 3) {
        const struct msu_resident_config_v2 *config2 = (const void *)config;
        if (config2->config_bytes != (config->version == 3 ?
                sizeof(struct msu_resident_config_v3) : sizeof(*config2))) return -EINVAL;
        if (config2->namespace_record_bytes || config2->namespace_record || config->version == 2) {
            if (config2->namespace_record_bytes != sizeof(*deployment) ||
                !msu_deployment_namespace_valid(config2->namespace_record)) return -EINVAL;
        }
        image = config2->image;
        append = config2->append;
        append_bytes = config2->append_bytes;
        credential = config2->credential;
        deployment = config2->namespace_record;
    } else return -EINVAL;
    if (!image || !append || !credential ||
        (append_bytes != MAPLESU_LEGACY_METADATA_V2_BYTES &&
         append_bytes != MAPLESU_LEGACY_METADATA_AUDIT_BYTES &&
         append_bytes != MAPLESU_APPEND64_BYTES)) return -EINVAL;
    if (append_bytes == MAPLESU_APPEND64_BYTES) {
        u32 magic, sentinel;
        u16 length;
        s64 displacement;
        memcpy(&sentinel, append, sizeof(sentinel));
        memcpy(&magic, (const u8 *)append + MAPLESU_APPEND64_HEADER, sizeof(magic));
        memcpy(&length, (const u8 *)append + MAPLESU_APPEND64_HEADER + 4, sizeof(length));
        memcpy(&displacement, (const u8 *)append + MAPLESU_APPEND64_BASE, sizeof(displacement));
        if (sentinel || magic != MAPLESU_APPEND64_MAGIC || length != append_bytes ||
            (u64)displacement != (u64)(unsigned long)append - (u64)(unsigned long)image)
            return -EPROTO;
    }
    /* Direct loader v1/v2 callers may reuse this mapping after a fully
     * successful discard. Never reopen a partly retired generation. */
    if (!mutex_trylock(&msu_resident_discard_mutex)) return -EINPROGRESS;
    if (msu_resident_discard_stage && msu_resident_discard_stage != 5) {
        mutex_unlock(&msu_resident_discard_mutex);
        return -EINPROGRESS;
    }
    msu_resident_discard_stage = 0;
    mutex_unlock(&msu_resident_discard_mutex);
    if (config->version == 3) {
        const struct msu_resident_config_v3 *config3 = (const void *)config;
        error = msu_resident_retirement_prepare(config3);
        if (error) return error;
#ifdef MSU_RAW_RESIDENT_LOOKUP
        error = msu_su_nonroot_work_bind_boot_resident(config3->slot,
            config3->memory, config3->allocation_bytes);
        if (error) return error;
        error = msu_su_exec_session_bind_boot_resident(config3->slot,
            config3->memory, config3->allocation_bytes);
        if (error) return error;
        error = msu_ksu_compat_bind_boot_resident(config3->slot,
            config3->memory, config3->allocation_bytes);
        if (error) return error;
        error = msu_boot_events_bind_boot_resident(config3->slot,
            config3->memory, config3->allocation_bytes);
        if (error) return error;
#endif
    }
    error = msu_manager_owner_init(&host.owner);
    if (error) return error;
    get_random_bytes(&host.metadata.host, sizeof(host.metadata.host));
    if (!(host.metadata.host.low | host.metadata.host.high)) return -EIO;
    error = msu_management_init(&host.owner, &host.metadata.host);
    if (error) return error;
    common->magic = MAPLESU_METADATA_MAGIC;
    common->version = MAPLESU_OWNER_METADATA_VERSION;
    common->length = sizeof(host.metadata);
    common->capabilities = MAPLESU_OWNER_BASE_CAPABILITIES |
        (append_bytes == MAPLESU_LEGACY_METADATA_AUDIT_BYTES ? MAPLESU_CAP_APPEND_AUDIT : 0);
    if (append_bytes == MAPLESU_APPEND64_BYTES) {
        u32 audit;
        memcpy(&audit, (const u8 *)append + 54, sizeof(audit));
        common->capabilities |= MAPLESU_CAP_APPEND64 | (audit ? MAPLESU_CAP_APPEND_AUDIT : 0);
    }
    common->kernel_base_displacement = (unsigned long)image - descriptor;
    common->legacy_metadata_displacement = (unsigned long)append - descriptor;
    host.metadata.owner_schema = MAPLESU_OWNER_SCHEMA;
    host.metadata.owner_state = MAPLESU_OWNER_STATE_BOUND;
    host.extended_metadata = host.metadata;
    descriptor = (unsigned long)&host.extended_metadata;
    common = &host.extended_metadata.common;
    common->capabilities |= msu_management_capabilities() &
        ~(MAPLESU_CAP_UID_SNAPSHOT | MAPLESU_CAP_PROCATTR | MAPLESU_CAP_TASK_STATE);
    common->kernel_base_displacement = (unsigned long)image - descriptor;
    common->legacy_metadata_displacement = (unsigned long)append - descriptor;
    host.uid_metadata = host.extended_metadata;
    descriptor = (unsigned long)&host.uid_metadata;
    common = &host.uid_metadata.common;
    common->capabilities |= msu_management_capabilities() & MAPLESU_CAP_UID_SNAPSHOT;
    common->kernel_base_displacement = (unsigned long)image - descriptor;
    common->legacy_metadata_displacement = (unsigned long)append - descriptor;
    host.procattr_metadata = host.uid_metadata;
    descriptor = (unsigned long)&host.procattr_metadata;
    common = &host.procattr_metadata.common;
    common->capabilities |= msu_management_capabilities() & MAPLESU_CAP_PROCATTR;
    common->kernel_base_displacement = (unsigned long)image - descriptor;
    common->legacy_metadata_displacement = (unsigned long)append - descriptor;
    host.task_metadata = host.procattr_metadata;
    descriptor = (unsigned long)&host.task_metadata;
    common = &host.task_metadata.common;
    common->capabilities |= msu_management_capabilities() & MAPLESU_CAP_TASK_STATE;
    common->kernel_base_displacement = (unsigned long)image - descriptor;
    common->legacy_metadata_displacement = (unsigned long)append - descriptor;
    host.helper_metadata = host.metadata;
    descriptor = (unsigned long)&host.helper_metadata;
    common = &host.helper_metadata.common;
    common->capabilities = MAPLESU_HELPER_BASE_CAPABILITIES |
        (host.metadata.common.capabilities & (MAPLESU_CAP_APPEND64 | MAPLESU_CAP_APPEND_AUDIT)) |
        (msu_management_capabilities() & MAPLESU_HELPER_OPTIONAL_CAPABILITIES);
    common->kernel_base_displacement = (unsigned long)image - descriptor;
    common->legacy_metadata_displacement = (unsigned long)append - descriptor;
    host.helper_metadata.owner_state = MAPLESU_OWNER_STATE_HELPER;
    host.legacy.version = MSU_MANAGER_CLAIM_BOOT_VERSION;
    host.legacy.credential = credential;
    if (deployment) {
        host.deployment = *deployment;
        host.deployment_available = 1;
    }
    if (deployment) {
#ifdef MSU_RAW_RESIDENT_LOOKUP
        /* The raw carrier pairs the real native lookup TU with this host.
         * Bind only once per materialized image; successful v1/v2 discard may
         * reuse that same image without reinitializing private target tables. */
        if (!msu_resident_raw_targets_bound) {
            error = msu_init_bridge_lookup_init();
            if (error && error != -EALREADY) return error;
            error = msu_su_runtime_target_bind(msu_init_bridge_lookup, msu_init_bridge_resolve_cfi);
            if (error) return error;
            error = msu_early_runtime_target_bind(msu_init_bridge_lookup, msu_init_bridge_resolve_cfi);
            if (error) return error;
            msu_resident_raw_targets_bound = true;
        }
#endif
        error = msu_installation_init(&host.metadata.host, deployment->name);
        if (error) return error;
        error = msu_su_runtime_init(&host.metadata.host, deployment);
        if (error) return error;
#ifdef MSU_RAW_RESIDENT_LOOKUP
        /* kernel_init_freeable has returned in PID1; userspace init has not
         * executed or read init.rc. Preserve its actual stage ordering. */
        msu_resident_boot_events_stopped = false;
        error = msu_boot_events_start(deployment);
        if (error) return error;
#endif
    }
    smp_store_release(&host.initialized, 1);
    return 0;
}

/* Kernel-only readback; the authenticated gateway owns user delivery. This
 * immutable path fact is not installation or vendor-completion authority. */
int msu_manager_resident_deployment(struct msu_deployment_namespace_record *output)
{
    if (!output) return -EINVAL;
    memset(output, 0, sizeof(*output));
    if (!smp_load_acquire(&host.initialized) || !host.deployment_available) return -EOPNOTSUPP;
    *output = host.deployment;
    return 0;
}
int msu_manager_resident_ksu_appid(u32 *appid)
{
    if (!appid) return -EINVAL;
    if (!smp_load_acquire(&host.initialized)) return -EAGAIN;
    return msu_manager_owner_appid(&host.owner, appid);
}

/* Only the authenticated helper session supplies principal/epoch. The template
 * address anchors signed displacements; it never grants arbitrary kernel READ. */
int msu_manager_resident_helper_metadata(u32 principal_uid, u32 epoch,
    maplesu_metadata_owner_v3 *output, u64 *descriptor)
{
    if (!output || !descriptor) return -EINVAL;
    memset(output, 0, sizeof(*output));
    *descriptor = 0;
    if (!smp_load_acquire(&host.initialized) || !host.deployment_available || !epoch)
        return -EOPNOTSUPP;
    *output = host.helper_metadata;
    output->owner_full_uid = principal_uid;
    output->owner_epoch = epoch;
    *descriptor = (unsigned long)&host.helper_metadata;
    return 0;
}

int msu_manager_resident_discard(void)
{
    int error = 0;
    if (!msu_runtime_layout_ready()) return -EPROTO;
    if (!msu_resident_sleepable()) return -EWOULDBLOCK;
    if (!mutex_trylock(&msu_resident_discard_mutex)) return -EINPROGRESS;
#ifdef MSU_RAW_RESIDENT_LOOKUP
    if (!msu_resident_boot_events_stopped) {
        error = msu_boot_events_stop();
        if (error) goto out;
        msu_resident_boot_events_stopped = true;
    }
#endif
    /* Every successful stage is consumed once. A pending later owner never
     * re-enters an already-discarded runtime or replays prior cleanup effects. */
    if (!msu_resident_discard_stage) {
        error = msu_su_runtime_close();
        if (error) goto out;
        ++msu_resident_discard_stage;
    }
    if (msu_resident_discard_stage == 1) {
        error = msu_management_discard_unpublished();
        if (error) goto out;
        ++msu_resident_discard_stage;
    }
    if (msu_resident_discard_stage == 2) {
        error = msu_su_runtime_discarded();
        if (error) goto out;
        ++msu_resident_discard_stage;
    }
    if (msu_resident_discard_stage == 3) {
        error = msu_installation_discard_unpublished();
        if (error) goto out;
        ++msu_resident_discard_stage;
    }
    if (msu_resident_discard_stage == 4) {
        error = msu_manager_owner_discard_unpublished(&host.owner);
        if (error) goto out;
        memset(&host, 0, sizeof(host));
        ++msu_resident_discard_stage;
    }
out:
    mutex_unlock(&msu_resident_discard_mutex);
    return error;
}

static bool msu_owner_snapshot(u32 uid, u32 epoch)
{
    u32 actual_uid = 0, actual_epoch = 0;
    return !msu_management_authorize(&actual_uid, &actual_epoch) &&
        actual_uid == uid && actual_epoch == epoch;
}

static void msu_response_clear_output(maplesu_response_owner_v3 *response, bool management)
{
    if (!management) response->result = response->metadata_address = response->metadata_length = 0;
    response->metadata_version = 0;
}

static bool msu_host_matches(maplesu_host_instance value)
{
    return value.low == host.metadata.host.low && value.high == host.metadata.host.high;
}

static bool msu_user_range(u64 address, u32 bytes)
{
    return address && bytes && address <= ~0ULL - (bytes - 1) &&
        !(address & (1ULL << 55));
}

static int msu_owner_request(unsigned long user)
{
    maplesu_request_owner_v3 request;
    maplesu_response_owner_v3 response = {0};
    maplesu_read_owner_v3 read;
    maplesu_management_v3 operation = {0};
    maplesu_host_instance instance;
    maplesu_metadata_owner_v3 metadata;
    unsigned char bytes[MAPLESU_READ_MAX_BYTES];
    const u64 base_descriptor = (unsigned long)&host.metadata;
    const u64 extended_descriptor = (unsigned long)&host.extended_metadata;
    const u64 uid_descriptor = (unsigned long)&host.uid_metadata;
    const u64 procattr_descriptor = (unsigned long)&host.procattr_metadata;
    const u64 task_descriptor = (unsigned long)&host.task_metadata;
    const u64 helper_descriptor = (unsigned long)&host.helper_metadata;
    u64 descriptor = base_descriptor;
    const u32 caller_uid = __kuid_val(msu_dynamic_current_uid());
    const u32 completion = MAPLESU_RESPONSE_MAGIC;
    u32 uid = 0, epoch = 0;
    int error;
    bool bootstrap, management = false, closing = false, vendor = false, helper = false;
    if (!msu_user_range(user, sizeof(request)) ||
        copy_from_user_nofault(&request, (void __user *)user, sizeof(request))) return -EFAULT;
    if (!msu_user_range(request.response_user, sizeof(response))) {
        msu_management_delivery_failed(0);
        return -EFAULT;
    }
    response.version = MAPLESU_OWNER_PROTOCOL_VERSION;
    response.length = sizeof(response);
    response.command = request.command;
    response.request_id = request.request_id;
    response.status = MAPLESU_REQUEST_STATUS_INVALID;
    if (request.magic != MAPLESU_REQUEST_MAGIC || request.version != MAPLESU_OWNER_PROTOCOL_VERSION ||
        request.length != sizeof(request)) goto respond;
    switch (request.command) {
    case MAPLESU_REQUEST_VENDOR_STATE:
        /* The same frame/selector, authenticated by exact installed executable
         * rather than Manager mutation lifetime. No implicit Manager claim. */
        vendor = management = true;
        if (msu_dynamic_current_user_ns() != &init_user_ns || caller_uid ||
            __kuid_val(msu_dynamic_current_euid())) {
            response.status = -EPERM;
            goto respond;
        }
        if (request.arg0 || request.expected_epoch ||
            !msu_user_range(request.arg1, sizeof(operation)) ||
            copy_from_user_nofault(&operation, (void __user *)(unsigned long)request.arg1,
                sizeof(operation)) || operation.reserved || operation.flags || operation.session ||
            operation.length != sizeof(maplesu_installation_state_v3) ||
            !msu_user_range(operation.data_user, operation.length)) goto respond;
        if ((operation.address == MAPLESU_INSTALLATION_READ ||
             operation.address == MAPLESU_VENDOR_BEGIN) ? operation.handle != 0 :
            ((operation.address != MAPLESU_VENDOR_BEFORE &&
              operation.address != MAPLESU_VENDOR_COMPLETE) || operation.handle >= 3)) goto respond;
        {
            maplesu_installation_state_v3 state;
            if (copy_from_user_nofault(&state, (void __user *)(unsigned long)operation.data_user,
                sizeof(state))) goto fault;
            if (operation.address != MAPLESU_INSTALLATION_READ &&
                (state.host.low != operation.host.low || state.host.high != operation.host.high)) goto respond;
            response.status = msu_installation_vendor(operation.address, operation.handle,
                &state, &response.metadata_length);
            if (!response.status) {
                if (operation.address == MAPLESU_INSTALLATION_READ) {
                    if (copy_to_user_nofault((void __user *)(unsigned long)operation.data_user,
                        &state, sizeof(state))) goto fault;
                    response.result = sizeof(state);
                } else response.result = state.revision;
            }
        }
        goto respond;
    case MAPLESU_REQUEST_QUERY:
        if (request.expected_epoch || request.arg1 || (request.arg0 &&
            request.arg0 != MAPLESU_OWNER_OPTIONAL_CAPABILITIES &&
            request.arg0 != MAPLESU_OWNER_UID_QUERY &&
            request.arg0 != MAPLESU_OWNER_PROCATTR_QUERY &&
            request.arg0 != MAPLESU_OWNER_TASK_QUERY)) goto respond;
        break;
    case MAPLESU_REQUEST_ECHO64:
    case MAPLESU_REQUEST_READ:
        break;
    case MAPLESU_REQUEST_SESSION_OPEN:
    case MAPLESU_REQUEST_SESSION_CLOSE:
    case MAPLESU_REQUEST_ALLOC:
    case MAPLESU_REQUEST_WRITE:
    case MAPLESU_REQUEST_EXEC:
    case MAPLESU_REQUEST_RELEASE:
    case MAPLESU_REQUEST_CRED_ROOT:
    case MAPLESU_REQUEST_CRED_CAPS:
    case MAPLESU_REQUEST_PUBLICATION_PREPARE:
    case MAPLESU_REQUEST_PUBLICATION_PUBLISH:
    case MAPLESU_REQUEST_PUBLICATION_DISABLE:
    case MAPLESU_REQUEST_PUBLICATION_RETIRE:
    case MAPLESU_REQUEST_SHARED_BOOL_CREATE:
    case MAPLESU_REQUEST_SHARED_BOOL_STORE:
    case MAPLESU_REQUEST_SHARED_BOOL_RELEASE:
    case MAPLESU_REQUEST_UID_CREATE:
    case MAPLESU_REQUEST_UID_PREPARE:
    case MAPLESU_REQUEST_UID_COMMIT:
    case MAPLESU_REQUEST_UID_CANCEL:
    case MAPLESU_REQUEST_UID_READBACK:
    case MAPLESU_REQUEST_UID_SHUTDOWN:
    case MAPLESU_REQUEST_CRED_PROCATTR:
    case MAPLESU_REQUEST_TASK_STATE:
    case MAPLESU_REQUEST_DEPLOYMENT_READ:
    case MAPLESU_REQUEST_INSTALLATION_STATE:
    case MAPLESU_REQUEST_SU_RUNTIME_READ:
    case MAPLESU_REQUEST_EARLY_FIXED:
        management = true;
        /* Authenticate restricted operations before touching their user body.
         * Helper OPEN authenticates its exact installed executable below, after
         * this root-only admission. CLOSE retains the existing task-owned drain
         * path, including revoked owners and a lost OPEN completion. */
        if (request.command == MAPLESU_REQUEST_SESSION_OPEN && !request.expected_epoch) {
            if (msu_dynamic_current_user_ns() != &init_user_ns || caller_uid ||
                __kuid_val(msu_dynamic_current_euid())) {
                response.status = -EPERM;
                goto respond;
            }
        } else if (request.command != MAPLESU_REQUEST_SESSION_CLOSE) {
            error = msu_management_authorize(&uid, &epoch);
            if (error) { response.status = error; goto respond; }
        }
        if (request.arg0) goto respond;
        if (!msu_user_range(request.arg1, sizeof(operation)) ||
            copy_from_user_nofault(&operation, (void __user *)(unsigned long)request.arg1,
                sizeof(operation))) goto fault;
        if (msu_management_helper_open_shape(&request, &operation)) {
            msu_management_dispatch(&request, &operation, &response);
            if (!response.status) {
                error = msu_management_authorize(&uid, &epoch);
                if (error || !msu_management_current_helper()) {
                    response.status = error ? error : -EPERM;
                    msu_management_delivery_failed(0);
                    response.metadata_length |= MAPLESU_EFFECT_SESSION_CLOSED;
                }
            }
            goto respond;
        }
        if (msu_management_helper_close_shape(&request, &operation)) {
            closing = true;
            msu_management_dispatch(&request, &operation, &response);
            goto respond;
        }
        if (!request.expected_epoch) goto respond;
        if ((operation.reserved && request.command != MAPLESU_REQUEST_EARLY_FIXED) ||
            !msu_host_matches(operation.host) ||
            (operation.flags && !((request.command == MAPLESU_REQUEST_WRITE &&
                operation.flags == MAPLESU_MANAGEMENT_WRITE_TEXT) ||
                (request.command == MAPLESU_REQUEST_ALLOC && operation.flags == MAPLESU_MANAGEMENT_ALLOC_CODE) ||
                (request.command == MAPLESU_REQUEST_EARLY_FIXED &&
                 operation.flags <= MAPLESU_EARLY_FIXED_EXPLOIT_MAX)))) goto respond;
        if (request.command == MAPLESU_REQUEST_SESSION_CLOSE) {
            closing = true;
            msu_management_dispatch(&request, &operation, &response);
            goto respond;
        }
        break;
    default:
        response.status = MAPLESU_REQUEST_STATUS_UNSUPPORTED;
        goto respond;
    }
    error = msu_management_authorize(&uid, &epoch);
    if (request.command == MAPLESU_REQUEST_QUERY && error == -EPERM &&
        caller_uid <= 2147483647U && caller_uid % 100000U >= 10000U &&
        caller_uid % 100000U <= 19999U) {
        error = msu_manager_owner_claim_entry(&host.owner, &epoch);
        if (!error) error = msu_management_authorize(&uid, &epoch);
    }
    if (error) { response.status = error; goto respond; }
    helper = msu_management_current_helper();
    if (helper && request.command == MAPLESU_REQUEST_QUERY) {
        response.status = -EPERM;
        goto respond;
    }
    if (management) {
        if (request.expected_epoch != epoch) goto respond;
        msu_management_dispatch(&request, &operation, &response);
        goto respond;
    }
    switch (request.command) {
    case MAPLESU_REQUEST_QUERY:
        if (request.arg0 == MAPLESU_OWNER_TASK_QUERY) {
            descriptor = task_descriptor;
            response.result = host.task_metadata.common.capabilities;
        } else if (request.arg0 == MAPLESU_OWNER_PROCATTR_QUERY) {
            descriptor = procattr_descriptor;
            response.result = host.procattr_metadata.common.capabilities;
        } else if (request.arg0 == MAPLESU_OWNER_UID_QUERY) {
            descriptor = uid_descriptor;
            response.result = host.uid_metadata.common.capabilities;
        } else {
            if (request.arg0) descriptor = extended_descriptor;
            response.result = request.arg0 ? host.extended_metadata.common.capabilities : host.metadata.common.capabilities;
        }
        response.metadata_address = descriptor;
        response.metadata_length = sizeof(metadata);
        response.metadata_version = MAPLESU_OWNER_METADATA_VERSION;
        break;
    case MAPLESU_REQUEST_ECHO64:
        if (!msu_user_range(request.arg1, sizeof(instance)) ||
            copy_from_user_nofault(&instance, (void __user *)(unsigned long)request.arg1, sizeof(instance)))
            goto fault;
        if (request.expected_epoch != epoch || !msu_host_matches(instance)) goto respond;
        response.result = request.arg0;
        break;
    case MAPLESU_REQUEST_READ:
        if (!msu_user_range(request.arg1, sizeof(read)) ||
            copy_from_user_nofault(&read, (void __user *)(unsigned long)request.arg1, sizeof(read))) goto fault;
        if (read.reserved || !read.length || read.length > sizeof(bytes) ||
            request.arg0 < 0xffff000000000000ULL || request.arg0 > ~0ULL - (read.length - 1) ||
            !msu_user_range(read.destination_user, read.length)) goto respond;
        if (helper) {
            u64 helper_address = 0;
            if (request.arg0 != helper_descriptor || read.length != sizeof(metadata) ||
                request.expected_epoch != epoch || !msu_host_matches(read.host)) goto respond;
            error = msu_manager_resident_helper_metadata(uid, epoch, &metadata, &helper_address);
            if (error) { response.status = error; goto respond; }
            if (helper_address != request.arg0 || !msu_owner_snapshot(uid, epoch)) goto respond;
            if (copy_to_user_nofault((void __user *)(unsigned long)read.destination_user,
                    &metadata, sizeof(metadata))) goto fault;
            if (!msu_owner_snapshot(uid, epoch)) goto respond;
            response.result = sizeof(metadata);
            break;
        }
        if (request.arg0 == extended_descriptor) descriptor = extended_descriptor;
        if (request.arg0 == uid_descriptor) descriptor = uid_descriptor;
        if (request.arg0 == procattr_descriptor) descriptor = procattr_descriptor;
        if (request.arg0 == task_descriptor) descriptor = task_descriptor;
        bootstrap = !request.expected_epoch && !(read.host.low | read.host.high);
        if (bootstrap ? request.arg0 != descriptor || read.length != sizeof(metadata) :
            request.expected_epoch != epoch || !msu_host_matches(read.host)) goto respond;
        if ((request.arg0 < base_descriptor + sizeof(metadata) && base_descriptor < request.arg0 + read.length) ||
            (request.arg0 < extended_descriptor + sizeof(metadata) && extended_descriptor < request.arg0 + read.length) ||
            (request.arg0 < uid_descriptor + sizeof(metadata) && uid_descriptor < request.arg0 + read.length) ||
            (request.arg0 < procattr_descriptor + sizeof(metadata) && procattr_descriptor < request.arg0 + read.length) ||
            (request.arg0 < task_descriptor + sizeof(metadata) && task_descriptor < request.arg0 + read.length)) {
            if (request.arg0 != descriptor || read.length != sizeof(metadata)) goto respond;
            metadata = descriptor == task_descriptor ? host.task_metadata :
                (descriptor == procattr_descriptor ? host.procattr_metadata :
                (descriptor == uid_descriptor ? host.uid_metadata :
                (descriptor == extended_descriptor ? host.extended_metadata : host.metadata)));
            metadata.owner_epoch = epoch;
            metadata.owner_full_uid = uid;
            memcpy(bytes, &metadata, sizeof(metadata));
        } else if (copy_from_kernel_nofault(bytes, (void *)(unsigned long)request.arg0, read.length)) goto fault;
        if (!msu_owner_snapshot(uid, epoch)) goto respond;
        if (copy_to_user_nofault((void __user *)(unsigned long)read.destination_user, bytes, read.length)) goto fault;
        if (!msu_owner_snapshot(uid, epoch)) goto respond;
        response.result = read.length;
        break;
    }
    if (!msu_owner_snapshot(uid, epoch)) goto respond;
    response.status = MAPLESU_REQUEST_STATUS_OK;
    goto respond;
fault:
    response.status = MAPLESU_REQUEST_STATUS_FAULT;
respond:
    if (response.status == MAPLESU_REQUEST_STATUS_OK && !closing && !vendor && !msu_owner_snapshot(uid, epoch)) {
        response.status = MAPLESU_REQUEST_STATUS_INVALID;
        if (management) {
            msu_management_delivery_failed(0);
            response.metadata_length |= MAPLESU_EFFECT_SESSION_CLOSED;
        }
    }
    if (response.status != MAPLESU_REQUEST_STATUS_OK) msu_response_clear_output(&response, management);
    if (response.status && request.command == MAPLESU_REQUEST_SESSION_OPEN &&
        !request.expected_epoch) response.metadata_address = 0;
    if (copy_to_user_nofault((void __user *)(unsigned long)request.response_user, &response, sizeof(response))) {
        msu_management_delivery_failed(0);
        return -EFAULT;
    }
    if (response.status == MAPLESU_REQUEST_STATUS_OK && !closing && !vendor && !msu_owner_snapshot(uid, epoch)) {
        response.status = MAPLESU_REQUEST_STATUS_INVALID;
        if (management) {
            msu_management_delivery_failed(0);
            response.metadata_length |= MAPLESU_EFFECT_SESSION_CLOSED;
        }
        msu_response_clear_output(&response, management);
        if (request.command == MAPLESU_REQUEST_SESSION_OPEN && !request.expected_epoch)
            response.metadata_address = 0;
        if (copy_to_user_nofault((void __user *)(unsigned long)request.response_user, &response, sizeof(response))) {
            msu_management_delivery_failed(0);
            return -EFAULT;
        }
    }
    __atomic_thread_fence(__ATOMIC_RELEASE);
    if (copy_to_user_nofault((void __user *)(unsigned long)request.response_user,
        &completion, sizeof(completion))) {
        msu_management_delivery_failed(0);
        return -EFAULT;
    }
    return 1;
}

int msu_manager_resident_dispatch(const char *name)
{
    unsigned char frame[18];
    unsigned long request = 0;
    unsigned int i;
    if (!msu_runtime_layout_ready()) return -EPROTO;
    if (!msu_resident_sleepable()) return -EWOULDBLOCK;
    if (!host.initialized || !name) return 0;
    if (strncmp(name, MAPLESU_OWNER_ENVELOPE_PREFIX, MAPLESU_OWNER_ENVELOPE_PREFIX_BYTES))
        return msu_manager_claim_entry(&host.legacy, name);
    if (strnlen(name, 86) != 84) return -EINVAL;
    for (i = 0; i < sizeof(frame); ++i)
        frame[i] = (unsigned char)name[66 + i] + (unsigned char)name[48 + i];
    if (frame[0] != MAPLESU_REQUEST_SELECTOR_FLAG || frame[1] != MAPLESU_REQUEST_SELECTOR_SUB)
        return -EINVAL;
    for (i = 2; i < 10; ++i) if (frame[i]) return -EINVAL;
    for (i = 0; i < 8; ++i) request |= (unsigned long)frame[10 + i] << (8 * i);
    return msu_owner_request(request);
}
