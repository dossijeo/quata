[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$suffix = [Guid]::NewGuid().ToString("N").Substring(0, 10)
$networkName = "quata-post-likes-net-$suffix"
$dbContainer = "quata-post-likes-db-$suffix"
$apiContainer = "quata-post-likes-api-$suffix"
$jwtSecret = "quata-isolated-post-likes-secret-2026"
$password = [Guid]::NewGuid().ToString("N")

try {
    docker network create $networkName | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "community_post_likes_postgrest_network_failed" }

    docker run `
        --detach `
        --name $dbContainer `
        --network $networkName `
        --network-alias db `
        --env "POSTGRES_PASSWORD=$password" `
        --volume "${repoRoot}:/workspace:ro" `
        postgres:17-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "community_post_likes_postgrest_database_failed" }

    $ready = $false
    foreach ($attempt in 1..30) {
        docker exec $dbContainer pg_isready -U postgres -d postgres *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "community_post_likes_postgrest_database_not_ready" }

    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $setupOutput = docker exec -e "PGPASSWORD=$password" $dbContainer `
            psql -U postgres -d postgres -X -v ON_ERROR_STOP=1 `
            -f /workspace/scripts/sql/community-post-likes-postgrest.setup.sql 2>&1
        $setupExitCode = $LASTEXITCODE
    }
    finally { $ErrorActionPreference = $previous }
    if ($setupExitCode -ne 0) {
        $setupOutput | Write-Output
        throw "community_post_likes_postgrest_setup_failed"
    }

    docker run `
        --detach `
        --name $apiContainer `
        --network $networkName `
        --network-alias postgrest `
        --env "PGRST_DB_URI=postgres://postgres:${password}@db:5432/postgres" `
        --env PGRST_DB_ANON_ROLE=anon `
        --env PGRST_DB_SCHEMAS=public `
        --env "PGRST_JWT_SECRET=$jwtSecret" `
        postgrest/postgrest:v12.2.3 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "community_post_likes_postgrest_api_failed" }

    docker run `
        --rm `
        --network $networkName `
        --volume "${repoRoot}:/workspace:ro" `
        node:22-alpine `
        node /workspace/scripts/community-post-likes-postgrest.test.mjs `
        http://postgrest:3000 $jwtSecret
    if ($LASTEXITCODE -ne 0) { throw "community_post_likes_postgrest_contract_failed" }
}
finally {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        docker rm --force $apiContainer $dbContainer *> $null
        docker network rm $networkName *> $null
    }
    finally { $ErrorActionPreference = $previous }
}
