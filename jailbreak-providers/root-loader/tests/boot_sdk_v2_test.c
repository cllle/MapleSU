/* The production authenticated gateway and B1 handlers, with nofault seams. */
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "boot_sdk_protocol.h"
#include "request_protocol.h"
#include "legacy_hook_private.h"
#define MSU_PH_NOCFI
#ifndef MSU_SDK_U64_MAX
#define MSU_SDK_U64_MAX UINT64_MAX
#endif
#define MSU_BS_PAGE_BYTES 4096UL
typedef uintptr_t uptr;
typedef unsigned int u32;
typedef unsigned long long u64;
static unsigned checks, failures;
#define CHECK(x) do { ++checks; if (!(x)) { ++failures; fprintf(stderr, "line %d: %s\n", __LINE__, #x); } } while (0)
static u32 state, stage_busy;
static char root_key[49], original_root_key[49];
static uptr native_execve = 123, kernel_base, arena;
static maplesu_metadata_v2 sdk_request_metadata;
static unsigned char packet[85], read_bytes[256], destination[256];
static maplesu_request_v2 request;
static maplesu_response_v2 response;
static maplesu_read_v2 read_args;
static maplesu_management_v3 management_args;
static maplesu_host_instance returned_host;
static struct maplesu_legacy_hook_provider provider;
static int provider_lease;
static unsigned provider_gets, provider_puts, provider_binds, provider_begins, provider_ends, provider_dispatches, provider_closes;
static int provider_get_error, provider_end_error, provider_status;
static u64 provider_result, provider_handle;
static u64 provider_bind_result, provider_begin_result;
static u32 provider_effects;
static int (*legacy_provider_get)(const struct maplesu_legacy_hook_provider **, void **);
static void (*legacy_provider_put)(void *);
extern unsigned char msu_boot_sdk_legacy_scope_start[], msu_boot_sdk_legacy_scope_end[];
static const uptr packet_at = 0x12340000, request_at = 0x12341000;
static const uptr response_at = 0x12342000, descriptor_at = 0x12343000, destination_at = 0x12344000;
static const uptr host_at = 0x12345000, management_at = 0x12346000;
static const uptr source_at = UINT64_C(0xffff800000100000);
static const u64 session = UINT64_C(0x8123456789abcdef);
static uptr original_argument;
static unsigned private_calls, lock_calls, legacy_calls, reply_calls, handover_calls, native_calls;
static unsigned input_calls, output_calls, kernel_calls, completion_calls, response_calls, descriptor_calls;
static int fail_input, fail_output, fail_read, private_error, busy, mutate_descriptor, mutate_request;
static u64 legacy_receipt;
static int range(uptr at, unsigned long bytes, uptr base, unsigned long extent) {
    return at >= base && at - base <= extent && bytes <= extent - (at - base);
}
static long copy_in(void *to, const void *from, unsigned long bytes) {
    const uptr at = (uptr)from;
    void *source = NULL;
    ++input_calls;
    if (range(at, bytes, packet_at, sizeof packet)) source = packet + (at - packet_at);
    if (range(at, bytes, request_at, sizeof request)) source = (unsigned char *)&request + (at - request_at);
    if (range(at, bytes, descriptor_at, sizeof read_args)) {
        source = (unsigned char *)&read_args + (at - descriptor_at); ++descriptor_calls;
    }
    if (range(at, bytes, management_at, sizeof management_args))
        source = (unsigned char *)&management_args + (at - management_at);
    if (!source) return -14;
    if (fail_input == (int)input_calls) {
        if (bytes) memcpy(to, source, bytes - 1);
        return bytes ? 1 : -14; /* a nonzero short-copy result is also failure */
    }
    memcpy(to, source, bytes);
    if (mutate_request && at == request_at) memset(&request, 0x9b, sizeof request);
    if (mutate_descriptor && at == descriptor_at) memset(&read_args, 0xff, sizeof read_args);
    return 0;
}
static long copy_out(void *to, const void *from, unsigned long bytes) {
    const uptr at = (uptr)to;
    void *target = NULL;
    ++output_calls;
    if (at == response_at && bytes == sizeof response) {
        const maplesu_response_v2 *body = from;
        ++response_calls;
        CHECK(body->completion_magic == 0 && completion_calls == 0);
        target = &response;
    } else if (at == response_at && bytes == 4) {
        ++completion_calls;
        CHECK(response_calls == 1 && response.completion_magic == 0);
        target = &response;
    } else if (range(at, bytes, destination_at, sizeof destination)) target = destination + (at - destination_at);
    else if (at == host_at && bytes == sizeof returned_host) target = &returned_host;
    if (!target) return -14;
    if (fail_output == (int)output_calls) {
        if (bytes > 1) memcpy(target, from, bytes - 1);
        return 1;
    }
    memcpy(target, from, bytes);
    return 0;
}
static long read_kernel(void *to, const void *from, unsigned long bytes) {
    ++kernel_calls;
    if (fail_read || !range((uptr)from, bytes, source_at, sizeof read_bytes)) return -14;
    memcpy(to, read_bytes + ((uptr)from - source_at), bytes);
    return 0;
}
static long msu_sdk_call_original(uptr function, const void *args) {
    CHECK(function == native_execve && *(const uptr *)args == original_argument);
    ++native_calls; return -123;
}
static int private_cred(void) { ++private_calls; return private_error; }
static int root_handover(void) { ++handover_calls; return 0; }
static int take(u32 *lock) { CHECK(lock == &stage_busy); ++lock_calls; *lock = !busy; return !busy; }
static void reply(u64 value) { ++reply_calls; legacy_receipt = value; }
static u64 dispatch(unsigned char *frame) { ++legacy_calls; return msu_sdk_le(frame + 68, 8); }
#include "boot_sdk_v2_gateway.inc"

