"""Replay production root preparation through the real page/router/SDK admission chain."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import jdk_tool

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/specgen"))
from specgen.product import cached_jar


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--old-order", action="store_true", help="Prove the regression against refresh-first preparation")
    parser.add_argument("--module-boot-overlap", action="store_true", help="Replay the module service's exact root probe during a cold service launch")
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    runtime = ROOT / "manager/src/main/java/dev/maplesu/manager/runtime"
    test = ROOT / "manager/src/test/java/dev/maplesu/manager/runtime"
    sources = [runtime / name for name in ["RootExecutionRouter.kt", "BoundedPageRead.kt",
        "UltraRootCommandReceipt.kt", "RootServiceBootstrap.kt", "ultra/UltraRuntimeClient.kt",
        "ultra/UltraReceipt.kt", "ultra/UltraReadinessRules.kt"]]
    sources += [runtime.parent / "ultrakernel/UltraKernelCredential.kt",
        *[test / name for name in ["RootExecutionRouterTest.kt", "RootExecutionResponsivenessTest.kt",
            "BoundedPageReadTest.kt", "UltraRootCommandReceiptTest.kt"]]]
    digests = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    module_probe = None
    if args.module_boot_overlap:
        service = runtime.parent / "modules/LiteModuleBootService.kt"
        service_text = service.read_text(encoding="utf-8-sig")
        digests[service.relative_to(ROOT).as_posix()] = hashlib.sha256(service.read_bytes()).hexdigest()
        module_probe = re.search(r'val root = (.*?)\n\s*check\(root\.ok', service_text, re.S)[1]
        module_probe = module_probe.replace('execute(this,', 'execute(Context(),') \
            .replace('execute(this@LiteModuleBootService,', 'execute(Context(),')
    if args.old_order:
        source = sources[0].read_text(encoding="utf-8")
        fixed = "(UltraEnvironmentProbe.usesUltraBackend(application) ||\n                                UltraEnvironmentProbe.refresh(application, force = false) == UltraEnvironmentProbe.State.Running)"
        old = "(UltraEnvironmentProbe.refresh(application, force = false) == UltraEnvironmentProbe.State.Running ||\n                                UltraEnvironmentProbe.usesUltraBackend(application))"
        assert source.count(fixed) == 1, "Production preparation changed; update the mutation deliberately"
        mutated = out / "RootExecutionRouter.kt"
        mutated.write_text(source.replace(fixed, old), encoding="utf-8")
        sources[0] = mutated

    fixtures = {
        "Context.kt": '''package android.content
class ApplicationInfo { val nativeLibraryDir = "/fixture/no-runtime" }
open class Context { val applicationContext get() = this; val applicationInfo = ApplicationInfo() }
''',
        "Looper.kt": '''package android.os
class Looper {
 companion object {
  private val main = Looper()
  var mainThread: Thread? = null
  @JvmStatic fun myLooper(): Looper? = if (Thread.currentThread() === mainThread) main else null
  @JvmStatic fun getMainLooper(): Looper = main
 }
}
''',
        "Log.kt": '''package android.util
object Log { fun w(tag:String, message:String, error:Throwable?=null):Int = 0 }
''',
        "Mode.kt": '''package dev.maplesu.manager.workmode
enum class WorkMode { LITE, KSU }
object ModeRuntime { var active: WorkMode? = WorkMode.LITE }
''',
        "Runtime.kt": '''package dev.maplesu.manager.runtime
import android.content.Context
object DirectRoot { data class Result(val code:Int, val output:String)
 fun run(context:Context, script:String):Result { error("Legacy transport was selected") } }
class MapleSuCredentialStore(context:Context) { fun load():String? = null }
object HookRootExecutor {
 fun production(context:Context):RootExecutor = object:RootExecutor {
  override val backend = RootBackend.HOOK
  override fun available() = false
  override fun run(script:String):RootExecResult { error("Hook transport was selected") }
 }
}
''',
        "Probe.kt": '''package dev.maplesu.manager.runtime.ultra
import android.content.Context
import dev.maplesu.manager.runtime.PageReadDeadline
import java.util.concurrent.TimeUnit
object UltraSdkCredentials { var key:String? = null; fun read(context:Context):String? = key }
object UltraEnvironmentProbe {
 enum class State { Running, NotInstalled, Fault, Unknown, NoKey }
 var identity = false
 var state = State.Unknown
 var refreshes = 0
 var invalidations = 0
 var requests = 0
 var consumeDeadline = false
 fun isRunning() = state == State.Running
 fun isRootAvailable(context:Context) = identity && isRunning()
 fun usesUltraBackend(context:Context) = identity
 fun refresh(context:Context, force:Boolean):State {
  refreshes++
  if (consumeDeadline) {
   val deadline = checkNotNull(PageReadDeadline.currentNanos())
   while (deadline - System.nanoTime() > 0) TimeUnit.NANOSECONDS.sleep(deadline - System.nanoTime())
  }
  return state
 }
 fun invalidateAvailability() { invalidations++ }
 fun requestRefresh(context:Context) { requests++ }
}
fun interface RootScriptRunner { fun run(script:String):SuExecResult? }
data class SuExecResult(val exitCode:Int,val output:String,val timedOut:Boolean=false,val failure:String?=null)
object UltraSdkLogging { fun invalidate() {}; fun redact(value:String?):String = value.orEmpty() }
object UltraIdentityRecovery { fun suspend(client:UltraRuntimeClient) {} }
''',
        "RootPreparationTest.kt": '''package dev.maplesu.manager.runtime
import android.content.Context
import android.os.Looper
import dev.maplesu.manager.ultrakernel.UltraKernelCredential
import dev.maplesu.manager.runtime.ultra.*
import dev.maplesu.manager.runtime.ultra.bridge.NativeBridge
import dev.maplesu.manager.workmode.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RootPreparationTest {
 @Before fun reset() {
  RootExec.resetForTest()
  ModeRuntime.active = WorkMode.LITE
  Looper.mainThread = null
  UltraSdkCredentials.key = String(UltraKernelCredential.of("A".repeat(48)).withMetadataDisplacement(4096).copySecret(), Charsets.US_ASCII)
  UltraEnvironmentProbe.identity = true
  UltraEnvironmentProbe.state = UltraEnvironmentProbe.State.Unknown
  UltraEnvironmentProbe.refreshes = 0
  UltraEnvironmentProbe.invalidations = 0
  UltraEnvironmentProbe.requests = 0
  UltraEnvironmentProbe.consumeDeadline = false
  NativeBridge.calls = 0; NativeBridge.exitCode = 0; NativeBridge.error = false; NativeBridge.missingMarker = false
  NativeBridge.remainingNanos = 0
 }
 @After fun cleanup() { RootExec.resetForTest(); Looper.mainThread = null; Thread.interrupted() }
 private fun production() = RootExecutionRouter.production(Context())

 @Test fun verifiedIdentityReachesRealSdkWithinThePageBudgetWithoutAnEnvironmentRead() = runBlocking {
  UltraEnvironmentProbe.consumeDeadline = true
  val router = production()
  assertFalse(router.capability().available)
  val finished = CountDownLatch(1)
  val result = try {
   BoundedPageRead<RootExecResult>(200).read("LITE") {
    try { router.execute("printf page-ready") } finally { finished.countDown() }
   }
  } finally { assertTrue(finished.await(2, TimeUnit.SECONDS)) }
  assertTrue(result.detail, result.ok)
  assertEquals("page-ready", result.output)
  assertEquals(0, UltraEnvironmentProbe.refreshes)
  assertEquals(1, NativeBridge.calls)
  assertTrue(NativeBridge.remainingNanos in 1..TimeUnit.MILLISECONDS.toNanos(200))
 }
 @Test fun unknownIdentityStillRequiresARunningObservationBeforeSubmitting() {
  UltraEnvironmentProbe.identity = false
  assertFalse(production().execute("id").ok)
  assertEquals(1, UltraEnvironmentProbe.refreshes)
  assertEquals(0, NativeBridge.calls)
  UltraEnvironmentProbe.state = UltraEnvironmentProbe.State.Running
  assertTrue(production().execute("id").ok)
  assertEquals(2, UltraEnvironmentProbe.refreshes)
  assertEquals(1, NativeBridge.calls)
 }
 @Test fun anUnverifiedFaultSubmitsNoRootCommand() {
  UltraEnvironmentProbe.identity = false
  UltraEnvironmentProbe.state = UltraEnvironmentProbe.State.Fault
  assertFalse(production().execute("id").ok)
  assertEquals(1, UltraEnvironmentProbe.refreshes)
  assertEquals(0, NativeBridge.calls)
 }
 @Test fun aVerifiedFaultUsesTheRealReceiptAndDoesNotGrantRootFromIdentity() {
  UltraEnvironmentProbe.state = UltraEnvironmentProbe.State.Fault
  NativeBridge.error = true
  val result = production().execute("write-once")
  assertFalse(result.ok)
  assertEquals("PRO_ROOT_FAILED:FIXTURE_ROOT_DENIED", result.detail)
  assertEquals(1, NativeBridge.calls)
  assertEquals(0, UltraEnvironmentProbe.refreshes)
  assertEquals(1, UltraEnvironmentProbe.invalidations)
  assertEquals(1, UltraEnvironmentProbe.requests)
 }
 @Test fun ksuAndMainThreadDoNotStartPreparationOrSdkCommands() {
  ModeRuntime.active = WorkMode.KSU
  assertFalse(production().execute("id").ok)
  ModeRuntime.active = WorkMode.LITE
  Looper.mainThread = Thread.currentThread()
  assertFalse(production().execute("id").ok)
  assertEquals(0, UltraEnvironmentProbe.refreshes)
  assertEquals(0, NativeBridge.calls)
 }
 @Test fun expiredPageDeadlineDoesNotPrepareOrSubmit() = runBlocking(PageReadDeadline.context(1)) {
  Thread.sleep(5)
  assertEquals("ROOT_EXECUTION_BUSY", production().execute("id").detail)
  assertEquals(0, UltraEnvironmentProbe.refreshes)
  assertEquals(0, NativeBridge.calls)
 }
 @Test fun commandExitAndMissingReceiptAreNotTurnedIntoSuccessOrReplayed() {
  NativeBridge.exitCode = 17
  val exit = production().execute("exit 17")
  assertFalse(exit.ok)
  assertEquals(17, exit.code)
  assertEquals("PRO_ROOT_COMMAND_EXIT:17", exit.detail)
  assertEquals(0, UltraEnvironmentProbe.invalidations)
  NativeBridge.missingMarker = true
  val missing = production().execute("write-once")
  assertFalse(missing.ok)
  assertEquals("PRO_ROOT_COMMAND_RESULT_UNCONFIRMED", missing.detail)
  assertEquals(2, NativeBridge.calls)
  assertEquals(1, UltraEnvironmentProbe.invalidations)
  assertEquals(0, UltraEnvironmentProbe.refreshes)
 }
}
''',
    }
    if module_probe is not None:
        fixtures["ModuleBootRootPreparationTest.kt"] = '''package dev.maplesu.manager.runtime
import android.content.Context
import android.os.Looper
import dev.maplesu.manager.ultrakernel.UltraKernelCredential
import dev.maplesu.manager.runtime.ultra.*
import dev.maplesu.manager.runtime.ultra.bridge.NativeBridge
import dev.maplesu.manager.workmode.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ModuleBootRootPreparationTest {
 private fun probe() = MODULE_PROBE
 @Test fun coldOfficialServiceLaunchDoesNotLoseTheModuleBootProbe() {
  Looper.mainThread = null
  ModeRuntime.active = WorkMode.LITE
  UltraSdkCredentials.key = String(UltraKernelCredential.of("A".repeat(48)).withMetadataDisplacement(4096).copySecret(), Charsets.US_ASCII)
  UltraEnvironmentProbe.identity = true
  UltraEnvironmentProbe.state = UltraEnvironmentProbe.State.Running
  UltraEnvironmentProbe.consumeDeadline = false
  UltraEnvironmentProbe.refreshes = 0
  NativeBridge.calls = 0; NativeBridge.exitCode = 0; NativeBridge.error = false; NativeBridge.missingMarker = false
  NativeBridge.coldEntered = java.util.concurrent.CountDownLatch(1)
  NativeBridge.coldRelease = java.util.concurrent.CountDownLatch(1)
  RootExec.installForTest(RootExecutionRouter.production(Context()))
  val worker = Executors.newSingleThreadExecutor()
  val timer = Executors.newSingleThreadScheduledExecutor()
  try {
   val first = worker.submit<RootExecResult> { RootExec.execute(Context(), "printf cold-service") }
   assertTrue(NativeBridge.coldEntered.await(2, TimeUnit.SECONDS))
   timer.schedule({ NativeBridge.coldRelease.countDown() }, 1800L, TimeUnit.MILLISECONDS)
   val result = probe()
   assertTrue("MODULE_ROOT_PROBE_FAILED:${result.backend}/${result.code}:${result.detail}", result.ok)
   assertEquals("0", result.output)
   assertTrue(first.get(2, TimeUnit.SECONDS).ok)
   assertEquals(2, NativeBridge.calls)
   assertEquals(0, UltraEnvironmentProbe.refreshes)
  } finally {
   NativeBridge.coldRelease.countDown(); RootExec.resetForTest()
   worker.shutdownNow(); timer.shutdownNow()
  }
 }
 @Test fun aShorterInheritedDeadlineStillRejectsAnExpiredModuleProbe() = runBlocking(PageReadDeadline.context(1)) {
  Thread.sleep(5)
  assertEquals("ROOT_EXECUTION_BUSY", probe().detail)
 }
}
'''.replace('MODULE_PROBE', module_probe)
    generated = []
    for name, value in fixtures.items():
        target = out / name
        target.write_text(value, encoding="utf-8")
        generated.append(target)

    bridge = runtime / "ultra/bridge/NativeBridge.java"
    signatures = re.findall(r"public static native String (\w+)\(([^;]*)\);", bridge.read_text(encoding="utf-8"))
    methods = []
    for name, parameters in signatures:
        body = "throw new AssertionError(\"Unexpected native operation: " + name + "\");"
        if name == "runRootCmd":
            body = '''calls++;
            Long deadline = dev.maplesu.manager.runtime.PageReadDeadline.INSTANCE.currentNanos();
            remainingNanos = deadline == null ? -1 : deadline - System.nanoTime();
            if (error) return "{\\"backend\\":\\"maplesu_ultra\\",\\"ok\\":false,\\"sdkError\\":\\"FIXTURE_ROOT_DENIED\\",\\"sdkErrorCode\\":-9}";
            java.util.regex.Matcher marker = java.util.regex.Pattern.compile("MAPLESU_ROOT_EXIT:([A-Za-z0-9-]+):").matcher(cmd);
            if (!marker.find()) throw new AssertionError("Missing production shell frame");
            String output = "page-ready" + (missingMarker ? "" : "\\nMAPLESU_ROOT_EXIT:" + marker.group(1) + ":" + exitCode + "\\n");
            return new org.json.JSONObject().put("backend", "maplesu_ultra").put("ok", true).put("sdkError", "OK").put("sdkErrorCode", 0)
              .put("data", new org.json.JSONObject().put("result", java.net.URLEncoder.encode(output, java.nio.charset.StandardCharsets.UTF_8))).toString();'''
        methods.append("public static String " + name + "(" + parameters + ") { " + body + " }")
    if args.module_boot_overlap:
        methods = [method.replace('calls++;', '''calls++;
            if (cmd.contains("cold-service")) {
                coldEntered.countDown();
                try { if (!coldRelease.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Cold launch release timed out"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
            }''').replace('String output = "page-ready"', 'String output = (cmd.contains("/system/bin/id -u") ? "0" : "page-ready")')
                   if 'public static String runRootCmd(' in method else method for method in methods]
    java_fixture = out / "NativeBridge.java"
    java_fixture.write_text("package dev.maplesu.manager.runtime.ultra.bridge;\npublic final class NativeBridge {\n"
        "public static int calls, exitCode; public static boolean error, missingMarker; public static long remainingNanos;\n"
        "public static java.util.concurrent.CountDownLatch coldEntered, coldRelease;\n"
        + "\n".join(methods) + "\n}\n", encoding="utf-8")

    deps = [cached_jar(*item) for item in [("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.20"),
        ("org.jetbrains", "annotations", "13.0"), ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.11.0"),
        ("org.json", "json", "20240303"), ("junit", "junit", "4.13.2"), ("org.hamcrest", "hamcrest-core", "1.3")]]
    compiler = cached_jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.4.20")
    java, javac = jdk_tool(), jdk_tool("javac")
    classes = out / "classes"
    classes.mkdir(exist_ok=True)
    existing_java = ROOT / "manager/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"
    assert existing_java.is_dir(), "Previously compiled module-model classes are required"
    cp = os.pathsep.join(map(str, [classes, existing_java, *deps]))
    tests = ["RootPreparationTest", "RootExecutionRouterTest", "RootExecutionResponsivenessTest",
        "BoundedPageReadTest", "UltraRootCommandReceiptTest"]
    if args.module_boot_overlap:
        tests.append("ModuleBootRootPreparationTest")
    commands = [[java, "-Xmx256m", "-cp", os.pathsep.join(map(str, [compiler, *deps])),
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-nowarn", "-jvm-target", "21",
        "-classpath", cp, "-d", classes, *sources, *generated, java_fixture],
        [javac, "-encoding", "UTF-8", "-cp", cp, "-d", classes, java_fixture],
        [java, "-Dfile.encoding=UTF-8", "-cp", cp, "org.junit.runner.JUnitCore",
            *["dev.maplesu.manager.runtime." + name for name in tests]]]
    logs = []
    result = None
    for command in commands:
        result = subprocess.run(list(map(str, command)), cwd=ROOT, capture_output=True, text=True,
            encoding="utf-8", errors="replace", timeout=55)
        logs.append(result.stdout + result.stderr)
        if result.returncode:
            break
    unchanged = all(hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == digest for name, digest in digests.items())
    log = "\n".join(logs)
    (out / "regression.log").write_text(log, encoding="utf-8")
    record = dict(passed=result.returncode == 0 and unchanged, exitCode=result.returncode,
        recordedAt=datetime.now(timezone.utc).isoformat(), oldOrder=args.old_order, deviceAccess=False,
        moduleBootOverlap=args.module_boot_overlap,
        inputsUnchanged=unchanged, sourceSha256=digests,
        scope="Fresh production page deadline, router assembly, SDK admission and shell receipt; environment observations, Android and JNI are fixtures")
    (out / "verification.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    print(log)
    raise SystemExit(result.returncode if unchanged else 1)


if __name__ == "__main__":
    main()
