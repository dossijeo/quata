#!/usr/bin/env node
/**
 * SB-06: authenticated Profile/SOS contract using the same PostgREST shapes as
 * ProfileRemoteGateway. It deliberately works only on an isolated account whose
 * SOS set is empty before the run, so every created row has a deterministic delete.
 */
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const required = [
  "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY",
  "QUATA_E2E_PROFILE_COUNTRY_CODE", "QUATA_E2E_PROFILE_PHONE", "QUATA_E2E_PROFILE_PASSWORD",
  "QUATA_E2E_PROFILE_SOS_SCOPE", "QUATA_E2E_PROFILE_SOS_CLEANUP", "QUATA_E2E_PROFILE_SOS_CONTACT_IDS",
];
const profileSelect = "id,display_name,nombre,neighborhood,barrio,country_code,code,phone_local,phone_e164,phone,telefono,avatar_url,avatar,secret_question";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function args(argv) {
  if (argv.length === 4 && argv[0] === "--allow-profile-mutation" && argv[1] === "--allow-sos-contact-mutation" && argv[2] === "--out" && argv[3].trim()) return { output: argv[3] };
  if (argv.length === 1 && argv[0] === "--help") { console.log("Usage: node scripts/supabase-e2e-sb06.mjs --allow-profile-mutation --allow-sos-contact-mutation --out <safe-local-report.json>"); process.exit(0); }
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
  if (process.env.QUATA_E2E_PROFILE_SOS_SCOPE !== "isolated_sb06_profile") throw new Error("isolated_profile_scope_missing");
  if (process.env.QUATA_E2E_PROFILE_SOS_CLEANUP !== "restore_display_name_and_delete_empty_contact_set") throw new Error("safe_cleanup_contract_missing");
  const requested = process.env.QUATA_E2E_PROFILE_SOS_CONTACT_IDS.split(",").map((id) => id.trim()).filter(Boolean);
  const contacts = [...new Set(requested)];
  if (!contacts.length || contacts.length > 5 || contacts.some((id) => !uuid.test(id))) throw new Error("invalid_sos_contact_identifiers");
  return { baseUrl, key, countryCode: process.env.QUATA_E2E_PROFILE_COUNTRY_CODE.trim(), phone: process.env.QUATA_E2E_PROFILE_PHONE.trim(), password: process.env.QUATA_E2E_PROFILE_PASSWORD, contacts };
}
function headers(configuration, token) { return { apikey: configuration.key, "content-type": "application/json", accept: "application/json", "x-client-info": "quata-e2e-sb06", ...(token ? { authorization: `Bearer ${token}` } : {}) }; }
async function request(url, options, prefix) {
  let response;
  try { response = await fetch(url, { ...options, signal: AbortSignal.timeout(15_000) }); } catch { throw new Error(`${prefix}:network`); }
  const text = await response.text();
  if (!response.ok) throw new Error(`${prefix}:http_${response.status}`);
  if (!text) return null;
  try { return JSON.parse(text); } catch { throw new Error(`${prefix}:invalid_json`); }
}
async function login(configuration) {
  const payload = await request(`${configuration.baseUrl}/functions/v1/quata-auth-bridge`, { method: "POST", headers: headers(configuration), body: JSON.stringify({ action: "web_login", country_code: configuration.countryCode, phone_local: configuration.phone, password: configuration.password, client_instance_id: `e2e-sb06-${crypto.randomUUID()}` }) }, "public_auth_request_failed");
  const profileId = payload?.profile?.id, accessToken = payload?.session?.access_token;
  if (!uuid.test(profileId ?? "") || typeof accessToken !== "string" || !accessToken) throw new Error("invalid_auth_response:profile_or_session");
  return { profileId, accessToken };
}
function restUrl(configuration, table, query = {}) { const url = new URL(`${configuration.baseUrl}/rest/v1/${table}`); for (const [key, value] of Object.entries(query)) url.searchParams.set(key, value); return url; }
async function rows(configuration, session, table, query, prefix) {
  const body = await request(restUrl(configuration, table, query), { headers: headers(configuration, session.accessToken) }, prefix);
  if (!Array.isArray(body)) throw new Error(`${prefix}:not_array`);
  return body;
}
async function patch(configuration, session, profileId, displayName) {
  const body = await request(restUrl(configuration, "community_profiles", { id: `eq.${profileId}`, select: "id,display_name" }), { method: "PATCH", headers: { ...headers(configuration, session.accessToken), Prefer: "return=representation" }, body: JSON.stringify({ display_name: displayName }) }, "profile_patch_failed");
  if (!Array.isArray(body) || body.length !== 1 || body[0]?.id !== profileId) throw new Error("profile_patch_failed:unexpected_row");
  return body[0];
}
async function deleteContacts(configuration, session, profileId) {
  await request(restUrl(configuration, "community_emergency_contacts", { profile_id: `eq.${profileId}` }), { method: "DELETE", headers: { ...headers(configuration, session.accessToken), Prefer: "return=representation" } }, "sos_cleanup_failed");
}
async function verifyNoContacts(configuration, session, profileId) {
  const remaining = await rows(configuration, session, "community_emergency_contacts", { select: "contact_profile_id", profile_id: `eq.${profileId}`, limit: "1" }, "sos_cleanup_failed");
  if (remaining.length) throw new Error("sos_cleanup_failed:contacts_remain");
}
async function restoreDisplayName(configuration, session, profileId, displayName) {
  await patch(configuration, session, profileId, displayName);
  const restored = await rows(configuration, session, "community_profiles", { select: "id,display_name", id: `eq.${profileId}`, limit: "1" }, "profile_patch_failed");
  if (restored.length !== 1 || restored[0]?.id !== profileId || restored[0]?.display_name !== displayName) throw new Error("profile_patch_failed:restore_not_verified");
}
async function revoke(configuration, session) {
  await request(`${configuration.baseUrl}/auth/v1/logout`, { method: "POST", headers: headers(configuration, session.accessToken), body: JSON.stringify({ scope: "global" }) }, "session_revocation_failed");
}
function normalizeContactIds(ids) { return [...new Set(ids.map((id) => id.trim()).filter(Boolean))]; }
function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "unknown";
  const known = ["invalid_arguments", "missing_environment", "invalid_public_supabase_url", "invalid_or_privileged_supabase_key", "isolated_profile_scope_missing", "safe_cleanup_contract_missing", "invalid_sos_contact_identifiers", "public_auth_request_failed", "invalid_auth_response", "profile_read_failed", "sos_read_failed", "profile_patch_failed", "sos_insert_failed", "sos_cleanup_failed", "session_revocation_failed", "profile_contract_invalid", "sos_contract_invalid"];
  return { status: "failed", error: known.find((prefix) => message.startsWith(prefix)) ?? "unexpected_profile_sos_runner_failure" };
}
async function report(output, payload) { const target = resolve(output); await mkdir(dirname(target), { recursive: true }); await writeFile(target, `${JSON.stringify(payload, null, 2)}\n`, { encoding: "utf8", mode: 0o600 }); console.log(`SB-06 report written: ${target}`); }

