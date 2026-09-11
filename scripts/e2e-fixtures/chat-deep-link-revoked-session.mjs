import {createHash} from "node:crypto";
import {revokeDeepLinkSessions} from "./chat-deep-link-session.mjs";
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

async function identity({journal,record,ticket,session}) {
  const durable=await journal.read();
  const matches=durable.state.sessions.filter(s=>s.clientInstanceId===ticket.clientInstanceId);
  const fields=["runId","profileId","authUserId","authSessionId","webSessionId"];
  if(typeof ticket.clientInstanceId!=="string"||ticket.clientInstanceId.length<8||matches.length!==1||durable.state.sessions.length!==1||
      fields.some(k=>!uuid.test(ticket[k])||matches[0][k]!==ticket[k])||
      ["runId","profileId","authUserId"].some(k=>durable[k]!==record[k]||record[k]!==ticket[k])||
      matches[0].purpose!==ticket.purpose||ticket.kind!==undefined||matches[0].kind!==undefined||
      matches[0].noSession!==undefined||session?.profileId!==record.profileId)throw Error("deep_link_revoked_identity_mismatch");
  const entry=matches[0],login=entry.privateLoginResponse;
  if(login?.status!==200||login.body?.profile?.id!==record.profileId||
      typeof session.refreshToken!=="string"||!session.refreshToken||
      typeof session.webSessionToken!=="string"||!session.webSessionToken||
      login.body?.session?.refresh_token!==session.refreshToken||login.body?.web_session?.token!==session.webSessionToken)
    throw Error("deep_link_revoked_credentials_mismatch");
  return {durable,entry};
}

async function audit({client,record,ticket,session},revoked) {
  let result;
  try {
    result=await client.query(`select
      exists(select 1 from auth.users u join public.community_profiles p on p.auth_user_id=u.id
        where u.id=$1::uuid and p.id=$2::uuid and p.account_status='active'
        and u.raw_app_meta_data->'quata_e2e'->>'unit'='FLOW-DEEP-LINKS'
        and u.raw_app_meta_data->'quata_e2e'->>'run_id'=$3) as owned,
      (select count(*)::int from auth.sessions where user_id=$1::uuid) as auth_count,
      exists(select 1 from auth.sessions where id=$4::uuid and user_id=$1::uuid) as exact_auth,
      (select count(*)::int from public.web_client_sessions where auth_user_id=$1::uuid and revoked_at is null) as web_count,
      exists(select 1 from public.web_client_sessions where id=$5::uuid and auth_user_id=$1::uuid
        and profile_id=$2::uuid and client_instance_id=$6 and token_hash=$7
        and (revoked_at is not null)=$8::boolean) as exact_web`,
      [record.authUserId,record.profileId,record.runId,ticket.authSessionId,ticket.webSessionId,ticket.clientInstanceId,
        createHash("sha256").update(session.webSessionToken).digest("hex"),revoked]);
  }catch{throw Error("deep_link_revoked_audit_failed");}
  const row=result.rows?.[0];
  if(result.rowCount!==1||row?.owned!==true||row.exact_web!==true||row.exact_auth!==!revoked||
      row.auth_count!==(revoked?0:1)||row.web_count!==(revoked?0:1))throw Error("deep_link_revoked_audit_failed");
}

// Called before opening any browser context. Intent and exact receipts survive
// a failed transaction/verification; never retry an uncertain revocation here.
export async function prepareRevokedDeepLinkSession(args) {
  if(typeof args.operationsSettled!=="function"||await args.operationsSettled()!==true)
    throw Error("deep_link_revoked_operations_unsettled");
  const {durable,entry}=await identity(args);
  if(entry.revocation!==undefined||entry.refreshAttempt!==undefined)throw Error("deep_link_revoked_ticket_used");
  await audit(args,false);
  entry.revocation={started:true};await args.journal.checkpoint(durable.state);
  await revokeDeepLinkSessions(args);
  await audit(args,true);
  const verified=await identity(args);verified.entry.revocation.verified=true;
  await args.journal.checkpoint(verified.durable.state);
  return {revoked:true};
}

// Separate from a successful renewal: only known Auth rejection codes plus
// verified remote absence count. HTTP errors alone never prove revocation.
export async function observeRevokedDeepLinkRefresh({...args}) {
  const root=new URL(args.backendUrl);
  if(root.protocol!=="https:"||root.username||root.password||root.pathname!=="/"||root.search||root.hash||
      typeof args.requestRefresh!=="function"||typeof args.responseJournaled!=="function")
    throw Error("deep_link_revoked_configuration_invalid");
  const {durable,entry}=await identity(args);
  if(entry.revocation?.verified!==true||entry.refreshAttempt!==undefined)throw Error("deep_link_revoked_ticket_unavailable");
  await audit(args,true);
  entry.refreshAttempt={requestStarted:true,expectedOutcome:"revoked"};await args.journal.checkpoint(durable.state);
  let response,body;
  try {
    response=await args.requestRefresh(new URL("/auth/v1/token?grant_type=refresh_token",root),{
      method:"POST",headers:{apikey:args.publicKey,"content-type":"application/json"},body:JSON.stringify({refresh_token:args.session.refreshToken}),
    });body=await response.json();
  }catch{throw Error("deep_link_revoked_response_uncertain");}
  const received=await identity(args);received.entry.refreshAttempt.privateResponse={status:response.status,body};
  await args.journal.checkpoint(received.durable.state);
  try{await args.responseJournaled();}catch{}
  if(![400,401].includes(response.status)||!["refresh_token_not_found","session_not_found"].includes(body?.error_code))
    throw Error("deep_link_revoked_rejection_unverified");
  await audit(args,true);
  const verified=await identity(args);verified.entry.refreshAttempt.verified=true;
  verified.entry.refreshAttempt.rejectionCode=body.error_code;
  await args.journal.checkpoint(verified.durable.state);
  return {verified:true,rejected:true};
}
