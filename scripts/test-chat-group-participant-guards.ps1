[CmdletBinding()]
param([string]$DockerImage = "postgres:17-alpine")

$ErrorActionPreference = "Stop"
$repository = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$container = "quata-chat-group-guards-" + [guid]::NewGuid().ToString("N").Substring(0, 12)
$password = [guid]::NewGuid().ToString("N")
try {
    & docker run -d --rm --name $container -e "POSTGRES_PASSWORD=$password" -v "${repository}:/repo:ro" $DockerImage | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "chat_group_guards_container_start_failed" }
    $ready = $false
    foreach ($attempt in 1..30) {
        & docker exec $container pg_isready -U postgres *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "chat_group_guards_container_not_ready" }
    $output = & docker exec -e "PGPASSWORD=$password" $container psql -U postgres -d postgres -v ON_ERROR_STOP=1 -f /repo/scripts/sql/chat-group-participant-guards.test.sql 2>&1
    if ($LASTEXITCODE -ne 0 -or ($output -join "`n") -notmatch "chat_group_participant_guards_passed") {
        throw "chat_group_guards_sql_test_failed"
    }
    Write-Output "chat_group_participant_guards_test_passed"
}
finally {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try { & docker rm -f $container *> $null } finally { $ErrorActionPreference = $previous }
}
