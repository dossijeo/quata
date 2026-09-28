-- Keep account deactivation and every database-backed session retirement in one
-- PostgreSQL transaction. Active delivery rows also lock the owning profile so
-- a request that started before deactivation cannot recreate them afterwards.

create table if not exists public.account_deactivation_operations (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.community_profiles(id) on delete cascade,
  auth_user_id uuid not null references auth.users(id) on delete cascade,
  deactivated_at timestamptz not null,
  lease_expires_at timestamptz not null,
  state text not null default 'database_applied'
    check (state in ('database_applied', 'completed', 'compensating', 'rolled_back', 'reactivating', 'reactivated')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists account_deactivation_operations_open_profile_idx
  on public.account_deactivation_operations(profile_id)
  where state = 'database_applied';

alter table public.account_deactivation_operations enable row level security;
revoke all on table public.account_deactivation_operations from public, anon, authenticated;

create or replace function public.quata_guard_deactivation_reactivation()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
begin
  if old.account_status = 'deactivated' and new.account_status = 'active'
     and not exists (
       select 1 from public.account_deactivation_operations
        where profile_id = old.id
          and auth_user_id = new.auth_user_id
          and deactivated_at = old.deactivated_at
          and state in ('compensating', 'reactivating')
     ) then
    raise exception 'account reactivation was not reserved' using errcode = '55000';
  end if;
  return new;
end;
$function$;

drop trigger if exists quata_community_profiles_deactivation_guard on public.community_profiles;
create trigger quata_community_profiles_deactivation_guard
before update on public.community_profiles
for each row execute function public.quata_guard_deactivation_reactivation();

create or replace function public.quata_guard_active_delivery_owner()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
declare
  v_row jsonb := to_jsonb(new);
  v_profile_id uuid;
  v_row_auth_user_id uuid;
  v_account_status text;
  v_auth_user_id uuid;
  v_session_profile_id uuid;
  v_session_auth_user_id uuid;
  v_session_revoked_at timestamptz;
begin
  if tg_table_name = 'push_tokens' and nullif(v_row->>'disabled_at', '') is not null then
    return new;
  elsif tg_table_name = 'web_client_sessions' and nullif(v_row->>'revoked_at', '') is not null then
    return new;
  elsif tg_table_name = 'web_push_subscriptions' and nullif(v_row->>'disabled_at', '') is not null then
    return new;
  end if;

  v_profile_id := coalesce(
    nullif(v_row->>'profile_id', '')::uuid,
    nullif(v_row->>'user_id', '')::uuid
  );
  v_row_auth_user_id := nullif(v_row->>'auth_user_id', '')::uuid;

  select account_status, auth_user_id
    into v_account_status, v_auth_user_id
    from public.community_profiles
   where id = v_profile_id
   for update;

  if v_account_status is distinct from 'active'
     or v_auth_user_id is null
     or v_auth_user_id is distinct from v_row_auth_user_id then
    raise exception 'active delivery owner mismatch' using errcode = '42501';
  end if;

  if tg_table_name = 'web_push_subscriptions' then
    select profile_id, auth_user_id, revoked_at
      into v_session_profile_id, v_session_auth_user_id, v_session_revoked_at
      from public.web_client_sessions
     where id = nullif(v_row->>'web_session_id', '')::uuid;
    if v_session_profile_id is distinct from v_profile_id
       or v_session_auth_user_id is distinct from v_row_auth_user_id
       or v_session_revoked_at is not null then
      raise exception 'active web session mismatch' using errcode = '42501';
    end if;
  end if;
  return new;
end;
$function$;

drop trigger if exists quata_push_tokens_active_owner_guard on public.push_tokens;
create trigger quata_push_tokens_active_owner_guard
before insert or update on public.push_tokens
for each row execute function public.quata_guard_active_delivery_owner();

drop trigger if exists quata_web_client_sessions_active_owner_guard on public.web_client_sessions;
create trigger quata_web_client_sessions_active_owner_guard
before insert or update on public.web_client_sessions
for each row execute function public.quata_guard_active_delivery_owner();

drop trigger if exists quata_web_push_subscriptions_active_owner_guard on public.web_push_subscriptions;
create trigger quata_web_push_subscriptions_active_owner_guard
before insert or update on public.web_push_subscriptions
for each row execute function public.quata_guard_active_delivery_owner();

create or replace function public.quata_account_deactivate(
  p_operation_id uuid,
  p_profile_id uuid,
  p_auth_user_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $function$
declare
  v_updated integer;
  v_deactivated_at timestamptz := clock_timestamp();
  v_account_status text;
  v_row_auth_user_id uuid;
  v_deactivated_auth_user_id uuid;
  v_existing_id uuid;
  v_existing_state text;
begin
  select account_status, auth_user_id, deactivated_auth_user_id, deactivated_at
    into v_account_status, v_row_auth_user_id, v_deactivated_auth_user_id, v_deactivated_at
    from public.community_profiles
   where id = p_profile_id
   for update;

  select id, state, deactivated_at
    into v_existing_id, v_existing_state, v_deactivated_at
    from public.account_deactivation_operations
   where id = p_operation_id
     and profile_id = p_profile_id
     and auth_user_id = p_auth_user_id
   for update;
  if v_existing_id is not null then
    if v_existing_state not in ('database_applied', 'completed') then
      raise exception 'deactivation operation is not resumable' using errcode = '55000';
    end if;
    if v_existing_state = 'database_applied' then
      update public.account_deactivation_operations
         set lease_expires_at = clock_timestamp() + interval '15 minutes', updated_at = now()
       where id = v_existing_id;
    end if;
    return jsonb_build_object(
      'ok', true,
      'profile_id', p_profile_id,
      'operation_id', v_existing_id,
      'deactivated_at', v_deactivated_at,
      'state', v_existing_state
    );
  end if;

  if v_account_status = 'deactivated'
     and v_row_auth_user_id is null
     and v_deactivated_auth_user_id = p_auth_user_id then
    select id, state, deactivated_at
      into v_existing_id, v_existing_state, v_deactivated_at
      from public.account_deactivation_operations
     where profile_id = p_profile_id
       and auth_user_id = p_auth_user_id
       and state in ('database_applied', 'completed')
     order by created_at desc
     limit 1
     for update;
    if v_existing_id is null then
      raise exception 'deactivated account has no resumable operation' using errcode = '55000';
    end if;
    if v_existing_state = 'database_applied' then
      update public.account_deactivation_operations
         set lease_expires_at = clock_timestamp() + interval '15 minutes', updated_at = now()
       where id = v_existing_id;
    end if;
    return jsonb_build_object(
      'ok', true,
      'profile_id', p_profile_id,
      'operation_id', v_existing_id,
      'deactivated_at', v_deactivated_at,
      'state', v_existing_state
    );
  end if;

  if v_account_status is distinct from 'active'
     or v_row_auth_user_id is distinct from p_auth_user_id then
    raise exception 'active account identity mismatch' using errcode = '42501';
  end if;
  v_deactivated_at := clock_timestamp();
  update public.community_profiles
     set account_status = 'deactivated',
         deactivated_at = v_deactivated_at,
         deactivated_auth_user_id = p_auth_user_id,
         auth_user_id = null
   where id = p_profile_id
     and auth_user_id = p_auth_user_id
     and account_status = 'active';
  get diagnostics v_updated = row_count;
  if v_updated <> 1 then
    raise exception 'active account identity mismatch' using errcode = '42501';
  end if;

  update public.web_push_subscriptions
     set disabled_at = v_deactivated_at,
         last_error_text = 'Disabled on account deactivation',
         updated_at = v_deactivated_at
   where (profile_id = p_profile_id or auth_user_id = p_auth_user_id)
     and disabled_at is null;

  update public.web_client_sessions
     set revoked_at = v_deactivated_at,
         updated_at = v_deactivated_at
   where (profile_id = p_profile_id or auth_user_id = p_auth_user_id)
     and revoked_at is null;

  update public.push_tokens
     set disabled_at = v_deactivated_at,
         last_error_text = 'Disabled on account deactivation',
         updated_at = v_deactivated_at
   where (user_id = p_profile_id or auth_user_id = p_auth_user_id)
     and disabled_at is null;

  insert into public.account_deactivation_operations(
    id, profile_id, auth_user_id, deactivated_at, lease_expires_at, state
  ) values (
    p_operation_id, p_profile_id, p_auth_user_id, v_deactivated_at,
    v_deactivated_at + interval '15 minutes', 'database_applied'
  );

  return jsonb_build_object(
    'ok', true,
    'profile_id', p_profile_id,
    'operation_id', p_operation_id,
    'deactivated_at', v_deactivated_at,
    'state', 'database_applied'
  );
end;
$function$;

-- Keep the predecessor Edge deployable while the function rollout moves from
-- two arguments to the caller-owned idempotency key.
create or replace function public.quata_account_deactivate(
  p_profile_id uuid,
  p_auth_user_id uuid
)
returns jsonb
language sql
security definer
set search_path = public, auth
as $function$
  select public.quata_account_deactivate(gen_random_uuid(), p_profile_id, p_auth_user_id);
$function$;

create or replace function public.quata_account_deactivation_complete(
  p_operation_id uuid,
  p_profile_id uuid,
  p_auth_user_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $function$
declare
  v_state text;
begin
  select state into v_state
    from public.account_deactivation_operations
   where id = p_operation_id
     and profile_id = p_profile_id
     and auth_user_id = p_auth_user_id
   for update;
  if v_state = 'completed' then
    return jsonb_build_object('ok', true, 'state', v_state);
  end if;
  if v_state is distinct from 'database_applied' then
    raise exception 'deactivation operation is not completable' using errcode = '42501';
  end if;
  update public.account_deactivation_operations
     set state = 'completed', updated_at = now()
   where id = p_operation_id;
  return jsonb_build_object('ok', true, 'state', 'completed');
end;
$function$;

create or replace function public.quata_account_deactivation_compensate(
  p_operation_id uuid,
  p_profile_id uuid,
  p_auth_user_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $function$
declare
  v_state text;
  v_deactivated_at timestamptz;
  v_updated integer;
begin
  select state, deactivated_at into v_state, v_deactivated_at
    from public.account_deactivation_operations
   where id = p_operation_id
     and profile_id = p_profile_id
     and auth_user_id = p_auth_user_id
   for update;
  if v_state = 'rolled_back' then
    return jsonb_build_object('ok', true, 'state', v_state);
  end if;
  if v_state is distinct from 'database_applied' then
    raise exception 'deactivation operation is not compensatable' using errcode = '42501';
  end if;

  update public.account_deactivation_operations
     set state = 'compensating', updated_at = now()
   where id = p_operation_id;
  update public.community_profiles
     set account_status = 'active',
         deactivated_at = null,
         deactivated_auth_user_id = null,
         auth_user_id = p_auth_user_id
   where id = p_profile_id
     and account_status = 'deactivated'
     and auth_user_id is null
     and deactivated_auth_user_id = p_auth_user_id
     and deactivated_at = v_deactivated_at;
  get diagnostics v_updated = row_count;
  if v_updated <> 1 then
    raise exception 'deactivation compensation identity mismatch' using errcode = '42501';
  end if;

  update public.web_client_sessions
     set revoked_at = null, updated_at = now()
   where (profile_id = p_profile_id or auth_user_id = p_auth_user_id)
     and revoked_at = v_deactivated_at;
  update public.web_push_subscriptions
     set disabled_at = null, last_error_text = null, updated_at = now()
   where (profile_id = p_profile_id or auth_user_id = p_auth_user_id)
     and disabled_at = v_deactivated_at
     and last_error_text = 'Disabled on account deactivation';
  update public.push_tokens
     set disabled_at = null, last_error_text = null, updated_at = now()
   where (user_id = p_profile_id or auth_user_id = p_auth_user_id)
     and disabled_at = v_deactivated_at
     and last_error_text = 'Disabled on account deactivation';
  update public.account_deactivation_operations
     set state = 'rolled_back', updated_at = now()
   where id = p_operation_id;
  return jsonb_build_object('ok', true, 'state', 'rolled_back');
end;
$function$;

create or replace function public.quata_account_reactivation_begin(
  p_operation_id uuid,
  p_profile_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $function$
declare
  v_auth_user_id uuid;
  v_deactivated_at timestamptz;
  v_operation_id uuid;
  v_lease_expires_at timestamptz;
begin
  select deactivated_auth_user_id, deactivated_at
    into v_auth_user_id, v_deactivated_at
    from public.community_profiles
   where id = p_profile_id
     and account_status = 'deactivated'
     and auth_user_id is null
     and deactivated_auth_user_id is not null
   for update;
  if v_auth_user_id is null or v_deactivated_at is null then
    raise exception 'deactivated account identity mismatch' using errcode = '42501';
  end if;
  select id into v_operation_id
    from public.account_deactivation_operations
   where profile_id = p_profile_id
     and auth_user_id = v_auth_user_id
     and deactivated_at = v_deactivated_at
     and state = 'reactivating'
   order by created_at desc
   limit 1
   for update;
  if v_operation_id is not null then
    return jsonb_build_object(
      'ok', true,
      'operation_id', v_operation_id,
      'auth_user_id', v_auth_user_id
    );
  end if;
  if exists (
    select 1 from public.account_deactivation_operations
     where profile_id = p_profile_id and state = 'compensating'
  ) then
    raise exception 'account lifecycle transition already open' using errcode = '55000';
  end if;

  select lease_expires_at into v_lease_expires_at
    from public.account_deactivation_operations
   where profile_id = p_profile_id
     and auth_user_id = v_auth_user_id
     and deactivated_at = v_deactivated_at
     and state = 'database_applied'
   order by created_at desc
   limit 1
   for update;
  if v_lease_expires_at is not null then
    -- Hosted Edge workers are capped at 400 seconds. A 15-minute lease leaves
    -- more than twice that budget before a reactivation can take ownership.
    if v_lease_expires_at > clock_timestamp() then
      raise exception 'account lifecycle transition already open' using errcode = '55000';
    end if;
    update public.account_deactivation_operations
       set state = 'completed', updated_at = now()
     where profile_id = p_profile_id
       and auth_user_id = v_auth_user_id
       and deactivated_at = v_deactivated_at
       and state = 'database_applied';
  end if;

  select id into v_operation_id
    from public.account_deactivation_operations
   where profile_id = p_profile_id
     and auth_user_id = v_auth_user_id
     and deactivated_at = v_deactivated_at
     and state = 'completed'
   order by created_at desc
   limit 1
   for update;
  if v_operation_id is null then
    v_operation_id := p_operation_id;
    insert into public.account_deactivation_operations(
      id, profile_id, auth_user_id, deactivated_at, lease_expires_at, state
    ) values (
      v_operation_id, p_profile_id, v_auth_user_id, v_deactivated_at, now(), 'reactivating'
    );
  else
    update public.account_deactivation_operations
       set state = 'reactivating', updated_at = now()
     where id = v_operation_id;
  end if;
  return jsonb_build_object(
    'ok', true,
    'operation_id', v_operation_id,
    'auth_user_id', v_auth_user_id
  );
end;
$function$;

create or replace function public.quata_account_reactivation_complete(
  p_operation_id uuid,
  p_profile_id uuid,
  p_auth_user_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $function$
declare
  v_state text;
  v_deactivated_at timestamptz;
  v_updated integer;
begin
  select state, deactivated_at into v_state, v_deactivated_at
    from public.account_deactivation_operations
   where id = p_operation_id
     and profile_id = p_profile_id
     and auth_user_id = p_auth_user_id
   for update;
  if v_state = 'reactivated' then
    return jsonb_build_object('ok', true, 'state', v_state);
  end if;
  if v_state is distinct from 'reactivating' then
    raise exception 'account reactivation is not completable' using errcode = '42501';
  end if;
  update public.community_profiles
     set account_status = 'active',
         deactivated_at = null,
         deactivated_auth_user_id = null,
         auth_user_id = p_auth_user_id,
         last_login_at = now()
   where id = p_profile_id
     and account_status = 'deactivated'
     and auth_user_id is null
     and deactivated_auth_user_id = p_auth_user_id
     and deactivated_at = v_deactivated_at;
  get diagnostics v_updated = row_count;
  if v_updated <> 1 then
    raise exception 'account reactivation identity mismatch' using errcode = '42501';
  end if;
  update public.account_deactivation_operations
     set state = 'reactivated', updated_at = now()
   where id = p_operation_id;
  return jsonb_build_object('ok', true, 'state', 'reactivated');
end;
$function$;

create or replace function public.quata_account_reactivation_cancel(
  p_operation_id uuid,
  p_profile_id uuid,
  p_auth_user_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $function$
declare
  v_state text;
begin
  select state into v_state
    from public.account_deactivation_operations
   where id = p_operation_id
     and profile_id = p_profile_id
     and auth_user_id = p_auth_user_id
   for update;
  if v_state = 'completed' then
    return jsonb_build_object('ok', true, 'state', v_state);
  end if;
  if v_state is distinct from 'reactivating' then
    raise exception 'account reactivation is not cancellable' using errcode = '42501';
  end if;
  update public.account_deactivation_operations
     set state = 'completed', updated_at = now()
   where id = p_operation_id;
  return jsonb_build_object('ok', true, 'state', 'completed');
end;
$function$;

revoke all on function public.quata_guard_active_delivery_owner() from public, anon, authenticated;
revoke all on function public.quata_guard_deactivation_reactivation() from public, anon, authenticated;
revoke all on function public.quata_account_deactivate(uuid, uuid) from public, anon, authenticated;
revoke all on function public.quata_account_deactivate(uuid, uuid, uuid) from public, anon, authenticated;
revoke all on function public.quata_account_deactivation_complete(uuid, uuid, uuid) from public, anon, authenticated;
revoke all on function public.quata_account_deactivation_compensate(uuid, uuid, uuid) from public, anon, authenticated;
revoke all on function public.quata_account_reactivation_begin(uuid, uuid) from public, anon, authenticated;
revoke all on function public.quata_account_reactivation_complete(uuid, uuid, uuid) from public, anon, authenticated;
revoke all on function public.quata_account_reactivation_cancel(uuid, uuid, uuid) from public, anon, authenticated;
grant execute on function public.quata_account_deactivate(uuid, uuid) to service_role;
grant execute on function public.quata_account_deactivate(uuid, uuid, uuid) to service_role;
grant execute on function public.quata_account_deactivation_complete(uuid, uuid, uuid) to service_role;
grant execute on function public.quata_account_deactivation_compensate(uuid, uuid, uuid) to service_role;
grant execute on function public.quata_account_reactivation_begin(uuid, uuid) to service_role;
grant execute on function public.quata_account_reactivation_complete(uuid, uuid, uuid) to service_role;
grant execute on function public.quata_account_reactivation_cancel(uuid, uuid, uuid) to service_role;
