import assert from "node:assert/strict";
import test from "node:test";
import {createRecoveryIosProduct} from "./e2e-fixtures/recovery-ios-product.mjs";

function fixture({changeReply,verify=async()=>true,verifyLogout=async()=>true}={}) {
  const record={runId:"run",profileId:"profile",authUserId:"auth",countryCode:"240",phone:"799000000000",
    temporaryQuestion:"madre",temporaryAnswer:"synthetic-answer",temporaryPassword:"synthetic-password"};
  const events=[],inputs=[],released=[];
  let closeCalls=0;
  const results={identity:{storedIdentityMatched:true},open:{accountVisible:true,answerEmpty:true,accountIdentityMatched:true},
    configure:{saveDispatched:true,saveCount:1,accountIdentityMatched:true},
    read:{questionMatched:true,answerEmpty:true,accountIdentityMatched:true,accountVisible:true,saveEnabled:true,errorAbsent:true},
    logout:{sessionEmpty:true},empty:{sessionEmpty:true},"clear-owned":{sessionEmpty:true},recover:{loginReturned:true,submitCount:1}};
  const product=createRecoveryIosProduct({record,displayName:"Synthetic actor",questionLabel:"Synthetic question",
    runStep:async input=>{
      inputs.push(input);events.push(input.stage);
      const result=input.stage==="login"?{ticketId:input.ticketId,accessToken:"synthetic-token",sessionRestored:true}:structuredClone(results[input.stage]);
      const reply={terminal:true,exitCode:0,receipt:{runId:input.runId,stepId:input.stepId,stage:input.stage,
        profileId:input.profileId,authUserId:input.authUserId,result}};
      return changeReply?await changeReply(reply,input):reply;
    },releaseStep:async input=>{events.push("release:"+input.stage);released.push(input.stepId);return true;},
    verifyActor:async(...args)=>{events.push("verifyActor");return verify(...args);},
    verifyLogout,
    closeResources:async()=>{closeCalls++;return true;}});
  return {record,product,events,inputs,released,closeCalls:()=>closeCalls};
}
const login=f=>f.product.login("synthetic-original",{kind:"native",ticketId:"ticket"});
const prepare=async f=>{await login(f);await f.product.openAccount();await f.product.configureSecret(f.record.temporaryQuestion,f.record.temporaryAnswer);};

test("iOS defers product Save until the core call and binds login before private receipt removal",async()=>{
  let credentials;
  const f=fixture({verify:async(record,ticket,value)=>{credentials=value;return value.profileId===record.profileId && value.accessToken==="synthetic-token";}});
  await prepare(f);
  assert.deepEqual(f.events.slice(0,3),["login","verifyActor","release:login"]);
  assert.equal(credentials.accessToken,null);
  assert.equal(f.inputs.some(input=>input.stage==="configure"),false);
  await assert.rejects(f.product.readPermittedState(),/operation_unavailable/);
  await f.product.saveSecret();
  assert.equal(f.inputs.filter(input=>input.stage==="configure").length,1);
  await assert.rejects(f.product.saveSecret(),/operation_unavailable/);
  assert.deepEqual(await f.product.readPermittedState(),{visible:true,question:"madre",answerEmpty:true,saving:false,failed:false,saved:true});
  await f.product.logout();await f.product.recoverPassword(f.record.temporaryAnswer,f.record.temporaryPassword);
  assert.equal(await f.product.close(),true);assert.equal(await f.product.close(),true);
  assert.equal(f.closeCalls(),1);
  assert.deepEqual(f.inputs.slice(-2).map(input=>input.stage),["clear-owned","empty"]);
  assert.equal(new Set(f.inputs.map(input=>input.stepId)).size,f.inputs.length);
});

test("wrong identity, nonterminal result or private extra field retains receipt and forbids replay",async()=>{
  for(const fault of ["identity","terminal","extra"]){
    const f=fixture({changeReply:reply=>{
      if(fault==="identity")reply.receipt.authUserId="another-actor";
      if(fault==="terminal")reply.terminal=false;
      if(fault==="extra")reply.receipt.result.secretAnswer="must-not-be-accepted";
      return reply;
    }});
    await assert.rejects(login(f),/operation_unverified/);
    assert.equal(f.released.length,0);assert.equal(f.product.operationsSettled(),false);
    await assert.rejects(login(f),/operation_unavailable/);
    assert.equal(await f.product.close(),false);assert.equal(f.inputs.length,1);
  }
});

test("bearer verification failure never removes its private receipt",async()=>{
  const f=fixture({verify:async()=>{throw Error("synthetic private backend detail");}});
  await assert.rejects(login(f),error=>error.message==="recovery_ios_operation_unverified");
  assert.equal(f.released.length,0);assert.equal(await f.product.close(),false);
});

test("read receipt cannot turn a disabled Save or visible error into successful state",async()=>{
  for(const key of ["saveEnabled","errorAbsent"]){
    const f=fixture({changeReply:(reply,input)=>{if(input.stage==="read")reply.receipt.result[key]=false;return reply;}});
    await prepare(f);await f.product.saveSecret();
    await assert.rejects(f.product.readPermittedState(),/operation_unverified/);
    assert.equal(f.released.includes(f.inputs.at(-1).stepId),false);
    assert.equal(f.product.operationsSettled(),false);
  }
});

test("an observation failure keeps uncertainty and close cannot erase it",async()=>{
  const f=fixture({changeReply:async()=>{throw Error("synthetic timeout");}});
  await assert.rejects(login(f),/operation_unverified/);
  assert.equal(await f.product.close(),false);
  assert.equal(f.inputs.length,1);assert.equal(f.closeCalls(),1);
});

test("local logout alone cannot permit recovery or claim that operations settled",async()=>{
  const f=fixture({verifyLogout:async()=>false});
  await prepare(f);await f.product.saveSecret();await f.product.readPermittedState();
  await assert.rejects(f.product.logout(),/operation_unverified/);
  assert.equal(f.product.operationsSettled(),false);
  await assert.rejects(f.product.recoverPassword(f.record.temporaryAnswer,f.record.temporaryPassword),/operation_unavailable/);
  assert.equal(f.inputs.some(input=>input.stage==="recover"),false);
});
