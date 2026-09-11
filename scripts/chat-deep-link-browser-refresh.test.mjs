import test from "node:test";
import assert from "node:assert/strict";
import {createDeepLinkBrowserRefresh} from "./e2e-fixtures/chat-deep-link-browser-refresh.mjs";
const endpoint="https://example.test/auth/v1/token?grant_type=refresh_token";
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function gate(){let release;const promise=new Promise(resolve=>{release=resolve;});return {promise,release};}
function setup({beforeIntent=async()=>{},beforeJournal=async()=>{},beforeVerify=async()=>{},timeoutMs=25000}={}){
  const events=[];
  const request={url:()=>endpoint,method:()=>"POST",postDataJSON:()=>({refresh_token:"private"}),allHeaders:async()=>({apikey:"public"})};
  const response={status:()=>200,json:async()=>({access_token:"private-result"})};
  const route={request:()=>request,fetch:async options=>{events.push("fetch");assert.equal(options.maxRedirects,0);assert.equal(options.maxRetries,0);return response;},
    fulfill:async({response:actual})=>{assert.equal(actual,response);events.push("fulfill");},abort:async()=>{events.push("abort");}};
  const adapter=createDeepLinkBrowserRefresh({backendUrl:"https://example.test",publicKey:"public",timeoutMs,
    observeRefresh:async(transport,journaled)=>{
      await beforeIntent();events.push("intent");
      const actual=await transport(new URL(endpoint),{method:"POST",body:JSON.stringify({refresh_token:"private"})});
      assert.equal(actual.status,200);assert.deepEqual(await actual.json(),{access_token:"private-result"});
      await beforeJournal();events.push("checkpoint");await journaled();await beforeVerify();events.push("verify");return {verified:true};
    }});
  return {adapter,route,request,events};
}
test("intent is prepared before request and delivery precedes verification",async()=>{
  const s=setup();await s.adapter.prepare();assert.deepEqual(s.events,["intent"]);
  await s.adapter.handle(s.route);assert.deepEqual(s.events,["intent","fetch","checkpoint","fulfill","verify"]);
  assert.equal(s.adapter.passed(),true);
  const t=s.adapter.diagnostics().timings;
  assert.ok(t.prepared<=t.request&&t.request<=t.response&&t.response<=t.responseJournaled&&t.responseJournaled<=t.delivered&&t.delivered<=t.verified);
  await s.adapter.handle(s.route);assert.equal(s.adapter.passed(),false);assert.equal(s.events.filter(e=>e==="fetch").length,1);
});
test("delivery is usable while verification still blocks PASS and cleanup",async()=>{
  const verification=gate();const s=setup({beforeVerify:()=>verification.promise});await s.adapter.prepare();
  const handling=s.adapter.handle(s.route);await delay(10);
  assert.equal(s.adapter.diagnostics().delivered,true);assert.equal(s.adapter.passed(),false);assert.equal(s.adapter.operationsSettled(),false);
  verification.release();await handling;assert.equal(await s.adapter.finish(),true);assert.equal(s.adapter.operationsSettled(),true);
});
test("foreign request fields are rejected without forwarding",async()=>{
  for(const field of ["url","method","payload","key","token"]){
    const s=setup();await s.adapter.prepare();
    if(field==="url")s.request.url=()=>endpoint+"&other=true";
    if(field==="method")s.request.method=()=>"GET";
    if(field==="payload")s.request.postDataJSON=()=>({refresh_token:"private",extra:true});
    if(field==="key")s.request.allHeaders=async()=>({apikey:"foreign"});
    if(field==="token")s.request.postDataJSON=()=>({refresh_token:"foreign"});
    await s.adapter.handle(s.route);await s.adapter.finish();assert.equal(s.events.includes("fetch"),false);assert.equal(s.adapter.passed(),false);
  }
});
test("prepare timeout keeps live checkpoint work unsettled and forbids late send",async()=>{
  const intent=gate();const s=setup({beforeIntent:()=>intent.promise,timeoutMs:20});
  await assert.rejects(s.adapter.prepare(),/prepare_failed/);assert.equal(s.adapter.operationsSettled(),false);
  intent.release();await delay(10);await s.adapter.handle(s.route);
  assert.equal(s.events.includes("fetch"),false);assert.equal(s.adapter.operationsSettled(),true);
});
test("late response checkpoint cannot deliver after timeout and remains uncertain",async()=>{
  const checkpoint=gate();const s=setup({beforeJournal:()=>checkpoint.promise,timeoutMs:20});
  await s.adapter.prepare();await s.adapter.handle(s.route);assert.equal(s.adapter.operationsSettled(),false);
  checkpoint.release();await delay(10);assert.equal(s.events.includes("fulfill"),false);assert.equal(s.adapter.operationsSettled(),false);
});
test("closed observer refuses requests and missing request cannot pass",async()=>{
  const s=setup();await s.adapter.prepare();assert.equal(await s.adapter.finish(),false);s.adapter.close();await s.adapter.handle(s.route);
  assert.equal(s.events.includes("fetch"),false);assert.equal(s.adapter.passed(),false);
});
