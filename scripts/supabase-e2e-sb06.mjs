#!/usr/bin/env node
/**
 * SB-06: authenticated Profile/SOS contract using the same PostgREST shapes as
 * ProfileRemoteGateway. It supports either an isolated empty profile fixture or
 * the explicitly approved Gabrielo/Gabrielu lane, where the runner snapshots and
 * restores the actor's existing SOS set exactly.
 */
import { createHash, randomUUID } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const required = [
  "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY",
  "QUATA_E2E_PROFILE_COUNTRY_CODE", "QUATA_E2E_PROFILE_PHONE", "QUATA_E2E_PROFILE_PASSWORD",
  "QUATA_E2E_PROFILE_SOS_SCOPE", "QUATA_E2E_PROFILE_SOS_CLEANUP", "QUATA_E2E_PROFILE_SOS_CONTACT_IDS",
];
const credentialsFileEnvironment = "QUATA_CHAT_GROUP_CREDENTIALS_FILE";
const profileSelect = "id,display_name,nombre,neighborhood,barrio,country_code,code,phone_local,phone_e164,phone,telefono,avatar_url,avatar,secret_question";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const sharedSourceRevisionFile = "web/build/dist/wasmJs/productionExecutable/quata-source-revision.txt";

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
function sha256(value) {
  return createHash("sha256").update(String(value)).digest("hex");
}
function fingerprintRows(rows) {
  return sha256(JSON.stringify(rows)).slice(0, 16);
}
async function publicBackendConfig() {
  const configuredUrl = process.env.QUATA_SUPABASE_URL?.trim();
  const configuredKey = process.env.QUATA_SUPABASE_PUBLISHABLE_KEY?.trim();
  if (configuredUrl && configuredKey) return { baseUrl: configuredUrl.replace(/\/+$/, ""), key: configuredKey };
  const source = await readFile("core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", "utf8");
  const baseUrl = source.match(/SUPABASE_URL\s*=\s*"([^"]+)"/)?.[1]?.replace(/\/+$/, "");
  const key = source.match(/SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/)?.[1];
  if (!baseUrl || !key) throw new Error("missing_public_supabase_configuration");
  return { baseUrl, key };
}
function user(entry, label) {
  return {
    label,
    countryCode: String(entry?.country_code ?? entry?.countryCode ?? "").trim(),
    phone: String(entry?.phone ?? "").trim(),
    password: String(entry?.password ?? ""),
  };
}
async function usersFromPrivateFile() {
  const file = process.env[credentialsFileEnvironment]?.trim();
  if (!file) return null;
  const parsed = JSON.parse((await readFile(file, "utf8")).replace(/^\uFEFF/, ""));
  const users = [user(parsed.a, "A"), user(parsed.b, "B")];
  if (users.some((candidate) => !candidate.countryCode || !candidate.phone || !candidate.password)) throw new Error("missing_profile_sos_credentials");
  if (`${users[0].countryCode}|${users[0].phone}` === `${users[1].countryCode}|${users[1].phone}`) throw new Error("profile_sos_users_must_differ");
  return users;
}
async function config() {
  const missing = required.filter((name) => !process.env[name]?.trim());
  const publicConfig = await publicBackendConfig();
  const baseUrl = publicConfig.baseUrl;
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(baseUrl)) throw new Error("invalid_public_supabase_url");
  const key = publicConfig.key;
  if (!publicKey(key)) throw new Error("invalid_or_privileged_supabase_key");
  const credentialUsers = await usersFromPrivateFile();
  if (missing.length && credentialUsers) {
    return {
      baseUrl,
      key,
      users: credentialUsers,
      contacts: [],
      restoreMode: "snapshot_existing_contacts",
      mode: "two_approved_existing_profiles_public_key",
    };
  }
  if (missing.length) throw new Error(`missing_environment:${missing.join(",")}`);
  if (process.env.QUATA_E2E_PROFILE_SOS_SCOPE !== "isolated_sb06_profile") throw new Error("isolated_profile_scope_missing");
  if (process.env.QUATA_E2E_PROFILE_SOS_CLEANUP !== "restore_display_name_and_delete_empty_contact_set") throw new Error("safe_cleanup_contract_missing");
  const requested = process.env.QUATA_E2E_PROFILE_SOS_CONTACT_IDS.split(",").map((id) => id.trim()).filter(Boolean);
  const contacts = [...new Set(requested)];
  if (!contacts.length || contacts.length > 5 || contacts.some((id) => !uuid.test(id))) throw new Error("invalid_sos_contact_identifiers");
  return {
    baseUrl,
    key,
    users: [user({
      country_code: process.env.QUATA_E2E_PROFILE_COUNTRY_CODE,
      phone: process.env.QUATA_E2E_PROFILE_PHONE,
      password: process.env.QUATA_E2E_PROFILE_PASSWORD,
    }, "A")],
    contacts,
    restoreMode: "delete_empty_contact_set",
    mode: "public_key_authenticated_isolated_profile",
  };
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
async function login(configuration, loginUser) {
  const payload = await request(`${configuration.baseUrl}/functions/v1/quata-auth-bridge`, { method: "POST", headers: headers(configuration), body: JSON.stringify({ action: "web_login", country_code: loginUser.countryCode, phone_local: loginUser.phone, password: loginUser.password, client_instance_id: `e2e-sb06-${loginUser.label.toLowerCase()}-${randomUUID()}` }) }, "public_auth_request_failed");
  const profileId = payload?.profile?.id, accessToken = payload?.session?.access_token;
  if (!uuid.test(profileId ?? "") || typeof accessToken !== "string" || !accessToken) throw new Error(`invalid_auth_response:${loginUser.label}`);
  return { label: loginUser.label, profileId, accessToken };
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
async function restoreContacts(configuration, session, profileId, snapshot) {
  await deleteContacts(configuration, session, profileId);
  if (snapshot.length) {
    await request(restUrl(configuration, "community_emergency_contacts", { select: "contact_profile_id,position" }), {
      method: "POST",
      headers: { ...headers(configuration, session.accessToken), Prefer: "return=representation" },
      body: JSON.stringify(snapshot.map((row, index) => ({
        profile_id: profileId,
        contact_profile_id: row.contact_profile_id,
        position: Number(row.position) || index + 1,
      }))),
    }, "sos_cleanup_failed");
  }
  const restored = await rows(configuration, session, "community_emergency_contacts", { select: "contact_profile_id,position", profile_id: `eq.${profileId}`, order: "position.asc,created_at.asc", limit: "6" }, "sos_cleanup_failed");
  const restoredOrder = restored.map(({ contact_profile_id, position }, index) => [contact_profile_id, Number(position) || index + 1]);
  const expectedOrder = snapshot.map(({ contact_profile_id, position }, index) => [contact_profile_id, Number(position) || index + 1]);
  if (JSON.stringify(restoredOrder) !== JSON.stringify(expectedOrder)) {
    throw new Error(`sos_cleanup_failed:snapshot_restore_mismatch:expected_${fingerprintRows(expectedOrder)}:actual_${fingerprintRows(restoredOrder)}:count_${restoredOrder.length}`);
  }
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
  const known = ["invalid_arguments", "missing_environment", "missing_public_supabase_configuration", "missing_profile_sos_credentials", "profile_sos_users_must_differ", "invalid_public_supabase_url", "invalid_or_privileged_supabase_key", "isolated_profile_scope_missing", "safe_cleanup_contract_missing", "invalid_sos_contact_identifiers", "public_auth_request_failed", "invalid_auth_response", "profile_read_failed", "sos_read_failed", "profile_patch_failed", "sos_insert_failed", "sos_cleanup_failed", "session_revocation_failed", "profile_contract_invalid", "sos_contract_invalid"];
  const errorCode = known.find((prefix) => message.startsWith(prefix)) ?? "unexpected_profile_sos_runner_failure";
  const reason = message
    .replace(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/ig, "[uuid]")
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .slice(0, 240);
  return { status: "failed", error: errorCode, reason };
}
async function report(output, payload) { const target = resolve(output); await mkdir(dirname(target), { recursive: true }); await writeFile(target, `${JSON.stringify(payload, null, 2)}\n`, { encoding: "utf8", mode: 0o600 }); console.log(`SB-06 report written: ${target}`); }
async function sourceRevision() {
  try { return (await readFile(sharedSourceRevisionFile, "utf8")).trim(); } catch { return null; }
}

async function main() {
  const { output } = args(process.argv.slice(2)); const startedAt = new Date().toISOString(); const steps = [];
  let configuration; let session; let peerSession; let originalDisplayName; let originalContacts = []; let profilePatchAttempted = false; let contactsMutationAttempted = false; let cleanup = { state: "not_started" };
  try {
    configuration = await config(); // All consent, identity and delete-only constraints precede network access.
    session = await login(configuration, configuration.users[0]); steps.push("profile_sos_actor_logged_in");
    const actorProfileIdSha256 = sha256(session.profileId).slice(0, 16);
    if (configuration.users[1]) {
      peerSession = await login(configuration, configuration.users[1]);
      configuration.contacts = [peerSession.profileId];
      steps.push("profile_sos_contact_logged_in");
    }
    if (configuration.contacts.includes(session.profileId)) throw new Error("sos_contract_invalid:self_contact_rejected");
    const profiles = await rows(configuration, session, "community_profiles", { select: profileSelect, id: `eq.${session.profileId}`, limit: "1" }, "profile_read_failed");
    if (profiles.length !== 1 || profiles[0]?.id !== session.profileId) throw new Error("profile_contract_invalid:authenticated_profile_missing");
    if (!Object.hasOwn(profiles[0], "display_name") || (profiles[0].display_name !== null && typeof profiles[0].display_name !== "string")) throw new Error("profile_contract_invalid:display_name_snapshot_missing");
    originalDisplayName = profiles[0].display_name; steps.push("profile_gateway_projection_read");
    const before = await rows(configuration, session, "community_emergency_contacts", { select: "contact_profile_id,position", profile_id: `eq.${session.profileId}`, order: "position.asc,created_at.asc", limit: "6" }, "sos_read_failed");
    originalContacts = before.map(({ contact_profile_id, position }, index) => ({ contact_profile_id, position: Number(position) || index + 1 }));
    if (configuration.restoreMode === "delete_empty_contact_set" && before.length !== 0) throw new Error("sos_contract_invalid:preexisting_contacts_require_external_restore_plan");
    steps.push(configuration.restoreMode === "snapshot_existing_contacts" ? "existing_sos_set_snapshotted_for_restore" : "empty_sos_set_confirmed");
    const candidates = await rows(configuration, session, "community_profiles", { select: "id", id: `in.(${configuration.contacts.join(",")})`, limit: String(configuration.contacts.length) }, "profile_read_failed");
    if (new Set(candidates.map((row) => row?.id)).size !== configuration.contacts.length) throw new Error("sos_contract_invalid:approved_candidates_not_visible");
    steps.push("up_to_five_sos_candidates_visible");
    const marker = `e2e-sb06-${randomUUID()}`;
    profilePatchAttempted = true;
    await patch(configuration, session, session.profileId, marker); steps.push("profile_display_name_patch");
    const patched = await rows(configuration, session, "community_profiles", { select: "id,display_name", id: `eq.${session.profileId}`, limit: "1" }, "profile_read_failed");
    if (patched[0]?.display_name !== marker) throw new Error("profile_contract_invalid:patch_not_visible"); steps.push("profile_patch_readback");
    const normalized = normalizeContactIds(configuration.contacts);
    contactsMutationAttempted = true;
    await deleteContacts(configuration, session, session.profileId); steps.push("sos_existing_contacts_deleted_before_replace");
    const inserted = await request(restUrl(configuration, "community_emergency_contacts", { select: "contact_profile_id,position" }), { method: "POST", headers: { ...headers(configuration, session.accessToken), Prefer: "return=representation" }, body: JSON.stringify(normalized.map((contact_profile_id, index) => ({ profile_id: session.profileId, contact_profile_id, position: index + 1 }))) }, "sos_insert_failed");
    if (!Array.isArray(inserted) || inserted.length !== normalized.length) throw new Error("sos_contract_invalid:insert_count"); steps.push("sos_contacts_inserted");
    const after = await rows(configuration, session, "community_emergency_contacts", { select: "contact_profile_id,position", profile_id: `eq.${session.profileId}`, order: "position.asc,created_at.asc", limit: "6" }, "sos_read_failed");
    const actualOrder = after.map((row, index) => [row?.contact_profile_id, Number(row?.position) || index + 1]);
    const expectedOrder = normalized.map((id, index) => [id, index + 1]);
    if (JSON.stringify(actualOrder) !== JSON.stringify(expectedOrder)) throw new Error(`sos_contract_invalid:normalized_order:expected_${fingerprintRows(expectedOrder)}:actual_${fingerprintRows(actualOrder)}:count_${actualOrder.length}`);
    steps.push("sos_contacts_normalized_to_kmp_contract");
    await deleteContacts(configuration, session, session.profileId);
    if (configuration.restoreMode === "snapshot_existing_contacts") {
      await restoreContacts(configuration, session, session.profileId, originalContacts);
      contactsMutationAttempted = false; steps.push("sos_contacts_restored_from_snapshot_and_verified");
    } else {
      await verifyNoContacts(configuration, session, session.profileId);
      contactsMutationAttempted = false; steps.push("created_sos_contacts_deleted_and_verified");
    }
    await restoreDisplayName(configuration, session, session.profileId, originalDisplayName); profilePatchAttempted = false; steps.push("profile_display_name_restored_exactly");
    await revoke(configuration, session); session = null; if (peerSession) { await revoke(configuration, peerSession).catch(() => {}); peerSession = null; } cleanup = { state: configuration.restoreMode === "snapshot_existing_contacts" ? "profile_field_and_contact_snapshot_restored" : "profile_field_restored_and_created_contacts_deleted" }; steps.push("session_revoked");
    await report(output, {
      check: "SB-06",
      status: "passed",
      startedAt,
      finishedAt: new Date().toISOString(),
      sourceRevision: await sourceRevision(),
      mode: configuration.mode,
      steps,
      cleanup,
      actor: { profileIdSha256: actorProfileIdSha256 },
      contactCount: configuration.contacts.length,
      mutationPolicy: configuration.restoreMode === "snapshot_existing_contacts"
        ? "Public key plus approved existing user JWTs only. No service-role, database URL, SQL, DDL, RPC, schema changes, or user creation. The runner snapshots display_name and SOS rows before mutation, then restores the exact display_name and contact snapshot."
        : "Public key plus isolated-user JWT only. No service-role, database URL, SQL, DDL, RPC, schema changes, or user creation. The runner patches only display_name, snapshots it first, and only creates SOS rows after proving the preexisting set is empty.",
    });
  } catch (error) {
    const actions = [];
    if (configuration && session) {
      try { if (contactsMutationAttempted) { if (configuration.restoreMode === "snapshot_existing_contacts") { await restoreContacts(configuration, session, session.profileId, originalContacts); actions.push("sos_contacts_restored_from_snapshot_after_failure"); } else { await deleteContacts(configuration, session, session.profileId); await verifyNoContacts(configuration, session, session.profileId); actions.push("created_sos_contacts_deleted_and_verified_after_failure"); } contactsMutationAttempted = false; } if (profilePatchAttempted) { await restoreDisplayName(configuration, session, session.profileId, originalDisplayName); profilePatchAttempted = false; actions.push("display_name_restored_and_verified_after_failure"); } cleanup = { state: "rolled_back_after_failure", actions }; }
      catch { cleanup = { state: "rollback_pending", action: configuration?.restoreMode === "snapshot_existing_contacts" ? "restore the approved profile display_name and SOS contact snapshot before any later E2E run" : "restore the isolated profile display_name and delete every community_emergency_contacts row for that isolated profile before any later E2E run" }; }
      try { await revoke(configuration, session); } catch { cleanup.sessionRevocation = "pending"; }
      if (peerSession) await revoke(configuration, peerSession).catch(() => {});
    }
    const failure = { check: "SB-06", startedAt, finishedAt: new Date().toISOString(), cleanup, ...safeFailure(error) };
    await report(output, failure).catch(() => {});
    console.error(JSON.stringify(failure)); process.exitCode = 1;
  }
}
main().catch((error) => { console.error(JSON.stringify({ check: "SB-06", status: "failed", ...safeFailure(error) })); process.exitCode = 1; });
