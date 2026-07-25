param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot ".."))
)

$sourceRoots = @("app", "core", "designsystem", "feature", "iosApp", "web") |
    ForEach-Object { Join-Path $RepositoryRoot (Join-Path $_ "src") } |
    Where-Object { Test-Path $_ }

# Typical UTF-8 bytes decoded a second time as Windows-1252/Latin-1. Keep this intentionally
# narrow: correctly encoded source in another language must not be rejected merely for using Ã.
$mojibakePattern = '(\u00C3[\u0080-\u00BF]|\u00C2[\u0080-\u00BF]|\u00E2[\u0080-\u00BF]{1,2}|\u00F0[\u0080-\u00BF]{1,3})'
$matches = foreach ($root in $sourceRoots) {
    # Restrict this to checked-in source trees: scanning module build output can make a clean
    # source change fail on generated Kotlin, while documentation is not in scope at all.
    Get-ChildItem -Path $root -Recurse -File -Include *.kt,*.kts |
        Select-String -Pattern $mojibakePattern |
        ForEach-Object { "{0}:{1}: {2}" -f $_.Path, $_.LineNumber, $_.Line.Trim() }
}

if ($matches) {
    $matches | Write-Error
    throw "Detected likely UTF-8 mojibake in Kotlin source. Use correctly encoded text or Unicode escapes."
}

Write-Host "No UTF-8 mojibake signatures found in Kotlin source roots."
