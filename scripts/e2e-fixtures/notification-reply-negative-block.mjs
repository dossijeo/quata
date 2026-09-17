import {assertNoExternalDeepLinkReferences} from './chat-deep-link-cleanup.mjs';
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const id=x=>typeof x==='string'&&/^[1-9][0-9]*$/.test(x)&&BigInt(x)<=9223372036854775807n;
const fail=()=>Error('notification_reply_negative_block_unresolved');
const same=(a,b)=>JSON.stringify(a)===JSON.stringify(b);

async function context(journal,peerJournal) {
  const record=await journal.read(),peer=await peerJournal.read();
  const plan=record.state?.threadPlan,target=record.state?.threadReceipt;
  if(![record.runId,record.profileId,record.authUserId,peer.profileId,peer.authUserId].every(x=>uuid.test(x))||
    peer.runId!==record.runId||plan?.runId!==record.runId||plan.ownerId!==record.profileId||plan.peerId!==peer.profileId||
    plan.ownerId===plan.peerId||record.authUserId===peer.authUserId||
    plan.uniqueKey!==`quata-deep-link-${record.runId}`||plan.messageKey!==`quata-deep-link-message-${record.runId}`||
    plan.body!==`Deep link ${record.runId}`||record.state.profileCreated!==true||peer.state?.profileCreated!==true||
    record.state.threadStarted!==true||record.state.threadRemoved===true||!id(target?.threadId)||!id(target?.messageId))throw fail();
  return {record,peer,plan,target};
}

async function baseline(client,{record,peer,plan,target}) {
  const owned=await client.query(`select p.id,p.auth_user_id from public.community_profiles p join auth.users u on u.id=p.auth_user_id
    where p.id=any($1::uuid[]) and p.account_status='active'
    and u.raw_app_meta_data->'quata_e2e'->>'unit'='FLOW-DEEP-LINKS'
    and u.raw_app_meta_data->'quata_e2e'->>'run_id'=$2 for update of p,u`,[[plan.ownerId,plan.peerId],record.runId]);
  if(owned.rowCount!==2||owned.rows.filter(x=>x.id===record.profileId&&x.auth_user_id===record.authUserId).length!==1||
    owned.rows.filter(x=>x.id===peer.profileId&&x.auth_user_id===peer.authUserId).length!==1)throw fail();
  const thread=await client.query(`select id::text,type,created_by_profile_id,unique_key from public.chat_threads
    where id=$1::bigint or unique_key=$2 for update`,[target.threadId,plan.uniqueKey]);
  const t=thread.rows?.[0];
  if(thread.rowCount!==1||t.id!==target.threadId||t.type!=='group'||t.created_by_profile_id!==plan.ownerId||t.unique_key!==plan.uniqueKey)throw fail();
  const participants=await client.query('select profile_id,left_at from public.chat_participants where thread_id=$1::bigint for update',[target.threadId]);
  if(participants.rowCount!==2||participants.rows.some(x=>x.left_at!==null)||
    !same(participants.rows.map(x=>x.profile_id).sort(),[plan.ownerId,plan.peerId].sort()))throw fail();
  const messages=await client.query(`select id::text,sender_profile_id,body,client_message_id,reply_to_message_id,forwarded_from_message_id
    from public.chat_messages where thread_id=$1::bigint for update`,[target.threadId]);
  const m=messages.rows?.[0];
  if(messages.rowCount!==1||m.id!==target.messageId||m.sender_profile_id!==plan.peerId||m.body!==plan.body||
    m.client_message_id!==plan.messageKey||m.reply_to_message_id!==null||m.forwarded_from_message_id!==null)throw fail();
  const attachments=await client.query('select count(*)::text as count from public.chat_attachments where thread_id=$1::bigint or message_id=$2::bigint',[target.threadId,target.messageId]);
  if(attachments.rows?.[0]?.count!=='0')throw fail();
  await assertNoExternalDeepLinkReferences({client,threadId:target.threadId});
}

