# Runbook de release aislado RLS-001 + RLS-002

## Alcance y decisión

Este runbook cubre exclusivamente:

1. `20260726171001_community_comments_delete_rls.sql`;
2. `20260726171002_official_post_likes_actor_guard.sql`.

`20260726171003` y `20260726171004` quedan fuera del corte. El historial remoto
no permite usar con seguridad `supabase db push`: la única vía autorizable es
`scripts/run-security-release-serial-executor.ps1`, cuya allowlist limita los
SQL y rollbacks por SHA-256.

La decisión permanece **NO-GO para cualquier nueva apply** —actualmente sólo
002— hasta autorización explícita del release manager. El dry-run es read-only.

Estado remoto actual: 171001 y 171005 están en ledger byte-exacto; el catálogo
de comentarios está hardened y 002 está ausente. Ni `apply-001` ni
`apply-001-forward` son ya acciones válidas. Sólo 002 puede abrir una futura
ventana, siempre con autorización explícita separada y gates post-171005.

### Autoridad y excepción limitada

El release manager es la única autoridad para abrir la ventana de 002 y
autorizar `apply-002`. La forward 171005 ya fue autorizada, aplicada y cerrada.
Ha aceptado para este lote la ausencia de PITR y
la no reconciliación de 29 migraciones históricas porque el ejecutor no toca el
backlog, 001/002 son DDL/RLS sin DML, los rollbacks exactos están probados y
existe backup lógico Full con restore verificado de los objetos afectados.
Esas 29 migraciones siguen siendo un hallazgo: no se marcan, reparan ni
aplican. La recuperación lógica no equivale a restaurar Supabase integralmente.

## Evidencia ya disponible

- Rama de integración: `codex/security-release-001-002`.
- Hashes de migración y rollback congelados en
  `scripts/security-release-serial-allowlist.json`.
- Regresiones PostgreSQL 17 de 001 y 002 en verde.
- Rollback y reaplicación de 001/002 ensayados.
- Compatibilidad pública Web, Android API-37 y contratos 18 tablas/44 RPC en
  verde.
- Backup lógico Full real cifrado y clave separada con ACL exclusiva.
- Drill PostgreSQL 17 de los objetos afectados: TOC, autenticidad, checksums y
  conteos reales exactos.
- PITR no está habilitado; el backup lógico no equivale a una restauración
  integral de todos los servicios gestionados de Supabase.
- Dry-run remoto serial guardado bajo
  `build-reports/security-release/remote-dry-run.json`.

## Puertas GO

- corte Git inmutable e independiente revisado;
- backup lógico Full y drill de alcance verificados;
- hashes de la allowlist sin cambios;
- dry-run remoto con `status=passed`, 171001/171005 `ledger=present`
  byte-exacto, 002 `ledger=absent` y fingerprints archivados;
- baseline y compatibilidad completos en verde;
- evidencia encadenada de postflight 171005 vigente antes de 002;
- una única terminal/persona ejecutora;
- autorización explícita separada para `apply-002`;
- 003/004 ausentes de la rama y de la ejecución.

Un solo fallo conserva o devuelve la decisión a NO-GO.

Inmediatamente antes de la ventana se deben refrescar el backup lógico Full,
el drill con conteos reales, el snapshot read-only, el baseline de
compatibilidad y este dry-run remoto.

## Variables de la terminal de release

```powershell
$dbUrlFile = 'C:\Users\PC\.quata-supabase-db-url.txt'
$tlsCaFile = 'C:\Users\PC\.quata-supabase-pooler-ca.pem'
$webDist = '<dist Web de producción local>'
$androidApk = '<APK aprobado>'
$deviceId = '<serial API-37>'
```

La URL nunca se pasa por argv ni se imprime. El ejecutor elimina parámetros
TLS de la URL y fuerza CA explícita, hostname y `rejectUnauthorized=true`.

## Dry-run remoto read-only

```powershell
.\scripts\run-security-release-serial-executor.ps1 `
  -Action dry-run `
  -DbUrlFile $dbUrlFile `
  -TlsCaFile $tlsCaFile `
  -Output build-reports/security-release/remote-dry-run.json
```

Registrar los fingerprints de 171005 y 002 y verificar el ledger exacto. No
sustituir este comando por `supabase db push --dry-run`.

## Forward 001 (171005) completada

