begin;

do $$
begin
    if to_regclass('public.community_post_likes') is null then
        raise exception 'COMMUNITY-POST-LIKES-ACTOR-GUARD precondition failed: community_post_likes missing';
    end if;
    if to_regprocedure('public.quata_chat_auth_profile_id()') is null then
        raise exception 'COMMUNITY-POST-LIKES-ACTOR-GUARD precondition failed: profile resolver missing';
    end if;
end $$;

alter table public.community_post_likes enable row level security;

drop policy if exists "public read likes" on public.community_post_likes;
drop policy if exists "public insert likes" on public.community_post_likes;
drop policy if exists "public delete likes" on public.community_post_likes;
drop policy if exists community_post_likes_public_read on public.community_post_likes;
drop policy if exists community_post_likes_insert_own on public.community_post_likes;
drop policy if exists community_post_likes_delete_own on public.community_post_likes;

create policy community_post_likes_public_read
on public.community_post_likes
for select
to public
using (true);

create policy community_post_likes_insert_own
on public.community_post_likes
for insert
to authenticated
with check (
    (select public.quata_chat_auth_profile_id()) is not null
    and profile_id = (select public.quata_chat_auth_profile_id())
);

create policy community_post_likes_delete_own
on public.community_post_likes
for delete
to authenticated
using (
    (select public.quata_chat_auth_profile_id()) is not null
    and profile_id = (select public.quata_chat_auth_profile_id())
);

revoke all privileges on table public.community_post_likes
from public, anon, authenticated;
grant select on table public.community_post_likes to anon;
grant select, insert, delete on table public.community_post_likes to authenticated;

comment on policy community_post_likes_insert_own on public.community_post_likes is
    'Only an active authenticated profile may create its own like.';
comment on policy community_post_likes_delete_own on public.community_post_likes is
    'Only an active authenticated profile may remove its own like.';

commit;
