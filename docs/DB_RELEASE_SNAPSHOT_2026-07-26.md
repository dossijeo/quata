# Snapshot de release de base de datos — 2026-07-26

## Decisión

**NO-GO para producción.** Este documento congela la evidencia necesaria para
preparar el lote, pero no autoriza un despliegue. No se ejecutó DDL, DML,
`migration repair`, despliegue de Edge Functions ni escritura del ledger.

El snapshot read-only terminó en `passed` con fingerprint:

`86c9d97fa4b5f88a4b3e02fdd004820761c07b2b4fdcc2eddfab6739f0300eb1`

El informe completo se regenera localmente en
`build-reports/db-release-safety/snapshot.json`; se excluye de Git porque es
evidencia de ejecución, no fuente.

## Corte observado

- base de código: `origin/main@366c86aa`;
- servidor: PostgreSQL 17.6 mediante TLS `verify-full` y CA explícita;
- ledger remoto: `20260628/0001_chat_schema` y
  `20260723/0001_multidevice_fcm_and_web_push`;
- historial local: 31 SQL, con siete prefijos CLI repetidos y 29 ficheros sin
  fila remota;
- reconciliación de catálogo: dos `remote_ledger_anchor`, 22
  `catalog_effects_observed`, siete
  `catalog_effects_observed_superseded`, 29 decisiones sin evidencia semántica
  exhaustiva y cero marcadores ausentes;
- contrato Android: 18 tablas/vistas y 44 RPC observados, cero ausentes;
- Feed anónimo: grants `SELECT` presentes y al menos un post visible.

El fingerprint cubre el contenido normalizado de la evidencia: hashes de las
migraciones históricas, ledger, reconciliación, políticas, grants, firmas de
RPC y funciones de trigger. No incluye URL, credenciales, IDs o filas de
negocio.

## Método de ledger ensayado, no autorizado

El repositorio completo sigue siendo inseguro para `supabase db push`. La vía
ensayada es un paquete efímero que contiene únicamente las dos migraciones
ancla del ledger y los timestamps nuevos seleccionados, pero el estado real
tiene `selectivePackageEligible=false`.

`scripts/test-db-release-ledger-package.ps1` probó este método en PostgreSQL 17
desechable con TLS:

1. ledger inicial con dos anclas;
2. primer `supabase db push --dry-run` enumerando sólo cuatro probes nuevos;
3. push con ledger final 6/6 y cuatro tablas probe;
4. segundo dry-run sin pendientes;
5. eliminación del contenedor y del directorio temporal.

Esto prueba la mecánica sin falsear las 29 filas históricas. No demuestra que
sea seguro excluirlas: los marcadores parciales sólo prueban efectos de
catálogo, no ejecución íntegra ni equivalencia semántica. El empaquetador
rechaza el snapshot real hasta resolver esa evidencia.

## Matriz de migración y rollback

| Orden | Versión | Fuente congelada | Rollback | Decisión |
|---|---|---|---|---|
| 1 | `20260726171001` Communities comments | `46a54b54`, blob SQL `d6b847f4da85e7a85ae196b7595d235efe2a1e02` | versionado en el mismo commit, blob `0a6994e70bc6f1ad5f571f813cee58e2c4a7c78b` | Apta para staging; no producción sin gates |
| 2 | `20260726171002` Official Likes | `47c6abe3`, blob SQL `d54abd255ec598621e707864a68caab265fe8e33` | versionado, blob `fe27738b72c030eb4e3e437f0ce9c53b66145dfd` | Rollback cerrado; release aún bloqueado por ledger/backup/gates |
| 3 | `20260726171003` Profiles actor guard | `473f2400`, blob SQL `3f7ac5e522347e6abd601af6cb292a6b0c3d2f54` | versionado en el mismo commit, blob `0c464ce00ce478f596b579d49d765c3979df73f8` | Bloqueada por Android legado, admin inactivo y RLS-004 |
| 4 | `20260726171004` Web registration | `f6266215`, blob SQL `40af62de9671cd41724fd88cea392a94b0806b62` | inexistente | Bloqueada por 003 y por rollback/compatibilidad |

### Rollback 001

La fuente canónica es
`supabase/rollbacks/20260726171001_community_comments_delete_rls.rollback.sql`
en `46a54b54`. Fue incluida en la regresión desechable de la candidata
(aplicación, rollback y reaplicación). Restaura las políticas públicas previas;
por diseño reabre el hallazgo RLS-001 y sólo es un escape de compatibilidad.

### Rollback 002

La fuente canónica es
`supabase/rollbacks/20260726171002_official_post_likes_actor_guard.rollback.sql`
en `47c6abe3`. El SQL:

