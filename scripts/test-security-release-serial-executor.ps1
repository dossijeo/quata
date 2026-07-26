[CmdletBinding()]
param()

# Runs only against a disposable PostgreSQL 17 Docker container.
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$temp = Join-Path ([IO.Path]::GetTempPath()) ("quata-serial-executor-" + [guid]::NewGuid().ToString("N"))
$container = "quata-serial-executor-" + [guid]::NewGuid().ToString("N")
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0); $listener.Start(); $port = ([Net.IPEndPoint]$listener.LocalEndpoint).Port; $listener.Stop()
$oldNodePath = $env:NODE_PATH
function Assert-True([bool]$value, [string]$message) { if (-not $value) { throw $message } }
function Sql([string]$text) {
    $oldNativePreference = $PSNativeCommandUseErrorActionPreference
    try { $PSNativeCommandUseErrorActionPreference = $false; $text | docker exec -i $container psql -U postgres -X -v ON_ERROR_STOP=1 *> $null }
    finally { $PSNativeCommandUseErrorActionPreference = $oldNativePreference }
    if ($LASTEXITCODE -ne 0) { throw "Fixture SQL failed" }
}
function Exec([string[]]$a, [hashtable]$env = @{}) {
    $old = @{}; foreach ($k in $env.Keys) { $old[$k] = [Environment]::GetEnvironmentVariable($k); [Environment]::SetEnvironmentVariable($k, $env[$k]) }
    $oldNativePreference = $PSNativeCommandUseErrorActionPreference; $oldErrorPreference = $ErrorActionPreference
    try { $PSNativeCommandUseErrorActionPreference = $false; $ErrorActionPreference = "Continue"; $o = @(& node (Join-Path $PSScriptRoot "security-release-serial-executor.mjs") @a 2>&1); return [pscustomobject]@{ code=$LASTEXITCODE; output=$o } }
    finally { $ErrorActionPreference = $oldErrorPreference; $PSNativeCommandUseErrorActionPreference = $oldNativePreference; foreach ($k in $env.Keys) { [Environment]::SetEnvironmentVariable($k, $old[$k]) } }
}
function Fixture {
    Sql @"
create schema auth;
create schema supabase_migrations;
create role anon nologin;
create role authenticated nologin;
create table supabase_migrations.schema_migrations(version text primary key, statements text[], name text);
create function auth.uid() returns uuid language sql stable as 'select null::uuid';
create table public.community_comments(id uuid primary key, profile_id uuid not null, post_id uuid not null, body text);
create function public.quata_chat_auth_profile_id() returns uuid language sql stable as 'select null::uuid';
create function public.quata_current_profile_is_admin() returns boolean language sql stable as 'select false';
create table public.official_post_likes(id uuid primary key, profile_id uuid not null);
create function public.quata_current_profile_id() returns uuid language sql stable as 'select null::uuid';
create function public.quata_current_role_is_service() returns boolean language sql stable as 'select false';
create or replace function public.quata_guard_official_post_likes()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as `$guard`$
declare
    v_actor uuid := public.quata_current_profile_id();
begin
    if public.quata_current_role_is_service() then
        if tg_op = 'DELETE' then
            return old;
        end if;
        return new;
    end if;

    if v_actor is null then
        raise exception 'Authentication required'
            using errcode = '42501';
    end if;

    if tg_op = 'INSERT' and new.profile_id <> v_actor then
        raise exception 'Likes must be created by the current profile'
            using errcode = '42501';
    end if;

    if tg_op = 'DELETE' and old.profile_id <> v_actor and not public.quata_current_profile_is_admin() then
        raise exception 'Only the like owner or an administrator can remove this like'
            using errcode = '42501';
    end if;

    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
`$guard`$;
create trigger quata_guard_official_post_likes_trg before insert or delete on public.official_post_likes for each row execute function public.quata_guard_official_post_likes();
grant select, insert, delete on public.official_post_likes to anon, authenticated;
grant select, insert, delete, update on public.community_comments to anon, authenticated;
create policy "public delete comments" on public.community_comments for delete to public using (true);
create policy "public insert comments" on public.community_comments for insert to public with check (true);
create policy "public read comments" on public.community_comments for select to public using (true);
create policy "public update comments" on public.community_comments for update to public using (true) with check (true);
"@
    # Replace the compact bootstrap definition with the exact production
    # definition used by the original migration. The rollback deliberately
    # fingerprints pg_get_functiondef byte-for-byte.
    $officialBaseline = Get-Content -LiteralPath (Join-Path $root "scripts/official-likes-rls-migration-test.sql") -Raw
    $script:guardDefinition = [regex]::Match($officialBaseline, '(?s)create function public\.quata_guard_official_post_likes\(\).*?\$\$;').Value
    Assert-True (-not [string]::IsNullOrWhiteSpace($script:guardDefinition)) "production guard fixture definition missing"
    $script:guardDefinition = $script:guardDefinition -replace '^create function', 'create or replace function'
    Sql $script:guardDefinition
}
try {
    New-Item -ItemType Directory -Path $temp | Out-Null
    npm --prefix $temp install --ignore-scripts --no-save --package-lock=false --fund=false --audit=false pg@8.16.3
    if ($LASTEXITCODE -ne 0) { throw "Unable to install pg" }; $env:NODE_PATH = Join-Path $temp "node_modules"
    $password = "serial-executor-disposable-only"
    docker run -d --rm --name $container -e "POSTGRES_PASSWORD=$password" -p "${port}:5432" postgres:17 sh -c "openssl req -new -x509 -days 1 -nodes -subj /CN=localhost -out /tmp/server.crt -keyout /tmp/server.key >/dev/null 2>&1 && chown postgres:postgres /tmp/server.crt /tmp/server.key && chmod 600 /tmp/server.key && exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/tmp/server.crt -c ssl_key_file=/tmp/server.key" | Out-Null
    for ($i=0; $i -lt 40; $i++) { docker exec $container pg_isready -U postgres *> $null; if ($LASTEXITCODE -eq 0) { break }; Start-Sleep -Milliseconds 250 }; if ($LASTEXITCODE -ne 0) { throw "PostgreSQL not ready" }
    $ca = Join-Path $temp "ca.pem"; docker cp "${container}:/tmp/server.crt" $ca
    $env:SUPABASE_DB_URL = "postgresql://postgres:${password}@localhost:${port}/postgres"; $env:SUPABASE_DB_TLS_CA_FILE = $ca
    $probe = "import {readFile} from 'node:fs/promises'; import {validateAllowlist} from './scripts/security-release-serial-executor.mjs'; const a=JSON.parse(await readFile('./scripts/security-release-serial-allowlist.json')); const s=await readFile('./supabase/migrations/20260726171001_community_comments_delete_rls.sql','utf8'); let ok=false; try{validateAllowlist(a,'20260726171001',s+'x')}catch{ok=true}; if(!ok)process.exit(7)"
    $probe | node --input-type=module -; if ($LASTEXITCODE -ne 0) { throw "Hash drift was accepted" }
    Fixture
    $env:QUATA_SERIAL_EXECUTOR_TEST_FORCE_FUNCTION_DDL_LOCK = "1"
    $outputRelative = "build-reports/security-release/serial-executor-disposable-$([guid]::NewGuid().ToString('N')).json"
    $dry = Exec @("--action", "dry-run", "--out", $outputRelative); Assert-True ($dry.code -eq 0) "dry-run failed"; Assert-True (Test-Path (Join-Path $root $outputRelative)) "Windows output path was rejected or not written"; $fp1 = (($dry.output[-1] | ConvertFrom-Json).migrations | Where-Object version -eq "20260726171001").preconditionSha256
    $primaryProjectFingerprint = ($dry.output[-1] | ConvertFrom-Json).databaseProjectFingerprint
    Sql "create role release_alt login password 'serial-executor-alt'; grant usage on schema supabase_migrations to release_alt; grant select on supabase_migrations.schema_migrations to release_alt; grant execute on function pg_control_system() to release_alt;"
    $primaryUrl = $env:SUPABASE_DB_URL
    $env:SUPABASE_DB_URL = "postgresql://release_alt:serial-executor-alt@localhost:${port}/postgres"
    $altDry = Exec @("--action", "dry-run"); Assert-True ($altDry.code -eq 0) "same-pooler alternate-user dry-run failed"
    $altProjectFingerprint = ($altDry.output[-1] | ConvertFrom-Json).databaseProjectFingerprint
    Assert-True ($altProjectFingerprint -ne $primaryProjectFingerprint) "target fingerprint did not bind the normalized database username"
    $env:SUPABASE_DB_URL = $primaryUrl
    $failed = Exec @("--action", "apply-001", "--expected-precondition-sha256", $fp1) @{QUATA_SERIAL_EXECUTOR_TEST_FAIL_BEFORE_LEDGER="1"}; Assert-True ($failed.code -ne 0) "injected rollback failure passed"
    $atomic = (& docker exec $container psql -U postgres -A -t -c "select (select count(*) from supabase_migrations.schema_migrations), (select relrowsecurity from pg_class where oid='public.community_comments'::regclass);").Trim(); Assert-True ($atomic -eq "0|f") "migration and ledger were not atomic: $atomic"
    $rollbackCosts = (& docker exec $container psql -U postgres -A -t -c "select string_agg(procost::text,',' order by proname) from pg_proc where proname in ('quata_chat_auth_profile_id','quata_current_profile_is_admin');").Trim(); Assert-True ($rollbackCosts -eq "100,100") "failed transaction did not restore function COST: $rollbackCosts"
    $preLockRace = Start-Job -ScriptBlock { param($file,$fp) $env:QUATA_SERIAL_EXECUTOR_TEST_HOLD_LOCK_MS='1000'; & node $file --action apply-001 --expected-precondition-sha256 $fp; exit $LASTEXITCODE } -ArgumentList (Join-Path $PSScriptRoot "security-release-serial-executor.mjs"),$fp1
    Start-Sleep -Milliseconds 250; Sql "insert into supabase_migrations.schema_migrations(version, statements, name) values ('20260726171001', array['external'], 'external');"
    Wait-Job $preLockRace | Out-Null; $oldErrorPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'; $preLockOut=Receive-Job $preLockRace 2>&1; $ErrorActionPreference=$oldErrorPreference; Remove-Job $preLockRace; Assert-True (($preLockOut -join "`n") -match "ledger_changed_after_lock") "external pre-lock writer was not caught by locked revalidation"
    $raceState=(& docker exec $container psql -U postgres -A -t -c "select (select relrowsecurity from pg_class where oid='public.community_comments'::regclass), (select name from supabase_migrations.schema_migrations where version='20260726171001');").Trim(); Assert-True ($raceState -eq "f|external") "pre-lock race left partial DDL: $raceState"; Sql "delete from supabase_migrations.schema_migrations where version='20260726171001';"
    $job = Start-Job -ScriptBlock { param($file,$fp) $env:QUATA_SERIAL_EXECUTOR_TEST_HOLD_AFTER_LOCK_MS='1800'; & node $file --action apply-001 --expected-precondition-sha256 $fp; exit $LASTEXITCODE } -ArgumentList (Join-Path $PSScriptRoot "security-release-serial-executor.mjs"),$fp1
    Start-Sleep -Milliseconds 350; $rival = Exec @("--action", "apply-001", "--expected-precondition-sha256", $fp1); Assert-True ($rival.code -ne 0 -and ($rival.output -join "`n") -match "lock_unavailable") "advisory lock allowed concurrent release"
    $writer = Start-Job -ScriptBlock { param($name) & docker exec $name psql -U postgres -X -v ON_ERROR_STOP=1 -c "alter table public.community_comments add column external_writer_race integer;"; exit $LASTEXITCODE } -ArgumentList $container
    Start-Sleep -Milliseconds 300; Assert-True ($writer.State -eq "Running") "external catalog writer was not blocked by table lock"
    Wait-Job $job | Out-Null; $state = $job.State; $jobOut=Receive-Job $job; Remove-Job $job; Assert-True ($state -eq "Completed") "lock owner failed: $($jobOut -join "`n")"
    Wait-Job $writer | Out-Null; $writerState=$writer.State; Receive-Job $writer | Out-Null; Remove-Job $writer; Assert-True ($writerState -eq "Completed") "external writer did not resume after commit"
    $l1 = (& docker exec $container psql -U postgres -A -t -c "select version||'|'||name||'|'||cardinality(statements) from supabase_migrations.schema_migrations;").Trim(); Assert-True ($l1 -eq "20260726171001|community_comments_delete_rls|1") "001 ledger not exact: $l1"
    $again = Exec @("--action", "apply-001", "--expected-precondition-sha256", $fp1); Assert-True ($again.code -ne 0 -and ($again.output -join "`n") -match "duplicate_ledger") "duplicate was accepted"
    $dry2=Exec @("--action", "dry-run"); $dry2Report=($dry2.output[-1]|ConvertFrom-Json); $fp2=($dry2Report.migrations|Where-Object version -eq "20260726171002").preconditionSha256; $gate=Join-Path $temp "gates.json"; $shaA=('a'*64); $project=$dry2Report.databaseProjectFingerprint; @{schemaVersion=1;releaseCommit=('b'*40);snapshotFingerprint=('c'*64);databaseProjectFingerprint=$project;generatedAt=(Get-Date).ToUniversalTime().ToString('o');expiresAt=(Get-Date).AddHours(1).ToUniversalTime().ToString('o');migration="20260726171001";status="passed";preconditionSha256=$fp1;postflight=@{status="passed";sha256=$shaA};reports=@{dbReleaseSafety=@{status="passed";sha256=$shaA;databaseProjectFingerprint=$project};backendCompatibility=@{status="passed";sha256=$shaA;databaseProjectFingerprint=$project};sb07=@{status="passed";sha256=$shaA;databaseProjectFingerprint=$project}}}|ConvertTo-Json -Depth 4|Set-Content $gate
    $gateHash=(Get-FileHash -LiteralPath $gate -Algorithm SHA256).Hash.ToLowerInvariant()
    $wrongTarget=Exec @("--action","apply-002","--expected-precondition-sha256",$fp2,"--gate-evidence",$gate,"--expected-gate-evidence-sha256",$gateHash,"--expected-release-commit",('b'*40),"--expected-snapshot-fingerprint",('c'*64),"--expected-database-project-fingerprint",('e'*64)); Assert-True ($wrongTarget.code -ne 0 -and ($wrongTarget.output -join "`n") -match "gate_evidence_invalid") "wrong target fingerprint was accepted"
    $two=Exec @("--action","apply-002","--expected-precondition-sha256",$fp2,"--gate-evidence",$gate,"--expected-gate-evidence-sha256",$gateHash,"--expected-release-commit",('b'*40),"--expected-snapshot-fingerprint",('c'*64),"--expected-database-project-fingerprint",$project); Assert-True ($two.code -eq 0) "002 failed: $($two.output -join "`n")"
    $twoReport=$two.output[-1]|ConvertFrom-Json; Assert-True ($twoReport.migrations[0].functionLockMode -eq "function_cost_roundtrip") "managed-role function lock fallback was not reported"
    $ledger = (& docker exec $container psql -U postgres -A -t -c "select string_agg(version||'|'||name||'|'||cardinality(statements),',' order by version) from supabase_migrations.schema_migrations;").Trim(); Assert-True ($ledger -eq "20260726171001|community_comments_delete_rls|1,20260726171002|official_post_likes_actor_guard|1") "ledger mismatch: $ledger"
    $guardHash = (& docker exec $container psql -U postgres -A -t -c "select md5(pg_get_functiondef('public.quata_guard_official_post_likes()'::regprocedure));").Trim()
    Assert-True ($guardHash -eq "c9505e6d5b5fbb818c465cf84a3ebf56") "production-like guard fingerprint mismatch: $guardHash"
    $rollback2Dry=Exec @("--action","dry-run"); $rollback2Fp=((($rollback2Dry.output[-1]|ConvertFrom-Json).migrations|Where-Object version -eq "20260726171002").preconditionSha256)
    $rollback2Job=Start-Job -ScriptBlock { param($file,$fp) $env:QUATA_SERIAL_EXECUTOR_TEST_HOLD_AFTER_LOCK_MS='1800'; & node $file --action rollback-002 --expected-precondition-sha256 $fp; if($LASTEXITCODE -ne 0){throw "rollback-002 executor failed"} } -ArgumentList (Join-Path $PSScriptRoot "security-release-serial-executor.mjs"),$rollback2Fp
    Start-Sleep -Milliseconds 350
    $callTimer=[Diagnostics.Stopwatch]::StartNew(); docker exec $container psql -U postgres -X -v ON_ERROR_STOP=1 -Atqc "select public.quata_current_profile_is_admin();" | Out-Null; $callTimer.Stop(); Assert-True ($LASTEXITCODE -eq 0 -and $callTimer.ElapsedMilliseconds -lt 1000) "function calls were blocked by catalog tuple lock"
    $functionWriter=Start-Job -ScriptBlock { param($name,$sql) ("begin;`n" + $sql + "`nrollback;") | docker exec -i $name psql -U postgres -X -v ON_ERROR_STOP=1; if($LASTEXITCODE -ne 0){throw "function writer failed"} } -ArgumentList $container,$guardDefinition
    Start-Sleep -Milliseconds 300; Assert-True ($functionWriter.State -eq "Running") "external function writer was not blocked by selective pg_proc lock"
    Wait-Job $rollback2Job | Out-Null; $rollback2StateJob=$rollback2Job.State; $rollback2Output=Receive-Job $rollback2Job; Remove-Job $rollback2Job; Assert-True ($rollback2StateJob -eq "Completed") "rollback-002 failed: $($rollback2Output -join "`n")"
    Wait-Job $functionWriter | Out-Null; $functionWriterState=$functionWriter.State
    $oldErrorPreference=$ErrorActionPreference; $ErrorActionPreference='Continue'; $functionWriterOutput=Receive-Job $functionWriter 2>&1; $ErrorActionPreference=$oldErrorPreference; Remove-Job $functionWriter
    Assert-True ($functionWriterState -eq "Failed" -and ($functionWriterOutput -join "`n") -match "tuple concurrently updated") "external function writer was not rejected after the guarded commit"
    $rollback2State=(& docker exec $container psql -U postgres -A -t -c "select (select relrowsecurity from pg_class where oid='public.official_post_likes'::regclass), to_regprocedure('public.quata_official_like_delete_allowed(uuid)') is null;").Trim(); Assert-True ($rollback2State -eq "f|t") "rollback-002 did not restore the production-like fixture: $rollback2State"
    $rollback2AgainDry=Exec @("--action","dry-run"); $rollback2AgainFp=((($rollback2AgainDry.output[-1]|ConvertFrom-Json).migrations|Where-Object version -eq "20260726171002").preconditionSha256)
    $rollback2Again=Exec @("--action","rollback-002","--expected-precondition-sha256",$rollback2AgainFp); Assert-True ($rollback2Again.code -ne 0 -and ($rollback2Again.output -join "`n") -match "function_lock_missing") "rollback-002 drift/idempotency was accepted"
    $rollbackDry=Exec @("--action","dry-run"); $rollbackFp=((($rollbackDry.output[-1]|ConvertFrom-Json).migrations|Where-Object version -eq "20260726171001").preconditionSha256)
    $rollback=Exec @("--action","rollback-001","--expected-precondition-sha256",$rollbackFp); Assert-True ($rollback.code -eq 0) "rollback 001 failed: $($rollback.output -join "`n")"
    $preserved=(& docker exec $container psql -U postgres -A -t -c "select count(*) from supabase_migrations.schema_migrations where version='20260726171001';").Trim(); Assert-True ($preserved -eq "1") "rollback repaired/deleted ledger"
    $rollbackAgain=Exec @("--action","rollback-001","--expected-precondition-sha256",$rollbackFp); Assert-True ($rollbackAgain.code -ne 0) "rollback drift/idempotency was accepted"
    $finalCosts = (& docker exec $container psql -U postgres -A -t -c "select string_agg(procost::text,',' order by proname) from pg_proc where proname in ('quata_chat_auth_profile_id','quata_current_profile_id','quata_current_profile_is_admin','quata_current_role_is_service','quata_guard_official_post_likes');").Trim(); Assert-True ($finalCosts -eq "100,100,100,100,100") "successful executor changed final function COST: $finalCosts"
    Write-Output "Serial executor PostgreSQL 17 test passed: hash rejection, rollback atomicity, table/function races, exact ledger, ordering and idempotency."
} finally { Remove-Item Env:QUATA_SERIAL_EXECUTOR_TEST_FORCE_FUNCTION_DDL_LOCK -ErrorAction SilentlyContinue; $env:NODE_PATH=$oldNodePath; docker rm -f $container *> $null; if(Test-Path $temp){Remove-Item $temp -Recurse -Force}; Get-ChildItem -LiteralPath (Join-Path $root "build-reports/security-release") -Filter "serial-executor-disposable-*.json" -ErrorAction SilentlyContinue | Remove-Item -Force }
