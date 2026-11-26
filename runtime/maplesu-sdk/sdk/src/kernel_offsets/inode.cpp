// inode implementation.
//
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/vfs.h>
#include <sys/xattr.h>
#include <unistd.h>

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <functional>
#include <sstream>
#include <string>
#include <vector>

#include "kernel_module_kit_umbrella.h"
#include "maplesu_kernel_layout_private.h"

#include "offsets_internal.h"
#include "private_maplesu_paths.h"

// Suppresses the informational "symbol not found" traces while one candidate
// name is probed, restoring the previous silent state afterwards. The vendor
// helper header declares the same guard under its own internal name; the name
// used here is the one this translation unit's helper surface carries.
class SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8 final {
public:
    SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8() noexcept {
        active_ = get_symbol_lookup_printf_silent_d8685f89f10ca8eb96df766c8babd6c3();
        if (active_) {
            set_symbol_lookup_printf_silent_d8685f89f10ca8eb96df766c8babd6c3(false);
        }
    }
    ~SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8() noexcept {
        if (active_) {
            set_symbol_lookup_printf_silent_d8685f89f10ca8eb96df766c8babd6c3(true);
        }
    }
    SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8(
            const SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8&) = delete;
    SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8& operator=(
            const SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8&) = delete;

private:
    bool active_ = false;
};

// Runs one emitted AArch64 routine in the kernel and drops its X0 result. An
// emitter failure returns before the routine epilogue is emitted, so an
// unbalanced prologue is never handed to the executor.
static KModErr exec_kernel_shellcode_no_result(
        std::function<KModErr(asmjit::a64::Assembler*)> emitter) {
    aarch64_asm_ctx asm_ctx = init_aarch64_asm();
    asmjit::a64::Assembler* assembler = asm_ctx.assembler();
    kernel_module::arm64_module_asm_func_start(assembler);

    KModErr err = emitter(assembler);
    if (is_failed(err)) {
        return err;
    }
    kernel_module::arm64_module_asm_func_end(assembler);

    std::vector<uint8_t> func_bytes = aarch64_asm_to_bytes(assembler);
    uint64_t output_result = 0;
    return kernel_module::execute_kernel_asm_func(func_bytes, output_result);
}

// Runs one emitted AArch64 routine in the kernel and publishes the X0 value of
// the register the emitter selected as the routine result. The routine is only
// finished when the emitter succeeded.
static KModErr get_kernel_shellcode_u64_result(
        std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emitter,
        uint64_t& out_result) {
    aarch64_asm_ctx asm_ctx = init_aarch64_asm();
    asmjit::a64::Assembler* assembler = asm_ctx.assembler();
    kernel_module::arm64_module_asm_func_start(assembler);

    asmjit::a64::GpX result_reg = asmjit::a64::regs::x0;
    KModErr err = emitter(assembler, result_reg);
    if (is_failed(err)) {
        return err;
    }
    kernel_module::arm64_module_asm_func_end(assembler, result_reg);

    std::vector<uint8_t> func_bytes = aarch64_asm_to_bytes(assembler);
    return kernel_module::execute_kernel_asm_func(func_bytes, out_result);
}

