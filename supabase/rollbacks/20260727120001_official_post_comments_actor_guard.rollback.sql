-- Emergency rollback for RLS-006. This restores the captured pre-RLS guard
-- and grants; it never changes comment rows or the migration ledger.
begin;

do $$
declare
    v_catalog_fingerprint text;
begin
    if to_regclass('public.official_post_comments') is null
       or to_regprocedure('public.quata_guard_official_post_comments()') is null
       or to_regprocedure('public.quata_official_comment_mutation_allowed(uuid)') is null then
        raise exception 'official_post_comments_rollback_refused:release_object_missing';
    end if;

    with policy_state as (
        select coalesce(string_agg(
            policyname || '|' || cmd || '|' || array_to_string(roles, ',') || '|'
            || coalesce(qual, '') || '|' || coalesce(with_check, ''),
            E'\n' order by policyname
        ), '') as value
        from pg_policies
        where schemaname = 'public' and tablename = 'official_post_comments'
    ),
    guard_state as (
        select pg_get_functiondef(p.oid) || '|' || p.prosecdef || '|'
            || coalesce(p.proconfig::text, '') || '|' || coalesce(p.proacl::text, '') || '|'
            || pg_get_userbyid(p.proowner) as value
        from pg_proc p
        where p.oid = 'public.quata_guard_official_post_comments()'::regprocedure
    ),
    helper_state as (
        select pg_get_functiondef(p.oid) || '|' || p.prosecdef || '|'
            || coalesce(p.proconfig::text, '') || '|' || coalesce(p.proacl::text, '') || '|'
            || pg_get_userbyid(p.proowner) as value
        from pg_proc p
        where p.oid = 'public.quata_official_comment_mutation_allowed(uuid)'::regprocedure
    ),
    trigger_state as (
        select pg_get_triggerdef(t.oid, true) || '|' || t.tgenabled::text as value
        from pg_trigger t
        where t.tgrelid = 'public.official_post_comments'::regclass
          and t.tgname = 'quata_guard_official_post_comments_trg'
          and not t.tgisinternal
    ),
    table_state as (
        select c.relrowsecurity || '|' || c.relforcerowsecurity || '|'
            || pg_get_userbyid(c.relowner) || '|' || coalesce(c.relacl::text, '') as value
        from pg_class c
        where c.oid = 'public.official_post_comments'::regclass
    )
    select md5(concat_ws(E'\n',
        (select value from table_state),
        (select value from policy_state),
        (select value from guard_state),
        (select value from helper_state),
        (select value from trigger_state)
    )) into v_catalog_fingerprint;

    -- PostgreSQL 17 fingerprint of the exact state installed by RLS-006.
    -- Any later policy, ACL, ownership, trigger, RLS-mode or function change
    -- must be handled by a new forward migration, never erased by this rollback.
    if v_catalog_fingerprint is distinct from 'aec234b12010b22a2313a924b9528d8e' then
        raise exception 'official_post_comments_rollback_refused:catalog_drift:%',
            coalesce(v_catalog_fingerprint, 'null');
    end if;
end;
$$;

drop policy if exists official_post_comments_authenticated_delete_own_or_admin on public.official_post_comments;
drop policy if exists official_post_comments_authenticated_update_own_or_admin on public.official_post_comments;
drop policy if exists official_post_comments_authenticated_insert_own on public.official_post_comments;
drop policy if exists official_post_comments_public_read on public.official_post_comments;
alter table public.official_post_comments disable row level security;

drop function if exists public.quata_official_comment_mutation_allowed(uuid);

create or replace function public.quata_guard_official_post_comments()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_actor uuid := public.quata_current_profile_id();
begin
    if public.quata_current_role_is_service() then
        if tg_op = 'UPDATE' then new.updated_at = now(); end if;
        if tg_op = 'DELETE' then return old; end if;
        return new;
    end if;
    if v_actor is null then raise exception 'Authentication required' using errcode = '42501'; end if;
    if tg_op = 'INSERT' and new.profile_id <> v_actor then
        raise exception 'Comments must be created by the current profile' using errcode = '42501';
    end if;
    if tg_op in ('UPDATE', 'DELETE') and old.profile_id <> v_actor
       and not public.quata_current_profile_is_admin() then
        raise exception 'Only the comment owner or an administrator can change this comment' using errcode = '42501';
    end if;
    if tg_op = 'UPDATE' then new.updated_at = now(); return new; end if;
    if tg_op = 'DELETE' then return old; end if;
    return new;
end;
$$;

revoke all on function public.quata_guard_official_post_comments()
from public, anon, authenticated, service_role, postgres;
grant execute on function public.quata_guard_official_post_comments()
to public, postgres, anon, authenticated, service_role;
revoke all privileges on public.official_post_comments from anon;
grant select on public.official_post_comments to anon;
revoke all privileges on public.official_post_comments from authenticated;
grant select, insert, update, delete on public.official_post_comments to authenticated;

commit;
