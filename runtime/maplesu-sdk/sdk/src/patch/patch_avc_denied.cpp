// Maintainable reconstruction of member 73; production adoption is separate.
#include "kernel_module_kit_umbrella.h"

#include <sys/utsname.h>

#include <chrono>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <functional>
#include <memory>
#include <random>
#include <sstream>
#include <string>
#include <vector>

#include "module_base_kernel_func_executor.h"
#include "module_base_kernel_mem.h"
#include "module_err_def.h"

namespace kernel_module {
KModErr get_append_package_kallsyms_kaddr(uint64_t& out_addr);
KModErr get_append_package_run_fake_proc_path_addr(uint64_t& out_addr);
KModErr execute_kernel_asm_func_with_buf(const uint8_t* buf, uint32_t size, uint64_t& out_result);
KModErr read_kernel_mem(uint64_t kaddr, void* out, uint32_t size);
KModErr get_kernel_base_vaddr(uint64_t& out_base);
}  // namespace kernel_module

extern "C" const char* __get_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW();


// 把 “主.次.补丁” 形式的版本号切成最多三段整数，不足三段补 0。
static std::vector<int> myparse_version(const char* ver) {
    std::vector<int> parts;
    std::stringstream ss{std::string(ver)};
    std::string item;
    while (std::getline(ss, item, '.')) {
        parts.push_back(std::stoi(item));
    }
    while (parts.size() < 3) {
        parts.push_back(0);
    }
    return parts;
}

// 读取当前内核版本号（uname release 里连续的数字与点号），结果缓存。
static std::string myfind_kernel_version() {
    static std::string last_ver;
    if (!last_ver.empty()) {
        return last_ver;
    }
    struct utsname info;
    if (uname(&info) != 0) {
        return {};
    }
    const std::string release(info.release);
    std::string result;
    for (char c : release) {
        if ((c >= '0' && c <= '9') || c == '.') {
            result.push_back(c);
        } else {
            break;
        }
    }
    last_ver = result;
    return result;
}

// 当前内核版本是否低于 ver（逐段比较前三个分量）。
static bool my_is_kernel_version_less(const char* ver) {
    const std::string current = myfind_kernel_version();
    if (current.empty()) {
        return false;
    }
    const std::vector<int> current_parts = myparse_version(current.c_str());
    const std::vector<int> want_parts = myparse_version(ver);
    for (std::size_t i = 0; i < 3; ++i) {
        if (current_parts[i] < want_parts[i]) {
            return true;
        }
        if (current_parts[i] > want_parts[i]) {
            return false;
        }
    }
    return false;
}

namespace random_utils {
namespace detail {

inline std::mt19937_64& mt19937_64() {
    static thread_local std::mt19937_64 gen(std::chrono::steady_clock::now().time_since_epoch().count());
    return gen;
}

}  // namespace detail

inline std::string generate_random_str(std::size_t len) {
    if (len == 0) {
        return {};
    }
    static thread_local std::uniform_int_distribution<std::size_t> dist(0, 61);
    static const char alphabet[63] = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    std::string out;
    out.reserve(len);
    std::mt19937_64& rng = detail::mt19937_64();
    for (std::size_t i = 0; i < len; ++i) {
        out.push_back(alphabet[dist(rng)]);
    }
    return out;
}

}  // namespace random_utils

class SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8 {
public:
    SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8() noexcept : saved_(false) {
        saved_ = get_symbol_lookup_printf_silent_d8685f89f10ca8eb96df766c8babd6c3();
        if (saved_) {
            set_symbol_lookup_printf_silent_d8685f89f10ca8eb96df766c8babd6c3(false);
        }
    }

    ~SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8() {
        if (saved_) {
            set_symbol_lookup_printf_silent_d8685f89f10ca8eb96df766c8babd6c3(true);
        }
    }

    SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8(const SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8&) = delete;
    SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8& operator=(const SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8&) = delete;

private:
    bool saved_;
};

// （logger_/codeHolder_/err_/assembler_，布局与 aarch64_asm_helper.h 的 aarch64_asm_ctx
struct RaZ56zROHJzpk0T7 {
    std::unique_ptr<asmjit::StringLogger> logger_;
    std::unique_ptr<asmjit::CodeHolder> code_holder_;
    std::unique_ptr<MyAsmJitErrorHandler> error_handler_;
    std::unique_ptr<asmjit::a64::Assembler> assembler_;

