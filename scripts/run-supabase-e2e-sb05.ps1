[CmdletBinding()]
param(
    [switch]$AllowExistingTestData,
    [switch]$AllowChatAttachmentMutation,
    [string]$Output = "build-reports/supabase/sb-05.json"
)

$ErrorActionPreference = "Stop"
if (-not $AllowExistingTestData -or -not $AllowChatAttachmentMutation) {
    throw "SB-05 uploads a test object and creates a Chat attachment. Re-run with -AllowExistingTestData -AllowChatAttachmentMutation only after two isolated accounts and an externally verified hard-purge contract are approved."
}
foreach ($name in @(
    "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY",
    "QUATA_E2E_CHAT_A_COUNTRY_CODE", "QUATA_E2E_CHAT_A_PHONE", "QUATA_E2E_CHAT_A_PASSWORD",
    "QUATA_E2E_CHAT_B_COUNTRY_CODE", "QUATA_E2E_CHAT_B_PHONE", "QUATA_E2E_CHAT_B_PASSWORD",
    "QUATA_E2E_CHAT_A_E2E_SCOPE", "QUATA_E2E_CHAT_B_E2E_SCOPE",
    "QUATA_E2E_SB05_EXTERNAL_HARD_CLEANUP"
)) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name must be set in the current process. SB-05 never accepts credentials, URLs or identifiers as arguments."
    }
}
if ($env:QUATA_E2E_SB05_EXTERNAL_HARD_CLEANUP -cne "approved_isolated_account_purge_and_attachment_verification") {
    throw "SB-05 cannot mutate: QUATA_E2E_SB05_EXTERNAL_HARD_CLEANUP must equal approved_isolated_account_purge_and_attachment_verification. Public Chat RPCs cannot hard-delete attachment rows, so an authorized account-lifecycle purge and post-purge verification are mandatory."
}
if ($env:QUATA_E2E_CHAT_A_E2E_SCOPE -cne "isolated_sb05_attachment_account" -or $env:QUATA_E2E_CHAT_B_E2E_SCOPE -cne "isolated_sb05_attachment_account") {
    throw "SB-05 cannot mutate: both QUATA_E2E_CHAT_*_E2E_SCOPE values must equal isolated_sb05_attachment_account."
}
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw "Node.js is required to run SB-05." }

& node (Join-Path $PSScriptRoot "supabase-e2e-sb05.mjs") --allow-existing-test-data --allow-chat-attachment-mutation --out $Output
exit $LASTEXITCODE
