import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {installNotificationReplyNegativeBlock as install,removeNotificationReplyNegativeBlock as remove} from './e2e-fixtures/notification-reply-negative-block.mjs';

function fixture() {
  const runId=randomUUID(),owner=randomUUID(),peer=randomUUID(),auth=randomUUID(),peerAuth=randomUUID();
  const plan={runId,ownerId:owner,peerId:peer,uniqueKey:`quata-deep-link-${runId}`,messageKey:`quata-deep-link-message-${runId}`,body:`Deep link ${runId}`};
  let saved={runId,profileId:owner,authUserId:auth,state:{profileCreated:true,threadStarted:true,threadPlan:plan,threadReceipt:{threadId:'12',messageId:'34'}}};
  const peerRecord={runId,profileId:peer,authUserId:peerAuth,state:{profileCreated:true}};
  const f={saved:()=>saved,peerRecord,events:[],blocks:[],alter:()=>{},fault:null};
  f.journal={read:async()=>structuredClone(saved),checkpoint:async state=>{f.events.push('checkpoint');if(f.fault==='checkpoint')throw Error('private detail');saved.state=structuredClone(state);}};
  f.peerJournal={read:async()=>structuredClone(peerRecord)};
  f.client={query:async(sql,p)=>{
    f.events.push(sql);
    if(f.fault&&sql===f.fault)throw Error('private detail');
    let rows=[];
    if(sql.includes('from public.community_profiles p'))rows=[{id:owner,auth_user_id:auth},{id:peer,auth_user_id:peerAuth}];
    else if(sql.includes('from public.chat_threads'))rows=[{id:'12',type:'group',created_by_profile_id:owner,unique_key:plan.uniqueKey}];
    else if(sql.includes('from public.chat_participants'))rows=[{profile_id:owner,left_at:null},{profile_id:peer,left_at:null}];
    else if(!sql.startsWith('with owned_messages')&&sql.includes('from public.chat_messages where thread_id'))rows=[{id:'34',sender_profile_id:peer,body:plan.body,client_message_id:plan.messageKey,reply_to_message_id:null,forwarded_from_message_id:null}];
    else if(sql.includes('from public.chat_attachments'))rows=[{count:'0'}];
    else if(sql.startsWith('with owned_messages'))rows=[{external_messages:'0',external_conversation_state:'0',sos_events:'0',sos_recipients:'0'}];
    else if(sql.startsWith('select id::text,thread_id::text'))rows=structuredClone(f.blocks);
    else if(sql.startsWith('insert into public.chat_profile_blocks')) {
      assert.equal(saved.state.notificationReplyNegativeBlock.started,true);
      assert.deepEqual(p,['12',peer,owner]);f.blocks=[{id:'56',thread_id:'12'}];rows=[{id:'56'}];
    } else if(sql.startsWith('delete from public.chat_profile_blocks')) {
      assert.equal(saved.state.notificationReplyNegativeBlock.removalStarted,true);
      assert.deepEqual(p,['12',peer,owner,'56']);rows=[{id:'56'}];f.blocks=[];
    }
    f.alter(sql,rows);return {rowCount:rows.length,rows};
  }};
  f.args={client:f.client,journal:f.journal,peerJournal:f.peerJournal};return f;
}

test('own peer block is journaled before insert, read back and removed only after settlement and unchanged baseline',async()=>{
  const f=fixture();assert.deepEqual(await install(f.args),{installed:true});
  assert.equal(f.saved().state.notificationReplyNegativeBlock.verified,true);
  await assert.rejects(remove({...f.args,operationsSettled:async()=>false}));
  assert.equal(f.blocks.length,1);
  assert.deepEqual(await remove({...f.args,operationsSettled:async()=>true}),{removed:true,baselineUnchanged:true});
  assert.equal(f.saved().state.notificationReplyNegativeBlock.removed,true);
});

test('namespace, exact peer custody and prior attempt guard reject before SQL',async()=>{
  for(const mutate of [f=>f.peerRecord.runId=randomUUID(),f=>f.saved().state.threadPlan.uniqueKey='foreign',
    f=>f.saved().state.notificationReply={},f=>f.peerRecord.profileId=randomUUID(),f=>f.saved().state.profileCreated=false]) {
    const f=fixture();mutate(f);await assert.rejects(install(f.args),/negative_block_unresolved/);
    assert.equal(f.events.some(x=>x==='begin'),false);
  }
});

