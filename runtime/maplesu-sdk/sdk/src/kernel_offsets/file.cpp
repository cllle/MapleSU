// file implementation.
//
#include <fcntl.h>
#include <sys/eventfd.h>
#include <sys/socket.h>
#include <sys/xattr.h>
#include <unistd.h>

#include <algorithm>
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

namespace maplesu::sdk {

// The module's system file directory name: the first 16 characters of the
// module root key followed by that same first character again.
static inline std::string get_system_file_dir_name() {
    std::string root_key(__get_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW());
    std::string head = root_key.substr(0, FOLDER_HEAD_ROOT_KEY_LEN);
    return head + head[0];
}

// The module's system file directory, "/data/<17-character name>/".
static std::string get_system_file_dir_path() {
    char path_buf[1024] = {0};
    snprintf(path_buf, sizeof(path_buf), "/data/%s/", get_system_file_dir_name().c_str());
    return std::string(path_buf);
}

} // namespace maplesu::sdk

namespace kernel_module {

namespace {

// Kernel addresses are the pointer values at or above the top of the canonical
// user range; lower values are not kernel pointers.
constexpr uint64_t kKernelAddrMin = 0xFFFF000000000000ULL;

KModErr resolve_file_btf_offset(const char* member, uint32_t width,
                                uint32_t& offset, bool& available) {
    KernelFieldDescription field;
    KModErr error = maplesu_resolve_btf_field("file", member, width, field, available);
    if (available && is_ok(error)) offset = field.offset_bytes;
    return error;
}

} // namespace

// Builds the path of the probe file with the given index: the module's system
// file directory followed by a fixed name that differs per index.
static std::string get_temp_file_path(int index) {
    std::stringstream name_stream;
    name_stream << "temp" << index << "zsOi6N56u5pTIiCvZ5MpiCkRvMJJCHOd";
    const std::filesystem::path full_path =
        std::filesystem::path(maplesu::sdk::get_system_file_dir_path()) /
        std::filesystem::path(name_stream.str());
    return full_path.string();
}

// Creates or recreates the two probe files the resolvers below work on: every
// file is opened read-write, truncated to a distinct length, given mode 0777 and
// labelled system_file so the kernel-side probes can read it.
static KModErr init_f_op_offset_test_file() {
    KModErr err;
    for (uint64_t index = 0; index != 2; ++index) {
        std::string path = get_temp_file_path(static_cast<int>(index));
        fd_guard fd{open(path.c_str(), O_RDWR | O_CREAT | O_TRUNC, 0666)};
        const char *file_path = path.c_str();
        bool ok = false;
        if (fd.get() < 0) {
            err = KModErr::ERR_MODULE_CREATE_EMPTY_FILE;
        } else if (ftruncate(fd.get(), static_cast<off_t>(index + 4660)) != 0) {
            err = KModErr::ERR_MODULE_WRITE_FILE;
        } else if (chmod(file_path, 0777) != 0) {
            err = KModErr::ERR_SET_FILE_SELINUX;
        } else {
            ok = setxattr(file_path, "security.selinux", "u:object_r:system_file:s0", 26, 0) == 0;
            if (!ok) {
                err = KModErr::ERR_SET_FILE_SELINUX;
            }
        }
        if (!ok) {
            return err;
        }
    }
    return KModErr::OK;
}

// Locates struct path f_path of struct file.  The probe opens the module's
// system file directory and resolves that same path with kern_path(); the
// kernel publishes the result as a `struct path` of {mnt, dentry}.  The aligned
// slot of the open file's struct file that holds the dentry pointer therefore
// marks f_path's second member, so the field starts one pointer earlier.
KModErr get_file_f_path_offset(uint32_t &offset) {
    bool available = false;
    KModErr btf_error = resolve_file_btf_offset("f_path", 0, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("file_f_path", offset);
    if (is_ok(err)) {
        return KModErr::OK; // already cached
    }

    std::string dir_path = maplesu::sdk::get_system_file_dir_path();
    int dir_fd = open(dir_path.c_str(), O_RDONLY);
    fd_guard dir_fd_guard(dir_fd);
    if (dir_fd < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    uint64_t current_task = 0;
    err = get_kernel_shellcode_u64_result(
        [](asmjit::a64::Assembler *a, asmjit::a64::GpX &result) -> KModErr {
            result = asmjit::a64::regs::x10;
            RETURN_IF_ERROR(export_symbol::get_current_checked(a, asmjit::a64::regs::x10));
            return KModErr::OK;
        },
        current_task);
    if (is_failed(err)) {
        return err;
    }

    uint64_t file_ptr = 0;
    RETURN_IF_ERROR(get_current_task_file_ptr(dir_fd, file_ptr));

    scoped_kpath dir_kpath{};
    RETURN_IF_ERROR(acquire_kpath(dir_path, dir_kpath));
    if (dir_kpath.kp.dentry == 0) {
        return KModErr::ERR_MODULE_DENTRY_EMPTY;
    }

    uint32_t found_offset = 0;
    bool found = false;
    for (uint64_t probe = 0; probe < 100; probe += 4) {
        uint64_t slot = 0;
        RETURN_IF_ERROR(read_kernel_mem(file_ptr + probe, &slot, sizeof(slot)));
        if (slot == dir_kpath.kp.dentry) {
            if (probe < 8) return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
            found_offset = static_cast<uint32_t>(probe - 8);
            found = true;
            break;
        }
    }
    if (!found) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    offset = found_offset;
    return put_offset_cache("file_f_path", offset);
}

// Locates struct inode *f_inode of struct file.  The probe takes the inode
// pointer out of the dentry of the resolved system directory and then scans the
// aligned slots of the open file's struct file for that pointer.
KModErr get_file_f_inode_offset(uint32_t &offset) {
    bool available = false;
    KModErr btf_error = resolve_file_btf_offset("f_inode", 8, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("file_f_inode", offset);
    if (is_ok(err)) {
        return KModErr::OK; // already cached
    }

    std::string dir_path = maplesu::sdk::get_system_file_dir_path();
    int dir_fd = open(dir_path.c_str(), O_RDONLY);
    fd_guard dir_fd_guard(dir_fd);
    if (dir_fd < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    uint64_t current_task = 0;
    err = get_kernel_shellcode_u64_result(
        [](asmjit::a64::Assembler *a, asmjit::a64::GpX &result) -> KModErr {
            result = asmjit::a64::regs::x10;
            RETURN_IF_ERROR(export_symbol::get_current_checked(a, asmjit::a64::regs::x10));
            return KModErr::OK;
        },
        current_task);
    if (is_failed(err)) {
        return err;
    }

    uint64_t file_ptr = 0;
    RETURN_IF_ERROR(get_current_task_file_ptr(dir_fd, file_ptr));

    scoped_kpath dir_kpath{};
    RETURN_IF_ERROR(acquire_kpath(dir_path, dir_kpath));
    if (dir_kpath.kp.dentry == 0) {
        return KModErr::ERR_MODULE_DENTRY_EMPTY;
    }

    uint32_t d_inode_offset = 0;
    uint64_t inode_kaddr = 0;
    RETURN_IF_ERROR(get_dentry_d_inode_offset(d_inode_offset));
    RETURN_IF_ERROR(
        read_kernel_mem(dir_kpath.kp.dentry + d_inode_offset, &inode_kaddr, sizeof(inode_kaddr)));

    offset = 0;
    for (uint64_t probe = 0; probe < 256; probe += 4) {
        uint64_t slot = 0;
        RETURN_IF_ERROR(read_kernel_mem(file_ptr + probe, &slot, sizeof(slot)));
        if (slot == inode_kaddr) {
            offset = static_cast<uint32_t>(probe);
            break;
        }
    }
    if (offset == 0) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    return put_offset_cache("file_f_inode", offset);
}

// Locates struct file_operations *f_op of struct file.  Three descriptors are
// opened: two on the same regular probe file and one on a socket.  Their struct
// file slots are compared around the f_inode field; the slot that holds the same
// non-zero kernel pointer for both regular files but a different one for the
// socket is f_op.
KModErr get_file_f_op_offset(uint32_t &offset) {
    bool available = false;
    KModErr btf_error = resolve_file_btf_offset("f_op", 8, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("file_f_op", offset);
    if (is_ok(err)) {
        return KModErr::OK; // already cached
    }

    err = init_f_op_offset_test_file();
    if (is_failed(err)) {
        return err;
    }

    std::string file0_path = get_temp_file_path(0);
    std::string file1_path = get_temp_file_path(1);
    int file0_fd = open(file0_path.c_str(), O_RDONLY);
    int file1_fd = open(file1_path.c_str(), O_RDONLY);
    int socket_fd = socket(AF_INET, SOCK_STREAM, 0);
    fd_guard file0_guard(file0_fd);
    fd_guard file1_guard(file1_fd);
    fd_guard socket_guard(socket_fd);
    if ((file0_fd | file1_fd) < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }
    if (socket_fd < 0) {
        return KModErr::ERR_MODULE_SOCKET;
    }

    uint64_t file0_ptr = 0;
    uint64_t file1_ptr = 0;
    uint64_t socket_ptr = 0;
    RETURN_IF_ERROR(get_current_task_file_ptr(file0_fd, file0_ptr));
    RETURN_IF_ERROR(get_current_task_file_ptr(file1_fd, file1_ptr));
    RETURN_IF_ERROR(get_current_task_file_ptr(socket_fd, socket_ptr));

    uint32_t f_inode_offset = 0;
    RETURN_IF_ERROR(get_file_f_inode_offset(f_inode_offset));

    const uint32_t window_begin =
        static_cast<uint32_t>(std::max(static_cast<int32_t>(f_inode_offset), 0));
    const int32_t window_end = static_cast<int32_t>(f_inode_offset) + 32;
    const int32_t window_len = window_end - static_cast<int32_t>(window_begin);

    std::vector<uint8_t> file0_snapshot(static_cast<size_t>(window_len));
    std::vector<uint8_t> file1_snapshot(static_cast<size_t>(window_len));
    std::vector<uint8_t> socket_snapshot(static_cast<size_t>(window_len));

    RETURN_IF_ERROR(read_kernel_mem(file0_ptr + static_cast<uint64_t>(window_begin),
                                    file0_snapshot.data(), static_cast<uint32_t>(window_len)));
    RETURN_IF_ERROR(read_kernel_mem(file1_ptr + static_cast<uint64_t>(window_begin),
                                    file1_snapshot.data(), static_cast<uint32_t>(window_len)));
    RETURN_IF_ERROR(read_kernel_mem(socket_ptr + static_cast<uint64_t>(window_begin),
                                    socket_snapshot.data(), static_cast<uint32_t>(window_len)));

    std::vector<uint32_t> candidates;
    for (int64_t probe = 0; probe < static_cast<int64_t>(window_len); probe += 8) {
        const int64_t field_offset = static_cast<int64_t>(window_begin) + probe;
        const uint32_t candidate = static_cast<uint32_t>(field_offset);
        if (field_offset == f_inode_offset) {
            continue;
        }
        uint64_t file0_value = 0;
        uint64_t file1_value = 0;
        uint64_t socket_value = 0;
        memcpy(&file0_value, file0_snapshot.data() + probe, sizeof(file0_value));
        memcpy(&file1_value, file1_snapshot.data() + probe, sizeof(file1_value));
        memcpy(&socket_value, socket_snapshot.data() + probe, sizeof(socket_value));
        if (file0_value == 0 || file1_value == 0 || socket_value == 0 ||
            file0_value != file1_value || file0_value < kKernelAddrMin ||
            file0_value == socket_value) {
            continue;
        }
        candidates.push_back(candidate);
    }

    if (candidates.empty()) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    offset = candidates[0];
    if (offset == 0) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    return put_offset_cache("file_f_op", offset);
}

// Locates unsigned int f_flags of struct file.  /dev/null is opened read-only
// and its file flags are read, extended with O_APPEND|O_NONBLOCK, then restored;
// the 4-byte slot that changes by exactly that mask and returns to its original
// value is f_flags.
KModErr get_file_f_flags_offset(uint32_t &offset) {
    bool available = false;
    KModErr btf_error = resolve_file_btf_offset("f_flags", 4, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("file_f_flags", offset);
    if (is_ok(err)) {
        return KModErr::OK; // already cached
    }

    int fd = open("/dev/null", O_RDONLY);
    if (fd < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }
    fd_guard fd_guard_(fd);

    uint64_t file_ptr = 0;
    err = get_current_task_file_ptr(fd, file_ptr);
    if (is_failed(err)) {
        return err;
    }

    std::vector<uint32_t> before(64);
    std::vector<uint32_t> modified(64);
    std::vector<uint32_t> restored(64);
    RETURN_IF_ERROR(read_kernel_mem(file_ptr, before.data(), 256));

    const int flags = fcntl(fd, F_GETFL);
    if (flags == -1) {
        return KModErr::ERR_MODULE_FCNTL;
    }
    fcntl(fd, F_SETFL, flags | O_APPEND | O_NONBLOCK);
    RETURN_IF_ERROR(read_kernel_mem(file_ptr, modified.data(), 256));
    fcntl(fd, F_SETFL, flags);
    RETURN_IF_ERROR(read_kernel_mem(file_ptr, restored.data(), 256));

    uint32_t match_count = 0;
    uint32_t found_offset = 0;
    for (size_t index = 0; index < before.size(); ++index) {
        const uint32_t before_value = before[index];
        const uint32_t modified_value = modified[index];
        const uint32_t restored_value = restored[index];
        if (before_value != restored_value || before_value == modified_value) {
            continue;
        }
        const uint32_t changed = modified_value ^ before_value;
        if (changed == (O_APPEND | O_NONBLOCK)) {
            ++match_count;
            found_offset = static_cast<uint32_t>(index * 4);
        }
    }
    if (match_count != 1) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    offset = found_offset;
    return put_offset_cache("file_f_flags", found_offset);
}

// Locates fmode_t f_mode of struct file.  /dev/null is opened read-only, write
// only and read-write; their aligned 4-byte slots are compared and the slot whose
// low two bits are 1, 2 and 3 and where the read-write value is the OR of the
// other two is f_mode.
KModErr get_file_f_mode_offset(uint32_t &offset) {
    bool available = false;
    KModErr btf_error = resolve_file_btf_offset("f_mode", 4, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("file_f_mode", offset);
    if (is_ok(err)) {
        return KModErr::OK; // already cached
    }

    int read_fd = open("/dev/null", O_RDONLY);
    int write_fd = open("/dev/null", O_WRONLY);
    int rdwr_fd = open("/dev/null", O_RDWR);
    fd_guard read_guard(read_fd);
    fd_guard write_guard(write_fd);
    fd_guard rdwr_guard(rdwr_fd);
    if ((read_fd | write_fd | rdwr_fd) < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    uint64_t read_file = 0;
    uint64_t write_file = 0;
    uint64_t rdwr_file = 0;
    err = get_current_task_file_ptr(read_fd, read_file);
    if (is_failed(err)) {
        return err;
    }
    RETURN_IF_ERROR(get_current_task_file_ptr(write_fd, write_file));
    RETURN_IF_ERROR(get_current_task_file_ptr(rdwr_fd, rdwr_file));

    uint32_t match_count = 0;
    uint32_t found_offset = 0;
    for (uint64_t probe = 0; probe <= 255; probe += 4) {
        uint32_t read_mode = 0;
        uint32_t write_mode = 0;
        uint32_t rdwr_mode = 0;
        RETURN_IF_ERROR(read_kernel_mem(read_file + probe, &read_mode, sizeof(read_mode)));
        RETURN_IF_ERROR(read_kernel_mem(write_file + probe, &write_mode, sizeof(write_mode)));
        RETURN_IF_ERROR(read_kernel_mem(rdwr_file + probe, &rdwr_mode, sizeof(rdwr_mode)));
        if ((read_mode & 3) == 1 && (write_mode & 3) == 2 && (rdwr_mode & 3) == 3 &&
            (read_mode | write_mode) == rdwr_mode) {
            ++match_count;
            found_offset = static_cast<uint32_t>(probe);
        }
    }
    if (match_count != 1) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    offset = found_offset;
    return put_offset_cache("file_f_mode", found_offset);
}

// Locates loff_t f_pos of struct file.  The probe file is opened read-write, the
// descriptor's position is moved to a marker value and the aligned 8-byte slots
// of its struct file are scanned for it; the matching slot must follow the
// second position as well, which proves it is f_pos.
KModErr get_file_f_pos_offset(uint32_t &offset) {
    bool available = false;
    KModErr btf_error = resolve_file_btf_offset("f_pos", 8, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("file_f_pos", offset);
    if (is_ok(err)) {
        return KModErr::OK; // already cached
    }

    err = init_f_op_offset_test_file();
    if (is_failed(err)) {
        return err;
    }

    std::string path = get_temp_file_path(0);
    int fd = open(path.c_str(), O_RDWR);
    if (fd < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }
    fd_guard fd_guard_(fd);

    uint64_t file_ptr = 0;
    RETURN_IF_ERROR(get_current_task_file_ptr(fd, file_ptr));

    if (lseek64(fd, 439041101, SEEK_SET) == -1) {
        return KModErr::ERR_MODULE_WRITE_FILE;
    }
    for (uint64_t probe = 0; probe < 512; probe += 8) {
        uint64_t slot = 0;
        RETURN_IF_ERROR(read_kernel_mem(file_ptr + probe, &slot, sizeof(slot)));
        if (slot != 439041101) {
            continue;
        }
        if (lseek64(fd, 1295788826, SEEK_SET) == -1) {
            return KModErr::ERR_MODULE_WRITE_FILE;
        }
        uint64_t second_slot = 0;
        RETURN_IF_ERROR(read_kernel_mem(file_ptr + probe, &second_slot, sizeof(second_slot)));
        if (second_slot == 1295788826) {
            offset = static_cast<uint32_t>(probe);
            return put_offset_cache("file_f_pos", offset);
        }
        lseek64(fd, 439041101, SEEK_SET);
    }
    return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
}

// Locates struct cred *f_cred of struct file.  The probe file is opened
// read-only and the cred pointer of the calling task is taken from task_struct;
// the aligned 8-byte slots of the file are scanned for that pointer.
KModErr get_file_f_cred_offset(uint32_t &offset) {
    bool available = false;
    KModErr btf_error = resolve_file_btf_offset("f_cred", 8, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("file_f_cred", offset);
    if (is_ok(err)) {
        return KModErr::OK; // already cached
    }

    err = init_f_op_offset_test_file();
    if (is_failed(err)) {
        return err;
    }

    std::string path = get_temp_file_path(0);
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }
    fd_guard fd_guard_(fd);

    uint64_t file_ptr = 0;
    RETURN_IF_ERROR(get_current_task_file_ptr(fd, file_ptr));

    uint64_t current_task = 0;
    RETURN_IF_ERROR(get_kernel_shellcode_u64_result(
        [](asmjit::a64::Assembler *a, asmjit::a64::GpX &result) -> KModErr {
            result = asmjit::a64::regs::x10;
            RETURN_IF_ERROR(export_symbol::get_current_checked(a, asmjit::a64::regs::x10));
            return KModErr::OK;
        },
        current_task));

    uint32_t task_cred_offset = 0;
    RETURN_IF_ERROR(get_task_struct_cred_offset(task_cred_offset));

    uint64_t cred_ptr = 0;
    RETURN_IF_ERROR(read_kernel_mem(current_task + task_cred_offset, &cred_ptr, sizeof(cred_ptr)));

    for (uint64_t probe = 0; probe < 512; probe += 8) {
        uint64_t slot = 0;
        RETURN_IF_ERROR(read_kernel_mem(file_ptr + probe, &slot, sizeof(slot)));
        if (slot == cred_ptr) {
            offset = static_cast<uint32_t>(probe);
            break;
        }
    }
    if (offset == 0) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    return put_offset_cache("file_f_cred", offset);
}

// Locates void* private_data of struct file.  /dev/null keeps its private_data
// empty while an eventfd keeps a kernel pointer there; the slot that is zero for
// the first and a kernel pointer for the second, between f_cred and the end of
// the already known fields, is private_data.
KModErr get_file_private_data_offset(uint32_t &offset) {
    bool available = false;
    KModErr btf_error = resolve_file_btf_offset("private_data", 8, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("file_private_data", offset);
    if (is_ok(err)) {
        return KModErr::OK; // already cached
    }

    int null_fd = open("/dev/null", O_RDONLY);
    int event_fd = eventfd(0, 0);
    fd_guard null_guard(null_fd);
    fd_guard event_guard(event_fd);
    if (null_fd < 0 || event_fd < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    uint64_t null_file = 0;
    uint64_t event_file = 0;
    err = get_current_task_file_ptr(null_fd, null_file);
    if (is_failed(err)) {
        return err;
    }
    RETURN_IF_ERROR(get_current_task_file_ptr(event_fd, event_file));

    uint32_t cred_offset = 0;
    uint32_t inode_offset = 0;
    uint32_t op_offset = 0;
    RETURN_IF_ERROR(get_file_f_cred_offset(cred_offset));
    RETURN_IF_ERROR(get_file_f_inode_offset(inode_offset));
    RETURN_IF_ERROR(get_file_f_op_offset(op_offset));

    const int32_t scan_end = static_cast<int32_t>(cred_offset) + 136;
    std::vector<uint32_t> candidates;
    int32_t field_offset = static_cast<int32_t>(cred_offset);
    while ((field_offset += 8) < scan_end) {
        if (field_offset == static_cast<int32_t>(inode_offset) ||
            field_offset == static_cast<int32_t>(op_offset)) {
            continue;
        }
        uint64_t null_value = 0;
        uint64_t event_value = 0;
        RETURN_IF_ERROR(read_kernel_mem(null_file + field_offset, &null_value, sizeof(null_value)));
        RETURN_IF_ERROR(
            read_kernel_mem(event_file + field_offset, &event_value, sizeof(event_value)));
        if (null_value != 0 || event_value < kKernelAddrMin) {
            continue;
        }
        candidates.push_back(static_cast<uint32_t>(field_offset));
    }
    if (candidates.empty()) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    offset = candidates[0];
    return put_offset_cache("file_private_data", offset);
}

// Locates struct address_space *f_mapping of struct file.  Two regular probe
// files are opened; the aligned 8-byte slots of their struct files are compared
// and each pair of kernel pointers is followed to its respective
// address_space.  f_mapping is the slot whose address_space host is each file's
// own inode.
KModErr get_file_f_mapping_offset(uint32_t &offset) {
    bool available = false;
    KModErr btf_error = resolve_file_btf_offset("f_mapping", 8, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("file_f_mapping", offset);
    if (is_ok(err)) {
        return KModErr::OK; // already cached
    }

    err = init_f_op_offset_test_file();
    if (is_failed(err)) {
        return err;
    }

    std::string file0_path = get_temp_file_path(0);
    std::string file1_path = get_temp_file_path(1);
    int file0_fd = open(file0_path.c_str(), O_RDWR);
    int file1_fd = open(file1_path.c_str(), O_RDWR);
    fd_guard file0_guard(file0_fd);
    fd_guard file1_guard(file1_fd);
    if ((file0_fd | file1_fd) < 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    uint64_t file0_ptr = 0;
    uint64_t file1_ptr = 0;
    RETURN_IF_ERROR(get_current_task_file_ptr(file0_fd, file0_ptr));
    RETURN_IF_ERROR(get_current_task_file_ptr(file1_fd, file1_ptr));

    uint32_t f_inode_offset = 0;
    RETURN_IF_ERROR(get_file_f_inode_offset(f_inode_offset));

    uint64_t file0_inode = 0;
    uint64_t file1_inode = 0;
    RETURN_IF_ERROR(read_kernel_mem(file0_ptr + f_inode_offset, &file0_inode, sizeof(file0_inode)));
    RETURN_IF_ERROR(read_kernel_mem(file1_ptr + f_inode_offset, &file1_inode, sizeof(file1_inode)));

    uint32_t host_offset = 0;
    RETURN_IF_ERROR(get_address_space_host_offset(host_offset));

    uint32_t match_count = 0;
    uint32_t found_offset = 0;
    for (uint64_t probe = 0; probe <= 511; probe += 8) {
        if (probe == f_inode_offset) {
            continue;
        }
        uint64_t file0_value = 0;
        uint64_t file1_value = 0;
        RETURN_IF_ERROR(read_kernel_mem(file0_ptr + probe, &file0_value, sizeof(file0_value)));
        RETURN_IF_ERROR(read_kernel_mem(file1_ptr + probe, &file1_value, sizeof(file1_value)));
        if (file0_value < kKernelAddrMin || file1_value < kKernelAddrMin) {
            continue;
        }
        uint64_t file0_host = 0;
        uint64_t file1_host = 0;
        bool file0_ok = false;
        bool file1_ok = false;
        RETURN_IF_ERROR(safe_read_kaddr_t(file0_value + host_offset, file0_ok, file0_host));
        if (!file0_ok) {
            continue;
        }
        RETURN_IF_ERROR(safe_read_kaddr_t(file1_value + host_offset, file1_ok, file1_host));
        if (!file1_ok) {
            continue;
        }
        if (file0_host != file0_inode) {
            continue;
        }
        if (file1_host == file1_inode) {
            found_offset = static_cast<uint32_t>(probe);
            ++match_count;
        }
    }
    if (match_count != 1) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    offset = found_offset;
    return put_offset_cache("file_f_mapping", found_offset);
}

} // namespace kernel_module
