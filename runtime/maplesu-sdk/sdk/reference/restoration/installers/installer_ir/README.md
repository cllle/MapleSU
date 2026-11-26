# `sdk/src/skroot/installer_ir` — recovered build inputs for the installer members

This directory holds the **actual build inputs** of installer members whose shipped
objects carry the release-time executable-publishing path:

| member (0-based ar idx) | build input |
|---|---|
| `skroot_unsafe_su_install.o` (48) | `skroot_unsafe_su_install_patched.ll` + `executable_publish.cpp` + `executable_publish.h` |
| `skroot_unsafe_resetprop_install.o` (49) | `../skroot_unsafe_resetprop_install_patched.ll` |
| `skroot_unsafe_webui_loader_install.o` (50) | `../skroot_unsafe_webui_loader_install_patched.ll` |

The readable C++ files next to this directory (`../skroot_unsafe_*.cpp`,
`../skroot_executable_publish.cpp`) are **documentation of the behaviour, not build
inputs**: they do not reproduce the shipped machine code (the release-time
publishing/logging surface was emitted from the vendor-era build input), so they are
excluded from the archive build. The manifests and
`notes/work/AGENT107/STATUS.md` state the same split.

## What the inputs are

* `skroot_unsafe_su_install_patched.ll` — vendor-era installer translation unit with
  its old file-writing stream replaced by the release-time executable-publishing
  call, and the uid-isolation owner-only mode (0700) applied to both publish sites.
  The frozen su-uid-isolation member is this unit linked with the publisher unit.
* `skroot_executable_publish.{cpp,h}` — the publisher implementation that stages a
  program under an exclusive temporary name, labels the new inode, then renames it
  into place; the published mode is owner-only (0700).
* `../skroot_unsafe_resetprop_install_patched.ll`,
  `../skroot_unsafe_webui_loader_install_patched.ll` — vendor-era installer
  translation units with the same old-stream → publishing-call replacement.

The embedded program images stay in the `.data` payload arrays (`global`, not
`constant`), matching the frozen members' mutable payload sections.

## Provenance (checked-in bytes)

| input | provenance | sha256 |
|---|---|---|
| idx 48 installer | vendor installer member bytes `work/panic-fix-20261105/sdk-510/m048.original.bc` processed by `scripts/repair-pro-environment-upgrade.py` (`patch_stream`, idx 48 spec); the recorded result is `work/upgrade-validation-20261106/err-module-open-file-20261107/candidate/m048.patched.ll`, then the two publish modes are set to 0700 | `36ceb53945071677f94d0759745410d8f32190b05e70963663edcca24d136929` |
| idx 48 publisher | `work/ultra-policy-status-events-20261108/delivery/before/runtime/pro/upgrade/executable_publish.cpp` (the build-source digest recorded in the same `candidate/build.json`), published mode set to 0700 | `e6e89fdf642b1897033fb6704b0ce74626ce56623681ce9ddac0e6045a1bcc4b` |
| idx 48 publisher header | as above, unmodified | `3b2d18ef40f0980b8d424c449900a921d4cde05bf374740ddb40a9d905feaa97` |
| idx 49 | `runtime/pro/upgrade/sdk-m049.bc` → `patch_resetprop` (recorded: `notes/work/LARGE/repro2/m049.patched.ll`) | `45a9b199b29e427f10081a50b297082256a5dc1a0a6c0faee2c32d1a49812cbe` |
| idx 50 | `runtime/pro/upgrade/sdk-m050.bc` → `patch_stream` (recorded: `notes/work/LARGE/repro2/m050.patched.ll`) | `895e34e2a7bd13089b8613312620fa4b7d711bf33f5e26c618765c58b0a944a5` |

Recorded intermediate digests: vendor installer member bytes
`work/panic-fix-20261105/sdk-510/m048.original.bc` = `8ab3db4cbf680c93cfb85908e446a5287a3b97bc1fbeb9971d81497ce76d2811`;
patched result `candidate/m048.patched.ll` = `141309992c760718d214100db47f311a3e867f6ee0fcd7ed862d569a02f150ce`
(the checked-in file is that byte stream with only the publish mode constant changed
0777 → 0700, so the archive build needs no post-link byte patching).

## Re-deriving

The idx 48 installer input is `patch_stream` applied to the vendor installer
translation unit, followed by the two mode edits above. `patch_resetprop` /
`patch_stream` and their exact argument tuples live in
`scripts/repair-pro-environment-upgrade.py` (idx 48 spec:
`_ZN10skroot_envL13write_su_execEPKc`, 56, 73, 74, 3, 741912, 0, 70, 69, (72, 71),
`u:object_r:system_file:s0`; the script re-emits the vendor bitcode to textual IR
before patching, which is why the recorded file carries the vendor
`source_filename` and the vendor compiler identification).

The publisher unit must be compiled from a file named `executable_publish.cpp`: the
object carries that file symbol, and renaming the file changes the object bytes.
Reproduction commands and the byte-identity measurements are recorded in
`notes/work/AGENT107/STATUS.md`.
