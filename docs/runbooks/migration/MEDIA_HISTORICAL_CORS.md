# OVR-MEDIA — migración de objetos históricos para CORS/COEP

Este runbook mueve los MP4 históricos que todavía están referenciados por
`community_posts.video_url` u `official_posts.media_url` y cuyo origen exacto es
`https://egquata.com/wp-content/uploads/...`. También cierra el productor que
originaba esa deuda: las nuevas cargas de vídeo autenticadas de Feed y Official
usan Supabase Storage en Android, Web e iOS. La moderación y la inserción de
posts conservan sus contratos. No habilita COEP, no cambia el reproductor y no
amplía los orígenes aceptados por el producto.

Los destinos conservan la semántica actual de cada productor:

- Feed usa el bucket público `community-posts`;
- Official usa el bucket público `official-media`;
- cada ejecución escribe dentro de
  `historical-cors-v1/<run-id>/<sha256-del-url>.mp4`;
- dos filas que comparten origen y bucket comparten objeto; un mismo origen en
  buckets distintos se copia por separado.

Los adaptadores de publicación escriben vídeos nuevos bajo el perfil actor en
`<profileId>/video-<timestamp>-<token>.<ext>`, con bearer renovable y rollback
del objeto si falla la inserción. Feed y el editor Official usan
`community-posts`, el bucket que ya admite carga y borrado del cliente; el bucket
`official-media` queda reservado a la copia histórica controlada. Las imágenes
mantienen su ruta previa en `community-posts`. WordPress conserva únicamente la
moderación de Composer y el rollback compatible con tokens antiguos; no recibe
vídeos nuevos.

La herramienta no acepta otros hosts, extensiones ni columnas. El journal
privado contiene las URLs e identificadores necesarios para rollback y permanece
en `build-reports/migration/private/`, fuera de Git. Sus checkpoints se escriben
mediante archivo temporal sincronizado y renombrado atómico. Apply, postflight y
rollback toman un lock exclusivo junto al journal para impedir dos ejecuciones
concurrentes. Si un proceso termina de forma abrupta, se comprueba primero que
no siga vivo ni exista otra ejecución activa y sólo entonces se retira
manualmente el archivo `<journal>.lock` antes de reanudar.

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

## Cierre productivo 2026-10-10

Product SHA `10b415a09e239b7a3cebb211f83c9982721b5f47` completó el apply y el
postflight: 43 referencias de fila quedaron ligadas a 41 objetos propios
(173 617 469 bytes), con igualdad de SHA-256/tamaño, lectura pública CORS y
ausencia de referencias al origen histórico. El apply quedó precedido por un
backup Full cifrado y un restore drill focal ligado al manifiesto. Los hashes
sanitizados y la custodia del cierre están en
`docs/candidate-attestations/media-historical-cors.json`; el journal privado se
conserva fuera de Git para rollback.
