"""Legacy zero-base Images keep the complete base slot and exact name/marker pairing."""
from pathlib import Path
import struct
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/specgen"))
from specgen.bootimg import kernel
from specgen.kallsyms import KallsymsError, _Probe, decode


def legacy_fixture(marker_width, base, modern=False):
    """Same label order exercised by UltraKernelKallsymsNgkiTest; no parser is reused."""
    count = 1025
    out = bytearray(128)

    def align():
        out.extend(bytes(-len(out) % 8))

    def word(value, width):
        out.extend(value.to_bytes(width, "little"))

    if not modern:
        for index in range(count):
            word(index * 16, 4)
        align()
        word(base, 8)
    count_at = len(out)
    word(count, 4)
    align()
    names_at = len(out)
    markers = []
    for index in range(count):
        if index % 256 == 0:
            markers.append(len(out) - names_at)
        tokens = bytes((84, 128)) if index == 0 else bytes((84, 95)) + ("%04d" % index).encode("ascii")
        out.append(len(tokens))
        out.extend(tokens)
    align()
    markers_at = len(out)
    for value in markers:
        word(value, marker_width)
    align()
    token_at = len(out)
    token_index = []
    for index in range(256):
        token_index.append(len(out) - token_at)
        value = chr(index) if 48 <= index <= 57 else {84: "T", 95: "_s", 128: "_text"}.get(index, "z")
        out.extend(value.encode("ascii") + b"\0")
    align()
    for value in token_index:
        word(value, 2)
    if modern:
        align()
        for index in range(count):
            word(index * 16, 4)
        align()
        word(base, 8)
    out.extend(bytes(max(0, 0x6000 - len(out))))
    out[56:60] = b"ARMd"
    return out, {"count": count_at, "names": names_at, "markers": markers_at}


class KallsymsNgkiTest(unittest.TestCase):
    def test_legacy_zero_base_preserves_complete_slot_for_both_marker_widths(self):
        for width in (4, 8):
            with self.subTest(marker_width=width):
                raw, _ = legacy_fixture(width, 0)
                symbols = decode(bytes(raw))
                self.assertEqual(0, symbols.relative_base)
                self.assertEqual(0, symbols.text_base)
                self.assertEqual(1025, len(symbols.rows))
                self.assertEqual(0, symbols.offset_of("_text"))
                self.assertEqual(16, symbols.offset_of("_s0001"))
                self.assertEqual(16384, symbols.offset_of("_s1024"))

    def test_canonical_legacy_base_is_unchanged_for_both_marker_widths(self):
        for width in (4, 8):
            with self.subTest(marker_width=width):
                raw, _ = legacy_fixture(width, 0xFFFFFFC080000000)
                symbols = decode(bytes(raw))
                self.assertEqual(1025, len(symbols.rows))
                self.assertEqual(0, symbols.offset_of("_text"))
                self.assertEqual(16, symbols.offset_of("_s0001"))

    def test_positive_nonzero_legacy_base_is_rejected(self):
        raw, _ = legacy_fixture(4, 0x50000)
        with self.assertRaisesRegex(KallsymsError, "KALLSYMS_RELATIVE_BASE_IMPLAUSIBLE"):
            decode(bytes(raw))

    def test_zero_base_requires_text_at_image_start(self):
        for width in (4, 8):
            with self.subTest(marker_width=width):
                raw, _ = legacy_fixture(width, 0)
                struct.pack_into("<i", raw, 128, 0x10000)
                with self.assertRaisesRegex(KallsymsError, "^KALLSYMS_IMAGE_RELATIVE_TEXT_MISMATCH$"):
                    decode(bytes(raw))

    def test_zero_base_rejects_shifted_address_table(self):
        raw, _ = legacy_fixture(4, 0)
        raw[128:128 + 1024 * 4] = raw[132:128 + 1025 * 4]
        with self.assertRaisesRegex(KallsymsError, "^KALLSYMS_IMAGE_RELATIVE_TEXT_MISMATCH$"):
            decode(bytes(raw))

    def test_zero_base_table_stays_inside_image(self):
        raw, _ = legacy_fixture(4, 0)
        raw = raw[136:]
        raw[56:60] = b"ARMd"
        with self.assertRaisesRegex(KallsymsError, "KALLSYMS_OFFSETS_OUT_OF_RANGE"):
            decode(bytes(raw))

    def test_zero_base_names_match_every_marker(self):
        raw, layout = legacy_fixture(4, 0)
        at = layout["markers"] + 4
        struct.pack_into("<I", raw, at, struct.unpack_from("<I", raw, at)[0] + 4)
        with self.assertRaisesRegex(KallsymsError, "^KALLSYMS_NAMES_MARKER_MISMATCH:256$"):
            decode(bytes(raw))

    def test_zero_base_requires_eight_byte_count_label(self):
        with self.assertRaisesRegex(KallsymsError, "^KALLSYMS_NUM_SYMS_ALIGNMENT$"):
            _Probe(bytes(64)).find_offsets_legacy(4, 28, 4)

    def test_modern_zero_base_is_still_rejected(self):
        with self.assertRaisesRegex(KallsymsError, "KALLSYMS_RELATIVE_BASE_IMPLAUSIBLE:0x0"):
            _Probe(bytes(256)).find_offsets(4, 128, 4, 0)

    def test_modern_canonical_base_is_unchanged(self):
        raw, _ = legacy_fixture(4, 0xFFFFFFC080000000, modern=True)
        symbols = decode(bytes(raw))
        self.assertEqual(1025, len(symbols.rows))
        self.assertEqual(0, symbols.offset_of("_text"))
        self.assertEqual(16, symbols.offset_of("_s0001"))

    def test_actual_ngki_retains_verified_kotlin_coordinates(self):
        path = ROOT / "Boot_Research/NGKI/boot.img"
        if not path.is_file():
            self.skipTest("NGKI actual boot fixture absent")
        symbols = decode(kernel(path))
        self.assertEqual(0, symbols.relative_base)
        self.assertEqual(0, symbols.text_base)
        for name, offset in (("_text", 0), ("_stext", 0x1000), ("_etext", 0x1640000),
                             ("kallsyms_lookup_name", 0x32F888), ("file_open_root", 4780656)):
            self.assertEqual(offset, symbols.offset_of(name), name)


if __name__ == "__main__":
    unittest.main()
