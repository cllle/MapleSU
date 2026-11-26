/* SPDX-License-Identifier: GPL-3.0-only */
#include "boot_sdk_protocol.h"
#include "boot_sdk_layout.h" /* measured from the target image */
#ifdef MSU_BOOT_SDK_NATIVE
#include "native_sdk.h"
#include "preinit.h"
#include "manager_resident_host.h"
#include "manager_claim_abi.h"
#if MSU_INIT_BRIDGE_CFI_LEGACY
#include "native_legacy_cfi.h"
#endif
#include <linux/irqflags.h>
#include <linux/limits.h>
#include <linux/preempt.h>
#include <linux/string.h>
#define MSU_PH_NOCFI
#define MSU_SDK_U64_MAX U64_MAX
#else
#include "policy_hook_dispatch.h"
#define MSU_SDK_U64_MAX UINT64_MAX
#endif
#include "boot_sdk_allocator.h"
#include "request_protocol.h"
#include "legacy_hook_private.h"
#ifndef MSU_BS_ENVIRONMENT
#define MSU_BS_ENVIRONMENT 0
#endif
#ifndef MSU_BS_STATIC_BOOTSTRAP
#define MSU_BS_STATIC_BOOTSTRAP 1
#endif

typedef unsigned long uptr;
#ifndef MSU_BOOT_SDK_NATIVE
typedef unsigned int u32;
typedef unsigned long long u64;
#endif
struct kprobe;
struct pt_regs;
#ifdef MSU_BOOT_SDK_NATIVE
int msu_boot_sdk_avc_redirect(struct pt_regs *regs);
int msu_boot_sdk_pre(struct kprobe *p, struct pt_regs *regs);
int msu_boot_sdk_allocator_pre(struct kprobe *p, struct pt_regs *regs);
long msu_boot_sdk_control(uptr user);
int msu_boot_sdk_init(uptr (*lookup)(const char *), int (*subject)(void),
    int (*handover)(void));
void msu_boot_sdk_cleanup(void);
#define register_kprobe msu_sdk_native_register
#define unregister_kprobe msu_sdk_native_unregister
extern void msu_sdk_call_template(uptr target, const void *packet, void *reply);
#if MSU_INIT_BRIDGE_CFI_KCFI
/* Supply the compiler-owned assembly type-ID relocation at final linkage. */
__ADDRESSABLE(msu_sdk_call_template);
#endif
extern unsigned char msu_sdk_call_prefix[];
#define MSU_SDK_CODE_START msu_sdk_call_prefix
#if MSU_INIT_BRIDGE_CFI_LEGACY
#define MSU_SDK_CODE_ENTRY 0UL
#else
#define MSU_SDK_CODE_ENTRY ((uptr)msu_sdk_call_template - (uptr)MSU_SDK_CODE_START)
#endif
#else
extern int register_kprobe(void *);
extern void unregister_kprobe(void *);
extern unsigned char msu_sdk_call_template[];
#define MSU_SDK_CODE_START msu_sdk_call_template
#define MSU_SDK_CODE_ENTRY 0UL
#endif
extern unsigned char msu_sdk_call_blr[], msu_sdk_call_template_end[];
extern unsigned char msu_sdk_original_return[];
extern long msu_sdk_call_original(uptr, const void *);
extern unsigned char msu_sdk_lookup_template[], msu_sdk_lookup_target[], msu_sdk_lookup_end[];
extern unsigned char msu_sdk_avc_template[], msu_sdk_avc_branch[], msu_sdk_avc_result[], msu_sdk_avc_end[];
extern unsigned char msu_sdk_avc_bridge[], msu_sdk_avc_original_return[];

#if MSU_BS_ENVIRONMENT
#define MSU_BS_IDLE_PAGES 64u
#else
#define MSU_BS_IDLE_PAGES 0u
#endif
#define MSU_BS_ARENA_PAGES (2u + MSU_BS_IDLE_PAGES)
#if defined(MSU_BOOT_SDK_NATIVE) && MSU_INIT_BRIDGE_CFI_LEGACY
extern unsigned char msu_sdk_legacy_metadata[], msu_sdk_legacy_carrier_end[];
/* This C definition has compiler-generated legacy membership. It calls its
 * module-owned assembly implementation directly; no copied target is claimed
 * as a compiler CFI member. The SDK's existing JIT assembly ABI is unchanged. */
static noinline void msu_sdk_legacy_invoke(uptr target, const void *packet, void *reply)
{
    msu_sdk_call_template(target, packet, reply);
}
static void (*volatile msu_sdk_legacy_call)(uptr, const void *, void *) = msu_sdk_legacy_invoke;
#endif

static uptr (*lookup_name)(const char *);
static MSU_PH_NOCFI uptr sdk_observation_entry(const char *name)
{
#if defined(MSU_BOOT_SDK_NATIVE) && MSU_INIT_BRIDGE_CFI_LEGACY
    return msu_init_bridge_observation_entry(name);
#else
    return lookup_name(name);
#endif
}
static int (*root_subject)(void);
static int (*root_handover)(void);
static long (*copy_in)(void *, const void *, unsigned long);
static long (*copy_out)(void *, const void *, unsigned long);
static long (*read_kernel)(void *, const void *, unsigned long);
static long (*write_kernel)(void *, const void *, unsigned long);
static void *(*allocate)(unsigned long);
struct sdk_pgprot { uptr pgprot; };
#ifdef MSU_BOOT_SDK_NATIVE
#define MSU_SDK_PGPROT pgprot_t
#else
#define MSU_SDK_PGPROT struct sdk_pgprot
#endif
static void *(*allocate_range)(unsigned long, unsigned long, uptr, uptr,
    unsigned int, MSU_SDK_PGPROT, unsigned long, int, const void *);
static void *(*allocate_segmented_range)(unsigned long, unsigned long,
    unsigned int, unsigned int, unsigned int, unsigned int, uptr, uptr,
    unsigned int, MSU_SDK_PGPROT, unsigned long, int, const void *);
static void (*release)(const void *);
#ifndef MSU_BOOT_SDK_NATIVE
static void (*random_bytes)(void *, unsigned long);
#endif
static void (*flush_code)(uptr, uptr);
static int (*memory_ro)(uptr, int), (*memory_rw)(uptr, int);
static int (*memory_x)(uptr, int), (*memory_nx)(uptr, int);
#ifdef MSU_BOOT_SDK_NATIVE
static typeof(&prepare_creds) prepare_cred;
static typeof(&commit_creds) commit_cred;
static typeof(&__put_cred) drop_cred;
static typeof(&get_task_cred) hold_cred;
#else
static void *(*prepare_cred)(void);
static int (*commit_cred)(void *);
static void (*drop_cred)(void *);
static void *(*hold_cred)(void *);
#endif
static uptr native_execve, kernel_base, arena, avc_result;
#if MSU_BS_ENVIRONMENT
static uptr native_avc;
#endif
static unsigned char probe[MSU_BOOT_SDK_PROBE_BYTES] __attribute__((aligned(8)));
static char root_key[MSU_BOOT_SDK_KEY_BYTES + 1];
static char original_root_key[MSU_BOOT_SDK_KEY_BYTES + 1];
static u32 state, control_busy, stage_busy;
#ifdef MSU_BOOT_SDK_NATIVE
static char native_credential[MSU_BOOT_SDK_KEY_BYTES + 1];
static int native_host_started;
#endif
static struct {
    maplesu_metadata_v2 common;
    struct maplesu_legacy_hook_metadata_private legacy;
} sdk_request_descriptor;
#define sdk_request_metadata sdk_request_descriptor.common
static int (*legacy_provider_get)(const struct maplesu_legacy_hook_provider **, void **);
static void (*legacy_provider_put)(void *);
extern unsigned char msu_boot_sdk_legacy_scope_start[], msu_boot_sdk_legacy_scope_end[];
static int bootstrap_error, probe_installed;
static int sdk_dynamic_ready;
static unsigned int sdk_config_stage;
static uptr sdk_allocator_owner, sdk_allocator_args[8];
static unsigned int sdk_allocator_hits;
static unsigned int sdk_allocator_segmented;
#if MSU_BS_STATIC_BOOTSTRAP
static struct msu_boot_sdk_layout sdk_layout = {
    .task_cred=MSU_BS_TASK_CRED, .cred_suid=MSU_BS_CRED_SUID,
    .cred_securebits=MSU_BS_CRED_SECUREBITS, .cred_usage=MSU_BS_CRED_USAGE,
    .cred_usage_size=MSU_BS_CRED_USAGE_SIZE, .kp_addr=MSU_BS_KP_ADDR,
    .kp_name=MSU_BS_KP_NAME, .kp_pre=MSU_BS_KP_PRE, .regs_pc=MSU_BS_REGS_PC,
    .environment=MSU_BS_ENVIRONMENT,
};
static struct msu_sdk_vm sdk_vm = {
    MSU_BS_IMAGE_BYTES, 0, 0,
    MSU_BS_VM_ALIGN, MSU_BS_VM_GFP, MSU_BS_VM_PROT, MSU_BS_VM_PROT_SET, MSU_BS_VM_PROT_FLAG,
};
static u32 sdk_kallsyms = MSU_BS_KALLSYMS;
#else
static struct msu_boot_sdk_layout sdk_layout;
static struct msu_sdk_vm sdk_vm;
static u32 sdk_kallsyms;
#endif
#if MSU_BS_ENVIRONMENT
#if MSU_BS_STATIC_BOOTSTRAP
static u32 sdk_execve_offset = MSU_BS_EXECVE_OFFSET, sdk_filldir_offset = MSU_BS_FILLDIR_OFFSET;
static u32 sdk_audit_offset = MSU_BS_AUDIT_OFFSET, sdk_seccomp_offset = MSU_BS_SECCOMP_OFFSET;
#else
static u32 sdk_execve_offset, sdk_filldir_offset, sdk_audit_offset, sdk_seccomp_offset;
#endif
#endif
#undef MSU_BS_TASK_CRED
#undef MSU_BS_CRED_SUID
#undef MSU_BS_CRED_SECUREBITS
#undef MSU_BS_CRED_USAGE
#define MSU_BS_TASK_CRED sdk_layout.task_cred
#define MSU_BS_CRED_SUID sdk_layout.cred_suid
#define MSU_BS_CRED_SECUREBITS sdk_layout.cred_securebits
#define MSU_BS_CRED_USAGE sdk_layout.cred_usage
#undef MSU_BS_KP_NAME
#undef MSU_BS_KP_PRE
#undef MSU_BS_KP_ADDR
#undef MSU_BS_REGS_PC
#define MSU_BS_KP_NAME sdk_layout.kp_name
#define MSU_BS_KP_PRE sdk_layout.kp_pre
#define MSU_BS_KP_ADDR sdk_layout.kp_addr
#define MSU_BS_REGS_PC sdk_layout.regs_pc
struct stage { uptr address; u32 bytes, pages, written, quarantined; };
static struct stage stages[64];
#if MSU_BS_ENVIRONMENT
static void (*release_module)(const void *);
static MSU_PH_NOCFI void sdk_module_free(const void *);
#endif

