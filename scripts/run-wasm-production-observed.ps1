[CmdletBinding()]
param(
    [ValidateRange(1, 120)]
    [int]$NoProgressMinutes = 10,
    [ValidateRange(1, 180)]
    [int]$MaximumMinutes = 20,
    [string]$ReportDirectory = 'build/reports/wasm-bundle'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$reportDirectory = Join-Path $root $ReportDirectory
New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
$stdout = Join-Path $reportDirectory 'gradle-production.stdout.log'
$stderr = Join-Path $reportDirectory 'gradle-production.stderr.log'
Remove-Item -LiteralPath $stdout, $stderr -ErrorAction SilentlyContinue

# Keep Kotlin's production optimization intact. This wrapper only terminates a
# diagnostic run after explicit inactivity/maximum limits and leaves evidence.
$launcher = Start-Process -FilePath (Join-Path $root 'gradlew.bat') `
    -ArgumentList ':web:wasmJsBrowserDistribution', '--no-daemon', '--console=plain', '--stacktrace' `
    -WorkingDirectory $root -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
$started = Get-Date
$launcherStartedAt = $launcher.StartTime
$lastOutputBytes = 0L
$lastProgress = $started
$samples = [System.Collections.Generic.List[object]]::new()
$stoppedByWatchdog = $false

while (-not $launcher.HasExited) {
    Start-Sleep -Seconds 15
    $launcher.Refresh()
    $tree = Get-DescendantProcessSnapshot -RootProcessId $launcher.Id -RootStartedAt $launcherStartedAt
    $stdoutItem = Get-Item $stdout -ErrorAction SilentlyContinue
    $stderrItem = Get-Item $stderr -ErrorAction SilentlyContinue
    $stdoutBytes = if ($stdoutItem) { [long]$stdoutItem.Length } else { 0L }
    $stderrBytes = if ($stderrItem) { [long]$stderrItem.Length } else { 0L }
    $outputBytes = $stdoutBytes + $stderrBytes
    if ($outputBytes -gt $lastOutputBytes) {
        $lastOutputBytes = $outputBytes
        $lastProgress = Get-Date
    }
    $samples.Add([pscustomobject]@{
        elapsedSeconds = [Math]::Round(((Get-Date) - $started).TotalSeconds, 2)
        outputBytes = $outputBytes
        processCount = $tree.Count
        workingSetBytes = [long]($tree | Measure-Object WorkingSet64 -Sum).Sum
        cpuSeconds = [Math]::Round([double]($tree | Measure-Object CPU -Sum).Sum, 2)
        processes = @($tree | Select-Object Id, ProcessName, WorkingSet64, CPU)
    })
    if (((Get-Date) - $lastProgress).TotalMinutes -gt $NoProgressMinutes -or
        ((Get-Date) - $started).TotalMinutes -gt $MaximumMinutes) {
        $stoppedByWatchdog = $true
        Stop-ProcessTree -RootProcessId $launcher.Id -RootStartedAt $launcherStartedAt
        break
    }
}

$launcher.WaitForExit()
$launcher.Refresh()
$distribution = Join-Path $root 'web/build/dist/wasmJs/productionExecutable'
$summary = [pscustomobject]@{
    task = ':web:wasmJsBrowserDistribution'
    started = $started.ToString('o')
    elapsedSeconds = [Math]::Round(((Get-Date) - $started).TotalSeconds, 2)
    exitCode = $launcher.ExitCode
    stoppedByWatchdog = $stoppedByWatchdog
    noProgressMinutes = $NoProgressMinutes
    maximumMinutes = $MaximumMinutes
    distributionExists = Test-Path $distribution
    artifacts = if (Test-Path $distribution) {
        @(Get-ChildItem -Recurse -File $distribution | ForEach-Object {
            [pscustomobject]@{
                path = $_.FullName.Substring($root.Length).TrimStart('\\', '/')
                bytes = $_.Length
            }
        } | Sort-Object path)
    } else { @() }
    samples = $samples
}
$summary | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 (Join-Path $reportDirectory 'gradle-production-observation.json')
if ($stoppedByWatchdog) {
    throw "Diagnostic watchdog stopped Gradle; see $reportDirectory. No production bundle was certified."
}
exit $launcher.ExitCode

function Get-DescendantProcessSnapshot {
    param(
        [int]$RootProcessId,
        [datetime]$RootStartedAt
    )
    $all = Get-CimInstance Win32_Process
    $pending = [System.Collections.Generic.Queue[int]]::new()
    $pending.Enqueue($RootProcessId)
    $ids = [System.Collections.Generic.List[int]]::new()
    while ($pending.Count -gt 0) {
        $parent = $pending.Dequeue()
        $ids.Add($parent)
        foreach ($child in $all | Where-Object ParentProcessId -eq $parent) { $pending.Enqueue([int]$child.ProcessId) }
    }
    return @($ids | Sort-Object -Unique | ForEach-Object {
        $process = Get-Process -Id $_ -ErrorAction SilentlyContinue
        if (-not $process) { return }
        # A PID can theoretically be recycled between the CIM snapshot and
        # this lookup. Never include a process that predates this diagnostic
        # launcher; the root must also be the exact process we started.
        try {
            if ($process.StartTime -lt $RootStartedAt) { return }
            if ($process.Id -eq $RootProcessId -and $process.StartTime -ne $RootStartedAt) { return }
            [pscustomobject]@{
                Id = $process.Id
                ProcessName = $process.ProcessName
                WorkingSet64 = $process.WorkingSet64
                CPU = $process.CPU
                StartTime = $process.StartTime
            }
        } catch { return }
    } | Where-Object { $_ })
}

function Stop-ProcessTree {
    param(
        [int]$RootProcessId,
        [datetime]$RootStartedAt
    )
    # Kill only the process tree that was created by this wrapper. Re-check
    # each StartTime immediately before killing so a recycled PID cannot turn
    # a diagnostic timeout into a broad process kill.
    Get-DescendantProcessSnapshot -RootProcessId $RootProcessId -RootStartedAt $RootStartedAt |
        Sort-Object Id -Descending |
        ForEach-Object {
            $candidate = $_
            try {
                $live = Get-Process -Id $candidate.Id -ErrorAction Stop
                if ($live.StartTime -eq $candidate.StartTime) {
                    Stop-Process -Id $candidate.Id -Force -ErrorAction Stop
                }
            } catch { }
        }
}
