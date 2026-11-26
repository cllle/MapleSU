#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Check the architecture rules that docs/ARCHITECTURE.md describes.

    python tools/check_architecture.py            run every rule; exit status 1 on a violation
    python tools/check_architecture.py --report   also show how the Manager's sources relate to the
                                                  pinned 7kimisu snapshot
    python tools/check_architecture.py --json     machine-readable result

The rules only read the repository. They need neither Gradle nor a device, and they work on the private
checkout as well as on the public candidate tree: paths under the prefixes that
release/source-policy.json excludes may be absent.

When a rule fails on a change you intend to make, update the rule and docs/ARCHITECTURE.md in the same
commit. The rules exist so that the documented boundaries cannot drift unnoticed.
"""
from __future__ import annotations

import argparse
import ast
import hashlib
import json
import os
import posixpath
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath
from typing import Callable, Iterable, Iterator

ROOT = Path(__file__).resolve().parents[1]

# --------------------------------------------------------------------------------------------------
# What the rules protect. Change these together with docs/ARCHITECTURE.md.
# --------------------------------------------------------------------------------------------------

#: Gradle module -> the project modules it may depend on. Any other edge is a violation.
EXPECTED_EDGES: dict[str, set[str]] = {
    ":manager": {":image-core", ":installer-core", ":libsu-service"},
    ":maplesu-consumer": {":maplesu-client"},
}
KNOWN_MODULES = {":image-core", ":installer-core", ":manager", ":libsu-service", ":maplesu-client", ":maplesu-consumer"}

#: Host-testable modules: the JDK only, plus their own packages.
PURE_JAVA_MODULES: dict[str, tuple[str, ...]] = {
    "image-core": ("dev.maplesu.image.",),
    "installer-core": ("dev.maplesu.installer.",),
}

#: The Binder contract the client shares with the Manager, declared in both trees (see aidl_parity).
SHARED_BINDER_TYPES = {
    "dev.maplesu.manager.runtime.INativeSuDiscovery",
    "dev.maplesu.manager.runtime.INativeSuService",
    "dev.maplesu.manager.runtime.INativeSuSession",
}
#: Test frameworks may be imported from test source sets of any module.
TEST_FRAMEWORKS = ("org.junit.", "org.hamcrest.", "androidx.test.", "kotlin.test.", "com.google.common.truth.")
#: Modules that integrating apps build against: they must not reach into Manager internals.
APP_FACING_MODULES: dict[str, tuple[str, ...]] = {
    "client": ("android.", "androidx.annotation.", "java.", "javax.", "kotlin.", "kotlinx.", "dev.maplesu.client."),
    "consumer": ("android.", "androidx.", "java.", "javax.", "kotlin.", "kotlinx.", "dev.maplesu.client.",
                 "dev.maplesu.consumer."),
}

#: Source roots compiled into the Manager (manager/build.gradle.kts adds the last four).
MANAGER_SOURCE_ROOTS = (
    "manager/src/main/java",
    "manager/src/ksu/java",
    "jailbreak-providers/android/src/main/java",
    "jailbreak-providers/maplesu-93260/src/main/kotlin",
    "jailbreak-providers/ghostlock/profile-core/src/main/kotlin",
    "jailbreak-providers/ghostlock/extractor/src/main/kotlin",
)

#: The only files that load a native library or declare JNI entry points.
NATIVE_SITES: dict[str, str] = {
    "manager/src/ksu/java/dev/maplesu/manager/ksu/KsuNatives.kt": "Normal: KernelSU supercall JNI",
    "manager/src/main/java/dev/maplesu/manager/runtime/NativeSu.kt": "native SU client",
    "manager/src/main/java/dev/maplesu/manager/runtime/NativeSessionProcess.kt": "supervised native sessions",
    "manager/src/main/java/dev/maplesu/manager/runtime/NativeSuBootstrap.java": "root-side bootstrap entry",
    "manager/src/main/java/dev/maplesu/manager/runtime/ultra/bridge/NativeBridge.java":
        "Ultra: the single native SDK call surface",
    "manager/src/main/java/dev/maplesu/manager/installer/BlockPartitionAccess.kt": "installer block-device access",
    "manager/src/main/java/dev/maplesu/manager/liteweb/daemon/UltraSdkSuAuthRevoke.kt":
        "standalone web daemon: loads the SDK by absolute path in its own process",
}

#: Documents that must describe the repository layout.
ARCHITECTURE_DOC = "docs/ARCHITECTURE.md"
DOCS_INDEX = "docs/README.md"
#: Roots a short path such as ``workmode/detect/DetectionRules.kt`` may be relative to.
DOC_PATH_ROOTS = (
    "",
    "manager/src/main/java/dev/maplesu/manager/",
    "manager/src/main/java/com/maplesu/core/",
    "manager/src/ksu/java/dev/maplesu/manager/",
    "client/src/main/java/dev/maplesu/client/",
)

PINNED_7K_MANIFEST = "third_party/7kimisu/source-manifest.json"
ORIGIN_SOURCE_ROOTS = ("manager/src/main/java/", "manager/src/ksu/java/")

#: Top-level directories that need no README.md of their own: build plumbing, the policy file and the retained
#: private inputs. Directories whose name starts with a dot are exempt as well.
README_EXEMPT = {"gradle", "release", "evidence", "work"}

#: What counts as a maintainer's machine. Tooling finds the SDK, NDK, JDK, Python and adb through the environment
#: (tools/native_toolchain.py); nothing in the tree may carry one person's workspace, user profile or device.
LOCAL_PATH_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("a maintainer's workspace directory", re.compile(r"Maple_(?:Env|Workflow)")),
    ("a Windows user profile", re.compile(
        r"[A-Za-z]:[\\/]+Users[\\/]+(?!<|\$|%|\*|\{|(?:Public|Default|Administrator|you|name|user)\b)[^\\/\s'\"`]+", re.IGNORECASE)),
    ("a Unix home directory", re.compile(r"(?<![\w.])/(?:Users|home)/(?!runner\b|<|\$|\{|(?:user|you|name)\b)[A-Za-z0-9._-]+/")),
    ("a pinned JDK or Python install directory", re.compile(r"jdk-\d+\.\d+\.\d+\.\d+-hotspot|[A-Za-z]:[\\/]+Python3\d+", re.IGNORECASE)),
    ("a device serial number", re.compile(r"\ba261972a\b")),
)
#: Retained inputs and receipts record the machine that produced them (native-inputs/ keeps them byte for byte; see
#: native-inputs/README.md), and pinned upstream trees are someone else's code; neither is edited to hide where it
#: came from.
LOCAL_PATH_EXEMPT_PREFIXES = (".analysis/", ".deps/", ".handoff/", "evidence/", "native-inputs/", "work/",
                              "third_party/rezygisk/", "third_party/kernelsu/applied/")
LOCAL_PATH_EXEMPT_DIRECTORIES = ("upstream", "maplesu-sdk")
#: Files that name these strings in order to refuse them.
LOCAL_PATH_DETECTORS = ("tools/check_architecture.py", "tools/tests/test_check_architecture.py",
                        "zygisk/package.py", "zygisk/tests/host_checks.py")

#: These exact retained receipts and IR headers describe their original producer.
#: JSON is provenance data; IR is still scanned after removing only its source metadata.
LOCAL_PATH_RETAINED_PROVENANCE = {
    "image-core/src/test/resources/zstd-compressed/receipt.json":
        "5f48f34cda78416c134807abfa37092d6bda9c79604624aff15dbb26ef50c10f",
    "manager/src/test/resources/ultrakernel/case04-saved-id-frame/SOURCE.json":
        "e7f20f3b2c0231fcb691beb058a009f875858e1028f6ce32dcbf897bffc47148",
    "runtime/ultra/authorization/su-isolation/stat-access/m084.patched.ll":
        "6d5dfab0be8dfbfa0306124a36e7fa4ae077771bbf3785beac7a66eb07cb8ae1",
    "runtime/ultra/authorization/su-isolation/stat-access/m085.patched.ll":
        "ef195a242a8f76d95d7b1c52edcd42db26ee6af789812b37c4f51581889aeea3",
    "runtime/ultra/boot/bootstrap-readiness/m053.patched.ll":
        "7876faab55a70ecbb247e5d05b4188bd536dc2aed0d520f30069a6b5247638ed",
    "runtime/ultra/policy/hook-bundles/policy-bundles.json":
        "32fd24fea891c4a65cb5f8a664eccc9d8d00f65120106ed3533396a80059f29c",
}

#: The SDK record that pins part of the Ultra toolchain, and the scripts whose SHA-256 it (or the delivery and
#: maintenance records under runtime/maplesu-sdk/maintenance/, formerly under work/) records. Renaming, moving or
#: editing one of them without re-pinning the SDK breaks the provenance chain without any compile error. When a new
#: SDK generation pins another script, add it here.
SDK_RECORD = "runtime/maplesu-sdk/SDK-RELEASE.json"
SDK_MAINTENANCE_RECORDS = ("runtime/maplesu-sdk/maintenance", "work")
#: Pinned scripts edited after the SDK recorded them, with the digest of the one accepted edit. 4a93745 moved the
#: 505 baseline archive from evidence/pro-sdk-505/sdk/ to runtime/maplesu-sdk/maintenance/baseline/ and pointed
#: these two repair scripts at it; SDK-RELEASE.json still records the digests they had when the repairs ran.
#: Re-pinning them in the SDK records makes these entries redundant; any other edit still fails the rule.
REPOINTED_SCRIPT_DIGESTS = {
    "scripts/repair-ultra-boot-lifecycle.py": "973695e0e98d42eb5864c5edd5ba1bf0d8c516098df8ce814086ba4e93aaa531",
    "scripts/repair-ultra-environment-state.py": "14ecb137af7d40bf5ea2e9440337df44c32f3a03be9802d48d6b2fced00e703b",
}
PINNED_SCRIPT_DIGESTS = (
    "scripts/build-ultra-policy-entry.py",
    "scripts/build-ultra-policy-bundles.py",
    "scripts/build-ultra-policy-compatibility.py",
    "scripts/build-ultra-policy-config-declarations.py",
    "scripts/build-ultra-policy-config-equivalence.py",
    "scripts/policy_backend_identity.py",
    "scripts/policy_config_declarations.py",
    "scripts/rebind-ultra-policy-backend.py",
    "scripts/repair-ultra-boot-lifecycle.py",
    "scripts/repair-ultra-environment-state.py",
    "scripts/repair-ultra-environment-upgrade.py",
    "scripts/repair-ultra-request-context.py",
    "scripts/repair-ultra-request-reply.py",
)

# --------------------------------------------------------------------------------------------------
# Repository access
# --------------------------------------------------------------------------------------------------


def list_files(root: Path) -> list[str]:
    """Repository-relative POSIX paths: Git's view when available, else a directory walk."""
    names: set[str] = set()
    if (root / ".git").exists():
        try:
            raw = subprocess.check_output(
                ["git", "-C", str(root), "ls-files", "-z", "--cached", "--others", "--exclude-standard"])
            names = {p.decode("utf-8") for p in raw.split(b"\0") if p}
        except (OSError, subprocess.CalledProcessError):
            names = set()
    if not names:
        for base, dirs, files in os.walk(root):
            dirs[:] = [d for d in dirs if d not in {
                ".git", ".gradle", ".kotlin", ".cxx", "build", "target", "__pycache__", "node_modules"}]
            for file in files:
                names.add(Path(base, file).relative_to(root).as_posix())
    return sorted(name for name in names if (root / name).is_file())


