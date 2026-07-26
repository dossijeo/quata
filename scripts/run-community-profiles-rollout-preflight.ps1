[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

foreach ($name in @(
    "QUATA_EXPECTED_ADMIN_SHA256",
    "QUATA_EXPECTED_OFFICIAL_SHA256"
)) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name is required. Refusing the fail-closed rollout preflight."
    }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$databaseUrl = (
    Get-Content -LiteralPath "C:\Users\PC\.quata-supabase-db-url.txt" -Raw
).Trim()

docker run `
    --rm `
    --volume "${repoRoot}:/workspace:ro" `
    --volume "C:\Users\PC\.quata-supabase-pooler-ca.pem:/run/secrets/ca.pem:ro" `
    --env PGSSLROOTCERT=/run/secrets/ca.pem `
    postgres:16-alpine `
    psql $databaseUrl -X -v ON_ERROR_STOP=1 `
    -v "EXPECTED_ADMIN_SHA256=$env:QUATA_EXPECTED_ADMIN_SHA256" `
    -v "EXPECTED_OFFICIAL_SHA256=$env:QUATA_EXPECTED_OFFICIAL_SHA256" `
    -f /workspace/scripts/sql/community-profiles-rollout-preflight.sql

if ($LASTEXITCODE -ne 0) {
    throw "community_profiles rollout preflight failed."
}
