begin;

-- The published Android v32 bundle calls these RPCs with the anonymous role and
-- its embedded public key. Keep only that observed signature behind the
-- existing kill switch; every current client carries either an authenticated
-- Supabase JWT or x-quata-client-generation=android-auth-boundary-v1.
create or replace function public.quata_legacy_android_v32_chat_request_allowed()
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    with request_context as (
        select
            nullif(current_setting('request.jwt.claim.role', true), '') as jwt_role,
            upper(coalesce(nullif(current_setting('request.method', true), ''), '')) as method,
            coalesce(nullif(current_setting('request.path', true), ''), '') as path,
            coalesce(nullif(current_setting('request.headers', true), '')::jsonb, '{}'::jsonb) as headers
    )
    select coalesce((
        select compatibility.enabled
           and (
               context.jwt_role = 'anon'
               or (
                   context.jwt_role is null
                   and coalesce(context.headers ->> 'apikey', '') like 'sb_publishable_%'
               )
           )
           and context.method = 'POST'
           and context.path in (
               '/rpc/quata_chat_add_participants',
               '/rpc/quata_chat_block_participant',
               '/rpc/quata_chat_cleanup_empty_private_thread',
               '/rpc/quata_chat_delete_messages',
               '/rpc/quata_chat_delete_thread',
               '/rpc/quata_chat_demote_moderator',
               '/rpc/quata_chat_edit_message',
               '/rpc/quata_chat_forward_message',
               '/rpc/quata_chat_get_favorites',
               '/rpc/quata_chat_get_inbox',
               '/rpc/quata_chat_get_or_create_private_thread',
               '/rpc/quata_chat_get_thread',
               '/rpc/quata_chat_leave_thread',
               '/rpc/quata_chat_list_shared_attachments',
               '/rpc/quata_chat_mark_messages_state',
               '/rpc/quata_chat_mark_thread_read',
               '/rpc/quata_chat_match_registered_contacts',
               '/rpc/quata_chat_open_community_thread',
               '/rpc/quata_chat_promote_moderator',
               '/rpc/quata_chat_register_attachment',
               '/rpc/quata_chat_remove_participant',
               '/rpc/quata_chat_restore_thread',
               '/rpc/quata_chat_search_conversation_candidates',
               '/rpc/quata_chat_send_message',
               '/rpc/quata_chat_send_sos',
               '/rpc/quata_chat_set_favorite',
               '/rpc/quata_chat_set_member_invites_enabled',
               '/rpc/quata_chat_set_muted',
               '/rpc/quata_chat_start_thread'
           )
           and lower(coalesce(context.headers ->> 'user-agent', '')) = 'okhttp/4.12.0'
           and context.headers ->> 'x-quata-client-generation' is null
           and context.headers ->> 'origin' is null
           and context.headers ->> 'referer' is null
           and lower(coalesce(context.headers ->> 'content-profile', 'public')) = 'public'
           and coalesce(context.headers ->> 'apikey', '') <> ''
           and lower(coalesce(context.headers ->> 'authorization', '')) like 'bearer %'
        from public.quata_legacy_android_v32_compatibility compatibility
        cross join request_context context
        where compatibility.singleton
    ), false);
$$;

revoke all on function public.quata_legacy_android_v32_chat_request_allowed() from public;
grant execute on function public.quata_legacy_android_v32_chat_request_allowed() to anon, authenticated;

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

    if p_actor_profile_id is not null
       and public.quata_legacy_android_v32_chat_request_allowed() then
        if exists (
            select 1 from public.community_profiles
             where id = p_actor_profile_id and account_status = 'active'
        ) then
            return p_actor_profile_id;
        end if;
        raise exception 'actor profile does not exist or is inactive'
            using errcode = '42501';
    end if;

    raise exception 'authenticated chat actor is required' using errcode = '42501';
end;
$$;

commit;
