import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {captureWebNotificationSubscription,observeWebNotificationSubscription,removeWebNotificationSubscription}
  from './e2e-fixtures/web-notification-subscription.mjs';

function fixture() {
  const runId=randomUUID(),profileId=randomUUID(),authUserId=randomUUID(),webSessionId=randomUUID();
  let saved={runId,profileId,authUserId,state:{profileCreated:true,sessions:[{
    runId,profileId,authUserId,webSessionId,authSessionId:randomUUID(),requestStarted:true}],
    threadPlan:{runId,ownerId:profileId},threadReceipt:{threadId:'123',messageId:'456'}}};
  const events=[],subscription={endpoint:'https://push.example.invalid/owned',keys:{p256dh:'p'.repeat(40),auth:'a'.repeat(16)},expirationTime:null};
  const journal={read:async()=>structuredClone(saved),checkpoint:async state=>{events.push('checkpoint');saved.state=structuredClone(state);}};
  const row={id:randomUUID(),web_session_id:webSessionId,profile_id:profileId,auth_user_id:authUserId,
    endpoint:subscription.endpoint,p256dh:subscription.keys.p256dh,auth_secret:subscription.keys.auth,expiration_time:null,disabled_at:null};
  const log={id:'789',message_id:'456',profile_id:profileId,subscription_id:row.id,status:'sent'};
  const db={rows:[],logs:[],refs:[],foreignCount:'0',residue:false,loseCommit:false};
  const client={query:async(sql,args)=>{
    events.push(sql);
    if(sql.startsWith('select w.id'))return {rowCount:1,rows:[{id:webSessionId}]};
    if(sql.startsWith('select id from public.web_push_subscriptions'))return {rowCount:db.rows.length,rows:db.rows};
    if(sql.startsWith('select * from public.web_push_subscriptions'))return {rowCount:db.rows.length,rows:structuredClone(db.rows)};
    if(sql.includes('select id::text,message_id::text'))return {rowCount:db.logs.length,rows:structuredClone(db.logs)};
    if(sql.startsWith('select n.nspname'))return {rowCount:db.refs.length,rows:db.refs};
    if(sql.startsWith('select count(*)'))return {rowCount:1,rows:[{count:db.foreignCount}]};
    if(sql.startsWith('delete from public.web_push_delivery_log')){
      assert.ok(saved.state.webNotificationSubscription.cleanupAudit);assert.deepEqual(args,[db.logs.map(value=>value.id)]);db.logs=[];
    }
    if(sql.startsWith('delete from public.web_push_subscriptions')){
      assert.deepEqual(args,[row.id,row.endpoint]);db.rows=[];
    }
    if(sql.includes('not exists(select 1 from public.web_push_subscriptions'))return {rowCount:1,rows:[{subscription:!db.residue&&db.rows.length===0,deliveries:db.logs.length===0}]};
    if(sql==='commit'&&db.loseCommit){db.loseCommit=false;throw Error('private_transport_detail');}
    return {rowCount:0,rows:[]};
  }};
  return {journal,client,subscription,row,log,db,events,get:()=>saved,
    cleanBrowser:()=>{saved.state.webNotificationSubscription.browserClean=true;}};
}
async function registered() {
  const f=fixture();
  assert.deepEqual(await captureWebNotificationSubscription(f),{captured:true});
  f.db.rows=[f.row];
  assert.deepEqual(await observeWebNotificationSubscription(f),{persisted:true});
  f.row.disabled_at='2026-09-14T22:00:00Z';f.db.logs=[f.log];f.cleanBrowser();
  return f;
}

test('subscription must be absent and durable before the original subscribe can proceed',async()=>{
  const f=fixture();f.db.rows=[f.row];
  await assert.rejects(captureWebNotificationSubscription(f));
  assert.equal(f.events.includes('checkpoint'),false);
  f.db.rows=[];await captureWebNotificationSubscription(f);
  assert.equal(f.get().state.webNotificationSubscription.absentBeforeForward,true);
  await assert.rejects(captureWebNotificationSubscription(f));
});
test('concurrent captures cannot overwrite the same actor journal',async()=>{
  const f=fixture();
  const results=await Promise.allSettled([captureWebNotificationSubscription(f),captureWebNotificationSubscription(f),captureWebNotificationSubscription(f)]);
  assert.equal(results.filter(value=>value.status==='fulfilled').length,1);
  assert.equal(f.events.filter(value=>value==='checkpoint').length,1);
});
test('reconciliation rejects an endpoint reassigned to another actor or session',async()=>{
  for(const column of ['profile_id','auth_user_id','web_session_id','p256dh']) {
    const f=fixture();await captureWebNotificationSubscription(f);f.db.rows=[{...f.row,[column]:randomUUID()}];
    await assert.rejects(observeWebNotificationSubscription(f));
    assert.equal(f.get().state.webNotificationSubscription.receipt,undefined);
  }
});
test('cleanup deletes exact verified logs before subscription and reports no secrets',async()=>{
  const f=await registered();
  assert.deepEqual(await removeWebNotificationSubscription({...f,operationsSettled:async()=>true}),{removed:true});
  const logDelete=f.events.findIndex(value=>value.startsWith('delete from public.web_push_delivery_log'));
  const subscriptionDelete=f.events.findIndex(value=>value.startsWith('delete from public.web_push_subscriptions'));
  assert.ok(logDelete>=0&&subscriptionDelete>logDelete);
  assert.equal(f.get().state.webNotificationSubscription.removed,true);
});
test('cleanup rejects crossed logs, reserved deliveries, live subscriptions and unknown dependencies',async()=>{
  for(const mode of ['foreign-message','foreign-subscription','foreign-recipient','reserved','enabled','new-fk','browser','transport']) {
    const f=await registered();
    if(mode==='foreign-message')f.log.message_id='999';
    if(mode==='foreign-subscription')f.log.subscription_id=randomUUID();
    if(mode==='foreign-recipient')f.log.profile_id=randomUUID();
    if(mode==='reserved')f.log.status='reserved';
    if(mode==='enabled')f.row.disabled_at=null;
    if(mode==='browser')f.get().state.webNotificationSubscription.browserClean=false;
    if(mode==='new-fk'){
      f.db.refs=[{schema:'public',table:'future_table',column:'delivery_id',parent:'web_push_delivery_log',key_count:1,parent_column:'id'}];
      f.db.foreignCount='1';
    }
    await assert.rejects(removeWebNotificationSubscription({...f,operationsSettled:async()=>mode!=='transport'}),
      {message:'web_notification_subscription_unverified'});
    assert.equal(f.events.some(value=>value.startsWith('delete from')),false);
    assert.notEqual(f.get().state.webNotificationSubscription.removed,true);
  }
});
test('a lost commit reply reconciles durable exact IDs without repeating provider operations',async()=>{
  const f=await registered();f.db.loseCommit=true;
  await assert.rejects(removeWebNotificationSubscription({...f,operationsSettled:async()=>true}));
  assert.equal(f.db.rows.length,0);assert.equal(f.db.logs.length,0);
  assert.deepEqual(await removeWebNotificationSubscription({...f,operationsSettled:async()=>true}),{removed:true});
});
