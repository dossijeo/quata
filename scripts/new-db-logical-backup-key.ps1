[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$KeyFile)

# Creates an independent 256-bit AES key. The key must live outside every repo.
Set-StrictMode -Version Latest
$ErrorActionPreference="Stop"
function Fail([string]$Code) { throw $Code }
function Test-Within([string]$Candidate, [string]$Container) { $candidateFull=[IO.Path]::GetFullPath($Candidate); $containerFull=[IO.Path]::GetFullPath($Container).TrimEnd([IO.Path]::DirectorySeparatorChar,[IO.Path]::AltDirectorySeparatorChar); return $candidateFull.StartsWith($containerFull+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) }
$target=[IO.Path]::GetFullPath($KeyFile); if (Test-Path -LiteralPath $target) { Fail "backup_key_must_not_overwrite" }
$worktrees=@(& git -C (Join-Path $PSScriptRoot "..") worktree list --porcelain 2>$null | Where-Object { $_ -like "worktree *" } | ForEach-Object { $_.Substring(9) })
if (@($worktrees | Where-Object { Test-Within $target $_ }).Count -ne 0) { Fail "backup_key_must_not_be_inside_a_repository_worktree" }
New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
$key=[byte[]]::new(32); $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($key); [IO.File]::WriteAllText($target,[Convert]::ToBase64String($key),[Text.UTF8Encoding]::new($false)); if ($env:OS -eq "Windows_NT") { $identity=(& whoami).Trim(); & icacls $target /inheritance:r /grant:r "${identity}:F" *> $null; if ($LASTEXITCODE -ne 0) { Fail "backup_key_acl_failed" } } } finally { [Array]::Clear($key,0,$key.Length); $rng.Dispose() }
Write-Output "logical_backup_key_created"
