import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {waitWebNotificationWorker} from './e2e-fixtures/web-notification-browser-ui.mjs';
for(const state of ['absent','installing','wrong-scope','wrong-script','active'])test(`worker preflight ${state} has a finite observation`,async()=>{
  const origin='https://synthetic.invalid';
  const registration=state==='absent'?undefined:{scope:state==='wrong-scope'?origin+'/other/':origin+'/',
    active:{state:state==='installing'?'activating':'activated',scriptURL:origin+(state==='wrong-script'?'/other.js':'/quata-sw.js')}};
  const page={evaluate:async(callback,arg)=>{
    return vm.runInNewContext(`(${callback.toString()})(origin)`,{origin:arg,navigator:{serviceWorker:{getRegistration:async()=>registration}}});
  }};
  if(state==='active')await waitWebNotificationWorker({page,origin,timeoutMs:20});
  else await assert.rejects(waitWebNotificationWorker({page,origin,timeoutMs:20}),/web_notification_worker_unverified/);
});
test('unsettled browser observation cannot hold worker preflight forever',async()=>{
  await assert.rejects(waitWebNotificationWorker({page:{evaluate:()=>new Promise(()=>{})},origin:'https://synthetic.invalid',timeoutMs:20}),/web_notification_worker_unverified/);
});
