[CmdletBinding()]
param(
    [string]$Ref = "",
    [string]$Workflow = "ios-build.yml",
    [string]$OutputDirectory = "build-reports/ios",
    [switch]$SkipDownload
)

$ErrorActionPreference = "Stop"

function Invoke-Gh {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

    & gh @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "gh $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI is not installed. Install it from https://cli.github.com/."
}

Invoke-Gh auth status

if ([string]::IsNullOrWhiteSpace($Ref)) {
    $Ref = (& git branch --show-current).Trim()
}
if ([string]::IsNullOrWhiteSpace($Ref)) {
    throw "No Git ref was supplied and the repository is in detached HEAD state."
}

$startedAt = (Get-Date).ToUniversalTime().AddSeconds(-5)
Write-Host "Starting workflow '$Workflow' on '$Ref'..."
Invoke-Gh workflow run $Workflow --ref $Ref

$run = $null
for ($attempt = 0; $attempt -lt 20 -and $null -eq $run; $attempt++) {
    Start-Sleep -Seconds 3
    $runs = Invoke-Gh run list `
        --workflow $Workflow `
        --branch $Ref `
        --event workflow_dispatch `
        --limit 20 `
        --json databaseId,createdAt,status,url

    $run = ($runs | ConvertFrom-Json) |
        Where-Object { [DateTime]$_.createdAt -ge $startedAt } |
        Sort-Object { [DateTime]$_.createdAt } -Descending |
        Select-Object -First 1
}

if ($null -eq $run) {
    throw "The dispatched workflow run was not found."
}

$runId = [string]$run.databaseId
Write-Host "Following run ${runId}: $($run.url)"

$watchExitCode = 0
& gh run watch $runId --exit-status
if ($LASTEXITCODE -ne 0) {
    $watchExitCode = $LASTEXITCODE
    Write-Warning "The iOS build failed. Printing failed steps..."
    & gh run view $runId --log-failed
}

if (-not $SkipDownload) {
    $destination = Join-Path $OutputDirectory $runId
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    Write-Host "Downloading diagnostic bundle to '$destination'..."
    & gh run download $runId `
        --name "ios-build-report-$runId" `
        --dir $destination
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "The diagnostic artifact is not available yet. Download it later with:"
        Write-Warning "gh run download $runId --name ios-build-report-$runId --dir `"$destination`""
    }
}

if ($watchExitCode -ne 0) {
    exit $watchExitCode
}

Write-Host "iOS compilation completed successfully."
