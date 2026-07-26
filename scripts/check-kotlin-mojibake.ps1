param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot ".."))
)

# Restrict the audit to first-party source roots. It never rewrites files and intentionally skips
# generated/dependency trees even when one is nested under a source root.
$sourceRoots = @(
    "app/src",
    "core/src",
    "designsystem/src",
    "feature",
    "iosApp/iosApp",
    "iosApp/iosAppTests",
    "iosApp/iosAppUITests",
    "web/src"
) |
    ForEach-Object { Join-Path $RepositoryRoot $_ } |
    Where-Object { Test-Path $_ } |
    Select-Object -Unique

# Typical UTF-8 bytes decoded a second time as Windows-1252/Latin-1. Keep this intentionally
# narrow: correctly encoded source in another language must not be rejected merely for using
# non-ASCII text.
$mojibakePattern = '(\u00C3[\u0080-\u00BF]|\u00C2[\u0080-\u00BF]|\u00E2[\u0080-\u00BF]{1,2}|\u00F0[\u0080-\u00BF]{1,3})'
$sourceExtensions = @(".kt", ".kts", ".swift")
$excludedPathSegment = '(?i)[\\/](build|\.gradle|node_modules|vendor|vendored|third_party|third-party|Pods|DerivedData)[\\/]'
$utf8 = [System.Text.UTF8Encoding]::new($false, $true)

$sourceFiles = foreach ($root in $sourceRoots) {
    Get-ChildItem -Path $root -Recurse -File |
        Where-Object {
            $_.Extension -in $sourceExtensions -and
            $_.FullName -notmatch $excludedPathSegment
        }
}

$matches = foreach ($file in $sourceFiles | Sort-Object FullName) {
    try {
        $content = $utf8.GetString([System.IO.File]::ReadAllBytes($file.FullName))
    } catch {
        "{0}: invalid UTF-8 ({1})" -f $file.FullName, $_.Exception.Message
        continue
    }
    $lineNumber = 0
    foreach ($line in $content -split "`r?`n") {
        $lineNumber += 1
        if ($line -match $mojibakePattern) {
            "{0}:{1}: {2}" -f $file.FullName, $lineNumber, $line.Trim()
        }
    }
}

if ($matches) {
    $matches | Write-Error
    throw "Detected invalid UTF-8 or likely UTF-8 mojibake in owned Kotlin/Swift source. Use correctly encoded text or Unicode escapes."
}

Write-Host "No invalid UTF-8 or mojibake signatures found in owned Kotlin/Swift source roots."
