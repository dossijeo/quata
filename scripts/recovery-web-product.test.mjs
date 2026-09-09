import test from "node:test";
import assert from "node:assert/strict";
import vm from "node:vm";
import {EventEmitter} from "node:events";
import {createRecoveryWebProduct} from "./e2e-fixtures/recovery-web-product.mjs";

function fixture(timeout=45000){
  const calls=[],storage=new Map(),attributes=new Map();
  let state={visible:false,question:"old",answerEmpty:true,saving:false,saved:false,failed:false};
  const context=vm.createContext({localStorage:{setItem:(key,value)=>storage.set(key,value),getItem:key=>storage.get(key)??null},location:{hash:""},
    document:{documentElement:{getAttribute:key=>attributes.get(key)??null,hasAttribute:key=>attributes.has(key)}}});
  context.__quataAuthE2eProduct={version:1,
    login:async()=>{assert.equal(storage.get("quata_web_client_instance_id"),"planned-session");calls.push("login");attributes.set("data-quata-ugc-terms-profile-id","profile");storage.set("quata_web_user_id","profile");storage.set("quata_web_access_token","synthetic-access");storage.set("quata_web_session_token","synthetic-web");return "authenticated";},
    logout:async()=>{attributes.delete("data-quata-ugc-terms-profile-id");return "logged_out";},
    openRecovery:()=>attributes.set("data-quata-auth-destination","recovery"),
    recoveryQuestion:async()=>"pet",resetPassword:async(...args)=>{calls.push("reset");assert.deepEqual(args,["240","synthetic-phone","synthetic-answer","temporary"]);return "password_reset";}};
  const bridge={version:1,open:()=>{state.visible=true;},configure:(question,answer)=>{state={...state,question,answerEmpty:answer.length===0};},
    save:()=>{calls.push("save");state={...state,answerEmpty:true,saved:true};},snapshot:()=>({...state})};
  context.__quataRecoverySecretE2eProduct=bridge;
  const page=new EventEmitter();page.url=()=>"http://localhost:8000/?quata-recovery-secret-e2e=1";
  page.evaluate=async(fn,arg)=>{context.argument=arg;return vm.runInContext(`(${fn.toString()})(argument)`,context);};
  page.waitForFunction=async(fn,arg)=>assert.equal(await page.evaluate(fn,arg),true);
  const product=createRecoveryWebProduct({page,record:{countryCode:"240",phone:"synthetic-phone",profileId:"profile",temporaryQuestion:"pet"},
    backendOrigin:"https://backend.example",timeout,verifyActor:async(record,ticket,credentials)=>{assert.equal(credentials.profileId,record.profileId);assert.equal(credentials.accessToken,"synthetic-access");assert.equal(credentials.webSessionToken,"synthetic-web");calls.push("verify_actor");return true;},closeResources:async()=>true});
  return {product,page,calls,context};
}

test("Web adapter uses focal product callbacks, planned login identity and one Save",async()=>{
  const f=fixture();assert.equal(await f.product.login("original",{clientInstanceId:"planned-session"}),true);
  await f.product.openAccount();await f.product.configureSecret("pet","synthetic-answer");await f.product.saveSecret();
  const state=await f.product.readPermittedState();assert.equal(state.answerEmpty,true);assert.equal(state.saved,true);
  await f.product.logout();await f.product.recoverPassword("synthetic-answer","temporary");
  assert.deepEqual(f.calls,["login","verify_actor","save","reset"]);assert.equal(f.product.operationsSettled(),true);
  await assert.rejects(f.product.saveSecret(),/operation_failed/);assert.equal(f.calls.filter(x=>x==="save").length,1);
  assert.equal(f.product.operationsSettled(),false);assert.equal(await f.product.close(),true);
});

test("pending and failed backend writes prevent settlement; completed reads do not conceal them",async()=>{
  const f=fixture();const request={url:()=>"https://backend.example/functions/v1/quata-auth-bridge",method:()=>"POST"};
  f.page.emit("request",request);assert.equal(f.product.operationsSettled(),false);
  f.page.emit("requestfinished",request);assert.equal(f.product.operationsSettled(),true);
  f.page.emit("request",request);f.page.emit("requestfailed",request);
  assert.equal(f.product.operationsSettled(),false);
  f.page.emit("requestfinished",request);assert.equal(f.product.operationsSettled(),false);
  await f.product.close();assert.equal(f.page.listenerCount("request"),0);
});

test("unexpected origin and missing ticket fail before login",async()=>{
  for(const foreign of [true,false]){
    const f=fixture();if(foreign)f.page.url=()=>"https://public.example/?quata-recovery-secret-e2e=1";
    await assert.rejects(f.product.login("original",{}),/operation_failed/);
    assert.deepEqual(f.calls,[]);assert.equal(f.product.operationsSettled(),false);await f.product.close();
  }
});

test("a bridge that never resolves returns control with sticky uncertainty",async()=>{
  const f=fixture(20);
  f.context.__quataAuthE2eProduct.login=()=>new Promise(()=>{});
  await assert.rejects(f.product.login("original",{clientInstanceId:"planned-session"}),/operation_failed/);
  assert.equal(f.product.operationsSettled(),false);
  assert.equal(f.calls.includes("verify_actor"),false);
  assert.equal(await f.product.close(),true);
});

test("missing or mismatched stored credentials cannot reach actor verification",async()=>{
  for(const key of ["quata_web_user_id","quata_web_access_token","quata_web_session_token"]){
    const f=fixture();const login=f.context.__quataAuthE2eProduct.login;
    f.context.__quataAuthE2eProduct.login=async()=>{const result=await login();f.context.localStorage.setItem(key,null);return result;};
    await assert.rejects(f.product.login("original",{clientInstanceId:"planned-session"}),/operation_failed/);
    assert.equal(f.calls.includes("verify_actor"),false);assert.equal(f.product.operationsSettled(),false);await f.product.close();
  }
});
