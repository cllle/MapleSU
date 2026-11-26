// task struct implementation.
//
#include <signal.h>
#include <sys/prctl.h>
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <unistd.h>

#include <atomic>
#include <array>
#include <cerrno>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <functional>
#include <set>
#include <string>
#include <thread>
#include <vector>

#include "kernel_module_kit_umbrella.h"

#include "offsets_internal.h"
#include "maplesu_kernel_layout_private.h"
#include "maplesu_request_private.h"

// Runs one asmjit-emitted AArch64 routine inside the kernel and returns its X0
// result through `result`. The emitter receives the register that
// arm64_module_asm_func_end() will move the return value into and must publish
// its result register through `out_regs`.
//
// Failure semantics: if the emitter reports an error, no
// arm64_module_asm_func_end() is emitted (the prologue would be left
// unbalanced) and the error is propagated unchanged.
static KModErr get_kernel_shellcode_u64_result(
        std::function<KModErr(asmjit::a64::Assembler*, asmjit::a64::GpX&)> emitter,
        uint64_t& result) {
    aarch64_asm_ctx asm_ctx = init_aarch64_asm();
    asmjit::a64::Assembler* a = asm_ctx.assembler();
    kernel_module::arm64_module_asm_func_start(a);

    asmjit::a64::GpX x0 = asmjit::a64::x0;
    KModErr err = emitter(a, x0);
    if (is_failed(err)) {
        return err;
    }
    kernel_module::arm64_module_asm_func_end(a, x0);

    std::vector<uint8_t> func_bytes = aarch64_asm_to_bytes(a);
    return kernel_module::execute_kernel_asm_func(func_bytes, result);
}

namespace {

// Resolve the calling thread's task_struct address inside the kernel. The
// emitter used by the layout probes is the SDK current-task emitter, which
// keeps every supported THREAD_INFO variant.
KModErr shellcode_current_task(uint64_t& task) {
    return get_kernel_shellcode_u64_result(
            [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
                RETURN_IF_ERROR(kernel_module::export_symbol::get_current_checked(a, asmjit::a64::x10));
                out = asmjit::a64::x10;
                return KModErr::OK;
            },
            task);
}

// SP_EL0 as an AArch64 system-register immediate: SysReg::encode(3,0,4,1,0)
// == 49672 == 0xC208.
constexpr uint32_t kSpEl0SysReg = 49672;

// Candidate mask sizes for the inherited raw SP_EL0/SP classification heuristic.
// Matching a masked base is not independent proof of the target structure.
constexpr uint64_t kThreadSizes[] = {0x4000, 0x8000, 0x10000, 0x20000};

// Inherited upper-range floor; callers also exclude the error-pointer tail.
// This envelope does not validate canonical addresses for the target VA width.
constexpr uint64_t kKernelAddressFloor = 0xFFF0000000000000ULL;

}  // namespace

