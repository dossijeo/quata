import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {prepareWebReplyDestinationInvariant} from './e2e-fixtures/web-notification-cleanup-disposition.mjs';
import {removeWebNotificationSubscription} from './e2e-fixtures/web-notification-subscription.mjs';
import {removeWebNotificationReplyThread} from './e2e-fixtures/notification-reply-attempt.mjs';

async function fixture() {
  const runId=randomUUID(),profileId=randomUUID(),authUserId=randomUUID(),peerId=randomUUID(),peerAuthId=randomUUID();
  const webSessionId=randomUUID(),subscriptionId=randomUUID(),dispatcherFingerprint='a'.repeat(64);
  const plan={runId,ownerId:profileId,peerId,uniqueKey:`quata-deep-link-${runId}`,messageKey:`quata-deep-link-message-${runId}`,body:`Deep link ${runId}`};
  let record={runId,profileId,authUserId,state:{profileCreated:true,threadStarted:true,threadPlan:plan,threadReceipt:{threadId:'123',messageId:'456'},
    webRuntimeFreeze:{dispatcherFingerprint},sessions:[{runId,profileId,authUserId,requestStarted:true,authSessionId:randomUUID(),webSessionId}]}};
  const peer={runId,profileId:peerId,authUserId:peerAuthId,state:{profileCreated:true,sessions:[]}};
  const journal={read:async()=>structuredClone(record),checkpoint:async state=>{record.state=structuredClone(state);}};
  const peerJournal={read:async()=>structuredClone(peer)};
  const input={endpoint:'https://push.example.invalid/owned',p256dh:'p'.repeat(40),authSecret:'a'.repeat(16),expirationTime:null};
  const seed={id:'456',sender_profile_id:peerId,body:plan.body,client_message_id:plan.messageKey,reply_to_message_id:null,forwarded_from_message_id:null};
  const reply={id:'457',sender_profile_id:profileId,body:`quata-web-reply-${runId}`,client_message_id:'1789412345678--7abc',reply_to_message_id:null,forwarded_from_message_id:null};
  let db={thread:true,messages:[seed],subscriptions:[{id:subscriptionId,web_session_id:webSessionId,profile_id:profileId,auth_user_id:authUserId,
    endpoint:input.endpoint,p256dh:input.p256dh,auth_secret:input.authSecret,expiration_time:null,disabled_at:'2026-09-14T22:00:00Z'}],
    logs:[{id:'789',message_id:'456',profile_id:profileId,subscription_id:subscriptionId,status:'sent'}]};
  let transaction,failCommit,peerHasToken=false;
  const events=[],rows=values=>({rowCount:values.length,rows:structuredClone(values)});
  const client={query:async(sql,args)=>{
    events.push(sql);
    if(sql==='begin'){transaction=structuredClone(db);return rows([]);}
    if(sql==='rollback'){if(transaction)db=transaction;transaction=undefined;return rows([]);}
    if(sql==='commit'){
      if(failCommit==='rollback'){failCommit=undefined;throw Error('commit_not_applied');}
      transaction=undefined;
      if(failCommit==='lost'){failCommit=undefined;throw Error('commit_reply_lost');}
      return rows([]);
    }
    if(sql.startsWith('set local'))return rows([]);
    if(sql.startsWith('select p.id'))return rows([{id:profileId,auth_user_id:authUserId},{id:peerId,auth_user_id:peerAuthId}]);
    if(sql.startsWith('select w.id'))return rows([{id:webSessionId}]);
    if(sql.startsWith('select id::text from public.chat_threads'))return rows(db.thread?[{id:'123'}]:[]);
    if(sql.startsWith('select id::text,type'))return rows(db.thread?[{id:'123',type:'group',created_by_profile_id:profileId,unique_key:plan.uniqueKey}]:[]);
    if(sql.startsWith('select profile_id'))return rows(db.thread?[{profile_id:profileId,left_at:null},{profile_id:peerId,left_at:null}]:[]);
    if(sql.includes('as auth_sessions'))return rows([{auth_sessions:true,web_sessions:true,native_tokens:!peerHasToken,web_subscriptions:true,native_logs:true,reply_web_logs:true}]);
    if(sql.startsWith('select id::text from public.chat_messages'))return rows(db.messages.filter(row=>row.id===args[0]));
    if(sql.startsWith('select id::text,sender_profile_id'))return rows(db.messages);
    if(sql.startsWith('select * from public.web_push_subscriptions'))return rows(db.subscriptions);
    if(sql.startsWith('select id::text,message_id::text'))return rows(db.logs);
    if(sql.startsWith('select n.nspname'))return rows([{schema:'public',table:'web_push_delivery_log',column:'subscription_id',parent:'web_push_subscriptions',key_count:1,parent_column:'id'}]);
    if(sql.startsWith('delete from public.web_push_delivery_log')){db.logs=[];return rows([]);}
    if(sql.startsWith('delete from public.web_push_subscriptions')){db.subscriptions=[];return rows([]);}
    if(sql.startsWith('delete from public.chat_threads')){db.thread=false;db.messages=[];return rows([]);}
    if(sql.includes('from public.chat_attachments'))return rows([{count:'0'}]);
    if(sql.startsWith('with owned_messages'))return rows([{external_messages:'0',external_conversation_state:'0',sos_events:'0',sos_recipients:'0'}]);
    if(sql.includes('as subscription')||sql.includes('as subscriptions'))return rows([{subscription:db.subscriptions.length===0,subscriptions:db.subscriptions.length===0,deliveries:db.logs.length===0}]);
    if(sql.includes('as conversation_state'))return rows([{thread:!db.thread,messages:db.messages.length===0,participants:!db.thread,events:!db.thread,conversation_state:!db.thread,web_logs:db.logs.length===0}]);
    throw Error('unhandled_test_query');
  }};
  const args={client,journal,peerJournal,dispatcherFingerprint};
  await prepareWebReplyDestinationInvariant(args);
  record.state.webNotificationSubscription={input,webSessionId,absentBeforeForward:true,receipt:{subscriptionId},browserClean:true};
  record.state.webBrowserClosure={runId,transportSettled:true};
  record.state.webNotificationSeedPush={settledWithoutDestinations:true};record.state.webNotificationDispatch={settled:true};
  record.state.webNotificationMessage={capturedBeforeForward:true,input:{p_actor_profile_id:profileId,p_thread_id:123,p_message:reply.body,
    p_client_message_id:reply.client_message_id,p_reply_to_message_id:null,p_file_ids:[]},backendReceipt:{messageId:'457',clientMessageId:reply.client_message_id}};
  db.messages.push(reply);
  args.cleanupDisposition={kind:'reply-trigger-no-mutable-destinations',runId,ownerId:profileId,peerId,threadId:'123',replyMessageId:'457',dispatcherFingerprint,
    browserTransportSettled:true,seedDispatcherSettled:true,explicitDispatcherSettled:true,replyTrigger:{requestTerminal:null,mutationImpossible:true}};
  events.length=0;
  return {args,events,get:()=>record,db:()=>db,failCommit:mode=>{failCommit=mode;},addPeerToken:()=>{peerHasToken=true;}};
}

