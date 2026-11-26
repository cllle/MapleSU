"""Locate and decode an in-image kallsyms table from a raw arm64 ``Image``.

This exists so the spec generator needs nothing but a kernel file: no prebuilt engine, no
pre-dumped symbol list, no ELF. It is a compact port of the well-known layout walk:

    kallsyms_num_syms     u32 / u64
    kallsyms_names        length-prefixed token strings (>= 0x80 escapes to 2 bytes)
    kallsyms_markers      one names offset per 256 symbols, pointer-sized
    kallsyms_token_table  256 NUL-terminated token strings
    kallsyms_token_index  256 u16 offsets into the token table
    kallsyms_offsets      s32 per symbol  (BASE_RELATIVE, kernel >= 4.6)
    kallsyms_relative_base u64

Kernel >= 6.4 places ``kallsyms_offsets`` *after* ``kallsyms_token_index``; older kernels
place the address table before ``kallsyms_names``. Both orders are handled. The output is the
same ``offset<TAB>type<TAB>name`` dump the rest of the toolchain consumes, where ``offset`` is
a file offset inside the image (``virtual_address - text_base``).

The decoder never writes to the image and never executes anything.
"""
from __future__ import annotations

import re
import struct

TEXT_TYPES = "TtWw"


class KallsymsError(ValueError):
    pass


class SymbolTable:
    """Resolved symbols of one raw Image, plus the layout facts that produced them."""

    def __init__(self, image_size: int, text_base: int, relative_base: int, rows: list[tuple[int, str, str]],
                 layout: dict):
        self.image_size = image_size
        self.text_base = text_base
        self.relative_base = relative_base
        self.rows = rows                      # sorted [(file_offset, type, name)]
        self.layout = layout
        self._by_name: dict[str, list[int]] = {}
        self._by_offset: dict[int, list[str]] = {}
        self._offsets: list[int] = []
        for offset, _type, name in rows:
            self._by_name.setdefault(name, []).append(offset)
            self._by_offset.setdefault(offset, []).append(name)
            self._offsets.append(offset)
        self._offsets.sort()
        self._starts = set(self._offsets)

    def names_at(self, offset: int) -> list[str]:
        """Every symbol name that starts exactly at ``offset``."""
        return list(self._by_offset.get(offset, ()))

    def reference(self, address: int) -> tuple[str, int]:
        """A ``(name, delta)`` that resolves *back* to ``address`` on this table.

        kallsyms carries aliases and repeated names, so a name is only usable when it
        round-trips: the generator must emit a reference that the applier-side resolver
        turns into the same address again. Raises [KallsymsError] when nothing round-trips.
        """
        for name in self.names_at(address):
            if len(self._by_name[name]) == 1:
                return name, 0
        owner = self.containing(address)
        if owner is not None:
            base = address - owner[1]
            for name in self.names_at(base):
                if len(self._by_name[name]) == 1:
                    return name, owner[1]
            if len(self._by_name[owner[0]]) == 1 and self._by_name[owner[0]][0] == base:
                return owner
        raise KallsymsError("NO_ROUND_TRIP_SYMBOL:0x%X" % address)

    def offset_of(self, name: str) -> int | None:
        """File offset of ``name``; the lowest one when kallsyms carries aliases."""
        hits = self._by_name.get(name)
        return min(hits) if hits else None

    def all_offsets(self, name: str) -> list[int]:
        return list(self._by_name.get(name, ()))

    def containing(self, offset: int) -> tuple[str, int] | None:
        """``(name, delta)`` of the symbol that owns ``offset``, or ``None``."""
        import bisect
        index = bisect.bisect_right(self._offsets, offset) - 1
        if index < 0:
            return None
        base = self._offsets[index]
        for row_offset, _type, name in self.rows:
            if row_offset == base:
                return name, offset - base
        return None

    def next_offset(self, offset: int) -> int | None:
        """File offset of the first symbol strictly after ``offset``."""
        import bisect
        index = bisect.bisect_right(self._offsets, offset)
        return self._offsets[index] if index < len(self._offsets) else None

    def has(self, name: str) -> bool:
        return name in self._by_name

    def is_symbol_start(self, value: int) -> bool:
        return value in self._starts

    def write_tsv(self, path) -> None:
        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            handle.write("#offset\ttype\tname\n")
            for offset, kind, name in self.rows:
                handle.write("%d\t%s\t%s\n" % (offset, kind, name))


def load_tsv(path) -> SymbolTable:
    """Read a ``offset<TAB>type<TAB>name`` dump produced here or by the engine tooling."""
    rows = []
    with open(path, "r", encoding="utf-8", errors="replace") as handle:
        for line in handle:
            line = line.rstrip("\r\n")
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) != 3:
                continue
            rows.append((int(parts[0]), parts[1], parts[2]))
    rows.sort()
    return SymbolTable(0, 0, 0, rows, {"source": "tsv", "path": str(path)})


