// SPDX-License-Identifier: GPL-3.0-only
#include <linux/cred.h>
#include <linux/dcache.h>
#include <linux/fdtable.h>
#include <linux/fs.h>
#include <linux/fs_struct.h>
#include <linux/mm.h>
#include <linux/mount.h>
#include <linux/nsproxy.h>
#include <linux/sched/signal.h>
#include <linux/seccomp.h>
#include <linux/stddef.h>
#include <linux/printk.h>
#include "msu_resolver_build_config.h"
#if __has_include(<fs/mount.h>)
#include <fs/mount.h>
#define MSU_DYNAMIC_HAS_MOUNT 1
#endif
#include "manager_dynamic_layout.h"
#if defined(MSU_INIT_BRIDGE_RUNTIME_FACTS) && MSU_INIT_BRIDGE_RUNTIME_FACTS
#include "manager_runtime_btf.h"
#endif

#define MSU_DYN_COMPILED_AS(type, key, member) \
    [MSU_DYN_##type##_##key] = { offsetof(struct type, member), \
        sizeof(((struct type *)0)->member), sizeof(struct type) }
#define MSU_DYN_COMPILED(type, member) MSU_DYN_COMPILED_AS(type, member, member)
#define MSU_DYN_ABSENT(type, member) [MSU_DYN_##type##_##member] = { ~0U, 0, 0 }
struct msu_runtime_layout_table msu_runtime_layout
    __attribute__((section(".msu.runtime.layout"), aligned(4))) = {
    .magic = MSU_RUNTIME_LAYOUT_MAGIC,
    .schema = MSU_RUNTIME_LAYOUT_SCHEMA,
    .bytes = sizeof(struct msu_runtime_layout_table),
    .fields = MSU_RUNTIME_LAYOUT_FIELD_COUNT,
    .ready = 0,
    .reserved = 0,
    .values = {
        MSU_DYN_COMPILED(task_struct, cred),
        MSU_DYN_COMPILED(task_struct, real_cred),
        MSU_DYN_COMPILED(task_struct, mm),
        MSU_DYN_COMPILED(task_struct, self_exec_id),
        MSU_DYN_COMPILED(task_struct, flags),
        MSU_DYN_COMPILED(task_struct, usage),
        MSU_DYN_COMPILED(task_struct, alloc_lock),
        MSU_DYN_COMPILED(task_struct, comm),
        MSU_DYN_COMPILED(task_struct, fs),
        MSU_DYN_COMPILED(task_struct, stack),
        MSU_DYN_COMPILED(task_struct, rcu),
        MSU_DYN_COMPILED(task_struct, real_parent),
        MSU_DYN_COMPILED(task_struct, nsproxy),
#if defined(CONFIG_THREAD_INFO_IN_TASK)
        MSU_DYN_COMPILED(task_struct, thread_info),
#else
        MSU_DYN_ABSENT(task_struct, thread_info),
#endif
        MSU_DYN_COMPILED(task_struct, sighand),
#if defined(CONFIG_SECCOMP)
        MSU_DYN_COMPILED(task_struct, seccomp),
#else
        MSU_DYN_ABSENT(task_struct, seccomp),
#endif
        MSU_DYN_COMPILED(task_struct, signal),
        MSU_DYN_COMPILED(task_struct, thread_node),
        MSU_DYN_COMPILED(cred, usage),
        MSU_DYN_COMPILED(cred, non_rcu),
        MSU_DYN_COMPILED(cred, uid),
        MSU_DYN_COMPILED(cred, gid),
        MSU_DYN_COMPILED(cred, suid),
        MSU_DYN_COMPILED(cred, sgid),
        MSU_DYN_COMPILED(cred, euid),
        MSU_DYN_COMPILED(cred, egid),
        MSU_DYN_COMPILED(cred, fsuid),
        MSU_DYN_COMPILED(cred, fsgid),
        MSU_DYN_COMPILED(cred, securebits),
        MSU_DYN_COMPILED(cred, cap_inheritable),
        MSU_DYN_COMPILED(cred, cap_permitted),
        MSU_DYN_COMPILED(cred, cap_effective),
        MSU_DYN_COMPILED(cred, cap_bset),
        MSU_DYN_COMPILED(cred, cap_ambient),
        MSU_DYN_COMPILED(cred, user),
        MSU_DYN_COMPILED(cred, user_ns),
        MSU_DYN_COMPILED(cred, group_info),
#if defined(CONFIG_SECURITY)
        MSU_DYN_COMPILED(cred, security),
#else
        MSU_DYN_ABSENT(cred, security),
#endif
        MSU_DYN_COMPILED(fs_struct, root),
        MSU_DYN_COMPILED(fs_struct, seq),
#if MSU_RESOLVER_FS_HAS_LOCK
        MSU_DYN_COMPILED(fs_struct, lock),
#else
        MSU_DYN_ABSENT(fs_struct, lock),
#endif
        MSU_DYN_COMPILED(file, f_path),
        MSU_DYN_COMPILED(file, f_inode),
        MSU_DYN_COMPILED(file, f_op),
#ifndef FILE_REF_ONEREF
        MSU_DYN_COMPILED(file, f_count),
#else
        MSU_DYN_ABSENT(file, f_count),
#endif
        MSU_DYN_COMPILED(inode, i_uid),
        MSU_DYN_COMPILED(inode, i_gid),
        MSU_DYN_COMPILED(inode, i_mode),
        MSU_DYN_COMPILED(inode, i_sb),
        MSU_DYN_COMPILED(inode, i_nlink),
        MSU_DYN_COMPILED(inode, i_generation),
        MSU_DYN_COMPILED(inode, i_writecount),
        MSU_DYN_COMPILED(dentry, d_inode),
        MSU_DYN_COMPILED(dentry, d_parent),
        MSU_DYN_COMPILED(dentry, d_name),
        MSU_DYN_COMPILED(qstr, len),
        MSU_DYN_COMPILED(qstr, name),
        MSU_DYN_COMPILED(super_block, s_magic),
        MSU_DYN_COMPILED(file_operations, write),
        MSU_DYN_COMPILED(mm_struct, mm_count),
        MSU_DYN_COMPILED(mm_struct, mmap_lock),
        MSU_DYN_COMPILED(vm_area_struct, vm_file),
        MSU_DYN_COMPILED(vm_area_struct, vm_end),
        MSU_DYN_COMPILED(sighand_struct, siglock),
#if defined(CONFIG_SECCOMP)
        MSU_DYN_COMPILED(seccomp, mode),
#else
        MSU_DYN_ABSENT(seccomp, mode),
#endif
#if defined(CONFIG_SECCOMP)
        MSU_DYN_COMPILED(seccomp, filter_count),
#else
        MSU_DYN_ABSENT(seccomp, filter_count),
#endif
#if defined(CONFIG_SECCOMP)
        MSU_DYN_COMPILED(seccomp, filter),
#else
        MSU_DYN_ABSENT(seccomp, filter),
#endif
        MSU_DYN_COMPILED(thread_info, flags),
#if defined(CONFIG_GENERIC_ENTRY)
        MSU_DYN_COMPILED(thread_info, syscall_work),
#else
        MSU_DYN_ABSENT(thread_info, syscall_work),
#endif
#if defined(CONFIG_PREEMPT_COUNT)
        MSU_DYN_COMPILED(thread_info, preempt_count),
#else
        MSU_DYN_ABSENT(thread_info, preempt_count),
#endif
        MSU_DYN_COMPILED(signal_struct, thread_head),
        MSU_DYN_COMPILED(nsproxy, mnt_ns),
        MSU_DYN_COMPILED(nsproxy, count),
#if defined(MSU_DYNAMIC_HAS_MOUNT)
        MSU_DYN_COMPILED(mount, mnt),
#else
        MSU_DYN_ABSENT(mount, mnt),
#endif
#if defined(MSU_DYNAMIC_HAS_MOUNT)
        MSU_DYN_COMPILED(mount, mnt_mounts),
#else
        MSU_DYN_ABSENT(mount, mnt_mounts),
#endif
#if defined(MSU_DYNAMIC_HAS_MOUNT)
        MSU_DYN_COMPILED(mount, mnt_child),
#else
        MSU_DYN_ABSENT(mount, mnt_child),
#endif
#if defined(MSU_DYNAMIC_HAS_MOUNT)
        MSU_DYN_COMPILED(mount, mnt_parent),
#else
        MSU_DYN_ABSENT(mount, mnt_parent),
#endif
#if defined(MSU_DYNAMIC_HAS_MOUNT)
        MSU_DYN_COMPILED(mount, mnt_mountpoint),
#else
        MSU_DYN_ABSENT(mount, mnt_mountpoint),
#endif
#if defined(MSU_DYNAMIC_HAS_MOUNT)
        MSU_DYN_COMPILED(mount, mnt_ns),
#else
        MSU_DYN_ABSENT(mount, mnt_ns),
#endif
#if defined(MSU_DYNAMIC_HAS_MOUNT)
        MSU_DYN_COMPILED(mount, mnt_id),
#else
        MSU_DYN_ABSENT(mount, mnt_id),
#endif
        MSU_DYN_COMPILED(vfsmount, mnt_root),
#ifdef FILE_REF_ONEREF
        MSU_DYN_COMPILED(file, f_ref),
#else
        MSU_DYN_ABSENT(file, f_ref),
#endif
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 3, 0)
        MSU_DYN_COMPILED(vfsmount, mnt_idmap),
        MSU_DYN_ABSENT(vfsmount, mnt_user_ns),
#elif LINUX_VERSION_CODE >= KERNEL_VERSION(5, 12, 0)
        MSU_DYN_ABSENT(vfsmount, mnt_idmap),
        MSU_DYN_COMPILED_AS(vfsmount, mnt_user_ns, mnt_userns),
#else
        MSU_DYN_ABSENT(vfsmount, mnt_idmap),
        MSU_DYN_ABSENT(vfsmount, mnt_user_ns),
#endif
        MSU_DYN_COMPILED(task_struct, pid),
        MSU_DYN_COMPILED(task_struct, files),
        MSU_DYN_COMPILED(inode, i_size),
        MSU_DYN_COMPILED(file, f_pos),
        MSU_DYN_COMPILED(file, f_mode),
        MSU_DYN_COMPILED(file, f_flags),
        MSU_DYN_COMPILED(file, f_cred),
        MSU_DYN_COMPILED(file, private_data),
        MSU_DYN_COMPILED(files_struct, file_lock),
        MSU_DYN_COMPILED(files_struct, fdt),
        MSU_DYN_COMPILED(files_struct, count),
        MSU_DYN_COMPILED(files_struct, next_fd),
        MSU_DYN_COMPILED(fdtable, max_fds),
        MSU_DYN_COMPILED(fdtable, fd),
        MSU_DYN_COMPILED(fdtable, open_fds),
        MSU_DYN_COMPILED(fdtable, full_fds_bits),
        MSU_DYN_COMPILED(fdtable, close_on_exec),
        MSU_DYN_COMPILED(task_struct, atomic_flags),
        MSU_DYN_COMPILED(fs_struct, pwd),
    },
};
#undef MSU_DYN_COMPILED
#undef MSU_DYN_COMPILED_AS
#undef MSU_DYN_ABSENT

