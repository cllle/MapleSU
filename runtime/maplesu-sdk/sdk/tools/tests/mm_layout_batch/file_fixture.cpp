// Complete production file getters; kernel/BTF/filesystem probes are controlled edges.
#include <algorithm>
#include <array>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <unordered_set>
#include <vector>
#include <fcntl.h>
#include <sys/eventfd.h>
#include <sys/socket.h>
#include <unistd.h>
#include "module_err_def.h"
#include "maplesu_kernel_layout_private.h"

namespace fixture {
constexpr uint64_t file0 = 0xffff000010000000, file1 = 0xffff000020000000;
constexpr uint64_t dentry = 0xffff000030000000, inode0 = 0xffff000040000000, inode1 = 0xffff000050000000;
constexpr uint64_t mapping0 = 0xffff000060000000, mapping1 = 0xffff000070000000;
struct State {
    bool available = true, btf_error = false, cache_hit = false, bad_host = false;
    unsigned btf_calls = 0, cache_calls = 0, opens = 0, closes = 0, fail_open = 0;
    unsigned checks = 0, failures = 0, cases = 0;
    std::array<uint8_t, 640> first{}, second{};
    std::unordered_set<int> descriptors;
    std::string target, last_field;
};
State state;
void check(bool ok, const char* reason) {
    ++state.checks;
    if (!ok) { ++state.failures; std::fprintf(stderr, "FAIL %s [%s]\n", reason, state.target.c_str()); }
}
void reset(const char* name) {
    check(state.descriptors.empty(), "all prior descriptors closed");
    const auto checks = state.checks, failures = state.failures, cases = state.cases;
    state = State{}; state.target = name; state.checks = checks; state.failures = failures; state.cases = cases + 1;
}
int open(const char*, int, ...) {
    const unsigned call = ++state.opens;
    if (call == state.fail_open) return -1;
    const int fd = 10 + call - 1; state.descriptors.insert(fd); return fd;
}
int close(int fd) { ++state.closes; check(state.descriptors.erase(fd) == 1, "descriptor closes exactly once"); return 0; }
void word(std::array<uint8_t, 640>& bytes, unsigned at, uint64_t value) { std::memcpy(bytes.data() + at, &value, 8); }
void mapping_fixture(bool duplicate = false) {
    word(state.first, 16, inode0); word(state.second, 16, inode1);
    word(state.first, 80, mapping0); word(state.second, 80, mapping1);
    if (duplicate) { word(state.first, 88, mapping0); word(state.second, 88, mapping1); }
}
}
struct fd_guard {
    int value; explicit fd_guard(int fd) : value(fd) {}
    ~fd_guard() { if (value >= 0) fixture::close(value); }
    int get() const { return value; }
};
namespace asmjit::a64 { struct GpX {}; struct Assembler {}; namespace regs { inline GpX x10; } }
namespace maplesu::sdk { static std::string get_system_file_dir_path() { return "/fixture/"; } }
namespace kernel_module {
constexpr uint64_t kKernelAddrMin = 0xffff000000000000;
KModErr maplesu_resolve_btf_field(const char* owner, const char* member, uint32_t width,
                                KernelFieldDescription& field, bool& available) {
    auto& s = fixture::state; ++s.btf_calls; s.last_field = member;
    fixture::check(std::strcmp(owner, "file") == 0, "generic file owner reaches shared resolver");
    const unsigned expected = std::strcmp(member, "f_path") == 0 ? 0 :
        ((std::strcmp(member, "f_flags") == 0 || std::strcmp(member, "f_mode") == 0) ? 4 : 8);
    fixture::check(width == expected, "actual file field width reaches shared resolver");
    available = s.available; field = {}; field.offset_bytes = 0; field.owner_size_bytes = 512;
    return s.btf_error ? KModErr::ERR_MODULE_READ_FILE : (available ? KModErr::OK : KModErr::ERR_MODULE_SYMBOL_NOT_EXIST);
}
KModErr get_offset_cache(const char*, uint32_t& out) {
    ++fixture::state.cache_calls;
    if (fixture::state.cache_hit) { out = 64; return KModErr::OK; }
    return KModErr::ERR_MODULE_OFFSET_CACHE_NOT_FOUND;
}
KModErr put_offset_cache(const char*, uint32_t) { return KModErr::OK; }
static std::string get_temp_file_path(int index) { return "/fixture/" + std::to_string(index); }
static KModErr init_f_op_offset_test_file() { return KModErr::OK; }
KModErr get_current_task_file_ptr(int fd, uint64_t& out) { out = fd == 11 ? fixture::file1 : fixture::file0; return KModErr::OK; }
struct scoped_kpath { struct { uint64_t dentry = fixture::dentry; } kp; };
KModErr acquire_kpath(const std::string&, scoped_kpath&) { return KModErr::OK; }
KModErr get_dentry_d_inode_offset(uint32_t& out) { out = 0; return KModErr::OK; }
KModErr get_task_struct_cred_offset(uint32_t& out) { out = 0; return KModErr::OK; }
KModErr get_address_space_host_offset(uint32_t& out) { out = 8; return KModErr::OK; }
KModErr read_kernel_mem(uint64_t address, void* out, uint32_t count) {
    using namespace fixture;
    if (address == dentry && count == 8) { std::memcpy(out, &inode0, 8); return KModErr::OK; }
    const uint8_t* source = nullptr;
    if (address >= file0 && address - file0 <= state.first.size() - count) source = state.first.data() + address - file0;
    if (address >= file1 && address - file1 <= state.second.size() - count) source = state.second.data() + address - file1;
    if (!source) return KModErr::ERR_MODULE_READ_FILE;
    std::memcpy(out, source, count); return KModErr::OK;
}
template<class T> KModErr safe_read_kaddr_t(uint64_t address, bool& ok, T& out) {
    ok = address == fixture::mapping0 + 8 || address == fixture::mapping1 + 8;
    out = address == fixture::mapping0 + 8 || fixture::state.bad_host ? fixture::inode0 : fixture::inode1;
    return KModErr::OK;
}
namespace export_symbol {
template<class... Args> void get_current(Args...) {}
template<class... Args> KModErr get_current_checked(Args... args) { get_current(args...); return KModErr::OK; }
}
template<class Emitter> KModErr get_kernel_shellcode_u64_result(Emitter emitter, uint64_t& out) {
    asmjit::a64::Assembler assembler; asmjit::a64::GpX result;
    const auto error = emitter(&assembler, result); out = 1; return error;
}
}
#define open fixture::open
#include "extracted.cpp"
#undef open

