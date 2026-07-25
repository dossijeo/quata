[CmdletBinding()]
param(
    [switch]$AllowExistingTestUser,
    [switch]$CreateUser,
    [string]$Output = "build-reports/supabase/sb-02.json"
)

$ErrorActionPreference = "Stop"
if ($CreateUser) {
    throw "SB-02 cannot create a user: Quata Web has no public safe registration endpoint. Provision an isolated account through the approved workflow, then use -AllowExistingTestUser."
}
if (-not $AllowExistingTestUser) {
    throw "SB-02 is mutating authentication traffic. Re-run with -AllowExistingTestUser after providing an isolated, approved E2E account."
}
foreach ($name in @("QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY", "QUATA_E2E_COUNTRY_CODE", "QUATA_E2E_PHONE", "QUATA_E2E_PASSWORD")) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name must be set in the current process. SB-02 never accepts credentials or URLs as arguments."
    }
}
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "Node.js is required to run SB-02."
}

& node (Join-Path $PSScriptRoot "supabase-e2e-sb02.mjs") --out $Output
exit $LASTEXITCODE
