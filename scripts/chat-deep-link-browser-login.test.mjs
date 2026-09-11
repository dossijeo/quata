import test from "node:test";
import assert from "node:assert/strict";
import {EventEmitter} from "node:events";
import {createDeepLinkBrowserLogin} from "./e2e-fixtures/chat-deep-link-browser-login.mjs";

function setup({duplicate=false,mismatch=false,stalled=false,productFails=false}={}) {
  const payload={action:"web_login",profile_id:"owned-profile",country_code:"240",phone_local:"123456",password:"private",client_instance_id:"owned-client"};
  const url="https://example.test/functions/v1/quata-auth-bridge";
  const body={session:{access_token:"private-access"}};
  const page=new EventEmitter();let complete,calls=0;
  page.waitForResponse=predicate=>new Promise(resolve=>{complete=request=>{
    const response={request:()=>request,status:()=>200,json:async()=>body};
    if(predicate(response))resolve(response);
  };});
  page.evaluate=async(_callback,args)=>{
    calls++;assert.deepEqual(args,{countryCode:"240",phone:"123456",password:"private"});
    if(stalled)return new Promise(()=>{});
    // WebAuthRepository identifies by phone; profile_id is not sent by product.
    const {profile_id:unused,...productPayload}=payload;
    const request={method:()=>"POST",url:()=>url,postDataJSON:()=>({...productPayload,...(mismatch?{profile_id:"foreign"}:{})})};
    page.emit("request",request);if(duplicate)page.emit("request",request);complete(request);
    if(productFails)throw Error("private exception");
    return "authenticated";
  };
  const adapter=createDeepLinkBrowserLogin({page,backendUrl:"https://example.test",clientInstanceId:"owned-client",timeoutMs:100});
  return {adapter,page,body,url,options:{method:"POST",body:JSON.stringify(payload)},calls:()=>calls};
}

test("product login returns its real response once and removes observers",async()=>{
  const s=setup();const response=await s.adapter.requestLogin(s.url,s.options);
  assert.equal(response.status,200);assert.equal(await response.json(),s.body);
  assert.deepEqual(s.adapter.diagnostics(),{attempted:true,settled:true,productAuthenticated:true});
  await assert.rejects(s.adapter.requestLogin(s.url,s.options),/already_attempted/);
  assert.equal(s.calls(),1);assert.equal(s.page.listenerCount("request"),0);
  assert.ok(!JSON.stringify(s.adapter.diagnostics()).includes("private"));
});
test("foreign or duplicate login keeps uncertainty and forbids retries",async()=>{
  for(const mode of [{duplicate:true},{mismatch:true}]) {
    const s=setup(mode);await assert.rejects(s.adapter.requestLogin(s.url,s.options),/response_uncertain/);
    assert.equal(s.adapter.operationsSettled(),false);
    await assert.rejects(s.adapter.requestLogin(s.url,s.options),/already_attempted/);
    assert.equal(s.calls(),1);
  }
});
test("a stalled product call is bounded and remains unresolved",async()=>{
  const s=setup({stalled:true});
  await assert.rejects(s.adapter.requestLogin(s.url,s.options),/response_uncertain/);
  assert.equal(s.adapter.operationsSettled(),false);assert.equal(s.page.listenerCount("request"),0);
});
test("HTTP response survives a subsequent product failure for journal reconciliation",async()=>{
  const s=setup({productFails:true});const response=await s.adapter.requestLogin(s.url,s.options);
  assert.equal(await response.json(),s.body);assert.equal(s.adapter.operationsSettled(),true);
  assert.equal(s.adapter.diagnostics().productAuthenticated,false);
});
test("invalid destination or pre-aborted signal never invokes login",async()=>{
  const s=setup();await assert.rejects(s.adapter.requestLogin("https://foreign.test/functions/v1/quata-auth-bridge",s.options),/request_invalid/);
  await assert.rejects(s.adapter.requestLogin(s.url,{...s.options,signal:AbortSignal.abort()}),/aborted/);
  assert.equal(s.calls(),0);
});