namespace kernel_module {

static KModErr resolve_task_btf_offset(const char* member, uint32_t width,
                                      uint32_t& offset, bool& available) {
    KernelFieldDescription field;
    KModErr error = maplesu_resolve_btf_field("task_struct", member, width, field, available);
    if (available && is_ok(error)) offset = field.offset_bytes;
    return error;
}

// get_task_struct_state_offset()
//
// Locate `offsetof(struct task_struct, __state)` by watching one helper thread
// change state inside the kernel:
//   1. the helper reports its task_struct address, then loops on a shared
//      flag (TASK_RUNNING while spinning);
//   2. the caller snapshots the first 128 bytes of that task_struct while the
//      helper spins, then releases it;
//   3. the helper blocks in a pipe read (TASK_INTERRUPTIBLE); the caller takes
//      a second snapshot, then wakes it with one byte;
//   4. after the helper returns to the spin loop the caller takes the third
//      snapshot, joins the helper and closes the pipe;
//   5. the state word is the only word that reads zero in the first and third
//      snapshot and a small non-zero value in the middle one.
KModErr get_task_struct_state_offset(uint32_t& offset) {
    uint32_t cached = 0;
    KModErr err = get_offset_cache("task_struct_state", cached);
    if (is_ok(err)) {
        offset = cached;
        return err;
    }

    std::array<uint32_t, 32> running_before{};
    std::array<uint32_t, 32> blocked{};
    std::array<uint32_t, 32> running_after{};

    struct SharedState {
        std::atomic<uint64_t> task;
        std::atomic<uint32_t> flag;
        std::atomic<bool> stop;
        std::atomic<KModErr> result{KModErr::OK};
        int read_fd = -1;
        int write_fd = -1;
    } shared{};

    struct ProbeGuard {
        SharedState& shared;
        std::thread worker;

        ~ProbeGuard() {
            shared.stop.store(true, std::memory_order_release);
            shared.flag.store(0, std::memory_order_release);
            if (shared.write_fd >= 0) ::close(shared.write_fd);
            if (worker.joinable()) worker.join();
            if (shared.read_fd >= 0) ::close(shared.read_fd);
        }
    } guard{shared};

    int fds[2];
    if (::pipe(fds) != 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }
    shared.read_fd = fds[0];
    shared.write_fd = fds[1];

    guard.worker = std::thread([&shared]() {
        uint64_t task = 0;
        KModErr result = shellcode_current_task(task);
        if (is_ok(result) && task == 0) result = KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
        shared.result.store(result, std::memory_order_release);
        shared.task.store(task, std::memory_order_release);
        shared.flag.store(1, std::memory_order_release);
        if (is_failed(result)) return;
        while (shared.flag.load(std::memory_order_acquire) == 1 &&
               !shared.stop.load(std::memory_order_acquire)) {
            std::this_thread::yield();
        }
        if (shared.stop.load(std::memory_order_acquire)) return;
        shared.flag.store(2, std::memory_order_release);
        char value = 0;
        ssize_t count;
        do {
            count = ::read(shared.read_fd, &value, 1);
        } while (count < 0 && errno == EINTR);
        if (count != 1) shared.result.store(KModErr::ERR_MODULE_READ_FILE, std::memory_order_release);
        shared.flag.store(3, std::memory_order_release);
        while (shared.flag.load(std::memory_order_acquire) == 3 &&
               !shared.stop.load(std::memory_order_acquire)) {
            std::this_thread::yield();
        }
    });

    while (shared.flag.load(std::memory_order_acquire) != 1) {
        ::usleep(10000);
    }
    err = shared.result.load(std::memory_order_acquire);
    if (is_failed(err)) return err;

    err = read_kernel_mem(shared.task.load(std::memory_order_acquire),
                          running_before.data(),
                          static_cast<uint32_t>(running_before.size() * sizeof(uint32_t)));
    if (is_failed(err)) {
        return err;
    }

    shared.flag.store(0, std::memory_order_release);
    while (shared.flag.load(std::memory_order_acquire) < 2) {
        ::usleep(1000);
    }
    ::usleep(50000);
    err = shared.result.load(std::memory_order_acquire);
    if (is_failed(err)) return err;

    err = read_kernel_mem(shared.task.load(std::memory_order_acquire),
                          blocked.data(),
                          static_cast<uint32_t>(blocked.size() * sizeof(uint32_t)));
    if (is_failed(err)) {
        return err;
    }

    const char wake = 'X';
    ssize_t count;
    do {
        count = ::write(shared.write_fd, &wake, 1);
    } while (count < 0 && errno == EINTR);
    if (count != 1) return KModErr::ERR_MODULE_WRITE_FILE;

    while (shared.flag.load(std::memory_order_acquire) != 3) {
        ::usleep(1000);
    }
    err = shared.result.load(std::memory_order_acquire);
    if (is_failed(err)) return err;

    err = read_kernel_mem(shared.task.load(std::memory_order_acquire),
                          running_after.data(),
                          static_cast<uint32_t>(running_after.size() * sizeof(uint32_t)));
    if (is_failed(err)) {
        return err;
    }

    uint32_t matched = 0;
    uint32_t candidate = 0;
    for (uint32_t index = 0; index < running_before.size(); ++index) {
        const bool runs_zero =
                (running_before[index] | running_after[index]) == 0;
        if (runs_zero && blocked[index] != 0 && blocked[index] < 0x100) {
            ++matched;
            candidate = index * static_cast<uint32_t>(sizeof(uint32_t));
        }
    }
    if (matched != 1) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }

    err = put_offset_cache("task_struct_state", candidate);
    if (is_ok(err)) offset = candidate;
    return err;
}

