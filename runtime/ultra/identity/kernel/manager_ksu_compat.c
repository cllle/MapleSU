// SPDX-License-Identifier: GPL-3.0-only
#include <linux/anon_inodes.h>
#include <linux/compat.h>
#include <linux/cred.h>
#include <linux/err.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/irqflags.h>
#include <linux/mm.h>
#include <linux/preempt.h>
#include <linux/security.h>
#include <linux/slab.h>
#include <linux/spinlock.h>
#include <linux/task_work.h>
#include <linux/uaccess.h>
#include <linux/user_namespace.h>
#include <asm/ptrace.h>
#include "manager_dynamic_layout.h"
#include "manager_ksu_compat.h"
#include "manager_su_runtime_factory.h"
#include "manager_resident_host.h"
#include "su_install_registry.h"

#ifndef MSU_KSU_COMPAT_RAW_RESIDENT
#define MSU_KSU_COMPAT_RAW_RESIDENT 0
#endif
#if MSU_KSU_COMPAT_RAW_RESIDENT != 0 && MSU_KSU_COMPAT_RAW_RESIDENT != 1
#error "MSU_KSU_COMPAT_RAW_RESIDENT must be 0 or 1"
#endif
#if MSU_KSU_COMPAT_RAW_RESIDENT
#include "manager_resident_abi.h"
#else
#include <linux/module.h>
#endif

struct msu_ksu_descriptor { u64 fence; };
struct msu_ksu_install_work {
    struct callback_head resume;
    struct task_struct *task;
    struct mm_struct *mm;
    const struct cred *cred;
    unsigned long exec_id;
    int __user *output;
    u64 fence;
};
static DEFINE_SPINLOCK(msu_ksu_lock);
static struct {
    bool opened, closed;
    u32 users;
#if MSU_KSU_COMPAT_RAW_RESIDENT
    const struct msu_resident_slot *slot;
    const void *memory;
    u64 allocation_bytes;
#endif
} msu_ksu;

static int msu_ksu_root(void)
{
    const struct cred *cred;
    if (!msu_runtime_layout_ready()) return -EAGAIN;
    if (!in_task() || in_atomic() || irqs_disabled()) return -EWOULDBLOCK;
    cred = msu_dynamic_current_cred();
    if (!cred || cred != msu_dynamic_current_real_cred() ||
        MSU_DYN_FIELD(cred, cred, user_ns) != &init_user_ns ||
        __kuid_val(MSU_DYN_FIELD(cred, cred, uid)) ||
        __kuid_val(MSU_DYN_FIELD(cred, cred, euid))) return -EPERM;
    return 0;
}

