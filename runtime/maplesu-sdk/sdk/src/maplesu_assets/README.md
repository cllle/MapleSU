# `sdk/src/maplesu_assets` — the four Ultra program installers, as source

This directory documents the four installers that the Ultra kernel-module kit links
into `libkernel_module_kit_static.a`. The programs they install (`su`, `resetprop`,
`webui_loader`, `autorun_bootstrap`) are **assets** (`sdk/assets/*.bin`, hashes in
`sdk/assets/ASSETS.json`).

**Build inputs vs readable transcription.** The archive members themselves are
built from the recovered build inputs, not from the `.cpp` files here:

* `installer_ir/` holds the idx 48 build inputs (vendor-era installer unit with the
  release-time publish call + the executable publisher);
* `maplesu_unsafe_resetprop_install_patched.ll` and
  `maplesu_unsafe_webui_loader_install_patched.ll` are the idx 49 / 50 build inputs;
* `maplesu_autorun_bootstrap_install_patched.ll` is the idx 52 build input.

Their manifests declare those files as `sources` and mark the members `complete`.
The `.cpp` files here are a first-party **readable transcription** of the same
behaviour, kept for review and gap records; they are not compiled into the archive
and are not claimed to reproduce the shipped machine code byte-for-byte. See
`installer_ir/README.md` and `notes/work/AGENT107/STATUS.md` for the split and the
measured byte identity of each member.

| archive member (0-based ar idx) | readable transcription | payload asset |
|---|---|---|
| `maplesu_unsafe_su_install.o` (48) | `maplesu_unsafe_su_install.cpp` | `assets/su.bin` |
| `maplesu_unsafe_resetprop_install.o` (49) | `maplesu_unsafe_resetprop_install.cpp` | `assets/resetprop_file.bin` |
| `maplesu_unsafe_webui_loader_install.o` (50) | `maplesu_unsafe_webui_loader_install.cpp` | `assets/webui_loader.bin` |
| `maplesu_autorun_bootstrap_install.o` (52) | `maplesu_autorun_bootstrap_install.cpp` | `assets/autorun_bootstrap.bin` |

Shared headers (not translation units):

| header | role |
|---|---|
| `maplesu_install_core.h` | freestanding install primitives: data-dir/path derivation, equal-length token replacement, `install_blob()` step, error codes. No `#include`, so it also builds for `wasm32` (host test). |
| `maplesu_install_env.h` | kit bindings (`module_root::set_current_root`, `__get_current_module_root_key_*`) + `std::string` path helpers |
| `maplesu_install_platform.h` | the I/O seam: `maplesu_publish_executable` declaration, `ofstream`/`chmod`/`setxattr`/`renameat` implementation, hooks table for `install_blob` |
| `maplesu_payload_assets.h` | binds the generated payload arrays (or 1-byte stubs for host tests) and pins the expected sizes |
| `maplesu_module_webui_hooks.h` | the four weak module-hook symbols every member carries |

## Build the readable transcription (for host review, not the archive)

The commands below compile the `.cpp` transcriptions so their behaviour can be
exercised on the host. They are not the archive build: the shipped members 48 / 49 /
50 are compiled from `installer_ir/` inputs and the `*_patched.ll` files declared in
their manifests (see `installer_ir/README.md`).

Generated payload headers first — they are **build output**, never committed:

```bash
py runtime/maplesu-sdk/sdk/tools/gen_payload_headers.py
# -> sdk/build/assets-gen/{su_data,resetprop_file_data,webui_loader_data,
#    autorun_bootstrap_file_data}.h + payload_assets.generated.h
```

Then compile. Flags mirror the shipped members (`-Oz`, hidden visibility, function/
data sections, exceptions on — every shipped member imports `__cxa_throw`,
`_Unwind_Resume` and carries `__gxx_personality_v0`):

```bash
NDK=D:/Maple_Env/Android/Sdk/ndk/26.1.10909125/toolchains/llvm/prebuilt/windows-x86_64/bin
for f in maplesu_unsafe_su_install maplesu_unsafe_resetprop_install \
         maplesu_unsafe_webui_loader_install maplesu_autorun_bootstrap_install; do
  "$NDK/clang++.exe" \
    --target=aarch64-linux-android21 -Oz -fPIC \
    -ffunction-sections -fdata-sections \
    -fvisibility=hidden -fvisibility-inlines-hidden \
    -DNDEBUG -std=c++20 \
    -DMAPLESU_PAYLOAD_ASSETS=1 \
    -Isdk/src/maplesu_assets -Isdk/build/assets-gen \
    -Wall -Wextra \
    -c sdk/src/maplesu_assets/$f.cpp -o sdk/build/programs/$f.o
done
```

`-DMAPLESU_PAYLOAD_ASSETS=1` is mandatory for a shippable object. Without it the
TU compiles against 1-byte stub arrays (for host tests only) and
`maplesu::sdk::kPayloadsReal` is `false`; every installer still compiles but the
objects must not be shipped.

## What "done" means here (acceptance, reproducible)

```bash
py runtime/maplesu-sdk/sdk/tools/gen_payload_headers.py --check   # headers match assets
py runtime/maplesu-sdk/sdk/tests/verify_assets_from_archive.py    # assets == frozen archive slices
py runtime/maplesu-sdk/sdk/tests/tu_set_diff.py                   # T/U sets vs frozen members
py runtime/maplesu-sdk/sdk/tests/run_core_test.py                 # host unit test (wasm32 + Node)
```

`tu_set_diff.py` compares external definitions (`T`) and imports (`U`) against the
members inside the **frozen** archive. The `T` sets match in both directions for
all four installers. The `U` sets do not, and are not claimed to: the remaining
differences are libc++ inlining choices (`std::filesystem::path`,
`std::string::__grow_by/__init/insert`, `bad_array_new_length` ctor/dtor), the
vendor's TU-local `snprintf` shadow, and the documented 5.2.0 additions in
`maplesu_autorun_bootstrap_install.cpp` (`get_sdk_version`, stdio session/log
housekeeping) that the vendor IR predates.

## Fidelity notes worth knowing before editing

* **Paths are odd on purpose.** The data dir is `key.substr(0,16) + key[0]`
  (17 chars) under `/data/`, e.g. `/data/0123456789abcdef0/su`. The trailing
  repeat of the first key character is vendor obfuscation, reproduced verbatim.
* **Tokens are 4096 bytes, replaced in place.** 128 hex chars + 3968 NULs. The
  search loop is `for (i = 0; i <= buf_size - token_size; ++i)`, i.e.
  `741912 - 4095 = 737817` iterations for `su` — the `<=` is intentional and
  asserted by the host test. The replacement only writes `min(4095, len+1)` bytes,
  so the token's tail survives; do not "fix" it to zero the whole window.
* **`resetprop` has no token.** That member carries no `static_inline_*_rootkey`;
  its payload is written unchanged.
* **`webui_loader` chmods twice** and its path goes through `"modules"`; the
  directory itself is created by the kit, not by this installer.
* **`autorun_bootstrap` uses the `kernel` SELinux context**, not `system_file`,
  and installs to `/data/<dir>/init`.
* **Sizes come from `sizeof()`**, never from a literal: the 5.2.0 `autorun`
  payload is 1,348,016 B while the vendor-IR build was 1,163,328 B.
* These sources build objects for review; they are not linked or run here (no device,
  no parent `build.py` change — that file belongs to ARCH). The archive members are
  built by `build.py` from the recovered inputs listed in `installer_ir/README.md`.
