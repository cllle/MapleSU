// SPDX-License-Identifier: GPL-3.0-or-later
#include "legacy_direct_bind.h"
#include <elf.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>

static const char direct_marker[] =
    "MAPLESU_DIRECT_V1\0file_open_root\0getname_kernel\0groups_alloc\0putname\0";
static const char *const direct_names[] = { "file_open_root", "getname_kernel", "groups_alloc", "putname" };

static const char direct_v2_magic[] = "MAPLESU_DIRECT_V2";
static const char direct_prefix[] = "msu_direct_target_";
static const char *const direct_targets_v2[] = {
    "__arm64_sys_execve",
    "__flush_icache_range",
    "__module_address",
    "__module_text_address",
    "__put_cred",
    "__vfs_getxattr",
    "__vmalloc_node_range",
    "__vmalloc_node_range_noprof",
    "aarch64_insn_patch_text",
    "abort_creds",
    "alloc_uid",
    "caches_clean_inval_pou",
    "cancel_work",
    "commit_creds",
    "copy_from_kernel_nofault",
    "copy_from_user_nofault",
    "copy_to_kernel_nofault",
    "copy_to_user_nofault",
    "core_kernel_text",
    "execmem_alloc",
    "execmem_alloc_rw",
    "execmem_free",
    "file_open_root",
    "filp_open",
    "find_task_by_pid_ns",
    "fortify_panic",
    "free_nsproxy",
    "fsnotify_add_mark",
    "fsnotify_alloc_group",
    "fsnotify_destroy_group",
    "fsnotify_destroy_mark",
    "fsnotify_get_group",
    "fsnotify_get_mark",
    "fsnotify_init_mark",
    "fsnotify_put_group",
    "fsnotify_put_mark",
    "fsnotify_wait_marks_destroyed",
    "get_mm_exe_file",
    "get_task_cred",
    "get_task_exe_file",
    "getname_kernel",
    "groups_alloc",
    "groups_sort",
    "is_subdir",
    "iterate_dir",
    "kernel_read",
    "module_alloc",
    "module_memfree",
    "our_mnt",
    "override_creds",
    "path_get",
    "path_mount",
    "path_umount",
    "prepare_creds",
    "putname",
    "revert_creds",
    "schedule_on_each_cpu",
    "seccomp_filter_release",
    "security_cred_getsecid",
    "security_secctx_to_secid",
    "set_cred_ucounts",
    "set_groups",
    "set_memory_nx",
    "set_memory_ro",
    "set_memory_rw",
    "set_memory_x",
    "sha256",
    "sync_icache_aliases",
    "synchronize_rcu_tasks",
    "task_work_add",
    "vfree",
    "vfs_getxattr",
    "vfs_setxattr",
    "vfs_write",
    "vmalloc",
    "vmalloc_noprof",
    "vmalloc_user",
    "vmalloc_user_noprof",
};
_Static_assert(sizeof(direct_targets_v2) / sizeof(direct_targets_v2[0]) == MSU_DIRECT_LIMIT,
    "native direct consumer capacity");

