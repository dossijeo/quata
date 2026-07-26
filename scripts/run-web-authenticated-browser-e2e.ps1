[CmdletBinding()]
param(
    [switch]$AllowExistingTestUser,
    [string]$Distribution = "web/build/dist/wasmJs/productionExecutable",
    [string]$Output = "build-reports/web/authenticated-browser-e2e.json"
)

$ErrorActionPreference = "Stop"
if (-not $AllowExistingTestUser) {
    throw "This browser E2E authenticates and globally revokes an isolated test user. Re-run with -AllowExistingTestUser."
}
foreach ($name in @("QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY", "QUATA_E2E_COUNTRY_CODE", "QUATA_E2E_PHONE", "QUATA_E2E_PASSWORD")) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { throw "$name must be set in the current process." }
}
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw "Node.js is required." }
& node (Join-Path $PSScriptRoot "web-authenticated-browser-e2e.mjs") --dist $Distribution --out $Output
exit $LASTEXITCODE
