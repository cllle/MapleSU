// SPDX-License-Identifier: GPL-3.0-only
#ifndef MAPLESU_MANAGER_TASK_STATE_H
#define MAPLESU_MANAGER_TASK_STATE_H

#include <linux/types.h>

/* Trusted current-task operation, not a user-selected target or qualification.
 * The producer must prove this call runs after the current syscall's seccomp
 * evaluator, and qualify the native helper, layouts, bit operation and CFI.
 * The validator runs outside siglock before and after the state transaction.
 * Completed includes an already-disabled or CONFIG_SECCOMP=n no-op; changed records the actual
 * transition even if the final qualification fails. previous_mode is published
 * only for a completed state transaction. No error authorizes replay.
 *
 * Only this task's mode/filter/count/syscall work are changed. Native release
 * drops exactly its detached filter reference, preserving listener and other
 * task references. No nnp, speculative-mitigation or other task flags change.
 * The task's original credential and mm identity references remain held through
 * native release and final validation. Invalid state combinations fail before
 * mutation; disabled state with residual filter/count/work is not repaired.
 * A final siglock observation rejects a filter attached during release or
 * validation, retaining completed effects. A later TSYNC can still attach a
 * new filter; this is not a permanent exclusion and no failure is retried. */
int msu_task_state_release_current(int (*validate)(void *context), void *context,
    u32 *effects, u64 *previous_mode);

/* Separate layout facts; existing management/procattr words remain unchanged.
 * These facts are build inputs, not proof of target provenance or execution. */
enum msu_task_state_layout_word {
    MSU_TASK_STATE_SCHEMA, MSU_TASK_STATE_KERNEL_VERSION,
    MSU_TASK_STATE_SECCOMP, MSU_TASK_STATE_SECCOMP_FILTER,
    MSU_TASK_STATE_GENERIC_ENTRY, MSU_TASK_STATE_THREAD_INFO_IN_TASK,
    MSU_TASK_STATE_PREEMPT_RT,
    MSU_TASK_STATE_TASK_SIZE, MSU_TASK_STATE_TASK_ALIGN,
    MSU_TASK_STATE_TASK_SECCOMP_OFFSET, MSU_TASK_STATE_TASK_SECCOMP_BYTES,
    MSU_TASK_STATE_TASK_SIGHAND_OFFSET, MSU_TASK_STATE_TASK_SIGHAND_BYTES,
    MSU_TASK_STATE_TASK_THREAD_INFO_OFFSET, MSU_TASK_STATE_TASK_THREAD_INFO_BYTES,
    MSU_TASK_STATE_TASK_STACK_OFFSET, MSU_TASK_STATE_TASK_STACK_BYTES,
    MSU_TASK_STATE_SECCOMP_SIZE, MSU_TASK_STATE_SECCOMP_ALIGN,
    MSU_TASK_STATE_MODE_OFFSET, MSU_TASK_STATE_MODE_BYTES,
    MSU_TASK_STATE_FILTER_COUNT_OFFSET, MSU_TASK_STATE_FILTER_COUNT_BYTES,
    MSU_TASK_STATE_FILTER_OFFSET, MSU_TASK_STATE_FILTER_BYTES,
    MSU_TASK_STATE_SIGHAND_SIZE, MSU_TASK_STATE_SIGHAND_ALIGN,
    MSU_TASK_STATE_SIGLOCK_OFFSET, MSU_TASK_STATE_SIGLOCK_BYTES,
    MSU_TASK_STATE_THREAD_INFO_SIZE, MSU_TASK_STATE_THREAD_INFO_ALIGN,
    MSU_TASK_STATE_WORK_OFFSET, MSU_TASK_STATE_WORK_BYTES, MSU_TASK_STATE_WORK_BIT,
    MSU_TASK_STATE_SPINLOCK_SIZE, MSU_TASK_STATE_SPINLOCK_ALIGN,
    MSU_TASK_STATE_SPINLOCK_RAW_OFFSET, MSU_TASK_STATE_SPINLOCK_RAW_BYTES,
    MSU_TASK_STATE_RAW_SPINLOCK_SIZE, MSU_TASK_STATE_RAW_SPINLOCK_ALIGN,
    MSU_TASK_STATE_RAW_LOCK_OFFSET, MSU_TASK_STATE_RAW_LOCK_BYTES,
    MSU_TASK_STATE_RAW_MAGIC_OFFSET, MSU_TASK_STATE_RAW_MAGIC_BYTES,
    MSU_TASK_STATE_RAW_CPU_OFFSET, MSU_TASK_STATE_RAW_CPU_BYTES,
    MSU_TASK_STATE_RAW_OWNER_OFFSET, MSU_TASK_STATE_RAW_OWNER_BYTES,
    MSU_TASK_STATE_RAW_DEP_MAP_OFFSET, MSU_TASK_STATE_RAW_DEP_MAP_BYTES,
    MSU_TASK_STATE_DISABLED_MODE, MSU_TASK_STATE_STRICT_MODE, MSU_TASK_STATE_FILTER_MODE,
    MSU_TASK_STATE_LAYOUT_WORDS,
};
extern const unsigned int msu_task_state_compiled_layout[MSU_TASK_STATE_LAYOUT_WORDS];

