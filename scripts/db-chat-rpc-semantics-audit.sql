select jsonb_build_object(
  'functions', (
    select jsonb_agg(jsonb_build_object(
      'name',p.proname,
      'args',pg_get_function_identity_arguments(p.oid),
      'result',pg_get_function_result(p.oid),
      'md5',md5(replace(pg_get_functiondef(p.oid),E'\r\n',E'\n')),
      'securityDefiner',p.prosecdef,
      'volatility',p.provolatile::text,
      'config',coalesce(p.proconfig,array[]::text[]),
      'acl',p.proacl,
      'anonExecute',has_function_privilege('anon',p.oid,'execute'),
      'authenticatedExecute',has_function_privilege('authenticated',p.oid,'execute')
    ) order by p.proname)
    from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname='public' and p.proname in ('quata_chat_epoch_millis','quata_chat_profile_json','quata_chat_attachment_json','quata_chat_message_json','quata_chat_thread_json','quata_chat_get_thread','quata_chat_get_inbox','quata_chat_mark_thread_read','quata_chat_check_new','quata_chat_get_or_create_private_thread','quata_chat_start_thread','quata_chat_register_attachment','quata_chat_send_message','quata_chat_send_files','quata_chat_list_attachments','quata_chat_set_favorite','quata_chat_get_favorites','quata_chat_edit_message','quata_chat_delete_messages','quata_chat_forward_message','quata_chat_change_subject','quata_chat_set_muted','quata_chat_set_member_invites_enabled','quata_chat_add_participants','quata_chat_promote_moderator','quata_chat_remove_participant','quata_chat_block_participant','quata_chat_leave_thread','quata_chat_delete_thread','quata_chat_restore_thread','quata_chat_send_sos')
  ),
  'functionCount',(
    select count(*)::int from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname='public' and p.proname in ('quata_chat_epoch_millis','quata_chat_profile_json','quata_chat_attachment_json','quata_chat_message_json','quata_chat_thread_json','quata_chat_get_thread','quata_chat_get_inbox','quata_chat_mark_thread_read','quata_chat_check_new','quata_chat_get_or_create_private_thread','quata_chat_start_thread','quata_chat_register_attachment','quata_chat_send_message','quata_chat_send_files','quata_chat_list_attachments','quata_chat_set_favorite','quata_chat_get_favorites','quata_chat_edit_message','quata_chat_delete_messages','quata_chat_forward_message','quata_chat_change_subject','quata_chat_set_muted','quata_chat_set_member_invites_enabled','quata_chat_add_participants','quata_chat_promote_moderator','quata_chat_remove_participant','quata_chat_block_participant','quata_chat_leave_thread','quata_chat_delete_thread','quata_chat_restore_thread','quata_chat_send_sos')
  )
) observation;
