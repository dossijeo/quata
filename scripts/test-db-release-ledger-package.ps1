[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("quata-ledger-test-" + [guid]::NewGuid().ToString("N"))
$containerName = "quata-ledger-test-" + [guid]::NewGuid().ToString("N")
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
$listener.Start()
$port = ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
$listener.Stop()
$password = "disposable-ledger-test-only"
$databaseUrl = "postgresql://postgres:$password@127.0.0.1:$port/postgres?sslmode=require"

try {
    $migrationRoot = Join-Path $temporaryRoot "supabase/migrations"
    New-Item -ItemType Directory -Path $migrationRoot -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $repositoryRoot "supabase/config.toml") -Destination (Join-Path $temporaryRoot "supabase/config.toml")

    $anchorFiles = @(
        "20260628_0001_chat_schema.sql",
        "20260723_0001_multidevice_fcm_and_web_push.sql"
    )
    foreach ($anchor in $anchorFiles) {
        Copy-Item -LiteralPath (Join-Path $repositoryRoot "supabase/migrations/$anchor") -Destination (Join-Path $migrationRoot $anchor)
    }
    $selectedFiles = @(
        "20260726171001_probe_one.sql",
        "20260726171002_probe_two.sql",
        "20260726171003_probe_three.sql",
        "20260726171004_probe_four.sql"
    )
    for ($index = 0; $index -lt $selectedFiles.Count; $index++) {
        "create table public.release_probe_$($index + 1)(id integer primary key);" |
            Set-Content -Encoding ascii -LiteralPath (Join-Path $migrationRoot $selectedFiles[$index])
    }

    $local = @($anchorFiles + $selectedFiles | ForEach-Object {
        [ordered]@{
            file = $_
            version = $_.Replace(".sql", "")
        }
    })
    $snapshot = [ordered]@{
        check = "DB-RELEASE-SAFETY"
        phase = "snapshot"
        status = "passed"
        historicalReconciliation = [ordered]@{
            selectivePackageEligible = $true
        }
        migrationHistory = [ordered]@{
            remote = @(
                [ordered]@{ version = "20260628"; name = "0001_chat_schema" },
                [ordered]@{ version = "20260723"; name = "0001_multidevice_fcm_and_web_push" }
            )
            local = $local
        }
    }
    $snapshotPath = Join-Path $temporaryRoot "snapshot.json"
    $snapshot | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 -LiteralPath $snapshotPath
    $packageRelative = "build-reports/db-release-safety/release-package"
    & (Join-Path $PSScriptRoot "prepare-db-release-package.ps1") `
        -RepositoryRoot $temporaryRoot `
        -Snapshot $snapshotPath `
        -OutputDirectory $packageRelative `
        -MigrationFile $selectedFiles
    $packageRoot = Join-Path $temporaryRoot $packageRelative

    & docker run -d --rm --name $containerName `
        -e "POSTGRES_PASSWORD=$password" `
        -p "${port}:5432" `
        postgres:17 `
        sh -c "openssl req -new -x509 -days 1 -nodes -subj /CN=localhost -out /tmp/server.crt -keyout /tmp/server.key >/dev/null 2>&1 && chown postgres:postgres /tmp/server.crt /tmp/server.key && chmod 600 /tmp/server.key && exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/tmp/server.crt -c ssl_key_file=/tmp/server.key" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Unable to start disposable PostgreSQL." }
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        & docker exec $containerName pg_isready -U postgres *> $null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Milliseconds 500
    }
    if ($LASTEXITCODE -ne 0) { throw "Disposable PostgreSQL did not become ready." }

    $ledgerSql = @"
create schema supabase_migrations;
create table supabase_migrations.schema_migrations(
    version text primary key,
    statements text[],
    name text
);
insert into supabase_migrations.schema_migrations(version, statements, name) values
('20260628', '{}', '0001_chat_schema'),
('20260723', '{}', '0001_multidevice_fcm_and_web_push');
"@
    $ledgerSql | docker exec -i $containerName psql -U postgres -v ON_ERROR_STOP=1 *> $null
    if ($LASTEXITCODE -ne 0) { throw "Unable to initialize disposable ledger." }

    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $dryRun = @(& npx --yes supabase@2.109.1 db push --db-url $databaseUrl --workdir $packageRoot --dry-run 2>&1)
    $ErrorActionPreference = $previousErrorAction
    if ($LASTEXITCODE -ne 0) { throw "Selective package dry-run failed.`n$($dryRun -join "`n")" }
    foreach ($selected in $selectedFiles) {
        if (($dryRun -join "`n") -notmatch [regex]::Escape($selected)) {
            throw "Dry-run omitted selected migration: $selected"
        }
    }
    if (($dryRun -join "`n") -match "20260628_0002_chat_rpc") {
        throw "Dry-run attempted historical backlog."
    }

    $ErrorActionPreference = "Continue"
    $pushOutput = @(& npx --yes supabase@2.109.1 db push --db-url $databaseUrl --workdir $packageRoot --yes 2>&1)
    $ErrorActionPreference = $previousErrorAction
    if ($LASTEXITCODE -ne 0) { throw "Selective disposable push failed.`n$($pushOutput -join "`n")" }
    $result = (& docker exec $containerName psql -U postgres -A -t -c `
        "select (select count(*) from supabase_migrations.schema_migrations), (select count(*) from pg_tables where schemaname='public' and tablename like 'release_probe_%');").Trim()
    if ($result -ne "6|4") { throw "Unexpected disposable ledger result: $result" }

    $ErrorActionPreference = "Continue"
    $secondDryRun = @(& npx --yes supabase@2.109.1 db push --db-url $databaseUrl --workdir $packageRoot --dry-run 2>&1)
    $ErrorActionPreference = $previousErrorAction
    if ($LASTEXITCODE -ne 0 -or ($secondDryRun -join "`n") -notmatch "Remote database is up to date") {
        throw "Second dry-run did not prove idempotent ledger state."
    }
    Write-Output "Disposable ledger package test passed: 2 anchors + 4 selected; backlog excluded; second dry-run clean."
}
finally {
    $containerId = ((@(& docker container ls -aq --filter "name=^/${containerName}$")) -join "").Trim()
    if (-not [string]::IsNullOrWhiteSpace($containerId)) {
        & docker rm -f $containerName | Out-Null
    }
    $resolvedTemporary = [System.IO.Path]::GetFullPath($temporaryRoot)
    $systemTemporary = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTemporary.StartsWith($systemTemporary, [System.StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path $resolvedTemporary -Leaf).StartsWith("quata-ledger-test-")) {
        if (Test-Path -LiteralPath $resolvedTemporary) {
            Remove-Item -LiteralPath $resolvedTemporary -Recurse -Force
        }
    } else {
        throw "Refusing to remove unexpected temporary path."
    }
}
