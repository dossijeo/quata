-- Read-only semantic audit for Chat message and attachment push triggers.
-- Emits catalogue metadata only; it never invokes the trigger function.
with target_function as (
  select
    p.proname as function_name,
    pg_get_function_identity_arguments(p.oid) as identity_arguments,
    pg_get_function_result(p.oid) as result_type,
    l.lanname as language_name,
    p.prosecdef as security_definer,
    p.provolatile::text as volatility,
    coalesce(p.proconfig, array[]::text[]) as configuration,
    md5(pg_get_functiondef(p.oid)) as definition_md5
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  join pg_language l on l.oid = p.prolang
  where n.nspname = 'public'
    and p.proname = 'quata_enqueue_chat_push'
    and pg_get_function_identity_arguments(p.oid) = ''
), function_mismatches as (
  select count(*) as mismatch_count
  from target_function
  where result_type is distinct from 'trigger'
     or language_name is distinct from 'plpgsql'
     or security_definer is distinct from true
     or volatility is distinct from 'v'
     or configuration is distinct from array['search_path=public, extensions']::text[]
     or definition_md5 is distinct from 'e169f79cff681fe701d7eec34cf16dd0'
), target_triggers as (
  select
    table_class.relname as table_name,
    t.tgname as trigger_name,
    pg_get_triggerdef(t.oid) as definition,
    t.tgenabled::text as enabled,
    p.proname as function_name
  from pg_trigger t
  join pg_class table_class on table_class.oid = t.tgrelid
  join pg_namespace n on n.oid = table_class.relnamespace
  join pg_proc p on p.oid = t.tgfoid
  where not t.tgisinternal
    and n.nspname = 'public'
    and (
      (table_class.relname = 'chat_messages' and t.tgname = 'chat_messages_after_insert_push')
      or (table_class.relname = 'chat_attachments' and t.tgname = 'chat_attachments_after_link_push')
    )
), trigger_mismatches as (
  select count(*) as mismatch_count
  from target_triggers
  where enabled is distinct from 'O'
     or function_name is distinct from 'quata_enqueue_chat_push'
     or (
       trigger_name = 'chat_messages_after_insert_push'
       and definition is distinct from
         $trigger$CREATE TRIGGER chat_messages_after_insert_push AFTER INSERT ON public.chat_messages FOR EACH ROW WHEN ((NULLIF(btrim(COALESCE(new.body, ''::text)), ''::text) IS NOT NULL)) EXECUTE FUNCTION quata_enqueue_chat_push()$trigger$
     )
     or (
       trigger_name = 'chat_attachments_after_link_push'
       and definition is distinct from
         $trigger$CREATE TRIGGER chat_attachments_after_link_push AFTER INSERT OR UPDATE OF message_id ON public.chat_attachments FOR EACH ROW WHEN ((new.message_id IS NOT NULL)) EXECUTE FUNCTION quata_enqueue_chat_push()$trigger$
     )
)
select jsonb_build_object(
  'functions', (select coalesce(jsonb_agg(to_jsonb(f)), '[]'::jsonb) from target_function f),
  'functionCount', (select count(*) from target_function),
  'functionMismatchCount',
    (select mismatch_count from function_mismatches)
    + case when (select count(*) from target_function) = 1 then 0 else 1 end,
  'triggers', (select coalesce(jsonb_agg(to_jsonb(t) order by table_name), '[]'::jsonb) from target_triggers t),
  'triggerCount', (select count(*) from target_triggers),
  'triggerMismatchCount',
    (select mismatch_count from trigger_mismatches)
    + case when (select count(*) from target_triggers) = 2 then 0 else 1 end,
  'allEffectsExact',
    (select count(*) from target_function) = 1
    and (select mismatch_count from function_mismatches) = 0
    and (select count(*) from target_triggers) = 2
    and (select mismatch_count from trigger_mismatches) = 0
);