test('both cleanup transactions audit the explicit Web disposition before deleting',async()=>{
  const f=await fixture();
  await removeWebNotificationSubscription(f.args);
  const result=await removeWebNotificationReplyThread(f.args);
  assert.equal(result.removed,true);assert.equal(f.get().state.threadRemoved,true);
  assert.equal(f.get().state.webThreadCleanupDisposition.replyTrigger.requestTerminal,null);
  const deletes=f.events.map((sql,index)=>sql.startsWith('delete from')?index:-1).filter(index=>index>=0);
  for(const index of deletes) {
    const transactionStart=f.events.lastIndexOf('begin',index);
    assert.ok(f.events.slice(transactionStart,index).some(sql=>sql.includes('as auth_sessions')));
  }
});

test('thread cleanup recovers commit applied with lost reply and a real rollback differently',async()=>{
  for(const mode of ['lost','rollback']) {
    const f=await fixture();await removeWebNotificationSubscription(f.args);f.failCommit(mode);
    await assert.rejects(removeWebNotificationReplyThread(f.args));
    assert.equal(f.db().thread,mode==='rollback');
    assert.notEqual(f.get().state.threadRemoved,true);
    const result=await removeWebNotificationReplyThread(f.args);
    assert.equal(result.removed,true);assert.equal(f.db().thread,false);
    assert.equal(f.get().state.webThreadCleanupDisposition.replyTrigger.requestTerminal,null);
  }
});

test('a new peer destination between subscription and thread cleanup prevents thread deletion',async()=>{
  const f=await fixture();await removeWebNotificationSubscription(f.args);f.events.length=0;f.addPeerToken();
  await assert.rejects(removeWebNotificationReplyThread(f.args));
  assert.equal(f.events.some(sql=>sql.startsWith('delete from')),false);
  assert.equal(f.db().thread,true);
});

test('subscription cleanup also recovers lost commit reply and rollback with the explicit disposition',async()=>{
  for(const mode of ['lost','rollback']) {
    const f=await fixture();f.failCommit(mode);
    await assert.rejects(removeWebNotificationSubscription(f.args));
    assert.equal(f.db().subscriptions.length,mode==='rollback'?1:0);
    assert.equal(f.db().logs.length,mode==='rollback'?1:0);
    assert.equal(f.db().thread,true);
    assert.notEqual(f.get().state.webNotificationSubscription.removed,true);
    assert.deepEqual(await removeWebNotificationSubscription(f.args),{removed:true});
    assert.equal((await removeWebNotificationReplyThread(f.args)).removed,true);
  }
});

test('missing thread without prior durable deletion audit does not count as recovered',async()=>{
  const f=await fixture();await removeWebNotificationSubscription(f.args);
  f.db().thread=false;f.db().messages=[];
  await assert.rejects(removeWebNotificationReplyThread(f.args));
  assert.notEqual(f.get().state.threadRemoved,true);
});

test('a fabricated disposition cannot delete subscriptions or use the settled callback fallback',async()=>{
  for(const mixed of [false,true]) {
    const f=await fixture();f.args.cleanupDisposition.replyMessageId='999';
    if(mixed)f.args.operationsSettled=async()=>true;
    await assert.rejects(removeWebNotificationSubscription(f.args));
    assert.equal(f.events.some(sql=>sql.startsWith('delete from')),false);
    assert.equal(f.db().subscriptions.length,1);
  }
});
