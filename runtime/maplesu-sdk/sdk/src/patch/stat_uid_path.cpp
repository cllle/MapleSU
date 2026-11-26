#include "kernel_module_kit_umbrella.h"
#include "aarch64_asm_helper.h"
#include "aarch64_asm_reg_protect_guard.h"
#include "module_base_kernel_string_ops.h"
#include "module_base_kernel_export_symbol.h"
#include <string>
using namespace asmjit;
using namespace asmjit::a64;
namespace default_modules::module_su_redirect {
class PatchBase {
public:
 std::string get_fake_su_path();
};
}
extern "C" std::string maplesu_stat_uid_template(void* object) {
 auto original = static_cast<default_modules::module_su_redirect::PatchBase*>(object)->get_fake_su_path();
 if (original.size() >= 3 && original.compare(original.size()-3, 3, "/su") == 0)
  return original.substr(0, original.size()-2) + "u-00000000/su";
 return original;
}
extern "C" void maplesu_emit_uid_stat_copy(Assembler* a, KModErr& error,
 GpX destination, GpX source, uint64_t size, const void* object) {
 const auto* layout = static_cast<const int32_t*>(object);
 const size_t allocated = (size + 15) & ~size_t(15);
 RegProtectGuard guard(a, x1,x2,x3,x4,x5,x6,x7,x8,x9,x10,x11,x12,x13,x14,x15,x16,x17,x18,x19);
 a->mov(x19, destination);
 a->sub(sp, sp, Imm(allocated));
 a->mov(x11, source);
 a->mov(x10, sp);
 kernel_module::string_ops::kstrcpy(a, x10, x11);
 error = kernel_module::export_symbol::get_current_checked(a, x12);
 if (is_failed(error)) return;
 a->ldr(x12, ptr(x12, layout[0]));
 a->ldr(w12, ptr(x12, layout[1])); // Same full cred.uid as the allowlist and exec path.
 aarch64_asm_set_x_cstr_ptr(a, x15, std::string("0123456789abcdef"));
 for (unsigned index=0; index<8; ++index) {
  a->lsr(w14,w12,Imm(28-index*4));
  a->and_(w14,w14,Imm(15));
  a->ldrb(w14,ptr(x15,x14));
  a->strb(w14,ptr(sp,size-12+index));
 }
 a->mov(x10,sp);
 kernel_module::export_symbol::copy_to_user(a,error,x19,x10,size);
 a->add(sp,sp,Imm(allocated));
}