const tuple=c=>({runId:c.record.runId,threadId:c.target.threadId,blockerProfileId:c.plan.peerId,blockedProfileId:c.plan.ownerId});
const params=t=>[t.threadId,t.blockerProfileId,t.blockedProfileId];
async function blocks(client,t) {
  // Also reject a global block: it would make this differential ambiguous.
  return client.query(`select id::text,thread_id::text from public.chat_profile_blocks
    where (thread_id=$1::bigint or thread_id is null) and blocker_profile_id=$2::uuid and blocked_profile_id=$3::uuid for update`,params(t));
}

// Preparation only, never invokes the notification action or Chat RPC.
export async function installNotificationReplyNegativeBlock({client,journal,peerJournal}) {
  try {
    const c=await context(journal,peerJournal),input=tuple(c);
    if(c.record.state.notificationReplyNegativeBlock!==undefined||c.record.state.notificationReply!==undefined)throw fail();
    c.record.state.notificationReplyNegativeBlock={input,started:true};
    await journal.checkpoint(c.record.state); // durable before any possible INSERT
    await client.query('begin');
    try {
      await client.query("set local lock_timeout='5s'");
      await baseline(client,c);
      if((await blocks(client,input)).rowCount!==0)throw fail();
      const inserted=await client.query(`insert into public.chat_profile_blocks(thread_id,blocker_profile_id,blocked_profile_id)
        values ($1::bigint,$2::uuid,$3::uuid) returning id::text`,params(input));
      if(inserted.rowCount!==1||!id(inserted.rows?.[0]?.id))throw fail();
      const blockId=inserted.rows[0].id,readback=await blocks(client,input);
      if(readback.rowCount!==1||readback.rows[0].id!==blockId||readback.rows[0].thread_id!==input.threadId)throw fail();
      c.record.state.notificationReplyNegativeBlock.blockId=blockId;
      await journal.checkpoint(c.record.state); // keeps exact identity even if COMMIT acknowledgement is lost
      await client.query('commit');
    } catch {await client.query('rollback').catch(()=>{});throw fail();}
    const current=await context(journal,peerJournal),b=current.record.state.notificationReplyNegativeBlock;
    if(!same(b,c.record.state.notificationReplyNegativeBlock))throw fail();
    b.verified=true;await journal.checkpoint(current.record.state);
    return {installed:true};
  } catch {throw fail();}
}

// A settled producer/transport is required even for an uncertain installation.
// Never deletes journals or fixtures: caller reconciles those after this receipt.
export async function removeNotificationReplyNegativeBlock({client,journal,peerJournal,operationsSettled}) {
  try {
    if(typeof operationsSettled!=='function'||await operationsSettled()!==true)throw fail();
    const c=await context(journal,peerJournal),input=tuple(c),b=c.record.state.notificationReplyNegativeBlock;
    if(b?.started!==true||!same(b.input,input)||!id(b.blockId)||b.removed===true)throw fail();
    b.removalStarted=true;await journal.checkpoint(c.record.state);
    await client.query('begin');
    try {
      await client.query("set local lock_timeout='5s'");
      await baseline(client,c); // seed only, zero owner messages, including late arrivals
      const found=await blocks(client,input);
      if(found.rowCount>1||found.rowCount===1&&(found.rows[0].id!==b.blockId||found.rows[0].thread_id!==input.threadId))throw fail();
      if(found.rowCount===1) {
        const removed=await client.query(`delete from public.chat_profile_blocks where id=$4::bigint and thread_id=$1::bigint
          and blocker_profile_id=$2::uuid and blocked_profile_id=$3::uuid returning id::text`,[...params(input),b.blockId]);
        if(removed.rowCount!==1||removed.rows[0].id!==b.blockId)throw fail();
      }
      if((await blocks(client,input)).rowCount!==0)throw fail();
      await client.query('commit');
    } catch {await client.query('rollback').catch(()=>{});throw fail();}
    const current=await context(journal,peerJournal);
    if(!same(current.record.state.notificationReplyNegativeBlock,b))throw fail();
    current.record.state.notificationReplyNegativeBlock.removed=true;
    await journal.checkpoint(current.record.state);
    return {removed:true,baselineUnchanged:true};
  } catch {throw fail();}
}
