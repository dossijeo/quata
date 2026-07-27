begin;

alter table public.community_profile_follows enable row level security;

drop policy if exists "allow all" on public.community_profile_follows;
drop policy if exists "public delete profile follows" on public.community_profile_follows;
drop policy if exists "public insert profile follows" on public.community_profile_follows;
drop policy if exists "public read profile follows" on public.community_profile_follows;
drop policy if exists community_profile_follows_public_read on public.community_profile_follows;
drop policy if exists community_profile_follows_insert_own on public.community_profile_follows;
drop policy if exists community_profile_follows_delete_own_or_admin on public.community_profile_follows;

create or replace function public.quata_profile_follow_target_is_active(
    p_profile_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = public, auth
as $$
    select exists (
        select 1
        from public.community_profiles cp
        where cp.id = p_profile_id
          and cp.account_status = 'active'
    );
$$;

create or replace function public.quata_profile_follow_delete_allowed(
    p_follower_profile_id uuid
)
returns boolean
language plpgsql
stable
security definer
set search_path = public, auth
as $$
declare
    v_actor uuid := public.quata_chat_auth_profile_id();
begin
    if v_actor is null then
        raise exception 'An active authenticated profile is required'
            using errcode = '42501';
    end if;

    if p_follower_profile_id = v_actor
       or public.quata_current_profile_is_admin() then
        return true;
    end if;

    raise exception 'Only the follow owner or an administrator can remove this edge'
        using errcode = '42501';
end;
$$;

create or replace function public.quata_guard_profile_follows()
returns trigger
language plpgsql
security invoker
set search_path = public, auth
as $$
declare
    v_actor uuid;
begin
    if public.quata_current_role_is_service() then
        if tg_op = 'DELETE' then return old; end if;
        return new;
    end if;

    v_actor := public.quata_chat_auth_profile_id();
    if v_actor is null then
        raise exception 'An active authenticated profile is required'
            using errcode = '42501';
    end if;

    if tg_op = 'INSERT' then
        if new.follower_profile_id <> v_actor then
            raise exception 'A follow edge must belong to the current profile'
                using errcode = '42501';
        end if;
        if not public.quata_profile_follow_target_is_active(
            new.followed_profile_id
        ) then
            raise exception 'The followed profile must be active'
                using errcode = '42501';
        end if;
        return new;
    end if;

    if old.follower_profile_id <> v_actor
       and not public.quata_current_profile_is_admin() then
        raise exception 'Only the follow owner or an administrator can remove this edge'
            using errcode = '42501';
    end if;
    return old;
end;
$$;

drop trigger if exists quata_guard_profile_follows_trg
on public.community_profile_follows;
create trigger quata_guard_profile_follows_trg
before insert or delete on public.community_profile_follows
for each row execute function public.quata_guard_profile_follows();

create policy community_profile_follows_public_read
on public.community_profile_follows
for select
to anon, authenticated
using (true);

create policy community_profile_follows_insert_own
on public.community_profile_follows
for insert
to authenticated
with check (
    follower_profile_id = public.quata_chat_auth_profile_id()
    and public.quata_profile_follow_target_is_active(followed_profile_id)
);

create policy community_profile_follows_delete_own_or_admin
on public.community_profile_follows
for delete
to authenticated
using (
    public.quata_profile_follow_delete_allowed(follower_profile_id)
);

revoke all privileges on public.community_profile_follows from anon, authenticated;
grant select on public.community_profile_follows to anon;
grant select, insert, delete on public.community_profile_follows to authenticated;

-- This legacy RPC uses auth.uid() as a profile id and references obsolete
-- column names. Current Android does not call it; keep it unavailable until it
-- is replaced by an actor-aware implementation.
revoke execute on function public.toggle_follow_profile(uuid)
from public, anon, authenticated;
grant execute on function public.toggle_follow_profile(uuid) to service_role;
comment on function public.toggle_follow_profile(uuid) is
    'Deprecated: use actor-guarded community_profile_follows INSERT/DELETE.';

revoke all on function public.quata_profile_follow_target_is_active(uuid)
from public, anon;
grant execute on function public.quata_profile_follow_target_is_active(uuid)
to authenticated;

revoke all on function public.quata_profile_follow_delete_allowed(uuid)
from public, anon;
grant execute on function public.quata_profile_follow_delete_allowed(uuid)
to authenticated;

commit;
