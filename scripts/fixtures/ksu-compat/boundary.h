#ifndef MSU_KSU_COMPAT_TEST_BOUNDARY_H
#define MSU_KSU_COMPAT_TEST_BOUNDARY_H
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
typedef uint8_t u8;
typedef uint32_t u32;
typedef uint64_t u64;
typedef pthread_mutex_t raw_spinlock_t;
#define __user
#define READ_ONCE(value) (value)
#define MSU_DYN_FIELD(object, type, field) ((object)->field)
#define __kuid_val(value) (value)
#define DEFINE_SPINLOCK(name) pthread_mutex_t name = PTHREAD_MUTEX_INITIALIZER
#define spin_lock_irqsave(lock, irq) do { (irq) = 0; pthread_mutex_lock(lock); } while (0)
#define spin_unlock_irqrestore(lock, irq) do { (void)(irq); pthread_mutex_unlock(lock); } while (0)
#define raw_spin_lock_irqsave spin_lock_irqsave
#define raw_spin_unlock_irqrestore spin_unlock_irqrestore
#define GFP_KERNEL 0
#define PF_EXITING 4
#define TWA_RESUME 1
#define container_of(ptr, type, member) ((type *)((char *)(ptr) - offsetof(type, member)))
#define THIS_MODULE ((void *)1)
#define IS_ERR(ptr) ((uintptr_t)(ptr) >= (uintptr_t)-4095L)
#define PTR_ERR(ptr) ((long)(ptr))
struct user_namespace { int fixture; };
static struct user_namespace init_user_ns, child_ns;
struct cred { struct user_namespace *user_ns; u32 uid, euid; int refs; };
struct mm_struct { int refs; };
struct task_struct { struct mm_struct *mm; unsigned long self_exec_id, flags; int refs; };
struct pt_regs { unsigned long regs[31]; };
struct callback_head { struct callback_head *next; void (*func)(struct callback_head *); };
struct inode { int fixture; };
struct file;
struct file_operations {
    void *owner;
    long (*unlocked_ioctl)(struct file *, unsigned int, unsigned long);
#ifdef CONFIG_COMPAT
    long (*compat_ioctl)(struct file *, unsigned int, unsigned long);
#endif
    int (*release)(struct inode *, struct file *);
};
struct file { void *private_data; const struct file_operations *fops; };
static struct task_struct *current;
static struct cred *fixture_cred, *fixture_real_cred;
static struct pt_regs fixture_regs;
static int fixture_copies, fixture_copy_fault, fixture_compat;
static int fixture_alloc_fail, fixture_task_add_error, fixture_anon_error, fixture_fd_error, fixture_module_refs;
static struct callback_head *fixture_pending;
static struct file *fixture_files[256];
static bool msu_runtime_layout_ready(void) { return true; }
static bool in_task(void) { return true; }
static bool in_atomic(void) { return false; }
static bool irqs_disabled(void) { return false; }
static const struct cred *msu_dynamic_current_cred(void) { return fixture_cred; }
static const struct cred *msu_dynamic_current_real_cred(void) { return fixture_real_cred; }
static const struct cred *msu_dynamic_get_current_cred(void) { ++fixture_cred->refs; return fixture_cred; }
static void msu_dynamic_put_cred(const struct cred *cred) { --((struct cred *)cred)->refs; }
static void msu_dynamic_get_task_struct(struct task_struct *task) { ++task->refs; }
static void msu_dynamic_put_task_struct(struct task_struct *task) { --task->refs; }
static void msu_dynamic_mmgrab(struct mm_struct *mm) { assert(mm); ++mm->refs; }
static void msu_dynamic_mmdrop(struct mm_struct *mm) { assert(mm); --mm->refs; }
static struct pt_regs *msu_dynamic_task_pt_regs(struct task_struct *task) { (void)task; return &fixture_regs; }
static bool is_compat_task(void) { return fixture_compat; }
#ifdef CONFIG_COMPAT
static void *compat_ptr(unsigned long pointer) { return (void *)(uintptr_t)(u32)pointer; }
#endif
static void *kzalloc(size_t bytes, int flags) { (void)flags; if (fixture_alloc_fail) return NULL; return calloc(1, bytes); }
static void kfree(void *pointer) { free(pointer); }
static unsigned long copy_to_user(void *to, const void *from, size_t bytes)
{ ++fixture_copies; if (!to || fixture_copy_fault) return bytes; memcpy(to, from, bytes); return 0; }
static unsigned long copy_from_user(void *to, const void *from, size_t bytes)
{ if (!from || fixture_copy_fault) return bytes; memcpy(to, from, bytes); return 0; }
static int try_module_get(void *module) { (void)module; ++fixture_module_refs; return 1; }
static void module_put(void *module) { (void)module; --fixture_module_refs; }
static int get_unused_fd_flags(int flags)
{ (void)flags; if (fixture_fd_error) return fixture_fd_error; for (int i=3;i<256;++i) if (!fixture_files[i]) return i; return -EMFILE; }
static void put_unused_fd(int fd) { (void)fd; }
static struct file *anon_inode_getfile(const char *name, const struct file_operations *fops, void *data, int flags)
{ (void)flags; assert(!strcmp(name,"[ksu_driver]")); if (fixture_anon_error) return (void *)(intptr_t)fixture_anon_error;
  struct file *file=calloc(1,sizeof(*file)); assert(file); file->private_data=data; file->fops=fops; return file; }
