const id=value=>typeof value==="string"&&/^[1-9][0-9]*$/.test(value)&&Number.isSafeInteger(Number(value));

// Read-only verification of the isolated fixture, before and after browser use.
export async function verifyMissingDeepLinkMessage({client,target,plan}) {
  if(!id(target.threadId)||!id(target.messageId)||!id(target.visibleMessageId)||target.messageId===target.visibleMessageId)
    throw Error("deep_link_missing_target_invalid");
  const result=await client.query(`select
    exists(select 1 from public.chat_threads where id=$1::bigint and unique_key=$4 and created_by_profile_id=$5::uuid) as owned,
    not exists(select 1 from public.chat_messages where id=$2::bigint) as absent,
    (select count(*)::text from public.chat_messages where thread_id=$1::bigint) as count,
    exists(select 1 from public.chat_messages where id=$3::bigint and thread_id=$1::bigint and body=$6 and client_message_id=$7) as visible`,
    [target.threadId,target.messageId,target.visibleMessageId,plan.uniqueKey,plan.ownerId,plan.body,plan.messageKey]);
  const row=result.rows?.[0];
  if(row?.owned!==true||row.absent!==true||row.count!=="1"||row.visible!==true)throw Error("deep_link_missing_fixture_changed");
  return true;
}

// Classifies actual product RPC responses; never replaces transport or UI state.
// With exactly one independently verified message, a full initial page shorter
// than its requested limit proves the RPC exhausted this fixture's history.
export function createMissingMessageReadObserver({target,profileId}) {
  if(!id(target.threadId)||!id(target.messageId)||!id(target.visibleMessageId)||target.messageId===target.visibleMessageId||!profileId)
    throw Error("deep_link_missing_target_invalid");
  let responses=0,exhausted=false,failed=false;
  return {
    fail(){failed=true;},
    observe({request,status,body}) {
      responses++;
      try {
        if(status!==200||request.p_actor_profile_id!==profileId||String(request.p_thread_id)!==target.threadId||
            !Number.isInteger(request.p_limit)||request.p_limit<=1||request.p_limit>500||
            !Array.isArray(request.p_known_message_ids)||request.p_known_message_ids.some(value=>String(value)!==target.visibleMessageId)||
            !body||String(body.thread?.id)!==target.threadId||!Array.isArray(body.messages))throw Error();
        if(body.messages.some(message=>String(message.id)!==target.visibleMessageId||String(message.thread_id)!==target.threadId)||
            body.messages.length>1)throw Error();
        if(request.p_known_message_ids.length===0) {
          if(body.messages.length!==1)throw Error();
          exhausted=true;
        }
      } catch {failed=true;}
    },
    passed(){return exhausted&&!failed;},
    diagnostics(){return {responses,historyExhausted:exhausted,failed};},
  };
}
