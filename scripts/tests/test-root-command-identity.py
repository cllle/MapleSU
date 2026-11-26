"""Replay production Root command identity admission and JNI receipt assembly on the host."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

import shutil
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import jdk_tool

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/specgen"))
from specgen.product import cached_jar


def block(source, marker):
    """Extract a braced production declaration, ignoring comments and literals."""
    start = source.index(marker)
    opening = source.index("{", start)
    tokens = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|[{}]')
    depth = 0
    for token in tokens.finditer(source, opening):
        if token[0] == "{":
            depth += 1
        elif token[0] == "}":
            depth -= 1
            if depth == 0:
                return source[start:token.end()]
    raise AssertionError("Unclosed production declaration: " + marker)


def write(out, name, value):
    path = out / name
    path.write_text(value, encoding="utf-8")
    return path


KOTLIN_TEST = r'''package dev.maplesu.manager.runtime.ultra
import dev.maplesu.manager.runtime.PageReadDeadline
import dev.maplesu.manager.runtime.ultra.bridge.NativeBridge
import dev.maplesu.manager.ultrakernel.UltraKernelCredential
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RootCommandIdentityTest {
 private val valid = String(UltraKernelCredential.of("A".repeat(48)).withMetadataDisplacement(4096).copySecret(), Charsets.US_ASCII)
 private val denied = """{"schemaVersion":1,"backend":"maplesu_ultra","operation":"run_root_cmd","ok":false,"sdkError":"ERR_MODULE_OPEN_FILE","sdkErrorCode":-19934,"data":{"rootCommand":{"phase":5,"systemError":13,"rootAttempted":false}}}"""
 @Before fun reset() { NativeBridge.calls = 0; NativeBridge.lastKey = "unset"; NativeBridge.answer = denied; PageReadDeadline.beforeRead = null }
 private fun runner(client: UltraRuntimeClient, body: (String) -> SuExecResult?) {
  client.javaClass.getDeclaredField("rootCommands").apply { isAccessible = true }.set(client, RootScriptRunner(body))
 }
 private fun noSubmission(receipt: NativeReceipt, error: String) {
  assertFalse(receipt.succeeded); assertEquals(error, receipt.sdkError); assertEquals(0, NativeBridge.calls)
 }
 @Test fun genuineNullSelectsOwnerOnceAndPreservesTheNativeFailure() {
  val result = UltraRuntimeClient { null }.runRootCommand("id")
  assertEquals(1, NativeBridge.calls); assertNull(NativeBridge.lastKey); assertEquals(denied, result.raw)
 }
 @Test fun explicitValidKeyIsSubmittedUnchangedOnce() {
  val result = UltraRuntimeClient { valid }.runRootCommand("id")
  assertEquals(1, NativeBridge.calls); assertEquals(valid, NativeBridge.lastKey); assertEquals(denied, result.raw)
 }
 @Test fun malformedNonNullKeysNeverBecomeOwnerRequests() {
  for (key in listOf("", "malformed", "A".repeat(48))) {
   noSubmission(UltraRuntimeClient { key }.runRootCommand("id"), "MAPLESU_ULTRA_ROOT_KEY_ABSENT")
  }
 }
 @Test fun invalidatedKeyScopeDoesNotSubmit() {
  noSubmission(UltraRuntimeClient { valid }.scoped(valid) { false }.runRootCommand("id"), "SDK_CREDENTIAL_SCOPE_CHANGED")
 }
 @Test fun invalidatedOwnerScopeDoesNotSubmit() {
  noSubmission(UltraRuntimeClient { null }.scoped(null) { false }.runRootCommand("id"), "SDK_CREDENTIAL_SCOPE_CHANGED")
 }
 @Test fun keyChangingWhileEnteringAdmissionDoesNotSubmit() {
  var supplied: String? = valid
  PageReadDeadline.beforeRead = { supplied = null }
  noSubmission(UltraRuntimeClient { supplied }.runRootCommand("id"), "SDK_CREDENTIAL_SCOPE_CHANGED")
 }
 @Test fun ownerChangingWhileEnteringAdmissionDoesNotSubmit() {
  var supplied: String? = null
  PageReadDeadline.beforeRead = { supplied = valid }
  noSubmission(UltraRuntimeClient { supplied }.runRootCommand("id"), "SDK_CREDENTIAL_SCOPE_CHANGED")
 }
 @Test fun verifiedKeyKeepsItsRunnerAndNeverReplaysFailureThroughJni() {
  var count = 0
  val client = UltraRuntimeClient { valid }
  runner(client) { count++; SuExecResult(7, "runner output") }
  val receipt = client.runRootCommand("id")
  assertEquals(1, count); assertEquals(0, NativeBridge.calls); assertEquals("MAPLESU_ROOT_COMMAND_EXIT", receipt.sdkError)
  assertEquals("runner+output", receipt.string("result")); assertEquals(7, receipt.int("exitCode"))
 }
 @Test fun ownerDoesNotUseAnExistingKeyRunner() {
  var count = 0
  val client = UltraRuntimeClient { null }
  runner(client) { count++; SuExecResult(0, "unexpected") }
  assertEquals(denied, client.runRootCommand("id").raw)
  assertEquals(0, count); assertEquals(1, NativeBridge.calls); assertNull(NativeBridge.lastKey)
 }
 @Test fun runnerExceptionIsReportedOnceWithoutTransportReplay() {
  var count = 0
  val client = UltraRuntimeClient { valid }
  runner(client) { count++; throw IllegalStateException("runner fixture") }
  assertEquals("MAPLESU_BRIDGE_EXCEPTION", client.runRootCommand("id").sdkError)
  assertEquals(1, count); assertEquals(0, NativeBridge.calls)
 }
}
'''


def kotlin(out, client_text, receipt_text, credential):
    imports = '''package dev.maplesu.manager.runtime.ultra
import dev.maplesu.manager.runtime.ultra.bridge.NativeBridge
import dev.maplesu.manager.runtime.PageReadDeadline
import dev.maplesu.manager.ultrakernel.UltraKernelCredential
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
'''
    methods = [block(client_text, marker) for marker in ["private fun call(", "private fun busy(",
        "private fun localCall(", "private fun key(", "internal fun scoped(",
        "fun runRootCommand(", "private fun callWithIdentity(", "companion object {"]]
    # busy() is an expression function; its next brace belongs to localCall().
    methods[1] = client_text[client_text.index("private fun busy("):client_text.index("    // Compiled identity")].strip()
    methods[4] = client_text[client_text.index("internal fun scoped("):client_text.index("    /** Read-only readiness")].strip()
    assembled = imports + '''class UltraRuntimeClient(private val rootKeyProvider: () -> String? = { null }) {
 private var rootCommands: RootScriptRunner? = null
 private var identityCurrent: () -> Boolean = { true }
 private val loadFailure: String? = null
''' + "\n".join(methods) + "\n}\n"
    sources = [write(out, "UltraRuntimeClient.kt", assembled), credential,
        write(out, "NativeReceipt.kt", "package dev.maplesu.manager.runtime.ultra\nimport org.json.*\nimport java.net.URLEncoder\n" + block(receipt_text, "data class NativeReceipt(")),
        write(out, "RootCommandIdentityTest.kt", KOTLIN_TEST),
        write(out, "Fixtures.kt", '''package dev.maplesu.manager.runtime.ultra
fun interface RootScriptRunner { fun run(script: String): SuExecResult? }
data class SuExecResult(val exitCode: Int, val output: String, val timedOut: Boolean = false, val failure: String? = null)
class UltraAuthorizationResult { companion object { fun parse(value: org.json.JSONObject?): UltraAuthorizationResult? = null } }
object UltraSdkLogging { fun redact(value: String?) = value.orEmpty() }
'''),
        write(out, "Deadline.kt", '''package dev.maplesu.manager.runtime
object PageReadDeadline { var beforeRead: (() -> Unit)? = null; fun currentNanos(): Long? { beforeRead?.invoke(); return null } }
'''),
        write(out, "StoredInputs.kt", '''package dev.maplesu.manager.compat
object StoredInputs { fun receiptBackend(value: String) = value; fun error(value: String) = value }
''')]
    bridge = write(out, "NativeBridge.java", '''package dev.maplesu.manager.runtime.ultra.bridge;
public final class NativeBridge {
 public static int calls; public static String lastKey, answer;
 public static String runRootCmd(String key, String command) { calls++; lastKey = key; return answer; }
}
''')
    deps = [cached_jar(*item) for item in [("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.20"),
        ("org.jetbrains", "annotations", "13.0"), ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.11.0"),
        ("org.json", "json", "20240303"), ("junit", "junit", "4.13.2"), ("org.hamcrest", "hamcrest-core", "1.3")]]
    compiler = cached_jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.4.20")
    java, javac = jdk_tool(), jdk_tool("javac")
    classes = out / "classes"
    classes.mkdir(exist_ok=True)
    cp = os.pathsep.join(map(str, [classes, *deps]))
    return [[java, "-Xmx256m", "-cp", os.pathsep.join(map(str, [compiler, *deps])),
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-nowarn", "-jvm-target", "21",
        "-classpath", cp, "-d", classes, *sources, bridge],
        [javac, "-encoding", "UTF-8", "-cp", cp, "-d", classes, bridge],
        [java, "-cp", cp, "org.junit.runner.JUnitCore", "dev.maplesu.manager.runtime.ultra.RootCommandIdentityTest"]]


def native(out, source):
    helpers = [block(source, name) for name in ["std::string jstring_to_utf8(", "std::string url_encode_copy(",
        "cJSON* begin(", "void mark_ok(", "void mark_sdk_error(", "void mark_bridge_error(",
        "void mark_call_bridge_error(", "jstring finish(", "struct JsonDeleter", "cJSON* owner_u64(",
        "cJSON* owner_error(", "bool attach_owned_json(cJSON* object, const char* field, cJSON* child) {",
        "cJSON* owner_root_command_json(", "cJSON* root_command_result_json(",
        "template <typename Body>\njstring guarded(", "KModErr authenticated_primary_error(",
        "void authenticated_receipt_alloc(", "void mark_authenticated_ok(", "void restore_authenticated_mode(",
        "template <typename Body>\njstring guarded_authenticated("]]
    helpers[8] += ";\nusing OwnedJson = std::unique_ptr<cJSON, JsonDeleter>;"
    method = block(source, 'extern "C" JNIEXPORT jstring JNICALL\nJava_dev_maplesu_manager_runtime_ultra_bridge_NativeBridge_runRootCmd(')
    write(out, "root_command_production.inc", "\n\n".join(helpers) + "\n\n" + method)
    binary = out / "native-root-identity.exe"
    return [[(os.environ.get("CXX") or shutil.which("g++") or "g++"), "-std=c++17", "-Wall", "-Wextra", "-Werror", "-Wno-unused-function",
        "-I" + str(out), "-I" + str(ROOT / "runtime/ultra/bridge"),
        "-I" + str(ROOT / "runtime/maplesu-sdk/include"), "-I" + str(ROOT / "runtime/maplesu-sdk/sdk/include"),
        "-I" + str(ROOT / "runtime/ultra/third_party/cJSON"), "-I" + str(ROOT / "runtime/ultra/third_party/include"),
        ROOT / "runtime/tests/ultra_root_command_identity_test.cpp", ROOT / "runtime/ultra/third_party/cJSON/cJSON.cpp",
        "-static-libgcc", "-static-libstdc++", "-o", binary], [binary]]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    paths = [ROOT / name for name in ["runtime/ultra/bridge/native_lib_ultra.cpp",
        "manager/src/main/java/dev/maplesu/manager/runtime/ultra/UltraRuntimeClient.kt",
        "manager/src/main/java/dev/maplesu/manager/runtime/ultra/UltraReceipt.kt",
        "manager/src/main/java/dev/maplesu/manager/ultrakernel/UltraKernelCredential.kt",
        "runtime/ultra/bridge/manager_owner_identity.h", "runtime/maplesu-sdk/sdk/include/maplesu_root_command_private.h",
        "runtime/ultra/bridge/sdk_error_receipt.h", "runtime/ultra/third_party/cJSON/cJSON.cpp",
        "runtime/tests/ultra_root_command_identity_test.cpp", "scripts/tests/test-root-command-identity.py"]]
    digests = {str(path.relative_to(ROOT)).replace("\\", "/"): hashlib.sha256(path.read_bytes()).hexdigest() for path in paths}
    texts = [path.read_text(encoding="utf-8-sig") for path in paths[:3]]
    runs = {}
    for name, commands in [("kotlin", kotlin(out, texts[1], texts[2], paths[3])), ("native", native(out, texts[0]))]:
        logs = []
        codes = []
        for command in commands:
            env = os.environ.copy()
            env["PATH"] = str(Path(shutil.which(os.environ.get("CXX") or "g++") or os.environ.get("CXX") or "g++").resolve().parent) + os.pathsep + env["PATH"]
            result = subprocess.run(list(map(str, command)), cwd=ROOT, env=env, capture_output=True,
                text=True, encoding="utf-8", errors="replace", timeout=55)
            codes.append(result.returncode)
            logs.append(result.stdout + result.stderr)
            if result.returncode:
                break
        write(out, name + ".log", "\n".join(logs))
        runs[name] = dict(exitCodes=codes, passed=all(code == 0 for code in codes))
        print(name + ": " + str(codes) + "\n" + "\n".join(logs))
    unchanged = all(hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == digest for name, digest in digests.items())
    record = dict(passed=all(run["passed"] for run in runs.values()) and unchanged, runs=runs,
        recordedAt=datetime.now(timezone.utc).isoformat(), sourceSha256=digests, inputsUnchanged=unchanged,
        deviceAccess=False, sdkBuild=False, apkBuild=False,
        scope="Extracted production Kotlin admission/key/routing/receipt and JNI method/guards/JSON with real cJSON, SDK diagnostic types and RequestAuthModeScope. JNI VM, SDK execution, auth transport, SDK admission and Android observations are host fixtures. No device Root result is asserted.")
    write(out, "RESULT.json", json.dumps(record, indent=2) + "\n")
    raise SystemExit(0 if record["passed"] else 1)


if __name__ == "__main__":
    main()
