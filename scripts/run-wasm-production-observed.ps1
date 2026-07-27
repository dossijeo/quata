[CmdletBinding()]
param(
    [ValidateRange(1, 120)]
    [int]$NoProgressMinutes = 10,
    [ValidateRange(1, 180)]
    [int]$MaximumMinutes = 20,
    [string]$ReportDirectory = 'build/reports/wasm-bundle',
    # Runs deterministic, no-Gradle ownership and cleanup contracts.
    [switch]$ContractTest
)

$ErrorActionPreference = 'Stop'

function Initialize-WindowsJobObjectInterop {
    if ('Quata.Watchdog.JobProcessLease' -as [type]) { return }
    if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') {
        throw 'windows_job_object_required'
    }

    Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;

namespace Quata.Watchdog
{
    public sealed class JobRootState
    {
        public bool Running { get; private set; }
        public int ExitCode { get; private set; }

        public JobRootState(bool running, int exitCode)
        {
            Running = running;
            ExitCode = exitCode;
        }
    }

    public sealed class JobProcessLease : IDisposable
    {
        private const uint CREATE_SUSPENDED = 0x00000004;
        private const uint CREATE_NO_WINDOW = 0x08000000;
        private const uint JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x00002000;
        private const uint STILL_ACTIVE = 259;
        private const uint WAIT_OBJECT_0 = 0;
        private const uint WAIT_TIMEOUT = 258;
        private const uint WAIT_FAILED = 0xFFFFFFFF;
        private const int JobObjectBasicProcessIdList = 3;
        private const int JobObjectExtendedLimitInformation = 9;

        private IntPtr jobHandle;
        private IntPtr processHandle;
        private bool disposed;

        public int ProcessId { get; private set; }

        private JobProcessLease(IntPtr jobHandle, IntPtr processHandle, int processId)
        {
            this.jobHandle = jobHandle;
            this.processHandle = processHandle;
            ProcessId = processId;
        }

        public static JobProcessLease Start(
            string applicationPath,
            string commandLine,
            string workingDirectory)
        {
            IntPtr job = IntPtr.Zero;
            PROCESS_INFORMATION processInformation = new PROCESS_INFORMATION();
            try
            {
                job = CreateJobObject(IntPtr.Zero, null);
                if (job == IntPtr.Zero) ThrowLastWin32("CreateJobObject");

                JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits =
                    new JOBOBJECT_EXTENDED_LIMIT_INFORMATION();
                limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
                int limitsSize = Marshal.SizeOf(typeof(JOBOBJECT_EXTENDED_LIMIT_INFORMATION));
                IntPtr limitsPointer = Marshal.AllocHGlobal(limitsSize);
                try
                {
                    Marshal.StructureToPtr(limits, limitsPointer, false);
                    if (!SetInformationJobObject(
                        job,
                        JobObjectExtendedLimitInformation,
                        limitsPointer,
                        (uint)limitsSize))
                    {
                        ThrowLastWin32("SetInformationJobObject");
                    }
                }
                finally
                {
                    Marshal.FreeHGlobal(limitsPointer);
                }

                STARTUPINFO startup = new STARTUPINFO();
                startup.cb = Marshal.SizeOf(typeof(STARTUPINFO));
                StringBuilder mutableCommandLine = new StringBuilder(commandLine);
                if (!CreateProcess(
                    applicationPath,
                    mutableCommandLine,
                    IntPtr.Zero,
                    IntPtr.Zero,
                    false,
                    CREATE_SUSPENDED | CREATE_NO_WINDOW,
                    IntPtr.Zero,
                    workingDirectory,
                    ref startup,
                    out processInformation))
                {
                    ThrowLastWin32("CreateProcess");
                }

                if (!AssignProcessToJobObject(job, processInformation.hProcess))
                {
                    ThrowLastWin32("AssignProcessToJobObject");
                }
                if (ResumeThread(processInformation.hThread) == UInt32.MaxValue)
                {
                    ThrowLastWin32("ResumeThread");
                }

                CloseHandle(processInformation.hThread);
                processInformation.hThread = IntPtr.Zero;
                JobProcessLease lease = new JobProcessLease(
                    job,
                    processInformation.hProcess,
                    unchecked((int)processInformation.dwProcessId));
                job = IntPtr.Zero;
                processInformation.hProcess = IntPtr.Zero;
                return lease;
            }
            catch
            {
                if (processInformation.hProcess != IntPtr.Zero)
                {
                    TerminateProcess(processInformation.hProcess, 1);
                }
                throw;
            }
            finally
            {
                if (processInformation.hThread != IntPtr.Zero)
                {
                    CloseHandle(processInformation.hThread);
                }
                if (processInformation.hProcess != IntPtr.Zero)
                {
                    CloseHandle(processInformation.hProcess);
                }
                if (job != IntPtr.Zero)
                {
                    CloseHandle(job);
                }
            }
        }

