import { recordRecoverySession } from "./recovery-session-receipt.mjs";

// One login attempt for one disposable ACCOUNT-LIFECYCLE actor. The durable
// requestStarted marker makes an uncertain HTTP response non-repeatable.
export async function loginAccountLifecycleSession({ client, journal, record, ticket, password, backendUrl,
  publicKey, fetchImpl = fetch, recordReceipt = recordRecoverySession }) {
  const root = new URL(backendUrl);
  if (root.protocol !== "https:" || root.username || root.password || root.pathname !== "/" || root.search || root.hash) {
    throw new Error("account_lifecycle_login_invalid_backend");
  }
  const value = await journal.read();
  const entry = value.state.sessions?.find((item) => item.clientInstanceId === ticket.clientInstanceId);
  if (!entry || value.state.sessions.filter((item) => item.clientInstanceId === ticket.clientInstanceId).length !== 1 ||
      ["runId", "profileId", "authUserId", "purpose"].some((key) => entry[key] !== ticket[key]) ||
      ["runId", "profileId", "authUserId"].some((key) => value[key] !== record[key] || ticket[key] !== record[key]) ||
      entry.requestStarted || entry.authSessionId || entry.webSessionId || typeof password !== "string" || password.length < 20) {
    throw new Error("account_lifecycle_login_ticket_unavailable");
  }
  const actor = await client.query(`select
    (u.raw_app_meta_data->'quata_e2e'->>'unit'='ACCOUNT-LIFECYCLE'
      and u.raw_app_meta_data->'quata_e2e'->>'run_id'=$3) as owned,
    (p.account_status='active' and (select count(*) from public.community_profiles q where q.auth_user_id=u.id)=1) as unique_active,
    (p.phone_local=$5 and regexp_replace(coalesce(nullif(p.country_code,''),p.code,''),'[^0-9]','','g')=$4) as phone_matches,
    (not exists(select 1 from auth.sessions s where s.user_id=u.id)
      and not exists(select 1 from public.web_client_sessions w where w.auth_user_id=u.id and w.revoked_at is null)) as no_sessions
    from public.community_profiles p join auth.users u on u.id=p.auth_user_id
    where p.id=$1::uuid and u.id=$2::uuid`,
  [record.profileId, record.authUserId, record.runId, record.countryCode, record.phone]);
  if (actor.rowCount !== 1 || ["owned", "unique_active", "phone_matches", "no_sessions"]
    .some((key) => actor.rows?.[0]?.[key] !== true)) {
    throw new Error("account_lifecycle_login_requires_exclusive_fixture");
  }
  entry.requestStarted = true;
  await journal.checkpoint(value.state);
  let response;
  let body;
  try {
    response = await fetchImpl(new URL("/functions/v1/quata-auth-bridge", root), {
      method: "POST",
      headers: { apikey: publicKey, "content-type": "application/json" },
      body: JSON.stringify({ action: "web_login", profile_id: record.profileId, country_code: record.countryCode,
        phone_local: record.phone, password, client_instance_id: ticket.clientInstanceId }),
      signal: AbortSignal.timeout(15_000),
    });
    body = await response.json();
  } catch {
    throw new Error("account_lifecycle_login_response_uncertain");
  }
  const received = await journal.read();
  const receivedEntry = received.state.sessions.find((item) => item.clientInstanceId === ticket.clientInstanceId);
  receivedEntry.privateLoginResponse = { status: response.status, body };
  await journal.checkpoint(received.state);
  if (response.status !== 200) throw new Error("account_lifecycle_login_unresolved_response");
  await recordReceipt({ client, journal, ticket, backendUrl, publicKey, fetchImpl,
    accessToken: body?.session?.access_token, webSessionToken: body?.web_session?.token });
  if (body?.profile?.id !== record.profileId || typeof body.session?.refresh_token !== "string" ||
      !body.session.refresh_token || !Number.isFinite(body.session.expires_at)) {
    throw new Error("account_lifecycle_login_invalid_session");
  }
  return { profileId: record.profileId, authUserId: record.authUserId, accessToken: body.session.access_token,
    refreshToken: body.session.refresh_token, expiresAt: body.session.expires_at,
    webSessionToken: body.web_session.token };
}
