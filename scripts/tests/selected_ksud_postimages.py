"""Replay only the installer test's selected source closure in a private tree."""
from pathlib import Path
import hashlib
import importlib
import json
import re
import shutil
import subprocess
import sys
import tempfile

BASE_FILES = ["hot_install.rs", "metamodule.rs", "installer.sh", "init_event.rs", "defs.rs",
              "resetprop.rs", "sepolicy.rs", "preinit.rs", "module_config.rs", "module.rs"]
NEW_FILES = ["module_install_transaction.rs", "module_removal_transaction.rs"]
SOURCE_PREFIX = "userspace/ksud/src/"
SELECTED_SUFFIX = ["0026-module-install-transaction.patch", "0027-module-removal-transaction.patch"]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_source_postimages(repo, out):
    vendor = repo / "third_party/kernelsu"
    scripts = vendor / "scripts"
    # Import the actual selected patch definitions without writing vendor bytecode.
    sys.dont_write_bytecode = True
    sys.path.insert(0, str(scripts))
    selected = importlib.import_module("gen_patches")
    assert Path(selected.__file__).resolve() == (scripts / "gen_patches.py").resolve()
    materialized = selected.C.read_series(vendor / "patches/7k/series")
    specs = selected.PATCHES
    assert [spec["name"] for spec in specs] == materialized + SELECTED_SUFFIX, (
        "Selected patch order changed; review this narrow replay contract")
    relatives = [SOURCE_PREFIX + name for name in BASE_FILES + NEW_FILES]
    materialized_paths = [vendor / "patches/7k" / name for name in materialized]
    patches = []
    for path in materialized_paths:
        touched = set(re.findall(r"^diff --git a/\S+ b/(\S+)$",
                                 path.read_text(encoding="utf-8"), re.MULTILINE))
        if touched.intersection(relatives):
            patches.append(path)
    imported = sorted({Path(module.__file__).resolve() for module in list(sys.modules.values())
                       if getattr(module, "__file__", None)
                       and Path(module.__file__).resolve().parent == scripts.resolve()})
    inputs = ([vendor / "upstream" / SOURCE_PREFIX / name for name in BASE_FILES]
              + [vendor / "applied" / rel for rel in relatives]
              + [vendor / "patches/7k/series", *materialized_paths, *imported])
    before = [{"path": str(path), "sha256": sha(path)} for path in inputs]
    (out / "source-postimage-inputs.json").write_text(json.dumps(before, indent=2), encoding="utf-8")
    commands = []

    def git(stage, *args):
        argv = ["git", "-c", "core.autocrlf=false", "-C", str(stage), *args]
        process = subprocess.run(argv, capture_output=True, text=True,
                                 encoding="utf-8", errors="replace", timeout=30)
        commands.append({"argv": argv, "exitCode": process.returncode,
                         "stdout": process.stdout, "stderr": process.stderr})
        assert process.returncode == 0, commands[-1]

    with tempfile.TemporaryDirectory(prefix="selected-ksud-postimage-", dir=out) as temporary:
        stage = Path(temporary)
        for name in BASE_FILES:
            target = stage / SOURCE_PREFIX / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(vendor / "upstream" / SOURCE_PREFIX / name, target)
        git(stage, "init", "--quiet")
        includes = ["--include=" + rel for rel in relatives]
        for path in patches:
            git(stage, "apply", "--check", "--whitespace=error-all", *includes, str(path))
            git(stage, "apply", "--whitespace=error-all", *includes, str(path))
        tree = selected.Tree(stage)
        source = selected.Tree(vendor / "upstream")
        for spec in specs[len(materialized):]:
            changed = spec["apply"](tree, source)
            assert set(changed).issubset(relatives), (spec["name"], changed)
        rows = []
        for rel in relatives:
            expected = stage / rel
            actual = vendor / "applied" / rel
            saved = out / "source-postimage-files" / rel
            saved.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(expected, saved)
            assert expected.read_bytes() == actual.read_bytes(), (
                "Selected source postimage mismatch", rel, sha(expected), sha(actual))
            upstream = vendor / "upstream" / rel
            rows.append({"path": rel, "upstreamSha256": sha(upstream) if upstream.is_file() else None,
                         "expectedSha256": sha(expected), "appliedSha256": sha(actual),
                         "exactPostimage": True})
    assert all(sha(Path(row["path"])) == row["sha256"] for row in before), "Source inputs changed during replay"
    report = {"passed": True, "scope": "Ten original installer-related sources plus two selected transaction files; private targeted patch replay and full-byte postimage equality, no Rust or Android execution",
              "upstreamCommit": selected.C.UPSTREAM_COMMIT, "materializedSeries": materialized,
              "targetedMaterializedPatches": [path.name for path in patches],
              "selectedSourceDefinitionSuffix": SELECTED_SUFFIX, "files": rows,
              "inputs": before, "inputsStable": True, "commands": commands}
    (out / "source-postimages.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report
