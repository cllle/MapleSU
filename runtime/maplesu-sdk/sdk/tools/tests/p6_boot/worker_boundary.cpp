#include "worker_boundary.h"
#include "maplesu_storage_worker_private.h"
#include <fcntl.h>
#include <fstream>
#include <unistd.h>

extern "C" const char maplesu_helper_root_key_token[4096];

// Authenticated kernel/storage operations are boundary seams; bootstrap entry
// points are recording stubs in test_boot.cpp. WorkerScope and boot/session
// state machines are production code; files, flock, fork, exec and wait are real.
namespace {
uint64_t auth_state;
pid_t session_process, boot_process, rooted_process;
std::string failure;
maplesu::storage::StorageScope* current_scope;
}
namespace boot_fixture {
void reset(const char* fail) {
    auth_state = 0;
    session_process = boot_process = rooted_process = 0;
    current_scope = nullptr;
    failure = fail;
    unlink("/tmp/helper-workers");
}
void event(const char* name) {
    const std::string line = std::string(name) + ":" + std::to_string(getpid()) + "\n";
    const int fd = open("/tmp/helper-workers", O_WRONLY | O_APPEND | O_CREAT | O_CLOEXEC, 0600);
    if (fd >= 0) { const auto written = write(fd, line.data(), line.size()); (void)written; close(fd); }
}
bool root_held() {
    return !kernel_module::maplesu_request_helper_mode() || rooted_process == getpid();
}
bool fail_lock_close() { return failure == "lock-close" && boot_process && boot_process != getpid(); }
std::string events() {
    std::ifstream stream("/tmp/helper-workers");
    return {std::istreambuf_iterator<char>(stream), {}};
}
}
namespace kernel_module {
uint64_t maplesu_request_auth_state() { return auth_state; }
KModErr maplesu_request_set_auth_mode(RequestAuthMode mode) {
    boot_fixture::event("select-helper");
    auth_state = (auth_state + 4) | static_cast<unsigned>(mode);
    return KModErr::OK;
}
KModErr maplesu_request_helper_vendor(uint64_t action, unsigned, maplesu_installation_state_v3& state) {
    boot_fixture::event("vendor-read");
    if (action != MAPLESU_INSTALLATION_READ || failure == "locate") return KModErr::ERR_MODULE_PARAM;
    std::memcpy(state.name, "fedcba9876543210", 16);
    return KModErr::OK;
}
bool maplesu_request_owner_lifetime_capture(RequestOwnerLifetimeToken&) {
    return session_process == getpid();
}
bool maplesu_request_owner_binding_matches(const RequestOwnerBinding& binding) {
    return binding.auth_state == auth_state && binding.process == static_cast<uint64_t>(getpid()) &&
        session_process == getpid();
}
KModErr maplesu_request_helper_open() {
    if (!maplesu_request_helper_mode() || session_process == getpid()) return KModErr::ERR_MODULE_PARAM;
    if (!boot_process) boot_process = getpid();
    boot_fixture::event("helper-open");
    session_process = getpid();
    return KModErr::OK;
}
KModErr maplesu_request_owner_close() {
    const bool active = session_process == getpid();
    boot_fixture::event(active ? "close" : "close-idle");
    session_process = 0;
    if (active && failure == (boot_process == getpid() ? "boot-close" : "shell-close"))
        return KModErr::ERR_MODULE_PARAM;
    return KModErr::OK;
}
KModErr maplesu_request_owner_deployment(RequestDeploymentNamespace& out) {
    if (session_process != getpid()) return KModErr::ERR_MODULE_PARAM;
    std::memcpy(out.record.name, "fedcba9876543210", 16);
    out.binding.auth_state = auth_state;
    out.binding.process = getpid();
    out.binding.actual_uid = getuid();
    return KModErr::OK;
}
CleanupReceiptScope::CleanupReceiptScope() noexcept
    : saved_errno_(errno), saved_process_(getpid()), saved_reply_{} {}
CleanupReceiptScope::~CleanupReceiptScope() noexcept { errno = saved_errno_; }
}
namespace default_modules::module_root {
KModErr set_current_root(bool privileged) {
    boot_fixture::event("root");
    if (kernel_module::maplesu_request_helper_mode() &&
        (privileged || session_process != getpid() ||
         failure == (boot_process == getpid() ? "boot-root" : "shell-root")))
        return KModErr::ERR_MODULE_PARAM;
    rooted_process = getpid();
    return KModErr::OK;
}
}
namespace maplesu::storage {
StorageScope::StorageScope(const StorageLocator& locator, const char* module_id)
    : previous_(current_scope) {
    if (session_process != getpid()) return;
    context_.locator = locator;
    context_.module_id = module_id ? module_id : "";
    error_ = KModErr::OK;
    selected_ = true;
    current_scope = this;
    boot_fixture::event("storage");
}
StorageScope::~StorageScope() { clear(); }
void StorageScope::clear() {
    if (selected_) { current_scope = previous_; selected_ = false; boot_fixture::event("storage-end"); }
}
KModErr StorageScope::select_module(const char* module_id) {
    context_.module_id = module_id ? module_id : "";
    return KModErr::OK;
}
KModErr current_context(StorageContext& out) {
    if (kernel_module::maplesu_request_auth_legacy(auth_state)) {
        out = {};
        std::memcpy(out.locator.kernel_component, maplesu_helper_root_key_token, 16);
        std::memcpy(out.locator.system_component, maplesu_helper_root_key_token, 16);
        out.locator.system_component[16] = out.locator.kernel_component[0];
        return KModErr::OK;
    }
    if (!current_scope || session_process != getpid()) return KModErr::ERR_MODULE_ROOT_KEY;
    out = current_scope->context_;
    return KModErr::OK;
}
KModErr current_kernel_directory(std::string& out) {
    StorageContext context;
    RETURN_IF_ERROR(current_context(context));
    out = std::string("/data/") + context.locator.kernel_component + "/";
    return KModErr::OK;
}
}
