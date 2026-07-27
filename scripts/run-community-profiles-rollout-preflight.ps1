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

docker run `
    --rm `
    --volume "${repoRoot}:/workspace:ro" `
    --volume "C:\Users\PC\.quata-supabase-pooler-ca.pem:/run/secrets/ca.pem:ro" `
    --volume "C:\Users\PC\.quata-supabase-db-url.txt:/run/secrets/db-url:ro" `
    --env PGSSLROOTCERT=/run/secrets/ca.pem `
    --env "EXPECTED_ADMIN_SHA256=$env:QUATA_EXPECTED_ADMIN_SHA256" `
    --env "EXPECTED_OFFICIAL_SHA256=$env:QUATA_EXPECTED_OFFICIAL_SHA256" `
    postgres:16-alpine `
    sh /workspace/scripts/run-community-profiles-rollout-preflight.sh

if ($LASTEXITCODE -ne 0) {
    throw "community_profiles rollout preflight failed."
}
