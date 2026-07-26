# Seguridad de release de base de datos

## Estado del corte

- Rama de preparación: `codex/db-release-safety`.
- Base auditada: `origin/main` `366c86aa`.
- Fecha: 2026-07-26.
- Estado remoto: PostgreSQL 17.6, consultado con TLS `verify-full`, CA explícita
  y transacción `READ ONLY`.
- Despliegue: **bloqueado**. Este lote no ejecutó DDL, DML, RPC ni funciones.

El release manager de este lote es el único dueño eventual de la ejecución
serial. Ninguna rama proveedora debe ejecutar `supabase db push`, aplicar SQL,
reparar el ledger o desplegar funciones. Hace falta autorización expresa del
orquestador después de revisar migraciones, rollback y evidencias.

## Bloqueo de historial

El repositorio contiene 31 SQL de migración, pero
`supabase_migrations.schema_migrations` sólo registra:

- `20260628 / 0001_chat_schema`;
- `20260723 / 0001_multidevice_fcm_and_web_push`.

Hay 29 SQL locales sin entrada remota y siete prefijos de versión repetidos.
Los nombres históricos `YYYYMMDD_000N_...sql` no son versiones únicas para
Supabase CLI: la versión es el segmento anterior al primer `_`. Por ello:

- **no ejecutar `supabase db push`**: podría intentar aplicar migraciones
  históricas ya desplegadas fuera del ledger;
- no ejecutar `migration repair` en bloque ni inventar que los 29 SQL están
  desplegados; primero habría que reconciliar cada hash con catálogo remoto;
- cada migración nueva usa un timestamp único de 14 dígitos.

Reserva de integración:

1. `20260726171001_community_comments_delete_rls.sql`;
2. `20260726171002_official_post_likes_actor_guard.sql`;
3. gates de compatibilidad;
4. `20260726171003_community_profiles_actor_guard.sql`;
5. repetir gates;
6. `20260726171004_web_registration_contract.sql`.

La reserva ordena el paquete, pero no autoriza su aplicación.

## Evidencia read-only y puertas

Ejecutar desde la raíz, con la URL en fichero y una sola CA explícita:

```powershell
$env:SUPABASE_DB_TLS_CA_FILE = 'C:\ruta\pooler-ca.pem'
.\scripts\run-db-release-safety.ps1 `
  -Phase snapshot `
  -DbUrlFile 'C:\ruta\db-url.txt'

.\scripts\run-db-release-safety.ps1 `
  -Phase preflight `
  -DbUrlFile 'C:\ruta\db-url.txt'
```

Los informes se escriben bajo `build-reports/db-release-safety/`, que está
ignorado por Git. No contienen URL, credenciales, IDs ni valores de negocio.
Incluyen hashes SHA-256 de los SQL locales, ledger remoto, políticas/grants,
firmas de RPC y hashes de funciones de trigger.

El `snapshot` captura evidencia aunque exista drift. `preflight` falla cerrado
con `blocked_history_reconciliation` mientras el ledger no tenga un método de
aplicación seleccionado que garantice que no se reejecutan los 29 SQL.

### ANDROID-COMPAT-01

PASS exige simultáneamente:

- las diez tablas consumidas por el cliente actual presentes;
- los 32 RPC `quata_chat_*` invocados por `SupabaseCommunityApi` presentes;
- firmas registradas para comparar antes/después;
- tras el eventual despliegue, repetición del catálogo y smoke del APK actual
  en API-37 sin crash/ANR.

En el preflight del 2026-07-26: `missingTables=[]`,
`missingRpcs=[]`. El código de producto de `366c86aa` es idéntico a
`9cc84dc2` en `app`, `core`, `designsystem`, `feature` y `web`; ese corte ya
tenía assemble y smoke Android acreditados. Esto es evidencia previa, no
sustituye el smoke postflight.

### FEED-ANON-01

PASS de base de datos exige:

- `anon` conserva `SELECT` sobre `community_posts`, `community_profiles` y
  `community_walls`;
- dentro de `SET LOCAL ROLE anon` y transacción read-only existe al menos un
  post visible;
- las políticas y grants quedan incluidos en el snapshot para comparar.

En el preflight del 2026-07-26: `missingSelectGrants=[]` y
`hasVisiblePost=true`.

La navegación es una puerta separada: el smoke Web versionado de `9cc84dc2`
recorrió `#feed`, y el árbol Web de `366c86aa` es idéntico. Tras el eventual
despliegue se debe repetir la ruta `#feed` y una lectura sin bearer. Catálogo
SQL y smoke local por separado no se presentan como E2E de navegador remoto.

### TRIGGER-ACTOR-01

El preflight inventaría triggers en posts, comentarios y likes, y marca como
riesgo cualquier función de trigger `SECURITY DEFINER` que consulte
`current_user` directamente o mediante `quata_current_role_is_service()`.
No publica el cuerpo: sólo nombre, flags y SHA-256.

El snapshot desplegado encontró tres riesgos:

- `official_posts / quata_guard_official_posts_trg`;
- `official_post_comments / quata_guard_official_post_comments_trg`;
- `official_post_likes / quata_guard_official_post_likes_trg`.

La candidata RLS-002 sólo corrige Likes. Posts y Comments quedan registrados
para un lote independiente; este hallazgo no autoriza ampliar el despliegue.
El postflight de `20260726171002` falla si el guard de Likes sigue bajo el mismo
patrón.

### PUBLIC-MUTATION-01

La auditoría de catálogo clasifica políticas `public`/`anon` de mutación con
`USING (true)` o `WITH CHECK (true)`. El estado desplegado contiene:

- `community_comments`: INSERT, UPDATE y DELETE incondicionales;
- `community_post_likes`: INSERT y DELETE incondicionales;
- `community_profiles`: INSERT y UPDATE incondicionales.

