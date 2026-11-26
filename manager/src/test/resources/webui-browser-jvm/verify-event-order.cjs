const fs = require('fs'), vm = require('vm'), assert = require('assert');
const root = process.argv[2] || process.cwd();
const timers = [], actions = [];
const context = {console, location:{origin:'http://127.0.0.1:1234'},setTimeout(fn){timers.push(fn);return timers.length},clearTimeout(){},addEventListener(){},
 XMLHttpRequest:class {
  open(){} setRequestHeader(){}
  send(body){
   const request=JSON.parse(body);actions.push(request.action);let result;
   if(request.action==='open')result={session:'fixture',schema:[{spawn:[4],io:[0],pm:[0]},{},{version:[0]},{},{},{},{},{version:[0]}]};
   else if(request.call.method==='io')result={kind:'object',type:2};
   else if(request.call.method==='pm')result={kind:'object',type:7};
   else if(request.call.method==='version')result={kind:'number',value:'1'};
   else if(request.call.method==='spawn'){
    const callback=request.call.args[3][1];
    result={kind:'void',events:[{kind:'data',callback,stream:'stdout',value:'immediate'},{kind:'exit',callback,code:0,stderr:''}]};
   }else throw Error('Unexpected fixture action');
   this.status=200;this.responseText=JSON.stringify(result);
  }
 }
};context.window=context;
vm.createContext(context);
vm.runInContext(fs.readFileSync(root+'/manager/src/main/assets/liteweb/a/modbridge.js','utf8'),context);
vm.runInContext(fs.readFileSync(root+'/third_party/kernelsu/upstream/js/index.js','utf8').replace(/export (function|const) /g,'$1 ')+'\nthis.spawnApi=spawn;',context);
const child=context.spawnApi('fixture'),received=[];
child.stdout.on('data',value=>received.push(value));child.on('exit',value=>received.push(value));
assert.deepEqual(received,[],'Synchronous RPC must not emit before caller attaches listeners');
assert.equal(timers.length,2,'Initial poll plus deferred command callback');timers[1]();
assert.deepEqual(received,['immediate',0]);
console.log(JSON.stringify({passed:true,scope:'Deterministic transport response fixture containing immediate stdout/exit; current entire production JS and unchanged fixed KSU spawn library.',actions}));
