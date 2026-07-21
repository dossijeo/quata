-- Reversible account deactivation and irreversible self-service data deletion.
-- The Edge Function is the only caller of the administrative RPCs below.

alter table public.community_profiles
    add column if not exists account_status text not null default 'active',
    add column if not exists deactivated_at timestamptz;

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'community_profiles_account_status_check'
    ) then
        alter table public.community_profiles
            add constraint community_profiles_account_status_check
            check (account_status in ('active', 'deactivated'));
    end if;
end $$;

create index if not exists community_profiles_account_status_idx
    on public.community_profiles(account_status);

create table if not exists public.account_deletion_requests (
    auth_user_id uuid primary key references auth.users(id) on delete cascade,
    profile_id uuid not null,
    started_at timestamptz not null default now(),
    assets_removed_at timestamptz,
    database_deleted_at timestamptz
);

alter table public.account_deletion_requests enable row level security;
revoke all on table public.account_deletion_requests from public, anon, authenticated;
grant all on table public.account_deletion_requests to service_role;

create or replace function public.quata_chat_auth_profile_id()
returns uuid
language sql
stable
security definer
set search_path = public, auth
as $$
    select cp.id
      from public.community_profiles cp
     where auth.uid() is not null
       and cp.account_status = 'active'
       and (cp.id = auth.uid() or cp.auth_user_id = auth.uid())
     limit 1
$$;

-- An authenticated JWT that is no longer linked to a profile must never fall
-- through to the old anonymous compatibility path. This makes deactivation
-- effective immediately for every chat/moderation RPC that uses this resolver.
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

    -- Kept only for legacy anonymous clients. Deactivated profiles are never
    -- accepted through this compatibility path.
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

