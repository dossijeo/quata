import {randomUUID} from "node:crypto";
import {auditRecoveryActor} from "./recovery-backend-audit.mjs";
import {recordRecoverySession,createRecoveryWebActorVerifier} from "./recovery-session-receipt.mjs";

// Dedicated, serialized DB connection; statements must have a coordinator timeout.
// No producer here: only the product adapter may configure/save the temporary secret.
export function createRecoveryBackend({client,journal,record,backendUrl,publicKey,pageOperationsSettled,
  confirmPreviousRunSettled,fetchImpl=fetch}) {
  const root=new URL(backendUrl);
  if(root.protocol!=="https:" || root.username || root.password || root.pathname!=="/" || root.search || root.hash ||
      typeof pageOperationsSettled!=="function")throw Error("recovery_backend_configuration_invalid");
  let uncertain=false,pending=0;
  const sameActor=value=>["runId","profileId","authUserId"].every(key=>value?.[key]===record[key]);
  const post=async payload=>{
    pending++;
    try {
      const response=await fetchImpl(new URL("/functions/v1/quata-auth-bridge",root),{
        method:"POST",headers:{apikey:publicKey,"content-type":"application/json"},body:JSON.stringify(payload),signal:AbortSignal.timeout(15000)});
      return {status:response.status,body:await response.json()};
    } catch {uncertain=true;throw Error("recovery_backend_transport_uncertain");}
    finally {pending--;}
  };
  const identity={profile_id:record.profileId,country_code:record.countryCode,phone_local:record.phone};
  const durableTicket=async ticket=>{
    if(!sameActor(ticket))throw Error("recovery_ticket_actor_mismatch");
    const durable=await journal.read();
    if(!sameActor(durable))throw Error("recovery_journal_actor_mismatch");
    const matches=durable.state.sessions.filter(item=>item.clientInstanceId===ticket.clientInstanceId);
    if(matches.length!==1 || !sameActor(matches[0]) || matches[0].purpose!==ticket.purpose)throw Error("recovery_ticket_missing");
    if(matches[0].noSession===true && (matches[0].authSessionId || matches[0].webSessionId))throw Error("recovery_ticket_conflicting_receipt");
    return {durable,entry:matches[0]};
  };
  const receipt=async ticket=>{
    const {entry}=await durableTicket(ticket);
    if(!entry.authSessionId || !entry.webSessionId)throw Error("recovery_ticket_receipt_missing");
    return entry;
  };
  const audit=async(value,tickets)=>{
    if(!sameActor(value))return false;
    const ids=[];
    for(const ticket of tickets){const {entry}=await durableTicket(ticket);if(entry.authSessionId)ids.push(entry.authSessionId);}
    return (await auditRecoveryActor({client,profileId:record.profileId,authUserId:record.authUserId,ownedAuthSessionIds:ids})).eligible;
  };
  return Object.freeze({
    verifyActor:createRecoveryWebActorVerifier({client,journal,backendUrl,publicKey,fetchImpl}),
    auditRecoverySessions:audit,
    async preflight(value){
      if(!sameActor(value) || !(await audit(value,[])))return false;
      const result=await post({action:"update_recovery_secret",version:1});
      return result.status===401 && result.body.error==="authentication_required";
    },
    async planSession(value){
      if(!sameActor(value) || !["producer","temporary_verification","original_verification"].includes(value.purpose))throw Error("recovery_session_plan_invalid");
      return {runId:record.runId,profileId:record.profileId,authUserId:record.authUserId,purpose:value.purpose,clientInstanceId:randomUUID()};
    },
    async secretMatchesPlanned(value){
      if(!sameActor(value))return false;
      const result=await client.query(`select (secret_question=$3 and secret_answer=$4) as matches
        from public.community_profiles where id=$1::uuid and auth_user_id=$2::uuid`,
        [record.profileId,record.authUserId,record.temporaryQuestion,record.temporaryAnswer]);
      return result.rowCount===1 && result.rows[0].matches===true;
    },
    async readRecoveryQuestion(value){
      if(!sameActor(value))throw Error("recovery_actor_mismatch");
      const result=await post({action:"recovery_question",...identity});
      if(result.status!==200 || Object.keys(result.body).join(",")!=="secret_question" || typeof result.body.secret_question!=="string")throw Error("recovery_question_read_failed");
      return result.body;
    },
    async verifyLogin(password,ticket){
      const {durable,entry:planned}=await durableTicket(ticket);
      if(planned.requestStarted || planned.noSession!==undefined || planned.authSessionId || planned.webSessionId)throw Error("recovery_ticket_already_resolved");
      planned.requestStarted=true;await journal.checkpoint(durable.state);ticket.requestStarted=true;
      const result=await post({action:"web_login",...identity,password,client_instance_id:ticket.clientInstanceId});
      if(result.status===401 && result.body.error==="invalid_credentials"){
        const {durable,entry}=await durableTicket(ticket);entry.noSession=true;
        await journal.checkpoint(durable.state);ticket.noSession=true;return false;
      }
      if(result.status!==200)throw Error("recovery_login_failed");
      return recordRecoverySession({client,journal,ticket,backendUrl,publicKey,fetchImpl,
        accessToken:result.body.session?.access_token,webSessionToken:result.body.web_session?.token});
    },
    async restorePassword(value){
      if(!sameActor(value))return false;
      const result=await post({action:"reset_password",...identity,secret_answer:record.temporaryAnswer,new_password:record.originalPassword});
      return result.status===200 && result.body.ok===true;
    },
    async confirmOperationsSettled(){return !uncertain && pending===0 && (await pageOperationsSettled())===true;},
    async confirmInterruptedRunSettled(value){
      return sameActor(value) && typeof confirmPreviousRunSettled==="function" && (await confirmPreviousRunSettled(value))===true;
    },
    async revokeSessions(tickets){
      let incomplete=false;
      for(const ticket of tickets){
        let entry;
        try {const value=await durableTicket(ticket);if(value.entry.noSession===true)continue;entry=await receipt(ticket);}
        catch {incomplete=true;continue;}
        await client.query("begin");
        try {
          await client.query(`update public.web_client_sessions set revoked_at=coalesce(revoked_at,now())
            where id=$1::uuid and profile_id=$2::uuid and auth_user_id=$3::uuid and client_instance_id=$4`,
            [entry.webSessionId,record.profileId,record.authUserId,entry.clientInstanceId]);
          await client.query(`delete from auth.sessions where id=$1::uuid and user_id=$2::uuid`,[entry.authSessionId,record.authUserId]);
          await client.query("commit");
        } catch {await client.query("rollback").catch(()=>{});incomplete=true;}
      }
      if(incomplete)throw Error("recovery_session_cleanup_receipt_missing");
    },
    async sessionsClean(tickets){
      for(const ticket of tickets){
        const {entry}=await durableTicket(ticket);if(entry.noSession===true)continue;
        const verified=await receipt(ticket);
        const result=await client.query(`select
          (select count(*)::int from auth.sessions where id=$1::uuid and user_id=$2::uuid) as auth_count,
          (select count(*)::int from public.web_client_sessions where id=$3::uuid and profile_id=$4::uuid
            and auth_user_id=$2::uuid and client_instance_id=$5 and revoked_at is null) as web_count`,
          [verified.authSessionId,record.authUserId,verified.webSessionId,record.profileId,verified.clientInstanceId]);
        if(result.rows[0]?.auth_count!==0 || result.rows[0]?.web_count!==0)return false;
      }
      return !uncertain && pending===0;
    },
  });
}
