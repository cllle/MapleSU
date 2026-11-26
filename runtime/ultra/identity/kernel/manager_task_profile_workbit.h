/* SPDX-License-Identifier: GPL-3.0-only */
#ifndef MAPLESU_MANAGER_TASK_PROFILE_WORKBIT_H
#define MAPLESU_MANAGER_TASK_PROFILE_WORKBIT_H

/* Bounded, read-only A64 dataflow over a snapshot of the live dispatcher.
 * Callers supply scratch memory; the same decoder is exercised by host tests. */
#define MSU_PROFILE_REG_UNKNOWN (-2147483647 - 1)
#define MSU_PROFILE_REG_WORK (MSU_PROFILE_REG_UNKNOWN + 1)
struct msu_profile_work_program {
    const u32 *code;
    const s32 *jump_targets;
    unsigned long address, tracepoint, tracepoint_funcs, iterators[2];
    u32 words, task_work_offset;
    int initial_work_register;
};
struct msu_profile_work_state {
    s32 registers[31];
    u8 seen, queued, candidate;
};

static int msu_profile_work_index(const struct msu_profile_work_program *p,
    unsigned long address)
{
    if (address < p->address || ((address - p->address) & 3UL) ||
        (address - p->address) / 4 >= p->words) return -EPROTO;
    return (address - p->address) / 4;
}

static unsigned long msu_profile_work_branch(unsigned long pc, u32 word, u32 bits)
{
    s32 offset = bits == 26 ? (s32)(word << 6) >> 4 :
        bits == 19 ? (s32)((word >> 5 & 0x7ffffU) << 13) >> 11 :
        (s32)((word >> 5 & 0x3fffU) << 18) >> 16;
    return pc + offset;
}

static int msu_profile_work_edges(const struct msu_profile_work_program *p,
    u32 at, int edges[2])
{
    u32 word = p->code[at];
    unsigned long pc = p->address + at * 4;
    int count = 0;
    if (p->jump_targets && p->jump_targets[at] >= 0) {
        int target = p->jump_targets[at];
        if ((u32)target >= p->words || at + 1 >= p->words ||
            (word != 0xd503201fU && ((word & 0xfc000000U) != 0x14000000U ||
                msu_profile_work_branch(pc, word, 26) != p->address + target * 4))) return -EPROTO;
        /* A native static key can change after this snapshot. The live jump
         * entry binds exactly these NOP/B variants, including a disabled key. */
        edges[0] = target; edges[1] = at + 1;
        return 2;
    }
    if ((word & 0xfffffc1fU) == 0xd65f0000U ||
        (word & 0xffe0001fU) == 0xd4200000U) return 0; /* RET / BRK */
    if ((word & 0xfc000000U) == 0x14000000U) {
        edges[0] = msu_profile_work_index(p, msu_profile_work_branch(pc, word, 26));
        return edges[0] < 0 ? edges[0] : 1;
    }
    if ((word & 0xff000010U) == 0x54000000U ||
        (word & 0x7e000000U) == 0x34000000U ||
        (word & 0x7e000000U) == 0x36000000U) {
        edges[count++] = msu_profile_work_index(p, msu_profile_work_branch(pc, word,
            (word & 0x7e000000U) == 0x36000000U ? 14 : 19));
        if (edges[0] < 0) return edges[0];
    } else if (((word & 0xfe000000U) == 0xd6000000U &&
            (word & 0xfffffc1fU) != 0xd63f0000U) ||
        (word & 0xff000000U) == 0xd4000000U ||
        (word & 0xffff0000U) == 0 ||
        ((word & 0xff000000U) == 0x54000000U && (word & 0x10))) return -EOPNOTSUPP;
    if (at + 1 >= p->words) return -EPROTO;
    edges[count++] = at + 1;
    return count;
}

