// SPDX-License-Identifier: GPL-3.0-or-later
#define _GNU_SOURCE
#include "handoff.h"
#include "legacy_direct_loader.h"
#include "../../identity/kernel/manager_deployment_namespace.h"

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#ifdef MAPLESU_ULTRA_INIT_OS_FIXTURE
#include "tests/os_fixture.h"
#else
#include <sys/syscall.h>
#endif
#include <unistd.h>

static void report_failure(const char *operation, const char *path, int error) {
    char diagnostic[192];
    const int length = snprintf(diagnostic, sizeof(diagnostic),
        "MapleSU early init: %s %s failed errno=%d\n", operation, path, error);
    if (length > 0 && (size_t)length < sizeof(diagnostic)) {
        (void)write(STDERR_FILENO, diagnostic, (size_t)length);
    }
}

static void clear_parameters(char *parameters, size_t bytes) {
    volatile unsigned char *at = (volatile unsigned char *)parameters;
    while (bytes--) *at++ = 0;
}

static int read_parameters(char parameters[128]) {
    const char *path = "/.maplesu_ultra/credential";
    int fd = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) {
        const int error = errno;
        parameters[0] = 0;
        if (error == ENOENT) return 1; // Existing embedded-credential artifacts need no file.
        report_failure("open", path, error);
        return 0;
    }
    size_t bytes = 0;
    int error = 0;
    memcpy(parameters, "credential=", 11);
    while (bytes < 49) {
        ssize_t count = read(fd, parameters + 11 + bytes, 49 - bytes);
        if (count < 0) {
            if (errno == EINTR) continue;
            error = errno;
            break;
        }
        if (count == 0) break;
        bytes += (size_t)count;
    }
    if (!error && bytes != 48) error = EINVAL;
    for (size_t index = 11; !error && index < 59; ++index) {
        unsigned char value = (unsigned char)parameters[index];
        if (!((value >= '0' && value <= '9') || (value >= 'a' && value <= 'z') ||
              (value >= 'A' && value <= 'Z'))) error = EINVAL;
    }
    if (error) report_failure("read", path, error);
    const int closed = close(fd);
    const int close_error = closed < 0 ? errno : 0;
    if (close_error) report_failure("close", path, close_error);
    if (unlink(path) < 0) report_failure("unlink", path, errno);
    if (error || close_error) return 0;
    parameters[59] = 0;
    return 1;
}

static int read_deployment(char parameters[128]) {
    const char *path = "/.maplesu_ultra/deployment";
    int fd = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) {
        const int error = errno;
        if (error == ENOENT) return 1; // Old artifacts keep their config1 path.
        report_failure("open", path, error);
        return 0;
    }
    unsigned char encoded[sizeof(struct msu_deployment_namespace_record) + 1];
    struct msu_deployment_namespace_record record;
    size_t bytes = 0;
    int error = 0;
    while (bytes < sizeof(encoded)) {
        ssize_t count = read(fd, encoded + bytes, sizeof(encoded) - bytes);
        if (count < 0) {
            if (errno == EINTR) continue;
            error = errno;
            break;
        }
        if (count == 0) break;
        bytes += (size_t)count;
    }
    if (!error && bytes != sizeof(record)) error = EINVAL;
    if (!error) {
        memcpy(&record, encoded, sizeof(record));
        if (!msu_deployment_namespace_valid(&record)) error = EINVAL;
    }
    if (error) report_failure("read", path, error);
    const int closed = close(fd);
    const int close_error = closed < 0 ? errno : 0;
    if (close_error) report_failure("close", path, close_error);
    if (unlink(path) < 0) report_failure("unlink", path, errno);
    if (error || close_error) return 0;
    const size_t used = strlen(parameters);
    const int count = snprintf(parameters + used, 128 - used, "%sdeployment_namespace=%.*s",
        used ? " " : "", (int)sizeof(record.name), (const char *)record.name);
    if (count < 0 || (size_t)count >= 128 - used) {
        report_failure("parameters", path, EINVAL);
        return 0;
    }
    return 1;
}

static void load_bridge_once(void) {
    const char *path = "/.maplesu_ultra/bridge.ko";
    char parameters[128] = {0};
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        report_failure("open", path, errno);
        (void)read_parameters(parameters);
        (void)read_deployment(parameters);
        clear_parameters(parameters, sizeof(parameters));
        return;
    }
    const int valid_parameters = read_parameters(parameters);
    const int valid_deployment = read_deployment(parameters);
    // Ordinary imports retain normal loading. Explicit legacy direct calls use
    // pinned-KSU SHN_ABS binding; signature/version failures are never retried.
    const long loaded = valid_parameters && valid_deployment ? msu_load_bridge_fd(fd, parameters) : 0;
    const int load_error = loaded < 0 ? errno : 0;
    clear_parameters(parameters, sizeof(parameters));
    const int closed = close(fd);
    const int close_error = closed < 0 ? errno : 0;
    if (load_error) report_failure("load_module", path, load_error);
    if (close_error) report_failure("close", path, close_error);
}

int maplesu_ultra_first_stage(char *const argv[], char *const envp[]) {
    if (getpid() != 1) return 64;

    // Keeping both paths in / preserves a relative original-init symlink's parent.
    // Atomic replacement avoids an unlink window; a failed rename leaves its source.
    const char *original = "/init.maplesu.orig";
    const int restored = rename(original, "/init");
    const int restore_error = restored < 0 ? errno : 0;
    const char *target = restored == 0 ? "/init" : original;
    if (restore_error) report_failure("rename", original, restore_error);

    load_bridge_once();
    // Both load outcomes hand off in the same PID with the untouched kernel vectors.
    // On rename failure /init may still be this shim, so only execute the original.
    do {
        execve(target, argv, envp);
    } while (errno == EINTR);
    const int error = errno;
    report_failure("execve", target, error);
    return 126;
}
