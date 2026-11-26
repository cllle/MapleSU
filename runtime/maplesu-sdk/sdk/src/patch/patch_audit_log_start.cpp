// patch audit log start implementation.
//
// Installs a before-hook on the kernel's audit_log_start(): when the calling
// task is eligible to bypass SELinux checks, the original function is skipped
// and the call returns immediately; every other caller continues into the
// original function unchanged.
//
#include <cstdio>

#include "kernel_module_kit_umbrella.h"

#include "private_patch_base.h"

#include "aarch64_asm_helper.h"
#include "module_base_kernel_func_hook.h"

namespace default_modules {
namespace module_root {

PatchAuditLogStart::PatchAuditLogStart(const PatchBase& base, const unsigned long& audit_log_start_kaddr)
    : PatchBase(base), audit_log_start_kaddr_(audit_log_start_kaddr) {}

PatchAuditLogStart::~PatchAuditLogStart() = default;

KModErr PatchAuditLogStart::patch_audit_log_start() {
    if (audit_log_start_kaddr_ == 0) {
        puts("audit_log_start kaddr not found.");
        return KModErr::ERR_MODULE_AUDIT_PATCH;
    }

    // Generate the hook handler: the SELinux bypass eligibility is written to
    // x10 by the check sequence; zero continues the original function, while
    // a non-zero result returns zero without running the original function.
    aarch64_asm_ctx asm_ctx = init_aarch64_asm();
    asmjit::a64::Assembler* a = asm_ctx.assembler();
    asmjit::Label label_end = a->newLabel();

    kernel_module::arm64_before_hook_start(a);
    RETURN_IF_ERROR(emit_check_current_allow_selinux_to_x10_checked(a));
    a->cbz(asmjit::a64::x10, label_end);
    a->mov(asmjit::a64::w0, asmjit::a64::wzr);
    kernel_module::arm64_before_hook_end(a, false);
    a->bind(label_end);
    kernel_module::arm64_before_hook_end(a, true);

    return patch_kernel_before_hook(audit_log_start_kaddr_, a);
}

}  // namespace module_root
}  // namespace default_modules
