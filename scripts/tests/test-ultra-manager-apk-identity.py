"""Exercise MapleSU's single-object APK pin parser; no device or grant claims."""
import argparse
import ctypes
import hashlib
import io
import json
from pathlib import Path
import random
import struct
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PIN = "30bafb57e3604afe93da13a1285516e043feb82b56a861a157b53ccaa27c3506"


def lp(data):
    return struct.pack("<I", len(data)) + data


def v2_signer(certificate, chain=b"", reserved=b""):
    algorithm = struct.pack("<I", 0x0103)
    signed_data = lp(lp(algorithm + lp(bytes(32)))) + lp(lp(certificate) + chain) + lp(b"") + reserved
    return lp(signed_data) + lp(lp(algorithm + lp(b"signature"))) + lp(b"public-key")


def fixture(certificate, names=None, comment=b"", signers=None, extra_pairs=b"", chain=b""):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_STORED) as archive:
        for index, name in enumerate(names or ["assets/ksu_manager_gen2", "AndroidManifest.xml"]):
            archive.writestr(name, b"fixture" + bytes([index]))
        archive.comment = comment
    raw = output.getvalue()
    eocd = raw.rfind(b"PK\x05\x06")
    cd = struct.unpack_from("<I", raw, eocd + 16)[0]
    if signers is None:
        signers = [v2_signer(certificate, chain)]
    value = struct.pack("<I", 0x7109871A) + lp(b"".join(lp(signer) for signer in signers))
    pair = struct.pack("<Q", len(value)) + value + extra_pairs
    size = struct.pack("<Q", len(pair) + 24)
    block = size + pair + size + b"APK Sig Block 42"
    result = bytearray(raw[:cd] + block + raw[cd:])
    struct.pack_into("<I", result, eocd + len(block) + 16, cd + len(block))
    return bytes(result)


def certificate_from_apk(raw):
    eocd = raw.rfind(b"PK\x05\x06")
    cd = struct.unpack_from("<I", raw, eocd + 16)[0]
    size = struct.unpack_from("<Q", raw, cd - 24)[0]
    at, end = cd - size, cd - 24
    while at < end:
        length, block_id = struct.unpack_from("<QI", raw, at)
        if block_id == 0x7109871A:
            position = at + 12
            # signers, signer, signed data, digest sequence
            position += 12
            digest_length = struct.unpack_from("<I", raw, position)[0]
            position += 4 + digest_length
            position += 4  # certificates sequence
            cert_length = struct.unpack_from("<I", raw, position)[0]
            return raw[position + 4:position + 4 + cert_length]
        at += 8 + length
    raise AssertionError("No v2 signer in reference APK")


