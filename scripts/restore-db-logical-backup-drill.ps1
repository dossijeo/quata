[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BackupSet,
    [Parameter(Mandatory = $true)][string]$EncryptionKeyFile,
    [string]$DockerImage = "postgres:17-alpine",
    [switch]$CleanTarget,
    [switch]$AffectedTablesOnly,
    [switch]$ValidateSecurityReleaseScope,
    [switch]$ProfileFollowScope,
    [switch]$CommunityPostLikesScope,
    [switch]$ShowRelevantToc,
    [int]$ExpectedCommunityComments = -1,
    [int]$ExpectedOfficialPostLikes = -1,
    [int]$ExpectedCommunityProfiles = -1,
    [int]$ExpectedCommunityProfileFollows = -1,
    [int]$ExpectedCommunityPostLikes = -1
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
$legacyExpectedCounts = $ExpectedCommunityComments -ge 0 -or $ExpectedOfficialPostLikes -ge 0
$profileExpectedCounts = $ExpectedCommunityProfiles -ge 0 -or $ExpectedCommunityProfileFollows -ge 0
$likesExpectedCount = $ExpectedCommunityPostLikes -ge 0
if ($ProfileFollowScope -and $CommunityPostLikesScope) { Fail "restore_scope_conflict" }
if (($ProfileFollowScope -or $CommunityPostLikesScope) -and $AffectedTablesOnly) { Fail "restore_scope_conflict" }
if ($ProfileFollowScope -and ($ValidateSecurityReleaseScope -or $legacyExpectedCounts -or $likesExpectedCount)) { Fail "restore_scope_conflict" }
if ($CommunityPostLikesScope -and ($ValidateSecurityReleaseScope -or $legacyExpectedCounts -or $profileExpectedCounts)) { Fail "restore_scope_conflict" }
if (-not $ProfileFollowScope -and $profileExpectedCounts) { Fail "restore_profile_follow_scope_required" }
if (-not $CommunityPostLikesScope -and $likesExpectedCount) { Fail "restore_community_post_likes_scope_required" }
if ($CommunityPostLikesScope -and -not $likesExpectedCount) { Fail "restore_expected_community_post_likes_required" }
$manifest=Get-Content -LiteralPath (Join-Path $BackupSet "manifest.json") -Raw | ConvertFrom-Json
Assert-Manifest $manifest $BackupSet
$restoreTables = if ($ProfileFollowScope) { @("community_profiles", "community_profile_follows") } elseif ($CommunityPostLikesScope) { @("community_post_likes") } else { @("community_comments", "official_post_likes") }
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
    if ($ProfileFollowScope) {
        if ($manifest.scope -ne "Full") { Fail "restore_profile_follow_scope_requires_full_backup" }
        $fullDump = Join-Path $work "database.dump"
        $toc = @(& docker run --rm -v "${work}:/backup:ro" $DockerImage pg_restore --list /backup/database.dump 2>$null)
        if ($LASTEXITCODE -ne 0) { Fail "backup_toc_unreadable" }
        if ($ShowRelevantToc) {
            $toc | Where-Object { $_ -match "community_profiles|community_profile_follows" } | Write-Output
        }
        foreach ($table in $restoreTables) {
            if (-not @($toc | Where-Object { $_ -match "\bTABLE\b" -and $_ -match "\b$table\b" }).Count) {
                Fail "backup_toc_profile_follow_table_missing"
            }
            if (-not @($toc | Where-Object { $_ -match "\bTABLE DATA\b" -and $_ -match "\b$table\b" }).Count) {
                Fail "backup_toc_profile_follow_data_missing"
            }
            if (-not @($toc | Where-Object { $_ -match "\bACL\b" -and $_ -match "\b$table\b" }).Count) {
                Fail "backup_toc_profile_follow_acl_missing"
            }
        }
    }
    if ($CommunityPostLikesScope) {
        if ($manifest.scope -ne "Full") { Fail "restore_community_post_likes_scope_requires_full_backup" }
        $fullDump = Join-Path $work "database.dump"
        $toc = @(& docker run --rm -v "${work}:/backup:ro" $DockerImage pg_restore --list /backup/database.dump 2>$null)
        if ($LASTEXITCODE -ne 0) { Fail "backup_toc_unreadable" }
        if ($ShowRelevantToc) {
            $toc | Where-Object { $_ -match "community_post_likes|quata_chat_auth_profile_id|community_profiles|\bauth\b.*\buid\(\)|\bSCHEMA\b.*\bauth\b" } | Write-Output
        }
        if (-not @($toc | Where-Object { $_ -match "\bTABLE\b" -and $_ -match "\bcommunity_post_likes\b" }).Count) {
            Fail "backup_toc_community_post_likes_table_missing"
        }
        if (-not @($toc | Where-Object { $_ -match "\bTABLE DATA\b" -and $_ -match "\bcommunity_post_likes\b" }).Count) {
            Fail "backup_toc_community_post_likes_data_missing"
        }
        if (-not @($toc | Where-Object { $_ -match "\bACL\b" -and $_ -match "\bcommunity_post_likes\b" }).Count) {
            Fail "backup_toc_community_post_likes_acl_missing"
        }
        if (-not @($toc | Where-Object { $_ -match "\bROW SECURITY\b" -and $_ -match "\bcommunity_post_likes\b" }).Count -or
            -not @($toc | Where-Object { $_ -match "\bPOLICY\b" -and $_ -match "\bcommunity_post_likes\b" }).Count) {
            Fail "backup_toc_community_post_likes_policy_state_missing"
        }
        if (-not @($toc | Where-Object { $_ -match "\bFUNCTION\b" -and $_ -match "\bquata_chat_auth_profile_id\b" }).Count) {
            Fail "backup_toc_community_post_likes_resolver_missing"
        }
        if (-not @($toc | Where-Object { $_ -match "\bACL\b" -and $_ -match "\bquata_chat_auth_profile_id\b" }).Count) {
            Fail "backup_toc_community_post_likes_resolver_acl_missing"
        }
        if (-not @($toc | Where-Object { $_ -match "\bTABLE public community_profiles\b" }).Count -or
            -not @($toc | Where-Object { $_ -match "\bSCHEMA\b.*\bauth\b" }).Count -or
            -not @($toc | Where-Object { $_ -match "\bFUNCTION auth uid\(\)" }).Count) {
            Fail "backup_toc_community_post_likes_resolver_dependency_missing"
        }
        $likesRestoreList = Join-Path $work "community-post-likes.restore.list"
        $likesRestoreEntries = @($toc | Where-Object {
            $_ -match '^;' -or
            (($_ -match '\bcommunity_post_likes\b') -and ($_ -notmatch '\bFK CONSTRAINT\b')) -or
            ($_ -match '^\d+;\s+\d+\s+\d+\s+(?:FUNCTION public|ACL public FUNCTION) quata_chat_auth_profile_id\(\)') -or
            ($_ -match '^\d+;\s+\d+\s+\d+\s+TABLE public community_profiles\b') -or
            ($_ -match '^\d+;\s+\d+\s+\d+\s+SCHEMA - auth\b') -or
            ($_ -match '^\d+;\s+\d+\s+\d+\s+FUNCTION auth uid\(\)')
        })
        [IO.File]::WriteAllLines($likesRestoreList, [string[]]$likesRestoreEntries, [Text.UTF8Encoding]::new($false))
        if (-not (Test-Path -LiteralPath $likesRestoreList) -or (Get-Item -LiteralPath $likesRestoreList).Length -eq 0) {
            Fail "backup_community_post_likes_restore_list_empty"
        }
    }
    & docker run -d --rm --name $name -e "POSTGRES_PASSWORD=$password" -v "${work}:/backup" $DockerImage 2>$null | Out-Null; if ($LASTEXITCODE -ne 0) { Fail "restore_target_start_failed" }
    $ready=$false; foreach ($n in 1..30) { & docker exec $name pg_isready -U postgres 2>$null | Out-Null; if ($LASTEXITCODE -eq 0) { $ready=$true; break }; Start-Sleep -Milliseconds 500 }; if (-not $ready) { Fail "restore_target_not_ready" }
    $files=@($manifest.artifacts | ForEach-Object { $_.name -replace '\.enc$','' })
    if ($CommunityPostLikesScope) {
        $supportSql = @'
create role anon nologin;
create role authenticated nologin;
create role service_role nologin;
'@
        & docker exec -e "PGPASSWORD=$password" $name psql -U postgres -d postgres -X -v ON_ERROR_STOP=1 -c $supportSql 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) { Fail "restore_community_post_likes_support_failed" }
    }
    foreach ($file in $files) {
        $restoreArguments = @("exec", "-e", "PGPASSWORD=$password", $name, "pg_restore", "-U", "postgres", "-d", "postgres", "--no-owner")
        if (-not $CommunityPostLikesScope) { $restoreArguments += "--no-acl" }
        if ($CleanTarget) { $restoreArguments += @("--clean", "--if-exists") }
        if ($CommunityPostLikesScope) {
            $restoreArguments += "--use-list=/backup/community-post-likes.restore.list"
        }
        elseif ($AffectedTablesOnly -or $ProfileFollowScope) {
            $restoreArguments += @($restoreTables | ForEach-Object { "--table=$_" })
        }
        $restoreArguments += "/backup/$file"
        & docker @restoreArguments 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) { Fail "restore_command_failed" }
    }
    $requiredRelations = if ($ProfileFollowScope) { "to_regclass('public.community_profiles') is not null and to_regclass('public.community_profile_follows') is not null" } elseif ($CommunityPostLikesScope) { "to_regclass('public.community_post_likes') is not null" } else { "to_regclass('public.community_comments') is not null and to_regclass('public.official_post_likes') is not null" }
    $verified = & docker exec -e "PGPASSWORD=$password" $name psql -U postgres -d postgres -Atqc "select case when $requiredRelations then 'ok' else 'missing' end" 2>$null | Select-String -Quiet '^ok$'
    if (-not $verified) { Fail "restore_verification_failed" }
    if ($CommunityPostLikesScope) {
        $securitySql = @'
with policy_state as (
    select count(*) = 3
       and bool_and(roles = '{public}'::name[])
       and bool_and(
           (policyname = 'public delete likes' and cmd = 'DELETE' and qual = 'true' and with_check is null)
        or (policyname = 'public insert likes' and cmd = 'INSERT' and qual is null and with_check = 'true')
        or (policyname = 'public read likes' and cmd = 'SELECT' and qual = 'true' and with_check is null)
       ) as exact
      from pg_policies
     where schemaname = 'public' and tablename = 'community_post_likes'
), grant_state as (
    select
      string_agg(privilege_type, ',' order by privilege_type) filter (where grantee = 'anon') = 'DELETE,INSERT,REFERENCES,SELECT,TRIGGER,TRUNCATE,UPDATE'
      and string_agg(privilege_type, ',' order by privilege_type) filter (where grantee = 'authenticated') = 'DELETE,INSERT,REFERENCES,SELECT,TRIGGER,TRUNCATE,UPDATE' as exact
      from information_schema.role_table_grants
     where table_schema = 'public' and table_name = 'community_post_likes'
       and grantee in ('anon', 'authenticated')
), resolver_state as (
    select count(*) = 1
       and bool_and(l.lanname = 'sql' and p.provolatile = 's' and p.prosecdef
           and p.proconfig = array['search_path=public, auth']::text[]
           and btrim(regexp_replace(p.prosrc, '[[:space:]]+', ' ', 'g')) = 'select cp.id from public.community_profiles cp where auth.uid() is not null and cp.account_status = ''active'' and (cp.id = auth.uid() or cp.auth_user_id = auth.uid()) limit 1'
           and exists (select 1 from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl where acl.grantee = 0 and acl.privilege_type = 'EXECUTE')
           and has_function_privilege('anon', p.oid, 'execute')
           and has_function_privilege('authenticated', p.oid, 'execute')) as exact
      from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
      join pg_language l on l.oid = p.prolang
     where n.nspname = 'public' and p.proname = 'quata_chat_auth_profile_id' and p.pronargs = 0
)
select case when c.relrowsecurity and policy_state.exact and grant_state.exact and resolver_state.exact then 'ok' else 'mismatch' end
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
 cross join policy_state cross join grant_state cross join resolver_state
 where n.nspname = 'public' and c.relname = 'community_post_likes';
'@
        $securityVerified = & docker exec -e "PGPASSWORD=$password" $name psql -U postgres -d postgres -Atqc $securitySql 2>$null | Select-String -Quiet '^ok$'
        if (-not $securityVerified) {
            if ($ShowRelevantToc) {
                & docker exec -e "PGPASSWORD=$password" $name psql -U postgres -d postgres -Atqc "select policyname || '|' || cmd || '|' || roles::text || '|' || coalesce(qual,'<null>') || '|' || coalesce(with_check,'<null>') from pg_policies where schemaname='public' and tablename='community_post_likes' order by policyname; select grantee || '|' || privilege_type from information_schema.role_table_grants where table_schema='public' and table_name='community_post_likes' and grantee in ('anon','authenticated') order by grantee, privilege_type;" 2>$null | Write-Output
                & docker exec -e "PGPASSWORD=$password" $name psql -U postgres -d postgres -Atqc "select n.nspname || '|' || p.proname || '|' || p.pronargs::text || '|' || p.provolatile::text || '|' || p.prosecdef::text || '|' || coalesce(p.proconfig::text,'<null>') || '|' || regexp_replace(trim(p.prosrc), '\s+', ' ', 'g') from pg_proc p join pg_namespace n on n.oid=p.pronamespace where p.proname='quata_chat_auth_profile_id';" 2>&1 | Write-Output
            }
            Fail "restore_community_post_likes_security_state_mismatch"
        }
    }
    if ($profileExpectedCounts) {
        if ($ExpectedCommunityProfiles -lt 0 -or $ExpectedCommunityProfileFollows -lt 0) { Fail "restore_expected_counts_incomplete" }
        $counts = & docker exec -e "PGPASSWORD=$password" $name psql -U postgres -d postgres -Atqc "select (select count(*) from public.community_profiles)::text || ',' || (select count(*) from public.community_profile_follows)::text" 2>$null
        if ($counts -cne "$ExpectedCommunityProfiles,$ExpectedCommunityProfileFollows") { Fail "restore_row_count_verification_failed" }
    }
    elseif ($likesExpectedCount) {
        $count = & docker exec -e "PGPASSWORD=$password" $name psql -U postgres -d postgres -Atqc "select count(*) from public.community_post_likes" 2>$null
        if ($count -cne "$ExpectedCommunityPostLikes") { Fail "restore_row_count_verification_failed" }
    }
    elseif ($legacyExpectedCounts) {
        if ($ExpectedCommunityComments -lt 0 -or $ExpectedOfficialPostLikes -lt 0) { Fail "restore_expected_counts_incomplete" }
        $counts = & docker exec -e "PGPASSWORD=$password" $name psql -U postgres -d postgres -Atqc "select (select count(*) from public.community_comments)::text || ',' || (select count(*) from public.official_post_likes)::text" 2>$null
        if ($counts -cne "$ExpectedCommunityComments,$ExpectedOfficialPostLikes") { Fail "restore_row_count_verification_failed" }
    }
    Write-Output "logical_backup_restore_drill_passed"
}
finally { $previousErrorActionPreference=$ErrorActionPreference; $ErrorActionPreference="SilentlyContinue"; try { & docker rm -f $name 2>$null | Out-Null } finally { $ErrorActionPreference=$previousErrorActionPreference }; if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force }; if ($null -ne $key) { [Array]::Clear($key,0,$key.Length) } }
