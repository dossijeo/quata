import {mkdir,open,unlink} from 'node:fs/promises';
import path from 'node:path';
import {randomUUID,randomBytes,randomInt} from 'node:crypto';
import {createRecoveryJournal} from './e2e-fixtures/recovery-private-journal.mjs';
import {createDeepLinkProfile,retireDeepLinkProfile} from './e2e-fixtures/chat-deep-link-profile.mjs';
import {seedDeepLinkThread,removeDeepLinkThread} from './e2e-fixtures/chat-deep-link-thread.mjs';
import {recordRecoveryNativeSession} from './e2e-fixtures/recovery-session-receipt.mjs';
import {iosDeepLinkCustodySettled} from './e2e-fixtures/chat-deep-link-ios-custody.mjs';
import {retireAndroidDeepLinkResidue} from './e2e-fixtures/chat-deep-link-android-residue.mjs';
import {verifyIosDeepLinkResidueAbsent} from './e2e-fixtures/chat-deep-link-ios-residue.mjs';

export async function acknowledgeIosNativeOwnedRead({journal,ticket,input,channel}) {
  const saved=await journal.read();
  const matches=saved.state.sessions.filter(entry=>entry.ticketId===ticket.ticketId);
  const entry=matches[0],read=entry?.iosNativeLogin?.read;
  if(matches.length!==1||['runId','profileId','authUserId','authSessionId'].some(key=>entry[key]!==ticket[key])||
    read?.verified!==true||read.started!==true||!read.privateSession||read.acknowledgment!==undefined||
    ['runId','stepId','stage','profileId','authUserId'].some(key=>read.input?.[key]!==input[key]))
    throw Error('deep_link_native_acknowledgment_unverified');
  read.acknowledgment={runId:input.runId,stepId:input.stepId,started:true,verified:false};
  await journal.checkpoint(saved.state);
  const receipt=await channel.acknowledgeOwnedRead({runId:input.runId,stepId:input.stepId});
  if(receipt?.acknowledged!==true||receipt.runId!==input.runId||receipt.stepId!==input.stepId)
    throw Error('deep_link_native_acknowledgment_unverified');
  read.acknowledgment.verified=true;
  await journal.checkpoint(saved.state);
}