171005 ya se aplicó atómicamente con el executor allowlisted. Emitió
`postconditionSha256`, insertó su ledger exacto y pasó el postflight compuesto.
No volver a ejecutar `apply-001-forward`: el executor debe rechazarla por
ledger duplicado.

Después deben pasar:

```powershell
.\scripts\run-db-release-safety.ps1 `
  -Phase postflight `
  -Output build-reports/db-release-safety/post-001.json `
  -DbUrlFile $dbUrlFile `
  -ExpectedMigration 20260726171001,20260726171005

.\scripts\run-backend-compatibility-gates.ps1 `
  -DbUrlFile $dbUrlFile `
  -TlsCaFile $tlsCaFile `
  -WebDistribution $webDist `
  -AndroidApk $androidApk `
  -DeviceId $deviceId `
  -OutputDirectory build-reports/backend-compatibility/post-001
```

No avanzar si falla ledger, catálogo 18/44, Web, Android o la evidencia SB-07
compuesta.

### Excepción SB-07 sin fixtures productivos

Para 171005 no se crean cuentas, perfiles ni comentarios temporales en
producción. El riesgo de un cleanup parcial de Auth/perfiles supera el valor
adicional del ensayo mutante remoto. El gate `sb07` se compone de:

- SB-07 completo contra Supabase local exacto: anon shape, own insert,
  spoof `42501`, outsider/owner/admin active-inactive, UPDATE bloqueado,
  rollback/reaplicación y cleanup;
- hashes allowlisted de migración/rollback 171005;
- `postconditionSha256` emitido por el executor antes del commit;
- catálogo productivo bajo locks con RLS, policies, grants y helpers exactos;
- gates read-only productivos 18/44, 10 GET, Web y Feed anónimo;
- API-37 autenticado con Feed, Communities/«Abre una comunidad», comentarios
  visibles y cero crash/ANR, sin crear fixtures.

El runner productivo `run-supabase-e2e-sb07-post-forward.ps1` queda como
follow-up no bloqueante y **no se ejecuta ni integra** hasta obtener GO
crash-safe independiente.

Construir el JSON de gate 171005 según
`docs/SERIAL_SECURITY_RELEASE_EXECUTOR.md`; debe estar anclado a commit,
snapshot, destino, precondición, `postconditionSha256` de 171005 y hashes de
todos los informes.

## Apply 002

Sólo tras autorización explícita separada y gate 171005 válido:

```powershell
.\scripts\run-security-release-serial-executor.ps1 `
  -Action apply-002 `
  -DbUrlFile $dbUrlFile `
  -TlsCaFile $tlsCaFile `
  -ExpectedPreconditionSha256 '<fingerprint 002 actualizado>' `
  -GateEvidence '<gate-001.json>' `
  -ExpectedGateEvidenceSha256 '<sha256 gate>' `
  -ExpectedReleaseCommit '<commit de 40 hex>' `
  -ExpectedSnapshotFingerprint '<sha256 snapshot>' `
  -ExpectedDatabaseProjectFingerprint '<fingerprint derivado del destino>' `
  -ExpectedForwardPostconditionSha256 '<postconditionSha256 de apply-001-forward>' `
  -Output build-reports/security-release/apply-002.json
```

Repetir postflight, compatibilidad, Web, Android y SB-09. Confirmar que
`anon SELECT` permanece y que spoof y borrado ajeno fallan.

## Abort y rollback

El rollback siempre usa el ejecutor y el fingerprint obtenido inmediatamente
antes. Nunca ejecutar SQL manual, `migration repair`, `db push`, un rollback de
003/004 ni un `DROP` genérico.

```powershell
.\scripts\run-security-release-serial-executor.ps1 `
  -Action rollback-002 `
  -DbUrlFile $dbUrlFile `
  -TlsCaFile $tlsCaFile `
  -ExpectedPreconditionSha256 '<fingerprint actual 002>'
```

Para la forward se usa el mismo comando con `-Action rollback-001-forward`.
Los ledgers 171001/171005 son históricos y se conservan. Tras ese rollback, una
nueva contención requiere otra versión forward; nunca se reaplica 171005.

## Cierre

El release sólo se declara completado cuando:

- ledger contiene 171001, 171005 y 002 exactamente una vez con
  fuente/nombre allowlisted;
- informes apply y postflight están encadenados y archivados;
- todos los gates y hard-cleanup terminan en verde;
- no quedan cuentas ni filas E2E temporales;
- 003/004 continúan fuera de producción.
