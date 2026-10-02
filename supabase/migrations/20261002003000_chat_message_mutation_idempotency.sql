begin;

-- New authenticated clients attach one stable key to an edit/delete intent. The
-- receipt and the product mutation commit in the same transaction, so retrying
-- an unknown response returns the first result without repeating chat_events.
-- The published Android v32 RPCs remain unchanged and separate.
create table if not exists public.chat_message_mutation_receipts (
    actor_profile_id uuid not null references public.community_profiles(id) on delete cascade,
    client_mutation_id text not null,
    operation text not null check (operation in ('edit', 'delete')),
    thread_id bigint not null references public.chat_threads(id) on delete cascade,
    message_ids bigint[] not null,
    payload_text text,
    response jsonb,
    created_at timestamptz not null default now(),
    completed_at timestamptz,
    primary key (actor_profile_id, client_mutation_id),
    check (char_length(client_mutation_id) between 16 and 128),
    check (client_mutation_id ~ '^[A-Za-z0-9._:-]+$'),
    check (cardinality(message_ids) > 0),
    check ((operation = 'edit' and cardinality(message_ids) = 1 and payload_text is not null)
        or (operation = 'delete' and payload_text is null))
);

alter table public.chat_message_mutation_receipts enable row level security;
revoke all on table public.chat_message_mutation_receipts from public, anon, authenticated;

create or replace function public.quata_chat_edit_message_v2(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_message_id bigint,
    p_message text,
    p_client_mutation_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_inserted integer;
    v_receipt public.chat_message_mutation_receipts%rowtype;
    v_response jsonb;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    insert into public.chat_message_mutation_receipts(
        actor_profile_id, client_mutation_id, operation, thread_id, message_ids, payload_text
    ) values (
        v_actor, p_client_mutation_id, 'edit', p_thread_id, array[p_message_id], coalesce(p_message, '')
    ) on conflict (actor_profile_id, client_mutation_id) do nothing;
    get diagnostics v_inserted = row_count;

    if v_inserted = 0 then
        select * into strict v_receipt
          from public.chat_message_mutation_receipts
         where actor_profile_id = v_actor
           and client_mutation_id = p_client_mutation_id;
        if v_receipt.operation is distinct from 'edit'
           or v_receipt.thread_id is distinct from p_thread_id
           or v_receipt.message_ids is distinct from array[p_message_id]
           or v_receipt.payload_text is distinct from coalesce(p_message, '') then
            raise exception 'client mutation id was reused for a different request' using errcode = '22023';
        end if;
        if v_receipt.response is null then
            raise exception 'client mutation receipt is incomplete' using errcode = '55000';
        end if;
        return v_receipt.response;
    end if;

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;

    update public.chat_messages m
       set body = coalesce(p_message, ''),
           edited_at = now(),
           updated_at = now()
     where m.id = p_message_id
       and m.thread_id = p_thread_id
       and m.deleted_at is null
       and m.sender_profile_id = v_actor;

    if not found then
        raise exception 'message cannot be edited' using errcode = '42501';
    end if;

    insert into public.chat_events(thread_id, actor_profile_id, event_type, payload)
    values (p_thread_id, v_actor, 'message_edited', jsonb_build_object('message_id', p_message_id));

    v_response := public.quata_chat_get_thread(v_actor, p_thread_id);
    update public.chat_message_mutation_receipts
       set response = v_response, completed_at = now()
     where actor_profile_id = v_actor and client_mutation_id = p_client_mutation_id;
    return v_response;
end;
$$;

create or replace function public.quata_chat_delete_messages_v2(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_message_ids bigint[],
    p_client_mutation_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_inserted integer;
    v_receipt public.chat_message_mutation_receipts%rowtype;
    v_response jsonb;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    insert into public.chat_message_mutation_receipts(
        actor_profile_id, client_mutation_id, operation, thread_id, message_ids, payload_text
    ) values (
        v_actor, p_client_mutation_id, 'delete', p_thread_id, coalesce(p_message_ids, array[]::bigint[]), null
    ) on conflict (actor_profile_id, client_mutation_id) do nothing;
    get diagnostics v_inserted = row_count;

    if v_inserted = 0 then
        select * into strict v_receipt
          from public.chat_message_mutation_receipts
         where actor_profile_id = v_actor
           and client_mutation_id = p_client_mutation_id;
        if v_receipt.operation is distinct from 'delete'
           or v_receipt.thread_id is distinct from p_thread_id
           or v_receipt.message_ids is distinct from coalesce(p_message_ids, array[]::bigint[]) then
            raise exception 'client mutation id was reused for a different request' using errcode = '22023';
        end if;
        if v_receipt.response is null then
            raise exception 'client mutation receipt is incomplete' using errcode = '55000';
        end if;
        return v_receipt.response;
    end if;

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;

    if exists (
        select 1
          from public.chat_messages m
         where m.thread_id = p_thread_id
           and m.id = any(coalesce(p_message_ids, array[]::bigint[]))
           and m.deleted_at is null
           and m.sender_profile_id is distinct from v_actor
    ) then
        raise exception 'messages cannot be deleted' using errcode = '42501';
    end if;

    update public.chat_messages m
       set deleted_at = now(),
           deleted_by_profile_id = v_actor,
           updated_at = now()
     where m.thread_id = p_thread_id
       and m.id = any(coalesce(p_message_ids, array[]::bigint[]))
       and m.deleted_at is null
       and m.sender_profile_id = v_actor;

    insert into public.chat_events(thread_id, actor_profile_id, event_type, payload)
    values (p_thread_id, v_actor, 'messages_deleted', jsonb_build_object('message_ids', to_jsonb(p_message_ids)));

    v_response := public.quata_chat_get_thread(v_actor, p_thread_id);
    update public.chat_message_mutation_receipts
       set response = v_response, completed_at = now()
     where actor_profile_id = v_actor and client_mutation_id = p_client_mutation_id;
    return v_response;
end;
$$;

-- Supabase may install explicit default EXECUTE grants for anon/authenticated;
-- revoking PUBLIC alone is therefore insufficient for an authenticated-only v2 lane.
revoke all on function public.quata_chat_edit_message_v2(uuid, bigint, bigint, text, text) from public, anon, authenticated;
revoke all on function public.quata_chat_delete_messages_v2(uuid, bigint, bigint[], text) from public, anon, authenticated;
grant execute on function public.quata_chat_edit_message_v2(uuid, bigint, bigint, text, text) to authenticated;
grant execute on function public.quata_chat_delete_messages_v2(uuid, bigint, bigint[], text) to authenticated;

commit;
