# resetprop payload program

Source tree for the `resetprop` program the SDK installs inside the root-key
directory (`<root key dir>/resetprop`).  `sdk/build.py` compiles this tree into
`build/out/assets/resetprop_file.bin`, and the
`maplesu_unsafe_resetprop_install.o` member embeds those bytes as
`maplesu::sdk::resetprop_file_data`.

## Origin of the implementation

The installed program is the open-source Magisk `resetprop` applet, rebuilt from
the public Magisk sources that are retained in this checkout:

| Upstream source (Magisk v30.7) | Ported to | Duty |
| --- | --- | --- |
| `native/src/core/resetprop/cli.rs` | `src/cli_contract.h`, `src/prop_command.{h,cpp}` | option surface, operation modes, read/write/delete/wait/file semantics, exit statuses |
| `native/src/core/resetprop/mod.rs` | `src/property_api.{h,cpp}` | property-area access: find/read/foreach/write/delete/wait through the platform's dynamic property API |
| `native/src/core/resetprop/sys.cpp` | `src/property_api.cpp` | run-time resolution of the platform property symbols (classic and long-property variants), `__system_properties_init` |
| `native/src/core/resetprop/persist.rs` | `src/persist.{h,cpp}`, `src/persist_proto.h` | `/data/property`: protobuf record list or one-file-per-property, atomic publish |
| `native/src/base/files.rs` (`for_each_prop`) | `src/prop_line.h` | `-f` file line grammar (trim, `#` comments, `NAME=VALUE`) |

The vendor ships a full Magisk `libmagisk.so` image (every Magisk applet plus the
native-bridge entry); this program contains only the resetprop duty the SDK
actually runs.  It is installed with no root-key token (the installer passes
`token == nullptr` for this payload), so it carries no feature-token block.

## Recovered contract

`resetprop [flags] [arguments...]`

| Mode | Arguments | Behaviour |
| --- | --- | --- |
| read | *(none)* | print every property as `[name]: [value]`, name-sorted |
| read | `NAME` | print the value of `NAME` (+ newline) |
| write | `NAME VALUE` | set/replace `NAME` |
| write | `-f/--file FILE` | load `NAME=VALUE` lines and set each |
| write | `-d/--delete NAME` | delete `NAME` |
| wait | `-w NAME` | block until `NAME` exists/changes |
| wait | `-w NAME OLD` | block until the value differs from `OLD` |

Flags: `-v/--verbose`, `-w`, `-p`, `-P`, `-Z`, `-n`, `-h/--help`; `-f`/`-d` take
a value.  `-p` adds persistent storage to reads and forces persistent writes to
storage; `-P` reads persistent storage only; `-Z` prints the SELinux context;
`-n` bypasses `property_service` (direct area update/add).

Write semantics (identical to upstream):

* `ro.*` properties always take the bypass path; a long value (or an existing
  long record) makes the program delete the record first and recreate it, because
  a long property cannot be updated in place.
* Without `-n` the write goes through `property_service`
  (`__system_property_set`); with `-n` it updates or adds the record directly.
* `-n -p` also writes `persist.*` values to `/data/property`.
* Delete removes the record with pruning, plus the persistent storage entry when
  `-p` is given.

Exit statuses: `0` success; `1` for every operational failure (missing property,
unreadable file, failed delete) **and** for every usage error; `0` after printing
the help text (`-h`/`--help`, to stderr, like the upstream applet).  Usage errors
print their reason followed by the help text.

Persistent storage: when `/data/property/persistent_properties` exists the record
list is decoded from its proto2 form, kept name-sorted, and republished through a
`mkstemp` + attribute copy + `rename` sequence; otherwise one file per property
under `/data/property/` is used, written through a temporary file and renamed.
Nothing is ever written in place.

## Build

`sdk/build.py --payloads` runs `sdk/tools/build_payloads.py --only resetprop`
internally; the recipe lives in `program.json` (AArch64 Android PIE, static libc++,
`-fno-exceptions -fno-rtti`, stripped).  No third-party sources are needed.

## What is intentionally not reproduced

* The Magisk applet dispatcher and the `NativeBridgeItf` export of the shipped
  `libmagisk.so` image: this program is only ever executed as `resetprop`.
* The release build's debug trace: upstream compiles its `debug!` statements out
  in release builds, so `-v` is accepted but produces no extra output, exactly as
  in the reference image.
* Upstream's logcat mirror of error lines: this program writes diagnostics to its
  own standard streams (the installer runs it with stdio wired to the module's
  log).