def inside(build):
    read_type = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.c_void_p, ctypes.c_uint64,
                                ctypes.c_void_p, ctypes.c_size_t)
    hash_type = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.c_void_p, ctypes.c_void_p,
                                ctypes.c_size_t, ctypes.c_void_p)

    class Source(ctypes.Structure):
        _fields_ = [("context", ctypes.c_void_p), ("length", ctypes.c_uint64),
                    ("read_at", read_type), ("sha256", hash_type)]

    class Pin(ctypes.Structure):
        _fields_ = [("size", ctypes.c_uint32), ("digest", ctypes.c_ubyte * 32),
                    ("minimum", ctypes.c_uint32)]

    library = ctypes.CDLL(str(build / "parser.so"))
    verify = library.msu_manager_apk_identity
    verify.argtypes = [ctypes.POINTER(Source), ctypes.POINTER(Pin), ctypes.POINTER(ctypes.c_uint32)]
    verify.restype = ctypes.c_int
    cert = (b"MapleSU parser fixture\0" * 40)[:772]
    (build / "fixture.cert").write_bytes(cert)
    pin = Pin(len(cert), (ctypes.c_ubyte * 32).from_buffer_copy(hashlib.sha256(cert).digest()), 2)
    tested = []
    corpus = build / "corpus"
    corpus.mkdir(exist_ok=True)
    manifest = []

    def check(name, data, expected, selected_pin=pin, retain=True):
        reads = 0

        @read_type
        def read(_, offset, destination, length):
            nonlocal reads
            reads += 1
            if offset > len(data) or length > len(data) - offset:
                raise AssertionError("parser requested out of bounds read")
            ctypes.memmove(destination, data[offset:offset + length], length)
            return 0

        @hash_type
        def digest(_, source, length, destination):
            ctypes.memmove(destination, hashlib.sha256(ctypes.string_at(source, length)).digest(), 32)
            return 0

        generation = ctypes.c_uint32(123)
        source = Source(None, len(data), read, digest)
        result = verify(ctypes.byref(source), ctypes.byref(selected_pin), ctypes.byref(generation))
        observed = generation.value if result else 0
        assert observed == expected and (result or generation.value == 0), (name, expected, observed)
        tested.append({"name": name, "expectedGeneration": expected, "reads": reads})
        if retain:
            filename = f"case-{len(manifest):05}.apk"
            (corpus / filename).write_bytes(data)
            manifest.append(f"{filename} {expected}\n")

    valid = fixture(cert)
    check("single signer", valid, 2)
    check("AOSP trailing empty field", fixture(cert, signers=[v2_signer(cert, reserved=lp(b""))]), 2)
    check("nonempty reserved field", fixture(cert, signers=[v2_signer(cert, reserved=lp(b"x"))]), 0)
    check("two reserved fields", fixture(cert, signers=[v2_signer(cert, reserved=lp(b"") * 2)]), 0)
    check("certificate chain", fixture(cert, chain=lp(b"issuer certificate")), 2)
    check("max ZIP comment", fixture(cert, comment=b"x" * 65535), 2)
    check("comment crossing EOCD window", fixture(cert, comment=b"x" * 499), 2)
    check("generation 3", fixture(cert, names=["assets/ksu_manager_gen3"]), 3)
    check("public certificate on wrong first signer", fixture(cert, signers=[v2_signer(b"other"), v2_signer(cert)]), 0)
    check("second signer", fixture(cert, signers=[v2_signer(cert), v2_signer(b"other")]), 0)
    check("two matching signers", fixture(cert, signers=[v2_signer(cert)] * 2), 0)
    check("wrong signer", fixture(bytes(reversed(cert))), 0)
    check("empty signer set", fixture(cert, signers=[]), 0)
    check("empty certificate chain", fixture(cert, signers=[v2_signer(b"")]), 0)
    check("generation below minimum", fixture(cert, names=["assets/ksu_manager_gen1"]), 0)
    check("missing generation", fixture(cert, names=["AndroidManifest.xml"]), 0)
    check("duplicate generations", fixture(cert, names=["assets/ksu_manager_gen2", "assets/ksu_manager_gen3"]), 0)
    check("generation overflow", fixture(cert, names=["assets/ksu_manager_gen9999999999999999"]), 0)
    check("generation suffix", fixture(cert, names=["assets/ksu_manager_gen2.bak"]), 0)
    padding = struct.pack("<QI", 7, 0x42726577) + b"\0" * 3
    check("verity padding", fixture(cert, extra_pairs=padding), 2)
    check("extra v2 block", fixture(cert, extra_pairs=struct.pack("<QI", 4, 0x7109871A)), 0)
    check("v3 block outside current pin contract", fixture(cert, extra_pairs=struct.pack("<QI", 4, 0xF05368C0)), 0)
    eocd = valid.rfind(b"PK\x05\x06")
    cd = struct.unpack_from("<I", valid, eocd + 16)[0]
    block_size = struct.unpack_from("<Q", valid, cd - 24)[0]
    block = cd - block_size - 8
    mutations = [
        ("EOCD disk", eocd + 4, b"\1\0"),
        ("EOCD count mismatch", eocd + 8, b"\0\0"),
        ("ZIP64 count", eocd + 10, b"\xff\xff"),
        ("central directory offset overflow", eocd + 16, b"\xff" * 4),
        ("footer size underflow", cd - 24, bytes(8)),
        ("footer size overflow", cd - 24, b"\xff" * 8),
        ("header footer mismatch", block, bytes(8)),
        ("signing magic", cd - 16, b"not"),
        ("pair length underflow", block + 8, bytes(8)),
        ("pair length overflow", block + 8, b"\xff" * 8),
        ("sequence length overflow", block + 20, b"\xff" * 4),
        ("local generation name mismatch", 30, b"z"),
        ("central directory signature", cd, bytes(4)),
        ("central directory local offset", cd + 42, b"\xff" * 4),
        ("central directory name overflow", cd + 28, b"\xff\xff"),
        ("central directory extra overflow", cd + 30, b"\xff\xff"),
    ]
    for name, offset, value in mutations:
        altered = bytearray(valid)
        altered[offset:offset + len(value)] = value
        check(name, bytes(altered), 0)
    for size in range(len(valid)):
        check(f"truncate {size}", valid[:size], 0)
    generator = random.Random(0x4D5355)
    # Random malformed inputs test termination and memory bounds. Expectations
    # come from the real parser pass, then repeat under ASan/UBSan independently.
    for index in range(2000):
        raw = bytearray(valid)
        for _ in range(generator.randint(1, 5)):
            raw[generator.randrange(len(raw))] = generator.randrange(256)
        observed = []

        @read_type
        def read_mutant(_, offset, destination, length):
            assert offset <= len(raw) and length <= len(raw) - offset
            ctypes.memmove(destination, bytes(raw[offset:offset + length]), length)
            return 0

        @hash_type
        def hash_mutant(_, source, length, destination):
            ctypes.memmove(destination, hashlib.sha256(ctypes.string_at(source, length)).digest(), 32)
            return 0

        out = ctypes.c_uint32(123)
        src = Source(None, len(raw), read_mutant, hash_mutant)
        result = verify(ctypes.byref(src), ctypes.byref(pin), ctypes.byref(out))
        observed.append(out.value if result else 0)
        check(f"bounded mutation {index}", bytes(raw), observed[0])
    actual = []
    for reference in sorted(build.glob("reference-*.apk")):
        raw = reference.read_bytes()
        certificate = certificate_from_apk(raw)
        official = len(certificate) == 772 and hashlib.sha256(certificate).hexdigest() == PIN
        reference_pin = Pin(772, (ctypes.c_ubyte * 32).from_buffer_copy(bytes.fromhex(PIN)), 2)
        check(reference.name + " release pin", raw, 2 if official else 0, reference_pin, retain=False)
        actual.append({"apk": reference.name, "sha256": hashlib.sha256(raw).hexdigest(),
                       "officialReleasePinAccepted": official, "passed": True})
    (build / "manifest.txt").write_text("".join(manifest), encoding="utf-8")
    (build / "python-results.json").write_text(json.dumps({
        "tests": len(tested), "passed": True, "cases": tested, "referenceApks": actual,
        "mutationExpectationsAreRegressionOnly": True,
        "standaloneApkSignatureVerification": False, "kernelExecuted": False,
        "rootGranted": False, "deviceExecuted": False,
    }, indent=2) + "\n", encoding="utf-8")
    print(f"single-object APK parser: {len(tested)} cases; {len(actual)} real APKs")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build", type=Path, required=True)
    parser.add_argument("--apk", type=Path, action="append", default=[])
    parser.add_argument("--inside", action="store_true")
    args = parser.parse_args()
    if args.inside:
        inside(args.build)
        return 0
    build = args.build.resolve()
    build.mkdir(parents=True, exist_ok=True)
    import shutil
    for index, apk in enumerate(args.apk):
        shutil.copyfile(apk, build / f"reference-{index}.apk")
    sources = ["runtime/ultra/identity/kernel/manager_apk_identity.c",
               "runtime/ultra/identity/kernel/manager_apk_identity.h",
               "runtime/ultra/identity/kernel/manager_claim_abi.h",
               "runtime/ultra/bridge/manager_identity_claim.h",
               "runtime/tests/pro_manager_apk_identity_test.c",
               "runtime/tests/pro_manager_claim_client_test.cpp",
               "scripts/tests/test-ultra-manager-apk-identity.py"]
    source_hashes = {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in sources}
    script = r'''set -eu
C=/opt/ddk/clang/clang-r510928
P=/repo/runtime/ultra/identity/kernel/manager_apk_identity.c
$C/bin/clang -std=c11 -O2 -Wall -Wextra -Werror -fPIC -shared "$P" -o /case/parser.so
python3 /repo/scripts/tests/test-ultra-manager-apk-identity.py --inside --build /case
$C/bin/clang -std=c11 -O1 -g -Wall -Wextra -Werror -fno-omit-frame-pointer -fsanitize=address,undefined "$P" /repo/runtime/tests/pro_manager_apk_identity_test.c -o /case/parser-sanitized.test
/case/parser-sanitized.test /case/fixture.cert /case/manifest.txt /case/corpus
$C/bin/clang++ -std=c++20 -O1 -g -stdlib=libc++ -Wall -Wextra -Werror -fno-omit-frame-pointer -fsanitize=address,undefined -fno-sanitize=vptr -Wl,-rpath,$C/lib/x86_64-unknown-linux-gnu /repo/runtime/tests/pro_manager_claim_client_test.cpp -o /case/claim-client.test
/case/claim-client.test
'''
    result = subprocess.run([
        "docker", "run", "--rm", "--pull=never", "--network", "none",
        "--mount", f"type=bind,source={ROOT},target=/repo,readonly",
        "--mount", f"type=bind,source={build},target=/case", "--entrypoint", "sh",
        "ghcr.io/ylarod/ddk-min:android15-6.6-20260828", "-c", script,
    ], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=240)
    output = result.stdout + result.stderr
    (build / "run.log").write_text(output, encoding="utf-8")
    unchanged = all(hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == digest
                    for name, digest in source_hashes.items())
    (build / "results.json").write_text(json.dumps({
        "passed": result.returncode == 0 and unchanged, "exitCode": result.returncode,
        "sourceHashes": source_hashes, "sourcesUnchanged": unchanged,
        "sanitizers": ["address", "undefined"], "gradleExecuted": False,
        "kernelExecuted": False, "rootGranted": False, "deviceExecuted": False,
    }, indent=2) + "\n", encoding="utf-8")
    print(output, end="")
    return result.returncode if unchanged else 1


if __name__ == "__main__":
    sys.exit(main())
