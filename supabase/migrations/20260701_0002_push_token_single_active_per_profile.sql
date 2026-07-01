-- Keep one active push token per profile. The app treats a user session as
-- single-device, so stale FCM tokens must not keep receiving/sounding pushes.

create or replace function public.quata_register_push_token(
    p_profile_id uuid,
    p_token text,
    p_platform text default 'android'
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_auth_uid uuid := auth.uid();
    v_profile_auth_user_id uuid;
    v_token text := nullif(trim(coalesce(p_token, '')), '');
    v_platform text := lower(nullif(trim(coalesce(p_platform, 'android')), ''));
    v_token_id uuid;
begin
    if v_token is null then
        raise exception 'push token is required' using errcode = '22023';
    end if;

    if v_platform is null or v_platform not in ('android', 'ios', 'web') then
        v_platform := 'android';
    end if;

    select auth_user_id
    into v_profile_auth_user_id
    from public.community_profiles
    where id = p_profile_id;

    if v_profile_auth_user_id is null then
        raise exception 'profile has no auth identity' using errcode = '42501';
    end if;

    if v_auth_uid is not null and v_auth_uid <> v_profile_auth_user_id then
        raise exception 'cannot register token for another profile' using errcode = '42501';
    end if;

    insert into public.push_tokens(user_id, auth_user_id, token, platform, updated_at, last_seen_at, disabled_at, last_error_text)
    values (p_profile_id, v_profile_auth_user_id, v_token, v_platform, now(), now(), null, null)
    on conflict (token) do update
    set user_id = excluded.user_id,
        auth_user_id = excluded.auth_user_id,
        platform = excluded.platform,
        updated_at = now(),
        last_seen_at = now(),
        disabled_at = null,
        last_error_text = null
    returning id into v_token_id;

    update public.push_tokens
    set disabled_at = now(),
        last_error_text = 'Superseded by a newer active push token for this profile'
    where user_id = p_profile_id
      and id <> v_token_id
      and disabled_at is null;

    return jsonb_build_object('result', true, 'id', v_token_id);
end;
$$;

revoke all on function public.quata_register_push_token(uuid, text, text) from public;
grant execute on function public.quata_register_push_token(uuid, text, text) to authenticated;

with ranked as (
    select
        id,
        row_number() over (
            partition by user_id
            order by last_seen_at desc nulls last, updated_at desc nulls last, created_at desc nulls last
        ) as rn
    from public.push_tokens
    where disabled_at is null
)
update public.push_tokens p
set disabled_at = now(),
    last_error_text = 'Superseded by a newer active push token for this profile'
from ranked r
where p.id = r.id
  and r.rn > 1;
