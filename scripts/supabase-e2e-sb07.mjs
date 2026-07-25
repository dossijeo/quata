#!/usr/bin/env node
/** SB-07: authenticated Communities comments and emoji reactions via PostgREST. */
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const required = [
  "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY",
  "QUATA_E2E_COMMUNITIES_ACTOR_COUNTRY_CODE", "QUATA_E2E_COMMUNITIES_ACTOR_PHONE", "QUATA_E2E_COMMUNITIES_ACTOR_PASSWORD",
  "QUATA_E2E_COMMUNITIES_OUTSIDER_COUNTRY_CODE", "QUATA_E2E_COMMUNITIES_OUTSIDER_PHONE", "QUATA_E2E_COMMUNITIES_OUTSIDER_PASSWORD",
  "QUATA_E2E_COMMUNITIES_WALL_ID", "QUATA_E2E_COMMUNITIES_POST_ID",
  "QUATA_E2E_COMMUNITIES_ACTOR_E2E_SCOPE", "QUATA_E2E_COMMUNITIES_OUTSIDER_E2E_SCOPE", "QUATA_E2E_COMMUNITIES_EXTERNAL_HARD_CLEANUP",
];
const cleanupAck = "approved_isolated_communities_purge";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function args(argv) {
  if (argv.length === 1 && argv[0] === "--help") { console.log("Usage: node scripts/supabase-e2e-sb07.mjs --allow-existing-test-data --allow-community-mutation --out <safe-local-report.json>"); process.exit(0); }
  if (argv.length === 4 && argv[0] === "--allow-existing-test-data" && argv[1] === "--allow-community-mutation" && argv[2] === "--out" && argv[3].trim()) return { output: argv[3] };
  throw new Error("invalid_arguments");
}
function isPublicKey(value) {
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
  if (!isPublicKey(key)) throw new Error("invalid_or_privileged_supabase_key");
  if (process.env.QUATA_E2E_COMMUNITIES_EXTERNAL_HARD_CLEANUP !== cleanupAck) throw new Error("safe_cleanup_contract_missing");
  if (process.env.QUATA_E2E_COMMUNITIES_ACTOR_E2E_SCOPE !== "isolated_sb07_community_actor" || process.env.QUATA_E2E_COMMUNITIES_OUTSIDER_E2E_SCOPE !== "isolated_sb07_community_outsider") throw new Error("isolated_e2e_scope_missing");
  const wallId = process.env.QUATA_E2E_COMMUNITIES_WALL_ID.trim(), postId = process.env.QUATA_E2E_COMMUNITIES_POST_ID.trim();
  if (!uuid.test(wallId) || !uuid.test(postId)) throw new Error("invalid_isolated_community_identifier");
  const actorPhone = `${process.env.QUATA_E2E_COMMUNITIES_ACTOR_COUNTRY_CODE.trim()}|${process.env.QUATA_E2E_COMMUNITIES_ACTOR_PHONE.trim()}`;
  const outsiderPhone = `${process.env.QUATA_E2E_COMMUNITIES_OUTSIDER_COUNTRY_CODE.trim()}|${process.env.QUATA_E2E_COMMUNITIES_OUTSIDER_PHONE.trim()}`;
  if (actorPhone === outsiderPhone) throw new Error("isolated_e2e_accounts_must_differ");
  return { baseUrl, key, wallId, postId, users: [
    { label: "actor", countryCode: process.env.QUATA_E2E_COMMUNITIES_ACTOR_COUNTRY_CODE.trim(), phone: process.env.QUATA_E2E_COMMUNITIES_ACTOR_PHONE.trim(), password: process.env.QUATA_E2E_COMMUNITIES_ACTOR_PASSWORD },
    { label: "outsider", countryCode: process.env.QUATA_E2E_COMMUNITIES_OUTSIDER_COUNTRY_CODE.trim(), phone: process.env.QUATA_E2E_COMMUNITIES_OUTSIDER_PHONE.trim(), password: process.env.QUATA_E2E_COMMUNITIES_OUTSIDER_PASSWORD },
  ] };
}
function headers(key, token, extra = {}) { return { apikey: key, "content-type": "application/json", "x-client-info": "quata-e2e-sb07", ...(token ? { authorization: `Bearer ${token}` } : {}), ...extra }; }
async function request(url, options, prefix) {
  let response; try { response = await fetch(url, { ...options, signal: AbortSignal.timeout(15_000) }); } catch { throw new Error(`${prefix}:network`); }
  const text = await response.text(); let body = null;
  if (text) try { body = JSON.parse(text); } catch { throw new Error(`${prefix}:invalid_json`); }
  return { ok: response.ok, status: response.status, body };
}
async function requiredRequest(url, options, prefix) { const result = await request(url, options, prefix); if (!result.ok) throw new Error(`${prefix}:http_${result.status}`); return result.body; }
async function login(c, user) {
  const payload = await requiredRequest(`${c.baseUrl}/functions/v1/quata-auth-bridge`, { method: "POST", headers: headers(c.key), body: JSON.stringify({ action: "web_login", country_code: user.countryCode, phone_local: user.phone, password: user.password, client_instance_id: `e2e-sb07-${user.label}-${crypto.randomUUID()}` }) }, "public_auth_request_failed");
  if (!uuid.test(payload?.profile?.id) || typeof payload?.session?.access_token !== "string" || !payload.session.access_token) throw new Error("invalid_auth_response:profile_or_session");
  return { profileId: payload.profile.id, accessToken: payload.session.access_token };
}
function rest(c, table, query = "") { return `${c.baseUrl}/rest/v1/${table}${query ? `?${query}` : ""}`; }
async function list(c, s, table, query, prefix) { const body = await requiredRequest(rest(c, table, query), { method: "GET", headers: headers(c.key, s.accessToken) }, prefix); if (!Array.isArray(body)) throw new Error(`${prefix}:invalid_list`); return body; }
async function insert(c, s, table, row, select, prefix) {
  const body = await requiredRequest(rest(c, table, `select=${encodeURIComponent(select)}`), { method: "POST", headers: headers(c.key, s.accessToken, { Prefer: "return=representation" }), body: JSON.stringify(row) }, prefix);
  if (!Array.isArray(body) || body.length !== 1) throw new Error(`${prefix}:invalid_insert_shape`); return body[0];
}
async function deleteReturning(c, s, table, id, prefix, allowRlsDenial = false) {
  const result = await request(rest(c, table, `id=eq.${encodeURIComponent(id)}`), { method: "DELETE", headers: headers(c.key, s.accessToken, { Prefer: "return=representation" }) }, prefix);
  // A server failure must never be recorded as an RLS success. PostgREST can
  // either filter a denied DELETE to an empty 2xx representation or answer
  // with an authentication/authorization status.
  if (!result.ok) {
    if (allowRlsDenial && (result.status === 401 || result.status === 403)) return { denied: true, deleted: [] };
    throw new Error(`${prefix}:http_${result.status}`);
  }
  if (!Array.isArray(result.body)) throw new Error(`${prefix}:invalid_delete_shape`);
  return { denied: false, deleted: result.body };
}
async function revoke(c, s) { const r = await request(`${c.baseUrl}/auth/v1/logout`, { method: "POST", headers: headers(c.key, s.accessToken), body: JSON.stringify({ scope: "global" }) }, "public_auth_request_failed"); if (!r.ok) throw new Error(`public_auth_request_failed:http_${r.status}`); }
function own(rows, id, profileId) { return rows.some((item) => item?.id === id && item?.profile_id === profileId); }
async function report(output, body) { const target = resolve(output); await mkdir(dirname(target), { recursive: true }); await writeFile(target, `${JSON.stringify(body, null, 2)}\n`, { encoding: "utf8", mode: 0o600 }); console.log(`SB-07 report written: ${target}`); }
function safeFailure(error) { const m = typeof error?.message === "string" ? error.message : "unknown"; const known = ["invalid_arguments", "missing_environment", "invalid_public_supabase_url", "invalid_or_privileged_supabase_key", "safe_cleanup_contract_missing", "isolated_e2e_scope_missing", "invalid_isolated_community_identifier", "isolated_e2e_accounts_must_differ", "public_auth_request_failed", "invalid_auth_response", "community_contract_invalid", "community_request_failed", "rls_violation"]; return { status: "failed", error: known.find((prefix) => m.startsWith(prefix)) ?? "unexpected_communities_runner_failure" }; }