// get_task_struct_stack_offset()
//
// Locate `offsetof(struct task_struct, stack)` by reading the kernel's own
// `init_task` image and comparing its words against the symbols the kernel
// publishes for its boot stack:
//   * kernels older than 4.7 keep the thread_info (and therefore the stack
//     base) address in the init_thread_info symbol;
//   * newer kernels publish the stack base as init_stack.
// Only the first `comm_offset` bytes are needed, because `comm` is the last
// anchor field this SDK relies on.
KModErr get_task_struct_stack_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_task_btf_offset("stack", 8, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("task_struct_stack", offset);
    if (is_ok(err)) {
        return err;
    }

    uint32_t comm_offset = 0;
    err = get_task_struct_comm_offset(comm_offset);
    if (is_failed(err)) {
        return err;
    }

    std::vector<uint8_t> task_image(comm_offset);

    uint64_t init_task = 0;
    err = kallsyms_lookup_name("init_task", init_task);
    if (is_failed(err)) {
        return err;
    }

    uint64_t init_thread_info = 0;
    uint64_t init_stack = 0;
    if (is_kernel_version_less("4.7.0")) {
        err = kallsyms_lookup_name("init_thread_info", init_thread_info);
    } else {
        err = kallsyms_lookup_name("init_stack", init_stack);
    }
    if (is_failed(err)) {
        return err;
    }

    err = read_kernel_mem(init_task, task_image.data(),
                          static_cast<uint32_t>(task_image.size()));
    if (is_failed(err)) {
        return err;
    }

    uint32_t candidate = 0;
    bool found = false;
    for (uint64_t index = 0; index + sizeof(uint64_t) <= task_image.size(); index += 4) {
        uint64_t word = 0;
        std::memcpy(&word, task_image.data() + index, sizeof(word));
        if ((init_thread_info != 0 && word == init_thread_info) ||
            (init_stack != 0 && word == init_stack)) {
            candidate = static_cast<uint32_t>(index);
            found = true;
            break;
        }
    }
    if (!found) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    err = put_offset_cache("task_struct_stack", candidate);
    if (is_ok(err)) offset = candidate;
    return err;
}

// Use live boot-stack marker spans, not the next-symbol gap or readable pages.
// Pinned generic v5.10/v6.18 linkers set end=start+THREAD_SIZE and both aliases
// to start. Same-base aliases alone do not prove an arbitrary vendor end marker
// excludes extra objects/padding; that target span qualification remains separate.
// Address checks cover the upper-range/error-pointer envelope, not target VA width.
// No current-task or stack-offset bootstrap.
__attribute__((visibility("hidden"))) KModErr maplesu_lookup_optional_symbol(const char*, uint64_t&);
static KModErr resolve_current_stack_size(uint32_t& size) {
    constexpr const char* markers[][2] = {
        {"__start_init_stack", "__end_init_stack"},
        {"__start_init_task", "__end_init_task"},
    };
    for (const auto& names : markers) {
        uint64_t start = 0, end = 0, stack = 0, thread_union = 0;
        KModErr error = maplesu_lookup_optional_symbol(names[0], start);
        if (error == KModErr::ERR_MODULE_SYMBOL_NOT_EXIST) continue;
        RETURN_IF_ERROR(error);
        RETURN_IF_ERROR(maplesu_lookup_optional_symbol(names[1], end));
        RETURN_IF_ERROR(kallsyms_lookup_name("init_stack", stack));
        RETURN_IF_ERROR(kallsyms_lookup_name("init_thread_union", thread_union));
        if (start < kKernelAddressFloor || end >= static_cast<uint64_t>(-4095LL) || end <= start)
            return KModErr::ERR_MODULE_NOT_KADDR;
        const uint64_t span = end - start;
        if (stack != start || thread_union != start || span < 0x4000 || span > UINT32_MAX ||
            (span & (span - 1)) || (start & (span - 1)))
            return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
        size = static_cast<uint32_t>(span);
        return KModErr::OK;
    }
    return KModErr::ERR_MODULE_SYMBOL_NOT_EXIST;
}

KModErr get_task_struct_stack_size(uint32_t& size) {
    // Old probe-cache values were mapping boundaries, not THREAD_SIZE evidence.
    // Reuse marker validation; arbitrary vendor span qualification stays separate.
    return resolve_current_stack_size(size);
}

