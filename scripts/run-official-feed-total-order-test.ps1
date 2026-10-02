[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw "Docker is required for the isolated Official feed test." }
& cmd.exe /c "docker info >NUL 2>NUL"
if ($LASTEXITCODE -ne 0) { throw "Docker daemon is not running." }

$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$suffix = [guid]::NewGuid().ToString("N").Substring(0, 10)
$network = "quata-official-page-net-$suffix"
$postgres = "quata-official-page-db-$suffix"
$postgrest = "quata-official-page-api-$suffix"

try {
    & docker network create $network | Out-Null
    & docker run --detach --name $postgres --network $network --network-alias postgres `
        --env POSTGRES_PASSWORD=quata-test-only --volume "${workspace}:/workspace:ro" postgres:17-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not start isolated PostgreSQL." }

    foreach ($attempt in 1..30) {
        & docker exec $postgres pg_isready -U postgres 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
        if ($attempt -eq 30) { throw "Isolated PostgreSQL did not become ready." }
        Start-Sleep -Milliseconds 500
    }

    & docker exec $postgres psql -U postgres -X -v ON_ERROR_STOP=1 -f /workspace/scripts/sql/official-feed-total-order.test.sql
    if ($LASTEXITCODE -ne 0) { throw "Official feed PostgreSQL contract failed." }

    & docker run --detach --name $postgrest --network $network --network-alias postgrest `
        --env "PGRST_DB_URI=postgres://authenticator:quata@postgres:5432/postgres" `
        --env PGRST_DB_ANON_ROLE=anon --env PGRST_DB_SCHEMAS=public `
        postgrest/postgrest:v12.2.3 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not start isolated PostgREST." }

    & docker run --rm --network $network --volume "${workspace}:/workspace:ro" node:22-alpine `
        node /workspace/scripts/official-feed-total-order-postgrest.test.mjs http://postgrest:3000
    if ($LASTEXITCODE -ne 0) { throw "Official feed PostgREST contract failed." }

    Write-Host "Official feed total-order PostgreSQL and anonymous PostgREST contracts passed."
}
finally {
    & docker rm --force $postgrest $postgres 2>$null | Out-Null
    & docker network rm $network 2>$null | Out-Null
}
