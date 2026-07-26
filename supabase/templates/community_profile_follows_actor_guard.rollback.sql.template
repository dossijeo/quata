begin;

drop policy if exists community_profile_follows_delete_own_or_admin
on public.community_profile_follows;
drop policy if exists community_profile_follows_insert_own
on public.community_profile_follows;
drop policy if exists community_profile_follows_public_read
on public.community_profile_follows;

drop trigger if exists quata_guard_profile_follows_trg
on public.community_profile_follows;
drop function if exists public.quata_guard_profile_follows();
drop function if exists public.quata_profile_follow_delete_allowed(uuid);
drop function if exists public.quata_profile_follow_target_is_active(uuid);

create policy "allow all"
on public.community_profile_follows
for all
using (true)
with check (true);
create policy "public read profile follows"
on public.community_profile_follows
for select
using (true);
create policy "public insert profile follows"
on public.community_profile_follows
for insert
with check (true);
create policy "public delete profile follows"
on public.community_profile_follows
for delete
using (true);

grant select, insert, update, delete, truncate, references, trigger
on public.community_profile_follows
to anon, authenticated;

grant execute on function public.toggle_follow_profile(uuid)
to public, anon, authenticated, service_role;
comment on function public.toggle_follow_profile(uuid) is null;

commit;
