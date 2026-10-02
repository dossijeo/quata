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

test("remote probe receives private inputs by file and redacts unexpected failures", () => {
  assert.match(source, /--db-url-file/);
  assert.match(source, /--tls-ca-file/);
  assert.match(source, /rejectUnauthorized:\s*true/);
  assert.match(source, /probe_failed_redacted/);
  assert.doesNotMatch(source, /console\.(?:log|error)/);
});
