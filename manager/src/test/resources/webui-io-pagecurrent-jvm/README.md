# Real IO page-current regressions

This runner compiles the current six production IO files and resource cleanup,
then selects only the five new `retiredPage...` methods from
`WebUiIoLifecycleTest`. Existing IO tests and the twelve session composition
cases are not rerun. Input SHA-256, command arguments, logs and stability checks
are written into a fresh output directory.

Supply JDK 21, Kotlin compiler 2.4.20, stdlib, annotations, coroutines JVM 1.11.0,
JUnit 4.13.2, Hamcrest 1.3, org.json 20240303, and the original local libsu
core/io/nio jars. `AndroidUtil.kt` retains the previous IO harness's JVM Base64
and Log boundary. `JavascriptInterface.kt` is an annotation declaration only.
The production `KsuIO`, facades, stream registration and resource close paths
remain unchanged. Java byte-array streams are real resources; mock shells
subclass the actual libsu `Shell`, declare ordinary status and reject commands.
The default root-shell entry throws immediately, and the selector verifies its
call count remains zero.

These tests cover retirement before explicit destroy, independent pages, input
and output factories returning after retirement, and injected shell acquisition.
They do not establish real root file IO, authenticated cross-process revocation,
APK, Android/device behavior, or complete P6 acceptance.
