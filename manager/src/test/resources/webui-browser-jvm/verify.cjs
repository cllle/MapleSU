const fs = require('fs'), vm = require('vm'), assert = require('assert'), {execFileSync} = require('child_process');
const origin = 'http://127.0.0.1:' + process.argv[2];
const token = process.env.FIXTURE_TOKEN || 'a'.repeat(64);
const base = '/x7k9f/' + token;
const root = process.argv[3] || process.cwd();
const source = process.argv[4] || root + '/manager/src/main/assets/liteweb/a/modbridge.js';
function request(path, data, headers = {}) {
  const args=['--silent','--show-error','--max-time','10',origin+path];
  if(data !== undefined) args.push('-X','POST','-H','Content-Type: application/json','--data-binary','@-');
  Object.entries(headers).forEach(([k,v])=>args.push('-H',k+': '+v));
  return JSON.parse(execFileSync('curl.exe',args,{encoding:'utf8',input:data===undefined?undefined:JSON.stringify(data)}));
}
const info=request('/fixture/info');
const pageResponse=execFileSync('curl.exe',['--silent','--show-error','--include',origin+base+'/modweb/fixture/index.html'],{encoding:'utf8'});
assert.match(pageResponse,/HTTP\/1\.1 200/);assert.match(pageResponse,/Referrer-policy: same-origin/i);assert.match(pageResponse,/Content-security-policy:/i);
assert(pageResponse.indexOf('modbridge.js')<pageResponse.indexOf('window.firstBridge'));
assert(pageResponse.includes(base));
const headers={Origin:origin,Referer:origin+base+'/modweb/fixture/index.html','Sec-Fetch-Site':'same-origin'};
const listeners={},posts=[],downloads=[],blobs=new Map(),errors=[];
let closed=false;
const location={origin,href:origin+base+'/modweb/fixture/index.html',reload(){}};
const document={body:{appendChild(){}},head:{appendChild(){}},querySelector(){return null},documentElement:{requestFullscreen(){return Promise.resolve()}},exitFullscreen(){return Promise.resolve()},createElement(tag){return {click(){downloads.push({fileName:this.download,blob:blobs.get(this.href)})},remove(){}}}};
const Url=class extends URL {};
Url.createObjectURL=blob=>{const id='blob:fixture-'+blobs.size;blobs.set(id,blob);return id};
Url.revokeObjectURL=id=>blobs.delete(id);
const context={__7K_BASE__:base,__7K_MODULE__:{id:'fixture'},location,document,console:{warn(){},log(){},error(e){errors.push(String(e))}},URL:Url,Blob,Uint8Array,atob,TextEncoder,parent:{postMessage(value){posts.push(value)}},open(){},addEventListener(name,cb){listeners[name]=cb},setTimeout(cb,ms){return setTimeout(cb,ms)},clearTimeout,
 XMLHttpRequest:class{open(method,path,async){this.path=path;assert.equal(async,false)}setRequestHeader(){}send(data){this.status=200;this.responseText=JSON.stringify(request(this.path,JSON.parse(data),headers))}},
 fetch(path,opts){return fetch(origin+path,{...opts,headers:{...opts.headers,...headers}})}
};context.window=context;
vm.createContext(context);
vm.runInContext(fs.readFileSync(source,'utf8'),context);
// Evaluate the untouched fixed baseline library, apart from module-export binding for VM access.
const upstream=fs.readFileSync(root+'/third_party/kernelsu/upstream/js/index.js','utf8').replace(/export (function|const) /g,'$1 ')+'\nthis.api={exec,spawn,io,pm,moduleInfo,toast,exit};';
vm.runInContext(upstream,context);
async function until(check){for(let i=0;i<100;i++){if(check())return;await new Promise(r=>setTimeout(r,30))}throw Error('Timed out')}
(async()=>{
 const api=context.api;
 assert.equal(api.io.support,true);assert.equal(api.io.version,1);assert.equal(api.pm.support,true);assert.equal(api.pm.version,1);
 const file=api.io.File(info.moduleDir+'/input.bin');assert.equal(file.exists(),true);assert.equal(file.length(),5);assert.equal(file.getName(),'input.bin');
 const input=api.io.FileInputStream();const handle=input.open(file.getPath());assert(handle);assert.equal(input.available(handle),5);assert.equal(input.read(handle,2),'AQI=');assert.equal(input.read(handle),'AwQF');assert.equal(input.read(handle),'');assert.equal(input.close(handle),true);
 const first=api.io.File(info.moduleDir+'/first.bin'),second=api.io.File(info.moduleDir+'/second.bin');assert.equal(first.getName(),'first.bin');assert.equal(second.getName(),'second.bin');
 const output=api.io.FileOutputStream();let out=output.open(first.getPath());assert(out);assert.equal(output.write(out,'aGk='),true);assert.equal(output.flush(out),true);assert.equal(output.close(out),true);out=output.open(first.getPath(),true);assert.equal(output.writeByte(out,10),true);assert.equal(output.close(out),true);assert.equal(first.length(),3);
 const raf=api.io.RandomAccessFile();const random=raf.open(second.getPath(),'rw');assert(random);raf.writeInt(random,123456);assert.equal(raf.seek(random,0),true);assert.equal(raf.readInt(random),123456);assert.equal(raf.close(random),true);
 assert.equal(api.pm.listPackages('user')[0],'fixture.app');assert.equal(api.pm.getPackagesInfo(['fixture.app'])[0].uid,10042);assert.equal(api.pm.getPackagesInfo(['missing'])[0].error,'Package not found or inaccessible');
 const command=await api.exec('id',{cwd:info.moduleDir,env:{KEY:'value'}});assert.equal(command.errno,17);assert.equal(command.stdout,'line-1\nline-2');
 let nativeCallback;context.nativeReply=(...a)=>nativeCallback=a;context.ksu.exec('id','nativeReply');await until(()=>nativeCallback);assert.equal(nativeCallback[0],17);
 assert.equal(context.ksu.exec('id'),'line-1\nline-2');
 assert.equal(context.ksu.exec('sync-options',JSON.stringify({cwd:info.moduleDir,env:{KEY:'value'}}),null),'line-1\nline-2');
 assert(request('/fixture/info').commands.some(v=>v.startsWith("cd -- '")&&v.includes("export KEY='value'")&&v.endsWith('sync-options')));
 let spawnExit,spawnOut=[];const child=api.spawn('fixture-command',[]);child.stdout.on('data',v=>spawnOut.push(v));child.on('exit',v=>spawnExit=v);await until(()=>spawnExit!==undefined);assert.equal(spawnExit,17);assert.deepEqual(spawnOut,['line-1','line-2']);
 context.ksu.toast('hello');await until(()=>posts.some(p=>p.message==='hello'));
 const chunk=context.ksu_download.startChunkedDownload('../fixture.txt','text/plain');assert(chunk.startsWith('https://blob-download.kernelsu.internal/'));assert.equal(context.ksu_download.writeDownloadChunk(chunk,'dGVzdA=='),true);assert.equal(context.ksu_download.completeChunkedDownload(chunk),true);await until(()=>downloads.length===1);assert.equal(downloads[0].fileName,'fixture.txt');assert.equal(await downloads[0].blob.text(),'test');
 const cancel=context.ksu_download.startChunkedDownload('cancel','text/plain');context.ksu_download.cancelChunkedDownload(cancel);assert.equal(context.ksu_download.writeDownloadChunk(cancel,'AA=='),false);
 context.ksu_download.save('bGVnYWN5','legacy.txt');await until(()=>downloads.length===2);assert.equal(await downloads[1].blob.text(),'legacy');
 assert.throws(()=>context.ksu.noSuchMethod(),/WEBUI_BRIDGE_METHOD_UNAVAILABLE/);assert.equal(context.ksu.then,undefined);assert.equal(context.ksu[Symbol.toStringTag],undefined);
 const large=Buffer.alloc(1024*1024+7,0x42);
 context.ksu_download.save(large.toString('base64'),'large.bin');await until(()=>downloads.length===3);assert.equal((await downloads[2].blob.arrayBuffer()).byteLength,large.length);
 const openSession=()=>request(base+'/api/module/bridge',{id:'fixture',action:'open'},headers).session;
 const raw=(session,root,path,method,args)=>request(base+'/api/module/bridge',{id:'fixture',session,action:'invoke',call:{root,path,method,args}},headers);
 const one=openSession(),two=openSession();
 const streamPath=[['io',[]],['FileOutputStream',[]]];
 const shared=raw(one,'ksu',streamPath,'open',[['string',info.moduleDir+'/scoped.bin']]).value;assert(shared);
 assert.equal(raw(two,'ksu',streamPath,'writeByte',[['string',shared],['number','1']]).value,false);
 request(base+'/api/module/bridge',{id:'fixture',session:one,action:'close'},headers);
 assert.equal(raw(one,'ksu',[], 'moduleInfo',[]).ok,false);
 assert.equal(raw(two,'ksu',[], 'moduleInfo',[]).kind,'value');
 const denied=request(base+'/api/module/bridge',{id:'fixture',action:'open'},{Origin:'http://other.invalid',Referer:headers.Referer});assert.equal(denied.ok,false);
 const page=request(base+'/api/module/bridge',{id:'fixture',action:'open'},headers);assert(page.session);const malformed=request(base+'/api/module/bridge',{id:'fixture',session:page.session,action:'invoke',call:{root:'ksu',path:[],method:'getClass',args:[]}},headers);assert.equal(malformed.kind,'error');
 request('/fixture/retire');assert.throws(()=>file.exists(),/MODULE_WEB_SESSION/);
 listeners.pagehide();await new Promise(r=>setTimeout(r,100));
 const final=request('/fixture/info');assert(final.closed>0);
 console.log(JSON.stringify({passed:true,scope:'Unmodified fixed KSU JS API + current entire modbridge.js -> actual HTTP handler/session/dispatcher/IO/PM/download implementations; platform/libsu explicit JVM fixtures, not device.',coverage:['HTML early bridge / CSP / referrer / external token','IO files / streams / append / RAF','PM package filtering and errors','exec sync / options / callback overloads','spawn stdout / exit','toast','chunk / legacy / >1 MiB download / cancellation','separate IO session handles','origin / annotation guards','module retirement / shell close'],downloadBytes:1048593,closedShells:final.closed}));
})().catch(e=>{console.error(e);process.exitCode=1}).finally(()=>{try{listeners.pagehide&&listeners.pagehide()}catch{};setTimeout(()=>process.exit(process.exitCode||0),150)});

