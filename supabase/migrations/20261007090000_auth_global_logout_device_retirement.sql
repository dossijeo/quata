create or replace function public.quata_retire_all_device_endpoints(p_auth_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_native_tokens integer := 0;
    v_web_subscriptions integer := 0;
    v_web_sessions integer := 0;
    v_now timestamptz := now();
begin
    if p_auth_user_id is null then
        raise exception 'auth user is required' using errcode = '22023';
    end if;

    update public.push_tokens
    set disabled_at = v_now,
        last_error_text = 'Disabled on global logout',
        updated_at = v_now
    where auth_user_id = p_auth_user_id
      and disabled_at is null;
    get diagnostics v_native_tokens = row_count;

    update public.web_push_subscriptions
    set disabled_at = v_now,
        last_error_text = 'Disabled on global logout',
        updated_at = v_now
    where auth_user_id = p_auth_user_id
      and disabled_at is null;
    get diagnostics v_web_subscriptions = row_count;

    update public.web_client_sessions
    set revoked_at = v_now,
        updated_at = v_now
    where auth_user_id = p_auth_user_id
      and revoked_at is null;
    get diagnostics v_web_sessions = row_count;

    return jsonb_build_object(
        'result', true,
        'native_tokens_disabled', v_native_tokens,
        'web_subscriptions_disabled', v_web_subscriptions,
        'web_sessions_revoked', v_web_sessions
    );
end;
$$;

revoke all on function public.quata_retire_all_device_endpoints(uuid) from public, anon, authenticated;
grant execute on function public.quata_retire_all_device_endpoints(uuid) to service_role;
