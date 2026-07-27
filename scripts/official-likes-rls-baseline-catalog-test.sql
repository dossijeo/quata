\set ON_ERROR_STOP on

do $$
begin
    if (select relrowsecurity from pg_class where oid = 'public.official_post_likes'::regclass) then
        raise exception 'baseline_catalog_rls_enabled';
    end if;
    if exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'official_post_likes') then
        raise exception 'baseline_catalog_policy_remains';
    end if;
    if not (select prosecdef from pg_proc where oid = 'public.quata_guard_official_post_likes()'::regprocedure) then
        raise exception 'baseline_catalog_guard_not_security_definer';
    end if;
    if (select md5(prosrc) from pg_proc where oid = 'public.quata_guard_official_post_likes()'::regprocedure)
            <> 'a2248d523b9a3386702018eec65422a4'
       or (select md5(pg_get_functiondef(oid)) from pg_proc where oid = 'public.quata_guard_official_post_likes()'::regprocedure)
            <> 'a7a42ed79f6f245516ebf9b15aa304c3' then
        raise exception 'baseline_catalog_guard_source_anchor_mismatch';
    end if;
    if not has_function_privilege('public', 'public.quata_guard_official_post_likes()', 'execute')
       or not has_function_privilege('anon', 'public.quata_guard_official_post_likes()', 'execute')
       or not has_function_privilege('authenticated', 'public.quata_guard_official_post_likes()', 'execute')
       or not has_function_privilege('service_role', 'public.quata_guard_official_post_likes()', 'execute')
       or (select coalesce(proacl::text, '') from pg_proc where oid = 'public.quata_guard_official_post_likes()'::regprocedure)
            <> '{=X/postgres,postgres=X/postgres,anon=X/postgres,authenticated=X/postgres,service_role=X/postgres}' then
        raise exception 'baseline_catalog_guard_acl_changed';
    end if;
    if to_regprocedure('public.quata_official_like_delete_allowed(uuid)') is not null then
        raise exception 'baseline_catalog_delete_helper_remains';
    end if;
    if not has_table_privilege('anon', 'public.official_post_likes', 'select')
       or has_table_privilege('anon', 'public.official_post_likes', 'insert')
       or has_table_privilege('anon', 'public.official_post_likes', 'delete') then
        raise exception 'baseline_catalog_anon_grants_changed';
    end if;
    if not has_table_privilege('authenticated', 'public.official_post_likes', 'select')
       or not has_table_privilege('authenticated', 'public.official_post_likes', 'insert')
       or not has_table_privilege('authenticated', 'public.official_post_likes', 'delete')
       or has_table_privilege('authenticated', 'public.official_post_likes', 'update') then
        raise exception 'baseline_catalog_authenticated_grants_changed';
    end if;
    if not exists (select 1 from public.official_post_likes where id = '40000000-0000-4000-8000-000000000009') then
        raise exception 'baseline_catalog_existing_like_lost';
    end if;
end;
$$;

\echo 'Official likes baseline catalog is exact and existing data is intact.'
