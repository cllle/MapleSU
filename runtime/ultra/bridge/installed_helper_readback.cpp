#include "installed_helper_readback.h"
#include "../../maplesu-sdk/sdk/include/maplesu_request_private.h"
#include "../../maplesu-sdk/sdk/include/maplesu_root_private.h"

#include <algorithm>
#include <atomic>
#include <cerrno>
#include <chrono>
#include <climits>
#include <csignal>
#include <cstring>
#include <fcntl.h>
#include <initializer_list>
#include <poll.h>
#include <pthread.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <time.h>
#include <type_traits>
#include <unistd.h>

extern "C" void __set_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW(const char*);
extern "C" __attribute__((visibility("hidden"))) void maplesu_request_fork_prepare() noexcept;
extern "C" __attribute__((visibility("hidden"))) void maplesu_hook_fork_prepare() noexcept;
extern "C" __attribute__((visibility("hidden"))) void maplesu_hook_fork_complete() noexcept;
extern "C" __attribute__((visibility("hidden"))) bool maplesu_btf_try_fork_prepare() noexcept;
extern "C" __attribute__((visibility("hidden"))) void maplesu_btf_fork_complete() noexcept;
namespace default_modules::module_root { KModErr set_current_root(bool); }

namespace maplesu {
namespace {
using namespace kernel_module;
using Result = InstalledHelperReadbackResult;
using Clock = std::chrono::steady_clock;
uint64_t elapsed_us(Clock::time_point start) {
    return static_cast<uint64_t>(std::chrono::duration_cast<std::chrono::microseconds>(Clock::now() - start).count());
}
struct LayoutForkGuard {
    bool acquired = false;
    LayoutForkGuard() {
        // Hook installation can resolve BTF while holding publication. Acquire
        // publication before the nonblocking cache admission, never after it.
        maplesu_hook_fork_prepare();
        acquired = maplesu_btf_try_fork_prepare();
        if (!acquired) maplesu_hook_fork_complete();
    }
    ~LayoutForkGuard() {
        if (acquired) {
            maplesu_btf_fork_complete();
            maplesu_hook_fork_complete();
        }
    }
};
constexpr uint32_t kMagic = 0x31524248;
constexpr uint32_t kVersion = 1;
constexpr uint64_t kSlotBytes = 4096;
enum : uint32_t { kRoot = 1, kHeader, kChunk, kEnd, kFinal, kProgress };
struct Frame { uint32_t magic = kMagic, version = kVersion, kind = 0, bytes = 0; };
struct Header { uint64_t actual_size = 0, offset = 0; };
struct End {
    uint64_t actual_size = 0, offset = 0, source_bytes = 0, stream_bytes = 0;
    int32_t error = 0;
    uint8_t attempted = 0, metadata_stable = 0, path_stable = 0, source_eof = 0;
};
static_assert(std::is_trivially_copyable_v<Result>);
static_assert(sizeof(Frame) + sizeof(Result) <= PIPE_BUF);
std::atomic<uint64_t> reader_slot{0}, wait_owner{0};

void wipe(void* value, size_t size) {
    auto* bytes = static_cast<volatile uint8_t*>(value);
    while (size--) *bytes++ = 0;
}
struct Buffer {
    uint8_t bytes[kInstalledHelperReadbackChunkBytes];
    ~Buffer() { wipe(bytes, sizeof(bytes)); }
};
struct Fd {
    int value = -1;
    ~Fd() { if (value >= 0) close(value); }
    int finish() {
        const int fd = value;
        value = -1;
        return fd >= 0 && close(fd) < 0 ? errno : 0;
    }
};
uint64_t slot_for(pid_t parent, pid_t child = 0) {
    return (uint64_t(static_cast<uint32_t>(parent)) << 32) | static_cast<uint32_t>(child);
}
int budget_ms(const timespec& deadline) {
    timespec now{};
    if (clock_gettime(CLOCK_MONOTONIC, &now)) return -errno;
    const int64_t left = int64_t(deadline.tv_sec - now.tv_sec) * INT64_C(1000000000) +
        deadline.tv_nsec - now.tv_nsec;
    return left <= 0 ? 0 : static_cast<int>((left + 999999) / 1000000);
}
int expired_error(const timespec& deadline) {
    const int budget = budget_ms(deadline);
    return budget <= 0 ? budget ? -budget : ETIMEDOUT : 0;
}
pid_t wait_once(pid_t child, int& status, const timespec& deadline, int& error) {
    for (;;) {
        const pid_t waited = waitpid(child, &status, WNOHANG);
        if (waited >= 0) { error = 0; return waited; }
        error = errno;
        if (error != EINTR) return -1;
        if (const int expired = expired_error(deadline)) { error = expired; return -1; }
    }
}
void pending(Result& out, uint64_t slot) {
    out.admission_error = EINPROGRESS;
    out.child_pid = static_cast<uint32_t>(slot);
    out.cleanup_pending = out.child_pid != 0;
    out.cleanup_scheduled = out.cleanup_pending && wait_owner.load(std::memory_order_acquire) == slot;
}
bool admit(Result& out, const timespec& deadline, uint64_t& lease) {
    const auto parent = getpid();
    lease = slot_for(parent);
    auto owner = wait_owner.load(std::memory_order_acquire);
    if (owner && static_cast<uint32_t>(owner >> 32) != static_cast<uint32_t>(parent))
        wait_owner.compare_exchange_strong(owner, 0, std::memory_order_acq_rel);
    auto slot = reader_slot.load(std::memory_order_acquire);
    if (slot && static_cast<uint32_t>(slot >> 32) != static_cast<uint32_t>(parent)) {
        if (reader_slot.compare_exchange_strong(slot, lease, std::memory_order_acq_rel)) return true;
        pending(out, slot); return false;
    }
    if (!slot) {
        if (!wait_owner.load(std::memory_order_acquire) &&
            reader_slot.compare_exchange_strong(slot, lease, std::memory_order_acq_rel)) return true;
        pending(out, slot); return false;
    }
    if (!static_cast<uint32_t>(slot)) { pending(out, slot); return false; }
    owner = 0;
    if (!wait_owner.compare_exchange_strong(owner, slot, std::memory_order_acq_rel)) {
        pending(out, slot); return false;
    }
    if (reader_slot.load(std::memory_order_acquire) != slot) {
        auto expected = slot;
        wait_owner.compare_exchange_strong(expected, 0, std::memory_order_acq_rel);
        pending(out, reader_slot.load(std::memory_order_acquire)); return false;
    }
    int status = 0, error = 0;
    const pid_t waited = wait_once(static_cast<pid_t>(static_cast<uint32_t>(slot)), status, deadline, error);
    const bool reclaimed = waited > 0 || (waited < 0 && error == ECHILD);
    auto expected = slot;
    const bool acquired = reclaimed && reader_slot.compare_exchange_strong(expected, lease, std::memory_order_acq_rel);
    if (waited < 0 && error != ECHILD) out.cleanup_error = error;
    if (!acquired) pending(out, slot);
    expected = slot;
    wait_owner.compare_exchange_strong(expected, 0, std::memory_order_acq_rel);
    if (!acquired) out.cleanup_scheduled = false;
    return acquired;
}
void* reap(void* value) {
    const auto child = static_cast<pid_t>(reinterpret_cast<uintptr_t>(value));
    const auto slot = slot_for(getpid(), child);
    int status = 0;
    pid_t waited;
    do { waited = waitpid(child, &status, 0); } while (waited < 0 && errno == EINTR);
    if (waited == child || (waited < 0 && errno == ECHILD)) {
        auto expected = slot;
        reader_slot.compare_exchange_strong(expected, 0, std::memory_order_acq_rel);
    }
    auto expected = slot;
    wait_owner.compare_exchange_strong(expected, 0, std::memory_order_acq_rel);
    return nullptr;
}
void defer_cleanup(pid_t child, Result& out) {
    const auto slot = slot_for(getpid(), child);
    wait_owner.store(slot, std::memory_order_release);
    reader_slot.store(slot, std::memory_order_release);
    out.cleanup_pending = true;
    pthread_attr_t attributes;
    int error = pthread_attr_init(&attributes);
    if (!error) {
        error = pthread_attr_setdetachstate(&attributes, PTHREAD_CREATE_DETACHED);
        if (!error) {
            pthread_t thread;
            error = pthread_create(&thread, &attributes, reap,
                reinterpret_cast<void*>(static_cast<uintptr_t>(child)));
            out.cleanup_scheduled = !error;
        }
        const int destroy_error = pthread_attr_destroy(&attributes);
        if (!error) error = destroy_error;
    }
    if (error && !out.cleanup_error) out.cleanup_error = error;
    if (!out.cleanup_scheduled) {
        auto expected = slot;
        wait_owner.compare_exchange_strong(expected, 0, std::memory_order_acq_rel);
    }
}
int arm_watchdog(const timespec& deadline) {
    sigevent event{};
    event.sigev_notify = SIGEV_SIGNAL;
    event.sigev_signo = SIGKILL;
    timer_t timer;
    if (timer_create(CLOCK_MONOTONIC, &event, &timer)) return errno;
    itimerspec expiration{};
    expiration.it_value = deadline;
    return timer_settime(timer, TIMER_ABSTIME, &expiration, nullptr) ? errno : 0;
}
int write_exact(int fd, const void* value, size_t bytes, const timespec& deadline) {
    auto* data = static_cast<const uint8_t*>(value);
    while (bytes) {
        if (const int error = expired_error(deadline)) return error;
        const ssize_t count = write(fd, data, bytes);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return count ? errno : EIO;
        data += count;
        bytes -= static_cast<size_t>(count);
    }
    return 0;
}
int write_frame(int fd, uint32_t kind, const void* data, uint32_t bytes, const timespec& deadline) {
    const Frame header{kMagic, kVersion, kind, bytes};
    const int error = write_exact(fd, &header, sizeof(header), deadline);
    return error ? error : write_exact(fd, data, bytes, deadline);
}
bool progress(Result& out, const char* phase, int output, const timespec& deadline) {
    std::strncpy(out.root_phase, phase, sizeof(out.root_phase) - 1);
    const int error = write_frame(output, kProgress, &out, sizeof(out), deadline);
    if (error) out.child_io_error = error;
    return !error;
}
int read_exact(int fd, void* value, size_t bytes, const timespec& deadline) {
    auto* data = static_cast<uint8_t*>(value);
    while (bytes) {
        const int budget = budget_ms(deadline);
        if (budget <= 0) return budget ? -budget : ETIMEDOUT;
        pollfd watch{fd, POLLIN, 0};
        const int ready = poll(&watch, 1, budget);
        if (ready < 0 && errno == EINTR) continue;
        if (ready < 0) return errno;
        if (!ready) return ETIMEDOUT;
        if (watch.revents & POLLNVAL) return EBADF;
        const ssize_t count = read(fd, data, bytes);
        if (count < 0 && errno == EINTR) continue;
        if (count < 0) return errno;
        if (!count) return EPIPE;
        data += count;
        bytes -= static_cast<size_t>(count);
    }
    return 0;
}
int read_eof(int fd, const timespec& deadline) {
    for (;;) {
        const int budget = budget_ms(deadline);
        if (budget <= 0) return budget ? -budget : ETIMEDOUT;
        pollfd watch{fd, POLLIN, 0};
        const int ready = poll(&watch, 1, budget);
        if (ready < 0 && errno == EINTR) continue;
        if (ready < 0) return errno;
        if (!ready) return ETIMEDOUT;
        if (watch.revents & POLLNVAL) return EBADF;
        uint8_t extra = 0;
        const ssize_t count = read(fd, &extra, 1);
        wipe(&extra, sizeof(extra));
        if (count < 0 && errno == EINTR) continue;
        return count < 0 ? errno : count ? EPROTO : 0;
    }
}
bool alphanumeric(unsigned char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
}
bool valid_path(const char* path) {
    if (!path || strnlen(path, 28) != 27 || std::memcmp(path, "/data/", 6) ||
        std::memcmp(path + 22, "/init", 5)) return false;
    for (size_t index = 6; index < 22; ++index) if (!alphanumeric(path[index])) return false;
    return true;
}
bool valid_key(const char* key) {
    if (!key || strnlen(key, 49) != 48) return false;
    for (size_t index = 0; index < 48; ++index) if (!alphanumeric(key[index])) return false;
    return true;
}
void copy_reply(ManagerOwnerRootCommand& out, const RequestManagementReply& reply) {
    out.reply_observed = reply.command != 0;
    out.command = reply.command; out.status = reply.status;
    out.result = reply.result; out.handle = reply.handle;
    out.effects = reply.effects; out.completed = reply.completed;
}
bool command_ok(const ManagerOwnerRootCommand& value, uint32_t command, uint32_t effect) {
    return value.attempted && !value.error && value.reply_observed && value.completed &&
        value.command == command && !value.status && !value.result && !value.handle &&
        (value.effects & effect) == effect;
}
bool deployment_ok(const ManagerOwnerRootCommand& value) {
    return value.attempted && !value.error && value.reply_observed && value.completed &&
        value.command == MAPLESU_REQUEST_DEPLOYMENT_READ && !value.status &&
        value.result == MAPLESU_DEPLOYMENT_NAMESPACE_WIRE_BYTES && !value.handle && !value.effects;
}
bool same_command(const ManagerOwnerRootCommand& left, const ManagerOwnerRootCommand& right) {
    return left.attempted == right.attempted && left.reply_observed == right.reply_observed &&
        left.error == right.error && left.command == right.command && left.status == right.status &&
        left.result == right.result && left.handle == right.handle && left.effects == right.effects &&
        left.completed == right.completed;
}
bool same_authentication(const Result& left, const Result& right) {
    return left.mode_selected == right.mode_selected && left.mode_error == right.mode_error &&
        left.owner_bound == right.owner_bound && left.channel_error == right.channel_error &&
        left.owner_full_uid == right.owner_full_uid && left.owner_epoch == right.owner_epoch &&
        left.host_low == right.host_low && left.host_high == right.host_high &&
        left.namespace_requested == right.namespace_requested && left.namespace_resolved == right.namespace_resolved &&
        !std::memcmp(left.helper_path, right.helper_path, sizeof(left.helper_path)) &&
        same_command(left.deployment, right.deployment) && same_command(left.root, right.root) &&
        same_command(left.task_state, right.task_state) && left.transition_observed == right.transition_observed &&
        left.root_committed == right.root_committed && left.root_verified == right.root_verified &&
        left.credentials_managed == right.credentials_managed &&
        left.credentials_error == right.credentials_error && left.root_system_error == right.root_system_error &&
        left.legacy_environment_completed == right.legacy_environment_completed &&
        !std::memcmp(left.root_phase, right.root_phase, sizeof(left.root_phase)) &&
        left.ruid == right.ruid && left.euid == right.euid && left.uid_observed == right.uid_observed &&
        left.uid_error == right.uid_error;
}
bool valid_snapshot_path(const Result& out, const char* requested) {
    if (out.helper_path[sizeof(out.helper_path) - 1] ||
        out.namespace_requested != (out.owner_mode && requested == nullptr)) return false;
    if (requested) return valid_path(out.helper_path) && !std::strcmp(requested, out.helper_path) &&
        !out.namespace_resolved && !out.deployment.attempted;
    if (!out.namespace_resolved) return !out.helper_path[0] && !out.root.attempted &&
        !out.root_verified && !out.read_attempted;
    return out.owner_bound && valid_path(out.helper_path) && deployment_ok(out.deployment);
}
void authenticate(Result& out, const char* key, const char* path, int output, const timespec& deadline) {
    if (path) std::memcpy(out.helper_path, path, sizeof(out.helper_path));
    if (!progress(out, "auth-mode", output, deadline)) return;
    out.mode_error = static_cast<int64_t>(maplesu_request_set_auth_mode(
        out.owner_mode ? RequestAuthMode::ManagerOwner : RequestAuthMode::LegacyKey));
    out.mode_selected = !out.mode_error;
    if (!out.mode_selected) { out.sdk_error = out.mode_error; return; }
    if (out.owner_mode) {
        out.credentials_managed = true;
        maplesu_metadata_owner_v3 metadata{};
        uint64_t descriptor = 0;
        RequestOwnerBinding binding{};
        if (!progress(out, "owner-channel", output, deadline)) return;
        out.channel_error = static_cast<int64_t>(maplesu_request_owner_channel(metadata, descriptor, binding));
        out.owner_bound = !out.channel_error;
        if (!out.owner_bound) { out.sdk_error = out.channel_error; return; }
        out.owner_full_uid = metadata.owner_full_uid; out.owner_epoch = metadata.owner_epoch;
        out.host_low = metadata.host.low; out.host_high = metadata.host.high;
        if (out.namespace_requested) {
            RequestDeploymentNamespace deployment{};
            out.deployment.attempted = true;
            if (!progress(out, "deployment-namespace", output, deadline)) return;
            out.deployment.error = static_cast<int64_t>(maplesu_request_owner_deployment(deployment));
            copy_reply(out.deployment, maplesu_request_management_reply());
            if (out.deployment.error) { out.sdk_error = out.deployment.error; return; }
            if (!deployment_ok(out.deployment)) {
                out.sdk_error = static_cast<int64_t>(KModErr::ERR_MODULE_TEST_CHANNEL);
                return;
            }
            const auto& actual = deployment.binding;
            if (!maplesu_request_owner_binding_matches(actual) ||
                actual.process != static_cast<uint64_t>(getpid()) || actual.actual_uid != getuid() ||
                actual.auth_state != binding.auth_state || actual.process != binding.process ||
                actual.actual_uid != binding.actual_uid || actual.full_uid != out.owner_full_uid ||
                actual.epoch != out.owner_epoch || actual.host.low != out.host_low || actual.host.high != out.host_high) {
                out.sdk_error = static_cast<int64_t>(KModErr::ERR_MODULE_ROOT_KEY);
                return;
            }
            std::memcpy(out.helper_path, "/data/", 6);
            std::memcpy(out.helper_path + 6, deployment.record.name, MAPLESU_DEPLOYMENT_NAMESPACE_BYTES);
            std::memcpy(out.helper_path + 22, "/init", 6);
            if (!valid_path(out.helper_path)) {
                std::memset(out.helper_path, 0, sizeof(out.helper_path));
                out.sdk_error = static_cast<int64_t>(KModErr::ERR_MODULE_TEST_CHANNEL);
                return;
            }
            out.namespace_resolved = true;
        }
        out.root.attempted = true;
        if (!progress(out, "root-transition", output, deadline)) return;
        out.root.error = static_cast<int64_t>(maplesu_request_owner_root());
        copy_reply(out.root, maplesu_request_management_reply());
        out.credentials_error = out.root.error;
        std::memcpy(out.root_phase, "credentials", sizeof("credentials"));
        out.root_committed = out.root.completed && out.root.command == MAPLESU_REQUEST_CRED_ROOT &&
            (out.root.effects & MAPLESU_EFFECT_COMMITTED);
    } else {
        if (!progress(out, "legacy-key", output, deadline)) return;
        __set_current_module_root_key_hd04aBTnbSZlTRP7S4mQpIeVvr2ssGjW(key);
        uint64_t capabilities = 0;
        if (!progress(out, "credential-channel", output, deadline)) return;
        out.channel_error = static_cast<int64_t>(maplesu_request_credential_channel(out.credentials_managed, capabilities));
        if (out.channel_error) { out.sdk_error = out.channel_error; return; }
        if (!progress(out, "root-transition", output, deadline)) return;
        if (out.credentials_managed) {
            // Key remains LegacyKey. Use the same authenticated credential command
            // as the composite SDK Root path, without preparing an su environment.
            if (!(capabilities & MAPLESU_CAP_CREDENTIAL)) {
                out.root.error = static_cast<int64_t>(KModErr::ERR_MODULE_TEST_CHANNEL);
            } else {
                out.root.attempted = true;
                errno = 0;
                out.root.error = static_cast<int64_t>(maplesu_request_owner_root());
                out.root_system_error = errno;
                copy_reply(out.root, maplesu_request_management_reply());
            }
            out.credentials_error = out.root.error;
            std::memcpy(out.root_phase, "credentials", sizeof("credentials"));
            out.root_committed = out.root.completed && out.root.command == MAPLESU_REQUEST_CRED_ROOT &&
                (out.root.effects & MAPLESU_EFFECT_COMMITTED);
        } else {
            out.root.attempted = true;
            out.root.error = static_cast<int64_t>(default_modules::module_root::set_current_root(false));
            const auto transition = default_modules::module_root::current_root_transition();
            out.credentials_error = static_cast<int64_t>(transition.credentials_error);
            out.root_system_error = transition.system_error;
            out.legacy_environment_completed = transition.environment_completed;
            out.transition_observed = transition.phase != nullptr;
            out.task_state.attempted = transition.task_state_attempted;
            out.task_state.error = static_cast<int64_t>(transition.task_state_error);
            copy_reply(out.task_state, transition.task_state);
            if (transition.phase) std::strncpy(out.root_phase, transition.phase, sizeof(out.root_phase) - 1);
        }
    }
    out.sdk_error = out.root.error;
    uid_t ruid, euid, suid;
    if (getresuid(&ruid, &euid, &suid)) out.uid_error = errno;
    else { out.ruid = ruid; out.euid = euid; out.uid_observed = true; }
    out.root_verified = !out.root.error && out.uid_observed && !out.ruid && !out.euid &&
        (!out.owner_mode || out.owner_bound) &&
        (!out.credentials_managed || command_ok(out.root, MAPLESU_REQUEST_CRED_ROOT, MAPLESU_EFFECT_COMMITTED));
}
void close_owner(Result& out) {
    if (!out.credentials_managed || !out.mode_selected) return;
    out.close.attempted = true;
    try { out.close.error = static_cast<int64_t>(maplesu_request_owner_close()); }
    catch (...) { out.child_exception = true; out.close.error = static_cast<int64_t>(KModErr::ERR_MODULE_TEST_CHANNEL); }
    const auto reply = maplesu_request_management_reply();
    if (reply.command == MAPLESU_REQUEST_SESSION_CLOSE) copy_reply(out.close, reply);
    if (!out.sdk_error && out.close.error) out.sdk_error = out.close.error;
}
bool same_file(const struct stat& a, const struct stat& b) {
    return S_ISREG(b.st_mode) && a.st_dev == b.st_dev && a.st_ino == b.st_ino && a.st_size == b.st_size &&
        a.st_mtim.tv_sec == b.st_mtim.tv_sec && a.st_mtim.tv_nsec == b.st_mtim.tv_nsec &&
        a.st_ctim.tv_sec == b.st_ctim.tv_sec && a.st_ctim.tv_nsec == b.st_ctim.tv_nsec;
}
int produce(int output, const char* path, Result& out, const timespec& deadline) {
    out.read_attempted = true;
    Fd source{open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK)};
    if (source.value < 0) return errno;
    struct stat before{};
    if (fstat(source.value, &before)) return errno;
    if (!S_ISREG(before.st_mode) || before.st_size <= 0) return EINVAL;
    out.actual_size = static_cast<uint64_t>(before.st_size);
    if (out.actual_size > kInstalledHelperReadbackMaxBytes) return EOVERFLOW;
    if (out.normalized_offset > out.actual_size || kSlotBytes > out.actual_size - out.normalized_offset)
        return EINVAL;
    const Header header{out.actual_size, out.normalized_offset};
    if (const int error = write_frame(output, kHeader, &header, sizeof(header), deadline)) {
        out.child_io_error = error; return error;
    }
    out.header_received = true;
    Buffer buffer;
    while (out.source_bytes < out.actual_size) {
        if (const int error = expired_error(deadline)) return error;
        const auto wanted = static_cast<size_t>(std::min<uint64_t>(sizeof(buffer.bytes), out.actual_size - out.source_bytes));
        const ssize_t count = read(source.value, buffer.bytes, wanted);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return count ? errno : EIO;
        const uint64_t start = out.source_bytes;
        out.source_bytes += static_cast<uint64_t>(count);
        if (out.normalized_offset) {
            const uint64_t first = std::max(start, out.normalized_offset);
            const uint64_t last = std::min(out.source_bytes, out.normalized_offset + kSlotBytes);
            if (last > first) std::memset(buffer.bytes + (first - start), 0, static_cast<size_t>(last - first));
        }
        const int error = write_frame(output, kChunk, buffer.bytes, static_cast<uint32_t>(count), deadline);
        wipe(buffer.bytes, static_cast<size_t>(count));
        if (error) { out.child_io_error = error; return error; }
        out.stream_bytes += static_cast<uint64_t>(count);
    }
    uint8_t extra = 0;
    ssize_t count;
    do {
        if (const int error = expired_error(deadline)) return error;
        count = read(source.value, &extra, 1);
    } while (count < 0 && errno == EINTR);
    wipe(&extra, sizeof(extra));
    if (count) return count < 0 ? errno : ESTALE;
    out.source_eof = true;
    struct stat after{}, installed{};
    if (fstat(source.value, &after)) return errno;
    if (!same_file(before, after)) return ESTALE;
    out.metadata_stable = true;
    if (lstat(path, &installed)) return errno;
    if (!same_file(before, installed)) return ESTALE;
    out.path_stable = true;
    return source.finish();
}
[[noreturn]] void child_run(int input, int output, const char* key, const char* path,
    uint64_t offset, const timespec& deadline) {
    Result out;
    out.owner_mode = key == nullptr;
    out.namespace_requested = out.owner_mode && path == nullptr;
    if (path) std::memcpy(out.helper_path, path, sizeof(out.helper_path));
    out.normalized_offset = offset;
    out.child_pid = static_cast<uint32_t>(getpid());
    (void)progress(out, "worker-start", output, deadline);
    out.watchdog_error = arm_watchdog(deadline);
    out.watchdog_armed = !out.watchdog_error;
    if (close(input)) out.child_io_error = errno;
    if (signal(SIGPIPE, SIG_IGN) == SIG_ERR && !out.child_io_error) out.child_io_error = errno;
    const auto authentication_started = Clock::now();
    if (out.watchdog_armed && !out.child_io_error) try { authenticate(out, key, path, output, deadline); }
    catch (...) {
        out.child_exception = true;
        if (out.deployment.attempted && !out.root.attempted) {
            if (!out.deployment.error) out.deployment.error = static_cast<int64_t>(KModErr::ERR_MODULE_TEST_CHANNEL);
            copy_reply(out.deployment, maplesu_request_management_reply());
        }
        if (out.root.attempted) {
            out.root.error = static_cast<int64_t>(KModErr::ERR_MODULE_TEST_CHANNEL);
            if (out.owner_mode) {
                copy_reply(out.root, maplesu_request_management_reply());
                out.root_committed = out.root.completed && out.root.command == MAPLESU_REQUEST_CRED_ROOT &&
                    (out.root.effects & MAPLESU_EFFECT_COMMITTED);
            }
        }
        out.sdk_error = static_cast<int64_t>(KModErr::ERR_MODULE_TEST_CHANNEL);
    }
    out.authentication_us = elapsed_us(authentication_started);
    out.root_checkpoint_received = true;
    int delivered = write_frame(output, kRoot, &out, sizeof(out), deadline);
    if (!delivered && out.root_verified) {
        const auto source_started = Clock::now();
        try { out.producer_error = produce(output, out.helper_path, out, deadline); }
        catch (...) { out.child_exception = true; out.producer_error = EIO; }
        out.source_us = elapsed_us(source_started);
    }
    const End end{out.actual_size, out.normalized_offset, out.source_bytes, out.stream_bytes,
        out.producer_error, uint8_t(out.read_attempted), uint8_t(out.metadata_stable),
        uint8_t(out.path_stable), uint8_t(out.source_eof)};
    if (!delivered) delivered = write_frame(output, kEnd, &end, sizeof(end), deadline);
    if (delivered && !out.child_io_error) out.child_io_error = delivered;
    close_owner(out);
    out.receipt_complete = true;
    const int final_error = write_frame(output, kFinal, &out, sizeof(out), deadline);
    if (close(output) && !delivered) delivered = errno;
    _exit(!delivered && !final_error && !out.child_io_error ? 0 : 1);
}
bool end_matches(const End& end, const Result& value) {
    return value.actual_size == end.actual_size && value.normalized_offset == end.offset &&
        value.source_bytes == end.source_bytes && value.stream_bytes == end.stream_bytes &&
        value.producer_error == end.error && value.read_attempted == bool(end.attempted) &&
        value.metadata_stable == bool(end.metadata_stable) && value.path_stable == bool(end.path_stable) &&
        value.source_eof == bool(end.source_eof);
}
int receive(int input, pid_t child, bool owner, const char* path, uint64_t offset, Result& out,
    InstalledHelperChunkConsumer consume, void* context, const timespec& deadline) {
    bool root_seen = false, header_seen = false, end_seen = false;
    Header header;
    End end;
    Buffer buffer;
    for (;;) {
        Frame frame;
        if (const int error = read_exact(input, &frame, sizeof(frame), deadline)) return error;
        if (frame.magic != kMagic || frame.version != kVersion) return EPROTO;
        if (frame.kind == kRoot || frame.kind == kFinal || frame.kind == kProgress) {
            if (frame.bytes != sizeof(Result) || (frame.kind == kFinal ? !root_seen || !end_seen : root_seen)) return EPROTO;
            Result snapshot;
            if (const int error = read_exact(input, &snapshot, sizeof(snapshot), deadline)) return error;
            if (snapshot.abi_version != 1 || snapshot.child_pid != static_cast<uint32_t>(child) ||
                snapshot.owner_mode != owner || snapshot.normalized_offset != offset ||
                snapshot.root_checkpoint_received != (frame.kind != kProgress) || snapshot.receipt_complete != (frame.kind == kFinal) ||
                snapshot.root_phase[sizeof(snapshot.root_phase) - 1] || !valid_snapshot_path(snapshot, path)) return EPROTO;
            // Progress is tentative: only kRoot carries the completed authentication.
            if (frame.kind == kProgress) {
                if (snapshot.root_verified || snapshot.read_attempted || snapshot.header_received ||
                    snapshot.producer_end_received) return EPROTO;
                out = snapshot;
                continue;
            }
            if (frame.kind == kRoot) { out = snapshot; root_seen = true; continue; }
            if (!end_matches(end, snapshot) || !same_authentication(out, snapshot)) return EPROTO;
            const auto received = out.received_bytes;
            out = snapshot;
            out.received_bytes = received;
            out.header_received = header_seen;
            out.producer_end_received = true;
            return 0;
        }
        if (!root_seen || end_seen) return EPROTO;
        if (frame.kind == kHeader) {
            if (header_seen || frame.bytes != sizeof(Header) || !out.root_verified) return EPROTO;
            if (const int error = read_exact(input, &header, sizeof(header), deadline)) return error;
            if (header.actual_size > kInstalledHelperReadbackMaxBytes || header.offset != offset ||
                header.offset > header.actual_size || kSlotBytes > header.actual_size - header.offset) return EPROTO;
            out.actual_size = header.actual_size; out.header_received = header_seen = true;
        } else if (frame.kind == kChunk) {
            if (!header_seen || !frame.bytes || frame.bytes > sizeof(buffer.bytes) ||
                out.received_bytes > header.actual_size || frame.bytes > header.actual_size - out.received_bytes) return EPROTO;
            if (const int error = read_exact(input, buffer.bytes, frame.bytes, deadline)) return error;
            bool accepted = false;
            try { accepted = consume(context, buffer.bytes, frame.bytes); }
            catch (...) { out.sink_error = ECANCELED; }
            wipe(buffer.bytes, frame.bytes);
            if (!accepted) { out.sink_error = ECANCELED; return ECANCELED; }
            out.received_bytes += frame.bytes;
        } else if (frame.kind == kEnd) {
            if (frame.bytes != sizeof(End)) return EPROTO;
            if (const int error = read_exact(input, &end, sizeof(end), deadline)) return error;
            if (end.error < 0 || end.offset != offset || end.stream_bytes != out.received_bytes ||
                end.actual_size > kInstalledHelperReadbackMaxBytes || end.source_bytes > end.actual_size ||
                (header_seen && end.actual_size != header.actual_size) ||
                end.attempted > 1 || end.metadata_stable > 1 || end.path_stable > 1 || end.source_eof > 1 ||
                (!end.error && end.attempted && (!header_seen || end.source_bytes != end.actual_size ||
                    end.stream_bytes != end.actual_size || !end.metadata_stable || !end.path_stable || !end.source_eof))) return EPROTO;
            out.actual_size = end.actual_size; out.source_bytes = end.source_bytes;
            out.stream_bytes = end.stream_bytes; out.producer_error = end.error;
            out.read_attempted = end.attempted; out.metadata_stable = end.metadata_stable;
            out.path_stable = end.path_stable; out.source_eof = end.source_eof;
            out.producer_end_received = true;
            end_seen = true;
        } else return EPROTO;
    }
}
int finish_wait(pid_t child, Result& out, const timespec& deadline, uint64_t lease, const timespec& began) {
    int error = 0;
    pid_t waited;
    for (;;) {
        waited = wait_once(child, out.child_status, deadline, error);
        if (waited || (error = expired_error(deadline))) break;
        poll(nullptr, 0, 1);
    }
    // Freeze the deadline at the final required observation, before releasing
    // the slot. Cleanup or a later scheduling delay cannot undo an observed exit.
    timespec completed{};
    int deadline_error = clock_gettime(CLOCK_MONOTONIC, &completed) ? errno : 0;
    if (!deadline_error) {
        const int64_t elapsed_ns = int64_t(completed.tv_sec - began.tv_sec) * INT64_C(1000000000) +
            completed.tv_nsec - began.tv_nsec;
        out.completion_us = elapsed_ns > 0 ? static_cast<uint64_t>(elapsed_ns / 1000) : 0;
        if (completed.tv_sec > deadline.tv_sec ||
            (completed.tv_sec == deadline.tv_sec && completed.tv_nsec >= deadline.tv_nsec))
            deadline_error = ETIMEDOUT;
    }
    if (waited == child) out.child_status_valid = true;
    else if (error) out.wait_error = error;
    if (error == ETIMEDOUT) {
        out.timed_out = true;
        if (!out.termination_requested) {
            out.termination_requested = true;
            if (kill(child, SIGKILL) && errno != ESRCH) out.termination_error = errno;
        }
    }
    if (waited == child || (waited < 0 && error == ECHILD))
        reader_slot.compare_exchange_strong(lease, 0, std::memory_order_acq_rel);
    else defer_cleanup(child, out);
    if (out.child_status_valid && (!WIFEXITED(out.child_status) || WEXITSTATUS(out.child_status)) && !out.wait_error)
        out.wait_error = EPROTO;
    return deadline_error;
}
} // namespace

