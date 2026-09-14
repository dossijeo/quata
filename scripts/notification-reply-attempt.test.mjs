import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {submitNotificationReplyAttempt,observeNotificationReplyMessage,removeNotificationReplyThread,
  observeWebNotificationReplyMessage,removeWebNotificationReplyThread} from './e2e-fixtures/notification-reply-attempt.mjs';
import {createWebNotificationMessageCustody} from './e2e-fixtures/web-notification-message-custody.mjs';

function fixture() {
  const runId=randomUUID(),profileId=randomUUID(),authUserId=randomUUID(),peerId=randomUUID(),authSessionId=randomUUID();
  let saved={runId,profileId,authUserId,state:{profileCreated:true,threadStarted:true,
    threadPlan:{runId,ownerId:profileId,peerId,uniqueKey:`quata-deep-link-${runId}`,messageKey:`quata-deep-link-message-${runId}`,body:`Deep link ${runId}`},
    threadReceipt:{threadId:'123',messageId:'456'},sessions:[{runId,profileId,authUserId,authSessionId,
      iosSession:{install:{started:true,verified:true,input:{runId,profileId,authUserId,authSessionId}}}}]}};
  const events=[],journal={read:async()=>structuredClone(saved),checkpoint:async state=>{events.push('checkpoint');saved.state=structuredClone(state);}};
  const stepId=randomUUID(),receipt={runId,stepId,submittedBySystemUi:true,backendVerified:false,
    replyMarker:`qadata-reply-text-${stepId}`,notificationMarker:`qadata-reply-alert-${stepId}`};
  const seed={id:'456',sender_profile_id:peerId,body:saved.state.threadPlan.body,client_message_id:saved.state.threadPlan.messageKey,
    reply_to_message_id:null,forwarded_from_message_id:null};
  const reply={id:'457',sender_profile_id:profileId,body:receipt.replyMarker,client_message_id:`notification-reply-${randomUUID()}`,
    reply_to_message_id:null,forwarded_from_message_id:null};
  return {journal,stepId,receipt,events,seed,reply,get:()=>saved,change:fn=>fn(saved)};
}

test('intent must be durable before the only submission and an uncertain attempt cannot retry',async()=>{
  for(const mode of ['success','lost','wrong-receipt','checkpoint-failed']) {
    const f=fixture();let calls=0;
    if(mode==='checkpoint-failed')f.journal.checkpoint=async()=>{throw Error('disk');};
    const args={journal:f.journal,stepId:f.stepId,execute:async input=>{
      calls++;assert.equal(f.get().state.notificationReply.started,true);
      assert.deepEqual(input,f.get().state.notificationReply.input);
      if(mode==='lost')throw Error('uncertain');
      return {...f.receipt,...(mode==='wrong-receipt'?{backendVerified:true}:{})};
    }};
    if(mode==='success')await submitNotificationReplyAttempt(args);
    else await assert.rejects(submitNotificationReplyAttempt(args));
    assert.equal(calls,mode==='checkpoint-failed'?0:1);
    if(mode!=='checkpoint-failed') {
      await assert.rejects(submitNotificationReplyAttempt({...args,stepId:randomUUID()}));
      assert.equal(calls,1);
    }
  }
});

test('unverified or foreign session custody cannot submit',async()=>{
  for(const alter of [f=>{f.state.sessions[0].iosSession.install.verified=false;},
    f=>{f.state.sessions[0].profileId=randomUUID();},f=>{f.state.sessions[0].iosSession.clear={};},
    f=>{f.state.sessions.push(structuredClone(f.state.sessions[0]));}]) {
    const f=fixture();f.change(alter);let called=false;
    await assert.rejects(submitNotificationReplyAttempt({journal:f.journal,stepId:f.stepId,execute:async()=>{called=true;}}));
    assert.equal(called,false);assert.equal(f.events.length,0);
  }
});

async function submitted(f) {
  await submitNotificationReplyAttempt({journal:f.journal,stepId:f.stepId,execute:async()=>f.receipt});
}