static uptr sdk_current_task(void)
{
#ifdef MSU_BOOT_SDK_NATIVE
    return (uptr)current;
#else
    uptr task;
    __asm__ volatile("mrs %0, sp_el0" : "=r"(task));
    return task;
#endif
}
static uptr sdk_current_cred(void)
{
#ifdef MSU_BOOT_SDK_NATIVE
    return (uptr)current_cred();
#else
    return *(const volatile uptr *)(sdk_current_task() + MSU_BS_TASK_CRED);
#endif
}
static void zero(void *at, unsigned long bytes)
{
    unsigned char *p = at;
    while (bytes--) *p++ = 0;
}
/* Published once, before READY. Unlike the legacy arena this descriptor belongs
 * to the resident module's static data and needs no LR-distance convention. */
static void request_v2_init(int audit_published)
{
    zero(&sdk_request_metadata, sizeof sdk_request_metadata);
    sdk_request_metadata.magic = MAPLESU_METADATA_MAGIC;
    sdk_request_metadata.version = MAPLESU_METADATA_VERSION;
    sdk_request_metadata.length = sizeof sdk_request_metadata;
    sdk_request_metadata.capabilities = MAPLESU_REQUEST_CAPABILITIES |
        (audit_published ? MAPLESU_CAP_APPEND_AUDIT : 0)
#if defined(MSU_BOOT_SDK_NATIVE) && MSU_INIT_BRIDGE_CFI_LEGACY
        | MAPLESU_CAP_APPEND64
#endif
        ;
    sdk_request_metadata.kernel_base_displacement =
        (int64_t)((u64)kernel_base - (u64)(uptr)&sdk_request_metadata);
    sdk_request_metadata.legacy_metadata_displacement =
        (int64_t)((u64)(arena + MSU_BS_PAGE_BYTES) - (u64)(uptr)&sdk_request_metadata);
}
static int take(u32 *lock)
{
    u32 expected = 0;
    return __atomic_compare_exchange_n(lock, &expected, 1, 0, __ATOMIC_ACQUIRE, __ATOMIC_RELAXED);
}
#if MSU_BS_ENVIRONMENT
static int same_name(const char *left, const char *right)
{
    unsigned int n;
    for (n = 0; n < 128; ++n) {
        if (left[n] != right[n]) return 0;
        if (!left[n]) return 1;
    }
    return 0;
}
/* The old SDK's idle allocator must never receive released init text. These
 * bounds describe retained pages owned by this boot's pinned resident module. */
static MSU_PH_NOCFI __attribute__((__noinline__)) uptr sdk_lookup(const char *name)
{
    if (same_name(name, "module_memfree") || same_name(name, "execmem_free"))
        return (uptr)sdk_module_free;
    if (same_name(name, "__inittext_begin") || same_name(name, "_sinittext"))
        return arena + 2UL * MSU_BS_PAGE_BYTES;
    if (same_name(name, "__inittext_end") || same_name(name, "_einittext"))
        return arena + MSU_BS_ARENA_PAGES * (unsigned long)MSU_BS_PAGE_BYTES;
    return lookup_name(name);
}
#endif

/* Called only by the already registered native AVC listener. No allocation,
 * user copy or SDK code runs while kprobe dispatch owns the exception frame. */
int msu_boot_sdk_avc_redirect(struct pt_regs *regs)
{
#if MSU_BS_ENVIRONMENT
    uptr *frame = (void *)regs;
    if (!frame || !sdk_layout.environment || frame[30] == (uptr)msu_sdk_avc_original_return ||
        __atomic_load_n(&state, __ATOMIC_ACQUIRE) != MSU_BOOT_SDK_READY) return 0;
    frame[16] = native_avc;
    frame[17] = avc_result;
    frame[MSU_BS_REGS_PC / sizeof(uptr)] = (uptr)msu_sdk_avc_bridge;
    return 1;
#else
    (void)regs;
    return 0;
#endif
}
/* Every request receives a fresh task cred before saved-ID replies are written.
 * The SDK also prepares its thread; this covers helper and fork boundaries. */
static MSU_PH_NOCFI int private_cred(void)
{
    void *cred = prepare_cred();
    return cred ? commit_cred(cred) : -12;
}
static MSU_PH_NOCFI void put_held_cred(uptr cred)
{
    int last = sdk_layout.cred_usage_size == 8 ?
        !__atomic_sub_fetch((u64 *)(cred + MSU_BS_CRED_USAGE), 1, __ATOMIC_ACQ_REL) :
        !__atomic_sub_fetch((u32 *)(cred + MSU_BS_CRED_USAGE), 1, __ATOMIC_ACQ_REL);
    if (last)
        drop_cred((void *)cred);
}
static void reply(u64 value)
{
    msu_sdk_store((unsigned char *)sdk_current_cred() + MSU_BS_CRED_SUID, value, 8);
}
static struct stage *owned(uptr address)
{
    unsigned int n;
    for (n = 0; n < 64; ++n)
        if (stages[n].address && address >= stages[n].address &&
            address - stages[n].address < stages[n].bytes) return &stages[n];
    return (void *)0;
}
static MSU_PH_NOCFI int discard(struct stage *s)
{
    /* A failed protection transition retains its ledger entry and allocation. */
    s->quarantined = 1;
    if (memory_nx(s->address, s->pages) || memory_rw(s->address, s->pages)) return -5;
    release((void *)s->address);
    zero(s, sizeof *s);
    return 0;
}
#if MSU_BS_ENVIRONMENT
/* SDK free_kernel_mem() calls module_memfree or execmem_free through a JIT,
 * not command 0x35. Retire gateway allocations here, so the ledger never holds a
 * freed address. Unrelated allocations keep the native helper's semantics. */
static MSU_PH_NOCFI void sdk_module_free(const void *address)
{
    struct stage *s = owned((uptr)address);
    if (s) {
        if (s->address == (uptr)address) (void)discard(s);
        return;
    }
    release_module(address);
}
#endif
struct sdk_legacy_scope {
    const struct maplesu_legacy_hook_provider *provider;
    void *lease;
    u64 scope;
};

/* This callsite is in the resident authenticated executor, never in user JIT
 * bytes. The provider validates the real kernel code range and current task. */
static MSU_PH_NOCFI __attribute__((__noinline__)) int legacy_scope_begin(struct sdk_legacy_scope *scope)
{
    typedef u64 (*bind_call)(u64, u64);
    typedef u64 (*begin_call)(u64);
    const struct maplesu_legacy_hook_provider *provider;
    u64 token;
    int error = MAPLESU_REQUEST_STATUS_UNSUPPORTED;
    __asm__ volatile(".global msu_boot_sdk_legacy_scope_start\nmsu_boot_sdk_legacy_scope_start:");
    zero(scope, sizeof *scope);
    if (!legacy_provider_get || !legacy_provider_put) goto out;
    error = legacy_provider_get(&scope->provider, &scope->lease);
    if (error) {
        if (error == -2) error = MAPLESU_REQUEST_STATUS_UNSUPPORTED;
        goto out;
    }
    provider = scope->provider;
    error = -71;
    if (!provider || provider->magic != MAPLESU_LEGACY_HOOK_MAGIC ||
        provider->schema != MAPLESU_LEGACY_HOOK_SCHEMA || provider->length != sizeof *provider ||
        !provider->bind_function || !provider->begin_function || !provider->end_function ||
        !provider->dispatch_function) goto put;
    token = ((bind_call)(uptr)provider->bind_function)((uptr)msu_boot_sdk_legacy_scope_start,
        msu_boot_sdk_legacy_scope_end - msu_boot_sdk_legacy_scope_start);
    error = MAPLESU_REQUEST_STATUS_DENIED;
    if (!token) goto put;
    scope->scope = ((begin_call)(uptr)provider->begin_function)(token);
    error = scope->scope ? 0 : MAPLESU_REQUEST_STATUS_STALE;
    if (!error) goto out;
put:
    legacy_provider_put(scope->lease);
    zero(scope, sizeof *scope);
out:
    __asm__ volatile(".global msu_boot_sdk_legacy_scope_end\nmsu_boot_sdk_legacy_scope_end:");
    return error;
}

