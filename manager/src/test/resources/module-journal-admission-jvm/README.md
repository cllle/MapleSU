# Prepared operation journal regression

Run `run.ps1` with JDK 21, Kotlin compiler 2.4.20, a JVM dependency classpath
(Kotlin stdlib, coroutines, serialization, JUnit 4, Hamcrest, actual org.json),
and the selected production dependency classpath. Use a fresh output directory.
The eleven complete production Kotlin sources are recompiled for every run.

Six cases check both install operations, staged cleanup failure, the distinction
between preparation and post-commit logging failure, toggle admission, and an
unchanged toggle and cancellation. Host calls and filesystem journal faults are injected;
the production transaction and outcome code are not replaced.
