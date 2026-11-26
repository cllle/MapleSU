/* Run the real bootstrap through append publication and READY, without a kernel. */
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "boot_sdk_protocol.h"
#include "boot_sdk_allocator.h"
#include "request_protocol.h"
typedef uintptr_t uptr;
typedef uint32_t u32;
typedef unsigned long long u64;
#define MSU_PH_NOCFI
#ifndef MSU_BS_PAGE_BYTES
#define MSU_BS_PAGE_BYTES 4096UL
#endif
#define MSU_BS_IDLE_PAGES (MSU_BS_ENVIRONMENT ? 64u : 0u)
#define MSU_BS_ARENA_PAGES (2u + MSU_BS_IDLE_PAGES)
#define MSU_BS_VM_START_MAG 0x800000000000UL
#define MSU_BS_VM_END_MAG 0x7fff00000000UL
#define MSU_BS_KP_ADDR 16u
#define MSU_BS_KP_NAME 24u
#define MSU_BS_KP_PRE 32u
#define MSU_BS_EXECVE_REG 1
#define MSU_BS_EXECVE_CHAR 0
static unsigned checks, failures;
#define CHECK(x) do { ++checks; if (!(x)) { ++failures; fprintf(stderr,"line %d: %s\n",__LINE__,#x); } } while(0)
static unsigned char storage[66 * MSU_BS_PAGE_BYTES] __attribute__((aligned(MSU_BS_PAGE_BYTES)));
static unsigned char msu_sdk_call_template[4], msu_sdk_lookup_template[8], msu_sdk_avc_template[16];
static unsigned char *msu_sdk_call_template_end = msu_sdk_call_template + 4;
static unsigned char *msu_sdk_call_blr = msu_sdk_call_template;
static unsigned char *msu_sdk_lookup_end = msu_sdk_lookup_template + 8;
static unsigned char *msu_sdk_lookup_target = msu_sdk_lookup_template;
static unsigned char *msu_sdk_avc_end = msu_sdk_avc_template + 16;
static unsigned char *msu_sdk_avc_branch = msu_sdk_avc_template + 4;
static unsigned char *msu_sdk_avc_result = msu_sdk_avc_template + 8;
static char root_key[49], original_root_key[49];
#ifdef MSU_BOOT_SDK_NATIVE
static char native_credential[49];
static int native_host_started;
static int filename_error;
static void memzero_explicit(void *p,unsigned long n){memset(p,0,n);}
#endif
static unsigned char probe[64];
static uptr arena, kernel_base, native_execve, avc_result;
static u32 state, sdk_kallsyms = 0x400, sdk_execve_offset = 0x600;
static u32 sdk_filldir_offset = 0x800, sdk_seccomp_offset = 2280, sdk_audit_offset;
static int bootstrap_error, probe_installed, sdk_dynamic_ready = 1, hook_error;
static unsigned allocations, hooks, protection_calls;
static unsigned allocator_kind, nine_calls, thirteen_calls;
static maplesu_metadata_v2 sdk_request_metadata;
static struct msu_boot_sdk_layout sdk_layout;
static struct msu_sdk_vm sdk_vm;
struct sdk_pgprot { uptr pgprot; };
#define MSU_SDK_CODE_START msu_sdk_call_template
typedef struct sdk_pgprot MSU_SDK_PGPROT;
static int sdk_image_qualified(void) { return 1; }
static long read_kernel(void *to,const void *from,unsigned long bytes) { (void)from; memset(to,0,bytes); return 0; }
static void *fixture_allocate_range(unsigned long bytes,unsigned long align,uptr start,uptr end,unsigned int flags,
    struct sdk_pgprot prot,unsigned long shift,int node,const void *caller) {
    CHECK((allocator_kind&10)!=10);
    CHECK(align==sdk_vm.align&&flags==sdk_vm.gfp&&prot.pgprot==sdk_vm.prot);
    CHECK(!shift&&node==-1&&caller);
    CHECK(state == MSU_BOOT_SDK_INITIALIZING && !sdk_request_metadata.magic);
    CHECK(bytes == MSU_BS_ARENA_PAGES * MSU_BS_PAGE_BYTES && start < end);
    ++allocations; ++nine_calls; return storage;
}
static void *fixture_allocate_segmented_range(unsigned long bytes,unsigned long align,
    unsigned int begin,unsigned int pages,unsigned int init_begin,unsigned int init_end,
    uptr start,uptr end,unsigned int flags,struct sdk_pgprot prot,unsigned long shift,int node,const void *caller) {
    CHECK((allocator_kind&10)==10&&!begin&&pages==MSU_BS_ARENA_PAGES&&!init_begin&&init_end==MSU_BS_ARENA_PAGES);
    CHECK(align==sdk_vm.align&&flags==sdk_vm.gfp&&prot.pgprot==sdk_vm.prot);
    CHECK(!shift&&node==-1&&caller);
    CHECK(state==MSU_BOOT_SDK_INITIALIZING&&!sdk_request_metadata.magic);
    CHECK(bytes==MSU_BS_ARENA_PAGES*MSU_BS_PAGE_BYTES&&start<end);
    ++allocations; ++thirteen_calls; return storage;
}
static void *(*allocate_range)(unsigned long,unsigned long,uptr,uptr,unsigned int,
    struct sdk_pgprot,unsigned long,int,const void *)=fixture_allocate_range;
