// SPDX-License-Identifier: GPL-3.0-only
#include <linux/atomic.h>
#include <linux/compiler.h>
#include <linux/compat.h>
#include <linux/cred.h>
#include <linux/errno.h>
#include <linux/irqflags.h>
#include <linux/kernel.h>
#include <linux/kprobes.h>
#include <linux/mm.h>
#include <linux/mutex.h>
#include <linux/preempt.h>
#include <linux/rcupdate.h>
#include <linux/sched.h>
#include <linux/sched/mm.h>
#include <linux/sched/signal.h>
#include <linux/seccomp.h>
#include <linux/slab.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <linux/thread_info.h>
#include <linux/tracepoint.h>
#include <linux/uaccess.h>
#include <linux/version.h>
#include <asm/ptrace.h>
#include <asm/unistd.h>
#include "manager_task_state.h"
#include "request_protocol.h"
#include "manager_dynamic_layout.h"
#if defined(MSU_INIT_BRIDGE_HOST_IMPORTS) && defined(MSU_INIT_BRIDGE_RUNTIME_FACTS)
#include "manager_task_profile_runtime.h"
#endif

#ifdef CONFIG_SECCOMP_FILTER
/* The native public API consumes a private carrier for the detached tree.
 * Its actual body selects whether that carrier needs a live sighand lock.
 * No filter layout, reference algorithm or BPF destructor is copied. */
#ifndef MSU_TASK_SECCOMP_RELEASE_LOCKS_TASK
#error "actual seccomp task-release body qualification is required"
#endif
#if MSU_TASK_SECCOMP_RELEASE_LOCKS_TASK != 0 && MSU_TASK_SECCOMP_RELEASE_LOCKS_TASK != 1
#error "invalid seccomp task-release body qualification"
#endif
extern void seccomp_filter_release(struct task_struct *task);
_Static_assert(__builtin_types_compatible_p(typeof(&seccomp_filter_release),
    void (*)(struct task_struct *)), "native seccomp task release type");
#endif
_Static_assert(__builtin_types_compatible_p(typeof(((struct sighand_struct *)0)->siglock),
    spinlock_t), "native sighand lock type");

#ifdef CONFIG_SECCOMP
/* Match the same native work-bit contract recorded in the compiled layout. */
#ifdef CONFIG_GENERIC_ENTRY
#define MSU_TASK_SECCOMP_WORK(task) test_bit(SYSCALL_WORK_BIT_SECCOMP, &MSU_DYN_FIELD(msu_dynamic_task_thread_info(task), thread_info, syscall_work))
#define MSU_CLEAR_TASK_SECCOMP_WORK(task) clear_bit(SYSCALL_WORK_BIT_SECCOMP, &MSU_DYN_FIELD(msu_dynamic_task_thread_info(task), thread_info, syscall_work))
#else
#define MSU_TASK_SECCOMP_WORK(task) test_bit(TIF_SECCOMP, &MSU_DYN_FIELD(msu_dynamic_task_thread_info(task), thread_info, flags))
#define MSU_CLEAR_TASK_SECCOMP_WORK(task) clear_bit(TIF_SECCOMP, &MSU_DYN_FIELD(msu_dynamic_task_thread_info(task), thread_info, flags))
#endif
#endif

#define MSU_LAYOUT_TYPE(type) sizeof(type), __alignof__(type)
#define MSU_LAYOUT_FIELD(type, member) offsetof(type, member), sizeof(((type *)0)->member)
const unsigned int msu_task_state_compiled_layout[MSU_TASK_STATE_LAYOUT_WORDS]
    __attribute__((section(".msu.task_state.layout"))) = {
    1, LINUX_VERSION_CODE, IS_ENABLED(CONFIG_SECCOMP), IS_ENABLED(CONFIG_SECCOMP_FILTER),
    IS_ENABLED(CONFIG_GENERIC_ENTRY), IS_ENABLED(CONFIG_THREAD_INFO_IN_TASK), IS_ENABLED(CONFIG_PREEMPT_RT),
    MSU_LAYOUT_TYPE(struct task_struct),
#ifdef CONFIG_SECCOMP
    MSU_LAYOUT_FIELD(struct task_struct, seccomp),
#else
    ~0U, 0,
#endif
    MSU_LAYOUT_FIELD(struct task_struct, sighand),
#ifdef CONFIG_THREAD_INFO_IN_TASK
    MSU_LAYOUT_FIELD(struct task_struct, thread_info),
#else
    ~0U, 0,
#endif
    MSU_LAYOUT_FIELD(struct task_struct, stack),
#ifdef CONFIG_SECCOMP
    MSU_LAYOUT_TYPE(struct seccomp),
    MSU_LAYOUT_FIELD(struct seccomp, mode),
    MSU_LAYOUT_FIELD(struct seccomp, filter_count),
    MSU_LAYOUT_FIELD(struct seccomp, filter),
#else
    ~0U, 0, ~0U, 0, ~0U, 0, ~0U, 0,
#endif
    MSU_LAYOUT_TYPE(struct sighand_struct),
    MSU_LAYOUT_FIELD(struct sighand_struct, siglock),
    MSU_LAYOUT_TYPE(struct thread_info),
#if !defined(CONFIG_SECCOMP)
    ~0U, 0, ~0U,
#elif defined(CONFIG_GENERIC_ENTRY)
    MSU_LAYOUT_FIELD(struct thread_info, syscall_work), SYSCALL_WORK_BIT_SECCOMP,
#else
    MSU_LAYOUT_FIELD(struct thread_info, flags), TIF_SECCOMP,
#endif
    MSU_LAYOUT_TYPE(spinlock_t),
#ifdef CONFIG_PREEMPT_RT
    ~0U, 0,
#else
    MSU_LAYOUT_FIELD(spinlock_t, rlock),
#endif
    MSU_LAYOUT_TYPE(raw_spinlock_t), MSU_LAYOUT_FIELD(raw_spinlock_t, raw_lock),
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
    SECCOMP_MODE_DISABLED, SECCOMP_MODE_STRICT, SECCOMP_MODE_FILTER,
};
#undef MSU_LAYOUT_FIELD
#undef MSU_LAYOUT_TYPE

