-- Emergency rollback for 20260726171002_official_post_likes_actor_guard.sql.
--
-- This script intentionally restores the catalog captured immediately before
-- that migration: RLS disabled, no policies on this table, the guard function
-- SECURITY DEFINER with its captured PUBLIC/API ACL, no delete-policy helper,
-- and the original anon/authenticated table grants. It never deletes or updates
-- rows.
--
-- It is deliberately fail-closed on schema drift. Do not use it as a general
-- policy reset: if another release has changed this table, take a fresh backup
-- and prepare a dedicated rollback instead.

begin;

do $$
declare
    v_policy_fingerprint text;
    v_guard_acl_fingerprint text;
    v_guard_body_fingerprint text;
    v_guard_definition_fingerprint text;
    v_helper_fingerprint text;
    v_helper_acl_fingerprint text;
    v_trigger_fingerprint text;
    v_trigger_enabled "char";
    v_table_acl_fingerprint text;
begin
    -- Fingerprints are calculated from the exact release DDL on PostgreSQL 17.
    -- This intentionally rejects a same-name policy/function edited by a later
    -- release instead of dropping it as though it were RLS-002's state.
    select md5(string_agg(
        p.policyname || '|' || p.cmd || '|' || coalesce(array_to_string(p.roles, ','), '') || '|'
        || coalesce(p.qual, '') || '|' || coalesce(p.with_check, ''), E'\n' order by p.policyname))
    into v_policy_fingerprint
    from pg_policies p
    where p.schemaname = 'public' and p.tablename = 'official_post_likes';
    if to_regprocedure('public.quata_guard_official_post_likes()') is null
       or to_regprocedure('public.quata_official_like_delete_allowed(uuid)') is null then
        raise exception 'official_post_likes_rollback_refused:release_function_missing';
    end if;
    select md5(coalesce(string_agg(
        coalesce(pg_get_userbyid(a.grantee), 'PUBLIC') || '|' ||
        coalesce(pg_get_userbyid(a.grantor), 'PUBLIC') || '|' ||
        a.privilege_type || '|' || a.is_grantable::text,
        E'\n' order by coalesce(pg_get_userbyid(a.grantee), 'PUBLIC'),
                         coalesce(pg_get_userbyid(a.grantor), 'PUBLIC'),
                         a.privilege_type, a.is_grantable), ''))
    into v_guard_acl_fingerprint
    from pg_proc p
    cross join lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a
    where p.oid = 'public.quata_guard_official_post_likes()'::regprocedure;
    select md5(pg_get_functiondef('public.quata_official_like_delete_allowed(uuid)'::regprocedure)),
           md5(coalesce(string_agg(
               coalesce(pg_get_userbyid(a.grantee), 'PUBLIC') || '|' ||
               coalesce(pg_get_userbyid(a.grantor), 'PUBLIC') || '|' ||
               a.privilege_type || '|' || a.is_grantable::text,
               E'\n' order by coalesce(pg_get_userbyid(a.grantee), 'PUBLIC'),
                                coalesce(pg_get_userbyid(a.grantor), 'PUBLIC'),
                                a.privilege_type, a.is_grantable), ''))
    into v_helper_fingerprint, v_helper_acl_fingerprint
    from pg_proc p
    cross join lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a
    where p.oid = 'public.quata_official_like_delete_allowed(uuid)'::regprocedure;
    select md5(pg_get_triggerdef(t.oid, true)), t.tgenabled into v_trigger_fingerprint, v_trigger_enabled
    from pg_trigger t
    where t.tgrelid = 'public.official_post_likes'::regclass
      and t.tgname = 'quata_guard_official_post_likes_trg' and not t.tgisinternal;
    select md5(coalesce(c.relacl::text, '')) into v_table_acl_fingerprint
    from pg_class c where c.oid = 'public.official_post_likes'::regclass;

    if not (select relrowsecurity and not relforcerowsecurity
            from pg_class where oid = 'public.official_post_likes'::regclass) then
        raise exception 'official_post_likes_rollback_refused:rls_state_mismatch';
    end if;
    if (select pg_get_userbyid(relowner) from pg_class where oid = 'public.official_post_likes'::regclass) <> 'postgres' then
        raise exception 'official_post_likes_rollback_refused:table_owner_mismatch';
    end if;
    if (select pg_get_userbyid(proowner) from pg_proc where oid = 'public.quata_guard_official_post_likes()'::regprocedure) <> 'postgres'
       or (select pg_get_userbyid(proowner) from pg_proc where oid = 'public.quata_official_like_delete_allowed(uuid)'::regprocedure) <> 'postgres' then
        raise exception 'official_post_likes_rollback_refused:function_owner_mismatch';
    end if;
    select md5(prosrc), md5(pg_get_functiondef(oid))
    into v_guard_body_fingerprint, v_guard_definition_fingerprint
    from pg_proc where oid = 'public.quata_guard_official_post_likes()'::regprocedure;
    if (select prosecdef from pg_proc where oid = 'public.quata_guard_official_post_likes()'::regprocedure)
       or not exists (
           select 1 from pg_proc p join pg_language l on l.oid = p.prolang
           where p.oid = 'public.quata_guard_official_post_likes()'::regprocedure
             and l.lanname = 'plpgsql' and pg_get_function_result(p.oid) = 'trigger'
             and pg_get_function_arguments(p.oid) = '' and p.provolatile = 'v'
             and not p.proisstrict and not p.proleakproof and p.proparallel = 'u'
             and p.prokind = 'f' and p.proconfig::text = '{"search_path=public, auth"}')
       or v_guard_body_fingerprint is distinct from 'a2248d523b9a3386702018eec65422a4'
       or v_guard_definition_fingerprint is distinct from '2e850b71a1f7aa2c1249fe2a0c0ee35d' then
        raise exception 'official_post_likes_rollback_refused:guard_anchor_mismatch';
    end if;
    if v_policy_fingerprint is distinct from 'd046ca9fab6ca48f72bd0c5eb03981ac' then
        raise exception 'official_post_likes_rollback_refused:policy_fingerprint_mismatch';
    end if;
    -- The body is bound byte-for-byte by the executor's locked catalog
    -- fingerprint. This release changes only prosecdef, so do not reject the
    -- production baseline merely because its pre-existing body differs from
    -- the disposable test fixture.
    if v_guard_acl_fingerprint is distinct from 'bdadd35f53ba42be34057c3354b5adc2' then
        raise exception 'official_post_likes_rollback_refused:guard_fingerprint_mismatch';
    end if;
    if v_helper_fingerprint is distinct from '139c75e8a54504468e1861557a681264'
       or v_helper_acl_fingerprint is distinct from 'f68c8117f92737941988fa4a4d629d92' then
        raise exception 'official_post_likes_rollback_refused:helper_fingerprint_mismatch';
    end if;
    if v_trigger_fingerprint is distinct from 'abba4fbe811d7c60f8973aafeb46c845'
       or v_trigger_enabled is distinct from 'O' then
        raise exception 'official_post_likes_rollback_refused:trigger_binding_mismatch';
    end if;
    if v_table_acl_fingerprint is distinct from 'c7df1252ee777b76dbef7e2f5ce23b2c' then
        raise exception 'official_post_likes_rollback_refused:table_acl_mismatch';
    end if;
end;
$$;

drop policy official_post_likes_authenticated_delete_own_or_admin
    on public.official_post_likes;
drop policy official_post_likes_authenticated_insert_own
    on public.official_post_likes;
drop policy official_post_likes_public_read
    on public.official_post_likes;

alter table public.official_post_likes disable row level security;
alter function public.quata_guard_official_post_likes() security definer;
-- Reset the explicit owner entry too, then replay the captured grant order.
-- Ownership itself is unchanged; this only makes proacl byte-stable on PG17.
revoke all on function public.quata_guard_official_post_likes()
from public, anon, authenticated, service_role, postgres;
grant execute on function public.quata_guard_official_post_likes()
to public, postgres, anon, authenticated, service_role;

revoke all privileges on public.official_post_likes from anon;
grant select on public.official_post_likes to anon;
revoke all privileges on public.official_post_likes from authenticated;
grant select, insert, delete on public.official_post_likes to authenticated;

drop function public.quata_official_like_delete_allowed(uuid);

commit;
