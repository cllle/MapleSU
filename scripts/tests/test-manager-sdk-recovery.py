"""Exercise the real foreground recovery and SU publisher with fixture OS/SDK boundaries."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import jdk_tool

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/specgen"))
from specgen.product import cached_jar


def declaration(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    for end in range(opening + 1, len(source)):
        if source[end] == "{": depth += 1
        if source[end] == "}": depth -= 1
        if depth == 0: return source[start:end + 1]
    raise ValueError(signature)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--source-root", type=Path, default=ROOT)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    source_root = args.source_root.resolve()
    runtime = source_root / "manager/src/main/java/dev/maplesu/manager/runtime"
    app = source_root / "manager/src/main/java/com/maplesu/core/KernelSUApplication.kt"
    session = runtime / "ultra/UltraSdkSession.kt"
    boot_session = runtime / "ultra/UltraBootSdkSession.kt"
    deployment = runtime / "RuntimeDeployment.kt"
    actual = [runtime / "HookSuCompatibility.kt", runtime / "ultra/UltraSelfWhitelist.kt",
              ROOT / "manager/src/main/java/dev/maplesu/manager/runtime/ultra/UltraStartupWarmup.kt",
              ROOT / "manager/src/main/java/dev/maplesu/manager/runtime/ultra/UltraAuthorizationState.kt",
              ROOT / "manager/src/test/java/dev/maplesu/manager/runtime/ultra/UltraSelfWhitelistExecTest.kt",
              ROOT / "manager/src/test/java/dev/maplesu/manager/runtime/RuntimeDeploymentRequestsTest.kt",
              ROOT / "manager/src/test/java/dev/maplesu/manager/runtime/HookSuCompatibilityTest.kt",
              ROOT / "manager/src/test/java/dev/maplesu/manager/runtime/ultra/UltraForegroundRecoveryWiringTest.kt"]
    uid_method = ""
    session_text = session.read_text(encoding="utf-8")
    preparation_method = declaration(session_text, "fun ensure(context: Context): UltraBootSdkInstall.Result")
    boot_install = declaration(boot_session.read_text(encoding="utf-8"), "internal object UltraBootSdkInstall")
    if "fun verifyManagerSu(" in session_text:
        uid_method = declaration(session_text, "fun verifyManagerSu(")
    publication_method = ""
    deployment_text = deployment.read_text(encoding="utf-8")
    if "fun resumeBootSdk(" in deployment_text:
        publication_method = declaration(deployment_text, "fun resumeBootSdk(")
    recovery = declaration(app.read_text(encoding="utf-8"), "private suspend fun recoverUltraEnvironment()")
    fixtures = {
        "Context.kt": """package android.content
class ApplicationInfo { val uid = 10123 }
class Preferences {
 class Editor {
  fun putString(key:String,value:String)=this
  fun commit():Boolean { dev.maplesu.manager.runtime.PreparationFixture.guardStores++;return true }
 }
 fun edit()=Editor()
}
open class Context {
 val applicationContext get() = this
 val applicationInfo = ApplicationInfo()
 val packageName = "dev.maplesu.manager"
 fun getSharedPreferences(name:String,mode:Int)=Preferences()
 companion object { const val MODE_PRIVATE=0 }
}
""",
        "Os.kt": """package android.os
object Looper { fun myLooper():Any? = null; fun getMainLooper():Any = this }
""",
        "Log.kt": """package android.util
object Log {
 const val INFO = 4; const val WARN = 5
 fun i(tag:String,text:String)=0
 fun w(tag:String,text:String,error:Throwable?=null)=0
}
""",
        "Mode.kt": """package dev.maplesu.manager.workmode
