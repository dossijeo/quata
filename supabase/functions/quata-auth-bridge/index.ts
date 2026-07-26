import { createClient } from "npm:@supabase/supabase-js@2";
import {
  constantTimeEqualHex,
  hashRecoveryAnswer,
  hashRegistrationPassword,
  verifyRegistrationPassword,
  recoverySecretPatch,
  registrationPhoneHash,
} from "../_shared/web-registration-security.mjs";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-quata-api-key",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type BridgeRequest = {
  action?: "login" | "web_login" | "recovery_question" | "reset_password" | "update_recovery_secret";
  version?: number;
  profile_id?: string;
  country_code?: string;
  phone?: string;
  phone_local?: string;
  password?: string;
  secret_answer?: string;
  new_password?: string;
  reactivate_deactivated?: boolean;
  client_instance_id?: string;
  secret_question?: string;
};

type CommunityProfile = {
  id: string;
  auth_user_id?: string | null;
  display_name?: string | null;
  nombre?: string | null;
  phone?: string | null;
  phone_normalized?: string | null;
  phone_local?: string | null;
  phone_e164?: string | null;
  country_code?: string | null;
  code?: string | null;
  telefono?: string | null;
  pass_hash?: string | null;
  pass_plain?: string | null;
  avatar_url?: string | null;
  avatar?: string | null;
  barrio?: string | null;
  neighborhood?: string | null;
  secret_question?: string | null;
  secret_answer?: string | null;
  secret_answer_hash?: string | null;
  account_status?: "active" | "deactivated" | null;
  deactivated_at?: string | null;
};

const profileSelect = [
  "id",
  "auth_user_id",
  "display_name",
  "nombre",
  "phone",
  "phone_normalized",
  "phone_local",
  "phone_e164",
  "country_code",
  "code",
  "telefono",
  "pass_hash",
  "pass_plain",
  "avatar_url",
  "avatar",
  "barrio",
  "neighborhood",
  "secret_question",
  "secret_answer",
  "secret_answer_hash",
  "account_status",
  "deactivated_at",
].join(",");

Deno.serve(async (req) => {
  try {
    return await handleRequest(req);
  } catch (error) {
    console.error(JSON.stringify({ event: "auth_bridge_failed", code: "internal_error" }));
    return jsonResponse(
      {
        error: "internal_error",
      },
      500,
    );
  }
});

