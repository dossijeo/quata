-- Quata chat RPC endpoints exposed by PostgREST at /rest/v1/rpc/<function_name>.
-- These functions return JSON payloads shaped for the Android app migration.

create or replace function public.quata_chat_epoch_millis(p_value timestamptz)
returns bigint
language sql
immutable
as $$
    select case
        when p_value is null then null
        else floor(extract(epoch from p_value) * 1000)::bigint
    end
$$;

create or replace function public.quata_chat_profile_json(p_profile_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
    select jsonb_build_object(
        'id', cp.id,
        'display_name', coalesce(nullif(cp.display_name, ''), nullif(cp.nombre, ''), 'Usuario'),
        'name', coalesce(nullif(cp.display_name, ''), nullif(cp.nombre, ''), 'Usuario'),
        'avatar_url', coalesce(nullif(cp.avatar_url, ''), nullif(cp.avatar, '')),
        'neighborhood', coalesce(nullif(cp.neighborhood, ''), nullif(cp.barrio, '')),
        'phone_local', cp.phone_local,
        'country_code', cp.country_code,
        'auth_user_id', cp.auth_user_id
    )
    from public.community_profiles cp
    where cp.id = p_profile_id
$$;

create or replace function public.quata_chat_attachment_json(p_attachment_id bigint)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
    select jsonb_build_object(
        'id', a.id,
        'thread_id', a.thread_id,
        'message_id', a.message_id,
        'url', a.file_url,
        'thumb', a.thumb,
        'mime_type', a.mime_type,
        'name', a.file_name,
        'size', a.size_bytes,
        'ext', a.ext,
        'storage_bucket', a.storage_bucket,
        'storage_path', a.storage_path,
        'created_at', a.created_at
    )
    from public.chat_attachments a
    where a.id = p_attachment_id
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
        left join actor_state ap on true
        where m.thread_id = p_thread_id
          and m.deleted_at is null
          and m.sender_profile_id <> p_actor_profile_id
          and (
              ap.last_read_at is null
              or m.created_at > ap.last_read_at
              or (ap.last_read_message_id is not null and m.id > ap.last_read_message_id)
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
        'is_hidden', coalesce(actor_state.is_hidden, false),
        'is_deleted', coalesce(actor_state.is_deleted, false) or t.deleted_at is not null,
        'is_muted', actor_state.muted_at is not null,
        'is_pinned', actor_state.pinned_at is not null,
        'unread', coalesce(unread_state.unread_count, 0),
        'meta', jsonb_build_object('allowInvite', t.allow_invite),
        'permissions', jsonb_build_object(
            'isModerator', actor_state.role in ('owner', 'moderator'),
            'deleteAllowed', true,
            'canDeleteOwnMessages', true,
            'canDeleteAllMessages', actor_state.role in ('owner', 'moderator'),
            'canEditOwnMessages', true,
            'canEditAllMessages', actor_state.role in ('owner', 'moderator'),
            'canFavorite', true,
            'canMuteThread', true,
            'canEraseThread', true,
            'canClearThread', true,
            'canInvite', t.allow_invite or actor_state.role in ('owner', 'moderator'),
            'canLeave', true,
            'canUpload', true,
            'canReply', true
        ),
        'metadata', t.metadata
    )
    from t
    left join actor_state on true
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
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;

    select coalesce(jsonb_agg(public.quata_chat_message_json(q.id, v_actor) order by q.created_at, q.id), '[]'::jsonb)
    into v_messages
    from (
        select m.id, m.created_at
        from public.chat_messages m
        where m.thread_id = p_thread_id
          and not (m.id = any(coalesce(p_known_message_ids, array[]::bigint[])))
        order by m.created_at asc, m.id asc
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

    select coalesce(array_agg(q.id), array[]::bigint[])
    into v_thread_ids
    from (
        select t.id
        from public.chat_threads t
        join public.chat_participants p on p.thread_id = t.id
        where p.profile_id = v_actor
          and p.left_at is null
          and p.is_hidden = false
          and p.is_deleted = false
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
        where m.thread_id = any(v_thread_ids)
          and m.deleted_at is null
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
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;

    select max(id)
    into v_last_message_id
    from public.chat_messages
    where thread_id = p_thread_id;

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

    return jsonb_build_object('result', true, 'thread_id', p_thread_id);
end;
$$;

create or replace function public.quata_chat_check_new(
    p_actor_profile_id uuid,
    p_last_update_millis bigint default 0,
    p_visible_thread_ids bigint[] default '{}',
    p_thread_ids bigint[] default '{}'
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_since timestamptz;
    v_thread_ids bigint[];
    v_threads jsonb;
    v_messages jsonb;
    v_profiles jsonb;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);
    v_since := case
        when coalesce(p_last_update_millis, 0) > 0 then to_timestamp(p_last_update_millis / 1000.0)
        else to_timestamp(0)
    end;

    if coalesce(array_length(p_visible_thread_ids, 1), 0) > 0 then
        update public.chat_participants p
        set last_read_at = now(),
            last_read_message_id = (
                select max(m.id)
                from public.chat_messages m
                where m.thread_id = p.thread_id
            )
        where p.profile_id = v_actor
          and p.thread_id = any(p_visible_thread_ids);
    end if;

    select coalesce(array_agg(distinct t.id), array[]::bigint[])
    into v_thread_ids
    from public.chat_threads t
    join public.chat_participants p on p.thread_id = t.id
    where p.profile_id = v_actor
      and p.left_at is null
      and p.is_hidden = false
      and p.is_deleted = false
      and t.deleted_at is null
      and (
          t.updated_at >= v_since
          or t.id = any(coalesce(p_thread_ids, array[]::bigint[]))
          or exists (
              select 1
              from public.chat_messages m
              where m.thread_id = t.id
                and coalesce(m.updated_at, m.edited_at, m.created_at) >= v_since
          )
      );

    select coalesce(jsonb_agg(public.quata_chat_thread_json(t.id, v_actor) order by t.updated_at desc, t.id desc), '[]'::jsonb)
    into v_threads
    from public.chat_threads t
    where t.id = any(v_thread_ids);

    select coalesce(jsonb_agg(public.quata_chat_message_json(m.id, v_actor) order by m.created_at, m.id), '[]'::jsonb)
    into v_messages
    from public.chat_messages m
    where m.thread_id = any(v_thread_ids)
      and coalesce(m.updated_at, m.edited_at, m.created_at) >= v_since;

    select coalesce(jsonb_agg(distinct public.quata_chat_profile_json(p.profile_id)), '[]'::jsonb)
    into v_profiles
    from public.chat_participants p
    where p.thread_id = any(v_thread_ids)
      and p.left_at is null;

    return jsonb_build_object(
        'threads', coalesce(v_threads, '[]'::jsonb),
        'profiles', coalesce(v_profiles, '[]'::jsonb),
        'messages', coalesce(v_messages, '[]'::jsonb),
        'current_time_millis', public.quata_chat_epoch_millis(clock_timestamp())
    );
end;
$$;

create or replace function public.quata_chat_get_or_create_private_thread(
    p_actor_profile_id uuid,
    p_peer_profile_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_low uuid;
    v_high uuid;
    v_thread_id bigint;
    v_created_thread_id bigint;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if p_peer_profile_id is null or p_peer_profile_id = v_actor then
        raise exception 'peer profile is required' using errcode = '22023';
    end if;

    if not exists (select 1 from public.community_profiles where id = p_peer_profile_id) then
        raise exception 'peer profile does not exist' using errcode = '22023';
    end if;

    if v_actor < p_peer_profile_id then
        v_low := v_actor;
        v_high := p_peer_profile_id;
    else
        v_low := p_peer_profile_id;
        v_high := v_actor;
    end if;

    select thread_id
    into v_thread_id
    from public.chat_private_threads
    where profile_low_id = v_low
      and profile_high_id = v_high;

    if v_thread_id is null then
        insert into public.chat_threads(type, created_by_profile_id, unique_key)
        values ('private', v_actor, 'private:' || v_low::text || ':' || v_high::text)
        returning id into v_created_thread_id;

        begin
            insert into public.chat_private_threads(thread_id, profile_low_id, profile_high_id)
            values (v_created_thread_id, v_low, v_high);
            v_thread_id := v_created_thread_id;
        exception when unique_violation then
            select thread_id
            into v_thread_id
            from public.chat_private_threads
            where profile_low_id = v_low
              and profile_high_id = v_high;

            delete from public.chat_threads where id = v_created_thread_id;
        end;
    end if;

    insert into public.chat_participants(thread_id, profile_id, role)
    values (v_thread_id, v_actor, 'owner')
    on conflict (thread_id, profile_id)
    do update set left_at = null, is_hidden = false, is_deleted = false;

    insert into public.chat_participants(thread_id, profile_id, role)
    values (v_thread_id, p_peer_profile_id, 'member')
    on conflict (thread_id, profile_id)
    do update set left_at = null;

    insert into public.chat_events(thread_id, actor_profile_id, event_type, payload)
    values (v_thread_id, v_actor, 'private_thread_opened', jsonb_build_object('peer_profile_id', p_peer_profile_id));

    return public.quata_chat_get_thread(v_actor, v_thread_id);
end;
$$;

create or replace function public.quata_chat_start_thread(
    p_actor_profile_id uuid,
    p_recipient_profile_ids uuid[] default '{}',
    p_subject text default null,
    p_type text default 'group',
    p_message text default '',
    p_unique_key text default null,
    p_community_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_type text := coalesce(nullif(p_type, ''), 'group');
    v_recipient_ids uuid[];
    v_thread_id bigint;
    v_message_id bigint;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if v_type not in ('group', 'wall', 'sos') then
        v_type := 'group';
    end if;

    select coalesce(array_agg(distinct x), array[]::uuid[])
    into v_recipient_ids
    from unnest(coalesce(p_recipient_profile_ids, array[]::uuid[])) x
    where x is not null
      and x <> v_actor
      and exists (select 1 from public.community_profiles cp where cp.id = x);

    if coalesce(array_length(v_recipient_ids, 1), 0) = 0 then
        raise exception 'at least one recipient is required' using errcode = '22023';
    end if;

    if p_unique_key is not null then
        select t.id
        into v_thread_id
        from public.chat_threads t
        join public.chat_participants p on p.thread_id = t.id
        where t.unique_key = p_unique_key
          and p.profile_id = v_actor
          and p.left_at is null
        limit 1;

        if v_thread_id is not null then
            return public.quata_chat_get_thread(v_actor, v_thread_id);
        end if;
    end if;

    insert into public.chat_threads(
        type,
        subject,
        title,
        community_id,
        created_by_profile_id,
        unique_key,
        allow_invite
    )
    values (
        v_type,
        nullif(p_subject, ''),
        nullif(p_subject, ''),
        p_community_id,
        v_actor,
        p_unique_key,
        true
    )
    on conflict (unique_key) where unique_key is not null
    do update set updated_at = public.chat_threads.updated_at
    returning id into v_thread_id;

    insert into public.chat_participants(thread_id, profile_id, role)
    values (v_thread_id, v_actor, 'owner')
    on conflict (thread_id, profile_id)
    do update set left_at = null, is_hidden = false, is_deleted = false;

    insert into public.chat_participants(thread_id, profile_id, role)
    select v_thread_id, x, 'member'
    from unnest(v_recipient_ids) x
    on conflict (thread_id, profile_id)
    do update set left_at = null;

    if nullif(p_message, '') is not null then
        insert into public.chat_messages(thread_id, sender_profile_id, body)
        values (v_thread_id, v_actor, p_message)
        returning id into v_message_id;
    end if;

    insert into public.chat_events(thread_id, actor_profile_id, event_type, payload)
    values (
        v_thread_id,
        v_actor,
        'thread_started',
        jsonb_build_object(
            'type', v_type,
            'recipient_profile_ids', to_jsonb(v_recipient_ids),
            'message_id', v_message_id
        )
    );

    return public.quata_chat_get_thread(v_actor, v_thread_id)
        || jsonb_build_object('result', true, 'thread_id', v_thread_id, 'message_id', v_message_id);
end;
$$;

create or replace function public.quata_chat_register_attachment(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_file_url text,
    p_storage_bucket text default 'chat-attachments',
    p_storage_path text default null,
    p_mime_type text default 'application/octet-stream',
    p_name text default null,
    p_size_bytes bigint default null,
    p_ext text default null,
    p_thumb jsonb default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_attachment_id bigint;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;

    if nullif(p_file_url, '') is null then
        raise exception 'file_url is required' using errcode = '22023';
    end if;

    insert into public.chat_attachments(
        thread_id,
        uploaded_by_profile_id,
        storage_bucket,
        storage_path,
        file_url,
        mime_type,
        file_name,
        size_bytes,
        ext,
        thumb
    )
    values (
        p_thread_id,
        v_actor,
        p_storage_bucket,
        p_storage_path,
        p_file_url,
        p_mime_type,
        p_name,
        p_size_bytes,
        p_ext,
        p_thumb
    )
    returning id into v_attachment_id;

    return jsonb_build_object(
        'result', true,
        'id', v_attachment_id,
        'file', public.quata_chat_attachment_json(v_attachment_id)
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

create or replace function public.quata_chat_list_attachments(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_page integer default 1,
    p_per_page integer default 20,
    p_type text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_page integer := greatest(1, coalesce(p_page, 1));
    v_per_page integer := greatest(1, least(coalesce(p_per_page, 20), 100));
    v_total integer;
    v_files jsonb;
    v_counts jsonb;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;

    select count(*)
    into v_total
    from public.chat_attachments a
    where a.thread_id = p_thread_id
      and a.message_id is not null
      and (
          p_type is null
          or (p_type = 'photos' and a.mime_type like 'image/%')
          or (p_type = 'videos' and a.mime_type like 'video/%')
          or (p_type = 'audio' and a.mime_type like 'audio/%')
          or (p_type = 'files' and coalesce(a.mime_type, '') not like 'image/%'
              and coalesce(a.mime_type, '') not like 'video/%'
              and coalesce(a.mime_type, '') not like 'audio/%')
      );

    select coalesce(jsonb_agg(public.quata_chat_attachment_json(q.id) order by q.created_at desc, q.id desc), '[]'::jsonb)
    into v_files
    from (
        select a.id, a.created_at
        from public.chat_attachments a
        where a.thread_id = p_thread_id
          and a.message_id is not null
          and (
              p_type is null
              or (p_type = 'photos' and a.mime_type like 'image/%')
              or (p_type = 'videos' and a.mime_type like 'video/%')
              or (p_type = 'audio' and a.mime_type like 'audio/%')
              or (p_type = 'files' and coalesce(a.mime_type, '') not like 'image/%'
                  and coalesce(a.mime_type, '') not like 'video/%'
                  and coalesce(a.mime_type, '') not like 'audio/%')
          )
        order by a.created_at desc, a.id desc
        limit v_per_page
        offset (v_page - 1) * v_per_page
    ) q;

    select jsonb_build_object(
        'photos', count(*) filter (where mime_type like 'image/%'),
        'videos', count(*) filter (where mime_type like 'video/%'),
        'audio', count(*) filter (where mime_type like 'audio/%'),
        'files', count(*) filter (
            where coalesce(mime_type, '') not like 'image/%'
              and coalesce(mime_type, '') not like 'video/%'
              and coalesce(mime_type, '') not like 'audio/%'
        )
    )
    into v_counts
    from public.chat_attachments
    where thread_id = p_thread_id
      and message_id is not null;

    return jsonb_build_object(
        'files', coalesce(v_files, '[]'::jsonb),
        'hasMore', v_total > v_page * v_per_page,
        'page', v_page,
        'counts', v_counts,
        'activeType', p_type
    );
end;
$$;

create or replace function public.quata_chat_set_favorite(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_message_id bigint,
    p_favorite boolean default true
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

    if not exists (
        select 1 from public.chat_messages where id = p_message_id and thread_id = p_thread_id
    ) then
        raise exception 'message is not in this thread' using errcode = '22023';
    end if;

    if coalesce(p_favorite, true) then
        insert into public.chat_message_favorites(profile_id, message_id)
        values (v_actor, p_message_id)
        on conflict do nothing;
    else
        delete from public.chat_message_favorites
        where profile_id = v_actor
          and message_id = p_message_id;
    end if;

    return jsonb_build_object(
        'result', true,
        'message_id', p_message_id,
        'favorite', coalesce(p_favorite, true)
    );
end;
$$;

create or replace function public.quata_chat_get_favorites(
    p_actor_profile_id uuid,
    p_limit integer default 250
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_limit integer := greatest(1, least(coalesce(p_limit, 250), 500));
    v_messages jsonb;
    v_thread_ids bigint[];
    v_threads jsonb;
    v_profiles jsonb;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    select coalesce(jsonb_agg(public.quata_chat_message_json(q.id, v_actor) order by q.created_at desc, q.id desc), '[]'::jsonb),
           coalesce(array_agg(distinct q.thread_id), array[]::bigint[])
    into v_messages, v_thread_ids
    from (
        select m.id, m.thread_id, m.created_at
        from public.chat_message_favorites f
        join public.chat_messages m on m.id = f.message_id
        join public.chat_participants p on p.thread_id = m.thread_id and p.profile_id = v_actor
        where f.profile_id = v_actor
          and m.deleted_at is null
          and p.left_at is null
          and p.is_hidden = false
          and p.is_deleted = false
        order by m.created_at desc, m.id desc
        limit v_limit
    ) q;

    select coalesce(jsonb_agg(public.quata_chat_thread_json(tid, v_actor)), '[]'::jsonb)
    into v_threads
    from unnest(v_thread_ids) tid;

    select coalesce(jsonb_agg(distinct public.quata_chat_profile_json(p.profile_id)), '[]'::jsonb)
    into v_profiles
    from public.chat_participants p
    where p.thread_id = any(v_thread_ids)
      and p.left_at is null;

    return jsonb_build_object(
        'threads', coalesce(v_threads, '[]'::jsonb),
        'profiles', coalesce(v_profiles, '[]'::jsonb),
        'messages', coalesce(v_messages, '[]'::jsonb),
        'server_time_millis', public.quata_chat_epoch_millis(clock_timestamp())
    );
end;
$$;

create or replace function public.quata_chat_edit_message(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_message_id bigint,
    p_message text
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

    update public.chat_messages m
    set body = coalesce(p_message, ''),
        edited_at = now(),
        updated_at = now()
    where m.id = p_message_id
      and m.thread_id = p_thread_id
      and m.deleted_at is null
      and (
          m.sender_profile_id = v_actor
          or public.quata_chat_can_moderate(p_thread_id, v_actor)
      );

    if not found then
        raise exception 'message cannot be edited' using errcode = '42501';
    end if;

    insert into public.chat_events(thread_id, actor_profile_id, event_type, payload)
    values (p_thread_id, v_actor, 'message_edited', jsonb_build_object('message_id', p_message_id));

    return public.quata_chat_get_thread(v_actor, p_thread_id);
end;
$$;

create or replace function public.quata_chat_delete_messages(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_message_ids bigint[]
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

    update public.chat_messages m
    set deleted_at = now(),
        deleted_by_profile_id = v_actor,
        updated_at = now()
    where m.thread_id = p_thread_id
      and m.id = any(coalesce(p_message_ids, array[]::bigint[]))
      and m.deleted_at is null
      and (
          m.sender_profile_id = v_actor
          or public.quata_chat_can_moderate(p_thread_id, v_actor)
      );

    insert into public.chat_events(thread_id, actor_profile_id, event_type, payload)
    values (p_thread_id, v_actor, 'messages_deleted', jsonb_build_object('message_ids', to_jsonb(p_message_ids)));

    return public.quata_chat_get_thread(v_actor, p_thread_id);
end;
$$;

create or replace function public.quata_chat_forward_message(
    p_actor_profile_id uuid,
    p_message_id bigint,
    p_thread_ids bigint[]
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_source public.chat_messages%rowtype;
    v_target_thread_id bigint;
    v_new_message_id bigint;
    v_sent jsonb := '{}'::jsonb;
    v_errors text[] := '{}';
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    select *
    into v_source
    from public.chat_messages
    where id = p_message_id
      and deleted_at is null;

    if v_source.id is null then
        raise exception 'source message does not exist' using errcode = '22023';
    end if;

    if not public.quata_chat_is_thread_participant(v_source.thread_id, v_actor) then
        raise exception 'profile cannot read source message' using errcode = '42501';
    end if;

    foreach v_target_thread_id in array coalesce(p_thread_ids, array[]::bigint[])
    loop
        begin
            if not public.quata_chat_is_thread_participant(v_target_thread_id, v_actor) then
                v_errors := array_append(v_errors, 'not participant in thread ' || v_target_thread_id::text);
                continue;
            end if;

            insert into public.chat_messages(
                thread_id,
                sender_profile_id,
                body,
                forwarded_from_message_id,
                forwarded_from_profile_id
            )
            values (
                v_target_thread_id,
                v_actor,
                v_source.body,
                v_source.id,
                v_source.sender_profile_id
            )
            returning id into v_new_message_id;

            insert into public.chat_attachments(
                thread_id,
                message_id,
                uploaded_by_profile_id,
                storage_bucket,
                storage_path,
                file_url,
                thumb,
                mime_type,
                file_name,
                size_bytes,
                ext,
                attached_at
            )
            select
                v_target_thread_id,
                v_new_message_id,
                v_actor,
                a.storage_bucket,
                a.storage_path,
                a.file_url,
                a.thumb,
                a.mime_type,
                a.file_name,
                a.size_bytes,
                a.ext,
                now()
            from public.chat_attachments a
            where a.message_id = v_source.id;

            v_sent := v_sent || jsonb_build_object(v_target_thread_id::text, v_new_message_id);
        exception when others then
            v_errors := array_append(v_errors, sqlerrm);
        end;
    end loop;

    return jsonb_build_object(
        'result', v_sent <> '{}'::jsonb,
        'sent', v_sent,
        'errors', to_jsonb(v_errors)
    );
end;
$$;

create or replace function public.quata_chat_change_subject(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_subject text
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

    if not public.quata_chat_can_moderate(p_thread_id, v_actor) then
        raise exception 'profile cannot change subject' using errcode = '42501';
    end if;

    update public.chat_threads
    set subject = nullif(p_subject, ''),
        title = nullif(p_subject, '')
    where id = p_thread_id;

    insert into public.chat_events(thread_id, actor_profile_id, event_type, payload)
    values (p_thread_id, v_actor, 'subject_changed', jsonb_build_object('subject', p_subject));

    return jsonb_build_object('result', true, 'thread_id', p_thread_id);
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
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    update public.chat_participants
    set muted_at = case when coalesce(p_muted, false) then now() else null end
    where thread_id = p_thread_id
      and profile_id = v_actor
      and left_at is null;

    if not found then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;

    return jsonb_build_object('result', true, 'thread_id', p_thread_id, 'muted', coalesce(p_muted, false));
end;
$$;

create or replace function public.quata_chat_set_member_invites_enabled(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_enabled boolean
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

    if not public.quata_chat_can_moderate(p_thread_id, v_actor) then
        raise exception 'profile cannot change invite settings' using errcode = '42501';
    end if;

    update public.chat_threads
    set allow_invite = coalesce(p_enabled, true)
    where id = p_thread_id;

    return jsonb_build_object('result', true, 'thread_id', p_thread_id, 'allow_invite', coalesce(p_enabled, true));
end;
$$;

create or replace function public.quata_chat_add_participants(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_participant_profile_ids uuid[]
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_allow_invite boolean;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    select t.allow_invite or public.quata_chat_can_moderate(p_thread_id, v_actor)
    into v_allow_invite
    from public.chat_threads t
    where t.id = p_thread_id;

    if not public.quata_chat_is_thread_participant(p_thread_id, v_actor) or not coalesce(v_allow_invite, false) then
        raise exception 'profile cannot add participants' using errcode = '42501';
    end if;

    insert into public.chat_participants(thread_id, profile_id, role)
    select p_thread_id, x, 'member'
    from unnest(coalesce(p_participant_profile_ids, array[]::uuid[])) x
    where x is not null
      and x <> v_actor
      and exists (select 1 from public.community_profiles cp where cp.id = x)
    on conflict (thread_id, profile_id)
    do update set left_at = null, is_hidden = false, is_deleted = false;

    insert into public.chat_events(thread_id, actor_profile_id, event_type, payload)
    values (p_thread_id, v_actor, 'participants_added', jsonb_build_object('profile_ids', to_jsonb(p_participant_profile_ids)));

    return public.quata_chat_get_thread(v_actor, p_thread_id);
end;
$$;

create or replace function public.quata_chat_promote_moderator(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_profile_id uuid
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

    if not public.quata_chat_can_moderate(p_thread_id, v_actor) then
        raise exception 'profile cannot promote moderators' using errcode = '42501';
    end if;

    update public.chat_participants
    set role = 'moderator'
    where thread_id = p_thread_id
      and profile_id = p_profile_id
      and left_at is null;

    if not found then
        raise exception 'target participant does not exist' using errcode = '22023';
    end if;

    return public.quata_chat_get_thread(v_actor, p_thread_id);
end;
$$;

create or replace function public.quata_chat_remove_participant(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_profile_id uuid
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

    if not public.quata_chat_can_moderate(p_thread_id, v_actor) then
        raise exception 'profile cannot remove participants' using errcode = '42501';
    end if;

    update public.chat_participants
    set left_at = now(),
        is_hidden = true
    where thread_id = p_thread_id
      and profile_id = p_profile_id
      and role <> 'owner';

    return public.quata_chat_get_thread(v_actor, p_thread_id);
end;
$$;

create or replace function public.quata_chat_block_participant(
    p_actor_profile_id uuid,
    p_thread_id bigint,
    p_profile_id uuid
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

    insert into public.chat_profile_blocks(thread_id, blocker_profile_id, blocked_profile_id)
    values (p_thread_id, v_actor, p_profile_id)
    on conflict do nothing;

    return jsonb_build_object('result', true, 'thread_id', p_thread_id, 'blocked_profile_id', p_profile_id);
end;
$$;

create or replace function public.quata_chat_leave_thread(
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

    update public.chat_participants
    set left_at = now(),
        is_hidden = true
    where thread_id = p_thread_id
      and profile_id = v_actor;

    if not found then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;

    insert into public.chat_events(thread_id, actor_profile_id, event_type)
    values (p_thread_id, v_actor, 'participant_left');

    return jsonb_build_object('result', true, 'thread_id', p_thread_id);
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

    update public.chat_participants
    set is_hidden = true,
        is_deleted = true
    where thread_id = p_thread_id
      and profile_id = v_actor
      and left_at is null;

    if not found then
        raise exception 'profile is not a participant of this thread' using errcode = '42501';
    end if;

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

    update public.chat_participants
    set is_hidden = false,
        is_deleted = false,
        left_at = null
    where thread_id = p_thread_id
      and profile_id = v_actor;

    if not found then
        raise exception 'profile is not related to this thread' using errcode = '42501';
    end if;

    return public.quata_chat_get_thread(v_actor, p_thread_id);
end;
$$;

create or replace function public.quata_chat_send_sos(
    p_actor_profile_id uuid,
    p_contact_profile_ids uuid[] default '{}',
    p_message text default '',
    p_lat double precision default null,
    p_lng double precision default null,
    p_accuracy double precision default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_contact_ids uuid[];
    v_all_ids uuid[];
    v_key text;
    v_thread_id bigint;
    v_message_id bigint;
    v_event_id uuid;
    v_start jsonb;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    if nullif(coalesce(p_message, ''), '') is null then
        raise exception 'SOS message is required' using errcode = '22023';
    end if;

    select coalesce(array_agg(distinct x), array[]::uuid[])
    into v_contact_ids
    from unnest(coalesce(p_contact_profile_ids, array[]::uuid[])) x
    where x is not null
      and x <> v_actor
      and exists (select 1 from public.community_profiles cp where cp.id = x);

    if coalesce(array_length(v_contact_ids, 1), 0) = 0 then
        select coalesce(array_agg(q.contact_profile_id order by q.position, q.created_at), array[]::uuid[])
        into v_contact_ids
        from (
            select ec.contact_profile_id, ec.position, ec.created_at
            from public.community_emergency_contacts ec
            where ec.profile_id = v_actor
              and ec.contact_profile_id <> v_actor
            order by ec.position, ec.created_at
            limit 5
        ) q;
    end if;

    if coalesce(array_length(v_contact_ids, 1), 0) = 0 then
        raise exception 'no SOS contacts' using errcode = '22023';
    end if;

    select array_agg(x order by x::text)
    into v_all_ids
    from (
        select distinct x
        from unnest(array_append(v_contact_ids, v_actor)) x
    ) s;

    select string_agg(x::text, '-' order by x::text)
    into v_key
    from unnest(v_all_ids) x;

    select t.id
    into v_thread_id
    from public.chat_threads t
    join public.chat_participants p on p.thread_id = t.id and p.profile_id = v_actor
    where t.unique_key = 'quata-sos:' || v_key
    limit 1;

    if v_thread_id is null then
        v_start := public.quata_chat_start_thread(
            v_actor,
            v_contact_ids,
            'SOS',
            'sos',
            '',
            'quata-sos:' || v_key,
            null
        );
        v_thread_id := (v_start->>'thread_id')::bigint;
    else
        perform public.quata_chat_add_participants(v_actor, v_thread_id, v_contact_ids);
        perform public.quata_chat_restore_thread(v_actor, v_thread_id);
    end if;

    select (public.quata_chat_send_message(v_actor, v_thread_id, p_message)->>'message_id')::bigint
    into v_message_id;

    insert into public.chat_sos_events(
        thread_id,
        profile_id,
        message_id,
        message,
        latitude,
        longitude,
        accuracy_m,
        sent_count
    )
    values (
        v_thread_id,
        v_actor,
        v_message_id,
        p_message,
        p_lat,
        p_lng,
        p_accuracy,
        coalesce(array_length(v_contact_ids, 1), 0)
    )
    returning id into v_event_id;

    insert into public.chat_sos_recipients(sos_event_id, recipient_profile_id, delivered_thread_id)
    select v_event_id, x, v_thread_id
    from unnest(v_contact_ids) x
    on conflict do nothing;

    return jsonb_build_object(
        'sent', coalesce(array_length(v_contact_ids, 1), 0),
        'errors', '[]'::jsonb,
        'mode', 'supabase',
        'self_send_blocked', true,
        'only_saved_emergency_contacts', coalesce(array_length(p_contact_profile_ids, 1), 0) = 0,
        'thread_id', v_thread_id,
        'message_id', v_message_id,
        'sos_event_id', v_event_id
    );
end;
$$;

grant execute on function public.quata_chat_epoch_millis(timestamptz) to anon, authenticated;
grant execute on function public.quata_chat_profile_json(uuid) to anon, authenticated;
grant execute on function public.quata_chat_attachment_json(bigint) to anon, authenticated;
grant execute on function public.quata_chat_message_json(bigint, uuid) to anon, authenticated;
grant execute on function public.quata_chat_thread_json(bigint, uuid) to anon, authenticated;
grant execute on function public.quata_chat_get_thread(uuid, bigint, bigint[], integer) to anon, authenticated;
grant execute on function public.quata_chat_get_inbox(uuid, integer) to anon, authenticated;
grant execute on function public.quata_chat_mark_thread_read(uuid, bigint) to anon, authenticated;
grant execute on function public.quata_chat_check_new(uuid, bigint, bigint[], bigint[]) to anon, authenticated;
grant execute on function public.quata_chat_get_or_create_private_thread(uuid, uuid) to anon, authenticated;
grant execute on function public.quata_chat_start_thread(uuid, uuid[], text, text, text, text, uuid) to anon, authenticated;
grant execute on function public.quata_chat_register_attachment(uuid, bigint, text, text, text, text, text, bigint, text, jsonb) to anon, authenticated;
grant execute on function public.quata_chat_send_message(uuid, bigint, text, bigint[], bigint, text) to anon, authenticated;
grant execute on function public.quata_chat_send_files(uuid, bigint, bigint[], text) to anon, authenticated;
grant execute on function public.quata_chat_list_attachments(uuid, bigint, integer, integer, text) to anon, authenticated;
grant execute on function public.quata_chat_set_favorite(uuid, bigint, bigint, boolean) to anon, authenticated;
grant execute on function public.quata_chat_get_favorites(uuid, integer) to anon, authenticated;
grant execute on function public.quata_chat_edit_message(uuid, bigint, bigint, text) to anon, authenticated;
grant execute on function public.quata_chat_delete_messages(uuid, bigint, bigint[]) to anon, authenticated;
grant execute on function public.quata_chat_forward_message(uuid, bigint, bigint[]) to anon, authenticated;
grant execute on function public.quata_chat_change_subject(uuid, bigint, text) to anon, authenticated;
grant execute on function public.quata_chat_set_muted(uuid, bigint, boolean) to anon, authenticated;
grant execute on function public.quata_chat_set_member_invites_enabled(uuid, bigint, boolean) to anon, authenticated;
grant execute on function public.quata_chat_add_participants(uuid, bigint, uuid[]) to anon, authenticated;
grant execute on function public.quata_chat_promote_moderator(uuid, bigint, uuid) to anon, authenticated;
grant execute on function public.quata_chat_remove_participant(uuid, bigint, uuid) to anon, authenticated;
grant execute on function public.quata_chat_block_participant(uuid, bigint, uuid) to anon, authenticated;
grant execute on function public.quata_chat_leave_thread(uuid, bigint) to anon, authenticated;
grant execute on function public.quata_chat_delete_thread(uuid, bigint) to anon, authenticated;
grant execute on function public.quata_chat_restore_thread(uuid, bigint) to anon, authenticated;
grant execute on function public.quata_chat_send_sos(uuid, uuid[], text, double precision, double precision, double precision) to anon, authenticated;
