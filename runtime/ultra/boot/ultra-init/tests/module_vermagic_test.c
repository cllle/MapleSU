// SPDX-License-Identifier: GPL-3.0-or-later
#define _GNU_SOURCE
#include "legacy_direct_bind.h"
#include "legacy_direct_loader.h"
#include <assert.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/sysmacros.h>
#include <unistd.h>

/* Actual parser/rewriter/loader are linked. Only OS boundaries are wrapped;
 * these structurally valid ELF fixtures are not loadable kernel modules. */
static const char old_magic[] = "6.6.127-4k-g46a034eca005-dirty SMP preempt mod_unload modversions aarch64";
static const char new_magic[] = "6.6.94-android15-9-device-longer SMP preempt mod_unload modversions aarch64";
static const char parameters[] = "credential=012345 deployment_namespace=ABCD2345EFGH6789";
static const char signature[] = "~Module signature appended~\n";
static unsigned char image[4096];
static size_t image_bytes, modinfo_header, modinfo_at;
static int init_calls, finit_calls, open_calls, seek_calls, close_calls, read_calls, pread_calls;
static int syscall_errors[3], stat_error, pread_error, pread_interrupt, pread_eof;
static size_t pread_piece;
static off_t stat_bytes;
static int log_missing, seek_error, log_read_error, log_interrupt, log_close_error, cursor_fresh;
static const char *log_records[40];
static size_t log_count, log_at;
static int allocation_calls, fail_allocation_at;
static char message[1024];

void *__real_malloc(size_t bytes);
void *__wrap_malloc(size_t bytes)
{
    if (++allocation_calls == fail_allocation_at) { errno = ENOMEM; return NULL; }
    return __real_malloc(bytes);
}

static void make_image(const char *extra, int marked, int signed_module)
{
    static const char names[] = "\0.shstrtab\0.modinfo\0";
    memset(image, 0, sizeof(image));
    Elf64_Ehdr header = {0};
    memcpy(header.e_ident, ELFMAG, SELFMAG);
    header.e_ident[EI_CLASS] = ELFCLASS64; header.e_ident[EI_DATA] = ELFDATA2LSB;
    header.e_ident[EI_VERSION] = EV_CURRENT; header.e_type = ET_REL;
    header.e_machine = EM_AARCH64; header.e_version = EV_CURRENT;
    header.e_ehsize = sizeof(header); header.e_shentsize = sizeof(Elf64_Shdr);
    header.e_shnum = 3; header.e_shstrndx = 1; header.e_shoff = sizeof(header);
    Elf64_Shdr sections[3] = {0};
    size_t at = sizeof(header) + sizeof(sections);
    sections[1] = (Elf64_Shdr){.sh_name=1, .sh_type=SHT_STRTAB, .sh_offset=at, .sh_size=sizeof(names)};
    memcpy(image + at, names, sizeof(names)); at += sizeof(names);
    modinfo_at = at;
    if (marked) {
        static const char marker[] = "maplesu_runtime_facts=1";
        memcpy(image + at, marker, sizeof(marker)); at += sizeof(marker);
    }
    const int count = snprintf((char *)image + at, sizeof(image) - at, "vermagic=%s", old_magic);
    assert(count > 0); at += (size_t)count + 1;
    if (extra) {
        const size_t bytes = strlen(extra) + 1;
        memcpy(image + at, extra, bytes); at += bytes;
    }
    sections[2] = (Elf64_Shdr){.sh_name=11, .sh_type=SHT_PROGBITS, .sh_flags=SHF_ALLOC,
        .sh_offset=modinfo_at, .sh_size=at-modinfo_at, .sh_addralign=8};
    memcpy(image, &header, sizeof(header));
    memcpy(image + header.e_shoff, sections, sizeof(sections));
    modinfo_header = header.e_shoff + 2 * sizeof(Elf64_Shdr);
    if (signed_module) { memcpy(image + at, signature, sizeof(signature)-1); at += sizeof(signature)-1; }
    image_bytes = at;
}

