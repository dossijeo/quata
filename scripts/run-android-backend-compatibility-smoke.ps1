[CmdletBinding()]
param(
    [string]$ApkPath,
    [string]$DeviceId = "",
    [string]$Output = "build-reports/backend-compatibility/android.json"
)

$ErrorActionPreference = "Stop"
if (Test-Path variable:PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $false
}
$sdkRoot = $env:ANDROID_SDK_ROOT
if ([string]::IsNullOrWhiteSpace($sdkRoot)) { $sdkRoot = $env:ANDROID_HOME }
if ([string]::IsNullOrWhiteSpace($sdkRoot)) { $sdkRoot = "C:\Users\PC\AppData\Local\Android\Sdk" }
$adb = Join-Path $sdkRoot "platform-tools\adb.exe"
if (-not (Test-Path -LiteralPath $adb -PathType Leaf)) { throw "adb was not found. Set ANDROID_SDK_ROOT." }

if ([string]::IsNullOrWhiteSpace($DeviceId)) {
    $devices = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" } | ForEach-Object { ($_ -split "\s+")[0] })
    if ($devices.Count -ne 1) { throw "Specify DeviceId unless exactly one device is connected." }
    $DeviceId = $devices[0]
}
if (-not [string]::IsNullOrWhiteSpace($ApkPath)) {
    $resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
    & $adb -s $DeviceId install -r $resolvedApk
    if ($LASTEXITCODE -ne 0) { throw "adb install -r failed; existing app data was not cleared." }
}

function Invoke-UiAutomatorDump([string]$RemotePath) {
    $stdout = [System.IO.Path]::GetTempFileName()
    $stderr = [System.IO.Path]::GetTempFileName()
    try {
        $process = Start-Process -FilePath $adb -ArgumentList @("-s", $DeviceId, "shell", "uiautomator", "dump", $RemotePath) -NoNewWindow -Wait -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
        return $process.ExitCode -eq 0
    } finally {
        Remove-Item -LiteralPath $stdout, $stderr -Force -ErrorAction SilentlyContinue
    }
}

function Get-Semantics([string]$Name) {
    $remote = "/sdcard/quata-backend-compatibility-$Name.xml"
    $xml = ""
    for ($attempt = 0; $attempt -lt 6 -and $xml -notmatch "<hierarchy"; $attempt++) {
        [void](Invoke-UiAutomatorDump $remote)
        $exists = (& $adb -s $DeviceId shell "if [ -f $remote ]; then echo yes; fi" 2>$null) -join ""
        if ($exists.Trim() -eq "yes") {
            $xml = (& $adb -s $DeviceId shell cat $remote 2>$null) -join ""
        }
        if ($xml -notmatch "<hierarchy") { Start-Sleep -Seconds 2 }
    }
    & $adb -s $DeviceId shell rm -f $remote | Out-Null
    if ($xml -notmatch "<hierarchy") { throw "UI hierarchy unavailable for $Name." }
    return @([regex]::Matches($xml, '(?:text|content-desc)="([^"]+)"') |
        ForEach-Object { $_.Groups[1].Value } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Select-Object -Unique)
}

function Dismiss-DialogIfPresent([string]$Name) {
    $remote = "/sdcard/quata-backend-compatibility-$Name-overlay.xml"
    [void](Invoke-UiAutomatorDump $remote)
    $exists = (& $adb -s $DeviceId shell "if [ -f $remote ]; then echo yes; fi" 2>$null) -join ""
    if ($exists.Trim() -ne "yes") { return }
    $xml = (& $adb -s $DeviceId shell cat $remote 2>$null) -join ""
    & $adb -s $DeviceId shell rm -f $remote | Out-Null
    $root = [regex]::Match($xml, '<hierarchy[^>]*>\s*<node[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $root.Success) { return }
    if ([int]$root.Groups[1].Value -gt 0 -or [int]$root.Groups[2].Value -gt 0) {
        & $adb -s $DeviceId shell input keyevent 4
        Start-Sleep -Seconds 1
    }
}

function Test-Route([string]$Name, [double]$XFraction, [string[]]$Expected) {
    if ($XFraction -ge 0) {
        $sizeLine = (& $adb -s $DeviceId shell wm size | Select-String -Pattern "Physical size:" | Select-Object -First 1).ToString()
        if ($sizeLine -notmatch "(\d+)x(\d+)") { throw "Unable to determine device dimensions." }
        $x = [int]([int]$Matches[1] * $XFraction)
        $y = [int]([int]$Matches[2] * 0.90)
        & $adb -s $DeviceId shell input tap $x $y
        Start-Sleep -Seconds 2
    }
    Dismiss-DialogIfPresent $Name
    $semantics = Get-Semantics $Name
    $joined = $semantics -join " | "
    $missing = @($Expected | Where-Object { $joined -notmatch [regex]::Escape($_) })
    return [ordered]@{
        route = $Name
        expected = $Expected
        missing = $missing
        passed = $missing.Count -eq 0
    }
}

$startedAt = Get-Date
$epochBefore = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
& $adb -s $DeviceId shell am force-stop com.quata
$launchOutput = @(& $adb -s $DeviceId shell timeout 30 am start -W -n com.quata/.MainActivity)
Start-Sleep -Seconds 3
$routes = @(
    (Test-Route "feed" -1 @("Feed", "Chats", "Official", "Account")),
    (Test-Route "chat" 0.30 @("Chats")),
    (Test-Route "official" 0.50 @("Official", "Share")),
    # Keep assertions ASCII-only so Windows PowerShell 5.1 and PowerShell 7 decode the script
    # identically on CI and developer workstations.
    (Test-Route "communities" 0.10 @("Open chat", "View users")),
    (Test-Route "profile" 0.90 @("Account"))
)
$appPid = (& $adb -s $DeviceId shell pidof com.quata).Trim()
$recentLog = @(& $adb -s $DeviceId logcat -v epoch -d -t 1200 | Where-Object {
    if ($_ -match "^\s*([0-9]+)\.") { [int64]$Matches[1] -ge $epochBefore } else { $false }
})
$crashOrAnr = @($recentLog | Select-String -Pattern "FATAL EXCEPTION.*com\.quata|Process: com\.quata|ANR in com\.quata|am_anr.*com\.quata")
$report = [ordered]@{
    check = "BACKEND-COMPATIBILITY-ANDROID"
    startedAt = $startedAt.ToUniversalTime().ToString("o")
    finishedAt = (Get-Date).ToUniversalTime().ToString("o")
    device = $DeviceId
    package = "com.quata"
    pidAlive = -not [string]::IsNullOrWhiteSpace($appPid)
    launch = ($launchOutput -join "`n")
    routes = $routes
    crashOrAnrCount = $crashOrAnr.Count
    status = if ($appPid -and $crashOrAnr.Count -eq 0 -and @($routes | Where-Object { -not $_.passed }).Count -eq 0) { "passed" } else { "failed" }
    mutationPolicy = "The runner issues no backend request and taps only normal read-route tabs. It does not intercept encrypted app traffic, so normal app startup/background synchronization is outside this UI smoke's guarantees. Pair this evidence with the read-only API/catalogue gates."
}
$outputPath = [System.IO.Path]::GetFullPath($Output)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outputPath) | Out-Null
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $outputPath -Encoding utf8
Write-Output "Android backend compatibility report written: $outputPath"
if ($report.status -ne "passed") { throw "Android backend compatibility smoke failed." }
