# Pure root-key parser bounds review

Scope: only `parser_root_key_info` and its pure base62/string helpers in
`runtime/maplesu-sdk/sdk/src/module/module_append_package_parser.cpp`.
The production file was read, never changed by this review. No kernel execution,
permission changes, hiding behavior, network access or device operations were tested.

## Actual source and boundary

`run.py` copies the exact unnamed-namespace/base62 helpers and the exact public
function body into `actual_parser.cpp` under the C: output directory. It includes
the source's standard-library headers and excludes the unrelated emitter and
other functions. `fixture.cpp` includes this extracted source; it does not replace
the parser or helpers. No external API stub is required.

The six project-generated fixtures call the exact, separately extracted pure
`msu_sdk_bind_key` function from
`jailbreak-providers/root-loader/module/boot_sdk_protocol.h`. No other function
from that header is compiled. They start from a public deterministic 48-character
synthetic string and test `-134217728`, `-4096`, `-4`, `4`, `4096`, `134217728`.
There are no production credentials in this test or its receipts.

Other fixtures encode the same base62 rotation and two appended characters to
exercise integer and length boundaries. A test-only `operator new` fault models
allocation failure; it does not replace parsing. Each case runs in its own process.

## Original red evidence

The tested production source SHA-256 is
`1796be970cbfb83db66deaf5630e15b3f9f2b78b49c728688fda64f86007a2ff`.
The actual original code had **22 failures among 42 cases**.
`red-receipt.json` records `passed=false`, `testedCodeHadFailures=true` and
`failures=22`. The runner's exit zero with `--expect-red` means the requested
failing baseline was observed; it does not mean the tested code passed.

- Lengths 254 and 255: ASan stack-buffer-overflow while reading the fixed array
  after both appended bytes remove its terminating NUL.
- Lengths 256, 4096 and 65536: fortified runtime terminates with
  `*** buffer overflow detected ***` during the fixed-array copy (exit 134).
- Prefix counts 0, 1 and 61: a `std::exception` escapes the public `void` function.
  Counts 0/1 reach erase on an empty decoded tail; count 61 exceeds a 16-byte
  input plus two appended characters and underflows the substring position.
- Six invalid characters in actually decoded prefix/tail positions produce
  nonzero incorrect results instead of the existing zero failure value.
- Positive/negative `int` overflow, `UINT64_MAX` in either sign, base62 arithmetic
  overflow and a 20-digit value yield narrowed or wrapped nonzero results.
- Negating the signed conversion of magnitude `2^63` produces a UBSan error.
- Injected allocation failure lets `std::bad_alloc` escape the public function.

All raw output, extracted source, executable and complete command receipts remain
under `C:/Temp/maplesu-root-key-parser-red-20261115/`.

## Minimal proposal and candidate verification

`parser-bounds.patch` applies to the exact production hash above. It changes only
the pure helpers and public parser: checked base62 conversion, rejection of invalid
encoded characters, `std::string` append instead of the stack array, prefix/tail
bounds, `int` magnitude limits with an explicit `INT_MIN` case, and containment of
standard-library exceptions with `out_value=0`.

Only the encoded prefix and selected tail are character-checked. Unused characters
are not restricted; any nonzero final decoded sign keeps its original positive
meaning. No authentication or new key format is introduced. The existing unrelated
metadata-v2 range checks are outside the patch.

The C:-only candidate source is
`C:/Temp/maplesu-root-key-parser-proposal-20261115.cpp`.
The same **42 cases pass 42/42**, with no ASan or UBSan diagnostics, under
`C:/Temp/maplesu-root-key-parser-proposal-green-20261115/`.
`proposal-green-receipt.json` records `passed=true`,
`testedCodeHadFailures=false` and `failures=0`.

Legal-behavior checks pass on both original and candidate code: all six actual
project-binder fixtures, `INT_MAX`, `INT_MIN`, negative `INT_MAX`, positive/negative
zero, a leading-zero magnitude, a nonzero sign other than `1`, a 253-byte input,
and an invalid character deliberately placed in an unused body position.
The original parser already returns zero for null/empty/short input and prefix
count 2; these remain zero. Longer valid inputs now parse without corrupting memory.

The fixed Docker image is
`sha256:272ada1e61c525116032246df2a790d1ccec1804b7a9e0b09203c5e650dcb78e`.
Both runs use C++20, `-O1 -g -fsanitize=address,undefined`,
`-fno-sanitize-recover=all`, frame pointers and separate case processes.

## Reproduction

Run from the repository root with a working Python and Docker installation:

```powershell
py runtime/maplesu-sdk/sdk/tools/tests/root_key_parser/run.py --source C:/Temp/maplesu-root-key-parser-red-20261115/source_snapshot.cpp --out C:/Temp/root-key-parser-original --expect-red
py runtime/maplesu-sdk/sdk/tools/tests/root_key_parser/run.py --source C:/Temp/maplesu-root-key-parser-proposal-20261115.cpp --out C:/Temp/root-key-parser-candidate
```

After the main agent reviews/applies the proposal, omit `--source` and `--expect-red`
to test the current production parser. All generated output is restricted to C:.
These host tests establish pure text-parsing behavior only.
