import test from "node:test";
import assert from "node:assert/strict";
import { observeChatReadLifecycle } from "./e2e-fixtures/chat-message-read-lifecycle.mjs";

test("accepts one exact READ receipt and the sender-visible READ projection", async () => {
  const calls = [];
  const result = await observeChatReadLifecycle({
    withDatabase: async (operation) => operation({
      query: async (sql, values) => {
        calls.push({ sql, values });
        return { rows: [{
          read_states: 1,
          read_rows: 1,
          delivered_states: 1,
          participant_last_read_message_id: "42",
          user_state_last_read_message_id: "42",
        }] };
      },
    }),
    rpc: async (_config, _session, name, body) => {
      assert.equal(name, "quata_chat_get_thread");
      assert.equal(body.p_thread_id, 7);
      return { messages: [{ id: 42, delivery_state: "READ" }] };
    },
    config: {},
    senderSession: { profileId: "sender" },
    readerProfileId: "reader",
    threadId: 7,
    messageId: 42,
    timeoutMs: 100,
    pollIntervalMs: 1,
  });
  assert.equal(result.senderDeliveryState, "READ");
  assert.deepEqual(calls[0].values, [7, 42, "reader"]);
});

test("does not accept a database READ until the sender projection is READ", async () => {
  let attempts = 0;
  const result = await observeChatReadLifecycle({
    withDatabase: async (operation) => operation({ query: async () => ({ rows: [{
      read_states: 1,
      read_rows: 1,
      delivered_states: 1,
      participant_last_read_message_id: 42,
      user_state_last_read_message_id: 42,
    }] }) }),
    rpc: async () => ({ messages: [{ id: 42, delivery_state: ++attempts === 1 ? "DELIVERED" : "READ" }] }),
    config: {},
    senderSession: { profileId: "sender" },
    readerProfileId: "reader",
    threadId: 7,
    messageId: 42,
    timeoutMs: 100,
    pollIntervalMs: 1,
  });
  assert.equal(attempts, 2);
  assert.equal(result.senderDeliveryState, "READ");
});
