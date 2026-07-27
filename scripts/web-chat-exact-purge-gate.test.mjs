import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { buildPurgePlan, canonical, redactedEvidence, sha256 } from "./web-chat-exact-purge-gate.mjs";

const runId = "11111111-1111-4111-8111-111111111111";
const a = "22222222-2222-4222-8222-222222222222", b = "33333333-3333-4333-8333-333333333333";
const pa = "44444444-4444-4444-8444-444444444444", pb = "55555555-5555-4555-8555-555555555555";
function manifest() { return { schemaVersion: 1, run_id: runId, purpose: "isolated_sb04_chat_fixture_purge", auth_user_ids: [a, b], profile_ids: [pa, pb], fixtures: [
  { run_id: runId, auth_user_id: a, profile_id: pa, scope: "isolated_sb04_account", provenance: "web-chat-browser-e2e" },
  { run_id: runId, auth_user_id: b, profile_id: pb, scope: "isolated_sb04_account", provenance: "web-chat-browser-e2e" },
] }; }
function input(m = manifest()) { return { manifest: m, expectedRunId: runId, expectedAllowlistSha256: sha256(canonical(m)), databaseFingerprint: "a".repeat(64), constraints: [
  { child_schema: "public", child_table: "chat_messages", child_column: "author_profile_id", parent_schema: "public", parent_table: "community_profiles", parent_column: "id", delete_rule: "c" },
], snapshots: ["chat_threads", "chat_private_threads", "chat_participants", "chat_messages", "chat_attachments", "chat_message_favorites", "chat_message_reactions", "chat_message_reads", "chat_profile_blocks", "chat_events", "chat_sos_events", "chat_sos_recipients", "push_tokens", "push_delivery_log", "web_client_sessions", "web_push_subscriptions", "web_push_delivery_log", "account_deletion_requests", "community_profiles", "auth.users"].map(table => ({ table, count: 0 })) }; }
test("exact immutable two-account allowlist plans auth.users last", () => {
  const plan = buildPurgePlan(input()); assert.equal(plan.deleteOrder.at(-1), "auth.users"); assert.deepEqual(plan.authUserIds, [a, b]);
  const evidence = redactedEvidence(plan); assert.equal(evidence.containsIdentifiers, false); assert.doesNotMatch(JSON.stringify(evidence), new RegExp(a));
});
test("mismatched run, mapping or hash rejects before any database operation", () => {
  const badRun = input(); badRun.manifest.fixtures[1].run_id = "66666666-6666-4666-8666-666666666666"; badRun.expectedAllowlistSha256 = sha256(canonical(badRun.manifest));
  assert.throws(() => buildPurgePlan(badRun), /purge_fixture_run_mismatch/);
  const badHash = input(); badHash.expectedAllowlistSha256 = "b".repeat(64);
  assert.throws(() => buildPurgePlan(badHash), /purge_allowlist_hash_mismatch/);
  const same = input(); same.manifest.auth_user_ids[1] = a; same.expectedAllowlistSha256 = sha256(canonical(same.manifest));
  assert.throws(() => buildPurgePlan(same), /purge_auth_ids_invalid/);
});
test("unknown, restrict and unmanifested attachment foreign keys fail closed", () => {
  for (const mutated of [
    { child_schema: "public", child_table: "chat_messages", child_column: "author_profile_id", parent_schema: "public", parent_table: "community_profiles", parent_column: "id", delete_rule: "r" },
    { child_schema: "public", child_table: "chat_messages", child_column: "untracked_fixture_id", parent_schema: "public", parent_table: "chat_threads", parent_column: "id", delete_rule: "c" },
    { child_schema: "public", child_table: "chat_files", child_column: "profile_id", parent_schema: "public", parent_table: "community_profiles", parent_column: "id", delete_rule: "c" },
  ]) { const value = input(); value.constraints = [mutated]; assert.throws(() => buildPurgePlan(value), /purge_fk_/); }
});
test("wrapper defaults to a read-only rollback and has no pattern account selector", async () => {
  const wrapper = await readFile(new URL("./run-web-chat-exact-purge-gate.ps1", import.meta.url), "utf8");
  assert.match(wrapper, /BEGIN READ ONLY; ROLLBACK/);
  assert.match(wrapper, /purge_commit_requires_ApproveExactIdPurge/);
  assert.match(wrapper, /MANAGER_APPROVED_EXACT_ID_PURGE/);
  assert.match(wrapper, /quata_account_delete_data/);
  assert.match(wrapper, /delete from auth\.users/);
  assert.doesNotMatch(wrapper, /where[\s\S]{0,200}(?:\blike\b|\bilike\b|\bsimilar\s+to\b|~\*?)/i);
});
