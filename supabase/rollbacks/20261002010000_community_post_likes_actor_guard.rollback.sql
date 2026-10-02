begin;

drop policy if exists community_post_likes_delete_own
on public.community_post_likes;
drop policy if exists community_post_likes_insert_own
on public.community_post_likes;
drop policy if exists community_post_likes_public_read
on public.community_post_likes;
drop policy if exists "public delete likes" on public.community_post_likes;
drop policy if exists "public insert likes" on public.community_post_likes;
drop policy if exists "public read likes" on public.community_post_likes;

create policy "public delete likes"
on public.community_post_likes
for delete
to public
using (true);

create policy "public insert likes"
on public.community_post_likes
for insert
to public
with check (true);

create policy "public read likes"
on public.community_post_likes
for select
to public
using (true);

grant delete, insert, references, select, trigger, truncate, update
on table public.community_post_likes
to anon, authenticated;

commit;
