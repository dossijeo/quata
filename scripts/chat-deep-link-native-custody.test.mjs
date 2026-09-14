import test from 'node:test';
import assert from 'node:assert/strict';
import {androidDeepLinkCustodySettled,iosDeepLinkCustodySettled} from './e2e-fixtures/chat-deep-link-ios-custody.mjs';
import {runNativeDeepLinkChatTrial,acknowledgeIosNativeOwnedRead} from './flow-deep-links-native-chat-trial.mjs';
const id=n=>`00000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
function entry() {
 const value={runId:id(1),profileId:id(2),authUserId:id(3),authSessionId:id(4),kind:'native',ticketId:id(5),requestStarted:true};
 const session={profileId:id(2),authUserId:id(3),authSessionId:id(4),accessToken:'synthetic-access',refreshToken:'synthetic-refresh',
   expiresAt:2000000000,email:'fixture@example.invalid',displayName:'Fixture',isOfficial:false};
 value.androidNativeLogin={observationVerified:true,
   read:{started:true,verified:true,input:{runId:id(1),profileId:id(2),authUserId:id(3),stage:'read-owned',stepId:id(6)},privateSession:session},
   clear:{started:true,verified:true,input:{...session,runId:id(1),stage:'clear',stepId:id(7)}}};return value;
}
test('native login cannot be retired with an unresolved observation, read or clear',()=>{
 assert.equal(androidDeepLinkCustodySettled(entry()),true);
 for(const mutate of [v=>v.requestStarted=false,v=>v.kind='web',v=>v.androidNativeLogin.observationVerified=false,
   v=>v.androidNativeLogin.read.started=false,v=>v.androidNativeLogin.read.verified=false,
   v=>v.androidNativeLogin.clear.started=false,v=>v.androidNativeLogin.clear.verified=false,
   v=>delete v.androidNativeLogin.read.privateSession,
   v=>v.androidNativeLogin.clear.input.stepId=v.androidNativeLogin.read.input.stepId,
   v=>v.androidNativeLogin.read.input.profileId=id(9),v=>v.authSessionId=id(9),
   v=>v.androidNativeLogin.clear.input.refreshToken='later-refresh',v=>v.androidNativeLogin.clear.input.expiresAt++,
   v=>v.androidNativeLogin.clear.input.accessToken='later-login']) {
   const value=entry();mutate(value);assert.equal(androidDeepLinkCustodySettled(value),false);
 }
});

test('iOS acknowledgment is durable before dispatch and cannot replay uncertain delivery',async()=>{
 for(const failure of ['none','checkpoint','lost-response','wrong-receipt','final-checkpoint']) {
  const ticket=entry();ticket.iosNativeLogin=ticket.androidNativeLogin;delete ticket.androidNativeLogin;
  let saved={state:{sessions:[structuredClone(ticket)]}},writes=0,sends=0;
  const journal={read:async()=>structuredClone(saved),checkpoint:async state=>{
   writes++;
   if((failure==='checkpoint'&&writes===1)||(failure==='final-checkpoint'&&writes===2))throw Error('synthetic-disk');
   saved={state:structuredClone(state)};
  }};
  const input=ticket.iosNativeLogin.read.input;
  const channel={acknowledgeOwnedRead:async request=>{
   sends++;assert.equal(saved.state.sessions[0].iosNativeLogin.read.acknowledgment.started,true);
   assert.ok(saved.state.sessions[0].iosNativeLogin.read.privateSession);
   if(failure==='lost-response')throw Error('synthetic-transport');
   return {...request,acknowledged:true,...(failure==='wrong-receipt'?{stepId:id(9)}:{})};
  }};
  const run=()=>acknowledgeIosNativeOwnedRead({journal,ticket,input,channel});
  if(failure==='none')await run();else await assert.rejects(run());
  assert.equal(sends,failure==='checkpoint'?0:1);
  if(failure!=='checkpoint') {
   assert.equal(saved.state.sessions[0].iosNativeLogin.read.acknowledgment.verified,failure==='none');
   await assert.rejects(run());assert.equal(sends,1);
  }
 }
});
test('native trial rejects an unknown lifecycle or incomplete dependencies before fixtures',async()=>{
 for(const mode of ['unknown','cold','warm'])await assert.rejects(runNativeDeepLinkChatTrial({mode,privateDirectory:process.cwd()}),
   {message:'deep_link_native_configuration_invalid'});
});

test('iOS native retirement requires an acknowledged exact owned read and exact clear',()=>{
 const iosEntry=()=>{
  const value=entry();value.iosNativeLogin=value.androidNativeLogin;delete value.androidNativeLogin;
  value.iosNativeLogin.read.acknowledgment={started:true,verified:true,runId:value.runId,stepId:id(6)};
  return value;
 };
 assert.equal(iosDeepLinkCustodySettled(iosEntry()),true);
 for(const mutate of [v=>delete v.iosNativeLogin.read.acknowledgment,
   v=>v.iosNativeLogin.read.acknowledgment.started=false,
   v=>v.iosNativeLogin.read.acknowledgment.verified=false,
   v=>v.iosNativeLogin.read.acknowledgment.runId=id(9),
   v=>v.iosNativeLogin.read.acknowledgment.stepId=id(9),
   v=>v.iosNativeLogin.observationVerified=false,
   v=>v.iosNativeLogin.read.verified=false,
   v=>delete v.iosNativeLogin.read.privateSession,
   v=>v.iosNativeLogin.clear.verified=false,
   v=>v.iosNativeLogin.clear.input.accessToken='another-session',
   v=>v.iosNativeLogin.clear.input.stepId=id(6),
   v=>v.authSessionId=id(9)]) {
  const value=iosEntry();mutate(value);assert.equal(iosDeepLinkCustodySettled(value),false);
 }
});
