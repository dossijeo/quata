import {mkdir,open,unlink} from 'node:fs/promises';
import path from 'node:path';
import {randomUUID,randomBytes,randomInt} from 'node:crypto';
import {createRecoveryJournal} from './e2e-fixtures/recovery-private-journal.mjs';
import {createDeepLinkProfile,retireDeepLinkProfile} from './e2e-fixtures/chat-deep-link-profile.mjs';
import {loginDeepLinkSession} from './e2e-fixtures/chat-deep-link-session.mjs';
import {seedDeepLinkThread,removeDeepLinkThread} from './e2e-fixtures/chat-deep-link-thread.mjs';
import {prepareIosDeepLinkSession} from './e2e-fixtures/chat-deep-link-ios-session.mjs';
import {runIosDeepLinkSessionStep,iosDeepLinkCustodySettled} from './e2e-fixtures/chat-deep-link-ios-custody.mjs';
import {submitNotificationReplyAttempt,observeNotificationReplyMessage,removeNotificationReplyThread} from './e2e-fixtures/notification-reply-attempt.mjs';
import {observeWebNotificationSeedPush} from './e2e-fixtures/web-notification-seed-push.mjs';
import {auditReplyDestinations} from './e2e-fixtures/notification-reply-destination-audit.mjs';

// Private Windows coordinator, using the reviewed disposable deep-link fixture
// ownership protocol. Native UI submission is the sole producer of the Reply;
// this runner never invokes a send RPC or fabricates a successful message.
export async function runIosNotificationReplyTrial({client,privateDirectory,backendUrl,publicKey,
  adminRequest,preflight,channel,transportSettled,fetchImpl=fetch}) {
  if(!path.isAbsolute(privateDirectory)||typeof preflight!=='function'||typeof adminRequest!=='function'||
    typeof transportSettled!=='function'||['sessionStep','submitNotificationReply','verifyNotificationReplyOutcome','close','settled','abort']
      .some(key=>typeof channel?.[key]!=='function'))throw Error('notification_reply_trial_configuration_invalid');
  await mkdir(privateDirectory,{recursive:true});
  const lockPath=path.join(privateDirectory,'flow-deep-links.lock'),lock=await open(lockPath,'wx',0o600);
  const runId=randomUUID(),actors=[],report={unit:'FLOW-NOTIFICATION-REPLY',platform:'ios',runId,
    status:'failed',phase:'preflight',cleanupComplete:false,appleDeliveryCertified:false};
  let freeze,plan,nativeInput,closed=false,loginUncertain=false;
  const settled=async()=>closed&&channel.settled()===true&&!loginUncertain&&await transportSettled()===true&&
    (await Promise.all(actors.map(async actor=>(await actor.journal.read()).state.sessions.every(entry=>iosDeepLinkCustodySettled(entry))))).every(Boolean);
  const checkpoint=async(actor,change)=>{const current=await actor.journal.read();change(current.state);await actor.journal.checkpoint(current.state);};
  const verifyFreeze=async phase=>{
    const value=await preflight({runId,phase});
    if(value?.passed!==true||value.senderExcluded!==true||!/^([0-9a-f]{64})$/.test(value.dispatcherFingerprint??'')||
      freeze&&freeze.dispatcherFingerprint!==value.dispatcherFingerprint)throw Error('notification_reply_preflight_failed');
    return {dispatcherFingerprint:value.dispatcherFingerprint};
  };
  const destinationProof=async()=>{
    const owner=await actors[0].journal.read(),peer=await actors[1].journal.read();
    if(peer.runId!==runId||peer.state.sessions.length!==0||peer.state.profileCreated!==true||
      owner.state.webNotificationSeedPush?.settledWithoutDestinations!==true)throw Error('notification_reply_destinations_unverified');
    return {runId,ownerId:owner.profileId,ownerAuthId:owner.authUserId,peerId:peer.profileId,
      peerAuthId:peer.authUserId,threadId:owner.state.threadReceipt.threadId,...freeze,preparedBeforeSend:true};
  };
  const auditDestinations=async(replyId=null)=>{
    const record=await actors[0].journal.read(),proof=await destinationProof();
    if(record.state.notificationReply!==undefined&&JSON.stringify(record.state.iosReplyDestinationInvariant)!==JSON.stringify(proof))
      throw Error('notification_reply_destinations_unverified');
    await auditReplyDestinations(client,record,proof,replyId);
    return proof;
  };
  try {
    await lock.writeFile(JSON.stringify({runId,pid:process.pid}));await lock.sync();
    freeze=await verifyFreeze('initial');
    for(let index=0;index<2;index++) {
      const authUserId=randomUUID(),record={runId,profileId:randomUUID(),authUserId,
        email:`deep-link-${authUserId}@example.invalid`,countryCode:'240',phone:`99${randomInt(100000000,1000000000)}`,
        password:randomBytes(24).toString('base64url'),state:{sessions:[],evidenceUnit:'FLOW-NOTIFICATION-REPLY'}};
      const journal=await createRecoveryJournal({directory:privateDirectory,record});
      const actor={record,journal};actors.push(actor);
      report.phase=`create_fixture_${index}`;
      await createDeepLinkProfile({client,journal,record,password:record.password,adminRequest});
    }
    const actor=actors[0],ticket={runId,profileId:actor.record.profileId,authUserId:actor.record.authUserId,
      purpose:'deep_link',clientInstanceId:randomUUID()};
    await checkpoint(actor,state=>{state.sessions.push(ticket);});
    report.phase='login_fixture';loginUncertain=true;
    let session;
    try {
      session=await loginDeepLinkSession({client,journal:actor.journal,record:actor.record,ticket,
        password:actor.record.password,backendUrl,publicKey,fetchImpl});
      loginUncertain=false;
    } catch(error) {
      const current=await actor.journal.read(),entry=current.state.sessions[0];
      loginUncertain=entry?.requestStarted===true&&!(entry.authSessionId&&entry.webSessionId)&&entry.noSession!==true;
      throw error;
    }
    plan={runId,ownerId:actor.record.profileId,peerId:actors[1].record.profileId,
      uniqueKey:`quata-deep-link-${runId}`,messageKey:`quata-deep-link-message-${runId}`,body:`Deep link ${runId}`};
    await checkpoint(actor,state=>{state.threadPlan=plan;});
    report.phase='seed_thread';
    const target=await seedDeepLinkThread({client,journal:actor.journal,plan,capturePushRequest:true});
    const seedDeadline=Date.now()+30000;
    while((await observeWebNotificationSeedPush({client,journal:actor.journal})).settled!==true) {
      if(Date.now()>=seedDeadline)throw Error('notification_reply_seed_push_pending');
      await new Promise(resolve=>setTimeout(resolve,500));
    }
    await verifyFreeze('before_install');
    report.phase='install_owned_session';
    nativeInput=await prepareIosDeepLinkSession({client,journal:actor.journal,record:actor.record,ticket,session,backendUrl,publicKey,fetchImpl});
    await runIosDeepLinkSessionStep({journal:actor.journal,input:nativeInput,execute:input=>channel.sessionStep(input)});
    await client.query('begin');
    let destination;
    try {destination=await auditDestinations();await client.query('commit');}
    catch(error){await client.query('rollback');throw error;}
    await checkpoint(actor,state=>{state.iosReplyDestinationInvariant=destination;});
    report.phase='system_reply';
    const stepId=randomUUID();
    report.ui=await submitNotificationReplyAttempt({journal:actor.journal,stepId,execute:input=>channel.submitNotificationReply(input)});
    report.phase='verify_exact_message';
    const deadline=Date.now()+30000;
    do {
      report.message=await observeNotificationReplyMessage({client,journal:actor.journal});
      if(report.message.persisted)break;
      await new Promise(resolve=>setTimeout(resolve,500));
    } while(Date.now()<deadline);
    if(report.message.persisted!==true)throw Error('notification_reply_message_missing');
    report.phase='verify_notification_removed';
    const outcome={runId,stepId:randomUUID(),profileId:actor.record.profileId,threadId:target.threadId};
    await checkpoint(actor,state=>{state.notificationReply.outcome={input:outcome,started:true,verified:false};});
    const receipt=await channel.verifyNotificationReplyOutcome(outcome);
    if(!receipt||Object.keys(receipt).sort().join(',')!=='notificationRemoved,runId,stepId'||
      receipt.runId!==runId||receipt.stepId!==outcome.stepId||receipt.notificationRemoved!==true)
      throw Error('notification_reply_outcome_unverified');
    await checkpoint(actor,state=>{state.notificationReply.outcome.verified=true;});
    report.notificationRemoved=true;
    // Re-observe after the native outcome test; a late duplicate must fail too.
    report.message=await observeNotificationReplyMessage({client,journal:actor.journal});
    if(report.message.persisted!==true)throw Error('notification_reply_message_missing');
    report.status='passed';
  } catch(error) {
    report.failureCode=/^(notification_reply|deep_link)_[a-z_]+$/.test(error?.message??'')?error.message:'notification_reply_trial_failed';
  } finally {
    try {
      if(nativeInput)await runIosDeepLinkSessionStep({journal:actors[0].journal,
        input:{...nativeInput,stage:'clear',stepId:randomUUID()},execute:input=>channel.sessionStep(input)});
      await channel.close();closed=channel.settled()===true;
    } catch {report.nativeClosureUnverified=true;channel.abort();}
    if(await settled().catch(()=>false))try {
      if(plan) {
        const current=await actors[0].journal.read();
        if(current.state.threadStarted) {
          if(current.state.webNotificationSeedPush!==undefined&&current.state.webNotificationSeedPush.settledWithoutDestinations!==true)
            throw Error('notification_reply_cleanup_unresolved');
          if(current.state.threadReceipt) {
            await verifyFreeze('cleanup_thread');
            if(current.state.webNotificationSeedPush?.settledWithoutDestinations!==true||
              current.state.notificationReply!==undefined&&(!current.state.notificationReply.backendReceipt||report.notificationRemoved!==true))
              throw Error('notification_reply_cleanup_unresolved');
            let inTransaction=false;
            const guardedClient={query:async(sql,args)=>{
              if(sql.startsWith('delete from public.chat_threads ')) {
                if(!inTransaction)throw Error('notification_reply_cleanup_unresolved');
                await auditDestinations(current.state.notificationReply?.backendReceipt?.messageId??null);
              }
              const result=await client.query(sql,args);
              if(sql==='begin')inTransaction=true;
              if(sql==='commit'||sql==='rollback')inTransaction=false;
              return result;
            }};
            report.threadCleanup=await removeNotificationReplyThread({client:guardedClient,journal:actors[0].journal,operationsSettled:settled});
            if(current.state.notificationReply!==undefined)report.replyTrigger={requestTerminal:null,mutationImpossible:true};
            if(report.status==='passed'&&report.threadCleanup.matchesVerifiedReceipt!==true) {
              report.status='failed';report.failureCode='notification_reply_final_message_set_changed';
            }
          }
          else await removeDeepLinkThread({client,journal:actors[0].journal,plan,operationsSettled:settled});
        }
      }
      for(const actor of [...actors].reverse()) {
        const current=await actor.journal.read();
        if(current.state.profileCreationStarted)await retireDeepLinkProfile({client,journal:actor.journal,record:actor.record,operationsSettled:settled});
      }
      for(const actor of [...actors].reverse())await actor.journal.removeAfterVerification(async()=>{
        const result=await client.query(`select
          not exists(select 1 from auth.users where id=$1::uuid or email=$3) as auth,
          not exists(select 1 from public.community_profiles where id=$2::uuid or auth_user_id=$1::uuid) as profile,
          not exists(select 1 from auth.sessions where user_id=$1::uuid) as sessions,
          not exists(select 1 from public.web_client_sessions where auth_user_id=$1::uuid or profile_id=$2::uuid) as web_sessions`,
          [actor.record.authUserId,actor.record.profileId,actor.record.email]);
        const ok=result.rowCount===1&&['auth','profile','sessions','web_sessions'].every(key=>result.rows[0][key]===true);
        return {password:ok,secret:ok,sessions:ok};
      });
      report.cleanupComplete=true;
    } catch {report.cleanupFailureCode='notification_reply_cleanup_unresolved';}
    await lock.close();
    if(report.cleanupComplete)await unlink(lockPath);
    else report.status='failed_cleanup_pending';
  }
  return report;
}
