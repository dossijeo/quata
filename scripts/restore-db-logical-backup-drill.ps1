[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BackupSet,
    [Parameter(Mandatory = $true)][string]$EncryptionKeyFile,
    [string]$DockerImage = "postgres:17-alpine",
    [switch]$CleanTarget,
    [switch]$AffectedTablesOnly,
    [switch]$ValidateSecurityReleaseScope,
    [switch]$ShowRelevantToc,
    [int]$ExpectedCommunityComments = -1,
    [int]$ExpectedOfficialPostLikes = -1
)

# A restoration target is always a fresh disposable PostgreSQL 17 container.
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
function Fail([string]$Code) { throw $Code }
function Restrict-Directory([string]$Path) {
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
    if ($env:OS -eq "Windows_NT") {
        $identity=(& whoami).Trim()
        & icacls $Path /inheritance:r /grant:r "${identity}:(OI)(CI)F" *> $null
        if ($LASTEXITCODE -ne 0) { Fail "restore_directory_acl_failed" }
        $unsafe=@((Get-Acl -LiteralPath $Path).Access | Where-Object { $_.AccessControlType -eq "Allow" -and $_.IdentityReference.Value -match "(^|\\)(Everyone|Users|Authenticated Users)$" })
        if ($unsafe.Count -ne 0) { Fail "restore_directory_acl_not_restricted" }
    }
}
function Assert-Manifest($Manifest, [string]$SetPath) {
    $allowed=@("format","createdAt","scope","encryption","tls","grantsIncluded","artifacts","containsConnectionData","notes")
    foreach ($property in $Manifest.PSObject.Properties.Name) { if ($property -notin $allowed) { Fail "backup_manifest_unrecognized_field" } }
    if ($Manifest.format -ne "quata-logical-backup-v1" -or $Manifest.encryption -ne "AES-256-GCM" -or $Manifest.tls -ne "verify-full_explicit_ca" -or $Manifest.grantsIncluded -ne $true -or $Manifest.containsConnectionData -ne $false) { Fail "backup_manifest_invalid" }
    if ($Manifest.scope -eq "Full") { $expected=@([pscustomobject]@{name="database.dump.enc";kind="full_custom"}) }
    elseif ($Manifest.scope -eq "Critical") { $expected=@([pscustomobject]@{name="schema.dump.enc";kind="schema_custom"},[pscustomobject]@{name="critical-data.dump.enc";kind="critical_data_custom"}) }
    else { Fail "backup_manifest_invalid_scope" }
    $artifacts=@($Manifest.artifacts); if ($artifacts.Count -ne $expected.Count) { Fail "backup_manifest_artifact_cardinality" }
    for ($index=0; $index -lt $expected.Count; $index++) {
        $artifact=$artifacts[$index]; $rule=$expected[$index]
        $allowedArtifactFields=if ($rule.kind -eq "critical_data_custom") { @("name","kind","tables","plaintextSha256","ciphertextSha256") } else { @("name","kind","plaintextSha256","ciphertextSha256") }
        foreach ($property in $artifact.PSObject.Properties.Name) { if ($property -notin $allowedArtifactFields) { Fail "backup_manifest_artifact_invalid" } }
        if ($artifact.name -ne $rule.name -or $artifact.kind -ne $rule.kind -or [string]::IsNullOrWhiteSpace($artifact.name) -or $artifact.name -match '[\\/]' -or $artifact.name.Contains('..') -or [IO.Path]::IsPathRooted($artifact.name)) { Fail "backup_manifest_artifact_invalid" }
        if ($artifact.plaintextSha256 -notmatch '^[a-f0-9]{64}$' -or $artifact.ciphertextSha256 -notmatch '^[a-f0-9]{64}$') { Fail "backup_manifest_checksum_invalid" }
        if ($rule.kind -eq "critical_data_custom") { if (@($artifact.tables).Count -ne 2 -or $artifact.tables[0] -ne "public.community_comments" -or $artifact.tables[1] -ne "public.official_post_likes") { Fail "backup_manifest_critical_tables_invalid" } }
        elseif ($null -ne $artifact.PSObject.Properties["tables"]) { Fail "backup_manifest_artifact_invalid" }
    }
    $expectedFiles=@("manifest.json") + @($expected | ForEach-Object { $_.name })
    $actualFiles=@(Get-ChildItem -LiteralPath $SetPath -File | ForEach-Object Name)
    if ($actualFiles.Count -ne $expectedFiles.Count -or @($actualFiles | Where-Object { $_ -notin $expectedFiles }).Count -ne 0 -or @(Get-ChildItem -LiteralPath $SetPath -Directory).Count -ne 0) { Fail "backup_set_contains_unexpected_files" }
}
function Read-Key([string]$Path) { try { $key=[Convert]::FromBase64String((Get-Content -LiteralPath $Path -Raw).Trim()) } catch { Fail "encryption_key_invalid" }; if ($key.Length -ne 32) { Fail "encryption_key_invalid" }; return ,$key }
function Decrypt-File([string]$Source, [string]$Destination, [byte[]]$Key) {
    & node (Join-Path $PSScriptRoot "db-backup-crypto.mjs") decrypt $Source $Destination $EncryptionKeyFile *> $null
    if ($LASTEXITCODE -ne 0) { Fail "backup_decryption_failed" }
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Fail "docker_required" }
if (-not (Test-Path -LiteralPath (Join-Path $BackupSet "manifest.json") -PathType Leaf)) { Fail "backup_manifest_missing" }
$manifest=Get-Content -LiteralPath (Join-Path $BackupSet "manifest.json") -Raw | ConvertFrom-Json
Assert-Manifest $manifest $BackupSet
$key=Read-Key $EncryptionKeyFile; $work=Join-Path ([IO.Path]::GetTempPath()) ("quata-restore-drill-"+[guid]::NewGuid().ToString("N")); $name="quata-restore-"+[guid]::NewGuid().ToString("N").Substring(0,12); $password=[guid]::NewGuid().ToString("N")
try {
    Restrict-Directory $work
    foreach ($artifact in $manifest.artifacts) {
        $encrypted=Join-Path $BackupSet $artifact.name; if (-not (Test-Path -LiteralPath $encrypted)) { Fail "backup_artifact_missing" }
        if ([string]::IsNullOrWhiteSpace($artifact.ciphertextSha256) -or (Get-FileHash $encrypted -Algorithm SHA256).Hash.ToLowerInvariant() -cne $artifact.ciphertextSha256) { Fail "backup_ciphertext_checksum_mismatch" }
        $plainName=$artifact.name -replace '\.enc$',''; $plain=Join-Path $work $plainName; Decrypt-File $encrypted $plain $key
        if ((Get-FileHash $plain -Algorithm SHA256).Hash.ToLowerInvariant() -cne $artifact.plaintextSha256) { Fail "backup_checksum_mismatch" }
    }
    if ($manifest.scope -eq "Full" -and $ValidateSecurityReleaseScope) {
        $fullDump = Join-Path $work "database.dump"
        $toc = @(& docker run --rm -v "${work}:/backup:ro" $DockerImage pg_restore --list /backup/database.dump 2>$null)
        if ($LASTEXITCODE -ne 0) { Fail "backup_toc_unreadable" }
        if ($ShowRelevantToc) {
            $toc | Where-Object {
                $_ -match "community_comments|official_post_likes|quata_chat_auth_profile_id|quata_current_profile_is_admin|quata_guard_official_post_likes|quata_current_profile_id"
            } | Write-Output
        }
        foreach ($table in @("community_comments", "official_post_likes")) {
            if (-not @($toc | Where-Object { $_ -match "\bTABLE\b" -and $_ -match "\b$table\b" }).Count) {
                Fail "backup_toc_required_table_missing"
            }
            if (-not @($toc | Where-Object { $_ -match "\bTABLE DATA\b" -and $_ -match "\b$table\b" }).Count) {
                Fail "backup_toc_required_data_missing"
            }
            if (-not @($toc | Where-Object { $_ -match "\bACL\b" -and $_ -match "\b$table\b" }).Count) {
                Fail "backup_toc_required_acl_missing"
            }
        }
        if (-not @($toc | Where-Object { $_ -match "\bROW SECURITY\b" -and $_ -match "\bcommunity_comments\b" }).Count -or
            -not @($toc | Where-Object { $_ -match "\bPOLICY\b" -and $_ -match "\bcommunity_comments\b" }).Count) {
            Fail "backup_toc_community_comments_policy_state_missing"
        }
        if (@($toc | Where-Object { $_ -match "\bROW SECURITY\b" -and $_ -match "\bofficial_post_likes\b" }).Count -or
            @($toc | Where-Object { $_ -match "\bPOLICY\b" -and $_ -match "\bofficial_post_likes\b" }).Count) {
            Fail "backup_toc_official_likes_precondition_drift"
        }
        foreach ($function in @(
            "quata_chat_auth_profile_id",
            "quata_current_profile_is_admin",
            "quata_guard_official_post_likes",
            "quata_current_profile_id"
        )) {
            if (-not @($toc | Where-Object { $_ -match "\bFUNCTION\b" -and $_ -match "\b$function\b" }).Count) {
                Fail "backup_toc_required_function_missing"
            }
        }
    }
    & docker run -d --rm --name $name -e "POSTGRES_PASSWORD=$password" -v "${work}:/backup" $DockerImage 2>$null | Out-Null; if ($LASTEXITCODE -ne 0) { Fail "restore_target_start_failed" }
    $ready=$false; foreach ($n in 1..30) { & docker exec $name pg_isready -U postgres 2>$null | Out-Null; if ($LASTEXITCODE -eq 0) { $ready=$true; break }; Start-Sleep -Milliseconds 500 }; if (-not $ready) { Fail "restore_target_not_ready" }
    $files=@($manifest.artifacts | ForEach-Object { $_.name -replace '\.enc$','' })
    foreach ($file in $files) {
        $restoreArguments = @("exec", "-e", "PGPASSWORD=$password", $name, "pg_restore", "-U", "postgres", "-d", "postgres", "--no-owner", "--no-acl")
        if ($CleanTarget) { $restoreArguments += @("--clean", "--if-exists") }
        if ($AffectedTablesOnly) {
            $restoreArguments += @("--table=community_comments", "--table=official_post_likes")
        }
        $restoreArguments += "/backup/$file"
        & docker @restoreArguments 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) { Fail "restore_command_failed" }
    }
    $verified = & docker exec -e "PGPASSWORD=$password" $name psql -U postgres -d postgres -Atqc "select case when to_regclass('public.community_comments') is not null and to_regclass('public.official_post_likes') is not null then 'ok' else 'missing' end" 2>$null | Select-String -Quiet '^ok$'
    if (-not $verified) { Fail "restore_verification_failed" }
    if ($ExpectedCommunityComments -ge 0 -or $ExpectedOfficialPostLikes -ge 0) {
        if ($ExpectedCommunityComments -lt 0 -or $ExpectedOfficialPostLikes -lt 0) { Fail "restore_expected_counts_incomplete" }
        $counts = & docker exec -e "PGPASSWORD=$password" $name psql -U postgres -d postgres -Atqc "select (select count(*) from public.community_comments)::text || ',' || (select count(*) from public.official_post_likes)::text" 2>$null
        if ($counts -cne "$ExpectedCommunityComments,$ExpectedOfficialPostLikes") { Fail "restore_row_count_verification_failed" }
    }
    Write-Output "logical_backup_restore_drill_passed"
}
finally { $previousErrorActionPreference=$ErrorActionPreference; $ErrorActionPreference="SilentlyContinue"; try { & docker rm -f $name 2>$null | Out-Null } finally { $ErrorActionPreference=$previousErrorActionPreference }; if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force }; if ($null -ne $key) { [Array]::Clear($key,0,$key.Length) } }
