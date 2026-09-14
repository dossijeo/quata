import {mkdir,open,unlink} from "node:fs/promises";
import path from "node:path";
import {randomUUID,randomBytes,randomInt} from "node:crypto";
import {createRecoveryJournal} from "./e2e-fixtures/recovery-private-journal.mjs";
import {createDeepLinkProfile,retireDeepLinkProfile} from "./e2e-fixtures/chat-deep-link-profile.mjs";
import {loginDeepLinkSession} from "./e2e-fixtures/chat-deep-link-session.mjs";
import {observeDeepLinkRefresh} from "./e2e-fixtures/chat-deep-link-refresh.mjs";
import {prepareRevokedDeepLinkSession,observeRevokedDeepLinkRefresh} from "./e2e-fixtures/chat-deep-link-revoked-session.mjs";
import {seedDeepLinkThread,removeDeepLinkThread} from "./e2e-fixtures/chat-deep-link-thread.mjs";
import {verifyMissingDeepLinkMessage} from "./e2e-fixtures/chat-deep-link-missing-message.mjs";
import {verifyMissingDeepLinkThread} from "./e2e-fixtures/chat-deep-link-missing-thread.mjs";
import {iosDeepLinkCustodySettled,runIosDeepLinkSessionStep,androidDeepLinkCustodySettled,runAndroidDeepLinkCustodyStep} from "./e2e-fixtures/chat-deep-link-ios-custody.mjs";
import {prepareIosDeepLinkSession,prepareAndroidDeepLinkSession} from "./e2e-fixtures/chat-deep-link-ios-session.mjs";
import {retireAndroidDeepLinkResidue} from "./e2e-fixtures/chat-deep-link-android-residue.mjs";
import {prepareNativeDeepLinkExpiry,installNativeDeepLinkExpiry,readNativeDeepLinkExpiry,
  verifyNativeDeepLinkExpiryIdentity,acknowledgeNativeDeepLinkExpiryRead,clearNativeDeepLinkExpiry} from './e2e-fixtures/chat-deep-link-native-expiry.mjs';
import {prepareAndroidNativeDeepLinkRejection,prepareIosNativeDeepLinkRejection} from './e2e-fixtures/chat-deep-link-native-rejection.mjs';
import {observeAndroidNativeDeepLinkRejection,confirmAndroidNativeDeepLinkRejectionAbsence,
  observeIosNativeDeepLinkRejection,clearIosNativeDeepLinkRejection} from './e2e-fixtures/chat-deep-link-native-rejection-observation.mjs';

