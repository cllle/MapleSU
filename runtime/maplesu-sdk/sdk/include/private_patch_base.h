// private patch base implementation.
//
#pragma once

#include <cstdint>

#include "module_err_def.h"

// asmjit 把 ABI 命名空间放在 inline namespace（v1_16）里，因此
// `asmjit::a64::Assembler` 与 `asmjit::v1_16::a64::Assembler` 是同一类型，
// mangling 也相同。这里直接按 AArch64 目标包含 core + a64 头。
#define ASMJIT_STATIC
#include "third_party/asmjit2/core.h"
#include "third_party/asmjit2/a64.h"

namespace default_modules {

// 每个 patch 子模块命名空间各有一份同布局的 PatchBase。
#define MAPLESU_DECLARE_PATCH_BASE(ns)                                     \
    namespace ns {                                                         \
    class PatchBase {                                                      \
    public:                                                                \
        PatchBase();                                                       \
        PatchBase(const PatchBase&);                                       \
        ~PatchBase();                                                      \
        KModErr patch_kernel_before_hook(uint64_t kaddr, const asmjit::a64::Assembler* assembler); \
        KModErr patch_kernel_after_hook(uint64_t kaddr, const asmjit::a64::Assembler* assembler);  \
        KModErr emit_check_current_allow_selinux_to_x10_checked(asmjit::a64::Assembler* assembler); \
        void emit_check_current_allow_selinux_to_x10(asmjit::a64::Assembler* assembler);           \
                                                                               \
    private:                                                               \
        int field0_ = 0;                                                   \
        int field1_ = 0;                                                   \
        int field2_ = 0;                                                   \
        int field3_ = 0;                                                   \
    };                                                                     \
    static_assert(sizeof(PatchBase) == 16, "layout constraint violated"); \
    }

MAPLESU_DECLARE_PATCH_BASE(module_root)
MAPLESU_DECLARE_PATCH_BASE(module_su_redirect)

#undef MAPLESU_DECLARE_PATCH_BASE

namespace module_root {
class PatchAuditLogStart : public PatchBase {
public:
    PatchAuditLogStart(const PatchBase& base, const unsigned long& audit_log_start_kaddr);
    ~PatchAuditLogStart();
    KModErr patch_audit_log_start();

private:
    uint64_t audit_log_start_kaddr_ = 0;
};
static_assert(sizeof(PatchAuditLogStart) == 24, "layout constraint violated");
}  // namespace module_root

namespace module_su_redirect {
class PatchSysStatx : public PatchBase {
public:
    PatchSysStatx(const PatchBase& base, unsigned long sys_statx_kaddr);
    ~PatchSysStatx();
    KModErr patch_sys_statx(unsigned long arg);

private:
    uint64_t sys_statx_kaddr_ = 0;
    uint64_t reserved_0_ = 0;
    uint64_t reserved_1_ = 0;
    uint64_t reserved_2_ = 0;
};
static_assert(sizeof(PatchSysStatx) == 48, "layout constraint violated");
}  // namespace module_su_redirect

}  // namespace default_modules