        public JobRootState GetRootState()
        {
            EnsureOpen();
            uint exitCode;
            if (!GetExitCodeProcess(processHandle, out exitCode))
            {
                ThrowLastWin32("GetExitCodeProcess");
            }
            return new JobRootState(
                exitCode == STILL_ACTIVE,
                exitCode == STILL_ACTIVE ? 0 : unchecked((int)exitCode));
        }

        public long[] GetProcessIds()
        {
            EnsureOpen();
            int capacity = 16;
            while (capacity <= 4096)
            {
                int headerBytes = sizeof(uint) * 2;
                int pointerBytes = IntPtr.Size * capacity;
                IntPtr buffer = Marshal.AllocHGlobal(headerBytes + pointerBytes);
                try
                {
                    uint returned;
                    bool ok = QueryInformationJobObject(
                        jobHandle,
                        JobObjectBasicProcessIdList,
                        buffer,
                        (uint)(headerBytes + pointerBytes),
                        out returned);
                    int error = ok ? 0 : Marshal.GetLastWin32Error();
                    uint assigned = unchecked((uint)Marshal.ReadInt32(buffer, 0));
                    uint listed = unchecked((uint)Marshal.ReadInt32(buffer, sizeof(uint)));
                    if (ok || (error == 234 && assigned > listed))
                    {
                        if (assigned > listed)
                        {
                            capacity = Math.Max(capacity * 2, checked((int)assigned));
                            continue;
                        }
                        List<long> ids = new List<long>();
                        for (int index = 0; index < listed; index++)
                        {
                            IntPtr value = Marshal.ReadIntPtr(
                                buffer,
                                headerBytes + (index * IntPtr.Size));
                            ids.Add(value.ToInt64());
                        }
                        return ids.ToArray();
                    }
                    throw new Win32Exception(error, "QueryInformationJobObject failed");
                }
                finally
                {
                    Marshal.FreeHGlobal(buffer);
                }
            }
            throw new InvalidOperationException("Job process list exceeded the supported diagnostic capacity.");
        }

        public void StopAndWait(int timeoutMilliseconds)
        {
            EnsureOpen();
            if (!TerminateJobObject(jobHandle, 1))
            {
                ThrowLastWin32("TerminateJobObject");
            }
            uint wait = WaitForSingleObject(jobHandle, unchecked((uint)timeoutMilliseconds));
            if (wait == WAIT_TIMEOUT)
            {
                throw new TimeoutException("Job Object did not become empty after termination.");
            }
            if (wait == WAIT_FAILED)
            {
                ThrowLastWin32("WaitForSingleObject");
            }
            if (wait != WAIT_OBJECT_0)
            {
                throw new InvalidOperationException("Unexpected Job Object wait result: " + wait);
            }
        }

        public void Dispose()
        {
            if (disposed) return;
            disposed = true;
            if (jobHandle != IntPtr.Zero)
            {
                // KILL_ON_JOB_CLOSE is the final backstop even if explicit
                // termination or PowerShell cleanup was interrupted.
                CloseHandle(jobHandle);
                jobHandle = IntPtr.Zero;
            }
            if (processHandle != IntPtr.Zero)
            {
                CloseHandle(processHandle);
                processHandle = IntPtr.Zero;
            }
            GC.SuppressFinalize(this);
        }