static void make_signed_direct_image(void)
{
    static const char labels[] = "\0.shstrtab\0.strtab\0.symtab\0.text\0.rela.text\0.msu_direct_calls\0.modinfo\0";
    static const char names[] = "\0file_open_root\0getname_kernel\0groups_alloc\0putname\0";
    static const char marker[] = "MAPLESU_DIRECT_V1\0file_open_root\0getname_kernel\0groups_alloc\0putname\0";
    memset(image, 0, sizeof(image));
    Elf64_Ehdr header = {0};
    memcpy(header.e_ident, ELFMAG, SELFMAG);
    header.e_ident[EI_CLASS] = ELFCLASS64; header.e_ident[EI_DATA] = ELFDATA2LSB;
    header.e_ident[EI_VERSION] = EV_CURRENT; header.e_type = ET_REL;
    header.e_machine = EM_AARCH64; header.e_version = EV_CURRENT;
    header.e_ehsize = sizeof(header); header.e_shentsize = sizeof(Elf64_Shdr);
    header.e_shnum = 8; header.e_shstrndx = 1; header.e_shoff = sizeof(header);
    Elf64_Shdr sections[8] = {0};
    size_t at = sizeof(header) + sizeof(sections);
    sections[1] = (Elf64_Shdr){.sh_name=1, .sh_type=SHT_STRTAB, .sh_offset=at, .sh_size=sizeof(labels)};
    memcpy(image+at, labels, sizeof(labels)); at += sizeof(labels);
    sections[2] = (Elf64_Shdr){.sh_name=11, .sh_type=SHT_STRTAB, .sh_offset=at, .sh_size=sizeof(names)};
    memcpy(image+at, names, sizeof(names)); at += sizeof(names); at = (at+7) & ~(size_t)7;
    sections[3] = (Elf64_Shdr){.sh_name=19, .sh_type=SHT_SYMTAB, .sh_offset=at,
        .sh_size=5*sizeof(Elf64_Sym), .sh_link=2, .sh_info=1, .sh_entsize=sizeof(Elf64_Sym)};
    const unsigned int positions[] = {1, 16, 31, 44};
    for (size_t index = 0; index < 4; ++index) {
        const Elf64_Sym symbol = {.st_name=positions[index], .st_info=ELF64_ST_INFO(STB_GLOBAL, STT_NOTYPE)};
        memcpy(image+at+(index+1)*sizeof(symbol), &symbol, sizeof(symbol));
    }
    at += sections[3].sh_size;
    sections[4] = (Elf64_Shdr){.sh_name=27, .sh_type=SHT_PROGBITS, .sh_flags=SHF_ALLOC|SHF_EXECINSTR,
        .sh_offset=at, .sh_size=16};
    for (size_t index = 0; index < 4; ++index) {
        const uint32_t instruction = 0x94000000U;
        memcpy(image+at+index*4, &instruction, sizeof(instruction));
    }
    at += 16; at = (at+7) & ~(size_t)7;
    sections[5] = (Elf64_Shdr){.sh_name=33, .sh_type=SHT_RELA, .sh_offset=at,
        .sh_size=4*sizeof(Elf64_Rela), .sh_link=3, .sh_info=4, .sh_entsize=sizeof(Elf64_Rela)};
    for (size_t index = 0; index < 4; ++index) {
        const Elf64_Rela relocation = {.r_offset=index*4, .r_info=ELF64_R_INFO(index+1, 283)};
        memcpy(image+at+index*sizeof(relocation), &relocation, sizeof(relocation));
    }
    at += sections[5].sh_size;
    sections[6] = (Elf64_Shdr){.sh_name=44, .sh_type=SHT_PROGBITS, .sh_offset=at, .sh_size=sizeof(marker)};
    memcpy(image+at, marker, sizeof(marker)); at += sizeof(marker); modinfo_at = at;
    static const char facts[] = "maplesu_runtime_facts=1";
    memcpy(image+at, facts, sizeof(facts)); at += sizeof(facts);
    const int count = snprintf((char *)image+at, sizeof(image)-at, "vermagic=%s", old_magic);
    assert(count > 0); at += (size_t)count+1;
    sections[7] = (Elf64_Shdr){.sh_name=62, .sh_type=SHT_PROGBITS, .sh_flags=SHF_ALLOC,
        .sh_offset=modinfo_at, .sh_size=at-modinfo_at, .sh_addralign=8};
    memcpy(image, &header, sizeof(header)); memcpy(image+header.e_shoff, sections, sizeof(sections));
    modinfo_header = header.e_shoff+7*sizeof(Elf64_Shdr);
    memcpy(image+at, signature, sizeof(signature)-1); image_bytes = at+sizeof(signature)-1;
}

