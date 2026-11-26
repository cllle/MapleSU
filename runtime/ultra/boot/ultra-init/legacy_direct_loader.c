// SPDX-License-Identifier: GPL-3.0-or-later
#define _GNU_SOURCE
#include "legacy_direct_loader.h"
#include "legacy_direct_bind.h"
#include <errno.h>
#include <ctype.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/sysmacros.h>
#include <unistd.h>

static int write_value(const char *path, const char *value, size_t length)
{
    int fd = open(path, O_WRONLY | O_CLOEXEC);
    if (fd < 0) return -errno;
    int error = 0;
    for (size_t at = 0; at < length;) {
        ssize_t count = write(fd, value + at, length - at);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) { error = count < 0 ? -errno : -EIO; break; }
        at += (size_t)count;
    }
    if (close(fd) < 0 && !error) error = -errno;
    return error;
}

static int horizontal_space(char value)
{
    return value == ' ' || value == '\t';
}

static int parse_kallsyms_record(char *line, size_t length, uint64_t *address,
    char *type, char **name)
{
    if (!length || memchr(line, 0, length)) return -EINVAL;
    if (line[length - 1] == '\n') line[--length] = 0;
    char *at = line;
    uint64_t value = 0;
    size_t digits = 0;
    while (isxdigit((unsigned char)*at)) {
        unsigned int digit = *at >= '0' && *at <= '9' ? (unsigned int)(*at - '0') :
            (unsigned int)(tolower((unsigned char)*at) - 'a' + 10);
        if (++digits > 16) return -EINVAL;
        value = (value << 4) | digit;
        ++at;
    }
    if (!digits || !horizontal_space(*at)) return -EINVAL;
    while (horizontal_space(*at)) ++at;
    if (!*at || isspace((unsigned char)*at)) return -EINVAL;
    *type = *at++;
    if (!horizontal_space(*at)) return -EINVAL;
    while (horizontal_space(*at)) ++at;
    char *symbol = at;
    while (*at && !isspace((unsigned char)*at)) ++at;
    if (at == symbol) return -EINVAL;
    if (*at) {
        if (!horizontal_space(*at)) return -EINVAL;
        *at++ = 0;
        while (horizontal_space(*at)) ++at;
        if (*at) {
            if (*at++ != '[') return -EINVAL;
            char *module = at;
            while (*at && *at != ']' && !isspace((unsigned char)*at)) ++at;
            if (at == module || *at++ != ']') return -EINVAL;
            while (horizontal_space(*at)) ++at;
            if (*at) return -EINVAL;
        }
    }
    *address = value;
    *name = symbol;
    return 0;
}

static int resolve_boot_symbols(void *context, const char *const *names, uint64_t *addresses, size_t count)
{
    (void)context;
    if (!count || count > MSU_DIRECT_LIMIT) return -EINVAL;
    unsigned int seen[MSU_DIRECT_LIMIT] = {0};
    int created = mkdir("/proc", 0755) == 0;
    if (!created && errno != EEXIST) return -errno;
    if (mount("proc", "/proc", "proc", MS_NOSUID | MS_NODEV | MS_NOEXEC, NULL) < 0) {
        const int error = -errno;
        if (created) (void)rmdir("/proc");
        return error;
    }
    int error = 0, restore = 0;
    char saved[32] = {0};
    FILE *setting = fopen("/proc/sys/kernel/kptr_restrict", "re");
    if (!setting) error = -errno;
    else {
        if (!fgets(saved, sizeof(saved), setting) || fgetc(setting) != EOF || ferror(setting)) error = -EIO;
        if (fclose(setting) != 0 && !error) error = -errno;
        if (!error) {
            restore = 1;
            error = write_value("/proc/sys/kernel/kptr_restrict", "1", 1);
        }
    }
    if (!error) {
        FILE *symbols = fopen("/proc/kallsyms", "re");
        if (!symbols) error = -errno;
        else {
            char *line = NULL; size_t capacity = 0; ssize_t length;
            while ((length = getline(&line, &capacity, symbols)) >= 0) {
                const int module_record = memchr(line, '[', (size_t)length) != NULL;
                uint64_t address; char type; char *name;
                error = parse_kallsyms_record(line, (size_t)length, &address, &type, &name);
                if (error) break;
                if (module_record) continue;
                for (size_t index = 0; index < count; ++index) if (!strcmp(name, names[index])) {
                    if (seen[index]++) { error = -ESTALE; break; }
                    if (!address || (type != 't' && type != 'T' && type != 'w' && type != 'W')) {
                        error = -ENOENT; break;
                    }
                    addresses[index] = address;
                }
                if (error) break;
            }
            if (ferror(symbols) && !error) error = -EIO;
            free(line);
            if (fclose(symbols) != 0 && !error) error = -errno;
        }
    }
    if (restore) {
        const int restored = write_value("/proc/sys/kernel/kptr_restrict", saved, strlen(saved));
        if (restored && !error) error = restored;
    }
    if (umount2("/proc", MNT_DETACH) < 0 && !error) error = -errno;
    if (created && rmdir("/proc") < 0 && !error) error = -errno;
    if (!error) for (size_t index = 0; index < count; ++index)
        if (!addresses[index]) { error = -ENOENT; break; }
    return error;
}

