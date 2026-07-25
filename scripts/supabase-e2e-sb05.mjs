#!/usr/bin/env node
/**
 * SB-05: authenticated Chat attachment / Storage contract.
 *
 * This runner only uses the public Supabase key and two pre-provisioned,
 * isolated E2E accounts. It deliberately cannot hard-delete chat rows: the
 * current public Chat RPCs only soft-delete messages. Therefore the explicit
 * external account-purge contract is checked before the first network call and
 * a successful run is still reported as pending that independently verified
 * purge. Do not weaken this gate by passing normal user accounts to it.
 */
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve, relative } from "node:path";

const required = [
  "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY",
  "QUATA_E2E_CHAT_A_COUNTRY_CODE", "QUATA_E2E_CHAT_A_PHONE", "QUATA_E2E_CHAT_A_PASSWORD",
  "QUATA_E2E_CHAT_B_COUNTRY_CODE", "QUATA_E2E_CHAT_B_PHONE", "QUATA_E2E_CHAT_B_PASSWORD",
  "QUATA_E2E_CHAT_A_E2E_SCOPE", "QUATA_E2E_CHAT_B_E2E_SCOPE",
  "QUATA_E2E_SB05_EXTERNAL_HARD_CLEANUP",
];
const isolatedScope = "isolated_sb05_attachment_account";
const cleanupApproval = "approved_isolated_account_purge_and_attachment_verification";
const bucket = "chat-attachments";

function args(argv) {
  if (argv.length === 4 && argv[0] === "--allow-existing-test-data" && argv[1] === "--allow-chat-attachment-mutation" && argv[2] === "--out" && argv[3].trim()) return { output: argv[3] };
  if (argv.length === 1 && argv[0] === "--help") {
    console.log("Usage: node scripts/supabase-e2e-sb05.mjs --allow-existing-test-data --allow-chat-attachment-mutation --out <safe-local-report.json>");
    process.exit(0);
  }
  throw new Error("invalid_arguments");
}

function isPublicKey(value) {
  if (value.startsWith("sb_secret_") || value.toLowerCase().includes("service_role")) return false;
  const parts = value.split(".");
  if (parts.length !== 3) return true; // sb_publishable_* is an opaque public key.
  try { return JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"))?.role !== "service_role"; }
  catch { return false; }
}

function config() {
  const missing = required.filter((name) => !process.env[name]?.trim());
  if (missing.length) throw new Error(`missing_environment:${missing.join(",")}`);
  const baseUrl = process.env.QUATA_SUPABASE_URL.trim().replace(/\/+$/, "");
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(baseUrl)) throw new Error("invalid_public_supabase_url");
  const key = process.env.QUATA_SUPABASE_PUBLISHABLE_KEY.trim();
  if (!isPublicKey(key)) throw new Error("invalid_or_privileged_supabase_key");
  if (process.env.QUATA_E2E_SB05_EXTERNAL_HARD_CLEANUP !== cleanupApproval) throw new Error("attachment_row_hard_cleanup_contract_missing");
  if (["A", "B"].some((label) => process.env[`QUATA_E2E_CHAT_${label}_E2E_SCOPE`] !== isolatedScope)) throw new Error("isolated_e2e_attachment_account_scope_missing");
  const identityA = `${process.env.QUATA_E2E_CHAT_A_COUNTRY_CODE.trim()}|${process.env.QUATA_E2E_CHAT_A_PHONE.trim()}`;
  const identityB = `${process.env.QUATA_E2E_CHAT_B_COUNTRY_CODE.trim()}|${process.env.QUATA_E2E_CHAT_B_PHONE.trim()}`;
  if (identityA === identityB) throw new Error("isolated_e2e_accounts_must_differ");
  return {
    baseUrl, key,
    users: ["A", "B"].map((label) => ({
      label,
      countryCode: process.env[`QUATA_E2E_CHAT_${label}_COUNTRY_CODE`].trim(),
      phone: process.env[`QUATA_E2E_CHAT_${label}_PHONE`].trim(),
      password: process.env[`QUATA_E2E_CHAT_${label}_PASSWORD`],
    })),
  };
}

