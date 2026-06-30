-- Paginated directory for starting private chat conversations.

create or replace function public.quata_chat_search_conversation_candidates(
    p_actor_profile_id uuid,
    p_query text default '',
    p_limit integer default 30,
    p_offset integer default 0
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_actor uuid;
    v_actor_neighborhood text;
    v_actor_neighborhood_key text;
    v_query text;
    v_limit integer;
    v_offset integer;
    v_items jsonb;
    v_total integer;
    v_page_count integer;
begin
    v_actor := public.quata_chat_actor_profile_id(p_actor_profile_id);
    v_query := lower(trim(coalesce(p_query, '')));
    v_limit := least(greatest(coalesce(p_limit, 30), 1), 50);
    v_offset := greatest(coalesce(p_offset, 0), 0);

    select coalesce(nullif(cp.neighborhood, ''), nullif(cp.barrio, ''), nullif(cp.barrio_normalized, ''))
    into v_actor_neighborhood
    from public.community_profiles cp
    where cp.id = v_actor;

    v_actor_neighborhood_key := public.quata_chat_community_key(v_actor_neighborhood);

    with private_contacts as (
        select
            case
                when cpt.profile_low_id = v_actor then cpt.profile_high_id
                else cpt.profile_low_id
            end as profile_id,
            cpt.thread_id
        from public.chat_private_threads cpt
        where cpt.profile_low_id = v_actor
           or cpt.profile_high_id = v_actor
    ),
    candidates as (
        select
            cp.id as profile_id,
            coalesce(nullif(cp.display_name, ''), nullif(cp.nombre, ''), 'Usuario') as display_name,
            coalesce(nullif(cp.neighborhood, ''), nullif(cp.barrio, ''), nullif(cp.barrio_normalized, ''), '') as neighborhood,
            public.quata_chat_community_key(coalesce(nullif(cp.neighborhood, ''), nullif(cp.barrio, ''), nullif(cp.barrio_normalized, ''))) as neighborhood_key,
            coalesce(nullif(cp.phone_e164, ''), nullif(cp.phone, ''), nullif(cp.phone_local, ''), nullif(cp.telefono, ''), '') as phone,
            cp.avatar_url,
            pc.thread_id as existing_thread_id,
            exists (
                select 1
                from public.community_profile_follows f
                where f.follower_profile_id = v_actor
                  and f.followed_profile_id = cp.id
            ) as is_following,
            exists (
                select 1
                from public.community_profile_follows f
                where f.follower_profile_id = cp.id
                  and f.followed_profile_id = v_actor
            ) as is_follower
        from public.community_profiles cp
        left join private_contacts pc on pc.profile_id = cp.id
        where cp.id <> v_actor
          and (
              v_query = ''
              or lower(concat_ws(
                    ' ',
                    cp.display_name,
                    cp.nombre,
                    cp.neighborhood,
                    cp.barrio,
                    cp.barrio_normalized,
                    cp.phone,
                    cp.phone_local,
                    cp.phone_e164,
                    cp.country_code,
                    cp.telefono,
                    concat(coalesce(cp.country_code, ''), coalesce(cp.phone_local, '')),
                    concat(coalesce(cp.code, ''), coalesce(cp.telefono, ''))
                )) like ('%' || v_query || '%')
          )
    ),
    classified as (
        select
            c.*,
            case
                when c.existing_thread_id is not null then 'contacts'
                when c.is_following then 'following'
                when c.is_follower then 'followers'
                when c.neighborhood_key <> '' and c.neighborhood_key = v_actor_neighborhood_key then 'neighborhood'
                else 'other'
            end as section_key,
            case
                when c.existing_thread_id is not null then 10
                when c.is_following then 20
                when c.is_follower then 30
                when c.neighborhood_key <> '' and c.neighborhood_key = v_actor_neighborhood_key then 40
                else 50
            end as section_rank
        from candidates c
    ),
    ordered as (
        select *
        from classified c
        order by
            c.section_rank,
            case when c.section_key = 'other' then lower(c.neighborhood) else '' end,
            lower(c.display_name),
            c.profile_id
    ),
    counted as (
        select count(*)::int as total_count
        from ordered
    ),
    paged as (
        select *
        from ordered
        limit v_limit
        offset v_offset
    )
    select
        coalesce(
            jsonb_agg(
                jsonb_build_object(
                    'profile_id', p.profile_id,
                    'display_name', p.display_name,
                    'neighborhood', p.neighborhood,
                    'phone', '',
                    'avatar_url', p.avatar_url,
                    'section_key', p.section_key,
                    'neighborhood_group', p.neighborhood,
                    'existing_thread_id', p.existing_thread_id
                )
                order by
                    p.section_rank,
                    case when p.section_key = 'other' then lower(p.neighborhood) else '' end,
                    lower(p.display_name),
                    p.profile_id
            ),
            '[]'::jsonb
        ),
        (select total_count from counted),
        count(*)::int
    into v_items, v_total, v_page_count
    from paged p;

    return jsonb_build_object(
        'items', coalesce(v_items, '[]'::jsonb),
        'has_more', coalesce(v_total, 0) > v_offset + v_page_count,
        'next_offset', v_offset + v_page_count,
        'total', coalesce(v_total, 0),
        'actor_neighborhood', coalesce(v_actor_neighborhood, '')
    );
end;
$$;

grant execute on function public.quata_chat_search_conversation_candidates(uuid, text, integer, integer) to anon, authenticated;
