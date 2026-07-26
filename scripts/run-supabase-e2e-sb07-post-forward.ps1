[CmdletBinding()]
param(
    [ValidateSet('preflight-auth','full')][string]$Mode = 'preflight-auth',
    [Parameter(Mandatory = $true)][string]$DbUrlFile,
    [Parameter(Mandatory = $true)][string]$TlsCaFile,
    [Parameter(Mandatory = $true)][string]$RecoveryFile,
    [string]$Output = 'build-reports/supabase/sb-07-post-forward.json'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($env:QUATA_SB07_PRODUCTION_GATE_APPROVED -cne 'approved_temporary_fixture_only') { throw 'explicit_fixture_authorization_missing' }
if ($Mode -eq 'full' -and $env:QUATA_SB07_PRODUCTION_GATE_ALLOW_MUTATION -cne 'approved_public_postgrest_mutations') { throw 'explicit_public_mutation_authorization_missing' }
if (-not (Test-Path -LiteralPath $DbUrlFile -PathType Leaf) -or -not (Test-Path -LiteralPath $TlsCaFile -PathType Leaf)) { throw 'secure_database_input_missing' }
if (Test-Path -LiteralPath $RecoveryFile) { throw 'recovery_file_must_not_overwrite' }
function Test-Within([string]$Child,[string]$Parent) { $c=[IO.Path]::GetFullPath($Child); $p=[IO.Path]::GetFullPath($Parent).TrimEnd('\','/'); return $c.StartsWith($p+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) }
$recovery=[IO.Path]::GetFullPath($RecoveryFile)
$worktrees=@(& git -C (Join-Path $PSScriptRoot '..') worktree list --porcelain 2>$null | Where-Object { $_ -like 'worktree *' } | ForEach-Object { $_.Substring(9) })
if (@($worktrees | Where-Object { Test-Within $recovery $_ }).Count -ne 0) { throw 'recovery_file_must_be_outside_repository_worktrees' }
New-Item -ItemType Directory -Force -Path (Split-Path $recovery -Parent) | Out-Null
New-Item -ItemType File -Path $recovery -ErrorAction Stop | Out-Null
if ($env:OS -eq 'Windows_NT') { $who=(& whoami).Trim(); & icacls $recovery /inheritance:r /grant:r "${who}:F" *> $null; if($LASTEXITCODE -ne 0){throw 'recovery_file_acl_failed'}; $bad=@((Get-Acl $recovery).Access | Where-Object { $_.AccessControlType -eq 'Allow' -and $_.IdentityReference.Value -match '(^|\\)(Everyone|Users|Authenticated Users)$' }); if($bad.Count){throw 'recovery_file_acl_failed'} }
else { & chmod 600 $recovery; if($LASTEXITCODE -ne 0){throw 'recovery_file_acl_failed'} }
if (-not (Get-Command npm -ErrorAction SilentlyContinue) -or -not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'node_npm_required' }
$temp = Join-Path ([IO.Path]::GetTempPath()) ('quata-sb07-post-forward-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp | Out-Null
try {
  npm --prefix $temp install --ignore-scripts --no-save --package-lock=false --fund=false --audit=false pg@8.16.3
  if ($LASTEXITCODE -ne 0) { throw 'pinned_pg_install_failed' }
  $env:SUPABASE_DB_URL = (Get-Content -LiteralPath $DbUrlFile -Raw).Trim()
  $env:SUPABASE_DB_TLS_CA_FILE = (Resolve-Path -LiteralPath $TlsCaFile).Path
  $env:NODE_PATH = Join-Path $temp 'node_modules'
  $env:QUATA_SB07_RECOVERY_FILE = $recovery
  & node (Join-Path $PSScriptRoot 'supabase-e2e-sb07-post-forward.mjs') --mode $Mode --out $Output
  if ((Get-Item -LiteralPath $recovery).Length -eq 0) { [IO.File]::Delete($recovery) }
  exit $LASTEXITCODE
} finally {
  Remove-Item Env:SUPABASE_DB_URL -ErrorAction SilentlyContinue
  Remove-Item Env:SUPABASE_DB_TLS_CA_FILE -ErrorAction SilentlyContinue
  Remove-Item Env:NODE_PATH -ErrorAction SilentlyContinue
  Remove-Item Env:QUATA_SB07_RECOVERY_FILE -ErrorAction SilentlyContinue
  if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
}