// get_task_struct_ptrace_offset()
//
// Locate `offsetof(struct task_struct, ptrace)` by driving a freshly forked
// child through four observable ptrace states and diffing the first 256 bytes
// of its task_struct after each step:
//   1. child blocked in a pipe read, not traced;
//   2. after PTRACE_ATTACH and waitpid: stopped and traced;
//   3. after PTRACE_SETOPTIONS: options added to the ptrace flags word;
//   4. after PTRACE_DETACH: not traced again.
// The ptrace word is the only word that carries the low flag bit in both the
// traced snapshots while reading zero in both untraced ones.
KModErr get_task_struct_ptrace_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_task_btf_offset("ptrace", 4, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("task_struct_ptrace", offset);
    if (is_ok(err)) {
        return err;
    }

    struct ProbeOwner {
        int task_pipe[2] = {-1, -1};
        int wake_pipe[2] = {-1, -1};
        pid_t child = -1;

        void close_fd(int& fd) {
            if (fd >= 0) {
                ::close(fd);
                fd = -1;
            }
        }

        pid_t wait_child(int& status, int options) {
            pid_t result;
            do {
                result = ::waitpid(child, &status, options);
            } while (result < 0 && errno == EINTR);
            if ((result == child && (WIFEXITED(status) || WIFSIGNALED(status))) ||
                (result < 0 && errno == ECHILD)) child = -1;
            return result;
        }

        ~ProbeOwner() {
            close_fd(task_pipe[0]);
            close_fd(task_pipe[1]);
            close_fd(wake_pipe[0]);
            close_fd(wake_pipe[1]);
            if (child <= 0) return;
            int status = 0;
            if (wait_child(status, WNOHANG) < 0 || child <= 0) return;
            if (::kill(child, SIGKILL) != 0 && errno != ESRCH) return;
            while (child > 0) {
                if (wait_child(status, 0) < 0) break;
            }
        }
    } owner;
    if (pipe(owner.task_pipe) != 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }
    if (pipe(owner.wake_pipe) != 0) {
        return KModErr::ERR_MODULE_OPEN_FILE;
    }

    struct TaskReceipt {
        KModErr error = KModErr::OK;
        uint64_t task = 0;
    } receipt;
    const pid_t child = fork();
    owner.child = child;
    if (child < 0) {
        return KModErr::ERR_READ_CHILD_DATA;
    }
    if (child == 0) {
        owner.close_fd(owner.task_pipe[0]);
        owner.close_fd(owner.wake_pipe[1]);
        receipt.error = shellcode_current_task(receipt.task);
        size_t sent = 0;
        while (sent < sizeof(receipt)) {
            const ssize_t count = ::write(owner.task_pipe[1],
                reinterpret_cast<const char*>(&receipt) + sent, sizeof(receipt) - sent);
            if (count < 0 && errno == EINTR) continue;
            if (count <= 0) ::_exit(1);
            sent += static_cast<size_t>(count);
        }
        owner.close_fd(owner.task_pipe[1]);
        if (is_failed(receipt.error)) ::_exit(1);
        char value = 0;
        ssize_t count;
        do {
            count = ::read(owner.wake_pipe[0], &value, 1);
        } while (count < 0 && errno == EINTR);
        ::_exit(count < 0 ? 1 : 0);
    }

    owner.close_fd(owner.task_pipe[1]);
    owner.close_fd(owner.wake_pipe[0]);

    size_t received = 0;
    while (received < sizeof(receipt)) {
        const ssize_t count = ::read(owner.task_pipe[0],
            reinterpret_cast<char*>(&receipt) + received, sizeof(receipt) - received);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return KModErr::ERR_READ_CHILD_DATA;
        received += static_cast<size_t>(count);
    }
    owner.close_fd(owner.task_pipe[0]);
    if (is_failed(receipt.error)) return receipt.error;
    const uint64_t child_task = receipt.task;
    if (child_task < kKernelAddressFloor || child_task >= static_cast<uint64_t>(-4095LL)) {
        return KModErr::ERR_MODULE_NOT_KADDR;
    }

    std::vector<uint32_t> before(64);
    std::vector<uint32_t> stopped(64);
    std::vector<uint32_t> with_options(64);
    std::vector<uint32_t> after(64);

    err = read_kernel_mem(child_task, before.data(),
                          static_cast<uint32_t>(before.size() * sizeof(uint32_t)));
    if (is_failed(err)) {
        return err;
    }

    if (ptrace(PTRACE_ATTACH, child, nullptr, nullptr) == -1) {
        return KModErr::ERR_MODULE_PTRACE_ATTACH;
    }
    int status = 0;
    if (owner.wait_child(status, 0) != child || !WIFSTOPPED(status)) {
        return KModErr::ERR_MODULE_PTRACE_ATTACH;
    }

    err = read_kernel_mem(child_task, stopped.data(),
                          static_cast<uint32_t>(stopped.size() * sizeof(uint32_t)));
    if (is_failed(err)) {
        return err;
    }

    constexpr uint32_t kPtraceTraced = 1u;
    constexpr uint32_t kPtraceOptions = PTRACE_O_TRACEFORK | PTRACE_O_TRACEVFORK;
    // Linux task->ptrace stores PTRACE_O_* above PT_OPT_FLAG_SHIFT.
    constexpr uint32_t kPtraceOptionFlagShift = 3u;
    constexpr uint32_t kPtraceWithOptions =
            kPtraceTraced | (kPtraceOptions << kPtraceOptionFlagShift);
    if (ptrace(PTRACE_SETOPTIONS, child, nullptr,
               reinterpret_cast<void*>(static_cast<uintptr_t>(kPtraceOptions))) == -1) {
        return KModErr::ERR_MODULE_PTRACE_ATTACH;
    }

    err = read_kernel_mem(child_task, with_options.data(),
                          static_cast<uint32_t>(with_options.size() * sizeof(uint32_t)));
    if (is_failed(err)) {
        return err;
    }

    if (ptrace(PTRACE_DETACH, child, nullptr, nullptr) == -1) {
        return KModErr::ERR_MODULE_PTRACE_ATTACH;
    }

    err = read_kernel_mem(child_task, after.data(),
                          static_cast<uint32_t>(after.size() * sizeof(uint32_t)));
    if (is_failed(err)) {
        return err;
    }

    owner.close_fd(owner.wake_pipe[1]);
    if (owner.wait_child(status, 0) != child || !WIFEXITED(status) || WEXITSTATUS(status) != 0) {
        return KModErr::ERR_READ_CHILD_DATA;
    }

    uint32_t matched = 0;
    uint32_t candidate = 0;
    for (uint32_t index = 0; index < before.size(); ++index) {
        const bool expected_states =
                before[index] == 0 && stopped[index] == kPtraceTraced &&
                with_options[index] == kPtraceWithOptions && after[index] == 0;
        if (expected_states) {
            ++matched;
            candidate = index * static_cast<uint32_t>(sizeof(uint32_t));
        }
    }
    if (matched != 1) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }

    offset = candidate;
    return put_offset_cache("task_struct_ptrace", offset);
}

