-- Per-user chat visibility state.
-- Keeps delete/mute/read state independent per participant while preserving
-- the existing Android RPC surface.

create table if not exists public.conversation_user_state (
    conversation_id bigint not null references public.chat_threads(id) on delete cascade,
    user_id uuid not null references public.community_profiles(id) on delete cascade,
    deleted_at timestamptz,
    first_visible_message_id bigint references public.chat_messages(id) on delete set null,
    muted_at timestamptz,
    last_read_message_id bigint references public.chat_messages(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint conversation_user_state_pkey primary key (conversation_id, user_id),
    constraint conversation_user_state_unique unique (conversation_id, user_id)
);

create index if not exists conversation_user_state_user_active_idx
    on public.conversation_user_state(user_id, deleted_at, updated_at desc);
create index if not exists conversation_user_state_conversation_idx
    on public.conversation_user_state(conversation_id, deleted_at);
create index if not exists conversation_user_state_first_visible_idx
    on public.conversation_user_state(conversation_id, user_id, first_visible_message_id);

alter table public.conversation_user_state enable row level security;

drop policy if exists conversation_user_state_select_thread_participants on public.conversation_user_state;
create policy conversation_user_state_select_thread_participants
on public.conversation_user_state
for select
to authenticated
using (
    public.quata_chat_is_thread_participant(conversation_id, public.quata_chat_auth_profile_id())
);

create or replace function public.quata_chat_touch_conversation_user_state_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists conversation_user_state_touch_updated_at on public.conversation_user_state;
create trigger conversation_user_state_touch_updated_at
before update on public.conversation_user_state
for each row
execute function public.quata_chat_touch_conversation_user_state_updated_at();

create or replace function public.quata_chat_first_message_id(p_thread_id bigint)
returns bigint
language sql
stable
security definer
set search_path = public
as $$
    select min(m.id)
    from public.chat_messages m
    where m.thread_id = p_thread_id
$$;

create or replace function public.quata_chat_ensure_conversation_user_state(
    p_conversation_id bigint,
    p_user_id uuid,
    p_first_visible_message_id bigint default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_first_visible_message_id bigint;
begin
    if p_conversation_id is null or p_user_id is null then
        return;
    end if;

    v_first_visible_message_id := coalesce(
        p_first_visible_message_id,
        public.quata_chat_first_message_id(p_conversation_id)
    );

    insert into public.conversation_user_state(
        conversation_id,
        user_id,
        first_visible_message_id,
        created_at,
        updated_at
    )
    values (
        p_conversation_id,
        p_user_id,
        v_first_visible_message_id,
        now(),
        now()
    )
    on conflict (conversation_id, user_id)
    do update set
        first_visible_message_id = coalesce(
            public.conversation_user_state.first_visible_message_id,
            excluded.first_visible_message_id
        );
end;
$$;

-- Existing chats start as active for every participant. The first visible
-- message is the first message in the thread so old app versions keep seeing
-- the same history through the RPCs.
insert into public.conversation_user_state(
    conversation_id,
    user_id,
    deleted_at,
    first_visible_message_id,
    muted_at,
    last_read_message_id,
    created_at,
    updated_at
)
select
    p.thread_id,
    p.profile_id,
    null::timestamptz,
    min(m.id),
    p.muted_at,
    p.last_read_message_id,
    min(p.joined_at),
    now()
from public.chat_participants p
left join public.chat_messages m
  on m.thread_id = p.thread_id
group by p.thread_id, p.profile_id, p.muted_at, p.last_read_message_id
on conflict (conversation_id, user_id)
do update set
    first_visible_message_id = coalesce(
        public.conversation_user_state.first_visible_message_id,
        excluded.first_visible_message_id
    ),
    muted_at = coalesce(public.conversation_user_state.muted_at, excluded.muted_at),
    last_read_message_id = nullif(greatest(
        coalesce(public.conversation_user_state.last_read_message_id, 0),
        coalesce(excluded.last_read_message_id, 0)
    ), 0);

create or replace function public.quata_chat_participant_sync_user_state()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    perform public.quata_chat_ensure_conversation_user_state(new.thread_id, new.profile_id);

    update public.conversation_user_state s
    set muted_at = new.muted_at,
        last_read_message_id = nullif(greatest(
            coalesce(s.last_read_message_id, 0),
            coalesce(new.last_read_message_id, 0)
        ), 0)
    where s.conversation_id = new.thread_id
      and s.user_id = new.profile_id;

    return new;
end;
$$;

drop trigger if exists chat_participants_sync_conversation_user_state on public.chat_participants;
create trigger chat_participants_sync_conversation_user_state
after insert or update of muted_at, last_read_message_id on public.chat_participants
for each row
execute function public.quata_chat_participant_sync_user_state();

create or replace function public.quata_chat_reactivate_user_state_for_message()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.conversation_user_state(
        conversation_id,
        user_id,
        first_visible_message_id,
        muted_at,
        last_read_message_id,
        created_at,
        updated_at
    )
    select
        p.thread_id,
        p.profile_id,
        coalesce(public.quata_chat_first_message_id(p.thread_id), new.id),
        p.muted_at,
        p.last_read_message_id,
        now(),
        now()
    from public.chat_participants p
    where p.thread_id = new.thread_id
      and p.left_at is null
    on conflict (conversation_id, user_id)
    do nothing;

    update public.conversation_user_state s
    set deleted_at = null,
        first_visible_message_id = case
            when s.deleted_at is not null then new.id
            else coalesce(s.first_visible_message_id, new.id)
        end
    where s.conversation_id = new.thread_id
      and s.user_id in (
          select p.profile_id
          from public.chat_participants p
          where p.thread_id = new.thread_id
            and p.left_at is null
            and (
                p.profile_id = new.sender_profile_id
                or s.deleted_at is not null
            )
      );

    update public.chat_participants p
    set is_hidden = false,
        is_deleted = false
    where p.thread_id = new.thread_id
      and p.left_at is null
      and exists (
          select 1
          from public.conversation_user_state s
          where s.conversation_id = p.thread_id
            and s.user_id = p.profile_id
            and s.deleted_at is null
      );

    return new;
end;
$$;

drop trigger if exists chat_messages_before_insert_reactivate_user_state on public.chat_messages;
drop trigger if exists aa_chat_messages_after_insert_reactivate_user_state on public.chat_messages;
create trigger aa_chat_messages_after_insert_reactivate_user_state
after insert on public.chat_messages
for each row
execute function public.quata_chat_reactivate_user_state_for_message();

create or replace function public.quata_chat_thread_json(
    p_thread_id bigint,
    p_actor_profile_id uuid
)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
    with t as (
        select *
        from public.chat_threads
        where id = p_thread_id
    ),
    actor_state as (
        select *
        from public.chat_participants
        where thread_id = p_thread_id
          and profile_id = p_actor_profile_id
    ),
    user_state as (
        select *
        from public.conversation_user_state
        where conversation_id = p_thread_id
          and user_id = p_actor_profile_id
    ),
    visibility as (
        select
            user_state.deleted_at,
            user_state.first_visible_message_id,
            coalesce(user_state.muted_at, actor_state.muted_at) as muted_at,
            greatest(
                coalesce(user_state.last_read_message_id, 0),
                coalesce(actor_state.last_read_message_id, 0)
            ) as last_read_message_id,
            actor_state.last_read_at,
            actor_state.pinned_at,
            actor_state.role
        from actor_state
        left join user_state on true
    ),
    participant_state as (
        select
            coalesce(jsonb_agg(p.profile_id order by p.joined_at, p.profile_id), '[]'::jsonb) as participant_ids,
            coalesce(
                jsonb_agg(p.profile_id order by p.joined_at, p.profile_id)
                    filter (where p.role in ('owner', 'moderator')),
                '[]'::jsonb
            ) as moderator_ids,
            count(*)::int as participants_count
        from public.chat_participants p
        where p.thread_id = p_thread_id
          and p.left_at is null
    ),
    unread_state as (
        select count(*)::int as unread_count
        from public.chat_messages m
        cross join visibility v
        where m.thread_id = p_thread_id
          and v.deleted_at is null
          and m.deleted_at is null
          and m.sender_profile_id <> p_actor_profile_id
          and (v.first_visible_message_id is null or m.id >= v.first_visible_message_id)
          and (
              v.last_read_at is null
              or m.created_at > v.last_read_at
              or m.id > coalesce(v.last_read_message_id, 0)
          )
    )
    select jsonb_build_object(
        'id', t.id,
        'thread_id', t.id,
        'type', t.type,
        'title', coalesce(nullif(t.subject, ''), nullif(t.title, ''), 'Chat ' || t.id::text),
        'subject', t.subject,
        'image', t.image_url,
        'community_id', t.community_id,
        'created_by_profile_id', t.created_by_profile_id,
        'unique_key', t.unique_key,
        'participants', participant_state.participant_ids,
        'participants_count', participant_state.participants_count,
        'moderators', participant_state.moderator_ids,
        'last_message_at', t.last_message_at,
        'last_time_millis', public.quata_chat_epoch_millis(t.last_message_at),
        'last_message_preview', t.last_message_preview,
        'created_at', t.created_at,
        'updated_at', t.updated_at,
        'updated_at_millis', public.quata_chat_epoch_millis(t.updated_at),
        'is_hidden', visibility.deleted_at is not null,
        'is_deleted', visibility.deleted_at is not null or t.deleted_at is not null,
        'is_muted', visibility.muted_at is not null,
        'is_pinned', visibility.pinned_at is not null,
        'unread', coalesce(unread_state.unread_count, 0),
        'meta', jsonb_build_object('allowInvite', t.allow_invite),
        'permissions', jsonb_build_object(
            'isModerator', visibility.role in ('owner', 'moderator'),
            'deleteAllowed', true,
            'canDeleteOwnMessages', true,
            'canDeleteAllMessages', visibility.role in ('owner', 'moderator'),
            'canEditOwnMessages', true,
            'canEditAllMessages', visibility.role in ('owner', 'moderator'),
            'canFavorite', true,
            'canMuteThread', true,
            'canEraseThread', true,
            'canClearThread', true,
            'canInvite', t.allow_invite or visibility.role in ('owner', 'moderator'),
            'canLeave', true,
            'canUpload', true,
            'canReply', true
        ),
        'metadata', t.metadata
    )
    from t
    left join actor_state on true
    left join visibility on true
    cross join participant_state
    cross join unread_state
$$;

create or replace function public.quata_chat_get_thread(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_known_message_ids bigint[] default '{}',
    p_limit integer default 250
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_messages jsonb;
    v_profiles jsonb;
    v_limit integer := greatest(1, least(coalesce(p_limit, 250), 500));
    v_deleted_at timestamptz;
    v_first_visible_message_id bigint;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;

    perform public.quata_chat_ensure_conversation_user_state(p_thread_id, v_actor);

    select s.deleted_at, s.first_visible_message_id
    into v_deleted_at, v_first_visible_message_id
    from public.conversation_user_state s
    where s.conversation_id = p_thread_id
      and s.user_id = v_actor;

    select coalesce(jsonb_agg(public.quata_chat_message_json(q.id, v_actor) order by q.created_at, q.id), '[]'::jsonb)
    into v_messages
    from (
        select m.id, m.created_at
        from public.chat_messages m
        where m.thread_id = p_thread_id
          and v_deleted_at is null
          and (v_first_visible_message_id is null or m.id >= v_first_visible_message_id)
          and not (m.id = any(coalesce(p_known_message_ids, array[]::bigint[])))
        order by m.created_at desc, m.id desc
        limit v_limit
    ) q;

    select coalesce(jsonb_agg(public.quata_chat_profile_json(p.profile_id) order by p.joined_at), '[]'::jsonb)
    into v_profiles
    from public.chat_participants p
    where p.thread_id = p_thread_id
      and p.left_at is null;

    return jsonb_build_object(
        'threads', jsonb_build_array(public.quata_chat_thread_json(p_thread_id, v_actor)),
        'thread', public.quata_chat_thread_json(p_thread_id, v_actor),
        'profiles', v_profiles,
        'messages', coalesce(v_messages, '[]'::jsonb),
        'server_time_millis', public.quata_chat_epoch_millis(clock_timestamp())
    );
end;
$$;

create or replace function public.quata_chat_get_inbox(
    p_actor_profile_id uuid,
    p_limit integer default 100
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_limit integer := greatest(1, least(coalesce(p_limit, 100), 250));
    v_thread_ids bigint[];
    v_threads jsonb;
    v_messages jsonb;
    v_profiles jsonb;
    v_unread_total integer;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    insert into public.conversation_user_state(conversation_id, user_id, first_visible_message_id, muted_at, last_read_message_id)
    select p.thread_id, p.profile_id, public.quata_chat_first_message_id(p.thread_id), p.muted_at, p.last_read_message_id
    from public.chat_participants p
    where p.profile_id = v_actor
    on conflict (conversation_id, user_id)
    do nothing;

    select coalesce(array_agg(q.id), array[]::bigint[])
    into v_thread_ids
    from (
        select t.id
        from public.chat_threads t
        join public.chat_participants p on p.thread_id = t.id
        join public.conversation_user_state s
          on s.conversation_id = t.id
         and s.user_id = v_actor
        where p.profile_id = v_actor
          and p.left_at is null
          and s.deleted_at is null
          and t.deleted_at is null
        order by t.last_message_at desc nulls last, t.updated_at desc, t.id desc
        limit v_limit
    ) q;

    select coalesce(jsonb_agg(public.quata_chat_thread_json(t.id, v_actor) order by t.last_message_at desc nulls last, t.updated_at desc, t.id desc), '[]'::jsonb)
    into v_threads
    from public.chat_threads t
    where t.id = any(v_thread_ids);

    select coalesce(jsonb_agg(public.quata_chat_message_json(q.id, v_actor) order by q.created_at, q.id), '[]'::jsonb)
    into v_messages
    from (
        select distinct on (m.thread_id) m.id, m.thread_id, m.created_at
        from public.chat_messages m
        join public.conversation_user_state s
          on s.conversation_id = m.thread_id
         and s.user_id = v_actor
        where m.thread_id = any(v_thread_ids)
          and s.deleted_at is null
          and m.deleted_at is null
          and (s.first_visible_message_id is null or m.id >= s.first_visible_message_id)
        order by m.thread_id, m.created_at desc, m.id desc
    ) q;

    select coalesce(jsonb_agg(distinct public.quata_chat_profile_json(p.profile_id)), '[]'::jsonb)
    into v_profiles
    from public.chat_participants p
    where p.thread_id = any(v_thread_ids)
      and p.left_at is null;

    select coalesce(sum((public.quata_chat_thread_json(tid, v_actor)->>'unread')::int), 0)
    into v_unread_total
    from unnest(v_thread_ids) tid;

    return jsonb_build_object(
        'threads', coalesce(v_threads, '[]'::jsonb),
        'profiles', coalesce(v_profiles, '[]'::jsonb),
        'messages', coalesce(v_messages, '[]'::jsonb),
        'unread_total', coalesce(v_unread_total, 0),
        'current_time_millis', public.quata_chat_epoch_millis(clock_timestamp())
    );
end;
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

    perform public.quata_chat_ensure_conversation_user_state(p_thread_id, v_actor);

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
    v_first_visible_message_id bigint;
    v_deleted_at timestamptz;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;

    perform public.quata_chat_ensure_conversation_user_state(p_thread_id, v_actor);

    select s.deleted_at, s.first_visible_message_id
    into v_deleted_at, v_first_visible_message_id
    from public.conversation_user_state s
    where s.conversation_id = p_thread_id
      and s.user_id = v_actor;

    select max(id)
    into v_last_message_id
    from public.chat_messages
    where thread_id = p_thread_id
      and v_deleted_at is null
      and (v_first_visible_message_id is null or id >= v_first_visible_message_id);

    select coalesce(array_agg(m.id order by m.id), array[]::bigint[])
    into v_message_ids
    from public.chat_messages m
    where m.thread_id = p_thread_id
      and v_deleted_at is null
      and m.deleted_at is null
      and m.sender_profile_id <> v_actor
      and (v_first_visible_message_id is null or m.id >= v_first_visible_message_id);

    update public.conversation_user_state
    set last_read_message_id = v_last_message_id
    where conversation_id = p_thread_id
      and user_id = v_actor;

    update public.chat_participants
    set last_read_at = now(),
        last_read_message_id = v_last_message_id
    where thread_id = p_thread_id
      and profile_id = v_actor;

    insert into public.chat_message_reads(message_id, profile_id, read_at)
    select m.id, v_actor, now()
    from public.chat_messages m
    where m.thread_id = p_thread_id
      and v_deleted_at is null
      and m.sender_profile_id <> v_actor
      and (v_first_visible_message_id is null or m.id >= v_first_visible_message_id)
    on conflict (message_id, profile_id)
    do update set read_at = excluded.read_at;

    perform public.quata_chat_mark_messages_state(v_actor, v_message_ids, 'READ', 'thread_read');

    return jsonb_build_object('result', true, 'thread_id', p_thread_id);
end;
$$;

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
    v_status text := upper(coalesce(p_status, ''));
    v_message_ids bigint[];
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if v_status not in ('DELIVERED', 'READ') then
        raise exception 'invalid chat message state' using errcode = '22023';
    end if;

    with candidate as (
        select distinct m.id as message_id, m.thread_id
        from public.chat_messages m
        join public.chat_participants p
          on p.thread_id = m.thread_id
         and p.profile_id = v_actor
         and p.left_at is null
        join public.conversation_user_state s
          on s.conversation_id = m.thread_id
         and s.user_id = v_actor
        where m.id = any(coalesce(p_message_ids, array[]::bigint[]))
          and m.sender_profile_id <> v_actor
          and m.deleted_at is null
          and s.deleted_at is null
          and (s.first_visible_message_id is null or m.id >= s.first_visible_message_id)
    )
    select coalesce(array_agg(message_id order by message_id), array[]::bigint[])
    into v_message_ids
    from candidate;

    if v_status = 'DELIVERED' then
        with candidate as (
            select distinct m.id as message_id, m.thread_id
            from public.chat_messages m
            join public.chat_participants p
              on p.thread_id = m.thread_id
             and p.profile_id = v_actor
             and p.left_at is null
            join public.conversation_user_state s
              on s.conversation_id = m.thread_id
             and s.user_id = v_actor
            where m.id = any(v_message_ids)
        )
        insert into public.chat_message_states(message_id, thread_id, profile_id, status, source, recorded_at, updated_at)
        select c.message_id, c.thread_id, v_actor, 'DELIVERED', left(nullif(coalesce(p_source, ''), ''), 40), now(), now()
        from candidate c
        on conflict (message_id, profile_id, status)
        do update set recorded_at = least(public.chat_message_states.recorded_at, excluded.recorded_at),
                      updated_at = now(),
                      source = coalesce(excluded.source, public.chat_message_states.source);
    else
        with candidate as (
            select distinct m.id as message_id, m.thread_id
            from public.chat_messages m
            join public.chat_participants p
              on p.thread_id = m.thread_id
             and p.profile_id = v_actor
             and p.left_at is null
            join public.conversation_user_state s
              on s.conversation_id = m.thread_id
             and s.user_id = v_actor
            where m.id = any(v_message_ids)
        )
        insert into public.chat_message_states(message_id, thread_id, profile_id, status, source, recorded_at, updated_at)
        select c.message_id, c.thread_id, v_actor, 'READ', left(nullif(coalesce(p_source, ''), ''), 40), now(), now()
        from candidate c
        on conflict (message_id, profile_id, status)
        do update set recorded_at = excluded.recorded_at,
                      updated_at = now(),
                      source = coalesce(excluded.source, public.chat_message_states.source);

        insert into public.chat_message_reads(message_id, profile_id, read_at)
        select unnest(v_message_ids), v_actor, now()
        on conflict (message_id, profile_id)
        do update set read_at = excluded.read_at;

        with read_max as (
            select m.thread_id, max(m.id) as last_message_id
            from public.chat_messages m
            where m.id = any(v_message_ids)
            group by m.thread_id
        )
        update public.conversation_user_state s
        set last_read_message_id = nullif(greatest(coalesce(s.last_read_message_id, 0), read_max.last_message_id), 0)
        from read_max
        where s.conversation_id = read_max.thread_id
          and s.user_id = v_actor;

        with read_max as (
            select m.thread_id, max(m.id) as last_message_id
            from public.chat_messages m
            where m.id = any(v_message_ids)
            group by m.thread_id
        )
        update public.chat_participants p
        set last_read_at = now(),
            last_read_message_id = nullif(greatest(coalesce(p.last_read_message_id, 0), read_max.last_message_id), 0)
        from read_max
        where p.thread_id = read_max.thread_id
          and p.profile_id = v_actor;
    end if;

    return jsonb_build_object(
        'result', true,
        'status', v_status,
        'count', coalesce(array_length(v_message_ids, 1), 0)
    );
end;
$$;

create or replace function public.quata_chat_set_muted(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_muted boolean
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_muted_at timestamptz := case when coalesce(p_muted, false) then now() else null end;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;

    perform public.quata_chat_ensure_conversation_user_state(p_thread_id, v_actor);

    update public.conversation_user_state
    set muted_at = v_muted_at
    where conversation_id = p_thread_id
      and user_id = v_actor;

    update public.chat_participants
    set muted_at = v_muted_at
    where thread_id = p_thread_id
      and profile_id = v_actor
      and left_at is null;

    return jsonb_build_object('result', true, 'thread_id', p_thread_id, 'muted', coalesce(p_muted, false));
end;
$$;

create or replace function public.quata_chat_delete_thread(
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
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;

    perform public.quata_chat_ensure_conversation_user_state(p_thread_id, v_actor);

    update public.conversation_user_state
    set deleted_at = now()
    where conversation_id = p_thread_id
      and user_id = v_actor;

    update public.chat_participants
    set is_hidden = true,
        is_deleted = true
    where thread_id = p_thread_id
      and profile_id = v_actor
      and left_at is null;

    insert into public.chat_events(thread_id, actor_profile_id, event_type)
    values (p_thread_id, v_actor, 'thread_deleted_for_participant');

    return jsonb_build_object('result', true, 'thread_id', p_thread_id);
end;
$$;

create or replace function public.quata_chat_restore_thread(
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
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not related to this thread' using errcode = '42501';
    end if;

    perform public.quata_chat_ensure_conversation_user_state(p_thread_id, v_actor);

    update public.conversation_user_state
    set deleted_at = null
    where conversation_id = p_thread_id
      and user_id = v_actor;

    update public.chat_participants
    set is_hidden = false,
        is_deleted = false,
        left_at = null
    where thread_id = p_thread_id
      and profile_id = v_actor;

    return public.quata_chat_get_thread(v_actor, p_thread_id);
end;
$$;

create or replace function public.quata_chat_list_shared_attachments(
    p_actor_profile_id uuid,
    p_peer_profile_id uuid default null,
    p_thread_id bigint default null,
    p_community_id uuid default null,
    p_limit integer default 100,
    p_offset integer default 0,
    p_kind text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_limit integer := greatest(1, least(coalesce(p_limit, 100), 250));
    v_offset integer := greatest(0, coalesce(p_offset, 0));
    v_thread_ids bigint[];
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if p_thread_id is not null then
        if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
            raise exception 'profile is not a participant of this thread' using errcode = '42501';
        end if;
        v_thread_ids := array[p_thread_id];
    elsif p_peer_profile_id is not null then
        select coalesce(array_agg(t.id order by t.updated_at desc), array[]::bigint[])
        into v_thread_ids
        from public.chat_threads t
        join public.chat_participants actor_p
          on actor_p.thread_id = t.id
         and actor_p.profile_id = v_actor
         and actor_p.left_at is null
        join public.conversation_user_state actor_s
          on actor_s.conversation_id = t.id
         and actor_s.user_id = v_actor
         and actor_s.deleted_at is null
        join public.chat_participants peer_p
          on peer_p.thread_id = t.id
         and peer_p.profile_id = p_peer_profile_id
         and peer_p.left_at is null
        where t.deleted_at is null;
    elsif p_community_id is not null then
        select coalesce(array_agg(t.id order by t.updated_at desc), array[]::bigint[])
        into v_thread_ids
        from public.chat_threads t
        join public.chat_participants actor_p
          on actor_p.thread_id = t.id
         and actor_p.profile_id = v_actor
         and actor_p.left_at is null
        join public.conversation_user_state actor_s
          on actor_s.conversation_id = t.id
         and actor_s.user_id = v_actor
         and actor_s.deleted_at is null
        where t.deleted_at is null
          and t.community_id = p_community_id;
    else
        select coalesce(array_agg(t.id order by t.updated_at desc), array[]::bigint[])
        into v_thread_ids
        from public.chat_threads t
        join public.chat_participants actor_p
          on actor_p.thread_id = t.id
         and actor_p.profile_id = v_actor
         and actor_p.left_at is null
        join public.conversation_user_state actor_s
          on actor_s.conversation_id = t.id
         and actor_s.user_id = v_actor
         and actor_s.deleted_at is null
        where t.deleted_at is null;
    end if;

    return (
        select jsonb_build_object(
            'files',
            coalesce(jsonb_agg(public.quata_chat_attachment_json(q.id) order by q.created_at desc, q.id desc), '[]'::jsonb)
        )
        from (
            select a.id, a.created_at
            from public.chat_attachments a
            join public.chat_messages m
              on m.id = a.message_id
            join public.conversation_user_state s
              on s.conversation_id = a.thread_id
             and s.user_id = v_actor
            where a.thread_id = any(v_thread_ids)
              and s.deleted_at is null
              and m.deleted_at is null
              and (s.first_visible_message_id is null or m.id >= s.first_visible_message_id)
              and (
                  p_kind is null
                  or p_kind = ''
                  or (p_kind = 'image' and a.mime_type like 'image/%')
                  or (p_kind = 'video' and a.mime_type like 'video/%')
                  or (p_kind = 'document' and a.mime_type not like 'image/%' and a.mime_type not like 'video/%')
              )
            order by a.created_at desc, a.id desc
            limit v_limit
            offset v_offset
        ) q
    );
end;
$$;

grant select on public.conversation_user_state to authenticated;
grant execute on function public.quata_chat_ensure_conversation_user_state(bigint, uuid, bigint) to anon, authenticated;
grant execute on function public.quata_chat_mark_messages_state(uuid, bigint[], text, text) to anon, authenticated;

do $$
begin
    begin
        alter publication supabase_realtime add table public.conversation_user_state;
    exception
        when duplicate_object then null;
        when undefined_object then null;
        when insufficient_privilege then null;
    end;
end $$;
