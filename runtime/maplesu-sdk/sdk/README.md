# `runtime/maplesu-sdk/sdk` — the single build entry for the sample chain

This directory is the **only** place in the repository where the Ultra SDK is built
from source. It covers one deliberately small sample:

| ar # | member | role in the chain |
|---:|---|---|
| 23 | `module_base_kernel_request.o` | kernel request channel (`request_kernel_*`, `unsafe_request_*`, `maplesu_prepare_kernel_request`) |
| 24 | `module_base_kernel_mem.o` | kernel memory permissions + read/write path (`set_kernel_mem_protection`, `write_kernel_mem`, …) |
| 34 | `module_base_cpu_icache_sync.o` | instruction-cache synchronisation (`icache_invalidate_all_cpus`) |
| 29 | `module_base_kernel_func_hook.o` | hook publication (`install_kfunc_*`, `maplesu_publish_hook_checked`) |

The four members form one readable chain:

```
request (23) -> mem permission (24) -> write (24) -> icache (34) -> publish (29)
```

The final harness link also replaces executor member 25 from source: the memory
member depends on its legacy frame helpers. The partial-link sample remains the
four members above; final-link substitution and verification cover all five.

## One command

```bash
py runtime/maplesu-sdk/sdk/build.py --all
```

That single command:

1. **checks the toolchain** — clang 17.0.2 / llvm-ar 17.0.2 / ld.lld 17.0.2 out of
   NDK 26.1.10909125, by absolute path, never from `PATH`;
2. **validates `manifest.jsonl`** (schema, uniqueness, source inside this tree);
3. **compiles every TU** in the manifest with fixed flags and writes `build/obj/<member>.o`;
4. **partial-links** just those four objects with `ld.lld -r` and reports every
   symbol the sample still imports from outside itself;
5. **links the harness** `build/link/libmaplesu_sample_chain.so` with
   `-Wl,--no-undefined`;
6. **verifies** the substitution contract (see below) and writes
   `build/report/build-report.json`.

Typical run on this machine: **≈15–17 s** (four TUs dominate), and byte-identical
output across two clean builds of the same tree — measured in the all-placeholder
state, where all four `.o` files and the `.so` hashed equal across runs. See
`build/report/build-report.json` for the objects, the link command actually used
and the per-stage timings.

## Outputs

| path | what it is |
|---|---|
| `build/obj/<member>.o` | one object per manifest line |
| `build/link/sample-chain.partial.o` | `ld.lld -r` of the four objects only (no prebuilt bytes involved) |
| `build/link/kit-residual.a` | copy of the prebuilt archive **with the four sample members deleted** (254 of 258 members left) |
| `build/link/libmaplesu_sample_chain.so` | harness + four objects + residual archive, linked with `--no-undefined` |
| `build/report/build-report.json` | machine-readable record of every stage, command, symbol set and timing |
| `build/logs/*.log` | `$ cmd`, exit code, stdout, stderr for every invocation |
| `build/baseline/<member>.o` | the baseline member read back out of the archive, used for the contract check |

`build/` is covered by the repository-wide `**/build/` ignore rule; nothing under
it is tracked.

## Fixed dependencies

| dependency | pinned value | how it is enforced |
|---|---|---|
| NDK | `D:/Maple_Env/Android/Sdk/ndk/26.1.10909125` | `--check` fails with exit 2 if the directory name differs; override with `--ndk` or `MAPLESU_NDK` |
| clang / clang++ | `clang version 17.0.2` (r487747d, 10552028) | substring asserted on `--version` |
| llvm-ar, ld.lld | LLVM/LLD 17.0.2 | substring asserted on `--version` |
| target | `aarch64-linux-android21` | part of the fixed flag list |
| Python | 3.10+ (tested on 3.14.2) | stdlib only, no third-party imports |

Fixed code-generation flags (identical for all four TUs):

```
--target=aarch64-linux-android21 -Oz -fPIC -ffunction-sections -fdata-sections \
-fno-exceptions -fno-rtti -DNDEBUG -std=c++20
```

`-Wall -Wextra` are added for diagnostics only (they do not change codegen);
`--werror` promotes warnings to errors. A manifest line may carry `extra_flags`
when a driver-specific extra is genuinely required. The one that matters today is
`-fexceptions`: every shipped sample member imports `_Unwind_Resume` and
`DW.ref.__gxx_personality_v0`, i.e. the prebuilt objects were built **with**
exceptions enabled, and a faithful reconstruction that uses `try`/`throw` must
say so. `-fexceptions` in `extra_flags` replaces the pinned `-fno-exceptions`
rather than being appended next to it; the same applies to `-frtti`. The pinned
default stays `-fno-exceptions -fno-rtti` for anything that does not override it.

## `manifest.jsonl`

One JSON object per line, in ar-ordinal order. This is the **only** place that
describes "which source produces which archive member".

```json
{"idx":23,"name":"module_base_kernel_request.o","source":"src/kernel_request/module_base_kernel_request.cpp","producer":"clang++","status":"stub","baseline_size":98352,"notes":"..."}
```