int msu_task_state_release_current(int (*validate)(void *context), void *context,
    u32 *effects, u64 *previous_mode)
{
    struct task_struct *task = current;
#ifdef CONFIG_SECCOMP
    struct sighand_struct *sighand;
#ifdef CONFIG_SECCOMP_FILTER
    struct seccomp_filter *detached = NULL;
    struct task_struct *release_task = NULL;
#endif
    unsigned long flags;
    int mode, count;
    bool work;
#endif
    struct mm_struct *mm;
    const struct cred *original;
    u64 exec_id;
    int error;
    if (!effects || !previous_mode) return -EINVAL;
    *effects = 0;
    if (!msu_runtime_layout_ready()) return -EPROTO;
    if (!in_task() || in_atomic() || irqs_disabled()) return -EWOULDBLOCK;
    if (!validate) return -EINVAL;
    if (!READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm)) || (READ_ONCE(MSU_DYN_FIELD(current, task_struct, flags)) & PF_EXITING) ||
        msu_dynamic_current_cred() != msu_dynamic_current_real_cred()) return -EPERM;
    original = msu_dynamic_get_current_cred();
    mm = READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm));
    msu_dynamic_mmgrab(mm);
    exec_id = READ_ONCE(MSU_DYN_FIELD(current, task_struct, self_exec_id));
#ifdef CONFIG_SECCOMP_FILTER
    if (!msu_runtime_layout.values[MSU_DYN_task_struct_flags].owner_bytes) { error = -EPROTO; goto out; }
    release_task = kvzalloc(msu_runtime_layout.values[MSU_DYN_task_struct_flags].owner_bytes, GFP_KERNEL);
    if (!release_task) { error = -ENOMEM; goto out; }
#endif
    error = validate(context);
    if (error) goto out;
#ifdef CONFIG_SECCOMP
    sighand = READ_ONCE(MSU_DYN_FIELD(current, task_struct, sighand));
    if (!sighand) { error = -ESTALE; goto out; }
    spin_lock_irqsave(&MSU_DYN_FIELD(sighand, sighand_struct, siglock), flags);
    if (current != task || READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm)) != mm || READ_ONCE(MSU_DYN_FIELD(current, task_struct, self_exec_id)) != exec_id ||
        READ_ONCE(MSU_DYN_FIELD(current, task_struct, sighand)) != sighand || (READ_ONCE(MSU_DYN_FIELD(current, task_struct, flags)) & PF_EXITING) ||
        msu_dynamic_current_cred() != original || msu_dynamic_current_real_cred() != original) {
        error = -ESTALE;
        goto unlock;
    }
    mode = READ_ONCE(MSU_DYN_FIELD(&MSU_DYN_FIELD(current, task_struct, seccomp), seccomp, mode));
    count = atomic_read(&MSU_DYN_FIELD(&MSU_DYN_FIELD(current, task_struct, seccomp), seccomp, filter_count));
    work = MSU_TASK_SECCOMP_WORK(current);
    if (mode == SECCOMP_MODE_DISABLED) {
        if (MSU_DYN_FIELD(&MSU_DYN_FIELD(current, task_struct, seccomp), seccomp, filter) || count || work) { error = -EUCLEAN; goto unlock; }
    } else if (mode == SECCOMP_MODE_STRICT) {
        if (MSU_DYN_FIELD(&MSU_DYN_FIELD(current, task_struct, seccomp), seccomp, filter) || count || !work) { error = -EUCLEAN; goto unlock; }
    } else if (mode == SECCOMP_MODE_FILTER) {
#ifdef CONFIG_SECCOMP_FILTER
        if (!MSU_DYN_FIELD(&MSU_DYN_FIELD(current, task_struct, seccomp), seccomp, filter) || count <= 0 || !work) { error = -EUCLEAN; goto unlock; }
#else
        error = -EUCLEAN;
        goto unlock;
#endif
    } else {
        error = -EUCLEAN;
        goto unlock;
    }
    if (mode != SECCOMP_MODE_DISABLED) {
        MSU_CLEAR_TASK_SECCOMP_WORK(current);
        smp_mb__after_atomic();
#ifdef CONFIG_SECCOMP_FILTER
        detached = MSU_DYN_FIELD(&MSU_DYN_FIELD(current, task_struct, seccomp), seccomp, filter);
        MSU_DYN_FIELD(release_task, task_struct, flags) = PF_EXITING;
#if MSU_TASK_SECCOMP_RELEASE_LOCKS_TASK
        MSU_DYN_FIELD(release_task, task_struct, sighand) = sighand;
#endif
        MSU_DYN_FIELD(&MSU_DYN_FIELD(release_task, task_struct, seccomp), seccomp, filter) = detached;
#endif
        WRITE_ONCE(MSU_DYN_FIELD(&MSU_DYN_FIELD(current, task_struct, seccomp), seccomp, filter), NULL);
        atomic_set(&MSU_DYN_FIELD(&MSU_DYN_FIELD(current, task_struct, seccomp), seccomp, filter_count), 0);
        WRITE_ONCE(MSU_DYN_FIELD(&MSU_DYN_FIELD(current, task_struct, seccomp), seccomp, mode), SECCOMP_MODE_DISABLED);
        *effects |= MAPLESU_EFFECT_TASK_STATE_CHANGED;
    }
    *effects |= MAPLESU_EFFECT_TASK_STATE_COMPLETED;
    *previous_mode = (u64)mode;
    error = 0;
