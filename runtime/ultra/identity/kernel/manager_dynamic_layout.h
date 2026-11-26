// SPDX-License-Identifier: GPL-3.0-only
#ifndef MAPLESU_MANAGER_DYNAMIC_LAYOUT_H
#define MAPLESU_MANAGER_DYNAMIC_LAYOUT_H
#include <linux/types.h>
#include <linux/compiler.h>
#include <linux/errno.h>
#include <linux/cred.h>
#include <linux/refcount.h>
#include <linux/preempt.h>
#include <linux/sched.h>
#include <linux/sched/mm.h>
#include <linux/sched/task.h>
#include <linux/version.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/fs_struct.h>
#include <linux/nsproxy.h>
#include "msu_resolver_build_config.h"

/* The exact-Image carrier or boot runtime producer binds these slots before
 * publishing ready. The table stays writable and non-const here:
 * folding compiled offsets back into native code defeats target relocation.
 * Slots are append-only. Local embedded kernel objects retain their separate
 * exact ABI qualification; this table does not resize those objects. */
#define MSU_RUNTIME_LAYOUT_MAGIC 0x4d53554cU
#define MSU_RUNTIME_LAYOUT_SCHEMA 1U
#define MSU_RUNTIME_LAYOUT_FIELDS(X) \
    X(task_struct, cred) \
    X(task_struct, real_cred) \
    X(task_struct, mm) \
    X(task_struct, self_exec_id) \
    X(task_struct, flags) \
    X(task_struct, usage) \
    X(task_struct, alloc_lock) \
    X(task_struct, comm) \
    X(task_struct, fs) \
    X(task_struct, stack) \
    X(task_struct, rcu) \
    X(task_struct, real_parent) \
    X(task_struct, nsproxy) \
    X(task_struct, thread_info) \
    X(task_struct, sighand) \
    X(task_struct, seccomp) \
    X(task_struct, signal) \
    X(task_struct, thread_node) \
    X(cred, usage) \
    X(cred, non_rcu) \
    X(cred, uid) \
    X(cred, gid) \
    X(cred, suid) \
    X(cred, sgid) \
    X(cred, euid) \
    X(cred, egid) \
    X(cred, fsuid) \
    X(cred, fsgid) \
    X(cred, securebits) \
    X(cred, cap_inheritable) \
    X(cred, cap_permitted) \
    X(cred, cap_effective) \
    X(cred, cap_bset) \
    X(cred, cap_ambient) \
    X(cred, user) \
    X(cred, user_ns) \
    X(cred, group_info) \
    X(cred, security) \
    X(fs_struct, root) \
    X(fs_struct, seq) \
    X(fs_struct, lock) \
    X(file, f_path) \
    X(file, f_inode) \
    X(file, f_op) \
    X(file, f_count) \
    X(inode, i_uid) \
    X(inode, i_gid) \
    X(inode, i_mode) \
    X(inode, i_sb) \
    X(inode, i_nlink) \
    X(inode, i_generation) \
    X(inode, i_writecount) \
    X(dentry, d_inode) \
    X(dentry, d_parent) \
    X(dentry, d_name) \
    X(qstr, len) \
    X(qstr, name) \
    X(super_block, s_magic) \
    X(file_operations, write) \
    X(mm_struct, mm_count) \
    X(mm_struct, mmap_lock) \
    X(vm_area_struct, vm_file) \
    X(vm_area_struct, vm_end) \
    X(sighand_struct, siglock) \
    X(seccomp, mode) \
    X(seccomp, filter_count) \
    X(seccomp, filter) \
    X(thread_info, flags) \
    X(thread_info, syscall_work) \
    X(thread_info, preempt_count) \
    X(signal_struct, thread_head) \
    X(nsproxy, mnt_ns) \
    X(nsproxy, count) \
    X(mount, mnt) \
    X(mount, mnt_mounts) \
    X(mount, mnt_child) \
    X(mount, mnt_parent) \
    X(mount, mnt_mountpoint) \
    X(mount, mnt_ns) \
    X(mount, mnt_id) \
    X(vfsmount, mnt_root) \
    X(file, f_ref) \
    X(vfsmount, mnt_idmap) \
    X(vfsmount, mnt_user_ns) \
    X(task_struct, pid) \
    X(task_struct, files) \
    X(inode, i_size) \
    X(file, f_pos) \
    X(file, f_mode) \
    X(file, f_flags) \
    X(file, f_cred) \
    X(file, private_data) \
    X(files_struct, file_lock) \
    X(files_struct, fdt) \
    X(files_struct, count) \
    X(files_struct, next_fd) \
    X(fdtable, max_fds) \
    X(fdtable, fd) \
    X(fdtable, open_fds) \
    X(fdtable, full_fds_bits) \
    X(fdtable, close_on_exec) \
    X(task_struct, atomic_flags) \
    X(fs_struct, pwd)

