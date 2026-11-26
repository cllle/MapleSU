# Root tree session lifecycle regression

`run.py` recompiles the current complete `RootModuleTree.kt` and `ModuleWebUiSessions.kt` with the two fixture files. It starts one temporary, network-disabled container from the already available pinned image and runs the generated production shell unchanged. The container owns a disposable tmpfs and is removed on completion.

Six cases cover live activation, rollback and removal; unrelated page independence; pending preparation, marker and snapshot isolation; a real rename followed by injected sync failure; pre-rename failure; and absent/nested removal. The last case invokes the private production command wrapper to exercise its actual `remove_tree` helper directly. No marker is synthesized by the fixture.

The fixture `module-sync` tool uses Linux `sync -f`; its deliberate failure is explicit. These tests establish mutation/notification causality and original error preservation, not Android transport, boot, cross-process authentication, target durability or device behavior.

Run from the repository with an installed JDK 21 and the Kotlin compiler/stdlib classpaths:

```powershell
py -3.14 manager/src/test/resources/root-tree-session-shell/run.py --java JAVA_EXE --compiler-classpath COMPILER_CP --stdlib STDLIB_JAR --output FRESH_OUTPUT_DIRECTORY
```