    asmjit::a64::Assembler* assembler() noexcept { return assembler_.get(); }
};

static_assert(sizeof(RaZ56zROHJzpk0T7) == 32, "layout constraint violated");
static_assert(alignof(RaZ56zROHJzpk0T7) == 8, "layout constraint violated");

static inline __attribute__((always_inline)) RaZ56zROHJzpk0T7 aarch64_my_init_asm_ctx() {
    RaZ56zROHJzpk0T7 ctx;
    asmjit::Environment env(asmjit::Arch::kAArch64);
    ctx.code_holder_ = std::make_unique<asmjit::CodeHolder>();
    ctx.code_holder_->init(env);
    ctx.logger_ = std::make_unique<asmjit::StringLogger>();
    ctx.logger_->addFlags(asmjit::FormatFlags::kNone);
    ctx.code_holder_->setLogger(ctx.logger_.get());
    ctx.error_handler_.reset(new MyAsmJitErrorHandler());
    ctx.assembler_ = std::make_unique<asmjit::a64::Assembler>(ctx.code_holder_.get());
    ctx.assembler_->setErrorHandler(ctx.error_handler_.get());
    return ctx;
}

// 取当前已生成代码的字节流（空代码或空代码段时返回空向量）。
static inline __attribute__((always_inline)) std::vector<std::uint8_t> aarch64_my_asm_to_bytes(
    const asmjit::a64::Assembler* a) {
    asmjit::CodeHolder* code = a->code();
    if (!code) {
        return {};
    }
    asmjit::Section* section = code->sectionById(0);
    if (!section) {
        return {};
    }
    const asmjit::CodeBuffer& buffer = section->buffer();
    return {buffer.data(), buffer.data() + buffer.size()};
}

// Shared SDK frame; preserve the original payload and stage error semantics.
template <typename Emitter>
static inline __attribute__((always_inline)) KModErr aarch64_my_exec_emitter(
    Emitter&& emitter,
    uint64_t& output_result) {
    RaZ56zROHJzpk0T7 ctx = aarch64_my_init_asm_ctx();
    asmjit::a64::Assembler* a = ctx.assembler();

    kernel_module::arm64_module_asm_func_start(a);

    asmjit::a64::GpX result_reg = x0;
    const KModErr emit_err = emitter(a, result_reg);
    if (is_failed(emit_err)) {
        return emit_err;
    }

    kernel_module::arm64_module_asm_func_end(a, result_reg);

    const std::vector<std::uint8_t> code_bytes = aarch64_my_asm_to_bytes(a);
    return kernel_module::execute_kernel_asm_func(code_bytes, output_result);
}

// 手工生成一条 ADR：在临时汇编环境里按相对偏移摆出一条 adr，
// 取出其 4 字节编码后写回目标汇编器；偏移非法时静默失败。
static inline __attribute__((always_inline)) bool aarch64_my_asm_adr_x(
    asmjit::a64::Assembler* a, asmjit::a64::GpX x, int32_t adr_value) {
    if (!a) {
        return false;
    }
    if (adr_value % 4 != 0) {
        return false;
    }
    if (adr_value < -(1 << 20) || adr_value > ((1 << 20) - 1)) {
        return false;
    }
    RaZ56zROHJzpk0T7 ctx = aarch64_my_init_asm_ctx();
    asmjit::a64::Assembler* a2 = ctx.assembler();
    if (!a2) {
        return false;
    }
    asmjit::Label label_start = a2->newLabel();
    const int32_t size = std::abs(adr_value);
    std::vector<uint8_t> filler(static_cast<std::size_t>(size), 0x00);
    if (adr_value > 0) {
        a2->adr(x, label_start);
        if (size) {
            a2->embed(filler.data(), filler.size());
        }
        a2->bind(label_start);
        a2->nop();
    } else {
        a2->bind(label_start);
        if (size) {
            a2->embed(filler.data(), filler.size());
        }
        a2->adr(x, label_start);
    }
    std::vector<uint8_t> bytes = aarch64_my_asm_to_bytes(a2);
    if (bytes.empty()) {
        return false;
    }
    const std::size_t adr_off = (adr_value < 0) ? static_cast<std::size_t>(size) : 0;
    if (bytes.size() < adr_off + 4) {
        return false;
    }
    a->embed(bytes.data() + adr_off, 4);
    return true;
}

