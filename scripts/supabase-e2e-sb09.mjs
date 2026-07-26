#!/usr/bin/env node
/**
 * SB-09: Official likes must be bound to the authenticated profile by the database.
 *
 * Fixtures are provisioned and hard-purged by an authorized operator outside this runner.
 * The runner only uses the public key and the two isolated users' bearer sessions.
 */
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const required = [
  "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY",
  "QUATA_E2E_OFFICIAL_A_COUNTRY_CODE", "QUATA_E2E_OFFICIAL_A_PHONE", "QUATA_E2E_OFFICIAL_A_PASSWORD",
  "QUATA_E2E_OFFICIAL_B_COUNTRY_CODE", "QUATA_E2E_OFFICIAL_B_PHONE", "QUATA_E2E_OFFICIAL_B_PASSWORD",
  "QUATA_E2E_OFFICIAL_POST_ID", "QUATA_E2E_OFFICIAL_A_E2E_SCOPE", "QUATA_E2E_OFFICIAL_B_E2E_SCOPE",
  "QUATA_E2E_OFFICIAL_EXTERNAL_HARD_CLEANUP",
];
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function args(argv) {
  if (argv.length === 4 && argv[0] === "--allow-existing-test-data" && argv[1] === "--allow-official-like-mutation" && argv[2] === "--out" && argv[3].trim()) return { output: argv[3] };
  throw new Error("invalid_arguments");
}
function publicKey(value) {
  if (value.startsWith("sb_secret_") || value.toLowerCase().includes("service_role")) return false;
  const parts = value.split(".");
  if (parts.length !== 3) return true;
  try { return JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"))?.role !== "service_role"; } catch { return false; }
}
function config() {
  const missing = required.filter((name) => !process.env[name]?.trim());
  if (missing.length) throw new Error(`missing_environment:${missing.join(",")}`);
  const baseUrl = process.env.QUATA_SUPABASE_URL.trim().replace(/\/+$/, "");
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(baseUrl)) throw new Error("invalid_public_supabase_url");
  const key = process.env.QUATA_SUPABASE_PUBLISHABLE_KEY.trim();
  if (!publicKey(key)) throw new Error("invalid_or_privileged_supabase_key");
  if (process.env.QUATA_E2E_OFFICIAL_EXTERNAL_HARD_CLEANUP !== "approved_isolated_account_purge") throw new Error("safe_cleanup_contract_missing");
  if (process.env.QUATA_E2E_OFFICIAL_A_E2E_SCOPE !== "isolated_sb09_account" || process.env.QUATA_E2E_OFFICIAL_B_E2E_SCOPE !== "isolated_sb09_account") throw new Error("isolated_e2e_account_scope_missing");
  const postId = process.env.QUATA_E2E_OFFICIAL_POST_ID.trim();
  if (!uuid.test(postId)) throw new Error("invalid_official_post_identifier");
  const users = ["A", "B"].map((label) => ({
    label,
    countryCode: process.env[`QUATA_E2E_OFFICIAL_${label}_COUNTRY_CODE`].trim(),
    phone: process.env[`QUATA_E2E_OFFICIAL_${label}_PHONE`].trim(),
    password: process.env[`QUATA_E2E_OFFICIAL_${label}_PASSWORD`],
  }));
  if (`${users[0].countryCode}|${users[0].phone}` === `${users[1].countryCode}|${users[1].phone}`) throw new Error("isolated_e2e_accounts_must_differ");
  return { baseUrl, key, postId, users };
}
function headers(configuration, token) { return { apikey: configuration.key, "content-type": "application/json", accept: "application/json", "x-client-info": "quata-e2e-sb09", ...(token ? { authorization: `Bearer ${token}` } : {}) }; }
async function request(url, options, prefix) {
  let response;
  try { response = await fetch(url, { ...options, signal: AbortSignal.timeout(15_000) }); } catch { throw new Error(`${prefix}:network`); }
  const text = await response.text();
  let body = null;
  if (text) { try { body = JSON.parse(text); } catch { body = null; } }
  return { ok: response.ok, status: response.status, body };
}
function restUrl(configuration, table, query = {}) { const url = new URL(`${configuration.baseUrl}/rest/v1/${table}`); for (const [key, value] of Object.entries(query)) url.searchParams.set(key, value); return url; }
async function login(configuration, user) {
  const result = await request(`${configuration.baseUrl}/functions/v1/quata-auth-bridge`, { method: "POST", headers: headers(configuration), body: JSON.stringify({ action: "web_login", country_code: user.countryCode, phone_local: user.phone, password: user.password, client_instance_id: `e2e-sb09-${user.label.toLowerCase()}-${crypto.randomUUID()}` }) }, "public_auth_request_failed");
  const profileId = result.body?.profile?.id, accessToken = result.body?.session?.access_token;
  if (!result.ok || !uuid.test(profileId ?? "") || typeof accessToken !== "string" || !accessToken) throw new Error(`invalid_auth_response:${user.label}`);
  return { ...user, profileId, accessToken };
}
async function revoke(configuration, session) {
  const result = await request(`${configuration.baseUrl}/auth/v1/logout`, { method: "POST", headers: headers(configuration, session.accessToken), body: JSON.stringify({ scope: "global" }) }, "session_revocation_failed");
  if (!result.ok) throw new Error(`session_revocation_failed:http_${result.status}`);
}
function is42501(result) { return !result.ok && (result.body?.code === "42501" || result.body?.error?.code === "42501"); }
async function rows(configuration, session, query, prefix) {
  const result = await request(restUrl(configuration, "official_post_likes", query), { headers: headers(configuration, session.accessToken) }, prefix);
  if (!result.ok || !Array.isArray(result.body)) throw new Error(`${prefix}:http_${result.status}`);
  return result.body;
}
async function assertAbsent(configuration, session, postId, profileId, prefix) {
  const remaining = await rows(configuration, session, { select: "id", official_post_id: `eq.${postId}`, profile_id: `eq.${profileId}`, limit: "1" }, prefix);
  if (remaining.length) throw new Error(`${prefix}:row_remains`);
}
function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "unknown";
  const known = ["invalid_arguments", "missing_environment", "invalid_public_supabase_url", "invalid_or_privileged_supabase_key", "safe_cleanup_contract_missing", "isolated_e2e_account_scope_missing", "invalid_official_post_identifier", "isolated_e2e_accounts_must_differ", "public_auth_request_failed", "invalid_auth_response", "official_like_insert_failed", "official_like_spoof_not_denied", "official_like_spoof_persisted", "official_like_cross_delete_not_denied", "official_like_cross_delete_changed_row", "official_like_delete_failed", "official_like_cleanup"];
  return { status: "failed", error: known.find((prefix) => message.startsWith(prefix)) ?? "unexpected_official_like_runner_failure" };
}
async function report(output, payload) { const target = resolve(output); await mkdir(dirname(target), { recursive: true }); await writeFile(target, `${JSON.stringify(payload, null, 2)}\n`, { encoding: "utf8", mode: 0o600 }); console.log(`SB-09 report written: ${target}`); }