// Copies size bytes from a kernel address into out_buf through a kernel page
// filled by a nofault copy executed in the kernel itself, so an unreadable
// source never faults the caller. out_ok reports whether the copy succeeded.
static KModErr safe_read_kaddr(uint64_t kaddr, bool& out_ok, void* out_buf,
                               size_t size) {
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
            [kaddr, page_kaddr, size](asmjit::a64::Assembler* assembler,
                                      asmjit::a64::GpX& result) -> KModErr {
                KModErr call_err = KModErr::ERR_MODULE_PARAM;
                aarch64_asm_mov_x(assembler, asmjit::a64::regs::x10, page_kaddr);
                aarch64_asm_mov_x(assembler, asmjit::a64::regs::x11, kaddr);
                kernel_module::export_symbol::copy_from_kernel_nofault(
                        assembler, call_err, asmjit::a64::regs::x10,
                        asmjit::a64::regs::x11, size);
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
static KModErr safe_read_kaddr_t(uint64_t kaddr, bool& out_ok, T& out) {
    return safe_read_kaddr(kaddr, out_ok, &out, sizeof(T));
}


#include "kpath_owner_private.h"

namespace {

// Resolves the struct file pointer the calling process holds for fd:
// current->files->fdt->fd[fd]. The pointer is published in out_file_ptr; a zero
// pointer means the descriptor had no file or the walk failed.
[[maybe_unused]] static KModErr get_current_task_file_ptr(int fd, uint64_t& out_file_ptr) {
    uint64_t current_task = 0;
    KModErr err = get_kernel_shellcode_u64_result(
            [](asmjit::a64::Assembler* assembler, asmjit::a64::GpX& result) -> KModErr {
                result = asmjit::a64::regs::x10;
                RETURN_IF_ERROR(kernel_module::export_symbol::get_current_checked(assembler, result));
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
            fd_array + static_cast<uint64_t>(fd) * sizeof(uint64_t), &file_ptr,
            sizeof(file_ptr));
    if (is_failed(err)) {
        return err;
    }
    out_file_ptr = file_ptr;
    return KModErr::OK;
}

}  // namespace

// are _ZN8fd_guard5resetEi / _ZN8fd_guardD2Ev (no namespace component).
struct fd_guard {
    int fd;

    ~fd_guard() { reset(-1); }

    void reset(int new_fd) {
        if (fd > -1) {
            close(fd);
        }
        fd = new_fd;
    }

    int get() const { return fd; }
};

namespace file_utils {

// Removes a path and every entry below it; the error codes are ignored because
// a path that never existed is a valid starting point for the probe.
static void delete_path(const std::filesystem::path& path) {
    std::error_code remove_error;
    std::filesystem::remove_all(path, remove_error);
    std::error_code exists_error;
    (void)!std::filesystem::exists(path, exists_error);
}

}  // namespace file_utils

namespace kernel_module {

static KModErr resolve_inode_btf_offset(const char* member, uint32_t width,
                                       uint32_t& offset, bool& available) {
    KernelFieldDescription field;
    KModErr error = maplesu_resolve_btf_field("inode", member, width, field, available);
    if (available && is_ok(error)) offset = field.offset_bytes;
    return error;
}

//   %"struct.kernel_module::timespec64" = type { i64, i64 }
// Vendor-private (not in include/); the < 6.11.0 inode stores one 16-byte
// timespec64 per timestamp. Only used inside this TU.
struct timespec64 {
    int64_t tv_sec;
    int64_t tv_nsec;
};

static_assert(sizeof(struct timespec64) == 16,
              "struct timespec64 is {i64 tv_sec; i64 tv_nsec;}");
static_assert(offsetof(struct timespec64, tv_nsec) == 8,
              "tv_nsec follows tv_sec, no padding");

//
// Builds the path of one probe file: "temp<index>" followed by a fixed 33-byte
// marker, placed inside the SDK's system-file directory under /data.
static std::string get_temp_file_path(int index) {
    std::stringstream name_stream;
    name_stream << "temp" << index << "hL7bM0iX9hD4kS2dN2uG5sO1oQ9hA3bI";

    std::filesystem::path dir_path(maplesu::sdk::get_system_file_dir_path());
    return (dir_path / name_stream.str()).string();
}

//
// Creates/recreates the three probe files used by the time-offset getters and
// stamps them with distinct synthetic timestamps: each file is opened
// read-write, truncated to a distinct length, given an atime/mtime pair one
// second and one nanosecond apart, and labelled system_file so the kernel
// probes can read it.
static KModErr init_time_offset_test_file() {
    for (uint64_t idx = 0; idx != 3; ++idx) {
        std::string path = get_temp_file_path(static_cast<int>(idx));
        file_utils::delete_path(std::filesystem::path(path));

        fd_guard fd{open(path.c_str(), O_RDWR | O_CREAT | O_TRUNC, 0666)};
        if (fd.get() < 0) {
            return KModErr::ERR_MODULE_CREATE_EMPTY_FILE;
        }
        if (ftruncate(fd.get(), static_cast<off_t>(idx + 4660)) != 0) {  // :34
            return KModErr::ERR_MODULE_WRITE_FILE;
        }

        struct timespec stamp[2];
        stamp[0].tv_sec = static_cast<time_t>(idx + 1611111111);
        stamp[0].tv_nsec = static_cast<long>(idx + 111111111);
        stamp[1].tv_sec = static_cast<time_t>(idx + 1722222222);
        stamp[1].tv_nsec = static_cast<long>(idx + 222222222);
        if (futimens(fd.get(), stamp) != 0) {  // :46
            return KModErr::ERR_MODULE_WRITE_FILE;
        }
        if (chmod(path.c_str(), 0777) != 0) {  // :57
            return KModErr::ERR_SET_FILE_SELINUX;
        }
        if (setxattr(path.c_str(), "security.selinux",
                    "u:object_r:system_file:s0", 26, 0) != 0) {  // :60
            return KModErr::ERR_SET_FILE_SELINUX;
        }
    }
    return KModErr::OK;  // :79
}

// is an enum class : ssize_t; values are the contiguous AUTO list starting at
// ERR_MODULE_PARAM = -20000).
static_assert(static_cast<ssize_t>(KModErr::ERR_MODULE_OFFSET_NOT_MATCH_LINUX_VER) == -19956,
              "unexpected layout size");
static_assert(static_cast<ssize_t>(KModErr::ERR_MODULE_OFFSET_NOT_FOUND) == -19955,
              "unexpected layout size");
static_assert(static_cast<ssize_t>(KModErr::ERR_MODULE_CREATE_EMPTY_FILE) == -19935,
              "unexpected layout size");
static_assert(static_cast<ssize_t>(KModErr::ERR_MODULE_OPEN_FILE) == -19934,
              "unexpected layout size");
static_assert(static_cast<ssize_t>(KModErr::ERR_MODULE_WRITE_FILE) == -19933,
              "unexpected layout size");
static_assert(static_cast<ssize_t>(KModErr::ERR_SET_FILE_SELINUX) == -19915,
              "unexpected layout size");

// Linux >= 6.11.0 inode: three contiguous 8-byte sec fields (atime, mtime,
// ctime at i, i+8, i+16) and three contiguous 4-byte nsec fields (at j, j+4,
// j+8). Searched with two inodes of the same shape; a field is accepted only
// when both test files carry the matching fstat() value.
KModErr get_inode_time_offset(uint32_t& i_atime_sec_offset,
                              uint32_t& i_atime_nsec_offset,
                              uint32_t& i_mtime_sec_offset,
                              uint32_t& i_mtime_nsec_offset,
                              uint32_t& i_ctime_sec_offset,
                              uint32_t& i_ctime_nsec_offset) {
    // This overload only makes sense on Linux >= 6.11.0.
    if (is_kernel_version_less("6.11.0")) {
        return KModErr::ERR_MODULE_OFFSET_NOT_MATCH_LINUX_VER;
    }

    // Create/re-stamp the probe files.
    KModErr err = init_time_offset_test_file();
    if (is_failed(err)) {
        return err;
    }

    // Publish the complete group only after both probe snapshots agree.
    uint32_t atime_sec = 0, atime_nsec = 0;
    uint32_t mtime_sec = 0, mtime_nsec = 0;
    uint32_t ctime_sec = 0, ctime_nsec = 0;

    // Two files: the second one makes accidental matches impossible.
    std::string file0_path = get_temp_file_path(0);
    std::string file1_path = get_temp_file_path(1);

    int file0_fd = open(file0_path.c_str(), O_RDONLY);
    int file1_fd = open(file1_path.c_str(), O_RDONLY);
    fd_guard file0_guard{file0_fd};
    fd_guard file1_guard{file1_fd};
    if (file0_fd < 0 || file1_fd < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    struct stat file0_stat{};
    struct stat file1_stat{};
    if (fstat(file0_fd, &file0_stat) != 0) {
        return KModErr::ERR_MODULE_READ_FILE;
    }
    if (fstat(file1_fd, &file1_stat) != 0) {
        return KModErr::ERR_MODULE_READ_FILE;
    }

    scoped_kpath file0_kpath{};
    scoped_kpath file1_kpath{};
    err = acquire_kpath(file0_path, file0_kpath);
    if (is_failed(err)) {
        return err;
    }
    err = acquire_kpath(file1_path, file1_kpath);
    if (is_failed(err)) {
        return err;
    }

    // dentry -> inode pointer for both files.
    uint32_t d_inode_offset = 0;
    err = get_dentry_d_inode_offset(d_inode_offset);
    if (is_failed(err)) {
        return err;
    }

    uint64_t file0_inode_kaddr = 0;
    uint64_t file1_inode_kaddr = 0;
    err = read_kernel_mem(file0_kpath.kp.dentry + d_inode_offset,
                          &file0_inode_kaddr, sizeof(file0_inode_kaddr));
    if (is_failed(err)) {
        return err;
    }
    err = read_kernel_mem(file1_kpath.kp.dentry + d_inode_offset,
                          &file1_inode_kaddr, sizeof(file1_inode_kaddr));
    if (is_failed(err)) {
        return err;
    }

    bool found_sec = false;
    bool found_nsec = false;
    for (uint64_t i = 0; i + sizeof(uint32_t) <= 1024; i += 4) {
        if (!found_sec && (i & 4) == 0 && i + 3 * sizeof(int64_t) <= 1024) {
            int64_t atime_sec0 = 0;
            int64_t atime_sec1 = 0;
            err = read_kernel_mem(file0_inode_kaddr + i, &atime_sec0,
                                  sizeof(atime_sec0));
            if (is_failed(err)) {
                return err;
            }
            err = read_kernel_mem(file1_inode_kaddr + i, &atime_sec1,
                                  sizeof(atime_sec1));
            if (is_failed(err)) {
                return err;
            }
            if (atime_sec0 == static_cast<int64_t>(file0_stat.st_atim.tv_sec) &&
                atime_sec1 == static_cast<int64_t>(file1_stat.st_atim.tv_sec)) {
                int64_t mtime_sec0 = 0;
                int64_t mtime_sec1 = 0;
                err = read_kernel_mem(file0_inode_kaddr + i + 8, &mtime_sec0,
                                      sizeof(mtime_sec0));
                if (is_failed(err)) {
                    return err;
                }
                err = read_kernel_mem(file1_inode_kaddr + i + 8, &mtime_sec1,
                                      sizeof(mtime_sec1));
                if (is_failed(err)) {
                    return err;
                }
                if (mtime_sec0 == static_cast<int64_t>(file0_stat.st_mtim.tv_sec) &&
                    mtime_sec1 == static_cast<int64_t>(file1_stat.st_mtim.tv_sec)) {
                    int64_t ctime_sec0 = 0;
                    int64_t ctime_sec1 = 0;
                    err = read_kernel_mem(file0_inode_kaddr + i + 16, &ctime_sec0,
                                          sizeof(ctime_sec0));
                    if (is_failed(err)) {
                        return err;
                    }
                    err = read_kernel_mem(file1_inode_kaddr + i + 16, &ctime_sec1,
                                          sizeof(ctime_sec1));
                    if (is_failed(err)) {
                        return err;
                    }
                    if (ctime_sec0 == static_cast<int64_t>(file0_stat.st_ctim.tv_sec) &&
                        ctime_sec1 == static_cast<int64_t>(file1_stat.st_ctim.tv_sec)) {
                        atime_sec = static_cast<uint32_t>(i);
                        mtime_sec = static_cast<uint32_t>(i + 8);
                        ctime_sec = static_cast<uint32_t>(i + 16);
                        found_sec = true;
                    }
                }
            }
        }

        if (!found_nsec && i + 3 * sizeof(uint32_t) <= 1024) {
            uint32_t atime_nsec0 = 0;
            uint32_t atime_nsec1 = 0;
            err = read_kernel_mem(file0_inode_kaddr + i, &atime_nsec0,
                                  sizeof(atime_nsec0));
            if (is_failed(err)) {
                return err;
            }
            err = read_kernel_mem(file1_inode_kaddr + i, &atime_nsec1,
                                  sizeof(atime_nsec1));
            if (is_failed(err)) {
                return err;
            }
            if (atime_nsec0 == static_cast<uint32_t>(file0_stat.st_atim.tv_nsec) &&
                atime_nsec1 == static_cast<uint32_t>(file1_stat.st_atim.tv_nsec)) {
                uint32_t mtime_nsec0 = 0;
                uint32_t mtime_nsec1 = 0;
                err = read_kernel_mem(file0_inode_kaddr + i + 4, &mtime_nsec0,
                                      sizeof(mtime_nsec0));
                if (is_failed(err)) {
                    return err;
                }
                err = read_kernel_mem(file1_inode_kaddr + i + 4, &mtime_nsec1,
                                      sizeof(mtime_nsec1));
                if (is_failed(err)) {
                    return err;
                }
                if (mtime_nsec0 == static_cast<uint32_t>(file0_stat.st_mtim.tv_nsec) &&
                    mtime_nsec1 == static_cast<uint32_t>(file1_stat.st_mtim.tv_nsec)) {
                    uint32_t ctime_nsec0 = 0;
                    uint32_t ctime_nsec1 = 0;
                    err = read_kernel_mem(file0_inode_kaddr + i + 8, &ctime_nsec0,
                                          sizeof(ctime_nsec0));
                    if (is_failed(err)) {
                        return err;
                    }
                    err = read_kernel_mem(file1_inode_kaddr + i + 8, &ctime_nsec1,
                                          sizeof(ctime_nsec1));
                    if (is_failed(err)) {
                        return err;
                    }
                    if (ctime_nsec0 == static_cast<uint32_t>(file0_stat.st_ctim.tv_nsec) &&
                        ctime_nsec1 == static_cast<uint32_t>(file1_stat.st_ctim.tv_nsec)) {
                        atime_nsec = static_cast<uint32_t>(i);
                        mtime_nsec = static_cast<uint32_t>(i + 4);
                        ctime_nsec = static_cast<uint32_t>(i + 8);
                        found_nsec = true;
                    }
                }
            }
        }

        if (found_sec && found_nsec) {
            break;
        }
    }

    if (!found_sec || !found_nsec) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    i_atime_sec_offset = atime_sec;
    i_atime_nsec_offset = atime_nsec;
    i_mtime_sec_offset = mtime_sec;
    i_mtime_nsec_offset = mtime_nsec;
    i_ctime_sec_offset = ctime_sec;
    i_ctime_nsec_offset = ctime_nsec;
    return KModErr::OK;
}

// Linux < 6.11.0 inode: each timestamp is a 16-byte struct timespec64; the
// getter reports the offset of the atime, mtime and ctime structures. Both
// test files are scanned at the same 4-byte offsets until all three 16-byte
// values were matched against their fstat() results.
KModErr get_inode_time_offset(uint32_t& i_atime_offset,
                              uint32_t& i_mtime_offset,
                              uint32_t& i_ctime_offset) {
    // This overload only makes sense on Linux < 6.11.0.
    if (!is_kernel_version_less("6.11.0")) {
        return KModErr::ERR_MODULE_OFFSET_NOT_MATCH_LINUX_VER;
    }

    // Create/re-stamp the probe files.
    KModErr err = init_time_offset_test_file();
    if (is_failed(err)) {
        return err;
    }

    std::string file0_path = get_temp_file_path(0);  // :21
    std::string file1_path = get_temp_file_path(1);  // :21

    int file0_fd = open(file0_path.c_str(), O_RDONLY);  // :30
    int file1_fd = open(file1_path.c_str(), O_RDONLY);  // :39
    fd_guard file0_guard{file0_fd};
    fd_guard file1_guard{file1_fd};
    if (file0_fd < 0 || file1_fd < 0) {  // :40-:41
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    struct stat file0_stat{};
    struct stat file1_stat{};
    if (fstat(file0_fd, &file0_stat) != 0) {
        return KModErr::ERR_MODULE_READ_FILE;
    }
    if (fstat(file1_fd, &file1_stat) != 0) {
        return KModErr::ERR_MODULE_READ_FILE;
    }

    scoped_kpath file0_kpath{};  // :49-:51, memset 24 bytes
    scoped_kpath file1_kpath{};
    err = acquire_kpath(file0_path, file0_kpath);  // :52
    if (is_failed(err)) {
        return err;
    }
    err = acquire_kpath(file1_path, file1_kpath);  // :58
    if (is_failed(err)) {
        return err;
    }

    uint32_t d_inode_offset = 0;
    err = get_dentry_d_inode_offset(d_inode_offset);  // :64
    if (is_failed(err)) {
        return err;
    }

    uint64_t file0_inode_kaddr = 0;
    uint64_t file1_inode_kaddr = 0;
    err = read_kernel_mem(file0_kpath.kp.dentry + d_inode_offset,
                          &file0_inode_kaddr, sizeof(file0_inode_kaddr));  // :75
    if (is_failed(err)) {
        return err;
    }
    err = read_kernel_mem(file1_kpath.kp.dentry + d_inode_offset,
                          &file1_inode_kaddr, sizeof(file1_inode_kaddr));  // :86
    if (is_failed(err)) {
        return err;
    }

    // Offset zero is valid; availability is tracked separately from the value.
    uint32_t atime = 0, mtime = 0, ctime = 0;
    bool found_atime = false, found_mtime = false, found_ctime = false;

    for (uint64_t i = 0; i + sizeof(timespec64) <= 256; i += 4) {
        struct timespec64 file0_time{};
        struct timespec64 file1_time{};
        err = read_kernel_mem(file0_inode_kaddr + i, &file0_time,
                              sizeof(file0_time));
        if (is_failed(err)) {
            return err;
        }
        err = read_kernel_mem(file1_inode_kaddr + i, &file1_time,
                              sizeof(file1_time));
        if (is_failed(err)) {
            return err;
        }

        if (!found_atime &&
            file0_time.tv_sec == static_cast<int64_t>(file0_stat.st_atim.tv_sec) &&
            file0_time.tv_nsec == static_cast<int64_t>(file0_stat.st_atim.tv_nsec) &&
            file1_time.tv_sec == static_cast<int64_t>(file1_stat.st_atim.tv_sec) &&
            file1_time.tv_nsec == static_cast<int64_t>(file1_stat.st_atim.tv_nsec)) {
            atime = static_cast<uint32_t>(i);
            found_atime = true;
        }

        if (!found_mtime &&
            file0_time.tv_sec == static_cast<int64_t>(file0_stat.st_mtim.tv_sec) &&
            file0_time.tv_nsec == static_cast<int64_t>(file0_stat.st_mtim.tv_nsec) &&
            file1_time.tv_sec == static_cast<int64_t>(file1_stat.st_mtim.tv_sec) &&
            file1_time.tv_nsec == static_cast<int64_t>(file1_stat.st_mtim.tv_nsec)) {
            mtime = static_cast<uint32_t>(i);
            found_mtime = true;
        }

        if (!found_ctime &&
            file0_time.tv_sec == static_cast<int64_t>(file0_stat.st_ctim.tv_sec) &&
            file0_time.tv_nsec == static_cast<int64_t>(file0_stat.st_ctim.tv_nsec) &&
            file1_time.tv_sec == static_cast<int64_t>(file1_stat.st_ctim.tv_sec) &&
            file1_time.tv_nsec == static_cast<int64_t>(file1_stat.st_ctim.tv_nsec)) {
            ctime = static_cast<uint32_t>(i);
            found_ctime = true;
        }
        if (found_atime && found_mtime && found_ctime) break;
    }

    if (!found_atime || !found_mtime || !found_ctime) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    i_atime_offset = atime;
    i_mtime_offset = mtime;
    i_ctime_offset = ctime;
    return KModErr::OK;
}


// ===========================================================================
// Inode field offset resolvers
//
// Every resolver in this file follows the same shape: bring a probe file (or a
// device via /dev), resolve the kernel inode behind it, copy the front of the
// inode into user memory and match a value that only the wanted field can hold.
// The offset is cached once a resolver proved it.
// ===========================================================================

namespace {

// arm64 kernel addresses have their top twelve bits set; anything below the
// floor is user space data or a small integer.
bool is_kernel_address(uint64_t value) {
    return value >= 0xFFF0000000000000ULL;
}

// Reads one 16-bit field out of an inode snapshot already copied to user memory.
uint16_t snapshot_u16(const std::vector<uint8_t>& snapshot, uint64_t offset) {
    uint16_t value = 0;
    memcpy(&value, snapshot.data() + offset, sizeof(value));
    return value;
}

// Reads a u64 pointer out of an inode snapshot.
uint64_t snapshot_u64(const std::vector<uint8_t>& snapshot, uint64_t offset) {
    uint64_t value = 0;
    memcpy(&value, snapshot.data() + offset, sizeof(value));
    return value;
}

}  // namespace

// Linux >= 4.7.0 stores the inode lock as an rw_semaphore placed directly after
// i_state, so its offset is the state offset shifted by the 8-byte state word.
KModErr get_inode_i_rwsem_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_inode_btf_offset("i_rwsem", 0, offset, available);
    if (available) return btf_error;

    if (is_kernel_version_less("4.7.0")) {
        return KModErr::ERR_MODULE_OFFSET_NOT_MATCH_LINUX_VER;
    }
    uint32_t state_offset = 0;
    KModErr err = get_inode_i_state_offset(state_offset);
    if (is_failed(err)) {
        return err;
    }
    offset = state_offset + 8;
    return KModErr::OK;
}

// Linux < 4.7.0 stores the inode lock as a mutex; it occupies the same slot the
// later kernels use for the rw_semaphore, hence the forwarding to the rwsem
// resolver inside the version window.
KModErr get_inode_i_mutex_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_inode_btf_offset("i_mutex", 0, offset, available);
    if (available) return btf_error;

    if (!is_kernel_version_less("4.7.0")) {
        return KModErr::ERR_MODULE_OFFSET_NOT_MATCH_LINUX_VER;
    }
    uint32_t rwsem_offset = 0;
    KModErr err = get_inode_i_rwsem_offset(rwsem_offset);
    if (is_failed(err)) {
        return err;
    }
    offset = rwsem_offset;
    return KModErr::OK;
}

// Resolve each target field independently; i_dir_seq is narrower than the pointers.
// Only the missing-BTF branches retain the old shared union probe.
KModErr get_inode_i_pipe_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_inode_btf_offset("i_pipe", 8, offset, available);
    if (available) return btf_error;

    return get_inode_i_bdev_offset(offset);
}

KModErr get_inode_i_cdev_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_inode_btf_offset("i_cdev", 8, offset, available);
    if (available) return btf_error;

    return get_inode_i_bdev_offset(offset);
}

KModErr get_inode_i_link_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_inode_btf_offset("i_link", 8, offset, available);
    if (available) return btf_error;

    return get_inode_i_bdev_offset(offset);
}

KModErr get_inode_i_dir_seq_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_inode_btf_offset("i_dir_seq", 4, offset, available);
    if (available) return btf_error;

    return get_inode_i_bdev_offset(offset);
}

