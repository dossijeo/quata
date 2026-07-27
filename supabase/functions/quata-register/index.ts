import { createClient } from "npm:@supabase/supabase-js@2";
import {
  hashRecoveryAnswer,
  hashRegistrationPassword,
  sha256Hex,
  registrationPhoneHash,
} from "../_shared/web-registration-security.mjs";
import {
  RegistrationContractError,
  runRegistration,
} from "./contract.mjs";
import { opaqueRegistrationDelay } from "./response-policy.mjs";
import { isAllowedOrigin, parseRegistrationConfig, verifyTurnstileChallenge } from "./http-policy.mjs";

type RegistrationRecord = {
  id: string;
  profileId: string;
  authUserId?: string | null;
};

Deno.serve(async (request) => {
  const responseFloorStartedAt = Date.now();
  const origin = request.headers.get("origin") || "";
  const cors = corsHeaders(origin);
  if (request.method === "OPTIONS") {
    return allowedOrigin(origin)
      ? new Response("ok", { headers: cors })
      : json({ error: "origin_not_allowed" }, 403, cors);
  }
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405, cors);
  if (origin && !allowedOrigin(origin)) return json({ error: "origin_not_allowed" }, 403, cors);

  try {
    const configuration = requireConfiguration();
    if (!configuration.enabled) return json({ error: "registration_unavailable" }, 503, cors);
    if (!constantTimeEqual(request.headers.get("apikey") || "", configuration.publicApiKey)) {
      return json({ error: "invalid_api_key" }, 401, cors);
    }
    const declaredLength = Number(request.headers.get("content-length") || "0");
    if (declaredLength > 16_384) return json({ error: "request_too_large" }, 413, cors);
    const raw = await request.text();
    if (raw.length > 16_384) return json({ error: "request_too_large" }, 413, cors);
    let payload: unknown;
    try {
      payload = JSON.parse(raw);
    } catch {
      return json({ error: "invalid_json" }, 400, cors);
    }

    const admin = createClient(configuration.supabaseUrl, configuration.serviceRoleKey, {
      auth: { persistSession: false, autoRefreshToken: false },
      global: { headers: { "X-Client-Info": "quata-register" } },
    });
    // Supabase Edge forwards Cloudflare's trusted header. If absent, use a
    // per-request nonce so attackers cannot force every request into one global bucket.
    const sourceIp = request.headers.get("cf-connecting-ip") || `untrusted-${crypto.randomUUID()}`;

    const result = await runRegistration(payload, {
      prepare: async (input: Record<string, string>) => {
        if (configuration.enabled) {
          const challengeOk = await verifyTurnstileChallenge(
            configuration.turnstileSecret!,
            input.challengeToken,
            sourceIp,
            `register_${input.channel}`,
            configuration.turnstileAllowedHostnames,
          );
          if (!challengeOk) throw new RegistrationContractError("challenge_failed", 403);
        }
        const phoneIdentity = input.phoneE164;
        return {
          ...input,
          requestKeyHash: await sha256Hex(`${input.idempotencyKey}:${configuration.pepper}`),
          payloadHash: await sha256Hex(JSON.stringify({
            displayName: input.displayName,
            neighborhood: input.neighborhood,
            phoneIdentity,
            password: input.password,
            secretQuestion: input.secretQuestion,
            secretAnswer: input.secretAnswer,
            clientInstanceId: input.clientInstanceId,
            channel: input.channel,
            pepper: configuration.pepper,
          })),
          phoneHash: await registrationPhoneHash(input.countryCode, input.phoneLocal, configuration.pepper),
          clientHash: await sha256Hex(`${input.clientInstanceId}:${configuration.pepper}`),
          ipHash: await sha256Hex(`${sourceIp}:${configuration.pepper}`),
          authEmail: `${input.countryCode}${input.phoneLocal}@phone.quata.app`,
        };
      },
      accepted: () => ({ version: 1, status: "accepted" }),
      claim: async (context: Record<string, string>) => {
        const { data, error } = await admin.rpc("quata_claim_web_registration", {
          p_request_key_hash: context.requestKeyHash,
          p_payload_hash: context.payloadHash,
          p_phone_hash: context.phoneHash,
          p_client_hash: context.clientHash,
          p_ip_hash: context.ipHash,
        });
        if (error) throw error;
        const row = data as Record<string, unknown>;
        const requestRow = row.request as Record<string, unknown> | null;
        return {
          kind: String(row.kind),
          retryAfterSeconds: Number(row.retry_after_seconds || 0) || null,
          record: requestRow ? registrationRecord(requestRow) : null,
        };
      },
      findProfile: async (context: Record<string, string>) => {
        const { data, error } = await admin
          .from("community_profiles")
          .select("id,auth_user_id,display_name,country_code,phone_local,neighborhood")
          .eq("country_code", context.countryCode)
          .eq("phone_e164", context.phoneE164)
          .maybeSingle();
        if (error) throw error;
        return data;
      },
      findAuthUser: async (context: Record<string, string>) => {
        const { data, error } = await admin.rpc("quata_web_registration_auth_user", {
          p_email: context.authEmail,
        });
        if (error) throw error;
        const row = Array.isArray(data) ? data[0] : data;
        if (!row) return null;
        const metadata = (row.raw_user_meta_data || {}) as Record<string, unknown>;
        return { id: String(row.id), profileId: String(metadata.profile_id || "") };
      },
      createAuthUser: async (context: Record<string, string>, record: RegistrationRecord) => {
        const password = await internalAuthPassword(
          record.profileId,
          context.password,
          configuration.internalAuthPasswordSecret,
        );
        const { data, error } = await admin.auth.admin.createUser({
          email: context.authEmail,
          password,
          email_confirm: true,
          user_metadata: {
            profile_id: record.profileId,
            display_name: context.displayName,
            neighborhood: context.neighborhood,
            auth_source: "quata_web_registration",
            registration_request_id: record.id,
            auth_password_secret_version: configuration.internalAuthPasswordSecretVersion,
          },
        });
        if (error || !data.user) throw error ?? new Error("auth_user_not_created");
        return { id: data.user.id, profileId: record.profileId };
      },
      recordAuthUser: (record: RegistrationRecord, authUserId: string) =>
        updateRequest(admin, record.id, { auth_user_id: authUserId, updated_at: new Date().toISOString() }),
      createProfile: async (
        context: Record<string, string>,
        record: RegistrationRecord,
        authUserId: string,
      ) => {
        const [passwordHash, recoveryHash] = await Promise.all([
          hashRegistrationPassword(context.password),
          hashRecoveryAnswer(context.secretAnswer, configuration.pepper),
        ]);
        const { data, error } = await admin
          .from("community_profiles")
          .insert({
            id: record.profileId,
            display_name: context.displayName,
            nombre: context.displayName,
            phone: context.phoneE164,
            phone_normalized: context.phoneLocal,
            country_code: context.countryCode,
            phone_local: context.phoneLocal,
            phone_e164: context.phoneE164,
            code: context.countryCode,
            telefono: context.phoneLocal,
            barrio: context.neighborhood,
            barrio_normalized: context.neighborhood.toLocaleLowerCase("es"),
            neighborhood: context.neighborhood,
            pass_hash: passwordHash,
            pass_plain: null,
            secret_question: context.secretQuestion,
            secret_answer: null,
            secret_answer_hash: recoveryHash,
            auth_user_id: authUserId,
            account_status: "active",
            deactivated_at: null,
            is_admin: false,
            is_official: false,
          })
          .select("id,auth_user_id,display_name,country_code,phone_local,neighborhood")
          .single();
        if (error) throw error;
        return data;
      },
      recordProfile: (record: RegistrationRecord, profileId: string) =>
        updateRequest(admin, record.id, { profile_id: profileId, updated_at: new Date().toISOString() }),
      finalizeRegistration: (
      ) => Promise.resolve(null),
      restoreCompleted: async (_context: Record<string, string>, record: RegistrationRecord) => {
        const { data: profile, error } = await admin
          .from("community_profiles")
          .select("id,auth_user_id,display_name,country_code,phone_local,neighborhood")
          .eq("id", record.profileId)
          .maybeSingle();
        if (error || !profile || !record.authUserId) {
          throw new RegistrationContractError("registration_unavailable", 409);
        }
        return null;
      },
      complete: (record: RegistrationRecord, authUserId: string, profileId: string) =>
        updateRequest(admin, record.id, {
          status: "completed",
          auth_user_id: authUserId,
          profile_id: profileId,
          completed_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
          last_error_code: null,
        }),
      deleteProfile: async (profileId: string, authUserId: string | null) => {
        let query = admin.from("community_profiles").delete().eq("id", profileId);
        if (authUserId) query = query.eq("auth_user_id", authUserId);
        const { error } = await query;
        if (error) throw error;
      },
      deleteAuthUser: async (authUserId: string) => {
        const { error } = await admin.auth.admin.deleteUser(authUserId);
        if (error) throw error;
      },
      fail: (record: RegistrationRecord, code: string) =>
        updateRequest(admin, record.id, {
          status: "failed",
          last_error_code: code,
          updated_at: new Date().toISOString(),
        }),
      requireCleanup: (record: RegistrationRecord, authUserId: string | null, code: string) =>
        updateRequest(admin, record.id, {
          status: "cleanup_required",
          auth_user_id: authUserId,
          last_error_code: code,
          updated_at: new Date().toISOString(),
        }),
    });
    await opaqueRegistrationDelay(responseFloorStartedAt);
    return json(result, 202, cors);
  } catch (error) {
    console.error(JSON.stringify({ event: "web_registration_failed", code:
      error instanceof RegistrationContractError ? error.code : "internal_error" }));
    if (error instanceof RegistrationContractError) {
      const headers = error.retryAfterSeconds
        ? { ...cors, "Retry-After": String(error.retryAfterSeconds) }
        : cors;
      return json({ error: error.code }, error.status, headers);
    }
    return json({ error: "registration_failed" }, 503, cors);
  }
});