async function handleRequest(req: Request): Promise<Response> {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return jsonResponse({ error: "method_not_allowed" }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  if (!supabaseUrl) {
    return jsonResponse({ error: "missing_supabase_url" }, 500);
  }

  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || envDictionaryValue("SUPABASE_SECRET_KEYS");
  if (!serviceRoleKey) {
    return jsonResponse({ error: "missing_service_role_key" }, 500);
  }

  const publicKey =
    Deno.env.get("QUATA_SUPABASE_PUBLIC_KEY") ||
    Deno.env.get("SUPABASE_ANON_KEY") ||
    envDictionaryValue("SUPABASE_PUBLISHABLE_KEYS");
  if (!publicKey) {
    return jsonResponse({ error: "missing_public_key" }, 500);
  }

  const expectedBridgeKey = Deno.env.get("QUATA_AUTH_BRIDGE_API_KEY");
  if (expectedBridgeKey) {
    const dedicated = req.headers.get("x-quata-api-key");
    const publishable = req.headers.get("apikey") ||
      req.headers.get("authorization")?.replace(/^Bearer\s+/i, "");
    if (dedicated !== expectedBridgeKey && publishable !== publicKey) {
      return jsonResponse({ error: "invalid_api_key" }, 401);
    }
  }

  let payload: BridgeRequest;
  try {
    payload = await req.json();
  } catch {
    return jsonResponse({ error: "invalid_json" }, 400);
  }

  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { "X-Client-Info": "quata-auth-bridge" } },
  });

  const action = payload.action || "login";
  if (action === "update_recovery_secret") {
    if (payload.version !== 1) return jsonResponse({ error: "unsupported_version" }, 400);
    const bearer = req.headers.get("authorization")?.match(/^Bearer\s+(.+)$/i)?.[1];
    if (!bearer) return jsonResponse({ error: "authentication_required" }, 401);
    const { data: identity, error: identityError } = await admin.auth.getUser(bearer);
    if (identityError || !identity.user) return jsonResponse({ error: "authentication_required" }, 401);
    const pepper = Deno.env.get("QUATA_WEB_REGISTRATION_PEPPER");
    let patch;
    try {
      patch = await recoverySecretPatch(payload.secret_question, payload.secret_answer, pepper);
    } catch {
      return jsonResponse({ error: "recovery_secret_invalid" }, 400);
    }
    const { error: updateError } = await admin.from("community_profiles").update(patch)
      .eq("auth_user_id", identity.user.id);
    if (updateError) throw updateError;
    return jsonResponse({ ok: true, version: 1 });
  }
  const profile = await findProfile(admin, payload);
  if (profile && await isRegistrationQuarantined(admin, profile)) {
    return jsonResponse({ error: "account_unavailable" }, 503);
  }
  if (action === "recovery_question") {
    if (!profile?.secret_question?.trim()) {
      return jsonResponse({ error: "recovery_profile_not_found" }, 404);
    }
    return jsonResponse({ secret_question: profile.secret_question });
  }
  if (action === "reset_password") {
    return handlePasswordReset({ admin, profile, payload, serviceRoleKey });
  }

  const password = payload.password?.trim();
  if (!password) {
    return jsonResponse({ error: "password_required" }, 400);
  }
  if (!profile) {
    return jsonResponse({ error: "invalid_credentials" }, 401);
  }

  const passwordMatches = await validateLegacyPassword(profile, password);
  if (!passwordMatches) {
    return jsonResponse({ error: "invalid_credentials" }, 401);
  }

  const isDeactivated = profile.account_status === "deactivated" || Boolean(profile.deactivated_at);
  if (isDeactivated && payload.reactivate_deactivated !== true) {
    return jsonResponse({ error: "account_deactivated" }, 403);
  }

  const email = profileEmail(profile, payload);
  const displayName = displayNameFor(profile);
  const authPassword = await supabaseAuthPassword(profile.id, password, serviceRoleKey);
  const userMetadata = {
    profile_id: profile.id,
    display_name: displayName,
    avatar_url: profile.avatar_url || profile.avatar || null,
    neighborhood: profile.neighborhood || profile.barrio || null,
    auth_source: "quata_legacy_bridge",
    auth_password_secret_version: Deno.env.get("QUATA_INTERNAL_AUTH_PASSWORD_SECRET_VERSION") || "legacy",
  };

  const authUserId = await ensureAuthUser(admin, {
    profile,
    email,
    password: authPassword,
    userMetadata,
    reactivate: isDeactivated,
  });

  const signInClient = createClient(supabaseUrl, publicKey, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { "X-Client-Info": "quata-auth-bridge-signin" } },
  });

  let { data: signInData, error: signInError } = await signInClient.auth.signInWithPassword({
    email,
    password: authPassword,
  });

  // Updating an Auth password revokes every refresh token for that user. A
  // regular bridge login must therefore reuse the existing internal password
  // so a dashboard session cannot sign the Android app out (or vice versa).
  // The legacy password was already verified above; only rotate the internal
  // Auth password when it is genuinely out of date after a legacy change.
  if ((signInError || !signInData.session) && isInvalidAuthPassword(signInError)) {
    const { error: updatePasswordError } = await admin.auth.admin.updateUserById(authUserId, {
      password: authPassword,
      user_metadata: userMetadata,
    });
    if (updatePasswordError) {
      return jsonResponse({ error: "auth_session_failed", detail: updatePasswordError.message }, 500);
    }
    ({ data: signInData, error: signInError } = await signInClient.auth.signInWithPassword({
      email,
      password: authPassword,
    }));
  }

  if (signInError || !signInData.session) {
    return jsonResponse(
      {
        error: "auth_session_failed",
        detail: signInError?.message,
      },
      500,
    );
  }

  const { error: linkProfileError } = await admin
    .from("community_profiles")
    .update({
      auth_user_id: authUserId,
      last_login_at: new Date().toISOString(),
      account_status: "active",
      deactivated_at: null,
    })
    .eq("id", profile.id);
  if (linkProfileError) throw linkProfileError;

  const response: Record<string, unknown> = {
    version: 1,
    profile: publicProfile(profile, authUserId),
    session: signInData.session,
    user: signInData.user,
  };
  if (action === "web_login") {
    const clientInstanceId = payload.client_instance_id?.trim() || "";
    if (clientInstanceId.length < 8 || clientInstanceId.length > 200) {
      return jsonResponse({ error: "invalid_client_instance_id" }, 400);
    }
    response.client_type = "web";
    response.web_session = await createWebClientSession(admin, {
      profileId: profile.id,
      authUserId,
      clientInstanceId,
    });
  }
  return jsonResponse(response);
}

async function createWebClientSession(
  admin: any,
  params: { profileId: string; authUserId: string; clientInstanceId: string },
) {
  const token = randomBase64Url(32);
  const tokenHash = await sha256(token);
  const now = new Date().toISOString();
  const { data, error } = await admin
    .from("web_client_sessions")
    .upsert(
      {
        profile_id: params.profileId,
        auth_user_id: params.authUserId,
        client_instance_id: params.clientInstanceId,
        token_hash: tokenHash,
        updated_at: now,
        last_seen_at: now,
        revoked_at: null,
      },
      { onConflict: "profile_id,client_instance_id" },
    )
    .select("id")
    .single();
  if (error) throw error;
  return { id: data.id, token };
}

