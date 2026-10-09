[CmdletBinding()]
param([string]$DockerImage = "postgres:17-alpine")

$ErrorActionPreference = "Stop"
$repository = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$container = "quata-conversation-create-atomicity-" + [guid]::NewGuid().ToString("N").Substring(0, 12)
$password = [guid]::NewGuid().ToString("N")
$testFailure = $null
$cleanupFailure = $null
try {
    & docker run -d --rm --name $container -e "POSTGRES_PASSWORD=$password" -v "${repository}:/repo:ro" $DockerImage | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "conversation_create_atomicity_container_start_failed" }
    $ready = $false
    foreach ($attempt in 1..30) {
        & docker exec $container pg_isready -U postgres *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "conversation_create_atomicity_container_not_ready" }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & docker exec -e "PGPASSWORD=$password" $container psql -U postgres -d postgres -v ON_ERROR_STOP=1 -f /repo/scripts/sql/conversation-create-backend-atomicity.test.sql 2>&1
        $psqlExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previous
    }
    if ($psqlExitCode -ne 0 -or ($output -join "`n") -notmatch "conversation_create_backend_atomicity_passed") {
        $safeTail = (($output | Select-Object -Last 30) -join "`n")
        throw "conversation_create_atomicity_sql_test_failed`n$safeTail"
    }
}
catch {
    $testFailure = $_
}
finally {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & docker rm -f $container *> $null
        $remaining = & docker ps -a --filter "name=^/$container$" --format "{{.Names}}" 2>&1
        $verifyExitCode = $LASTEXITCODE
        if ($verifyExitCode -ne 0 -or ($remaining -join "").Trim().Length -ne 0) {
            $cleanupFailure = "conversation_create_atomicity_container_cleanup_unverified"
        }
    }
    finally {
        $ErrorActionPreference = $previous
    }
}

if ($cleanupFailure) { throw $cleanupFailure }
if ($testFailure) { throw $testFailure }
Write-Output "conversation_create_backend_atomicity_test_passed"