// i_ino is unique per file on the same filesystem, so two probe files with
// distinct inode numbers make the field unambiguous: the 8-byte slot that
// carries both fstat() inode numbers is the i_ino field.
KModErr get_inode_i_ino_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_inode_btf_offset("i_ino", 8, offset, available);
    if (available) return btf_error;

    KModErr err = init_time_offset_test_file();
    if (is_failed(err)) {
        return err;
    }

    const std::string file0_path = get_temp_file_path(0);
    const std::string file1_path = get_temp_file_path(1);

    const int file0_fd = open(file0_path.c_str(), O_RDONLY);
    const int file1_fd = open(file1_path.c_str(), O_RDONLY);
    fd_guard file0_guard{file0_fd};
    fd_guard file1_guard{file1_fd};
    if (file0_fd < 0 || file1_fd < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    struct stat file0_stat{};
    struct stat file1_stat{};
    if (fstat(file0_fd, &file0_stat) != 0) {
        return KModErr::ERR_MODULE_READ_FILE;
    }
    if (fstat(file1_fd, &file1_stat) != 0) {
        return KModErr::ERR_MODULE_READ_FILE;
    }

    scoped_kpath file0_kpath{};
    scoped_kpath file1_kpath{};
    err = acquire_kpath(file0_path, file0_kpath);
    if (is_failed(err)) {
        return err;
    }
    err = acquire_kpath(file1_path, file1_kpath);
    if (is_failed(err)) {
        return err;
    }

    uint32_t d_inode_offset = 0;
    err = get_dentry_d_inode_offset(d_inode_offset);
    if (is_failed(err)) {
        return err;
    }
    uint64_t file0_inode = 0;
    uint64_t file1_inode = 0;
    err = read_kernel_mem(file0_kpath.kp.dentry + d_inode_offset, &file0_inode,
                          sizeof(file0_inode));
    if (is_failed(err)) {
        return err;
    }
    err = read_kernel_mem(file1_kpath.kp.dentry + d_inode_offset, &file1_inode,
                          sizeof(file1_inode));
    if (is_failed(err)) {
        return err;
    }

    const uint64_t ino0 = static_cast<uint64_t>(file0_stat.st_ino);
    const uint64_t ino1 = static_cast<uint64_t>(file1_stat.st_ino);
    offset = 0;
    for (uint64_t shift = 0; shift < 256; shift += 4) {
        uint64_t value0 = 0;
        uint64_t value1 = 0;
        err = read_kernel_mem(file0_inode + shift, &value0, sizeof(value0));
        if (is_failed(err)) {
            return err;
        }
        err = read_kernel_mem(file1_inode + shift, &value1, sizeof(value1));
        if (is_failed(err)) {
            return err;
        }
        if (value0 == ino0 && value1 == ino1) {
            offset = static_cast<uint32_t>(shift);
            break;
        }
    }
    return offset == 0 ? KModErr::ERR_MODULE_OFFSET_NOT_FOUND : KModErr::OK;
}

