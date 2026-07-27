-- RLS-006: bind Official comment mutations to their authenticated profile.
--
-- This is a forward-only, deployment-ready migration. It intentionally keeps
-- the existing anonymous/authenticated reads and Android/Web table shape.
-- `profile_id` is immutable after creation, including for administrators.

begin;

alter function public.quata_guard_official_post_comments() security invoker;
revoke all on function public.quata_guard_official_post_comments()
from public, anon, authenticated, service_role;
grant execute on function public.quata_guard_official_post_comments() to postgres;

create or replace function public.quata_official_comment_mutation_allowed(
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
        raise exception 'Authentication required' using errcode = '42501';
    end if;

    if p_profile_id = v_actor or public.quata_current_profile_is_admin() then
        return true;
    end if;

    raise exception 'Only the comment owner or an administrator can change this comment'
        using errcode = '42501';
end;
$$;

revoke all on function public.quata_official_comment_mutation_allowed(uuid)
from public, anon;
grant execute on function public.quata_official_comment_mutation_allowed(uuid)
to authenticated;

-- Replace the baseline trigger body so the invariant is enforced independently
-- of RLS. The service role may maintain content, but never transfer authorship.
create or replace function public.quata_guard_official_post_comments()
returns trigger
language plpgsql
security invoker
set search_path = public, auth
as $$
declare
    v_actor uuid := public.quata_current_profile_id();
begin
    if tg_op = 'UPDATE' and new.profile_id is distinct from old.profile_id then
        raise exception 'Official comment author cannot be changed' using errcode = '42501';
    end if;

    if public.quata_current_role_is_service() then
        if tg_op = 'UPDATE' then new.updated_at = now(); end if;
        if tg_op = 'DELETE' then return old; end if;
        return new;
    end if;

    if v_actor is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;
    if tg_op = 'INSERT' and new.profile_id <> v_actor then
        raise exception 'Comments must be created by the current profile' using errcode = '42501';
    end if;
    if tg_op in ('UPDATE', 'DELETE')
       and old.profile_id <> v_actor
       and not public.quata_current_profile_is_admin() then
        raise exception 'Only the comment owner or an administrator can change this comment'
            using errcode = '42501';
    end if;
    if tg_op = 'UPDATE' then new.updated_at = now(); return new; end if;
    if tg_op = 'DELETE' then return old; end if;
    return new;
end;
$$;

alter table public.official_post_comments enable row level security;

drop policy if exists official_post_comments_public_read on public.official_post_comments;
create policy official_post_comments_public_read on public.official_post_comments
for select to anon, authenticated using (true);

drop policy if exists official_post_comments_authenticated_insert_own on public.official_post_comments;
create policy official_post_comments_authenticated_insert_own on public.official_post_comments
for insert to authenticated
with check (auth.uid() is not null and profile_id = public.quata_current_profile_id());

drop policy if exists official_post_comments_authenticated_update_own_or_admin on public.official_post_comments;
create policy official_post_comments_authenticated_update_own_or_admin on public.official_post_comments
for update to authenticated
using (public.quata_official_comment_mutation_allowed(profile_id))
with check (public.quata_official_comment_mutation_allowed(profile_id));

drop policy if exists official_post_comments_authenticated_delete_own_or_admin on public.official_post_comments;
create policy official_post_comments_authenticated_delete_own_or_admin on public.official_post_comments
for delete to authenticated
using (public.quata_official_comment_mutation_allowed(profile_id));

revoke all privileges on public.official_post_comments from anon;
grant select on public.official_post_comments to anon;
revoke all privileges on public.official_post_comments from authenticated;
grant select, insert, update, delete on public.official_post_comments to authenticated;

commit;
