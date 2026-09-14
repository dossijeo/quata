import test from 'node:test';
import assert from 'node:assert/strict';
import {EventEmitter} from 'node:events';
import {createWebNotificationBrowserTransport} from './e2e-fixtures/web-notification-browser-transport.mjs';
const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve};};
async function setup() {
  const context=new EventEmitter();let handler;
  context.route=async(pattern,callback)=>{assert.equal(pattern,'https://example.invalid/**');handler=callback;};
  const transport=await createWebNotificationBrowserTransport({context,backendUrl:'https://example.invalid'});
  function request(path='/rest/v1/rpc/quata_chat_send_message',body={p_message:'private'},status=200) {
    const calls=[],value={url:()=>`https://example.invalid${path}`,method:()=> 'POST',postDataJSON:()=>body,response:async()=>({status:()=>status})};
    const route={request:()=>value,abort:async()=>calls.push('abort'),continue:async()=>calls.push('continue')};
    return {value,route,calls,run:()=>handler(route)};
  }
  return {context,transport,request};
}
test('original Send waits for custody and HTTP completion',async()=>{
  const {context,transport,request}=await setup(),checkpoint=deferred(),entered=deferred(),r=request();
  transport.arm('message',async payload=>{assert.deepEqual(payload,{p_message:'private'});entered.resolve();await checkpoint.promise;return {captured:true};});
  const running=r.run();await entered.promise;assert.deepEqual(r.calls,[]);
  checkpoint.resolve();await running;assert.deepEqual(r.calls,['continue']);
  assert.equal(transport.diagnostics().pending,1);
  context.emit('requestfinished',r.value);await new Promise(resolve=>setImmediate(resolve));
  assert.deepEqual(await transport.gateAndDrain(),{settled:true});
});
test('unarmed Send and duplicate Send are aborted',async()=>{
  const first=await setup(),r=first.request();await r.run();assert.deepEqual(r.calls,['abort']);
  const second=await setup();second.transport.arm('message',async()=>({captured:true}));
  const a=second.request(),b=second.request();await a.run();await b.run();
  assert.deepEqual(a.calls,['continue']);assert.deepEqual(b.calls,['abort']);
  assert.equal(second.transport.diagnostics().uncertain,true);
});
test('subscription passes only nested original payload to durable capture',async()=>{
  const {transport,request}=await setup(),subscription={endpoint:'https://private.invalid',keys:{auth:'private'}};
  transport.arm('subscription',async payload=>{assert.equal(payload,subscription);return {captured:true};});
  const r=request('/functions/v1/quata-web-push',{action:'subscribe',subscription});await r.run();
  assert.deepEqual(r.calls,['continue']);assert.equal(transport.diagnostics().subscriptionCaptured,true);
  assert.equal(JSON.stringify(transport.diagnostics()).includes('private'),false);
});
test('checkpoint failure never forwards original request',async()=>{
  const {transport,request}=await setup();transport.arm('message',async()=>{throw Error('private detail');});
  const r=request();await r.run();assert.deepEqual(r.calls,['abort']);assert.deepEqual(await transport.gateAndDrain(),{settled:false});
});
test('unknown mutation cannot bypass message custody',async()=>{
  const {transport,request}=await setup();
  const r=request('/rest/v1/chat_messages',{body:'private'});await r.run();
  assert.deepEqual(r.calls,['abort']);assert.deepEqual(await transport.gateAndDrain(),{settled:false});
});
test('shutdown during checkpoint prevents a late producer',async()=>{
  const {transport,request}=await setup(),checkpoint=deferred(),entered=deferred();
  transport.arm('message',async()=>{entered.resolve();await checkpoint.promise;return {captured:true};});
  const r=request(),running=r.run();await entered.promise;
  const draining=transport.gateAndDrain();checkpoint.resolve();await running;
  assert.deepEqual(r.calls,['abort']);assert.deepEqual(await draining,{settled:true});
  const late=request();await late.run();assert.deepEqual(late.calls,['abort']);
});
for(const mode of ['requestfailed','http-error','continue-error','timeout'])test(`${mode} cannot authorize cleanup`,async()=>{
  const {context,transport,request}=await setup();transport.arm('message',async()=>({captured:true}));
  const r=request(undefined,undefined,mode==='http-error'?500:200);
  if(mode==='continue-error')r.route.continue=async()=>{throw Error('private');};
  await r.run();
  if(mode==='requestfailed')context.emit('requestfailed',r.value);
  if(mode==='http-error'){context.emit('requestfinished',r.value);await new Promise(resolve=>setImmediate(resolve));}
  assert.deepEqual(await transport.gateAndDrain({timeoutMs:0}),{settled:false});
});