// i_size carries the file length the probe files were created with; the two
// lengths differ, so the slot that matches both fstat() sizes is i_size.
KModErr get_inode_i_size_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_inode_btf_offset("i_size", 8, offset, available);
    if (available) return btf_error;

    KModErr err = init_time_offset_test_file();
    if (is_failed(err)) {
        return err;
    }

    const std::string file0_path = get_temp_file_path(0);
    const std::string file1_path = get_temp_file_path(1);

    const int file0_fd = open(file0_path.c_str(), O_RDONLY);
    const int file1_fd = open(file1_path.c_str(), O_RDONLY);
    fd_guard file0_guard{file0_fd};
    fd_guard file1_guard{file1_fd};
    if (file0_fd < 0 || file1_fd < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    struct stat file0_stat{};
    struct stat file1_stat{};
    if (fstat(file0_fd, &file0_stat) != 0) {
        return KModErr::ERR_MODULE_READ_FILE;
    }
    if (fstat(file1_fd, &file1_stat) != 0) {
        return KModErr::ERR_MODULE_READ_FILE;
    }

    scoped_kpath file0_kpath{};
    scoped_kpath file1_kpath{};
    err = acquire_kpath(file0_path, file0_kpath);
    if (is_failed(err)) {
        return err;
    }
    err = acquire_kpath(file1_path, file1_kpath);
    if (is_failed(err)) {
        return err;
    }

    uint32_t d_inode_offset = 0;
    err = get_dentry_d_inode_offset(d_inode_offset);
    if (is_failed(err)) {
        return err;
    }
    uint64_t file0_inode = 0;
    uint64_t file1_inode = 0;
    err = read_kernel_mem(file0_kpath.kp.dentry + d_inode_offset, &file0_inode,
                          sizeof(file0_inode));
    if (is_failed(err)) {
        return err;
    }
    err = read_kernel_mem(file1_kpath.kp.dentry + d_inode_offset, &file1_inode,
                          sizeof(file1_inode));
    if (is_failed(err)) {
        return err;
    }

    const uint64_t size0 = static_cast<uint64_t>(file0_stat.st_size);
    const uint64_t size1 = static_cast<uint64_t>(file1_stat.st_size);
    offset = 0;
    for (uint64_t shift = 0; shift < 256; shift += 4) {
        uint64_t value0 = 0;
        uint64_t value1 = 0;
        err = read_kernel_mem(file0_inode + shift, &value0, sizeof(value0));
        if (is_failed(err)) {
            return err;
        }
        err = read_kernel_mem(file1_inode + shift, &value1, sizeof(value1));
        if (is_failed(err)) {
            return err;
        }
        if (value0 == size0 && value1 == size1) {
            offset = static_cast<uint32_t>(shift);
            break;
        }
    }
    return offset == 0 ? KModErr::ERR_MODULE_OFFSET_NOT_FOUND : KModErr::OK;
}

