-- Add client-side idempotency to chat sends so retried requests do not create duplicate rows.

alter table public.chat_messages
    add column if not exists client_message_id text;

create unique index if not exists chat_messages_client_message_unique_idx
    on public.chat_messages(thread_id, sender_profile_id, client_message_id)
    where client_message_id is not null;

create or replace function public.quata_chat_message_json(
    p_message_id bigint,
    p_actor_profile_id uuid
)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
    select jsonb_build_object(
        'id', m.id,
        'thread_id', m.thread_id,
        'sender_profile_id', m.sender_profile_id,
        'sender', public.quata_chat_profile_json(m.sender_profile_id),
        'body', case when m.deleted_at is null then m.body else '' end,
        'created_at', m.created_at,
        'created_at_millis', public.quata_chat_epoch_millis(m.created_at),
        'updated_at', coalesce(m.edited_at, m.updated_at),
        'updated_at_millis', public.quata_chat_epoch_millis(coalesce(m.edited_at, m.updated_at, m.created_at)),
        'edited_at', m.edited_at,
        'deleted_at', m.deleted_at,
        'is_deleted', m.deleted_at is not null,
        'is_edited', m.edited_at is not null,
        'favorited', exists (
            select 1
            from public.chat_message_favorites f
            where f.message_id = m.id
              and f.profile_id = p_actor_profile_id
        ),
        'reply_to_message_id', m.reply_to_message_id,
        'forwarded_from_message_id', m.forwarded_from_message_id,
        'forwarded_from_profile_id', m.forwarded_from_profile_id,
        'client_message_id', m.client_message_id,
        'metadata', m.metadata,
        'attachments', coalesce(
            (
                select jsonb_agg(public.quata_chat_attachment_json(a.id) order by a.id)
                from public.chat_attachments a
                where a.message_id = m.id
            ),
            '[]'::jsonb
        )
    )
    from public.chat_messages m
    where m.id = p_message_id
$$;

create or replace function public.quata_chat_send_message(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_message text default '',
    p_file_ids bigint[] default '{}',
    p_reply_to_message_id bigint default null,
    p_client_message_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_message_id bigint;
    v_existing_message_id bigint;
    v_file_count integer;
    v_client_message_id text := nullif(left(trim(coalesce(p_client_message_id, '')), 128), '');
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;

    if v_client_message_id is not null then
        select m.id
        into v_existing_message_id
        from public.chat_messages m
        where m.thread_id = p_thread_id
          and m.sender_profile_id = v_actor
          and m.client_message_id = v_client_message_id
        order by m.id desc
        limit 1;

        if v_existing_message_id is not null then
            return jsonb_build_object(
                'result', true,
                'duplicate', true,
                'message_id', v_existing_message_id,
                'thread_id', p_thread_id,
                'message', public.quata_chat_message_json(v_existing_message_id, v_actor),
                'update', public.quata_chat_get_thread(v_actor, p_thread_id)
            );
        end if;
    end if;

    select count(*)
    into v_file_count
    from public.chat_attachments a
    where a.id = any(coalesce(p_file_ids, array[]::bigint[]))
      and a.thread_id = p_thread_id
      and a.uploaded_by_profile_id = v_actor
      and a.message_id is null;

    if nullif(coalesce(p_message, ''), '') is null and v_file_count = 0 then
        raise exception 'message or file is required' using errcode = '22023';
    end if;

    if p_reply_to_message_id is not null and not exists (
        select 1
        from public.chat_messages
        where id = p_reply_to_message_id
          and thread_id = p_thread_id
    ) then
        raise exception 'reply target is not in this thread' using errcode = '22023';
    end if;

    if exists (
        select 1
        from public.chat_participants p
        where p.thread_id = p_thread_id
          and p.profile_id <> v_actor
          and p.left_at is null
          and public.quata_chat_is_blocked(p_thread_id, v_actor, p.profile_id)
    ) then
        raise exception 'message blocked by recipient' using errcode = '42501';
    end if;

    begin
        insert into public.chat_messages(thread_id, sender_profile_id, body, reply_to_message_id, client_message_id)
        values (p_thread_id, v_actor, coalesce(p_message, ''), p_reply_to_message_id, v_client_message_id)
        returning id into v_message_id;
    exception when unique_violation then
        if v_client_message_id is null then
            raise;
        end if;

        select m.id
        into v_message_id
        from public.chat_messages m
        where m.thread_id = p_thread_id
          and m.sender_profile_id = v_actor
          and m.client_message_id = v_client_message_id
        order by m.id desc
        limit 1;

        if v_message_id is null then
            raise;
        end if;
    end;

    update public.chat_attachments
    set message_id = v_message_id,
        attached_at = now()
    where id = any(coalesce(p_file_ids, array[]::bigint[]))
      and thread_id = p_thread_id
      and uploaded_by_profile_id = v_actor
      and message_id is null;

    insert into public.chat_events(thread_id, actor_profile_id, event_type, payload)
    values (p_thread_id, v_actor, 'message_sent', jsonb_build_object('message_id', v_message_id));

    return jsonb_build_object(
        'result', true,
        'message_id', v_message_id,
        'thread_id', p_thread_id,
        'message', public.quata_chat_message_json(v_message_id, v_actor),
        'update', public.quata_chat_get_thread(v_actor, p_thread_id)
    );
end;
$$;

create or replace function public.quata_chat_send_files(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_file_ids bigint[],
    p_message text default ''
)
returns jsonb
language sql
security definer
set search_path = public
as $$
    select public.quata_chat_send_message(
        p_actor_profile_id,
        p_thread_id,
        coalesce(p_message, ''),
        coalesce(p_file_ids, array[]::bigint[]),
        null,
        null
    )
$$;

grant execute on function public.quata_chat_send_message(uuid, bigint, text, bigint[], bigint, text) to anon, authenticated;
grant execute on function public.quata_chat_send_files(uuid, bigint, bigint[], text) to anon, authenticated;

drop function if exists public.quata_chat_send_message(uuid, bigint, text, bigint[], bigint);