static void reset(void)
{
    init_calls = finit_calls = open_calls = seek_calls = close_calls = read_calls = pread_calls = 0;
    memset(syscall_errors, 0, sizeof(syscall_errors));
    stat_error = pread_error = pread_interrupt = pread_eof = 0;
    pread_piece = sizeof(image); stat_bytes = -1;
    log_missing = seek_error = log_read_error = log_interrupt = log_close_error = cursor_fresh = 0;
    memset(log_records, 0, sizeof(log_records)); log_count = log_at = 0;
    allocation_calls = fail_allocation_at = 0;
    make_image(NULL, 1, 0);
    const int bytes = snprintf(message, sizeof(message),
        "6,123,456,-;maplesu_ultra_bridge: version magic '%s' should be '%s'\n", old_magic, new_magic);
    assert(bytes > 0 && (size_t)bytes < sizeof(message));
}

int __wrap_fstat(int fd, struct stat *status)
{
    assert(fd == 7);
    if (stat_error) { errno = stat_error; return -1; }
    memset(status, 0, sizeof(*status));
    status->st_size = stat_bytes >= 0 ? stat_bytes : (off_t)image_bytes;
    return 0;
}

ssize_t __wrap_pread(int fd, void *buffer, size_t bytes, off_t offset)
{
    assert(fd == 7 && offset >= 0 && (size_t)offset <= image_bytes); ++pread_calls;
    if (pread_interrupt) { pread_interrupt = 0; errno = EINTR; return -1; }
    if (pread_error) { errno = pread_error; return -1; }
    if (pread_eof) return 0;
    size_t count = image_bytes - (size_t)offset;
    if (count > bytes) count = bytes;
    if (count > pread_piece) count = pread_piece;
    memcpy(buffer, image + offset, count);
    return (ssize_t)count;
}

int __wrap_open(const char *path, int flags, ...)
{
    assert(!strcmp(path, "/dev/kmsg") || !strcmp(path, "/kmsg") || !strcmp(path, "/.maplesu_ultra/kmsg"));
    assert(flags == (O_RDONLY | O_NONBLOCK | O_CLOEXEC | O_NOFOLLOW));
    assert(!init_calls && !finit_calls); ++open_calls;
    if (log_missing) { errno = ENOENT; return -1; }
    return 42;
}

off_t __wrap_lseek(int fd, off_t offset, int whence)
{
    assert(fd == 42 && offset == 0 && whence == SEEK_END && !init_calls && !finit_calls);
    ++seek_calls;
    if (seek_error) { errno = seek_error; return -1; }
    cursor_fresh = 1;
    return 0;
}

int __wrap_mknod(const char *path, mode_t mode, dev_t device)
{
    assert(!strcmp(path, "/.maplesu_ultra/kmsg") && mode == (S_IFCHR | 0600));
    assert(device == makedev(1, 11)); errno = EPERM; return -1;
}

