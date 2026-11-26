package com.topjohnwu.superuser
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
open class Shell:java.io.Closeable {
 val isRoot=true
 class Builder {fun build(vararg command:String)=Shell();companion object{fun create()=Builder()}}
 val closes=AtomicInteger();var command="";var result=Result(17,listOf("line-1","line-2"),listOf("stderr"));var newJobError:Exception?=null;var execError:Exception?=null;var enqueueError:Exception?=null
 var fastCmdGate:(()->Unit)?=null;var closeGate:(()->Unit)?=null;var enqueueGate:(()->Unit)?=null;var queuedFuture:Future<Result>?=null;var outputs:MutableList<String>?=null;var errors:MutableList<String>?=null
 override fun close(){closeGate?.invoke();closes.incrementAndGet()}
 fun newJob():Job {newJobError?.let{throw it};return Job(this)}
 class Result(val code:Int,val out:List<String>,val err:List<String>){val isSuccess get()=code==0}
 class Job(private val shell:Shell) {
  fun add(command:String):Job {shell.command=command;return this}
  fun to(out:MutableList<String>?,err:MutableList<String>?):Job {shell.outputs=out;shell.errors=err;return this}
  fun exec():Result {shell.execError?.let{throw it};return shell.result}
  fun enqueue():Future<Result> {shell.enqueueError?.let{throw it};shell.enqueueGate?.invoke();shell.result.out.forEach{shell.outputs?.add(it)};shell.result.err.forEach{shell.errors?.add(it)};return shell.queuedFuture?:CompletableFuture.completedFuture(shell.result)}
 }
}
abstract class CallbackList<E>(private val executor:java.util.concurrent.Executor):java.util.AbstractList<E>() {
 override val size get()=0
 override fun get(index:Int):E=throw IndexOutOfBoundsException()
 override fun add(element:E):Boolean {executor.execute{onAddElement(element)};return true}
 abstract fun onAddElement(s:E)
}
object ShellUtils {fun escapedString(v:String)="'"+v.replace("'","'\"'\"'")+"'";fun fastCmd(s:Shell,c:String):String {s.command=c;s.fastCmdGate?.invoke();s.execError?.let{throw it};return s.result.out.joinToString("\n")}}

