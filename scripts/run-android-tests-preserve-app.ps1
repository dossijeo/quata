param(
    [string]$DeviceId = "",
    [string]$TestPackage = "",
    [string]$TestClass = ""
)

$ErrorActionPreference = "Stop"

# Do not replace this script with connectedDebugAndroidTest on a persistent
# emulator: that Gradle task may uninstall com.quata and erase its app data.
$projectRoot = Split-Path -Parent $PSScriptRoot
$localProperties = Join-Path $projectRoot "local.properties"
$sdkRoot = $env:ANDROID_SDK_ROOT
if ([string]::IsNullOrWhiteSpace($sdkRoot) -and (Test-Path -LiteralPath $localProperties)) {
    $sdkLine = Get-Content -LiteralPath $localProperties |
        Where-Object { $_ -match '^sdk\.dir=' } |
        Select-Object -First 1
    if ($sdkLine) {
        $sdkRoot = ($sdkLine -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\'
    }
}
if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    throw "ANDROID_SDK_ROOT no está definido y local.properties no contiene sdk.dir."
}

$adb = Join-Path $sdkRoot "platform-tools\adb.exe"
if (-not (Test-Path -LiteralPath $adb)) {
    throw "No se encontró adb en $adb"
}

if ([string]::IsNullOrWhiteSpace($DeviceId)) {
    $devices = @(& $adb devices |
        Select-Object -Skip 1 |
        Where-Object { $_ -match '\sdevice$' } |
        ForEach-Object { ($_ -split '\s+')[0] })
    if ($devices.Count -ne 1) {
        throw "Indica -DeviceId cuando no haya exactamente un dispositivo conectado."
    }
    $DeviceId = $devices[0]
}

Push-Location $projectRoot
try {
    & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw "Falló la compilación de las pruebas Android." }

    $appApk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
    $testApk = Join-Path $projectRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"

    # -r conserva la instalación y los datos (incluida la sesión iniciada).
    & $adb -s $DeviceId install -r $appApk
    if ($LASTEXITCODE -ne 0) { throw "No se pudo instalar la app conservando sus datos." }
    & $adb -s $DeviceId install -r -t $testApk
    if ($LASTEXITCODE -ne 0) { throw "No se pudo instalar el APK auxiliar de pruebas." }

    $arguments = @("-s", $DeviceId, "shell", "am", "instrument", "-w", "-r")
    if (-not [string]::IsNullOrWhiteSpace($TestPackage)) {
        $arguments += @("-e", "package", $TestPackage)
    }
    if (-not [string]::IsNullOrWhiteSpace($TestClass)) {
        $arguments += @("-e", "class", $TestClass)
    }
    $arguments += "com.quata.test/androidx.test.runner.AndroidJUnitRunner"

    & $adb @arguments
    if ($LASTEXITCODE -ne 0) { throw "Fallaron las pruebas instrumentadas." }
} finally {
    # Limpiamos solo el paquete auxiliar; com.quata y sus datos permanecen intactos.
    & $adb -s $DeviceId uninstall com.quata.test | Out-Null
    Pop-Location
}
