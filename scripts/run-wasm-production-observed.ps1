[CmdletBinding()]
param(
    [ValidateRange(1, 120)]
    [int]$NoProgressMinutes = 10,
    [ValidateRange(1, 180)]
    [int]$MaximumMinutes = 20,
    [string]$ReportDirectory = 'build/reports/wasm-bundle',
    # Kept for the executable, no-Gradle contract test below.  It is deliberately
    # opt-in so production invocations retain their existing command line.
    [switch]$ContractTest
)

$ErrorActionPreference = 'Stop'

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

function Add-ProcessIdentitySnapshot {
    param(
        [System.Collections.IDictionary]$KnownProcesses,
        [object[]]$Snapshot
    )
    foreach ($process in $Snapshot) {
        # PID plus creation time is the identity; a PID alone is never enough
        # to authorize a later kill.
        $KnownProcesses["$($process.Id)|$($process.StartTime.Ticks)"] = [pscustomobject]@{
            Id = [int]$process.Id
            StartTime = [datetime]$process.StartTime
        }
    }
}

function Stop-KnownProcessIdentities {
    param(
        [System.Collections.IDictionary]$KnownProcesses,
        [int]$ExcludeProcessId = 0
    )
    # A root may have exited before its leaked child is inspected.  At that
    # point its PPID is no longer reliable, so only terminate identities that
    # were observed as descendants while the root was still alive.
    foreach ($candidate in @($KnownProcesses.Values | Sort-Object Id -Descending)) {
        if ($candidate.Id -eq $ExcludeProcessId) { continue }
        try {
            $live = Get-Process -Id $candidate.Id -ErrorAction Stop
            if ($live.StartTime -eq $candidate.StartTime) {
                Stop-Process -Id $candidate.Id -Force -ErrorAction Stop
            }
        } catch { }
    }
}

function Get-LiveKnownProcessIdentities {
    param(
        [System.Collections.IDictionary]$KnownProcesses,
        [int]$ExcludeProcessId = 0
    )
    return @($KnownProcesses.Values | Where-Object {
        if ($_.Id -eq $ExcludeProcessId) { return $false }
        try {
            $live = Get-Process -Id $_.Id -ErrorAction Stop
            return $live.StartTime -eq $_.StartTime
        } catch { return $false }
    })
}

function Wait-KnownProcessIdentitiesExit {
    param(
        [System.Collections.IDictionary]$KnownProcesses,
        [int]$ExcludeProcessId = 0,
        [int]$TimeoutSeconds = 3
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $remaining = Get-LiveKnownProcessIdentities -KnownProcesses $KnownProcesses -ExcludeProcessId $ExcludeProcessId
        if ($remaining.Count -eq 0) { return @() }
        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $deadline)
    return @(Get-LiveKnownProcessIdentities -KnownProcesses $KnownProcesses -ExcludeProcessId $ExcludeProcessId)
}

function Stop-ProcessTree {
    param(
        [int]$RootProcessId,
        [datetime]$RootStartedAt,
        [System.Collections.IDictionary]$KnownProcesses
    )
    # Take one final live tree snapshot before stopping it, then use the
    # identity ledger as the root and its descendants disappear/re-parent.
    $snapshot = Get-DescendantProcessSnapshot -RootProcessId $RootProcessId -RootStartedAt $RootStartedAt
    Add-ProcessIdentitySnapshot -KnownProcesses $KnownProcesses -Snapshot $snapshot
    Stop-KnownProcessIdentities -KnownProcesses $KnownProcesses
}

