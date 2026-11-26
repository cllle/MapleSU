#pragma once
// Capture the actual patch_current TU's emission and execution boundaries only.
#include <cstdint>
#include <vector>
#include "module_err_def.h"
#include "kernel_offsets/cred.h"

namespace current_test {
inline unsigned builds = 0;
inline uint64_t cap_offset = UINT64_MAX;
inline std::vector<uint64_t> stores;
}
namespace asmjit {
struct Imm { uint64_t value; template<class T> explicit Imm(T v) : value(v) {} };
namespace a64 {
struct Reg { int id; };
namespace regs {
inline constexpr Reg x12{12}, x13{13}, x14{14}, x15{15}, xzr{31}, wzr{31}, sp{31};
}
using namespace regs;
struct Mem { Mem post(int) const { return *this; } };
inline Mem ptr(Reg, int64_t = 0) { return {}; }
struct Assembler {
    uint64_t values[32]{};
    void str(Reg value, Mem) { current_test::stores.push_back(values[value.id]); }
    template<class... T> void ldr(T...) {}
    void add(Reg dst, Reg base, Reg delta) { if (dst.id == 13 && base.id == 13 && delta.id == 14) current_test::cap_offset = values[delta.id]; }
    template<class... T> void add(T...) {}
    template<class... T> void mov(T...) {}
    template<class... T> void ldaxr(T...) {}
    template<class... T> void bic(T...) {}
    template<class... T> void stlxr(T...) {}
    template<class... T> void mrs(T...) {}
    template<class... T> void and_(T...) {}
};
}
}
struct aarch64_asm_ctx {
    asmjit::a64::Assembler value;
    asmjit::a64::Assembler* assembler() { return &value; }
};
inline aarch64_asm_ctx init_aarch64_asm() { ++current_test::builds; return {}; }
inline std::vector<uint8_t> aarch64_asm_to_bytes(asmjit::a64::Assembler*) { return {0}; }
inline void aarch64_asm_mov_x(asmjit::a64::Assembler* a, asmjit::a64::Reg reg, uint64_t value) {
    a->values[reg.id] = value;
}
namespace kernel_module {
inline void arm64_module_asm_func_start(asmjit::a64::Assembler*) {}
inline void arm64_module_asm_func_end(asmjit::a64::Assembler*) {}
inline bool is_CONFIG_THREAD_INFO_IN_TASK() { return true; }
inline bool is_CURRENT_FROM_SP_EL0_THREAD_INFO() { return false; }
KModErr execute_kernel_asm_func(const std::vector<uint8_t>&, uint64_t&);
namespace export_symbol {
inline void get_current(asmjit::a64::Assembler*, asmjit::a64::Reg) {}
inline KModErr get_current_checked(asmjit::a64::Assembler* a, asmjit::a64::Reg r) { get_current(a, r); return KModErr::OK; }
inline KModErr get_current_thread_info_checked(asmjit::a64::Assembler*, asmjit::a64::Reg) { return KModErr::OK; }
inline void emit_huawei_kti_add_ce72c7e634f27712ee84f3dd5e8e0ec9(
    asmjit::a64::Assembler*, asmjit::a64::Reg) {}
}
}
