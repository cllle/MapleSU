// renamedata implementation.
//
#include <stdio.h>
#include <sys/prctl.h>

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <random>
#include <sstream>
#include <string>
#include <system_error>
#include <vector>

#include "kernel_module_kit_umbrella.h"
#include "maplesu_kernel_layout_private.h"

#include "offset_probe_private.h"
#include "offsets_internal.h"
#include "private_file_utils.h"

namespace file_utils {

// Reports whether the directory exists, creating it (and every missing parent)
// when it does not; the result is the final existence check.
static bool create_directory_if_not_exists(const char* dir_path) {
    if (!file_exists(dir_path)) {
        std::filesystem::path path(dir_path);
        std::error_code error;
        std::filesystem::create_directories(path, error);
    }
    return file_exists(dir_path);
}

}  // namespace file_utils

namespace random_utils {

// Builds a payload of `count` distinct non-zero bytes in random order: the byte
// values 1..255 are shuffled and the first `count` of them are returned. A
// request for zero bytes, or for more bytes than the value space holds, yields
// an empty payload.
inline std::vector<uint8_t> generate_unique_non_zero_bytes(std::size_t count) {
    if (count == 0 || count > 255) {
        return std::vector<uint8_t>();
    }
    std::vector<uint8_t> bytes(0xFF);
    uint8_t value = 1;
    for (auto& byte : bytes) {
        byte = value++;
    }
    std::shuffle(bytes.begin(), bytes.end(), detail::mt19937_32());
    return std::vector<uint8_t>(bytes.begin(), bytes.begin() + static_cast<std::ptrdiff_t>(count));
}

}  // namespace random_utils

namespace kernel_module {

namespace {

// Name of the module's private data directory on /data: the first part of the
// module root key followed by a copy of its first character.
static inline std::string get_system_file_dir_name() {
    std::string head =
            std::string(__get_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW()).substr(
                    0, FOLDER_HEAD_ROOT_KEY_LEN);
    return head + head[0];
}

// Absolute path of the module's private data directory, "/data/<name>/".
static inline std::string get_system_file_dir_path() {
    char path_buf[1024] = {0};
    snprintf(path_buf, sizeof(path_buf), "/data/%s/", get_system_file_dir_name().c_str());
    return std::string(path_buf);
}

}  // namespace

// Name of the probe directory created under the module's private data directory
// for probe index `index`: "temp<index>" followed by a fixed marker so the name
// cannot collide with another module's probe data.
static std::string get_temp_dir_path(int index) {
    std::stringstream name_stream;
    name_stream << "temp" << index << "PIOraPxPqSPOOLOhGVUmf60mQzpRA3KN";
    return (std::filesystem::path(get_system_file_dir_path()) / name_stream.str()).string();
}

// Owns the handle of one installed kernel hook and uninstalls it when the guard
// leaves its scope, so an early return can never leave the hook installed.
class ScopedHookHandleGuard {
public:
    explicit ScopedHookHandleGuard(HookHandle handle) : handle_(handle) {}
    ScopedHookHandleGuard(const ScopedHookHandleGuard&) = delete;
    ScopedHookHandleGuard& operator=(const ScopedHookHandleGuard&) = delete;