def decode(image: bytes) -> SymbolTable:
    """Decode the kallsyms table of ``image`` (raw arm64 ``Image``)."""
    if len(image) < 64:
        raise KallsymsError("IMAGE_TOO_SMALL")
    if image[56:60] != b"ARMd":
        raise KallsymsError("RAW_ARM64_REQUIRED")
    probe = _Probe(image)
    token_table = probe.find_token_table()
    token_index, token_index_end, big_endian = probe.find_token_index(token_table)
    element_size, markers = probe.find_markers(token_table)
    names = probe.find_names(markers, token_table, element_size)
    num_syms, names, num_syms_at = probe.find_num_syms(names, markers, token_table, element_size)
    tokens = probe.read_tokens(token_table)
    try:
        offsets_at, relative_base, base_relative = probe.find_offsets(
            num_syms, token_index_end, element_size, token_table)
    except KallsymsError:
        # Kernel < 6.4 keeps the address table *before* kallsyms_names instead of after the token
        # index. Same structures, different order; retry with the older anchor before failing.
        offsets_at, relative_base, base_relative = probe.find_offsets_legacy(
            num_syms, num_syms_at, element_size)
    if relative_base == 0:
        if names != (num_syms_at + 4 + 7) & ~7:
            raise KallsymsError("KALLSYMS_NUM_SYMS_ALIGNMENT")
        marker_end = markers + (num_syms + 255) // 256 * element_size
        if token_table != (marker_end + 7) & ~7:
            raise KallsymsError("KALLSYMS_MARKER_TABLE_MISMATCH")
    symbol_names = probe.read_names(names, num_syms, tokens,
                                   (markers, element_size) if relative_base == 0 else None)
    addresses = probe.read_addresses(offsets_at, num_syms, relative_base, base_relative)
    if relative_base == 0 and not any(name[1:] == "_text" and address == 0
                                     for address, name in zip(addresses, symbol_names)):
        raise KallsymsError("KALLSYMS_IMAGE_RELATIVE_TEXT_MISMATCH")
    first_text = next(
        (address for address, name in zip(addresses, symbol_names) if name[:1].upper() == "T"), 0)
    if base_relative and relative_base < first_text:
        text_base = relative_base & ~0x1FFF
    else:
        text_base = first_text & ~0x1FFF
    # A one-entry table shift can retain a plausible base but move _text off the Image start.
    if not any(name[1:] == "_text" and address == text_base
               for address, name in zip(addresses, symbol_names)):
        raise KallsymsError("KALLSYMS_TEXT_BASE_MISMATCH")
    rows = []
    dropped = 0
    for address, name in zip(addresses, symbol_names):
        offset = address - text_base
        if 0 <= offset < len(image):
            rows.append((offset, _kind(name[:1]), name[1:]))
        else:
            dropped += 1
    rows.sort()
    layout = {
        "source": "image",
        "image_bytes": len(image),
        "text_base": "0x%X" % text_base,
        "relative_base": "0x%X" % relative_base,
        "base_relative": base_relative,
        "big_endian": big_endian,
        "offset_element_size": element_size,
        "num_syms": num_syms,
        "symbols_in_image": len(rows),
        "symbols_outside_image": dropped,
        "token_table": "0x%X" % token_table,
        "token_index": "0x%X" % token_index,
        "names": "0x%X" % names,
        "markers": "0x%X" % markers,
        "offsets": "0x%X" % offsets_at,
    }
    return SymbolTable(len(image), text_base, relative_base, rows, layout)


def _kind(letter: str) -> str:
    if letter in TEXT_TYPES:
        return "TEXT"
    upper = letter.upper()
    return {
        "B": "BSS", "D": "DATA", "R": "RODATA", "A": "ABSOLUTE",
        "V": "WEAK_OBJECT_WITH_DEFAULT", "W": "WEAK_SYMBOL_WITH_DEFAULT",
    }.get(upper, upper or "?")