test('backend verification rejects duplicates, wrong actor/text and malformed client keys',async()=>{
  for(const mode of ['missing','one','duplicate','foreign','wrong-text','bad-key','reply-to']) {
    const f=fixture();await submitted(f);
    const row=structuredClone(f.reply);
    if(mode==='foreign')row.sender_profile_id=randomUUID();
    if(mode==='wrong-text')row.body='unrelated';
    if(mode==='bad-key')row.client_message_id='not-a-native-reply';
    if(mode==='reply-to')row.reply_to_message_id='456';
    const rows=mode==='missing'?[]:mode==='duplicate'?[row,{...row,id:'458'}]:[row];
    const client={query:async()=>({rows,rowCount:rows.length})};
    if(mode==='missing')assert.deepEqual(await observeNotificationReplyMessage({client,journal:f.journal}),{persisted:false});
    else if(mode==='one') {
      assert.deepEqual(await observeNotificationReplyMessage({client,journal:f.journal}),{persisted:true,messageId:'457',count:1});
      assert.equal(f.get().state.notificationReply.backendReceipt.clientMessageId,row.client_message_id);
    } else await assert.rejects(observeNotificationReplyMessage({client,journal:f.journal}));
  }
});

function cleanupClient(f,{messages=[f.seed,f.reply],foreignParticipant=false,external=false,residue=false,pushResidue=false}={}) {
  const plan=f.get().state.threadPlan;
  return {query:async(sql,args)=>{
    f.events.push(sql);
    if(sql==='begin')assert.equal(f.get().state.replyCleanupStarted,true);
    if(sql.startsWith('select p.id'))return {rowCount:2,rows:[]};
    if(sql.startsWith('select id::text,type'))return {rowCount:1,rows:[{id:'123',type:'group',created_by_profile_id:plan.ownerId,unique_key:plan.uniqueKey}]};
    if(sql.startsWith('select profile_id'))return {rowCount:2,rows:[{profile_id:plan.ownerId},{profile_id:foreignParticipant?randomUUID():plan.peerId}]};
    if(sql.includes('select id::text,sender_profile_id'))return {rowCount:messages.length,rows:messages};
    if(sql.includes('from public.chat_attachments'))return {rows:[{count:'0'}]};
    if(sql.startsWith('with owned_messages'))return {rowCount:1,rows:[{external_messages:external?'1':'0',external_conversation_state:'0',sos_events:'0',sos_recipients:'0'}]};
    if(sql.includes('from public.web_push_subscriptions'))return {rowCount:1,rows:[{subscriptions:!pushResidue,deliveries:!pushResidue}]};
    if(sql.startsWith('delete from')) {
      assert.deepEqual(args,['123',plan.uniqueKey]);
      assert.equal(typeof f.get().state.replyCleanupAudit.replyCount,'number');
    }
    if(sql.startsWith('select\n'))return {rowCount:1,rows:[{thread:!residue,messages:true,participants:true,events:true,conversation_state:true}]};
    return {rowCount:0,rows:[]};
  }};
}

test('cleanup removes only the owned baseline and Reply, after all operations settle',async()=>{
  const f=fixture();await submitted(f);
  await observeNotificationReplyMessage({client:{query:async()=>({rowCount:1,rows:[f.reply]})},journal:f.journal});
  const result=await removeNotificationReplyThread({client:cleanupClient(f),journal:f.journal,operationsSettled:async()=>true});
  assert.deepEqual(result,{removed:true,replyCount:1,matchesVerifiedReceipt:true});
  assert.equal(f.get().state.threadRemoved,true);
  assert.ok(f.events.includes('commit'));
  const g=fixture();await assert.rejects(removeNotificationReplyThread({client:cleanupClient(g),journal:g.journal,operationsSettled:async()=>false}));
  assert.equal(g.events.length,0);
});

test('unexpected messages, participants, references and residue fail cleanup without a false success',async()=>{
  for(const mode of ['foreign-message','foreign-participant','external','residue']) {
    const f=fixture();await submitted(f);
    const options={foreignParticipant:mode==='foreign-participant',external:mode==='external',residue:mode==='residue'};
    if(mode==='foreign-message')options.messages=[f.seed,{...f.reply,body:'foreign text'}];
    await assert.rejects(removeNotificationReplyThread({client:cleanupClient(f,options),journal:f.journal,operationsSettled:async()=>true}));
    assert.ok(f.events.includes('rollback'));
    assert.equal(f.events.includes('commit'),false);
    assert.notEqual(f.get().state.threadRemoved,true);
    if(mode!=='residue')assert.equal(f.events.some(event=>event.startsWith('delete from')),false);
  }
});