async function main() {
  const { output } = args(process.argv.slice(2)); const startedAt = new Date().toISOString(); const steps = [];
  let configuration; let a; let b; let likeId = null; let cleanup = { state: "not_started" };
  try {
    configuration = config();
    a = await login(configuration, configuration.users[0]); b = await login(configuration, configuration.users[1]);
    if (a.profileId === b.profileId) throw new Error("isolated_e2e_accounts_must_differ");
    steps.push("two_isolated_users_logged_in");
    await assertAbsent(configuration, a, configuration.postId, a.profileId, "official_like_cleanup");
    const created = await request(restUrl(configuration, "official_post_likes"), { method: "POST", headers: { ...headers(configuration, a.accessToken), Prefer: "return=representation" }, body: JSON.stringify({ official_post_id: configuration.postId, profile_id: a.profileId }) }, "official_like_insert_failed");
    if (!created.ok || !Array.isArray(created.body) || created.body.length !== 1 || !uuid.test(created.body[0]?.id ?? "")) throw new Error(`official_like_insert_failed:http_${created.status}`);
    likeId = created.body[0].id; steps.push("a_created_own_like");
    const spoof = await request(restUrl(configuration, "official_post_likes"), { method: "POST", headers: { ...headers(configuration, a.accessToken), Prefer: "return=representation" }, body: JSON.stringify({ official_post_id: configuration.postId, profile_id: b.profileId }) }, "official_like_spoof_not_denied");
    if (!is42501(spoof)) throw new Error(`official_like_spoof_not_denied:http_${spoof.status}`);
    await assertAbsent(configuration, a, configuration.postId, b.profileId, "official_like_spoof_persisted"); steps.push("a_spoofed_b_profile_rejected_42501");
    const crossDelete = await request(restUrl(configuration, "official_post_likes", { id: `eq.${likeId}`, profile_id: `eq.${a.profileId}` }), { method: "DELETE", headers: { ...headers(configuration, b.accessToken), Prefer: "return=representation" } }, "official_like_cross_delete_not_denied");
    if (!is42501(crossDelete)) throw new Error(`official_like_cross_delete_not_denied:http_${crossDelete.status}`);
    const stillThere = await rows(configuration, a, { select: "id", id: `eq.${likeId}`, limit: "1" }, "official_like_cross_delete_changed_row");
    if (stillThere.length !== 1 || stillThere[0]?.id !== likeId) throw new Error("official_like_cross_delete_changed_row"); steps.push("b_cross_delete_rejected_42501");
    const deleted = await request(restUrl(configuration, "official_post_likes", { id: `eq.${likeId}`, profile_id: `eq.${a.profileId}` }), { method: "DELETE", headers: { ...headers(configuration, a.accessToken), Prefer: "return=representation" } }, "official_like_delete_failed");
    if (!deleted.ok) throw new Error(`official_like_delete_failed:http_${deleted.status}`);
    await assertAbsent(configuration, a, configuration.postId, a.profileId, "official_like_cleanup"); likeId = null; steps.push("a_deleted_own_like_and_absence_verified");
    await revoke(configuration, a); a = null; await revoke(configuration, b); b = null; cleanup = { state: "like_absence_verified_sessions_revoked_external_fixture_purge_required" }; steps.push("both_sessions_revoked");
    await report(output, { check: "SB-09", status: "passed_with_external_fixture_purge_pending", startedAt, finishedAt: new Date().toISOString(), mode: "two_isolated_users_public_key_official_like", steps, cleanup, mutationPolicy: "Public key plus isolated-user JWTs only. No service-role, database URL, SQL, DDL, RPC or schema changes. The separate fixture owner hard-purges all three isolated accounts and verifies Auth/profile absence." });
  } catch (error) {
    if (configuration && a && likeId) {
      try { const rollback = await request(restUrl(configuration, "official_post_likes", { id: `eq.${likeId}`, profile_id: `eq.${a.profileId}` }), { method: "DELETE", headers: { ...headers(configuration, a.accessToken), Prefer: "return=representation" } }, "official_like_cleanup"); cleanup = rollback.ok ? { state: "created_like_deleted_after_failure_external_fixture_purge_required" } : { state: "rollback_pending", action: "delete the isolated actor like before fixture purge" }; } catch { cleanup = { state: "rollback_pending", action: "delete the isolated actor like before fixture purge" }; }
    }
    for (const session of [a, b]) if (configuration && session) { try { await revoke(configuration, session); } catch { cleanup.sessionRevocation = "pending"; } }
    console.error(JSON.stringify({ check: "SB-09", startedAt, finishedAt: new Date().toISOString(), cleanup, ...safeFailure(error) })); process.exitCode = 1;
  }
}
main().catch((error) => { console.error(JSON.stringify({ check: "SB-09", status: "failed", ...safeFailure(error) })); process.exitCode = 1; });