unlock:
    spin_unlock_irqrestore(&MSU_DYN_FIELD(sighand, sighand_struct, siglock), flags);
#ifdef CONFIG_SECCOMP_FILTER
    if (detached) seccomp_filter_release(release_task);
#endif
    if (error) goto out;
#else
    if (current != task || READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm)) != mm || READ_ONCE(MSU_DYN_FIELD(current, task_struct, self_exec_id)) != exec_id ||
        (READ_ONCE(MSU_DYN_FIELD(current, task_struct, flags)) & PF_EXITING) || msu_dynamic_current_cred() != original || msu_dynamic_current_real_cred() != original) {
        error = -ESTALE;
        goto out;
    }
    *effects |= MAPLESU_EFFECT_TASK_STATE_COMPLETED;
    *previous_mode = SECCOMP_MODE_DISABLED;
#endif
    error = validate(context);
    if (!error && (current != task || READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm)) != mm || READ_ONCE(MSU_DYN_FIELD(current, task_struct, self_exec_id)) != exec_id ||
        (READ_ONCE(MSU_DYN_FIELD(current, task_struct, flags)) & PF_EXITING) || msu_dynamic_current_cred() != original || msu_dynamic_current_real_cred() != original))
        error = -ESTALE;
#ifdef CONFIG_SECCOMP
    if (!error) {
        if (READ_ONCE(MSU_DYN_FIELD(current, task_struct, sighand)) != sighand) { error = -ESTALE; goto out; }
        spin_lock_irqsave(&MSU_DYN_FIELD(sighand, sighand_struct, siglock), flags);
        if (current != task || READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm)) != mm || READ_ONCE(MSU_DYN_FIELD(current, task_struct, self_exec_id)) != exec_id ||
            READ_ONCE(MSU_DYN_FIELD(current, task_struct, sighand)) != sighand || (READ_ONCE(MSU_DYN_FIELD(current, task_struct, flags)) & PF_EXITING) ||
            msu_dynamic_current_cred() != original || msu_dynamic_current_real_cred() != original ||
            READ_ONCE(MSU_DYN_FIELD(&MSU_DYN_FIELD(current, task_struct, seccomp), seccomp, mode)) != SECCOMP_MODE_DISABLED || MSU_DYN_FIELD(&MSU_DYN_FIELD(current, task_struct, seccomp), seccomp, filter) ||
            atomic_read(&MSU_DYN_FIELD(&MSU_DYN_FIELD(current, task_struct, seccomp), seccomp, filter_count)) || MSU_TASK_SECCOMP_WORK(current))
            error = -ESTALE;
        spin_unlock_irqrestore(&MSU_DYN_FIELD(sighand, sighand_struct, siglock), flags);
    }
#endif
out:
#ifdef CONFIG_SECCOMP_FILTER
    kvfree(release_task);
#endif
    msu_dynamic_mmdrop(mm);
    msu_dynamic_put_cred(original);
    return error;
}

#if defined(CONFIG_ARM64) && defined(CONFIG_64BIT) && defined(CONFIG_THREAD_INFO_IN_TASK) && defined(CONFIG_HAVE_SYSCALL_TRACEPOINTS)
#define MSU_TASK_PROFILE_NATIVE 1
_Static_assert(__builtin_types_compatible_p(typeof(((struct thread_info *)0)->flags),
    unsigned long), "native thread flag type");
_Static_assert(sizeof(((struct thread_info *)0)->flags) == 8, "native bit63 storage");
#ifdef TIF_KSU_DISABLE_ESCAPE_WITH_ROOT
_Static_assert(TIF_KSU_DISABLE_ESCAPE_WITH_ROOT == MSU_TASK_PROFILE_DISABLE_ROOT_BIT,
    "fixed KSU disable-root bit");
#endif
#ifdef CONFIG_GENERIC_ENTRY
#define MSU_TASK_PROFILE_WORK_FIELD syscall_work
#define MSU_TASK_PROFILE_TRACE_BIT SYSCALL_WORK_BIT_SYSCALL_TRACEPOINT
#else
#define MSU_TASK_PROFILE_WORK_FIELD flags
#define MSU_TASK_PROFILE_TRACE_BIT TIF_SYSCALL_TRACEPOINT
#endif
_Static_assert(__builtin_types_compatible_p(
    typeof(((struct thread_info *)0)->MSU_TASK_PROFILE_WORK_FIELD), unsigned long),
    "native syscall work type");
_Static_assert(MSU_TASK_PROFILE_TRACE_BIT >= 0 && MSU_TASK_PROFILE_TRACE_BIT < 63,
    "native tracepoint bit separate from disable-root");
#else
#define MSU_TASK_PROFILE_NATIVE 0
#endif

