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
    $fixtureReconciliationPath = Join-Path $temporaryRoot "supabase/migration-reconciliation.json"
    '{"schemaVersion":1,"policy":"disposable fixture","migrations":[]}' |
        Set-Content -Encoding ascii -NoNewline -LiteralPath $fixtureReconciliationPath
    $fixtureReconciliationSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $fixtureReconciliationPath).Hash.ToLowerInvariant()
    $fixtureEvidencePath = Join-Path $temporaryRoot "docs/runbooks/migration/evidence/test-chat-rpc.json"
    New-Item -ItemType Directory -Path (Split-Path $fixtureEvidencePath -Parent) -Force | Out-Null
    '{"fixture":true}' | Set-Content -Encoding ascii -NoNewline -LiteralPath $fixtureEvidencePath
    $fixtureEvidenceSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $fixtureEvidencePath).Hash.ToLowerInvariant()

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
            sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $migrationRoot $_)).Hash.ToLowerInvariant()
        }
    })
    $snapshot = [ordered]@{
        check = "DB-RELEASE-SAFETY"
        phase = "snapshot"
        status = "passed"
        historicalReconciliation = [ordered]@{
            selectivePackageEligible = $true
            manifestSha256 = $fixtureReconciliationSha256
            decisions = @(
                [ordered]@{
                    file = "20260628_0002_chat_rpc.sql"
                    classification = "approved_ledger_reconciliation"
                    approvalScope = "selective_package_preparation"
                    evidenceFile = "docs/runbooks/migration/evidence/test-chat-rpc.json"
                    evidenceSha256 = $fixtureEvidenceSha256
                    requiredPackageMigrations = @($selectedFiles[0], $selectedFiles[1])
                    semanticEvidenceComplete = $false
                    approvedLedgerReconciliationComplete = $true
                    releaseDecisionComplete = $true
                    reconciliationProblems = @()
                }
            )
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
    $staleSnapshot = $snapshot | ConvertTo-Json -Depth 6 | ConvertFrom-Json
    $staleSnapshot.historicalReconciliation.manifestSha256 = "0" * 64
    $staleSnapshotPath = Join-Path $temporaryRoot "stale-snapshot.json"
    $staleSnapshot | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 -LiteralPath $staleSnapshotPath
    $stalePackageRelative = "build-reports/db-release-safety/release-package-stale"
    $staleSnapshotRejected = $false
    try {
        & (Join-Path $PSScriptRoot "prepare-db-release-package.ps1") `
            -RepositoryRoot $temporaryRoot `
            -Snapshot $staleSnapshotPath `
            -OutputDirectory $stalePackageRelative `
            -MigrationFile $selectedFiles
    } catch {
        $staleSnapshotRejected = $_.Exception.Message -match "manifest hash does not match"
    }
    if (-not $staleSnapshotRejected) {
        throw "Package preparation accepted a stale reconciliation snapshot."
    }
    if (Test-Path -LiteralPath (Join-Path $temporaryRoot $stalePackageRelative)) {
        throw "Rejected stale snapshot left an output directory."
    }

    $driftedMigrationPath = Join-Path $migrationRoot $selectedFiles[0]
    Add-Content -Encoding ascii -LiteralPath $driftedMigrationPath -Value "-- drift after snapshot"
    $migrationDriftRejected = $false
    try {
        & (Join-Path $PSScriptRoot "prepare-db-release-package.ps1") `
            -RepositoryRoot $temporaryRoot `
            -Snapshot $snapshotPath `
            -OutputDirectory "build-reports/db-release-safety/release-package-migration-drift" `
            -MigrationFile $selectedFiles
    } catch {
        $migrationDriftRejected = $_.Exception.Message -match "hash does not match current migration"
    }
    if (-not $migrationDriftRejected) {
        throw "Package preparation accepted migration drift after the snapshot."
    }
    "create table public.release_probe_1(id integer primary key);" |
        Set-Content -Encoding ascii -LiteralPath $driftedMigrationPath

    Add-Content -Encoding ascii -LiteralPath $fixtureEvidencePath -Value " "
    $evidenceDriftRejected = $false
    try {
        & (Join-Path $PSScriptRoot "prepare-db-release-package.ps1") `
            -RepositoryRoot $temporaryRoot `
            -Snapshot $snapshotPath `
            -OutputDirectory "build-reports/db-release-safety/release-package-evidence-drift" `
            -MigrationFile $selectedFiles
    } catch {
        $evidenceDriftRejected = $_.Exception.Message -match "hash does not match current evidence"
    }
    if (-not $evidenceDriftRejected) {
        throw "Package preparation accepted evidence drift after the snapshot."
    }
    '{"fixture":true}' | Set-Content -Encoding ascii -NoNewline -LiteralPath $fixtureEvidencePath

    $incompletePackageRelative = "build-reports/db-release-safety/release-package-incomplete"
    $omissionRejected = $false
    try {
        & (Join-Path $PSScriptRoot "prepare-db-release-package.ps1") `
            -RepositoryRoot $temporaryRoot `
            -Snapshot $snapshotPath `
            -OutputDirectory $incompletePackageRelative `
            -MigrationFile @($selectedFiles[0])
    } catch {
        $omissionRejected = $_.Exception.Message -match "omits required reconciliation migrations"
    }
    if (-not $omissionRejected) {
        throw "Package preparation did not fail closed when an approved reconciliation dependency was omitted."
    }
    if (Test-Path -LiteralPath (Join-Path $temporaryRoot $incompletePackageRelative)) {
        throw "Rejected incomplete package left an output directory."
    }

    $packageRelative = "build-reports/db-release-safety/release-package"
    & (Join-Path $PSScriptRoot "prepare-db-release-package.ps1") `
        -RepositoryRoot $temporaryRoot `
        -Snapshot $snapshotPath `
        -OutputDirectory $packageRelative `
        -MigrationFile $selectedFiles
    $packageRoot = Join-Path $temporaryRoot $packageRelative
    $packageManifest = Get-Content -Raw -Encoding utf8 -LiteralPath (Join-Path $packageRoot "release-manifest.json") | ConvertFrom-Json
    if (@($packageManifest.reconciliationDependencies).Count -ne 1 -or
        @($packageManifest.reconciliationDependencies[0].requiredPackageMigrations).Count -ne 2) {
        throw "Release manifest omitted approved reconciliation dependencies."
    }

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
    Write-Output "Disposable ledger package test passed: stale snapshot, SQL/evidence drift and required omission rejected; 2 anchors + 4 selected; backlog excluded; second dry-run clean."
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
