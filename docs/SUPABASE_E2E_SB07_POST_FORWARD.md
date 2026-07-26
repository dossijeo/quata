# SB-07 post-forward

`run-supabase-e2e-sb07-post-forward.ps1` is a production-safe, opt-in gate for RLS-001 after its forward migration. It never executes DDL, migrations, policy changes, RPCs, or business-row reads beyond its UUID fixtures.

It creates three uniquely marked temporary profiles through TLS PostgreSQL solely to provision and clean fixtures; all actor operations use the public key, the deployed auth bridge, JWTs and PostgREST. The report contains only hashes of fixture identifiers.

Permitted direct DML is restricted to these fixture UUIDs: `community_profiles`,
their `web_client_sessions`, one wall/post and their comments, plus `auth.users`
only after an exact one-to-one `community_profiles.auth_user_id` mapping has been
read back from PostgreSQL. The runner never trusts an Auth/bridge response for
deletion. If the profile mapping is incomplete it performs a second exact lookup
using the deterministic temporary fixture emails. If that fails too, it deletes
only dependent fixture content, preserves profiles and Auth rows recoverably,
fails with `residual_pending_auth_mapping`, and writes a local mode-600 recipe
(fixture UUIDs and emails only; no password, token or chat content) to the
mandatory `-RecoveryFile`, outside the sanitised report.

Authentication is preflighted while all profiles are active. In full mode the temporary admin alone is switched to `deactivated` immediately before the inactive-admin assertion, then reactivated for the permitted-admin assertion.

```powershell
$env:QUATA_SB07_PRODUCTION_GATE_APPROVED = 'approved_temporary_fixture_only'
.\scripts\run-supabase-e2e-sb07-post-forward.ps1 -Mode preflight-auth -DbUrlFile C:securedb-url.txt -TlsCaFile C:secureca.pem -RecoveryFile C:custodysb07-recovery.json

$env:QUATA_SB07_PRODUCTION_GATE_ALLOW_MUTATION = 'approved_public_postgrest_mutations'
.\scripts\run-supabase-e2e-sb07-post-forward.ps1 -Mode full -DbUrlFile C:securedb-url.txt -TlsCaFile C:secureca.pem -RecoveryFile C:custodysb07-recovery.json
```

Full mode verifies anonymous response shape, own 201 DTO insert, 42501 spoof and update rejection, outsider zero-delete with intact row, owner deletion, inactive admin denial with intact row, active admin deletion, and a `finally` hard cleanup verified to zero rows in fixtures/auth.
