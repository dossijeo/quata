[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$containerName = "quata-follows-integrity-$([Guid]::NewGuid().ToString('N').Substring(0, 10))"

try {
    docker run `
        --detach `
        --name $containerName `
        --env POSTGRES_PASSWORD=quata-test-only `
        --volume "${repoRoot}:/workspace:ro" `
        postgres:16-alpine | Out-Null

    $ready = $false
    foreach ($attempt in 1..30) {
        docker exec $containerName pg_isready -U postgres -d postgres *> $null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) {
        throw "PostgreSQL did not become ready."
    }

    docker exec $containerName `
        psql -U postgres -d postgres -X `
        -v KEEP_FIXTURES=1 `
        -f /workspace/scripts/sql/community-profile-follows-integrity.test.sql
    if ($LASTEXITCODE -ne 0) {
        throw "The isolated follows integrity test failed."
    }

    docker exec $containerName `
        psql -U postgres -d postgres -X `
        -f /workspace/scripts/sql/community-profile-follows-concurrency.test.sql
    if ($LASTEXITCODE -ne 0) {
        throw "The concurrent follows producer test failed."
    }
}
finally {
    docker rm --force $containerName *> $null
}
