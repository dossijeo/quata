import test from "node:test";
import assert from "node:assert/strict";
import {createDeepLinkBrowserRefresh} from "./e2e-fixtures/chat-deep-link-browser-refresh.mjs";

function setup(options={}) {
  const events=[];
  const endpoint="https://example.test/auth/v1/token?grant_type=refresh_token";
  const request={url:()=>endpoint,method:()=>"POST",postDataJSON:()=>({refresh_token:"private"}),allHeaders:async()=>({apikey:"public"})};
  const response={status:()=>200,json:async()=>({access_token:"private-result"})};
  const route={request:()=>request,fetch:async options=>{events.push("fetch");assert.equal(options.maxRedirects,0);assert.equal(options.maxRetries,0);return response;},
    fulfill:async({response:actual})=>{assert.equal(actual,response);events.push("fulfill");},abort:async()=>{events.push("abort");}};
  const observer=async transport=>{
    events.push("intent");const actual=await transport(new URL(endpoint),{method:"POST",body:JSON.stringify({refresh_token:"private"})});
    assert.equal(actual.status,200);assert.deepEqual(await actual.json(),{access_token:"private-result"});
    events.push("verify");return {verified:true};
  };
  const adapter=createDeepLinkBrowserRefresh({backendUrl:"https://example.test",publicKey:"public",observeRefresh:observer,...options});
  return {adapter,route,request,events,observer};
}

test("paused request is journaled and verified before delivering original response once",async()=>{
  const s=setup();await s.adapter.handle(s.route);
  assert.deepEqual(s.events,["intent","fetch","verify","fulfill"]);assert.equal(s.adapter.passed(),true);
  await s.adapter.handle(s.route);assert.equal(s.adapter.passed(),false);
  assert.equal(s.events.filter(e=>e==="fetch").length,1);assert.equal(s.events.at(-1),"abort");
});

test("foreign request fields never reach transport",async()=>{
  for(const field of ["url","method","payload","key"]) {
    const s=setup();
    if(field==="url")s.request.url=()=>"https://example.test/auth/v1/token?other=true";
    if(field==="method")s.request.method=()=>"GET";
    if(field==="payload")s.request.postDataJSON=()=>({refresh_token:"private",extra:"unexpected"});
    if(field==="key")s.request.allHeaders=async()=>({apikey:"foreign"});
    await s.adapter.handle(s.route);assert.deepEqual(s.events,["abort"]);assert.equal(s.adapter.passed(),false);
  }
});

test("mismatched token from observer is blocked before sending",async()=>{
  const s=setup({observeRefresh:async transport=>transport(new URL("https://example.test/auth/v1/token?grant_type=refresh_token"),{
    method:"POST",body:JSON.stringify({refresh_token:"foreign"}),
  })});
  await s.adapter.handle(s.route);assert.deepEqual(s.events,["abort"]);assert.equal(s.adapter.operationsSettled(),true);
});

test("timeout before checkpoint completes cannot forward later",async()=>{
  let release;const gate=new Promise(resolve=>{release=resolve;});
  const s=setup({timeoutMs:20,observeRefresh:async transport=>{await gate;return transport(new URL("https://example.test/auth/v1/token?grant_type=refresh_token"),{
    method:"POST",body:JSON.stringify({refresh_token:"private"}),
  });}});
  await s.adapter.handle(s.route);assert.equal(s.adapter.operationsSettled(),false);
  release();await new Promise(resolve=>setTimeout(resolve,20));
  assert.deepEqual(s.events,["abort"]);assert.equal(s.adapter.operationsSettled(),true);
});

test("timeout after forwarding stays uncertain and never fulfills late",async()=>{
  let release;const gate=new Promise(resolve=>{release=resolve;});
  const s=setup({timeoutMs:20});const fetch=s.route.fetch;
  s.route.fetch=async options=>{const result=await fetch(options);await gate;return result;};
  await s.adapter.handle(s.route);assert.equal(s.adapter.operationsSettled(),false);
  release();await new Promise(resolve=>setTimeout(resolve,20));
  assert.equal(s.events.includes("fulfill"),false);assert.equal(s.adapter.operationsSettled(),false);
});

test("closed observer refuses further requests",async()=>{
  const s=setup();s.adapter.close();await s.adapter.handle(s.route);
  assert.deepEqual(s.events,["abort"]);assert.equal(s.adapter.passed(),false);
});