enum class WorkMode { LITE, KSU }
object ModeRuntime {
 var active:WorkMode? = WorkMode.LITE
 val latestDetection:Detection? = null
 val detectionComplete = true
 val detectionFailure:Throwable? = null
 suspend fun awaitDetectionSettled(timeout:Long) {}
}
class Trace(val id:String,val ok:Boolean)
class Detection(val trace:List<Trace>,val state:dev.maplesu.manager.workmode.detect.ModeState)
""",
        "Detect.kt": """package dev.maplesu.manager.workmode.detect
enum class ModeState { FOREIGN_SU }
""",
        "Gate.kt": """package dev.maplesu.manager.workmode.gating
object ModeGate { fun allow(id:String){};fun skip(id:String,reason:String){} }
object ModeComponentIds { const val APP_PRO_AUTOPAIR="autopair" }
object ModeGateFormat { object Reason { const val NOT_APPLICABLE="not-applicable" } }
""",
        "Quiesce.kt": """package dev.maplesu.manager.workmode.superuser
object LiteImplantQuiesceGate { fun isBlocked()=false }
""",
        "Switch.kt": """package dev.maplesu.manager.workmode.ui
object WorkModeSwitchSession { val executing=false }
""",
        "Bootstrap.kt": """package dev.maplesu.manager.workmode.boot
object ModeBootstrap { fun retryEnvironmentDetection(){} }
""",
        "RootStore.kt": """package dev.maplesu.manager.modules
import android.content.Context
object UltraRootKeyStore { fun read(context:Context):String?=null }
""",
        "Jailbreak.kt": """package dev.maplesu.manager.jailbreak
object JailbreakStatus { const val STAGE_DAEMON_READY="DAEMON_READY" }
""",
        "Runtime.kt": """package dev.maplesu.manager.runtime