/* Same-object immutable evidence, never a runtime offset source. */
const u32 msu_runtime_layout_alignments[MSU_RUNTIME_LAYOUT_FIELD_COUNT]
    __attribute__((section(".msu.runtime.alignment"), used)) = {
        [MSU_DYN_task_struct_cred] = __alignof__(((struct task_struct *)0)->cred),
        [MSU_DYN_task_struct_real_cred] = __alignof__(((struct task_struct *)0)->real_cred),
        [MSU_DYN_task_struct_mm] = __alignof__(((struct task_struct *)0)->mm),
        [MSU_DYN_task_struct_self_exec_id] = __alignof__(((struct task_struct *)0)->self_exec_id),
        [MSU_DYN_task_struct_flags] = __alignof__(((struct task_struct *)0)->flags),
        [MSU_DYN_task_struct_usage] = __alignof__(((struct task_struct *)0)->usage),
        [MSU_DYN_task_struct_alloc_lock] = __alignof__(((struct task_struct *)0)->alloc_lock),
        [MSU_DYN_task_struct_comm] = __alignof__(((struct task_struct *)0)->comm),
        [MSU_DYN_task_struct_fs] = __alignof__(((struct task_struct *)0)->fs),
        [MSU_DYN_task_struct_stack] = __alignof__(((struct task_struct *)0)->stack),
        [MSU_DYN_task_struct_rcu] = __alignof__(((struct task_struct *)0)->rcu),
        [MSU_DYN_task_struct_real_parent] = __alignof__(((struct task_struct *)0)->real_parent),
        [MSU_DYN_task_struct_nsproxy] = __alignof__(((struct task_struct *)0)->nsproxy),
#if defined(CONFIG_THREAD_INFO_IN_TASK)
        [MSU_DYN_task_struct_thread_info] = __alignof__(((struct task_struct *)0)->thread_info),
#else
        [MSU_DYN_task_struct_thread_info] = 0,
#endif
        [MSU_DYN_task_struct_sighand] = __alignof__(((struct task_struct *)0)->sighand),
#if defined(CONFIG_SECCOMP)
        [MSU_DYN_task_struct_seccomp] = __alignof__(((struct task_struct *)0)->seccomp),
#else
        [MSU_DYN_task_struct_seccomp] = 0,
#endif
        [MSU_DYN_task_struct_signal] = __alignof__(((struct task_struct *)0)->signal),
        [MSU_DYN_task_struct_thread_node] = __alignof__(((struct task_struct *)0)->thread_node),
        [MSU_DYN_cred_usage] = __alignof__(((struct cred *)0)->usage),
        [MSU_DYN_cred_non_rcu] = __alignof__(((struct cred *)0)->non_rcu),
        [MSU_DYN_cred_uid] = __alignof__(((struct cred *)0)->uid),
        [MSU_DYN_cred_gid] = __alignof__(((struct cred *)0)->gid),
        [MSU_DYN_cred_suid] = __alignof__(((struct cred *)0)->suid),
        [MSU_DYN_cred_sgid] = __alignof__(((struct cred *)0)->sgid),
        [MSU_DYN_cred_euid] = __alignof__(((struct cred *)0)->euid),
        [MSU_DYN_cred_egid] = __alignof__(((struct cred *)0)->egid),
        [MSU_DYN_cred_fsuid] = __alignof__(((struct cred *)0)->fsuid),
        [MSU_DYN_cred_fsgid] = __alignof__(((struct cred *)0)->fsgid),
        [MSU_DYN_cred_securebits] = __alignof__(((struct cred *)0)->securebits),
        [MSU_DYN_cred_cap_inheritable] = __alignof__(((struct cred *)0)->cap_inheritable),
        [MSU_DYN_cred_cap_permitted] = __alignof__(((struct cred *)0)->cap_permitted),
        [MSU_DYN_cred_cap_effective] = __alignof__(((struct cred *)0)->cap_effective),
        [MSU_DYN_cred_cap_bset] = __alignof__(((struct cred *)0)->cap_bset),
        [MSU_DYN_cred_cap_ambient] = __alignof__(((struct cred *)0)->cap_ambient),
        [MSU_DYN_cred_user] = __alignof__(((struct cred *)0)->user),
        [MSU_DYN_cred_user_ns] = __alignof__(((struct cred *)0)->user_ns),
        [MSU_DYN_cred_group_info] = __alignof__(((struct cred *)0)->group_info),
#if defined(CONFIG_SECURITY)
        [MSU_DYN_cred_security] = __alignof__(((struct cred *)0)->security),
#else
        [MSU_DYN_cred_security] = 0,
#endif
        [MSU_DYN_fs_struct_root] = __alignof__(((struct fs_struct *)0)->root),
        [MSU_DYN_fs_struct_seq] = __alignof__(((struct fs_struct *)0)->seq),
#if MSU_RESOLVER_FS_HAS_LOCK
        [MSU_DYN_fs_struct_lock] = __alignof__(((struct fs_struct *)0)->lock),
#else
        [MSU_DYN_fs_struct_lock] = 0,
#endif
        [MSU_DYN_file_f_path] = __alignof__(((struct file *)0)->f_path),
        [MSU_DYN_file_f_inode] = __alignof__(((struct file *)0)->f_inode),
        [MSU_DYN_file_f_op] = __alignof__(((struct file *)0)->f_op),
#ifndef FILE_REF_ONEREF
        [MSU_DYN_file_f_count] = __alignof__(((struct file *)0)->f_count),
#else
        [MSU_DYN_file_f_count] = 0,
#endif
        [MSU_DYN_inode_i_uid] = __alignof__(((struct inode *)0)->i_uid),
        [MSU_DYN_inode_i_gid] = __alignof__(((struct inode *)0)->i_gid),
        [MSU_DYN_inode_i_mode] = __alignof__(((struct inode *)0)->i_mode),
        [MSU_DYN_inode_i_sb] = __alignof__(((struct inode *)0)->i_sb),
        [MSU_DYN_inode_i_nlink] = __alignof__(((struct inode *)0)->i_nlink),
        [MSU_DYN_inode_i_generation] = __alignof__(((struct inode *)0)->i_generation),
        [MSU_DYN_inode_i_writecount] = __alignof__(((struct inode *)0)->i_writecount),
        [MSU_DYN_dentry_d_inode] = __alignof__(((struct dentry *)0)->d_inode),
        [MSU_DYN_dentry_d_parent] = __alignof__(((struct dentry *)0)->d_parent),
        [MSU_DYN_dentry_d_name] = __alignof__(((struct dentry *)0)->d_name),
        [MSU_DYN_qstr_len] = __alignof__(((struct qstr *)0)->len),
        [MSU_DYN_qstr_name] = __alignof__(((struct qstr *)0)->name),
        [MSU_DYN_super_block_s_magic] = __alignof__(((struct super_block *)0)->s_magic),
        [MSU_DYN_file_operations_write] = __alignof__(((struct file_operations *)0)->write),
        [MSU_DYN_mm_struct_mm_count] = __alignof__(((struct mm_struct *)0)->mm_count),
        [MSU_DYN_mm_struct_mmap_lock] = __alignof__(((struct mm_struct *)0)->mmap_lock),
        [MSU_DYN_vm_area_struct_vm_file] = __alignof__(((struct vm_area_struct *)0)->vm_file),
        [MSU_DYN_vm_area_struct_vm_end] = __alignof__(((struct vm_area_struct *)0)->vm_end),
        [MSU_DYN_sighand_struct_siglock] = __alignof__(((struct sighand_struct *)0)->siglock),
#if defined(CONFIG_SECCOMP)
        [MSU_DYN_seccomp_mode] = __alignof__(((struct seccomp *)0)->mode),
#else
        [MSU_DYN_seccomp_mode] = 0,
#endif
#if defined(CONFIG_SECCOMP)
        [MSU_DYN_seccomp_filter_count] = __alignof__(((struct seccomp *)0)->filter_count),
#else
        [MSU_DYN_seccomp_filter_count] = 0,
#endif
#if defined(CONFIG_SECCOMP)
        [MSU_DYN_seccomp_filter] = __alignof__(((struct seccomp *)0)->filter),
#else
        [MSU_DYN_seccomp_filter] = 0,
#endif
        [MSU_DYN_thread_info_flags] = __alignof__(((struct thread_info *)0)->flags),
#if defined(CONFIG_GENERIC_ENTRY)
        [MSU_DYN_thread_info_syscall_work] = __alignof__(((struct thread_info *)0)->syscall_work),
#else
        [MSU_DYN_thread_info_syscall_work] = 0,
#endif
#if defined(CONFIG_PREEMPT_COUNT)
        [MSU_DYN_thread_info_preempt_count] = __alignof__(((struct thread_info *)0)->preempt_count),
#else
        [MSU_DYN_thread_info_preempt_count] = 0,
#endif
        [MSU_DYN_signal_struct_thread_head] = __alignof__(((struct signal_struct *)0)->thread_head),
        [MSU_DYN_nsproxy_mnt_ns] = __alignof__(((struct nsproxy *)0)->mnt_ns),
        [MSU_DYN_nsproxy_count] = __alignof__(((struct nsproxy *)0)->count),
#if defined(MSU_DYNAMIC_HAS_MOUNT)
        [MSU_DYN_mount_mnt] = __alignof__(((struct mount *)0)->mnt),
#else
        [MSU_DYN_mount_mnt] = 0,
#endif
#if defined(MSU_DYNAMIC_HAS_MOUNT)
        [MSU_DYN_mount_mnt_mounts] = __alignof__(((struct mount *)0)->mnt_mounts),
#else
        [MSU_DYN_mount_mnt_mounts] = 0,
#endif
#if defined(MSU_DYNAMIC_HAS_MOUNT)
        [MSU_DYN_mount_mnt_child] = __alignof__(((struct mount *)0)->mnt_child),
#else
        [MSU_DYN_mount_mnt_child] = 0,
#endif
#if defined(MSU_DYNAMIC_HAS_MOUNT)
        [MSU_DYN_mount_mnt_parent] = __alignof__(((struct mount *)0)->mnt_parent),
#else
        [MSU_DYN_mount_mnt_parent] = 0,
#endif
#if defined(MSU_DYNAMIC_HAS_MOUNT)
        [MSU_DYN_mount_mnt_mountpoint] = __alignof__(((struct mount *)0)->mnt_mountpoint),
#else
        [MSU_DYN_mount_mnt_mountpoint] = 0,
#endif
#if defined(MSU_DYNAMIC_HAS_MOUNT)
        [MSU_DYN_mount_mnt_ns] = __alignof__(((struct mount *)0)->mnt_ns),
#else
        [MSU_DYN_mount_mnt_ns] = 0,
#endif
#if defined(MSU_DYNAMIC_HAS_MOUNT)
        [MSU_DYN_mount_mnt_id] = __alignof__(((struct mount *)0)->mnt_id),
#else
        [MSU_DYN_mount_mnt_id] = 0,
#endif
        [MSU_DYN_vfsmount_mnt_root] = __alignof__(((struct vfsmount *)0)->mnt_root),
#ifdef FILE_REF_ONEREF
        [MSU_DYN_file_f_ref] = __alignof__(((struct file *)0)->f_ref),
#else
        [MSU_DYN_file_f_ref] = 0,
#endif
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 3, 0)
        [MSU_DYN_vfsmount_mnt_idmap] = __alignof__(((struct vfsmount *)0)->mnt_idmap),
        [MSU_DYN_vfsmount_mnt_user_ns] = 0,