        ~JobProcessLease()
        {
            Dispose();
        }

        private void EnsureOpen()
        {
            if (disposed || jobHandle == IntPtr.Zero || processHandle == IntPtr.Zero)
            {
                throw new ObjectDisposedException("JobProcessLease");
            }
        }

        private static void ThrowLastWin32(string operation)
        {
            int error = Marshal.GetLastWin32Error();
            throw new Win32Exception(error, operation + " failed");
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct IO_COUNTERS
        {
            public UInt64 ReadOperationCount;
            public UInt64 WriteOperationCount;
            public UInt64 OtherOperationCount;
            public UInt64 ReadTransferCount;
            public UInt64 WriteTransferCount;
            public UInt64 OtherTransferCount;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct JOBOBJECT_BASIC_LIMIT_INFORMATION
        {
            public Int64 PerProcessUserTimeLimit;
            public Int64 PerJobUserTimeLimit;
            public UInt32 LimitFlags;
            public UIntPtr MinimumWorkingSetSize;
            public UIntPtr MaximumWorkingSetSize;
            public UInt32 ActiveProcessLimit;
            public UIntPtr Affinity;
            public UInt32 PriorityClass;
            public UInt32 SchedulingClass;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct JOBOBJECT_EXTENDED_LIMIT_INFORMATION
        {
            public JOBOBJECT_BASIC_LIMIT_INFORMATION BasicLimitInformation;
            public IO_COUNTERS IoInfo;
            public UIntPtr ProcessMemoryLimit;
            public UIntPtr JobMemoryLimit;
            public UIntPtr PeakProcessMemoryUsed;
            public UIntPtr PeakJobMemoryUsed;
        }

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct STARTUPINFO
        {
            public Int32 cb;
            public string lpReserved;
            public string lpDesktop;
            public string lpTitle;
            public Int32 dwX;
            public Int32 dwY;
            public Int32 dwXSize;
            public Int32 dwYSize;
            public Int32 dwXCountChars;
            public Int32 dwYCountChars;
            public Int32 dwFillAttribute;
            public Int32 dwFlags;
            public Int16 wShowWindow;
            public Int16 cbReserved2;
            public IntPtr lpReserved2;
            public IntPtr hStdInput;
            public IntPtr hStdOutput;
            public IntPtr hStdError;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct PROCESS_INFORMATION
        {
            public IntPtr hProcess;
            public IntPtr hThread;
            public UInt32 dwProcessId;
            public UInt32 dwThreadId;
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern IntPtr CreateJobObject(IntPtr jobAttributes, string name);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool SetInformationJobObject(
            IntPtr job,
            int informationClass,
            IntPtr information,
            uint informationLength);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool CreateProcess(
            string applicationName,
            StringBuilder commandLine,
            IntPtr processAttributes,
            IntPtr threadAttributes,
            bool inheritHandles,
            uint creationFlags,
            IntPtr environment,
            string currentDirectory,
            ref STARTUPINFO startupInfo,
            out PROCESS_INFORMATION processInformation);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern uint ResumeThread(IntPtr thread);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool GetExitCodeProcess(IntPtr process, out uint exitCode);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool QueryInformationJobObject(
            IntPtr job,
            int informationClass,
            IntPtr information,
            uint informationLength,
            out uint returnLength);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool TerminateJobObject(IntPtr job, uint exitCode);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool TerminateProcess(IntPtr process, uint exitCode);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern uint WaitForSingleObject(IntPtr handle, uint milliseconds);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool CloseHandle(IntPtr handle);
    }
}
'@
}

function ConvertTo-PowerShellEncodedCommand {
    param([Parameter(Mandatory = $true)][string]$Script)
    return [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($Script))
}

function Get-JobProcessSnapshot {
    param([Parameter(Mandatory = $true)]$Lease)
    $snapshot = [System.Collections.Generic.List[object]]::new()
    foreach ($id in @($Lease.GetProcessIds())) {
        try {
            $process = Get-Process -Id ([int]$id) -ErrorAction Stop
            $snapshot.Add([pscustomobject]@{
                Id = $process.Id
                ProcessName = $process.ProcessName
                WorkingSet64 = $process.WorkingSet64
                CPU = $process.CPU
            })
        } catch [Microsoft.PowerShell.Commands.ProcessCommandException] {
            # A member can exit between the kernel membership query and the
            # diagnostic lookup. The Job Object still owns every live member.
            continue
        } catch [InvalidOperationException] {
            # Process properties become unavailable when the process exits
            # after Get-Process returned it. This is not an ownership gap.
            continue
        } catch [System.ComponentModel.Win32Exception] {
            throw
        } catch [System.UnauthorizedAccessException] {
            throw
        }
    }
    return @($snapshot)
}

function Invoke-OwnedWatchdogProcess {
    param(
        [Parameter(Mandatory = $true)][string]$CommandScript,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string]$StdoutPath,
        [Parameter(Mandatory = $true)][string]$StderrPath,
        [int]$NoProgressSeconds,
        [int]$MaximumSeconds,
        [int]$SampleIntervalMilliseconds = 2000,
        [scriptblock]$StateProbe,
        [scriptblock]$CancellationProbe
    )

    Initialize-WindowsJobObjectInterop
    Remove-Item -LiteralPath $StdoutPath, $StderrPath -ErrorAction SilentlyContinue
    $quotedStdout = $StdoutPath.Replace("'", "''")
    $quotedStderr = $StderrPath.Replace("'", "''")
    $wrappedScript = @"
`$ErrorActionPreference = 'Stop'
try {
    & {
$CommandScript
    } 1> '$quotedStdout' 2> '$quotedStderr'
    exit `$LASTEXITCODE
} catch {
    `$_ | Out-String | Add-Content -LiteralPath '$quotedStderr'
    exit 1
}
"@
    $powershellPath = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
    $encodedCommand = ConvertTo-PowerShellEncodedCommand -Script $wrappedScript
    $nativeCommandLine = "`"$powershellPath`" -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -EncodedCommand $encodedCommand"

    $started = Get-Date
    $lastProgress = $started
    $lastOutputBytes = 0L
    $samples = [System.Collections.Generic.List[object]]::new()
    $lease = $null
    $outcome = 'starting'
    $exitCode = $null
    $failure = $null
    $cleanupState = 'not_started'
    $iteration = 0

    try {
        $lease = [Quata.Watchdog.JobProcessLease]::Start(
            $powershellPath,
            $nativeCommandLine,
            $WorkingDirectory)
        $outcome = 'running'
        while ($true) {
            $iteration += 1
            if ($CancellationProbe -and (& $CancellationProbe $iteration $lease)) {
                throw [OperationCanceledException]::new('watchdog_cancelled')
            }

            try {
                $state = if ($StateProbe) {
                    & $StateProbe $lease $iteration
                } else {
                    $nativeState = $lease.GetRootState()
                    [pscustomobject]@{
                        State = if ($nativeState.Running) { 'Running' } else { 'Exited' }
                        ExitCode = $nativeState.ExitCode
                        Error = $null
                    }
                }
            } catch [System.ComponentModel.Win32Exception] {
                $outcome = 'state_unknown'
                $failure = "native_state_query_failed:$($_.Exception.NativeErrorCode)"
                break
            } catch [System.UnauthorizedAccessException] {
                $outcome = 'state_unknown'
                $failure = 'native_state_query_access_denied'
                break
            }

            if (-not $state -or $state.State -notin @('Running', 'Exited')) {
                $outcome = 'state_unknown'
                $failure = if ($state -and $state.Error) { "native_state_unknown:$($state.Error)" } else { 'native_state_unknown' }
                break
            }
            if ($state.State -eq 'Exited') {
                $exitCode = [int]$state.ExitCode
                $outcome = if ($exitCode -eq 0) { 'success' } else { 'process_failure' }
                break
            }

            try {
                $tree = Get-JobProcessSnapshot -Lease $lease
            } catch [System.ComponentModel.Win32Exception] {
                $outcome = 'state_unknown'
                $failure = "job_membership_query_failed:$($_.Exception.NativeErrorCode)"
                break
            } catch [System.UnauthorizedAccessException] {
                $outcome = 'state_unknown'
                $failure = 'job_membership_query_access_denied'
                break
            }

            $stdoutItem = Get-Item -LiteralPath $StdoutPath -ErrorAction SilentlyContinue
            $stderrItem = Get-Item -LiteralPath $StderrPath -ErrorAction SilentlyContinue
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
                processes = @($tree)
            })

            $now = Get-Date
            if (($now - $lastProgress).TotalSeconds -ge $NoProgressSeconds) {
                $outcome = 'no_progress_timeout'
                $failure = 'no_progress_timeout'
                break
            }
            if (($now - $started).TotalSeconds -ge $MaximumSeconds) {
                $outcome = 'maximum_timeout'
                $failure = 'maximum_timeout'
                break
            }
            Start-Sleep -Milliseconds $SampleIntervalMilliseconds
        }
    } catch [System.Management.Automation.PipelineStoppedException] {
        $outcome = 'cancelled'
        $failure = 'pipeline_cancelled'
    } catch [OperationCanceledException] {
        $outcome = 'cancelled'
        $failure = $_.Exception.Message
    } catch [System.ComponentModel.Win32Exception] {
        $outcome = if ($_.Exception.NativeErrorCode -eq 5) { 'state_unknown' } else { 'error' }
        $failure = "native_operation_failed:$($_.Exception.NativeErrorCode)"
    } catch [System.UnauthorizedAccessException] {
        $outcome = 'state_unknown'
        $failure = 'native_operation_access_denied'
    } catch {
        $outcome = 'error'
        $failure = $_.Exception.Message
    } finally {
        if ($lease) {
            try {
                $lease.StopAndWait(5000)
                $cleanupState = 'job_terminated_and_empty'
            } catch {
                $cleanupState = 'cleanup_failed'
                if (-not $failure) { $failure = $_.Exception.Message }
                $outcome = 'cleanup_failed'
            } finally {
                $lease.Dispose()
            }
        } else {
            $cleanupState = 'no_job_created'
        }
    }

