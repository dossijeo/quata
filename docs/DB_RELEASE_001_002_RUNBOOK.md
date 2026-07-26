# Runbook de release aislado RLS-001 + RLS-002

## Alcance y decisión

Este runbook cubre exclusivamente:

1. `20260726171001_community_comments_delete_rls.sql`;
2. `20260726171002_official_post_likes_actor_guard.sql`.

`20260726171003` y `20260726171004` quedan fuera del corte. El historial remoto
no permite usar con seguridad `supabase db push`: la única vía autorizable es
`scripts/run-security-release-serial-executor.ps1`, cuya allowlist limita los
SQL y rollbacks por SHA-256.

La decisión permanece **NO-GO para apply** hasta autorización explícita del
release manager. El dry-run es read-only.

Estado remoto actual: 171001 está en ledger porque fue aplicada y después
revertida; su catálogo vulnerable quedó restaurado. 171005 y 002 están
ausentes. Por tanto `apply-001` ya no es una acción válida: la única nueva
contención posible es `apply-001-forward` para 171005.

### Autoridad y excepción limitada

El release manager es la única autoridad para abrir la ventana y autorizar por
separado apply-001/apply-002. Ha aceptado para este lote la ausencia de PITR y
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
- dry-run remoto con `status=passed`, 001/002 `ledger=absent` y fingerprints
  previos archivados;
- baseline y compatibilidad completos en verde;
- evidencia encadenada de postflight 001 vigente antes de 002;
- una única terminal/persona ejecutora;
- autorización explícita separada para cada apply;
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

Registrar de ese informe los fingerprints previos de 001 y 002. No sustituir
este comando por `supabase db push --dry-run`.

## Apply forward 001 (171005)

Sólo tras autorización explícita:

```powershell
.\scripts\run-security-release-serial-executor.ps1 `
  -Action apply-001-forward `
  -DbUrlFile $dbUrlFile `
  -TlsCaFile $tlsCaFile `
  -ExpectedPreconditionSha256 '<fingerprint 171005 del dry-run>' `
  -Output build-reports/security-release/apply-001-forward.json
```

El ejecutor toma advisory lock y locks de tabla/ledger, vuelve a comprobar
ledger y catálogo dentro de una transacción serializable, aplica únicamente el
SQL allowlisted, valida el estado efectivo de RLS/policies/grants y escribe el
ledger en la misma transacción.

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

No avanzar si falla ledger, catálogo 18/44, Web, Android, SB-07 o limpieza.
Antes del gate completo debe pasar `preflight-auth` y después:

```powershell
$env:QUATA_SB07_PRODUCTION_GATE_APPROVED = 'approved_temporary_fixture_only'
$env:QUATA_SB07_PRODUCTION_GATE_ALLOW_MUTATION = 'approved_public_postgrest_mutations'
.\scripts\run-supabase-e2e-sb07-post-forward.ps1 `
  -Mode full `
  -DbUrlFile $dbUrlFile `
  -TlsCaFile $tlsCaFile `
  -RecoveryFile '<ruta externa al repo con ACL exclusiva>' `
  -Output build-reports/supabase/sb07-post-forward.json
```

Construir el JSON de gate 171005 según
`docs/SERIAL_SECURITY_RELEASE_EXECUTOR.md`; debe estar anclado a commit,
snapshot, destino, precondición, `postconditionSha256` de 171005 y hashes de
todos los informes.

## Apply 002

Sólo tras autorización explícita y gate 001 válido:

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
