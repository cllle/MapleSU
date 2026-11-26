// module base kernel physical addr implementation.
//
// Everything here answers "what physical address / what kernel layout does
// this kernel use", by emitting a short AArch64 routine, running it in kernel
// mode, and interpreting the register it leaves behind.
//
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <functional>
#include <vector>

// The frozen member's symbol-lookup silent guard is the private helper class
// with the c67c0e suffix; the recovered header that the public inline helpers
// use spells the same class with the 8dfccc suffix.  Both are declared in
// module_base_kernel_physical_addr_private.h / the runtime-helper header, so no
// renaming is needed here.
#include "kernel_module_kit_umbrella.h"

#include "aarch64_asm_helper.h"

#include "module_base_kernel_physical_addr_private.h"

namespace kernel_module {
__attribute__((visibility("hidden"))) KModErr get_pa_width_mask(uint64_t& out);
__attribute__((visibility("hidden"))) KModErr get_dynamic_ttbr0_mask(uint64_t& out);
}  // namespace kernel_module

// Emits one AArch64 routine, runs it in kernel mode, and publishes the value
// its result register holds.  The routine is only finished when the emitter
// reported success, so a failed emit never reaches the kernel.  A zero result
// is the caller's "the routine could not produce a value" convention.
static KModErr get_kernel_shellcode_u64_result(
        std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> gen,
        uint64_t& out) {
    using namespace asmjit::a64;

    aarch64_asm_ctx ctx = init_aarch64_asm();
    Assembler* a = ctx.assembler();
    kernel_module::arm64_module_asm_func_start(a);

    GpX result_reg = x0;
    KModErr err = gen(a, result_reg);
    if (is_failed(err)) {
        return err;
    }
    kernel_module::arm64_module_asm_func_end(a, result_reg);
    return kernel_module::execute_kernel_asm_func(aarch64_asm_to_bytes(a), out);
}