static bool msu_profile_work_reference(const struct msu_profile_work_program *p, u32 at)
{
    u32 word = p->code[at], next, reg;
    unsigned long pc = p->address + at * 4, target;
    s32 displacement;
    if ((word & 0xfc000000U) == 0x94000000U) {
        target = msu_profile_work_branch(pc, word, 26);
        return (p->iterators[0] && target == p->iterators[0]) ||
            (p->iterators[1] && target == p->iterators[1]);
    }
    if ((word & 0x9f000000U) != 0x10000000U &&
        (word & 0x9f000000U) != 0x90000000U) return false;
    displacement = (s32)((((word >> 5 & 0x7ffffU) << 2) | (word >> 29 & 3)) << 11) >> 11;
    if (!(word & 0x80000000U)) return pc + displacement == p->tracepoint ||
        (p->tracepoint_funcs && pc + displacement == p->tracepoint_funcs);
    if (at + 1 >= p->words) return false;
    next = p->code[at + 1];
    reg = word & 31;
    target = (pc & ~4095UL) + (long)displacement * 4096;
    if ((next & 0xffc003ffU) == (0x91000000U | reg << 5 | reg)) {
        target += next >> 10 & 4095;
        return target == p->tracepoint || (p->tracepoint_funcs && target == p->tracepoint_funcs);
    }
    /* Clang also materializes the callback array with ADRP + LDR X directly
     * from tracepoint.funcs, without an intermediate ADD or LDAR. */
    if ((next & 0xffc003e0U) == (0xf9400000U | reg << 5)) {
        target += (next >> 10 & 4095) * 8;
        return p->tracepoint_funcs && target == p->tracepoint_funcs;
    }
    return false;
}

static int msu_profile_work_reach(const struct msu_profile_work_program *p,
    u32 from, u32 stop, u8 *seen, u32 *pending)
{
    u32 count = 0, at;
    int edges[2], n, i;
    memset(seen, 0, p->words);
    if (from != stop) { seen[from] = 1; pending[count++] = from; }
    while (count) {
        at = pending[--count];
        n = msu_profile_work_edges(p, at, edges);
        if (n < 0) return n;
        for (i = 0; i < n; ++i) {
            if ((u32)edges[i] == stop || seen[edges[i]]) continue;
            seen[edges[i]] = 1;
            pending[count++] = edges[i];
        }
    }
    return 0;
}