class Repo:
    def __init__(self, root: Path):
        self.root = root
        self.files = list_files(root)
        self.file_set = set(self.files)
        self.dirs = {parent.as_posix() for name in self.files for parent in PurePosixPath(name).parents}
        self._excluded: tuple[str, ...] | None = None

    def has(self, rel: str) -> bool:
        return rel in self.file_set or rel.rstrip("/") in self.dirs

    def data(self, rel: str) -> bytes:
        return (self.root / rel).read_bytes()

    def text(self, rel: str) -> str:
        return self.data(rel).decode("utf-8-sig", errors="replace")

    def under(self, prefix: str, suffixes: tuple[str, ...] = ()) -> Iterator[str]:
        prefix = prefix.rstrip("/") + "/"
        for name in self.files:
            if name.startswith(prefix) and (not suffixes or name.endswith(suffixes)):
                yield name

    @property
    def excluded_prefixes(self) -> tuple[str, ...]:
        """Private-checkout paths that the public candidate omits (release/source-policy.json)."""
        if self._excluded is None:
            self._excluded = ()
            if self.has("release/source-policy.json"):
                try:
                    self._excluded = tuple(json.loads(self.text("release/source-policy.json")).get("excluded_prefixes", []))
                except ValueError:
                    pass
        return self._excluded

    def is_excluded(self, rel: str) -> bool:
        """True when ``rel`` (a file or a directory) lies under a prefix the public candidate omits."""
        forms = (rel, rel.rstrip("/") + "/")
        return any(form.startswith(prefix) for prefix in self.excluded_prefixes for form in forms)


