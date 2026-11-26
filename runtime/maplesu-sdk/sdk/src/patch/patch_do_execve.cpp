// patch do execve implementation.
//
#include "kernel_module_kit_umbrella.h"

#include <cstdint>
#include <string>

#include "aarch64_asm_helper.h"
#include "aarch64_asm_reg_protect_guard.h"
#include "module_base_kernel_func_hook.h"
#include "module_base_kernel_string_ops.h"

using namespace asmjit;
using namespace asmjit::a64;

// ExecveSymInfo：全局命名空间的 packed 结构（mangling 里是 `13ExecveSymInfo`，
struct __attribute__((packed)) ExecveSymInfo {
    uint64_t kaddr = 0;
    uint8_t  reg_index = 0;
    uint8_t  deref = 0;
};
static_assert(sizeof(ExecveSymInfo) == 10, "layout constraint violated");
static_assert(offsetof(ExecveSymInfo, reg_index) == 8, "layout constraint violated");
static_assert(offsetof(ExecveSymInfo, deref) == 9, "layout constraint violated");

namespace default_modules {
namespace module_su_redirect {

// PatchBase（40 字节）与 PatchDoExecve 的成员函数都由别的成员定义，
// 本 TU 只调用。这里的声明只为拿到正确的 mangling 与对象布局，
class PatchBase {
public:
    PatchBase(const PatchBase&);
    ~PatchBase();

    std::string get_fake_su_path();
    KModErr emit_current_uid(Assembler* a) {
        RETURN_IF_ERROR(kernel_module::export_symbol::get_current_checked(a, x12));
        a->ldr(x12, ptr(x12, field_0_));
        a->ldr(w12, ptr(x12, field_1_)); // +4 is the independently resolved cred.uid offset.
        return KModErr::OK;
    }

    // 生成"current 是否允许 su"的判定代码，结果放 x10（非 0 表示允许）。
    KModErr emit_check_current_allow_su_to_x10_checked(asmjit::v1_16::a64::Assembler* a, uint64_t arg);
    void emit_check_current_allow_su_to_x10(asmjit::v1_16::a64::Assembler* a, uint64_t hook_kaddr);

    // 把 a 里已经生成好的桩代码安装成 before-hook。
    KModErr patch_kernel_before_hook(uint64_t kaddr, const asmjit::v1_16::a64::Assembler* a);

private:
    int32_t     field_0_ = 0;
    int32_t     field_1_ = 0; // cred.uid offset; preserve the complete Android UID.
    int32_t     field_2_ = 0; // cred.euid offset; not the authorization/path subject.
    std::string field_3_;
};
static_assert(sizeof(PatchBase) == 40, "layout constraint violated");

class PatchDoExecve : public PatchBase {
public:
    PatchDoExecve(const PatchBase& base, const ExecveSymInfo& info);
    ~PatchDoExecve();

    // regs / hook_kaddr 传给 emit_check_current_allow_su_to_x10。
    KModErr patch_do_execve(uint64_t hook_kaddr);

private:
    ExecveSymInfo m_info = {};
};
static_assert(sizeof(PatchDoExecve) == 56, "layout constraint violated");

//   这与 `: PatchBase(base) { m_info = info; }` + 成员 `= {}` 的代码形状一致。
PatchDoExecve::PatchDoExecve(const PatchBase& base, const ExecveSymInfo& info) : PatchBase(base) {
    m_info = info;
}

PatchDoExecve::~PatchDoExecve() {}

KModErr PatchDoExecve::patch_do_execve(uint64_t hook_kaddr) {
    const std::string fake_su_path = get_fake_su_path();
    if (fake_su_path.size() < 3 || fake_su_path.compare(fake_su_path.size() - 3, 3, "/su") != 0)
        return KModErr::ERR_MODULE_PARAM;

    aarch64_asm_ctx asm_ctx = init_aarch64_asm();
    auto* a = asm_ctx.assembler();

    Label skip = a->newLabel();

    kernel_module::arm64_before_hook_start(a);

    // x10 = 1 表示"当前进程被允许走 su 路径"。
    const auto reader_error = emit_check_current_allow_su_to_x10_checked(a, hook_kaddr);
    if (is_failed(reader_error)) return reader_error;
    a->cbz(x10, skip);

    // File-backed execs have no filename; leave them to the native Image guard.
    a->cbz(x(m_info.reg_index), skip);

    // 路径参数为错误码（unsigned >= -4095）时不做改写。
    a->mov(x11, Imm(static_cast<int64_t>(-4095)));
    a->cmp(x(m_info.reg_index), x11);
    a->b(CondCode::kHS, skip);

    // 取出路径字符串指针：deref 时先解引用一次。
    if (m_info.deref != 0) {
        a->mov(x10, x(m_info.reg_index));
    } else {
        a->ldr(x10, ptr(x(m_info.reg_index)));
    }

    // 后缀比较：x10 指向的字符串是否以 "/su" 结尾。
    aarch64_asm_set_x_cstr_ptr(a, x11, std::string("/su"));
    {
        RegProtectGuard g(a, x0);
        kernel_module::string_ops::kendswith(a, x10, x11);
        a->mov(x11, x0);
    }
    a->cbz(x11, skip);

    // Only an allowlisted UID reaches this point. Build its private path on
    // the kernel stack; credentials, syscall entrypoints and services stay intact.
    {
        const std::string user_path = fake_su_path.substr(0, fake_su_path.size() - 2) + "u-00000000/su";
        const size_t aligned_size = (user_path.size() + 1 + 15) & ~size_t(15);
        RegProtectGuard guard(a, x0, x1, x2, x3, x4, x5, x6, x7,
            x8, x9, x10, x11, x12, x13, x14, x15, x16, x17, x18, x19);
        a->mov(x19, x10);
        a->sub(sp, sp, Imm(aligned_size));
        aarch64_asm_set_x_cstr_ptr(a, x11, user_path);
        a->mov(x10, sp);
        kernel_module::string_ops::kstrcpy(a, x10, x11);
        RETURN_IF_ERROR(emit_current_uid(a));
        aarch64_asm_set_x_cstr_ptr(a, x15, std::string("0123456789abcdef"));
        const size_t digits = fake_su_path.size();
        for (unsigned index = 0; index < 8; ++index) {
            a->lsr(w14, w12, Imm(28 - index * 4));
            a->and_(w14, w14, Imm(15));
            a->ldrb(w14, ptr(x15, x14));
            a->strb(w14, ptr(sp, digits + index));
        }
        a->mov(x10, x19);
        a->mov(x11, sp);
        kernel_module::string_ops::kstrcpy(a, x10, x11);
        a->add(sp, sp, Imm(aligned_size));
    }

    a->bind(skip);
    kernel_module::arm64_before_hook_end(a, true);

    return PatchBase::patch_kernel_before_hook(m_info.kaddr, a);
}

}  // namespace module_su_redirect
}  // namespace default_modules
