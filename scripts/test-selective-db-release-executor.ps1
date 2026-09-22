[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$scratch = Join-Path $repo ("build-reports/db-release-safety/selective-executor-test-" + [guid]::NewGuid().ToString("N"))
$container = "quata-selective-release-test-" + [guid]::NewGuid().ToString("N")
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0); $listener.Start()
$port = ([Net.IPEndPoint]$listener.LocalEndpoint).Port; $listener.Stop()
$password = "disposable-selective-release-only"
$oldUrl=$env:SUPABASE_DB_URL; $oldCa=$env:SUPABASE_DB_TLS_CA_FILE; $oldNode=$env:NODE_PATH; $oldMode=$env:QUATA_SELECTIVE_RELEASE_TEST_MODE; $oldCommitHook=$env:QUATA_SELECTIVE_RELEASE_TEST_THROW_AFTER_COMMIT; $oldHoldHook=$env:QUATA_SELECTIVE_RELEASE_TEST_HOLD_RECONCILIATION_LOCK_MS

function Write-Utf8([string]$Path,[string]$Content) {
    New-Item -ItemType Directory -Path (Split-Path $Path -Parent) -Force | Out-Null
    [IO.File]::WriteAllText($Path,$Content,[Text.UTF8Encoding]::new($false))
}
function New-Package([string]$Name,[string]$SecondSql,[bool]$IncludeDependencies=$true) {
    $package = Join-Path $scratch $Name
    $migrationRoot = Join-Path $package "supabase/migrations"
    New-Item -ItemType Directory -Path $migrationRoot -Force | Out-Null
    $specs = @(
        [ordered]@{file="20260628_0001_anchor.sql";version="20260628";role="remote_ledger_anchor";sql="select 1;`n"},
        [ordered]@{file="20260922190000_probe_one.sql";version="20260922190000";role="selected_new_migration";sql="create table public.selective_probe_one(id integer primary key);`n"},
        [ordered]@{file="20260922190100_probe_two.sql";version="20260922190100";role="selected_new_migration";sql=$SecondSql}
    )
    $entries=@()
    foreach($spec in $specs){$path=Join-Path $migrationRoot $spec.file; Write-Utf8 $path $spec.sql; $entries += [ordered]@{file=$spec.file;version=$spec.version;role=$spec.role;sha256=(Get-FileHash -Algorithm SHA256 $path).Hash.ToLowerInvariant()}}
    $dependencies = @()
    if ($IncludeDependencies) {
        $dependencies = @([ordered]@{historicalMigration="20260628_0002_history.sql";evidenceFile="docs/runbooks/migration/evidence/fixture.json";requiredPackageMigrations=@("20260922190000_probe_one.sql","20260922190100_probe_two.sql")})
    }
    $manifest=[ordered]@{schemaVersion=1;sourceCommit=("a"*40);deploymentAuthorized=$false;reconciliationManifestSha256=("b"*64);remoteLedgerAnchors=@("20260628");selectedVersions=@("20260922190000","20260922190100");reconciliationDependencies=$dependencies;migrations=$entries}
    $manifestPath=Join-Path $package "release-manifest.json"; Write-Utf8 $manifestPath (($manifest|ConvertTo-Json -Depth 8)+"`n")
    $authorization=[ordered]@{schemaVersion=1;approved=$true;scope="apply_selected_package";authorizedAt=(Get-Date).ToUniversalTime().ToString("o");sourceCommit=("a"*40);releaseManifestSha256=(Get-FileHash -Algorithm SHA256 $manifestPath).Hash.ToLowerInvariant();databaseProjectFingerprint=$null;selectedVersions=@("20260922190000","20260922190100")}
    Write-Utf8 (Join-Path $package "release-authorization.json") (($authorization|ConvertTo-Json -Depth 4)+"`n")
    return $package
}
function Invoke-Executor([string]$Action,[string]$Package,[switch]$Authorization) {
    $args=@((Join-Path $PSScriptRoot "selective-db-release-executor.mjs"),"--action",$Action,"--package",$Package,"--expected-source-commit",("a"*40),"--out",(Join-Path $scratch "$Action-report.json"))
    if($Authorization){$args+=@("--authorization",(Join-Path $Package "release-authorization.json"))}
    & node @args | Out-Null
    return $LASTEXITCODE
}

