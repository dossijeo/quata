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
create function public.quata_guard_official_post_likes() returns trigger language plpgsql security definer as 'begin return new; end';
create trigger quata_guard_official_post_likes_trg before insert or delete on public.official_post_likes for each row execute function public.quata_guard_official_post_likes();
grant select, insert, delete on public.official_post_likes to anon, authenticated;
grant select, insert, delete, update on public.community_comments to anon, authenticated;
create policy "public delete comments" on public.community_comments for delete to public using (true);
create policy "public insert comments" on public.community_comments for insert to public with check (true);
create policy "public update comments" on public.community_comments for update to public using (true) with check (true);
"@
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
    $outputRelative = "build-reports/security-release/serial-executor-disposable-$([guid]::NewGuid().ToString('N')).json"
    $dry = Exec @("--action", "dry-run", "--out", $outputRelative); Assert-True ($dry.code -eq 0) "dry-run failed"; Assert-True (Test-Path (Join-Path $root $outputRelative)) "Windows output path was rejected or not written"; $fp1 = (($dry.output[-1] | ConvertFrom-Json).migrations | Where-Object version -eq "20260726171001").preconditionSha256
    $failed = Exec @("--action", "apply-001", "--expected-precondition-sha256", $fp1) @{QUATA_SERIAL_EXECUTOR_TEST_FAIL_BEFORE_LEDGER="1"}; Assert-True ($failed.code -ne 0) "injected rollback failure passed"
    $atomic = (& docker exec $container psql -U postgres -A -t -c "select (select count(*) from supabase_migrations.schema_migrations), (select relrowsecurity from pg_class where oid='public.community_comments'::regclass);").Trim(); Assert-True ($atomic -eq "0|f") "migration and ledger were not atomic: $atomic"
    $job = Start-Job -ScriptBlock { param($file,$fp) $env:QUATA_SERIAL_EXECUTOR_TEST_HOLD_LOCK_MS='1800'; & node $file --action apply-001 --expected-precondition-sha256 $fp; exit $LASTEXITCODE } -ArgumentList (Join-Path $PSScriptRoot "security-release-serial-executor.mjs"),$fp1
    Start-Sleep -Milliseconds 350; $rival = Exec @("--action", "apply-001", "--expected-precondition-sha256", $fp1); Assert-True ($rival.code -ne 0 -and ($rival.output -join "`n") -match "lock_unavailable") "advisory lock allowed concurrent release"
    Wait-Job $job | Out-Null; $state = $job.State; $jobOut=Receive-Job $job; Remove-Job $job; Assert-True ($state -eq "Completed") "lock owner failed: $($jobOut -join "`n")"
    $l1 = (& docker exec $container psql -U postgres -A -t -c "select version||'|'||name||'|'||cardinality(statements) from supabase_migrations.schema_migrations;").Trim(); Assert-True ($l1 -eq "20260726171001|community_comments_delete_rls|1") "001 ledger not exact: $l1"
    $again = Exec @("--action", "apply-001", "--expected-precondition-sha256", $fp1); Assert-True ($again.code -ne 0 -and ($again.output -join "`n") -match "duplicate_ledger") "duplicate was accepted"
    $dry2=Exec @("--action", "dry-run"); $fp2=((($dry2.output[-1]|ConvertFrom-Json).migrations|Where-Object version -eq "20260726171002").preconditionSha256); $gate=Join-Path $temp "gates.json"; $shaA=('a'*64); $project=('d'*64); @{schemaVersion=1;releaseCommit=('b'*40);snapshotFingerprint=('c'*64);databaseProjectFingerprint=$project;generatedAt=(Get-Date).ToUniversalTime().ToString('o');expiresAt=(Get-Date).AddHours(1).ToUniversalTime().ToString('o');migration="20260726171001";status="passed";preconditionSha256=$fp1;postflight=@{status="passed";sha256=$shaA};reports=@{dbReleaseSafety=@{status="passed";sha256=$shaA;databaseProjectFingerprint=$project};backendCompatibility=@{status="passed";sha256=$shaA;databaseProjectFingerprint=$project};sb07=@{status="passed";sha256=$shaA;databaseProjectFingerprint=$project}}}|ConvertTo-Json -Depth 4|Set-Content $gate
    $gateHash=(Get-FileHash -LiteralPath $gate -Algorithm SHA256).Hash.ToLowerInvariant()
    $two=Exec @("--action","apply-002","--expected-precondition-sha256",$fp2,"--gate-evidence",$gate,"--expected-gate-evidence-sha256",$gateHash,"--expected-release-commit",('b'*40),"--expected-snapshot-fingerprint",('c'*64)); Assert-True ($two.code -eq 0) "002 failed: $($two.output -join "`n")"
    $ledger = (& docker exec $container psql -U postgres -A -t -c "select string_agg(version||'|'||name||'|'||cardinality(statements),',' order by version) from supabase_migrations.schema_migrations;").Trim(); Assert-True ($ledger -eq "20260726171001|community_comments_delete_rls|1,20260726171002|official_post_likes_actor_guard|1") "ledger mismatch: $ledger"
    $rollbackDry=Exec @("--action","dry-run"); $rollbackFp=((($rollbackDry.output[-1]|ConvertFrom-Json).migrations|Where-Object version -eq "20260726171001").preconditionSha256)
    $rollback=Exec @("--action","rollback-001","--expected-precondition-sha256",$rollbackFp); Assert-True ($rollback.code -eq 0) "rollback 001 failed: $($rollback.output -join "`n")"
    $preserved=(& docker exec $container psql -U postgres -A -t -c "select count(*) from supabase_migrations.schema_migrations where version='20260726171001';").Trim(); Assert-True ($preserved -eq "1") "rollback repaired/deleted ledger"
    $rollbackAgain=Exec @("--action","rollback-001","--expected-precondition-sha256",$rollbackFp); Assert-True ($rollbackAgain.code -ne 0) "rollback drift/idempotency was accepted"
    Write-Output "Serial executor PostgreSQL 17 test passed: hash rejection, rollback atomicity, advisory lock, exact ledger, ordering and idempotency."
} finally { $env:NODE_PATH=$oldNodePath; docker rm -f $container *> $null; if(Test-Path $temp){Remove-Item $temp -Recurse -Force}; Get-ChildItem -LiteralPath (Join-Path $root "build-reports/security-release") -Filter "serial-executor-disposable-*.json" -ErrorAction SilentlyContinue | Remove-Item -Force }