// i_rdev is not covered by fstat() for regular files, so the probe opens two
// character devices whose encoded device numbers are known in advance: the
// 4-byte slot that holds 0x100003 for /dev/null and 0x100005 for /dev/zero is
// the merged 32-bit i_rdev/i_mode union field.
KModErr get_inode_i_rdev_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_inode_btf_offset("i_rdev", 4, offset, available);
    if (available) return btf_error;

    const char* const dev_null_path = "/dev/null";
    const char* const dev_zero_path = "/dev/zero";

    const std::string file0_path(dev_null_path);
    const std::string file1_path(dev_zero_path);
    const int file0_fd = open(dev_null_path, O_RDONLY);
    const int file1_fd = open(dev_zero_path, O_RDONLY);
    fd_guard file0_guard{file0_fd};
    fd_guard file1_guard{file1_fd};
    if (file0_fd < 0 || file1_fd < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    scoped_kpath file0_kpath{};
    scoped_kpath file1_kpath{};
    KModErr err = acquire_kpath(file0_path, file0_kpath);
    if (is_failed(err)) {
        return err;
    }
    err = acquire_kpath(file1_path, file1_kpath);
    if (is_failed(err)) {
        return err;
    }

    uint32_t d_inode_offset = 0;
    err = get_dentry_d_inode_offset(d_inode_offset);
    if (is_failed(err)) {
        return err;
    }
    uint64_t file0_inode = 0;
    uint64_t file1_inode = 0;
    err = read_kernel_mem(file0_kpath.kp.dentry + d_inode_offset, &file0_inode,
                          sizeof(file0_inode));
    if (is_failed(err)) {
        return err;
    }
    err = read_kernel_mem(file1_kpath.kp.dentry + d_inode_offset, &file1_inode,
                          sizeof(file1_inode));
    if (is_failed(err)) {
        return err;
    }

    constexpr uint32_t kDevNullEncoded = 0x100003;  // new_encode_dev(makedev(1, 3))
    constexpr uint32_t kDevZeroEncoded = 0x100005;  // new_encode_dev(makedev(1, 5))
    offset = 0;
    for (uint64_t shift = 0; shift < 256; shift += 4) {
        uint32_t value0 = 0;
        uint32_t value1 = 0;
        err = read_kernel_mem(file0_inode + shift, &value0, sizeof(value0));
        if (is_failed(err)) {
            return err;
        }
        err = read_kernel_mem(file1_inode + shift, &value1, sizeof(value1));
        if (is_failed(err)) {
            return err;
        }
        if (value0 == kDevNullEncoded && value1 == kDevZeroEncoded) {
            offset = static_cast<uint32_t>(shift);
            break;
        }
    }
    return offset == 0 ? KModErr::ERR_MODULE_OFFSET_NOT_FOUND : KModErr::OK;
}

// i_mode is the first 16-bit word of struct inode. The probe creates a regular
// file (0777), a second regular file (0000) and a directory (0755); their
// st_mode values therefore differ, and only the i_mode slot holds all three at
// the same 2-byte aligned offset.
KModErr get_inode_i_mode_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_inode_btf_offset("i_mode", 2, offset, available);
    if (available) return btf_error;

    uint32_t d_inode_offset = 0;
    KModErr err = get_dentry_d_inode_offset(d_inode_offset);
    if (is_failed(err)) {
        return err;
    }

    const std::string file_path = get_temp_file_path(10);
    const std::string empty_path = get_temp_file_path(11);
    const std::string dir_path = get_temp_file_path(12);
    file_utils::delete_path(std::filesystem::path(file_path));
    file_utils::delete_path(std::filesystem::path(empty_path));
    file_utils::delete_path(std::filesystem::path(dir_path));

    const int file_fd = open(file_path.c_str(), O_RDWR | O_CREAT | O_TRUNC, 0777);
    const int empty_fd = open(empty_path.c_str(), O_RDWR | O_CREAT | O_TRUNC, 0);
    const int mkdir_result = mkdir(dir_path.c_str(), 0755);
    const int dir_fd = open(dir_path.c_str(), O_RDONLY | O_DIRECTORY);
    fd_guard file_guard{file_fd};
    fd_guard empty_guard{empty_fd};
    fd_guard dir_guard{dir_fd};
    (void)mkdir_result;
    if ((file_fd | empty_fd | dir_fd) < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    if (fchmod(file_fd, 0777) != 0 || fchmod(empty_fd, 0) != 0 ||
        fchmod(dir_fd, 0755) != 0) {
        return KModErr::ERR_MODULE_WRITE_FILE;
    }

    struct stat file_stat{};
    struct stat empty_stat{};
    struct stat dir_stat{};
    if (fstat(file_fd, &file_stat) != 0) {
        return KModErr::ERR_MODULE_READ_FILE;
    }
    if (fstat(empty_fd, &empty_stat) != 0) {
        return KModErr::ERR_MODULE_READ_FILE;
    }
    if (fstat(dir_fd, &dir_stat) != 0) {
        return KModErr::ERR_MODULE_READ_FILE;
    }
    const uint16_t file_mode = static_cast<uint16_t>(file_stat.st_mode);
    const uint16_t empty_mode = static_cast<uint16_t>(empty_stat.st_mode);
    const uint16_t dir_mode = static_cast<uint16_t>(dir_stat.st_mode);

    auto get_inode_kaddr = [&d_inode_offset](const std::string& path,
                                             uint64_t& out_inode) -> KModErr {
        scoped_kpath sk{};
        KModErr walk_err = acquire_kpath(path, sk);
        if (is_failed(walk_err)) {
            return walk_err;
        }
        return read_kernel_mem(sk.kp.dentry + d_inode_offset, &out_inode,
                               sizeof(out_inode));
    };

    uint64_t file_inode = 0;
    uint64_t empty_inode = 0;
    uint64_t dir_inode = 0;
    err = get_inode_kaddr(file_path, file_inode);
    if (is_failed(err)) {
        return err;
    }
    err = get_inode_kaddr(empty_path, empty_inode);
    if (is_failed(err)) {
        return err;
    }
    err = get_inode_kaddr(dir_path, dir_inode);
    if (is_failed(err)) {
        return err;
    }
    if (file_inode == 0 || empty_inode == 0 || dir_inode == 0) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }

    constexpr uint32_t kSnapshotBytes = 1536;
    std::vector<uint8_t> file_snapshot(kSnapshotBytes);
    std::vector<uint8_t> empty_snapshot(kSnapshotBytes);
    std::vector<uint8_t> dir_snapshot(kSnapshotBytes);
    err = read_kernel_mem(file_inode, file_snapshot.data(), kSnapshotBytes);
    if (is_failed(err)) {
        return err;
    }
    err = read_kernel_mem(empty_inode, empty_snapshot.data(), kSnapshotBytes);
    if (is_failed(err)) {
        return err;
    }
    err = read_kernel_mem(dir_inode, dir_snapshot.data(), kSnapshotBytes);
    if (is_failed(err)) {
        return err;
    }

    offset = 0;
    uint32_t match_count = 0;
    for (uint64_t shift = 0; shift < kSnapshotBytes - 2; shift += 2) {
        if (snapshot_u16(file_snapshot, shift) == file_mode &&
            snapshot_u16(empty_snapshot, shift) == empty_mode &&
            snapshot_u16(dir_snapshot, shift) == dir_mode) {
            offset = static_cast<uint32_t>(shift);
            ++match_count;
        }
    }
    if (match_count > 1) {
        if (snapshot_u16(file_snapshot, 0) == file_mode &&
            snapshot_u16(empty_snapshot, 0) == empty_mode &&
            snapshot_u16(dir_snapshot, 0) == dir_mode) {
            offset = 0;
            return KModErr::OK;
        }
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
}

