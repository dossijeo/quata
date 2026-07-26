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

### Reconciliación versionada

`supabase/migration-reconciliation.json` clasifica las 31 migraciones y declara
marcadores de catálogo duraderos. El snapshot read-only calcula el SHA-256 de
cada SQL y comprueba los marcadores:

- 24 `already_applied`;
- 7 `obsolete_superseded`;
- 0 `pending`;
- 0 decisiones sin fichero y 0 marcadores ausentes.

Esta clasificación no inserta 29 filas ficticias en el ledger: varias versiones
históricas colisionan y algunas definiciones fueron reemplazadas. Su registro
auditable es el manifiesto + fingerprint del snapshot. El ledger Supabase
continúa con sus dos anclas reales y, en adelante, sólo recibe timestamps
únicos aplicados por el paquete selectivo.

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

El `snapshot` captura evidencia aunque exista drift. `preflight` y `postflight`
sólo pueden pasar el gate de historial si la reconciliación está completa y se
usa el método de paquete selectivo. El informe conserva
`safeForSupabaseDbPush=false`: ejecutar desde el repositorio completo continúa
prohibido aunque `selectivePackageEligible=true`.

### ANDROID-COMPAT-01

PASS exige simultáneamente:

- las 18 tablas/vistas derivadas de las llamadas del cliente actual presentes;
- los 44 RPC derivados de `SupabaseCommunityApi` más los dos contratos de
  release-history presentes;
- firmas registradas para comparar antes/después;
- tras el eventual despliegue, repetición del catálogo y smoke del APK actual
  en API-37 sin crash/ANR.

La primera versión del release-safety cubría sólo un subconjunto manual de
10 tablas/32 RPC. Tras auditar `61987276`, el inventario se deriva del código y
usa el superset 18/44 de la suite de compatibilidad; ese es el contrato
autoritativo. En el preflight ampliado: `missingTables=[]`,
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

La suite `61987276` es la puerta completa propuesta: GET PostgREST público con
las columnas exactas de Feed/Official/Communities, comparación de firmas
pre/post, navegación Web sin credenciales y smoke API-37 preservando datos.
Release-safety conserva el catálogo 18/44 y el gate mínimo Feed; la suite
completa debe ejecutarse antes y después de cada migración autorizada.

### TRIGGER-ACTOR-01

El preflight inventaría triggers en posts, comentarios y likes, y marca como
riesgo cualquier función de trigger `SECURITY DEFINER` que consulte
`current_user` directamente o mediante `quata_current_role_is_service()`.
No publica el cuerpo: sólo nombre, flags y SHA-256.

El snapshot desplegado encontró cuatro riesgos:

- `community_profiles / quata_guard_profile_roles_trg`;
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

Revisión independiente: **aprobada para staging** por
`review_auxiliary_e2e`. Los únicos bloqueos restantes de esta candidata son el
método de ledger/release, el ensayo staging/SB-09 y los gates reales
pre/postflight de Android y Feed.

El rollback de RLS-002 sigue sólo dentro de la documentación de la candidata.
Antes de integrar debe existir como SQL versionado, con hash congelado y
regresión rollback/reaplicación.

## Auditoría de la candidata RLS-001

El primer commit `4757af17` fue rechazado. La revisión corregida
`46a54b54` está **aprobada para integración/staging** por
`review_auxiliary_e2e`, pero no desplegada.

- El rechazo inicial se debió a que sólo restringía DELETE, dejaba la evasión
  UPDATE→DELETE, permitía INSERT suplantado y no era auto-transaccional.
- La versión `20260726171001` corregida conserva rollback explícito y lectura
  pública, y mantiene el contrato Android de borrado por ID.

La revisión corregida cubre INSERT/UPDATE/DELETE, es transaccional, preserva
SELECT y prueba el contrato Android de insert propio/borrado por ID, admin
activo/inactivo, rollback/reaplicación y limpieza. Quedan pendientes la
regresión Supabase aislada y los gates reales post-release. El hallazgo crítico
de `community_profiles` mantiene su candidata/revisión separada.

## Auditoría de Web registration 004

Commit corregido revisado: `f6266215`, sin integrar ni desplegar.

La revisión corrige transacción, flags cliente/servidor fail-closed, Turnstile
obligatorio al habilitar, secreto interno dedicado, respuesta 202 uniforme y
runner de cleanup. Aun así, queda **bloqueada**:

- declara 003 como precondición, pero la candidata `473f2400` rompe el reset
  Android legado y permite que un admin desactivado asigne roles;
- no incluye rollback SQL versionado;
- al configurar el secreto dedicado, identidades Auth antiguas derivadas con
  la service-role key requieren una transición demostrada;
- el endpoint registra `error.message`, que debe reemplazarse por códigos
  operativos seguros.
- la UI Web no integra ni envía el token Turnstile, por lo que todas las altas
  quedarían rechazadas al habilitar el servidor;
- persiste una señal de enumeración por tiempo y el cleanup elimina
  trazabilidad, no tiene claim/lease y sólo está probado con mocks.

El bloqueo de 003 incluye el reset Android legado y la exposición pública de
`pass_plain`, `pass_hash` y `secret_answer`. Registration no se puede
empaquetar antes de resolver ese contrato.

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
5. acordar un método de ledger que aplique sólo los cuatro timestamps reservados.

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

`scripts/test-db-release-ledger-package.ps1` validó el procedimiento contra
PostgreSQL 17 desechable con TLS: dos anclas simuladas, dry-run que enumeró sólo
001-004, aplicación de cuatro probes, ledger final 6/6 y segundo dry-run sin
pendientes. El contenedor y el clon temporal se eliminaron al terminar. Esto
prueba selección/registro/idempotencia; las regresiones SQL de cada candidata
siguen siendo obligatorias porque los probes no sustituyen el esquema Supabase.

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

Si aparece una decisión histórica ausente, un marcador remoto ausente o un
fichero desconocido, el comando vuelve a `blocked_history_reconciliation`
aunque Android y Feed sean compatibles.

El corte reproducible, fingerprint y matriz de rollback 001-004 quedan
congelados en `docs/DB_RELEASE_SNAPSHOT_2026-07-26.md`.