int __wrap_unlink(const char *path)
{
    assert(!strcmp(path, "/.maplesu_ultra/kmsg")); return 0;
}

ssize_t __wrap_read(int fd, void *buffer, size_t bytes)
{
    assert(fd == 42 && cursor_fresh && init_calls > 0 && !finit_calls); ++read_calls;
    if (log_interrupt) { log_interrupt = 0; errno = EINTR; return -1; }
    if (log_read_error) { errno = log_read_error; return -1; }
    if (log_at == log_count) { errno = EAGAIN; return -1; }
    const char *record = log_records[log_at++];
    const size_t count = strlen(record); assert(count <= bytes);
    memcpy(buffer, record, count); return (ssize_t)count;
}

int __wrap_close(int fd)
{
    assert(fd == 42); ++close_calls;
    if (log_close_error) { errno = log_close_error; return -1; }
    return 0;
}

long __wrap_syscall(long number, ...)
{
    va_list arguments; va_start(arguments, number);
    int error;
    if (number == SYS_init_module) {
        const void *buffer = va_arg(arguments, const void *);
        const size_t bytes = va_arg(arguments, size_t);
        const char *options = va_arg(arguments, const char *);
        char magic[256];
        assert(msu_component_vermagic(buffer, bytes, magic, sizeof(magic)) == 1);
        assert(init_calls < 2 && !strcmp(magic, init_calls ? new_magic : old_magic));
        assert(log_missing || seek_error || cursor_fresh);
        assert(!strcmp(options, parameters));
        error = syscall_errors[init_calls++];
    } else {
        assert(number == SYS_finit_module && va_arg(arguments, int) == 7);
        assert(!strcmp(va_arg(arguments, const char *), parameters) && va_arg(arguments, int) == 0);
        assert(!open_calls && !seek_calls);
        error = syscall_errors[finit_calls++];
    }
    va_end(arguments);
    if (error) { errno = error; return -1; }
    return 0;
}

static void eligible_component_and_output_contract(void)
{
    reset(); char output[256]; memset(output, 0xa5, sizeof(output));
    assert(msu_component_vermagic(image, image_bytes, output, sizeof(output)) == 1);
    assert(!strcmp(output, old_magic));
    const char sentinel = (char)0xa5;
    memset(output, 0xa5, sizeof(output));
    assert(msu_component_vermagic(image, image_bytes, output, strlen(old_magic)) == -ENOSPC);
    assert(output[0] == sentinel);
    make_image(NULL, 0, 0);
    assert(msu_component_vermagic(image, image_bytes, output, sizeof(output)) == 0 && output[0] == sentinel);
    make_image("maplesu_runtime_facts=1", 1, 0);
    assert(msu_component_vermagic(image, image_bytes, output, sizeof(output)) == -EINVAL && output[0] == sentinel);
    make_image("vermagic=other", 1, 0);
    assert(msu_component_vermagic(image, image_bytes, output, sizeof(output)) == -EINVAL && output[0] == sentinel);
    make_image(NULL, 1, 1);
    assert(msu_component_vermagic(image, image_bytes, output, sizeof(output)) == 0 && output[0] == sentinel);
}

static void elf_short_input_and_section_bounds(void)
{
    reset(); char output[256]; memset(output, 0xa5, sizeof(output));
    for (size_t bytes = 0; bytes < sizeof(Elf64_Ehdr); ++bytes)
        assert(msu_component_vermagic(image, bytes, output, sizeof(output)) == -EINVAL);
    image[image_bytes-1] = 'X';
    assert(msu_component_vermagic(image, image_bytes, output, sizeof(output)) == -EINVAL);
    reset(); Elf64_Ehdr header; memcpy(&header, image, sizeof(header));
    header.e_shoff = UINT64_MAX; memcpy(image, &header, sizeof(header));
    assert(msu_component_vermagic(image, image_bytes, output, sizeof(output)) == -EINVAL);
    reset(); Elf64_Shdr section; memcpy(&section, image+modinfo_header, sizeof(section));
    section.sh_size = UINT64_MAX; memcpy(image+modinfo_header, &section, sizeof(section));
    assert(msu_component_vermagic(image, image_bytes, output, sizeof(output)) == -EINVAL);
    assert(output[0] == (char)0xa5);
}

