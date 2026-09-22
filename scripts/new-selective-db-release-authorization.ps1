[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Package,
    [Parameter(Mandatory = $true)][string]$ExpectedSourceCommit,
    [Parameter(Mandatory = $true)][string]$DatabaseProjectFingerprint
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$allowed = [IO.Path]::GetFullPath((Join-Path $root "build-reports/db-release-safety"))
$packageRoot = [IO.Path]::GetFullPath((Join-Path $root $Package))
if (-not $packageRoot.StartsWith($allowed + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Package must remain under build-reports/db-release-safety."
}
$manifestPath = Join-Path $packageRoot "release-manifest.json"
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "Release manifest is missing." }
$manifest = Get-Content -Raw -Encoding utf8 -LiteralPath $manifestPath | ConvertFrom-Json
if ($ExpectedSourceCommit -notmatch "^[a-f0-9]{40}$" -or $manifest.sourceCommit -ne $ExpectedSourceCommit -or
    $DatabaseProjectFingerprint -notmatch "^[a-f0-9]{64}$" -or $manifest.deploymentAuthorized -ne $false) {
    throw "Release manifest is not the expected reviewed preparation artifact."
}
$selected = @($manifest.migrations | Where-Object role -eq "selected_new_migration")
if ($selected.Count -eq 0) { throw "Release manifest has no selected migrations." }
$authorizationPath = Join-Path $packageRoot "release-authorization.json"
if (Test-Path -LiteralPath $authorizationPath) { throw "Release authorization already exists." }
$authorization = [ordered]@{
    schemaVersion = 1
    approved = $true
    scope = "apply_selected_package"
    authorizedAt = (Get-Date).ToUniversalTime().ToString("o")
    sourceCommit = $ExpectedSourceCommit
    releaseManifestSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $manifestPath).Hash.ToLowerInvariant()
    databaseProjectFingerprint = $DatabaseProjectFingerprint
    selectedVersions = @($selected.version)
}
[IO.File]::WriteAllText(
    $authorizationPath,
    (($authorization | ConvertTo-Json -Depth 4) + "`n"),
    [Text.UTF8Encoding]::new($false)
)
Write-Output "selective_release_authorization_created=$authorizationPath"
