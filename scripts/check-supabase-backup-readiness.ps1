[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$DbUrlFile,
    [string]$Output = "build-reports/db-release-safety/backup-readiness.json"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$allowedRoot = [IO.Path]::GetFullPath((Join-Path $root "build-reports/db-release-safety"))
$outputPath = [IO.Path]::GetFullPath((Join-Path $root $Output))
if (-not $outputPath.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Output must remain under build-reports/db-release-safety."
}
if (-not (Test-Path -LiteralPath $DbUrlFile -PathType Leaf)) {
    throw "Database URL file was not found."
}
if (-not (Get-Command npx -ErrorAction SilentlyContinue)) {
    throw "npx is required."
}

$rawUrl = (Get-Content -LiteralPath $DbUrlFile -Raw).Trim()
if ([string]::IsNullOrWhiteSpace($rawUrl)) {
    throw "Database URL file is empty."
}
$uri = [Uri]$rawUrl
$poolerUser = [Uri]::UnescapeDataString(($uri.UserInfo -split ":", 2)[0])
$projectRef = ($poolerUser -split "\.")[-1]
if ($projectRef -notmatch "^[a-z]{20}$") {
    throw "Could not derive a Supabase project ref from the pooler username."
}

$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$projectsJson = & npx --yes supabase@2.109.1 projects list --output json 2>$null
$projectsExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorAction
if ($projectsExitCode -ne 0) { throw "Unable to query Supabase projects." }
$projects = $projectsJson | ConvertFrom-Json
$project = @($projects | Where-Object ref -eq $projectRef)
if ($project.Count -ne 1) {
    throw "The DB URL project does not map to exactly one accessible Supabase project."
}

$ErrorActionPreference = "Continue"
$backupsJson = & npx --yes supabase@2.109.1 backups list --project-ref $projectRef --output json 2>$null
$backupsExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorAction
if ($backupsExitCode -ne 0) { throw "Unable to query Supabase backup status." }
$backupStatus = $backupsJson | ConvertFrom-Json
$backups = @($backupStatus.backups | Where-Object { $null -ne $_ })
$physicalEntries = @($backupStatus.physical_backup_data.psobject.Properties)
$hasVerifiedRestorePoint = $backups.Count -gt 0 -or $physicalEntries.Count -gt 0
$pitrEnabled = $backupStatus.pitr_enabled -eq $true
$releaseReady = $project[0].status -eq "ACTIVE_HEALTHY" -and ($pitrEnabled -or $hasVerifiedRestorePoint)

$sha = [Security.Cryptography.SHA256]::Create()
try {
    $projectHash = [Convert]::ToBase64String(
        $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($projectRef))
    )
} finally {
    $sha.Dispose()
}

$report = [ordered]@{
    check = "SUPABASE-BACKUP-READINESS"
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    cliVersion = "2.109.1"
    projectRefSha256Base64 = $projectHash
    projectStatus = $project[0].status
    region = $backupStatus.region
    pitrEnabled = $pitrEnabled
    walgEnabled = $backupStatus.walg_enabled -eq $true
    listedBackupCount = $backups.Count
    physicalBackupEntryCount = $physicalEntries.Count
    hasVerifiedRestorePoint = $hasVerifiedRestorePoint
    releaseReady = $releaseReady
    decision = if ($releaseReady) {
        "backup_or_pitr_verified"
    } else {
        "blocked_no_verifiable_restore_point"
    }
}

New-Item -ItemType Directory -Path (Split-Path $outputPath -Parent) -Force | Out-Null
$report | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $outputPath -Encoding utf8
Write-Output "Supabase backup readiness: $($report.decision); report=$outputPath"
if (-not $releaseReady) { exit 2 }
