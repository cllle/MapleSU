/* SPDX-License-Identifier: GPL-3.0-only */
#ifndef MAPLESU_BOOT_SDK_ABI_H
#define MAPLESU_BOOT_SDK_ABI_H

#define MSU_BOOT_SDK_VERSION 1u
#define MSU_BOOT_SDK_OP 4u
#define MSU_BOOT_SDK_TAG_LO 0x53444b31u
#define MSU_BOOT_SDK_TAG_HI 0x4d535532u
#define MSU_BOOT_SDK_CHANNEL 1u
#define MSU_BOOT_SDK_KERNEL_BASE 2u
#define MSU_BOOT_SDK_ENVIRONMENT 4u
#define MSU_BOOT_SDK_KEY_BYTES 48u
#define MSU_BOOT_SDK_PACKET_BYTES 85u
#define MSU_BOOT_SDK_MAX_STAGE (4u * 1024u * 1024u)

enum msu_boot_sdk_command { MSU_BOOT_SDK_QUERY, MSU_BOOT_SDK_BOOTSTRAP, MSU_BOOT_SDK_CONFIGURE };
enum msu_boot_sdk_state { MSU_BOOT_SDK_EMPTY, MSU_BOOT_SDK_READY,
    MSU_BOOT_SDK_INITIALIZING, MSU_BOOT_SDK_FAILED };

/* Reply padding[3]: bounded, address-free CONFIGURE failure detail (v1). */
enum msu_boot_sdk_config_stage {
    MSU_BS_CONFIG_NONE, MSU_BS_CONFIG_LAYOUT, MSU_BS_CONFIG_ENVIRONMENT,
    MSU_BS_CONFIG_ALLOCATOR, MSU_BS_CONFIG_IMAGE_READ, MSU_BS_CONFIG_IMAGE_HEADER,
    MSU_BS_CONFIG_KALLSYMS, MSU_BS_CONFIG_CREDENTIAL, MSU_BS_CONFIG_ENV_SYMBOLS,
    MSU_BS_CONFIG_PROBE, MSU_BS_CONFIG_PROBE_ENTRY, MSU_BS_CONFIG_ALLOCATE,
    MSU_BS_CONFIG_CAPTURE, MSU_BS_CONFIG_ARGUMENTS
};

/* Root-only control reply. SDK requests retain the pinned 48+18+18 execve ABI. */
struct msu_boot_sdk_request {
    unsigned int version, command;
    int status;
    unsigned int state;
    unsigned long long kernel_base;
    unsigned int capabilities, reserved;
    char key[MSU_BOOT_SDK_KEY_BYTES + 1];
    unsigned char padding[7];
};

/* CONFIGURE appends this record to the unchanged v1 reply. It is resolved from
 * /sys/kernel/btf/vmlinux on every boot, before resident SDK allocation or
 * hooks. A temporary native allocator probe qualifies live call operands
 * without retaining an SDK arena. */
struct msu_boot_sdk_layout {
    unsigned int version, bytes, page_bytes;
    unsigned int task_bytes, cred_bytes, task_cred;
    unsigned int cred_suid, cred_securebits, cred_usage, cred_usage_size;
    unsigned int kprobe_bytes, kp_addr, kp_name, kp_pre, regs_pc;
    /* Allocator bits: noprof, native inner range, user sampler, segmented observer.
     * INNER | SEGMENTED selects the thirteen-argument callable as well. */
    unsigned int allocator_noprof, execmem_free, environment, seccomp;
};
struct msu_boot_sdk_configuration {
    struct msu_boot_sdk_request reply;
    struct msu_boot_sdk_layout layout;
};

#define MSU_BOOT_SDK_ALLOCATOR_NOPROF 1u
#define MSU_BOOT_SDK_ALLOCATOR_INNER 2u
#define MSU_BOOT_SDK_ALLOCATOR_USER_SAMPLE 4u
#define MSU_BOOT_SDK_ALLOCATOR_SEGMENTED 8u
/* Reply padding[6]: observer support and, independently, the INNER|SEGMENTED callable. */
#define MSU_BOOT_SDK_SEGMENTED_OBSERVER 1u
#define MSU_BOOT_SDK_SEGMENTED_CALLABLE 2u
#define MSU_BOOT_SDK_PROBE_BYTES 512u
static inline int msu_boot_sdk_extent(unsigned int at, unsigned int size, unsigned int limit)
{
    return at <= limit && size <= limit - at;
}
static inline int msu_boot_sdk_layout_valid(const struct msu_boot_sdk_layout *p, unsigned int page)
{
    return p && p->version == 1 && p->bytes == sizeof(*p) && p->page_bytes == page &&
        (page == 4096 || page == 16384 || page == 65536) &&
        p->task_bytes && p->task_bytes <= (1u << 20) && p->cred_bytes && p->cred_bytes <= (1u << 16) &&
        !(p->task_cred & 7) && msu_boot_sdk_extent(p->task_cred, 8, p->task_bytes) &&
        !(p->cred_suid & 3) && p->cred_securebits == p->cred_suid + 24 &&
        msu_boot_sdk_extent(p->cred_suid, 28, p->cred_bytes) &&
        (p->cred_usage_size == 4 || p->cred_usage_size == 8) &&
        !(p->cred_usage % p->cred_usage_size) &&
        msu_boot_sdk_extent(p->cred_usage, p->cred_usage_size, p->cred_bytes) &&
        (p->cred_usage + p->cred_usage_size <= p->cred_suid || p->cred_usage >= p->cred_suid + 28) &&
        p->kprobe_bytes && p->kprobe_bytes <= MSU_BOOT_SDK_PROBE_BYTES &&
        !(p->kp_addr & 7) && !(p->kp_name & 7) && !(p->kp_pre & 7) &&
        p->kp_addr != p->kp_name && p->kp_addr != p->kp_pre && p->kp_name != p->kp_pre &&
        msu_boot_sdk_extent(p->kp_addr, 8, p->kprobe_bytes) &&
        msu_boot_sdk_extent(p->kp_name, 8, p->kprobe_bytes) &&
        msu_boot_sdk_extent(p->kp_pre, 8, p->kprobe_bytes) &&
        p->regs_pc >= 31 * 8 && p->regs_pc < 512 && !(p->regs_pc & 7) &&
        p->allocator_noprof <= (MSU_BOOT_SDK_ALLOCATOR_NOPROF | MSU_BOOT_SDK_ALLOCATOR_INNER | MSU_BOOT_SDK_ALLOCATOR_USER_SAMPLE | MSU_BOOT_SDK_ALLOCATOR_SEGMENTED) &&
        p->execmem_free <= 1 && p->environment <= 1 &&
        msu_boot_sdk_extent(p->seccomp, 4, p->task_bytes) &&
        (!p->environment || (p->seccomp && p->seccomp <= 32767));
}

#endif
