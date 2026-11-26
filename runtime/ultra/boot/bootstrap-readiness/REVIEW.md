# Candidate v2: report bootstrap result through the installer

The v1 candidate remains frozen in the parent directory. All v2 source, IR, objects, reports and tests are isolated here. No canonical files or device state are changed.

## Completion evidence used

`runtime/ultra/boot/bootstrap_finish.c` is the authoritative writer reviewed for this gate:

1. `.boot_session.completed`: exactly 37 bytes, current boot UUID followed by LF; regular file, uid 0, one hard link.
2. `.safe_flag`: exactly one byte, ASCII `2`; regular file, uid 0, one hard link.

The writer fsyncs the completion receipt before updating/fsyncing safe to `2`. The candidate reads `/proc/sys/kernel/random/boot_id`, validates its UUID/LF format and requires an exact match to the receipt. It rejects symlinks via O_NOFOLLOW and validates the opened inode. It does not depend on `.work`, whose write can fail after the committed success, and does not read `install.mode`: a mode-display mismatch cannot bypass this gate.

## Flow

HotLoad creates one dedicated CLOEXEC pipe before the detached fork. The worker closes its read end; its exec child loses the write end on successful exec. The original installer child closes its writer and waits for a typed fixed-size result. EOF, a short frame, invalid magic, worker failure, poll error or 120-second timeout returns `-21002` through the existing SDK error pipe.

The worker first requires successful wait of the expected helper PID with status 0, then validates the two completion files. It sends success only after both checks and before the original zygote restart branch. Failure sends an error and exits before restart. A failed send also blocks restart. The final SDK caller now retries an interrupted wait of its installer child and rejects wait failure, wrong PID and nonzero child status instead of accepting the received value blindly.

Boot mode retains its asynchronous reboot path. No SDK enum/header is edited; `-21002` remains the integrating owner's proposed bootstrap readiness error.

## ensure-05 fixture

The reported device run logged `set current root failed: ERR_MODULE_PARAM`, left safe at `1`, and had no completed receipt. Even if the helper's process exits 0, this exact state makes `maplesu_installer_result_complete()` send `-21002` and return false. The patched IR branches on that return value to `_exit(127)`, before either zygote restart call. The added host case recreates safe=`1`, missing completed and successful process exit and verifies the real candidate C functions reject it over real fork/pipe.

The mode text printed by that run does not affect v2's validation. Whether the helper misread or misreported `install.mode` is a separate upstream issue; this candidate does not rewrite that state or infer success from it.

## Outputs and checks

- `maplesu_installer.source.diff`: full minimal readable-source change against canonical recovered source.
- `maplesu_installer.v1-to-v2.diff`: only the result-channel changes on top of v1.
- `m053.patched.ll`: compilable IR with the result channel and final wait checks.
- `maplesu_installer.o`: standalone AArch64 relocatable candidate, existing SDK strong exports preserved.
- `build.json`, `commands.json`: exact hashes and build records.
- `result-test.json`, `result-test.log`: 23 passing fault-injection cases, actual candidate C functions with real fork/pipe; poll and pipe creation error outcomes are injected.

## Scope and limits

A valid bootstrap completion is stronger than process exit 0, but still does not substitute for a fresh third-party SU authorization test. The current-boot receipt may describe an earlier successful bootstrap in the same boot; the launch path resets safe first, and this candidate requires safe `2` after helper exit rather than fabricating that value.

Success is sent before zygote restart as requested. A later restart error is not part of that already-sent bootstrap result. Parent timeout fails the call; if the worker later finishes, its write encounters the closed pipe and it does not advance to restart. This does not terminate a stuck helper or undo partial bootstrap initialization. Integration/device acceptance remains pending.
