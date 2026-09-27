begin;

create or replace function public.quata_chat_actor_profile_id(p_actor_profile_id uuid default null)
returns uuid
language plpgsql
stable
security definer
set search_path = public, auth
as $$
declare
    v_auth_uid uuid := auth.uid();
    v_auth_profile_id uuid;
begin
    if v_auth_uid is not null then
        select cp.id
          into v_auth_profile_id
          from public.community_profiles cp
         where cp.auth_user_id = v_auth_uid
           and cp.account_status = 'active'
         limit 1;

        if v_auth_profile_id is null then
            raise exception 'authenticated user has no active profile'
                using errcode = '42501';
        end if;
        if p_actor_profile_id is not null and p_actor_profile_id <> v_auth_profile_id then
            raise exception 'actor profile does not match authenticated Supabase user'
                using errcode = '42501';
        end if;
        return v_auth_profile_id;
    end if;

    if p_actor_profile_id is not null then
        if exists (
            select 1 from public.community_profiles
             where id = p_actor_profile_id and account_status = 'active'
        ) then
            return p_actor_profile_id;
        end if;
        raise exception 'actor profile does not exist or is inactive'
            using errcode = '42501';
    end if;

    raise exception 'actor profile is required' using errcode = '42501';
end;
$$;

drop function if exists public.quata_legacy_android_v32_chat_request_allowed();

commit;
