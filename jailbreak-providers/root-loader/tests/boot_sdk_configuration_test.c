/* Exercise the production native sampler, CONFIGURE and control dispatcher. */
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "boot_sdk_protocol.h"
#include "boot_sdk_allocator.h"
typedef unsigned long uptr;
typedef unsigned int u32;
typedef unsigned long long u64;
#define MSU_PH_NOCFI
#define MSU_BS_ENVIRONMENT 0
#ifndef MSU_BS_PAGE_BYTES
#define MSU_BS_PAGE_BYTES 4096u
#endif
_Static_assert(sizeof(uptr) == 8, "the kernel ABI fixture requires an LP64 host");
struct kprobe;
struct pt_regs;
int msu_boot_sdk_allocator_pre(struct kprobe *, struct pt_regs *);
static unsigned char header[64];
static struct msu_boot_sdk_layout sdk_layout, probe_layout;
static struct msu_sdk_vm sdk_vm;
static unsigned int sdk_kallsyms, sdk_config_stage, sdk_allocator_hits;
static uptr kernel_base = 0xffff800000000000UL, task, credential, native_return;
static uptr sdk_allocator_owner, sdk_allocator_args[8], native_args[8];
static unsigned int sdk_allocator_segmented;
static uptr sample_regs[34], sample_stack[5];
static u32 state, control_busy;
static int sdk_dynamic_ready, bootstrap_error, sdk_allocations, native_allocations;
static int registrations, unregistrations, releases, sample_live, probe_active, fixture_fault;
static int missing_allocator, missing_range, missing_user_sampler, missing_kallsyms, distant_kallsyms;
static int user_allocations, segmented_fault;
static int failed_read, failed_probe, entry_offset, capture_mode, null_allocation, wrapper_reads;
static u32 entry_instruction = 0xd503245fu;
static void *active_probe;
static int (*observer)(struct kprobe *, struct pt_regs *);
static void *(*allocate)(unsigned long);
struct sdk_pgprot { uptr pgprot; };
static void *(*allocate_range)(unsigned long,unsigned long,uptr,uptr,unsigned int,struct sdk_pgprot,unsigned long,int,const void *);
static void *(*allocate_segmented_range)(unsigned long,unsigned long,unsigned int,unsigned int,
    unsigned int,unsigned int,uptr,uptr,unsigned int,struct sdk_pgprot,unsigned long,int,const void *);
