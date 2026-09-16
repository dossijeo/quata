import {auditReplyDestinations as audit} from './notification-reply-destination-audit.mjs';
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
