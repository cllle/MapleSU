// module base kernel idle mem implementation.
//
#define SymbolLookupSilentGuard_8dfccc5cf454087c7314725d3487e703 \
    SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8
#include "kernel_module_kit_umbrella.h"
#undef SymbolLookupSilentGuard_8dfccc5cf454087c7314725d3487e703

#include "allocation_history_private.h"
#include "maplesu_request_private.h"
#include "module_base_kernel_request.h"
#include "maplesu_storage_private.h"

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <functional>
#include <string>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/xattr.h>
#include <system_error>
#include <unistd.h>
#include <vector>

using namespace asmjit;
using namespace asmjit::a64;

namespace default_modules {
namespace module_root {
KModErr set_current_root(bool enable_root);
}  // namespace module_root
}  // namespace default_modules

extern "C" const char* __get_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW();

//     _ZN10file_utilsL11file_existsERKNSt6__ndk14__fs10filesystem4pathE   (t)
//     _ZN10file_utilsL28set_file_selinux_access_modeEPKcNS_15SelinuxFileFlagE (t)
//       "security.selinux", "u:object_r:kernel:s0", 21, 0)==0
//   （注意是 kernel 标签、长度 21，不是随包 sdk/include 里 system_file/26 那一版；
namespace file_utils {

enum class SelinuxFileFlag : int {
    UNSPECIFIED = 0,
};

static bool file_exists(const std::filesystem::path& path) {
    std::error_code err;
    return std::filesystem::exists(path, err);
}

static bool set_file_selinux_access_mode(const char* file_path,
                                         SelinuxFileFlag /*flag*/) {
    if (::chmod(file_path, 0777) != 0) {
        return false;
    }
    return ::setxattr(file_path, "security.selinux", "u:object_r:kernel:s0", 21,
                      0) == 0;
}

}  // namespace file_utils

namespace {

constexpr const char* kSinittextKernelVersion = "6.0.0";
constexpr const char* kCfiCheckSymbol = "__cfi_check";
constexpr const char* kInittextBeginSymbol = "__inittext_begin";
constexpr const char* kSinittextSymbol = "_sinittext";
constexpr const char* kInittextEndSymbol = "__inittext_end";
constexpr const char* kEinittextSymbol = "_einittext";

constexpr uint64_t kCfiCheckStartSkip = 8192;
constexpr uint64_t kCfiCheckEndSkip = 65536;

constexpr const char* kInitMmSymbol = "init_mm";
constexpr const char* kPteFnAbiKernelVersion = "5.3.0";

constexpr const char* kApplyToPageRange1ErrFmt = "apply_to_page_range1 err:%ld\n";
constexpr const char* kApplyToPageRange2ErrFmt = "apply_to_page_range2 err:%ld\n";

}  // namespace

// get_kernel_shellcode_u64_result —— 生成内核 shellcode、在内核态执行、回传 x0。
//     %3 = init_aarch64_asm(); %8 = assembler
//     arm64_module_asm_func_start(%8)
//     %4 = x0; %10 = emit(%8, %4)           ← std::function 发射器（可返回错误）
//     if (%10 != 0) return %10
//     %5 = %4; arm64_module_asm_func_end(%8, %5)
//     %6 = aarch64_asm_to_bytes(%8)
//     return execute_kernel_asm_func(%6, %1)
//   失败行为：发射器报错时原样返回错误码，且不执行 shellcode。
static KModErr get_kernel_shellcode_u64_result(
    std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emit,
    uint64_t& result, bool* execution_attempted = nullptr) {
    if (execution_attempted) *execution_attempted = false;
    aarch64_asm_ctx asm_ctx = init_aarch64_asm();
    asmjit::a64::Assembler* a = asm_ctx.assembler();
    kernel_module::arm64_module_asm_func_start(a);

    asmjit::a64::GpX return_reg = asmjit::a64::x0;
    if (const KModErr emit_err = emit(a, return_reg);
        emit_err != KModErr::OK) {
        return emit_err;
    }

    kernel_module::arm64_module_asm_func_end(a, return_reg);
    const auto code = aarch64_asm_to_bytes(a);
    if (execution_attempted) *execution_attempted = true;
    return kernel_module::execute_kernel_asm_func(code, result);
}

