"""Compile the actual package grant path; inject only Android and SDK boundaries."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import jdk_tool

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/specgen'))
from specgen.product import cached_jar

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--out',required=True,type=Path)
a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
pro=ROOT/'manager/src/main/java/dev/maplesu/manager/runtime/ultra'
actual=[];hashes={}
for name in ('UltraAuthorization.kt','UltraAuthorizationState.kt'):
 source=pro/name;b=source.read_bytes();dest=out/name;dest.write_bytes(b);actual.append(dest)
 hashes[name]=hashlib.sha256(b).hexdigest()
fixtures={
'Android.kt':'''package android.content
open class Context {
 val applicationContext get()=this
 val packageManager=PackageManager()
}
class PackageManager { fun getPackagesForUid(uid:Int):Array<String>?=arrayOf("test.target") }
''',
'Log.kt':'''package android.util
object Log { fun w(tag:String,message:String)=0;fun i(tag:String,message:String)=0 }
''',
'Boundary.kt':'''package dev.maplesu.manager.runtime.ultra
import android.content.Context
const val SU_DELIVERY_INCOMPLETE="ERR_SU_DELIVERY_INCOMPLETE"
interface SuAuthPort { fun snapshot():List<String>?;fun add(name:String):String;fun remove(name:String):String }
object Fixture {
 var state=UltraEnvironmentProbe.State.Unknown;var root=true;var blocked:String?=null
 var installedState=UltraEnvironmentProbe.State.Running;var installResult=false;var installThrows=false
 var changeScopeOnInstall=false;var installs=0;var writes=0
 val calls=mutableListOf<String>();val packages=mutableSetOf<String>()
 fun reset(){state=UltraEnvironmentProbe.State.Unknown;root=true;blocked=null;installedState=UltraEnvironmentProbe.State.Running;installResult=false;installThrows=false;changeScopeOnInstall=false;installs=0;writes=0;calls.clear();packages.clear()}
}
object UltraEnvironmentProbe {
 enum class State {Running,NotInstalled,Fault,Unknown,NoKey}
 fun usesUltraBackend(c:Context)=true
 fun refresh(c:Context,force:Boolean=true):State {Fixture.calls+="refresh:$force";return Fixture.state}
 fun isRootAvailable(c:Context)=Fixture.root
 fun invalidateAvailability(){}
 fun requestRefresh(c:Context){}
}
object UltraEnvironmentAutoSetup {
 data class Result(val attempted:Boolean,val installed:Boolean,val reason:String)
 fun blockedReason(c:Context):String?=Fixture.blocked
 fun ensure(c:Context):Result {Fixture.calls+="initialize";Fixture.installs++;if(Fixture.installThrows) error("transport failed");Fixture.state=Fixture.installedState;if(Fixture.changeScopeOnInstall)Fixture.blocked="BOOT_CHANGED";return Result(true,Fixture.installResult,"fixture")}
}
class UltraRuntimeClient
object UltraSdkCredentials {fun client(c:Context)=UltraRuntimeClient()}
object UltraSuAuthCache {fun publish(size:Int){}}
object UltraSelfWhitelist {
 fun clientPort(c:UltraRuntimeClient)=object:SuAuthPort {
  override fun snapshot()=Fixture.packages.toList()
  override fun add(name:String):String {Fixture.calls+="add:$name";Fixture.writes++;if(Fixture.state!=UltraEnvironmentProbe.State.Running)return "ERR_MODULE_STORAGE_NOT_FOUND";Fixture.packages+=name;return "OK"}
  override fun remove(name:String):String {Fixture.calls+="remove:$name";Fixture.writes++;Fixture.packages-=name;return "OK"}
 }
}
''',
'GrantInitializationTest.kt':'''package dev.maplesu.manager.runtime.ultra
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
class GrantInitializationTest {
 private val context=Context()
 @Before fun before(){Fixture.reset();UltraAuthorization.refresh(context);Fixture.calls.clear()}
 @Test fun reportedUidZeroUnknownMustStartEnvironmentBeforeGrant(){
  assertTrue("Root-capable Unknown must initialize the SU environment, not write absent storage",UltraAuthorization.grant(context,"test.target"))
  assertEquals(1,Fixture.installs);assertEquals(1,Fixture.writes)
  assertTrue(Fixture.calls.indexOf("initialize")<Fixture.calls.indexOf("add:test.target"))
  assertTrue(UltraAuthorization.isGranted("test.target"))
 }
 @Test fun notInstalledMustActuallyInstallBeforeGrant(){Fixture.state=UltraEnvironmentProbe.State.NotInstalled;assertTrue(UltraAuthorization.grant(context,"test.target"));assertEquals(1,Fixture.installs)}
 @Test fun runningDoesNotReinstall(){Fixture.state=UltraEnvironmentProbe.State.Running;assertTrue(UltraAuthorization.grant(context,"test.target"));assertEquals(0,Fixture.installs)}
 @Test fun faultAndNoKeyNeverInstallOrWrite(){for(s in listOf(UltraEnvironmentProbe.State.Fault,UltraEnvironmentProbe.State.NoKey)){Fixture.state=s;assertFalse(UltraAuthorization.grant(context,"test.target"));assertEquals(0,Fixture.installs);assertEquals(0,Fixture.writes)}}
 @Test fun protectedScopeNeverProbesInstallsOrWrites(){for(reason in listOf("SUSPENDED","SWITCH_PENDING","MODE_KSU","ROOT_CONFLICT")){Fixture.blocked=reason;assertFalse(UltraAuthorization.grant(context,"test.target"));assertTrue(Fixture.calls.isEmpty())}}
 @Test fun failedInitializationDoesNotWriteAbsentStorage(){Fixture.installedState=UltraEnvironmentProbe.State.Unknown;assertFalse(UltraAuthorization.grant(context,"test.target"));assertEquals(1,Fixture.installs);assertEquals(0,Fixture.writes)}
 @Test fun changedScopeAfterInitializationStopsGrant(){Fixture.changeScopeOnInstall=true;assertFalse(UltraAuthorization.grant(context,"test.target"));assertEquals(1,Fixture.installs);assertEquals(0,Fixture.writes)}
 @Test fun exceptionDuringInitializationStopsGrant(){Fixture.installThrows=true;assertFalse(UltraAuthorization.grant(context,"test.target"));assertEquals(1,Fixture.installs);assertEquals(0,Fixture.writes)}
 @Test fun freshRunningObservationWinsOverUnrelatedInstallerResult(){Fixture.installResult=false;assertTrue(UltraAuthorization.grant(context,"test.target"));assertEquals(1,Fixture.installs)}
 @Test fun runningWithoutLiveRootNeverGrants(){Fixture.state=UltraEnvironmentProbe.State.Running;Fixture.root=false;assertFalse(UltraAuthorization.grant(context,"test.target"));assertEquals(0,Fixture.writes)}
 @Test fun pageRefreshDoesNotInitialize(){assertTrue(UltraAuthorization.refresh(context));assertEquals(0,Fixture.installs);assertEquals(0,Fixture.writes)}
 @Test fun blankPackageDoesNotInitialize(){assertFalse(UltraAuthorization.grant(context," "));assertEquals(0,Fixture.installs);assertEquals(0,Fixture.writes)}
}
'''}
for name,s in fixtures.items():
 file=out/name;file.write_text(s,encoding='utf-8');actual.append(file)
deps=[cached_jar(*v) for v in [('org.jetbrains.kotlin','kotlin-stdlib','2.4.20'),('org.jetbrains','annotations','13.0'),('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.11.0'),('junit','junit','4.13.2'),('org.hamcrest','hamcrest-core','1.3')]]
compiler=cached_jar('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.4.20')
java=jdk_tool()
classes=out/'classes';classes.mkdir();cp=os.pathsep.join(map(str,[classes,*deps]));logs=[]
commands=[[java,'-Xmx256m','-cp',os.pathsep.join(map(str,[compiler,*deps])),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-jvm-target','21','-classpath',cp,'-d',classes,*actual],[java,'-cp',cp,'org.junit.runner.JUnitCore','dev.maplesu.manager.runtime.ultra.GrantInitializationTest']]
for cmd in commands:
 result=subprocess.run(list(map(str,cmd)),capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=45);logs.append(result.stdout+result.stderr)
 if result.returncode:break
receipt=dict(passed=result.returncode==0,exitCode=result.returncode,output='\n'.join(logs),sourceSha256=hashes,deviceAcceptance=False,scope='Actual package grant method and authorization state, Android/SDK boundary fixtures; verifies installation is invoked before native grant and no absent-storage grant occurs.')
(out/'result.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8');print(receipt['output']);raise SystemExit(result.returncode)
