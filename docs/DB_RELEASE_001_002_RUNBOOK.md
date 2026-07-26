# Runbook de release aislado RLS-001 + RLS-002

## Alcance y decisión actual

Este runbook cubre exclusivamente:

1. `20260726171001_community_comments_delete_rls.sql`;
2. `20260726171002_official_post_likes_actor_guard.sql`.

`20260726171003` y `20260726171004` quedan fuera de la rama de integración, del
paquete selectivo, del dry-run y del despliegue.

Decisión actual: **NO-GO**. Puede cambiar a GO cuando el historial deje de estar
bloqueado con evidencia semántica o reconciliación aprobada, RLS-002 tenga un
rollback SQL versionado con prueba rollback/reaplicación, las ramas estén
integradas en un corte inmutable, el backup/PITR esté confirmado y el baseline
completo pase.

## Fuentes congeladas

- release-safety: `codex/db-release-safety`;
- RLS-001: `origin/codex/fix-rls-communities@46a54b54`;
- RLS-002: `origin/codex/fix-rls-official-likes@77d93da4`;
- gates: `origin/codex/backend-compatibility-gates@61987276`.

Antes de preparar el release, la rama de integración debe contener esas
fuentes revisadas sin 003/004. Registrar su `git rev-parse HEAD` y no admitir
cambios durante la ventana.

## Puertas GO

- snapshot read-only con `selectivePackageEligible=true`, sin decisiones
  históricas semánticamente no verificadas;
- hashes de 001/002 y sus rollbacks congelados;
- regresiones PostgreSQL desechables de 001 y 002 en verde;
- rollback/reaplicación en verde para ambas;
- suite completa de compatibilidad en verde y baseline guardado fuera de los
  directorios que se sobrescriben;
- backup administrado/PITR confirmado con timestamp inmediatamente anterior;
- cuentas SB-07/SB-09 aisladas y hard-cleanup aprobado;
- una única persona/terminal como release manager;
- ramas 003/004 ausentes del corte.

Un solo fallo conserva o devuelve la decisión a NO-GO.

## Variables de la terminal de release

```powershell
$env:SUPABASE_DB_TLS_CA_FILE = 'C:\Users\PC\.quata-supabase-pooler-ca.pem'
$dbUrlFile = 'C:\Users\PC\.quata-supabase-db-url.txt'
$webDist = '<dist Web de producción local>'
$androidApk = '<APK debug/release aprobado>'
$deviceId = '<serial API-37>'
```

Las variables públicas y las cuentas aisladas exigidas por
`run-backend-compatibility-gates.ps1`, SB-07 y SB-09 deben estar cargadas en el
proceso sin imprimirlas. No guardar salidas de entorno en la evidencia.

Antes del baseline:

```powershell
.\scripts\check-supabase-backup-readiness.ps1 -DbUrlFile $dbUrlFile
```

Un resultado `blocked_no_verifiable_restore_point` mantiene NO-GO.

## Baseline pre-release

```powershell
.\scripts\run-db-release-safety.ps1 `
  -Phase snapshot `
  -Output build-reports/db-release-safety/pre-001-snapshot.json `
  -DbUrlFile $dbUrlFile

.\scripts\run-backend-compatibility-gates.ps1 `
  -DbUrlFile $dbUrlFile `
  -TlsCaFile $env:SUPABASE_DB_TLS_CA_FILE `
  -WebDistribution $webDist `
  -AndroidApk $androidApk `
  -DeviceId $deviceId `
  -OutputDirectory build-reports/backend-compatibility/pre-001

.\scripts\run-rls001-community-comments-sql.ps1 -AllowIsolatedDatabase
.\scripts\test-official-likes-rls-migration.ps1
```

La regresión 001 requiere además `QUATA_RLS_TEST_SCOPE` y
`QUATA_RLS_TEST_DB_URL` apuntando a una base aislada aprobada; nunca a
producción.

## Paquete y dry-run de 001

```powershell
.\scripts\prepare-db-release-package.ps1 `
  -Snapshot build-reports/db-release-safety/pre-001-snapshot.json `
  -OutputDirectory build-reports/db-release-safety/release-001 `
  -MigrationFile 20260726171001_community_comments_delete_rls.sql

npx --yes supabase@2.109.1 link `
  --workdir build-reports/db-release-safety/release-001 `
  --project-ref '<project-ref aprobado>'

npx --yes supabase@2.109.1 db push `
  --linked `
  --workdir build-reports/db-release-safety/release-001 `
  --dry-run
```

El dry-run debe listar exactamente `20260726171001` y ningún fichero
histórico ni 002/003/004. Capturar el output sanitizado y obtener autorización
final explícita antes del único comando mutante:

```powershell
npx --yes supabase@2.109.1 db push `
  --linked `
  --workdir build-reports/db-release-safety/release-001 `
  --yes
```