namespace kernel_module {

KModErr get_pa_width_mask(uint64_t& out) {
    using namespace asmjit::a64;

    uint64_t mmfr0 = 0;
    KModErr err = get_kernel_shellcode_u64_result(
        [](Assembler* a, GpX& x) -> KModErr {
            aarch64_asm_mrs_id_aa64mmfr0_el1(a, x10);
            x = x10;
            return KModErr::OK;
        },
        mmfr0);
    if (is_failed(err)) {
        return err;
    }

    uint32_t bits = 48;
    switch (static_cast<uint32_t>(mmfr0) & 0xFu) {
        case 0: bits = 32; break;
        case 1: bits = 36; break;
        case 2: bits = 40; break;
        case 3: bits = 42; break;
        case 4: bits = 44; break;
        case 5: bits = 48; break;
        case 6: bits = 52; break;
        default: bits = 48; break;
    }
    out = ~(static_cast<uint64_t>(-1LL) << bits);
    return KModErr::OK;
}

KModErr get_dynamic_ttbr0_mask(uint64_t& out) {
    using namespace asmjit::a64;

    uint64_t pa_mask = 0;
    RETURN_IF_ERROR(get_pa_width_mask(pa_mask));

    uint64_t tcr = 0;
    RETURN_IF_ERROR(get_kernel_shellcode_u64_result(
        [](Assembler* a, GpX& x) -> KModErr {
            aarch64_asm_mrs_tcr_el1(a, x10);
            x = x10;
            return KModErr::OK;
        },
        tcr));

    const uint32_t tg1 = (static_cast<uint32_t>(tcr) >> 14) & 0x3u;
    const uint64_t granule_mask = (tg1 == 2) ? 0xFFFFFFFFFFFFC000ULL   // ~0x3FFF
                                : (tg1 == 1) ? 0xFFFFFFFFFFFF0000ULL  // ~0xFFFF
                                             : 0xFFFFFFFFFFFFF000ULL; // ~0xFFF
    out = pa_mask & granule_mask;
    return KModErr::OK;
}

KModErr get_current_pgd_phys(uint64_t& out) {
    using namespace asmjit::a64;

    uint64_t ttbr0 = 0;
    RETURN_IF_ERROR(get_kernel_shellcode_u64_result(
        [](Assembler* a, GpX& x) -> KModErr {
            aarch64_asm_mrs_ttbr0_el1(a, x10);
            x = x10;
            return KModErr::OK;
        },
        ttbr0));

    uint64_t mask = 0;
    RETURN_IF_ERROR(get_dynamic_ttbr0_mask(mask));

    out = ttbr0 & mask;
    return KModErr::OK;
}

// Translates one kernel virtual address into its physical address by walking
// the current page tables in hardware: with IRQs masked, AT S1E1R performs the
// translation for the given address, ISB orders it, and MRS PAR_EL1 returns
// the result.  Bit 0 of PAR_EL1 is the translation-fault flag.
//
// Addresses below 0xFF00000000000000 are not kernel virtual addresses and are
// reported as 0 without executing anything.  A faulted translation is reported
// as 0 as well; a successful one is masked with the dynamic TTBR0 page mask so
// only the physical frame number survives.
KModErr hardware_virt_to_phys(uint64_t virt_kaddr, uint64_t& result) {
    using namespace asmjit::a64;

    if (virt_kaddr < 0xFF00000000000000ULL) {
        result = 0;
        return KModErr::OK;
    }

    uint64_t par = 0;
    KModErr err = get_kernel_shellcode_u64_result(
        [virt_kaddr](Assembler* a, GpX& x) -> KModErr {
            export_symbol::local_irq_save(a, x10);
            aarch64_asm_mov_x(a, x11, virt_kaddr);
            aarch64_asm_at_s1e1r(a, x11);
            aarch64_asm_isb(a);
            aarch64_asm_mrs_par_el1(a, x12);
            x = x12;
            export_symbol::local_irq_restore(a, x10);
            return KModErr::OK;
        },
        par);
    if (is_failed(err)) {
        return err;
    }

    if (par & 1) {
        result = 0;
        return KModErr::OK;
    }

    uint64_t mask = 0;
    KModErr mask_err = get_dynamic_ttbr0_mask(mask);
    if (is_ok(mask_err)) {
        result = par & mask;
    }
    return mask_err;
}

// Reads the running kernel's current task pointer and derives PAGE_OFFSET from
// it: the task structure lives in the direct map, so
//     PAGE_OFFSET = task_virt - task_phys + physvirt_offset
// where physvirt_offset is the "physvirt_offset" variable address found through
// kallsyms (and read only through virt_to_phys' own hardware translation).
//
// Kernels older than 4.6.0 lay the two addends out in the opposite order, so
// both kernel generations are covered explicitly.
KModErr __get_PAGE_OFFSET(uint64_t& result) {
    using namespace asmjit::a64;

    SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8 silent_guard;

    kallsyms_lookup_name("physvirt_offset", result);

    uint64_t physvirt_offset = 0;
    RETURN_IF_ERROR(export_symbol::get_PHYS_OFFSET(physvirt_offset));

    uint64_t task_kaddr = 0;
    KModErr err = get_kernel_shellcode_u64_result(
        [](Assembler* a, GpX& x) -> KModErr {
            GpX current = x10;
            RETURN_IF_ERROR(export_symbol::get_current_checked(a, current));
            x = x10;
            return KModErr::OK;
        },
        task_kaddr);
    if (is_failed(err)) {
        return err;
    }

    uint64_t task_phys = 0;
    RETURN_IF_ERROR(export_symbol::virt_to_phys(task_kaddr, task_phys));

    if (is_kernel_version_less("4.6.0")) {
        result = (task_kaddr - task_phys) + physvirt_offset;
    } else {
        const uint64_t physvirt = physvirt_offset;
        result = (task_kaddr - task_phys) + physvirt;
    }
    return KModErr::OK;
}

// Calibrates the vmemmap base and the struct page size at run time.
//
// Two pages are vmalloc'd.  For each one the generated routine asks the kernel
// for its struct page pointer (vmalloc_to_page) and its page frame number
// (vmalloc_to_pfn).  The distance between the two struct page pointers divided
// by the distance between the two frame numbers is sizeof(struct page); then
//     vmemmap = page_ptr - struct_page_size * pfn
//
// Each generated routine reports through x0 and its error through the kit's
// KModErr return channel.  Zero address means the vmalloc call failed.  Both
// pages are released before the arithmetic so a failure never leaks them.
//
// Results are cached in atomics: the first successful calibration serves every
// later call.  Failure diagnostics are printed, and a calibration that would
// produce a zero vmemmap is rejected rather than cached.
KModErr calibrate_vmemmap_dynamic(uint64_t& out_vmemmap, uint64_t& out_struct_page_size) {
    using namespace asmjit::a64;

    static std::atomic<uint64_t> last_vmemmap{0};
    static std::atomic<uint64_t> last_struct_page_size{0};

    const uint64_t cached_vmemmap = last_vmemmap.load(std::memory_order_acquire);
    const uint64_t cached_struct_page_size =
            last_struct_page_size.load(std::memory_order_acquire);
    if (cached_vmemmap != 0 && cached_struct_page_size != 0) {
        out_vmemmap = cached_vmemmap;
        out_struct_page_size = cached_struct_page_size;
        return KModErr::OK;
    }

    // Results of the six kernel-side queries: two vmalloc addresses, their
    // frame numbers (vmalloc_to_pfn) and their struct page pointers
    // (vmalloc_to_page).
    uint64_t page_a = 0;
    uint64_t page_b = 0;
    uint64_t pfn_a = 0;
    uint64_t pfn_b = 0;
    uint64_t page_ptr_a = 0;
    uint64_t page_ptr_b = 0;

    // Allocates one page through the kernel's vmalloc().
    KModErr err = get_kernel_shellcode_u64_result(
            [](Assembler* a, GpX& x) -> KModErr {
                KModErr call_err = KModErr::OK;
                export_symbol::vmalloc(
                        a, call_err, static_cast<uint64_t>(kernel_module::get_page_size()));
                if (is_ok(call_err)) {
                    x = x0;
                }
                return call_err;
            },
            page_a);
    if (is_failed(err)) {
        return err;
    }
    if (page_a == 0) {
        return KModErr::ERR_MODULE_NOT_KADDR;
    }
    vmalloc_guard guard_a(page_a, 0);

    err = get_kernel_shellcode_u64_result(
            [](Assembler* a, GpX& x) -> KModErr {
                KModErr call_err = KModErr::OK;
                export_symbol::vmalloc(
                        a, call_err, static_cast<uint64_t>(kernel_module::get_page_size()));
                if (is_ok(call_err)) {
                    x = x0;
                }
                return call_err;
            },
            page_b);
    if (is_failed(err)) {
        return err;
    }
    if (page_b == 0) {
        return KModErr::ERR_MODULE_NOT_KADDR;
    }
    vmalloc_guard guard_b(page_b, 0);

    err = get_kernel_shellcode_u64_result(
            [page_a](Assembler* a, GpX& x) -> KModErr {
                KModErr call_err = KModErr::OK;
                export_symbol::vmalloc_to_page(a, call_err, page_a);
                if (is_ok(call_err)) {
                    x = x0;
                }
                return call_err;
            },
            page_ptr_a);
    if (is_failed(err)) {
        return err;
    }

    err = get_kernel_shellcode_u64_result(
            [page_a](Assembler* a, GpX& x) -> KModErr {
                KModErr call_err = KModErr::OK;
                export_symbol::vmalloc_to_pfn(a, call_err, page_a);
                if (is_ok(call_err)) {
                    x = x0;
                }
                return call_err;
            },
            pfn_a);
    if (is_failed(err)) {
        return err;
    }

    err = get_kernel_shellcode_u64_result(
            [page_b](Assembler* a, GpX& x) -> KModErr {
                KModErr call_err = KModErr::OK;
                export_symbol::vmalloc_to_page(a, call_err, page_b);
                if (is_ok(call_err)) {
                    x = x0;
                }
                return call_err;
            },
            page_ptr_b);
    if (is_failed(err)) {
        return err;
    }

    err = get_kernel_shellcode_u64_result(
            [page_b](Assembler* a, GpX& x) -> KModErr {
                KModErr call_err = KModErr::OK;
                export_symbol::vmalloc_to_pfn(a, call_err, page_b);
                if (is_ok(call_err)) {
                    x = x0;
                }
                return call_err;
            },
            pfn_b);
    if (is_failed(err)) {
        return err;
    }

    guard_a.reset(0, 0);
    guard_b.reset(0, 0);

    // Both pages are neighbours in the same vmalloc area; identical frame
    // numbers would make the division below meaningless.
    if (pfn_b == pfn_a) {
        std::puts("vmemmap delta_pfn is zero");
        return KModErr::ERR_MODULE_PARAM;
    }

    const int64_t delta_pfn = static_cast<int64_t>(pfn_b - pfn_a);
    const int64_t delta_page = static_cast<int64_t>(page_ptr_b - page_ptr_a);
    const uint64_t page_delta_abs = static_cast<uint64_t>(
            delta_pfn < 0 ? -delta_page : delta_page);
    const uint64_t struct_page_size =
            page_delta_abs / static_cast<uint64_t>(std::abs(delta_pfn));
    out_struct_page_size = struct_page_size;

    const uint64_t vmemmap = page_ptr_a - struct_page_size * pfn_a;
    out_vmemmap = vmemmap;

    if (page_ptr_a == struct_page_size * pfn_a) {
        std::puts("vmemmap is not kaddr");
        return KModErr::ERR_MODULE_NOT_KADDR;
    }

    last_vmemmap.store(vmemmap, std::memory_order_release);
    last_struct_page_size.store(out_struct_page_size, std::memory_order_release);
    return KModErr::OK;
}

}  // namespace kernel_module
