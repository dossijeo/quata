const id=value=>typeof value==="string"&&/^[1-9][0-9]*$/.test(value)&&Number.isSafeInteger(Number(value));
export const missingThreadFunctions=new Set(["quata_chat_get_thread","quata_chat_mark_thread_read","quata_chat_cleanup_empty_private_thread"]);

export async function verifyMissingDeepLinkThread({client,target,plan}) {
  if(!id(target.threadId)||!id(target.ownedThreadId)||target.threadId===target.ownedThreadId)throw Error("deep_link_missing_thread_invalid");
  const result=await client.query(`select
    exists(select 1 from public.chat_threads where id=$2::bigint and unique_key=$3 and created_by_profile_id=$4::uuid) as owned,
    not exists(select 1 from public.chat_threads where id=$1::bigint) as absent,
    not exists(select 1 from public.chat_messages where thread_id=$1::bigint) as messages_absent,
    not exists(select 1 from public.chat_participants where thread_id=$1::bigint) as participants_absent,
    not exists(select 1 from public.conversation_user_state where conversation_id=$1::bigint) as state_absent`,
    [target.threadId,target.ownedThreadId,plan.uniqueKey,plan.ownerId]);
  if(["owned","absent","messages_absent","participants_absent","state_absent"].some(key=>result.rows?.[0]?.[key]!==true))
    throw Error("deep_link_missing_thread_changed");
  return true;
}

// Only definitive, fully read responses from the audited no-participant guards
// may be treated as settled. Unknown errors and transport loss remain uncertain.
export function createMissingThreadReadObserver({target,profileId}) {
  if(!id(target.threadId)||!profileId)throw Error("deep_link_missing_thread_invalid");
  let reads=0,failed=false;const responses={};
  return {
    targets(request){return String(request?.p_thread_id)===target.threadId;},
    fail(){failed=true;},
    observe({functionName,request,status,body}) {
      try {
        if(!missingThreadFunctions.has(functionName)||request?.p_actor_profile_id!==profileId||String(request.p_thread_id)!==target.threadId)throw Error();
        if(functionName==="quata_chat_cleanup_empty_private_thread") {
          if(status!==200||body?.deleted!==false||String(body.thread_id)!==target.threadId||body.reason!=="not_participant")throw Error();
        } else {
          if(status!==403||body?.code!=="42501"||body.message!=="profile is not a participant of this thread")throw Error();
          if(functionName==="quata_chat_get_thread")reads++;
        }
        responses[functionName]=(responses[functionName]??0)+1;return true;
      } catch {failed=true;return false;}
    },
    passed(){return reads>0&&!failed;},
    diagnostics(){return {rejectedReads:reads,responses:{...responses},failed};},
  };
}