#elif LINUX_VERSION_CODE >= KERNEL_VERSION(5, 12, 0)
        [MSU_DYN_vfsmount_mnt_idmap] = 0,
        [MSU_DYN_vfsmount_mnt_user_ns] = __alignof__(((struct vfsmount *)0)->mnt_userns),
#else
        [MSU_DYN_vfsmount_mnt_idmap] = 0,
        [MSU_DYN_vfsmount_mnt_user_ns] = 0,
#endif
        [MSU_DYN_task_struct_pid] = __alignof__(((struct task_struct *)0)->pid),
        [MSU_DYN_task_struct_files] = __alignof__(((struct task_struct *)0)->files),
        [MSU_DYN_inode_i_size] = __alignof__(((struct inode *)0)->i_size),
        [MSU_DYN_file_f_pos] = __alignof__(((struct file *)0)->f_pos),
        [MSU_DYN_file_f_mode] = __alignof__(((struct file *)0)->f_mode),
        [MSU_DYN_file_f_flags] = __alignof__(((struct file *)0)->f_flags),
        [MSU_DYN_file_f_cred] = __alignof__(((struct file *)0)->f_cred),
        [MSU_DYN_file_private_data] = __alignof__(((struct file *)0)->private_data),
        [MSU_DYN_files_struct_file_lock] = __alignof__(((struct files_struct *)0)->file_lock),
        [MSU_DYN_files_struct_fdt] = __alignof__(((struct files_struct *)0)->fdt),
        [MSU_DYN_files_struct_count] = __alignof__(((struct files_struct *)0)->count),
        [MSU_DYN_files_struct_next_fd] = __alignof__(((struct files_struct *)0)->next_fd),
        [MSU_DYN_fdtable_max_fds] = __alignof__(((struct fdtable *)0)->max_fds),
        [MSU_DYN_fdtable_fd] = __alignof__(((struct fdtable *)0)->fd),
        [MSU_DYN_fdtable_open_fds] = __alignof__(((struct fdtable *)0)->open_fds),
        [MSU_DYN_fdtable_full_fds_bits] = __alignof__(((struct fdtable *)0)->full_fds_bits),
        [MSU_DYN_fdtable_close_on_exec] = __alignof__(((struct fdtable *)0)->close_on_exec),
        [MSU_DYN_task_struct_atomic_flags] = __alignof__(((struct task_struct *)0)->atomic_flags),
        [MSU_DYN_fs_struct_pwd] = __alignof__(((struct fs_struct *)0)->pwd),
};
static struct task_struct *msu_dynamic_btf_task_struct __attribute__((used));
static struct cred *msu_dynamic_btf_cred __attribute__((used));
static struct fs_struct *msu_dynamic_btf_fs_struct __attribute__((used));
static struct file *msu_dynamic_btf_file __attribute__((used));
static struct files_struct *msu_dynamic_btf_files_struct __attribute__((used));
static struct fdtable *msu_dynamic_btf_fdtable __attribute__((used));
static struct inode *msu_dynamic_btf_inode __attribute__((used));
static struct dentry *msu_dynamic_btf_dentry __attribute__((used));
static struct qstr *msu_dynamic_btf_qstr __attribute__((used));
static struct super_block *msu_dynamic_btf_super_block __attribute__((used));
static struct file_operations *msu_dynamic_btf_file_operations __attribute__((used));
static struct mm_struct *msu_dynamic_btf_mm_struct __attribute__((used));
static struct vm_area_struct *msu_dynamic_btf_vm_area_struct __attribute__((used));
static struct sighand_struct *msu_dynamic_btf_sighand_struct __attribute__((used));
static struct seccomp *msu_dynamic_btf_seccomp __attribute__((used));
static struct thread_info *msu_dynamic_btf_thread_info __attribute__((used));
static struct signal_struct *msu_dynamic_btf_signal_struct __attribute__((used));
static struct nsproxy *msu_dynamic_btf_nsproxy __attribute__((used));
#ifdef MSU_DYNAMIC_HAS_MOUNT
static struct mount *msu_dynamic_btf_mount __attribute__((used));
#endif
static struct vfsmount *msu_dynamic_btf_vfsmount __attribute__((used));

