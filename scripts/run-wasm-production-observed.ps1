[CmdletBinding()]
param(
    [ValidateRange(1, 120)]
    [int]$NoProgressMinutes = 10,
    [ValidateRange(1, 180)]
    [int]$MaximumMinutes = 20,
    [string]$ReportDirectory = 'build/reports/wasm-bundle',
    # Runs deterministic, no-Gradle ownership and cleanup contracts.
    [switch]$ContractTest,
    # Internal fixture entrypoint used by the runspace cancellation contract.
    [switch]$ContractCancellationFixture,
    [string]$ContractEvidencePath,
    [string]$ContractChildPidPath
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
    public sealed class NativeTestSnapshot
    {
        public int JobHandlesCreated { get; internal set; }
        public int JobHandlesClosed { get; internal set; }
        public int ProcessHandlesCreated { get; internal set; }
        public int ProcessHandlesClosed { get; internal set; }
        public int ThreadHandlesCreated { get; internal set; }
        public int ThreadHandlesClosed { get; internal set; }
        public int TerminateProcessCalls { get; internal set; }
        public int TerminatedProcessWaits { get; internal set; }
        public int TerminateJobCalls { get; internal set; }
        public int EmptyJobWaits { get; internal set; }
        public int LastCreatedProcessId { get; internal set; }
    }

    public static class NativeTestHooks
    {
        public static bool ForceAssignProcessAccessDenied;
        public static bool ForceQueryJobAccessDenied;
        private static NativeTestSnapshot counters = new NativeTestSnapshot();

        public static void Reset()
        {
            ForceAssignProcessAccessDenied = false;
            ForceQueryJobAccessDenied = false;
            counters = new NativeTestSnapshot();
        }

        public static NativeTestSnapshot Snapshot()
        {
            return new NativeTestSnapshot
            {
                JobHandlesCreated = counters.JobHandlesCreated,
                JobHandlesClosed = counters.JobHandlesClosed,
                ProcessHandlesCreated = counters.ProcessHandlesCreated,
                ProcessHandlesClosed = counters.ProcessHandlesClosed,
                ThreadHandlesCreated = counters.ThreadHandlesCreated,
                ThreadHandlesClosed = counters.ThreadHandlesClosed,
                TerminateProcessCalls = counters.TerminateProcessCalls,
                TerminatedProcessWaits = counters.TerminatedProcessWaits,
                TerminateJobCalls = counters.TerminateJobCalls,
                EmptyJobWaits = counters.EmptyJobWaits,
                LastCreatedProcessId = counters.LastCreatedProcessId,
            };
        }

        internal static void JobCreated() { counters.JobHandlesCreated++; }
        internal static void JobClosed() { counters.JobHandlesClosed++; }
        internal static void ProcessCreated(int processId)
        {
            counters.ProcessHandlesCreated++;
            counters.LastCreatedProcessId = processId;
        }
        internal static void ProcessClosed() { counters.ProcessHandlesClosed++; }
        internal static void ThreadCreated() { counters.ThreadHandlesCreated++; }
        internal static void ThreadClosed() { counters.ThreadHandlesClosed++; }
        internal static void ProcessTerminationRequested() { counters.TerminateProcessCalls++; }
        internal static void ProcessTerminationConfirmed() { counters.TerminatedProcessWaits++; }
        internal static void JobTerminationRequested() { counters.TerminateJobCalls++; }
        internal static void JobEmptyConfirmed() { counters.EmptyJobWaits++; }
    }

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
                NativeTestHooks.JobCreated();

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
                NativeTestHooks.ProcessCreated(unchecked((int)processInformation.dwProcessId));
                NativeTestHooks.ThreadCreated();

                if (NativeTestHooks.ForceAssignProcessAccessDenied)
                {
                    throw new Win32Exception(5, "AssignProcessToJobObject failed (injected AccessDenied)");
                }
                if (!AssignProcessToJobObject(job, processInformation.hProcess))
                {
                    ThrowLastWin32("AssignProcessToJobObject");
                }
                if (ResumeThread(processInformation.hThread) == UInt32.MaxValue)
                {
                    ThrowLastWin32("ResumeThread");
                }

                CloseTrackedHandle(processInformation.hThread, "thread");
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
                    NativeTestHooks.ProcessTerminationRequested();
                    if (!TerminateProcess(processInformation.hProcess, 1))
                    {
                        ThrowLastWin32("TerminateProcess");
                    }
                    uint wait = WaitForSingleObject(processInformation.hProcess, 5000);
                    if (wait != WAIT_OBJECT_0)
                    {
                        if (wait == WAIT_FAILED) ThrowLastWin32("WaitForSingleObject(process)");
                        throw new TimeoutException("Suspended process did not terminate during failed startup cleanup.");
                    }
                    NativeTestHooks.ProcessTerminationConfirmed();
                }
                throw;
            }
            finally
            {
                if (processInformation.hThread != IntPtr.Zero)
                {
                    CloseTrackedHandle(processInformation.hThread, "thread");
                }
                if (processInformation.hProcess != IntPtr.Zero)
                {
                    CloseTrackedHandle(processInformation.hProcess, "process");
                }
                if (job != IntPtr.Zero)
                {
                    CloseTrackedHandle(job, "job");
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
            if (NativeTestHooks.ForceQueryJobAccessDenied)
            {
                throw new Win32Exception(5, "QueryInformationJobObject failed (injected AccessDenied)");
            }
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
            NativeTestHooks.JobTerminationRequested();
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
            NativeTestHooks.JobEmptyConfirmed();
        }

        public void Dispose()
        {
            if (disposed) return;
            disposed = true;
            if (jobHandle != IntPtr.Zero)
            {
                // KILL_ON_JOB_CLOSE is the final backstop even if explicit
                // termination or PowerShell cleanup was interrupted.
                CloseTrackedHandle(jobHandle, "job");
                jobHandle = IntPtr.Zero;
            }
            if (processHandle != IntPtr.Zero)
            {
                CloseTrackedHandle(processHandle, "process");
                processHandle = IntPtr.Zero;
            }
            GC.SuppressFinalize(this);
        }

        ~JobProcessLease()
        {
            try { Dispose(); }
            catch { /* finalizers must not terminate the PowerShell host */ }
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

        private static void CloseTrackedHandle(IntPtr handle, string kind)
        {
            if (!CloseHandle(handle)) ThrowLastWin32("CloseHandle(" + kind + ")");
            if (kind == "job") NativeTestHooks.JobClosed();
            else if (kind == "process") NativeTestHooks.ProcessClosed();
            else if (kind == "thread") NativeTestHooks.ThreadClosed();
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

function Get-NestedWin32Exception {
    param([Exception]$Exception)
    $current = $Exception
    while ($current) {
        if ($current -is [System.ComponentModel.Win32Exception]) { return $current }
        $current = $current.InnerException
    }
    return $null
}

function Start-JobOwnedPowerShell {
    param(
        [Parameter(Mandatory = $true)][string]$Script,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory
    )
    $powershellPath = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
    $encodedCommand = ConvertTo-PowerShellEncodedCommand -Script $Script
    $nativeCommandLine = "`"$powershellPath`" -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -EncodedCommand $encodedCommand"
    return [Quata.Watchdog.JobProcessLease]::Start(
        $powershellPath,
        $nativeCommandLine,
        $WorkingDirectory)
}

function Get-JobProcessSnapshot {
    param([Parameter(Mandatory = $true)]$Lease)
    $snapshot = [System.Collections.Generic.List[object]]::new()
    try {
        $processIds = @($Lease.GetProcessIds())
    } catch {
        $nativeFailure = Get-NestedWin32Exception -Exception $_.Exception
        if ($nativeFailure) { throw $nativeFailure }
        throw
    }
    foreach ($id in $processIds) {
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

function Stop-OwnedJobProcessTree {
    param(
        [Parameter(Mandatory = $true)]$Lease,
        [object[]]$DiagnosticProcessIdentityHints = @()
    )
    # PID/creation-time observations are diagnostic input only. Passing them
    # through this production cleanup seam proves that they never authorize a
    # Stop-Process call; kernel Job Object membership is the only kill scope.
    $ignoredHintCount = @($DiagnosticProcessIdentityHints).Count
    $Lease.StopAndWait(5000)
    return $ignoredHintCount
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
        [scriptblock]$CancellationProbe,
        [object[]]$DiagnosticProcessIdentityHints = @(),
        [string]$FinallyEvidencePath
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
    $pipelineStoppedCaught = $false
    $ignoredProcessIdentityHintCount = 0

    try {
        $lease = Start-JobOwnedPowerShell -Script $wrappedScript -WorkingDirectory $WorkingDirectory
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
            } catch {
                $nativeFailure = Get-NestedWin32Exception -Exception $_.Exception
                if ($nativeFailure) {
                    $outcome = 'state_unknown'
                    $failure = "native_state_query_failed:$($nativeFailure.NativeErrorCode)"
                    break
                }
                throw
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
        $pipelineStoppedCaught = $true
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
        $nativeFailure = Get-NestedWin32Exception -Exception $_.Exception
        if ($nativeFailure) {
            $outcome = if ($nativeFailure.NativeErrorCode -eq 5) { 'state_unknown' } else { 'error' }
            $failure = "native_operation_failed:$($nativeFailure.NativeErrorCode)"
        } else {
            $outcome = 'error'
            $failure = $_.Exception.Message
        }
    } finally {
        if ($lease) {
            try {
                $ignoredProcessIdentityHintCount = Stop-OwnedJobProcessTree `
                    -Lease $lease `
                    -DiagnosticProcessIdentityHints $DiagnosticProcessIdentityHints
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
        if ($FinallyEvidencePath) {
            $evidenceJson = '{"pipelineStoppedCaught":' +
                $pipelineStoppedCaught.ToString().ToLowerInvariant() +
                ',"cleanupState":"' + $cleanupState +
                '","ignoredProcessIdentityHintCount":' + $ignoredProcessIdentityHintCount + '}'
            [IO.File]::WriteAllText($FinallyEvidencePath, $evidenceJson, [Text.Encoding]::UTF8)
        }
    }

    return [pscustomobject]@{
        Outcome = $outcome
        ExitCode = $exitCode
        Failure = $failure
        CleanupState = $cleanupState
        RootProcessId = if ($lease) { $lease.ProcessId } else { $null }
        PipelineStoppedCaught = $pipelineStoppedCaught
        IgnoredProcessIdentityHintCount = $ignoredProcessIdentityHintCount
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

function Assert-NativeCounter {
    param(
        [Parameter(Mandatory = $true)]$Snapshot,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$Expected
    )
    $actual = [int]$Snapshot.$Name
    if ($actual -ne $Expected) {
        throw "Native counter $Name was $actual, expected $Expected."
    }
}

function Test-AssignProcessAccessDeniedCleanup {
    param([Parameter(Mandatory = $true)][string]$WorkingDirectory)
    [Quata.Watchdog.NativeTestHooks]::Reset()
    [Quata.Watchdog.NativeTestHooks]::ForceAssignProcessAccessDenied = $true
    $caught = $null
    try {
        $unexpectedLease = Start-JobOwnedPowerShell `
            -Script 'Start-Sleep -Seconds 60' `
            -WorkingDirectory $WorkingDirectory
        try {
            throw 'Injected AssignProcessToJobObject failure unexpectedly returned a lease.'
        } finally {
            if ($unexpectedLease) { $unexpectedLease.Dispose() }
        }
    } catch {
        $caught = Get-NestedWin32Exception -Exception $_.Exception
    } finally {
        [Quata.Watchdog.NativeTestHooks]::ForceAssignProcessAccessDenied = $false
    }
    if (-not $caught -or $caught.NativeErrorCode -ne 5) {
        throw "AssignProcessToJobObject seam did not surface Win32 error 5: $caught"
    }
    $snapshot = [Quata.Watchdog.NativeTestHooks]::Snapshot()
    foreach ($name in @(
        'JobHandlesCreated', 'JobHandlesClosed',
        'ProcessHandlesCreated', 'ProcessHandlesClosed',
        'ThreadHandlesCreated', 'ThreadHandlesClosed',
        'TerminateProcessCalls', 'TerminatedProcessWaits'
    )) {
        Assert-NativeCounter -Snapshot $snapshot -Name $name -Expected 1
    }
    Assert-NativeCounter -Snapshot $snapshot -Name 'TerminateJobCalls' -Expected 0
    Assert-NativeCounter -Snapshot $snapshot -Name 'EmptyJobWaits' -Expected 0
    Assert-ProcessExited `
        -ProcessId $snapshot.LastCreatedProcessId `
        -Message "Suspended process $($snapshot.LastCreatedProcessId) survived failed assignment."
}

function Test-QueryAccessDeniedCleanup {
    param([Parameter(Mandatory = $true)][string]$WorkingDirectory)
    [Quata.Watchdog.NativeTestHooks]::Reset()
    [Quata.Watchdog.NativeTestHooks]::ForceQueryJobAccessDenied = $true
    try {
        $result = Invoke-OwnedWatchdogProcess `
            -CommandScript 'Start-Sleep -Seconds 60' `
            -WorkingDirectory $WorkingDirectory `
            -StdoutPath (Join-Path $WorkingDirectory 'query-access-denied.stdout.log') `
            -StderrPath (Join-Path $WorkingDirectory 'query-access-denied.stderr.log') `
            -NoProgressSeconds 20 `
            -MaximumSeconds 20 `
            -SampleIntervalMilliseconds 50
    } finally {
        [Quata.Watchdog.NativeTestHooks]::ForceQueryJobAccessDenied = $false
    }
    if ($result.Outcome -ne 'state_unknown' -or
        $result.Failure -ne 'job_membership_query_failed:5' -or
        $result.CleanupState -ne 'job_terminated_and_empty') {
        throw "QueryInformationJobObject seam was not fail-closed: $($result | ConvertTo-Json -Compress)"
    }
    $snapshot = [Quata.Watchdog.NativeTestHooks]::Snapshot()
    foreach ($name in @(
        'JobHandlesCreated', 'JobHandlesClosed',
        'ProcessHandlesCreated', 'ProcessHandlesClosed',
        'ThreadHandlesCreated', 'ThreadHandlesClosed',
        'TerminateJobCalls', 'EmptyJobWaits'
    )) {
        Assert-NativeCounter -Snapshot $snapshot -Name $name -Expected 1
    }
    Assert-NativeCounter -Snapshot $snapshot -Name 'TerminateProcessCalls' -Expected 0
    Assert-NativeCounter -Snapshot $snapshot -Name 'TerminatedProcessWaits' -Expected 0
    Assert-ProcessExited `
        -ProcessId $snapshot.LastCreatedProcessId `
        -Message "Job root $($snapshot.LastCreatedProcessId) survived QueryInformationJobObject AccessDenied."
}

function Test-RunspacePipelineStopCleanup {
    param(
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string]$ScriptPath
    )
    $evidencePath = Join-Path $WorkingDirectory 'runspace-stop-finally.json'
    $childPidPath = Join-Path $WorkingDirectory 'runspace-stop-child.pid'
    $powerShell = [PowerShell]::Create()
    try {
        $null = $powerShell.AddCommand($ScriptPath).
            AddParameter('ContractCancellationFixture').
            AddParameter('ContractEvidencePath', $evidencePath).
            AddParameter('ContractChildPidPath', $childPidPath)
        $async = $powerShell.BeginInvoke()
        $childPid = Wait-ProcessIdFile -Path $childPidPath -TimeoutSeconds 10
        $powerShell.Stop()
        $stoppedState = $powerShell.InvocationStateInfo
        try {
            $null = $powerShell.EndInvoke($async)
        } catch [System.Management.Automation.PipelineStoppedException] {
            # Expected for a real PowerShell.Stop() request.
        }
        if ($stoppedState.State -ne [System.Management.Automation.PSInvocationState]::Stopped -or
            $stoppedState.Reason -isnot [System.Management.Automation.PipelineStoppedException]) {
            throw "Runspace did not expose PipelineStopped after PowerShell.Stop(): $($stoppedState | Out-String)"
        }
        $deadline = (Get-Date).AddSeconds(5)
        while (-not (Test-Path -LiteralPath $evidencePath) -and (Get-Date) -lt $deadline) {
            Start-Sleep -Milliseconds 50
        }
        if (-not (Test-Path -LiteralPath $evidencePath)) {
            throw 'PowerShell.Stop() did not reach the production finally evidence seam.'
        }
        $evidence = Get-Content -LiteralPath $evidencePath -Raw | ConvertFrom-Json
        if ($evidence.cleanupState -ne 'job_terminated_and_empty') {
            throw "PowerShell.Stop() evidence did not prove production finally cleanup: $($evidence | ConvertTo-Json -Compress)"
        }
        Assert-ProcessExited `
            -ProcessId $childPid `
            -Message "PowerShell.Stop() leaked Job Object child PID $childPid."
    } finally {
        $powerShell.Dispose()
    }
}

function Invoke-WasmProductionWatchdogContractTest {
    Initialize-WindowsJobObjectInterop
    $contractRoot = Join-Path ([IO.Path]::GetTempPath()) ("quata-wasm-watchdog-contract-" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $contractRoot | Out-Null
    $unrelated = Start-Process -FilePath "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" `
        -ArgumentList '-NoProfile', '-Command', 'Start-Sleep -Seconds 60' -PassThru
    try {
        Test-AssignProcessAccessDeniedCleanup -WorkingDirectory $contractRoot
        Test-QueryAccessDeniedCleanup -WorkingDirectory $contractRoot

        $stalePidIdentity = [pscustomobject]@{
            Id = $unrelated.Id
            StartTime = $unrelated.StartTime.AddTicks(-1)
        }
        $scenarios = @(
            [pscustomobject]@{ Name = 'success'; Expected = 'success'; Exit = 0; Timeout = 10; Cancel = $null; State = $null },
            [pscustomobject]@{ Name = 'failure'; Expected = 'process_failure'; Exit = 7; Timeout = 10; Cancel = $null; State = $null },
            [pscustomobject]@{ Name = 'timeout'; Expected = 'maximum_timeout'; Exit = $null; Timeout = 3; Cancel = $null; State = $null },
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
                Cancel = {
                    param($iteration, $lease)
                    if ($iteration -ge 15) {
                        throw [ApplicationException]::new('injected_watchdog_error')
                    }
                    return $false
                }
                State = $null
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
                -CancellationProbe $scenario.Cancel `
                -DiagnosticProcessIdentityHints $(if ($scenario.Name -eq 'success') { @($stalePidIdentity) } else { @() })

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
            $expectedHintCount = if ($scenario.Name -eq 'success') { 1 } else { 0 }
            if ($result.IgnoredProcessIdentityHintCount -ne $expectedHintCount) {
                throw "Scenario $($scenario.Name) did not pass its stale PID identity through production cleanup."
            }
        }

        Test-RunspacePipelineStopCleanup `
            -WorkingDirectory $contractRoot `
            -ScriptPath $PSCommandPath

        if (-not (Get-Process -Id $unrelated.Id -ErrorAction SilentlyContinue)) {
            throw 'Kernel-owned cleanup accepted an unrelated/reused PID candidate.'
        }
        Write-Output 'PASS: injected AssignProcessToJobObject/QueryInformationJobObject error 5 paths close each native handle once.'
        Write-Output 'PASS: PowerShell.Stop() produces PipelineStopped and reaches production finally cleanup.'
        Write-Output 'PASS: Job Object owns descendants before resume and cleans success/failure/timeout/cancel/error.'
        Write-Output 'PASS: stale PID identity reaches cleanup; unrelated/reused PID candidate survives.'
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

if ($ContractCancellationFixture) {
    if ([string]::IsNullOrWhiteSpace($ContractEvidencePath) -or
        [string]::IsNullOrWhiteSpace($ContractChildPidPath)) {
        throw 'Contract cancellation fixture requires evidence and child PID paths.'
    }
    $fixtureDirectory = Split-Path -Parent $ContractEvidencePath
    $quotedChildPidPath = $ContractChildPidPath.Replace("'", "''")
    $fixtureCommand = @"
`$child = Start-Process -FilePath '$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe' -ArgumentList '-NoProfile', '-Command', 'Start-Sleep -Seconds 60' -PassThru
Set-Content -LiteralPath '$quotedChildPidPath' -Value `$child.Id
Start-Sleep -Seconds 60
"@
    $null = Invoke-OwnedWatchdogProcess `
        -CommandScript $fixtureCommand `
        -WorkingDirectory $fixtureDirectory `
        -StdoutPath (Join-Path $fixtureDirectory 'runspace-stop.stdout.log') `
        -StderrPath (Join-Path $fixtureDirectory 'runspace-stop.stderr.log') `
        -NoProgressSeconds 120 `
        -MaximumSeconds 120 `
        -SampleIntervalMilliseconds 100 `
        -FinallyEvidencePath $ContractEvidencePath
    return
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
