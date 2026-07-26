-- RLS-002: bind every Official like mutation to the authenticated profile.
--
-- The original trigger was SECURITY DEFINER and owned by postgres. Its call to
-- quata_current_role_is_service() therefore observed current_user = postgres
-- and took the service bypass even for authenticated PostgREST requests.
--
-- Keep the trigger as a fail-loud guard, but run it as the invoking database
-- role. RLS is the independent table-level boundary. Anonymous and
-- authenticated reads stay public, preserving the existing feed and Android
-- contracts.

begin;

alter function public.quata_guard_official_post_likes() security invoker;

alter table public.official_post_likes enable row level security;

-- DELETE policies normally hide a foreign row and PostgREST reports an empty
-- successful representation. This helper keeps the public contract fail-loud:
-- an authenticated attempt against another profile raises SQLSTATE 42501 while
-- the policy remains the primary authorization boundary.
create or replace function public.quata_official_like_delete_allowed(
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

    raise exception 'Only the like owner or an administrator can remove this like'
        using errcode = '42501';
end;
$$;

revoke all on function public.quata_official_like_delete_allowed(uuid)
from public, anon;
grant execute on function public.quata_official_like_delete_allowed(uuid)
to authenticated;

drop policy if exists official_post_likes_public_read on public.official_post_likes;
create policy official_post_likes_public_read
on public.official_post_likes
for select
to anon, authenticated
using (true);

drop policy if exists official_post_likes_authenticated_insert_own
on public.official_post_likes;
create policy official_post_likes_authenticated_insert_own
on public.official_post_likes
for insert
to authenticated
with check (
    auth.uid() is not null
    and profile_id = public.quata_current_profile_id()
);

drop policy if exists official_post_likes_authenticated_delete_own_or_admin
on public.official_post_likes;
create policy official_post_likes_authenticated_delete_own_or_admin
on public.official_post_likes
for delete
to authenticated
using (
    public.quata_official_like_delete_allowed(profile_id)
);

-- Keep grants explicit. RLS narrows these table privileges per row.
revoke all privileges on public.official_post_likes from anon;
grant select on public.official_post_likes to anon;
grant select, insert, delete on public.official_post_likes to authenticated;

commit;
