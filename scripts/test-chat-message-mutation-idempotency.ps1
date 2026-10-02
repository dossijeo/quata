[CmdletBinding()]
param([string]$DockerImage = "postgres:17-alpine")

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$container = "quata-chat-message-mutation-$([guid]::NewGuid().ToString('N').Substring(0, 10))"
$password = "quata-test-only-$([guid]::NewGuid().ToString('N'))"

function Invoke-PsqlFile([string]$Path) {
    & docker exec -e "PGPASSWORD=$password" $container psql -U postgres -d postgres -X -v ON_ERROR_STOP=1 -q -f $Path
    if ($LASTEXITCODE -ne 0) { throw "psql_failed:$Path" }
}

function Invoke-PsqlText([string]$Sql, [string]$Label) {
    $Sql | & docker exec -i -e "PGPASSWORD=$password" $container psql -U postgres -d postgres -X -v ON_ERROR_STOP=1 -q
    if ($LASTEXITCODE -ne 0) { throw "psql_failed:$Label" }
}

try {
    & docker run --detach --rm --name $container --env "POSTGRES_PASSWORD=$password" --volume "${root}:/workspace:ro" $DockerImage | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "postgres_container_start_failed" }
    $deadline = (Get-Date).AddSeconds(60)
    do {
        Start-Sleep -Milliseconds 500
        & docker exec -e "PGPASSWORD=$password" $container pg_isready -U postgres -d postgres *> $null
        $ready = $LASTEXITCODE -eq 0
    } while (-not $ready -and (Get-Date) -lt $deadline)
    if (-not $ready) { throw "postgres_container_not_ready" }

    $testSql = Get-Content -Raw -LiteralPath (Join-Path $root "scripts/sql/chat-message-mutation-idempotency.test.sql")
    $assertionStart = $testSql.IndexOf("do `$`$")
    if ($assertionStart -lt 1) { throw "mutation_test_fixture_split_missing" }
    Invoke-PsqlText $testSql.Substring(0, $assertionStart) "fixture"
    Invoke-PsqlFile "/workspace/supabase/migrations/20261002003000_chat_message_mutation_idempotency.sql"
    Invoke-PsqlText $testSql.Substring($assertionStart) "assertions"
    Invoke-PsqlFile "/workspace/supabase/rollbacks/20261002003000_chat_message_mutation_idempotency.rollback.sql"
    $rollbackState = (& docker exec -e "PGPASSWORD=$password" $container psql -U postgres -d postgres -X -At -v ON_ERROR_STOP=1 -c "select (to_regclass('public.chat_message_mutation_receipts') is null)::text || '|' || (to_regprocedure('public.quata_chat_edit_message_v2(uuid,bigint,bigint,text,text)') is null)::text || '|' || (to_regprocedure('public.quata_chat_delete_messages_v2(uuid,bigint,bigint[],text)') is null)::text || '|' || (md5(pg_get_functiondef('public.quata_chat_edit_message(uuid,bigint,bigint,text)'::regprocedure))=(select value from public.legacy_function_fingerprint where name='edit'))::text || '|' || (md5(pg_get_functiondef('public.quata_chat_delete_messages(uuid,bigint,bigint[])'::regprocedure))=(select value from public.legacy_function_fingerprint where name='delete'))::text").Trim()
    if ($LASTEXITCODE -ne 0 -or $rollbackState -ne "true|true|true|true|true") { throw "mutation_rollback_postcondition_failed:$rollbackState" }
} finally {
    & docker rm -f $container *> $null
}

Write-Output "CHAT_MESSAGE_MUTATION_IDEMPOTENCY_POSTGRES_PASS"
