"""Replay the migrated production home panel with virtual time; Android/Compose/SDK are fixtures."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import jdk_tool

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/specgen'))
from specgen.product import cached_jar


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    source = ROOT / 'manager/src/main/java/com/maplesu/core/ui/screen/settings/UltraEnvironmentPanel.kt'
    source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
    policy_source = ROOT / 'manager/src/main/java/dev/maplesu/manager/runtime/ultra/UltraEnvironmentUpgradePolicy.kt'
    policy_hash = hashlib.sha256(policy_source.read_bytes()).hexdigest()
    fixtures = {
        'Android.kt': '''package android.content
open class Context {
 val applicationContext get() = this
 fun getString(id:Int, vararg args:Any) = id.toString()
}
''',
        'Toast.kt': '''package android.widget
import android.content.Context
object Toast {
 const val LENGTH_LONG = 1
 const val LENGTH_SHORT = 0
 fun makeText(context:Context, text:Any, length:Int) = this
 fun show() {}
}
''',
        'Log.kt': '''package android.util
object Log { fun w(tag:String, message:String) = 0 }
''',
        'Annotation.kt': '''package androidx.annotation
annotation class StringRes
''',
        'Compose.kt': '''package androidx.compose.runtime
import kotlin.reflect.KProperty
import kotlinx.coroutines.CoroutineScope
annotation class Composable
annotation class Stable
class MutableState<T>(@Volatile var value:T)
fun <T> mutableStateOf(value:T) = MutableState(value)
operator fun <T> MutableState<T>.getValue(owner:Any?, property:KProperty<*>) = value
operator fun <T> MutableState<T>.setValue(owner:Any?, property:KProperty<*>, value:T) { this.value = value }
fun <T> remember(factory:()->T) = factory()
object ComposeFixture { lateinit var scope:CoroutineScope }
fun rememberCoroutineScope() = ComposeFixture.scope
fun SideEffect(block:()->Unit) = block()
''',
        'ComposeUi.kt': '''package androidx.compose.ui.platform
object LocalContext { val current = android.content.Context() }
''',
        'Strings.kt': '''package androidx.compose.ui.res
fun stringResource(id:Int, vararg args:Any) = id.toString()
''',
        'Lifecycle.kt': '''package androidx.lifecycle.compose
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
object LifecycleFixture { var pause:()->Unit = {}; var keys:List<Any?> = emptyList() }
class ResumeScope { fun onPauseOrDispose(block:()->Unit) { LifecycleFixture.pause = block } }
fun LifecycleResumeEffect(key1:Any?, key2:Any?, key3:Any?, block:ResumeScope.()->Unit) {
 LifecycleFixture.keys = listOf(key1,key2,key3)
 ResumeScope().block()
}
fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateWithLifecycle() = MutableState(value)
fun <T> Flow<T>.collectAsStateWithLifecycle(initialValue:T) = MutableState(runBlocking { first() })
''',
        'Mode.kt': '''package dev.maplesu.manager.workmode
enum class WorkMode { LITE, KSU }
object ModeRuntime { var active:WorkMode? = WorkMode.LITE; val detectionRevision = kotlinx.coroutines.flow.MutableStateFlow(1L) }
''',
        'Upgrade.kt': '''package dev.maplesu.manager.runtime.ultra
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
object UltraInstalledIdentityProbe {
 fun normalizeSha(value:String?):String? = value?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
}
object UltraSdkLogging { fun redact(value:String) = value }
object UltraEnvironmentProbe { val rootAvailability = MutableStateFlow(false) }
object UltraEnvironmentUpgrade {
 enum class Result { SUBMITTED, FAILED, REFUSED }
 var inProgress = false
 val calls = AtomicInteger()
 val answers = ConcurrentLinkedQueue<Any>()
 @Volatile var callbackThenThrow = false
 fun observationScope(context:Context):String? =
  if (dev.maplesu.manager.workmode.ModeRuntime.active == dev.maplesu.manager.workmode.WorkMode.LITE) "fixture-scope" else null
 fun readFacts(context:Context, onInstalledVersion:(String?)->Unit = {}):UltraEnvFacts {
  calls.incrementAndGet()
  if (callbackThenThrow) { onInstalledVersion("5.2.0"); error("fixture interrupted identity read") }
  val next = answers.poll() ?: UltraEnvFacts(running=true)
  if (next is Throwable) throw next
  val facts = (next as UltraEnvFacts).let { it.copy(
   readUnknown = it.installedVersion == null, observationScope = observationScope(context)) }
  onInstalledVersion(facts.installedVersion)
  return facts
 }
 fun upgrade(context:Context) = Result.SUBMITTED
 fun upgradeAllowed(upgrade:UltraEnvUpgrade) = false
 fun persistedFailure(context:Context):String? = null
}
''',
        'PanelTest.kt': '''package com.maplesu.core.ui.screen.settings
import androidx.compose.runtime.ComposeFixture
import androidx.lifecycle.compose.LifecycleFixture
import dev.maplesu.manager.runtime.ultra.*
import dev.maplesu.manager.workmode.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UltraVersionRefreshTest {
 @Before fun reset() {
  UltraEnvironmentUpgrade.calls.set(0); UltraEnvironmentUpgrade.answers.clear(); UltraEnvironmentUpgrade.callbackThenThrow=false
  UltraEnvironmentProbe.rootAvailability.value = false
  ModeRuntime.active=WorkMode.LITE
  UltraEnvironmentPanel::class.java.getDeclaredField("last").apply { isAccessible=true }.set(null,null)
 }
 private fun TestScope.panel(visible:Boolean=true,foreground:Boolean=true):UltraEnvironmentPanel {
  ComposeFixture.scope = backgroundScope
  return rememberUltraEnvironmentPanel(visible,true,foreground)
 }
 private suspend fun TestScope.settle(expected:Int) {
  repeat(300) {
   runCurrent()
   if (UltraEnvironmentUpgrade.calls.get() >= expected) {
    // Let the real IO child return to the virtual main dispatcher before advancing its retry clock.
    repeat(5) { Thread.sleep(2); runCurrent() }
    return
   }
   Thread.sleep(2)
  }
  fail("Expected $expected reads, got ${UltraEnvironmentUpgrade.calls.get()}")
 }
 @Test fun firstUnavailableVersionRecoversOnTheSameHomeVisit() = runTest {
  UltraEnvironmentUpgrade.answers.add(UltraEnvFacts(running=true))
  UltraEnvironmentUpgrade.answers.add(UltraEnvFacts(running=true,installedVersion="5.2.0"))
  val panel = panel(); settle(1); assertNull(panel.version)
  advanceTimeBy(1000); settle(2)
  assertEquals("5.2.0",panel.version)
  advanceTimeBy(60000); runCurrent(); assertEquals(2,UltraEnvironmentUpgrade.calls.get())
 }
 @Test fun aThrownInitialReadAlsoGetsABoundedRetry() = runTest {
  UltraEnvironmentUpgrade.answers.add(IllegalStateException("SDK_BUSY"))
  UltraEnvironmentUpgrade.answers.add(UltraEnvFacts(running=true,installedVersion="5.2.0"))
  val panel=panel(); settle(1); advanceTimeBy(1000); settle(2)
  assertEquals("5.2.0",panel.version)
 }
 @Test fun repeatedUnknownVersionsStopAfterFourRetries() = runTest {
  val panel=panel(); settle(1)
  for ((index,wait) in longArrayOf(1000,2000,4000,8000).withIndex()) { advanceTimeBy(wait); settle(index+2) }
  assertNull(panel.version)
  advanceTimeBy(600000); runCurrent(); assertEquals(5,UltraEnvironmentUpgrade.calls.get())
 }
 @Test fun leavingTheHomePageStopsPendingRetries() = runTest {
  panel(); settle(1); LifecycleFixture.pause()
  advanceTimeBy(60000); runCurrent(); assertEquals(1,UltraEnvironmentUpgrade.calls.get())
 }
 @Test fun changingToNormalModeStopsPendingRetries() = runTest {
  panel(); settle(1); ModeRuntime.active=WorkMode.KSU
  advanceTimeBy(60000); runCurrent(); assertEquals(1,UltraEnvironmentUpgrade.calls.get())
 }
 @Test fun anAlreadyReadVersionSurvivesAMissingFollowupObservation() = runTest {
  UltraEnvironmentUpgrade.answers.add(UltraEnvFacts(running=true,installedVersion="5.2.0"))
  val panel=panel(); settle(1); assertEquals("5.2.0",panel.version)
  UltraEnvironmentUpgrade.answers.add(UltraEnvFacts())
  panel.refresh(); settle(2); assertEquals("5.2.0",panel.version)
  advanceTimeBy(60000); runCurrent(); assertEquals(2,UltraEnvironmentUpgrade.calls.get())
 }
 @Test fun incompleteVersionCallbackWaitsForAScopedObservationAndRetries() = runTest {
  UltraEnvironmentUpgrade.callbackThenThrow=true
  val panel=panel(); settle(1); assertNull(panel.version)
  UltraEnvironmentUpgrade.callbackThenThrow=false
  UltraEnvironmentUpgrade.answers.add(UltraEnvFacts(running=true,installedVersion="5.2.0"))
  advanceTimeBy(1000); settle(2); assertEquals("5.2.0",panel.version)
  advanceTimeBy(60000); runCurrent(); assertEquals(2,UltraEnvironmentUpgrade.calls.get())
 }
 @Test fun anInvisibleOrBackgroundPanelReadsNoSdkFacts() = runTest {
  panel(visible=false); panel(foreground=false)
  runCurrent(); advanceTimeBy(60000); runCurrent(); assertEquals(0,UltraEnvironmentUpgrade.calls.get())
 }
 @Test fun concurrentRefreshSignalsShareTheSameReadAndRetrySeries() = runTest {
  val panel=panel(); panel.refresh(); panel.refresh(); settle(1)
  UltraEnvironmentUpgrade.answers.add(UltraEnvFacts(running=true,installedVersion="5.2.0"))
  advanceTimeBy(1000); settle(2); assertEquals("5.2.0",panel.version)
  assertEquals(2,UltraEnvironmentUpgrade.calls.get())
 }
}
''',
    }
    text = source.read_text(encoding='utf-8-sig')
    resource_names = sorted(set(__import__('re').findall(r'R\.string\.(\w+)', text)))
    fixtures['Resources.kt'] = 'package com.maplesu.core\nobject R { object string {\n' + ''.join(
        f' const val {name} = {index}\n' for index, name in enumerate(resource_names, 1)) + '} }\n'
    generated = []
    for name, content in fixtures.items():
        path = out / name
        path.write_text(content, encoding='utf-8')
        generated.append(path)
    deps = [cached_jar(*item) for item in [
        ('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.20'), ('org.jetbrains', 'annotations', '13.0'),
        ('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.11.0'),
        ('org.jetbrains.kotlinx', 'kotlinx-coroutines-test-jvm', '1.11.0'),
        ('junit', 'junit', '4.13.2'), ('org.hamcrest', 'hamcrest-core', '1.3')]]
    compiler = cached_jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.4.20')
    classes = out / 'classes'
    classes.mkdir(exist_ok=True)
    cp = os.pathsep.join(map(str, [classes, *deps]))
    java = jdk_tool()
    commands = [[java, '-Xmx256m', '-cp', os.pathsep.join(map(str, [compiler, *deps])),
                 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-nowarn',
                 '-jvm-target', '21', '-classpath', cp, '-d', classes, source, policy_source, *generated],
                [java, '-Dfile.encoding=UTF-8', '-cp', cp, 'org.junit.runner.JUnitCore',
                 'com.maplesu.core.ui.screen.settings.UltraVersionRefreshTest']]
    logs = []
    for command in commands:
        result = subprocess.run(list(map(str, command)), capture_output=True, encoding='utf-8', errors='replace', timeout=55)
        logs.append(result.stdout + result.stderr)
        if result.returncode:
            break
    log = '\n'.join(logs)
    (out / 'regression.log').write_text(log, encoding='utf-8')
    unchanged = (hashlib.sha256(source.read_bytes()).hexdigest() == source_hash and
                 hashlib.sha256(policy_source.read_bytes()).hexdigest() == policy_hash)
    report = dict(passed=result.returncode == 0 and unchanged, exitCode=result.returncode,
        source=str(source), sourceSha256=source_hash, policySource=str(policy_source),
        policySha256=policy_hash, inputsUnchanged=unchanged, deviceAccess=False,
        recordedAt=datetime.now(timezone.utc).isoformat(),
        scope='Actual migrated home panel; virtual-time lifecycle/version recovery. Android, Compose rendering, version facts and SDK are fixtures.')
    (out / 'verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(log)
    return result.returncode if unchanged else 1


if __name__ == '__main__':
    raise SystemExit(main())
