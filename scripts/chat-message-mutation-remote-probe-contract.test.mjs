import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(new URL("./chat-message-mutation-remote-probe.mjs", import.meta.url), "utf8");

test("remote message mutation probe applies and exercises the migration only inside rollback custody", () => {
  for (const token of [
    "await client.query(\"begin\")",
    "await client.query(migration)",
    "assertInstalledBoundary",
    "quata_chat_edit_message_v2",
    "quata_chat_delete_messages_v2",
    "probe_mutation_key_reuse_not_denied",
    "await client.query(\"rollback\")",
    "assertBaselineAbsent(client)",
    "probe_fixture_residue_detected",
    "CHAT_MESSAGE_MUTATION_REMOTE_ROLLBACK_PASS",
  ]) assert.ok(source.includes(token), `missing ${token}`);
  assert.doesNotMatch(source, /client\.query\(["']commit["']\)/i);
});

test("postdeploy mode verifies the committed ledger and installed boundary while rolling back its fixture", () => {
  for (const token of [
    'mode: "predeploy"',
    '"postdeploy"',
    "assertInstalledLedger",
    "20261002003000",
    "chat_message_mutation_idempotency",
    "CHAT_MESSAGE_MUTATION_REMOTE_POSTDEPLOY_PASS",
  ]) assert.ok(source.includes(token), `missing ${token}`);
  assert.match(source, /args\.mode === "predeploy" \? readFile\(args\.migration/);
  assert.match(source, /if \(args\.mode === "predeploy"\) \{[\s\S]*await client\.query\(migration\);/);
  assert.match(source, /else \{[\s\S]*await assertInstalledLedger\(client\);[\s\S]*await assertInstalledBoundary\(client\);/);
  assert.doesNotMatch(source, /client\.query\(["']commit["']\)/i);
});

test("remote probe receives private inputs by file and redacts unexpected failures", () => {
  assert.match(source, /--db-url-file/);
  assert.match(source, /--tls-ca-file/);
  assert.match(source, /rejectUnauthorized:\s*true/);
  assert.match(source, /probe_failed_redacted/);
  assert.doesNotMatch(source, /console\.(?:log|error)/);
});
