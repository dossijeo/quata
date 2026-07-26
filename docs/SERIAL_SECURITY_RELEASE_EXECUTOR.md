# Ejecutor serial RLS-001 / RLS-002

`scripts/security-release-serial-executor.mjs` es el único mecanismo de este
lote que puede escribir las dos migraciones aprobadas. No llama a `supabase db
push`, no repara historiales y no considera ninguna migración de backlog.

Usar el wrapper para que la URL nunca llegue a argumentos ni a informes:

```powershell
.\scripts\run-security-release-serial-executor.ps1 -Action dry-run `
  -DbUrlFile C:\Users\PC\.quata-supabase-db-url.txt `
  -TlsCaFile C:\Users\PC\.quata-supabase-pooler-ca.pem `
  -Output build-reports/security-release/001-preflight.json
```

El informe devuelve un `preconditionSha256` por migración. Antes de aplicar,
comparar y copiar manualmente el hash correspondiente, sin volver a obtenerlo
después de abrir la ventana de release:

```powershell
.\scripts\run-security-release-serial-executor.ps1 -Action apply-001 `
  -DbUrlFile C:\Users\PC\.quata-supabase-db-url.txt `
  -TlsCaFile C:\Users\PC\.quata-supabase-pooler-ca.pem `
  -ExpectedPreconditionSha256 '<hash de 001>' `
  -Output build-reports/security-release/001-apply.json
```

Tras los gates externos post-001, crear una evidencia JSON revisada con
`migration: "20260726171001"`, `status: "passed"` y
`preconditionSha256`. Sólo entonces es posible abrir el segundo comando:

```powershell
.\scripts\run-security-release-serial-executor.ps1 -Action apply-002 `
  -DbUrlFile C:\Users\PC\.quata-supabase-db-url.txt `
  -TlsCaFile C:\Users\PC\.quata-supabase-pooler-ca.pem `
  -ExpectedPreconditionSha256 '<hash de 002>' `
  -GateEvidence build-reports/security-release/001-external-gates.json
```

Propiedades comprobadas:

- TLS usa CA explícita, `rejectUnauthorized=true` y `servername` del host.
- `pg_try_advisory_lock(hashtextextended(...))` rechaza otra sesión gestora.
- Los SHA-256 son de los **bytes exactos** UTF-8 del fichero versionado: no hay
  normalización de EOL. Por tanto un checkout que reescriba CRLF/LF se rechaza
  antes de conectarse; el release debe ejecutarse desde el commit congelado.
- Se valida y elimina exclusivamente el `BEGIN`/`COMMIT` exterior de cada SQL;
  se rechaza control transaccional ejecutable anidado. El payload y el
  `INSERT(version, statements, name)` se ejecutan dentro de una única
  transacción SERIALIZABLE.
- Se exige exactamente el formato remoto
  `supabase_migrations.schema_migrations(version text, statements text[], name text)`.
  La fila usa el texto completo como único elemento de `statements` y el nombre
  compatible de Supabase. Una versión existente es un error, nunca un repair o
  una reaplicación.

La prueba local se ejecuta así y crea/elimina un PostgreSQL 17 TLS desechable:

```powershell
.\scripts\test-security-release-serial-executor.ps1
```

Comprueba hash drift, rollback atómico de DDL+ledger, exclusión por lock,
orden/evidencia de 002, ledger exacto e idempotencia por rechazo.
