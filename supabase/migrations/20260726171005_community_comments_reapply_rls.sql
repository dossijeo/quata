-- RLS-001-FORWARD: reapply the reviewed community-comments containment after
-- emergency rollback of 20260726171001.  This is intentionally a NEW ledger
-- version; never edit, delete, repair, or replay 20260726171001.
begin;
do $$
begin
    if to_regclass('public.community_comments') is null then raise exception 'RLS-001-FORWARD precondition failed: community_comments missing'; end if;
    if to_regprocedure('public.quata_chat_auth_profile_id()') is null then raise exception 'RLS-001-FORWARD precondition failed: profile resolver missing'; end if;
    if to_regprocedure('public.quata_current_profile_is_admin()') is null then raise exception 'RLS-001-FORWARD precondition failed: admin predicate missing'; end if;
end $$;
alter table public.community_comments enable row level security;
drop policy if exists "public delete comments" on public.community_comments;
drop policy if exists "authenticated delete own or admin comments" on public.community_comments;
drop policy if exists "public insert comments" on public.community_comments;
drop policy if exists "authenticated insert own comments" on public.community_comments;
drop policy if exists "public update comments" on public.community_comments;
revoke delete on table public.community_comments from anon;
revoke delete on table public.community_comments from public;
revoke insert on table public.community_comments from anon;
revoke insert on table public.community_comments from public;
revoke update on table public.community_comments from anon, authenticated;
revoke update on table public.community_comments from public;
revoke truncate, references, trigger on table public.community_comments from anon, authenticated;
grant delete on table public.community_comments to authenticated;
grant insert on table public.community_comments to authenticated;
create policy "authenticated insert own comments" on public.community_comments for insert to authenticated with check ((select public.quata_chat_auth_profile_id()) is not null and profile_id = (select public.quata_chat_auth_profile_id()));
create policy "authenticated delete own or admin comments" on public.community_comments for delete to authenticated using ((select public.quata_chat_auth_profile_id()) is not null and (profile_id = (select public.quata_chat_auth_profile_id()) or (select public.quata_current_profile_is_admin())));
comment on policy "authenticated delete own or admin comments" on public.community_comments is 'RLS-001-FORWARD: active canonical owner or explicitly flagged active admin only.';
comment on policy "authenticated insert own comments" on public.community_comments is 'RLS-001-FORWARD: authenticated active canonical profile only.';
commit;
