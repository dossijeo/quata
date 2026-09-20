import { setTimeout as delay } from "node:timers/promises";

function numericId(value, label) {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) throw new Error(`chat_read_lifecycle_invalid_${label}`);
  return parsed;
}

function messagesFrom(payload) {
  return [
    payload?.message,
    ...(Array.isArray(payload?.messages) ? payload.messages : []),
    ...(Array.isArray(payload?.update?.messages) ? payload.update.messages : []),
  ].filter(Boolean);
}

export async function observeChatReadLifecycle({
  withDatabase,
  rpc,
  config,
  senderSession,
  readerProfileId,
  threadId,
  messageId,
  timeoutMs = 45_000,
  pollIntervalMs = 500,
}) {
  if (typeof withDatabase !== "function" || typeof rpc !== "function") {
    throw new Error("chat_read_lifecycle_observer_invalid");
  }
  const expectedThreadId = numericId(threadId, "thread_id");
  const expectedMessageId = numericId(messageId, "message_id");
  const deadline = Date.now() + timeoutMs;
  let last = null;

  while (Date.now() < deadline) {
    const database = await withDatabase(async (client) => {
      const result = await client.query(
        `select
          (select count(*)::int from public.chat_message_states
            where thread_id = $1 and message_id = $2 and profile_id = $3 and status = 'READ') as read_states,
          (select count(*)::int from public.chat_message_reads
            where message_id = $2 and profile_id = $3) as read_rows,
          (select count(*)::int from public.chat_message_states
            where thread_id = $1 and message_id = $2 and profile_id = $3 and status = 'DELIVERED') as delivered_states,
          (select coalesce(last_read_message_id, 0)::bigint from public.chat_participants
            where thread_id = $1 and profile_id = $3 and left_at is null) as participant_last_read_message_id,
          (select coalesce(last_read_message_id, 0)::bigint from public.conversation_user_state
            where conversation_id = $1 and user_id = $3 and deleted_at is null) as user_state_last_read_message_id`,
        [expectedThreadId, expectedMessageId, readerProfileId],
      );
      return result.rows[0] ?? {};
    });
    const senderPayload = await rpc(config, senderSession, "quata_chat_get_thread", {
      p_actor_profile_id: senderSession.profileId,
      p_thread_id: expectedThreadId,
      p_known_message_ids: [],
      p_limit: 250,
    });
    const senderMessage = messagesFrom(senderPayload).find((message) => Number(message?.id) === expectedMessageId);
    last = {
      readStates: Number(database.read_states ?? 0),
      readRows: Number(database.read_rows ?? 0),
      deliveredStates: Number(database.delivered_states ?? 0),
      participantLastReadMessageId: Number(database.participant_last_read_message_id ?? 0),
      userStateLastReadMessageId: Number(database.user_state_last_read_message_id ?? 0),
      senderDeliveryState: String(senderMessage?.delivery_state ?? "").toUpperCase(),
    };
    if (
      last.readStates === 1
      && last.readRows === 1
      && last.participantLastReadMessageId >= expectedMessageId
      && last.userStateLastReadMessageId >= expectedMessageId
      && last.senderDeliveryState === "READ"
    ) {
      return last;
    }
    await delay(pollIntervalMs);
  }
  throw new Error(`chat_read_lifecycle_not_observed:${JSON.stringify(last)}`);
}
