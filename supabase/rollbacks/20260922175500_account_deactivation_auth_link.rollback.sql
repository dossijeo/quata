-- Restore the exact pre-deployment function definition and ACL. The migration is a
-- semantic no-op against this observed state, so rollback intentionally preserves it.

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
