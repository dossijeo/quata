-- Allow Qüata administrators to moderate posts created by other users.
-- Normal users keep the previous owner-only behavior.

drop policy if exists community_posts_admin_delete on public.community_posts;
create policy community_posts_admin_delete
on public.community_posts
for delete
to authenticated
using (public.quata_current_profile_is_admin());

drop policy if exists official_posts_admin_update on public.official_posts;
create policy official_posts_admin_update
on public.official_posts
for update
to authenticated
using (
    profile_id = public.quata_current_profile_id()
    or public.quata_current_profile_is_admin()
)
with check (
    profile_id = public.quata_current_profile_id()
    or public.quata_current_profile_is_admin()
);

drop policy if exists official_posts_admin_delete on public.official_posts;
create policy official_posts_admin_delete
on public.official_posts
for delete
to authenticated
using (
    profile_id = public.quata_current_profile_id()
    or public.quata_current_profile_is_admin()
);