InstalledHelperReadbackResult read_installed_helper(const char* key, const char* path,
    int64_t slot_offset, InstalledHelperChunkConsumer consume, void* context, uint32_t timeout_ms) {
    Result out;
    out.owner_mode = key == nullptr;
    out.namespace_requested = out.owner_mode && path == nullptr;
    const auto start = Clock::now();
    out.normalized_offset = slot_offset > 0 ? static_cast<uint64_t>(slot_offset) : 0;
    timespec deadline{};
    if ((path ? !valid_path(path) : !out.owner_mode) || (key && !valid_key(key)) || !consume ||
        !timeout_ms || timeout_ms > 5000) out.system_error = EINVAL;
    else if (clock_gettime(CLOCK_MONOTONIC, &deadline)) out.system_error = errno;
    else {
        const timespec began = deadline;
        deadline.tv_sec += timeout_ms / 1000;
        deadline.tv_nsec += (timeout_ms % 1000) * 1000000;
        if (deadline.tv_nsec >= 1000000000) { ++deadline.tv_sec; deadline.tv_nsec -= 1000000000; }
        uint64_t lease = 0;
        if (admit(out, deadline, lease)) {
            const auto admission_us = elapsed_us(start);
            int fds[2];
            if (pipe2(fds, O_CLOEXEC)) {
                out.transport_error = errno;
                reader_slot.compare_exchange_strong(lease, 0, std::memory_order_acq_rel);
            } else {
                pid_t child = -1;
                int fork_error = 0;
                const auto fork_started = Clock::now();
                {
                    LayoutForkGuard layout;
                    if (!layout.acquired) out.admission_error = EAGAIN;
                    else if (const int expired = expired_error(deadline)) {
                        out.admission_error = expired;
                        out.timed_out = expired == ETIMEDOUT;
                    }
                    else {
                        maplesu_request_fork_prepare();
                        child = fork();
                        if (child < 0) fork_error = errno;
                    }
                }
                const auto fork_us = elapsed_us(fork_started);
                out.admission_us = admission_us;
                out.fork_us = fork_us;
                if (!child) child_run(fds[0], fds[1], key, path, out.normalized_offset, deadline);
                if (child < 0) {
                    if (!out.admission_error) out.transport_error = fork_error;
                    close(fds[0]); close(fds[1]);
                    reader_slot.compare_exchange_strong(lease, 0, std::memory_order_acq_rel);
                } else {
                    const int prior_cleanup_error = out.cleanup_error;
                    int error = close(fds[1]) ? errno : 0;
                    const auto receive_started = Clock::now();
                    const int received = receive(fds[0], child, out.owner_mode, path, out.normalized_offset,
                        out, consume, context, deadline);
                    if (!error) error = received;
                    if (!received) {
                        const int eof_error = read_eof(fds[0], deadline);
                        out.stream_eof = !eof_error;
                        if (!error) error = eof_error;
                    }
                    out.child_pid = static_cast<uint32_t>(child);
                    out.cleanup_error = prior_cleanup_error;
                    if (close(fds[0]) && !error) error = errno;
                    out.transport_error = error;
                    out.timed_out = error == ETIMEDOUT;
                    if (error) {
                        out.termination_requested = true;
                        if (kill(child, SIGKILL) && errno != ESRCH) out.termination_error = errno;
                    }
                    out.receive_us = elapsed_us(receive_started);
                    const auto wait_started = Clock::now();
                    const int deadline_error = finish_wait(child, out, deadline, lease, began);
                    out.wait_us = elapsed_us(wait_started);
                    out.admission_us = admission_us;
                    out.fork_us = fork_us;
                    if (deadline_error == ETIMEDOUT) out.timed_out = true;
                    else if (deadline_error && !out.transport_error) out.transport_error = deadline_error;
                    out.complete = valid_snapshot_path(out, path) && out.root_verified && !out.sdk_error && !out.watchdog_error &&
                        out.watchdog_armed && !out.child_exception && !out.child_io_error &&
                        out.header_received && out.producer_end_received && !out.producer_error && !out.sink_error &&
                        out.read_attempted && out.source_eof && out.metadata_stable && out.path_stable &&
                        out.actual_size == out.source_bytes && out.actual_size == out.stream_bytes &&
                        out.actual_size == out.received_bytes && out.root_checkpoint_received &&
                        out.receipt_complete && out.stream_eof && !out.transport_error && !out.wait_error &&
                        out.child_status_valid && WIFEXITED(out.child_status) && !WEXITSTATUS(out.child_status) &&
                        !out.timed_out && !out.termination_requested && !out.termination_error &&
                        !out.cleanup_pending && !out.cleanup_scheduled && !out.cleanup_error &&
                        (!out.credentials_managed || command_ok(out.close, MAPLESU_REQUEST_SESSION_CLOSE, MAPLESU_EFFECT_SESSION_CLOSED));
                }
            }
        }
    }
    if (!out.system_error) {
        for (const int error : {out.admission_error, out.watchdog_error, out.transport_error,
                out.sink_error, out.producer_error, out.uid_error, out.wait_error, out.cleanup_error})
            if (error) { out.system_error = error; break; }
        if (!out.system_error && out.timed_out) out.system_error = ETIMEDOUT;
        if (!out.complete && !out.system_error && !out.sdk_error) out.system_error = EPROTO;
    }
    out.elapsed_us = static_cast<uint64_t>(std::chrono::duration_cast<std::chrono::microseconds>(Clock::now() - start).count());
    return out;
}

} // namespace maplesu