#define MSU_PROFILE_TYPE(type) sizeof(type), __alignof__(type)
#define MSU_PROFILE_FIELD(type, member) offsetof(type, member), sizeof(((type *)0)->member)
const unsigned int msu_task_profile_compiled_layout[MSU_TASK_PROFILE_LAYOUT_WORDS]
    __attribute__((section(".msu.task_profile.layout"))) = {
    1, LINUX_VERSION_CODE, MSU_TASK_PROFILE_FLAGS_VERIFIED, MSU_TASK_PROFILE_NATIVE,
    IS_ENABLED(CONFIG_GENERIC_ENTRY), IS_ENABLED(CONFIG_THREAD_INFO_IN_TASK),
    MSU_PROFILE_TYPE(struct task_struct),
#ifdef CONFIG_THREAD_INFO_IN_TASK
    MSU_PROFILE_FIELD(struct task_struct, thread_info),
#else
    ~0U, 0,
#endif
    MSU_PROFILE_FIELD(struct task_struct, signal),
    MSU_PROFILE_TYPE(struct thread_info), MSU_PROFILE_FIELD(struct thread_info, flags),
    MSU_TASK_PROFILE_DISABLE_ROOT_BIT,
#ifdef CONFIG_GENERIC_ENTRY
    MSU_PROFILE_FIELD(struct thread_info, syscall_work), SYSCALL_WORK_BIT_SYSCALL_TRACEPOINT,
#else
    MSU_PROFILE_FIELD(struct thread_info, flags), TIF_SYSCALL_TRACEPOINT,
#endif
    MSU_PROFILE_TYPE(struct signal_struct), MSU_PROFILE_FIELD(struct signal_struct, thread_head),
    MSU_PROFILE_FIELD(struct task_struct, thread_node),
};
#undef MSU_PROFILE_FIELD
#undef MSU_PROFILE_TYPE

struct msu_task_profile_target_binding msu_task_profile_target
    __attribute__((section(".msu.task_profile.target"), aligned(4))) = {
    .magic = MSU_TASK_PROFILE_TARGET_MAGIC,
    .schema = MSU_TASK_PROFILE_TARGET_SCHEMA,
    .bytes = sizeof(struct msu_task_profile_target_binding),
};
_Static_assert(sizeof(struct msu_task_profile_target_binding) == 136,
    "TaskProfile target binding schema");

const unsigned int msu_task_profile_trace_compiled_layout[MSU_TASK_PROFILE_TRACE_LAYOUT_WORDS]
    __attribute__((section(".msu.task_profile.trace-layout"))) = {
    1, LINUX_VERSION_CODE, MSU_TASK_PROFILE_NATIVE,
#ifdef CONFIG_ARM64
    sizeof(struct pt_regs), __alignof__(struct pt_regs),
    offsetof(struct pt_regs, regs), sizeof(((struct pt_regs *)0)->regs[0]),
    offsetof(struct pt_regs, syscallno), sizeof(((struct pt_regs *)0)->syscallno),
    __NR_execve, __NR_execveat, 0,
#else
    ~0U, 0, ~0U, 0, ~0U, 0, ~0U, ~0U, 0,
#endif
};

static DEFINE_MUTEX(msu_task_profile_trace_lock);
static struct tracepoint *msu_task_profile_tracepoint;
static int msu_task_profile_trace_bind_error = -ENODEV;
static bool msu_task_profile_trace_registered, msu_task_profile_trace_active;

static bool msu_task_profile_hash_present(const unsigned char hash[32])
{
    unsigned int i;
    unsigned char value = 0;
    for (i = 0; i < 32; ++i) value |= hash[i];
    return value != 0;
}

static bool msu_task_profile_target_ready(void)
{
    const struct msu_task_profile_target_binding *binding = &msu_task_profile_target;
    const struct msu_runtime_layout_field *field, *root;
    u32 source = smp_load_acquire(&binding->bound);
    root = &msu_runtime_layout.values[MSU_DYN_thread_info_flags];
    if (!MSU_TASK_PROFILE_NATIVE || !msu_runtime_layout_ready() ||
        (source != MSU_TASK_PROFILE_TARGET_IMAGE && source != MSU_TASK_PROFILE_TARGET_RUNTIME) ||
        binding->magic != MSU_TASK_PROFILE_TARGET_MAGIC ||
        binding->schema != MSU_TASK_PROFILE_TARGET_SCHEMA ||
        binding->bytes != sizeof(*binding) || binding->reserved ||
        binding->root_bit != MSU_TASK_PROFILE_DISABLE_ROOT_BIT ||
        binding->work_member > 1 || binding->work_bit >= 63 || binding->compat ||
        root->bytes != sizeof(unsigned long) ||
        (root->offset & (__alignof__(unsigned long) - 1)) ||
        root->offset > root->owner_bytes || root->bytes > root->owner_bytes - root->offset) return false;
    if (source == MSU_TASK_PROFILE_TARGET_IMAGE) {
        if (binding->proof_mask != MSU_TASK_PROFILE_TARGET_PROOFS ||
            !msu_task_profile_hash_present(binding->target_sha256) ||
            !msu_task_profile_hash_present(binding->config_sha256) ||
            !msu_task_profile_hash_present(binding->proof_sha256)) return false;
    } else {
#if defined(MSU_INIT_BRIDGE_HOST_IMPORTS) && defined(MSU_INIT_BRIDGE_RUNTIME_FACTS)
        if (binding->proof_mask != MSU_TASK_PROFILE_RUNTIME_PROOFS ||
            !msu_task_profile_runtime_ready()) return false;
#else
        return false;
#endif
    }
    field = &msu_runtime_layout.values[binding->work_member ?
        MSU_DYN_thread_info_syscall_work : MSU_DYN_thread_info_flags];
    return field->bytes == sizeof(unsigned long) &&
        !(field->offset & (__alignof__(unsigned long) - 1)) &&
        field->offset <= field->owner_bytes && field->bytes <= field->owner_bytes - field->offset;
}

static unsigned long *msu_task_profile_work(struct task_struct *task)
{
    u32 slot = msu_task_profile_target.work_member ?
        MSU_DYN_thread_info_syscall_work : MSU_DYN_thread_info_flags;
    return (unsigned long *)((unsigned char *)msu_dynamic_task_thread_info(task) +
        msu_runtime_layout.values[slot].offset);
}

struct msu_task_profile_marker_binding msu_task_profile_marker_target
    __attribute__((section(".msu.task_profile.marker-target"), aligned(4))) = {
    .magic = MSU_TASK_PROFILE_MARKER_MAGIC, .schema = 1,
    .bytes = sizeof(struct msu_task_profile_marker_binding),
};
_Static_assert(sizeof(struct msu_task_profile_marker_binding) == 112,
    "TaskProfile native marker binding schema");
