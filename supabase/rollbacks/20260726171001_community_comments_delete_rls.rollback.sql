-- Emergency rollback for 20260726171001_community_comments_delete_rls.sql.
-- WARNING: this intentionally restores the vulnerable deployed DELETE contract.

drop policy if exists "authenticated delete own or admin comments"
on public.community_comments;

grant delete on table public.community_comments to anon, authenticated;

create policy "public delete comments"
on public.community_comments
for delete
to public
using (true);

comment on policy "public delete comments"
on public.community_comments is
'Rollback of RLS-001 mitigation: restores the pre-migration permissive DELETE policy.';
