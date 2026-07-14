[CmdletBinding()]
param(
    [string]$CodeQl = "$env:USERPROFILE\.tools\codeql\2.26.0\codeql\codeql.exe",
    [string]$OutputDirectory = "$env:TEMP\quata-codeql"
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$javaDatabase = Join-Path $OutputDirectory "java-kotlin"
$javascriptDatabase = Join-Path $OutputDirectory "javascript-typescript"
$javaSarif = Join-Path $OutputDirectory "java-kotlin.sarif"
$javascriptSarif = Join-Path $OutputDirectory "javascript-typescript.sarif"

if (-not (Test-Path -LiteralPath $CodeQl)) {
    throw "CodeQL CLI was not found at '$CodeQl'. Install the CodeQL bundle or pass -CodeQl."
}

if (-not $env:JAVA_HOME) {
    $androidStudioJbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path -LiteralPath $androidStudioJbr) {
        $env:JAVA_HOME = $androidStudioJbr
    }
}
if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME must point to a Java 17 installation."
}

$env:Path = "$env:JAVA_HOME\bin;$env:Path"
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Invoke-Checked {
    param(
        [Parameter(Mandatory)] [string]$Executable,
        [Parameter(Mandatory)] [string[]]$Arguments
    )
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code $LASTEXITCODE`: $Executable $($Arguments -join ' ')"
    }
}

Push-Location $repositoryRoot
try {
    Invoke-Checked $CodeQl @(
        "pack", "download",
        "codeql/java-queries@1.11.5",
        "codeql/javascript-queries@2.4.0"
    )

    & .\gradlew.bat --stop | Out-Null

    Invoke-Checked $CodeQl @(
        "database", "create", $javaDatabase,
        "--overwrite",
        "--language=java-kotlin",
        "--source-root=$repositoryRoot",
        "--command=.\gradlew.bat --no-daemon --rerun-tasks :app:assembleDebug",
        "--threads=0"
    )
    Invoke-Checked $CodeQl @(
        "database", "analyze", $javaDatabase,
        "codeql/java-queries:codeql-suites/java-security-extended.qls",
        "--format=sarif-latest",
        "--output=$javaSarif",
        "--sarif-category=java-kotlin",
        "--threads=0"
    )

    Invoke-Checked $CodeQl @(
        "database", "create", $javascriptDatabase,
        "--overwrite",
        "--language=javascript-typescript",
        "--build-mode=none",
        "--source-root=$repositoryRoot",
        "--codescanning-config=$repositoryRoot\.github\codeql\javascript-config.yml",
        "--threads=0"
    )
    Invoke-Checked $CodeQl @(
        "database", "analyze", $javascriptDatabase,
        "codeql/javascript-queries:codeql-suites/javascript-security-extended.qls",
        "--format=sarif-latest",
        "--output=$javascriptSarif",
        "--sarif-category=javascript-typescript",
        "--threads=0"
    )

    foreach ($sarifPath in @($javaSarif, $javascriptSarif)) {
        $sarif = Get-Content -LiteralPath $sarifPath -Raw | ConvertFrom-Json
        $resultCount = @($sarif.runs | ForEach-Object { $_.results }).Count
        Write-Host "$(Split-Path -Leaf $sarifPath): $resultCount result(s)"
    }
} finally {
    Pop-Location
}