static struct {
    struct tracepoint *exit_tracepoint;
    struct task_struct *init_task;
    unsigned long addresses[4];
    struct kretprobe *probes[4];
    bool opened;
} msu_task_profile_marker;
static DEFINE_RAW_SPINLOCK(msu_task_profile_marker_lock);

static bool msu_task_profile_marker_field(u32 owner, u32 offset, u32 bytes, u32 expected)
{
    return bytes == expected && offset <= owner && bytes <= owner - offset &&
        !(offset & (min_t(u32, expected, sizeof(unsigned long)) - 1));
}

static bool msu_task_profile_marker_ready(void)
{
    const struct msu_task_profile_marker_binding *m = &msu_task_profile_marker_target;
    return smp_load_acquire(&m->bound) == 1 && m->magic == MSU_TASK_PROFILE_MARKER_MAGIC &&
        m->schema == 1 && m->bytes == sizeof(*m) &&
        m->task_bytes == msu_runtime_layout.values[MSU_DYN_task_struct_flags].owner_bytes &&
        msu_task_profile_marker_field(m->task_bytes, m->tasks_offset, m->tasks_bytes, sizeof(struct list_head)) &&
        msu_task_profile_marker_field(m->tracepoint_bytes, m->funcs_offset, m->funcs_bytes, sizeof(void *)) &&
        msu_task_profile_marker_field(m->func_bytes, m->callback_offset, m->callback_bytes, sizeof(void *)) &&
        msu_task_profile_marker_field(m->func_bytes, m->data_offset, m->data_bytes, sizeof(void *)) &&
        m->kretprobe_bytes && m->kretprobe_bytes <= 65536 &&
        msu_task_profile_marker_field(m->kretprobe_bytes, m->handler_offset, m->handler_bytes, sizeof(void *)) &&
        msu_task_profile_marker_field(m->kretprobe_bytes, m->entry_handler_offset, m->entry_handler_bytes, sizeof(void *)) &&
        msu_task_profile_marker_field(m->kretprobe_bytes, m->kp_addr_offset, m->kp_addr_bytes, sizeof(void *)) &&
        msu_task_profile_marker_field(m->kretprobe_bytes, m->maxactive_offset, m->maxactive_bytes, sizeof(int)) &&
        msu_task_profile_marker_field(m->kretprobe_bytes, m->nmissed_offset, m->nmissed_bytes, sizeof(int));
}

/* Count the actual current callback arrays, not a guessed registration count.
 * A second sys_enter subscriber does not invoke native syscall_regfunc again.
 * The public-register return probes therefore cover that same-key transition
 * as well as the fixed KernelSU regfunc/unregfunc native transitions. */
static int msu_task_profile_marker_mode(void)
{
    const struct msu_task_profile_marker_binding *m = &msu_task_profile_marker_target;
    struct tracepoint *tracepoints[2] = {
        msu_task_profile_tracepoint, msu_task_profile_marker.exit_tracepoint,
    };
    unsigned int i, index;
    bool own = false;
    for (i = 0; i < 2; ++i) {
        unsigned char *array = rcu_dereference_raw(*(void __rcu **)
            ((unsigned char *)tracepoints[i] + m->funcs_offset));
        if (!array) continue;
        for (index = 0; index < 1024; ++index) {
            unsigned char *row = array + index * m->func_bytes;
            void *callback = READ_ONCE(*(void **)(row + m->callback_offset));
            void *data;
            if (!callback) break;
            data = READ_ONCE(*(void **)(row + m->data_offset));
            if (i || callback != (void *)msu_task_profile_sys_enter ||
                data != &msu_task_profile_trace_registered) return 2;
            own = true;
        }
        if (index == 1024) return 2; /* preserve unknown/native subscribers */
    }
    return own ? 1 : 0;
}

static void msu_task_profile_marker_update(void)
{
    const struct msu_task_profile_marker_binding *m = &msu_task_profile_marker_target;
    struct list_head *process_head, *process, *thread_head, *thread_node;
    struct task_struct *leader, *thread;
    unsigned long irq;
    int before, after;
    if (!READ_ONCE(msu_task_profile_marker.opened)) return;
    raw_spin_lock_irqsave(&msu_task_profile_marker_lock, irq);
    if (!msu_task_profile_marker.opened) goto out;
    /* Match the native for_each_process_thread RCU lists, using the stable
     * signal thread-head sentinel rather than a possibly exiting leader.
     * Tasks (and their signal references) survive until after the RCU grace
     * period. No sleepable tasklist rwlock is acquired by this return probe. */
    rcu_read_lock();
    before = msu_task_profile_marker_mode();
    /* regfunc returns before a first callback array is published. With no
     * visible subscriber leave its native set/clear result untouched; the
     * public registration return will observe the completed array. */
    if (!before) goto unlock_native;
    process_head = (void *)((unsigned char *)msu_task_profile_marker.init_task + m->tasks_offset);
    for (process = rcu_dereference_raw(process_head->next); process != process_head;
            process = rcu_dereference_raw(process->next)) {
        leader = (void *)((unsigned char *)process - m->tasks_offset);
        thread_head = &MSU_DYN_FIELD(READ_ONCE(MSU_DYN_FIELD(leader, task_struct, signal)), signal_struct, thread_head);
        for (thread_node = rcu_dereference_raw(thread_head->next); thread_node != thread_head;
                thread_node = rcu_dereference_raw(thread_node->next)) {
            thread = MSU_DYN_CONTAINER(thread_node, task_struct, thread_node);
            if (before == 2 || (before == 1 && msu_su_runtime_trace_mark_task(thread)))
                set_bit(msu_task_profile_target.work_bit, msu_task_profile_work(thread));
            else
                clear_bit(msu_task_profile_target.work_bit, msu_task_profile_work(thread));
        }
    }
    after = msu_task_profile_marker_mode();
    if (before != 2 && after == 2) {
        /* A concurrent public registration may return after this scan. Its
         * return probe also restores all tasks; preserve it in this pass too.
         * This is the fixed baseline's completed-event contract, not a claim
         * of a global zero-window snapshot during clone/registration. */
        for (process = rcu_dereference_raw(process_head->next); process != process_head;
                process = rcu_dereference_raw(process->next)) {
            leader = (void *)((unsigned char *)process - m->tasks_offset);
            thread_head = &MSU_DYN_FIELD(READ_ONCE(MSU_DYN_FIELD(leader, task_struct, signal)), signal_struct, thread_head);
            for (thread_node = rcu_dereference_raw(thread_head->next); thread_node != thread_head;
                    thread_node = rcu_dereference_raw(thread_node->next)) {
                thread = MSU_DYN_CONTAINER(thread_node, task_struct, thread_node);
                set_bit(msu_task_profile_target.work_bit, msu_task_profile_work(thread));
            }
        }
    }
unlock_native:
    rcu_read_unlock();
out:
    raw_spin_unlock_irqrestore(&msu_task_profile_marker_lock, irq);
}