static int msu_profile_work_decode(const struct msu_profile_work_program *p,
    struct msu_profile_work_state *states, u32 *pending, u8 *set, u8 *clear,
    u32 *output)
{
    s32 registers[31];
    u32 count = 0, iterations = 0, at, word, rd, rn, rm, i, j, bit, selected = ~0U;
    int edges[2], n, error;
    bool changed;
    if (!p->words || p->words > 4096 || p->initial_work_register >= 31 ||
        p->initial_work_register < -1) return -EINVAL;
    memset(states, 0, sizeof(*states) * p->words);
    for (i = 0; i < 31; ++i) states[0].registers[i] = MSU_PROFILE_REG_UNKNOWN;
    if (p->initial_work_register >= 0)
        states[0].registers[p->initial_work_register] = MSU_PROFILE_REG_WORK;
    states[0].seen = states[0].queued = 1;
    pending[count++] = 0;
    while (count) {
        if (++iterations > 131072) return -E2BIG;
        at = pending[--count];
        states[at].queued = 0;
        states[at].candidate = 0;
        memcpy(registers, states[at].registers, sizeof(registers));
        word = p->code[at]; rd = word & 31; rn = word >> 5 & 31; rm = word >> 16 & 31;
        n = msu_profile_work_edges(p, at, edges);
        if (n < 0) return n;
        if ((word & 0xffffffe0U) == 0xd5384100U) { /* MRS SP_EL0 */
            if (rd < 31) registers[rd] = 0;
        } else if ((word & 0xff000000U) == 0x91000000U) {
            if (rd < 31) {
                u32 delta = (word >> 10 & 4095) << ((word & (1U << 22)) ? 12 : 0);
                registers[rd] = rn < 31 && registers[rn] >= 0 &&
                    delta <= 65536 && (u32)registers[rn] <= 65536 - delta ?
                    registers[rn] + delta : MSU_PROFILE_REG_UNKNOWN;
            }
        } else if ((word & 0xffe0ffe0U) == 0xaa0003e0U) {
            if (rd < 31) registers[rd] = rm < 31 ? registers[rm] : MSU_PROFILE_REG_UNKNOWN;
        } else if ((word & 0xffc00000U) == 0xf9400000U) {
            if (rd < 31) registers[rd] = rn < 31 && registers[rn] >= 0 &&
                registers[rn] + (word >> 10 & 4095) * 8 == p->task_work_offset ?
                MSU_PROFILE_REG_WORK : MSU_PROFILE_REG_UNKNOWN;
        } else if ((word & 0xfffffc00U) == 0xc8dffc00U ||
            (word & 0xfffffc00U) == 0xf8bfc000U) { /* LDAR / LDAPR X */
            if (rd < 31) registers[rd] = rn < 31 && registers[rn] >= 0 &&
                (u32)registers[rn] == p->task_work_offset ?
                MSU_PROFILE_REG_WORK : MSU_PROFILE_REG_UNKNOWN;
        } else if ((word & 0xfffffc00U) == 0x88dffc00U ||
            (word & 0xfffffc00U) == 0xb8bfc000U) { /* LDAR / LDAPR W */
            if (rd < 31) registers[rd] = MSU_PROFILE_REG_UNKNOWN;
        } else if ((word & 0x7e000000U) == 0x36000000U) {
            bit = (word >> 19 & 31) | (word >> 26 & 32);
            if (rd < 31 && registers[rd] == MSU_PROFILE_REG_WORK && bit < 63)
                states[at].candidate = bit + 1;
        } else if ((word & 0xfc000000U) == 0x94000000U ||
            (word & 0xfffffc1fU) == 0xd63f0000U) {
            for (i = 0; i <= 18; ++i) registers[i] = MSU_PROFILE_REG_UNKNOWN;
        } else if ((word & 0x7c000000U) == 0x14000000U ||
            (word & 0xff000010U) == 0x54000000U ||
            (word & 0x7e000000U) == 0x34000000U || !n ||
            (word & 0xfffff01fU) == 0xd503201fU ||
            (word & 0xfff00000U) == 0xd5100000U) {
            /* Branches, native hints, and system-register writes preserve GPRs. */
        } else if ((word & 0x3b000000U) == 0x39000000U) {
            if ((word >> 22 & 3) && rd < 31) registers[rd] = MSU_PROFILE_REG_UNKNOWN;
        } else if ((word & 0x3b200c00U) == 0x38200800U) {
            if ((word >> 22 & 3) && rd < 31) registers[rd] = MSU_PROFILE_REG_UNKNOWN;
        } else if ((word & 0x3b200000U) == 0x38000000U) {
            if ((word >> 22 & 3) && rd < 31) registers[rd] = MSU_PROFILE_REG_UNKNOWN;
            i = word >> 10 & 3;
            if ((i == 1 || i == 3) && rn < 31) registers[rn] = MSU_PROFILE_REG_UNKNOWN;
        } else if ((word & 0x3e000000U) == 0x28000000U) {
            if (word & (1U << 22)) {
                if (rd < 31) registers[rd] = MSU_PROFILE_REG_UNKNOWN;
                i = word >> 10 & 31;
                if (i < 31) registers[i] = MSU_PROFILE_REG_UNKNOWN;
            }
            i = word >> 23 & 3;
            if ((i == 1 || i == 3) && rn < 31) registers[rn] = MSU_PROFILE_REG_UNKNOWN;
        } else if ((word & 0x1c000000U) == 0x10000000U ||
            (word & 0x0e000000U) == 0x0a000000U ||
            (word & 0xfff00000U) == 0xd5300000U) {
            if (rd < 31) registers[rd] = MSU_PROFILE_REG_UNKNOWN;
        } else {
            for (i = 0; i < 31; ++i) registers[i] = MSU_PROFILE_REG_UNKNOWN;
        }
        for (i = 0; i < (u32)n; ++i) {
            struct msu_profile_work_state *to = &states[edges[i]];
            changed = !to->seen;
            if (!to->seen) {
                memcpy(to->registers, registers, sizeof(registers));
                to->seen = 1;
            } else for (j = 0; j < 31; ++j) {
                if (to->registers[j] == registers[j] || to->registers[j] == MSU_PROFILE_REG_UNKNOWN) continue;
                to->registers[j] = MSU_PROFILE_REG_UNKNOWN;
                changed = true;
            }
            if (changed && !to->queued) { to->queued = 1; pending[count++] = edges[i]; }
        }
    }
    for (at = 0; at < p->words; ++at) {
        if (!states[at].candidate) continue;
        word = p->code[at];
        n = msu_profile_work_edges(p, at, edges);
        if (n != 2) return -EPROTO;
        /* TBNZ: branch is the set edge. TBZ: fallthrough is the set edge. */
        error = msu_profile_work_reach(p, edges[(word >> 24 & 1) ? 0 : 1], at, set, pending);
        if (!error) error = msu_profile_work_reach(p, edges[(word >> 24 & 1) ? 1 : 0], at, clear, pending);
        if (error) return error;
        for (i = 0; i < p->words; ++i) {
            if (!set[i] || clear[i] || !msu_profile_work_reference(p, i)) continue;
            bit = states[at].candidate - 1;
            if (selected != ~0U && bit != selected) return -EPROTO;
            selected = bit;
        }
    }
    if (selected == ~0U) return -ENOENT;
    *output = selected;
    return 0;
}
#endif
