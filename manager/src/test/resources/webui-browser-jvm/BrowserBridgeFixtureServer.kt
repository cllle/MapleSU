package com.maplesu.core.ui.webui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.maplesu.core.data.model.AppInfo
import com.topjohnwu.superuser.Shell
import com.sun.net.httpserver.HttpServer
import dev.maplesu.manager.liteweb.*
import dev.maplesu.manager.liteweb.daemon.DaemonBridgeToken
import dev.maplesu.manager.liteweb.daemon.DaemonRequestHandler
import dev.maplesu.manager.liteweb.daemon.LiteWebDaemonConfig
import dev.maplesu.manager.liteweb.daemon.LiteWebDaemonCore
import dev.maplesu.manager.modules.webui.ModuleWebUiSessions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/** Actual production handler/dispatcher/object calls over HTTP; only platform/libsu are fixtures. */
object BrowserBridgeFixtureServer {
 @JvmStatic fun main(args:Array<String>){
  val temp=Files.createTempDirectory("browser-bridge-fixture").toFile()
  val modules=File(temp,"modules").apply{mkdirs()}
  val module=File(modules,"fixture").apply{mkdirs()}
  File(module,"module.prop").writeText("id=fixture\nname=Fixture Module\nversion=1\n")
  File(module,"webroot").mkdirs()
  File(module,"webroot/index.html").writeText("<html><head><script>window.firstBridge=typeof ksu</script></head><body>fixture</body></html>")
  File(module,"input.bin").writeBytes(byteArrayOf(1,2,3,4,5))
  val current=AtomicBoolean(true)
  val shell=java.util.concurrent.CopyOnWriteArrayList<Shell>()
  val files=LiteWebModuleFiles(modules)
  val bridge=ExternalWebUiBridge(files,File(temp,"cache"),{
   listOf(AppInfo("Fixture App",PackageInfo().also{it.packageName="fixture.app";it.versionName="1";it.applicationInfo=ApplicationInfo().also{a->a.uid=10042}},null))
  },current::get,sourceIdentity={"fixture"},shellFactory={Shell().also{shell.add(it)}})
  val daemon=args.contains("--daemon")
  val token=if(daemon) "FixtureLegacyToken12345" else "a".repeat(64)
  val handlerToken=if(daemon) DaemonBridgeToken.derive(token) else token
  val handler=LiteWebRequestHandler({handlerToken},object:LiteWebBackend{
   override fun status()=LiteWebStatusView(rootAvailable=true,moduleCount=1)
   override fun modulesJson()="[]"
   override fun superusersJson()="[]"
   override fun sulog()=LiteWebSulogView(available=false)
   override fun toggleModule(moduleId:String,enabled:Boolean)=LiteWebToggleView.Failed("FIXTURE","not used")
   override fun assets()=object:LiteWebAssets{override fun read(relativePath:String):ByteArray?=null}
   override fun moduleBridgePort()=bridge
   override fun moduleWebFile(moduleId:String,relativePath:String)=files.read(moduleId,relativePath)
  },documentTokenProvider={token})
  val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
  server.executor=java.util.concurrent.Executors.newCachedThreadPool{Thread(it).apply{isDaemon=true}}
  val wire:LiteWebHandler=if(daemon) DaemonRequestHandler(LiteWebDaemonCore(LiteWebDaemonConfig(server.address.port,token,"LITE"),handler=handler)) else handler
  val stop=CountDownLatch(1)
  server.createContext("/"){exchange->
   val path=exchange.requestURI.path
   val result=when(path){
    "/fixture/info"->LiteWebHttpResponse.json(200,JSONObject().put("moduleDir",module.path).put("shells",shell.size).put("closed",shell.sumOf{it.closes.get()}).put("commands",JSONArray(shell.map{it.command})).toString())
    "/fixture/retire"->{ModuleWebUiSessions.committed("fixture");LiteWebHttpResponse.ok()}
    "/fixture/mode"->{dev.maplesu.manager.workmode.ModeRuntime.activation=Any();LiteWebHttpResponse.ok()}
    "/fixture/source"->{current.set(false);LiteWebHttpResponse.ok()}
    "/fixture/stop"->{stop.countDown();LiteWebHttpResponse.ok()}
    else->wire.handle(LiteWebHttpRequest(exchange.requestMethod,exchange.requestURI.toString(),exchange.requestHeaders.mapValues{it.value.first()},exchange.requestBody.readBytes()))
   }
   exchange.responseHeaders.set("Content-Type",result.contentType)
   result.headers.forEach{(key,value)->exchange.responseHeaders.set(key,value)}
   exchange.sendResponseHeaders(result.status,result.body.size.toLong());exchange.responseBody.use{it.write(result.body)}
  }
  server.start();println("PORT=${server.address.port}");System.out.flush()
  try{stop.await()}finally{bridge.close();server.stop(0);temp.deleteRecursively()}
 }
}