int main() {
    using namespace kernel_module;
    struct Case { const char* member; KModErr(*getter)(uint32_t&); };
    const Case getters[] = {{"f_path", get_file_f_path_offset}, {"f_inode", get_file_f_inode_offset},
        {"f_op", get_file_f_op_offset}, {"f_flags", get_file_f_flags_offset}, {"f_mode", get_file_f_mode_offset},
        {"f_pos", get_file_f_pos_offset}, {"f_cred", get_file_f_cred_offset},
        {"private_data", get_file_private_data_offset}, {"f_mapping", get_file_f_mapping_offset}};
    for (const auto& test : getters) {
        fixture::reset(test.member); uint32_t out = 777;
        fixture::check(test.getter(out) == KModErr::OK && out == 0, "BTF zero offset is valid");
        fixture::check(fixture::state.last_field == test.member && fixture::state.btf_calls == 1 &&
            fixture::state.cache_calls == 0 && fixture::state.opens == 0, "BTF uses actual field before any old probe");
        fixture::reset(test.member); out = 777; fixture::state.btf_error = true;
        fixture::check(test.getter(out) == KModErr::ERR_MODULE_READ_FILE && out == 777,
            "present BTF error is exact and keeps output");
        fixture::check(fixture::state.cache_calls == 0 && fixture::state.opens == 0, "BTF error does not probe");
        fixture::reset(test.member); out = 777; fixture::state.available = false; fixture::state.cache_hit = true;
        fixture::check(test.getter(out) == KModErr::OK && out == 64 && fixture::state.opens == 0,
            "unavailable BTF retains the original cache fallback");
    }
    for (unsigned dentry_offset : {4u, 8u, 24u}) {
        fixture::reset("f_path"); fixture::state.available = false;
        fixture::word(fixture::state.first, dentry_offset, fixture::dentry);
        uint32_t out = 777; auto error = get_file_f_path_offset(out);
        fixture::check(dentry_offset < 8 ? error == KModErr::ERR_MODULE_OFFSET_NOT_FOUND && out == 777 :
            error == KModErr::OK && out == dentry_offset - 8, "path subtraction rejects underflow and accepts zero");
        fixture::check(fixture::state.descriptors.empty(), "path guard closes its descriptor");
    }
    for (unsigned mode = 0; mode < 5; ++mode) {
        fixture::reset("f_mapping"); auto& s = fixture::state; s.available = false;
        fixture::mapping_fixture(mode == 2); s.bad_host = mode == 1; s.fail_open = mode >= 3 ? mode - 2 : 0;
        uint32_t out = 777; const auto error = get_file_f_mapping_offset(out);
        fixture::check(mode == 0 ? error == KModErr::OK && out == 80 :
            error == (mode >= 3 ? KModErr::ERR_MODULE_OPEN_FILE : KModErr::ERR_MODULE_OFFSET_NOT_FOUND),
            "distinct mapping hosts match only their own inode; ambiguous/missing/open errors remain errors");
        fixture::check(s.descriptors.empty(), "mapping closes every successfully opened descriptor");
    }
    std::printf("file-layout-batch: %u scenarios, %u checks, %u failures\n",
        fixture::state.cases, fixture::state.checks, fixture::state.failures);
    return fixture::state.failures ? 1 : 0;
}
