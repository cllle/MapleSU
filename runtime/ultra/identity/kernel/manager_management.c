// SPDX-License-Identifier: GPL-3.0-only
#include <linux/binfmts.h>
#include <linux/capability.h>
#include <linux/compiler.h>
#include <linux/cpu.h>
#include <linux/cred.h>
#include <linux/dcache.h>
#include <linux/err.h>
#include <linux/errno.h>
#include <linux/fcntl.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/irqflags.h>
#include <linux/list.h>
#include <linux/kernel.h>
#include <linux/magic.h>
#include <linux/memory.h>
#include <linux/mm.h>
#include <linux/mutex.h>
#include <linux/module.h>
#include <linux/preempt.h>
#include <linux/rcupdate.h>
#include <linux/sched.h>
#include <linux/sched/mm.h>
#include <linux/sched/task.h>
#include <linux/sched/user.h>
#include <linux/slab.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <linux/tracepoint.h>
#include <linux/uaccess.h>
#include <linux/user_namespace.h>
#include <linux/version.h>
#include <linux/vmalloc.h>
#include <linux/workqueue.h>
#include <linux/wait.h>
#if __has_include(<linux/execmem.h>)
#include <linux/execmem.h>
#define MSU_MANAGEMENT_EXECMEM 1
#else
#include <linux/moduleloader.h>
#endif
#include <asm/cacheflush.h>
#if __has_include(<asm/text-patching.h>)
#include <asm/text-patching.h>
#elif __has_include(<asm/patching.h>)
#include <asm/patching.h>
#else
#include <asm/insn.h>
#endif
#include <asm/set_memory.h>
#include <trace/events/sched.h>
#include "execution_frame.h"
#include "manager_management.h"
#include "su_exec_session.h"
#include "manager_resident_host.h"
#include "manager_installation_state.h"
#include "manager_early_helper.h"
#include "manager_early_runtime_factory.h"
#include "manager_su_runtime_factory.h"
#include "manager_credential_profile.h"
#include "manager_credential_domain.h"
#include "publication_return_private.h"
#include "legacy_hook_private.h"
#if defined(MSU_MANAGEMENT_TASK_STATE_VERIFIED) && MSU_MANAGEMENT_TASK_STATE_VERIFIED
#include "manager_task_state.h"
#endif
#include "manager_owner.h"
#include "manager_uid_gateway.h"
#include "su_observed_install_source.h"
#if defined(MSU_MANAGEMENT_NATIVE_TEXT_OWNER) && MSU_MANAGEMENT_NATIVE_TEXT_OWNER
#include "native-text/kprobe_text_owner.h"
#endif

#include "manager_dynamic_layout.h"

#if defined(MSU_MANAGEMENT_NATIVE_TEXT_OWNER) && MSU_MANAGEMENT_NATIVE_TEXT_OWNER && \
    defined(MSU_MANAGEMENT_RESIDENT_TEXT_OWNER) && MSU_MANAGEMENT_RESIDENT_TEXT_OWNER
#error "select exactly one native-text or resident Legacy provider"
#endif

#define MSU_MANAGEMENT_SLOTS 64U
#ifndef MSU_MANAGEMENT_CRED_UCOUNTS
#error "management requires target cred field/helper selection"
#endif
_Static_assert(MSU_MANAGEMENT_CRED_UCOUNTS == 0 || MSU_MANAGEMENT_CRED_UCOUNTS == 1,
    "management cred ucounts selection");
#if defined(__GNUC__)
_Static_assert(__builtin_types_compatible_p(typeof(&prepare_creds),
    struct cred *(*)(void)), "management prepare_creds ABI");
_Static_assert(__builtin_types_compatible_p(typeof(&commit_creds),
    int (*)(struct cred *)), "management commit_creds ABI");
_Static_assert(__builtin_types_compatible_p(typeof(&abort_creds),
    void (*)(struct cred *)), "management abort_creds ABI");
#endif
#if MSU_MANAGEMENT_CRED_UCOUNTS
_Static_assert(__builtin_types_compatible_p(typeof(&set_cred_ucounts),
    int (*)(struct cred *)), "management cred ucounts helper type");
#endif
#define MSU_ICACHE_ADDRESS_LENGTH __builtin_types_compatible_p(typeof(&sync_icache_aliases), \
    void (*)(void *, unsigned long))
_Static_assert(MSU_ICACHE_ADDRESS_LENGTH ||
    __builtin_types_compatible_p(typeof(&sync_icache_aliases),
        void (*)(unsigned long, unsigned long)), "management cache helper type");
#if defined(CONFIG_PROC_FS) && defined(CONFIG_SECURITY)
/* The real native function is an identity marker, never a direct call here.
 * P2 must bind its actual target symbol, declaration, fields and CFI. */
extern ssize_t proc_pid_attr_write(struct file *file, const char __user *buffer,
    size_t bytes, loff_t *position);
_Static_assert(__builtin_types_compatible_p(typeof(&proc_pid_attr_write),
    typeof(((struct file_operations *)0)->write)), "native procattr write type");
_Static_assert(__builtin_types_compatible_p(typeof(&vfs_write),
    ssize_t (*)(struct file *, const char __user *, size_t, loff_t *)), "native VFS write type");
_Static_assert(__builtin_types_compatible_p(typeof(&filp_open),
    struct file *(*)(const char *, int, umode_t)), "native open type");
_Static_assert(__builtin_types_compatible_p(typeof(&fput),
    void (*)(struct file *)), "native file release type");
#endif
_Static_assert(CAP_LAST_CAP < 64, "management capability wire is 64 bits");
_Static_assert(__builtin_types_compatible_p(typeof(&aarch64_insn_patch_text),
    int (*)(void **, u32 *, int)), "management text patch requires the target declaration");
#ifdef MSU_MANAGEMENT_EXECMEM
_Static_assert(__builtin_types_compatible_p(typeof(&execmem_alloc),
    void *(*)(enum execmem_type, size_t)), "management execmem allocator type");
_Static_assert(__builtin_types_compatible_p(typeof(&execmem_free),
    void (*)(void *)), "management execmem release type");
#if defined(MSU_MANAGEMENT_EXECMEM_ALLOC_RW) && MSU_MANAGEMENT_EXECMEM_ALLOC_RW
_Static_assert(__builtin_types_compatible_p(typeof(&execmem_alloc_rw),
    void *(*)(enum execmem_type, size_t)), "management writable execmem allocator type");
#endif
#else
_Static_assert(__builtin_types_compatible_p(typeof(&module_alloc),
    void *(*)(unsigned long)), "management module allocator type");
_Static_assert(__builtin_types_compatible_p(typeof(&module_memfree),
    void (*)(void *)), "management module release type");
#endif
#define MSU_LAYOUT_TYPE(type) sizeof(type), __alignof__(type)
#define MSU_LAYOUT_FIELD(type, member) offsetof(type, member), sizeof(((type *)0)->member)
const unsigned int msu_management_compiled_layout[MSU_MANAGEMENT_LAYOUT_WORDS]
    __attribute__((section(".msu.layout"))) = {
    1, LINUX_VERSION_CODE,
    MSU_LAYOUT_TYPE(struct task_struct),
    MSU_LAYOUT_FIELD(struct task_struct, cred),
    MSU_LAYOUT_FIELD(struct task_struct, real_cred),
    MSU_LAYOUT_FIELD(struct task_struct, mm),
    MSU_LAYOUT_FIELD(struct task_struct, self_exec_id),
    MSU_LAYOUT_FIELD(struct task_struct, flags),
    MSU_LAYOUT_FIELD(struct task_struct, usage),
    MSU_LAYOUT_TYPE(struct cred),
    MSU_LAYOUT_FIELD(struct cred, usage),
    MSU_LAYOUT_FIELD(struct cred, non_rcu),
    MSU_LAYOUT_FIELD(struct cred, uid), MSU_LAYOUT_FIELD(struct cred, gid),
    MSU_LAYOUT_FIELD(struct cred, suid), MSU_LAYOUT_FIELD(struct cred, sgid),
    MSU_LAYOUT_FIELD(struct cred, euid), MSU_LAYOUT_FIELD(struct cred, egid),
    MSU_LAYOUT_FIELD(struct cred, fsuid), MSU_LAYOUT_FIELD(struct cred, fsgid),
    MSU_LAYOUT_FIELD(struct cred, securebits),
    MSU_LAYOUT_FIELD(struct cred, cap_inheritable),
    MSU_LAYOUT_FIELD(struct cred, cap_permitted),
    MSU_LAYOUT_FIELD(struct cred, cap_effective),
    MSU_LAYOUT_FIELD(struct cred, cap_bset),
    MSU_LAYOUT_FIELD(struct cred, cap_ambient),
    MSU_LAYOUT_FIELD(struct cred, user),
    MSU_LAYOUT_FIELD(struct cred, user_ns),
#if MSU_MANAGEMENT_CRED_UCOUNTS
    MSU_LAYOUT_FIELD(struct cred, ucounts),
#else
    ~0U, 0,
#endif
    MSU_LAYOUT_FIELD(struct cred, group_info),
    MSU_LAYOUT_TYPE(struct group_info), MSU_LAYOUT_FIELD(struct group_info, usage),
    __builtin_types_compatible_p(typeof(((struct group_info *)0)->usage), refcount_t),
    MSU_LAYOUT_TYPE(struct mm_struct), MSU_LAYOUT_FIELD(struct mm_struct, mm_count),
    MSU_LAYOUT_TYPE(struct mutex), MSU_LAYOUT_TYPE(raw_spinlock_t),
    MSU_LAYOUT_FIELD(raw_spinlock_t, raw_lock),
#ifdef CONFIG_DEBUG_SPINLOCK
    MSU_LAYOUT_FIELD(raw_spinlock_t, magic),
    MSU_LAYOUT_FIELD(raw_spinlock_t, owner_cpu),
    MSU_LAYOUT_FIELD(raw_spinlock_t, owner),
#else
    ~0U, 0, ~0U, 0, ~0U, 0,
#endif
#ifdef CONFIG_DEBUG_LOCK_ALLOC
    MSU_LAYOUT_FIELD(raw_spinlock_t, dep_map),
#else
    ~0U, 0,
#endif
    MSU_LAYOUT_TYPE(struct work_struct),
    MSU_LAYOUT_FIELD(struct work_struct, data),
    MSU_LAYOUT_FIELD(struct work_struct, entry),
    MSU_LAYOUT_FIELD(struct work_struct, func),
#ifdef CONFIG_LOCKDEP
    MSU_LAYOUT_FIELD(struct work_struct, lockdep_map),
#else
    ~0U, 0,
#endif
    MSU_LAYOUT_TYPE(kernel_cap_t), CAP_LAST_CAP, PAGE_SHIFT,
#ifdef CONFIG_TRACEPOINTS
    1,
#else
    0,
#endif
#ifdef CONFIG_PREEMPT_RT
    1,
#else
    0,
#endif
#ifdef CONFIG_MULTIUSER
    1,
#else
    0,
#endif
};
const unsigned int msu_procattr_compiled_layout[MSU_PROCATTR_LAYOUT_WORDS]
    __attribute__((section(".msu.procattr.layout"))) = {
    1, LINUX_VERSION_CODE,
#if defined(CONFIG_PROC_FS) && defined(CONFIG_SECURITY)
    1,
#else
    0,
#endif
    MSU_LAYOUT_TYPE(struct file),
    MSU_LAYOUT_FIELD(struct file, f_op),
    MSU_LAYOUT_FIELD(struct file, f_path),
    MSU_LAYOUT_FIELD(struct file, f_inode),
    MSU_LAYOUT_TYPE(struct path),
    MSU_LAYOUT_FIELD(struct path, dentry),
    MSU_LAYOUT_TYPE(struct file_operations),
    MSU_LAYOUT_FIELD(struct file_operations, write),
    MSU_LAYOUT_TYPE(struct inode),
    MSU_LAYOUT_FIELD(struct inode, i_sb),
    MSU_LAYOUT_TYPE(struct super_block),
    MSU_LAYOUT_FIELD(struct super_block, s_magic),
    MSU_LAYOUT_TYPE(struct dentry),
    MSU_LAYOUT_FIELD(struct dentry, d_parent),
    MSU_LAYOUT_FIELD(struct dentry, d_name),
    MSU_LAYOUT_TYPE(struct qstr),
    MSU_LAYOUT_FIELD(struct qstr, len),
    MSU_LAYOUT_FIELD(struct qstr, name),
    PROC_SUPER_MAGIC,
};
#undef MSU_LAYOUT_FIELD
#undef MSU_LAYOUT_TYPE

struct msu_runtime_service;
struct msu_legacy_gateway {
    u64 base, bytes, id;
};

struct msu_management_session {
    struct mutex operation;
    struct work_struct cleanup;
    struct task_struct *task;
    struct mm_struct *mm;
    const struct cred *original, *active;
    struct msu_installation_helper_lease *helper;
    struct msu_runtime_service *service;
    struct msu_legacy_gateway *legacy_gateway;
    u64 id, exec_id;
    u32 principal_uid, epoch, references;
    bool attached, legacy_active, legacy_opened;
};

struct msu_publication;

struct msu_runtime_service {
    struct mutex operation;
    maplesu_host_instance host;
    const void *lifecycle_owner;
    u64 id, entries[MSU_RUNTIME_SERVICE_MAX_ENTRIES];
    u32 entry_count, operations, helper_sessions;
    enum msu_runtime_service_kind kind;
    bool reserved, active, aborting, discarded, factory_owned, sealed;
};

enum msu_management_backend {
    MSU_MANAGEMENT_VMALLOC,
    MSU_MANAGEMENT_MODULE,
    MSU_MANAGEMENT_EXECMEM_BACKEND,
};

struct msu_management_resource {
    struct msu_management_session *session;
    struct msu_runtime_service *service;
    struct msu_publication *publication;
    void *address;
    u64 handle;
    u32 bytes, uploaded, pages;
    enum msu_management_backend backend;
    bool reserved, consumed, quarantined;
};

struct msu_shared_bool {
    struct mutex operation;
    struct msu_management_session *creator;
    struct msu_runtime_service *service;
    u8 *address;
    u64 id, published_address;
    u32 epoch, references;
    bool reserved, creator_reference, quarantined, legacy;
};

struct msu_publication_member {
    struct msu_management_resource *resource;
    struct msu_shared_bool *shared_bool;
    u32 role;
};

/* P5-only ownership records. These stay inside the existing publication
 * transaction; no control-page or request wire field is added. */
struct msu_private_return_owner {
    struct list_head node;
    struct msu_publication *publication;
    struct task_struct *task;
    u64 publication_id;
    u64 caller_x28, caller_lr, incoming_sp;
    bool pooled, generic, occupied;
};

struct msu_publication {
    struct mutex operation;
    struct work_struct cleanup;
    struct msu_management_session *session;
    struct msu_runtime_service *service;
    maplesu_publication_control_v3 *control;
    struct msu_publication_member members[MSU_MANAGEMENT_SLOTS];
    u64 id, entry, gate, payload;
    u32 epoch, count, references, original, branch;
    struct list_head private_return_owners;
    struct msu_private_return_owner *private_return_pool;
    u32 private_return_count;
    int private_return_error;
    u64 private_return_failures;
    struct module *target_module;
    struct module *private_actor_module;
    u64 private_actor_target, private_actor_allocation;
    u32 private_actor_kcfi;
    bool private_actor_attached;
    bool reserved, canceled, ever_exposed, enabled, entry_restored;
    bool retire_requested, payload_released, quarantined;
    bool entry_drained, gate_drained;
    bool native_text_owned, legacy;
};

/* Sleepable order: session (when present) -> service construction (when present)
 * -> publication -> CPU hotplug -> text -> table.
 * Shared bool operations use session -> publication (when applicable) -> one
 * shared mutex -> table, without entering CPU/text locks while holding it.
 * Atomic gate/revoke paths take only the table lock and enqueue work. */
static DEFINE_RAW_SPINLOCK(msu_management_lock);
static DEFINE_MUTEX(msu_management_discard_lock);
static struct {
    struct msu_manager_owner *owner;
    maplesu_host_instance host;
    struct msu_management_session *sessions[MSU_MANAGEMENT_SLOTS];
    struct msu_management_resource resources[MSU_MANAGEMENT_SLOTS];
    struct msu_shared_bool shared_bools[MSU_MANAGEMENT_SLOTS];
    struct msu_publication publications[MSU_MANAGEMENT_SLOTS];
    struct msu_runtime_service services[MSU_MANAGEMENT_SLOTS];
    struct msu_legacy_gateway legacy_gateways[MSU_MANAGEMENT_SLOTS];
    struct maplesu_legacy_hook_provider legacy_provider;
    u64 next_session, next_resource, next_publication, next_shared_bool, next_service;
    u32 live_sessions;
    bool ready, ever_opened, ever_registered, exec_registered, exit_registered, legacy_registered;
} management;

/* External linkage preserves the compiler's real KCFI callback type IDs. */
void msu_management_cleanup(struct work_struct *work);
void msu_publication_cleanup(struct work_struct *work);
void msu_management_process_exit(void *data, struct task_struct *task);
void msu_management_process_exit_group_dead(void *data, struct task_struct *task,
    bool group_dead);
void msu_management_process_exec(void *data, struct task_struct *task,
    pid_t old_pid, struct linux_binprm *bprm);
int msu_management_validate_credentials(void *context);
static int msu_publication_cancel_session(struct msu_management_session *session,
    u32 *effects);
static int msu_publication_release_unexposed(struct msu_publication *publication,
    u32 *effects);
static int msu_shared_bool_close_session(struct msu_management_session *session,
    u32 *effects);
static int msu_management_construction_check(struct msu_management_session *session,
    struct msu_runtime_service *service);
static void msu_management_detach_locked(struct msu_management_session *session);
static int msu_legacy_hook_init(void);
static int msu_legacy_hook_shutdown(void);

#ifdef CONFIG_TRACEPOINTS
typedef void (*msu_management_exit_probe_fn)(void *, struct task_struct *);
typedef void (*msu_management_exit_group_probe_fn)(void *, struct task_struct *, bool);
typedef int (*msu_management_exit_register_fn)(msu_management_exit_probe_fn, void *);
typedef int (*msu_management_exit_group_register_fn)(msu_management_exit_group_probe_fn, void *);
_Static_assert(__builtin_types_compatible_p(typeof(&register_trace_sched_process_exit),
    msu_management_exit_register_fn) ||
    __builtin_types_compatible_p(typeof(&register_trace_sched_process_exit),
        msu_management_exit_group_register_fn), "unsupported sched_process_exit probe type");
_Static_assert(__builtin_types_compatible_p(typeof(&register_trace_sched_process_exit),
    typeof(&unregister_trace_sched_process_exit)), "sched_process_exit register/unregister type mismatch");
/* Select from the actual headers; both callbacks retain their real KCFI types. */
#define MSU_MANAGEMENT_EXIT_PROBE __builtin_choose_expr(\
    __builtin_types_compatible_p(typeof(&register_trace_sched_process_exit), \
        msu_management_exit_group_register_fn), \
    &msu_management_process_exit_group_dead, &msu_management_process_exit)
#endif

static bool msu_management_host_matches(maplesu_host_instance value)
{
    return value.low == management.host.low && value.high == management.host.high;
}

/* Protocol addresses retain the caller's ARM64 top-byte tag. Nofault user
 * access and range checks consume the same canonical user address. */
static void __user *msu_management_user_pointer(u64 address)
{
#ifdef CONFIG_ARM64
    return (void __user *)untagged_addr((unsigned long)address);
#else
    return (void __user *)(unsigned long)address;
#endif
}

static bool msu_management_user_range(u64 address, u32 length)
{
    address = (unsigned long)msu_management_user_pointer(address);
    return address && length && address <= ~0ULL - (length - 1) &&
        !(address & (1ULL << 55));
}

static bool msu_management_sleepable(void)
{
    return in_task() && !in_atomic() && !irqs_disabled();
}

static bool msu_management_code_available(void)
{
#ifdef MSU_MANAGEMENT_EXECMEM
#if !defined(CONFIG_EXECMEM) || (defined(CONFIG_ARCH_HAS_EXECMEM_ROX) && \
    !(defined(MSU_MANAGEMENT_EXECMEM_ALLOC_RW) && MSU_MANAGEMENT_EXECMEM_ALLOC_RW))
    return false;
#else
    return true;
#endif
#else
    return IS_ENABLED(CONFIG_MODULES);
#endif
}

u64 msu_management_capabilities(void)
{
    u64 capabilities = MAPLESU_MANAGEMENT_CAPABILITIES | MAPLESU_CAP_TEXT_WRITE |
        MAPLESU_CAP_SHARED_BOOL | MAPLESU_CAP_UID_SNAPSHOT;
#if defined(MSU_MANAGEMENT_TASK_STATE_VERIFIED) && MSU_MANAGEMENT_TASK_STATE_VERIFIED
    capabilities |= MAPLESU_CAP_TASK_STATE;
#endif
#if defined(CONFIG_PROC_FS) && defined(CONFIG_SECURITY)
    capabilities |= MAPLESU_CAP_PROCATTR;
#endif
    if (!msu_management_code_available()) return capabilities;
    capabilities |= MAPLESU_CAP_CODE_RESERVE;
#if defined(CONFIG_TASKS_RCU) && defined(MSU_MANAGEMENT_NATIVE_TEXT_OWNER) && MSU_MANAGEMENT_NATIVE_TEXT_OWNER
    capabilities |= MAPLESU_CAP_PUBLICATION;
#endif
    return capabilities;
}