@dataclass
class Result:
    rule: str
    title: str
    problems: list[str] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)
    skipped: str = ""

    @property
    def passed(self) -> bool:
        return not self.problems


IMPORT = re.compile(r"^\s*import\s+(?:static\s+)?([A-Za-z_][\w.]*)", re.MULTILINE)
AIDL_IMPORT = re.compile(r"^\s*import\s+([\w.]+)\s*;", re.MULTILINE)


def normalized_sha256(data: bytes) -> str:
    return hashlib.sha256(data.replace(b"\r\n", b"\n")).hexdigest()


# --------------------------------------------------------------------------------------------------
# Rules
# --------------------------------------------------------------------------------------------------


def rule_gradle_edges(repo: Repo) -> Result:
    result = Result("gradle-edges", "Gradle modules depend only on the documented modules")
    if not repo.has("settings.gradle.kts"):
        result.skipped = "no settings.gradle.kts"
        return result
    settings = repo.text("settings.gradle.kts")
    modules: set[str] = set()
    for match in re.finditer(r"include\(([^)]*)\)", settings):
        modules |= set(re.findall(r'"(:[^"]+)"', match.group(1)))
    directories = {name: name[1:] for name in modules}
    for name, directory in re.findall(r'project\("(:[^"]+)"\)\.projectDir\s*=\s*file\("([^"]+)"\)', settings):
        directories[name] = directory
    for name in sorted(modules - KNOWN_MODULES):
        result.problems.append(f"{name}: module is not documented; add it to docs/ARCHITECTURE.md and KNOWN_MODULES")
    for name in sorted(modules):
        build = f"{directories[name]}/build.gradle.kts"
        if not repo.has(build):
            continue
        found = set(re.findall(r'project\(\s*"(:[^"]+)"\s*\)', repo.text(build)))
        allowed = EXPECTED_EDGES.get(name, set())
        for dependency in sorted(found - allowed):
            result.problems.append(f"{name} -> {dependency}: dependency is not allowed ({build})")
        for dependency in sorted(allowed - found):
            result.notes.append(f"{name} -> {dependency}: documented but not declared")
    return result


