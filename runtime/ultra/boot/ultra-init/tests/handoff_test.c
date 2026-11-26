// SPDX-License-Identifier: GPL-3.0-or-later
#define _GNU_SOURCE
#include "handoff.h"
#include "../../../identity/kernel/manager_deployment_namespace.h"

#include <assert.h>
#include <errno.h>
#include <elf.h>
#include <stdlib.h>
#include <stdint.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <setjmp.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#ifdef MAPLESU_ULTRA_INIT_OS_FIXTURE
#include "os_fixture.h"
#else
#include <sys/syscall.h>
#endif
#include <sys/types.h>

static jmp_buf handed_off;
static char trace[64];
static char diagnostic[512];
static size_t steps, diagnostic_size;
static pid_t pid;
static int rename_error, open_error, load_error, close_error, exec_error, exec_interrupt;
static const char *credential;
static size_t credential_size, credential_at, read_piece;
static int credential_open_error, read_error, read_interrupt, credential_close_error, unlink_error;
static const unsigned char *deployment;
static size_t deployment_size, deployment_at, deployment_piece;
static int deployment_open_error, deployment_read_error, deployment_interrupt, deployment_close_error, deployment_unlink_error;
static const char *loaded_parameters;
static char **expected_argv, **expected_envp;


static unsigned char bridge_image[2048];
static size_t bridge_bytes, bridge_piece, bridge_reads;
static int stat_error, bridge_read_error, bridge_eof, bridge_interrupt, allocation_error;
static int direct_mode, init_calls, finit_calls, proc_mounted, proc_mounts, proc_unmounts;
static int restore_error, lookup_error, setting_writes;
static char setting_value[32], setting_input[32];
static const char *symbol_input;
static const char symbol_lines[] =
    "ffffffc008100000 T file_open_root\nffffffc008100100 T getname_kernel\n"
    "ffffffc008100200 T groups_alloc\nffffffc008100300 T putname\n";
static size_t symbol_offset;

static void make_bridge(int marked)
{
    static const char labels[] = "\0.shstrtab\0.strtab\0.symtab\0.text\0.rela.text\0.msu_direct_calls\0";
    static const char names[] = "\0file_open_root\0getname_kernel\0groups_alloc\0putname\0ordinary_export\0";
    static const char marker[] = "MAPLESU_DIRECT_V1\0file_open_root\0getname_kernel\0groups_alloc\0putname\0";
    memset(bridge_image, 0, sizeof(bridge_image));
    Elf64_Ehdr header = {0};
    memcpy(header.e_ident, ELFMAG, SELFMAG);
    header.e_ident[EI_CLASS] = ELFCLASS64; header.e_ident[EI_DATA] = ELFDATA2LSB;
    header.e_ident[EI_VERSION] = EV_CURRENT; header.e_type = ET_REL;
    header.e_machine = EM_AARCH64; header.e_version = EV_CURRENT;
    header.e_ehsize = sizeof(header); header.e_shentsize = sizeof(Elf64_Shdr);
    header.e_shnum = marked ? 7 : 2; header.e_shstrndx = 1; header.e_shoff = sizeof(header);
    Elf64_Shdr sections[7] = {0};
    size_t at = sizeof(header) + header.e_shnum * sizeof(Elf64_Shdr);
    sections[1] = (Elf64_Shdr){.sh_name=1, .sh_type=SHT_STRTAB, .sh_offset=at, .sh_size=sizeof(labels)};
    memcpy(bridge_image + at, labels, sizeof(labels)); at += sizeof(labels);
    if (marked) {
        sections[2] = (Elf64_Shdr){.sh_name=11, .sh_type=SHT_STRTAB, .sh_offset=at, .sh_size=sizeof(names)};
        memcpy(bridge_image + at, names, sizeof(names)); at += sizeof(names);
        at = (at + 7) & ~(size_t)7; symbol_offset = at;
        sections[3] = (Elf64_Shdr){.sh_name=19, .sh_type=SHT_SYMTAB, .sh_offset=at,
            .sh_size=6*sizeof(Elf64_Sym), .sh_link=2, .sh_info=1, .sh_entsize=sizeof(Elf64_Sym)};
        const unsigned int positions[] = {1, 16, 31, 44, 52};
        for (size_t index=0; index<5; ++index) {
            Elf64_Sym symbol = {.st_name=positions[index], .st_info=ELF64_ST_INFO(STB_GLOBAL, STT_NOTYPE)};
            memcpy(bridge_image + at + (index+1)*sizeof(symbol), &symbol, sizeof(symbol));
        }
        at += sections[3].sh_size;
        sections[4] = (Elf64_Shdr){.sh_name=27, .sh_type=SHT_PROGBITS, .sh_flags=SHF_ALLOC|SHF_EXECINSTR,
            .sh_offset=at, .sh_size=20};
        for (size_t index=0; index<5; ++index) {
            const uint32_t instruction=0x94000000U;
            memcpy(bridge_image + at + index*4, &instruction, sizeof(instruction));
        }
        at += 20; at = (at + 7) & ~(size_t)7;
        sections[5] = (Elf64_Shdr){.sh_name=33, .sh_type=SHT_RELA, .sh_offset=at,
            .sh_size=5*sizeof(Elf64_Rela), .sh_link=3, .sh_info=4, .sh_entsize=sizeof(Elf64_Rela)};
        for (size_t index=0; index<5; ++index) {
            Elf64_Rela relocation = {.r_offset=index*4, .r_info=ELF64_R_INFO(index+1, 283)};
            memcpy(bridge_image + at + index*sizeof(relocation), &relocation, sizeof(relocation));
        }
        at += sections[5].sh_size;
        sections[6] = (Elf64_Shdr){.sh_name=44, .sh_type=SHT_PROGBITS, .sh_offset=at, .sh_size=sizeof(marker)};
        memcpy(bridge_image + at, marker, sizeof(marker)); at += sizeof(marker);
    }
    memcpy(bridge_image, &header, sizeof(header));
    memcpy(bridge_image + header.e_shoff, sections, header.e_shnum*sizeof(Elf64_Shdr));
    bridge_bytes=at; direct_mode=marked;
}

