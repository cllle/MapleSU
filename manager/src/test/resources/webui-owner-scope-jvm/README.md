# Observed owner scope JVM regressions

This runner compiles the current `ModuleWebUiSessions`, `WebUIState`,
`WebViewInterface`, resource cleanup, callback compatibility and WebUI plan.
At each run it also extracts the current Probe snapshot type,
`publishSnapshot` and `webUiOwnerScope`, the launcher's entire `launch` method,
and the Activity revision/owner binding block. The extracted bodies are executed,
not checked as strings. The test resources keep Android, Compose, libsu,
credential/boot scope and file IO fixtures out of ordinary Gradle tests.

The 15 cases exercise already observed authenticated identity changes across
full UID, epoch and host instance, same-owner and missing-identity observations,
stale activations and KSU, late binding, pending old UI cleanup, subscription
unregistration, commit/identity races, callback failure, captured Intent transport,
Activity recreation, partial extras, legacy callers, stale module revisions and
real activity-start failure semantics. Retained IO here checks page gating and
bridge destruction; actual file IO has its separate lifecycle suite.

```powershell
& ./manager/src/test/resources/webui-owner-scope-jvm/run.ps1 `
  -Java $Java21 `
  -CompilerClassPath $KotlinCompilerClassPath `
  -DependencyClassPath $JvmTestDependencyClassPath `
  -ProductionSourceRoot ./manager/src/main/java `
  -OutputDirectory ./.tmp/p6-owner-scope-current
```

Use a fresh output directory, JDK 21, Kotlin compiler 2.4.20, Kotlin stdlib,
annotations, coroutines-core JVM 1.11.0, org.json, JUnit 4 and Hamcrest.
The runner records all selected inputs, generated body slices, arguments, logs
and exit codes. It performs no native probe, root command, kernel change,
Gradle build, APK creation or device action. It verifies only the consumer of
an identity tuple already observed by the existing Probe. Cross-process
notification latency and Android/device acceptance remain separate evidence.
