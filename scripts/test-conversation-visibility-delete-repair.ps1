[CmdletBinding()]
param([string]$DockerImage = "postgres:17-alpine")

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$container = "quata-visibility-delete-" + [guid]::NewGuid().ToString("N").Substring(0, 12)
$password = [guid]::NewGuid().ToString("N")
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Invoke-Sql([string]$Sql) {
    $Sql | docker exec -i -e "PGPASSWORD=$password" $container psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres *> $null
    if ($LASTEXITCODE -ne 0) { throw "visibility_delete_repair_sql_failed" }
}

try {
    docker run -d --rm --name $container -e "POSTGRES_PASSWORD=$password" -v "${repo}:/workspace:ro" $DockerImage | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "visibility_delete_repair_container_failed" }
    $ready = $false
    foreach ($attempt in 1..30) {
        docker exec $container pg_isready -U postgres *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "visibility_delete_repair_database_not_ready" }

    Invoke-Sql @'
create table public.chat_messages(
    id bigint primary key,
    thread_id bigint not null
);
create table public.conversation_user_state(
    conversation_id bigint not null,
    user_id uuid not null,
    first_visible_message_id bigint references public.chat_messages(id) on delete set null,
    primary key(conversation_id, user_id)
);
insert into public.chat_messages(id, thread_id) values (10, 1), (20, 1), (30, 1), (40, 2), (50, 2);
insert into public.conversation_user_state(conversation_id, user_id, first_visible_message_id) values
    (1, '00000000-0000-0000-0000-000000000001', null),
    (2, '00000000-0000-0000-0000-000000000002', 40);
'@
    docker exec -e "PGPASSWORD=$password" $container psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres -f /workspace/supabase/migrations/20260927100000_conversation_visibility_delete_repair.sql *> $null
    if ($LASTEXITCODE -ne 0) { throw "visibility_delete_repair_migration_failed" }

    Invoke-Sql "delete from public.chat_messages where id=10;"
    $afterFirst = (docker exec -e "PGPASSWORD=$password" $container psql -X -At -U postgres -d postgres -c "select first_visible_message_id from public.conversation_user_state where conversation_id=1").Trim()
    if ($afterFirst -ne "20") { throw "visibility_delete_repair_single_delete_failed" }

    Invoke-Sql "delete from public.chat_messages where id in (20,30); delete from public.chat_messages where thread_id=2;"
    $remainingNulls = (docker exec -e "PGPASSWORD=$password" $container psql -X -At -U postgres -d postgres -c "select count(*) from public.conversation_user_state where first_visible_message_id is null").Trim()
    if ($remainingNulls -ne "2") { throw "visibility_delete_repair_last_message_failed" }

    docker exec -e "PGPASSWORD=$password" $container psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres -f /workspace/supabase/rollbacks/20260927100000_conversation_visibility_delete_repair.rollback.sql *> $null
    if ($LASTEXITCODE -ne 0) { throw "visibility_delete_repair_rollback_failed" }
    $objects = (docker exec -e "PGPASSWORD=$password" $container psql -X -At -U postgres -d postgres -c "select (to_regprocedure('public.quata_chat_repoint_visibility_before_message_delete()') is null and not exists(select 1 from pg_trigger where tgname='chat_messages_repoint_visibility_before_delete'))::text").Trim()
    if ($objects -ne "true") { throw "visibility_delete_repair_rollback_objects_failed" }

    Write-Output "conversation_visibility_delete_repair_test_passed"
}
finally {
    docker rm -f $container *> $null
}