test('a late own duplicate can be cleaned but invalidates the previously verified result',async()=>{
  const f=fixture();await submitted(f);
  await observeNotificationReplyMessage({client:{query:async()=>({rowCount:1,rows:[f.reply]})},journal:f.journal});
  const result=await removeNotificationReplyThread({client:cleanupClient(f,{messages:[f.seed,f.reply,{...f.reply,id:'458'}]}),
    journal:f.journal,operationsSettled:async()=>true});
  assert.deepEqual(result,{removed:true,replyCount:2,matchesVerifiedReceipt:false});
});

async function webFixture() {
  const f=fixture(),record=f.get(),marker=`quata-web-reply-${record.runId}`;
  await createWebNotificationMessageCustody({journal:f.journal,runId:record.runId,
    profileId:record.profileId,threadId:'123',marker}).capture({p_actor_profile_id:record.profileId,
      p_thread_id:123,p_message:marker,p_file_ids:[],p_reply_to_message_id:null,
      p_client_message_id:'1789412345678--7abc'});
  f.reply.body=marker;f.reply.client_message_id='1789412345678--7abc';
  return f;
}

test('Web reconciliation uses the exact captured ID and does not claim UI acceptance',async()=>{
  for(const mode of ['one','missing','different-key','different-actor','duplicate']) {
    const f=await webFixture(),row={...f.reply};
    if(mode==='different-key')row.client_message_id='1789412345678--7abd';
    if(mode==='different-actor')row.sender_profile_id=randomUUID();
    const rows=mode==='missing'?[]:mode==='duplicate'?[row,{...row,id:'458'}]:[row];
    const action=()=>observeWebNotificationReplyMessage({client:{query:async()=>({rowCount:rows.length,rows})},journal:f.journal});
    if(mode==='one')assert.deepEqual(await action(),{persisted:true,messageId:'457',count:1});
    else if(mode==='missing')assert.deepEqual(await action(),{persisted:false});
    else await assert.rejects(action());
    assert.equal(f.get().state.notificationReply,undefined);
  }
});

test('Web cleanup keeps native guards and refuses pending push cascades or an uncaptured message',async()=>{
  for(const mode of ['one','push','different-key','foreign-participant','external','unsettled']) {
    const f=await webFixture(),row={...f.reply};
    if(mode==='different-key')row.client_message_id='1789412345678--7abd';
    const options={messages:[f.seed,row],pushResidue:mode==='push',
      foreignParticipant:mode==='foreign-participant',external:mode==='external'};
    const action=()=>removeWebNotificationReplyThread({client:cleanupClient(f,options),journal:f.journal,
      operationsSettled:async()=>mode!=='unsettled'});
    if(mode==='one')assert.deepEqual(await action(),{removed:true,replyCount:1,matchesVerifiedReceipt:false});
    else {
      await assert.rejects(action());
      assert.equal(f.events.some(event=>event.startsWith('delete from')),false);
      assert.notEqual(f.get().state.threadRemoved,true);
    }
  }
});

test('Web and native attempt journals cannot substitute for one another',async()=>{
  const f=await webFixture();
  await assert.rejects(observeNotificationReplyMessage({client:{},journal:f.journal}));
  await assert.rejects(removeNotificationReplyThread({client:cleanupClient(f),journal:f.journal,operationsSettled:async()=>true}));
  f.events.length=0;
  await assert.rejects(removeNotificationReplyThread({client:cleanupClient(f,{messages:[f.seed],pushResidue:true}),
    journal:f.journal,operationsSettled:async()=>true}));
  assert.equal(f.events.length,0);
  const g=fixture();await submitted(g);
  await assert.rejects(observeWebNotificationReplyMessage({client:{},journal:g.journal}));
  await assert.rejects(removeWebNotificationReplyThread({client:cleanupClient(g),journal:g.journal,operationsSettled:async()=>true}));
});
