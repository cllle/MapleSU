# scripts/

The build, identity and regression toolchain for the **Ultra backend**: the Ultra SDK adapters, the policy backend and the native runtime. Repository-wide entry points (host checks, input preparation, release audit) live in [`tools/`](../tools/README.md).

## Read this before moving or editing a file here

Some files in this directory are part of the SDK's provenance chain. `runtime/maplesu-sdk/SDK-RELEASE.json` and the delivery manifests under `work/` record their **paths and SHA-256 digests**, and `scripts/sync-ultra-sdk-identity.py --check` and the release gates rely on those records. Renaming, moving or reformatting such a file, or editing it without re-pinning, breaks the chain without any compile error. `python tools/check_architecture.py` (rule `pinned-scripts`) fails when a recorded script is missing or no longer matches its record. If a change is intended, regenerate the SDK record in the same commit and say so in the pull request.

Files that are not named in those records, and everything in [`tests/`](tests/), can be changed freely.

## Layout

| Files | Purpose |
| --- | --- |
| `build-ultra-*.py`, `build-ultra-policy-*.py`, `build-ultra-vr-filter.py`, `build-*.ps1` | Build SDK candidates, policy bundles, layout and compatibility contracts, the module filter and the native runtime |
| `policy_*.py`, `policy-kernel-clang.py`, `ar_archive.py`, `verify_sdk_member_diff.py` | Shared helpers: backend identity and delivery checks, BTF ABI, payload inspection, `ar` member parsing and the SDK member-diff gate |
| `sync-ultra-sdk-identity.py`, `rebind-ultra-policy-backend.py`, `install-ultra-*.py` | Generate and check the build identity, pair a rebuilt policy backend with its helper, install verified SDK extensions |
| `repair-ultra-*.py`, `stamp-ultra-*.py`, `import-ultra-sdk-505.ps1` | One-time SDK member repairs, version stamps and the 5.0.5 import. They remain as implementation records; reproduce an older SDK generation with their explicit version and digest preconditions |
| `check-*.ps1`, `check-*.sh`, `verify-*.py`, `verify-*.sh` | Gates for a built Manager, packaged native pages, vivo artifacts and Normal-module parity |
| [`tests/`](tests/) | Host regression tests for the Ultra backend and the Manager's restore wiring |

## Running the regression tests

Run them from the repository root, so the relative defaults resolve:

```shell
python -m pip install -r requirements-native.txt
python scripts/tests/test-ultra-policy-entry.py --help
```

Each script documents its own inputs in `--help`. Tools are found through the environment variables in the [build guide](../docs/BUILD.md#locating-tools): `ANDROID_HOME`, `JAVA_HOME`, `MAPLESU_PROVIDER_NDK` and, for the few device checks, `ANDROID_SERIAL`. Tests that compile native code expect the NDK, Docker or the retained SDK inputs described in [Development](../docs/DEVELOPMENT.md) and [Native dependencies](../docs/NATIVE_DEPENDENCIES.md). The `.cjs` tests run with Node.js and the `.sh` test runs inside an isolated container. Tests that need an input that is not present exit with an error that names it. Every test runs on the host except `test-parasite-loader.py` and `test-parasite-startup.py --device`, which drive a connected device through adb and need `ANDROID_SERIAL` or `--serial`.

Scripts locate the repository with `Path(__file__).resolve().parents[N]`. A test in `tests/` uses `parents[2]`, and it adds `scripts/` to `sys.path` before importing a helper from this directory.

## Verify a delivered Release APK

`verify-release-apk.py` compares the delivered APK with the completed Release output,
the SDK manifest recorded by that build, stripped native libraries, all four embedded
payloads, and the complete init_boot bridge bundle with its ten recorded inputs.
It invokes Android Build Tools to verify the package version and pinned signing
certificate. Use JDK 21 through `JAVA_HOME` or `PATH`.

```shell
python scripts/verify-release-apk.py --apk DELIVERY.apk --execution RELEASE-EXECUTION.json --sdk STAGED_SDK --manager-build MANAGER_BUILD --build-tools ANDROID_BUILD_TOOLS --output NEW_VERIFICATION_DIRECTORY
```

The execution receipt must contain `phase=release`, `status=FINISHED`, integer
`exitCode=0`, and the SDK/bridge input identities captured for that build. Paths
in the SDK manifest resolve against the repository; `--root` selects another
source checkout. The output directory must be new. Logs and
`APK-VERIFICATION.json` remain available on failure. Successful artifact
verification leaves functional, full release, and device acceptance pending.
Hashes and sizes use the same open file. Observed APK identity changes around
version/signature tools, ZIP reads, or final success fail verification; these
checks are stage samples, not an atomic snapshot across external tools.

Run the ordinary ZIP/mock regressions with
`python scripts/tests/test-release-apk.py`; they do not build products or access a device.
