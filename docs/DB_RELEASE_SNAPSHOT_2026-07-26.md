# Snapshot de release de base de datos — 2026-07-26

## Decisión

**NO-GO para 002 sin autorización separada.** RLS-001
(`20260726171001`) se aplicó y revirtió; después se aplicó con autorización la
forward `20260726171005`. Su postflight funcional pasó con evidencia SB-07
compuesta. Los ledgers 171001/171005 se conservan y el catálogo está hardened.
No se aplicó 002, no se usó `migration repair` y no se desplegaron Edge
Functions.

El snapshot read-only terminó en `passed` con fingerprint:

`86c9d97fa4b5f88a4b3e02fdd004820761c07b2b4fdcc2eddfab6739f0300eb1`

El informe completo se regenera localmente en
`build-reports/db-release-safety/snapshot.json`; se excluye de Git porque es
evidencia de ejecución, no fuente.

## Corte observado y candidato actual

- corte operativo con 171005 aplicado: `origin/codex/security-release-001-002@8a548d2f`;
- corte candidato actual para preparar la ventana de 002:
  `409adae0d0ee8a3d9d8b8ab7b2a5b7dfbeb3465f`;
- evidencia documental postflight: misma rama, validada contra su `HEAD`;
- servidor: PostgreSQL 17.6 mediante TLS `verify-full` y CA explícita;
- ledger remoto: anclas `20260628`, `20260723`, RLS-001 171001 y su forward
  `20260726171005/community_comments_reapply_rls`;
- historial local: 34 SQL (31 históricos + 001/002/forward-001), con siete prefijos CLI
  históricos repetidos, 29 ficheros históricos sin fila remota y dos
  candidatas no desplegadas;
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
| 2 | `20260726171002` Official Likes | `409adae0`, SHA-256 SQL `8697a16fe40658f57205cc8cd32d8795880d82d99a132f283da83649d85dd5f4` | SHA-256 `fe498b6c61ea714d42b330668a49d7cd584961a6a023c0d2d38aadb58aef5f79` | Pruebas locales cerradas; NO-GO apply hasta backup, gate nueva y autorización |
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
en `409adae0`, con SHA-256
`fe498b6c61ea714d42b330668a49d7cd584961a6a023c0d2d38aadb58aef5f79`.
El SQL:

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

### Alternativa lógica ejecutada

Ante la ausencia de PITR se creó, sin DDL/DML, un backup lógico **Full** real:

- TLS efectivo `verify-full` con CA explícita;
- `pg_dump` custom completo, grants/ACL incluidos;
- cifrado streaming AES-256-GCM sin dump plano persistente;
- clave custodiada separadamente y backup/key con ACL exclusiva del usuario;
- manifiesto sin conexión ni valores de negocio;
- SHA-256 cifrado
  `9f8ff3730575595ffbb8d13c91d98977a16fe4219bec147f207f86f8790910b1`;
- SHA-256 plano autenticado
  `ccf93e7eb02ebc6bc27589028eb3855dfd11b84e03423b41843ced90a65f29b8`.

El Full gestionado no restauró íntegramente en PostgreSQL vanilla
(`pg_cron`) ni sobre una imagen Supabase ya inicializada (conflictos de objetos
gestionados); esos intentos fallaron cerrados y eliminaron plaintext/contenedor.
El drill de alcance del lote sí pasó en PostgreSQL 17:

- el TOC confirmó tablas, datos, ACL/grants, funciones y estado previo de
  policies/RLS;
- se restauraron `community_comments` y `official_post_likes`;
- los conteos restaurados coincidieron exactamente con el snapshot read-only:
  112 y 19;
- checksum cifrado, tag GCM y checksum plano fueron verificados.

Esto proporciona recuperación lógica de los objetos/datos afectados, pero no
se presenta como sustituto equivalente de PITR ni como restauración integral de
todos los servicios gestionados de Supabase.

## Evidencia local del corte de integración

Sobre `codex/security-release-001-002@409adae0`, antes de cualquier despliegue:

