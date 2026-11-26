# Session retirement JVM regressions

`WebUiSessionLifecycleJvmTest.kt` contains the five actual `WebUIState` /
`WebViewInterface` / `ModuleWebUiSessions` composition cases. It lives in test
resources intentionally: its Android, Compose, libsu, mode and IO stand-ins must
not shadow the platform or product classes in ordinary Gradle tests. The IO
stand-in checks bridge destruction and page gating, not actual file IO. Real
`KsuIO` has its own `WebUiIoLifecycleTest` suite.

`UltraModuleSessionNotificationTest.kt` in `src/test/java` contains eight ordinary
JUnit 4 cases driving the actual install/uninstall transactions and process-local
session provider with injected host receipts and catalog. Its no-op journal uses
the production durable-journal default; it does not replace transaction logic.

Run only after the selected production composition is available. Supply a fresh
output directory, JDK 21, Kotlin compiler 2.4.20 classpath, and an explicit runtime
classpath containing Kotlin stdlib, annotations, coroutines-core JVM 1.11.0,
JUnit 4.13.2, Hamcrest 1.3, and org.json 20240303. The production dependency
classpath also needs kotlinx-serialization core/JSON JVM 1.11.0 for `StoredInputs`.
The runner recompiles the selected receipt, authorization, stored inputs and five
compatibility dependencies together with the transactions. The remaining
production dependency classpath must match the selected composition. Runtime
client declarations on that classpath are not invoked: the host and device facts
are injected. No Gradle, native host, real installation,
root, kernel or device operation is invoked by this runner.

```powershell
& ./manager/src/test/resources/webui-session-jvm/run.ps1 `
  -Java $Java21 `
  -CompilerClassPath $KotlinCompilerClassPath `
  -DependencyClassPath $JvmTestDependencyClassPath `
  -ProductionClassPath $SelectedProductionDependencyClassPath `
  -ProductionSourceRoot ./manager/src/main/java `
  -OutputDirectory ./.tmp/p6-session-jvm-selected-input
```

`ProductionSourceRoot` may point at the single owner's selected postimage tree.
The runner records source/fixture/test SHA-256 inputs, Java argument files, logs,
and exit codes in the fresh output directory; it compiles both suites and runs
each suite once. It never writes production or candidate source.

These thirteen cases cover same-process native install/uninstall session retirement
and queued JavaScript exit. They do not close authenticated cross-process retirement, script-module
mutation sources, Android/device acceptance, or the whole P6 gate.
