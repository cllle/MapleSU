// SPDX-License-Identifier: GPL-3.0-only
#include <linux/compiler.h>
#include <linux/errno.h>
#include <linux/kernel.h>
#include <linux/kprobes.h>
#include <linux/sched.h>
#include <linux/slab.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <linux/thread_info.h>
#include <linux/uaccess.h>
#include <asm/ptrace.h>
#include <asm/unistd.h>
#include <trace/events/syscalls.h>
#include "manager_task_profile_runtime.h"
#include "manager_task_state.h"
#include "manager_dynamic_layout.h"
#include "manager_runtime_btf.h"
#include "manager_runtime_accessor.h"
#include "native_cfi.h"
#include "native_lookup.h"
#if MSU_INIT_BRIDGE_CFI_LEGACY && defined(MODULE)
#include "native_legacy_cfi.h"
#endif
#include "manager_task_profile_workbit.h"

#if defined(MSU_INIT_BRIDGE_HOST_IMPORTS) && defined(MSU_INIT_BRIDGE_RUNTIME_FACTS)
static bool msu_task_profile_live_published;

static int msu_profile_value(u32 id, u32 kind, u32 bytes, const char *name)
{
    struct msu_runtime_btf_type type;
    int error = msu_runtime_btf_type(id, &type);
    if (error) return error;
    return type.kind == kind && type.bytes == bytes &&
        (!name || (type.name && !strcmp(type.name, name))) ? 0 : -EPROTO;
}

#if !MSU_INIT_BRIDGE_CFI_KCFI
static int msu_profile_pointer(u32 id, const char *name)
{
    struct msu_runtime_btf_type type;
    int error = msu_runtime_btf_type(id, &type);
    if (error) return error;
    if (type.kind != 2 || type.bytes != sizeof(void *)) return -EPROTO;
    error = msu_runtime_btf_type(type.target_id, &type);
    if (error) return error;
    if (!name) return type.kind == 0 ? 0 : -EPROTO;
    return type.kind == 4 && type.name && !strcmp(type.name, name) ? 0 : -EPROTO;
}
#endif

static int msu_profile_dynamic_field(const char *owner, const char *name, u32 slot)
{
    const struct msu_runtime_layout_field *bound = &msu_runtime_layout.values[slot];
    struct msu_runtime_btf_field field;
    int error = msu_runtime_facts_field(owner, name, &field);
    if (error) return error;
    return bound->offset == field.offset && bound->bytes == field.bytes &&
        bound->owner_bytes == field.owner_bytes ? 0 : -ESTALE;
}

_Static_assert(__builtin_types_compatible_p(typeof(&msu_task_profile_marker_entry),
    kretprobe_handler_t), "native marker entry callback");
_Static_assert(__builtin_types_compatible_p(typeof(&msu_task_profile_marker_return),
    kretprobe_handler_t), "native marker return callback");

static int msu_profile_trace_callback(unsigned long (*lookup)(const char *))
{
    (void)lookup;
    /* Use the generated native event API, as fixed KernelSU does. A named
     * emitted peer is not required; actual kernel callback calls retain CFI. */
#ifdef CONFIG_HAVE_SYSCALL_TRACEPOINTS
    check_trace_callback_type_sys_enter(msu_task_profile_sys_enter);
    return 0;
#else
    return -EOPNOTSUPP;
#endif
}

static int msu_profile_marker(struct msu_task_profile_marker_binding *marker)
{
    struct msu_runtime_btf_field field;
    int error;
    memset(marker, 0, sizeof(*marker));
    marker->magic = MSU_TASK_PROFILE_MARKER_MAGIC;
    marker->schema = 1;
    marker->bytes = sizeof(*marker);
#define MSU_PROFILE_MARKER(owner, member, owner_member, offset_member, bytes_member, expected) do { \
    error = msu_runtime_facts_field(owner, member, &field); \
    if (error) return error; \
    if (field.bytes != (expected) || field.offset % min_t(u32, expected, sizeof(void *))) return -EPROTO; \
    marker->owner_member = field.owner_bytes; marker->offset_member = field.offset; \
    marker->bytes_member = field.bytes; \
} while (0)
    MSU_PROFILE_MARKER("task_struct", "tasks", task_bytes, tasks_offset, tasks_bytes, sizeof(struct list_head));
    error = field.type_id ? msu_profile_value(field.type_id, 4, sizeof(struct list_head), "list_head") : 0;
    if (error) return error;
    MSU_PROFILE_MARKER("tracepoint", "funcs", tracepoint_bytes, funcs_offset, funcs_bytes, sizeof(void *));
    MSU_PROFILE_MARKER("tracepoint_func", "func", func_bytes, callback_offset, callback_bytes, sizeof(void *));
    MSU_PROFILE_MARKER("tracepoint_func", "data", func_bytes, data_offset, data_bytes, sizeof(void *));
