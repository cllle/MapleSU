// The canonical image stays inside a root-only directory. Authorized native
// sessions execute a held descriptor after applying their selected Profile.
#include "module_err_def.h"
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <string>
#include <sys/stat.h>
#include <sys/xattr.h>
#include <unistd.h>

namespace {
struct Fd {
    int value;
    ~Fd() { if (value >= 0) close(value); }
};
constexpr char system_context[] = "u:object_r:system_file:s0";

bool has_context(int fd) {
    char label[128]{};
    const auto size = fgetxattr(fd, "security.selinux", label, sizeof(label));
    return (size == sizeof(system_context) || size == sizeof(system_context) - 1) &&
        memcmp(label, system_context, sizeof(system_context) - 1) == 0 &&
        (size != sizeof(system_context) || label[size - 1] == '\0');
}
KModErr prepare_inode(int fd, bool directory, bool normalize_mode = true) {
    struct stat st{};
    if (fstat(fd, &st) != 0 || st.st_uid != 0 ||
        (directory ? !S_ISDIR(st.st_mode) : !S_ISREG(st.st_mode) || st.st_nlink != 1 || st.st_size == 0))
        return KModErr::ERR_MODULE_OPEN_FILE;
    const mode_t mode = 0711;
    if (normalize_mode && (st.st_mode & 07777) != mode && fchmod(fd, mode) != 0)
        return KModErr::ERR_SET_FILE_SELINUX;
    if (!has_context(fd) && fsetxattr(fd, "security.selinux", system_context, sizeof(system_context), 0) != 0)
        return KModErr::ERR_SET_FILE_SELINUX;
    if (fstat(fd, &st) != 0 || (normalize_mode && (st.st_mode & 07777) != mode) || !has_context(fd))
        return KModErr::ERR_SET_FILE_SELINUX;
    return KModErr::OK;
}
}

extern "C" KModErr maplesu_prepare_su_exec_directory(const char* system_directory) {
    if (!system_directory || strnlen(system_directory, 26) != 24 ||
        memcmp(system_directory, "/data/", 6) || system_directory[23] != '/')
        return KModErr::ERR_MODULE_PARAM;
    for (size_t i = 6; i < 23; ++i) {
        const char c = system_directory[i];
        if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')))
            return KModErr::ERR_MODULE_PARAM;
    }
    const std::string name(system_directory + 6, 17);
    Fd data{open("/data", O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)};
    if (data.value < 0) return KModErr::ERR_MODULE_OPEN_FILE;
    Fd directory{openat(data.value, name.c_str(), O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)};
    if (directory.value < 0) return KModErr::ERR_MODULE_OPEN_FILE;
    auto error = prepare_inode(directory.value, true);
    if (is_failed(error)) return error;
    if (mkdirat(directory.value, "u-00000000", 0700) != 0 && errno != EEXIST)
        return KModErr::ERR_MODULE_OPEN_DIR;
    Fd private_directory{openat(directory.value, "u-00000000", O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)};
    struct stat st{};
    if (private_directory.value < 0 || fstat(private_directory.value, &st) != 0 ||
        st.st_uid != 0 || st.st_gid != 0 || (st.st_mode & 07777) != 0700)
        return KModErr::ERR_MODULE_OPEN_DIR;
    if (!has_context(private_directory.value) &&
        fsetxattr(private_directory.value, "security.selinux", system_context, sizeof(system_context), 0) != 0)
        return KModErr::ERR_SET_FILE_SELINUX;
    Fd legacy{openat(directory.value, "su", O_RDONLY | O_NONBLOCK | O_NOFOLLOW | O_CLOEXEC)};
    if (legacy.value >= 0) {
        // Normalize only after rename: another caller may already hold this
        // inode inside the private directory with its final execution mode.
        error = prepare_inode(legacy.value, false, false);
        if (is_failed(error)) return error;
        if (renameat(directory.value, "su", private_directory.value, "su") != 0) {
            const int saved = errno;
            if (saved != ENOENT) return KModErr::ERR_MODULE_WRITE_FILE;
            Fd published{openat(private_directory.value, "su", O_RDONLY | O_NONBLOCK | O_NOFOLLOW | O_CLOEXEC)};
            struct stat source{}, target{};
            if (published.value < 0 || fstat(legacy.value, &source) != 0 ||
                fstat(published.value, &target) != 0 || source.st_dev != target.st_dev ||
                source.st_ino != target.st_ino) {
                errno = saved;
                return KModErr::ERR_MODULE_WRITE_FILE;
            }
        }
        if (fsync(private_directory.value) != 0 || fsync(directory.value) != 0)
            return KModErr::ERR_MODULE_WRITE_FILE;
    } else if (errno != ENOENT) return KModErr::ERR_MODULE_OPEN_FILE;
    Fd helper{openat(private_directory.value, "su", O_RDONLY | O_NONBLOCK | O_NOFOLLOW | O_CLOEXEC)};
    if (helper.value < 0) return KModErr::ERR_MODULE_OPEN_FILE;
    error = prepare_inode(helper.value, false);
    if (is_failed(error)) return error;
    return fsync(helper.value) == 0 ? KModErr::OK : KModErr::ERR_MODULE_WRITE_FILE;
}

extern "C" KModErr maplesu_prepare_su_exec(const char* key) {
    if (!key || strnlen(key, 49) != 48) return KModErr::ERR_MODULE_PARAM;
    for (size_t i = 0; i < 48; ++i) {
        const char c = key[i];
        if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')))
            return KModErr::ERR_MODULE_PARAM;
    }
    const std::string directory = "/data/" + std::string(key, 16) + key[0] + "/";
    return maplesu_prepare_su_exec_directory(directory.c_str());
}
