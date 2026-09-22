with policy_state as (
  select jsonb_agg(jsonb_build_object(
    'name', p.polname,
    'command', p.polcmd::text,
    'permissive', p.polpermissive,
    'roles', (
      select jsonb_agg(r.rolname order by r.rolname)
      from unnest(p.polroles) role_oid
      join pg_roles r on r.oid = role_oid
    ),
    'using', coalesce(pg_get_expr(p.polqual, p.polrelid, true), ''),
    'withCheck', coalesce(pg_get_expr(p.polwithcheck, p.polrelid, true), '')
  ) order by p.polname) metadata
  from pg_policy p
  join pg_class c on c.oid = p.polrelid
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'storage'
    and c.relname = 'objects'
    and p.polname in (
      'chat_attachments_storage_read',
      'chat_attachments_storage_insert',
      'chat_attachments_storage_update_own',
      'chat_attachments_storage_delete_own'
    )
), function_state as (
  select jsonb_build_object(
    'name', p.proname,
    'args', pg_get_function_identity_arguments(p.oid),
    'result', pg_get_function_result(p.oid),
    'language', l.lanname,
    'securityDefiner', p.prosecdef,
    'volatility', p.provolatile::text,
    'config', coalesce(p.proconfig, array[]::text[]),
    'normalizedDefinitionMd5', md5(replace(pg_get_functiondef(p.oid), E'\r\n', E'\n')),
    'directAnonExecute', exists (
      select 1 from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
      join pg_roles r on r.oid = acl.grantee
      where r.rolname = 'anon' and acl.privilege_type = 'EXECUTE'
    ),
    'directAuthenticatedExecute', exists (
      select 1 from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
      join pg_roles r on r.oid = acl.grantee
      where r.rolname = 'authenticated' and acl.privilege_type = 'EXECUTE'
    ),
    'acl', p.proacl
  ) metadata
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  join pg_language l on l.oid = p.prolang
  where n.nspname = 'public'
    and p.oid = to_regprocedure('public.quata_chat_list_shared_attachments(uuid,uuid,bigint,uuid,integer,integer,text)')
), exact_state as (
  select
    coalesce(jsonb_array_length(p.metadata), 0) = 4
      and exists (
        select 1 from jsonb_array_elements(p.metadata) item
        where item->>'name' = 'chat_attachments_storage_read'
          and item->>'command' = 'r'
          and (item->>'permissive')::boolean
          and item->'roles' = '["anon","authenticated"]'::jsonb
          and item->>'using' = 'bucket_id = ''chat-attachments''::text'
          and item->>'withCheck' = ''
      )
      and exists (
        select 1 from jsonb_array_elements(p.metadata) item
        where item->>'name' = 'chat_attachments_storage_insert'
          and item->>'command' = 'a'
          and (item->>'permissive')::boolean
          and item->'roles' = '["authenticated"]'::jsonb
          and item->>'using' = ''
          and item->>'withCheck' = 'bucket_id = ''chat-attachments''::text AND (storage.foldername(name))[1] = quata_chat_auth_profile_id()::text'
      )
      and exists (
        select 1 from jsonb_array_elements(p.metadata) item
        where item->>'name' = 'chat_attachments_storage_update_own'
          and item->>'command' = 'w'
          and (item->>'permissive')::boolean
          and item->'roles' = '["authenticated"]'::jsonb
          and item->>'using' = 'bucket_id = ''chat-attachments''::text AND (storage.foldername(name))[1] = quata_chat_auth_profile_id()::text'
          and item->>'withCheck' = 'bucket_id = ''chat-attachments''::text AND (storage.foldername(name))[1] = quata_chat_auth_profile_id()::text'
      )
      and exists (
        select 1 from jsonb_array_elements(p.metadata) item
        where item->>'name' = 'chat_attachments_storage_delete_own'
          and item->>'command' = 'd'
          and (item->>'permissive')::boolean
          and item->'roles' = '["authenticated"]'::jsonb
          and item->>'using' = 'bucket_id = ''chat-attachments''::text AND (storage.foldername(name))[1] = quata_chat_auth_profile_id()::text'
          and item->>'withCheck' = ''
      ) as policies_exact,
    f.metadata->>'name' = 'quata_chat_list_shared_attachments'
      and f.metadata->>'args' = 'p_actor_profile_id uuid, p_peer_profile_id uuid, p_thread_id bigint, p_community_id uuid, p_limit integer, p_offset integer, p_kind text'
      and f.metadata->>'result' = 'jsonb'
      and f.metadata->>'language' = 'plpgsql'
      and (f.metadata->>'securityDefiner')::boolean
      and f.metadata->>'volatility' = 'v'
      and f.metadata->'config' = '["search_path=public"]'::jsonb
      and f.metadata->>'normalizedDefinitionMd5' = 'eb2785d0843a52431a7b3e2e58a3bddc'
      and (f.metadata->>'directAnonExecute')::boolean
      and (f.metadata->>'directAuthenticatedExecute')::boolean
      as function_and_grant_exact
  from policy_state p cross join function_state f
)
select jsonb_build_object(
  'policies', policy_state.metadata,
  'function', function_state.metadata,
  'policiesExact', exact_state.policies_exact,
  'functionAndGrantExact', exact_state.function_and_grant_exact,
  'allEffectsExact', exact_state.policies_exact and exact_state.function_and_grant_exact
)
from policy_state cross join function_state cross join exact_state;