static void *msu_management_code_alloc(size_t bytes)
{
    if (!msu_management_code_available()) return NULL;
#ifdef MSU_MANAGEMENT_EXECMEM
#if defined(MSU_MANAGEMENT_EXECMEM_ALLOC_RW) && MSU_MANAGEMENT_EXECMEM_ALLOC_RW
    return execmem_alloc_rw(EXECMEM_DEFAULT, bytes);
#else
    return execmem_alloc(EXECMEM_DEFAULT, bytes);
#endif
#else
    return module_alloc(bytes);
#endif
}

static void msu_management_resource_free(struct msu_management_resource *resource)
{
    if (resource->backend == MSU_MANAGEMENT_VMALLOC) {
        vfree(resource->address);
        return;
    }
#ifdef MSU_MANAGEMENT_EXECMEM
    execmem_free(resource->address);
#else
    module_memfree(resource->address);
#endif
}

static bool msu_management_branch(u64 entry, u64 target, u32 *instruction)
{
    if ((entry | target) & 3) return false;
    if (target >= entry) {
        if (target - entry > (1ULL << 27) - 4) return false;
    } else if (entry - target > (1ULL << 27)) return false;
    if (instruction) *instruction = 0x14000000U | (u32)(((target - entry) >> 2) & 0x03ffffffU);
    return true;
}

static bool msu_management_overlaps(u64 start, u64 last, const void *address, u64 bytes)
{
    u64 other = (unsigned long)address;
    return address && bytes && start <= other + bytes - 1 && last >= other;
}

/* Call under the table lock. Public raw writes never modify lifecycle state. */
static bool msu_management_protected(u64 address, u32 bytes)
{
    u64 last = address + bytes - 1;
    unsigned int i;
    if (msu_management_overlaps(address, last, &management, sizeof(management)) ||
        msu_management_overlaps(address, last, &msu_management_lock, sizeof(msu_management_lock)) ||
        msu_management_overlaps(address, last, &msu_management_discard_lock,
            sizeof(msu_management_discard_lock))) return true;
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_publication *publication = &management.publications[i];
        struct msu_management_resource *resource = &management.resources[i];
        struct msu_shared_bool *shared_bool = &management.shared_bools[i];
        if (publication->reserved &&
            ((!publication->canceled && !publication->payload_released && msu_management_overlaps(address, last,
                (void *)(unsigned long)publication->entry, 4)) ||
             msu_management_overlaps(address, last, publication->control, PAGE_SIZE))) return true;
        if (resource->reserved &&
            msu_management_overlaps(address, last, resource->address,
                (u64)resource->pages << PAGE_SHIFT)) return true;
        if (shared_bool->reserved &&
            msu_management_overlaps(address, last, shared_bool->address, PAGE_SIZE)) return true;
    }
    return false;
}

static struct msu_publication *msu_publication_find(u64 id)
{
    unsigned int i;
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i)
        if (management.publications[i].reserved && management.publications[i].id == id)
            return &management.publications[i];
    return NULL;
}

/* Service identities and entry sets never change after registration. */
static int msu_publication_entry_check(const maplesu_publication_prepare_v3 *descriptor);

static struct msu_runtime_service *msu_runtime_service_find(u64 id)
{
    unsigned int i;
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i)
        if (management.services[i].reserved && management.services[i].id == id)
            return &management.services[i];
    return NULL;
}

static bool msu_runtime_service_live(const struct msu_runtime_service *service)
{
    return service && READ_ONCE(service->reserved) && READ_ONCE(service->active) &&
        !READ_ONCE(service->aborting) && !READ_ONCE(service->discarded) &&
        msu_management_host_matches(service->host);
}

static bool msu_runtime_service_entry(const struct msu_runtime_service *service,
    u64 entry)
{
    unsigned int i;
    for (i = 0; i < service->entry_count; ++i)
        if (service->entries[i] == entry) return true;
    return false;
}

/* Table lock protects the execution domain and the refs it admits. */
static bool msu_publication_lifetime_valid(struct msu_publication *publication)
{
    unsigned int i;
    if (publication->service) {
        if (!msu_runtime_service_live(publication->service) ||
            !msu_runtime_service_entry(publication->service, publication->entry)) return false;
    } else if (!publication->legacy &&
        publication->epoch != msu_manager_owner_epoch(management.owner)) return false;
    for (i = 0; i < publication->count; ++i) {
        struct msu_shared_bool *shared = publication->members[i].shared_bool;
        struct msu_management_resource *resource = publication->members[i].resource;
        if (publication->members[i].role == MAPLESU_PUBLICATION_SHARED_BOOL) {
            if (!shared || !shared->address || !shared->references || shared->quarantined ||
                shared->service != publication->service ||
                shared->legacy != publication->legacy ||
                (!shared->service && shared->epoch != publication->epoch)) return false;
        } else if (!resource || !resource->reserved || resource->consumed ||
            resource->quarantined || resource->publication != publication) return false;
    }
    return true;
}

int msu_runtime_service_register(const struct msu_runtime_service_descriptor *descriptor,
    u64 *id)
{
    struct msu_runtime_service_descriptor snapshot;
    struct msu_runtime_service *service = NULL;
    u64 entries[MSU_RUNTIME_SERVICE_MAX_ENTRIES];
    unsigned long flags;
    unsigned int i, j, k;
    int error = 0;
    if (!id) return -EINVAL;
    *id = 0;
    if (!msu_management_sleepable()) return -EWOULDBLOCK;
    if (!descriptor) return -EINVAL;
    snapshot = *descriptor;
    if (!READ_ONCE(management.ready) || !msu_management_host_matches(snapshot.host) ||
        !snapshot.lifecycle_owner || snapshot.entry_count > MSU_RUNTIME_SERVICE_MAX_ENTRIES ||
        (snapshot.kind == MSU_RUNTIME_SERVICE_EARLY_HELPER ?
            (snapshot.entries || snapshot.entry_count) :
            (!snapshot.entries || !snapshot.entry_count))) return -EINVAL;
    switch (snapshot.kind) {
    case MSU_RUNTIME_SERVICE_UID_SU:
    case MSU_RUNTIME_SERVICE_EARLY_HELPER:
    case MSU_RUNTIME_SERVICE_LEGACY_MODULE:
    case MSU_RUNTIME_SERVICE_EARLY_FIXED:
        break;
    default:
        return -EINVAL;
    }
    if (snapshot.entry_count)
        memcpy(entries, snapshot.entries, snapshot.entry_count * sizeof(*entries));
    for (i = 0; i < snapshot.entry_count; ++i) {
        maplesu_publication_prepare_v3 target = { .entry_address = entries[i] };
        if ((entries[i] & 3) || entries[i] > ~0ULL - 4) return -EINVAL;
        error = msu_publication_entry_check(&target);
        if (error) return error;
        for (j = 0; j < i; ++j) if (entries[j] == entries[i]) return -EINVAL;
    }
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    if (!management.ready) { error = -ESTALE; goto out; }
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_runtime_service *other = &management.services[i];
        if (!other->reserved) {
            if (!service) service = other;
            continue;
        }
        if (!other->active) continue;
        for (j = 0; j < snapshot.entry_count; ++j)
            for (k = 0; k < other->entry_count; ++k)
                if (entries[j] == other->entries[k]) { error = -EBUSY; goto out; }
    }
    if (!service || !management.next_service) { error = -ENOSPC; goto out; }
    service->host = snapshot.host;
    service->kind = snapshot.kind;
    service->lifecycle_owner = snapshot.lifecycle_owner;
    service->entry_count = snapshot.entry_count;
    if (snapshot.entry_count)
        memcpy(service->entries, entries, snapshot.entry_count * sizeof(*entries));
    service->id = management.next_service++;
    service->reserved = service->active = true;
    management.ever_registered = true;
    *id = service->id;
out:
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    return error;
}

int msu_runtime_service_revoke(const maplesu_host_instance *host, u64 id,
    u32 *effects)
{
    struct msu_runtime_service *service;
    unsigned long flags;
    unsigned int i;
    bool pending = false, retained = false;
    int error = 0;
    if (!effects) return -EINVAL;
    *effects = 0;
    if (in_nmi()) return -EWOULDBLOCK;
    if (!host || !id || !msu_management_host_matches(*host)) return -EINVAL;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    service = msu_runtime_service_find(id);
    if (!service) { error = -ESTALE; goto out; }
    if (service->discarded) { *effects |= MAPLESU_EFFECT_RELEASED; goto out; }
    service->active = false;
    if (service->operations || service->helper_sessions) pending = true;
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_management_session *session = management.sessions[i];
        struct msu_publication *publication = &management.publications[i];
        struct msu_shared_bool *shared = &management.shared_bools[i];
        struct msu_management_resource *resource = &management.resources[i];
        bool drained;
        if (session && session->helper && session->service == service)
            msu_management_detach_locked(session);
        if (shared->service == service && shared->references)
            *effects |= MAPLESU_EFFECT_SHARED_RETAINED;
        if ((resource->service == service && resource->reserved && !resource->publication) ||
            (shared->service == service && service->factory_owned &&
                (shared->address || shared->references || shared->creator_reference || shared->quarantined))) pending = true;
        if ((resource->service == service && resource->quarantined) ||
            (shared->service == service && shared->quarantined)) *effects |= MAPLESU_EFFECT_QUARANTINED;
        if (!publication->reserved || publication->service != service) continue;
        publication->enabled = false;
        publication->retire_requested = true;
        if (publication->ever_exposed) {
            retained = true;
            drained = publication->entry_restored && publication->payload_released &&
                !publication->references && !publication->quarantined;
        } else drained = publication->canceled && publication->payload_released &&
            !publication->quarantined;
        if (publication->quarantined) *effects |= MAPLESU_EFFECT_QUARANTINED;
        if (!drained) {
            pending = true;
            if (!service->aborting) schedule_work(&publication->cleanup);
        }
    }
    if (retained) *effects |= MAPLESU_EFFECT_PUBLICATION_RETAINED | MAPLESU_EFFECT_GATE_PINNED;
    if (pending) {
        *effects |= MAPLESU_EFFECT_PENDING;
        error = -EINPROGRESS;
    } else *effects |= MAPLESU_EFFECT_RELEASED;
out:
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    return error;
}

static void msu_private_return_fault(struct msu_publication *publication, u64 id, int error)
{
    unsigned long flags;
    u64 failures = 0;
    bool first = false;
    if (!publication || !error) return;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    if (publication->id == id && publication->ever_exposed && !publication->payload_released) {
        first = !publication->private_return_error;
        if (first) publication->private_return_error = error;
        if (publication->private_return_failures != ~0ULL) ++publication->private_return_failures;
        failures = publication->private_return_failures;
        publication->enabled = false;
        publication->retire_requested = true;
        schedule_work(&publication->cleanup);
    }
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    if (first)
        pr_err("MapleSU private return owner: publication %llu error %d, failures=%llu; admission closed\n",
            (unsigned long long)id, error, (unsigned long long)failures);
}

/* The publication owns 64 reusable records. Atomic overflow allocation keeps
 * nested/parallel originals correct beyond that fast pool; allocation failure
 * happens before admission, closes future admission, and records real ENOMEM.
 * Every admitted record owns the ref until original AND callback have left. */
static struct msu_private_return_owner *msu_private_owner_acquire(u64 id,
    u64 caller_x28, u64 caller_lr, u64 incoming_sp, bool generic)
{
    struct msu_private_return_owner *owner = NULL, *allocated = NULL;
    struct msu_publication *publication;
    unsigned long flags;
    unsigned int i;
    if (in_nmi() || (READ_ONCE(MSU_DYN_FIELD(current, task_struct, flags)) & PF_EXITING)) return NULL;
retry:
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    publication = msu_publication_find(id);
    if (!publication || !publication->ever_exposed || !publication->enabled ||
        publication->quarantined || publication->retire_requested) goto denied;
    if (!msu_publication_lifetime_valid(publication) || publication->references == ~0U) {
        publication->enabled = false;
        publication->retire_requested = true;
        if (publication->references == ~0U) publication->quarantined = true;
        schedule_work(&publication->cleanup);
        goto denied;
    }
    for (i = 0; publication->private_return_pool && i < MSU_MANAGEMENT_SLOTS; ++i) {
        if (publication->private_return_pool[i].occupied) continue;
        owner = &publication->private_return_pool[i];
        owner->pooled = true;
        break;
    }
    if (!owner && !allocated) {
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        allocated = kmalloc(sizeof(*allocated), GFP_ATOMIC | __GFP_NOWARN);
        if (!allocated) {
            msu_private_return_fault(publication, id, -ENOMEM);
            return NULL;
        }
        goto retry;
    }
    if (!owner) {
        owner = allocated;
        allocated = NULL;
        owner->pooled = false;
    }
    owner->occupied = true;
    owner->generic = generic;
    owner->task = current;
    owner->publication = publication;
    owner->publication_id = id;
    owner->caller_x28 = caller_x28;
    owner->caller_lr = caller_lr;
    owner->incoming_sp = incoming_sp;
    msu_dynamic_get_task_struct(owner->task);
    INIT_LIST_HEAD(&owner->node);
    list_add_tail(&owner->node, &publication->private_return_owners);
    ++publication->private_return_count;
    ++publication->references;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    kfree(allocated);
    return owner;
denied:
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    kfree(allocated);
    return NULL;
}

/* Detach and scrub under the table lock. The last reference is dropped only
 * after the task put and dynamic free: retirement may then free the whole
 * pool, and this function must never touch a pooled record again. */
static void msu_private_owner_release(struct msu_private_return_owner *owner)
{
    struct msu_publication *publication;
    struct task_struct *task;
    unsigned long flags;
    bool pooled;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    publication = owner->publication;
    task = owner->task;
    pooled = owner->pooled;
    list_del_init(&owner->node);
    owner->task = NULL;
    owner->occupied = false;
    owner->publication = NULL;
    --publication->private_return_count;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    msu_dynamic_put_task_struct(task);
    if (!pooled) kfree(owner);
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    if (!publication->references) publication->quarantined = true;
    else --publication->references;
    if ((!publication->references || publication->quarantined) && publication->retire_requested)
        schedule_work(&publication->cleanup);
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
}

u64 msu_publication_enter(u64 id)
{
    struct msu_private_return_owner *owner = msu_private_owner_acquire(id, 0, 0, 0, true);
    return owner ? owner->publication->payload : 0;
}

void msu_publication_leave(u64 id)
{
    struct msu_publication *publication;
    struct msu_private_return_owner *owner, *found = NULL;
    unsigned long flags;
    if (in_nmi()) return;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    publication = msu_publication_find(id);
    if (publication) {
        list_for_each_entry_reverse(owner, &publication->private_return_owners, node) {
            if (owner->task == current && owner->generic) { found = owner; break; }
        }
    }
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    if (found) msu_private_owner_release(found);
    else if (publication) msu_private_return_fault(publication, id, -EINVAL);
}

static u64 msu_private_return_owner_acquire(u64 id, u64 caller_x28,
    u64 caller_lr, u64 incoming_sp)
{
    return (u64)(unsigned long)msu_private_owner_acquire(id,
        caller_x28, caller_lr, incoming_sp, false);
}

static void msu_private_return_owner_release(u64 address)
{
    struct msu_private_return_owner *owner = (void *)(unsigned long)address;
    if (!owner) return;
    if (owner->task != current || owner->generic || !owner->occupied) {
        msu_private_return_fault(owner->publication, owner->publication_id, -ESHUTDOWN);
        return;
    }
    msu_private_owner_release(owner);
}

static int msu_private_return_status(u64 id, int *error, u64 *failures)
{
    struct msu_publication *publication;
    unsigned long flags;
    if (!error || !failures) return -EINVAL;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    publication = msu_publication_find(id);
    if (!publication || !publication->reserved) {
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        return -ESTALE;
    }
    *error = publication->private_return_error;
    *failures = publication->private_return_failures;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    return 0;
}

static void msu_private_return_owner_task_exit(struct task_struct *task)
{
    struct msu_private_return_owner *owner, *found;
    unsigned long flags;
    unsigned int i;
    /* sched_process_exit is on the dying task; no original/callback can later
     * resume its abandoned stack. The final trampoline GP covers this suffix. */
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_publication *publication = &management.publications[i];
        for (;;) {
            found = NULL;
            raw_spin_lock_irqsave(&msu_management_lock, flags);
            list_for_each_entry(owner, &publication->private_return_owners, node) {
                if (owner->task == task) { found = owner; break; }
            }
            raw_spin_unlock_irqrestore(&msu_management_lock, flags);
            if (!found) break;
            msu_private_return_fault(publication, found->publication_id, -ESHUTDOWN);
            msu_private_owner_release(found);
        }
    }
}

static u64 msu_private_return_caller_x28(u64 owner_address)
{
    struct msu_private_return_owner *owner = (void *)(unsigned long)owner_address;
    return owner && owner->task == current ? owner->caller_x28 : 0;
}

static u64 msu_private_return_caller_lr(u64 owner_address)
{
    struct msu_private_return_owner *owner = (void *)(unsigned long)owner_address;
    return owner && owner->task == current ? owner->caller_lr : 0;
}

static u64 msu_private_return_payload(u64 address)
{
    struct msu_private_return_owner *owner = (void *)(unsigned long)address;
    if (!owner || owner->task != current) return 0;
    /* This owner already holds the publication ref. Retire waits for it; a
     * concurrent disable closes new admission and cannot erase this callback. */
    return owner->publication->payload;
}

/* Called only by the matching management EXEC before PUBLISH. The actual
 * session operation mutex is held by that outer request. No module or target
 * pointer supplied by userspace is trusted as a lifetime reference. */
static int msu_private_actor_attach(u64 id, u64 allocation, u64 target, u32 kcfi)
{
    struct msu_publication *publication;
    struct msu_management_session *session;
    struct msu_management_resource *actor = NULL;
    struct module *module = NULL;
    unsigned long flags;
    unsigned int i;
    u32 observed;
    int error;
    if (target < 0xffff000000000004ULL || target > ~0ULL - 4 || (target & 3)) return -EINVAL;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    publication = msu_publication_find(id);
    session = publication ? publication->session : NULL;
    if (!session || session->task != current || session->helper) publication = NULL;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    if (!publication) return -ESTALE;
    mutex_lock(&publication->operation);
    error = msu_management_construction_check(session, NULL);
    if (error) goto out;
    if (publication->id != id || publication->session != session || publication->ever_exposed ||
        publication->canceled || publication->retire_requested || publication->private_actor_attached) {
        error = -ESTALE;
        goto out;
    }
    for (i = 0; i < publication->count; ++i) {
        struct msu_management_resource *member = publication->members[i].resource;
        if (publication->members[i].role == MAPLESU_PUBLICATION_PAYLOAD_CODE && member &&
            (unsigned long)member->address == allocation) actor = member;
    }
    if (!actor || actor->bytes < 8 || actor->uploaded != actor->bytes || actor->quarantined) {
        error = -EINVAL;
        goto out;
    }
    preempt_disable();
    if (!core_kernel_text(target - 4) || !core_kernel_text(target + 4)) {
        module = __module_text_address((unsigned long)target);
        if (!module || __module_text_address((unsigned long)target - 4) != module ||
            __module_text_address((unsigned long)target + 4) != module || !try_module_get(module)) {
            preempt_enable();
            error = -ENODEV;
            goto out;
        }
    }
    preempt_enable();
    if (copy_from_kernel_nofault(&observed, (void *)(unsigned long)(target - 4), sizeof(observed))) {
        error = -EFAULT;
        goto put;
    }
    if (observed != kcfi || memcmp(actor->address, &kcfi, sizeof(kcfi))) {
        error = -ESTALE;
        goto put;
    }
    error = msu_management_construction_check(session, NULL);
    if (error) goto put;
    publication->private_actor_module = module;
    publication->private_actor_target = target;
    publication->private_actor_allocation = allocation;
    publication->private_actor_kcfi = kcfi;
    publication->private_actor_attached = true;
    module = NULL;
put:
    if (module) module_put(module);
out:
    mutex_unlock(&publication->operation);
    return error;
}

static void msu_private_actor_release(struct msu_publication *publication)
{
    struct module *module = publication->private_actor_module;
    publication->private_actor_module = NULL;
    publication->private_actor_attached = false;
    if (module) module_put(module);
}

_Static_assert(__builtin_types_compatible_p(typeof(&msu_private_return_owner_acquire),
    u64 (*)(u64, u64, u64, u64)), "return acquire gate ABI");
_Static_assert(__builtin_types_compatible_p(typeof(&msu_private_return_owner_release),
    void (*)(u64)), "return release gate ABI");
_Static_assert(__builtin_types_compatible_p(typeof(&msu_private_return_caller_x28),
    u64 (*)(u64)) && __builtin_types_compatible_p(typeof(&msu_private_return_caller_lr),
    u64 (*)(u64)) && __builtin_types_compatible_p(typeof(&msu_private_return_payload),
    u64 (*)(u64)), "return accessors gate ABI");
_Static_assert(__builtin_types_compatible_p(typeof(&msu_private_return_status),
    int (*)(u64, int *, u64 *)), "return status gate ABI");
_Static_assert(__builtin_types_compatible_p(typeof(&msu_private_actor_attach),
    int (*)(u64, u64, u64, u32)), "actor attach gate ABI");
_Static_assert(offsetof(maplesu_publication_return_private, acquire_function) == 16 &&
    offsetof(maplesu_publication_return_private, payload_function) == 64 &&
    sizeof(maplesu_publication_return_private) == 72, "private return descriptor ABI");

