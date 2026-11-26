// patch current implementation.
//
#include "kernel_module_kit_umbrella.h"
#include "maplesu_caps_private.h"
#include "maplesu_request_private.h"
#include <atomic>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <limits>
#include <sched.h>

namespace kernel_module {
KModErr request_kernel_set_suid_sgid(uint32_t suid, uint32_t sgid);
}

class CpuPinGuardAuto {
public:
    explicit CpuPinGuardAuto(bool verbose) : pinned_(false), cpu_(-1), verbose_(verbose) {
        CPU_ZERO(&saved_);
        if (sched_getaffinity(0, sizeof(saved_), &saved_) != 0) {
            if (verbose_) std::fprintf(stderr, "CpuPinGuardAuto: sched_getaffinity failed: %s\n", std::strerror(errno));
            return;
        }
        const int cpu = sched_getcpu();
        if (cpu < 0) {
            if (verbose_) std::fprintf(stderr, "CpuPinGuardAuto: sched_getcpu failed: %s\n", std::strerror(errno));
            return;
        }
        cpu_ = cpu;
        cpu_set_t one;
        CPU_ZERO(&one);
        CPU_SET(cpu, &one);
        if (sched_setaffinity(0, sizeof(one), &one) != 0) {
            if (verbose_) std::fprintf(stderr, "CpuPinGuardAuto: sched_setaffinity(cpu %d) failed: %s\n", cpu, std::strerror(errno));
            return;
        }
        std::atomic_thread_fence(std::memory_order_seq_cst);
        pinned_ = true;
        if (verbose_) std::printf("CpuPinGuardAuto: pinned to CPU %d\n", cpu_);
    }
    ~CpuPinGuardAuto() {
        if (!pinned_) return;
        if (sched_setaffinity(0, sizeof(saved_), &saved_) != 0 && verbose_)
            std::fprintf(stderr, "CpuPinGuardAuto: restore sched_setaffinity failed: %s\n", std::strerror(errno));
        std::atomic_thread_fence(std::memory_order_seq_cst);
        if (verbose_) std::printf("CpuPinGuardAuto: restored from CPU %d\n", cpu_);
    }
private:
    cpu_set_t saved_;
    bool pinned_;
    int cpu_;
    bool verbose_;
};
static_assert(sizeof(cpu_set_t) == 128);
static_assert(sizeof(CpuPinGuardAuto) == 144 && alignof(CpuPinGuardAuto) == 8);
static_assert(sizeof(kernel_module::caps_info) == 40);

