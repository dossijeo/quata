param(
    [string]$KeystorePath = "keystores/quata-release.jks",
    [string]$Alias = "quata-release",
    [string]$DName = "CN=Quata, OU=Quata, O=Quata, L=Malabo, ST=Bioko Norte, C=GQ",
    [int]$ValidityDays = 10000,
    [switch]$GeneratePasswords
)

$ErrorActionPreference = "Stop"

function ConvertTo-PlainText([securestring]$SecureValue) {
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

function New-Password {
    Add-Type -AssemblyName System.Web
    [System.Web.Security.Membership]::GeneratePassword(32, 8)
}

$keytool = $null
if ($env:JAVA_HOME) {
    $javaHomeKeytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    if (Test-Path $javaHomeKeytool) {
        $keytool = $javaHomeKeytool
    }
}

if (-not $keytool) {
    $keytoolCommand = Get-Command keytool -ErrorAction SilentlyContinue
    if ($keytoolCommand) {
        $keytool = $keytoolCommand.Source
    }
}

if (-not $keytool) {
    $androidStudioKeytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
    if (Test-Path $androidStudioKeytool) {
        $keytool = $androidStudioKeytool
    }
}

if (-not $keytool) {
    throw "No se encontro keytool. Instala un JDK o abre la terminal con JAVA_HOME configurado."
}

if (Test-Path $KeystorePath) {
    throw "Ya existe $KeystorePath. No se sobrescribe una clave de publicacion existente."
}

if ($GeneratePasswords) {
    $plainStorePassword = New-Password
    $plainKeyPassword = New-Password
} else {
    $storePassword = Read-Host "Password del keystore" -AsSecureString
    $keyPassword = Read-Host "Password de la clave '$Alias' (puede ser el mismo)" -AsSecureString
    $plainStorePassword = ConvertTo-PlainText $storePassword
    $plainKeyPassword = ConvertTo-PlainText $keyPassword
}

try {
    New-Item -ItemType Directory -Force -Path (Split-Path $KeystorePath) | Out-Null

    & $keytool `
        -genkeypair `
        -v `
        -keystore $KeystorePath `
        -storetype JKS `
        -storepass $plainStorePassword `
        -alias $Alias `
        -keypass $plainKeyPassword `
        -keyalg RSA `
        -keysize 4096 `
        -validity $ValidityDays `
        -dname $DName

    if ($LASTEXITCODE -ne 0) {
        throw "keytool termino con codigo $LASTEXITCODE."
    }

    $signingProperties = @"
storeFile=$KeystorePath
storePassword=$plainStorePassword
keyAlias=$Alias
keyPassword=$plainKeyPassword
"@

    [System.IO.File]::WriteAllText(
        (Join-Path (Get-Location) "release-signing.properties"),
        $signingProperties,
        [System.Text.UTF8Encoding]::new($false)
    )

    Write-Host ""
    Write-Host "Firma creada correctamente:"
    Write-Host "  Keystore: $KeystorePath"
    Write-Host "  Config:   release-signing.properties"
    Write-Host ""
    Write-Host "Guarda una copia segura del keystore y sus passwords. Sin esta clave no podras actualizar la app publicada."
} finally {
    $plainStorePassword = $null
    $plainKeyPassword = $null
}
