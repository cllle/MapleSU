The runner compiles production `helper_boot.cpp`, `helper_session.cpp`,
`helper_text.cpp`, `maplesu_storage_worker_private.h` and `bootstrap_finish.c`.
It uses real Linux file operations, flock, fork, exec and wait inside the pinned
host container, followed by strict Android NDK compilation of both changed TUs.

Kernel authentication/storage operations and core/native initialization are
explicit boundary seams. These tests establish helper ordering, per-process
session ownership, checked cleanup, original argv/env handoff and receipt failure
semantics. They do not establish kernel factory, module effects or device boot.

Run from the repository root:

```powershell
py -3 runtime/maplesu-sdk/sdk/tools/tests/p6_boot/run.py --out .tmp/p6-boot
```

For a focused candidate inside the repository, add `--boot-source PATH`.
The receipt-read fixture has 75 checks. The real boot/worker fixture has 34
assertions, including a real FIFO state file and a CLOSE error after the inherited
lock descriptor has already been consumed. Timeouts, partial runs, compilation
failures and changed source inputs leave `passed` false.

`run_single_close.py --out PATH` runs only the single-module helper regression.
It compiles the complete production `bootstrap_runtime.cpp` and actual
`WorkerScope`, injecting kernel/storage/module callback boundaries. Seven causal
groups cover callback/loader/root failure, skipped work, Legacy, and checked close
without a second destructor close. The changed TU is also compiled for Android
API21. This entry does not replay the receipt/boot matrix or qualify the native
early factory, formal helper or device.