// pte_fn_t 回调发射器（save / restore 两枚）。
//
//     embed(kcfi_hash, 4)                       ← KCFI 类型哈希，回调入口 = code+4
//     aarch64_asm_bit_jc()
//     if (is_kernel_version_less("5.3.0"))
//         ldr x4,[x0]; ldr x5,[x3]; str x4,[x5]; add x5,#8; str x5,[x3]
//     else
//         ldr x4,[x0]; ldr x5,[x2]; str x4,[x5]; add x5,#8; str x5,[x2]
//     mov x0, xzr; ret x30
//     ... ldr x5,[data]; ldr x4,[x5]; str x4,[x0]; add x5,#8; str x5,[data]
//   语义：x0 = pte_t*；data（x2/x3）= &游标；游标指向 pte 快照数组。
//     save    : 快照[i] = *pte;         游标 += 8      （保存 trampoline 页表项）
//     restore : *pte   = 快照[i];       游标 += 8      （写回 idle 区页表项）
//   返回值 x0 = 0（int pte_fn_t 的“成功”）。
//
//   两个发射器各只被调用一次，-Oz 下会被整体内联进 remap_kernel_exec_range
static std::vector<uint8_t> make_pte_fn_save_shellcode(uint32_t kcfi_hash) {
    aarch64_asm_ctx asm_ctx = init_aarch64_asm();
    asmjit::a64::Assembler* a = asm_ctx.assembler();

    a->embed(&kcfi_hash, sizeof(kcfi_hash));
    aarch64_asm_bit_jc(a);

    if (kernel_module::is_kernel_version_less(kPteFnAbiKernelVersion)) {
        // < 5.3.0: int pte_fn_t(pte_t*, pgtable_t, unsigned long, void*)
        a->ldr(asmjit::a64::x4, ptr(asmjit::a64::x0));
        a->ldr(asmjit::a64::x5, ptr(asmjit::a64::x3));
        a->str(asmjit::a64::x4, ptr(asmjit::a64::x5));
        a->add(asmjit::a64::x5, asmjit::a64::x5, imm(8));
        a->str(asmjit::a64::x5, ptr(asmjit::a64::x3));
    } else {
        // >= 5.3.0: int pte_fn_t(pte_t*, unsigned long, void*)
        a->ldr(asmjit::a64::x4, ptr(asmjit::a64::x0));
        a->ldr(asmjit::a64::x5, ptr(asmjit::a64::x2));
        a->str(asmjit::a64::x4, ptr(asmjit::a64::x5));
        a->add(asmjit::a64::x5, asmjit::a64::x5, imm(8));
        a->str(asmjit::a64::x5, ptr(asmjit::a64::x2));
    }

    a->mov(asmjit::a64::x0, asmjit::a64::xzr);
    a->ret(asmjit::a64::x30);
    return aarch64_asm_to_bytes(a);
}

static std::vector<uint8_t> make_pte_fn_restore_shellcode(uint32_t kcfi_hash) {
    aarch64_asm_ctx asm_ctx = init_aarch64_asm();
    asmjit::a64::Assembler* a = asm_ctx.assembler();

    a->embed(&kcfi_hash, sizeof(kcfi_hash));
    aarch64_asm_bit_jc(a);

    if (kernel_module::is_kernel_version_less(kPteFnAbiKernelVersion)) {
        a->ldr(asmjit::a64::x5, ptr(asmjit::a64::x3));
        a->ldr(asmjit::a64::x4, ptr(asmjit::a64::x5));
        a->str(asmjit::a64::x4, ptr(asmjit::a64::x0));
        a->add(asmjit::a64::x5, asmjit::a64::x5, imm(8));
        a->str(asmjit::a64::x5, ptr(asmjit::a64::x3));
    } else {
        a->ldr(asmjit::a64::x5, ptr(asmjit::a64::x2));
        a->ldr(asmjit::a64::x4, ptr(asmjit::a64::x5));
        a->str(asmjit::a64::x4, ptr(asmjit::a64::x0));
        a->add(asmjit::a64::x5, asmjit::a64::x5, imm(8));
        a->str(asmjit::a64::x5, ptr(asmjit::a64::x2));
    }

    a->mov(asmjit::a64::x0, asmjit::a64::xzr);
    a->ret(asmjit::a64::x30);
    return aarch64_asm_to_bytes(a);
}

// is_kaddr_can_read —— 判断某内核地址是否可读（内核态实测，不 panic）。
//     KModErr err = ERR_MODULE_PARAM(-20000);
//     StackBufGuard buf(a, 1, x10);                 // 栈上 16 字节暂存
//     aarch64_asm_mov_x(a, x11, kaddr);
//     export_symbol::copy_from_kernel_nofault(a, err, x10, x11, 1);
//     if (err == OK) result = x0;                   // x0 = copy_from_kernel_nofault 返回
//   copy_from_kernel_nofault 返回 0 表示读成功 => 可读。
//   失败行为：发射器（符号查找失败等）报错时 out_can_read 保持 false 并返回该错误。
static KModErr is_kaddr_can_read(uint64_t kaddr, bool& out_can_read) {
    out_can_read = false;

    uint64_t exec_result = 0;
    const KModErr err = get_kernel_shellcode_u64_result(
        [kaddr](asmjit::a64::Assembler* a, asmjit::a64::GpX& result) -> KModErr {
            KModErr copy_err = KModErr::ERR_MODULE_PARAM;
            StackBufGuard stack_buf(a, 1, asmjit::a64::x10);
            aarch64_asm_mov_x(a, asmjit::a64::x11, kaddr);
            kernel_module::export_symbol::copy_from_kernel_nofault(
                a, copy_err, asmjit::a64::x10, asmjit::a64::x11, 1);
            if (copy_err == KModErr::OK) {
                result = asmjit::a64::x0;
            }
            return copy_err;
        },
        exec_result);
    if (err != KModErr::OK) {
        return err;
    }
    out_can_read = (exec_result == 0);
    return KModErr::OK;
}

