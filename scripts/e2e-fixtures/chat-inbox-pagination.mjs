function threadId(row) {
  const value = Number(row?.thread_id ?? row?.id);
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error("chat_inbox_pagination_invalid_thread_id");
  return value;
}

function cursor(payload) {
  const value = payload?.next_cursor;
  const thread = Number(value?.thread_id);
  if (!value || !Number.isSafeInteger(thread) || thread <= 0 || !value.updated_at) {
    throw new Error("chat_inbox_pagination_invalid_cursor");
  }
  return {
    lastMessageAt: value.last_message_at ?? null,
    updatedAt: value.updated_at,
    threadId: thread,
  };
}

export async function verifyChatInboxCursorPagination({
  rpc,
  config,
  session,
  expectedThreadIds,
}) {
  const expected = new Set(expectedThreadIds.map(Number));
  if (expected.size !== 2 || [...expected].some((value) => !Number.isSafeInteger(value) || value <= 0)) {
    throw new Error("chat_inbox_pagination_invalid_fixture");
  }
  const first = await rpc(config, session, "quata_chat_get_inbox_page", {
    p_actor_profile_id: session.profileId,
    p_limit: 1,
    p_before_last_message_at: null,
    p_before_updated_at: null,
    p_before_thread_id: null,
  });
  const firstRows = Array.isArray(first?.threads) ? first.threads : [];
  if (firstRows.length !== 1 || first?.has_more !== true) {
    throw new Error("chat_inbox_pagination_first_page_invalid");
  }
  const next = cursor(first);
  const second = await rpc(config, session, "quata_chat_get_inbox_page", {
    p_actor_profile_id: session.profileId,
    p_limit: 1,
    p_before_last_message_at: next.lastMessageAt,
    p_before_updated_at: next.updatedAt,
    p_before_thread_id: next.threadId,
  });
  const secondRows = Array.isArray(second?.threads) ? second.threads : [];
  if (secondRows.length !== 1) throw new Error("chat_inbox_pagination_second_page_invalid");
  const observed = [threadId(firstRows[0]), threadId(secondRows[0])];
  if (new Set(observed).size !== 2 || observed.some((value) => !expected.has(value))) {
    throw new Error("chat_inbox_pagination_fixture_order_or_identity_invalid");
  }
  return {
    rpc: "quata_chat_get_inbox_page",
    pageSize: 1,
    pagesObserved: 2,
    distinctThreads: true,
    fixtureThreadsObserved: 2,
    firstPageHasMore: true,
    cursorFields: ["last_message_at", "updated_at", "thread_id"],
  };
}