static MSU_PH_NOCFI int legacy_scope_end(struct sdk_legacy_scope *scope)
{
    typedef int (*end_call)(u64);
    int error = 0;
    if (scope->scope) {
        error = ((end_call)(uptr)scope->provider->end_function)(scope->scope);
        legacy_provider_put(scope->lease);
    }
    zero(scope, sizeof *scope);
    return error;
}

static MSU_PH_NOCFI u64 execute(struct stage *s, unsigned char *packet)
{
#if !defined(MSU_BOOT_SDK_NATIVE) || !MSU_INIT_BRIDGE_CFI_LEGACY
    typedef void (*jit_call)(uptr, const void *, void *);
#endif
    struct sdk_legacy_scope scope;
    uptr cred;
    u64 result = (u64)-19999;
    if (memory_ro(s->address, s->pages) || memory_x(s->address, s->pages)) goto out;
    flush_code(s->address, s->address + s->bytes);
    cred = (uptr)hold_cred((void *)sdk_current_task());
    legacy_scope_begin(&scope);
#if defined(MSU_BOOT_SDK_NATIVE) && MSU_INIT_BRIDGE_CFI_LEGACY
    /* The volatile typed call performs the real compiler cfi-icall check. */
    msu_sdk_legacy_call(s->address, packet, (void *)(cred + MSU_BS_CRED_SUID));
#else
    ((jit_call)(arena + MSU_SDK_CODE_ENTRY))(s->address, packet, (void *)(cred + MSU_BS_CRED_SUID));
#endif
    /* A JIT may itself commit credentials. Keep its reply object alive, then
     * copy the result into another private cred when the current one changed. */
    result = msu_sdk_le((const unsigned char *)cred + MSU_BS_CRED_SUID, 8);
    if (legacy_scope_end(&scope)) result = (u64)-19999;
    if (sdk_current_cred() != cred) {
        u32 securebits = *(const u32 *)(cred + MSU_BS_CRED_SECUREBITS);
        if (private_cred()) result = (u64)-19999;
        else *(u32 *)(sdk_current_cred() + MSU_BS_CRED_SECUREBITS) = securebits;
    }
    put_held_cred(cred);
out:
    if (discard(s)) result = (u64)-19999;
    return result;
}
static MSU_PH_NOCFI u64 dispatch(unsigned char *packet)
{
    uptr address = (uptr)msu_sdk_le(packet + 68, 8);
    u32 bytes = (u32)msu_sdk_le(packet + 76, 4);
    u32 pages = (u32)msu_sdk_le(packet + 80, 4);
    struct stage *s;
    unsigned int n;
    unsigned char value;
    switch (packet[66]) {
    case 0x30:
        if (!bytes || bytes > MSU_BOOT_SDK_MAX_STAGE) return (u64)-19999;
        for (n = 0; n < 64 && stages[n].address; ++n) { }
        if (n == 64) return (u64)-19999;
        pages = (bytes + MSU_BS_PAGE_BYTES - 1) / MSU_BS_PAGE_BYTES;
        address = (uptr)allocate((unsigned long)pages * MSU_BS_PAGE_BYTES);
        if (!address) return 0;
        s = &stages[n]; s->address = address; s->bytes = bytes; s->pages = pages;
        if (memory_nx(address, pages)) { (void)discard(s); return (u64)-19999; }
        zero((void *)address, (unsigned long)pages * MSU_BS_PAGE_BYTES);
        return address;
    case 0x31:
        s = owned(address);
        if (s && s->quarantined) return (u64)-19999;
        /* KMP_RW also updates retained SDK data and live kernel state. Keep the
         * measured nofault helper's protection checks instead of a raw store. */
        if (write_kernel((void *)address, packet + 67, 1)) return (u64)-19999;
        if (s && address - s->address == s->written) ++s->written;
        return 0;
    case 0x32:
        return read_kernel(&value, (const void *)address, 1) ? (u64)-19999 : value;
    case 0x33:
        s = owned(address);
        /* The pinned executor submits page-rounded bytes after copying only its
         * instructions. The remainder is initialized by allocation, not upload. */
        if (!s || s->quarantined || s->address != address || !s->written || (s->written & 3) ||
            bytes != (unsigned long)s->pages * MSU_BS_PAGE_BYTES || pages != s->pages)
            return (u64)-19999;
        return execute(s, packet);
    case 0x34: return address;
    case 0x35:
        s = owned(address);
        return s && s->address == address && !discard(s) ? 0 : (u64)-19999;
    default: return (u64)-19999;
    }
}

/* Cleanup is scoped to this producer's exact task session. The provider lease
 * remains held through response delivery, including after end_function closes
 * active admission for this packet. */
static MSU_PH_NOCFI void request_v2_close(struct sdk_legacy_scope *scope,
    maplesu_response_v2 *response)
{
    typedef int (*dispatch_call)(u64, u32, const maplesu_management_v3 *, maplesu_response_owner_v3 *);
    maplesu_management_v3 operation;
    maplesu_response_owner_v3 closed;
    if (!scope->scope) return;
    zero(&operation, sizeof operation);
    operation.host = scope->provider->host;
    operation.session = scope->scope;
    zero(&closed, sizeof closed);
    ((dispatch_call)(uptr)scope->provider->dispatch_function)(scope->scope,
        MAPLESU_REQUEST_SESSION_CLOSE, &operation, &closed);
    if (response) response->metadata_length |= closed.metadata_length;
}

/* Key/v2 uses the same native resources without borrowing Owner authority or
 * credential reply storage. OPEN returns only the native host instance; the
 * public immutable metadata descriptor remains unchanged. */
static MSU_PH_NOCFI void request_v2_management(const maplesu_request_v2 *request,
    maplesu_response_v2 *response, struct sdk_legacy_scope *scope)
{
    typedef int (*dispatch_call)(u64, u32, const maplesu_management_v3 *, maplesu_response_owner_v3 *);
    maplesu_management_v3 operation;
    maplesu_response_owner_v3 native;
    u64 output = 0;
    u64 required = MAPLESU_CAP_SESSION;
    int closing = request->command == MAPLESU_REQUEST_SESSION_CLOSE;
    int error;
    if (request->arg0) return;
    response->status = MAPLESU_REQUEST_STATUS_FAULT;
    if (copy_in(&operation, (const void *)(uptr)request->arg1, sizeof operation)) return;
    response->status = MAPLESU_REQUEST_STATUS_INVALID;
    if (request->command == MAPLESU_REQUEST_SESSION_OPEN) {
        if ((operation.host.low | operation.host.high) || operation.session || operation.handle ||
            operation.address || operation.flags || operation.reserved ||
            operation.length != sizeof(maplesu_host_instance) || !operation.data_user ||
            operation.data_user > MSU_SDK_U64_MAX - (sizeof(maplesu_host_instance) - 1)) return;
        output = operation.data_user;
        operation.data_user = 0;
        operation.length = 0;
        required |= MAPLESU_CAP_ALLOC | MAPLESU_CAP_WRITE | MAPLESU_CAP_EXEC | MAPLESU_CAP_RELEASE;
    } else if (request->command == MAPLESU_REQUEST_ALLOC) required |= MAPLESU_CAP_ALLOC;
    else if (request->command == MAPLESU_REQUEST_WRITE) required |= MAPLESU_CAP_WRITE;
    else if (request->command == MAPLESU_REQUEST_EXEC) required |= MAPLESU_CAP_EXEC;
    else if (request->command == MAPLESU_REQUEST_RELEASE) required |= MAPLESU_CAP_RELEASE;
    else if (request->command == MAPLESU_REQUEST_CRED_ROOT ||
        request->command == MAPLESU_REQUEST_CRED_CAPS) required |= MAPLESU_CAP_CREDENTIAL;
    else if (request->command == MAPLESU_REQUEST_CRED_PROCATTR) required |= MAPLESU_CAP_PROCATTR;
    else if (request->command == MAPLESU_REQUEST_TASK_STATE) required |= MAPLESU_CAP_TASK_STATE;
    error = legacy_scope_begin(scope);
    if (error) {
        response->status = error;
        return;
    }
    response->status = MAPLESU_REQUEST_STATUS_UNSUPPORTED;
    if ((scope->provider->capabilities & required) != required) return;
    if (output || (closing && !(operation.host.low | operation.host.high)))
        operation.host = scope->provider->host;
    else if (operation.host.low != scope->provider->host.low ||
        operation.host.high != scope->provider->host.high) {
        response->status = MAPLESU_REQUEST_STATUS_STALE;
        return;
    }
    if (!output && operation.session != scope->scope && !(closing && !operation.session)) {
        response->status = MAPLESU_REQUEST_STATUS_STALE;
        return;
    }
    zero(&native, sizeof native);
    ((dispatch_call)(uptr)scope->provider->dispatch_function)(scope->scope,
        request->command, &operation, &native);
    response->status = native.status;
    response->result = native.result;
    response->metadata_address = native.metadata_address;
    response->metadata_length = native.metadata_length;
    response->metadata_version = native.metadata_version;
    if (output && !response->status &&
        copy_out((void *)(uptr)output, &scope->provider->host, sizeof(maplesu_host_instance))) {
        response->status = MAPLESU_REQUEST_STATUS_FAULT;
        request_v2_close(scope, response);
    }
}