// flush_all_tlb —— 内核态刷全量 TLB（TLBI VMALLE1IS）。
//     dsb ishst ; tlbi vmalle1is ; dsb ish ; isb   （Predicate 值 10/–/11/15 实测）
//   arm64_module_asm_func_start，再调用发射器；发射器成功后 func_end + to_bytes +
static KModErr flush_all_tlb() {
    std::function<KModErr(asmjit::a64::Assembler*)> emit =
        [](asmjit::a64::Assembler* a) -> KModErr {
        aarch64_asm_dsb_ishst(a);
        aarch64_asm_tlbi_vmalle1is(a);
        aarch64_asm_dsb_ish(a);
        aarch64_asm_isb(a);
        return KModErr::OK;
    };

    aarch64_asm_ctx asm_ctx = init_aarch64_asm();
    asmjit::a64::Assembler* a = asm_ctx.assembler();
    kernel_module::arm64_module_asm_func_start(a);

    const KModErr emit_err = emit(a);
    if (emit_err != KModErr::OK) return emit_err;
    kernel_module::arm64_module_asm_func_end(a);
    uint64_t output_result = 0;
    return kernel_module::execute_kernel_asm_func(aarch64_asm_to_bytes(a), output_result);
}

// remap_kernel_exec_range —— 把 idle 区 [kaddr, kaddr+size) 的页表重映射到新分配
// 的 trampoline 可执行页上，并刷新 TLB。
//
//     pte_snapshot_bytes = (map_size / page_size) * 8;
//                         &游标); 失败 → printf(...1...) + ERR_MODULE_IDLE_MEM_REMAP
//                         &游标); 失败 → printf(...2...) + ERR_MODULE_IDLE_MEM_REMAP
//
//   失败行为：返回 module_err_def.h 既有错误码；已分配的临时内核内存按逆序回滚；
//   一旦第二次 apply_to_page_range 可能建立别名，trampoline 不再释放，
//   避免 idle 区页表指向已回收物理页。
static KModErr remap_kernel_exec_range(uint64_t kaddr, uint64_t size,
                                       uint64_t& out_end_addr) {
    const uint64_t page_size = kernel_module::get_page_size();
    if (page_size < sizeof(uint64_t) || (page_size & (page_size - 1)) ||
        !size || size > UINT64_MAX - kaddr ||
        kaddr + size > UINT64_MAX - (page_size - 1)) {
        return KModErr::ERR_MODULE_PARAM;
    }
    out_end_addr = kaddr + size;

    uint64_t init_mm_kaddr = 0;
    KModErr err = kernel_module::kallsyms_lookup_name(kInitMmSymbol, init_mm_kaddr);
    if (err != KModErr::OK) {
        return err;
    }

    const uint64_t page_start_addr = kaddr & ~(page_size - 1);
    const uint64_t page_end_addr =
        (kaddr + size + page_size - 1) & ~(page_size - 1);
    const uint64_t map_size = page_end_addr - page_start_addr;
    const uint64_t page_count = map_size / page_size;
    if (map_size == 0 || map_size > UINT32_MAX ||
        page_count > UINT32_MAX / sizeof(uint64_t)) {
        return KModErr::ERR_MODULE_PARAM;
    }
    const uint32_t pte_snapshot_bytes =
        static_cast<uint32_t>(page_count * sizeof(uint64_t));

    uint64_t trampoline_kaddr = 0;
    uint64_t pte_snapshot_kaddr = 0;
    uint64_t cursor_slot_kaddr = 0;
    uint64_t save_code_kaddr = 0;
    uint64_t restore_code_kaddr = 0;
    struct RemapAllocations {
        uint64_t& trampoline;
        uint64_t& snapshot;
        uint64_t& cursor;
        uint64_t& save_code;
        uint64_t& restore_code;
        uint64_t trampoline_size;
        uint64_t save_size = 0;
        uint64_t restore_size = 0;
        bool alias_possible = false;
        bool trampoline_protected = false;
        bool save_protected = false;
        bool restore_protected = false;
        ~RemapAllocations() {
            kernel_module::CleanupReceiptScope preserve_failure;
            const auto release = [](uint64_t address, uint64_t bytes, bool protected_page) {
                if (!address) return;
                try {
                    if (protected_page && kernel_module::set_kernel_mem_protection(
                            address, bytes, kernel_module::KernMemProt::KMP_RW) != KModErr::OK) return;
                    (void)kernel_module::free_kernel_mem(address);
                } catch (...) {
                    // Cleanup emits shellcode too; retain this allocation on allocation failure.
                }
            };
            release(restore_code, restore_size, restore_protected);
            release(save_code, save_size, save_protected);
            release(cursor, 0, false);
            release(snapshot, 0, false);
            // The second apply can install a prefix even when its receipt fails.
            if (!alias_possible) release(trampoline, trampoline_size, trampoline_protected);
        }
    } allocations{trampoline_kaddr, pte_snapshot_kaddr, cursor_slot_kaddr,
                  save_code_kaddr, restore_code_kaddr, map_size};

    // The remapped alias survives this management session for the boot.
    // Its Legacy backing must not be a session-owned staging allocation.
    err = kernel_module::maplesu_request_owner_mode()
        ? kernel_module::alloc_kernel_mem(static_cast<uint32_t>(map_size), trampoline_kaddr)
        : kernel_module::request_kernel_alloc_mem(static_cast<uint32_t>(map_size), trampoline_kaddr);
    if (err != KModErr::OK) {
        return err;
    }
    allocations.trampoline_protected = true;
    err = kernel_module::set_kernel_mem_protection(
        trampoline_kaddr, map_size, kernel_module::KernMemProt::KMP_X);
    if (err != KModErr::OK) {
        return err;
    }
    err = kernel_module::alloc_kernel_mem(pte_snapshot_bytes, pte_snapshot_kaddr);
    if (err != KModErr::OK) {
        return err;
    }
    err = kernel_module::alloc_kernel_mem(8, cursor_slot_kaddr);
    if (err != KModErr::OK) {
        return err;
    }
    err = kernel_module::write_kernel_mem(cursor_slot_kaddr, &pte_snapshot_kaddr,
                                          sizeof(pte_snapshot_kaddr),
                                          kernel_module::KernMemProt::KMP_RW);
    if (err != KModErr::OK) {
        return err;
    }

    uint32_t pte_fn_kcfi_hash = 0;
    err = kernel_module::get_pte_fn_t_kcfi_hash(pte_fn_kcfi_hash);
    if (err != KModErr::OK) {
        return err;
    }
    std::vector<uint8_t> save_code = make_pte_fn_save_shellcode(pte_fn_kcfi_hash);
    std::vector<uint8_t> restore_code =
        make_pte_fn_restore_shellcode(pte_fn_kcfi_hash);

    allocations.save_size = save_code.size();
    allocations.restore_size = restore_code.size();
    err = kernel_module::alloc_kernel_mem(
        static_cast<uint32_t>(save_code.size()), save_code_kaddr);
    if (err != KModErr::OK) {
        return err;
    }
    err = kernel_module::alloc_kernel_mem(
        static_cast<uint32_t>(restore_code.size()), restore_code_kaddr);
    if (err != KModErr::OK) {
        return err;
    }
    err = kernel_module::write_kernel_mem(save_code_kaddr, save_code.data(),
                                          static_cast<uint32_t>(save_code.size()),
                                          kernel_module::KernMemProt::KMP_RW);
    if (err != KModErr::OK) {
        return err;
    }
    err = kernel_module::write_kernel_mem(
        restore_code_kaddr, restore_code.data(),
        static_cast<uint32_t>(restore_code.size()),
        kernel_module::KernMemProt::KMP_RW);
    if (err != KModErr::OK) {
        return err;
    }
    allocations.save_protected = true;
    err = kernel_module::set_kernel_mem_protection(
        save_code_kaddr, save_code.size(), kernel_module::KernMemProt::KMP_X);
    if (err != KModErr::OK) {
        return err;
    }
    allocations.restore_protected = true;
    err = kernel_module::set_kernel_mem_protection(
        restore_code_kaddr, restore_code.size(), kernel_module::KernMemProt::KMP_X);
    if (err != KModErr::OK) {
        return err;
    }

    uint64_t apply_result = 0;
    err = get_kernel_shellcode_u64_result(
        [init_mm_kaddr, trampoline_kaddr, map_size, save_code_kaddr,
         cursor_slot_kaddr](asmjit::a64::Assembler* a,
                            asmjit::a64::GpX& result) -> KModErr {
            KModErr call_err = KModErr::ERR_MODULE_PARAM;
            aarch64_asm_mov_x(a, asmjit::a64::x10, init_mm_kaddr);
            aarch64_asm_mov_x(a, asmjit::a64::x11, trampoline_kaddr);
            aarch64_asm_mov_x(a, asmjit::a64::x12, map_size);
            aarch64_asm_mov_x(a, asmjit::a64::x13, save_code_kaddr + 4);
            aarch64_asm_mov_x(a, asmjit::a64::x14, cursor_slot_kaddr);
            kernel_module::export_symbol::apply_to_page_range(
                a, call_err, asmjit::a64::x10, asmjit::a64::x11,
                asmjit::a64::x12, asmjit::a64::x13, asmjit::a64::x14);
            if (call_err == KModErr::OK) {
                result = asmjit::a64::x0;
            }
            return call_err;
        },
        apply_result);
    if (err != KModErr::OK) {
        return err;
    }
    if (apply_result != 0) {
        printf(kApplyToPageRange1ErrFmt, static_cast<long>(apply_result));
        return KModErr::ERR_MODULE_IDLE_MEM_REMAP;
    }

    err = kernel_module::write_kernel_mem(cursor_slot_kaddr, &pte_snapshot_kaddr,
                                          sizeof(pte_snapshot_kaddr),
                                          kernel_module::KernMemProt::KMP_RW);
    if (err != KModErr::OK) {
        return err;
    }

    apply_result = 0;
    allocations.alias_possible = true;
    err = get_kernel_shellcode_u64_result(
        [init_mm_kaddr, page_start_addr, map_size, restore_code_kaddr,
         cursor_slot_kaddr](asmjit::a64::Assembler* a,
                            asmjit::a64::GpX& result) -> KModErr {
            KModErr call_err = KModErr::ERR_MODULE_PARAM;
            aarch64_asm_mov_x(a, asmjit::a64::x10, init_mm_kaddr);
            aarch64_asm_mov_x(a, asmjit::a64::x11, page_start_addr);
            aarch64_asm_mov_x(a, asmjit::a64::x12, map_size);
            aarch64_asm_mov_x(a, asmjit::a64::x13, restore_code_kaddr + 4);
            aarch64_asm_mov_x(a, asmjit::a64::x14, cursor_slot_kaddr);
            kernel_module::export_symbol::apply_to_page_range(
                a, call_err, asmjit::a64::x10, asmjit::a64::x11,
                asmjit::a64::x12, asmjit::a64::x13, asmjit::a64::x14);
            if (call_err == KModErr::OK) {
                result = asmjit::a64::x0;
            }
            return call_err;
        },
        apply_result);
    if (err != KModErr::OK) {
        return err;
    }
    if (apply_result != 0) {
        printf(kApplyToPageRange2ErrFmt, static_cast<long>(apply_result));
        return KModErr::ERR_MODULE_IDLE_MEM_REMAP;
    }

    err = flush_all_tlb();
    if (err != KModErr::OK) return err;

    err = kernel_module::set_kernel_mem_protection(
        save_code_kaddr, save_code.size(), kernel_module::KernMemProt::KMP_RW);
    if (err != KModErr::OK) {
        return err;
    }
    err = kernel_module::set_kernel_mem_protection(
        restore_code_kaddr, restore_code.size(),
        kernel_module::KernMemProt::KMP_RW);
    if (err != KModErr::OK) {
        return err;
    }
    err = kernel_module::free_kernel_mem(save_code_kaddr);
    save_code_kaddr = 0;
    if (err != KModErr::OK) {
        return err;
    }
    err = kernel_module::free_kernel_mem(restore_code_kaddr);
    restore_code_kaddr = 0;
    if (err != KModErr::OK) {
        return err;
    }
    err = kernel_module::free_kernel_mem(pte_snapshot_kaddr);
    pte_snapshot_kaddr = 0;
    if (err != KModErr::OK) {
        return err;
    }
    err = kernel_module::free_kernel_mem(cursor_slot_kaddr);
    cursor_slot_kaddr = 0;
    return err;
}