#undef MSU_PROFILE_MARKER
    /* Native API objects and callback types use the selected DDK, as in fixed
     * KernelSU. Optional BTF is not another qualification for those objects. */
    marker->kretprobe_bytes = sizeof(struct kretprobe);
#define MSU_PROFILE_PROBE_FIELD(member, offset_member, bytes_member) do { \
    marker->offset_member = offsetof(struct kretprobe, member); \
    marker->bytes_member = sizeof(((struct kretprobe *)0)->member); \
} while (0)
    MSU_PROFILE_PROBE_FIELD(handler, handler_offset, handler_bytes);
    MSU_PROFILE_PROBE_FIELD(entry_handler, entry_handler_offset, entry_handler_bytes);
    MSU_PROFILE_PROBE_FIELD(kp.addr, kp_addr_offset, kp_addr_bytes);
    MSU_PROFILE_PROBE_FIELD(maxactive, maxactive_offset, maxactive_bytes);
    MSU_PROFILE_PROBE_FIELD(nmissed, nmissed_offset, nmissed_bytes);
#undef MSU_PROFILE_PROBE_FIELD
    /* Retained for the existing receipt schema; the RCU marker does not use
     * tasklist_lock or qualify a target lock through these DDK descriptors. */
    marker->rwlock_bytes = sizeof(rwlock_t);
    marker->rwlock_align = __alignof__(rwlock_t);
    return 0;
}

static noinline long msu_profile_syscall_reference(const struct pt_regs *regs)
{
    return regs ? -EOPNOTSUPP : -EINVAL;
}

static int msu_profile_regs(unsigned long (*lookup)(const char *))
{
    static const char *const names[] = { "__arm64_sys_execve", "__arm64_sys_execveat" };
    const u32 numbers[] = { __NR_execve, __NR_execveat };
    unsigned long table, bytes, address, entry;
    u32 i;
#if !MSU_INIT_BRIDGE_CFI_KCFI
    struct msu_runtime_btf_function function;
#endif
    int error;
    error = msu_init_bridge_lookup_size("sys_call_table", &table, &bytes);
    if (error) return error;
    if (table != lookup("sys_call_table") || table % sizeof(void *) || bytes > 8192) return -EPROTO;
    for (i = 0; i < ARRAY_SIZE(names); ++i) {
        if (bytes < (numbers[i] + 1) * sizeof(void *)) return -EPROTO;
#if !MSU_INIT_BRIDGE_CFI_KCFI
        error = msu_runtime_btf_function(names[i], &function);
        if (error && error != -ENOENT) return error;
        if (!error) {
            if (function.variadic || function.argument_count != 1) return -EPROTO;
            error = msu_profile_value(function.return_type, 1, sizeof(long), NULL);
            if (!error) error = msu_profile_pointer(function.arguments[0], "pt_regs");
            if (error) return error;
        }
#endif
        error = msu_init_bridge_resolve_cfi(names[i], msu_profile_syscall_reference, &address);
        if (error) return error;
        if (copy_from_kernel_nofault(&entry, (void *)(table + numbers[i] * sizeof(void *)), sizeof(entry))) return -EFAULT;
        if (entry != address) return -ESTALE;
    }
    return 0;
}