| key | required | meaning |
|---|---|---|
| `idx` | yes | ar ordinal; positive integer, unique |
| `name` | yes | archive member name, must end in `.o`, unique |
| `source` | yes | path **relative to this directory**, must resolve inside it and exist |
| `producer` | yes | `clang` or `clang++` (which driver compiles the TU) |
| `status` | yes | `stub` (placeholder slot), `partial` (real reconstruction with a documented gap) or `source` (complete reconstruction) |
| `baseline_size` | no | byte size of the member in the prebuilt archive, for the report |
| `extra_flags` | no | list of extra compiler flags for this TU only |
| `notes` | no | free text shown in the report |

The file must contain **exactly `SAMPLE_MEMBER_COUNT` (=4) lines**: the sample is
fixed by agreement, and `build.py` refuses to silently grow or shrink it.
Supported source kinds are `.cpp/.cc/.cxx` (C++), `.c` (C) and `.ll/.bc`
(LLVM IR, compiled with `-x ir`) — the IR kinds exist so a member that has a
verified IR input can go through the same entry while its source is recovered.

Include path: `runtime/maplesu-sdk/include` (the shipped tree) plus
`sdk/include` when that directory exists. Recovered ABI headers that the shipped
tree does not carry (for example `module_base_kernel_request.h`, `icache_policy.h`)
belong in `sdk/include/`; nothing under `runtime/maplesu-sdk/include` is touched.

`status` is not decoration: `build.py` prints a warning for every `stub` line and
stamps `"sample_status": "all-stub" | "mixed" | "all-partial" | "all-source"` into
the report, so neither the report nor this file can quietly claim that a
placeholder implements the chain.

## How a placeholder becomes real source

Drop the reconstruction at the path the manifest already names and flip
`"status"` to `"source"`. **No change to `build.py`, the manifest schema or the
harness is required.** The four invariants the new TU must satisfy:

1. it compiles with the fixed flags above (add `extra_flags` only if a member
   genuinely needs an exception/RTTI build);
2. it keeps every symbol listed in the *contract* (see below);
3. it keeps the C++ mangling of the kit's namespace (`kernel_module::…`) and the
   plain C names of the MapleSU exports (`maplesu_*`, `unsafe_module_*`);
4. it does not introduce a duplicate strong definition of a symbol another
   archive member already owns.

## The substitution contract (why the link proves something)

`--link-sample` copies the prebuilt archive and **deletes the four sample
members from the copy** before linking. A source-built object can therefore never
silently fall back to the prebuilt bytes: if a hop of the chain is missing, the
link fails. On top of that, `--verify` re-derives the contract on every run:

* `build/baseline/<member>.o` is read back out of the archive, and its strong
  external symbols are the *published surface* of that member;
* the residual archive's strong undefined symbols are the *importer side*;
* the intersection is what the rest of the kit still expects from this ordinal —
  a source-built replacement that does not define one of those symbols fails the
  build (exit 6), even if the link happens not to pull the importing member.

The report also records `not_reproduced_vs_baseline`: which published symbols the
current slot does *not* yet provide. For a stub that number is expected to be
non-zero; for a real reconstruction it should reach zero.

## Exit codes

| code | stage | typical cause |
|---:|---|---|
| 0 | — | everything selected succeeded |
| 2 | check | wrong/missing NDK, clang or LLVM version drift |
| 3 | manifest | bad JSON, missing key, duplicate idx/name, source outside the tree, wrong member count |
| 4 | compile | a TU does not compile (command + stderr are printed) |
| 5 | link | residual archive missing, or `--no-undefined` reported unresolved symbols |
| 6 | verify | contract symbol missing, wrong ELF type, sample symbol still imported, chain symbol absent from the `.so` |

## What is *not* proven yet

The tree is currently **`all-partial`** (`"sample_status": "all-partial"`): all
four members carry a reconstruction, but none of them claims to be a complete
behavioural equivalent, so every manifest line still says `status: "partial"`.

The published symbol surface is complete — the contract check reports 11/11,
12/12, 10/10 and 2/2 baseline externals reproduced, and `not_reproduced_vs_baseline`
is empty for all four members. What is *not* complete is the **internal call
graph**: the shipped members reference **10** symbols that the other three
provide, while the current tree closes only **7**:

```
kernel_module::alloc_kernel_mem(unsigned int, unsigned long&)
kernel_module::fill00_kernel_mem(unsigned long, unsigned int, KernMemProt)
kernel_module::set_kernel_mem_protection(unsigned long, unsigned long, KernMemProt)
```

i.e. the reconstruction routes allocation through `request_kernel_alloc_mem` and
does not (yet) call the `fill00` / `set_protection` helpers that the shipped bytes
called. `--partial-link` prints the count as `cross-member edges closed inside the
sample: 7`; it must reach 10 before the sample can claim behavioural equivalence.
That single number is the cheapest progress indicator for the reconstruction.

Everything here is additive: no file under `runtime/maplesu-sdk/` outside this
directory was modified, `scripts/**` and `manager/**` are untouched, and no
device or gradle step is involved.
