import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const runner = await readFile("scripts/chat-group-backend-evidence.mjs", "utf8");
const packageJson = JSON.parse(await readFile("package.json", "utf8"));

test("CHAT-GROUP backend runner is part of fast and Wave2 contracts", () => {
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /scripts\/chat-group-backend-evidence-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /scripts\/chat-group-backend-evidence-contract\.test\.mjs/);
});

test("CHAT-GROUP backend runner keeps credentials private and mutations explicitly authorized", () => {
  assert.match(runner, /QUATA_CHAT_GROUP_CREDENTIALS_FILE/);
  assert.doesNotMatch(runner, /680242607|680242608|21085800/);
  assert.match(runner, /QUATA_CHAT_GROUP_HARD_CLEANUP_AUTHORIZATION/);
  assert.match(runner, /MANAGER_APPROVED_QADATA_CHAT_GROUP_HARD_CLEANUP/);
  assert.match(runner, /qadata-chat-group-/);
  assert.match(runner, /uniqueKey\.startsWith\("qadata-chat-group-"\)/);
});

test("CHAT-GROUP backend runner exercises real group RPC mutations", () => {
  for (const rpc of [
    "quata_chat_start_thread",
    "quata_chat_set_member_invites_enabled",
    "quata_chat_add_participants",
    "quata_chat_promote_moderator",
    "quata_chat_demote_moderator",
    "quata_chat_remove_participant",
    "quata_chat_block_participant",
    "quata_chat_leave_thread",
    "quata_chat_delete_thread",
  ]) {
    assert.match(runner, new RegExp(rpc));
  }
  assert.match(runner, /participants_added/);
  assert.match(runner, /temporary_participant_promoted_and_demoted/);
  assert.match(runner, /temporary_participant_removed/);
  assert.match(runner, /temporary_participant_blocked/);
  assert.match(runner, /peer_left_and_actor_deleted_thread_from_inbox/);
});

test("CHAT-GROUP backend runner hard-cleans only owned temporary rows", () => {
  assert.match(runner, /begin/);
  assert.match(runner, /for update/);
  assert.match(runner, /cleanup_residue_detected:incomplete_state/);
  assert.match(runner, /delete from public\.chat_threads where id = \$1 and unique_key = \$2 returning id/);
  assert.match(runner, /delete from public\.community_profiles where id = \$1 and display_name = \$2 and phone_local = \$3 returning id/);
  assert.match(runner, /cleanup_verified_physical_residue_absent/);
  assert.match(runner, /cleanup_verified_after_failure/);
  assert.match(runner, /safeFailure\(error\)/);
  assert.match(runner, /cleanupFailure = safeFailure\(cleanupError\)/);
  assert.match(runner, /chat_profile_blocks/);
  assert.match(runner, /conversation_user_state/);
  assert.match(runner, /rejectUnauthorized: true/);
  assert.match(runner, /parsedConnection\.searchParams\.delete\("sslmode"\)/);
});