// get_task_struct_mm_offset()
//
// Locate `offsetof(struct task_struct, mm)`:
//   1. inside the kernel, resolve the calling task and take one reference to
//      its mm through get_task_mm(), then drop the reference with mmput();
//      the same emitted routine keeps the mm address in its result register;
//   2. resolve the calling task_struct address on the host;
//   3. read the first `comm_offset` bytes of the task_struct (mm precedes
//      comm) and find the 8-byte word equal to the mm address.
KModErr get_task_struct_mm_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_task_btf_offset("mm", 8, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("task_struct_mm", offset);
    if (is_ok(err)) {
        return err;
    }

    uint64_t mm = 0;
    err = get_kernel_shellcode_u64_result(
            [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
                KModErr call_err = KModErr::ERR_MODULE_PARAM;
                RETURN_IF_ERROR(kernel_module::export_symbol::get_current_checked(a, asmjit::a64::x10));
                kernel_module::export_symbol::get_task_mm(
                        a, call_err, asmjit::a64::x10);
                if (is_failed(call_err)) {
                    return call_err;
                }
                kernel_module::export_symbol::mmput(a, call_err, asmjit::a64::x0);
                if (is_failed(call_err)) {
                    return call_err;
                }
                out = asmjit::a64::x0;
                return call_err;
            },
            mm);
    if (is_failed(err)) {
        return err;
    }

    if (mm < kKernelAddressFloor || mm >= static_cast<uint64_t>(-4095LL)) {
        return KModErr::ERR_MODULE_NOT_KADDR;
    }

    uint64_t task = 0;
    err = shellcode_current_task(task);
    if (is_failed(err)) {
        return err;
    }

    uint32_t comm_offset = 0;
    err = get_task_struct_comm_offset(comm_offset);
    if (is_failed(err)) {
        return err;
    }

    std::vector<char> task_image(comm_offset);
    err = read_kernel_mem(task, task_image.data(),
                          static_cast<uint32_t>(task_image.size()));
    if (is_failed(err)) {
        return err;
    }

    uint32_t candidate = 0;
    bool found = false;
    for (uint64_t index = 0; index + sizeof(uint64_t) <= task_image.size(); index += 4) {
        uint64_t word = 0;
        std::memcpy(&word, task_image.data() + index, sizeof(word));
        if (word == mm) {
            candidate = static_cast<uint32_t>(index);
            found = true;
            break;
        }
    }
    if (!found) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    err = put_offset_cache("task_struct_mm", candidate);
    if (is_ok(err)) offset = candidate;
    return err;
}

