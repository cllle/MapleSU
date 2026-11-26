#include "lifecycle.h"
#include "installation_state.h"
#include "../compat/installer_legacy.h"
#include "maplesu_storage_private.h"
#include "maplesu_storage_worker_private.h"

#include <cerrno>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <new>
#include <optional>
#include <stdexcept>
#include <string>
#include <string_view>
#include <system_error>
#include <vector>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/xattr.h>
#include <unistd.h>

// ---- helper-allowlist 环境状态助手（实现在 maplesu_environment_state.o） ----
extern "C" uint32_t maplesu_environment_state();
extern "C" pid_t maplesu_wait_environment_child(pid_t child, int* status);
extern "C" long maplesu_installer_launch_error(int stage, const char* helper);
extern "C" int maplesu_installer_reboot(const char* helper);
extern "C" int maplesu_installer_exec(const char*, char* const[], char* const[]);
extern "C" pid_t maplesu_installer_wait_child(pid_t, int*, const char*);
extern "C" long maplesu_installer_result_open(int, int[2]);
extern "C" long maplesu_installer_result_parent(pid_t, int, int[2]);
extern "C" void maplesu_installer_result_worker(int[2]);
extern "C" int maplesu_installer_result_complete(int[2], const char*, int);
extern "C" KModErr maplesu_prepare_su_exec_directory(const char*);

