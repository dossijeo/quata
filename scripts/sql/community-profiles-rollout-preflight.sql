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

    select count(*) into v_anomalies
    from (
        select country_code, phone_local
        from public.community_profiles
        where nullif(trim(country_code), '') is not null
          and nullif(trim(phone_local), '') is not null
        group by country_code, phone_local
        having count(*) > 1
    ) duplicated_phone_identity;
    if v_anomalies <> 0 then
        raise exception 'Preflight failed: duplicate normalized phone identities (%)',
            v_anomalies using errcode = '23505';
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
        raise exception 'Preflight failed: lifecycle/counter anomalies (%)',
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
