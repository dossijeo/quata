[CmdletBinding()]
param(
    [switch]$AllowExistingTestData,
    [string]$Output = "build-reports/supabase/sb-03.json"
)

$ErrorActionPreference = "Stop"
if (-not $AllowExistingTestData) {
    throw "SB-03 reads approved existing E2E rows through public and authenticated identities. Re-run with -AllowExistingTestData only after provisioning isolated rows and their visibility contract."
}
foreach ($name in @(
    "QUATA_SUPABASE_URL",
    "QUATA_SUPABASE_PUBLISHABLE_KEY",
    "QUATA_E2E_COUNTRY_CODE",
    "QUATA_E2E_PHONE",
    "QUATA_E2E_PASSWORD",
    "QUATA_E2E_FEED_POST_ID",
    "QUATA_E2E_FEED_PUBLIC_EXPECTED",
    "QUATA_E2E_OFFICIAL_POST_ID",
    "QUATA_E2E_OFFICIAL_PUBLIC_EXPECTED"
)) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name must be set in the current process. SB-03 never accepts credentials, URLs or row identifiers as arguments."
    }
}
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "Node.js is required to run SB-03."
}

& node (Join-Path $PSScriptRoot "supabase-e2e-sb03.mjs") --out $Output
exit $LASTEXITCODE
