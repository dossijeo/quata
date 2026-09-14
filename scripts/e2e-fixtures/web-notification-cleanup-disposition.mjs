import {assertWebNotificationMessageInput} from './web-notification-message-custody.mjs';
const uuid=/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
const hash=/^[0-9a-f]{64}$/;
const fail=()=>Error('web_notification_cleanup_disposition_unverified');

function baseline(record,peer,dispatcherFingerprint) {
  const plan=record.state?.threadPlan,target=record.state?.threadReceipt;
  if(!hash.test(dispatcherFingerprint)||record.state?.webRuntimeFreeze?.dispatcherFingerprint!==dispatcherFingerprint||
    ![record.runId,record.profileId,record.authUserId,peer.profileId,peer.authUserId].every(id=>typeof id==='string'&&uuid.test(id))||
    peer.runId!==record.runId||record.profileId===peer.profileId||record.authUserId===peer.authUserId||
    record.state.profileCreated!==true||peer.state?.profileCreated!==true||peer.state.sessions?.length!==0||
    plan?.runId!==record.runId||plan.ownerId!==record.profileId||plan.peerId!==peer.profileId||
    plan.uniqueKey!==`quata-deep-link-${record.runId}`||!target||!/^\d+$/.test(target.threadId))throw fail();
  return {runId:record.runId,ownerId:record.profileId,ownerAuthId:record.authUserId,
    peerId:peer.profileId,peerAuthId:peer.authUserId,threadId:target.threadId,dispatcherFingerprint,preparedBeforeSend:true};
}

// Caller holds a transaction through this audit and any following DELETE.
// Exclusive fixture credentials/producer custody are maintained by the runner;
// the peer never receives a browser, login ticket or token-producing operation.
async function audit(client,record,proof,replyId=null,allowAbsentThread=false) {
  const owned=await client.query(`select p.id,p.auth_user_id from public.community_profiles p join auth.users u on u.id=p.auth_user_id
    where p.id=any($1::uuid[]) and u.raw_app_meta_data->'quata_e2e'->>'unit'='FLOW-DEEP-LINKS'
      and u.raw_app_meta_data->'quata_e2e'->>'run_id'=$2 for update of p,u`,[[proof.ownerId,proof.peerId],proof.runId]);
  if(owned.rowCount!==2||!owned.rows.some(row=>row.id===proof.ownerId&&row.auth_user_id===proof.ownerAuthId)||
    !owned.rows.some(row=>row.id===proof.peerId&&row.auth_user_id===proof.peerAuthId))throw fail();
  const thread=await client.query(`select id::text from public.chat_threads where id=$1::bigint
    and type='group' and created_by_profile_id=$2::uuid and unique_key=$3 for update`,
    [proof.threadId,proof.ownerId,record.state.threadPlan.uniqueKey]);
  if(thread.rowCount!==1&&!(allowAbsentThread&&thread.rowCount===0))throw fail();
  const participants=await client.query('select profile_id,left_at from public.chat_participants where thread_id=$1::bigint for update',[proof.threadId]);
  if(thread.rowCount===0) {if(participants.rowCount!==0)throw fail();}
  else if(participants.rowCount!==2||participants.rows.some(row=>row.left_at!==null)||
    JSON.stringify(participants.rows.map(row=>row.profile_id).sort())!==JSON.stringify([proof.ownerId,proof.peerId].sort()))throw fail();
  const result=await client.query(`select
    not exists(select 1 from auth.sessions where user_id=$1::uuid) as auth_sessions,
    not exists(select 1 from public.web_client_sessions where auth_user_id=$1::uuid or profile_id=$2::uuid) as web_sessions,
    not exists(select 1 from public.push_tokens where auth_user_id=$1::uuid or user_id=$2::uuid) as native_tokens,
    not exists(select 1 from public.web_push_subscriptions where auth_user_id=$1::uuid or profile_id=$2::uuid) as web_subscriptions,
    not exists(select 1 from public.push_delivery_log where message_id in
      (select id from public.chat_messages where thread_id=$3::bigint) or message_id=any($5::bigint[])) as native_logs,
    not exists(select 1 from public.web_push_delivery_log where message_id=$4::bigint) as reply_web_logs`,
    [proof.peerAuthId,proof.peerId,proof.threadId,replyId,[record.state.threadReceipt.messageId,...(replyId?[replyId]:[])]]);
  if(result.rowCount!==1||['auth_sessions','web_sessions','native_tokens','web_subscriptions','native_logs','reply_web_logs']
    .some(key=>result.rows[0][key]!==true))throw fail();
  return {threadPresent:thread.rowCount===1};
}

