// task comm scan implementation.
//
#include <sys/prctl.h>

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <limits>
#include <random>
#include <vector>

#include "kernel_module_kit_umbrella.h"

#include "task_comm_scan.h"

namespace random_utils {

namespace detail {

// One thread-local generator seeded from the platform entropy source on first
// use. The marker generator below needs a shuffle source only.
static std::mt19937& mt19937_32() {
    static thread_local std::mt19937 gen(std::random_device{}());
    return gen;
}

}  // namespace detail

// Return `count` distinct non-zero bytes in random order. The candidate space
// is the 255 non-zero byte values; a request for more bytes than that cannot
// be satisfied and is treated as empty.
static std::vector<uint8_t> generate_unique_non_zero_bytes(std::size_t count) {
    if (count == 0) {
        return {};
    }
    std::vector<uint8_t> bytes(0xFF);
    uint8_t value = 1;
    for (auto& byte : bytes) {
        byte = value++;
    }
    std::shuffle(bytes.begin(), bytes.end(), detail::mt19937_32());
    return std::vector<uint8_t>(bytes.begin(), bytes.begin() + static_cast<std::ptrdiff_t>(count));
}

}  // namespace random_utils

namespace {

// Restores the caller-visible process name when the scope is left, unless the
// owner already restored it explicitly.
class RestoreName {
    const char* name_;
    bool active_ = true;

public:
    explicit RestoreName(const char* name) : name_(name) {}

    ~RestoreName() {
        if (active_) {
            prctl(PR_SET_NAME, name_, 0, 0, 0);
        }
    }

    KModErr restore() {
        if (prctl(PR_SET_NAME, name_, 0, 0, 0) != 0) {
            return KModErr::ERR_MODULE_PR_SET_NAME;
        }
        active_ = false;
        return KModErr::OK;
    }
};

}  // namespace

namespace kernel_module {

// Locate `offsetof(struct task_struct, comm)` on the running kernel without
// any prior layout knowledge:
//   1. install a unique non-zero marker as the process name and read it back,
//      so the kernel has definitely copied it into `comm`;
//   2. resolve the calling thread's task_struct through the SDK current-task
//      emitter;
//   3. scan the first 8 KiB of that task_struct for the marker;
//   4. restore the saved name and cache the discovered offset.
KModErr get_task_struct_comm_offset(uint32_t& offset) {
    static std::atomic<uint32_t> cached{0};
    if (const auto known = cached.load(std::memory_order_relaxed)) {
        offset = known;
        return KModErr::OK;
    }

    char original[16]{};
    if (prctl(PR_GET_NAME, original, 0, 0, 0) != 0) {
        return KModErr::ERR_MODULE_PR_GET_NAME;
    }

    auto random = random_utils::generate_unique_non_zero_bytes(15);
    random.push_back(0);

    RestoreName restore(original);
    if (prctl(PR_SET_NAME, random.data(), 0, 0, 0) != 0) {
        return KModErr::ERR_MODULE_PR_SET_NAME;
    }

    uint8_t name[16]{};
    if (prctl(PR_GET_NAME, name, 0, 0, 0) != 0 ||
        std::memcmp(name, random.data(), sizeof(name)) != 0) {
        return KModErr::ERR_MODULE_PR_SET_NAME;
    }

    // Resolve the current task_struct with the SDK current-task emitter so all
    // supported THREAD_INFO layouts are handled.
    auto context = init_aarch64_asm();
    auto a = context.assembler();
    arm64_module_asm_func_start(a);
    RETURN_IF_ERROR(export_symbol::get_current_checked(a, asmjit::a64::x0));
    arm64_module_asm_func_end(a, asmjit::a64::x0);
    const auto bytes = aarch64_asm_to_bytes(a);
    if (context.has_error() || bytes.empty()) {
        return KModErr::ERR_MODULE_ASM;
    }

    uint64_t task = 0;
    auto error = execute_kernel_asm_func(bytes, task);
    if (is_failed(error)) {
        return error;
    }
    if (task < 0xffff000000000000ULL ||
        task > std::numeric_limits<uint64_t>::max() - 8191) {
        return KModErr::ERR_MODULE_NOT_KADDR;
    }

    error = maplesu_task_comm::scan(
        task, name, offset,
        [](uint64_t address, void* output, uint32_t size) {
            return read_kernel_mem(address, output, size);
        });

    const auto restored = restore.restore();
    if (is_failed(restored)) {
        return restored;
    }
    if (is_failed(error)) {
        return error;
    }
    cached.store(offset, std::memory_order_relaxed);
    return KModErr::OK;
}

}  // namespace kernel_module
