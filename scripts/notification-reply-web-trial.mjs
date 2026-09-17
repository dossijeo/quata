import {mkdir,open,unlink} from 'node:fs/promises';
import path from 'node:path';
import {randomUUID,randomBytes,randomInt} from 'node:crypto';
import {createRecoveryJournal} from './e2e-fixtures/recovery-private-journal.mjs';
import {createDeepLinkProfile,retireDeepLinkProfile} from './e2e-fixtures/chat-deep-link-profile.mjs';
import {loginDeepLinkSession} from './e2e-fixtures/chat-deep-link-session.mjs';
import {seedDeepLinkThread} from './e2e-fixtures/chat-deep-link-thread.mjs';
import {observeWebNotificationSeedPush} from './e2e-fixtures/web-notification-seed-push.mjs';
import {captureWebNotificationSubscription,observeWebNotificationSubscription,removeWebNotificationSubscription} from './e2e-fixtures/web-notification-subscription.mjs';
import {dispatchWebNotificationSeed} from './e2e-fixtures/web-notification-dispatch.mjs';
import {prepareWebReplyDestinationInvariant} from './e2e-fixtures/web-notification-cleanup-disposition.mjs';
import {createWebNotificationMessageCustody} from './e2e-fixtures/web-notification-message-custody.mjs';
import {observeWebNotificationReplyMessage,removeWebNotificationReplyThread} from './e2e-fixtures/notification-reply-attempt.mjs';