// i_opflags is a 16-bit word located right after i_mode. The probe compares the
// operation flags of a regular file, a directory and the /dev/null character
// device: only a real i_opflags slot shows the same flag pattern the three
// inodes publish while also staying below the flag-space ceiling.
KModErr get_inode_i_opflags_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_inode_btf_offset("i_opflags", 2, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("inode_i_opflags", offset);
    if (is_ok(err)) {
        return err;
    }

    uint32_t mode_offset = 0;
    err = get_inode_i_mode_offset(mode_offset);
    if (is_failed(err)) {
        return err;
    }

    err = init_time_offset_test_file();
    if (is_failed(err)) {
        return err;
    }

    const std::string file_path = get_temp_file_path(0);
    const std::string dir_path = get_temp_file_path(1) + "_dir";
    file_utils::delete_path(std::filesystem::path(dir_path));
    const int mkdir_result = mkdir(dir_path.c_str(), 0755);
    if (mkdir_result != 0 && errno != EEXIST) {
        return KModErr::ERR_MODULE_CREATE_EMPTY_FILE;
    }

    const int file_fd = open(file_path.c_str(), O_RDONLY);
    const int dir_fd = open(dir_path.c_str(), O_RDONLY | O_DIRECTORY);
    const int null_fd = open("/dev/null", O_RDONLY);
    fd_guard file_guard{file_fd};
    fd_guard dir_guard{dir_fd};
    fd_guard null_guard{null_fd};
    if ((file_fd | dir_fd | null_fd) < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    uint32_t d_inode_offset = 0;
    err = get_dentry_d_inode_offset(d_inode_offset);
    if (is_failed(err)) {
        return err;
    }
    auto get_inode_kaddr = [&d_inode_offset](const std::string& path,
                                             uint64_t& out_inode) -> KModErr {
        scoped_kpath sk{};
        KModErr walk_err = acquire_kpath(path, sk);
        if (is_failed(walk_err)) {
            return walk_err;
        }
        return read_kernel_mem(sk.kp.dentry + d_inode_offset, &out_inode,
                               sizeof(out_inode));
    };

    uint64_t file_inode = 0;
    uint64_t dir_inode = 0;
    uint64_t null_inode = 0;
    err = get_inode_kaddr(file_path, file_inode);
    if (is_failed(err)) {
        return err;
    }
    err = get_inode_kaddr(dir_path, dir_inode);
    if (is_failed(err)) {
        return err;
    }
    const std::string null_path("/dev/null");
    err = get_inode_kaddr(null_path, null_inode);
    if (is_failed(err)) {
        return err;
    }
    if (file_inode == 0 || dir_inode == 0 || null_inode == 0) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }

    constexpr uint32_t kSnapshotBytes = 1024;
    std::vector<uint8_t> file_snapshot(kSnapshotBytes);
    std::vector<uint8_t> dir_snapshot(kSnapshotBytes);
    std::vector<uint8_t> null_snapshot(kSnapshotBytes);
    err = read_kernel_mem(file_inode, file_snapshot.data(), kSnapshotBytes);
    if (is_failed(err)) {
        return err;
    }
    err = read_kernel_mem(dir_inode, dir_snapshot.data(), kSnapshotBytes);
    if (is_failed(err)) {
        return err;
    }
    err = read_kernel_mem(null_inode, null_snapshot.data(), kSnapshotBytes);
    if (is_failed(err)) {
        return err;
    }

    offset = 0;
    uint32_t match_count = 0;
    uint32_t found_offset = 0;
    for (uint64_t shift = 0; shift <= kSnapshotBytes - 3; shift += 2) {
        if (shift == mode_offset) {
            continue;
        }
        const uint32_t file_flags = snapshot_u16(file_snapshot, shift);
        const uint32_t dir_flags = snapshot_u16(dir_snapshot, shift);
        const uint32_t null_flags = snapshot_u16(null_snapshot, shift);
        const bool plausible = file_flags < 512 && dir_flags < 512 && null_flags < 512;
        const bool matched = plausible && (dir_flags & 2) != 0 &&
                             (file_flags & 2) == 0 && (null_flags & 2) == 0 &&
                             (dir_flags & 8) != 0 && (file_flags & 8) != 0 &&
                             (null_flags & 8) == 0;
        if (!matched) {
            continue;
        }
        ++match_count;
        found_offset = static_cast<uint32_t>(shift);
        if (shift == static_cast<uint64_t>(mode_offset) + 2 ||
            shift == static_cast<uint64_t>(mode_offset) - 2) {
            offset = found_offset;
            return put_offset_cache("inode_i_opflags", offset);
        }
    }
    if (match_count == 1) {
        offset = found_offset;
        return put_offset_cache("inode_i_opflags", offset);
    }
    return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
}

// i_sb points at the superblock the inode belongs to. The probe file lives on a
// regular filesystem, while the socket inode belongs to sockfs; the slot that
// points at the same object for two files on one filesystem, differs for the
// socket inode, and whose target starts with the filesystem type fstatfs()
// reports is i_sb.
KModErr get_inode_i_sb_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_inode_btf_offset("i_sb", 8, offset, available);
    if (available) return btf_error;

    KModErr err = init_time_offset_test_file();
    if (is_failed(err)) {
        return err;
    }

    const std::string file0_path = get_temp_file_path(0);
    const std::string file1_path = get_temp_file_path(1);
    const int file0_fd = open(file0_path.c_str(), O_RDONLY);
    const int file1_fd = open(file1_path.c_str(), O_RDONLY);
    const int socket_fd = socket(AF_INET, SOCK_STREAM, 0);
    fd_guard file0_guard{file0_fd};
    fd_guard file1_guard{file1_fd};
    fd_guard socket_guard{socket_fd};
    if (file0_fd < 0 || file1_fd < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }
    if (socket_fd < 0) {
        return KModErr::ERR_MODULE_SOCKET;
    }

    struct statfs filesystem{};
    if (fstatfs(file0_fd, &filesystem) != 0) {
        return KModErr::ERR_MODULE_READ_FILE;
    }
    const uint64_t fs_type = static_cast<uint64_t>(filesystem.f_type);

    scoped_kpath file0_kpath{};
    scoped_kpath file1_kpath{};
    err = acquire_kpath(file0_path, file0_kpath);
    if (is_failed(err)) {
        return err;
    }
    err = acquire_kpath(file1_path, file1_kpath);
    if (is_failed(err)) {
        return err;
    }

    uint32_t d_inode_offset = 0;
    err = get_dentry_d_inode_offset(d_inode_offset);
    if (is_failed(err)) {
        return err;
    }
    uint64_t file0_inode = 0;
    uint64_t file1_inode = 0;
    err = read_kernel_mem(file0_kpath.kp.dentry + d_inode_offset, &file0_inode,
                          sizeof(file0_inode));
    if (is_failed(err)) {
        return err;
    }
    err = read_kernel_mem(file1_kpath.kp.dentry + d_inode_offset, &file1_inode,
                          sizeof(file1_inode));
    if (is_failed(err)) {
        return err;
    }

    uint64_t socket_file = 0;
    err = get_current_task_file_ptr(socket_fd, socket_file);
    if (is_failed(err)) {
        return err;
    }
    uint32_t file_inode_offset = 0;
    err = get_file_f_inode_offset(file_inode_offset);
    if (is_failed(err)) {
        return err;
    }
    uint64_t socket_inode = 0;
    err = read_kernel_mem(socket_file + file_inode_offset, &socket_inode,
                          sizeof(socket_inode));
    if (is_failed(err)) {
        return err;
    }

    offset = 0;
    const uint32_t fs_type32 = static_cast<uint32_t>(fs_type);
    for (uint64_t shift = 0; shift < 512; shift += 8) {
        uint64_t value0 = 0;
        uint64_t value1 = 0;
        uint64_t value2 = 0;
        err = read_kernel_mem(file0_inode + shift, &value0, sizeof(value0));
        if (is_failed(err)) {
            return err;
        }
        err = read_kernel_mem(file1_inode + shift, &value1, sizeof(value1));
        if (is_failed(err)) {
            return err;
        }
        err = read_kernel_mem(socket_inode + shift, &value2, sizeof(value2));
        if (is_failed(err)) {
            return err;
        }
        if (!is_kernel_address(value0) || value0 != value1 || value0 == value2) {
            continue;
        }

        constexpr uint32_t kSuperblockPrefix = 256;
        std::vector<uint8_t> superblock(kSuperblockPrefix);
        KModErr read_err =
                read_kernel_mem(value0, superblock.data(), kSuperblockPrefix);
        if (is_failed(read_err)) {
            return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
        }
        for (uint64_t field = 0; field < kSuperblockPrefix - 8; field += 4) {
            uint32_t type32 = 0;
            memcpy(&type32, superblock.data() + field, sizeof(type32));
            if (type32 == fs_type32 || snapshot_u64(superblock, field) == fs_type) {
                offset = static_cast<uint32_t>(shift);
                return KModErr::OK;
            }
        }
    }
    return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
}

