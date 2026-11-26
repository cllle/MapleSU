#include "resolver_fixture.h"
#include <cstdio>
#include <string>
unsigned checks=0,failures=0;
void check(bool ok,const char* text){++checks;if(!ok){++failures;std::fprintf(stderr,"FAIL: %s\n",text);}}
int main(int argc,char** argv){
    if(argc!=2)return 2;
    using namespace kernel_module; using resolver_test::reply;
    const std::string mode=argv[1];
    constexpr uint64_t task=0xffff000000300000ULL,stack=0xffff000000100000ULL;
    if(mode=="embedded-retry"){
        reply(KModErr::ERR_MODULE_READ_FILE,0,1);
        check(!is_CONFIG_THREAD_INFO_IN_TASK(),"first classifier probe error is not true");
        // Old implementation uses another SP_EL0 read in its nested classifier.
        resolver_test::embedded_success(task,stack+0x9000);
        check(is_CONFIG_THREAD_INFO_IN_TASK(),"embedded classifier retries after transient executor failure");
    }else if(mode=="thread-info-retry"){
        reply(KModErr::ERR_MODULE_NO_BOOT,0,1);
        check(!is_CURRENT_FROM_SP_EL0_THREAD_INFO(),"first thread_info probe error is not true");
        reply(KModErr::OK,stack,1);reply(KModErr::OK,stack+0x9000,2);
        check(is_CURRENT_FROM_SP_EL0_THREAD_INFO(),"thread_info classifier retries after transient executor failure");
    }else if(mode=="exact-first-error"){
        CurrentTaskModel model{CurrentTaskSource::stack_thread_info,0x1234};
        reply(KModErr::ERR_MODULE_READ_FILE,0,1);
        check(resolver_test::resolve(model)==KModErr::ERR_MODULE_READ_FILE,"resolver preserves first executor error");
        check(model.source==CurrentTaskSource::stack_thread_info && model.thread_size==0x1234,"resolver error preserves caller output");
        check(resolver_test::calls==1,"first probe error stops before SP probe");
    }else if(mode=="exact-second-error"){
        CurrentTaskModel model{CurrentTaskSource::stack_thread_info,0x1234};
        reply(KModErr::OK,task,1);reply(KModErr::ERR_MODULE_BOOT_SESSION,0,2);
        check(resolver_test::resolve(model)==KModErr::ERR_MODULE_BOOT_SESSION,"resolver preserves SP executor error");
        check(model.source==CurrentTaskSource::stack_thread_info && model.thread_size==0x1234,"second probe error preserves output");
    }else if(mode=="unknown-stack"){
        CurrentTaskModel model{CurrentTaskSource::sp_el0_thread_info,0x1234};
        reply(KModErr::OK,0,1);
        check(resolver_test::resolve(model)==KModErr::ERR_MODULE_SYMBOL_NOT_EXIST,"missing stack extent preserves native missing-symbol error");
        check(model.source==CurrentTaskSource::sp_el0_thread_info && model.thread_size==0x1234,"unsupported model leaves output unchanged");
    }else if(mode=="embedded"){
        CurrentTaskModel model;
        resolver_test::embedded_success(task,stack+0x9000);
        check(resolver_test::resolve(model)==KModErr::OK && model.source==CurrentTaskSource::sp_el0_task && !model.thread_size,"upper-envelope raw SP_EL0 retains the inherited default-task heuristic");
    }else if(mode=="thread-info"){
        CurrentTaskModel model;
        resolver_test::thread_success(stack,stack+0x9000);
        check(resolver_test::resolve(model)==KModErr::OK && model.source==CurrentTaskSource::sp_el0_thread_info,"raw SP_EL0 matching synthetic masked base selects the inherited thread_info branch");
    }else if(mode=="invalid-sp"){
        CurrentTaskModel model;
        reply(KModErr::OK,task,1);reply(KModErr::OK,1234,2);
        check(resolver_test::resolve(model)==KModErr::ERR_MODULE_NOT_KADDR,"SP value 1234 below the upper-range floor returns its exact error; no VA-hole coverage");
    }else if(mode.rfind("stack",0)==0){
        uint64_t size=mode=="stack16"?0x4000:mode=="stack32"?0x8000:mode=="stack128"?0x20000:0x10000;
        resolver_test::symbols={{"__start_init_task",stack},{"__end_init_task",stack+size},{"init_stack",stack},{"init_thread_union",stack}};
        KModErr expected=KModErr::OK;
        if(mode=="stack-missing-end"){resolver_test::symbols.erase("__end_init_task");expected=KModErr::ERR_MODULE_SYMBOL_NOT_EXIST;}
        if(mode=="stack-bad-alias"){resolver_test::symbols["init_stack"]+=0x100;expected=KModErr::ERR_MODULE_OFFSET_NOT_FOUND;}
        if(mode=="stack-bad-span"){resolver_test::symbols["__end_init_task"]+=0x100;expected=KModErr::ERR_MODULE_OFFSET_NOT_FOUND;}
        if(mode=="stack-lookup-error"){resolver_test::symbol_errors["init_stack"]=KModErr::ERR_MODULE_ROOT_KEY;expected=KModErr::ERR_MODULE_ROOT_KEY;}
        if(mode=="stack-marker-error"){resolver_test::symbol_errors["__start_init_stack"]=KModErr::ERR_MODULE_BOOT_SESSION;expected=KModErr::ERR_MODULE_BOOT_SESSION;}
        if(mode=="stack-invalid-address"){resolver_test::symbols["__end_init_task"]=1234;expected=KModErr::ERR_MODULE_NOT_KADDR;}
        if(mode=="stack-new-markers"){
            resolver_test::symbols["__start_init_stack"]=stack;resolver_test::symbols["__end_init_stack"]=stack+size;
            resolver_test::symbols["__end_init_task"]=1234; // Old marker is not consulted after a complete new pair.
        }
        CurrentTaskModel model{CurrentTaskSource::sp_el0_thread_info,0x1234};
        reply(KModErr::OK,0,1);
        check(resolver_test::resolve(model)==expected,"stack resolver preserves exact synthetic marker validation result");
        if(is_ok(expected))check(model.source==CurrentTaskSource::stack_thread_info && model.thread_size==size,"stack model uses the injected synthetic marker span");
        else check(model.source==CurrentTaskSource::sp_el0_thread_info && model.thread_size==0x1234,"invalid synthetic extent/error leaves model output unchanged");
    }else return 2;
    check(resolver_test::operations,"actual classifier emitted expected register-read operations");
    std::printf("current-resolver %s: %u checks, %u failures\n",mode.c_str(),checks,failures);
    return failures?1:0;
}
