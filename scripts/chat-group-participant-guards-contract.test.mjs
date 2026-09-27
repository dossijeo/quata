import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";

const root = resolve(import.meta.dirname, "..");
const read = (path) => readFileSync(resolve(root, path), "utf8");
const migration = read("supabase/migrations/20260927123000_chat_group_participant_guards.sql");
const rollback = read("supabase/rollbacks/20260927123000_chat_group_participant_guards.rollback.sql");
const blockLockMigration = read("supabase/migrations/20260927133000_chat_group_block_target_lock.sql");
const blockLockRollback = read("supabase/rollbacks/20260927133000_chat_group_block_target_lock.rollback.sql");
const viewModel = read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatViewModel.kt");
const viewModelTest = read("feature/chat/src/commonTest/kotlin/com/quata/feature/chat/presentation/chat/ChatViewModelComposerActionsTest.kt");
const sqlTest = read("scripts/sql/chat-group-participant-guards.test.sql");
const runner = read("scripts/test-chat-group-participant-guards.ps1");
const backendEvidence = read("scripts/chat-group-backend-evidence.mjs");

test("group participant RPCs lock and validate targets before mutation", () => {
  for (const name of ["promote_moderator", "demote_moderator", "remove_participant"]) {
    const definition = migration.match(new RegExp(`create or replace function public\\.quata_chat_${name}\\([\\s\\S]*?\\n\\$\\$;`, "i"))?.[0] ?? "";
    assert.match(definition, /quata_chat_can_moderate/);
    assert.match(definition, /select role[\s\S]*for update;/i);
    assert.match(definition, /target participant does not exist/);
    assert.match(definition, /v_target_role = 'owner'/);
    assert.ok(definition.indexOf("select role") < definition.indexOf("update public.chat_participants"));
  }
});

test("conversation-scoped block rejects self and inactive or unrelated targets", () => {
  const definition = blockLockMigration.match(/create or replace function public\.quata_chat_block_participant\([\s\S]*?\n\$\$;/i)?.[0] ?? "";
  assert.match(definition, /quata_chat_is_thread_participant\(p_thread_id, v_actor\)/);
  assert.match(definition, /p_profile_id = v_actor/);
  assert.match(definition, /select role[\s\S]*left_at is null[\s\S]*for update;/i);
  assert.match(definition, /target participant does not exist/);
  assert.ok(definition.indexOf("profile cannot block itself") < definition.indexOf("insert into public.chat_profile_blocks"));
});

test("the common UI preserves state on rejection and clears stale errors on retry", () => {
  for (const action of ["promoteModerator", "demoteModerator", "removeParticipant", "blockParticipant"]) {
    const definition = viewModel.match(new RegExp(`private fun ${action}\\(userId: String\\)[\\s\\S]*?\\n    }`))?.[0] ?? "";
    assert.match(definition, /isConversationActionInProgress = true, error = null/);
    assert.match(definition, /isConversationActionInProgress = false/);
  }
  assert.match(viewModelTest, /failedGroupParticipantActionsPreserveConversationAndAllowCleanRetry/);
  assert.match(viewModelTest, /assertEquals\(before, model\.uiState\.value\.conversation\)/);
});

test("the disposable PostgreSQL trial covers rollback, negative guards and positive paths", () => {
  assert.match(sqlTest, /forced post-mutation failure/);
  assert.match(sqlTest, /failed promotion must roll back its role update/);
  assert.match(sqlTest, /rollback did not restore the prior promotion definition/);
  assert.match(sqlTest, /migration did not reapply after rollback/);
  for (const fragment of [
    "profile cannot promote moderators",
    "thread owner cannot be promoted",
    "thread owner cannot be demoted",
    "thread owner cannot be removed",
    "profile cannot block itself",
    "target participant does not exist",
    "valid promotion failed",
    "valid demotion failed",
    "valid block failed",
    "valid removal failed",
  ]) assert.match(sqlTest, new RegExp(fragment));
  assert.match(runner, /postgres:17-alpine/);
  assert.match(runner, /ON_ERROR_STOP=1/);
  assert.match(runner, /finally/);
  assert.match(runner, /docker rm -f/);
});

test("the authenticated backend coordinator proves rejection without residue", () => {
  assert.match(backendEvidence, /expectRpcRejection/);
  assert.match(backendEvidence, /error instanceof JsonHttpError/);
  assert.match(backendEvidence, /error\.status === expectedStatus && error\.code === expectedSqlState/);
  assert.match(backendEvidence, /"42501"/);
  assert.match(backendEvidence, /"22023"/);
  assert.match(backendEvidence, /unauthorized_owner_self_and_nonparticipant_guards_rejected_without_mutation/);
  assert.match(backendEvidence, /removed_participant_block_rejected_without_mutation/);
  assert.match(backendEvidence, /JSON\.stringify\(snapshot\) !== guardedBaseline/);
  assert.match(backendEvidence, /blockCount\(state\.thread\) !== 0/);
  assert.ok(backendEvidence.indexOf("active_temporary_participant_blocked") < backendEvidence.indexOf("temporary_participant_removed"));
});

test("the emergency rollback restores every pre-change RPC definition atomically", () => {
  assert.match(rollback, /^begin;/);
  assert.match(rollback, /commit;\s*$/);
  for (const name of ["promote_moderator", "demote_moderator", "remove_participant", "block_participant"]) {
    assert.match(rollback, new RegExp(`create or replace function public\\.quata_chat_${name}`));
  }
  assert.doesNotMatch(rollback, /for update;/i);
  assert.match(blockLockRollback, /^begin;/);
  assert.match(blockLockRollback, /commit;\s*$/);
  assert.match(blockLockRollback, /quata_chat_is_thread_participant\(p_thread_id, p_profile_id\)/);
  assert.doesNotMatch(blockLockRollback, /for update;/i);
});
