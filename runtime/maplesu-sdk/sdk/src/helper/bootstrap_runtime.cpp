#include "private/bootstrap_runtime.h"
#include "helper_sdk_version.h"
#include "allocation_history_private.h"
#include "../maplesu/module/runtime.h"
#include "module_base_disk_storage.h"
#include "maplesu_storage_worker_private.h"
#include "../maplesu/install/installation_state.h"

#include <fstream>
#include <iterator>
#include <string>
#include <vector>
#include <unistd.h>

extern "C" void __set_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW(const char*);
extern "C" void __set_current_module_id32_1b0494ae8a788541db46b82cc1f0577c(const char*);
extern "C" int maplesu_environment_identity_start();
extern "C" KModErr maplesu_prepare_su_exec_directory(const char*);

namespace default_modules {
class module_bypass_mtk_mkp { public: static KModErr init(); };
class module_bypass_oplus_secure_guard { public: static KModErr init(); };
class module_bypass_oplus_inte { public: static KModErr init(); };
namespace module_root {
KModErr set_current_root(bool);
KModErr init_anti_detect_root_env(const std::vector<std::string>&, bool);
}
namespace module_su_redirect { KModErr init(const char*, bool); }
namespace module_proc_bootconfig { KModErr init(); }
namespace module_proc_cmdline { KModErr init(); }
namespace module_selinux { KModErr init(maplesu::sdk::InstallMode, maplesu::sdk::ExploitMethod); }
namespace module_tombstone_purge { KModErr init(bool, bool); }

KModErr reset_boot_allocation_state(const helper_context& context) {
    if (!helper_boot_id_valid(context.boot_id, helper_strlen(context.boot_id)))
        return KModErr::ERR_MODULE_BOOT_SESSION;
    constexpr const char* key = "maplesu_boot_allocation_session_v1";
    std::string previous;
    const auto read = kernel_module::read_string_disk_storage(key, previous);
    if (read != KModErr::OK && read != KModErr::ERR_MODULE_STORAGE_NOT_FOUND) return read;
    if (read == KModErr::OK && previous == context.boot_id) return KModErr::OK;
    // The helper holds its boot lock. Same-boot retries must retain allocation
    // cursors: resetting them can overwrite code/data still resident in kernel.
    RETURN_IF_ERROR(maplesu::allocation_history::reset_idle_memory());
    RETURN_IF_ERROR(maplesu::allocation_history::reset_hook_flags());
    return kernel_module::write_string_disk_storage(key, context.boot_id);
}
}

