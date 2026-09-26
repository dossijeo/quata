-- Serialize get-or-create for one unordered profile pair before either table is
-- inspected. The earlier unique-violation handler covered chat_private_threads,
-- but simultaneous callers could collide first on chat_threads.unique_key.
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

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('quata-private:' || v_low::text || ':' || v_high::text, 0)
    );

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

