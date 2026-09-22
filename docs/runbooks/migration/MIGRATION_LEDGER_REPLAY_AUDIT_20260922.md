# Auditoría de replay del ledger — 22 de septiembre de 2026

## Resultado

La reconciliación sigue **abierta** y `supabase db push` sigue siendo inseguro.
El replay aislado y comparaciones focales read-only reducen de 29 a 22 las
decisiones históricas sin equivalencia semántica acreditada. Cuatro archivos
formados íntegramente por `CREATE OR REPLACE FUNCTION` y, cuando corresponde,
`GRANT`, quedan acreditados por replay:

- `20260629_0011_chat_demote_moderator.sql`;
- `20260630_0013_chat_conversation_candidates.sql`;
- `20260630_0014_chat_cleanup_empty_private_threads.sql`;
- `20260714_0005_chat_reply_snapshots.sql`.

En esos cuatro casos el replay dejó idénticos el digest completo del catálogo
auditado y el digest de todas las tablas y secuencias de aplicación. El catálogo
incluye firma, cuerpo, atributos, configuración y ACL de funciones. Como esos
archivos no contienen DDL condicional ni DML, el resultado acredita la semántica
versionada presente, sin inferirla a partir de un marcador parcial.

`20260628_0003_auth_bridge_support.sql` queda también como
`verified_applied_semantics`: su único `DO` crea cinco índices condicionales y
las cinco columnas existen en el remoto. El audit read-only comparó para cada
rama nombre, columna, predicado parcial y estado `valid/ready`; las cinco
definiciones son exactas. La evidencia reproducible está en
[`auth-bridge-semantics-20260922.json`](evidence/auth-bridge-semantics-20260922.json)
y no afirma ejecución histórica.

`20260709_0003_official_post_soft_delete_policy.sql` queda igualmente
acreditado. El audit remoto read-only comprobó una única política con comando
`SELECT`, modo permisivo, roles `anon` y `authenticated`, expresión `USING`
exacta y ausencia de `WITH CHECK`. En el replay aislado, `DROP POLICY` seguido
de `CREATE POLICY` cambió el OID del objeto y el orden interno del array de OID
de roles, pero no el conjunto de roles, la definición normalizada ni ningún
digest de datos. El orden de ese array no altera la pertenencia de roles. La
evidencia reproducible está en
[`official-soft-delete-policy-semantics-20260922.json`](evidence/official-soft-delete-policy-semantics-20260922.json).

La decisión separada `20260808_0001_official_posts_actor_guard.sql` queda
reconciliada sin inventar una fila de ledger. PR #195 registró la aplicación
manual del SQL versionado exacto. El replay directo confirma que el archivo no
es idempotente porque vuelve a crear tres políticas con sus nombres actuales;
en una restauración aislada se retiraron sólo esas tres políticas y las 25
sentencias reprodujeron funciones, ACL, RLS y políticas exactas sin cambiar
ningún digest de datos. Un audit remoto read-only confirmó los mismos efectos.
Evidencia:
[`official-actor-guard-semantics-20260922.json`](evidence/official-actor-guard-semantics-20260922.json).

`20260702_0004_official_read_more_label.sql` queda también reconciliado. Su
replay fue un no-op completo de catálogo y datos, y el audit remoto read-only
comprobó la única columna con tipo `text`, `NOT NULL`, sin identidad ni
generación, colación por defecto y comentario exacto. El default original
`Leer mas` es el único efecto sustituido: la sentencia 3 exacta de
`20260709_0002_official_post_languages.sql` lo cambia a `read_more`, que es el
valor observado. Esa migración posterior conserva abiertos sus demás efectos.
Evidencia:
[`official-read-more-label-semantics-20260922.json`](evidence/official-read-more-label-semantics-20260922.json).

## Método aislado

Se restauró el backup lógico de aplicación del 22 de septiembre en
`public.ecr.aws/supabase/postgres:17.6.1.063`: 87 tablas y 52.211 filas. El
ensayo no abrió una conexión de escritura a Supabase.

[`db-historical-migration-replay-digest.sql`](../../../scripts/db-historical-migration-replay-digest.sql)
calcula dos hashes sin emitir valores de negocio:

1. catálogo de `public`, `storage` y `security_citizen`: relaciones, columnas,
   restricciones, índices, triggers, políticas, funciones, tipos y ACL;
2. contenido de todas las tablas y secuencias de `public` y
   `security_citizen`, agregando únicamente hashes por fila.

Cada archivo transaccional se ejecutó entre un digest previo y otro posterior y
se revirtió. `20260716_0001_ugc_moderation.sql`, que contiene su propio
`COMMIT`, se ejecutó al final en una restauración desechable. El actor guard de
Official se ensayó en otra restauración limpia. El informe durable, con hashes
del restore, del helper y el resultado por archivo, es
[`migration-ledger-replay-20260922.json`](evidence/migration-ledger-replay-20260922.json).

## Hallazgos conservadores

- Dieciocho archivos pendientes modificarían catálogo, datos o ambos si se repitieran
  sobre el snapshot actual. Son evidencia directa contra un `db push` completo.
- `20260628_0004_chat_android_runtime_support.sql` no puede completarse porque
  el backup de aplicación excluye `storage.objects`.
- `20260702_0003_official_accounts.sql` depende de filas de Auth que el backup
  de aplicación excluye.
- Otros tres archivos no cambiaron los digests, pero usan `IF NOT EXISTS`,
  DDL condicional o DML histórico. Un no-op no demuestra que la definición
  existente sea la misma que la definición omitida; permanecen sin verificar.
- El replay directo de `20260808_0001_official_posts_actor_guard.sql` conserva
  su conflicto no idempotente como evidencia; la reconciliación usa además el
  replay controlado, el recibo de PR #195 y el audit remoto completo.

Por tanto, `selectivePackageEligible` continúa en `false`: faltan 22 decisiones,
además de la divergencia ya registrada de la política admin de Official. Esta
auditoría no amplía ninguna excepción de gobernanza, no
autoriza RLS-003/RLS-004 y no sustituye backup administrado o PITR.
