import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const migrationUrl = new URL("../supabase/migrations/20260927130000_chat_forward_atomicity.sql", import.meta.url);
const rollbackUrl = new URL("../supabase/rollbacks/20260927130000_chat_forward_atomicity.rollback.sql", import.meta.url);
const releaseUrl = new URL("./chat-forward-atomicity-release.mjs", import.meta.url);

test("forward migration validates every destination before its first write", async () => {
  const sql = await readFile(migrationUrl, "utf8");
  const authorization = sql.indexOf("Complete authorization before the first write");
  const firstInsert = sql.indexOf("insert into public.chat_messages");

  assert.match(sql, /create or replace function public\.quata_chat_forward_message\(/);
  assert.match(sql, /select distinct unnest\(coalesce\(p_thread_ids, array\[\]::bigint\[\]\)\)/);
  assert.match(sql, /raise exception 'profile cannot forward to target thread' using errcode = '42501'/);
  assert.ok(authorization > 0 && authorization < firstInsert, "authorization must finish before writes");
  assert.equal((sql.match(/foreach v_target_thread_id in array v_target_thread_ids/g) ?? []).length, 2);
});

test("forward migration propagates failures so PostgreSQL rolls back the whole RPC", async () => {
  const sql = await readFile(migrationUrl, "utf8");

  assert.doesNotMatch(sql, /exception when others/i);
  assert.doesNotMatch(sql, /continue;/i);
  assert.doesNotMatch(sql, /v_errors/i);
  assert.match(sql, /'errors', '\[\]'::jsonb/);
});

test("release rollback restores the reviewed partial-result implementation", async () => {
  const rollback = await readFile(rollbackUrl, "utf8");

  assert.match(rollback, /v_errors text\[\] := '\{\}'/);
  assert.match(rollback, /exception when others then/);
  assert.match(rollback, /v_errors := array_append\(v_errors, sqlerrm\)/);
  assert.match(rollback, /'errors', to_jsonb\(v_errors\)/);
});

test("forward atomicity contract runs in both mandatory fast suites", async () => {
  const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));
  for (const suite of ["test:ci-fast-contracts", "test:web-wave2-contracts"]) {
    assert.match(packageJson.scripts[suite], /scripts\/chat-forward-atomicity-contract\.test\.mjs/);
  }
});

test("release executor applies and proves atomicity in one fail-closed transaction", async () => {
  const source = await readFile(releaseUrl, "utf8");

  assert.match(source, /I_ACCEPT_ATOMIC_CHAT_FORWARD_DATABASE_RELEASE/);
  assert.match(source, /begin isolation level repeatable read/);
  assert.match(source, /pg_advisory_xact_lock/);
  assert.match(source, /atomic_function_installed_inside_transaction/);
  assert.match(source, /migration_ledger_staged_inside_transaction/);
  assert.match(source, /savepoint mixed_destination_probe/);
  assert.match(source, /rollback to savepoint mixed_destination_probe/);
  assert.match(source, /partialCopyDelta: afterFailure - baseline/);
  assert.match(source, /savepoint duplicate_destination_probe/);
  assert.match(source, /atomicity_candidate_precondition_failed/);
  assert.match(source, /duplicate_destination_probe_failed_/);
  assert.match(source, /persistentCopyDelta: afterSuccessRollback - baseline/);
  assert.match(source, /if \(transactionOpen && client\)[\s\S]*?query\("rollback"\)/);
  assert.match(source, /report\.failureStage = failureStage/);
  assert.match(source, /\^\[0-9A-Z\]\{5\}\$/);
  assert.doesNotMatch(source, /console\.(?:log|error)\([^\n]*(?:candidate|actor|source|target)/i);
});