// 在当前代码后嵌入一块 4 字节对齐的数据，并让 x 指向它。
static inline __attribute__((always_inline)) void aarch64_my_set_x_data_ptr(
    asmjit::a64::Assembler* a, asmjit::a64::GpX x, const std::vector<uint8_t>& data) {
    auto align_up4 = [](std::size_t v) -> std::size_t { return (v + 3) & ~std::size_t(3); };
    const std::size_t n_raw = data.size();
    const std::size_t n_padded = align_up4(n_raw);
    std::vector<uint8_t> buf(n_padded, 0u);
    if (!data.empty()) {
        std::memcpy(buf.data(), data.data(), data.size());
    }
    asmjit::Label label_entry = a->newLabel();
    a->b(label_entry);
    int off_addr_start = a->offset();
    a->embed(buf.data(), buf.size());
    a->bind(label_entry);
    const int off = off_addr_start - a->offset();
    aarch64_my_asm_adr_x(a, x, off);
}

// 嵌入一个 C 字符串并让 x 指向它。
static inline __attribute__((always_inline)) void aarch64_my_set_x_cstr_ptr(
    asmjit::a64::Assembler* a, asmjit::a64::GpX x, const std::string& str) {
    std::vector<uint8_t> buf(str.size() + 1, 0u);
    if (!str.empty()) {
        std::memcpy(buf.data(), str.c_str(), str.size());
    }
    aarch64_my_set_x_data_ptr(a, x, buf);
}

// Recovered from the complete pinned member-73 IR; candidate only.
// Include after the existing aarch64_my_set_x_cstr_ptr and root-key declaration.
#include <cstdint>
#include <string>

// Leaves the parsed key word in w15, with the mask in w11. The original accepts the same
// unchecked eight characters (key[28..31], key[36..39]); do not truncate the key.
static void emit_serial_key_decode(asmjit::a64::Assembler* a) {
    using namespace asmjit;
    using namespace asmjit::a64;
    const Label scan = a->newLabel();
    const Label digit = a->newLabel();
    const Label next = a->newLabel();
    a->mov(x11, x0);
    aarch64_my_set_x_cstr_ptr(a, x12, std::string(
        __get_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW()));
    a->add(x13, x12, Imm(28));
    a->mov(w14, Imm(8));
    a->mov(w15, Imm(0));
    a->bind(scan);
    a->ldrb(w16, ptr(x13));
    a->add(x13, x13, Imm(1));
    a->sub(w17, w16, Imm(48));
    a->cmp(w16, Imm(57));
    a->b_ls(digit);
    a->orr(w16, w16, Imm(32));
    a->sub(w17, w16, Imm(87));
    a->bind(digit);
    a->lsl(w15, w15, Imm(4));
    a->orr(w15, w15, w17);
    a->subs(w14, w14, Imm(1));
    a->cmp(w14, Imm(4));
    a->b_ne(next);
    a->add(x13, x13, Imm(4));
    a->bind(next);
    a->cbnz(w14, scan);
}

static KModErr emit_boot_source_serial(asmjit::a64::Assembler* a, asmjit::a64::GpX& out,
                                      uint64_t source_symbol, uint64_t strstr_address,
                                      const std::string& prefix, uint64_t avc_target) {
    using namespace asmjit;
    using namespace asmjit::a64;
    const Label accept = a->newLabel();
    const Label reject = a->newLabel();
    const Label done = a->newLabel();
    // x4 is assigned before transforming the serial in the original.
    aarch64_asm_mov_x(a, x4, avc_target);
    a->sub(w0, w15, w11);
    a->ror(w0, w0, Imm(11));
    a->eor(w0, w0, w11);
    a->mov(w3, w0);
    a->stp(x4, x3, ptr(sp).pre(-16));
    aarch64_asm_mov_x(a, x0, source_symbol);
    a->ldr(x0, ptr(x0));
    aarch64_my_set_x_cstr_ptr(a, x1, prefix);
    aarch64_asm_mov_x(a, x11, strstr_address);
    a->stp(x29, x30, ptr(sp).pre(-16));
    a->blr(x11);
    a->ldp(x29, x30, ptr(sp).post(16));
    // A missing prefix rejects the installation instead of dereferencing null.
    const Label restore_arguments = a->newLabel();
    a->cbz(x0, restore_arguments);
    const int32_t serial_offset = static_cast<int32_t>(static_cast<uint32_t>(prefix.size()) + 4u);
    a->ldr(x0, ptr(x0, serial_offset));
    a->bind(restore_arguments);
    a->ldp(x4, x3, ptr(sp).post(16));
    a->cbz(w0, reject);
    a->sub(w12, w3, w0);
    a->cbz(w12, accept);
    a->bind(reject);
    a->mov(x0, xzr);
    a->b(done);
    a->bind(accept);
    a->mov(x0, x4);
    a->bind(done);
    out = x0;
    return KModErr::OK;
}

