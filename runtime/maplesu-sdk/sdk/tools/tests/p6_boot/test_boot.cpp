#include "private/bootstrap_runtime.h"
#include "worker_boundary.h"
#include <cerrno>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <fcntl.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

namespace fs = std::filesystem;
extern "C" const char maplesu_helper_root_key_token[4096] =
    "0123456789abcdef0123456789abcdef0123456789abcdef";
static const char* self = "/data/0123456789abcdef/init";
static int checks;
static void check(bool ok, const char* name) {
    ++checks;
    if (!ok) {
        std::cerr << "FAIL boot " << name << '\n';
        std::ifstream log(std::string(self) + ".log");
        std::cerr << log.rdbuf() << boot_fixture::events();
        std::exit(1);
    }
}
static int open_path(const char* path, helper_u32 flags, helper_u32 mode) {
    if (!boot_fixture::root_held()) return HELPER_E_ACCES;
    if (strstr(path, HELPER_SUFFIX_LOCK)) boot_fixture::event("lock-open");
    int native = (flags & HELPER_OPEN_WRITE) ? ((flags & HELPER_OPEN_READ) ? O_RDWR : O_WRONLY) : O_RDONLY;
    if (flags & HELPER_OPEN_CREATE) native |= O_CREAT;
    if (flags & HELPER_OPEN_NOFOLLOW) native |= O_NOFOLLOW;
    if (flags & HELPER_OPEN_NONBLOCK) native |= O_NONBLOCK;
    if (flags & HELPER_OPEN_DIRECTORY) native |= O_DIRECTORY;
    if (flags & HELPER_OPEN_EXCL) native |= O_EXCL;
    const int fd = open(path, native | O_CLOEXEC, mode);
    return fd < 0 ? -errno : fd;
}
static int stat_fd(int fd, helper_stat_info* out) {
    struct stat st{};
    if (fstat(fd, &st)) return -errno;
    *out = {};
    out->size = st.st_size; out->uid = st.st_uid; out->nlink = st.st_nlink;
    out->is_reg = S_ISREG(st.st_mode); out->is_dir = S_ISDIR(st.st_mode);
    return 0;
}
static long long read_fd(int fd, void* out, helper_u32 n) { const auto r = read(fd, out, n); return r < 0 ? -errno : r; }
static long long write_fd(int fd, const void* in, helper_u32 n) { const auto r = write(fd, in, n); return r < 0 ? -errno : r; }
static int close_fd(int fd) {
    char name[64], target[HELPER_PATH_MAX]{};
    snprintf(name, sizeof(name), "/proc/self/fd/%d", fd);
    const auto size = readlink(name, target, sizeof(target) - 1);
    const bool injected = size > 0 && strstr(target, HELPER_SUFFIX_LOCK) && boot_fixture::fail_lock_close();
    const int result = close(fd) ? -errno : 0;
    return injected ? HELPER_E_IO : result;
}
static int sync_fd(int fd) { return fsync(fd) ? -errno : 0; }
static int lock_fd(int fd, helper_u32 flags) {
    boot_fixture::event(flags & HELPER_LOCK_UN ? "unlock" : "lock");
    int native = flags & HELPER_LOCK_UN ? LOCK_UN : LOCK_EX;
    if (flags & HELPER_LOCK_NB) native |= LOCK_NB;
    return flock(fd, native) ? -errno : 0;
}
static int self_path(char* out, helper_u32 cap, helper_u32* length) {
    *length = strlen(self); if (*length >= cap) return HELPER_E_OVERFLOW;
    strcpy(out, self); return 0;
}
static int boot_id(char* out, helper_u32, helper_u32* length) {
    std::ifstream file("/proc/sys/kernel/random/boot_id"); std::string id; std::getline(file, id);
    strcpy(out, id.c_str()); *length = id.size(); return 0;
}
static int execute(const char* path, char* const args[], char* const env[]) {
    boot_fixture::event("exec");
    if (!boot_fixture::root_held()) return HELPER_E_ACCES;
    for (int fd = 3; fd < 128; ++fd) {
        char name[64], target[HELPER_PATH_MAX]{};
        snprintf(name, sizeof(name), "/proc/self/fd/%d", fd);
        const auto size = readlink(name, target, sizeof(target) - 1);
        if (size > 0 && strstr(target, HELPER_SUFFIX_LOCK)) return -EBUSY;
    }
    execve(path, args, env);
    return -errno;
}
static int rename_at(int old_fd, const char* old_name, int new_fd, const char* new_name) {
    return renameat(old_fd, old_name, new_fd, new_name) ? -errno : 0;
}
static int unlink_at(int fd, const char* name, helper_u32 flags) {
    return unlinkat(fd, name, flags) ? -errno : 0;
}
static void log_line(const char* text) { std::cerr << text << '\n'; }
static void put(const std::string& path, const std::string& data) { std::ofstream(path) << data; }
static std::string get(const std::string& path) { std::ifstream f(path); return {std::istreambuf_iterator<char>(f), {}}; }
namespace maplesu::bootstrap {
module::SdkVersion sdk_version() { return {5, 2, 0}; }
KModErr initialize_core(helper_context&) {
    boot_fixture::event("core");
    std::ofstream("/tmp/native-init", std::ios::app) << "core\n";
    return KModErr::OK;
}
KModErr initialize_modules(helper_context& context) {
    boot_fixture::event("modules");
    context.capability_stage = 'C'; context.capability_error = 0;
    std::ofstream("/tmp/native-init", std::ios::app) << "modules\n";
    return KModErr::OK;
}
KModErr initialize(helper_context& context) { initialize_core(context); return initialize_modules(context); }
KModErr acquire_root(const char*) { return KModErr::OK; }
int load_single(helper_context&, const char*) { return 0; }
}
int main() {
    helper_ops ops{};
    ops.open_path = open_path; ops.fstat = stat_fd; ops.read = read_fd; ops.write = write_fd;
    ops.close = close_fd; ops.fsync = sync_fd; ops.flock_dir = lock_fd;
    ops.renameat = rename_at; ops.unlinkat = unlink_at;
    ops.read_self_path = self_path; ops.read_boot_id = boot_id; ops.execve = execute; ops.log_line = log_line;
    fs::create_directories(fs::path(self).parent_path());
    put(std::string(fs::path(self).parent_path()) + "/install.mode", "boot");
    fs::create_directories("/data/adb/maplesu/bin");
    fs::create_directories("/system/bin");
    fs::create_symlink("/bin/sh", "/system/bin/sh");
    const std::string dispatcher = "/data/adb/maplesu/bin/module-boot.sh";
    put(dispatcher, "#!/bin/sh\nprintf '%s\\n' \"$1\" >> /tmp/helper-events\nprintf 'post-fs-data\\n' >> /tmp/native-init\nprintf '%s|%s|%s|%s' \"$KSU\" \"$KSU_VER_CODE\" \"$KSU_RUNTIME_MODE\" \"$MAPLESU_RUNTIME_MODE\" > /tmp/helper-mode\nexit 17\n");
    chmod(dispatcher.c_str(), 0755);
    char* args[] = {const_cast<char*>(self), const_cast<char*>("--boot-event"), const_cast<char*>("post-fs-data"), nullptr};
    check(helper_run_boot(&ops, 3, args, nullptr) == 17, "event preserves dispatcher exit code");
    check(get(std::string(self) + HELPER_SUFFIX_SAFE_FLAG) == "2", "script nonzero does not turn SDK bootstrap into protection");
    check(get("/tmp/native-init") == "core\npost-fs-data\nmodules\n", "actual early dispatcher completes before native modules");
    check(get("/tmp/helper-mode") == "true|50200|lkm|boot", "early dispatcher receives actual persisted boot mode and SDK facts");
    put(dispatcher, "#!/bin/sh\nprintf '%s\\n' \"$1\" >> /tmp/helper-events\nexit 0\n");
    args[2] = const_cast<char*>("service");
    check(helper_run_boot(&ops, 3, args, nullptr) == 0, "late event reaches persistent dispatcher");
    check(get("/tmp/native-init") == "core\npost-fs-data\nmodules\n", "late event coalesces completed native initialization");
    check(get("/tmp/helper-events") == "post-fs-data\nservice\n", "event argument survives real fork exec wait");
    args[2] = const_cast<char*>("post-fs-data");
    check(helper_run_boot(&ops, 3, args, nullptr) == 0, "completed native session still dispatches actual repeated early event");
    check(get("/tmp/native-init") == "core\npost-fs-data\nmodules\n", "repeated event does not load native modules twice");
    put(dispatcher, "#!/bin/sh\nkill -TERM $$\n");
    args[2] = const_cast<char*>("service");
    check(helper_run_boot(&ops, 3, args, nullptr) == HELPER_EXIT_ENVIRONMENT, "nested dispatcher signal remains a real failure");
    check(get(std::string(self) + HELPER_SUFFIX_SAFE_FLAG) == "2", "script signal preserves successful native initialization");
    args[2] = const_cast<char*>("not-an-event");
    check(helper_run_boot(&ops, 3, args, nullptr) == HELPER_EXIT_USAGE, "unknown event is rejected before initialization");
    args[2] = const_cast<char*>("boot-completed");
    fs::remove(dispatcher); fs::create_symlink("/bin/true", dispatcher);
    check(helper_run_boot(&ops, 3, args, nullptr) == HELPER_EXIT_ENVIRONMENT, "symlink dispatcher is not executed");
    check(get(std::string(self) + HELPER_SUFFIX_SAFE_FLAG) == "2", "publication failure preserves successful SDK session");

    // Exercise production WorkerScope on the forked helper and shell processes.
    // No Key path matches this installed name; the held-executable READ seam
    // admits the explicit EarlyHelper mode before either child is created.
    self = "/data/fedcba9876543210/init";
    fs::create_directories(fs::path(self).parent_path());
    put(std::string(fs::path(self).parent_path()) + "/install.mode", "boot");
    fs::remove(dispatcher);
    put(dispatcher, "#!/bin/sh\nprintf '%s\\n' \"$1\" >> /tmp/helper-events\nexit 0\n");
    args[0] = const_cast<char*>(self);
    args[2] = const_cast<char*>("post-fs-data");
    boot_fixture::reset();
    check(helper_run_boot(&ops, 3, args, nullptr) == 0, "EarlyHelper real boot worker succeeds");
    auto flow = boot_fixture::events();
    check(flow.find("vendor-read:") < flow.find("select-helper:"), "installation READ precedes explicit helper mode");
    check(flow.find("helper-open:") < flow.find("root:") && flow.find("root:") < flow.find("lock-open:"),
          "forked worker opens and roots before the boot lock");
    const auto exec = flow.find("exec:");
    const auto shell_pid = flow.substr(exec + 5, flow.find('\n', exec) - exec - 5);
    check(exec != std::string::npos && flow.find("helper-open:" + shell_pid) < exec &&
          flow.find("close:" + shell_pid) < exec, "shell opens its own session and checked-closes before exec");
    const auto boot_open = flow.find("helper-open:");
    const auto boot_pid = flow.substr(boot_open + 12, flow.find('\n', boot_open) - boot_open - 12);
    check(flow.find("unlock:" + boot_pid) < flow.rfind("close:" + boot_pid),
          "boot worker releases lock before its checked session close");
    check(get(std::string(self) + HELPER_SUFFIX_SAFE_FLAG) == "2", "helper worker commits real completion receipt");

    boot_fixture::reset("shell-root");
    args[2] = const_cast<char*>("service");
    check(helper_run_boot(&ops, 3, args, nullptr) == HELPER_EXIT_ENVIRONMENT,
          "shell ROOT failure blocks exec and reaches the helper caller");
    check(boot_fixture::events().find("exec:") == std::string::npos, "failed shell ROOT never executes dispatcher");
    boot_fixture::reset("shell-close");
    check(helper_run_boot(&ops, 3, args, nullptr) == HELPER_EXIT_ENVIRONMENT,
          "uncertain shell CLOSE blocks exec and reaches the helper caller");
    check(boot_fixture::events().find("exec:") == std::string::npos, "failed shell CLOSE never executes dispatcher");
    boot_fixture::reset("lock-close");
    check(helper_run_boot(&ops, 3, args, nullptr) == HELPER_EXIT_ENVIRONMENT,
          "inherited lock CLOSE error reaches the helper caller");
    check(boot_fixture::events().find("exec:") == std::string::npos,
          "inherited lock CLOSE error prevents dispatcher exec");
    boot_fixture::reset("boot-close");
    check(helper_run_boot(&ops, 3, args, nullptr) == HELPER_EXIT_ENVIRONMENT,
          "boot worker CLOSE failure is not replaced by stage success");
    boot_fixture::reset("boot-root");
    check(helper_run_boot(&ops, 3, args, nullptr) == HELPER_EXIT_ENVIRONMENT,
          "boot worker ROOT failure remains a real failure");
    flow = boot_fixture::events();
    check(flow.find("lock-open:") == std::string::npos && flow.find("core:") == std::string::npos,
          "boot worker ROOT failure prevents lock and initialization");

    // Real original-target exec keeps option-like argv and the exact env vector.
    self = "/data/0123456789abcdef/init";
    boot_fixture::reset();
    const char* original = "/system/bin/original-fixture";
    put(original, "#!/bin/sh\nprintf '%s\\n' \"$0\" \"$#\" \"$1\" \"$2\" \"$P6_SENTINEL\" > /tmp/original-vector\nexit 23\n");
    chmod(original, 0755);
    char* original_args[] = {const_cast<char*>(original), const_cast<char*>("--boot-event"),
                             const_cast<char*>("post-fs-data"), nullptr};
    char* original_env[] = {const_cast<char*>("P6_SENTINEL=kept"), nullptr};
    const pid_t child = fork();
    if (!child) _exit(helper_run_boot(&ops, 3, original_args, original_env));
    int status = 0;
    check(child > 0 && waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 23,
          "original target runs after helper resources are retired");
    check(get("/tmp/original-vector") == std::string(original) + "\n2\n--boot-event\npost-fs-data\nkept\n",
          "original argv and env survive actual fork exec wait");

    self = "/data/fedcba9876543210/init";
    boot_fixture::reset();
    const auto safe = std::string(self) + HELPER_SUFFIX_SAFE_FLAG;
    fs::remove(safe);
    check(mkfifo(safe.c_str(), 0600) == 0, "real FIFO receipt created");
    args[0] = const_cast<char*>(self);
    check(helper_run_boot(&ops, 3, args, nullptr) == HELPER_EXIT_ENVIRONMENT,
          "real FIFO state read is nonblocking and rejected");
    check(fs::is_fifo(safe), "malformed state remains unchanged for repair");
    std::cout << "PASS boot " << checks << " assertions\n";
}