static int msu_profile_workbit(unsigned long (*lookup)(const char *), u32 member, u32 *output)
{
    const char *name = member ? "syscall_enter_from_user_mode_work" : "syscall_trace_enter";
    struct msu_profile_work_program program = { .initial_work_register = -1 };
    struct msu_runtime_btf_function function;
    struct msu_runtime_btf_type type;
    struct msu_runtime_btf_field funcs, code_field, target_field;
    struct msu_profile_work_state *states = NULL;
    u32 *code = NULL, *pending = NULL, i;
    s32 *jumps = NULL;
    u8 *reach = NULL;
    unsigned long bytes, jump_start, jump_end, entry, pc, target;
    u32 jump_bytes;
    s32 relative;
    s64 explicit;
    int error = msu_init_bridge_lookup_size(name, &program.address, &bytes);
    if (error) return error;
    if (!bytes || bytes > 16384 || bytes % 4) return -EPROTO;
    program.words = bytes / 4;
    program.tracepoint = lookup("__tracepoint_sys_enter");
    program.iterators[0] = lookup("__traceiter_sys_enter");
    program.iterators[1] = lookup("__do_trace_sys_enter");
    if (!program.tracepoint) return -ENOENT;
    error = msu_runtime_facts_field("tracepoint", "funcs", &funcs);
    if (!error) error = msu_runtime_facts_type_size("jump_entry", &jump_bytes);
    if (!error) error = msu_runtime_facts_field("jump_entry", "code", &code_field);
    if (!error) error = msu_runtime_facts_field("jump_entry", "target", &target_field);
    if (error) return error;
    if (funcs.bytes != sizeof(void *) || code_field.bytes != sizeof(s32) ||
        target_field.bytes != sizeof(s32) || jump_bytes < 8 || jump_bytes > 64 ||
        jump_bytes % 4) return -EPROTO;
    program.tracepoint_funcs = program.tracepoint + funcs.offset;
    jump_start = lookup("__start___jump_table");
    jump_end = lookup("__stop___jump_table");
    if (!jump_start || jump_end < jump_start || jump_end - jump_start > 64UL * 1024 * 1024 ||
        (jump_end - jump_start) % jump_bytes) return -EPROTO;
    program.task_work_offset = msu_runtime_layout.values[MSU_DYN_task_struct_thread_info].offset +
        msu_runtime_layout.values[member ? MSU_DYN_thread_info_syscall_work : MSU_DYN_thread_info_flags].offset;
    if (member) {
        error = msu_runtime_btf_function(name, &function);
        if (error) return error;
        for (i = 0; i < function.argument_count && i < 8; ++i) {
            error = msu_runtime_btf_type(function.arguments[i], &type);
            if (error) return error;
            if (type.kind != 1 || type.bytes != sizeof(unsigned long)) continue;
            if (program.initial_work_register >= 0) return -EPROTO;
            program.initial_work_register = i;
        }
        if (program.initial_work_register < 0) return -EPROTO;
    }
    code = kmalloc(bytes, GFP_KERNEL);
    states = kvcalloc(program.words, sizeof(*states), GFP_KERNEL);
    pending = kmalloc_array(program.words, sizeof(*pending), GFP_KERNEL);
    reach = kmalloc_array(program.words, 2, GFP_KERNEL);
    jumps = kmalloc_array(program.words, sizeof(*jumps), GFP_KERNEL);
    if (!code || !states || !pending || !reach || !jumps) { error = -ENOMEM; goto out; }
    memset(jumps, 0xff, program.words * sizeof(*jumps));
    for (entry = jump_start; entry < jump_end; entry += jump_bytes) {
        if (copy_from_kernel_nofault(&relative, (void *)(entry + code_field.offset), sizeof(relative))) {
            error = -EFAULT; goto out;
        }
        pc = entry + code_field.offset + (long)relative;
        if (pc < program.address || pc - program.address >= bytes) continue;
        if (copy_from_kernel_nofault(&relative, (void *)(entry + target_field.offset), sizeof(relative))) {
            error = -EFAULT; goto out;
        }
        target = entry + target_field.offset + (long)relative;
        if ((pc & 3) || target < program.address || target - program.address >= bytes || (target & 3)) {
            error = -EPROTO; goto out;
        }
        i = (pc - program.address) / 4;
        relative = (target - program.address) / 4;
        if (jumps[i] >= 0 && jumps[i] != relative) { error = -ENOTUNIQ; goto out; }
        jumps[i] = relative;
    }
    if (copy_from_kernel_nofault(code, (void *)program.address, bytes)) { error = -EFAULT; goto out; }
    program.code = code;
    program.jump_targets = jumps;
    error = msu_profile_work_decode(&program, states, pending, reach, reach + program.words, output);
    if (!error && member) {
        error = msu_runtime_btf_enum("SYSCALL_WORK_BIT_SYSCALL_TRACEPOINT", &explicit);
        if (!error && explicit != *output) error = -ESTALE;
    }
out:
    kfree(jumps); kfree(reach); kfree(pending); kvfree(states); kfree(code);
    return error;
}

bool msu_task_profile_runtime_ready(void)
{
    return smp_load_acquire(&msu_task_profile_live_published);
}

