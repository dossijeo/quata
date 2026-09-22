[CmdletBinding()]
param(
    [ValidateSet("dry-run", "apply")][string]$Action,
    [Parameter(Mandatory = $true)][string]$Package,
    [Parameter(Mandatory = $true)][string]$ExpectedSourceCommit,
    [Parameter(Mandatory = $true)][string]$DbUrlFile,
    [Parameter(Mandatory = $true)][string]$TlsCaFile,
    [string]$Authorization = "",
    [string]$Output = "build-reports/db-release-safety/selective-release.json"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $DbUrlFile -PathType Leaf)) { throw "Database URL file was not found." }
if (-not (Test-Path -LiteralPath $TlsCaFile -PathType Leaf)) { throw "TLS CA file was not found." }
$env:SUPABASE_DB_URL = (Get-Content -Raw -LiteralPath $DbUrlFile).Trim()
$env:SUPABASE_DB_TLS_CA_FILE = (Resolve-Path -LiteralPath $TlsCaFile).Path
$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ("quata-selective-release-" + [guid]::NewGuid().ToString("N"))
$previousNodePath = $env:NODE_PATH
try {
    New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
    npm --prefix $temporaryRoot install --ignore-scripts --no-save --package-lock=false --fund=false --audit=false pg@8.16.3
    if ($LASTEXITCODE -ne 0) { throw "Unable to provision pinned pg dependency." }
    $env:NODE_PATH = Join-Path $temporaryRoot "node_modules"
    $arguments = @(
        (Join-Path $PSScriptRoot "selective-db-release-executor.mjs"),
        "--action", $Action,
        "--package", $Package,
        "--expected-source-commit", $ExpectedSourceCommit,
        "--out", $Output
    )
    if ($Action -eq "apply") {
        if ([string]::IsNullOrWhiteSpace($Authorization)) { throw "Apply requires an authorization file." }
        $arguments += @("--authorization", $Authorization)
    }
    & node @arguments
    exit $LASTEXITCODE
} finally {
    $env:NODE_PATH = $previousNodePath
    $env:SUPABASE_DB_URL = $null
    $env:SUPABASE_DB_TLS_CA_FILE = $null
    if (Test-Path -LiteralPath $temporaryRoot) { Remove-Item -LiteralPath $temporaryRoot -Recurse -Force }
}