/* This authenticated path never prepares credentials or enters the legacy
 * staging lock. A failed user copy is not replayed through the old protocol. */
static MSU_PH_NOCFI long request_v2(uptr user)
{
    maplesu_request_v2 request;
    maplesu_response_v2 response;
    maplesu_read_v2 read;
    struct sdk_legacy_scope scope;
    unsigned char data[MAPLESU_READ_MAX_BYTES];
    u32 completion = MAPLESU_RESPONSE_MAGIC;
    if (copy_in(&request, (const void *)user, sizeof request))
        return MAPLESU_REQUEST_STATUS_FAULT;
    zero(&response, sizeof response);
    zero(&scope, sizeof scope);
    response.version = MAPLESU_REQUEST_PROTOCOL_VERSION;
    response.length = sizeof response;
    response.command = request.command;
    response.request_id = request.request_id;
    response.status = MAPLESU_REQUEST_STATUS_INVALID;
    if (request.magic != MAPLESU_REQUEST_MAGIC ||
        request.version != MAPLESU_REQUEST_PROTOCOL_VERSION ||
        request.length != sizeof request || request.reserved) goto respond;
    switch (request.command) {
    case MAPLESU_REQUEST_SESSION_OPEN:
    case MAPLESU_REQUEST_SESSION_CLOSE:
    case MAPLESU_REQUEST_ALLOC:
    case MAPLESU_REQUEST_WRITE:
    case MAPLESU_REQUEST_EXEC:
    case MAPLESU_REQUEST_RELEASE:
    case MAPLESU_REQUEST_CRED_ROOT:
    case MAPLESU_REQUEST_CRED_CAPS:
    case MAPLESU_REQUEST_CRED_PROCATTR:
    case MAPLESU_REQUEST_TASK_STATE:
        request_v2_management(&request, &response, &scope);
        goto respond;
    case MAPLESU_REQUEST_QUERY:
        response.result = sdk_request_metadata.capabilities;
        response.metadata_address = (uptr)&sdk_request_metadata;
        response.metadata_length = sizeof sdk_request_metadata;
        response.metadata_version = MAPLESU_METADATA_VERSION;
        if (request.arg0 == MAPLESU_KEY_CREDENTIAL_QUERY && !request.arg1) {
            response.status = legacy_scope_begin(&scope);
            if (response.status) goto respond;
            response.result |= scope.provider->capabilities & MAPLESU_KEY_CREDENTIAL_QUERY;
        }
        break;
    case MAPLESU_REQUEST_ECHO64:
        response.result = request.arg0;
        break;
    case MAPLESU_REQUEST_READ:
        response.status = MAPLESU_REQUEST_STATUS_FAULT;
        if (copy_in(&read, (const void *)(uptr)request.arg1, sizeof read)) goto respond;
        response.status = MAPLESU_REQUEST_STATUS_INVALID;
        if (read.reserved || !read.length || read.length > sizeof data ||
            request.arg0 < UINT64_C(0xffff000000000000) ||
            (u64)(read.length - 1) > MSU_SDK_U64_MAX - request.arg0 ||
            !read.destination_user ||
            (u64)(read.length - 1) > MSU_SDK_U64_MAX - read.destination_user) goto respond;
        response.status = MAPLESU_REQUEST_STATUS_FAULT;
        if (read_kernel(data, (const void *)(uptr)request.arg0, read.length) ||
            copy_out((void *)(uptr)read.destination_user, data, read.length)) goto respond;
        response.result = read.length;
        break;
    default:
        response.status = MAPLESU_REQUEST_STATUS_UNSUPPORTED;
        goto respond;
    }
    response.status = MAPLESU_REQUEST_STATUS_OK;
respond:
    if (scope.scope) {
        typedef int (*end_call)(u64);
        int end_error = ((end_call)(uptr)scope.provider->end_function)(scope.scope);
        if (end_error) {
            if (!response.status) response.status = end_error;
            request_v2_close(&scope, &response);
        }
    }
    if (copy_out((void *)(uptr)request.response_user, &response, sizeof response)) goto delivery_failed;
    __atomic_thread_fence(__ATOMIC_RELEASE);
    if (copy_out((void *)(uptr)request.response_user, &completion, sizeof completion)) goto delivery_failed;
    if (scope.lease) legacy_provider_put(scope.lease);
    return -2;
delivery_failed:
    request_v2_close(&scope, (void *)0);
    if (scope.lease) legacy_provider_put(scope.lease);
    return MAPLESU_REQUEST_STATUS_FAULT;
}

#ifdef MSU_BOOT_SDK_NATIVE
static long preinit_original_execve(const void *args)
{
    return msu_sdk_call_original(native_execve, args);
}
#endif
/* All user copies, credential allocation and JIT calls run after probe dispatch. */
static MSU_PH_NOCFI __attribute__((__noinline__)) long sdk_execve(const void *args)
{
    const uptr *syscall = args;
    uptr filename = syscall[0];
    unsigned char packet[MSU_BOOT_SDK_PACKET_BYTES];
    unsigned int n, bound_difference = 0, original_difference = 0;
    const char *packet_key;
    u64 result;
    int matched;
#ifndef MSU_BOOT_SDK_NATIVE
    int error;
#endif
#ifdef MSU_BOOT_SDK_NATIVE
    char owner_name[86];
    long preinit_result;
    if (msu_preinit_execve(args, preinit_original_execve, &preinit_result)) return preinit_result;
#endif
    /* ARM64 malloc may tag the filename's top byte. The generic nofault copy
     * must receive its user address; native passthrough retains the original.
     * Never turn a bit-55 kernel address into a user address. */
    if (!(filename & (1ULL << 55))) filename &= 0x00ffffffffffffffULL;
#ifdef MSU_BOOT_SDK_NATIVE
    /* Same host for Manager identity and owner-v3; short native names do not
     * require an 86-byte readable user mapping. */
    if (__atomic_load_n(&state, __ATOMIC_ACQUIRE) == MSU_BOOT_SDK_READY) {
        unsigned int length;
        for (length = 0; length < sizeof(owner_name); ++length) {
            if (copy_in(owner_name + length, (const void *)(filename + length), 1)) break;
            if (!owner_name[length]) {
                int owner_request = length == 84 &&
                    !memcmp(owner_name, MAPLESU_OWNER_ENVELOPE_PREFIX, MAPLESU_OWNER_ENVELOPE_PREFIX_BYTES);
                int identity_request = msu_manager_claim_address(owner_name, length) != 0;
                if (owner_request || identity_request) {
                    int result = msu_manager_resident_dispatch(owner_name);
                    zero(owner_name, sizeof owner_name);
                    if (owner_request) return result;
                    return msu_sdk_call_original(native_execve, args);
                }
                break;
            }
        }
        zero(owner_name, sizeof owner_name);
    }
#endif
    if (__atomic_load_n(&state, __ATOMIC_ACQUIRE) != MSU_BOOT_SDK_READY ||
        copy_in(packet, (const void *)filename, MSU_BOOT_SDK_KEY_BYTES))
        return msu_sdk_call_original(native_execve, args);
    /* Do not require an 85-byte readable range for a native pathname. */
    for (n = 0; n < MSU_BOOT_SDK_KEY_BYTES; ++n) {
        bound_difference |= packet[n] ^ (unsigned char)root_key[n];
        original_difference |= packet[n] ^ (unsigned char)original_root_key[n];
    }
    if (!bound_difference) packet_key = root_key;
    else if (!original_difference) packet_key = original_root_key;
    else return msu_sdk_call_original(native_execve, args);
    if (copy_in(packet + 48, (const void *)(filename + 48), sizeof packet - 48)) return -14;
    matched = msu_sdk_decode(packet, packet_key);
    if (matched != 1) return -22;
    if (packet[66] == MAPLESU_REQUEST_SELECTOR_FLAG && packet[67] == MAPLESU_REQUEST_SELECTOR_SUB) {
        uptr request_user = (uptr)msu_sdk_le(packet + 76, 8);
        zero(packet, sizeof packet);
        return request_v2(request_user);
    }
    if (private_cred()) return -12;
    if (!take(&stage_busy)) { reply((u64)-19999); return -16; }
    result = dispatch(packet);
#ifndef MSU_BOOT_SDK_NATIVE
    /* Only L3 has a private marker to hand over; the native bridge does not
     * claim or emulate that provider. Legacy JIT result remains unchanged. */
    error = root_handover();
    if (error) result = (u64)-19999;
#endif
    reply(result);
    __atomic_store_n(&stage_busy, 0, __ATOMIC_RELEASE);
    zero(packet, sizeof packet);
#ifndef MSU_BOOT_SDK_NATIVE
    return error ? error : -2;
#else
    return -2;
#endif
}
__attribute__((__noinline__)) int msu_boot_sdk_pre(struct kprobe *p, struct pt_regs *regs)
{
    uptr *frame = (void *)regs;
    (void)p;
    if (!frame || frame[30] == (uptr)msu_sdk_original_return ||
        __atomic_load_n(&state, __ATOMIC_ACQUIRE) != MSU_BOOT_SDK_READY) return 0;
    frame[MSU_BS_REGS_PC / sizeof(uptr)] = (uptr)sdk_execve;
    return 1;
}