/* Canonical table keys stay stable when the kernel C member has another name. */
#define MSU_RUNTIME_LAYOUT_ALIASES(X) \
    X(vfsmount, mnt_user_ns, mnt_userns)

enum msu_runtime_layout_slot {
#define MSU_DYN_SLOT(type, member) MSU_DYN_##type##_##member,
    MSU_RUNTIME_LAYOUT_FIELDS(MSU_DYN_SLOT)
#undef MSU_DYN_SLOT
    MSU_RUNTIME_LAYOUT_FIELD_COUNT,
};
struct msu_runtime_layout_field { u32 offset, bytes, owner_bytes; };
struct msu_runtime_layout_table {
    u32 magic, schema, bytes, fields, ready, reserved;
    struct msu_runtime_layout_field values[MSU_RUNTIME_LAYOUT_FIELD_COUNT];
};
extern struct msu_runtime_layout_table msu_runtime_layout;
/* The provider must return actual target facts, including owner size. This
 * port also permits a read-only accessor provider when target BTF is absent.
 * A partial/error result never publishes ready or uses compiled offsets. */
struct msu_runtime_btf_field;
typedef int (*msu_runtime_field_query)(const char *, const char *, struct msu_runtime_btf_field *);
int msu_runtime_layout_init(msu_runtime_field_query query);
static inline bool msu_runtime_layout_ready(void)
{
    return smp_load_acquire(&msu_runtime_layout.ready) == 1 &&
        msu_runtime_layout.magic == MSU_RUNTIME_LAYOUT_MAGIC &&
        msu_runtime_layout.schema == MSU_RUNTIME_LAYOUT_SCHEMA &&
        msu_runtime_layout.bytes == sizeof(msu_runtime_layout) &&
        msu_runtime_layout.fields == MSU_RUNTIME_LAYOUT_FIELD_COUNT &&
        !msu_runtime_layout.reserved;
}
#define MSU_DYN_SLOT_INDEX_(type, member) MSU_DYN_##type##_##member
#define MSU_DYN_SLOT_INDEX(type, member) MSU_DYN_SLOT_INDEX_(type, member)
#define MSU_DYN_FIELD_AS(object, type, key, member) \
    (*(__typeof__(((struct type *)0)->member) *)((char *)(object) + \
        msu_runtime_layout.values[MSU_DYN_SLOT_INDEX(type, key)].offset))
#define MSU_DYN_FIELD(object, type, member) \
    MSU_DYN_FIELD_AS(object, type, member, member)

#define MSU_DYN_CONTAINER(object, type, member) \
    ((struct type *)((char *)(object) - \
        msu_runtime_layout.values[MSU_DYN_SLOT_INDEX(type, member)].offset))

static inline struct thread_info *msu_dynamic_task_thread_info(struct task_struct *task)
{
#ifdef CONFIG_THREAD_INFO_IN_TASK
    return &MSU_DYN_FIELD(task, task_struct, thread_info);
#else
    return (struct thread_info *)MSU_DYN_FIELD(task, task_struct, stack);
#endif
}