static KModErr emit_serial_function(asmjit::a64::Assembler* a, asmjit::a64::GpX& out,
                                  uint64_t serial_function, uint64_t avc_target) {
    using namespace asmjit;
    using namespace asmjit::a64;
    a->mov(x0, Imm(28892));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(27377));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(15353));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(6670));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(24449));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(21537));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(30283));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(28148));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(53483));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(46477));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(8165));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(59360));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(9031));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(34184));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(4993));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(13185));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(53483));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(46477));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(8165));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(59360));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    emit_serial_key_decode(a);
    const Label accept = a->newLabel();
    const Label reject = a->newLabel();
    const Label done = a->newLabel();
    aarch64_asm_mov_x(a, x4, avc_target);
    a->sub(w0, w15, w11);
    a->ror(w0, w0, Imm(11));
    a->eor(w0, w0, w11);
    a->mov(w1, w0);
    a->stp(x4, x1, ptr(sp).pre(-16));
    aarch64_asm_mov_x(a, x10, serial_function);
    a->stp(x29, x30, ptr(sp).pre(-16));
    a->blr(x10);
    a->ldp(x29, x30, ptr(sp).post(16));
    a->ldp(x4, x1, ptr(sp).post(16));
    a->cbz(w0, reject);
    a->sub(w12, w1, w0);
    a->cbz(w12, accept);
    a->bind(reject);
    a->mov(x0, xzr);
    a->b(done);
    a->bind(accept);
    a->mov(x0, x4);
    a->bind(done);
    out = x0;
    return KModErr::OK;
}

static KModErr emit_cmdline_serial(asmjit::a64::Assembler* a, asmjit::a64::GpX& out,
                                  uint64_t source_symbol, uint64_t strstr_address,
                                  const std::string& prefix, uint64_t avc_target) {
    using namespace asmjit;
    using namespace asmjit::a64;
    a->mov(x0, Imm(21726));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(44253));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(43988));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(43408));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(52790));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(16463));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(4572));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(11958));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(16183));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(28507));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(9370));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(57909));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(34022));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(63873));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(34513));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(63422));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(16183));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(28507));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(9370));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(57909));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    emit_serial_key_decode(a);
    return emit_boot_source_serial(a, out, source_symbol, strstr_address, prefix, avc_target);
}

static KModErr emit_bootconfig_serial(asmjit::a64::Assembler* a, asmjit::a64::GpX& out,
                                  uint64_t source_symbol, uint64_t strstr_address,
                                     const std::string& prefix, uint64_t avc_target) {
    using namespace asmjit;
    using namespace asmjit::a64;
    a->mov(x0, Imm(61701));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(47766));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(62531));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(29491));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(23169));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(23874));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(34158));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(28284));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(60505));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(1478));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(33308));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(11881));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(38703));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(47661));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(59131));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(62699));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(60505));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(1478));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(33308));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(11881));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    emit_serial_key_decode(a);
    return emit_boot_source_serial(a, out, source_symbol, strstr_address, prefix, avc_target);
}


namespace default_modules {
namespace module_root {

// 拷贝构造 / 析构 / emit_check_current_allow_selinux_to_x10 / patch_kernel_before_hook
// 都由 ordinal 71 `patch_base.o` 提供（本成员的 U 集实测），因此这里只声明不定义。
class PatchBase {
public:
    PatchBase(const PatchBase& other);   // _ZN15default_modules11module_root9PatchBaseC2ERKS1_
    ~PatchBase();                        // _ZN15default_modules11module_root9PatchBaseD2Ev

