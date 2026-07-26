-- Durable, server-only contract for Web registration.
--
-- This migration deliberately does not change community_profiles RLS or grants. The deployed
-- clients still depend on those legacy policies; the browser registration bundle calls only the
-- quata-web-register Edge Function with a publishable key.

alter table public.community_profiles
    add column if not exists secret_answer_hash text;

create table if not exists public.web_registration_requests (
    id uuid primary key default gen_random_uuid(),
    request_key_hash text not null,
    payload_hash text not null,
    phone_hash text not null,
    client_hash text not null,
    status text not null default 'processing',
    profile_id uuid not null default gen_random_uuid(),
    auth_user_id uuid,
    attempt_count integer not null default 1,
    last_error_code text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    completed_at timestamptz,
    constraint web_registration_requests_key_hash_check
        check (request_key_hash ~ '^[0-9a-f]{64}$'),
    constraint web_registration_requests_payload_hash_check
        check (payload_hash ~ '^[0-9a-f]{64}$'),
    constraint web_registration_requests_phone_hash_check
        check (phone_hash ~ '^[0-9a-f]{64}$'),
    constraint web_registration_requests_client_hash_check
        check (client_hash ~ '^[0-9a-f]{64}$'),
    constraint web_registration_requests_status_check
        check (status in ('processing', 'completed', 'failed', 'cleanup_required')),
    constraint web_registration_requests_attempt_count_check
        check (attempt_count between 1 and 20),
    constraint web_registration_requests_key_unique unique (request_key_hash)
);

create unique index if not exists web_registration_requests_active_phone_key
on public.web_registration_requests(phone_hash)
where status in ('processing', 'completed', 'cleanup_required');

create index if not exists web_registration_requests_cleanup_idx
on public.web_registration_requests(status, updated_at)
where status in ('processing', 'cleanup_required');

create table if not exists public.web_registration_rate_limits (
    scope_hash text not null,
    window_started_at timestamptz not null,
    attempts integer not null default 1,
    updated_at timestamptz not null default now(),
    primary key (scope_hash, window_started_at),
    constraint web_registration_rate_limits_scope_check
        check (scope_hash ~ '^(phone|client|ip):[0-9a-f]{64}$'),
    constraint web_registration_rate_limits_attempts_check
        check (attempts between 1 and 100000)
);

create index if not exists web_registration_rate_limits_expiry_idx
on public.web_registration_rate_limits(window_started_at);

alter table public.web_registration_requests enable row level security;
alter table public.web_registration_rate_limits enable row level security;

revoke all on table public.web_registration_requests from public, anon, authenticated;
revoke all on table public.web_registration_rate_limits from public, anon, authenticated;
grant select, insert, update, delete on table public.web_registration_requests to service_role;
grant select, insert, update, delete on table public.web_registration_rate_limits to service_role;

