/* Exercise the production gateway with ARM64 tagged-address copy fixtures. */
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "boot_sdk_protocol.h"
#include "request_protocol.h"
#define CHECK(x) do { if (!(x)) { fprintf(stderr, "line=%d: %s\n", __LINE__, #x); exit(1); } } while (0)
#define MSU_PH_NOCFI
typedef uintptr_t uptr;
typedef unsigned int u32;
typedef unsigned long long u64;
static u32 state = MSU_BOOT_SDK_READY, stage_busy;
static char root_key[49], original_root_key[49];
static unsigned char user_packet[85];
static const uptr user_address = 0x00007abc12345000ULL;
static uptr original_argument;
static unsigned copies, originals, credentials, executions;
static int body_fault, credential_error, busy;
static u64 receipt;
static uptr native_execve = 123;
static long copy_in(void *to, const void *from, unsigned long bytes) {
    uptr at = (uptr)from;
    ++copies;
    if (at < user_address || at - user_address > sizeof user_packet ||
        bytes > sizeof user_packet - (at - user_address) ||
        (body_fault && at != user_address)) return -14;
    memcpy(to, user_packet + (at - user_address), bytes);
    return 0;
}
static long msu_sdk_call_original(uptr function, const void *args) {
    CHECK(function == native_execve && *(const uptr *)args == original_argument);
    ++originals;
    return -123;
}
static int private_cred(void) { ++credentials; return credential_error; }
static int root_handover(void) { return 0; }
static int take(u32 *lock) { CHECK(lock == &stage_busy); *lock = !busy; return !busy; }
static void reply(u64 value) { receipt = value; }
static u64 dispatch(unsigned char *packet) {
    CHECK(packet[66] == 0x34);
    ++executions;
    return msu_sdk_le(packet + 68, 8);
}
/* All cases in this legacy fixture have sub=0 or fail before protocol dispatch. */
static long request_v2(uptr user) { (void)user; CHECK(0); return -14; }
#include "boot_sdk_user_gateway.inc"
static void reset(unsigned tag) {
    memset(root_key, 'K', 48);
    root_key[48] = 0;
    memcpy(original_root_key,root_key,sizeof root_key);
    CHECK(!msu_sdk_bind_key(root_key,4096));
    CHECK(memcmp(original_root_key,root_key,48));
    memset(user_packet, 0, sizeof user_packet);
    memcpy(user_packet, root_key, 48);
    unsigned char frame[18] = {0x34};
    msu_sdk_store(frame + 2, 0x2468135713572468ULL, 8);
    for (unsigned n = 0; n < 18; ++n) {
        user_packet[48+n] = frame[n] == 1 ? 2 : 1;
        user_packet[66+n] = frame[n] - user_packet[48+n];
    }
    original_argument = user_address | ((uptr)tag << 56);
    copies = originals = credentials = executions = 0;
    body_fault = credential_error = busy = 0;
    stage_busy = 0;
    state = MSU_BOOT_SDK_READY;
    receipt = 0;
}
int main(void) {
    const unsigned tags[] = {0, 1, 0x7f, 0xb4, 0xff};
    for (unsigned n = 0; n < sizeof tags / sizeof tags[0]; ++n) {
        reset(tags[n]);
        CHECK(sdk_execve(&original_argument) == -2);
        CHECK(receipt == 0x2468135713572468ULL && copies == 2 && !originals);
        CHECK(credentials == 1 && executions == 1 && !stage_busy);
        CHECK(!memcmp(user_packet, root_key, 48));
        reset(tags[n]); memcpy(user_packet,original_root_key,48);
        CHECK(sdk_execve(&original_argument)==-2&&copies==2&&!originals);
        CHECK(receipt==0x2468135713572468ULL&&credentials==1&&executions==1&&!stage_busy);
        CHECK(!memcmp(user_packet,original_root_key,48));
        reset(tags[n]); memcpy(user_packet,original_root_key,48);user_packet[84]=1;
        CHECK(sdk_execve(&original_argument)==-22&&!credentials&&!executions);
        reset(tags[n]); user_packet[0] ^= 1;
        CHECK(sdk_execve(&original_argument) == -123);
        CHECK(originals == 1 && !credentials && !executions);
        reset(tags[n]); body_fault = 1;
        CHECK(sdk_execve(&original_argument) == -14);
        CHECK(!originals && !credentials && !executions);
        reset(tags[n]); user_packet[84] = 1;
        CHECK(sdk_execve(&original_argument) == -22);
        CHECK(!credentials && !executions);
    }
    reset(0xb4); state = MSU_BOOT_SDK_EMPTY;
    CHECK(sdk_execve(&original_argument) == -123 && !copies && originals == 1);
    reset(0xb4); original_argument |= 1ULL << 55;
    CHECK(sdk_execve(&original_argument) == -123 && originals == 1 && !credentials);
    reset(0xb4); credential_error = -12;
    CHECK(sdk_execve(&original_argument) == -12 && !executions);
    reset(0xb4); busy = 1;
    CHECK(sdk_execve(&original_argument) == -16 && receipt == (u64)-19999 && !executions);
    puts("PASS production gateway: tagged users, native fallback and failure boundaries");
    return 0;
}