/* Legacy controllers retain exact-image qualification. Current controllers
 * provide BTF-qualified layouts and observe native allocator call arguments. */
static MSU_PH_NOCFI int sdk_image_qualified(void)
{
    if (sdk_dynamic_ready) return 1;
#if MSU_BS_STATIC_BOOTSTRAP
    static const unsigned char expected[] = MSU_BS_KERNEL_NOTES;
    unsigned char observed[MSU_BS_KERNEL_NOTES_BYTES];
    uptr begin = lookup_name("__start_notes"), end = lookup_name("__stop_notes");
    unsigned int n;
    if (!begin || end <= begin || end - begin != sizeof(expected) ||
        read_kernel(observed, (const void *)begin, sizeof(observed))) return 0;
    for (n = 0; n < sizeof(expected); ++n)
        if (observed[n] != expected[n]) return 0;
    return 1;
#else
    return 0;
#endif
}

/* Observe the qualified native ABI, rather than compiler-specific vmalloc
 * instruction templates. This callback only reads its owner's argument frame. */
MSU_PH_NOCFI __attribute__((__noinline__)) int msu_boot_sdk_allocator_pre(struct kprobe *p, struct pt_regs *regs)
{
    const uptr *args = (const void *)regs;
    uptr owner = __atomic_load_n(&sdk_allocator_owner, __ATOMIC_ACQUIRE);
    unsigned int n;
    (void)p;
    if (!owner || owner != sdk_current_task() || !args || args[0] != MSU_BS_PAGE_BYTES) return 0;
    if (__atomic_add_fetch(&sdk_allocator_hits, 1, __ATOMIC_RELAXED) == 1) {
        for (n = 0; n < 8; ++n) sdk_allocator_args[n] = args[n];
        if (sdk_allocator_segmented) {
            uptr stack[4];
            /* The qualified 13-argument ABI has pt_regs.sp at slot 31.
             * Observe a whole one-page allocation before the arena call. */
            if (args[2] || args[3] != 1 || args[4] || args[5] != 1 ||
                !args[31] || read_kernel(stack, (const void *)args[31], sizeof stack)) {
                sdk_allocator_args[1] = 0;
            } else {
                sdk_allocator_args[2] = args[6]; sdk_allocator_args[3] = args[7];
                for (n = 0; n < 4; ++n) sdk_allocator_args[n + 4] = stack[n];
            }
        }
    }
    return 0;
}

static MSU_PH_NOCFI int sample_allocator(const struct msu_boot_sdk_layout *layout,
    uptr allocator, uptr range, unsigned long image_bytes, struct msu_sdk_vm *vm)
{
    unsigned char observer[MSU_BOOT_SDK_PROBE_BYTES] __attribute__((aligned(8)));
    uptr allocation, entry;
    u32 instruction;
    int error;
    zero(observer, sizeof observer);
    zero(sdk_allocator_args, sizeof sdk_allocator_args);
    sdk_allocator_hits = 0;
    *(const char **)(observer + layout->kp_name) = layout->allocator_noprof &
        (MSU_BOOT_SDK_ALLOCATOR_INNER | MSU_BOOT_SDK_ALLOCATOR_SEGMENTED) ?
        (layout->allocator_noprof & MSU_BOOT_SDK_ALLOCATOR_NOPROF ? "___vmalloc_node_range_noprof" : "___vmalloc_node_range") :
        (layout->allocator_noprof & MSU_BOOT_SDK_ALLOCATOR_NOPROF ? "__vmalloc_node_range_noprof" : "__vmalloc_node_range");
    *(uptr *)(observer + layout->kp_pre) = (uptr)msu_boot_sdk_allocator_pre;
    sdk_config_stage = MSU_BS_CONFIG_PROBE;
    error = register_kprobe(observer);
    if (error) return error;
    sdk_config_stage = MSU_BS_CONFIG_PROBE_ENTRY;
    entry = *(const uptr *)(observer + layout->kp_addr);
    error = -95;
    if (entry != range && (entry != range + 4 ||
        read_kernel(&instruction, (const void *)range, sizeof instruction) || instruction != 0xd503245f))
        goto out;
    /* CONFIGURE runs in syscall process context, after probe dispatch returns.
     * Registration/unregistration and native vmalloc/vfree may sleep here. */
    sdk_allocator_segmented = layout->allocator_noprof & MSU_BOOT_SDK_ALLOCATOR_SEGMENTED;
    __atomic_store_n(&sdk_allocator_owner, sdk_current_task(), __ATOMIC_RELEASE);
    allocation = (uptr)((void *(*)(unsigned long))allocator)(MSU_BS_PAGE_BYTES);
    __atomic_store_n(&sdk_allocator_owner, 0, __ATOMIC_RELEASE);
    sdk_allocator_segmented = 0;
    unregister_kprobe(observer);
    if (allocation) release((const void *)allocation); /* retain any native tag */
    sdk_config_stage = MSU_BS_CONFIG_ALLOCATE;
    if (!allocation) { error = -12; goto clear; }
    sdk_config_stage = MSU_BS_CONFIG_CAPTURE;
    if (!sdk_allocator_hits) { error = -61; goto clear; } /* ENODATA: no native call observed. */
    if (sdk_allocator_hits != 1) goto clear;
    sdk_config_stage = MSU_BS_CONFIG_ARGUMENTS;
    if (!sdk_allocator_args[1] || (sdk_allocator_args[1] & (sdk_allocator_args[1] - 1)) ||
        (!(layout->allocator_noprof & MSU_BOOT_SDK_ALLOCATOR_USER_SAMPLE) &&
            sdk_allocator_args[1] > MSU_BS_PAGE_BYTES) ||
        !(sdk_allocator_args[2] & (1UL << 55)) || sdk_allocator_args[3] <= sdk_allocator_args[2] ||
        sdk_allocator_args[1] > sdk_allocator_args[3] - sdk_allocator_args[2] ||
        !(u32)sdk_allocator_args[4] || !sdk_allocator_args[5] ||
        (!(layout->allocator_noprof & MSU_BOOT_SDK_ALLOCATOR_USER_SAMPLE) && sdk_allocator_args[6]) ||
        (u32)sdk_allocator_args[7] != 0xffffffffu) goto clear;
    *vm = (struct msu_sdk_vm){image_bytes, sdk_allocator_args[2], sdk_allocator_args[3],
        sdk_allocator_args[1], (u32)sdk_allocator_args[4], sdk_allocator_args[5],
        sdk_allocator_args[5], 0};
    error = 0;
    goto clear;
out:
    unregister_kprobe(observer);
clear:
    zero(sdk_allocator_args, sizeof sdk_allocator_args);
    sdk_allocator_hits = 0;
    return error;
}

