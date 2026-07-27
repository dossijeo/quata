[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$suffix = [Guid]::NewGuid().ToString("N").Substring(0, 10)
$networkName = "quata-profile-guard-net-$suffix"
$dbContainer = "quata-profile-guard-db-$suffix"
$apiContainer = "quata-profile-guard-api-$suffix"
$jwtSecret = "quata-isolated-postgrest-secret-2026"

try {
    docker network create $networkName | Out-Null
    docker run `
        --detach `
        --name $dbContainer `
        --network $networkName `
        --network-alias db `
        --env POSTGRES_PASSWORD=quata-test-only `
        --volume "${repoRoot}:/workspace:ro" `
        postgres:16-alpine | Out-Null

    $ready = $false
    foreach ($attempt in 1..30) {
        docker exec $dbContainer pg_isready -U postgres -d postgres *> $null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) {
        throw "PostgreSQL did not become ready."
    }

    docker exec $dbContainer `
        psql -U postgres -d postgres -X `
        -v KEEP_FIXTURES=1 `
        -f /workspace/scripts/sql/community-profiles-actor-guard.test.sql
    if ($LASTEXITCODE -ne 0) {
        throw "The isolated database contract failed before PostgREST started."
    }

    docker run `
        --detach `
        --name $apiContainer `
        --network $networkName `
        --network-alias postgrest `
        --env "PGRST_DB_URI=postgres://postgres:quata-test-only@db:5432/postgres" `
        --env PGRST_DB_ANON_ROLE=anon `
        --env PGRST_DB_SCHEMAS=public `
        --env PGRST_DB_EXTRA_SEARCH_PATH=auth `
        --env "PGRST_JWT_SECRET=$jwtSecret" `
        postgrest/postgrest:v12.2.3 | Out-Null

    docker run `
        --rm `
        --network $networkName `
        --volume "${repoRoot}:/workspace:ro" `
        node:22-alpine `
        node /workspace/scripts/community-profiles-postgrest.test.mjs `
        http://postgrest:3000 $jwtSecret
    if ($LASTEXITCODE -ne 0) {
        throw "The isolated PostgREST contract failed."
    }
}
finally {
    docker rm --force $apiContainer $dbContainer *> $null
    docker network rm $networkName *> $null
}
