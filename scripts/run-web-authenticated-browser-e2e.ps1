[CmdletBinding()]
param(
    [switch]$Real,
    [switch]$AllowExistingTestUser,
    [switch]$AcceptSessionRevocation,
    [string]$Distribution = "web/build/dist/wasmJs/productionExecutable",
    [string]$Output = "build-reports/web/authenticated-browser-e2e.json"
)

$ErrorActionPreference = "Stop"
$previousOptIn = $env:QUATA_AUTH_E2E_REAL_OPT_IN
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw "Node.js is required." }
if ($Real) {
    if (-not $AllowExistingTestUser -or -not $AcceptSessionRevocation) {
        throw "Real mode requires both -AllowExistingTestUser and -AcceptSessionRevocation."
    }
    foreach ($name in @("QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY", "QUATA_E2E_COUNTRY_CODE", "QUATA_E2E_PHONE", "QUATA_E2E_PASSWORD")) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { throw "$name must be set in the current process." }
    }
    $env:QUATA_AUTH_E2E_REAL_OPT_IN = "I_ACCEPT_SESSION_REVOCATION"
}
$arguments = @((Join-Path $PSScriptRoot "web-authenticated-browser-e2e.mjs"), "--dist", $Distribution, "--out", $Output)
if ($Real) { $arguments += "--real" }
try {
    & node @arguments
    $exitCode = $LASTEXITCODE
} finally {
    if ($null -eq $previousOptIn) {
        Remove-Item Env:QUATA_AUTH_E2E_REAL_OPT_IN -ErrorAction SilentlyContinue
    } else {
        $env:QUATA_AUTH_E2E_REAL_OPT_IN = $previousOptIn
    }
}
exit $exitCode
