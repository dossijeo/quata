-- WhatsApp-style per-recipient chat message states for Supabase chat.

create table if not exists public.chat_message_states (
    message_id bigint not null references public.chat_messages(id) on delete cascade,
    thread_id bigint not null references public.chat_threads(id) on delete cascade,
    profile_id uuid not null references public.community_profiles(id) on delete cascade,
    status text not null check (status in ('DELIVERED', 'READ')),
    source text,
    recorded_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (message_id, profile_id, status)
);

create index if not exists chat_message_states_thread_idx
    on public.chat_message_states(thread_id, updated_at desc);

create index if not exists chat_message_states_message_status_idx
    on public.chat_message_states(message_id, status, profile_id);

create index if not exists chat_message_states_profile_idx
    on public.chat_message_states(profile_id, updated_at desc);

alter table public.chat_message_states enable row level security;

drop policy if exists chat_message_states_select_thread_participants on public.chat_message_states;
create policy chat_message_states_select_thread_participants
on public.chat_message_states
for select
to authenticated
using (
    public.quata_chat_is_thread_participant(thread_id, public.quata_chat_auth_profile_id())
);

create or replace function public.quata_chat_touch_message_state_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists chat_message_states_touch_updated_at on public.chat_message_states;
create trigger chat_message_states_touch_updated_at
before update on public.chat_message_states
for each row
execute function public.quata_chat_touch_message_state_updated_at();

