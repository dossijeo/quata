[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is required for the isolated Official likes RLS migration test."
}

$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$container = "quata-official-likes-rls-" + [guid]::NewGuid().ToString("N")
$password = [guid]::NewGuid().ToString("N")

try {
    & docker run --detach --name $container `
        --env "POSTGRES_PASSWORD=$password" `
        --volume "${workspace}:/workspace:ro" `
        postgres:16-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not start isolated PostgreSQL." }

    $ready = $false
    foreach ($attempt in 1..30) {
        & docker exec $container pg_isready -U postgres 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "Isolated PostgreSQL did not become ready." }

    & docker exec $container psql -U postgres -X -v ON_ERROR_STOP=1 `
        -f /workspace/scripts/official-likes-rls-migration-test.sql
    if ($LASTEXITCODE -ne 0) { throw "Official likes RLS migration regression failed." }
} finally {
    & docker rm --force $container 2>$null | Out-Null
}
