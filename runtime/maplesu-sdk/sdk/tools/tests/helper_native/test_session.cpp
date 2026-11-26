#include "private/helper_core.h"
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <iterator>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

static int checks;
static bool fail_work;
static void check(bool ok, const char* name) {
    ++checks;
    if (!ok) { std::cerr << "FAIL " << name << '\n'; std::exit(1); }
}
static std::string read_file(const std::string& path) {
    std::ifstream input(path, std::ios::binary);
    return {std::istreambuf_iterator<char>(input), {}};
}
static int open_path(const char* path, helper_u32 flags, helper_u32 mode) {
    if (fail_work && std::string(path).ends_with(std::string(HELPER_SUFFIX_WORK) + ".pending") &&
        (flags & HELPER_OPEN_WRITE))
        return HELPER_E_NOSPC;
    int native = (flags & HELPER_OPEN_WRITE) ? O_WRONLY : O_RDONLY;
    if (flags & HELPER_OPEN_CREATE) native |= O_CREAT;
    if (flags & HELPER_OPEN_DIRECTORY) native |= O_DIRECTORY;
    if (flags & HELPER_OPEN_EXCL) native |= O_EXCL;
    if (flags & HELPER_OPEN_NOFOLLOW) native |= O_NOFOLLOW;
    if (flags & HELPER_OPEN_NONBLOCK) native |= O_NONBLOCK;
    const int fd = open(path, native | O_CLOEXEC, mode);
    return fd < 0 ? -errno : fd;
}
static int stat_file(int fd, helper_stat_info* out) {
    struct stat info{};
    if (fstat(fd, &info)) return -errno;
    out->size = info.st_size; out->uid = info.st_uid; out->nlink = info.st_nlink;
    out->is_reg = S_ISREG(info.st_mode);
    out->is_dir = S_ISDIR(info.st_mode);
    return HELPER_OK;
}
static long long read_bytes(int fd, void* out, helper_u32 size) {
    const auto result = read(fd, out, size); return result < 0 ? -errno : result;
}
static long long write_bytes(int fd, const void* in, helper_u32 size) {
    const auto result = write(fd, in, size); return result < 0 ? -errno : result;
}
static int close_file(int fd) { return close(fd) ? -errno : HELPER_OK; }
static int sync_file(int fd) { return fsync(fd) ? -errno : HELPER_OK; }
static int rename_file(int olddir, const char* oldname, int newdir, const char* newname) {
    return renameat(olddir, oldname, newdir, newname) ? -errno : HELPER_OK;
}
static int unlink_file(int dir, const char* name, helper_u32 flags) {
    return unlinkat(dir, name, (flags & HELPER_AT_REMOVEDIR) ? AT_REMOVEDIR : 0) ? -errno : HELPER_OK;
}

int main() {
    char directory[] = "/tmp/maplesu-session-XXXXXX";
    check(mkdtemp(directory) != nullptr, "disposable session directory");
    helper_context context{};
    const std::string self = std::string(directory) + "/init";
    strcpy(context.self_path, self.c_str());
    const auto boot = read_file("/proc/sys/kernel/random/boot_id");
    check(boot.size() == HELPER_RECEIPT_LEN, "actual kernel boot identity");
    memcpy(context.boot_id, boot.data(), HELPER_BOOT_ID_LEN);
    context.ops.open_path = open_path; context.ops.fstat = stat_file;
    context.ops.read = read_bytes; context.ops.write = write_bytes;
    context.ops.close = close_file; context.ops.fsync = sync_file;
    context.ops.renameat = rename_file; context.ops.unlinkat = unlink_file;
    const auto flag = self + HELPER_SUFFIX_SAFE_FLAG;
    const auto work = self + HELPER_SUFFIX_WORK;
    const auto completed = self + HELPER_SUFFIX_COMPLETED;
    const auto protection = self + HELPER_SUFFIX_FAIL_PROTECT;
    int action = -1;
    check(helper_session_begin(&context, &action) == HELPER_OK && action == HELPER_SESSION_NORMAL,
          "first invocation enters initialization");
    check(read_file(flag) == "1", "initialization is recorded before running stages");
    check(helper_session_fail(&context, HELPER_E_IO) == HELPER_EXIT_PROTECTED, "failure propagates");
    check(read_file(flag) == "3" && !std::filesystem::exists(work) && !std::filesystem::exists(completed),
          "failure publishes no success receipt");
    std::ofstream(protection).close();
    check(helper_session_begin(&context, &action) == HELPER_OK && action == HELPER_SESSION_PROTECTED,
          "configured failure protection prevents automatic retry");
    std::filesystem::remove(protection);
    check(helper_session_begin(&context, &action) == HELPER_OK && action == HELPER_SESSION_RETRY,
          "retry is available when failure protection is disabled");
    fail_work = true;
    check(helper_session_finish(&context) == HELPER_E_NOSPC, "final publication failure propagates");
    check(read_file(flag) == "2" && read_file(completed) == boot && !std::filesystem::exists(work),
          "partial commit is distinct from a published running session");
    helper_session_fail(&context, HELPER_E_NOSPC);
    fail_work = false;
    check(helper_session_begin(&context, &action) == HELPER_OK && action == HELPER_SESSION_RETRY,
          "completion receipt alone does not suppress retry");
    check(helper_session_finish(&context) == HELPER_OK, "retry commits successfully");
    check(read_file(flag) == "2" && read_file(work) == boot && read_file(completed) == boot,
          "all success evidence belongs to the current boot");
    check(helper_session_begin(&context, &action) == HELPER_OK && action == HELPER_SESSION_COMPLETED,
          "published session is idempotent");
    context.boot_id[0] = context.boot_id[0] == '0' ? '1' : '0';
    check(helper_session_begin(&context, &action) == HELPER_OK && action == HELPER_SESSION_NORMAL,
          "previous boot receipt does not suppress initialization");
    check(read_file(flag) == "1", "new boot enters initialization");
    std::filesystem::remove_all(directory);
    std::cout << "PASS helper durable session " << checks << " assertions\n";
}