import android.content.Context
import dev.maplesu.manager.runtime.ultra.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
object RecoveryFixture {
 var hook=true
 var current:UltraBootSdkSession?=null
 var bindOnSetup=true
 var environmentReady=true
 var rootUid:Int?=0
 var suWorks=false
 var refreshSucceeds=true
 var execCalls=0
 var rootCalls=0
 var adds=0
 var publicationRequests=0
 var publications=0
 var observedPublicationSession=false
 var currentChecks=0
 var loseAfterRoot=false
 var loseAfterRefresh=false
 var loseAfterEnsure=false
 fun reset(){
  hook=true;current=UltraBootSdkSession("boot","key")
  bindOnSetup=true;environmentReady=true;rootUid=0;suWorks=false;refreshSucceeds=true
  execCalls=0;rootCalls=0;adds=0;publicationRequests=0;publications=0;observedPublicationSession=false
  currentChecks=0;loseAfterRoot=false;loseAfterRefresh=false;loseAfterEnsure=false
  RuntimeDeployment.observed=RuntimeDeployment.Status()
 }
}
object PreparationFixture {
 var failure:String?=null
 var state="NotInstalled"
 var channel=true
 var prepareCalls=0
 var guardStores=0
 var submissions=0
 var loseOnPrepare=false
 fun reset(){
  RecoveryFixture.reset();failure=null;state="NotInstalled";channel=true
  prepareCalls=0;guardStores=0;submissions=0;loseOnPrepare=false
 }
}
object HookRootExecutor { fun production(context:Context)=this;fun available()=RecoveryFixture.hook }
object RootExec {
 class Result(val ok:Boolean=true,val output:String="HOOK_SU_LEGACY_CLEAN",val code:Int=0,val detail:String="")
 fun executeHook(context:Context,script:String)=Result()
}
object SuEntryMode { enum class Mode { TRADITIONAL_COMPAT, COOPERATIVE_MINIMAL } }
object NativeSuDaemon {
 class Outcome(val stage:String="DAEMON_READY",val failure:String?=null)
 fun reproveHookOutcome(context:Context,scheduleDeployment:Boolean)=Outcome()
}
class Binder { val isBinderAlive=true }
object NativeSuDiscovery { fun peek(uid:Int)=Binder() }
data class SuAuditEvent(val backend:String,val event:String,val decision:String,val detail:String="")
object SuAuditLog { fun append(context:Context,event:SuAuditEvent){} }
object RuntimeDeployment {
 data class Status(val clientPath:String?=null,val standardPath:String?=null,
  val brokerReady:Boolean=false,val error:String?=null)
 var observed=Status()
 fun status()=observed
 private val publicationGeneration=AtomicLong(0)
 private class PublicationSession(val entryMode:SuEntryMode.Mode=SuEntryMode.Mode.TRADITIONAL_COMPAT)
 private fun publicationSession(context:Context):PublicationSession? =
  if(RecoveryFixture.current!=null && RecoveryFixture.hook) PublicationSession() else null
 private fun start(context:Context,onReady:(()->Unit)?,retry:RuntimePublicationRetry,expectedSession:PublicationSession?){
  RecoveryFixture.publicationRequests++
  RecoveryFixture.observedPublicationSession=expectedSession!=null
  val result=runCatching { HookSuCompatibility.publish(context,SuEntryMode.Mode.TRADITIONAL_COMPAT,false) }
  observed=result.getOrElse { Status(error=it.message) }
  if(result.isSuccess) RecoveryFixture.publications++
 }
""" + publication_method + "\n}\n" + declaration(deployment_text, "internal data class RuntimePublicationRetry(") +
            "\n" + declaration(deployment_text, "internal class RuntimeDeploymentRequests"),
        "Sdk.kt": """package dev.maplesu.manager.runtime.ultra
import android.content.Context
import android.os.Looper
import android.util.Log
import dev.maplesu.manager.runtime.*
class UltraBootSdkSession(val bootId:String,val key:String,val installSubmitted:Boolean=false) {
 fun submitted()=UltraBootSdkSession(bootId,key,true)
 fun encode()="submitted"
}
object UltraSdkSession {
 fun current(context:Context)=RecoveryFixture.current
 fun client(context:Context,session:UltraBootSdkSession)=UltraRuntimeClient()
 private fun usable(context:Context,session:UltraBootSdkSession):Boolean {
  RecoveryFixture.currentChecks++
  return current(context)?.let {it.bootId==session.bootId && it.key==session.key}==true
 }
 class Result(val ready:Boolean)
 fun ensure(context:Context):Result {
  if(RecoveryFixture.loseAfterEnsure) RecoveryFixture.current=null
  return Result(RecoveryFixture.environmentReady)
 }
""" + uid_method + """
}
internal object UltraSdkPreparationSession {
 private const val PREFS="fixture"
 private const val RECORD="session"
 fun current(context:Context)=RecoveryFixture.current
 private fun usable(context:Context,session:UltraBootSdkSession)=current(context)?.let {
  it.bootId==session.bootId && it.key==session.key
 }==true
 class Gateway(val ready:Boolean=true,val key:String="key",val environment:Boolean=true)
 private fun gateway(context:Context,bootId:String)=Gateway()
 fun client(context:Context,session:UltraBootSdkSession)=UltraRuntimeClient()
""" + preparation_method + "\n}\n" + boot_install + """
class NativeReceipt(val succeeded:Boolean=true,val sdkError:String="OK",
 private val fields:Map<String,String> = emptyMap()) {
 val operation="install_maplesu_environment"
 val sdkErrorCode=if(succeeded) 0 else -1
 fun string(key:String)=fields[key]
 fun array(key:String):List<String>?=if(key=="modules") emptyList() else null
}
class SuSnapshot(val receipt:NativeReceipt=NativeReceipt(),val packages:List<String> = listOf("dev.maplesu.manager")) {
 val ok get()=receipt.succeeded
}
class UltraRuntimeClient(private val key:()->String?={null}) {
 fun testBasics(what:String)=NativeReceipt(what!="Channel" || PreparationFixture.channel)
 fun environmentState()=NativeReceipt(fields=mapOf("state" to PreparationFixture.state))
 fun installEnvironment(mode:String,args:String):NativeReceipt {
  PreparationFixture.submissions++;PreparationFixture.state="Running";return NativeReceipt()
 }
 fun moduleList()=NativeReceipt()
 fun suAuthSnapshot()=SuSnapshot()
 fun addSuAuth(packageName:String):NativeReceipt {
  RecoveryFixture.adds++
  if(RecoveryFixture.refreshSucceeds) RecoveryFixture.suWorks=true
  if(RecoveryFixture.loseAfterRefresh) RecoveryFixture.current=null
  return NativeReceipt(RecoveryFixture.refreshSucceeds,if(RecoveryFixture.refreshSucceeds) "OK" else "ERR_MODULE_READ_FILE")
 }
 fun removeSuAuth(packageName:String):NativeReceipt=error("No revocation during UID refresh")
}
object UltraIdentityRecovery {
 fun uid(client:UltraRuntimeClient):Int? {
  RecoveryFixture.rootCalls++
  if(RecoveryFixture.loseAfterRoot) RecoveryFixture.current=null
  return RecoveryFixture.rootUid
 }
 fun recover(context:Context)="BOOT_SDK_CURRENT"
}
object UltraAuthorization { fun refresh(context:Context)=true }
object UltraEnvironmentAutoSetup {
 data class Result(val installed:Boolean=true,val reason:String="SDK_BIND_UNVERIFIED")
 fun blockedReason(context:Context,bootSession:Boolean):String?=null
 fun hasPostBootUltraSwitchRecovery(context:Context)=false
 fun hasVerifiedUltraIdentity(context:Context)=false
 fun ensure(context:Context):Result {
  if(RecoveryFixture.bindOnSetup) RecoveryFixture.current=UltraBootSdkSession("boot","key")
  return Result(RecoveryFixture.current!=null && RecoveryFixture.environmentReady)
 }
}
object UltraPolicyBackendDeployment {
 fun prepare(context:Context,client:UltraRuntimeClient):String? {
  PreparationFixture.prepareCalls++
  if(PreparationFixture.loseOnPrepare) RecoveryFixture.current=null
  return PreparationFixture.failure
 }
}
object ProcessSuExec {
 class Result(val ok:Boolean,val output:String)
 fun exec(args:List<String>,timeout:Long,environment:Any?):Result {
  RecoveryFixture.execCalls++
  return Result(RecoveryFixture.suWorks,if(RecoveryFixture.suWorks) "0" else "su: Permission denied")
 }
}
object UltraInstallCredentialRecovery { fun hasPendingInstallCredential(context:Context)=false }
object UltraSdkCredentials { fun suspended(context:Context)=false }
object UltraAutoPair { class Result(val paired:Boolean=false,val state:String="Unknown",val reason:String="NO_KEY")
 fun ensure(context:Context)=Result() }
object UltraEnvironmentProbe {
 enum class State { Fault, Running, Unknown }
 class Snapshot(val rootAvailable:Boolean=false,val state:State=State.Unknown)
 fun currentSnapshot(context:Context)=Snapshot()
 fun invalidateAvailability(){}
}
object UltraSdkLogging {
 fun ensureDisabled(context:Context){}
 fun logSafely(priority:Int,message:String){}
}
""",
        "App.kt": """package com.maplesu.core
import android.content.Context
import android.util.Log
import dev.maplesu.manager.workmode.ModeRuntime
import dev.maplesu.manager.workmode.gating.*
import kotlinx.coroutines.ensureActive
class RecoveryApp:Context() {
 companion object { const val TAG="recovery-test" }
 suspend fun recover(){recoverUltraEnvironment()}
""" + recovery + "\n}\n",
        "RecoveryTest.kt": """package dev.maplesu.manager.runtime
import android.content.Context
import com.maplesu.core.RecoveryApp
import dev.maplesu.manager.runtime.ultra.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
class ManagerSdkRecoveryTest {
 @Before fun reset()=RecoveryFixture.reset()
 @Test fun listedPackageWithStaleUidIsRefreshedAndOrdinaryExecIsVerified(){
  val result=HookSuCompatibility.publish(Context(),SuEntryMode.Mode.TRADITIONAL_COMPAT,false)
  assertEquals("/system/bin/su",result.standardPath)
  assertEquals(1,RecoveryFixture.rootCalls)
  assertEquals(1,RecoveryFixture.adds)
  assertEquals(2,RecoveryFixture.execCalls)
 }
 @Test fun workingOrdinaryUidDoesNotWriteTheAllowlist(){
  RecoveryFixture.suWorks=true
  HookSuCompatibility.publish(Context(),SuEntryMode.Mode.TRADITIONAL_COMPAT,false)
  assertEquals(0,RecoveryFixture.rootCalls)
  assertEquals(0,RecoveryFixture.adds)
  assertEquals(1,RecoveryFixture.execCalls)
 }
 @Test fun missingSdkRootNeverRefreshesOrClaimsPublication(){
  RecoveryFixture.rootUid=2000
  val error=runCatching{HookSuCompatibility.publish(Context(),SuEntryMode.Mode.TRADITIONAL_COMPAT,false)}.exceptionOrNull()
  assertEquals("HOOK_SU_EXEC_UNVERIFIED",error?.message)
  assertEquals(0,RecoveryFixture.adds)
 }
 @Test fun failedRefreshNeverLoopsOrClaimsPublication(){
  RecoveryFixture.refreshSucceeds=false
  val error=runCatching{HookSuCompatibility.publish(Context(),SuEntryMode.Mode.TRADITIONAL_COMPAT,false)}.exceptionOrNull()
  assertEquals("HOOK_SU_EXEC_UNVERIFIED",error?.message)
  assertEquals(1,RecoveryFixture.adds)
  assertEquals(1,RecoveryFixture.execCalls)
 }
 @Test fun sessionLossDuringSdkRootPreventsTheUidWrite(){
  RecoveryFixture.loseAfterRoot=true
  val error=runCatching{HookSuCompatibility.publish(Context(),SuEntryMode.Mode.TRADITIONAL_COMPAT,false)}.exceptionOrNull()
  assertEquals("HOOK_SU_EXEC_UNVERIFIED",error?.message)
  assertEquals(0,RecoveryFixture.adds)
 }
 @Test fun sessionLossDuringRefreshPreventsTheSecondExec(){
  RecoveryFixture.loseAfterRefresh=true
  val error=runCatching{HookSuCompatibility.publish(Context(),SuEntryMode.Mode.TRADITIONAL_COMPAT,false)}.exceptionOrNull()
  assertEquals("HOOK_SU_EXEC_UNVERIFIED",error?.message)
  assertEquals(1,RecoveryFixture.adds)
  assertEquals(1,RecoveryFixture.execCalls)
 }
 @Test fun foregroundBindingRecoveryResumesPublicationForThatSession()=runBlocking {
  RecoveryFixture.current=null
  RecoveryApp().recover()
  assertEquals(1,RecoveryFixture.publications)
  assertTrue(RecoveryFixture.observedPublicationSession)
 }
 @Test fun moduleVerificationFailureDoesNotBlockLiveSdkRootAndOrdinaryUidPublication()=runBlocking {
  RecoveryFixture.environmentReady=false
  RecoveryApp().recover()
  assertEquals(1,RecoveryFixture.publications)
  assertEquals(1,RecoveryFixture.rootCalls)
  assertEquals(1,RecoveryFixture.adds)
  assertEquals(2,RecoveryFixture.execCalls)
  assertNull(RuntimeDeployment.observed.error)
 }
 @Test fun boundSessionWithoutWorkingSdkRootNeverClaimsPublication()=runBlocking {
  RecoveryFixture.environmentReady=false
  RecoveryFixture.rootUid=2000
  RecoveryApp().recover()
  assertEquals(1,RecoveryFixture.publicationRequests)
  assertEquals(0,RecoveryFixture.publications)
  assertEquals(0,RecoveryFixture.adds)
  assertEquals("HOOK_SU_EXEC_UNVERIFIED",RuntimeDeployment.observed.error)
 }
 @Test fun sessionLossDuringForegroundValidationDoesNotEnqueuePublication()=runBlocking {
  RecoveryFixture.loseAfterEnsure=true
  RecoveryFixture.suWorks=true
  RecoveryApp().recover()
  assertEquals(0,RecoveryFixture.publicationRequests)
  assertEquals(0,RecoveryFixture.publications)
 }
 @Test fun failedBindingCanRecoverOnALaterForegroundWithoutAnotherExploit()=runBlocking {
  RecoveryFixture.current=null
  RecoveryFixture.bindOnSetup=false
  RecoveryApp().recover()
  assertEquals(0,RecoveryFixture.publications)
  RecoveryFixture.bindOnSetup=true
  RecoveryApp().recover()
  assertEquals(1,RecoveryFixture.publications)
 }
 @Test fun verifiedExistingPublicationIsNotRedeployedOnEveryForeground()=runBlocking {
  RecoveryFixture.suWorks=true
  RuntimeDeployment.observed=RuntimeDeployment.Status(standardPath="/system/bin/su",brokerReady=true)
  RecoveryApp().recover()
  assertEquals(0,RecoveryFixture.publications)
 }
}
""",
        "PreparationTest.kt": """package dev.maplesu.manager.runtime.ultra
import android.content.Context
import dev.maplesu.manager.runtime.PreparationFixture
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
class UltraSdkPreparationReasonTest {
 @Before fun reset()=PreparationFixture.reset()
 @Test fun preparationFailurePreservesTheModuleAndPolicyBoundaryWithoutSubmitting(){
  for(failure in listOf("MODULE_TOOLS_ASSET_UNAVAILABLE","MODULE_TOOLS_TRANSPORT:ERR_MODULE_ROOT",
    "MODULE_TOOLS_EXIT:126:policy","MODULE_POLICY_TOOLS_NOT_REPORTED","POLICY_BACKEND_ASSET_UNVERIFIED")) {
   PreparationFixture.failure=failure
   val result=UltraSdkPreparationSession.ensure(Context())
   assertFalse(result.ready);assertFalse(result.submitted)
   assertEquals("HOTLOAD_PREPARATION_FAILED:$failure",result.reason)
   assertEquals(0,PreparationFixture.guardStores);assertEquals(0,PreparationFixture.submissions)
  }
  assertEquals(5,PreparationFixture.prepareCalls)
 }
 @Test fun failedPreparationCanBeRetriedUntilItSucceedsWithoutReplayingHotload(){
  PreparationFixture.failure="MODULE_TOOLS_RESULT_UNCONFIRMED"
  assertFalse(UltraSdkPreparationSession.ensure(Context()).ready)
  assertEquals(0,PreparationFixture.guardStores)
  PreparationFixture.failure=null
  val result=UltraSdkPreparationSession.ensure(Context())
  assertTrue(result.ready);assertTrue(result.submitted);assertEquals("RUNNING_VERIFIED",result.reason)
  assertEquals(1,PreparationFixture.guardStores);assertEquals(1,PreparationFixture.submissions)
 }
 @Test fun diagnosticPathsAndArbitraryNativeTextStayOutOfTheFailureReason(){
  for(failure in listOf("MODULE_TOOLS_TRANSPORT:ERR_MODULE_ROOT /data/secret/key",
    "MODULE_TOOLS_TRANSPORT:"+"A".repeat(256),"untrusted detail", "")) {
   PreparationFixture.failure=failure
   val result=UltraSdkPreparationSession.ensure(Context())
   assertEquals("HOTLOAD_PREPARATION_FAILED",result.reason)
   assertFalse(result.ready);assertFalse(result.submitted)
   assertEquals(0,PreparationFixture.guardStores);assertEquals(0,PreparationFixture.submissions)
  }
 }
 @Test fun existingReadinessFailuresKeepTheirOwnReasonAndNeverPrepare(){
  PreparationFixture.channel=false
  assertEquals("SDK_CHANNEL_UNVERIFIED",UltraSdkPreparationSession.ensure(Context()).reason)
  assertEquals(0,PreparationFixture.prepareCalls)
 }
 @Test fun runningEnvironmentNeverRunsPreparationAgain(){
  PreparationFixture.state="Running";PreparationFixture.failure="MODULE_TOOLS_RECEIPT_INVALID"
  assertTrue(UltraSdkPreparationSession.ensure(Context()).ready)
  assertEquals(0,PreparationFixture.prepareCalls);assertEquals(0,PreparationFixture.submissions)
 }
 @Test fun sessionChangeAfterPreparationStopsBeforeTheGuard(){
  PreparationFixture.loseOnPrepare=true
  assertEquals("SESSION_CHANGED",UltraSdkPreparationSession.ensure(Context()).reason)
  assertEquals(0,PreparationFixture.guardStores);assertEquals(0,PreparationFixture.submissions)
 }
}
""",
    }
    generated=[]
    for name,text in fixtures.items():
        path=out/name
        path.write_text(text,encoding="utf-8")
        generated.append(path)
    deps=[cached_jar(*item) for item in [
        ("org.jetbrains.kotlin","kotlin-stdlib","2.4.20"),("org.jetbrains","annotations","13.0"),
        ("org.jetbrains.kotlinx","kotlinx-coroutines-core-jvm","1.11.0"),
        ("junit","junit","4.13.2"),("org.hamcrest","hamcrest-core","1.3")]]
    compiler=cached_jar("org.jetbrains.kotlin","kotlin-compiler-embeddable","2.4.20")
    java=jdk_tool()
    classes=out/"classes"
    classes.mkdir(parents=True,exist_ok=True)
    cp=os.pathsep.join(map(str,[classes,*deps]))
    commands=[
        [java,"-Xmx512m","-cp",os.pathsep.join(map(str,[compiler,*deps])),
         "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler","-no-stdlib","-no-reflect","-nowarn",
         "-jvm-target","21","-classpath",cp,"-d",classes,*actual,*generated],
        [java,"-cp",cp,"org.junit.runner.JUnitCore",
         "dev.maplesu.manager.runtime.ManagerSdkRecoveryTest",
         "dev.maplesu.manager.runtime.HookSuCompatibilityTest",
         "dev.maplesu.manager.runtime.ultra.UltraSelfWhitelistExecTest",
         "dev.maplesu.manager.runtime.RuntimeDeploymentRequestsTest",
         "dev.maplesu.manager.runtime.ultra.UltraForegroundRecoveryWiringTest",
         "dev.maplesu.manager.runtime.ultra.UltraSdkPreparationReasonTest"]]
    logs=[]
    for command in commands:
        result=subprocess.run(list(map(str,command)),cwd=source_root,text=True,capture_output=True,
                              encoding="utf-8",errors="replace",timeout=55)
        logs.append(result.stdout+result.stderr)
        if result.returncode: break
    report=dict(passed=result.returncode==0,exitCode=result.returncode,output="\n".join(logs),
                scope="Real SU publisher, self-whitelist, foreground recovery, session UID repair and HotLoad preparation/install functions; OS/SDK boundaries are fixtures",
                sources={str(path.relative_to(source_root) if path.is_relative_to(source_root) else path):hashlib.sha256(path.read_bytes()).hexdigest()
                         for path in [*actual,app,session,boot_session,deployment]})
    (out/"result.json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    print(report["output"])
    raise SystemExit(result.returncode)


if __name__=="__main__": main()
