import {mkdir,open,unlink} from "node:fs/promises";
import path from "node:path";
import {randomUUID,randomBytes,randomInt} from "node:crypto";
import {createRecoveryJournal} from "./e2e-fixtures/recovery-private-journal.mjs";
import {createDeepLinkProfile,retireDeepLinkProfile} from "./e2e-fixtures/chat-deep-link-profile.mjs";
import {loginDeepLinkSession} from "./e2e-fixtures/chat-deep-link-session.mjs";
import {seedDeepLinkThread,removeDeepLinkThread} from "./e2e-fixtures/chat-deep-link-thread.mjs";

// Server-side assembly. The reviewed platform adapter owns the UI lifecycle.
// Caller supplies an already-connected dedicated DB client with statement_timeout,
// private Admin transport, exact remote-contract preflight, and a UI adapter that
// closes all contexts before close() resolves. This module never prints secrets.
export async function runDeepLinkChatTrial({client,privateDirectory,backendUrl,publicKey,
  adminRequest,preflight,ui,transportSettled,fetchImpl=fetch}) {
  if(!path.isAbsolute(privateDirectory) || typeof preflight!=="function" ||
      typeof transportSettled!=="function" || typeof ui?.run!=="function" || typeof ui?.close!=="function") {
    throw Error("deep_link_trial_configuration_invalid");
  }
  await mkdir(privateDirectory,{recursive:true});
  const lockPath=path.join(privateDirectory,"flow-deep-links.lock");
  const lock=await open(lockPath,"wx",0o600);
  const runId=randomUUID();
  const report={unit:"FLOW-DEEP-LINKS",runId,status:"failed",cleanupComplete:false};
  const actors=[];
  let plan,uiClosed=false,loginUncertain=false;
  const settled=async()=>uiClosed && !loginUncertain && await transportSettled()===true;
  try {
    await lock.writeFile(JSON.stringify({runId,pid:process.pid}));await lock.sync();
    if(await preflight()!==true)throw Error("preflight_failed");
    for(let index=0;index<2;index++) {
      const authUserId=randomUUID();
      const record={runId,profileId:randomUUID(),authUserId,email:`deep-link-${authUserId}@example.invalid`,
        countryCode:"240",phone:`99${randomInt(100000000,1000000000)}`,
        password:randomBytes(24).toString("base64url"),state:{sessions:[]}};
      const journal=await createRecoveryJournal({directory:privateDirectory,record});
      actors.push({record,journal});
      await createDeepLinkProfile({client,journal,record,password:record.password,adminRequest});
    }
    const actor=actors[0];
    const ticket={runId,profileId:actor.record.profileId,authUserId:actor.record.authUserId,
      purpose:"deep_link",clientInstanceId:randomUUID()};
    const durable=await actor.journal.read();durable.state.sessions.push(ticket);await actor.journal.checkpoint(durable.state);
    let session;
    loginUncertain=true;
    try {session=await loginDeepLinkSession({client,journal:actor.journal,record:actor.record,ticket,
      password:actor.record.password,backendUrl,publicKey,fetchImpl});loginUncertain=false;}
    catch(error){
      // No receipt is not permission to retry or infer that the remote POST ended.
      const current=await actor.journal.read();const entry=current.state.sessions[0];
      loginUncertain=entry?.requestStarted===true && !(entry.authSessionId && entry.webSessionId) && entry.noSession!==true;
      throw error;
    }
    plan={runId,ownerId:actor.record.profileId,peerId:actors[1].record.profileId,
      uniqueKey:`quata-deep-link-${runId}`,messageKey:`quata-deep-link-message-${runId}`,body:`Deep link ${runId}`};
    const threadState=await actor.journal.read();threadState.state.threadPlan=plan;await actor.journal.checkpoint(threadState.state);
    const target=await seedDeepLinkThread({client,journal:actor.journal,plan});
    report.observation=await ui.run({session,clientInstanceId:ticket.clientInstanceId,target,body:plan.body});
    report.status=report.observation?.passed===true?"passed":"failed";
  } catch {report.status="failed";}
  finally {
    try {
    try {await ui.close();uiClosed=true;} catch {report.uiCloseFailed=true;}
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
          if(current.state.profileCreationStarted)await retireDeepLinkProfile({client,journal:actor.journal,record:actor.record,operationsSettled:settled});
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
