#pragma once
#include <cstdint>
#include <functional>
#include <map>
#include <string>
#include <vector>
#include "module_err_def.h"
namespace asmjit {
struct Imm { uint64_t value; template<class T> explicit Imm(T v):value(v){} };
namespace a64 {
struct GpX { int id; };
inline constexpr GpX x0{0},x10{10},sp{31};
struct Assembler {
    unsigned operation=0;
    void mrs(GpX,Imm) { operation=1; }
    void mov(GpX,GpX) { operation=2; }
};
}
}
struct aarch64_asm_ctx { asmjit::a64::Assembler a; asmjit::a64::Assembler* assembler(){return &a;} };
inline aarch64_asm_ctx init_aarch64_asm(){return {};}
inline std::vector<uint8_t> aarch64_asm_to_bytes(asmjit::a64::Assembler* a){return {static_cast<uint8_t>(a->operation)};}
namespace resolver_test {
struct Reply { KModErr error; uint64_t value; unsigned operation; };
inline std::vector<Reply> replies;
inline unsigned calls=0;
inline bool operations=true;
inline std::map<std::string,uint64_t> symbols;
inline std::map<std::string,KModErr> symbol_errors;
inline std::vector<std::string> lookups;
inline void reply(KModErr error,uint64_t value,unsigned operation){replies.push_back({error,value,operation});}
}
namespace kernel_module {
enum class CurrentTaskSource : uint32_t { sp_el0_task,sp_el0_thread_info,stack_thread_info };
struct CurrentTaskModel { CurrentTaskSource source=CurrentTaskSource::sp_el0_task; uint32_t thread_size=0; };
KModErr resolve_current_task_model(CurrentTaskModel&);
inline KModErr kallsyms_lookup_name(const char* name,uint64_t& out){
    resolver_test::lookups.emplace_back(name);
    auto error=resolver_test::symbol_errors.find(name);
    if(error!=resolver_test::symbol_errors.end())return error->second;
    auto found=resolver_test::symbols.find(name);
    if(found==resolver_test::symbols.end())return KModErr::ERR_MODULE_SYMBOL_NOT_EXIST;
    out=found->second;return KModErr::OK;
}
inline KModErr maplesu_lookup_optional_symbol(const char* name,uint64_t& out){return kallsyms_lookup_name(name,out);}
bool is_CONFIG_THREAD_INFO_IN_TASK();
bool is_CURRENT_FROM_SP_EL0_THREAD_INFO();
inline void arm64_module_asm_func_start(asmjit::a64::Assembler*){}
inline void arm64_module_asm_func_end(asmjit::a64::Assembler*,asmjit::a64::GpX){}
inline KModErr execute_kernel_asm_func(const std::vector<uint8_t>& bytes,uint64_t& result){
    ++resolver_test::calls;
    if(resolver_test::replies.empty())return KModErr::ERR_MODULE_ASM;
    auto reply=resolver_test::replies.front();resolver_test::replies.erase(resolver_test::replies.begin());
    resolver_test::operations&=bytes.size()==1 && bytes[0]==reply.operation;
    if(is_ok(reply.error))result=reply.value;
    return reply.error;
}
}
#include "actual_resolver.inl"
