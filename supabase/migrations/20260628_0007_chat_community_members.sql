-- Community chats should be shared by every profile that belongs to the wall/neighborhood.

create or replace function public.quata_chat_community_key(p_value text)
returns text
language sql
immutable
as $$
    select nullif(
        regexp_replace(
            translate(
                lower(coalesce(p_value, '')),
                'áàäâãåéèëêíìïîóòöôõúùüûñç',
                'aaaaaaeeeeiiiiooooouuuunc'
            ),
            '[^a-z0-9]+',
            '',
            'g'
        ),
        ''
    )
$$;

create or replace function public.quata_chat_open_community_thread(
    p_actor_profile_id uuid,
    p_community_id uuid,
    p_title text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_thread_id bigint;
    v_unique_key text := 'quata-community:' || p_community_id::text;
    v_wall public.community_walls%rowtype;
    v_title text;
    v_community_key text;
    v_title_key text;
    v_created boolean := false;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);

    select *
    into v_wall
    from public.community_walls
    where id = p_community_id;

    v_title := coalesce(nullif(p_title, ''), nullif(v_wall.name, ''), 'Comunidad');
    v_community_key := coalesce(
        public.quata_chat_community_key(v_wall.normalized_name),
        public.quata_chat_community_key(v_wall.name),
        public.quata_chat_community_key(v_wall.slug),
        public.quata_chat_community_key(v_title)
    );
    v_title_key := public.quata_chat_community_key(v_title);

    select id
    into v_thread_id
    from public.chat_threads
    where unique_key = v_unique_key
      and deleted_at is null
    limit 1;

    if v_thread_id is null then
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
            'wall',
            v_title,
            v_title,
            p_community_id,
            v_actor,
            v_unique_key,
            true
        )
        returning id into v_thread_id;
        v_created := true;
    end if;

    insert into public.chat_participants(thread_id, profile_id, role)
    select
        v_thread_id,
        member_id,
        case when member_id = v_actor and v_created then 'owner' else 'member' end
    from (
        select v_actor as member_id
        union
        select cp.id
        from public.community_profiles cp
        where public.quata_chat_community_key(cp.neighborhood) in (v_community_key, v_title_key)
           or public.quata_chat_community_key(cp.barrio) in (v_community_key, v_title_key)
           or public.quata_chat_community_key(cp.barrio_normalized) in (v_community_key, v_title_key)
    ) members
    where member_id is not null
    on conflict (thread_id, profile_id)
    do update set
        left_at = null,
        is_hidden = false,
        is_deleted = false,
        role = case
            when excluded.role = 'owner' and public.chat_participants.role = 'member' then 'owner'
            else public.chat_participants.role
        end;

    insert into public.chat_events(thread_id, actor_profile_id, event_type, payload)
    values (
        v_thread_id,
        v_actor,
        'community_thread_opened',
        jsonb_build_object(
            'community_id', p_community_id,
            'community_key', v_community_key,
            'members_synced', true
        )
    );

    return public.quata_chat_get_thread(v_actor, v_thread_id)
        || jsonb_build_object('result', true, 'thread_id', v_thread_id);
end;
$$;

grant execute on function public.quata_chat_community_key(text) to anon, authenticated;
grant execute on function public.quata_chat_open_community_thread(uuid, uuid, text) to anon, authenticated;

insert into public.chat_participants(thread_id, profile_id, role)
select t.id, t.created_by_profile_id, 'owner'
from public.chat_threads t
where t.type = 'wall'
  and t.deleted_at is null
  and t.created_by_profile_id is not null
on conflict (thread_id, profile_id)
do update set
    left_at = null,
    is_hidden = false,
    is_deleted = false,
    role = case
        when public.chat_participants.role = 'member' then 'owner'
        else public.chat_participants.role
    end;

insert into public.chat_participants(thread_id, profile_id, role)
select distinct t.id, cp.id, 'member'
from public.chat_threads t
join public.community_walls w on w.id = t.community_id
join public.community_profiles cp on (
    public.quata_chat_community_key(cp.neighborhood) in (
        public.quata_chat_community_key(w.normalized_name),
        public.quata_chat_community_key(w.name),
        public.quata_chat_community_key(w.slug),
        public.quata_chat_community_key(t.subject),
        public.quata_chat_community_key(t.title)
    )
    or public.quata_chat_community_key(cp.barrio) in (
        public.quata_chat_community_key(w.normalized_name),
        public.quata_chat_community_key(w.name),
        public.quata_chat_community_key(w.slug),
        public.quata_chat_community_key(t.subject),
        public.quata_chat_community_key(t.title)
    )
    or public.quata_chat_community_key(cp.barrio_normalized) in (
        public.quata_chat_community_key(w.normalized_name),
        public.quata_chat_community_key(w.name),
        public.quata_chat_community_key(w.slug),
        public.quata_chat_community_key(t.subject),
        public.quata_chat_community_key(t.title)
    )
)
where t.type = 'wall'
  and t.deleted_at is null
on conflict (thread_id, profile_id)
do update set
    left_at = null,
    is_hidden = false,
    is_deleted = false;