- RLS-002 pasó en PostgreSQL 17 + PostgREST 12.2.3 el baseline vulnerable, la
  migración segura, ataques spoof/cross-delete `42501`, rechazo atómico de
  rollback ante drift, rollback al fingerprint exacto y reaplicación segura;
- el gate completo de compatibilidad terminó `passed`: 10 lecturas públicas,
  inventario Android 18 tablas/44 RPC sin ausencias, 243 columnas y 45 firmas
  registradas;
- la navegación Web credential-free pasó `#feed`, `#official` y
  `#communities`;
- el emulador API-37 autenticado pasó Feed, Chat, Official, Communities y
  Profile, con proceso vivo y cero crash/ANR;
- el ejecutor serial allowlisted pasó en PostgreSQL 17: atomicidad,
  concurrencia externa de tablas y funciones, ledger exacto, orden,
  postcondiciones efectivas y rollback 001/002;
- el bundle local machine-readable
  `build-reports/security-release/409adae0-local-release-evidence/report.json`
  encadena logs y hashes de SB09 exacto PostgreSQL 17 + PostgREST 12.2.3 y de
  todas las transiciones seriales;
- el dry-run remoto post-forward terminó `passed`; 171001/171005 están
  presentes con fuente/nombre exactos, 002 está ausente y el fingerprint de
  `community_comments` coincide con el estado hardened. Su SHA-256 es
  `9cfccbe8...`: legacy 171001/171005 `2efec424...`, 002 ausente con
  precondición `f0b60c57...` y candidata `8697a16f...`;
- la identidad del destino se deriva del usuario/project-ref normalizado y se
  ancla a `pg_control_system().system_identifier`, OID/base y rol consultados,
  conservando únicamente el SHA-256;
- el ejecutor no enumera ni aplica el backlog histórico y el runbook prohíbe
  `supabase db push`.

Los informes permanecen ignorados bajo `build-reports/`. Ni 171001 ni 171005
pueden reaplicarse o repararse. Un rollback futuro de 171005 exigiría otra
versión forward nueva.

El informe del intento fallido y atómico de 002 tiene SHA-256
`a15a1bea89fa7b38a6baa6bf8f397841b87ed9ca52e4772c057ef2ede7733e00`.
Referencia hashes anteriores y se conserva exclusivamente como evidencia
forense: no habilita retry. Las gates SHA-256 `335f92ab...` y `0df21931...`
quedan definitivamente revocadas/stale. Antes de cualquier apply se debe crear
y autorizar una gate nueva vinculada a `409adae0`, forward `8697a16f...`,
rollback `fe498b6c...`, precondición `f0b60c57...` y postcondición legacy
171005 `2efec424...`.

## Excepción de gobernanza y condiciones para la ventana

El release manager acepta expresamente, sólo para 001/002, la ausencia de PITR
y la falta de reconciliación semántica de 29 migraciones históricas. La
excepción se basa en que el ejecutor no toca el backlog, ambas migraciones son
DDL/RLS sin DML, sus rollbacks exactos están probados y existe backup lógico
Full con drill verificado de los objetos afectados. No se presenta como
restauración integral de Supabase. Las 29 decisiones siguen abiertas como
hallazgo: no se marcan, reparan ni aplican.

Para la forward 171005 ya aplicada se aceptó una evidencia SB-07 compuesta
sin fixtures productivos: el ensayo mutante completo se ejecuta en Supabase
local exacto y producción aporta postcondición de catálogo bajo lock, contratos
read-only y smoke Android autenticado sin crear datos. El runner que crea y
purga Auth/perfiles en producción permanece NO-GO crash-safe y no bloquea
su aplicación porque no formó parte de la ventana.

Antes de abrir la futura ventana de 002:

1. congelar y revisar de nuevo el commit exacto del corte;
2. refrescar el backup lógico Full, su clave separada y el drill con conteos;
3. refrescar snapshot read-only, baseline 18/44, Web y Android API-37;
4. repetir el dry-run serial y archivar hashes/fingerprints;
5. designar una sola autoridad y una sola terminal ejecutora;
6. obtener autorización explícita separada para `apply-002`;
7. cerrar cada paso con postflight y gates antes de avanzar;
8. mantener 003/004 y las 29 históricas fuera de toda ejecución.
