// default modules module root patch base implementation.
//
#pragma once

#include <cstdint>
#include <vector>

#include "kernel_module_kit_umbrella.h"

namespace default_modules {
namespace module_root {

// 补丁基类：持有 4 个 u32 的内核结构偏移，并提供两个“把一个 asmjit 汇编上下文
// 安装成内核 hook”的入口。
class PatchBase {
public:
    PatchBase(uint32_t cred_offset, uint32_t cred_atomic_usage_size,
              uint32_t cred_securebits_offset, uint32_t cred_cap_effective_offset);
    PatchBase(const PatchBase& other);
    ~PatchBase();

    int get_cred_atomic_usage_size();
    int get_cred_securebits_size();

    // 发射 AArch64 资格检查：euid、securebits 和连续 capability 字段；x10 为 0/1。
    KModErr emit_check_current_allow_selinux_to_x10_checked(asmjit::a64::Assembler* a);
    void emit_check_current_allow_selinux_to_x10(asmjit::a64::Assembler* a);

    // 把 a 里已经发射好的机器码安装成 target 的 before/after hook。
    KModErr patch_kernel_before_hook(uint64_t kaddr, const asmjit::a64::Assembler* a);
    KModErr patch_kernel_after_hook(uint64_t kaddr, const asmjit::a64::Assembler* a);

private:
    uint32_t m_cred_offset;               // 字段0
    uint32_t m_cred_atomic_usage_size;    // 历史名称；调用方传 uid_offset
    uint32_t m_cred_securebits_offset;    // 历史名称；调用方传 euid_offset
    uint32_t m_cred_cap_effective_offset; // 历史名称；调用方传 cap_inheritable_offset
};

static_assert(sizeof(PatchBase) == 16, "unexpected layout size");
static_assert(alignof(PatchBase) == 4, "layout constraint violated");

} // namespace module_root
} // namespace default_modules
