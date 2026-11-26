// MapleSU unsafe su install implementation.
//
// Publishes separate system and kernel directory copies of su.
#include "kernel_module_kit_umbrella.h"

#include "maplesu_payload_assets.h"
#include "private_maple_runtime.h"
#include "maplesu_storage_private.h"
#include "../maplesu/install/executable_publish.h"

#include <algorithm>
#include <cstddef>
#include <cstdio>
#include <cstring>
#include <memory>
#include <new>
#include <string>
#include <string_view>

extern "C" KModErr maplesu_prepare_su_exec_directory(const char*);

namespace maplesu::sdk {

// Installer error codes (kit error space).
constexpr KModErr kOk = KModErr::OK;
constexpr KModErr kErrPayloadAlloc = static_cast<KModErr>(-19905);
constexpr KModErr kErrTokenMissing = static_cast<KModErr>(-19908);

// Equal-length feature token embedded in the payload; at install time every
// occurrence is overwritten with the current root key.
const char static_inline_su_rootkey[4096] =
    "ae40aa59c886d2c8762e849e9fa798155cd438a5faa748706084c6da3ca9add7"
    "2a02c0128f6d56ba4a077a7bbd6ca8b6fa93b8c32e1ba5c2203e6e4c3cc24064";

// Replaces every in-place occurrence of the 4096-byte feature token with the
// NUL-padded root key.  The replacement never changes the payload length.
static bool replace_feature_string_in_buf(const char* feature_string_buf,
                                   unsigned long feature_string_buf_size,
                                   std::string_view new_key, char* buf,
                                   unsigned long buf_size) {
    if (feature_string_buf_size == 0 || buf_size < feature_string_buf_size) return false;
    bool write = false;
    std::shared_ptr<char> sp_new_feature(
        new (std::nothrow) char[feature_string_buf_size], std::default_delete<char[]>());
    if (sp_new_feature) {
        memset(sp_new_feature.get(), 0, feature_string_buf_size);
        std::size_t copy_len = new_key.length() + 1;
        copy_len = std::min(static_cast<std::size_t>(feature_string_buf_size - 1), copy_len);
        strncpy(sp_new_feature.get(), new_key.data(), copy_len);

        for (std::size_t i = 0; i <= buf_size - feature_string_buf_size; i++) {
            char* desc = buf;
            desc += i;
            if (memcmp(desc, feature_string_buf, feature_string_buf_size) == 0) {
                memcpy(desc, sp_new_feature.get(), copy_len);
                write = true;
            }
        }
    }
    return write;
}

// Writes the su program to `target_path`, replacing its feature token with the
// root key.  Returns the publisher's status.
static KModErr write_su_exec(const char* target_path) {
    std::shared_ptr<char> sp_su_data(new (std::nothrow) char[sizeof(su_data)],
                                     std::default_delete<char[]>());
    if (!sp_su_data) return kErrPayloadAlloc;
    memcpy(sp_su_data.get(), su_data, sizeof(su_data));

    if (!replace_feature_string_in_buf(
            static_inline_su_rootkey, sizeof(static_inline_su_rootkey),
            kernel_module::maplesu_request_owner_mode() ? "" :
                __get_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW(),
            sp_su_data.get(), sizeof(su_data))) {
        return kErrTokenMissing;
    }
    return maplesu_publish_executable(target_path, sp_su_data.get(), sizeof(su_data),
                                      "u:object_r:system_file:s0");
}

}  // namespace maplesu::sdk

namespace maplesu::sdk {

// Installs the su program at the runtime data directory and labels it.
KModErr unsafe_install_su() {
    const KModErr set_root_err = default_modules::module_root::set_current_root(true);
    if (set_root_err != kOk) return set_root_err;

    std::string system_directory, kernel_directory;
    RETURN_IF_ERROR(maplesu::storage::current_system_directory(system_directory));
    RETURN_IF_ERROR(maplesu::storage::current_kernel_directory(kernel_directory));
    const std::string su_path = system_directory + "su";
    const std::string su_path2 = kernel_directory + "su";

    KModErr err = write_su_exec(su_path.c_str());
    if (err != kOk) return err;
    err = write_su_exec(su_path2.c_str());
    if (err != kOk) return err;

    return maplesu_prepare_su_exec_directory(system_directory.c_str());
}

}  // namespace maplesu::sdk

#include "../maplesu/compat/install_su_exports_legacy_abi.h"