def imports_outside(repo: Repo, files: Iterable[str], allowed: tuple[str, ...], exceptions: set[str] = frozenset()
                    ) -> Iterator[str]:
    for name in files:
        text = repo.text(name)
        found = AIDL_IMPORT.findall(text) if name.endswith(".aidl") else IMPORT.findall(text)
        in_tests = "/src/test/" in name or "/src/androidTest/" in name
        permitted = allowed + TEST_FRAMEWORKS if in_tests else allowed
        for target in found:
            if target in exceptions or target.startswith(permitted):
                continue
            yield f"{name}: imports {target}"


def rule_pure_java_modules(repo: Repo) -> Result:
    result = Result("pure-java-modules", "image-core and installer-core use only the JDK")
    present = [module for module in PURE_JAVA_MODULES if repo.has(module)]
    if not present:
        result.skipped = "modules not present"
        return result
    for module in present:
        allowed = ("java.", "javax.") + PURE_JAVA_MODULES[module]
        files = list(repo.under(f"{module}/src", (".java", ".kt")))
        result.problems.extend(imports_outside(repo, files, allowed))
    return result


def rule_app_facing_modules(repo: Repo) -> Result:
    result = Result("app-facing-modules", "client and consumer stay out of Manager internals")
    present = [module for module in APP_FACING_MODULES if repo.has(module)]
    if not present:
        result.skipped = "modules not present"
        return result
    for module in present:
        files = list(repo.under(module, (".java", ".kt", ".aidl")))
        result.problems.extend(imports_outside(repo, files, APP_FACING_MODULES[module], SHARED_BINDER_TYPES))
    return result


