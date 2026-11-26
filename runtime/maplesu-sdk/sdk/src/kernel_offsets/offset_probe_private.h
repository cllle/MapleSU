// Private probe helpers shared by the kernel-offset resolvers.
//
// Every offset resolver in this directory works the same way: it finds a kernel
// object address, reads the structure it is interested in, and matches known
// values against every aligned slot until the field is located.  The helpers
// below provide that loop once: emit an AArch64 routine, execute it inside the
// running kernel, read a kernel buffer safely, and own the kernel pages and user
// mappings that back a probe.
//
#pragma once

#include <stddef.h>
#include <stdint.h>
#include <sys/mman.h>

#include <functional>
#include <string>
#include <vector>

#include "kernel_module_kit_umbrella.h"
#include "private_module_tombstone_purge.h"

// --------------------------------------------------------------------------
// Kernel routine execution
// --------------------------------------------------------------------------

// Runs one emitted AArch64 routine in the kernel and drops its X0 result.
// An emitter failure returns before the routine epilogue is emitted, so an
// unbalanced prologue is never executed.
[[maybe_unused]] static KModErr exec_kernel_shellcode_no_result(
        std::function<KModErr(asmjit::a64::Assembler*)> emitter) {
    aarch64_asm_ctx asm_ctx = init_aarch64_asm();
    asmjit::a64::Assembler* a = asm_ctx.assembler();
    kernel_module::arm64_module_asm_func_start(a);

    KModErr err = emitter(a);
    if (is_failed(err)) {
        return err;
    }
    kernel_module::arm64_module_asm_func_end(a);

    std::vector<uint8_t> func_bytes = aarch64_asm_to_bytes(a);
    uint64_t output_result = 0;
    return kernel_module::execute_kernel_asm_func(func_bytes, output_result);
}

// Runs one emitted AArch64 routine in the kernel and publishes the X0 value of
// the register the emitter selected as the routine result.  The routine is
// only finished when the emitter succeeded, so a failed emitter never reaches
// the kernel.
[[maybe_unused]] static KModErr get_kernel_shellcode_u64_result(
        std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emitter,
        uint64_t& out_result) {
    aarch64_asm_ctx asm_ctx = init_aarch64_asm();
    asmjit::a64::Assembler* a = asm_ctx.assembler();
    kernel_module::arm64_module_asm_func_start(a);

    asmjit::a64::GpX result_reg = asmjit::a64::regs::x0;
    RETURN_IF_ERROR(emitter(a, result_reg));
    kernel_module::arm64_module_asm_func_end(a, result_reg);

    std::vector<uint8_t> func_bytes = aarch64_asm_to_bytes(a);
    return kernel_module::execute_kernel_asm_func(func_bytes, out_result);
}

// --------------------------------------------------------------------------
// Safe kernel reads
// --------------------------------------------------------------------------

// Copies size bytes from kaddr into out_buf through a kernel page that is
// filled by a nofault copy executed in the kernel itself, so an unreadable
// source never faults the caller.  out_ok reports whether the copy succeeded;
// the returned status reports the setup/read/free failure, if any.
[[maybe_unused]] static KModErr safe_read_kaddr(uint64_t kaddr, bool& out_ok,
                                                void* out_buf, size_t size) {
    out_ok = false;
    if (out_buf == nullptr) {
        return KModErr::ERR_MODULE_PARAM;
    }

    uint64_t page_kaddr = 0;
    KModErr err = kernel_module::alloc_kernel_mem(static_cast<uint32_t>(size), page_kaddr);
    if (is_failed(err)) {
        return err;
    }

    uint64_t copy_result = 0;
    KModErr asm_err = get_kernel_shellcode_u64_result(
            [kaddr, page_kaddr, size](asmjit::a64::Assembler* a,
                                      asmjit::a64::GpX& result) -> KModErr {
                KModErr call_err = KModErr::ERR_MODULE_PARAM;
                aarch64_asm_mov_x(a, asmjit::a64::regs::x10, page_kaddr);
                aarch64_asm_mov_x(a, asmjit::a64::regs::x11, kaddr);
                kernel_module::export_symbol::copy_from_kernel_nofault(
                        a, call_err, asmjit::a64::regs::x10, asmjit::a64::regs::x11, size);
                if (is_ok(call_err)) {
                    result = asmjit::a64::regs::x0;
                }
                return call_err;
            },
            copy_result);

    if (is_ok(asm_err)) {
        const KModErr read_err = kernel_module::read_kernel_mem(
                page_kaddr, out_buf, static_cast<uint32_t>(size));
        if (is_failed(read_err)) {
            kernel_module::free_kernel_mem(page_kaddr);
            return read_err;
        }
    }

    const KModErr free_err = kernel_module::free_kernel_mem(page_kaddr);
    if (is_failed(free_err)) {
        return free_err;
    }
    if (is_failed(asm_err)) {
        return asm_err;
    }
    out_ok = (copy_result == 0);
    return KModErr::OK;
}

// Typed form of safe_read_kaddr() for one trivially copyable value.
template <typename T>
[[maybe_unused]] static KModErr safe_read_kaddr_t(uint64_t kaddr, bool& out_ok, T& out) {
    return safe_read_kaddr(kaddr, out_ok, &out, sizeof(T));
}

