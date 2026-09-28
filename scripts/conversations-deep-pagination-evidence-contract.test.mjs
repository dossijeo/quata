import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const runnerUrl = new URL("./conversations-deep-pagination-evidence.mjs", import.meta.url);
const runnerPath = fileURLToPath(runnerUrl);
const runner = readFileSync(runnerUrl, "utf8");

test("deep pagination crosses the production boundary through authenticated product RPCs", () => {
  assert.match(runner, /const fixtureCount = 101/);
  assert.match(runner, /const productionPageSize = 100/);
  assert.match(runner, /"quata_chat_start_thread"/);
  assert.match(runner, /p_message: marker/);
  assert.match(runner, /"quata_chat_get_inbox_page"/);
  assert.match(runner, /p_limit: productionPageSize/);
  assert.match(runner, /firstRows\.length !== productionPageSize \|\| first\?\.has_more !== true/);
  assert.match(runner, /observedFixtureIds\.size !== fixtureCount/);
  assert.match(runner, /new Set\(observedIds\)\.size !== observedIds\.length/);
});

test("deep pagination cleanup is fail-closed, ownership-scoped and residue-audited", () => {
  assert.match(runner, /MANAGER_APPROVED_QADATA_CONVERSATIONS_DEEP_PAGINATION_CLEANUP/);
  assert.match(runner, /created_by_profile_id = \$1::uuid/);
  assert.match(runner, /unique_key = any\(\$2::text\[\]\)/);
  assert.match(runner, /subject like \$3/);
  assert.match(runner, /for update/);
  assert.match(runner, /delete from public\.chat_threads/);
  assert.match(runner, /chat_messages/);
  assert.match(runner, /chat_participants/);
  assert.match(runner, /chat_attachments/);
  assert.match(runner, /chat_message_states/);
  assert.match(runner, /chat_profile_blocks/);
  assert.match(runner, /chat_events/);
  assert.match(runner, /conversation_user_state/);
  assert.match(runner, /owned_fixture_threads_deleted_with_zero_physical_residue/);
});

test("missing cleanup authorization fails before login or any product RPC", () => {
  const authorizationCheck = runner.indexOf("requireCleanupAuthorization();", runner.indexOf("async function main"));
  const firstLogin = runner.indexOf("await login(", runner.indexOf("async function main"));
  const firstRpc = runner.indexOf("await rpc(", runner.indexOf("async function main"));
  assert.ok(authorizationCheck >= 0 && authorizationCheck < firstLogin && authorizationCheck < firstRpc);

  const directory = mkdtempSync(join(tmpdir(), "quata-deep-pagination-auth-"));
  const output = join(directory, "report.json");
  try {
    const env = { ...process.env };
    delete env.QUATA_CONVERSATIONS_DEEP_PAGINATION_CLEANUP_AUTHORIZATION;
    env.QUATA_CHAT_GROUP_CREDENTIALS_FILE = join(directory, "must-not-be-read.json");
    const result = spawnSync(process.execPath, [runnerPath, "--out", output], {
      cwd: process.cwd(),
      env,
      encoding: "utf8",
    });
    assert.equal(result.status, 1);
    const report = JSON.parse(readFileSync(output, "utf8"));
    assert.equal(report.error, "missing_cleanup_authorization");
    assert.equal(report.cleanup.state, "not_started");
    assert.equal(report.git, null);
    assert.deepEqual(report.steps, []);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test("deep pagination report cannot serialize credentials or local secret paths", () => {
  assert.doesNotMatch(runner, /report\s*=\s*\{[^}]*accessToken/s);
  assert.doesNotMatch(runner, /report\s*=\s*\{[^}]*password/s);
  assert.doesNotMatch(runner, /QUATA_CHAT_GROUP_CREDENTIALS_FILE\.txt/);
  assert.match(runner, /actorProfileSha256: sha256\(actor\.profileId\)/);
  assert.match(runner, /scope=local/);
});