def rule_aidl_parity(repo: Repo) -> Result:
    result = Result("aidl-parity", "The Binder contract is identical in client/ and manager/")
    client = "client/src/main/aidl/"
    manager = "manager/src/main/aidl/"
    contract = list(repo.under(client, (".aidl",)))
    if not contract or not repo.has(manager):
        result.skipped = "client or manager AIDL not present"
        return result
    for name in contract:
        twin = manager + name[len(client):]
        if twin not in repo.file_set:
            result.problems.append(f"{name}: no copy at {twin}")
        elif normalized_sha256(repo.data(name)) != normalized_sha256(repo.data(twin)):
            result.problems.append(f"{name}: differs from {twin}; the two copies must stay byte-identical")
    return result


NATIVE_LOAD = re.compile(r"\bSystem\.(?:loadLibrary|load)\s*\(")
KOTLIN_EXTERNAL = re.compile(r"\bexternal\s+fun\b")
JAVA_NATIVE = re.compile(r"\bnative\s+[\w<>\[\],.?]+\s+\w+\s*\(")
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.DOTALL)
LINE_COMMENT = re.compile(r"//[^\n]*")


def declares_native_code(text: str) -> bool:
    """True when the code, comments aside, loads a native library or declares a JNI method."""
    code = LINE_COMMENT.sub("", BLOCK_COMMENT.sub("", text))
    return bool(NATIVE_LOAD.search(code) or KOTLIN_EXTERNAL.search(code) or JAVA_NATIVE.search(code))


def rule_native_boundary(repo: Repo) -> Result:
    result = Result("native-boundary", "Native libraries are loaded only at the documented sites")
    scanned = [name for root in MANAGER_SOURCE_ROOTS for name in repo.under(root, (".kt", ".java"))]
    if not scanned:
        result.skipped = "manager sources not present"
        return result
    seen = set()
    for name in scanned:
        if declares_native_code(repo.text(name)):
            seen.add(name)
            if name not in NATIVE_SITES:
                result.problems.append(
                    f"{name}: loads a native library or declares JNI methods outside the documented sites; "
                    "route the call through an existing site or document a new one")
    for name in sorted(set(NATIVE_SITES) - seen):
        if repo.has(name):
            result.notes.append(f"{name}: listed in NATIVE_SITES but no longer touches native code")
    return result


RECORD_NAME = re.compile(
    r"^(license|licence|copying|notice|source|provenance|upstream-source-notice)([._-].*)?$|manifest.*\.json$",
    re.IGNORECASE)
LICENSE_NAME = re.compile(r"^(license|licence|copying)([._-].*)?$", re.IGNORECASE)
MARKDOWN_LINK = re.compile(r"\]\(<?([^)\s>]+)>?(?:\s+[^)]*)?\)")


def is_record(repo: Repo, name: str) -> bool:
    base = PurePosixPath(name).name
    if RECORD_NAME.search(base):
        return True
    if base.endswith(".json"):
        try:
            data = json.loads(repo.text(name))
        except ValueError:
            return False
        return isinstance(data, dict) and "repository" in data
    return False


def rule_third_party_records(repo: Repo) -> Result:
    result = Result("third-party-records", "Every vendored tree has a provenance record")
    if not repo.has("third_party"):
        result.skipped = "no third_party/"
        return result
    children = sorted({PurePosixPath(n).parts[1] for n in repo.under("third_party") if len(PurePosixPath(n).parts) > 2})
    for child in children:
        files = list(repo.under(f"third_party/{child}"))
        if not any(is_record(repo, n) for n in files):
            result.problems.append(f"third_party/{child}/: no source, license or manifest record")
        elif not any(LICENSE_NAME.match(PurePosixPath(n).name) for n in files):
            result.notes.append(f"third_party/{child}/: records present, no license text beside them")
    index = "third_party/DEPENDENCIES.md"
    if repo.has(index):
        for target in MARKDOWN_LINK.findall(repo.text(index)):
            if re.match(r"^[a-z][a-z0-9+.-]*:", target) or target.startswith("#"):
                continue
            resolved = posixpath.normpath("third_party/" + target.split("#")[0])
            if resolved.startswith("..") or repo.is_excluded(resolved):
                continue
            if not repo.has(resolved):
                result.problems.append(f"{index}: link target does not exist: {target}")
    return result


CODE_SPAN = re.compile(r"`([^`\n]+)`")
PATH_TOKEN = re.compile(r"^\.?/?[A-Za-z0-9_.\-]+(?:/[A-Za-z0-9_.\-]+)+/?$")


