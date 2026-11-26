// mm_struct implementation.
//
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

#include <cstdint>
#include <functional>
#include <vector>

#include "kernel_module_kit_umbrella.h"
#include "maplesu_kernel_layout_private.h"

#include "offsets_internal.h"
#include "private_module_tombstone_purge.h"

// --------------------------------------------------------------------------
// Kernel routine execution
// --------------------------------------------------------------------------

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

namespace kernel_module {

// Page-global-directory helpers provided by the kernel physical-address module.
KModErr get_current_pgd_phys(uint64_t& out);
KModErr get_dynamic_ttbr0_mask(uint64_t& out);

// Reads the auxiliary vector of the calling process as alternating type/value
// words, stopping at the terminating entry or at a short read.
static std::vector<uint64_t> get_auxv_signature();

// Reads the start address of the first mapping of the calling process.
static uint64_t get_first_vma_start();

// Reads the total number of mapped pages of the calling process.
static uint64_t get_statm_total_pages();

// Counts the lines of /proc/self/maps, or -1 when the file cannot be read.
static int count_maps_lines();

// Samples the mm_struct three times around one probe mapping and reports the
// slot that holds an rss counter.
static KModErr probe_rss_stat_window(uint64_t window_size, uint32_t& offset);

// Copies the current process's struct mm_struct out of the kernel into
// out_buf.  The current task is looked up, its mm reference is taken,
// released again and the still-valid mm pointer is read out of the routine.
static KModErr snapshot_current_mm_struct(char* out_buf, int size);

// Locates the pgd field by walking every aligned slot of the current
// mm_struct, translating each kernel pointer it holds and comparing the
// resulting physical page with the physical page the live pgd lives on.
static KModErr find_pgd_offset_via_at_instruction(uint64_t pgd_phys, uint32_t& offset);

// Reads /proc/self/statm once so that the resident counters of the current
// process are refreshed before the mm_struct is sampled.
static void force_rss_sync();

static KModErr resolve_mm_btf_offset(const char* member, uint32_t width,
                                    uint32_t& offset, bool& available) {
    KernelFieldDescription field;
    KModErr error = maplesu_resolve_btf_field("mm_struct", member, width, field, available);
    if (available && is_ok(error)) offset = field.offset_bytes;
    return error;
}

// Locates the mm_mt field: the maple tree root is the only pointer slot of the
// mm_struct that changes while one mapping is repeatedly split, and its leaf
// node is still readable through the untagged pointer.
KModErr get_mm_struct_mm_mt_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_mm_btf_offset("mm_mt", 0, offset, available);
    if (available) return btf_error;

    if (is_kernel_version_less("6.1.0")) {
        return KModErr::ERR_MODULE_OFFSET_NOT_MATCH_LINUX_VER;
    }

    KModErr err = get_offset_cache("mm_struct_mm_mt", offset);
    if (is_ok(err)) {
        return KModErr::OK;
    }

    std::vector<char> before(1024);
    std::vector<char> after(1024);
    err = snapshot_current_mm_struct(before.data(), 1024);
    if (is_failed(err)) {
        return err;
    }

    const uint32_t page_size = get_page_size();
    std::vector<void*> mappings;
    for (int i = 0; i != 300; ++i) {
        void* mapping = mmap(nullptr, page_size * 2, PROT_READ,
                             MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        if (mapping == MAP_FAILED) {
            continue;
        }
        munmap(static_cast<char*>(mapping) + page_size, page_size);
        mappings.push_back(mapping);
    }

    err = snapshot_current_mm_struct(after.data(), 1024);
    for (void* mapping : mappings) {
        munmap(mapping, page_size);
    }
    if (is_failed(err)) {
        return err;
    }

    int match_count = 0;
    uint32_t found_offset = 0;
    for (uint64_t i = 8; i < 1016; i += 8) {
        const uint64_t old_value = *reinterpret_cast<uint64_t*>(before.data() + i);
        const uint64_t new_value = *reinterpret_cast<uint64_t*>(after.data() + i);
        if (old_value == new_value) {
            continue;
        }
        if (old_value != 0 && old_value < 0xFFFF000000000000ULL) {
            continue;
        }
        if (new_value < 0xFFFF000000000000ULL) {
            continue;
        }
        if ((new_value & 7) == 0) {
            continue;
        }
        if (old_value != 0 && (old_value & 7) == 0) {
            continue;
        }
        uint64_t node_value = 0;
        bool read_ok = false;
        err = safe_read_kaddr_t<uint64_t>(new_value & ~7ULL, read_ok, node_value);
        if (is_failed(err)) {
            return err;
        }
        match_count += read_ok;
        found_offset = read_ok ? static_cast<uint32_t>(i) : found_offset;
    }

    if (match_count != 1) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    offset = found_offset - 8;
    return put_offset_cache("mm_struct_mm_mt", offset);
}

KModErr get_mm_struct_mmap_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_mm_btf_offset("mmap", 8, offset, available);
    if (available) return btf_error;

