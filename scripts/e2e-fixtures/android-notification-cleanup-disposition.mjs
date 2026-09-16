import {auditReplyDestinations} from './notification-reply-destination-audit.mjs';
import {androidDeepLinkCustodySettled} from './chat-deep-link-ios-custody.mjs';
const uuid=value=>typeof value==='string'&&/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(value);
const positive=value=>typeof value==='string'&&/^[1-9][0-9]*$/.test(value)&&BigInt(value)<=9223372036854775807n;
const fail=()=>Error('android_notification_cleanup_disposition_unverified');
const same=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
function baseline(record,peer,dispatcherFingerprint) {
  const plan=record.state?.threadPlan,target=record.state?.threadReceipt;
  if(!/^[0-9a-f]{64}$/.test(dispatcherFingerprint)||record.state?.androidRuntimeFreeze?.dispatcherFingerprint!==dispatcherFingerprint||
    ![record.runId,record.profileId,record.authUserId,peer.profileId,peer.authUserId].every(uuid)||
    peer.runId!==record.runId||record.profileId===peer.profileId||record.authUserId===peer.authUserId||
    record.state.profileCreated!==true||peer.state?.profileCreated!==true||peer.state.sessions?.length!==0||
    plan?.runId!==record.runId||plan.ownerId!==record.profileId||plan.peerId!==peer.profileId||
    plan.uniqueKey!==`quata-deep-link-${record.runId}`||!positive(target?.threadId)||!positive(target?.messageId)||
    record.state.webNotificationSeedPush?.settledWithoutDestinations!==true)throw fail();
  return {runId:record.runId,ownerId:record.profileId,ownerAuthId:record.authUserId,peerId:peer.profileId,
    peerAuthId:peer.authUserId,threadId:target.threadId,dispatcherFingerprint,preparedBeforeSend:true};
}

// The peer remains exclusively owned and never receives a login/device/session.
// The caller verifies the deployed dispatcher fingerprint and its sender exclusion.
export async function prepareAndroidReplyDestinationInvariant({client,journal,peerJournal,dispatcherFingerprint}) {
  try {
    const record=await journal.read(),peer=await peerJournal.read(),proof=baseline(record,peer,dispatcherFingerprint);
    if(record.state.notificationReply!==undefined||record.state.webNotificationMessage!==undefined||
      record.state.androidReplyDestinationInvariant!==undefined)throw fail();
    await client.query('begin');
    try {
      await client.query("set local lock_timeout='5s'");
      await auditReplyDestinations(client,record,proof);
      record.state.androidReplyDestinationInvariant=proof;await journal.checkpoint(record.state);
      await client.query('commit');
    } catch {await client.query('rollback').catch(()=>{});throw fail();}
    return {preparedBeforeSend:true};
  } catch {throw fail();}
}

// Must run inside the cleanup transaction before a DELETE. Unknown trigger
// termination remains null; this proves only that it cannot mutate destinations.
export async function assertAndroidReplyCleanupDisposition({client,journal,peerJournal,dispatcherFingerprint,disposition}) {
  try {
    const record=await journal.read(),peer=await peerJournal.read(),proof=baseline(record,peer,dispatcherFingerprint);
    const closure=record.state.androidReplyClosure,attempt=record.state.notificationReply,receipt=attempt?.backendReceipt;
    if(!same(record.state.androidReplyDestinationInvariant,proof)||closure?.runId!==record.runId||
      closure.processClosed!==true||closure.notificationRemoved!==true||closure.transportSettled!==true||
      record.state.webNotificationMessage!==undefined||attempt?.started!==true||!uuid(attempt.input?.stepId)||
      attempt.input.runId!==record.runId||attempt.input.profileId!==record.profileId||attempt.input.threadId!==proof.threadId||
      !positive(receipt?.messageId)||receipt.threadId!==proof.threadId||receipt.senderProfileId!==record.profileId||
      receipt.replyMarker!==`qadata-reply-text-${attempt.input.stepId}`||receipt.count!==1||
      !/^notification-reply-[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(receipt.clientMessageId??''))throw fail();
    const sessions=record.state.sessions;
    if(!Array.isArray(sessions)||sessions.length!==1||!sessions[0].androidSession||sessions[0].iosSession!==undefined||
      !androidDeepLinkCustodySettled(sessions[0])||['runId','profileId','authUserId'].some(key=>sessions[0][key]!==record[key]))throw fail();
    const expected={kind:'android-reply-trigger-no-mutable-destinations',runId:record.runId,ownerId:record.profileId,
      peerId:peer.profileId,threadId:proof.threadId,replyMessageId:receipt.messageId,dispatcherFingerprint,
      nativeProcessClosed:true,sessionCustodySettled:true,seedDispatcherSettled:true,
      replyTrigger:{requestTerminal:null,mutationImpossible:true}};
    if(!same(disposition,expected))throw fail();
    // A prior audit only permits recovery of a committed delete with a lost receipt.
    const allowAbsentThread=same(record.state.androidThreadCleanupDisposition,expected)&&
      Number.isInteger(record.state.replyCleanupAudit?.replyCount)&&record.state.replyCleanupAudit.replyCount>=0;
    const audited=await auditReplyDestinations(client,record,proof,receipt.messageId,allowAbsentThread);
    if(!audited.threadPresent) {
      const absent=await client.query(`select
        not exists(select 1 from public.chat_messages where thread_id=$1::bigint or id=any($2::bigint[])) as messages,
        not exists(select 1 from public.chat_events where thread_id=$1::bigint) as events,
        not exists(select 1 from public.conversation_user_state where conversation_id=$1::bigint) as conversation_state,
        not exists(select 1 from public.web_push_delivery_log where message_id=any($2::bigint[])) as web_logs`,
        [proof.threadId,[record.state.threadReceipt.messageId,receipt.messageId]]);
      if(absent.rowCount!==1||['messages','events','conversation_state','web_logs'].some(key=>absent.rows[0][key]!==true))throw fail();
      return expected;
    }
    const message=await client.query(`select id::text from public.chat_messages where id=$1::bigint and thread_id=$2::bigint
      and sender_profile_id=$3::uuid and body=$4 and client_message_id=$5
      and reply_to_message_id is null and forwarded_from_message_id is null for update`,
      [receipt.messageId,proof.threadId,record.profileId,receipt.replyMarker,receipt.clientMessageId]);
    if(message.rowCount!==1)throw fail();
    return expected;
  } catch {throw fail();}
}
