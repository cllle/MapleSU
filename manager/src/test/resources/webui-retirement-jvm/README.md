# Page retirement regressions

`run.ps1` compiles current production State, JS, Chrome, download, blob and Java asset entrypoints, then runs only the seven retirement regressions. Android, libsu shell/file and KsuIO are isolated JVM fixtures. Actual KsuIO has its separate lifecycle runner. MediaStore publication, authenticated root scope and device behavior remain outside this runner.

Pass Java 21, the Kotlin compiler classpath, and a fresh output directory. `DependencyClassPath` includes JUnit, coroutines, JSON, Kotlin serialization, AndroidX annotation/core/core-ktx/webkit, JSpecify annotations, the Android platform JAR and generated `R` classes. `ProductionClassPath` supplies the matching Manager dependency classes. The runner records exact source hashes, compiler/Javac/JUnit arguments and exit codes, and verifies that inputs did not change during execution.