static int parse_marker(const unsigned char *raw, size_t bytes,
    const char **aliases, const char **targets, int *version2)
{
    if (bytes == sizeof(direct_marker) && !memcmp(raw, direct_marker, bytes)) {
        for (size_t index = 0; index < 4; ++index) aliases[index] = targets[index] = direct_names[index];
        *version2 = 0;
        return 4;
    }
    if (bytes <= sizeof(direct_v2_magic) || memcmp(raw, direct_v2_magic, sizeof(direct_v2_magic))) return -EINVAL;
    const char *at = (const char *)raw + sizeof(direct_v2_magic), *end = (const char *)raw + bytes;
    size_t count = 0;
    *version2 = 1;
    for (;;) {
        if (at == end - 1 && !*at) return count ? (int)count : -EINVAL;
        if (at >= end || !*at || count == MSU_DIRECT_LIMIT) return -EINVAL;
        const char *alias = at, *stop = memchr(at, 0, (size_t)(end - at));
        if (!stop || stop + 1 >= end) return -EINVAL;
        at = stop + 1;
        const char *target = at;
        stop = memchr(at, 0, (size_t)(end - at));
        if (!stop || !*target || strncmp(alias, direct_prefix, sizeof(direct_prefix) - 1) ||
            strcmp(alias + sizeof(direct_prefix) - 1, target) ||
            (count && strcmp(targets[count - 1], target) >= 0)) return -EINVAL;
        size_t allowed = 0;
        for (; allowed < MSU_DIRECT_LIMIT; ++allowed)
            if (!strcmp(target, direct_targets_v2[allowed])) break;
        if (allowed == MSU_DIRECT_LIMIT) return -EINVAL;
        aliases[count] = alias;
        targets[count++] = target;
        at = stop + 1;
    }
}

static int inside(size_t total, uint64_t at, uint64_t bytes)
{
    return at <= total && bytes <= total - at;
}

static int section_at(const unsigned char *module, size_t bytes, const Elf64_Ehdr *header,
    size_t index, Elf64_Shdr *section)
{
    if (index >= header->e_shnum ||
        !inside(bytes, header->e_shoff, (uint64_t)header->e_shnum * sizeof(*section))) return -EINVAL;
    memcpy(section, module + header->e_shoff + index * sizeof(*section), sizeof(*section));
    return section->sh_type == SHT_NOBITS || inside(bytes, section->sh_offset, section->sh_size) ? 0 : -EINVAL;
}

static const char *string_at(const unsigned char *module, const Elf64_Shdr *strings, uint32_t at)
{
    if (at >= strings->sh_size) return NULL;
    const char *name = (const char *)module + strings->sh_offset + at;
    return memchr(name, 0, strings->sh_size - at) ? name : NULL;
}

