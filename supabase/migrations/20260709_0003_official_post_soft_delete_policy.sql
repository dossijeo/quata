-- Let official authors and administrators operate on their own/admin rows even
-- after a soft delete sets deleted_at. Public/anonymous reads still only see
-- non-deleted posts in the requested/default language.

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
