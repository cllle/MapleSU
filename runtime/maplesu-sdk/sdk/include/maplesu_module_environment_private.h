#pragma once

#include "maplesu_storage_private.h"
#include <cerrno>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <string>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <vector>

extern char** environ;

namespace maplesu::module_environment {

struct KsuCompatInfo { uint32_t version = 0, flags = 0, features = 0, uapi_version = 0; };
// Probe inside the authenticated Root worker. A valid descriptor, rather than
// the invalid magic reboot's return value, proves the live query interface.
inline KsuCompatInfo probe_ksu_compat() {
    const int saved_errno = errno;
    KsuCompatInfo result;
#if defined(SYS_reboot)
    if (getuid() == 0 && geteuid() == 0) {
        int fd = -1;
        (void)syscall(SYS_reboot, 0xdeadbeefU, 0xcafebabeU, 0, &fd);
        if (fd >= 0) {
            KsuCompatInfo observed;
            if (!ioctl(fd, 0x80104b02U, &observed) && observed.version == 10940U &&
                !observed.flags && !observed.features && !observed.uapi_version) result = observed;
            if (close(fd)) result = {};
        }
    }
#endif
    errno = saved_errno;
    return result;
}

class Environment final {
public:
    char* const* data() {
        pointers_.clear();
        for (auto& value : values_) pointers_.push_back(value.data());
        pointers_.push_back(nullptr);
        return pointers_.data();
    }
    // Only apply inside a real forked module worker. libc owns these copies,
    // including when a native module later calls setenv or creates threads.
    KModErr apply() const {
        if (values_.empty()) return static_cast<KModErr>(-EINVAL);
        std::vector<std::string> retired;
        if (environ) for (size_t i = 0; environ[i]; ++i) {
            const char* value = environ[i];
            if (!std::strncmp(value, "KSU=", 4) || !std::strncmp(value, "KSU_", 4) ||
                !std::strncmp(value, "MAPLESU_RUNTIME_MODE=", 21) ||
                !std::strncmp(value, "MAPLESU_KSU_COMPAT=", 19)) {
                const char* equals = std::strchr(value, '=');
                if (equals) retired.emplace_back(value, equals - value);
            }
        }
        for (const auto& name : retired)
            if (unsetenv(name.c_str())) return static_cast<KModErr>(-errno);
        for (size_t i = first_fact_; i < values_.size(); ++i) {
            const size_t equals = values_[i].find('=');
            if (setenv(values_[i].substr(0, equals).c_str(), values_[i].c_str() + equals + 1, 1))
                return static_cast<KModErr>(-errno);
        }
        return KModErr::OK;
    }
private:
    friend KModErr capture(uint32_t, uint32_t, uint32_t, char* const[], Environment&);
    std::vector<std::string> values_;
    std::vector<char*> pointers_;
    size_t first_fact_ = 0;
};

// Read while the caller's authenticated worker StorageScope is still current.
// install.mode is written by the actual SDK installation transaction.
inline KModErr capture(uint32_t major, uint32_t minor, uint32_t patch,
                      char* const inherited[], Environment& out) {
    out.values_.clear();
    out.pointers_.clear();
    out.first_fact_ = 0;
    std::string directory;
    RETURN_IF_ERROR(storage::current_kernel_directory(directory));
    while (directory.size() > 1 && directory.back() == '/') directory.pop_back();
    const int parent = open(directory.c_str(), O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (parent < 0) return static_cast<KModErr>(-errno);
    int error = 0;
    struct stat parent_info{}, before{}, after{}, linked{};
    if (fstat(parent, &parent_info)) error = -errno;
    else if (!S_ISDIR(parent_info.st_mode) || parent_info.st_uid || (parent_info.st_mode & 0022))
        error = -EACCES;
    const int fd = error ? -1 : openat(parent, "install.mode", O_RDONLY | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK);
    if (fd < 0 && !error) error = -errno;
    if (!error && fstat(fd, &before)) error = -errno;
    if (!error && (!S_ISREG(before.st_mode) || before.st_uid || before.st_nlink != 1 ||
        (before.st_mode & 0022) || (before.st_size != 4 && before.st_size != 7))) error = -EINVAL;
    char mode[8]{};
    size_t used = 0;
    while (!error && used < sizeof(mode)) {
        const ssize_t count = read(fd, mode + used, sizeof(mode) - used);
        if (count < 0) { if (errno != EINTR) error = -errno; }
        else if (!count) break;
        else used += static_cast<size_t>(count);
    }
    if (!error && fstat(fd, &after)) error = -errno;
    if (!error && fstatat(parent, "install.mode", &linked, AT_SYMLINK_NOFOLLOW)) error = -errno;
    if (!error && (before.st_dev != after.st_dev || before.st_ino != after.st_ino ||
        before.st_size != after.st_size || before.st_mode != after.st_mode ||
        before.st_uid != after.st_uid || after.st_nlink != 1 ||
        before.st_mtim.tv_sec != after.st_mtim.tv_sec || before.st_mtim.tv_nsec != after.st_mtim.tv_nsec ||
        before.st_ctim.tv_sec != after.st_ctim.tv_sec || before.st_ctim.tv_nsec != after.st_ctim.tv_nsec ||
        linked.st_dev != after.st_dev || linked.st_ino != after.st_ino ||
        used != static_cast<size_t>(after.st_size))) error = -ESTALE;
    if (fd >= 0 && close(fd) && !error) error = -errno;
    if (close(parent) && !error) error = -errno;
    if (error) return static_cast<KModErr>(error);
    const bool boot = used == 4 && !std::memcmp(mode, "boot", 4);
    const bool hotload = used == 7 && !std::memcmp(mode, "hotload", 7);
    if (!boot && !hotload) return static_cast<KModErr>(-EINVAL);

    std::vector<std::string> values;
    if (inherited) for (size_t i = 0; inherited[i]; ++i) {
        const char* value = inherited[i];
        if (!std::strncmp(value, "KSU=", 4) || !std::strncmp(value, "KSU_", 4) ||
            !std::strncmp(value, "MAPLESU_RUNTIME_MODE=", 21) ||
            !std::strncmp(value, "MAPLESU_KSU_COMPAT=", 19)) continue;
        values.emplace_back(value);
    }
    const std::string version = std::to_string(major) + "." + std::to_string(minor) + "." + std::to_string(patch);
    out.first_fact_ = values.size();
    values.emplace_back("KSU=true");
    // KSU_VER* describe this compatibility userspace, not a KernelSU driver.
    values.emplace_back("KSU_VER=MapleSU " + version);
    values.emplace_back("KSU_VER_CODE=" + std::to_string(uint64_t(major) * 10000 + uint64_t(minor) * 100 + patch));
    const auto kernel = probe_ksu_compat();
    values.emplace_back("KSU_KERNEL_VER_CODE=" + std::to_string(kernel.version));
    values.emplace_back("KSU_UAPI_VER=" + std::to_string(kernel.uapi_version));
    values.emplace_back(hotload ? "KSU_RUNTIME_MODE=late-load" : "KSU_RUNTIME_MODE=lkm");
    if (hotload) values.emplace_back("KSU_LATE_LOAD=1");
    values.emplace_back(hotload ? "MAPLESU_RUNTIME_MODE=hotload" : "MAPLESU_RUNTIME_MODE=boot");
    values.emplace_back("MAPLESU_KSU_COMPAT=1");
    out.values_.swap(values);
    out.pointers_.clear();
    return KModErr::OK;
}

} // namespace maplesu::module_environment
