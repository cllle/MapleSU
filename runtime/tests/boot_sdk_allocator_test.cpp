// SPDX-License-Identifier: GPL-3.0-only
// Real wire producer/consumer and helper control, with typed BTF and syscall ports.
#define MAPLESU_TEST_FACT_CONSUMER
#define main boot_sdk_fact_fixture_main
#include "boot_sdk_facts_test.cpp"
#undef main
#include <array>
#include <cstdarg>
#include <climits>
#include <fcntl.h>
#include <fstream>
#include <sys/prctl.h>
#include <unistd.h>

#ifdef MAPLESU_COUPLED_PAGE
extern "C" void fixture_native_setup(int);
extern "C" int fixture_native_transact(void*,int);
#endif

namespace {
unsigned listener_capability = 3, page_bytes = 4096, configure_calls, bootstrap_calls, file_reads;
bool capture_failure;
std::vector<unsigned> layouts;
unsigned fixture_getuid() { return 0; }
unsigned fixture_geteuid() { return 0; }
int fixture_open(const char* name, int) { assert(!std::strcmp(name,"/sys/kernel/btf/vmlinux")); return 42; }
int fixture_close(int fd) { assert(fd==42); return 0; }
ssize_t fixture_read(int fd, void* data, size_t) {
    assert(fd==42); if(file_reads++) return 0;
    *static_cast<unsigned char*>(data)=1; return 1;
}
long fixture_sysconf(int name) { assert(name==_SC_PAGESIZE); return page_bytes; }
int fixture_prctl(int option, ...) {
    assert(option==4);
    va_list args; va_start(args, option);
    assert(va_arg(args,unsigned long)==MSU_BOOT_SDK_OP);
    assert(va_arg(args,unsigned long)==MSU_BOOT_SDK_TAG_LO);
    assert(va_arg(args,unsigned long)==MSU_BOOT_SDK_TAG_HI);
    auto* request=va_arg(args,msu_boot_sdk_request*); va_end(args);
#ifdef MAPLESU_COUPLED_PAGE
    if(request->command==MSU_BOOT_SDK_CONFIGURE) {
        const auto& layout=reinterpret_cast<msu_boot_sdk_configuration*>(request)->layout;
        assert(msu_boot_sdk_layout_valid(&layout,page_bytes));
        layouts.push_back(layout.allocator_noprof);++configure_calls;
    } else if(request->command==MSU_BOOT_SDK_BOOTSTRAP)++bootstrap_calls;
    const int error=fixture_native_transact(request,capture_failure && configure_calls==1);
    if(request->command==MSU_BOOT_SDK_QUERY) {
        assert(request->padding[6]==3);
        request->padding[6]=listener_capability;
    }
    return error;
#else
    request->status=0;request->state=MSU_BOOT_SDK_EMPTY;
    if(request->command==MSU_BOOT_SDK_QUERY) {
        request->padding[0]=1;request->padding[1]=1;
        request->padding[4]=1;request->padding[5]=1;request->padding[6]=listener_capability;
    } else if(request->command==MSU_BOOT_SDK_CONFIGURE) {
        const auto& layout=reinterpret_cast<msu_boot_sdk_configuration*>(request)->layout;
        assert(msu_boot_sdk_layout_valid(&layout,page_bytes));
        layouts.push_back(layout.allocator_noprof);++configure_calls;
        if(capture_failure && configure_calls==1) {
            request->status=-61;request->padding[3]=MSU_BS_CONFIG_CAPTURE;
        }
    } else { assert(request->command==MSU_BOOT_SDK_BOOTSTRAP);++bootstrap_calls;request->status=-12; }
    return 0;
#endif
}
}

#define getuid fixture_getuid
#define geteuid fixture_geteuid
#define open fixture_open
#define close fixture_close
#define read fixture_read
#define sysconf fixture_sysconf
#define prctl fixture_prctl
#include "boot_sdk_control.inc"
#undef getuid
#undef geteuid
#undef open
#undef close
#undef read
#undef sysconf
#undef prctl

