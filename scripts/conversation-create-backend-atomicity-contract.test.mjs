import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8');
const [sql, runner, migration, concurrency, pkg] = await Promise.all([
  read('scripts/sql/conversation-create-backend-atomicity.test.sql'),
  read('scripts/test-conversation-create-backend-atomicity.ps1'),
  read('supabase/migrations/20260628_0002_chat_rpc.sql'),
  read('supabase/migrations/20260926171500_chat_private_thread_concurrency.sql'),
  read('package.json').then(JSON.parse),
]);

test('conversation creation atomicity executes the deployed RPC definitions in disposable PostgreSQL', () => {
  assert.match(sql, /\\ir \.\.\/\.\.\/supabase\/migrations\/20260628_0002_chat_rpc\.sql/);
  assert.match(sql, /\\ir \.\.\/\.\.\/supabase\/migrations\/20260926171500_chat_private_thread_concurrency\.sql/);
  assert.match(migration, /create or replace function public\.quata_chat_start_thread\(/i);
  assert.match(concurrency, /create or replace function public\.quata_chat_get_or_create_private_thread\(/i);
  assert.match(runner, /param\(\[string\]\$DockerImage = "postgres:17-alpine"\)/);
  assert.match(runner, /docker run -d --rm[\s\S]*\$DockerImage/);
  assert.match(runner, /-v "\$\{repository\}:\/repo:ro"/);
  assert.match(runner, /finally[\s\S]*docker rm -f \$container[\s\S]*docker ps -a[\s\S]*container_cleanup_unverified/);
  assert.ok(runner.indexOf('container_cleanup_unverified') < runner.indexOf('conversation_create_backend_atomicity_test_passed'));
});

test('both private and group failures occur after product writes and prove physical rollback before retry', () => {
  assert.match(sql, /before insert on public\.chat_events/);
  assert.match(sql, /private_thread_opened[\s\S]*expect_error[\s\S]*failed private creation left a thread[\s\S]*count\(\*\) = 0 from public\.chat_participants/);
  assert.match(sql, /thread_started[\s\S]*expect_error[\s\S]*failed group creation left a thread[\s\S]*failed group creation left a message/);
  assert.match(sql, /valid private creation did not create one pair/);
  assert.match(sql, /valid group creation did not create one thread/);
  assert.match(sql, /valid group creation did not create three participants/);
  assert.match(sql, /valid group creation did not create one message/);
  assert.match(sql, /valid group creation did not create one event/);
  assert.match(sql, /rollback;/);
});

test('the atomicity contract is mandatory in local fast suites', () => {
  for (const suite of ['test:ci-fast-contracts', 'test:web-wave2-contracts']) {
    assert.match(pkg.scripts[suite], /scripts\/conversation-create-backend-atomicity-contract\.test\.mjs/);
  }
});