create or replace function public.quata_account_deactivate(
    p_profile_id uuid,
    p_auth_user_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_updated integer;
begin
    update public.community_profiles
       set account_status = 'deactivated',
           deactivated_at = now(),
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
$$;

create or replace function public.quata_account_collect_deletion_assets(
    p_profile_id uuid,
    p_auth_user_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_urls jsonb;
    v_storage jsonb;
begin
    if not exists (
        select 1 from public.community_profiles
         where id = p_profile_id
           and auth_user_id = p_auth_user_id
           and account_status = 'active'
    ) then
        raise exception 'active account identity mismatch' using errcode = '42501';
    end if;

    with private_thread_ids as (
        select pt.thread_id
          from public.chat_private_threads pt
         where pt.profile_low_id = p_profile_id or pt.profile_high_id = p_profile_id
        union
        select cp.thread_id
          from public.chat_participants cp
          join public.chat_threads ct on ct.id = cp.thread_id and ct.type = 'private'
         where cp.profile_id = p_profile_id
    ), private_chat_ids as (
        select c.id
          from public.community_private_chats c
         where p_profile_id in (c.user_low_id, c.user_high_id, c.requester_profile_id, c.target_profile_id)
    ), asset_urls as (
        select cp.image_url as url from public.community_posts cp
         where (cp.profile_id = p_profile_id or cp.author_id = p_profile_id) and cp.image_url is not null
        union
        select cp.video_url from public.community_posts cp
         where (cp.profile_id = p_profile_id or cp.author_id = p_profile_id) and cp.video_url is not null
        union
        select op.media_url from public.official_posts op
         where op.profile_id = p_profile_id and op.media_url is not null
        union
        select ca.file_url from public.chat_attachments ca
         where (ca.uploaded_by_profile_id = p_profile_id or ca.thread_id in (select thread_id from private_thread_ids))
           and ca.file_url is not null
        union
        select pm.attachment_url from public.community_private_messages pm
         where pm.chat_id in (select id from private_chat_ids) and pm.attachment_url is not null
        union
        select cp.avatar_url from public.community_profiles cp
         where cp.id = p_profile_id and cp.avatar_url is not null
        union
        select cp.avatar from public.community_profiles cp
         where cp.id = p_profile_id and cp.avatar is not null
        union
        select pm.media_url from public.private_messages pm
         where pm.media_url is not null
           and (
             pm.sender_id in (p_profile_id, p_auth_user_id)
             or pm.conversation_id in (
                 select pcp.conversation_id from public.private_conversation_participants pcp
                  where pcp.user_id in (p_profile_id, p_auth_user_id)
             )
           )
        union
        select qsp.photo_url from public.quata_services_professionals qsp
         where qsp.user_id in (p_profile_id, p_auth_user_id) and qsp.photo_url is not null
    )
    select coalesce(jsonb_agg(url), '[]'::jsonb) into v_urls
      from (select distinct url from asset_urls where nullif(btrim(url), '') is not null) q;

    with private_thread_ids as (
        select pt.thread_id
          from public.chat_private_threads pt
         where pt.profile_low_id = p_profile_id or pt.profile_high_id = p_profile_id
        union
        select cp.thread_id
          from public.chat_participants cp
          join public.chat_threads ct on ct.id = cp.thread_id and ct.type = 'private'
         where cp.profile_id = p_profile_id
    )
    select coalesce(
        jsonb_agg(jsonb_build_object('bucket', storage_bucket, 'path', storage_path)),
        '[]'::jsonb
    ) into v_storage
      from (
        select distinct coalesce(ca.storage_bucket, 'chat-attachments') as storage_bucket,
                        ca.storage_path
          from public.chat_attachments ca
         where (ca.uploaded_by_profile_id = p_profile_id or ca.thread_id in (select thread_id from private_thread_ids))
           and nullif(btrim(ca.storage_path), '') is not null
      ) q;

    return jsonb_build_object(
        'profile_id', p_profile_id,
        'urls', v_urls,
        'storage_objects', v_storage
    );
end;
$$;

create or replace function public.quata_account_delete_data(
    p_profile_id uuid,
    p_auth_user_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_thread_id bigint;
begin
    perform 1
      from public.community_profiles
     where id = p_profile_id
       and auth_user_id = p_auth_user_id
       and account_status = 'active'
     for update;
    if not found then
        raise exception 'active account identity mismatch' using errcode = '42501';
    end if;

    create temporary table quata_deleted_account_private_threads(thread_id bigint primary key) on commit drop;
    insert into quata_deleted_account_private_threads(thread_id)
    select pt.thread_id
      from public.chat_private_threads pt
     where pt.profile_low_id = p_profile_id or pt.profile_high_id = p_profile_id
    union
    select cp.thread_id
      from public.chat_participants cp
      join public.chat_threads ct on ct.id = cp.thread_id and ct.type = 'private'
     where cp.profile_id = p_profile_id;

    create temporary table quata_deleted_account_affected_threads(thread_id bigint primary key) on commit drop;
    insert into quata_deleted_account_affected_threads(thread_id)
    select cp.thread_id from public.chat_participants cp where cp.profile_id = p_profile_id
    union
    select cm.thread_id from public.chat_messages cm where cm.sender_profile_id = p_profile_id;

    -- Complete one-to-one conversations are removed for both parties.
    delete from public.chat_threads
     where id in (select thread_id from quata_deleted_account_private_threads);

    delete from public.community_private_chats c
     where p_profile_id in (c.user_low_id, c.user_high_id, c.requester_profile_id, c.target_profile_id);

    -- Preserve group, wall and SOS history belonging to other people. A thread
    -- created by the departing account receives another active participant as
    -- creator; empty non-wall threads can safely disappear.
    update public.chat_threads ct
       set created_by_profile_id = (
          select cp.profile_id
            from public.chat_participants cp
            join public.community_profiles candidate on candidate.id = cp.profile_id
           where cp.thread_id = ct.id
             and cp.profile_id <> p_profile_id
             and cp.left_at is null
             and not cp.is_deleted
             and candidate.account_status = 'active'
           order by case cp.role when 'owner' then 0 when 'moderator' then 1 else 2 end,
                    cp.joined_at,
                    cp.profile_id
           limit 1
      )
     where ct.created_by_profile_id = p_profile_id
       and ct.type <> 'private'
       and exists (
          select 1
            from public.chat_participants cp
            join public.community_profiles candidate on candidate.id = cp.profile_id
           where cp.thread_id = ct.id
             and cp.profile_id <> p_profile_id
             and cp.left_at is null
             and not cp.is_deleted
             and candidate.account_status = 'active'
       );

    -- A wall chat must not be lost just because its original creator leaves.
    update public.chat_threads ct
       set created_by_profile_id = (
          select cm.profile_id
            from public.community_members cm
            join public.community_profiles candidate on candidate.id = cm.profile_id
           where cm.wall_id = ct.community_id
             and cm.profile_id <> p_profile_id
             and candidate.account_status = 'active'
           order by cm.created_at, cm.profile_id
           limit 1
      )
     where ct.created_by_profile_id = p_profile_id
       and ct.type = 'wall'
       and exists (
          select 1
            from public.community_members cm
            join public.community_profiles candidate on candidate.id = cm.profile_id
           where cm.wall_id = ct.community_id
             and cm.profile_id <> p_profile_id
             and candidate.account_status = 'active'
       );

    delete from public.chat_threads ct
     where ct.created_by_profile_id = p_profile_id
       and ct.type <> 'wall';

    if exists (select 1 from public.chat_threads where created_by_profile_id = p_profile_id) then
        raise exception 'could not transfer every shared conversation';
    end if;

    -- Legacy private-conversation tables are not linked to community_profiles.
    if to_regclass('public.private_conversation_participants') is not null
       and to_regclass('public.private_conversations') is not null then
        execute $sql$
            delete from public.private_conversations pc
             where pc.id in (
                 select pcp.conversation_id
                   from public.private_conversation_participants pcp
                  where pcp.user_id = $1 or pcp.user_id = $2
             )
                or pc.created_by = $1
                or pc.created_by = $2
        $sql$ using p_profile_id, p_auth_user_id;
    end if;

    if to_regclass('public.private_messages') is not null then
        execute 'delete from public.private_messages where sender_id = $1 or sender_id = $2'
            using p_profile_id, p_auth_user_id;
    end if;

    if to_regclass('public.private_chats') is not null then
        execute 'delete from public.private_chats where sender_id = $1 or sender_id = $2'
            using p_profile_id::text, p_auth_user_id::text;
    end if;

    if to_regclass('public.emergency_alerts') is not null then
        execute 'delete from public.emergency_alerts where from_profile_id = $1 or to_profile_id = $1'
            using p_profile_id;
    end if;

    -- Legacy/prototype modules use UUID identity columns without foreign keys.
    -- Remove both possible identity forms before Auth itself is deleted.
    if to_regclass('public.quata_money_withdrawals') is not null then
        execute 'delete from public.quata_money_withdrawals where user_id = $1 or user_id = $2'
            using p_profile_id, p_auth_user_id;
    end if;
    if to_regclass('public.quata_money_accounts') is not null then
        execute 'delete from public.quata_money_accounts where user_id = $1 or user_id = $2'
            using p_profile_id, p_auth_user_id;
    end if;
    if to_regclass('public.quata_rentals') is not null then
        execute 'delete from public.quata_rentals where user_id = $1 or user_id = $2'
            using p_profile_id, p_auth_user_id;
    end if;
    if to_regclass('public.quata_services_professionals') is not null then
        execute 'delete from public.quata_services_professionals where user_id = $1 or user_id = $2'
            using p_profile_id, p_auth_user_id;
    end if;

    -- Cascades delete posts, authored group messages, attachments, memberships,
    -- follows, SOS data, notifications, push tokens and all other profile data.
    delete from public.community_profiles where id = p_profile_id;
    if found then
        for v_thread_id in
            select a.thread_id
              from quata_deleted_account_affected_threads a
              join public.chat_threads t on t.id = a.thread_id
        loop
            perform public.quata_chat_refresh_thread_summary(v_thread_id);
        end loop;
    else
        raise exception 'profile deletion failed';
    end if;

    return jsonb_build_object('ok', true, 'profile_id', p_profile_id);
end;
$$;

revoke all on function public.quata_account_deactivate(uuid, uuid) from public, anon, authenticated;
revoke all on function public.quata_account_collect_deletion_assets(uuid, uuid) from public, anon, authenticated;
revoke all on function public.quata_account_delete_data(uuid, uuid) from public, anon, authenticated;
grant execute on function public.quata_account_deactivate(uuid, uuid) to service_role;
grant execute on function public.quata_account_collect_deletion_assets(uuid, uuid) to service_role;
grant execute on function public.quata_account_delete_data(uuid, uuid) to service_role;
