import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");
const runner = await read("./profile-safety-permissions-backend-evidence.mjs");
const migration = await read("../supabase/migrations/20260716_0001_ugc_moderation.sql");
const hardeningMigration = await read("../supabase/migrations/20261009073000_profile_safety_actor_permissions.sql");
const actorBoundary = await read("../supabase/migrations/20260927094500_chat_actor_auth_boundary.sql");
const packageJson = JSON.parse(await read("../package.json"));
const selectiveExecutor = await read("./selective-db-release-executor.mjs");
const restoreDrill = await read("./restore-db-logical-backup-drill.ps1");

test("PROF-SAFETY RPCs retain least-privilege ACLs", () => {
  for (const signature of [
    "quata_ugc_report(uuid,text,text,text,text)",
    "quata_profile_block(uuid,uuid)",
    "quata_profile_unblock(uuid,uuid)",
  ]) {
    assert.match(migration, new RegExp(`revoke all on function public\\.${signature.replace(/[()]/g, "\\$&")} from public`));
    assert.match(migration, new RegExp(`grant execute on function public\\.${signature.replace(/[()]/g, "\\$&")} to authenticated`));
  }
  assert.match(runner, /not has_function_privilege\('anon'/);
  assert.match(runner, /has_function_privilege\('authenticated'/);
  assert.match(runner, /not has_table_privilege\('authenticated', 'public\.ugc_reports', 'INSERT,UPDATE,DELETE'\)/);
  assert.match(runner, /not has_table_privilege\('authenticated', 'public\.chat_profile_blocks', 'INSERT,UPDATE,DELETE'\)/);
  assert.match(runner, /not has_sequence_privilege\('authenticated', 'public\.ugc_reports_id_seq', 'USAGE,SELECT,UPDATE'\)/);
  assert.match(hardeningMigration, /revoke all on function public\.quata_ugc_report\(uuid,text,text,text,text\) from public, anon, authenticated/);
  assert.match(hardeningMigration, /revoke insert, update, delete, truncate, references, trigger[\s\S]*public\.ugc_reports, public\.chat_profile_blocks[\s\S]*from anon, authenticated/);
  assert.match(hardeningMigration, /grant select on table public\.ugc_reports, public\.chat_profile_blocks to authenticated/);
  assert.match(hardeningMigration, /revoke all on sequence public\.ugc_reports_id_seq from anon, authenticated/);
});

test("PROF-SAFETY actor binding rejects anonymous and spoofed callers", () => {
  assert.match(actorBoundary, /p_actor_profile_id <> v_auth_profile_id[\s\S]*actor profile does not match authenticated Supabase user[\s\S]*errcode = '42501'/);
  assert.match(actorBoundary, /raise exception 'authenticated chat actor is required' using errcode = '42501'/);
  assert.match(runner, /set local role anon[\s\S]*anonymous_report[\s\S]*anonymous_block/);
  assert.match(runner, /set local role authenticated[\s\S]*request\.jwt\.claim\.sub[\s\S]*spoofed_report_actor[\s\S]*spoofed_block_actor/);
});

test("PROF-SAFETY rejects self-targeting and proves actor-owned mutations transactionally", () => {
  assert.match(migration, /v_reported = v_actor[\s\S]*You cannot report your own content/);
  assert.match(migration, /v_actor = p_profile_id[\s\S]*You cannot block yourself/);
  assert.match(runner, /self_report[\s\S]*self_block[\s\S]*actor_owned_mutation[\s\S]*rollback/);
  assert.match(runner, /spoofed_unblock_actor[\s\S]*spoofed_unblock_changed_foreign_edge[\s\S]*actor_owned_unblock/);
  assert.match(runner, /cleanup: \{ state: "completed", transactionRolledBack: true, residueZero: true \}/);
  assert.match(runner, /--migration-preview[\s\S]*20261009073000_profile_safety_actor_permissions\.sql[\s\S]*transactionalBody/);
});

test("PROF-SAFETY permission contract is part of both fast suites", () => {
  for (const scriptName of ["test:ci-fast-contracts", "test:web-wave2-contracts"]) {
    assert.match(packageJson.scripts[scriptName], /profile-safety-permissions-backend-contract\.test\.mjs/);
  }
});

test("PROF-SAFETY release is allowlisted with exact bytes and fail-closed postconditions", () => {
  assert.match(selectiveExecutor, /20261009073000[\s\S]*089e1a720afda2c6f6a38951b179293106f8dfad719060d79c1413cb55f64011/);
  assert.match(selectiveExecutor, /selectedVersions\.includes\("20261009073000"\)[\s\S]*anon_report_denied[\s\S]*authenticated_report_allowed[\s\S]*authenticated_block_table_denied[\s\S]*authenticated_report_sequence_denied/);
  assert.match(selectiveExecutor, /selective_release_profile_safety_permissions_postcondition_failed/);
});

test("PROF-SAFETY backup drill restores both affected tables and checks exact counts", () => {
  assert.match(restoreDrill, /\[switch\]\$ProfileSafetyScope/);
  assert.match(restoreDrill, /--section=pre-data[\s\S]*--table=ugc_reports[\s\S]*--table=chat_profile_blocks/);
  assert.match(restoreDrill, /--section=data[\s\S]*ExpectedUgcReports[\s\S]*ExpectedChatProfileBlocks/);
});
