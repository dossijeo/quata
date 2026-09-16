const fail=()=>Error('notification_reply_destinations_unverified');

// Caller holds a transaction through this audit and any following DELETE.
// Exclusive fixture credentials/producer custody are maintained by the runner;
// the peer never receives a browser, login ticket or token-producing operation.
export async function auditReplyDestinations(client,record,proof,replyId=null,allowAbsentThread=false) {
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

