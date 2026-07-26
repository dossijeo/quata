[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$DbUrlFile,
    [Parameter(Mandatory = $true)][string]$TlsCaFile,
    [Parameter(Mandatory = $true)][string]$EncryptionKeyFile,
    [string]$OutRoot = "backups/release-logical",
    [ValidateSet("Full", "Critical")][string]$Scope = "Full",
    [string]$DockerImage = "postgres:17-alpine",
    [string]$DockerNetwork = ""
)

# This command is intentionally non-interactive. It never accepts a connection
# string as a parameter and never writes it (or driver output) to the console.
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail([string]$Code) { throw $Code }
function Restrict-Directory([string]$Path) {
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
    if ($env:OS -eq "Windows_NT") {
        $identity = (& whoami).Trim()
        & icacls $Path /inheritance:r /grant:r "${identity}:(OI)(CI)F" *> $null
        if ($LASTEXITCODE -ne 0) { Fail "backup_directory_acl_failed" }
    }
}
function Read-Key([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { Fail "encryption_key_file_missing" }
    try { $key = [Convert]::FromBase64String((Get-Content -LiteralPath $Path -Raw).Trim()) } catch { Fail "encryption_key_invalid" }
    if ($key.Length -ne 32) { Fail "encryption_key_invalid" }
    return ,$key
}
function Parse-Connection([string]$File) {
    if (-not (Test-Path -LiteralPath $File -PathType Leaf)) { Fail "database_url_file_missing" }
    $raw = (Get-Content -LiteralPath $File -Raw).Trim()
    try { $uri = [Uri]$raw } catch { Fail "database_url_invalid" }
    if ($uri.Scheme -notin @("postgres", "postgresql") -or [string]::IsNullOrWhiteSpace($uri.Host) -or [string]::IsNullOrWhiteSpace($uri.UserInfo)) { Fail "database_url_invalid" }
    $query = @{}
    foreach ($pair in $uri.Query.TrimStart('?').Split('&', [StringSplitOptions]::RemoveEmptyEntries)) {
        $segments=$pair.Split('=',2); $name=[Uri]::UnescapeDataString($segments[0]).ToLowerInvariant(); $value=if ($segments.Count -gt 1) { [Uri]::UnescapeDataString($segments[1]) } else { "" }; $query[$name]=$value
    }
    if ($query["sslmode"] -cne "verify-full") { Fail "database_url_requires_verify_full" }
    foreach ($unsafe in @("ssl", "sslrootcert", "sslcert", "sslkey", "sslpassword")) { if ($query.ContainsKey($unsafe)) { Fail "database_url_unsafe_ssl_parameter" } }
    $parts = $uri.UserInfo -split ":", 2
    if ($parts.Count -ne 2 -or [string]::IsNullOrWhiteSpace($parts[0])) { Fail "database_url_invalid" }
    $database = $uri.AbsolutePath.Trim("/")
    if ([string]::IsNullOrWhiteSpace($database)) { Fail "database_url_invalid" }
    return [ordered]@{ Host=$uri.Host; Port=if ($uri.IsDefaultPort) { "5432" } else { "$($uri.Port)" }; User=[Uri]::UnescapeDataString($parts[0]); Password=[Uri]::UnescapeDataString($parts[1]); Database=[Uri]::UnescapeDataString($database) }
}
function Invoke-DumpAndEncrypt([string[]]$DumpArguments, [string]$EncryptedOutput, [System.Collections.IDictionary]$Connection, [string]$CaPath, [string]$EnvPath) {
    $envLines = @("PGHOST=$($Connection.Host)", "PGPORT=$($Connection.Port)", "PGUSER=$($Connection.User)", "PGPASSWORD=$($Connection.Password)", "PGDATABASE=$($Connection.Database)", "PGSSLMODE=verify-full", "PGSSLROOTCERT=/tls/ca.pem", "PGCONNECT_TIMEOUT=20")
    [IO.File]::WriteAllLines($EnvPath, $envLines, [Text.UTF8Encoding]::new($false))
    $outputDirectory=Split-Path $EncryptedOutput -Parent
    $args = @("run", "--rm", "--env-file", $EnvPath, "-v", "${outputDirectory}:/work", "-v", "${CaPath}:/tls/ca.pem:ro", "-v", "${PSScriptRoot}:/script:ro", "-v", "${EncryptionKeyFile}:/key/release.key:ro")
    if (-not [string]::IsNullOrWhiteSpace($DockerNetwork)) { $args += @("--network", $DockerNetwork) }
    $outputName=Split-Path $EncryptedOutput -Leaf
    $command="apk add --no-cache nodejs >/dev/null 2>&1; pg_dump $($DumpArguments -join ' ') | node /script/db-backup-crypto.mjs encrypt-stdin ignored /work/$outputName /key/release.key"
    $args += @($DockerImage, "sh", "-ec", $command)
    $checksum = & docker @args 2>$null
    if ($LASTEXITCODE -ne 0 -or $checksum -notmatch '^[a-f0-9]{64}$') { Fail "logical_backup_dump_or_encryption_failed" }
    return $checksum
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Fail "docker_required" }
if (-not (Test-Path -LiteralPath $TlsCaFile -PathType Leaf)) { Fail "tls_ca_file_missing" }
if ((Get-Content -LiteralPath $TlsCaFile -Raw) -notmatch "-----BEGIN CERTIFICATE-----") { Fail "tls_ca_invalid" }

$connection = Parse-Connection $DbUrlFile
$key = Read-Key $EncryptionKeyFile
$root = [IO.Path]::GetFullPath($OutRoot)
Restrict-Directory $root
$id = "release-" + (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ") + "-" + [guid]::NewGuid().ToString("N").Substring(0, 8)
$set = Join-Path $root $id
$work = Join-Path $root (".working-" + [guid]::NewGuid().ToString("N"))
$envFile = Join-Path $work "pg.env"
Restrict-Directory $set; Restrict-Directory $work

try {
    $artifacts = @()
    if ($Scope -eq "Full") {
        $encrypted = Join-Path $set "database.dump.enc"
        $checksum=Invoke-DumpAndEncrypt @("--format=custom", "--compress=9", "--no-owner") $encrypted $connection $TlsCaFile $envFile
        $artifacts += [ordered]@{ name="database.dump.enc"; kind="full_custom"; plaintextSha256=$checksum; ciphertextSha256=(Get-FileHash $encrypted -Algorithm SHA256).Hash.ToLowerInvariant() }
    } else {
        $encrypted = Join-Path $set "schema.dump.enc"; $checksum=Invoke-DumpAndEncrypt @("--format=custom", "--schema-only", "--no-owner") $encrypted $connection $TlsCaFile $envFile
        $artifacts += [ordered]@{ name="schema.dump.enc"; kind="schema_custom"; plaintextSha256=$checksum; ciphertextSha256=(Get-FileHash $encrypted -Algorithm SHA256).Hash.ToLowerInvariant() }
        $encrypted = Join-Path $set "critical-data.dump.enc"; $checksum=Invoke-DumpAndEncrypt @("--format=custom", "--data-only", "--no-owner", "--table=public.community_comments", "--table=public.official_post_likes") $encrypted $connection $TlsCaFile $envFile
        $artifacts += [ordered]@{ name="critical-data.dump.enc"; kind="critical_data_custom"; tables=@("public.community_comments", "public.official_post_likes"); plaintextSha256=$checksum; ciphertextSha256=(Get-FileHash $encrypted -Algorithm SHA256).Hash.ToLowerInvariant() }
    }
    $manifest = [ordered]@{ format="quata-logical-backup-v1"; createdAt=(Get-Date).ToUniversalTime().ToString("o"); scope=$Scope; encryption="AES-256-GCM"; tls="verify-full_explicit_ca"; grantsIncluded=$true; artifacts=$artifacts; containsConnectionData=$false; notes=@("Connection URL and credentials are never stored in this manifest.", "The backup pipeline writes no plaintext dump; retain the key separately.") }
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $set "manifest.json") -Encoding utf8
    Write-Output "logical_backup_created=$set"
}
finally {
    if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force }
    if ($null -ne $key) { [Array]::Clear($key, 0, $key.Length) }
}