async function handlePasswordReset(params: {
  admin: any;
  profile: CommunityProfile | null;
  payload: BridgeRequest;
  serviceRoleKey: string;
}): Promise<Response> {
  const { admin, profile, payload, serviceRoleKey } = params;
  const newPassword = payload.new_password?.trim() || "";
  const secretAnswer = payload.secret_answer?.trim() || "";
  if (
    !profile?.secret_question?.trim() ||
    (!profile.secret_answer?.trim() && !profile.secret_answer_hash?.trim())
  ) {
    return jsonResponse({ error: "recovery_profile_not_found" }, 404);
  }
  if (!newPassword || newPassword.length < 6) {
    return jsonResponse({ error: "invalid_new_password" }, 400);
  }
  let answerMatches = false;
  if (secretAnswer && profile.secret_answer_hash?.startsWith("v1:")) {
    const pepper = Deno.env.get("QUATA_WEB_REGISTRATION_PEPPER");
    if (!pepper) return jsonResponse({ error: "recovery_not_configured" }, 503);
    const candidate = await hashRecoveryAnswer(secretAnswer, pepper);
    answerMatches = constantTimeEqualHex(profile.secret_answer_hash, candidate);
  } else if (secretAnswer && profile.secret_answer?.trim()) {
    answerMatches =
      profile.secret_answer.trim().localeCompare(secretAnswer.trim(), undefined, { sensitivity: "accent" }) === 0;
  }
  if (!answerMatches) {
    return jsonResponse({ error: "invalid_secret_answer" }, 401);
  }

  const email = profileEmail(profile, payload);
  const authPassword = await supabaseAuthPassword(profile.id, newPassword, serviceRoleKey);
  const authUserId = await ensureAuthUser(admin, {
    profile,
    email,
    password: authPassword,
    userMetadata: {
      profile_id: profile.id,
      display_name: displayNameFor(profile),
      avatar_url: profile.avatar_url || profile.avatar || null,
      neighborhood: profile.neighborhood || profile.barrio || null,
      auth_source: "quata_legacy_bridge",
    },
  });
  const { error: authError } = await admin.auth.admin.updateUserById(authUserId, { password: authPassword });
  if (authError) throw authError;
  const { error: profileError } = await admin.from("community_profiles").update({
    auth_user_id: authUserId,
    pass_hash: await hashRegistrationPassword(newPassword),
    pass_plain: null,
  }).eq("id", profile.id);
  if (profileError) throw profileError;
  return jsonResponse({ ok: true });
}

async function findProfile(admin: any, payload: BridgeRequest): Promise<CommunityProfile | null> {
  const profileId = payload.profile_id?.trim();
  if (profileId) {
    const { data, error } = await admin
      .from("community_profiles")
      .select(profileSelect)
      .eq("id", profileId)
      .maybeSingle();
    if (error) throw error;
    return (data as CommunityProfile | null) ?? null;
  }

  const phone = digitsOnly(payload.phone_local || payload.phone || "");
  if (!phone) return null;

  let query = admin
    .from("community_profiles")
    .select(profileSelect)
    .or(`phone_local.eq.${phone},phone_normalized.eq.${phone},telefono.eq.${phone}`);

  const { data, error } = await query.limit(10);
  if (error) throw error;

  const countryCode = digitsOnly(payload.country_code || "");
  const rows = (data as CommunityProfile[] | null) ?? [];
  if (countryCode) {
    return rows.find((profile) => digitsOnly(profile.country_code || profile.code || "") === countryCode) ?? rows[0] ?? null;
  }
  return rows[0] ?? null;
}

async function ensureAuthUser(
  admin: any,
  params: {
    profile: CommunityProfile;
    email: string;
    password: string;
    userMetadata: Record<string, unknown>;
    reactivate?: boolean;
  },
): Promise<string> {
  const { profile, email, password, userMetadata, reactivate = false } = params;
  const currentAuthUserId = profile.auth_user_id?.trim();

  if (currentAuthUserId) {
    const { data, error } = await admin.auth.admin.updateUserById(currentAuthUserId, {
      email,
      user_metadata: userMetadata,
      email_confirm: true,
      ...(reactivate ? { ban_duration: "none" } : {}),
    });
    if (!error && data.user) return data.user.id;
  }

  const existing = await findAuthUserByEmail(admin, email);
  if (existing?.id) {
    const { data, error } = await admin.auth.admin.updateUserById(existing.id, {
      email,
      user_metadata: userMetadata,
      email_confirm: true,
      ...(reactivate ? { ban_duration: "none" } : {}),
    });
    if (error || !data.user) throw error ?? new Error("Could not update auth user");
    return data.user.id;
  }

  const { data, error } = await admin.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: userMetadata,
  });
  if (error || !data.user) throw error ?? new Error("Could not create auth user");
  return data.user.id;
}

