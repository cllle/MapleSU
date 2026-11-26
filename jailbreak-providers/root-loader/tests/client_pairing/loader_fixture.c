/* Actual extracted B1/bootstrap functions; external OS/kernel seams are hooked. */
#include <stdint.h>
#include "boot_sdk_protocol.h"
#include "boot_sdk_allocator.h"
#include "request_protocol.h"
typedef uintptr_t uptr;
typedef uint32_t u32;
typedef unsigned long long u64;
#define MSU_PH_NOCFI
#define MSU_BS_STATIC_BOOTSTRAP 0
#define MSU_BS_ENVIRONMENT 1
#define MSU_BS_PAGE_BYTES 4096UL
#define MSU_BS_IDLE_PAGES 64u
#define MSU_BS_ARENA_PAGES 66u
#define MSU_BS_KP_ADDR 16u
#define MSU_BS_KP_NAME 24u
#define MSU_BS_KP_PRE 32u
#define MSU_BS_EXECVE_REG 1
#define MSU_BS_EXECVE_CHAR 0
#define FIXTURE_BASE UINT64_C(0xffff000040000000)
static unsigned char msu_sdk_call_template[4] = {0x1f,0x20,0x03,0xd5};
static unsigned char msu_sdk_lookup_template[8], msu_sdk_avc_template[16];
static unsigned char *msu_sdk_call_template_end = msu_sdk_call_template + 4;
static unsigned char *msu_sdk_call_blr = msu_sdk_call_template;
static unsigned char *msu_sdk_lookup_end = msu_sdk_lookup_template + 8;
static unsigned char *msu_sdk_lookup_target = msu_sdk_lookup_template;
static unsigned char *msu_sdk_avc_end = msu_sdk_avc_template + 16;
static unsigned char *msu_sdk_avc_branch = msu_sdk_avc_template + 4;
static unsigned char *msu_sdk_avc_result = msu_sdk_avc_template + 8;
static char root_key[49], original_root_key[49];
static unsigned char probe[64];
static uptr arena, kernel_base, native_execve, avc_result;
static u32 state, stage_busy, sdk_kallsyms = 0x400, sdk_execve_offset = 0x600;
static u32 sdk_filldir_offset = 0x800, sdk_seccomp_offset = 2280, sdk_audit_offset;
static int bootstrap_error, probe_installed, sdk_dynamic_ready = 1;
static maplesu_metadata_v2 sdk_request_metadata;
static struct msu_boot_sdk_layout sdk_layout;
static struct msu_sdk_vm sdk_vm;
struct sdk_pgprot { uptr pgprot; };
extern long copy_in(void *,const void *,unsigned long);
extern long copy_out(void *,const void *,unsigned long);
extern long read_kernel(void *,const void *,unsigned long);
extern long msu_sdk_call_original(uptr,const void *);
extern int private_cred(void), root_handover(void), take(u32 *);
extern void reply(u64);
extern u64 dispatch(unsigned char *);
extern int sdk_image_qualified(void);
extern void *allocate_range(unsigned long,unsigned long,uptr,uptr,unsigned int,
    struct sdk_pgprot,unsigned long,int,const void *);
static void *(*allocate_segmented_range)(unsigned long,unsigned long,unsigned int,unsigned int,
    unsigned int,unsigned int,uptr,uptr,unsigned int,struct sdk_pgprot,unsigned long,int,const void *);
extern int memory_nx(uptr,int),memory_rw(uptr,int),memory_ro(uptr,int),memory_x(uptr,int);
extern void random_bytes(void *,unsigned long),flush_code(uptr,uptr),release(void *);
extern int register_kprobe(void *);
extern void unregister_kprobe(void *);
extern int msu_boot_sdk_pre(void *,void *);
extern uptr sdk_lookup(const char *);
#include "functions.inc"

long fixture_bootstrap(unsigned audit) {
    kernel_base = FIXTURE_BASE;
    native_execve = kernel_base + 0x100;
    sdk_layout.environment = 1;
    sdk_audit_offset = audit ? 0x900 : 0;
    sdk_vm.image_bytes = 8192;
    sdk_vm.start = kernel_base;
    sdk_vm.end = kernel_base + 0x1000000;
    sdk_vm.align = 1;
    sdk_vm.gfp = 0xcc0;
    sdk_vm.prot = 1;
    sdk_vm.prot_set = 2;
    return bootstrap();
}
long fixture_call(uptr filename) {
    uptr args[] = {filename};
    return sdk_execve(args);
}
