[CmdletBinding()]
param(
    [ValidateSet('preflight-auth','full')][string]$Mode = 'preflight-auth',
    [Parameter(Mandatory = $true)][string]$DbUrlFile,
    [Parameter(Mandatory = $true)][string]$TlsCaFile,
    [string]$Output = 'build-reports/supabase/sb-07-post-forward.json'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($env:QUATA_SB07_PRODUCTION_GATE_APPROVED -cne 'approved_temporary_fixture_only') { throw 'explicit_fixture_authorization_missing' }
if ($Mode -eq 'full' -and $env:QUATA_SB07_PRODUCTION_GATE_ALLOW_MUTATION -cne 'approved_public_postgrest_mutations') { throw 'explicit_public_mutation_authorization_missing' }
if (-not (Test-Path -LiteralPath $DbUrlFile -PathType Leaf) -or -not (Test-Path -LiteralPath $TlsCaFile -PathType Leaf)) { throw 'secure_database_input_missing' }
if (-not (Get-Command npm -ErrorAction SilentlyContinue) -or -not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'node_npm_required' }
$temp = Join-Path ([IO.Path]::GetTempPath()) ('quata-sb07-post-forward-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp | Out-Null
try {
  npm --prefix $temp install --ignore-scripts --no-save --package-lock=false --fund=false --audit=false pg@8.16.3
  if ($LASTEXITCODE -ne 0) { throw 'pinned_pg_install_failed' }
  $env:SUPABASE_DB_URL = (Get-Content -LiteralPath $DbUrlFile -Raw).Trim()
  $env:SUPABASE_DB_TLS_CA_FILE = (Resolve-Path -LiteralPath $TlsCaFile).Path
  $env:NODE_PATH = Join-Path $temp 'node_modules'
  & node (Join-Path $PSScriptRoot 'supabase-e2e-sb07-post-forward.mjs') --mode $Mode --out $Output
  exit $LASTEXITCODE
} finally {
  Remove-Item Env:SUPABASE_DB_URL -ErrorAction SilentlyContinue
  Remove-Item Env:SUPABASE_DB_TLS_CA_FILE -ErrorAction SilentlyContinue
  Remove-Item Env:NODE_PATH -ErrorAction SilentlyContinue
  if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
}
