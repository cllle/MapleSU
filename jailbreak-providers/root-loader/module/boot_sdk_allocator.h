/* SPDX-License-Identifier: GPL-3.0-only */
#ifndef MAPLESU_BOOT_SDK_ALLOCATOR_H
#define MAPLESU_BOOT_SDK_ALLOCATOR_H
/* Decode only a complete, known call sequence. Operands are live values; an
 * unrecognized instruction, callee or condition rejects before allocation. */
struct msu_sdk_vm {
    unsigned long image_bytes, start, end, align, gfp, prot, prot_set, flag;
};
static int msu_sdk_logical(unsigned int word, unsigned long *out)
{
    unsigned int top = ((word >> 22 & 1) << 6) | (~(word >> 10) & 63);
    unsigned int len = 0, width, ones, rotation, at;
    unsigned long mask, value;
    while (top >>= 1) ++len;
    if (len < 1 || len > 6) return -1;
    width = 1u << len; ones = (word >> 10 & 63) & (width - 1);
    rotation = (word >> 16 & 63) & (width - 1);
    if (ones == width - 1) return -1;
    mask = width == 64 ? ~0UL : (1UL << width) - 1;
    value = (1UL << (ones + 1)) - 1;
    if (rotation) value = ((value >> rotation) | (value << (width - rotation))) & mask;
    *out = 0;
    for (at = 0; at < 64; at += width) *out |= value << at;
    return 0;
}
static inline int msu_sdk_vmalloc(const unsigned char *code, unsigned long address,
    unsigned long target, unsigned long base, unsigned long image_bytes, unsigned int profiled,
    struct msu_sdk_vm *result)
{
    unsigned int w[30], branch, adrp, flag, start, end, sm, em, align, gfp, prot, ph, ps, i;
    unsigned long extra, high, flag_address;
    long immediate, delta;
    for (i = 0; i < 30; ++i) w[i] = (unsigned int)msu_sdk_le(code + i * 4, 4);
    /* Native dynamic SCS may rewrite the architectural PAC entry/exit hints. */
    if (w[0] == 0xf800865e) w[0] = 0xd503233f;
    if (w[24] == 0xf85f8e5e) w[24] = 0xd50323bf;
    if (w[28] == 0xf85f8e5e) w[28] = 0xd50323bf;
#define FIX(i, value) do { if (w[i] != value##u) return -1; } while (0)
#define MASK(i, mask, value) do { if ((w[i] & mask##u) != value##u) return -1; } while (0)
    FIX(0,0xd503233f);
    if (profiled) {
        FIX(1,0xd10103ff); FIX(2,0xa9037bfd); FIX(3,0x9100c3fd); FIX(4,0xd5384108);
        MASK(5,0xffc003ff,0xf9400108); FIX(6,0xf81f83a8); FIX(7,0xd50320ff);
        FIX(8,0xf81f03bf); FIX(9,0xd503201f); FIX(19,0x7100011f);
        FIX(22,0x9a891145); FIX(23,0xaa1f03e6); FIX(24,0x12800007); FIX(25,0xf90003fe);
        branch=26; adrp=10; flag=13; start=12; end=15; sm=18; em=20;
        align=17; gfp=21; prot=11; ph=14; ps=16;
    } else {
        FIX(1,0xd10083ff); FIX(2,0xa9017bfd); FIX(3,0x910043fd);
        if ((w[4] & 0x9f00001fu) == 0x90000008u) {
            FIX(5,0xd50320ff); FIX(7,0xb25963c9); FIX(8,0x9249bfca); FIX(9,0xf24903df);
            FIX(12,0x9a890149); FIX(16,0x7100011f); FIX(18,0x9a8a0165);
            FIX(22,0xaa1f03e6); FIX(23,0x12800007); FIX(24,0xf90003e9);
            FIX(26,0xa9417bfd); FIX(27,0x910083ff); FIX(28,0xd50323bf); FIX(29,0xd65f03c0);
            branch=25; adrp=4; flag=10; start=14; end=15; sm=19; em=20;
            align=17; gfp=21; prot=6; ph=11; ps=13;
        } else {
            FIX(4,0xd50320ff); FIX(13,0x7100011f); FIX(16,0x9a8a0125);
            FIX(18,0xaa1f03e6); FIX(19,0x12800007); FIX(20,0xf90003fe);
            FIX(22,0xa9417bfd); FIX(23,0x910083ff); FIX(24,0xd50323bf); FIX(25,0xd65f03c0);
            branch=21; adrp=5; flag=7; start=9; end=11; sm=14; em=15;
            align=12; gfp=17; prot=6; ph=8; ps=10;
        }
    }
    MASK(branch,0xfc000000,0x94000000); MASK(adrp,0x9f00001f,0x90000008);
    MASK(flag,0xffc003ff,0x39400108); MASK(start,0xff8003ff,0xb20003e2);
    MASK(end,0xff8003ff,0xb20003e3); MASK(sm,0xffe0001f,0xf2a00002);
#undef FIX
#undef MASK
    /* MOVK end uses LSL #16 in the profiled ABI and LSL #32 otherwise. */
    if ((w[em] & 0xffe0001fu) != (profiled ? 0xf2a00003u : 0xf2c00003u) ||
        (w[align] & 0xffe0001fu) != 0x52800001u || (w[gfp] & 0xffe0001fu) != 0x52800004u ||
        (w[prot] & 0xffe0001fu) != (profiled || branch==21 ? 0xd2800009u : 0xd280000bu) ||
        (w[ph] & 0xffe0001fu) != (profiled || branch==21 ? 0xf2e00009u : 0xf2e0000bu) ||
        (w[ps] & 0xff8003ffu) != (profiled || branch==21 ? 0xb200012au : 0xb200016au)) return -1;
    delta = (long)(w[branch] & 0x3ffffffu);
    if (delta & 0x2000000) delta -= 0x4000000;
    if (address + branch * 4 + delta * 4 != target) return -1;
    immediate = (long)(((w[adrp] >> 5 & 0x7ffff) << 2) | (w[adrp] >> 29 & 3));
    if (immediate & 0x100000) immediate -= 0x200000;
    flag_address = ((address + adrp * 4) & ~4095UL) + immediate * 4096 + (w[flag] >> 10 & 4095);
    if (msu_sdk_logical(w[start], &extra) || msu_sdk_logical(w[end], &high)) return -1;
    result->start = (extra & ~(0xffffUL << 16)) | (unsigned long)(w[sm] >> 5 & 0xffff) << 16;
    result->end = (high & ~(0xffffUL << (profiled ? 16 : 32))) |
        (unsigned long)(w[em] >> 5 & 0xffff) << (profiled ? 16 : 32);
    result->prot = (w[prot] >> 5 & 0xffff) | (unsigned long)(w[ph] >> 5 & 0xffff) << 48;
    if (msu_sdk_logical(w[ps], &extra)) return -1;
    result->prot_set = result->prot | extra;
    result->align = w[align] >> 5 & 0xffff; result->gfp = w[gfp] >> 5 & 0xffff;
    result->image_bytes = image_bytes; result->flag = flag_address;
    return !image_bytes || image_bytes > (1UL << 32) || flag_address <= base ||
        flag_address - base >= image_bytes || result->start <= (1UL << 63) ||
        result->end <= result->start || !result->align || (result->align & (result->align - 1)) ? -1 : 0;
}
#endif