function headers(key, token, contentType = "application/json") {
  return { apikey: key, ...(contentType ? { "content-type": contentType } : {}), "x-client-info": "quata-e2e-sb05", ...(token ? { authorization: `Bearer ${token}` } : {}) };
}

async function request(url, options, failurePrefix) {
  let response;
  try { response = await fetch(url, { ...options, signal: AbortSignal.timeout(15_000) }); }
  catch { throw new Error(`${failurePrefix}:network`); }
  const body = await response.text();
  if (!response.ok) throw new Error(`${failurePrefix}:http_${response.status}`);
  return { response, body };
}

async function jsonPost(url, requestHeaders, body, failurePrefix) {
  const result = await request(url, { method: "POST", headers: requestHeaders, body: JSON.stringify(body) }, failurePrefix);
  try { return JSON.parse(result.body); } catch { throw new Error(`${failurePrefix}:invalid_json`); }
}

async function login(configuration, user) {
  const payload = await jsonPost(`${configuration.baseUrl}/functions/v1/quata-auth-bridge`, headers(configuration.key), {
    action: "web_login", country_code: user.countryCode, phone_local: user.phone, password: user.password,
    client_instance_id: `e2e-sb05-${user.label.toLowerCase()}-${crypto.randomUUID()}`,
  }, "public_auth_request_failed");
  const profileId = payload?.profile?.id, accessToken = payload?.session?.access_token;
  if (typeof profileId !== "string" || !profileId || typeof accessToken !== "string" || !accessToken) throw new Error("invalid_auth_response:profile_or_session");
  return { label: user.label, profileId, accessToken };
}

function rpc(configuration, session, name, body) {
  return jsonPost(`${configuration.baseUrl}/rest/v1/rpc/${name}`, headers(configuration.key, session.accessToken), body, `chat_rpc_failed:${name}`);
}

function positiveId(value, name) {
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error(`chat_attachment_contract_invalid:${name}`);
  return value;
}

function threadId(payload) { return positiveId(payload?.thread?.id ?? payload?.threads?.[0]?.id ?? payload?.thread_id, "thread_id"); }
function attachmentId(payload) { return positiveId(payload?.id ?? payload?.file?.id, "attachment_id"); }
function messageId(payload) { return positiveId(payload?.message?.id ?? payload?.message_id, "message_id"); }
function pathSegment(path) { return path.split("/").map(encodeURIComponent).join("/"); }

function attachmentInThread(payload, id) {
  const messages = [payload?.message, ...(Array.isArray(payload?.messages) ? payload.messages : []), ...(Array.isArray(payload?.update?.messages) ? payload.update.messages : [])].filter(Boolean);
  return messages.flatMap((message) => Array.isArray(message?.attachments) ? message.attachments : []).some((attachment) => attachment?.id === id);
}

async function upload(configuration, session, storagePath, bytes) {
  await request(`${configuration.baseUrl}/storage/v1/object/${bucket}/${pathSegment(storagePath)}`, {
    method: "POST",
    headers: { ...headers(configuration.key, session.accessToken, "text/plain; charset=utf-8"), "x-upsert": "false" },
    body: bytes,
  }, "storage_upload_failed");
}

async function downloadAsPeer(configuration, session, storagePath, expected) {
  const { body } = await request(`${configuration.baseUrl}/storage/v1/object/${bucket}/${pathSegment(storagePath)}`, {
    method: "GET", headers: headers(configuration.key, session.accessToken, null),
  }, "storage_peer_download_failed");
  if (body !== expected) throw new Error("chat_attachment_contract_invalid:peer_download_content");
}

async function downloadAsPublicContract(configuration, storagePath, expected) {
  const { body } = await request(`${configuration.baseUrl}/storage/v1/object/public/${bucket}/${pathSegment(storagePath)}`, {
    method: "GET", headers: { apikey: configuration.key },
  }, "storage_public_download_contract_failed");
  if (body !== expected) throw new Error("chat_attachment_contract_invalid:public_download_content");
}

