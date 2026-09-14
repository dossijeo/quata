import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {dispatchWebNotificationSeed} from './e2e-fixtures/web-notification-dispatch.mjs';
function fixture() {
  const runId=randomUUID(),profileId=randomUUID(),authUserId=randomUUID(),peerId=randomUUID();
  let saved={runId,profileId,authUserId,state:{profileCreated:true,threadStarted:true,
    threadPlan:{runId,ownerId:profileId,peerId,uniqueKey:`quata-deep-link-${runId}`,messageKey:`quata-deep-link-message-${runId}`,body:`Deep link ${runId}`},
    threadReceipt:{threadId:'123',messageId:'456'},
    webNotificationSeedPush:{threadId:'123',messageId:'456',settledWithoutDestinations:true},
    webNotificationSubscription:{absentBeforeForward:true,receipt:{subscriptionId:randomUUID()},webSessionId:randomUUID(),
      input:{endpoint:'https://push.example.invalid/owned',p256dh:'p'.repeat(40),authSecret:'a'.repeat(16)}}}};
  const journal={read:async()=>structuredClone(saved),checkpoint:async state=>{saved.state=structuredClone(state);}};
  const destination={native_absent:true,web_count:'1',exact_subscription:true};
  const client={query:async sql=>sql.startsWith('select m.id')?{rowCount:1,rows:[{id:'456'}]}:{rowCount:1,rows:[destination]}};
  const response={status:200,body:{result:true,recipients:1,android_tokens:0,web_subscriptions:1,web_sent:1,web_skipped:0,sent:1,ios_tokens:0}};
  let calls=0;
  const execute=async input=>{
    calls++;assert.deepEqual(input,{messageId:'456'});assert.equal(saved.state.webNotificationDispatch.started,true);
    return response;
  };
  return {journal,client,execute,response,destination,get:()=>saved,calls:()=>calls};
}
test('dispatch is write-ahead, exactly once, and completion is independent of notification click',async()=>{
  const f=fixture();assert.deepEqual(await dispatchWebNotificationSeed(f),{completed:true,webSent:1});
  assert.equal(f.get().state.webNotificationDispatch.settled,true);
  await assert.rejects(dispatchWebNotificationSeed(f));assert.equal(f.calls(),1);
});
test('lost response and non-success receipts remain unresolved and never retry',async()=>{
  for(const mode of ['lost','http','skipped','wrong-count']) {
    const f=fixture();let called=0;const execute=f.execute;
    f.execute=async input=>{called++;if(mode==='lost')throw Error('private_transport');return execute(input);};
    if(mode==='http')f.response.status=504;
    if(mode==='skipped'){f.response.body.web_sent=0;f.response.body.web_skipped=1;}
    if(mode==='wrong-count')f.response.body.web_subscriptions=2;
    await assert.rejects(dispatchWebNotificationSeed(f),{message:'web_notification_dispatch_unverified'});
    assert.equal(f.get().state.webNotificationDispatch.settled,false);
    await assert.rejects(dispatchWebNotificationSeed(f));assert.equal(called,1);
  }
});
test('unexpected destinations and failed durable intent prevent dispatch',async()=>{
  for(const mode of ['native','web','reassigned','disk']) {
    const f=fixture();
    if(mode==='native')f.destination.native_absent=false;
    if(mode==='web')f.destination.web_count='2';
    if(mode==='reassigned')f.destination.exact_subscription=false;
    if(mode==='disk')f.journal.checkpoint=async()=>{throw Error('private_disk');};
    await assert.rejects(dispatchWebNotificationSeed(f));assert.equal(f.calls(),0);
  }
});
test('two handles for one fixture cannot dispatch concurrently',async()=>{
  const f=fixture();
  const outcomes=await Promise.allSettled([dispatchWebNotificationSeed(f),dispatchWebNotificationSeed({...f,journal:{...f.journal}})]);
  assert.equal(outcomes.filter(value=>value.status==='fulfilled').length,1);assert.equal(f.calls(),1);
});