/* The table owns one reference; every operation owns another. A detached
 * session is invisible before its last reference queues sleepable cleanup. */
static void msu_management_put(struct msu_management_session *session)
{
    unsigned long flags;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    if (!--session->references) schedule_work(&session->cleanup);
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
}

static void msu_management_detach_locked(struct msu_management_session *session)
{
    unsigned int i;
    if (!session->attached) return;
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        if (management.sessions[i] != session) continue;
        management.sessions[i] = NULL;
        WRITE_ONCE(session->attached, false);
        if (!--session->references) schedule_work(&session->cleanup);
        return;
    }
}

static void msu_management_retire(struct msu_management_session *session)
{
    unsigned long flags;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    msu_management_detach_locked(session);
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
}

static struct msu_management_session *msu_management_get(u64 id)
{
    struct msu_management_session *session = NULL;
    unsigned long flags;
    unsigned int i;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_management_session *value = management.sessions[i];
        if (!value || value->task != current || (id && value->id != id)) continue;
        ++value->references;
        session = value;
        break;
    }
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    return session;
}

static bool msu_management_task_matches(struct msu_management_session *session)
{
    return current == session->task && MSU_DYN_FIELD(current, task_struct, mm) == session->mm &&
        MSU_DYN_FIELD(current, task_struct, self_exec_id) == session->exec_id;
}

static int msu_management_check_expected_cred(struct msu_management_session *session,
    const struct cred *expected)
{
    const struct cred *overridden;
    u32 epoch = 0;
    bool allowed;
    if (!READ_ONCE(session->attached) || !msu_management_task_matches(session) ||
        (MSU_DYN_FIELD(current, task_struct, flags) & PF_EXITING) || msu_dynamic_current_cred() != expected ||
        msu_dynamic_current_real_cred() != expected) goto stale;
    if (session->legacy_gateway) {
        if (!READ_ONCE(management.ready) || !session->legacy_active) goto stale;
        return 0;
    }
    if (session->helper) {
        if (!session->service || session->service->kind != MSU_RUNTIME_SERVICE_EARLY_HELPER ||
            !msu_runtime_service_live(session->service) ||
            msu_installation_helper_validate(session->helper) ||
            !READ_ONCE(session->attached) || !msu_management_task_matches(session) ||
            (MSU_DYN_FIELD(current, task_struct, flags) & PF_EXITING) || msu_dynamic_current_cred() != expected ||
            msu_dynamic_current_real_cred() != expected || !msu_runtime_service_live(session->service)) goto stale;
        return 0;
    }
    if (msu_manager_owner_epoch(management.owner) != session->epoch) goto stale;
    /* Only the existing nonallocating owner check runs under this override.
     * No prepare/commit, user copy, I/O or request dispatch occurs here. */
    overridden = override_creds(session->original);
    allowed = msu_manager_owner_allowed(management.owner,
        session->principal_uid, &epoch);
    revert_creds(overridden);
    if (!allowed || epoch != session->epoch || !READ_ONCE(session->attached) ||
        !msu_management_task_matches(session) || (MSU_DYN_FIELD(current, task_struct, flags) & PF_EXITING) ||
        msu_dynamic_current_cred() != expected ||
        msu_dynamic_current_real_cred() != expected ||
        msu_manager_owner_epoch(management.owner) != session->epoch) goto stale;
    return 0;
stale:
    msu_management_retire(session);
    return -ESTALE;
}

static int msu_management_check(struct msu_management_session *session)
{
    return msu_management_check_expected_cred(session, session->active);
}

static int msu_management_construction_check(struct msu_management_session *session,
    struct msu_runtime_service *service)
{
    if (session) return msu_management_check(session);
    return msu_runtime_service_live(service) ? 0 : -ESTALE;
}

/* The corresponding session/service operation mutex is already held. */
static bool msu_management_construction_live(struct msu_management_session *session,
    struct msu_runtime_service *service)
{
    if (session) return READ_ONCE(session->attached) &&
        (session->legacy_gateway ? session->legacy_active :
            (session->helper ? msu_runtime_service_live(session->service) :
                session->epoch == msu_manager_owner_epoch(management.owner)));
    return msu_runtime_service_live(service);
}

/* The caller already authenticated the current session. This only selects its
 * command dialect; it never opens, claims or creates installation authority. */
bool msu_management_current_helper(void)
{
    struct msu_management_session *session = msu_management_get(0);
    bool helper = session && session->helper;
    if (session) msu_management_put(session);
    return helper;
}

int msu_management_authorize(u32 *principal_uid, u32 *epoch)
{
    struct msu_management_session *session;
    u32 uid, current_epoch = 0;
    int error;
    if (principal_uid) *principal_uid = 0;
    if (epoch) *epoch = 0;
    if (!msu_management_sleepable()) return -EWOULDBLOCK;
    if (!principal_uid || !epoch || !READ_ONCE(management.ready)) return -EPERM;
    session = msu_management_get(0);
    if (session) {
        mutex_lock(&session->operation);
        error = session->legacy_gateway ? -EPERM : msu_management_check(session);
        if (!error) { *principal_uid = session->principal_uid; *epoch = session->epoch; }
        mutex_unlock(&session->operation);
        msu_management_put(session);
        return error;
    }
    uid = __kuid_val(msu_dynamic_current_uid());
    if (msu_dynamic_current_cred() != msu_dynamic_current_real_cred() ||
        !msu_manager_owner_allowed(management.owner, uid, &current_epoch)) return -EPERM;
    *principal_uid = uid;
    *epoch = current_epoch;
    return 0;
}

static int msu_runtime_service_bind_session_locked(struct msu_management_session *session,
    const maplesu_host_instance *host, u64 service_id, u64 publication_id)
{
    struct msu_publication *publication;
    struct msu_runtime_service *service;
    unsigned long flags;
    unsigned int i;
    int error;
    bool service_locked = false;
    if (!msu_management_sleepable()) return -EWOULDBLOCK;
    if (!host || !service_id || !publication_id || !msu_management_host_matches(*host))
        return -EINVAL;
    error = msu_management_check(session);
    if (error) goto put_session;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    publication = msu_publication_find(publication_id);
    service = msu_runtime_service_find(service_id);
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    if (!publication || !service) { error = -ESTALE; goto put_session; }
    mutex_lock(&service->operation);
    service_locked = true;
    mutex_lock(&publication->operation);
    error = msu_management_check(session);
    if (error) goto unlock_publication;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    if (publication->id != publication_id || session->legacy_gateway || !session->attached ||
        session->epoch != msu_manager_owner_epoch(management.owner) || service->factory_owned ||
        !msu_runtime_service_live(service) ||
        !msu_runtime_service_entry(service, publication->entry) ||
        publication->session != session || publication->epoch != session->epoch ||
        publication->ever_exposed || publication->canceled || publication->retire_requested ||
        publication->payload_released || publication->quarantined) { error = -ESTALE; goto unlock_table; }
    if (publication->service) { error = -EALREADY; goto unlock_table; }
    for (i = 0; i < publication->count; ++i) {
        struct msu_shared_bool *shared = publication->members[i].shared_bool;
        struct msu_management_resource *resource = publication->members[i].resource;
        if (publication->members[i].role == MAPLESU_PUBLICATION_SHARED_BOOL) {
            if (!shared || !shared->address || !shared->references || shared->quarantined ||
                (shared->service && shared->service != service) ||
                (!shared->service && (shared->epoch != session->epoch ||
                    !shared->creator_reference || shared->creator != session ||
                    shared->references != 2))) { error = -ESTALE; goto unlock_table; }
        } else if (!resource || !resource->reserved || resource->publication != publication ||
            resource->session != session || resource->consumed || resource->quarantined ||
            resource->uploaded != resource->bytes) { error = -EINVAL; goto unlock_table; }
    }
    /* No domain changes until every member and the current lease have passed. */
    publication->service = service;
    for (i = 0; i < publication->count; ++i)
        if (publication->members[i].shared_bool)
            publication->members[i].shared_bool->service = service;
unlock_table:
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
unlock_publication:
    mutex_unlock(&publication->operation);
put_session:
    if (service_locked) mutex_unlock(&service->operation);
    return error;
}

int msu_runtime_service_bind(const maplesu_host_instance *host, u64 service_id,
    u64 publication_id)
{
    struct msu_management_session *session;
    int error;
    if (!msu_management_sleepable()) return -EWOULDBLOCK;
    session = msu_management_get(0);
    if (!session) return -ESTALE;
    mutex_lock(&session->operation);
    error = msu_runtime_service_bind_session_locked(session, host, service_id, publication_id);
    mutex_unlock(&session->operation);
    msu_management_put(session);
    return error;
}

int msu_runtime_service_bind_held(void *context, const maplesu_host_instance *host,
    u64 service_id, u64 publication_id)
{
    struct msu_management_session *session;
    int error;
    if (!msu_management_sleepable()) return -EWOULDBLOCK;
    session = msu_management_get(0);
    if (!session) return -ESTALE;
    if (context != session) error = -ESTALE;
    else {
        lockdep_assert_held(&session->operation);
        error = msu_runtime_service_bind_session_locked(session, host, service_id, publication_id);
    }
    msu_management_put(session);
    return error;
}

/* Shared objects and their IDs are boot-stable; only the backing page retires. */
static struct msu_shared_bool *msu_shared_bool_find(u64 id)
{
    unsigned int i;
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i)
        if (management.shared_bools[i].reserved && management.shared_bools[i].id == id)
            return &management.shared_bools[i];
    return NULL;
}

/* shared.operation serializes STORE, ref changes and the final physical free. */
static int msu_shared_bool_free(struct msu_shared_bool *shared, u32 *effects)
{
    unsigned long flags;
    int error;
    if (shared->references) return 0;
    if (!shared->address) {
        if (shared->quarantined) *effects |= MAPLESU_EFFECT_QUARANTINED;
        return shared->quarantined ? -EIO : 0;
    }
    if (shared->quarantined) { *effects |= MAPLESU_EFFECT_QUARANTINED; return -EIO; }
    error = set_memory_nx((unsigned long)shared->address, 1);
    if (!error) error = set_memory_rw((unsigned long)shared->address, 1);
    if (error) {
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        shared->quarantined = true;
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        *effects |= MAPLESU_EFFECT_QUARANTINED;
        return error;
    }
    vfree(shared->address);
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    shared->address = NULL;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    *effects |= MAPLESU_EFFECT_RELEASED;
    return 0;
}

static int msu_shared_bool_drop_creator(struct msu_shared_bool *shared,
    struct msu_management_session *session, u32 *effects)
{
    unsigned long flags;
    if (shared->creator_reference && shared->creator != session) return -EPERM;
    if (shared->creator_reference) {
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        shared->creator_reference = false;
        shared->creator = NULL;
        --shared->references;
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    }
    *effects |= MAPLESU_EFFECT_REFERENCE_DROPPED;
    if (shared->references) {
        *effects |= MAPLESU_EFFECT_SHARED_RETAINED;
        return 0;
    }
    if (!shared->address && !shared->quarantined) {
        *effects |= MAPLESU_EFFECT_RELEASED;
        return 0;
    }
    return msu_shared_bool_free(shared, effects);
}

static int msu_shared_bool_close_session(struct msu_management_session *session, u32 *effects)
{
    unsigned int i;
    int first_error = 0;
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_shared_bool *shared = &management.shared_bools[i];
        int error;
        if (READ_ONCE(shared->creator) != session) continue;
        mutex_lock(&shared->operation);
        error = msu_shared_bool_drop_creator(shared, session, effects);
        mutex_unlock(&shared->operation);
        if (!first_error) first_error = error;
    }
    return first_error;
}

static int msu_shared_bool_acquire_domain(struct msu_management_session *session,
    struct msu_runtime_service *service, u64 id, u64 address,
    struct msu_shared_bool **output)
{
    struct msu_shared_bool *shared;
    unsigned long flags;
    int error;
    /* A NULL output validates before closure reservation without taking a ref. */
    if (output) *output = NULL;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    shared = msu_shared_bool_find(id);
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    if (!shared) return -ESTALE;
    mutex_lock(&shared->operation);
    error = msu_management_construction_check(session, service);
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    if (!error && (!msu_management_construction_live(session, service) ||
        address != shared->published_address || !shared->address ||
        !shared->references || shared->quarantined ||
        shared->legacy != !!(session && session->legacy_gateway) ||
        (service ? shared->service != service :
            (shared->service ? !msu_runtime_service_live(shared->service) :
                shared->epoch != session->epoch)))) error = -ESTALE;
    if (!error && output && shared->references == ~0U) error = -EOVERFLOW;
    if (!error && output) {
        ++shared->references;
        *output = shared;
    }
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    mutex_unlock(&shared->operation);
    return error;
}

/* Publication drops never report creator-reference effects. */
static int msu_shared_bool_put(struct msu_shared_bool *shared, u32 *effects)
{
    unsigned long flags;
    int error;
    mutex_lock(&shared->operation);
    if (!shared->references) {
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        shared->quarantined = true;
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        *effects |= MAPLESU_EFFECT_QUARANTINED;
        error = -EIO;
    } else {
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        --shared->references;
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        error = msu_shared_bool_free(shared, effects);
    }
    mutex_unlock(&shared->operation);
    return error;
}

static int msu_shared_bool_create_domain(struct msu_management_session *session,
    struct msu_runtime_service *service, u8 value, maplesu_response_owner_v3 *response)
{
    struct msu_shared_bool *shared = NULL;
    unsigned long flags;
    unsigned int i;
    u8 *address;
    int error;
    if (value > 1) return -EINVAL;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        if (management.shared_bools[i].reserved) continue;
        shared = &management.shared_bools[i];
        break;
    }
    if (!shared || !management.next_shared_bool) {
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        return -ENOSPC;
    }
    shared->reserved = true;
    shared->id = management.next_shared_bool++;
    shared->epoch = session ? session->epoch : 0;
    shared->service = service;
    shared->legacy = session && session->legacy_gateway;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    mutex_lock(&shared->operation);
    address = vmalloc(PAGE_SIZE);
    if (!address) { error = -ENOMEM; goto out; }
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    shared->address = address;
    shared->published_address = (unsigned long)address;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    response->metadata_length |= MAPLESU_EFFECT_ALLOCATED;
    error = set_memory_nx((unsigned long)address, 1);
    if (!error) error = set_memory_rw((unsigned long)address, 1);
    if (!error) error = msu_management_construction_check(session, service);
    if (error) {
        int release_error = msu_shared_bool_free(shared, &response->metadata_length);
        (void)release_error;
        goto out;
    }
    memset(address, 0, PAGE_SIZE);
    WRITE_ONCE(*address, value);
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    shared->creator = session;
    shared->creator_reference = true;
    shared->references = 1;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    response->result = (unsigned long)address;
    response->metadata_address = shared->id;
out:
    mutex_unlock(&shared->operation);
    return error;
}

static int msu_shared_bool_create(struct msu_management_session *session,
    const maplesu_management_v3 *operation, maplesu_response_owner_v3 *response)
{
    u8 value;
    if (copy_from_user_nofault(&value, msu_management_user_pointer(operation->data_user), 1))
        return -EFAULT;
    return msu_shared_bool_create_domain(session, NULL, value, response);
}

static int msu_shared_bool_update(struct msu_management_session *session,
    const maplesu_management_v3 *operation, bool release,
    maplesu_response_owner_v3 *response)
{
    struct msu_shared_bool *shared;
    unsigned long flags;
    u8 value;
    int error;
    response->metadata_address = operation->handle;
    if (!release) {
        if (copy_from_user_nofault(&value, msu_management_user_pointer(operation->data_user), 1))
            return -EFAULT;
        if (value > 1) return -EINVAL;
    }
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    shared = msu_shared_bool_find(operation->handle);
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    if (!shared) return -ESTALE;
    mutex_lock(&shared->operation);
    error = msu_management_check(session);
    if (error) goto out;
    if (shared->legacy != !!session->legacy_gateway) { error = -EPERM; goto out; }
    if (operation->address != shared->published_address) { error = -ESTALE; goto out; }
    if (release) {
        error = msu_shared_bool_drop_creator(shared, session, &response->metadata_length);
    } else {
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        if (!session->attached || !shared->address || !shared->references || shared->quarantined ||
            (!session->legacy_gateway && msu_manager_owner_epoch(management.owner) != session->epoch) ||
            (shared->service ? !msu_runtime_service_live(shared->service) :
                shared->epoch != session->epoch)) error = -ESTALE;
        else {
            WRITE_ONCE(*shared->address, value);
            response->metadata_length |= MAPLESU_EFFECT_WRITTEN;
            if (READ_ONCE(*shared->address) == value) response->result = 1;
            else error = -EIO;
        }
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    }
out:
    mutex_unlock(&shared->operation);
    return error;
}

/* A failed permission transition keeps its global slot and allocation forever.
 * It cannot be found through a session or reused as a future resource. */
static void msu_management_clear_resource(struct msu_management_resource *resource)
{
    WRITE_ONCE(resource->session, NULL);
    resource->service = NULL;
    resource->publication = NULL;
    resource->address = NULL;
    resource->handle = 0;
    resource->bytes = resource->uploaded = resource->pages = 0;
    resource->backend = MSU_MANAGEMENT_VMALLOC;
    resource->reserved = resource->consumed = resource->quarantined = false;
}

static int msu_management_release(struct msu_management_resource *resource,
    u32 *effects)
{
    unsigned long flags;
    int error;
    resource->consumed = true;
    error = set_memory_nx((unsigned long)resource->address, resource->pages);
    if (!error) error = set_memory_rw((unsigned long)resource->address, resource->pages);
    if (!error) {
        msu_management_resource_free(resource);
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        msu_management_clear_resource(resource);
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        *effects |= MAPLESU_EFFECT_RELEASED;
        return 0;
    }
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    WRITE_ONCE(resource->session, NULL);
    resource->quarantined = true;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    *effects |= MAPLESU_EFFECT_QUARANTINED;
    return error;
}

static int msu_management_release_all(struct msu_management_session *session,
    u32 *effects)
{
    unsigned int i;
    int first_error = msu_publication_cancel_session(session, effects);
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_management_resource *resource = &management.resources[i];
        int error;
        if (READ_ONCE(resource->session) != session) continue;
        error = msu_management_release(resource, effects);
        if (!first_error) first_error = error;
    }
    {
        int error = msu_shared_bool_close_session(session, effects);
        if (!first_error) first_error = error;
    }
    {
        int error = msu_uid_gateway_close(session->id, effects);
        if (!first_error) first_error = error;
    }
    return first_error;
}

void msu_management_cleanup(struct work_struct *work)
{
    struct msu_management_session *session = container_of(work,
        struct msu_management_session, cleanup);
    unsigned long flags;
    u32 effects = 0;
    /* Zero references means no operation still owns this mutex or resources. */
    mutex_lock(&session->operation);
    msu_management_release_all(session, &effects);
    msu_dynamic_put_cred(session->active);
    msu_dynamic_put_cred(session->original);
    msu_dynamic_mmdrop(session->mm);
    msu_dynamic_put_task_struct(session->task);
    if (session->helper) msu_installation_helper_put(session->helper);
    mutex_unlock(&session->operation);
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    if (session->helper) --session->service->helper_sessions;
    --management.live_sessions;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    kfree(session);
}

static void msu_management_task_retire(struct task_struct *task)
{
    unsigned long flags;
    unsigned int i;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_management_session *session = management.sessions[i];
        if (session && session->task == task) msu_management_detach_locked(session);
    }
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
}

void msu_management_owner_invalidated(struct msu_manager_owner *owner)
{
    unsigned long flags;
    unsigned int i;
    u32 epoch;
    if (READ_ONCE(management.owner) != owner) return;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    /* Read inside the table lock so an older notification cannot retire a
     * session that was opened against a newer, already published epoch. */
    epoch = msu_manager_owner_epoch(owner);
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_management_session *session = management.sessions[i];
        struct msu_publication *publication = &management.publications[i];
        if (session && !session->helper && !session->legacy_gateway && session->epoch != epoch)
            msu_management_detach_locked(session);
        if (publication->reserved && publication->ever_exposed &&
            !publication->service && !publication->legacy && publication->epoch != epoch) {
            publication->enabled = false;
            publication->retire_requested = true;
            schedule_work(&publication->cleanup);
        }
    }
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
}

void msu_management_process_exit(void *data, struct task_struct *task)
{
    (void)data;
    msu_private_return_owner_task_exit(task);
    msu_su_exec_session_task_exit(task);
    msu_management_task_retire(task);
#if (defined(MSU_MANAGEMENT_NATIVE_TEXT_OWNER) && MSU_MANAGEMENT_NATIVE_TEXT_OWNER) || \
    (defined(MSU_MANAGEMENT_RESIDENT_TEXT_OWNER) && MSU_MANAGEMENT_RESIDENT_TEXT_OWNER)
    maplesu_legacy_hook_provider_task_exit(task);
#endif
}

void msu_management_process_exit_group_dead(void *data, struct task_struct *task,
    bool group_dead)
{
    (void)group_dead;
    msu_management_process_exit(data, task);
}

void msu_management_process_exec(void *data, struct task_struct *task,
    pid_t old_pid, struct linux_binprm *bprm)
{
    (void)data; (void)old_pid; (void)bprm;
    msu_management_task_retire(task);
}

