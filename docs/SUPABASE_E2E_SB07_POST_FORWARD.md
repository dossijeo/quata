# SB-07 post-forward

`run-supabase-e2e-sb07-post-forward.ps1` is a production-safe, opt-in gate for RLS-001 after its forward migration. It never executes DDL, migrations, policy changes, RPCs, or business-row reads beyond its UUID fixtures.

It creates three uniquely marked temporary profiles through TLS PostgreSQL solely to provision and clean fixtures; all actor operations use the public key, the deployed auth bridge, JWTs and PostgREST. The report contains only hashes of fixture identifiers.

Authentication is preflighted while all profiles are active. In full mode the temporary admin alone is switched to `deactivated` immediately before the inactive-admin assertion, then reactivated for the permitted-admin assertion.

```powershell
$env:QUATA_SB07_PRODUCTION_GATE_APPROVED = 'approved_temporary_fixture_only'
.\scripts\run-supabase-e2e-sb07-post-forward.ps1 -Mode preflight-auth -DbUrlFile C:securedb-url.txt -TlsCaFile C:secureca.pem

$env:QUATA_SB07_PRODUCTION_GATE_ALLOW_MUTATION = 'approved_public_postgrest_mutations'
.\scripts\run-supabase-e2e-sb07-post-forward.ps1 -Mode full -DbUrlFile C:securedb-url.txt -TlsCaFile C:secureca.pem
```

Full mode verifies anonymous response shape, own 201 DTO insert, 42501 spoof and update rejection, outsider zero-delete with intact row, owner deletion, inactive admin denial with intact row, active admin deletion, and a `finally` hard cleanup verified to zero rows in fixtures/auth.
