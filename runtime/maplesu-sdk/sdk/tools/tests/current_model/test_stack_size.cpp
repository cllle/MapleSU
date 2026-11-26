#include "stack_fixture.h"
#include <cstdio>
#include <string>
unsigned checks=0,failures=0;void check(bool ok,const char* text){++checks;if(!ok){++failures;std::fprintf(stderr,"FAIL: %s\n",text);}}
int main(int argc,char** argv){
    if(argc!=2)return 2;const std::string mode=argv[1];uint32_t size=0x1234;KModErr expected=KModErr::OK;uint64_t extent=0x10000;
    if(mode=="all-readable" || mode=="first-boundary" || mode=="stale-probe-cache" || mode=="helper-error"){
        expected=KModErr::ERR_MODULE_SYMBOL_NOT_EXIST;
        stack_test::cache_hit=mode=="stale-probe-cache";
        stack_test::boundary=mode=="first-boundary"?0x8000:0;
        if(mode=="helper-error")stack_test::helper_error=KModErr::ERR_MODULE_FUNC_NOT_STANDARD;
    }else{
        extent=mode=="size16"?0x4000:mode=="size32"?0x8000:mode=="size128"?0x20000:0x10000;
        stack_test::symbols={{"__start_init_task",stack_test::base},{"__end_init_task",stack_test::base+extent},{"init_stack",stack_test::base},{"init_thread_union",stack_test::base}};
        if(mode=="bad-alias"){stack_test::symbols["init_thread_union"]+=0x100;expected=KModErr::ERR_MODULE_OFFSET_NOT_FOUND;}
        if(mode=="bad-span"){stack_test::symbols["__end_init_task"]+=0x100;expected=KModErr::ERR_MODULE_OFFSET_NOT_FOUND;}
        if(mode=="lookup-error"){stack_test::errors["__end_init_task"]=KModErr::ERR_MODULE_BOOT_SESSION;expected=KModErr::ERR_MODULE_BOOT_SESSION;}
    }
    const auto result=kernel_module::get_task_struct_stack_size(size);
    check(result==expected,"actual stack-size getter returns synthetic marker validation result, not readable mapping size");
    check(is_ok(expected)?size==extent:size==0x1234,"actual stack-size getter publishes only successful synthetic marker validation output");
    check(stack_test::current_reads==0 && stack_test::executions==0 && stack_test::cache_reads==0 && stack_test::cache_writes==0,"synthetic marker path has no current bootstrap, mapping probe or stale probe cache");
    std::printf("stack-size %s: %u checks, %u failures\n",mode.c_str(),checks,failures);return failures?1:0;
}
