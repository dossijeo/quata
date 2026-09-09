import {assertNoExternalDeepLinkReferences} from "./chat-deep-link-cleanup.mjs";
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
async function readPlan(journal,plan) {
  if (![plan.runId,plan.ownerId,plan.peerId].every(x=>uuid.test(x)) || plan.ownerId===plan.peerId ||
      plan.uniqueKey!==`quata-deep-link-${plan.runId}` || plan.messageKey!==`quata-deep-link-message-${plan.runId}` ||
      plan.body!==`Deep link ${plan.runId}`)throw Error("deep_link_thread_invalid_plan");
  const record=await journal.read();
  if(record.runId!==plan.runId || record.profileId!==plan.ownerId ||
      Object.keys(plan).some(key=>record.state.threadPlan?.[key]!==plan[key]))throw Error("deep_link_thread_journal_mismatch");
  return record;
}

// Synthetic backend fixture only: does not certify thread creation or sending UI.
// One transaction avoids ambiguous in-flight HTTP mutations while preparing data.
export async function seedDeepLinkThread({client,journal,plan}) {
  const record=await readPlan(journal,plan);
  if(record.state.threadStarted)throw Error("deep_link_thread_already_started");
  record.state.threadStarted=true;await journal.checkpoint(record.state);
  await client.query("begin");
  let threadId,messageId;
  try {
    const profiles=await client.query(`select p.id from public.community_profiles p join auth.users u on u.id=p.auth_user_id
      where p.id=any($1::uuid[]) and u.raw_app_meta_data->'quata_e2e'->>'unit'='FLOW-DEEP-LINKS'
      and u.raw_app_meta_data->'quata_e2e'->>'run_id'=$2 and p.account_status='active' for update of p,u`,
      [[plan.ownerId,plan.peerId],plan.runId]);
    if(profiles.rowCount!==2)throw Error("fixture_identity_mismatch");
    const push=await client.query(`select
      not exists(select 1 from public.push_tokens where user_id=any($1::uuid[]) or auth_user_id in
        (select auth_user_id from public.community_profiles where id=any($1::uuid[]))) as native_absent,
      not exists(select 1 from public.web_push_subscriptions where profile_id=any($1::uuid[]) or auth_user_id in
        (select auth_user_id from public.community_profiles where id=any($1::uuid[]))) as web_absent`,[[plan.ownerId,plan.peerId]]);
    if(push.rows?.[0]?.native_absent!==true || push.rows?.[0]?.web_absent!==true)throw Error("fixture_has_push_registration");
    const collision=await client.query("select id from public.chat_threads where unique_key=$1",[plan.uniqueKey]);
    if(collision.rowCount!==0)throw Error("thread_collision");
    const inserted=await client.query(`insert into public.chat_threads(type,subject,title,created_by_profile_id,unique_key,allow_invite)
      values ('group',$1,$1,$2::uuid,$3,false) returning id::text`,[plan.body,plan.ownerId,plan.uniqueKey]);
    threadId=inserted.rows[0].id;
    await client.query(`insert into public.chat_participants(thread_id,profile_id,role)
      values ($1::bigint,$2::uuid,'owner'),($1::bigint,$3::uuid,'member')`,[threadId,plan.ownerId,plan.peerId]);
    const message=await client.query(`insert into public.chat_messages(thread_id,sender_profile_id,body,client_message_id)
      values ($1::bigint,$2::uuid,$3,$4) returning id::text`,[threadId,plan.peerId,plan.body,plan.messageKey]);
    messageId=message.rows[0].id;
    await client.query("commit");
  } catch {await client.query("rollback").catch(()=>{});throw Error("deep_link_thread_seed_unresolved");}
  const saved=await readPlan(journal,plan);saved.state.threadReceipt={threadId,messageId};await journal.checkpoint(saved.state);
  return {threadId,messageId};
}

// Requires all UI and fixture operations settled. Never retries seed on uncertainty.
export async function removeDeepLinkThread({client,journal,plan,operationsSettled}) {
  const record=await readPlan(journal,plan);
  if(!record.state.threadStarted || typeof operationsSettled!=="function" || await operationsSettled()!==true) {
    throw Error("deep_link_thread_cleanup_not_ready");
  }
  await client.query("begin");
  try {
    await client.query("set local lock_timeout='5s'");
    const thread=await client.query("select id::text,type,created_by_profile_id from public.chat_threads where unique_key=$1 for update",[plan.uniqueKey]);
    if(thread.rowCount===0) {
      const residue=await client.query("select count(*)::text as count from public.chat_messages where client_message_id=$1",[plan.messageKey]);
      if(residue.rows?.[0]?.count!=="0")throw Error("message_residue_without_thread");
    } else {
      if(thread.rowCount!==1 || thread.rows[0].type!=="group" || thread.rows[0].created_by_profile_id!==plan.ownerId ||
          (record.state.threadReceipt && record.state.threadReceipt.threadId!==thread.rows[0].id))throw Error("thread_not_owned");
      const threadId=thread.rows[0].id;
      const participants=await client.query("select profile_id from public.chat_participants where thread_id=$1::bigint for update",[threadId]);
      if(participants.rowCount!==2 || JSON.stringify(participants.rows.map(x=>x.profile_id).sort())!==JSON.stringify([plan.ownerId,plan.peerId].sort()))throw Error("participants_changed");
      const messages=await client.query("select id::text,sender_profile_id,body,client_message_id from public.chat_messages where thread_id=$1::bigint for update",[threadId]);
      if(messages.rowCount!==1 || messages.rows[0].sender_profile_id!==plan.peerId || messages.rows[0].body!==plan.body ||
          messages.rows[0].client_message_id!==plan.messageKey ||
          (record.state.threadReceipt && record.state.threadReceipt.messageId!==messages.rows[0].id))throw Error("messages_changed");
      const attachments=await client.query("select count(*)::text as count from public.chat_attachments where thread_id=$1::bigint or message_id=$2::bigint",[threadId,messages.rows[0].id]);
      if(attachments.rows?.[0]?.count!=="0")throw Error("unexpected_attachments");
      await assertNoExternalDeepLinkReferences({client,threadId});
      await client.query("delete from public.chat_threads where id=$1::bigint and unique_key=$2",[threadId,plan.uniqueKey]);
      const clean=await client.query(`select
        not exists(select 1 from public.chat_threads where id=$1::bigint or unique_key=$2) as thread,
        not exists(select 1 from public.chat_messages where thread_id=$1::bigint or client_message_id=$3) as messages,
        not exists(select 1 from public.chat_participants where thread_id=$1::bigint) as participants,
        not exists(select 1 from public.chat_events where thread_id=$1::bigint) as events,
        not exists(select 1 from public.conversation_user_state where conversation_id=$1::bigint) as conversation_state`,[threadId,plan.uniqueKey,plan.messageKey]);
      if(["thread","messages","participants","events","conversation_state"].some(key=>clean.rows?.[0]?.[key]!==true))throw Error("thread_residue");
    }
    await client.query("commit");
  } catch {await client.query("rollback").catch(()=>{});throw Error("deep_link_thread_cleanup_unresolved");}
  const removed=await readPlan(journal,plan);removed.state.threadRemoved=true;await journal.checkpoint(removed.state);
  return {removed:true};
}
