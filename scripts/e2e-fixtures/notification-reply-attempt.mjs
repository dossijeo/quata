import {assertNoExternalDeepLinkReferences} from './chat-deep-link-cleanup.mjs';
import {assertWebNotificationMessageInput} from './web-notification-message-custody.mjs';
import {assertWebReplyCleanupDisposition} from './web-notification-cleanup-disposition.mjs';
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const id=value=>typeof value==='string'&&/^[1-9][0-9]*$/.test(value)&&BigInt(value)<=9223372036854775807n;
const fail=()=>Error('notification_reply_fixture_unverified');
const same=(a,b)=>JSON.stringify(a)===JSON.stringify(b);

// The existing synthetic actor/thread ownership namespace is retained verbatim.
// The trial report is FLOW-NOTIFICATION-REPLY; this does not certify deep links.
async function context(journal) {
  const record=await journal.read(),plan=record.state?.threadPlan,target=record.state?.threadReceipt;
  if(![record.runId,record.profileId,record.authUserId,plan?.peerId].every(value=>uuid.test(value))||
    plan?.runId!==record.runId||plan.ownerId!==record.profileId||plan.peerId===plan.ownerId||
    plan.uniqueKey!==`quata-deep-link-${record.runId}`||plan.messageKey!==`quata-deep-link-message-${record.runId}`||
    plan.body!==`Deep link ${record.runId}`||record.state.profileCreated!==true||record.state.threadStarted!==true||
    record.state.threadRemoved===true||!id(target?.threadId)||!id(target?.messageId))throw fail();
  return {record,plan,target};
}

export async function submitNotificationReplyAttempt({journal,stepId,execute}) {
  const {record,target}=await context(journal);
  if(!uuid.test(stepId)||typeof execute!=='function'||record.state.notificationReply!==undefined)throw fail();
  const sessions=record.state.sessions;
  if(!Array.isArray(sessions)||sessions.length!==1)throw fail();
  const entry=sessions[0],install=entry.iosSession?.install;
  if(install?.started!==true||install.verified!==true||entry.iosSession.clear!==undefined||
    ['runId','profileId','authUserId'].some(key=>entry[key]!==record[key]||install.input?.[key]!==record[key])||
    entry.authSessionId!==install.input.authSessionId||entry.revocation!==undefined||entry.refreshAttempt!==undefined)throw fail();
  const input={runId:record.runId,stepId,profileId:record.profileId,threadId:target.threadId};
  record.state.notificationReply={input,started:true,uiVerified:false};
  await journal.checkpoint(record.state);
  const receipt=await execute(structuredClone(input));
  const expected={runId:record.runId,stepId,submittedBySystemUi:true,backendVerified:false,
    replyMarker:`qadata-reply-text-${stepId}`,notificationMarker:`qadata-reply-alert-${stepId}`};
  if(!receipt||Object.keys(receipt).sort().join(',')!==Object.keys(expected).sort().join(',')||
    Object.keys(expected).some(key=>receipt[key]!==expected[key]))throw fail();
  const current=await context(journal),attempt=current.record.state.notificationReply;
  if(!same(attempt?.input,input)||attempt.started!==true||attempt.uiVerified!==false)throw fail();
  attempt.uiVerified=true;attempt.receipt=receipt;
  await journal.checkpoint(current.record.state);
  return receipt;
}

