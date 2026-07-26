[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string[]]$MigrationFile,
    [string]$Snapshot = "build-reports/db-release-safety/snapshot.json",
    [string]$OutputDirectory = "build-reports/db-release-safety/release-package",
    [string]$RepositoryRoot = ""
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $Snapshot -PathType Leaf)) {
    throw "A current DB release snapshot is required."
}
$snapshotReport = Get-Content -Raw -Encoding utf8 -LiteralPath $Snapshot | ConvertFrom-Json
if ($snapshotReport.check -ne "DB-RELEASE-SAFETY" -or $snapshotReport.phase -ne "snapshot") {
    throw "Snapshot has an unexpected contract."
}

$repoRoot = if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
    (Resolve-Path -LiteralPath $RepositoryRoot).Path
}
$migrationRoot = Join-Path $repoRoot "supabase/migrations"
$selected = @()
foreach ($file in $MigrationFile) {
    $leaf = Split-Path $file -Leaf
    if ($leaf -notmatch "^(?<version>[0-9]{14})_[a-z0-9_]+\.sql$") {
        throw "New migrations require a unique 14-digit timestamp: $leaf"
    }
    $path = Join-Path $migrationRoot $leaf
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Migration is not present under supabase/migrations: $leaf"
    }
    $selected += [pscustomobject]@{
        File = $leaf
        Version = $Matches.version
        Path = $path
    }
}
if (($selected.Version | Sort-Object -Unique).Count -ne $selected.Count) {
    throw "Selected migration versions are not unique."
}

$anchors = @()
foreach ($remote in $snapshotReport.migrationHistory.remote) {
    $stem = "$($remote.version)_$($remote.name)".TrimEnd("_")
    $match = $snapshotReport.migrationHistory.local | Where-Object version -eq $stem
    if ($null -eq $match -or @($match).Count -ne 1) {
        throw "Remote ledger anchor cannot be mapped exactly to one local file: $stem"
    }
    $anchors += [pscustomobject]@{
        File = $match.file
        Version = $remote.version
        Path = Join-Path $migrationRoot $match.file
    }
}

$allVersions = @($anchors.Version) + @($selected.Version)
if (($allVersions | Sort-Object -Unique).Count -ne $allVersions.Count) {
    throw "Release package has a CLI version collision."
}

$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $OutputDirectory))
$allowedRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot "build-reports/db-release-safety"))
if (-not $resolvedOutput.StartsWith($allowedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "OutputDirectory must remain under build-reports/db-release-safety."
}
if (Test-Path -LiteralPath $resolvedOutput) {
    throw "Release package output already exists; use a new empty path."
}

$packageMigrations = Join-Path $resolvedOutput "supabase/migrations"
New-Item -ItemType Directory -Path $packageMigrations -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $repoRoot "supabase/config.toml") -Destination (Join-Path $resolvedOutput "supabase/config.toml")

$manifestMigrations = @()
foreach ($item in @($anchors) + @($selected)) {
    $destination = Join-Path $packageMigrations $item.File
    Copy-Item -LiteralPath $item.Path -Destination $destination
    $manifestMigrations += [ordered]@{
        file = $item.File
        version = $item.Version
        role = if ($anchors.File -contains $item.File) { "remote_ledger_anchor" } else { "selected_new_migration" }
        sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $destination).Hash.ToLowerInvariant()
    }
}

$manifest = [ordered]@{
    schemaVersion = 1
    createdAt = (Get-Date).ToUniversalTime().ToString("o")
    sourceCommit = if (Test-Path -LiteralPath (Join-Path $repoRoot ".git")) {
        (git -C $repoRoot rev-parse HEAD).Trim()
    } else {
        "disposable-test-fixture"
    }
    deploymentAuthorized = $false
    remoteLedgerAnchors = @($anchors.Version)
    selectedVersions = @($selected.Version)
    migrations = $manifestMigrations
    nextStep = "Independent review, linked-project dry-run and explicit release-manager authorization. This package does not deploy."
}
$manifest | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 -LiteralPath (Join-Path $resolvedOutput "release-manifest.json")
Write-Host "Prepared non-deploying DB release package: $resolvedOutput"
