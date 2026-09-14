import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {prepareWebReplyDestinationInvariant,assertWebReplyCleanupDisposition} from './e2e-fixtures/web-notification-cleanup-disposition.mjs';
function fixture() {
  const runId=randomUUID(),profileId=randomUUID(),authUserId=randomUUID(),peerId=randomUUID(),peerAuthId=randomUUID();
  const dispatcherFingerprint='a'.repeat(64);
  let saved={runId,profileId,authUserId,state:{profileCreated:true,webRuntimeFreeze:{dispatcherFingerprint},
    threadPlan:{runId,ownerId:profileId,peerId,uniqueKey:`quata-deep-link-${runId}`},threadReceipt:{threadId:'123',messageId:'456'}}};
  const peer={runId,profileId:peerId,authUserId:peerAuthId,state:{profileCreated:true,sessions:[]}};
  const journal={read:async()=>structuredClone(saved),checkpoint:async state=>{saved.state=structuredClone(state);}};
  const peerJournal={read:async()=>structuredClone(peer)};
  const audit={auth_sessions:true,web_sessions:true,native_tokens:true,web_subscriptions:true,native_logs:true,reply_web_logs:true};
  const events=[];
  const client={query:async sql=>{
    events.push(sql);
    if(sql.startsWith('select p.id'))return {rowCount:2,rows:[{id:profileId,auth_user_id:authUserId},{id:peerId,auth_user_id:peerAuthId}]};
    if(sql.startsWith('select profile_id'))return {rowCount:2,rows:[{profile_id:profileId,left_at:null},{profile_id:peerId,left_at:null}]};
    if(sql.startsWith('select id::text'))return {rowCount:1,rows:[{id:'123'}]};
    if(sql.startsWith('select\n'))return {rowCount:1,rows:[audit]};
    return {rowCount:0,rows:[]};
  }};
  const disposition={kind:'reply-trigger-no-mutable-destinations',runId,ownerId:profileId,peerId,threadId:'123',replyMessageId:'457',dispatcherFingerprint,
    browserTransportSettled:true,seedDispatcherSettled:true,explicitDispatcherSettled:true,replyTrigger:{requestTerminal:null,mutationImpossible:true}};
  const sent=()=>{
    saved.state.webBrowserClosure={runId,transportSettled:true};
    saved.state.webNotificationSeedPush={settledWithoutDestinations:true};saved.state.webNotificationDispatch={settled:true};
    saved.state.webNotificationMessage={capturedBeforeForward:true,input:{p_actor_profile_id:profileId,p_thread_id:123,
      p_message:`quata-web-reply-${runId}`,p_client_message_id:'1789412345678--7abc',p_reply_to_message_id:null,p_file_ids:[]},backendReceipt:{messageId:'457'}};
  };
  return {client,journal,peerJournal,dispatcherFingerprint,disposition,audit,peer,events,sent,get:()=>saved};
}
test('explicit disposition preserves unknown request termination and reaudits destinations',async()=>{
  const f=fixture();await prepareWebReplyDestinationInvariant(f);f.sent();
  const result=await assertWebReplyCleanupDisposition(f);
  assert.equal(result.replyTrigger.requestTerminal,null);
  assert.equal(result.replyTrigger.mutationImpossible,true);
  f.audit.native_tokens=false;
  await assert.rejects(assertWebReplyCleanupDisposition(f));
});
test('a snapshot taken after Send cannot manufacture a prior destination invariant',async()=>{
  const f=fixture();f.sent();await assert.rejects(prepareWebReplyDestinationInvariant(f));
  assert.equal(f.events.length,0);
  assert.equal(f.get().state.webReplyDestinationInvariant,undefined);
});
test('peer sessions, tokens and native or Reply logs forbid cleanup',async()=>{
  for(const key of ['auth_sessions','web_sessions','native_tokens','web_subscriptions','native_logs','reply_web_logs']) {
    const f=fixture();await prepareWebReplyDestinationInvariant(f);f.sent();f.audit[key]=false;
    await assert.rejects(assertWebReplyCleanupDisposition(f),{message:'web_notification_cleanup_disposition_unverified'});
  }
});
test('a different receipt, dispatcher or peer producer is rejected',async()=>{
  for(const mode of ['receipt','fingerprint','peer-session','browser','explicit-dispatch','terminal-claim']) {
    const f=fixture();await prepareWebReplyDestinationInvariant(f);f.sent();
    if(mode==='receipt')f.disposition.replyMessageId='999';
    if(mode==='fingerprint')f.dispatcherFingerprint='b'.repeat(64);
    if(mode==='peer-session')f.peer.state.sessions.push({requestStarted:true});
    if(mode==='browser')f.get().state.webBrowserClosure.transportSettled=false;
    if(mode==='explicit-dispatch')f.get().state.webNotificationDispatch.settled=false;
    if(mode==='terminal-claim')f.disposition.replyTrigger.requestTerminal=true;
    await assert.rejects(assertWebReplyCleanupDisposition(f));
  }
});