static MSU_PH_NOCFI int configure(const struct msu_boot_sdk_layout *layout)
{
    struct msu_sdk_vm vm;
    unsigned char header[64];
    unsigned int kallsyms;
    unsigned long image_bytes;
    uptr allocator, sampler, range, observe_range, cred;
    int error;
#if MSU_BS_ENVIRONMENT
    unsigned int execve, filldir, audit;
    uptr module_free;
#endif
    sdk_config_stage = MSU_BS_CONFIG_NONE;
    if (state != MSU_BOOT_SDK_EMPTY || sdk_dynamic_ready) return -16;
    sdk_config_stage = MSU_BS_CONFIG_LAYOUT;
    if (!msu_boot_sdk_layout_valid(layout, MSU_BS_PAGE_BYTES)) return -22;
#if !MSU_BS_ENVIRONMENT
    sdk_config_stage = MSU_BS_CONFIG_ENVIRONMENT;
    if (layout->environment) return -95;
#endif
    sdk_config_stage = MSU_BS_CONFIG_ALLOCATOR;
    allocator = lookup_name(layout->allocator_noprof & MSU_BOOT_SDK_ALLOCATOR_NOPROF ? "vmalloc_noprof" : "vmalloc");
    range = lookup_name(layout->allocator_noprof & MSU_BOOT_SDK_ALLOCATOR_INNER ?
        (layout->allocator_noprof & MSU_BOOT_SDK_ALLOCATOR_NOPROF ? "___vmalloc_node_range_noprof" : "___vmalloc_node_range") :
        (layout->allocator_noprof & MSU_BOOT_SDK_ALLOCATOR_NOPROF ? "__vmalloc_node_range_noprof" : "__vmalloc_node_range"));
    /* vmalloc may return from Android's vendor bypass before either range
     * entry. vmalloc_user needs a real, user-mappable area and enters the range
     * allocator directly. The helper qualifies its ABI before setting this bit. */
    sampler = layout->allocator_noprof & MSU_BOOT_SDK_ALLOCATOR_USER_SAMPLE ?
        lookup_name(layout->allocator_noprof & MSU_BOOT_SDK_ALLOCATOR_NOPROF ? "vmalloc_user_noprof" : "vmalloc_user") : allocator;
    observe_range = sdk_observation_entry(layout->allocator_noprof &
        (MSU_BOOT_SDK_ALLOCATOR_INNER | MSU_BOOT_SDK_ALLOCATOR_SEGMENTED) ?
        (layout->allocator_noprof & MSU_BOOT_SDK_ALLOCATOR_NOPROF ? "___vmalloc_node_range_noprof" : "___vmalloc_node_range") :
        (layout->allocator_noprof & MSU_BOOT_SDK_ALLOCATOR_NOPROF ? "__vmalloc_node_range_noprof" : "__vmalloc_node_range"));
    if (!allocator || !sampler || !range || !observe_range) return -95;
    sdk_config_stage = MSU_BS_CONFIG_IMAGE_READ;
    if (read_kernel(header, (const void *)kernel_base, sizeof header)) return -14;
    sdk_config_stage = MSU_BS_CONFIG_IMAGE_HEADER;
    image_bytes = msu_sdk_le(header + 16, 8);
    if (msu_sdk_le(header + 56, 4) != 0x644d5241u || !image_bytes || image_bytes > (1UL << 32)) return -95;
    sdk_config_stage = MSU_BS_CONFIG_KALLSYMS;
    if (msu_sdk_metadata_offset(kernel_base, lookup_name("kallsyms_lookup_name"), &kallsyms)) return -95;
    /* ARM64 kernel pointers can carry a top-byte allocation tag. Classify by
     * bit 55, as for an untagged address; retain the original pointer for use. */
    sdk_config_stage = MSU_BS_CONFIG_CREDENTIAL;
    if (read_kernel(&cred, (const void *)(sdk_current_task() + layout->task_cred), sizeof cred) ||
        !(cred & (1UL << 55)) || (cred & 7)) return -95;
#if MSU_BS_ENVIRONMENT
    sdk_config_stage = MSU_BS_CONFIG_ENV_SYMBOLS;
    module_free = lookup_name(layout->execmem_free ? "execmem_free" : "module_memfree");
    if (layout->environment && (!native_avc || !module_free ||
        msu_sdk_metadata_offset(kernel_base, lookup_name("do_execveat_common"), &execve) ||
        msu_sdk_metadata_offset(kernel_base, lookup_name("filldir64"), &filldir) ||
        msu_sdk_metadata_offset(kernel_base, lookup_name("slow_avc_audit"), &audit))) return -95;
#endif
    error = sample_allocator(layout, sampler, observe_range, image_bytes, &vm);
    if (error) return error;
#if MSU_BS_ENVIRONMENT
    if (layout->environment) {
        sdk_execve_offset=execve; sdk_filldir_offset=filldir; sdk_audit_offset=audit;
        sdk_seccomp_offset=layout->seccomp; release_module=(void *)module_free;
    }
#endif
    sdk_layout = *layout; sdk_vm = vm; sdk_kallsyms = kallsyms;
    allocate = (void *)allocator;
    if ((layout->allocator_noprof & (MSU_BOOT_SDK_ALLOCATOR_INNER | MSU_BOOT_SDK_ALLOCATOR_SEGMENTED)) ==
        (MSU_BOOT_SDK_ALLOCATOR_INNER | MSU_BOOT_SDK_ALLOCATOR_SEGMENTED)) {
        allocate_segmented_range = (void *)range;
        allocate_range = 0;
    } else {
        allocate_range = (void *)range;
        allocate_segmented_range = 0;
    }
    sdk_config_stage = MSU_BS_CONFIG_NONE;
    sdk_dynamic_ready = 1;
    return 0;
}

