# Native dependencies

The complete Manager build uses the native inputs retained in this repository. The same SDK archive, embedded helper and paired policy backend are used for local and CI builds. [Build](BUILD.md) lists the toolchains and commands; [Credits](../CREDITS.md) records component origins.

## Interfaces and ownership

| Component | Location | Responsibility |
| --- | --- | --- |
| Application client SDK | `client/`, `consumer/` | Binder sessions, streams, cancellation and exit status |
| Manager | `manager/` | Caller authorization, mode selection, installation and module orchestration |
| Ultra bridge | `runtime/pro/bridge/` | Native SDK calls, request context and structured results |
| Ultra runtime | `runtime/src/`, `runtime/pro/` | SU sessions, authorization transactions, environment and module lifecycle |
| Image and installation | `image-core/`, `installer-core/`, `patch-engine/` | Image containers, patch plans, write transactions and recovery |
| Normal backend | `third_party/kernelsu/` | KernelSU adaptation, userspace tools and kernel contract |

The application AAR and Ultra native module ABI have separate versioning. Native callbacks, symbols, descriptors and storage layouts retain their established ABI. MapleSU errors use `MAPLESU_*` identifiers; compatibility parsers preserve numeric results and success conditions.

## Retained build inputs

| Input | Location | Identity |
| --- | --- | --- |
| Offline patch core | `patch-engine/upstream/` | Fixed source manifests in `third_party/` |
| Runtime headers and archive | `runtime/upstream/` | Byte hashes in `release/native-inputs.json` |
| Ultra native SDK | `runtime/upstream-pro/` | `SDK-RELEASE.json`, `SDK-IDENTITY.cmake` |
| Embedded helper | `runtime/upstream-pro/helper/autorun_bootstrap` | Helper hash and build ID in the SDK release manifest |
| Paired policy backend | `runtime/upstream-pro/backend/` | SDK release manifest and ELF build ID |
| Kernel images and symbols | `native-inputs/kernels/` | Provider contracts and retained-input hashes |
| Policy payloads, BTF and configuration | `native-inputs/policy/` | Bundle manifest and compatibility contracts |
| Manager identity claim payloads | `manager/src/main/assets/ultra-manager-claim/` | Source and payload manifest, retained-input sizes and hashes |
| SDK maintenance baseline and entry | `runtime/upstream-pro/maintenance/` | Archive member comparison and paired entry identity |
| Normal tools and providers | `third_party/`, `jailbreak-providers/` | Component source and build manifests |
| SELinux / libsepol | `.deps/selinux/` | `module-policy/upstream/selinux-source.json` |

`release/native-inputs.json` records the files, sizes and SHA-256 values required by the complete source export. The export checker verifies those bytes in the actual archive, including inputs that have binary extensions. Build caches, previous backend generations and research records are excluded.

Verify the paired SDK before building:

```shell
python scripts/sync-pro-sdk-identity.py --check
python tools/prepare_native_inputs.py --check
python tools/build_module_policy.py --prepare-only --offline
```

The SDK identity check validates the archive, embedded helper, paired backend, current source hashes and all six policy profiles together. Manager BuildConfig and native CMake read the same identity. AAR builds are independent of this native ABI.

## Manager identity claim payloads

`scripts/build-pro-manager-claim.py` builds the six family-specific `claim.so` files from `runtime/pro/identity/kernel/` and the freestanding `bootstrap.bin` and `materializer.bin`. The adjacent `manifest.json` records the thirteen source hashes, all eight binary sizes and SHA-256 values, imports, entry offsets and relocation counts. `release/native-inputs.json` pins these eight binaries and the manifest together for the source-export checks. Source hashes cover the exact input bytes; preserve their recorded line endings when reproducing a build.

Publish a complete set with `python scripts/build-pro-manager-claim.py --out work/manager-claim-candidate --assets manager/src/main/assets/ultra-manager-claim`. The build requires the retained DDK images and the prepared 6.18 kernel inputs named in the script; inspect its build report before refreshing the nine retained-input records. The certificate policy in `manager_claim_entry.c` pins a 772-byte Manager certificate with SHA-256 `30bafb57e3604afe93da13a1285516e043feb82b56a861a157b53ccaa27c3506` and generation 2 or later. Compare that pin with `apksigner verify --print-certs` on the signed release APK whenever updating these assets.

## Update the policy backend

1. Keep the committed SDK as the baseline. Generate a candidate bundle in a new local output directory using `scripts/build-ultra-policy-bundles.py` and the six directories in `native-inputs/policy/policy-bundles.json`.
2. Configure `runtime/CMakeLists.txt` with NDK 26.1.10909125, ARM64, API 26 and `RelWithDebInfo`. Set `MAPLESU_PRO_SDK_DIRECTORY` to the retained SDK and `MAPLESU_POLICY_BUNDLE_SOURCE` to the candidate C++ bundle. Build `maplesu_policy_boot`.
3. Run `scripts/rebind-pro-policy-backend.py` with the compiled `--backend`, `--entry runtime/upstream-pro/maintenance/entry`, `--source runtime/upstream-pro`, `--ndk`, a new `--build-id` and a fresh `--out` directory under `work/`. The tool checks the existing entry before pairing the candidate backend.
4. Update delivery source hashes, bundle identity and compatibility contracts together. Generate and check the candidate identity with `scripts/sync-pro-sdk-identity.py --manifest ... --output ...`.
5. Verify SU, authorization and revocation, boot restoration, module installation/update/disable/uninstall, WebUI, mode transitions and recovery. Preserve module ABI and compare the candidate archive members against the retained baseline.
6. Import the matching SDK, helper, backend and maintenance entry as one change. Refresh the listed input hashes with `python tools/prepare_native_inputs.py --refresh-manifest`, then run the complete build and source-export checks in [Releasing](RELEASING.md). Add new build input paths to `release/native-inputs.json` in the same change.

Keep original component inputs, MapleSU source changes and generated outputs traceable through their manifests. Independently distributed Zygisk modules follow the [module repository](MODULE_REPOSITORY.md) process.
