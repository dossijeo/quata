[CmdletBinding()]
param(
    [switch]$AllowIsolatedDatabase
)

$ErrorActionPreference = "Stop"
if (-not $AllowIsolatedDatabase) {
    throw "This regression commits migration DDL, creates and purges ephemeral auth rows, rehearses rollback, and reapplies the secured migration. Re-run with -AllowIsolatedDatabase against a disposable isolated database."
}
if ($env:QUATA_RLS_TEST_SCOPE -cne "isolated_rls001_review_database") {
    throw "QUATA_RLS_TEST_SCOPE must equal isolated_rls001_review_database."
}
if ([string]::IsNullOrWhiteSpace($env:QUATA_RLS_TEST_DB_URL)) {
    throw "QUATA_RLS_TEST_DB_URL must be provided through the environment; URLs and credentials are never accepted as arguments."
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is required for the pinned PostgreSQL client."
}

$testFile = (Resolve-Path (Join-Path $PSScriptRoot "../supabase/tests/rls001_community_comments_delete.sql")).Path
$env:QUATA_RLS_TEST_DB_URL_IN_CONTAINER = $env:QUATA_RLS_TEST_DB_URL
try {
    & docker run --rm `
        -e QUATA_RLS_TEST_DB_URL_IN_CONTAINER `
        -v "${testFile}:/work/rls001.sql:ro" `
        -v "$((Resolve-Path (Join-Path $PSScriptRoot '../supabase/migrations')).Path):/migrations:ro" `
        -v "$((Resolve-Path (Join-Path $PSScriptRoot '../supabase/rollbacks')).Path):/rollbacks:ro" `
        postgres:17-alpine `
        sh -c 'psql "$QUATA_RLS_TEST_DB_URL_IN_CONTAINER" -X -v ON_ERROR_STOP=1 -f /work/rls001.sql'
    if ($LASTEXITCODE -ne 0) { throw "RLS-001 SQL/E2E regression failed." }
} finally {
    Remove-Item Env:QUATA_RLS_TEST_DB_URL_IN_CONTAINER -ErrorAction SilentlyContinue
}