int msu_management_discard_unpublished(void)
{
    maplesu_host_instance host;
    bool synchronize;
    unsigned long flags;
    unsigned int i;
    u32 uid_effects = 0;
    int first_error = 0;
    if (!msu_management_sleepable()) return -EWOULDBLOCK;
    mutex_lock(&msu_management_discard_lock);
    first_error = msu_early_runtime_close();
    if (first_error) goto out;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    if (management.ever_opened || management.live_sessions) first_error = -EBUSY;
    for (i = 0; !first_error && i < MSU_MANAGEMENT_SLOTS; ++i)
        if (management.services[i].reserved && (!management.services[i].discarded ||
            management.services[i].operations)) first_error = -EBUSY;
    if (first_error) {
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        goto out;
    }
    host = management.host;
    synchronize = management.exec_registered || management.exit_registered;
    WRITE_ONCE(management.ready, false);
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    /* Native controller construction does not open a Manager session. Service
     * abort cannot prove its registry/snapshot/callback domain has drained. */
    if (host.low | host.high) {
        first_error = msu_uid_gateway_discard_host_unpublished(&host, &uid_effects);
        if (first_error > 0) first_error = -EIO;
        if (!first_error && (uid_effects & MAPLESU_EFFECT_QUARANTINED)) first_error = -EIO;
        if (!first_error && (uid_effects & MAPLESU_EFFECT_PENDING)) first_error = -EINPROGRESS;
        if (first_error) goto out;
    }
    first_error = msu_legacy_hook_shutdown();
    if (first_error) goto out;
#ifdef CONFIG_TRACEPOINTS
    if (management.exec_registered) {
        int error = unregister_trace_sched_process_exec(msu_management_process_exec, NULL);
        if (error && error != -ENOENT) first_error = error;
        else management.exec_registered = false;
    }
    if (management.exit_registered) {
        int error = unregister_trace_sched_process_exit(MSU_MANAGEMENT_EXIT_PROBE, NULL);
        if (error && error != -ENOENT) { if (!first_error) first_error = error; }
        else management.exit_registered = false;
    }
#endif
    if (synchronize) tracepoint_synchronize_unregister();
    if (first_error) goto out;
    first_error = msu_early_runtime_discarded();
    if (first_error) goto out;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    memset(&management, 0, sizeof(management));
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
out:
    mutex_unlock(&msu_management_discard_lock);
    return first_error;
}

int msu_management_init(struct msu_manager_owner *owner,
    const maplesu_host_instance *host)
{
    unsigned int i;
    int error;
    if (!owner || !host || !(host->low | host->high)) return -EINVAL;
    if (!msu_runtime_layout_ready()) return -EPROTO;
    if (management.owner || management.ready) return -EALREADY;
    management.owner = owner;
    management.host = *host;
    management.next_session = management.next_resource = management.next_publication =
        management.next_shared_bool = management.next_service = 1;
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        mutex_init(&management.publications[i].operation);
        INIT_WORK(&management.publications[i].cleanup, msu_publication_cleanup);
        INIT_LIST_HEAD(&management.publications[i].private_return_owners);
        mutex_init(&management.shared_bools[i].operation);
        mutex_init(&management.services[i].operation);
    }
#ifdef CONFIG_TRACEPOINTS
    error = register_trace_sched_process_exit(MSU_MANAGEMENT_EXIT_PROBE, NULL);
    if (error) goto fail;
    management.exit_registered = true;
    error = register_trace_sched_process_exec(msu_management_process_exec, NULL);
    if (error) goto fail;
    management.exec_registered = true;
#else
    error = -EOPNOTSUPP;
    goto fail;
#endif
    error = msu_legacy_hook_init();
    if (error) goto fail;
    WRITE_ONCE(management.ready, true);
    return 0;
fail:
    msu_management_discard_unpublished();
    return error;
}

bool msu_management_helper_open_shape(const maplesu_request_owner_v3 *request,
    const maplesu_management_v3 *operation)
{
    return request && operation && request->command == MAPLESU_REQUEST_SESSION_OPEN &&
        !request->expected_epoch && !request->arg0 &&
        !(operation->host.low | operation->host.high) && !operation->session &&
        !operation->handle && !operation->address && !operation->flags && !operation->reserved &&
        operation->length == sizeof(maplesu_metadata_owner_v3) &&
        msu_management_user_range(operation->data_user, operation->length);
}

bool msu_management_helper_close_shape(const maplesu_request_owner_v3 *request,
    const maplesu_management_v3 *operation)
{
    return request && operation && request->command == MAPLESU_REQUEST_SESSION_CLOSE &&
        !request->expected_epoch && !request->arg0 &&
        !(operation->host.low | operation->host.high) && !operation->session &&
        !operation->handle && !operation->address && !operation->data_user &&
        !operation->length && !operation->flags && !operation->reserved;
}

static int msu_management_open_output(struct msu_management_session *session,
    const maplesu_management_v3 *operation, maplesu_response_owner_v3 *response)
{
    maplesu_metadata_owner_v3 metadata;
    u64 descriptor = 0;
    int error = msu_management_check(session);
    if (error) {
        if (session->helper && !READ_ONCE(session->attached))
            response->metadata_length |= MAPLESU_EFFECT_SESSION_CLOSED;
        return error;
    }
    response->result = session->id;
    if (!session->helper) return 0;
    error = msu_manager_resident_helper_metadata(session->principal_uid, session->epoch,
        &metadata, &descriptor);
    if (!error) {
        response->metadata_address = descriptor;
        if (copy_to_user_nofault(msu_management_user_pointer(operation->data_user),
                &metadata, sizeof(metadata))) error = -EFAULT;
    }
    if (!error) error = msu_management_check(session);
    if (error) {
        msu_management_retire(session);
        response->metadata_address = 0;
        response->metadata_length |= MAPLESU_EFFECT_SESSION_CLOSED;
    }
    return error;
}

static int msu_management_open(const maplesu_request_owner_v3 *request,
    const maplesu_management_v3 *operation, maplesu_response_owner_v3 *response)
{
    struct msu_management_session *session;
    struct msu_installation_helper_lease *helper = NULL;
    struct msu_runtime_service *service = NULL;
    unsigned long flags;
    u64 service_id = 0;
    u32 uid = __kuid_val(msu_dynamic_current_uid()), epoch = 0;
    unsigned int slot;
    bool helper_open = msu_management_helper_open_shape(request, operation);
    int error;
    session = msu_management_get(0);
    if (session) {
        mutex_lock(&session->operation);
        if (session->legacy_gateway || helper_open != !!session->helper) error = -EPERM;
        else {
            error = msu_management_check(session);
            if (!error && !helper_open && request->expected_epoch != session->epoch) error = -ESTALE;
            if (!error) error = msu_management_open_output(session, operation, response);
        }
        if (error && session->helper && !READ_ONCE(session->attached))
            response->metadata_length |= MAPLESU_EFFECT_SESSION_CLOSED;
        mutex_unlock(&session->operation);
        msu_management_put(session);
        return error;
    }
    if (!MSU_DYN_FIELD(current, task_struct, mm) || (MSU_DYN_FIELD(current, task_struct, flags) & PF_EXITING) ||
        msu_dynamic_current_cred() != msu_dynamic_current_real_cred()) return -EPERM;
    if (helper_open) {
        error = msu_installation_helper_acquire(&management.host, &helper, &epoch, &service_id,
            &response->metadata_length);
        if (error) return error;
        if (!helper || !epoch || !service_id) { error = -ESTALE; goto put_helper; }
    } else {
        if (!msu_manager_owner_allowed(management.owner, uid, &epoch)) return -EPERM;
        if (request->expected_epoch != epoch) return -ESTALE;
    }
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    if (helper_open) {
        service = msu_runtime_service_find(service_id);
        if (!service || service->kind != MSU_RUNTIME_SERVICE_EARLY_HELPER ||
            !msu_runtime_service_live(service) || service->entry_count ||
            service->helper_sessions == ~0U) {
            error = -ESTALE;
            goto unlock_put_helper;
        }
    }
    if (!management.ready || management.live_sessions == MSU_MANAGEMENT_SLOTS || !management.next_session) {
        error = -ENOSPC;
        goto unlock_put_helper;
    }
    ++management.live_sessions; /* Includes opens not yet allocated/published. */
    if (helper_open) ++service->helper_sessions;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    session = kzalloc(sizeof(*session), GFP_KERNEL);
    if (!session) { error = -ENOMEM; goto unreserve; }
    mutex_init(&session->operation);
    INIT_WORK(&session->cleanup, msu_management_cleanup);
    session->task = current;
    msu_dynamic_get_task_struct(session->task);
    session->mm = MSU_DYN_FIELD(current, task_struct, mm);
    msu_dynamic_mmgrab(session->mm);
    session->original = msu_dynamic_get_current_cred();
    session->active = msu_dynamic_get_cred(session->original);
    session->helper = helper;
    session->service = service;
    session->exec_id = MSU_DYN_FIELD(current, task_struct, self_exec_id);
    session->principal_uid = uid;
    session->epoch = epoch;
    session->references = 2; /* table plus this OPEN operation */
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    for (slot = 0; slot < MSU_MANAGEMENT_SLOTS; ++slot)
        if (!management.sessions[slot]) break;
    if (!management.ready || slot == MSU_MANAGEMENT_SLOTS || !management.next_session ||
        (helper_open && !msu_runtime_service_live(service))) {
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        msu_dynamic_put_cred(session->active); msu_dynamic_put_cred(session->original);
        msu_dynamic_mmdrop(session->mm); msu_dynamic_put_task_struct(session->task); kfree(session);
        error = -ESTALE;
        goto unreserve;
    }
    session->id = management.next_session++;
    WRITE_ONCE(session->attached, true);
    management.sessions[slot] = session;
    management.ever_opened = true;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    mutex_lock(&session->operation);
    error = msu_management_open_output(session, operation, response);
    mutex_unlock(&session->operation);
    msu_management_put(session);
    return error;
unreserve:
    if (helper) { msu_installation_helper_put(helper); helper = NULL; }
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    if (helper_open) --service->helper_sessions;
    --management.live_sessions;
unlock_put_helper:
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
put_helper:
    if (helper) msu_installation_helper_put(helper);
    return error;
}

static struct msu_management_resource *msu_management_resource_domain(
    struct msu_management_session *session, struct msu_runtime_service *service, u64 handle)
{
    unsigned int i;
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_management_resource *resource = &management.resources[i];
        if (((session && READ_ONCE(resource->session) == session && !resource->service) ||
                (!session && service && resource->service == service && !resource->session)) &&
            resource->handle == handle &&
            !resource->consumed && !resource->quarantined) return resource;
    }
    return NULL;
}

static struct msu_management_resource *msu_management_resource(
    struct msu_management_session *session, u64 handle)
{
    return msu_management_resource_domain(session, NULL, handle);
}

static int msu_management_alloc_domain(struct msu_management_session *session,
    struct msu_runtime_service *service,
    const maplesu_management_v3 *operation,
    maplesu_response_owner_v3 *response)
{
    struct msu_management_resource *resource = NULL;
    unsigned long flags;
    unsigned int i;
    u32 bytes = operation->length;
    bool code = operation->flags & MAPLESU_MANAGEMENT_ALLOC_CODE;
    void *address;
    int error;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        if (management.resources[i].reserved) continue;
        resource = &management.resources[i];
        break;
    }
    if (!resource || !management.next_resource) {
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        return -ENOSPC;
    }
    resource->reserved = true;
    WRITE_ONCE(resource->session, session);
    resource->service = service;
    resource->handle = management.next_resource++;
    resource->bytes = bytes;
    resource->pages = PAGE_ALIGN(bytes) >> PAGE_SHIFT;
    resource->backend = MSU_MANAGEMENT_VMALLOC;
    if (code) {
#ifdef MSU_MANAGEMENT_EXECMEM
        resource->backend = MSU_MANAGEMENT_EXECMEM_BACKEND;
#else
        resource->backend = MSU_MANAGEMENT_MODULE;
#endif
    }
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    address = code ? msu_management_code_alloc(PAGE_ALIGN(bytes)) :
        vmalloc(PAGE_ALIGN(bytes));
    if (!address) {
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        msu_management_clear_resource(resource);
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        return -ENOMEM;
    }
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    resource->address = address;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    response->metadata_length |= MAPLESU_EFFECT_ALLOCATED;
    error = set_memory_nx((unsigned long)resource->address, resource->pages);
    if (!error && code) error = set_memory_rw((unsigned long)resource->address, resource->pages);
    if (!error && code && operation->address &&
        (!msu_management_branch(operation->address, (unsigned long)resource->address, NULL) ||
         !msu_management_branch(operation->address, (unsigned long)resource->address +
             ((u64)resource->pages << PAGE_SHIFT) - 4, NULL))) error = -ERANGE;
    if (error) {
        int release_error = msu_management_release(resource, &response->metadata_length);
        (void)release_error;
        return error;
    }
    if (code) memset(resource->address, 0, PAGE_ALIGN(bytes));
    response->result = (unsigned long)resource->address;
    response->metadata_address = resource->handle;
    return 0;
}

static int msu_management_alloc(struct msu_management_session *session,
    const maplesu_management_v3 *operation, maplesu_response_owner_v3 *response)
{
    return msu_management_alloc_domain(session, NULL, operation, response);
}

/* CPU hotplug read lock and text_mutex are held by every caller. */
static int msu_management_patch_text_locked(struct msu_management_session *session,
    void **addresses, u32 *instructions, u32 count, maplesu_response_owner_v3 *response)
{
    u32 i;
    int error;
    if (session) {
        error = msu_management_check(session);
        if (error) return error;
    }
    response->metadata_length |= MAPLESU_EFFECT_TEXT_ATTEMPTED;
    error = aarch64_insn_patch_text(addresses, instructions, (int)count);
    if (!error) response->metadata_length |= MAPLESU_EFFECT_WRITTEN;
    for (i = 0; i < count; ++i) {
        u32 observed;
        if (copy_from_kernel_nofault(&observed, addresses[i], sizeof(observed))) {
            if (!error) error = -EFAULT;
            break;
        }
        if (observed != instructions[i]) {
            if (!error) error = -EIO;
            break;
        }
        response->result += sizeof(observed);
        response->metadata_length |= MAPLESU_EFFECT_WRITTEN;
    }
    return error;
}

static int msu_management_write_text(struct msu_management_session *session,
    const maplesu_management_v3 *operation, u32 *instructions,
    maplesu_response_owner_v3 *response)
{
    u32 bytes = ALIGN(operation->length, sizeof(*instructions));
    u32 count = bytes / sizeof(*instructions), i;
    void **addresses;
    unsigned long flags;
    bool protected;
    int error;
    addresses = vmalloc((size_t)count * sizeof(*addresses));
    if (!addresses) return -ENOMEM;
    for (i = 0; i < count; ++i)
        addresses[i] = (void *)(unsigned long)(operation->address +
            (u64)i * sizeof(*instructions));
    /* Native kprobe order: CPU hotplug read lock precedes text_mutex. */
    cpus_read_lock();
    mutex_lock(&text_mutex);
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    protected = msu_management_protected(operation->address, bytes);
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    error = protected ? -EBUSY : msu_management_check(session);
    if (!error && bytes != operation->length) {
        u32 tail;
        /* Sample neighboring bytes under the same lock as every gateway writer. */
        if (copy_from_kernel_nofault(&tail, addresses[count - 1], sizeof(tail)))
            error = -EFAULT;
        else memcpy((unsigned char *)instructions + operation->length,
            (unsigned char *)&tail + operation->length % sizeof(tail),
            bytes - operation->length);
    }
    if (!error) error = msu_management_patch_text_locked(session,
        addresses, instructions, count, response);
    if (response->result > operation->length) response->result = operation->length;
    mutex_unlock(&text_mutex);
    cpus_read_unlock();
    vfree(addresses);
    return error;
}

static int msu_management_write(struct msu_management_session *session,
    const maplesu_management_v3 *operation, maplesu_response_owner_v3 *response)
{
    struct msu_management_resource *resource = NULL;
    unsigned char *bytes;
    unsigned long flags;
    bool protected;
    u32 written;
    int error = 0;
    if (operation->handle) {
        resource = msu_management_resource(session, operation->handle);
        if (!resource) return -ESTALE;
        if (resource->publication && resource->publication->service) return -EBUSY;
        if (operation->address > resource->uploaded ||
            operation->address > resource->bytes ||
            operation->length > resource->bytes - operation->address) return -EINVAL;
    } else if (operation->address < 0xffff000000000000ULL ||
        operation->address > ~0ULL - (operation->length - 1)) return -EINVAL;
    bytes = vmalloc(operation->flags & MAPLESU_MANAGEMENT_WRITE_TEXT ?
        ALIGN(operation->length, sizeof(u32)) : operation->length);
    if (!bytes) return -ENOMEM;
    if (copy_from_user_nofault(bytes, msu_management_user_pointer(operation->data_user),
        operation->length)) { error = -EFAULT; goto out; }
    error = msu_management_check(session);
    if (error) goto out;
    if (operation->flags & MAPLESU_MANAGEMENT_WRITE_TEXT) {
        error = msu_management_write_text(session, operation, (u32 *)bytes, response);
    } else if (resource) {
        memcpy((unsigned char *)resource->address + operation->address,
            bytes, operation->length);
        if (operation->address + operation->length > resource->uploaded)
            resource->uploaded = operation->address + operation->length;
        response->result = operation->length;
        response->metadata_length |= MAPLESU_EFFECT_WRITTEN;
    } else {
        mutex_lock(&text_mutex);
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        protected = msu_management_protected(operation->address, operation->length);
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        error = protected ? -EBUSY : msu_management_check(session);
        if (error) { mutex_unlock(&text_mutex); goto out; }
        /* One-byte nofault writes make a fault's confirmed prefix exact. */
        for (written = 0; written < operation->length; ++written) {
            if (copy_to_kernel_nofault((void *)(unsigned long)(operation->address + written),
                bytes + written, 1)) { error = -EFAULT; break; }
            response->result = written + 1;
            response->metadata_length |= MAPLESU_EFFECT_WRITTEN;
        }
        mutex_unlock(&text_mutex);
    }
out:
    vfree(bytes);
    return error;
}

static bool msu_publication_code(u32 role)
{
    return role == MAPLESU_PUBLICATION_GATE_CODE || role == MAPLESU_PUBLICATION_PAYLOAD_CODE;
}

static int msu_publication_release_control(struct msu_publication *publication, u32 *effects)
{
    unsigned long flags;
    int error;
    if (!publication->control) return 0;
    error = set_memory_nx((unsigned long)publication->control, 1);
    if (!error) error = set_memory_rw((unsigned long)publication->control, 1);
    if (error) {
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        publication->quarantined = true;
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        *effects |= MAPLESU_EFFECT_QUARANTINED;
        return error;
    }
    vfree(publication->control);
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    publication->control = NULL;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    *effects |= MAPLESU_EFFECT_RELEASED;
    return 0;
}

static int msu_publication_cancel_session(struct msu_management_session *session, u32 *effects)
{
    unsigned int i;
    int first_error = 0;
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_publication *publication = &management.publications[i];
        int error;
        if (READ_ONCE(publication->session) != session) continue;
        mutex_lock(&publication->operation);
        error = publication->session == session && !publication->ever_exposed ?
            msu_publication_release_unexposed(publication, effects) : 0;
        mutex_unlock(&publication->operation);
        if (!first_error) first_error = error;
    }
    return first_error;
}

static int msu_publication_descriptor_check(const maplesu_publication_prepare_v3 *descriptor)
{
    if (descriptor->flags || descriptor->reserved[0] || descriptor->reserved[1] ||
        !descriptor->member_count || descriptor->member_count > MSU_MANAGEMENT_SLOTS ||
        descriptor->gate_index >= descriptor->member_count ||
        (descriptor->entry_address & 3) || descriptor->entry_address > ~0ULL - 4 ||
        (descriptor->gate_entry_offset & 3) || (descriptor->payload_entry & 3)) return -EINVAL;
    return 0;
}

static int msu_publication_entry_check(const maplesu_publication_prepare_v3 *descriptor)
{
    struct module *module;
    unsigned long entry = (unsigned long)descriptor->entry_address;
    int error = 0;
    preempt_disable();
    if (!core_kernel_text(entry) || !core_kernel_text(entry + 4)) {
        module = __module_text_address(entry);
        if (!module || __module_text_address(entry + 4) != module)
            error = -EOPNOTSUPP;
    }
    preempt_enable();
    return error;
}