    if (!is_kernel_version_less("6.1.0")) {
        return KModErr::ERR_MODULE_OFFSET_NOT_MATCH_LINUX_VER;
    }

    KModErr err = get_offset_cache("mm_struct_mmap", offset);
    if (is_ok(err)) {
        return KModErr::OK;
    }

    const uint64_t first_vma_start = get_first_vma_start();
    if (first_vma_start == 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    const uint64_t probe_addr = first_vma_start - 4096;

    std::vector<char> before(512);
    std::vector<char> during(512);
    std::vector<char> after(512);
    RETURN_IF_ERROR(snapshot_current_mm_struct(before.data(), 512));

    void* mapping = mmap(reinterpret_cast<void*>(probe_addr), 4096, PROT_READ,
                         MAP_PRIVATE | MAP_FIXED | MAP_ANONYMOUS, -1, 0);
    if (mapping == MAP_FAILED) {
        return KModErr::ERR_MODULE_PARAM;
    }

    err = snapshot_current_mm_struct(during.data(), 512);
    munmap(mapping, 4096);
    if (is_failed(err)) {
        return err;
    }
    RETURN_IF_ERROR(snapshot_current_mm_struct(after.data(), 512));

    int match_count = 0;
    uint32_t found_offset = 0;
    for (uint64_t i = 0; i < 512; i += 8) {
        const uint64_t old_value = *reinterpret_cast<uint64_t*>(before.data() + i);
        const uint64_t changed_value = *reinterpret_cast<uint64_t*>(during.data() + i);
        const uint64_t restored_value = *reinterpret_cast<uint64_t*>(after.data() + i);
        if (old_value != restored_value) {
            continue;
        }
        if (old_value == changed_value) {
            continue;
        }
        if (old_value < 0xFFFF000000000000ULL || changed_value < 0xFFFF000000000000ULL) {
            continue;
        }
        uint64_t vma_start = 0;
        bool read_ok = false;
        err = safe_read_kaddr_t<uint64_t>(old_value, read_ok, vma_start);
        if (is_failed(err)) {
            return err;
        }
        const bool matched = read_ok && (vma_start == first_vma_start);
        match_count += matched;
        found_offset = matched ? static_cast<uint32_t>(i) : found_offset;
    }

    if (match_count != 1) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    offset = found_offset;
    return put_offset_cache("mm_struct_mmap", offset);
}

KModErr get_mm_struct_pgd_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_mm_btf_offset("pgd", 8, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("mm_struct_pgd", offset);
    if (is_ok(err)) {
        return KModErr::OK;
    }

    uint64_t pgd_phys = 0;
    err = get_current_pgd_phys(pgd_phys);
    if (is_failed(err)) {
        return err;
    }

    err = find_pgd_offset_via_at_instruction(pgd_phys, offset);
    if (is_failed(err)) {
        return err;
    }
    return put_offset_cache("mm_struct_pgd", offset);
}

KModErr get_mm_struct_map_count_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_mm_btf_offset("map_count", 4, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("mm_struct_map_count", offset);
    if (is_ok(err)) {
        return KModErr::OK;
    }

    char before[300] = {0};
    char after[300] = {0};
    RETURN_IF_ERROR(snapshot_current_mm_struct(before, sizeof(before)));

    const uint32_t page_size = get_page_size();
    const uint64_t large_size = page_size * 11;
    const uint64_t tail_offset = page_size * 10;
    void* mappings[5] = {};
    size_t mapping_count = 0;
    for (int i = 0; i != 5; ++i) {
        void* mapping = mmap(nullptr, large_size, PROT_READ | PROT_WRITE,
                             MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        if (mapping == MAP_FAILED) {
            continue;
        }
        mappings[mapping_count++] = mapping;
        munmap(static_cast<char*>(mapping) + tail_offset, page_size);
    }

    err = snapshot_current_mm_struct(after, sizeof(after));
    const int maps_line_count = is_ok(err) ? count_maps_lines() : -1;
    for (size_t i = 0; i < mapping_count; ++i) {
        munmap(mappings[i], large_size);
    }
    if (is_failed(err)) {
        return err;
    }

    // The number of lines of /proc/self/maps is the current VMA count, so it
    // ranks the candidate counters that are close to it.
    const bool have_line_hint = maps_line_count > 0;

    int best_score = -1;
    uint32_t found_offset = 0;
    for (uint64_t i = 0; i < 300; i += 4) {
        const int32_t old_value = *reinterpret_cast<int32_t*>(before + i);
        const int32_t new_value = *reinterpret_cast<int32_t*>(after + i);
        const int32_t counter_delta = new_value - old_value;
        if (counter_delta > 0 && old_value < 1000000 && new_value < 1000000) {
            int score = 0;
            switch (counter_delta) {
                case 5:
                    score = 50;
                    break;
                case 50:
                    score = -100;
                    break;
                default:
                    score = (counter_delta >= 4 && counter_delta <= 6) ? 20 : 0;
                    break;
            }

            if (have_line_hint) {
                const uint32_t line_distance =
                        std::abs(old_value - maps_line_count);
                if (line_distance < 10) {
                    score += 50;
                } else if (line_distance < 30) {
                    score += 30;
                } else {
                    score -= 50;
                }
            }

            const bool better = (score > 0) && (score > best_score);
            best_score = better ? score : best_score;
            found_offset = better ? static_cast<uint32_t>(i) : found_offset;
        }
    }
    offset = found_offset;
    if (found_offset == 0) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    return put_offset_cache("mm_struct_map_count", found_offset);
}

KModErr get_mm_struct_total_vm_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_mm_btf_offset("total_vm", 8, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("mm_struct_total_vm", offset);
    if (is_ok(err)) {
        return KModErr::OK;
    }

    // The first field of /proc/self/statm is the current total_vm value in
    // pages, which is the anchor for the candidate total_vm slots.
    const uint64_t statm_pages = get_statm_total_pages();
    if (statm_pages == 0) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }

    std::vector<char> before(1024);
    std::vector<char> during(1024);
    std::vector<char> after(1024);
    RETURN_IF_ERROR(snapshot_current_mm_struct(before.data(), 1024));

    const uint32_t page_size = get_page_size();
    const size_t probe_length = page_size * 1000;
    void* mapping = mmap(nullptr, probe_length, PROT_READ | PROT_WRITE,
                         MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (mapping == MAP_FAILED) {
        return KModErr::ERR_MODULE_MMAP;
    }

    err = snapshot_current_mm_struct(during.data(), 1024);
    munmap(mapping, probe_length);
    if (is_failed(err)) {
        return err;
    }
    RETURN_IF_ERROR(snapshot_current_mm_struct(after.data(), 1024));

    int match_count = 0;
    uint32_t found_offset = 0;
    for (uint64_t i = 0; i < 1024; i += 8) {
        const uint64_t old_pages = *reinterpret_cast<uint64_t*>(before.data() + i);
        const uint64_t mapped_pages = *reinterpret_cast<uint64_t*>(during.data() + i);
        const uint64_t restored_pages = *reinterpret_cast<uint64_t*>(after.data() + i);
        if (mapped_pages - old_pages != 1000) {
            continue;
        }
        if (mapped_pages - restored_pages != 1000) {
            continue;
        }
        const int64_t statm_delta = static_cast<int64_t>(old_pages) -
                                    static_cast<int64_t>(statm_pages);
        const bool matched = std::abs(statm_delta) < 5;
        match_count += matched;
        found_offset = matched ? static_cast<uint32_t>(i) : found_offset;
    }

    if (match_count != 1) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    offset = found_offset;
    return put_offset_cache("mm_struct_total_vm", offset);
}

// Reads the start address of the first mapping of the calling process.
static uint64_t get_first_vma_start() {
    FILE* maps_file = fopen("/proc/self/maps", "r");
    if (maps_file == nullptr) {
        return 0;
    }
    char line[256];
    uint64_t first_vma_start = 0;
    if (fgets(line, sizeof(line), maps_file) != nullptr) {
        sscanf(line, "%lx-", &first_vma_start);
    }
    fclose(maps_file);
    return first_vma_start;
}

// Reads the total number of mapped pages of the calling process.
static uint64_t get_statm_total_pages() {
    FILE* statm_file = fopen("/proc/self/statm", "r");
    if (statm_file == nullptr) {
        return 0;
    }
    uint64_t statm_pages = 0;
    if (fscanf(statm_file, "%lu", &statm_pages) != 1) {
        statm_pages = 0;
    }
    fclose(statm_file);
    return statm_pages;
}

// Counts the lines of /proc/self/maps, or -1 when the file cannot be read.
static int count_maps_lines() {
    FILE* maps_file = fopen("/proc/self/maps", "r");
    if (maps_file == nullptr) {
        return -1;
    }
    char line[1024];
    int lines = 0;
    while (fgets(line, sizeof(line), maps_file) != nullptr) {
        lines += 1;
    }
    fclose(maps_file);
    return lines;
}

// Samples the mm_struct three times around one probe mapping and reports the
// slot that holds an rss counter.
static KModErr probe_rss_stat_window(uint64_t window_size, uint32_t& offset) {
    offset = 0;
    const uint32_t page_size = get_page_size();
    const size_t probe_length = page_size * 50;
    std::vector<char> before(window_size);
    std::vector<char> during(window_size);
    std::vector<char> after(window_size);

    force_rss_sync();
    RETURN_IF_ERROR(snapshot_current_mm_struct(
            before.data(), static_cast<int>(before.size())));

    void* mapping = mmap(nullptr, probe_length, PROT_READ | PROT_WRITE,
                         MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (mapping == MAP_FAILED) {
        return KModErr::ERR_MODULE_PARAM;
    }
    force_rss_sync();
    KModErr err = snapshot_current_mm_struct(
            during.data(), static_cast<int>(during.size()));
    if (is_failed(err)) {
        munmap(mapping, probe_length);
        return err;
    }
    memset(mapping, 0xAA, probe_length);
    force_rss_sync();
    err = snapshot_current_mm_struct(after.data(), static_cast<int>(after.size()));
    munmap(mapping, probe_length);
    if (is_failed(err)) {
        return err;
    }

    // MM_ANONPAGES is the counter that gains one page per touched page and
    // does not move while the mapping only exists.  rss_stat is an array of
    // counters, so one counter width sits in front of the sampled slot.
    const int64_t scan_limit = static_cast<int32_t>(before.size());
    for (int64_t i = 0; i < scan_limit; i += 8) {
        const int64_t old_count = *reinterpret_cast<int64_t*>(before.data() + i);
        const int64_t mapped_count = *reinterpret_cast<int64_t*>(during.data() + i);
        const int64_t touched_count = *reinterpret_cast<int64_t*>(after.data() + i);
        if (std::abs(mapped_count - old_count) >= 5) {
            continue;
        }
        if (std::abs(touched_count - mapped_count - 50) >= 5) {
            continue;
        }
        const int32_t candidate_index = static_cast<int32_t>(i);
        if (candidate_index > 0) {
            offset = static_cast<uint32_t>(candidate_index - 8);
        }
        break;
    }
    return KModErr::OK;
}

// Locates arg_start/arg_end: the saved auxiliary vector is embedded in the
// mm_struct right behind those four stack-bound fields, so its position gives
// both offsets once the whole auxv array read from /proc/self/auxv is found.
static std::vector<uint64_t> get_auxv_signature() {
    std::vector<uint64_t> auxv;
    fd_guard auxv_fd(open("/proc/self/auxv", O_RDONLY));
    if (auxv_fd.get() >= 0) {
        while (true) {
            uint64_t auxv_type;
            uint64_t auxv_value;
            if (read(auxv_fd.get(), &auxv_type, sizeof(auxv_type)) !=
                static_cast<ssize_t>(sizeof(auxv_type))) {
                break;
            }
            if (read(auxv_fd.get(), &auxv_value, sizeof(auxv_value)) !=
                static_cast<ssize_t>(sizeof(auxv_value))) {
                break;
            }
            auxv.push_back(auxv_type);
            auxv.push_back(auxv_value);
            if (auxv_type == 0) {
                break;
            }
        }
    }
    return auxv;
}

static KModErr resolve_mm_btf_pair(const char* first_name, const char* second_name,
                                  uint32_t& first_offset, uint32_t& second_offset,
                                  bool& available) {
    KernelFieldDescription first;
    KModErr error = maplesu_resolve_btf_field("mm_struct", first_name, 8, first, available);
    if (!available || is_failed(error)) return error;

    KernelFieldDescription second;
    bool second_available = false;
    error = maplesu_resolve_btf_field("mm_struct", second_name, 8, second, second_available);
    if (is_failed(error)) return error;
    if (!second_available || first.owner_size_bytes != second.owner_size_bytes)
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;

    first_offset = first.offset_bytes;
    second_offset = second.offset_bytes;
    return KModErr::OK;
}

KModErr get_mm_struct_arg_offset(uint32_t& arg_start_offset, uint32_t& arg_end_offset) {
    bool available = false;
    KModErr btf_error = resolve_mm_btf_pair("arg_start", "arg_end",
        arg_start_offset, arg_end_offset, available);
    if (available) return btf_error;

    static uint32_t arg_start_offset_cache = 0;
    static uint32_t arg_end_offset_cache = 0;
    if (arg_start_offset_cache != 0 && arg_end_offset_cache != 0) {
        arg_start_offset = arg_start_offset_cache;
        arg_end_offset = arg_end_offset_cache;
        return KModErr::OK;
    }

    KModErr err = KModErr::OK;
    std::vector<uint64_t> auxv = get_auxv_signature();
    if (auxv.empty()) {
        return KModErr::ERR_MODULE_GET_AUXV_SIGNATURE;
    }

    aarch64_asm_ctx asm_ctx = init_aarch64_asm();
    asmjit::a64::Assembler* a = asm_ctx.assembler();
    asmjit::Label fail_label = a->newLabel();
    kernel_module::arm64_module_asm_func_start(a);

    RETURN_IF_ERROR(kernel_module::export_symbol::get_current_checked(a, asmjit::a64::regs::x11));
    kernel_module::export_symbol::get_task_mm(a, err, asmjit::a64::regs::x11);
    if (is_failed(err)) {
        return err;
    }
    a->cbz(asmjit::a64::regs::x0, fail_label);
    a->mov(asmjit::a64::regs::x12, asmjit::a64::regs::x0);
    aarch64_asm_mov_x(a, asmjit::a64::regs::x13, 8192);

    std::vector<uint8_t> auxv_bytes(auxv.size() * sizeof(uint64_t));
    memcpy(auxv_bytes.data(), auxv.data(), auxv_bytes.size());
    aarch64_asm_set_x_data_ptr(a, asmjit::a64::regs::x14, auxv_bytes);
    aarch64_asm_mov_x(a, asmjit::a64::regs::x15, auxv_bytes.size());
    kernel_module::string_ops::kmemmem(a, asmjit::a64::regs::x12, asmjit::a64::regs::x13,
                                       asmjit::a64::regs::x14, asmjit::a64::regs::x15);
    kernel_module::export_symbol::mmput(a, err, asmjit::a64::regs::x12);
    if (is_failed(err)) {
        return err;
    }
    a->cbz(asmjit::a64::regs::x0, fail_label);
    a->sub(asmjit::a64::regs::x0, asmjit::a64::regs::x0, asmjit::a64::regs::x12);
    a->bind(fail_label);
    kernel_module::arm64_module_asm_func_end(a, asmjit::a64::regs::x0);

    std::vector<uint8_t> func_bytes = aarch64_asm_to_bytes(a);
    uint64_t result = 0;
    KModErr exec_err = kernel_module::execute_kernel_asm_func(func_bytes, result);
    if (is_ok(exec_err)) {
        if ((result & 0xFFFFFFFFULL) != 0) {
            const uint32_t saved_auxv_offset = static_cast<uint32_t>(result);
            arg_start_offset = saved_auxv_offset - 32;
            arg_end_offset = saved_auxv_offset - 24;
            arg_start_offset_cache = arg_start_offset;
            arg_end_offset_cache = arg_end_offset;
        }
    }
    return exec_err;
}

KModErr get_mm_struct_env_offset(uint32_t& env_start_offset, uint32_t& env_end_offset) {
    bool available = false;
    KModErr btf_error = resolve_mm_btf_pair("env_start", "env_end",
        env_start_offset, env_end_offset, available);
    if (available) return btf_error;

    static uint32_t env_start_offset_cache = 0;
    static uint32_t env_end_offset_cache = 0;
    if (env_start_offset_cache != 0 && env_end_offset_cache != 0) {
        env_start_offset = env_start_offset_cache;
        env_end_offset = env_end_offset_cache;
        return KModErr::OK;
    }

    uint32_t arg_start_offset = 0;
    uint32_t arg_end_offset = 0;
    KModErr err = get_mm_struct_arg_offset(arg_start_offset, arg_end_offset);
    if (is_ok(err)) {
        env_start_offset_cache = arg_end_offset + 8;
        env_start_offset = env_start_offset_cache;
        env_end_offset_cache = arg_end_offset + 16;
        env_end_offset = env_end_offset_cache;
    }
    return err;
}

KModErr get_mm_struct_rss_stat_offset(uint32_t& offset) {
    // The counters sit far enough into the mm_struct that a fixed window is not
    // always wide enough; extend the sampled window until the pair of slots is
    // found.
    uint64_t window_size = 400;
    while (window_size < 900) {
        KModErr err = probe_rss_stat_window(window_size, offset);
        if (is_failed(err)) {
            return err;
        }
        if (offset != 0) {
            return KModErr::OK;
        }
        window_size += 100;
    }
    return KModErr::OK;
}

// Reads back the live mm_struct through a routine that publishes its pointer.
static KModErr snapshot_current_mm_struct(char* out_buf, int size) {
    uint64_t mm_kaddr = 0;
    KModErr err = get_kernel_shellcode_u64_result(
            [](asmjit::a64::Assembler* a, asmjit::a64::GpX& result) -> KModErr {
                KModErr call_err = KModErr::ERR_MODULE_PARAM;
                RETURN_IF_ERROR(kernel_module::export_symbol::get_current_checked(a, asmjit::a64::regs::x10));
                kernel_module::export_symbol::get_task_mm(a, call_err, asmjit::a64::regs::x10);
                if (is_failed(call_err)) {
                    return call_err;
                }
                kernel_module::export_symbol::mmput(a, call_err, asmjit::a64::regs::x0);
                if (is_failed(call_err)) {
                    return call_err;
                }
                result = asmjit::a64::regs::x0;
                return KModErr::OK;
            },
            mm_kaddr);
    if (is_failed(err)) {
        return err;
    }
    return read_kernel_mem(mm_kaddr, out_buf, static_cast<uint32_t>(size));
}

static void force_rss_sync() {
    char statm_buf[128];
    fd_guard statm_fd(open("/proc/self/statm", O_RDONLY));
    if (statm_fd.get() > -1) {
        read(statm_fd.get(), statm_buf, sizeof(statm_buf));
    }
}

static KModErr find_pgd_offset_via_at_instruction(uint64_t pgd_phys, uint32_t& offset) {
    offset = 0;

    uint64_t ttbr0_mask = 0;
    KModErr err = get_dynamic_ttbr0_mask(ttbr0_mask);
    if (is_failed(err)) {
        return err;
    }

    uint64_t mm_kaddr = 0;
    err = get_kernel_shellcode_u64_result(
            [](asmjit::a64::Assembler* a, asmjit::a64::GpX& result) -> KModErr {
                KModErr call_err = KModErr::ERR_MODULE_PARAM;
                RETURN_IF_ERROR(kernel_module::export_symbol::get_current_checked(a, asmjit::a64::regs::x10));
                kernel_module::export_symbol::get_task_mm(a, call_err, asmjit::a64::regs::x10);
                if (is_failed(call_err)) {
                    return call_err;
                }
                kernel_module::export_symbol::mmput(a, call_err, asmjit::a64::regs::x0);
                if (is_failed(call_err)) {
                    return call_err;
                }
                result = asmjit::a64::regs::x0;
                return KModErr::OK;
            },
            mm_kaddr);
    if (is_failed(err)) {
        return err;
    }

    // The live pgd is a kernel pointer held by one of the first aligned words
    // of the mm_struct; its physical page is what identifies it.
    for (uint64_t i = 0; i < 768; i += 4) {
        uint64_t slot_value = 0;
        err = read_kernel_mem(mm_kaddr + i, &slot_value, sizeof(slot_value));
        if (is_failed(err)) {
            return err;
        }
        if (slot_value < 0xFFFFFF0000000000ULL) {
            continue;
        }
        uint64_t phys_kaddr = 0;
        err = hardware_virt_to_phys(slot_value, phys_kaddr);
        if (is_failed(err)) {
            return err;
        }
        if ((ttbr0_mask & phys_kaddr) == pgd_phys) {
            offset = static_cast<uint32_t>(i);
            break;
        }
    }

    if (offset == 0) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    return KModErr::OK;
}

}  // namespace kernel_module
