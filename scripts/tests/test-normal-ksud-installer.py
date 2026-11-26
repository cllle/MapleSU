"""Execute both pinned ksud installer shells in disposable host fixtures."""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess
import tempfile
import zipfile
from selected_ksud_postimages import verify_source_postimages

ROOT = Path(os.environ.get("MAPLESU_REMOVAL_SOURCE", Path(__file__).resolve().parents[2])).resolve()


def bash_path():
    if os.name == "nt":
        path = Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/bin/bash.exe"
        if not path.is_file():
            raise RuntimeError("Git Bash is required on Windows")
        return str(path)
    path = shutil.which("bash")
    if not path:
        raise RuntimeError("bash is required")
    return path


def shpath(path):
    value = path.resolve().as_posix()
    return "/" + value[0].lower() + value[2:] if os.name == "nt" else value


def run_fixture(tree, files, out, bash, *, meta=None):
    source = ROOT / f"third_party/kernelsu/{tree}/userspace/ksud/src/installer.sh"
    with tempfile.TemporaryDirectory(prefix="installer-", dir=out) as temp:
        base = Path(temp)
        path = shpath(base)
        archive = base / "module.zip"
        entries = {"module.prop": "id=fixture\nname=Fixture\nauthor=Fixture\nversionCode=2\n",
                   "system/bin/example": "payload", **files}
        with zipfile.ZipFile(archive, "w") as zip:
            for name, data in entries.items():
                zip.writestr(name, data)
        staged = base / "adb/modules_update/fixture"
        staged.mkdir(parents=True)
        with zipfile.ZipFile(archive) as zip:
            zip.extractall(staged)
        live = base / "adb/modules/fixture"
        live.mkdir(parents=True)
        (live / "module.prop").write_text("id=fixture\nversionCode=1\n")
        for marker in ["disable", "remove"]:
            (live / marker).touch()
        body = source.read_text(encoding="utf-8") + f'''
NVBASE='{path}/adb'
TMPDIR='{path}/tmp'
ZIPFILE='{path}/module.zip'
TRACE='{path}/trace'
export NVBASE TMPDIR ZIPFILE TRACE
mount_partitions() {{ :; }}
api_level_arch_detect() {{ API=36; ARCH=arm64; ABI=arm64-v8a; IS64BIT=true; }}
dos2unix() {{ tr -d '\\r'; }}
chown() {{ printf 'chown:%s\\n' "$*" >> "$TRACE"; }}
chcon() {{ printf 'chcon:%s\\n' "$*" >> "$TRACE"; }}
chmod() {{ printf 'chmod:%s\\n' "$*" >> "$TRACE"; command chmod "$@"; }}
'''
        body += (meta if meta is not None else "install_module\n") + "\nexit 0\n"
        script = base / "run.sh"
        script.write_text(body, encoding="utf-8", newline="\n")
        env = {**os.environ, "BOOTMODE": "true", "KSU": "true", "KSU_MODULE": "fixture",
               "KSU_VER": "PINNED", "KSU_VER_CODE": "1", "KSU_KERNEL_VER_CODE": "1",
               "KSU_UAPI_VER": "7", "KSU_RUNTIME_MODE": "LKM", "ASH_STANDALONE": "1",
               "PATH": "/usr/bin:/bin", "MSYS_NO_PATHCONV": "1"}
        process = subprocess.run([bash, "run.sh"], cwd=base, env=env, capture_output=True,
                                 text=True, encoding="utf-8", errors="replace", timeout=20)
        def entries_of(directory):
            return sorted(str(p.relative_to(directory)).replace("\\", "/") for p in directory.rglob("*") if p.is_file())
        result = {"code": process.returncode, "staged": entries_of(staged), "live": entries_of(live),
                  "liveProp": (live / "module.prop").read_text(),
                  "hot": (staged / ".ksu_hot_install").read_text() if (staged / ".ksu_hot_install").exists() else None,
                  "trace": (base / "trace").read_text().replace(path, "FIXTURE") if (base / "trace").exists() else "",
                  "out": process.stdout.replace(path, "FIXTURE"), "err": process.stderr.replace(path, "FIXTURE")}
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    bash = bash_path()
    cases = [
        ("default-modern", {}, None, 0),
        ("customize-environment", {"customize.sh": '''
test "$KSU" = true && test "$KSU_MODULE" = fixture || exit 41
test "$KSU_HAS_HOT_INSTALL" = true && test "$BOOTMODE" = true || exit 42
test "$MAGISK_VER" = 25.2 && test "$MAGISK_VER_CODE" = 25200 || exit 43
test "$KSU_VER" = PINNED && test "$KSU_UAPI_VER" = 7 && test "$KSU_RUNTIME_MODE" = LKM || exit 44
echo customize >> "$TRACE"
'''}, None, 0),
        ("skip-unzip", {"customize.sh": '''SKIPUNZIP=1
unzip -o "$ZIPFILE" module.prop system/bin/example -d "$MODPATH"
'''}, None, 0),
        ("abort-cleanup", {"customize.sh": "abort fixture-failure\n"}, None, 1),
        ("raw-exit-no-cleanup", {"customize.sh": "MODULE_HOT_INSTALL_REQUEST=true\nexit 42\n"}, None, 42),
        ("legacy-installer", {"install.sh": '''
SKIPMOUNT=true
PROPFILE=true
POSTFSDATA=true
LATESTARTSERVICE=true
print_modname() { :; }
on_install() { echo legacy >> "$TRACE"; }
set_permissions() { echo legacy-permissions >> "$TRACE"; }
''', "common/system.prop": "fixture.value=1\n", "common/post-fs-data.sh": ":\n",
         "common/service.sh": ":\n", "uninstall.sh": ":\n"}, None, 0),
        ("hot-request", {"customize.sh": "MODULE_HOT_INSTALL_REQUEST=true\nMODULE_HOT_RUN_SCRIPT=service.sh\n",
                         "service.sh": ":\n"}, None, 0),
        ("shipped-hot-marker-rejected", {".ksu_hot_install": "service.sh\n"}, None, 0),
        ("metainstall-wrap", {"customize.sh": "echo customize >> \"$TRACE\"\n"},
         "echo meta-before >> \"$TRACE\"\ninstall_module\necho meta-after >> \"$TRACE\"\n", 0),
        ("metainstall-exit-failure", {}, "exit 29\n", 29),
    ]
    records = []
    for name, files, meta, code in cases:
        values = {tree: run_fixture(tree, files, out, bash, meta=meta) for tree in ["upstream", "applied"]}
        assert values["upstream"] == values["applied"], (name, values)
        result = values["applied"]
        assert result["code"] == code, (name, result)
        if name == "abort-cleanup":
            assert result["staged"] == [] and "versionCode=1" in result["liveProp"]
        if name == "raw-exit-no-cleanup":
            assert "module.prop" in result["staged"] and "system/bin/example" in result["staged"]
            assert "versionCode=1" in result["liveProp"]
            assert result["hot"] is None
        if name == "metainstall-exit-failure":
            assert "module.prop" in result["staged"] and "versionCode=1" in result["liveProp"]
        if name == "legacy-installer":
            assert all(p in result["staged"] for p in ["skip_mount", "system.prop", "service.sh", "post-fs-data.sh", "uninstall.sh"])
        if name in ["default-modern", "skip-unzip"]:
            assert "system/bin/example" in result["staged"]
        if name == "default-modern":
            assert "chmod:0644 FIXTURE/adb/modules_update/fixture/module.prop" in result["trace"]
            assert "chmod:0755 FIXTURE/adb/modules_update/fixture/system/bin/example" in result["trace"]
            assert "chcon:u:object_r:system_file:s0 FIXTURE/adb/modules_update/fixture/system/bin/example" in result["trace"]
        if name == "hot-request":
            assert result["hot"] == "service.sh\n"
        if name == "shipped-hot-marker-rejected":
            assert result["hot"] is None
        if code == 0:
            assert "update" in result["live"] and "disable" not in result["live"] and "remove" not in result["live"]
        records.append({"case": name, "identical": True, **values})
        print(name, "PASS", "exit=" + str(code))
    source_postimages = verify_source_postimages(ROOT, out)
    (out / "verification.json").write_text(json.dumps({"scope": "ksud installer production shells with host platform substitutes",
        "passed": True, "deviceAccess": False, "fullKsudExecuted": False,
        "platformSubstitutes": ["fixture data/tmp paths", "mount_partitions no-op", "fixed Android API/ABI",
                               "recorded chown/chcon calls; no host SELinux labels"],
        "staticScope": "Metamodule safety gates, hot-install swap/apply, and boot lifecycle are exact selected-patch postimage checks, not Android execution",
        "cases": records, "sourcePostimages": source_postimages}, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
