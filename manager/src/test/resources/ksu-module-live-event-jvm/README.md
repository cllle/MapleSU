# Normal module live-directory events

`run.ps1` compiles current production `KsuModuleOps`, `KsuCommandBackend`,
`KsuRootShell`, support types and `ModuleWebUiSessions` with the live-event tests
and existing installation tests. Shell transport/path probes are injected. An
unused candidate-image producer fails immediately if that unrelated path is
accidentally invoked.

Supply `-Java`, `-CompilerClassPath`, `-DependencyClassPath` (stdlib,
serialization core/json, JUnit, Hamcrest) and a fresh `-OutputDirectory`. The
runner records source hashes, compiler/test commands and input stability.

The new cases cover targeted synchronous retirement, nonzero completion after a
real swap event, pending installs, malformed events, stdout isolation,
stream/receipt deduplication, receipt-only events and partial-output disconnects.
Native rename/rollback production and actual Android execution remain separate.