int msu_bind_legacy_direct(void *buffer, size_t bytes, msu_direct_resolver resolver, void *context)
{
    unsigned char *module = buffer;
    Elf64_Ehdr header;
    if (!module || bytes < sizeof(header)) return -EINVAL;
    static const char signature[] = "~Module signature appended~\n";
    if (bytes >= sizeof(signature) - 1 &&
        !memcmp(module + bytes - sizeof(signature) + 1, signature, sizeof(signature) - 1)) return 0;
    memcpy(&header, module, sizeof(header));
    if (memcmp(header.e_ident, ELFMAG, SELFMAG) || header.e_ident[EI_CLASS] != ELFCLASS64 ||
        header.e_ident[EI_DATA] != ELFDATA2LSB || header.e_ident[EI_VERSION] != EV_CURRENT ||
        header.e_type != ET_REL || header.e_machine != EM_AARCH64 || header.e_version != EV_CURRENT ||
        header.e_ehsize != sizeof(header) || header.e_shentsize != sizeof(Elf64_Shdr)) return -EINVAL;
    Elf64_Shdr names;
    if (section_at(module, bytes, &header, header.e_shstrndx, &names) || names.sh_type != SHT_STRTAB) return -EINVAL;
    Elf64_Shdr symtab = {0}, marker = {0};
    size_t symtab_index = 0, marker_count = 0, symtab_count = 0;
    for (size_t index = 1; index < header.e_shnum; ++index) {
        Elf64_Shdr section;
        if (section_at(module, bytes, &header, index, &section)) return -EINVAL;
        const char *name = string_at(module, &names, section.sh_name);
        if (!name) return -EINVAL;
        if (!strcmp(name, ".msu_direct_calls")) { marker = section; ++marker_count; }
        if (section.sh_type == SHT_SYMTAB) { symtab = section; symtab_index = index; ++symtab_count; }
    }
    if (!marker_count && !symtab_count) return 0;
    if (marker_count > 1 || (marker_count && (marker.sh_type != SHT_PROGBITS || !resolver)) ||
        symtab_count != 1 || symtab.sh_entsize != sizeof(Elf64_Sym) ||
        symtab.sh_size % sizeof(Elf64_Sym)) return -EINVAL;
    const char *aliases[MSU_DIRECT_LIMIT] = {0}, *target_names[MSU_DIRECT_LIMIT] = {0};
    int version2 = 0;
    int parsed = marker_count ? parse_marker(module + marker.sh_offset, marker.sh_size,
        aliases, target_names, &version2) : 0;
    if (parsed < 0) return parsed;
    const size_t direct_count = (size_t)parsed;
    Elf64_Shdr strings;
    if (section_at(module, bytes, &header, symtab.sh_link, &strings) || strings.sh_type != SHT_STRTAB) return -EINVAL;
    size_t indexes[MSU_DIRECT_LIMIT] = {0}, calls[MSU_DIRECT_LIMIT] = {0};
    Elf64_Sym selected[MSU_DIRECT_LIMIT];
    for (size_t index = 1; index < symtab.sh_size / sizeof(Elf64_Sym); ++index) {
        Elf64_Sym symbol;
        memcpy(&symbol, module + symtab.sh_offset + index * sizeof(symbol), sizeof(symbol));
        const char *name = string_at(module, &strings, symbol.st_name);
        if (!name) return -EINVAL;
        int declared = 0;
        for (size_t target = 0; target < direct_count; ++target) if (!strcmp(name, aliases[target])) {
            declared = 1;
            if (indexes[target] || symbol.st_shndx != SHN_UNDEF || ELF64_ST_BIND(symbol.st_info) != STB_GLOBAL ||
                (ELF64_ST_TYPE(symbol.st_info) != STT_NOTYPE && ELF64_ST_TYPE(symbol.st_info) != STT_FUNC)) return -EINVAL;
            indexes[target] = index;
            selected[target] = symbol;
        }
        if (!declared && !strncmp(name, direct_prefix, sizeof(direct_prefix) - 1)) return -EINVAL;
    }
    if (!marker_count) return 0;
    if (version2) for (size_t target = 0; target < direct_count; ++target)
        if (!indexes[target]) return -EINVAL;
    for (size_t index = 1; index < header.e_shnum; ++index) {
        Elf64_Shdr section;
        if (section_at(module, bytes, &header, index, &section)) return -EINVAL;
        if ((section.sh_type != SHT_RELA && section.sh_type != SHT_REL) || section.sh_link != symtab_index) continue;
        if (section.sh_type != SHT_RELA || section.sh_entsize != sizeof(Elf64_Rela) ||
            section.sh_size % sizeof(Elf64_Rela)) return -EINVAL;
        Elf64_Shdr code;
        if (section_at(module, bytes, &header, section.sh_info, &code)) return -EINVAL;
        for (size_t at = 0; at < section.sh_size; at += sizeof(Elf64_Rela)) {
            Elf64_Rela relocation;
            memcpy(&relocation, module + section.sh_offset + at, sizeof(relocation));
            for (size_t target = 0; target < direct_count; ++target) {
                if (!indexes[target] || ELF64_R_SYM(relocation.r_info) != indexes[target]) continue;
                const unsigned int type = ELF64_R_TYPE(relocation.r_info);
                if ((type != 282 && type != 283) || relocation.r_addend || !(code.sh_flags & SHF_EXECINSTR) ||
                    code.sh_type != SHT_PROGBITS || relocation.r_offset % 4 ||
                    !inside(code.sh_size, relocation.r_offset, sizeof(uint32_t))) return -EINVAL;
                uint32_t instruction;
                memcpy(&instruction, module + code.sh_offset + relocation.r_offset, sizeof(instruction));
                if ((instruction & 0xfc000000U) != (type == 283 ? 0x94000000U : 0x14000000U)) return -EINVAL;
                ++calls[target];
            }
        }
    }
    const char *requested[MSU_DIRECT_LIMIT]; uint64_t addresses[MSU_DIRECT_LIMIT] = {0};
    size_t targets[MSU_DIRECT_LIMIT], count = 0;
    for (size_t target = 0; target < direct_count; ++target) if (indexes[target]) {
        if (!calls[target]) return -EINVAL;
        requested[count] = target_names[target]; targets[count++] = target;
    }
    if (!count) return -EINVAL;
    const int error = resolver(context, requested, addresses, count);
    if (error) return error < 0 ? error : -EINVAL;
    for (size_t index = 0; index < count; ++index)
        if (!addresses[index] || (addresses[index] & 3)) return -ENOENT;
    /* Validate the complete request before changing any ELF symbol. No CRC is invented. */
    for (size_t index = 0; index < count; ++index) {
        const size_t target = targets[index];
        selected[target].st_shndx = SHN_ABS;
        selected[target].st_value = addresses[index];
        memcpy(module + symtab.sh_offset + indexes[target] * sizeof(Elf64_Sym), &selected[target], sizeof(Elf64_Sym));
    }
    return 1;
}

