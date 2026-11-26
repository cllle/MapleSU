/* SPDX-License-Identifier: GPL-3.0-only */
#ifndef MAPLESU_BOOT_SDK_PROTOCOL_H
#define MAPLESU_BOOT_SDK_PROTOCOL_H
#include "../boot_sdk_abi.h"

static unsigned long long msu_sdk_le(const unsigned char *p, unsigned int bytes)
{
    unsigned long long value = 0;
    unsigned int n;
    for (n = 0; n < bytes; ++n) value |= (unsigned long long)p[n] << (n * 8);
    return value;
}
static void msu_sdk_store(unsigned char *p, unsigned long long value, unsigned int bytes)
{
    unsigned int n;
    for (n = 0; n < bytes; ++n) p[n] = (unsigned char)(value >> (n * 8));
}
/* Decode in a private copy; never modify the syscall's userspace filename. */
static int msu_sdk_decode(unsigned char *packet, const char *key)
{
    unsigned int n, difference = 0;
    for (n = 0; n < MSU_BOOT_SDK_KEY_BYTES; ++n)
        difference |= packet[n] ^ (unsigned char)key[n];
    if (difference) return 0;
    if (packet[84]) return -1;
    for (n = 0; n < 18; ++n) packet[66 + n] += packet[48 + n];
    return packet[66] >= 0x30 && packet[66] <= 0x35 ? 1 : -1;
}
static int msu_sdk_bind_key(char *key, long displacement)
{
    static const char alphabet[] = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    unsigned int digits[8], count = 0, n, seed = 0, first = 0;
    unsigned long magnitude;
    if (!displacement || (displacement & 3) || displacement < -134217728L ||
        displacement > 134217728L) return -1;
    magnitude = displacement < 0 ? (unsigned long)-displacement : (unsigned long)displacement;
    do { digits[count++] = magnitude % 62; magnitude /= 62; } while (magnitude);
    while (alphabet[seed] != key[16] && seed < 62) ++seed;
    while (alphabet[first] != key[0] && first < 62) ++first;
    if (seed == 62 || first == 62) return -1;
    /* seed + base62 magnitude + sign; the final two bytes live in key[15], key[2]. */
    key[50 - (count + 2)] = alphabet[seed];
    for (n = 0; n <= count; ++n) {
        unsigned int digit = n < count ? digits[count - n - 1] : displacement > 0;
        unsigned int at = 50 - (count + 2) + n + 1;
        char value = alphabet[(digit + seed + n) % 62];
        if (at == 48) key[15] = value;
        else if (at == 49) key[2] = value;
        else key[at] = value;
    }
    key[1] = alphabet[(first + count + 2) % 62];
    return 0;
}
static int msu_sdk_metadata_offset(unsigned long base, unsigned long metadata,
    unsigned int *offset)
{
    if (metadata <= base || metadata - base > 0xffffffffUL) return -1;
    *offset = (unsigned int)(metadata - base);
    return 0;
}
/* __vmalloc_node_range still owns reservations and the guard page. Restrict
 * its search to the real VMALLOC range and the SDK's unsigned metadata window. */
static inline int msu_sdk_arena_range(unsigned long base, unsigned long image_bytes,
    unsigned long vm_start, unsigned long vm_end, unsigned long page,
    unsigned long bytes, unsigned long *start, unsigned long *end)
{
    unsigned long limit = ~0UL, low, high;
    if (!page || (page & (page - 1)) || !image_bytes || !bytes ||
        base > limit - image_bytes || base + image_bytes > limit - (page - 1)) return -1;
    low = (base + image_bytes + page - 1) & ~(page - 1);
    if (low < vm_start) low = vm_start;
    high = base > limit - 0xffffffffUL ? limit : base + 0xffffffffUL;
    if (high > vm_end) high = vm_end;
    high &= ~(page - 1);
    if ((low & (page - 1)) || high <= low || high - low < page ||
        bytes > high - low - page) return -1;
    *start = low; *end = high;
    return 0;
}
#endif