async function deleteObject(configuration, session, storagePath) {
  await request(`${configuration.baseUrl}/storage/v1/object/${bucket}`, {
    method: "DELETE", headers: headers(configuration.key, session.accessToken), body: JSON.stringify({ prefixes: [storagePath] }),
  }, "storage_delete_failed");
}

async function assertPeerCannotDownload(configuration, session, storagePath) {
  let response;
  try {
    response = await fetch(`${configuration.baseUrl}/storage/v1/object/${bucket}/${pathSegment(storagePath)}`, {
      method: "GET", headers: headers(configuration.key, session.accessToken, null), signal: AbortSignal.timeout(15_000),
    });
  } catch { throw new Error("storage_post_delete_verify_failed:network"); }
  if (response.ok) throw new Error("storage_post_delete_verify_failed:object_still_downloadable");
  if (![404, 400].includes(response.status)) throw new Error(`storage_post_delete_verify_failed:http_${response.status}`);
}

async function logicalCleanup(configuration, state) {
  const actions = [];
  if (state.objectCreated && state.a && state.storagePath) {
    await deleteObject(configuration, state.a, state.storagePath);
    state.objectCreated = false;
    if (state.b) await assertPeerCannotDownload(configuration, state.b, state.storagePath);
    actions.push("storage_object_deleted_and_peer_read_denied");
  }
  if (state.thread && state.a && state.message) {
    await rpc(configuration, state.a, "quata_chat_delete_messages", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread, p_message_ids: [state.message] });
    actions.push("attachment_message_logically_deleted");
  }
  if (state.thread && state.a) { await rpc(configuration, state.a, "quata_chat_delete_thread", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread }); actions.push("thread_hidden_for_a"); }
  if (state.thread && state.b) { await rpc(configuration, state.b, "quata_chat_delete_thread", { p_actor_profile_id: state.b.profileId, p_thread_id: state.thread }); actions.push("thread_hidden_for_b"); }
  return actions;
}

async function revoke(configuration, session) {
  await request(`${configuration.baseUrl}/auth/v1/logout`, {
    method: "POST", headers: headers(configuration.key, session.accessToken), body: JSON.stringify({ scope: "global" }),
  }, "public_auth_request_failed");
}

function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "unknown";
  const known = ["invalid_arguments", "missing_environment", "invalid_public_supabase_url", "invalid_or_privileged_supabase_key", "attachment_row_hard_cleanup_contract_missing", "isolated_e2e_attachment_account_scope_missing", "isolated_e2e_accounts_must_differ", "public_auth_request_failed", "invalid_auth_response", "chat_rpc_failed", "chat_attachment_contract_invalid", "storage_upload_failed", "storage_peer_download_failed", "storage_public_download_contract_failed", "storage_delete_failed", "storage_post_delete_verify_failed"];
  return { status: "failed", error: known.find((prefix) => message.startsWith(prefix)) ?? "unexpected_attachment_runner_failure" };
}