## Postflight de 001

```powershell
.\scripts\run-db-release-safety.ps1 `
  -Phase postflight `
  -Output build-reports/db-release-safety/post-001.json `
  -DbUrlFile $dbUrlFile `
  -ExpectedMigration 20260726171001

.\scripts\run-backend-compatibility-gates.ps1 `
  -DbUrlFile $dbUrlFile `
  -TlsCaFile $env:SUPABASE_DB_TLS_CA_FILE `
  -WebDistribution $webDist `
  -AndroidApk $androidApk `
  -DeviceId $deviceId `
  -Baseline build-reports/backend-compatibility/pre-001/contracts.json `
  -OutputDirectory build-reports/backend-compatibility/post-001

.\scripts\run-supabase-e2e-sb07.ps1 `
  -AllowExistingTestData `
  -AllowCommunityMutation `
  -Output build-reports/supabase/sb-07-post-001.json
```

No avanzar a 002 si falla ledger, catálogo 18/44, columnas públicas,
navegación Web, Android API-37, Feed anónimo, SB-07 o limpieza.

## Paquete y dry-run de 002

Generar un snapshot nuevo: ahora 001 debe aparecer como tercera ancla real.

```powershell
.\scripts\run-db-release-safety.ps1 `
  -Phase snapshot `
  -Output build-reports/db-release-safety/pre-002-snapshot.json `
  -DbUrlFile $dbUrlFile

.\scripts\prepare-db-release-package.ps1 `
  -Snapshot build-reports/db-release-safety/pre-002-snapshot.json `
  -OutputDirectory build-reports/db-release-safety/release-002 `
  -MigrationFile 20260726171002_official_post_likes_actor_guard.sql

npx --yes supabase@2.109.1 link `
  --workdir build-reports/db-release-safety/release-002 `
  --project-ref '<project-ref aprobado>'

npx --yes supabase@2.109.1 db push `
  --linked `
  --workdir build-reports/db-release-safety/release-002 `
  --dry-run
```

El dry-run debe listar exactamente `20260726171002`. Tras autorización final:

```powershell
npx --yes supabase@2.109.1 db push `
  --linked `
  --workdir build-reports/db-release-safety/release-002 `
  --yes
```

## Postflight de 002

```powershell
.\scripts\run-db-release-safety.ps1 `
  -Phase postflight `
  -Output build-reports/db-release-safety/post-002.json `
  -DbUrlFile $dbUrlFile `
  -ExpectedMigration 20260726171001,20260726171002

.\scripts\run-backend-compatibility-gates.ps1 `
  -DbUrlFile $dbUrlFile `
  -TlsCaFile $env:SUPABASE_DB_TLS_CA_FILE `
  -WebDistribution $webDist `
  -AndroidApk $androidApk `
  -DeviceId $deviceId `
  -Baseline build-reports/backend-compatibility/pre-001/contracts.json `
  -OutputDirectory build-reports/backend-compatibility/post-002

.\scripts\run-supabase-e2e-sb09.ps1 `
  -AllowExistingTestData `
  -AllowOfficialLikeMutation `
  -Output build-reports/supabase/sb-09-post-002.json
```

Repetir SB-07 tras 002 si la ventana permite mutaciones aisladas; como mínimo
repetir el gate público/catálogo, Web y Android completo. Confirmar que el
postflight ya no marca el guard de Official Likes, que `anon SELECT` permanece
y que spoof/delete ajeno fallan.

## Abort y rollback

### Si falla 001

Detener la serie. Aplicar únicamente el rollback versionado de 001, en una
transacción y desde una sesión de operador aprobada. Después ejecutar:

- postflight esperando que `20260726171001` permanezca en ledger;
- suite 18/44, Feed/Web/Android;
- registrar que RLS-001 vuelve a estar abierto.

El ledger es histórico: no se borra ni se repara para fingir que 001 no se
ejecutó. Una corrección posterior debe usar un timestamp nuevo.

### Si falla 002

Detener la serie. Aplicar únicamente el rollback versionado y ensayado de 002.
Después repetir postflight y compatibilidad y registrar que RLS-002 vuelve a
estar abierto. No revertir 001 salvo que la evidencia demuestre que 001 es la
causa independiente.

Nunca ejecutar un rollback de 003/004, `migration repair`, `DROP` genérico ni
el backlog histórico.

## Cierre

El release sólo se declara completado cuando:

- ledger contiene 001 y 002 una vez cada una;
- segundo dry-run de cada paquete queda “Remote database is up to date”;
- todos los gates y hard-cleanup terminan en verde;
- no hay residuos de cuentas/filas E2E;
- evidencia y hashes están archivados;
- 003/004 continúan fuera de producción.
