# Ejecutor serial RLS-001 / RLS-002

`scripts/security-release-serial-executor.mjs` es el único mecanismo de este
lote que puede escribir las migraciones allowlisted. No llama a `supabase db
push`, no repara historiales y no considera ninguna migración de backlog.

Usar el wrapper para que la URL nunca llegue a argumentos ni a informes:

```powershell
.\scripts\run-security-release-serial-executor.ps1 -Action dry-run `
  -DbUrlFile C:\Users\PC\.quata-supabase-db-url.txt `
  -TlsCaFile C:\Users\PC\.quata-supabase-pooler-ca.pem `
  -Output build-reports/security-release/001-preflight.json
```

El informe debe mostrar 171001 `present` con ledger byte-exacto y 171005/002
`absent`. Devuelve un `preconditionSha256` por migración. Antes de aplicar la
forward, comparar y copiar manualmente el hash de 171005:

```powershell
.\scripts\run-security-release-serial-executor.ps1 -Action apply-001-forward `
  -DbUrlFile C:\Users\PC\.quata-supabase-db-url.txt `
  -TlsCaFile C:\Users\PC\.quata-supabase-pooler-ca.pem `
  -ExpectedPreconditionSha256 '<hash precondición de 171005>' `
  -Output build-reports/security-release/001-forward-apply.json
```

Tras los gates externos post-forward 171005, crear una evidencia JSON revisada con el
esquema `1`. Debe incluir el `releaseCommit` congelado (40 hex), el
`snapshotFingerprint` del baseline (SHA-256), `migration`, `status`,
`preconditionSha256` **y** `postconditionSha256` de 171005 y estos tres informes con `status: "passed"` y SHA-256
del fichero que se revisó: `dbReleaseSafety`, `backendCompatibility` y `sb07`.
`sb07` es el informe compuesto aprobado: prueba mutante completa en Supabase
local exacto más postcondición/catalogo bajo lock y gates productivos
read-only/API-37; no requiere fixtures Auth/perfiles en producción.
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
  -ExpectedDatabaseProjectFingerprint '<SHA-256 del destino del preflight>' `
  -ExpectedForwardPostconditionSha256 '<postconditionSha256 emitido por apply-001-forward>'
```

Propiedades comprobadas:

- TLS usa CA explícita, `rejectUnauthorized=true` y `servername` del host.
- Los parámetros TLS de la URL no pueden reemplazar esa configuración.
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
- `apply-001-forward` emite su `postconditionSha256` antes de confirmar la
  transacciÃ³n. El gate y el argumento explÃ­cito de `apply-002` deben coincidir
  byte a byte con ese valor; tras bloquear ambas tablas, 002 vuelve a calcular
  el fingerprint efectivo de 171005 y rechaza cualquier drift o gate stale.
- El executor deriva `databaseProjectFingerprint` de host/puerto/base del URL
  de destino, usuario/project-ref normalizado y la identidad SQL consultada
  (`pg_control_system().system_identifier`, OID/base y rol); sólo conserva su
  SHA-256 y falla cerrado si esa identidad no está disponible. Debe coincidir
  con el informe, todos los reports post-001 y el anchor explícito.
- Dentro de la transacción toma locks selectivos `FOR SHARE` sobre las filas
  `pg_proc` de todos los resolvers/guards afectados. Después revalida el
  fingerprint y, antes del commit, comprueba definiciones, owner, ACL,
  policies, grants y binding del trigger del estado resultante.
- En Supabase gestionado, donde el owner puede alterar sus funciones pero no
  bloquear filas de `pg_proc`, usa un fallback no cooperativo: cambia
  transitoriamente `COST` y lo restaura dentro de la misma transacción. Esto
  toma el lock real del tuple frente a `ALTER`, `DROP` y
  `CREATE OR REPLACE`; el fingerprint y `procost` finales deben ser idénticos.
  Un fallo revierte también el cambio transitorio. El event trigger PostgREST
  sólo emite su notificación habitual de recarga y no ejecuta DML.

La prueba local se ejecuta así y crea/elimina un PostgreSQL 17 TLS desechable:

```powershell
.\scripts\test-security-release-serial-executor.ps1
```

Comprueba hash drift, rollback atómico de DDL+ledger, dos usuarios sobre el
mismo pooler, una carrera de escritor externo antes del lock, bloqueo selectivo
de DDL externo de tabla/`CREATE OR REPLACE FUNCTION` hasta el commit, llamadas
concurrentes a funciones no bloqueadas, restauración de `procost`, exclusión advisory,
orden/evidencia de 002, ledger exacto y rechazo de drift/idempotencia de
rollbacks 001/002.

## Rollback

`rollback-001` y `rollback-002` sólo ejecutan el rollback versionado cuyo hash
figura en la allowlist. Requieren el mismo fingerprint previo, lock advisory,
locks de tabla/ledger y una fila ledger ya aplicada **byte-exacta**. Nunca
borran, editan ni reparan dicha fila: una reaplicación necesita una nueva
migración forward revisada. El rollback se rechaza ante drift.

Tras rollback de 001, la reaplicación usa exclusivamente la nueva versión
`20260726171005_community_comments_reapply_rls`: el executor conserva 171001
byte-exacto, inserta 171005 y exige 171005 byte-exacto antes de permitir 002.
Si se ejecuta `rollback-001-forward`, 171005 permanece deliberadamente en el
ledger y esa forward queda cerrada: no puede reaplicarse. Cualquier nueva
contención exige otra versión forward revisada.
