# Script installation outcome regression

`run.ps1` extracts the current LiteScript branch of `flashModule`, its `FlashResult`
declaration, the actual `InstallResult` declaration and `UltraOutcome` enum. The
generated Kotlin compiles and runs four JUnit cases with an explicit backend
fixture. It checks unknown commit, success awaiting reboot, failure and exit 75
without an unknown flag; original exit/reboot values and both raw callbacks are
preserved and the backend is invoked exactly once.

Pass `-Java`, `-CompilerClassPath`, `-DependencyClassPath` (Kotlin stdlib, JUnit and
Hamcrest) and a fresh `-OutputDirectory`. Compiler/runtime dependencies may reuse
the existing `JAVA-CONFIG.json` from the P6 root-display validation. `RESULT.json`
records all source hashes, generated files, commands and input stability.

This is a JVM projection test. Archive extraction, actual installer transactions,
batch/UI lifecycle, JNI, root execution, APK and device behavior remain separate.