`anon` conserva los privilegios de tabla correspondientes. Por tanto, sin
ensayar DML en producción, el contrato SQL demuestra:

- UPDATE de comments puede reasignar `profile_id` a otro perfil y luego eludir
  un DELETE que sólo mire el propietario actual;
- INSERT de comments permite publicar autoría con cualquier `profile_id`
  existente;
- UPDATE de profiles puede alcanzar columnas sensibles como `is_admin`,
  `account_status` o `auth_user_id`, salvo una defensa no visible en las
  políticas/triggers inventariados.

Severidad: **crítica** para Profiles y **alta** para Comments/Likes. El release
queda bloqueado hasta contar con candidata y revisión para las vías que puedan
eludir las correcciones RLS-001/RLS-002. No se ejecutó explotación remota.

## Auditoría de la candidata RLS-002

Commit revisado: `77d93da4`, sin integrar ni desplegar.

- versión `20260726171002`: única y coincide con la reserva;
- estado remoto compatible: `official_post_likes` tiene RLS desactivado, cero
  políticas, trigger guard `SECURITY DEFINER`; grants actuales son exactamente
  `anon SELECT` y `authenticated SELECT/INSERT/DELETE`;
- la migración es transaccional, convierte el trigger a `SECURITY INVOKER`,
  habilita RLS, conserva lectura anónima y limita INSERT/DELETE al actor;
- el rollback documentado restaura el estado remoto observado y reabre
  explícitamente RLS-002; no borra filas;
- contrato estático y prueba PostgreSQL desechable pasaron: reproducción del
  spoof previo, insert propio A/B, rechazo `42501` de spoof/borrado ajeno,
  delete propio y lectura anónima.

Condiciones pendientes: revisión independiente, staging/SB-09, postflight
Android/Feed y método de ledger que no reejecute las 29 migraciones históricas.

## Auditoría de la candidata RLS-001

Commit rechazado para release: `4757af17`, no integrado ni desplegado.

- versión `20260726171001` correcta y rollback explícito;
- conserva lectura pública y el contrato Android de borrado por ID;
- pero sólo restringe DELETE: la política UPDATE pública permite que un
  outsider reasigne `profile_id` y se convierta en propietario antes de borrar;
- INSERT público también permite suplantar autoría;
- el SQL no contiene su propio `BEGIN/COMMIT`; la prueba lo envuelve, pero el
  artefacto de producción no garantiza atomicidad por sí solo.

Debe reemplazarse por una candidata que cubra como mínimo UPDATE y DELETE, y
que decida INSERT con evidencia de compatibilidad del Android actual. El
hallazgo crítico de `community_profiles` requiere candidata/revisión separada
antes del release; no se añadirá improvisadamente a RLS-001.

## Backup y rollback

`scripts/backup-supabase-before-chat-migration.ps1` no es apto como backup de
este release: fue escrito para Chat, pasa la URL completa a `pg_dump`, puede
generar perfiles/datos sensibles en claro, no fuerza la CA de este pooler y no
verifica una restauración.

Antes de autorizar:

1. confirmar en Supabase el backup administrado/PITR y su punto temporal;
2. conservar el snapshot read-only de este script;
3. exigir a cada migración un rollback SQL específico, revisado y probado en
   entorno no productivo;
4. comprobar que el rollback restaura políticas/grants/triggers anteriores y
   no borra datos de negocio;
5. acordar un método de ledger que aplique sólo los tres timestamps reservados.

El método propuesto es generar un workdir efímero con únicamente:

- los dos SQL que corresponden a las entradas ya presentes en el ledger, como
  anclas;
- las migraciones nuevas seleccionadas y revisadas, con timestamp único.

`scripts/prepare-db-release-package.ps1` prepara ese paquete, valida versiones,
calcula hashes y marca `deploymentAuthorized=false`; no conecta ni despliega.
Antes de aplicar, el release manager debe enlazar ese workdir de forma segura y
ejecutar `supabase db push --dry-run`. El dry-run debe listar sólo las
migraciones nuevas. Si aparece cualquier SQL histórico, se aborta. No se usará
el workdir completo del repositorio.

No existe un rollback genérico seguro. Para RLS se restaura la política previa
en una transacción. Para el endpoint de registro se revocan grants y se
eliminan únicamente los objetos nuevos de esa versión, o se restaura su
definición anterior. Un fallo de compatibilidad detiene la serie; no se avanza
a la migración siguiente.

## Orden propuesto, todavía no autorizado

1. Revisar y congelar hashes de Communities y su rollback.
2. Snapshot + preflight; confirmar backup administrado/PITR.
3. Aplicar sólo Communities en transacción serial.
4. Postflight: SB-07, ANDROID-COMPAT-01 y FEED-ANON-01.
5. Revisar y aplicar sólo Official Likes.
6. Postflight: SB-09 y repetir ambas puertas de compatibilidad.
7. Endurecer `community_profiles` y comprobar que el flujo servidor de alta
   sigue siendo la única vía de creación/actualización.
8. Repetir Android/Feed y revisar alta Web; sólo entonces aplicar Registration.
9. Postflight de contrato Auth, login/feed anónimo, Android y ausencia de
   residuos E2E.
10. Conservar evidencia y solicitar autorización separada antes de cualquier
   paso destructivo o rollback.

El postflight mecanizable requiere enumerar las versiones esperadas:

```powershell
.\scripts\run-db-release-safety.ps1 `
  -Phase postflight `
  -DbUrlFile 'C:\ruta\db-url.txt' `
  -ExpectedMigration 20260726171001,20260726171002
```

Mientras el plan de ledger siga abierto, el comando debe continuar bloqueado
aunque Android y Feed sean compatibles.
