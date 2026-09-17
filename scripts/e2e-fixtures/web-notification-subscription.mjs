// Coordinator-only: exclusive run lock, serialized DPAPI journal writes and an
// isolated browser profile are prerequisites. Never report subscription secrets.
import {assertWebReplyCleanupDisposition} from './web-notification-cleanup-disposition.mjs';
const uuid=/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
const fail=()=>Error('web_notification_subscription_unverified');
const same=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
const pending=new Set();
const quote=value=>'"'+value.replaceAll('"','""')+'"';

function ownedSession(record) {
  if(![record.runId,record.profileId,record.authUserId].every(value=>typeof value==='string'&&uuid.test(value))||
    record.state?.profileCreated!==true||record.state.profileRetired===true||record.state.sessions?.length!==1)throw fail();
  const session=record.state.sessions[0];
  if(['runId','profileId','authUserId'].some(key=>session[key]!==record[key])||
    session.requestStarted!==true||![session.webSessionId,session.authSessionId].every(value=>typeof value==='string'&&uuid.test(value)))throw fail();
  return session;
}

function subscriptionInput(value) {
  if(!value||typeof value.endpoint!=='string'||value.endpoint!==value.endpoint.trim()||
    !value.endpoint.startsWith('https://')||typeof value.keys?.p256dh!=='string'||value.keys.p256dh.length<40||
    typeof value.keys.auth!=='string'||value.keys.auth.length<16||
    ![null,undefined].includes(value.expirationTime)&&(!Number.isSafeInteger(value.expirationTime)||value.expirationTime<0))throw fail();
  const url=new URL(value.endpoint);
  if(url.username||url.password||url.hash)throw fail();
  return {endpoint:value.endpoint,p256dh:value.keys.p256dh,authSecret:value.keys.auth,expirationTime:value.expirationTime??null};
}

async function auditSession(client,record,session,lock=false) {
  const result=await client.query(`select w.id from public.web_client_sessions w
    join public.community_profiles p on p.id=w.profile_id join auth.users u on u.id=p.auth_user_id
    where w.id=$1::uuid and w.profile_id=$2::uuid and w.auth_user_id=$3::uuid and u.id=$3::uuid
      and u.raw_app_meta_data->'quata_e2e'->>'unit'='FLOW-DEEP-LINKS'
      and u.raw_app_meta_data->'quata_e2e'->>'run_id'=$4
      ${lock?'for update of w,p,u':'and w.revoked_at is null'}`,
    [session.webSessionId,record.profileId,record.authUserId,record.runId]);
  if(result.rowCount!==1)throw fail();
}

export async function captureWebNotificationSubscription({client,journal,subscription}) {
  let key,acquired=false;
  try {
    let record=await journal.read();
    const input=subscriptionInput(subscription);
    ownedSession(record);
    key=`${record.runId}/${record.profileId}`;
    if(pending.has(key))throw fail();
    pending.add(key);acquired=true;
    record=await journal.read();
    const session=ownedSession(record);
    if(key!==`${record.runId}/${record.profileId}`)throw fail();
    if(record.state.webNotificationSubscription!==undefined||session.revocation!==undefined)throw fail();
    await auditSession(client,record,session);
    const before=await client.query('select id from public.web_push_subscriptions where endpoint=$1',[input.endpoint]);
    if(before.rowCount!==0)throw fail();
    record.state.webNotificationSubscription={input,webSessionId:session.webSessionId,absentBeforeForward:true};
    await journal.checkpoint(record.state);
    return {captured:true};
  } catch {throw fail();}
  finally {if(acquired)pending.delete(key);}
}

function expected(row,record,entry) {
  return uuid.test(row.id)&&row.web_session_id===entry.webSessionId&&row.profile_id===record.profileId&&
    row.auth_user_id===record.authUserId&&row.endpoint===entry.input.endpoint&&row.p256dh===entry.input.p256dh&&
    row.auth_secret===entry.input.authSecret&&
    (row.expiration_time===null?null:String(row.expiration_time))===(entry.input.expirationTime===null?null:String(entry.input.expirationTime));
}

function custody(record,session) {
  const entry=record.state.webNotificationSubscription;
  if(entry?.absentBeforeForward!==true||entry.webSessionId!==session.webSessionId)throw fail();
  const input=subscriptionInput({endpoint:entry.input?.endpoint,keys:{p256dh:entry.input?.p256dh,auth:entry.input?.authSecret},
    expirationTime:entry.input?.expirationTime});
  if(!same(input,entry.input))throw fail();
  return entry;
}

export async function observeWebNotificationSubscription({client,journal}) {
  try {
    const record=await journal.read(),session=ownedSession(record),entry=custody(record,session);
    await auditSession(client,record,session);
    const result=await client.query('select * from public.web_push_subscriptions where endpoint=$1',[entry.input.endpoint]);
    if(result.rowCount===0)return {persisted:false};
    if(result.rowCount!==1||!expected(result.rows[0],record,entry)||result.rows[0].disabled_at!==null)throw fail();
    const receipt={subscriptionId:result.rows[0].id};
    if(entry.receipt!==undefined&&!same(receipt,entry.receipt))throw fail();
    entry.receipt=receipt;await journal.checkpoint(record.state);
    return {persisted:true};
  } catch {throw fail();}
}