int msu_ksu_compat_bind_boot_resident(const struct msu_resident_slot *slot,
    const void *memory, u64 allocation_bytes)
{
#if MSU_KSU_COMPAT_RAW_RESIDENT
    unsigned long irq, start = (unsigned long)memory, end;
    unsigned long code = (unsigned long)msu_ksu_compat_bind_boot_resident;
    unsigned long data = (unsigned long)&msu_ksu;
    int error = 0;
    if (!msu_runtime_layout_ready()) return -EAGAIN;
    if (!slot || ((unsigned long)slot & 63U) || !memory ||
        allocation_bytes < sizeof(msu_ksu) || allocation_bytes > ~0UL - start)
        return -EINVAL;
    end = start + allocation_bytes;
    if (code < start || code >= end || data < start || data > end - sizeof(msu_ksu) ||
        ((unsigned long)slot >= start && (unsigned long)slot < end)) return -EINVAL;
    if (smp_load_acquire(&slot->boot_magic) != MSU_RESIDENT_BOOT_MAGIC ||
        smp_load_acquire(&slot->state) != MSU_RESIDENT_INITIALIZING ||
        READ_ONCE(slot->memory) || READ_ONCE(slot->allocation_bytes)) return -ESTALE;
    spin_lock_irqsave(&msu_ksu_lock, irq);
    if (msu_ksu.closed || msu_ksu.opened || msu_ksu.users) error = -EBUSY;
    else if (msu_ksu.slot && (msu_ksu.slot != slot || msu_ksu.memory != memory ||
        msu_ksu.allocation_bytes != allocation_bytes)) error = -ESTALE;
    else { msu_ksu.slot = slot; msu_ksu.memory = memory; msu_ksu.allocation_bytes = allocation_bytes; }
    spin_unlock_irqrestore(&msu_ksu_lock, irq);
    return error;
#else
    (void)slot; (void)memory; (void)allocation_bytes;
    return -EOPNOTSUPP;
#endif
}
int msu_ksu_compat_open(void)
{
    unsigned long irq;
    int error = 0;
    if (!msu_runtime_layout_ready()) return -EAGAIN;
    spin_lock_irqsave(&msu_ksu_lock, irq);
    if (msu_ksu.closed) error = -ESHUTDOWN;
#if MSU_KSU_COMPAT_RAW_RESIDENT
    else if (!msu_ksu.slot) error = -ENODEV;
#endif
    else msu_ksu.opened = true;
    spin_unlock_irqrestore(&msu_ksu_lock, irq);
    return error;
}
int msu_ksu_compat_close(void)
{
    unsigned long irq;
    int error;
    spin_lock_irqsave(&msu_ksu_lock, irq);
    msu_ksu.closed = true;
    error = msu_ksu.users ? -EINPROGRESS : 0;
    spin_unlock_irqrestore(&msu_ksu_lock, irq);
    return error;
}
static int msu_ksu_get(void)
{
    unsigned long irq;
    int error = 0;
    spin_lock_irqsave(&msu_ksu_lock, irq);
    if (!msu_ksu.opened || msu_ksu.closed) error = -ESHUTDOWN;
    else if (msu_ksu.users == ~0U) error = -EOVERFLOW;
#if MSU_KSU_COMPAT_RAW_RESIDENT
    else if (!msu_ksu.slot ||
        smp_load_acquire(&msu_ksu.slot->boot_magic) != MSU_RESIDENT_BOOT_MAGIC ||
        smp_load_acquire(&msu_ksu.slot->state) != MSU_RESIDENT_READY ||
        READ_ONCE(msu_ksu.slot->memory) != (unsigned long)msu_ksu.memory ||
        READ_ONCE(msu_ksu.slot->allocation_bytes) != msu_ksu.allocation_bytes) error = -ENODEV;
#else
    else if (!THIS_MODULE || !try_module_get(THIS_MODULE)) error = -ENODEV;
#endif
    else ++msu_ksu.users;
    spin_unlock_irqrestore(&msu_ksu_lock, irq);
    return error;
}
static void msu_ksu_put(void)
{
    unsigned long irq;
    spin_lock_irqsave(&msu_ksu_lock, irq);
    --msu_ksu.users;
    spin_unlock_irqrestore(&msu_ksu_lock, irq);
#if !MSU_KSU_COMPAT_RAW_RESIDENT
    module_put(THIS_MODULE);
#endif
    /* A published READY raw resident is boot-owned, including callback return
     * paths. Failed initialization admits neither task_work nor descriptors. */
}
static long msu_ksu_ioctl(struct file *file, unsigned int command, unsigned long argument)
{
    const struct msu_ksu_descriptor *descriptor = MSU_DYN_FIELD(file, file, private_data);
    struct msu_ksu_compat_info info = { MSU_KSU_COMPAT_VERSION, 0, 0, 0 };
    struct msu_ksu_compat_uid uid;
    enum msu_su_non_root_umount_policy policy;
    u32 appid = MSU_KSU_COMPAT_INVALID_APPID, sid = 0, root_sid = 0;
    bool granted = false;
    void __user *output = (void __user *)argument;
    size_t size;
    int error;
    if (command != MSU_KSU_COMPAT_GET_INFO && command != MSU_KSU_COMPAT_GET_INFO_LEGACY &&
        command != MSU_KSU_COMPAT_UID_GRANTED_ROOT && command != MSU_KSU_COMPAT_UID_SHOULD_UMOUNT &&
        command != MSU_KSU_COMPAT_GET_MANAGER_APPID) return -ENOTTY;
    error = msu_ksu_root();
    if (error) return error;
    error = msu_su_runtime_ksu_validate(descriptor->fence);
    if (error) return error;
    if (command == MSU_KSU_COMPAT_GET_INFO || command == MSU_KSU_COMPAT_GET_INFO_LEGACY) {
        size = command == MSU_KSU_COMPAT_GET_INFO ? sizeof(info) : 3 * sizeof(u32);
        return copy_to_user(output, &info, size) ? -EFAULT : 0;
    }
    error = msu_manager_resident_ksu_appid(&appid);
    if (error) return error;
    if (command == MSU_KSU_COMPAT_GET_MANAGER_APPID)
        return copy_to_user(output, &appid, sizeof(appid)) ? -EFAULT : 0;
    if (copy_from_user(&uid, output, sizeof(uid))) return -EFAULT;
    error = msu_su_runtime_ksu_query(descriptor->fence, uid.uid, &granted, &policy);
    if (error) return error;
    if (appid != MSU_KSU_COMPAT_INVALID_APPID && uid.uid % 100000U == appid) {
        granted = true;
        policy = MSU_SU_NON_ROOT_UMOUNT_DISABLED;
    }
    if (!uid.uid && command == MSU_KSU_COMPAT_UID_GRANTED_ROOT) {
        error = security_secctx_to_secid("u:r:ksu:s0", sizeof("u:r:ksu:s0") - 1, &root_sid);
        if (error) return error;
        security_cred_getsecid(msu_dynamic_current_cred(), &sid);
        granted = root_sid && sid == root_sid;
    }
    uid.value = command == MSU_KSU_COMPAT_UID_GRANTED_ROOT ? granted :
        policy == MSU_SU_NON_ROOT_UMOUNT_ENABLED;
    error = msu_su_runtime_ksu_validate(descriptor->fence);
    if (error) return error;
    return copy_to_user(output, &uid, sizeof(uid)) ? -EFAULT : 0;
}
#ifdef CONFIG_COMPAT
static long msu_ksu_compat_ioctl(struct file *file, unsigned int command, unsigned long argument)
{
    return msu_ksu_ioctl(file, command, (unsigned long)compat_ptr(argument));
}
#endif
static int msu_ksu_release(struct inode *inode, struct file *file)
{
    (void)inode;
    kfree(MSU_DYN_FIELD(file, file, private_data));
    msu_ksu_put();
    return 0;
}
static const struct file_operations msu_ksu_fops = {
#if !MSU_KSU_COMPAT_RAW_RESIDENT
    .owner = THIS_MODULE,
#endif
    .unlocked_ioctl = msu_ksu_ioctl,
#ifdef CONFIG_COMPAT
    .compat_ioctl = msu_ksu_compat_ioctl,
#endif
    .release = msu_ksu_release,
};
#define MSU_KSU_FIELD(type, field) offsetof(type, field), sizeof(((type *)0)->field)
const u32 msu_ksu_compat_layout[MSU_KSU_COMPAT_LAYOUT_WORDS]
    __attribute__((section(".msu.ksu-compat.layout"), used)) = {
    1, sizeof(struct file_operations), __alignof__(struct file_operations),
    MSU_KSU_FIELD(struct file_operations, owner),
    MSU_KSU_FIELD(struct file_operations, unlocked_ioctl),
#ifdef CONFIG_COMPAT
    MSU_KSU_FIELD(struct file_operations, compat_ioctl),
#else
    ~0U, 0,
#endif
    MSU_KSU_FIELD(struct file_operations, release),
    sizeof(struct callback_head), MSU_KSU_FIELD(struct callback_head, func),
};
#undef MSU_KSU_FIELD
_Static_assert(sizeof(struct msu_ksu_compat_uid) == 8, "KSU fullUID query ABI");
_Static_assert(sizeof(struct msu_ksu_compat_info) == 16, "KSU GET_INFO ABI");