static MSU_PH_NOCFI int bootstrap(void)
{
#ifndef MSU_BOOT_SDK_NATIVE
    static const char alphabet[] = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
#endif
    unsigned int n, offset;
#ifndef MSU_BOOT_SDK_NATIVE
    unsigned char random[MSU_BOOT_SDK_KEY_BYTES];
#endif
    unsigned char *metadata;
    unsigned long code_bytes = (uptr)msu_sdk_call_template_end - (uptr)MSU_SDK_CODE_START;
#if !defined(MSU_BOOT_SDK_NATIVE) || !MSU_INIT_BRIDGE_CFI_LEGACY
    uptr start, end, vm_start = sdk_vm.start, vm_end = sdk_vm.end;
    unsigned char prot_flag = 0;
    MSU_SDK_PGPROT prot;
#else
    uptr legacy_carrier;
#endif
    long delta;
    int error = -34, audit_published = 0;
    /* No replacement key or second allocation is issued after uncertain work. */
    if (state != MSU_BOOT_SDK_EMPTY) return bootstrap_error;
#if !MSU_BS_STATIC_BOOTSTRAP
    if (!sdk_dynamic_ready) return -11; /* EAGAIN: CONFIGURE can still run. */
#endif
    __atomic_store_n(&state, MSU_BOOT_SDK_INITIALIZING, __ATOMIC_RELEASE);
    if (!sdk_image_qualified()) { error = -95; goto failed; }
    if (!code_bytes || code_bytes > MSU_BS_PAGE_BYTES) { error = -22; goto failed; }
#if MSU_BS_STATIC_BOOTSTRAP && (!defined(MSU_BOOT_SDK_NATIVE) || !MSU_INIT_BRIDGE_CFI_LEGACY)
    if (!sdk_dynamic_ready) { vm_start = 0UL-MSU_BS_VM_START_MAG; vm_end = 0UL-MSU_BS_VM_END_MAG; }
#endif
#if defined(MSU_BOOT_SDK_NATIVE) && MSU_INIT_BRIDGE_CFI_LEGACY
    error = msu_legacy_module_ready();
    if (error) goto failed;
    legacy_carrier = (uptr)MSU_SDK_CODE_START;
    if ((legacy_carrier & (MSU_BS_PAGE_BYTES - 1)) ||
        (uptr)msu_sdk_legacy_metadata - legacy_carrier != MSU_BS_PAGE_BYTES ||
        (uptr)msu_sdk_legacy_carrier_end - legacy_carrier !=
            MSU_BS_PAGE_BYTES * (unsigned long)MSU_BS_ARENA_PAGES ||
        !msu_legacy_module_text((void *)legacy_carrier,
            MSU_BS_PAGE_BYTES * (unsigned long)MSU_BS_ARENA_PAGES)) {
        error = -ESTALE; goto failed;
    }
    arena = legacy_carrier;
    /* This owner was registered by finit_module before our module init.
     * The module loader already applied every carrier/checker relocation. */
    error = memory_nx(arena, MSU_BS_ARENA_PAGES);
    if (error) goto failed;
    error = memory_rw(arena, MSU_BS_ARENA_PAGES);
    if (error) goto failed;
#else
    if (msu_sdk_arena_range(kernel_base, sdk_vm.image_bytes, vm_start, vm_end, MSU_BS_PAGE_BYTES,
        MSU_BS_PAGE_BYTES * (unsigned long)MSU_BS_ARENA_PAGES, &start, &end)) goto failed;
    if ((!sdk_dynamic_ready || sdk_vm.flag) &&
        read_kernel(&prot_flag, (const void *)(sdk_dynamic_ready ? sdk_vm.flag : kernel_base + sdk_vm.flag), 1)) {
        error = -14; goto failed;
    }
    prot.pgprot = prot_flag ? sdk_vm.prot_set : sdk_vm.prot;
    if ((sdk_layout.allocator_noprof & (MSU_BOOT_SDK_ALLOCATOR_INNER | MSU_BOOT_SDK_ALLOCATOR_SEGMENTED)) ==
        (MSU_BOOT_SDK_ALLOCATOR_INNER | MSU_BOOT_SDK_ALLOCATOR_SEGMENTED)) {
        arena = (uptr)allocate_segmented_range(MSU_BS_PAGE_BYTES * (unsigned long)MSU_BS_ARENA_PAGES,
            sdk_vm.align, 0, MSU_BS_ARENA_PAGES, 0, MSU_BS_ARENA_PAGES,
            start, end, sdk_vm.gfp, prot, 0, -1, (const void *)bootstrap);
    } else {
        arena = (uptr)allocate_range(MSU_BS_PAGE_BYTES * (unsigned long)MSU_BS_ARENA_PAGES, sdk_vm.align,
            start, end, sdk_vm.gfp, prot, 0, -1, (const void *)bootstrap);
    }
    if (!arena) { error = -12; goto failed; }
#endif
    metadata = (void *)(arena + MSU_BS_PAGE_BYTES);
    if (arena & (MSU_BS_PAGE_BYTES - 1)) goto failed;
#if defined(MSU_BOOT_SDK_NATIVE) && MSU_INIT_BRIDGE_CFI_LEGACY
    offset = 0; /* Signed descriptor/append64 is mandatory for this carrier. */
#else
    if (msu_sdk_metadata_offset(kernel_base, (uptr)metadata, &offset)) goto failed;
#endif
    delta = (long)((uptr)metadata - (arena + ((uptr)msu_sdk_call_blr - (uptr)MSU_SDK_CODE_START)));
#ifdef MSU_BOOT_SDK_NATIVE
    for (n = 0; n < sizeof native_credential; ++n) root_key[n] = native_credential[n];
#else
    random_bytes(random, sizeof random);
    for (n = 0; n < sizeof random; ++n) root_key[n] = alphabet[random[n] % 62];
    zero(random, sizeof random);
#endif
    for (n = 0; n < sizeof original_root_key; ++n) original_root_key[n] = root_key[n];
    if (msu_sdk_bind_key(root_key, delta)) { error = -22; goto failed; }
#ifdef MSU_BOOT_SDK_NATIVE
    if (memcmp(original_root_key, native_credential, MSU_BOOT_SDK_KEY_BYTES)) {
        error = -22; goto failed;
    }
#endif
#if defined(MSU_BOOT_SDK_NATIVE) && MSU_INIT_BRIDGE_CFI_LEGACY
    /* Keep the linked code/checker identities. Only owned data pages clear. */
    zero(metadata, MSU_BS_PAGE_BYTES * (unsigned long)(MSU_BS_ARENA_PAGES - 1));
#else
    zero((void *)arena, MSU_BS_PAGE_BYTES * (unsigned long)MSU_BS_ARENA_PAGES);
    for (n = 0; n < code_bytes; ++n) ((unsigned char *)arena)[n] = MSU_SDK_CODE_START[n];
#endif
    msu_sdk_store(metadata, offset, 4);
    msu_sdk_store(metadata + 4, sdk_kallsyms, 4);
#if defined(MSU_BOOT_SDK_NATIVE) && MSU_INIT_BRIDGE_CFI_LEGACY
    msu_sdk_store(metadata + MAPLESU_APPEND64_HEADER, MAPLESU_APPEND64_MAGIC, 4);
    msu_sdk_store(metadata + MAPLESU_APPEND64_HEADER + 4, MAPLESU_APPEND64_BYTES, 2);
    msu_sdk_store(metadata + MAPLESU_APPEND64_BASE, (u64)(uptr)metadata - (u64)kernel_base, 8);
    msu_sdk_store(metadata + MAPLESU_APPEND64_LOOKUP, sdk_kallsyms, 8);
#endif
    for (n = 0; n < 6; ++n) metadata[8 + n] = (const unsigned char[]){'/','d','a','t','a','/'}[n];
    for (n = 0; n < 16; ++n) metadata[14 + n] = root_key[n];
    for (n = 0; n < 5; ++n) metadata[30 + n] = (const unsigned char[]){'/','i','n','i','t'}[n];
#if MSU_BS_ENVIRONMENT
    if (sdk_layout.environment) {
        uptr lookup_at = (code_bytes + 15) & ~15UL;
        uptr lookup_bytes = msu_sdk_lookup_end - msu_sdk_lookup_template;
        uptr avc_at = (lookup_at + lookup_bytes + 15) & ~15UL;
        uptr avc_bytes = msu_sdk_avc_end - msu_sdk_avc_template;
        uptr branch_delta = msu_sdk_avc_branch - msu_sdk_avc_template;
        if (avc_at + avc_bytes > MSU_BS_PAGE_BYTES) { error = -34; goto failed; }
#if defined(MSU_BOOT_SDK_NATIVE) && MSU_INIT_BRIDGE_CFI_LEGACY
        if (arena + lookup_at != (uptr)msu_sdk_lookup_template ||
            arena + avc_at != (uptr)msu_sdk_avc_template) { error = -ESTALE; goto failed; }
        offset = 0;
        msu_sdk_store(metadata + MAPLESU_APPEND64_LOOKUP,
            (u64)(arena + lookup_at) - (u64)kernel_base, 8);
#else
        if (msu_sdk_metadata_offset(kernel_base, arena + lookup_at, &offset)) {
            error = -34; goto failed;
        }
        for (n = 0; n < lookup_bytes; ++n)
            ((unsigned char *)arena)[lookup_at + n] = msu_sdk_lookup_template[n];
#endif
        msu_sdk_store((void *)(arena + lookup_at + (msu_sdk_lookup_target - msu_sdk_lookup_template)),
            (uptr)sdk_lookup, sizeof(uptr));
        msu_sdk_store(metadata + 4, offset, 4);
#if defined(MSU_BOOT_SDK_NATIVE) && MSU_INIT_BRIDGE_CFI_LEGACY
        offset = 0;
        msu_sdk_store(metadata + MAPLESU_APPEND64_AVC,
            (u64)(arena + avc_at) - (u64)kernel_base, 8);
#else
        for (n = 0; n < avc_bytes; ++n)
            ((unsigned char *)arena)[avc_at + n] = msu_sdk_avc_template[n];
        if (msu_sdk_metadata_offset(kernel_base, arena + avc_at, &offset)) {
            error = -34; goto failed;
        }
#endif
        avc_result = arena + avc_at + (msu_sdk_avc_result - msu_sdk_avc_template);
        msu_sdk_store(metadata + 36, sdk_execve_offset, 4);
        metadata[40] = MSU_BS_EXECVE_REG; metadata[41] = MSU_BS_EXECVE_CHAR;
#ifdef MSU_BOOT_SDK_NATIVE
        {
            unsigned int filename;
            error = msu_init_bridge_environment_filename(&filename);
            if (error) goto failed;
            metadata[41] = filename;
        }
#endif
        msu_sdk_store(metadata + 42, offset, 4);
        msu_sdk_store(metadata + 46, (branch_delta + 4) / 4, 2);
        msu_sdk_store(metadata + 48, sdk_filldir_offset, 4);
        msu_sdk_store(metadata + 52, sdk_seccomp_offset, 2);
        msu_sdk_store(metadata + 54, sdk_audit_offset, 4);
        audit_published = sdk_layout.environment && sdk_audit_offset != 0;
        code_bytes = avc_at + avc_bytes;
    }
#endif
    error = memory_nx(arena, MSU_BS_ARENA_PAGES);
    if (error) goto failed;
    error = memory_ro(arena, MSU_BS_ARENA_PAGES);
    if (error) goto failed;
    flush_code(arena, arena + code_bytes);
    error = memory_x(arena, 1);
    if (error) goto failed;
#if MSU_BS_ENVIRONMENT
    error = memory_x(arena + 2UL * MSU_BS_PAGE_BYTES, MSU_BS_IDLE_PAGES);
    if (error) goto failed;
#endif
#ifdef MSU_BOOT_SDK_NATIVE
    native_host_started = 1;
    error = msu_init_bridge_host_start(kernel_base, metadata,
#if MSU_INIT_BRIDGE_CFI_LEGACY
        MAPLESU_APPEND64_BYTES,
#else
        audit_published ? MAPLESU_LEGACY_METADATA_AUDIT_BYTES : MAPLESU_LEGACY_METADATA_V2_BYTES,
#endif
        root_key);
    if (error) goto failed;
#endif
    *(const char **)(probe + MSU_BS_KP_NAME) = "__arm64_sys_execve";
    *(uptr *)(probe + MSU_BS_KP_PRE) = (uptr)msu_boot_sdk_pre;
    error = register_kprobe(probe);
    if (error) goto failed;
    probe_installed = 1;
#if defined(MSU_BOOT_SDK_NATIVE) && MSU_INIT_BRIDGE_CFI_LEGACY
    /* Keep the kernel probe identity separate from the compiler-owned typed
     * callable entry stored in native_execve. Accept only the exact body/BTI. */
    {
        uptr entry = sdk_observation_entry("__arm64_sys_execve");
        uptr probed = *(uptr *)(probe + MSU_BS_KP_ADDR);
        u32 instruction;
        if (!entry || (probed != entry && (probed != entry + 4 ||
            read_kernel(&instruction, (const void *)entry, sizeof instruction) ||
            (instruction != 0xd503245fU && instruction != 0xd50324dfU)))) {
            error = -ESTALE; goto failed;
        }
    }
#else
    if (*(uptr *)(probe + MSU_BS_KP_ADDR) != native_execve &&
        !(*(uptr *)(probe + MSU_BS_KP_ADDR) == native_execve + 4 &&
          *(const u32 *)native_execve == 0xd503245f)) { error = -95; goto failed; }
#endif
    request_v2_init(audit_published);
    __atomic_store_n(&state, MSU_BOOT_SDK_READY, __ATOMIC_RELEASE);
    return 0;
failed:
    if (probe_installed) { unregister_kprobe(probe); probe_installed = 0; }
#ifdef MSU_BOOT_SDK_NATIVE
    if (native_host_started) {
        int discarded = msu_init_bridge_host_abort();
        if (discarded) {
            bootstrap_error = error;
            __atomic_store_n(&state, MSU_BOOT_SDK_FAILED, __ATOMIC_RELEASE);
            return discarded;
        }
        native_host_started = 0;
    }
#endif
    if (arena && !memory_nx(arena, MSU_BS_ARENA_PAGES) && !memory_rw(arena, MSU_BS_ARENA_PAGES)) {
#if !defined(MSU_BOOT_SDK_NATIVE) || !MSU_INIT_BRIDGE_CFI_LEGACY
        release((void *)arena);
#else
        /* The real module loader alone frees this linked carrier section. */
        zero((void *)(arena + MSU_BS_PAGE_BYTES),
            MSU_BS_PAGE_BYTES * (unsigned long)(MSU_BS_ARENA_PAGES - 1));
#endif
        arena = 0;
    }
    avc_result = 0;
    zero(root_key, sizeof root_key);
    zero(original_root_key, sizeof original_root_key);
    bootstrap_error = error;
    __atomic_store_n(&state, MSU_BOOT_SDK_FAILED, __ATOMIC_RELEASE);
    return error;
}

