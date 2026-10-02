[CmdletBinding()]
param([string]$DockerImage = "postgres:17-alpine")

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$container = "quata-post-likes-guard-" + [guid]::NewGuid().ToString("N").Substring(0, 10)
$password = [guid]::NewGuid().ToString("N")
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

try {
    docker run -d --rm --name $container -e "POSTGRES_PASSWORD=$password" -v "${repo}:/workspace:ro" $DockerImage | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "community_post_likes_actor_guard_container_failed" }

    $ready = $false
    foreach ($attempt in 1..30) {
        docker exec $container pg_isready -U postgres *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "community_post_likes_actor_guard_database_not_ready" }

    $output = docker exec -e "PGPASSWORD=$password" $container psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres -f /workspace/scripts/sql/community-post-likes-actor-guard.test.sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        $output | Write-Output
        throw "community_post_likes_actor_guard_sql_failed"
    }
    if (-not ($output | Select-String -SimpleMatch "COMMUNITY_POST_LIKES_ACTOR_GUARD_TEST_OK" -Quiet)) {
        throw "community_post_likes_actor_guard_success_marker_missing"
    }
    Write-Output "community_post_likes_actor_guard_postgres_pass"
}
finally {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try { docker rm -f $container *> $null } finally { $ErrorActionPreference = $previous }
}