static int provider_get(const struct maplesu_legacy_hook_provider **value, void **lease) {
    ++provider_gets; if (provider_get_error) return provider_get_error;
    *value = &provider; *lease = &provider_lease; return 0;
}
static void provider_put(void *lease) {
    CHECK(lease == &provider_lease); ++provider_puts;
}
static u64 provider_bind(u64 address, u64 bytes) {
    CHECK(address == (uptr)msu_boot_sdk_legacy_scope_start && bytes &&
        address + bytes == (uptr)msu_boot_sdk_legacy_scope_end);
    ++provider_binds; return provider_bind_result;
}
static u64 provider_begin(u64 token) { CHECK(token == provider_bind_result); ++provider_begins; return provider_begin_result; }
static int provider_end(u64 token) { CHECK(token == session); ++provider_ends; return provider_end_error; }
static int provider_dispatch(u64 token, u32 command, const maplesu_management_v3 *operation,
    maplesu_response_owner_v3 *reply_body) {
    CHECK(token == session && operation->host.low == provider.host.low &&
        operation->host.high == provider.host.high);
    ++provider_dispatches;
    if (command == MAPLESU_REQUEST_SESSION_OPEN) {
        CHECK(!operation->session && !operation->data_user && !operation->length);
    } else CHECK(operation->session == session || (command == MAPLESU_REQUEST_SESSION_CLOSE && !operation->session));
    if (command == MAPLESU_REQUEST_SESSION_CLOSE) {
        ++provider_closes; reply_body->metadata_length = MAPLESU_EFFECT_SESSION_CLOSED;
    } else {
        reply_body->status = provider_status;
        reply_body->result = provider_result;
        reply_body->metadata_address = provider_handle;
        reply_body->metadata_length = provider_effects;
    }
    return reply_body->status;
}

