#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "boot_sdk_protocol.h"
#include "boot_sdk_allocator.h"
typedef uintptr_t uptr;
typedef uint32_t u32;
typedef uint64_t u64;
#define MSU_PH_NOCFI
#define MSU_BS_STATIC_BOOTSTRAP 1
#define MSU_BS_ENVIRONMENT 0
#define MSU_BS_PAGE_BYTES 4096UL
#define MSU_BS_ARENA_PAGES 2u
#define MSU_BS_IMAGE_BYTES 8192UL
#define MSU_BS_VM_START_MAG 0x800000000000UL
#define MSU_BS_VM_END_MAG 0x7fff00000000UL
#define MSU_BS_VM_PROT_FLAG 100u
#define MSU_BS_VM_PROT 1u
#define MSU_BS_VM_PROT_SET 2u
#define MSU_BS_VM_ALIGN 1u
#define MSU_BS_VM_GFP 0xcc0u
#define MSU_BS_KALLSYMS 400u
#define MSU_BS_KP_NAME 0u
#define MSU_BS_KP_PRE 8u
#define MSU_BS_KP_ADDR 16u
#define MSU_BS_KERNEL_NOTES_BYTES 4u
#define MSU_BS_KERNEL_NOTES {1,2,3,4}
static u32 state;
static int sdk_dynamic_ready;
static int bootstrap_error, probe_installed, allocations, hooks, mode;
static uptr kernel_base=0xffff800000000000UL, arena, native_execve=0xffff800000000100UL, avc_result;
static unsigned char msu_sdk_call_template[4], notes[4]={1,2,3,4}, probe[64];
static unsigned char *msu_sdk_call_template_end=msu_sdk_call_template+4, *msu_sdk_call_blr=msu_sdk_call_template;
static char root_key[49], original_root_key[49];
static struct msu_boot_sdk_layout sdk_layout;
static struct msu_sdk_vm sdk_vm={MSU_BS_IMAGE_BYTES,0UL-MSU_BS_VM_START_MAG,0UL-MSU_BS_VM_END_MAG,
 MSU_BS_VM_ALIGN,MSU_BS_VM_GFP,MSU_BS_VM_PROT,MSU_BS_VM_PROT_SET,MSU_BS_VM_PROT_FLAG};
static u32 sdk_kallsyms=MSU_BS_KALLSYMS;
struct sdk_pgprot { uptr pgprot; };
#define MSU_SDK_CODE_START msu_sdk_call_template
typedef struct sdk_pgprot MSU_SDK_PGPROT;
static void zero(void *p, unsigned long n) { memset(p,0,n); }
static uptr lookup_name(const char *name) {
 if(mode==2)return 0;
 return (uptr)notes + (!strcmp(name,"__start_notes") ? 0 : sizeof(notes)+(mode==3));
}
static long read_kernel(void *out, const void *in, unsigned long n) {
 if(mode==4)return -14;
 if(in==notes)memcpy(out,in,n);else memset(out,0,n);
 return 0;
}
static void *allocate_range(unsigned long a,unsigned long b,uptr c,uptr d,unsigned e,
 struct sdk_pgprot f,unsigned long g,int h,const void *i) {
 (void)a;(void)b;(void)c;(void)d;(void)e;(void)f;(void)g;(void)h;(void)i;++allocations;return NULL;
}
static void *(*allocate_segmented_range)(unsigned long,unsigned long,unsigned int,unsigned int,
 unsigned int,unsigned int,uptr,uptr,unsigned int,struct sdk_pgprot,unsigned long,int,const void *);
static int memory_nx(uptr p,int n){(void)p;(void)n;return 0;}
static int memory_rw(uptr p,int n){(void)p;(void)n;return 0;}
static int memory_ro(uptr p,int n){(void)p;(void)n;return 0;}
static int memory_x(uptr p,int n){(void)p;(void)n;return 0;}
static void random_bytes(void *p,unsigned long n){memset(p,0,n);}
static void flush_code(uptr p,uptr e){(void)p;(void)e;}
static void release(void *p){(void)p;}
static int register_kprobe(void *p){(void)p;++hooks;return 0;}
static void unregister_kprobe(void *p){(void)p;}
static int msu_boot_sdk_pre(void *p,void *r){(void)p;(void)r;return 0;}
/* This fixture's allocator always fails before the publication point. */
static void request_v2_init(int audit_published){(void)audit_published;}
#include "bootstrap.inc"
int main(int argc,char **argv) {
 const char *name=argc>1?argv[1]:"foreign";
 if(!strcmp(name,"foreign")){mode=1;notes[3]^=1;}
 if(!strcmp(name,"missing"))mode=2;
 if(!strcmp(name,"extent"))mode=3;
 if(!strcmp(name,"read-error"))mode=4;
 if(!strcmp(name,"dynamic")){mode=1;notes[3]^=1;sdk_dynamic_ready=1;}
 (void)sdk_dynamic_ready;
 int rc=bootstrap(), first=allocations;
 int again=bootstrap();
 printf("rc=%d allocations=%d hooks=%d state=%u stable=%d\n",rc,allocations,hooks,state,again==rc&&first==allocations);
 return 0;
}
