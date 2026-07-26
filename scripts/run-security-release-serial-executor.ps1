[CmdletBinding()]
param(
    [ValidateSet("dry-run", "apply-001", "apply-002", "rollback-001", "rollback-002")]
    [string]$Action = "dry-run",
    [string]$DbUrlFile,
    [string]$TlsCaFile,
    [string]$ExpectedPreconditionSha256,
    [string]$GateEvidence,
    [string]$ExpectedGateEvidenceSha256,
    [string]$ExpectedReleaseCommit,
    [string]$ExpectedSnapshotFingerprint,
    [string]$Output = ""
)

$ErrorActionPreference = "Stop"
if ($DbUrlFile) { $env:SUPABASE_DB_URL = (Get-Content -Raw -LiteralPath $DbUrlFile).Trim() }
if ($TlsCaFile) { $env:SUPABASE_DB_TLS_CA_FILE = (Resolve-Path -LiteralPath $TlsCaFile).Path }
if ([string]::IsNullOrWhiteSpace($env:SUPABASE_DB_URL) -or [string]::IsNullOrWhiteSpace($env:SUPABASE_DB_TLS_CA_FILE)) {
    throw "Configure DbUrlFile and TlsCaFile (or in-process equivalents). The URL is never passed in argv."
}
$temp = Join-Path ([IO.Path]::GetTempPath()) ("quata-serial-pg-" + [guid]::NewGuid().ToString("N"))
$oldNodePath = $env:NODE_PATH
try {
    npm --prefix $temp install --ignore-scripts --no-save --package-lock=false --fund=false --audit=false pg@8.16.3
    if ($LASTEXITCODE -ne 0) { throw "Unable to provision pinned pg dependency." }
    $env:NODE_PATH = Join-Path $temp "node_modules"
    $args = @((Join-Path $PSScriptRoot "security-release-serial-executor.mjs"), "--action", $Action)
    if ($ExpectedPreconditionSha256) { $args += @("--expected-precondition-sha256", $ExpectedPreconditionSha256) }
    if ($GateEvidence) { $args += @("--gate-evidence", $GateEvidence) }
    if ($ExpectedGateEvidenceSha256) { $args += @("--expected-gate-evidence-sha256", $ExpectedGateEvidenceSha256) }
    if ($ExpectedReleaseCommit) { $args += @("--expected-release-commit", $ExpectedReleaseCommit) }
    if ($ExpectedSnapshotFingerprint) { $args += @("--expected-snapshot-fingerprint", $ExpectedSnapshotFingerprint) }
    if ($Output) { $args += @("--out", $Output) }
    & node @args
    exit $LASTEXITCODE
} finally {
    $env:NODE_PATH = $oldNodePath
    if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
}