// get_task_struct_pid_offset()
//
// Locate `offsetof(struct task_struct, pid)` by matching the calling thread's
// packed (tid, tgid) pair in the first 8 KiB of the calling task_struct.
KModErr get_task_struct_pid_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_task_btf_offset("pid", 4, offset, available);
    if (available) return btf_error;

    KModErr cache_error = get_offset_cache("task_struct_pid", offset);
    if (is_ok(cache_error)) return cache_error;

    const uint32_t tgid = static_cast<uint32_t>(::getpid());
    const uint32_t tid = static_cast<uint32_t>(::gettid());
    const uint64_t expected = static_cast<uint64_t>(tid) |
                              (static_cast<uint64_t>(tgid) << 32);

    uint64_t task = 0;
    KModErr err = shellcode_current_task(task);
    if (is_failed(err)) {
        return err;
    }

    uint32_t candidate = 0;
    bool found = false;
    for (uint64_t index = 0; index + sizeof(uint64_t) <= 0x2000; index += 4) {
        uint64_t word = 0;
        err = read_kernel_mem(task + index, &word, sizeof(word));
        if (is_failed(err)) {
            return err;
        }
        if (word == expected) {
            candidate = static_cast<uint32_t>(index);
            found = true;
            break;
        }
    }
    if (!found) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }

    err = put_offset_cache("task_struct_pid", candidate);
    if (is_ok(err)) offset = candidate;
    return err;
}

// get_task_struct_tgid_offset()
//
// Resolve tgid independently from BTF; the old missing-BTF probe uses the pid pair.
KModErr get_task_struct_tgid_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_task_btf_offset("tgid", 4, offset, available);
    if (available) return btf_error;

    uint32_t pid_offset = 0;
    KModErr err = get_task_struct_pid_offset(pid_offset);
    if (is_failed(err)) {
        return err;
    }
    offset = pid_offset + 4;
    return KModErr::OK;
}

// get_task_struct_real_parent_offset()
//
// Locate the adjacent `real_parent` / `parent` pair. Both point at the same
// task for an untraced process, so the pair is the only 16-byte window near
// the pid/tgid anchor where two consecutive non-zero task pointers are equal.
KModErr get_task_struct_real_parent_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_task_btf_offset("real_parent", 8, offset, available);
    if (available) return btf_error;

    KModErr cache_error = get_offset_cache("task_struct_real_parent", offset);
    if (is_ok(cache_error)) return cache_error;

    uint32_t pid_offset = 0;
    KModErr err = get_task_struct_pid_offset(pid_offset);
    if (is_failed(err)) {
        return err;
    }

    uint64_t task = 0;
    err = shellcode_current_task(task);
    if (is_failed(err)) {
        return err;
    }

    uint32_t candidate = 0;
    bool found = false;
    for (uint64_t index = 0; index + 2 * sizeof(uint64_t) <= 0x100; index += 4) {
        uint64_t pair[2] = {0, 0};
        err = read_kernel_mem(task + pid_offset + 8 + index, pair, sizeof(pair));
        if (is_failed(err)) {
            return err;
        }
        if (pair[0] != 0 && pair[1] != 0 && pair[0] == pair[1]) {
            candidate = pid_offset + 8 + static_cast<uint32_t>(index);
            found = true;
            break;
        }
    }
    if (!found) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }

    err = put_offset_cache("task_struct_real_parent", candidate);
    if (is_ok(err)) offset = candidate;
    return err;
}

// get_task_struct_tasks_offset()
//
// Locate `offsetof(struct task_struct, tasks)`. Every task's `tasks` node sits
// at the same offset inside its task_struct and the list is circular, so
// scanning the boot `init_task` image for a word that points back into a task
// at exactly the same offset identifies that list offset:
//   1. walk the real_parent chain from the calling task up to the direct child
//      of init_task;
//   2. read init_task and find the word equal to (child + offset) at the same
//      offset.
KModErr get_task_struct_tasks_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_task_btf_offset("tasks", 0, offset, available);
    if (available) return btf_error;

    uint32_t real_parent_offset = 0;
    KModErr err = get_task_struct_real_parent_offset(real_parent_offset);
    if (is_failed(err)) {
        return err;
    }

    uint64_t task = 0;
    err = shellcode_current_task(task);
    if (is_failed(err)) {
        return err;
    }

    uint64_t init_task = 0;
    err = kallsyms_lookup_name("init_task", init_task);
    if (is_failed(err)) {
        return err;
    }

    uint64_t cursor = task;
    std::set<uint64_t> ancestors;
    for (;;) {
        if (cursor < kKernelAddressFloor || cursor >= static_cast<uint64_t>(-4095LL) ||
            real_parent_offset > UINT64_MAX - cursor ||
            !ancestors.insert(cursor).second) return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
        uint64_t parent = 0;
        err = read_kernel_mem(cursor + real_parent_offset, &parent, sizeof(parent));
        if (is_failed(err)) {
            return err;
        }
        if (parent == init_task) {
            break;
        }
        cursor = parent;
    }

    uint32_t candidate = 0;
    bool found = false;
    for (uint64_t index = 0; index + sizeof(uint64_t) <= 0x1000; index += 4) {
        uint64_t word = 0;
        err = read_kernel_mem(init_task + index, &word, sizeof(word));
        if (is_failed(err)) {
            return err;
        }
        if (word == cursor + index) {
            candidate = static_cast<uint32_t>(index);
            found = true;
            break;
        }
    }
    if (!found) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    offset = candidate;
    return KModErr::OK;
}

