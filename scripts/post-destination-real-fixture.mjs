import { createHash, randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";

export async function loadPublicBackendConfig() {
  const source = await readFile(
    new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url),
    "utf8",
  );
  const url = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  const key = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(source)?.[1];
  if (!url || !key) throw new Error("missing_public_supabase_configuration");
  return { url, key };
}

export async function loginDestinationActor(backend, credentials, clientLabel) {
  const clientInstanceId = `${clientLabel}-${randomUUID()}`;
  const response = await fetch(`${backend.url}/functions/v1/quata-auth-bridge`, {
    method: "POST",
    headers: {
      apikey: backend.key,
      "content-type": "application/json",
      "x-client-info": clientLabel,
    },
    body: JSON.stringify({
      action: "web_login",
      country_code: String(credentials.country_code),
      phone_local: localPhone(credentials.country_code, credentials.phone),
      password: String(credentials.password),
      client_instance_id: clientInstanceId,
    }),
    signal: AbortSignal.timeout(30_000),
  }).catch(() => null);
  if (!response) throw new Error("destination_fixture_login_network_failure");
  const payload = await response.json().catch(() => null);
  if (!response.ok) throw new Error(`destination_fixture_login_http_${response.status}`);
  const session = payload?.session;
  const profile = payload?.profile;
  const webSession = payload?.web_session;
  if (typeof session?.access_token !== "string" || typeof session?.refresh_token !== "string") {
    throw new Error("destination_fixture_login_invalid_session");
  }
  if (typeof webSession?.token !== "string" || typeof profile?.id !== "string") {
    throw new Error("destination_fixture_login_invalid_profile");
  }
  return {
    accessToken: session.access_token,
    refreshToken: session.refresh_token,
    webSessionToken: webSession.token,
    userId: profile.id,
    expiresAt: Number(session.expires_at ?? Math.floor(Date.now() / 1000) + Number(session.expires_in ?? 3600)),
    clientInstanceId,
  };
}

export async function retireDestinationActorSession(backend, session, fetchImpl = fetch) {
  if (!session?.accessToken || !session?.refreshToken || !session?.webSessionToken) {
    throw new Error("destination_fixture_cleanup_session_required");
  }
  const cleanup = {
    webSessionRevoked: false,
    webSessionProbe: null,
    refreshTokenRejected: false,
    refreshTokenProbe: null,
  };
  const failures = [];

  try {
    const webLogout = await fetchImpl(`${backend.url}/functions/v1/quata-web-push`, {
      method: "POST",
      headers: {
        apikey: backend.key,
        authorization: `Bearer ${session.accessToken}`,
        "content-type": "application/json",
        "x-client-info": "quata-post-destination-cleanup",
        "x-quata-web-session": session.webSessionToken,
      },
      body: JSON.stringify({ action: "logout" }),
      signal: AbortSignal.timeout(30_000),
    }).catch(() => null);
    if (!webLogout) throw new Error("destination_fixture_web_session_cleanup_network_failure");
    if (!webLogout.ok) throw new Error(`destination_fixture_web_session_cleanup_http_${webLogout.status}`);

    const webProbe = await fetchImpl(`${backend.url}/functions/v1/quata-web-push`, {
      method: "POST",
      headers: {
        apikey: backend.key,
        authorization: `Bearer ${session.accessToken}`,
        "content-type": "application/json",
        "x-client-info": "quata-post-destination-cleanup-probe",
        "x-quata-web-session": session.webSessionToken,
      },
      body: JSON.stringify({ action: "logout" }),
      signal: AbortSignal.timeout(30_000),
    }).catch(() => null);
    if (!webProbe) throw new Error("destination_fixture_web_session_cleanup_probe_network_failure");
    const webProbePayload = await webProbe.json().catch(() => null);
    if (webProbe.status !== 401 || webProbePayload?.error !== "invalid_web_session") {
      throw new Error("destination_fixture_web_session_cleanup_unverified");
    }
    cleanup.webSessionRevoked = true;
    cleanup.webSessionProbe = { status: 401, error: "invalid_web_session" };
  } catch (error) {
    failures.push(error instanceof Error ? error.message : "destination_fixture_web_session_cleanup_unknown_failure");
  }

  try {
    const authLogout = await fetchImpl(`${backend.url}/auth/v1/logout?scope=local`, {
      method: "POST",
      headers: { apikey: backend.key, authorization: `Bearer ${session.accessToken}` },
      signal: AbortSignal.timeout(30_000),
    }).catch(() => null);
    if (!authLogout) throw new Error("destination_fixture_auth_cleanup_network_failure");
    if (!authLogout.ok) throw new Error(`destination_fixture_auth_cleanup_http_${authLogout.status}`);

    const refreshProbe = await fetchImpl(`${backend.url}/auth/v1/token?grant_type=refresh_token`, {
      method: "POST",
      headers: { apikey: backend.key, "content-type": "application/json" },
      body: JSON.stringify({ refresh_token: session.refreshToken }),
      signal: AbortSignal.timeout(30_000),
    }).catch(() => null);
    if (!refreshProbe) throw new Error("destination_fixture_auth_cleanup_probe_network_failure");
    const refreshProbePayload = await refreshProbe.json().catch(() => null);
    if (refreshProbe.status !== 400 || refreshProbePayload?.error_code !== "refresh_token_not_found") {
      throw new Error("destination_fixture_auth_cleanup_unverified");
    }
    cleanup.refreshTokenRejected = true;
    cleanup.refreshTokenProbe = { status: 400, errorCode: "refresh_token_not_found" };
  } catch (error) {
    failures.push(error instanceof Error ? error.message : "destination_fixture_auth_cleanup_unknown_failure");
  }

  if (failures.length > 0) {
    const error = new Error(`destination_fixture_cleanup_failed:${failures.join(",")}`);
    error.cleanup = cleanup;
    throw error;
  }
  return cleanup;
}

