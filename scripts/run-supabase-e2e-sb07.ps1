[CmdletBinding()]
param(
    [switch]$AllowExistingTestData,
    [switch]$AllowCommunityMutation,
    [string]$Output = "build-reports/supabase/sb-07.json"
)

$ErrorActionPreference = "Stop"
if (-not $AllowExistingTestData -or -not $AllowCommunityMutation) {
    throw "SB-07 creates and deletes a comment and emoji reaction on an existing isolated post. Re-run with -AllowExistingTestData -AllowCommunityMutation only after the isolated users, wall/post, and external hard-cleanup contract are approved."
}
foreach ($name in @(
    "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY",
    "QUATA_E2E_COMMUNITIES_ACTOR_COUNTRY_CODE", "QUATA_E2E_COMMUNITIES_ACTOR_PHONE", "QUATA_E2E_COMMUNITIES_ACTOR_PASSWORD",
    "QUATA_E2E_COMMUNITIES_OUTSIDER_COUNTRY_CODE", "QUATA_E2E_COMMUNITIES_OUTSIDER_PHONE", "QUATA_E2E_COMMUNITIES_OUTSIDER_PASSWORD",
    "QUATA_E2E_COMMUNITIES_WALL_ID", "QUATA_E2E_COMMUNITIES_POST_ID",
    "QUATA_E2E_COMMUNITIES_ACTOR_E2E_SCOPE", "QUATA_E2E_COMMUNITIES_OUTSIDER_E2E_SCOPE", "QUATA_E2E_COMMUNITIES_EXTERNAL_HARD_CLEANUP"
)) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name must be set in the current process. SB-07 never accepts credentials, URLs or domain identifiers as arguments."
    }
}
if ($env:QUATA_E2E_COMMUNITIES_ACTOR_E2E_SCOPE -cne "isolated_sb07_community_actor" -or $env:QUATA_E2E_COMMUNITIES_OUTSIDER_E2E_SCOPE -cne "isolated_sb07_community_outsider") {
    throw "SB-07 cannot mutate: the two explicit isolated-account scope values do not match the SB-07 contract."
}
if ($env:QUATA_E2E_COMMUNITIES_EXTERNAL_HARD_CLEANUP -cne "approved_isolated_communities_purge") {
    throw "SB-07 cannot mutate: QUATA_E2E_COMMUNITIES_EXTERNAL_HARD_CLEANUP must equal approved_isolated_communities_purge. PostgREST row deletion alone does not prove that audit or soft-delete data has been purged."
}
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw "Node.js is required to run SB-07." }

& node (Join-Path $PSScriptRoot "supabase-e2e-sb07.mjs") --allow-existing-test-data --allow-community-mutation --out $Output
exit $LASTEXITCODE
