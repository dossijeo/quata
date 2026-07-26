\set ON_ERROR_STOP on

\if :{?EXPECTED_ADMIN_SHA256}
\else
    \echo 'EXPECTED_ADMIN_SHA256 is required; refusing rollout preflight.'
    \quit 4
\endif

\if :{?EXPECTED_OFFICIAL_SHA256}
\else
    \echo 'EXPECTED_OFFICIAL_SHA256 is required; refusing rollout preflight.'
    \quit 4
\endif

begin read only;

select set_config(
    'quata.preflight.expected_admin_sha256',
    :'EXPECTED_ADMIN_SHA256',
    true
) as configured_admin_fingerprint \gset
select set_config(
    'quata.preflight.expected_official_sha256',
    :'EXPECTED_OFFICIAL_SHA256',
    true
) as configured_official_fingerprint \gset

do $$
declare
    v_anomalies bigint;
    v_admin_sha256 text;
    v_official_sha256 text;
begin
    -- One Auth UUID must resolve to exactly one profile through the legacy
    -- (id OR auth_user_id) mapping.
    select count(*) into v_anomalies
    from (
        select mapped_auth_uid
        from (
            select cp.id as mapped_auth_uid, cp.id as profile_id
            from public.community_profiles cp
            union all
            select cp.auth_user_id, cp.id
            from public.community_profiles cp
            where cp.auth_user_id is not null
        ) mappings
        group by mapped_auth_uid
        having count(distinct profile_id) > 1
    ) collisions;
    if v_anomalies <> 0 then
        raise exception 'Preflight failed: ambiguous id/auth_user_id mappings (%)',
            v_anomalies using errcode = '23505';
    end if;

    -- Cover every representation used by Android/Auth bridge, removing
    -- formatting before comparing. Local-only keys are conservative because
    -- legacy login/recovery queries phone_local without a country predicate.
    select count(*) into v_anomalies
    from (
        select identity_key
        from (
            select cp.id,
                   'global:' || regexp_replace(cp.phone_e164, '\D', '', 'g')
                       as identity_key
            from public.community_profiles cp
            where nullif(regexp_replace(cp.phone_e164, '\D', '', 'g'), '') is not null
            union all
            select cp.id,
                   'global:' || regexp_replace(cp.phone, '\D', '', 'g')
            from public.community_profiles cp
            where nullif(regexp_replace(cp.phone, '\D', '', 'g'), '') is not null
            union all
            select cp.id,
                   'global:' || regexp_replace(
                       coalesce(cp.country_code, '') || coalesce(cp.phone_local, ''),
                       '\D', '', 'g'
                   )
            from public.community_profiles cp
            where nullif(regexp_replace(cp.country_code, '\D', '', 'g'), '') is not null
              and nullif(regexp_replace(cp.phone_local, '\D', '', 'g'), '') is not null
            union all
            select cp.id,
                   'global:' || regexp_replace(
                       coalesce(cp.code, '') || coalesce(cp.telefono, ''),
                       '\D', '', 'g'
                   )
            from public.community_profiles cp
            where nullif(regexp_replace(cp.code, '\D', '', 'g'), '') is not null
              and nullif(regexp_replace(cp.telefono, '\D', '', 'g'), '') is not null
            union all
            select cp.id,
                   'local:' || regexp_replace(cp.phone_normalized, '\D', '', 'g')
            from public.community_profiles cp
            where nullif(regexp_replace(cp.phone_normalized, '\D', '', 'g'), '') is not null
            union all
            select cp.id,
                   'local:' || regexp_replace(cp.phone_local, '\D', '', 'g')
            from public.community_profiles cp
            where nullif(regexp_replace(cp.phone_local, '\D', '', 'g'), '') is not null
            union all
            select cp.id,
                   'local:' || regexp_replace(cp.telefono, '\D', '', 'g')
            from public.community_profiles cp
            where nullif(regexp_replace(cp.telefono, '\D', '', 'g'), '') is not null
        ) identity_keys
        group by identity_key
        having count(distinct id) > 1
    ) duplicated_phone_identity;
    if v_anomalies <> 0 then
        raise exception 'Preflight failed: duplicate normalized phone identities (%)',
            v_anomalies using errcode = '23505';
    end if;

    if to_regclass('public.community_profile_follows') is null then
        raise exception 'Preflight failed: community_profile_follows is missing'
            using errcode = '42P01';
    end if;

    select count(*) into v_anomalies
    from public.community_profiles
    where followers_count < 0
       or following_count < 0
       or (
           account_status = 'active'
           and (
               deactivated_at is not null
               or deactivated_auth_user_id is not null
           )
       )
       or (
           account_status = 'deactivated'
           and deactivated_at is null
       );
    if v_anomalies <> 0 then
        raise exception 'Preflight failed: lifecycle/negative counter anomalies (%)',
            v_anomalies using errcode = '23514';
    end if;

    select count(*) into v_anomalies
    from public.community_profiles cp
    where cp.followers_count <> (
              select count(*)::integer
              from public.community_profile_follows f
              where f.followed_profile_id = cp.id
          )
       or cp.following_count <> (
              select count(*)::integer
              from public.community_profile_follows f
              where f.follower_profile_id = cp.id
          );
    if v_anomalies <> 0 then
        raise exception 'Preflight failed: counters do not match follow edges (%)',
            v_anomalies using errcode = '23514';
    end if;

    select encode(
        digest(
            coalesce(string_agg(id::text, ',' order by id), ''),
            'sha256'
        ),
        'hex'
    ) into v_admin_sha256
    from public.community_profiles
    where is_admin;

    select encode(
        digest(
            coalesce(string_agg(id::text, ',' order by id), ''),
            'sha256'
        ),
        'hex'
    ) into v_official_sha256
    from public.community_profiles
    where is_official;

    if v_admin_sha256 <> current_setting(
        'quata.preflight.expected_admin_sha256'
    ) then
        raise exception 'Preflight failed: administrator inventory mismatch'
            using errcode = '42501';
    end if;

    if v_official_sha256 <> current_setting(
        'quata.preflight.expected_official_sha256'
    ) then
        raise exception 'Preflight failed: official inventory mismatch'
            using errcode = '42501';
    end if;

    raise notice 'COMMUNITY_PROFILES_ROLLOUT_PREFLIGHT_OK';
end;
$$;

rollback;
