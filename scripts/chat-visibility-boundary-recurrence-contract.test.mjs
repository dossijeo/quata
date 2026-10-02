import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { accountDeactivateDefinitionMd5 } from "./selective-db-release-postconditions.mjs";

const root = new URL("../", import.meta.url);
const read = (path) => readFile(new URL(path, root), "utf8");

test("message insertion initializes every active participant boundary", async () => {
  const [migration, rollback] = await Promise.all([
    read("supabase/migrations/20261001211000_chat_visibility_boundary_recurrence.sql"),
    read("supabase/rollbacks/20261001211000_chat_visibility_boundary_recurrence.rollback.sql"),
  ]);

  assert.match(migration, /or state\.first_visible_message_id is null/);
  assert.match(migration, /missing\.updated_at <= missing\.first_message_created_at then missing\.first_message_id/);
  assert.match(migration, /missing\.updated_at <= missing\.first_message_created_at then null\s+else now\(\)/);
  assert.doesNotMatch(rollback, /or state\.first_visible_message_id is null/);
});

test("selective release distinguishes installed anchors and valid tombstones", async () => {
  const executor = await read("scripts/selective-db-release-executor.mjs");
  assert.match(executor, /20261001211000/);
  assert.match(executor, /d5e9ed9b0f78dc434918044c41f73d7bb3f1717629aba03f1052e3dd30f3b76b/);
  assert.match(executor, /s\.first_visible_message_id is null and s\.deleted_at is null/);
  assert.match(executor, /selective_release_visibility_boundary_recurrence_postcondition_failed/);
  assert.match(
    executor,
    /installedVersions = \[\.\.\.pkg\.anchors, \.\.\.pkg\.selected\]\.map\(\(\{ version \}\) => version\)/,
  );
  assert.equal(
    accountDeactivateDefinitionMd5(["20260928013000", "20261001211000"]),
    "290fcd85f9a57e8c999f3132235fdbe4",
  );
  assert.equal(
    accountDeactivateDefinitionMd5(["20261001211000"]),
    "d2504acfb2095176289fb99a939f7621",
  );
});

test("PostgreSQL fixture covers repair, recurrence prevention, privacy fallback, and rollback", async () => {
  const [runner, pkg] = await Promise.all([
    read("scripts/test-chat-visibility-boundary-recurrence.ps1"),
    read("package.json"),
  ]);
  assert.match(runner, /chat_visibility_boundary_recurrence_postgres_pass/);
  assert.match(runner, /non_sender_boundary_not_initialized/);
  assert.match(runner, /ambiguous_boundary_not_tombstoned/);
  assert.match(runner, /tombstone_not_reopened_at_new_message/);
  assert.match(runner, /rollback_did_not_restore_previous_trigger_behavior/);
  const scripts = JSON.parse(pkg).scripts;
  assert.match(scripts["test:ci-fast-contracts"], /chat-visibility-boundary-recurrence-contract\.test\.mjs/);
  assert.match(scripts["test:web-wave2-contracts"], /chat-visibility-boundary-recurrence-contract\.test\.mjs/);
});
