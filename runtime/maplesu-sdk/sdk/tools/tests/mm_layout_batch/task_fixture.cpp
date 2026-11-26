// Complete production adapters and PID probe, with current-task/kernel-read edges controlled.
#include <array>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <thread>
#include <vector>
#include <unistd.h>
#include "module_err_def.h"
#include "maplesu_kernel_layout_private.h"

namespace fixture {
constexpr uint64_t task = 0xffff000010000000;
bool available = true, error = false, cache_hit = false;
unsigned checks = 0, failures = 0, calls = 0, caches = 0;
std::array<uint8_t, 0x2008> image{};
std::string expected_member;
void check(bool ok, const char* reason) {
    ++checks;
    if (!ok) { ++failures; std::fprintf(stderr, "FAIL %s\n", reason); }
}
}
namespace asmjit::a64 { struct GpX {}; struct Assembler {}; inline GpX x0, x10; }
namespace kernel_module {
KModErr maplesu_resolve_btf_field(const char* owner, const char* member, uint32_t width,
                                KernelFieldDescription& field, bool& available) {
    ++fixture::calls;
    fixture::check(std::strcmp(owner, "task_struct") == 0 && member == fixture::expected_member,
        "task resolver receives the actual owner and field");
    fixture::check(width == (fixture::expected_member == "pid" || fixture::expected_member == "tgid" ? 4 : 8),
        "task field width is preserved");
    available = fixture::available; field = {}; field.offset_bytes = 0;
    return fixture::error ? KModErr::ERR_MODULE_READ_FILE :
        (available ? KModErr::OK : KModErr::ERR_MODULE_SYMBOL_NOT_EXIST);
}
KModErr get_offset_cache(const char*, uint32_t& out) {
    ++fixture::caches;
    if (fixture::cache_hit) { out = 32; return KModErr::OK; }
    return KModErr::ERR_MODULE_OFFSET_CACHE_NOT_FOUND;
}
KModErr put_offset_cache(const char*, uint32_t) { return KModErr::OK; }
KModErr get_task_struct_comm_offset(uint32_t& out) { out = 128; return KModErr::OK; }
bool is_kernel_version_less(const char*) { return false; }
KModErr kallsyms_lookup_name(const char*, uint64_t& out) { out = fixture::task; return KModErr::OK; }
KModErr shellcode_current_task(uint64_t& out) { out = fixture::task; return KModErr::OK; }
KModErr read_kernel_mem(uint64_t address, void* out, uint32_t count) {
    if (address < fixture::task || count > fixture::image.size() ||
        address - fixture::task > fixture::image.size() - count) return KModErr::ERR_MODULE_READ_FILE;
    std::memcpy(out, fixture::image.data() + address - fixture::task, count); return KModErr::OK;
}
namespace export_symbol {
template<class... Args> void get_current(Args...) {}
template<class... Args> KModErr get_current_checked(Args... args) { get_current(args...); return KModErr::OK; }
template<class T> void get_task_mm(asmjit::a64::Assembler*, KModErr& error, T) { error = KModErr::OK; }
template<class T> void mmput(asmjit::a64::Assembler*, KModErr& error, T) { error = KModErr::OK; }
}
template<class Emitter> KModErr get_kernel_shellcode_u64_result(Emitter emitter, uint64_t& out) {
    asmjit::a64::Assembler a; asmjit::a64::GpX reg;
    const auto error = emitter(&a, reg); out = fixture::task; return error;
}
}
#include "extracted.cpp"

int main() {
    using namespace kernel_module;
    struct Entry { const char* name; KModErr(*getter)(uint32_t&); };
    const Entry entries[] = {{"stack", get_task_struct_stack_offset}, {"mm", get_task_struct_mm_offset},
        {"pid", get_task_struct_pid_offset}, {"tgid", get_task_struct_tgid_offset}};
    unsigned scenarios = 0;
    for (const auto& item : entries) {
        fixture::expected_member = item.name; fixture::calls = fixture::caches = 0;
        uint32_t out = 777; ++scenarios;
        fixture::check(item.getter(out) == KModErr::OK && out == 0 && fixture::calls == 1 && fixture::caches == 0,
            "BTF independent zero offset wins before legacy cache");
        fixture::error = true; out = 777; ++scenarios;
        fixture::check(item.getter(out) == KModErr::ERR_MODULE_READ_FILE && out == 777,
            "present BTF errors preserve output"); fixture::error = false;
    }
    std::thread worker([&] {
        ++scenarios; fixture::available = false; fixture::expected_member = "pid";
        const uint32_t tid = static_cast<uint32_t>(gettid()), tgid = static_cast<uint32_t>(getpid());
        fixture::check(tid != tgid, "probe runs in an actual non-leader thread");
        const uint64_t correct = uint64_t(tid) | uint64_t(tgid) << 32;
        const uint64_t reversed = uint64_t(tgid) | uint64_t(tid) << 32;
        std::memcpy(fixture::image.data() + 32, &reversed, 8);
        std::memcpy(fixture::image.data() + 64, &correct, 8);
        uint32_t out = 777;
        fixture::check(get_task_struct_pid_offset(out) == KModErr::OK && out == 64,
            "actual thread PID is low word; thread group is high word");
    });
    worker.join();
    fixture::available = true; fixture::error = true; fixture::expected_member = "pid";
    uint32_t out = 777; ++scenarios;
    fixture::check(get_task_struct_pid_offset(out) == KModErr::ERR_MODULE_READ_FILE && out == 777,
        "primed legacy static cache does not hide present BTF error");
    std::printf("task-layout-batch: %u scenarios, %u checks, %u failures\n", scenarios, fixture::checks, fixture::failures);
    return fixture::failures ? 1 : 0;
}