/* Generic tracepoint register/unregister runs for many unrelated events. Only
 * native syscall objects participate in this marker's completed-event scan. */
int msu_task_profile_marker_entry(struct kretprobe_instance *instance, struct pt_regs *regs)
{
#ifdef CONFIG_ARM64
    unsigned long tracepoint = regs_get_kernel_argument(regs, 0);
    (void)instance;
    return tracepoint == (unsigned long)msu_task_profile_tracepoint ||
        tracepoint == (unsigned long)msu_task_profile_marker.exit_tracepoint ? 0 : 1;
#else
    (void)instance; (void)regs;
    return 1;
#endif
}
kretprobe_handler_t msu_task_profile_marker_entry_type
    __attribute__((used)) = msu_task_profile_marker_entry;

int msu_task_profile_marker_return(struct kretprobe_instance *instance, struct pt_regs *regs)
{
    (void)instance; (void)regs;
    msu_task_profile_marker_update();
    return 0;
}
kretprobe_handler_t msu_task_profile_marker_return_type
    __attribute__((used)) = msu_task_profile_marker_return;

static bool msu_task_profile_marker_missed(void)
{
    unsigned long irq;
    unsigned int i;
    bool missed = false;
    raw_spin_lock_irqsave(&msu_task_profile_marker_lock, irq);
    if (!msu_task_profile_marker.opened) missed = true;
    for (i = 0; !missed && i < ARRAY_SIZE(msu_task_profile_marker.probes); ++i) {
        struct kretprobe *probe = msu_task_profile_marker.probes[i];
        if (!probe || READ_ONCE(*(int *)((unsigned char *)probe +
                msu_task_profile_marker_target.nmissed_offset))) missed = true;
    }
    raw_spin_unlock_irqrestore(&msu_task_profile_marker_lock, irq);
    return missed;
}

static void msu_task_profile_marker_close(void)
{
    struct kretprobe *probes[ARRAY_SIZE(msu_task_profile_marker.probes)];
    unsigned long irq;
    unsigned int i;
    /* Readiness and callback scans share this withdrawal lock. Native
     * unregister waits for handlers after the pointers have become private. */
    raw_spin_lock_irqsave(&msu_task_profile_marker_lock, irq);
    WRITE_ONCE(msu_task_profile_marker.opened, false);
    for (i = 0; i < ARRAY_SIZE(probes); ++i) {
        probes[i] = msu_task_profile_marker.probes[i];
        msu_task_profile_marker.probes[i] = NULL;
    }
    raw_spin_unlock_irqrestore(&msu_task_profile_marker_lock, irq);
    for (i = ARRAY_SIZE(probes); i; --i) {
        if (!probes[i - 1]) continue;
        unregister_kretprobe(probes[i - 1]);
        kfree(probes[i - 1]);
    }
}

static int msu_task_profile_marker_open(void)
{
    const struct msu_task_profile_marker_binding *m = &msu_task_profile_marker_target;
    unsigned long irq;
    unsigned int i;
    int error;
    if (!IS_ENABLED(CONFIG_KRETPROBES) || !msu_task_profile_marker_ready()) return -EOPNOTSUPP;
    for (i = 0; i < ARRAY_SIZE(msu_task_profile_marker.probes); ++i) {
        struct kretprobe *probe = kzalloc(m->kretprobe_bytes, GFP_KERNEL);
        if (!probe) { error = -ENOMEM; goto fail; }
        *(kretprobe_handler_t *)((unsigned char *)probe + m->handler_offset) = msu_task_profile_marker_return;
        if (i >= 2)
            *(kretprobe_handler_t *)((unsigned char *)probe + m->entry_handler_offset) = msu_task_profile_marker_entry;
        *(unsigned long *)((unsigned char *)probe + m->kp_addr_offset) = msu_task_profile_marker.addresses[i];
        error = register_kretprobe(probe);
        if (error) { kfree(probe); goto fail; }
        msu_task_profile_marker.probes[i] = probe;
    }
    raw_spin_lock_irqsave(&msu_task_profile_marker_lock, irq);
    WRITE_ONCE(msu_task_profile_marker.opened, true);
    raw_spin_unlock_irqrestore(&msu_task_profile_marker_lock, irq);
    return 0;
fail:
    msu_task_profile_marker_close();
    return error;
}

