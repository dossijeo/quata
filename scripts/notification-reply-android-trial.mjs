import {mkdir,open,unlink} from 'node:fs/promises';
import path from 'node:path';
import {randomUUID,randomBytes,randomInt} from 'node:crypto';
import {createRecoveryJournal} from './e2e-fixtures/recovery-private-journal.mjs';
import {createDeepLinkProfile,retireDeepLinkProfile} from './e2e-fixtures/chat-deep-link-profile.mjs';
import {loginDeepLinkSession} from './e2e-fixtures/chat-deep-link-session.mjs';
import {seedDeepLinkThread,removeDeepLinkThread} from './e2e-fixtures/chat-deep-link-thread.mjs';
import {prepareAndroidDeepLinkSession} from './e2e-fixtures/chat-deep-link-ios-session.mjs';
import {runAndroidDeepLinkCustodyStep,androidDeepLinkCustodySettled} from './e2e-fixtures/chat-deep-link-ios-custody.mjs';
import {retireAndroidDeepLinkResidue} from './e2e-fixtures/chat-deep-link-android-residue.mjs';
import {observeWebNotificationSeedPush} from './e2e-fixtures/web-notification-seed-push.mjs';
import {prepareAndroidReplyDestinationInvariant} from './e2e-fixtures/android-notification-cleanup-disposition.mjs';
import {submitAndroidNotificationReplyAttempt,observeNotificationReplyMessage,removeAndroidNotificationReplyThread} from './e2e-fixtures/notification-reply-attempt.mjs';
const exact=(value,expected)=>value&&Object.keys(value).sort().join(',')===Object.keys(expected).sort().join(',')&&
  Object.keys(expected).every(key=>value[key]===expected[key]);

