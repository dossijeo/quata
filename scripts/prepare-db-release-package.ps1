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
if ($snapshotReport.status -ne "passed") {
    throw "Snapshot status is not passed."
}
if ($snapshotReport.historicalReconciliation.selectivePackageEligible -ne $true) {
    throw "Historical reconciliation does not authorize a selective package."
}

$repoRoot = if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
    (Resolve-Path -LiteralPath $RepositoryRoot).Path
}
$migrationRoot = Join-Path $repoRoot "supabase/migrations"
$reconciliationPath = Join-Path $repoRoot "supabase/migration-reconciliation.json"
if (-not (Test-Path -LiteralPath $reconciliationPath -PathType Leaf)) {
    throw "The reconciliation manifest is missing."
}
$snapshotManifestSha256 = [string]$snapshotReport.historicalReconciliation.manifestSha256
$currentManifestSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $reconciliationPath).Hash.ToLowerInvariant()
if ($snapshotManifestSha256 -notmatch "^[a-f0-9]{64}$" -or $snapshotManifestSha256 -ne $currentManifestSha256) {
    throw "Snapshot reconciliation manifest hash does not match the current checkout."
}
$snapshotDecisions = @($snapshotReport.historicalReconciliation.decisions)
if ($snapshotDecisions.Count -eq 0) {
    throw "Snapshot does not contain reconciliation decisions."
}
$snapshotLocalMigrations = @($snapshotReport.migrationHistory.local)
function Assert-SnapshotHash {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256,
        [Parameter(Mandatory = $true)][string]$Label
    )
    if ($ExpectedSha256 -notmatch "^[a-f0-9]{64}$" -or
        -not (Test-Path -LiteralPath $Path -PathType Leaf) -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant() -ne $ExpectedSha256) {
        throw "Snapshot hash does not match current $Label."
    }
}
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
    $snapshotMigration = @($snapshotLocalMigrations | Where-Object file -eq $leaf)
    if ($snapshotMigration.Count -ne 1) {
        throw "Snapshot does not identify exactly one local migration: $leaf"
    }
    Assert-SnapshotHash -Path $path -ExpectedSha256 ([string]$snapshotMigration[0].sha256) -Label "migration $leaf"
    $selected += [pscustomobject]@{
        File = $leaf
        Version = $Matches.version
        Path = $path
    }
}
if (($selected.Version | Sort-Object -Unique).Count -ne $selected.Count) {
    throw "Selected migration versions are not unique."
}

$approvedReconciliations = @($snapshotDecisions |
    Where-Object classification -eq "approved_ledger_reconciliation")
foreach ($decision in $approvedReconciliations) {
    if ($decision.approvalScope -ne "selective_package_preparation" -or
        [string]::IsNullOrWhiteSpace([string]$decision.evidenceFile) -or
        @($decision.requiredPackageMigrations).Count -eq 0 -or
        $decision.approvedLedgerReconciliationComplete -ne $true -or
        @($decision.reconciliationProblems).Count -ne 0) {
        throw "Approved reconciliation decision is incomplete: $($decision.file)"
    }
    $allowedEvidenceRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot "docs/runbooks/migration/evidence"))
    $evidencePath = [System.IO.Path]::GetFullPath((Join-Path $repoRoot ([string]$decision.evidenceFile)))
    if (-not $evidencePath.StartsWith($allowedEvidenceRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Approved reconciliation evidence escapes its allowed directory: $($decision.file)"
    }
    Assert-SnapshotHash -Path $evidencePath -ExpectedSha256 ([string]$decision.evidenceSha256) -Label "evidence $($decision.evidenceFile)"
}
$requiredPackageMigrations = @($approvedReconciliations |
    ForEach-Object { @($_.requiredPackageMigrations) } |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
    Sort-Object -Unique)
$selectedFiles = @($selected.File)
$missingRequiredMigrations = @($requiredPackageMigrations | Where-Object { $selectedFiles -notcontains $_ })
if ($missingRequiredMigrations.Count -gt 0) {
    throw "Selective package omits required reconciliation migrations: $($missingRequiredMigrations -join ', ')"
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
    Assert-SnapshotHash `
        -Path (Join-Path $migrationRoot $match.file) `
        -ExpectedSha256 ([string]$match.sha256) `
        -Label "remote ledger anchor $($match.file)"
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
    reconciliationManifestSha256 = $currentManifestSha256
    remoteLedgerAnchors = @($anchors.Version)
    selectedVersions = @($selected.Version)
    reconciliationDependencies = @($approvedReconciliations | ForEach-Object {
        [ordered]@{
            historicalMigration = $_.file
            evidenceFile = $_.evidenceFile
            requiredPackageMigrations = @($_.requiredPackageMigrations)
        }
    })
    migrations = $manifestMigrations
    nextStep = "Independent review, linked-project dry-run and explicit release-manager authorization. This package does not deploy."
}
$manifest | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 -LiteralPath (Join-Path $resolvedOutput "release-manifest.json")
Write-Host "Prepared non-deploying DB release package: $resolvedOutput"