create or replace function public.quata_chat_mark_messages_state(
    p_actor_profile_id uuid,
    p_message_ids bigint[],
    p_status text,
    p_source text default 'client'
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_status text := upper(trim(coalesce(p_status, '')));
    v_message_ids bigint[] := coalesce(p_message_ids, array[]::bigint[]);
    v_affected integer := 0;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if v_status not in ('DELIVERED', 'READ') then
        raise exception 'invalid message state' using errcode = '22023';
    end if;

    if cardinality(v_message_ids) = 0 then
        return jsonb_build_object('result', true, 'status', v_status, 'affected', 0);
    end if;

    with candidates as (
        select distinct m.id as message_id, m.thread_id
        from public.chat_messages m
        join public.chat_participants p
          on p.thread_id = m.thread_id
         and p.profile_id = v_actor
         and p.left_at is null
        where m.id = any(v_message_ids)
          and m.deleted_at is null
          and m.sender_profile_id <> v_actor
    ),
    delivered_upsert as (
        insert into public.chat_message_states(message_id, thread_id, profile_id, status, source, recorded_at, updated_at)
        select c.message_id, c.thread_id, v_actor, 'DELIVERED', left(nullif(coalesce(p_source, ''), ''), 40), now(), now()
        from candidates c
        on conflict (message_id, profile_id, status)
        do update set updated_at = excluded.updated_at,
                      source = coalesce(excluded.source, public.chat_message_states.source)
        returning message_id
    ),
    read_upsert as (
        insert into public.chat_message_states(message_id, thread_id, profile_id, status, source, recorded_at, updated_at)
        select c.message_id, c.thread_id, v_actor, 'READ', left(nullif(coalesce(p_source, ''), ''), 40), now(), now()
        from candidates c
        where v_status = 'READ'
        on conflict (message_id, profile_id, status)
        do update set updated_at = excluded.updated_at,
                      source = coalesce(excluded.source, public.chat_message_states.source)
        returning message_id
    ),
    read_receipts as (
        insert into public.chat_message_reads(message_id, profile_id, read_at)
        select c.message_id, v_actor, now()
        from candidates c
        where v_status = 'READ'
        on conflict (message_id, profile_id)
        do update set read_at = excluded.read_at
        returning message_id
    )
    select count(*)
    into v_affected
    from candidates;

    if v_status = 'READ' then
        with read_max as (
            select m.thread_id, max(m.id) as last_message_id
            from public.chat_messages m
            where m.id = any(v_message_ids)
              and m.sender_profile_id <> v_actor
            group by m.thread_id
        )
        update public.chat_participants p
        set last_read_at = now(),
            last_read_message_id = greatest(coalesce(p.last_read_message_id, 0), read_max.last_message_id)
        from read_max
        where p.thread_id = read_max.thread_id
          and p.profile_id = v_actor
          and p.left_at is null;
    end if;

    return jsonb_build_object(
        'result', true,
        'status', v_status,
        'affected', coalesce(v_affected, 0)
    );
end;
$$;

create or replace function public.quata_chat_mark_message_state(
    p_actor_profile_id uuid,
    p_message_id bigint,
    p_status text,
    p_source text default 'client'
)
returns jsonb
language sql
security definer
set search_path = public
as $$
    select public.quata_chat_mark_messages_state(
        p_actor_profile_id,
        array[p_message_id],
        p_status,
        p_source
    )
$$;

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
    with message_row as (
        select *
        from public.chat_messages
        where id = p_message_id
    ),
    recipient_state as (
        select count(*)::int as recipient_count
        from message_row m
        join public.chat_participants p
          on p.thread_id = m.thread_id
         and p.left_at is null
         and p.profile_id <> m.sender_profile_id
    ),
    status_state as (
        select
            count(distinct s.profile_id) filter (where s.status in ('DELIVERED', 'READ'))::int as delivered_count,
            count(distinct s.profile_id) filter (where s.status = 'READ')::int as read_count
        from public.chat_message_states s
        where s.message_id = p_message_id
    )
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
        'delivery_recipient_count', coalesce(recipient_state.recipient_count, 0),
        'delivered_count', coalesce(status_state.delivered_count, 0),
        'read_count', coalesce(status_state.read_count, 0),
        'delivery_state', case
            when m.sender_profile_id <> p_actor_profile_id then null
            when coalesce(recipient_state.recipient_count, 0) = 0 then 'SENT'
            when coalesce(status_state.read_count, 0) >= recipient_state.recipient_count then 'READ'
            when coalesce(status_state.delivered_count, 0) >= recipient_state.recipient_count then 'DELIVERED'
            else 'SENT'
        end,
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
    from message_row m
    cross join recipient_state
    cross join status_state
$$;

create or replace function public.quata_chat_mark_thread_read(
    p_actor_profile_id uuid,
    p_thread_id bigint
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_last_message_id bigint;
    v_message_ids bigint[];
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;

    select max(id)
    into v_last_message_id
    from public.chat_messages
    where thread_id = p_thread_id;

    select coalesce(array_agg(m.id order by m.id), array[]::bigint[])
    into v_message_ids
    from public.chat_messages m
    where m.thread_id = p_thread_id
      and m.deleted_at is null
      and m.sender_profile_id <> v_actor;

    update public.chat_participants
    set last_read_at = now(),
        last_read_message_id = v_last_message_id
    where thread_id = p_thread_id
      and profile_id = v_actor;

    insert into public.chat_message_reads(message_id, profile_id, read_at)
    select m.id, v_actor, now()
    from public.chat_messages m
    where m.thread_id = p_thread_id
      and m.sender_profile_id <> v_actor
    on conflict (message_id, profile_id)
    do update set read_at = excluded.read_at;

    perform public.quata_chat_mark_messages_state(v_actor, v_message_ids, 'READ', 'thread_read');

    return jsonb_build_object('result', true, 'thread_id', p_thread_id);
end;
$$;

grant select on public.chat_message_states to authenticated;
grant execute on function public.quata_chat_mark_messages_state(uuid, bigint[], text, text) to anon, authenticated;
grant execute on function public.quata_chat_mark_message_state(uuid, bigint, text, text) to anon, authenticated;

do $$
begin
    begin
        alter publication supabase_realtime add table public.chat_message_states;
    exception
        when duplicate_object then null;
        when undefined_object then null;
    end;
end $$;