export async function prepareWebReplyDestinationInvariant({client,journal,peerJournal,dispatcherFingerprint}) {
  try {
    const record=await journal.read(),peer=await peerJournal.read(),proof=baseline(record,peer,dispatcherFingerprint);
    if(record.state.webNotificationMessage!==undefined||record.state.webReplyDestinationInvariant!==undefined)throw fail();
    await client.query('begin');
    try {
      await client.query("set local lock_timeout='5s'");
      await audit(client,record,proof);
      record.state.webReplyDestinationInvariant=proof;await journal.checkpoint(record.state);
      await client.query('commit');
    } catch {await client.query('rollback').catch(()=>{});throw fail();}
    return {preparedBeforeSend:true};
  } catch {throw fail();}
}

// Explicit alternative for Web only. It proves absence of mutable destinations,
// NOT termination of the uncorrelated automatic Reply trigger. The fingerprint
// argument is freshly verified against the remote dispatcher by the coordinator.
// Must run inside EACH subscription/thread cleanup transaction before deletion.
export async function assertWebReplyCleanupDisposition({client,journal,peerJournal,dispatcherFingerprint,disposition,phase='subscription'}) {
  try {
    const record=await journal.read(),peer=await peerJournal.read(),expected=baseline(record,peer,dispatcherFingerprint);
    if(JSON.stringify(record.state.webReplyDestinationInvariant)!==JSON.stringify(expected)||
      record.state.webBrowserClosure?.runId!==record.runId||record.state.webBrowserClosure.transportSettled!==true||
      record.state.webNotificationSeedPush?.settledWithoutDestinations!==true||record.state.webNotificationDispatch?.settled!==true)throw fail();
    const attempt=record.state.webNotificationMessage,receipt=attempt?.backendReceipt;
    if(attempt?.capturedBeforeForward!==true||!receipt||!/^\d+$/.test(receipt.messageId))throw fail();
    assertWebNotificationMessageInput(attempt.input,{runId:record.runId,profileId:record.profileId,threadId:expected.threadId});
    const wanted={kind:'reply-trigger-no-mutable-destinations',runId:record.runId,ownerId:record.profileId,
      peerId:peer.profileId,threadId:expected.threadId,replyMessageId:receipt.messageId,dispatcherFingerprint,
      browserTransportSettled:true,seedDispatcherSettled:true,explicitDispatcherSettled:true,
      replyTrigger:{requestTerminal:null,mutationImpossible:true}};
    if(JSON.stringify(disposition)!==JSON.stringify(wanted))throw fail();
    if(!['subscription','thread'].includes(phase))throw fail();
    const allowAbsentThread=phase==='thread'&&record.state.webNotificationSubscription?.removed===true&&
      JSON.stringify(record.state.webThreadCleanupDisposition)===JSON.stringify(wanted)&&
      Number.isInteger(record.state.replyCleanupAudit?.replyCount)&&record.state.replyCleanupAudit.replyCount>=0;
    const audited=await audit(client,record,expected,receipt.messageId,allowAbsentThread);
    if(!audited.threadPresent) {
      const absent=await client.query(`select
        not exists(select 1 from public.chat_messages where thread_id=$1::bigint or id=any($2::bigint[])) as messages,
        not exists(select 1 from public.chat_events where thread_id=$1::bigint) as events,
        not exists(select 1 from public.conversation_user_state where conversation_id=$1::bigint) as conversation_state,
        not exists(select 1 from public.web_push_delivery_log where message_id=any($2::bigint[])) as web_logs`,
        [expected.threadId,[record.state.threadReceipt.messageId,receipt.messageId]]);
      if(absent.rowCount!==1||['messages','events','conversation_state','web_logs'].some(key=>absent.rows[0][key]!==true))throw fail();
      return wanted;
    }
    const message=await client.query(`select id::text from public.chat_messages where id=$1::bigint and thread_id=$2::bigint
      and sender_profile_id=$3::uuid and body=$4 and client_message_id=$5
      and reply_to_message_id is null and forwarded_from_message_id is null for update`,
      [receipt.messageId,expected.threadId,record.profileId,attempt.input.p_message,attempt.input.p_client_message_id]);
    if(message.rowCount!==1)throw fail();
    return wanted;
  } catch {throw fail();}
}
