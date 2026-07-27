const EXPLICIT_REFRESH_TOKEN_ERRORS = new Set([
  "invalid_refresh_token",
  "refresh_token_already_used",
  "refresh_token_not_found",
  "refresh_token_revoked",
]);

const EXPLICIT_INVALID_GRANT_DESCRIPTIONS = [
  "invalid refresh token",
  "refresh token already used",
  "refresh token not found",
  "refresh token revoked",
];

export function isPublicSupabaseKey(value) {
  if (typeof value !== "string") return false;
  const key = value.trim();
  if (!key) return false;
  if (key.startsWith("sb_secret_")) return false;
  if (key.startsWith("sb_publishable_")) {
    const suffix = key.slice("sb_publishable_".length);
    return suffix.length > 0 && /^[A-Za-z0-9_-]+$/.test(suffix);
  }

  const parts = key.split(".");
  if (parts.length !== 3 || parts.some(part => !part || !/^[A-Za-z0-9_-]+$/.test(part))) return false;
  try {
    const payload = JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"));
    return payload?.role === "anon";
  } catch {
    return false;
  }
}

export function assertExplicitRefreshTokenRejection(status, bodyText) {
  if (status >= 200 && status < 300) throw new Error("global_session_revocation_unverified");
  if (status !== 400 && status !== 401) {
    throw new Error("global_session_revocation_verification_transient_or_server_error");
  }

  let payload;
  try {
    payload = JSON.parse(bodyText);
  } catch {
    throw new Error("global_session_revocation_verification_inconclusive");
  }
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    throw new Error("global_session_revocation_verification_inconclusive");
  }

  const explicitCode = [payload.error_code, payload.code, payload.error]
    .find(value => typeof value === "string" && EXPLICIT_REFRESH_TOKEN_ERRORS.has(value.toLowerCase()));
  if (explicitCode) return;

  if (typeof payload.error === "string" && payload.error.toLowerCase() === "invalid_grant") {
    const description = [payload.error_description, payload.message]
      .filter(value => typeof value === "string")
      .join(" ")
      .toLowerCase();
    if (EXPLICIT_INVALID_GRANT_DESCRIPTIONS.some(expected => description.includes(expected))) return;
  }

  throw new Error("global_session_revocation_verification_inconclusive");
}