// get_task_struct_files_offset()
//
// Locate `offsetof(struct task_struct, files)`. The boot task's `files` field
// is the exported `init_files` object, and `files` follows `comm`, so scanning
// init_task starting at the known `comm` offset finds the pointer with a single
// 100-byte read.
KModErr get_task_struct_files_offset(uint32_t& offset) {
    bool available = false;
    KModErr btf_error = resolve_task_btf_offset("files", 8, offset, available);
    if (available) return btf_error;

    KModErr err = get_offset_cache("task_struct_files", offset);
    if (is_ok(err)) {
        return err;
    }

    uint32_t comm_offset = 0;
    err = get_task_struct_comm_offset(comm_offset);
    if (is_failed(err)) {
        return err;
    }

    uint64_t init_task = 0;
    err = kallsyms_lookup_name("init_task", init_task);
    if (is_failed(err)) {
        return err;
    }

    uint64_t init_files = 0;
    err = kallsyms_lookup_name("init_files", init_files);
    if (is_failed(err)) {
        return err;
    }

    uint8_t window[0x64] = {};
    err = read_kernel_mem(init_task + comm_offset, window, sizeof(window));
    if (is_failed(err)) {
        return err;
    }

    uint32_t candidate = 0;
    bool found = false;
    for (uint64_t index = 0; index + sizeof(uint64_t) <= sizeof(window); index += 4) {
        uint64_t word = 0;
        std::memcpy(&word, window + index, sizeof(word));
        if (word == init_files) {
            candidate = comm_offset + static_cast<uint32_t>(index);
            found = true;
            break;
        }
    }
    if (!found) {
        return KModErr::ERR_MODULE_OFFSET_NOT_FOUND;
    }
    err = put_offset_cache("task_struct_files", candidate);
    if (is_ok(err)) offset = candidate;
    return err;
}

// get_task_struct_real_cred_offset()
//
// Resolve the objective credential pointer independently from BTF or the
// target cap_capget data flow; task.cred is a separate field.
KModErr get_task_struct_real_cred_offset(uint32_t& offset) {
    KernelFieldDescription field;
    bool available = false;
    KModErr error = maplesu_resolve_btf_field("task_struct", "real_cred", 8, field, available);
    if (available) {
        if (is_ok(error)) offset = field.offset_bytes;
        return error;
    }
    error = maplesu_resolve_accessor_field("task_struct", "real_cred", 8, field);
    if (is_ok(error)) offset = field.offset_bytes;
    return error;
}

// get_task_struct_cred_offset()
//
// Resolve `cred` independently from BTF or the target ID getter data flow.
KModErr get_task_struct_cred_offset(uint32_t& offset) {
    KernelFieldDescription field;
    bool available = false;
    KModErr error = maplesu_resolve_btf_field("task_struct", "cred", 8, field, available);
    if (available) {
        if (is_ok(error)) offset = field.offset_bytes;
        return error;
    }
    error = maplesu_resolve_accessor_field("task_struct", "cred", 8, field);
    if (is_ok(error)) offset = field.offset_bytes;
    return error;
}

// get_task_struct_seccomp_offset()
//
// Resolve the effective 32-bit seccomp.mode field, independently of append metadata.
KModErr get_task_struct_seccomp_offset(uint32_t& offset) {
    KernelFieldDescription field;
    bool available = false;
    KModErr error = maplesu_resolve_btf_field("task_struct", "seccomp.mode", 4, field, available);
    if (available) {
        if (is_ok(error)) offset = field.offset_bytes;
        return error;
    }
    error = maplesu_resolve_accessor_field("task_struct", "seccomp.mode", 4, field);
    if (is_ok(error)) offset = field.offset_bytes;
    return error;
}

