begin;

-- RLS-003: keep the public profile feed readable and the legacy anonymous
-- registration insert available, but bind every mutable profile to its actor.
-- Password/recovery fields remain writable by the owner for compatibility with
-- the current Android profile editor. Identity, lifecycle and counters are
-- server-managed.

alter table public.community_profiles enable row level security;

drop policy if exists "Users can insert their own profile" on public.community_profiles;
drop policy if exists "Users can update their own profile" on public.community_profiles;
drop policy if exists "Users can view their own profile" on public.community_profiles;
drop policy if exists "public insert profiles" on public.community_profiles;
drop policy if exists "public read profiles" on public.community_profiles;
drop policy if exists "public update profiles" on public.community_profiles;
drop policy if exists "authenticated update profiles" on public.community_profiles;

create policy "public read profiles"
on public.community_profiles
for select
to anon, authenticated
using (true);

-- Android currently creates the profile before the Auth bridge returns a JWT.
-- The trigger below replaces a caller-supplied id and rejects every privileged
-- value; the policy repeats those invariants so they remain fail-closed if the
-- trigger is ever removed accidentally.
create policy "public insert profiles"
on public.community_profiles
for insert
to anon, authenticated
with check (
    auth_user_id is null
    and is_admin = false
    and is_official = false
    and account_status = 'active'
    and deactivated_at is null
    and deactivated_auth_user_id is null
    and followers_count = 0
    and following_count = 0
);

create policy "authenticated update profiles"
on public.community_profiles
for update
to authenticated
using (
    id = public.quata_current_profile_id()
    or public.quata_current_profile_is_admin()
)
with check (
    id = public.quata_current_profile_id()
    or public.quata_current_profile_is_admin()
);

create or replace function public.quata_guard_profile_roles()
returns trigger
language plpgsql
security invoker
set search_path = public, auth
as $$
declare
    v_actor uuid;
    v_actor_is_admin boolean := false;
begin
    -- This check is safe only because this trigger is SECURITY INVOKER:
    -- current_user is the real PostgREST/database role, never its owner.
    if public.quata_current_role_is_service() then
        return new;
    end if;

    if tg_op = 'INSERT' then
        if new.auth_user_id is not null
           or new.is_admin
           or new.is_official
           or new.account_status <> 'active'
           or new.deactivated_at is not null
           or new.deactivated_auth_user_id is not null
           or new.followers_count <> 0
           or new.following_count <> 0 then
            raise exception 'Profile identity, roles, lifecycle and counters are server-managed'
                using errcode = '42501';
        end if;

        -- Do not let an anonymous caller choose an Auth UUID and later inherit
        -- that identity through the legacy id = auth.uid() mapping.
        new.id := gen_random_uuid();
        return new;
    end if;

    v_actor := public.quata_current_profile_id();
    v_actor_is_admin := public.quata_current_profile_is_admin();

    if v_actor is null then
        raise exception 'An authenticated profile actor is required'
            using errcode = '42501';
    end if;

    if new.id is distinct from old.id
       or new.auth_user_id is distinct from old.auth_user_id
       or new.account_status is distinct from old.account_status
       or new.deactivated_at is distinct from old.deactivated_at
       or new.deactivated_auth_user_id is distinct from old.deactivated_auth_user_id
       or new.created_at is distinct from old.created_at
       or new.followers_count is distinct from old.followers_count
       or new.following_count is distinct from old.following_count then
        raise exception 'Profile identity, lifecycle and counters are server-managed'
            using errcode = '42501';
    end if;

    if new.is_admin is distinct from old.is_admin
       or new.is_official is distinct from old.is_official then
        if not v_actor_is_admin then
            raise exception 'Only administrators can change official roles'
                using errcode = '42501';
        end if;
    end if;

    if v_actor <> old.id then
        if not v_actor_is_admin then
            raise exception 'A profile can only update itself'
                using errcode = '42501';
        end if;

        -- Administrators may assign roles, but may not edit another user's
        -- display, contact or recovery data.
        if (to_jsonb(new) - array['is_admin', 'is_official'])
           is distinct from
           (to_jsonb(old) - array['is_admin', 'is_official']) then
            raise exception 'Administrators may only change profile roles'
                using errcode = '42501';
        end if;
    end if;

    return new;
end;
$$;

drop trigger if exists quata_guard_profile_roles_trg on public.community_profiles;
create trigger quata_guard_profile_roles_trg
before insert or update on public.community_profiles
for each row execute function public.quata_guard_profile_roles();

revoke delete, truncate, references, trigger
on public.community_profiles
from anon, authenticated;

revoke update
on public.community_profiles
from anon;

grant select, insert
on public.community_profiles
to anon, authenticated;

grant update
on public.community_profiles
to authenticated;

commit;