// Static inittext is not a VM_ALLOC area accepted by set_memory_ro/x.
// Change only leaf permission bits; retain the existing PFN and code bytes.
static std::vector<uint8_t> make_pte_fn_idle_exec_shellcode(uint32_t kcfi_hash) {
    constexpr uint64_t read_only = uint64_t{1} << 7;
    constexpr uint64_t dbm = uint64_t{1} << 51;
    constexpr uint64_t contiguous = uint64_t{1} << 52;
    constexpr uint64_t pxn = uint64_t{1} << 53;
    constexpr uint64_t uxn = uint64_t{1} << 54;
    aarch64_asm_ctx context = init_aarch64_asm();
    auto* a = context.assembler();
    const Label accepted = a->newLabel(), failed = a->newLabel();
    const GpX data = kernel_module::is_kernel_version_less(kPteFnAbiKernelVersion)
        ? x3 : x2;
    a->embed(&kcfi_hash, sizeof(kcfi_hash));
    aarch64_asm_bit_jc(a);
    a->ldr(x4, ptr(x0));
    a->and_(x5, x4, Imm(3));
    a->cmp(x5, Imm(3));
    a->b(CondCode::kNE, failed);
    a->and_(x5, x4, Imm(uint64_t{1} << 6));
    a->cbnz(x5, failed);
    aarch64_asm_mov_x(a, x6, dbm | pxn);
    a->bic(x5, x4, x6);
    aarch64_asm_mov_x(a, x6, read_only | uxn);
    a->orr(x5, x5, x6);
    a->cmp(x4, x5);
    a->b(CondCode::kEQ, accepted);
    // Check mode never repairs, and live contiguous groups are never split.
    a->ldr(x6, ptr(data));
    a->cbnz(x6, failed);
    aarch64_asm_mov_x(a, x6, contiguous);
    a->and_(x6, x4, x6);
    a->cbnz(x6, failed);
    a->str(x5, ptr(x0));
    a->bind(accepted);
    a->ldr(x4, ptr(data, 8));
    a->add(x4, x4, Imm(1));
    a->str(x4, ptr(data, 8));
    a->mov(x0, xzr);
    a->ret(x30);
    a->bind(failed);
    aarch64_asm_mov_x(a, x0, static_cast<uint64_t>(-22));
    a->ret(x30);
    return aarch64_asm_to_bytes(a);
}

