// Write-ahead custody only. This adapter neither sends nor retries a message.
// The coordinator must establish fixture/session ownership and settle transport
// before reconciliation. It must serialize all writes to the actor's journal.
const uuid=/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
const positive=value=>typeof value==='string'&&/^[1-9][0-9]*$/.test(value)&&BigInt(value)<=9223372036854775807n;
const fail=()=>Error('web_notification_message_custody_unverified');
// The trial coordinator owns the cross-process run lock. This also excludes
// concurrent adapter instances inside that coordinator, even with two handles.
const pending=new Set();

export function assertWebNotificationMessageInput(payload,{runId,profileId,threadId}) {
  if(!payload||Object.keys(payload).sort().join(',')!==[
    'p_actor_profile_id','p_client_message_id','p_file_ids','p_message',
    'p_reply_to_message_id','p_thread_id'].join(',')||
    payload.p_actor_profile_id!==profileId||
    !(typeof payload.p_thread_id==='number'&&Number.isSafeInteger(payload.p_thread_id)&&String(payload.p_thread_id)===threadId)||
    payload.p_message!==`quata-web-reply-${runId}`||!Array.isArray(payload.p_file_ids)||payload.p_file_ids.length!==0||
    payload.p_reply_to_message_id!==null||typeof payload.p_client_message_id!=='string'||
    !/^[1-9][0-9]{0,15}--?[0-9a-f]{1,16}$/.test(payload.p_client_message_id))throw fail();
}

export function createWebNotificationMessageCustody({journal,runId,profileId,threadId,marker}) {
  if(![runId,profileId].every(value=>typeof value==='string'&&uuid.test(value))||
    !positive(threadId)||marker!==`quata-web-reply-${runId}`||
    typeof journal?.read!=='function'||typeof journal?.checkpoint!=='function')throw fail();
  let attempted=false;
  return Object.freeze({
    async capture(payload) {
      // A second request, including a concurrent one, is never authorized here.
      if(attempted)throw fail();
      attempted=true;
      const key=`${runId}/${profileId}/${threadId}`;
      if(pending.has(key))throw fail();
      pending.add(key);
      try {
      assertWebNotificationMessageInput(payload,{runId,profileId,threadId});
      const input=structuredClone(payload);
      const record=await journal.read();
      const plan=record.state?.threadPlan,target=record.state?.threadReceipt;
      if(record.runId!==runId||record.profileId!==profileId||
        record.state?.profileCreated!==true||record.state.threadStarted!==true||record.state.threadRemoved===true||
        plan?.runId!==runId||plan.ownerId!==profileId||target?.threadId!==threadId||
        record.state.webNotificationMessage!==undefined)throw fail();
      record.state.webNotificationMessage={input,capturedBeforeForward:true};
      await journal.checkpoint(record.state);
      // No credentials or message content is returned for reporting.
      return {captured:true};
      } catch {throw fail();}
      finally {pending.delete(key);}
    },
  });
}
