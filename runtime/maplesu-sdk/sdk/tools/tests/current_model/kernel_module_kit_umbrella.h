#pragma once
#include <cstddef>
#include <cstdint>
#include <map>
#include <type_traits>
#include <vector>
#include "module_err_def.h"
#include "kernel_offsets/cred.h"
namespace kernel_module {
enum class CurrentTaskSource : uint32_t { sp_el0_task, sp_el0_thread_info, stack_thread_info };
struct CurrentTaskModel { CurrentTaskSource source = CurrentTaskSource::sp_el0_task; uint32_t thread_size = 0; };
}
namespace current_test {
inline unsigned builds = 0;
inline uint64_t cap_offset = UINT64_MAX;
inline std::vector<uint64_t> stores, store_addresses, flag_addresses, masks, reads;
inline std::map<uint64_t, uint64_t> memory;
inline kernel_module::CurrentTaskModel model;
inline KModErr model_error = KModErr::OK;
inline unsigned fail_model_call = 0;
inline uint64_t sp_value = 0xffff000000109000ULL, sp_el0 = 0xffff000000300000ULL;
inline uint64_t huawei_offset = 0;
inline unsigned model_calls = 0, asm_errors = 0;
inline void reset_emitter() {
    model = {}; model_error = KModErr::OK; model_calls = asm_errors = fail_model_call = 0;
    sp_value = 0xffff000000109000ULL; sp_el0 = 0xffff000000300000ULL;
    huawei_offset = 0; stores.clear(); store_addresses.clear(); flag_addresses.clear();
    masks.clear(); reads.clear(); memory.clear();
}
}
namespace asmjit {
inline constexpr unsigned kErrorInvalidState = 1;
// Compatibility declarations for the real void-wrapper API boundary only.
// This inert seam does not qualify CodeHolder invalidation; real_asmjit does.
enum class ResetPolicy { kHard };
struct CodeHolder { void reset(ResetPolicy) {} };
struct ErrorHandler {
    void handleError(unsigned, const char*, void*) { ++current_test::asm_errors; }
};
struct DebugUtils { static const char* errorAsString(unsigned) { return "fixture"; } };
struct Imm { uint64_t value; template<class T> explicit Imm(T v) : value(v) {} };
struct Label {};
namespace a64 {
struct Reg { int id; bool zero = false; };
using GpX = Reg;
namespace regs {
inline constexpr Reg x1{1}, x10{10}, x11{11}, x12{12}, x13{13}, x14{14}, x15{15}, xzr{31,true}, wzr{31,true}, sp{31};
}
using namespace regs;
struct SysReg { static uint32_t encode(unsigned,unsigned,unsigned,unsigned,unsigned) { return 49672; } };
struct Mem { Reg base; int64_t offset = 0; int64_t post_offset = 0; Mem post(int n) const { auto m=*this; m.post_offset=n; return m; } };
inline Mem ptr(Reg base, int64_t offset = 0) { return {base,offset}; }
struct Assembler {
    uint64_t values[32]{};
    Assembler() { values[31] = current_test::sp_value; }
    uint64_t value(Reg r) const { return r.zero ? 0 : values[r.id]; }
    uint64_t address(Mem m) const { return value(m.base)+m.offset; }
    Label newLabel() { return {}; }
    void reportError(unsigned) { ++current_test::asm_errors; }
    ErrorHandler* errorHandler() { static ErrorHandler handler; return &handler; }
    CodeHolder* code() { return nullptr; }
    void str(Reg r, Mem m) { current_test::stores.push_back(value(r)); current_test::store_addresses.push_back(address(m)); values[m.base.id]+=m.post_offset; }
    void ldr(Reg r, Mem m) { const auto p=address(m); current_test::reads.push_back(p); values[r.id]=current_test::memory[p]; }
    void add(Reg dst, Reg base, Reg delta) { if(dst.id==13 && base.id==13 && delta.id==14) current_test::cap_offset=value(delta); values[dst.id]=value(base)+value(delta); }
    void add(Reg dst, Reg base, Imm delta) { values[dst.id]=value(base)+delta.value; }
    void mov(Reg dst, Reg src) { values[dst.id]=value(src); }
    void mov(Reg dst, Imm src) { values[dst.id]=src.value; }
    void ldaxr(Reg dst, Mem m) { current_test::flag_addresses.push_back(address(m)); ldr(dst,m); }
    void bic(Reg dst, Reg left, Reg right) { values[dst.id]=value(left)&~value(right); }
    void stlxr(Reg status, Reg src, Mem m) { current_test::store_addresses.push_back(address(m)); current_test::memory[address(m)]=value(src); values[status.id]=0; }
    void mrs(Reg dst, Imm) { values[dst.id]=current_test::sp_el0; }
    void mrs(Reg dst, uint32_t n) { mrs(dst,Imm(n)); }
    void and_(Reg dst, Reg src, Imm mask) { current_test::masks.push_back(mask.value); values[dst.id]=value(src)&mask.value; }
};
}
}
struct aarch64_asm_ctx {
    asmjit::a64::Assembler value;
    asmjit::a64::Assembler* assembler() { return &value; }
};
inline aarch64_asm_ctx init_aarch64_asm() { ++current_test::builds; return {}; }
inline std::vector<uint8_t> aarch64_asm_to_bytes(asmjit::a64::Assembler*) { return current_test::asm_errors ? std::vector<uint8_t>{} : std::vector<uint8_t>{0}; }
inline void aarch64_asm_mov_x(asmjit::a64::Assembler* a, asmjit::a64::Reg r, uint64_t value) { a->values[r.id]=value; }
namespace kernel_module {
inline void arm64_module_asm_func_start(asmjit::a64::Assembler*) {}
inline void arm64_module_asm_func_end(asmjit::a64::Assembler*) {}
inline bool is_CONFIG_THREAD_INFO_IN_TASK() { return is_ok(current_test::model_error) && current_test::model.source==CurrentTaskSource::sp_el0_task; }
inline bool is_CURRENT_FROM_SP_EL0_THREAD_INFO() { return is_ok(current_test::model_error) && current_test::model.source==CurrentTaskSource::sp_el0_thread_info; }
inline KModErr resolve_current_task_model(CurrentTaskModel& out) { ++current_test::model_calls; if(is_failed(current_test::model_error) && (!current_test::fail_model_call || current_test::model_calls==current_test::fail_model_call)) return current_test::model_error; out=current_test::model; return KModErr::OK; }
KModErr execute_kernel_asm_func(const std::vector<uint8_t>&, uint64_t&);
namespace export_symbol {
inline void emit_huawei_kti_add_ce72c7e634f27712ee84f3dd5e8e0ec9(asmjit::a64::Assembler* a, asmjit::a64::GpX r) { a->values[r.id]+=current_test::huawei_offset; }
}
}
#include "actual_current.inl"