namespace maplesu::install {
namespace {
constexpr bool succeeded(core::err_t error) { return error == core::kOk; }
constexpr bool failed(core::err_t error) { return error != core::kOk; }
}

namespace {
constexpr size_t kRootKeyBufferSize = 1024;

bool parse_installed_environment_version(std::string_view text, Version& output) {
    std::size_t cursor = 0;
    const auto read_component = [&](uint32_t& value) {
        while (cursor < text.size() && (text[cursor] == ' ' || text[cursor] == '\t' ||
               text[cursor] == '\n' || text[cursor] == '\r' || text[cursor] == '\v' ||
               text[cursor] == '\f')) ++cursor;
        const bool negative = cursor < text.size() && text[cursor] == '-';
        if (cursor < text.size() && (text[cursor] == '+' || negative)) ++cursor;
        const std::size_t first_digit = cursor;
        uint32_t magnitude = 0;
        while (cursor < text.size() && text[cursor] >= '0' && text[cursor] <= '9') {
            const uint32_t digit = static_cast<uint32_t>(text[cursor] - '0');
            if (magnitude > (UINT32_MAX - digit) / 10u) return false;
            magnitude = magnitude * 10u + digit;
            ++cursor;
        }
        if (cursor == first_digit) return false;
        value = negative ? uint32_t{0} - magnitude : magnitude;
        return true;
    };
    Version parsed{};
    if (!read_component(parsed.major) || cursor == text.size() || text[cursor++] != '.' ||
        !read_component(parsed.minor) || cursor == text.size() || text[cursor++] != '.' ||
        !read_component(parsed.patch)) return false;
    output = parsed;
    return true;
}
}  // namespace

static std::string get_kernel_file_dir_path() {
    std::string directory;
    if (storage::current_kernel_directory(directory) != KModErr::OK) return {};
    return directory;
}

static std::string get_autorun_bootstrap_path() {
    const std::string directory = get_kernel_file_dir_path();
    if (directory.empty()) return {};
    return std::filesystem::path(directory) / "init";
}

static std::string version_path() {
    const std::string directory = get_kernel_file_dir_path();
    if (directory.empty()) return {};
    return std::filesystem::path(directory) / compat::kInstallerVersionFile;
}

static core::err_t retire_environment_version() {
    const std::string installed_path = version_path();
    if (installed_path.empty()) return compat::kInstallerIdentity;
    const std::string directory_path =
        std::filesystem::path(installed_path).parent_path().string();
    const int directory = ::open(directory_path.c_str(),
                                 O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (directory < 0) return compat::kInstallerOpenDirectory;
    core::err_t err = compat::kInstallerOk;
    const int removed = ::unlinkat(directory, compat::kInstallerVersionFile, 0);
    if (removed != 0 && errno != ENOENT) err = compat::kInstallerDeleteFile;
    if (removed == 0 && ::fsync(directory) != 0) err = compat::kInstallerWriteFile;
    if (::close(directory) != 0 && succeeded(err)) err = compat::kInstallerWriteFile;
    return err;
}

static int unsafe_environment_state() {
    return static_cast<int>(maplesu_environment_state());
}



namespace file_utils {

enum class SelinuxFileFlag : uint32_t {
    None = 0,
    KernelExec = 1,
};

static bool read_text_file(const char* path, std::string& out_text) {
    std::vector<uint8_t> bytes;
    std::ifstream stream(path, std::ios::binary);
    if (!stream) return false;

    stream.seekg(0, std::ios::end);
    const std::streamoff size = stream.tellg();
    if (size < 0) return false;

    stream.seekg(0, std::ios::beg);
    bytes.resize(static_cast<std::size_t>(size));
    stream.read(reinterpret_cast<char*>(bytes.data()), size);
    if (!stream) return false;

    out_text.assign(bytes.begin(), bytes.end());
    return true;
}

static bool create_empty_file(const char* path) {
    std::ofstream stream(path, std::ios::out | std::ios::trunc | std::ios::binary);
    if (!stream.is_open()) return false;
    stream.close();
    return stream.good();
}

static bool set_file_selinux_access_mode(const char* path,
                                         SelinuxFileFlag flag = SelinuxFileFlag::None,
                                         mode_t access_mode = 0777) {
    (void)flag;
    if (::chmod(path, access_mode) != 0) return false;
    static constexpr char kSelinuxKey[] = "security.selinux";
    static constexpr char kSelinuxValue[] = "u:object_r:kernel:s0";
    return ::setxattr(path, kSelinuxKey, kSelinuxValue, sizeof(kSelinuxValue), 0) == 0;
}

static bool write_text_file(const char* path, std::string_view text) {
    if (path == nullptr) return false;
    std::ofstream stream(path, std::ios::out | std::ios::trunc);
    if (!stream.is_open()) return false;
    stream << text;
    stream.close();
    return stream.good();
}

}  // namespace file_utils

namespace boot_session_utils {
namespace detail {

static void trim_ascii_space_inplace(std::string& text) {
    const auto is_ascii_space = [](char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    };
    const std::size_t size = text.size();
    std::size_t first = 0;
    while (first < size && is_ascii_space(text[first])) ++first;
    std::size_t last = size;
    while (last > first && is_ascii_space(text[last - 1])) --last;
    if (first != 0 || last != size) text = text.substr(first, last - first);
}

}  // namespace detail

static std::string read_boot_session() {
    std::FILE* fp = std::fopen("/proc/sys/kernel/random/boot_id", "r");
    if (fp == nullptr) return std::string();
    char line[128] = {0};
    char* got = std::fgets(line, static_cast<int>(sizeof(line)), fp);
    std::fclose(fp);
    if (got == nullptr) return std::string();
    std::string session(line);
    detail::trim_ascii_space_inplace(session);
    return session;
}

}  // namespace boot_session_utils



static core::err_t read_installed_version(std::string& out_version) {
    if (unsafe_environment_state() == 0) {
        return compat::kInstallerEnvironmentAbsent;
    }

    const std::string installed_path = version_path();
    if (installed_path.empty()) return compat::kInstallerIdentity;
    std::error_code status_error;
    const bool exists = std::filesystem::exists(installed_path, status_error);
    if (status_error) {
        errno = status_error.value();
        return compat::kInstallerReadFile;
    }
    if (!exists) return compat::kInstallerFileAbsent;
    if (!file_utils::read_text_file(installed_path.c_str(), out_version)) {
        return compat::kInstallerReadFile;
    }
    return compat::kInstallerOk;
}

static core::err_t unsafe_installed_version(std::string& out_version) {
    storage::WorkerScope worker;
    const core::err_t root_err = static_cast<core::err_t>(worker.root());
    if (failed(root_err)) return root_err;
    return read_installed_version(out_version);
}

static void exec_bootstrap_command(const char* command) {
    const auto is_space = [](char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    };
    if (command == nullptr || *command == '\0') ::_exit(127);
    char* buffer = ::strdup(command);
    if (buffer == nullptr) ::_exit(127);

    constexpr std::size_t kInitialCapacity = 8;
    char** argv = static_cast<char**>(::calloc(kInitialCapacity + 1, sizeof(char*)));
    if (argv == nullptr) {
        ::free(buffer);
        ::_exit(127);
    }

    std::size_t argc = 0;
    std::size_t capacity = kInitialCapacity;
    char* cursor = buffer;
    while (*cursor != '\0') {
        while (*cursor != '\0' && is_space(*cursor)) ++cursor;
        if (*cursor == '\0') break;

        if (argc >= capacity) {
            const std::size_t grown_capacity = capacity * 2 + 1;
            char** grown = static_cast<char**>(::realloc(argv, grown_capacity * sizeof(char*)));
            if (grown == nullptr) {
                ::free(argv);
                ::free(buffer);
                ::_exit(127);
            }
            ::memset(grown + capacity, 0, (grown_capacity - capacity) * sizeof(char*));
            argv = grown;
            capacity = grown_capacity;
        }

        argv[argc] = cursor;
        ++argc;

        if (*cursor == '"' || *cursor == '\'') {
            const char quote = *cursor;
            argv[argc - 1] = cursor + 1;
            char* scan = argv[argc - 1];
            while (*scan != '\0' && *scan != quote) ++scan;
            const bool quote_closed = (*scan == quote);
            *scan = '\0';
            cursor = scan + (quote_closed ? 1 : 0);
        } else {
            char* scan = cursor;
            while (*scan != '\0' && !is_space(*scan)) ++scan;
            if (*scan != '\0') {
                *scan = '\0';
                cursor = scan + 1;
            } else {
                cursor = scan;
            }
        }
    }

    if (argc == 0) {
        ::free(argv);
        ::free(buffer);
        ::_exit(127);
    }
    argv[argc] = nullptr;
    maplesu_installer_exec(argv[0], argv, environ);
    ::free(argv);
    ::free(buffer);
    ::_exit(127);
}

namespace {
bool installer_identity_valid(const char* root_key) {
    const auto auth = kernel_module::maplesu_request_auth_state();
    if (!kernel_module::maplesu_request_auth_usable(auth) ||
        kernel_module::maplesu_request_auth_helper(auth)) return false;
    if (root_key == nullptr) return true;
    const std::size_t length = ::strnlen(root_key, kRootKeyBufferSize);
    return kernel_module::maplesu_request_auth_legacy(auth) &&
        length >= core::kRootKeyDirChars && length < kRootKeyBufferSize;
}

core::err_t select_installer_worker_identity(const char* root_key) {
    if (!installer_identity_valid(root_key)) return compat::kInstallerIdentity;
    if (root_key != nullptr) {
        compat::select_installer_identity(root_key);
        return compat::kInstallerOk;
    }
    const auto auth = kernel_module::maplesu_request_auth_state();
    return kernel_module::maplesu_request_auth_manager(auth) ? compat::kInstallerOk :
        static_cast<core::err_t>(kernel_module::maplesu_request_set_auth_mode(
            kernel_module::RequestAuthMode::ManagerOwner));
}

core::err_t close_installer_worker(std::optional<storage::WorkerScope>& worker) {
    if (!worker) return compat::kInstallerOk;
    core::err_t error;
    try {
        error = static_cast<core::err_t>(worker->close_checked());
    } catch (const std::bad_alloc&) {
        errno = ENOMEM;
        error = static_cast<core::err_t>(KModErr::ERR_NO_MEM);
    } catch (const std::length_error&) {
        errno = ENOMEM;
        error = static_cast<core::err_t>(KModErr::ERR_NO_MEM);
    }
    const int saved = errno;
    worker.reset();
    errno = saved;
    return error;
}

core::err_t close_installation_scope(state::Mutation& installation,
                                    std::optional<storage::WorkerScope>& worker,
                                    core::err_t error) {
    const int saved = errno;
    installation.close();
    if (failed(error)) {
        kernel_module::CleanupReceiptScope preserve;
        (void)close_installer_worker(worker);
        return error;
    }
    const auto closed = close_installer_worker(worker);
    if (succeeded(closed)) errno = saved;
    return closed;
}

[[noreturn]] void finish_installation_child(compat::InstallerChannel& pipe,
                                          state::Mutation& installation,
                                          std::optional<storage::WorkerScope>& worker,
                                          core::err_t error) {
    error = close_installation_scope(installation, worker, error);
    const bool sent = pipe.write_error(error);
    pipe.close();
    ::_exit(sent ? 0 : 127);
}

core::err_t collect_installation_result(compat::InstallerChannel& pipe) {
    if (pipe.child_pid() <= 0) {
        pipe.close();
        return compat::kInstallerReadError;
    }
    core::err_t error = compat::kInstallerOk;
    if (!pipe.read_error(error)) error = compat::kInstallerReadError;
    const int saved = errno;
    pipe.close();
    int child_status = 0;
    const pid_t waited = maplesu_wait_environment_child(pipe.child_pid(), &child_status);
    if (succeeded(error) && (waited != pipe.child_pid() ||
        !WIFEXITED(child_status) || WEXITSTATUS(child_status) != 0))
        return static_cast<core::err_t>(-21002);
    errno = saved;
    return error;
}
}  // namespace

core::err_t install_environment(const char* root_key, DeploymentMode mode,
                                   const InstallOptions& config) {
    if (!installer_identity_valid(root_key)) return compat::kInstallerIdentity;
    if (mode != DeploymentMode::Boot && mode != DeploymentMode::HotLoad &&
        mode != DeploymentMode::Refresh) return static_cast<core::err_t>(KModErr::ERR_MODULE_PARAM);
    if (mode == DeploymentMode::Refresh && kernel_module::maplesu_request_owner_mode()) {
        errno = EOPNOTSUPP;
        return static_cast<core::err_t>(KModErr::ERR_MODULE_TEST_CHANNEL);
    }

    compat::InstallerChannel pipe;
    core::err_t err = compat::kInstallerOk;
    if (pipe.start()) {
        state::Mutation installation;
        std::optional<storage::WorkerScope> worker;
        const auto perform = [&]() -> core::err_t {
            err = static_cast<core::err_t>(worker->root());
            if (failed(err)) return err;
            if (get_kernel_file_dir_path().empty()) return compat::kInstallerIdentity;

            if (mode != DeploymentMode::Refresh) {
                err = compat::installer_clean_older();
                if (failed(err)) return err;
            }

            const std::string boot_session = boot_session_utils::read_boot_session();
            if (boot_session.empty()) return compat::kInstallerBootSession;

            std::string installed_version;
            const core::err_t version_error = read_installed_version(installed_version);
            if (failed(version_error) && version_error != compat::kInstallerEnvironmentAbsent &&
                version_error != compat::kInstallerFileAbsent) return version_error;

            // A failed publication may have retired only the version. Refresh can
            // reconcile that partial environment, but never initialize an absent one.
            if (mode == DeploymentMode::Refresh && version_error == compat::kInstallerEnvironmentAbsent)
                return version_error;
            const bool need_install =
                (mode == DeploymentMode::Boot) || (mode == DeploymentMode::Refresh) ||
                (mode == DeploymentMode::HotLoad && installed_version != kSdkVersionText);

            if (need_install) {
                err = compat::installer_create_directories();
                if (failed(err)) return err;

                auto observed = installation.open();
                state::diagnose("open-replace", observed);
                if (state::mutation_open_failed(observed)) return compat::kInstallerWriteFile;
                if (installation.ready()) {
                    const bool required = installation.revocation_required();
                    observed = installation.revoke();
                    state::diagnose("revoke-replace", observed);
                    if (required && observed.code) return compat::kInstallerWriteFile;
                }

                err = retire_environment_version();
                if (failed(err)) return err;

                err = compat::installer_su();
                if (failed(err)) return err;

                err = compat::installer_webui();
                if (failed(err)) return err;

                err = mode == DeploymentMode::Refresh ? compat::installer_refresh_bootstrap()
                                                      : compat::installer_prepare_bootstrap();
                if (failed(err)) return err;

                err = compat::installer_resetprop();
                if (failed(err)) return err;

                if (mode != DeploymentMode::Refresh) {
                    err = static_cast<core::err_t>(worker->root());
                    if (failed(err)) return err;

                    {
                        const std::string bootstrap_path = get_autorun_bootstrap_path();
                        if (bootstrap_path.empty()) return compat::kInstallerIdentity;
                        const std::string enable_flag_path = bootstrap_path + ".log" + ".enable";
                        if (!file_utils::create_empty_file(enable_flag_path.c_str())) {
                            return compat::kInstallerCreateFile;
                        }
                        if (!file_utils::set_file_selinux_access_mode(enable_flag_path.c_str())) {
                            return compat::kInstallerSetLabel;
                        }
                        ::sync();
                    }

                    err = static_cast<core::err_t>(worker->root());
                    if (failed(err)) return err;

                    {
                        const std::string bootstrap_path = get_autorun_bootstrap_path();
                        if (bootstrap_path.empty()) return compat::kInstallerIdentity;
                        const std::string fail_protect_path = bootstrap_path + ".boot_fail_protect";
                        if (!file_utils::create_empty_file(fail_protect_path.c_str())) {
                            return compat::kInstallerCreateFile;
                        }
                        if (!file_utils::set_file_selinux_access_mode(fail_protect_path.c_str())) {
                            return compat::kInstallerSetLabel;
                        }
                        ::sync();
                    }

                    (void)compat::installer_first_configuration();
                }
            }

            if (!need_install && kernel_module::maplesu_request_owner_mode()) {
                std::string system_directory;
                err = static_cast<core::err_t>(storage::current_system_directory(system_directory));
                if (failed(err)) return err;
                err = static_cast<core::err_t>(maplesu_prepare_su_exec_directory(system_directory.c_str()));
                if (failed(err)) return err;
            }

            if (mode != DeploymentMode::Refresh) {
                err = static_cast<core::err_t>(worker->root());
                if (failed(err)) return err;

                {
                    const std::string directory = get_kernel_file_dir_path();
                    if (directory.empty()) return compat::kInstallerIdentity;
                    const std::string mode_path = std::filesystem::path(directory) / "install.mode";
                    std::string mode_text;
                    switch (mode) {
                        case DeploymentMode::Boot: mode_text = "boot"; break;
                        case DeploymentMode::HotLoad: mode_text = "hotload"; break;
                        default: break;
                    }
                    if (!file_utils::write_text_file(mode_path.c_str(), mode_text)) {
                        return compat::kInstallerWriteFile;
                    }
                    if (!file_utils::set_file_selinux_access_mode(mode_path.c_str(),
                            file_utils::SelinuxFileFlag::None, 0600)) {
                        return compat::kInstallerSetLabel;
                    }
                }

                err = static_cast<core::err_t>(worker->root());
                if (failed(err)) return err;

                {
                    const std::string directory = get_kernel_file_dir_path();
                    if (directory.empty()) return compat::kInstallerIdentity;
                    const std::string exploit_path = std::filesystem::path(directory) / "install_config.exploit";
                    const std::string exploit_text =
                        config.exploit == ExploitPolicy::Magica
                            ? "magica"
                            : (config.exploit == ExploitPolicy::CVE2026_43499 ? "cve2026_43499" : "none");
                    if (!file_utils::write_text_file(exploit_path.c_str(), exploit_text)) {
                        return compat::kInstallerWriteFile;
                    }
                    if (!file_utils::set_file_selinux_access_mode(exploit_path.c_str())) {
                        return compat::kInstallerSetLabel;
                    }
                }
            } else {
                // Reload current durable fullUID policy and rebuild its copies under
                // the existing authorization transaction; never replay a saved list.
                err = compat::installer_refresh_authorization();
                if (failed(err)) return err;
            }

            if (need_install) {
                err = compat::installer_commit_version(installation);
                if (failed(err)) return err;
            }

            if (mode != DeploymentMode::Refresh) {
                if (need_install) {
                    err = compat::installer_prepare_bootstrap_state();
                    if (failed(err)) return err;
                } else {
                    err = compat::installer_reset_duplicate();
                    if (failed(err)) return err;
                    err = compat::installer_reset_safe();
                    if (failed(err)) return err;
                }
            }

            // Do not inherit the installation mutation lock into a detached helper.
            installation.close();
            ::sync();

            if (mode == DeploymentMode::Refresh) return compat::kInstallerOk;

            const std::string bootstrap_path = get_autorun_bootstrap_path();
            if (bootstrap_path.empty()) return compat::kInstallerIdentity;
            int result_pipe[2] = {-1, -1};
            const int mode_value = static_cast<int>(mode);
            const auto pipe_error = maplesu_installer_result_open(mode_value, result_pipe);
            if (pipe_error != 0) return static_cast<core::err_t>(pipe_error);
            const pid_t detached_pid = ::fork();
            if (detached_pid < 0) {
                (void)maplesu_installer_launch_error(1, bootstrap_path.c_str());
                return static_cast<core::err_t>(maplesu_installer_result_parent(detached_pid, mode_value, result_pipe));
            }
            if (detached_pid == 0) {
                maplesu_installer_result_worker(result_pipe);
                pipe.close();
                if (mode == DeploymentMode::Boot) {
                    (void)::sleep(3);
                    ::_exit(maplesu_installer_reboot(bootstrap_path.c_str()));
                } else if (mode == DeploymentMode::HotLoad) {
                    const pid_t exec_pid = ::fork();
                    if (exec_pid == 0) {
                        exec_bootstrap_command(bootstrap_path.c_str());  // 不返回
                    }
                    if (exec_pid < 0) {
                        (void)maplesu_installer_launch_error(2, bootstrap_path.c_str());
                        (void)maplesu_installer_result_complete(result_pipe, bootstrap_path.c_str(), 0);
                        ::_exit(127);
                    }
                    int exec_status = 0;
                    const bool launched = maplesu_installer_wait_child(exec_pid, &exec_status, bootstrap_path.c_str()) == exec_pid &&
                        exec_status == 0;
                    if (!maplesu_installer_result_complete(result_pipe, bootstrap_path.c_str(), launched)) {
                        ::_exit(127);
                    }
                }
                ::_exit(0);
            }

            const core::err_t launch_err = static_cast<core::err_t>(
                maplesu_installer_result_parent(detached_pid, mode_value, result_pipe));
            if (failed(launch_err)) return launch_err;
            if (mode == DeploymentMode::HotLoad &&
                (config.exploit == ExploitPolicy::None || config.exploit == ExploitPolicy::Magica)) {
                return compat::installer_restart_zygote();
            }
            return compat::kInstallerOk;
        };
        try {
            err = select_installer_worker_identity(root_key);
            if (succeeded(err)) {
                worker.emplace();
                err = perform();
            }
        } catch (const std::bad_alloc&) {
            errno = ENOMEM;
            err = static_cast<core::err_t>(KModErr::ERR_NO_MEM);
        } catch (const std::length_error&) {
            errno = ENOMEM;
            err = static_cast<core::err_t>(KModErr::ERR_NO_MEM);
        }
        finish_installation_child(pipe, installation, worker, err);
    }

    return collect_installation_result(pipe);
}

core::err_t uninstall_environment(const char* root_key) {
    if (!installer_identity_valid(root_key)) return compat::kInstallerIdentity;

    compat::InstallerChannel pipe;
    core::err_t err = compat::kInstallerOk;
    if (pipe.start()) {
        state::Mutation installation;
        std::optional<storage::WorkerScope> worker;
        const auto perform = [&]() -> core::err_t {
            err = static_cast<core::err_t>(worker->root());
            if (succeeded(err)) {
                if (get_kernel_file_dir_path().empty()) return compat::kInstallerIdentity;
                auto observed = installation.open();
                state::diagnose("open-uninstall", observed);
                if (state::mutation_open_failed(observed)) return compat::kInstallerWriteFile;
                if (installation.ready()) {
                    const bool required = installation.revocation_required();
                    observed = installation.revoke();
                    state::diagnose("revoke-uninstall", observed);
                    if (required && observed.code) return compat::kInstallerWriteFile;
                }
                compat::InstallerModules modules;
                err = modules.load();
                if (failed(err)) return err;
                for (std::size_t index = 0; index < modules.size(); ++index) {
                    err = modules.remove(index);
                    if (failed(err)) return err;
                }

                err = compat::installer_clean_older();
                if (succeeded(err)) {
                    const std::string bootstrap_path = get_autorun_bootstrap_path();
                    if (bootstrap_path.empty()) return compat::kInstallerIdentity;
                    const std::filesystem::path bootstrap_file(bootstrap_path.c_str());
                    std::error_code ec;
                    (void)std::filesystem::remove_all(bootstrap_file, ec);
                    const bool remove_failed = static_cast<bool>(ec);
                    const bool remains = std::filesystem::exists(bootstrap_file, ec);
                    if (remove_failed || ec || remains) {
                        err = compat::kInstallerDeleteFile;  // -19931：删除后仍存在
                    } else {
                        err = compat::installer_delete_directories();
                        if (succeeded(err)) {
                            installation.close();
                            ::sync();
                            const pid_t detached_pid = ::fork();
                            if (detached_pid < 0) {
                                return static_cast<core::err_t>(
                                    maplesu_installer_launch_error(1, bootstrap_path.c_str()));
                            }
                            if (detached_pid == 0) {
                                pipe.close();
                                (void)::sleep(3);
                                ::_exit(maplesu_installer_reboot(bootstrap_path.c_str()));
                            }
                            err = compat::kInstallerOk;
                        }
                    }
                }
            }
            return err;
        };
        try {
            err = select_installer_worker_identity(root_key);
            if (succeeded(err)) {
                worker.emplace();
                err = perform();
            }
        } catch (const std::bad_alloc&) {
            errno = ENOMEM;
            err = static_cast<core::err_t>(KModErr::ERR_NO_MEM);
        } catch (const std::length_error&) {
            errno = ENOMEM;
            err = static_cast<core::err_t>(KModErr::ERR_NO_MEM);
        }
        finish_installation_child(pipe, installation, worker, err);
    }

    return collect_installation_result(pipe);
}

core::err_t installed_version(const char* root_key,
                                                Version& out_ver) {
    if (!kernel_module::maplesu_request_owner_mode()) {
        if (root_key == nullptr) return compat::kInstallerIdentity;
        const std::size_t root_key_len = ::strnlen(root_key, kRootKeyBufferSize);
        if (root_key_len < core::kRootKeyDirChars || root_key_len >= kRootKeyBufferSize) {
            return compat::kInstallerIdentity;
        }
        compat::select_installer_identity(root_key);
    }

    std::string version_text;
    compat::InstallerChannel pipe;
    core::err_t err = compat::kInstallerOk;
    if (pipe.start()) {
        err = unsafe_installed_version(version_text);
        (void)pipe.write_error(err);
        (void)pipe.write_text(version_text);
        pipe.close();
        ::_exit(0);
    }

    if (pipe.child_pid() <= 0) return compat::kInstallerReadError;
    if (!pipe.read_error(err)) err = compat::kInstallerReadError;
    const bool text_read = pipe.read_text(version_text);
    if (succeeded(err) && !text_read) err = compat::kInstallerReadText;
    int child_status = 0;
    const pid_t waited = maplesu_wait_environment_child(pipe.child_pid(), &child_status);
    if (succeeded(err) && (waited != pipe.child_pid() ||
                           !WIFEXITED(child_status) || WEXITSTATUS(child_status) != 0)) {
        err = static_cast<core::err_t>(-21002);
    }
    if (failed(err)) return err;
    Version parsed;
    if (!parse_installed_environment_version(version_text, parsed))
        return compat::kInstallerParseVersion;
    out_ver = parsed;
    return err;
}





}  // namespace maplesu::install