def rule_docs_paths(repo: Repo) -> Result:
    result = Result("docs-paths", "Paths named in docs/ARCHITECTURE.md exist")
    if not repo.has(ARCHITECTURE_DOC):
        result.problems.append(f"{ARCHITECTURE_DOC}: missing")
        return result
    text = repo.text(ARCHITECTURE_DOC)
    checked = set()
    for token in CODE_SPAN.findall(text):
        if token in checked or not PATH_TOKEN.match(token):
            continue
        checked.add(token)
        relative = token[2:] if token.startswith("./") else token.lstrip("/")
        candidates = [root + relative for root in DOC_PATH_ROOTS]
        if any(repo.has(c) for c in candidates):
            continue
        if any(repo.is_excluded(c) for c in candidates):
            continue
        result.problems.append(f"{ARCHITECTURE_DOC}: `{token}` does not exist")
    top_level = sorted({PurePosixPath(n).parts[0] for n in repo.files if len(PurePosixPath(n).parts) > 1})
    for directory in top_level:
        if not repo.is_excluded(directory) and f"`{directory}/`" not in text:
            result.problems.append(f"{ARCHITECTURE_DOC}: top-level directory `{directory}/` is not described")
    return result


def rule_docs_index(repo: Repo) -> Result:
    result = Result("docs-index", "Every guide in docs/ is reachable from docs/README.md")
    if not repo.has(DOCS_INDEX):
        result.skipped = "no docs index"
        return result
    linked = {posixpath.normpath("docs/" + t.split("#")[0]) for t in MARKDOWN_LINK.findall(repo.text(DOCS_INDEX))}
    for name in repo.files:
        parts = PurePosixPath(name).parts
        if len(parts) == 2 and parts[0] == "docs" and name.endswith(".md") and name != DOCS_INDEX:
            if name not in linked:
                result.problems.append(f"{name}: not linked from {DOCS_INDEX}")
    return result


def local_link_problems(repo: Repo, document: str) -> Iterator[str]:
    """Markdown links in ``document`` that point at a repository path which does not exist."""
    for target in MARKDOWN_LINK.findall(repo.text(document)):
        if re.match(r"^[a-z][a-z0-9+.-]*:", target) or target.startswith("#"):
            continue
        resolved = posixpath.normpath(posixpath.join(posixpath.dirname(document), target.split("#")[0]))
        if resolved.startswith("..") or repo.is_excluded(resolved):
            continue
        if not repo.has(resolved):
            yield f"{document}: link target does not exist: {target}"


def rule_directory_readmes(repo: Repo) -> Result:
    result = Result("directory-readmes", "Every top-level directory explains itself")
    top_level = sorted({PurePosixPath(n).parts[0] for n in repo.files if len(PurePosixPath(n).parts) > 1})
    for directory in top_level:
        if directory.startswith(".") or directory in README_EXEMPT:
            continue
        readme = f"{directory}/README.md"
        if not repo.has(readme):
            result.problems.append(f"{directory}/: no README.md saying what lives here and how to build and test it")
        elif not repo.text(readme).strip():
            result.problems.append(f"{readme}: empty")
        else:
            result.problems.extend(local_link_problems(repo, readme))
    return result


#: ``ROOT = Path(__file__).resolve().parents[N]`` and the other names scripts give the repository root.
ROOT_ASSIGNMENT = re.compile(
    r"^\s*(?:ROOT|root|REPO|repo|source_root)\s*=\s*Path\(__file__\)(?:\.resolve\(\))?\.parents\[(\d+)\]", re.MULTILINE)


def rule_script_layout(repo: Repo) -> Result:
    result = Result("script-layout", "Scripts sit where the layout says and still find the repository root")
    for name in repo.under("scripts"):
        parts = PurePosixPath(name).parts
        if len(parts) == 2 and parts[1].startswith("test-"):
            result.problems.append(f"{name}: regression tests belong in scripts/tests/")
    for name in (*repo.under("scripts", (".py",)), *repo.under("tools", (".py",))):
        depth = len(PurePosixPath(name).parts) - 1
        for index in ROOT_ASSIGNMENT.findall(repo.text(name)):
            if int(index) != depth:
                result.problems.append(
                    f"{name}: parents[{index}] is not the repository root from here; this file needs parents[{depth}]")
    return result


