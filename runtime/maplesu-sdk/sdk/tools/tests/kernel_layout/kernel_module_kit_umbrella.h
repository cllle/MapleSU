#pragma once
// Test seam: keep production resolver/getter TUs intact; capture kernel I/O only.
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <map>
#include <string>
#include <vector>
#include "module_err_def.h"
#include "kernel_offsets/cred.h"
#include "kernel_offsets/task_struct.h"

namespace layout_test {
extern std::atomic<unsigned> writes;
enum Action : uint8_t { none, get_btf, get_strings, find_type, type_by_id, symbol_size };
}
namespace asmjit {
struct Imm { explicit Imm(uint32_t) {} };
namespace a64 {
struct GpX { int id; constexpr explicit GpX(int value = 0) : id(value) {} };
struct GpW { int id; constexpr explicit GpW(int value = 0) : id(value) {} };
namespace regs {
inline constexpr GpX x0{0}, x1{1}, x2{2}, x10{10}, x11{11}, x12{12}, x15{15}, sp{31};
inline constexpr GpW w2{2}, w3{3};
}
using namespace regs;
struct Mem {};
inline Mem ptr(GpX, int64_t = 0) { return {}; }
struct Assembler {
    uint8_t action = layout_test::none;
    uint64_t argument = 0;
    std::string text;
    int newLabel() { return 0; }
    template<class... Args> int bind(Args...) { return 0; }
    template<class... Args> int add(Args...) { return 0; }
    template<class... Args> int ldr(Args...) { return 0; }
    template<class... Args> int mov(Args...) { return 0; }
    template<class... Args> int mrs(Args...) { return 0; }
    template<class... Args> int cbz(Args...) { return 0; }
    template<class... Args> int b(Args...) { return 0; }
    template<class... Args> int cbnz(Args...) { return 0; }
    template<class... Args> int str(Args...) { ++layout_test::writes; return 0; }
};
}
}
struct aarch64_asm_ctx {
    asmjit::a64::Assembler value;
    asmjit::a64::Assembler* assembler() { return &value; }
};
inline aarch64_asm_ctx init_aarch64_asm() { return {}; }
inline std::vector<uint8_t> aarch64_asm_to_bytes(asmjit::a64::Assembler* a) {
    std::vector<uint8_t> bytes(9);
    bytes[0] = a->action;
    memcpy(bytes.data() + 1, &a->argument, 8);
    bytes.insert(bytes.end(), a->text.begin(), a->text.end());
    return bytes;
}
inline void aarch64_asm_mov_x(asmjit::a64::Assembler*, asmjit::a64::GpX, uint64_t) {}
inline void aarch64_asm_mov_w(asmjit::a64::Assembler*, asmjit::a64::GpW, uint32_t) {}
inline void aarch64_asm_set_x_cstr_ptr(asmjit::a64::Assembler* a, asmjit::a64::GpX, const std::string& s) { a->text = s; }
struct StackBufGuard { template<class... Args> StackBufGuard(Args...) {} };
struct RegProtectGuard { template<class... Args> RegProtectGuard(Args...) {} };
namespace kernel_module {
enum class KernMemProt { KMP_RW };
inline void arm64_module_asm_func_start(asmjit::a64::Assembler*) {}
inline void arm64_module_asm_func_end(asmjit::a64::Assembler*, asmjit::a64::GpX) {}
KModErr execute_kernel_asm_func(const std::vector<uint8_t>&, uint64_t&);
KModErr kallsyms_lookup_name(const char*, uint64_t&);
KModErr read_kernel_mem(uint64_t, void*, uint32_t);
KModErr write_kernel_mem(uint64_t, const void*, uint32_t, KernMemProt);
bool is_kernel_version_less(const char*);
KModErr btf_resolve_member_offset(const char*, const char*, uint32_t&);
KModErr btf_resolve_struct_size(const char*, uint32_t&);
namespace export_symbol {
inline void kallsyms_lookup_size_offset(asmjit::a64::Assembler* a, KModErr& error, uint64_t address, asmjit::a64::GpX, asmjit::a64::GpX) {
    a->action = layout_test::symbol_size; a->argument = address; error = KModErr::OK;
}
inline void get_current(asmjit::a64::Assembler*, asmjit::a64::GpX) {}
inline KModErr get_current_checked(asmjit::a64::Assembler* a, asmjit::a64::GpX r) { get_current(a, r); return KModErr::OK; }
template<class... Args> void copy_from_kernel_nofault(asmjit::a64::Assembler*, KModErr& error, Args...) { error = KModErr::OK; }
template<class... Args> void get_task_mm(asmjit::a64::Assembler*, KModErr& error, Args...) { error = KModErr::OK; }
template<class... Args> void mmput(asmjit::a64::Assembler*, KModErr& error, Args...) { error = KModErr::OK; }
namespace linux_above_5_10_0 {
extern KModErr function_error;
inline void bpf_get_btf_vmlinux(asmjit::a64::Assembler* a, KModErr& error) {
    a->action = layout_test::get_btf; error = function_error;
}
inline void btf_name_by_offset(asmjit::a64::Assembler* a, KModErr& error, uint64_t btf, uint32_t offset) {
    a->action = layout_test::get_strings; a->argument = btf;
    a->text.assign(reinterpret_cast<const char*>(&offset), sizeof(offset));
    error = KModErr::OK;
}
inline void btf_find_by_name_kind(asmjit::a64::Assembler* a, KModErr& error, uint64_t btf, asmjit::a64::GpX, uint32_t) {
    a->action = layout_test::find_type; a->argument = btf; error = KModErr::OK;
}
inline void btf_type_by_id(asmjit::a64::Assembler* a, KModErr& error, uint64_t, uint32_t id) {
    a->action = layout_test::type_by_id; a->argument = id; error = KModErr::OK;
}
}
}
}
