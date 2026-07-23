-- Multi-device native push and isolated Web Push sessions.
--
-- Android keeps using quata_register_push_token/quata_unregister_push_token.
-- Web clients authenticate through quata-auth-bridge action=web_login and
-- register browser subscriptions through the quata-web-push Edge Function.

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

    insert into public.push_tokens(
        user_id,
        auth_user_id,
        token,
        platform,
        updated_at,
        last_seen_at,
        disabled_at,
        last_error_text
    )
    values (
        p_profile_id,
        v_profile_auth_user_id,
        v_token,
        v_platform,
        now(),
        now(),
        null,
        null
    )
    on conflict (token) do update
    set user_id = excluded.user_id,
        auth_user_id = excluded.auth_user_id,
        platform = excluded.platform,
        updated_at = now(),
        last_seen_at = now(),
        disabled_at = null,
        last_error_text = null
    returning id into v_token_id;

    return jsonb_build_object('result', true, 'id', v_token_id);
end;
$$;

revoke all on function public.quata_register_push_token(uuid, text, text) from public;
grant execute on function public.quata_register_push_token(uuid, text, text) to authenticated;

-- Restore only tokens disabled by the removed single-device rule. Truly
-- invalid tokens and tokens explicitly disabled on logout remain disabled.
update public.push_tokens
set disabled_at = null,
    last_error_text = null,
    updated_at = now()
where disabled_at is not null
  and last_error_text = 'Superseded by a newer active push token for this profile';

create table if not exists public.web_client_sessions (
    id uuid primary key default gen_random_uuid(),
    profile_id uuid not null references public.community_profiles(id) on delete cascade,
    auth_user_id uuid not null references auth.users(id) on delete cascade,
    client_instance_id text not null,
    token_hash text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    revoked_at timestamptz,
    constraint web_client_sessions_profile_instance_key unique (profile_id, client_instance_id),
    constraint web_client_sessions_token_hash_key unique (token_hash),
    constraint web_client_sessions_instance_length_check
        check (char_length(client_instance_id) between 8 and 200),
    constraint web_client_sessions_token_hash_check
        check (token_hash ~ '^[0-9a-f]{64}$')
);

create index if not exists web_client_sessions_active_auth_idx
on public.web_client_sessions(auth_user_id, last_seen_at desc)
where revoked_at is null;

alter table public.web_client_sessions enable row level security;
revoke all on table public.web_client_sessions from public, anon, authenticated;

create table if not exists public.web_push_subscriptions (
    id uuid primary key default gen_random_uuid(),
    web_session_id uuid not null references public.web_client_sessions(id) on delete cascade,
    profile_id uuid not null references public.community_profiles(id) on delete cascade,
    auth_user_id uuid not null references auth.users(id) on delete cascade,
    endpoint text not null,
    p256dh text not null,
    auth_secret text not null,
    expiration_time bigint,
    user_agent text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    disabled_at timestamptz,
    last_error_text text,
    constraint web_push_subscriptions_endpoint_key unique (endpoint),
    constraint web_push_subscriptions_endpoint_check
        check (endpoint ~ '^https://'),
    constraint web_push_subscriptions_keys_check
        check (char_length(p256dh) >= 40 and char_length(auth_secret) >= 16)
);

create index if not exists web_push_subscriptions_active_profile_idx
on public.web_push_subscriptions(profile_id, last_seen_at desc)
where disabled_at is null;

create index if not exists web_push_subscriptions_session_idx
on public.web_push_subscriptions(web_session_id);

alter table public.web_push_subscriptions enable row level security;
revoke all on table public.web_push_subscriptions from public, anon, authenticated;

create table if not exists public.web_push_delivery_log (
    id bigint generated by default as identity primary key,
    message_id bigint not null references public.chat_messages(id) on delete cascade,
    profile_id uuid not null references public.community_profiles(id) on delete cascade,
    subscription_id uuid not null references public.web_push_subscriptions(id) on delete cascade,
    status text not null default 'reserved',
    error_text text,
    created_at timestamptz not null default now(),
    sent_at timestamptz,
    constraint web_push_delivery_log_status_check
        check (status in ('reserved', 'sent', 'error')),
    constraint web_push_delivery_log_message_subscription_key
        unique (message_id, subscription_id)
);

create index if not exists web_push_delivery_log_message_idx
on public.web_push_delivery_log(message_id);

create index if not exists web_push_delivery_log_profile_idx
on public.web_push_delivery_log(profile_id, created_at desc);

alter table public.web_push_delivery_log enable row level security;
revoke all on table public.web_push_delivery_log from public, anon, authenticated;