static void *(*allocate_segmented_range)(unsigned long,unsigned long,unsigned int,unsigned int,
    unsigned int,unsigned int,uptr,uptr,unsigned int,struct sdk_pgprot,unsigned long,int,const void *)=fixture_allocate_segmented_range;
static int protection(uptr at,int pages) {
    CHECK(state == MSU_BOOT_SDK_INITIALIZING && !sdk_request_metadata.magic);
    CHECK(at >= (uptr)storage && pages > 0); ++protection_calls; return 0;
}
static int memory_nx(uptr p,int n) { return protection(p,n); }
static int memory_rw(uptr p,int n) { return protection(p,n); }
static int memory_ro(uptr p,int n) { return protection(p,n); }
static int memory_x(uptr p,int n) { return protection(p,n); }
static void random_bytes(void *p,unsigned long n) { memset(p,0,n); }
static void flush_code(uptr p,uptr end) { CHECK(p == arena && end > p); }
static void release(void *p) { CHECK(p == storage); }
static int register_kprobe(void *p) {
    CHECK(!sdk_request_metadata.magic && state == MSU_BOOT_SDK_INITIALIZING);
    ++hooks; *(uptr *)((unsigned char *)p + MSU_BS_KP_ADDR) = native_execve; return hook_error;
}
static void unregister_kprobe(void *p) { CHECK(p == probe); }
static int msu_boot_sdk_pre(void *p,void *r) { (void)p;(void)r; return 0; }
static uptr sdk_lookup(const char *name) { (void)name; return 0; }
#ifdef MSU_BOOT_SDK_NATIVE
static int msu_init_bridge_host_start(uptr image,const void *append,unsigned bytes,const char *key){
    CHECK(image==kernel_base&&append==storage+MSU_BS_PAGE_BYTES&&bytes>=54);
    CHECK(!memcmp(key,root_key,48));return 0;
}
static int msu_init_bridge_host_abort(void){return 0;}
static int msu_init_bridge_environment_filename(unsigned int *offset){if(filename_error)return filename_error;*offset=24;return 0;}
#endif
#include "boot_sdk_v2_bootstrap.inc"
static void cold(unsigned environment,unsigned audit,int failure) {
    memset(storage,0xa5,sizeof storage); memset(&sdk_request_metadata,0,sizeof sdk_request_metadata);
    memset(root_key,0,sizeof root_key); memset(original_root_key,0,sizeof original_root_key); memset(probe,0,sizeof probe);
    state = MSU_BOOT_SDK_EMPTY; arena = avc_result = 0; bootstrap_error = probe_installed = 0;
#ifdef MSU_BOOT_SDK_NATIVE
    memset(native_credential,'0',48);native_credential[48]=0;native_host_started=0;
#endif
    allocations = hooks = protection_calls = nine_calls = thirteen_calls = 0; hook_error = failure;
    sdk_layout.environment = environment; sdk_layout.allocator_noprof=allocator_kind; sdk_audit_offset = audit;
    kernel_base = (uptr)storage - 0x100000; native_execve = kernel_base + 0x100;
    sdk_vm.image_bytes = 8192; sdk_vm.start = kernel_base; sdk_vm.end = kernel_base + 0x10000000;
    sdk_vm.align = 1; sdk_vm.gfp = 0xcc0; sdk_vm.prot = 1; sdk_vm.prot_set = 2; sdk_vm.flag = 0;
    int error = bootstrap();
    if (failure) {
        CHECK(error == failure && state == MSU_BOOT_SDK_FAILED && !sdk_request_metadata.magic);
        for(unsigned n=0;n<sizeof root_key;++n)CHECK(!root_key[n]&&!original_root_key[n]);
        return;
    }
    CHECK(error == 0 && state == MSU_BOOT_SDK_READY && allocations == 1 && hooks == 1);
    CHECK(nine_calls==((allocator_kind&10)!=10)&&thirteen_calls==((allocator_kind&10)==10));
    for(unsigned n=0;n<48;++n)CHECK(original_root_key[n]=='0');
    CHECK(!memcmp(original_root_key+48,"",1)&&memcmp(root_key,original_root_key,48));
    CHECK(sdk_request_metadata.magic == MAPLESU_METADATA_MAGIC && sdk_request_metadata.version == 2);
    CHECK(sdk_request_metadata.length == 48 && !sdk_request_metadata.layout_length && !sdk_request_metadata.layout_version);
    CHECK(!sdk_request_metadata.layout_displacement);
    CHECK((uptr)&sdk_request_metadata + (u64)sdk_request_metadata.kernel_base_displacement == kernel_base);
    CHECK((uptr)&sdk_request_metadata + (u64)sdk_request_metadata.legacy_metadata_displacement == arena + MSU_BS_PAGE_BYTES);
    int published = MSU_BS_ENVIRONMENT && environment && audit;
    CHECK(sdk_request_metadata.capabilities == (MAPLESU_REQUEST_CAPABILITIES | (published ? MAPLESU_CAP_APPEND_AUDIT : 0)));
    unsigned length = sdk_request_metadata.capabilities & MAPLESU_CAP_APPEND_AUDIT ?
        MAPLESU_LEGACY_METADATA_AUDIT_BYTES : MAPLESU_LEGACY_METADATA_V2_BYTES;
    CHECK(length == (published ? 58u : 54u));
    CHECK(msu_sdk_le(storage + MSU_BS_PAGE_BYTES + 54,4) == (MSU_BS_ENVIRONMENT && environment ? audit : 0));
#ifdef MSU_BOOT_SDK_NATIVE
    CHECK(storage[MSU_BS_PAGE_BYTES+41] == (MSU_BS_ENVIRONMENT && environment ? 24 : 0));
#endif
    const unsigned char *append = storage + MSU_BS_PAGE_BYTES;
    const int populated = MSU_BS_ENVIRONMENT && environment;
    CHECK(msu_sdk_le(append+36,4) == (populated ? sdk_execve_offset : 0));
    CHECK((msu_sdk_le(append+42,4) != 0) == populated);
    CHECK(msu_sdk_le(append+48,4) == (populated ? sdk_filldir_offset : 0));
    CHECK(msu_sdk_le(append+52,2) == (populated ? sdk_seccomp_offset : 0));
    CHECK(protection_calls == (MSU_BS_ENVIRONMENT ? 4u : 3u));
    maplesu_metadata_v2 saved = sdk_request_metadata;
    CHECK(bootstrap() == 0 && allocations == 1 && hooks == 1 && !memcmp(&saved,&sdk_request_metadata,sizeof saved));
}
int main(int argc,char **argv) {
    allocator_kind=argc>1?(unsigned)strtoul(argv[1],0,0):0;
    CHECK(allocator_kind<16&&allocate_segmented_range==fixture_allocate_segmented_range);
    /* Keep both compile-time environment variants warning-clean. */
    (void)msu_sdk_lookup_end;(void)msu_sdk_lookup_target;(void)msu_sdk_avc_end;
    (void)msu_sdk_avc_branch;(void)msu_sdk_avc_result;(void)sdk_execve_offset;
    (void)sdk_filldir_offset;(void)sdk_seccomp_offset;
    (void)random_bytes;(void)sdk_lookup;
    cold(0,0x123456,0); cold(1,0x123456,0); cold(1,0,0); cold(1,0x123456,-5);
#if defined(MSU_BOOT_SDK_NATIVE) && MSU_BS_ENVIRONMENT
    filename_error=-34; cold(1,0x123456,-34); filename_error=0;
#endif
    printf("{\"checks\":%u,\"failures\":%u,\"compiled_environment\":%u}\n",checks,failures,MSU_BS_ENVIRONMENT);
    return failures ? 1 : 0;
}