async function main() {
  const { output } = args(process.argv.slice(2)); const startedAt = new Date().toISOString(); const steps = [];
  let configuration; let session; let originalDisplayName; let profilePatchAttempted = false; let contactsMutationAttempted = false; let cleanup = { state: "not_started" };
  try {
    configuration = config(); // All consent, identity and delete-only constraints precede network access.
    session = await login(configuration); steps.push("isolated_profile_logged_in");
    if (configuration.contacts.includes(session.profileId)) throw new Error("sos_contract_invalid:self_contact_rejected");
    const profiles = await rows(configuration, session, "community_profiles", { select: profileSelect, id: `eq.${session.profileId}`, limit: "1" }, "profile_read_failed");
    if (profiles.length !== 1 || profiles[0]?.id !== session.profileId) throw new Error("profile_contract_invalid:authenticated_profile_missing");
    if (!Object.hasOwn(profiles[0], "display_name") || (profiles[0].display_name !== null && typeof profiles[0].display_name !== "string")) throw new Error("profile_contract_invalid:display_name_snapshot_missing");
    originalDisplayName = profiles[0].display_name; steps.push("profile_gateway_projection_read");
    const before = await rows(configuration, session, "community_emergency_contacts", { select: "contact_profile_id,position", profile_id: `eq.${session.profileId}`, order: "position.asc,created_at.asc", limit: "6" }, "sos_read_failed");
    if (before.length !== 0) throw new Error("sos_contract_invalid:preexisting_contacts_require_external_restore_plan");
    steps.push("empty_sos_set_confirmed");
    const candidates = await rows(configuration, session, "community_profiles", { select: "id", id: `in.(${configuration.contacts.join(",")})`, limit: String(configuration.contacts.length) }, "profile_read_failed");
    if (new Set(candidates.map((row) => row?.id)).size !== configuration.contacts.length) throw new Error("sos_contract_invalid:approved_candidates_not_visible");
    steps.push("up_to_five_sos_candidates_visible");
    const marker = `e2e-sb06-${crypto.randomUUID()}`;
    profilePatchAttempted = true;
    await patch(configuration, session, session.profileId, marker); steps.push("profile_display_name_patch");
    const patched = await rows(configuration, session, "community_profiles", { select: "id,display_name", id: `eq.${session.profileId}`, limit: "1" }, "profile_read_failed");
    if (patched[0]?.display_name !== marker) throw new Error("profile_contract_invalid:patch_not_visible"); steps.push("profile_patch_readback");
    const normalized = normalizeContactIds(configuration.contacts);
    contactsMutationAttempted = true;
    const inserted = await request(restUrl(configuration, "community_emergency_contacts", { select: "contact_profile_id,position" }), { method: "POST", headers: { ...headers(configuration, session.accessToken), Prefer: "return=representation" }, body: JSON.stringify(normalized.map((contact_profile_id, index) => ({ profile_id: session.profileId, contact_profile_id, position: index + 1 }))) }, "sos_insert_failed");
    if (!Array.isArray(inserted) || inserted.length !== normalized.length) throw new Error("sos_contract_invalid:insert_count"); steps.push("sos_contacts_inserted");
    const after = await rows(configuration, session, "community_emergency_contacts", { select: "contact_profile_id,position", profile_id: `eq.${session.profileId}`, order: "position.asc,created_at.asc", limit: "6" }, "sos_read_failed");
    if (JSON.stringify(after.map((row) => [row?.contact_profile_id, row?.position])) !== JSON.stringify(normalized.map((id, index) => [id, index + 1]))) throw new Error("sos_contract_invalid:normalized_order");
    steps.push("sos_contacts_normalized_to_kmp_contract");
    await deleteContacts(configuration, session, session.profileId);
    await verifyNoContacts(configuration, session, session.profileId); contactsMutationAttempted = false; steps.push("created_sos_contacts_deleted_and_verified");
    await restoreDisplayName(configuration, session, session.profileId, originalDisplayName); profilePatchAttempted = false; steps.push("profile_display_name_restored_exactly");
    await revoke(configuration, session); session = null; cleanup = { state: "profile_field_restored_and_created_contacts_deleted" }; steps.push("session_revoked");
    await report(output, { check: "SB-06", status: "passed", startedAt, finishedAt: new Date().toISOString(), mode: "public_key_authenticated_isolated_profile", steps, cleanup, mutationPolicy: "Public key plus isolated-user JWT only. No service-role, database URL, SQL, DDL, RPC, schema changes, or user creation. The runner patches only display_name, snapshots it first, and only creates SOS rows after proving the preexisting set is empty." });
  } catch (error) {
    const actions = [];
    if (configuration && session) {
      try { if (contactsMutationAttempted) { await deleteContacts(configuration, session, session.profileId); await verifyNoContacts(configuration, session, session.profileId); contactsMutationAttempted = false; actions.push("created_sos_contacts_deleted_and_verified_after_failure"); } if (profilePatchAttempted) { await restoreDisplayName(configuration, session, session.profileId, originalDisplayName); profilePatchAttempted = false; actions.push("display_name_restored_and_verified_after_failure"); } cleanup = { state: "rolled_back_after_failure", actions }; }
      catch { cleanup = { state: "rollback_pending", action: "restore the isolated profile display_name and delete every community_emergency_contacts row for that isolated profile before any later E2E run" }; }
      try { await revoke(configuration, session); } catch { cleanup.sessionRevocation = "pending"; }
    }
    console.error(JSON.stringify({ check: "SB-06", startedAt, finishedAt: new Date().toISOString(), cleanup, ...safeFailure(error) })); process.exitCode = 1;
  }
}
main().catch((error) => { console.error(JSON.stringify({ check: "SB-06", status: "failed", ...safeFailure(error) })); process.exitCode = 1; });
