import {createHash} from "node:crypto";

const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// The caller holds the run lock and pauses the actual product refresh until this
// callback is invoked. It must settle network activity before any cleanup. This
// function does not retry, retire sessions, or replace their original receipts.
export async function observeDeepLinkRefresh({client,journal,record,ticket,session,
  backendUrl,publicKey,requestRefresh,fetchImpl=fetch}) {
  const root=new URL(backendUrl);
  if(root.protocol!=="https:" || root.username || root.password || root.pathname!=="/" || root.search || root.hash ||
      typeof requestRefresh!=="function" || typeof session?.refreshToken!=="string" || !session.refreshToken ||
      typeof session?.webSessionToken!=="string" || !session.webSessionToken || session.profileId!==record.profileId) {
    throw Error("deep_link_refresh_invalid_configuration");
  }
  const durable=await journal.read();
  const matches=durable.state.sessions.filter(entry=>entry.clientInstanceId===ticket.clientInstanceId);
  const fields=["runId","profileId","authUserId","authSessionId","webSessionId"];
  if(typeof ticket.clientInstanceId!=="string" || ticket.clientInstanceId.length<8 ||
      matches.length!==1 || fields.some(key=>!uuid.test(ticket[key]) || matches[0][key]!==ticket[key]) ||
      ["runId","profileId","authUserId"].some(key=>durable[key]!==record[key] || record[key]!==ticket[key]) ||
      matches[0].purpose!==ticket.purpose || matches[0].kind!==undefined || ticket.kind!==undefined ||
      matches[0].noSession!==undefined || matches[0].refreshAttempt!==undefined) {
    throw Error("deep_link_refresh_ticket_unavailable");
  }
  const login=matches[0].privateLoginResponse;
  if(login?.status!==200 || login.body?.profile?.id!==record.profileId ||
      login.body?.session?.refresh_token!==session.refreshToken ||
      login.body?.web_session?.token!==session.webSessionToken) {
    throw Error("deep_link_refresh_credentials_mismatch");
  }
  // Audit exclusive fixture ownership and the exact original session before the
  // refresh can leave the browser. No credentials appear in query text/errors.
  const tokenHash=createHash("sha256").update(session.webSessionToken).digest("hex");
  let original;
  try {
    original=await client.query(`select s.id as auth_session_id,w.id as web_session_id
      from auth.sessions s join auth.users u on u.id=s.user_id
      join public.community_profiles p on p.auth_user_id=u.id
      join public.web_client_sessions w on w.profile_id=p.id and w.auth_user_id=u.id
      where s.id=$1::uuid and u.id=$2::uuid and p.id=$3::uuid
        and w.id=$4::uuid and w.client_instance_id=$5 and w.token_hash=$6 and w.revoked_at is null
        and u.raw_app_meta_data->'quata_e2e'->>'unit'='FLOW-DEEP-LINKS'
        and u.raw_app_meta_data->'quata_e2e'->>'run_id'=$7`,
      [ticket.authSessionId,ticket.authUserId,ticket.profileId,ticket.webSessionId,ticket.clientInstanceId,tokenHash,ticket.runId]);
  } catch {throw Error("deep_link_refresh_actor_unverified");}
  const exact=result=>result?.rowCount===1 && result.rows?.[0]?.auth_session_id===ticket.authSessionId &&
    result.rows?.[0]?.web_session_id===ticket.webSessionId;
  if(!exact(original))throw Error("deep_link_refresh_actor_unverified");
  matches[0].refreshAttempt={requestStarted:true};
  await journal.checkpoint(durable.state);
  let response,body;
  try {
    response=await requestRefresh(new URL("/auth/v1/token?grant_type=refresh_token",root),{
      method:"POST",headers:{apikey:publicKey,"content-type":"application/json"},
      body:JSON.stringify({refresh_token:session.refreshToken}),signal:AbortSignal.timeout(15000),
    });
    body=await response.json();
  } catch {throw Error("deep_link_refresh_response_uncertain");}
  const received=await journal.read();
  const entry=received.state.sessions.find(item=>item.clientInstanceId===ticket.clientInstanceId);
  entry.refreshAttempt.privateResponse={status:response.status,body};
  await journal.checkpoint(received.state);
  // Non-success stays unresolved. Revoked-session acceptance uses a separate
  // pre-revoked lifecycle; an arbitrary error here is not proof of revocation.
  if(response.status!==200)throw Error("deep_link_refresh_unresolved_response");
  try {
    if(typeof body?.access_token!=="string" || typeof body.refresh_token!=="string" || !body.refresh_token ||
        !Number.isFinite(body.expires_at))throw Error();
    const verified=await fetchImpl(new URL("/auth/v1/user",root),{
      headers:{apikey:publicKey,Authorization:`Bearer ${body.access_token}`},signal:AbortSignal.timeout(10000),
    });
    if(!verified.ok)throw Error();
    const actor=await verified.json();
    const claims=JSON.parse(Buffer.from(body.access_token.split(".")[1],"base64url").toString("utf8"));
    if(actor.id!==ticket.authUserId || claims.sub!==ticket.authUserId || claims.session_id!==ticket.authSessionId)throw Error();
    // Recheck the same exact receipt after Auth validation. Rotation of tokens
    // is allowed; a different session identity is never silently adopted.
    const current=await client.query(`select s.id as auth_session_id,w.id as web_session_id
      from auth.sessions s join public.community_profiles p on p.auth_user_id=s.user_id
      join public.web_client_sessions w on w.profile_id=p.id and w.auth_user_id=s.user_id
      where s.id=$1::uuid and s.user_id=$2::uuid and p.id=$3::uuid
        and w.id=$4::uuid and w.client_instance_id=$5 and w.token_hash=$6 and w.revoked_at is null`,
      [ticket.authSessionId,ticket.authUserId,ticket.profileId,ticket.webSessionId,ticket.clientInstanceId,tokenHash]);
    if(!exact(current))throw Error();
  } catch {throw Error("deep_link_refresh_receipt_unverified");}
  const completed=await journal.read();
  completed.state.sessions.find(item=>item.clientInstanceId===ticket.clientInstanceId).refreshAttempt.verified=true;
  await journal.checkpoint(completed.state);
  return {verified:true};
}