#define MSU_DYN_NAME(type, member) #type "." #member "\0"
const char msu_runtime_layout_names[]
    __attribute__((section(".msu.runtime.names"), used)) =
    MSU_RUNTIME_LAYOUT_FIELDS(MSU_DYN_NAME);
#undef MSU_DYN_NAME

#define MSU_DYN_ALIAS(type, key, member) #type "." #key "=" #type "." #member "\0"
const char msu_runtime_layout_aliases[]
    __attribute__((section(".msu.runtime.aliases"), used)) =
    MSU_RUNTIME_LAYOUT_ALIASES(MSU_DYN_ALIAS);
#undef MSU_DYN_ALIAS

#if defined(MSU_INIT_BRIDGE_RUNTIME_FACTS) && MSU_INIT_BRIDGE_RUNTIME_FACTS
int msu_runtime_layout_init(msu_runtime_field_query query)
{
    struct msu_runtime_layout_table next = msu_runtime_layout;
    const char *name = msu_runtime_layout_names;
    unsigned int i;
    int error;

    WRITE_ONCE(msu_runtime_layout.ready, 0);
    if (!query) return -EINVAL;
    if (next.magic != MSU_RUNTIME_LAYOUT_MAGIC || next.schema != MSU_RUNTIME_LAYOUT_SCHEMA ||
        next.bytes != sizeof(next) || next.fields != MSU_RUNTIME_LAYOUT_FIELD_COUNT || next.reserved)
        return -EPROTO;
    next.ready = 0;
    for (i = 0; i < MSU_RUNTIME_LAYOUT_FIELD_COUNT; ++i) {
        struct msu_runtime_btf_field field;
        char owner[32];
        const char *dot = strchr(name, '.');
        u32 width = next.values[i].bytes, alignment = msu_runtime_layout_alignments[i];
        size_t length;

        if (!dot || dot == name || (length = dot - name) >= sizeof(owner)) return -EPROTO;
        memcpy(owner, name, length); owner[length] = 0;
        if (next.values[i].offset == ~0U) {
            if (width || next.values[i].owner_bytes || alignment) return -EPROTO;
            name += strlen(name) + 1;
            continue;
        }
        error = query(owner, dot + 1, &field);
        if (error == -ENOENT && i == MSU_DYN_vfsmount_mnt_user_ns)
            error = query(owner, "mnt_userns", &field);
        if (!error && (!width || field.bytes != width || !alignment ||
            (alignment & (alignment - 1)) || field.offset % alignment ||
            !field.owner_bytes || field.offset > field.owner_bytes ||
            field.bytes > field.owner_bytes - field.offset)) error = -EOPNOTSUPP;
        if (error) {
            pr_err("MapleSU runtime layout: %s: %d\n", name, error);
            return error;
        }
        next.values[i].offset = field.offset;
        next.values[i].bytes = field.bytes;
        next.values[i].owner_bytes = field.owner_bytes;
        name += strlen(name) + 1;
    }
    memcpy(msu_runtime_layout.values, next.values, sizeof(next.values));
    smp_store_release(&msu_runtime_layout.ready, 1);
    return 0;
}
#endif
