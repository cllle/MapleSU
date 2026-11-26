#include <cassert>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <map>
#include <sys/ioctl.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

static long fixture_syscall(long number, ...);
static int fixture_ioctl(int fd, unsigned long command, ...);
static int fixture_close(int fd);
static uid_t fixture_getuid();
static uid_t fixture_geteuid();
#define syscall fixture_syscall
#define ioctl fixture_ioctl
#define close fixture_close
#define getuid fixture_getuid
#define geteuid fixture_geteuid
#include "maplesu_module_environment_private.h"
#undef syscall
#undef ioctl
#undef close
#undef getuid
#undef geteuid
#include "module_task_environment.h"

static std::string directory;
static int mode, calls, ioctls, closes, checks;
#define CHECK(value) do { ++checks; if (!(value)) { \
    std::fprintf(stderr, "FAIL line %d: %s\n", __LINE__, #value); std::exit(1); } } while (0)
static uid_t fixture_getuid() { return mode == 8 ? 10000 : 0; }
static uid_t fixture_geteuid() { return mode == 9 ? 10000 : 0; }
static long fixture_syscall(long number, ...) {
    ++calls;
    CHECK(number == SYS_reboot);
    va_list args; va_start(args, number);
    CHECK(va_arg(args, unsigned int) == 0xdeadbeefU);
    CHECK(va_arg(args, unsigned int) == 0xcafebabeU);
    CHECK(va_arg(args, int) == 0);
    int* output = va_arg(args, int*);
    va_end(args);
    CHECK(*output == -1);
    if (mode != 1) *output = 179;
    errno = EINVAL;
    return -1;
}
static int fixture_ioctl(int fd, unsigned long command, ...) {
    ++ioctls;
    CHECK(fd == 179 && command == 0x80104b02U);
    va_list args; va_start(args, command);
    auto* output = va_arg(args, maplesu::module_environment::KsuCompatInfo*);
    va_end(args);
    if (mode == 2) { errno = ENOTTY; return -1; }
    *output = {10940, 0, 0, 0};
    if (mode == 3) ++output->version;
    if (mode == 4) output->flags = 1;
    if (mode == 5) output->features = 1;
    if (mode == 6) output->uapi_version = 6;
    return 0;
}
static int fixture_close(int fd) {
    if (fd != 179) return ::close(fd);
    ++closes;
    if (mode == 7) { errno = EIO; return -1; }
    return 0;
}
namespace maplesu::storage {
KModErr current_kernel_directory(std::string& out) { out = directory; return KModErr::OK; }
}
static std::vector<std::string> capture(bool hotload = false) {
    const std::string path = directory + "/install.mode";
    const int fd = open(path.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0600);
    CHECK(fd >= 0);
    const char* value = hotload ? "hotload" : "boot";
    CHECK(write(fd, value, std::strlen(value)) == static_cast<ssize_t>(std::strlen(value)));
    CHECK(!::close(fd));
    maplesu::module_environment::Environment environment;
    CHECK(!is_failed(maplesu::module_environment::capture(5, 2, 1, nullptr, environment)));
    std::vector<std::string> facts;
    for (char* const* item = environment.data(); *item; ++item) facts.emplace_back(*item);
    return facts;
}
static std::map<std::string, std::string> names(const std::vector<std::string>& facts) {
    std::map<std::string, std::string> out;
    for (const auto& fact : facts) CHECK(out.emplace(fact.substr(0, fact.find('=')), fact.substr(fact.find('=') + 1)).second);
    return out;
}
static void replace(std::vector<std::string>& facts, const char* key, const char* value) {
    const std::string prefix = std::string(key) + "=";
    for (auto& fact : facts) if (fact.rfind(prefix, 0) == 0) { fact = prefix + value; return; }
    CHECK(false);
}
int main() {
    CHECK(::getuid() == 0);
    directory = "/tmp/ksu-module-env-" + std::to_string(getpid());
    CHECK(!mkdir(directory.c_str(), 0700));
    maplesu::module_task::Environment consumer;
    for (mode = 0; mode < 10; ++mode) {
        calls = ioctls = closes = 0;
        errno = E2BIG;
        const auto observed = maplesu::module_environment::probe_ksu_compat();
        CHECK(errno == E2BIG && observed.version == (mode == 0 ? 10940U : 0U) && !observed.uapi_version);
        CHECK(calls == (mode >= 8 ? 0 : 1));
        CHECK(ioctls == (mode == 1 || mode >= 8 ? 0 : 1) && closes == ioctls);
        const auto facts = capture();
        const auto values = names(facts);
        CHECK(values.at("KSU_KERNEL_VER_CODE") == (mode == 0 ? "10940" : "0"));
        CHECK(values.at("KSU_UAPI_VER") == "0" && values.at("KSU_VER_CODE") == "50201");
        CHECK(consumer.build(nullptr, facts) == 0);
    }
    mode = 0;
    auto facts = capture(true);
    CHECK(consumer.build(nullptr, facts) == 0);
    const auto values = names(facts);
    CHECK(values.at("KSU_LATE_LOAD") == "1" && values.at("KSU_RUNTIME_MODE") == "late-load");
    facts.emplace_back("KSU_FACT_SOURCE=hook");
    CHECK(consumer.build(nullptr, facts) == -EINVAL);
    replace(facts, "KSU_KERNEL_VER_CODE", "0");
    CHECK(consumer.build(nullptr, facts) == 0);
    facts = capture();
    replace(facts, "KSU_UAPI_VER", "6");
    CHECK(consumer.build(nullptr, facts) == -EINVAL);
    facts = capture(); replace(facts, "KSU_KERNEL_VER_CODE", "10941");
    CHECK(consumer.build(nullptr, facts) == -EINVAL);
    facts = {"KSU=true", "KSU_VER=MapleSU 5.2.1", "KSU_VER_CODE=50201", "KSU_KERNEL_VER_CODE=0",
        "KSU_UAPI_VER=0", "MAPLESU_RUNTIME_MODE=unknown", "MAPLESU_KSU_COMPAT=1", "KSU_FACT_SOURCE=legacy"};
    CHECK(consumer.build(nullptr, facts) == 0);
    replace(facts, "KSU_KERNEL_VER_CODE", "10940");
    CHECK(consumer.build(nullptr, facts) == -EINVAL);
    facts = capture(); CHECK(consumer.build(nullptr, facts) == 0);
    const pid_t child = fork(); CHECK(child >= 0);
    if (!child) {
        char* args[] = {const_cast<char*>("/bin/sh"), const_cast<char*>("-c"),
            const_cast<char*>("test \"$KSU_VER_CODE:$KSU_KERNEL_VER_CODE:$KSU_UAPI_VER:$KSU_RUNTIME_MODE\" = 50201:10940:0:lkm"), nullptr};
        execve(args[0], args, consumer.data()); _exit(99);
    }
    int status;
    CHECK(waitpid(child, &status, 0) == child && WIFEXITED(status) && !WEXITSTATUS(status));
    CHECK(!unlink((directory + "/install.mode").c_str()) && !rmdir(directory.c_str()));
    std::printf("KSU_MODULE_ENV_PASS checks=%d live GET_INFO boundary, fallback, errno/fd, protected capture, source parser and real child exec\n", checks);
}