namespace {
Facts only13(bool noprof) {
    auto f=fixture();erase_function(f,"__vmalloc_node_range");
    set_function(f,"___vmalloc_node_range",segmented_abi);
    if(noprof) for(auto& fn:f.layout.functions)
        if(fn.name=="vmalloc" || fn.name=="vmalloc_user" || fn.name=="___vmalloc_node_range")fn.name+="_noprof";
    return f;
}
void control_case(const std::string& kind) {
    active=fixture();listener_capability=3;capture_failure=false;
    unsigned expected_calls=1;std::string expected_error;
    if(kind=="new-only13" || kind=="new-only13-zero" || kind=="old-only13")active=only13(false);
    if(kind=="new-only13-zero") { capture_failure=true;expected_error="BOOT_SDK_CONFIG_CAPTURE:-61"; }
    if(kind=="old-only13") { listener_capability=1;expected_calls=0;expected_error="MODULE_POLICY_LAYOUT_ABI_MISSING:__vmalloc_node_range"; }
    if(kind=="new-observer" || kind=="old-observer")set_function(active,"___vmalloc_node_range",segmented_abi);
    if(kind=="old-observer" || kind=="old-inner9-zero")listener_capability=1;
    if(kind=="new-outer9")erase_function(active,"___vmalloc_node_range");
    if(kind=="new-inner9-zero" || kind=="old-inner9-zero") { capture_failure=true;expected_calls=2; }
    configure_calls=bootstrap_calls=file_reads=0;layouts.clear();
#ifdef MAPLESU_COUPLED_PAGE
    fixture_native_setup(kind.find("only13")!=std::string::npos);
#endif
    char command[]="boot-sdk-start",program[]="fixture";char* argv[]={program,command};
    std::string error;
    try { assert(boot_sdk_control(2,argv)==0); }catch(const std::runtime_error& e){error=e.what();}
    if(error!=expected_error)throw std::runtime_error(kind+": expected "+expected_error+", actual "+error);
    assert(configure_calls==expected_calls);
    assert(bootstrap_calls==(expected_error.empty()?1u:0u));
    if(kind=="new-only13" || kind=="new-only13-zero")assert(layouts[0]==14);
    if(kind=="new-observer" || kind=="old-observer")assert(layouts[0]==12);
    if(kind=="new-outer9")assert(layouts[0]==4);
    if(kind=="new-inner9-zero" || kind=="old-inner9-zero")assert((layouts==std::vector<unsigned>{6,4}));
    ++checks;
}
void only13_case() {
    for(bool noprof:{false,true})for(unsigned page:{4096u,16384u,65536u})for(bool user:{false,true}) {
        active=only13(noprof);
        const auto facts=describe_boot_sdk_layout({},true,user,3);
        const auto r=consume_boot_sdk_layout(facts,page,true,user,3);
        assert(r.allocator_noprof==(10u+unsigned(noprof)+4u*unsigned(user)) && r.environment);
        const auto resolved=resolve_boot_sdk_layout({},page,true,user,3);
        assert(!std::memcmp(&r,&resolved,sizeof r));++checks;
    }
    active=only13(false);set_function(active,"__vmalloc_node_range","bad");
    try { (void)resolve_boot_sdk_layout({},4096,true,true,3);assert(false); }
    catch(const std::runtime_error& e){assert(std::string(e.what())=="BOOT_SDK_ABI:__vmalloc_node_range");++checks;}
    active=only13(false);active.stack.types.clear();
    try { (void)resolve_boot_sdk_layout({},4096,true,true,3);assert(false); }
    catch(const std::runtime_error& e){assert(std::string(e.what())=="MODULE_POLICY_LAYOUT_MISSING:pt_regs");++checks;}
}
}
int main(int argc,char** argv) {
    assert(argc==2);
    try {
        const std::string mode=argv[1];
        if(mode=="facts")only13_case();
        else if(mode=="baseline") { char* args[]={argv[0]};assert(boot_sdk_fact_fixture_main(1,args)==0); }
        else for(unsigned page:{
#ifdef MAPLESU_COUPLED_PAGE
            MAPLESU_COUPLED_PAGE
#else
            4096u,16384u,65536u
#endif
        }){page_bytes=page;control_case(mode);}
        printf("PASS %s; checks=%u\n",argv[1],checks);return 0;
    }catch(const std::exception& e){fprintf(stderr,"%s\n",e.what());return 1;}
}
