// libmaplesu_parasite.so -- production parasite loader (MapleSU hidden mode).
//
// Shape: a shared object whose only job is to run code inside *somebody else's* app
// process at load time. The host library was rewritten (DT_NEEDED) to name this file,
// so bionic loads it from the host's own native-library directory before the host's
// static initialisers run.
//
// What it does (and nothing else):
//   1. Ask a su channel to check the root-owned start script. The host UID cannot
//      traverse /data/adb, so an app-side access() check would suppress every start.
//   2. Hand the script to a su channel the app's own SELinux domain can
//      execute. The script is idempotent (pid file + real readiness check in the
//      daemon), so repeated host launches cost one su round-trip, not a second daemon.
//
// Deliberate properties:
//   * the constructor starts a detached thread: a blocking/hanging su can never take
//     down the host app's static initialisation;
//   * fork/exec only -- the host app's atexit handlers must never run from a forked copy;
//   * success has no logcat output; an armed root-only activation challenge receives
//     a boot/UID/process-bound receipt after this host has started a ready daemon;
//   * no secret, no root key, no token and no package name is embedded or printed here.
//
// The su candidate order mirrors the R&D spike measurement: /system/bin/su is the shim
// the app's SELinux domain may execute, /data/adb/*/su lives on adb_data_file which
// untrusted_app cannot execute at all.

#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