class _Probe:
    def __init__(self, image: bytes):
        self.image = image
        self.size = len(image)

    # -- kallsyms_token_table ---------------------------------------------------------------
    def find_token_table(self) -> int:
        needle = b"".join(b"%c\0" % digit for digit in range(ord("0"), ord("9") + 1))
        avoid = (b":\0", b"\0\0", b"\0\1", b"\0\2", b"ASCII\0")
        candidates, clean = [], []
        position = -1
        while True:
            position = self.image.find(needle, position + 1)
            if position < 0:
                break
            tail = position + len(needle)
            if any(self.image[tail:tail + len(seq)] == seq for seq in avoid):
                continue
            candidates.append(position)
            if self.image[tail:tail + 1].isalnum():
                clean.append(position)
        if len(candidates) != 1 and len(clean) == 1:
            candidates = clean
        if len(candidates) != 1:
            raise KallsymsError("KALLSYMS_TOKEN_TABLE_CANDIDATES:%d" % len(candidates))
        position = candidates[0] - 1
        if self.image[position] != 0:
            raise KallsymsError("KALLSYMS_TOKEN_TABLE_ANCHOR")
        # Walk back over the tokens that precede the "0" token.
        for _ in range(ord("0")):
            for _chars in range(50):
                position -= 1
                if position < 0:
                    raise KallsymsError("KALLSYMS_TOKEN_TABLE_UNDERFLOW")
                byte = self.image[position]
                if byte == 0 or byte > ord("z"):
                    break
            else:
                raise KallsymsError("KALLSYMS_TOKEN_TABLE_TOKEN_TOO_LONG")
        position += 1
        return position + (-position % 4)

    def read_tokens(self, token_table: int) -> list[str]:
        tokens, position = [], token_table
        for _ in range(256):
            end = self.image.index(b"\0", position)
            tokens.append(self.image[position:end].decode("latin-1"))
            position = end + 1
        return tokens

    def find_token_index(self, token_table: int):
        entries, position = [], token_table
        for _ in range(256):
            entries.append(position - token_table)
            end = self.image.index(b"\0", position)
            if end - position > 50:
                raise KallsymsError("KALLSYMS_TOKEN_TOO_LONG")
            position = end + 1
        window = self.image[position:position + 256 * 2 + 256]
        for big_endian in (False, True):
            packed = struct.pack((">" if big_endian else "<") + "256H", *entries)
            found = window.find(packed)
            if found >= 0:
                start = position + found
                return start, start + 512, big_endian
        raise KallsymsError("KALLSYMS_TOKEN_INDEX_NOT_FOUND")

    # -- markers / names --------------------------------------------------------------------
    def find_markers(self, token_table: int):
        for element_size in (8, 4, 2):
            marker = {2: "H", 4: "I", 8: "Q"}[element_size]
            position = token_table
            for _ in range(32):
                position = self.image.rfind(b"\0" * element_size, 0, position)
                if position < 0:
                    break
                position -= position % element_size
                entries = struct.unpack_from("<4" + marker, self.image, position)
                if entries[0] != 0:
                    continue
                for index in range(1, len(entries)):
                    if (entries[index - 1] + 0x200 >= entries[index]
                            or entries[index - 1] + 0x40000 < entries[index]):
                        break
                else:
                    return element_size, position
        raise KallsymsError("KALLSYMS_MARKERS_NOT_FOUND")

    def find_names(self, markers: int, token_table: int, element_size: int) -> int:
        marker = {2: "H", 4: "I", 8: "Q"}[element_size]
        count = max(1, min(3000, (token_table - markers) // element_size))
        entries = list(struct.unpack_from("<%d%s" % (count, marker), self.image, markers))
        for index in range(1, len(entries)):
            if (entries[index - 1] + 0x200 >= entries[index]
                    or entries[index - 1] + 0x40000 < entries[index]):
                entries = entries[:index]
                break
        last = [entry for entry in entries if entry][-1]
        position = markers - last
        return position + (-position % element_size)

    def find_num_syms(self, names: int, markers: int, token_table: int, element_size: int):
        tokens = self.read_tokens(token_table)
        valid_types = "ABDRTVWGINPCSUuvw-?"
        marker = {2: "H", 4: "I", 8: "Q"}[element_size]
        explored: list[int] = []
        while True:
            if names + 1 >= self.size:
                raise KallsymsError("KALLSYMS_NAMES_UNDERFLOW")
            first_token = tokens[self.image[names + 1]]
            if not first_token:
                names -= 4
                continue
            head = first_token[0]
            if not (head.lower() in "uvw" and head in valid_types) and head.upper() not in valid_types:
                names -= 4
                continue
            span = markers - names
            for tail in range(len(explored), span + 1):
                byte = self.image[markers - tail]
                if byte & 0x80:
                    symbol_size = (byte & 0x7F | (self.image[markers - tail + 1] << 7)) + 2
                else:
                    symbol_size = byte + 1
                hop = tail - symbol_size
                if byte == 0:
                    explored.append(0 if tail <= 256 else -1)
                elif hop < 0 or explored[hop] == -1:
                    explored.append(-1)
                else:
                    explored.append(explored[hop] + 1)
            num_syms = explored[-1]
            if num_syms < 256:
                names -= 4
                continue
            encoded = struct.pack("<" + marker, num_syms) if element_size > 2 else None
            if encoded is None:
                raise KallsymsError("KALLSYMS_NUM_SYMS_ELEMENT_SIZE")
            needle = self.image.rfind(encoded, max(0, names - 256 - 20), names)
            if needle < 0:
                names -= 4
                continue
            return num_syms, names, needle

    def read_names(self, names: int, num_syms: int, tokens: list[str],
                   markers: tuple[int, int] | None = None) -> list[str]:
        out, position = [], names
        limit = markers[0] if markers is not None else self.size
        for index in range(num_syms):
            if markers is not None and index % 256 == 0:
                marker_at = markers[0] + index // 256 * markers[1]
                if int.from_bytes(self.image[marker_at:marker_at + markers[1]], "little") != position - names:
                    raise KallsymsError("KALLSYMS_NAMES_MARKER_MISMATCH:%d" % index)
            if position >= limit:
                raise KallsymsError("KALLSYMS_NAMES_CORRUPT")
            length = self.image[position]
            position += 1
            if length & 0x80:
                if position >= limit:
                    raise KallsymsError("KALLSYMS_NAMES_CORRUPT")
                length = (length & 0x7F) | (self.image[position] << 7)
                position += 1
            if not 0 < length < 0x4000 or position + length > limit:
                raise KallsymsError("KALLSYMS_NAMES_CORRUPT")
            name = "".join(tokens[self.image[position + index]] for index in range(length))
            position += length
            out.append(name)
        if markers is not None and (markers[0] != (position + 7) & ~7 or
                                    any(self.image[position:markers[0]])):
            raise KallsymsError("KALLSYMS_NAMES_MARKER_END_MISMATCH")
        return out

    # -- address table ----------------------------------------------------------------------
    def find_offsets(self, num_syms: int, token_index_end: int, element_size: int, token_table: int):
        """Locate ``kallsyms_offsets`` (or ``kallsyms_addresses``) and the relative base."""
        address_size = 8
        offset_size = min(4, element_size)
        align = 8
        # The u64 base is aligned independently of the s32 table. Subtracting the
        # table size from base_at consumes the end padding for odd symbol counts.
        offsets_at = token_index_end + (-token_index_end % align)
        end = offsets_at + num_syms * offset_size
        base_at = end + (-end % align)
        if base_at + address_size > self.size:
            raise KallsymsError("KALLSYMS_OFFSETS_OUT_OF_RANGE")
        if base_at <= 0:
            raise KallsymsError("KALLSYMS_RELATIVE_BASE_NOT_FOUND")
        relative_base = int.from_bytes(self.image[base_at:base_at + address_size], "little")
        if not (0xFFFF000000000000 <= relative_base < (1 << 64)):
            raise KallsymsError("KALLSYMS_RELATIVE_BASE_IMPLAUSIBLE:0x%X" % relative_base)
        if offsets_at < token_index_end or offsets_at >= base_at:
            raise KallsymsError("KALLSYMS_OFFSETS_OUT_OF_RANGE")
        return offsets_at, relative_base, True

    def find_offsets_legacy(self, num_syms: int, num_syms_at: int, element_size: int):
        """Kernel < 6.4: ``kallsyms_relative_base`` precedes ``kallsyms_num_syms``."""
        address_size = 8
        offset_size = min(4, element_size)
        # A complete zero base slot is data in a raw relocatable Image, not padding.
        base_at = num_syms_at - address_size
        if base_at <= 0:
            raise KallsymsError("KALLSYMS_RELATIVE_BASE_NOT_FOUND")
        relative_base = int.from_bytes(self.image[base_at:base_at + address_size], "little")
        if relative_base == 0:
            if num_syms_at % address_size or self.image[num_syms_at + 4:num_syms_at + 8] != b"\0" * 4:
                raise KallsymsError("KALLSYMS_NUM_SYMS_ALIGNMENT")
        elif not (0xFFFF000000000000 <= relative_base < (1 << 64)):
            raise KallsymsError("KALLSYMS_RELATIVE_BASE_IMPLAUSIBLE:0x%X" % relative_base)
        # Each label is eight-byte aligned; zero offsets are data, not padding.
        table_bytes = num_syms * offset_size
        offsets_at = base_at - (table_bytes + (-table_bytes % address_size))
        if offsets_at < 0 or offsets_at >= base_at:
            raise KallsymsError("KALLSYMS_OFFSETS_OUT_OF_RANGE")
        return offsets_at, relative_base, True

    def read_addresses(self, offsets_at: int, num_syms: int, relative_base: int, base_relative: bool):
        if base_relative:
            raw = struct.unpack_from("<%di" % num_syms, self.image, offsets_at)
            return [(relative_base + value) & ((1 << 64) - 1) for value in raw]
        raw = struct.unpack_from("<%dQ" % num_syms, self.image, offsets_at)
        return list(raw)


def release_of(image: bytes) -> str:
    """The ``Linux version`` release string carried by a raw Image."""
    match = re.search(rb"Linux version ([^ \n\0]+)", image)
    return match.group(1).decode("ascii", "replace") if match else ""