static inline const struct cred *msu_dynamic_current_cred(void)
{ return rcu_dereference_protected(MSU_DYN_FIELD(current, task_struct, cred), 1); }
static inline const struct cred *msu_dynamic_current_real_cred(void)
{ return rcu_dereference_protected(MSU_DYN_FIELD(current, task_struct, real_cred), 1); }
static inline kuid_t msu_dynamic_current_uid(void)
{ return MSU_DYN_FIELD(msu_dynamic_current_cred(), cred, uid); }
static inline kuid_t msu_dynamic_current_euid(void)
{ return MSU_DYN_FIELD(msu_dynamic_current_cred(), cred, euid); }
static inline struct user_namespace *msu_dynamic_current_user_ns(void)
{ return MSU_DYN_FIELD(msu_dynamic_current_cred(), cred, user_ns); }

/* Preserve the selected kernel family's native reference/release algorithms.
 * Width/signature/config qualification remains a carrier precondition. */
static inline const struct cred *msu_dynamic_get_cred(const struct cred *original)
{
    struct cred *cred = (struct cred *)original;
    if (!cred) return NULL;
    MSU_DYN_FIELD(cred, cred, non_rcu) = 0;
    if (sizeof(MSU_DYN_FIELD(cred, cred, usage)) == sizeof(atomic_long_t))
        atomic_long_inc((atomic_long_t *)&MSU_DYN_FIELD(cred, cred, usage));
    else
        atomic_inc((atomic_t *)&MSU_DYN_FIELD(cred, cred, usage));
    return original;
}
static inline void msu_dynamic_put_cred(const struct cred *original)
{
    struct cred *cred = (struct cred *)original;
    bool last;
    if (!cred) return;
    if (sizeof(MSU_DYN_FIELD(cred, cred, usage)) == sizeof(atomic_long_t))
        last = atomic_long_dec_and_test((atomic_long_t *)&MSU_DYN_FIELD(cred, cred, usage));
    else
        last = atomic_dec_and_test((atomic_t *)&MSU_DYN_FIELD(cred, cred, usage));
    if (last) __put_cred(cred);
}
static inline const struct cred *msu_dynamic_get_current_cred(void)
{ return msu_dynamic_get_cred(msu_dynamic_current_cred()); }
static inline struct task_struct *msu_dynamic_get_task_struct(struct task_struct *task)
{ refcount_inc(&MSU_DYN_FIELD(task, task_struct, usage)); return task; }
static inline void msu_dynamic_put_task_struct(struct task_struct *task)
{
    if (!refcount_dec_and_test(&MSU_DYN_FIELD(task, task_struct, usage))) return;
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 16, 0)
    call_rcu(&MSU_DYN_FIELD(task, task_struct, rcu), __put_task_struct_rcu_cb);
#elif defined(CONFIG_PREEMPT_RT)
    if (!in_task())
        call_rcu(&MSU_DYN_FIELD(task, task_struct, rcu), __put_task_struct_rcu_cb);
    else
        __put_task_struct(task);
#else
    __put_task_struct(task);
#endif
}
static inline void msu_dynamic_mmgrab(struct mm_struct *mm)
{ atomic_inc(&MSU_DYN_FIELD(mm, mm_struct, mm_count)); }
static inline void msu_dynamic_mmdrop(struct mm_struct *mm)
{
    if (atomic_dec_and_test(&MSU_DYN_FIELD(mm, mm_struct, mm_count))) __mmdrop(mm);
}