// i_mapping points at the address_space that hosts the inode's page cache. For
// both probe files that object points back at the inode it belongs to, at the
// same distance; the slot whose value lives inside its own inode and resolves
// back to that inode at an identical distance in both files is i_mapping.
KModErr get_inode_i_mapping_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_inode_btf_offset("i_mapping", 8, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("inode_i_mapping", offset);
    if (is_ok(err)) {
        return err;
    }

    err = init_time_offset_test_file();
    if (is_failed(err)) {
        return err;
    }

    const std::string file0_path = get_temp_file_path(0);
    const std::string file1_path = get_temp_file_path(1);
    const int file0_fd = open(file0_path.c_str(), O_RDONLY);
    const int file1_fd = open(file1_path.c_str(), O_RDONLY);
    fd_guard file0_guard{file0_fd};
    fd_guard file1_guard{file1_fd};
    if (file0_fd < 0 || file1_fd < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    scoped_kpath file0_kpath{};
    scoped_kpath file1_kpath{};
    err = acquire_kpath(file0_path, file0_kpath);
    if (is_failed(err)) {
        return err;
    }
    err = acquire_kpath(file1_path, file1_kpath);
    if (is_failed(err)) {
        return err;
    }

    uint32_t d_inode_offset = 0;
    err = get_dentry_d_inode_offset(d_inode_offset);
    if (is_failed(err)) {
        return err;
    }
    uint64_t file0_inode = 0;
    uint64_t file1_inode = 0;
    err = read_kernel_mem(file0_kpath.kp.dentry + d_inode_offset, &file0_inode,
                          sizeof(file0_inode));
    if (is_failed(err)) {
        return err;
    }
    err = read_kernel_mem(file1_kpath.kp.dentry + d_inode_offset, &file1_inode,
                          sizeof(file1_inode));
    if (is_failed(err)) {
        return err;
    }

    constexpr uint64_t kInodeWindow = 2048;
    constexpr uint64_t kScanLimit = 512;
    constexpr uint64_t kProbeLimit = 256;
    for (uint64_t shift = 0; shift < kScanLimit; shift += 8) {
        uint64_t mapping0 = 0;
        uint64_t mapping1 = 0;
        err = read_kernel_mem(file0_inode + shift, &mapping0, sizeof(mapping0));
        if (is_failed(err)) {
            return err;
        }
        err = read_kernel_mem(file1_inode + shift, &mapping1, sizeof(mapping1));
        if (is_failed(err)) {
            return err;
        }
        if (mapping0 <= file0_inode || mapping0 > file0_inode + kInodeWindow) {
            continue;
        }
        if (mapping1 <= file1_inode || mapping1 > file1_inode + kInodeWindow ||
            mapping1 == file1_inode + shift) {
            continue;
        }

        int32_t back0 = -1;
        int32_t back1 = -1;
        for (uint64_t probe = 0; probe <= kProbeLimit - 1; probe += 8) {
            uint64_t value0 = 0;
            uint64_t value1 = 0;
            bool ok0 = false;
            bool ok1 = false;
            err = safe_read_kaddr_t(mapping0 + probe, ok0, value0);
            if (is_failed(err)) {
                return err;
            }
            if (ok0 && value0 == file0_inode) {
                back0 = static_cast<int32_t>(probe);
            }
            err = safe_read_kaddr_t(mapping1 + probe, ok1, value1);
            if (is_failed(err)) {
                return err;
            }
            if (ok1 && value1 == file1_inode) {
                back1 = static_cast<int32_t>(probe);
            }
        }
        if (back0 == -1 || back0 != back1) {
            continue;
        }
        if (mapping0 - file0_inode != mapping1 - file1_inode) {
            continue;
        }
        offset = static_cast<uint32_t>(shift);
        return put_offset_cache("inode_i_mapping", offset);
    }
    return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
}

// i_op points at the inode_operations table of the filesystem in use. The probe
// resolves the candidate tables of the common filesystems through kallsyms and
// reports the inode slot that holds exactly one of them.
KModErr get_inode_i_op_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_inode_btf_offset("i_op", 8, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("inode_i_op", offset);
    if (is_ok(err)) {
        return err;
    }

    const std::vector<const char*> candidates = {
            "ext4_file_inode_operations",  "f2fs_file_inode_operations",
            "shmem_file_inode_operations", "erofs_file_inode_operations",
            "overlay_file_inode_operations", "btrfs_file_inode_operations",
            "vfat_file_inode_operations",
    };
    std::vector<uint64_t> addresses;
    for (const char* name : candidates) {
        SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8 silent_guard;
        uint64_t address = 0;
        KModErr lookup_err = kallsyms_lookup_name(name, address);
        if (is_ok(lookup_err) && address != 0) {
            addresses.push_back(address);
        }
    }
    if (addresses.empty()) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }

    err = init_time_offset_test_file();
    if (is_failed(err)) {
        return err;
    }

    const std::string file_path = get_temp_file_path(0);
    const int file_fd = open(file_path.c_str(), O_RDONLY);
    fd_guard file_guard{file_fd};
    if (file_fd < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    scoped_kpath file_kpath{};
    err = acquire_kpath(file_path, file_kpath);
    if (is_failed(err)) {
        return err;
    }
    uint32_t d_inode_offset = 0;
    err = get_dentry_d_inode_offset(d_inode_offset);
    if (is_failed(err)) {
        return err;
    }
    uint64_t inode = 0;
    err = read_kernel_mem(file_kpath.kp.dentry + d_inode_offset, &inode,
                          sizeof(inode));
    if (is_failed(err)) {
        return err;
    }
    if (!is_kernel_address(inode)) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }

    uint32_t match_count = 0;
    uint32_t found_offset = 0;
    for (uint64_t shift = 0; shift <= 1023; shift += 8) {
        uint64_t value = 0;
        bool ok = false;
        err = safe_read_kaddr_t(inode + shift, ok, value);
        if (is_failed(err)) {
            return err;
        }
        if (!ok || !is_kernel_address(value)) {
            continue;
        }
        for (uint64_t address : addresses) {
            if (value == address) {
                ++match_count;
                found_offset = static_cast<uint32_t>(shift);
                break;
            }
        }
    }
    if (match_count != 1) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    offset = found_offset;
    return put_offset_cache("inode_i_op", offset);
}

// i_fop points at the file_operations table of the filesystem in use; the probe
// resolves the common filesystem tables through kallsyms and reports the inode
// slot that holds exactly one of them.
KModErr get_inode_i_fop_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_inode_btf_offset("i_fop", 8, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("inode_i_fop", offset);
    if (is_ok(err)) {
        return err;
    }

    const std::vector<const char*> candidates = {
            "ext4_file_operations",  "f2fs_file_operations",
            "shmem_file_operations", "erofs_file_operations",
            "overlay_file_operations", "btrfs_file_operations",
            "vfat_file_operations",
    };
    std::vector<uint64_t> addresses;
    for (const char* name : candidates) {
        SymbolLookupSilentGuard_c67c0e4fed21dd11b34c67e0a5b878c8 silent_guard;
        uint64_t address = 0;
        KModErr lookup_err = kallsyms_lookup_name(name, address);
        if (is_ok(lookup_err) && address != 0) {
            addresses.push_back(address);
        }
    }
    if (addresses.empty()) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }

    err = init_time_offset_test_file();
    if (is_failed(err)) {
        return err;
    }

    const std::string file_path = get_temp_file_path(0);
    const int file_fd = open(file_path.c_str(), O_RDONLY);
    fd_guard file_guard{file_fd};
    if (file_fd < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    scoped_kpath file_kpath{};
    err = acquire_kpath(file_path, file_kpath);
    if (is_failed(err)) {
        return err;
    }
    uint32_t d_inode_offset = 0;
    err = get_dentry_d_inode_offset(d_inode_offset);
    if (is_failed(err)) {
        return err;
    }
    uint64_t inode = 0;
    err = read_kernel_mem(file_kpath.kp.dentry + d_inode_offset, &inode,
                          sizeof(inode));
    if (is_failed(err)) {
        return err;
    }
    if (!is_kernel_address(inode)) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }

    uint32_t match_count = 0;
    uint32_t found_offset = 0;
    for (uint64_t shift = 0; shift <= 1023; shift += 8) {
        uint64_t value = 0;
        bool ok = false;
        err = safe_read_kaddr_t(inode + shift, ok, value);
        if (is_failed(err)) {
            return err;
        }
        if (!ok || !is_kernel_address(value)) {
            continue;
        }
        for (uint64_t address : addresses) {
            if (value == address) {
                ++match_count;
                found_offset = static_cast<uint32_t>(shift);
                break;
            }
        }
    }
    if (match_count != 1) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    offset = found_offset;
    return put_offset_cache("inode_i_fop", offset);
}