static void exact_log_old_value_without_release_classification_gate(void)
{
    reset(); char output[256]; memset(output, 0xa5, sizeof(output));
    assert(msu_required_vermagic(message, strlen(message), old_magic, output, sizeof(output)) == 1);
    assert(!strcmp(output, new_magic));
    const char *const rejected[] = {
        "other_maplesu_ultra_bridge: version magic '%s' should be '%s'\n",
        "maplesu_ultra_bridge_extra: version magic '%s' should be '%s'\n",
        "6,1,2,-;other_module: version magic '%s' should be '%s'\n",
    };
    for (size_t index = 0; index < sizeof(rejected)/sizeof(rejected[0]); ++index) {
        char record[1024]; const int bytes = snprintf(record, sizeof(record), rejected[index], old_magic, new_magic);
        memset(output, 0xa5, sizeof(output));
        assert(msu_required_vermagic(record, (size_t)bytes, old_magic, output, sizeof(output)) == 0);
        assert(output[0] == (char)0xa5);
    }
    assert(msu_required_vermagic(message, strlen(message), "6.6.90-android15-wrong SMP", output, sizeof(output)) == 0);
    const char *const required[] = {"6.12.1-android15-8-device SMP", "6.6.94-android16-8-device SMP"};
    for (size_t index = 0; index < sizeof(required)/sizeof(required[0]); ++index) {
        char record[1024]; const int bytes = snprintf(record, sizeof(record),
            "maplesu_ultra_bridge: version magic '%s' should be '%s'\n", old_magic, required[index]);
        assert(msu_required_vermagic(record, (size_t)bytes, old_magic, output, sizeof(output)) == 1);
        assert(!strcmp(output, required[index]));
    }
}

static void log_short_input_and_failed_output_contract(void)
{
    reset(); char output[256]; memset(output, 0xa5, sizeof(output));
    const size_t complete = strlen(message);
    for (size_t bytes = 0; bytes < complete-2; ++bytes)
        assert(msu_required_vermagic(message, bytes, old_magic, output, sizeof(output)) == 0);
    assert(output[0] == (char)0xa5);
    assert(msu_required_vermagic(message, complete, old_magic, output, strlen(new_magic)) == -ENOSPC);
    assert(output[0] == (char)0xa5);
    assert(msu_required_vermagic(message, 65537, old_magic, output, sizeof(output)) == -EINVAL);
    char record[1024]; int bytes = snprintf(record, sizeof(record),
        "maplesu_ultra_bridge: version magic '%s' should be '%s'\n", old_magic, old_magic);
    assert(msu_required_vermagic(record, (size_t)bytes, old_magic, output, sizeof(output)) == 0);
    bytes = snprintf(record, sizeof(record),
        "maplesu_ultra_bridge: version magic '%s' should be '6.6.94-android15-device\nSMP'", old_magic);
    assert(msu_required_vermagic(record, (size_t)bytes, old_magic, output, sizeof(output)) == -EINVAL);
    assert(output[0] == (char)0xa5);
}

static unsigned char *copy_image(void)
{
    unsigned char *copy = __real_malloc(image_bytes); assert(copy);
    memcpy(copy, image, image_bytes); return copy;
}