int __wrap_fstat(int fd, struct stat *status)
{
    assert(fd == 7);
    if (stat_error) { errno=stat_error; return -1; }
    memset(status,0,sizeof(*status)); status->st_size=(off_t)bridge_bytes;
    return 0;
}
ssize_t __wrap_pread(int fd, void *buffer, size_t bytes, off_t offset)
{
    assert(fd == 7 && offset >= 0 && (size_t)offset <= bridge_bytes); ++bridge_reads;
    if (bridge_interrupt) { bridge_interrupt=0; errno=EINTR; return -1; }
    if (bridge_read_error) { errno=bridge_read_error; return -1; }
    if (bridge_eof) return 0;
    size_t count=bridge_bytes-(size_t)offset;
    if (count>bytes) count=bytes;
    if (count>bridge_piece) count=bridge_piece;
    memcpy(buffer,bridge_image+(size_t)offset,count); return (ssize_t)count;
}
void *__real_malloc(size_t bytes);
void *__wrap_malloc(size_t bytes)
{
    if (allocation_error && bytes == bridge_bytes) { errno=ENOMEM; return NULL; }
    return __real_malloc(bytes);
}
int __wrap_mkdir(const char *path, mode_t mode)
{
    assert(!strcmp(path,"/proc") && mode == 0755); errno=EEXIST; return -1;
}
int __wrap_rmdir(const char *path) { (void)path; assert(!"existing proc directory must be retained"); return -1; }
int __wrap_mount(const char *from, const char *to, const char *type, unsigned long flags, const void *data)
{
    assert(!strcmp(from,"proc") && !strcmp(to,"/proc") && !strcmp(type,"proc") && !data);
    assert(flags == (MS_NOSUID|MS_NODEV|MS_NOEXEC) && !proc_mounted);
    proc_mounted=1; ++proc_mounts; return 0;
}
int __wrap_umount2(const char *path, int flags)
{
    assert(!strcmp(path,"/proc") && flags == MNT_DETACH && proc_mounted);
    proc_mounted=0; ++proc_unmounts; return 0;
}
FILE *__wrap_fopen(const char *path, const char *mode)
{
    assert(proc_mounted && !strcmp(mode,"re"));
    if (!strcmp(path,"/proc/sys/kernel/kptr_restrict")) {
        strcpy(setting_input,setting_value); return fmemopen(setting_input,strlen(setting_input),"r");
    }
    assert(!strcmp(path,"/proc/kallsyms"));
    if (lookup_error) { errno=lookup_error; return NULL; }
    return fmemopen((void *)symbol_input,strlen(symbol_input),"r");
}

