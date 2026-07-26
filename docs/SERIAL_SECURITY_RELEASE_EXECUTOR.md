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

Tras los gates externos post-001, crear una evidencia JSON revisada con el
esquema `1`. Debe incluir el `releaseCommit` congelado (40 hex), el
`snapshotFingerprint` del baseline (SHA-256), `migration`, `status`,
`preconditionSha256` y estos tres informes con `status: "passed"` y SHA-256
del fichero que se revisó: `dbReleaseSafety`, `backendCompatibility` y `sb07`.
Un aprobador calcula además el SHA-256 de los **bytes** de esta evidencia. El
segundo paso exige los tres anchors por argv; no acepta un JSON genérico:

```powershell
.\scripts\run-security-release-serial-executor.ps1 -Action apply-002 `
  -DbUrlFile C:\Users\PC\.quata-supabase-db-url.txt `
  -TlsCaFile C:\Users\PC\.quata-supabase-pooler-ca.pem `
  -ExpectedPreconditionSha256 '<hash de 002>' `
  -GateEvidence build-reports/security-release/001-external-gates.json `
  -ExpectedGateEvidenceSha256 '<SHA-256 aprobado del JSON>' `
  -ExpectedReleaseCommit '<commit de 40 hex>' `
  -ExpectedSnapshotFingerprint '<SHA-256 del snapshot>' `
  -ExpectedDatabaseProjectFingerprint '<SHA-256 del destino del preflight>'
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
- La evidencia de gates de 002 se valida con esquema versionado y con el hash
  esperado que el aprobador pasó explícitamente. Esto no sustituye la revisión
  humana de los tres informes ni una firma externa, pero evita que un fichero
  JSON trivial, no anclado a la ventana/commit/snapshot aprobados, desbloquee
  el segundo cambio.
- El executor deriva `databaseProjectFingerprint` de host/puerto/base del URL
  de destino y sólo conserva su SHA-256. Debe coincidir con el informe,
  todos los reports post-001 y el anchor explícito; así una evidencia de otro
  proyecto/pooler no puede desbloquear 002.

La prueba local se ejecuta así y crea/elimina un PostgreSQL 17 TLS desechable:

```powershell
.\scripts\test-security-release-serial-executor.ps1
```

Comprueba hash drift, rollback atómico de DDL+ledger, una carrera de escritor
externo antes del lock (revalidación y aborto limpio), bloqueo de DDL externo
hasta el commit, exclusión advisory, orden/evidencia de 002, ledger exacto y
rechazo de drift/idempotencia de rollbacks 001/002.

## Rollback

`rollback-001` y `rollback-002` sólo ejecutan el rollback versionado cuyo hash
figura en la allowlist. Requieren el mismo fingerprint previo, lock advisory,
locks de tabla/ledger y una fila ledger ya aplicada **byte-exacta**. Nunca
borran, editan ni reparan dicha fila: una reaplicación necesita una nueva
migración forward revisada. El rollback se rechaza ante drift.