static void replacing_modinfo_preserves_other_sections(void)
{
    const char *const required[] = {new_magic, "6.6.94-android15-device SMP"};
    for (size_t index = 0; index < sizeof(required)/sizeof(required[0]); ++index) {
        reset(); unsigned char *copy = copy_image(); size_t bytes = image_bytes;
        assert(msu_replace_component_vermagic(&copy, &bytes, old_magic, required[index]) == 0);
        char magic[256]; assert(msu_component_vermagic(copy, bytes, magic, sizeof(magic)) == 1);
        assert(!strcmp(magic, required[index]) && bytes > image_bytes);
        assert(!memcmp(copy, image, modinfo_header));
        assert(!memcmp(copy+modinfo_header+sizeof(Elf64_Shdr), image+modinfo_header+sizeof(Elf64_Shdr),
            image_bytes-modinfo_header-sizeof(Elf64_Shdr)));
        free(copy);
    }
}

static void replace_failures_leave_buffer_identity(void)
{
    reset(); unsigned char *copy = copy_image(), *before = copy; size_t bytes = image_bytes;
    assert(msu_replace_component_vermagic(&copy, &bytes, "stale old", new_magic) == -ESTALE);
    assert(copy == before && bytes == image_bytes && !memcmp(copy, image, bytes));
    assert(msu_replace_component_vermagic(&copy, &bytes, old_magic, "") == -EINVAL);
    assert(copy == before && bytes == image_bytes && !memcmp(copy, image, bytes));
    fail_allocation_at = allocation_calls+1;
    assert(msu_replace_component_vermagic(&copy, &bytes, old_magic, new_magic) == -ENOMEM);
    assert(copy == before && bytes == image_bytes && !memcmp(copy, image, bytes)); free(copy);
    reset(); Elf64_Shdr section; memcpy(&section, image+modinfo_header, sizeof(section));
    section.sh_addralign = 3; memcpy(image+modinfo_header, &section, sizeof(section));
    copy = copy_image(); before = copy; bytes = image_bytes;
    assert(msu_replace_component_vermagic(&copy, &bytes, old_magic, new_magic) == -EINVAL);
    assert(copy == before && bytes == image_bytes && !memcmp(copy, image, bytes)); free(copy);
    reset(); make_image(NULL, 1, 1); copy = copy_image(); before = copy; bytes = image_bytes;
    assert(msu_replace_component_vermagic(&copy, &bytes, old_magic, new_magic) == -EINVAL);
    assert(copy == before && bytes == image_bytes && !memcmp(copy, image, bytes)); free(copy);
}

static void run_load(long expected, int expected_errno)
{
    unsigned char before[sizeof(image)]; memcpy(before, image, sizeof(image));
    errno = 0; assert(msu_load_bridge_fd(7, parameters) == expected);
    if (expected < 0) assert(errno == expected_errno);
    assert(!memcmp(before, image, sizeof(image)));
}

static void loader_retries_exact_fresh_message_once(void)
{
    const int errors[] = {ENOEXEC, EINVAL, EPERM, EKEYREJECTED, EBADMSG};
    for (size_t index = 0; index < sizeof(errors)/sizeof(errors[0]); ++index) {
        reset(); syscall_errors[0] = errors[index]; log_records[0] = message; log_count = 1;
        run_load(0, 0);
        assert(init_calls == 2 && !finit_calls && open_calls == 1 && seek_calls == 1 && read_calls == 1 && close_calls == 1);
    }
    reset(); syscall_errors[0] = ENOEXEC; syscall_errors[1] = EKEYREJECTED;
    log_records[0] = message; log_records[1] = message; log_count = 2;
    run_load(-1, EKEYREJECTED);
    assert(init_calls == 2 && read_calls == 1 && close_calls == 1);
}