def rule_pinned_scripts(repo: Repo) -> Result:
    result = Result("pinned-scripts", "Scripts pinned by the SDK records are present and unchanged")
    if not repo.has(SDK_RECORD):
        if repo.has("release/source-policy.json"):
            result.problems.append(f"{SDK_RECORD}: missing SDK receipt for the declared source policy")
        else:
            result.skipped = "the SDK records are not part of this tree"
        return result
    record = repo.text(SDK_RECORD)
    for named in sorted(set(re.findall(r'"((?:scripts|tools)/[A-Za-z0-9_./-]+)"', record))):
        if not repo.has(named):
            result.problems.append(f"{named}: named by {SDK_RECORD} but missing")
    if repo.has("release/source-policy.json"):
        try:
            policy = json.loads(repo.text("release/source-policy.json"))
            if policy.get("public_scope") == "work-in-progress-source-checkpoint":
                from check_sdk_source_contract import checked_retained, check_snapshot_policy
                from prepare_native_inputs import prepare
                from release_audit import native_inputs

                check_snapshot_policy(policy)
                inputs = native_inputs(repo.root, policy)
                for name in PINNED_SCRIPT_DIGESTS:
                    matching = [item for item in inputs if item["path"] == name]
                    if len(matching) != 1:
                        raise ValueError(f"{name}: needs exactly one current native-input record")
                    prepare(repo.root, matching[0], check_only=True)
                    ast.parse(repo.data(name), filename=name)
                retained = json.loads(record)
                for item in (retained["archive"], retained["helper"], retained["environmentPolicyBoot"]["backend"]):
                    checked_retained(repo.root, item)
                result.notes.append(
                    "WIP current source script identities and retained input bytes verified; "
                    "artifact provenance and runtime acceptance remain unverified")
                return result
        except (KeyError, OSError, SyntaxError, TypeError, ValueError) as error:
            result.problems.append("source snapshot contract: " + str(error))
            return result
    digests = record.lower() + "".join(repo.text(name).lower() for base in SDK_MAINTENANCE_RECORDS
                                       for name in repo.under(base, (".json",)))
    for name in PINNED_SCRIPT_DIGESTS:
        if not repo.has(name):
            result.problems.append(f"{name}: pinned by the SDK records but missing; moving or renaming it breaks them")
        else:
            # The records pin the committed bytes. Some of these scripts embed CRLF text, so the raw digest is the
            # one that matches; the line-ending-normalized one only covers a checkout that converted LF files.
            data = repo.data(name)
            raw = hashlib.sha256(data).hexdigest()
            if (raw not in digests and normalized_sha256(data) not in digests
                    and REPOINTED_SCRIPT_DIGESTS.get(name) != raw):
                result.problems.append(f"{name}: its SHA-256 is not the one the SDK records pin; "
                                       "revert the edit or re-pin the SDK in the same commit")
    return result


def rule_portable_paths(repo: Repo) -> Result:
    result = Result("portable-paths", "No file hard-codes a maintainer's machine")
    for name in repo.files:
        parts = PurePosixPath(name).parts
        if (name.startswith(LOCAL_PATH_EXEMPT_PREFIXES) or name in LOCAL_PATH_DETECTORS
                or any(part in LOCAL_PATH_EXEMPT_DIRECTORIES for part in parts[:-1])):
            continue
        path = repo.root / name
        retained = LOCAL_PATH_RETAINED_PROVENANCE.get(name)
        try:
            with path.open("rb") as stream:
                head = stream.read(4096)
                if retained is None and (b"\0" in head or path.stat().st_size > 2_000_000):
                    continue
                data = head + stream.read()
        except OSError as error:
            if retained is not None:
                result.problems.append(f"{name}: retained provenance read failed: {error}")
            continue
        if retained is not None:
            if path.is_symlink() or hashlib.sha256(data).hexdigest() != retained:
                result.problems.append(f"{name}: retained provenance bytes changed")
            elif name.endswith(".json"):
                continue
            else:
                data = re.sub(rb'^(?:; ModuleID = [^\r\n]*|source_filename = [^\r\n]*)\r?$',
                              b"", data, flags=re.MULTILINE)
        text = data.decode("utf-8", errors="replace")
        for label, pattern in LOCAL_PATH_PATTERNS:
            match = pattern.search(text)
            if match:
                line = text.count("\n", 0, match.start()) + 1
                result.problems.append(f"{name}:{line}: {label}: {match.group(0)[:60]}")
    return result


