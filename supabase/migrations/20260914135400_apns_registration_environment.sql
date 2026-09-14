-- Additive APNs registration. Published Android RPCs, grants and RLS remain unchanged.
alter table public.push_tokens
    add column if not exists apns_environment text
    check (apns_environment in ('sandbox', 'production'));

create or replace function public.quata_register_apns_token(
    p_profile_id uuid,
    p_token text,
    p_environment text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_auth_uid uuid := auth.uid();
    v_token text := lower(trim(coalesce(p_token, '')));
    v_token_id uuid;
begin
    if v_auth_uid is null or not exists (
        select 1 from public.community_profiles
        where id = p_profile_id and auth_user_id = v_auth_uid
    ) then
        raise exception 'authenticated profile required' using errcode = '42501';
    end if;
    if v_token !~ '^([0-9a-f]{2})+$' or char_length(v_token) > 1024 then
        raise exception 'invalid APNs token' using errcode = '22023';
    end if;
    if p_environment is null or p_environment not in ('sandbox', 'production') then
        raise exception 'explicit APNs environment required' using errcode = '22023';
    end if;

    insert into public.push_tokens (
        user_id, auth_user_id, token, platform, apns_environment,
        updated_at, last_seen_at, disabled_at, last_error_text
    ) values (
        p_profile_id, v_auth_uid, v_token, 'ios', p_environment,
        now(), now(), null, null
    )
    on conflict (token) do update
    set user_id = excluded.user_id,
        auth_user_id = excluded.auth_user_id,
        platform = excluded.platform,
        apns_environment = excluded.apns_environment,
        updated_at = excluded.updated_at,
        last_seen_at = excluded.last_seen_at,
        disabled_at = null,
        last_error_text = null
    returning id into v_token_id;

    return jsonb_build_object('result', true, 'id', v_token_id);
end;
$$;

revoke all on function public.quata_register_apns_token(uuid, text, text) from public, anon;
grant execute on function public.quata_register_apns_token(uuid, text, text) to authenticated;

-- Serialize claims and reject a registration that changed after recipient lookup.
create or replace function public.quata_reserve_apns_delivery(
    p_message_id bigint,
    p_profile_id uuid,
    p_token_id uuid,
    p_environment text,
    p_registration_updated_at timestamptz
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
    perform 1 from public.push_tokens
    where id = p_token_id and user_id = p_profile_id
      and platform = 'ios' and apns_environment = p_environment
      and updated_at = p_registration_updated_at and disabled_at is null
    for update;
    if not found then return false; end if;

    insert into public.push_delivery_log(message_id, profile_id, push_token_id, status)
    values (p_message_id, p_profile_id, p_token_id, 'reserved')
    on conflict (message_id, profile_id, push_token_id) do update
    set status = 'reserved', error_text = null, sent_at = null, created_at = now()
    where push_delivery_log.status = 'error'
       or (push_delivery_log.status = 'reserved'
           and push_delivery_log.created_at <= now() - interval '60 seconds');
    return found;
end;
$$;

revoke all on function public.quata_reserve_apns_delivery(bigint, uuid, uuid, text, timestamptz)
    from public, anon, authenticated;
grant execute on function public.quata_reserve_apns_delivery(bigint, uuid, uuid, text, timestamptz)
    to service_role;
