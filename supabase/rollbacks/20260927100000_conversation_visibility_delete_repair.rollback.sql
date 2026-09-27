begin;

drop trigger if exists chat_messages_repoint_visibility_before_delete on public.chat_messages;
drop function if exists public.quata_chat_repoint_visibility_before_message_delete();

-- The bounded backfill is deliberately retained: after the repair, a former
-- null cannot be distinguished safely from a boundary created later. The Full
-- encrypted pre-release backup is the recovery source if data restoration is
-- explicitly required.

commit;