namespace {

constexpr const char* kTag = "MapleSUParasite";
constexpr const char* kRootAccepted = "PARASITE_ROOT_ACCEPTED\n";
constexpr const char* kScriptCommand =
    "[ \"$(/system/bin/id -u)\" = 0 ] || exit 13; "
    "printf 'PARASITE_ROOT_ACCEPTED\\n'; "
    "[ \"$(cat /data/adb/maplesu/lite_hidden 2>/dev/null)\" != 1 ] || "
    "[ ! -f /data/adb/maplesu/recovery/parasite-start.sh ] || "
    "exec /system/bin/sh /data/adb/maplesu/recovery/parasite-start.sh "
    "--host-start %ld %lu %llu";
constexpr int kCommandTimeoutMs = 30000;

constexpr const char* kSuCandidates[] = {
    "/system/bin/su",
    "/debug_ramdisk/su",
    "/data/adb/maplesu/bin/su",
    "/data/adb/ksu/bin/su",
};

// Runs argv with stdout+stderr captured (bounded), returns the exit status or -1.
// On execv failure the child reports errno through the pipe so "su failed to start" is
// distinguishable from "su ran and the script refused".
int64_t monotonic_ms() {
    struct timespec now{};
    clock_gettime(CLOCK_MONOTONIC, &now);
    return static_cast<int64_t>(now.tv_sec) * 1000 + now.tv_nsec / 1000000;
}

int run_capture(const char* const argv[], char* output, size_t output_size, int* exec_errno) {
    if (exec_errno != nullptr) *exec_errno = 0;
    int pipe_fds[2];
    if (pipe(pipe_fds) != 0) return -1;
    pid_t pid = fork();
    if (pid < 0) {
        close(pipe_fds[0]);
        close(pipe_fds[1]);
        return -1;
    }
    if (pid == 0) {
        setpgid(0, 0);
        dup2(pipe_fds[1], STDOUT_FILENO);
        dup2(pipe_fds[1], STDERR_FILENO);
        close(pipe_fds[0]);
        close(pipe_fds[1]);
        execv(argv[0], const_cast<char* const*>(argv));
        int saved = errno;
        char note[32];
        int length = snprintf(note, sizeof(note), "EXEC_FAIL errno=%d", saved);
        if (length > 0 && write(STDOUT_FILENO, note, static_cast<size_t>(length)) < 0) {
            // Nothing left to report to.
        }
        _exit(127);
    }
    setpgid(pid, pid);
    close(pipe_fds[1]);
    fcntl(pipe_fds[0], F_SETFL, O_NONBLOCK);
    const int64_t deadline = monotonic_ms() + kCommandTimeoutMs;
    size_t used = 0;
    int status = 0;
    bool exited = false;
    bool eof = false;
    bool timed_out = false;
    while (!exited || !eof) {
        char buffer[512];
        ssize_t got;
        while ((got = read(pipe_fds[0], buffer, sizeof(buffer))) > 0) {
            const size_t available = output_size > used + 1 ? output_size - used - 1 : 0;
            const size_t copied = static_cast<size_t>(got) < available ? static_cast<size_t>(got) : available;
            if (copied > 0) memcpy(output + used, buffer, copied);
            used += copied;
            if (monotonic_ms() >= deadline) break;
        }
        if (got == 0) eof = true;
        if (!exited) {
            const pid_t waited = waitpid(pid, &status, WNOHANG);
            if (waited == pid) exited = true;
            else if (waited < 0 && errno != EINTR) { close(pipe_fds[0]); return -1; }
        }
        if (exited && eof) break;
        const int64_t remaining = deadline - monotonic_ms();
        if (remaining <= 0) {
            timed_out = true;
            // Terminate only this fork's process group, including a hung helper's descendants.
            kill(-pid, SIGKILL);
            if (!exited) {
                kill(pid, SIGKILL);
                while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {}
            }
            break;
        }
        struct pollfd fd {eof ? -1 : pipe_fds[0], POLLIN, 0};
        poll(&fd, 1, static_cast<int>(remaining < 50 ? remaining : 50));
    }
    if (output_size > 0) output[used] = '\0';
    close(pipe_fds[0]);
    if (exec_errno != nullptr && output_size > 0) {
        const char* note = strstr(output, "EXEC_FAIL errno=");
        if (note != nullptr) sscanf(note, "EXEC_FAIL errno=%d", exec_errno);
    }
    if (timed_out) return 124;
    return WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
}

// /proc/<pid>/stat field 22 distinguishes this host process from a reused PID.
unsigned long long host_start_ticks() {
    FILE* file = fopen("/proc/self/stat", "re");
    if (file == nullptr) return 0;
    char buffer[2048];
    const bool read = fgets(buffer, sizeof(buffer), file) != nullptr;
    fclose(file);
    if (!read) return 0;
    char* end = strrchr(buffer, ')');
    if (end == nullptr || end[1] != ' ') return 0;
    char* field = end + 2;
    for (int number = 3; number < 22; ++number) {
        field = strchr(field, ' ');
        if (field == nullptr) return 0;
        ++field;
    }
    char* parsed = nullptr;
    const unsigned long long ticks = strtoull(field, &parsed, 10);
    return parsed != field && (*parsed == ' ' || *parsed == '\n') ? ticks : 0;
}

void* start_thread(void*) {
    const unsigned long long ticks = host_start_ticks();
    if (ticks == 0) return nullptr;
    char command[1024];
    const int length = snprintf(command, sizeof(command), kScriptCommand,
        static_cast<long>(getpid()), static_cast<unsigned long>(getuid()), ticks);
    if (length < 0 || static_cast<size_t>(length) >= sizeof(command)) return nullptr;
    for (const char* candidate : kSuCandidates) {
        char output[256];
        int exec_errno = 0;
        const char* argv[] = {candidate, "-c", command, nullptr};
        int status = run_capture(argv, output, sizeof(output), &exec_errno);
        if (status == 0) return nullptr;  // success stays silent by design
        __android_log_print(
            ANDROID_LOG_WARN, kTag, "PARASITE_SU_FAILED su=%s status=%d exec_errno=%d",
            candidate, status, exec_errno);
        // Root already accepted this action. A script failure or timeout belongs to
        // that action, rather than to entry discovery; another su would replay it.
        if (strstr(output, kRootAccepted) != nullptr) return nullptr;
    }
    return nullptr;
}

}  // namespace

__attribute__((constructor)) void maplesu_parasite_ctor() {
    pthread_t thread;
    if (pthread_create(&thread, nullptr, start_thread, nullptr) == 0) {
        pthread_detach(thread);
    }
}
