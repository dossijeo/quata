begin;

drop function if exists public.quata_chat_delete_messages_v2(uuid, bigint, bigint[], text);
drop function if exists public.quata_chat_edit_message_v2(uuid, bigint, bigint, text, text);
drop table if exists public.chat_message_mutation_receipts;

commit;