function registrationRecord(row: Record<string, unknown>): RegistrationRecord {
  return {
    id: String(row.id),
    profileId: String(row.profile_id),
    authUserId: row.auth_user_id ? String(row.auth_user_id) : null,
  };
}

async function updateRequest(
  // Generated DB types are not available inside the vendored Edge bundle.
  admin: any,
  id: string,
  patch: Record<string, unknown>,
) {
  const { error } = await admin.from("web_registration_requests").update(patch).eq("id", id);
  if (error) throw error;
}

async function internalAuthPassword(profileId: string, password: string, stableSecret: string) {
  return `Qa-${await sha256Hex(`${profileId}:${password}:${stableSecret}`)}`;
}

function requireConfiguration() {
  try {
    return parseRegistrationConfig((name: string) => Deno.env.get(name));
  } catch {
    throw new RegistrationContractError("server_not_configured", 503);
  }
}

function allowedOrigin(origin: string) {
  const allowed = (Deno.env.get("QUATA_WEB_REGISTRATION_ALLOWED_ORIGINS") || "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
  return isAllowedOrigin(origin, allowed);
}

function corsHeaders(origin: string): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": allowedOrigin(origin) ? origin : "null",
    "Access-Control-Allow-Headers": "content-type, apikey, x-client-info",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Max-Age": "600",
    "Vary": "Origin",
    "Content-Type": "application/json",
  };
}

function json(body: unknown, status: number, headers: Record<string, string>) {
  return new Response(JSON.stringify(body), { status, headers });
}

function constantTimeEqual(left: string, right: string) {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index += 1) {
    difference |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return difference === 0;
}
