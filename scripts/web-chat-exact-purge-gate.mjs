#!/usr/bin/env node
/**
 * The data-free half of the exact Chat fixture purge gate.
 *
 * It intentionally has no "commit" mode.  A destructive run needs a
 * separately deployed, reviewed service with an Actions-attested approval
 * key; accepting a hand-written JSON document in a developer shell is not a
 * safe substitute.  This module therefore only prepares a frozen, read-only
 * inspection contract.
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
export const AUTH_LAST = "auth.users";
const RELATIONS = new Set(REQUIRED_TABLES);
const ALLOWED_COLUMNS = new Set(["profile_id", "user_id", "owner_id", "author_profile_id", "participant_profile_id", "created_by_profile_id", "sender_profile_id", "uploaded_by_profile_id", "blocker_profile_id", "blocked_profile_id", "actor_profile_id", "recipient_profile_id", "auth_user_id", "thread_id", "message_id", "reply_to_message_id", "forwarded_from_message_id", "web_session_id", "subscription_id"]);

export const sha256 = value => createHash("sha256").update(value).digest("hex");
export function canonical(value) {
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (value && typeof value === "object") return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canonical(value[key])}`).join(",")}}`;
  return JSON.stringify(value);
}
const fail = code => { throw new Error(code); };
/** The embedded provenance digest is excluded to avoid a self-referential hash. */
export function manifestDigest(manifest) {
  const copy = structuredClone(manifest);
  if (copy?.provenance) delete copy.provenance.manifest_sha256;
  return sha256(canonical(copy));
}
const normalizeUuid = value => {
  if (typeof value !== "string" || !UUID.test(value)) fail("purge_uuid_invalid");
  return value.toLowerCase();
};
function exactIds(value, code) {
  if (!Array.isArray(value) || value.length !== 2) fail(code);
  const ids = value.map(normalizeUuid).sort();
  if (new Set(ids).size !== 2) fail(code);
  return ids;
}
function relation(schema, table) { return schema === "auth" && table === "users" ? "auth.users" : schema === "public" ? table : null; }

/** Freeze the bytes-derived manifest before any connection is attempted. */
export function validateManifest(manifest, { expectedRunId, expectedManifestSha256, candidateSha, expectedProjectRef }) {
  if (!manifest || manifest.schemaVersion !== 2) fail("purge_manifest_schema_invalid");
  const runId = normalizeUuid(manifest.run_id);
  if (runId !== normalizeUuid(expectedRunId)) fail("purge_run_id_mismatch");
  if (!SHA256.test(expectedManifestSha256 ?? "")) fail("purge_manifest_hash_invalid");
  const manifestSha256 = manifestDigest(manifest);
  if (manifestSha256 !== expectedManifestSha256.toLowerCase()) fail("purge_manifest_hash_mismatch");
  if (!SHA256.test(candidateSha ?? "")) fail("purge_candidate_sha_invalid");
  if (manifest.candidate_sha !== candidateSha.toLowerCase()) fail("purge_candidate_sha_mismatch");
  if (manifest.purpose !== "isolated_sb04_chat_fixture_purge" || manifest.provenance?.source !== "web-chat-browser-e2e") fail("purge_provenance_invalid");
  if (manifest.provenance.run_id !== runId || manifest.provenance.manifest_sha256 !== manifestSha256 || manifest.provenance.candidate_sha !== candidateSha.toLowerCase()) fail("purge_provenance_binding_invalid");
  if (typeof expectedProjectRef !== "string" || !/^[a-z0-9-]{8,80}$/i.test(expectedProjectRef) || manifest.database?.project_ref !== expectedProjectRef) fail("purge_project_sentinel_mismatch");
  const authIds = exactIds(manifest.auth_user_ids, "purge_auth_ids_invalid");
  const profileIds = exactIds(manifest.profile_ids, "purge_profile_ids_invalid");
  if (!Array.isArray(manifest.fixtures) || manifest.fixtures.length !== 2) fail("purge_fixture_provenance_invalid");
  const mappedAuth = exactIds(manifest.fixtures.map(f => f?.auth_user_id), "purge_fixture_auth_mapping_invalid");
  const mappedProfiles = exactIds(manifest.fixtures.map(f => f?.profile_id), "purge_fixture_profile_mapping_invalid");
  if (canonical(authIds) !== canonical(mappedAuth) || canonical(profileIds) !== canonical(mappedProfiles)) fail("purge_fixture_mapping_mismatch");
  for (const fixture of manifest.fixtures) {
    if (fixture.scope !== "isolated_sb04_account" || normalizeUuid(fixture.run_id) !== runId) fail("purge_fixture_provenance_invalid");
  }
  // Attachments are deliberately unsupported until object-store proof is part of an atomic service.
  if (!Array.isArray(manifest.attachments) || manifest.attachments.length !== 0) fail("purge_attachments_not_supported");
  return { runId, authIds, profileIds, manifestSha256, candidateSha: candidateSha.toLowerCase(), projectRef: expectedProjectRef };
}