// The caller owns the dedicated device lease, public resolver delivery, exact
// product/backend preflight and passive session transport. No Web session import.
export async function runNativeDeepLinkChatTrial({client,privateDirectory,backendUrl,publicKey,
  adminRequest,preflight,ui,channel,sessionStep,transportSettled,mode,fetchImpl=fetch,
  platform='android',retireNativeResidue=platform==='android'?retireAndroidDeepLinkResidue:verifyIosDeepLinkResidueAbsent}) {
  if(!['android','ios'].includes(platform)||typeof retireNativeResidue!=='function'||
    (platform==='ios'&&typeof channel?.acknowledgeOwnedRead!=='function')||
    !['cold','warm'].includes(mode)||!path.isAbsolute(privateDirectory)||
    [preflight,ui?.deliver,ui?.login,ui?.close,sessionStep,transportSettled,channel?.close,channel?.settled,channel?.abort].some(x=>typeof x!=='function'))
    throw Error('deep_link_native_configuration_invalid');
  await mkdir(privateDirectory,{recursive:true});
  const nativeKey=platform==='android'?'androidNativeLogin':'iosNativeLogin';
  const lockPath=path.join(privateDirectory,'flow-deep-links.lock'),lock=await open(lockPath,'wx',0o600);
  const runId=randomUUID(),actors=[],report={unit:'FLOW-DEEP-LINKS',runId,mode,status:'failed',cleanupComplete:false};
  let plan,uiClosed=false;
  const settled=async()=>uiClosed&&channel.settled()===true&&await transportSettled()===true&&
    (await Promise.all(actors.map(async actor=>(await actor.journal.read()).state.sessions.every(entry=>iosDeepLinkCustodySettled(entry,platform))))).every(Boolean);
  const checkpoint=async(actor,change)=>{const saved=await actor.journal.read();change(saved.state);await actor.journal.checkpoint(saved.state);};
  try {
    await lock.writeFile(JSON.stringify({runId,pid:process.pid}));await lock.sync();
    report.phase='preflight';if(await preflight()!==true)throw Error('deep_link_native_preflight_failed');
    for(let index=0;index<2;index++) {
      const authUserId=randomUUID(),record={runId,profileId:randomUUID(),authUserId,email:`deep-link-${authUserId}@example.invalid`,
        countryCode:'240',phone:`99${randomInt(100000000,1000000000)}`,password:randomBytes(24).toString('base64url'),state:{sessions:[]}};
      const journal=await createRecoveryJournal({directory:privateDirectory,record}),actor={record,journal};actors.push(actor);
      report.phase=`create_profile_${index}`;await createDeepLinkProfile({client,journal,record,password:record.password,adminRequest});
    }
    const actor=actors[0];
    plan={runId,ownerId:actor.record.profileId,peerId:actors[1].record.profileId,uniqueKey:`quata-deep-link-${runId}`,
      messageKey:`quata-deep-link-message-${runId}`,body:`Deep link ${runId}`};
    await checkpoint(actor,state=>{state.threadPlan=plan;});
    report.phase='seed_thread';const target=await seedDeepLinkThread({client,journal:actor.journal,plan});
    report.phase='deliver_anonymous';report.delivery=await ui.deliver({runId,target,mode});
    report.phase='audit_native_actor';
    const audit=await client.query(`select
      (u.raw_app_meta_data->'quata_e2e'->>'unit'='FLOW-DEEP-LINKS' and u.raw_app_meta_data->'quata_e2e'->>'run_id'=$3) as owned,
      (p.account_status='active' and (select count(*) from public.community_profiles q where q.auth_user_id=u.id)=1) as unique_active,
      (p.phone_local=$5 and regexp_replace(coalesce(nullif(p.country_code,''),p.code,''),'[^0-9]','','g')=$4
        and (select count(*) from public.community_profiles q where q.phone_local=$5 or q.phone_normalized=$5 or q.telefono=$5)=1) as phone_matches,
      (not exists(select 1 from auth.sessions s where s.user_id=u.id)
        and not exists(select 1 from public.web_client_sessions w where w.auth_user_id=u.id and w.revoked_at is null)) as no_sessions
      from public.community_profiles p join auth.users u on u.id=p.auth_user_id where p.id=$1::uuid and u.id=$2::uuid`,
      [actor.record.profileId,actor.record.authUserId,runId,actor.record.countryCode,actor.record.phone]);
    if(audit.rowCount!==1||['owned','unique_active','phone_matches','no_sessions'].some(k=>audit.rows[0][k]!==true))throw Error('deep_link_native_actor_unverified');
    const ticket={runId,profileId:actor.record.profileId,authUserId:actor.record.authUserId,kind:'native',ticketId:randomUUID(),
      purpose:'deep_link',requestStarted:true,[nativeKey]:{observationVerified:false}};
    await checkpoint(actor,state=>{state.sessions.push(ticket);});
    report.phase='native_login';
    report.observation=await ui.login({runId,stepId:randomUUID(),countryCode:actor.record.countryCode,
      phone:actor.record.phone,password:actor.record.password,...(platform==='ios'?{ticketId:ticket.ticketId}:{}),
      messageId:String(target.messageId)});
    if(report.observation?.passed!==true)throw Error('deep_link_native_observation_failed');
    await ui.close();
    await checkpoint(actor,state=>{state.sessions[0][nativeKey].observationVerified=true;});
    const input={runId,stepId:randomUUID(),stage:'read-owned',profileId:actor.record.profileId,authUserId:actor.record.authUserId};
    report.phase='native_session_read';
    await checkpoint(actor,state=>{state.sessions[0][nativeKey].read={input,started:true,verified:false};});
    const read=await sessionStep(input),session=read.privateSession;
    // Persist the returned bearer before any remote verification or attempted clear.
    await checkpoint(actor,state=>{state.sessions[0][nativeKey].read.privateSession=session;});
    if(read.verified!==true||['runId','stepId','stage'].some(key=>read[key]!==input[key]))throw Error('deep_link_native_read_unverified');
    report.phase='native_receipt';
    await recordRecoveryNativeSession({client,journal:actor.journal,ticket,accessToken:session.accessToken,backendUrl,publicKey,fetchImpl});
    if(session.profileId!==ticket.profileId||session.authUserId!==ticket.authUserId||session.authSessionId!==ticket.authSessionId)
      throw Error('deep_link_native_session_mismatch');
    const exact=await client.query(`select
      (select count(*) from auth.sessions where user_id=$1::uuid)=1
        and exists(select 1 from auth.sessions where user_id=$1::uuid and id=$2::uuid) as one_native,
      not exists(select 1 from public.web_client_sessions where auth_user_id=$1::uuid) as no_web`,
      [ticket.authUserId,ticket.authSessionId]);
    if(exact.rows?.[0]?.one_native!==true||exact.rows?.[0]?.no_web!==true)throw Error('deep_link_native_session_set_unverified');
    await checkpoint(actor,state=>{state.sessions[0][nativeKey].read.verified=true;});
    if(platform==='ios') {
      report.phase='native_read_acknowledgment';
      await acknowledgeIosNativeOwnedRead({journal:actor.journal,ticket,input,channel});
    }
    const clearInput={...session,runId,stepId:randomUUID(),stage:'clear'};
    report.phase='native_session_clear';
    await checkpoint(actor,state=>{state.sessions[0][nativeKey].clear={input:clearInput,started:true,verified:false};});
    const cleared=await sessionStep(clearInput);
    if(cleared.verified!==true||['runId','stepId','stage'].some(key=>cleared[key]!==clearInput[key]))throw Error('deep_link_native_clear_unverified');
    await checkpoint(actor,state=>{state.sessions[0][nativeKey].clear.verified=true;});
    await channel.close();uiClosed=true;
    report.status='passed';report.phase='cleanup';
  } catch(error) {
    report.failureCode=/^deep_link_[a-z_]+$/.test(error.message??'')?error.message:'deep_link_native_trial_failed';
  } finally {
    // Only pre-login failures can close normally without an exact native clear.
    if(!uiClosed)try {
      const attempted=(await Promise.all(actors.map(async actor=>(await actor.journal.read()).state.sessions.length))).some(Boolean);
      if(!attempted){await ui.close();await channel.close();uiClosed=true;}
    }catch{/* Preserve device and private journals on uncertainty. */}
    if(await settled().catch(()=>false))try {
      if(plan&&(await actors[0].journal.read()).state.threadStarted)await removeDeepLinkThread({client,journal:actors[0].journal,plan,operationsSettled:settled});
      for(const actor of [...actors].reverse()) {
        const saved=await actor.journal.read();
        if(saved.state.sessions.some(s=>s[nativeKey]))await retireNativeResidue({client,journal:actor.journal,record:actor.record,operationsSettled:settled});
        if(saved.state.profileCreationStarted)await retireDeepLinkProfile({client,journal:actor.journal,record:actor.record,operationsSettled:settled});
      }
      for(const actor of [...actors].reverse())await actor.journal.removeAfterVerification(async()=>{
        const result=await client.query(`select
          not exists(select 1 from auth.users where id=$1::uuid or email=$3) as auth,
          not exists(select 1 from public.community_profiles where id=$2::uuid or auth_user_id=$1::uuid) as profile,
          not exists(select 1 from auth.sessions where user_id=$1::uuid) as sessions,
          not exists(select 1 from public.web_client_sessions where auth_user_id=$1::uuid or profile_id=$2::uuid) as web_sessions`,
          [actor.record.authUserId,actor.record.profileId,actor.record.email]);
        const ok=['auth','profile','sessions','web_sessions'].every(key=>result.rows?.[0]?.[key]===true);
        return {password:ok,secret:ok,sessions:ok};
      });
      report.cleanupComplete=true;
    }catch{report.cleanupFailureCode='deep_link_native_cleanup_unresolved';}
    if(!channel.settled())channel.abort();
    await lock.close();if(report.cleanupComplete)await unlink(lockPath);
    if(!report.cleanupComplete)report.status='failed_cleanup_pending';
  }
  return report;
}
