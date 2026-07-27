[CmdletBinding()]
param(
    [switch]$AllowExistingTestData,
    [switch]$AllowChatMutation,
    [string]$Distribution = "web/build/dist/wasmJs/productionExecutable",
    [string]$Output = "build-reports/web/chat-browser-e2e.json"
)

$ErrorActionPreference = "Stop"
if (-not $AllowExistingTestData -or -not $AllowChatMutation) {
    throw "The Chat browser E2E mutates isolated Chat data. Both explicit switches and a verified hard-purge plan are required."
}
foreach ($name in @(
    "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY",
    "QUATA_E2E_CHAT_A_COUNTRY_CODE", "QUATA_E2E_CHAT_A_PHONE", "QUATA_E2E_CHAT_A_PASSWORD",
    "QUATA_E2E_CHAT_B_COUNTRY_CODE", "QUATA_E2E_CHAT_B_PHONE", "QUATA_E2E_CHAT_B_PASSWORD",
    "QUATA_E2E_CHAT_A_E2E_SCOPE", "QUATA_E2E_CHAT_B_E2E_SCOPE", "QUATA_E2E_CHAT_EXTERNAL_HARD_CLEANUP",
    "QUATA_E2E_CHAT_MANAGER_AUTHORIZATION"
)) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name must be set in the current process."
    }
}
if ($env:QUATA_E2E_CHAT_EXTERNAL_HARD_CLEANUP -cne "approved_isolated_account_purge") {
    throw "The authorized external hard-purge contract is required."
}
if ($env:QUATA_E2E_CHAT_MANAGER_AUTHORIZATION -cne "MANAGER_APPROVED_ISOLATED_CHAT_E2E") {
    throw "A one-off manager authorization is required before the runner can authenticate either account."
}
if ($env:QUATA_E2E_CHAT_A_E2E_SCOPE -cne "isolated_sb04_account" -or $env:QUATA_E2E_CHAT_B_E2E_SCOPE -cne "isolated_sb04_account") {
    throw "Both users must be explicitly scoped as isolated_sb04_account."
}
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw "Node.js is required." }

& node (Join-Path $PSScriptRoot "web-chat-browser-e2e.mjs") --dist $Distribution --out $Output
exit $LASTEXITCODE
