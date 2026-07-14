-- Keep inbox previews meaningful when the newest message contains only an attachment.

create or replace function public.quata_chat_refresh_thread_summary(p_thread_id bigint)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_last record;
begin
    select
        m.created_at,
        case
            when nullif(btrim(coalesce(m.body, '')), '') is not null then left(m.body, 500)
            else coalesce((
                select case
                    when lower(coalesce(a.mime_type, '')) like 'audio/%'
                        then '[QUATA_ATTACHMENT:voice_note]'
                    when lower(coalesce(a.mime_type, '')) like 'image/%'
                        then '[QUATA_ATTACHMENT:photo]'
                    when lower(coalesce(a.mime_type, '')) like 'video/%'
                        then '[QUATA_ATTACHMENT:video]'
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
    into v_last
    from public.chat_messages m
    where m.thread_id = p_thread_id
      and m.deleted_at is null
    order by m.created_at desc, m.id desc
    limit 1;

    update public.chat_threads t
    set last_message_at = v_last.created_at,
        last_message_preview = coalesce(v_last.preview, ''),
        updated_at = now()
    where t.id = p_thread_id;
end;
$$;

create or replace function public.quata_chat_attachment_after_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_thread_id bigint;
begin
    v_thread_id := case when tg_op = 'DELETE' then old.thread_id else new.thread_id end;
    perform public.quata_chat_refresh_thread_summary(v_thread_id);

    if tg_op = 'UPDATE' and old.thread_id is distinct from new.thread_id then
        perform public.quata_chat_refresh_thread_summary(old.thread_id);
    end if;

    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;

drop trigger if exists chat_attachments_after_change_refresh_thread on public.chat_attachments;
create trigger chat_attachments_after_change_refresh_thread
after insert or update or delete on public.chat_attachments
for each row
execute function public.quata_chat_attachment_after_change();

do $$
declare
    v_thread_id bigint;
begin
    for v_thread_id in
        select distinct t.id
        from public.chat_threads t
        join public.chat_messages m on m.thread_id = t.id and m.deleted_at is null
        where nullif(btrim(t.last_message_preview), '') is null
    loop
        perform public.quata_chat_refresh_thread_summary(v_thread_id);
    end loop;
end;
$$;
