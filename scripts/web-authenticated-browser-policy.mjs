import { isPublicSupabaseKey } from "./web-authenticated-browser-security.mjs";

export const REAL_SESSION_OPT_IN = "I_ACCEPT_SESSION_REVOCATION";
export const BRIDGE_MUTATION_OPT_IN = "I_ACCEPT_AUTH_IDENTITY_AND_SESSION_MUTATIONS";
export const DEDICATED_ACCOUNT_SCOPE = "dedicated_web_auth_e2e";
export const PREPROVISIONED_AUTH_USER = "I_CONFIRM_AUTH_USER_ALREADY_EXISTS";
export const DISTRIBUTION_REVISION_FILE = "quata-source-revision.txt";

export const READ_ONLY_ROUTE_MATRIX = Object.freeze([
  Object.freeze({ fragment: "", route: "feed" }),
  Object.freeze({ fragment: "profile", route: "profile" }),
  Object.freeze({ fragment: "settings", route: "settings" }),
  Object.freeze({ fragment: "communities", route: "communities" }),
  Object.freeze({ fragment: "official", route: "official" }),
]);

export const READ_ONLY_ROUTE_EXCLUSIONS = Object.freeze([
  Object.freeze({
    fragments: Object.freeze(["whats-new", "about"]),
    method: "POST",
    path: "/rest/v1/rpc/quata_android_release_history",
    reason: "postgrest_rpc_post_not_get_only",
  }),
]);

const REQUIRED_REAL_ENVIRONMENT = Object.freeze([
  "QUATA_SUPABASE_URL",
  "QUATA_SUPABASE_PUBLISHABLE_KEY",
  "QUATA_E2E_COUNTRY_CODE",
  "QUATA_E2E_PHONE",
  "QUATA_E2E_PASSWORD",
]);

const FORBIDDEN_PRIVILEGED_ENVIRONMENT = Object.freeze([
  "SUPABASE_DB_URL",
  "SUPABASE_DB_TLS_CA_FILE",
  "SUPABASE_DB_TLS_CA_PEM",
  "SUPABASE_SERVICE_ROLE_KEY",
  "QUATA_SUPABASE_SERVICE_ROLE_KEY",
  "SUPABASE_ACCESS_TOKEN",
]);

export function loadRealAuthConfiguration(environment) {
  if (environment.QUATA_AUTH_E2E_REAL_OPT_IN !== REAL_SESSION_OPT_IN) {
    throw new Error("real_mode_session_revocation_opt_in_required");
  }
  if (environment.QUATA_AUTH_E2E_BRIDGE_MUTATION_OPT_IN !== BRIDGE_MUTATION_OPT_IN) {
    throw new Error("real_mode_bridge_mutation_opt_in_required");
  }
  if (environment.QUATA_E2E_ACCOUNT_SCOPE !== DEDICATED_ACCOUNT_SCOPE) {
    throw new Error("real_mode_dedicated_account_required");
  }
  if (environment.QUATA_E2E_AUTH_USER_PREPROVISIONED !== PREPROVISIONED_AUTH_USER) {
    throw new Error("real_mode_preprovisioned_auth_user_required");
  }
  if (FORBIDDEN_PRIVILEGED_ENVIRONMENT.some(name => environment[name]?.trim())) {
    throw new Error("real_mode_privileged_environment_forbidden");
  }
  if (REQUIRED_REAL_ENVIRONMENT.some(name => !environment[name]?.trim())) {
    throw new Error("real_mode_environment_missing");
  }

  const baseUrl = environment.QUATA_SUPABASE_URL.trim().replace(/\/+$/, "");
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(baseUrl)) {
    throw new Error("invalid_public_supabase_url");
  }
  const publishableKey = environment.QUATA_SUPABASE_PUBLISHABLE_KEY.trim();
  if (!isPublicSupabaseKey(publishableKey)) {
    throw new Error("privileged_or_invalid_publishable_key");
  }
  return {
    baseUrl,
    publishableKey,
    countryCode: environment.QUATA_E2E_COUNTRY_CODE.trim(),
    phone: environment.QUATA_E2E_PHONE.trim(),
    password: environment.QUATA_E2E_PASSWORD,
  };
}

export function backendBrowserRequestDecision({ backend, url, method, stage, body }) {
  let parsed;
  try {
    parsed = new URL(url);
  } catch {
    return Object.freeze({ backendApi: false, allowed: false, reason: "invalid_backend_request_url" });
  }
  const normalizedBackend = backend.replace(/\/+$/, "");
  if (!url.startsWith(`${normalizedBackend}/`)) {
    return Object.freeze({ backendApi: false, allowed: false, reason: "unexpected_backend_origin" });
  }
  const backendApi = /^\/(?:auth|functions|rest)\/v1(?:\/|$)/.test(parsed.pathname);
  if (!backendApi) return Object.freeze({ backendApi: false, allowed: true, reason: "non_api_asset" });

  const normalizedMethod = method.toUpperCase();
  if (["GET", "HEAD", "OPTIONS"].includes(normalizedMethod)) {
    return Object.freeze({ backendApi: true, allowed: true, reason: "read_only_method" });
  }

  const action = safeJson(body)?.action;
  if (
    normalizedMethod === "POST" &&
    parsed.pathname === "/functions/v1/quata-auth-bridge" &&
    stage === "native_login_controls" &&
    action === "web_login"
  ) {
    return Object.freeze({ backendApi: true, allowed: true, reason: "declared_login_bridge_effects" });
  }
  if (
    normalizedMethod === "POST" &&
    parsed.pathname === "/functions/v1/quata-web-push" &&
    stage === "native_logout" &&
    action === "logout"
  ) {
    return Object.freeze({ backendApi: true, allowed: true, reason: "declared_web_session_cleanup" });
  }
  return Object.freeze({
    backendApi: true,
    allowed: false,
    reason: `backend_mutation_blocked_${normalizedMethod.toLowerCase()}`,
  });
}

export function assertExactDistributionRevision({ repositoryRevision, markerRevision, trackedChanges }) {
  if (!/^[0-9a-f]{40}$/i.test(repositoryRevision ?? "")) {
    throw new Error("repository_revision_invalid");
  }
  if (!/^[0-9a-f]{40}$/i.test(markerRevision ?? "")) {
    throw new Error("distribution_revision_missing_or_invalid");
  }
  if (repositoryRevision.toLowerCase() !== markerRevision.toLowerCase()) {
    throw new Error("distribution_revision_mismatch");
  }
  if (trackedChanges.trim()) throw new Error("distribution_source_tree_dirty");
  return repositoryRevision.toLowerCase();
}

function safeJson(value) {
  try {
    return JSON.parse(value || "{}");
  } catch {
    return {};
  }
}