static int msu_publication_prepare_snapshot(struct msu_management_session *session,
    struct msu_runtime_service *service, const maplesu_publication_prepare_v3 *input,
    const maplesu_publication_member_v3 *members, maplesu_response_owner_v3 *response)
{
    maplesu_publication_prepare_v3 descriptor = *input;
    struct msu_publication *publication = NULL;
    struct module *target_module = NULL;
    unsigned long flags;
    unsigned int i, j, gate_count = 0;
    bool payload_found = false;
    int error = msu_publication_descriptor_check(&descriptor);
    _Static_assert(MAPLESU_PRIVATE_RETURN_CONTROL_OFFSET >= sizeof(maplesu_publication_control_v3),
        "private return control follows public control");
    _Static_assert(MAPLESU_PRIVATE_RETURN_CONTROL_OFFSET +
        sizeof(maplesu_publication_return_private) <= PAGE_SIZE, "private return control fits page");
    if (error) return error;
    error = msu_publication_entry_check(&descriptor);
    if (error) return error;
    if (service && !msu_runtime_service_entry(service, descriptor.entry_address)) return -EPERM;
    /* Pin the exact target before reserving a closure. Native PUBLISH will
     * acquire its own text-owner lease; this one closes the PREPARE/PUBLISH
     * unload-and-reuse window and remains until the complete gate is drained. */
    preempt_disable();
    if (!core_kernel_text(descriptor.entry_address) ||
        !core_kernel_text(descriptor.entry_address + 4)) {
        target_module = __module_text_address(descriptor.entry_address);
        if (!target_module || __module_text_address(descriptor.entry_address + 4) != target_module ||
            !try_module_get(target_module)) {
            target_module = NULL;
            error = -ENODEV;
        }
    }
    preempt_enable();
    if (error) return error;
    error = -EINVAL;
    for (i = 0; i < descriptor.member_count; ++i) {
        struct msu_management_resource *resource;
        bool shared = members[i].role == MAPLESU_PUBLICATION_SHARED_BOOL;
        if (members[i].reserved || members[i].role < MAPLESU_PUBLICATION_GATE_CODE ||
            members[i].role > MAPLESU_PUBLICATION_SHARED_BOOL) goto out;
        for (j = 0; j < i; ++j)
            if (members[j].handle == members[i].handle &&
                (members[j].role == MAPLESU_PUBLICATION_SHARED_BOOL) == shared) goto out;
        if (shared) {
            error = msu_shared_bool_acquire_domain(session, service,
                members[i].handle, members[i].address, NULL);
            if (error) goto out;
            error = -EINVAL;
            continue;
        }
        if (members[i].address) goto out;
        resource = msu_management_resource_domain(session, service, members[i].handle);
        if (!resource) { error = -ESTALE; goto out; }
        if (resource->publication) { error = -EBUSY; goto out; }
        if (msu_publication_code(members[i].role) !=
            (resource->backend != MSU_MANAGEMENT_VMALLOC)) goto out;
        if (members[i].role == MAPLESU_PUBLICATION_GATE_CODE) {
            ++gate_count;
            if (i != descriptor.gate_index || resource->bytes < 4 ||
                descriptor.gate_entry_offset > resource->bytes - 4) goto out;
        }
        if (members[i].role == MAPLESU_PUBLICATION_PAYLOAD_CODE &&
            resource->bytes >= 4 &&
            descriptor.payload_entry >= (unsigned long)resource->address &&
            descriptor.payload_entry - (unsigned long)resource->address <= resource->bytes - 4)
            payload_found = true;
    }
    if (gate_count != 1 || !payload_found) goto out;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_publication *slot = &management.publications[i];
        bool reusable = !slot->reserved || (slot->payload_released && !slot->quarantined &&
            !slot->control && !slot->private_return_pool && !slot->references &&
            !slot->private_return_count && (!slot->ever_exposed ||
                (slot->entry_restored && slot->entry_drained && slot->gate_drained)));
        if (!reusable || work_busy(&slot->cleanup) || !mutex_trylock(&slot->operation)) continue;
        publication = slot;
        break;
    }
    if (!publication || !management.next_publication) {
        if (publication) mutex_unlock(&publication->operation);
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        error = -ENOSPC;
        goto out;
    }
    /* Locks/work retain their initialized identity. All generation-dependent
     * fields reset before a monotonically new ID makes the slot visible. Old
     * callers revalidate their ID after taking operation, never act on reuse. */
    {
        size_t offset = offsetof(struct msu_publication, cleanup) + sizeof(publication->cleanup);
        memset((u8 *)publication + offset, 0, sizeof(*publication) - offset);
    }
    publication->reserved = true;
    publication->id = management.next_publication++;
    publication->session = session;
    publication->service = service;
    publication->epoch = session ? session->epoch : 0;
    publication->legacy = session && session->legacy_gateway;
    publication->target_module = target_module;
    target_module = NULL;
    publication->entry = descriptor.entry_address;
    publication->original = descriptor.expected_instruction;
    publication->payload = descriptor.payload_entry;
    INIT_LIST_HEAD(&publication->private_return_owners);
    publication->private_return_count = 0;
    publication->private_return_error = 0;
    publication->private_return_failures = 0;
    publication->private_actor_module = NULL;
    publication->private_actor_attached = false;
    publication->private_return_pool = NULL;
    publication->entry_drained = publication->gate_drained = false;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    mutex_lock(&text_mutex);
    error = msu_management_construction_check(session, service);
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    for (i = 0; !error && i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_publication *other = &management.publications[i];
        if (other != publication && other->reserved && !other->canceled &&
            !(other->entry_restored && other->payload_released && !other->enabled && !other->quarantined) &&
            other->entry == publication->entry) error = -EBUSY;
    }
    publication->count = descriptor.member_count;
    for (i = 0; i < descriptor.member_count; ++i) {
        struct msu_management_resource *resource;
        publication->members[i].role = members[i].role;
        if (members[i].role == MAPLESU_PUBLICATION_SHARED_BOOL) continue;
        resource = msu_management_resource_domain(session, service, members[i].handle);
        publication->members[i].resource = resource;
        resource->publication = publication;
        if (members[i].role == MAPLESU_PUBLICATION_GATE_CODE)
            publication->gate = (unsigned long)resource->address + descriptor.gate_entry_offset;
    }
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    mutex_unlock(&text_mutex);
    if (error) goto cancel;
    publication->private_return_pool = kcalloc(MSU_MANAGEMENT_SLOTS,
        sizeof(*publication->private_return_pool), GFP_KERNEL);
    if (!publication->private_return_pool) { error = -ENOMEM; goto cancel; }
    for (i = 0; i < descriptor.member_count; ++i) {
        if (members[i].role != MAPLESU_PUBLICATION_SHARED_BOOL) continue;
        error = msu_shared_bool_acquire_domain(session, service, members[i].handle, members[i].address,
            &publication->members[i].shared_bool);
        if (error) goto cancel;
    }
    if (!msu_management_branch(publication->entry, publication->gate, &publication->branch)) {
        error = -ERANGE;
        goto cancel;
    }
    {
        void *control = vmalloc(PAGE_SIZE);
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        publication->control = control;
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    }
    if (!publication->control) { error = -ENOMEM; goto cancel; }
    memset(publication->control, 0, PAGE_SIZE);
    *publication->control = (maplesu_publication_control_v3) {
        .publication_id = publication->id,
        .enter_function = (unsigned long)msu_publication_enter,
        .leave_function = (unsigned long)msu_publication_leave,
        .payload_entry = publication->payload,
        .entry_address = publication->entry,
        .resume_address = publication->entry + 4,
        .schema = 1, .length = sizeof(*publication->control), .epoch = publication->epoch,
    };
    *(maplesu_publication_return_private *)((u8 *)publication->control +
        MAPLESU_PRIVATE_RETURN_CONTROL_OFFSET) = (maplesu_publication_return_private) {
        .publication_id = publication->id,
        .schema = MAPLESU_PRIVATE_RETURN_CONTROL_SCHEMA,
        .length = sizeof(maplesu_publication_return_private),
        .acquire_function = (unsigned long)msu_private_return_owner_acquire,
        .release_function = (unsigned long)msu_private_return_owner_release,
        .caller_x28_function = (unsigned long)msu_private_return_caller_x28,
        .caller_lr_function = (unsigned long)msu_private_return_caller_lr,
        .status_function = (unsigned long)msu_private_return_status,
        .actor_attach_function = (unsigned long)msu_private_actor_attach,
        .payload_function = (unsigned long)msu_private_return_payload,
    };
    error = set_memory_nx((unsigned long)publication->control, 1);
    if (!error) error = set_memory_ro((unsigned long)publication->control, 1);
    if (error) goto cancel;
    error = msu_management_construction_check(session, service);
    if (error) goto cancel;
    if (service) {
        for (i = 0; i < publication->count; ++i) {
            struct msu_shared_bool *shared = publication->members[i].shared_bool;
            if (!shared) continue;
            mutex_lock(&shared->operation);
            if (shared->creator_reference && !shared->creator)
                error = msu_shared_bool_drop_creator(shared, NULL, &response->metadata_length);
            mutex_unlock(&shared->operation);
            if (error) goto cancel;
        }
    }
    response->result = publication->id;
    response->metadata_address = (unsigned long)publication->control;
    response->metadata_length |= MAPLESU_EFFECT_PUBLICATION_PREPARED;
    mutex_unlock(&publication->operation);
    goto out;
cancel:
    {
        int cleanup_error = msu_publication_release_unexposed(publication, &response->metadata_length);
        (void)cleanup_error;
    }
    mutex_unlock(&publication->operation);
out:
    if (target_module) module_put(target_module);
    return error;
}

static int msu_publication_prepare(struct msu_management_session *session,
    const maplesu_management_v3 *operation, maplesu_response_owner_v3 *response)
{
    maplesu_publication_prepare_v3 descriptor;
    maplesu_publication_member_v3 *members;
    int error;
    if (copy_from_user_nofault(&descriptor, msu_management_user_pointer(operation->data_user),
        sizeof(descriptor))) return -EFAULT;
    error = msu_publication_descriptor_check(&descriptor);
    if (error) return error;
    if (!msu_management_user_range(descriptor.members_user,
            descriptor.member_count * sizeof(*members))) return -EINVAL;
    error = msu_publication_entry_check(&descriptor);
    if (error) return error;
    members = vmalloc(descriptor.member_count * sizeof(*members));
    if (!members) return -ENOMEM;
    if (copy_from_user_nofault(members, msu_management_user_pointer(descriptor.members_user),
        descriptor.member_count * sizeof(*members))) error = -EFAULT;
    else error = msu_publication_prepare_snapshot(session, NULL, &descriptor, members, response);
    vfree(members);
    return error;
}

static int msu_publication_release_unexposed(struct msu_publication *publication, u32 *effects)
{
    unsigned long flags;
    unsigned int i;
    int first_error = 0;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    publication->enabled = false;
    publication->canceled = true;
    publication->session = NULL;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    for (i = 0; i < publication->count; ++i) {
        struct msu_management_resource *resource = publication->members[i].resource;
        int error;
        if (publication->members[i].shared_bool) {
            error = msu_shared_bool_put(publication->members[i].shared_bool, effects);
            publication->members[i].shared_bool = NULL;
            if (!first_error) first_error = error;
        }
        if (!resource) continue;
        error = msu_management_release(resource, effects);
        publication->members[i].resource = NULL;
        if (!first_error) first_error = error;
    }
    {
        int error = msu_publication_release_control(publication, effects);
        if (!first_error) first_error = error;
    }
    if (!first_error) {
        kfree(publication->private_return_pool);
        publication->private_return_pool = NULL;
    }
    if (first_error) {
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        publication->quarantined = true;
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    } else {
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        publication->payload_released = true;
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        msu_private_actor_release(publication);
        if (publication->target_module) {
            module_put(publication->target_module);
            publication->target_module = NULL;
        }
    }
    return first_error;
}

static int msu_publication_publish(struct msu_management_session *session,
    struct msu_publication *publication, maplesu_response_owner_v3 *response)
{
    struct msu_runtime_service *service = session ? NULL : publication->service;
    unsigned long flags;
    unsigned int i;
    void *entry = (void *)(unsigned long)publication->entry;
    u32 observed;
    int error;
    if (!(msu_management_capabilities() & MAPLESU_CAP_PUBLICATION)) return -EOPNOTSUPP;
    if (publication->canceled || publication->ever_exposed || publication->retire_requested ||
        publication->session != session ||
        (session ? publication->epoch != session->epoch : !service)) return -ESTALE;
    if (publication->private_actor_attached) {
        if (copy_from_kernel_nofault(&observed,
                (void *)(unsigned long)(publication->private_actor_target - 4), sizeof(observed)))
            return -EFAULT;
        if (observed != publication->private_actor_kcfi ||
            memcmp((void *)(unsigned long)publication->private_actor_allocation,
                &publication->private_actor_kcfi, sizeof(observed))) return -ESTALE;
    }
    for (i = 0; i < publication->count; ++i) {
        struct msu_management_resource *resource = publication->members[i].resource;
        if (publication->members[i].role == MAPLESU_PUBLICATION_SHARED_BOOL) {
            if (!publication->members[i].shared_bool) return -ESTALE;
            raw_spin_lock_irqsave(&msu_management_lock, flags);
            error = publication->members[i].shared_bool->service != publication->service ? -ESTALE : 0;
            raw_spin_unlock_irqrestore(&msu_management_lock, flags);
            if (error) return error;
            error = msu_shared_bool_acquire_domain(session, service, publication->members[i].shared_bool->id,
                publication->members[i].shared_bool->published_address, NULL);
            if (error) return error;
            continue;
        }
        if (!resource || resource->uploaded != resource->bytes || resource->quarantined) return -EINVAL;
    }
    for (i = 0; i < publication->count; ++i) {
        struct msu_management_resource *resource = publication->members[i].resource;
        if (publication->members[i].role == MAPLESU_PUBLICATION_SHARED_BOOL) continue;
        if (msu_publication_code(publication->members[i].role)) {
            error = set_memory_ro((unsigned long)resource->address, resource->pages);
            if (!error) error = set_memory_x((unsigned long)resource->address, resource->pages);
            if (!error) flush_icache_range((unsigned long)resource->address,
                (unsigned long)resource->address + resource->bytes);
        } else {
            error = set_memory_nx((unsigned long)resource->address, resource->pages);
            if (!error) error = set_memory_rw((unsigned long)resource->address, resource->pages);
        }
        if (error) goto unexposed;
    }
#if defined(MSU_MANAGEMENT_NATIVE_TEXT_OWNER) && MSU_MANAGEMENT_NATIVE_TEXT_OWNER
    error = msu_management_construction_check(session, service);
    if (error) goto unexposed;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    if (!msu_management_construction_live(session, service) ||
        !msu_publication_lifetime_valid(publication)) {
        error = -ESTALE;
    } else {
        /* Even a failed native store may expose a gate. Close admission until
         * claim returns; the native record retains target text throughout. */
        publication->ever_exposed = true;
    }
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    if (error) goto unexposed;
    {
        u32 native_effects = 0;
        error = kprobe_text_owner_claim(entry, publication->original, publication->branch,
            publication, &native_effects);
        if (native_effects & KPROBE_TEXT_OWNER_ATTEMPTED)
            response->metadata_length |= MAPLESU_EFFECT_TEXT_ATTEMPTED;
        if (native_effects & KPROBE_TEXT_OWNER_WRITTEN)
            response->metadata_length |= MAPLESU_EFFECT_WRITTEN;
        if (!error && (native_effects & KPROBE_TEXT_OWNER_WRITTEN)) response->result = 4;
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        publication->native_text_owned = !!(native_effects & KPROBE_TEXT_OWNER_RETAINED);
        if (publication->native_text_owned) {
            publication->session = NULL;
            for (i = 0; i < publication->count; ++i)
                if (publication->members[i].resource)
                    WRITE_ONCE(publication->members[i].resource->session, NULL);
        } else publication->ever_exposed = false;
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        if (!publication->native_text_owned) goto unexposed;
    }
    response->metadata_length |= MAPLESU_EFFECT_PUBLICATION_RETAINED;
    /* Native armed BRK requires no physical write: logical B is published. */
    if (!error) response->metadata_length |= MAPLESU_EFFECT_PUBLICATION_PUBLISHED;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    if (!error && !publication->retire_requested && msu_management_construction_live(session, service) &&
        msu_publication_lifetime_valid(publication)) {
        publication->enabled = true;
    } else {
        if (!error) error = -ESTALE;
        publication->enabled = false;
        publication->retire_requested = true;
        schedule_work(&publication->cleanup);
        response->metadata_length |= MAPLESU_EFFECT_PENDING;
    }
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    if (publication->ever_exposed) return error;
#else
    (void)entry;
    (void)flags;
    error = -EOPNOTSUPP;
#endif
unexposed:
    {
        int cleanup_error = msu_publication_release_unexposed(publication, &response->metadata_length);
        (void)cleanup_error;
    }
    return error;
}

/* publication.operation precedes CPU hotplug/text locks; no session is required
 * by trusted invalidation cleanup. Closing admission never waits for payload. */
static int msu_publication_disable(struct msu_publication *publication,
    struct msu_management_session *session, maplesu_response_owner_v3 *response)
{
    unsigned long flags;
    void *entry = (void *)(unsigned long)publication->entry;
    u32 observed;
    bool restored = false, quarantined;
    int error = 0;
    if (publication->canceled) return -ESTALE;
    if (!publication->ever_exposed) return -EINVAL;
    if (publication->payload_released) {
        response->metadata_length |= MAPLESU_EFFECT_RELEASED;
        if (READ_ONCE(publication->quarantined)) {
            response->metadata_length |= MAPLESU_EFFECT_QUARANTINED | MAPLESU_EFFECT_PENDING;
            return -EINPROGRESS;
        }
        return 0;
    }
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    publication->enabled = false;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    response->metadata_length |= MAPLESU_EFFECT_PUBLICATION_RETAINED | MAPLESU_EFFECT_GATE_PINNED;
#if defined(MSU_MANAGEMENT_NATIVE_TEXT_OWNER) && MSU_MANAGEMENT_NATIVE_TEXT_OWNER
    if (!publication->native_text_owned) error = -ESTALE;
    else if (session) error = msu_management_check(session);
    if (!error) {
        u32 native_effects = 0;
        error = kprobe_text_owner_disable(entry, publication, &native_effects);
        if (native_effects & KPROBE_TEXT_OWNER_ATTEMPTED)
            response->metadata_length |= MAPLESU_EFFECT_TEXT_ATTEMPTED;
        if (native_effects & KPROBE_TEXT_OWNER_WRITTEN)
            response->metadata_length |= MAPLESU_EFFECT_WRITTEN;
        if (!error && (native_effects & KPROBE_TEXT_OWNER_WRITTEN)) response->result = 4;
        restored = !error && (native_effects & KPROBE_TEXT_OWNER_DISABLED);
    }
    (void)observed;
#else
    cpus_read_lock();
    mutex_lock(&text_mutex);
    if (session) error = msu_management_check(session);
    if (!error && copy_from_kernel_nofault(&observed, entry, sizeof(observed))) error = -EFAULT;
    if (!error && observed == publication->original) {
        restored = true;
    } else if (!error && observed == publication->branch) {
        error = msu_management_patch_text_locked(session, &entry, &publication->original, 1, response);
        if (!error && response->result == 4) restored = true;
    } else if (!error) {
        /* A third party owns the changed entry. Do not overwrite its word. */
        error = -EBUSY;
    }
    mutex_unlock(&text_mutex);
    cpus_read_unlock();
#endif
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    publication->entry_restored = restored;
    quarantined = publication->quarantined;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    if (error || !restored) response->metadata_length |= MAPLESU_EFFECT_PENDING;
    if (quarantined) {
        response->metadata_length |= MAPLESU_EFFECT_PENDING | MAPLESU_EFFECT_QUARANTINED;
        if (!error) error = -EINPROGRESS;
    }
    return error;
}

/* Same trampoline contract as native ftrace: a forced work item on each CPU
 * drains IRQ/idle/NMI execution already ordered before the text rendezvous;
 * Tasks RCU additionally waits out involuntarily preempted gate instructions.
 * Gate prefixes/fallbacks and post-leave suffixes never voluntarily sleep.
 * Sleepable original/callback intervals are covered by the explicit owners. */
static void msu_publication_cpu_rendezvous(struct work_struct *work)
{
    (void)work;
}

static int msu_publication_gate_grace(void)
{
    int error = schedule_on_each_cpu(msu_publication_cpu_rendezvous);
    if (error) return error;
    synchronize_rcu_tasks();
    return 0;
}