create or replace function public.quata_claim_web_registration(
    p_request_key_hash text,
    p_payload_hash text,
    p_phone_hash text,
    p_client_hash text,
    p_ip_hash text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_request public.web_registration_requests%rowtype;
    v_window timestamptz := date_trunc('hour', clock_timestamp());
    v_phone_attempts integer;
    v_client_attempts integer;
    v_ip_attempts integer;
begin
    if p_request_key_hash !~ '^[0-9a-f]{64}$'
       or p_payload_hash !~ '^[0-9a-f]{64}$'
       or p_phone_hash !~ '^[0-9a-f]{64}$'
       or p_client_hash !~ '^[0-9a-f]{64}$'
       or p_ip_hash !~ '^[0-9a-f]{64}$' then
        raise exception 'invalid registration claim' using errcode = '22023';
    end if;

    select *
    into v_request
    from public.web_registration_requests
    where request_key_hash = p_request_key_hash
    for update;

    if found then
        if v_request.payload_hash <> p_payload_hash or v_request.phone_hash <> p_phone_hash then
            return jsonb_build_object('kind', 'conflict');
        end if;
        if v_request.status = 'completed' then
            return jsonb_build_object('kind', 'replay', 'request', to_jsonb(v_request));
        end if;
        if v_request.status = 'cleanup_required' or v_request.attempt_count >= 5 then
            return jsonb_build_object('kind', 'cleanup_required');
        end if;
        if v_request.status = 'processing'
           and v_request.updated_at > clock_timestamp() - interval '2 minutes' then
            return jsonb_build_object(
                'kind', 'busy',
                'retry_after_seconds', 15,
                'request', to_jsonb(v_request)
            );
        end if;
        update public.web_registration_requests
        set status = 'processing',
            attempt_count = attempt_count + 1,
            last_error_code = null,
            updated_at = clock_timestamp()
        where id = v_request.id
        returning * into v_request;
        return jsonb_build_object('kind', 'resume', 'request', to_jsonb(v_request));
    end if;

    insert into public.web_registration_rate_limits(scope_hash, window_started_at, attempts)
    values ('phone:' || p_phone_hash, v_window, 1)
    on conflict (scope_hash, window_started_at) do update
    set attempts = public.web_registration_rate_limits.attempts + 1,
        updated_at = clock_timestamp()
    returning attempts into v_phone_attempts;

    insert into public.web_registration_rate_limits(scope_hash, window_started_at, attempts)
    values ('client:' || p_client_hash, v_window, 1)
    on conflict (scope_hash, window_started_at) do update
    set attempts = public.web_registration_rate_limits.attempts + 1,
        updated_at = clock_timestamp()
    returning attempts into v_client_attempts;

    insert into public.web_registration_rate_limits(scope_hash, window_started_at, attempts)
    values ('ip:' || p_ip_hash, v_window, 1)
    on conflict (scope_hash, window_started_at) do update
    set attempts = public.web_registration_rate_limits.attempts + 1,
        updated_at = clock_timestamp()
    returning attempts into v_ip_attempts;

    if v_phone_attempts > 5 or v_client_attempts > 8 or v_ip_attempts > 30 then
        return jsonb_build_object(
            'kind', 'rate_limited',
            'retry_after_seconds',
            greatest(1, extract(epoch from (v_window + interval '1 hour' - clock_timestamp()))::integer)
        );
    end if;

    begin
        insert into public.web_registration_requests(
            request_key_hash,
            payload_hash,
            phone_hash,
            client_hash
        )
        values (
            p_request_key_hash,
            p_payload_hash,
            p_phone_hash,
            p_client_hash
        )
        returning * into v_request;
    exception
        when unique_violation then
            select *
            into v_request
            from public.web_registration_requests
            where request_key_hash = p_request_key_hash
               or (
                   phone_hash = p_phone_hash
                   and status in ('processing', 'completed', 'cleanup_required')
               )
            order by (request_key_hash = p_request_key_hash) desc
            limit 1;
            if v_request.request_key_hash = p_request_key_hash
               and v_request.payload_hash = p_payload_hash then
                return jsonb_build_object(
                    'kind',
                    case when v_request.status = 'completed' then 'replay' else 'busy' end,
                    'retry_after_seconds', 15,
                    'request', to_jsonb(v_request)
                );
            end if;
            return jsonb_build_object('kind', 'conflict');
    end;

    return jsonb_build_object('kind', 'new', 'request', to_jsonb(v_request));
end;
$$;

create or replace function public.quata_web_registration_auth_user(p_email text)
returns table(id uuid, raw_user_meta_data jsonb)
language sql
security definer
set search_path = auth, public
as $$
    select users.id, users.raw_user_meta_data
    from auth.users
    where lower(users.email) = lower(trim(p_email))
    limit 1
$$;

revoke all on function public.quata_claim_web_registration(text, text, text, text, text) from public, anon, authenticated;
revoke all on function public.quata_web_registration_auth_user(text) from public, anon, authenticated;
grant execute on function public.quata_claim_web_registration(text, text, text, text, text) to service_role;
grant execute on function public.quata_web_registration_auth_user(text) to service_role;

comment on table public.web_registration_requests is
    'Server-only saga/idempotency ledger for quata-web-register; contains hashes, never raw credentials.';
comment on table public.web_registration_rate_limits is
    'Server-only fixed-window anti-abuse counters for Web registration.';
comment on function public.quata_claim_web_registration(text, text, text, text, text) is
    'Atomically applies registration rate limits and claims/resumes an idempotent saga.';
