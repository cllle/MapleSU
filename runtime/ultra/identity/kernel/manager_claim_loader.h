// SPDX-License-Identifier: GPL-3.0-only
#ifndef MAPLESU_MANAGER_CLAIM_LOADER_H
#define MAPLESU_MANAGER_CLAIM_LOADER_H

#ifdef __KERNEL__
#include <linux/types.h>
typedef u8 msu_loader_u8;
typedef u16 msu_loader_u16;
typedef u32 msu_loader_u32;
typedef u64 msu_loader_u64;
typedef unsigned long msu_loader_uptr;
#else
#include <stdint.h>
typedef uint8_t msu_loader_u8;
typedef uint16_t msu_loader_u16;
typedef uint32_t msu_loader_u32;
typedef uint64_t msu_loader_u64;
typedef uintptr_t msu_loader_uptr;
#endif

#define MSU_LOADER_MAGIC 0x4355534dU
#define MSU_LOADER_VERSION 3U
#define MSU_LOADER_MAX_SECTIONS 32U
#define MSU_LOADER_MAX_CHUNKS 256U
#define MSU_LOADER_MAX_MEMORY_BYTES 1048576U
/* Each import is used by a relocation. Unique, aligned 8-byte destination
 * slots bound both counts; descriptor bytes impose their own tighter bound.
 * The materializer walks immutable records and has no count-sized buffer. */
#define MSU_LOADER_MAX_RELOCS (MSU_LOADER_MAX_MEMORY_BYTES / 8U)
#define MSU_LOADER_MAX_IMPORTS MSU_LOADER_MAX_RELOCS
#define MSU_LOADER_READ 1U
#define MSU_LOADER_WRITE 2U
#define MSU_LOADER_EXEC 4U
#define MSU_LOADER_RELOC_RELATIVE 1U
#define MSU_LOADER_RELOC_KERNEL_IMPORT 2U

/* Little-endian boot-owned records. Offsets and imports are generated from the
 * same bound kernel image; no caller-supplied symbols or relocation data. */
struct msu_loader_binding {
    msu_loader_u32 image_bytes, descriptor_offset, descriptor_bytes, descriptor_crc;
    msu_loader_u32 loader_offset, loader_bytes;
};
struct msu_loader_header {
    msu_loader_u32 magic, version, total_bytes, image_bytes, memory_bytes, entry_offset;
    msu_loader_u32 section_count, chunk_count, import_count, reloc_count, payload_crc, kernel_memory_bytes;
    msu_loader_u32 chunk_table_offset;
};
struct msu_loader_section {
    msu_loader_u32 offset, file_bytes, memory_bytes, flags;
};
struct msu_loader_chunk {
    msu_loader_u32 source, length;
};
struct msu_loader_reloc {
    msu_loader_u32 target, kind, value, reserved;
};

_Static_assert(sizeof(struct msu_loader_binding) == 24, "loader binding layout");
_Static_assert(sizeof(struct msu_loader_header) == 52, "loader header layout");
_Static_assert(sizeof(struct msu_loader_section) == 16, "loader section layout");
_Static_assert(sizeof(struct msu_loader_chunk) == 8, "loader chunk layout");
_Static_assert(sizeof(struct msu_loader_reloc) == 16, "loader relocation layout");
_Static_assert(sizeof(msu_loader_uptr) == sizeof(void *), "loader pointer layout");

enum msu_loader_error {
    MSU_LOADER_INVALID_BINDING = -1,
    MSU_LOADER_INVALID_HEADER = -2,
    MSU_LOADER_INVALID_DESCRIPTOR_SIZE = -3,
    MSU_LOADER_INVALID_SECTION = -4,
    MSU_LOADER_INVALID_ENTRY = -5,
    MSU_LOADER_INVALID_CHUNK = -6,
    MSU_LOADER_INVALID_PAYLOAD = -7,
    MSU_LOADER_INVALID_IMPORT = -8,
    MSU_LOADER_INVALID_RELOCATION = -9,
    MSU_LOADER_INVALID_CHUNK_TABLE = -10,
};

/* Internal materializer: binding and image must be immutable, retained objects
 * supplied by the protected boot-owned anchor. CRC detects corruption and does
 * not authenticate those objects. No userspace pointer or request may supply
 * binding, image, imports, relocations, or image identity. Protocol v3 binds a
 * separate kernel memory extent for imports, including target-BTF-verified BSS
 * objects. Functions remain file-bound at generation; all source reads remain
 * image_bytes-bound here. Versions 1 and 2 are rejected. The chunk table is
 * separate. descriptor_crc covers the descriptor bytes
 * followed by the entire chunk table in that order. Chunk destinations are
 * uniquely derived from the ordered sections and validated file-byte cursor.
 *
 * The caller owns a separate writable NX destination for the entire operation.
 * All validation finishes before any destination write. A negative error leaves
 * it unchanged; success returns entry offset + 1. This function neither
 * authenticates a manager nor allocates, publishes RX, executes, or grants root. */
int msu_manager_claim_materialize(const struct msu_loader_binding *binding,
                                  const void *image, void *destination,
                                  msu_loader_u32 capacity);

#endif
