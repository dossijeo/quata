import test from 'node:test';
import assert from 'node:assert/strict';
import {captureWebNotificationSeedPush,observeWebNotificationSeedPush} from './e2e-fixtures/web-notification-seed-push.mjs';
function fixture() {
  const plan={runId:'owned-run',ownerId:'owned-profile'};
  let record={runId:plan.runId,profileId:plan.ownerId,state:{threadStarted:true,threadPlan:plan}};
  const journal={read:async()=>structuredClone(record),checkpoint:async state=>{record.state=structuredClone(state);}};
  const response={status_code:200,timed_out:false,has_error:false,
    content:JSON.stringify({result:true,recipients:1,android_tokens:0,web_subscriptions:0,sent:0})};
  let queue=[{id:'999'}],responses=[response];
  const client={query:async sql=>{
    const rows=sql.includes('http_request_queue')?queue:responses;return {rowCount:rows.length,rows};
  }};
  return {client,journal,plan,threadId:'123',messageId:'456',response,
    queue:rows=>{queue=rows;},responses:rows=>{responses=rows;},
    commit:()=>{record.state.threadReceipt={threadId:'123',messageId:'456'};},get:()=>record};
}
test('captures one original request before commit and observes its no-destination response',async()=>{
  const f=fixture();assert.deepEqual(await captureWebNotificationSeedPush(f),{captured:true});
  assert.equal(f.get().state.webNotificationSeedPush.requestId,'999');
  await assert.rejects(observeWebNotificationSeedPush(f));
  f.commit();assert.deepEqual(await observeWebNotificationSeedPush(f),{settled:true,sent:0});
  await assert.rejects(captureWebNotificationSeedPush(f));
});
test('missing or duplicate queued requests fail before commit authorization',async()=>{
  for(const rows of [[],[{id:'999'},{id:'1000'}]]) {
    const f=fixture();f.queue(rows);await assert.rejects(captureWebNotificationSeedPush(f));
    assert.equal(f.get().state.webNotificationSeedPush,undefined);
  }
});
test('a missing response stays pending; timeout/error/provider mutation never certifies settlement',async()=>{
  for(const mode of ['pending','timeout','error','http-error','sent','malformed']) {
    const f=fixture();await captureWebNotificationSeedPush(f);f.commit();
    if(mode==='pending')f.responses([]);
    if(mode==='timeout')f.response.timed_out=true;
    if(mode==='error')f.response.has_error=true;
    if(mode==='http-error')f.response.status_code=500;
    if(mode==='sent')f.response.content=JSON.stringify({result:true,recipients:1,android_tokens:0,web_subscriptions:1,sent:1});
    if(mode==='malformed')f.response.content='private malformed body';
    if(mode==='pending')assert.deepEqual(await observeWebNotificationSeedPush(f),{settled:false});
    else await assert.rejects(observeWebNotificationSeedPush(f),{message:'web_notification_seed_push_unverified'});
    assert.notEqual(f.get().state.webNotificationSeedPush.settledWithoutDestinations,true);
  }
});
test('an iOS destination with zero sends is not a no-destination seed',async()=>{
  const f=fixture();await captureWebNotificationSeedPush(f);f.commit();
  // APNs disabled or skipped can leave sent=0 while a real destination exists.
  f.response.content=JSON.stringify({result:true,recipients:1,android_tokens:0,
    web_subscriptions:0,ios_tokens:1,ios_sent:0,ios_skipped:1,sent:0});
  await assert.rejects(observeWebNotificationSeedPush(f),{message:'web_notification_seed_push_unverified'});
  assert.notEqual(f.get().state.webNotificationSeedPush.settledWithoutDestinations,true);
});
