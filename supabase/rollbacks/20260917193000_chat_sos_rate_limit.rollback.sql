-- Restore the pre-cooldown SOS RPC definition.
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

grant execute on function public.quata_chat_send_sos(
    uuid, uuid[], text, double precision, double precision, double precision
) to anon, authenticated;
