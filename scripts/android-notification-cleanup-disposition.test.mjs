import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {prepareAndroidReplyDestinationInvariant,assertAndroidReplyCleanupDisposition} from './e2e-fixtures/android-notification-cleanup-disposition.mjs';
function fixture() {
  const runId=randomUUID(),profileId=randomUUID(),authUserId=randomUUID(),peerId=randomUUID(),peerAuthId=randomUUID();
  const dispatcherFingerprint='a'.repeat(64),stepId=randomUUID(),authSessionId=randomUUID();
  const saved={runId,profileId,authUserId,state:{profileCreated:true,androidRuntimeFreeze:{dispatcherFingerprint},
    webNotificationSeedPush:{settledWithoutDestinations:true},
    threadPlan:{runId,ownerId:profileId,peerId,uniqueKey:`quata-deep-link-${runId}`},threadReceipt:{threadId:'123',messageId:'456'}}};
  const peer={runId,profileId:peerId,authUserId:peerAuthId,state:{profileCreated:true,sessions:[]}};
  const journal={read:async()=>structuredClone(saved),checkpoint:async state=>{saved.state=structuredClone(state);}};
  const peerJournal={read:async()=>structuredClone(peer)};
  const audit={auth_sessions:true,web_sessions:true,native_tokens:true,web_subscriptions:true,native_logs:true,reply_web_logs:true};
  const events=[];let threadPresent=true,replyPresent=true,residue=false;
  const client={query:async sql=>{
    events.push(sql);
    if(sql.startsWith('select p.id'))return {rowCount:2,rows:[{id:profileId,auth_user_id:authUserId},{id:peerId,auth_user_id:peerAuthId}]};
    if(sql.startsWith('select profile_id'))return {rowCount:threadPresent?2:0,rows:threadPresent?[{profile_id:profileId,left_at:null},{profile_id:peerId,left_at:null}]:[]};
    if(sql.startsWith('select id::text from public.chat_threads'))return {rowCount:threadPresent?1:0,rows:threadPresent?[{id:'123'}]:[]};
    if(sql.startsWith('select id::text from public.chat_messages'))return {rowCount:replyPresent?1:0,rows:replyPresent?[{id:'457'}]:[]};
    if(sql.includes(' as messages,'))return {rowCount:1,rows:[{messages:!residue,events:true,conversation_state:true,web_logs:true}]};
    if(sql.startsWith('select\n'))return {rowCount:1,rows:[audit]};
    return {rowCount:0,rows:[]};
  }};
  const disposition={kind:'android-reply-trigger-no-mutable-destinations',runId,ownerId:profileId,peerId,threadId:'123',
    replyMessageId:'457',dispatcherFingerprint,nativeProcessClosed:true,sessionCustodySettled:true,seedDispatcherSettled:true,
    replyTrigger:{requestTerminal:null,mutationImpossible:true}};
  const sent=()=>{
    const input={runId,profileId,authUserId,authSessionId,accessToken:'private-test-access',refreshToken:'private-test-refresh',
      expiresAt:2000000000,email:'fixture@example.invalid',displayName:'Fixture',isOfficial:false};
    saved.state.sessions=[{runId,profileId,authUserId,authSessionId,androidSession:{
      install:{started:true,verified:true,input:{...input,stepId:randomUUID(),stage:'install'}},
      clear:{started:true,verified:true,input:{...input,stepId:randomUUID(),stage:'clear'}}}}];
    saved.state.androidReplyClosure={runId,processClosed:true,notificationRemoved:true,transportSettled:true};
    saved.state.notificationReply={started:true,uiVerified:true,input:{runId,stepId,profileId,threadId:'123'},
      backendReceipt:{messageId:'457',threadId:'123',senderProfileId:profileId,replyMarker:`qadata-reply-text-${stepId}`,
        clientMessageId:`notification-reply-${randomUUID()}`,count:1}};
  };
  return {client,journal,peerJournal,dispatcherFingerprint,disposition,audit,peer,events,sent,get:()=>saved,
    absent:()=>{threadPresent=false;},missingReply:()=>{replyPresent=false;},residue:()=>{residue=true;}};
}

test('Android cleanup preserves unknown trigger termination and audits destinations under caller locks',async()=>{
  const f=fixture();await prepareAndroidReplyDestinationInvariant(f);f.sent();
  const proof=await assertAndroidReplyCleanupDisposition(f);
  assert.equal(proof.replyTrigger.requestTerminal,null);assert.equal(proof.replyTrigger.mutationImpossible,true);
  f.audit.native_tokens=false;await assert.rejects(assertAndroidReplyCleanupDisposition(f));
});

test('seed uncertainty or an existing attempt cannot manufacture a before-send invariant',async()=>{
  for(const mode of ['seed','attempt','web']) {
    const f=fixture();
    if(mode==='seed')f.get().state.webNotificationSeedPush.settledWithoutDestinations=false;
    if(mode==='attempt')f.sent();
    if(mode==='web')f.get().state.webNotificationMessage={};
    await assert.rejects(prepareAndroidReplyDestinationInvariant(f));assert.equal(f.events.length,0);
  }
});

test('peer destinations and delivery logs forbid cleanup',async()=>{
  for(const key of ['auth_sessions','web_sessions','native_tokens','web_subscriptions','native_logs','reply_web_logs']) {
    const f=fixture();await prepareAndroidReplyDestinationInvariant(f);f.sent();f.audit[key]=false;
    await assert.rejects(assertAndroidReplyCleanupDisposition(f));
  }
});

test('wrong identity, changed dispatcher, incomplete native closure or uncertain session cannot delete',async()=>{
  for(const mode of ['fingerprint','peer-session','process','notification','transport','session','session-mixed','receipt','terminal-claim','message-missing']) {
    const f=fixture();await prepareAndroidReplyDestinationInvariant(f);f.sent();
    if(mode==='fingerprint')f.dispatcherFingerprint='b'.repeat(64);
    if(mode==='peer-session')f.peer.state.sessions.push({requestStarted:true});
    if(mode==='process')f.get().state.androidReplyClosure.processClosed=false;
    if(mode==='notification')f.get().state.androidReplyClosure.notificationRemoved=false;
    if(mode==='transport')f.get().state.androidReplyClosure.transportSettled=false;
    if(mode==='session')f.get().state.sessions[0].androidSession.clear.verified=false;
    if(mode==='session-mixed')f.get().state.sessions[0].iosSession={};
    if(mode==='receipt')f.get().state.notificationReply.backendReceipt.senderProfileId=randomUUID();
    if(mode==='terminal-claim')f.disposition.replyTrigger.requestTerminal=true;
    if(mode==='message-missing')f.missingReply();
    await assert.rejects(assertAndroidReplyCleanupDisposition(f));
  }
});

test('an absent thread requires the prior matching cleanup audit and complete absence',async()=>{
  const f=fixture();await prepareAndroidReplyDestinationInvariant(f);f.sent();f.absent();
  await assert.rejects(assertAndroidReplyCleanupDisposition(f));
  f.get().state.androidThreadCleanupDisposition=f.disposition;f.get().state.replyCleanupAudit={replyCount:1};
  await assertAndroidReplyCleanupDisposition(f);
  f.residue();await assert.rejects(assertAndroidReplyCleanupDisposition(f));
});
