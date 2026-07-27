#!/usr/bin/env node
/**
 * Pure validation/planning half of the Chat fixture purge gate.
 *
 * This module has deliberately no database client.  The PowerShell wrapper is
 * the only process allowed to open a database connection and it invokes this
 * module before it emits SQL.  Keeping the dangerous surface here data-free
 * makes the rejection rules hermetic and reviewable.
 */
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SHA256 = /^[a-f0-9]{64}$/i;
export const REQUIRED_TABLES = Object.freeze([
  "chat_threads", "chat_private_threads", "chat_participants", "chat_messages", "chat_attachments",
  "chat_message_favorites", "chat_message_reactions", "chat_message_reads", "chat_profile_blocks",
  "chat_events", "chat_sos_events", "chat_sos_recipients", "push_tokens", "push_delivery_log",
  "web_client_sessions", "web_push_subscriptions", "web_push_delivery_log", "account_deletion_requests",
  "community_profiles", "auth.users",
]);
const AUTH_LAST = "auth.users";

export function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}
export function canonical(value) {
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (value && typeof value === "object") return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canonical(value[key])}`).join(",")}}`;
  return JSON.stringify(value);
}
function fail(code) { throw new Error(code); }
function exactIds(value, code) {
  if (!Array.isArray(value) || value.length !== 2 || new Set(value).size !== 2 || !value.every(id => UUID.test(id))) fail(code);
  return [...value].sort();
}

/** Validate the immutable, per-run operator manifest before any live query. */
export function validateManifest(manifest, expectedRunId, expectedAllowlistSha256) {
  if (!manifest || manifest.schemaVersion !== 1) fail("purge_manifest_schema_invalid");
  if (!UUID.test(manifest.run_id ?? "") || manifest.run_id !== expectedRunId) fail("purge_run_id_mismatch");
  if (!SHA256.test(expectedAllowlistSha256 ?? "")) fail("purge_allowlist_hash_invalid");
  const digest = sha256(canonical(manifest));
  if (digest !== expectedAllowlistSha256.toLowerCase()) fail("purge_allowlist_hash_mismatch");
  if (manifest.purpose !== "isolated_sb04_chat_fixture_purge") fail("purge_manifest_purpose_invalid");
  const authIds = exactIds(manifest.auth_user_ids, "purge_auth_ids_invalid");
  const profileIds = exactIds(manifest.profile_ids, "purge_profile_ids_invalid");
  if (!Array.isArray(manifest.fixtures) || manifest.fixtures.length !== 2) fail("purge_fixture_provenance_invalid");
  const fixtureAuth = exactIds(manifest.fixtures.map(f => f?.auth_user_id), "purge_fixture_auth_mapping_invalid");
  const fixtureProfiles = exactIds(manifest.fixtures.map(f => f?.profile_id), "purge_fixture_profile_mapping_invalid");
  if (canonical(authIds) !== canonical(fixtureAuth) || canonical(profileIds) !== canonical(fixtureProfiles)) fail("purge_fixture_mapping_mismatch");
  for (const fixture of manifest.fixtures) {
    if (fixture.scope !== "isolated_sb04_account" || fixture.provenance !== "web-chat-browser-e2e") fail("purge_fixture_provenance_invalid");
    if (fixture.run_id !== expectedRunId) fail("purge_fixture_run_mismatch");
  }
  return { runId: manifest.run_id, authIds, profileIds, manifestSha256: digest };
}

/**
 * Refuse catalog drift.  The wrapper obtains these rows from pg_constraint;
 * no `RESTRICT`/`NO ACTION` edge or unmanifested child is ever guessed away.
 */