static void note(char step) {
    assert(steps + 1 < sizeof(trace));
    trace[steps++] = step;
    trace[steps] = 0;
}

pid_t __wrap_getpid(void) { return pid; }

int __wrap_rename(const char *from, const char *to) {
    note('R');
    assert(strcmp(from, "/init.maplesu.orig") == 0 && strcmp(to, "/init") == 0);
    errno = rename_error;
    return rename_error ? -1 : 0;
}

int __wrap_open(const char *path, int flags, ...) {
    if (!strcmp(path,"/proc/sys/kernel/kptr_restrict")) {
        assert(proc_mounted && flags == (O_WRONLY|O_CLOEXEC)); ++setting_writes;
        if (setting_writes > 1 && restore_error) { errno=restore_error; return -1; }
        return 55;
    }
    if (strcmp(path, "/.maplesu_ultra/deployment") == 0) {
        assert(flags == (O_RDONLY | O_CLOEXEC | O_NOFOLLOW));
        if (!deployment && !deployment_open_error) { errno = ENOENT; return -1; }
        note('N');
        errno = deployment_open_error;
        return deployment_open_error ? -1 : 9;
    }
    if (strcmp(path, "/.maplesu_ultra/credential") == 0) {
        assert(flags == (O_RDONLY | O_CLOEXEC | O_NOFOLLOW));
        if (!credential && !credential_open_error) { errno = ENOENT; return -1; }
        note('K');
        errno = credential_open_error;
        return credential_open_error ? -1 : 8;
    }
    note('O');
    assert(strcmp(path, "/.maplesu_ultra/bridge.ko") == 0);
    assert(flags == (O_RDONLY | O_CLOEXEC));
    errno = open_error;
    return open_error ? -1 : 7;
}

