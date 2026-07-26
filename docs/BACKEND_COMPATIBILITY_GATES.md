# Backend compatibility gates

These gates protect the Android 1.0.4 client and the public Web surfaces while backend fixes are
developed. They do not change product code, deploy functions, modify RLS, or write business data.

## Coverage

- Anonymous PostgREST reads used by Feed, Official and Communities, including the exact columns
  decoded by Android/Web.
- The PostgreSQL catalogue for Android tables and every RPC referenced by
  `SupabaseCommunityApi.kt`. Catalogue queries run inside a read-only transaction.
- Credential-free Web navigation for `#feed`, `#official` and `#communities`. The runner injects
  only the public URL/publishable key into a local distribution, verifies the Compose route shell,
  and fails if any observed backend request uses a bearer token. The current Web PostgREST client
  deliberately does not issue remote reads without an authenticated session; anonymous API
  compatibility is therefore asserted separately by the public GET gate.
- Android API-37 UI smoke for Feed, Chat, Official, Communities and Profile. `adb install -r`
  preserves the authenticated session and app data. This runner does not intercept encrypted app
  traffic: it proves launch, route semantics and crash/ANR safety, not the absence of normal
  startup/background synchronization performed by the installed client.

No test account or fixture is created. Browser profiles and temporary Node dependencies are
deleted in `finally` blocks. The Android runner removes only its own UI hierarchy files and must be
paired with the read-only API/catalogue gates when evaluating a backend correction.

## Required environment

Public checks:

```text
QUATA_SUPABASE_URL
QUATA_SUPABASE_PUBLISHABLE_KEY
```

Catalogue checks:

```text
SUPABASE_DB_URL
SUPABASE_DB_TLS_CA_FILE
```

`SUPABASE_DB_TLS_CA_PEM` can replace the CA file in CI. Exactly one CA source must be configured.
Reports never contain keys, tokens, connection strings, user data, or certificate contents.

## Pre/post workflow

Generate the pre-change baseline:

```powershell
.\scripts\run-backend-compatibility-gates.ps1 `
  -DbUrlFile C:\Users\PC\.quata-supabase-db-url.txt `
  -TlsCaFile C:\Users\PC\.quata-supabase-pooler-ca.pem `
  -WebDistribution C:\path\to\web\build\dist\wasmJs\productionExecutable `
  -AndroidApk C:\path\to\app\build\outputs\apk\debug\app-debug.apk
```

After a backend correction, run the same command with:

```powershell
-Baseline build-reports/backend-compatibility/contracts.json
```

Keep the pre-change report outside the output directory or use a different `OutputDirectory`, so
the post run cannot overwrite it. The comparison fails if a table column or RPC signature changes.

For catalogue-only CI:

```powershell
.\scripts\run-backend-compatibility-gates.ps1 -Mode catalog -SkipWeb -SkipAndroid
```

For a fast public/API check:

```powershell
.\scripts\run-backend-compatibility-gates.ps1 -Mode public -SkipWeb -SkipAndroid
```

The checked-in manifest is intentionally limited to client-visible contracts. Update it only when
a coordinated client migration explicitly changes those contracts.