static int msu_publication_retire(struct msu_publication *publication,
    struct msu_management_session *session, maplesu_response_owner_v3 *response)
{
    unsigned long flags;
    unsigned int i;
    int error, first_error = 0;
    bool pending;
    if (!publication->ever_exposed) {
        if (publication->canceled) {
            if (READ_ONCE(publication->quarantined)) response->metadata_length |= MAPLESU_EFFECT_QUARANTINED;
            else response->metadata_length |= MAPLESU_EFFECT_RELEASED;
            return READ_ONCE(publication->quarantined) ? -EIO : 0;
        }
        if (publication->session != session) return -ESTALE;
        return msu_publication_release_unexposed(publication, &response->metadata_length);
    }
    if (publication->payload_released) {
        response->metadata_length |= MAPLESU_EFFECT_RELEASED;
        if (READ_ONCE(publication->quarantined)) {
            response->metadata_length |= MAPLESU_EFFECT_QUARANTINED | MAPLESU_EFFECT_PENDING;
            return -EINPROGRESS;
        }
        return 0;
    }
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    publication->enabled = false;
    publication->retire_requested = true;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    error = msu_publication_disable(publication, session, response);
    if (error) return error;
    if (!publication->entry_drained) {
        error = msu_publication_gate_grace();
        if (error) { response->metadata_length |= MAPLESU_EFFECT_PENDING; return error; }
        publication->entry_drained = true;
    }
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    pending = publication->references || publication->private_return_count ||
        !publication->entry_restored || publication->quarantined;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    if (pending) {
        response->metadata_length |= MAPLESU_EFFECT_PENDING;
        if (READ_ONCE(publication->quarantined)) response->metadata_length |= MAPLESU_EFFECT_QUARANTINED;
        return -EINPROGRESS;
    }
    if (!publication->gate_drained) {
        error = msu_publication_gate_grace();
        if (error) { response->metadata_length |= MAPLESU_EFFECT_PENDING; return error; }
        publication->gate_drained = true;
    }
#if defined(MSU_MANAGEMENT_NATIVE_TEXT_OWNER) && MSU_MANAGEMENT_NATIVE_TEXT_OWNER
    if (publication->native_text_owned) {
        u32 native_effects = 0;
        error = kprobe_text_owner_release((void *)(unsigned long)publication->entry,
            publication, &native_effects);
        if (error || !(native_effects & KPROBE_TEXT_OWNER_RELEASED)) {
            response->metadata_length |= MAPLESU_EFFECT_PENDING;
            return error ? error : -EIO;
        }
        publication->native_text_owned = false;
    }
#endif
    for (i = 0; i < publication->count; ++i) {
        struct msu_management_resource *resource = publication->members[i].resource;
        int release_error;
        if (publication->members[i].shared_bool) {
            release_error = msu_shared_bool_put(publication->members[i].shared_bool,
                &response->metadata_length);
            publication->members[i].shared_bool = NULL;
            if (release_error) {
                raw_spin_lock_irqsave(&msu_management_lock, flags);
                publication->quarantined = true;
                raw_spin_unlock_irqrestore(&msu_management_lock, flags);
                if (!first_error) first_error = release_error;
            }
        }
        if (!resource) continue;
        release_error = msu_management_release(resource, &response->metadata_length);
        if (release_error) {
            raw_spin_lock_irqsave(&msu_management_lock, flags);
            publication->quarantined = true;
            raw_spin_unlock_irqrestore(&msu_management_lock, flags);
            if (!first_error) first_error = release_error;
        } else publication->members[i].resource = NULL;
    }
    if (first_error) {
        response->metadata_length |= MAPLESU_EFFECT_PENDING;
        return first_error;
    }
    error = msu_publication_release_control(publication, &response->metadata_length);
    if (error) {
        response->metadata_length |= MAPLESU_EFFECT_PENDING | MAPLESU_EFFECT_QUARANTINED;
        return error;
    }
    kfree(publication->private_return_pool);
    publication->private_return_pool = NULL;
    msu_private_actor_release(publication);
    if (publication->target_module) {
        module_put(publication->target_module);
        publication->target_module = NULL;
    }
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    publication->payload_released = true;
    pending = publication->quarantined;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    response->metadata_length &= ~(MAPLESU_EFFECT_PUBLICATION_RETAINED | MAPLESU_EFFECT_GATE_PINNED);
    response->metadata_length |= MAPLESU_EFFECT_RELEASED;
    if (pending) response->metadata_length |= MAPLESU_EFFECT_PENDING | MAPLESU_EFFECT_QUARANTINED;
    return pending ? -EINPROGRESS : 0;
}

void msu_publication_cleanup(struct work_struct *work)
{
    struct msu_publication *publication = container_of(work, struct msu_publication, cleanup);
    struct msu_management_session *session = NULL;
    struct msu_runtime_service *service = NULL;
    maplesu_response_owner_v3 response = { 0 };
    unsigned long flags;
    /* Unexposed uploads use session.operation, so cancellation must take it
     * before publication.operation. Do not resurrect a queued session free. */
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    if (!publication->ever_exposed && !publication->canceled && publication->retire_requested &&
        publication->session && publication->session->references &&
        publication->session->references != ~0U) {
        session = publication->session;
        ++session->references;
    }
    if (!publication->ever_exposed && !publication->session && publication->service &&
        publication->service->factory_owned) service = publication->service;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    if (session) mutex_lock(&session->operation);
    if (service) mutex_lock(&service->operation);
    mutex_lock(&publication->operation);
    if (READ_ONCE(publication->retire_requested)) {
        if (publication->ever_exposed) msu_publication_retire(publication, NULL, &response);
        else if ((session && publication->session == session) ||
            (service && publication->service == service))
            msu_publication_release_unexposed(publication, &response.metadata_length);
    }
    mutex_unlock(&publication->operation);
    if (service) mutex_unlock(&service->operation);
    if (session) {
        mutex_unlock(&session->operation);
        msu_management_put(session);
    }
}

static void msu_runtime_construction_unlock(struct msu_runtime_service *service)
{
    unsigned long flags;
    mutex_unlock(&service->operation);
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    --service->operations;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
}

static int msu_runtime_construction_lock(const maplesu_host_instance *host, u64 id,
    const void *owner, bool mutable, struct msu_runtime_service **output)
{
    struct msu_runtime_service *service;
    unsigned long flags;
    unsigned int i;
    int error = 0;
    *output = NULL;
    if (!msu_management_sleepable()) return -EWOULDBLOCK;
    if (!host || !id || !owner || !msu_management_host_matches(*host)) return -EINVAL;
    if (!(msu_management_capabilities() & MAPLESU_CAP_PUBLICATION)) return -EOPNOTSUPP;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    service = msu_runtime_service_find(id);
    if (!READ_ONCE(management.ready) || !service || service->lifecycle_owner != owner) error = -ESTALE;
    else if (service->kind == MSU_RUNTIME_SERVICE_EARLY_HELPER) error = -EPERM;
    else if (service->operations == ~0U) error = -EOVERFLOW;
    else ++service->operations;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    if (error) return error;
    mutex_lock(&service->operation);
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    if (!READ_ONCE(management.ready) || !msu_runtime_service_live(service)) error = -ESTALE;
    else if (mutable && service->sealed) error = -EBUSY;
    else if (!service->factory_owned) {
        for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i)
            if (management.publications[i].reserved && management.publications[i].service == service) {
                error = -EBUSY;
                break;
            }
        if (!error) service->factory_owned = true;
    }
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    if (error) msu_runtime_construction_unlock(service);
    else *output = service;
    return error;
}

int msu_runtime_service_alloc(const maplesu_host_instance *host, u64 id,
    const void *owner, u32 bytes, bool code, u64 near_entry,
    maplesu_response_owner_v3 *response)
{
    struct msu_runtime_service *service;
    maplesu_management_v3 operation = { .length = bytes,
        .flags = code ? MAPLESU_MANAGEMENT_ALLOC_CODE : 0, .address = near_entry };
    int error;
    if (!response) return -EINVAL;
    memset(response, 0, sizeof(*response));
    if (!bytes || bytes > MAPLESU_MANAGEMENT_MAX_BYTES || (!code && near_entry))
        return response->status = -EINVAL;
    error = msu_runtime_construction_lock(host, id, owner, true, &service);
    if (error) return response->status = error;
    if (code && !(msu_management_capabilities() & MAPLESU_CAP_CODE_RESERVE)) error = -EOPNOTSUPP;
    else if (near_entry && !msu_runtime_service_entry(service, near_entry)) error = -EPERM;
    else error = msu_management_alloc_domain(NULL, service, &operation, response);
    if (!error) error = msu_management_construction_check(NULL, service);
    msu_runtime_construction_unlock(service);
    return response->status = error;
}

int msu_runtime_service_write(const maplesu_host_instance *host, u64 id,
    const void *owner, u64 handle, u32 offset, const void *source, u32 length,
    maplesu_response_owner_v3 *response)
{
    struct msu_runtime_service *service;
    struct msu_management_resource *resource;
    struct msu_publication *publication = NULL;
    void *bytes;
    int error;
    if (!response) return -EINVAL;
    memset(response, 0, sizeof(*response));
    if (!msu_management_sleepable()) return response->status = -EWOULDBLOCK;
    if (!handle || !source || !length || length > MAPLESU_MANAGEMENT_MAX_BYTES)
        return response->status = -EINVAL;
    bytes = vmalloc(length);
    if (!bytes) return response->status = -ENOMEM;
    if (copy_from_kernel_nofault(bytes, source, length)) { error = -EFAULT; goto free_bytes; }
    error = msu_runtime_construction_lock(host, id, owner, true, &service);
    if (error) goto free_bytes;
    resource = msu_management_resource_domain(NULL, service, handle);
    if (!resource) { error = -ESTALE; goto unlock; }
    publication = resource->publication;
    if (publication) mutex_lock(&publication->operation);
    if ((publication && (publication->service != service || publication->ever_exposed ||
            publication->canceled || publication->retire_requested || publication->quarantined)) ||
        offset > resource->uploaded || offset > resource->bytes || length > resource->bytes - offset) {
        error = -EINVAL;
        goto unlock_publication;
    }
    error = msu_management_construction_check(NULL, service);
    if (error) goto unlock_publication;
    memcpy((u8 *)resource->address + offset, bytes, length);
    if (offset + length > resource->uploaded) resource->uploaded = offset + length;
    response->result = length;
    response->metadata_length |= MAPLESU_EFFECT_WRITTEN;
    error = msu_management_construction_check(NULL, service);
unlock_publication:
    if (publication) mutex_unlock(&publication->operation);
unlock:
    msu_runtime_construction_unlock(service);
free_bytes:
    vfree(bytes);
    return response->status = error;
}

int msu_runtime_service_shared_bool_create(const maplesu_host_instance *host,
    u64 id, const void *owner, u8 value, maplesu_response_owner_v3 *response)
{
    struct msu_runtime_service *service;
    int error;
    if (!response) return -EINVAL;
    memset(response, 0, sizeof(*response));
    if (value > 1) return response->status = -EINVAL;
    error = msu_runtime_construction_lock(host, id, owner, true, &service);
    if (error) return response->status = error;
    error = msu_shared_bool_create_domain(NULL, service, value, response);
    if (!error) error = msu_management_construction_check(NULL, service);
    msu_runtime_construction_unlock(service);
    return response->status = error;
}

int msu_runtime_service_shared_bool_store(const maplesu_host_instance *host,
    u64 id, const void *owner, u64 shared_id, u8 value,
    maplesu_response_owner_v3 *response)
{
    struct msu_runtime_service *service;
    struct msu_shared_bool *shared;
    unsigned long flags;
    int error;
    if (!response) return -EINVAL;
    memset(response, 0, sizeof(*response));
    response->metadata_address = shared_id;
    if (value > 1 || !shared_id) return response->status = -EINVAL;
    error = msu_runtime_construction_lock(host, id, owner, false, &service);
    if (error) return response->status = error;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    shared = msu_shared_bool_find(shared_id);
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    if (!shared) { error = -ESTALE; goto out; }
    mutex_lock(&shared->operation);
    error = msu_management_construction_check(NULL, service);
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    if (!error && (!msu_runtime_service_live(service) || shared->service != service ||
        !shared->address || !shared->references || shared->quarantined ||
        shared->published_address != (unsigned long)shared->address)) error = -ESTALE;
    if (!error) {
        WRITE_ONCE(*shared->address, value);
        response->metadata_length |= MAPLESU_EFFECT_WRITTEN;
        if (READ_ONCE(*shared->address) == value) response->result = 1;
        else error = -EIO;
    }
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    mutex_unlock(&shared->operation);
out:
    msu_runtime_construction_unlock(service);
    return response->status = error;
}

int msu_runtime_service_prepare(const maplesu_host_instance *host, u64 id,
    const void *owner, const maplesu_publication_prepare_v3 *input,
    const maplesu_publication_member_v3 *input_members, maplesu_response_owner_v3 *response)
{
    struct msu_runtime_service *service;
    maplesu_publication_prepare_v3 descriptor;
    maplesu_publication_member_v3 *members;
    int error;
    if (!response) return -EINVAL;
    memset(response, 0, sizeof(*response));
    if (!msu_management_sleepable()) return response->status = -EWOULDBLOCK;
    if (!input || !input_members) return response->status = -EINVAL;
    if (copy_from_kernel_nofault(&descriptor, input, sizeof(descriptor)))
        return response->status = -EFAULT;
    error = msu_publication_descriptor_check(&descriptor);
    if (error) return response->status = error;
    if (descriptor.members_user) return response->status = -EINVAL;
    error = msu_publication_entry_check(&descriptor);
    if (error) return response->status = error;
    members = vmalloc(descriptor.member_count * sizeof(*members));
    if (!members) return response->status = -ENOMEM;
    if (copy_from_kernel_nofault(members, input_members, descriptor.member_count * sizeof(*members))) {
        error = -EFAULT;
        goto free_members;
    }
    error = msu_runtime_construction_lock(host, id, owner, true, &service);
    if (error) goto free_members;
    error = msu_publication_prepare_snapshot(NULL, service, &descriptor, members, response);
    msu_runtime_construction_unlock(service);
free_members:
    vfree(members);
    return response->status = error;
}

/* No entry is exposed while any registered entry or owned allocation lacks its
 * complete closure. The same service mutex excludes all later factory writes. */
static int msu_runtime_construction_seal(struct msu_runtime_service *service)
{
    unsigned long flags;
    unsigned int i, j;
    int error = 0;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    if (!msu_runtime_service_live(service)) { error = -ESTALE; goto out; }
    if (service->sealed) goto out;
    for (i = 0; i < service->entry_count; ++i) {
        struct msu_publication *publication = NULL;
        for (j = 0; j < MSU_MANAGEMENT_SLOTS; ++j) {
            struct msu_publication *value = &management.publications[j];
            if (value->reserved && value->service == service && value->entry == service->entries[i]) {
                if (publication) { error = -EINVAL; goto out; }
                publication = value;
            }
        }
        if (!publication || publication->session || publication->ever_exposed || publication->canceled ||
            publication->retire_requested || publication->quarantined || !publication->control ||
            !msu_publication_lifetime_valid(publication)) { error = -ESTALE; goto out; }
    }
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_management_resource *resource = &management.resources[i];
        struct msu_shared_bool *shared = &management.shared_bools[i];
        if (resource->service == service && (!resource->publication || resource->consumed ||
            resource->quarantined || resource->uploaded != resource->bytes)) { error = -EINVAL; goto out; }
        if (shared->service == service && (shared->creator_reference || !shared->references ||
            !shared->address || shared->quarantined)) { error = -EINVAL; goto out; }
    }
    service->sealed = true;
out:
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    return error;
}

int msu_runtime_service_publish(const maplesu_host_instance *host, u64 id,
    const void *owner, u64 publication_id, maplesu_response_owner_v3 *response)
{
    struct msu_runtime_service *service;
    struct msu_publication *publication;
    int error;
    if (!response) return -EINVAL;
    memset(response, 0, sizeof(*response));
    if (!publication_id) return response->status = -EINVAL;
    error = msu_runtime_construction_lock(host, id, owner, false, &service);
    if (error) return response->status = error;
    {
        unsigned long flags;
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        publication = msu_publication_find(publication_id);
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    }
    if (!publication || publication->service != service || publication->session) error = -ESTALE;
    else {
        mutex_lock(&publication->operation);
        error = publication->id != publication_id || publication->service != service ||
            publication->session ? -ESTALE : msu_runtime_construction_seal(service);
        if (!error) error = msu_publication_publish(NULL, publication, response);
        mutex_unlock(&publication->operation);
    }
    if (error) {
        u32 effects = 0;
        int retire_error = msu_runtime_service_revoke(host, id, &effects);
        (void)retire_error;
        response->metadata_length |= effects;
    }
    msu_runtime_construction_unlock(service);
    return response->status = error;
}

int msu_runtime_service_abort_unexposed(const maplesu_host_instance *host,
    u64 id, const void *owner, u32 *effects)
{
    struct msu_runtime_service *service;
    unsigned long flags;
    unsigned int i;
    int first_error = 0;
    bool exposed = false, pending = false;
    if (!effects) return -EINVAL;
    *effects = 0;
    if (!msu_management_sleepable()) return -EWOULDBLOCK;
    if (!host || !id || !owner || !msu_management_host_matches(*host)) return -EINVAL;
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    service = msu_runtime_service_find(id);
    if (!READ_ONCE(management.ready) || !service || service->lifecycle_owner != owner) first_error = -ESTALE;
    else if (service->kind == MSU_RUNTIME_SERVICE_EARLY_HELPER) {
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        first_error = msu_runtime_service_revoke(host, id, effects);
        if (!first_error) {
            raw_spin_lock_irqsave(&msu_management_lock, flags);
            service->discarded = true;
            raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        }
        return first_error;
    }
    else if (service->operations == ~0U) first_error = -EOVERFLOW;
    else { service->active = false; ++service->operations; }
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    if (first_error) return first_error;
    mutex_lock(&service->operation);
    if (service->discarded) { *effects |= MAPLESU_EFFECT_RELEASED; goto unlock; }
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_publication *publication = &management.publications[i];
        if (publication->service == service && (publication->ever_exposed || publication->session)) exposed = true;
    }
    if (exposed) {
        msu_runtime_construction_unlock(service);
        first_error = msu_runtime_service_revoke(host, id, effects);
        *effects |= MAPLESU_EFFECT_PENDING;
        return first_error ? first_error : -EINPROGRESS;
    }
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    service->aborting = true;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    mutex_unlock(&service->operation);
    /* Workers take service.operation; drain them with that mutex released.
     * Inactive+aborting forbids new construction and further revoke queues. */
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i)
        if (management.publications[i].service == service)
            cancel_work_sync(&management.publications[i].cleanup);
    mutex_lock(&service->operation);
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_publication *publication = &management.publications[i];
        int error;
        if (publication->service != service) continue;
        mutex_lock(&publication->operation);
        if (publication->ever_exposed || publication->references) {
            pending = true;
            error = -EINPROGRESS;
        } else error = msu_publication_release_unexposed(publication, effects);
        mutex_unlock(&publication->operation);
        if (!first_error) first_error = error;
    }
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_management_resource *resource = &management.resources[i];
        struct msu_shared_bool *shared = &management.shared_bools[i];
        int error = 0;
        if (resource->service == service && !resource->publication && !resource->quarantined)
            error = msu_management_release(resource, effects);
        if (!first_error) first_error = error;
        if (shared->service != service) continue;
        mutex_lock(&shared->operation);
        error = shared->creator_reference && !shared->creator ?
            msu_shared_bool_drop_creator(shared, NULL, effects) : msu_shared_bool_free(shared, effects);
        mutex_unlock(&shared->operation);
        if (!first_error) first_error = error;
    }
    raw_spin_lock_irqsave(&msu_management_lock, flags);
    for (i = 0; i < MSU_MANAGEMENT_SLOTS; ++i) {
        struct msu_publication *publication = &management.publications[i];
        struct msu_management_resource *resource = &management.resources[i];
        struct msu_shared_bool *shared = &management.shared_bools[i];
        if (publication->service == service && (publication->ever_exposed || publication->references ||
            !publication->canceled || !publication->payload_released || publication->control || publication->quarantined))
            pending = true;
        if (resource->service == service && (resource->address || resource->quarantined)) pending = true;
        if (shared->service == service && (shared->address || shared->references ||
            shared->creator_reference || shared->quarantined)) pending = true;
        if ((publication->service == service && publication->quarantined) ||
            (resource->service == service && resource->quarantined) ||
            (shared->service == service && shared->quarantined)) *effects |= MAPLESU_EFFECT_QUARANTINED;
    }
    if (!pending && !first_error) service->discarded = true;
    raw_spin_unlock_irqrestore(&msu_management_lock, flags);
    if (pending || first_error) {
        *effects |= MAPLESU_EFFECT_PENDING;
        if (!first_error) first_error = -EINPROGRESS;
    } else *effects |= MAPLESU_EFFECT_RELEASED; /* This service only; UID teardown is independent. */
unlock:
    msu_runtime_construction_unlock(service);
    return first_error;
}

/* The only CFI exception is this authenticated, complete owned JIT frame. All
 * imported APIs and registered callbacks keep their target function types. */
static u64 __nocfi msu_management_call(void *address)
{
    typedef u64 (*msu_jit_function)(void);
    return ((msu_jit_function)address)();
}

static int msu_management_exec(struct msu_management_session *session,
    const maplesu_management_v3 *operation, maplesu_response_owner_v3 *response)
{
    struct msu_management_resource *resource = msu_management_resource(session, operation->handle);
    int error, release_error;
    if (!resource) return -ESTALE;
    if (resource->publication) return -EBUSY;
    resource->consumed = true;
    if (resource->bytes != operation->length || resource->uploaded != operation->length) {
        error = -EINVAL;
        goto release;
    }
    error = set_memory_ro((unsigned long)resource->address, resource->pages);
    if (!error && !maplesu_standard_frame_valid(resource->address, resource->bytes)) error = -EINVAL;
    if (!error) error = set_memory_x((unsigned long)resource->address, resource->pages);
    if (!error) {
        sync_icache_aliases(
            __builtin_choose_expr(MSU_ICACHE_ADDRESS_LENGTH,
                resource->address, (unsigned long)resource->address),
            __builtin_choose_expr(MSU_ICACHE_ADDRESS_LENGTH,
                (unsigned long)resource->bytes, (unsigned long)resource->address + resource->bytes));
        error = msu_management_check(session);
        if (!error) {
            response->result = msu_management_call(resource->address);
            response->metadata_length |= MAPLESU_EFFECT_EXECUTED;
        }
    }
release:
    release_error = msu_management_release(resource, &response->metadata_length);
    return error ? error : release_error;
}

static kernel_cap_t msu_management_cap(u64 value)
{
    kernel_cap_t result = CAP_EMPTY_SET;
    unsigned int bit;
    for (bit = 0; bit <= CAP_LAST_CAP; ++bit)
        if (value & (1ULL << bit)) cap_raise(result, bit);
    return result;
}

