[CmdletBinding()]
param([string]$DockerImage = "postgres:17-alpine")

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$container = "quata-visibility-recurrence-" + [guid]::NewGuid().ToString("N").Substring(0, 10)
$password = [guid]::NewGuid().ToString("N")
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Invoke-Sql([string]$Sql) {
    $Sql | docker exec -i -e "PGPASSWORD=$password" $container psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres *> $null
    if ($LASTEXITCODE -ne 0) { throw "visibility_boundary_recurrence_sql_failed" }
}

try {
    docker run -d --rm --name $container -e "POSTGRES_PASSWORD=$password" -v "${repo}:/workspace:ro" $DockerImage | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "visibility_boundary_recurrence_container_failed" }
    $ready = $false
    foreach ($attempt in 1..30) {
        docker exec $container pg_isready -U postgres *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "visibility_boundary_recurrence_database_not_ready" }

    Invoke-Sql @'
create table public.chat_messages(
    id bigint primary key,
    thread_id bigint not null,
    sender_profile_id uuid not null,
    created_at timestamptz not null default now()
);
create table public.chat_participants(
    thread_id bigint not null,
    profile_id uuid not null,
    muted_at timestamptz,
    last_read_message_id bigint,
    left_at timestamptz,
    is_hidden boolean not null default false,
    is_deleted boolean not null default false,
    primary key(thread_id, profile_id)
);
create table public.conversation_user_state(
    conversation_id bigint not null,
    user_id uuid not null,
    deleted_at timestamptz,
    first_visible_message_id bigint references public.chat_messages(id) on delete set null,
    muted_at timestamptz,
    last_read_message_id bigint,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key(conversation_id, user_id)
);
create function public.quata_chat_first_message_id(p_thread_id bigint)
returns bigint language sql stable as $$
    select min(message.id) from public.chat_messages message where message.thread_id=p_thread_id
$$;

insert into public.chat_participants(thread_id, profile_id) values
    (1, '00000000-0000-0000-0000-000000000001'),
    (1, '00000000-0000-0000-0000-000000000002');
insert into public.chat_messages(id, thread_id, sender_profile_id, created_at) values
    (10, 1, '00000000-0000-0000-0000-000000000001', '2026-01-02T00:00:00Z');
insert into public.conversation_user_state(conversation_id, user_id, updated_at) values
    (1, '00000000-0000-0000-0000-000000000001', '2026-01-01T00:00:00Z'),
    (1, '00000000-0000-0000-0000-000000000002', '2026-01-03T00:00:00Z');
'@

    docker exec -e "PGPASSWORD=$password" $container psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres -f /workspace/supabase/rollbacks/20261001211000_chat_visibility_boundary_recurrence.rollback.sql *> $null
    if ($LASTEXITCODE -ne 0) { throw "visibility_boundary_recurrence_baseline_failed" }
    Invoke-Sql "create trigger aa_chat_messages_after_insert_reactivate_user_state after insert on public.chat_messages for each row execute function public.quata_chat_reactivate_user_state_for_message();"

    docker exec -e "PGPASSWORD=$password" $container psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres -f /workspace/supabase/migrations/20261001211000_chat_visibility_boundary_recurrence.sql *> $null
    if ($LASTEXITCODE -ne 0) { throw "visibility_boundary_recurrence_migration_failed" }

    $repair = (docker exec -e "PGPASSWORD=$password" $container psql -X -At -U postgres -d postgres -c "select string_agg((coalesce(first_visible_message_id,0)::text||':'||(deleted_at is not null)::text),',' order by user_id) from public.conversation_user_state where conversation_id=1").Trim()
    if ($repair -ne "10:false,0:true") { throw "visibility_boundary_recurrence_backfill_failed:$repair" }

    Invoke-Sql @'
insert into public.chat_participants(thread_id, profile_id) values
    (2, '00000000-0000-0000-0000-000000000003'),
    (2, '00000000-0000-0000-0000-000000000004');
insert into public.conversation_user_state(conversation_id, user_id) values
    (2, '00000000-0000-0000-0000-000000000003'),
    (2, '00000000-0000-0000-0000-000000000004');
insert into public.chat_messages(id, thread_id, sender_profile_id) values
    (20, 2, '00000000-0000-0000-0000-000000000003');
'@
    $initialized = (docker exec -e "PGPASSWORD=$password" $container psql -X -At -U postgres -d postgres -c "select count(*) from public.conversation_user_state where conversation_id=2 and first_visible_message_id=20 and deleted_at is null").Trim()
    if ($initialized -ne "2") { throw "non_sender_boundary_not_initialized" }
    $ambiguous = (docker exec -e "PGPASSWORD=$password" $container psql -X -At -U postgres -d postgres -c "select count(*) from public.conversation_user_state where conversation_id=1 and user_id='00000000-0000-0000-0000-000000000002' and first_visible_message_id is null and deleted_at is not null").Trim()
    if ($ambiguous -ne "1") { throw "ambiguous_boundary_not_tombstoned" }
    Invoke-Sql "insert into public.chat_messages(id, thread_id, sender_profile_id) values (11, 1, '00000000-0000-0000-0000-000000000001');"
    $reopened = (docker exec -e "PGPASSWORD=$password" $container psql -X -At -U postgres -d postgres -c "select count(*) from public.conversation_user_state where conversation_id=1 and user_id='00000000-0000-0000-0000-000000000002' and first_visible_message_id=11 and deleted_at is null").Trim()
    if ($reopened -ne "1") { throw "tombstone_not_reopened_at_new_message" }

    docker exec -e "PGPASSWORD=$password" $container psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres -f /workspace/supabase/rollbacks/20261001211000_chat_visibility_boundary_recurrence.rollback.sql *> $null
    if ($LASTEXITCODE -ne 0) { throw "visibility_boundary_recurrence_rollback_failed" }
    Invoke-Sql @'
insert into public.chat_participants(thread_id, profile_id) values
    (3, '00000000-0000-0000-0000-000000000005'),
    (3, '00000000-0000-0000-0000-000000000006');
insert into public.conversation_user_state(conversation_id, user_id) values
    (3, '00000000-0000-0000-0000-000000000005'),
    (3, '00000000-0000-0000-0000-000000000006');
insert into public.chat_messages(id, thread_id, sender_profile_id) values
    (30, 3, '00000000-0000-0000-0000-000000000005');
'@
    $rollbackState = (docker exec -e "PGPASSWORD=$password" $container psql -X -At -U postgres -d postgres -c "select count(*) from public.conversation_user_state where conversation_id=3 and first_visible_message_id is null").Trim()
    if ($rollbackState -ne "1") { throw "rollback_did_not_restore_previous_trigger_behavior" }

    Write-Output "chat_visibility_boundary_recurrence_postgres_pass"
}
finally {
    docker rm -f $container *> $null
}