static void loader_crc_cfi_and_signature_errors_do_not_retry(void)
{
    const int errors[] = {EINVAL, EPERM, EKEYREJECTED, EBADMSG};
    for (size_t index = 0; index < sizeof(errors)/sizeof(errors[0]); ++index) {
        reset(); syscall_errors[0] = errors[index];
        run_load(-1, errors[index]); assert(init_calls == 1 && read_calls == 1 && close_calls == 1);
    }
    const char *const diagnostics[] = {
        "6,1,2,-;maplesu_ultra_bridge: disagrees about version of symbol module_layout\n",
        "6,1,2,-;CFI failure at maplesu_ultra_bridge\n",
    };
    for (size_t index = 0; index < sizeof(diagnostics)/sizeof(diagnostics[0]); ++index) {
        reset(); syscall_errors[0] = ENOEXEC; log_records[0] = diagnostics[index]; log_count = 1;
        run_load(-1, ENOEXEC); assert(init_calls == 1 && close_calls == 1);
    }
}

static void loader_unrelated_or_missing_log_keeps_first_errno(void)
{
    reset(); syscall_errors[0] = ENOEXEC; log_missing = 1;
    run_load(-1, ENOEXEC); assert(init_calls == 1 && open_calls == 2 && !seek_calls && !read_calls && !close_calls);
    reset(); syscall_errors[0] = ENOEXEC; seek_error = EINVAL;
    run_load(-1, ENOEXEC); assert(init_calls == 1 && seek_calls == 2 && close_calls == 2 && !read_calls);
    reset(); syscall_errors[0] = ENOEXEC; log_read_error = EACCES; log_close_error = EIO;
    run_load(-1, ENOEXEC); assert(init_calls == 1 && read_calls == 1 && close_calls == 1);
    reset(); syscall_errors[0] = ENOEXEC;
    log_records[0] = "6,1,2,-;maplesu_ultra_bridge: version magic 'other' should be '6.6.94-android15-device SMP'\n";
    log_count = 1; run_load(-1, ENOEXEC); assert(init_calls == 1);
    reset(); syscall_errors[0] = ENOEXEC; fail_allocation_at = 2; log_records[0] = message; log_count = 1;
    run_load(-1, ENOEXEC); assert(init_calls == 1 && allocation_calls == 2);
}

static void loader_without_exact_mismatch_keeps_each_first_errno(void)
{
    const int errors[] = {ENOEXEC, EINVAL, EPERM, EKEYREJECTED, EBADMSG};
    const char *const records[] = {NULL,
        "6,1,2,-;other_module: version magic 'unrelated' should be 'other'\n",
        "6,1,2,-;maplesu_ultra_bridge: version magic 'stale' should be 'other'\n"};
    for (size_t error = 0; error < sizeof(errors)/sizeof(errors[0]); ++error) {
        for (size_t record = 0; record < sizeof(records)/sizeof(records[0]); ++record) {
            reset(); syscall_errors[0] = errors[error]; log_close_error = EIO;
            if (records[record]) { log_records[0] = records[record]; log_count = 1; }
            run_load(-1, errors[error]);
            assert(init_calls == 1 && !finit_calls && read_calls == (records[record] ? 2 : 1) && close_calls == 1);
        }
    }
}

static void loader_retry_preserves_each_second_errno(void)
{
    const int first[] = {EINVAL, EPERM, EKEYREJECTED, EBADMSG};
    const int second[] = {EKEYREJECTED, EBADMSG, EINVAL, ENOEXEC};
    for (size_t index = 0; index < sizeof(first)/sizeof(first[0]); ++index) {
        reset(); syscall_errors[0] = first[index]; syscall_errors[1] = second[index];
        log_records[0] = message; log_records[1] = message; log_count = 2; log_close_error = EIO;
        run_load(-1, second[index]);
        assert(init_calls == 2 && read_calls == 1 && log_at == 1 && close_calls == 1);
    }
}

static void loader_log_interrupt_and_bounded_reads(void)
{
    reset(); syscall_errors[0] = ENOEXEC; log_interrupt = 1; log_records[0] = message; log_count = 1;
    run_load(0, 0); assert(init_calls == 2 && read_calls == 2);
    reset(); syscall_errors[0] = ENOEXEC;
    for (size_t index = 0; index < 40; ++index) log_records[index] = "6,1,2,-;unrelated kernel record\n";
    log_count = 40; run_load(-1, ENOEXEC); assert(init_calls == 1 && read_calls == 32);
}

