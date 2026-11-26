// Complete production getters are included unchanged; BTF/kernel/VM boundaries are seams.
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <unordered_map>
#include <vector>
#include <sys/mman.h>
#include "module_err_def.h"
#include "maplesu_kernel_layout_private.h"

namespace fixture {
struct State {
    bool available = true, second_available = true, old_kernel = false, cache_hit = false;
    bool auxv = false, mapping_fail = false, mismatch_owner = false;
    unsigned btf_calls = 0, cache_calls = 0, snapshots = 0, fail_snapshot = 0;
    unsigned mmap_calls = 0, munmap_calls = 0, check_count = 0, failures = 0;
    unsigned case_count = 0, vm_at_hint = 0;
    int error_call = 0;
    std::string target, names;
    std::unordered_map<void*, size_t> mappings;
};
State state;
void check(bool ok, const char* reason) {
    ++state.check_count;
    if (!ok) { ++state.failures; std::fprintf(stderr, "FAIL %s [%s]\n", reason, state.target.c_str()); }
}
void reset(const char* target) {
    check(state.mappings.empty(), "previous case released mappings");
    const auto checks = state.check_count, failures = state.failures, cases = state.case_count;
    state = State{}; state.target = target; state.case_count = cases + 1;
    state.check_count = checks; state.failures = failures;
}
void* map(void*, size_t length, int, int, int, long) {
    ++state.mmap_calls;
    if (state.mapping_fail) return MAP_FAILED;
    void* value = std::malloc(length);
    if (!value) return MAP_FAILED;
    state.mappings.emplace(value, length);
    return value;
}
int unmap(void* value, size_t length) {
    ++state.munmap_calls;
    auto exact = state.mappings.find(value);
    if (exact != state.mappings.end()) {
        check(length == exact->second || length == 4096, "release is full range or retained first page");
        std::free(exact->first); state.mappings.erase(exact); return 0;
    }
    for (const auto& [base, size] : state.mappings) {
        if (static_cast<char*>(base) + size - length == value) return 0; // Tail split.
    }
    check(false, "unmap belongs to an existing probe"); return -1;
}
}

namespace asmjit { struct Label {}; namespace a64 {
struct GpX {}; namespace regs { inline GpX x0, x11, x12, x13, x14, x15; }
struct Assembler {
    Label newLabel() { return {}; }
    template<class... Args> void cbz(Args...) {}
    template<class... Args> void mov(Args...) {}
    template<class... Args> void sub(Args...) {}
    void bind(Label) {}
};
}}
struct aarch64_asm_ctx { asmjit::a64::Assembler a; auto* assembler() { return &a; } };
inline aarch64_asm_ctx init_aarch64_asm() { return {}; }
inline std::vector<uint8_t> aarch64_asm_to_bytes(asmjit::a64::Assembler*) { return {1}; }
template<class... Args> void aarch64_asm_mov_x(Args...) {}
template<class... Args> void aarch64_asm_set_x_data_ptr(Args...) {}