/* Independent kernel-private Profile tail, not management wire or Linux nnp.
 * Enabling requires the actual target's fixed KSU bit63, embedded thread_info,
 * native atomic/RCU and tracepoint ABI. The native KMI route retains KSU's
 * clone/exec lifecycle; the exact Image route has its separate code proof. */
#ifndef MSU_TASK_PROFILE_FLAGS_VERIFIED
#define MSU_TASK_PROFILE_FLAGS_VERIFIED 0
#endif
#define MSU_TASK_PROFILE_DISABLE_ROOT (1ULL << 0)
#define MSU_TASK_PROFILE_DISABLE_ROOT_BIT 63

#define MSU_TASK_PROFILE_TARGET_MAGIC 0x4d535450U
#define MSU_TASK_PROFILE_TARGET_SCHEMA 1U
#define MSU_TASK_PROFILE_TARGET_PROOFS 0x3fU
#define MSU_TASK_PROFILE_TARGET_IMAGE 1U
#define MSU_TASK_PROFILE_TARGET_RUNTIME 2U
#define MSU_TASK_PROFILE_RUNTIME_PROOFS 0x1fU
/* bound=1 is the existing Image/BTF materializer's six-proof receipt, including
 * its Image/config/proof hashes. bound=2 identifies the live-kernel producer:
 * five live/private plus native DDK API facts, KSU clone/exec lifecycle, and zero offline
 * hashes. It is accepted only with the producer's private publication receipt.
 * Offsets remain in the shared dynamic table; work_bit is decoded from the
 * actual running kernel's syscall-entry instructions, never a family constant. */
struct msu_task_profile_target_binding {
    u32 magic, schema, bytes, bound, proof_mask, reserved;
    u32 root_bit, work_member, work_bit, compat;
    unsigned char target_sha256[32], config_sha256[32], proof_sha256[32];
};
extern struct msu_task_profile_target_binding msu_task_profile_target;

#define MSU_TASK_PROFILE_MARKER_MAGIC 0x4d53544dU
/* Target-private task/tracepoint facts plus the selected native DDK's probe
 * API receipt. BTF qualifies private facts, not native API objects. Legacy
 * rwlock descriptors remain schema-compatible but are not consumed by the
 * synchronous RCU marker. All used pointer/count fields are bounds checked. */
struct msu_task_profile_marker_binding {
    u32 magic, schema, bytes, bound;
    u32 task_bytes, tasks_offset, tasks_bytes;
    u32 tracepoint_bytes, funcs_offset, funcs_bytes;
    u32 func_bytes, callback_offset, callback_bytes, data_offset, data_bytes;
    u32 kretprobe_bytes, handler_offset, handler_bytes, kp_addr_offset, kp_addr_bytes;
    u32 maxactive_offset, maxactive_bytes, nmissed_offset, nmissed_bytes;
    u32 rwlock_bytes, rwlock_align, entry_handler_offset, entry_handler_bytes;
};
extern struct msu_task_profile_marker_binding msu_task_profile_marker_target;
struct task_struct;
/* Atomic marking hint only. A published UID row never grants credentials. */
bool msu_su_runtime_trace_mark_task(struct task_struct *task);

enum msu_task_profile_effect {
    MSU_TASK_PROFILE_DISABLE_ROOT_CHANGED = 1U << 0,
    MSU_TASK_PROFILE_TRACEPOINT_CHANGED = 1U << 1,
    MSU_TASK_PROFILE_COMPLETED = 1U << 2,
};
struct msu_task_profile_receipt {
    u32 effects, threads_seen, threads_changed;
    int error;
};

/* Structural support only: never examines the publishing producer's task.
 * Unknown flags return EINVAL; even flags=0 requires the native marker stage. */
int msu_task_profile_flags_supported(u64 flags);

/* Bind the actual running kernel's native sys_enter object. Binding is not
 * registration or qualification; the actual lookup error is retained by open.
 * Open is called only for the installed SU generation, outside atomic paths.
 * Close first blocks admission, unregisters this exact callback/data pair and
 * drains native readers. A failed close keeps the owning image live. */