test('preexisting scoped or global block cannot be adopted or removed',async()=>{
  for(const thread_id of ['12',null]) {
    const f=fixture();f.blocks=[{id:'99',thread_id}];await assert.rejects(install(f.args));
    await assert.rejects(remove({...f.args,operationsSettled:async()=>true}));
    assert.equal(f.events.some(x=>x.startsWith('insert')||x.startsWith('delete')),false);
  }
});

test('changed actor, thread, participants, seed, attachments and external references fail closed',async()=>{
  for(const needle of ['from public.community_profiles p','from public.chat_threads','from public.chat_participants',
    'from public.chat_messages where thread_id','from public.chat_attachments','with owned_messages']) {
    for(const phase of ['install','remove']) {
      const f=fixture();if(phase==='remove')await install(f.args);
      f.alter=(sql,rows)=>{if(sql.includes(needle))rows.length=0;};
      await assert.rejects(phase==='install'?install(f.args):remove({...f.args,operationsSettled:async()=>true}));
      assert.equal(f.events.some(x=>x.startsWith('delete')),false);
      assert.notEqual(f.saved().state.notificationReplyNegativeBlock.removed,true);
    }
  }
});

test('new owner message retains the block and custody',async()=>{
  const f=fixture();await install(f.args);
  f.alter=(sql,rows)=>{if(sql.includes('from public.chat_messages where thread_id'))rows.push({...rows[0],id:'35',sender_profile_id:f.saved().profileId});};
  await assert.rejects(remove({...f.args,operationsSettled:async()=>true}));assert.equal(f.blocks.length,1);
});

test('checkpoint failure prevents mutation; uncertain commit retains exact id and prevents install retry',async()=>{
  const a=fixture();a.fault='checkpoint';await assert.rejects(install(a.args),/^Error: notification_reply_negative_block_unresolved$/);
  assert.equal(a.events.some(x=>x.startsWith('insert')),false);
  const f=fixture();f.fault='commit';await assert.rejects(install(f.args));
  assert.equal(f.saved().state.notificationReplyNegativeBlock.blockId,'56');
  await assert.rejects(install(f.args));assert.equal(f.events.filter(x=>x.startsWith('insert')).length,1);
  f.fault=null;await remove({...f.args,operationsSettled:async()=>true});
});

test('replacement block cannot be deleted by tuple alone',async()=>{
  const f=fixture();await install(f.args);f.blocks[0].id='99';
  await assert.rejects(remove({...f.args,operationsSettled:async()=>true}));
  assert.equal(f.events.some(x=>x.startsWith('delete')),false);
});

test('lost removal commit acknowledgement reconciles absence without repeating deletion',async()=>{
  const f=fixture();await install(f.args);f.fault='commit';
  await assert.rejects(remove({...f.args,operationsSettled:async()=>true}));
  assert.notEqual(f.saved().state.notificationReplyNegativeBlock.removed,true);
  f.fault=null;await remove({...f.args,operationsSettled:async()=>true});
  assert.equal(f.events.filter(x=>x.startsWith('delete')).length,1);
});

test('unverified insert readback retains intent and does not permit a second insert',async()=>{
  const f=fixture();
  f.alter=(sql,rows)=>{if(sql.startsWith('select id::text,thread_id::text')&&rows.length)rows[0].id='99';};
  await assert.rejects(install(f.args));await assert.rejects(install(f.args));
  assert.equal(f.events.filter(x=>x.startsWith('insert')).length,1);
  assert.notEqual(f.saved().state.notificationReplyNegativeBlock.verified,true);
});

test('failed post-delete readback never marks removal complete',async()=>{
  const f=fixture();await install(f.args);
  f.alter=(sql,rows)=>{if(sql.startsWith('select id::text,thread_id::text')&&f.events.some(x=>x.startsWith('delete')))rows.push({id:'99',thread_id:'12'});};
  await assert.rejects(remove({...f.args,operationsSettled:async()=>true}));
  assert.notEqual(f.saved().state.notificationReplyNegativeBlock.removed,true);
});
