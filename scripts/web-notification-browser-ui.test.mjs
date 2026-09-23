import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {verifyWebNotificationActivationReceipt,waitWebNotificationChatPage,waitWebNotificationWorker} from './e2e-fixtures/web-notification-browser-ui.mjs';
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
const chatPage=(route,{closed=false}={})=>({
  isClosed:()=>closed,
  evaluate:async(_callback,expected)=>route===expected,
});
test('notification route observation follows the newly opened exact Chat client',async()=>{
  const original=chatPage('settings'),opened=chatPage('chat/sb:123');
  const selected=await waitWebNotificationChatPage({context:{pages:()=>[original,opened]},threadId:'123',timeoutMs:20});
  assert.equal(selected,opened);
});
test('notification route observation retains an in-place controlled client',async()=>{
  const original=chatPage('chat/sb:456');
  assert.equal(await waitWebNotificationChatPage({context:{pages:()=>[original]},threadId:'456',timeoutMs:20}),original);
});
test('notification route observation rejects missing, closed and wrong clients finitely',async()=>{
  const context={pages:()=>[chatPage('chat/sb:123',{closed:true}),chatPage('chat/sb:999')]};
  await assert.rejects(waitWebNotificationChatPage({context,threadId:'123',timeoutMs:20}),/web_notification_chat_route_unverified/);
});
test('browser boundary keeps native and launch-id activation receipts disjoint',()=>{
  const input={threadId:'123',messageId:'456'},runId='run';
  assert.equal(verifyWebNotificationActivationReceipt({receipt:{runId,...input,clickedViaSystemUi:true},input,runId,
    activationMode:'native-system-ui'}).clickedViaSystemUi,true);
  assert.equal(verifyWebNotificationActivationReceipt({receipt:{runId,...input,clickedViaSystemUi:false,forwardedViaStoredLaunchId:true},input,runId,
    activationMode:'stored-launch-id-control'}).forwardedViaStoredLaunchId,true);
  assert.throws(()=>verifyWebNotificationActivationReceipt({receipt:{runId,...input,clickedViaSystemUi:true},input,runId,
    activationMode:'stored-launch-id-control'}),/web_notification_click_unverified/);
});
