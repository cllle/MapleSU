#pragma once

#include "manager_owner_root_probe.h"
#include <cstddef>
#include <cstdint>

namespace maplesu {

inline constexpr size_t kInstalledHelperReadbackChunkBytes = 64 * 1024;
inline constexpr uint64_t kInstalledHelperReadbackMaxBytes = (UINT64_C(1) << 53) - 1;

struct InstalledHelperReadbackResult {
    uint32_t abi_version = 1;
    bool owner_mode = false, mode_selected = false, owner_bound = false;
    int64_t mode_error = 0, channel_error = 0, sdk_error = 0;
    uint32_t owner_full_uid = 0, owner_epoch = 0;
    uint64_t host_low = 0, host_high = 0;
    ManagerOwnerRootCommand root, close;
    ManagerOwnerRootCommand deployment;
    bool namespace_requested = false, namespace_resolved = false;
    char helper_path[28]{};
    bool root_committed = false, root_verified = false, credentials_managed = false;
    uint32_t ruid = UINT32_MAX, euid = UINT32_MAX;
    bool uid_observed = false;
    int32_t uid_error = 0, root_system_error = 0;
    int64_t credentials_error = 0;
    char root_phase[32]{};
    bool legacy_environment_completed = false;
    ManagerOwnerRootCommand task_state;
    bool transition_observed = false;

    uint64_t actual_size = 0, normalized_offset = 0;
    uint64_t source_bytes = 0, stream_bytes = 0, received_bytes = 0;
    bool read_attempted = false, header_received = false, producer_end_received = false;
    bool metadata_stable = false, path_stable = false, source_eof = false;
    int32_t producer_error = 0, sink_error = 0;
    bool root_checkpoint_received = false, receipt_complete = false, stream_eof = false;

    uint32_t child_pid = 0;
    int32_t system_error = 0, transport_error = 0, wait_error = 0;
    int32_t watchdog_error = 0, child_io_error = 0;
    bool watchdog_armed = false, child_exception = false;
    bool timed_out = false, termination_requested = false;
    int32_t termination_error = 0, child_status = 0;
    bool child_status_valid = false;
    int32_t admission_error = 0, cleanup_error = 0;
    bool cleanup_pending = false, cleanup_scheduled = false;
    uint64_t completion_us = 0;
    uint64_t elapsed_us = 0;
    uint64_t admission_us = 0, fork_us = 0, authentication_us = 0;
    uint64_t source_us = 0, receive_us = 0, wait_us = 0;
    bool complete = false;
};

// Runs only in the calling parent thread; bytes are ephemeral and tentative.
// A false return cancels the worker. Publish a digest only when complete is true.
using InstalledHelperChunkConsumer = bool (*)(void*, const uint8_t*, size_t);

// Call under the bridge Isolated admission; this owns a bounded worker/slot.
// Null Key explicitly selects Owner;
// a non-null Key explicitly selects Legacy. No fallback or transport replay.
// Only /data/[A-Za-z0-9]{16}/init is accepted. A null path is Owner-only
// and resolves the retained image namespace in this worker's authenticated session.
// offset <= 0 hashes unchanged bytes.
// timeout_ms is the single worker/transport/wait deadline (1..5000, page default 300).
// Existing SDK publication-lock admission can wait longer; an expired admission
// is rejected before fork, and no stale worker is submitted.
__attribute__((visibility("hidden")))
InstalledHelperReadbackResult read_installed_helper(const char* root_key_or_null,
    const char* helper_path, int64_t slot_offset,
    InstalledHelperChunkConsumer consume, void* context, uint32_t timeout_ms = 300);

// Read-only classification of the existing completion facts. This never changes
// the deadline, worker lifetime, failure errno or the authoritative complete bit.
__attribute__((visibility("hidden"))) inline const char* installed_helper_readback_failure_stage(
    const InstalledHelperReadbackResult& out, bool digest_complete) {
    if (out.complete) return digest_complete ? "complete" : "digest-finalize";
    if (out.admission_error) return "worker-admission";
    if (out.system_error && !out.child_pid) return "worker-setup";
    if (out.watchdog_error || (out.root_checkpoint_received && out.child_pid && !out.watchdog_armed)) return "watchdog";
    if (out.mode_error) return "auth-mode";
    if (out.channel_error) return out.owner_mode ? "owner-channel" : "credential-channel";
    if (out.deployment.error || (out.namespace_requested && !out.namespace_resolved)) return "deployment-namespace";
    if (out.root.error) return "root-transition";
    if (out.child_exception) return "child-exception";
    if (!out.root_checkpoint_received) return "root-checkpoint";
    if (!out.root_verified) return "root-proof";
    if (out.sink_error) return "digest-stream";
    if (out.producer_error) {
        if (!out.header_received) return "source-open-stat";
        if (!out.source_eof) return "source-stream";
        return out.metadata_stable ? "source-path-recheck" : "source-metadata-recheck";
    }
    if (out.transport_error || out.child_io_error) return "frame-transport";
    if (out.credentials_managed && (out.close.error || !out.close.completed || !out.close.reply_observed)) return "session-close";
    if (out.sdk_error) return "root-transition";
    if (out.wait_error || !out.child_status_valid) return "child-wait";
    if (out.timed_out) return "deadline";
    if (out.cleanup_pending || out.cleanup_scheduled || out.cleanup_error ||
        out.termination_requested || out.termination_error) return "worker-cleanup";
    if (!out.header_received || !out.producer_end_received) return "source-framing";
    if (out.actual_size != out.source_bytes || out.actual_size != out.stream_bytes ||
        out.actual_size != out.received_bytes) return "stream-size";
    if (!out.metadata_stable || !out.path_stable || !out.source_eof) return "source-stability";
    if (!out.receipt_complete || !out.stream_eof) return "receipt";
    return "completion-check";
}

} // namespace maplesu
