[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$containerName = "quata-profile-guard-$([Guid]::NewGuid().ToString('N').Substring(0, 10))"

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
        -f /workspace/scripts/sql/community-profiles-actor-guard.test.sql
    if ($LASTEXITCODE -ne 0) {
        throw "The isolated community_profiles actor-guard test failed."
    }
}
finally {
    docker rm --force $containerName *> $null
}
