# OVR-MEDIA — migración de objetos históricos para CORS/COEP

Este runbook mueve únicamente los MP4 que todavía están referenciados por
`community_posts.video_url` u `official_posts.media_url` y cuyo origen exacto es
`https://egquata.com/wp-content/uploads/...`. No habilita COEP, no cambia el
reproductor y no amplía los orígenes aceptados por el producto.

Los destinos conservan la semántica actual de cada productor:

- Feed usa el bucket público `community-posts`;
- Official usa el bucket público `official-media`;
- cada ejecución escribe dentro de
  `historical-cors-v1/<run-id>/<sha256-del-url>.mp4`;
- dos filas que comparten origen y bucket comparten objeto; un mismo origen en
  buckets distintos se copia por separado.

La herramienta no acepta otros hosts, extensiones ni columnas. El journal
privado contiene las URLs e identificadores necesarios para rollback y permanece
en `build-reports/migration/private/`, fuera de Git.

## Preflight de solo lectura

```powershell
npm run test:media-historical-cors-contract
node scripts/media-historical-cors-migration.mjs `
  --mode dry-run `
  --run-id cors-YYYYMMDD-XXXXXXXX `
  --out build-reports/migration/media-historical-cors-dry-run.json
```

El preflight descarga y hashea cada origen completo, deduplica por bucket y
exige que el namespace de destino esté vacío. No autentica ni escribe.

## Backup y restauración focal

Antes de cada apply se crea un backup **Full** nuevo. La conexión debe exigir
`verify-full`, la CA se pasa por archivo y el dump nunca se escribe en claro.

```powershell
pwsh -NoProfile -File scripts/new-db-logical-backup.ps1 `
  -DbUrlFile C:\Users\PC\.quata-supabase-db-url-verify-full.txt `
  -TlsCaFile C:\Users\PC\.quata-supabase-pooler-ca.pem `
  -EncryptionKeyFile C:\Users\PC\.quata-release-backup-20260726.key `
  -OutRoot C:\Users\PC\Desktop\QÜATA\Database-Backups `
  -Scope Full

pwsh -NoProfile -File scripts/restore-db-logical-backup-drill.ps1 `
  -BackupSet <backup-set> `
  -EncryptionKeyFile C:\Users\PC\.quata-release-backup-20260726.key `
  -MediaHistoricalCorsScope `
  -ExpectedCommunityPosts <count> `
  -ExpectedOfficialPosts <count> `
  -ReportPath build-reports/migration/private/media-historical-cors-restore-proof.json
```

El apply rechaza un backup de más de 24 horas, un manifiesto que no sea Full y
una prueba de restauración que no esté ligada por SHA-256 al mismo manifiesto y
a las dos tablas.

## Apply y postflight

```powershell
node scripts/media-historical-cors-migration.mjs `
  --mode apply `
  --run-id cors-YYYYMMDD-XXXXXXXX `
  --authorization I_ACCEPT_REVERSIBLE_HISTORICAL_MEDIA_MIGRATION `
  --backup-manifest <backup-set>\manifest.json `
  --restore-proof build-reports/migration/private/media-historical-cors-restore-proof.json `
  --journal build-reports/migration/private/media-historical-cors-journal.json `
  --out build-reports/migration/media-historical-cors-apply.json

node scripts/media-historical-cors-migration.mjs `
  --mode postflight `
  --journal build-reports/migration/private/media-historical-cors-journal.json `
  --out build-reports/migration/media-historical-cors-postflight.json
```

Cada objeto se vuelve a descargar desde Supabase y debe conservar tamaño y
SHA-256, además de `Access-Control-Allow-Origin: *`. Sólo entonces una única
transacción cambia cada fila si su URL todavía coincide exactamente con el
snapshot. Cualquier drift aborta todo el cambio de base de datos.

## Rollback

El fallo durante el apply restaura las filas que ya se hubieran cambiado y
elimina sólo los objetos del prefijo propiedad de esa ejecución. Para rollback
posterior se reutilizan el journal, el backup y la prueba de restauración:

```powershell
node scripts/media-historical-cors-migration.mjs `
  --mode rollback `
  --authorization I_ACCEPT_REVERSIBLE_HISTORICAL_MEDIA_MIGRATION `
  --backup-manifest <backup-set>\manifest.json `
  --restore-proof build-reports/migration/private/media-historical-cors-restore-proof.json `
  --journal build-reports/migration/private/media-historical-cors-journal.json `
  --out build-reports/migration/media-historical-cors-rollback.json
```

El journal se valida antes de interpolar nombres SQL o borrar Storage: sólo
admite las dos tablas, sus columnas conocidas, los dos buckets y el prefijo del
`run-id`. No se versionan credenciales, URLs de origen, IDs de filas, tokens ni
el journal.