export async function selectRealAlternateDestination(backend, session) {
  const headers = {
    apikey: backend.key,
    authorization: `Bearer ${session.accessToken}`,
    accept: "application/json",
  };
  const memberships = await getJson(
    `${backend.url}/rest/v1/community_members?${new URLSearchParams({
      select: "wall_id",
      profile_id: `eq.${session.userId}`,
    })}`,
    headers,
    "destination_fixture_memberships",
  );
  const walls = await getJson(
    `${backend.url}/rest/v1/community_walls_stats?${new URLSearchParams({
      select: "id,slug,name,city,description",
      is_active: "eq.true",
      order: "sort_order.asc,chat_last_at.desc,created_at.desc",
      limit: "250",
    })}`,
    headers,
    "destination_fixture_walls",
  );
  if (!Array.isArray(memberships) || !Array.isArray(walls)) throw new Error("destination_fixture_invalid_rows");
  const memberIds = [...new Set(memberships.map((row) => row?.wall_id).filter(nonEmptyString))];
  const memberSet = new Set(memberIds);
  const eligible = walls
    .filter((row) => nonEmptyString(row?.id))
    .filter((row) => memberSet.size === 0 || memberSet.has(row.id));
  if (eligible.length < 2) throw new Error(`destination_fixture_requires_multiple:${eligible.length}`);
  return {
    eligibleCount: eligible.length,
    defaultWallId: eligible[0].id,
    alternateWallId: eligible[1].id,
  };
}

export function destinationFixtureSummary(fixture) {
  return {
    eligibleCount: fixture.eligibleCount,
    defaultWallIdSha256: sha256(fixture.defaultWallId),
    alternateWallIdSha256: sha256(fixture.alternateWallId),
  };
}

async function getJson(url, headers, label) {
  const response = await fetch(url, { headers, signal: AbortSignal.timeout(30_000) }).catch(() => null);
  if (!response) throw new Error(`${label}_network_failure`);
  if (!response.ok) throw new Error(`${label}_http_${response.status}`);
  return response.json().catch(() => { throw new Error(`${label}_invalid_json`); });
}

function localPhone(countryCode, phone) {
  const country = String(countryCode ?? "").replace(/\D/g, "");
  const digits = String(phone ?? "").replace(/\D/g, "");
  return digits.startsWith(country) ? digits.slice(country.length) : digits;
}

function nonEmptyString(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function sha256(value) {
  return createHash("sha256").update(String(value)).digest("hex");
}