MSU_PH_NOCFI long msu_boot_sdk_control(uptr user)
{
    struct msu_boot_sdk_request request;
    struct msu_boot_sdk_layout layout;
    unsigned int n;
    int error;
    if (!root_subject || !root_subject()) return -1;
    if (!take(&control_busy)) return -16;
    error = copy_in(&request, (const void *)user, sizeof request) ? -14 : 0;
    if (error) goto out;
    if (request.version != MSU_BOOT_SDK_VERSION || request.reserved ||
        request.command > MSU_BOOT_SDK_CONFIGURE) error = -22;
    for (n = 0; n < sizeof request.padding; ++n)
        if (request.padding[n]) error = -22;
    if (!error && request.command == MSU_BOOT_SDK_CONFIGURE) {
        if (copy_in(&layout, (const void *)(user + sizeof request), sizeof layout)) error = -14;
        else error = configure(&layout);
        zero(&layout, sizeof layout);
    }
    if (!error && request.command == MSU_BOOT_SDK_BOOTSTRAP) error = bootstrap();
    if (!error && state == MSU_BOOT_SDK_FAILED) error = bootstrap_error;
    request.status = error; request.state = __atomic_load_n(&state, __ATOMIC_ACQUIRE);
    request.kernel_base = request.state == MSU_BOOT_SDK_READY ? kernel_base : 0;
    request.capabilities = request.state == MSU_BOOT_SDK_READY ?
        MSU_BOOT_SDK_CHANNEL | MSU_BOOT_SDK_KERNEL_BASE
#if MSU_BS_ENVIRONMENT
        | (sdk_layout.environment ? MSU_BOOT_SDK_ENVIRONMENT : 0)
#endif
        : 0;
    zero(request.key, sizeof request.key);
    request.padding[0] = 1; /* runtime-layout protocol, independent of v1 JSON */
    request.padding[1] = MSU_BS_ENVIRONMENT ? 1 : 0;
    request.padding[2] = sdk_dynamic_ready ? 1 : 0;
    request.padding[3] = (unsigned char)sdk_config_stage;
    request.padding[4] = 1; /* qualified inner allocator variant selection */
    request.padding[5] = 1; /* qualified vmalloc_user sampler selection */
    request.padding[6] = MSU_BOOT_SDK_SEGMENTED_OBSERVER | MSU_BOOT_SDK_SEGMENTED_CALLABLE;
    if (!error && request.state == MSU_BOOT_SDK_READY)
        for (n = 0; n < sizeof root_key; ++n) request.key[n] = root_key[n];
    error = copy_out((void *)user, &request, sizeof request) ? -14 : 0;
    zero(&request, sizeof request);
out:
    __atomic_store_n(&control_busy, 0, __ATOMIC_RELEASE);
    return error;
}
MSU_PH_NOCFI int msu_boot_sdk_init(uptr (*lookup)(const char *), int (*subject)(void),
    int (*handover)(void))
{
    lookup_name = lookup; root_subject = subject; root_handover = handover;
#define RESOLVE(variable, name) variable = (void *)lookup_name(name)
    RESOLVE(copy_in, "copy_from_user_nofault"); RESOLVE(copy_out, "copy_to_user_nofault");
    RESOLVE(read_kernel, "copy_from_kernel_nofault"); RESOLVE(write_kernel, "copy_to_kernel_nofault");
#if MSU_BS_ALLOCATOR_NOPROF
    RESOLVE(allocate, "vmalloc_noprof");
    RESOLVE(allocate_range, "__vmalloc_node_range_noprof");
#else
    RESOLVE(allocate, "vmalloc");
    RESOLVE(allocate_range, "__vmalloc_node_range");
#endif
#if MSU_BS_STATIC_BOOTSTRAP
    if (!allocate || !allocate_range) {
        RESOLVE(allocate, "vmalloc"); RESOLVE(allocate_range, "__vmalloc_node_range");
        if (!allocate || !allocate_range) {
            RESOLVE(allocate, "vmalloc_noprof"); RESOLVE(allocate_range, "__vmalloc_node_range_noprof");
        }
    }
#else
    /* CONFIGURE selects and qualifies the callable range ABI. A target with
     * only the segmented inner entry has no nine-argument range to bind here. */
    if (!allocate) {
        RESOLVE(allocate, "vmalloc");
        if (!allocate) RESOLVE(allocate, "vmalloc_noprof");
    }
#endif
    RESOLVE(release, "vfree");
#ifndef MSU_BOOT_SDK_NATIVE
    RESOLVE(random_bytes, "get_random_bytes");
#endif
    RESOLVE(memory_ro, "set_memory_ro"); RESOLVE(memory_rw, "set_memory_rw");
    RESOLVE(memory_x, "set_memory_x"); RESOLVE(memory_nx, "set_memory_nx");
    RESOLVE(flush_code, "caches_clean_inval_pou");
    if (!flush_code) RESOLVE(flush_code, "__flush_icache_range");
    RESOLVE(prepare_cred, "prepare_creds"); RESOLVE(commit_cred, "commit_creds");
    RESOLVE(hold_cred, "get_task_cred"); RESOLVE(drop_cred, "__put_cred");
    native_execve = lookup_name("__arm64_sys_execve"); kernel_base = lookup_name("_text");
    RESOLVE(legacy_provider_get, "maplesu_legacy_hook_provider_get");
    RESOLVE(legacy_provider_put, "maplesu_legacy_hook_provider_put");
    if (legacy_provider_get && legacy_provider_put) {
        sdk_request_descriptor.legacy.magic = MAPLESU_LEGACY_HOOK_METADATA_MAGIC;
        sdk_request_descriptor.legacy.schema = MAPLESU_LEGACY_HOOK_SCHEMA;
        sdk_request_descriptor.legacy.length = sizeof sdk_request_descriptor.legacy;
        sdk_request_descriptor.legacy.provider_get_displacement =
            (u64)(uptr)legacy_provider_get - (u64)(uptr)&sdk_request_metadata;
        sdk_request_descriptor.legacy.provider_put_displacement =
            (u64)(uptr)legacy_provider_put - (u64)(uptr)&sdk_request_metadata;
    }
#if MSU_BS_ENVIRONMENT
    native_avc = lookup_name("avc_denied");
#if defined(MSU_BS_EXECMEM_FREE) && MSU_BS_EXECMEM_FREE
    RESOLVE(release_module, "execmem_free");
#else
    RESOLVE(release_module, "module_memfree");
#endif
    if (!release_module) RESOLVE(release_module, "execmem_free");
    if (!release_module) RESOLVE(release_module, "module_memfree");
#endif
#undef RESOLVE
    if (!copy_in || !copy_out || !read_kernel || !write_kernel || !allocate || !release ||
#if MSU_BS_STATIC_BOOTSTRAP
        !allocate_range ||
#endif
        !memory_ro || !memory_rw || !memory_x || !memory_nx || !flush_code ||
        !prepare_cred || !commit_cred || !hold_cred || !drop_cred || !native_execve || !kernel_base
#ifndef MSU_BOOT_SDK_NATIVE
        || !random_bytes || !root_handover
#endif
        )
        return -2;
    return 0;
}
#ifdef MSU_BOOT_SDK_NATIVE
int msu_boot_sdk_native_start(const struct msu_boot_sdk_layout *layout,
    const char credential[MSU_BOOT_SDK_KEY_BYTES])
{
    unsigned int n;
    int error;

    if (!in_task() || in_atomic() || irqs_disabled()) return -EWOULDBLOCK;
    if (state != MSU_BOOT_SDK_EMPTY || sdk_dynamic_ready) return -EBUSY;
    if (!layout || !credential) return -EINVAL;
    for (n = 0; n < MSU_BOOT_SDK_KEY_BYTES; ++n) {
        unsigned char c = credential[n];
        if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')))
            return -EINVAL;
    }
#if MSU_INIT_BRIDGE_CFI_KCFI
    /* Keep the compiler-owned hash and reject other prefix placements. */
    if (MSU_SDK_CODE_ENTRY != 4 || cfi_get_offset() != 4 ||
        !cfi_get_func_hash((void *)msu_sdk_call_template)) return -EOPNOTSUPP;
#elif MSU_INIT_BRIDGE_CFI_NONE
    /* A naturally uninstrumented target has no copied CFI prefix. */
    if (MSU_SDK_CODE_ENTRY != 0) return -EOPNOTSUPP;
#else
    error = msu_legacy_module_ready();
    if (error) return error;
    if (MSU_SDK_CODE_ENTRY != 0 ||
        (uptr)msu_sdk_legacy_metadata - (uptr)MSU_SDK_CODE_START != MSU_BS_PAGE_BYTES)
        return -EPROTO;
#endif
    error = msu_boot_sdk_init(msu_init_bridge_checked_lookup, NULL, NULL);
    if (error) return error;
    memcpy(native_credential, credential, MSU_BOOT_SDK_KEY_BYTES);
    native_credential[MSU_BOOT_SDK_KEY_BYTES] = 0;
    error = configure(layout);
    if (!error) error = bootstrap();
    memzero_explicit(native_credential, sizeof native_credential);
    return error;
}

int msu_boot_sdk_native_abort(void)
{
    if (state == MSU_BOOT_SDK_READY) return -EBUSY;
    /* bootstrap owns rollback. Nonzero retained objects mean the module must
     * stay loaded; caller cannot free their callbacks after an init error. */
    return arena || native_host_started ? -EINPROGRESS : 0;
}
#endif
MSU_PH_NOCFI void msu_boot_sdk_cleanup(void)
{
    /* The authenticated resident control pins the LKM before bootstrap. Only
     * an unused listener can reach module cleanup through native unload. */
    if (probe_installed) unregister_kprobe(probe);
}
