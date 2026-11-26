package com.maplesu.core.ui.webui

import com.topjohnwu.superuser.Shell
import dev.maplesu.manager.liteweb.LiteWebHttpRequest
import dev.maplesu.manager.liteweb.LiteWebModuleFiles
import dev.maplesu.manager.modules.webui.ModuleWebUiSessions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ExternalWebUiBridgeLifecycleTest {
 private class Fixture:AutoCloseable {
  val root=Files.createTempDirectory("browser-lifecycle-").toFile()
  val module=File(root,"modules/fixture").apply{mkdirs()}
  val current=AtomicBoolean(true)
  var generation="first"
  val shells=java.util.concurrent.CopyOnWriteArrayList<Shell>()
  var shellFactory:()->Shell={Shell()}
  val bridge:ExternalWebUiBridge
  init {
   File(module,"module.prop").writeText("id=fixture\n")
   File(module,"webroot").mkdirs()
   bridge=ExternalWebUiBridge(LiteWebModuleFiles(File(root,"modules")),File(root,"cache"),{emptyList()},current::get,
    sourceIdentity={generation},shellFactory={shellFactory().also(shells::add)})
  }
  fun call(action:String,session:String?=null,extra:JSONObject=JSONObject(),origin:String="http://127.0.0.1:1234"):JSONObject {
   val body=extra.put("action",action).put("id","fixture");session?.let{body.put("session",it)}
   return JSONObject(bridge.handle(LiteWebHttpRequest("POST","/bridge",mapOf("Host" to "127.0.0.1:1234","Content-Type" to "application/json",
    "Origin" to origin,"Referer" to "http://127.0.0.1:1234/x7k9f/TOKEN/modweb/fixture/index.html"),body.toString().toByteArray()),"a".repeat(64)).text())
  }
  fun open()=call("open").getString("session")
  fun invoke(session:String,method:String,args:JSONArray=JSONArray(),root:String="ksu",path:JSONArray=JSONArray())=
   call("invoke",session,JSONObject().put("call",JSONObject().put("root",root).put("path",path).put("method",method).put("args",args)))
  override fun close(){bridge.close();await{shells.all{it.closes.get()>0}};root.deleteRecursively()}
 }
 companion object {
  private fun await(check:()->Boolean){val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!check()){if(System.nanoTime()>until)fail("Lifecycle resource did not retire");Thread.sleep(2)}}
  private fun string(value:String)=JSONArray().put("string").put(value)
 }
 @Test fun closeCancelsTheRealCommandFutureAndDoesNotRetireAnotherDocument(){
  Fixture().use{f->
   val future=CompletableFuture<Shell.Result>()
   f.shellFactory={Shell().also{it.queuedFuture=future}}
   val first=f.open();val second=f.open()
   f.invoke(first,"exec",JSONArray().put(string("id")).put(string("{}" )).put(string("callback")))
   await{f.shells.isNotEmpty()&&f.shells.first().command=="id"}
   assertTrue(f.call("close",first).getBoolean("ok"))
   await{future.isCancelled&&f.shells.first().closes.get()==1}
   assertFalse(f.call("poll",first).getBoolean("ok"))
   assertTrue(f.call("poll",second).getBoolean("ok"))
  }
 }
 @Test fun moduleModeAndSourceChangesRetireRetainedObjects(){
  Fixture().use{f->val page=f.open();ModuleWebUiSessions.committed("fixture");assertFalse(f.call("poll",page).getBoolean("ok"))}
  Fixture().use{f->val page=f.open();dev.maplesu.manager.workmode.ModeRuntime.activation=Any();assertFalse(f.call("poll",page).getBoolean("ok"))}
  Fixture().use{f->val page=f.open();f.generation="replacement";assertFalse(f.call("poll",page).getBoolean("ok"))}
  Fixture().use{f->val page=f.open();f.current.set(false);assertFalse(f.call("poll",page).getBoolean("ok"))}
  Fixture().use{f->val page=f.open();File(f.module,"disable").writeText("");assertFalse(f.call("poll",page).getBoolean("ok"))}
 }
 @Test fun closedDownloadSpoolIsReclaimedAndOriginDenialDoesNotCreateASession(){
  Fixture().use{f->
   assertFalse(f.call("open",origin="http://different.invalid").getBoolean("ok"))
   val page=f.open()
   val id=f.invoke(page,"startChunkedDownload",JSONArray().put(string("file.bin")).put(string("application/octet-stream")),root="ksu_download").getString("value")
   assertTrue(f.invoke(page,"writeDownloadChunk",JSONArray().put(string(id)).put(string("AQID")),root="ksu_download").getBoolean("value"))
   f.call("close",page)
   await{File(f.root,"cache").listFiles()?.isEmpty()!=false}
  }
 }
 @Test fun annotationsRemainTheOnlyCallableObjectSurface(){
  Fixture().use{f->val page=f.open();assertEquals("error",f.invoke(page,"getClass").getString("kind"));assertEquals("error",f.invoke(page,"destroy").getString("kind"))}
 }
 @Test fun retirementDuringSubscriptionAdmissionClosesTheLateRegistration(){
  Fixture().use{f->
   val page=f.open()
   val sessions=f.bridge.javaClass.getDeclaredField("sessions").apply{isAccessible=true}.get(f.bridge) as Map<*,*>
   val retained=checkNotNull(sessions[page])
   f.call("close",page)
   // Exact close-before-registration-publication ordering, without a timing race.
   val subscribe=retained.javaClass.getDeclaredMethod("subscribe").apply{isAccessible=true}
   assertEquals(false,subscribe.invoke(retained))
   val active=ModuleWebUiSessions.javaClass.getDeclaredField("listeners").apply{isAccessible=true}.get(ModuleWebUiSessions) as Map<*,*>
   assertFalse(active.containsKey("fixture"))
  }
 }
}
