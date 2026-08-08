-- Bind Official post mutations to the authenticated official profile.
--
-- The historical guard ran as SECURITY DEFINER and called
-- quata_current_role_is_service(), so PostgREST requests could take the owner
-- bypass when the function owner was postgres. RLS is now the primary
-- authorization boundary; the trigger remains a fail-loud invariant guard.

begin;

alter function public.quata_guard_official_posts() security invoker;

revoke all on function public.quata_guard_official_posts()
from public, anon, authenticated, service_role;
grant execute on function public.quata_guard_official_posts() to postgres;

alter table public.official_posts enable row level security;

create or replace function public.quata_official_post_insert_allowed(
    p_profile_id uuid
)
returns boolean
language plpgsql
stable
security definer
set search_path = public, auth
as $$
declare
    v_actor uuid := public.quata_current_profile_id();
    v_is_official boolean := false;
begin
    if auth.uid() is null or v_actor is null then
        raise exception 'Authentication required'
            using errcode = '42501';
    end if;

    if p_profile_id is distinct from v_actor then
        raise exception 'Official posts must be created by the current profile'
            using errcode = '42501';
    end if;

    select cp.is_official
    into v_is_official
    from public.community_profiles cp
    where cp.id = v_actor
      and cp.account_status = 'active';

    if not coalesce(v_is_official, false) then
        raise exception 'Only official accounts can publish official posts'
            using errcode = '42501';
    end if;

    return true;
end;
$$;

create or replace function public.quata_official_post_owner_or_admin_allowed(
    p_profile_id uuid
)
returns boolean
language plpgsql
stable
security definer
set search_path = public, auth
as $$
declare
    v_actor uuid := public.quata_current_profile_id();
begin
    if auth.uid() is null or v_actor is null then
        raise exception 'Authentication required'
            using errcode = '42501';
    end if;

    if p_profile_id = v_actor or public.quata_current_profile_is_admin() then
        return true;
    end if;

    raise exception 'Only the official author or an administrator can mutate this post'
        using errcode = '42501';
end;
$$;

revoke all on function public.quata_official_post_insert_allowed(uuid)
from public, anon;
revoke all on function public.quata_official_post_owner_or_admin_allowed(uuid)
from public, anon;
grant execute on function public.quata_official_post_insert_allowed(uuid)
to authenticated;
grant execute on function public.quata_official_post_owner_or_admin_allowed(uuid)
to authenticated;

drop policy if exists official_posts_public_read_language on public.official_posts;
create policy official_posts_public_read_language
on public.official_posts
for select
to anon, authenticated
using (
    (
        is_published = true
        and deleted_at is null
        and (
            language = 'es'
            or language = public.quata_requested_official_post_language()
        )
    )
    or (
        auth.role() = 'authenticated'
        and (
            profile_id = public.quata_current_profile_id()
            or public.quata_current_profile_is_admin()
        )
    )
);

drop policy if exists official_posts_authenticated_insert on public.official_posts;
drop policy if exists official_posts_authenticated_update_guarded on public.official_posts;
drop policy if exists official_posts_authenticated_delete_guarded on public.official_posts;
drop policy if exists official_posts_admin_update on public.official_posts;
drop policy if exists official_posts_admin_delete on public.official_posts;

create policy official_posts_authenticated_insert_official_own
on public.official_posts
for insert
to authenticated
with check (
    public.quata_official_post_insert_allowed(profile_id)
);

create policy official_posts_authenticated_update_author_or_admin
on public.official_posts
for update
to authenticated
using (
    public.quata_official_post_owner_or_admin_allowed(profile_id)
)
with check (
    public.quata_official_post_owner_or_admin_allowed(profile_id)
);

create policy official_posts_authenticated_delete_author_or_admin
on public.official_posts
for delete
to authenticated
using (
    public.quata_official_post_owner_or_admin_allowed(profile_id)
);

revoke all privileges on public.official_posts from anon;
grant select on public.official_posts to anon;
grant select, insert, update, delete on public.official_posts to authenticated;

commit;