namespace kernel_module {
KModErr maplesu_resolve_btf_field(const char* owner, const char* name, uint32_t width,
                                KernelFieldDescription& field, bool& available) {
    auto& s = fixture::state; ++s.btf_calls;
    fixture::check(std::strcmp(owner, "mm_struct") == 0, "actual owner is mm_struct");
    const unsigned expected = std::strcmp(name, "map_count") == 0 ? 4 :
        (std::strcmp(name, "mm_mt") == 0 ? 0 : 8);
    fixture::check(width == expected, "field-specific width reaches shared resolver");
    s.names += std::string(name) + ';';
    available = s.btf_calls == 2 ? s.second_available : s.available;
    field = {}; field.offset_bytes = s.btf_calls == 1 ? 0 : 136;
    field.owner_size_bytes = s.mismatch_owner && s.btf_calls == 2 ? 1024 : 2048;
    field.width_bytes = width;
    if (s.error_call == static_cast<int>(s.btf_calls)) return KModErr::ERR_MODULE_READ_FILE;
    return available ? KModErr::OK : KModErr::ERR_MODULE_SYMBOL_NOT_EXIST;
}
KModErr get_offset_cache(const char*, uint32_t& out) {
    ++fixture::state.cache_calls;
    if (fixture::state.cache_hit) { out = 72; return KModErr::OK; }
    return KModErr::ERR_MODULE_OFFSET_CACHE_NOT_FOUND;
}
KModErr put_offset_cache(const char*, uint32_t) { return KModErr::OK; }
bool is_kernel_version_less(const char*) { return fixture::state.old_kernel; }
uint32_t get_page_size() { return 4096; }
static uint64_t get_first_vma_start() { return 0x100000; }
static uint64_t get_statm_total_pages() { return 100; }
static int count_maps_lines() { fixture::state.vm_at_hint = fixture::state.mappings.size(); return 105; }
static void force_rss_sync() {}
static KModErr snapshot_current_mm_struct(char* bytes, int size) {
    auto& s = fixture::state; ++s.snapshots;
    if (s.snapshots == s.fail_snapshot) return KModErr::ERR_MODULE_READ_FILE;
    std::memset(bytes, 0, size);
    if (s.target == "map_count") {
        const int32_t value = s.snapshots == 1 ? 100 : 105;
        std::memcpy(bytes + 20, &value, sizeof(value));
    } else if (s.target == "total_vm") {
        const uint64_t value = s.snapshots == 2 ? 1100 : 100;
        std::memcpy(bytes + 24, &value, sizeof(value));
    } else if (s.target == "rss_stat") {
        const uint64_t value = s.snapshots == 3 ? 50 : 0;
        std::memcpy(bytes + 16, &value, sizeof(value));
    }
    return KModErr::OK;
}
template<class T> KModErr safe_read_kaddr_t(uint64_t, bool& ok, T&) { ok = false; return KModErr::OK; }
KModErr get_current_pgd_phys(uint64_t& value) { value = 1; return KModErr::OK; }
static KModErr find_pgd_offset_via_at_instruction(uint64_t, uint32_t& out) { out = 88; return KModErr::OK; }
static std::vector<uint64_t> get_auxv_signature() { return fixture::state.auxv ? std::vector<uint64_t>{0, 0} : std::vector<uint64_t>{}; }
template<class... Args> void arm64_module_asm_func_start(Args...) {}
template<class... Args> void arm64_module_asm_func_end(Args...) {}
namespace export_symbol {
template<class... Args> void get_current(Args...) {}
template<class... Args> KModErr get_current_checked(Args... args) { get_current(args...); return KModErr::OK; }
template<class Reg> void get_task_mm(asmjit::a64::Assembler*, KModErr& error, Reg) { error = KModErr::OK; }
template<class Reg> void mmput(asmjit::a64::Assembler*, KModErr& error, Reg) { error = KModErr::OK; }
}
namespace string_ops { template<class... Args> void kmemmem(Args...) {} }
KModErr execute_kernel_asm_func(const std::vector<uint8_t>&, uint64_t& out) { out = 256; return KModErr::OK; }
}

#define mmap fixture::map
#define munmap fixture::unmap
#include "extracted.cpp"
#undef mmap
#undef munmap

