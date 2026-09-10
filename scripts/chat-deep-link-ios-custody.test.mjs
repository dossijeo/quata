import test from "node:test";
import assert from "node:assert/strict";
import {randomUUID} from "node:crypto";
import {runIosDeepLinkSessionStep,iosDeepLinkCustodySettled} from "./e2e-fixtures/chat-deep-link-ios-custody.mjs";

function fixture() {
  const input={runId:randomUUID(),profileId:randomUUID(),authUserId:randomUUID(),authSessionId:randomUUID(),
    stepId:randomUUID(),stage:"install",accessToken:"private-access",refreshToken:"private-refresh",expiresAt:2000000000,
    email:"fixture@example.invalid",displayName:"Fixture",isOfficial:false};
  let saved={runId:input.runId,profileId:input.profileId,authUserId:input.authUserId,state:{sessions:[{
    ...Object.fromEntries(["runId","profileId","authUserId","authSessionId"].map(key=>[key,input[key]])),
    purpose:"deep_link",requestStarted:true,webSessionId:randomUUID(),privateLoginResponse:{status:200,
      body:{session:{access_token:input.accessToken,refresh_token:input.refreshToken,expires_at:input.expiresAt}}}}]}};
  const events=[];
  const journal={read:async()=>structuredClone(saved),checkpoint:async state=>{events.push("checkpoint");saved.state=structuredClone(state);}};
  const execute=async value=>{events.push("execute");assert.equal(saved.state.sessions[0].iosSession[value.stage].started,true);
    return {runId:value.runId,stepId:value.stepId,stage:value.stage,verified:true};};
  return {input,journal,execute,events,get:()=>saved,set:value=>saved=value};
}

test("journals install and exact clear before execution; only full lifecycle settles",async()=>{
  const f=fixture();assert.equal(iosDeepLinkCustodySettled(f.get().state.sessions[0]),true);
  await runIosDeepLinkSessionStep(f);assert.deepEqual(f.events,["checkpoint","execute","checkpoint"]);
  assert.equal(iosDeepLinkCustodySettled(f.get().state.sessions[0]),false);
  await runIosDeepLinkSessionStep({...f,input:{...f.input,stage:"clear",stepId:randomUUID()}});
  assert.equal(iosDeepLinkCustodySettled(f.get().state.sessions[0]),true);
  await assert.rejects(runIosDeepLinkSessionStep(f));
});

test("lost response or malformed receipt keeps intent and forbids retry",async()=>{
  for(const execute of [async()=>{throw Error("private-access");},async()=>({verified:true}),
    async input=>({runId:input.runId,stepId:input.stepId,stage:input.stage,verified:true,token:"private"})]){
    const f=fixture();await assert.rejects(runIosDeepLinkSessionStep({...f,execute}),{message:"deep_link_ios_custody_unresolved"});
    assert.equal(f.get().state.sessions[0].iosSession.install.started,true);
    assert.equal(iosDeepLinkCustodySettled(f.get().state.sessions[0]),false);
    await assert.rejects(runIosDeepLinkSessionStep(f));assert.equal(f.events.includes("execute"),false);
  }
});

test("failed checkpoint prevents transport; different clear cannot touch session",async()=>{
  const f=fixture();await assert.rejects(runIosDeepLinkSessionStep({...f,journal:{...f.journal,checkpoint:async()=>{throw Error();}}}));
  assert.deepEqual(f.events,[]);
  await runIosDeepLinkSessionStep(f);
  const count=f.events.length;
  await assert.rejects(runIosDeepLinkSessionStep({...f,input:{...f.input,stage:"clear",stepId:randomUUID(),refreshToken:"different"}}));
  assert.equal(f.events.length,count);
});

test("clear response loss remains unsettled and cannot be replayed",async()=>{
  const f=fixture();await runIosDeepLinkSessionStep(f);
  const input={...f.input,stage:"clear",stepId:randomUUID()};
  await assert.rejects(runIosDeepLinkSessionStep({...f,input,execute:async()=>{throw Error();}}));
  assert.equal(iosDeepLinkCustodySettled(f.get().state.sessions[0]),false);
  await assert.rejects(runIosDeepLinkSessionStep({...f,input}));
});

test("receipt followed by failed verification checkpoint keeps durable uncertainty",async()=>{
  const f=fixture();let writes=0;
  const journal={...f.journal,checkpoint:async state=>{if(++writes===2)throw Error("disk full");await f.journal.checkpoint(state);}};
  await assert.rejects(runIosDeepLinkSessionStep({...f,journal}));
  assert.equal(f.get().state.sessions[0].iosSession.install.verified,false);
  assert.equal(iosDeepLinkCustodySettled(f.get().state.sessions[0]),false);
  assert.equal(f.events.filter(event=>event==="execute").length,1);
  await assert.rejects(runIosDeepLinkSessionStep(f));
  assert.equal(f.events.filter(event=>event==="execute").length,1);
});