int msu_credential_profile_validate(const struct msu_credential_profile *profile)
{
#ifdef CONFIG_MULTIUSER
    u32 i;
#endif
#if CAP_LAST_CAP < 63
    const u64 valid = (1ULL << (CAP_LAST_CAP + 1)) - 1;
#else
    const u64 valid = ~0ULL;
#endif
    if (!profile || (profile->cap_write_mask & ~MSU_CRED_CAP_ALL) ||
        profile->groups_count > MSU_CRED_PROFILE_MAX_GROUPS ||
        (!profile->replace_groups && profile->groups_count) ||
        !memchr(profile->selinux_domain, 0, sizeof(profile->selinux_domain))) return -EINVAL;
#ifdef CONFIG_MULTIUSER
    if (profile->replace_ids &&
        (!uid_valid(make_kuid(&init_user_ns, profile->uid)) ||
         !gid_valid(make_kgid(&init_user_ns, profile->gid)))) return -EINVAL;
    for (i = 0; i < profile->groups_count; ++i)
        if (!gid_valid(make_kgid(&init_user_ns, profile->groups[i]))) return -EINVAL;
#else
    if (profile->replace_ids || profile->replace_groups) return -EOPNOTSUPP;
#endif
    if (((profile->cap_write_mask & MSU_CRED_CAP_INHERITABLE) && (profile->caps.inheritable & ~valid)) ||
        ((profile->cap_write_mask & MSU_CRED_CAP_PERMITTED) && (profile->caps.permitted & ~valid)) ||
        ((profile->cap_write_mask & MSU_CRED_CAP_EFFECTIVE) && (profile->caps.effective & ~valid)) ||
        ((profile->cap_write_mask & MSU_CRED_CAP_BOUNDING) && (profile->caps.bounding & ~valid)) ||
        ((profile->cap_write_mask & MSU_CRED_CAP_AMBIENT) && (profile->caps.ambient & ~valid))) return -EINVAL;
    if ((profile->cap_write_mask & (MSU_CRED_CAP_EFFECTIVE | MSU_CRED_CAP_PERMITTED)) ==
            (MSU_CRED_CAP_EFFECTIVE | MSU_CRED_CAP_PERMITTED) &&
        (profile->caps.effective & ~profile->caps.permitted)) return -EINVAL;
    if ((profile->cap_write_mask & (MSU_CRED_CAP_AMBIENT | MSU_CRED_CAP_PERMITTED | MSU_CRED_CAP_INHERITABLE)) ==
            (MSU_CRED_CAP_AMBIENT | MSU_CRED_CAP_PERMITTED | MSU_CRED_CAP_INHERITABLE) &&
        (profile->caps.ambient & ~(profile->caps.permitted & profile->caps.inheritable))) return -EINVAL;
    return profile->selinux_domain[0] ? msu_credential_domain_supported() : 0;
}

static int msu_credential_replace_profile(const struct msu_credential_profile *profile,
    int (*validate)(void *context),
    int (*adopt)(void *context, const struct cred *before, const struct cred *after),
    void *context, u32 *effects)
{
    struct task_struct *task = current;
    struct msu_credential_profile snapshot;
    const struct cred *original, *committed = NULL;
    struct mm_struct *mm;
    struct cred *prepared;
    bool prepared_owned = false;
    u64 exec_id;
    int error;
#ifdef CONFIG_MULTIUSER
    u32 i;
#endif
    if (!effects) return -EINVAL;
    *effects = 0;
    if (!msu_management_sleepable()) return -EWOULDBLOCK;
    if (!profile || !validate) return -EINVAL;
    snapshot = *profile;
    profile = &snapshot;
    error = msu_credential_profile_validate(profile);
    if (error) return error;
    if (msu_dynamic_current_cred() != msu_dynamic_current_real_cred()) return -EPERM;
    mm = READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm));
    if (mm) msu_dynamic_mmgrab(mm);
    exec_id = READ_ONCE(MSU_DYN_FIELD(current, task_struct, self_exec_id));
    original = msu_dynamic_get_current_cred();
    error = validate(context);
    if (error) goto put_original;
    if (current != task || READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm)) != mm || READ_ONCE(MSU_DYN_FIELD(current, task_struct, self_exec_id)) != exec_id ||
        msu_dynamic_current_cred() != original || msu_dynamic_current_real_cred() != original) {
        error = -ESTALE;
        goto put_original;
    }
    if ((profile->replace_ids || profile->replace_groups) && MSU_DYN_FIELD(original, cred, user_ns) != &init_user_ns) {
        error = -EOPNOTSUPP;
        goto put_original;
    }
    prepared = prepare_creds();
    if (!prepared) { error = -ENOMEM; goto put_original; }
    prepared_owned = true;
#ifdef CONFIG_MULTIUSER
    if (profile->replace_ids) {
        struct user_struct *root_user;
        const kuid_t uid = make_kuid(&init_user_ns, profile->uid);
        const kgid_t gid = make_kgid(&init_user_ns, profile->gid);
        MSU_DYN_FIELD(prepared, cred, uid) = MSU_DYN_FIELD(prepared, cred, suid) = MSU_DYN_FIELD(prepared, cred, euid) = MSU_DYN_FIELD(prepared, cred, fsuid) = uid;
        MSU_DYN_FIELD(prepared, cred, gid) = MSU_DYN_FIELD(prepared, cred, sgid) = MSU_DYN_FIELD(prepared, cred, egid) = MSU_DYN_FIELD(prepared, cred, fsgid) = gid;
        root_user = alloc_uid(uid);
        if (!root_user) { error = -EAGAIN; goto abort; }
        free_uid(MSU_DYN_FIELD(prepared, cred, user));
        MSU_DYN_FIELD(prepared, cred, user) = root_user;
#if MSU_MANAGEMENT_CRED_UCOUNTS
        error = set_cred_ucounts(prepared);
        if (error) goto abort;
#endif
    }
    if (profile->replace_groups) {
        struct group_info *groups = groups_alloc(profile->groups_count);
        if (!groups) { error = -ENOMEM; goto abort; }
        for (i = 0; i < profile->groups_count; ++i)
            groups->gid[i] = make_kgid(&init_user_ns, profile->groups[i]);
        groups_sort(groups);
        set_groups(prepared, groups);
        /* set_groups retained groups for prepared; only our non-last local
         * reference is released here. Native cred cleanup owns final free. */
        _Generic(&groups->usage, refcount_t *: refcount_dec,
                 atomic_t *: atomic_dec)(&groups->usage);
    }
#endif
    if (profile->clear_securebits) MSU_DYN_FIELD(prepared, cred, securebits) = 0;
    if (profile->cap_write_mask & MSU_CRED_CAP_INHERITABLE)
        MSU_DYN_FIELD(prepared, cred, cap_inheritable) = msu_management_cap(profile->caps.inheritable);
    if (profile->cap_write_mask & MSU_CRED_CAP_PERMITTED)
        MSU_DYN_FIELD(prepared, cred, cap_permitted) = msu_management_cap(profile->caps.permitted);
    if (profile->cap_write_mask & MSU_CRED_CAP_EFFECTIVE)
        MSU_DYN_FIELD(prepared, cred, cap_effective) = msu_management_cap(profile->caps.effective);
    if (profile->cap_write_mask & MSU_CRED_CAP_BOUNDING)
        MSU_DYN_FIELD(prepared, cred, cap_bset) = msu_management_cap(profile->caps.bounding);
    if (profile->cap_write_mask & MSU_CRED_CAP_AMBIENT)
        MSU_DYN_FIELD(prepared, cred, cap_ambient) = msu_management_cap(profile->caps.ambient);
    if (!cap_issubset(MSU_DYN_FIELD(prepared, cred, cap_effective), MSU_DYN_FIELD(prepared, cred, cap_permitted)) ||
        !cap_issubset(MSU_DYN_FIELD(prepared, cred, cap_ambient), cap_intersect(MSU_DYN_FIELD(prepared, cred, cap_permitted), MSU_DYN_FIELD(prepared, cred, cap_inheritable)))) { error = -EINVAL; goto abort; }
    if (profile->selinux_domain[0]) {
        error = msu_credential_domain_prepare(prepared, profile->selinux_domain,
            strnlen(profile->selinux_domain, sizeof(profile->selinux_domain)));
        if (error) goto abort;
    }
    error = validate(context);
    if (error) goto abort;
    if (current != task || READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm)) != mm || READ_ONCE(MSU_DYN_FIELD(current, task_struct, self_exec_id)) != exec_id ||
        msu_dynamic_current_cred() != original || msu_dynamic_current_real_cred() != original) {
        error = -ESTALE;
        goto abort;
    }
    /* commit consumes prepared even if an unexpected target ABI reports an
     * error. No path after this call aborts or writes the old credentials. */
    committed = msu_dynamic_get_cred(prepared);
    error = commit_creds(prepared);
    prepared_owned = false;
    *effects |= MAPLESU_EFFECT_COMMITTED;
    if (!error && (current != task || READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm)) != mm || READ_ONCE(MSU_DYN_FIELD(current, task_struct, self_exec_id)) != exec_id ||
        msu_dynamic_current_cred() != committed || msu_dynamic_current_real_cred() != committed))
        error = -ESTALE;
    if (!error && adopt) error = adopt(context, original, committed);
    if (!error && adopt) error = validate(context);
    if (!error && (READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm)) != mm || READ_ONCE(MSU_DYN_FIELD(current, task_struct, self_exec_id)) != exec_id ||
        msu_dynamic_current_cred() != committed || msu_dynamic_current_real_cred() != committed)) error = -ESTALE;
    goto put_original;
abort:
    if (prepared_owned) {
        abort_creds(prepared);
        prepared_owned = false;
    }
put_original:
    msu_dynamic_put_cred(committed);
    msu_dynamic_put_cred(original);
    if (mm) msu_dynamic_mmdrop(mm);
    return error;
}

int msu_credential_replace_profile_current(const struct msu_credential_profile *profile,
    int (*validate)(void *context),
    int (*adopt)(void *context, const struct cred *before, const struct cred *after),
    void *context, u32 *effects)
{
    if (!effects) return -EINVAL;
    *effects = 0;
    if (!adopt) return -EINVAL;
    return msu_credential_replace_profile(profile, validate, adopt, context, effects);
}

int msu_credential_replace_current(const maplesu_caps_v3 *caps,
    int (*validate)(void *context), void *context, u32 *effects)
{
    struct msu_credential_profile profile = {0};
    if (!effects) return -EINVAL;
    *effects = 0;
    if (!msu_management_sleepable()) return -EWOULDBLOCK;
    if (!validate) return -EINVAL;
    if (caps) profile.caps = *caps;
    else {
        maplesu_caps_v3 full;
#if CAP_LAST_CAP < 63
        const u64 value = (1ULL << (CAP_LAST_CAP + 1)) - 1;
#else
        const u64 value = ~0ULL;
#endif
        full.inheritable = full.permitted = full.effective = full.bounding = full.ambient = value;
        profile.caps = full;
        profile.replace_ids = profile.replace_groups = profile.clear_securebits = true;
    }
    profile.cap_write_mask = MSU_CRED_CAP_ALL;
    return msu_credential_replace_profile(&profile, validate, NULL, context, effects);
}

int msu_management_validate_credentials(void *context)
{
    return msu_management_check(context);
}

static int msu_management_credentials(struct msu_management_session *session,
    const maplesu_management_v3 *operation, bool root,
    maplesu_response_owner_v3 *response)
{
    maplesu_caps_v3 caps;
    const struct cred *previous;
    int error;
    if (!root && copy_from_user_nofault(&caps,
        msu_management_user_pointer(operation->data_user), sizeof(caps))) return -EFAULT;
    error = msu_credential_replace_current(root ? NULL : &caps,
        msu_management_validate_credentials, session, &response->metadata_length);
    if (response->metadata_length & MAPLESU_EFFECT_COMMITTED) {
        if (error || msu_dynamic_current_cred() != msu_dynamic_current_real_cred()) msu_management_retire(session);
        else {
            previous = session->active;
            session->active = msu_dynamic_get_current_cred();
            msu_dynamic_put_cred(previous);
        }
    }
    return error;
}

#if defined(CONFIG_PROC_FS) && defined(CONFIG_SECURITY)
static bool msu_management_procattr_name(const struct dentry *entry,
    const char *name, u32 length)
{
    const unsigned char *observed;
    if (!entry || READ_ONCE(MSU_DYN_FIELD(&MSU_DYN_FIELD(entry, dentry, d_name), qstr, len)) != length) return false;
    observed = READ_ONCE(MSU_DYN_FIELD(&MSU_DYN_FIELD(entry, dentry, d_name), qstr, name));
    return observed && !memcmp(observed, name, length);
}

static bool msu_management_procattr_file(struct file *file, const char *name, u32 length)
{
    struct inode *inode = msu_dynamic_file_inode(file);
    struct dentry *entry = MSU_DYN_FIELD(file, file, f_path).dentry;
    struct dentry *parent;
    if (!inode || !MSU_DYN_FIELD(inode, inode, i_sb) || MSU_DYN_FIELD(MSU_DYN_FIELD(inode, inode, i_sb), super_block, s_magic) != PROC_SUPER_MAGIC ||
        !MSU_DYN_FIELD(file, file, f_op) || MSU_DYN_FIELD(MSU_DYN_FIELD(file, file, f_op), file_operations, write) != proc_pid_attr_write || !entry) return false;
    parent = READ_ONCE(MSU_DYN_FIELD(entry, dentry, d_parent));
    /* Native proc entries retain their fixed names; this does not establish
     * the target's default LSM selector or replace its self-task checks. */
    return msu_management_procattr_name(entry, name, length) &&
        msu_management_procattr_name(parent, "attr", 4);
}
#endif

static int msu_management_procattr(struct msu_management_session *session,
    const maplesu_management_v3 *operation, maplesu_response_owner_v3 *response)
{
#if defined(CONFIG_PROC_FS) && defined(CONFIG_SECURITY)
    const struct cred *before = NULL, *observed = NULL, *previous;
    const char *path, *name;
    struct file *file;
    loff_t position = 0;
    ssize_t written;
    u32 name_length;
    int error;
    if (operation->address == MAPLESU_PROCATTR_CURRENT) {
        path = "/proc/thread-self/attr/current";
        name = "current";
        name_length = 7;
    } else if (operation->address == MAPLESU_PROCATTR_EXEC) {
        path = "/proc/thread-self/attr/exec";
        name = "exec";
        name_length = 4;
    } else return -EINVAL;
    file = filp_open(path, O_WRONLY | O_NOFOLLOW | O_NONBLOCK | O_NOCTTY, 0);
    if (IS_ERR(file)) { error = PTR_ERR(file); file = NULL; goto out; }
    if (!msu_management_procattr_file(file, name, name_length)) { error = -EXDEV; goto out; }
    /* An open/permission callback cannot supply a replacement active cred. */
    error = msu_management_check(session);
    if (error) goto out;
    before = msu_dynamic_get_current_cred();
    response->metadata_length |= MAPLESU_EFFECT_PROC_WRITE_ATTEMPTED;
    written = vfs_write(file, (const char __user *)msu_management_user_pointer(operation->data_user),
        operation->length, &position);
    observed = msu_dynamic_get_current_cred();
    if (observed != before || msu_dynamic_current_real_cred() != before)
        response->metadata_length |= MAPLESU_EFFECT_CRED_CHANGED;
    if (written < 0) error = (int)written;
    else {
        response->result = (u64)written;
        error = written == operation->length ? 0 : -EIO;
    }
    fput(file);
    file = NULL;
    if (!error) error = msu_management_check_expected_cred(session, observed);
    if (!error) {
        previous = session->active;
        session->active = observed;
        observed = NULL; /* The session now owns this exact after-image ref. */
        msu_dynamic_put_cred(previous);
        error = msu_management_check(session);
    }
out:
    if (file) fput(file);
    if (msu_dynamic_current_cred() != session->active || msu_dynamic_current_real_cred() != session->active) {
        response->metadata_length |= MAPLESU_EFFECT_CRED_CHANGED;
        if (!error) error = -ESTALE;
    }
    if (error && (response->metadata_length &
        (MAPLESU_EFFECT_PROC_WRITE_ATTEMPTED | MAPLESU_EFFECT_CRED_CHANGED)))
        msu_management_retire(session);
    if (observed) msu_dynamic_put_cred(observed);
    if (before) msu_dynamic_put_cred(before);
    return error;
#else
    (void)session; (void)operation; (void)response;
    return -EOPNOTSUPP;
#endif
}

/* The host owns the immutable boot copy; session pre/post checks stay in the
 * sole dispatch path. A later stale result never turns a copied path into authority. */
static int msu_management_deployment(const maplesu_management_v3 *operation,
    maplesu_response_owner_v3 *response)
{
    struct msu_deployment_namespace_record retained;
    maplesu_deployment_namespace_v3 output = {0};
    int error;
    _Static_assert(MSU_DEPLOYMENT_NAMESPACE_MAGIC == MAPLESU_DEPLOYMENT_NAMESPACE_MAGIC,
        "deployment magic source/wire");
    _Static_assert(MSU_DEPLOYMENT_NAMESPACE_SCHEMA == MAPLESU_DEPLOYMENT_NAMESPACE_SCHEMA,
        "deployment schema source/wire");
    _Static_assert(MSU_DEPLOYMENT_NAMESPACE_BYTES == sizeof(output.name),
        "deployment name source/wire");
    _Static_assert(sizeof(retained) == sizeof(output), "deployment record source/wire");
    error = msu_manager_resident_deployment(&retained);
    if (error) return error;
    if (!msu_deployment_namespace_valid(&retained)) return -EIO;
    output.magic = retained.magic;
    output.schema = retained.schema;
    output.bytes = sizeof(output);
    memcpy(output.name, retained.name, sizeof(output.name));
    if (copy_to_user_nofault(msu_management_user_pointer(operation->data_user),
            &output, sizeof(output))) return -EFAULT;
    response->result = sizeof(output);
    return 0;
}

