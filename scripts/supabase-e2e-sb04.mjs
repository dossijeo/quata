#!/usr/bin/env node
/**
 * SB-04: two-user authenticated Chat RPC contract.
 * It never accepts credentials, URLs or domain identifiers as arguments.
 */
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const required = [
  "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY",
  "QUATA_E2E_CHAT_A_COUNTRY_CODE", "QUATA_E2E_CHAT_A_PHONE", "QUATA_E2E_CHAT_A_PASSWORD",
  "QUATA_E2E_CHAT_B_COUNTRY_CODE", "QUATA_E2E_CHAT_B_PHONE", "QUATA_E2E_CHAT_B_PASSWORD",
  "QUATA_E2E_CHAT_A_E2E_SCOPE", "QUATA_E2E_CHAT_B_E2E_SCOPE",
  "QUATA_E2E_CHAT_EXTERNAL_HARD_CLEANUP",
];
const cleanupAck = "approved_isolated_account_purge";
const isolatedAccountScope = "isolated_sb04_account";

function args(argv) {
  if (argv.length === 4 && argv[0] === "--allow-existing-test-data" && argv[1] === "--allow-chat-mutation" && argv[2] === "--out" && argv[3].trim()) return { output: argv[3] };
  if (argv.length === 1 && argv[0] === "--help") { console.log("Usage: node scripts/supabase-e2e-sb04.mjs --allow-existing-test-data --allow-chat-mutation --out <safe-local-report.json>"); process.exit(0); }
  throw new Error("invalid_arguments");
}
function isPublicKey(value) {
  if (value.startsWith("sb_secret_") || value.toLowerCase().includes("service_role")) return false;
  const parts = value.split(".");
  if (parts.length !== 3) return true; // Current sb_publishable_* keys are opaque.
  try {
    const payload = JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"));
    return payload?.role !== "service_role";
  } catch { return false; }
}
function config() {
  const missing = required.filter((name) => !process.env[name]?.trim());
  if (missing.length) throw new Error(`missing_environment:${missing.join(",")}`);
  const baseUrl = process.env.QUATA_SUPABASE_URL.trim().replace(/\/+$/, "");
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(baseUrl)) throw new Error("invalid_public_supabase_url");
  const key = process.env.QUATA_SUPABASE_PUBLISHABLE_KEY.trim();
  if (!isPublicKey(key)) throw new Error("invalid_or_privileged_supabase_key");
  // Current delete RPCs retain events, states and soft-deleted rows. Do not mutate
  // until an operator has explicitly accepted the separate, authorized hard purge.
  if (process.env.QUATA_E2E_CHAT_EXTERNAL_HARD_CLEANUP !== cleanupAck) throw new Error("safe_cleanup_contract_missing");
  if (["A", "B"].some((label) => process.env[`QUATA_E2E_CHAT_${label}_E2E_SCOPE`] !== isolatedAccountScope)) throw new Error("isolated_e2e_account_scope_missing");
  const accountA = `${process.env.QUATA_E2E_CHAT_A_COUNTRY_CODE.trim()}|${process.env.QUATA_E2E_CHAT_A_PHONE.trim()}`;
  const accountB = `${process.env.QUATA_E2E_CHAT_B_COUNTRY_CODE.trim()}|${process.env.QUATA_E2E_CHAT_B_PHONE.trim()}`;
  if (accountA === accountB) throw new Error("isolated_e2e_accounts_must_differ");
  return {
    baseUrl, key,
    users: ["A", "B"].map((label) => ({
      label, countryCode: process.env[`QUATA_E2E_CHAT_${label}_COUNTRY_CODE`].trim(),
      phone: process.env[`QUATA_E2E_CHAT_${label}_PHONE`].trim(),
      password: process.env[`QUATA_E2E_CHAT_${label}_PASSWORD`],
    })),
  };
}
function headers(key, token) {
  return { apikey: key, "content-type": "application/json", "x-client-info": "quata-e2e-sb04", ...(token ? { authorization: `Bearer ${token}` } : {}) };
}
async function post(url, requestHeaders, body, errorPrefix) {
  let response;
  try { response = await fetch(url, { method: "POST", headers: requestHeaders, body: JSON.stringify(body), signal: AbortSignal.timeout(15_000) }); }
  catch { throw new Error(`${errorPrefix}:network`); }
  const text = await response.text();
  if (!response.ok) throw new Error(`${errorPrefix}:http_${response.status}`);
  try { return JSON.parse(text); } catch { throw new Error(`${errorPrefix}:invalid_json`); }
}
async function login(configuration, user) {
  const payload = await post(`${configuration.baseUrl}/functions/v1/quata-auth-bridge`, headers(configuration.key), {
    action: "web_login", country_code: user.countryCode, phone_local: user.phone, password: user.password,
    client_instance_id: `e2e-sb04-${user.label.toLowerCase()}-${crypto.randomUUID()}`,
  }, "public_auth_request_failed");
  const profileId = payload?.profile?.id, accessToken = payload?.session?.access_token;
  if (typeof profileId !== "string" || !profileId || typeof accessToken !== "string" || !accessToken) throw new Error("invalid_auth_response:profile_or_session");
  return { label: user.label, profileId, accessToken };
}
function rpc(configuration, session, name, body) {
  return post(`${configuration.baseUrl}/rest/v1/rpc/${name}`, headers(configuration.key, session.accessToken), body, `chat_rpc_failed:${name}`);
}
async function revoke(configuration, session) {
  let response;
  try {
    response = await fetch(`${configuration.baseUrl}/auth/v1/logout`, {
      method: "POST", headers: headers(configuration.key, session.accessToken), body: JSON.stringify({ scope: "global" }), signal: AbortSignal.timeout(15_000),
    });
  } catch { throw new Error("public_auth_request_failed:network"); }
  if (!response.ok) throw new Error(`public_auth_request_failed:http_${response.status}`);
}
function id(value, name) {
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error(`chat_contract_invalid:${name}`);
  return value;
}
function threadId(payload) { return id(payload?.thread?.id ?? payload?.threads?.[0]?.id ?? payload?.thread_id, "thread_id"); }
function messageId(payload) { return id(payload?.message?.id ?? payload?.message_id, "message_id"); }
function findMessage(payload, messageId) {
  return [payload?.message, ...(Array.isArray(payload?.messages) ? payload.messages : []), ...(Array.isArray(payload?.update?.messages) ? payload.update.messages : [])].find((item) => item?.id === messageId);
}
function assertThread(payload, expected) {
  const threads = [...(Array.isArray(payload?.threads) ? payload.threads : []), payload?.thread].filter(Boolean);
  if (!threads.some((item) => item?.id === expected)) throw new Error("chat_contract_invalid:inbox_missing_thread");
}
async function logicalCleanup(configuration, state) {
  // Best-effort local rollback. It is intentionally not reported as hard cleanup.
  const actions = [];
  if (state.thread && state.a && state.messageA) { await rpc(configuration, state.a, "quata_chat_delete_messages", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread, p_message_ids: [state.messageA] }); actions.push("sender_a_message_logically_deleted"); }
  if (state.thread && state.b && state.messageB) { await rpc(configuration, state.b, "quata_chat_delete_messages", { p_actor_profile_id: state.b.profileId, p_thread_id: state.thread, p_message_ids: [state.messageB] }); actions.push("sender_b_reply_logically_deleted"); }
  if (state.thread && state.a) { await rpc(configuration, state.a, "quata_chat_delete_thread", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread }); actions.push("thread_hidden_for_a"); }
  if (state.thread && state.b) { await rpc(configuration, state.b, "quata_chat_delete_thread", { p_actor_profile_id: state.b.profileId, p_thread_id: state.thread }); actions.push("thread_hidden_for_b"); }
  return actions;
}
function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "unknown";
  const known = ["invalid_arguments", "missing_environment", "invalid_public_supabase_url", "invalid_or_privileged_supabase_key", "safe_cleanup_contract_missing", "isolated_e2e_account_scope_missing", "isolated_e2e_accounts_must_differ", "public_auth_request_failed", "invalid_auth_response", "chat_rpc_failed", "chat_contract_invalid"];
  return { status: "failed", error: known.find((prefix) => message.startsWith(prefix)) ?? "unexpected_chat_runner_failure" };
}
async function report(output, body) {
  const target = resolve(output);
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, `${JSON.stringify(body, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  console.log(`SB-04 report written: ${target}`);
}
async function main() {
  const { output } = args(process.argv.slice(2)), startedAt = new Date().toISOString(), steps = [];
  let configuration; const state = { a: null, b: null, thread: null, messageA: null, messageB: null };
  let cleanup = { state: "not_started" };
  try {
    configuration = config(); // preflight before authentication or a mutation
    state.a = await login(configuration, configuration.users[0]);
    state.b = await login(configuration, configuration.users[1]); steps.push("two_isolated_users_logged_in");
    if (state.a.profileId === state.b.profileId) throw new Error("chat_contract_invalid:isolated_profiles_must_differ");
    const opened = await rpc(configuration, state.a, "quata_chat_get_or_create_private_thread", { p_actor_profile_id: state.a.profileId, p_peer_profile_id: state.b.profileId });
    state.thread = threadId(opened); steps.push("private_thread_opened_or_reused");
    const marker = crypto.randomUUID();
    const seed = await rpc(configuration, state.a, "quata_chat_send_message", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread, p_message: `e2e-sb04 seed ${marker}`, p_file_ids: [], p_reply_to_message_id: null, p_client_message_id: `e2e-sb04-a-${marker}` });
    state.messageA = messageId(seed); steps.push("seed_message_sent_by_a");
    const inbox = await rpc(configuration, state.b, "quata_chat_get_inbox", { p_actor_profile_id: state.b.profileId, p_limit: 100 });
    assertThread(inbox, state.thread); steps.push("inbox_b_contains_thread");
    const detailB = await rpc(configuration, state.b, "quata_chat_get_thread", { p_actor_profile_id: state.b.profileId, p_thread_id: state.thread, p_known_message_ids: [], p_limit: 250 });
    if (!findMessage(detailB, state.messageA)) throw new Error("chat_contract_invalid:detail_missing_seed"); steps.push("detail_b_contains_seed");
    const reply = await rpc(configuration, state.b, "quata_chat_send_message", { p_actor_profile_id: state.b.profileId, p_thread_id: state.thread, p_message: `e2e-sb04 reply ${marker}`, p_file_ids: [], p_reply_to_message_id: state.messageA, p_client_message_id: `e2e-sb04-b-${marker}` });
    state.messageB = messageId(reply); steps.push("reply_sent_by_b");
    const detailA = await rpc(configuration, state.a, "quata_chat_get_thread", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread, p_known_message_ids: [], p_limit: 250 });
    if (findMessage(detailA, state.messageB)?.reply_to_message_id !== state.messageA) throw new Error("chat_contract_invalid:reply_link"); steps.push("detail_a_contains_linked_reply");
    const read = await rpc(configuration, state.b, "quata_chat_mark_thread_read", { p_actor_profile_id: state.b.profileId, p_thread_id: state.thread });
    if (read?.result !== true || read?.thread_id !== state.thread) throw new Error("chat_contract_invalid:mark_read"); steps.push("thread_marked_read_by_b");
    const mute = await rpc(configuration, state.b, "quata_chat_set_muted", { p_actor_profile_id: state.b.profileId, p_thread_id: state.thread, p_muted: true });
    if (mute?.result !== true || mute?.muted !== true) throw new Error("chat_contract_invalid:mute"); steps.push("thread_muted_by_b");
    const unmute = await rpc(configuration, state.b, "quata_chat_set_muted", { p_actor_profile_id: state.b.profileId, p_thread_id: state.thread, p_muted: false });
    if (unmute?.result !== true || unmute?.muted !== false) throw new Error("chat_contract_invalid:unmute"); steps.push("thread_unmuted_by_b");
    cleanup = { state: "external_hard_purge_pending", logicalActions: await logicalCleanup(configuration, state), required: "authorized_operator_purges_both_isolated_accounts_and_all_related_chat_records" };
    steps.push("logical_cleanup_requested");
    await revoke(configuration, state.a); await revoke(configuration, state.b); steps.push("both_sessions_revoked");
    await report(output, { check: "SB-04", status: "passed_with_external_cleanup_pending", startedAt, finishedAt: new Date().toISOString(), mode: "two_existing_isolated_users_public_key", steps, cleanup, mutationPolicy: "Only public key and authenticated sessions. No service-role, DDL or SQL; existing RPC cleanup is logical only." });
  } catch (error) {
    if (configuration && state.thread) {
      try { cleanup = { state: "external_hard_purge_pending_after_failure", logicalActions: await logicalCleanup(configuration, state), required: "authorized_operator_purges_both_isolated_accounts_and_all_related_chat_records" }; }
      catch { cleanup = { state: "rollback_pending", required: "authorized_operator_performs_logical_cleanup_and_hard_purges_both_isolated_accounts" }; }
    }
    for (const session of [state.a, state.b]) if (configuration && session) { try { await revoke(configuration, session); } catch { cleanup.sessionRevocation = "pending"; } }
    console.error(JSON.stringify({ check: "SB-04", startedAt, finishedAt: new Date().toISOString(), cleanup, ...safeFailure(error) }));
    process.exitCode = 1;
  }
}
main().catch((error) => {
  // Argument failures happen before a report path is accepted. Keep their output
  // machine-readable and secret-free just like failures after preflight.
  console.error(JSON.stringify({ check: "SB-04", status: "failed", ...safeFailure(error) }));
  process.exitCode = 1;
});
