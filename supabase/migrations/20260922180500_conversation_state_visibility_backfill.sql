-- Repair only conversation states whose initial visibility boundary is still absent.
-- The historical replay would also rewrite updated_at on every state; this bounded repair
-- changes rows only when the product-visible first message boundary is missing.

update public.conversation_user_state s
set first_visible_message_id = (
    select min(m.id)
    from public.chat_messages m
    where m.thread_id = s.conversation_id
)
where s.first_visible_message_id is null
  and exists (
      select 1
      from public.chat_messages m
      where m.thread_id = s.conversation_id
  );