int msu_task_profile_trace_bind(unsigned long (*lookup)(const char *))
{
    static const char *const names[] = { "syscall_regfunc", "syscall_unregfunc",
        "tracepoint_probe_register_prio", "tracepoint_probe_unregister" };
    unsigned long address;
    unsigned int i;
    int error = 0;
    if (!lookup) return -EINVAL;
    if (!in_task() || in_atomic() || irqs_disabled()) return -EWOULDBLOCK;
    mutex_lock(&msu_task_profile_trace_lock);
    if (msu_task_profile_trace_registered) { error = -EBUSY; goto out; }
#if defined(MSU_INIT_BRIDGE_HOST_IMPORTS) && defined(MSU_INIT_BRIDGE_RUNTIME_FACTS)
    error = msu_task_profile_runtime_produce(lookup);
    if (error) {
        msu_task_profile_trace_bind_error = error;
        msu_task_profile_tracepoint = NULL;
        goto out;
    }
#endif
    address = lookup("__tracepoint_sys_enter");
    if (!address) error = -ENOENT;
    else if (address % __alignof__(struct tracepoint)) error = -EINVAL;
    if (!error) {
        msu_task_profile_marker.exit_tracepoint = (void *)lookup("__tracepoint_sys_exit");
        msu_task_profile_marker.init_task = (void *)lookup("init_task");
        if (!msu_task_profile_marker.exit_tracepoint || !msu_task_profile_marker.init_task) error = -ENOENT;
        else if ((unsigned long)msu_task_profile_marker.exit_tracepoint % __alignof__(struct tracepoint) ||
            (unsigned long)msu_task_profile_marker.init_task % __alignof__(struct task_struct)) error = -EINVAL;
        for (i = 0; !error && i < ARRAY_SIZE(names); ++i) {
            msu_task_profile_marker.addresses[i] = lookup(names[i]);
            if (!msu_task_profile_marker.addresses[i]) error = -ENOENT;
            else if (msu_task_profile_marker.addresses[i] & 3UL) error = -EINVAL;
        }
    }
    msu_task_profile_trace_bind_error = error;
    msu_task_profile_tracepoint = error ? NULL : (struct tracepoint *)address;
out:
    mutex_unlock(&msu_task_profile_trace_lock);
    return error;
}

bool msu_task_profile_trace_ready(void)
{
    return smp_load_acquire(&msu_task_profile_trace_active) && msu_task_profile_target_ready() &&
        msu_task_profile_marker_ready() && !msu_task_profile_marker_missed();
}

/* The native syscall tracepoint cannot sleep or run the Profile transaction.
 * It consumes only the already committed current-task disable-root marker.
 * The existing permanent SU gate retains the actual lease/Profile execution.
 * Generic, compat and unmarked ordinary calls leave all registers unchanged. */
void msu_task_profile_sys_enter(void *data, struct pt_regs *regs, long id)
{
#if MSU_TASK_PROFILE_NATIVE
    static const char su_path[] = "/system/bin/su";
    char path[sizeof(su_path)];
    const char __user *filename;
    if (data != &msu_task_profile_trace_registered || !regs ||
        !msu_task_profile_trace_ready() || is_compat_task() ||
        !test_bit(MSU_TASK_PROFILE_DISABLE_ROOT_BIT,
            &MSU_DYN_FIELD(msu_dynamic_task_thread_info(current), thread_info, flags))) return;
    if (id == __NR_execve) filename = (const char __user *)(unsigned long)regs->regs[0];
    else if (id == __NR_execveat) filename = (const char __user *)(unsigned long)regs->regs[1];
    else return;
    if (copy_from_user_nofault(path, filename, sizeof(path)) ||
        memcmp(path, su_path, sizeof(path))) return;
    /* Consume the native ARM64 sys_enter contract used by fixed KernelSU:
     * the dispatcher reloads syscallno after invoking the tracepoint. */
    WRITE_ONCE(regs->regs[0], (unsigned long)-EPERM);
    WRITE_ONCE(regs->syscallno, -1);
#else
    (void)data; (void)regs; (void)id;
#endif
}
void (*msu_task_profile_sys_enter_type)(void *, struct pt_regs *, long)
    __attribute__((used)) = msu_task_profile_sys_enter;

int msu_task_profile_trace_open(void)
{
    int error;
    if (!MSU_TASK_PROFILE_NATIVE || !msu_task_profile_target_ready()) return -EOPNOTSUPP;
    if (!in_task() || in_atomic() || irqs_disabled()) return -EWOULDBLOCK;
    mutex_lock(&msu_task_profile_trace_lock);
    if (msu_task_profile_trace_registered) {
        error = msu_task_profile_trace_ready() ? 0 : -EINPROGRESS;
        goto out;
    }
    error = msu_task_profile_trace_bind_error;
    if (error) goto out;
    error = msu_task_profile_marker_open();
    if (error) goto out;
    error = tracepoint_probe_register_prio(msu_task_profile_tracepoint,
        (void *)msu_task_profile_sys_enter, &msu_task_profile_trace_registered, INT_MIN);
    if (error) { msu_task_profile_marker_close(); goto out; }
    msu_task_profile_trace_registered = true;
    msu_task_profile_marker_update();
    if (msu_task_profile_marker_missed()) { error = -EIO; goto out; }
    smp_store_release(&msu_task_profile_trace_active, true);
out:
    mutex_unlock(&msu_task_profile_trace_lock);
    return error;
}

