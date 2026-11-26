// patch base implementation.
//
#include "default_modules_module_root_patch_base.h"

#include <cstdio>
#include <cstdint>
#include <vector>

// aarch64_asm_helper.h 提供匿名命名空间的 aarch64_asm_to_bytes / MyAsmJitErrorHandler，
#include "aarch64_asm_helper.h"

namespace default_modules {
namespace module_root {

// 调试用：把 kaddr 处的 8 个字节按 " %02hhx" 打印到 stdout（字节数仅为历史
// 接口形状，默认不传）。
static void printf_kernel_mem(uint64_t kaddr, unsigned int size = 0);

// 调试用：把 kaddr 处的 8 个字节按 " %02hhx" 打印到 stdout。
static void printf_kernel_mem(uint64_t kaddr, unsigned int size) {
    (void)size;
    std::vector<uint8_t> buf(8);
    KModErr err = kernel_module::read_kernel_mem(
        kaddr, buf.data(), static_cast<uint32_t>(buf.size()));
    if (is_ok(err)) {
        for (size_t i = 0; i < buf.size(); ++i) {
            printf(" %02hhx", static_cast<int>(buf[i]));
        }
        putchar('\n');
    }
}

PatchBase::PatchBase(uint32_t cred_offset, uint32_t cred_atomic_usage_size,
                     uint32_t cred_securebits_offset, uint32_t cred_cap_effective_offset)
    : m_cred_offset(cred_offset),
      m_cred_atomic_usage_size(cred_atomic_usage_size),
      m_cred_securebits_offset(cred_securebits_offset),
      m_cred_cap_effective_offset(cred_cap_effective_offset) {}

PatchBase::PatchBase(const PatchBase& other)
    : m_cred_offset(other.m_cred_offset),
      m_cred_atomic_usage_size(other.m_cred_atomic_usage_size),
      m_cred_securebits_offset(other.m_cred_securebits_offset),
      m_cred_cap_effective_offset(other.m_cred_cap_effective_offset) {}

PatchBase::~PatchBase() {}

int PatchBase::get_cred_atomic_usage_size() {
    return m_cred_atomic_usage_size;
}

int PatchBase::get_cred_securebits_size() {
    return m_cred_cap_effective_offset - 32u - m_cred_atomic_usage_size;
}

// 发射“current 是否允许绕过 SELinux”的检查序列，结论写在 x10：
//   x10 = 0（默认，不允许）  或  x10 = 1（允许）。
// 原始对象 0071 的 Imm payload 与 post-index Mem signature 已核对。
// 成员名保留恢复接口；调用方实际传入 cred / uid / euid / cap_inheritable 偏移：
//   x11 = current;  x11 = cred;
//   if (*(u32*)(cred + euid_offset) != 0) goto deny;
//   x11 = cred + uid_offset + 32;
//   w13 = *(u32*)x11; x11 = cred + cap_inheritable_offset;
//   if (w13 != 0) goto deny;
//   x12 = cap_ability_max; x13 = caps_cnt;
//   loop: x14 = *(u64*)x11; x11 += 8;
//         if (x14 < cap_ability_max) goto deny;
//         if (--x13 != 0) goto loop;
//   x10 = 1;
// deny:
KModErr PatchBase::emit_check_current_allow_selinux_to_x10_checked(asmjit::a64::Assembler* a) {
    using namespace asmjit;
    using namespace asmjit::a64;

    // 保持同样顺序，生成的指令序列才逐条一致（否则两条 call 会落到不同位置）。
    const uint32_t atomic_usage_size = m_cred_atomic_usage_size;
    const uint32_t cap_effective_offset = m_cred_cap_effective_offset;
    const uint64_t cap_ability_max = kernel_module::get_cap_ability_max();
    const int caps_cnt = kernel_module::get_caps_cnt();

    Label deny = a->newLabel();
    Label loop = a->newLabel();

    a->mov(x10, Imm(0));
    RETURN_IF_ERROR(kernel_module::export_symbol::get_current_checked(a, x11));

    a->ldr(x11, Mem(x11, static_cast<int32_t>(m_cred_offset)));
    a->ldr(w12, Mem(x11, static_cast<int32_t>(m_cred_securebits_offset)));
    a->cbnz(w12, deny);

    // atomic_usage_size 实际接收 uid_offset；32 为八个 uid/gid 字段的总长度。
    a->add(x11, x11, Imm(static_cast<int64_t>(static_cast<int32_t>(atomic_usage_size)) + 32));

    // 读取当前字段，再将基址后递增至实际传入的 cap_inheritable_offset。
    a->ldr(w13, ptr_post(x11, static_cast<int32_t>(cap_effective_offset) - 32 -
                                   static_cast<int32_t>(atomic_usage_size)));
    a->cbnz(w13, deny);

    a->mov(x12, Imm(cap_ability_max));
    a->mov(x13, Imm(static_cast<uint64_t>(caps_cnt)));

    a->bind(loop);
    a->ldr(x14, ptr_post(x11, 8));
    a->cmp(x14, x12);
    a->b(CondCode::kLO, deny);
    a->subs(x13, x13, Imm(1));
    a->b(CondCode::kNE, loop);
    a->mov(x10, Imm(1));
    a->bind(deny);
    return KModErr::OK;
}
void PatchBase::emit_check_current_allow_selinux_to_x10(asmjit::a64::Assembler* a) {
    if (is_failed(emit_check_current_allow_selinux_to_x10_checked(a)))
        a->reportError(asmjit::kErrorInvalidState);
}

// 把 a 里发射好的机器码装成 before hook。
// 失败且原因为“指令不受支持”时打印目标地址原指令，便于定位机型。
KModErr PatchBase::patch_kernel_before_hook(uint64_t kaddr,
                                            const asmjit::a64::Assembler* a) {
    std::vector<uint8_t> shellcode = aarch64_asm_to_bytes(a);
    KModErr err = kernel_module::install_kernel_function_before_hook(kaddr, shellcode, nullptr);
    if (err == KModErr::ERR_MODULE_HOOK_INSN_NOT_SUPPORT) {
        printf_kernel_mem(kaddr);
    }
    return err;
}

KModErr PatchBase::patch_kernel_after_hook(uint64_t kaddr,
                                           const asmjit::a64::Assembler* a) {
    std::vector<uint8_t> shellcode = aarch64_asm_to_bytes(a);
    KModErr err = kernel_module::install_kernel_function_after_hook(kaddr, shellcode, nullptr);
    if (err == KModErr::ERR_MODULE_HOOK_INSN_NOT_SUPPORT) {
        printf_kernel_mem(kaddr);
    }
    return err;
}

} // namespace module_root
} // namespace default_modules
