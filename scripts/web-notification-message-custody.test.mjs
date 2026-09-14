import test from 'node:test';
import assert from 'node:assert/strict';
import {createWebNotificationMessageCustody} from './e2e-fixtures/web-notification-message-custody.mjs';

const runId='12345678-1234-4234-8234-123456789abc',profileId='22345678-1234-4234-8234-123456789abc';
function fixture({writeFails=false}={}) {
  let record={runId,profileId,state:{profileCreated:true,threadStarted:true,
    threadPlan:{runId,ownerId:profileId},threadReceipt:{threadId:'123'}}};
  let writes=0;
  const journal={read:async()=>structuredClone(record),checkpoint:async state=>{
    if(writeFails)throw Error('private_write_failed');
    writes++;record.state=structuredClone(state);
  }};
  const options={journal,runId,profileId,threadId:'123',marker:`quata-web-reply-${runId}`};
  const payload={p_actor_profile_id:profileId,p_thread_id:123,p_message:options.marker,
    p_file_ids:[],p_reply_to_message_id:null,p_client_message_id:'1789412345678--7abc'};
  return {options,payload,read:()=>record,writes:()=>writes};
}
test('captures the real signed-hex client ID before permitting forward',async()=>{
  const f=fixture(),adapter=createWebNotificationMessageCustody(f.options);
  assert.deepEqual(await adapter.capture(f.payload),{captured:true});
  assert.deepEqual(f.read().state.webNotificationMessage.input,f.payload);
  await assert.rejects(adapter.capture(f.payload));
  assert.equal(f.writes(),1);
});
test('a failed durable write never authorizes forward or a second attempt',async()=>{
  const f=fixture({writeFails:true}),adapter=createWebNotificationMessageCustody(f.options);
  await assert.rejects(adapter.capture(f.payload));
  await assert.rejects(adapter.capture(f.payload));
  assert.equal(f.writes(),0);
});
test('rejects foreign actors, attachments, altered text, unknown fields and unsafe thread IDs',async()=>{
  for(const change of [{p_actor_profile_id:runId},{p_file_ids:[1]},{p_message:'foreign'},
    {extra:true},{p_thread_id:Number.MAX_SAFE_INTEGER+1},{p_client_message_id:'notification-reply-'+runId}]) {
    const f=fixture();
    await assert.rejects(createWebNotificationMessageCustody(f.options).capture({...f.payload,...change}));
    assert.equal(f.writes(),0);
  }
});
test('concurrent captures and a recreated adapter cannot overwrite custody',async()=>{
  const f=fixture(),adapter=createWebNotificationMessageCustody(f.options);
  const result=await Promise.allSettled([adapter.capture(f.payload),adapter.capture(f.payload)]);
  assert.equal(result.filter(value=>value.status==='fulfilled').length,1);
  await assert.rejects(createWebNotificationMessageCustody(f.options).capture(f.payload));
  assert.equal(f.writes(),1);
});
test('two adapters sharing a fixture cannot authorize concurrent forwarding',async()=>{
  const f=fixture();
  const first=createWebNotificationMessageCustody(f.options);
  const second=createWebNotificationMessageCustody({...f.options,journal:{...f.options.journal}});
  const result=await Promise.allSettled([first.capture(f.payload),second.capture(f.payload)]);
  assert.equal(result.filter(value=>value.status==='fulfilled').length,1);
  assert.equal(f.writes(),1);
});
test('private journal errors are replaced with a fixed diagnostic',async()=>{
  const f=fixture({writeFails:true});
  await assert.rejects(createWebNotificationMessageCustody(f.options).capture(f.payload),
    {message:'web_notification_message_custody_unverified'});
});
