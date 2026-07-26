const allowedQuestions = new Set(["madre", "barrio", "amigo", "comida"]);

export class RegistrationContractError extends Error {
  constructor(code, status = 400, retryAfterSeconds = null) {
    super(code);
    this.name = "RegistrationContractError";
    this.code = code;
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export function validateRegistrationPayload(payload) {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    throw new RegistrationContractError("invalid_request");
  }
  rejectUnknownFields(payload);
  if (payload.version !== 1) throw new RegistrationContractError("unsupported_version");
  const displayName = normalizedText(payload.display_name, 2, 80, "invalid_display_name");
  const neighborhood = normalizedText(payload.neighborhood, 2, 100, "invalid_neighborhood");
  const countryCode = digits(payload.country_code);
  const phoneLocal = digits(payload.phone_local ?? payload.phone);
  const password = typeof payload.password === "string" ? payload.password : "";
  const secretQuestion = typeof payload.secret_question === "string" ? payload.secret_question.trim() : "";
  const secretAnswer = normalizedText(payload.secret_answer, 2, 160, "invalid_secret_answer");
  const clientInstanceId = normalizedText(payload.client_instance_id, 8, 200, "invalid_client_instance_id");
  const idempotencyKey = normalizedText(payload.idempotency_key, 16, 200, "invalid_idempotency_key");
  const challengeToken = typeof payload.challenge_token === "string" ? payload.challenge_token.trim() : "";
  const channel = payload.channel;
  if (channel !== "web" && channel !== "android") throw new RegistrationContractError("invalid_channel");

  if (!/^[1-9][0-9]{0,2}$/.test(countryCode)) {
    throw new RegistrationContractError("invalid_country_code");
  }
  if (!/^[0-9]{6,14}$/.test(phoneLocal) || `${countryCode}${phoneLocal}`.length > 15) {
    throw new RegistrationContractError("invalid_phone");
  }
  if (
    password !== password.trim() ||
    password.length < 10 ||
    password.length > 128 ||
    !/[a-z]/.test(password) ||
    !/[A-Z]/.test(password) ||
    !/[0-9]/.test(password)
  ) {
    throw new RegistrationContractError("invalid_password");
  }
  if (!allowedQuestions.has(secretQuestion)) {
    throw new RegistrationContractError("invalid_secret_question");
  }
  if (!/^[A-Za-z0-9_-]{16,200}$/.test(idempotencyKey)) {
    throw new RegistrationContractError("invalid_idempotency_key");
  }

  return {
    displayName,
    neighborhood,
    countryCode,
    phoneLocal,
    phoneE164: `+${countryCode}${phoneLocal}`,
    password,
    secretQuestion,
    secretAnswer,
    clientInstanceId,
    idempotencyKey,
    challengeToken,
    channel,
  };
}

export async function runRegistration(payload, dependencies) {
  const input = validateRegistrationPayload(payload);
  const context = await dependencies.prepare(input);
  const claim = await dependencies.claim(context);
  if (claim.kind === "rate_limited") {
    throw new RegistrationContractError("rate_limited", 429, claim.retryAfterSeconds ?? 60);
  }
  if (claim.kind === "conflict" || claim.kind === "cleanup_required") {
    return dependencies.accepted();
  }
  if (claim.kind === "busy") {
    throw new RegistrationContractError("registration_in_progress", 409, claim.retryAfterSeconds ?? 15);
  }
  if (claim.kind === "replay") {
    await dependencies.restoreCompleted(context, claim.record);
    return dependencies.accepted();
  }

  const record = claim.record;
  let authUser = null;
  let createdAuthUser = false;
  let profile = null;
  let createdProfile = false;
  try {
    const existingProfile = await dependencies.findProfile(context);
    if (existingProfile && existingProfile.id !== record.profileId) {
      await dependencies.fail(record, "identity_unavailable");
      return dependencies.accepted();
    }

    authUser = await dependencies.findAuthUser(context);
    if (authUser && authUser.profileId !== record.profileId) {
      await dependencies.fail(record, "identity_unavailable");
      return dependencies.accepted();
    }
    if (!authUser) {
      authUser = await dependencies.createAuthUser(context, record);
      createdAuthUser = true;
    }
    await dependencies.recordAuthUser(record, authUser.id);

    profile = existingProfile ?? await dependencies.createProfile(context, record, authUser.id);
    createdProfile = existingProfile == null;
    await dependencies.recordProfile(record, profile.id);
    const result = await dependencies.createAuthenticatedResult(context, record, authUser, profile);
    await dependencies.complete(record, authUser.id, profile.id);
    return dependencies.accepted();
  } catch (error) {
    if (error instanceof RegistrationContractError) throw error;
    if (createdProfile && profile?.id) {
      try {
        await dependencies.deleteProfile(profile.id, authUser?.id);
      } catch {
        await dependencies.requireCleanup(record, authUser?.id ?? null, "profile_cleanup_failed");
        throw new RegistrationContractError("registration_failed", 503);
      }
    }
    if (createdAuthUser && authUser?.id) {
      try {
        await dependencies.deleteAuthUser(authUser.id);
        await dependencies.fail(record, "compensated");
      } catch {
        await dependencies.requireCleanup(record, authUser.id, "auth_cleanup_failed");
      }
    } else {
      await dependencies.fail(record, "registration_failed");
    }
    throw new RegistrationContractError("registration_failed", 503);
  }
}

function normalizedText(value, minimum, maximum, code) {
  if (typeof value !== "string") throw new RegistrationContractError(code);
  const normalized = value.normalize("NFKC").trim().replace(/\s+/g, " ");
  if (
    normalized.length < minimum ||
    normalized.length > maximum ||
    [...normalized].some((character) => /\p{Cc}/u.test(character))
  ) {
    throw new RegistrationContractError(code);
  }
  return normalized;
}

function digits(value) {
  return typeof value === "string" ? value.replace(/\D/g, "") : "";
}

function rejectUnknownFields(payload) {
  const allowed = new Set([
    "version", "display_name", "neighborhood", "country_code", "phone_local",
    "password", "secret_question", "secret_answer", "client_instance_id",
    "idempotency_key", "challenge_token", "channel",
  ]);
  if (Object.keys(payload).some((field) => !allowed.has(field))) {
    throw new RegistrationContractError("unknown_field");
  }
}
