// A narrow precondition for the focal text-only fixture, not a cleanup owner.
// The coordinator must separately verify ownership and hold the thread/messages
// locked through this audit, deletion and residue verification in one transaction.
export async function assertNoExternalDeepLinkReferences({client, threadId}) {
  if (typeof threadId !== "string" || !/^[1-9][0-9]*$/.test(threadId)) {
    throw Error("deep_link_cleanup_invalid_thread");
  }
  let result;
  try {
    result = await client.query(`with owned_messages as (
      select id from public.chat_messages where thread_id = $1::bigint
    ) select
      (select count(*)::text from public.chat_messages where thread_id is distinct from $1::bigint
        and (reply_to_message_id in (select id from owned_messages)
          or forwarded_from_message_id in (select id from owned_messages))) as external_messages,
      (select count(*)::text from public.conversation_user_state where conversation_id is distinct from $1::bigint
        and (first_visible_message_id in (select id from owned_messages)
          or last_read_message_id in (select id from owned_messages))) as external_conversation_state,
      (select count(*)::text from public.chat_sos_events where thread_id = $1::bigint
        or message_id in (select id from owned_messages)) as sos_events,
      (select count(*)::text from public.chat_sos_recipients
        where delivered_thread_id = $1::bigint) as sos_recipients`, [threadId]);
  } catch {
    throw Error("deep_link_cleanup_reference_audit_failed");
  }
  const keys = ["external_messages", "external_conversation_state", "sos_events", "sos_recipients"];
  if (result.rowCount !== 1 || !result.rows?.[0] ||
      keys.some(key => result.rows[0][key] !== "0")) {
    throw Error("deep_link_cleanup_external_references_or_incomplete_audit");
  }
  return {externalReferences: 0};
}