static int open_component_log(void)
{
    const char *const paths[] = {"/dev/kmsg", "/kmsg"};
    for (size_t index = 0; index < sizeof(paths) / sizeof(paths[0]); ++index) {
        const int fd = open(paths[index], O_RDONLY | O_NONBLOCK | O_CLOEXEC | O_NOFOLLOW);
        if (fd < 0) continue;
        if (lseek(fd, 0, SEEK_END) >= 0) return fd;
        (void)close(fd);
    }
    /* Android first-stage init has not populated /dev yet. Keep our temporary
     * log node inside the owned ramdisk directory and unlink it immediately. */
    const char *path = "/.maplesu_ultra/kmsg";
    if (mknod(path, S_IFCHR | 0600, makedev(1, 11)) < 0) return -1;
    const int fd = open(path, O_RDONLY | O_NONBLOCK | O_CLOEXEC | O_NOFOLLOW);
    (void)unlink(path);
    if (fd >= 0 && lseek(fd, 0, SEEK_END) < 0) { (void)close(fd); return -1; }
    return fd;
}

static int component_log_magic(int fd, const char *current, char required[256])
{
    char record[16384];
    size_t total = 0;
    for (unsigned int index = 0; index < 32 && total < 65536; ++index) {
        const ssize_t count = read(fd, record, sizeof(record));
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) break;
        total += (size_t)count;
        const int found = msu_required_vermagic(record, (size_t)count, current, required, 256);
        if (found) return found;
    }
    return 0;
}

long msu_load_bridge_fd(int fd, const char *parameters)
{
    struct stat status;
    if (fstat(fd, &status) < 0) return -1;
    if (status.st_size <= 0 || (uintmax_t)status.st_size > SIZE_MAX) { errno = EINVAL; return -1; }
    size_t bytes = (size_t)status.st_size;
    if (bytes > 64U * 1024U * 1024U) { errno = EFBIG; return -1; }
    unsigned char *module = malloc(bytes);
    if (!module) return -1;
    int error = 0;
    for (size_t at = 0; at < bytes;) {
        ssize_t count = pread(fd, module + at, bytes - at, (off_t)at);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) { error = count < 0 ? errno : EIO; break; }
        at += (size_t)count;
    }
    long result = -1;
    if (!error) {
        const int bound = msu_bind_legacy_direct(module, bytes, resolve_boot_symbols, NULL);
        if (bound < 0) error = -bound;
        else {
            char current[256], required[256];
            const int component = msu_component_vermagic(module, bytes, current, sizeof(current));
            if (component < 0) error = -component;
            else {
                const int log_fd = component ? open_component_log() : -1;
                result = bound || component ? syscall(SYS_init_module, module, bytes, parameters) :
                    syscall(SYS_finit_module, fd, parameters, 0);
                if (result < 0) {
                    error = errno;
                    if (component && log_fd >= 0 &&
                        component_log_magic(log_fd, current, required) == 1 &&
                        !msu_replace_component_vermagic(&module, &bytes, current, required)) {
                        result = syscall(SYS_init_module, module, bytes, parameters);
                        error = result < 0 ? errno : 0;
                    }
                }
                if (log_fd >= 0) (void)close(log_fd);
            }
        }
    }
    free(module);
    if (error) errno = error;
    return result;
}