// Server-side assembly. The reviewed platform adapter owns the UI lifecycle.
// Caller supplies an already-connected dedicated DB client with statement_timeout,
// private Admin transport, exact remote-contract preflight, and a UI adapter that
// closes all contexts before close() resolves. This module never prints secrets.
export async function runDeepLinkChatTrial({client,privateDirectory,backendUrl,publicKey,
  adminRequest,preflight,ui,transportSettled,sessionMode,targetMode,fetchImpl=fetch}) {
  if(targetMode!==undefined&&(!["missing-message","missing-thread"].includes(targetMode)||sessionMode!==undefined||ui?.prepareLogin!==undefined))throw Error("deep_link_trial_target_mode_invalid");
  if(sessionMode!==undefined && !["refresh","revoked","native-refresh-cold","native-refresh-warm","native-rejection-cold"].includes(sessionMode))throw Error("deep_link_trial_session_mode_invalid");
  const nativeRejection=sessionMode==='native-rejection-cold';
  const nativeExpiry=nativeRejection||['native-refresh-cold','native-refresh-warm'].includes(sessionMode);
  if(!path.isAbsolute(privateDirectory) || typeof preflight!=="function" ||
      typeof transportSettled!=="function" || typeof ui?.run!=="function" || typeof ui?.close!=="function") {
    throw Error("deep_link_trial_configuration_invalid");
  }
  const loginInUi=ui.prepareLogin!==undefined || ui.requestLogin!==undefined;
  if(ui.iosSessionChannel!==undefined&&ui.androidSessionChannel!==undefined)throw Error("deep_link_trial_multiple_native_channels");
  const android=ui.androidSessionChannel!==undefined;
  const nativeChannel=android?ui.androidSessionChannel:ui.iosSessionChannel;
  const prepareNativeSession=android?prepareAndroidDeepLinkSession:prepareIosDeepLinkSession;
  const runNativeStep=android?runAndroidDeepLinkCustodyStep:runIosDeepLinkSessionStep;
  if(nativeRejection&&(!nativeChannel||ui.nativeRejectionMode!=='cold'||ui.nativeExpiryMode!==undefined)||
    !nativeRejection&&ui.nativeRejectionMode!==undefined)throw Error('deep_link_trial_native_rejection_configuration_invalid');
  if(nativeExpiry&&!nativeRejection&&(!nativeChannel||`native-refresh-${ui.nativeExpiryMode}`!==sessionMode||
    (android?sessionMode!=='native-refresh-cold':typeof nativeChannel.acknowledgeOwnedRead!=='function')))
    throw Error('deep_link_trial_native_expiry_configuration_invalid');
  if(!nativeExpiry&&ui.nativeExpiryMode!==undefined)throw Error('deep_link_trial_native_expiry_configuration_invalid');
  if(nativeChannel!==undefined&&((sessionMode!==undefined&&!nativeExpiry)||loginInUi||
      ["sessionStep","close","settled","abort"].some(key=>typeof nativeChannel?.[key]!=="function")))throw Error(android?"deep_link_trial_android_channel_invalid":"deep_link_trial_ios_channel_invalid");
  if(loginInUi&&sessionMode!==undefined)throw Error("deep_link_trial_session_mode_invalid");
  if(loginInUi && (typeof ui.prepareLogin!=="function" || typeof ui.requestLogin!=="function")) {
    throw Error("deep_link_trial_ui_login_configuration_invalid");
  }
  await mkdir(privateDirectory,{recursive:true});
  const lockPath=path.join(privateDirectory,"flow-deep-links.lock");
  const lock=await open(lockPath,"wx",0o600);
  const runId=randomUUID();
  const report={unit:"FLOW-DEEP-LINKS",runId,status:"failed",phase:"preflight",cleanupComplete:false};
  const actors=[];
  let plan,nativeInput,expiryPrepared=false,expiryVerified=false,rejectionVerified=false,uiClosed=false,loginUncertain=false;
  const settled=async()=>{
    if(!uiClosed || loginUncertain || await transportSettled()!==true)return false;
    if(nativeChannel&&nativeChannel.settled()!==true)return false;
    // A response lost after refresh may have rotated credentials remotely. Keep
    // all fixtures/journals until the exact attempt is reconciled, even when the
    // browser has closed and the original login already had valid receipts.
    for(const actor of actors) {
      const current=await actor.journal.read();
      if(current.state.sessions.some(entry=>entry.refreshAttempt!==undefined && entry.refreshAttempt.verified!==true))return false;
      if(current.state.sessions.some(entry=>entry.revocation!==undefined && entry.revocation.verified!==true))return false;
      if(current.state.sessions.some(entry=>entry.nativeSessionRejection!==undefined?
        !(android?androidDeepLinkCustodySettled(entry):iosDeepLinkCustodySettled(entry)):
        (!iosDeepLinkCustodySettled(entry)||!androidDeepLinkCustodySettled(entry))))return false;
    }
    return true;
  };
  try {
    await lock.writeFile(JSON.stringify({runId,pid:process.pid}));await lock.sync();
    if(await preflight()!==true)throw Error("preflight_failed");
    for(let index=0;index<2;index++) {
      const authUserId=randomUUID();
      const record={runId,profileId:randomUUID(),authUserId,email:`deep-link-${authUserId}@example.invalid`,
        countryCode:"240",phone:`99${randomInt(100000000,1000000000)}`,
        password:randomBytes(24).toString("base64url"),state:{sessions:[]}};
      report.phase=`create_journal_${index}`;
      const journal=await createRecoveryJournal({directory:privateDirectory,record});
      actors.push({record,journal});
      report.phase=`create_profile_${index}`;
      await createDeepLinkProfile({client,journal,record,password:record.password,adminRequest});
    }
    report.phase="login";
    const actor=actors[0];
    const ticket={runId,profileId:actor.record.profileId,authUserId:actor.record.authUserId,
      purpose:"deep_link",clientInstanceId:randomUUID()};
    const durable=await actor.journal.read();durable.state.sessions.push(ticket);await actor.journal.checkpoint(durable.state);
    let target;
    const seedTarget=async()=>{
      plan={runId,ownerId:actor.record.profileId,peerId:actors[1].record.profileId,
        uniqueKey:`quata-deep-link-${runId}`,messageKey:`quata-deep-link-message-${runId}`,body:`Deep link ${runId}`};
      const threadState=await actor.journal.read();threadState.state.threadPlan=plan;await actor.journal.checkpoint(threadState.state);
      report.phase="seed_thread";
      target=await seedDeepLinkThread({client,journal:actor.journal,plan});
    };
    if(loginInUi) {
      // The anonymous browser needs the owned destination before authenticating.
      // Preparation receives no credentials and must not initiate a login.
      await seedTarget();
      report.phase="prepare_ui_login";
      await ui.prepareLogin({target,body:plan.body,clientInstanceId:ticket.clientInstanceId});
    }
    let session;
    report.phase="login";
    loginUncertain=true;
    try {session=await loginDeepLinkSession({client,journal:actor.journal,record:actor.record,ticket,
      password:actor.record.password,backendUrl,publicKey,fetchImpl,
      requestLogin:loginInUi?(...args)=>ui.requestLogin(...args):fetchImpl});loginUncertain=false;}
    catch(error){
      // No receipt is not permission to retry or infer that the remote POST ended.
      const current=await actor.journal.read();const entry=current.state.sessions[0];
      loginUncertain=entry?.requestStarted===true && !(entry.authSessionId && entry.webSessionId) && entry.noSession!==true;
      throw error;
    }
    if(!loginInUi)await seedTarget();
    if(nativeExpiry) {
      report.phase='prepare_native_expiry';
      await prepareNativeDeepLinkExpiry({client,journal:actor.journal,record:actor.record,ticket,session,backendUrl,publicKey,fetchImpl,platform:android?'android':'ios'});
      expiryPrepared=true;
      report.phase='install_native_expiry';
      await installNativeDeepLinkExpiry({journal:actor.journal,record:actor.record,stepId:randomUUID(),
        execute:input=>nativeChannel.sessionStep(input)});
    } else if(nativeChannel) {
      report.phase=android?"android_session_import":"ios_session_import";
      nativeInput=await prepareNativeSession({client,journal:actor.journal,record:actor.record,ticket,session,backendUrl,publicKey,fetchImpl});
      await runNativeStep({journal:actor.journal,input:nativeInput,execute:value=>nativeChannel.sessionStep(value)});
    }
    if(targetMode==="missing-message") {
      target={threadId:target.threadId,visibleMessageId:target.messageId,messageId:String(randomInt(1000000000000,9000000000000))};
      await verifyMissingDeepLinkMessage({client,target,plan});
      const saved=await actor.journal.read();saved.state.missingMessageTarget=target;await actor.journal.checkpoint(saved.state);
    }
    if(targetMode==="missing-thread") {
      target={ownedThreadId:target.threadId,threadId:String(randomInt(1000000000000,9000000000000)),messageId:String(randomInt(1000000000000,9000000000000))};
      await verifyMissingDeepLinkThread({client,target,plan});
      const saved=await actor.journal.read();saved.state.missingThreadTarget=target;await actor.journal.checkpoint(saved.state);
    }
    if(sessionMode==="revoked") {
      report.phase="revoke_owned_session";
      await prepareRevokedDeepLinkSession({client,journal:actor.journal,record:actor.record,ticket,session,
        backendUrl,publicKey,operationsSettled:transportSettled});
    }
    if(nativeRejection) {
      report.phase='revoke_native_owned_session';
      await (android?prepareAndroidNativeDeepLinkRejection:prepareIosNativeDeepLinkRejection)({client,journal:actor.journal,record:actor.record,ticket,session,
        backendUrl,publicKey,operationsSettled:transportSettled});
    }
    report.phase=nativeChannel?(android?"android_ui":"ios_ui"):"web_ui";
    const runUi=()=>ui.run({session,clientInstanceId:ticket.clientInstanceId,target,body:plan.body,
      observeRefresh:nativeExpiry?()=>{throw Error('deep_link_native_harness_refresh_forbidden');}:(requestRefresh,responseJournaled)=>(sessionMode==="revoked"?observeRevokedDeepLinkRefresh:observeDeepLinkRefresh)({client,journal:actor.journal,record:actor.record,ticket,
          session,backendUrl,publicKey,fetchImpl,requestRefresh,responseJournaled})});
    report.observation=nativeRejection?await (android?observeAndroidNativeDeepLinkRejection:observeIosNativeDeepLinkRejection)({client,journal:actor.journal,
      record:actor.record,ticket,session,target,execute:runUi}):await runUi();
    if(nativeRejection)rejectionVerified=true;
    if(targetMode==="missing-message")await verifyMissingDeepLinkMessage({client,target,plan});
    if(targetMode==="missing-thread")await verifyMissingDeepLinkThread({client,target,plan});
    if(nativeExpiry&&!nativeRejection) {
      if(report.observation?.passed!==true)throw Error('deep_link_native_expiry_observation_failed');
      report.phase='read_native_expiry';
      report.nativeExpiry=await readNativeDeepLinkExpiry({journal:actor.journal,record:actor.record,stepId:randomUUID(),
        execute:input=>nativeChannel.sessionStep(input)});
      report.phase='verify_native_expiry';
      report.nativeExpiry.identity=await verifyNativeDeepLinkExpiryIdentity({journal:actor.journal,record:actor.record,client,backendUrl,publicKey,fetchImpl});
      if(!android) {
        report.phase='ack_native_expiry';
        await acknowledgeNativeDeepLinkExpiryRead({journal:actor.journal,record:actor.record,
          acknowledge:input=>nativeChannel.acknowledgeOwnedRead(input)});
      }
      expiryVerified=true;
    }
    report.status=report.observation?.passed===true?"passed":"failed";
  } catch(error) {
    report.status="failed";
    report.failureCode=/^deep_link_[a-z_]+$/.test(error?.message??"")?error.message:"internal_failure";
  }
  finally {
    try {
    try {
      await ui.close();
      if(nativeChannel) {
        if(expiryPrepared) {
          if(nativeRejection) {
            if(!rejectionVerified)throw Error('deep_link_native_rejection_closure_unverified');
            report.nativeRejection=await (android?confirmAndroidNativeDeepLinkRejectionAbsence:clearIosNativeDeepLinkRejection)({journal:actors[0].journal,
              record:actors[0].record,stepId:randomUUID(),execute:input=>nativeChannel.sessionStep(input),operationsSettled:transportSettled});
          } else {
            if(!expiryVerified)throw Error('deep_link_native_expiry_closure_unverified');
            await clearNativeDeepLinkExpiry({journal:actors[0].journal,record:actors[0].record,stepId:randomUUID(),
              execute:input=>nativeChannel.sessionStep(input),operationsSettled:transportSettled});
          }
        }
        if(nativeInput)await runNativeStep({journal:actors[0].journal,
          input:{...nativeInput,stage:"clear",stepId:randomUUID()},execute:value=>nativeChannel.sessionStep(value)});
        await nativeChannel.close();
        if(nativeChannel.settled()!==true)throw Error(android?"deep_link_android_channel_unresolved":"deep_link_ios_channel_unresolved");
      }
      uiClosed=true;
    } catch {report.uiCloseFailed=true;nativeChannel?.abort();}
    if(await settled().catch(()=>false)) {
      let clean=true;
      if(plan) {
        const current=await actors[0].journal.read();
        if(current.state.threadStarted)try {await removeDeepLinkThread({client,journal:actors[0].journal,plan,operationsSettled:settled});}
        catch {clean=false;}
      }
      // Keep journals if the thread cannot be reconciled; do not orphan references.
      if(clean)for(const actor of [...actors].reverse()) {
        try {
          const current=await actor.journal.read();
          if(current.state.sessions.some(entry=>entry.androidSession||entry.nativeSessionRenewal?.platform==='android'))
            await retireAndroidDeepLinkResidue({client,journal:actor.journal,record:actor.record,operationsSettled:settled});
          if(current.state.profileCreationStarted)await retireDeepLinkProfile({client,journal:actor.journal,record:actor.record,operationsSettled:settled});
        } catch(error) {clean=false;report.cleanupFailureCode=/^deep_link_[a-z_]+$/.test(error?.message??"")?error.message:"deep_link_profile_cleanup_failed";}
      }
      // Keep both journals readable while retirement of either actor still
      // checks settled(). Remove them only after every retirement succeeded.
      if(clean)for(const actor of [...actors].reverse()) {
        try {
          await actor.journal.removeAfterVerification(async()=>{
            const absent=await client.query(`select
              not exists(select 1 from auth.users where id=$1::uuid or email=$3) as auth,
              not exists(select 1 from public.community_profiles where id=$2::uuid or auth_user_id=$1::uuid) as profile,
              not exists(select 1 from auth.sessions where user_id=$1::uuid) as sessions,
              not exists(select 1 from public.web_client_sessions where auth_user_id=$1::uuid or profile_id=$2::uuid) as web_sessions`,
              [actor.record.authUserId,actor.record.profileId,actor.record.email]);
            const ok=["auth","profile","sessions","web_sessions"].every(key=>absent.rows?.[0]?.[key]===true);
            // Original baseline is absence of these newly generated identities.
            return {password:ok,secret:ok,sessions:ok};
          });
        } catch {clean=false;}
      }
      report.cleanupComplete=clean;
    }
    } catch {report.cleanupComplete=false;}
    finally {
      await lock.close();
      if(report.cleanupComplete)await unlink(lockPath);
      else report.status="failed_cleanup_pending";
    }
  }
  return report;
}