static int msu_management_su_runtime(const maplesu_management_v3 *operation,
    maplesu_response_owner_v3 *response)
{
    struct msu_su_runtime_status native;
    maplesu_su_runtime_status_v3 output;
    int error;
#define MSU_RUNTIME_WIRE_FIELD(field) \
    _Static_assert(offsetof(struct msu_su_runtime_status, field) == \
        offsetof(maplesu_su_runtime_status_v3, field), "SU runtime " #field)
    _Static_assert(sizeof(native) == sizeof(output), "SU runtime portable size");
    MSU_RUNTIME_WIRE_FIELD(schema); MSU_RUNTIME_WIRE_FIELD(bytes);
    MSU_RUNTIME_WIRE_FIELD(host); MSU_RUNTIME_WIRE_FIELD(installation_generation);
    MSU_RUNTIME_WIRE_FIELD(runtime_generation); MSU_RUNTIME_WIRE_FIELD(installation_fence);
    MSU_RUNTIME_WIRE_FIELD(flags); MSU_RUNTIME_WIRE_FIELD(users);
    MSU_RUNTIME_WIRE_FIELD(producers); MSU_RUNTIME_WIRE_FIELD(hook_count);
    MSU_RUNTIME_WIRE_FIELD(status); MSU_RUNTIME_WIRE_FIELD(target_error);
    MSU_RUNTIME_WIRE_FIELD(cleanup_error); MSU_RUNTIME_WIRE_FIELD(start_error);
    MSU_RUNTIME_WIRE_FIELD(target_qualified); MSU_RUNTIME_WIRE_FIELD(installation_flags);
#undef MSU_RUNTIME_WIRE_FIELD
    error = msu_su_runtime_status_get(&operation->host, &native);
    if (error > 0 || error < -4095 || native.status != error ||
        native.schema != MAPLESU_SU_RUNTIME_STATUS_SCHEMA || native.bytes != sizeof(output)) return -EIO;
    memcpy(&output, &native, sizeof(output));
    if (copy_to_user_nofault(msu_management_user_pointer(operation->data_user),
            &output, sizeof(output))) return error ? error : -EFAULT;
    response->result = sizeof(output);
    return error;
}

static int msu_management_installation(const maplesu_management_v3 *operation,
    maplesu_response_owner_v3 *response)
{
    maplesu_installation_state_v3 value;
    int error;
    if (operation->address != MAPLESU_INSTALLATION_READ &&
        copy_from_user_nofault(&value, msu_management_user_pointer(operation->data_user),
            sizeof(value))) return -EFAULT;
    error = msu_installation_management(operation->address, &value, &response->metadata_length);
    if (error) return error;
    if (operation->address == MAPLESU_INSTALLATION_READ) {
        if (copy_to_user_nofault(msu_management_user_pointer(operation->data_user),
            &value, sizeof(value))) return -EFAULT;
        response->result = sizeof(value);
    }
    return 0;
}

static int msu_management_runtime_uid_sync(struct msu_management_session *session,
    u32 full_uid, maplesu_response_owner_v3 *response)
{
    struct msu_su_observed_sync_receipt detail = {0};
    maplesu_response_owner_v3 receipt = {0};
    int error = msu_su_runtime_sync_held(full_uid, &receipt,
        msu_management_validate_credentials, session, &detail);
    /* Preserve the outer R02 request identity and expose only real producer
     * effects. The HOST owns every retained candidate and its final receipt. */
    response->result = receipt.result;
    response->metadata_address = receipt.metadata_address;
    response->metadata_length = receipt.metadata_length;
    if (error > 0 || error < -4095 || receipt.status != error || receipt.metadata_version)
        return -EIO;
    return error;
}

/* The HOST pins one immutable runtime snapshot through delivery. */
static int msu_management_runtime_uid_readback(struct msu_management_session *session,
    const maplesu_management_v3 *operation, maplesu_response_owner_v3 *response)
{
    maplesu_uid_readback_v3 descriptor;
    maplesu_uid_metadata_v3 metadata;
    maplesu_response_owner_v3 receipt = {0};
    struct msu_su_runtime_readback *view = NULL;
    maplesu_uid_record_v3 *records;
    void *buffer = NULL;
    size_t bytes;
    u32 i;
    int error;
    if (copy_from_user_nofault(&descriptor,
        msu_management_user_pointer(operation->data_user), sizeof(descriptor))) return -EFAULT;
    if (descriptor.reserved || descriptor.capacity > MAPLESU_UID_SNAPSHOT_MAX_RECORDS) return -EINVAL;
    bytes = sizeof(metadata) + (size_t)descriptor.capacity * sizeof(*records);
    if (!msu_management_user_range(descriptor.destination_user, bytes)) return -EINVAL;
    error = msu_su_runtime_readback_acquire(msu_management_validate_credentials,
        session, &view, &metadata, &receipt);
    response->metadata_address = receipt.metadata_address;
    response->metadata_length = receipt.metadata_length;
    if (error) {
        if (receipt.status != error || receipt.metadata_address || receipt.metadata_version ||
            (error == -EINPROGRESS ? receipt.metadata_length != MAPLESU_EFFECT_PENDING :
                receipt.metadata_length != 0)) error = -EIO;
        goto out;
    }
    if (receipt.status || receipt.metadata_version || receipt.metadata_address ||
        receipt.metadata_length) {
        error = -EIO;
        goto out;
    }
    if (!view || metadata.schema != MAPLESU_UID_SNAPSHOT_SCHEMA || metadata.reserved ||
        metadata.record_bytes != sizeof(*records) || metadata.count > MAPLESU_UID_SNAPSHOT_MAX_RECORDS ||
        !metadata.content_revision || metadata.host.low != operation->host.low ||
        metadata.host.high != operation->host.high) { error = -EIO; goto out; }
    if (descriptor.expected_revision && metadata.content_revision != descriptor.expected_revision) {
        error = -ESTALE;
        goto out;
    }
    if (metadata.count > descriptor.capacity) { error = -ENOSPC; goto out; }
    bytes = sizeof(metadata) + (size_t)metadata.count * sizeof(*records);
    buffer = kvmalloc(bytes, GFP_KERNEL);
    if (!buffer) { error = -ENOMEM; goto out; }
    memcpy(buffer, &metadata, sizeof(metadata));
    records = (maplesu_uid_record_v3 *)((char *)buffer + sizeof(metadata));
    for (i = 0; i < metadata.count; ++i) {
        error = msu_su_runtime_readback_record(view, i, &records[i]);
        if (error) {
            if (error == -EINPROGRESS) response->metadata_length |= MAPLESU_EFFECT_PENDING;
            goto out;
        }
    }
    error = msu_management_check(session);
    if (error) goto out;
    if (copy_to_user_nofault(msu_management_user_pointer(descriptor.destination_user),
        buffer, bytes)) {
        error = -EFAULT;
        goto out;
    }
    response->result = metadata.content_revision;
out:
    kvfree(buffer);
    if (view) msu_su_runtime_readback_put(view);
    return error;
}

static bool msu_management_shape(u32 command, const maplesu_management_v3 *value)
{
    if (value->reserved && command != MAPLESU_REQUEST_EARLY_FIXED) return false;
    if (value->flags && command != MAPLESU_REQUEST_WRITE &&
        command != MAPLESU_REQUEST_ALLOC && command != MAPLESU_REQUEST_EARLY_FIXED) return false;
    switch (command) {
    case MAPLESU_REQUEST_SESSION_OPEN:
        return !value->session && !value->handle && !value->address &&
            !value->data_user && !value->length;
    case MAPLESU_REQUEST_SESSION_CLOSE:
        return !value->handle && !value->address && !value->data_user && !value->length;
    case MAPLESU_REQUEST_CRED_ROOT:
    case MAPLESU_REQUEST_TASK_STATE:
        return value->session && !value->handle && !value->address &&
            !value->data_user && !value->length;
    case MAPLESU_REQUEST_ALLOC:
        return !(value->flags & ~MAPLESU_MANAGEMENT_ALLOC_CODE) && value->session &&
            !value->handle && !value->data_user &&
            ((value->flags & MAPLESU_MANAGEMENT_ALLOC_CODE) ?
                (!value->address || (value->address >= 0xffff000000000000ULL && !(value->address & 3))) :
                !value->address) &&
            value->length && value->length <= MAPLESU_MANAGEMENT_MAX_BYTES;
    case MAPLESU_REQUEST_WRITE:
        return !(value->flags & ~MAPLESU_MANAGEMENT_WRITE_TEXT) && value->session &&
            value->length && value->length <= MAPLESU_MANAGEMENT_MAX_BYTES &&
            (!(value->flags & MAPLESU_MANAGEMENT_WRITE_TEXT) ||
                (!value->handle && !(value->address & 3) &&
                 value->address <= ~0ULL - (ALIGN(value->length, sizeof(u32)) - 1))) &&
            msu_management_user_range(value->data_user, value->length);
    case MAPLESU_REQUEST_EXEC:
        return value->session && value->handle && !value->address && !value->data_user &&
            value->length && value->length <= MAPLESU_MANAGEMENT_MAX_BYTES;
    case MAPLESU_REQUEST_RELEASE:
        return value->session && value->handle && !value->address && !value->data_user && !value->length;
    case MAPLESU_REQUEST_DEPLOYMENT_READ:
        return value->session && !value->handle && !value->address &&
            value->length == sizeof(maplesu_deployment_namespace_v3) &&
            msu_management_user_range(value->data_user, value->length);
    case MAPLESU_REQUEST_SU_RUNTIME_READ:
        return value->session && !value->handle && !value->address &&
            value->length == sizeof(maplesu_su_runtime_status_v3) &&
            msu_management_user_range(value->data_user, value->length);
    case MAPLESU_REQUEST_EARLY_FIXED:
        /* Private helper-only business call: address=operation, handle=mode,
         * flags=exploit, reserved=value, data_user=result destination. */
        return value->session && value->address >= MSU_EARLY_FIXED_OEM_MTK &&
            value->address <= MSU_EARLY_FIXED_SELINUX_WRITE_SEQUENCE &&
            value->handle <= MAPLESU_EARLY_FIXED_MODE_MAX &&
            value->flags <= MAPLESU_EARLY_FIXED_EXPLOIT_MAX &&
            value->length == sizeof(struct msu_early_fixed_result) &&
            msu_management_user_range(value->data_user, value->length);
    case MAPLESU_REQUEST_INSTALLATION_STATE:
        return value->session && !value->handle &&
            (value->address == MAPLESU_INSTALLATION_READ ||
             value->address == MAPLESU_INSTALLATION_COMMIT ||
             value->address == MAPLESU_INSTALLATION_REVOKE) &&
            value->length == sizeof(maplesu_installation_state_v3) &&
            msu_management_user_range(value->data_user, value->length);
    case MAPLESU_REQUEST_CRED_CAPS:
        return value->session && !value->handle && !value->address &&
            value->length == sizeof(maplesu_caps_v3) &&
            msu_management_user_range(value->data_user, value->length);
    case MAPLESU_REQUEST_CRED_PROCATTR:
        return value->session && !value->handle &&
            (value->address == MAPLESU_PROCATTR_CURRENT || value->address == MAPLESU_PROCATTR_EXEC) &&
            value->length && value->length <= MAPLESU_PROCATTR_MAX_BYTES &&
            msu_management_user_range(value->data_user, value->length);
    case MAPLESU_REQUEST_PUBLICATION_PREPARE:
        return value->session && !value->handle && !value->address &&
            value->length == sizeof(maplesu_publication_prepare_v3) &&
            msu_management_user_range(value->data_user, value->length);
    case MAPLESU_REQUEST_PUBLICATION_PUBLISH:
    case MAPLESU_REQUEST_PUBLICATION_DISABLE:
    case MAPLESU_REQUEST_PUBLICATION_RETIRE:
        return value->session && value->handle && !value->address && !value->data_user && !value->length;
    case MAPLESU_REQUEST_SHARED_BOOL_CREATE:
        return value->session && !value->handle && !value->address && value->length == 1 &&
            msu_management_user_range(value->data_user, 1);
    case MAPLESU_REQUEST_SHARED_BOOL_STORE:
        return value->session && value->handle && value->address && value->length == 1 &&
            msu_management_user_range(value->data_user, 1);
    case MAPLESU_REQUEST_SHARED_BOOL_RELEASE:
        return value->session && value->handle && value->address && !value->data_user && !value->length;
    case MAPLESU_REQUEST_UID_CREATE:
        return value->session && !value->handle && !value->address && !value->data_user && !value->length;
    case MAPLESU_REQUEST_UID_PREPARE:
        return value->session && value->handle && !value->address &&
            value->length == sizeof(maplesu_uid_prepare_v3) &&
            msu_management_user_range(value->data_user, value->length);
    case MAPLESU_REQUEST_UID_COMMIT:
        /* Zero handle selects only the boot-owned durable runtime sync. */
        return value->session && !value->data_user && !value->length &&
            (value->handle ? !!value->address : value->address < (u64)~0U);
    case MAPLESU_REQUEST_UID_CANCEL:
        return value->session && value->handle && value->address && !value->data_user && !value->length;
    case MAPLESU_REQUEST_UID_READBACK:
        return value->session && !value->address &&
            value->length == sizeof(maplesu_uid_readback_v3) &&
            msu_management_user_range(value->data_user, value->length);
    case MAPLESU_REQUEST_UID_SHUTDOWN:
        return value->session && value->handle && !value->address && !value->data_user && !value->length;
    default:
        return false;
    }
}

void msu_management_dispatch(const maplesu_request_owner_v3 *request,
    const maplesu_management_v3 *operation, maplesu_response_owner_v3 *response)
{
    struct msu_management_session *session;
    struct msu_management_resource *resource;
    struct msu_publication *publication;
    struct msu_shared_bool *shared;
    unsigned long flags;
    u64 capabilities = msu_management_capabilities();
    bool helper_open, helper_close;
    int error, post_error;
    if (!response) return;
    response->result = response->metadata_address = 0;
    response->metadata_length = response->metadata_version = 0;
    response->status = -EINVAL;
    if (!msu_management_sleepable()) { response->status = -EWOULDBLOCK; return; }
    helper_open = msu_management_helper_open_shape(request, operation);
    helper_close = msu_management_helper_close_shape(request, operation);
    if (!READ_ONCE(management.ready) || !request || !operation ||
        request->magic != MAPLESU_REQUEST_MAGIC || request->version != MAPLESU_OWNER_PROTOCOL_VERSION ||
        request->length != sizeof(*request) || request->arg0 || !request->arg1 ||
        (!(helper_open || helper_close) && (!request->expected_epoch ||
            !msu_management_host_matches(operation->host) ||
            !msu_management_shape(request->command, operation)))) return;
    if (request->command == MAPLESU_REQUEST_SHARED_BOOL_STORE ||
        request->command == MAPLESU_REQUEST_SHARED_BOOL_RELEASE)
        response->metadata_address = operation->handle;
    if ((request->command == MAPLESU_REQUEST_ALLOC &&
            (operation->flags & MAPLESU_MANAGEMENT_ALLOC_CODE) && !(capabilities & MAPLESU_CAP_CODE_RESERVE)) ||
        (request->command == MAPLESU_REQUEST_WRITE &&
            (operation->flags & MAPLESU_MANAGEMENT_WRITE_TEXT) && !(capabilities & MAPLESU_CAP_TEXT_WRITE)) ||
        (request->command >= MAPLESU_REQUEST_PUBLICATION_PREPARE &&
            request->command <= MAPLESU_REQUEST_PUBLICATION_RETIRE && !(capabilities & MAPLESU_CAP_PUBLICATION)) ||
        (request->command >= MAPLESU_REQUEST_SHARED_BOOL_CREATE &&
            request->command <= MAPLESU_REQUEST_SHARED_BOOL_RELEASE && !(capabilities & MAPLESU_CAP_SHARED_BOOL)) ||
        (request->command >= MAPLESU_REQUEST_UID_CREATE &&
            request->command <= MAPLESU_REQUEST_UID_SHUTDOWN && !(capabilities & MAPLESU_CAP_UID_SNAPSHOT)) ||
        (request->command == MAPLESU_REQUEST_CRED_PROCATTR && !(capabilities & MAPLESU_CAP_PROCATTR)) ||
        (request->command == MAPLESU_REQUEST_TASK_STATE && !(capabilities & MAPLESU_CAP_TASK_STATE))) {
        response->status = -EOPNOTSUPP;
        return;
    }
    if (request->command == MAPLESU_REQUEST_SESSION_OPEN) {
        response->status = msu_management_open(request, operation, response);
        return;
    }
    session = msu_management_get(operation->session);
    if (!session) {
        if (request->command == MAPLESU_REQUEST_SESSION_CLOSE && !operation->session) {
            response->metadata_length = MAPLESU_EFFECT_SESSION_CLOSED;
            response->status = 0;
        } else response->status = -ESTALE;
        return;
    }
    mutex_lock(&session->operation);
    if (session->legacy_gateway) { error = -EPERM; goto out; }
    if (request->command == MAPLESU_REQUEST_SESSION_CLOSE) {
        if (helper_close && !session->helper) { error = -EPERM; goto out; }
        if (!msu_management_task_matches(session)) {
            msu_management_retire(session);
            error = -ESTALE;
            goto out;
        }
        msu_management_retire(session);
        error = msu_management_release_all(session, &response->metadata_length);
        response->metadata_length |= MAPLESU_EFFECT_SESSION_CLOSED;
        goto out;
    }
    if (request->expected_epoch != session->epoch) { error = -ESTALE; goto out; }
    error = msu_management_check(session);
    if (error) goto out;
    if (session->helper && request->command != MAPLESU_REQUEST_CRED_ROOT &&
        request->command != MAPLESU_REQUEST_TASK_STATE &&
        request->command != MAPLESU_REQUEST_CRED_PROCATTR &&
        request->command != MAPLESU_REQUEST_DEPLOYMENT_READ &&
        request->command != MAPLESU_REQUEST_SU_RUNTIME_READ &&
        request->command != MAPLESU_REQUEST_EARLY_FIXED) {
        error = -EPERM;
        goto out;
    }
    switch (request->command) {
    case MAPLESU_REQUEST_ALLOC:
        error = msu_management_alloc(session, operation, response);
        break;
    case MAPLESU_REQUEST_WRITE:
        error = msu_management_write(session, operation, response);
        break;
    case MAPLESU_REQUEST_EXEC:
        error = msu_management_exec(session, operation, response);
        break;
    case MAPLESU_REQUEST_RELEASE:
        resource = msu_management_resource(session, operation->handle);
        error = !resource ? -ESTALE : resource->publication ? -EBUSY :
            msu_management_release(resource, &response->metadata_length);
        break;
    case MAPLESU_REQUEST_CRED_ROOT:
    case MAPLESU_REQUEST_CRED_CAPS:
        error = msu_management_credentials(session, operation,
            request->command == MAPLESU_REQUEST_CRED_ROOT, response);
        break;
    case MAPLESU_REQUEST_DEPLOYMENT_READ:
        error = msu_management_deployment(operation, response);
        break;
    case MAPLESU_REQUEST_SU_RUNTIME_READ:
        error = msu_management_su_runtime(operation, response);
        break;
    case MAPLESU_REQUEST_EARLY_FIXED: {
        struct msu_early_fixed_result fixed = {};
        if (!session->helper) { error = -EPERM; break; }
        /* Provider call is authenticated by this live helper session and the
         * installation-bound host in operation; missing provider remains a
         * completed unsupported/error receipt, never an inferred success. */
        error = msu_early_runtime_activate(&operation->host, (u32)operation->address);
        if (!error) error = msu_management_check(session);
        if (!error) error = msu_early_fixed_provider_call(&operation->host,
            (u32)operation->address, (u32)operation->handle,
            (u32)operation->flags, operation->reserved, &fixed);
        else fixed.native_errno = error < 0 && error >= -4095 ? -error : 0;
        if (copy_to_user_nofault(msu_management_user_pointer(operation->data_user),
                &fixed, sizeof(fixed))) {
            if (!error) error = -EFAULT;
        } else {
            response->result = sizeof(fixed);
        }
        break;
    }
    case MAPLESU_REQUEST_INSTALLATION_STATE:
        error = msu_management_installation(operation, response);
        break;
    case MAPLESU_REQUEST_CRED_PROCATTR:
        error = msu_management_procattr(session, operation, response);
        break;
    case MAPLESU_REQUEST_TASK_STATE:
#if defined(MSU_MANAGEMENT_TASK_STATE_VERIFIED) && MSU_MANAGEMENT_TASK_STATE_VERIFIED
        error = msu_task_state_release_current(msu_management_validate_credentials,
            session, &response->metadata_length, &response->result);
#else
        error = -EOPNOTSUPP;
#endif
        break;
    case MAPLESU_REQUEST_PUBLICATION_PREPARE:
        error = msu_publication_prepare(session, operation, response);
        break;
    case MAPLESU_REQUEST_PUBLICATION_PUBLISH:
    case MAPLESU_REQUEST_PUBLICATION_DISABLE:
    case MAPLESU_REQUEST_PUBLICATION_RETIRE:
        response->metadata_address = operation->handle;
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        publication = msu_publication_find(operation->handle);
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        if (!publication) { error = -ESTALE; break; }
        mutex_lock(&publication->operation);
        if (publication->id != operation->handle || publication->legacy ||
            (!publication->ever_exposed && !publication->canceled && publication->session != session)) {
            error = -ESTALE;
        } else if (request->command == MAPLESU_REQUEST_PUBLICATION_PUBLISH) {
            error = msu_publication_publish(session, publication, response);
        } else if (request->command == MAPLESU_REQUEST_PUBLICATION_DISABLE) {
            error = msu_publication_disable(publication, session, response);
        } else error = msu_publication_retire(publication, session, response);
        mutex_unlock(&publication->operation);
        break;
    case MAPLESU_REQUEST_SHARED_BOOL_CREATE:
        error = msu_shared_bool_create(session, operation, response);
        break;
    case MAPLESU_REQUEST_SHARED_BOOL_STORE:
    case MAPLESU_REQUEST_SHARED_BOOL_RELEASE:
        error = msu_shared_bool_update(session, operation,
            request->command == MAPLESU_REQUEST_SHARED_BOOL_RELEASE, response);
        break;
    case MAPLESU_REQUEST_UID_CREATE:
    case MAPLESU_REQUEST_UID_PREPARE:
    case MAPLESU_REQUEST_UID_COMMIT:
    case MAPLESU_REQUEST_UID_CANCEL:
    case MAPLESU_REQUEST_UID_READBACK:
    case MAPLESU_REQUEST_UID_SHUTDOWN:
        if (request->command == MAPLESU_REQUEST_UID_COMMIT && !operation->handle)
            error = msu_management_runtime_uid_sync(session, (u32)operation->address, response);
        else if (request->command == MAPLESU_REQUEST_UID_READBACK && !operation->handle)
            error = msu_management_runtime_uid_readback(session, operation, response);
        else error = msu_uid_gateway_dispatch(request->command, session->id, operation, response,
            msu_management_validate_credentials, session);
        break;
    default:
        error = -EOPNOTSUPP;
    }
    post_error = msu_management_check(session);
    if (!error) error = post_error;
    if (error && request->command == MAPLESU_REQUEST_SHARED_BOOL_CREATE && response->metadata_address) {
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        shared = msu_shared_bool_find(response->metadata_address);
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        if (shared) {
            mutex_lock(&shared->operation);
            {
                int release_error = msu_shared_bool_drop_creator(shared, session, &response->metadata_length);
                (void)release_error;
            }
            mutex_unlock(&shared->operation);
        }
        response->result = response->metadata_address = 0;
    }
    if (error && request->command == MAPLESU_REQUEST_PUBLICATION_PREPARE &&
        (response->metadata_length & MAPLESU_EFFECT_PUBLICATION_PREPARED)) {
        raw_spin_lock_irqsave(&msu_management_lock, flags);
        publication = msu_publication_find(response->result);
        raw_spin_unlock_irqrestore(&msu_management_lock, flags);
        if (publication) {
            mutex_lock(&publication->operation);
            {
                int release_error = publication->id == response->result &&
                    publication->session == session && !publication->ever_exposed ?
                    msu_publication_release_unexposed(publication, &response->metadata_length) : -ESTALE;
                (void)release_error;
            }
            mutex_unlock(&publication->operation);
        }
        response->result = response->metadata_address = 0;
    }
    if (error && request->command == MAPLESU_REQUEST_ALLOC) {
        response->result = response->metadata_address = 0;
        if (post_error) {
            int release_error = msu_management_release_all(session, &response->metadata_length);
            (void)release_error;
        }
    }
out:
    if (!READ_ONCE(session->attached)) response->metadata_length |= MAPLESU_EFFECT_SESSION_CLOSED;
    response->status = error;
    mutex_unlock(&session->operation);
    msu_management_put(session);
}

void msu_management_delivery_failed(u64 id)
{
    struct msu_management_session *session = msu_management_get(id);
    u32 effects = 0;
    if (!session) return;
    mutex_lock(&session->operation);
    msu_management_retire(session);
    msu_management_release_all(session, &effects);
    mutex_unlock(&session->operation);
    msu_management_put(session);
}

#include "manager_legacy_provider.inc"
#include "manager_legacy_hook.inc"
