[CmdletBinding()]
param(
    [ValidateSet("public", "catalog", "all")]
    [string]$Mode = "all",
    [string]$OutputDirectory = "build-reports/backend-compatibility",
    [string]$Baseline,
    [string]$DbUrlFile,
    [string]$TlsCaFile,
    [string]$WebDistribution,
    [string]$ChromePath,
    [string]$AndroidApk,
    [string]$DeviceId,
    [switch]$SkipWeb,
    [switch]$SkipAndroid
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path $projectRoot $OutputDirectory))
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null
$previousDbUrl = $env:SUPABASE_DB_URL
$previousCaFile = $env:SUPABASE_DB_TLS_CA_FILE
$previousCaPem = $env:SUPABASE_DB_TLS_CA_PEM

$temporaryRoot = $null
$previousNodePath = $env:NODE_PATH
try {
    if ($DbUrlFile) {
        $env:SUPABASE_DB_URL = (Get-Content -LiteralPath (Resolve-Path -LiteralPath $DbUrlFile) -Raw).Trim()
    }
    if ($TlsCaFile) {
        $env:SUPABASE_DB_TLS_CA_FILE = (Resolve-Path -LiteralPath $TlsCaFile).Path
        Remove-Item Env:SUPABASE_DB_TLS_CA_PEM -ErrorAction SilentlyContinue
    }
    $temporaryPackages = @()
    if ($Mode -ne "public") { $temporaryPackages += "pg@8.16.3" }
    if (-not $SkipWeb) { $temporaryPackages += "playwright-core@1.62.0" }
    if ($temporaryPackages.Count -gt 0) {
        $temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("quata-backend-gates-" + [guid]::NewGuid().ToString("N"))
        New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
        npm --prefix $temporaryRoot install --ignore-scripts --no-save --package-lock=false --fund=false --audit=false @temporaryPackages
        if ($LASTEXITCODE -ne 0) { throw "Unable to provision pinned compatibility-gate dependencies." }
        $env:NODE_PATH = Join-Path $temporaryRoot "node_modules"
    }

    $contractArguments = @(
        (Join-Path $PSScriptRoot "backend-compatibility-contracts.mjs"),
        "--mode", $Mode,
        "--out", (Join-Path $resolvedOutput "contracts.json")
    )
    if ($Baseline) { $contractArguments += @("--baseline", (Resolve-Path -LiteralPath $Baseline).Path) }
    & node @contractArguments
    if ($LASTEXITCODE -ne 0) { throw "Backend API/catalog compatibility gate failed." }

    if (-not $SkipWeb) {
        if (-not $WebDistribution) { throw "WebDistribution is required unless SkipWeb is set." }
        $webRunner = Join-Path $temporaryRoot "backend-compatibility-web-smoke.mjs"
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot "backend-compatibility-web-smoke.mjs") -Destination $webRunner
        $webArguments = @(
            $webRunner,
            "--dist", (Resolve-Path -LiteralPath $WebDistribution).Path
        )
        if ($ChromePath) { $webArguments += @("--chrome", $ChromePath) }
        & node @webArguments | Set-Content -LiteralPath (Join-Path $resolvedOutput "web.json") -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw "Anonymous Web navigation compatibility gate failed." }
    }

    if (-not $SkipAndroid) {
        $androidArguments = @{
            Output = Join-Path $resolvedOutput "android.json"
        }
        if ($AndroidApk) { $androidArguments.ApkPath = (Resolve-Path -LiteralPath $AndroidApk).Path }
        if ($DeviceId) { $androidArguments.DeviceId = $DeviceId }
        & (Join-Path $PSScriptRoot "run-android-backend-compatibility-smoke.ps1") @androidArguments
        if ($LASTEXITCODE -ne 0) { throw "Android compatibility smoke failed." }
    }
}
finally {
    $env:NODE_PATH = $previousNodePath
    if ($null -eq $previousDbUrl) { Remove-Item Env:SUPABASE_DB_URL -ErrorAction SilentlyContinue } else { $env:SUPABASE_DB_URL = $previousDbUrl }
    if ($null -eq $previousCaFile) { Remove-Item Env:SUPABASE_DB_TLS_CA_FILE -ErrorAction SilentlyContinue } else { $env:SUPABASE_DB_TLS_CA_FILE = $previousCaFile }
    if ($null -eq $previousCaPem) { Remove-Item Env:SUPABASE_DB_TLS_CA_PEM -ErrorAction SilentlyContinue } else { $env:SUPABASE_DB_TLS_CA_PEM = $previousCaPem }
    if ($temporaryRoot -and (Test-Path -LiteralPath $temporaryRoot)) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}

Write-Output "Backend compatibility gates passed. Reports: $resolvedOutput"