static void fd_install(int fd, struct file *file) { assert(!fixture_files[fd]); fixture_files[fd]=file; }
static void fput(struct file *file) { file->fops->release(NULL,file); free(file); }
static int task_work_add(struct task_struct *task, struct callback_head *callback, int flags)
{ (void)task; (void)flags; if (fixture_task_add_error) return fixture_task_add_error; assert(!fixture_pending); fixture_pending=callback; return 0; }
static int security_secctx_to_secid(const char *context, u32 length, u32 *sid)
{ assert(length==10 && !strcmp(context,"u:r:ksu:s0")); *sid=99; return 0; }
static void security_cred_getsecid(const struct cred *cred, u32 *sid) { (void)cred; *sid=99; }
enum msu_su_non_root_umount_policy { MSU_SU_NON_ROOT_UMOUNT_DEFAULT=0, MSU_SU_NON_ROOT_UMOUNT_DISABLED, MSU_SU_NON_ROOT_UMOUNT_ENABLED };
struct msu_su_profile { bool explicit_profile, allow_su, non_root, non_root_use_default, umount_modules; };
struct msu_su_install_entry {
    u32 full_uid, users; u64 generation;
    bool revoked, busy, cancelled, granted, source_invalidated;
    struct { u32 full_uid; const void *pin; } installation;
    struct msu_su_profile saved_profile;
};
struct msu_su_registry {
    raw_spinlock_t lock;
    struct { u64 low, high; } host;
    u64 controller; u32 count;
    bool closed, gateway_binding, gateway_bound, gateway_retired;
    struct msu_su_install_entry *entries[8];
};
struct msu_uid_snapshot { int refs; };
struct msu_uid_snapshot_record { u32 full_uid, decision; u64 profile_revision; };
static struct msu_uid_snapshot fixture_snapshot;
static struct msu_su_registry fixture_registry;
static struct msu_su_install_entry fixture_entries[4];
static pthread_mutex_t fixture_wait_lock=PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t fixture_wait_cond=PTHREAD_COND_INITIALIZER;
static int fixture_lookup_block, fixture_lookup_entered, fixture_lookup_resume;
static int fixture_revoke_during_lookup;
static bool msu_su_sleepable(void) { return true; }
static u32 msu_su_position(struct msu_su_registry *registry, u32 uid)
{ u32 low=0, high=registry->count; while(low<high) {u32 m=low+(high-low)/2; if(registry->entries[m]->full_uid<uid)low=m+1;else high=m;} return low; }
static struct msu_uid_snapshot *msu_uid_gateway_acquire(const void *host, u64 controller)
{ (void)host; assert(controller==1); ++fixture_snapshot.refs; return &fixture_snapshot; }
static void msu_uid_snapshot_put(struct msu_uid_snapshot *snapshot) { --snapshot->refs; }
static int msu_uid_snapshot_lookup(struct msu_uid_snapshot *snapshot,u32 uid,u64 generation,struct msu_uid_snapshot_record *row)
{ assert(snapshot->refs>0); u32 p=msu_su_position(&fixture_registry,uid); if(p==fixture_registry.count||fixture_registry.entries[p]->full_uid!=uid)return -ENOENT;
  struct msu_su_install_entry *e=fixture_registry.entries[p]; row->full_uid=uid;row->decision=e->granted;row->profile_revision=generation;
  if(fixture_revoke_during_lookup)e->revoked=true;
  pthread_mutex_lock(&fixture_wait_lock); if(fixture_lookup_block){fixture_lookup_entered=1;pthread_cond_broadcast(&fixture_wait_cond);while(!fixture_lookup_resume)pthread_cond_wait(&fixture_wait_cond,&fixture_wait_lock);}pthread_mutex_unlock(&fixture_wait_lock);
  return 0; }
struct msu_manager_owner_binding { struct msu_manager_owner_binding *next; u64 identity; u32 user; };
struct msu_manager_owner { struct msu_manager_owner_binding *bindings; u32 epoch, initialized; };
static struct msu_manager_owner fixture_owner;
static DEFINE_SPINLOCK(msu_su_runtime_lock);
static struct {
    bool initialized,bound,started,published,closed,installation_closed;
    struct msu_su_registry *registry; u32 users; u64 installation_fence;
} runtime;
int msu_su_runtime_ksu_fence(u64 *fence);
int msu_su_runtime_ksu_validate(u64 fence);
int msu_su_runtime_ksu_query(u64 fence,u32 uid,bool *granted,enum msu_su_non_root_umount_policy *policy);
int msu_manager_owner_appid(const struct msu_manager_owner *owner,u32 *appid);
static int msu_manager_resident_ksu_appid(u32 *appid) { return msu_manager_owner_appid(&fixture_owner,appid); }
#endif