static void encode(unsigned sub) {
    unsigned char frame[18] = {0x34};
    frame[1] = sub;
    msu_sdk_store(frame + 2, UINT64_C(0x2468135713572468), 8);
    msu_sdk_store(frame + 10, request_at, 8);
    memcpy(packet, root_key, 48); packet[84] = 0;
    for (unsigned n = 0; n < 18; ++n) {
        packet[48+n] = frame[n] == 1 ? 2 : 1;
        packet[66+n] = frame[n] - packet[48+n];
    }
}
static void reset(unsigned command) {
    memset(root_key, 'K', 48); root_key[48] = 0;
    memset(packet, 0, sizeof packet); memset(&request, 0, sizeof request);
    memset(&response, 0, sizeof response); memset(destination, 0, sizeof destination);
    for (unsigned n = 0; n < sizeof read_bytes; ++n) read_bytes[n] = (unsigned char)(n ^ 0xb7);
    request.magic = MAPLESU_REQUEST_MAGIC; request.version = MAPLESU_REQUEST_PROTOCOL_VERSION;
    request.length = sizeof request; request.command = command;
    request.request_id = UINT64_C(0xfedcba9876543210); request.response_user = response_at;
    request.arg0 = source_at; request.arg1 = descriptor_at;
    read_args.destination_user = destination_at; read_args.length = 256; read_args.reserved = 0;
    private_calls = lock_calls = legacy_calls = reply_calls = handover_calls = native_calls = 0;
    input_calls = output_calls = kernel_calls = response_calls = completion_calls = descriptor_calls = 0;
    fail_input = fail_output = fail_read = private_error = busy = mutate_request = mutate_descriptor = 0;
    memset(&management_args, 0, sizeof management_args); memset(&returned_host, 0, sizeof returned_host);
    provider_gets = provider_puts = provider_binds = provider_begins = provider_ends = provider_dispatches = provider_closes = 0;
    provider_get_error = provider_end_error = provider_status = 0;
    provider_result = provider_handle = provider_effects = 0;
    provider_bind_result = 123; provider_begin_result = session;
    legacy_provider_get = NULL; legacy_provider_put = NULL;
    provider = (struct maplesu_legacy_hook_provider){
        .magic = MAPLESU_LEGACY_HOOK_MAGIC, .schema = MAPLESU_LEGACY_HOOK_SCHEMA, .length = sizeof provider,
        .host = {UINT64_C(0x1234567812345678), UINT64_C(0x8765432187654321)},
        .capabilities = MAPLESU_MANAGEMENT_CAPABILITIES,
        .bind_function = (uptr)provider_bind, .begin_function = (uptr)provider_begin,
        .end_function = (uptr)provider_end, .dispatch_function = (uptr)provider_dispatch };
    stage_busy = 0; legacy_receipt = 0; state = MSU_BOOT_SDK_READY;
    kernel_base = source_at; arena = source_at + 4096;
    original_argument = packet_at | UINT64_C(0xb400000000000000);
#ifdef MSU_B1_IMPLEMENTED
    request_v2_init(0);
#endif
    encode(MAPLESU_REQUEST_SELECTOR_SUB);
}
static void management(unsigned command) {
    reset(command); request.arg0 = 0; request.arg1 = management_at;
    legacy_provider_get = provider_get; legacy_provider_put = provider_put;
    management_args.host = provider.host; management_args.session = session;
    if (command == MAPLESU_REQUEST_SESSION_OPEN) {
        memset(&management_args, 0, sizeof management_args);
        management_args.data_user = host_at; management_args.length = sizeof returned_host;
        provider_result = session;
    }
}
static void isolated(void) {
    CHECK(!private_calls && !lock_calls && !legacy_calls && !reply_calls && !handover_calls && !native_calls);
    CHECK(!stage_busy);
}
static void completed(int status, u64 result) {
    CHECK(sdk_execve(&original_argument) == -2);
    CHECK(response.completion_magic == MAPLESU_RESPONSE_MAGIC);
    CHECK(response.version == 2 && response.length == sizeof response);
    CHECK(response.command == request.command && response.request_id == request.request_id);
    CHECK(response.status == status && response.result == result);
    CHECK(response_calls == 1 && completion_calls == 1);
    isolated();
}
int main(int argc, char **argv) {
    (void)sdk_request_metadata;
    reset(MAPLESU_REQUEST_QUERY);
    completed(0, MAPLESU_REQUEST_CAPABILITIES);
    if (argc > 1 && !strcmp(argv[1], "red")) goto done;
    CHECK(response.metadata_address == (uptr)&sdk_request_metadata);
    CHECK(response.metadata_length == 48 && response.metadata_version == 2);
    for (unsigned available = 0; available < 8; ++available) {
        reset(MAPLESU_REQUEST_QUERY);
        request.arg0 = MAPLESU_KEY_CREDENTIAL_QUERY; request.arg1 = 0;
        legacy_provider_get = provider_get; legacy_provider_put = provider_put;
        const u64 capabilities = (available & 1 ? MAPLESU_CAP_CREDENTIAL : 0) |
            (available & 2 ? MAPLESU_CAP_PROCATTR : 0) | (available & 4 ? MAPLESU_CAP_TASK_STATE : 0);
        provider.capabilities = MAPLESU_CAP_SESSION | capabilities;
        const maplesu_metadata_v2 immutable = sdk_request_metadata;
        completed(0, MAPLESU_REQUEST_CAPABILITIES | capabilities);
        CHECK(!memcmp(&immutable, &sdk_request_metadata, sizeof immutable));
        CHECK(response.metadata_address == (uptr)&sdk_request_metadata && response.metadata_length == 48 &&
            response.metadata_version == 2 && !provider_dispatches && !provider_closes);
        CHECK(provider_gets == 1 && provider_binds == 1 && provider_begins == 1 && provider_ends == 1 && provider_puts == 1);
    }
    reset(MAPLESU_REQUEST_QUERY); request.arg0 = MAPLESU_KEY_CREDENTIAL_QUERY; request.arg1 = 0;
    completed(MAPLESU_REQUEST_STATUS_UNSUPPORTED, MAPLESU_REQUEST_CAPABILITIES);
    CHECK(!provider_gets && !provider_puts);
    reset(MAPLESU_REQUEST_QUERY); request.arg0 = MAPLESU_KEY_CREDENTIAL_QUERY;
    completed(0, MAPLESU_REQUEST_CAPABILITIES); CHECK(!provider_gets);
    reset(MAPLESU_REQUEST_QUERY); request.arg0 = MAPLESU_KEY_CREDENTIAL_QUERY; request.arg1 = 0;
    legacy_provider_get = provider_get; legacy_provider_put = provider_put; provider_get_error = -12;
    completed(-12, MAPLESU_REQUEST_CAPABILITIES); CHECK(provider_gets == 1 && !provider_puts);
    reset(MAPLESU_REQUEST_QUERY); request.arg0 = MAPLESU_KEY_CREDENTIAL_QUERY; request.arg1 = 0;
    legacy_provider_get = provider_get; legacy_provider_put = provider_put;
    provider.capabilities |= MAPLESU_CAP_PROCATTR | MAPLESU_CAP_TASK_STATE;
    provider_end_error = -116;
    completed(-116, MAPLESU_REQUEST_CAPABILITIES | MAPLESU_KEY_CREDENTIAL_QUERY);
    CHECK(response.metadata_length == (48 | MAPLESU_EFFECT_SESSION_CLOSED) && provider_ends == 1 &&
        provider_closes == 1 && provider_puts == 1);
#ifdef MSU_B1_IMPLEMENTED
    reset(MAPLESU_REQUEST_QUERY); request_v2_init(1);
    completed(0, MAPLESU_REQUEST_CAPABILITIES | MAPLESU_CAP_APPEND_AUDIT);
    CHECK(response.metadata_address == (uptr)&sdk_request_metadata && response.metadata_length == 48);
#endif
    const u64 values[] = {0, 1, UINT64_MAX, UINT64_C(0x8000000000000001), (u64)-19999};
    for (unsigned n = 0; n < sizeof values / sizeof values[0]; ++n) {
        reset(MAPLESU_REQUEST_ECHO64); request.arg0 = values[n]; request.request_id = values[n];
        completed(0, values[n]);
    }
    for (unsigned n = 0; n < 5; ++n) {
        reset(MAPLESU_REQUEST_QUERY);
        switch(n) { case 0: request.magic ^= 1; break; case 1: ++request.version; break;
        case 2: --request.length; break; case 3: ++request.length; break; default: request.reserved = 1; }
        completed(MAPLESU_REQUEST_STATUS_INVALID, 0);
    }
    reset(0xfeed); completed(MAPLESU_REQUEST_STATUS_UNSUPPORTED, 0);
    reset(MAPLESU_REQUEST_READ); completed(0, 256);
    CHECK(kernel_calls == 1 && descriptor_calls == 1 && !memcmp(destination, read_bytes, 256));
    reset(MAPLESU_REQUEST_READ); read_args.length = 1; completed(0, 1); CHECK(destination[0] == read_bytes[0]);
    for (unsigned n = 0; n < 8; ++n) {
        reset(MAPLESU_REQUEST_READ);
        switch(n) { case 0: read_args.length = 0; break; case 1: read_args.length = 257; break;
        case 2: read_args.reserved = 1; break; case 3: request.arg0 = 1; break;
        case 4: request.arg0 = UINT64_MAX; break; case 5: read_args.destination_user = 0; break;
        case 6: read_args.destination_user = UINT64_MAX; break; default: request.arg0 = UINT64_C(0xfffeffffffffffff); }
        completed(MAPLESU_REQUEST_STATUS_INVALID, 0); CHECK(!kernel_calls);
    }
    reset(MAPLESU_REQUEST_READ); request.arg1 = 0; completed(MAPLESU_REQUEST_STATUS_FAULT, 0);
    reset(MAPLESU_REQUEST_READ); fail_input = 4; completed(MAPLESU_REQUEST_STATUS_FAULT, 0); CHECK(!kernel_calls);
    reset(MAPLESU_REQUEST_READ); fail_read = 1; completed(MAPLESU_REQUEST_STATUS_FAULT, 0);
    reset(MAPLESU_REQUEST_READ); fail_output = 1; completed(MAPLESU_REQUEST_STATUS_FAULT, 0);
    reset(MAPLESU_REQUEST_READ); read_args.destination_user = 1; completed(MAPLESU_REQUEST_STATUS_FAULT, 0);
    reset(MAPLESU_REQUEST_READ); mutate_descriptor = 1;
    CHECK(sdk_execve(&original_argument) == -2 && response.status == 0 && response.result == 256);
    CHECK(descriptor_calls == 1 && !memcmp(destination, read_bytes, 256)); isolated();
    reset(MAPLESU_REQUEST_ECHO64); request.arg0 = UINT64_C(0xaabbccddeeff0011); mutate_request = 1;
    CHECK(sdk_execve(&original_argument) == -2 && response.status == 0);
    CHECK(response.request_id == UINT64_C(0xfedcba9876543210) && response.result == UINT64_C(0xaabbccddeeff0011)); isolated();
    for (unsigned n = 0; n < 2; ++n) {
        reset(MAPLESU_REQUEST_QUERY);
        if (n) fail_input = 3; else {
            unsigned char plain[18] = {0x34, MAPLESU_REQUEST_SELECTOR_SUB};
            for (unsigned j = 0; j < 18; ++j) { packet[48+j] = plain[j] == 1 ? 2 : 1; packet[66+j] = plain[j] - packet[48+j]; } }
        CHECK(sdk_execve(&original_argument) == -14 && !output_calls); isolated();
    }
    reset(MAPLESU_REQUEST_QUERY); request.response_user = 1;
    CHECK(sdk_execve(&original_argument) == -14 && output_calls == 1 && !completion_calls); isolated();
    reset(MAPLESU_REQUEST_QUERY); fail_output = 1;
    CHECK(sdk_execve(&original_argument) == -14 && !completion_calls && response.completion_magic == 0); isolated();
    reset(MAPLESU_REQUEST_QUERY); fail_output = 2;
    CHECK(sdk_execve(&original_argument) == -14 && response_calls == 1 && completion_calls == 1);
    CHECK(response.completion_magic != MAPLESU_RESPONSE_MAGIC); isolated();
    reset(MAPLESU_REQUEST_QUERY); busy = 1; private_error = -12;
    completed(0, MAPLESU_REQUEST_CAPABILITIES);
    reset(MAPLESU_REQUEST_QUERY); encode(0);
    CHECK(sdk_execve(&original_argument) == -2 && private_calls == 1 && lock_calls == 1);
    CHECK(legacy_calls == 1 && handover_calls == 1 && reply_calls == 1 && legacy_receipt == UINT64_C(0x2468135713572468));
    for (unsigned flag = 0x30; flag <= 0x35; ++flag) {
        if (flag == MAPLESU_REQUEST_SELECTOR_FLAG) continue;
        reset(MAPLESU_REQUEST_QUERY); packet[66] = flag - packet[48];
        CHECK(sdk_execve(&original_argument) == -2 && private_calls == 1 && legacy_calls == 1);
        CHECK(lock_calls == 1 && handover_calls == 1 && reply_calls == 1 && !response_calls);
    }
    reset(MAPLESU_REQUEST_QUERY); packet[0] ^= 1;
    CHECK(sdk_execve(&original_argument) == -123 && native_calls == 1 && !private_calls && !response_calls);
    reset(MAPLESU_REQUEST_QUERY); packet[84] = 1;
    CHECK(sdk_execve(&original_argument) == -22 && !private_calls && !response_calls);
    management(MAPLESU_REQUEST_SESSION_OPEN); completed(0, session);
    CHECK(returned_host.low == provider.host.low && returned_host.high == provider.host.high);
    CHECK(!response.metadata_length && !response.metadata_version && provider_dispatches == 1 &&
        provider_gets == 1 && provider_puts == 1 && provider_binds == 1 && provider_begins == 1 && provider_ends == 1);
    const unsigned management_commands[] = { MAPLESU_REQUEST_ALLOC, MAPLESU_REQUEST_WRITE,
        MAPLESU_REQUEST_EXEC, MAPLESU_REQUEST_RELEASE, MAPLESU_REQUEST_SESSION_CLOSE };
    for (unsigned n = 0; n < sizeof management_commands / sizeof management_commands[0]; ++n) {
        management(management_commands[n]); management_args.handle = 79; management_args.address = source_at;
        provider_result = n == 2 ? UINT64_C(0xfedcba9876543210) : n == 4 ? 0 : source_at + n;
        provider_handle = n == 0 ? 79 : 0;
        provider_effects = n == 0 ? MAPLESU_EFFECT_ALLOCATED : n == 1 ? MAPLESU_EFFECT_WRITTEN :
            n == 2 ? MAPLESU_EFFECT_EXECUTED : n == 3 ? MAPLESU_EFFECT_RELEASED : MAPLESU_EFFECT_SESSION_CLOSED;
        completed(0, provider_result);
        CHECK(response.metadata_address == provider_handle && response.metadata_length == provider_effects);
        CHECK(provider_dispatches == 1 && provider_ends == 1 && provider_puts == 1);
    }
    const unsigned credential_commands[] = { MAPLESU_REQUEST_CRED_ROOT, MAPLESU_REQUEST_CRED_CAPS,
        MAPLESU_REQUEST_CRED_PROCATTR, MAPLESU_REQUEST_TASK_STATE };
    for (unsigned n = 0; n < sizeof credential_commands / sizeof credential_commands[0]; ++n) {
        const u64 capability = n < 2 ? MAPLESU_CAP_CREDENTIAL : n == 2 ? MAPLESU_CAP_PROCATTR : MAPLESU_CAP_TASK_STATE;
        management(credential_commands[n]); provider.capabilities |= MAPLESU_KEY_CREDENTIAL_QUERY;
        provider_result = n == 2 ? 15 : n == 3 ? 2 : 0;
        provider_effects = n < 2 ? MAPLESU_EFFECT_COMMITTED : n == 2 ? MAPLESU_EFFECT_PROC_WRITE_ATTEMPTED |
            MAPLESU_EFFECT_CRED_CHANGED : MAPLESU_EFFECT_TASK_STATE_CHANGED | MAPLESU_EFFECT_TASK_STATE_COMPLETED;
        completed(0, provider_result);
        CHECK(response.metadata_length == provider_effects && provider_dispatches == 1 &&
            provider_ends == 1 && provider_puts == 1);
        management(credential_commands[n]); provider.capabilities = MAPLESU_CAP_SESSION;
        provider.capabilities |= MAPLESU_KEY_CREDENTIAL_QUERY & ~capability;
        completed(MAPLESU_REQUEST_STATUS_UNSUPPORTED, 0);
        CHECK(!provider_dispatches && provider_ends == 1 && provider_puts == 1);
    }
    management(MAPLESU_REQUEST_CRED_ROOT); provider_status = -5; provider_end_error = -116;
    provider_effects = MAPLESU_EFFECT_COMMITTED;
    completed(-5, 0);
    CHECK(response.metadata_length == (MAPLESU_EFFECT_COMMITTED | MAPLESU_EFFECT_SESSION_CLOSED) &&
        provider_closes == 1 && provider_ends == 1 && provider_puts == 1);
    for (unsigned failure = 1; failure <= 2; ++failure) {
        management(MAPLESU_REQUEST_CRED_ROOT); provider_effects = MAPLESU_EFFECT_COMMITTED;
        fail_output = (int)failure;
        CHECK(sdk_execve(&original_argument) == MAPLESU_REQUEST_STATUS_FAULT);
        CHECK(provider_dispatches == 2 && provider_closes == 1 && provider_ends == 1 && provider_puts == 1);
        isolated();
    }
    management(MAPLESU_REQUEST_EXEC); provider_result = UINT64_MAX;
    provider_effects = MAPLESU_EFFECT_EXECUTED; provider_end_error = -116;
    completed(-116, UINT64_MAX);
    CHECK(response.metadata_length == (MAPLESU_EFFECT_EXECUTED | MAPLESU_EFFECT_SESSION_CLOSED) &&
        provider_dispatches == 2 && provider_closes == 1 && provider_ends == 1 && provider_puts == 1);
    management(MAPLESU_REQUEST_ALLOC); provider_status = -12; provider_end_error = -116;
    completed(-12, 0); CHECK(provider_closes == 1 && provider_dispatches == 2 && provider_puts == 1);
    management(MAPLESU_REQUEST_SESSION_OPEN); fail_output = 1;
    completed(MAPLESU_REQUEST_STATUS_FAULT, session);
    CHECK(response.metadata_length == MAPLESU_EFFECT_SESSION_CLOSED && provider_closes == 1 && provider_puts == 1);
    for (unsigned failure = 1; failure <= 2; ++failure) {
        management(MAPLESU_REQUEST_ALLOC); provider_result = source_at; provider_effects = MAPLESU_EFFECT_ALLOCATED;
        fail_output = (int)failure;
        CHECK(sdk_execve(&original_argument) == MAPLESU_REQUEST_STATUS_FAULT);
        CHECK(provider_dispatches == 2 && provider_closes == 1 && provider_ends == 1 && provider_puts == 1);
        CHECK(response.completion_magic != MAPLESU_RESPONSE_MAGIC); isolated();
    }
    management(MAPLESU_REQUEST_WRITE); fail_input = 4;
    completed(MAPLESU_REQUEST_STATUS_FAULT, 0); CHECK(!provider_gets && !provider_dispatches);
    management(MAPLESU_REQUEST_ALLOC); management_args.host.high ^= 1;
    completed(MAPLESU_REQUEST_STATUS_STALE, 0);
    CHECK(!provider_dispatches && provider_ends == 1 && provider_puts == 1);
    management(MAPLESU_REQUEST_SESSION_OPEN); provider.capabilities &= ~MAPLESU_CAP_EXEC;
    completed(MAPLESU_REQUEST_STATUS_UNSUPPORTED, 0); CHECK(!provider_dispatches && provider_puts == 1);
    management(MAPLESU_REQUEST_ALLOC); provider_get_error = -12;
    completed(-12, 0); CHECK(!provider_dispatches && !provider_puts);
    management(MAPLESU_REQUEST_SESSION_OPEN); provider_get_error = -2;
    completed(MAPLESU_REQUEST_STATUS_UNSUPPORTED, 0); CHECK(!provider_dispatches && !provider_puts);
    management(MAPLESU_REQUEST_SESSION_OPEN); provider.schema = 0;
    completed(-71, 0); CHECK(!provider_dispatches && !provider_binds && provider_puts == 1);
    management(MAPLESU_REQUEST_SESSION_OPEN); provider_bind_result = 0;
    completed(MAPLESU_REQUEST_STATUS_DENIED, 0); CHECK(!provider_dispatches && !provider_begins && provider_puts == 1);
    management(MAPLESU_REQUEST_SESSION_OPEN); provider_begin_result = 0;
    completed(MAPLESU_REQUEST_STATUS_STALE, 0); CHECK(!provider_dispatches && !provider_ends && provider_puts == 1);
    management(MAPLESU_REQUEST_SESSION_OPEN); management_args.length = sizeof returned_host - 1;
    completed(MAPLESU_REQUEST_STATUS_INVALID, 0); CHECK(!provider_gets && !provider_dispatches);
    management(MAPLESU_REQUEST_SESSION_CLOSE); management_args.session = 0;
    management_args.host = (maplesu_host_instance){0}; completed(0, 0);
    CHECK(response.metadata_length == MAPLESU_EFFECT_SESSION_CLOSED && provider_dispatches == 1 &&
        provider_closes == 1 && provider_puts == 1);
    const unsigned stale_commands[] = { MAPLESU_REQUEST_SESSION_CLOSE, MAPLESU_REQUEST_ALLOC };
    for (unsigned n = 0; n < sizeof stale_commands / sizeof stale_commands[0]; ++n) {
        management(stale_commands[n]); management_args.session ^= 1;
        completed(MAPLESU_REQUEST_STATUS_STALE, 0);
        CHECK(!provider_dispatches && !provider_closes && provider_puts == 1);
    }
    management(MAPLESU_REQUEST_ALLOC); management_args.session = 0;
    completed(MAPLESU_REQUEST_STATUS_STALE, 0); CHECK(!provider_dispatches && provider_puts == 1);
done:
    printf("{\"checks\":%u,\"failures\":%u,\"private\":%u,\"legacy\":%u,\"locks\":%u}\n",
        checks, failures, private_calls, legacy_calls, lock_calls);
    return failures ? 1 : 0;
}
