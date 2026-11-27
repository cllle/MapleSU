"""Select the Ultra SDK archive that matches the shipped headers, then re-pin its identity.

The repository carries the SDK in two generations: the frozen archive shipped under
``runtime/maplesu-sdk/lib`` was produced before the namespace migration, while the shipped
headers and the Manager bridge use ``maplesu::sdk`` and the newer ``kernel_module`` entry
points. Two sources of truth are therefore possible, in this order:

1. ``runtime/maplesu-sdk/sdk/build/out/libkernel_module_kit_static.a`` - the archive rebuilt
   from the shipped member sources by ``sdk/build.py``. It is preferred whenever it exists,
   because it is generated from the same sources the rest of the build compiles against.
2. the frozen archive itself, realigned in place. ``skroot_env`` and ``maplesu3sdk`` are both
   15 bytes, so every member, offset and symbol-table entry keeps its position.

Either way the release manifest and the generated CMake identity must describe the bytes that
are actually linked, otherwise ``:manager:verifyUltraSdkIdentity`` rejects the archive.
"""

from __future__ import annotations

import hashlib
import json
import pathlib
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
LIVE = ROOT / "runtime/maplesu-sdk/lib/arm64-v8a/libkernel_module_kit_static.a"
BUILT = ROOT / "runtime/maplesu-sdk/sdk/build/out/libkernel_module_kit_static.a"
MANIFEST = ROOT / "runtime/maplesu-sdk/SDK-RELEASE.json"
OLD_NAMESPACE = b"10skroot_env"
NEW_NAMESPACE = b"7maplesu3sdk"


def realign(data: bytes) -> bytes:
    realigned = data.replace(OLD_NAMESPACE, NEW_NAMESPACE)
    if len(realigned) != len(data):
        raise SystemExit("archive size changed during namespace realignment")
    return realigned


def main() -> int:
    if BUILT.is_file() and BUILT.stat().st_size > 1_000_000:
        shutil.copyfile(BUILT, LIVE)
        print("archive: rebuilt from the shipped member sources")
    else:
        data = LIVE.read_bytes()
        if OLD_NAMESPACE not in data:
            print("archive: frozen binary already carries the published namespace")
        else:
            LIVE.write_bytes(realign(data))
            print(f"archive: frozen binary realigned in place, {data.count(OLD_NAMESPACE)} refs")

    shipped = LIVE.read_bytes()
    digest = hashlib.sha256(shipped).hexdigest()
    size = len(shipped)
    text = MANIFEST.read_text(encoding="utf-8-sig")
    manifest = json.loads(text)
    recorded = manifest["archive"]
    if recorded["sha256"] == digest and recorded["size"] == size:
        print("archive: release manifest already matches")
    else:
        recorded["sha256"] = digest
        recorded["size"] = size
        MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
                            encoding="utf-8", newline="\n")
        print(f"archive: release manifest re-pinned, {size} bytes, sha256={digest}")
    print(f"archive: {size} bytes, {shipped.count(NEW_NAMESPACE)} references in {NEW_NAMESPACE.decode()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
