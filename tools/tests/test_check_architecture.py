import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_architecture as ca

BINDER = "client/src/main/aidl/dev/maplesu/manager/runtime/INativeSuService.aidl"
BINDER_COPY = "manager/src/main/aidl/dev/maplesu/manager/runtime/INativeSuService.aidl"
NATIVE_SITE = "manager/src/main/java/dev/maplesu/manager/runtime/NativeSu.kt"


class CheckArchitectureTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)

    def write(self, name, text=""):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(text.encode("utf-8") if isinstance(text, str) else text)

    def repo(self):
        return ca.Repo(self.root)

    # -- gradle-edges -------------------------------------------------------------------------

    def settings(self):
        self.write("settings.gradle.kts",
                   'include(":image-core", ":installer-core", ":manager")\n'
                   'include(":libsu-service")\n'
                   'project(":libsu-service").projectDir = file("third_party/libsu-service")\n'
                   'include(":maplesu-client", ":maplesu-consumer")\n'
                   'project(":maplesu-client").projectDir = file("client")\n'
                   'project(":maplesu-consumer").projectDir = file("consumer")\n')

    def test_documented_gradle_edges_pass(self):
        self.settings()
        self.write("manager/build.gradle.kts",
                   'dependencies { implementation(project(":image-core"))\n'
                   'implementation(project(":installer-core"))\nimplementation(project(":libsu-service")) }\n')
        self.write("consumer/build.gradle.kts", 'dependencies { implementation(project(":maplesu-client")) }\n')
        self.assertTrue(ca.rule_gradle_edges(self.repo()).passed)

    def test_unexpected_gradle_edge_fails(self):
        self.settings()
        self.write("client/build.gradle.kts", 'dependencies { implementation(project(":manager")) }\n')
        result = ca.rule_gradle_edges(self.repo())
        self.assertEqual(1, len(result.problems))
        self.assertIn(":maplesu-client -> :manager", result.problems[0])

    def test_undocumented_gradle_module_fails(self):
        self.write("settings.gradle.kts", 'include(":manager", ":extra")\n')
        self.assertTrue(any(":extra" in p for p in ca.rule_gradle_edges(self.repo()).problems))

    # -- pure-java-modules / app-facing-modules -------------------------------------------------

    def test_pure_java_module_rejects_android_imports(self):
        self.write("image-core/src/main/java/dev/maplesu/image/A.java",
                   "import java.io.File;\nimport dev.maplesu.image.B;\nimport android.os.Build;\n")
        problems = ca.rule_pure_java_modules(self.repo()).problems
        self.assertEqual(1, len(problems))
        self.assertIn("android.os.Build", problems[0])

    def test_pure_java_module_accepts_jdk_and_own_package(self):
        self.write("installer-core/src/main/java/dev/maplesu/installer/A.java",
                   "import static java.util.Objects.requireNonNull;\nimport dev.maplesu.installer.B;\n")
        self.assertTrue(ca.rule_pure_java_modules(self.repo()).passed)

    def test_client_may_import_only_the_shared_binder_types(self):
        self.write("client/src/main/java/dev/maplesu/client/C.kt",
                   "import dev.maplesu.manager.runtime.INativeSuSession\n"
                   "import dev.maplesu.manager.runtime.NativeSuBinder\n")
        problems = ca.rule_app_facing_modules(self.repo()).problems
        self.assertEqual(1, len(problems))
        self.assertIn("NativeSuBinder", problems[0])

    def test_test_frameworks_are_allowed_in_test_source_sets_only(self):
        self.write("consumer/src/androidTest/java/dev/maplesu/consumer/T.kt", "import org.junit.Test\n")
        self.write("consumer/src/main/java/dev/maplesu/consumer/M.kt", "import org.junit.Test\n")
        problems = ca.rule_app_facing_modules(self.repo()).problems
        self.assertEqual(1, len(problems))
        self.assertIn("src/main", problems[0])

    # -- aidl-parity ------------------------------------------------------------------------------

    def test_aidl_copies_must_match_and_line_endings_are_ignored(self):
        self.write(BINDER, "interface A {\n  void f();\n}\n")
        self.write(BINDER_COPY, "interface A {\r\n  void f();\r\n}\r\n")
        self.assertTrue(ca.rule_aidl_parity(self.repo()).passed)
        self.write(BINDER_COPY, "interface A {\n  void g();\n}\n")
        self.assertIn("differs", ca.rule_aidl_parity(self.repo()).problems[0])

    def test_missing_aidl_copy_fails(self):
        self.write(BINDER, "interface A {}\n")
        self.write("manager/src/main/aidl/dev/maplesu/manager/other/IOther.aidl", "interface B {}\n")
        self.assertIn("no copy", ca.rule_aidl_parity(self.repo()).problems[0])

    # -- native-boundary --------------------------------------------------------------------------

    def test_native_loading_outside_the_documented_sites_fails(self):
        self.write("manager/src/main/java/dev/maplesu/manager/ui/Loader.kt",
                   'object Loader { init { System.loadLibrary("x") } }\n')
        self.write("manager/src/main/java/dev/maplesu/manager/ui/Jni.kt",
                   "class Jni { private external fun call(): Int }\n")
        self.write("manager/src/main/java/dev/maplesu/manager/ui/Bridge.java",
                   "class Bridge { public static native int call(int x); }\n")
        self.assertEqual(3, len(ca.rule_native_boundary(self.repo()).problems))

    def test_native_boundary_ignores_comments_and_accepts_listed_sites(self):
        self.write("manager/src/main/java/dev/maplesu/manager/ui/Doc.kt",
                   "// System.loadLibrary(\"x\") happens elsewhere\n/**\n * external fun is declared in NativeSu\n"
                   " * native module (see docs)\n */\nclass Doc\n")
        self.write(NATIVE_SITE, 'object NativeSu { init { System.loadLibrary("maplesu") } }\n')
        result = ca.rule_native_boundary(self.repo())
        self.assertTrue(result.passed, result.problems)

    # -- third-party-records ----------------------------------------------------------------------

    def test_vendored_tree_needs_a_record(self):
        self.write("third_party/a/code.c", "int x;\n")
        self.assertIn("third_party/a/", ca.rule_third_party_records(self.repo()).problems[0])
        self.write("third_party/a/SOURCE.json", "{}\n")
        result = ca.rule_third_party_records(self.repo())
        self.assertTrue(result.passed)
        self.assertTrue(any("no license text" in note for note in result.notes))

    def test_json_with_a_repository_key_counts_as_a_record(self):
        self.write("third_party/b/contracts.json", json.dumps({"repository": "https://example.invalid/x"}))
        self.assertTrue(ca.rule_third_party_records(self.repo()).passed)

    def test_dependency_inventory_links_must_resolve(self):
        self.write("third_party/a/SOURCE.json", "{}\n")
        self.write("third_party/DEPENDENCIES.md",
                   "[ok](a/SOURCE.json) [missing](a/NOPE.md) [web](https://example.invalid) [top](../README.md)\n")
        problems = ca.rule_third_party_records(self.repo()).problems
        self.assertEqual(2, len(problems))
        self.assertTrue(any("a/NOPE.md" in p for p in problems))

    # -- docs-paths / docs-index --------------------------------------------------------------------

    def test_architecture_doc_paths_and_top_level_directories(self):
        self.write("manager/src/main/java/dev/maplesu/manager/workmode/detect/DetectionRules.kt", "")
        self.write("tools/check_architecture.py", "")
        self.write("runtime/placeholder.txt", "")
        self.write("docs/ARCHITECTURE.md",
                   "`manager/` `tools/` `docs/`\n`workmode/detect/DetectionRules.kt` `tools/check_architecture.py`\n"
                   "`tools/gone.py` `runtime/maplesu-sdk/include/` `KernelSU / ksud` `a/b`\n")
        self.write("release/source-policy.json", json.dumps({"excluded_prefixes": ["runtime/maplesu-sdk/"]}))
        problems = ca.rule_docs_paths(self.repo()).problems
        self.assertEqual({"`tools/gone.py` does not exist", "`a/b` does not exist",
                          "top-level directory `runtime/` is not described",
                          "top-level directory `release/` is not described"},
                         {p.split(": ", 1)[1] for p in problems})

    def test_private_roots_do_not_require_public_architecture_entries(self):
        self.write(".handoff/README.md", "private")
        self.write("release/source-policy.json", json.dumps({"excluded_prefixes": [".handoff/"]}))
        self.write("docs/ARCHITECTURE.md", "`docs/` `release/`\n")
        self.assertTrue(ca.rule_docs_paths(self.repo()).passed)

    def test_exported_source_ignores_generated_build_directories(self):
        self.write("docs/ARCHITECTURE.md", "`docs/`\n")
        self.write(".gradle/cache.bin", "generated")
        self.write("manager/build/generated/Generated.kt", "generated")
        self.write("tools/__pycache__/cached.pyc", "generated")
        self.assertTrue(ca.rule_docs_paths(self.repo()).passed)
        self.assertEqual(["docs/ARCHITECTURE.md"], ca.list_files(self.root))

    def test_every_guide_must_be_linked_from_the_index(self):
        self.write("docs/README.md", "[a](A.md)\n")
        self.write("docs/A.md", "")
        self.write("docs/B.md", "")
        self.write("docs/assets/README.md", "")
        problems = ca.rule_docs_index(self.repo()).problems
        self.assertEqual(["docs/B.md: not linked from docs/README.md"], problems)

    # -- directory-readmes / script-layout / pinned-scripts ---------------------------------------

    def test_every_top_level_directory_needs_a_readme(self):
        self.write("manager/src/A.kt", "")
        self.write("scripts/x.py", "")
        self.write("scripts/README.md", "# scripts\n")
        self.write("docs/README.md", "# docs\n")
        for exempt in ("gradle/libs.versions.toml", "release/source-policy.json", "work/a.txt", ".github/a.yml"):
            self.write(exempt, "")
        self.write("README.md", "# root\n")
        problems = ca.rule_directory_readmes(self.repo()).problems
        self.assertEqual(1, len(problems))
        self.assertTrue(problems[0].startswith("manager/: no README.md"))
        self.write("manager/README.md", "  \n")
        self.assertEqual(["manager/README.md: empty"], ca.rule_directory_readmes(self.repo()).problems)
        self.write("manager/README.md", "# manager\n")
        self.assertTrue(ca.rule_directory_readmes(self.repo()).passed)

    def test_directory_readme_links_must_resolve(self):
        self.write("client/src/A.kt", "")
        self.write("client/README.md", "[ok](src/A.kt) [dir](../client/) [up](../docs/SDK.md#a) "
                                       "[gone](../docs/GONE.md) [web](https://example.invalid/x) [here](#top)\n")
        self.write("docs/SDK.md", "")
        self.write("docs/README.md", "# docs\n")
        problems = ca.rule_directory_readmes(self.repo()).problems
        self.assertEqual(["client/README.md: link target does not exist: ../docs/GONE.md"], problems)

    def test_regression_tests_belong_in_scripts_tests(self):
        self.write("scripts/test-a.py", "")
        self.write("scripts/tests/test-b.py", "")
        self.write("scripts/build-c.py", "")
        problems = ca.rule_script_layout(self.repo()).problems
        self.assertEqual(["scripts/test-a.py: regression tests belong in scripts/tests/"], problems)

    def test_script_root_resolution_must_match_the_files_depth(self):
        header = "from pathlib import Path\nROOT = Path(__file__).resolve().parents[{}]\n"
        self.write("scripts/tests/test-a.py", header.format(1))
        self.write("scripts/tests/test-b.py", header.format(2))
        self.write("scripts/c.py", header.format(1))
        self.write("tools/d.py", "repo = Path(__file__).parents[1]\n")
        self.write("tools/tests/e.py", "root = Path(__file__).resolve().parents[1]\n")
        self.write("tools/specgen/f.py", "HERE = Path(__file__).resolve().parents[5]\n")
        problems = ca.rule_script_layout(self.repo()).problems
        self.assertEqual(2, len(problems))
        self.assertIn("scripts/tests/test-a.py: parents[1] is not the repository root", problems[0])
        self.assertIn("this file needs parents[2]", problems[0])
        self.assertIn("tools/tests/e.py: parents[1]", problems[1])

    def test_pinned_scripts_are_skipped_without_the_sdk_record(self):
        self.write("scripts/a.py", "x\n")
        self.assertTrue(ca.rule_pinned_scripts(self.repo()).skipped)

    def test_pinned_scripts_must_match_the_sdk_records(self):
        import hashlib
        from unittest import mock
        recorded, embedded_crlf, edited = b"print('a')\n", b"print('b')\r\nprint('c')\n", b"print('d')\n"
        digest = lambda data: hashlib.sha256(data).hexdigest()
        self.write("scripts/repair-a.py", recorded)
        self.write("scripts/repair-b.py", embedded_crlf)
        self.write("scripts/repair-c.py", edited + b"# tampered\n")
        self.write("runtime/maplesu-sdk/SDK-RELEASE.json",
                   json.dumps({"rebuildScript": "scripts/repair-a.py", "aSha256": digest(recorded),
                               "bSha256": digest(embedded_crlf)}))
        self.write("work/delivery/build.json", json.dumps({"cSha256": digest(edited)}))
        with mock.patch.object(ca, "PINNED_SCRIPT_DIGESTS",
                               ("scripts/repair-a.py", "scripts/repair-b.py", "scripts/repair-c.py", "scripts/gone.py")):
            problems = ca.rule_pinned_scripts(self.repo()).problems
        self.assertEqual(2, len(problems))
        self.assertTrue(any(p.startswith("scripts/repair-c.py: its SHA-256 is not the one") for p in problems))
        self.assertTrue(any(p.startswith("scripts/gone.py: pinned by the SDK records but missing") for p in problems))

    def test_pinned_scripts_follow_the_maintenance_records_and_the_recorded_repoints(self):
        import hashlib
        from unittest import mock
        digest = lambda data: hashlib.sha256(data).hexdigest()
        moved, repointed, edited = b"print('moved')\n", b"print('repointed')\n", b"print('edited')\n"
        self.write("scripts/repair-a.py", moved)
        self.write("scripts/repair-b.py", repointed)
        self.write("scripts/repair-c.py", edited)
        self.write("runtime/maplesu-sdk/SDK-RELEASE.json", json.dumps({"bSha256": digest(b"print('before')\n")}))
        self.write("runtime/maplesu-sdk/maintenance/entry/build.json", json.dumps({"aSha256": digest(moved)}))
        with mock.patch.object(ca, "PINNED_SCRIPT_DIGESTS",
                               ("scripts/repair-a.py", "scripts/repair-b.py", "scripts/repair-c.py")), \
                mock.patch.object(ca, "REPOINTED_SCRIPT_DIGESTS", {"scripts/repair-b.py": digest(repointed),
                                                                   "scripts/repair-c.py": digest(b"print('other')\n")}):
            problems = ca.rule_pinned_scripts(self.repo()).problems
        self.assertEqual(["scripts/repair-c.py: its SHA-256 is not the one the SDK records pin; "
                          "revert the edit or re-pin the SDK in the same commit"], problems)

    def test_a_script_named_by_the_sdk_record_must_exist(self):
        from unittest import mock
        self.write("runtime/maplesu-sdk/SDK-RELEASE.json", json.dumps({"archiveScript": "scripts/repair-x.py"}))
        with mock.patch.object(ca, "PINNED_SCRIPT_DIGESTS", ()):
            problems = ca.rule_pinned_scripts(self.repo()).problems
        self.assertEqual(["scripts/repair-x.py: named by runtime/maplesu-sdk/SDK-RELEASE.json but missing"], problems)

    def source_script_snapshot(self):
        import hashlib
        policy = {"public_scope": "work-in-progress-source-checkpoint",
                  "native_input_manifest": "release/native-inputs.json",
                  "release_blockers": [{"id": name, "resolved": False} for name in
                                       ("complete-source-build", "maplesu-5.2.1-acceptance")]}
        self.write("release/source-policy.json", json.dumps(policy))
        inputs = []
        for name in ca.PINNED_SCRIPT_DIGESTS:
            data = b"print('current source')\n"
            self.write(name, data)
            inputs.append({"path": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
        self.write("release/native-inputs.json", json.dumps({"files": inputs}))
        retained = []
        for name in ("archive.a", "helper", "backend.so"):
            data = ("retained " + name).encode()
            path = "runtime/maplesu-sdk/" + name
            self.write(path, data)
            retained.append({"path": path, "size": len(data), "sha256": hashlib.sha256(data).hexdigest()})
        sdk = {"archive": retained[0], "helper": retained[1], "environmentPolicyBoot": {"backend": retained[2]}}
        self.write(ca.SDK_RECORD, json.dumps(sdk))
        return policy, inputs, sdk

    def test_wip_scripts_follow_current_path_bound_inputs(self):
        self.source_script_snapshot()
        result = ca.rule_pinned_scripts(self.repo())
        self.assertTrue(result.passed, result.problems)
        self.assertIn("artifact provenance and runtime acceptance remain unverified", result.notes[0])

    def test_wip_current_script_edits_are_rejected(self):
        self.source_script_snapshot()
        self.write(ca.PINNED_SCRIPT_DIGESTS[0], "print('changed source')\n")
        result = ca.rule_pinned_scripts(self.repo())
        self.assertFalse(result.passed)
        self.assertIn("digest or size mismatch", result.problems[0])

    def test_wip_current_record_edits_are_rejected(self):
        _, inputs, _ = self.source_script_snapshot()
        inputs[0]["sha256"] = "0" * 64
        self.write("release/native-inputs.json", json.dumps({"files": inputs}))
        result = ca.rule_pinned_scripts(self.repo())
        self.assertFalse(result.passed)
        self.assertIn("digest or size mismatch", result.problems[0])

    def test_wip_current_records_are_required_and_unambiguous(self):
        _, inputs, _ = self.source_script_snapshot()
        for entries in (inputs[1:], inputs + inputs[:1]):
            with self.subTest(entries=len(entries)):
                self.write("release/native-inputs.json", json.dumps({"files": entries}))
                result = ca.rule_pinned_scripts(self.repo())
                self.assertFalse(result.passed)
                self.assertIn("exactly one current native-input record", result.problems[0])

    def test_wip_registered_scripts_still_need_valid_python(self):
        import hashlib
        _, inputs, _ = self.source_script_snapshot()
        data = b"def broken(:\n"
        self.write(inputs[0]["path"], data)
        inputs[0].update(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
        self.write("release/native-inputs.json", json.dumps({"files": inputs}))
        result = ca.rule_pinned_scripts(self.repo())
        self.assertFalse(result.passed)
        self.assertIn("invalid syntax", result.problems[0])

    def test_wip_retained_archive_helper_and_backend_edits_are_rejected(self):
        _, _, sdk = self.source_script_snapshot()
        for item in (sdk["archive"], sdk["helper"], sdk["environmentPolicyBoot"]["backend"]):
            with self.subTest(path=item["path"]):
                original = (self.root / item["path"]).read_bytes()
                self.write(item["path"], original + b"changed")
                result = ca.rule_pinned_scripts(self.repo())
                self.assertFalse(result.passed)
                self.assertIn("retained input changed", result.problems[0])
                self.write(item["path"], original)

    def test_wip_with_a_resolved_blocker_fails_instead_of_falling_back(self):
        policy, _, _ = self.source_script_snapshot()
        for blocker in policy["release_blockers"]:
            with self.subTest(blocker=blocker["id"]):
                blocker["resolved"] = True
                self.write("release/source-policy.json", json.dumps(policy))
                result = ca.rule_pinned_scripts(self.repo())
                self.assertFalse(result.passed)
                self.assertIn("unresolved release blockers", result.problems[0])
                blocker["resolved"] = False

    def test_release_candidate_keeps_the_sdk_record_contract(self):
        policy, _, _ = self.source_script_snapshot()
        policy["public_scope"] = "release-candidate"
        self.write("release/source-policy.json", json.dumps(policy))
        result = ca.rule_pinned_scripts(self.repo())
        self.assertFalse(result.passed)
        self.assertEqual(len(ca.PINNED_SCRIPT_DIGESTS), len(result.problems))
        self.assertTrue(all("the SDK records pin" in item for item in result.problems))
        self.assertEqual([], result.notes)

    def test_declared_source_policy_requires_the_sdk_receipt(self):
        policy, _, _ = self.source_script_snapshot()
        (self.root / ca.SDK_RECORD).unlink()
        for scope in ("work-in-progress-source-checkpoint", "release-candidate"):
            with self.subTest(scope=scope):
                policy["public_scope"] = scope
                self.write("release/source-policy.json", json.dumps(policy))
                result = ca.rule_pinned_scripts(self.repo())
                self.assertFalse(result.passed)
                self.assertFalse(result.skipped)
                self.assertIn("missing SDK receipt", result.problems[0])

    def test_wip_manifest_path_stays_inside_the_tree(self):
        policy, _, _ = self.source_script_snapshot()
        policy["native_input_manifest"] = "../outside.json"
        self.write("release/source-policy.json", json.dumps(policy))
        result = ca.rule_pinned_scripts(self.repo())
        self.assertFalse(result.passed)
        self.assertIn("leaves candidate tree", result.problems[0])

    # -- portable-paths ---------------------------------------------------------------------------

    def test_a_maintainers_machine_must_not_appear_in_the_tree(self):
        self.write("scripts/a.py", "SDK = 'D:/Maple_Env/Android/Sdk'\n")
        self.write("scripts/b.ps1", "$p = 'C:\\Users\\Alice\\AppData'\n")
        self.write("tools/c.sh", "cd /Users/bob/src/MapleSU\n")
        self.write("tools/d.sh", "JAVA='C:/Program Files/Microsoft/jdk-21.0.12.101-hotspot'\n")
        self.write("tools/e.sh", "echo ok\nadb -s a261972a shell\n")
        self.write("tools/f.sh", "PY=C:/Python314/python.exe\n")
        self.write("docs/placeholders.md", "C:\\Users\\<name>\\x, C:/Users/Public, /home/runner/work/MapleSU, "
                                           "/Users/$USER/x, ~/Android/Sdk, $ANDROID_HOME/ndk\n")
        for exempt in ("work/receipt.json", ".analysis/x.json", "evidence/x.txt", "native-inputs/policy/a/identity.json",
                       "third_party/kernelsu/upstream/a.py",
                       "runtime/maplesu-sdk/b.txt", "zygisk/package.py", "tools/check_architecture.py"):
            self.write(exempt, "D:/Maple_Workflow\n")
        self.write("assets/logo.bin", b"\0\0D:/Maple_Env")
        problems = ca.rule_portable_paths(self.repo()).problems
        self.assertEqual(
            ["scripts/a.py:1: a maintainer's workspace directory: Maple_Env",
             "scripts/b.ps1:1: a Windows user profile: C:\\Users\\Alice",
             "tools/c.sh:1: a Unix home directory: /Users/bob/",
             "tools/d.sh:1: a pinned JDK or Python install directory: jdk-21.0.12.101-hotspot",
             "tools/e.sh:2: a device serial number: a261972a",
             "tools/f.sh:1: a pinned JDK or Python install directory: C:/Python314"],
            sorted(problems))

    def test_retained_provenance_requires_exact_bytes(self):
        import hashlib
        from unittest import mock
        name = "fixtures/receipt.json"
        data = b'{"producer": "D:/Maple_Workflow/source"}\n'
        self.write(name, data)
        with mock.patch.object(ca, "LOCAL_PATH_RETAINED_PROVENANCE",
                               {name: hashlib.sha256(data).hexdigest()}):
            self.assertTrue(ca.rule_portable_paths(self.repo()).passed)
            for changed in (data + b" ", b"\0" + data, data + b" " * 2_000_001):
                with self.subTest(bytes=len(changed)):
                    self.write(name, changed)
                    self.assertTrue(any("retained provenance bytes changed" in p
                                        for p in ca.rule_portable_paths(self.repo()).problems))

    def test_retained_ir_only_exempts_source_metadata(self):
        import hashlib
        from unittest import mock
        name = "fixtures/producer.ll"
        metadata = (b"; ModuleID = 'D:/Maple_Workflow/source.bc'\r\n"
                    b'source_filename = "C:/Users/Alice/source.cpp"\r\n')
        for body, passes in ((b'define void @f() { ret void }\n', True),
                             (b'@path = constant [1 x i8] c"D:/Maple_Workflow/data"\n', False)):
            with self.subTest(body=body):
                data = metadata + body
                self.write(name, data)
                with mock.patch.object(ca, "LOCAL_PATH_RETAINED_PROVENANCE",
                                       {name: hashlib.sha256(data).hexdigest()}):
                    self.assertEqual(passes, ca.rule_portable_paths(self.repo()).passed)

    def test_retained_provenance_read_errors_fail(self):
        import hashlib
        from unittest import mock
        name = "fixtures/receipt.json"
        data = b'{"producer": "original"}\n'
        self.write(name, data)
        repo = self.repo()
        with mock.patch.object(ca, "LOCAL_PATH_RETAINED_PROVENANCE",
                               {name: hashlib.sha256(data).hexdigest()}), \
                mock.patch.object(Path, "open", side_effect=PermissionError("read denied")):
            problems = ca.rule_portable_paths(repo).problems
            self.assertEqual(1, len(problems))
            self.assertIn("retained provenance read failed", problems[0])

    def test_retained_provenance_does_not_exempt_a_neighbor(self):
        import hashlib
        from unittest import mock
        name = "fixtures/receipt.json"
        data = b'{"producer": "D:/Maple_Workflow/source"}\n'
        self.write(name, data)
        self.write("fixtures/run.py", "ROOT = 'D:/Maple_Workflow/source'\n")
        with mock.patch.object(ca, "LOCAL_PATH_RETAINED_PROVENANCE",
                               {name: hashlib.sha256(data).hexdigest()}):
            problems = ca.rule_portable_paths(self.repo()).problems
            self.assertEqual(1, len(problems))
            self.assertTrue(problems[0].startswith("fixtures/run.py:"))

    # -- origin report ---------------------------------------------------------------------------

    def test_origin_report_classifies_manager_sources(self):
        import hashlib
        same, edited = "class Same\n", "class Edited\n"
        pinned = {"files": [
            {"path": "src/main/java/A.kt", "sha256": hashlib.sha256(same.encode()).hexdigest()},
            {"path": "src/main/java/B.kt", "sha256": hashlib.sha256(b"class Before\n").hexdigest()}],
            "repository": "https://example.invalid/7k", "commit": "abc"}
        self.write("third_party/7kimisu/source-manifest.json", json.dumps(pinned))
        self.write("manager/src/main/java/A.kt", same)
        self.write("manager/src/main/java/B.kt", edited)
        self.write("manager/src/main/java/C.kt", "class C\nclass D\n")
        self.write("manager/src/main/res/values/strings.xml", "<resources/>\n")
        buckets = ca.origin_report(self.repo())["buckets"]
        self.assertEqual((1, 1), (buckets["unchanged"]["files"], buckets["unchanged"]["lines"]))
        self.assertEqual((1, 1), (buckets["modified"]["files"], buckets["modified"]["lines"]))
        self.assertEqual((1, 2), (buckets["added"]["files"], buckets["added"]["lines"]))

    # -- the repository itself ----------------------------------------------------------------------

    def test_repository_follows_its_own_architecture_rules(self):
        repo = ca.Repo(ca.ROOT)
        for rule in ca.RULES:
            result = rule(repo)
            self.assertTrue(result.passed, f"{result.rule}: {result.problems}")


if __name__ == "__main__":
    unittest.main()