    KModErr emit_check_current_allow_selinux_to_x10_checked(asmjit::a64::Assembler* a);
    void emit_check_current_allow_selinux_to_x10(asmjit::a64::Assembler* a);

    KModErr patch_kernel_before_hook(uint64_t kaddr, const asmjit::a64::Assembler* a);

private:
    uint32_t field0_;
    uint32_t cred_atomic_usage_size_;
    uint32_t field2_;
    uint32_t field3_;
};

static_assert(sizeof(PatchBase) == 16, "layout constraint violated");
static_assert(alignof(PatchBase) == 4, "layout constraint violated");

class PatchAvcDenied : public PatchBase {
public:
    PatchAvcDenied(const PatchBase& patch_base, const std::size_t& avc_denied_addr);
    ~PatchAvcDenied();

    KModErr patch_avc_denied();
    KModErr disable_autorun_bootstrap_path();

private:
    std::size_t m_avc_denied_addr_;
};

static_assert(sizeof(PatchAvcDenied) == 24, "unexpected layout size");

PatchAvcDenied::PatchAvcDenied(const PatchBase& patch_base, const std::size_t& avc_denied_addr)
    : PatchBase(patch_base), m_avc_denied_addr_(avc_denied_addr) {}

PatchAvcDenied::~PatchAvcDenied() = default;

KModErr PatchAvcDenied::patch_avc_denied() {
    KModErr err = disable_autorun_bootstrap_path();
    if (is_failed(err)) {
        return err;
    }

    aarch64_asm_ctx handler_ctx = init_aarch64_asm();
    asmjit::a64::Assembler* handler_a = handler_ctx.assembler();
    if (!handler_a) {
        return KModErr::ERR_MODULE_ASM;
    }
    {
        asmjit::Label handler_end = handler_a->newLabel();
        kernel_module::arm64_before_hook_start(handler_a);
        handler_a->cbz(w0, handler_end);
        RETURN_IF_ERROR(this->emit_check_current_allow_selinux_to_x10_checked(handler_a));
        handler_a->cbz(x10, handler_end);
        handler_a->mov(w0, wzr);          // 放行：avc_denied 返回 0
        handler_a->bind(handler_end);
        kernel_module::arm64_before_hook_end(handler_a, false);
    }

    if (handler_ctx.has_error()) {
        return KModErr::ERR_MODULE_AVC_DENIED_PATCH;
    }

    {
    SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8 symbol_lookup_guard;
    (void)symbol_lookup_guard;

    //     实测）。每个阶段的完整骨架在 aarch64_my_exec_emitter 里。
    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_0 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(56400));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(44465));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(9213));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(51364));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(14004));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(42148));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(61239));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(11406));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(53844));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(2631));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(46848));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(60744));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(27669));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(22008));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(52387));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(61699));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(53844));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(2631));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(46848));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(60744));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_1 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(49541));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(36934));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(13295));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(25562));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(63579));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(42145));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(24968));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(50091));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(38233));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(9932));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(51504));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(30074));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(23811));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(51455));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(5254));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(34329));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(38233));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(9932));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(51504));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(30074));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_2 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(39268));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(12554));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(37730));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(23607));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(16082));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(12469));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(50417));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(38864));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(27439));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(50980));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(35907));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(6463));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(2093));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(31466));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(23115));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(56363));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(27439));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(50980));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(35907));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(6463));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_3 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(26689));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(20024));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(30333));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(29155));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(27852));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(47848));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(57616));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(1178));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(34733));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(32269));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(17083));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(14571));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(19010));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(41844));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(31584));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(17741));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(34733));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(32269));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(17083));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(14571));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_4 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(53782));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(61996));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(17311));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(19313));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(17297));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(49278));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(64456));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(19850));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(24377));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(59648));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(64650));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(28073));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(39400));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(42429));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(63500));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(60073));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(24377));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(59648));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(64650));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(28073));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_5 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(60741));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(4955));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(34809));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(38374));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(53196));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(42147));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(39766));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(16319));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(26605));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(27728));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(58359));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(63186));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(18870));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(48817));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(17747));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(28983));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(26605));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(27728));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(58359));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(63186));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_6 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(63742));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(25884));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(59669));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(55713));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(58863));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(16105));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(62750));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(61688));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(63391));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(38412));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(31989));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(28682));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(49590));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(49355));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(64375));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(7749));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(63391));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(38412));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(31989));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(28682));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_7 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(40559));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(47176));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(48185));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(14051));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(14674));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(55524));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(17761));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(8358));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(24423));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(52306));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(33654));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(27384));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(19852));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(17555));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(26039));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(3873));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(24423));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(52306));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(33654));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(27384));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_8 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(16319));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(30822));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(14238));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(50169));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(18494));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(47476));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(21277));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(50994));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(17425));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(19634));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(60585));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(8278));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(47958));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(60021));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(60373));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(17284));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(17425));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(19634));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(60585));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(8278));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_9 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(30826));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(15826));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(40676));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(18602));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(54798));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(53632));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(3757));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(62009));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(46280));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(45973));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(14887));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(57650));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(12807));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(55657));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(13497));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(44155));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(46280));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(45973));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(14887));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(57650));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_10 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(61706));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(61821));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(14875));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(8006));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(58030));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(51832));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(15409));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(21368));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(21309));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(62287));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(53141));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(31611));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(29240));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(153));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(47169));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(57283));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(21309));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(62287));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(53141));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(31611));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_11 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(45743));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(21040));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(39286));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(12142));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(41324));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(46021));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(2536));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(10165));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(9746));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(28999));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(21080));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(46054));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(488));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(59330));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(34087));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(64430));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(9746));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(28999));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(21080));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(46054));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_12 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(8920));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(30085));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(29164));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(33049));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(55527));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(22228));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(31399));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(36415));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(8474));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(47291));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(8369));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(53069));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(48421));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(13696));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(55808));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(56794));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(8474));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(47291));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(8369));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(53069));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_13 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(3326));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(45810));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(31204));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(62656));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(40051));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(47287));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(55793));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(25466));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(33530));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(33311));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(39971));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(13063));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(2708));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(63655));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(39116));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(61803));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(33530));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(33311));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(39971));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(13063));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_14 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(2901));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(37706));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(11852));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(39517));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(7773));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(45646));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(4620));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(30104));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(41759));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(59537));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(49844));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(21073));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(39897));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(33393));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(47733));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(31594));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(41759));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(59537));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(49844));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(21073));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_15 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(29106));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(42759));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(627));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(50134));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(9673));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(44065));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(54963));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(8277));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(47294));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(34377));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(56936));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(29230));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(37464));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(36517));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(29691));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(18995));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(47294));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(34377));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(56936));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(29230));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_16 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(36398));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(43457));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(61667));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(64469));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(43072));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(8707));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(46869));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(25955));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(52835));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(63583));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(18378));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(36460));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(531));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(64541));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(52396));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(51708));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(52835));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(63583));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(18378));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(36460));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit_17 =
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
    a->mov(x0, Imm(6725));
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(54190));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(57631));
    a->orr(x0, x0, x11);
    a->lsl(x0, x0, Imm(16));
    a->mov(x11, Imm(46169));
    a->orr(x0, x0, x11);
    a->mov(x9, Imm(56541));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(13312));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(34830));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(29272));
    a->orr(x9, x9, x11);
    a->sub(x0, x0, x9);
    a->ror(x0, x0, Imm(29));
    a->mov(x9, Imm(32212));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(8352));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(23732));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(56643));
    a->orr(x9, x9, x11);
    a->mov(x10, Imm(40468));
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(44672));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(18403));
    a->orr(x10, x10, x11);
    a->lsl(x10, x10, Imm(16));
    a->mov(x11, Imm(11301));
    a->orr(x10, x10, x11);
    a->eor(x9, x9, x10);
    a->ror(x9, x9, Imm(13));
    a->eor(x0, x0, x9);
    a->sub(x0, x0, x10);
    a->ror(x0, x0, Imm(17));
    a->mov(x9, Imm(32212));
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(8352));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(23732));
    a->orr(x9, x9, x11);
    a->lsl(x9, x9, Imm(16));
    a->mov(x11, Imm(56643));
    a->orr(x9, x9, x11);
    a->eor(x0, x0, x9);
    out = x0;
        return KModErr::OK;
    };

    auto decoded_part = [](const auto& emitter) {
        uint64_t value = 0;
        (void)aarch64_my_exec_emitter(emitter, value);
        char bytes[sizeof(value) + 1] = {};
        std::memcpy(bytes, &value, sizeof(value));
        return std::string(bytes);
    };

    std::string serial_function_name = decoded_part(emit_0);
    serial_function_name.append(decoded_part(emit_1));
    serial_function_name.append(decoded_part(emit_2));
    serial_function_name.append(decoded_part(emit_3));
    uint64_t serial_function = 0;
    (void)kernel_module::kallsyms_lookup_name(serial_function_name.c_str(), serial_function);

    std::string command_line_symbol = decoded_part(emit_4);
    command_line_symbol.append(decoded_part(emit_5));
    command_line_symbol.append(decoded_part(emit_6));
    const std::string android_prefix = decoded_part(emit_7);
    std::string command_line_prefix = android_prefix;
    command_line_prefix.append(decoded_part(emit_8));
    command_line_prefix.append(decoded_part(emit_9));
    std::string boot_config_symbol = decoded_part(emit_10);
    boot_config_symbol.append(decoded_part(emit_11));
    boot_config_symbol.append(decoded_part(emit_12));
    std::string boot_config_prefix = android_prefix;
    boot_config_prefix.append(decoded_part(emit_13));
    boot_config_prefix.append(decoded_part(emit_14));
    boot_config_prefix.append(decoded_part(emit_15));
    const std::string version_boundary = decoded_part(emit_16);
    const std::string strstr_symbol = decoded_part(emit_17);

    // Capture only resolved values, and submit the output directly to the member.
    if (serial_function != 0) {
        const uint64_t avc_target = m_avc_denied_addr_;
        (void)aarch64_my_exec_emitter(
            [serial_function, avc_target](Assembler* a, GpX& out) {
                return emit_serial_function(a, out, serial_function, avc_target);
            }, m_avc_denied_addr_);
    } else {
        const bool legacy_serial = my_is_kernel_version_less(version_boundary.c_str());
        uint64_t serial_source = 0;
        uint64_t strstr_address = 0;
        (void)kernel_module::kallsyms_lookup_name(
            legacy_serial ? command_line_symbol.c_str() : boot_config_symbol.c_str(), serial_source);
        (void)kernel_module::kallsyms_lookup_name(strstr_symbol.c_str(), strstr_address);
        if (serial_source == 0) {
            return KModErr::OK;
        }
        const uint64_t avc_target = m_avc_denied_addr_;
        const std::string& prefix = legacy_serial ? command_line_prefix : boot_config_prefix;
        if (legacy_serial) {
            (void)aarch64_my_exec_emitter(
                [serial_source, strstr_address, prefix, avc_target](Assembler* a, GpX& out) {
                    return emit_cmdline_serial(a, out, serial_source, strstr_address, prefix, avc_target);
                }, m_avc_denied_addr_);
        } else {
            (void)aarch64_my_exec_emitter(
                [serial_source, strstr_address, prefix, avc_target](Assembler* a, GpX& out) {
                    return emit_bootconfig_serial(a, out, serial_source, strstr_address, prefix, avc_target);
                }, m_avc_denied_addr_);
        }
    }

    }  // Restore symbol-lookup logging before installing the hook, as in the original.
    if (m_avc_denied_addr_ == 0) {
        return KModErr::OK;
    }
    return this->patch_kernel_before_hook(m_avc_denied_addr_, handler_a);

}

KModErr PatchAvcDenied::disable_autorun_bootstrap_path() {
    uint64_t fake_proc_path_addr = 0;
    const KModErr addr_err = kernel_module::get_append_package_run_fake_proc_path_addr(fake_proc_path_addr);
    if (is_failed(addr_err)) {
        return addr_err;
    }

    std::string fake_path = "/";
    fake_path.append(random_utils::generate_random_str(16));

    return kernel_module::write_kernel_mem(fake_proc_path_addr,
                                           fake_path.c_str(),
                                           static_cast<uint32_t>(fake_path.size()) + 1,
                                           static_cast<kernel_module::KernMemProt>(2));
}

}  // namespace module_root
}  // namespace default_modules
