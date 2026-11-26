#include "boundary.h"
#include "production-query-slices.h"
#include "manager_ksu_compat.c"
#ifdef CONFIG_COMPAT
#include <sys/mman.h>
#endif

static struct cred root_cred={&init_user_ns,0,0,0}, app_cred={&init_user_ns,10123,10123,0};
static struct mm_struct task_mm;
static struct task_struct task;
static int checks;
#define CHECK(expr) do { ++checks; if(!(expr)){fprintf(stderr,"check failed %s:%d: %s\n",__FILE__,__LINE__,#expr);exit(1);} } while(0)
static void reset(void)
{
    for(int i=0;i<256;++i)assert(!fixture_files[i]);
    assert(!fixture_pending && !fixture_module_refs && !fixture_snapshot.refs && !runtime.users);
    assert(!root_cred.refs && !task_mm.refs && !task.refs);
    memset(&msu_ksu,0,sizeof(msu_ksu)); memset(&runtime,0,sizeof(runtime));
    memset(&task,0,sizeof(task)); task.mm=&task_mm;task.self_exec_id=42;current=&task;
    fixture_cred=fixture_real_cred=&root_cred; root_cred.user_ns=&init_user_ns;
    fixture_copy_fault=fixture_copies=fixture_compat=fixture_alloc_fail=fixture_task_add_error=fixture_anon_error=fixture_fd_error=0;
    fixture_revoke_during_lookup=fixture_lookup_block=fixture_lookup_entered=fixture_lookup_resume=0;
    memset(&fixture_regs,0,sizeof(fixture_regs));
    memset(&fixture_owner,0,sizeof(fixture_owner)); fixture_owner.initialized=2;fixture_owner.epoch=7;
    memset(&fixture_registry,0,sizeof(fixture_registry)); pthread_mutex_init(&fixture_registry.lock,NULL);
    fixture_registry.gateway_bound=true;fixture_registry.controller=1;fixture_registry.count=4;
    memset(fixture_entries,0,sizeof(fixture_entries));
    u32 uids[4]={10123,10124,110123,110124};
    for(int i=0;i<4;++i){ struct msu_su_install_entry *e=&fixture_entries[i]; fixture_registry.entries[i]=e;
      e->full_uid=e->installation.full_uid=uids[i];e->installation.pin=e;e->generation=10+i;
      e->granted=i==0;e->saved_profile.explicit_profile=i!=0;e->saved_profile.non_root=i!=0;
      e->saved_profile.umount_modules=i==1;e->saved_profile.non_root_use_default=i==3; }
    runtime.initialized=runtime.bound=runtime.started=runtime.published=true;
    runtime.registry=&fixture_registry;runtime.installation_fence=3;
    CHECK(msu_ksu_compat_open()==0);
}
static int open_driver(void)
{
    int fd=-77; long result=999;
    fixture_regs.regs[0]=0xdeadbeefU;fixture_regs.regs[1]=0xcafebabeU;fixture_regs.regs[3]=(uintptr_t)&fd;
    CHECK(msu_ksu_compat_reboot(&fixture_regs,&result));CHECK(result==-EINVAL);CHECK(fd==-77);
    CHECK(msu_ksu.users==1 && fixture_pending);
    struct callback_head *callback=fixture_pending;fixture_pending=NULL;callback->func(callback);
    CHECK(fd>=3 && fixture_files[fd]); CHECK(msu_ksu.users==1);
    CHECK(!root_cred.refs && !task_mm.refs && !task.refs);
    return fd;
}
static long query(int fd,unsigned command,void *output)
{ return fixture_files[fd]->fops->unlocked_ioctl(fixture_files[fd],command,(uintptr_t)output); }
static void close_driver(int fd)
{ struct file *file=fixture_files[fd];fixture_files[fd]=NULL;fput(file);CHECK(!msu_ksu.users && !fixture_module_refs); }
static void query_cases(void)
{
    reset(); int fd=open_driver();
    struct msu_ksu_compat_info info={0}; CHECK(query(fd,MSU_KSU_COMPAT_GET_INFO,&info)==0);
    CHECK(info.version==10940 && !info.flags && !info.features && !info.uapi_version);
    u32 legacy[4]={0,0,0,0x76543210};CHECK(query(fd,MSU_KSU_COMPAT_GET_INFO_LEGACY,legacy)==0);
    CHECK(legacy[0]==10940 && legacy[3]==0x76543210);
    u32 appid=0;CHECK(query(fd,MSU_KSU_COMPAT_GET_MANAGER_APPID,&appid)==0);CHECK(appid==~0U);
    struct msu_ksu_compat_uid uid={10123,0,{1,2,3}};
    CHECK(query(fd,MSU_KSU_COMPAT_UID_GRANTED_ROOT,&uid)==0 && uid.value==1 && uid.uid==10123);
    uid.uid=110123;CHECK(query(fd,MSU_KSU_COMPAT_UID_GRANTED_ROOT,&uid)==0 && uid.value==0);
    uid.uid=10124;CHECK(query(fd,MSU_KSU_COMPAT_UID_SHOULD_UMOUNT,&uid)==0 && uid.value==1);
    uid.uid=110123;CHECK(query(fd,MSU_KSU_COMPAT_UID_SHOULD_UMOUNT,&uid)==0 && uid.value==0);
    uid.uid=110124;CHECK(query(fd,MSU_KSU_COMPAT_UID_SHOULD_UMOUNT,&uid)==0 && uid.value==0);
    uid.uid=10125;CHECK(query(fd,MSU_KSU_COMPAT_UID_GRANTED_ROOT,&uid)==0 && uid.value==0);
    uid.uid=0;CHECK(query(fd,MSU_KSU_COMPAT_UID_GRANTED_ROOT,&uid)==0 && uid.value==1);
    struct msu_manager_owner_binding binding={NULL,((u64)7<<32)|10200,0};fixture_owner.bindings=&binding;
    CHECK(query(fd,MSU_KSU_COMPAT_GET_MANAGER_APPID,&appid)==0 && appid==10200);
    uid.uid=110200;CHECK(query(fd,MSU_KSU_COMPAT_UID_GRANTED_ROOT,&uid)==0 && uid.value==1);
    CHECK(query(fd,MSU_KSU_COMPAT_UID_SHOULD_UMOUNT,&uid)==0 && uid.value==0);
    fixture_owner.epoch=8;CHECK(query(fd,MSU_KSU_COMPAT_GET_MANAGER_APPID,&appid)==0 && appid==~0U);
    CHECK(query(fd,0x12345678,&info)==-ENOTTY);
    CHECK(query(fd,0x4b01,&info)==-ENOTTY);
    fixture_cred=fixture_real_cred=&app_cred;info.version=27;
    CHECK(query(fd,MSU_KSU_COMPAT_GET_INFO,&info)==-EPERM && info.version==27);
    bool granted=true;enum msu_su_non_root_umount_policy policy=MSU_SU_NON_ROOT_UMOUNT_ENABLED;
    CHECK(msu_su_registry_query_root(&fixture_registry,10123,&granted,&policy)==-EPERM);
    fixture_cred=fixture_real_cred=&root_cred;root_cred.user_ns=&child_ns;
    CHECK(query(fd,MSU_KSU_COMPAT_GET_MANAGER_APPID,&appid)==-EPERM);root_cred.user_ns=&init_user_ns;
    uid.uid=10123;uid.value=88;fixture_revoke_during_lookup=1;
    CHECK(query(fd,MSU_KSU_COMPAT_UID_GRANTED_ROOT,&uid)==-ESTALE && uid.value==88);
    fixture_revoke_during_lookup=0;fixture_entries[0].revoked=false;
    ++runtime.installation_fence;CHECK(query(fd,MSU_KSU_COMPAT_GET_INFO,&info)==-ESTALE);
    close_driver(fd);
    puts("target fullUID, cross-user, explicit profiles, defaults, manager binding/invalidation, root-only, revoke and stale-fd passed");
}
static void work_cases(void)
{
    for(int mode=0;mode<12;++mode){reset();int output=-77;long result=999;
      fixture_regs.regs[0]=0xdeadbeefU;fixture_regs.regs[1]=0xcafebabeU;fixture_regs.regs[3]=(uintptr_t)&output;
      if(mode==0){fixture_cred=fixture_real_cred=&app_cred;CHECK(!msu_ksu_compat_reboot(&fixture_regs,&result));CHECK(result==999 && !msu_ksu.users);continue;}
      if(mode==1){fixture_regs.regs[0]=0;CHECK(!msu_ksu_compat_reboot(&fixture_regs,&result));CHECK(!msu_ksu.users);continue;}
      if(mode==2){fixture_task_add_error=-ESRCH;CHECK(msu_ksu_compat_reboot(&fixture_regs,&result));CHECK(result==-ESRCH && !msu_ksu.users);continue;}
      if(mode==8){fixture_alloc_fail=1;CHECK(msu_ksu_compat_reboot(&fixture_regs,&result));CHECK(result==-ENOMEM && !msu_ksu.users);continue;}
      CHECK(msu_ksu_compat_reboot(&fixture_regs,&result));CHECK(fixture_pending && msu_ksu.users==1);
      if(mode==3)++task.self_exec_id;
      if(mode==4)task.flags|=PF_EXITING;
      if(mode==5)fixture_copy_fault=1;
      if(mode==6)fixture_anon_error=-ENOMEM;
      if(mode==7)fixture_fd_error=-EMFILE;
      if(mode==9)fixture_alloc_fail=1;
      if(mode==10)fixture_cred=fixture_real_cred=&app_cred;
      if(mode==11)++runtime.installation_fence;
      struct callback_head *callback=fixture_pending;fixture_pending=NULL;callback->func(callback);
      CHECK(!msu_ksu.users && !fixture_module_refs && !root_cred.refs && !task_mm.refs && !task.refs);
      if(mode==3||mode==4||mode==10)CHECK(output==-77 && !fixture_copies);
      if(mode==6)CHECK(output==-ENOMEM);if(mode==7)CHECK(output==-EMFILE);
      if(mode==9)CHECK(output==-ENOMEM);if(mode==11)CHECK(output==-ESTALE);
    }
    puts("ordinary reboot/nonroot unchanged, taskwork queue failure, exec/exit, copy, anon-file and fd-allocation cleanup passed");
}
#ifdef CONFIG_COMPAT
static void compat_pointer_case(void)
{
    reset();
    void *page=mmap(NULL,4096,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS|MAP_32BIT,-1,0);
    CHECK(page!=MAP_FAILED && (uintptr_t)page<=UINT32_MAX-4096U);
    int *output=page;*output=-77;long result=999;
    fixture_compat=1;
    fixture_regs.regs[0]=0xdeadbeefU;fixture_regs.regs[1]=0xcafebabeU;
    fixture_regs.regs[3]=(UINT64_C(0xfeedbeef)<<32)|(uintptr_t)page;
    CHECK(msu_ksu_compat_reboot(&fixture_regs,&result) && result==-EINVAL);
    struct callback_head *callback=fixture_pending;fixture_pending=NULL;callback->func(callback);
    const int fd=*output;CHECK(fd>=3 && fixture_files[fd]);
    struct msu_ksu_compat_info *info=(void *)((char *)page+16);
    CHECK(fixture_files[fd]->fops->compat_ioctl(fixture_files[fd],MSU_KSU_COMPAT_GET_INFO,
        (UINT64_C(0x12345678)<<32)|(uintptr_t)info)==0 && info->version==10940);
    struct msu_ksu_compat_uid *uid=(void *)((char *)page+64);
    uid->uid=110123;uid->value=77;
    CHECK(fixture_files[fd]->fops->compat_ioctl(fixture_files[fd],MSU_KSU_COMPAT_UID_GRANTED_ROOT,
        (UINT64_C(0x87654321)<<32)|(uintptr_t)uid)==0 && uid->uid==110123 && uid->value==0);
    close_driver(fd);CHECK(munmap(page,4096)==0);
    puts("compat reboot arg4 and compat_ioctl truncate upper pointer bits and execute against a real low-address mapping");
}
#endif
static void *concurrent_query(void *context)
{
    int fd=*(int *)context; struct msu_ksu_compat_uid uid={10123,77,{0}};
    long result=query(fd,MSU_KSU_COMPAT_UID_GRANTED_ROOT,&uid);
    CHECK(result==-ESTALE && uid.value==77);return NULL;
}
static void retirement_case(void)
{
    reset();int fd=open_driver();fixture_lookup_block=1;pthread_t worker;
    CHECK(pthread_create(&worker,NULL,concurrent_query,&fd)==0);
    pthread_mutex_lock(&fixture_wait_lock);while(!fixture_lookup_entered)pthread_cond_wait(&fixture_wait_cond,&fixture_wait_lock);pthread_mutex_unlock(&fixture_wait_lock);
    pthread_mutex_lock(&msu_su_runtime_lock);CHECK(runtime.users==1);runtime.closed=true;pthread_mutex_unlock(&msu_su_runtime_lock);
    CHECK(msu_ksu_compat_close()==-EINPROGRESS);
    pthread_mutex_lock(&fixture_wait_lock);fixture_lookup_resume=1;pthread_cond_broadcast(&fixture_wait_cond);pthread_mutex_unlock(&fixture_wait_lock);
    CHECK(pthread_join(worker,NULL)==0);CHECK(runtime.users==0);
    close_driver(fd);CHECK(msu_ksu_compat_close()==0);
    puts("in-flight target query versus concurrent retirement: real pending, stale output withheld, final release drain passed");
}
int main(void)
{
    query_cases();work_cases();retirement_case();
#ifdef CONFIG_COMPAT
    compat_pointer_case();
#endif
    printf("KSU_COMPAT_SOURCE_PASS checks=%d compat=%d\n",checks,
#ifdef CONFIG_COMPAT
        1
#else
        0
#endif
    );return 0;
}