function expectedReply(row,record,stepId) {
  return id(row.id)&&row.sender_profile_id===record.profileId&&row.body===`qadata-reply-text-${stepId}`&&
    typeof row.client_message_id==='string'&&/^notification-reply-[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(row.client_message_id)&&
    row.reply_to_message_id===null&&row.forwarded_from_message_id===null;
}

export async function observeNotificationReplyMessage({client,journal}) {
  const {record,target}=await context(journal),attempt=record.state.notificationReply;
  if(attempt?.started!==true||attempt.uiVerified!==true||!uuid.test(attempt.input?.stepId))throw fail();
  return observeReply({client,journal,record,target,attempt,matches:row=>expectedReply(row,record,attempt.input.stepId)});
}

function webAttempt(record,target) {
  const attempt=record.state.webNotificationMessage;
  if(record.state.notificationReply!==undefined||attempt?.capturedBeforeForward!==true)throw fail();
  assertWebNotificationMessageInput(attempt.input,{runId:record.runId,profileId:record.profileId,threadId:target.threadId});
  return attempt;
}

function expectedWebReply(row,record,attempt) {
  return id(row.id)&&row.sender_profile_id===record.profileId&&row.body===attempt.input.p_message&&
    row.client_message_id===attempt.input.p_client_message_id&&
    row.reply_to_message_id===null&&row.forwarded_from_message_id===null;
}

// Database persistence only; the coordinator separately proves notification
// receipt/click, Chat navigation and the actual Send gesture. No inline Reply claim.
export async function observeWebNotificationReplyMessage({client,journal}) {
  const {record,target}=await context(journal),attempt=webAttempt(record,target);
  return observeReply({client,journal,record,target,attempt,matches:row=>expectedWebReply(row,record,attempt)});
}

async function observeReply({client,journal,record,target,attempt,matches}) {
  const result=await client.query(`select id::text,sender_profile_id,body,client_message_id,
    reply_to_message_id,forwarded_from_message_id from public.chat_messages
    where thread_id=$1::bigint and id<>$2::bigint order by id`,[target.threadId,target.messageId]);
  if(result.rowCount===0)return {persisted:false};
  if(result.rowCount!==1||!matches(result.rows[0]))throw fail();
  const row=result.rows[0];
  const receipt={messageId:row.id,clientMessageId:row.client_message_id,threadId:target.threadId,
    senderProfileId:record.profileId,replyMarker:row.body,count:1};
  if(attempt.backendReceipt!==undefined&&!same(attempt.backendReceipt,receipt))throw fail();
  attempt.backendReceipt=receipt;await journal.checkpoint(record.state);
  return {persisted:true,messageId:row.id,count:1};
}

// Cleanup after the UI/transport are settled. Accepts only the original seed and
// exact own Reply markers; duplicates remain a failed test but are removable.
// No dependency or foreign text is silently swept away.
export async function removeNotificationReplyThread({client,journal,operationsSettled}) {
  return removeReplyThread({client,journal,operationsSettled,web:false});
}

export async function removeWebNotificationReplyThread({client,journal,operationsSettled,cleanupDisposition,peerJournal,dispatcherFingerprint}) {
  return removeReplyThread({client,journal,operationsSettled,cleanupDisposition,peerJournal,dispatcherFingerprint,web:true});
}

async function removeReplyThread({client,journal,operationsSettled,web,cleanupDisposition,peerJournal,dispatcherFingerprint}) {
  if(cleanupDisposition!==undefined) {
    if(!web||operationsSettled!==undefined)throw fail();
  } else if(typeof operationsSettled!=='function'||await operationsSettled()!==true)throw fail();
  const {record,plan,target}=await context(journal);
  // Distinct entry points prevent Web custody from relaxing native guards.
  if(web&&record.state.notificationReply!==undefined)throw fail();
  if(!web&&record.state.webNotificationMessage!==undefined)throw fail();
  const attempt=web?(record.state.webNotificationMessage===undefined?undefined:webAttempt(record,target)):record.state.notificationReply;
  if(!web&&attempt!==undefined&&(attempt.started!==true||!uuid.test(attempt.input?.stepId)||
    attempt.input.runId!==record.runId||attempt.input.profileId!==record.profileId||attempt.input.threadId!==target.threadId))throw fail();
  const matches=row=>web?expectedWebReply(row,record,attempt):expectedReply(row,record,attempt.input.stepId);
  record.state.replyCleanupStarted=true;await journal.checkpoint(record.state);
  await client.query('begin');
  try {
    await client.query("set local lock_timeout='5s'");
    if(cleanupDisposition!==undefined) {
      record.state.webThreadCleanupDisposition=await assertWebReplyCleanupDisposition({client,journal,peerJournal,
        dispatcherFingerprint,disposition:cleanupDisposition,phase:'thread'});
      await journal.checkpoint(record.state);
    }
    const owned=await client.query(`select p.id from public.community_profiles p join auth.users u on u.id=p.auth_user_id
      where p.id=any($1::uuid[]) and u.raw_app_meta_data->'quata_e2e'->>'unit'='FLOW-DEEP-LINKS'
      and u.raw_app_meta_data->'quata_e2e'->>'run_id'=$2 for update of p,u`,[[plan.ownerId,plan.peerId],record.runId]);
    if(owned.rowCount!==2)throw fail();
    const thread=await client.query(`select id::text,type,created_by_profile_id,unique_key from public.chat_threads
      where id=$1::bigint or unique_key=$2 for update`,[target.threadId,plan.uniqueKey]);
    if(thread.rowCount>1)throw fail();
    if(thread.rowCount===1) {
      const row=thread.rows[0];
      if(row.id!==target.threadId||row.unique_key!==plan.uniqueKey||row.type!=='group'||row.created_by_profile_id!==plan.ownerId)throw fail();
      const participants=await client.query('select profile_id from public.chat_participants where thread_id=$1::bigint for update',[target.threadId]);
      if(!same(participants.rows.map(row=>row.profile_id).sort(),[plan.ownerId,plan.peerId].sort()))throw fail();
      const messages=await client.query(`select id::text,sender_profile_id,body,client_message_id,
        reply_to_message_id,forwarded_from_message_id from public.chat_messages where thread_id=$1::bigint for update`,[target.threadId]);
      const seed=messages.rows.filter(row=>row.id===target.messageId),replies=messages.rows.filter(row=>row.id!==target.messageId);
      if(seed.length!==1||seed[0].sender_profile_id!==plan.peerId||seed[0].body!==plan.body||seed[0].client_message_id!==plan.messageKey||
        seed[0].reply_to_message_id!==null||seed[0].forwarded_from_message_id!==null||
        replies.some(row=>!attempt||!matches(row)))throw fail();
      const attachments=await client.query(`select count(*)::text as count from public.chat_attachments
        where thread_id=$1::bigint or message_id in (select id from public.chat_messages where thread_id=$1::bigint)`,[target.threadId]);
      if(attachments.rows?.[0]?.count!=='0')throw fail();
      await assertNoExternalDeepLinkReferences({client,threadId:target.threadId});
      if(web) {
        // Subscription/log reconciliation must precede the thread cascade.
        // The profile and message locks above exclude new dependent inserts.
        const push=await client.query(`select
          not exists(select 1 from public.web_push_subscriptions where profile_id=any($1::uuid[])
            or auth_user_id in (select auth_user_id from public.community_profiles where id=any($1::uuid[]))) as subscriptions,
          not exists(select 1 from public.web_push_delivery_log where profile_id=any($1::uuid[])
            or message_id in (select id from public.chat_messages where thread_id=$2::bigint)) as deliveries`,
          [[plan.ownerId,plan.peerId],target.threadId]);
        if(push.rowCount!==1||push.rows[0].subscriptions!==true||push.rows[0].deliveries!==true)throw fail();
      }
      record.state.replyCleanupAudit={replyCount:replies.length,
        matchesVerifiedReceipt:attempt?.backendReceipt!==undefined&&replies.length===1&&
          replies[0].id===attempt.backendReceipt.messageId&&replies[0].client_message_id===attempt.backendReceipt.clientMessageId};
      await journal.checkpoint(record.state);
      await client.query('delete from public.chat_threads where id=$1::bigint and unique_key=$2',[target.threadId,plan.uniqueKey]);
    } else if(record.state.replyCleanupAudit===undefined)throw fail();
    const clean=await client.query(`select
      not exists(select 1 from public.chat_threads where id=$1::bigint or unique_key=$2) as thread,
      not exists(select 1 from public.chat_messages where thread_id=$1::bigint or client_message_id=$3) as messages,
      not exists(select 1 from public.chat_participants where thread_id=$1::bigint) as participants,
      not exists(select 1 from public.chat_events where thread_id=$1::bigint) as events,
      not exists(select 1 from public.conversation_user_state where conversation_id=$1::bigint) as conversation_state`,
      [target.threadId,plan.uniqueKey,plan.messageKey]);
    if(clean.rowCount!==1||['thread','messages','participants','events','conversation_state'].some(key=>clean.rows[0][key]!==true))throw fail();
    await client.query('commit');
  } catch {await client.query('rollback').catch(()=>{});throw Error('notification_reply_cleanup_unresolved');}
  const current=await context(journal);current.record.state.threadRemoved=true;
  await journal.checkpoint(current.record.state);
  return {removed:true,...current.record.state.replyCleanupAudit};
}
