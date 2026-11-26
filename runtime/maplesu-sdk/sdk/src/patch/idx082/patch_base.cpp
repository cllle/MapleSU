// patch base implementation.
//
#include "kernel_module_kit_umbrella.h"

#include <cstdint>
#include <cstdio>
#include <string>
#include <vector>

#include "maplesu_request_private.h"
#include "../../module/su_redirect_tu/su_uid_lifetime_private.h"
#include "../../module/su_redirect_tu/su_uid_index_private.h"
#include "default_module/module_su_redirect/patch_base.h"
#include "private_module_su_patch.h"
#include "maplesu_hook_private.h"

namespace default_modules::module_su_redirect {

namespace {
thread_local SuPatchBatch* su_patch_batch = nullptr;
}
KModErr begin_su_patch_batch(SuPatchBatch& batch) {
    if (su_patch_batch || batch.count) return KModErr::ERR_MODULE_PARAM;
    su_patch_batch = &batch;
    return KModErr::OK;
}
KModErr end_su_patch_batch(SuPatchBatch& batch, bool commit) {
    if (su_patch_batch != &batch) return KModErr::ERR_MODULE_PARAM;
    su_patch_batch = nullptr;
    kernel_module::HookHandle installed[13] = {};
    uint32_t count = 0;
    KModErr first = KModErr::OK;
    bool retired = true;
    if (commit) for (uint32_t i = 0; i < batch.count; ++i) {
        kernel_module::HookHandle handle = nullptr;
        try {
            first = kernel_module::install_kernel_function_before_hook(
                batch.entries[i], batch.payloads[i], &handle);
        } catch (...) { first = KModErr::ERR_MODULE_ASM; retired = false; }
        if (handle) installed[count++] = handle;
        else if (is_ok(first)) {
            first = KModErr::ERR_MODULE_ASM;
            retired = false; // A successful publication without its handle is unknown.
        } else {
            const auto reply = kernel_module::maplesu_hook_reply();
            constexpr uint32_t exposed = MAPLESU_EFFECT_TEXT_ATTEMPTED | MAPLESU_EFFECT_WRITTEN |
                MAPLESU_EFFECT_PUBLICATION_PUBLISHED | MAPLESU_EFFECT_PUBLICATION_RETAINED |
                MAPLESU_EFFECT_GATE_PINNED | MAPLESU_EFFECT_QUARANTINED | MAPLESU_EFFECT_PENDING;
            const bool observed_unexposed = reply.completed && !(reply.effects & exposed) &&
                (reply.command == MAPLESU_REQUEST_PUBLICATION_PREPARE ||
                 reply.command == MAPLESU_REQUEST_PUBLICATION_PUBLISH ||
                 (reply.command == MAPLESU_REQUEST_PUBLICATION_RETIRE &&
                  (reply.effects & MAPLESU_EFFECT_RELEASED)));
            if (!observed_unexposed) retired = false;
        }
        if (is_failed(first)) {
            const int original_errno = errno;
            std::printf("SU Hook commit failed index=%u entry=0x%llx rc=%lld\n", i,
                static_cast<unsigned long long>(batch.entries[i]),
                static_cast<long long>(first));
            errno = original_errno;
            break;
        }
    }
    if (!commit || is_failed(first)) {
        const kernel_module::HookCleanupReceiptScope preserve_receipt;
        while (count) {
            KModErr cleanup;
            try { cleanup = kernel_module::uninstall_kernel_hook(installed[--count]); }
            catch (...) { cleanup = KModErr::ERR_MODULE_ASM; }
            if (is_failed(cleanup)) {
                retired = false;
                std::printf("SU Hook rollback: %s\n", to_string(cleanup).c_str());
                if (is_ok(first)) first = cleanup;
            }
        }
    } else if (count) {
        retired = false;
    }
    batch.readers_retired = retired;
    batch.count = 0;
    return first;
}

// Layout: +0 task.cred offset / +4 cred.uid offset (legacy name) / +8 cred.euid offset / +16 string(24).
PatchBase::PatchBase(uint32_t task_cred_offset, uint32_t cred_uid_offset, uint32_t cred_euid_offset,
                     const char* fake_su_path)
    : field0_(task_cred_offset),
      cred_atomic_usage_size_(cred_uid_offset),
      field2_(cred_euid_offset),
      fake_su_path_(fake_su_path) {}

PatchBase::PatchBase(const PatchBase& other)
    : field0_(other.field0_),
      cred_atomic_usage_size_(other.cred_atomic_usage_size_),
      field2_(other.field2_),
      fake_su_path_(other.fake_su_path_) {}

PatchBase::~PatchBase() = default;

std::string PatchBase::get_fake_su_path() { return fake_su_path_; }

int PatchBase::get_cred_atomic_usage_size() { return cred_atomic_usage_size_; }

// 读目标函数开头 8 字节，按 " %02hhx" 逐字节打印后换行；读失败静默返回。
// 形参 2 (unsigned int) 在函数体内未使用（ThinLTO DAE 已删除签名中的它，
// 但符号名保留 Emj），保留形参以维持 ABI；调用点走默认实参，不传第二实参，
// 因此调用方不产字节数计算。
static void printf_kernel_mem(uint64_t kaddr, unsigned int size = 0);

static void printf_kernel_mem(uint64_t kaddr, unsigned int size) {
    (void)size;
    std::vector<uint8_t> bytes(8);
    const KModErr read_error =
        kernel_module::read_kernel_mem(kaddr, bytes.data(), static_cast<uint32_t>(bytes.size()));
    if (is_failed(read_error)) {
        return;
    }
    for (std::size_t i = 0; i < bytes.size(); ++i) {
        std::printf(" %02hhx", static_cast<int>(bytes[i]));
    }
    std::putchar('\n');
}

//          只有 ERR_MODULE_HOOK_INSN_NOT_SUPPORT(-19971) 才额外 dump 目标函数。
//          ERR_MODULE_HOOK_INSN_NOT_SUPPORT，且与"hook 安装器拒绝了目标函数
//          开头指令，所以把原指令打出来"的语义一致。
KModErr PatchBase::patch_kernel_before_hook(uint64_t kaddr, const Assembler* a) {
    if (su_patch_batch && su_patch_batch->count == 13) return KModErr::ERR_MODULE_PARAM;
    std::vector<uint8_t> bytes = aarch64_asm_to_bytes(a);
    if (su_patch_batch) {
        const auto index = su_patch_batch->count;
        su_patch_batch->entries[index] = kaddr;
        su_patch_batch->payloads[index] = std::move(bytes);
        ++su_patch_batch->count;
        return KModErr::OK; // Every payload is prepared before the first publication.
    }
    const KModErr error = kernel_module::install_kernel_function_before_hook(kaddr, bytes, nullptr);
    if (error == KModErr::ERR_MODULE_HOOK_INSN_NOT_SUPPORT) {
        printf_kernel_mem(kaddr);
    }
    return error;
}

KModErr PatchBase::patch_kernel_after_hook(uint64_t kaddr, const Assembler* a) {
    const std::vector<uint8_t> bytes = aarch64_asm_to_bytes(a);
    const KModErr error = kernel_module::install_kernel_function_after_hook(kaddr, bytes, nullptr);
    if (error == KModErr::ERR_MODULE_HOOK_INSN_NOT_SUPPORT) {
        printf_kernel_mem(kaddr);
    }
    return error;
}

//
// 生成"当前进程是否允许 su"的判定代码，结论写在 x10：
//   x10 = 0（默认：不允许） / x10 = 1（允许）
//
//   944/950/956  a->newLabel() ×3           （%4/%5/%6）
//   969          mov x10, Imm(0)            （%7 = Imm(0)）
//   971-975      export_symbol::get_current(a, x11)
//   977-984      ldr x11, Mem(x11, this->field0_)       // +0：cred 在 task 内的偏移
//   Current      ldr w11, Mem(x11, cred_atomic_usage_size_) // +4: resolved cred.uid (u32).
//   996          cbz w11, <deny>
//   998-1002     aarch64_asm_mov_x(a, x12, arg)        // x12 = 调用方给的地址
//   1003-1010    ldr x13, Mem(x12)                     // x13 = *(u64*)arg
//   1011         cbz x13, <end>
//   1012         mov x12, xzr                         // x12 = 0：数组下标
//   1013         bind(<loop>)
//   1015-1026    ldr w14, Mem(x13, x12)                // w14 = list[i]
//   1027         cbz w14, <end>
//   1028-1036    add x12, x12, Imm(4)
//   1037         cmp w11, w14
//   1038         b.ne <loop>                          // _emitI(0x1800001C) = b.ne
//   1039         bind(<deny>)
//   1040-1049    mov x10, Imm(1)                      // 命中：允许
//   1050         bind(<end>)
//
// field0_ stores the independently resolved task_struct.cred offset. The legacy
// cred_atomic_usage_size_ member stores the independently resolved cred.uid offset;
// arg points to a full u32 UID allowlist (its first u64 is the array address).
// 命中即放行。调用方 patch_do_execve/sys_statx/... 传进来的实参即该数组地址。
void PatchBase::emit_check_current_allow_su_to_x10(Assembler* a, uint64_t arg) {
    // Only the authenticated Legacy mode may use the compatibility UID table.
    // Owner/helper authorization is supplied by the retained native SU gates.
    const uint64_t auth_state = kernel_module::maplesu_request_auth_state();
    if (!kernel_module::maplesu_request_auth_legacy(auth_state)) {
        a->mov(x10, Imm(0));
        errno = kernel_module::maplesu_request_auth_authenticated(auth_state)
            ? EOPNOTSUPP : EACCES;
        return;
    }
    Label label_end = a->newLabel();    // %4
    Label label_allow = a->newLabel();  // %5
    Label label_loop = a->newLabel();   // %6

    a->mov(x10, Imm(0));
    if (is_failed(kernel_module::export_symbol::get_current_checked(a, x11))) return;
    a->cbz(x11, label_end);

    a->ldr(x11, Mem(x11, static_cast<int32_t>(field0_)));
    a->cbz(x11, label_end);
    a->ldr(w11, Mem(x11, static_cast<int32_t>(cred_atomic_usage_size_)));
    a->cbz(w11, label_allow);

    aarch64_asm_mov_x(a, x12, arg);
    a->ldar(x13, ptr(x12));
    a->cbz(x13, label_end);

    a->mov(x12, xzr);
    a->bind(label_loop);
    // Legacy writers admit at most 4095 UIDs and one terminator.
    a->cmp(x12, Imm(4096 * sizeof(uint32_t)));
    a->b(CondCode::kHS, label_end);
    a->ldr(w14, Mem(x13, x12));
    a->cbz(w14, label_end);
    a->add(x12, x12, Imm(4));
    a->cmp(w11, w14);
    a->b(CondCode::kNE, label_loop);

    a->bind(label_allow);
    a->mov(x10, Imm(1));
    a->bind(label_end);
}

KModErr PatchBase::emit_check_current_allow_su_to_x10_checked(Assembler* a, uint64_t arg) {
    const auto state = kernel_module::maplesu_request_auth_state();
    if (!kernel_module::maplesu_request_auth_legacy(state)) {
        errno = kernel_module::maplesu_request_auth_authenticated(state) ? EOPNOTSUPP : EACCES;
        return KModErr::ERR_MODULE_TEST_CHANNEL;
    }
    using namespace maplesu::su_auth::detail;
    uint64_t state_address = 0, lock = 0, unlock = 0;
    auto error = maplesu_su_uid_index_state(arg, state_address);
    if (is_failed(error)) return error;
    error = maplesu_su_uid_reader_functions(lock, unlock);
    if (is_failed(error)) return error;
    {
        RegProtectGuard preserve(a, x0, x1, x2, x3, x4, x5, x6, x7, x8,
            x9, x11, x12, x13, x14, x15, x16, x17, x19, x20, x30);
        const auto done = a->newLabel(), allow = a->newLabel();
        const auto indexed = a->newLabel(), chain = a->newLabel();
        const auto scan = a->newLabel(), scan_loop = a->newLabel();
        a->mov(x20, 0);
        aarch64_asm_mov_x(a, x16, lock);
        a->blr(x16);
        RETURN_IF_ERROR(kernel_module::export_symbol::get_current_checked(a, x11));
        a->cbz(x11, done);
        a->ldr(x11, Mem(x11, static_cast<int32_t>(field0_)));
        a->cbz(x11, done);
        a->ldr(w19, Mem(x11, static_cast<int32_t>(cred_atomic_usage_size_)));
        a->cbz(w19, allow);
        aarch64_asm_mov_x(a, x12, arg);
        a->ldar(x12, ptr(x12));
        a->cbz(x12, done);
        for (unsigned slot = 0; slot < 2; ++slot) {
            const auto next = a->newLabel();
            aarch64_asm_mov_x(a, x13, state_address + offsetof(UidIndexState, slots) + slot * sizeof(uint64_t));
            a->ldar(x13, ptr(x13));
            a->cbz(x13, next);
            a->ldr(x14, ptr(x13));
            a->cmp(x14, x12);
            a->b(CondCode::kEQ, indexed);
            a->bind(next);
        }
        a->b(scan); // Only old, unregistered plain lists use compatibility scan.
        a->bind(indexed);
        a->ldr(w17, Mem(x13, offsetof(UidIndexHeader, count)));
        a->cmp(w17, uid_index_max);
        a->b(CondCode::kHI, done);
        a->mov(w14, UINT32_C(0x61c88647));
        a->mul(w14, w19, w14);
        a->lsr(w14, w14, 24);
        a->lsl(x14, x14, 2);
        a->add(x15, x13, x14);
        a->ldr(w14, Mem(x15, offsetof(UidIndexHeader, buckets)));
        a->bind(chain);
        a->cbz(w14, done);
        a->cbz(w17, done);
        a->ldr(w16, Mem(x13, offsetof(UidIndexHeader, count)));
        a->cmp(w14, w16);
        a->b(CondCode::kHI, done);
        a->sub(w17, w17, 1);
        a->sub(w14, w14, 1);
        a->lsl(x14, x14, 3);
        a->add(x15, x13, x14);
        a->ldr(w16, Mem(x15, sizeof(UidIndexHeader)));
        a->cmp(w19, w16);
        a->b(CondCode::kEQ, allow);
        a->ldr(w14, Mem(x15, sizeof(UidIndexHeader) + offsetof(UidIndexEntry, next)));
        a->b(chain);
        a->bind(scan);
        a->mov(x14, 0);
        a->bind(scan_loop);
        a->cmp(x14, 4096 * sizeof(uint32_t));
        a->b(CondCode::kHS, done);
        a->ldr(w16, Mem(x12, x14));
        a->cbz(w16, done);
        a->cmp(w19, w16);
        a->b(CondCode::kEQ, allow);
        a->add(x14, x14, sizeof(uint32_t));
        a->b(scan_loop);
        a->bind(allow);
        a->mov(x20, 1);
        a->bind(done);
        aarch64_asm_mov_x(a, x16, unlock);
        a->blr(x16);
        a->mov(x10, x20);
    }
    return KModErr::OK;
}

//
// 把用户态 (src) 的 NUL 结尾字符串逐字节拷贝进内核缓冲 (dst)，最多 max_len 字节：
//   * 每轮 copy_from_user 1 字节 → 再从 dst 侧 ldrb 回读 → 为 0 即结束；
//   * 结果写 x0：1 = 找到 NUL 并拷完，0 = 超长 / err 失败。
//
//   dst_off (第1个 acquireX) / src_off (2) / i (3) / limit (4) / found (5) / byte (GpW)
//
//   2453-2455  IdleRegPool::make(dst, src)
//   2456-2470  acquireX×5 + acquireW
//   2472-2484  RegProtectGuard(a, excluding_x0(pool.getUsed()))
//   2494-2501  newLabel ×3（loop / found / end）
//   2508/2510  mov found, xzr ; mov i, xzr
//   2512       bind(loop)
//   2519-2521  add dst_off, dst, i ; add src_off, src, i
//   2527-2532  { RegProtectGuard(a, x0); copy_from_user(a, err, dst_off, src_off, 1); }
//   2535-2538  if (is_failed(err)) return;
//   2540-2545  ldrb byte, Mem(dst_off)
//   2546       cbz byte, found
//   2548-2554  add i, i, #1
//   2556-2566  aarch64_asm_mov_x(a, limit, max_len)
//   2567-2571  cmp i, limit
//   2572       b.cs end          (_emitI(0x2000001C)，cc=4=kCS)
//   2573       b loop
//   2574-2582  bind(found) ; mov found, Imm(1)
//   2583       bind(end) ; mov x0, found
void PatchBase::emit_dynamic_copy_string_from_user(Assembler* a, KModErr& err, GpX dst, GpX src,
                                                   uint64_t max_len) {
    IdleRegPool pool = IdleRegPool::make(dst, src);
    GpX dst_off = pool.acquireX();  // %10
    GpX src_off = pool.acquireX();  // %11
    GpX index = pool.acquireX();    // %12
    GpX limit = pool.acquireX();    // %13
    GpX found = pool.acquireX();    // %14
    GpW byte = pool.acquireW();     // %15
    GpX residue = pool.acquireX();
    RegProtectGuard guard(a, excluding_x0(pool.getUsed()));

    Label label_loop = a->newLabel();   // %19
    Label label_found = a->newLabel();  // %20
    Label label_end = a->newLabel();    // %21

    a->mov(found, xzr);
    a->mov(index, xzr);
    if (max_len == 0) {
        a->mov(x0, found);
        return;
    }

    a->bind(label_loop);
    a->add(dst_off, dst, index);
    a->add(src_off, src, index);
    {
        // x0 是 copy_from_user 的返回值寄存器，调用期必须保护现场。
        RegProtectGuard call_guard(a, x0);
        kernel_module::export_symbol::copy_from_user(a, err, dst_off, src_off, 1);
        a->mov(residue, x0);
    }
    if (is_failed(err)) {
        return;
    }
    a->cbnz(residue, label_end);

    a->ldrb(byte, asmjit::a64::ptr(dst_off));
    a->cbz(byte, label_found);

    a->add(index, index, Imm(1));
    aarch64_asm_mov_x(a, limit, max_len);
    a->cmp(index, limit);
    a->b(CondCode::kCS, label_end);
    a->b(label_loop);

    a->bind(label_found);
    a->mov(found, Imm(1));
    a->bind(label_end);
    a->mov(x0, found);
}

// aarch64_asm_set_x_cstr_ptr / aarch64_asm_adr_x 被内联展开）
//
// 生成"用户态文件名是否等于 /system/bin/su"的判定代码，结果写 x0：
//   x0 = 1（是 su）/ 0（不是 / 拷贝失败 / 为空）
//
//   1353-1355  IdleRegPool::make(user_path)
//   1356-1364  acquireX ×3  → buf(第1) / result(第2) / tmp(第3)
//   1365-1389  RegProtectGuard(a, excluding_x0(pool.getUsed()))
//   1391-1393  StackBufGuard(a, 15, buf)      // "/system/bin/su" 含 NUL 共 15 字节
//   1394-1399  newLabel → label_end
//   1401       mov result, xzr
//   1403-1408  { RegProtectGuard(a, x0); string_ops::kmemset(a, buf, 0, 15); }
//   1409-1434  { RegProtectGuard(a, x0);
//                emit_dynamic_copy_string_from_user(a, err, buf, user_path, 15);
//                mov tmp, x0; }
//   1436       cbz tmp, label_end            // 拷贝失败 → 直接返回 0
//   1444-1993  aarch64_asm_set_x_cstr_ptr(a, tmp, "/system/bin/su")
//   2007-2035  { RegProtectGuard(a, x0); string_ops::kstrcmp(a, tmp, buf); mov tmp, x0; }
//   2037       cbnz tmp, label_end           // 不等 → 返回 0
//   2038-2057  mov result, Imm(1)
//   2058       bind(label_end)
//   2059-2064  mov x0, result
//
// ABI/资源：StackBufGuard 在 RegProtectGuard 之后声明 ⇒ 先析构它再弹寄存器
void PatchBase::emit_check_user_filename_is_su(Assembler* a, KModErr& err, GpX user_path) {
    IdleRegPool pool = IdleRegPool::make(user_path);
    GpX buffer = pool.acquireX();                     // %23：15 字节栈缓冲首址
    GpX result = pool.acquireX();                     // %24：最终结果
    GpX tmp = pool.acquireX();                        // %25：x0 暂存
    RegProtectGuard guard(a, excluding_x0(pool.getUsed()));
    StackBufGuard stack_buffer(a, 15, buffer);

    Label label_end = a->newLabel();
    a->mov(result, xzr);

    {
        RegProtectGuard call_guard(a, x0);
        kernel_module::string_ops::kmemset(a, buffer, 0, 15);
    }
    {
        RegProtectGuard call_guard(a, x0);
        emit_dynamic_copy_string_from_user(a, err, buffer, user_path, 15);
        a->mov(tmp, x0);
    }
    a->cbz(tmp, label_end);

    aarch64_asm_set_x_cstr_ptr(a, tmp, "/system/bin/su");

    {
        RegProtectGuard call_guard(a, x0);
        kernel_module::string_ops::kstrcmp(a, tmp, buffer);
        a->mov(tmp, x0);
    }
    a->cbnz(tmp, label_end);
    a->mov(result, Imm(1));
    a->bind(label_end);
    a->mov(x0, result);
}

}  // namespace default_modules::module_su_redirect
