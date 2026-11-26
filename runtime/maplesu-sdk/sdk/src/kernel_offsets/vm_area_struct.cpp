// vm_area_struct implementation.
//
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/xattr.h>
#include <unistd.h>

#include <cstdint>
#include <filesystem>
#include <functional>
#include <sstream>
#include <string>
#include <vector>

#include "kernel_module_kit_umbrella.h"
#include "maplesu_kernel_layout_private.h"

#include "offset_probe_private.h"
#include "offsets_internal.h"
#include "private_module_tombstone_purge.h"
#include "private_maplesu_paths.h"

namespace kernel_module {

namespace {

// Kernel addresses are the pointer values at or above the top of the canonical
// user range; lower values are not kernel pointers.
constexpr uint64_t kKernelAddrMin = 0xFFFF000000000000ULL;

// Size of the anonymous mapping every vm_area_struct probe takes: one mapping
// yields one VMA that covers exactly this range.
constexpr size_t kVmaProbeMapLen = 16384;

KModErr resolve_vma_btf_offset(const char* member, uint32_t& offset, bool& available) {
    KernelFieldDescription field;
    KModErr error = maplesu_resolve_btf_field("vm_area_struct", member, 8, field, available);
    if (available && is_ok(error)) offset = field.offset_bytes;
    return error;
}

} // namespace

// Builds the path of the probe file with the given index: the module's data
// directory followed by a fixed name that differs per index.
static std::string get_temp_file_path(int index) {
    std::stringstream name_stream;
    name_stream << "temp" << index << "sP4yO3eH6dC9mI8hR8wD1lS4oK2vB7bT";
    const std::filesystem::path full_path =
        std::filesystem::path(maplesu::sdk::get_system_file_dir_path()) /
        std::filesystem::path(name_stream.str());
    return full_path.string();
}

// Locates vm_start and vm_end of struct vm_area_struct.  A 16 KiB anonymous
// mapping produces one VMA whose start and end are the mapping's first and
// one-past-last user addresses; the two aligned slots of that VMA holding those
// two addresses are the fields.  The address of the mapped page is found in the
// VMA first because the mapping call does not publish it.
KModErr get_vm_area_struct_vm_start_offset(uint32_t &vm_start_offset, uint32_t &vm_end_offset) {
    KernelFieldDescription start, end;
    bool available = false;
    KModErr btf_error = maplesu_resolve_btf_field("vm_area_struct", "vm_start", 8, start, available);
    if (available) {
        if (is_failed(btf_error)) return btf_error;
        bool end_available = false;
        btf_error = maplesu_resolve_btf_field("vm_area_struct", "vm_end", 8, end, end_available);
        if (is_failed(btf_error)) return btf_error;
        if (!end_available || start.owner_size_bytes != end.owner_size_bytes)
            return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
        vm_start_offset = start.offset_bytes;
        vm_end_offset = end.offset_bytes;
        return KModErr::OK;
    }

    uint32_t found_start_offset = 0;
    uint32_t found_end_offset = 0;
    KModErr err = get_offset_cache("vm_area_struct_vm_start", found_start_offset);
    if (is_ok(err)) {
        err = get_offset_cache("vm_area_struct_vm_end", found_end_offset);
        if (is_ok(err)) {
            vm_start_offset = found_start_offset;
            vm_end_offset = found_end_offset;
            return KModErr::OK; // both offsets already cached
        }
    }

    void *map_base =
        mmap(nullptr, kVmaProbeMapLen, PROT_READ | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (map_base == MAP_FAILED) {
        return KModErr::ERR_MODULE_MMAP;
    }
    mmap_guard map_guard(map_base, kVmaProbeMapLen);
    const uint64_t map_start = reinterpret_cast<uint64_t>(map_base);
    const uint64_t map_end = map_start + kVmaProbeMapLen;

    // The mm of the calling process: current->mm, taken and dropped around the
    // read so the reference held by get_task_mm is balanced.
    uint64_t mm_addr = 0;
    RETURN_IF_ERROR(get_kernel_shellcode_u64_result(
        [](asmjit::a64::Assembler *a, asmjit::a64::GpX &result) -> KModErr {
            KModErr call_err = KModErr::ERR_MODULE_ASM;
            RETURN_IF_ERROR(export_symbol::get_current_checked(a, asmjit::a64::regs::x10));
            export_symbol::get_task_mm(a, call_err, asmjit::a64::regs::x10);
            if (is_ok(call_err)) {
                export_symbol::mmput(a, call_err, asmjit::a64::regs::x0);
            }
            if (is_ok(call_err)) {
                result = asmjit::a64::regs::x0;
            }
            return call_err;
        },
        mm_addr));

    // The VMA of the mapped page.
    uint64_t vma_addr = 0;
    RETURN_IF_ERROR(get_kernel_shellcode_u64_result(
        [mm_addr, map_start](asmjit::a64::Assembler *a, asmjit::a64::GpX &result) -> KModErr {
            KModErr call_err = KModErr::ERR_MODULE_ASM;
            export_symbol::find_vma(a, call_err, mm_addr, map_start);
            if (is_ok(call_err)) {
                result = asmjit::a64::regs::x0;
            }
            return call_err;
        },
        vma_addr));

    bool found_start = false;
    bool found_end = false;
    for (uint64_t probe = 0; probe <= 255; probe += sizeof(uint32_t)) {
        uint64_t slot = 0;
        RETURN_IF_ERROR(read_kernel_mem(vma_addr + probe, &slot, sizeof(slot)));
        if (slot == map_start) {
            found_start_offset = static_cast<uint32_t>(probe);
            found_start = true;
        }
        if (slot == map_end) {
            found_end_offset = static_cast<uint32_t>(probe);
            found_end = true;
        }
    }

    if (!found_start || !found_end) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    RETURN_IF_ERROR(put_offset_cache("vm_area_struct_vm_start", found_start_offset));
    RETURN_IF_ERROR(put_offset_cache("vm_area_struct_vm_end", found_end_offset));
    vm_start_offset = found_start_offset;
    vm_end_offset = found_end_offset;
    return KModErr::OK;
}

// Locates the vm_mm field of struct vm_area_struct.  Every VMA carries the mm
// it belongs to, so for a VMA taken from the calling process the slot holding
// that mm pointer is vm_mm.  The comparison value is current->mm; the VMA is
// found with find_vma(), resolved by name and called through its address
// because this probe must also work when the exported wrapper is unavailable.
KModErr get_vm_area_struct_vm_mm_offset(uint32_t &offset) {
    bool available = false;
    KModErr btf_error = resolve_vma_btf_offset("vm_mm", offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("vm_area_struct_vm_mm", offset);
    if (is_ok(err)) {
        return KModErr::OK; // already cached
    }

    uint64_t mm_addr = 0;
    RETURN_IF_ERROR(get_kernel_shellcode_u64_result(
        [](asmjit::a64::Assembler *a, asmjit::a64::GpX &result) -> KModErr {
            KModErr call_err = KModErr::ERR_MODULE_ASM;
            RETURN_IF_ERROR(export_symbol::get_current_checked(a, asmjit::a64::regs::x10));
            export_symbol::get_task_mm(a, call_err, asmjit::a64::regs::x10);
            export_symbol::mmput(a, call_err, asmjit::a64::regs::x0);
            result = asmjit::a64::regs::x0;
            return KModErr::OK;
        },
        mm_addr));
    if (mm_addr < kKernelAddrMin) {
        return KModErr::ERR_MODULE_NOT_KADDR;
    }

    const uint32_t page_size = get_page_size();
    void *map_base =
        mmap(nullptr, page_size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (map_base == MAP_FAILED) {
        return KModErr::ERR_MODULE_MMAP;
    }
    mmap_guard map_guard(map_base, page_size);
    const uint64_t map_addr = reinterpret_cast<uint64_t>(map_base);

    uint64_t find_vma_addr = 0;
    err = kallsyms_lookup_name("find_vma", find_vma_addr);
    if (is_failed(err)) {
        return err;
    }
    if (find_vma_addr == 0) {
        return KModErr::ERR_MODULE_NOT_KADDR;
    }

    // find_vma(mm, map_addr) through its resolved address: x0/x1 carry the
    // arguments and x2 the call target.
    uint64_t vma_addr = 0;
    RETURN_IF_ERROR(get_kernel_shellcode_u64_result(
        [&mm_addr, &map_addr, &find_vma_addr](asmjit::a64::Assembler *a,
                                              asmjit::a64::GpX &result) -> KModErr {
            aarch64_asm_mov_x(a, asmjit::a64::regs::x0, mm_addr);
            aarch64_asm_mov_x(a, asmjit::a64::regs::x1, map_addr);
            aarch64_asm_mov_x(a, asmjit::a64::regs::x2, find_vma_addr);
            a->blr(asmjit::a64::regs::x2);
            result = asmjit::a64::regs::x0;
            return KModErr::OK;
        },
        vma_addr));
    if (vma_addr < kKernelAddrMin) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }

    // The mm slot is the only one equal to current->mm.
    uint64_t match_count = 0;
    uint32_t mm_offset = 0;
    for (uint64_t probe = 0; probe < 256; probe += sizeof(uint64_t)) {
        uint64_t slot = 0;
        RETURN_IF_ERROR(read_kernel_mem(vma_addr + probe, &slot, sizeof(slot)));
        const bool is_mm = (slot == mm_addr);
        match_count += is_mm ? 1 : 0;
        if (is_mm) {
            mm_offset = static_cast<uint32_t>(probe);
        }
    }
    if (match_count != 1) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }

    offset = mm_offset;
    return put_offset_cache("vm_area_struct_vm_mm", mm_offset);
}

// Locates the vm_flags field of struct vm_area_struct.  The VMA of one page is
// read three times while the protection of that page changes: read-only, then
// read-write, then read-write-execute.  vm_flags is the only field that gains
// the write bit on the first mprotect and the execute bit on the second.
KModErr get_vm_area_struct_vm_flags_offset(uint32_t &offset) {
    bool available = false;
    KModErr btf_error = resolve_vma_btf_offset("vm_flags", offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("vm_area_struct_vm_flags", offset);
    if (is_ok(err)) {
        return KModErr::OK; // already cached
    }

    void *map_base = mmap(nullptr, kVmaProbeMapLen, PROT_READ, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (map_base == MAP_FAILED) {
        return KModErr::ERR_MODULE_MMAP;
    }
    mmap_guard map_guard(map_base, kVmaProbeMapLen);

    // current->mm, held for the whole probe because every VMA lookup needs it.
    uint64_t mm_addr = 0;
    RETURN_IF_ERROR(get_kernel_shellcode_u64_result(
        [](asmjit::a64::Assembler *a, asmjit::a64::GpX &result) -> KModErr {
            KModErr call_err = KModErr::ERR_MODULE_ASM;
            RETURN_IF_ERROR(export_symbol::get_current_checked(a, asmjit::a64::regs::x10));
            export_symbol::get_task_mm(a, call_err, asmjit::a64::regs::x10);
            if (is_ok(call_err)) {
                export_symbol::mmput(a, call_err, asmjit::a64::regs::x0);
            }
            if (is_ok(call_err)) {
                result = asmjit::a64::regs::x0;
            }
            return call_err;
        },
        mm_addr));

    // Re-reads the VMA of the mapped page; the VMA address itself is not
    // reused across the protection changes because the tree is walked again.
    auto read_vma = [&mm_addr, &map_base](uint64_t &out_vma) -> KModErr {
        return get_kernel_shellcode_u64_result(
            [mm_addr, map_base](asmjit::a64::Assembler *a, asmjit::a64::GpX &result) -> KModErr {
                KModErr call_err = KModErr::ERR_MODULE_ASM;
                export_symbol::find_vma(a, call_err, mm_addr, reinterpret_cast<uint64_t>(map_base));
                if (is_ok(call_err)) {
                    result = asmjit::a64::regs::x0;
                }
                return call_err;
            },
            out_vma);
    };

    std::vector<uint8_t> snapshot_read_only(256);
    std::vector<uint8_t> snapshot_read_write(256);
    std::vector<uint8_t> snapshot_read_write_exec(256);

    uint64_t vma_addr = 0;
    RETURN_IF_ERROR(read_vma(vma_addr));
    RETURN_IF_ERROR(read_kernel_mem(vma_addr, snapshot_read_only.data(), 256));

    mprotect(map_base, kVmaProbeMapLen, PROT_READ | PROT_WRITE);
    RETURN_IF_ERROR(read_vma(vma_addr));
    RETURN_IF_ERROR(read_kernel_mem(vma_addr, snapshot_read_write.data(), 256));

    mprotect(map_base, kVmaProbeMapLen, PROT_READ | PROT_WRITE | PROT_EXEC);
    RETURN_IF_ERROR(read_vma(vma_addr));
    RETURN_IF_ERROR(read_kernel_mem(vma_addr, snapshot_read_write_exec.data(), 256));

    // The mapping is released before the snapshots are compared; only their
    // copies are needed from here on.
    map_guard.reset(MAP_FAILED, 0);

    std::vector<uint32_t> candidates;
    for (uint64_t probe = 0; probe < 256; probe += sizeof(uint64_t)) {
        const uint64_t read_only =
            *reinterpret_cast<const uint64_t *>(snapshot_read_only.data() + probe);
        const uint64_t read_write =
            *reinterpret_cast<const uint64_t *>(snapshot_read_write.data() + probe);
        const uint64_t read_write_exec =
            *reinterpret_cast<const uint64_t *>(snapshot_read_write_exec.data() + probe);
        if (read_only == read_write && read_write == read_write_exec) {
            continue;
        }
        // The write bit must appear with the first change and be absent before.
        if ((read_write & 0x2) == 0 || (read_only & 0x2) != 0) {
            continue;
        }
        // The execute bit must appear with the second change only.
        if ((read_write_exec & 0x4) == 0 || (read_write & 0x4) != 0) {
            continue;
        }
        candidates.push_back(static_cast<uint32_t>(probe));
    }

    if (candidates.size() != 1) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    offset = candidates[0];
    return put_offset_cache("vm_area_struct_vm_flags", candidates[0]);
}

// Locates the vm_file field of struct vm_area_struct.  A file mapping keeps a
// reference to the mapped file, so for a VMA backed by the probe file the slot
// holding that file's struct file pointer is vm_file.  The expected pointer is
// taken from the caller's own descriptor table.
KModErr get_vm_area_struct_vm_file_offset(uint32_t &offset) {
    bool available = false;
    KModErr btf_error = resolve_vma_btf_offset("vm_file", offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("vm_area_struct_vm_file", offset);
    if (is_ok(err)) {
        return KModErr::OK; // already cached
    }

    // Two probe files: the first is mapped and compared, the second keeps the
    // first one from being the only file mapping opened by the caller.
    for (uint64_t index = 0; index != 2; ++index) {
        const std::string file_path = get_temp_file_path(static_cast<int>(index));
        fd_guard file_fd(open(file_path.c_str(), O_RDWR | O_CREAT | O_TRUNC, 0666));
        if (file_fd.get() < 0) {
            return KModErr::ERR_MODULE_CREATE_EMPTY_FILE;
        }
        if (ftruncate(file_fd.get(), 4660 + index) != 0) {
            return KModErr::ERR_MODULE_WRITE_FILE;
        }
        if (chmod(file_path.c_str(), 0777) != 0) {
            return KModErr::ERR_SET_FILE_SELINUX;
        }
        if (setxattr(file_path.c_str(), "security.selinux", "u:object_r:system_file:s0", 26, 0) !=
            0) {
            return KModErr::ERR_SET_FILE_SELINUX;
        }
    }

    const std::string map_file_path = get_temp_file_path(0);
    fd_guard map_file_fd(open(map_file_path.c_str(), O_RDONLY));
    if (map_file_fd.get() < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    void *map_base = mmap(nullptr, kVmaProbeMapLen, PROT_READ, MAP_SHARED, map_file_fd.get(), 0);
    if (map_base == MAP_FAILED) {
        return KModErr::ERR_MODULE_MMAP;
    }
    mmap_guard map_guard(map_base, kVmaProbeMapLen);

    uint64_t file_ptr = 0;
    err = get_current_task_file_ptr(map_file_fd.get(), file_ptr);
    if (is_failed(err)) {
        return err;
    }

    uint64_t mm_addr = 0;
    RETURN_IF_ERROR(get_kernel_shellcode_u64_result(
        [](asmjit::a64::Assembler *a, asmjit::a64::GpX &result) -> KModErr {
            KModErr call_err = KModErr::ERR_MODULE_ASM;
            RETURN_IF_ERROR(export_symbol::get_current_checked(a, asmjit::a64::regs::x10));
            export_symbol::get_task_mm(a, call_err, asmjit::a64::regs::x10);
            if (is_ok(call_err)) {
                export_symbol::mmput(a, call_err, asmjit::a64::regs::x0);
            }
            if (is_ok(call_err)) {
                result = asmjit::a64::regs::x0;
            }
            return call_err;
        },
        mm_addr));

    uint64_t vma_addr = 0;
    RETURN_IF_ERROR(get_kernel_shellcode_u64_result(
        [mm_addr, map_base](asmjit::a64::Assembler *a, asmjit::a64::GpX &result) -> KModErr {
            KModErr call_err = KModErr::ERR_MODULE_ASM;
            export_symbol::find_vma(a, call_err, mm_addr, reinterpret_cast<uint64_t>(map_base));
            if (is_ok(call_err)) {
                result = asmjit::a64::regs::x0;
            }
            return call_err;
        },
        vma_addr));

    // The file pointer is the only slot equal to the mapped file.
    offset = 0;
    for (uint64_t probe = 0; probe < 256; probe += sizeof(uint32_t)) {
        uint64_t slot = 0;
        RETURN_IF_ERROR(read_kernel_mem(vma_addr + probe, &slot, sizeof(slot)));
        if (slot == file_ptr) {
            offset = static_cast<uint32_t>(probe);
            break;
        }
    }
    if (offset == 0) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    return put_offset_cache("vm_area_struct_vm_file", offset);
}

} // namespace kernel_module
