import test from "node:test";
import assert from "node:assert/strict";
import {assertNoExternalDeepLinkReferences} from "./e2e-fixtures/chat-deep-link-cleanup.mjs";

const zero = {external_messages: "0", external_conversation_state: "0", sos_events: "0", sos_recipients: "0"};
const audit = (rows = [zero], rowCount = rows.length) => assertNoExternalDeepLinkReferences({
  threadId: "9007199254740993",
  client: {query: async (sql, args) => {
    assert.deepEqual(args, ["9007199254740993"]); // bigint must never round through Number.
    assert.doesNotMatch(sql, /\b(delete|update|insert)\b/i);
    return {rows, rowCount};
  }},
});

test("accepts complete zero-reference observation without coercing bigint", async () => {
  assert.deepEqual(await audit(), {externalReferences: 0});
});
test("refuses each external reference and incomplete or malformed observations", async () => {
  for (const key of Object.keys(zero)) {
    for (const value of ["1", undefined, null, "", "-1", 0]) {
      await assert.rejects(audit([{...zero, [key]: value}]), /external_references_or_incomplete_audit/);
    }
  }
  await assert.rejects(audit([]), /incomplete_audit/);
  await assert.rejects(audit([zero, zero]), /incomplete_audit/);
});
test("rejects invalid identities before querying and redacts database errors", async () => {
  for (const threadId of [null, 1, "0", "-1", "1;delete", "1.5"]) {
    await assert.rejects(assertNoExternalDeepLinkReferences({threadId, client: {query() {throw Error("should not query");}}}), /invalid_thread/);
  }
  await assert.rejects(assertNoExternalDeepLinkReferences({threadId: "1", client: {query() {throw Error("private row content");}}}),
    {message: "deep_link_cleanup_reference_audit_failed"});
});
