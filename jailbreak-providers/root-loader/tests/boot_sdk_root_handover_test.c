/* Production gateway/L3 functions, with refcounted kernel credential fixtures. */
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "boot_sdk_protocol.h"
#include "request_protocol.h"
#define CHECK(x) do { if (!(x)) { fprintf(stderr, "line=%d: %s\n", __LINE__, #x); exit(1); } } while (0)
#define MSU_PH_NOCFI
#define MAPLESU_NOCFI
#define MAPLESU_ENOMEM 12
#define MAPLESU_EPERM 1
#define MAPLESU_L3_BOOT_SDK 1

typedef uintptr_t uptr;
typedef unsigned int u32;
typedef unsigned long long u64;
struct groups { unsigned refs; };
struct cred {
    unsigned usage, published;
    u32 uid, euid, gid, egid, suid, sgid, sid;
    struct groups *groups;
};
struct task { u64 flags; u32 mode, count; uptr filter, cred; };
#define MAPLESU_TASK_CRED_OFF offsetof(struct task, cred)
#define MAPLESU_CRED_UID_OFF offsetof(struct cred, uid)
#define MAPLESU_CRED_EUID_OFF offsetof(struct cred, euid)
#define MAPLESU_CRED_GROUPS_OFF offsetof(struct cred, groups)
#define MAPLESU_TASK_SECCOMP_MODE_OFF offsetof(struct task, mode)
#define MAPLESU_TASK_THREAD_FLAGS_OFF offsetof(struct task, flags)
#define MAPLESU_TIF_SECCOMP_MASK (1ULL << 11)
#define MSU_BS_CRED_SUID offsetof(struct cred, suid)
static struct groups app_groups, marker, replacement;
static struct cred parent_cred, allocated[32];
static struct task child;
static u32 maplesu_ready = 1, state = MSU_BOOT_SDK_READY, stage_busy;
static uptr maplesu_groups, native_execve = 123;
static unsigned prepares, commits, allocations, group_changes, marked_commits;
static unsigned fail_prepare, dispatches, originals;
static char root_key[49], original_root_key[49];
static unsigned char user_packet[85];
static const uptr user_address = 0x12345000;
static const u64 expected_reply = 0x2468135713572468ULL;
static const u64 other_flags = (1ULL << 3) | (1ULL << 35);
static const char *scenario;
static struct cred *jit_cred;
static uptr maplesu_current_task(void) { return (uptr)&child; }
static uptr current_cred(void) { return child.cred; }
#define sdk_current_cred current_cred
static long request_v2(uptr user){(void)user;CHECK(0);return -14;}
static void drop_task_cred(struct cred *cred) {
    CHECK(cred->usage >= 2);
    cred->usage -= 2;
    if (!cred->usage) {
        CHECK(cred->groups->refs);
        --cred->groups->refs;
    }
}
static void *fixture_prepare(void) {
    ++prepares;
    if (prepares == fail_prepare) return NULL;
    CHECK(allocations < sizeof allocated / sizeof allocated[0]);
    struct cred *cred = &allocated[allocations++];
    *cred = *(struct cred *)child.cred;
    cred->usage = 1;
    cred->published = 0;
    ++cred->groups->refs;
    return cred;
}
static void fixture_set_groups(void *at, void *groups) {
    struct cred *cred = at;
    struct groups *next = groups;
    CHECK(!cred->published && cred->usage == 1);
    CHECK(cred->groups->refs && next);
    --cred->groups->refs;
    ++next->refs;
    cred->groups = next;
    ++group_changes;
}
static int fixture_commit(void *at) {
    struct cred *cred = at, *previous = (void *)child.cred;
    CHECK(!cred->published && cred->usage == 1);
    if (cred->groups == &marker && previous->groups != &marker) {
        CHECK(child.mode == 2 && (child.flags & MAPLESU_TIF_SECCOMP_MASK));
        ++marked_commits;
    }
    cred->usage = 2;
    cred->published = 1;
    child.cred = (uptr)cred;
    drop_task_cred(previous);
    ++commits;
    return 0;
}
static void *(*prepare_cred)(void) = fixture_prepare;
static int (*commit_cred)(void *) = fixture_commit;
static void *(*maplesu_prepare_creds)(void) __attribute__((unused)) = fixture_prepare;
static int (*maplesu_commit)(void *) __attribute__((unused)) = fixture_commit;
static void (*maplesu_set_groups)(void *, void *) __attribute__((unused)) = fixture_set_groups;
static int maplesu_sdk_root_handover(void);
static int (*root_handover)(void) __attribute__((unused)) = maplesu_sdk_root_handover;
static long copy_in(void *to, const void *from, unsigned long bytes) {
    uptr at = (uptr)from;
    CHECK(at >= user_address && at - user_address <= sizeof user_packet);
    CHECK(bytes <= sizeof user_packet - (at - user_address));
    memcpy(to, user_packet + at - user_address, bytes);
    return 0;
}
static long msu_sdk_call_original(uptr function, const void *args) {
    CHECK(function == native_execve && *(const uptr *)args == user_address);
    ++originals;
    return -123;
}
static int take(u32 *lock) { CHECK(!*lock); *lock = 1; return 1; }
static u64 dispatch(unsigned char *packet) {
    struct cred *cred = (void *)child.cred;
    ++dispatches;
    CHECK(packet[66] == 0x33 || packet[66] == 0x34);
    if (!strcmp(scenario, "jit-commit")) {
        cred = fixture_prepare();
        CHECK(cred);
        cred->uid = cred->euid = 0;
        fixture_commit(cred);
        jit_cred = cred;
    } else if (strcmp(scenario, "query")) {
        if (strcmp(scenario, "euid-only")) cred->uid = 0;
        if (strcmp(scenario, "uid-only")) cred->euid = 0;
    }
    return expected_reply;
}
#include "boot_sdk_root_handover.inc"
static long request(void) {
    uptr argument = user_address;
    return sdk_execve(&argument);
}
static void packet(void) {
    memset(user_packet, 0, sizeof user_packet);
    memcpy(user_packet, root_key, 48);
    unsigned char frame[18] = {0x33};
    if (!strcmp(scenario, "query")) frame[0] = 0x34;
    for (unsigned n = 0; n < 18; ++n) {
        user_packet[48+n] = frame[n] == 1 ? 2 : 1;
        user_packet[66+n] = frame[n] - user_packet[48+n];
    }
}
static void unchanged_parent(void) {
    CHECK(parent_cred.uid == 10155 && parent_cred.euid == 10155);
    CHECK(parent_cred.suid == 10155 && parent_cred.sgid == 10155);
    CHECK(parent_cred.sid == 0xabcdef && parent_cred.groups == &app_groups);
    CHECK(parent_cred.usage == 2 && app_groups.refs == 1);
}
static void root_result(void) {
    struct cred *cred = (void *)child.cred;
    CHECK(maplesu_root_subject((uptr)&child));
    CHECK(cred->usage == 2 && cred->groups == &marker && marker.refs == 2);
    CHECK(cred->sid == 0xabcdef);
    CHECK(msu_sdk_le((unsigned char *)cred + MSU_BS_CRED_SUID, 8) == expected_reply);
    CHECK(child.mode == 0 && child.flags == other_flags);
    CHECK(child.count == 3 && child.filter == 0x12340);
    CHECK(!stage_busy);
}
int main(int argc, char **argv) {
    CHECK(argc == 2);
    scenario = argv[1];
    app_groups.refs = marker.refs = 1;
    maplesu_groups = (uptr)&marker;
    parent_cred = (struct cred){4, 1, 10155, 10155, 10155, 10155, 10155, 10155, 0xabcdef, &app_groups};
    child = (struct task){other_flags | MAPLESU_TIF_SECCOMP_MASK, 2, 3, 0x12340, (uptr)&parent_cred};
    memset(root_key, 'K', 48);
    packet();
    if (!strcmp(scenario, "bad-key")) user_packet[0] ^= 1;
    if (!strcmp(scenario, "not-ready")) state = MSU_BOOT_SDK_EMPTY;
    if (!strcmp(scenario, "private-fail")) fail_prepare = 1;
    if (!strcmp(scenario, "handover-fail")) fail_prepare = 2;
    if (!strcmp(scenario, "marked") || !strcmp(scenario, "inherit")) {
        parent_cred.uid = parent_cred.euid = 0;
        parent_cred.groups = &marker;
        app_groups.refs = 0;
        marker.refs = 2; /* module + shared parent credential */
        CHECK(request() == -2);
        CHECK(prepares == 1 && group_changes == 0 && marked_commits == 0);
        CHECK(parent_cred.usage == 2 && marker.refs == 3);
        CHECK(maplesu_root_subject((uptr)&child) && child.mode == 0);
        if (!strcmp(scenario, "inherit")) {
            struct task forked = child;
            ((struct cred *)forked.cred)->usage += 2;
            CHECK(maplesu_root_subject((uptr)&forked));
            drop_task_cred((void *)forked.cred);
        }
    } else if (!strcmp(scenario, "bad-key") || !strcmp(scenario, "not-ready")) {
        CHECK(request() == -123);
        CHECK(originals == 1 && !prepares && !dispatches && !group_changes);
        CHECK(child.cred == (uptr)&parent_cred && parent_cred.usage == 4);
        CHECK(child.mode == 2 && (child.flags & MAPLESU_TIF_SECCOMP_MASK));
    } else if (!strcmp(scenario, "private-fail")) {
        CHECK(request() == -12);
        CHECK(!dispatches && !commits && !group_changes);
        CHECK(child.cred == (uptr)&parent_cred && child.mode == 2);
    } else if (!strcmp(scenario, "handover-fail")) {
        CHECK(request() == -12);
        CHECK(prepares == 2 && commits == 1 && !group_changes);
        CHECK(msu_sdk_le((unsigned char *)child.cred + MSU_BS_CRED_SUID, 8) == (u64)-19999);
        CHECK(!maplesu_root_subject((uptr)&child) && child.mode == 2);
        CHECK(child.flags == (other_flags | MAPLESU_TIF_SECCOMP_MASK));
        CHECK(child.count == 3 && child.filter == 0x12340 && !stage_busy);
    } else if (!strcmp(scenario, "query") || !strcmp(scenario, "uid-only") || !strcmp(scenario, "euid-only")) {
        CHECK(request() == -2);
        CHECK(prepares == 1 && commits == 1 && !group_changes);
        CHECK(!maplesu_root_subject((uptr)&child) && child.mode == 2);
        CHECK(child.flags == (other_flags | MAPLESU_TIF_SECCOMP_MASK));
        CHECK(msu_sdk_le((unsigned char *)child.cred + MSU_BS_CRED_SUID, 8) == expected_reply);
    } else {
        CHECK(request() == -2);
        root_result();
        unchanged_parent();
        CHECK(group_changes == 1 && marked_commits == 1);
        CHECK(prepares == (!strcmp(scenario, "jit-commit") ? 3u : 2u));
        CHECK(allocated[0].suid == 10155 && allocated[0].sgid == 10155);
        if (jit_cred) CHECK(jit_cred->suid == 10155 && jit_cred->sgid == 10155);
        if (!strcmp(scenario, "setgroups")) {
            struct cred *next = fixture_prepare();
            fixture_set_groups(next, &replacement);
            fixture_commit(next);
            CHECK(!maplesu_root_subject((uptr)&child) && marker.refs == 1);
            child.mode = 2;
            child.flags |= MAPLESU_TIF_SECCOMP_MASK;
            CHECK(request() == -2);
            root_result();
            CHECK(replacement.refs == 0);
        }
    }
    puts("PASS production SDK/L3 credential handover");
    return 0;
}
