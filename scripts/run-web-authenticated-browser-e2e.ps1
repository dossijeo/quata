[CmdletBinding()]
param(
    [switch]$Real,
    [switch]$AllowExistingTestUser,
    [switch]$AcceptSessionRevocation,
    [switch]$AcceptBridgeIdentityAndSessionMutations,
    [switch]$ConfirmDedicatedWebAccount,
    [switch]$ConfirmPreprovisionedAuthUser,
    [string]$Distribution = "web/build/dist/wasmJs/productionExecutable",
    [string]$Output = "build-reports/web/authenticated-browser-e2e.json"
)

$ErrorActionPreference = "Stop"
$previousOptIn = $env:QUATA_AUTH_E2E_REAL_OPT_IN
$previousBridgeOptIn = $env:QUATA_AUTH_E2E_BRIDGE_MUTATION_OPT_IN
$previousAccountScope = $env:QUATA_E2E_ACCOUNT_SCOPE
$previousPreprovisioned = $env:QUATA_E2E_AUTH_USER_PREPROVISIONED
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw "Node.js is required." }
if ($Real) {
    if (
        -not $AllowExistingTestUser -or
        -not $AcceptSessionRevocation -or
        -not $AcceptBridgeIdentityAndSessionMutations -or
        -not $ConfirmDedicatedWebAccount -or
        -not $ConfirmPreprovisionedAuthUser
    ) {
        throw "Real mode requires all account, bridge-mutation and session-revocation confirmations."
    }
    foreach ($name in @("QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY", "QUATA_E2E_COUNTRY_CODE", "QUATA_E2E_PHONE", "QUATA_E2E_PASSWORD")) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { throw "$name must be set in the current process." }
    }
    $env:QUATA_AUTH_E2E_REAL_OPT_IN = "I_ACCEPT_SESSION_REVOCATION"
    $env:QUATA_AUTH_E2E_BRIDGE_MUTATION_OPT_IN = "I_ACCEPT_AUTH_IDENTITY_AND_SESSION_MUTATIONS"
    $env:QUATA_E2E_ACCOUNT_SCOPE = "dedicated_web_auth_e2e"
    $env:QUATA_E2E_AUTH_USER_PREPROVISIONED = "I_CONFIRM_AUTH_USER_ALREADY_EXISTS"
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
    if ($null -eq $previousBridgeOptIn) {
        Remove-Item Env:QUATA_AUTH_E2E_BRIDGE_MUTATION_OPT_IN -ErrorAction SilentlyContinue
    } else {
        $env:QUATA_AUTH_E2E_BRIDGE_MUTATION_OPT_IN = $previousBridgeOptIn
    }
    if ($null -eq $previousAccountScope) {
        Remove-Item Env:QUATA_E2E_ACCOUNT_SCOPE -ErrorAction SilentlyContinue
    } else {
        $env:QUATA_E2E_ACCOUNT_SCOPE = $previousAccountScope
    }
    if ($null -eq $previousPreprovisioned) {
        Remove-Item Env:QUATA_E2E_AUTH_USER_PREPROVISIONED -ErrorAction SilentlyContinue
    } else {
        $env:QUATA_E2E_AUTH_USER_PREPROVISIONED = $previousPreprovisioned
    }
}
exit $exitCode