int msu_task_profile_trace_close(void)
{
    int error = 0;
    /* Even an atomic retire attempt closes new admission before reporting the
     * sleepable unregister continuation; the owning image stays retained. */
    smp_store_release(&msu_task_profile_trace_active, false);
    if (!in_task() || in_atomic() || irqs_disabled()) return -EWOULDBLOCK;
    mutex_lock(&msu_task_profile_trace_lock);
    if (!msu_task_profile_trace_registered) goto out;
    error = tracepoint_probe_unregister(msu_task_profile_tracepoint,
        (void *)msu_task_profile_sys_enter, &msu_task_profile_trace_registered);
    if (error && error != -ENOENT) goto out;
    tracepoint_synchronize_unregister();
    msu_task_profile_trace_registered = false;
    msu_task_profile_marker_close();
    error = 0;
out:
    mutex_unlock(&msu_task_profile_trace_lock);
    return error;
}

int msu_task_profile_flags_supported(u64 flags)
{
    if (flags & ~MSU_TASK_PROFILE_DISABLE_ROOT) return -EINVAL;
    if (!msu_runtime_layout_ready()) return -EPROTO;
    return MSU_TASK_PROFILE_NATIVE && msu_task_profile_trace_ready() ? 0 : -EOPNOTSUPP;
}

#if MSU_TASK_PROFILE_NATIVE
struct msu_task_profile_identity {
    struct task_struct *task;
    const struct cred *cred;
    struct mm_struct *mm;
    u64 exec_id;
};

static int msu_task_profile_validate(const struct msu_task_profile_identity *held,
    int (*validate)(void *context), void *context)
{
    int error;
    if (!msu_task_profile_trace_ready()) return -ESHUTDOWN;
    error = validate(context);
    if (error) return error;
    if (current != held->task || READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm)) != held->mm ||
        READ_ONCE(MSU_DYN_FIELD(current, task_struct, self_exec_id)) != held->exec_id ||
        (READ_ONCE(MSU_DYN_FIELD(current, task_struct, flags)) & PF_EXITING) ||
        msu_dynamic_current_cred() != held->cred || msu_dynamic_current_real_cred() != held->cred)
        return -ESTALE;
    return 0;
}

static int msu_task_profile_hold(struct msu_task_profile_identity *held)
{
    if (!in_task() || in_atomic() || irqs_disabled()) return -EWOULDBLOCK;
    if (!READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm)) || (READ_ONCE(MSU_DYN_FIELD(current, task_struct, flags)) & PF_EXITING) ||
        msu_dynamic_current_cred() != msu_dynamic_current_real_cred()) return -EPERM;
    held->task = current;
    held->cred = msu_dynamic_get_current_cred();
    held->mm = READ_ONCE(MSU_DYN_FIELD(current, task_struct, mm));
    msu_dynamic_mmgrab(held->mm);
    held->exec_id = READ_ONCE(MSU_DYN_FIELD(current, task_struct, self_exec_id));
    return 0;
}

static void msu_task_profile_put(const struct msu_task_profile_identity *held)
{
    msu_dynamic_mmdrop(held->mm);
    msu_dynamic_put_cred(held->cred);
}
#endif

int msu_task_profile_check_current(int (*validate)(void *context), void *context)
{
    int error;
    if (!validate) return -EINVAL;
    error = msu_task_profile_flags_supported(0);
    if (error) return error;
#if MSU_TASK_PROFILE_NATIVE
    {
        struct msu_task_profile_identity held;
        error = msu_task_profile_hold(&held);
        if (error) return error;
        error = msu_task_profile_validate(&held, validate, context);
        if (!error && test_bit(MSU_TASK_PROFILE_DISABLE_ROOT_BIT, &MSU_DYN_FIELD(msu_dynamic_task_thread_info(current), thread_info, flags))) error = -EPERM;
        if (!error) error = msu_task_profile_validate(&held, validate, context);
        if (!error && test_bit(MSU_TASK_PROFILE_DISABLE_ROOT_BIT, &MSU_DYN_FIELD(msu_dynamic_task_thread_info(current), thread_info, flags))) error = -EPERM;
        msu_task_profile_put(&held);
    }
#endif
    return error;
}

int msu_task_profile_apply_current(u64 flags,
    int (*validate)(void *context), void *context,
    struct msu_task_profile_receipt *receipt)
{
    int error;
    if (!receipt) return -EINVAL;
    *receipt = (struct msu_task_profile_receipt){0};
    error = msu_task_profile_flags_supported(flags);
    if (error) goto done;
    if (!validate) { error = -EINVAL; goto done; }
#if MSU_TASK_PROFILE_NATIVE
    {
        struct msu_task_profile_identity held;
        struct task_struct *thread;
        struct list_head *head, *node;
        error = msu_task_profile_hold(&held);
        if (error) goto done;
        error = msu_task_profile_validate(&held, validate, context);
        if (error) goto release;
        if ((flags & MSU_TASK_PROFILE_DISABLE_ROOT) &&
            !test_and_set_bit(MSU_TASK_PROFILE_DISABLE_ROOT_BIT, &MSU_DYN_FIELD(msu_dynamic_task_thread_info(current), thread_info, flags)))
            receipt->effects |= MSU_TASK_PROFILE_DISABLE_ROOT_CHANGED;
        rcu_read_lock();
        head = &MSU_DYN_FIELD(MSU_DYN_FIELD(held.task, task_struct, signal), signal_struct, thread_head);
        for (node = head->next; node != head; node = node->next) {
            thread = MSU_DYN_CONTAINER(node, task_struct, thread_node);
            receipt->threads_seen++;
            if (!test_and_set_bit(msu_task_profile_target.work_bit,
                msu_task_profile_work(thread))) {
                receipt->threads_changed++;
                receipt->effects |= MSU_TASK_PROFILE_TRACEPOINT_CHANGED;
            }
        }
        rcu_read_unlock();
        receipt->effects |= MSU_TASK_PROFILE_COMPLETED;
        error = msu_task_profile_validate(&held, validate, context);
release:
        msu_task_profile_put(&held);
    }
#endif
done:
    receipt->error = error;
    return error;
}
