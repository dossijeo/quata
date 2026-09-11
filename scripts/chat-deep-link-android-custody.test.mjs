import test from "node:test";
import assert from "node:assert/strict";
import {randomUUID} from "node:crypto";
import {runAndroidDeepLinkCustodyStep,androidDeepLinkCustodySettled} from "./e2e-fixtures/chat-deep-link-ios-custody.mjs";
import {runDeepLinkChatTrial} from "./flow-deep-links-chat-trial.mjs";

function fixture() {
  const input={runId:randomUUID(),profileId:randomUUID(),authUserId:randomUUID(),authSessionId:randomUUID(),
    stepId:randomUUID(),stage:"install",accessToken:"synthetic-access",refreshToken:"synthetic-refresh",
    expiresAt:2000000000,email:"fixture@example.invalid",displayName:"Fixture",isOfficial:false};
  let saved={runId:input.runId,profileId:input.profileId,authUserId:input.authUserId,state:{sessions:[{
    ...Object.fromEntries(["runId","profileId","authUserId","authSessionId"].map(key=>[key,input[key]])),
    purpose:"deep_link",requestStarted:true,webSessionId:randomUUID(),privateLoginResponse:{status:200,
    body:{session:{access_token:input.accessToken,refresh_token:input.refreshToken,expires_at:input.expiresAt}}}}]}};
  const journal={read:async()=>structuredClone(saved),checkpoint:async state=>{saved.state=structuredClone(state);}};
  const execute=async value=>{
    assert.equal(saved.state.sessions[0].androidSession[value.stage].started,true);
    assert.equal(saved.state.sessions[0].iosSession,undefined);
    return {runId:value.runId,stepId:value.stepId,stage:value.stage,verified:true};
  };
  return {input,journal,execute,entry:()=>saved.state.sessions[0]};
}

test("Android journal is durable before transport and settles only after exact clear",async()=>{
  const f=fixture();await runAndroidDeepLinkCustodyStep(f);
  assert.equal(androidDeepLinkCustodySettled(f.entry()),false);
  await assert.rejects(runAndroidDeepLinkCustodyStep({...f,input:{...f.input,stage:"clear",stepId:randomUUID(),refreshToken:"later"}}));
  await runAndroidDeepLinkCustodyStep({...f,input:{...f.input,stage:"clear",stepId:randomUUID()}});
  assert.equal(androidDeepLinkCustodySettled(f.entry()),true);
  assert.equal(f.entry().iosSession,undefined);
});

test("uncertain Android delivery cannot be replayed or mistaken for settled custody",async()=>{
  for(const stage of ["install","clear"]) {
    const f=fixture();if(stage==="clear")await runAndroidDeepLinkCustodyStep(f);
    const input={...f.input,stage,stepId:randomUUID()};
    await assert.rejects(runAndroidDeepLinkCustodyStep({...f,input,execute:async()=>{throw Error("lost reply");}}),
      {message:"deep_link_android_custody_unresolved"});
    assert.equal(androidDeepLinkCustodySettled(f.entry()),false);
    let executed=false;
    await assert.rejects(runAndroidDeepLinkCustodyStep({...f,input,execute:async()=>{executed=true;}}));
    assert.equal(executed,false);
  }
});

test("trial rejects simultaneous native channels before side effects",async()=>{
  await assert.rejects(runDeepLinkChatTrial({privateDirectory:process.cwd(),preflight:async()=>{throw Error("unexpected");},
    transportSettled:async()=>true,ui:{run:async()=>{},close:async()=>{},iosSessionChannel:{},androidSessionChannel:{}}}),
    {message:"deep_link_trial_multiple_native_channels"});
});