- elimina las tres políticas nuevas;
- desactiva RLS en `official_post_likes`;
- devuelve el guard a `SECURITY DEFINER`;
- elimina `quata_official_like_delete_allowed(uuid)`;
- no elimina filas.

La regresión PostgreSQL desechable pasó
migración→contrato seguro→rollback→reproducción del spoof
histórico→reaplicación→rechazo del spoof→limpieza. Ejecutarlo reabre
explícitamente RLS-002.

### Rollback 003

La fuente canónica es
`supabase/rollbacks/20260726171003_community_profiles_actor_guard.rollback.sql`
en `473f2400`; la regresión desechable cubrió rollback. Esto no hace que la
migración sea apta para release:

- revocar el UPDATE anónimo rompe el reset de contraseña del Android legado;
- un administrador desactivado todavía puede asignar roles porque los
  resolvers usados no comprueban que la cuenta esté activa;
- la lectura pública continúa exponiendo `pass_plain`, `pass_hash` y
  `secret_answer` (RLS-004).

La solución debe separar columnas públicas, mutaciones actor-bound y transición
del reset. El rollback reabre la mutación pública crítica, por lo que sólo es
una salida de incidente.

### Rollback 004

El rollback operacional seguro antes de cualquier migración de datos es:

1. mantener `QUATA_WEB_REGISTRATION_ENABLED=false` en cliente y servidor;
2. retirar/no desplegar `quata-web-register`;
3. conservar tablas, hashes y solicitudes para análisis; no borrar datos;
4. restaurar la versión anterior de `quata-auth-bridge` sólo si su contrato de
   identidades se ha demostrado compatible.

No hay rollback SQL versionado que elimine las tablas, funciones y
`secret_answer_hash`. Después de activación, un `DROP` sería potencialmente
destructivo y no se acepta como rollback genérico.

La candidata también necesita un plan para identidades existentes: al definir
`QUATA_INTERNAL_AUTH_PASSWORD_SECRET`, `quata-auth-bridge` deja de usar de
inmediato el fallback basado en la service-role key. Sin transición, las
contraseñas Auth ya derivadas podrían cambiar. Asimismo,
`quata-web-register` debe registrar códigos seguros y no `error.message`.

La revisión independiente añadió bloqueos funcionales: la UI Web no integra ni
envía `challenge_token`, por lo que el servidor con Turnstile obligatorio
rechazaría todas las altas; la respuesta uniforme mantiene una señal temporal;
y el runner de cleanup elimina trazabilidad y carece de claim/lease atómico.
Las pruebas disponibles son unitarias con mocks, no DB/Edge/navegador reales.

## Drift y riesgos que deben permanecer visibles

El snapshot remoto detectó cuatro guards `SECURITY DEFINER` dependientes de
contexto de actor:

- `community_profiles`;
- `official_posts`;
- `official_post_comments`;
- `official_post_likes`.

También observó políticas públicas de mutación incondicional en
`community_comments`, `community_post_likes` y `community_profiles`. No se
intentó explotación ni escritura remota.

## Backup/PITR

El 2026-07-26T19:36:55Z,
`scripts/check-supabase-backup-readiness.ps1` consultó en modo lectura la CLI
Supabase 2.109.1 y verificó que la URL del pooler correspondía a un proyecto
`ACTIVE_HEALTHY`. El resultado fue:

- PITR deshabilitado;
- WAL-G habilitado, pero sin restore point verificable por sí solo;
- cero backups listados y cero entradas de backup físico;
- `releaseReady=false`, decisión
  `blocked_no_verifiable_restore_point`.

El informe sanitizado está en
`build-reports/db-release-safety/backup-readiness.json`. No contiene URL,
credenciales ni project ref en claro. Este bloqueo exige habilitar/confirmar un
restore point recuperable desde Supabase antes de cualquier release.

## Condiciones mínimas para pasar a GO

1. resolver las 29 decisiones históricas con evidencia semántica exhaustiva o
   reconciliación aprobada;
2. confirmar un backup/PITR recuperable;
3. integrar 001 y 002 sólo después de sus regresiones aisladas y rollback
   versionado;
4. ejecutar snapshot, paquete selectivo y dry-run; el listado debe contener
   exclusivamente la siguiente migración autorizada;
5. ejecutar después de cada versión la suite 18/44, Feed público, Web y smoke
   Android API-37;
6. corregir la candidata 003 para exigir admin activo y coordinarla con un
   reemplazo desplegado del reset Android legado y un contrato de columnas
   públicas seguro;
7. volver a auditar 004 después de 003, versionar rollback y demostrar la
   transición del secreto interno;
8. validar iOS mediante CI antes de cerrar el lote;
9. confirmar backup administrado/PITR inmediatamente antes de cualquier
   cambio remoto.
