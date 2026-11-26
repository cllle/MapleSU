# webui_loader payload program

Source tree for the `webui_loader` program the SDK installs next to the module
tree.  `sdk/build.py` compiles this tree into `build/out/assets/webui_loader.bin`,
and the `maplesu_unsafe_webui_loader_install.o` member embeds those bytes as
`maplesu::sdk::webui_loader_data`.

## Origin of the implementation

The kernel-side kit has no open source, but the userspace half of the same
feature ships in the vendor's open edition:

| Open-edition source | Used for |
| --- | --- |
| `.research/MapleSU/Lite_version/src/testRoot/kernel_root_kit/src/jni/lib_web_server_loader/lib_web_server_loader.cpp` | launch/dispatch shape: read the root key, start the module's UI, keep the process for the caller |
| `.research/MapleSU/Lite_version/src/testRoot/kernel_root_kit/src/jni/lib_web_server_loader/shm_open_anon.cpp` | behaviour reference for the anonymous memory-file path (the Ultra loader maps the module ELF directly, so this file is not compiled) |
| `.research/MapleSU/Lite_version/src/testRoot/kernel_root_kit/src/jni/web_server/web_server.cpp` + `civetweb-1.16/` + `civetweb-1.16/src/CivetServer.cpp` | the HTTP contract the served webroot keeps: explicit `GET /` index response, CivetWeb static-file serving, localhost-only listener |
| `.research/MapleSU/Lite_version/src/testRoot/kernel_root_kit/src/jni/web_server/cJSON.cpp` | evaluated and not imported: the loader-side fallback has no JSON endpoint of its own (module handlers own that surface), so importing it would add unused code |

The Ultra behaviour was recovered from the SDK side that starts this
program (`runtime/maplesu-sdk/sdk/src/maplesu_core/maplesu_module_webui_loader.cpp`)
and from the module ABI headers (`private_desc_parser.h`,
`module_web_ui_http_handler.h`): the argument vector, the three module entry
points, the exit-action policy and the static webroot live there.

## Contract

`webui_loader <mod_id32> <port> <caller_pid> <maplesu_log_flag>`

* `mod_id32` - 32 hex characters, the module identity parsed by the kit.
* `port` - decimal port in `[20000, 60000]`; the kit allocates it.
* `caller_pid` - owner of the session; the loader stops serving when it exits
  unless the module answers `KeepRunning`.
* `maplesu_log_flag` - `1` when MapleSU file logging is enabled, else `0`.

Paths are derived from the module root key the installer writes into the image
(the 4096-byte feature token), never from argv:

    /data/<root_key[0..16] + root_key[0]>/modules/webui_loader      this program
    .../modules/<mod_id32>/<mod_id32>                               module ELF
    .../modules/<mod_id32>/webroot/                                 static web files

See `sdk/notes/PAYLOADS.md` for the full recovered contract, exit codes and the
acceptance record.

## third_party/civetweb

CivetWeb 1.16, MIT, copied from
`.research/MapleSU/Lite_version/src/testRoot/kernel_root_kit/src/jni/web_server/civetweb-1.16`
(copyright notices are preserved in each file).  Compiled with `NO_SSL`,
`NO_CGI`, `NO_CACHING`; the loader only needs the static-file listener.
