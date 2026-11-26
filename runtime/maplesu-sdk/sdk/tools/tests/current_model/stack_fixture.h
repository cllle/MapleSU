#pragma once
#include <cstdint>
#include <functional>
#include <map>
#include <string>
#include <vector>
#include "module_err_def.h"
namespace stack_test {
inline constexpr uint64_t task=0xffff000000300000ULL,base=0xffff000000100000ULL;
inline std::map<std::string,uint64_t> symbols;
inline std::map<std::string,KModErr> errors;
inline std::vector<uint64_t> probes;
inline uint64_t boundary=0;
inline KModErr helper_error=KModErr::OK,executor_error=KModErr::OK;
inline unsigned cache_reads=0,cache_writes=0,current_reads=0,executions=0;
inline bool cache_hit=false;
inline uint32_t cached_value=0x20000;
}
namespace asmjit {
struct Imm { uint64_t value;template<class T>explicit Imm(T n):value(n){} };
namespace a64 {
struct GpX {int id;};inline constexpr GpX x0{0},x1{1},x10{10},x11{11},x12{12},x15{15},sp{31};
struct Mem {GpX base;int64_t offset;};inline Mem ptr(GpX r,int64_t n=0){return {r,n};}
struct Step {unsigned op;int dst,rn,rm,label;uint64_t value;};
struct Assembler {
    std::vector<Step> steps;std::map<int,size_t> labels;int next_label=1;int return_reg=0;
    int newLabel(){return next_label++;}void bind(int label){labels[label]=steps.size();}
    void constant(GpX r,uint64_t value){steps.push_back({1,r.id,0,0,0,value});}
    void ldr(GpX r,Mem m){steps.push_back({2,r.id,m.base.id,0,0,uint64_t(m.offset)});}
    void add(GpX r,GpX n,GpX m){steps.push_back({3,r.id,n.id,m.id,0,0});}
    void cbnz(GpX r,int label){steps.push_back({4,0,r.id,0,label,0});}
    void nofault(GpX address){steps.push_back({5,0,address.id,0,0,0});}
};
}
}
struct aarch64_asm_ctx {asmjit::a64::Assembler value;asmjit::a64::Assembler* assembler(){return &value;}};
inline aarch64_asm_ctx init_aarch64_asm(){return {};}
inline asmjit::a64::Assembler* last_assembler=nullptr;
inline std::vector<uint8_t> aarch64_asm_to_bytes(asmjit::a64::Assembler* a){last_assembler=a;return {1};}
inline void aarch64_asm_mov_x(asmjit::a64::Assembler* a,asmjit::a64::GpX r,uint64_t value){a->constant(r,value);}
struct StackBufGuard {StackBufGuard(asmjit::a64::Assembler* a,unsigned,asmjit::a64::GpX r){a->constant(r,0xffff000000200000ULL);}};
struct RegProtectGuard {template<class... T>RegProtectGuard(T...) {}};
namespace kernel_module {
inline KModErr get_offset_cache(const char*,uint32_t& value){++stack_test::cache_reads;if(stack_test::cache_hit){value=stack_test::cached_value;return KModErr::OK;}return KModErr::ERR_MODULE_OFFSET_CACHE_NOT_FOUND;}
inline KModErr put_offset_cache(const char*,uint32_t){++stack_test::cache_writes;return KModErr::OK;}
inline KModErr get_task_struct_stack_offset(uint32_t& value){value=512;return KModErr::OK;}
inline KModErr kallsyms_lookup_name(const char* name,uint64_t& value){
    auto e=stack_test::errors.find(name);if(e!=stack_test::errors.end())return e->second;
    auto s=stack_test::symbols.find(name);if(s==stack_test::symbols.end())return KModErr::ERR_MODULE_SYMBOL_NOT_EXIST;
    value=s->second;return KModErr::OK;
}
inline KModErr maplesu_lookup_optional_symbol(const char* name,uint64_t& value){return kallsyms_lookup_name(name,value);}
inline void arm64_module_asm_func_start(asmjit::a64::Assembler*){}
inline void arm64_module_asm_func_end(asmjit::a64::Assembler* a,asmjit::a64::GpX r){a->return_reg=r.id;}
inline KModErr execute_kernel_asm_func(const std::vector<uint8_t>&,uint64_t& output){
    ++stack_test::executions;if(is_failed(stack_test::executor_error))return stack_test::executor_error;
    auto* a=last_assembler;uint64_t values[32]{};
    for(size_t pc=0;pc<a->steps.size();){const auto s=a->steps[pc++];
        if(s.op==1)values[s.dst]=s.value;
        if(s.op==2)values[s.dst]=(values[s.rn]+s.value==stack_test::task+512)?stack_test::base:0;
        if(s.op==3)values[s.dst]=values[s.rn]+values[s.rm];
        if(s.op==4 && values[s.rn])pc=a->labels.at(s.label);
        if(s.op==5){const auto address=values[s.rn];stack_test::probes.push_back(address);values[0]=(stack_test::boundary && address-stack_test::base>=stack_test::boundary)?1:0;}
    }
    output=values[a->return_reg];return KModErr::OK;
}
namespace export_symbol {
inline void get_current(asmjit::a64::Assembler* a,asmjit::a64::GpX r){++stack_test::current_reads;a->constant(r,stack_test::task);}
inline void copy_from_kernel_nofault(asmjit::a64::Assembler* a,KModErr& error,asmjit::a64::GpX,asmjit::a64::GpX source,uint64_t){error=stack_test::helper_error;if(is_ok(error))a->nofault(source);}
}
}
#include "actual_stack_size.inl"