// --------------------------------------------------------------------------
// Process file table walking
// --------------------------------------------------------------------------

namespace {

// Resolves the struct file pointer the caller's process holds for fd:
// current->files->fdt->fd[fd].  The pointer is published in out_file_ptr and a
// zero pointer means the descriptor had no file (or the walk failed).
[[maybe_unused]] static KModErr get_current_task_file_ptr(int fd, uint64_t& out_file_ptr) {
    uint64_t current_task = 0;
    KModErr err = get_kernel_shellcode_u64_result(
            [](asmjit::a64::Assembler* a, asmjit::a64::GpX& result) -> KModErr {
                result = asmjit::a64::regs::x10;
                RETURN_IF_ERROR(kernel_module::export_symbol::get_current_checked(a, result));
                return KModErr::OK;
            },
            current_task);
    if (is_failed(err)) {
        return err;
    }

    uint32_t files_offset = 0;
    err = kernel_module::get_task_struct_files_offset(files_offset);
    if (is_failed(err)) {
        return err;
    }
    uint64_t files = 0;
    err = kernel_module::read_kernel_mem(current_task + files_offset, &files, sizeof(files));
    if (is_failed(err)) {
        return err;
    }

    uint32_t fdt_offset = 0;
    err = kernel_module::get_file_struct_fdt_offset(fdt_offset);
    if (is_failed(err)) {
        return err;
    }
    uint64_t fdt = 0;
    err = kernel_module::read_kernel_mem(files + fdt_offset, &fdt, sizeof(fdt));
    if (is_failed(err)) {
        return err;
    }

    uint32_t fd_offset = 0;
    err = kernel_module::get_fdtable_fd_offset(fd_offset);
    if (is_failed(err)) {
        return err;
    }
    uint64_t fd_array = 0;
    err = kernel_module::read_kernel_mem(fdt + fd_offset, &fd_array, sizeof(fd_array));
    if (is_failed(err)) {
        return err;
    }

    uint64_t file_ptr = 0;
    err = kernel_module::read_kernel_mem(
            fd_array + static_cast<uint64_t>(fd) * sizeof(uint64_t), &file_ptr, sizeof(file_ptr));
    if (is_failed(err)) {
        return err;
    }
    out_file_ptr = file_ptr;
    return KModErr::OK;
}

}  // namespace

// --------------------------------------------------------------------------
// Path acquisition
// --------------------------------------------------------------------------

#include "kpath_owner_private.h"

// --------------------------------------------------------------------------
// Random names
// --------------------------------------------------------------------------

namespace random_utils {

// Builds a random alphanumeric string of len characters drawn uniformly from
// "a-z A-Z 0-9", used to create probe entries with a name unlikely to collide.
[[maybe_unused]] inline std::string generate_random_str(size_t len) {
    if (len == 0) {
        return std::string();
    }
    static thread_local std::uniform_int_distribution<size_t> dist(0, 61);
    static const char alphabet[] =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    std::string out;
    out.reserve(len);
    for (size_t i = 0; i < len; ++i) {
        out.push_back(alphabet[dist(detail::mt19937_64())]);
    }
    return out;
}

}  // namespace random_utils

// --------------------------------------------------------------------------
// Owning guards
// --------------------------------------------------------------------------

// Owns one kernel allocation: holds the kernel address and its size, releases
// the allocation on reset(0, 0) and on destruction, and ignores a zero address
// so a failed allocation needs no special case at the call site.
struct maplesu_alloc_mem_guard {
    uint64_t kaddr_ = 0;
    uint64_t size_ = 0;

    maplesu_alloc_mem_guard() = default;
    maplesu_alloc_mem_guard(uint64_t kaddr, uint64_t size) { reset(kaddr, size); }

    static KModErr kernel_vfree(uint64_t kaddr) {
        if (kaddr == 0) {
            return KModErr::OK;
        }
        return kernel_module::free_kernel_mem(kaddr);
    }

    void reset(uint64_t kaddr, uint64_t size) {
        if (kaddr_ != 0) {
            const uint64_t old_kaddr = kaddr_;
            kaddr_ = 0;
            size_ = 0;
            kernel_vfree(old_kaddr);
        }
        if (kaddr != 0) {
            kaddr_ = kaddr;
            size_ = size;
        }
    }

    ~maplesu_alloc_mem_guard() { reset(0, 0); }
};

// Owns one user mapping: unmaps the previous range before taking a new one and
// treats MAP_FAILED / a zero length as "nothing owned".
struct mmap_guard {
    void* addr_ = MAP_FAILED;
    size_t size_ = 0;

    mmap_guard() = default;
    mmap_guard(void* addr, size_t size) {
        addr_ = MAP_FAILED;
        size_ = 0;
        reset(addr, size);
    }

    // Unmaps the previous range before taking a new one and treats
    // MAP_FAILED / a zero length as "nothing owned".
    void reset(void* addr, size_t size) {
        const bool owns_mapping = addr_ != MAP_FAILED && size_ != 0;
        if (owns_mapping) {
            munmap(addr_, size_);
        }
        const bool nothing_owned = (addr == MAP_FAILED) || (size == 0);
        addr_ = nothing_owned ? MAP_FAILED : addr;
        size_ = nothing_owned ? 0 : size;
    }

    ~mmap_guard() { reset(MAP_FAILED, 0); }
};