noinline __used __visible int msu_task_profile_runtime_produce(unsigned long (*lookup)(const char *))
{
    struct msu_task_profile_target_binding target = {0};
    struct msu_task_profile_marker_binding marker;
    struct msu_runtime_btf_field flags, info, stack, work, list;
    u32 member = IS_ENABLED(CONFIG_GENERIC_ENTRY);
    s64 explicit;
    int error;
    if (!lookup || !msu_runtime_layout_ready()) return -EINVAL;
    if (smp_load_acquire(&msu_task_profile_target.bound) == MSU_TASK_PROFILE_TARGET_IMAGE) return 0;
    if (READ_ONCE(msu_task_profile_target.bound))
        return msu_task_profile_runtime_ready() ? 0 : -ESTALE;
    if (msu_task_profile_target.magic != MSU_TASK_PROFILE_TARGET_MAGIC ||
        msu_task_profile_target.schema != MSU_TASK_PROFILE_TARGET_SCHEMA ||
        msu_task_profile_target.bytes != sizeof(target) || msu_task_profile_target.reserved ||
        msu_task_profile_marker_target.magic != MSU_TASK_PROFILE_MARKER_MAGIC ||
        msu_task_profile_marker_target.schema != 1 || msu_task_profile_marker_target.bytes != sizeof(marker) ||
        READ_ONCE(msu_task_profile_marker_target.bound)) return -EPROTO;
    if (!IS_ENABLED(CONFIG_ARM64) || !IS_ENABLED(CONFIG_64BIT) ||
        !IS_ENABLED(CONFIG_THREAD_INFO_IN_TASK) || !IS_ENABLED(CONFIG_HAVE_SYSCALL_TRACEPOINTS) ||
        !IS_ENABLED(CONFIG_KRETPROBES)) return -EOPNOTSUPP;
    error = msu_runtime_facts_field("thread_info", "flags", &flags);
    if (!error) error = msu_runtime_facts_field("task_struct", "thread_info", &info);
    if (!error) error = msu_runtime_facts_field("task_struct", "stack", &stack);
    if (!error) error = msu_runtime_facts_field("thread_info", member ? "syscall_work" : "flags", &work);
    if (error) return error;
    if (flags.bytes != sizeof(unsigned long) || flags.kind != 1 || flags.offset % sizeof(unsigned long) ||
        info.kind != 4 || info.bytes != flags.owner_bytes || info.offset % sizeof(unsigned long) ||
        stack.kind != 2 || stack.bytes != sizeof(void *) || stack.offset % sizeof(void *) ||
        work.kind != 1 || work.bytes != sizeof(unsigned long) || work.offset % sizeof(unsigned long) ||
        work.owner_bytes != flags.owner_bytes) return -EPROTO;
    error = msu_runtime_btf_enum("TIF_KSU_DISABLE_ESCAPE_WITH_ROOT", &explicit);
    if (error && error != -ENOENT) return error;
    if (!error && explicit != MSU_TASK_PROFILE_DISABLE_ROOT_BIT) return -EPROTO;
#define MSU_PROFILE_DYNAMIC(owner, field) do { \
    error = msu_profile_dynamic_field(#owner, #field, MSU_DYN_##owner##_##field); \
    if (error) return error; \
} while (0)
    MSU_PROFILE_DYNAMIC(task_struct, thread_info);
    MSU_PROFILE_DYNAMIC(thread_info, flags);
    MSU_PROFILE_DYNAMIC(task_struct, signal);
    MSU_PROFILE_DYNAMIC(task_struct, thread_node);
    MSU_PROFILE_DYNAMIC(signal_struct, thread_head);
    if (member) { MSU_PROFILE_DYNAMIC(thread_info, syscall_work); }
#undef MSU_PROFILE_DYNAMIC
    error = msu_runtime_facts_field("task_struct", "thread_node", &list);
    if (!error && list.type_id) error = msu_profile_value(list.type_id, 4, sizeof(struct list_head), "list_head");
    if (!error && (list.bytes != sizeof(struct list_head) || list.offset % sizeof(void *))) error = -EPROTO;
    if (!error) error = msu_runtime_facts_field("signal_struct", "thread_head", &list);
    if (!error && list.type_id) error = msu_profile_value(list.type_id, 4, sizeof(struct list_head), "list_head");
    if (!error && (list.bytes != sizeof(struct list_head) || list.offset % sizeof(void *))) error = -EPROTO;
    if (error) return error;
    error = msu_profile_marker(&marker);
    if (!error) error = msu_profile_trace_callback(lookup);
    if (!error) error = msu_profile_regs(lookup);
    if (!error) error = msu_profile_workbit(lookup, member, &target.work_bit);
    if (error) return error;
#if MSU_INIT_BRIDGE_CFI_LEGACY && defined(MODULE)
    error = msu_legacy_module_ready();
    if (error) return error;
#endif
    target.magic = MSU_TASK_PROFILE_TARGET_MAGIC;
    target.schema = MSU_TASK_PROFILE_TARGET_SCHEMA;
    target.bytes = sizeof(target);
    target.proof_mask = MSU_TASK_PROFILE_RUNTIME_PROOFS;
    target.root_bit = MSU_TASK_PROFILE_DISABLE_ROOT_BIT;
    target.work_member = member;
    /* No offline Image/config/clone proof is fabricated for a native KMI LKM.
     * All temporary allocations are retired before publishing either table. */
    msu_task_profile_marker_target = marker;
    msu_task_profile_target = target;
    smp_store_release(&msu_task_profile_marker_target.bound, 1);
    smp_store_release(&msu_task_profile_live_published, true);
    smp_store_release(&msu_task_profile_target.bound, MSU_TASK_PROFILE_TARGET_RUNTIME);
    return 0;
}
#endif
