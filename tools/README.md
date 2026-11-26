# tools/

Repository-wide entry points: host checks, native-input preparation, builders for the standalone native tools, generators and the release audit. Ultra-backend and SDK-identity tooling lives in [`scripts/`](../scripts/README.md).

Run everything from the repository root. The scripts need Python 3.12 or later; `requirements-native.txt` covers the ELF and emulation libraries that some of them import.

## Day-to-day

| Command | What it does |
| --- | --- |
| `python tools/check_host.py` | Compiles and runs the synthetic Java suites of `image-core` and `installer-core` with a bare JDK |
| `python tools/check_architecture.py` | Checks the module, native-boundary, provenance, structure and documentation rules in [Architecture](../docs/ARCHITECTURE.md); `--report` prints the origin of the Manager's sources |
| `python -m unittest discover -s tools/tests -p "test_*.py"` | Unit tests of the tools themselves |
| `python tools/tests/check_documentation_links.py` | Checks local Markdown link targets in the READMEs and `docs/`; never fetches a remote link |
| `python tools/release_audit.py --strict` | Audits the Git candidate against [`release/source-policy.json`](../release/source-policy.json); never modifies or publishes files |

## Native inputs and builds

| File | Purpose |
| --- | --- |
| `prepare_native_inputs.py` | Fetch pinned public inputs with size and SHA-256 checks |
| `native_toolchain.py` | Find the SDK, NDK, CMake, JDK and adb from the environment ([variables](../docs/BUILD.md#locating-tools)); scripts and tests import it instead of hard-coding a path |
| `build_module_policy.py` | Build the standalone module-policy tool from the pinned SELinux and Magisk sources |
| `build_module_tools.py` | Build the module tool host from the pinned `ksu_props` library |
| `build_policy_hook_package.py`, `build_su_client.sh` | Package measured policy sidecars; build the L3 `su` JNI shim |

## Gates

| File | Purpose |
| --- | --- |
| `check_module_shape.py` | Emulate the Manager's module-shape gate for an Ultra native module ([guide](../docs/ULTRA_MODULES.md)) |
| `check_ksu_embedding.py` | Verify shipped Normal modules and Ultra companions against the canonical assets |
| `check_kmi_matrix.py` | Require the provider's device-class support matrix to agree with the code and the artifacts |
| `check_exploit_payloads.py`, `check_attestation_fields.py` | Report which kernel module each KMI carries; check that both sides of the attestation protocol agree on field names |
| `verify_p0p1_dex.py` | Check that built APKs carry the third-party `su` device tests, the authority classes and the L3 JNI entry points |
| `ghostlock_supported_devices.py` | Generate the packaged supported-device asset from the upstream table |

## Generators and analysis

| Directory | Purpose |
| --- | --- |
| `patch/` | Builds an unsigned, non-flashable offline boot candidate and verifies it independently; includes the Java kernel codec |
| `specgen/` | Ultra kernel spec generator ([README](specgen/README.md)) |
| `ir-gen/` | Per-release IR specs and release assets from a kernel image and its symbol dump, with the rule engine and AArch64 encoder |
| `avb/` | `avb_scan.py` detects and classifies the AVB surface of a boot image; it never signs or writes |
| `ultra-vr-filter/` | Freestanding C and assembly sources of the Ultra module filter, built by `scripts/build-ultra-vr-filter.py` |
| `brand/` | Renders the README artwork and the User Advisor Program wall; see [Brand assets](../docs/assets/README.md) |

## Device checks

These talk to a connected device. Run them only on a device you own, and read each header first: `device_acceptance.sh` and `device_hook_evidence.sh` make a provider attempt and tolerate a device that reboots during it, while `verify_ultra_device.sh` and `collect-ultra-module-evidence.ps1` only read.

| File | Purpose |
| --- | --- |
| `device_acceptance.sh`, `device_hook_evidence.sh`, `device_l3_acceptance.sh` | Acceptance passes and evidence collection around a provider attempt, and the L3 (ABI v2) acceptance driver |
| `verify_ultra_device.sh` | Read-only checks of the Ultra build's logs and heartbeats |
| `collect-ultra-module-evidence.ps1` | Read-only host-side collection of a Ultra module fault |

## Tests

`tests/` holds the unit tests (`test_*.py`) and the host checks that write a JSON report (`check_*.py`, `verify_plan_replay.py`). The reports land under `.analysis/`, which Git ignores.