    ~ScopedHookHandleGuard() {
        if (handle_ != nullptr) {
            uninstall_kernel_hook(handle_);
            handle_ = nullptr;
        }
    }

private:
    HookHandle handle_;
};

// Recovers the offsets of the `inode *old_dir` and `inode *new_dir` fields of
// the kernel's `struct renamedata` by driving one directory rename through a
// temporary before-hook installed on vfs_rename():
//
//  1. two probe directories are created under the module data directory, an
//     empty file is placed inside the first one and the second probe path is
//     removed, so a later rename() of the file to that path reaches
//     vfs_rename() with both parents being probe directories;
//  2. both probe directories are resolved to kernel dentries and the inode
//     pointers behind their d_inode slot are read out of kernel memory;
//  3. the process name is replaced by a random marker so the hook only reacts
//     to this probe process;
//  4. the hook payload compares current->comm against the marker, then looks
//     for the two inode pointers inside the first 64 bytes of the renamedata
//     argument and stores (match address - argument address) for each;
//  5. rename() is issued, the process name restored and the two offsets read
//     back out of the kernel slot, then cached.
//
// Only Linux >= 5.12.0 has struct renamedata with both fields.
static KModErr get_renamedata_old_dir_and_new_dir_offset(uint32_t& old_dir_offset,
                                                         uint32_t& new_dir_offset) {
    KernelFieldDescription old_dir, new_dir;
    bool available = false;
    KModErr btf_error = maplesu_resolve_btf_field("renamedata", "old_dir", 8, old_dir, available);
    if (available) {
        if (is_failed(btf_error)) return btf_error;
        bool new_available = false;
        btf_error = maplesu_resolve_btf_field("renamedata", "new_dir", 8, new_dir, new_available);
        if (is_failed(btf_error)) return btf_error;
        if (!new_available || old_dir.owner_size_bytes != new_dir.owner_size_bytes)
            return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
        old_dir_offset = old_dir.offset_bytes;
        new_dir_offset = new_dir.offset_bytes;
        return KModErr::OK;
    }

    if (is_kernel_version_less("5.12.0")) {
        return KModErr::ERR_MODULE_OFFSET_NOT_MATCH_LINUX_VER;
    }

    uint32_t cached_old_dir = 0, cached_new_dir = 0;
    if (is_ok(get_offset_cache("renamedata_old_dir", cached_old_dir)) &&
        is_ok(get_offset_cache("renamedata_new_dir", cached_new_dir))) {
        old_dir_offset = cached_old_dir;
        new_dir_offset = cached_new_dir;
        return KModErr::OK;
    }

    std::string temp_dir0 = get_temp_dir_path(0);
    std::string temp_dir1 = get_temp_dir_path(1);
    std::string old_file_path =
            (std::filesystem::path(temp_dir0) / std::filesystem::path("bMkQNdFIxeQJw9WWyuUwKimLPFn5sUGZ")).string();
    std::string new_file_path =
            (std::filesystem::path(temp_dir1) / std::filesystem::path("gH5fBS5mI9KmrqeFBtz5I4Fb2Npqe4sA")).string();

    if (!file_utils::create_directory_if_not_exists(temp_dir0.c_str())) {
        return KModErr::ERR_MODULE_CREATE_DIR;
    }
    if (!file_utils::create_directory_if_not_exists(temp_dir1.c_str())) {
        return KModErr::ERR_MODULE_CREATE_DIR;
    }
    {
        // The probe file itself only has to exist: the hook watches the rename
        // call that moves it, not its contents.
        std::ofstream probe_file(old_file_path.c_str(),
                                 std::ios::out | std::ios::trunc | std::ios::binary);
        if (!probe_file.is_open()) {
            return KModErr::ERR_MODULE_CREATE_EMPTY_FILE;
        }
        probe_file.close();
    }
    {
        std::filesystem::path new_target_path(new_file_path.c_str());
        std::error_code error;
        std::filesystem::remove_all(new_target_path, error);
        if (file_utils::file_exists(new_target_path)) {
            return KModErr::ERR_MODULE_DEL_FILE;
        }
    }

    scoped_kpath temp_dir0_kpath{};
    scoped_kpath temp_dir1_kpath{};
    KModErr err = acquire_kpath(temp_dir0, temp_dir0_kpath);
    if (is_failed(err)) {
        return err;
    }
    err = acquire_kpath(temp_dir1, temp_dir1_kpath);
    if (is_failed(err)) {
        return err;
    }

    uint32_t d_inode_offset = 0;
    err = get_dentry_d_inode_offset(d_inode_offset);
    if (is_failed(err)) {
        return err;
    }

    uint64_t old_dir_inode = 0;
    uint64_t new_dir_inode = 0;
    err = read_kernel_mem(temp_dir0_kpath.kp.dentry + d_inode_offset, &old_dir_inode,
                          sizeof(old_dir_inode));
    if (is_failed(err)) {
        return err;
    }
    err = read_kernel_mem(temp_dir1_kpath.kp.dentry + d_inode_offset, &new_dir_inode,
                          sizeof(new_dir_inode));
    if (is_failed(err)) {
        return err;
    }

    // Resolve the emitter before changing comm or allocating hook result slots.
    aarch64_asm_ctx asm_ctx = init_aarch64_asm();
    asmjit::a64::Assembler* a = asm_ctx.assembler();
    asmjit::Label skip_label = a->newLabel();
    arm64_before_hook_start(a);
    RETURN_IF_ERROR(export_symbol::get_current_checked(a, asmjit::a64::x10));

    char original_name[16] = {0};
    if (prctl(PR_GET_NAME, original_name, 0, 0, 0) != 0) {
        return KModErr::ERR_MODULE_PR_GET_NAME;
    }
    std::vector<uint8_t> name_pattern = random_utils::generate_unique_non_zero_bytes(15);
    name_pattern.push_back(0);
    if (prctl(PR_SET_NAME, name_pattern.data(), 0, 0, 0) != 0) {
        return KModErr::ERR_MODULE_PR_SET_NAME;
    }
    char probe_name[16] = {0};
    if (prctl(PR_GET_NAME, probe_name, 0, 0, 0) != 0 ||
        strncmp(reinterpret_cast<const char*>(name_pattern.data()), probe_name,
                sizeof(probe_name)) != 0) {
        return KModErr::ERR_MODULE_PR_SET_NAME;
    }

    uint64_t rename_func_kaddr = 0;
    err = kallsyms_lookup_name("vfs_rename", rename_func_kaddr);
    if (is_failed(err)) {
        return err;
    }

    std::vector<uint8_t> hook_payload;
    uint64_t old_dir_slot_kaddr = 0;
    uint64_t new_dir_slot_kaddr = 0;
    {
        std::string pattern_name(probe_name);
        uint64_t result_kaddr = 0;
        err = alloc_kernel_mem(sizeof(uint64_t) * 2, result_kaddr);
        if (is_ok(err)) {
            old_dir_slot_kaddr = result_kaddr;
            new_dir_slot_kaddr = result_kaddr + sizeof(uint64_t);
            uint32_t comm_offset = 0;
            err = get_task_struct_comm_offset(comm_offset);
            if (is_ok(err)) {
                uint64_t name_first = 0;
                uint64_t name_second = 0;
                std::memcpy(&name_first, pattern_name.data(), sizeof(name_first));
                std::memcpy(&name_second, pattern_name.data() + 8, sizeof(name_second));

                // current->comm must carry the probe marker, otherwise the hook
                // lets the original vfs_rename() run untouched.
                aarch64_asm_mov_x(a, asmjit::a64::x11, comm_offset);
                a->add(asmjit::a64::x10, asmjit::a64::x10, asmjit::a64::x11);
                a->ldr(asmjit::a64::x11, ptr(asmjit::a64::x10));
                aarch64_asm_mov_x(a, asmjit::a64::x12, name_first);
                a->cmp(asmjit::a64::x11, asmjit::a64::x12);
                a->b(asmjit::a64::CondCode::kNE, skip_label);
                a->ldr(asmjit::a64::x11, ptr(asmjit::a64::x10, 8));
                aarch64_asm_mov_x(a, asmjit::a64::x12, name_second);
                a->cmp(asmjit::a64::x11, asmjit::a64::x12);
                a->b(asmjit::a64::CondCode::kNE, skip_label);

                // x15 keeps the renamedata argument while the search helper
                // rewrites x0; each search uses one 8-byte stack scratch slot.
                a->mov(asmjit::a64::x15, asmjit::a64::x0);
                {
                    StackBufGuard scratch(a, 8, asmjit::a64::x10);
                    aarch64_asm_mov_x(a, asmjit::a64::x11, old_dir_inode);
                    a->str(asmjit::a64::x11, ptr(asmjit::a64::x10));
                    string_ops::kmemmem(a, asmjit::a64::x0, 64, asmjit::a64::x10, 8);
                    a->sub(asmjit::a64::x0, asmjit::a64::x0, asmjit::a64::x15);
                    aarch64_asm_mov_x(a, asmjit::a64::x11, old_dir_slot_kaddr);
                    a->str(asmjit::a64::x0, ptr(asmjit::a64::x11));
                    a->mov(asmjit::a64::x0, asmjit::a64::x15);
                }
                a->mov(asmjit::a64::x15, asmjit::a64::x0);
                {
                    StackBufGuard scratch(a, 8, asmjit::a64::x10);
                    aarch64_asm_mov_x(a, asmjit::a64::x11, new_dir_inode);
                    a->str(asmjit::a64::x11, ptr(asmjit::a64::x10));
                    string_ops::kmemmem(a, asmjit::a64::x0, 64, asmjit::a64::x10, 8);
                    a->sub(asmjit::a64::x0, asmjit::a64::x0, asmjit::a64::x15);
                    aarch64_asm_mov_x(a, asmjit::a64::x11, new_dir_slot_kaddr);
                    a->str(asmjit::a64::x0, ptr(asmjit::a64::x11));
                    a->mov(asmjit::a64::x0, asmjit::a64::x15);
                }
                a->bind(skip_label);
                arm64_before_hook_end(a, true);

                hook_payload = aarch64_asm_to_bytes(a);
                if (hook_payload.empty()) {
                    err = KModErr::ERR_MODULE_ASM;
                }
            }
        }
    }
    if (is_failed(err)) {
        return err;
    }

    HookHandle hook_handle;
    err = install_kernel_function_before_hook(rename_func_kaddr, hook_payload, &hook_handle);
    if (is_failed(err)) {
        return err;
    }
    {
        ScopedHookHandleGuard hook_guard(hook_handle);

        rename(old_file_path.c_str(), new_file_path.c_str());

        if (prctl(PR_SET_NAME, original_name, 0, 0, 0) != 0) {
            return KModErr::ERR_MODULE_PR_SET_NAME;
        }

        uint64_t value = 0;
        err = read_kernel_mem(old_dir_slot_kaddr, &value, sizeof(value));
        if (is_failed(err)) {
            return err;
        }
        old_dir_offset = static_cast<uint32_t>(value);
        value = 0;
        err = read_kernel_mem(new_dir_slot_kaddr, &value, sizeof(value));
        if (is_failed(err)) {
            return err;
        }
        new_dir_offset = static_cast<uint32_t>(value);

        if (old_dir_offset != 0) {
            err = put_offset_cache("renamedata_old_dir", old_dir_offset);
            if (is_failed(err)) {
                return err;
            }
        }
        if (new_dir_offset != 0) {
            err = put_offset_cache("renamedata_new_dir", new_dir_offset);
            if (is_failed(err)) {
                return err;
            }
        }
        if (old_dir_offset == 0 || new_dir_offset == 0) {
            return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
        }
    }
    return KModErr::OK;
}

KModErr get_renamedata_old_dir_offset(uint32_t& old_dir_offset) {
    uint32_t resolved_old_dir = 0;
    uint32_t new_dir_offset = 0;
    KModErr error = get_renamedata_old_dir_and_new_dir_offset(resolved_old_dir, new_dir_offset);
    if (is_ok(error)) old_dir_offset = resolved_old_dir;
    return error;
}

KModErr get_renamedata_new_dir_offset(uint32_t& new_dir_offset) {
    uint32_t old_dir_offset = 0;
    uint32_t resolved_new_dir = 0;
    KModErr error = get_renamedata_old_dir_and_new_dir_offset(old_dir_offset, resolved_new_dir);
    if (is_ok(error)) new_dir_offset = resolved_new_dir;
    return error;
}

}  // namespace kernel_module