static void signed_and_unmarked_modules_keep_finit_loader(void)
{
    reset(); make_image(NULL, 1, 1); syscall_errors[0] = EKEYREJECTED;
    run_load(-1, EKEYREJECTED); assert(finit_calls == 1 && !init_calls && !open_calls && !read_calls);
    reset(); make_image(NULL, 0, 0);
    run_load(0, 0); assert(finit_calls == 1 && !init_calls && !open_calls && !read_calls);
}

static int resolver_calls;
static int signed_resolver(void *context, const char *const *names, uint64_t *addresses, size_t count)
{
    (void)context; (void)names; (void)addresses; (void)count;
    ++resolver_calls; return -EINVAL;
}

static void signed_direct_module_is_untouched_before_kernel_checks(void)
{
    reset(); make_signed_direct_image(); resolver_calls = 0;
    unsigned char before[sizeof(image)]; memcpy(before, image, sizeof(image));
    assert(msu_bind_legacy_direct(image, image_bytes-sizeof(signature)+1, signed_resolver, NULL) == -EINVAL);
    assert(resolver_calls == 1 && !memcmp(before, image, sizeof(image)));
    resolver_calls = 0;
    assert(msu_bind_legacy_direct(image, image_bytes, signed_resolver, NULL) == 0);
    assert(!resolver_calls && !memcmp(before, image, sizeof(image)));
    syscall_errors[0] = EKEYREJECTED;
    run_load(-1, EKEYREJECTED);
    assert(finit_calls == 1 && !init_calls && !open_calls && !read_calls && !resolver_calls);
}

static void loader_short_reads_and_early_failures(void)
{
    reset(); pread_piece = 7; pread_interrupt = 1;
    run_load(0, 0); assert(init_calls == 1 && pread_calls > 2);
    reset(); pread_eof = 1; run_load(-1, EIO); assert(!init_calls && !open_calls);
    reset(); pread_error = EACCES; run_load(-1, EACCES); assert(!init_calls && !open_calls);
    reset(); stat_error = EIO; run_load(-1, EIO); assert(!pread_calls && !allocation_calls);
    reset(); stat_bytes = 0; run_load(-1, EINVAL); assert(!pread_calls && !allocation_calls);
    reset(); stat_bytes = 64U*1024U*1024U+1; run_load(-1, EFBIG); assert(!pread_calls && !allocation_calls);
    reset(); fail_allocation_at = 1; run_load(-1, ENOMEM); assert(!pread_calls && !init_calls);
}

#define RUN(test) do { test(); ++cases; puts("PASS " #test); } while (0)
int main(void)
{
    unsigned int cases = 0;
    RUN(eligible_component_and_output_contract);
    RUN(elf_short_input_and_section_bounds);
    RUN(exact_log_old_value_without_release_classification_gate);
    RUN(log_short_input_and_failed_output_contract);
    RUN(replacing_modinfo_preserves_other_sections);
    RUN(replace_failures_leave_buffer_identity);
    RUN(loader_retries_exact_fresh_message_once);
    RUN(loader_crc_cfi_and_signature_errors_do_not_retry);
    RUN(loader_unrelated_or_missing_log_keeps_first_errno);
    RUN(loader_without_exact_mismatch_keeps_each_first_errno);
    RUN(loader_retry_preserves_each_second_errno);
    RUN(loader_log_interrupt_and_bounded_reads);
    RUN(signed_and_unmarked_modules_keep_finit_loader);
    RUN(signed_direct_module_is_untouched_before_kernel_checks);
    RUN(loader_short_reads_and_early_failures);
    printf("VERMAGIC_CASES=%u actual helper+loader+binder linked; OS boundaries wrapped\n", cases);
    return 0;
}
