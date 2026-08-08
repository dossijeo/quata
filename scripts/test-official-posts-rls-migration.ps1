[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw "Docker is required for the isolated Official posts RLS migration test." }
& cmd.exe /c "docker info >NUL 2>NUL"
$dockerInfoExitCode = $LASTEXITCODE
if ($dockerInfoExitCode -ne 0) { throw "Docker daemon is not running; start Docker before running the isolated Official posts RLS migration test." }

$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$suffix = [guid]::NewGuid().ToString("N")
$postgres = "quata-official-posts-db-$suffix"
$postgresPassword = [guid]::NewGuid().ToString("N")

try {
    & docker run --detach --name $postgres --env "POSTGRES_PASSWORD=$postgresPassword" --volume "${workspace}:/workspace:ro" postgres:17-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not start isolated PostgreSQL." }

    foreach ($attempt in 1..30) {
        & docker exec $postgres pg_isready -U postgres 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
        if ($attempt -eq 30) { throw "Isolated PostgreSQL did not become ready." }
        Start-Sleep -Milliseconds 500
    }

    & docker exec $postgres psql -U postgres -X -v ON_ERROR_STOP=1 -f /workspace/scripts/official-posts-rls-migration-test.sql
    if ($LASTEXITCODE -ne 0) { throw "Official posts RLS migration test failed." }
} finally {
    & docker rm --force $postgres 2>$null | Out-Null
}
