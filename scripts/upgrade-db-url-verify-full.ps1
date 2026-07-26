[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$InputFile,[Parameter(Mandatory = $true)][string]$OutputFile)

# Creates, never overwrites, a separate connection file upgraded from require to
# verify-full. A trusted CA is still mandatory at backup execution time.
Set-StrictMode -Version Latest
$ErrorActionPreference="Stop"
function Fail([string]$Code) { throw $Code }
if (-not (Test-Path -LiteralPath $InputFile -PathType Leaf)) { Fail "database_url_file_missing" }
$output=[IO.Path]::GetFullPath($OutputFile); if (Test-Path -LiteralPath $output) { Fail "database_url_output_must_not_overwrite" }
$raw=(Get-Content -LiteralPath $InputFile -Raw).Trim(); try { $uri=[Uri]$raw } catch { Fail "database_url_invalid" }
if ($uri.Scheme -notin @("postgres","postgresql") -or $uri.Query -notmatch '(?i)(^|[?&])sslmode=(require|verify-full)(&|$)') { Fail "database_url_sslmode_not_supported" }
$upgraded=[regex]::Replace($raw,'(?i)([?&]sslmode=)require(?=(&|$))','${1}verify-full')
New-Item -ItemType Directory -Force -Path (Split-Path $output -Parent) | Out-Null
[IO.File]::WriteAllText($output,$upgraded,[Text.UTF8Encoding]::new($false))
if ($env:OS -eq "Windows_NT") { $identity=(& whoami).Trim(); & icacls $output /inheritance:r /grant:r "${identity}:F" *> $null; if ($LASTEXITCODE -ne 0) { Remove-Item -LiteralPath $output -Force; Fail "database_url_output_acl_failed" } }
Write-Output "database_url_verify_full_created"
