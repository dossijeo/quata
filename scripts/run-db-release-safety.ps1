[CmdletBinding()]
param(
    [ValidateSet("snapshot", "preflight", "postflight")]
    [string]$Phase = "preflight",
    [string]$Output = "",
    [string]$DbUrlFile,
    [string[]]$ExpectedMigration = @()
)

$ErrorActionPreference = "Stop"

if (-not [string]::IsNullOrWhiteSpace($DbUrlFile)) {
    if (-not (Test-Path -LiteralPath $DbUrlFile -PathType Leaf)) {
        throw "Database URL file was not found."
    }
    $databaseUrl = (Get-Content -LiteralPath $DbUrlFile -Raw).Trim()
    if ([string]::IsNullOrWhiteSpace($databaseUrl)) {
        throw "Database URL file is empty."
    }
    $env:SUPABASE_DB_URL = $databaseUrl
}
if ([string]::IsNullOrWhiteSpace($env:SUPABASE_DB_URL)) {
    throw "Set SUPABASE_DB_URL in-process or use DbUrlFile. URLs are never accepted as command arguments."
}
if ([string]::IsNullOrWhiteSpace($env:SUPABASE_DB_TLS_CA_FILE) -eq [string]::IsNullOrWhiteSpace($env:SUPABASE_DB_TLS_CA_PEM)) {
    throw "Configure exactly one TLS CA source: SUPABASE_DB_TLS_CA_FILE or SUPABASE_DB_TLS_CA_PEM."
}
if ($Phase -eq "postflight" -and $ExpectedMigration.Count -eq 0) {
    throw "Postflight requires at least one ExpectedMigration."
}
if (-not (Get-Command node -ErrorAction SilentlyContinue) -or -not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw "Node.js and npm are required."
}
if ([string]::IsNullOrWhiteSpace($Output)) {
    $Output = "build-reports/db-release-safety/$Phase.json"
}

$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("quata-db-release-" + [guid]::NewGuid().ToString("N"))
$previousNodePath = $env:NODE_PATH
try {
    New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
    npm --prefix $temporaryRoot install --ignore-scripts --no-save --package-lock=false --fund=false --audit=false pg@8.16.3
    if ($LASTEXITCODE -ne 0) { throw "Unable to provision pinned pg dependency." }
    $env:NODE_PATH = Join-Path $temporaryRoot "node_modules"

    & node --test (Join-Path $PSScriptRoot "supabase-e2e-sb01-tls.test.mjs")
    if ($LASTEXITCODE -ne 0) { throw "TLS configuration self-test failed." }

    $arguments = @(
        (Join-Path $PSScriptRoot "db-release-safety.mjs"),
        "--phase", $Phase,
        "--out", $Output
    )
    foreach ($migration in $ExpectedMigration) {
        $arguments += @("--expected-migration", $migration)
    }
    & node @arguments
    exit $LASTEXITCODE
}
finally {
    $env:NODE_PATH = $previousNodePath
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}