static void (*release)(const void *);
static char root_key[49];
static void *fixture_allocate(unsigned long);
static void *fixture_allocate_noprof(unsigned long bytes){return fixture_allocate(bytes);}
static void *fixture_allocate_user(unsigned long bytes){
    int saved=capture_mode;
    if(capture_mode==7)capture_mode=0; /* Native user allocation bypasses the vendor's small-page cache. */
    ++user_allocations;
    void *result=fixture_allocate(bytes);
    capture_mode=saved;
    return result;
}
static void zero(void *p,unsigned long n){memset(p,0,n);}
static int take(u32 *p){if(*p)return 0;*p=1;return 1;}
static int fixture_root(void){return 1;}
static int (*root_subject)(void)=fixture_root;
static long copy_in(void *to,const void *from,unsigned long n){memcpy(to,from,n);return 0;}
static long copy_out(void *to,const void *from,unsigned long n){memcpy(to,from,n);return 0;}
static uptr sdk_current_task(void){return task;}
static uptr fixture_range_kind(unsigned kind){return kernel_base+6144+(kind&1)*256+(kind&2)*2048;}
static uptr fixture_range(void){return fixture_range_kind(probe_layout.allocator_noprof | ((probe_layout.allocator_noprof&8)?2:0));}
static const char *fixture_range_name(unsigned kind){
    static const char *names[]={"__vmalloc_node_range","__vmalloc_node_range_noprof",
        "___vmalloc_node_range","___vmalloc_node_range_noprof"};
    return names[(kind | ((kind&8)?2:0))&3];
}
static uptr lookup_name(const char *name){
    if(!strcmp(name,"vmalloc"))return missing_allocator?0:(uptr)fixture_allocate;
    if(!strcmp(name,"vmalloc_noprof"))return missing_allocator?0:(uptr)fixture_allocate_noprof;
    if(!strcmp(name,"vmalloc_user")||!strcmp(name,"vmalloc_user_noprof"))
        return missing_user_sampler?0:(uptr)fixture_allocate_user;
    for(unsigned kind=0;kind<4;++kind)if(!strcmp(name,fixture_range_name(kind)))return missing_range?0:fixture_range_kind(kind);
    if(!strcmp(name,"kallsyms_lookup_name"))return missing_kallsyms?0:kernel_base+(distant_kallsyms?0x100000000UL:8000);
    return 0;
}
static long read_kernel(void *to,const void *from,unsigned long n){
    if((uptr)from==(uptr)fixture_allocate||(uptr)from==(uptr)fixture_allocate_noprof){++wrapper_reads;return -14;}
    if((uptr)from==kernel_base&&n==sizeof(header)){
        if(failed_read==1)return -14;
        memcpy(to,header,n);
    }else if((uptr)from==task+2048&&n==sizeof(uptr)){
        if(failed_read==2)return -14;
        memcpy(to,&credential,n);
    }else if((uptr)from==fixture_range()&&n==sizeof(entry_instruction)){
        if(failed_read==3)return -14;
        memcpy(to,&entry_instruction,n);
    }else if(from==sample_stack&&n==sizeof(uptr)*4){
        if(segmented_fault==1)return -14;
        memcpy(to,from,n);
    }else return -14;
    return 0;
}
static void emit_capture(void){
    memset(sample_regs,0,sizeof sample_regs);
    memcpy(sample_regs,native_args,sizeof native_args);
    if(probe_layout.allocator_noprof&8){
        sample_regs[2]=0;sample_regs[3]=1;sample_regs[4]=0;sample_regs[5]=1;
        sample_regs[6]=native_args[2];sample_regs[7]=native_args[3];
        memcpy(sample_stack,native_args+4,4*sizeof(uptr));sample_stack[4]=0xabc;
        sample_regs[31]=(uptr)sample_stack;
        if(segmented_fault>=2&&segmented_fault<=5)++sample_regs[segmented_fault];
        if(segmented_fault==6)sample_regs[31]=0;
    }
    uptr before[34];memcpy(before,sample_regs,sizeof before);
    if(!observer||observer((struct kprobe *)active_probe,(struct pt_regs *)sample_regs)||
        memcmp(before,sample_regs,sizeof before))fixture_fault=1;
}
int register_kprobe(void *p){
    ++registrations;
    if(probe_active||sdk_allocator_owner||sdk_allocator_hits)fixture_fault=1;
    const char *name=*(const char **)((unsigned char *)p+probe_layout.kp_name);
    if(strcmp(name,fixture_range_name(probe_layout.allocator_noprof)))fixture_fault=1;
    observer=(void *)*(uptr *)((unsigned char *)p+probe_layout.kp_pre);
    if(observer!=msu_boot_sdk_allocator_pre)fixture_fault=1;
    active_probe=p;
    emit_capture(); /* Registration-time activity has no sampling owner. */
    if(failed_probe){active_probe=0;observer=0;return -22;}
    *(uptr *)((unsigned char *)p+probe_layout.kp_addr)=fixture_range()+(unsigned)entry_offset;
    probe_active=1;return 0;
}
void unregister_kprobe(void *p){
    if(!probe_active||p!=active_probe||sdk_allocator_owner)fixture_fault=1;
    ++unregistrations;probe_active=0;active_probe=0;observer=0;
}
static void fixture_release(const void *p){
    if(probe_active||sdk_allocator_owner||!sample_live||(uptr)p!=native_return)fixture_fault=1;
    ++releases;sample_live=0;
}
static void *fixture_allocate(unsigned long bytes){
    ++native_allocations;
    if(!probe_active||sdk_allocator_owner!=task||bytes!=MSU_BS_PAGE_BYTES)fixture_fault=1;
    if(capture_mode==4||capture_mode==6){uptr saved=task;task+=0x1000;emit_capture();task=saved;}
    if(capture_mode==5||capture_mode==6){native_args[0]*=2;emit_capture();native_args[0]/=2;}
    if(!capture_mode||capture_mode==2||capture_mode==6)emit_capture();
    if(capture_mode==2)emit_capture();
    if(null_allocation)return 0;
    sample_live=1;return (void *)native_return;
}
static int bootstrap(void){++sdk_allocations;state=MSU_BOOT_SDK_FAILED;return bootstrap_error=-12;}
#include "configuration.inc"
struct test_case {const char *name;int error;unsigned stage,probes,native;};
static const struct test_case cases[]={
    {"valid",0,MSU_BS_CONFIG_NONE,1,1},
    {"resume",0,MSU_BS_CONFIG_NONE,1,1},
    {"tagged-cred",0,MSU_BS_CONFIG_NONE,1,1},
    {"tagged-sample",0,MSU_BS_CONFIG_NONE,1,1},
    {"noprof",0,MSU_BS_CONFIG_NONE,1,1},
    {"inner-range",0,MSU_BS_CONFIG_NONE,1,1},
    {"inner-range-noprof",0,MSU_BS_CONFIG_NONE,1,1},
    {"unreadable-wrapper",0,MSU_BS_CONFIG_NONE,1,1},
    {"native-6.12-arguments",0,MSU_BS_CONFIG_NONE,1,1},
    {"bti-entry",0,MSU_BS_CONFIG_NONE,1,1},
    {"moved-probe-fields",0,MSU_BS_CONFIG_NONE,1,1},
    {"irrelevant-capture",0,MSU_BS_CONFIG_NONE,1,1},
    {"node-sign-extended",0,MSU_BS_CONFIG_NONE,1,1},
    {"node-upper-unspecified",0,MSU_BS_CONFIG_NONE,1,1},
    {"bad-field",-22,MSU_BS_CONFIG_LAYOUT,0,0},
    {"bad-page",-22,MSU_BS_CONFIG_LAYOUT,0,0},
    {"bad-probe-layout",-22,MSU_BS_CONFIG_LAYOUT,0,0},
    {"bad-refcount",-22,MSU_BS_CONFIG_LAYOUT,0,0},
    {"bad-allocator-kind",-22,MSU_BS_CONFIG_LAYOUT,0,0},
    {"user-sample",0,MSU_BS_CONFIG_NONE,1,1},
    {"user-sample-noprof",0,MSU_BS_CONFIG_NONE,1,1},
    {"user-sample-inner",0,MSU_BS_CONFIG_NONE,1,1},
    {"vendor-vmalloc-bypass",0,MSU_BS_CONFIG_NONE,1,1},
    {"user-native-flags",0,MSU_BS_CONFIG_NONE,1,1},
    {"user-native-alignment",0,MSU_BS_CONFIG_NONE,1,1},
    {"segmented-range",0,MSU_BS_CONFIG_NONE,1,1},
    {"segmented-range-noprof",0,MSU_BS_CONFIG_NONE,1,1},
    {"segmented-user-sample",0,MSU_BS_CONFIG_NONE,1,1},
    {"segmented-stack-read",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"segmented-begin",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"segmented-count",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"segmented-init-begin",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"segmented-init-end",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"segmented-null-stack",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"segmented-inner-range",0,MSU_BS_CONFIG_NONE,1,1},
    {"segmented-inner-noprof",0,MSU_BS_CONFIG_NONE,1,1},
    {"segmented-inner-user",0,MSU_BS_CONFIG_NONE,1,1},
    {"user-zero-align",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"user-probe-no-capture",-61,MSU_BS_CONFIG_CAPTURE,1,1},
    {"missing-user-sampler",-95,MSU_BS_CONFIG_ALLOCATOR,0,0},
    {"bad-environment",-95,MSU_BS_CONFIG_ENVIRONMENT,0,0},
    {"missing-allocator",-95,MSU_BS_CONFIG_ALLOCATOR,0,0},
    {"missing-range",-95,MSU_BS_CONFIG_ALLOCATOR,0,0},
    {"bad-header",-95,MSU_BS_CONFIG_IMAGE_HEADER,0,0},
    {"zero-image",-95,MSU_BS_CONFIG_IMAGE_HEADER,0,0},
    {"oversize-image",-95,MSU_BS_CONFIG_IMAGE_HEADER,0,0},
    {"image-read",-14,MSU_BS_CONFIG_IMAGE_READ,0,0},
    {"missing-kallsyms",-95,MSU_BS_CONFIG_KALLSYMS,0,0},
    {"distant-kallsyms",-95,MSU_BS_CONFIG_KALLSYMS,0,0},
    {"credential-read",-95,MSU_BS_CONFIG_CREDENTIAL,0,0},
    {"user-cred",-95,MSU_BS_CONFIG_CREDENTIAL,0,0},
    {"tagged-user-cred",-95,MSU_BS_CONFIG_CREDENTIAL,0,0},
    {"misaligned-cred",-95,MSU_BS_CONFIG_CREDENTIAL,0,0},
    {"probe-failure",-22,MSU_BS_CONFIG_PROBE,1,0},
    {"bad-entry",-95,MSU_BS_CONFIG_PROBE_ENTRY,1,0},
    {"bad-bti",-95,MSU_BS_CONFIG_PROBE_ENTRY,1,0},
    {"bti-read",-95,MSU_BS_CONFIG_PROBE_ENTRY,1,0},
    {"allocation-null",-12,MSU_BS_CONFIG_ALLOCATE,1,1},
    {"missing-capture",-61,MSU_BS_CONFIG_CAPTURE,1,1},
    {"retry-after-capture-failure",-61,MSU_BS_CONFIG_CAPTURE,1,1},
    {"duplicate-capture",-95,MSU_BS_CONFIG_CAPTURE,1,1},
    {"wrong-task-capture",-61,MSU_BS_CONFIG_CAPTURE,1,1},
    {"wrong-size-capture",-61,MSU_BS_CONFIG_CAPTURE,1,1},
    {"zero-align",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"odd-align",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"oversize-align",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"user-start",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"reversed-range",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"zero-gfp",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"gfp-low32-zero",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"zero-prot",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"unexpected-vmflags",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"wrong-node",-95,MSU_BS_CONFIG_ARGUMENTS,1,1},
    {"busy",-16,MSU_BS_CONFIG_NONE,0,0},
};
int main(int argc,char **argv){
    if(argc!=2)return 3;
    const struct test_case *test=0;
    for(unsigned i=0;i<sizeof cases/sizeof cases[0];++i)if(!strcmp(argv[1],cases[i].name))test=&cases[i];
    if(!test)return 3;
    const char *name=test->name;
    task=kernel_base+0x4000;credential=kernel_base+0x8000;native_return=kernel_base+0xa000;
    uptr initial_args[]={MSU_BS_PAGE_BYTES,1,kernel_base+0x10000000,kernel_base+0x70000000,0xcc0,0x0068000000000703,0,0xffffffffu};
    memcpy(native_args,initial_args,sizeof native_args);release=fixture_release;
    msu_sdk_store(header+16,0x10000000,8);msu_sdk_store(header+56,0x644d5241,4);
    struct msu_boot_sdk_configuration request={0};
    request.reply.version=MSU_BOOT_SDK_VERSION;request.reply.command=MSU_BOOT_SDK_CONFIGURE;
    request.layout=(struct msu_boot_sdk_layout){.version=1,.bytes=sizeof(request.layout),.page_bytes=MSU_BS_PAGE_BYTES,
        .task_bytes=4096,.cred_bytes=256,.task_cred=2048,.cred_suid=16,.cred_securebits=40,.cred_usage=0,
        .cred_usage_size=8,.kprobe_bytes=128,.kp_addr=40,.kp_name=48,.kp_pre=64,.regs_pc=256};
    if(!strcmp(name,"tagged-cred"))credential=0xf0ff800000008000UL;
    if(!strcmp(name,"tagged-sample"))native_return=0xf0ff80000000a000UL;
    if(!strcmp(name,"noprof"))request.layout.allocator_noprof=1;
    if(!strcmp(name,"inner-range"))request.layout.allocator_noprof=2;
    if(!strcmp(name,"inner-range-noprof"))request.layout.allocator_noprof=3;
    if(!strcmp(name,"native-6.12-arguments")){native_args[2]=0xffffffc080000000UL;native_args[3]=0xfffffffebf800000UL;}
    if(!strcmp(name,"bti-entry")||!strcmp(name,"bad-bti")||!strcmp(name,"bti-read"))entry_offset=4;
    if(!strcmp(name,"moved-probe-fields")){request.layout.kp_addr=64;request.layout.kp_name=72;request.layout.kp_pre=88;}
    if(!strcmp(name,"irrelevant-capture"))capture_mode=6;
    if(!strcmp(name,"node-sign-extended"))native_args[7]=~0UL;
    if(!strcmp(name,"node-upper-unspecified"))native_args[7]=0x12345678ffffffffUL;
    if(!strcmp(name,"bad-field"))request.layout.task_cred=4096;
    if(!strcmp(name,"bad-page"))request.layout.page_bytes=MSU_BS_PAGE_BYTES==4096?16384:4096;
    if(!strcmp(name,"bad-probe-layout"))request.layout.kp_pre=48;
    if(!strcmp(name,"bad-refcount"))request.layout.cred_usage_size=16;
    if(!strcmp(name,"bad-allocator-kind"))request.layout.allocator_noprof=16;
    if(!strncmp(name,"user-sample",11)||!strncmp(name,"user-native",11)||
        !strcmp(name,"user-zero-align")||!strcmp(name,"user-probe-no-capture")||
        !strcmp(name,"missing-user-sampler"))request.layout.allocator_noprof=4;
    if(!strcmp(name,"user-sample-noprof"))request.layout.allocator_noprof=5;
    if(!strcmp(name,"user-sample-inner"))request.layout.allocator_noprof=7;
    if(!strcmp(name,"vendor-vmalloc-bypass")){request.layout.allocator_noprof=5;capture_mode=7;}
    if(!strcmp(name,"user-native-flags"))native_args[6]=8;
    if(!strcmp(name,"user-native-alignment"))native_args[1]=65536;
    if(!strncmp(name,"segmented-",10))request.layout.allocator_noprof=8;
    if(!strcmp(name,"segmented-range-noprof"))request.layout.allocator_noprof=9;
    if(!strcmp(name,"segmented-user-sample")){request.layout.allocator_noprof=13;native_args[6]=8;}
    if(!strcmp(name,"segmented-stack-read"))segmented_fault=1;
    if(!strcmp(name,"segmented-begin"))segmented_fault=2;
    if(!strcmp(name,"segmented-count"))segmented_fault=3;
    if(!strcmp(name,"segmented-init-begin"))segmented_fault=4;
    if(!strcmp(name,"segmented-init-end"))segmented_fault=5;
    if(!strcmp(name,"segmented-null-stack"))segmented_fault=6;
    if(!strcmp(name,"segmented-inner-range"))request.layout.allocator_noprof=10;
    if(!strcmp(name,"segmented-inner-noprof"))request.layout.allocator_noprof=11;
    if(!strcmp(name,"segmented-inner-user")){request.layout.allocator_noprof=15;native_args[6]=8;}
    if(!strcmp(name,"user-zero-align"))native_args[1]=0;
    if(!strcmp(name,"user-probe-no-capture"))capture_mode=1;
    if(!strcmp(name,"missing-user-sampler"))missing_user_sampler=1;
    if(!strcmp(name,"bad-environment")){request.layout.environment=1;request.layout.seccomp=32;}
    if(!strcmp(name,"missing-allocator"))missing_allocator=1;
    if(!strcmp(name,"missing-range"))missing_range=1;
    if(!strcmp(name,"bad-header"))header[56]^=1;
    if(!strcmp(name,"zero-image"))msu_sdk_store(header+16,0,8);
    if(!strcmp(name,"oversize-image"))msu_sdk_store(header+16,0x100000001ULL,8);
    if(!strcmp(name,"image-read"))failed_read=1;
    if(!strcmp(name,"missing-kallsyms"))missing_kallsyms=1;
    if(!strcmp(name,"distant-kallsyms"))distant_kallsyms=1;
    if(!strcmp(name,"credential-read"))failed_read=2;
    if(!strcmp(name,"user-cred"))credential=0x12345000;
    if(!strcmp(name,"tagged-user-cred"))credential=0xf000000012345000UL;
    if(!strcmp(name,"misaligned-cred"))credential|=1;
    if(!strcmp(name,"probe-failure"))failed_probe=1;
    if(!strcmp(name,"bad-entry"))entry_offset=8;
    if(!strcmp(name,"bad-bti"))entry_instruction=0xd503201f;
    if(!strcmp(name,"bti-read"))failed_read=3;
    if(!strcmp(name,"allocation-null"))null_allocation=1;
    if(!strcmp(name,"missing-capture")||!strcmp(name,"retry-after-capture-failure"))capture_mode=1;
    if(!strcmp(name,"duplicate-capture"))capture_mode=2;
    if(!strcmp(name,"wrong-task-capture"))capture_mode=4;
    if(!strcmp(name,"wrong-size-capture"))capture_mode=5;
    if(!strcmp(name,"zero-align"))native_args[1]=0;
    if(!strcmp(name,"odd-align"))native_args[1]=3;
    if(!strcmp(name,"oversize-align"))native_args[1]=2UL*MSU_BS_PAGE_BYTES;
    if(!strcmp(name,"user-start"))native_args[2]=0x12345000;
    if(!strcmp(name,"reversed-range"))native_args[3]=native_args[2];
    if(!strcmp(name,"zero-gfp"))native_args[4]=0;
    if(!strcmp(name,"gfp-low32-zero"))native_args[4]=0x100000000UL;
    if(!strcmp(name,"zero-prot"))native_args[5]=0;
    if(!strcmp(name,"unexpected-vmflags"))native_args[6]=1;
    if(!strcmp(name,"wrong-node"))native_args[7]=0;
    if(!strcmp(name,"busy"))state=MSU_BOOT_SDK_INITIALIZING;
    probe_layout=request.layout;
    long control_result=msu_boot_sdk_control((uptr)&request);
    fprintf(stdout,"CONFIGURE status=%d stage=%u configured=%u\n",
        request.reply.status,request.reply.padding[3],request.reply.padding[2]);
    if(control_result||request.reply.status!=test->error||
        request.reply.padding[3]!=test->stage||request.reply.padding[4]!=1||request.reply.padding[5]!=1||request.reply.padding[6]!=3||!!sdk_dynamic_ready!=!test->error||sdk_allocations||
        registrations!=(int)test->probes||native_allocations!=(int)test->native||
        unregistrations!=registrations-failed_probe||releases!=native_allocations-null_allocation||
        fixture_fault||sample_live||probe_active||active_probe||observer||sdk_allocator_owner||sdk_allocator_hits||sdk_allocator_segmented||control_busy||wrapper_reads)return 1;
    for(unsigned i=0;i<8;++i)if(sdk_allocator_args[i])return 1;
    for(unsigned i=0;i<sizeof request.reply.key;++i)if(request.reply.key[i])return 1;
    if(!test->error){
        if(!sdk_kallsyms||sdk_layout.task_cred!=2048||sdk_vm.image_bytes!=0x10000000||
            sdk_vm.flag||sdk_vm.start!=native_args[2]||sdk_vm.end!=native_args[3]||
            sdk_vm.prot!=native_args[5]||sdk_vm.prot_set!=sdk_vm.prot||
            (uptr)allocate!=lookup_name(request.layout.allocator_noprof&1?"vmalloc_noprof":"vmalloc")||
            ((request.layout.allocator_noprof&10)==10 ?
                ((uptr)allocate_segmented_range!=fixture_range_kind(request.layout.allocator_noprof)||allocate_range!=0) :
                ((uptr)allocate_range!=fixture_range_kind(request.layout.allocator_noprof)||allocate_segmented_range!=0)))return 1;
    }else if(sdk_kallsyms||sdk_layout.task_cred||sdk_vm.start||allocate||allocate_range||allocate_segmented_range)return 1;
    request.reply=(struct msu_boot_sdk_request){.version=MSU_BOOT_SDK_VERSION,.command=MSU_BOOT_SDK_QUERY};
    if(msu_boot_sdk_control((uptr)&request)||request.reply.padding[2]!=(unsigned)!test->error||
        request.reply.padding[3]!=test->stage||request.reply.padding[4]!=1||request.reply.padding[5]!=1||request.reply.padding[6]!=3||sdk_allocations||registrations!=(int)test->probes)return 1;
    if(!strcmp(name,"retry-after-capture-failure")){
        capture_mode=0;
        request.reply=(struct msu_boot_sdk_request){.version=MSU_BOOT_SDK_VERSION,.command=MSU_BOOT_SDK_CONFIGURE};
        if(msu_boot_sdk_control((uptr)&request)||request.reply.status||request.reply.padding[3]||
            !sdk_dynamic_ready||registrations!=2||unregistrations!=2||native_allocations!=2||releases!=2||
            fixture_fault||sample_live||probe_active||active_probe||observer||sdk_allocator_owner||sdk_allocator_hits||sdk_allocations)return 1;
        request.reply=(struct msu_boot_sdk_request){.version=MSU_BOOT_SDK_VERSION,.command=MSU_BOOT_SDK_BOOTSTRAP};
        if(msu_boot_sdk_control((uptr)&request)||request.reply.status!=-12||sdk_allocations!=1||
            registrations!=2||native_allocations!=2||releases!=2)return 1;
    }
    if(!test->error){
        if(!strcmp(name,"resume")){
            request.reply=(struct msu_boot_sdk_request){.version=MSU_BOOT_SDK_VERSION,.command=MSU_BOOT_SDK_CONFIGURE};
            if(msu_boot_sdk_control((uptr)&request)||request.reply.status!=-16||registrations!=1||native_allocations!=1)return 1;
        }
        request.reply=(struct msu_boot_sdk_request){.version=MSU_BOOT_SDK_VERSION,.command=MSU_BOOT_SDK_BOOTSTRAP};
        if(msu_boot_sdk_control((uptr)&request)||request.reply.status!=-12||sdk_allocations!=1||
            registrations!=1||native_allocations!=1||sample_live||probe_active)return 1;
    }
    if(user_allocations!=(int)((request.layout.allocator_noprof&4)?test->native:0))return 1;
    printf("PASS %s; stage=%u; native sample released; SDK bootstrap count=%d\n",name,test->stage,sdk_allocations);
    return 0;
}