try {
    New-Item -ItemType Directory -Path $scratch -Force | Out-Null
    & docker run -d --rm --name $container -e "POSTGRES_PASSWORD=$password" -p "${port}:5432" postgres:17 `
        sh -c "openssl req -new -x509 -days 1 -nodes -subj /CN=localhost -addext subjectAltName=IP:127.0.0.1 -out /tmp/server.crt -keyout /tmp/server.key >/dev/null 2>&1 && chown postgres:postgres /tmp/server.crt /tmp/server.key && chmod 600 /tmp/server.key && exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/tmp/server.crt -c ssl_key_file=/tmp/server.key" | Out-Null
    if($LASTEXITCODE -ne 0){throw "container_start_failed"}
    for($i=0;$i-lt 30;$i++){& docker exec $container pg_isready -U postgres *> $null;if($LASTEXITCODE-eq 0){break};Start-Sleep -Milliseconds 500}
    if($LASTEXITCODE-ne 0){throw "container_not_ready"}
    $ca=Join-Path $scratch "server.crt"; & docker cp "${container}:/tmp/server.crt" $ca *> $null
    $ledger=@'
create schema supabase_migrations;
create table supabase_migrations.schema_migrations(version text primary key, statements text[], name text);
insert into supabase_migrations.schema_migrations values ('20260628','{}','0001_anchor');
'@
    $ledger | docker exec -i $container psql -U postgres -v ON_ERROR_STOP=1 *> $null
    if($LASTEXITCODE-ne 0){throw "ledger_seed_failed"}
    $env:SUPABASE_DB_URL="postgresql://postgres:$password@127.0.0.1:$port/postgres?sslmode=verify-full"
    $env:SUPABASE_DB_TLS_CA_FILE=$ca
    $env:NODE_PATH=Join-Path 'C:\Users\PC\StudioProjects\quata' 'node_modules'
    $env:QUATA_SELECTIVE_RELEASE_TEST_MODE="1"

    $fresh=New-Package "fresh" "create table public.selective_probe_two(id integer primary key);`n" $false
    if((Invoke-Executor "dry-run" $fresh)-ne 0){throw "fresh_release_without_historical_dependencies_failed"}
    $good=New-Package "good" "create table public.selective_probe_two(id integer primary key);`n"
    if((Invoke-Executor "dry-run" $good)-ne 0){throw "dry_run_failed"}
    $targetFingerprint=(Get-Content -Raw (Join-Path $scratch "dry-run-report.json")|ConvertFrom-Json).databaseProjectFingerprint
    foreach($package in @($good)){$path=Join-Path $package "release-authorization.json";$auth=Get-Content -Raw $path|ConvertFrom-Json;$auth.databaseProjectFingerprint=$targetFingerprint;Write-Utf8 $path (($auth|ConvertTo-Json -Depth 4)+"`n")}
    $oldPreference=$ErrorActionPreference;$ErrorActionPreference="Continue"
    $noAuth=Invoke-Executor "apply" $good
    $ErrorActionPreference=$oldPreference
    if($noAuth-eq 0){throw "apply_without_authorization_accepted"}

    $bad=New-Package "bad" "select 1 / 0;`n"
    $badAuthPath=Join-Path $bad "release-authorization.json";$badAuth=Get-Content -Raw $badAuthPath|ConvertFrom-Json;$badAuth.databaseProjectFingerprint=$targetFingerprint;Write-Utf8 $badAuthPath (($badAuth|ConvertTo-Json -Depth 4)+"`n")
    $ErrorActionPreference="Continue";$badApply=Invoke-Executor "apply" $bad -Authorization;$ErrorActionPreference=$oldPreference
    if($badApply-eq 0){throw "failing_package_accepted"}
    $afterFailure=(& docker exec $container psql -U postgres -Atqc "select count(*)||'|'||(to_regclass('public.selective_probe_one') is null)::text from supabase_migrations.schema_migrations;").Trim()
    if($afterFailure-ne "1|true"){throw "failing_package_not_atomic:$afterFailure"}

    $env:QUATA_SELECTIVE_RELEASE_TEST_THROW_AFTER_COMMIT="1"
    $env:QUATA_SELECTIVE_RELEASE_TEST_HOLD_RECONCILIATION_LOCK_MS="1200"
    if((Invoke-Executor "apply" $good -Authorization)-ne 0){throw "authorized_apply_failed"}
    $applyReport=Get-Content -Raw (Join-Path $scratch "apply-report.json")|ConvertFrom-Json
    if($applyReport.commitStatus-ne "confirmed_after_reconnect"){throw "uncertain_commit_not_reconciled:$($applyReport.commitStatus)"}
    if($applyReport.reconciliationLockWaitMs-lt 1000){throw "reconciliation_did_not_wait_for_release_lock:$($applyReport.reconciliationLockWaitMs)"}
    $env:QUATA_SELECTIVE_RELEASE_TEST_THROW_AFTER_COMMIT=$null
    $env:QUATA_SELECTIVE_RELEASE_TEST_HOLD_RECONCILIATION_LOCK_MS=$null
    $final=(& docker exec $container psql -U postgres -Atqc "select count(*)||'|'||(to_regclass('public.selective_probe_one') is not null)::text||'|'||(to_regclass('public.selective_probe_two') is not null)::text from supabase_migrations.schema_migrations;").Trim()
    if($final-ne "3|true|true"){throw "unexpected_final_state:$final"}
    Write-Output "Selective DB release executor test passed: read-only dry-run, authorization gate, atomic rollback and exact apply."
} finally {
    $env:SUPABASE_DB_URL=$oldUrl;$env:SUPABASE_DB_TLS_CA_FILE=$oldCa;$env:NODE_PATH=$oldNode;$env:QUATA_SELECTIVE_RELEASE_TEST_MODE=$oldMode;$env:QUATA_SELECTIVE_RELEASE_TEST_THROW_AFTER_COMMIT=$oldCommitHook;$env:QUATA_SELECTIVE_RELEASE_TEST_HOLD_RECONCILIATION_LOCK_MS=$oldHoldHook
    $id=((@(& docker container ls -aq --filter "name=^/${container}$"))-join "").Trim();if($id){& docker rm -f $container|Out-Null}
    $resolved=[IO.Path]::GetFullPath($scratch);$allowed=[IO.Path]::GetFullPath((Join-Path $repo "build-reports/db-release-safety"));if($resolved.StartsWith($allowed+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)-and(Test-Path $resolved)){Remove-Item -LiteralPath $resolved -Recurse -Force}
}