// Native SystemUI is the only Reply producer. channel owns the candidate lease,
// pinned APK changes, actual process closure, and the private session socket.
// Uncertain submission is never retried or accepted as a backend/UI success.
export async function runAndroidNotificationReplyTrial({client,privateDirectory,backendUrl,publicKey,
  adminRequest,preflight,channel,transportSettled,fetchImpl=fetch}) {
  if(!path.isAbsolute(privateDirectory)||[adminRequest,preflight,transportSettled].some(fn=>typeof fn!=='function')||
    ['sessionStep','submitNotificationReply','verifyNotificationReplyOutcome','reconcileNotification','close','settled','abort']
      .some(key=>typeof channel?.[key]!=='function'))throw Error('notification_reply_android_trial_configuration_invalid');
  await mkdir(privateDirectory,{recursive:true});
  const lockPath=path.join(privateDirectory,'flow-deep-links.lock'),lock=await open(lockPath,'wx',0o600);
  const runId=randomUUID(),actors=[],report={unit:'FLOW-NOTIFICATION-REPLY',platform:'android',runId,
    status:'failed',phase:'preflight',cleanupComplete:false,pushDeliveryCertified:false};
  let freeze,plan,nativeInput,nativeAttempt,closed=false,notificationClean=false,noNativeIntent=false,loginUncertain=false;
  const checkpoint=async(actor,change)=>{const current=await actor.journal.read();change(current.state);await actor.journal.checkpoint(current.state);};
  const verifyFreeze=async phase=>{
    const result=await preflight({runId,phase});
    if(result?.passed!==true||result.senderExcluded!==true||typeof result.dispatcherFingerprint!=='string'||
      !/^[0-9a-f]{64}$/.test(result.dispatcherFingerprint)||freeze&&freeze.dispatcherFingerprint!==result.dispatcherFingerprint)
      throw Error('notification_reply_android_preflight_failed');
    return {dispatcherFingerprint:result.dispatcherFingerprint};
  };
  const settled=async()=>closed&&channel.settled()===true&&!loginUncertain&&await transportSettled()===true&&
    (await Promise.all(actors.map(async actor=>(await actor.journal.read()).state.sessions.every(androidDeepLinkCustodySettled)))).every(Boolean);
  const poll=async(observe,ready)=>{
    const deadline=Date.now()+30000;
    do {const value=await observe();if(ready(value))return value;await new Promise(resolve=>setTimeout(resolve,500));}while(Date.now()<deadline);
    throw Error('notification_reply_android_observation_pending');
  };
  try {
    await lock.writeFile(JSON.stringify({runId,pid:process.pid}));await lock.sync();
    freeze=await verifyFreeze('initial');
    for(let index=0;index<2;index++) {
      report.phase=`create_fixture_${index}`;
      const authUserId=randomUUID(),record={runId,profileId:randomUUID(),authUserId,
        email:`deep-link-${authUserId}@example.invalid`,countryCode:'240',phone:`99${randomInt(100000000,1000000000)}`,
        password:randomBytes(24).toString('base64url'),state:{sessions:[],evidenceUnit:'FLOW-NOTIFICATION-REPLY',androidRuntimeFreeze:freeze}};
      const journal=await createRecoveryJournal({directory:privateDirectory,record}),actor={record,journal};actors.push(actor);
      await createDeepLinkProfile({client,journal,record,password:record.password,adminRequest});
    }
    const actor=actors[0],peer=actors[1],ticket={runId,profileId:actor.record.profileId,authUserId:actor.record.authUserId,
      purpose:'deep_link',clientInstanceId:randomUUID()};
    await checkpoint(actor,state=>state.sessions.push(ticket));
    report.phase='login_fixture';loginUncertain=true;
    let session;
    try {
      session=await loginDeepLinkSession({client,journal:actor.journal,record:actor.record,ticket,password:actor.record.password,
        backendUrl,publicKey,fetchImpl});loginUncertain=false;
    } catch(error) {
      const entry=(await actor.journal.read()).state.sessions[0];
      loginUncertain=entry?.requestStarted===true&&!(entry.authSessionId&&entry.webSessionId)&&entry.noSession!==true;throw error;
    }
    plan={runId,ownerId:actor.record.profileId,peerId:peer.record.profileId,uniqueKey:`quata-deep-link-${runId}`,
      messageKey:`quata-deep-link-message-${runId}`,body:`Deep link ${runId}`};
    await checkpoint(actor,state=>{state.threadPlan=plan;});
    report.phase='seed_and_settle_push';
    const target=await seedDeepLinkThread({client,journal:actor.journal,plan,capturePushRequest:true});
    await poll(()=>observeWebNotificationSeedPush({client,journal:actor.journal}),value=>value.settled===true);
    await verifyFreeze('before_install');
    report.phase='install_owned_session';
    nativeInput=await prepareAndroidDeepLinkSession({client,journal:actor.journal,record:actor.record,ticket,session,backendUrl,publicKey,fetchImpl});
    await runAndroidDeepLinkCustodyStep({journal:actor.journal,input:nativeInput,execute:input=>channel.sessionStep(input)});
    await prepareAndroidReplyDestinationInvariant({client,journal:actor.journal,peerJournal:peer.journal,...freeze});
    report.phase='system_reply';
    report.ui=await submitAndroidNotificationReplyAttempt({journal:actor.journal,stepId:randomUUID(),execute:async input=>{
      nativeAttempt=input;
      return channel.submitNotificationReply({...input,authUserId:actor.record.authUserId});
    }});
    report.phase='verify_exact_message';
    report.message=await poll(()=>observeNotificationReplyMessage({client,journal:actor.journal}),value=>value.persisted===true);
    report.phase='verify_notification_removed';
    const outcome={runId,stepId:randomUUID(),attemptStepId:nativeAttempt.stepId,profileId:actor.record.profileId,threadId:target.threadId};
    await checkpoint(actor,state=>{state.notificationReply.outcome={input:outcome,started:true,verified:false};});
    const receipt=await channel.verifyNotificationReplyOutcome(outcome);
    if(!exact(receipt,{runId,stepId:outcome.stepId,attemptStepId:nativeAttempt.stepId,notificationRemoved:true,
      backendVerified:false,reconciled:false}))throw Error('notification_reply_android_outcome_unverified');
    await checkpoint(actor,state=>{state.notificationReply.outcome.verified=true;});
    report.notificationRemoved=true;
    report.message=await observeNotificationReplyMessage({client,journal:actor.journal});
    if(report.message.persisted!==true)throw Error('notification_reply_message_missing');
    report.status='passed';
  } catch(error) {
    report.failureCode=/^(notification_reply|deep_link|android_notification|web_notification)_[a-z_]+$/.test(error?.message??'')?
      error.message:'notification_reply_android_trial_failed';
  } finally {
    try {
      if(nativeAttempt) {
        const input={runId,stepId:randomUUID(),attemptStepId:nativeAttempt.stepId,profileId:nativeAttempt.profileId,threadId:nativeAttempt.threadId};
        await checkpoint(actors[0],state=>{state.notificationReply.reconciliation={input,started:true,verified:false};});
        const receipt=await channel.reconcileNotification(input);
        const expected={runId,stepId:input.stepId,attemptStepId:nativeAttempt.stepId,notificationRemoved:true,
          backendVerified:false,reconciled:true};
        noNativeIntent=exact(receipt,{...expected,intentAbsent:true});
        if(!noNativeIntent&&!exact(receipt,expected))throw Error('notification_reply_android_cleanup_receipt_invalid');
        const attempt=(await actors[0].journal.read()).state.notificationReply;
        if(noNativeIntent&&(attempt.uiVerified!==false||attempt.backendReceipt!==undefined))
          throw Error('notification_reply_android_cleanup_contradiction');
        await checkpoint(actors[0],state=>{state.notificationReply.reconciliation.verified=true;
          state.notificationReply.reconciliation.receipt=receipt;});
      }
      notificationClean=true; // No native attempt, or its independent receipt above.
      if(nativeInput)await runAndroidDeepLinkCustodyStep({journal:actors[0].journal,input:{...nativeInput,stage:'clear',stepId:randomUUID()},
        execute:input=>channel.sessionStep(input)});
      const closure=await channel.close({runId});
      closed=exact(closure,{runId,processClosed:true})&&channel.settled()===true;
      if(!closed)throw Error('notification_reply_android_closure_unverified');
    } catch {report.nativeClosureUnverified=true;channel.abort();}
    if(await settled().catch(()=>false))try {
      if(plan) {
        const actor=actors[0],record=await actor.journal.read(),state=record.state;
        if(state.threadStarted===true&&(!state.threadReceipt||state.webNotificationSeedPush?.settledWithoutDestinations!==true))
          throw Error('notification_reply_android_seed_unresolved');
        if(state.threadReceipt) {
          await verifyFreeze('cleanup_thread');
          if(state.notificationReply!==undefined&&!noNativeIntent) {
            const receipt=state.notificationReply.backendReceipt;
            if(!receipt||!notificationClean)throw Error('notification_reply_android_attempt_uncertain');
            await checkpoint(actor,next=>{next.androidReplyClosure={runId,processClosed:true,notificationRemoved:true,transportSettled:true};});
            const cleanupDisposition={kind:'android-reply-trigger-no-mutable-destinations',runId,ownerId:record.profileId,
              peerId:actors[1].record.profileId,threadId:state.threadReceipt.threadId,replyMessageId:receipt.messageId,
              dispatcherFingerprint:freeze.dispatcherFingerprint,nativeProcessClosed:true,sessionCustodySettled:true,
              seedDispatcherSettled:true,replyTrigger:{requestTerminal:null,mutationImpossible:true}};
            report.threadCleanup=await removeAndroidNotificationReplyThread({client,journal:actor.journal,peerJournal:actors[1].journal,
              ...freeze,cleanupDisposition});
            report.replyTrigger=cleanupDisposition.replyTrigger;
            if(report.status==='passed'&&report.threadCleanup.matchesVerifiedReceipt!==true) {
              report.status='failed';report.failureCode='notification_reply_final_message_set_changed';
            }
          } else await removeDeepLinkThread({client,journal:actor.journal,plan,operationsSettled:settled});
        }
      }
      if(actors[0]&&(await actors[0].journal.read()).state.sessions.some(entry=>entry.androidSession))
        await retireAndroidDeepLinkResidue({client,journal:actors[0].journal,record:actors[0].record,operationsSettled:settled});
      for(const actor of [...actors].reverse()) {
        if((await actor.journal.read()).state.profileCreationStarted)
          await retireDeepLinkProfile({client,journal:actor.journal,record:actor.record,operationsSettled:settled});
      }
      for(const actor of [...actors].reverse())await actor.journal.removeAfterVerification(async()=>{
        const result=await client.query(`select not exists(select 1 from auth.users where id=$1::uuid or email=$3) as auth,
          not exists(select 1 from public.community_profiles where id=$2::uuid or auth_user_id=$1::uuid) as profile,
          not exists(select 1 from auth.sessions where user_id=$1::uuid) as sessions,
          not exists(select 1 from public.web_client_sessions where auth_user_id=$1::uuid or profile_id=$2::uuid) as web_sessions`,
          [actor.record.authUserId,actor.record.profileId,actor.record.email]);
        const ok=result.rowCount===1&&['auth','profile','sessions','web_sessions'].every(key=>result.rows[0][key]===true);
        return {password:ok,secret:ok,sessions:ok};
      });
      report.cleanupComplete=true;
    } catch {report.cleanupFailureCode='notification_reply_android_cleanup_unresolved';}
    await lock.close();
    if(report.cleanupComplete)await unlink(lockPath);else report.status='failed_cleanup_pending';
  }
  return report;
}
