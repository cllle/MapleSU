"""Run the real module policy CLI with libsepol and redirected selinuxfs only."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
IMAGE = "sha256:272ada1e61c525116032246df2a790d1ccec1804b7a9e0b09203c5e650dcb78e"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rules", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    rules = args.rules.resolve()
    (out / "official.rule").write_bytes(rules.read_bytes())
    (out / "libsepol.lsan").write_text("leak:policydb_role_cache\n", encoding="utf-8")
    inputs = [ROOT / path for path in (
        "module-policy/src/main.cpp", "module-policy/src/shared_file_contract.h",
        "module-policy/derived/sepolicy.cpp", "module-policy/derived/policy.hpp",
        "module-policy/tests/module_compat_tests.cpp", "module-policy/tests/module_compat_offline.py",
        "runtime/ultra/policy/module_policy_config.cpp", "runtime/ultra/policy/module_policy_config.h",
        "module-policy/src/base.hpp",
        "evidence/zygisk-startup-window-20261106/product-v5-policy/actual-policy.bin")]
    inputs += sorted((ROOT / ".deps/selinux/libsepol").rglob("*.h"))
    inputs += sorted((ROOT / ".deps/selinux/libsepol/src").glob("*.c"))
    inputs.append(rules)
    hashes = {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs}
    include = ("-I/source/module-policy/derived -I/source/module-policy/src "
               "-I/source/runtime/ultra/policy -I/source/third_party/kernelsu/applied "
               "-I/source/.deps/selinux/libsepol/include ")
    script = ("set -eu\nmkdir -p /output/build /output/fixture /data/adb\n"
              "chmod 700 /data/adb\ncd /output/build\n"
              "cc -D_GNU_SOURCE -DHAVE_REALLOCARRAY -Wno-unused-result "
              "-I/source/.deps/selinux/libsepol/include -I/source/.deps/selinux/libsepol/src "
              "-c /source/.deps/selinux/libsepol/src/*.c\nar rcs libsepol.a *.o\n"
              "c++ -std=c++17 -D_GNU_SOURCE -UNDEBUG -Wall -Wextra -Werror "
              "-Wno-calloc-transposed-args -Wno-unused-function "
              "-fsanitize=address,undefined -fno-omit-frame-pointer " + include +
              "/source/module-policy/tests/module_compat_tests.cpp /source/module-policy/derived/sepolicy.cpp "
              "/source/runtime/ultra/policy/module_policy_config.cpp /output/build/libsepol.a "
              "-Wl,--wrap=open -Wl,--wrap=write -Wl,--wrap=close -o /output/module-probe\n"
              "LSAN_OPTIONS=suppressions=/output/libsepol.lsan /output/module-probe "
              "/source/evidence/zygisk-startup-window-20261106/product-v5-policy/actual-policy.bin "
              "/output/fixture /output/official.rule\n")
    (out / "run.sh").write_text(script, encoding="utf-8", newline="\n")
    command = ["docker", "run", "--rm", "--pull", "never", "--network", "none",
               "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
               "--mount", f"type=bind,source={ROOT},target=/source,readonly",
               "--mount", f"type=bind,source={out},target=/output", IMAGE, "sh", "/output/run.sh"]
    result = subprocess.run(command, capture_output=True, text=True, timeout=180)
    (out / "stdout.log").write_text(result.stdout, encoding="utf-8")
    (out / "stderr.log").write_text(result.stderr, encoding="utf-8")
    checks = [json.loads(line) for line in result.stdout.splitlines() if line.startswith('{"test":')]
    record = dict(exitCode=result.returncode, image=IMAGE, sourceSha256=hashes,
                  sourceInputsStable=all(hashlib.sha256(path.read_bytes()).hexdigest() == hashes[str(path)] for path in inputs),
                  checks=checks, deviceAccess=False, simulatedBoundaries=["selinuxfs"],
                  sanitizers=["ASan", "UBSan"], libsepolSanitized=False,
                  leakSuppression="existing libsepol policydb_role_cache", liveDeviceWrites=0,
                  fixtureSha256={path.name: hashlib.sha256(path.read_bytes()).hexdigest()
                                 for path in (out / "fixture").glob("*.policy")})
    record["passed"] = result.returncode == 0 and len(checks) >= 14 and record["sourceInputsStable"]
    (out / "RESULTS.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: record[key] for key in ("passed", "exitCode", "sourceInputsStable", "checks")}, indent=2))
    if not record["passed"]:
        print(result.stdout[-5000:] + result.stderr[-5000:])
    raise SystemExit(0 if record["passed"] else 1)


if __name__ == "__main__":
    main()