function Invoke-WasmProductionWatchdogContractTest {
    $unrelated = Start-Process -FilePath "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" `
        -ArgumentList '-NoProfile', '-Command', 'Start-Sleep -Seconds 30' -PassThru
    $rootCommand = '$child = Start-Process -FilePath "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -ArgumentList ''-NoProfile'', ''-Command'', ''Start-Sleep -Seconds 30'' -PassThru; Start-Sleep -Seconds 3'
    $root = Start-Process -FilePath "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" `
        -ArgumentList '-NoProfile', '-Command', $rootCommand -PassThru
    $known = @{}
    try {
        $deadline = (Get-Date).AddSeconds(10)
        do {
            $root.Refresh()
            $snapshot = Get-DescendantProcessSnapshot -RootProcessId $root.Id -RootStartedAt $root.StartTime
            Add-ProcessIdentitySnapshot -KnownProcesses $known -Snapshot $snapshot
            # conhost can briefly be the first child. Wait for the actual
            # long-lived fixture instead of treating that transient helper as
            # proof that the descendant ledger saw the child.
            if (@($snapshot | Where-Object { $_.Id -ne $root.Id -and $_.ProcessName -eq 'powershell' }).Count -ge 1) { break }
            Start-Sleep -Milliseconds 100
        } while ((Get-Date) -lt $deadline -and -not $root.HasExited)
        if (@($snapshot | Where-Object { $_.Id -ne $root.Id -and $_.ProcessName -eq 'powershell' }).Count -lt 1) {
            throw 'Contract fixture did not record its long-lived root child.'
        }
        $root.WaitForExit()
        Stop-KnownProcessIdentities -KnownProcesses $known -ExcludeProcessId $root.Id
        $remainingKnown = Wait-KnownProcessIdentitiesExit -KnownProcesses $known -ExcludeProcessId $root.Id
        if ($remainingKnown.Count -gt 0) { throw "Observed descendant(s) remained alive after cleanup: $($remainingKnown.Id -join ', ')." }
        if (-not (Get-Process -Id $unrelated.Id -ErrorAction SilentlyContinue)) { throw 'Cleanup terminated an unrelated process.' }
        $staleIdentity = @{
            "stale-$($unrelated.Id)" = [pscustomobject]@{
                Id = $unrelated.Id
                StartTime = $unrelated.StartTime.AddTicks(-1)
            }
        }
        Stop-KnownProcessIdentities -KnownProcesses $staleIdentity
        if (-not (Get-Process -Id $unrelated.Id -ErrorAction SilentlyContinue)) { throw 'Cleanup accepted a PID with a mismatched creation time.' }
        Write-Output 'PASS: orphan descendant cleanup leaves unrelated and stale-PID identities alive.'
    } finally {
        Stop-Process -Id $root.Id, $unrelated.Id -Force -ErrorAction SilentlyContinue
        Stop-KnownProcessIdentities -KnownProcesses $known -ExcludeProcessId $root.Id
    }
}

if ($ContractTest) {
    Invoke-WasmProductionWatchdogContractTest
    exit 0
}

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
$knownProcesses = @{}
$stoppedByWatchdog = $false
$sampleIntervalSeconds = 2

# Record the launcher identity before the first sleep. This closes the usual
# race where gradlew starts a JVM before the first watchdog sample.
$initialTree = Get-DescendantProcessSnapshot -RootProcessId $launcher.Id -RootStartedAt $launcherStartedAt
Add-ProcessIdentitySnapshot -KnownProcesses $knownProcesses -Snapshot $initialTree

while (-not $launcher.HasExited) {
    Start-Sleep -Seconds $sampleIntervalSeconds
    $launcher.Refresh()
    $tree = Get-DescendantProcessSnapshot -RootProcessId $launcher.Id -RootStartedAt $launcherStartedAt
    Add-ProcessIdentitySnapshot -KnownProcesses $knownProcesses -Snapshot $tree
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
        Stop-ProcessTree -RootProcessId $launcher.Id -RootStartedAt $launcherStartedAt -KnownProcesses $knownProcesses
        break
    }
}

$launcher.WaitForExit()
$launcher.Refresh()
# The Gradle launcher may exit while a child JVM remains alive.  Its parent
# relationship is then lost, so drain only the identities sampled from this
# exact launcher tree. This is intentionally also done after a successful run.
Stop-KnownProcessIdentities -KnownProcesses $knownProcesses -ExcludeProcessId $launcher.Id
$remainingTrackedProcesses = Wait-KnownProcessIdentitiesExit -KnownProcesses $knownProcesses -ExcludeProcessId $launcher.Id
$distribution = Join-Path $root 'web/build/dist/wasmJs/productionExecutable'
$summary = [pscustomobject]@{
    task = ':web:wasmJsBrowserDistribution'
    started = $started.ToString('o')
    elapsedSeconds = [Math]::Round(((Get-Date) - $started).TotalSeconds, 2)
    exitCode = $launcher.ExitCode
    stoppedByWatchdog = $stoppedByWatchdog
    noProgressMinutes = $NoProgressMinutes
    maximumMinutes = $MaximumMinutes
    remainingTrackedProcessCount = $remainingTrackedProcesses.Count
    remainingTrackedProcesses = @($remainingTrackedProcesses | Select-Object Id, StartTime)
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
if ($remainingTrackedProcesses.Count -gt 0) {
    throw "Diagnostic cleanup could not stop tracked Gradle descendant(s): $($remainingTrackedProcesses.Id -join ', '). No production bundle was certified."
}
exit $launcher.ExitCode
