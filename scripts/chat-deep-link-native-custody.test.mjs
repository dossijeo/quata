import test from 'node:test';
import assert from 'node:assert/strict';
import {androidDeepLinkCustodySettled} from './e2e-fixtures/chat-deep-link-ios-custody.mjs';
import {runNativeDeepLinkChatTrial} from './flow-deep-links-native-chat-trial.mjs';
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
test('native trial rejects an unknown lifecycle or incomplete dependencies before fixtures',async()=>{
 for(const mode of ['unknown','cold','warm'])await assert.rejects(runNativeDeepLinkChatTrial({mode,privateDirectory:process.cwd()}),
   {message:'deep_link_native_configuration_invalid'});
});