namespace default_modules {
namespace module_root {
// loads: four u32 fields, with field 0 used as the cred pointer offset.
class PatchBase {
public:
    PatchBase(const PatchBase&);
    ~PatchBase();
    int get_cred_atomic_usage_size();
private:
    uint32_t m_cred_offset;
    uint32_t m_layout_fields[3];
    friend class PatchCurrent;
};
static_assert(sizeof(PatchBase) == 16 && alignof(PatchBase) == 4);
class PatchCurrent : public PatchBase {
public:
    explicit PatchCurrent(const PatchBase& base);
    ~PatchCurrent();
    int get_need_write_cap_cnt();
    KModErr set_current_root(uint32_t task_field_offset);
    KModErr set_current_caps(const kernel_module::caps_info& caps);
};
static_assert(sizeof(PatchCurrent) == 16 && alignof(PatchCurrent) == 4);
PatchCurrent::PatchCurrent(const PatchBase& base) : PatchBase(base) {}
PatchCurrent::~PatchCurrent() = default;
int PatchCurrent::get_need_write_cap_cnt() { return kernel_module::get_caps_cnt(); }

// intentionally has one attempt, without a retry branch.
KModErr PatchCurrent::set_current_root(uint32_t task_field_offset) {
    bool managed = false;
    uint64_t capabilities = 0;
    const auto channel_error = kernel_module::maplesu_request_credential_channel(managed, capabilities);
    if (is_failed(channel_error)) return channel_error;
    if (managed) {
        const uint64_t required = MAPLESU_CAP_CREDENTIAL | MAPLESU_CAP_TASK_STATE;
        if ((capabilities & required) != required) {
            errno = EOPNOTSUPP;
            return KModErr::ERR_MODULE_TEST_CHANNEL;
        }
        const auto error = kernel_module::maplesu_request_owner_root();
        return is_failed(error) ? error : kernel_module::maplesu_request_owner_task_state();
    }
    using namespace asmjit;
    using namespace asmjit::a64;
    CpuPinGuardAuto pin(false);
    const int usage_size = get_cred_atomic_usage_size();
    aarch64_asm_ctx ctx = init_aarch64_asm();
    Assembler* a = ctx.assembler();
    kernel_module::arm64_module_asm_func_start(a);
    RETURN_IF_ERROR(kernel_module::export_symbol::get_current_checked(a, x12));
    RETURN_IF_ERROR(kernel_module::export_symbol::get_current_thread_info_checked(a, x13));
    a->ldr(x14, ptr(x12, m_cred_offset));
    a->add(x14, x14, Imm(usage_size));
    a->str(xzr, ptr(x14).post(8));
    a->str(xzr, ptr(x14).post(8));
    a->str(xzr, ptr(x14).post(8));
    a->str(xzr, ptr(x14).post(8));
    a->str(wzr, ptr(x14));
    a->mov(x15, Imm(2048));
    a->ldaxr(x14, ptr(x13));
    a->bic(x14, x14, x15);
    a->stlxr(x15, x14, ptr(x13));
    a->str(wzr, ptr(x12, task_field_offset));
    kernel_module::arm64_module_asm_func_end(a);
    const auto bytes = aarch64_asm_to_bytes(a);
    uint64_t result = 0;
    if (bytes.size() > std::numeric_limits<uint32_t>::max()) return KModErr::ERR_MODULE_ASM;
    KModErr err = kernel_module::maplesu_execute_legacy_credential_frame(
        bytes.data(), static_cast<uint32_t>(bytes.size()), result);
    if (is_failed(err)) return err;
    err = kernel_module::request_kernel_set_suid_sgid(0, 0);
    if (is_failed(err)) return err;
    const uint64_t maximum = kernel_module::get_cap_ability_max();
    if (maximum == 0) return KModErr::ERR_MODULE_CAP_ABILITY;
    kernel_module::caps_info caps;
    caps.inheritable = caps.permitted = caps.effective = caps.bounding = caps.ambient = maximum;
    return set_current_caps(caps);
}

// Ambient presence comes from one complete status snapshot; read errors propagate.
KModErr PatchCurrent::set_current_caps(const kernel_module::caps_info& caps) {
    bool managed = false;
    uint64_t capabilities = 0;
    const auto channel_error = kernel_module::maplesu_request_credential_channel(managed, capabilities);
    if (is_failed(channel_error)) return channel_error;
    if (managed) {
        if (!(capabilities & MAPLESU_CAP_CREDENTIAL)) {
            errno = EOPNOTSUPP;
            return KModErr::ERR_MODULE_TEST_CHANNEL;
        }
        const maplesu_caps_v3 value{caps.inheritable,caps.permitted,caps.effective,caps.bounding,caps.ambient};
        return kernel_module::maplesu_request_owner_caps(value);
    }
    using namespace asmjit;
    using namespace asmjit::a64;
    const uint32_t offset = m_layout_fields[2];
    kernel_module::caps_info current_caps{};
    bool ambient_present = false;
    const KModErr snapshot_error = kernel_module::get_current_caps_snapshot(current_caps, ambient_present);
    if (is_failed(snapshot_error)) return snapshot_error;
    aarch64_asm_ctx ctx = init_aarch64_asm();
    Assembler* a = ctx.assembler();
    kernel_module::arm64_module_asm_func_start(a);
    RETURN_IF_ERROR(kernel_module::export_symbol::get_current_checked(a, x12));
    a->ldr(x13, ptr(x12, m_cred_offset));
    aarch64_asm_mov_x(a, x14, offset);
    a->add(x13, x13, x14);
    aarch64_asm_mov_x(a, x14, caps.inheritable);
    a->str(x14, ptr(x13));
    aarch64_asm_mov_x(a, x14, caps.permitted);
    a->str(x14, ptr(x13, 8));
    aarch64_asm_mov_x(a, x14, caps.effective);
    a->str(x14, ptr(x13, 16));
    aarch64_asm_mov_x(a, x14, caps.bounding);
    a->str(x14, ptr(x13, 24));
    if (ambient_present) {
        aarch64_asm_mov_x(a, x14, caps.ambient);
        a->str(x14, ptr(x13, 32));
    }
    kernel_module::arm64_module_asm_func_end(a);
    const auto bytes = aarch64_asm_to_bytes(a);
    uint64_t result = 0;
    if (bytes.size() > std::numeric_limits<uint32_t>::max()) return KModErr::ERR_MODULE_ASM;
    return kernel_module::maplesu_execute_legacy_credential_frame(
        bytes.data(), static_cast<uint32_t>(bytes.size()), result);
}
} // namespace module_root
} // namespace default_modules