function isInvalidAuthPassword(error: { code?: string | null; message?: string | null } | null): boolean {
  return error?.code === "invalid_credentials" || /invalid login credentials/i.test(error?.message || "");
}

async function findAuthUserByEmail(admin: any, email: string) {
  const normalizedEmail = email.trim().toLowerCase();
  const perPage = 1000;
  for (let page = 1; page <= 20; page++) {
    const { data, error } = await admin.auth.admin.listUsers({ page, perPage });
    if (error) throw error;
    const found = data.users.find((user: any) => user.email?.toLowerCase() === normalizedEmail);
    if (found) return found;
    if (data.users.length < perPage) return null;
  }
  return null;
}

async function validateLegacyPassword(profile: CommunityProfile, password: string): Promise<boolean> {
  const passHash = profile.pass_hash?.trim();
  if (passHash) {
    if (passHash.startsWith("pbkdf2_sha256$")) {
      return verifyRegistrationPassword(passHash, password);
    }
    const sha = await sha256(password);
    if (sha.toLowerCase() === passHash.toLowerCase()) return true;
  }
  return profile.pass_plain === password;
}

async function sha256(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const hash = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(hash))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

async function isRegistrationQuarantined(
  admin: any,
  profile: CommunityProfile,
): Promise<boolean> {
  if (Deno.env.get("QUATA_REGISTRATION_QUARANTINE_ENABLED") !== "true") return false;
  const pepper = Deno.env.get("QUATA_WEB_REGISTRATION_PEPPER");
  if (!pepper || pepper.length < 32) throw new Error("quarantine_configuration_missing");
  const country = digitsOnly(profile.country_code || profile.code || "");
  const local = digitsOnly(profile.phone_local || profile.phone_normalized || profile.telefono || "");
  // New registrations are always canonical. A legacy row without both parts
  // cannot match the new ledger and must keep its existing login behavior.
  if (!country || !local) return false;
  const phoneHash = await registrationPhoneHash(country, local, pepper);
  const { count, error } = await admin.from("web_registration_requests")
    .select("id", { count: "exact", head: true })
    .eq("phone_hash", phoneHash)
    .eq("status", "cleanup_required");
  if (error) throw error;
  return (count || 0) > 0;
}

function randomBase64Url(byteLength: number): string {
  const bytes = crypto.getRandomValues(new Uint8Array(byteLength));
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

async function supabaseAuthPassword(profileId: string, legacyPassword: string, serviceRoleKey: string): Promise<string> {
  // A dedicated stable secret prevents service-role rotation from invalidating every Auth password.
  // The fallback preserves already-deployed legacy identities until the release config is migrated.
  const stableSecret = Deno.env.get("QUATA_INTERNAL_AUTH_PASSWORD_SECRET") || serviceRoleKey;
  const hash = await sha256(`${profileId}:${legacyPassword}:${stableSecret}`);
  return `Qa-${hash}`;
}

function profileEmail(profile: CommunityProfile, payload: BridgeRequest): string {
  const countryCode = digitsOnly(profile.country_code || profile.code || payload.country_code || "");
  const phoneLocal = digitsOnly(
    profile.phone_local ||
      profile.phone_normalized ||
      profile.telefono ||
      payload.phone_local ||
      payload.phone ||
      "",
  );
  const prefix = `${countryCode}${phoneLocal}`.trim();
  if (prefix) return `${prefix}@phone.quata.app`;
  return `profile-${profile.id}@profile.quata.app`;
}

function publicProfile(profile: CommunityProfile, authUserId: string) {
  return {
    id: profile.id,
    auth_user_id: authUserId,
    display_name: displayNameFor(profile),
    phone_local: profile.phone_local || profile.phone_normalized || profile.telefono || null,
    country_code: profile.country_code || profile.code || null,
    avatar_url: profile.avatar_url || profile.avatar || null,
    neighborhood: profile.neighborhood || profile.barrio || null,
  };
}

function displayNameFor(profile: CommunityProfile): string {
  return profile.display_name?.trim() || profile.nombre?.trim() || profile.phone_local?.trim() || "Usuario";
}

function digitsOnly(value: string): string {
  return value.replace(/\D/g, "");
}

function envDictionaryValue(name: string, preferredKey = "default"): string | null {
  const raw = Deno.env.get(name);
  if (!raw) return null;

  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const preferred = parsed[preferredKey];
    if (typeof preferred === "string" && preferred.trim()) return preferred;

    for (const value of Object.values(parsed)) {
      if (typeof value === "string" && value.trim()) return value;
    }
  } catch {
    return null;
  }

  return null;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}
