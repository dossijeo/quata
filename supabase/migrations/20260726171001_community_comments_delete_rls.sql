-- RLS-001: community comments may only be deleted by their active canonical
-- profile owner or by an explicitly flagged active administrator.
--
-- Compatibility:
-- * SELECT remains unchanged for anon/authenticated clients.
-- * Android continues deleting by comment id; RLS filters the matching row.
-- * INSERT/UPDATE policies are deliberately out of scope for this migration.

do $$
begin
    if to_regclass('public.community_comments') is null then
        raise exception 'RLS-001 precondition failed: public.community_comments is missing';
    end if;
    if to_regprocedure('public.quata_chat_auth_profile_id()') is null then
        raise exception 'RLS-001 precondition failed: canonical profile resolver is missing';
    end if;
    if to_regprocedure('public.quata_current_profile_is_admin()') is null then
        raise exception 'RLS-001 precondition failed: administrator predicate is missing';
    end if;
end
$$;

alter table public.community_comments enable row level security;

-- The deployed policy is permissive TO public USING (true), so it must be
-- removed rather than combined with a narrower policy.
drop policy if exists "public delete comments" on public.community_comments;
drop policy if exists "authenticated delete own or admin comments" on public.community_comments;

revoke delete on table public.community_comments from anon;
revoke delete on table public.community_comments from public;
grant delete on table public.community_comments to authenticated;

create policy "authenticated delete own or admin comments"
on public.community_comments
for delete
to authenticated
using (
    (select public.quata_chat_auth_profile_id()) is not null
    and (
        profile_id = (select public.quata_chat_auth_profile_id())
        or (select public.quata_current_profile_is_admin())
    )
);

comment on policy "authenticated delete own or admin comments"
on public.community_comments is
'RLS-001: active canonical owner or explicitly flagged active admin only; anonymous DELETE is not granted.';