RULES: tuple[Callable[[Repo], Result], ...] = (
    rule_gradle_edges,
    rule_pure_java_modules,
    rule_app_facing_modules,
    rule_aidl_parity,
    rule_native_boundary,
    rule_third_party_records,
    rule_docs_paths,
    rule_docs_index,
    rule_directory_readmes,
    rule_script_layout,
    rule_pinned_scripts,
    rule_portable_paths,
)

# --------------------------------------------------------------------------------------------------
# Origin report
# --------------------------------------------------------------------------------------------------


def origin_report(repo: Repo) -> dict:
    """Compare the Manager's Kotlin/Java sources with the pinned 7kimisu snapshot.

    "added" means: not present in the pinned snapshot. It says nothing about files that other recorded
    upstreams contributed; those are listed in their own manifests and file headers.
    """
    if not repo.has(PINNED_7K_MANIFEST):
        return {}
    manifest = json.loads(repo.text(PINNED_7K_MANIFEST))
    pinned = {"manager/" + entry["path"]: entry["sha256"] for entry in manifest["files"]}
    buckets = {"unchanged": [0, 0], "modified": [0, 0], "added": [0, 0]}
    for name in repo.files:
        if not name.startswith(ORIGIN_SOURCE_ROOTS) or not name.endswith((".kt", ".java")):
            continue
        data = repo.data(name)
        lines = data.count(b"\n") + (0 if data.endswith(b"\n") or not data else 1)
        if name in pinned:
            same = pinned[name] in (hashlib.sha256(data).hexdigest(), normalized_sha256(data))
            key = "unchanged" if same else "modified"
        else:
            key = "added"
        buckets[key][0] += 1
        buckets[key][1] += lines
    total = sum(lines for _, lines in buckets.values()) or 1
    return {
        "snapshot": {"repository": manifest.get("repository"), "commit": manifest.get("commit")},
        "roots": list(ORIGIN_SOURCE_ROOTS),
        "buckets": {key: {"files": files, "lines": lines, "share": round(100.0 * lines / total, 1)}
                    for key, (files, lines) in buckets.items()},
    }


def print_report(report: dict) -> None:
    if not report:
        print(f"origin report: {PINNED_7K_MANIFEST} not present")
        return
    snapshot = report["snapshot"]
    print("\nManager Kotlin/Java sources compared with the pinned 7kimisu snapshot")
    print(f"  snapshot: {snapshot['repository']} @ {str(snapshot['commit'])[:12]}")
    print(f"  roots:    {', '.join(report['roots'])}")
    labels = {"unchanged": "derived, unchanged", "modified": "derived, modified by MapleSU",
              "added": "not in the snapshot"}
    for key in ("unchanged", "modified", "added"):
        bucket = report["buckets"][key]
        print(f"  {labels[key]:30s} {bucket['files']:5d} files {bucket['lines']:8,d} lines {bucket['share']:5.1f}%")


# --------------------------------------------------------------------------------------------------
# Entry point
# --------------------------------------------------------------------------------------------------


def main(argv: list[str] | None = None) -> int:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=ROOT, help="repository root (default: this checkout)")
    parser.add_argument("--report", action="store_true", help="also print the origin report")
    parser.add_argument("--json", action="store_true", help="print machine-readable JSON instead of text")
    args = parser.parse_args(argv)

    repo = Repo(args.root.resolve())
    results = [rule(repo) for rule in RULES]
    failed = [r for r in results if not r.passed]
    report = origin_report(repo) if args.report else None

    if args.json:
        payload = {"passed": not failed,
                   "rules": [{"rule": r.rule, "title": r.title, "passed": r.passed, "skipped": r.skipped,
                              "problems": r.problems, "notes": r.notes} for r in results]}
        if report is not None:
            payload["origin"] = report
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return int(bool(failed))

    for r in results:
        status = "SKIP" if r.skipped else ("PASS" if r.passed else "FAIL")
        print(f"{status}  {r.rule}: {r.title}" + (f" ({r.skipped})" if r.skipped else ""))
        for problem in r.problems:
            print(f"      - {problem}")
        for note in r.notes:
            print(f"      note: {note}")
    if report is not None:
        print_report(report)
    print(f"\n{len(results) - len(failed)}/{len(results)} rules passed")
    return int(bool(failed))


if __name__ == "__main__":
    raise SystemExit(main())
