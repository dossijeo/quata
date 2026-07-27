\set ON_ERROR_STOP on

do $$
begin
    if not exists (select 1 from public.official_post_likes where id = '40000000-0000-4000-8000-000000000009') then
        raise exception 'drift_rollback_changed_existing_like';
    end if;
    if not (select relrowsecurity from pg_class where oid = 'public.official_post_likes'::regclass) then
        raise exception 'drift_rollback_changed_rls';
    end if;
    if (select with_check from pg_policies where schemaname = 'public'
        and tablename = 'official_post_likes'
        and policyname = 'official_post_likes_authenticated_insert_own') <> 'true' then
        raise exception 'drift_rollback_changed_same_name_policy';
    end if;
end;
$$;

\echo 'Same-name drift survived rejected rollback unchanged, including preserved row.'
