[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker is required for the isolated Official comments RLS test.' }
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Node.js is required for the static Official comments RLS contract.' }
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$container = "quata-official-comments-$([guid]::NewGuid().ToString('N'))"
function Invoke-PsqlFile([string]$path) {
  docker exec $container psql -U postgres -X -v ON_ERROR_STOP=1 -f $path
  if ($LASTEXITCODE -ne 0) { throw "psql failed: $path" }
}
function Invoke-PsqlCommand([string]$sql) {
  docker exec $container psql -U postgres -X -v ON_ERROR_STOP=1 -c $sql
  if ($LASTEXITCODE -ne 0) { throw 'psql command failed.' }
}
function Get-CatalogFingerprint {
  $value = (docker exec $container psql -U postgres -X -v ON_ERROR_STOP=1 -f /workspace/scripts/official-comments-rls-catalog-fingerprint.sql | Select-Object -Last 1).Trim()
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($value)) { throw 'Could not fingerprint Official comments catalog.' }
  return $value
}
try {
  & node (Join-Path $PSScriptRoot 'official-comments-rls-migration-contract.mjs')
  if ($LASTEXITCODE -ne 0) { throw 'Static migration contract failed.' }
  docker run --detach --name $container --env POSTGRES_PASSWORD=quata-test-only --volume "${root}:/workspace:ro" postgres:17-alpine | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Could not start isolated PostgreSQL.' }
  foreach ($attempt in 1..30) { docker exec $container pg_isready -U postgres *> $null; if ($LASTEXITCODE -eq 0) { break }; if ($attempt -eq 30) { throw 'PostgreSQL did not become ready.' }; Start-Sleep -Milliseconds 500 }
  Invoke-PsqlFile '/workspace/scripts/official-comments-rls-migration-test.sql'

  # A same-name policy changed by a later release must make rollback abort
  # before any DDL executes. Compare the whole controlled catalog before/after.
  Invoke-PsqlCommand 'alter policy official_post_comments_authenticated_update_own_or_admin on public.official_post_comments with check (true);'
  $beforeDriftRollback = Get-CatalogFingerprint
  $savedErrorActionPreference = $ErrorActionPreference
  try {
    $ErrorActionPreference = 'Continue'
    docker exec $container psql -U postgres -X -v ON_ERROR_STOP=1 -f /workspace/supabase/rollbacks/20260727120001_official_post_comments_actor_guard.rollback.sql *> $null
    $driftRollbackExitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $savedErrorActionPreference
  }
  if ($driftRollbackExitCode -eq 0) { throw 'Rollback accepted simulated same-name policy drift.' }
  $afterDriftRollback = Get-CatalogFingerprint
  if ($beforeDriftRollback -ne $afterDriftRollback) { throw 'Rejected rollback changed the drifted catalog.' }

  Invoke-PsqlFile '/workspace/supabase/migrations/20260727120001_official_post_comments_actor_guard.sql'
  Invoke-PsqlFile '/workspace/supabase/rollbacks/20260727120001_official_post_comments_actor_guard.rollback.sql'
  Invoke-PsqlFile '/workspace/supabase/migrations/20260727120001_official_post_comments_actor_guard.sql'
  Write-Host 'Official comments fail-closed drift rollback contract passed.'
} finally { docker rm --force $container *> $null }
