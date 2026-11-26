package com.topjohnwu.superuser.io
import java.io.File
import com.topjohnwu.superuser.Shell
open class SuFile(path:String):File(path){
 private var boundShell:Shell?=null;val shell get()=boundShell!!;fun setShell(value:Shell){boundShell=value}
 fun newInputStream():java.io.InputStream=inputStream()
 fun newOutputStream(append:Boolean):java.io.OutputStream {if(!append) clear();return java.io.FileOutputStream(this,append)}
 open fun clear():Boolean {java.io.FileOutputStream(this,false).close();return true}
 fun deleteRecursive()=deleteRecursively()
 val isBlock=false;val isCharacter=false;val isSymlink get()=java.nio.file.Files.isSymbolicLink(toPath())
 fun createNewSymlink(target:String):Boolean {java.nio.file.Files.createSymbolicLink(toPath(),File(target).toPath());return true}
 fun createNewLink(existing:String):Boolean {java.nio.file.Files.createLink(toPath(),File(existing).toPath());return true}
}
object SuFileInputStream {@JvmStatic fun open(file:SuFile)=file.newInputStream()}
class SuRandomAccessFile(path:String,mode:String):java.io.RandomAccessFile(path,mode){companion object {@JvmStatic fun open(file:SuFile,mode:String)=SuRandomAccessFile(file.path,mode)}}