async function report(output, body) {
  const target = resolve(output), workspace = resolve(process.cwd());
  if (relative(workspace, target).startsWith("..")) throw new Error("unsafe_report_path");
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, `${JSON.stringify(body, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  console.log(`SB-05 report written: ${target}`);
}

async function main() {
  const { output } = args(process.argv.slice(2)), startedAt = new Date().toISOString(), steps = [];
  let configuration; const state = { a: null, b: null, thread: null, attachment: null, message: null, storagePath: null, objectCreated: false };
  let cleanup = { state: "not_started" };
  try {
    configuration = config(); // Every guard above runs before authentication or Storage mutation.
    state.a = await login(configuration, configuration.users[0]);
    state.b = await login(configuration, configuration.users[1]);
    if (state.a.profileId === state.b.profileId) throw new Error("chat_attachment_contract_invalid:isolated_profiles_must_differ");
    steps.push("two_isolated_users_logged_in");
    state.thread = threadId(await rpc(configuration, state.a, "quata_chat_get_or_create_private_thread", { p_actor_profile_id: state.a.profileId, p_peer_profile_id: state.b.profileId }));
    steps.push("private_thread_opened_or_reused");
    const marker = crypto.randomUUID();
    const content = `e2e-sb05 attachment ${marker}\n`;
    state.storagePath = `${state.a.profileId}/e2e-sb05/${marker}.txt`;
    await upload(configuration, state.a, state.storagePath, content); state.objectCreated = true; steps.push("non_sensitive_blob_uploaded_by_a");
    const publicUrl = `${configuration.baseUrl}/storage/v1/object/public/${bucket}/${pathSegment(state.storagePath)}`;
    state.attachment = attachmentId(await rpc(configuration, state.a, "quata_chat_register_attachment", {
      p_actor_profile_id: state.a.profileId, p_thread_id: state.thread, p_file_url: publicUrl, p_storage_bucket: bucket,
      p_storage_path: state.storagePath, p_mime_type: "text/plain", p_name: "e2e-sb05.txt", p_size_bytes: Buffer.byteLength(content), p_ext: "txt", p_thumb: null,
    }));
    steps.push("attachment_registered");
    state.message = messageId(await rpc(configuration, state.a, "quata_chat_send_message", {
      p_actor_profile_id: state.a.profileId, p_thread_id: state.thread, p_message: "", p_file_ids: [state.attachment], p_reply_to_message_id: null, p_client_message_id: `e2e-sb05-${marker}`,
    }));
    steps.push("attachment_linked_to_message");
    const peerThread = await rpc(configuration, state.b, "quata_chat_get_thread", { p_actor_profile_id: state.b.profileId, p_thread_id: state.thread, p_known_message_ids: [], p_limit: 250 });
    if (!attachmentInThread(peerThread, state.attachment)) throw new Error("chat_attachment_contract_invalid:peer_thread_missing_attachment");
    steps.push("peer_can_observe_attachment_metadata");
    await downloadAsPeer(configuration, state.b, state.storagePath, content); steps.push("peer_session_download_matches_blob");
    // The versioned schema intentionally makes this bucket public. This is a
    // contract check, not an authorization assertion between Chat participants.
    await downloadAsPublicContract(configuration, state.storagePath, content); steps.push("public_storage_read_contract_matches_blob");
    const logicalActions = await logicalCleanup(configuration, state);
    cleanup = {
      state: "external_hard_purge_and_row_verification_pending",
      logicalActions,
      required: "authorized account-lifecycle purge deletes both isolated profiles and cascading chat_attachments rows; operator must record a post-purge row/object verification",
    };
    steps.push("storage_object_deleted_and_logical_cleanup_requested");
    await revoke(configuration, state.a); await revoke(configuration, state.b); steps.push("both_sessions_revoked");
    await report(output, { check: "SB-05", status: "passed_with_external_hard_cleanup_pending", startedAt, finishedAt: new Date().toISOString(), mode: "two_existing_isolated_users_public_key", steps, cleanup, storageReadPolicy: "chat-attachments is currently public; peer retrieval is a Chat-link check, not Storage participant isolation.", mutationPolicy: "Public key and authenticated sessions only. No service-role, DB URL, SQL, DDL, migrations or remote configuration changes." });
  } catch (error) {
    if (configuration && state.objectCreated) {
      try {
        const logicalActions = await logicalCleanup(configuration, state);
        cleanup = { state: "external_hard_purge_and_row_verification_pending_after_failure", logicalActions, required: "authorized account-lifecycle purge plus post-purge row/object verification" };
      } catch { cleanup = { state: "rollback_pending", required: "delete Storage object with owner identity, then authorized account-lifecycle purge plus post-purge verification" }; }
    }
    for (const session of [state.a, state.b]) if (configuration && session) { try { await revoke(configuration, session); } catch { cleanup.sessionRevocation = "pending"; } }
    console.error(JSON.stringify({ check: "SB-05", startedAt, finishedAt: new Date().toISOString(), cleanup, ...safeFailure(error) }));
    process.exitCode = 1;
  }
}

main().catch((error) => { console.error(JSON.stringify({ check: "SB-05", status: "failed", ...safeFailure(error) })); process.exitCode = 1; });
