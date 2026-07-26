[CmdletBinding()]
param(
    [string]$Output = "build-reports/supabase/sb-01.json",
    [string]$DbUrlFile
)

$ErrorActionPreference = "Stop"
if (-not [string]::IsNullOrWhiteSpace($DbUrlFile)) {
    if (-not (Test-Path -LiteralPath $DbUrlFile -PathType Leaf)) {
        throw "SB-01 database URL file was not found."
    }
    $databaseUrl = (Get-Content -LiteralPath $DbUrlFile -Raw).Trim()
    if ([string]::IsNullOrWhiteSpace($databaseUrl)) {
        throw "SB-01 database URL file is empty."
    }
    $env:SUPABASE_DB_URL = $databaseUrl
}
if ([string]::IsNullOrWhiteSpace($env:SUPABASE_DB_URL)) {
    throw "SUPABASE_DB_URL must be set in the current process or supplied through DbUrlFile. This runner never accepts a URL argument."
}
if ([string]::IsNullOrWhiteSpace($env:SUPABASE_DB_TLS_CA_FILE) -eq [string]::IsNullOrWhiteSpace($env:SUPABASE_DB_TLS_CA_PEM)) {
    throw "Configure exactly one explicit SB-01 TLS CA source: SUPABASE_DB_TLS_CA_FILE or SUPABASE_DB_TLS_CA_PEM. TLS verification is never disabled."
}
if (-not (Get-Command node -ErrorAction SilentlyContinue) -or -not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw "Node.js and npm are required to run SB-01."
}

# Keep the pg dependency out of the repository and remove only the directory created here.
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("quata-sb01-" + [guid]::NewGuid().ToString("N"))
$previousNodePath = $env:NODE_PATH
try {
    New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
    npm --prefix $temporaryRoot install --ignore-scripts --no-save --package-lock=false --fund=false --audit=false pg@8.16.3
    if ($LASTEXITCODE -ne 0) { throw "Unable to provision the pinned pg dependency for SB-01." }
    $env:NODE_PATH = Join-Path $temporaryRoot "node_modules"
    & node --test (Join-Path $PSScriptRoot "supabase-e2e-sb01-tls.test.mjs")
    if ($LASTEXITCODE -ne 0) { throw "SB-01 TLS configuration self-test failed." }
    & node (Join-Path $PSScriptRoot "supabase-e2e-sb01.mjs") --out $Output
    exit $LASTEXITCODE
}
finally {
    $env:NODE_PATH = $previousNodePath
    if (Test-Path -LiteralPath $temporaryRoot) { Remove-Item -LiteralPath $temporaryRoot -Recurse -Force }
}
