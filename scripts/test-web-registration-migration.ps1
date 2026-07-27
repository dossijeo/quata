$ErrorActionPreference = 'Stop'
$container = "quata-registration-db-test-$PID"
try {
  docker run --name $container -e POSTGRES_PASSWORD=test -d postgres:17-alpine | Out-Null
  for ($i=0; $i -lt 30; $i++) {
    docker exec $container pg_isready -U postgres 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep -Milliseconds 500
  }
  @'
create role anon; create role authenticated; create role service_role;
create schema auth;
create table auth.users(id uuid primary key default gen_random_uuid(),email text,raw_user_meta_data jsonb);
create table public.community_profiles(id uuid primary key default gen_random_uuid(),
 phone_e164 text,country_code text,phone_local text);
create function public.quata_guard_profile_roles() returns trigger language plpgsql as $$begin return new;end$$;
create trigger quata_guard_profile_roles_trg before insert or update on public.community_profiles
for each row execute function public.quata_guard_profile_roles();
'@ | docker exec -i $container psql -v ON_ERROR_STOP=1 -U postgres
  Get-Content "$PSScriptRoot/../supabase/migrations/20260726171004_web_registration_contract.sql" -Raw |
    docker exec -i $container psql -v ON_ERROR_STOP=1 -U postgres
  $sql = @'
select public.quata_claim_web_registration(repeat('a',64),repeat('b',64),repeat('c',64),repeat('d',64),repeat('e',64));
select public.quata_claim_web_registration(repeat('a',64),repeat('b',64),repeat('c',64),repeat('d',64),repeat('e',64));
'@
  $output = $sql | docker exec -i $container psql -At -v ON_ERROR_STOP=1 -U postgres
  if (($output | Select-String '"kind": "new"').Count -ne 1) { throw 'new_claim_count_failed' }
  if (($output | Select-String '"kind": "busy"').Count -ne 1) { throw 'concurrent_lease_failed' }
  $cleanup = @'
update public.web_registration_requests set status='cleanup_required' where request_key_hash=repeat('a',64);
select public.quata_claim_web_registration_cleanup('11111111-1111-1111-1111-111111111111','db-test');
select public.quata_claim_web_registration_cleanup('22222222-2222-2222-2222-222222222222','db-test');
select public.quata_finish_web_registration_cleanup(id,'11111111-1111-1111-1111-111111111111','db-test',true,'{"row_counts":{"profile":0}}')
from public.web_registration_requests where request_key_hash=repeat('a',64);
select status from public.web_registration_requests where request_key_hash=repeat('a',64);
select count(*) from public.web_registration_cleanup_events;
'@ | docker exec -i $container psql -At -v ON_ERROR_STOP=1 -U postgres
  if (($cleanup | Select-String '"kind": "claimed"').Count -ne 1) { throw 'cleanup_claim_failed' }
  if (($cleanup | Select-String '"kind": "empty"').Count -ne 1) { throw 'cleanup_concurrency_failed' }
  if (($cleanup | Select-String '^cleanup_completed$').Count -ne 1) { throw 'cleanup_ledger_not_preserved' }
  if (($cleanup | Select-String '^2$').Count -ne 1) { throw 'cleanup_audit_events_failed' }
} finally {
  docker rm -f $container 2>$null | Out-Null
}