static void msu_ksu_install_resume(struct callback_head *callback)
{
    struct msu_ksu_install_work *work = container_of(callback, struct msu_ksu_install_work, resume);
    struct msu_ksu_descriptor *descriptor;
    struct file *file;
    int fd, error = msu_ksu_root();
    /* A saved userspace pointer belongs to precisely this task/mm/exec/cred.
     * Exit or replacement must not receive even an error at an old address. */
    if (current != work->task || msu_dynamic_current_cred() != work->cred ||
        MSU_DYN_FIELD(current, task_struct, mm) != work->mm ||
        MSU_DYN_FIELD(current, task_struct, self_exec_id) != work->exec_id ||
        (READ_ONCE(MSU_DYN_FIELD(current, task_struct, flags)) & PF_EXITING)) goto out;
    if (!error) error = msu_su_runtime_ksu_validate(work->fence);
    if (error) goto report;
    descriptor = kzalloc(sizeof(*descriptor), GFP_KERNEL);
    if (!descriptor) { error = -ENOMEM; goto report; }
    descriptor->fence = work->fence;
    error = msu_ksu_get();
    if (error) { kfree(descriptor); goto report; }
    fd = get_unused_fd_flags(O_CLOEXEC);
    if (fd < 0) { error = fd; msu_ksu_put(); kfree(descriptor); goto report; }
    file = anon_inode_getfile("[ksu_driver]", &msu_ksu_fops, descriptor, O_RDWR);
    if (IS_ERR(file)) {
        error = PTR_ERR(file); put_unused_fd(fd); msu_ksu_put(); kfree(descriptor); goto report;
    }
    error = msu_su_runtime_ksu_validate(work->fence);
    if (!error && copy_to_user(work->output, &fd, sizeof(fd))) error = -EFAULT;
    if (error) { put_unused_fd(fd); fput(file); goto out; }
    fd_install(fd, file);
    goto out;
report:
    if (copy_to_user(work->output, &error, sizeof(error))) goto out;
out:
    msu_dynamic_put_cred(work->cred);
    msu_dynamic_mmdrop(work->mm);
    msu_dynamic_put_task_struct(work->task);
    kfree(work);
    msu_ksu_put();
}
bool msu_ksu_compat_reboot(const struct pt_regs *regs, long *result)
{
    struct msu_ksu_install_work *work;
    u64 fence;
    int error;
    if (!regs || !result || regs != msu_dynamic_task_pt_regs(current) ||
        (u32)READ_ONCE(regs->regs[0]) != 0xdeadbeefU ||
        (u32)READ_ONCE(regs->regs[1]) != 0xcafebabeU ||
        READ_ONCE(regs->regs[2]) || msu_ksu_root() ||
        !MSU_DYN_FIELD(current, task_struct, mm)) return false;
    error = msu_su_runtime_ksu_fence(&fence);
    if (error) { *result = error; return true; }
    error = msu_ksu_get();
    if (error) { *result = error; return true; }
    work = kzalloc(sizeof(*work), GFP_KERNEL);
    if (!work) { msu_ksu_put(); *result = -ENOMEM; return true; }
    work->task = current; msu_dynamic_get_task_struct(work->task);
    work->mm = MSU_DYN_FIELD(current, task_struct, mm); msu_dynamic_mmgrab(work->mm);
    work->cred = msu_dynamic_get_current_cred();
    work->exec_id = MSU_DYN_FIELD(current, task_struct, self_exec_id);
    work->output = (int __user *)(unsigned long)(is_compat_task() ?
        (u32)READ_ONCE(regs->regs[3]) : READ_ONCE(regs->regs[3]));
    work->fence = fence; work->resume.func = msu_ksu_install_resume;
    error = task_work_add(current, &work->resume, TWA_RESUME);
    if (error) {
        msu_dynamic_put_cred(work->cred); msu_dynamic_mmdrop(work->mm);
        msu_dynamic_put_task_struct(work->task); kfree(work); msu_ksu_put();
    }
    /* Fixed KSU's kprobe also leaves the invalid magic reboot's EINVAL result;
     * userspace obtains the descriptor through arg4 on its resume callback. */
    *result = error ? error : -EINVAL;
    return true;
}
