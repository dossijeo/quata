[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Join-Path $PSScriptRoot "..")
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

# This is intentionally a read-only architecture gate. It scans Kotlin source only
# under Quata-owned module roots, so prose, generated files and vendored readers cannot
# produce a false positive or make the result depend on third-party source drops.
$projectRoot = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$ownedRootNames = @("app", "core", "designsystem", "feature", "ios-shared", "iosApp", "web")
$ownedRoots = @(
    $ownedRootNames |
        ForEach-Object { Join-Path $projectRoot $_ } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Container }
)
$excludedPathSegment = '[\\/](?:build|node_modules|\.gradle|\.kotlin|document-reader)[\\/]'
$androidImportPattern = '^\s*import\s+android(?:\.|\s|$)'

function Get-RelativePath([string]$Path) {
    # Windows PowerShell 5.1 has no Path.GetRelativePath.
    $rootUri = New-Object Uri(($projectRoot.TrimEnd("\\", "/") + [IO.Path]::DirectorySeparatorChar))
    $pathUri = New-Object Uri($Path)
    return [Uri]::UnescapeDataString($rootUri.MakeRelativeUri($pathUri).ToString()).Replace("\\", "/")
}

$violations = @()
foreach ($root in $ownedRoots) {
    $commonMainFiles = Get-ChildItem -LiteralPath $root -Recurse -File -Filter "*.kt" -ErrorAction Stop |
        Where-Object {
            $_.FullName -match '[\\/]src[\\/]commonMain[\\/]' -and
            $_.FullName -notmatch $excludedPathSegment
        } |
        Sort-Object FullName

    foreach ($file in $commonMainFiles) {
        $lineNumber = 0
        foreach ($line in [IO.File]::ReadLines($file.FullName)) {
            $lineNumber++
            if ($line -match $androidImportPattern) {
                $violations += [pscustomobject]@{
                    Path = Get-RelativePath $file.FullName
                    Line = $lineNumber
                    Import = $line.Trim()
                }
            }
        }
    }
}

if ($violations.Count -gt 0) {
    Write-Host "FAIL: Android imports found in Quata-owned commonMain Kotlin sources:" -ForegroundColor Red
    $violations |
        Sort-Object Path, Line |
        ForEach-Object { Write-Host ("  {0}:{1}: {2}" -f $_.Path, $_.Line, $_.Import) }
    exit 1
}

Write-Host "PASS: no Android imports in Quata-owned */src/commonMain Kotlin sources."
Write-Host ("Scope: {0}; exclusions: build, node_modules, .gradle, .kotlin, document-reader." -f ($ownedRootNames -join ", "))