async function main() {
  const { output } = args(process.argv.slice(2)), startedAt = new Date().toISOString(), steps = [];
  let c, actor, outsider; const state = { commentId: null, reactionId: null }; let cleanup = { state: "not_started" };
  try {
    c = config(); // All gates complete before authentication, network or mutation.
    actor = await login(c, c.users[0]); outsider = await login(c, c.users[1]);
    if (actor.profileId === outsider.profileId) throw new Error("community_contract_invalid:isolated_profiles_must_differ");
    steps.push("two_isolated_users_logged_in");
    const post = await list(c, actor, "community_posts", `select=id,wall_id&id=eq.${encodeURIComponent(c.postId)}`, "community_request_failed:load_post");
    if (post.length !== 1 || post[0]?.wall_id !== c.wallId) throw new Error("community_contract_invalid:isolated_post_not_in_configured_wall");
    const memberships = await list(c, actor, "community_members", `select=wall_id,profile_id&wall_id=eq.${encodeURIComponent(c.wallId)}&profile_id=eq.${encodeURIComponent(actor.profileId)}`, "community_request_failed:load_actor_membership");
    if (!memberships.some((x) => x?.wall_id === c.wallId && x?.profile_id === actor.profileId)) throw new Error("community_contract_invalid:actor_membership_not_visible");
    steps.push("authorized_community_post_and_actor_membership_confirmed");
    const marker = crypto.randomUUID();
    const comment = await insert(c, actor, "community_comments", { post_id: c.postId, profile_id: actor.profileId, body: `e2e-sb07 comment ${marker}` }, "id,post_id,profile_id,body,created_at", "community_request_failed:create_comment");
    if (!uuid.test(comment?.id) || comment?.post_id !== c.postId || comment?.profile_id !== actor.profileId) throw new Error("community_contract_invalid:created_comment");
    state.commentId = comment.id;
    if (!own(await list(c, actor, "community_comments", `select=id,post_id,profile_id,body,created_at&post_id=eq.${encodeURIComponent(c.postId)}&id=eq.${encodeURIComponent(state.commentId)}`, "community_request_failed:list_comment"), state.commentId, actor.profileId)) throw new Error("community_contract_invalid:created_comment_not_listed");
    steps.push("comment_created_and_listed_by_actor");
    // This is the verified emoji/reaction transport. No persistent ranking endpoint is consumed by SupabaseCommunityApi.
    const reaction = await insert(c, actor, "community_post_reactions", { post_id: c.postId, profile_id: actor.profileId, reaction_type: "\uD83D\uDC4D" }, "id,post_id,profile_id,reaction_type,created_at", "community_request_failed:create_reaction");
    if (!uuid.test(reaction?.id) || reaction?.post_id !== c.postId || reaction?.profile_id !== actor.profileId || reaction?.reaction_type !== "\uD83D\uDC4D") throw new Error("community_contract_invalid:created_reaction");
    state.reactionId = reaction.id;
    if (!own(await list(c, actor, "community_post_reactions", `select=id,post_id,profile_id,reaction_type,created_at&post_id=eq.${encodeURIComponent(c.postId)}&id=eq.${encodeURIComponent(state.reactionId)}`, "community_request_failed:list_reaction"), state.reactionId, actor.profileId)) throw new Error("community_contract_invalid:created_reaction_not_listed");
    steps.push("emoji_reaction_created_and_listed_by_actor");
    // RLS may be HTTP-denied or filtered to zero rows by PostgREST. Both are only valid if actor still sees its row.
    const outsiderDelete = await deleteReturning(c, outsider, "community_comments", state.commentId, "community_request_failed:outsider_delete_comment", true);
    const stillThere = await list(c, actor, "community_comments", `select=id,post_id,profile_id,body,created_at&post_id=eq.${encodeURIComponent(c.postId)}&id=eq.${encodeURIComponent(state.commentId)}`, "community_request_failed:verify_negative_delete");
    if (outsiderDelete.deleted.length !== 0 || !own(stillThere, state.commentId, actor.profileId)) throw new Error("rls_violation:outsider_deleted_actor_comment");
    steps.push("outsider_delete_denied_or_filtered_and_actor_comment_preserved");
    const deletedReaction = await deleteReturning(c, actor, "community_post_reactions", state.reactionId, "community_request_failed:delete_reaction");
    if (deletedReaction.denied || deletedReaction.deleted.length !== 1) throw new Error("community_contract_invalid:actor_reaction_cleanup_not_confirmed"); state.reactionId = null;
    const deletedComment = await deleteReturning(c, actor, "community_comments", state.commentId, "community_request_failed:delete_comment");
    if (deletedComment.denied || deletedComment.deleted.length !== 1) throw new Error("community_contract_invalid:actor_comment_cleanup_not_confirmed"); state.commentId = null;
    if ((await list(c, actor, "community_post_reactions", `select=id&post_id=eq.${encodeURIComponent(c.postId)}&id=eq.${encodeURIComponent(reaction.id)}`, "community_request_failed:verify_reaction_cleanup")).length !== 0) throw new Error("community_contract_invalid:actor_reaction_cleanup_still_visible");
    if ((await list(c, actor, "community_comments", `select=id&post_id=eq.${encodeURIComponent(c.postId)}&id=eq.${encodeURIComponent(comment.id)}`, "community_request_failed:verify_comment_cleanup")).length !== 0) throw new Error("community_contract_invalid:actor_comment_cleanup_still_visible");
    cleanup = { state: "verified_via_postgrest_then_external_hard_purge_pending", required: "authorized_operator_confirms_no_soft_deleted_or_audit_rows_remain_for_the_two_isolated_sb07_accounts" };
    steps.push("actor_created_rows_deleted_by_postgrest"); await revoke(c, actor); await revoke(c, outsider); steps.push("both_sessions_revoked");
    await report(output, { check: "SB-07", status: "passed_with_external_hard_cleanup_pending", startedAt, finishedAt: new Date().toISOString(), mode: "two_existing_isolated_users_public_postgrest", steps, cleanup, ranking: { status: "not_exercised", reason: "No persistent ranking endpoint is consumed by SupabaseCommunityApi; shared ranking is UI-derived." }, mutationPolicy: "Public key plus authenticated JWT only. No service-role, SQL, DDL, migrations, RPCs or direct database connection." });
  } catch (error) {
    if (c && actor) { const remaining = []; for (const [table, key] of [["community_post_reactions", "reactionId"], ["community_comments", "commentId"]]) if (state[key]) try { const r = await deleteReturning(c, actor, table, state[key], `community_request_failed:rollback_${table}`); if (r.denied || r.deleted.length !== 1) remaining.push(table); else state[key] = null; } catch { remaining.push(table); }
      cleanup = remaining.length ? { state: "rollback_pending", remaining, required: "authorized_operator_purges_the_isolated_sb07_accounts_and_related_rows" } : { state: "rows_deleted_via_postgrest_then_external_hard_purge_pending", required: "authorized_operator_confirms_no_soft_deleted_or_audit_rows_remain" }; }
    for (const s of [actor, outsider]) if (c && s) try { await revoke(c, s); } catch { cleanup.sessionRevocation = "pending"; }
    console.error(JSON.stringify({ check: "SB-07", startedAt, finishedAt: new Date().toISOString(), cleanup, ...safeFailure(error) })); process.exitCode = 1;
  }
}
main().catch((error) => { console.error(JSON.stringify({ check: "SB-07", status: "failed", ...safeFailure(error) })); process.exitCode = 1; });
