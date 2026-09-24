[CmdletBinding()]
param(
    [string]$PostgresImage = "postgres:16-alpine"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$containerName = "quata-follows-integrity-$([Guid]::NewGuid().ToString('N').Substring(0, 10))"
$rollbackStdout = Join-Path $env:TEMP "$containerName-rollback.out"
$rollbackStderr = Join-Path $env:TEMP "$containerName-rollback.err"
$mutationStdout = Join-Path $env:TEMP "$containerName-mutation.out"
$mutationStderr = Join-Path $env:TEMP "$containerName-mutation.err"
$mutationSqlFile = Join-Path $env:TEMP "$containerName-mutation.sql"

try {
    docker run `
        --detach `
        --name $containerName `
        --env POSTGRES_PASSWORD=quata-test-only `
        --volume "${repoRoot}:/workspace:ro" `
        $PostgresImage | Out-Null

    $ready = $false
    foreach ($attempt in 1..30) {
        docker exec $containerName pg_isready -U postgres -d postgres *> $null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) {
        throw "PostgreSQL did not become ready."
    }

    docker exec $containerName `
        psql -U postgres -d postgres -X `
        -v KEEP_FIXTURES=1 `
        -f /workspace/scripts/sql/community-profile-follows-integrity.test.sql
    if ($LASTEXITCODE -ne 0) {
        throw "The isolated follows integrity test failed."
    }

    docker exec $containerName `
        psql -U postgres -d postgres -X -v ON_ERROR_STOP=1 -c `
        "create function public.test_delay_follow_counter_restore() returns trigger language plpgsql as `$`$ begin perform pg_sleep(0.4); return new; end; `$`$; create trigger test_delay_follow_counter_restore_trg before update of followers_count, following_count on public.community_profiles for each row execute function public.test_delay_follow_counter_restore();"
    if ($LASTEXITCODE -ne 0) {
        throw "Could not install the rollback concurrency probe."
    }

    $rollbackProcess = Start-Process -FilePath "docker" -ArgumentList @(
        "exec", $containerName,
        "psql", "-U", "postgres", "-d", "postgres", "-X",
        "-v", "ON_ERROR_STOP=1",
        "-f", "/workspace/supabase/templates/community_profile_follow_counter_reconciliation.rollback.sql.template"
    ) -PassThru -WindowStyle Hidden -RedirectStandardOutput $rollbackStdout -RedirectStandardError $rollbackStderr

    $rollbackLocksReady = $false
    foreach ($attempt in 1..100) {
        $rollbackProcess.Refresh()
        if ($rollbackProcess.HasExited) {
            $rollbackError = Get-Content $rollbackStderr -Raw -ErrorAction SilentlyContinue
            throw "Rollback exited before the exclusion locks were observable: $rollbackError"
        }
        $lockCount = docker exec $containerName `
            psql -U postgres -d postgres -X -Atc `
            "select count(distinct relation) from pg_locks where granted and mode = 'ShareRowExclusiveLock' and relation in ('public.community_profile_follows'::regclass, 'public.community_profiles'::regclass);"
        if ($LASTEXITCODE -eq 0 -and [int]$lockCount -eq 2) {
            $rollbackLocksReady = $true
            break
        }
        Start-Sleep -Milliseconds 100
    }
    if (-not $rollbackLocksReady) {
        throw "Rollback did not acquire both exclusion locks."
    }

    $mutationSql = @"
begin;
set local role service_role;
insert into public.community_profile_follows (
    follower_profile_id,
    followed_profile_id
) values (
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
);
commit;
"@
    [System.IO.File]::WriteAllText(
        $mutationSqlFile,
        $mutationSql,
        [System.Text.UTF8Encoding]::new($false)
    )
    docker cp $mutationSqlFile "${containerName}:/tmp/follow-mutation.sql" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not stage the rollback concurrency mutation."
    }

    $mutationStartedAt = [DateTime]::UtcNow
    $mutationProcess = Start-Process -FilePath "docker" -ArgumentList @(
        "exec", $containerName,
        "psql", "-U", "postgres", "-d", "postgres", "-X",
        "-v", "ON_ERROR_STOP=1", "-f", "/tmp/follow-mutation.sql"
    ) -PassThru -WindowStyle Hidden -RedirectStandardOutput $mutationStdout -RedirectStandardError $mutationStderr

    Start-Sleep -Milliseconds 250
    $mutationProcess.Refresh()
    if ($mutationProcess.HasExited) {
        $mutationError = Get-Content $mutationStderr -Raw -ErrorAction SilentlyContinue
        throw "Follow mutation was not blocked by rollback: $mutationError"
    }

    $rollbackProcess | Wait-Process -Timeout 30
    $rollbackProcess.WaitForExit()
    $mutationProcess | Wait-Process -Timeout 30
    $mutationProcess.WaitForExit()
    if (([DateTime]::UtcNow - $mutationStartedAt).TotalMilliseconds -lt 250) {
        throw "The follow mutation did not wait for the rollback transaction."
    }

    $rollbackState = docker exec $containerName `
        psql -U postgres -d postgres -X -Atc `
        "select concat_ws(':', count(*), to_regclass('public.quata_follow_count_reconciliation_batches') is null, to_regprocedure('public.quata_sync_profile_follow_counts()') is null) from public.community_profile_follows where follower_profile_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb' and followed_profile_id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';"
    if ($LASTEXITCODE -ne 0 -or $rollbackState.Trim() -ne "1:t:t") {
        $rollbackError = Get-Content $rollbackStderr -Raw -ErrorAction SilentlyContinue
        $rollbackOutput = Get-Content $rollbackStdout -Raw -ErrorAction SilentlyContinue
        $mutationError = Get-Content $mutationStderr -Raw -ErrorAction SilentlyContinue
        $mutationOutput = Get-Content $mutationStdout -Raw -ErrorAction SilentlyContinue
        throw "Rollback or blocked mutation did not complete cleanly: state=$rollbackState rollback=$rollbackError $rollbackOutput mutation=$mutationError $mutationOutput"
    }

    docker exec $containerName `
        psql -U postgres -d postgres -X -v ON_ERROR_STOP=1 -c `
        "drop trigger test_delay_follow_counter_restore_trg on public.community_profiles; drop function public.test_delay_follow_counter_restore(); delete from public.community_profile_follows where follower_profile_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb' and followed_profile_id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';"
    if ($LASTEXITCODE -ne 0) {
        throw "Could not clean the rollback concurrency probe."
    }

    docker exec $containerName `
        psql -U postgres -d postgres -X -v ON_ERROR_STOP=1 `
        -f /workspace/supabase/templates/community_profile_follow_counter_reconciliation.sql.template
    if ($LASTEXITCODE -ne 0) {
        throw "Could not restore the reconciled fixture after the rollback concurrency probe."
    }
    Write-Output "COMMUNITY_PROFILE_FOLLOWS_ROLLBACK_CONCURRENCY_TEST_OK"

    docker exec $containerName `
        psql -U postgres -d postgres -X `
        -f /workspace/scripts/sql/community-profile-follows-concurrency.test.sql
    if ($LASTEXITCODE -ne 0) {
        throw "The concurrent follows producer test failed."
    }
}
finally {
    docker rm --force $containerName *> $null
    Remove-Item -LiteralPath $rollbackStdout, $rollbackStderr, $mutationStdout, $mutationStderr, $mutationSqlFile `
        -Force -ErrorAction SilentlyContinue
}
