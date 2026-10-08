$ErrorActionPreference = "Stop"

$containerName = "quata-auth-global-logout-" + [guid]::NewGuid().ToString("N").Substring(0, 8)
$scratch = Join-Path $env:TEMP $containerName
New-Item -ItemType Directory -Path $scratch -Force | Out-Null
$setupPath = Join-Path $scratch "setup.sql"
$probePath = Join-Path $scratch "probe.sql"

@'
create role anon;
create role authenticated;
create role service_role;
create table public.push_tokens(id uuid default gen_random_uuid(), auth_user_id uuid not null, disabled_at timestamptz, last_error_text text, updated_at timestamptz default now());
create table public.web_client_sessions(id uuid primary key default gen_random_uuid(), auth_user_id uuid not null, revoked_at timestamptz, updated_at timestamptz default now());
create table public.web_push_subscriptions(id uuid default gen_random_uuid(), web_session_id uuid not null, auth_user_id uuid not null, disabled_at timestamptz, last_error_text text, updated_at timestamptz default now());
insert into public.push_tokens(auth_user_id) values
  ('11111111-1111-4111-8111-111111111111'),
  ('11111111-1111-4111-8111-111111111111'),
  ('22222222-2222-4222-8222-222222222222');
insert into public.web_client_sessions(auth_user_id) values
  ('11111111-1111-4111-8111-111111111111'),
  ('11111111-1111-4111-8111-111111111111'),
  ('22222222-2222-4222-8222-222222222222');
insert into public.web_push_subscriptions(web_session_id, auth_user_id)
select id, auth_user_id from public.web_client_sessions;
'@ | Set-Content -LiteralPath $setupPath -Encoding UTF8

@'
set role service_role;
select public.quata_retire_all_device_endpoints('11111111-1111-4111-8111-111111111111'::uuid);
reset role;
select
  (select count(*) from push_tokens where auth_user_id='11111111-1111-4111-8111-111111111111' and disabled_at is null),
  (select count(*) from web_push_subscriptions where auth_user_id='11111111-1111-4111-8111-111111111111' and disabled_at is null),
  (select count(*) from web_client_sessions where auth_user_id='11111111-1111-4111-8111-111111111111' and revoked_at is null),
  (select count(*) from push_tokens where auth_user_id='22222222-2222-4222-8222-222222222222' and disabled_at is null);
'@ | Set-Content -LiteralPath $probePath -Encoding UTF8

docker run --name $containerName -e POSTGRES_PASSWORD=postgres -d postgres:16-alpine | Out-Null
try {
    $ready = $false
    foreach ($attempt in 1..30) {
        docker exec $containerName pg_isready -U postgres 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "postgres_not_ready" }

    docker cp $setupPath "${containerName}:/tmp/setup.sql" | Out-Null
    docker cp "supabase/migrations/20261007090000_auth_global_logout_device_retirement.sql" "${containerName}:/tmp/migration.sql" | Out-Null
    docker cp $probePath "${containerName}:/tmp/probe.sql" | Out-Null
    docker exec $containerName psql -U postgres -v ON_ERROR_STOP=1 -f /tmp/setup.sql | Out-Null
    docker exec $containerName psql -U postgres -v ON_ERROR_STOP=1 -f /tmp/migration.sql | Out-Null
    $probe = docker exec $containerName psql -U postgres -v ON_ERROR_STOP=1 -Atf /tmp/probe.sql
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $denied = docker exec $containerName psql -U postgres -v ON_ERROR_STOP=0 -Atc "set role authenticated; select public.quata_retire_all_device_endpoints('22222222-2222-4222-8222-222222222222'::uuid);" 2>&1
    $ErrorActionPreference = $previousErrorActionPreference

    $probeText = $probe -join "`n"
    $deniedText = $denied -join "`n"
    if ($probeText -notmatch '"native_tokens_disabled"\s*:\s*2') { throw "native_retirement_count_invalid" }
    if ($probeText -notmatch '(?m)^0\|0\|0\|1$') { throw "retirement_postcondition_invalid" }
    if ($deniedText -notmatch 'permission denied') { throw "authenticated_execution_not_denied" }
    Write-Output "AUTH_GLOBAL_LOGOUT_POSTGRES_PASS"
} finally {
    docker rm -f $containerName | Out-Null
}