struct msu_modinfo_location {
    Elf64_Shdr section;
    size_t header_offset, magic_offset, magic_bytes;
};

static int component_modinfo(const unsigned char *module, size_t bytes, struct msu_modinfo_location *result)
{
    static const char signature[] = "~Module signature appended~\n";
    if (!module || bytes < sizeof(Elf64_Ehdr)) return -EINVAL;
    if (bytes >= sizeof(signature) - 1 &&
        !memcmp(module + bytes - sizeof(signature) + 1, signature, sizeof(signature) - 1)) return 0;
    Elf64_Ehdr header;
    memcpy(&header, module, sizeof(header));
    if (memcmp(header.e_ident, ELFMAG, SELFMAG) || header.e_ident[EI_CLASS] != ELFCLASS64 ||
        header.e_ident[EI_DATA] != ELFDATA2LSB || header.e_ident[EI_VERSION] != EV_CURRENT ||
        header.e_type != ET_REL || header.e_machine != EM_AARCH64 || header.e_version != EV_CURRENT ||
        header.e_ehsize != sizeof(header) || header.e_shentsize != sizeof(Elf64_Shdr)) return -EINVAL;
    Elf64_Shdr names;
    if (section_at(module, bytes, &header, header.e_shstrndx, &names) || names.sh_type != SHT_STRTAB) return -EINVAL;
    struct msu_modinfo_location location = {0};
    unsigned int found = 0;
    for (size_t index = 1; index < header.e_shnum; ++index) {
        Elf64_Shdr section;
        if (section_at(module, bytes, &header, index, &section)) return -EINVAL;
        const char *name = string_at(module, &names, section.sh_name);
        if (!name) return -EINVAL;
        if (strcmp(name, ".modinfo")) continue;
        if (found++ || section.sh_type != SHT_PROGBITS) return -EINVAL;
        location.section = section;
        location.header_offset = header.e_shoff + index * sizeof(section);
    }
    if (!found) return 0;
    unsigned int facts = 0, magic = 0;
    for (size_t at = 0; at < location.section.sh_size;) {
        const char *entry = (const char *)module + location.section.sh_offset + at;
        const char *end = memchr(entry, 0, location.section.sh_size - at);
        if (!end) return -EINVAL;
        const size_t count = (size_t)(end - entry);
        if (!strcmp(entry, "maplesu_runtime_facts=1")) ++facts;
        if (count >= 9 && !memcmp(entry, "vermagic=", 9)) {
            ++magic;
            location.magic_offset = at + 9;
            location.magic_bytes = count - 9;
        }
        at += count + 1;
    }
    if (!facts) return 0;
    if (facts != 1 || magic != 1 || !location.magic_bytes || location.magic_bytes > 255) return -EINVAL;
    *result = location;
    return 1;
}

