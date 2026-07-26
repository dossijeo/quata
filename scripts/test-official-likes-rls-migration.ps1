[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw "Docker is required for the isolated Official likes RLS migration test." }
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw "Node.js is required for the isolated PostgREST contract test." }

$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$suffix = [guid]::NewGuid().ToString("N")
$network = "quata-official-likes-net-$suffix"
$postgres = "quata-official-likes-db-$suffix"
$postgrest = "quata-official-likes-api-$suffix"
$postgresPassword = [guid]::NewGuid().ToString("N")
$jwtSecret = [guid]::NewGuid().ToString("N")

function Invoke-PsqlFile([string]$path) {
    & docker exec $postgres psql -U postgres -X -v ON_ERROR_STOP=1 -f $path
    if ($LASTEXITCODE -ne 0) { throw "psql failed: $path" }
}
function Invoke-PsqlCommand([string]$sql) {
    & docker exec $postgres psql -U postgres -X -v ON_ERROR_STOP=1 -c $sql
    if ($LASTEXITCODE -ne 0) { throw "psql command failed" }
}
function Invoke-PostgrestContract([string]$phase, [string]$url) {
    $previousUrl = $env:OFFICIAL_LIKES_POSTGREST_URL
    $previousSecret = $env:OFFICIAL_LIKES_POSTGREST_JWT_SECRET
    try {
        $env:OFFICIAL_LIKES_POSTGREST_URL = $url
        $env:OFFICIAL_LIKES_POSTGREST_JWT_SECRET = $jwtSecret
        & node (Join-Path $workspace "scripts\official-likes-rls-postgrest-contract.mjs") $phase
        if ($LASTEXITCODE -ne 0) { throw "PostgREST contract failed: $phase" }
    } finally {
        $env:OFFICIAL_LIKES_POSTGREST_URL = $previousUrl
        $env:OFFICIAL_LIKES_POSTGREST_JWT_SECRET = $previousSecret
    }
}

try {
    & node (Join-Path $workspace "scripts\official-likes-rls-migration-contract.mjs")
    if ($LASTEXITCODE -ne 0) { throw "Static migration contract failed." }

    & docker network create $network | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not create isolated Docker network." }
    & docker run --detach --name $postgres --network $network --network-alias postgres `
        --env "POSTGRES_PASSWORD=$postgresPassword" --volume "${workspace}:/workspace:ro" postgres:16-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not start isolated PostgreSQL." }

    foreach ($attempt in 1..30) {
        & docker exec $postgres pg_isready -U postgres 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
        if ($attempt -eq 30) { throw "Isolated PostgreSQL did not become ready." }
        Start-Sleep -Milliseconds 500
    }
    Invoke-PsqlFile "/workspace/scripts/official-likes-rls-migration-test.sql"
    Invoke-PsqlFile "/workspace/scripts/official-likes-rls-baseline-catalog-test.sql"

    & docker run --detach --name $postgrest --network $network --publish "127.0.0.1::3000" `
        --env "PGRST_DB_URI=postgres://authenticator:isolated-postgrest-password@postgres:5432/postgres" `
        --env "PGRST_DB_SCHEMA=public" --env "PGRST_DB_ANON_ROLE=anon" `
        --env "PGRST_JWT_SECRET=$jwtSecret" postgrest/postgrest:v12.2.3 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not start isolated PostgREST." }
    $mapping = (& docker port $postgrest 3000/tcp | Select-Object -First 1).Trim()
    if ($mapping -notmatch ':(\d+)$') { throw "Could not determine isolated PostgREST port." }
    $url = "http://127.0.0.1:$($Matches[1])/"
    foreach ($attempt in 1..30) {
        $status = (& curl.exe --silent --output NUL --write-out "%{http_code}" --max-time 2 "$url`official_post_likes?select=id&limit=1")
        if ($LASTEXITCODE -eq 0 -and $status -eq "200") { break }
        if ($attempt -eq 30) { throw "Isolated PostgREST did not become ready (last HTTP status: $status)." }
        Start-Sleep -Milliseconds 500
    }

    # Baseline proves the catalog we are going to restore, through PostgREST too.
    Invoke-PostgrestContract "baseline" $url

    Invoke-PsqlFile "/workspace/supabase/migrations/20260726171002_official_post_likes_actor_guard.sql"
    Invoke-PsqlCommand "notify pgrst, 'reload schema';"
    Invoke-PsqlFile "/workspace/scripts/official-likes-rls-secured-catalog-test.sql"
    Invoke-PostgrestContract "secured" $url

    Invoke-PsqlFile "/workspace/supabase/rollbacks/20260726171002_official_post_likes_actor_guard.rollback.sql"
    Invoke-PsqlCommand "notify pgrst, 'reload schema';"
    Invoke-PsqlFile "/workspace/scripts/official-likes-rls-baseline-catalog-test.sql"
    Invoke-PostgrestContract "baseline" $url

    # A second apply is mandatory evidence that a post-rollback redeploy is safe.
    Invoke-PsqlFile "/workspace/supabase/migrations/20260726171002_official_post_likes_actor_guard.sql"
    Invoke-PsqlCommand "notify pgrst, 'reload schema';"
    Invoke-PsqlFile "/workspace/scripts/official-likes-rls-secured-catalog-test.sql"
    Invoke-PostgrestContract "secured" $url
    Write-Host "Official likes apply -> PostgREST attacks blocked -> rollback -> exact baseline -> reapply contract passed."
} finally {
    & docker rm --force $postgrest 2>$null | Out-Null
    & docker rm --force $postgres 2>$null | Out-Null
    & docker network rm $network 2>$null | Out-Null
}
