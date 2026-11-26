"""Actual store and line transport; explicit Java pipe fixture at Android socket boundary."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools'))
import native_toolchain
parser = argparse.ArgumentParser()
parser.add_argument('--out', type=Path, required=True)
parser.add_argument('--java', default=str(native_toolchain.jdk_tool('java')))
parser.add_argument('--gradle-cache', type=Path, default=Path.home()/'.gradle/caches/modules-2/files-2.1')
args = parser.parse_args()
out = args.out.resolve()
out.mkdir(parents=True, exist_ok=False)
java, cache = args.java, args.gradle_cache
def jar(group, name, version): return next((cache/group/name/version).rglob('*.jar'))
jars = [jar(g,n,v) for g,n,v in [('org.jetbrains.kotlin','kotlin-stdlib','2.4.20'),('junit','junit','4.13.2'),('org.hamcrest','hamcrest-core','1.3')]]
compiler = os.pathsep.join(map(str, [jar(g,n,v) for g,n,v in [
 ('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.4.20'),
 ('org.jetbrains.kotlin','kotlin-stdlib','2.4.20'),
 ('org.jetbrains','annotations','13.0'),
 ('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.11.0')]]))
fixture = out/'fixtures'; fixture.mkdir()
fixtures = {
 'RootShellLease.java': '''package dev.maplesu.manager.jailbreak;
import java.io.*;
public final class RootShellLease extends Process implements Closeable {
 private final InputStream input; private final OutputStream output; private boolean live=true, handover=false;
 public RootShellLease(InputStream i,OutputStream o){input=i;output=o;}
 public InputStream getInputStream(){return input;} public InputStream getErrorStream(){return InputStream.nullInputStream();}
 public OutputStream getOutputStream(){return output;} public boolean isAlive(){return live;}
 public LeaseProtocol.Identity identity(){return null;} public LeaseProtocol.Restore restore(){return null;}
 public void confirmHandover(){handover=true;} public boolean handedOver(){return handover;}
 public int waitFor(){return 0;} public int exitValue(){return 0;} public void destroy(){close();}
 public void close(){live=false;try{input.close();}catch(IOException e){}try{output.close();}catch(IOException e){}}
}''',
 'Shell.java': '''package com.topjohnwu.superuser;
public class Shell implements AutoCloseable {
 public boolean isAlive(){return false;} public boolean isRoot(){return false;} public void close(){}
 public static class Builder {
  public static Builder create(){return new Builder();}
  public Builder setContext(android.content.Context c){return this;} public Builder setTimeout(long t){return this;}
  public Shell build(Process p){return new Shell();}
 }
}''',
 'Context.java': '''package android.content;
public class Context { public Context getApplicationContext(){return this;} }''',
 'NativeSuDirectoryProvider.java': '''package dev.maplesu.manager.runtime;
public class NativeSuDirectoryProvider {}'''}
paths=[]
for name, content in fixtures.items():
    p=fixture/name; p.write_text(content,encoding='utf-8'); paths.append(p)
protocol=ROOT/'jailbreak-providers/android/src/main/java/dev/maplesu/manager/jailbreak/LeaseProtocol.java'
sources=[ROOT/'manager/src/main/java/dev/maplesu/manager'/n for n in ['jailbreak/JailbreakLeaseStore.kt','jailbreak/JailbreakLeaseTransport.kt','runtime/JailbreakLeaseArgv.kt']]
test=ROOT/'manager/src/test/resources/jailbreak-liveness-jvm/JailbreakLeaseLivenessTest.kt'
def identity(p): return {'path':str(p),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
receipt={'scope':'Real current store/transport/argv and current protocol; explicit Java RootShellLease pipe/Shell fixtures; no Android execution, native provider exploit, or device claim.',
         'inputs':[identity(p) for p in sources+[test,protocol]],'fixtures':[identity(p) for p in paths],'steps':[]}
def run(name,args):
    p=subprocess.run(list(map(str,args)),cwd=ROOT,capture_output=True,timeout=60)
    (out/(name+'.log')).write_bytes(p.stdout+p.stderr)
    receipt['steps'].append({'name':name,'argv':list(map(str,args)),'exitCode':p.returncode})
    return p.returncode
code=run('fixture-javac',[Path(java).parent/('javac.exe' if os.name=='nt' else 'javac'),'-proc:none','-d',out/'fixture-classes',*paths,protocol])
if code==0:
    code=run('compile',[java,'-Xmx256m','-XX:+UseSerialGC','-cp',compiler,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
     '-no-stdlib','-no-reflect','-jvm-target','17',
     '-classpath',os.pathsep.join(map(str,[out/'fixture-classes',*jars])),
     '-d',out/'classes',*sources,test])
if code==0:
    code=run('junit',[java,'-Xmx128m','-cp',os.pathsep.join(map(str,[out/'classes',out/'fixture-classes',*jars])),
     'org.junit.runner.JUnitCore','dev.maplesu.manager.jailbreak.JailbreakLeaseLivenessTest'])
receipt['exitCode']=code
receipt['inputsUnchanged']=receipt['inputs']==[identity(p) for p in sources+[test,protocol]]
(out/'RESULT.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'steps':[{'name':s['name'],'exitCode':s['exitCode']} for s in receipt['steps']]}))
print((out/(receipt['steps'][-1]['name']+'.log')).read_text(encoding='utf-8',errors='replace')[-5000:])
raise SystemExit(code)
