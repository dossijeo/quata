[CmdletBinding()]
param(
    [switch]$AllowExistingTestData,
    [switch]$AllowChatMutation,
    [string]$Output = "build-reports/supabase/sb-04.json"
)

$ErrorActionPreference = "Stop"
if (-not $AllowExistingTestData -or -not $AllowChatMutation) {
    throw "SB-04 creates a private thread and messages. Re-run with -AllowExistingTestData -AllowChatMutation only after two isolated users and their external hard-cleanup contract are approved."
}
foreach ($name in @(
    "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY",
    "QUATA_E2E_CHAT_A_COUNTRY_CODE", "QUATA_E2E_CHAT_A_PHONE", "QUATA_E2E_CHAT_A_PASSWORD",
    "QUATA_E2E_CHAT_B_COUNTRY_CODE", "QUATA_E2E_CHAT_B_PHONE", "QUATA_E2E_CHAT_B_PASSWORD",
    "QUATA_E2E_CHAT_A_E2E_SCOPE", "QUATA_E2E_CHAT_B_E2E_SCOPE",
    "QUATA_E2E_CHAT_EXTERNAL_HARD_CLEANUP"
)) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name must be set in the current process. SB-04 never accepts credentials, URLs or identifiers as arguments."
    }
}
if ($env:QUATA_E2E_CHAT_EXTERNAL_HARD_CLEANUP -cne "approved_isolated_account_purge") {
    throw "SB-04 cannot mutate: QUATA_E2E_CHAT_EXTERNAL_HARD_CLEANUP must equal approved_isolated_account_purge. Current chat RPC cleanup is logical only; an authorized external hard purge for both isolated accounts is mandatory."
}
if ($env:QUATA_E2E_CHAT_A_E2E_SCOPE -cne "isolated_sb04_account" -or $env:QUATA_E2E_CHAT_B_E2E_SCOPE -cne "isolated_sb04_account") {
    throw "SB-04 cannot mutate: both QUATA_E2E_CHAT_*_E2E_SCOPE values must equal isolated_sb04_account. This explicit per-account scope prevents using a normal account by accident."
}
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw "Node.js is required to run SB-04." }

& node (Join-Path $PSScriptRoot "supabase-e2e-sb04.mjs") --allow-existing-test-data --allow-chat-mutation --out $Output
exit $LASTEXITCODE