    return [pscustomobject]@{
        Outcome = $outcome
        ExitCode = $exitCode
        Failure = $failure
        CleanupState = $cleanupState
        RootProcessId = if ($lease) { $lease.ProcessId } else { $null }
        Started = $started
        ElapsedSeconds = [Math]::Round(((Get-Date) - $started).TotalSeconds, 2)
        Samples = $samples
    }
}

function Wait-ProcessIdFile {
    param([string]$Path, [int]$TimeoutSeconds = 5)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if (Test-Path -LiteralPath $Path) {
            $value = (Get-Content -LiteralPath $Path -Raw).Trim()
            if ($value -match '^\d+$') { return [int]$value }
        }
        Start-Sleep -Milliseconds 50
    } while ((Get-Date) -lt $deadline)
    throw "Fixture did not publish a child PID: $Path"
}

function Assert-ProcessExited {
    param([int]$ProcessId, [string]$Message)
    $deadline = (Get-Date).AddSeconds(3)
    do {
        if (-not (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)) { return }
        Start-Sleep -Milliseconds 50
    } while ((Get-Date) -lt $deadline)
    throw $Message
}

function Invoke-WasmProductionWatchdogContractTest {
    Initialize-WindowsJobObjectInterop
    $contractRoot = Join-Path ([IO.Path]::GetTempPath()) ("quata-wasm-watchdog-contract-" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $contractRoot | Out-Null
    $unrelated = Start-Process -FilePath "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" `
        -ArgumentList '-NoProfile', '-Command', 'Start-Sleep -Seconds 60' -PassThru
    try {
        $scenarios = @(
            [pscustomobject]@{ Name = 'success'; Expected = 'success'; Exit = 0; Timeout = 10; Cancel = $null; State = $null },
            [pscustomobject]@{ Name = 'failure'; Expected = 'process_failure'; Exit = 7; Timeout = 10; Cancel = $null; State = $null },
            [pscustomobject]@{ Name = 'timeout'; Expected = 'maximum_timeout'; Exit = $null; Timeout = 1; Cancel = $null; State = $null },
            [pscustomobject]@{
                Name = 'cancel'
                Expected = 'cancelled'
                Exit = $null
                Timeout = 10
                Cancel = { param($iteration, $lease) return $iteration -ge 15 }
                State = $null
            },
            [pscustomobject]@{
                Name = 'access-denied'
                Expected = 'state_unknown'
                Exit = $null
                Timeout = 10
                Cancel = $null
                State = {
                    param($lease, $iteration)
                    if ($iteration -lt 15) {
                        $nativeState = $lease.GetRootState()
                        return [pscustomobject]@{
                            State = if ($nativeState.Running) { 'Running' } else { 'Exited' }
                            ExitCode = $nativeState.ExitCode
                            Error = $null
                        }
                    }
                    return [pscustomobject]@{ State = 'Unknown'; Error = 'AccessDenied' }
                }
            },
            [pscustomobject]@{
                Name = 'error'
                Expected = 'error'
                Exit = $null
                Timeout = 10
                Cancel = $null
                State = {
                    param($lease, $iteration)
                    if ($iteration -lt 15) {
                        $nativeState = $lease.GetRootState()
                        return [pscustomobject]@{
                            State = if ($nativeState.Running) { 'Running' } else { 'Exited' }
                            ExitCode = $nativeState.ExitCode
                            Error = $null
                        }
                    }
                    throw [InvalidOperationException]::new('injected_state_probe_error')
                }
            }
        )

        foreach ($scenario in $scenarios) {
            $childPidPath = Join-Path $contractRoot "$($scenario.Name)-child.pid"
            $quotedChildPidPath = $childPidPath.Replace("'", "''")
            $childCommand = @"
`$child = Start-Process -FilePath '$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe' -ArgumentList '-NoProfile', '-Command', 'Start-Sleep -Seconds 60' -PassThru
Set-Content -LiteralPath '$quotedChildPidPath' -Value `$child.Id
"@
            if ($null -ne $scenario.Exit) {
                $childCommand += "`nexit $($scenario.Exit)"
            } else {
                $childCommand += "`nStart-Sleep -Seconds 60"
            }

            $result = Invoke-OwnedWatchdogProcess `
                -CommandScript $childCommand `
                -WorkingDirectory $contractRoot `
                -StdoutPath (Join-Path $contractRoot "$($scenario.Name).stdout.log") `
                -StderrPath (Join-Path $contractRoot "$($scenario.Name).stderr.log") `
                -NoProgressSeconds 20 `
                -MaximumSeconds $scenario.Timeout `
                -SampleIntervalMilliseconds 100 `
                -StateProbe $scenario.State `
                -CancellationProbe $scenario.Cancel

            if ($result.Outcome -ne $scenario.Expected) {
                throw "Scenario $($scenario.Name) returned $($result.Outcome), expected $($scenario.Expected): $($result.Failure)"
            }
            if ($result.CleanupState -ne 'job_terminated_and_empty') {
                throw "Scenario $($scenario.Name) did not confirm Job Object cleanup: $($result.CleanupState)"
            }
            $childPid = Wait-ProcessIdFile -Path $childPidPath
            Assert-ProcessExited -ProcessId $childPid -Message "Scenario $($scenario.Name) leaked child PID $childPid."
            if ($null -ne $scenario.Exit -and $result.ExitCode -ne $scenario.Exit) {
                throw "Scenario $($scenario.Name) returned exit $($result.ExitCode), expected $($scenario.Exit)."
            }
            if (-not (Get-Process -Id $unrelated.Id -ErrorAction SilentlyContinue)) {
                throw "Scenario $($scenario.Name) terminated an unrelated process."
            }
        }

        # A reused PID is not actionable input: cleanup is kernel Job Object
        # membership, never a later PID lookup or parent-chain guess.
        $stalePidIdentity = [pscustomobject]@{
            Id = $unrelated.Id
            StartTime = $unrelated.StartTime.AddTicks(-1)
        }
        if ($stalePidIdentity.Id -ne $unrelated.Id) { throw 'Stale PID fixture was not constructed.' }
        if (-not (Get-Process -Id $unrelated.Id -ErrorAction SilentlyContinue)) {
            throw 'Kernel-owned cleanup accepted an unrelated/reused PID candidate.'
        }
        Write-Output 'PASS: Job Object owns descendants before resume and cleans success/failure/timeout/cancel/error.'
        Write-Output 'PASS: unknown/AccessDenied state fails closed; unrelated and stale/reused PID candidates survive.'
    } finally {
        Stop-Process -Id $unrelated.Id -Force -ErrorAction SilentlyContinue
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
        $resolvedContractRoot = [IO.Path]::GetFullPath($contractRoot)
        if ($resolvedContractRoot.StartsWith($tempRoot + '\', [StringComparison]::OrdinalIgnoreCase) -and
            (Split-Path -Leaf $resolvedContractRoot).StartsWith('quata-wasm-watchdog-contract-')) {
            Remove-Item -LiteralPath $resolvedContractRoot -Recurse -Force -ErrorAction SilentlyContinue
        }
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
$gradlew = (Join-Path $root 'gradlew.bat').Replace("'", "''")
$commandScript = @"
& '$gradlew' ':web:wasmJsBrowserDistribution' '--no-daemon' '--console=plain' '--stacktrace'
exit `$LASTEXITCODE
"@

$result = Invoke-OwnedWatchdogProcess `
    -CommandScript $commandScript `
    -WorkingDirectory $root `
    -StdoutPath $stdout `
    -StderrPath $stderr `
    -NoProgressSeconds ($NoProgressMinutes * 60) `
    -MaximumSeconds ($MaximumMinutes * 60)

$distribution = Join-Path $root 'web/build/dist/wasmJs/productionExecutable'
$summary = [pscustomobject]@{
    task = ':web:wasmJsBrowserDistribution'
    ownership = 'windows_job_object_kill_on_close'
    powershellEdition = $PSVersionTable.PSEdition
    powershellVersion = $PSVersionTable.PSVersion.ToString()
    started = $result.Started.ToString('o')
    elapsedSeconds = $result.ElapsedSeconds
    exitCode = $result.ExitCode
    outcome = $result.Outcome
    failure = $result.Failure
    cleanupState = $result.CleanupState
    noProgressMinutes = $NoProgressMinutes
    maximumMinutes = $MaximumMinutes
    distributionExists = Test-Path $distribution
    artifacts = if (Test-Path $distribution) {
        @(Get-ChildItem -Recurse -File $distribution | ForEach-Object {
            [pscustomobject]@{
                path = $_.FullName.Substring($root.Length).TrimStart('\', '/')
                bytes = $_.Length
            }
        } | Sort-Object path)
    } else { @() }
    samples = $result.Samples
}
$summary | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 (Join-Path $reportDirectory 'gradle-production-observation.json')

if ($result.CleanupState -ne 'job_terminated_and_empty') {
    throw "Diagnostic cleanup state is unknown or failed ($($result.CleanupState)); no production bundle was certified."
}
switch ($result.Outcome) {
    'success' {
        if (-not (Test-Path $distribution)) {
            throw 'Gradle exited successfully without a production distribution; no bundle was certified.'
        }
        exit 0
    }
    'process_failure' { exit $result.ExitCode }
    'cancelled' { throw "Diagnostic watchdog was cancelled; Job Object cleanup completed. No production bundle was certified." }
    'no_progress_timeout' { throw "Diagnostic watchdog stopped Gradle after inactivity; see $reportDirectory. No production bundle was certified." }
    'maximum_timeout' { throw "Diagnostic watchdog stopped Gradle at the maximum runtime; see $reportDirectory. No production bundle was certified." }
    'state_unknown' { throw "Diagnostic watchdog could not prove native process state ($($result.Failure)); no production bundle was certified." }
    default { throw "Diagnostic watchdog failed ($($result.Outcome): $($result.Failure)); no production bundle was certified." }
}
