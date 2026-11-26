# manager/

The Android app (`:manager`): UI, mode detection and routing, authorization, installation, modules, themes and catalogs. Material and Miuix share one orchestration layer.

[Architecture](../docs/ARCHITECTURE.md#the-manager) has the full map: the six source roots, the two Kotlin namespaces, the `dev.maplesu.manager` packages and where new code goes. This page is only the way in.

## Layout

| Path | Content |
| --- | --- |
| `src/main/` | Kotlin and Java sources (`com.maplesu.core` shell, `dev.maplesu.manager` domain packages), native glue in `cpp/`, resources and assets |
| `src/ksu/java/` | The Normal backend: KernelSU JNI facade, root executor, module operations |
| `src/debug/` | Debug-build sources: hidden-backend diagnostics and the Binder-admission test brokers |
| `src/test/` | JVM unit tests |
| `src/androidTest/` | Instrumentation tests; they need a device and are run by a maintainer |
| `src/themeTest/` | The manifest that debug builds use for presentation tests, which must not bootstrap root |
| `proguard-rules.pro`, `lint-baseline.xml` | Shrinker rules and the accepted lint baseline |

The module also compiles four source roots from [`jailbreak-providers/`](../jailbreak-providers/README.md), so provider work stays reviewable in one place.

## Build and test

```shell
./gradlew :manager:testDebugUnitTest :manager:lintDebug
./gradlew :manager:assembleDebug
```

A complete Manager build needs the pinned native inputs described in the [build guide](../docs/BUILD.md#build-the-complete-manager) (on Windows PowerShell use `./gradlew.bat`). Run `python tools/check_architecture.py` before you push; it fails when a file loads a native library outside the documented sites or when `client/` and `manager/` drift apart.

## Rules that are easy to break

- Add a native library loader only at an existing site (`native-boundary`); Ultra reaches its SDK only through `runtime/ultra/bridge/NativeBridge.java`.
- Keep `src/main/aidl` byte-identical to [`client/src/main/aidl`](../client/) (`aidl-parity`).
- Add Material and Miuix variants together, with English default strings and Simplified Chinese strings.
- Files that originate in 7kimisu keep their notices; `python tools/check_architecture.py --report` shows which files are unchanged, modified or MapleSU's own.