int msu_component_vermagic(const void *module, size_t bytes, char *magic, size_t capacity)
{
    struct msu_modinfo_location location;
    const int found = component_modinfo(module, bytes, &location);
    if (found <= 0) return found;
    if (!magic || capacity <= location.magic_bytes) return -ENOSPC;
    memcpy(magic, (const unsigned char *)module + location.section.sh_offset + location.magic_offset,
           location.magic_bytes + 1);
    return 1;
}

int msu_required_vermagic(const char *record, size_t bytes, const char *current, char *required, size_t capacity)
{
    static const char prefix[] = "maplesu_ultra_bridge: version magic '";
    static const char separator[] = "' should be '";
    if (!record || !current || !required || !capacity || bytes > 65536) return -EINVAL;
    const size_t current_bytes = strlen(current);
    const char *header_end = memchr(record, ';', bytes);
    size_t at = header_end ? (size_t)(header_end - record) + 1 : 0;
    if (sizeof(prefix) - 1 > bytes - at || memcmp(record + at, prefix, sizeof(prefix) - 1)) return 0;
    {
        at += sizeof(prefix) - 1;
        if (current_bytes > bytes - at || memcmp(record + at, current, current_bytes)) return 0;
        at += current_bytes;
        if (sizeof(separator) - 1 > bytes - at || memcmp(record + at, separator, sizeof(separator) - 1)) return 0;
        at += sizeof(separator) - 1;
        const char *end = memchr(record + at, '\'', bytes - at);
        if (!end || end == record + at) return 0;
        const size_t count = (size_t)(end - record - at);
        if (count >= capacity || count > 255) return -ENOSPC;
        char candidate[256];
        for (size_t index = 0; index < count; ++index)
            if ((unsigned char)record[at + index] < 32 || (unsigned char)record[at + index] > 126) return -EINVAL;
        memcpy(candidate, record + at, count); candidate[count] = 0;
        if (!strcmp(current, candidate)) return 0;
        memcpy(required, candidate, count + 1);
        return 1;
    }
    return 0;
}

int msu_replace_component_vermagic(unsigned char **module, size_t *bytes,
    const char *current, const char *required)
{
    if (!module || !bytes || !current || !required) return -EINVAL;
    struct msu_modinfo_location location;
    const int found = component_modinfo(*module, *bytes, &location);
    if (found <= 0) return found ? found : -EINVAL;
    if (strcmp((const char *)*module + location.section.sh_offset + location.magic_offset, current)) return -ESTALE;
    const size_t length = strlen(required), alignment = location.section.sh_addralign ? location.section.sh_addralign : 1;
    if (!length || length > 255 || alignment > 65536 || (alignment & (alignment - 1))) return -EINVAL;
    const size_t new_size = location.section.sh_size - location.magic_bytes + length;
    if (*bytes > 64U * 1024U * 1024U || new_size > 1024U * 1024U) return -EFBIG;
    const size_t offset = (*bytes + alignment - 1) & ~(alignment - 1), total = offset + new_size;
    unsigned char *updated = malloc(total);
    if (!updated) return -ENOMEM;
    memcpy(updated, *module, *bytes);
    memset(updated + *bytes, 0, offset - *bytes);
    const unsigned char *previous = *module + location.section.sh_offset;
    memcpy(updated + offset, previous, location.magic_offset);
    memcpy(updated + offset + location.magic_offset, required, length);
    memcpy(updated + offset + location.magic_offset + length,
           previous + location.magic_offset + location.magic_bytes,
           location.section.sh_size - location.magic_offset - location.magic_bytes);
    location.section.sh_offset = offset; location.section.sh_size = new_size;
    memcpy(updated + location.header_offset, &location.section, sizeof(location.section));
    free(*module);
    *module = updated; *bytes = total;
    return 0;
}
