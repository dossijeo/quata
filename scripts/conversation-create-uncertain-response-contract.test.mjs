import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8');
const [migration, rollback, sql, runner, restoreDrill, remoteProbe, viewModel, releaseExecutor, pkg] = await Promise.all([
  read('supabase/migrations/20261009070000_chat_private_thread_open_idempotency.sql'),
  read('supabase/rollbacks/20261009070000_chat_private_thread_open_idempotency.rollback.sql'),
  read('scripts/sql/conversation-create-uncertain-response.test.sql'),
  read('scripts/test-conversation-create-uncertain-response.ps1'),
  read('scripts/restore-conversation-create-backup-drill.ps1'),
  read('scripts/conversation-create-uncertain-response-remote-probe.mjs'),
  read('feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/conversations/ConversationsViewModel.kt'),
  read('scripts/selective-db-release-executor.mjs'),
  read('package.json').then(JSON.parse),
]);

test('private conversation replay emits its creation effect only for a newly inserted thread', () => {
  assert.match(migration, /v_created boolean := false/);
  assert.match(migration, /v_thread_id := v_created_thread_id;\s+v_created := true;/);
  assert.match(migration, /if v_created then\s+insert into public\.chat_events[\s\S]*private_thread_opened[\s\S]*end if;/);
  assert.doesNotMatch(rollback, /v_created boolean/);
  assert.match(rollback, /insert into public\.chat_events[\s\S]*private_thread_opened/);
});

test('uncertain-response probe replays both product RPCs and proves exact durable cardinalities', () => {
  assert.match(sql, /20261009070000_chat_private_thread_open_idempotency\.sql/);
  assert.match(sql, /first committed result is deliberately ignored/);
  assert.match(sql, /private retry duplicated its open event/);
  assert.match(sql, /group retry duplicated its first message/);
  assert.match(sql, /group retry duplicated its start event/);
  assert.match(sql, /rollback did not restore the previous behavior/);
  assert.match(sql, /reapply did not restore idempotent open effects/);
});

test('the common group retry retains one request key until a successful response', () => {
  assert.match(viewModel, /groupRequestKeys\.getOrPut\(requestSignature, newGroupRequestKey\)/);
  assert.match(viewModel, /onSuccess = \{ conversationId ->[\s\S]*groupRequestKeys\.remove\(requestSignature\)/);
});

test('runner fails closed until disposable-container cleanup is verified', () => {
  assert.match(runner, /docker rm -f \$container[\s\S]*docker ps -a[\s\S]*container_cleanup_unverified/);
  assert.ok(runner.indexOf('container_cleanup_unverified') < runner.indexOf('conversation_create_uncertain_response_test_passed'));
});

test('backup drill restores the exact pre-release function and removes plaintext', () => {
  assert.match(restoreDrill, /kind -eq "full_custom"/);
  assert.match(restoreDrill, /FUNCTION public quata_chat_get_or_create_private_thread\\\(uuid, uuid\\\)/);
  assert.match(restoreDrill, /pg_restore[\s\S]*--use-list=\/backup\/private-open\.restore\.list/);
  assert.match(restoreDrill, /pg_get_functiondef\('public\.quata_chat_get_or_create_private_thread\(uuid,uuid\)'::regprocedure\)/);
  assert.match(restoreDrill, /restore_drill_plaintext_cleanup_unverified/);
  assert.ok(restoreDrill.indexOf('restore_drill_plaintext_cleanup_unverified') < restoreDrill.indexOf('conversation_create_backup_restore_drill_passed'));
});

test('selective release binds the migration bytes and verifies the deployed function', () => {
  assert.match(releaseExecutor, /20261009070000[\s\S]*71bba05c73b6f1af7284c279e6ebb40b0e3deb2e13a55d82e6a4d6622ee26495/);
  assert.match(releaseExecutor, /selectedVersions\.includes\("20261009070000"\)/);
  assert.match(releaseExecutor, /quata_chat_get_or_create_private_thread\(uuid,uuid\)/);
  assert.match(releaseExecutor, /selective_release_private_open_idempotency_postcondition_failed/);
});

test('postdeploy probe replays both committed results inside rollback custody and proves zero residue', () => {
  assert.match(remoteProbe, /20261009070000[\s\S]*chat_private_thread_open_idempotency/);
  assert.match(remoteProbe, /client\.query\('begin'\)[\s\S]*fixture = await probe\(client\)[\s\S]*client\.query\('rollback'\)/);
  assert.match(remoteProbe, /quata_chat_get_or_create_private_thread/);
  assert.match(remoteProbe, /quata_chat_start_thread/);
  assert.match(remoteProbe, /events: 1/);
  assert.match(remoteProbe, /messages: 1, events: 1/);
  assert.match(remoteProbe, /probe_transaction_residue_detected/);
  assert.match(remoteProbe, /CONVERSATION_CREATE_UNCERTAIN_RESPONSE_POSTDEPLOY_PASS/);
  assert.doesNotMatch(remoteProbe, /console\.log|process\.stdout\.write\(connection|process\.stderr\.write\(error/);
});

test('the uncertain-response contract is mandatory in local fast suites', () => {
  for (const suite of ['test:ci-fast-contracts', 'test:web-wave2-contracts']) {
    assert.match(pkg.scripts[suite], /scripts\/conversation-create-uncertain-response-contract\.test\.mjs/);
  }
});