// Reconciliation requires producer/dispatcher AND browser transport settled,
// browser unsubscribe/owned notification removal complete, and remote disabled.
// It never infers settlement merely from a delivery status or observation timeout.
export async function removeWebNotificationSubscription({client,journal,operationsSettled,cleanupDisposition,peerJournal,dispatcherFingerprint}) {
  try {
    if(cleanupDisposition!==undefined) {
      if(operationsSettled!==undefined)throw fail();
    } else if(typeof operationsSettled!=='function'||await operationsSettled()!==true)throw fail();
    const record=await journal.read(),session=ownedSession(record),entry=custody(record,session);
    const target=record.state.threadReceipt,plan=record.state.threadPlan;
    if(!target||!/^\d+$/.test(target.threadId)||!/^\d+$/.test(target.messageId)||
      plan?.runId!==record.runId||plan.ownerId!==record.profileId||entry.browserClean!==true)throw fail();
    entry.cleanupStarted=true;await journal.checkpoint(record.state);
    await client.query('begin');
    try {
      await client.query("set local lock_timeout='5s'");
      if(cleanupDisposition!==undefined) {
        entry.cleanupDisposition=await assertWebReplyCleanupDisposition({client,journal,peerJournal,
          dispatcherFingerprint,disposition:cleanupDisposition,phase:'subscription'});
        await journal.checkpoint(record.state);
      }
      await auditSession(client,record,session,true);
      const subscriptions=await client.query(`select * from public.web_push_subscriptions
        where endpoint=$1 or profile_id=$2::uuid or auth_user_id=$3::uuid or web_session_id=$4::uuid for update`,
        [entry.input.endpoint,record.profileId,record.authUserId,session.webSessionId]);
      if(subscriptions.rowCount>1)throw fail();
      if(subscriptions.rowCount===1) {
        const row=subscriptions.rows[0];
        if(!expected(row,record,entry)||row.disabled_at===null||
          entry.receipt!==undefined&&entry.receipt.subscriptionId!==row.id)throw fail();
        entry.receipt={subscriptionId:row.id};
      } else if(entry.receipt!==undefined&&entry.cleanupAudit===undefined)throw fail();
      const subscriptionId=entry.receipt?.subscriptionId??null;
      const logs=await client.query(`select id::text,message_id::text,profile_id,subscription_id,status
        from public.web_push_delivery_log where subscription_id=$1::uuid or profile_id=$2::uuid
        or message_id in (select id from public.chat_messages where thread_id=$3::bigint) for update`,
        [subscriptionId,record.profileId,target.threadId]);
      if(logs.rows.some(row=>!/^\d+$/.test(row.id)||row.message_id!==target.messageId||row.profile_id!==record.profileId||
        row.subscription_id!==subscriptionId||!['sent','error'].includes(row.status)))throw fail();
      // Lock each selected row before auditing incoming FKs. Explicitly delete
      // verified logs first; allow no dependent cascade, including new schema.
      const refs=await client.query(`select n.nspname as schema,c.relname as table,a.attname as column,
        f.confrelid::regclass::text as parent,cardinality(f.conkey) as key_count,pa.attname as parent_column
        from pg_constraint f join pg_class c on c.oid=f.conrelid join pg_namespace n on n.oid=c.relnamespace
        join pg_attribute a on a.attrelid=f.conrelid and a.attnum=f.conkey[1]
        join pg_attribute pa on pa.attrelid=f.confrelid and pa.attnum=f.confkey[1]
        where f.contype='f' and f.confrelid in
          ('public.web_push_subscriptions'::regclass,'public.web_push_delivery_log'::regclass)`);
      for(const ref of refs.rows) {
        if(ref.key_count!==1||ref.parent_column!=='id')throw fail();
        const isSubscription=['web_push_subscriptions','public.web_push_subscriptions'].includes(ref.parent);
        if(isSubscription&&ref.schema==='public'&&ref.table==='web_push_delivery_log'&&ref.column==='subscription_id')continue;
        const ids=isSubscription?(subscriptionId?[subscriptionId]:[]):logs.rows.map(row=>row.id);
        const result=await client.query(`select count(*)::text as count from ${quote(ref.schema)}.${quote(ref.table)}
          where ${quote(ref.column)}=any($1::${isSubscription?'uuid':'bigint'}[])`,[ids]);
        if(result.rows?.[0]?.count!=='0')throw fail();
      }
      if(entry.cleanupAudit===undefined) {
        entry.cleanupAudit={subscriptionId,logIds:logs.rows.map(row=>row.id).sort()};
      } else if(entry.cleanupAudit.subscriptionId!==subscriptionId||logs.rows.some(row=>!entry.cleanupAudit.logIds.includes(row.id)))throw fail();
      await journal.checkpoint(record.state);
      await client.query('delete from public.web_push_delivery_log where id=any($1::bigint[])',[logs.rows.map(row=>row.id)]);
      if(subscriptionId)await client.query('delete from public.web_push_subscriptions where id=$1::uuid and endpoint=$2',
        [subscriptionId,entry.input.endpoint]);
      const gone=await client.query(`select
        not exists(select 1 from public.web_push_subscriptions where endpoint=$1 or profile_id=$2::uuid or auth_user_id=$3::uuid) as subscription,
        not exists(select 1 from public.web_push_delivery_log where subscription_id=$4::uuid or profile_id=$2::uuid
          or message_id in (select id from public.chat_messages where thread_id=$5::bigint)) as deliveries`,
        [entry.input.endpoint,record.profileId,record.authUserId,subscriptionId,target.threadId]);
      if(gone.rowCount!==1||gone.rows[0].subscription!==true||gone.rows[0].deliveries!==true)throw fail();
      await client.query('commit');
    } catch {await client.query('rollback').catch(()=>{});throw fail();}
    entry.removed=true;await journal.checkpoint(record.state);
    return {removed:true};
  } catch {throw fail();}
}