export function validateConstraints(rows) {
  if (!Array.isArray(rows)) fail("purge_constraint_snapshot_invalid");
  const known = new Set(REQUIRED_TABLES.map(table => table.includes(".") ? table.split(".")[1] : table));
  for (const row of rows) {
    const child = `${row.child_schema}.${row.child_table}`;
    const parent = `${row.parent_schema}.${row.parent_table}`;
    if (row.child_schema !== "public" && row.child_schema !== "auth") fail("purge_fk_unknown_schema");
    if (!known.has(row.child_table) || !known.has(row.parent_table)) fail("purge_fk_unknown_table");
    if (!row.child_column || !row.parent_column) fail("purge_fk_unknown_column");
    if (!["c", "n"].includes(row.delete_rule)) fail(`purge_fk_restrict_or_unknown:${child}->${parent}`);
    // A constraint to an allowed table is not enough: only profile/user keyed
    // Chat fixture relationships may be affected by this account lifecycle run.
    if (!/^(profile_id|user_id|owner_id|author_profile_id|participant_profile_id|created_by_profile_id|thread_id|message_id|reply_to_message_id|forwarded_from_message_id|web_session_id|subscription_id|auth_user_id)$/i.test(row.child_column)) {
      fail(`purge_fk_unmanifested_attachment:${child}.${row.child_column}`);
    }
  }
  return rows;
}

export function validateSnapshots(snapshots) {
  if (!Array.isArray(snapshots) || snapshots.length !== REQUIRED_TABLES.length) fail("purge_snapshot_incomplete");
  const names = snapshots.map(row => row.table);
  if (new Set(names).size !== names.length || REQUIRED_TABLES.some(table => !names.includes(table))) fail("purge_snapshot_incomplete");
  for (const row of snapshots) if (!Number.isSafeInteger(row.count) || row.count < 0) fail("purge_snapshot_count_invalid");
  return Object.fromEntries(snapshots.map(row => [row.table, row.count]));
}

export function buildPurgePlan({ manifest, expectedRunId, expectedAllowlistSha256, constraints, snapshots, databaseFingerprint }) {
  const identity = validateManifest(manifest, expectedRunId, expectedAllowlistSha256);
  if (!SHA256.test(databaseFingerprint ?? "")) fail("purge_database_fingerprint_invalid");
  validateConstraints(constraints); const counts = validateSnapshots(snapshots);
  return {
    schemaVersion: 1, state: "dry_run_ready", runId: identity.runId,
    manifestSha256: identity.manifestSha256, databaseFingerprint: databaseFingerprint.toLowerCase(),
    authUserIds: identity.authIds, profileIds: identity.profileIds,
    snapshotCounts: counts, deleteOrder: REQUIRED_TABLES.filter(table => table !== AUTH_LAST).concat(AUTH_LAST),
    invariant: "exact_uuid_allowlist_only_auth_users_last_rollback_default",
  };
}

export function redactedEvidence(plan) {
  return {
    schemaVersion: 1, check: "WEB-CHAT-EXACT-PURGE-GATE-001", status: "dry_run_prepared",
    runIdSha256: sha256(plan.runId), manifestSha256: plan.manifestSha256,
    databaseFingerprint: plan.databaseFingerprint, snapshotCounts: plan.snapshotCounts,
    deleteOrder: plan.deleteOrder, containsIdentifiers: false,
  };
}

async function main(argv) {
  if (argv.length !== 8 || argv[0] !== "--manifest" || argv[2] !== "--run-id" || argv[4] !== "--allowlist-sha256" || argv[6] !== "--input") fail("purge_cli_arguments_invalid");
  const [manifest, input] = await Promise.all([readFile(argv[1], "utf8"), readFile(argv[7], "utf8")]);
  const data = JSON.parse(input); const plan = buildPurgePlan({ manifest: JSON.parse(manifest), expectedRunId: argv[3], expectedAllowlistSha256: argv[5], ...data });
  process.stdout.write(`${JSON.stringify({ plan, evidence: redactedEvidence(plan) })}\n`);
}
if (import.meta.url === new URL(process.argv[1], "file:").href) {
  main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1; });
}