static inline struct inode *msu_dynamic_file_inode(const struct file *file)
{ return MSU_DYN_FIELD(file, file, f_inode); }
static inline struct inode *msu_dynamic_d_inode(const struct dentry *dentry)
{ return MSU_DYN_FIELD(dentry, dentry, d_inode); }
static inline int msu_dynamic_deny_write_access(struct file *file)
{
    struct inode *inode = msu_dynamic_file_inode(file);
    return atomic_dec_unless_positive(&MSU_DYN_FIELD(inode, inode, i_writecount)) ? 0 : -ETXTBSY;
}
static inline void msu_dynamic_allow_write_access(struct file *file)
{
    if (file) atomic_inc(&MSU_DYN_FIELD(msu_dynamic_file_inode(file), inode, i_writecount));
}
static inline struct file *msu_dynamic_get_file(struct file *file)
{
#ifdef FILE_REF_ONEREF
    file_ref_inc(&MSU_DYN_FIELD(file, file, f_ref));
#else
    atomic_long_inc(&MSU_DYN_FIELD(file, file, f_count));
#endif
    return file;
}
static inline void msu_dynamic_task_lock(struct task_struct *task)
{ spin_lock(&MSU_DYN_FIELD(task, task_struct, alloc_lock)); }
static inline void msu_dynamic_task_unlock(struct task_struct *task)
{ spin_unlock(&MSU_DYN_FIELD(task, task_struct, alloc_lock)); }
static inline void msu_dynamic_get_fs_root(struct fs_struct *fs, struct path *root)
{
#if MSU_RESOLVER_FS_SEQLOCK
    read_seqlock_excl(&MSU_DYN_FIELD(fs, fs_struct, seq));
#else
    spin_lock(&MSU_DYN_FIELD(fs, fs_struct, lock));
#endif
    *root = MSU_DYN_FIELD(fs, fs_struct, root);
    path_get(root);
#if MSU_RESOLVER_FS_SEQLOCK
    read_sequnlock_excl(&MSU_DYN_FIELD(fs, fs_struct, seq));
#else
    spin_unlock(&MSU_DYN_FIELD(fs, fs_struct, lock));
#endif
}
static inline void msu_dynamic_get_fs_pwd(struct fs_struct *fs, struct path *pwd)
{
#if MSU_RESOLVER_FS_SEQLOCK
    read_seqlock_excl(&MSU_DYN_FIELD(fs, fs_struct, seq));
#else
    spin_lock(&MSU_DYN_FIELD(fs, fs_struct, lock));
#endif
    *pwd = MSU_DYN_FIELD(fs, fs_struct, pwd);
    path_get(pwd);
#if MSU_RESOLVER_FS_SEQLOCK
    read_sequnlock_excl(&MSU_DYN_FIELD(fs, fs_struct, seq));
#else
    spin_unlock(&MSU_DYN_FIELD(fs, fs_struct, lock));
#endif
}
static inline void msu_dynamic_get_nsproxy(struct nsproxy *ns)
{
    if (__builtin_types_compatible_p(typeof(MSU_DYN_FIELD(ns, nsproxy, count)), refcount_t))
        refcount_inc((refcount_t *)&MSU_DYN_FIELD(ns, nsproxy, count));
    else
        atomic_inc((atomic_t *)&MSU_DYN_FIELD(ns, nsproxy, count));
}
static inline void msu_dynamic_put_nsproxy(struct nsproxy *ns)
{
    bool last;
    if (__builtin_types_compatible_p(typeof(MSU_DYN_FIELD(ns, nsproxy, count)), refcount_t))
        last = refcount_dec_and_test((refcount_t *)&MSU_DYN_FIELD(ns, nsproxy, count));
    else
        last = atomic_dec_and_test((atomic_t *)&MSU_DYN_FIELD(ns, nsproxy, count));
    if (last) free_nsproxy(ns);
}
/* These macros are instantiated only by consumers that include the native
 * mount/pt_regs declarations. Their embedded object ABIs stay qualified. */
#define msu_dynamic_real_mount(object) MSU_DYN_CONTAINER(object, mount, mnt)
#define msu_dynamic_task_pt_regs(task) \
    ((struct pt_regs *)(THREAD_SIZE + (unsigned long)MSU_DYN_FIELD(task, task_struct, stack)) - 1)
#endif