namespace maplesu::bootstrap {
namespace {
KModErr read_config(const helper_context& context, const char* name, std::string& text) {
    const std::string path = std::string(context.env_dir) + "/" + name;
    if (access(path.c_str(), F_OK) != 0) return KModErr::ERR_MODULE_FILE_NOT_EXIST;
    std::ifstream input(path, std::ios::binary);
    if (!input) return KModErr::ERR_MODULE_READ_FILE;
    text.assign(std::istreambuf_iterator<char>(input), {});
    return input.bad() ? KModErr::ERR_MODULE_READ_FILE : KModErr::OK;
}
KModErr stage(helper_context& context, const char* name, KModErr result) {
    install::state::ErrnoGuard restore;
    helper_log_error(&context, name, static_cast<int64_t>(result));
    return result;
}
void bind(const char* key) {
    if (!kernel_module::maplesu_request_owner_mode())
        __set_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW(key);
    // SDK built-ins share the original m039 storage identity; helper metadata keeps its own ID.
    __set_current_module_id32_1b0494ae8a788541db46b82cc1f0577c("GEfQdKmhZSkuWJI2rs21JO39Xmp8XcZg");
}
}

module::SdkVersion sdk_version() {
    const auto version = MAPLESU_HELPER_SDK_VERSION;
    return {version.major, version.minor, version.patch};
}

KModErr acquire_root(const char* key) {
    bind(key);
    return default_modules::module_root::set_current_root(true);
}

KModErr initialize_core(helper_context& context) {
    using namespace default_modules;
    bind(context.root_key);
    const bool helper = kernel_module::maplesu_request_auth_helper(
        kernel_module::maplesu_request_auth_state());
    RETURN_IF_ERROR(stage(context, "config-root rc=", module_root::set_current_root(true)));
    std::string mode_text;
    RETURN_IF_ERROR(stage(context, "install.mode rc=", read_config(context, "install.mode", mode_text)));
    RETURN_IF_ERROR(stage(context, "allocation-reset rc=", reset_boot_allocation_state(context)));
    if (helper) {
        std::string native_directory;
        RETURN_IF_ERROR(stage(context, "native-su-storage rc=", storage::current_system_directory(native_directory)));
        RETURN_IF_ERROR(stage(context, "native-su-executable rc=", maplesu_prepare_su_exec_directory(native_directory.c_str())));
    }
    install::state::VendorAttempt vendor;
    const auto observation = [&](const char* name, install::state::Result value) {
        install::state::ErrnoGuard restore;
        if (!value.code) return;
        helper_log_error(&context, name, value.code);
        helper_log_error(&context, "vendor-state phase=", static_cast<uint32_t>(value.phase));
        helper_log_error(&context, "vendor-state applied=", value.applied ? 1 : 0);
    };
    const auto begin = vendor.begin(context.boot_id);
    if (begin.code != ENODATA) observation("vendor-state-begin error=", begin);
    if (helper) {
        if (begin.code) {
            // BEGIN may have committed before native startup failed. Keep that
            // same CORE receipt and raw errno; never replay or select Legacy.
            if (begin.code > 0 && begin.code <= 4095) errno = static_cast<int>(begin.code);
            return begin.code < -4095 ? static_cast<KModErr>(begin.code) : KModErr::ERR_MODULE_TEST_CHANNEL;
        }
        std::string native_directory;
        RETURN_IF_ERROR(stage(context, "native-su-storage rc=", storage::current_system_directory(native_directory)));
        const std::string native_su = native_directory + "su";
        RETURN_IF_ERROR(stage(context, "native-su-factory rc=", module_su_redirect::init(native_su.c_str(), false)));
    }
    const auto vendor_stage = [&](unsigned index, const char* name, auto init) {
        observation("vendor-state-before error=", vendor.before(index));
        const KModErr result = stage(context, name, init());
        observation("vendor-state-complete error=", vendor.complete(index, static_cast<int64_t>(result)));
        return result;
    };
    KModErr oem = vendor_stage(0, "oem-mtk rc=", [] { return module_bypass_mtk_mkp::init(); });
    if (is_ok(oem)) oem = vendor_stage(1, "oem-oplus-guard rc=", [] { return module_bypass_oplus_secure_guard::init(); });
    if (is_ok(oem)) (void)vendor_stage(2, "oem-oplus-inte rc=", [] { return module_bypass_oplus_inte::init(); });
    RETURN_IF_ERROR(stage(context, "root rc=", module_root::set_current_root(true)));
    storage::StorageContext storage_context;
    RETURN_IF_ERROR(stage(context, "root-storage rc=", storage::current_context(storage_context)));
    RETURN_IF_ERROR(stage(context, "root-environment rc=",
        module_root::init_anti_detect_root_env({storage_context.locator.kernel_component,
                                             storage_context.locator.system_component}, false)));
    std::string system_directory;
    RETURN_IF_ERROR(stage(context, "su-storage rc=", storage::current_system_directory(system_directory)));
    const std::string su = system_directory + "su";
    RETURN_IF_ERROR(stage(context, "su-redirect rc=", module_su_redirect::init(su.c_str(), false)));
    (void)stage(context, "proc-bootconfig rc=", module_proc_bootconfig::init());
    (void)stage(context, "proc-cmdline rc=", module_proc_cmdline::init());
    // Re-read configuration at the same boundary as the original native chain.
    RETURN_IF_ERROR(stage(context, "selinux-config-root rc=", module_root::set_current_root(true)));
    RETURN_IF_ERROR(stage(context, "selinux-install.mode rc=", read_config(context, "install.mode", mode_text)));
    const auto mode = mode_text == "hotload" ? maplesu::sdk::InstallMode::HotLoad : maplesu::sdk::InstallMode::Boot;
    std::string exploit_text;
    RETURN_IF_ERROR(stage(context, "install_config.exploit rc=",
                          read_config(context, "install_config.exploit", exploit_text)));
    // Preserve the existing parser: magica currently selects None in the boot helper.
    const auto exploit = exploit_text == "cve2026_43499" ? maplesu::sdk::ExploitMethod::CVE2026_43499 :
                                                       maplesu::sdk::ExploitMethod::None;
    char message[96];
    const int policy = helper_policy_start(&context, message, sizeof(message));
    context.policy_error = policy;
    helper_log_error(&context, "policy rc=", policy);
    if (policy) helper_log_pair(&context, "policy detail: ", message);
    // A policy failure still executes native SELinux initialization.
    RETURN_IF_ERROR(stage(context, "selinux-native rc=", module_selinux::init(mode, exploit)));
    (void)stage(context, "tombstone rc=", module_tombstone_purge::init(false, false));
    helper_log_error(&context, "identity-launch rc=", maplesu_environment_identity_start());
    return KModErr::OK;
}

KModErr initialize_modules(helper_context& context) {
    context.capability_stage = 'U';
    context.capability_error = HELPER_E_UNKNOWN;
    if (context.policy_ready) {
        std::vector<module_runtime::Record> results;
        const auto batch = module_runtime::run_boot_batch(context.root_key, sdk_version(), results);
        stage(context, "module-batch rc=", batch.first_error);
        if (is_failed(batch.bootstrap_error))
            return stage(context, "module-batch-bootstrap rc=", batch.bootstrap_error);
        if (is_failed(batch.first_error)) {
            context.capability_error = static_cast<helper_i64>(batch.first_error);
            return KModErr::OK;
        }
        context.capability_stage = 'C';
        context.capability_error = 0;
    } else {
        helper_log(&context, "module-batch gated: policy is not ready");
        context.capability_stage = context.policy_error ? 'P' : 'U';
        context.capability_error = context.policy_error ? context.policy_error : HELPER_E_UNKNOWN;
    }
    return KModErr::OK;
}

KModErr initialize(helper_context& context) {
    RETURN_IF_ERROR(initialize_core(context));
    return initialize_modules(context);
}

int load_single(helper_context& context, const char* id) {
    storage::WorkerScope worker(id);
    const KModErr rooted = worker.owner_mode() ? worker.root() : acquire_root(context.root_key);
    if (is_failed(rooted)) { stage(context, "single-root rc=", rooted); return HELPER_EXIT_ENVIRONMENT; }
    const auto result = module_runtime::run_single(context.root_key, id, sdk_version());
    helper_log_error(&context, "single-loader rc=", static_cast<int64_t>(result.loader));
    helper_log_error(&context, "single-main rc=", result.entry);
    if (result.skipped) helper_log(&context, "single-module skipped");
    const bool succeeded = (is_ok(result.loader) && result.entry == 0) || result.skipped;
    const KModErr closed = worker.close_checked();
    if (is_failed(closed)) helper_log_error(&context, "single-worker-close rc=", static_cast<int64_t>(closed));
    return succeeded && is_ok(closed) ? HELPER_EXIT_OK : HELPER_EXIT_ENVIRONMENT;
}
}
