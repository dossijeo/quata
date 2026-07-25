[CmdletBinding()]
param(
    [switch]$AllowProfileMutation,
    [switch]$AllowSosContactMutation,
    [string]$Output = "build-reports/supabase/sb-06.json"
)

$ErrorActionPreference = "Stop"
if (-not $AllowProfileMutation -or -not $AllowSosContactMutation) {
    throw "SB-06 changes an isolated profile and creates SOS rows. Re-run with -AllowProfileMutation -AllowSosContactMutation only after the explicit restoration/delete contract has been approved."
}

foreach ($name in @(
    "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY",
    "QUATA_E2E_PROFILE_COUNTRY_CODE", "QUATA_E2E_PROFILE_PHONE", "QUATA_E2E_PROFILE_PASSWORD",
    "QUATA_E2E_PROFILE_SOS_SCOPE", "QUATA_E2E_PROFILE_SOS_CLEANUP",
    "QUATA_E2E_PROFILE_SOS_CONTACT_IDS"
)) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name must be set in the current process. SB-06 never accepts credentials, URLs or profile identifiers as arguments."
    }
}
if ($env:QUATA_E2E_PROFILE_SOS_SCOPE -cne "isolated_sb06_profile") {
    throw "SB-06 cannot mutate: QUATA_E2E_PROFILE_SOS_SCOPE must equal isolated_sb06_profile."
}
if ($env:QUATA_E2E_PROFILE_SOS_CLEANUP -cne "restore_display_name_and_delete_empty_contact_set") {
    throw "SB-06 cannot mutate: QUATA_E2E_PROFILE_SOS_CLEANUP must equal restore_display_name_and_delete_empty_contact_set."
}
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw "Node.js is required to run SB-06." }

& node (Join-Path $PSScriptRoot "supabase-e2e-sb06.mjs") --allow-profile-mutation --allow-sos-contact-mutation --out $Output
exit $LASTEXITCODE
