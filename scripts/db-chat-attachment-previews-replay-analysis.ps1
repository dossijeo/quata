[CmdletBinding()]
param(
    [string]$ProbeDump = 'build-reports/db-release-safety/database-probe.dump'
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$resolvedDump = (Resolve-Path (Join-Path $repo $ProbeDump)).Path
$relativeDump = [IO.Path]::GetRelativePath($repo, $resolvedDump).Replace('\', '/')
$image = 'postgres:17-alpine'
$name = 'quata-attachment-preview-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$password = [guid]::NewGuid().ToString('N')
$database = 'replay'

function Invoke-Sql([string]$sql) {
    & docker exec -e "PGPASSWORD=$password" $name psql -U postgres -d $database -X -v ON_ERROR_STOP=1 -q -c $sql
    if ($LASTEXITCODE -ne 0) { throw 'sql_failed' }
}
function Invoke-PsqlFile([string]$file) {
    & docker exec -e "PGPASSWORD=$password" $name psql -U postgres -d $database -X -v ON_ERROR_STOP=1 -q -f $file
    if ($LASTEXITCODE -ne 0) { throw "psql_failed:$file" }
}
function Invoke-Restore([string]$section) {
    $saved = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & docker exec -e "PGPASSWORD=$password" $name pg_restore -U postgres -d $database --no-owner --no-acl --schema=public --section=$section "/workspace/$relativeDump" 2>&1
    $exit = $LASTEXITCODE
    $ErrorActionPreference = $saved
    if ($exit -ne 0) { $output | Select-Object -First 30 | Write-Output; throw "restore_failed:$section" }
}

try {
    & docker run -d --rm --name $name -e "POSTGRES_PASSWORD=$password" -v "${repo}:/workspace:ro" $image | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'container_start_failed' }
    $ready = $false
    foreach ($attempt in 1..60) {
        & docker exec -e "PGPASSWORD=$password" $name pg_isready -U postgres *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'container_not_ready' }
    & docker exec -e "PGPASSWORD=$password" $name createdb -U postgres -T template0 $database
    if ($LASTEXITCODE -ne 0) { throw 'database_create_failed' }
    Invoke-PsqlFile '/workspace/build-reports/db-release-safety/policy-replay-bootstrap.sql'
    Invoke-Restore 'pre-data'
    Invoke-Restore 'data'
    Invoke-Sql "insert into auth.users(id) select id from (select auth_user_id as id from public.community_profiles union select id from public.profiles union select follower_id from public.follows union select following_id from public.follows union select user_id from public.reactions union select auth_user_id from public.push_tokens union select auth_user_id from public.account_deletion_requests union select auth_user_id from public.web_client_sessions union select auth_user_id from public.web_push_subscriptions) q where id is not null on conflict do nothing;"
    Invoke-Restore 'post-data'
    Invoke-Sql @'
create table public.quata_attachment_preview_before as
select t.* from public.chat_threads t
where nullif(btrim(t.last_message_preview), '') is null
  and exists (select 1 from public.chat_messages m where m.thread_id = t.id and m.deleted_at is null);
'@
    Invoke-PsqlFile '/workspace/supabase/migrations/20260714_0004_chat_attachment_previews.sql'
    & docker exec -e "PGPASSWORD=$password" $name psql -U postgres -d $database -X -qAt -c @'
select jsonb_build_object(
  'selectedRows', count(*),
  'changedRows', count(*) filter (where to_jsonb(b) is distinct from to_jsonb(t)),
  'previewChangedRows', count(*) filter (where b.last_message_preview is distinct from t.last_message_preview),
  'lastMessageAtChangedRows', count(*) filter (where b.last_message_at is distinct from t.last_message_at),
  'updatedAtChangedRows', count(*) filter (where b.updated_at is distinct from t.updated_at),
  'otherColumnsChangedRows', count(*) filter (
    where (to_jsonb(b) - array['last_message_preview','last_message_at','updated_at'])
      is distinct from (to_jsonb(t) - array['last_message_preview','last_message_at','updated_at'])
  )
) from public.quata_attachment_preview_before b join public.chat_threads t using (id);
'@
    if ($LASTEXITCODE -ne 0) { throw 'comparison_failed' }
}
finally {
    $saved = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    try { & docker rm -f $name *> $null } finally { $ErrorActionPreference = $saved }
}
