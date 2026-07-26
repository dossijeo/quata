-- Emergency rollback for 20260726171002_official_post_likes_actor_guard.sql.
--
-- This script intentionally restores the catalog captured immediately before
-- that migration: RLS disabled, no policies on this table, the guard function
-- SECURITY DEFINER, no delete-policy helper, and the original anon/authenticated
-- grants. It never deletes or updates rows.
--
-- It is deliberately fail-closed on schema drift. Do not use it as a general
-- policy reset: if another release has changed this table, take a fresh backup
-- and prepare a dedicated rollback instead.

begin;

do $$
declare
    v_unexpected_policy text;
begin
    if not (select relrowsecurity from pg_class where oid = 'public.official_post_likes'::regclass) then
        raise exception 'official_post_likes_rollback_refused:rls_not_enabled';
    end if;

    if (select prosecdef from pg_proc where oid = 'public.quata_guard_official_post_likes()'::regprocedure) then
        raise exception 'official_post_likes_rollback_refused:guard_not_security_invoker';
    end if;

    if to_regprocedure('public.quata_official_like_delete_allowed(uuid)') is null then
        raise exception 'official_post_likes_rollback_refused:delete_helper_missing';
    end if;

    select policyname into v_unexpected_policy
    from pg_policies
    where schemaname = 'public'
      and tablename = 'official_post_likes'
      and policyname not in (
          'official_post_likes_public_read',
          'official_post_likes_authenticated_insert_own',
          'official_post_likes_authenticated_delete_own_or_admin'
      )
    limit 1;
    if v_unexpected_policy is not null then
        raise exception 'official_post_likes_rollback_refused:unexpected_policy:%', v_unexpected_policy;
    end if;

    if (select count(*) from pg_policies where schemaname = 'public' and tablename = 'official_post_likes') <> 3 then
        raise exception 'official_post_likes_rollback_refused:expected_three_release_policies';
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

revoke all privileges on public.official_post_likes from anon;
grant select on public.official_post_likes to anon;
revoke all privileges on public.official_post_likes from authenticated;
grant select, insert, delete on public.official_post_likes to authenticated;

drop function public.quata_official_like_delete_allowed(uuid);

commit;
