\set ON_ERROR_STOP on

do $$
begin
    if not (select relrowsecurity from pg_class where oid = 'public.official_post_likes'::regclass) then
        raise exception 'secured_catalog_rls_disabled';
    end if;
    if (select count(*) from pg_policies where schemaname = 'public' and tablename = 'official_post_likes') <> 3 then
        raise exception 'secured_catalog_policy_count_changed';
    end if;
    if (select prosecdef from pg_proc where oid = 'public.quata_guard_official_post_likes()'::regprocedure) then
        raise exception 'secured_catalog_guard_not_security_invoker';
    end if;
    if to_regprocedure('public.quata_official_like_delete_allowed(uuid)') is null then
        raise exception 'secured_catalog_delete_helper_missing';
    end if;
    if not exists (select 1 from public.official_post_likes where id = '40000000-0000-4000-8000-000000000009') then
        raise exception 'secured_catalog_existing_like_lost';
    end if;
end;
$$;

\echo 'Official likes secured catalog and preserved data verified.'
