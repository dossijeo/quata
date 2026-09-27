do $rollback_guard$
begin
  if to_regclass('public.account_deactivation_operations') is not null
     and exists (
       select 1 from public.account_deactivation_operations
        where state in ('database_applied', 'compensating', 'reactivating')
     ) then
    raise exception 'account lifecycle transition is still open; rollback refused' using errcode = '55000';
  end if;
end;
$rollback_guard$;

drop trigger if exists quata_web_push_subscriptions_active_owner_guard on public.web_push_subscriptions;
drop trigger if exists quata_web_client_sessions_active_owner_guard on public.web_client_sessions;
drop trigger if exists quata_push_tokens_active_owner_guard on public.push_tokens;
drop trigger if exists quata_community_profiles_deactivation_guard on public.community_profiles;
drop function if exists public.quata_guard_deactivation_reactivation();
drop function if exists public.quata_guard_active_delivery_owner();
drop function if exists public.quata_account_deactivation_complete(uuid, uuid, uuid);
drop function if exists public.quata_account_deactivation_compensate(uuid, uuid, uuid);
drop function if exists public.quata_account_deactivate(uuid, uuid, uuid);
drop function if exists public.quata_account_reactivation_begin(uuid, uuid);
drop function if exists public.quata_account_reactivation_complete(uuid, uuid, uuid);
drop function if exists public.quata_account_reactivation_cancel(uuid, uuid, uuid);
drop table if exists public.account_deactivation_operations;

CREATE OR REPLACE FUNCTION public.quata_account_deactivate(p_profile_id uuid, p_auth_user_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'auth'
AS $function$
declare
  v_updated integer;
begin
  update public.community_profiles
     set account_status = 'deactivated',
         deactivated_at = now(),
         deactivated_auth_user_id = p_auth_user_id,
         auth_user_id = null
   where id = p_profile_id
     and auth_user_id = p_auth_user_id
     and account_status = 'active';
  get diagnostics v_updated = row_count;
  if v_updated <> 1 then
    raise exception 'active account identity mismatch' using errcode = '42501';
  end if;

  delete from public.push_tokens
   where user_id = p_profile_id or auth_user_id = p_auth_user_id;

  return jsonb_build_object('ok', true, 'profile_id', p_profile_id);
end;
$function$;

revoke all on function public.quata_account_deactivate(uuid, uuid) from public, anon, authenticated;
grant execute on function public.quata_account_deactivate(uuid, uuid) to service_role;