int main() {
    using namespace kernel_module;
    using Getter = KModErr(*)(uint32_t&);
    struct Single { const char* name; Getter getter; };
    const Single singles[] = {{"mm_mt", get_mm_struct_mm_mt_offset}, {"mmap", get_mm_struct_mmap_offset},
        {"pgd", get_mm_struct_pgd_offset}, {"map_count", get_mm_struct_map_count_offset},
        {"total_vm", get_mm_struct_total_vm_offset}};
    for (const auto& item : singles) {
        fixture::reset(item.name); uint32_t out = 777;
        fixture::check(item.getter(out) == KModErr::OK && out == 0, "BTF zero offset is valid");
        fixture::check(fixture::state.btf_calls == 1 && fixture::state.cache_calls == 0 &&
            fixture::state.mmap_calls == 0, "BTF success bypasses cache/version/probe");
        fixture::reset(item.name); out = 777; fixture::state.error_call = 1;
        fixture::check(item.getter(out) == KModErr::ERR_MODULE_READ_FILE && out == 777,
            "present BTF error preserves output and exact error");
        fixture::check(fixture::state.cache_calls == 0 && fixture::state.mmap_calls == 0,
            "present BTF failure never probes");
        fixture::reset(item.name); out = 777;
        fixture::state.available = false; fixture::state.cache_hit = true;
        fixture::state.old_kernel = std::string(item.name) == "mmap";
        fixture::check(item.getter(out) == KModErr::OK && out == 72,
            "unavailable BTF retains old cache fallback");
    }
    using Pair = KModErr(*)(uint32_t&, uint32_t&);
    const Pair pairs[] = {get_mm_struct_arg_offset, get_mm_struct_env_offset};
    for (unsigned n = 0; n < 2; ++n) {
        const char* target = n == 0 ? "arg" : "env";
        fixture::reset(target); uint32_t first = 777, second = 888;
        fixture::check(pairs[n](first, second) == KModErr::OK && first == 0 && second == 136,
            "BTF pair independently resolves nonadjacent offsets including zero");
        fixture::check(fixture::state.names == (n == 0 ? "arg_start;arg_end;" : "env_start;env_end;"),
            "public pair routes the two correct field names");
        for (int error_call : {1, 2}) {
            fixture::reset(target); first = 777; second = 888; fixture::state.error_call = error_call;
            fixture::check(pairs[n](first, second) == KModErr::ERR_MODULE_READ_FILE &&
                first == 777 && second == 888, "pair errors publish neither output");
        }
        fixture::reset(target); first = 777; second = 888; fixture::state.mismatch_owner = true;
        fixture::check(pairs[n](first, second) == KModErr::ERR_MODULE_OFFSET_NOT_FOUND &&
            first == 777 && second == 888, "mismatched pair owners publish neither output");
        fixture::reset(target); first = 777; second = 888; fixture::state.second_available = false;
        fixture::check(pairs[n](first, second) == KModErr::ERR_MODULE_SYMBOL_NOT_EXIST &&
            first == 777 && second == 888, "second-source disappearance preserves outputs");
    }
    for (const char* target : {"mm_mt", "mmap", "map_count", "total_vm", "rss_stat"}) {
        for (bool fail : {false, true}) {
            fixture::reset(target); auto& s = fixture::state;
            s.available = false; s.old_kernel = s.target == "mmap"; s.fail_snapshot = fail ? 2 : 0;
            uint32_t out = 777;
            KModErr error = KModErr::ERR_MODULE_PARAM;
            if (s.target == "rss_stat") error = probe_rss_stat_window(400, out);
            else for (const auto& item : singles) if (s.target == item.name) error = item.getter(out);
            fixture::check(s.mmap_calls > 0 && s.mappings.empty(), "all probe mappings released");
            if (fail) fixture::check(error == KModErr::ERR_MODULE_READ_FILE,
                "snapshot failure preserves original error after cleanup");
            else if (s.target == "map_count") {
                fixture::check(error == KModErr::OK && out == 20 && s.vm_at_hint == 5,
                    "map-count sampling keeps mappings through hint then releases");
            } else if (s.target == "total_vm" || s.target == "rss_stat")
                fixture::check(error == KModErr::OK && out == (s.target == "total_vm" ? 24 : 8),
                    "probe result remains based on original samples");
            else fixture::check(error == KModErr::ERR_MODULE_OFFSET_NOT_FOUND,
                    "no-match probe releases resources and keeps legacy error");
        }
    }
    fixture::reset("rss_stat"); fixture::state.fail_snapshot = 3; uint32_t out = 777;
    fixture::check(probe_rss_stat_window(400, out) == KModErr::ERR_MODULE_READ_FILE &&
        fixture::state.mappings.empty(), "post-touch snapshot error releases mapping");
    std::printf("mm-layout-batch: %u scenarios, %u checks, %u failures\n",
        fixture::state.case_count, fixture::state.check_count, fixture::state.failures);
    return fixture::state.failures ? 1 : 0;
}