static KModErr protect_kernel_idle_exec_range(uint64_t address, uint64_t size) {
    const uint64_t page_size = kernel_module::get_page_size();
    if ((page_size != 4096 && page_size != 16384 && page_size != 65536) ||
        !size || ((address | size) & (page_size - 1)) || size > UINT64_MAX - address)
        return KModErr::ERR_MODULE_PARAM;
    uint64_t init_mm = 0;
    KModErr error = kernel_module::kallsyms_lookup_name(kInitMmSymbol, init_mm);
    if (error != KModErr::OK) return error;
    uint32_t hash = 0;
    error = kernel_module::get_pte_fn_t_kcfi_hash(hash);
    if (error != KModErr::OK) return error;
    const auto code = make_pte_fn_idle_exec_shellcode(hash);
    uint64_t callback = 0, state = 0;
    struct CallbackAllocations {
        uint64_t& callback;
        uint64_t& state;
        uint64_t size;
        bool protected_page = false;
        bool callback_pending = false;
        ~CallbackAllocations() {
            // Unknown completion must keep everything the callback can access.
            if (callback_pending) return;
            kernel_module::CleanupReceiptScope preserve_failure;
            try {
                if (state) (void)kernel_module::free_kernel_mem(state);
                if (callback && (!protected_page ||
                    kernel_module::set_kernel_mem_protection(callback, size,
                        kernel_module::KernMemProt::KMP_RW) == KModErr::OK))
                    (void)kernel_module::free_kernel_mem(callback);
            } catch (...) {
                // Keep executable storage when its cleanup receipt is uncertain.
            }
        }
    } allocations{callback, state, code.size()};
    error = kernel_module::alloc_kernel_mem(static_cast<uint32_t>(code.size()), callback);
    if (error != KModErr::OK) return error;
    error = kernel_module::alloc_kernel_mem(16, state);
    if (error != KModErr::OK) return error;
    error = kernel_module::write_kernel_mem(callback, code.data(),
        static_cast<uint32_t>(code.size()), kernel_module::KernMemProt::KMP_RW);
    if (error != KModErr::OK) return error;
    allocations.protected_page = true;
    error = kernel_module::set_kernel_mem_protection(callback, code.size(),
        kernel_module::KernMemProt::KMP_X);
    if (error != KModErr::OK) return error;
    for (uint64_t check = 0; check != 2; ++check) {
        uint64_t receipt[2] = {check, 0};
        error = kernel_module::write_kernel_mem(state, receipt, sizeof(receipt),
            kernel_module::KernMemProt::KMP_RW);
        if (error != KModErr::OK) return error;
        uint64_t result = 0;
        error = get_kernel_shellcode_u64_result(
            [=](Assembler* a, GpX& out) -> KModErr {
                KModErr call_error = KModErr::ERR_MODULE_PARAM;
                aarch64_asm_mov_x(a, x10, init_mm);
                aarch64_asm_mov_x(a, x11, address);
                aarch64_asm_mov_x(a, x12, size);
                aarch64_asm_mov_x(a, x13, callback + 4);
                aarch64_asm_mov_x(a, x14, state);
                kernel_module::export_symbol::apply_to_page_range(
                    a, call_error, x10, x11, x12, x13, x14);
                if (call_error == KModErr::OK) out = x0;
                return call_error;
            }, result, &allocations.callback_pending);
        // A failed public EXEC has no per-call identity available here. An old
        // completed diagnostic receipt is not proof that this callback stopped.
        if (error == KModErr::OK) allocations.callback_pending = false;
        if (allocations.callback_pending) return error;
        // A completed failing apply can have changed a prefix. Keep its first
        // error/receipt while flushing, and never replay the failed apply.
        if (error != KModErr::OK || result) {
            if (!check) {
                kernel_module::CleanupReceiptScope preserve_failure;
                (void)flush_all_tlb();
            }
            return error != KModErr::OK ? error : KModErr::ERR_MODULE_IDLE_MEM_REMAP;
        }
        if (!check) {
            error = flush_all_tlb();
            if (error != KModErr::OK) return error;
        }
        error = kernel_module::read_kernel_mem(state, receipt, sizeof(receipt));
        if (error != KModErr::OK) return error;
        if (receipt[0] != check || receipt[1] != size / page_size)
            return KModErr::ERR_MODULE_IDLE_MEM_REMAP;
    }
    return KModErr::OK;
}

