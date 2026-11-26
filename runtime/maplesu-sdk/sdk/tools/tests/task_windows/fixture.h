#pragma once
// Production function bodies are extracted unchanged; replace external boundaries only.
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>
#include "module_err_def.h"

namespace fixture {
inline constexpr uint64_t init_task = 0xfff0000011110000ULL;
inline constexpr uint64_t current_task = 0xfff0000022220000ULL;
inline constexpr uint64_t stack = 0xfff0000033334444ULL;
inline constexpr uint64_t mm = 0xfff0000055556666ULL;
struct State {
    std::string target, mode, trace;
    std::vector<uint8_t> image;
    unsigned cache_get = 0, cache_put = 0, reads = 0, symbols = 0;
    unsigned comm = 0, execute = 0, current = 0, mm_get = 0, mm_put = 0;
    uint32_t saved_offset = 0;
    bool contract = true;
};
inline State state;
inline void event(const char* value) { state.trace += std::string(value) + ";"; }
inline bool key_ok(const char* key) { return std::string(key) == "task_struct_" + state.target; }
}

namespace asmjit::a64 {
struct GpX { int id = 0; };
struct Assembler {};
inline constexpr GpX x0{0}, x10{10};
}

namespace kernel_module {
inline KModErr get_offset_cache(const char* key, uint32_t& offset) {
    auto& s = fixture::state;
    ++s.cache_get; fixture::event("cache-get"); s.contract &= fixture::key_ok(key);
    if (s.mode == "cache-zero" || s.mode == "cache-hit") {
        offset = s.mode == "cache-zero" ? 0 : 144;
        return KModErr::OK;
    }
    return s.mode == "cache-read-error" ? KModErr::ERR_MODULE_OFFSET_CACHE_READ
        : KModErr::ERR_MODULE_OFFSET_CACHE_NOT_FOUND;
}
inline KModErr put_offset_cache(const char* key, uint32_t offset) {
    auto& s = fixture::state;
    ++s.cache_put; fixture::event("cache-put"); s.contract &= fixture::key_ok(key);
    s.saved_offset = offset;
    return s.mode == "cache-write-error" ? KModErr::ERR_MODULE_OFFSET_CACHE_WRITE : KModErr::OK;
}
inline KModErr get_task_struct_comm_offset(uint32_t& offset) {
    auto& s = fixture::state;
    ++s.comm; fixture::event("comm");
    if (s.mode == "comm-error") return KModErr::ERR_MODULE_FUNC_NOT_STANDARD;
    offset = static_cast<uint32_t>(s.image.size());
    return KModErr::OK;
}
inline bool is_kernel_version_less(const char* version) {
    fixture::state.contract &= std::string(version) == "4.7.0";
    return fixture::state.mode == "legacy" || fixture::state.mode == "legacy-symbol-error";
}
inline KModErr kallsyms_lookup_name(const char* name, uint64_t& address) {
    auto& s = fixture::state;
    ++s.symbols; fixture::event(name);
    if ((s.mode == "task-symbol-error" && std::string(name) == "init_task") ||
        (s.mode == "stack-symbol-error" && std::string(name) == "init_stack") ||
        (s.mode == "legacy-symbol-error" && std::string(name) == "init_thread_info"))
        return KModErr::ERR_MODULE_SYMBOL_NOT_EXIST;
    if (std::string(name) == "init_task") address = fixture::init_task;
    else if (std::string(name) == "init_stack" || std::string(name) == "init_thread_info") address = fixture::stack;
    else { s.contract = false; return KModErr::ERR_MODULE_SYMBOL_PARAM; }
    return KModErr::OK;
}
inline KModErr read_kernel_mem(uint64_t address, void* output, uint32_t size) {
    auto& s = fixture::state;
    ++s.reads; fixture::event("read");
    s.contract &= address == (s.target == "stack" ? fixture::init_task : fixture::current_task)
        && size == s.image.size();
    if (s.mode == "read-error") return KModErr::ERR_MODULE_READ_FILE;
    if (!s.contract) return KModErr::ERR_MODULE_PARAM;
    if (size != 0) std::memcpy(output, s.image.data(), size);
    return KModErr::OK;
}
namespace export_symbol {
inline void get_current(asmjit::a64::Assembler*, asmjit::a64::GpX reg) {
    fixture::state.contract &= reg.id == 10; fixture::event("get-current");
}
inline KModErr get_current_checked(asmjit::a64::Assembler* a, asmjit::a64::GpX r) { get_current(a, r); return KModErr::OK; }
inline void get_task_mm(asmjit::a64::Assembler*, KModErr& error, asmjit::a64::GpX reg) {
    auto& s = fixture::state;
    ++s.mm_get; fixture::event("get-task-mm"); s.contract &= reg.id == 10;
    error = s.mode == "mm-get-error" ? KModErr::ERR_MODULE_FUNC_NOT_STANDARD : KModErr::OK;
}
inline void mmput(asmjit::a64::Assembler*, KModErr& error, asmjit::a64::GpX reg) {
    auto& s = fixture::state;
    ++s.mm_put; fixture::event("mmput"); s.contract &= reg.id == 0;
    error = s.mode == "mmput-error" ? KModErr::ERR_MODULE_PARAM : KModErr::OK;
}
}
KModErr get_task_struct_stack_offset(uint32_t&);
KModErr get_task_struct_mm_offset(uint32_t&);
}

template<class Emitter>
KModErr get_kernel_shellcode_u64_result(Emitter emitter, uint64_t& result) {
    auto& s = fixture::state;
    asmjit::a64::Assembler assembler;
    asmjit::a64::GpX out;
    const auto error = emitter(&assembler, out);
    if (is_failed(error)) return error;
    ++s.execute; fixture::event("execute"); s.contract &= out.id == 0;
    if (s.mode == "execute-error") return KModErr::ERR_MODULE_TEST_CHANNEL;
    result = fixture::mm;
    return KModErr::OK;
}
inline KModErr shellcode_current_task(uint64_t& result) {
    auto& s = fixture::state;
    ++s.current; fixture::event("current-task");
    if (s.mode == "current-error") return KModErr::ERR_MODULE_NOT_KADDR;
    result = fixture::current_task;
    return KModErr::OK;
}
