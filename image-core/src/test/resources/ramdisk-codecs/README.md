# Independent compressed ramdisk fixtures

`stock.cpio` is deterministic newc: regular mode 0755 `init` contains
`stock-android-init`, and `system/bin/sh` contains `stock-shell` (no newlines).
UID/GID/mtime are zero; the final entry is `TRAILER!!!`.

Two LZ4 frames use system `LZ4F_compressFrame`, including genuine compressed
blocks; the checksums variant enables both block and content checksums.
Three XZ streams use Python standard `lzma` with real LZMA2 compression and
CRC32/CRC64/SHA256 checks. No MapleSU codec generates or verifies these files.

Regenerate in an otherwise empty output directory containing `generate.py`:

```powershell
docker run --pull never --network none --rm --mount "type=bind,source=$PWD,target=/out" --workdir /out --entrypoint python3 sha256:272ada1e61c525116032246df2a790d1ccec1804b7a9e0b09203c5e650dcb78e /out/generate.py
```

`receipt.json` records tool versions, source/generator and fixture hashes,
independent library round trips, GNU cpio mode/content checks, and exact
checksum offsets. XZ block checks are additionally recomputed from raw CPIO.
Offsets are zero-based; all XZ files have one block. Flip only the named
checksum byte to test checksum rejection without corrupting a block header.

These tiny synthetic files establish format/codec behavior, not device boot
acceptance or kernel functionality. Public system libraries only; no keys,
SDK payloads, device contents, downloads, or installed dependencies.
