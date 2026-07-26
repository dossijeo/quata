-- Emergency rollback of RLS-001-FORWARD. Preserve migration ledger; a future
-- containment restoration must use another forward migration.
begin;
drop policy if exists "authenticated delete own or admin comments" on public.community_comments;
drop policy if exists "authenticated insert own comments" on public.community_comments;
grant delete, insert, update, truncate, references, trigger on table public.community_comments to anon, authenticated;
create policy "public delete comments" on public.community_comments for delete to public using (true);
create policy "public insert comments" on public.community_comments for insert to public with check (true);
create policy "public read comments" on public.community_comments for select to public using (true);
create policy "public update comments" on public.community_comments for update to public using (true) with check (true);
commit;
