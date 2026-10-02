export async function fetchNoRedirect(url, init = {}, fetchImpl = globalThis.fetch) {
  return fetchImpl(url, {
    ...init,
    redirect: "error",
    signal: init.signal ?? AbortSignal.timeout(20_000),
  });
}

export async function jsonRequestNoRedirect(url, init, failure, fetchImpl = globalThis.fetch) {
  let response;
  try {
    response = await fetchNoRedirect(url, init, fetchImpl);
  } catch {
    throw new Error(`${failure}_network_failed`);
  }
  if (!response.ok) throw new Error(`${failure}_http_${response.status}`);
  try {
    return await response.json();
  } catch {
    throw new Error(`${failure}_invalid_json`);
  }
}

export async function authenticateExactChatActor({
  backend,
  baseUrl,
  key,
  credentials,
  request = jsonRequestNoRedirect,
  captureCustody,
}) {
  const phoneEmail = `${digitsOnly(credentials.country_code)}${digitsOnly(credentials.phone)}@phone.quata.app`;
  const auth = backend
    ? await request(`${baseUrl}/auth/v1/token?grant_type=password`, {
        method: "POST",
        headers: { apikey: key, "content-type": "application/json" },
        body: JSON.stringify({ email: phoneEmail, password: credentials.password }),
      }, "exact_chat_direct_auth")
    : await request(`${baseUrl}/functions/v1/quata-auth-bridge`, {
        method: "POST",
        headers: { apikey: key, "content-type": "application/json" },
        body: JSON.stringify({
          action: "web_login",
          country_code: credentials.country_code,
          phone_local: credentials.phone,
          password: credentials.password,
          client_instance_id: `shell-process-death-${Date.now()}`,
        }),
      }, "exact_chat_auth");
  const accessToken = backend ? auth?.access_token : auth?.session?.access_token;
  const refreshToken = backend ? auth?.refresh_token : auth?.session?.refresh_token;
  const webSessionToken = backend ? null : auth?.web_session?.token;
  captureCustody({
    accessToken: accessToken ?? null,
    refreshToken: refreshToken ?? null,
    webSessionToken: webSessionToken ?? null,
  });
  if (!accessToken || !refreshToken || (!backend && !webSessionToken)) {
    throw new Error("exact_chat_auth_response_invalid");
  }
  const profileId = backend
    ? await request(`${baseUrl}/rest/v1/rpc/quata_chat_auth_profile_id`, {
        method: "POST",
        headers: { apikey: key, authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
        body: "{}",
      }, "exact_chat_profile_resolver")
    : auth?.profile?.id;
  if (!profileId) throw new Error("exact_chat_auth_response_invalid");
  return {
    auth,
    accessToken,
    refreshToken,
    webSessionToken,
    profileId,
    phoneEmail,
  };
}

function digitsOnly(value) {
  return String(value ?? "").replace(/\D/g, "");
}