namespace kernel_module {

//       get_idle_mem_base_dir() / path("idle_mem_*.…")
//       dir = snprintf("/data/%s/", root_key.substr(0,16))  (bionic fortify inline)
//       if (!dir.empty() && exists(dir) && is_directory(dir)) return dir;
//       return path("/dev");
//       read → 必须读出恰好 8 字节，memcpy 到输出。
namespace detail_idle_mem_history {

static std::string make_idle_mem_base_dir_path() {
    char path_buf[1024] = {0};
    // bionic <stdio.h> 的 fortify inline snprintf（static inline，符号名 _ZL8snprintf…）
    snprintf(path_buf, sizeof(path_buf), "/data/%s/",
             std::string(__get_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW())
                 .substr(0, 16)
                 .c_str());
    return std::string(path_buf);
}

static std::filesystem::path get_idle_mem_base_dir() {
    std::error_code err;
    const std::filesystem::path dir_path = make_idle_mem_base_dir_path();
    if (!dir_path.empty() && std::filesystem::exists(dir_path, err) &&
        std::filesystem::is_directory(dir_path, err)) {
        return dir_path;
    }
    return std::filesystem::path("/dev");
}

static std::string get_idle_mem_start_path() {
    return (get_idle_mem_base_dir() /
            std::filesystem::path("idle_mem_start.576b1fdc5cf2a37f8b0ceb0aaae2994b"))
        .string();
}

static std::string get_idle_mem_next_path() {
    return (get_idle_mem_base_dir() /
            std::filesystem::path("idle_mem_next.aebac350a93a132dbe921caac01888a5"))
        .string();
}

static std::string get_idle_mem_end_path() {
    return (get_idle_mem_base_dir() /
            std::filesystem::path("idle_mem_end.12d478816ccc2405ea6a0e394ab2e7cf"))
        .string();
}

// Authenticated workers use their held deployment; Legacy retains its old fallback.
static KModErr get_idle_mem_history_paths(std::string& start, std::string& next,
                                             std::string& end) {
    if (kernel_module::maplesu_request_owner_mode()) {
        std::string directory;
        const KModErr error = maplesu::storage::current_kernel_directory(directory);
        if (error != KModErr::OK) return error;
        start = directory + "idle_mem_start.576b1fdc5cf2a37f8b0ceb0aaae2994b";
        next = directory + "idle_mem_next.aebac350a93a132dbe921caac01888a5";
        end = directory + "idle_mem_end.12d478816ccc2405ea6a0e394ab2e7cf";
    } else {
        start = get_idle_mem_start_path();
        next = get_idle_mem_next_path();
        end = get_idle_mem_end_path();
    }
    return KModErr::OK;
}

static bool read_file_u64(const char* file_path, uint64_t& out_value) {
    std::ifstream file(file_path, std::ios::binary);
    if (file.fail()) {
        return false;
    }
    uint64_t value = 0;
    file.read(reinterpret_cast<char*>(&value), sizeof(value));
    if (file.fail()) {
        return false;
    }
    char extra = 0;
    file.read(&extra, 1);
    if (!file.eof() || file.bad()) {
        return false;
    }
    out_value = value;
    return true;
}

static bool write_file_u64(const char* file_path, uint64_t value) {
    if (file_path == nullptr) {
        return false;
    }
    std::ofstream file(file_path,
                       std::ios::out | std::ios::trunc | std::ios::binary);
    if (file.fail()) {
        return false;
    }
    file.write(reinterpret_cast<const char*>(&value), sizeof(value));
    file.close();
    return file.good();
}

static KModErr write_history_files(uint64_t start, uint64_t next, uint64_t end) {
    std::string start_path, next_path, end_path;
    const KModErr path_error = get_idle_mem_history_paths(start_path, next_path, end_path);
    if (path_error != KModErr::OK) return path_error;
    if (!write_file_u64(start_path.c_str(), start) ||
        !write_file_u64(next_path.c_str(), next) ||
        !write_file_u64(end_path.c_str(), end)) {
        return KModErr::ERR_MODULE_WRITE_FILE;
    }
    if (!file_utils::set_file_selinux_access_mode(
            start_path.c_str(), file_utils::SelinuxFileFlag::UNSPECIFIED) ||
        !file_utils::set_file_selinux_access_mode(
            next_path.c_str(), file_utils::SelinuxFileFlag::UNSPECIFIED) ||
        !file_utils::set_file_selinux_access_mode(
            end_path.c_str(), file_utils::SelinuxFileFlag::UNSPECIFIED)) {
        return KModErr::ERR_SET_FILE_SELINUX;
    }
    return KModErr::OK;
}

}  // namespace detail_idle_mem_history

/***************************************************************************
 * get_empty_area_start_addr — 取“内核可执行空洞”的起始地址。
 *
 *                         addr += 8192;             // 跳过 CFI 检查桩
 *                     kallsyms_lookup_name("_sinittext", addr);
 *
 * 失败行为：符号全查不到时返回 -19969（ERR_MODULE_IDLE_MEM_NOT_FOUND），
 * out_addr 写 0；调用方不得据此继续做空洞扫描。
 ***************************************************************************/
KModErr get_empty_area_start_addr(uint64_t& out_addr) {
    SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8 pg;

    uint64_t addr = 0;
    bool resolved = false;

    if (is_kernel_version_less(kSinittextKernelVersion)) {
        if (kallsyms_lookup_name(kCfiCheckSymbol, addr) == KModErr::OK) {
            addr += kCfiCheckStartSkip;
            resolved = true;
        }
    }

    if (!resolved) {
        if (kallsyms_lookup_name(kInittextBeginSymbol, addr) != KModErr::OK) {
            kallsyms_lookup_name(kSinittextSymbol, addr);
        }
    }

    out_addr = addr;
    return (addr == 0) ? KModErr::ERR_MODULE_IDLE_MEM_NOT_FOUND : KModErr::OK;
}

/***************************************************************************
 * get_empty_area_end_addr — 取“内核可执行空洞”的结束地址。
 *
 *                         → out = addr + 65536;
 *                 if (ok && addr != 0) → out = addr;
 *                 else { kallsyms_lookup_name("_einittext", addr); out = addr; }
 *
 * 直接采用，否则再退一步查 _einittext —— 这段是两个 phi 合流的等价表达，
 ***************************************************************************/
KModErr get_empty_area_end_addr(uint64_t& out_addr) {
    SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8 pg;

    uint64_t addr = 0;
    bool resolved = false;

    if (is_kernel_version_less(kSinittextKernelVersion) &&
        kallsyms_lookup_name(kCfiCheckSymbol, addr) == KModErr::OK) {
        addr += kCfiCheckEndSkip;
        resolved = true;
    }

    if (!resolved) {
        if (!((kallsyms_lookup_name(kInittextEndSymbol, addr) == KModErr::OK) &&
              addr != 0)) {
            kallsyms_lookup_name(kEinittextSymbol, addr);
        }
    }

    out_addr = addr;
    return (addr == 0) ? KModErr::ERR_MODULE_IDLE_MEM_NOT_FOUND : KModErr::OK;
}

/***************************************************************************
 * acquire_kernel_idle_region — 取一段可执行 idle 区（必要时把 inittext 空洞
 * 重映射成可执行内存），并以“历史记录”方式推进游标。
 *
 *                 ERR_MODULE_IDLE_MEM_NOT_ENOUGH(-19968)
 *                  写文件失败 → ERR_MODULE_WRITE_FILE(-19933)
 *                  打 SELinux 标签失败 → ERR_SET_FILE_SELINUX(-19915)
 *
 * 失败行为：不写任何历史、不返回地址；重映射失败时返回 remap 的错误码。
 ***************************************************************************/
KModErr acquire_kernel_idle_region(uint64_t size, uint64_t& out_addr) {
    if (!size || (size & 3u)) return KModErr::ERR_MODULE_PARAM;
    uint64_t start_addr = 0;  // %54
    uint64_t next_addr = 0;   // %55
    uint64_t end_addr = 0;    // %56
    bool exec_range_established = false;

    if (getuid() != 0) {
        const KModErr root_err = default_modules::module_root::set_current_root(true);
        if (root_err != KModErr::OK) {
            return root_err;
        }
    }

    {
        std::string start_path, next_path, end_path;
        const KModErr path_error = detail_idle_mem_history::get_idle_mem_history_paths(start_path, next_path, end_path);
        if (path_error != KModErr::OK) return path_error;

        const bool has_start = file_utils::file_exists(std::filesystem::path(start_path));
        const bool has_next = file_utils::file_exists(std::filesystem::path(next_path));
        const bool has_end = file_utils::file_exists(std::filesystem::path(end_path));
        if ((has_start || has_next || has_end) && !(has_start && has_next && has_end))
            return KModErr::ERR_MODULE_READ_FILE;
        bool history_ok = true;
        if (has_start) {
            history_ok =
                detail_idle_mem_history::read_file_u64(start_path.c_str(),
                                                       start_addr) &&
                detail_idle_mem_history::read_file_u64(next_path.c_str(),
                                                       next_addr) &&
                detail_idle_mem_history::read_file_u64(end_path.c_str(), end_addr);
        }
        if (!history_ok) {
            return KModErr::ERR_MODULE_READ_FILE;
        }
    }

    if ((start_addr || next_addr || end_addr) && !(start_addr && next_addr && end_addr))
        return KModErr::ERR_MODULE_READ_FILE;
    if (!start_addr) {
        KModErr err = get_empty_area_start_addr(start_addr);
        if (err != KModErr::OK) {
            return err;
        }
        err = get_empty_area_end_addr(end_addr);
        if (err != KModErr::OK) {
            return err;
        }
        next_addr = start_addr;
        if (!start_addr || start_addr >= end_addr) return KModErr::ERR_MODULE_IDLE_MEM_UNKNOW;
        if ((start_addr | end_addr) & 3u) return KModErr::ERR_MODULE_ADDR_NOT_ALGIN4;

        const uint64_t page_size = get_page_size();
        if ((page_size != 4096 && page_size != 16384 && page_size != 65536) ||
            start_addr > UINT64_MAX - (page_size - 1))
            return KModErr::ERR_MODULE_IDLE_MEM_UNKNOW;
        // Permission changes and remaps must both stay inside the selected arena.
        start_addr = (start_addr + page_size - 1) & ~(page_size - 1);
        end_addr &= ~(page_size - 1);
        if (start_addr >= end_addr || size > end_addr - start_addr)
            return KModErr::ERR_MODULE_IDLE_MEM_NOT_ENOUGH;
        next_addr = start_addr;

        bool can_read = false;
        const KModErr read_err = is_kaddr_can_read(start_addr, can_read);
        if (read_err != KModErr::OK) return read_err;
        if (!can_read) {
            // trampoline 可执行页；失败立即返回（不写历史）。
            err = remap_kernel_exec_range(start_addr, end_addr - start_addr, end_addr);
            if (err != KModErr::OK) {
                return err;
            }
            exec_range_established = true;
        }
    }

    if (!start_addr || start_addr > next_addr || next_addr > end_addr)
        return KModErr::ERR_MODULE_IDLE_MEM_UNKNOW;
    if ((start_addr | next_addr | end_addr) & 3u) return KModErr::ERR_MODULE_ADDR_NOT_ALGIN4;
    // [next, end) is half-open: an exact final reservation is valid.
    if (size > end_addr - next_addr) {
        return KModErr::ERR_MODULE_IDLE_MEM_NOT_ENOUGH;
    }
    if (!exec_range_established) {
        const uint64_t page_size = get_page_size();
        if ((page_size != 4096 && page_size != 16384 && page_size != 65536) ||
            next_addr + size > UINT64_MAX - (page_size - 1))
            return KModErr::ERR_MODULE_IDLE_MEM_UNKNOW;
        const uint64_t page_start = next_addr & ~(page_size - 1);
        const uint64_t page_end = (next_addr + size + page_size - 1) & ~(page_size - 1);
        if (page_start < start_addr || page_end > end_addr)
            return KModErr::ERR_MODULE_IDLE_MEM_NOT_ENOUGH;
        const KModErr permission_error = protect_kernel_idle_exec_range(
            page_start, page_end - page_start);
        if (permission_error != KModErr::OK) return permission_error;
    }
    const uint64_t chosen_addr = start_addr;       // %621
    const uint64_t new_next_addr = next_addr + size;  // %622

    if (getuid() != 0) {
        const KModErr root_err = default_modules::module_root::set_current_root(true);
        if (root_err != KModErr::OK) {
            return root_err;
        }
    }

    const KModErr write_error = detail_idle_mem_history::write_history_files(
        chosen_addr, new_next_addr, end_addr);
    if (write_error != KModErr::OK) {
        return write_error;
    }

    out_addr = next_addr;
    return KModErr::OK;
}

}  // namespace kernel_module

KModErr maplesu::allocation_history::reset_idle_memory() {
    return kernel_module::detail_idle_mem_history::write_history_files(0, 0, 0);
}
