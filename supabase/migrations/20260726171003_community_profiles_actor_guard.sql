begin;

-- RLS-003: keep the public profile feed readable and the legacy anonymous
-- registration insert available, but bind every mutable profile to its actor.
-- Password/recovery fields remain writable by the owner for compatibility with
-- the current Android profile editor. Identity, lifecycle and counters are
-- server-managed.

alter table public.community_profiles enable row level security;

-- Published Android v32 performs password recovery as an anonymous direct
-- PATCH. Keep that unsafe path behind one server-side switch and an exact
-- request signature until telemetry shows no remaining v32 traffic.
create table if not exists public.quata_legacy_android_v32_compatibility (
    singleton boolean primary key default true check (singleton),
    enabled boolean not null default true,
    request_count bigint not null default 0 check (request_count >= 0),
    last_used_at timestamptz,
    disabled_at timestamptz,
    check (enabled or disabled_at is not null)
);

insert into public.quata_legacy_android_v32_compatibility (singleton, enabled)
values (true, true)
on conflict (singleton) do nothing;

revoke all on public.quata_legacy_android_v32_compatibility
from public, anon, authenticated;
grant select on public.quata_legacy_android_v32_compatibility to service_role;

create or replace function public.quata_legacy_android_v32_request_allowed()
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    with request_context as (
        select
            coalesce(
                nullif(current_setting('request.jwt.claim.role', true), ''),
                nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role'
            ) as jwt_role,
            upper(coalesce(current_setting('request.method', true), '')) as method,
            coalesce(current_setting('request.path', true), '') as path,
            coalesce(
                nullif(current_setting('request.headers', true), '')::jsonb,
                '{}'::jsonb
            ) as headers
    )
    select coalesce((
        select compatibility.enabled
           and context.jwt_role = 'anon'
           and context.method in ('PATCH', 'POST')
           and context.path = '/community_profiles'
           and lower(coalesce(context.headers ->> 'user-agent', '')) = 'okhttp/4.12.0'
           and context.headers ->> 'x-quata-client-generation' is null
           and context.headers ->> 'origin' is null
           and context.headers ->> 'referer' is null
           and lower(coalesce(context.headers ->> 'content-profile', 'public')) = 'public'
           and lower(coalesce(context.headers ->> 'prefer', '')) = 'return=representation'
           and coalesce(context.headers ->> 'apikey', '') <> ''
           and lower(coalesce(context.headers ->> 'authorization', '')) like 'bearer %'
        from public.quata_legacy_android_v32_compatibility compatibility
        cross join request_context context
        where compatibility.singleton
    ), false);
$$;

revoke all on function public.quata_legacy_android_v32_request_allowed()
from public;
grant execute on function public.quata_legacy_android_v32_request_allowed()
to anon, authenticated, service_role;

create or replace function public.quata_record_legacy_android_v32_request()
returns void
language plpgsql
volatile
security definer
set search_path = pg_catalog, public
as $$
begin
    if not public.quata_legacy_android_v32_request_allowed() then
        raise exception 'Legacy Android v32 request signature is not allowed'
            using errcode = '42501';
    end if;

    update public.quata_legacy_android_v32_compatibility
    set request_count = request_count + 1,
        last_used_at = clock_timestamp()
    where singleton and enabled;

    if not found then
        raise exception 'Legacy Android v32 compatibility is disabled'
            using errcode = '42501';
    end if;
end;
$$;

revoke all on function public.quata_record_legacy_android_v32_request()
from public, authenticated;
grant execute on function public.quata_record_legacy_android_v32_request()
to anon, service_role;

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
to anon
with check (
    public.quata_legacy_android_v32_request_allowed()
    and auth_user_id is null
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
    id = public.quata_chat_auth_profile_id()
    or (
        public.quata_chat_auth_profile_id() is not null
        and public.quata_current_profile_is_admin()
    )
)
with check (
    id = public.quata_chat_auth_profile_id()
    or (
        public.quata_chat_auth_profile_id() is not null
        and public.quata_current_profile_is_admin()
    )
);

create policy "legacy android v32 password reset"
on public.community_profiles
for update
to anon
using (public.quata_legacy_android_v32_request_allowed())
with check (public.quata_legacy_android_v32_request_allowed());

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
        if not public.quata_legacy_android_v32_request_allowed()
           or new.auth_user_id is not null
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
        perform public.quata_record_legacy_android_v32_request();
        return new;
    end if;

    v_actor := public.quata_chat_auth_profile_id();
    v_actor_is_admin :=
        v_actor is not null and public.quata_current_profile_is_admin();

    if v_actor is null
       and tg_op = 'UPDATE'
       and public.quata_legacy_android_v32_request_allowed() then
        if (to_jsonb(new) - array['pass_hash', 'pass_plain'])
           is distinct from
           (to_jsonb(old) - array['pass_hash', 'pass_plain'])
           or new.pass_hash is not distinct from old.pass_hash
           or new.pass_plain is not distinct from old.pass_plain
           or coalesce(new.pass_plain, '') = ''
           or lower(coalesce(new.pass_hash, ''))
              <> encode(sha256(convert_to(new.pass_plain, 'UTF8')), 'hex') then
            raise exception 'Legacy Android v32 may only rotate its password pair'
                using errcode = '42501';
        end if;

        perform public.quata_record_legacy_android_v32_request();
        return new;
    end if;

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

revoke insert
on public.community_profiles
from anon, authenticated;

grant select
on public.community_profiles
to anon, authenticated;

-- Explicit allowlist: new columns are denied automatically. Credentials remain
-- temporarily because the published Android registration sends them; rollout
-- is blocked until that client moves behind the server auth boundary.
grant insert (
    display_name,
    phone,
    pass_hash,
    phone_normalized,
    country_code,
    phone_local,
    phone_e164,
    barrio,
    barrio_normalized,
    home_community_id,
    neighborhood,
    code,
    telefono,
    nombre,
    avatar_url,
    secret_question,
    secret_answer,
    pass_plain,
    avatar
)
on public.community_profiles
to anon;

grant update
on public.community_profiles
to authenticated;

grant update (pass_hash, pass_plain)
on public.community_profiles
to anon;

commit;
