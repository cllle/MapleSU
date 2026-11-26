# `helper/autorun_bootstrap` — source candidate

The current sources build the native helper candidate against the real SDK
archive. The r7 candidate restores the original vector / `PatchDoExecve` /
64-bit control ABI, native SDK initialization, module worker and single-module
entry, boot session handling, and paired policy loader. Its status is
`source-candidate-unvalidated-device`; host and artifact checks do not establish
Android boot or kernel behavior.

The implementation and evidence are recorded in
[`HELPER-SOURCE-INTEGRATION.md`](../../../../../docs/dual-mode/impl/_work/2026-11-15-p1/HELPER-SOURCE-INTEGRATION.md).
The original gap assessment remains a historical record. Its “not implemented”
rows and the frozen manifest's `status=partial` / `acceptance.pending` wording
predate this integration; use the integration report for the actual tested
scope. The manifest stayed unchanged after r7 froze its source closure.

The r7 helper is 1,369,296 bytes, SHA-256
`a06a900dcd4370b00550197372a44355d939ff9e1f8c5312f6faa899eab43250`.
The actual helper, generated payload, and staged candidate have identical bytes.
The old reference at `runtime/maplesu-sdk/helper/autorun_bootstrap` is preserved
and is not a source build input. Its byte identity is not an acceptance target.

The native host fixture runs the actual module runtime, metadata parser, module
state implementation, ABI adapter, bootstrap orchestration and policy loader:

```powershell
py runtime/maplesu-sdk/sdk/tools/tests/helper_native/run.py --out .tmp/helper-native-tests
```

The recorded run passes 137 assertions (module runtime 37, original ABI 43,
bootstrap order 57) and four paired-loader cases. The 14 artifact audit checks
independently verify the final r7 helper and its 35 captured input hashes. See
`evidence/helper-native-source-r1` beside the integration report for commands,
source hashes and logs. The earlier 78 wasm checks targeted the replaced
experimental ABI and do not represent this implementation. The earlier 17 POSIX
adapter checks remain evidence of that adapter only.

`tools/build_helper.py` requires an explicit SDK archive and backend pairing:

```powershell
py runtime/maplesu-sdk/sdk/tools/build_helper.py `
  --sdk-archive PATH_TO_ARCHIVE `
  --backend-identity PATH_TO_PAIRING_DIRECTORY `
  --out PATH_TO_CANDIDATE --report PATH_TO_REPORT
```

The manifest lists 12 native translation units, including maintained backend,
identity, disable-gate and finish sources. The build compiles with exceptions
and static libc++, exports exactly `maplesu_helper_su_exec` and
`maplesu_helper_su_write`, strips the result, checks the paired backend, and
records compiler dependencies. Device acceptance remains a separate result.
