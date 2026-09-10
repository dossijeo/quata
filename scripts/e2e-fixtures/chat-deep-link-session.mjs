import {recordRecoverySession} from "./recovery-session-receipt.mjs";
import {createRecoveryBackend} from "./recovery-backend.mjs";

// The caller holds an exclusive run lock across login, UI and cleanup, and creates
// the private journal with this ticket BEFORE calling login.
// No retry after requestStarted: an absent response does not prove no session.
export async function loginDeepLinkSession({client,journal,record,ticket,password,backendUrl,publicKey,
  fetchImpl=fetch,requestLogin=fetchImpl,recordReceipt=recordRecoverySession}) {
  // A platform adapter may execute the single login through the product UI and
  // return its actual HTTP response. Receipt verification keeps its own transport.
  // Ownership audit and durable requestStarted still precede that callback.
  if(typeof requestLogin!=="function")throw Error("deep_link_login_invalid_transport");
  const root = new URL(backendUrl);
  if (root.protocol !== "https:" || root.username || root.password || root.pathname !== "/" || root.search || root.hash) {
    throw Error("deep_link_login_invalid_backend");
  }
  const durable = await journal.read();
  const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if (["runId","profileId","authUserId"].some(key => !uuid.test(record[key]) || durable[key] !== record[key] || ticket[key] !== record[key]) ||
      ["countryCode","phone"].some(key => typeof record[key] !== "string" || !/^[0-9]+$/.test(record[key]) || durable[key] !== record[key])) {
    throw Error("deep_link_login_identity_mismatch");
  }
  const matches = durable.state.sessions.filter(entry => entry.clientInstanceId === ticket.clientInstanceId);
  if (matches.length !== 1 || ["runId","profileId","authUserId","purpose"].some(key => matches[0][key] !== ticket[key]) ||
      ticket.kind !== undefined || ticket.ticketId !== undefined || matches[0].kind !== undefined || matches[0].ticketId !== undefined ||
      typeof ticket.clientInstanceId !== "string" || ticket.clientInstanceId.length < 8 ||
      matches[0].requestStarted || matches[0].noSession !== undefined || matches[0].authSessionId || matches[0].webSessionId) {
    throw Error("deep_link_login_ticket_unavailable");
  }
  // web_login can reconcile Auth credentials internally. Use only an exclusively
  // owned disposable identity, never an existing account merely lacking sessions.
  let actor;
  try {
    actor = await client.query(`select
      (u.raw_app_meta_data->'quata_e2e'->>'unit' = 'FLOW-DEEP-LINKS'
        and u.raw_app_meta_data->'quata_e2e'->>'run_id' = $3) as owned,
      (p.account_status = 'active'
        and (select count(*) from public.community_profiles q where q.auth_user_id=u.id) = 1) as unique_active,
      (p.phone_local = $5 and regexp_replace(coalesce(nullif(p.country_code,''),p.code,''),'[^0-9]','','g') = $4
        and (select count(*) from public.community_profiles q
          where q.phone_local=$5 or q.phone_normalized=$5 or q.telefono=$5) = 1) as phone_matches,
      (not exists(select 1 from auth.sessions s where s.user_id=u.id)
        and not exists(select 1 from public.web_client_sessions w where w.auth_user_id=u.id and w.revoked_at is null)) as no_sessions
      from public.community_profiles p join auth.users u on u.id=p.auth_user_id
      where p.id=$1::uuid and u.id=$2::uuid`, [record.profileId,record.authUserId,record.runId,record.countryCode,record.phone]);
  } catch { throw Error("deep_link_login_actor_audit_failed"); }
  if (actor.rowCount !== 1 || ["owned","unique_active","phone_matches","no_sessions"].some(key=>actor.rows?.[0]?.[key] !== true)) {
    throw Error("deep_link_login_requires_exclusive_fixture");
  }
  matches[0].requestStarted = true;
  await journal.checkpoint(durable.state);
  let response, body;
  try {
    response = await requestLogin(new URL("/functions/v1/quata-auth-bridge", root), {
      method:"POST", headers:{apikey:publicKey,"content-type":"application/json"},
      body:JSON.stringify({action:"web_login",profile_id:record.profileId,country_code:record.countryCode,phone_local:record.phone,
        password,client_instance_id:ticket.clientInstanceId}), signal:AbortSignal.timeout(15000),
    });
    body = await response.json();
  } catch { throw Error("deep_link_login_response_uncertain"); }
  const received = await journal.read();
  const entry = received.state.sessions.find(item => item.clientInstanceId === ticket.clientInstanceId);
  if (response.status === 401 && body?.error === "invalid_credentials") {
    entry.noSession = true;
    await journal.checkpoint(received.state);
    throw Error("deep_link_login_invalid_credentials");
  }
  // Preserve even an incomplete/error response privately for reconciliation.
  // Never emit its content in errors, stdout or a public evidence report.
  entry.privateLoginResponse = {status:response.status,body};
  await journal.checkpoint(received.state);
  if (response.status !== 200) throw Error("deep_link_login_unresolved_response");
  await recordReceipt({client,journal,ticket,backendUrl,publicKey,fetchImpl,
    accessToken:body?.session?.access_token,webSessionToken:body?.web_session?.token});
  if (body?.profile?.id !== record.profileId || typeof body.session?.refresh_token !== "string" ||
      !body.session.refresh_token || !Number.isFinite(body.session.expires_at)) {
    throw Error("deep_link_login_invalid_ui_session");
  }
  return {profileId:record.profileId,accessToken:body.session.access_token,refreshToken:body.session.refresh_token,
    expiresAt:body.session.expires_at,webSessionToken:body.web_session.token};
}

// Uses exact verified receipts. The caller must settle browser activity first.
// An unresolved ticket fails closed and retains the journal for reconciliation.
export async function revokeDeepLinkSessions({client,journal,record,backendUrl,publicKey,operationsSettled}) {
  if (typeof operationsSettled !== "function" || await operationsSettled() !== true) {
    throw Error("deep_link_session_operations_unsettled");
  }
  const backend = createRecoveryBackend({client,journal,record,backendUrl,publicKey,pageOperationsSettled:operationsSettled});
  const current = await journal.read();
  await backend.revokeSessions(current.state.sessions);
  if (!await backend.sessionsClean(current.state.sessions)) throw Error("deep_link_sessions_not_clean");
  return {sessions:true};
}