int msu_task_profile_trace_bind(unsigned long (*lookup)(const char *));
int msu_task_profile_trace_open(void);
int msu_task_profile_trace_close(void);
bool msu_task_profile_trace_ready(void);
struct pt_regs;
struct kretprobe_instance;
void msu_task_profile_sys_enter(void *data, struct pt_regs *regs, long id);
int msu_task_profile_marker_entry(struct kretprobe_instance *instance, struct pt_regs *regs);
int msu_task_profile_marker_return(struct kretprobe_instance *instance, struct pt_regs *regs);

/* Independent compiled facts, never appended to the old 26-word layout. */
enum msu_task_profile_trace_layout_word {
    MSU_TASK_PROFILE_TRACE_SCHEMA, MSU_TASK_PROFILE_TRACE_KERNEL_VERSION,
    MSU_TASK_PROFILE_TRACE_NATIVE_ENABLED,
    MSU_TASK_PROFILE_TRACE_REGS_SIZE, MSU_TASK_PROFILE_TRACE_REGS_ALIGN,
    MSU_TASK_PROFILE_TRACE_ARG0_OFFSET, MSU_TASK_PROFILE_TRACE_ARG0_BYTES,
    MSU_TASK_PROFILE_TRACE_SYSCALLNO_OFFSET, MSU_TASK_PROFILE_TRACE_SYSCALLNO_BYTES,
    MSU_TASK_PROFILE_TRACE_EXECVE_NR, MSU_TASK_PROFILE_TRACE_EXECVEAT_NR,
    MSU_TASK_PROFILE_TRACE_COMPAT, MSU_TASK_PROFILE_TRACE_LAYOUT_WORDS,
};
extern const unsigned int msu_task_profile_trace_compiled_layout[MSU_TASK_PROFILE_TRACE_LAYOUT_WORDS];

/* Admission before prepare/commit, using the held operation's raw validator.
 * No mutation/allocation. Do not embed this check in that raw validator: the
 * operation must finish normally after it sets its own disable-root flag. */
int msu_task_profile_check_current(int (*validate)(void *context), void *context);

/* After successful credential commit and seccomp release, before namespace.
 * Hold current cred/mm/exec through both validations, outside native RCU.
 * Set bit63 only on current when bit0 is requested; flags=0 never clears it.
 * Mark all observed threads, including current/leader, with native tracepoint
 * work. RCU protects their embedded flags, not a concurrent-clone snapshot.
 * Completed records the mutation pass, even if final validation fails. All
 * effects/counts survive errors; no rollback or automatic replay is implied. */
int msu_task_profile_apply_current(u64 flags,
    int (*validate)(void *context), void *context,
    struct msu_task_profile_receipt *receipt);

/* Separate from the unchanged 53-word seccomp layout and all public ABIs. */
enum msu_task_profile_layout_word {
    MSU_TASK_PROFILE_SCHEMA, MSU_TASK_PROFILE_KERNEL_VERSION,
    MSU_TASK_PROFILE_BUILD_VERIFIED, MSU_TASK_PROFILE_NATIVE_ENABLED,
    MSU_TASK_PROFILE_GENERIC_ENTRY, MSU_TASK_PROFILE_THREAD_INFO_IN_TASK,
    MSU_TASK_PROFILE_TASK_SIZE, MSU_TASK_PROFILE_TASK_ALIGN,
    MSU_TASK_PROFILE_TASK_THREAD_INFO_OFFSET, MSU_TASK_PROFILE_TASK_THREAD_INFO_BYTES,
    MSU_TASK_PROFILE_TASK_SIGNAL_OFFSET, MSU_TASK_PROFILE_TASK_SIGNAL_BYTES,
    MSU_TASK_PROFILE_THREAD_INFO_SIZE, MSU_TASK_PROFILE_THREAD_INFO_ALIGN,
    MSU_TASK_PROFILE_FLAGS_OFFSET, MSU_TASK_PROFILE_FLAGS_BYTES,
    MSU_TASK_PROFILE_ROOT_BIT, MSU_TASK_PROFILE_WORK_OFFSET,
    MSU_TASK_PROFILE_WORK_BYTES, MSU_TASK_PROFILE_WORK_BIT,
    MSU_TASK_PROFILE_SIGNAL_SIZE, MSU_TASK_PROFILE_SIGNAL_ALIGN,
    MSU_TASK_PROFILE_THREAD_HEAD_OFFSET, MSU_TASK_PROFILE_THREAD_HEAD_BYTES,
    MSU_TASK_PROFILE_THREAD_NODE_OFFSET, MSU_TASK_PROFILE_THREAD_NODE_BYTES,
    MSU_TASK_PROFILE_LAYOUT_WORDS,
};
extern const unsigned int msu_task_profile_compiled_layout[MSU_TASK_PROFILE_LAYOUT_WORDS];

#endif
