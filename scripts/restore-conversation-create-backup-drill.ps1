[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BackupSet,
    [Parameter(Mandatory = $true)][string]$EncryptionKeyFile,
    [string]$DockerImage = "postgres:17-alpine"
)

$ErrorActionPreference = "Stop"
$manifestPath = Join-Path $BackupSet "manifest.json"
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "restore_drill_manifest_missing" }
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
$artifact = @($manifest.artifacts | Where-Object kind -eq "full_custom")
if ($manifest.scope -ne "Full" -or $artifact.Count -ne 1) { throw "restore_drill_full_artifact_missing" }
$encrypted = Join-Path $BackupSet $artifact[0].name
if ((Get-FileHash -Algorithm SHA256 $encrypted).Hash.ToLowerInvariant() -cne [string]$artifact[0].ciphertextSha256) {
    throw "restore_drill_ciphertext_hash_mismatch"
}

$temporary = Join-Path ([IO.Path]::GetTempPath()) ("quata-private-open-restore-" + [guid]::NewGuid().ToString("N"))
$container = "quata-private-open-restore-" + [guid]::NewGuid().ToString("N").Substring(0, 12)
$password = [guid]::NewGuid().ToString("N")
$failure = $null
$cleanupFailure = $null
New-Item -ItemType Directory -Force -Path $temporary | Out-Null
try {
    $dump = Join-Path $temporary "database.dump"
    & node (Join-Path $PSScriptRoot "db-backup-crypto.mjs") decrypt $encrypted $dump $EncryptionKeyFile *> $null
    if ($LASTEXITCODE -ne 0) { throw "restore_drill_decrypt_failed" }
    if ((Get-FileHash -Algorithm SHA256 $dump).Hash.ToLowerInvariant() -cne [string]$artifact[0].plaintextSha256) {
        throw "restore_drill_plaintext_hash_mismatch"
    }
    $toc = @(& docker run --rm -v "${temporary}:/backup:ro" $DockerImage pg_restore --list /backup/database.dump 2>$null)
    if ($LASTEXITCODE -ne 0) { throw "restore_drill_toc_unreadable" }
    $functionEntries = @($toc | Where-Object {
        $_ -match "\bFUNCTION public quata_chat_get_or_create_private_thread\(uuid, uuid\)"
    })
    if ($functionEntries.Count -ne 1) { throw "restore_drill_function_toc_identity_mismatch" }
    $restoreList = Join-Path $temporary "private-open.restore.list"
    [IO.File]::WriteAllLines($restoreList, [string[]](@($toc | Where-Object { $_ -match '^;' }) + $functionEntries), [Text.UTF8Encoding]::new($false))
    & docker run -d --rm --name $container -e "POSTGRES_PASSWORD=$password" -v "${temporary}:/backup:ro" $DockerImage | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "restore_drill_container_start_failed" }
    $ready = $false
    foreach ($attempt in 1..30) {
        & docker exec $container pg_isready -U postgres *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "restore_drill_container_not_ready" }
    & docker exec -e "PGPASSWORD=$password" $container pg_restore -U postgres -d postgres --no-owner --no-acl --use-list=/backup/private-open.restore.list /backup/database.dump *> $null
    if ($LASTEXITCODE -ne 0) { throw "restore_drill_function_restore_failed" }
    $definition = & docker exec -e "PGPASSWORD=$password" $container psql -U postgres -d postgres -Atqc "select pg_get_functiondef('public.quata_chat_get_or_create_private_thread(uuid,uuid)'::regprocedure)" 2>$null
    if ($LASTEXITCODE -ne 0 -or ($definition -join "`n") -notmatch "pg_advisory_xact_lock" -or ($definition -join "`n") -notmatch "private_thread_opened") {
        throw "restore_drill_function_verification_failed"
    }
}
catch {
    $failure = $_
}
finally {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & docker rm -f $container *> $null
        $remaining = & docker ps -a --filter "name=^/$container$" --format "{{.Names}}" 2>&1
        if ($LASTEXITCODE -ne 0 -or ($remaining -join "").Trim().Length -ne 0) {
            $cleanupFailure = "restore_drill_container_cleanup_unverified"
        }
        Remove-Item -LiteralPath $temporary -Recurse -Force -ErrorAction SilentlyContinue
        if (Test-Path -LiteralPath $temporary) { $cleanupFailure = "restore_drill_plaintext_cleanup_unverified" }
    }
    finally {
        $ErrorActionPreference = $previous
    }
}

if ($cleanupFailure) { throw $cleanupFailure }
if ($failure) { throw $failure }
$manifestSha256 = (Get-FileHash -Algorithm SHA256 $manifestPath).Hash.ToLowerInvariant()
Write-Output "conversation_create_backup_restore_drill_passed manifest_sha256=$manifestSha256"
