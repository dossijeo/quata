import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {submitNotificationReplyAttempt,submitAndroidNotificationReplyAttempt,observeNotificationReplyMessage,removeNotificationReplyThread,
  observeWebNotificationReplyMessage,removeWebNotificationReplyThread,removeAndroidNotificationReplyThread} from './e2e-fixtures/notification-reply-attempt.mjs';
import {prepareAndroidReplyDestinationInvariant} from './e2e-fixtures/android-notification-cleanup-disposition.mjs';
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

test('Android submission requires its own verified custody and rejects mixed or uncertain installation',async()=>{
  for(const mode of ['verified','ios-only','mixed','unverified','clearing','foreign-owner','lost-receipt']) {
    const f=fixture();let calls=0;
    f.change(saved=>{
      const session=saved.state.sessions[0];
      if(mode!=='ios-only') {
        session.androidSession=structuredClone(session.iosSession);
        if(mode!=='mixed')delete session.iosSession;
      }
      if(mode==='unverified')session.androidSession.install.verified=false;
      if(mode==='clearing')session.androidSession.clear={started:true};
      if(mode==='foreign-owner')session.androidSession.install.input.profileId=randomUUID();
    });
    const execute=async()=>{
      calls++;assert.equal(f.get().state.notificationReply.started,true);
      if(mode==='lost-receipt')throw Error('uncertain');
      return f.receipt;
    };
    const args={journal:f.journal,stepId:f.stepId,execute};
    if(mode==='verified') {
      await submitAndroidNotificationReplyAttempt(args);
      const result=await observeNotificationReplyMessage({client:{query:async()=>({rowCount:1,rows:[f.reply]})},journal:f.journal});
      assert.deepEqual(result,{persisted:true,messageId:'457',count:1});
    } else await assert.rejects(submitAndroidNotificationReplyAttempt(args));
    assert.equal(calls,['verified','lost-receipt'].includes(mode)?1:0);
    if(calls) {
      await assert.rejects(submitAndroidNotificationReplyAttempt({...args,stepId:randomUUID()}));
      assert.equal(calls,1);
    }
  }
});

test('iOS submission cannot consume Android or mixed custody',async()=>{
  for(const mixed of [false,true]) {
    const f=fixture();f.change(saved=>{
      const session=saved.state.sessions[0];session.androidSession=structuredClone(session.iosSession);
      if(!mixed)delete session.iosSession;
    });
    await assert.rejects(submitNotificationReplyAttempt({journal:f.journal,stepId:f.stepId,
      execute:async()=>{assert.fail('foreign platform must not execute');}}));
    assert.equal(f.events.length,0);
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

test('Android cannot use the legacy native cleanup without its provider invariant',async()=>{
  const f=fixture();f.change(record=>{
    record.state.sessions[0].androidSession=record.state.sessions[0].iosSession;
    delete record.state.sessions[0].iosSession;
  });
  await assert.rejects(removeNotificationReplyThread({client:cleanupClient(f),journal:f.journal,operationsSettled:async()=>true}));
  assert.equal(f.events.length,0);
  await assert.rejects(removeAndroidNotificationReplyThread({client:cleanupClient(f),journal:f.journal}));
  assert.equal(f.events.length,0);
});

test('Android thread deletion rechecks provider destinations inside its own transaction',async()=>{
  for(const pendingDestination of [false,true]) {
    const f=fixture(),dispatcherFingerprint='a'.repeat(64),peerAuth=randomUUID();
    f.change(record=>{
      record.state.androidRuntimeFreeze={dispatcherFingerprint};
      record.state.webNotificationSeedPush={settledWithoutDestinations:true};
      const session=record.state.sessions[0];session.androidSession=session.iosSession;delete session.iosSession;
      session.androidSession.install.input={...session.androidSession.install.input,stage:'install',stepId:randomUUID(),
        accessToken:'test-access',refreshToken:'test-refresh',expiresAt:2000000000,email:'fixture@example.invalid',displayName:'Fixture',isOfficial:false};
    });
    const peerJournal={read:async()=>({runId:f.get().runId,profileId:f.get().state.threadPlan.peerId,authUserId:peerAuth,
      state:{profileCreated:true,sessions:[]}})};
    const base=cleanupClient(f);let forbid=false;
    const client={query:async(sql,args)=>{
      const record=f.get(),peer=record.state.threadPlan.peerId;
      if(sql.startsWith('select p.id,p.auth_user_id')) {
        f.events.push(sql);return {rowCount:2,rows:[{id:record.profileId,auth_user_id:record.authUserId},{id:peer,auth_user_id:peerAuth}]};
      }
      if(sql.startsWith('select id::text from public.chat_threads'))return {rowCount:1,rows:[{id:'123'}]};
      if(sql.startsWith('select profile_id,left_at'))return {rowCount:2,rows:[{profile_id:record.profileId,left_at:null},{profile_id:peer,left_at:null}]};
      if(sql.includes(' as auth_sessions,')) {
        f.events.push('destination-audit');return {rowCount:1,rows:[{auth_sessions:true,web_sessions:true,native_tokens:!forbid,
          web_subscriptions:true,native_logs:true,reply_web_logs:true}]};
      }
      if(sql.startsWith('select id::text from public.chat_messages'))return {rowCount:1,rows:[{id:'457'}]};
      return base.query(sql,args);
    }};
    // The preparation transaction is independent of the later cleanup transaction.
    // Base mock's begin assertion applies only once cleanup is marked started.
    const preparationClient={query:async(sql,args)=>['begin','commit'].includes(sql)?{rowCount:0,rows:[]}:client.query(sql,args)};
    await prepareAndroidReplyDestinationInvariant({client:preparationClient,journal:f.journal,peerJournal,dispatcherFingerprint});
    await submitAndroidNotificationReplyAttempt({journal:f.journal,stepId:f.stepId,execute:async()=>f.receipt});
    await observeNotificationReplyMessage({client:{query:async()=>({rowCount:1,rows:[f.reply]})},journal:f.journal});
    f.change(record=>{
      const session=record.state.sessions[0];session.androidSession.clear={started:true,verified:true,
        input:{...session.androidSession.install.input,stage:'clear',stepId:randomUUID()}};
      record.state.androidReplyClosure={runId:record.runId,processClosed:true,notificationRemoved:true,transportSettled:true};
    });
    const record=f.get(),cleanupDisposition={kind:'android-reply-trigger-no-mutable-destinations',runId:record.runId,
      ownerId:record.profileId,peerId:record.state.threadPlan.peerId,threadId:'123',replyMessageId:'457',dispatcherFingerprint,
      nativeProcessClosed:true,sessionCustodySettled:true,seedDispatcherSettled:true,replyTrigger:{requestTerminal:null,mutationImpossible:true}};
    f.events.length=0;forbid=pendingDestination;
    const args={client,journal:f.journal,peerJournal,dispatcherFingerprint,cleanupDisposition};
    if(forbid) {
      await assert.rejects(removeAndroidNotificationReplyThread(args));
      assert.ok(f.events.includes('rollback'));assert.equal(f.events.some(value=>value.startsWith('delete from')),false);
    } else {
      const result=await removeAndroidNotificationReplyThread(args);assert.equal(result.matchesVerifiedReceipt,true);
      const auditIndex=f.events.indexOf('destination-audit'),deleteIndex=f.events.findIndex(value=>value.startsWith('delete from'));
      assert.ok(f.events.indexOf('begin')<auditIndex&&auditIndex<deleteIndex);
      assert.ok(f.events.includes('commit'));
    }
  }
});
