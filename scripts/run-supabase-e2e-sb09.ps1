[CmdletBinding()]
param(
    [switch]$AllowExistingTestData,
    [switch]$AllowOfficialLikeMutation,
    [string]$Output = "build-reports/supabase/sb-09.json"
)

$ErrorActionPreference = "Stop"
if (-not $AllowExistingTestData -or -not $AllowOfficialLikeMutation) {
    throw "SB-09 mutates an isolated Official like. Re-run only with both explicit switches after provisioned fixtures have a verified hard-purge plan."
}
foreach ($name in @(
    "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY",
    "QUATA_E2E_OFFICIAL_A_COUNTRY_CODE", "QUATA_E2E_OFFICIAL_A_PHONE", "QUATA_E2E_OFFICIAL_A_PASSWORD",
    "QUATA_E2E_OFFICIAL_B_COUNTRY_CODE", "QUATA_E2E_OFFICIAL_B_PHONE", "QUATA_E2E_OFFICIAL_B_PASSWORD",
    "QUATA_E2E_OFFICIAL_POST_ID", "QUATA_E2E_OFFICIAL_A_E2E_SCOPE", "QUATA_E2E_OFFICIAL_B_E2E_SCOPE",
    "QUATA_E2E_OFFICIAL_EXTERNAL_HARD_CLEANUP"
)) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name must be set in the current process. SB-09 never accepts credentials or identifiers as arguments."
    }
}
if ($env:QUATA_E2E_OFFICIAL_EXTERNAL_HARD_CLEANUP -cne "approved_isolated_account_purge") {
    throw "SB-09 cannot mutate without the explicit external hard-purge contract."
}
if ($env:QUATA_E2E_OFFICIAL_A_E2E_SCOPE -cne "isolated_sb09_account" -or $env:QUATA_E2E_OFFICIAL_B_E2E_SCOPE -cne "isolated_sb09_account") {
    throw "SB-09 cannot mutate unless both accounts are explicitly scoped as isolated_sb09_account."
}
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw "Node.js is required to run SB-09." }

& node (Join-Path $PSScriptRoot "supabase-e2e-sb09.mjs") --allow-existing-test-data --allow-official-like-mutation --out $Output
exit $LASTEXITCODE
