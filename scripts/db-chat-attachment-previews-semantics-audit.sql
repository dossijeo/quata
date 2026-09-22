with functions_state as (
  select jsonb_agg(jsonb_build_object(
    'name', p.proname,
    'args', pg_get_function_identity_arguments(p.oid),
    'result', pg_get_function_result(p.oid),
    'language', l.lanname,
    'securityDefiner', p.prosecdef,
    'volatility', p.provolatile::text,
    'config', coalesce(p.proconfig, array[]::text[]),
    'normalizedDefinitionMd5', md5(replace(pg_get_functiondef(p.oid), E'\r\n', E'\n')),
    'acl', p.proacl
  ) order by p.proname) metadata
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  join pg_language l on l.oid = p.prolang
  where n.nspname = 'public'
    and p.proname in ('quata_chat_refresh_thread_summary', 'quata_chat_attachment_after_change')
), trigger_state as (
  select jsonb_agg(jsonb_build_object(
    'name', t.tgname,
    'definition', pg_get_triggerdef(t.oid, true),
    'enabled', t.tgenabled::text,
    'function', p.proname
  )) metadata
  from pg_trigger t
  join pg_class c on c.oid = t.tgrelid
  join pg_namespace n on n.oid = c.relnamespace
  join pg_proc p on p.oid = t.tgfoid
  where n.nspname = 'public'
    and c.relname = 'chat_attachments'
    and t.tgname = 'chat_attachments_after_change_refresh_thread'
    and not t.tgisinternal
), backfill_state as (
  select
    count(*) as selected_row_count,
    count(*) filter (
      where t.last_message_at is distinct from expected.created_at
         or t.last_message_preview is distinct from coalesce(expected.preview, '')
    ) as mismatch_count
  from public.chat_threads t
  cross join lateral (
    select
      m.created_at,
      case
        when nullif(btrim(coalesce(m.body, '')), '') is not null then left(m.body, 500)
        else coalesce((
          select case
            when lower(coalesce(a.mime_type, '')) like 'audio/%' then '[QUATA_ATTACHMENT:voice_note]'
            when lower(coalesce(a.mime_type, '')) like 'image/%' then '[QUATA_ATTACHMENT:photo]'
            when lower(coalesce(a.mime_type, '')) like 'video/%' then '[QUATA_ATTACHMENT:video]'
            when lower(coalesce(a.mime_type, '')) = 'application/pdf'
              or lower(coalesce(a.mime_type, '')) like 'text/%'
              or lower(coalesce(a.mime_type, '')) like 'application/msword%'
              or lower(coalesce(a.mime_type, '')) like 'application/rtf%'
              or lower(coalesce(a.mime_type, '')) like 'application/vnd.ms-%'
              or lower(coalesce(a.mime_type, '')) like 'application/vnd.openxmlformats-officedocument.%'
              or lower(coalesce(a.mime_type, '')) like 'application/vnd.oasis.opendocument.%'
              then '[QUATA_ATTACHMENT:document]'
            else '[QUATA_ATTACHMENT:file]'
          end
          from public.chat_attachments a
          where a.message_id = m.id
          order by a.id
          limit 1
        ), '')
      end as preview
    from public.chat_messages m
    where m.thread_id = t.id
      and m.deleted_at is null
    order by m.created_at desc, m.id desc
    limit 1
  ) expected
  where nullif(btrim(t.last_message_preview), '') is null
), exact_state as (
  select
    coalesce(jsonb_array_length(f.metadata), 0) = 2
      and exists (
        select 1 from jsonb_array_elements(f.metadata) item
        where item->>'name' = 'quata_chat_attachment_after_change'
          and item->>'args' = ''
          and item->>'result' = 'trigger'
          and item->>'language' = 'plpgsql'
          and (item->>'securityDefiner')::boolean
          and item->>'volatility' = 'v'
          and item->'config' = '["search_path=public"]'::jsonb
          and item->>'normalizedDefinitionMd5' = '48ebd66804f0ff396ceb20b18c7fb6b6'
      )
      and exists (
        select 1 from jsonb_array_elements(f.metadata) item
        where item->>'name' = 'quata_chat_refresh_thread_summary'
          and item->>'args' = 'p_thread_id bigint'
          and item->>'result' = 'void'
          and item->>'language' = 'plpgsql'
          and (item->>'securityDefiner')::boolean
          and item->>'volatility' = 'v'
          and item->'config' = '["search_path=public"]'::jsonb
          and item->>'normalizedDefinitionMd5' = '1fba49b7af9ee72331bbc347b11974d3'
      ) as functions_exact,
    coalesce(jsonb_array_length(t.metadata), 0) = 1
      and t.metadata->0->>'name' = 'chat_attachments_after_change_refresh_thread'
      and t.metadata->0->>'enabled' = 'O'
      and t.metadata->0->>'function' = 'quata_chat_attachment_after_change'
      and t.metadata->0->>'definition' = 'CREATE TRIGGER chat_attachments_after_change_refresh_thread AFTER INSERT OR DELETE OR UPDATE ON chat_attachments FOR EACH ROW EXECUTE FUNCTION quata_chat_attachment_after_change()'
      as trigger_exact
  from functions_state f
  cross join trigger_state t
)
select jsonb_build_object(
  'functions', functions_state.metadata,
  'triggers', trigger_state.metadata,
  'selectedRowCount', backfill_state.selected_row_count,
  'backfillMismatchCount', backfill_state.mismatch_count,
  'allSelectedSummariesCurrent', backfill_state.mismatch_count = 0,
  'functionsExact', exact_state.functions_exact,
  'triggerExact', exact_state.trigger_exact,
  'allEffectsExact', exact_state.functions_exact
    and exact_state.trigger_exact
    and backfill_state.mismatch_count = 0
)
from functions_state
cross join trigger_state
cross join backfill_state
cross join exact_state;