// i_state carries the inode dirty/lock state. Writing one byte through the open
// probe file marks the inode dirty; fsync clears that state again. The qword
// that changes on write, has its low dirty bits set only between write and
// fsync, and is clean afterwards is i_state.
KModErr get_inode_i_state_offset(uint32_t& offset) {
    KernelFieldDescription field;
    bool available = false;
    KModErr btf_error = maplesu_resolve_btf_field("inode", "i_state", 0, field, available);
    if (available) {
        if (is_failed(btf_error)) return btf_error;
        if (field.width_bytes != 4 && field.width_bytes != 8)
            return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
        offset = field.offset_bytes;
        return KModErr::OK;
    }

    const std::string file_path = get_temp_file_path(0);
    const int file_fd = open(file_path.c_str(), O_RDWR | O_CREAT | O_TRUNC, 0600);
    fd_guard file_guard{file_fd};
    if (file_fd < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    scoped_kpath file_kpath{};
    KModErr err = acquire_kpath(file_path, file_kpath);
    if (is_failed(err)) {
        return err;
    }
    uint32_t d_inode_offset = 0;
    err = get_dentry_d_inode_offset(d_inode_offset);
    if (is_failed(err)) {
        return err;
    }
    uint64_t inode = 0;
    err = read_kernel_mem(file_kpath.kp.dentry + d_inode_offset, &inode,
                          sizeof(inode));
    if (is_failed(err)) {
        return err;
    }

    uint32_t size_offset = 0;
    err = get_inode_i_size_offset(size_offset);
    if (is_failed(err)) {
        return err;
    }

    constexpr uint64_t kQwordCount = 128;
    constexpr uint32_t kSnapshotBytes = kQwordCount * sizeof(uint64_t);
    std::vector<uint64_t> initial(kQwordCount);
    std::vector<uint64_t> written(kQwordCount);
    std::vector<uint64_t> synced(kQwordCount);

    err = read_kernel_mem(inode + size_offset, initial.data(), kSnapshotBytes);
    if (is_failed(err)) {
        return err;
    }

    const char marker = 'A';
    if (write(file_fd, &marker, 1) != 1) {
        return KModErr::ERR_MODULE_WRITE_FILE;
    }

    err = read_kernel_mem(inode + size_offset, written.data(), kSnapshotBytes);
    if (is_failed(err)) {
        return err;
    }

    (void)fsync(file_fd);

    err = read_kernel_mem(inode + size_offset, synced.data(), kSnapshotBytes);
    if (is_failed(err)) {
        return err;
    }

    offset = 0;
    uint32_t match_count = 0;
    for (uint64_t index = 0; index < kQwordCount; ++index) {
        const uint64_t value0 = initial[index];
        const uint64_t value1 = written[index];
        const uint64_t value2 = synced[index];
        if (value2 == value0 || value2 == value1) {
            continue;
        }
        if ((value1 & 7) == 0 || (value2 & 7) != 0) {
            continue;
        }
        offset = static_cast<uint32_t>(index * sizeof(uint64_t)) + size_offset;
        ++match_count;
    }
    if (match_count != 1) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    return KModErr::OK;
}

// Linux < 4.7.0 keeps the block device / character device / pipe / link /
// directory-cursor union at the end of struct inode. The probe opens a real
// block device, /dev/null, a pipe and a regular file: the regular file keeps the
// union empty while the other three point at distinct kernel objects, so the
// all-zero slot that turns into three pairwise distinct kernel pointers is the
// union field.
KModErr get_inode_i_bdev_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_inode_btf_offset("i_bdev", 8, offset, available);
    if (available) return btf_error;

    if (!is_kernel_version_less("4.7.0")) {
        return KModErr::ERR_MODULE_OFFSET_NOT_MATCH_LINUX_VER;
    }

    static const char* const kBlockCandidates[] = {
            "/dev/block/by-name/boot", "/dev/block/by-name/userdata",
            "/dev/block/loop0", "/dev/block/loop1", "/dev/block/ram0",
    };
    int block_fd = -1;
    for (const char* candidate : kBlockCandidates) {
        block_fd = open(candidate, O_RDONLY);
        if (block_fd >= 0) {
            break;
        }
    }
    if (block_fd < 0) {
        return KModErr::ERR_MODULE_OPEN_DEV_BLOCK;
    }
    fd_guard block_guard{block_fd};

    KModErr err = init_time_offset_test_file();
    if (is_failed(err)) {
        return err;
    }

    const std::string file_path = get_temp_file_path(0);
    const int file_fd = open(file_path.c_str(), O_RDONLY);
    const int null_fd = open("/dev/null", O_RDONLY);
    fd_guard file_guard{file_fd};
    fd_guard null_guard{null_fd};
    if ((file_fd | null_fd) < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    int pipe_fds[2] = {-1, -1};
    if (pipe(pipe_fds) != 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }
    fd_guard pipe_read_guard{pipe_fds[0]};
    fd_guard pipe_write_guard{pipe_fds[1]};

    uint32_t d_inode_offset = 0;
    err = get_dentry_d_inode_offset(d_inode_offset);
    if (is_failed(err)) {
        return err;
    }
    uint32_t file_inode_offset = 0;
    err = get_file_f_inode_offset(file_inode_offset);
    if (is_failed(err)) {
        return err;
    }

    scoped_kpath file_kpath{};
    err = acquire_kpath(file_path, file_kpath);
    if (is_failed(err)) {
        return err;
    }
    uint64_t file_inode = 0;
    err = read_kernel_mem(file_kpath.kp.dentry + d_inode_offset, &file_inode,
                          sizeof(file_inode));
    if (is_failed(err)) {
        return err;
    }

    const std::string null_path("/dev/null");
    scoped_kpath null_kpath{};
    err = acquire_kpath(null_path, null_kpath);
    if (is_failed(err)) {
        return err;
    }
    uint64_t null_inode = 0;
    err = read_kernel_mem(null_kpath.kp.dentry + d_inode_offset, &null_inode,
                          sizeof(null_inode));
    if (is_failed(err)) {
        return err;
    }

    uint64_t block_file = 0;
    err = get_current_task_file_ptr(block_fd, block_file);
    if (is_failed(err)) {
        return err;
    }
    uint64_t block_inode = 0;
    err = read_kernel_mem(block_file + file_inode_offset, &block_inode,
                          sizeof(block_inode));
    if (is_failed(err)) {
        return err;
    }

    uint64_t pipe_file = 0;
    err = get_current_task_file_ptr(pipe_fds[0], pipe_file);
    if (is_failed(err)) {
        return err;
    }
    uint64_t pipe_inode = 0;
    err = read_kernel_mem(pipe_file + file_inode_offset, &pipe_inode,
                          sizeof(pipe_inode));
    if (is_failed(err)) {
        return err;
    }

    offset = 0;
    for (uint64_t shift = 0; shift < 1024; shift += 8) {
        uint64_t value0 = 0;
        uint64_t value1 = 0;
        uint64_t value2 = 0;
        uint64_t value3 = 0;
        err = read_kernel_mem(file_inode + shift, &value0, sizeof(value0));
        if (is_failed(err)) {
            return err;
        }
        err = read_kernel_mem(null_inode + shift, &value1, sizeof(value1));
        if (is_failed(err)) {
            return err;
        }
        err = read_kernel_mem(block_inode + shift, &value2, sizeof(value2));
        if (is_failed(err)) {
            return err;
        }
        err = read_kernel_mem(pipe_inode + shift, &value3, sizeof(value3));
        if (is_failed(err)) {
            return err;
        }
        if (value0 != 0) {
            continue;
        }
        if (!is_kernel_address(value1) || !is_kernel_address(value2) ||
            !is_kernel_address(value3)) {
            continue;
        }
        if (value1 == value2 || value1 == value3 || value2 == value3) {
            continue;
        }
        offset = static_cast<uint32_t>(shift);
        return KModErr::OK;
    }
    return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
}

}  // namespace kernel_module