/** Catalog rows are the complete live FK graph, not a guessed deletion order. */
export function validateConstraints(rows) {
  if (!Array.isArray(rows) || !rows.length) fail("purge_constraint_snapshot_invalid");
  const seen = new Set();
  for (const row of rows) {
    const child = relation(row.child_schema, row.child_table), parent = relation(row.parent_schema, row.parent_table);
    if (!child || !parent || !RELATIONS.has(child) || !RELATIONS.has(parent)) fail("purge_fk_unknown_relation");
    if (!ALLOWED_COLUMNS.has(row.child_column) || !row.parent_column) fail("purge_fk_unmanifested_attachment");
    if (!["c", "n"].includes(row.delete_rule)) fail(`purge_fk_restrict_or_unknown:${child}->${parent}`);
    const key = `${child}.${row.child_column}->${parent}.${row.parent_column}`;
    if (seen.has(key)) fail("purge_fk_duplicate_snapshot");
    seen.add(key);
  }
  return [...seen].sort();
}
export function validateSnapshots(snapshots) {
  if (!Array.isArray(snapshots) || snapshots.length !== REQUIRED_TABLES.length) fail("purge_snapshot_incomplete");
  const result = Object.create(null);
  for (const row of snapshots) {
    if (!RELATIONS.has(row?.table) || Object.hasOwn(result, row.table) || !Number.isSafeInteger(row.count) || row.count < 0) fail("purge_snapshot_invalid");
    result[row.table] = row.count;
  }
  if (REQUIRED_TABLES.some(table => !Object.hasOwn(result, table))) fail("purge_snapshot_incomplete");
  return result;
}
export function validateFingerprint(fingerprint, expectedProjectRef) {
  if (!fingerprint || fingerprint.project_ref !== expectedProjectRef || !/^[0-9]+$/.test(String(fingerprint.server_version_num)) || !/^[A-Za-z0-9_.-]+$/.test(fingerprint.database ?? "") || !/^[A-Za-z0-9_.:-]+$/.test(fingerprint.server_endpoint ?? "")) fail("purge_database_identity_invalid");
  return sha256(canonical({ project_ref: fingerprint.project_ref, server_version_num: String(fingerprint.server_version_num), database: fingerprint.database, server_endpoint: fingerprint.server_endpoint }));
}
export function buildPurgePlan({ manifest, expectedRunId, expectedManifestSha256, candidateSha, expectedProjectRef, constraints, snapshots, fingerprint }) {
  const identity = validateManifest(manifest, { expectedRunId, expectedManifestSha256, candidateSha, expectedProjectRef });
  const graphSha256 = sha256(canonical(validateConstraints(constraints)));
  const snapshotCounts = validateSnapshots(snapshots);
  const databaseFingerprint = validateFingerprint(fingerprint, expectedProjectRef);
  return { schemaVersion: 2, state: "read_only_inspection_ready", ...identity, databaseFingerprint, graphSha256, snapshotCounts, deleteOrder: REQUIRED_TABLES.filter(x => x !== AUTH_LAST).concat(AUTH_LAST), invariant: "exact_uuid_temp_tables_readonly_no_commit" };
}
export function redactedEvidence(plan) {
  return { schemaVersion: 2, check: "WEB-CHAT-EXACT-PURGE-GATE-002", status: "read_only_rolled_back", runIdSha256: sha256(plan.runId), manifestSha256: plan.manifestSha256, candidateSha: plan.candidateSha, databaseFingerprint: plan.databaseFingerprint, graphSha256: plan.graphSha256, snapshotCounts: plan.snapshotCounts, containsIdentifiers: false, destructiveAuthorization: "unavailable_by_construction" };
}
async function main(argv) {
  if (argv[0] === "--validate-manifest" && argv.length === 10 && argv[2] === "--run-id" && argv[4] === "--manifest-sha256" && argv[6] === "--candidate-sha" && argv[8] === "--project-ref") {
    const manifest = JSON.parse(await readFile(argv[1], "utf8"));
    const identity = validateManifest(manifest, { expectedRunId: argv[3], expectedManifestSha256: argv[5], candidateSha: argv[7], expectedProjectRef: argv[9] });
    process.stdout.write(`${JSON.stringify(identity)}\n`); return;
  }
  if (argv.length !== 12 || argv[0] !== "--manifest" || argv[2] !== "--run-id" || argv[4] !== "--manifest-sha256" || argv[6] !== "--candidate-sha" || argv[8] !== "--project-ref" || argv[10] !== "--input") fail("purge_cli_arguments_invalid");
  const [manifestText, inputText] = await Promise.all([readFile(argv[1], "utf8"), readFile(argv[11], "utf8")]);
  const plan = buildPurgePlan({ manifest: JSON.parse(manifestText), expectedRunId: argv[3], expectedManifestSha256: argv[5], candidateSha: argv[7], expectedProjectRef: argv[9], ...JSON.parse(inputText) });
  process.stdout.write(`${JSON.stringify({ plan, evidence: redactedEvidence(plan) })}\n`);
}
if (import.meta.url === new URL(process.argv[1], "file:").href) main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1; });
