const uuid=/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
const fail=()=>Error('web_notification_dispatch_unverified');
const pending=new Set();

// One explicit delivery of the already-owned seed. This does not send a chat
// message. The transport supplies the private dispatcher secret through memory.
export async function dispatchWebNotificationSeed({client,journal,execute}) {
  let key,acquired=false;
  try {
    let record=await journal.read();
    if(typeof execute!=='function'||![record.runId,record.profileId,record.authUserId].every(id=>typeof id==='string'&&uuid.test(id)))throw fail();
    key=`${record.runId}/${record.profileId}`;
    if(pending.has(key))throw fail();
    pending.add(key);acquired=true;
    record=await journal.read();
    if(key!==`${record.runId}/${record.profileId}`)throw fail();
    const state=record.state,plan=state?.threadPlan,target=state?.threadReceipt,subscription=state?.webNotificationSubscription;
    if(state?.profileCreated!==true||state.threadStarted!==true||state.threadRemoved===true||
      state.webNotificationDispatch!==undefined||state.webNotificationSeedPush?.settledWithoutDestinations!==true||
      state.webNotificationSeedPush.threadId!==target?.threadId||state.webNotificationSeedPush.messageId!==target?.messageId||
      plan?.runId!==record.runId||plan.ownerId!==record.profileId||!uuid.test(plan.peerId)||plan.peerId===record.profileId||
      plan.uniqueKey!==`quata-deep-link-${record.runId}`||plan.messageKey!==`quata-deep-link-message-${record.runId}`||plan.body!==`Deep link ${record.runId}`||
      subscription?.absentBeforeForward!==true||!uuid.test(subscription.receipt?.subscriptionId)||subscription.removed===true||
      !/^[1-9][0-9]*$/.test(target.messageId)||!Number.isSafeInteger(Number(target.messageId)))throw fail();
    const owned=await client.query(`select m.id::text from public.chat_messages m
      join public.chat_threads t on t.id=m.thread_id
      where m.id=$1::bigint and m.thread_id=$2::bigint and m.sender_profile_id=$3::uuid
        and m.body=$4 and m.client_message_id=$5 and m.deleted_at is null
        and t.type='group' and t.created_by_profile_id=$6::uuid and t.unique_key=$7
        and (select count(*) from public.chat_participants where thread_id=t.id)=2
        and (select count(*) from public.chat_participants where thread_id=t.id and profile_id=any($8::uuid[]) and left_at is null)=2`,
      [target.messageId,target.threadId,plan.peerId,plan.body,plan.messageKey,record.profileId,plan.uniqueKey,[plan.ownerId,plan.peerId]]);
    if(owned.rowCount!==1)throw fail();
    const destinations=await client.query(`select
      not exists(select 1 from public.push_tokens where user_id=any($1::uuid[]) or auth_user_id in
        (select auth_user_id from public.community_profiles where id=any($1::uuid[]))) as native_absent,
      (select count(*)::text from public.web_push_subscriptions where profile_id=any($1::uuid[]) or auth_user_id in
        (select auth_user_id from public.community_profiles where id=any($1::uuid[]))) as web_count,
      exists(select 1 from public.web_push_subscriptions where id=$2::uuid and profile_id=$3::uuid and auth_user_id=$4::uuid
        and web_session_id=$5::uuid and endpoint=$6 and p256dh=$7 and auth_secret=$8 and disabled_at is null) as exact_subscription`,
      [[plan.ownerId,plan.peerId],subscription.receipt.subscriptionId,record.profileId,record.authUserId,
        subscription.webSessionId,subscription.input.endpoint,subscription.input.p256dh,subscription.input.authSecret]);
    if(destinations.rowCount!==1||destinations.rows[0].native_absent!==true||destinations.rows[0].web_count!=='1'||destinations.rows[0].exact_subscription!==true)throw fail();
    const input={messageId:target.messageId};
    state.webNotificationDispatch={input,started:true,settled:false};await journal.checkpoint(state);
    const response=await execute(structuredClone(input));
    const current=await journal.read(),attempt=current.state?.webNotificationDispatch;
    if(current.runId!==record.runId||current.profileId!==record.profileId||attempt?.started!==true||attempt.settled!==false||attempt.input?.messageId!==input.messageId)throw fail();
    // Preserve a received response privately even if it cannot prove completion.
    attempt.privateResponse=response;await journal.checkpoint(current.state);
    const body=response?.body;
    if(response?.status!==200||body?.result!==true||body.recipients!==1||body.android_tokens!==0||
      body.web_subscriptions!==1||body.web_sent!==1||body.web_skipped!==0||body.sent!==1||
      (body.ios_tokens!==undefined&&body.ios_tokens!==0))throw fail();
    attempt.settled=true;await journal.checkpoint(current.state);
    return {completed:true,webSent:1};
  } catch {throw fail();}
  finally {if(acquired)pending.delete(key);}
}
