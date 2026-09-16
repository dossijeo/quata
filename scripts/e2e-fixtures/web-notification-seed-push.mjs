const positive=value=>typeof value==='string'&&/^[1-9][0-9]*$/.test(value)&&BigInt(value)<=9223372036854775807n;
const fail=()=>Error('web_notification_seed_push_unverified');

// Called inside the seed transaction, after its INSERT trigger and before COMMIT.
// Only the request ID is retained; queue headers contain a private dispatch secret.
export async function captureWebNotificationSeedPush({client,journal,plan,threadId,messageId}) {
  try {
    if(!positive(threadId)||!positive(messageId))throw fail();
    const record=await journal.read();
    if(record.runId!==plan.runId||record.profileId!==plan.ownerId||record.state.threadStarted!==true||
      record.state.webNotificationSeedPush!==undefined||
      Object.keys(plan).some(key=>record.state.threadPlan?.[key]!==plan[key]))throw fail();
    const result=await client.query(`select id::text from net.http_request_queue
      where method='POST' and url='https://yrrlankpwmhluexshxnw.supabase.co/functions/v1/quata-push-dispatch'
      and convert_from(body,'UTF8')::jsonb=jsonb_build_object('message_id',$1::bigint)`,[messageId]);
    if(result.rowCount!==1||!positive(result.rows[0]?.id))throw fail();
    record.state.webNotificationSeedPush={threadId,messageId,requestId:result.rows[0].id,capturedBeforeCommit:true};
    await journal.checkpoint(record.state);
    return {captured:true};
  } catch {throw fail();}
}

// A missing response is pending, not success. Timeout/error is not dispatcher
// settlement. No retry, queue deletion or secret-bearing response is performed.
export async function observeWebNotificationSeedPush({client,journal}) {
  try {
    const record=await journal.read(),entry=record.state?.webNotificationSeedPush,target=record.state?.threadReceipt;
    if(entry?.capturedBeforeCommit!==true||!positive(entry.requestId)||
      target?.threadId!==entry.threadId||target?.messageId!==entry.messageId)throw fail();
    const result=await client.query(`select status_code,timed_out,error_msg is not null as has_error,content
      from net._http_response where id=$1::bigint`,[entry.requestId]);
    if(result.rowCount===0)return {settled:false};
    if(result.rowCount!==1)throw fail();
    const row=result.rows[0];
    if(row.status_code!==200||row.timed_out===true||row.has_error!==false)throw fail();
    const body=JSON.parse(row.content);
    if(body.result!==true||body.recipients!==1||body.android_tokens!==0||body.web_subscriptions!==0||body.sent!==0||
      (body.ios_tokens!==undefined&&body.ios_tokens!==0))throw fail();
    // This receipt concerns the initial, unsubscribed seed only. A later explicit
    // real delivery has independent custody and must never reuse this result.
    entry.settledWithoutDestinations=true;await journal.checkpoint(record.state);
    return {settled:true,sent:0};
  } catch {throw fail();}
}