// The browser channel owns actual product gestures and original HTTP requests.
// All callbacks are awaited; it must serialize request custody with this runner.
// No token injection, synthetic notification click, direct Send RPC or retry.
export async function runWebNotificationReplyTrial({client,privateDirectory,backendUrl,publicKey,
  adminRequest,preflight,ui,executeDispatch,transportSettled,fetchImpl=fetch}) {
  if(!path.isAbsolute(privateDirectory)||[adminRequest,preflight,executeDispatch,transportSettled].some(fn=>typeof fn!=='function')||
    ['start','requestLogin','enablePush','clickNotification','sendReply','closeOwnedState'].some(key=>typeof ui?.[key]!=='function'))
    throw Error('web_notification_trial_configuration_invalid');
  await mkdir(privateDirectory,{recursive:true});
  const lockPath=path.join(privateDirectory,'flow-deep-links.lock'),lock=await open(lockPath,'wx',0o600);
  const runId=randomUUID(),clientInstanceId=randomUUID(),actors=[];
  const report={unit:'FLOW-NOTIFICATION-REPLY',platform:'web',runId,status:'failed',phase:'preflight',cleanupComplete:false,
    inlineReplyCertified:false,appleDeliveryCertified:false};
  let target,freeze,loginUncertain=false,closure;
  const checkpoint=async(actor,change)=>{const current=await actor.journal.read();change(current.state);await actor.journal.checkpoint(current.state);};
  const verifyFreeze=async phase=>{
    const value=await preflight({runId,phase});
    if(value?.passed!==true||!/^[0-9a-f]{64}$/.test(value.dispatcherFingerprint)||
      freeze&&freeze.dispatcherFingerprint!==value.dispatcherFingerprint)throw Error('web_notification_preflight_failed');
    return {dispatcherFingerprint:value.dispatcherFingerprint};
  };
  const poll=async(observe,ready)=>{
    const deadline=Date.now()+30000;
    do {const value=await observe();if(ready(value))return value;await new Promise(resolve=>setTimeout(resolve,500));}while(Date.now()<deadline);
    throw Error('web_notification_observation_pending');
  };
  try {
    await lock.writeFile(JSON.stringify({runId,pid:process.pid}));await lock.sync();
    freeze=await verifyFreeze('initial');
    const startup=await ui.start({runId,clientInstanceId});
    if(startup?.runId!==runId||startup.publicReady!==true||startup.anonymous!==true)throw Error('web_notification_public_startup_unverified');
    for(let index=0;index<2;index++) {
      report.phase=`create_fixture_${index}`;
      const authUserId=randomUUID(),record={runId,profileId:randomUUID(),authUserId,email:`deep-link-${authUserId}@example.invalid`,
        countryCode:'240',phone:`99${randomInt(100000000,1000000000)}`,password:randomBytes(24).toString('base64url'),
        state:{sessions:[],evidenceUnit:'FLOW-NOTIFICATION-REPLY',webRuntimeFreeze:freeze}};
      const journal=await createRecoveryJournal({directory:privateDirectory,record});
      actors.push({record,journal});
      await createDeepLinkProfile({client,journal,record,password:record.password,adminRequest});
    }
    const actor=actors[0],peer=actors[1],ticket={runId,profileId:actor.record.profileId,authUserId:actor.record.authUserId,
      purpose:'deep_link',clientInstanceId};
    await checkpoint(actor,state=>state.sessions.push(ticket));
    report.phase='product_login';loginUncertain=true;
    try {
      await loginDeepLinkSession({client,journal:actor.journal,record:actor.record,ticket,password:actor.record.password,
        backendUrl,publicKey,fetchImpl,requestLogin:(url,options)=>ui.requestLogin(url,options)});
      loginUncertain=false;
    } catch(error) {
      const entry=(await actor.journal.read()).state.sessions[0];
      loginUncertain=entry.requestStarted===true&&!(entry.authSessionId&&entry.webSessionId)&&entry.noSession!==true;
      throw error;
    }
    const plan={runId,ownerId:actor.record.profileId,peerId:peer.record.profileId,uniqueKey:`quata-deep-link-${runId}`,
      messageKey:`quata-deep-link-message-${runId}`,body:`Deep link ${runId}`};
    await checkpoint(actor,state=>{state.threadPlan=plan;});
    report.phase='seed_and_correlate_push';
    target=await seedDeepLinkThread({client,journal:actor.journal,plan,capturePushRequest:true});
    await poll(()=>observeWebNotificationSeedPush({client,journal:actor.journal}),value=>value.settled===true);
    report.phase='product_subscribe';
    const subscription=await ui.enablePush({runId,capture:subscription=>captureWebNotificationSubscription({client,journal:actor.journal,subscription})});
    if(subscription?.runId!==runId||subscription.productSubscribed!==true)throw Error('web_notification_subscription_ui_unverified');
    await poll(()=>observeWebNotificationSubscription({client,journal:actor.journal}),value=>value.persisted===true);
    report.phase='explicit_seed_delivery';
    report.dispatch=await dispatchWebNotificationSeed({client,journal:actor.journal,execute:executeDispatch});
    const subscribed=(await actor.journal.read()).state.webNotificationSubscription;
    const delivery=await client.query(`select status from public.web_push_delivery_log where message_id=$1::bigint
      and profile_id=$2::uuid and subscription_id=$3::uuid`,[target.messageId,actor.record.profileId,subscribed.receipt.subscriptionId]);
    if(delivery.rowCount!==1||delivery.rows[0].status!=='sent')throw Error('web_notification_delivery_log_unverified');
    report.deliveryLogSent=true;
    report.phase='native_notification_click';
    const click=await ui.clickNotification({runId,...target});
    if(click?.runId!==runId||click.messageId!==target.messageId||click.threadId!==target.threadId||
      click.clickedViaSystemUi!==true||click.chatVisible!==true)throw Error('web_notification_click_unverified');
    report.notificationClick=click;
    report.phase='prepare_reply_invariant';
    await prepareWebReplyDestinationInvariant({client,journal:actor.journal,peerJournal:peer.journal,...freeze});
    const marker=`quata-web-reply-${runId}`;
    const custody=createWebNotificationMessageCustody({journal:actor.journal,runId,profileId:actor.record.profileId,threadId:target.threadId,marker});
    report.phase='chat_ui_send';
    const sent=await ui.sendReply({runId,threadId:target.threadId,marker,capture:payload=>custody.capture(payload)});
    if(sent?.runId!==runId||sent.sentViaChatUi!==true)throw Error('web_notification_send_ui_unverified');
    report.message=await poll(()=>observeWebNotificationReplyMessage({client,journal:actor.journal}),value=>value.persisted===true);
    report.status='passed';
  } catch(error) {
    report.failureCode=/^(web_notification|deep_link|notification_reply)_[a-z_]+$/.test(error?.message??'')?error.message:'web_notification_trial_failed';
  } finally {
    try {
      closure=await ui.closeOwnedState({runId,target});
      if(closure?.runId!==runId||closure.browserClosed!==true||closure.serverClosed!==true||closure.transportSettled!==true||
        closure.ownedNotificationsRemoved!==true||closure.browserSubscriptionRemoved!==true)throw Error();
      if(actors[0])await checkpoint(actors[0],state=>{
        state.webBrowserClosure={runId,transportSettled:true};
        if(state.webNotificationSubscription)state.webNotificationSubscription.browserClean=true;
      });
      if(loginUncertain||await transportSettled()!==true)throw Error();
      if(actors[0]) {
        const actor=actors[0],record=await actor.journal.read(),state=record.state;
        if(state.threadStarted===true&&!state.threadReceipt)throw Error();
        if(state.webNotificationSeedPush&&state.webNotificationSeedPush.settledWithoutDestinations!==true)throw Error();
        if(state.webNotificationDispatch&&state.webNotificationDispatch.settled!==true)throw Error();
        let cleanupDisposition;
        if(state.webNotificationMessage) {
          // Without an exact observed persisted Reply, retain custody for recovery.
          if(!state.webNotificationMessage.backendReceipt||actors.length!==2)throw Error();
          const proof=state.webReplyDestinationInvariant,receipt=state.webNotificationMessage.backendReceipt;
          if(!proof)throw Error();
          cleanupDisposition={kind:'reply-trigger-no-mutable-destinations',runId,ownerId:record.profileId,peerId:actors[1].record.profileId,
            threadId:state.threadReceipt.threadId,replyMessageId:receipt.messageId,dispatcherFingerprint:freeze.dispatcherFingerprint,
            browserTransportSettled:true,seedDispatcherSettled:true,explicitDispatcherSettled:true,replyTrigger:{requestTerminal:null,mutationImpossible:true}};
          report.replyTrigger=cleanupDisposition.replyTrigger;
        }
        const readiness=cleanupDisposition?{cleanupDisposition,peerJournal:actors[1].journal,...freeze}:{operationsSettled:async()=>true};
        if(state.webNotificationSubscription) {
          await verifyFreeze('cleanup_subscription');
          await removeWebNotificationSubscription({client,journal:actor.journal,...readiness});
        }
        if(state.threadReceipt) {
          await verifyFreeze('cleanup_thread');
          report.threadCleanup=await removeWebNotificationReplyThread({client,journal:actor.journal,...readiness});
          if(report.status==='passed'&&report.threadCleanup.matchesVerifiedReceipt!==true){report.status='failed';report.failureCode='web_notification_final_message_set_changed';}
        }
      }
      // Browser and mutating dispatches are reconciled. The peer is retired only
      // after the Web-specific no-destination invariance has guarded both deletes.
      for(const actor of [...actors].reverse()) {
        if((await actor.journal.read()).state.profileCreationStarted)await retireDeepLinkProfile({client,journal:actor.journal,record:actor.record,operationsSettled:async()=>true});
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
    } catch {report.cleanupFailureCode='web_notification_cleanup_pending';report.status='failed_cleanup_pending';}
    await lock.close();
    if(report.cleanupComplete)await unlink(lockPath);
  }
  return report;
}