// Probe errors must not become a cached false and select a write-capable stack
// fallback. Probe payloads only read registers; the legacy executor may still
// write and restore receipt credentials. Classification preserves the inherited
// raw SP_EL0 heuristic, not a proven vendor structure or Huawei-adjusted result.
// No model is cached: a transient executor failure is retried by the next caller.
KModErr resolve_current_task_model(CurrentTaskModel& model) {
    uint64_t sp_el0 = 0;
    RETURN_IF_ERROR(get_kernel_shellcode_u64_result(
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
            a->mrs(asmjit::a64::x10, asmjit::Imm(kSpEl0SysReg));
            out = asmjit::a64::x10;
            return KModErr::OK;
        }, sp_el0));
    if (sp_el0 < kKernelAddressFloor || sp_el0 >= static_cast<uint64_t>(-4095LL)) {
        CurrentTaskModel resolved;
        resolved.source = CurrentTaskSource::stack_thread_info;
        RETURN_IF_ERROR(resolve_current_stack_size(resolved.thread_size));
        model = resolved;
        return KModErr::OK;
    }
    uint64_t sp = 0;
    RETURN_IF_ERROR(get_kernel_shellcode_u64_result(
        [](asmjit::a64::Assembler* a, asmjit::a64::GpX& out) -> KModErr {
            a->mov(out, asmjit::a64::sp);
            return KModErr::OK;
        }, sp));
    if (sp < kKernelAddressFloor || sp >= static_cast<uint64_t>(-4095LL))
        return KModErr::ERR_MODULE_NOT_KADDR;
    CurrentTaskModel resolved;
    for (uint64_t thread_size : kThreadSizes) {
        if (sp_el0 == (sp & ~(thread_size - 1))) {
            resolved.source = CurrentTaskSource::sp_el0_thread_info;
            model = resolved;
            return KModErr::OK;
        }
    }
    model = resolved;
    return KModErr::OK;
}

bool is_CONFIG_THREAD_INFO_IN_TASK() {
    CurrentTaskModel model;
    return is_ok(resolve_current_task_model(model)) && model.source == CurrentTaskSource::sp_el0_task;
}

bool is_CURRENT_FROM_SP_EL0_THREAD_INFO() {
    CurrentTaskModel model;
    return is_ok(resolve_current_task_model(model)) && model.source == CurrentTaskSource::sp_el0_thread_info;
}

// set_current_process_name()
//
// Overwrite the current task's `comm` with up to 15 bytes, NUL padded to the
// full 16-byte field. The kernel write uses the calling-task pointer selected
// by the current emitter; target VA-width validation is a separate boundary.
KModErr set_current_process_name(const char* comm_name) {
    if (!comm_name) return KModErr::ERR_MODULE_PARAM;
    char name[16] = {};
    const std::size_t len = strnlen(comm_name, 15);
    memcpy(name, comm_name, len);
    name[15] = '\0';

    if (maplesu_request_owner_mode()) {
        if (!maplesu_request_auth_authenticated(maplesu_request_auth_state())) {
            errno = EPERM;
            return KModErr::ERR_MODULE_ROOT_KEY;
        }
        // The native operation targets this calling task, like the legacy comm write.
        if (::prctl(PR_SET_NAME, name, 0UL, 0UL, 0UL) != 0)
            return KModErr::ERR_MODULE_PR_SET_NAME;
        char observed[sizeof(name)] = {};
        if (::prctl(PR_GET_NAME, observed, 0UL, 0UL, 0UL) != 0)
            return KModErr::ERR_MODULE_PR_GET_NAME;
        if (std::memcmp(name, observed, sizeof(name))) {
            errno = EIO;
            return KModErr::ERR_MODULE_PR_SET_NAME;
        }
        return KModErr::OK;
    }

    uint32_t comm_offset = 0;
    KModErr err = get_task_struct_comm_offset(comm_offset);
    if (is_failed(err)) {
        return err;
    }

    uint64_t current_task = 0;
    err = shellcode_current_task(current_task);
    if (is_failed(err)) {
        return err;
    }

    return write_kernel_mem(current_task + comm_offset, name, sizeof(name),
                            KernMemProt::KMP_RW);
}

}  // namespace kernel_module

// Preserve the historical internal C ABI without restoring positional guesses.
extern "C" __attribute__((visibility("hidden")))
KModErr maplesu_find_real_cred_offset(uint32_t* output) {
    if (!output) return KModErr::ERR_MODULE_PARAM;
    return kernel_module::get_task_struct_real_cred_offset(*output);
}
