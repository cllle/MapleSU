#include "../maplesu-sdk/sdk/src/kernel_request/module_base_kernel_request.cpp"
#include "../ultra/bridge/installed_helper_readback.h"
#include "../ultra/bridge/manager_owner_root_probe.h"
#include "../maplesu-sdk/sdk/include/maplesu_root_private.h"
#include "../maplesu-sdk/sdk/include/maplesu_caps_private.h"
#include <thread>
#include <cstdio>
static pid_t parent;
static thread_local int child_registrations;
extern "C" int __real___cxa_thread_atexit(void (*)(void*),void*,void*);
extern "C" int __wrap___cxa_thread_atexit(void (*f)(void*),void*p,void*d){if(parent&&getpid()!=parent){++child_registrations;return 0;}return __real___cxa_thread_atexit(f,p,d);}
extern "C" KModErr __real__ZN13kernel_module29maplesu_request_set_auth_modeENS_15RequestAuthModeE(kernel_module::RequestAuthMode);
extern "C" KModErr __wrap__ZN13kernel_module29maplesu_request_set_auth_modeENS_15RequestAuthModeE(kernel_module::RequestAuthMode mode){
 const auto result=__real__ZN13kernel_module29maplesu_request_set_auth_modeENS_15RequestAuthModeE(mode);
 if(result!=KModErr::OK)return result;
 return child_registrations?KModErr::ERR_MODULE_PARAM:KModErr::ERR_MODULE_TEST_CHANNEL;
}
extern "C" const char* __get_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW(){return "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuv";}
extern "C" void __set_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW(const char*){}
extern "C" int64_t maplesu_prepare_kernel_request()noexcept{return 0;}
// This fixture tests real request TLS preparation, not layout/publication locks.
extern "C" void maplesu_hook_fork_prepare()noexcept{}
extern "C" void maplesu_hook_fork_complete()noexcept{}
extern "C" bool maplesu_btf_try_fork_prepare()noexcept{return true;}
extern "C" void maplesu_btf_fork_complete()noexcept{}
namespace kernel_module{uint32_t get_page_size(){return 4096;} KModErr get_current_caps_snapshot(caps_info&,bool&){return KModErr::ERR_MODULE_TEST_CHANNEL;}}
namespace default_modules::module_root{KModErr set_current_root(bool){return KModErr::ERR_MODULE_TEST_CHANNEL;}RootTransitionResult current_root_transition(){return {};}}
static bool consume(void*,const uint8_t*,size_t){return true;}
int main(){parent=getpid();for(int thread=0;thread<6;++thread){int error=0;std::thread worker([&]{
 auto read=maplesu::read_installed_helper("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuv","/data/ABCDEFGHIJKLMNOP/init",0,consume,nullptr);
 printf("read checkpoint=%d mode=%lld timeout=%d complete=%d us=%llu\n",read.root_checkpoint_received,(long long)read.mode_error,read.timed_out,read.complete,(unsigned long long)read.elapsed_us);
 if(!read.root_checkpoint_received||read.mode_error!=(long long)KModErr::ERR_MODULE_TEST_CHANNEL||read.timed_out||read.complete||!read.receipt_complete)error=1;
 auto owner=maplesu::probe_manager_owner_root();
 printf("owner checkpoint=%d mode=%lld timeout=%d verified=%d us=%llu\n",owner.root_checkpoint_received,(long long)owner.mode_error,owner.timed_out,owner.root_verified,(unsigned long long)owner.elapsed_us);
 if(!owner.root_checkpoint_received||owner.mode_error!=(long long)KModErr::ERR_MODULE_TEST_CHANNEL||owner.timed_out||owner.root_verified||!owner.receipt_complete)error=2;
 });worker.join();if(error)return error;}return 0;}