#ifdef MAPLESU_ULTRA_INIT_OS_FIXTURE
int __wrap_read(int fd, void *buffer, unsigned int bytes) {
#else
ssize_t __wrap_read(int fd, void *buffer, size_t bytes) {
#endif
    if (fd == 9) {
        note('d');
        if (deployment_interrupt) { deployment_interrupt = 0; errno = EINTR; return -1; }
        if (deployment_read_error) { errno = deployment_read_error; return -1; }
        size_t count = deployment_size - deployment_at;
        if (count > bytes) count = bytes;
        if (count > deployment_piece) count = deployment_piece;
        memcpy(buffer, deployment + deployment_at, count);
        deployment_at += count;
        return count;
    }
    assert(fd == 8);
    note('D');
    if (read_interrupt) { read_interrupt = 0; errno = EINTR; return -1; }
    if (read_error) { errno = read_error; return -1; }
    size_t count = credential_size - credential_at;
    if (count > bytes) count = bytes;
    if (count > read_piece) count = read_piece;
    memcpy(buffer, credential + credential_at, count);
    credential_at += count;
    return count;
}

int __wrap_unlink(const char *path) {
    if (strcmp(path, "/.maplesu_ultra/deployment") == 0) {
        note('u');
        errno = deployment_unlink_error;
        return deployment_unlink_error ? -1 : 0;
    }
    assert(strcmp(path, "/.maplesu_ultra/credential") == 0);
    note('U');
    errno = unlink_error;
    return unlink_error ? -1 : 0;
}

long __wrap_syscall(long number, ...) {
    note('L');
    assert(number == (direct_mode ? SYS_init_module : SYS_finit_module));
    assert(!proc_mounted);
    if (direct_mode) ++init_calls; else ++finit_calls;
    va_list args;
    va_start(args, number);
    if (direct_mode) {
        const unsigned char *loaded = va_arg(args, const unsigned char *);
        assert(va_arg(args, size_t) == bridge_bytes);
        unsigned char expected[sizeof(bridge_image)]; memcpy(expected,bridge_image,bridge_bytes);
        for (size_t index=0; index<4; ++index) {
            Elf64_Sym symbol;
            const size_t at=symbol_offset+(index+1)*sizeof(symbol);
            memcpy(&symbol,expected+at,sizeof(symbol));
            symbol.st_shndx=SHN_ABS; symbol.st_value=0xffffffc008100000ULL+index*0x100;
            memcpy(expected+at,&symbol,sizeof(symbol));
        }
        assert(!memcmp(loaded,expected,bridge_bytes));
    } else assert(va_arg(args, int) == 7);
    loaded_parameters = va_arg(args, const char *);
    char expected[128] = {0};
    if (credential) {
        memcpy(expected, "credential=", 11);
        memcpy(expected + 11, credential, 48);
    }
    if (deployment) {
        const size_t used = strlen(expected);
        snprintf(expected + used, sizeof(expected) - used, "%sdeployment_namespace=%.*s",
            used ? " " : "", 16, (const char *)deployment + 8);
    }
    assert(strcmp(loaded_parameters, expected) == 0);
    if (!direct_mode) assert(va_arg(args, int) == 0);
    va_end(args);
    errno = load_error;
    return load_error ? -1 : 0;
}

int __wrap_close(int fd) {
    if (fd == 55) return 0;
    if (fd == 9) {
        note('n');
        errno = deployment_close_error;
        return deployment_close_error ? -1 : 0;
    }
    if (fd == 8) {
        note('k');
        errno = credential_close_error;
        return credential_close_error ? -1 : 0;
    }
    note('C');
    assert(fd == 7);
    if (loaded_parameters) {
        for (size_t index = 0; index < 128; ++index) assert(loaded_parameters[index] == 0);
    }
    errno = close_error;
    return close_error ? -1 : 0;
}

int __wrap_execve(const char *path, char *const argv[], char *const envp[]) {
    note('E');
    assert(strcmp(path, rename_error ? "/init.maplesu.orig" : "/init") == 0);
    assert(argv == expected_argv && envp == expected_envp);
    assert(strcmp(argv[0], "/init") == 0 && strcmp(argv[1], "first_stage") == 0 && argv[2] == NULL);
    assert(strcmp(envp[0], "INIT_ENV=kept value") == 0 && envp[1] == NULL);
    if (exec_interrupt) {
        exec_interrupt = 0;
        errno = EINTR;
        return -1;
    }
    if (exec_error) {
        errno = exec_error;
        return -1;
    }
    longjmp(handed_off, 1);
}

#ifdef MAPLESU_ULTRA_INIT_OS_FIXTURE
int __wrap_write(int fd, const void *bytes, unsigned int size) {
#else
ssize_t __wrap_write(int fd, const void *bytes, size_t size) {
#endif
    if (fd == 55) {
        assert(proc_mounted && size < sizeof(setting_value));
        memcpy(setting_value,bytes,size); setting_value[size]=0; return (ssize_t)size;
    }
    note('W');
    assert(fd == 2 && diagnostic_size + size < sizeof(diagnostic));
    memcpy(diagnostic + diagnostic_size, bytes, size);
    diagnostic_size += size;
    diagnostic[diagnostic_size] = 0;
    return size;
}

static void reset(void) {
    make_bridge(0); bridge_piece=sizeof(bridge_image); bridge_reads=0;
    stat_error=bridge_read_error=bridge_eof=bridge_interrupt=allocation_error=0;
    init_calls=finit_calls=proc_mounted=proc_mounts=proc_unmounts=0;
    restore_error=lookup_error=setting_writes=0; strcpy(setting_value,"2\n"); symbol_input=symbol_lines;
    memset(trace, 0, sizeof(trace));
    memset(diagnostic, 0, sizeof(diagnostic));
    steps = diagnostic_size = 0;
    pid = 1;
    rename_error = open_error = load_error = close_error = exec_error = exec_interrupt = 0;
    credential = loaded_parameters = NULL;
    credential_size = credential_at = 0;
    read_piece = 49;
    credential_open_error = read_error = read_interrupt = credential_close_error = unlink_error = 0;
    deployment = NULL;
    deployment_size = deployment_at = 0;
    deployment_piece = 25;
    deployment_open_error = deployment_read_error = deployment_interrupt = deployment_close_error = deployment_unlink_error = 0;
}

static void expect_handoff(const char *sequence) {
    if (setjmp(handed_off) == 0) {
        (void)maplesu_ultra_first_stage(expected_argv, expected_envp);
        assert(!"original init was not executed");
    }
    assert(strcmp(trace, sequence) == 0);
}

int main(void) {
    char name[] = "/init", argument[] = "first_stage", environment[] = "INIT_ENV=kept value";
    char *argv[] = {name, argument, NULL};
    char *envp[] = {environment, NULL};
    expected_argv = argv;
    expected_envp = envp;

    reset();
    expect_handoff("ROLCE");
    reset(); open_error = ENOENT;
    expect_handoff("ROWE");
    assert(strstr(diagnostic, "open /.maplesu_ultra/bridge.ko failed errno=2") != NULL);
    reset(); load_error = ENOEXEC;
    expect_handoff("ROLCWE");
    assert(strstr(diagnostic, "load_module /.maplesu_ultra/bridge.ko failed errno=8") != NULL);
    reset(); close_error = EIO;
    expect_handoff("ROLCWE");
    assert(strstr(diagnostic, "close /.maplesu_ultra/bridge.ko failed errno=5") != NULL);
    reset(); load_error = ENOEXEC; close_error = EIO;
    expect_handoff("ROLCWWE");
    const char *load_message = strstr(diagnostic, "load_module /.maplesu_ultra/bridge.ko failed errno=8");
    const char *close_message = strstr(diagnostic, "close /.maplesu_ultra/bridge.ko failed errno=5");
    assert(load_message && close_message && load_message < close_message);
    reset(); rename_error = EROFS;
    expect_handoff("RWOLCE");
    assert(strstr(diagnostic, "rename /init.maplesu.orig failed errno=30") != NULL);
    reset(); rename_error = EROFS; load_error = ENOEXEC; close_error = EIO;
    expect_handoff("RWOLCWWE");
    const char *rename_message = strstr(diagnostic, "rename /init.maplesu.orig failed errno=30");
    load_message = strstr(diagnostic, "load_module /.maplesu_ultra/bridge.ko failed errno=8");
    close_message = strstr(diagnostic, "close /.maplesu_ultra/bridge.ko failed errno=5");
    assert(rename_message && load_message && close_message &&
           rename_message < load_message && load_message < close_message);
    reset(); exec_interrupt = 1;
    expect_handoff("ROLCEE");
    reset(); exec_error = EACCES;
    assert(maplesu_ultra_first_stage(argv, envp) == 126 && strcmp(trace, "ROLCEW") == 0);
    assert(strstr(diagnostic, "execve /init failed errno=13") != NULL);
    reset(); rename_error = EROFS; exec_error = ENOENT;
    assert(maplesu_ultra_first_stage(argv, envp) == 126 && strcmp(trace, "RWOLCEW") == 0);
    assert(strstr(diagnostic, "execve /init.maplesu.orig failed errno=2") != NULL);
    reset(); pid = 123;
    assert(maplesu_ultra_first_stage(argv, envp) == 64 && steps == 0);
    assert(strcmp(name, "/init") == 0 && strcmp(argument, "first_stage") == 0);
    assert(strcmp(environment, "INIT_ENV=kept value") == 0);

    const char key[] = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKL";
    assert(sizeof(key) == 49);
    reset(); credential = key; credential_size = 48;
    expect_handoff("ROKDDkULCE");
    assert(diagnostic_size == 0);
    reset(); credential = key; credential_size = 48; read_piece = 17; read_interrupt = 1;
    expect_handoff("ROKDDDDDkULCE");
    reset(); credential = key; credential_size = 47;
    expect_handoff("ROKDDWkUCE");
    assert(strstr(diagnostic, "read /.maplesu_ultra/credential failed errno=22"));
    reset(); credential = key; credential_size = 49;
    expect_handoff("ROKDWkUCE");
    reset(); credential = "bad\n"; credential_size = 4;
    expect_handoff("ROKDDWkUCE");
    reset(); credential = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJK/"; credential_size = 48;
    expect_handoff("ROKDDWkUCE");
    reset(); credential = "";
    expect_handoff("ROKDWkUCE");
    reset(); credential = key; credential_size = 48; read_error = EIO;
    expect_handoff("ROKDWkUCE");
    assert(strstr(diagnostic, "read /.maplesu_ultra/credential failed errno=5"));
    reset(); credential = key; credential_size = 48; credential_close_error = EIO;
    expect_handoff("ROKDDkWUCE");
    reset(); credential_open_error = ELOOP;
    expect_handoff("ROKWCE");
    reset(); credential = key; credential_size = 48; unlink_error = EROFS;
    expect_handoff("ROKDDkUWLCE");
    assert(strstr(diagnostic, "unlink /.maplesu_ultra/credential failed errno=30"));
    reset(); credential = key; credential_size = 48; load_error = ENOEXEC;
    expect_handoff("ROKDDkULCWE");
    assert(strstr(diagnostic, key) == NULL);
    reset(); credential = key; credential_size = 48; open_error = ENOENT;
    expect_handoff("ROWKDDkUE");
    assert(credential_at == 48 && strstr(diagnostic, key) == NULL);

    struct msu_deployment_namespace_record record = {
        .magic = MSU_DEPLOYMENT_NAMESPACE_MAGIC,
        .schema = MSU_DEPLOYMENT_NAMESPACE_SCHEMA,
        .bytes = sizeof(record),
    };
    memcpy(record.name, "ABCD2345EFGH6789", sizeof(record.name));
    unsigned char namespace_bytes[sizeof(record) + 1] = {0};
    memcpy(namespace_bytes, &record, sizeof(record));
    reset(); deployment = namespace_bytes; deployment_size = sizeof(record);
    expect_handoff("RONddnuLCE");
    assert(diagnostic_size == 0);
    reset(); credential = key; credential_size = 48; deployment = namespace_bytes; deployment_size = sizeof(record);
    expect_handoff("ROKDDkUNddnuLCE");
    reset(); deployment = namespace_bytes; deployment_size = sizeof(record); deployment_piece = 7; deployment_interrupt = 1;
    expect_handoff("RONddddddnuLCE");
    reset(); deployment = namespace_bytes; deployment_size = sizeof(record) - 1;
    expect_handoff("RONddWnuCE");
    reset(); deployment = namespace_bytes; deployment_size = sizeof(namespace_bytes);
    expect_handoff("RONdWnuCE");
    namespace_bytes[0] ^= 1;
    reset(); deployment = namespace_bytes; deployment_size = sizeof(record);
    expect_handoff("RONddWnuCE");
    namespace_bytes[0] ^= 1;
    namespace_bytes[8] = '/';
    reset(); deployment = namespace_bytes; deployment_size = sizeof(record);
    expect_handoff("RONddWnuCE");
    memcpy(namespace_bytes, &record, sizeof(record));
    reset(); deployment_open_error = ELOOP;
    expect_handoff("RONWCE");
    reset(); deployment = namespace_bytes; deployment_size = sizeof(record); deployment_read_error = EIO;
    expect_handoff("RONdWnuCE");
    reset(); deployment = namespace_bytes; deployment_size = sizeof(record); deployment_close_error = EIO;
    expect_handoff("RONddnWuCE");
    reset(); deployment = namespace_bytes; deployment_size = sizeof(record); deployment_unlink_error = EROFS;
    expect_handoff("RONddnuWLCE");
    reset(); deployment = namespace_bytes; deployment_size = sizeof(record); open_error = ENOENT;
    expect_handoff("ROWNddnuE");
    assert(deployment_at == sizeof(record));
    reset(); deployment = namespace_bytes; deployment_size = sizeof(record); credential = key; credential_size = 47;
    expect_handoff("ROKDDWkUNddnuCE");
    assert(deployment_at == sizeof(record));
    reset(); deployment = namespace_bytes; deployment_size = sizeof(record); load_error = ENOEXEC;
    expect_handoff("RONddnuLCWE");
    reset(); deployment = namespace_bytes; deployment_size = sizeof(record); rename_error = EROFS;
    expect_handoff("RWONddnuLCE");

    reset(); stat_error=EIO;
    expect_handoff("ROCWE"); assert(!init_calls && !finit_calls && !bridge_reads);
    assert(strstr(diagnostic,"load_module /.maplesu_ultra/bridge.ko failed errno=5"));
    reset(); bridge_bytes=0;
    expect_handoff("ROCWE"); assert(!init_calls && !finit_calls);
    assert(strstr(diagnostic,"load_module /.maplesu_ultra/bridge.ko failed errno=22"));
    reset(); allocation_error=1;
    expect_handoff("ROCWE"); assert(!init_calls && !finit_calls && !bridge_reads);
    assert(strstr(diagnostic,"load_module /.maplesu_ultra/bridge.ko failed errno=12"));
    reset(); bridge_piece=17; bridge_interrupt=1;
    expect_handoff("ROLCE"); assert(finit_calls == 1 && !init_calls && bridge_reads > 2);
    assert(!proc_mounts && !proc_unmounts);
    reset(); bridge_eof=1;
    expect_handoff("ROCWE"); assert(!init_calls && !finit_calls);
    assert(strstr(diagnostic,"load_module /.maplesu_ultra/bridge.ko failed errno=5"));
    reset(); bridge_read_error=EACCES; close_error=EIO;
    expect_handoff("ROCWWE"); assert(!init_calls && !finit_calls);
    load_message=strstr(diagnostic,"load_module /.maplesu_ultra/bridge.ko failed errno=13");
    close_message=strstr(diagnostic,"close /.maplesu_ultra/bridge.ko failed errno=5");
    assert(load_message && close_message && load_message < close_message);
    reset(); bridge_image[0]='X';
    expect_handoff("ROCWE"); assert(!init_calls && !finit_calls && !proc_mounts);
    reset(); make_bridge(1);
    expect_handoff("ROLCE"); assert(init_calls == 1 && !finit_calls);
    assert(proc_mounts == 1 && proc_unmounts == 1 && !strcmp(setting_value,"2\n"));
    reset(); make_bridge(1); credential=key; credential_size=48; deployment=namespace_bytes; deployment_size=sizeof(record);
    expect_handoff("ROKDDkUNddnuLCE"); assert(init_calls == 1 && !finit_calls && !diagnostic_size);
    reset(); make_bridge(1); load_error=ENOEXEC;
    expect_handoff("ROLCWE"); assert(init_calls == 1 && !finit_calls && !strcmp(setting_value,"2\n"));
    assert(strstr(diagnostic,"load_module /.maplesu_ultra/bridge.ko failed errno=8"));
    reset(); make_bridge(1); lookup_error=EACCES;
    expect_handoff("ROCWE"); assert(!init_calls && !finit_calls && proc_unmounts == 1 && !strcmp(setting_value,"2\n"));
    reset(); make_bridge(1); restore_error=EROFS;
    expect_handoff("ROCWE"); assert(!init_calls && !finit_calls && proc_unmounts == 1 && !strcmp(setting_value,"1"));
    assert(strstr(diagnostic,"load_module /.maplesu_ultra/bridge.ko failed errno=30"));
    reset(); make_bridge(1); lookup_error=EACCES; restore_error=EROFS;
    expect_handoff("ROCWE"); assert(!init_calls && !finit_calls && proc_unmounts == 1);
    assert(strstr(diagnostic,"load_module /.maplesu_ultra/bridge.ko failed errno=13"));
    reset(); make_bridge(1); rename_error=EROFS; load_error=ENOEXEC; close_error=EIO;
    expect_handoff("RWOLCWWE"); assert(init_calls == 1 && !finit_calls);
    puts("HANDOFF_DIRECT_NEW_CASES=14 original handoff cases retained; actual loader+file-read+binder linked");

    puts("PASS isolated first-stage handoff seam");
    return 0;
}
